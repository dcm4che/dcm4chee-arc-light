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
import org.dcm4chee.arc.diff.DiffBatch;
import org.dcm4chee.arc.diff.DiffSCU;
import org.dcm4chee.arc.entity.AttributesBlob;
import org.dcm4chee.arc.entity.AttributesBlob_;
import org.dcm4chee.arc.entity.Task;
import org.dcm4chee.arc.entity.Task_;
import org.dcm4chee.arc.query.util.QueryBuilder;
import org.dcm4chee.arc.query.util.TaskQueryParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Tuple;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.*;
import java.util.ArrayList;
import java.util.Date;
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

    public long diffTasksOfBatch(String batchID) {
        return em.createNamedQuery(Task.COUNT_BY_BATCH_ID_AND_TYPE, Long.class)
                .setParameter(1, batchID)
                .setParameter(2, Task.Type.DIFF)
                .getSingleResult();
    }

    public List<byte[]> getDiffTaskAttributes(Task task, int offset, int limit) {
        TypedQuery<byte[]> query = em.createNamedQuery(Task.DIFF_ATTRS_BY_PK, byte[].class)
                .setParameter(1, task.getPk());
        if (offset > 0)
            query.setFirstResult(offset);
        if (limit > 0)
            query.setMaxResults(limit);
        return query.getResultList();
    }

    public List<byte[]> getDiffTaskAttributes(TaskQueryParam taskQueryParam, int offset, int limit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<byte[]> q = cb.createQuery(byte[].class);
        Root<Task> task = q.from(Task.class);
        CollectionJoin<Task, AttributesBlob> attrsBlobs = task.join(Task_.diffTaskAttributes);
        List<Predicate> predicates = new QueryBuilder(cb).taskPredicates(task, taskQueryParam);
        q.where(predicates.toArray(new Predicate[0]));
        TypedQuery<byte[]> query = em.createQuery(q.select(attrsBlobs.get(AttributesBlob_.encodedAttributes)));
        if (offset > 0)
            query.setFirstResult(offset);
        if (limit > 0)
            query.setMaxResults(limit);
        return query.getResultList();
    }

    public List<DiffBatch> listDiffBatches(TaskQueryParam taskQueryParam, int offset, int limit) {
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
        final TaskQueryParam queryParam;

        ListDiffBatches(TaskQueryParam queryParam) {
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
            predicates.add(cb.equal(task.get(Task_.type), queryParam.getType()));
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
            queryDistinctStringValues(diffBatch);
            queryDistinctBooleanValues(diffBatch);
            diffBatch.setCompleted(tuple.get(completed));
            diffBatch.setCanceled(tuple.get(canceled));
            diffBatch.setWarning(tuple.get(warning));
            diffBatch.setFailed(tuple.get(failed));
            diffBatch.setScheduled(tuple.get(scheduled));
            diffBatch.setInProcess(tuple.get(inprocess));
            return diffBatch;
        }

        private void queryDistinctStringValues(DiffBatch diffBatch) {
            CriteriaQuery<String> query = cb.createQuery(String.class).distinct(true);
            Root<Task> diffTask = query.from(Task.class);
            query.where(predicates(diffTask, diffBatch));
            diffBatch.setDeviceNames(select(query, diffTask.get(Task_.deviceName)));
            diffBatch.setComparefields(select(query, diffTask.get(Task_.compareFields)));
            diffBatch.setLocalAETs(select(query, diffTask.get(Task_.localAET)));
            diffBatch.setPrimaryAETs(select(query, diffTask.get(Task_.remoteAET)));
            diffBatch.setSecondaryAETs(select(query, diffTask.get(Task_.destinationAET)));
        }

        private void queryDistinctBooleanValues(DiffBatch diffBatch) {
            CriteriaQuery<Boolean> query = cb.createQuery(Boolean.class).distinct(true);
            Root<Task> diffTask = query.from(Task.class);
            query.where(predicates(diffTask, diffBatch));
            diffBatch.setCheckMissing(select(query, diffTask.get(Task_.checkMissing)));
            diffBatch.setCheckDifferent(select(query, diffTask.get(Task_.checkDifferent)));
        }

        private Predicate[] predicates(Root<Task> diffTask, DiffBatch diffBatch) {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(diffTask.get(Task_.batchID), diffBatch.getBatchID()));
            queryBuilder.matchDiffBatch(predicates, queryParam, diffTask);
            return predicates.toArray(new Predicate[0]);
        }

        private <T> List<T> select(CriteriaQuery<T> query, Path<T> path) {
            return em.createQuery(query.select(path)).getResultList();
        }

    }
}
