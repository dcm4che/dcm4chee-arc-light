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

import org.dcm4che3.data.Attributes;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.diff.DiffBatch;
import org.dcm4chee.arc.diff.DiffSCU;
import org.dcm4chee.arc.diff.DiffService;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.event.QueueMessageEvent;
import org.dcm4chee.arc.qmgt.IllegalTaskStateException;
import org.dcm4chee.arc.qmgt.QueueManager;
import org.dcm4chee.arc.query.util.MatchTask;
import org.dcm4chee.arc.query.util.QueryBuilder;
import org.dcm4chee.arc.query.util.TaskQueryParam;
import org.dcm4chee.arc.query.util.TaskQueryParam1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Tuple;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.*;
import javax.persistence.metamodel.SingularAttribute;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
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

    @Inject
    private Device device;

    public void addDiffTaskAttributes(Task diffTask, Attributes attrs) {
        diffTask = em.find(Task.class, diffTask.getPk());
        if (diffTask != null) {
            diffTask.getDiffTaskAttributes().add(new AttributesBlob(attrs));
        }
    }

    public void resetDiffTask(Task diffTask) {
        diffTask = em.find(Task.class, diffTask.getPk());
        diffTask.resetDiffTask();
        diffTask.getDiffTaskAttributes().clear();
    }

    public void updateDiffTask(Task diffTask, DiffSCU diffSCU) {
        diffTask = em.find(Task.class, diffTask.getPk());
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

        queueManager.deleteTask(task.getQueueMessage().getPk(), queueEvent);
        LOG.info("Delete {}", task);
        return true;
    }

    public int deleteTasks(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam diffTaskQueryParam, int deleteTasksFetchSize) {
        List<Long> referencedQueueMsgPKs = em.createQuery(
                select(Long.class, QueueMessage_.pk, queueTaskQueryParam, diffTaskQueryParam))
                .setMaxResults(deleteTasksFetchSize)
                .getResultList();

        referencedQueueMsgPKs.forEach(taskPK -> queueManager.deleteTask(taskPK, null));
        return referencedQueueMsgPKs.size();
    }

    public List<String> listDistinctDeviceNames(TaskQueryParam queueTaskQueryParam, TaskQueryParam diffTaskQueryParam) {
        return em.createQuery(
                select(String.class, QueueMessage_.deviceName, queueTaskQueryParam, diffTaskQueryParam).distinct(true))
                .getResultList();
    }

    public List<Long> listDiffTaskQueueMsgIDs(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam diffTaskQueryParam, int limit) {
        return em.createQuery(select(Long.class, QueueMessage_.pk, queueTaskQueryParam, diffTaskQueryParam))
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
                        queueMsg.get(QueueMessage_.pk),
                        queueMsg.get(QueueMessage_.messageProperties)))
                .setMaxResults(limit)
                .getResultList();
    }

    private <T> CriteriaQuery<T> select(Class<T> claszz, SingularAttribute<QueueMessage, T> attribute,
                                        TaskQueryParam queueTaskQueryParam, TaskQueryParam diffTaskQueryParam) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<T> q = cb.createQuery(claszz);
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

        queueManager.cancelTask(queueMessage.getPk(), queueEvent);
        LOG.info("Cancel {}", task);
        return true;
    }

    public long cancelDiffTasks(TaskQueryParam queueTaskQueryParam, TaskQueryParam diffTaskQueryParam) {
        return queueManager.cancelDiffTasks(queueTaskQueryParam, diffTaskQueryParam);
    }

    public void rescheduleDiffTask(Long pk, QueueMessageEvent queueEvent, Date scheduledTime) {
        DiffTask task = em.find(DiffTask.class, pk);
        if (task == null)
            return;

        LOG.info("Reschedule {}", task);
        queueManager.rescheduleTask(task.getQueueMessage().getPk(), DiffService.QUEUE_NAME, queueEvent, scheduledTime);
    }

    public void rescheduleDiffTaskByMsgID(Long msgId, QueueMessageEvent queueEvent, Date scheduledTime) {
        queueManager.rescheduleTask(msgId, DiffService.QUEUE_NAME, queueEvent, scheduledTime);
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

    public List<DiffBatch> listDiffBatches(TaskQueryParam1 taskQueryParam, int offset, int limit) {
        ListDiffBatches listDiffBatches = new ListDiffBatches(taskQueryParam);
        TypedQuery<Tuple> query = em.createQuery(listDiffBatches.query);
        if (offset > 0)
            query.setFirstResult(offset);
        if (limit > 0)
            query.setMaxResults(limit);

        return query.getResultStream().map(listDiffBatches::toDiffBatch).collect(Collectors.toList());
    }

    private class ListDiffBatches {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        final QueryBuilder queryBuilder = new QueryBuilder(cb);
        final CriteriaQuery<Tuple> query = cb.createTupleQuery();
        final Root<Task> task = query.from(Task.class);
        final Expression<Date> minProcessingStartTime = cb.least(task.get(Task_.processingStartTime));
        final Expression<Date> maxProcessingStartTime = cb.greatest(task.get(Task_.processingStartTime));
        final Expression<Date> minProcessingEndTime = cb.least(task.get(Task_.processingEndTime));
        final Expression<Date> maxProcessingEndTime = cb.greatest(task.get(Task_.processingEndTime));
        final Expression<Date> minScheduledTime = cb.least(task.get(Task_.scheduledTime));
        final Expression<Date> maxScheduledTime = cb.greatest(task.get(Task_.scheduledTime));
        final Expression<Date> minCreatedTime = cb.least(task.get(Task_.createdTime));
        final Expression<Date> maxCreatedTime = cb.greatest(task.get(Task_.createdTime));
        final Expression<Date> minUpdatedTime = cb.least(task.get(Task_.updatedTime));
        final Expression<Date> maxUpdatedTime = cb.greatest(task.get(Task_.updatedTime));
        final Expression<Long> matches = cb.sumAsLong((task.get(Task_.matches)));
        final Expression<Long> missing = cb.sumAsLong(task.get(Task_.missing));
        final Expression<Long> different = cb.sumAsLong(task.get(Task_.different));
        final Path<String> batchIDPath = task.get(Task_.batchID);
        final Expression<Long> completed;
        final Expression<Long> failed;
        final Expression<Long> warning;
        final Expression<Long> canceled;
        final Expression<Long> scheduled;
        final Expression<Long> inprocess;
        final TaskQueryParam1 queryParam;

        ListDiffBatches(TaskQueryParam1 queryParam) {
            this.queryParam = queryParam;
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
                    matches, missing, different,
                    completed, failed, warning, canceled, scheduled, inprocess);
            query.groupBy(task.get(Task_.batchID));
            List<Predicate> predicates = new ArrayList<>();
            if (queryParam.getBatchID() != null)
                predicates.add(cb.equal(task.get(Task_.batchID), queryParam.getBatchID()));
            else
                predicates.add(task.get(Task_.batchID).isNotNull());
            if (queryParam.getStatus() != null)
                predicates.add(cb.equal(task.get(Task_.status), queryParam.getStatus()));
            queryBuilder.matchDiffBatch(predicates, queryParam, task);
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
            queryBuilder.matchDiffBatch(predicates, queryParam, sqtask);
            sq.where(predicates.toArray(new Predicate[0]));
            sq.select(cb.count(sqtask));
            return sq;
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

            List<Predicate> predicateList = new ArrayList<>();
            predicateList.add(cb.equal(task.get(Task_.batchID), batchID));
            queryBuilder.matchDiffBatch(predicateList, queryParam, task);
            Predicate[] predicates = predicateList.toArray(new Predicate[0]);

            CriteriaQuery<String> distinctString = cb.createQuery(String.class).distinct(true).where(predicates);
            diffBatch.setDeviceNames(selectString(distinctString, task.get(Task_.deviceName)));
            diffBatch.setComparefields(selectString(distinctString, task.get(Task_.compareFields)));
            diffBatch.setLocalAETs(selectString(distinctString, task.get(Task_.localAET)));
            diffBatch.setPrimaryAETs(selectString(distinctString, task.get(Task_.remoteAET)));
            diffBatch.setSecondaryAETs(selectString(distinctString, task.get(Task_.destinationAET)));

            CriteriaQuery<Boolean> distinctBoolean = cb.createQuery(Boolean.class).distinct(true).where(predicates);
            diffBatch.setCheckMissing(selectBoolean(distinctBoolean, task.get(Task_.checkMissing)));
            diffBatch.setCheckDifferent(selectBoolean(distinctBoolean, task.get(Task_.checkDifferent)));

            diffBatch.setCompleted(tuple.get(completed));
            diffBatch.setCanceled(tuple.get(canceled));
            diffBatch.setWarning(tuple.get(warning));
            diffBatch.setFailed(tuple.get(failed));
            diffBatch.setScheduled(tuple.get(scheduled));
            diffBatch.setInProcess(tuple.get(inprocess));
            return diffBatch;
        }

        private List<String> selectString(CriteriaQuery<String> query, Path<String> path) {
            return em.createQuery(query.select(path)).getResultList();
        }

        private List<Boolean> selectBoolean(CriteriaQuery<Boolean> query, Path<Boolean> path) {
            return em.createQuery(query.select(path)).getResultList();
        }
    }
}
