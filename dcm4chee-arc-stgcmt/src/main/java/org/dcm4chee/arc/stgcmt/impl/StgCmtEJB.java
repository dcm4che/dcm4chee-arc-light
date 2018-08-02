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
 * Portions created by the Initial Developer are Copyright (C) 2016
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

import com.mysema.commons.lang.CloseableIterator;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.jpa.hibernate.HibernateDeleteClause;
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
import org.dcm4chee.arc.stgcmt.StgCmtBatch;
import org.dcm4chee.arc.stgcmt.StgCmtManager;
import org.dcm4chee.arc.stgcmt.StgCmtTaskQuery;
import org.hibernate.Session;
import org.hibernate.StatelessSession;
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
import java.util.*;

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

    private static final Expression<?>[] SELECT = {
            QQueueMessage.queueMessage.processingStartTime.min(),
            QQueueMessage.queueMessage.processingStartTime.max(),
            QQueueMessage.queueMessage.processingEndTime.min(),
            QQueueMessage.queueMessage.processingEndTime.max(),
            QQueueMessage.queueMessage.scheduledTime.min(),
            QQueueMessage.queueMessage.scheduledTime.max(),
            QStgCmtTask.stgCmtTask.createdTime.min(),
            QStgCmtTask.stgCmtTask.createdTime.max(),
            QStgCmtTask.stgCmtTask.updatedTime.min(),
            QStgCmtTask.stgCmtTask.updatedTime.max(),
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

    public int setDigest(Long pk, String digest) {
        return em.createNamedQuery(Location.SET_DIGEST)
                .setParameter(1, pk)
                .setParameter(2, digest)
                .executeUpdate();
    }

    public int setStatus(Long pk, Location.Status status) {
        return em.createNamedQuery(Location.SET_STATUS)
                .setParameter(1, pk)
                .setParameter(2, status)
                .executeUpdate();
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

    public List<StgCmtResult> listStgCmts(
            StgCmtResult.Status status, String studyUID, String exporterID, int offset, int limit) {
        HibernateQuery<StgCmtResult> query = getStgCmtResults(status, studyUID, exporterID);
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

    private HibernateQuery<StgCmtResult> getStgCmtResults(StgCmtResult.Status status, String studyUID, String exporterId) {
        Predicate predicate = getPredicates(status, studyUID, exporterId);
        HibernateQuery<StgCmtResult> query = new HibernateQuery<Void>(em.unwrap(Session.class))
                .select(QStgCmtResult.stgCmtResult).from(QStgCmtResult.stgCmtResult);
        return query.where(predicate);
    }

    private Predicate getPredicates(StgCmtResult.Status status, String studyUID, String exporterId) {
        BooleanBuilder predicate = new BooleanBuilder();
        if (status != null)
            predicate.and(QStgCmtResult.stgCmtResult.status.eq(status));
        if (studyUID != null)
            predicate.and(QStgCmtResult.stgCmtResult.studyInstanceUID.eq(studyUID));
        if (exporterId != null)
            predicate.and(QStgCmtResult.stgCmtResult.exporterID.eq(exporterId.toUpperCase()));
        return predicate;
    }

    public boolean scheduleStgCmtTask(StgCmtTask stgCmtTask, HttpServletRequestInfo httpServletRequestInfo,
                                   String batchID) throws QueueSizeLimitExceededException {
        if (isAlreadyScheduled(stgCmtTask))
            return false;

        try {
            ObjectMessage msg = queueManager.createObjectMessage(0);
            msg.setStringProperty("LocalAET", stgCmtTask.getLocalAET());
            msg.setStringProperty("StudyInstanceUID", stgCmtTask.getStudyInstanceUID());
            if (stgCmtTask.getSeriesInstanceUID() != null) {
                msg.setStringProperty("SeriesInstanceUID", stgCmtTask.getSeriesInstanceUID());
                if (stgCmtTask.getSOPInstanceUID() != null) {
                    msg.setStringProperty("SOPInstanceUID", stgCmtTask.getSOPInstanceUID());
                }
            }
            httpServletRequestInfo.copyTo(msg);
            QueueMessage queueMessage = queueManager.scheduleMessage(StgCmtManager.QUEUE_NAME, msg,
                    Message.DEFAULT_PRIORITY, batchID);
            stgCmtTask.setQueueMessage(queueMessage);
            em.persist(stgCmtTask);
        } catch (JMSException e) {
            throw QueueMessage.toJMSRuntimeException(e);
        }
        return true;
    }

    private boolean isAlreadyScheduled(StgCmtTask stgCmtTask) {
        BooleanBuilder predicate = new BooleanBuilder(QStgCmtTask.stgCmtTask.queueMessage.status.in(
                QueueMessage.Status.SCHEDULED, QueueMessage.Status.IN_PROCESS));
        predicate.and(QStgCmtTask.stgCmtTask.studyInstanceUID.eq(stgCmtTask.getStudyInstanceUID()));
        if (stgCmtTask.getSeriesInstanceUID() == null) {
            predicate.and(QStgCmtTask.stgCmtTask.seriesInstanceUID.isNull());
        } else {
            predicate.and(ExpressionUtils.or(
                    QStgCmtTask.stgCmtTask.seriesInstanceUID.isNull(),
                    QStgCmtTask.stgCmtTask.seriesInstanceUID.eq(stgCmtTask.getSeriesInstanceUID())));
            if (stgCmtTask.getSOPInstanceUID() == null) {
                predicate.and(QStgCmtTask.stgCmtTask.sopInstanceUID.isNull());
            } else {
                predicate.and(ExpressionUtils.or(
                    QStgCmtTask.stgCmtTask.sopInstanceUID.isNull(),
                    QStgCmtTask.stgCmtTask.sopInstanceUID.eq(stgCmtTask.getSOPInstanceUID())));
            }
        }
        if (stgCmtTask.getStgCmtPolicy() != null) {
            predicate.and(QStgCmtTask.stgCmtTask.stgCmtPolicy.eq(stgCmtTask.getStgCmtPolicy()));
        }
        if (stgCmtTask.getStorageIDsAsString() != null) {
            predicate.and(QStgCmtTask.stgCmtTask.storageIDs.eq(stgCmtTask.getStorageIDsAsString()));
        }
        try (CloseableIterator<Long> iterate = new HibernateQuery<>(em.unwrap(Session.class))
                .select(QStgCmtTask.stgCmtTask.pk)
                .from(QStgCmtTask.stgCmtTask)
                .where(predicate)
                .iterate()) {
            return iterate.hasNext();
        }
    }

    public int updateStgCmtTask(StgCmtTask stgCmtTask) {
        return em.createNamedQuery(StgCmtTask.UPDATE_RESULT_BY_PK)
                .setParameter(1, stgCmtTask.getPk())
                .setParameter(2, stgCmtTask.getCompleted())
                .setParameter(3, stgCmtTask.getFailed())
                .executeUpdate();
    }

    public int updateSeries(String studyIUID, String seriesIUID, int failures) {
        return em.createNamedQuery(Series.UPDATE_STGCMT_FAILURES)
                .setParameter(1, studyIUID)
                .setParameter(2, seriesIUID)
                .setParameter(3, failures)
                .executeUpdate();
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public StgCmtTaskQuery listStgCmtTasks(Predicate matchQueueMessage, Predicate matchStgCmtTask,
                                             OrderSpecifier<Date> order, int offset, int limit) {
        return new StgCmtTaskQueryImpl(
                openStatelessSession(), queryFetchSize(), matchQueueMessage, matchStgCmtTask, order, offset, limit);
    }

    private StatelessSession openStatelessSession() {
        return em.unwrap(Session.class).getSessionFactory().openStatelessSession();
    }

    private int queryFetchSize() {
        return device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class).getQueryFetchSize();
    }

    public long countStgCmtTasks(Predicate matchQueueMessage, Predicate matchStgCmtTask) {
        return createQuery(matchQueueMessage, matchStgCmtTask)
                .fetchCount();
    }

    private HibernateQuery<StgCmtTask> createQuery(Predicate matchQueueMessage, Predicate matchStgCmtTask) {
        HibernateQuery<QueueMessage> queueMsgQuery = new HibernateQuery<QueueMessage>(em.unwrap(Session.class))
                .from(QQueueMessage.queueMessage)
                .where(matchQueueMessage);
        return new HibernateQuery<StgCmtTask>(em.unwrap(Session.class))
                .from(QStgCmtTask.stgCmtTask)
                .where(matchStgCmtTask, QStgCmtTask.stgCmtTask.queueMessage.in(queueMsgQuery));
    }

    public boolean cancelStgCmtTask(Long pk, QueueMessageEvent queueEvent) throws IllegalTaskStateException {
        StgCmtTask task = em.find(StgCmtTask.class, pk);
        if (task == null)
            return false;

        QueueMessage queueMessage = task.getQueueMessage();
        if (queueMessage == null)
            throw new IllegalTaskStateException("Cannot cancel Task with status: 'TO SCHEDULE'");

        queueManager.cancelTask(queueMessage.getMessageID(), queueEvent);
        LOG.info("Cancel {}", task);
        return true;
    }

    public long cancelStgCmtTasks(Predicate matchQueueMessage, Predicate matchStgCmtTask, QueueMessage.Status prev)
            throws IllegalTaskStateException {
        return queueManager.cancelStgCmtTasks(matchQueueMessage, matchStgCmtTask, prev);
    }

    public String findDeviceNameByPk(Long pk) {
        try {
            return em.createNamedQuery(StgCmtTask.FIND_DEVICE_BY_PK, String.class)
                    .setParameter(1, pk)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    public void rescheduleStgCmtTask(Long pk, QueueMessageEvent queueEvent) {
        StgCmtTask task = em.find(StgCmtTask.class, pk);
        if (task == null)
            return;

        LOG.info("Reschedule {}", task);
        rescheduleStgCmtTask(task.getQueueMessage().getMessageID(), queueEvent);
    }

    public void rescheduleStgCmtTask(String stgCmtTaskQueueMsgId, QueueMessageEvent queueEvent) {
        queueManager.rescheduleTask(stgCmtTaskQueueMsgId, StgCmtManager.QUEUE_NAME, queueEvent);
    }

    public List<String> listDistinctDeviceNames(Predicate matchQueueMessage, Predicate matchStgCmtTask) {
        return createQuery(matchQueueMessage, matchStgCmtTask)
                .select(QQueueMessage.queueMessage.deviceName)
                .distinct()
                .fetch();
    }

    public List<String> listStgCmtTaskQueueMsgIDs(Predicate matchQueueMsg, Predicate matchStgCmtTask, int limit) {
        return createQuery(matchQueueMsg, matchStgCmtTask)
                .select(QQueueMessage.queueMessage.messageID)
                .limit(limit)
                .fetch();
    }

    public boolean deleteStgCmtTask(Long pk, QueueMessageEvent queueEvent) {
        StgCmtTask task = em.find(StgCmtTask.class, pk);
        if (task == null)
            return false;

        queueEvent.setQueueMsg(task.getQueueMessage());
        em.remove(task);
        LOG.info("Delete {}", task);
        return true;
    }

    public int deleteTasks(Predicate matchQueueMessage, Predicate matchStgCmtTask, int deleteTasksFetchSize) {
        List<Long> referencedQueueMsgs = createQuery(matchQueueMessage, matchStgCmtTask)
                .select(QStgCmtTask.stgCmtTask.queueMessage.pk)
                .limit(deleteTasksFetchSize)
                .fetch();

        new HibernateDeleteClause(em.unwrap(Session.class), QStgCmtTask.stgCmtTask)
                .where(matchStgCmtTask, QStgCmtTask.stgCmtTask.queueMessage.pk.in(referencedQueueMsgs))
                .execute();
        return (int) new HibernateDeleteClause(em.unwrap(Session.class), QQueueMessage.queueMessage)
                .where(matchQueueMessage, QQueueMessage.queueMessage.pk.in(referencedQueueMsgs)).execute();
    }

    public List<StgCmtBatch> listStgCmtBatches(Predicate matchQueueBatch, Predicate matchStgCmtBatch,
                                               OrderSpecifier<Date> order, int offset, int limit) {
        HibernateQuery<StgCmtTask> stgCmtTaskQuery = createQuery(matchQueueBatch, matchStgCmtBatch);
        if (limit > 0)
            stgCmtTaskQuery.limit(limit);
        if (offset > 0)
            stgCmtTaskQuery.offset(offset);

        List<Tuple> batches = stgCmtTaskQuery.select(SELECT)
                .groupBy(QQueueMessage.queueMessage.batchID)
                .orderBy(order)
                .fetch();

        List<StgCmtBatch> stgCmtBatches = new ArrayList<>();
        for (Tuple batch : batches) {
            StgCmtBatch stgCmtBatch = new StgCmtBatch();
            String batchID = batch.get(QQueueMessage.queueMessage.batchID);
            stgCmtBatch.setBatchID(batchID);

            stgCmtBatch.setCreatedTimeRange(
                    batch.get(QStgCmtTask.stgCmtTask.createdTime.min()),
                    batch.get(QStgCmtTask.stgCmtTask.createdTime.max()));
            stgCmtBatch.setUpdatedTimeRange(
                    batch.get(QStgCmtTask.stgCmtTask.updatedTime.min()),
                    batch.get(QStgCmtTask.stgCmtTask.updatedTime.max()));
            stgCmtBatch.setScheduledTimeRange(
                    batch.get(QQueueMessage.queueMessage.scheduledTime.min()),
                    batch.get(QQueueMessage.queueMessage.scheduledTime.max()));
            stgCmtBatch.setProcessingStartTimeRange(
                    batch.get(QQueueMessage.queueMessage.processingStartTime.min()),
                    batch.get(QQueueMessage.queueMessage.processingStartTime.max()));
            stgCmtBatch.setProcessingEndTimeRange(
                    batch.get(QQueueMessage.queueMessage.processingEndTime.min()),
                    batch.get(QQueueMessage.queueMessage.processingEndTime.max()));

            stgCmtBatch.setDeviceNames(
                    batchIDQuery(batchID)
                            .select(QQueueMessage.queueMessage.deviceName)
                            .distinct()
                            .orderBy(QQueueMessage.queueMessage.deviceName.asc())
                            .fetch());
            stgCmtBatch.setLocalAETs(
                    batchIDQuery(batchID)
                            .select(QStgCmtTask.stgCmtTask.localAET)
                            .distinct()
                            .orderBy(QStgCmtTask.stgCmtTask.localAET.asc())
                            .fetch());

            stgCmtBatch.setCompleted(
                    batchIDQuery(batchID)
                            .where(QQueueMessage.queueMessage.status.eq(QueueMessage.Status.COMPLETED))
                            .fetchCount());
            stgCmtBatch.setCanceled(
                    batchIDQuery(batchID)
                            .where(QQueueMessage.queueMessage.status.eq(QueueMessage.Status.CANCELED))
                            .fetchCount());
            stgCmtBatch.setWarning(
                    batchIDQuery(batchID)
                            .where(QQueueMessage.queueMessage.status.eq(QueueMessage.Status.WARNING))
                            .fetchCount());
            stgCmtBatch.setFailed(
                    batchIDQuery(batchID)
                            .where(QQueueMessage.queueMessage.status.eq(QueueMessage.Status.FAILED))
                            .fetchCount());
            stgCmtBatch.setScheduled(
                    batchIDQuery(batchID)
                            .where(QQueueMessage.queueMessage.status.eq(QueueMessage.Status.SCHEDULED))
                            .fetchCount());
            stgCmtBatch.setInProcess(
                    batchIDQuery(batchID)
                            .where(QQueueMessage.queueMessage.status.eq(QueueMessage.Status.IN_PROCESS))
                            .fetchCount());

            stgCmtBatches.add(stgCmtBatch);
        }

        return stgCmtBatches;
    }

    private HibernateQuery<StgCmtTask> batchIDQuery(String batchID) {
        return new HibernateQuery<StgCmtTask>(em.unwrap(Session.class))
                .from(QStgCmtTask.stgCmtTask)
                .leftJoin(QStgCmtTask.stgCmtTask.queueMessage, QQueueMessage.queueMessage)
                .where(QQueueMessage.queueMessage.batchID.eq(batchID));
    }
}
