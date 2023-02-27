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
import org.dcm4chee.arc.entity.Task;
import org.dcm4chee.arc.entity.Task_;
import org.dcm4chee.arc.query.util.QueryBuilder;
import org.dcm4chee.arc.query.util.TaskQueryParam;
import org.dcm4chee.arc.retrieve.ExternalRetrieveContext;
import org.dcm4chee.arc.retrieve.mgt.RetrieveBatch;
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
import java.util.Iterator;
import java.util.List;
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
            task.setQueryString(ctx.getHttpServletRequestInfo().queryString);
        }
        em.persist(task);
        LOG.info("Create {}", task);
        ctx.setRetrieveTask(task);
        return true;
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
                    ctx.setRetrieveTask(retrieveTask1);
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

    public List<RetrieveBatch> listRetrieveBatches(TaskQueryParam queryParam, int offset, int limit) {
        ListRetrieveBatches listRetrieveBatches1 = new ListRetrieveBatches(queryParam);
        TypedQuery<Tuple> query = em.createQuery(listRetrieveBatches1.query);
        if (offset > 0)
            query.setFirstResult(offset);
        if (limit > 0)
            query.setMaxResults(limit);
        return query.getResultStream().map(listRetrieveBatches1::toRetrieveBatch).collect(Collectors.toList());
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
        final TaskQueryParam queryParam;

        ListRetrieveBatches(TaskQueryParam queryParam) {
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
            predicates.add(cb.equal(task.get(Task_.type), queryParam.getType()));
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
                    tuple.get(minProcessingStartTime),
                    tuple.get(maxProcessingStartTime));
            retrieveBatch.setProcessingEndTimeRange(
                    tuple.get(minProcessingEndTime),
                    tuple.get(maxProcessingEndTime));

            CriteriaQuery<String> distinct = cb.createQuery(String.class).distinct(true);
            Root<Task> retrieveTask = distinct.from(Task.class);
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(retrieveTask.get(Task_.batchID), batchID));
            queryBuilder.matchRetrieveBatch(predicates, queryParam, retrieveTask);
            distinct.where(predicates.toArray(new Predicate[0]));
            retrieveBatch.setDeviceNames(select(distinct, retrieveTask.get(Task_.deviceName)));
            retrieveBatch.setQueueNames(select(distinct, retrieveTask.get(Task_.queueName)));
            retrieveBatch.setLocalAETs(select(distinct, retrieveTask.get(Task_.localAET)));
            retrieveBatch.setRemoteAETs(select(distinct, retrieveTask.get(Task_.remoteAET)));
            retrieveBatch.setDestinationAETs(select(distinct, retrieveTask.get(Task_.destinationAET)));
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

}
