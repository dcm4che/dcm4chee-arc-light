package org.dcm4chee.arc.export.mgt.impl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Predicate;
import com.querydsl.jpa.hibernate.HibernateQuery;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.entity.ExportTask;
import org.dcm4chee.arc.entity.QExportTask;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.export.mgt.ExportManager;
import org.dcm4chee.arc.qmgt.QueueManager;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Stateless;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.jms.JMSException;
import javax.jms.JMSRuntimeException;
import javax.jms.ObjectMessage;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import org.hibernate.Session;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Oct 2015
 */
@Stateless
public class ExportManagerEJB implements ExportManager {

    static final Logger LOG = LoggerFactory.getLogger(ExportManagerEJB.class);

    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    @Inject
    private Device device;

    @Inject
    private QueryService queryService;

    @Inject
    private QueueManager queueManager;

    private static final Expression<?>[] SELECT = {
            QExportTask.exportTask.exporterID,
            QExportTask.exportTask.status,
            QExportTask.exportTask.messageID,
            QExportTask.exportTask.createdTime,
            QExportTask.exportTask.updatedTime,
            QExportTask.exportTask.scheduledTime,
            QExportTask.exportTask.studyInstanceUID,
            QExportTask.exportTask.seriesInstanceUID,
            QExportTask.exportTask.sopInstanceUID,
            QExportTask.exportTask.modalities,
            QExportTask.exportTask.numberOfInstances,
            QExportTask.exportTask.numberOfFailures,
            QExportTask.exportTask.processingStartTime,
            QExportTask.exportTask.processingEndTime,
            QExportTask.exportTask.errorMessage,
            QExportTask.exportTask.outcomeMessage
    };

    @Override
    public void onStore(@Observes StoreContext ctx) {
        if (ctx.getLocations().isEmpty() || ctx.getException() != null)
            return;

        StoreSession session = ctx.getStoreSession();
        String hostname = session.getRemoteHostName();
        String sendingAET = session.getCallingAET();
        String receivingAET = session.getCalledAET();
        Calendar now = Calendar.getInstance();
        ArchiveAEExtension arcAE = session.getArchiveAEExtension();
        ArchiveDeviceExtension arcDev = arcAE.getArchiveDeviceExtension();
        for (Map.Entry<String, ExportRule> entry
                : arcAE.findExportRules(hostname, sendingAET, receivingAET, ctx.getAttributes(), now).entrySet()) {
            String exporterID = entry.getKey();
            ExportRule rule = entry.getValue();
            ExporterDescriptor desc = arcDev.getExporterDescriptorNotNull(exporterID);
            Date scheduledTime = scheduledTime(now, rule.getExportDelay(), desc.getSchedules());
            switch (rule.getEntity()) {
                case Study:
                    createOrUpdateStudyExportTask(exporterID, ctx.getStudyInstanceUID(), scheduledTime);
                    if (rule.isExportPreviousEntity() && ctx.isPreviousDifferentStudy())
                        createOrUpdateStudyExportTask(exporterID,
                                ctx.getPreviousInstance().getSeries().getStudy().getStudyInstanceUID(), scheduledTime);
                    break;
                case Series:
                    createOrUpdateSeriesExportTask(exporterID, ctx.getStudyInstanceUID(), ctx.getSeriesInstanceUID(),
                            scheduledTime);
                    if (rule.isExportPreviousEntity() && ctx.isPreviousDifferentSeries())
                        createOrUpdateSeriesExportTask(exporterID,
                                ctx.getPreviousInstance().getSeries().getStudy().getStudyInstanceUID(),
                                ctx.getPreviousInstance().getSeries().getSeriesInstanceUID(),
                                scheduledTime);
                    break;
                case Instance:
                    createOrUpdateInstanceExportTask(exporterID, ctx.getStudyInstanceUID(), ctx.getSeriesInstanceUID(),
                            ctx.getSopInstanceUID(), scheduledTime);
                    break;
            }
        }
    }

    private void createOrUpdateStudyExportTask(String exporterID, String studyIUID, Date scheduledTime) {
        try {
            ExportTask task = em.createNamedQuery(ExportTask.FIND_BY_EXPORTER_ID_AND_STUDY_IUID, ExportTask.class)
                    .setParameter(1, exporterID)
                    .setParameter(2, studyIUID)
                    .getSingleResult();
            task.setSeriesInstanceUID("*");
            task.setSopInstanceUID("*");
            task.setScheduledTime(scheduledTime);
        } catch (NoResultException nre) {
            createExportTask(exporterID, studyIUID, "*", "*", scheduledTime);
        }
    }

    private void createOrUpdateSeriesExportTask(
            String exporterID, String studyIUID, String seriesIUID, Date scheduledTime) {
        try {
            ExportTask task = em.createNamedQuery(
                    ExportTask.FIND_BY_EXPORTER_ID_AND_STUDY_IUID_AND_SERIES_IUID, ExportTask.class)
                    .setParameter(1, exporterID)
                    .setParameter(2, studyIUID)
                    .setParameter(3, seriesIUID)
                    .getSingleResult();
            task.setSopInstanceUID("*");
            task.setScheduledTime(scheduledTime);
        } catch (NoResultException nre) {
            createExportTask(exporterID, studyIUID, seriesIUID, "*", scheduledTime);
        }
    }

    private void createOrUpdateInstanceExportTask(
            String exporterID, String studyIUID, String seriesIUID, String sopIUID, Date scheduledTime) {
        try {
            ExportTask task = em.createNamedQuery(
                    ExportTask.FIND_BY_EXPORTER_ID_AND_STUDY_IUID_AND_SERIES_IUID_AND_SOP_IUID, ExportTask.class)
                    .setParameter(1, exporterID)
                    .setParameter(2, studyIUID)
                    .setParameter(3, seriesIUID)
                    .setParameter(4, sopIUID)
                    .getSingleResult();
            task.setScheduledTime(scheduledTime);
        } catch (NoResultException nre) {
            createExportTask(exporterID, studyIUID, seriesIUID, sopIUID, scheduledTime);
        }
    }

    private ExportTask createExportTask(
            String exporterID, String studyIUID, String seriesIUID, String sopIUID, Date scheduledTime) {
        ExportTask task = new ExportTask();
        task.setDeviceName(device.getDeviceName());
        task.setExporterID(exporterID);
        task.setStudyInstanceUID(studyIUID);
        task.setSeriesInstanceUID(seriesIUID);
        task.setSopInstanceUID(sopIUID);
        task.setScheduledTime(scheduledTime);
        task.setStatus(QueueMessage.Status.TO_SCHEDULE);
        em.persist(task);
        return task;
    }

    private Date scheduledTime(Calendar cal, Duration exportDelay, ScheduleExpression[] schedules) {
        if (exportDelay != null) {
            cal = (Calendar) cal.clone();
            cal.add(Calendar.SECOND, (int) exportDelay.getSeconds());
        }
        cal = ScheduleExpression.ceil(cal, schedules);
        return cal.getTime();
    }

    @Override
    public int scheduleExportTasks(int fetchSize) {
        final List<ExportTask> resultList = em.createNamedQuery(
                ExportTask.FIND_SCHEDULED_BY_DEVICE_NAME, ExportTask.class)
                .setParameter(1, device.getDeviceName())
                .setMaxResults(fetchSize)
                .getResultList();
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        for (ExportTask exportTask : resultList) {
            ExporterDescriptor exporter = arcDev.getExporterDescriptor(exportTask.getExporterID());
            scheduleExportTask(exportTask, exporter);
        }
        return resultList.size();
    }

    @Override
    public void scheduleExportTask(String studyUID, String seriesUID, String objectUID, ExporterDescriptor exporter) {
        ExportTask task = createExportTask(exporter.getExporterID(), studyUID, seriesUID, objectUID, new Date());
        scheduleExportTask(task, exporter);
    }

    private void scheduleExportTask(ExportTask exportTask, ExporterDescriptor exporter) {
        QueueMessage queueMessage = queueManager.scheduleMessage(exporter.getQueueName(),
                createMessage(exportTask, exporter.getAETitle()));
        exportTask.setMessageID(queueMessage.getMessageID());
        exportTask.setScheduledTime(queueMessage.getScheduledTime());
        exportTask.setStatus(queueMessage.getStatus());
        Attributes attrs = queryService.queryExportTaskInfo(
                exportTask.getStudyInstanceUID(),
                exportTask.getSeriesInstanceUID(),
                exportTask.getSopInstanceUID(),
                device.getApplicationEntity(exporter.getAETitle(), true));
        if (attrs != null) {
            exportTask.setModalities(attrs.getStrings(Tag.ModalitiesInStudy));
            exportTask.setNumberOfInstances(
                    Integer.valueOf(attrs.getInt(Tag.NumberOfStudyRelatedInstances, -1)));
        }
    }

    private ObjectMessage createMessage(ExportTask exportTask, String aeTitle) {
        ObjectMessage msg = queueManager.createObjectMessage(exportTask.getPk());
        try {
            msg.setStringProperty("StudyInstanceUID", exportTask.getStudyInstanceUID());
            msg.setStringProperty("SeriesInstanceUID", exportTask.getSeriesInstanceUID());
            msg.setStringProperty("SopInstanceUID", exportTask.getSopInstanceUID());
            msg.setStringProperty("ExporterID", exportTask.getExporterID());
            msg.setStringProperty("AETitle", aeTitle);
        } catch (JMSException e) {
            throw new JMSRuntimeException(e.getMessage(), e.getErrorCode(), e.getCause());
        }
        return msg;
    }

    @Override
    public void updateExportTask(QueueMessage queueMessage) {
        ExportTask exportTask = em.find(ExportTask.class, queueMessage.getMessageBody());
        exportTask.setScheduledTime(queueMessage.getScheduledTime());
        exportTask.setProcessingStartTime(queueMessage.getProcessingStartTime());
        exportTask.setProcessingEndTime(queueMessage.getProcessingEndTime());
        exportTask.setNumberOfFailures(queueMessage.getNumberOfFailures());
        exportTask.setOutcomeMessage(queueMessage.getOutcomeMessage());
        exportTask.setErrorMessage(queueMessage.getErrorMessage());
        exportTask.setStatus(queueMessage.getStatus());
    }

    @Override
    public List<Tuple> search(String exporterID, String studyUID, QueueMessage.Status status, int offset, int limit) {
        HibernateQuery<Tuple> query = new HibernateQuery<Void>(em.unwrap(Session.class))
                .select(SELECT)
                .from(QExportTask.exportTask)
                .where(createPredicate(exporterID, studyUID, status));
        if (limit > 0)
            query.setFetchSize(limit);
        if (offset > 0)
            query.offset(offset);
        return query.fetch();
    }

    private Predicate createPredicate(String exporterID, String studyUID, QueueMessage.Status status) {
        BooleanBuilder builder = new BooleanBuilder();
        if (exporterID != null)
            builder.and(QExportTask.exportTask.exporterID.eq(exporterID));
        if (studyUID != null)
            builder.and(QExportTask.exportTask.studyInstanceUID.eq(studyUID));
        if (status != null)
            builder.and(QExportTask.exportTask.status.eq(status));
        return builder;
    }

}
