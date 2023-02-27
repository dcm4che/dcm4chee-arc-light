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

import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.entity.Task;
import org.dcm4chee.arc.entity.Task_;
import org.dcm4chee.arc.export.mgt.ExportBatch;
import org.dcm4chee.arc.export.mgt.ExportManager;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.query.util.QueryBuilder;
import org.dcm4chee.arc.query.util.TaskQueryParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.*;
import javax.persistence.criteria.*;
import java.util.ArrayList;
import java.util.Date;
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
                    .setParameter(4, Task.Type.EXPORT)
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
                    .setParameter(5, Task.Type.EXPORT)
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
                    .setParameter(6, Task.Type.EXPORT)
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
            task.setQueryString(httpServletRequestInfo.queryString);
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
    public boolean scheduleStudyExport(
            String studyUID, ExporterDescriptor exporter,
            Date notExportedAfter, String batchID, Date scheduledTime) {
        List<Task> prevTasks = em.createNamedQuery(Task.FIND_STUDY_EXPORT_AFTER, Task.class)
                .setParameter(1, notExportedAfter)
                .setParameter(2, exporter.getExporterID())
                .setParameter(3, studyUID)
                .setParameter(4, Task.Type.EXPORT)
                .getResultList();
        if (!prevTasks.isEmpty()) {
            for (Task prevTask : prevTasks) {
                LOG.info("Previous {} found - suppress duplicate Export", prevTask);
            }
            return false;
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

    @Override
    public List<ExportBatch> listExportBatches(TaskQueryParam queryParam, int offset, int limit) {
        ListExportBatches listExportBatches = new ListExportBatches(queryParam);
        TypedQuery<Tuple> query = em.createQuery(listExportBatches.query);
        if (offset > 0)
            query.setFirstResult(offset);
        if (limit > 0)
            query.setMaxResults(limit);

        return query.getResultStream().map(listExportBatches::toExportBatch).collect(Collectors.toList());
    }

    @Override
    public void merge(Task task) {
        em.merge(task);
    }

    private class ListExportBatches {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        final QueryBuilder queryBuilder = new QueryBuilder(cb);
        final CriteriaQuery<Tuple> query = cb.createTupleQuery();
        final Root<Task> task = query.from(Task.class);
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
        final Path<String> batchIDPath = task.get(Task_.batchID);
        final Expression<Long> completed;
        final Expression<Long> failed;
        final Expression<Long> warning;
        final Expression<Long> canceled;
        final Expression<Long> scheduled;
        final Expression<Long> inprocess;
        final TaskQueryParam queryParam;

        ListExportBatches(TaskQueryParam queryParam) {
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
            queryBuilder.matchExportBatch(predicates, queryParam, task);
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
            queryBuilder.matchExportBatch(predicates, queryParam, sqtask);
            sq.where(predicates.toArray(new Predicate[0]));
            sq.select(cb.count(sqtask));
            return sq;
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
            exportBatch.setProcessingStartTimeRange(
                    tuple.get(minProcessingStartTime),
                    tuple.get(maxProcessingStartTime));
            exportBatch.setProcessingEndTimeRange(
                    tuple.get(minProcessingEndTime),
                    tuple.get(maxProcessingEndTime));

            CriteriaQuery<String> distinct = cb.createQuery(String.class).distinct(true);
            Root<Task> exportTask = distinct.from(Task.class);
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(exportTask.get(Task_.batchID), batchID));
            queryBuilder.matchExportBatch(predicates, queryParam, exportTask);
            distinct.where(predicates.toArray(new Predicate[0]));
            exportBatch.setDeviceNames(select(distinct, exportTask.get(Task_.deviceName)));
            exportBatch.setExporterIDs(select(distinct, exportTask.get(Task_.exporterID)));
            exportBatch.setCompleted(tuple.get(completed));
            exportBatch.setCanceled(tuple.get(canceled));
            exportBatch.setWarning(tuple.get(warning));
            exportBatch.setFailed(tuple.get(failed));
            exportBatch.setScheduled(tuple.get(scheduled));
            exportBatch.setInProcess(tuple.get(inprocess));
            return exportBatch;
        }

        private List<String> select(CriteriaQuery<String> query, Path<String> path) {
            return em.createQuery(query.select(path)).getResultList();
        }
    }

}