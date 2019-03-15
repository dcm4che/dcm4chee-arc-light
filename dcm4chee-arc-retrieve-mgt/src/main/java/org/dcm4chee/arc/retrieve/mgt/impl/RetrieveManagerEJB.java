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

import javax.persistence.criteria.Expression;
import javax.persistence.Tuple;
import com.querydsl.core.types.Predicate;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.event.QueueMessageEvent;
import org.dcm4chee.arc.qmgt.HttpServletRequestInfo;
import org.dcm4chee.arc.qmgt.IllegalTaskStateException;
import org.dcm4chee.arc.qmgt.QueueManager;
import org.dcm4chee.arc.qmgt.QueueSizeLimitExceededException;
import org.dcm4chee.arc.query.util.MatchTask;
import org.dcm4chee.arc.query.util.TaskQueryParam;
import org.dcm4chee.arc.retrieve.ExternalRetrieveContext;
import org.dcm4chee.arc.retrieve.mgt.RetrieveBatch;
import org.dcm4chee.arc.retrieve.mgt.RetrieveManager;
import org.hibernate.annotations.QueryHints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.persistence.*;
import javax.persistence.criteria.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Oct 2017
 */
@Stateless
public class RetrieveManagerEJB {
    private static final Logger LOG = LoggerFactory.getLogger(RetrieveManagerEJB.class);

    private Join<RetrieveTask, QueueMessage> queueMsg;
    private Root<RetrieveTask> retrieveTask;

    @PersistenceContext(unitName = "dcm4chee-arc")
    private EntityManager em;

    @Inject
    private QueueManager queueManager;

    @Inject
    private Device device;

    public boolean scheduleRetrieveTask(int priority, ExternalRetrieveContext ctx, String batchID,
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
            QueueMessage queueMessage = queueManager.scheduleMessage(RetrieveManager.QUEUE_NAME, msg,
                    Message.DEFAULT_PRIORITY, batchID, delay);
            createRetrieveTask(ctx, queueMessage);
            return true;
        } catch (JMSException e) {
            throw QueueMessage.toJMSRuntimeException(e);
        }
    }

    private boolean isAlreadyScheduledOrRetrievedAfter(EntityManager em, ExternalRetrieveContext ctx, Date retrievedAfter) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<RetrieveTask> q = cb.createQuery(RetrieveTask.class);
        retrieveTask = q.from(RetrieveTask.class);
        queueMsg = retrieveTask.join(RetrieveTask_.queueMessage);

        List<javax.persistence.criteria.Predicate> predicates = new ArrayList<>();
        javax.persistence.criteria.Predicate statusPredicate = queueMsg.get(QueueMessage_.status)
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
                .where(predicates.toArray(new javax.persistence.criteria.Predicate[0]))
                .select(retrieveTask))
                .getResultStream()
                .iterator();
        if (iterator.hasNext()) {
            LOG.info("Previous {} found - suppress duplicate retrieve", iterator.next());
            return true;
        }
        return false;
    }

    private void createRetrieveTask(ExternalRetrieveContext ctx, QueueMessage queueMessage) {
        RetrieveTask task = new RetrieveTask();
        task.setLocalAET(ctx.getLocalAET());
        task.setRemoteAET(ctx.getRemoteAET());
        task.setDestinationAET(ctx.getDestinationAET());
        task.setStudyInstanceUID(ctx.getStudyInstanceUID());
        task.setSeriesInstanceUID(ctx.getSeriesInstanceUID());
        task.setSOPInstanceUID(ctx.getSOPInstanceUID());
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

    private CriteriaQuery<String> createQuery(TaskQueryParam queueTaskQueryParam, TaskQueryParam retrieveTaskQueryParam) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        MatchTask matchTask = new MatchTask(cb);
        CriteriaQuery<String> q = cb.createQuery(String.class);
        retrieveTask = q.from(RetrieveTask.class);
        queueMsg = retrieveTask.join(RetrieveTask_.queueMessage);
        return restrict(queueTaskQueryParam, retrieveTaskQueryParam, matchTask, q);
    }

    public boolean deleteRetrieveTask(Long pk, QueueMessageEvent queueEvent) {
        RetrieveTask task = em.find(RetrieveTask.class, pk);
        if (task == null)
            return false;

        queueManager.deleteTask(task.getQueueMessage().getMessageID(), queueEvent);
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
    
    public long cancelRetrieveTasks(Predicate matchQueueMessage, Predicate matchRetrieveTask, QueueMessage.Status prev)
            throws IllegalTaskStateException {
        return queueManager.cancelRetrieveTasks(matchQueueMessage, matchRetrieveTask, prev);
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

    public void rescheduleRetrieveTask(Long pk, QueueMessageEvent queueEvent) {
        RetrieveTask task = em.find(RetrieveTask.class, pk);
        if (task == null)
            return;

        LOG.info("Reschedule {}", task);
        rescheduleRetrieveTask(task.getQueueMessage().getMessageID(), queueEvent);
    }

    public void rescheduleRetrieveTask(String retrieveTaskQueueMsgId, QueueMessageEvent queueEvent) {
        queueManager.rescheduleTask(retrieveTaskQueueMsgId, RetrieveManager.QUEUE_NAME, queueEvent);
    }

    public List<String> listDistinctDeviceNames(TaskQueryParam queueTaskQueryParam, TaskQueryParam retrieveTaskQueryParam) {
        return em.createQuery(createQuery(queueTaskQueryParam, retrieveTaskQueryParam)
                .select(queueMsg.get(QueueMessage_.deviceName))
                .distinct(true))
                .getResultList();
    }

    public List<String> listRetrieveTaskQueueMsgIDs(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam retrieveTaskQueryParam, int limit) {
        return em.createQuery(createQuery(queueTaskQueryParam, retrieveTaskQueryParam)
                .select(queueMsg.get(QueueMessage_.messageID)))
                .setMaxResults(limit)
                .getResultList();
    }

    public List<RetrieveBatch> listRetrieveBatches(
            TaskQueryParam queueBatchQueryParam, TaskQueryParam retrieveBatchQueryParam, int offset, int limit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        MatchTask matchTask = new MatchTask(cb);
        CriteriaQuery<javax.persistence.Tuple> q = cb.createTupleQuery();
        retrieveTask = q.from(RetrieveTask.class);
        queueMsg = retrieveTask.join(RetrieveTask_.queueMessage);

        Expression<Date> minProcessingStartTime = cb.least(queueMsg.get(QueueMessage_.processingStartTime));
        Expression<Date> maxProcessingStartTime = cb.greatest(queueMsg.get(QueueMessage_.processingStartTime));
        Expression<Date> minProcessingEndTime = cb.least(queueMsg.get(QueueMessage_.processingEndTime));
        Expression<Date> maxProcessingEndTime = cb.greatest(queueMsg.get(QueueMessage_.processingEndTime));
        Expression<Date> minScheduledTime = cb.least(queueMsg.get(QueueMessage_.scheduledTime));
        Expression<Date> maxScheduledTime = cb.greatest(queueMsg.get(QueueMessage_.scheduledTime));
        Expression<Date> minCreatedTime = cb.least(retrieveTask.get(RetrieveTask_.createdTime));
        Expression<Date> maxCreatedTime = cb.greatest(retrieveTask.get(RetrieveTask_.createdTime));
        Expression<Date> minUpdatedTime = cb.least(retrieveTask.get(RetrieveTask_.updatedTime));
        Expression<Date> maxUpdatedTime = cb.greatest(retrieveTask.get(RetrieveTask_.updatedTime));
        Path<String> batchid = queueMsg.get(QueueMessage_.batchID);

        CriteriaQuery<Tuple> multiselect = orderBatch(
                retrieveBatchQueryParam,
                matchTask,
                groupBy(restrictBatch(queueBatchQueryParam, retrieveBatchQueryParam, matchTask, q)))
                .multiselect(minProcessingStartTime,
                        maxProcessingStartTime,
                        minProcessingEndTime,
                        maxProcessingEndTime,
                        minScheduledTime,
                        maxScheduledTime,
                        minCreatedTime,
                        maxCreatedTime,
                        minUpdatedTime,
                        maxUpdatedTime,
                        batchid);

        TypedQuery<Tuple> query = em.createQuery(multiselect);
        if (offset > 0)
            query.setFirstResult(offset);
        if (limit > 0)
            query.setMaxResults(limit);

        List<RetrieveBatch> retrieveBatches = new ArrayList<>();
        query.getResultList().forEach(batch -> {
            String batchID = batch.get(batchid);
            RetrieveBatch retrieveBatch = new RetrieveBatch(batchID);
            retrieveBatch.setProcessingStartTimeRange(
                    batch.get(maxProcessingStartTime),
                    batch.get(maxProcessingStartTime));
            retrieveBatch.setProcessingEndTimeRange(
                    batch.get(minProcessingEndTime),
                    batch.get(maxProcessingEndTime));
            retrieveBatch.setScheduledTimeRange(
                    batch.get(minScheduledTime),
                    batch.get(maxScheduledTime));
            retrieveBatch.setCreatedTimeRange(
                    batch.get(minCreatedTime),
                    batch.get(maxCreatedTime));
            retrieveBatch.setUpdatedTimeRange(
                    batch.get(minUpdatedTime),
                    batch.get(maxUpdatedTime));

            retrieveBatch.setDeviceNames(em.createQuery(
                    batchIDQuery(batchID, String.class)
                            .select(queueMsg.get(QueueMessage_.deviceName))
                            .distinct(true)
                            .orderBy(cb.asc(queueMsg.get(QueueMessage_.deviceName))))
                    .getResultList());
            retrieveBatch.setLocalAETs(em.createQuery(
                    batchIDQuery(batchID, String.class)
                            .select(retrieveTask.get(RetrieveTask_.localAET))
                            .distinct(true)
                            .orderBy(cb.asc(retrieveTask.get(RetrieveTask_.localAET))))
                    .getResultList());
            retrieveBatch.setRemoteAETs(em.createQuery(
                    batchIDQuery(batchID, String.class)
                            .select(retrieveTask.get(RetrieveTask_.remoteAET))
                            .distinct(true)
                            .orderBy(cb.asc(retrieveTask.get(RetrieveTask_.remoteAET))))
                    .getResultList());
            retrieveBatch.setDestinationAETs(em.createQuery(
                    batchIDQuery(batchID, String.class)
                            .select(retrieveTask.get(RetrieveTask_.destinationAET))
                            .distinct(true)
                            .orderBy(cb.asc(retrieveTask.get(RetrieveTask_.destinationAET))))
                    .getResultList());

            retrieveBatch.setCompleted(em.createQuery(
                    batchIDQuery(batchID, Long.class)
                            .where(cb.equal(queueMsg.get(QueueMessage_.status), QueueMessage.Status.COMPLETED))
                            .select(cb.count(queueMsg)))
                    .getSingleResult());
            retrieveBatch.setCanceled(em.createQuery(
                    batchIDQuery(batchID, Long.class)
                            .where(cb.equal(queueMsg.get(QueueMessage_.status), QueueMessage.Status.CANCELED))
                            .select(cb.count(queueMsg)))
                    .getSingleResult());
            retrieveBatch.setWarning(em.createQuery(
                    batchIDQuery(batchID, Long.class)
                            .where(cb.equal(queueMsg.get(QueueMessage_.status), QueueMessage.Status.WARNING))
                            .select(cb.count(queueMsg)))
                    .getSingleResult());
            retrieveBatch.setFailed(em.createQuery(
                    batchIDQuery(batchID, Long.class)
                            .where(cb.equal(queueMsg.get(QueueMessage_.status), QueueMessage.Status.FAILED))
                            .select(cb.count(queueMsg)))
                    .getSingleResult());
            retrieveBatch.setScheduled(em.createQuery(
                    batchIDQuery(batchID, Long.class)
                            .where(cb.equal(queueMsg.get(QueueMessage_.status), QueueMessage.Status.SCHEDULED))
                            .select(cb.count(queueMsg)))
                    .getSingleResult());
            retrieveBatch.setInProcess(em.createQuery(
                    batchIDQuery(batchID, Long.class)
                            .where(cb.equal(queueMsg.get(QueueMessage_.status), QueueMessage.Status.IN_PROCESS))
                            .select(cb.count(queueMsg)))
                    .getSingleResult());

            retrieveBatches.add(retrieveBatch);
        });

        return retrieveBatches;
    }

    private <T> CriteriaQuery<T> restrictBatch(
            TaskQueryParam queueBatchQueryParam, TaskQueryParam retrieveBatchQueryParam, MatchTask matchTask, CriteriaQuery<T> q) {
        List<javax.persistence.criteria.Predicate> predicates = matchTask.retrieveBatchPredicates(
                queueMsg,
                retrieveTask,
                queueBatchQueryParam,
                retrieveBatchQueryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new javax.persistence.criteria.Predicate[0]));
        return q;
    }

    private <T> CriteriaQuery<T> orderBatch(
            TaskQueryParam retrieveBatchQueryParam, MatchTask matchTask, CriteriaQuery<T> q) {
        if (retrieveBatchQueryParam.getOrderBy() != null)
            q = q.orderBy(matchTask.retrieveBatchOrder(retrieveBatchQueryParam.getOrderBy(), retrieveTask));
        return q;
    }

    private <T> CriteriaQuery<T> groupBy(CriteriaQuery<T> q) {
        return q.groupBy(queueMsg.get(QueueMessage_.batchID));
    }

    private <T> CriteriaQuery<T> batchIDQuery(String batchID, Class<T> clazz) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<T> q = cb.createQuery(clazz);
        retrieveTask = q.from(RetrieveTask.class);
        queueMsg = retrieveTask.join(RetrieveTask_.queueMessage, JoinType.LEFT);
        return q.where(cb.equal(queueMsg.get(QueueMessage_.batchID), batchID));
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Iterator<RetrieveTask> listRetrieveTasks(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam retrieveTaskQueryParam, int offset, int limit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        MatchTask matchTask = new MatchTask(cb);
        TypedQuery<RetrieveTask> query = em.createQuery(select(cb, matchTask, queueTaskQueryParam, retrieveTaskQueryParam))
                .setHint(QueryHints.FETCH_SIZE, queryFetchSize());
        if (offset > 0)
            query.setFirstResult(offset);
        if (limit > 0)
            query.setMaxResults(limit);
        return query.getResultStream().iterator();
    }

    public long countTasks(TaskQueryParam queueTaskQueryParam, TaskQueryParam retrieveTaskQueryParam) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        MatchTask matchTask = new MatchTask(cb);

        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        retrieveTask = q.from(RetrieveTask.class);
        queueMsg = retrieveTask.join(RetrieveTask_.queueMessage);

        return em.createQuery(
                restrict(queueTaskQueryParam, retrieveTaskQueryParam, matchTask, q).select(cb.count(retrieveTask)))
                .getSingleResult();
    }

    private <T> CriteriaQuery<T> restrict(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam retrieveTaskQueryParam, MatchTask matchTask, CriteriaQuery<T> q) {
        List<javax.persistence.criteria.Predicate> predicates = matchTask.retrievePredicates(
                queueMsg,
                retrieveTask,
                queueTaskQueryParam,
                retrieveTaskQueryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new javax.persistence.criteria.Predicate[0]));
        return q;
    }

    private int queryFetchSize() {
        return device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class).getQueryFetchSize();
    }

    private CriteriaQuery<RetrieveTask> select(CriteriaBuilder cb,
            MatchTask matchTask, TaskQueryParam queueTaskQueryParam, TaskQueryParam retrieveTaskQueryParam) {
        CriteriaQuery<RetrieveTask> q = cb.createQuery(RetrieveTask.class);
        retrieveTask = q.from(RetrieveTask.class);
        queueMsg = retrieveTask.join(RetrieveTask_.queueMessage);

        q = restrict(queueTaskQueryParam, retrieveTaskQueryParam, matchTask, q);
        if (retrieveTaskQueryParam.getOrderBy() != null)
            q.orderBy(matchTask.retrieveTaskOrder(retrieveTaskQueryParam.getOrderBy(), retrieveTask));

        return q.select(retrieveTask);
    }

    public int deleteTasks(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam retrieveTaskQueryParam, int deleteTasksFetchSize) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        MatchTask matchTask = new MatchTask(cb);
        CriteriaQuery<String> q = cb.createQuery(String.class);
        retrieveTask = q.from(RetrieveTask.class);
        queueMsg = retrieveTask.join(RetrieveTask_.queueMessage);

        TypedQuery<String> query = em.createQuery(restrict(queueTaskQueryParam, retrieveTaskQueryParam, matchTask, q)
                .multiselect(queueMsg.get(QueueMessage_.messageID)));

        if (deleteTasksFetchSize > 0)
            query.setMaxResults(deleteTasksFetchSize);

        List<String> referencedQueueMsgIDs = query.getResultList();
        referencedQueueMsgIDs.forEach(queueMsgID -> queueManager.deleteTask(queueMsgID, null));
        return referencedQueueMsgIDs.size();
    }

}
