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
 */

package org.dcm4chee.arc.query.util;

import com.querydsl.core.BooleanBuilder;
import javax.persistence.criteria.Predicate;
import org.dcm4chee.arc.entity.*;

import javax.persistence.criteria.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jan 2018
 */
public class MatchTask {

    private final CriteriaBuilder cb;

    public MatchTask(CriteriaBuilder cb) {
        this.cb = Objects.requireNonNull(cb);
    }

    public static com.querydsl.core.types.Predicate matchQueueMessage(
            String queueName, String deviceName, QueueMessage.Status status, String batchID, String jmsMessageID,
            String createdTime, String updatedTime, Date updatedBefore) {
        BooleanBuilder predicate = new BooleanBuilder();
        if (queueName != null)
            predicate.and(QQueueMessage.queueMessage.queueName.eq(queueName));
        if (status != null && status != QueueMessage.Status.TO_SCHEDULE)
            predicate.and(QQueueMessage.queueMessage.status.eq(status));
        if (deviceName != null)
            predicate.and(QQueueMessage.queueMessage.deviceName.eq(deviceName));
        if (batchID != null)
            predicate.and(QQueueMessage.queueMessage.batchID.eq(batchID));
        if (jmsMessageID != null)
            predicate.and(QQueueMessage.queueMessage.messageID.eq(jmsMessageID));
        if (createdTime != null)
            predicate.and(MatchDateTimeRange.range(
                    QQueueMessage.queueMessage.createdTime, createdTime, MatchDateTimeRange.FormatDate.DT));
        if (updatedTime != null)
            predicate.and(MatchDateTimeRange.range(
                    QQueueMessage.queueMessage.updatedTime, updatedTime, MatchDateTimeRange.FormatDate.DT));
        if (updatedBefore != null)
            predicate.and(QQueueMessage.queueMessage.updatedTime.before(updatedBefore));
        return predicate;
    }

    public List<Predicate> queueMsgPredicates(
            Root<QueueMessage> queueMsg, TaskQueryParam queueTaskQueryParam) {
        List<Predicate> predicates = new ArrayList<>();
        matchQueueMsg(predicates, queueTaskQueryParam, queueMsg);
        return predicates;
    }

    public List<Predicate> exportPredicates(
            From<ExportTask, QueueMessage> queueMsg, Root<ExportTask> exportTask,
            TaskQueryParam queueTaskQueryParam, TaskQueryParam exportTaskQueryParam) {
        List<Predicate> predicates = new ArrayList<>();
        matchQueueMsg(predicates, queueTaskQueryParam, queueMsg);
        matchExportTask(predicates, exportTaskQueryParam, exportTask);
        return predicates;
    }

    public List<Predicate> retrievePredicates(
            From<RetrieveTask, QueueMessage> queueMsg, Root<RetrieveTask> retrieveTask,
            TaskQueryParam queueTaskQueryParam, TaskQueryParam retrieveTaskQueryParam) {
        List<Predicate> predicates = new ArrayList<>();
        matchQueueMsg(predicates, queueTaskQueryParam, queueMsg);
        matchRetrieveTask(predicates, retrieveTaskQueryParam, retrieveTask);
        return predicates;
    }

    public List<Predicate> exportBatchPredicates(
            From<ExportTask, QueueMessage> queueMsg, Root<ExportTask> exportTask,
            TaskQueryParam queueBatchQueryParam, TaskQueryParam exportBatchQueryParam) {
        List<Predicate> predicates = new ArrayList<>();
        matchQueueBatch(predicates, queueBatchQueryParam, queueMsg);
        matchExportBatch(predicates, exportBatchQueryParam, exportTask);
        return predicates;
    }

    public List<Predicate> retrieveBatchPredicates(
            From<RetrieveTask, QueueMessage> queueMsg, Root<RetrieveTask> retrieveTask,
            TaskQueryParam queueBatchQueryParam, TaskQueryParam retrieveBatchQueryParam) {
        List<Predicate> predicates = new ArrayList<>();
        matchQueueBatch(predicates, queueBatchQueryParam, queueMsg);
        matchRetrieveBatch(predicates, retrieveBatchQueryParam, retrieveTask);
        return predicates;
    }

    public List<Predicate> stgVerBatchPredicates(
            From<StorageVerificationTask, QueueMessage> queueMsg, Root<StorageVerificationTask> stgVerTask,
            TaskQueryParam queueBatchQueryParam, TaskQueryParam stgVerBatchQueryParam) {
        List<Predicate> predicates = new ArrayList<>();
        matchQueueBatch(predicates, queueBatchQueryParam, queueMsg);
        matchStgVerBatch(predicates, stgVerBatchQueryParam, stgVerTask);
        return predicates;
    }

    public List<Predicate> diffBatchPredicates(
            From<DiffTask, QueueMessage> queueMsg, Root<DiffTask> diffTask,
            TaskQueryParam queueBatchQueryParam, TaskQueryParam diffBatchQueryParam) {
        List<Predicate> predicates = new ArrayList<>();
        matchQueueBatch(predicates, queueBatchQueryParam, queueMsg);
        matchDiffBatch(predicates, diffBatchQueryParam, diffTask);
        return predicates;
    }

    public List<Predicate> diffPredicates(
            From<DiffTask, QueueMessage> queueMsg, Root<DiffTask> diffTask,
            TaskQueryParam queueTaskQueryParam, TaskQueryParam diffTaskQueryParam) {
        List<Predicate> predicates = new ArrayList<>();
        matchQueueMsg(predicates, queueTaskQueryParam, queueMsg);
        matchDiffTask(predicates, diffTaskQueryParam, diffTask);
        return predicates;
    }

    public List<Predicate> stgVerPredicates(
            From<StorageVerificationTask, QueueMessage> queueMsg, Root<StorageVerificationTask> stgVerTask,
            TaskQueryParam queueTaskQueryParam, TaskQueryParam stgVerTaskQueryParam) {
        List<Predicate> predicates = new ArrayList<>();
        matchQueueMsg(predicates, queueTaskQueryParam, queueMsg);
        matchStgVerTask(predicates, stgVerTaskQueryParam, stgVerTask);
        return predicates;
    }

    private <Z> void matchQueueMsg(List<Predicate> predicates,
                                   TaskQueryParam taskQueryParam, From<Z, QueueMessage> queueMsg) {
        if (taskQueryParam == null || queueMsg == null)
            return;

        if (taskQueryParam.getQueueName() != null)
            predicates.add(cb.equal(queueMsg.get(QueueMessage_.queueName), taskQueryParam.getQueueName()));
        QueueMessage.Status status = taskQueryParam.getStatus();
        if (status != null && status != QueueMessage.Status.TO_SCHEDULE)
            predicates.add(cb.equal(queueMsg.get(QueueMessage_.status), status));
        if (taskQueryParam.getDeviceName() != null)
            predicates.add(cb.equal(queueMsg.get(QueueMessage_.deviceName), taskQueryParam.getDeviceName()));
        if (taskQueryParam.getBatchID() != null)
            predicates.add(cb.equal(queueMsg.get(QueueMessage_.batchID), taskQueryParam.getBatchID()));
        if (taskQueryParam.getJmsMessageID() != null)
            predicates.add(cb.equal(queueMsg.get(QueueMessage_.messageID), taskQueryParam.getJmsMessageID()));
        if (taskQueryParam.getCreatedTime() != null)
            predicates.add(MatchDateTimeRange.range(cb, queueMsg.get(QueueMessage_.createdTime), taskQueryParam.getCreatedTime()));
        if (taskQueryParam.getUpdatedTime() != null)
            predicates.add(MatchDateTimeRange.range(cb, queueMsg.get(QueueMessage_.updatedTime), taskQueryParam.getUpdatedTime()));
        if (taskQueryParam.getUpdatedBefore() != null)
            predicates.add(cb.lessThan(queueMsg.get(QueueMessage_.updatedTime), taskQueryParam.getUpdatedBefore()));
    }

    private void matchExportTask(
            List<Predicate> predicates, TaskQueryParam taskQueryParam,
            Root<ExportTask> exportTask) {
        if (!taskQueryParam.getExporterIDs().isEmpty())
            predicates.add(cb.and(exportTask.get(ExportTask_.exporterID).in(taskQueryParam.getExporterIDs())));
        if (taskQueryParam.getDeviceName() != null)
            predicates.add(cb.equal(exportTask.get(ExportTask_.deviceName), taskQueryParam.getDeviceName()));
        if (taskQueryParam.getStudyIUID() != null)
            predicates.add(cb.equal(exportTask.get(ExportTask_.studyInstanceUID), taskQueryParam.getStudyIUID()));
        if (taskQueryParam.getCreatedTime() != null)
            predicates.add(MatchDateTimeRange.range(
                    cb, exportTask.get(ExportTask_.createdTime), taskQueryParam.getCreatedTime()));
        if (taskQueryParam.getUpdatedTime() != null)
            predicates.add(MatchDateTimeRange.range(
                    cb, exportTask.get(ExportTask_.updatedTime), taskQueryParam.getUpdatedTime()));
    }

    private void matchRetrieveTask(List<Predicate> predicates,
                                   TaskQueryParam taskQueryParam,
                                   Path<RetrieveTask> retrieveTask) {
        if (taskQueryParam.getLocalAET() != null)
            predicates.add(cb.equal(retrieveTask.get(RetrieveTask_.localAET), taskQueryParam.getLocalAET()));
        if (taskQueryParam.getRemoteAET() != null)
            predicates.add(cb.equal(retrieveTask.get(RetrieveTask_.remoteAET), taskQueryParam.getRemoteAET()));
        if (taskQueryParam.getDestinationAET() != null)
            predicates.add(cb.equal(retrieveTask.get(RetrieveTask_.destinationAET), taskQueryParam.getDestinationAET()));
        if (taskQueryParam.getStudyIUID() != null)
            predicates.add(cb.equal(retrieveTask.get(RetrieveTask_.studyInstanceUID), taskQueryParam.getStudyIUID()));
        if (taskQueryParam.getCreatedTime() != null)
            predicates.add(MatchDateTimeRange.range(
                    cb, retrieveTask.get(RetrieveTask_.createdTime), taskQueryParam.getCreatedTime()));
        if (taskQueryParam.getUpdatedTime() != null)
            predicates.add(MatchDateTimeRange.range(
                    cb, retrieveTask.get(RetrieveTask_.updatedTime), taskQueryParam.getUpdatedTime()));
    }

    private void matchDiffTask(
            List<Predicate> predicates, TaskQueryParam taskQueryParam, Path<DiffTask> diffTask) {
        if (taskQueryParam.getLocalAET() != null)
            predicates.add(cb.equal(diffTask.get(DiffTask_.localAET), taskQueryParam.getLocalAET()));
        if (taskQueryParam.getPrimaryAET() != null)
            predicates.add(cb.equal(diffTask.get(DiffTask_.primaryAET), taskQueryParam.getPrimaryAET()));
        if (taskQueryParam.getSecondaryAET() != null)
            predicates.add(cb.equal(diffTask.get(DiffTask_.secondaryAET), taskQueryParam.getSecondaryAET()));
        if (taskQueryParam.getCompareFields() != null)
            predicates.add(cb.equal(diffTask.get(DiffTask_.compareFields), taskQueryParam.getCompareFields()));
        if (taskQueryParam.getCheckMissing() != null)
            predicates.add(cb.equal(diffTask.get(DiffTask_.checkMissing), Boolean.parseBoolean(taskQueryParam.getCheckMissing())));
        if (taskQueryParam.getCheckDifferent() != null)
            predicates.add(cb.equal(diffTask.get(DiffTask_.checkDifferent), Boolean.parseBoolean(taskQueryParam.getCheckDifferent())));
        if (taskQueryParam.getCreatedTime() != null)
            predicates.add(MatchDateTimeRange.range(
                    cb, diffTask.get(DiffTask_.createdTime), taskQueryParam.getCreatedTime()));
        if (taskQueryParam.getUpdatedTime() != null)
            predicates.add(MatchDateTimeRange.range(
                    cb, diffTask.get(DiffTask_.updatedTime), taskQueryParam.getUpdatedTime()));
    }

    private void matchStgVerTask(
            List<Predicate> predicates, TaskQueryParam taskQueryParam,
            Root<StorageVerificationTask> stgVerTask) {
        if (taskQueryParam.getLocalAET() != null)
            predicates.add(cb.equal(stgVerTask.get(StorageVerificationTask_.localAET), taskQueryParam.getLocalAET()));
        if (taskQueryParam.getStudyIUID() != null)
            predicates.add(cb.equal(stgVerTask.get(StorageVerificationTask_.studyInstanceUID), taskQueryParam.getStudyIUID()));
        if (taskQueryParam.getCreatedTime() != null)
            predicates.add(MatchDateTimeRange.range(
                    cb, stgVerTask.get(StorageVerificationTask_.createdTime), taskQueryParam.getCreatedTime()));
        if (taskQueryParam.getUpdatedTime() != null)
            predicates.add(MatchDateTimeRange.range(
                    cb, stgVerTask.get(StorageVerificationTask_.updatedTime), taskQueryParam.getUpdatedTime()));
    }

    public static com.querydsl.core.types.Predicate matchDiffTask(
            String localAET, String primaryAET, String secondaryAET, String checkDifferent,
            String checkMissing, String comparefields, String createdTime, String updatedTime) {
        BooleanBuilder predicate = new BooleanBuilder();
        if (localAET != null)
            predicate.and(QDiffTask.diffTask.localAET.eq(localAET));
        if (primaryAET != null)
            predicate.and(QDiffTask.diffTask.primaryAET.eq(primaryAET));
        if (secondaryAET != null)
            predicate.and(QDiffTask.diffTask.secondaryAET.eq(secondaryAET));
        if (checkDifferent != null)
            predicate.and(QDiffTask.diffTask.checkDifferent.eq(Boolean.parseBoolean(checkDifferent)));
        if (checkMissing != null)
            predicate.and(QDiffTask.diffTask.checkMissing.eq(Boolean.parseBoolean(checkMissing)));
        if (comparefields != null)
            predicate.and(QDiffTask.diffTask.compareFields.eq(comparefields));
        if (createdTime != null)
            predicate.and(MatchDateTimeRange.range(
                    QDiffTask.diffTask.createdTime, createdTime, MatchDateTimeRange.FormatDate.DT));
        if (updatedTime != null)
            predicate.and(MatchDateTimeRange.range(
                    QDiffTask.diffTask.updatedTime, updatedTime, MatchDateTimeRange.FormatDate.DT));
        return predicate;
    }

    public Order exportTaskOrder(String orderby, Path<ExportTask> exportTask) {
        return taskOrder(orderby,
                exportTask.get(ExportTask_.createdTime),
                exportTask.get(ExportTask_.updatedTime));
    }

    public Order retrieveTaskOrder(String orderby, Path<RetrieveTask> retrieveTask) {
        return taskOrder(orderby,
                retrieveTask.get(RetrieveTask_.createdTime),
                retrieveTask.get(RetrieveTask_.updatedTime));
    }

    public Order stgVerTaskOrder(String orderby, Path<StorageVerificationTask> stgVerTask) {
        return taskOrder(orderby,
                stgVerTask.get(StorageVerificationTask_.createdTime),
                stgVerTask.get(StorageVerificationTask_.updatedTime));
    }

    public Order diffTaskOrder(String orderby, Path<DiffTask> diffTask) {
        return taskOrder(orderby,
                diffTask.get(DiffTask_.createdTime),
                diffTask.get(DiffTask_.updatedTime));
    }

    public Order queueMessageOrder(String orderBy, Path<QueueMessage> queueMsg) {
        return taskOrder(orderBy,
                queueMsg.get(QueueMessage_.createdTime),
                queueMsg.get(QueueMessage_.updatedTime));
    }

    private Order taskOrder(
            String orderby, Path<Date> createdTime, Path<Date> updatedTime) {
        return order(orderby, createdTime, updatedTime);
    }

    private Order order(String orderby, Path<Date> createdTime, Path<Date> updatedTime) {
        switch (orderby) {
            case "createdTime":
                return cb.asc(createdTime);
            case "updatedTime":
                return cb.asc(updatedTime);
            case "-createdTime":
                return cb.desc(createdTime);
            case "-updatedTime":
                return cb.desc(updatedTime);
        }

        throw new IllegalArgumentException(orderby);
    }

    public List<Predicate> matchStgCmtResult(
            Root<StgCmtResult> stgCmtResult, TaskQueryParam stgCmtResultQueryParam) {
        List<Predicate> predicates = new ArrayList<>();
        if (stgCmtResultQueryParam.getStgCmtStatus() != null)
            predicates.add(cb.equal(stgCmtResult.get(StgCmtResult_.status), stgCmtResultQueryParam.getStgCmtStatus()));
        if (stgCmtResultQueryParam.getStudyIUID() != null)
            predicates.add(cb.equal(stgCmtResult.get(StgCmtResult_.studyInstanceUID), stgCmtResultQueryParam.getStudyIUID()));
        if (stgCmtResultQueryParam.getStgCmtExporterID() != null)
            predicates.add(cb.equal(
                    stgCmtResult.get(StgCmtResult_.exporterID),
                    stgCmtResultQueryParam.getStgCmtExporterID().toUpperCase()));
        if (stgCmtResultQueryParam.getBatchID() != null)
            predicates.add(cb.equal(stgCmtResult.get(StgCmtResult_.batchID), stgCmtResultQueryParam.getBatchID()));
        if (stgCmtResultQueryParam.getJmsMessageID() != null)
            predicates.add(cb.equal(stgCmtResult.get(StgCmtResult_.messageID), stgCmtResultQueryParam.getJmsMessageID()));
        if (stgCmtResultQueryParam.getUpdatedBefore() != null)
            predicates.add(cb.lessThan(stgCmtResult.get(StgCmtResult_.updatedTime), stgCmtResultQueryParam.getUpdatedBefore()));
        return predicates;
    }

    public Order exportBatchOrder(String orderby, Path<ExportTask> exportTask) {
        return batchOrder(orderby, exportTask.get(ExportTask_.createdTime), exportTask.get(ExportTask_.updatedTime));
    }

    public Order retrieveBatchOrder(String orderby, Path<RetrieveTask> retrieveTask) {
        return batchOrder(orderby, retrieveTask.get(RetrieveTask_.createdTime), retrieveTask.get(RetrieveTask_.updatedTime));
    }

    public Order diffBatchOrder(String orderby, Path<DiffTask> diffTask) {
        return batchOrder(orderby, diffTask.get(DiffTask_.createdTime), diffTask.get(DiffTask_.updatedTime));
    }

    public Order stgVerBatchOrder(String orderby, Path<StorageVerificationTask> stgVerTask) {
        return batchOrder(orderby,
                stgVerTask.get(StorageVerificationTask_.createdTime),
                stgVerTask.get(StorageVerificationTask_.updatedTime));
    }

    private Order batchOrder(String orderby, Path<Date> createdTime, Path<Date> updatedTime) {
        switch (orderby) {
            case "createdTime":
                return cb.asc(cb.least(createdTime));
            case "updatedTime":
                return cb.asc(cb.least(updatedTime));
            case "-createdTime":
                return cb.desc(cb.greatest(createdTime));
            case "-updatedTime":
                return cb.desc(cb.greatest(updatedTime));
        }

        throw new IllegalArgumentException(orderby);
    }

    private void matchQueueBatch(List<Predicate> predicates, TaskQueryParam taskQueryParam,
                                               Path<QueueMessage> queueMsg) {
        predicates.add(cb.isNotNull(queueMsg.get(QueueMessage_.batchID)));
        if (taskQueryParam.getStatus() != null)
            predicates.add(cb.equal(queueMsg.get(QueueMessage_.status), taskQueryParam.getStatus()));
        if (taskQueryParam.getDeviceName() != null)
            predicates.add(cb.equal(queueMsg.get(QueueMessage_.deviceName), taskQueryParam.getDeviceName()));
        if (taskQueryParam.getBatchID() != null)
            predicates.add(cb.equal(queueMsg.get(QueueMessage_.batchID), taskQueryParam.getBatchID()));
        
    }

    private void matchExportBatch(List<Predicate> predicates, TaskQueryParam taskQueryParam,
                                                Path<ExportTask> exportTask) {
        if (!taskQueryParam.getExporterIDs().isEmpty())
            predicates.add(cb.and(exportTask.get(ExportTask_.exporterID).in(taskQueryParam.getExporterIDs())));
        if (taskQueryParam.getDeviceName() != null)
            predicates.add(cb.equal(exportTask.get(ExportTask_.deviceName), taskQueryParam.getDeviceName()));
        if (taskQueryParam.getCreatedTime() != null)
            predicates.add(cb.or(MatchDateTimeRange.range(
                    cb, exportTask.get(ExportTask_.createdTime), taskQueryParam.getCreatedTime())));
        if (taskQueryParam.getUpdatedTime() != null)
            predicates.add(cb.or(MatchDateTimeRange.range(
                    cb, exportTask.get(ExportTask_.updatedTime), taskQueryParam.getUpdatedTime())));
        
    }

    private void matchRetrieveBatch(List<Predicate> predicates, TaskQueryParam taskQueryParam,
                                                  Path<RetrieveTask> retrieveTask) {
        if (taskQueryParam.getLocalAET() != null)
            predicates.add(cb.equal(retrieveTask.get(RetrieveTask_.localAET), taskQueryParam.getLocalAET()));
        if (taskQueryParam.getRemoteAET() != null)
            predicates.add(cb.equal(retrieveTask.get(RetrieveTask_.remoteAET), taskQueryParam.getRemoteAET()));
        if (taskQueryParam.getDestinationAET() != null)
            predicates.add(cb.equal(retrieveTask.get(RetrieveTask_.destinationAET), taskQueryParam.getDestinationAET()));
        if (taskQueryParam.getCreatedTime() != null)
            predicates.add(cb.or(MatchDateTimeRange.range(
                    cb, retrieveTask.get(RetrieveTask_.createdTime), taskQueryParam.getCreatedTime())));
        if (taskQueryParam.getUpdatedTime() != null)
            predicates.add(cb.or(MatchDateTimeRange.range(
                    cb, retrieveTask.get(RetrieveTask_.updatedTime), taskQueryParam.getUpdatedTime())));
        
    }

    private void matchDiffBatch(List<Predicate> predicates, TaskQueryParam taskQueryParam,
                                              Path<DiffTask> diffTask) {
        if (taskQueryParam.getLocalAET() != null)
            predicates.add(cb.equal(diffTask.get(DiffTask_.localAET), taskQueryParam.getLocalAET()));
        if (taskQueryParam.getPrimaryAET() != null)
            predicates.add(cb.equal(diffTask.get(DiffTask_.primaryAET), taskQueryParam.getPrimaryAET()));
        if (taskQueryParam.getSecondaryAET() != null)
            predicates.add(cb.equal(diffTask.get(DiffTask_.secondaryAET), taskQueryParam.getSecondaryAET()));
        if (taskQueryParam.getCompareFields() != null)
            predicates.add(cb.equal(diffTask.get(DiffTask_.compareFields), taskQueryParam.getCompareFields()));
        if (taskQueryParam.getCheckMissing() != null)
            predicates.add(cb.equal(diffTask.get(DiffTask_.checkMissing), Boolean.parseBoolean(taskQueryParam.getCheckMissing())));
        if (taskQueryParam.getCheckDifferent() != null)
            predicates.add(cb.equal(diffTask.get(DiffTask_.checkDifferent), Boolean.parseBoolean(taskQueryParam.getCheckDifferent())));
        if (taskQueryParam.getCreatedTime() != null)
            predicates.add(cb.or(MatchDateTimeRange.range(
                    cb, diffTask.get(DiffTask_.createdTime), taskQueryParam.getCreatedTime())));
        if (taskQueryParam.getUpdatedTime() != null)
            predicates.add(cb.or(MatchDateTimeRange.range(
                    cb, diffTask.get(DiffTask_.updatedTime), taskQueryParam.getUpdatedTime())));
    }

    private void matchStgVerBatch(List<Predicate> predicates, TaskQueryParam taskQueryParam,
                                                Path<StorageVerificationTask> stgVerTask) {
        if (taskQueryParam.getLocalAET() != null)
            predicates.add(cb.equal(stgVerTask.get(StorageVerificationTask_.localAET), taskQueryParam.getLocalAET()));
        if (taskQueryParam.getCreatedTime() != null)
            predicates.add(cb.or(MatchDateTimeRange.range(
                    cb, stgVerTask.get(StorageVerificationTask_.createdTime), taskQueryParam.getCreatedTime())));
        if (taskQueryParam.getUpdatedTime() != null)
            predicates.add(cb.or(MatchDateTimeRange.range(
                    cb, stgVerTask.get(StorageVerificationTask_.updatedTime), taskQueryParam.getUpdatedTime())));
    }

}
