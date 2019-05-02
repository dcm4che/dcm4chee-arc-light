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
import org.dcm4chee.arc.qmgt.HttpServletRequestInfo;
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
import javax.persistence.metamodel.SingularAttribute;
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

    public boolean scheduleRetrieveTask(int priority, ExternalRetrieveContext ctx,
            Date notRetrievedAfter, long delay)
            throws QueueSizeLimitExceededException {
        if (isAlreadyScheduledOrRetrievedAfter(em, ctx, notRetrievedAfter)) {
            return false;
        }
        try {
            ObjectMessage msg = queueManager.createObjectMessage(ctx.getKeys());
            msg.setStringProperty("LocalAET", ctx.getLocalAET());
            msg.setStringProperty("RemoteAET", ctx.getRemoteAET());
            msg.setIntProperty("Priority", priority);
            msg.setStringProperty("DestinationAET", ctx.getDestinationAET());
            msg.setStringProperty("StudyInstanceUID", ctx.getStudyInstanceUID());
            HttpServletRequestInfo.copyTo(ctx.getHttpServletRequestInfo(), msg);
            QueueMessage queueMessage = queueManager.scheduleMessage(ctx.getQueueName(), msg,
                    Message.DEFAULT_PRIORITY, ctx.getBatchID(), delay);
            createRetrieveTask(ctx, queueMessage);
            return true;
        } catch (JMSException e) {
            throw QueueMessage.toJMSRuntimeException(e);
        }
    }

    private boolean isAlreadyScheduledOrRetrievedAfter(EntityManager em, ExternalRetrieveContext ctx, Date retrievedAfter) {
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
        predicates.add(cb.equal(retrieveTask.get(RetrieveTask_.studyInstanceUID), ctx.getStudyInstanceUID()));
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

    public void createRetrieveTask(ExternalRetrieveContext ctx) {
        createRetrieveTask(ctx, null);
    }

    private void createRetrieveTask(ExternalRetrieveContext ctx, QueueMessage queueMessage) {
        RetrieveTask task = new RetrieveTask();
        task.setLocalAET(ctx.getLocalAET());
        task.setRemoteAET(ctx.getRemoteAET());
        task.setDestinationAET(ctx.getDestinationAET());
        task.setStudyInstanceUID(ctx.getStudyInstanceUID());
        task.setSeriesInstanceUID(ctx.getSeriesInstanceUID());
        task.setSOPInstanceUID(ctx.getSOPInstanceUID());
        task.setDeviceName(ctx.getDeviceName());
        task.setQueueName(ctx.getQueueName());
        task.setBatchID(ctx.getBatchID());
        task.setQueueMessage(queueMessage);
        em.persist(task);
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

    public String findDeviceNameByPk(Long pk) {
        try {
            return em.createNamedQuery(RetrieveTask.FIND_DEVICE_BY_PK, String.class)
                    .setParameter(1, pk)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    public void rescheduleRetrieveTask(Long pk, String newQueueName, QueueMessageEvent queueEvent) {
        RetrieveTask task = em.find(RetrieveTask.class, pk);
        if (task == null)
            return;

        QueueMessage queueMessage = task.getQueueMessage();
        if (queueMessage == null) {
            LOG.info("Schedule {}", task);
            scheduleRetrieveTask(0, createExtRetrieveCtx(task, queueEvent.getRequest()), null, 0L);
        } else {
            LOG.info("Reschedule {}", task);
            rescheduleRetrieveTask(task.getQueueMessage().getMessageID(), newQueueName, queueEvent);
        }
    }

    private ExternalRetrieveContext createExtRetrieveCtx(RetrieveTask task, HttpServletRequest request) {
        Attributes keys = toKeys(task);
        return new ExternalRetrieveContext()
                .setDeviceName(task.getDeviceName())
                .setQueueName(task.getQueueName())
                .setBatchID(task.getBatchID())
                .setLocalAET(task.getLocalAET())
                .setRemoteAET(task.getRemoteAET())
                .setDestinationAET(task.getDestinationAET())
                .setHttpServletRequestInfo(HttpServletRequestInfo.valueOf(request))
                .setKeys(keys);
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

    public void rescheduleRetrieveTask(String retrieveTaskQueueMsgId, String queueName, QueueMessageEvent queueEvent) {
        queueManager.rescheduleTask(retrieveTaskQueueMsgId, queueName, queueEvent);
    }

    public List<String> listDistinctDeviceNames(TaskQueryParam queueTaskQueryParam, TaskQueryParam retrieveTaskQueryParam) {
        return em.createQuery(
                select(QueueMessage_.deviceName, queueTaskQueryParam, retrieveTaskQueryParam).distinct(true))
                .getResultList();
    }

    public List<String> listRetrieveTaskQueueMsgIDs(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam retrieveTaskQueryParam, int limit) {
        return em.createQuery(select(QueueMessage_.messageID, queueTaskQueryParam, retrieveTaskQueryParam))
                .setMaxResults(limit)
                .getResultList();
    }

    public List<RetrieveBatch> listRetrieveBatches(
            TaskQueryParam queueBatchQueryParam, TaskQueryParam retrieveBatchQueryParam, int offset, int limit) {
        ListRetrieveBatches listRetrieveBatches = new ListRetrieveBatches(queueBatchQueryParam, retrieveBatchQueryParam);
        TypedQuery<Tuple> query = em.createQuery(listRetrieveBatches.query);
        if (offset > 0)
            query.setFirstResult(offset);
        if (limit > 0)
            query.setMaxResults(limit);

        return query.getResultStream().map(listRetrieveBatches::toRetrieveBatch).collect(Collectors.toList());
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
                    status == null && queueTaskQueryParam.getBatchID() == null
                            ? JoinType.LEFT : JoinType.INNER);
            predicates = matchTask.retrievePredicates(queueMsg, retrieveTask, queueTaskQueryParam, retrieveTaskQueryParam);
        }
        return predicates;
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

    private Subquery<Long> statusSubquery(TaskQueryParam queueBatchQueryParam, TaskQueryParam retrieveBatchQueryParam,
            From<RetrieveTask, QueueMessage> queueMsg, QueueMessage.Status status) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<QueueMessage> query = cb.createQuery(QueueMessage.class);
        Subquery<Long> sq = query.subquery(Long.class);
        Root<RetrieveTask> retrieveTask = sq.from(RetrieveTask.class);
        Join<RetrieveTask, QueueMessage> queueMsg1 = sq.correlate(retrieveTask.join(RetrieveTask_.queueMessage));
        MatchTask matchTask = new MatchTask(cb);
        List<Predicate> predicates = matchTask.retrieveBatchPredicates(
                queueMsg1, retrieveTask, queueBatchQueryParam, retrieveBatchQueryParam);
        predicates.add(cb.equal(queueMsg1.get(QueueMessage_.batchID), queueMsg.get(QueueMessage_.batchID)));
        predicates.add(cb.equal(queueMsg1.get(QueueMessage_.status), status));
        sq.where(predicates.toArray(new Predicate[0]));
        sq.select(cb.count(retrieveTask));
        return sq;
    }

    private class ListRetrieveBatches {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        final MatchTask matchTask = new MatchTask(cb);
        final CriteriaQuery<Tuple> query = cb.createTupleQuery();
        final Root<RetrieveTask> retrieveTask = query.from(RetrieveTask.class);
        final From<RetrieveTask, QueueMessage> queueMsg = retrieveTask.join(RetrieveTask_.queueMessage);
        final Path<String> batchIDPath = queueMsg.get(QueueMessage_.batchID);
        final Expression<Date> minProcessingStartTime = cb.least(queueMsg.get(QueueMessage_.processingStartTime));
        final Expression<Date> maxProcessingStartTime = cb.greatest(queueMsg.get(QueueMessage_.processingStartTime));
        final Expression<Date> minProcessingEndTime = cb.least(queueMsg.get(QueueMessage_.processingEndTime));
        final Expression<Date> maxProcessingEndTime = cb.greatest(queueMsg.get(QueueMessage_.processingEndTime));
        final Expression<Date> minScheduledTime = cb.least(queueMsg.get(QueueMessage_.scheduledTime));
        final Expression<Date> maxScheduledTime = cb.greatest(queueMsg.get(QueueMessage_.scheduledTime));
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
        final TaskQueryParam queueBatchQueryParam;
        final TaskQueryParam retrieveBatchQueryParam;

        ListRetrieveBatches(TaskQueryParam queueBatchQueryParam, TaskQueryParam retrieveBatchQueryParam) {
            this.queueBatchQueryParam = queueBatchQueryParam;
            this.retrieveBatchQueryParam = retrieveBatchQueryParam;
            this.completed = statusSubquery(queueBatchQueryParam, retrieveBatchQueryParam,
                    queueMsg, QueueMessage.Status.COMPLETED).getSelection();
            this.failed = statusSubquery(queueBatchQueryParam, retrieveBatchQueryParam,
                    queueMsg, QueueMessage.Status.FAILED).getSelection();
            this.warning = statusSubquery(queueBatchQueryParam, retrieveBatchQueryParam,
                    queueMsg, QueueMessage.Status.WARNING).getSelection();
            this.canceled = statusSubquery(queueBatchQueryParam, retrieveBatchQueryParam,
                    queueMsg, QueueMessage.Status.CANCELED).getSelection();
            this.scheduled = statusSubquery(queueBatchQueryParam, retrieveBatchQueryParam,
                    queueMsg, QueueMessage.Status.SCHEDULED).getSelection();
            this.inprocess = statusSubquery(queueBatchQueryParam, retrieveBatchQueryParam,
                    queueMsg, QueueMessage.Status.IN_PROCESS).getSelection();
            query.multiselect(batchIDPath,
                    minProcessingStartTime, maxProcessingStartTime,
                    minProcessingEndTime, maxProcessingEndTime,
                    minScheduledTime, maxScheduledTime,
                    minCreatedTime, maxCreatedTime,
                    minUpdatedTime, maxUpdatedTime,
                    completed, failed, warning, canceled, scheduled, inprocess);
            query.groupBy(queueMsg.get(QueueMessage_.batchID));
            List<Predicate> predicates = matchTask.retrieveBatchPredicates(
                    queueMsg, retrieveTask, queueBatchQueryParam, retrieveBatchQueryParam);
            if (!predicates.isEmpty())
                query.where(predicates.toArray(new Predicate[0]));
            if (retrieveBatchQueryParam.getOrderBy() != null)
                query.orderBy(matchTask.retrieveBatchOrder(retrieveBatchQueryParam.getOrderBy(), retrieveTask));
        }

        RetrieveBatch toRetrieveBatch(Tuple tuple) {
            String batchID = tuple.get(batchIDPath);
            RetrieveBatch retrieveBatch = new RetrieveBatch(batchID);
            retrieveBatch.setProcessingStartTimeRange(
                    tuple.get(maxProcessingStartTime),
                    tuple.get(maxProcessingStartTime));
            retrieveBatch.setProcessingEndTimeRange(
                    tuple.get(minProcessingEndTime),
                    tuple.get(maxProcessingEndTime));
            retrieveBatch.setScheduledTimeRange(
                    tuple.get(minScheduledTime),
                    tuple.get(maxScheduledTime));
            retrieveBatch.setCreatedTimeRange(
                    tuple.get(minCreatedTime),
                    tuple.get(maxCreatedTime));
            retrieveBatch.setUpdatedTimeRange(
                    tuple.get(minUpdatedTime),
                    tuple.get(maxUpdatedTime));

            CriteriaQuery<String> distinct = cb.createQuery(String.class).distinct(true);
            Root<RetrieveTask> retrieveTask = distinct.from(RetrieveTask.class);
            From<RetrieveTask, QueueMessage> queueMsg = retrieveTask.join(RetrieveTask_.queueMessage);
            distinct.where(predicates(queueMsg, retrieveTask, batchID));
            retrieveBatch.setDeviceNames(select(distinct, queueMsg.get(QueueMessage_.deviceName)));
            retrieveBatch.setQueueNames(select(distinct, queueMsg.get(QueueMessage_.queueName)));
            retrieveBatch.setLocalAETs(select(distinct, retrieveTask.get(RetrieveTask_.localAET)));
            retrieveBatch.setRemoteAETs(select(distinct, retrieveTask.get(RetrieveTask_.remoteAET)));
            retrieveBatch.setDestinationAETs(select(distinct, retrieveTask.get(RetrieveTask_.destinationAET)));
            retrieveBatch.setCompleted(tuple.get(completed));
            retrieveBatch.setCanceled(tuple.get(canceled));
            retrieveBatch.setWarning(tuple.get(warning));
            retrieveBatch.setFailed(tuple.get(failed));
            retrieveBatch.setScheduled(tuple.get(scheduled));
            retrieveBatch.setInProcess(tuple.get(inprocess));
            return retrieveBatch;
        }

        private Predicate[] predicates(Path<QueueMessage> queueMsg, Path<RetrieveTask> retrieveTask, String batchID) {
            List<Predicate> predicates = matchTask.retrieveBatchPredicates(
                    queueMsg, retrieveTask, queueBatchQueryParam, retrieveBatchQueryParam);
            predicates.add(cb.equal(queueMsg.get(QueueMessage_.batchID), batchID));
            return predicates.toArray(new Predicate[0]);
        }

        private List<String> select(CriteriaQuery<String> query, Path<String> path) {
            return em.createQuery(query.select(path)).getResultList();
        }
    }

    private CriteriaQuery<String> select(SingularAttribute<QueueMessage, String> attribute,
                                         TaskQueryParam queueTaskQueryParam, TaskQueryParam retrieveTaskQueryParam) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<String> q = cb.createQuery(String.class);
        Root<RetrieveTask> retrieveTask = q.from(RetrieveTask.class);
        From<RetrieveTask, QueueMessage> queueMsg = retrieveTask.join(RetrieveTask_.queueMessage);
        List<Predicate> predicates = new MatchTask(cb).retrievePredicates(
                queueMsg, retrieveTask, queueTaskQueryParam, retrieveTaskQueryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));
        return q.select(queueMsg.get(attribute));
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

//        List<String> referencedQueueMsgIDs = em.createQuery(
//                select(QueueMessage_.messageID, queueTaskQueryParam, retrieveTaskQueryParam))
//                .setMaxResults(deleteTasksFetchSize)
//                .getResultList();
//
//        referencedQueueMsgIDs.forEach(queueMsgID -> queueManager.deleteTask(queueMsgID, null));
//        return referencedQueueMsgIDs.size();
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
        List<String> referencedQueueMsgIDs = em.createQuery(
                select(QueueMessage_.messageID, queueTaskQueryParam, retrieveTaskQueryParam))
                .setMaxResults(deleteTasksFetchSize)
                .getResultList();

        referencedQueueMsgIDs.forEach(queueMsgID -> queueManager.deleteTask(queueMsgID, null));
        return referencedQueueMsgIDs.size();
    }

}
