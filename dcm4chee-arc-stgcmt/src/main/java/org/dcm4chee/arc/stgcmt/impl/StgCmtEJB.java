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

import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
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
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.qmgt.IllegalTaskStateException;
import org.dcm4chee.arc.qmgt.QueueManager;
import org.dcm4chee.arc.qmgt.QueueSizeLimitExceededException;
import org.dcm4chee.arc.query.util.MatchTask;
import org.dcm4chee.arc.query.util.QueryBuilder;
import org.dcm4chee.arc.query.util.TaskQueryParam;
import org.dcm4chee.arc.stgcmt.StgVerBatch;
import org.dcm4chee.arc.stgcmt.StgCmtManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.persistence.*;
import javax.persistence.criteria.*;
import javax.persistence.metamodel.SingularAttribute;
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

    @Inject
    private QueueManager queueManager;

    public void addExternalRetrieveAETs(Attributes eventInfo, Device device) {
        String transactionUID = eventInfo.getString(Tag.TransactionUID);
        StgCmtResult result = getStgCmtResult(transactionUID);
        if (result == null)
            return;
        updateExternalRetrieveAETs(eventInfo, result.getStudyInstanceUID(),
                device.getDeviceExtension(ArchiveDeviceExtension.class).getExporterDescriptor(result.getExporterID()));
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
        if (ed == null)
            return;

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

    public List<StgCmtResult> listStgCmts(TaskQueryParam stgCmtResultQueryParam, int offset, int limit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<StgCmtResult> q = cb.createQuery(StgCmtResult.class);
        Root<StgCmtResult> stgCmtResult = q.from(StgCmtResult.class);
        List<Predicate> predicates = new MatchTask(cb).matchStgCmtResult(stgCmtResult, stgCmtResultQueryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));
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
        TaskQueryParam stgCmtResultQueryParam = new TaskQueryParam();
        stgCmtResultQueryParam.setStgCmtStatus(status);
        stgCmtResultQueryParam.setUpdatedBefore(updatedBefore);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        MatchTask matchTask = new MatchTask(cb);
        CriteriaDelete<StgCmtResult> q = cb.createCriteriaDelete(StgCmtResult.class);
        Root<StgCmtResult> stgCmtResult = q.from(StgCmtResult.class);
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
        Root<StorageVerificationTask> stgVerTask = q.from(StorageVerificationTask.class);
        From<StorageVerificationTask, QueueMessage> queueMsg = stgVerTask.join(StorageVerificationTask_.queueMessage);

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
        try (Stream<Long> resultStream = em.createQuery(q
                .where(predicates.toArray(new Predicate[0]))
                .select(stgVerTask.get(StorageVerificationTask_.pk)))
                .getResultStream()) {
            Optional<Long> prev = resultStream.findFirst();
            if (prev.isPresent()) {
                LOG.info("Previous {} found - suppress duplicate storage verification", prev.get());
                return true;
            }
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

    public int updateSeries(String studyIUID, String seriesIUID, int failures, long size) {
        return em.createNamedQuery(Series.UPDATE_STGVER_FAILURES)
                .setParameter(1, studyIUID)
                .setParameter(2, seriesIUID)
                .setParameter(3, failures)
                .setParameter(4, size)
                .executeUpdate();
    }

    public int updateStudySize(Long studyPk, long studySize) {
        return em.createNamedQuery(Study.SET_STUDY_SIZE)
                .setParameter(1, studyPk)
                .setParameter(2, studySize)
                .executeUpdate();
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

    public Tuple findDeviceNameAndMsgPropsByPk(Long pk) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> tupleQuery = cb.createTupleQuery();
        Root<StorageVerificationTask> stgVerTask = tupleQuery.from(StorageVerificationTask.class);
        Join<StorageVerificationTask, QueueMessage> queueMsg = stgVerTask.join(StorageVerificationTask_.queueMessage);
        tupleQuery.where(cb.equal(stgVerTask.get(StorageVerificationTask_.pk), pk));
        tupleQuery.multiselect(
                queueMsg.get(QueueMessage_.deviceName),
                queueMsg.get(QueueMessage_.messageProperties));
        return em.createQuery(tupleQuery).getSingleResult();
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
        return em.createQuery(
                select(QueueMessage_.deviceName, queueTaskQueryParam, stgVerTaskQueryParam).distinct(true))
                .getResultList();
    }

    public List<String> listStgVerTaskQueueMsgIDs(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam stgVerTaskQueryParam, int limit) {
        return em.createQuery(select(QueueMessage_.messageID, queueTaskQueryParam, stgVerTaskQueryParam))
                .setMaxResults(limit)
                .getResultList();
    }

    public List<Tuple> listStgVerTaskQueueMsgIDAndMsgProps(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam stgVerTaskQueryParam, int limit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        Root<StorageVerificationTask> stgVerTask = q.from(StorageVerificationTask.class);
        From<StorageVerificationTask, QueueMessage> queueMsg = stgVerTask.join(StorageVerificationTask_.queueMessage);
        List<Predicate> predicates = new MatchTask(cb).stgVerPredicates(
                queueMsg, stgVerTask, queueTaskQueryParam, stgVerTaskQueryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));
        return em.createQuery(
                q.multiselect(
                    queueMsg.get(QueueMessage_.messageID),
                    queueMsg.get(QueueMessage_.messageProperties)))
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
        ListStgVerBatches listStgVerBatches = new ListStgVerBatches(queueBatchQueryParam, stgVerBatchQueryParam);
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

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Iterator<StorageVerificationTask> listStgVerTasks(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam stgVerTaskQueryParam, int offset, int limit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<StorageVerificationTask> q = cb.createQuery(StorageVerificationTask.class);
        Root<StorageVerificationTask> stgVerTask = q.from(StorageVerificationTask.class);
        From<StorageVerificationTask, QueueMessage> queueMsg = stgVerTask.join(StorageVerificationTask_.queueMessage);
        MatchTask matchTask = new MatchTask(cb);
        List<Predicate> predicates = matchTask.stgVerPredicates(queueMsg, stgVerTask, queueTaskQueryParam, stgVerTaskQueryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));
        if (stgVerTaskQueryParam.getOrderBy() != null)
            q.orderBy(matchTask.stgVerTaskOrder(stgVerTaskQueryParam.getOrderBy(), stgVerTask));
        TypedQuery<StorageVerificationTask> query = em.createQuery(q);
        if (offset > 0)
            query.setFirstResult(offset);
        if (limit > 0)
            query.setMaxResults(limit);
        return query.getResultStream().iterator();
    }

    public long countTasks(TaskQueryParam queueTaskQueryParam, TaskQueryParam stgVerTaskQueryParam) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        Root<StorageVerificationTask> stgVerTask = q.from(StorageVerificationTask.class);
        From<StorageVerificationTask, QueueMessage> queueMsg = stgVerTask.join(StorageVerificationTask_.queueMessage);
        List<Predicate> predicates = new MatchTask(cb).stgVerPredicates(
                queueMsg, stgVerTask, queueTaskQueryParam, stgVerTaskQueryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));
        return QueryBuilder.unbox(em.createQuery(q.select(cb.count(stgVerTask))).getSingleResult(), 0L);
    }

    private Subquery<Long> statusSubquery(TaskQueryParam queueBatchQueryParam, TaskQueryParam stgVerBatchQueryParam,
                                          From<StorageVerificationTask, QueueMessage> queueMsg, QueueMessage.Status status) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<QueueMessage> query = cb.createQuery(QueueMessage.class);
        Subquery<Long> sq = query.subquery(Long.class);
        Root<StorageVerificationTask> stgVerTask = sq.from(StorageVerificationTask.class);
        Join<StorageVerificationTask, QueueMessage> queueMsg1 = sq.correlate(stgVerTask.join(StorageVerificationTask_.queueMessage));
        MatchTask matchTask = new MatchTask(cb);
        List<Predicate> predicates = matchTask.stgVerBatchPredicates(
                queueMsg1, stgVerTask, queueBatchQueryParam, stgVerBatchQueryParam);
        predicates.add(cb.equal(queueMsg1.get(QueueMessage_.batchID), queueMsg.get(QueueMessage_.batchID)));
        predicates.add(cb.equal(queueMsg1.get(QueueMessage_.status), status));
        sq.where(predicates.toArray(new Predicate[0]));
        sq.select(cb.count(stgVerTask));
        return sq;
    }

    private class ListStgVerBatches {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        final MatchTask matchTask = new MatchTask(cb);
        final CriteriaQuery<Tuple> query = cb.createTupleQuery();
        final Root<StorageVerificationTask> stgVerTask = query.from(StorageVerificationTask.class);
        final From<StorageVerificationTask, QueueMessage> queueMsg = stgVerTask.join(StorageVerificationTask_.queueMessage);
        final Expression<Date> minProcessingStartTime = cb.least(queueMsg.get(QueueMessage_.processingStartTime));
        final Expression<Date> maxProcessingStartTime = cb.greatest(queueMsg.get(QueueMessage_.processingStartTime));
        final Expression<Date> minProcessingEndTime = cb.least(queueMsg.get(QueueMessage_.processingEndTime));
        final Expression<Date> maxProcessingEndTime = cb.greatest(queueMsg.get(QueueMessage_.processingEndTime));
        final Expression<Date> minScheduledTime = cb.least(queueMsg.get(QueueMessage_.scheduledTime));
        final Expression<Date> maxScheduledTime = cb.greatest(queueMsg.get(QueueMessage_.scheduledTime));
        final Expression<Date> minCreatedTime = cb.least(stgVerTask.get(StorageVerificationTask_.createdTime));
        final Expression<Date> maxCreatedTime = cb.greatest(stgVerTask.get(StorageVerificationTask_.createdTime));
        final Expression<Date> minUpdatedTime = cb.least(stgVerTask.get(StorageVerificationTask_.updatedTime));
        final Expression<Date> maxUpdatedTime = cb.greatest(stgVerTask.get(StorageVerificationTask_.updatedTime));
        final Path<String> batchIDPath = queueMsg.get(QueueMessage_.batchID);
        final Expression<Long> completed;
        final Expression<Long> failed;
        final Expression<Long> warning;
        final Expression<Long> canceled;
        final Expression<Long> scheduled;
        final Expression<Long> inprocess;
        final TaskQueryParam queueBatchQueryParam;
        final TaskQueryParam stgVerBatchQueryParam;

        ListStgVerBatches(TaskQueryParam queueBatchQueryParam, TaskQueryParam stgVerBatchQueryParam) {
            this.queueBatchQueryParam = queueBatchQueryParam;
            this.stgVerBatchQueryParam = stgVerBatchQueryParam;
            this.completed = statusSubquery(queueBatchQueryParam, stgVerBatchQueryParam,
                    queueMsg, QueueMessage.Status.COMPLETED).getSelection();
            this.failed = statusSubquery(queueBatchQueryParam, stgVerBatchQueryParam,
                    queueMsg, QueueMessage.Status.FAILED).getSelection();
            this.warning = statusSubquery(queueBatchQueryParam, stgVerBatchQueryParam,
                    queueMsg, QueueMessage.Status.WARNING).getSelection();
            this.canceled = statusSubquery(queueBatchQueryParam, stgVerBatchQueryParam,
                    queueMsg, QueueMessage.Status.CANCELED).getSelection();
            this.scheduled = statusSubquery(queueBatchQueryParam, stgVerBatchQueryParam,
                    queueMsg, QueueMessage.Status.SCHEDULED).getSelection();
            this.inprocess = statusSubquery(queueBatchQueryParam, stgVerBatchQueryParam,
                    queueMsg, QueueMessage.Status.IN_PROCESS).getSelection();
            List<Predicate> predicates = matchTask.stgVerBatchPredicates(
                    queueMsg, stgVerTask, queueBatchQueryParam, stgVerBatchQueryParam);
            query.multiselect(batchIDPath,
                    minProcessingStartTime, maxProcessingStartTime,
                    minProcessingEndTime, maxProcessingEndTime,
                    minScheduledTime, maxScheduledTime,
                    minCreatedTime, maxCreatedTime,
                    minUpdatedTime, maxUpdatedTime,
                    completed, failed, warning, canceled, scheduled, inprocess);
            query.groupBy(queueMsg.get(QueueMessage_.batchID));
            if (!predicates.isEmpty())
                query.where(predicates.toArray(new Predicate[0]));
            if (stgVerBatchQueryParam.getOrderBy() != null)
                query.orderBy(matchTask.stgVerBatchOrder(stgVerBatchQueryParam.getOrderBy(), stgVerTask));
        }

        StgVerBatch toStgVerBatch(Tuple tuple) {
            String batchID = tuple.get(batchIDPath);
            StgVerBatch stgVerBatch = new StgVerBatch(batchID);
            stgVerBatch.setProcessingStartTimeRange(
                    tuple.get(maxProcessingStartTime),
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
            Root<StorageVerificationTask> stgVerTask = distinct.from(StorageVerificationTask.class);
            From<StorageVerificationTask, QueueMessage> queueMsg = stgVerTask.join(StorageVerificationTask_.queueMessage);
            distinct.where(predicates(queueMsg, stgVerTask, batchID));
            stgVerBatch.setDeviceNames(select(distinct, queueMsg.get(QueueMessage_.deviceName)));
            stgVerBatch.setLocalAETs(select(distinct, stgVerTask.get(StorageVerificationTask_.localAET)));
            stgVerBatch.setCompleted(tuple.get(completed));
            stgVerBatch.setCanceled(tuple.get(canceled));
            stgVerBatch.setWarning(tuple.get(warning));
            stgVerBatch.setFailed(tuple.get(failed));
            stgVerBatch.setScheduled(tuple.get(scheduled));
            stgVerBatch.setInProcess(tuple.get(inprocess));
            return stgVerBatch;
        }

        private Predicate[] predicates(Path<QueueMessage> queueMsg, Path<StorageVerificationTask> stgVerTask, String batchID) {
            List<Predicate> predicates = matchTask.stgVerBatchPredicates(
                    queueMsg, stgVerTask, queueBatchQueryParam, stgVerBatchQueryParam);
            predicates.add(cb.equal(queueMsg.get(QueueMessage_.batchID), batchID));
            return predicates.toArray(new Predicate[0]);
        }

        private List<String> select(CriteriaQuery<String> query, Path<String> path) {
            return em.createQuery(query.select(path)).getResultList();
        }
    }

    private CriteriaQuery<String> select(SingularAttribute<QueueMessage, String> attribute,
                                         TaskQueryParam queueTaskQueryParam, TaskQueryParam stgVerTaskQueryParam) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<String> q = cb.createQuery(String.class);
        Root<StorageVerificationTask> stgVerTask = q.from(StorageVerificationTask.class);
        From<StorageVerificationTask, QueueMessage> queueMsg = stgVerTask.join(StorageVerificationTask_.queueMessage);
        List<Predicate> predicates = new MatchTask(cb).stgVerPredicates(
                queueMsg, stgVerTask, queueTaskQueryParam, stgVerTaskQueryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));
        return q.select(queueMsg.get(attribute));
    }

    public int deleteTasks(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam stgVerTaskQueryParam, int deleteTasksFetchSize) {
        List<String> referencedQueueMsgIDs = em.createQuery(
                select(QueueMessage_.messageID, queueTaskQueryParam, stgVerTaskQueryParam))
                .setMaxResults(deleteTasksFetchSize)
                .getResultList();

        referencedQueueMsgIDs.forEach(queueMsgID -> queueManager.deleteTask(queueMsgID, null));
        return referencedQueueMsgIDs.size();
    }
}
