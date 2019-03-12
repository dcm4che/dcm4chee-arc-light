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

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.jpa.hibernate.HibernateQuery;
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
import org.hibernate.Session;
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
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.Root;
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
    
    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    @Inject
    private Device device;

    @Inject
    private QueueManager queueManager;

    private static final Expression<?>[] SELECT = {
            QQueueMessage.queueMessage.processingStartTime.min(),
            QQueueMessage.queueMessage.processingStartTime.max(),
            QQueueMessage.queueMessage.processingEndTime.min(),
            QQueueMessage.queueMessage.processingEndTime.max(),
            QQueueMessage.queueMessage.scheduledTime.min(),
            QQueueMessage.queueMessage.scheduledTime.max(),
            QStorageVerificationTask.storageVerificationTask.createdTime.min(),
            QStorageVerificationTask.storageVerificationTask.createdTime.max(),
            QStorageVerificationTask.storageVerificationTask.updatedTime.min(),
            QStorageVerificationTask.storageVerificationTask.updatedTime.max(),
            QQueueMessage.queueMessage.batchID
    };

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

    public List<StgCmtResult> listStgCmts(StgCmtResult.Status status, String studyUID, String exporterID, String batchID,
                                          String msgID, int offset, int limit) {
        HibernateQuery<StgCmtResult> query = getStgCmtResults(status, studyUID, exporterID, batchID, msgID);
        if (limit > 0)
            query.limit(limit);
        if (offset > 0)
            query.offset(offset);
        List<StgCmtResult> results = query.fetch();
        if (results.isEmpty())
            return Collections.emptyList();
        return results;
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
        List<StgCmtResult> results = status != null
                                    ? updatedBefore != null
                                        ? em.createNamedQuery(StgCmtResult.FIND_BY_STATUS_AND_UPDATED_BEFORE, StgCmtResult.class)
                                            .setParameter(1, status).setParameter(2, updatedBefore).getResultList()
                                        : em.createNamedQuery(StgCmtResult.FIND_BY_STATUS, StgCmtResult.class)
                                            .setParameter(1, status).getResultList()
                                    : updatedBefore != null
                                        ? em.createNamedQuery(StgCmtResult.FIND_BY_UPDATED_BEFORE, StgCmtResult.class)
                                            .setParameter(1, updatedBefore).getResultList()
                                        : em.createNamedQuery(StgCmtResult.FIND_ALL, StgCmtResult.class).getResultList();
        if (results.isEmpty())
            return 0;
        for (StgCmtResult result : results)
            em.remove(result);
        return results.size();
    }

    private HibernateQuery<StgCmtResult> getStgCmtResults(StgCmtResult.Status status, String studyUID, String exporterId,
                                                          String batchID, String msgID) {
        Predicate predicate = getPredicates(status, studyUID, exporterId, batchID, msgID);
        HibernateQuery<StgCmtResult> query = new HibernateQuery<Void>(em.unwrap(Session.class))
                .select(QStgCmtResult.stgCmtResult).from(QStgCmtResult.stgCmtResult);
        return query.where(predicate);
    }

    private Predicate getPredicates(StgCmtResult.Status status, String studyUID, String exporterId, String batchID,
                                    String msgID) {
        BooleanBuilder predicate = new BooleanBuilder();
        if (status != null)
            predicate.and(QStgCmtResult.stgCmtResult.status.eq(status));
        if (studyUID != null)
            predicate.and(QStgCmtResult.stgCmtResult.studyInstanceUID.eq(studyUID));
        if (exporterId != null)
            predicate.and(QStgCmtResult.stgCmtResult.exporterID.eq(exporterId.toUpperCase()));
        if (batchID != null)
            predicate.and(QStgCmtResult.stgCmtResult.batchID.eq(batchID));
        if (msgID != null)
            predicate.and(QStgCmtResult.stgCmtResult.messageID.eq(msgID));
        return predicate;
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

        List<javax.persistence.criteria.Predicate> predicates = new ArrayList<>();
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
        return em.createQuery(q
                    .where(predicates.toArray(new javax.persistence.criteria.Predicate[0]))
                    .select(stgVerTask.get(StorageVerificationTask_.pk)))
                .getResultStream()
                .iterator()
                .hasNext();
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

    private HibernateQuery<StorageVerificationTask> createQuery(Predicate matchQueueMessage, Predicate matchStgVerTask) {
        HibernateQuery<QueueMessage> queueMsgQuery = new HibernateQuery<QueueMessage>(em.unwrap(Session.class))
                .from(QQueueMessage.queueMessage)
                .where(matchQueueMessage);
        return new HibernateQuery<StorageVerificationTask>(em.unwrap(Session.class))
                .from(QStorageVerificationTask.storageVerificationTask)
                .where(matchStgVerTask, QStorageVerificationTask.storageVerificationTask.queueMessage.in(queueMsgQuery));
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

    public long cancelStgVerTasks(Predicate matchQueueMessage, Predicate matchStgVerTask, QueueMessage.Status prev)
            throws IllegalTaskStateException {
        return queueManager.cancelStgVerTasks(matchQueueMessage, matchStgVerTask, prev);
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

    public List<String> listDistinctDeviceNames(Predicate matchQueueMessage, Predicate matchStgVerTask) {
        return createQuery(matchQueueMessage, matchStgVerTask)
                .select(QQueueMessage.queueMessage.deviceName)
                .distinct()
                .fetch();
    }

    public List<String> listStgVerTaskQueueMsgIDs(Predicate matchQueueMsg, Predicate matchStgVerTask, int limit) {
        return createQuery(matchQueueMsg, matchStgVerTask)
                .select(QQueueMessage.queueMessage.messageID)
                .limit(limit)
                .fetch();
    }

    public boolean deleteStgVerTask(Long pk, QueueMessageEvent queueEvent) {
        StorageVerificationTask task = em.find(StorageVerificationTask.class, pk);
        if (task == null)
            return false;

        queueManager.deleteTask(task.getQueueMessage().getMessageID(), queueEvent);
        LOG.info("Delete {}", task);
        return true;
    }

    public List<StgVerBatch> listStgVerBatches(Predicate matchQueueBatch, Predicate matchStgCmtBatch,
                                               OrderSpecifier<Date> order, int offset, int limit) {
        HibernateQuery<StorageVerificationTask> stgVerTaskQuery = createQuery(matchQueueBatch, matchStgCmtBatch);
        if (limit > 0)
            stgVerTaskQuery.limit(limit);
        if (offset > 0)
            stgVerTaskQuery.offset(offset);

        List<Tuple> batches = stgVerTaskQuery.select(SELECT)
                .groupBy(QQueueMessage.queueMessage.batchID)
                .orderBy(order)
                .fetch();

        List<StgVerBatch> stgVerBatches = new ArrayList<>();
        for (Tuple batch : batches) {
            StgVerBatch stgVerBatch = new StgVerBatch();
            String batchID = batch.get(QQueueMessage.queueMessage.batchID);
            stgVerBatch.setBatchID(batchID);

            stgVerBatch.setCreatedTimeRange(
                    batch.get(QStorageVerificationTask.storageVerificationTask.createdTime.min()),
                    batch.get(QStorageVerificationTask.storageVerificationTask.createdTime.max()));
            stgVerBatch.setUpdatedTimeRange(
                    batch.get(QStorageVerificationTask.storageVerificationTask.updatedTime.min()),
                    batch.get(QStorageVerificationTask.storageVerificationTask.updatedTime.max()));
            stgVerBatch.setScheduledTimeRange(
                    batch.get(QQueueMessage.queueMessage.scheduledTime.min()),
                    batch.get(QQueueMessage.queueMessage.scheduledTime.max()));
            stgVerBatch.setProcessingStartTimeRange(
                    batch.get(QQueueMessage.queueMessage.processingStartTime.min()),
                    batch.get(QQueueMessage.queueMessage.processingStartTime.max()));
            stgVerBatch.setProcessingEndTimeRange(
                    batch.get(QQueueMessage.queueMessage.processingEndTime.min()),
                    batch.get(QQueueMessage.queueMessage.processingEndTime.max()));

            stgVerBatch.setDeviceNames(
                    batchIDQuery(batchID)
                            .select(QQueueMessage.queueMessage.deviceName)
                            .distinct()
                            .orderBy(QQueueMessage.queueMessage.deviceName.asc())
                            .fetch());
            stgVerBatch.setLocalAETs(
                    batchIDQuery(batchID)
                            .select(QStorageVerificationTask.storageVerificationTask.localAET)
                            .distinct()
                            .orderBy(QStorageVerificationTask.storageVerificationTask.localAET.asc())
                            .fetch());

            stgVerBatch.setCompleted(
                    batchIDQuery(batchID)
                            .where(QQueueMessage.queueMessage.status.eq(QueueMessage.Status.COMPLETED))
                            .fetchCount());
            stgVerBatch.setCanceled(
                    batchIDQuery(batchID)
                            .where(QQueueMessage.queueMessage.status.eq(QueueMessage.Status.CANCELED))
                            .fetchCount());
            stgVerBatch.setWarning(
                    batchIDQuery(batchID)
                            .where(QQueueMessage.queueMessage.status.eq(QueueMessage.Status.WARNING))
                            .fetchCount());
            stgVerBatch.setFailed(
                    batchIDQuery(batchID)
                            .where(QQueueMessage.queueMessage.status.eq(QueueMessage.Status.FAILED))
                            .fetchCount());
            stgVerBatch.setScheduled(
                    batchIDQuery(batchID)
                            .where(QQueueMessage.queueMessage.status.eq(QueueMessage.Status.SCHEDULED))
                            .fetchCount());
            stgVerBatch.setInProcess(
                    batchIDQuery(batchID)
                            .where(QQueueMessage.queueMessage.status.eq(QueueMessage.Status.IN_PROCESS))
                            .fetchCount());

            stgVerBatches.add(stgVerBatch);
        }

        return stgVerBatches;
    }

    private HibernateQuery<StorageVerificationTask> batchIDQuery(String batchID) {
        return new HibernateQuery<StorageVerificationTask>(em.unwrap(Session.class))
                .from(QStorageVerificationTask.storageVerificationTask)
                .leftJoin(QStorageVerificationTask.storageVerificationTask.queueMessage, QQueueMessage.queueMessage)
                .where(QQueueMessage.queueMessage.batchID.eq(batchID));
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
        List<javax.persistence.criteria.Predicate> predicates = matchTask.stgVerPredicates(
                queueMsg,
                stgVerTask,
                queueTaskQueryParam,
                stgVerTaskQueryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new javax.persistence.criteria.Predicate[0]));
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
