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
 * Portions created by the Initial Developer are Copyright (C) 2017-2019
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

import javax.persistence.criteria.Expression;
import javax.persistence.Tuple;

import javax.persistence.criteria.Predicate;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.Device;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.event.QueueMessageEvent;
import org.dcm4chee.arc.export.mgt.ExportBatch;
import org.dcm4chee.arc.export.mgt.ExportManager;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.qmgt.IllegalTaskStateException;
import org.dcm4chee.arc.qmgt.QueueManager;
import org.dcm4chee.arc.qmgt.QueueSizeLimitExceededException;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.query.util.MatchTask;
import org.dcm4chee.arc.query.util.QueryBuilder;
import org.dcm4chee.arc.query.util.TaskQueryParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import javax.jms.JMSException;
import javax.jms.JMSRuntimeException;
import javax.jms.ObjectMessage;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.*;
import javax.persistence.metamodel.SingularAttribute;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Oct 2015
 */
@Stateless
public class ExportManagerEJB implements ExportManager {

    private static final Logger LOG = LoggerFactory.getLogger(ExportManagerEJB.class);
    
    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    @Inject
    private Device device;

    @Inject
    private QueryService queryService;

    @Inject
    private QueueManager queueManager;

    @Override
    public void createOrUpdateStudyExportTask(String exporterID, String studyIUID, Date scheduledTime) {
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

    @Override
    public void createOrUpdateSeriesExportTask(
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

    @Override
    public void createOrUpdateInstanceExportTask(
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
    public void scheduleStudyExportTasks(ExporterDescriptor exporter, HttpServletRequestInfo httpServletRequestInfo,
                                         String batchID, String... studyUIDs)
            throws QueueSizeLimitExceededException {
        for (String studyUID : studyUIDs)
            scheduleExportTask(studyUID, null, null, exporter, httpServletRequestInfo, batchID);
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
        LOG.info("Schedule {}", exportTask);
        QueueMessage queueMessage = queueManager.scheduleMessage(
                exporter.getQueueName(),
                createMessage(exportTask, httpServletRequestInfo),
                exporter.getPriority(),
                batchID, 0L);
        exportTask.setQueueMessage(queueMessage);
        try {
            Attributes attrs = queryService.queryExportTaskInfo(
                    exportTask.getStudyInstanceUID(),
                    exportTask.getSeriesInstanceUID(),
                    exportTask.getSopInstanceUID(),
                    device.getApplicationEntity(exporter.getAETitle(), true));
            if (attrs == null) {
                LOG.info("No Export Task Info found for {}", exportTask);
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
                    msg.setStringProperty("SOPInstanceUID", exportTask.getSopInstanceUID());
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
    public boolean deleteExportTask(Long pk, QueueMessageEvent queueEvent) {
        ExportTask task = em.find(ExportTask.class, pk);
        if (task == null)
            return false;

        QueueMessage queueMsg = task.getQueueMessage();
        if (queueMsg == null)
            em.remove(task);
        else
            queueManager.deleteTask(queueMsg.getMessageID(), queueEvent);

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
    public long cancelExportTasks(TaskQueryParam queueTaskQueryParam, TaskQueryParam exportTaskQueryParam) {
        return queueManager.cancelExportTasks(queueTaskQueryParam, exportTaskQueryParam);
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

        if (task.getQueueMessage() == null)
            throw new IllegalTaskStateException("Cannot reschedule task[pk=" + task.getPk() + "] with status TO SCHEDULE");

        task.setExporterID(exporter.getExporterID());
        queueManager.rescheduleTask(task.getQueueMessage().getMessageID(), exporter.getQueueName(), queueEvent);
        LOG.info("Reschedule {} to Exporter[id={}]", task, task.getExporterID());
    }

    @Override
    public List<String> listDistinctDeviceNames(TaskQueryParam exportTaskQueryParam) {
        return em.createQuery(
                select(ExportTask_.deviceName, exportTaskQueryParam).distinct(true))
                .getResultList();
    }

    @Override
    public List<ExportBatch> listExportBatches(
            TaskQueryParam queueBatchQueryParam, TaskQueryParam exportBatchQueryParam, int offset, int limit) {
        ListExportBatches listExportBatches = new ListExportBatches(queueBatchQueryParam, exportBatchQueryParam);
        TypedQuery<Tuple> query = em.createQuery(listExportBatches.query);
        if (offset > 0)
            query.setFirstResult(offset);
        if (limit > 0)
            query.setMaxResults(limit);

        return query.getResultStream().map(listExportBatches::toExportBatch).collect(Collectors.toList());
    }

    private Subquery<Long> statusSubquery(TaskQueryParam queueBatchQueryParam, TaskQueryParam exportBatchQueryParam,
                                          From<ExportTask, QueueMessage> queueMsg, QueueMessage.Status status) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<QueueMessage> query = cb.createQuery(QueueMessage.class);
        Subquery<Long> sq = query.subquery(Long.class);
        Root<ExportTask> exportTask = sq.from(ExportTask.class);
        Join<ExportTask, QueueMessage> queueMsg1 = sq.correlate(exportTask.join(ExportTask_.queueMessage));
        MatchTask matchTask = new MatchTask(cb);
        List<Predicate> predicates = matchTask.exportBatchPredicates(
                queueMsg1, exportTask, queueBatchQueryParam, exportBatchQueryParam);
        predicates.add(cb.equal(queueMsg1.get(QueueMessage_.batchID), queueMsg.get(QueueMessage_.batchID)));
        predicates.add(cb.equal(queueMsg1.get(QueueMessage_.status), status));
        sq.where(predicates.toArray(new Predicate[0]));
        sq.select(cb.count(exportTask));
        return sq;
    }

    private class ListExportBatches {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        final MatchTask matchTask = new MatchTask(cb);
        final CriteriaQuery<Tuple> query = cb.createTupleQuery();
        final Root<ExportTask> exportTask = query.from(ExportTask.class);
        final From<ExportTask, QueueMessage> queueMsg = exportTask.join(ExportTask_.queueMessage);
        final Expression<Date> minProcessingStartTime = cb.least(queueMsg.get(QueueMessage_.processingStartTime));
        final Expression<Date> maxProcessingStartTime = cb.greatest(queueMsg.get(QueueMessage_.processingStartTime));
        final Expression<Date> minProcessingEndTime = cb.least(queueMsg.get(QueueMessage_.processingEndTime));
        final Expression<Date> maxProcessingEndTime = cb.greatest(queueMsg.get(QueueMessage_.processingEndTime));
        final Expression<Date> minScheduledTime = cb.least(exportTask.get(ExportTask_.scheduledTime));
        final Expression<Date> maxScheduledTime = cb.greatest(exportTask.get(ExportTask_.scheduledTime));
        final Expression<Date> minCreatedTime = cb.least(exportTask.get(ExportTask_.createdTime));
        final Expression<Date> maxCreatedTime = cb.greatest(exportTask.get(ExportTask_.createdTime));
        final Expression<Date> minUpdatedTime = cb.least(exportTask.get(ExportTask_.updatedTime));
        final Expression<Date> maxUpdatedTime = cb.greatest(exportTask.get(ExportTask_.updatedTime));
        final Path<String> batchIDPath = queueMsg.get(QueueMessage_.batchID);
        final Expression<Long> completed;
        final Expression<Long> failed;
        final Expression<Long> warning;
        final Expression<Long> canceled;
        final Expression<Long> scheduled;
        final Expression<Long> inprocess;
        final TaskQueryParam queueBatchQueryParam;
        final TaskQueryParam exportBatchQueryParam;

        ListExportBatches(TaskQueryParam queueBatchQueryParam, TaskQueryParam exportBatchQueryParam) {
            this.queueBatchQueryParam = queueBatchQueryParam;
            this.exportBatchQueryParam = exportBatchQueryParam;
            this.completed = statusSubquery(queueBatchQueryParam, exportBatchQueryParam,
                    queueMsg, QueueMessage.Status.COMPLETED).getSelection();
            this.failed = statusSubquery(queueBatchQueryParam, exportBatchQueryParam,
                    queueMsg, QueueMessage.Status.FAILED).getSelection();
            this.warning = statusSubquery(queueBatchQueryParam, exportBatchQueryParam,
                    queueMsg, QueueMessage.Status.WARNING).getSelection();
            this.canceled = statusSubquery(queueBatchQueryParam, exportBatchQueryParam,
                    queueMsg, QueueMessage.Status.CANCELED).getSelection();
            this.scheduled = statusSubquery(queueBatchQueryParam, exportBatchQueryParam,
                    queueMsg, QueueMessage.Status.SCHEDULED).getSelection();
            this.inprocess = statusSubquery(queueBatchQueryParam, exportBatchQueryParam,
                    queueMsg, QueueMessage.Status.IN_PROCESS).getSelection();
            query.multiselect(batchIDPath,
                    minProcessingStartTime, maxProcessingStartTime,
                    minProcessingEndTime, maxProcessingEndTime,
                    minScheduledTime, maxScheduledTime,
                    minCreatedTime, maxCreatedTime,
                    minUpdatedTime, maxUpdatedTime,
                    completed, failed, warning, canceled, scheduled, inprocess);
            query.groupBy(queueMsg.get(QueueMessage_.batchID));
            MatchTask matchTask = new MatchTask(cb);
            List<Predicate> predicates = matchTask.exportBatchPredicates(
                    queueMsg, exportTask, queueBatchQueryParam, exportBatchQueryParam);
            if (!predicates.isEmpty())
                query.where(predicates.toArray(new Predicate[0]));
            if (exportBatchQueryParam.getOrderBy() != null)
                query.orderBy(matchTask.exportBatchOrder(exportBatchQueryParam.getOrderBy(), exportTask));
        }

        ExportBatch toExportBatch(Tuple tuple) {
            String batchID = tuple.get(batchIDPath);
            ExportBatch exportBatch = new ExportBatch(batchID);
            exportBatch.setProcessingStartTimeRange(
                    tuple.get(maxProcessingStartTime),
                    tuple.get(maxProcessingStartTime));
            exportBatch.setProcessingEndTimeRange(
                    tuple.get(minProcessingEndTime),
                    tuple.get(maxProcessingEndTime));
            exportBatch.setScheduledTimeRange(
                    tuple.get(minScheduledTime),
                    tuple.get(maxScheduledTime));
            exportBatch.setCreatedTimeRange(
                    tuple.get(minCreatedTime),
                    tuple.get(maxCreatedTime));
            exportBatch.setUpdatedTimeRange(
                    tuple.get(minUpdatedTime),
                    tuple.get(maxUpdatedTime));

            CriteriaQuery<String> distinct = cb.createQuery(String.class).distinct(true);
            Root<ExportTask> exportTask = distinct.from(ExportTask.class);
            From<ExportTask, QueueMessage> queueMsg = exportTask.join(ExportTask_.queueMessage);
            distinct.where(predicates(queueMsg, exportTask, batchID));
            exportBatch.setDeviceNames(select(distinct, queueMsg.get(QueueMessage_.deviceName)));
            exportBatch.setExporterIDs(select(distinct, exportTask.get(ExportTask_.exporterID)));
            exportBatch.setCompleted(tuple.get(completed));
            exportBatch.setCanceled(tuple.get(canceled));
            exportBatch.setWarning(tuple.get(warning));
            exportBatch.setFailed(tuple.get(failed));
            exportBatch.setScheduled(tuple.get(scheduled));
            exportBatch.setInProcess(tuple.get(inprocess));
            return exportBatch;
        }

        private Predicate[] predicates(Path<QueueMessage> queueMsg, Path<ExportTask> exportTask, String batchID) {
            List<Predicate> predicates = matchTask.exportBatchPredicates(
                    queueMsg, exportTask, queueBatchQueryParam, exportBatchQueryParam);
            predicates.add(cb.equal(queueMsg.get(QueueMessage_.batchID), batchID));
            return predicates.toArray(new Predicate[0]);
        }

        private List<String> select(CriteriaQuery<String> query, Path<String> path) {
            return em.createQuery(query.select(path)).getResultList();
        }
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Iterator<ExportTask> listExportTasks(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam exportTaskQueryParam, int offset, int limit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        MatchTask matchTask = new MatchTask(cb);
        CriteriaQuery<ExportTask> q = cb.createQuery(ExportTask.class);
        Root<ExportTask> exportTask = q.from(ExportTask.class);

        List<Predicate> predicates = predicates(exportTask, matchTask, queueTaskQueryParam, exportTaskQueryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));

        if (exportTaskQueryParam.getOrderBy() != null)
            q.orderBy(matchTask.exportTaskOrder(exportTaskQueryParam.getOrderBy(), exportTask));
        TypedQuery<ExportTask> query = em.createQuery(q);
        if (offset > 0)
            query.setFirstResult(offset);
        if (limit > 0)
            query.setMaxResults(limit);
        return query.getResultStream().iterator();
    }

    public List<Tuple> exportTaskPksAndExporterIDs(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam exportTaskQueryParam, int limit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        MatchTask matchTask = new MatchTask(cb);
        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        Root<ExportTask> exportTask = q.from(ExportTask.class);
        From<ExportTask, QueueMessage> queueMsg = exportTask.join(ExportTask_.queueMessage);

        q.multiselect(exportTask.get(ExportTask_.pk), exportTask.get(ExportTask_.exporterID));

        List<Predicate> predicates = matchTask.exportPredicates(queueMsg, exportTask, queueTaskQueryParam, exportTaskQueryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));

        TypedQuery<Tuple> query = em.createQuery(q);
        if (limit > 0)
            query.setMaxResults(limit);

        return query.getResultList();
    }

    public long countTasks(TaskQueryParam queueTaskQueryParam, TaskQueryParam exportTaskQueryParam) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        MatchTask matchTask = new MatchTask(cb);
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        Root<ExportTask> exportTask = q.from(ExportTask.class);

        List<Predicate> predicates = predicates(exportTask, matchTask, queueTaskQueryParam, exportTaskQueryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));

        return QueryBuilder.unbox(em.createQuery(q.select(cb.count(exportTask))).getSingleResult(), 0L);
    }

    private List<Predicate> predicates(Root<ExportTask> exportTask, MatchTask matchTask,
                                       TaskQueryParam queueTaskQueryParam, TaskQueryParam exportTaskQueryParam) {
        List<Predicate> predicates = new ArrayList<>();
        QueueMessage.Status status = queueTaskQueryParam.getStatus();
        if (status == QueueMessage.Status.TO_SCHEDULE) {
            matchTask.matchExportTask(predicates, exportTaskQueryParam, exportTask);
            predicates.add(exportTask.get(ExportTask_.queueMessage).isNull());
        } else {
            From<ExportTask, QueueMessage> queueMsg = exportTask.join(ExportTask_.queueMessage,
                    status == null && queueTaskQueryParam.getBatchID() == null
                            ? JoinType.LEFT : JoinType.INNER);
            predicates = matchTask.exportPredicates(queueMsg, exportTask, queueTaskQueryParam, exportTaskQueryParam);
        }
        return predicates;
    }
    
    public int deleteTasks(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam exportTaskQueryParam, int deleteTasksFetchSize) {
        QueueMessage.Status status = queueTaskQueryParam.getStatus();
        if (status == QueueMessage.Status.TO_SCHEDULE)
            return deleteToSchedule(exportTaskQueryParam);

        if (status == null && queueTaskQueryParam.getBatchID() == null)
            return deleteReferencedTasks(queueTaskQueryParam, exportTaskQueryParam, deleteTasksFetchSize)
                    + deleteToSchedule(exportTaskQueryParam);

        return deleteReferencedTasks(queueTaskQueryParam, exportTaskQueryParam, deleteTasksFetchSize);
    }

    private int deleteToSchedule(TaskQueryParam exportTaskQueryParam) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaDelete<ExportTask> q = cb.createCriteriaDelete(ExportTask.class);
        Root<ExportTask> exportTask = q.from(ExportTask.class);
        List<Predicate> predicates = new ArrayList<>();
        new MatchTask(cb).matchExportTask(predicates, exportTaskQueryParam, exportTask);
        predicates.add(exportTask.get(ExportTask_.queueMessage).isNull());
        q.where(predicates.toArray(new Predicate[0]));
        return em.createQuery(q).executeUpdate();
    }

    private int deleteReferencedTasks(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam exportTaskQueryParam, int deleteTasksFetchSize) {
        List<String> referencedQueueMsgIDs = em.createQuery(
                select(QueueMessage_.messageID, queueTaskQueryParam, exportTaskQueryParam))
                .setMaxResults(deleteTasksFetchSize)
                .getResultList();

        referencedQueueMsgIDs.forEach(queueMsgID -> queueManager.deleteTask(queueMsgID, null));
        return referencedQueueMsgIDs.size();
    }

    private CriteriaQuery<String> select(
            SingularAttribute<ExportTask, String> attribute, TaskQueryParam exportTaskQueryParam) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<String> q = cb.createQuery(String.class);
        Root<ExportTask> exportTask = q.from(ExportTask.class);
        List<Predicate> predicates = new ArrayList<>();
        new MatchTask(cb).matchExportTask(predicates, exportTaskQueryParam, exportTask);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));
        return q.select(exportTask.get(attribute));
    }

    private CriteriaQuery<String> select(SingularAttribute<QueueMessage, String> attribute,
                                         TaskQueryParam queueTaskQueryParam, TaskQueryParam exportTaskQueryParam) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<String> q = cb.createQuery(String.class);
        Root<ExportTask> exportTask = q.from(ExportTask.class);
        From<ExportTask, QueueMessage> queueMsg = exportTask.join(ExportTask_.queueMessage);
        List<Predicate> predicates = new MatchTask(cb).exportPredicates(
                queueMsg, exportTask, queueTaskQueryParam, exportTaskQueryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));
        return q.select(queueMsg.get(attribute));
    }
}