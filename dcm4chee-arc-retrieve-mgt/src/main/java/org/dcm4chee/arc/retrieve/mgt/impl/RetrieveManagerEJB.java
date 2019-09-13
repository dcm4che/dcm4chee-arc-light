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

import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Predicate;
import javax.persistence.Tuple;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.event.QueueMessageEvent;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.qmgt.IllegalTaskStateException;
import org.dcm4chee.arc.qmgt.QueueManager;
import org.dcm4chee.arc.qmgt.QueueSizeLimitExceededException;
import org.dcm4chee.arc.query.util.MatchTask;
import org.dcm4chee.arc.query.util.QueryBuilder;
import org.dcm4chee.arc.query.util.TaskQueryParam;
import org.dcm4chee.arc.retrieve.ExternalRetrieveContext;
import org.dcm4chee.arc.retrieve.mgt.RetrieveBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.persistence.*;
import javax.persistence.criteria.*;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

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

    public int scheduleRetrieveTask(int priority, ExternalRetrieveContext ctx,
                                    Date notRetrievedAfter, long delay)
            throws QueueSizeLimitExceededException {
        int count = 0;
        Attributes keys = ctx.getKeys();
        String[] studyUIDs = keys.getStrings(Tag.StudyInstanceUID);
        for (String studyUID : studyUIDs) {
            keys.setString(Tag.StudyInstanceUID, VR.UI, studyUID);
            if (scheduleRetrieveTask(priority, ctx, notRetrievedAfter, delay, keys))
                count++;
        }

        return count;
    }

    private boolean scheduleRetrieveTask(int priority, ExternalRetrieveContext ctx, Date notRetrievedAfter, long delay,
                                         Attributes keys) throws QueueSizeLimitExceededException {
        String studyUID = keys.getString(Tag.StudyInstanceUID);
        if (isAlreadyScheduledOrRetrievedAfter(em, ctx, notRetrievedAfter, studyUID))
            return false;

        try {
            ObjectMessage msg = queueManager.createObjectMessage(keys);
            msg.setStringProperty("LocalAET", ctx.getLocalAET());
            msg.setStringProperty("RemoteAET", ctx.getRemoteAET());
            msg.setIntProperty("Priority", priority);
            msg.setStringProperty("DestinationAET", ctx.getDestinationAET());
            msg.setStringProperty("StudyInstanceUID", studyUID);
            HttpServletRequestInfo.copyTo(ctx.getHttpServletRequestInfo(), msg);
            QueueMessage queueMessage = queueManager.scheduleMessage(ctx.getQueueName(), msg,
                    Message.DEFAULT_PRIORITY, ctx.getBatchID(), delay);
            persist(createRetrieveTask(ctx, queueMessage), studyUID);
            return true;
        } catch (JMSException e) {
            throw QueueMessage.toJMSRuntimeException(e);
        }
    }

    public void scheduleRetrieveTask(RetrieveTask retrieveTask, HttpServletRequest request) {
        try {
            ObjectMessage msg = queueManager.createObjectMessage(toKeys(retrieveTask));
            msg.setStringProperty("LocalAET", retrieveTask.getLocalAET());
            msg.setStringProperty("RemoteAET", retrieveTask.getRemoteAET());
            msg.setIntProperty("Priority", 0);
            msg.setStringProperty("DestinationAET", retrieveTask.getDestinationAET());
            msg.setStringProperty("StudyInstanceUID", retrieveTask.getStudyInstanceUID());
            HttpServletRequestInfo.copyTo(HttpServletRequestInfo.valueOf(request), msg);
            QueueMessage queueMessage = queueManager.scheduleMessage(retrieveTask.getQueueName(), msg,
                    Message.DEFAULT_PRIORITY, retrieveTask.getBatchID(), 0L);
            retrieveTask.setQueueMessage(queueMessage);
        } catch (JMSException e) {
            throw QueueMessage.toJMSRuntimeException(e);
        }
    }

    private boolean isAlreadyScheduledOrRetrievedAfter(EntityManager em, ExternalRetrieveContext ctx, Date retrievedAfter,
                                                       String studyUID) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<RetrieveTask> q = cb.createQuery(RetrieveTask.class);
        Root<RetrieveTask> retrieveTask = q.from(RetrieveTask.class);
        From<RetrieveTask, QueueMessage> queueMsg = retrieveTask.join(RetrieveTask_.queueMessage);

        List<Predicate> predicates = new ArrayList<>();
        Predicate statusPredicate = queueMsg.get(QueueMessage_.status)
                .in(QueueMessage.Status.SCHEDULED, QueueMessage.Status.IN_PROCESS);
        if (retrievedAfter != null)
            statusPredicate = cb.or(
                    statusPredicate,
                    cb.greaterThan(retrieveTask.get(RetrieveTask_.updatedTime), retrievedAfter));

        predicates.add(statusPredicate);
        predicates.add(cb.equal(retrieveTask.get(RetrieveTask_.remoteAET), ctx.getRemoteAET()));
        predicates.add(cb.equal(retrieveTask.get(RetrieveTask_.destinationAET), ctx.getDestinationAET()));
        predicates.add(cb.equal(retrieveTask.get(RetrieveTask_.studyInstanceUID), studyUID));
        if (ctx.getSeriesInstanceUID() != null)
            predicates.add(cb.equal(retrieveTask.get(RetrieveTask_.seriesInstanceUID), ctx.getSeriesInstanceUID()));
        else {
            predicates.add(cb.or(
                    retrieveTask.get(RetrieveTask_.seriesInstanceUID).isNull(),
                    cb.equal(retrieveTask.get(RetrieveTask_.seriesInstanceUID),
                            ctx.getSeriesInstanceUID())));
            if (ctx.getSOPInstanceUID() == null)
                predicates.add(retrieveTask.get(RetrieveTask_.sopInstanceUID).isNull());
            else
                predicates.add(cb.or(
                        retrieveTask.get(RetrieveTask_.sopInstanceUID).isNull(),
                        cb.equal(retrieveTask.get(RetrieveTask_.sopInstanceUID),
                                ctx.getSOPInstanceUID())));
        }

        Iterator<RetrieveTask> iterator = em.createQuery(q
                .where(predicates.toArray(new Predicate[0]))
                .select(retrieveTask))
                .getResultStream()
                .iterator();
        if (iterator.hasNext()) {
            LOG.info("Previous {} found - suppress duplicate retrieve", iterator.next());
            return true;
        }
        return false;
    }

    public int createRetrieveTask(ExternalRetrieveContext ctx) {
        int count = 0;
        for (String studyUID : ctx.getKeys().getStrings(Tag.StudyInstanceUID)) {
            persist(createRetrieveTask(ctx, null), studyUID);
            count++;
        }
        return count;
    }

    private void persist(RetrieveTask task, String studyUID) {
        task.setStudyInstanceUID(studyUID);
        em.persist(task);
    }

    private RetrieveTask createRetrieveTask(ExternalRetrieveContext ctx, QueueMessage queueMessage) {
        RetrieveTask task = new RetrieveTask();
        task.setLocalAET(ctx.getLocalAET());
        task.setRemoteAET(ctx.getRemoteAET());
        task.setDestinationAET(ctx.getDestinationAET());
        task.setSeriesInstanceUID(ctx.getSeriesInstanceUID());
        task.setSOPInstanceUID(ctx.getSOPInstanceUID());
        task.setDeviceName(ctx.getDeviceName());
        task.setQueueName(ctx.getQueueName());
        task.setBatchID(ctx.getBatchID());
        task.setQueueMessage(queueMessage);
        return task;
    }

    public void updateRetrieveTask(QueueMessage queueMessage, Attributes cmd) {
        em.createNamedQuery(RetrieveTask.UPDATE_BY_QUEUE_MESSAGE)
                .setParameter(1, queueMessage)
                .setParameter(2, cmd.getInt(Tag.NumberOfRemainingSuboperations, 0))
                .setParameter(3, cmd.getInt(Tag.NumberOfCompletedSuboperations, 0))
                .setParameter(4, cmd.getInt(Tag.NumberOfFailedSuboperations, 0))
                .setParameter(5, cmd.getInt(Tag.NumberOfWarningSuboperations, 0))
                .setParameter(6, cmd.getInt(Tag.Status, 0))
                .setParameter(7, cmd.getString(Tag.ErrorComment, null))
                .executeUpdate();
    }

    public void resetRetrieveTask(QueueMessage queueMessage) {
        em.createNamedQuery(RetrieveTask.UPDATE_BY_QUEUE_MESSAGE)
                .setParameter(1, queueMessage)
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
            queueManager.deleteTask(queueMsg.getMessageID(), queueEvent);

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

        queueManager.cancelTask(queueMessage.getMessageID(), queueEvent);
        LOG.info("Cancel {}", task);
        return true;
    }

    public long cancelRetrieveTasks(TaskQueryParam queueTaskQueryParam, TaskQueryParam retrieveTaskQueryParam) {
        return queueManager.cancelRetrieveTasks(queueTaskQueryParam, retrieveTaskQueryParam);
    }

    public void rescheduleRetrieveTask(Long pk, String newQueueName, QueueMessageEvent queueEvent) {
        RetrieveTask task = em.find(RetrieveTask.class, pk);
        if (task == null)
            return;

        if (newQueueName != null)
            task.setQueueName(newQueueName);

        QueueMessage queueMessage = task.getQueueMessage();
        if (queueMessage == null) {
            LOG.info("Schedule {}", task);
            scheduleRetrieveTask(task, queueEvent.getRequest());
        } else {
            LOG.info("Reschedule {}", task);
            queueManager.rescheduleTask(task.getQueueMessage().getMessageID(), task.getQueueName(), queueEvent);
        }
    }

    private static Attributes toKeys(RetrieveTask task) {
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

    public List<RetrieveBatch> listRetrieveBatches(
            TaskQueryParam queueBatchQueryParam, TaskQueryParam retrieveBatchQueryParam, int offset, int limit) {
        ListRetrieveBatches listRetrieveBatches1 = new ListRetrieveBatches(queueBatchQueryParam, retrieveBatchQueryParam);
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

    private Subquery<Long> statusSubquery(TaskQueryParam retrieveBatchQueryParam,
                                           Root<RetrieveTask> retrieveTask, QueueMessage.Status status) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        MatchTask matchTask = new MatchTask(cb);
        CriteriaQuery<RetrieveTask> query = cb.createQuery(RetrieveTask.class);
        Subquery<Long> sq = query.subquery(Long.class);
        Root<RetrieveTask> retrieveTask1 = sq.from(RetrieveTask.class);

        List<Predicate> predicates = new ArrayList<>();
        matchTask.matchRetrieveBatch(predicates, retrieveBatchQueryParam, retrieveTask1);
        if (status == QueueMessage.Status.TO_SCHEDULE)
            predicates.add(retrieveTask1.get(RetrieveTask_.queueMessage).isNull());
        else {
            Join<RetrieveTask, QueueMessage> queueMsg1 = sq.correlate(retrieveTask1.join(RetrieveTask_.queueMessage));
            predicates.add(cb.equal(queueMsg1.get(QueueMessage_.status), status));
        }
        predicates.add(cb.equal(retrieveTask1.get(RetrieveTask_.batchID), retrieveTask.get(RetrieveTask_.batchID)));
        sq.where(predicates.toArray(new Predicate[0]));
        sq.select(cb.count(retrieveTask1));
        return sq;
    }

    private class ListRetrieveBatches {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        final MatchTask matchTask = new MatchTask(cb);
        final CriteriaQuery<Tuple> query = cb.createTupleQuery();
        final Root<RetrieveTask> retrieveTask = query.from(RetrieveTask.class);
        From<RetrieveTask, QueueMessage> queueMsg;
        final Path<String> batchIDPath = retrieveTask.get(RetrieveTask_.batchID);
        Expression<Date> minProcessingStartTime;
        Expression<Date> maxProcessingStartTime;
        Expression<Date> minProcessingEndTime;
        Expression<Date> maxProcessingEndTime;
        Expression<Date> minScheduledTime;
        Expression<Date> maxScheduledTime;
        final Expression<Date> minCreatedTime = cb.least(retrieveTask.get(RetrieveTask_.createdTime));
        final Expression<Date> maxCreatedTime = cb.greatest(retrieveTask.get(RetrieveTask_.createdTime));
        final Expression<Date> minUpdatedTime = cb.least(retrieveTask.get(RetrieveTask_.updatedTime));
        final Expression<Date> maxUpdatedTime = cb.greatest(retrieveTask.get(RetrieveTask_.updatedTime));
        final Expression<Long> completed;
        final Expression<Long> failed;
        final Expression<Long> warning;
        final Expression<Long> canceled;
        final Expression<Long> scheduled;
        final Expression<Long> inprocess;
        final Expression<Long> toschedule;
        final TaskQueryParam queueBatchQueryParam;
        final TaskQueryParam retrieveBatchQueryParam;

        ListRetrieveBatches(TaskQueryParam queueBatchQueryParam, TaskQueryParam retrieveBatchQueryParam) {
            this.queueBatchQueryParam = queueBatchQueryParam;
            this.retrieveBatchQueryParam = retrieveBatchQueryParam;
            if (queueBatchQueryParam.getStatus() != QueueMessage.Status.TO_SCHEDULE) {
                this.queueMsg = retrieveTask.join(RetrieveTask_.queueMessage,
                        queueBatchQueryParam.getStatus() == null ? JoinType.LEFT : JoinType.INNER);
                this.minProcessingStartTime = cb.least(queueMsg.get(QueueMessage_.processingStartTime));
                this.maxProcessingStartTime = cb.greatest(queueMsg.get(QueueMessage_.processingStartTime));
                this.minProcessingEndTime = cb.least(queueMsg.get(QueueMessage_.processingEndTime));
                this.maxProcessingEndTime = cb.greatest(queueMsg.get(QueueMessage_.processingEndTime));
                this.minScheduledTime = cb.least(queueMsg.get(QueueMessage_.scheduledTime));
                this.maxScheduledTime = cb.greatest(queueMsg.get(QueueMessage_.scheduledTime));
            }
            this.completed = statusSubquery(retrieveBatchQueryParam, retrieveTask, QueueMessage.Status.COMPLETED).getSelection();
            this.failed = statusSubquery(retrieveBatchQueryParam, retrieveTask, QueueMessage.Status.FAILED).getSelection();
            this.warning = statusSubquery(retrieveBatchQueryParam, retrieveTask, QueueMessage.Status.WARNING).getSelection();
            this.canceled = statusSubquery(retrieveBatchQueryParam, retrieveTask, QueueMessage.Status.CANCELED).getSelection();
            this.scheduled = statusSubquery(retrieveBatchQueryParam, retrieveTask, QueueMessage.Status.SCHEDULED).getSelection();
            this.inprocess = statusSubquery(retrieveBatchQueryParam, retrieveTask, QueueMessage.Status.IN_PROCESS).getSelection();
            this.toschedule= statusSubquery(retrieveBatchQueryParam, retrieveTask, QueueMessage.Status.TO_SCHEDULE).getSelection();
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
                        minCreatedTime, maxCreatedTime,
                        minUpdatedTime, maxUpdatedTime,
                        completed, failed, warning, canceled, scheduled, inprocess, toschedule);
            query.groupBy(retrieveTask.get(RetrieveTask_.batchID));
            List<Predicate> predicates = matchTask.retrieveBatchPredicates(retrieveTask, queueMsg, queueBatchQueryParam, retrieveBatchQueryParam);
            if (!predicates.isEmpty())
                query.where(predicates.toArray(new Predicate[0]));
            if (retrieveBatchQueryParam.getOrderBy() != null)
                query.orderBy(matchTask.retrieveBatchOrder(retrieveBatchQueryParam.getOrderBy(), retrieveTask));
        }

        RetrieveBatch toRetrieveBatch(Tuple tuple) {
            String batchID = tuple.get(batchIDPath);
            RetrieveBatch retrieveBatch = new RetrieveBatch(batchID);
            retrieveBatch.setCreatedTimeRange(
                    tuple.get(minCreatedTime),
                    tuple.get(maxCreatedTime));
            retrieveBatch.setUpdatedTimeRange(
                    tuple.get(minUpdatedTime),
                    tuple.get(maxUpdatedTime));

            if (queueBatchQueryParam.getStatus() != QueueMessage.Status.TO_SCHEDULE) {
                retrieveBatch.setProcessingStartTimeRange(
                        tuple.get(maxProcessingStartTime),
                        tuple.get(maxProcessingStartTime));
                retrieveBatch.setProcessingEndTimeRange(
                        tuple.get(minProcessingEndTime),
                        tuple.get(maxProcessingEndTime));
                retrieveBatch.setScheduledTimeRange(
                        tuple.get(minScheduledTime),
                        tuple.get(maxScheduledTime));
            }

            CriteriaQuery<String> distinct = cb.createQuery(String.class).distinct(true);
            Root<RetrieveTask> retrieveTask = distinct.from(RetrieveTask.class);
            From<RetrieveTask, QueueMessage> queueMsg = retrieveTask.join(RetrieveTask_.queueMessage,
                    queueBatchQueryParam.getStatus() != null && queueBatchQueryParam.getStatus() != QueueMessage.Status.TO_SCHEDULE
                            ? JoinType.INNER : JoinType.LEFT);
            distinct.where(predicates(queueMsg, retrieveTask, batchID));
            retrieveBatch.setDeviceNames(select(distinct, retrieveTask.get(RetrieveTask_.deviceName)));
            retrieveBatch.setQueueNames(select(distinct, retrieveTask.get(RetrieveTask_.queueName)));
            retrieveBatch.setLocalAETs(select(distinct, retrieveTask.get(RetrieveTask_.localAET)));
            retrieveBatch.setRemoteAETs(select(distinct, retrieveTask.get(RetrieveTask_.remoteAET)));
            retrieveBatch.setDestinationAETs(select(distinct, retrieveTask.get(RetrieveTask_.destinationAET)));
            retrieveBatch.setCompleted(tuple.get(completed));
            retrieveBatch.setCanceled(tuple.get(canceled));
            retrieveBatch.setWarning(tuple.get(warning));
            retrieveBatch.setFailed(tuple.get(failed));
            retrieveBatch.setScheduled(tuple.get(scheduled));
            retrieveBatch.setInProcess(tuple.get(inprocess));
            retrieveBatch.setToSchedule(tuple.get(toschedule));
            return retrieveBatch;
        }

        private Predicate[] predicates(Path<QueueMessage> queueMsg, Path<RetrieveTask> retrieveTask, String batchID) {
            List<Predicate> predicates = matchTask.retrieveBatchPredicates(
                    retrieveTask, queueMsg, queueBatchQueryParam, retrieveBatchQueryParam);
            predicates.add(cb.equal(retrieveTask.get(RetrieveTask_.batchID), batchID));
            return predicates.toArray(new Predicate[0]);
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
        CriteriaQuery<String> q = cb.createQuery(String.class);
        Root<RetrieveTask> retrieveTask = q.from(RetrieveTask.class);
        From<RetrieveTask, QueueMessage> queueMsg = retrieveTask.join(RetrieveTask_.queueMessage);
        List<Predicate> predicates = new MatchTask(cb).retrievePredicates(
                queueMsg, retrieveTask, queueTaskQueryParam, retrieveTaskQueryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));
        List<String> referencedQueueMsgIDs = em.createQuery(
                q.select(queueMsg.get(QueueMessage_.messageID)))
                .setMaxResults(deleteTasksFetchSize)
                .getResultList();

        referencedQueueMsgIDs.forEach(queueMsgID -> queueManager.deleteTask(queueMsgID, null));
        return referencedQueueMsgIDs.size();
    }

}
