/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 *  The contents of this file are subject to the Mozilla Public License Version
 *  1.1 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  http://www.mozilla.org/MPL/
 *
 *  Software distributed under the License is distributed on an "AS IS" basis,
 *  WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 *  for the specific language governing rights and limitations under the
 *  License.
 *
 *  The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 *  Java(TM), hosted at https://github.com/dcm4che.
 *
 *  The Initial Developer of the Original Code is
 *  J4Care.
 *  Portions created by the Initial Developer are Copyright (C) 2015-2019
 *  the Initial Developer. All Rights Reserved.
 *
 *  Contributor(s):
 *  See @authors listed below
 *
 *  Alternatively, the contents of this file may be used under the terms of
 *  either the GNU General Public License Version 2 or later (the "GPL"), or
 *  the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 *  in which case the provisions of the GPL or the LGPL are applicable instead
 *  of those above. If you wish to allow use of your version of this file only
 *  under the terms of either the GPL or the LGPL, and not to allow others to
 *  use your version of this file under the terms of the MPL, indicate your
 *  decision by deleting the provisions above and replace them with the notice
 *  and other provisions required by the GPL or the LGPL. If you do not delete
 *  the provisions above, a recipient may use your version of this file under
 *  the terms of any one of the MPL, the GPL or the LGPL.
 *
 */

package org.dcm4chee.arc.retrieve.mgt.impl;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.event.QueueMessageEvent;
import org.dcm4chee.arc.qmgt.IllegalTaskStateException;
import org.dcm4chee.arc.qmgt.QueueManager;
import org.dcm4chee.arc.query.util.MatchTask;
import org.dcm4chee.arc.query.util.QueryBuilder;
import org.dcm4chee.arc.query.util.TaskQueryParam;
import org.dcm4chee.arc.query.util.TaskQueryParam1;
import org.dcm4chee.arc.retrieve.ExternalRetrieveContext;
import org.dcm4chee.arc.retrieve.mgt.RetrieveBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Tuple;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.*;
import javax.servlet.http.HttpServletRequest;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Oct 2017
 */
@Stateless
public class RetrieveManagerEJB {
    private static final Logger LOG = LoggerFactory.getLogger(RetrieveManagerEJB.class);

    @PersistenceContext(unitName = "dcm4chee-arc")
    private EntityManager em;

    @Inject
    private QueueManager queueManager;

    @Inject
    private Device device;

    public int scheduleRetrieveTask(ExternalRetrieveContext ctx, Date notRetrievedAfter) {
        int count = 0;
        Attributes keys = ctx.getKeys();
        String[] studyUIDs = keys.getStrings(Tag.StudyInstanceUID);
        for (String studyUID : studyUIDs) {
            keys.setString(Tag.StudyInstanceUID, VR.UI, studyUID);
            if (scheduleRetrieveTask(ctx, notRetrievedAfter, keys))
                count++;
        }

        return count;
    }

    private boolean scheduleRetrieveTask(ExternalRetrieveContext ctx, Date notRetrievedAfter, Attributes keys) {
        String studyUID = keys.getString(Tag.StudyInstanceUID);
        if (isAlreadyScheduledOrRetrievedAfter(ctx, notRetrievedAfter, studyUID))
            return false;

        Task task = new Task();
        task.setDeviceName(ctx.getDeviceName());
        task.setQueueName(ctx.getQueueName());
        task.setType(Task.Type.RETRIEVE);
        task.setFindSCP(ctx.getFindSCP());
        task.setPayload(ctx.getKeys());
        task.setStatus(Task.Status.SCHEDULED);
        task.setBatchID(ctx.getBatchID());
        task.setLocalAET(ctx.getLocalAET());
        task.setRemoteAET(ctx.getRemoteAET());
        task.setDestinationAET(ctx.getDestinationAET());
        task.setStudyInstanceUID(ctx.getStudyInstanceUID());
        task.setSeriesInstanceUID(ctx.getSeriesInstanceUID());
        task.setSOPInstanceUID(ctx.getSOPInstanceUID());
        task.setScheduledTime(ctx.getScheduledTime());
        if (ctx.getHttpServletRequestInfo() != null) {
            task.setRequesterUserID(ctx.getHttpServletRequestInfo().requesterUserID);
            task.setRequesterHost(ctx.getHttpServletRequestInfo().requesterHost);
            task.setRequestURI(ctx.getHttpServletRequestInfo().requestURI);
        }
        em.persist(task);
        LOG.info("Create {}", task);
        return true;
    }

    public void scheduleRetrieveTask(RetrieveTask retrieveTask, HttpServletRequest request) {
        LOG.info("Schedule {}", retrieveTask);
        StringWriter sw = new StringWriter();
        try (JsonGenerator gen = Json.createGenerator(sw)) {
            gen.writeStartObject();
            gen.write("LocalAET", retrieveTask.getLocalAET());
            gen.write("RemoteAET", retrieveTask.getRemoteAET());
            gen.write("Priority", 0);
            gen.write("DestinationAET", retrieveTask.getDestinationAET());
            gen.write("StudyInstanceUID", retrieveTask.getStudyInstanceUID());
/*
            if (request != null)
                HttpServletRequestInfo.valueOf(request).writeTo(gen);
*/
            gen.writeEnd();
        }
        Date scheduledTime = new Date();
        QueueMessage queueMessage = queueManager.scheduleMessage(device.getDeviceName(),
                retrieveTask.getQueueName(), scheduledTime, sw.toString(), toKeys(retrieveTask), retrieveTask.getBatchID());
        retrieveTask.setQueueMessage(queueMessage);
        retrieveTask.setScheduledTime(scheduledTime);
    }

    private boolean isAlreadyScheduledOrRetrievedAfter(ExternalRetrieveContext ctx, Date retrievedAfter,
                                                       String studyUID) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Task> q = cb.createQuery(Task.class);
        Root<Task> task = q.from(Task.class);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(task.get(Task_.type), Task.Type.RETRIEVE));
        Predicate statusPredicate = task.get(Task_.status).in(Task.Status.SCHEDULED, Task.Status.IN_PROCESS);
        predicates.add(retrievedAfter != null
                ? cb.or(statusPredicate, cb.greaterThan(task.get(Task_.updatedTime), retrievedAfter))
                : statusPredicate);
        predicates.add(cb.equal(task.get(Task_.remoteAET), ctx.getRemoteAET()));
        predicates.add(cb.equal(task.get(Task_.destinationAET), ctx.getDestinationAET()));
        predicates.add(cb.equal(task.get(Task_.studyInstanceUID), studyUID));
        if (ctx.getSeriesInstanceUID() != null)
            predicates.add(cb.equal(task.get(Task_.seriesInstanceUID), ctx.getSeriesInstanceUID()));
        else {
            predicates.add(cb.or(
                    task.get(Task_.seriesInstanceUID).isNull(),
                    cb.equal(task.get(Task_.seriesInstanceUID),
                            ctx.getSeriesInstanceUID())));
            if (ctx.getSOPInstanceUID() == null)
                predicates.add(task.get(Task_.sopInstanceUID).isNull());
            else
                predicates.add(cb.or(
                        task.get(Task_.sopInstanceUID).isNull(),
                        cb.equal(task.get(Task_.sopInstanceUID),
                                ctx.getSOPInstanceUID())));
        }

        try (Stream<Task> resultStream = em.createQuery(q
                .where(predicates.toArray(new Predicate[0]))
                .select(task))
                .getResultStream()) {
            Iterator<Task> iterator = resultStream.iterator();
            if (iterator.hasNext()) {
                iterator.forEachRemaining(retrieveTask1 -> {
                    if (ctx.getScheduledTime().before(retrieveTask1.getScheduledTime())) {
                        LOG.info("Previous {} found - Update scheduled time to {}", retrieveTask1, ctx.getScheduledTime());
                        retrieveTask1.setScheduledTime(ctx.getScheduledTime());
                    } else
                        LOG.info("Previous {} found - suppress duplicate retrieve", retrieveTask1);
                });
                return true;
            }
        }
        return false;
    }

    public void updateRetrieveTask(Task task, Attributes cmd) {
        em.createNamedQuery(Task.UPDATE_RETRIEVE_RESULT_BY_PK)
                .setParameter(1, task.getPk())
                .setParameter(2, cmd.getInt(Tag.NumberOfRemainingSuboperations, 0))
                .setParameter(3, cmd.getInt(Tag.NumberOfCompletedSuboperations, 0))
                .setParameter(4, cmd.getInt(Tag.NumberOfFailedSuboperations, 0))
                .setParameter(5, cmd.getInt(Tag.NumberOfWarningSuboperations, 0))
                .setParameter(6, cmd.getInt(Tag.Status, 0))
                .setParameter(7, cmd.getString(Tag.ErrorComment, null))
                .executeUpdate();
    }

    public void resetRetrieveTask(Task task) {
        em.createNamedQuery(Task.UPDATE_RETRIEVE_RESULT_BY_PK)
                .setParameter(1, task.getPk())
                .setParameter(2, -1)
                .setParameter(3, 0)
                .setParameter(4, 0)
                .setParameter(5, 0)
                .setParameter(6, -1)
                .setParameter(7, null)
                .executeUpdate();
    }

    public boolean deleteRetrieveTask(Long pk, QueueMessageEvent queueEvent) {
        RetrieveTask task = em.find(RetrieveTask.class, pk);
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

    public boolean cancelRetrieveTask(Long pk, QueueMessageEvent queueEvent) throws IllegalTaskStateException {
        RetrieveTask task = em.find(RetrieveTask.class, pk);
        if (task == null)
            return false;

        QueueMessage queueMessage = task.getQueueMessage();
        if (queueMessage == null)
            throw new IllegalTaskStateException("Cannot cancel Task with status: 'TO SCHEDULE'");

        queueManager.cancelTask(queueMessage.getPk(), queueEvent);
        LOG.info("Cancel {}", task);
        return true;
    }

    public long cancelRetrieveTasks(TaskQueryParam queueTaskQueryParam, TaskQueryParam retrieveTaskQueryParam) {
        return queueManager.cancelRetrieveTasks(queueTaskQueryParam, retrieveTaskQueryParam);
    }

    public void rescheduleRetrieveTask(Long pk, String newQueueName, QueueMessageEvent queueEvent) {
        rescheduleRetrieveTask(pk, newQueueName, queueEvent, null);
    }

    public void rescheduleRetrieveTask(Long pk, String newQueueName, QueueMessageEvent queueEvent, Date scheduledTime) {
        RetrieveTask task = em.find(RetrieveTask.class, pk);
        if (task == null)
            return;

        if (newQueueName != null)
            task.setQueueName(newQueueName);

        if (scheduledTime == null)
            rescheduleImmediately(task, queueEvent);
        else
            rescheduleAtScheduledTime(task, queueEvent, scheduledTime);
    }

    private void rescheduleAtScheduledTime(RetrieveTask task, QueueMessageEvent queueEvent, Date scheduledTime) {
        task.setScheduledTime(scheduledTime);
        if (task.getQueueMessage() != null) {
            queueManager.deleteTask(task.getQueueMessage().getPk(), queueEvent, false);
            task.setQueueMessage(null);
        }
    }

    private void rescheduleImmediately(RetrieveTask task, QueueMessageEvent queueEvent) {
        if (task.getQueueMessage() == null)
            scheduleRetrieveTask(task, queueEvent.getRequest());
        else {
            LOG.info("Reschedule {}", task);
            task.setScheduledTime(new Date());
            queueManager.rescheduleTask(task.getQueueMessage().getPk(), task.getQueueName(), queueEvent, new Date());
        }
    }

    public void markTaskForRetrieve(
            Long pk, String devName, String newQueueName, QueueMessageEvent queueEvent, Date scheduledTime) {
        RetrieveTask task = em.find(RetrieveTask.class, pk);
        if (task == null)
            return;

        LOG.info("Mark {} for retrieve on device {}", task, devName);
        task.setScheduledTime(scheduledTime != null ? scheduledTime : new Date());
        task.setDeviceName(devName);
        if (newQueueName != null)
            task.setQueueName(newQueueName);
        if (task.getQueueMessage() == null)
            return;

        queueManager.deleteTask(task.getQueueMessage().getPk(), queueEvent, false);
        task.setQueueMessage(null);
    }

    private Attributes toKeys(RetrieveTask task) {
        int n = task.getSOPInstanceUID() != null ? 3 : task.getSeriesInstanceUID() != null ? 2 : 1;
        Attributes keys = new Attributes(n + 1);
        keys.setString(Tag.QueryRetrieveLevel, VR.CS, QueryRetrieveLevel2.values()[n].name());
        keys.setString(Tag.StudyInstanceUID, VR.UI, task.getStudyInstanceUID());
        if (n > 1) {
            keys.setString(Tag.SeriesInstanceUID, VR.UI, task.getSeriesInstanceUID());
            if (n > 2)
                keys.setString(Tag.SOPInstanceUID, VR.UI, task.getSOPInstanceUID());
        }
        return keys;
    }

    public List<String> listDistinctDeviceNames(TaskQueryParam retrieveTaskQueryParam) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<String> q = cb.createQuery(String.class);
        Root<RetrieveTask> retrieveTask = q.from(RetrieveTask.class);
        List<Predicate> predicates = new ArrayList<>();
        new MatchTask(cb).matchRetrieveTask(predicates, retrieveTaskQueryParam, retrieveTask);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));
        return em.createQuery(
                q.select(retrieveTask.get(RetrieveTask_.deviceName)).distinct(true))
                .getResultList();
    }

    public List<Long> listRetrieveTaskPks(TaskQueryParam queueTaskQueryParam, TaskQueryParam retrieveTaskQueryParam,
                                          int limit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        MatchTask matchTask = new MatchTask(cb);
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        Root<RetrieveTask> retrieveTask = q.from(RetrieveTask.class);
        List<Predicate> predicates = predicates(retrieveTask, matchTask, queueTaskQueryParam, retrieveTaskQueryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));

        TypedQuery<Long> query = em.createQuery(q.select(retrieveTask.get(RetrieveTask_.pk)));
        if (limit > 0)
            query.setMaxResults(limit);
        return query.getResultList();
    }

    public List<Tuple> listRetrieveTaskPkAndLocalAETs(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam retrieveTaskQueryParam, int limit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        MatchTask matchTask = new MatchTask(cb);
        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        Root<RetrieveTask> retrieveTask = q.from(RetrieveTask.class);
        List<Predicate> predicates = predicates(retrieveTask, matchTask, queueTaskQueryParam, retrieveTaskQueryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));
        return em.createQuery(
                q.multiselect(
                        retrieveTask.get(RetrieveTask_.pk),
                        retrieveTask.get(RetrieveTask_.localAET)))
                .setMaxResults(limit)
                .getResultList();
    }

    public List<RetrieveBatch> listRetrieveBatches(TaskQueryParam1 queryParam, int offset, int limit) {
        ListRetrieveBatches listRetrieveBatches1 = new ListRetrieveBatches(queryParam);
        TypedQuery<Tuple> query = em.createQuery(listRetrieveBatches1.query);
        if (offset > 0)
            query.setFirstResult(offset);
        if (limit > 0)
            query.setMaxResults(limit);
        return query.getResultStream().map(listRetrieveBatches1::toRetrieveBatch).collect(Collectors.toList());
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Iterator<RetrieveTask> listRetrieveTasks(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam retrieveTaskQueryParam, int offset, int limit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        MatchTask matchTask = new MatchTask(cb);
        CriteriaQuery<RetrieveTask> q = cb.createQuery(RetrieveTask.class);
        Root<RetrieveTask> retrieveTask = q.from(RetrieveTask.class);

        List<Predicate> predicates = predicates(retrieveTask, matchTask, queueTaskQueryParam, retrieveTaskQueryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));
        if (retrieveTaskQueryParam.getOrderBy() != null)
            q.orderBy(matchTask.retrieveTaskOrder(retrieveTaskQueryParam.getOrderBy(), retrieveTask));
        TypedQuery<RetrieveTask> query = em.createQuery(q);
        if (offset > 0)
            query.setFirstResult(offset);
        if (limit > 0)
            query.setMaxResults(limit);
        return query.getResultStream().iterator();
    }

    private List<Predicate> predicates(Root<RetrieveTask> retrieveTask, MatchTask matchTask,
                                       TaskQueryParam queueTaskQueryParam, TaskQueryParam retrieveTaskQueryParam) {
        List<Predicate> predicates = new ArrayList<>();
        QueueMessage.Status status = queueTaskQueryParam.getStatus();
        if (status == QueueMessage.Status.TO_SCHEDULE) {
            matchTask.matchRetrieveTask(predicates, retrieveTaskQueryParam, retrieveTask);
            predicates.add(retrieveTask.get(RetrieveTask_.queueMessage).isNull());
        } else {
            From<RetrieveTask, QueueMessage> queueMsg = retrieveTask.join(RetrieveTask_.queueMessage,
                    status == null ? JoinType.LEFT : JoinType.INNER);
            predicates = matchTask.retrievePredicates(queueMsg, retrieveTask, queueTaskQueryParam, retrieveTaskQueryParam);
        }
        return predicates;
    }

    public Tuple findDeviceNameAndLocalAETByPk(Long pk) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> tupleQuery = cb.createTupleQuery();
        Root<RetrieveTask> retrieveTask = tupleQuery.from(RetrieveTask.class);
        tupleQuery.where(cb.equal(retrieveTask.get(RetrieveTask_.pk), pk));
        tupleQuery.multiselect(
                retrieveTask.get(RetrieveTask_.deviceName),
                retrieveTask.get(RetrieveTask_.localAET));
        return em.createQuery(tupleQuery).getSingleResult();
    }

    public long countTasks(TaskQueryParam queueTaskQueryParam, TaskQueryParam retrieveTaskQueryParam) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        MatchTask matchTask = new MatchTask(cb);
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        Root<RetrieveTask> retrieveTask = q.from(RetrieveTask.class);

        List<Predicate> predicates = predicates(retrieveTask, matchTask, queueTaskQueryParam, retrieveTaskQueryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));
        return QueryBuilder.unbox(em.createQuery(q.select(cb.count(retrieveTask))).getSingleResult(), 0L);
    }

    private class ListRetrieveBatches {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        final QueryBuilder queryBuilder = new QueryBuilder(cb);
        final CriteriaQuery<Tuple> query = cb.createTupleQuery();
        final Root<Task> task = query.from(Task.class);
        final Path<String> batchIDPath = task.get(Task_.batchID);
        Expression<Date> minProcessingStartTime;
        Expression<Date> maxProcessingStartTime;
        Expression<Date> minProcessingEndTime;
        Expression<Date> maxProcessingEndTime;
        final Expression<Date> minScheduledTime = cb.least(task.get(Task_.scheduledTime));
        final Expression<Date> maxScheduledTime = cb.greatest(task.get(Task_.scheduledTime));
        final Expression<Date> minCreatedTime = cb.least(task.get(Task_.createdTime));
        final Expression<Date> maxCreatedTime = cb.greatest(task.get(Task_.createdTime));
        final Expression<Date> minUpdatedTime = cb.least(task.get(Task_.updatedTime));
        final Expression<Date> maxUpdatedTime = cb.greatest(task.get(Task_.updatedTime));
        final Expression<Long> completed;
        final Expression<Long> failed;
        final Expression<Long> warning;
        final Expression<Long> canceled;
        final Expression<Long> scheduled;
        final Expression<Long> inprocess;
        final TaskQueryParam1 queryParam;

        ListRetrieveBatches(TaskQueryParam1 queryParam) {
            this.queryParam = queryParam;
            this.minProcessingStartTime = cb.least(task.get(Task_.processingStartTime));
            this.maxProcessingStartTime = cb.greatest(task.get(Task_.processingStartTime));
            this.minProcessingEndTime = cb.least(task.get(Task_.processingEndTime));
            this.maxProcessingEndTime = cb.greatest(task.get(Task_.processingEndTime));
            this.completed = statusSubquery(Task.Status.COMPLETED).getSelection();
            this.failed = statusSubquery(Task.Status.FAILED).getSelection();
            this.warning = statusSubquery(Task.Status.WARNING).getSelection();
            this.canceled = statusSubquery(Task.Status.CANCELED).getSelection();
            this.scheduled = statusSubquery(Task.Status.SCHEDULED).getSelection();
            this.inprocess = statusSubquery(Task.Status.IN_PROCESS).getSelection();
            query.multiselect(batchIDPath,
                minProcessingStartTime, maxProcessingStartTime,
                minProcessingEndTime, maxProcessingEndTime,
                minScheduledTime, maxScheduledTime,
                minCreatedTime, maxCreatedTime,
                minUpdatedTime, maxUpdatedTime,
                completed, failed, warning, canceled, scheduled, inprocess);
            query.groupBy(task.get(Task_.batchID));
            List<Predicate> predicates = new ArrayList<>();
            if (queryParam.getBatchID() != null)
                predicates.add(cb.equal(task.get(Task_.batchID), queryParam.getBatchID()));
            else
                predicates.add(task.get(Task_.batchID).isNotNull());
            if (queryParam.getStatus() != null)
                predicates.add(cb.equal(task.get(Task_.status), queryParam.getStatus()));
            queryBuilder.matchRetrieveBatch(predicates, queryParam, task);
            if (!predicates.isEmpty())
                query.where(predicates.toArray(new Predicate[0]));
            if (queryParam.getOrderBy() != null)
                query.orderBy(queryBuilder.orderBatches(task, queryParam.getOrderBy()));
        }

        private Subquery<Long> statusSubquery(Task.Status status) {
            CriteriaQuery<Task> query = cb.createQuery(Task.class);
            Subquery<Long> sq = query.subquery(Long.class);
            Root<Task> sqtask = sq.from(Task.class);
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(sqtask.get(Task_.status), status));
            predicates.add(cb.equal(sqtask.get(Task_.batchID), task.get(Task_.batchID)));
            queryBuilder.matchRetrieveBatch(predicates, queryParam, sqtask);
            sq.where(predicates.toArray(new Predicate[0]));
            sq.select(cb.count(sqtask));
            return sq;
        }

        RetrieveBatch toRetrieveBatch(Tuple tuple) {
            String batchID = tuple.get(batchIDPath);
            RetrieveBatch retrieveBatch = new RetrieveBatch(batchID);
            retrieveBatch.setScheduledTimeRange(
                    tuple.get(minScheduledTime),
                    tuple.get(maxScheduledTime));
            retrieveBatch.setCreatedTimeRange(
                    tuple.get(minCreatedTime),
                    tuple.get(maxCreatedTime));
            retrieveBatch.setUpdatedTimeRange(
                    tuple.get(minUpdatedTime),
                    tuple.get(maxUpdatedTime));
            retrieveBatch.setProcessingStartTimeRange(
                    tuple.get(maxProcessingStartTime),
                    tuple.get(maxProcessingStartTime));
            retrieveBatch.setProcessingEndTimeRange(
                    tuple.get(minProcessingEndTime),
                    tuple.get(maxProcessingEndTime));

            CriteriaQuery<String> distinct = cb.createQuery(String.class).distinct(true);
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(task.get(Task_.batchID), batchID));
            queryBuilder.matchRetrieveBatch(predicates, queryParam, task);
            distinct.where(predicates.toArray(new Predicate[0]));
            retrieveBatch.setDeviceNames(select(distinct, task.get(Task_.deviceName)));
            retrieveBatch.setQueueNames(select(distinct, task.get(Task_.queueName)));
            retrieveBatch.setLocalAETs(select(distinct, task.get(Task_.localAET)));
            retrieveBatch.setRemoteAETs(select(distinct, task.get(Task_.remoteAET)));
            retrieveBatch.setDestinationAETs(select(distinct, task.get(Task_.destinationAET)));
            retrieveBatch.setCompleted(tuple.get(completed));
            retrieveBatch.setCanceled(tuple.get(canceled));
            retrieveBatch.setWarning(tuple.get(warning));
            retrieveBatch.setFailed(tuple.get(failed));
            retrieveBatch.setScheduled(tuple.get(scheduled));
            retrieveBatch.setInProcess(tuple.get(inprocess));
            return retrieveBatch;
        }

        private List<String> select(CriteriaQuery<String> query, Path<String> path) {
            return em.createQuery(query.select(path)).getResultList();
        }
    }

    public int deleteTasks(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam retrieveTaskQueryParam, int deleteTasksFetchSize) {
        QueueMessage.Status status = queueTaskQueryParam.getStatus();
        if (status == QueueMessage.Status.TO_SCHEDULE)
            return deleteToSchedule(retrieveTaskQueryParam);

        if (status == null && queueTaskQueryParam.getBatchID() == null)
            return deleteReferencedTasks(queueTaskQueryParam, retrieveTaskQueryParam, deleteTasksFetchSize)
                    + deleteToSchedule(retrieveTaskQueryParam);

        return deleteReferencedTasks(queueTaskQueryParam, retrieveTaskQueryParam, deleteTasksFetchSize);
    }

    private int deleteToSchedule(TaskQueryParam retrieveTaskQueryParam) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaDelete<RetrieveTask> q = cb.createCriteriaDelete(RetrieveTask.class);
        Root<RetrieveTask> retrieveTask = q.from(RetrieveTask.class);
        List<Predicate> predicates = new ArrayList<>();
        new MatchTask(cb).matchRetrieveTask(predicates, retrieveTaskQueryParam, retrieveTask);
        predicates.add(retrieveTask.get(RetrieveTask_.queueMessage).isNull());
        q.where(predicates.toArray(new Predicate[0]));
        return em.createQuery(q).executeUpdate();
    }

    private int deleteReferencedTasks(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam retrieveTaskQueryParam, int deleteTasksFetchSize) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        Root<RetrieveTask> retrieveTask = q.from(RetrieveTask.class);
        From<RetrieveTask, QueueMessage> queueMsg = retrieveTask.join(RetrieveTask_.queueMessage);
        List<Predicate> predicates = new MatchTask(cb).retrievePredicates(
                queueMsg, retrieveTask, queueTaskQueryParam, retrieveTaskQueryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));
        List<Long> referencedQueueMsgIDs = em.createQuery(
                q.select(queueMsg.get(QueueMessage_.pk)))
                .setMaxResults(deleteTasksFetchSize)
                .getResultList();

        referencedQueueMsgIDs.forEach(queueMsgID -> queueManager.deleteTask(queueMsgID, null));
        return referencedQueueMsgIDs.size();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<RetrieveTask.PkAndQueueName> findRetrieveTasksToSchedule(int fetchSize, Set<String> suspendedQueues) {
        return queryRetrieveTasksToSchedule(suspendedQueues)
                .setMaxResults(fetchSize)
                .getResultList();
    }

    private TypedQuery<RetrieveTask.PkAndQueueName> queryRetrieveTasksToSchedule(Set<String> suspendedQueues) {
        return suspendedQueues.isEmpty()
                ? em.createNamedQuery(RetrieveTask.FIND_SCHEDULED_BY_DEVICE_NAME,
                        RetrieveTask.PkAndQueueName.class)
                    .setParameter(1, device.getDeviceName())
                : em.createNamedQuery(RetrieveTask.FIND_SCHEDULED_BY_DEVICE_NAME_AND_NOT_IN_QUEUE,
                        RetrieveTask.PkAndQueueName.class)
                    .setParameter(1, device.getDeviceName())
                    .setParameter(2, suspendedQueues);
    }

    public boolean scheduleRetrieveTask(Long pk) {
        RetrieveTask retrieveTask = em.find(RetrieveTask.class, pk);
        scheduleRetrieveTask(retrieveTask, null);
        return true;
    }
}
