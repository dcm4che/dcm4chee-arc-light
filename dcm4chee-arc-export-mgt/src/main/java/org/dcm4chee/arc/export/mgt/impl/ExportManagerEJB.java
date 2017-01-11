package org.dcm4chee.arc.export.mgt.impl;

import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.entity.ExportTask;
import org.dcm4chee.arc.entity.Instance;
import org.dcm4chee.arc.entity.Series;
import org.dcm4chee.arc.export.mgt.ExportManager;
import org.dcm4chee.arc.qmgt.QueueManager;
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
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
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
    private QueueManager queueManager;

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
            boolean exportPreviousEntity = rule.isExportPreviousEntity();
            switch (rule.getEntity()) {
                case Study:
                    studyExportTaskPrevAndOrStoredInst(exporterID, ctx, scheduledTime, exportPreviousEntity);
                    break;
                case Series:
                    seriesExportTaskPrevAndOrStoredInst(exporterID, ctx, scheduledTime, exportPreviousEntity);
                    break;
                case Instance:
                    instanceExportTaskPrevAndOrStoredInst(exporterID, ctx, scheduledTime, exportPreviousEntity);
                    break;
            }
        }
    }

    private void studyExportTaskPrevAndOrStoredInst(
            String exporterID, StoreContext ctx, Date scheduledTime, boolean exportPreviousEntity) {
        if (exportPreviousEntity && ctx.getPreviousInstance() != null)
            createOrUpdateStudyExportTask(
                    exporterID, ctx.getPreviousInstance().getSeries().getStudy().getStudyInstanceUID(), scheduledTime);
        createOrUpdateStudyExportTask(exporterID, ctx.getStudyInstanceUID(), scheduledTime);
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
            ExportTask task = new ExportTask();
            task.setDeviceName(device.getDeviceName());
            task.setExporterID(exporterID);
            task.setStudyInstanceUID(studyIUID);
            task.setSeriesInstanceUID("*");
            task.setSopInstanceUID("*");
            task.setScheduledTime(scheduledTime);
            em.persist(task);
        }
    }

    private void seriesExportTaskPrevAndOrStoredInst(
            String exporterID, StoreContext ctx, Date scheduledTime, boolean exportPreviousEntity) {
        if (exportPreviousEntity && ctx.getPreviousInstance() != null) {
            Series prevSer = ctx.getPreviousInstance().getSeries();
            createOrUpdateSeriesExportTask(
                    exporterID, prevSer.getStudy().getStudyInstanceUID(), prevSer.getSeriesInstanceUID(), scheduledTime);
        }
        createOrUpdateSeriesExportTask(exporterID, ctx.getStudyInstanceUID(), ctx.getSeriesInstanceUID(), scheduledTime);
    }

    private void createOrUpdateSeriesExportTask(
            String exporterID, String studyInstanceUID, String seriesInstanceUID, Date scheduledTime) {
        try {
            ExportTask task = em.createNamedQuery(
                    ExportTask.FIND_BY_EXPORTER_ID_AND_STUDY_IUID_AND_SERIES_IUID, ExportTask.class)
                    .setParameter(1, exporterID)
                    .setParameter(2, studyInstanceUID)
                    .setParameter(3, seriesInstanceUID)
                    .getSingleResult();
            task.setSopInstanceUID("*");
            task.setScheduledTime(scheduledTime);
        } catch (NoResultException nre) {
            ExportTask task = new ExportTask();
            task.setDeviceName(device.getDeviceName());
            task.setExporterID(exporterID);
            task.setStudyInstanceUID(studyInstanceUID);
            task.setSeriesInstanceUID(seriesInstanceUID);
            task.setSopInstanceUID("*");
            task.setScheduledTime(scheduledTime);
            em.persist(task);
        }
    }

    private void instanceExportTaskPrevAndOrStoredInst(
            String exporterID, StoreContext ctx, Date scheduledTime, boolean exportPreviousEntity) {
        if (exportPreviousEntity && ctx.getPreviousInstance() != null) {
            Instance i = ctx.getPreviousInstance();
            createOrUpdateInstanceExportTask(exporterID, i.getSeries().getStudy().getStudyInstanceUID(),
                    i.getSeries().getSeriesInstanceUID(), i.getSopInstanceUID(), scheduledTime);
        }
        createOrUpdateInstanceExportTask(
                exporterID, ctx.getStudyInstanceUID(), ctx.getSeriesInstanceUID(), ctx.getSopInstanceUID(), scheduledTime);
    }

    private void createOrUpdateInstanceExportTask(
            String exporterID, String studyInstanceUID, String seriesInstanceUID, String sopInstanceUID,
            Date scheduledTime) {
        try {
            ExportTask task = em.createNamedQuery(
                    ExportTask.FIND_BY_EXPORTER_ID_AND_STUDY_IUID_AND_SERIES_IUID_AND_SOP_IUID, ExportTask.class)
                    .setParameter(1, exporterID)
                    .setParameter(2, studyInstanceUID)
                    .setParameter(3, seriesInstanceUID)
                    .setParameter(4, sopInstanceUID)
                    .getSingleResult();
            task.setScheduledTime(scheduledTime);
        } catch (NoResultException nre) {
            ExportTask task = new ExportTask();
            task.setDeviceName(device.getDeviceName());
            task.setExporterID(exporterID);
            task.setStudyInstanceUID(studyInstanceUID);
            task.setSeriesInstanceUID(seriesInstanceUID);
            task.setSopInstanceUID(sopInstanceUID);
            task.setScheduledTime(scheduledTime);
            em.persist(task);
        }
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
            scheduleExportTask(
                    exportTask.getStudyInstanceUID(),
                    exportTask.getSeriesInstanceUID(),
                    exportTask.getSopInstanceUID(),
                    exporter,
                    exporter.getAETitle());
            em.remove(exportTask);
        }
        return resultList.size();
    }

    @Override
    public void scheduleExportTask(String studyUID, String seriesUID, String objectUID, ExporterDescriptor exporter,
                                   String aeTitle) {
        queueManager.scheduleMessage(exporter.getQueueName(),
                createMessage(studyUID, seriesUID, objectUID, exporter.getExporterID(), aeTitle));
    }

    private ObjectMessage createMessage(String studyUID, String seriesUID, String objectUID, String exporterID,
                                        String aeTitle) {
        ObjectMessage msg = queueManager.createObjectMessage("");
        try {
            msg.setStringProperty("StudyInstanceUID", studyUID);
            msg.setStringProperty("SeriesInstanceUID", seriesUID);
            msg.setStringProperty("SopInstanceUID", objectUID);
            msg.setStringProperty("ExporterID", exporterID);
            msg.setStringProperty("AETitle", aeTitle);
        } catch (JMSException e) {
            throw new JMSRuntimeException(e.getMessage(), e.getErrorCode(), e.getCause());
        }
        return msg;
    }
}
