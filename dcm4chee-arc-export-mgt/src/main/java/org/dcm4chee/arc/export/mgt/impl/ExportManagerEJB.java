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

import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.event.QueueMessageEvent;
import org.dcm4chee.arc.export.mgt.ExportBatch;
import org.dcm4chee.arc.export.mgt.ExportManager;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.qmgt.IllegalTaskStateException;
import org.dcm4chee.arc.qmgt.QueueManager;
import org.dcm4chee.arc.query.util.MatchTask;
import org.dcm4chee.arc.query.util.QueryBuilder;
import org.dcm4chee.arc.query.util.TaskQueryParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.persistence.*;
import javax.persistence.criteria.*;
import javax.persistence.metamodel.SingularAttribute;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
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
    private QueueManager queueManager;

    @Override
    public void createOrUpdateStudyExportTask(String deviceName, ExporterDescriptor exporterDesc,
                                              String studyIUID, Date scheduledTime) {
        createOrUpdateStudyExportTask(deviceName, exporterDesc, studyIUID, null, scheduledTime);
    }

    private void createOrUpdateStudyExportTask(String deviceName, ExporterDescriptor exporterDesc,
                                               String studyIUID, String batchID, Date scheduledTime) {
        try {
            Task task = em.createNamedQuery(Task.FIND_BY_EXPORTER_ID_AND_STUDY_IUID, Task.class)
                    .setParameter(1, exporterDesc.getExporterID())
                    .setParameter(2, studyIUID)
                    .setParameter(3, Task.Status.SCHEDULED)
                    .getSingleResult();
            updateExportTask(deviceName, task, "*", "*", scheduledTime);
        } catch (NoResultException nre) {
            createExportTask(deviceName, exporterDesc,
                    studyIUID, "*", "*",
                    batchID, scheduledTime, null);
        }
    }

    @Override
    public void createOrUpdateSeriesExportTask(String deviceName, ExporterDescriptor exporterDesc,
                                               String studyIUID, String seriesIUID,
                                               Date scheduledTime) {
        try {
            Task task = em.createNamedQuery(
                    Task.FIND_BY_EXPORTER_ID_AND_STUDY_IUID_AND_SERIES_IUID, Task.class)
                    .setParameter(1, exporterDesc.getExporterID())
                    .setParameter(2, studyIUID)
                    .setParameter(3, seriesIUID)
                    .setParameter(4, Task.Status.SCHEDULED)
                    .getSingleResult();
            updateExportTask(deviceName, task, seriesIUID, "*", scheduledTime);
        } catch (NoResultException nre) {
            createExportTask(deviceName, exporterDesc,
                    studyIUID, seriesIUID, "*",
                    null, scheduledTime, null);
        }
    }

    @Override
    public void createOrUpdateInstanceExportTask(String deviceName, ExporterDescriptor exporterDesc,
                                                 String studyIUID, String seriesIUID, String sopIUID,
                                                 Date scheduledTime) {
        try {
            Task task = em.createNamedQuery(
                    Task.FIND_BY_EXPORTER_ID_AND_STUDY_IUID_AND_SERIES_IUID_AND_SOP_IUID, Task.class)
                    .setParameter(1, exporterDesc.getExporterID())
                    .setParameter(2, studyIUID)
                    .setParameter(3, seriesIUID)
                    .setParameter(4, sopIUID)
                    .setParameter(5, Task.Status.SCHEDULED)
                    .getSingleResult();
            updateExportTask(deviceName, task, seriesIUID, sopIUID, scheduledTime);
        } catch (NoResultException nre) {
            createExportTask(deviceName, exporterDesc,
                    studyIUID, seriesIUID, sopIUID,
                    null, scheduledTime, null);
        }
    }

    private void updateExportTask(String deviceName, Task task, String seriesIUID, String sopIUID, Date scheduledTime) {
        task.setDeviceName(deviceName);
        if (!task.getSeriesInstanceUID().equals("*"))
            task.setSeriesInstanceUID(seriesIUID);
        if (!task.getSOPInstanceUID().equals("*"))
            task.setSOPInstanceUID(sopIUID);
        if (task.getScheduledTime() != null && task.getScheduledTime().before(scheduledTime))
            task.setScheduledTime(scheduledTime);
        LOG.debug("Update {}", task);
    }

    @Override
    public Task createExportTask(String deviceName, ExporterDescriptor exporterDesc,
                                 String studyIUID, String seriesIUID, String sopIUID,
                                 String batchID, Date scheduledTime,
                                 HttpServletRequestInfo httpServletRequestInfo) {
        Task task = new Task();
        task.setDeviceName(deviceName);
        task.setQueueName(exporterDesc.getQueueName());
        task.setType(Task.Type.EXPORT);
        if (httpServletRequestInfo != null) {
            task.setRequesterUserID(httpServletRequestInfo.requesterUserID);
            task.setRequesterHost(httpServletRequestInfo.requesterHost);
            task.setRequestURI(httpServletRequestInfo.requestURI);
        }
        task.setStatus(Task.Status.SCHEDULED);
        task.setBatchID(batchID);
        task.setExporterID(exporterDesc.getExporterID());
        task.setLocalAET(exporterDesc.getAETitle());
        task.setStudyInstanceUID(studyIUID);
        task.setSeriesInstanceUID(seriesIUID);
        task.setSOPInstanceUID(sopIUID);
        task.setScheduledTime(scheduledTime);
        em.persist(task);
        LOG.info("Create {}", task);
        return task;
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<Long> findExportTasksToSchedule(int fetchSize) {
        return em.createNamedQuery(
                ExportTask.FIND_SCHEDULED_BY_DEVICE_NAME, Long.class)
                .setParameter(1, device.getDeviceName())
                .setMaxResults(fetchSize)
                .getResultList();
    }

    @Override
    public boolean scheduleExportTask(Long pk) {
        ExportTask exportTask = em.find(ExportTask.class, pk);
        ArchiveDeviceExtension arcDev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
        ExporterDescriptor exporter = arcDev.getExporterDescriptor(exportTask.getExporterID());
        scheduleExportTask(exportTask, exporter, null, null);
        return true;
    }

    @Override
    public boolean scheduleStudyExport(
            String studyUID, ExporterDescriptor exporter,
            Date notExportedAfter, String batchID, Date scheduledTime) {
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

        createExportTask(
                device.getDeviceName(),
                exporter,
                studyUID,
                "*",
                "*",
                batchID,
                scheduledTime,
                null);
        return true;
    }

    private void scheduleExportTask(ExportTask exportTask, ExporterDescriptor exporter,
                                    HttpServletRequestInfo httpServletRequestInfo, String batchID) {
        ApplicationEntity ae = device.getApplicationEntity(exporter.getAETitle(), true);
        if (ae == null) {
            LOG.warn("Failed to schedule {}: no such Archive AE Title - {}", exportTask, exporter.getAETitle());
            exportTask.setScheduledTime(null);
            return;
        }
        LOG.info("Schedule {}", exportTask);
        QueueMessage queueMessage = queueManager.scheduleMessage(
                device.getDeviceName(),
                exporter.getQueueName(),
                new Date(),
                toJSON(exportTask, httpServletRequestInfo),
                exportTask.getPk(),
                batchID);
        exportTask.setQueueMessage(queueMessage);
//        Attributes attrs = queryService.queryExportTaskInfo(exportTask, ae);
//        if (attrs == null) {
//            LOG.info("No Export Task Info found for {}", exportTask);
//            return;
//        }
//        exportTask.setModalities(attrs.getStrings(Tag.ModalitiesInStudy));
//        exportTask.setNumberOfInstances(attrs.getInt(Tag.NumberOfStudyRelatedInstances, -1));
    }

    private String toJSON(ExportTask exportTask, HttpServletRequestInfo httpServletRequestInfo) {
        StringWriter sw = new StringWriter();
        try (JsonGenerator gen = Json.createGenerator(sw)) {
            gen.writeStartObject();
            gen.write("StudyInstanceUID", exportTask.getStudyInstanceUID());
            if (!exportTask.getSeriesInstanceUID().equals("*")) {
                gen.write("SeriesInstanceUID", exportTask.getSeriesInstanceUID());
                if (!exportTask.getSopInstanceUID().equals("*")) {
                    gen.write("SOPInstanceUID", exportTask.getSopInstanceUID());
                }
            }
            gen.write("ExporterID", exportTask.getExporterID());
/*
            if (httpServletRequestInfo != null)
                httpServletRequestInfo.writeTo(gen);
*/
            gen.writeEnd();
        }
        return sw.toString();
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
            queueManager.deleteTask(queueMsg.getPk(), queueEvent);

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

        queueManager.cancelTask(queueMessage.getPk(), queueEvent);
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
    public void rescheduleExportTask(Long pk, ExporterDescriptor exporter, QueueMessageEvent queueEvent) {
        rescheduleExportTask(pk, exporter, null, queueEvent, null);
    }

    @Override
    public void rescheduleExportTask(Long pk, ExporterDescriptor exporter, HttpServletRequestInfo httpServletRequestInfo,
                                     QueueMessageEvent queueEvent) {
        rescheduleExportTask(pk, exporter, httpServletRequestInfo, queueEvent, null);
    }

    @Override
    public void rescheduleExportTask(Long pk, ExporterDescriptor exporter, HttpServletRequestInfo httpServletRequestInfo,
                                     QueueMessageEvent queueEvent, Date scheduledTime) {
        ExportTask task = em.find(ExportTask.class, pk);
        if (task == null)
            return;

        task.setExporterID(exporter.getExporterID());
        if (scheduledTime == null)
            rescheduleImmediately(task, exporter, httpServletRequestInfo, queueEvent);
        else
            rescheduleAtScheduledTime(task, queueEvent, scheduledTime);
    }

    private void rescheduleAtScheduledTime(ExportTask task, QueueMessageEvent queueEvent, Date scheduledTime) {
        task.setScheduledTime(scheduledTime);
        if (task.getQueueMessage() != null) {
            queueManager.deleteTask(task.getQueueMessage().getPk(), queueEvent, false);
            task.setQueueMessage(null);
        }
    }

    private void rescheduleImmediately(ExportTask task, ExporterDescriptor exporter, HttpServletRequestInfo httpServletRequestInfo,
                                       QueueMessageEvent queueEvent) {
        if (task.getQueueMessage() == null)
            scheduleExportTask(task, exporter, httpServletRequestInfo, task.getBatchID());
        else {
            queueManager.rescheduleTask(task.getQueueMessage().getPk(), exporter.getQueueName(), queueEvent, new Date());
            LOG.info("Reschedule {} to Exporter[id={}]", task, task.getExporterID());
        }
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

    private Subquery<Long> statusSubquery(TaskQueryParam exportBatchQueryParam,
                                          Root<ExportTask> exportTask, QueueMessage.Status status) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        MatchTask matchTask = new MatchTask(cb);
        CriteriaQuery<ExportTask> query = cb.createQuery(ExportTask.class);
        Subquery<Long> sq = query.subquery(Long.class);
        Root<ExportTask> exportTask1 = sq.from(ExportTask.class);

        List<Predicate> predicates = new ArrayList<>();
        matchTask.matchExportBatch(predicates, exportBatchQueryParam, exportTask1);
        if (status == QueueMessage.Status.TO_SCHEDULE)
            predicates.add(exportTask1.get(ExportTask_.queueMessage).isNull());
        else {
            Join<ExportTask, QueueMessage> queueMsg1 = sq.correlate(exportTask1.join(ExportTask_.queueMessage));
            predicates.add(cb.equal(queueMsg1.get(QueueMessage_.status), status));
        }
        predicates.add(cb.equal(exportTask1.get(ExportTask_.batchID), exportTask.get(ExportTask_.batchID)));
        sq.where(predicates.toArray(new Predicate[0]));
        sq.select(cb.count(exportTask1));
        return sq;
    }

    @Override
    public void merge(Task task) {
        em.merge(task);
    }

    private class ListExportBatches {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        final MatchTask matchTask = new MatchTask(cb);
        final CriteriaQuery<Tuple> query = cb.createTupleQuery();
        final Root<ExportTask> exportTask = query.from(ExportTask.class);
        From<ExportTask, QueueMessage> queueMsg;
        Expression<Date> minProcessingStartTime;
        Expression<Date> maxProcessingStartTime;
        Expression<Date> minProcessingEndTime;
        Expression<Date> maxProcessingEndTime;
        final Expression<Date> minScheduledTime = cb.least(exportTask.get(ExportTask_.scheduledTime));
        final Expression<Date> maxScheduledTime = cb.greatest(exportTask.get(ExportTask_.scheduledTime));
        final Expression<Date> minCreatedTime = cb.least(exportTask.get(ExportTask_.createdTime));
        final Expression<Date> maxCreatedTime = cb.greatest(exportTask.get(ExportTask_.createdTime));
        final Expression<Date> minUpdatedTime = cb.least(exportTask.get(ExportTask_.updatedTime));
        final Expression<Date> maxUpdatedTime = cb.greatest(exportTask.get(ExportTask_.updatedTime));
        final Path<String> batchIDPath = exportTask.get(ExportTask_.batchID);
        final Expression<Long> completed;
        final Expression<Long> failed;
        final Expression<Long> warning;
        final Expression<Long> canceled;
        final Expression<Long> scheduled;
        final Expression<Long> inprocess;
        final Expression<Long> toschedule;
        final TaskQueryParam queueBatchQueryParam;
        final TaskQueryParam exportBatchQueryParam;

        ListExportBatches(TaskQueryParam queueBatchQueryParam, TaskQueryParam exportBatchQueryParam) {
            this.queueBatchQueryParam = queueBatchQueryParam;
            this.exportBatchQueryParam = exportBatchQueryParam;
            if (queueBatchQueryParam.getStatus() != QueueMessage.Status.TO_SCHEDULE) {
                this.queueMsg = exportTask.join(ExportTask_.queueMessage,
                        queueBatchQueryParam.getStatus() == null ? JoinType.LEFT : JoinType.INNER);
                this.minProcessingStartTime = cb.least(queueMsg.get(QueueMessage_.processingStartTime));
                this.maxProcessingStartTime = cb.greatest(queueMsg.get(QueueMessage_.processingStartTime));
                this.minProcessingEndTime = cb.least(queueMsg.get(QueueMessage_.processingEndTime));
                this.maxProcessingEndTime = cb.greatest(queueMsg.get(QueueMessage_.processingEndTime));
            }
            this.completed = statusSubquery(exportBatchQueryParam, exportTask, QueueMessage.Status.COMPLETED).getSelection();
            this.failed = statusSubquery(exportBatchQueryParam, exportTask, QueueMessage.Status.FAILED).getSelection();
            this.warning = statusSubquery(exportBatchQueryParam, exportTask, QueueMessage.Status.WARNING).getSelection();
            this.canceled = statusSubquery(exportBatchQueryParam, exportTask, QueueMessage.Status.CANCELED).getSelection();
            this.scheduled = statusSubquery(exportBatchQueryParam, exportTask, QueueMessage.Status.SCHEDULED).getSelection();
            this.inprocess = statusSubquery(exportBatchQueryParam, exportTask, QueueMessage.Status.IN_PROCESS).getSelection();
            this.toschedule= statusSubquery(exportBatchQueryParam, exportTask, QueueMessage.Status.TO_SCHEDULE).getSelection();
            if (queueBatchQueryParam.getStatus() != QueueMessage.Status.TO_SCHEDULE)
                query.multiselect(batchIDPath,
                        minProcessingStartTime, maxProcessingStartTime,
                        minProcessingEndTime, maxProcessingEndTime,
                        minScheduledTime, maxScheduledTime,
                        minCreatedTime, maxCreatedTime,
                        minUpdatedTime, maxUpdatedTime,
                        completed, failed, warning, canceled, scheduled, inprocess, toschedule);
            else
                query.multiselect(batchIDPath,
                        minScheduledTime, maxScheduledTime,
                        minCreatedTime, maxCreatedTime,
                        minUpdatedTime, maxUpdatedTime,
                        completed, failed, warning, canceled, scheduled, inprocess, toschedule);
            query.groupBy(exportTask.get(ExportTask_.batchID));
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
            exportBatch.setScheduledTimeRange(
                    tuple.get(minScheduledTime),
                    tuple.get(maxScheduledTime));
            exportBatch.setCreatedTimeRange(
                    tuple.get(minCreatedTime),
                    tuple.get(maxCreatedTime));
            exportBatch.setUpdatedTimeRange(
                    tuple.get(minUpdatedTime),
                    tuple.get(maxUpdatedTime));
            if (queueBatchQueryParam.getStatus() != QueueMessage.Status.TO_SCHEDULE) {
                exportBatch.setProcessingStartTimeRange(
                        tuple.get(minProcessingStartTime),
                        tuple.get(maxProcessingStartTime));
                exportBatch.setProcessingEndTimeRange(
                        tuple.get(minProcessingEndTime),
                        tuple.get(maxProcessingEndTime));
            }

            CriteriaQuery<String> distinct = cb.createQuery(String.class).distinct(true);
            Root<ExportTask> exportTask = distinct.from(ExportTask.class);
            From<ExportTask, QueueMessage> queueMsg = exportTask.join(ExportTask_.queueMessage,
                    queueBatchQueryParam.getStatus() != null && queueBatchQueryParam.getStatus() != QueueMessage.Status.TO_SCHEDULE
                            ? JoinType.INNER : JoinType.LEFT);
            distinct.where(predicates(queueMsg, exportTask, batchID));
            exportBatch.setDeviceNames(select(distinct, exportTask.get(ExportTask_.deviceName)));
            exportBatch.setExporterIDs(select(distinct, exportTask.get(ExportTask_.exporterID)));
            exportBatch.setCompleted(tuple.get(completed));
            exportBatch.setCanceled(tuple.get(canceled));
            exportBatch.setWarning(tuple.get(warning));
            exportBatch.setFailed(tuple.get(failed));
            exportBatch.setScheduled(tuple.get(scheduled));
            exportBatch.setInProcess(tuple.get(inprocess));
            exportBatch.setToSchedule(tuple.get(toschedule));
            return exportBatch;
        }

        private Predicate[] predicates(Path<QueueMessage> queueMsg, Path<ExportTask> exportTask, String batchID) {
            List<Predicate> predicates = matchTask.exportBatchPredicates(
                    queueMsg, exportTask, queueBatchQueryParam, exportBatchQueryParam);
            predicates.add(cb.equal(exportTask.get(ExportTask_.batchID), batchID));
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

        q.multiselect(exportTask.get(ExportTask_.pk), exportTask.get(ExportTask_.exporterID));

        List<Predicate> predicates = predicates(exportTask, matchTask, queueTaskQueryParam, exportTaskQueryParam);
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
                    status == null ? JoinType.LEFT : JoinType.INNER);
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
        List<Long> referencedQueueMsgIDs = em.createQuery(
                select(QueueMessage_.pk, queueTaskQueryParam, exportTaskQueryParam))
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

    private CriteriaQuery<Long> select(SingularAttribute<QueueMessage, Long> attribute,
                                       TaskQueryParam queueTaskQueryParam, TaskQueryParam exportTaskQueryParam) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        Root<ExportTask> exportTask = q.from(ExportTask.class);
        From<ExportTask, QueueMessage> queueMsg = exportTask.join(ExportTask_.queueMessage);
        List<Predicate> predicates = new MatchTask(cb).exportPredicates(
                queueMsg, exportTask, queueTaskQueryParam, exportTaskQueryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));
        return q.select(queueMsg.get(attribute));
    }
}