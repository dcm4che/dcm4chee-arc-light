/*
 * *** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * J4Care.
 * Portions created by the Initial Developer are Copyright (C) 2017
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * *** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.export.mgt.impl;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.jpa.hibernate.HibernateDeleteClause;
import com.querydsl.jpa.hibernate.HibernateQuery;
import org.dcm4che3.data.*;
import org.dcm4che3.net.Device;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.entity.ExportTask;
import org.dcm4chee.arc.entity.QExportTask;
import org.dcm4chee.arc.entity.QQueueMessage;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.event.QueueMessageEvent;
import org.dcm4chee.arc.export.mgt.*;
import org.dcm4chee.arc.qmgt.*;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreSession;
import org.hibernate.Session;
import org.hibernate.StatelessSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.jms.JMSException;
import javax.jms.JMSRuntimeException;
import javax.jms.ObjectMessage;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import java.util.*;

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
            QQueueMessage.queueMessage.processingStartTime.min(),
            QQueueMessage.queueMessage.processingStartTime.max(),
            QQueueMessage.queueMessage.processingEndTime.min(),
            QQueueMessage.queueMessage.processingEndTime.max(),
            QExportTask.exportTask.createdTime.min(),
            QExportTask.exportTask.createdTime.max(),
            QExportTask.exportTask.updatedTime.min(),
            QExportTask.exportTask.updatedTime.max(),
            QExportTask.exportTask.scheduledTime.min(),
            QExportTask.exportTask.scheduledTime.max(),
            QQueueMessage.queueMessage.batchID
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
            updateExportTask(task, "*", "*", scheduledTime);
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
            updateExportTask(task, seriesIUID, "*", scheduledTime);
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
            updateExportTask(task, seriesIUID, sopIUID, scheduledTime);
        } catch (NoResultException nre) {
            createExportTask(exporterID, studyIUID, seriesIUID, sopIUID, scheduledTime);
        }
    }

    private void updateExportTask(ExportTask task, String seriesIUID, String sopIUID, Date scheduledTime) {
        task.setDeviceName(device.getDeviceName());
        task.setSeriesInstanceUID(seriesIUID);
        task.setSopInstanceUID(sopIUID);
        task.setScheduledTime(scheduledTime);
        QueueMessage queueMessage = task.getQueueMessage();
        if (queueMessage != null) {
            if (queueMessage.getStatus() == QueueMessage.Status.SCHEDULED) {
                queueMessage.setStatus(QueueMessage.Status.CANCELED);
                LOG.info("Cancel processing of Task[id={}] at Queue {}",
                        queueMessage.getMessageID(), queueMessage.getQueueName());
            }
            task.setQueueMessage(null);
        }
        LOG.debug("Update {}", task);
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
        em.persist(task);
        LOG.info("Create {}", task);
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
        ArchiveDeviceExtension arcDev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
        int count = 0;
        for (ExportTask exportTask : resultList) {
            ExporterDescriptor exporter = arcDev.getExporterDescriptor(exportTask.getExporterID());
            try {
                scheduleExportTask(exportTask, exporter, null, null);
            } catch (QueueSizeLimitExceededException e) {
                LOG.info(e.getLocalizedMessage() + " - retry to schedule Export Tasks");
                return count;
            }
            count++;
        }
        return count;
    }

    @Override
    public void scheduleExportTask(String studyUID, String seriesUID, String objectUID, ExporterDescriptor exporter,
                                   HttpServletRequestInfo httpServletRequestInfo, String batchID)
            throws QueueSizeLimitExceededException {
        ExportTask task = createExportTask(
                exporter.getExporterID(),
                studyUID,
                StringUtils.maskNull(seriesUID, "*"),
                StringUtils.maskNull(objectUID, "*"),
                new Date());
        scheduleExportTask(task, exporter, httpServletRequestInfo, batchID);
    }

    @Override
    public boolean scheduleStudyExport(
            String studyUID, ExporterDescriptor exporter, Date notExportedAfter, String batchID) {
        try {
            ExportTask prevTask = em.createNamedQuery(ExportTask.FIND_STUDY_EXPORT_AFTER, ExportTask.class)
                    .setParameter(1, notExportedAfter)
                    .setParameter(2, exporter.getExporterID())
                    .setParameter(3, studyUID)
                    .getSingleResult();
            LOG.info("Previous {} found - suppress duplicate Export", prevTask);
            return false;
        } catch (NoResultException e) {
        }

        ExportTask task = createExportTask(exporter.getExporterID(), studyUID, "*", "*", new Date());
        try {
            scheduleExportTask(task, exporter, null, batchID);
        } catch (QueueSizeLimitExceededException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    private void scheduleExportTask(ExportTask exportTask, ExporterDescriptor exporter,
                                    HttpServletRequestInfo httpServletRequestInfo, String batchID)
            throws QueueSizeLimitExceededException {
        QueueMessage queueMessage = queueManager.scheduleMessage(
                exporter.getQueueName(),
                createMessage(exportTask, httpServletRequestInfo),
                exporter.getPriority(),
                batchID);
        exportTask.setQueueMessage(queueMessage);
        try {
            Attributes attrs = queryService.queryExportTaskInfo(
                    exportTask.getStudyInstanceUID(),
                    exportTask.getSeriesInstanceUID(),
                    exportTask.getSopInstanceUID(),
                    device.getApplicationEntity(exporter.getAETitle(), true));
            if (attrs == null) {
                LOG.info("No result found for export task with [pk={}, studyUID={}, seriesUID={}, objectUID={}]",
                        exportTask.getPk(), exportTask.getStudyInstanceUID(), exportTask.getSeriesInstanceUID(), exportTask.getSopInstanceUID());
                return;
            }
            exportTask.setModalities(attrs.getStrings(Tag.ModalitiesInStudy));
            exportTask.setNumberOfInstances(
                    Integer.valueOf(attrs.getInt(Tag.NumberOfStudyRelatedInstances, -1)));
        } catch (Exception e) {
            LOG.warn("Failed to query Export Task Info for {} - ", exportTask, e);
        }
    }

    private ObjectMessage createMessage(ExportTask exportTask, HttpServletRequestInfo httpServletRequestInfo) {
        ObjectMessage msg = queueManager.createObjectMessage(exportTask.getPk());
        try {
            msg.setStringProperty("StudyInstanceUID", exportTask.getStudyInstanceUID());
            if (!exportTask.getSeriesInstanceUID().equals("*")) {
                msg.setStringProperty("SeriesInstanceUID", exportTask.getSeriesInstanceUID());
                if (!exportTask.getSopInstanceUID().equals("*")) {
                    msg.setStringProperty("SopInstanceUID", exportTask.getSopInstanceUID());
                }
            }
            msg.setStringProperty("ExporterID", exportTask.getExporterID());
            if (httpServletRequestInfo != null)
                httpServletRequestInfo.copyTo(msg);
        } catch (JMSException e) {
            throw new JMSRuntimeException(e.getMessage(), e.getErrorCode(), e.getCause());
        }
        return msg;
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public ExportTaskQuery listExportTasks(QueueMessage.Status status, Predicate matchQueueMessage, Predicate matchExportTask,
                                           OrderSpecifier<Date> order, int offset, int limit) {
        return new ExportTaskQueryImpl(status,
                openStatelessSession(), queryFetchSize(), matchQueueMessage, matchExportTask, order, offset, limit);
    }

    @Override
    public long countExportTasks(QueueMessage.Status status, Predicate matchQueueMessage, Predicate matchExportTask) {
        return createQuery(status, matchQueueMessage, matchExportTask).fetchCount();
    }

    private HibernateQuery<QueueMessage> createQuery(Predicate matchQueueMessage) {
        return new HibernateQuery<QueueMessage>(em.unwrap(Session.class))
                .from(QQueueMessage.queueMessage)
                .where(matchQueueMessage);
    }

    private HibernateQuery<ExportTask> createQuery(Predicate matchQueueMessage, Predicate matchExportTask) {
        return new HibernateQuery<ExportTask>(em.unwrap(Session.class))
                .from(QExportTask.exportTask)
                .leftJoin(QExportTask.exportTask.queueMessage, QQueueMessage.queueMessage)
                .where(matchExportTask, QExportTask.exportTask.queueMessage.in(createQuery(matchQueueMessage)));
    }

    private HibernateQuery<ExportTask> createQuery(QueueMessage.Status status, Predicate matchQueueMessage, Predicate matchExportTask) {
        return new HibernateQuery<ExportTask>(em.unwrap(Session.class))
                .from(QExportTask.exportTask)
                .leftJoin(QExportTask.exportTask.queueMessage, QQueueMessage.queueMessage)
                .where(matchExportTask, queuePredicate(status, createQuery(matchQueueMessage)));
    }

    private Predicate queuePredicate(QueueMessage.Status status, HibernateQuery<QueueMessage> queueMsgQuery) {
        return status == QueueMessage.Status.TO_SCHEDULE
                    ? QExportTask.exportTask.queueMessage.isNull()
                    : status == null
                        ? ExpressionUtils.or(QExportTask.exportTask.queueMessage.isNull(), QExportTask.exportTask.queueMessage.in(queueMsgQuery))
                        : QExportTask.exportTask.queueMessage.in(queueMsgQuery);
    }

    @Override
    public boolean deleteExportTask(Long pk, QueueMessageEvent queueEvent) {
        ExportTask task = em.find(ExportTask.class, pk);
        if (task == null)
            return false;

        queueEvent.setQueueMsg(task.getQueueMessage());
        em.remove(task);
        LOG.info("Delete {}", task);
        return true;
    }

    @Override
    public boolean cancelExportTask(Long pk, QueueMessageEvent queueEvent) throws IllegalTaskStateException {
        ExportTask task = em.find(ExportTask.class, pk);
        if (task == null)
            return false;

        QueueMessage queueMessage = task.getQueueMessage();
        if (queueMessage == null)
            throw new IllegalTaskStateException("Cannot cancel Task with status: 'TO SCHEDULE'");

        queueManager.cancelTask(queueMessage.getMessageID(), queueEvent);
        LOG.info("Cancel {}", task);
        return true;
    }

    @Override
    public long cancelExportTasks(Predicate matchQueueMessage, Predicate matchExportTask, QueueMessage.Status prev)
            throws IllegalTaskStateException {
        return queueManager.cancelExportTasks(matchQueueMessage, matchExportTask, prev);
    }

    @Override
    public String findDeviceNameByPk(Long pk) {
        try {
            return em.createNamedQuery(ExportTask.FIND_DEVICE_BY_PK, String.class)
                    .setParameter(1, pk)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    @Override
    public void rescheduleExportTask(Long pk, ExporterDescriptor exporter, QueueMessageEvent queueEvent)
            throws IllegalTaskStateException {
        ExportTask task = em.find(ExportTask.class, pk);
        if (task == null)
            return;

        if (task.getQueueMessage() != null)
            rescheduleExportTask(task, exporter, queueEvent);
        else
            throw new IllegalTaskStateException("Cannot reschedule task[pk=" + task.getPk() + "] with status TO SCHEDULE");
    }

    @Override
    public void rescheduleExportTask(ExportTask task, ExporterDescriptor exporter, QueueMessageEvent queueEvent) {
        task.setExporterID(exporter.getExporterID());
        queueManager.rescheduleTask(task.getQueueMessage().getMessageID(), exporter.getQueueName(), queueEvent);
        LOG.info("Reschedule {} to Exporter[id={}]", task, task.getExporterID());
    }

    @Override
    public int deleteTasks(QueueMessage.Status status, Predicate matchQueueMessage, Predicate matchExportTask) {
        HibernateQuery<ExportTask> exportTaskQuery = createQuery(status, matchQueueMessage, matchExportTask);
        List<Long> refQueuePks = exportTaskQuery.select(QExportTask.exportTask.queueMessage.pk).fetch();

        int count = (int) new HibernateDeleteClause(em.unwrap(Session.class), QExportTask.exportTask)
                .where(QExportTask.exportTask.pk.in(exportTaskQuery.select(QExportTask.exportTask.pk)))
                .execute();

        new HibernateDeleteClause(em.unwrap(Session.class), QQueueMessage.queueMessage)
                .where(QQueueMessage.queueMessage.pk.in(refQueuePks)).execute();

        return count;
    }

    @Override
    public List<String> listDistinctDeviceNames(Predicate matchQueueMessage, Predicate matchExportTask) {
        return createQuery(matchQueueMessage, matchExportTask)
                .select(QQueueMessage.queueMessage.deviceName)
                .distinct()
                .fetch();
    }

    @Override
    public List<ExportBatch> listExportBatches(Predicate matchQueueBatch, Predicate matchExportBatch,
                                               OrderSpecifier<Date> order, int offset, int limit) {
        HibernateQuery<ExportTask> exportTaskQuery = createQuery(matchQueueBatch, matchExportBatch);
        if (limit > 0)
            exportTaskQuery.limit(limit);
        if (offset > 0)
            exportTaskQuery.offset(offset);

        List<Tuple> batches = exportTaskQuery.select(SELECT)
                                .groupBy(QQueueMessage.queueMessage.batchID)
                                .orderBy(order)
                                .fetch();

        List<ExportBatch> exportBatches = new ArrayList<>();
        for (Tuple batch : batches) {
            ExportBatch exportBatch = new ExportBatch();
            String batchID = batch.get(QQueueMessage.queueMessage.batchID);
            exportBatch.setBatchID(batchID);
            exportBatch.setCreatedTimeRange(
                    batch.get(QExportTask.exportTask.createdTime.min()),
                    batch.get(QExportTask.exportTask.createdTime.max()));
            exportBatch.setUpdatedTimeRange(
                    batch.get(QExportTask.exportTask.updatedTime.min()),
                    batch.get(QExportTask.exportTask.updatedTime.max()));
            exportBatch.setScheduledTimeRange(
                    batch.get(QExportTask.exportTask.scheduledTime.min()),
                    batch.get(QExportTask.exportTask.scheduledTime.max()));
            exportBatch.setProcessingStartTimeRange(
                    batch.get(QQueueMessage.queueMessage.processingStartTime.min()),
                    batch.get(QQueueMessage.queueMessage.processingStartTime.max()));
            exportBatch.setProcessingEndTimeRange(
                    batch.get(QQueueMessage.queueMessage.processingEndTime.min()),
                    batch.get(QQueueMessage.queueMessage.processingEndTime.max()));

            exportBatch.setDeviceNames(
                    batchIDQuery(batchID)
                        .select(QExportTask.exportTask.deviceName)
                        .distinct()
                        .orderBy(QExportTask.exportTask.deviceName.asc())
                        .fetch());
            exportBatch.setExporterIDs(
                    batchIDQuery(batchID)
                        .select(QExportTask.exportTask.exporterID)
                        .distinct()
                        .orderBy(QExportTask.exportTask.exporterID.asc())
                        .fetch());

            exportBatch.setCompleted(
                    batchIDQuery(batchID)
                        .where(QQueueMessage.queueMessage.status.eq(QueueMessage.Status.COMPLETED))
                        .fetchCount());
            exportBatch.setCanceled(
                    batchIDQuery(batchID)
                        .where(QQueueMessage.queueMessage.status.eq(QueueMessage.Status.CANCELED))
                        .fetchCount());
            exportBatch.setWarning(
                    batchIDQuery(batchID)
                        .where(QQueueMessage.queueMessage.status.eq(QueueMessage.Status.WARNING))
                        .fetchCount());
            exportBatch.setFailed(
                    batchIDQuery(batchID)
                        .where(QQueueMessage.queueMessage.status.eq(QueueMessage.Status.FAILED))
                        .fetchCount());
            exportBatch.setScheduled(
                    batchIDQuery(batchID)
                        .where(QQueueMessage.queueMessage.status.eq(QueueMessage.Status.SCHEDULED))
                        .fetchCount());
            exportBatch.setInProcess(
                    batchIDQuery(batchID)
                        .where(QQueueMessage.queueMessage.status.eq(QueueMessage.Status.IN_PROCESS))
                        .fetchCount());

            exportBatches.add(exportBatch);
        }
        return exportBatches;
    }

    private HibernateQuery<ExportTask> batchIDQuery(String batchID) {
        return new HibernateQuery<ExportTask>(em.unwrap(Session.class))
                .from(QExportTask.exportTask)
                .leftJoin(QExportTask.exportTask.queueMessage, QQueueMessage.queueMessage)
                .where(QQueueMessage.queueMessage.batchID.eq(batchID));
    }

    private StatelessSession openStatelessSession() {
        return em.unwrap(Session.class).getSessionFactory().openStatelessSession();
    }

    private int queryFetchSize() {
        return device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class).getQueryFetchSize();
    }
}
