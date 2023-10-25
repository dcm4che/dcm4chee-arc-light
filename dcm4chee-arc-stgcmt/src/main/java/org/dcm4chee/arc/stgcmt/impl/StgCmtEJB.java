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
 * Portions created by the Initial Developer are Copyright (C) 2016-2019
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

package org.dcm4chee.arc.stgcmt.impl;

import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.persistence.*;
import jakarta.persistence.criteria.*;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.conf.RejectionNote;
import org.dcm4chee.arc.conf.StorageVerificationPolicy;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.query.util.QueryBuilder;
import org.dcm4chee.arc.query.util.StgCmtResultQueryParam;
import org.dcm4chee.arc.query.util.TaskQueryParam;
import org.dcm4chee.arc.stgcmt.StgCmtManager;
import org.dcm4chee.arc.stgcmt.StgVerBatch;
import org.hibernate.Session;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Sep 2016
 */
@Stateless
public class StgCmtEJB {

    private final Logger LOG = LoggerFactory.getLogger(StgCmtEJB.class);

    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    @Inject
    private Device device;

    public void addExternalRetrieveAETs(Attributes eventInfo, Device device) {
        String transactionUID = eventInfo.getString(Tag.TransactionUID);
        StgCmtResult result = getStgCmtResult(transactionUID);
        if (result == null)
            return;

        ArchiveDeviceExtension arcdev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        ExporterDescriptor ed = arcdev.getExporterDescriptor(result.getExporterID());
        String configRetrieveAET = ed != null && ed.getRetrieveAETitles().length > 0 ? ed.getRetrieveAETitles()[0] : null;
        String defRetrieveAET = eventInfo.getString(Tag.RetrieveAETitle, ed != null ? ed.getStgCmtSCPAETitle() : null);
        Sequence sopSeq = eventInfo.getSequence(Tag.ReferencedSOPSequence);
        Map<String,List<String>> iuidsByRetrieveAET = new HashMap<>();
        for (Attributes sopRef : sopSeq) {
            iuidsByRetrieveAET.computeIfAbsent(configRetrieveAET != null
                            ? configRetrieveAET
                            : sopRef.getString(Tag.RetrieveAETitle, defRetrieveAET),
                    x -> new ArrayList<>())
                    .add(sopRef.getString(Tag.ReferencedSOPInstanceUID));

        }
        int limit = getInExpressionCountLimit() - 10;
        for (Map.Entry<String, List<String>> entry : iuidsByRetrieveAET.entrySet()) {
            List<String> iuids = entry.getValue();
            int toIndex = 0;
            int size = iuids.size();
            do {
                int fromIndex = toIndex;
                toIndex += limit < 0 ? size : Math.min(limit, size - toIndex);
                em.createNamedQuery(Instance.UPDATE_EXTERNAL_RETRIEVE_AET_BY_SOP_IUIDS)
                        .setParameter(1, iuids.subList(fromIndex, toIndex))
                        .setParameter(2, entry.getKey())
                        .executeUpdate();
            } while (toIndex < size);
        }
        String studyInstanceUID = result.getStudyInstanceUID();
        String seriesInstanceUID = result.getSeriesInstanceUID();
        if (seriesInstanceUID != null) {
            List<String> aets = em.createNamedQuery(Instance.DISTINCT_EXTERNAL_RETRIEVE_AET_BY_SERIES_IUID, String.class)
                    .setParameter(1, seriesInstanceUID)
                    .getResultList();
            em.createNamedQuery(Series.SET_EXTERNAL_RETRIEVE_AET_BY_SERIES_IUID)
                    .setParameter(1, seriesInstanceUID)
                    .setParameter(2, aets.size() == 1 ? aets.get(0) : null)
                    .executeUpdate();
        } else {
            for (Long seriesPk : em.createNamedQuery(Series.SERIES_PKS_OF_STUDY_BY_STUDY_IUID, Long.class)
                    .setParameter(1, studyInstanceUID)
                    .getResultList()) {
                List<String> aets = em.createNamedQuery(Instance.DISTINCT_EXTERNAL_RETRIEVE_AET_BY_SERIES_PK, String.class)
                        .setParameter(1, seriesPk)
                        .getResultList();
                em.createNamedQuery(Series.SET_EXTERNAL_RETRIEVE_AET_BY_SERIES_PK)
                        .setParameter(1, seriesPk)
                        .setParameter(2, aets.size() == 1 ? aets.get(0) : null)
                        .executeUpdate();
            }
        }
        List<String> aets = em.createNamedQuery(Series.DISTINCT_EXTERNAL_RETRIEVE_AET_BY_STUDY_IUID, String.class)
                .setParameter(1, studyInstanceUID)
                .getResultList();
        em.createNamedQuery(Study.SET_EXTERNAL_RETRIEVE_AET_BY_STUDY_IUID)
                .setParameter(1, studyInstanceUID)
                .setParameter(2, aets.size() == 1
                        ? Objects.requireNonNullElse(aets.get(0), "*")
                        : "*")
                .executeUpdate();
        result.setStgCmtResult(eventInfo);
    }

    private StgCmtResult getStgCmtResult(String transactionUID) throws NoResultException {
        StgCmtResult result;
        try {
            result = em.createNamedQuery(StgCmtResult.FIND_BY_TRANSACTION_UID, StgCmtResult.class)
                    .setParameter(1, transactionUID).getSingleResult();
        } catch (NoResultException e) {
            LOG.warn("No Storage Commitment result found with transaction UID : " + transactionUID);
            return null;
        }
        return result;
    }

    private int getInExpressionCountLimit() {
        return ((SessionFactoryImplementor) em.unwrap(Session.class).getSessionFactory())
                .getServiceRegistry().getService(JdbcServices.class)
                .getDialect().getInExpressionCountLimit();
    }

/*
    private void updateExternalRetrieveAETs(Attributes eventInfo, String suid, ExporterDescriptor ed) {
        String configRetrieveAET = ed.getRetrieveAETitles().length > 0 ? ed.getRetrieveAETitles()[0] : null;
        String defRetrieveAET = eventInfo.getString(Tag.RetrieveAETitle, ed.getStgCmtSCPAETitle());
        Sequence sopSeq = eventInfo.getSequence(Tag.ReferencedSOPSequence);
        List<Instance> instances = em.createNamedQuery(Instance.FIND_BY_STUDY_IUID, Instance.class)
                                .setParameter(1, suid).getResultList();
        Set<String> studyExternalAETs = new HashSet<>(4);
        Map<Series,Set<String>> seriesExternalAETsMap = new IdentityHashMap<>();
        for (Instance inst : instances) {
            Attributes sopRef = sopRefOf(inst.getSopInstanceUID(), sopSeq);
            if (sopRef != null) {
                inst.setExternalRetrieveAET(
                        configRetrieveAET != null
                                ? configRetrieveAET
                                : sopRef.getString(Tag.RetrieveAETitle, defRetrieveAET));
            }
            if (!isRejected(inst) && !isRejectionNoteDataRetentionPolicyExpired(inst)) {
                String externalRetrieveAET = inst.getExternalRetrieveAET();
                Series series = inst.getSeries();
                Set<String> seriesExternalAETs = seriesExternalAETsMap.get(series);
                if (seriesExternalAETs == null)
                    seriesExternalAETsMap.put(series, seriesExternalAETs = new HashSet<String>(4));
                seriesExternalAETs.add(externalRetrieveAET);
                studyExternalAETs.add(externalRetrieveAET);
            }
        }
        for (Map.Entry<Series, Set<String>> entry : seriesExternalAETsMap.entrySet()) {
            Set<String> seriesExternalAETs = entry.getValue();
            if (seriesExternalAETs.size() == 1 && !seriesExternalAETs.contains(null))
                entry.getKey().setExternalRetrieveAET(seriesExternalAETs.iterator().next());
        }
        if (studyExternalAETs.size() == 1 && !studyExternalAETs.contains(null))
            instances.get(0).getSeries().getStudy().setExternalRetrieveAET(studyExternalAETs.iterator().next());
    }
*/

    private boolean isRejected(Instance inst) {
        try {
            em.createNamedQuery(RejectedInstance.FIND_BY_UIDS, RejectedInstance.class)
                    .setParameter(1, inst.getSeries().getStudy().getStudyInstanceUID())
                    .setParameter(2, inst.getSeries().getSeriesInstanceUID())
                    .setParameter(3, inst.getSopInstanceUID())
                    .getSingleResult();
            return true;
        } catch (NoResultException e) {
            return false;
        }
    }

    private boolean isRejectionNoteDataRetentionPolicyExpired(Instance inst) {
        if (!inst.getSopClassUID().equals(UID.KeyObjectSelectionDocumentStorage)
                || inst.getConceptNameCode() == null)
            return false;

        ArchiveDeviceExtension arcdev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        RejectionNote rjnote = arcdev.getRejectionNote(inst.getConceptNameCode().getCode());
        return rjnote != null && rjnote.getRejectionNoteType() == RejectionNote.Type.DATA_RETENTION_POLICY_EXPIRED;
    }

    private Attributes sopRefOf(String iuid, Sequence seq) {
        for (Attributes item : seq) {
            if (iuid.equals(item.getString(Tag.ReferencedSOPInstanceUID)))
                return item;
        }
        return null;
    }

    public void persistStgCmtResult(StgCmtResult result) {
        em.persist(result);
    }

    public List<StgCmtResult> listStgCmts(StgCmtResultQueryParam queryParam, int offset, int limit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        QueryBuilder queryBuilder = new QueryBuilder(cb);
        CriteriaQuery<StgCmtResult> q = cb.createQuery(StgCmtResult.class);
        Root<StgCmtResult> stgCmtResult = q.from(StgCmtResult.class);
        List<Predicate> predicates = queryBuilder.stgCmtResultPredicates(stgCmtResult, queryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));
        q.orderBy(cb.desc(stgCmtResult.get(StgCmtResult_.updatedTime)));
        TypedQuery<StgCmtResult> query = em.createQuery(q);
        if (offset > 0)
            query.setFirstResult(offset);
        if (limit > 0)
            query.setMaxResults(limit);
        return query.getResultList();
    }

    public boolean deleteStgCmt(String transactionUID) {
        try {
            StgCmtResult result = getStgCmtResult(transactionUID);
            if (result != null) {
                em.remove(result);
                return true;
            }
        } catch (Exception e) {
            LOG.warn("Deletion of Storage Commitment Result threw exception : " + e);
        }
        return false;
    }

    public int deleteStgCmts(StgCmtResult.Status status, Date updatedBefore) {
        StgCmtResultQueryParam stgCmtResultQueryParam = new StgCmtResultQueryParam(
                status, updatedBefore, null, null, null, null);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        QueryBuilder queryBuilder = new QueryBuilder(cb);
        CriteriaDelete<StgCmtResult> q = cb.createCriteriaDelete(StgCmtResult.class);
        Root<StgCmtResult> stgCmtResult = q.from(StgCmtResult.class);
        List<Predicate> predicates = queryBuilder.stgCmtResultPredicates(stgCmtResult, stgCmtResultQueryParam);
        if (!predicates.isEmpty()) {
            q.where(predicates.toArray(new Predicate[0]));
        }
        return em.createQuery(q).executeUpdate();
    }

    public int updateStgVerTask(Task storageVerificationTask) {
        return em.createNamedQuery(Task.UPDATE_STGVER_RESULT_BY_PK)
                .setParameter(1, storageVerificationTask.getPk())
                .setParameter(2, storageVerificationTask.getCompleted())
                .setParameter(3, storageVerificationTask.getFailed())
                .executeUpdate();
    }

    public int updateSeries(long seriesPk, int failures, long size) {
        return em.createNamedQuery(Series.UPDATE_STGVER_FAILURES)
                .setParameter(1, seriesPk)
                .setParameter(2, failures)
                .setParameter(3, size)
                .executeUpdate();
    }

    public int updateStudySize(Long studyPk, long studySize) {
        return em.createNamedQuery(Study.SET_STUDY_SIZE)
                .setParameter(1, studyPk)
                .setParameter(2, studySize)
                .executeUpdate();
    }

    public List<StgVerBatch> listStgVerBatches(TaskQueryParam taskQueryParam, int offset, int limit) {
        ListStgVerBatches listStgVerBatches = new ListStgVerBatches(taskQueryParam);
        TypedQuery<Tuple> query = em.createQuery(listStgVerBatches.query);
        if (offset > 0)
            query.setFirstResult(offset);
        if (limit > 0)
            query.setMaxResults(limit);

        return query.getResultStream().map(listStgVerBatches::toStgVerBatch).collect(Collectors.toList());
    }
    
    public List<Series.StorageVerification> findSeriesForScheduledStorageVerifications(int fetchSize) {
        return em.createNamedQuery(Series.SCHEDULED_STORAGE_VERIFICATION, Series.StorageVerification.class)
                .setMaxResults(fetchSize)
                .getResultList();
    }

    public int claimForStorageVerification(Long seriesPk, Date verificationTime, Date nextVerificationTime) {
        return em.createNamedQuery(Series.CLAIM_STORAGE_VERIFICATION)
                .setParameter(1, seriesPk)
                .setParameter(2, verificationTime, TemporalType.TIMESTAMP)
                .setParameter(3, nextVerificationTime, TemporalType.TIMESTAMP)
                .executeUpdate();
    }

    private class ListStgVerBatches {
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
        final Path<String> batchIDPath = task.get(Task_.batchID);
        final Expression<Long> completed;
        final Expression<Long> failed;
        final Expression<Long> warning;
        final Expression<Long> canceled;
        final Expression<Long> scheduled;
        final Expression<Long> scheduledForRetry;
        final Expression<Long> inprocess;
        final TaskQueryParam queryParam;

        ListStgVerBatches(TaskQueryParam queryParam) {
            this.queryParam = queryParam;
            this.completed = statusSubquery(Task.Status.COMPLETED).getSelection();
            this.failed = statusSubquery(Task.Status.FAILED).getSelection();
            this.warning = statusSubquery(Task.Status.WARNING).getSelection();
            this.canceled = statusSubquery(Task.Status.CANCELED).getSelection();
            this.scheduled = statusSubquery(Task.Status.SCHEDULED).getSelection();
            this.scheduledForRetry = statusSubquery(Task.Status.SCHEDULED_FOR_RETRY).getSelection();
            this.inprocess = statusSubquery(Task.Status.IN_PROCESS).getSelection();
            query.multiselect(batchIDPath,
                    minProcessingStartTime, maxProcessingStartTime,
                    minProcessingEndTime, maxProcessingEndTime,
                    minScheduledTime, maxScheduledTime,
                    minCreatedTime, maxCreatedTime,
                    minUpdatedTime, maxUpdatedTime,
                    completed, failed, warning, canceled, scheduled, scheduledForRetry, inprocess);
            query.groupBy(task.get(Task_.batchID));
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(task.get(Task_.type), queryParam.getType()));
            if (queryParam.getBatchID() != null)
                predicates.add(cb.equal(task.get(Task_.batchID), queryParam.getBatchID()));
            else
                predicates.add(task.get(Task_.batchID).isNotNull());
            if (queryParam.getStatus() != null)
                predicates.add(cb.equal(task.get(Task_.status), queryParam.getStatus()));
            queryBuilder.matchStgVerBatch(predicates, queryParam, task);
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
            queryBuilder.matchStgVerBatch(predicates, queryParam, sqtask);
            sq.where(predicates.toArray(new Predicate[0]));
            sq.select(cb.count(sqtask));
            return sq;
        }

        StgVerBatch toStgVerBatch(Tuple tuple) {
            String batchID = tuple.get(batchIDPath);
            StgVerBatch stgVerBatch = new StgVerBatch(batchID);
            stgVerBatch.setProcessingStartTimeRange(
                    tuple.get(minProcessingStartTime),
                    tuple.get(maxProcessingStartTime));
            stgVerBatch.setProcessingEndTimeRange(
                    tuple.get(minProcessingEndTime),
                    tuple.get(maxProcessingEndTime));
            stgVerBatch.setScheduledTimeRange(
                    tuple.get(minScheduledTime),
                    tuple.get(maxScheduledTime));
            stgVerBatch.setCreatedTimeRange(
                    tuple.get(minCreatedTime),
                    tuple.get(maxCreatedTime));
            stgVerBatch.setUpdatedTimeRange(
                    tuple.get(minUpdatedTime),
                    tuple.get(maxUpdatedTime));

            CriteriaQuery<String> distinct = cb.createQuery(String.class).distinct(true);
            Root<Task> stgverTask = distinct.from(Task.class);
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(stgverTask.get(Task_.batchID), batchID));
            queryBuilder.matchStgVerBatch(predicates, queryParam, stgverTask);
            distinct.where(predicates.toArray(new Predicate[0]));
            stgVerBatch.setDeviceNames(select(distinct, stgverTask.get(Task_.deviceName)));
            stgVerBatch.setLocalAETs(select(distinct, stgverTask.get(Task_.localAET)));
            stgVerBatch.setCompleted(tuple.get(completed));
            stgVerBatch.setCanceled(tuple.get(canceled));
            stgVerBatch.setWarning(tuple.get(warning));
            stgVerBatch.setFailed(tuple.get(failed));
            stgVerBatch.setScheduled(tuple.get(scheduled));
            stgVerBatch.setScheduledForRetry(tuple.get(scheduledForRetry));
            stgVerBatch.setInProcess(tuple.get(inprocess));
            return stgVerBatch;
        }

        private List<String> select(CriteriaQuery<String> query, Path<String> path) {
            return em.createQuery(query.select(path)).getResultList();
        }
    }

    public boolean scheduleStgVerTask(String localAET, QueryRetrieveLevel2 qrlevel,
                                      HttpServletRequestInfo httpServletRequestInfo,
                                      String studyInstanceUID, String seriesInstanceUID, String sopInstanceUID,
                                      String batchID, Date scheduledTime, StorageVerificationPolicy storageVerificationPolicy,
                                      Boolean updateLocationStatus, String... storageIDs) {
        Task task = new Task();
        task.setDeviceName(device.getDeviceName());
        task.setQueueName(StgCmtManager.QUEUE_NAME);
        task.setType(Task.Type.STGVER);
        task.setScheduledTime(scheduledTime);
        if (httpServletRequestInfo != null) {
            task.setRequesterUserID(httpServletRequestInfo.requesterUserID);
            task.setRequesterHost(httpServletRequestInfo.requesterHost);
            task.setRequestURI(requestURL(httpServletRequestInfo));
        }
        task.setStatus(Task.Status.SCHEDULED);
        task.setBatchID(batchID);
        task.setLocalAET(localAET);
        task.setStorageVerificationPolicy(storageVerificationPolicy);
        task.setUpdateLocationStatus(updateLocationStatus);
        task.setStorageIDs(storageIDs);
        task.setStudyInstanceUID(studyInstanceUID);
        if (qrlevel != QueryRetrieveLevel2.STUDY) {
            task.setSeriesInstanceUID(seriesInstanceUID);
            if (qrlevel == QueryRetrieveLevel2.IMAGE) {
                task.setSOPInstanceUID(sopInstanceUID);
            }
        }
        if (isStorageVerificationTaskAlreadyScheduled(task)) {
            return false;
        }
        em.persist(task);
        LOG.info("Create {}", task);
        return true;
    }

    private String requestURL(HttpServletRequestInfo httpServletRequestInfo) {
        String requestURI = httpServletRequestInfo.requestURI;
        String queryString = httpServletRequestInfo.queryString;
        return queryString == null ? requestURI : requestURI + '?' + queryString;
    }

    public boolean scheduleStgVerTask(String localAET, String studyInstanceUID, String seriesInstanceUID, String batchID) {
        return scheduleStgVerTask(localAET, QueryRetrieveLevel2.SERIES, null,
                studyInstanceUID, seriesInstanceUID, null,
                batchID, new Date(), null, null);
    }

    private boolean isStorageVerificationTaskAlreadyScheduled(Task storageVerificationTask) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        Root<Task> stgVerTask = q.from(Task.class);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(stgVerTask.get(Task_.type), Task.Type.STGVER));
        predicates.add(stgVerTask.get(Task_.status).in(Task.Status.SCHEDULED, Task.Status.IN_PROCESS));
        predicates.add(cb.equal(
                stgVerTask.get(Task_.studyInstanceUID), storageVerificationTask.getStudyInstanceUID()));
        if (storageVerificationTask.getSeriesInstanceUID() == null)
            predicates.add(stgVerTask.get(Task_.seriesInstanceUID).isNull());
        else {
            predicates.add(cb.or(
                    stgVerTask.get(Task_.seriesInstanceUID).isNull(),
                    cb.equal(stgVerTask.get(Task_.seriesInstanceUID),
                            storageVerificationTask.getSeriesInstanceUID())));
            if (storageVerificationTask.getSOPInstanceUID() == null)
                predicates.add(stgVerTask.get(Task_.sopInstanceUID).isNull());
            else
                predicates.add(cb.or(
                        stgVerTask.get(Task_.sopInstanceUID).isNull(),
                        cb.equal(stgVerTask.get(Task_.sopInstanceUID),
                                storageVerificationTask.getSOPInstanceUID())));
        }
        if (storageVerificationTask.getStorageVerificationPolicy() != null)
            predicates.add(cb.equal(stgVerTask.get(Task_.storageVerificationPolicy),
                    storageVerificationTask.getStorageVerificationPolicy()));
        if (storageVerificationTask.getStorageIDsAsString() != null)
            predicates.add(cb.equal(stgVerTask.get(Task_.storageIDs),
                    storageVerificationTask.getStorageIDsAsString()));
        try (Stream<Long> resultStream = em.createQuery(q
                .where(predicates.toArray(new Predicate[0]))
                .select(stgVerTask.get(Task_.pk)))
                .getResultStream()) {
            Optional<Long> prev = resultStream.findFirst();
            if (prev.isPresent()) {
                LOG.info("Previous {} found - suppress duplicate storage verification", prev.get());
                return true;
            }
        }
        return false;
    }

    public long countScheduledTasksOnThisDevice(String queueName) {
        return em.createNamedQuery(Task.COUNT_BY_DEVICE_AND_QUEUE_NAME_AND_STATUS, Long.class)
                .setParameter(1, device.getDeviceName())
                .setParameter(2, queueName)
                .setParameter(3, Task.Status.SCHEDULED).getSingleResult();
    }
}
