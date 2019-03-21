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

import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Predicate;
import javax.persistence.Tuple;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.conf.RejectionNote;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.event.QueueMessageEvent;
import org.dcm4chee.arc.qmgt.HttpServletRequestInfo;
import org.dcm4chee.arc.qmgt.IllegalTaskStateException;
import org.dcm4chee.arc.qmgt.QueueManager;
import org.dcm4chee.arc.qmgt.QueueSizeLimitExceededException;
import org.dcm4chee.arc.query.util.MatchTask;
import org.dcm4chee.arc.query.util.TaskQueryParam;
import org.dcm4chee.arc.stgcmt.StgVerBatch;
import org.dcm4chee.arc.stgcmt.StgCmtManager;
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
import java.util.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Sep 2016
 */
@Stateless
public class StgCmtEJB {

    private final Logger LOG = LoggerFactory.getLogger(StgCmtEJB.class);

    private Join<StorageVerificationTask, QueueMessage> queueMsg;
    private Root<StorageVerificationTask> stgVerTask;
    private Root<StgCmtResult> stgCmtResult;

    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    @Inject
    private Device device;

    @Inject
    private QueueManager queueManager;

    public void addExternalRetrieveAETs(Attributes eventInfo, Device device) {
        String transactionUID = eventInfo.getString(Tag.TransactionUID);
        StgCmtResult result = getStgCmtResult(transactionUID);
        if (result == null)
            return;
        updateExternalRetrieveAETs(eventInfo, result.getStudyInstanceUID(),
                device.getDeviceExtension(ArchiveDeviceExtension.class).getExporterDescriptorNotNull(result.getExporterID()));
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
            if (!isRejectedOrRejectionNoteDataRetentionPolicyExpired(inst)) {
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

    private boolean isRejectedOrRejectionNoteDataRetentionPolicyExpired(Instance inst) {
        if (inst.getRejectionNoteCode() != null)
            return true;

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

    public List<StgCmtResult> listStgCmts(TaskQueryParam stgCmtResultQueryParam, int offset, int limit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        MatchTask matchTask = new MatchTask(cb);
        CriteriaQuery<StgCmtResult> q = cb.createQuery(StgCmtResult.class);
        stgCmtResult = q.from(StgCmtResult.class);
        TypedQuery<StgCmtResult> query = em.createQuery(restrict(stgCmtResultQueryParam, matchTask, q));
        if (offset > 0)
            query.setFirstResult(offset);
        if (limit > 0)
            query.setMaxResults(limit);
        return query.getResultList();
    }

    private <T> CriteriaQuery<T> restrict(
            TaskQueryParam stgCmtResultQueryParam, MatchTask matchTask, CriteriaQuery<T> q) {
        List<Predicate> predicates = matchTask.matchStgCmtResult(stgCmtResult, stgCmtResultQueryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));
        return q;
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
        TaskQueryParam stgCmtResultQueryParam = new TaskQueryParam();
        stgCmtResultQueryParam.setStgCmtStatus(status);
        stgCmtResultQueryParam.setUpdatedBefore(updatedBefore);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        MatchTask matchTask = new MatchTask(cb);
        CriteriaDelete<StgCmtResult> q = cb.createCriteriaDelete(StgCmtResult.class);
        stgCmtResult = q.from(StgCmtResult.class);
        List<Predicate> predicates = matchTask.matchStgCmtResult(stgCmtResult, stgCmtResultQueryParam);
        q.where(predicates.toArray(new Predicate[0]));
        return em.createQuery(q).executeUpdate();
    }

    public boolean  scheduleStgVerTask(StorageVerificationTask storageVerificationTask, HttpServletRequestInfo httpServletRequestInfo,
                                      String batchID) throws QueueSizeLimitExceededException {
        if (isAlreadyScheduled(storageVerificationTask))
            return false;

        try {
            ObjectMessage msg = queueManager.createObjectMessage(0);
            msg.setStringProperty("LocalAET", storageVerificationTask.getLocalAET());
            msg.setStringProperty("StudyInstanceUID", storageVerificationTask.getStudyInstanceUID());
            if (storageVerificationTask.getSeriesInstanceUID() != null) {
                msg.setStringProperty("SeriesInstanceUID", storageVerificationTask.getSeriesInstanceUID());
                if (storageVerificationTask.getSOPInstanceUID() != null) {
                    msg.setStringProperty("SOPInstanceUID", storageVerificationTask.getSOPInstanceUID());
                }
            }
            if (httpServletRequestInfo != null) {
                httpServletRequestInfo.copyTo(msg);
            }
            QueueMessage queueMessage = queueManager.scheduleMessage(StgCmtManager.QUEUE_NAME, msg,
                    Message.DEFAULT_PRIORITY, batchID, 0L);
            storageVerificationTask.setQueueMessage(queueMessage);
            em.persist(storageVerificationTask);
        } catch (JMSException e) {
            throw QueueMessage.toJMSRuntimeException(e);
        }
        return true;
    }

    private boolean isAlreadyScheduled(StorageVerificationTask storageVerificationTask) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        stgVerTask = q.from(StorageVerificationTask.class);
        queueMsg = stgVerTask.join(StorageVerificationTask_.queueMessage);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(queueMsg.get(QueueMessage_.status).in(QueueMessage.Status.SCHEDULED, QueueMessage.Status.IN_PROCESS));
        predicates.add(cb.equal(
                stgVerTask.get(StorageVerificationTask_.studyInstanceUID), storageVerificationTask.getStudyInstanceUID()));
        if (storageVerificationTask.getSeriesInstanceUID() == null)
            predicates.add(stgVerTask.get(StorageVerificationTask_.seriesInstanceUID).isNull());
        else {
            predicates.add(cb.or(
                    stgVerTask.get(StorageVerificationTask_.seriesInstanceUID).isNull(),
                    cb.equal(stgVerTask.get(StorageVerificationTask_.seriesInstanceUID),
                            storageVerificationTask.getSeriesInstanceUID())));
            if (storageVerificationTask.getSOPInstanceUID() == null)
                predicates.add(stgVerTask.get(StorageVerificationTask_.sopInstanceUID).isNull());
            else
                predicates.add(cb.or(
                        stgVerTask.get(StorageVerificationTask_.sopInstanceUID).isNull(),
                        cb.equal(stgVerTask.get(StorageVerificationTask_.sopInstanceUID),
                                storageVerificationTask.getSOPInstanceUID())));
        }
        if (storageVerificationTask.getStorageVerificationPolicy() != null)
            predicates.add(cb.equal(stgVerTask.get(StorageVerificationTask_.storageVerificationPolicy),
                    storageVerificationTask.getStorageVerificationPolicy()));
        if (storageVerificationTask.getStorageIDsAsString() != null)
            predicates.add(cb.equal(stgVerTask.get(StorageVerificationTask_.storageIDs),
                    storageVerificationTask.getStorageIDsAsString()));
        Iterator<Long> iterator = em.createQuery(q
                .where(predicates.toArray(new Predicate[0]))
                .select(stgVerTask.get(StorageVerificationTask_.pk)))
                .getResultStream()
                .iterator();
        if (iterator.hasNext()) {
            LOG.info("Previous {} found - suppress duplicate storage verification", iterator.next());
            return true;
        }
        return false;
    }

    public int updateStgVerTask(StorageVerificationTask storageVerificationTask) {
        return em.createNamedQuery(StorageVerificationTask.UPDATE_RESULT_BY_PK)
                .setParameter(1, storageVerificationTask.getPk())
                .setParameter(2, storageVerificationTask.getCompleted())
                .setParameter(3, storageVerificationTask.getFailed())
                .executeUpdate();
    }

    public int updateSeries(String studyIUID, String seriesIUID, int failures) {
        return em.createNamedQuery(Series.UPDATE_STGVER_FAILURES)
                .setParameter(1, studyIUID)
                .setParameter(2, seriesIUID)
                .setParameter(3, failures)
                .executeUpdate();
    }

    private CriteriaQuery<String> createQuery(TaskQueryParam queueTaskQueryParam, TaskQueryParam stgVerTaskQueryParam) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        MatchTask matchTask = new MatchTask(cb);
        CriteriaQuery<String> q = cb.createQuery(String.class);
        stgVerTask = q.from(StorageVerificationTask.class);
        queueMsg = stgVerTask.join(StorageVerificationTask_.queueMessage);
        return restrict(queueTaskQueryParam, stgVerTaskQueryParam, matchTask, q);
    }

    public boolean cancelStgVerTask(Long pk, QueueMessageEvent queueEvent) throws IllegalTaskStateException {
        StorageVerificationTask task = em.find(StorageVerificationTask.class, pk);
        if (task == null)
            return false;

        QueueMessage queueMessage = task.getQueueMessage();
        if (queueMessage == null)
            throw new IllegalTaskStateException("Cannot cancel Task with status: 'TO SCHEDULE'");

        queueManager.cancelTask(queueMessage.getMessageID(), queueEvent);
        LOG.info("Cancel {}", task);
        return true;
    }

    public long cancelStgVerTasks(TaskQueryParam queueTaskQueryParam, TaskQueryParam stgVerTaskQueryParam) {
        return queueManager.cancelStgVerTasks(queueTaskQueryParam, stgVerTaskQueryParam);
    }

    public String findDeviceNameByPk(Long pk) {
        try {
            return em.createNamedQuery(StorageVerificationTask.FIND_DEVICE_BY_PK, String.class)
                    .setParameter(1, pk)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    public void rescheduleStgVerTask(Long pk, QueueMessageEvent queueEvent) {
        StorageVerificationTask task = em.find(StorageVerificationTask.class, pk);
        if (task == null)
            return;

        LOG.info("Reschedule {}", task);
        rescheduleStgVerTask(task.getQueueMessage().getMessageID(), queueEvent);
    }

    public void rescheduleStgVerTask(String stgVerTaskQueueMsgId, QueueMessageEvent queueEvent) {
        queueManager.rescheduleTask(stgVerTaskQueueMsgId, StgCmtManager.QUEUE_NAME, queueEvent);
    }

    public List<String> listDistinctDeviceNames(TaskQueryParam queueTaskQueryParam, TaskQueryParam stgVerTaskQueryParam) {
        return em.createQuery(createQuery(queueTaskQueryParam, stgVerTaskQueryParam)
                .select(queueMsg.get(QueueMessage_.deviceName))
                .distinct(true))
                .getResultList();
    }

    public List<String> listStgVerTaskQueueMsgIDs(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam stgVerTaskQueryParam, int limit) {
        return em.createQuery(createQuery(queueTaskQueryParam, stgVerTaskQueryParam)
                .select(queueMsg.get(QueueMessage_.messageID)))
                .setMaxResults(limit)
                .getResultList();
    }

    public boolean deleteStgVerTask(Long pk, QueueMessageEvent queueEvent) {
        StorageVerificationTask task = em.find(StorageVerificationTask.class, pk);
        if (task == null)
            return false;

        queueManager.deleteTask(task.getQueueMessage().getMessageID(), queueEvent);
        LOG.info("Delete {}", task);
        return true;
    }

    public List<StgVerBatch> listStgVerBatches(
            TaskQueryParam queueBatchQueryParam, TaskQueryParam stgVerBatchQueryParam, int offset, int limit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        MatchTask matchTask = new MatchTask(cb);
        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        stgVerTask = q.from(StorageVerificationTask.class);
        queueMsg = stgVerTask.join(StorageVerificationTask_.queueMessage);

        Expression<Date> minProcessingStartTime = cb.least(queueMsg.get(QueueMessage_.processingStartTime));
        Expression<Date> maxProcessingStartTime = cb.greatest(queueMsg.get(QueueMessage_.processingStartTime));
        Expression<Date> minProcessingEndTime = cb.least(queueMsg.get(QueueMessage_.processingEndTime));
        Expression<Date> maxProcessingEndTime = cb.greatest(queueMsg.get(QueueMessage_.processingEndTime));
        Expression<Date> minScheduledTime = cb.least(queueMsg.get(QueueMessage_.scheduledTime));
        Expression<Date> maxScheduledTime = cb.greatest(queueMsg.get(QueueMessage_.scheduledTime));
        Expression<Date> minCreatedTime = cb.least(stgVerTask.get(StorageVerificationTask_.createdTime));
        Expression<Date> maxCreatedTime = cb.greatest(stgVerTask.get(StorageVerificationTask_.createdTime));
        Expression<Date> minUpdatedTime = cb.least(stgVerTask.get(StorageVerificationTask_.updatedTime));
        Expression<Date> maxUpdatedTime = cb.greatest(stgVerTask.get(StorageVerificationTask_.updatedTime));
        Path<String> batchid = queueMsg.get(QueueMessage_.batchID);

        CriteriaQuery<Tuple> multiselect = orderBatch(
                stgVerBatchQueryParam,
                matchTask,
                groupBy(restrictBatch(queueBatchQueryParam, stgVerBatchQueryParam, matchTask, q)))
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

        List<StgVerBatch> stgVerBatches = new ArrayList<>();
        query.getResultStream().forEach(batch -> {
            String batchID = batch.get(batchid);
            StgVerBatch stgVerBatch = new StgVerBatch(batchID);
            stgVerBatch.setProcessingStartTimeRange(
                    batch.get(maxProcessingStartTime),
                    batch.get(maxProcessingStartTime));
            stgVerBatch.setProcessingEndTimeRange(
                    batch.get(minProcessingEndTime),
                    batch.get(maxProcessingEndTime));
            stgVerBatch.setScheduledTimeRange(
                    batch.get(minScheduledTime),
                    batch.get(maxScheduledTime));
            stgVerBatch.setCreatedTimeRange(
                    batch.get(minCreatedTime),
                    batch.get(maxCreatedTime));
            stgVerBatch.setUpdatedTimeRange(
                    batch.get(minUpdatedTime),
                    batch.get(maxUpdatedTime));

            stgVerBatch.setDeviceNames(em.createQuery(
                    batchIDQuery(batchID)
                            .select(queueMsg.get(QueueMessage_.deviceName))
                            .distinct(true)
                            .orderBy(cb.asc(queueMsg.get(QueueMessage_.deviceName))))
                    .getResultList());
            stgVerBatch.setLocalAETs(em.createQuery(
                    batchIDQuery(batchID)
                            .select(stgVerTask.get(StorageVerificationTask_.localAET))
                            .distinct(true)
                            .orderBy(cb.asc(stgVerTask.get(StorageVerificationTask_.localAET))))
                    .getResultList());

            stgVerBatch.setCompleted(em.createQuery(
                    batchIDQuery(batchID, QueueMessage.Status.COMPLETED).select(cb.count(queueMsg)))
                    .getSingleResult());
            stgVerBatch.setCanceled(em.createQuery(
                    batchIDQuery(batchID, QueueMessage.Status.CANCELED).select(cb.count(queueMsg)))
                    .getSingleResult());
            stgVerBatch.setWarning(em.createQuery(
                    batchIDQuery(batchID, QueueMessage.Status.WARNING).select(cb.count(queueMsg)))
                    .getSingleResult());
            stgVerBatch.setFailed(em.createQuery(
                    batchIDQuery(batchID, QueueMessage.Status.FAILED).select(cb.count(queueMsg)))
                    .getSingleResult());
            stgVerBatch.setScheduled(em.createQuery(
                    batchIDQuery(batchID, QueueMessage.Status.SCHEDULED).select(cb.count(queueMsg)))
                    .getSingleResult());
            stgVerBatch.setInProcess(em.createQuery(
                    batchIDQuery(batchID, QueueMessage.Status.IN_PROCESS).select(cb.count(queueMsg)))
                    .getSingleResult());

            stgVerBatches.add(stgVerBatch);
        });

        return stgVerBatches;
    }

    private <T> CriteriaQuery<T> restrictBatch(
            TaskQueryParam queueBatchQueryParam, TaskQueryParam stgVerBatchQueryParam, MatchTask matchTask, CriteriaQuery<T> q) {
        List<Predicate> predicates = matchTask.stgVerBatchPredicates(
                queueMsg,
                stgVerTask,
                queueBatchQueryParam,
                stgVerBatchQueryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));
        return q;
    }

    private <T> CriteriaQuery<T> orderBatch(
            TaskQueryParam stgVerBatchQueryParam, MatchTask matchTask, CriteriaQuery<T> q) {
        if (stgVerBatchQueryParam.getOrderBy() != null)
            q = q.orderBy(matchTask.stgVerBatchOrder(stgVerBatchQueryParam.getOrderBy(), stgVerTask));
        return q;
    }

    private <T> CriteriaQuery<T> groupBy(CriteriaQuery<T> q) {
        return q.groupBy(queueMsg.get(QueueMessage_.batchID));
    }

    private CriteriaQuery<String> batchIDQuery(String batchID) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<String> q = cb.createQuery(String.class);
        stgVerTask = q.from(StorageVerificationTask.class);
        queueMsg = stgVerTask.join(StorageVerificationTask_.queueMessage, JoinType.LEFT);
        return q.where(cb.equal(queueMsg.get(QueueMessage_.batchID), batchID));
    }

    private CriteriaQuery<Long> batchIDQuery(String batchID, QueueMessage.Status status) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        stgVerTask = q.from(StorageVerificationTask.class);
        queueMsg = stgVerTask.join(StorageVerificationTask_.queueMessage, JoinType.LEFT);
        return q.where(
                cb.equal(queueMsg.get(QueueMessage_.batchID), batchID),
                cb.equal(queueMsg.get(QueueMessage_.status), status));
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

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Iterator<StorageVerificationTask> listStgVerTasks(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam stgVerTaskQueryParam, int offset, int limit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        MatchTask matchTask = new MatchTask(cb);
        TypedQuery<StorageVerificationTask> query = em.createQuery(select(cb, matchTask, queueTaskQueryParam, stgVerTaskQueryParam))
                .setHint(QueryHints.FETCH_SIZE, queryFetchSize());
        if (offset > 0)
            query.setFirstResult(offset);
        if (limit > 0)
            query.setMaxResults(limit);
        return query.getResultStream().iterator();
    }

    public long countTasks(TaskQueryParam queueTaskQueryParam, TaskQueryParam stgVerTaskQueryParam) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        MatchTask matchTask = new MatchTask(cb);

        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        stgVerTask = q.from(StorageVerificationTask.class);
        queueMsg = stgVerTask.join(StorageVerificationTask_.queueMessage);

        return em.createQuery(
                restrict(queueTaskQueryParam, stgVerTaskQueryParam, matchTask, q).select(cb.count(stgVerTask)))
                .getSingleResult();
    }

    private <T> CriteriaQuery<T> restrict(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam stgVerTaskQueryParam, MatchTask matchTask, CriteriaQuery<T> q) {
        List<Predicate> predicates = matchTask.stgVerPredicates(
                queueMsg,
                stgVerTask,
                queueTaskQueryParam,
                stgVerTaskQueryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));
        return q;
    }

    private int queryFetchSize() {
        return device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class).getQueryFetchSize();
    }

    private CriteriaQuery<StorageVerificationTask> select(CriteriaBuilder cb,
                                               MatchTask matchTask, TaskQueryParam queueTaskQueryParam, TaskQueryParam stgVerTaskQueryParam) {
        CriteriaQuery<StorageVerificationTask> q = cb.createQuery(StorageVerificationTask.class);
        stgVerTask = q.from(StorageVerificationTask.class);
        queueMsg = stgVerTask.join(StorageVerificationTask_.queueMessage);

        q = restrict(queueTaskQueryParam, stgVerTaskQueryParam, matchTask, q);
        if (stgVerTaskQueryParam.getOrderBy() != null)
            q.orderBy(matchTask.stgVerTaskOrder(stgVerTaskQueryParam.getOrderBy(), stgVerTask));

        return q.select(stgVerTask);
    }

    public int deleteTasks(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam stgVerTaskQueryParam, int deleteTasksFetchSize) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        MatchTask matchTask = new MatchTask(cb);
        CriteriaQuery<String> q = cb.createQuery(String.class);
        stgVerTask = q.from(StorageVerificationTask.class);
        queueMsg = stgVerTask.join(StorageVerificationTask_.queueMessage);

        TypedQuery<String> query = em.createQuery(restrict(queueTaskQueryParam, stgVerTaskQueryParam, matchTask, q)
                .multiselect(queueMsg.get(QueueMessage_.messageID)));

        if (deleteTasksFetchSize > 0)
            query.setMaxResults(deleteTasksFetchSize);

        List<String> referencedQueueMsgIDs = query.getResultList();
        referencedQueueMsgIDs.forEach(queueMsgID -> queueManager.deleteTask(queueMsgID, null));
        return referencedQueueMsgIDs.size();
    }
}
