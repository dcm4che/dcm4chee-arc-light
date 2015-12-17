package org.dcm4chee.arc.export.mgt.impl;

import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.entity.ExportTask;
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
public class ExportManagerEJB {

    static final Logger LOG = LoggerFactory.getLogger(ExportManagerEJB.class);

    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    @Inject
    private Device device;

    @Inject
    private QueueManager queueManager;

    public void onStore(@Observes StoreContext ctx) {
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
            ExporterDescriptor desc = arcDev.getExporterDescriptor(exporterID);
            if (desc == null) {
                LOG.warn("No Configuration for Exporter with ID: {} referenced by {} - no export scheduled",
                        exporterID, rule);
                continue;
            }
            Date scheduledTime = scheduledTime(now, rule.getExportDelay(), desc.getSchedules());
            switch (rule.getEntity()) {
                case Study:
                    createOrUpdateStudyExportTask(exporterID, ctx.getStudyInstanceUID(), scheduledTime);
                    break;
                case Series:
                    createOrUpdateSeriesExportTask(exporterID, ctx.getStudyInstanceUID(),
                            ctx.getSeriesInstanceUID(), scheduledTime);
                    break;
                case Instance:
                    createOrUpdateInstanceExportTask(exporterID, ctx.getStudyInstanceUID(),
                            ctx.getSeriesInstanceUID(), ctx.getSopInstanceUID(), scheduledTime);
                    break;
            }
        }
    }

    private void createOrUpdateStudyExportTask(String exporterID, String studyInstanceUID, Date scheduledTime) {
        try {
            ExportTask task = em.createNamedQuery(ExportTask.FIND_BY_EXPORTER_ID_AND_STUDY_IUID, ExportTask.class)
                    .setParameter(1, exporterID)
                    .setParameter(2, studyInstanceUID)
                    .getSingleResult();
            task.setSeriesInstanceUID("*");
            task.setSopInstanceUID("*");
            task.setScheduledTime(scheduledTime);
        } catch (NoResultException nre) {
            ExportTask task = new ExportTask();
            task.setExporterID(exporterID);
            task.setStudyInstanceUID(studyInstanceUID);
            task.setSeriesInstanceUID("*");
            task.setSopInstanceUID("*");
            task.setScheduledTime(scheduledTime);
            em.persist(task);
        }
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
            task.setExporterID(exporterID);
            task.setStudyInstanceUID(studyInstanceUID);
            task.setSeriesInstanceUID(seriesInstanceUID);
            task.setSopInstanceUID("*");
            task.setScheduledTime(scheduledTime);
            em.persist(task);
        }
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

    public int scheduleExportTasks(int fetchSize) {
        final List<ExportTask> resultList = em.createNamedQuery(
                ExportTask.FIND_SCHEDULED, ExportTask.class)
                .setMaxResults(fetchSize)
                .getResultList();
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        for (ExportTask exportTask : resultList) {
            ExporterDescriptor desc = arcDev.getExporterDescriptor(exportTask.getExporterID());
            queueManager.scheduleMessage(desc.getQueueName(), createMessage(exportTask));
            em.remove(exportTask);
        }
        return resultList.size();
    }

    private ObjectMessage createMessage(ExportTask exportTask) {
        ObjectMessage msg = queueManager.createObjectMessage("");
        try {
            msg.setStringProperty("ExporterID", exportTask.getExporterID());
            msg.setStringProperty("StudyInstanceUID", exportTask.getStudyInstanceUID());
            msg.setStringProperty("SeriesInstanceUID", exportTask.getSeriesInstanceUID());
            msg.setStringProperty("SopInstanceUID", exportTask.getSopInstanceUID());
        } catch (JMSException e) {
            throw new JMSRuntimeException(e.getMessage(), e.getErrorCode(), e.getCause());
        }
        return msg;
    }
}
