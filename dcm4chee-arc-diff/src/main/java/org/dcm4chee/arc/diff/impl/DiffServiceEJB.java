/*
 * **** BEGIN LICENSE BLOCK *****
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
 * Portions created by the Initial Developer are Copyright (C) 2015-2019
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
 * **** END LICENSE BLOCK *****
 *
 */

package org.dcm4chee.arc.diff.impl;

import javax.persistence.criteria.Expression;
import javax.persistence.Tuple;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.diff.*;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.event.QueueMessageEvent;
import org.dcm4chee.arc.qmgt.IllegalTaskStateException;
import org.dcm4chee.arc.qmgt.QueueManager;
import org.dcm4chee.arc.qmgt.QueueSizeLimitExceededException;
import org.dcm4chee.arc.query.util.MatchTask;
import org.dcm4chee.arc.query.util.TaskQueryParam;
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
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Mar 2018
 */
@Stateless
public class DiffServiceEJB {

    private static final Logger LOG = LoggerFactory.getLogger(DiffServiceEJB.class);

    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    private Join<DiffTask, QueueMessage> queueMsg;
    private Root<DiffTask> diffTask;

    @Inject
    private Device device;

    @Inject
    private QueueManager queueManager;

    public void scheduleDiffTask(DiffContext ctx) throws QueueSizeLimitExceededException {
        try {
            ObjectMessage msg = queueManager.createObjectMessage(0);
            msg.setStringProperty("LocalAET", ctx.getLocalAE().getAETitle());
            msg.setStringProperty("PrimaryAET", ctx.getPrimaryAE().getAETitle());
            msg.setStringProperty("SecondaryAET", ctx.getSecondaryAE().getAETitle());
            msg.setIntProperty("Priority", ctx.priority());
            msg.setStringProperty("QueryString", ctx.getQueryString());
            if (ctx.getHttpServletRequestInfo() != null)
                ctx.getHttpServletRequestInfo().copyTo(msg);
            QueueMessage queueMessage = queueManager.scheduleMessage(DiffService.QUEUE_NAME, msg,
                    Message.DEFAULT_PRIORITY, ctx.getBatchID(), 0L);
            createDiffTask(ctx, queueMessage);
        } catch (JMSException e) {
            throw QueueMessage.toJMSRuntimeException(e);
        }
    }

    private void createDiffTask(DiffContext ctx, QueueMessage queueMessage) {
        DiffTask task = new DiffTask();
        task.setLocalAET(ctx.getLocalAE().getAETitle());
        task.setPrimaryAET(ctx.getPrimaryAE().getAETitle());
        task.setSecondaryAET(ctx.getSecondaryAE().getAETitle());
        task.setQueryString(ctx.getQueryString());
        task.setCheckMissing(ctx.isCheckMissing());
        task.setCheckDifferent(ctx.isCheckDifferent());
        task.setCompareFields(ctx.getCompareFields());
        task.setQueueMessage(queueMessage);
        em.persist(task);
    }

    public void resetDiffTask(DiffTask diffTask) {
        diffTask = em.find(DiffTask.class, diffTask.getPk());
        diffTask.reset();
        diffTask.getDiffTaskAttributes().forEach(entity -> em.remove(entity));
    }

    public void addDiffTaskAttributes(DiffTask diffTask, Attributes attrs) {
        DiffTaskAttributes entity = new DiffTaskAttributes();
        entity.setDiffTask(diffTask);
        entity.setAttributes(attrs);
        em.persist(entity);
    }

    public void updateDiffTask(DiffTask diffTask, DiffSCU diffSCU) {
        diffTask = em.find(DiffTask.class, diffTask.getPk());
        if (diffTask != null) {
            diffTask.setMatches(diffSCU.matches());
            diffTask.setMissing(diffSCU.missing());
            diffTask.setDifferent(diffSCU.different());
        }
    }

    public DiffTask getDiffTask(long taskPK) {
        return em.find(DiffTask.class, taskPK);
    }

    public boolean deleteDiffTask(Long pk, QueueMessageEvent queueEvent) {
        DiffTask task = em.find(DiffTask.class, pk);
        if (task == null)
            return false;

        queueManager.deleteTask(task.getQueueMessage().getMessageID(), queueEvent);
        LOG.info("Delete {}", task);
        return true;
    }

    public int deleteTasks(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam diffTaskQueryParam, int deleteTasksFetchSize) {
        List<String> referencedQueueMsgIDs = em.createQuery(createQuery(queueTaskQueryParam, diffTaskQueryParam)
                .select(queueMsg.get(QueueMessage_.messageID)))
                .setMaxResults(deleteTasksFetchSize)
                .getResultList();

        for (String queueMsgID : referencedQueueMsgIDs)
            queueManager.deleteTask(queueMsgID, null);

        return referencedQueueMsgIDs.size();
    }

    public List<String> listDistinctDeviceNames(TaskQueryParam queueTaskQueryParam, TaskQueryParam diffTaskQueryParam) {
        return em.createQuery(createQuery(queueTaskQueryParam, diffTaskQueryParam)
                .select(queueMsg.get(QueueMessage_.deviceName))
                .distinct(true))
                .getResultList();
    }

    public List<String> listDiffTaskQueueMsgIDs(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam diffTaskQueryParam, int limit) {
        return em.createQuery(createQuery(queueTaskQueryParam, diffTaskQueryParam)
                .select(queueMsg.get(QueueMessage_.messageID)))
                .setMaxResults(limit)
                .getResultList();
    }

    private CriteriaQuery<String> createQuery(TaskQueryParam queueTaskQueryParam, TaskQueryParam diffTaskQueryParam) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        MatchTask matchTask = new MatchTask(cb);
        CriteriaQuery<String> q = cb.createQuery(String.class);
        diffTask = q.from(DiffTask.class);
        queueMsg = diffTask.join(DiffTask_.queueMessage);
        return restrict(queueTaskQueryParam, diffTaskQueryParam, matchTask, q);
    }

    public long diffTasksOfBatch(String batchID) {
        return em.createQuery(batchIDQuery(batchID, Long.class)
                .select(em.getCriteriaBuilder().count(diffTask)))
                .getSingleResult();
    }

    public boolean cancelDiffTask(Long pk, QueueMessageEvent queueEvent) throws IllegalTaskStateException {
        DiffTask task = em.find(DiffTask.class, pk);
        if (task == null)
            return false;

        QueueMessage queueMessage = task.getQueueMessage();
        if (queueMessage == null)
            throw new IllegalTaskStateException("Cannot cancel Task with status: 'TO SCHEDULE'");

        queueManager.cancelTask(queueMessage.getMessageID(), queueEvent);
        LOG.info("Cancel {}", task);
        return true;
    }

    public long cancelDiffTasks(TaskQueryParam queueTaskQueryParam, TaskQueryParam diffTaskQueryParam) {
        return queueManager.cancelDiffTasks(queueTaskQueryParam, diffTaskQueryParam);
    }

    public void rescheduleDiffTask(Long pk, QueueMessageEvent queueEvent) {
        DiffTask task = em.find(DiffTask.class, pk);
        if (task == null)
            return;

        LOG.info("Reschedule {}", task);
        rescheduleDiffTask(task.getQueueMessage().getMessageID(), queueEvent);
    }

    public void rescheduleDiffTask(String msgId, QueueMessageEvent queueEvent) {
        queueManager.rescheduleTask(msgId, DiffService.QUEUE_NAME, queueEvent);
    }

    public String findDeviceNameByPk(Long pk) {
        try {
            return em.createNamedQuery(DiffTask.FIND_DEVICE_BY_PK, String.class)
                    .setParameter(1, pk)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    public List<AttributesBlob> getDiffTaskAttributes(DiffTask diffTask, int offset, int limit) {
        return em.createNamedQuery(DiffTaskAttributes.FIND_BY_DIFF_TASK, AttributesBlob.class)
                .setParameter(1, diffTask)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }

    public List<AttributesBlob> getDiffTaskAttributes(
            TaskQueryParam queueBatchQueryParam, TaskQueryParam diffBatchQueryParam, int offset, int limit) {
        Subquery<DiffTask> sq = referencedDiffTasks(referencedQueueMsgs(queueBatchQueryParam, diffBatchQueryParam));

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<AttributesBlob> q = cb.createQuery(AttributesBlob.class);
        Root<DiffTaskAttributes> diffTaskAttrs = q.from(DiffTaskAttributes.class);
        TypedQuery<AttributesBlob> query = em.createQuery(q.where(diffTaskAttrs.get(DiffTaskAttributes_.diffTask).in(sq))
                .select(diffTaskAttrs.get(DiffTaskAttributes_.attributesBlob)));

        if (offset > 0)
            query.setFirstResult(offset);
        if (limit > 0)
            query.setMaxResults(limit);
        return query.getResultList();

    }

    private Subquery<DiffTask> referencedDiffTasks(Subquery<QueueMessage> sq) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        CriteriaQuery<DiffTaskAttributes> q1 = cb.createQuery(DiffTaskAttributes.class);
        Subquery<DiffTask> sq1 = q1.subquery(DiffTask.class);
        Root<DiffTaskAttributes> diffTaskAttrs = sq1.from(DiffTaskAttributes.class);
        Join<DiffTaskAttributes, DiffTask> diffTask = sq1.correlate(diffTaskAttrs.join(DiffTaskAttributes_.diffTask));

        CriteriaQuery<DiffTask> q2 = cb.createQuery(DiffTask.class);
        Subquery<DiffTask> sq2 = q2.subquery(DiffTask.class);
        sq2.from(DiffTask.class);

        return sq1.where(diffTaskAttrs.get(DiffTaskAttributes_.diffTask)
                    .in(sq2.where(diffTask.get(DiffTask_.queueMessage).in(sq)).select(diffTask)))
                .select(diffTaskAttrs.get(DiffTaskAttributes_.diffTask));
    }

    private Subquery<QueueMessage> referencedQueueMsgs(
            TaskQueryParam queueBatchQueryParam, TaskQueryParam diffBatchQueryParam) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        MatchTask matchTask = new MatchTask(cb);

        CriteriaQuery<DiffTask> q = cb.createQuery(DiffTask.class);
        Subquery<QueueMessage> sq = q.subquery(QueueMessage.class);
        Root<DiffTask> diffTask = sq.from(DiffTask.class);
        Join<DiffTask, QueueMessage> queueMsg = sq.correlate(diffTask.join(DiffTask_.queueMessage));

        List<Predicate> predicates = matchTask.diffBatchPredicates(
                queueMsg, diffTask, queueBatchQueryParam, diffBatchQueryParam);
        if (!predicates.isEmpty())
            sq.where(predicates.toArray(new Predicate[0]));

        return sq.select(diffTask.get(DiffTask_.queueMessage));
    }

    public List<DiffBatch> listDiffBatches(
            TaskQueryParam queueBatchQueryParam, TaskQueryParam diffBatchQueryParam, int offset, int limit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        MatchTask matchTask = new MatchTask(cb);
        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        diffTask = q.from(DiffTask.class);
        queueMsg = diffTask.join(DiffTask_.queueMessage);

        Expression<Date> minProcessingStartTime = cb.least(queueMsg.get(QueueMessage_.processingStartTime));
        Expression<Date> maxProcessingStartTime = cb.greatest(queueMsg.get(QueueMessage_.processingStartTime));
        Expression<Date> minProcessingEndTime = cb.least(queueMsg.get(QueueMessage_.processingEndTime));
        Expression<Date> maxProcessingEndTime = cb.greatest(queueMsg.get(QueueMessage_.processingEndTime));
        Expression<Date> minScheduledTime = cb.least(queueMsg.get(QueueMessage_.scheduledTime));
        Expression<Date> maxScheduledTime = cb.greatest(queueMsg.get(QueueMessage_.scheduledTime));
        Expression<Date> minCreatedTime = cb.least(diffTask.get(DiffTask_.createdTime));
        Expression<Date> maxCreatedTime = cb.greatest(diffTask.get(DiffTask_.createdTime));
        Expression<Date> minUpdatedTime = cb.least(diffTask.get(DiffTask_.updatedTime));
        Expression<Date> maxUpdatedTime = cb.greatest(diffTask.get(DiffTask_.updatedTime));
        Expression<Long> matches = cb.sumAsLong((diffTask.get(DiffTask_.matches)));
        Expression<Long> missing = cb.sumAsLong(diffTask.get(DiffTask_.missing));
        Expression<Long> different = cb.sumAsLong(diffTask.get(DiffTask_.different));
        Path<String> batchid = queueMsg.get(QueueMessage_.batchID);

        CriteriaQuery<Tuple> multiselect = orderBatch(
                diffBatchQueryParam,
                matchTask,
                groupBy(restrictBatch(queueBatchQueryParam, diffBatchQueryParam, matchTask, q)))
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
                        matches,
                        missing,
                        different,
                        batchid);

        TypedQuery<Tuple> query = em.createQuery(multiselect);
        if (offset > 0)
            query.setFirstResult(offset);
        if (limit > 0)
            query.setMaxResults(limit);

        List<DiffBatch> diffBatches = new ArrayList<>();
        query.getResultStream().forEach(batch -> {
            String batchID = batch.get(batchid);
            DiffBatch diffBatch = new DiffBatch(batchID);
            diffBatch.setProcessingStartTimeRange(
                    batch.get(maxProcessingStartTime),
                    batch.get(maxProcessingStartTime));
            diffBatch.setProcessingEndTimeRange(
                    batch.get(minProcessingEndTime),
                    batch.get(maxProcessingEndTime));
            diffBatch.setScheduledTimeRange(
                    batch.get(minScheduledTime),
                    batch.get(maxScheduledTime));
            diffBatch.setCreatedTimeRange(
                    batch.get(minCreatedTime),
                    batch.get(maxCreatedTime));
            diffBatch.setUpdatedTimeRange(
                    batch.get(minUpdatedTime),
                    batch.get(maxUpdatedTime));

            diffBatch.setMatches(batch.get(matches));
            diffBatch.setMissing(batch.get(missing));
            diffBatch.setDifferent(batch.get(different));

            diffBatch.setDeviceNames(em.createQuery(
                    batchIDQuery(batchID, String.class)
                            .select(queueMsg.get(QueueMessage_.deviceName))
                            .distinct(true)
                            .orderBy(cb.asc(queueMsg.get(QueueMessage_.deviceName))))
                    .getResultList());
            diffBatch.setLocalAETs(em.createQuery(
                    batchIDQuery(batchID, String.class)
                            .select(diffTask.get(DiffTask_.localAET))
                            .distinct(true)
                            .orderBy(cb.asc(diffTask.get(DiffTask_.localAET))))
                    .getResultList());
            diffBatch.setPrimaryAETs(em.createQuery(
                    batchIDQuery(batchID, String.class)
                            .select(diffTask.get(DiffTask_.primaryAET))
                            .distinct(true)
                            .orderBy(cb.asc(diffTask.get(DiffTask_.primaryAET))))
                    .getResultList());
            diffBatch.setSecondaryAETs(em.createQuery(
                    batchIDQuery(batchID, String.class)
                            .select(diffTask.get(DiffTask_.secondaryAET))
                            .distinct(true)
                            .orderBy(cb.asc(diffTask.get(DiffTask_.secondaryAET))))
                    .getResultList());
            diffBatch.setComparefields(em.createQuery(
                    batchIDQuery(batchID)
                            .select(diffTask.get(DiffTask_.compareFields))
                            .distinct(true))
                    .getResultList());
            diffBatch.setCheckMissing(em.createQuery(
                    batchIDQuery(batchID, Boolean.class)
                            .select(diffTask.get(DiffTask_.checkMissing))
                            .distinct(true))
                    .getResultList());
            diffBatch.setCheckDifferent(em.createQuery(
                    batchIDQuery(batchID, Boolean.class)
                            .select(diffTask.get(DiffTask_.checkDifferent))
                            .distinct(true))
                    .getResultList());

            diffBatch.setCompleted(em.createQuery(
                    batchIDQuery(batchID, QueueMessage.Status.COMPLETED).select(cb.count(queueMsg)))
                    .getSingleResult());
            diffBatch.setCanceled(em.createQuery(
                    batchIDQuery(batchID, QueueMessage.Status.CANCELED).select(cb.count(queueMsg)))
                    .getSingleResult());
            diffBatch.setWarning(em.createQuery(
                    batchIDQuery(batchID, QueueMessage.Status.WARNING).select(cb.count(queueMsg)))
                    .getSingleResult());
            diffBatch.setFailed(em.createQuery(
                    batchIDQuery(batchID, QueueMessage.Status.FAILED).select(cb.count(queueMsg)))
                    .getSingleResult());
            diffBatch.setScheduled(em.createQuery(
                    batchIDQuery(batchID, QueueMessage.Status.SCHEDULED).select(cb.count(queueMsg)))
                    .getSingleResult());
            diffBatch.setInProcess(em.createQuery(
                    batchIDQuery(batchID, QueueMessage.Status.IN_PROCESS).select(cb.count(queueMsg)))
                    .getSingleResult());

            diffBatches.add(diffBatch);
        });

        return diffBatches;
    }

    private <T> CriteriaQuery<T> restrictBatch(
            TaskQueryParam queueBatchQueryParam, TaskQueryParam diffBatchQueryParam, MatchTask matchTask, CriteriaQuery<T> q) {
        List<javax.persistence.criteria.Predicate> predicates = matchTask.diffBatchPredicates(
                queueMsg,
                diffTask,
                queueBatchQueryParam,
                diffBatchQueryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new javax.persistence.criteria.Predicate[0]));
        return q;
    }

    private <T> CriteriaQuery<T> orderBatch(
            TaskQueryParam diffBatchQueryParam, MatchTask matchTask, CriteriaQuery<T> q) {
        if (diffBatchQueryParam.getOrderBy() != null)
            q = q.orderBy(matchTask.diffBatchOrder(diffBatchQueryParam.getOrderBy(), diffTask));
        return q;
    }

    private <T> CriteriaQuery<T> groupBy(CriteriaQuery<T> q) {
        return q.groupBy(queueMsg.get(QueueMessage_.batchID));
    }

    private <T> CriteriaQuery<T> batchIDQuery(String batchID, Class<T> clazz) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<T> q = cb.createQuery(clazz);
        diffTask = q.from(DiffTask.class);
        queueMsg = diffTask.join(DiffTask_.queueMessage, JoinType.LEFT);
        return q.where(cb.equal(queueMsg.get(QueueMessage_.batchID), batchID));
    }

    private CriteriaQuery<Long> batchIDQuery(String batchID, QueueMessage.Status status) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        diffTask = q.from(DiffTask.class);
        queueMsg = diffTask.join(DiffTask_.queueMessage, JoinType.LEFT);
        return q.where(
                cb.equal(queueMsg.get(QueueMessage_.batchID), batchID),
                cb.equal(queueMsg.get(QueueMessage_.status), status));
    }

    private CriteriaQuery<String> batchIDQuery(String batchID) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<String> q = cb.createQuery(String.class);
        diffTask = q.from(DiffTask.class);
        queueMsg = diffTask.join(DiffTask_.queueMessage, JoinType.LEFT);
        return q.where(
                cb.equal(queueMsg.get(QueueMessage_.batchID), batchID),
                diffTask.get(DiffTask_.compareFields).isNotNull());
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Iterator<DiffTask> listDiffTasks(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam diffTaskQueryParam, int offset, int limit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        MatchTask matchTask = new MatchTask(cb);
        TypedQuery<DiffTask> query = em.createQuery(select(cb, matchTask, queueTaskQueryParam, diffTaskQueryParam))
                .setHint(QueryHints.FETCH_SIZE, queryFetchSize());
        if (offset > 0)
            query.setFirstResult(offset);
        if (limit > 0)
            query.setMaxResults(limit);
        return query.getResultStream().iterator();
    }

    public long countTasks(TaskQueryParam queueTaskQueryParam, TaskQueryParam diffTaskQueryParam) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        MatchTask matchTask = new MatchTask(cb);

        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        diffTask = q.from(DiffTask.class);
        queueMsg = diffTask.join(DiffTask_.queueMessage);

        return em.createQuery(
                restrict(queueTaskQueryParam, diffTaskQueryParam, matchTask, q).select(cb.count(diffTask)))
                .getSingleResult();
    }

    private <T> CriteriaQuery<T> restrict(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam diffTaskQueryParam, MatchTask matchTask, CriteriaQuery<T> q) {
        List<javax.persistence.criteria.Predicate> predicates = matchTask.diffPredicates(
                queueMsg,
                diffTask,
                queueTaskQueryParam,
                diffTaskQueryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new javax.persistence.criteria.Predicate[0]));
        return q;
    }

    private int queryFetchSize() {
        return device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class).getQueryFetchSize();
    }

    private CriteriaQuery<DiffTask> select(
            CriteriaBuilder cb, MatchTask matchTask, TaskQueryParam queueTaskQueryParam, TaskQueryParam diffTaskQueryParam) {
        CriteriaQuery<DiffTask> q = cb.createQuery(DiffTask.class);
        diffTask = q.from(DiffTask.class);
        queueMsg = diffTask.join(DiffTask_.queueMessage);

        q = restrict(queueTaskQueryParam, diffTaskQueryParam, matchTask, q);
        if (diffTaskQueryParam.getOrderBy() != null)
            q.orderBy(matchTask.diffTaskOrder(diffTaskQueryParam.getOrderBy(), diffTask));

        return q.select(diffTask);
    }
    
}
