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

import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.criteria.Expression;
import javax.persistence.Tuple;

import org.dcm4che3.data.Attributes;
import org.dcm4chee.arc.diff.*;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.event.QueueMessageEvent;
import org.dcm4chee.arc.qmgt.IllegalTaskStateException;
import org.dcm4chee.arc.qmgt.QueueManager;
import org.dcm4chee.arc.qmgt.QueueSizeLimitExceededException;
import org.dcm4chee.arc.query.util.MatchTask;
import org.dcm4chee.arc.query.util.QueryBuilder;
import org.dcm4chee.arc.query.util.TaskQueryParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.jms.JMSException;
import javax.jms.Message;
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
 * @since Mar 2018
 */
@Stateless
public class DiffServiceEJB {

    private static final Logger LOG = LoggerFactory.getLogger(DiffServiceEJB.class);

    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    @Inject
    private QueueManager queueManager;

    public void scheduleDiffTask(DiffContext ctx) throws QueueSizeLimitExceededException {
        scheduleDiffTask(ctx, ctx.getQueryString());
    }

    private void scheduleDiffTask(DiffContext ctx, String queryString) throws QueueSizeLimitExceededException{
        try {
            ObjectMessage msg = queueManager.createObjectMessage(0);
            msg.setStringProperty("LocalAET", ctx.getLocalAE().getAETitle());
            msg.setStringProperty("PrimaryAET", ctx.getPrimaryAE().getAETitle());
            msg.setStringProperty("SecondaryAET", ctx.getSecondaryAE().getAETitle());
            msg.setIntProperty("Priority", ctx.priority());
            msg.setStringProperty("QueryString", queryString);
            if (ctx.getHttpServletRequestInfo() != null)
                ctx.getHttpServletRequestInfo().copyTo(msg);
            QueueMessage queueMessage = queueManager.scheduleMessage(DiffService.QUEUE_NAME, msg,
                    Message.DEFAULT_PRIORITY, ctx.getBatchID(), 0L);
            createDiffTask(ctx, queueMessage, queryString);
        } catch (JMSException e) {
            throw QueueMessage.toJMSRuntimeException(e);
        }
    }

    public void scheduleDiffTasks(DiffContext ctx, List<String> studyUIDs) throws QueueSizeLimitExceededException {
        studyUIDs.forEach(studyUID -> scheduleDiffTask(ctx, ctx.getQueryString() + studyUID));
    }

    private void createDiffTask(DiffContext ctx, QueueMessage queueMessage, String queryString) {
        DiffTask task = new DiffTask();
        task.setLocalAET(ctx.getLocalAE().getAETitle());
        task.setPrimaryAET(ctx.getPrimaryAE().getAETitle());
        task.setSecondaryAET(ctx.getSecondaryAE().getAETitle());
        task.setQueryString(queryString);
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
        diffTask = em.find(DiffTask.class, diffTask.getPk());
        if (diffTask != null) {
            diffTask.getDiffTaskAttributes().add(new AttributesBlob(attrs));
        }
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
        List<String> referencedQueueMsgIDs = em.createQuery(
                select(QueueMessage_.messageID, queueTaskQueryParam, diffTaskQueryParam))
                .setMaxResults(deleteTasksFetchSize)
                .getResultList();

        referencedQueueMsgIDs.forEach(queueMsgID -> queueManager.deleteTask(queueMsgID, null));
        return referencedQueueMsgIDs.size();
    }

    public List<String> listDistinctDeviceNames(TaskQueryParam queueTaskQueryParam, TaskQueryParam diffTaskQueryParam) {
        return em.createQuery(
                select(QueueMessage_.deviceName, queueTaskQueryParam, diffTaskQueryParam).distinct(true))
                .getResultList();
    }

    public List<String> listDiffTaskQueueMsgIDs(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam diffTaskQueryParam, int limit) {
        return em.createQuery(select(QueueMessage_.messageID, queueTaskQueryParam, diffTaskQueryParam))
                .setMaxResults(limit)
                .getResultList();
    }

    public List<Tuple> listDiffTaskQueueMsgIDAndMsgProps(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam diffTaskQueryParam, int limit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        Root<DiffTask> diffTask = q.from(DiffTask.class);
        From<DiffTask, QueueMessage> queueMsg = diffTask.join(DiffTask_.queueMessage);
        List<Predicate> predicates = new MatchTask(cb).diffPredicates(
                queueMsg, diffTask, queueTaskQueryParam, diffTaskQueryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));
        return em.createQuery(
                q.multiselect(
                        queueMsg.get(QueueMessage_.messageID),
                        queueMsg.get(QueueMessage_.messageProperties)))
                .setMaxResults(limit)
                .getResultList();
    }

    private CriteriaQuery<String> select(SingularAttribute<QueueMessage, String> attribute,
            TaskQueryParam queueTaskQueryParam, TaskQueryParam diffTaskQueryParam) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<String> q = cb.createQuery(String.class);
        Root<DiffTask> diffTask = q.from(DiffTask.class);
        From<DiffTask, QueueMessage> queueMsg = diffTask.join(DiffTask_.queueMessage);
        List<Predicate> predicates = new MatchTask(cb).diffPredicates(
                queueMsg, diffTask, queueTaskQueryParam, diffTaskQueryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));
        return q.select(queueMsg.get(attribute));
    }

    public long diffTasksOfBatch(String batchID) {
        return em.createNamedQuery(DiffTask.COUNT_BY_BATCH_ID, Long.class)
                .setParameter(1, batchID)
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

    public Tuple findDeviceNameAndMsgPropsByPk(Long pk) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> tupleQuery = cb.createTupleQuery();
        Root<DiffTask> diffTask = tupleQuery.from(DiffTask.class);
        Join<DiffTask, QueueMessage> queueMsg = diffTask.join(DiffTask_.queueMessage);
        tupleQuery.where(cb.equal(diffTask.get(DiffTask_.pk), pk));
        tupleQuery.multiselect(
                queueMsg.get(QueueMessage_.deviceName),
                queueMsg.get(QueueMessage_.messageProperties));
        return em.createQuery(tupleQuery).getSingleResult();
    }

    public List<byte[]> getDiffTaskAttributes(DiffTask diffTask, int offset, int limit) {
        return em.createNamedQuery(DiffTask.FIND_ATTRS_BY_PK, byte[].class)
                .setParameter(1, diffTask.getPk())
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }

    public List<byte[]> getDiffTaskAttributes(
            TaskQueryParam queueBatchQueryParam, TaskQueryParam diffBatchQueryParam, int offset, int limit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<byte[]> q = cb.createQuery(byte[].class);
        Root<DiffTask> diffTask = q.from(DiffTask.class);
        From<DiffTask, QueueMessage> queueMsg = diffTask.join(DiffTask_.queueMessage);

        List<Predicate> predicates = new MatchTask(cb).diffBatchPredicates(
                queueMsg, diffTask, queueBatchQueryParam, diffBatchQueryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));

        CollectionJoin<DiffTask, AttributesBlob> attrsBlobs = diffTask.join(DiffTask_.diffTaskAttributes);
        TypedQuery<byte[]> query = em.createQuery(q.select(attrsBlobs.get(AttributesBlob_.encodedAttributes)));
        if (offset > 0)
            query.setFirstResult(offset);
        if (limit > 0)
            query.setMaxResults(limit);
        return query.getResultList();

    }

    public List<DiffBatch> listDiffBatches(
            TaskQueryParam queueBatchQueryParam, TaskQueryParam diffBatchQueryParam, int offset, int limit) {
        ListDiffBatches listDiffBatches = new ListDiffBatches(queueBatchQueryParam, diffBatchQueryParam);
        TypedQuery<Tuple> query = em.createQuery(listDiffBatches.query);
        if (offset > 0)
            query.setFirstResult(offset);
        if (limit > 0)
            query.setMaxResults(limit);

        return query.getResultStream().map(listDiffBatches::toDiffBatch).collect(Collectors.toList());
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Iterator<DiffTask> listDiffTasks(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam diffTaskQueryParam, int offset, int limit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<DiffTask> q = cb.createQuery(DiffTask.class);
        Root<DiffTask> diffTask = q.from(DiffTask.class);
        From<DiffTask, QueueMessage> queueMsg = diffTask.join(DiffTask_.queueMessage);
        MatchTask matchTask = new MatchTask(cb);
        List<Predicate> predicates = matchTask.diffPredicates(queueMsg, diffTask, queueTaskQueryParam, diffTaskQueryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));
        if (diffTaskQueryParam.getOrderBy() != null)
            q.orderBy(matchTask.diffTaskOrder(diffTaskQueryParam.getOrderBy(), diffTask));
        TypedQuery<DiffTask> query = em.createQuery(q);
        if (offset > 0)
            query.setFirstResult(offset);
        if (limit > 0)
            query.setMaxResults(limit);
        return query.getResultStream().iterator();
    }

    public long countTasks(TaskQueryParam queueTaskQueryParam, TaskQueryParam diffTaskQueryParam) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        Root<DiffTask> diffTask = q.from(DiffTask.class);
        From<DiffTask, QueueMessage> queueMsg = diffTask.join(DiffTask_.queueMessage);
        List<Predicate> predicates = new MatchTask(cb).diffPredicates(queueMsg, diffTask, queueTaskQueryParam, diffTaskQueryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));
        return QueryBuilder.unbox(em.createQuery(q.select(cb.count(diffTask))).getSingleResult(), 0L);
    }

    private Subquery<Long> statusSubquery(TaskQueryParam queueBatchQueryParam, TaskQueryParam diffBatchQueryParam,
                                          From<DiffTask, QueueMessage> queueMsg, QueueMessage.Status status) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<QueueMessage> query = cb.createQuery(QueueMessage.class);
        Subquery<Long> sq = query.subquery(Long.class);
        Root<DiffTask> diffTask = sq.from(DiffTask.class);
        Join<DiffTask, QueueMessage> queueMsg1 = sq.correlate(diffTask.join(DiffTask_.queueMessage));
        MatchTask matchTask = new MatchTask(cb);
        List<Predicate> predicates = matchTask.diffBatchPredicates(
                queueMsg1, diffTask, queueBatchQueryParam, diffBatchQueryParam);
        predicates.add(cb.equal(queueMsg1.get(QueueMessage_.batchID), queueMsg.get(QueueMessage_.batchID)));
        predicates.add(cb.equal(queueMsg1.get(QueueMessage_.status), status));
        sq.where(predicates.toArray(new Predicate[0]));
        sq.select(cb.count(diffTask));
        return sq;
    }

    private class ListDiffBatches {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        final MatchTask matchTask = new MatchTask(cb);
        final CriteriaQuery<Tuple> query = cb.createTupleQuery();
        final Root<DiffTask> diffTask = query.from(DiffTask.class);
        final From<DiffTask, QueueMessage> queueMsg = diffTask.join(DiffTask_.queueMessage);
        final Expression<Date> minProcessingStartTime = cb.least(queueMsg.get(QueueMessage_.processingStartTime));
        final Expression<Date> maxProcessingStartTime = cb.greatest(queueMsg.get(QueueMessage_.processingStartTime));
        final Expression<Date> minProcessingEndTime = cb.least(queueMsg.get(QueueMessage_.processingEndTime));
        final Expression<Date> maxProcessingEndTime = cb.greatest(queueMsg.get(QueueMessage_.processingEndTime));
        final Expression<Date> minScheduledTime = cb.least(queueMsg.get(QueueMessage_.scheduledTime));
        final Expression<Date> maxScheduledTime = cb.greatest(queueMsg.get(QueueMessage_.scheduledTime));
        final Expression<Date> minCreatedTime = cb.least(diffTask.get(DiffTask_.createdTime));
        final Expression<Date> maxCreatedTime = cb.greatest(diffTask.get(DiffTask_.createdTime));
        final Expression<Date> minUpdatedTime = cb.least(diffTask.get(DiffTask_.updatedTime));
        final Expression<Date> maxUpdatedTime = cb.greatest(diffTask.get(DiffTask_.updatedTime));
        final Expression<Long> matches = cb.sumAsLong((diffTask.get(DiffTask_.matches)));
        final Expression<Long> missing = cb.sumAsLong(diffTask.get(DiffTask_.missing));
        final Expression<Long> different = cb.sumAsLong(diffTask.get(DiffTask_.different));
        final Path<String> batchIDPath = queueMsg.get(QueueMessage_.batchID);
        final Expression<Long> completed;
        final Expression<Long> failed;
        final Expression<Long> warning;
        final Expression<Long> canceled;
        final Expression<Long> scheduled;
        final Expression<Long> inprocess;
        final TaskQueryParam queueBatchQueryParam;
        final TaskQueryParam diffBatchQueryParam;

        ListDiffBatches(TaskQueryParam queueBatchQueryParam, TaskQueryParam diffBatchQueryParam) {
            this.queueBatchQueryParam = queueBatchQueryParam;
            this.diffBatchQueryParam = diffBatchQueryParam;
            this.completed = statusSubquery(queueBatchQueryParam, diffBatchQueryParam,
                    queueMsg, QueueMessage.Status.COMPLETED).getSelection();
            this.failed = statusSubquery(queueBatchQueryParam, diffBatchQueryParam,
                    queueMsg, QueueMessage.Status.FAILED).getSelection();
            this.warning = statusSubquery(queueBatchQueryParam, diffBatchQueryParam,
                    queueMsg, QueueMessage.Status.WARNING).getSelection();
            this.canceled = statusSubquery(queueBatchQueryParam, diffBatchQueryParam,
                    queueMsg, QueueMessage.Status.CANCELED).getSelection();
            this.scheduled = statusSubquery(queueBatchQueryParam, diffBatchQueryParam,
                    queueMsg, QueueMessage.Status.SCHEDULED).getSelection();
            this.inprocess = statusSubquery(queueBatchQueryParam, diffBatchQueryParam,
                    queueMsg, QueueMessage.Status.IN_PROCESS).getSelection();
            query.multiselect(batchIDPath,
                    minProcessingStartTime, maxProcessingStartTime,
                    minProcessingEndTime, maxProcessingEndTime,
                    minScheduledTime, maxScheduledTime,
                    minCreatedTime, maxCreatedTime,
                    minUpdatedTime, maxUpdatedTime,
                    matches, missing, different,
                    completed, failed, warning, canceled, scheduled, inprocess);
            query.groupBy(queueMsg.get(QueueMessage_.batchID));
            List<Predicate> predicates = matchTask.diffBatchPredicates(
                    queueMsg, diffTask, queueBatchQueryParam, diffBatchQueryParam);
            if (!predicates.isEmpty())
                query.where(predicates.toArray(new Predicate[0]));
            if (diffBatchQueryParam.getOrderBy() != null)
                query.orderBy(matchTask.diffBatchOrder(diffBatchQueryParam.getOrderBy(), diffTask));
        }

        DiffBatch toDiffBatch(Tuple tuple) {
            String batchID = tuple.get(batchIDPath);
            DiffBatch diffBatch = new DiffBatch(batchID);
            diffBatch.setProcessingStartTimeRange(
                    tuple.get(maxProcessingStartTime),
                    tuple.get(maxProcessingStartTime));
            diffBatch.setProcessingEndTimeRange(
                    tuple.get(minProcessingEndTime),
                    tuple.get(maxProcessingEndTime));
            diffBatch.setScheduledTimeRange(
                    tuple.get(minScheduledTime),
                    tuple.get(maxScheduledTime));
            diffBatch.setCreatedTimeRange(
                    tuple.get(minCreatedTime),
                    tuple.get(maxCreatedTime));
            diffBatch.setUpdatedTimeRange(
                    tuple.get(minUpdatedTime),
                    tuple.get(maxUpdatedTime));

            diffBatch.setMatches(tuple.get(matches));
            diffBatch.setMissing(tuple.get(missing));
            diffBatch.setDifferent(tuple.get(different));

            CriteriaQuery<String> distinctString = cb.createQuery(String.class).distinct(true);
            Root<DiffTask> diffTask = distinctString.from(DiffTask.class);
            From<DiffTask, QueueMessage> queueMsg = diffTask.join(DiffTask_.queueMessage);
            distinctString.where(predicates(queueMsg, diffTask, batchID));
            diffBatch.setDeviceNames(selectString(distinctString, queueMsg.get(QueueMessage_.deviceName)));
            diffBatch.setComparefields(selectString(distinctString, diffTask.get(DiffTask_.compareFields)));
            diffBatch.setLocalAETs(selectString(distinctString, diffTask.get(DiffTask_.localAET)));
            diffBatch.setPrimaryAETs(selectString(distinctString, diffTask.get(DiffTask_.primaryAET)));
            diffBatch.setSecondaryAETs(selectString(distinctString, diffTask.get(DiffTask_.secondaryAET)));

            CriteriaQuery<Boolean> distinctBoolean = cb.createQuery(Boolean.class).distinct(true);
            Root<DiffTask> diffTask1 = distinctBoolean.from(DiffTask.class);
            From<DiffTask, QueueMessage> queueMsg1 = diffTask1.join(DiffTask_.queueMessage);
            distinctBoolean.where(predicates(queueMsg1, diffTask1, batchID));
            diffBatch.setCheckMissing(selectBoolean(distinctBoolean, diffTask1.get(DiffTask_.checkMissing)));
            diffBatch.setCheckDifferent(selectBoolean(distinctBoolean, diffTask1.get(DiffTask_.checkDifferent)));

            diffBatch.setCompleted(tuple.get(completed));
            diffBatch.setCanceled(tuple.get(canceled));
            diffBatch.setWarning(tuple.get(warning));
            diffBatch.setFailed(tuple.get(failed));
            diffBatch.setScheduled(tuple.get(scheduled));
            diffBatch.setInProcess(tuple.get(inprocess));
            return diffBatch;
        }

        private Predicate[] predicates(Path<QueueMessage> queueMsg, Path<DiffTask> diffTask, String batchID) {
            List<Predicate> predicates = matchTask.diffBatchPredicates(
                    queueMsg, diffTask, queueBatchQueryParam, diffBatchQueryParam);
            predicates.add(cb.equal(queueMsg.get(QueueMessage_.batchID), batchID));
            return predicates.toArray(new Predicate[0]);
        }

        private List<String> selectString(CriteriaQuery<String> query, Path<String> path) {
            return em.createQuery(query.select(path)).getResultList();
        }

        private List<Boolean> selectBoolean(CriteriaQuery<Boolean> query, Path<Boolean> path) {
            return em.createQuery(query.select(path)).getResultList();
        }
    }
}
