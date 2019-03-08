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
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.ComparableExpressionBase;
import com.querydsl.core.types.dsl.DateTimeExpression;
import com.querydsl.core.types.dsl.DateTimePath;
import org.dcm4chee.arc.entity.*;

import javax.persistence.criteria.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

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

    public static Predicate matchQueueMessage(
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
    
    public static Predicate matchRetrieveTask(String localAET, String remoteAET, String destinationAET, String studyUID,
                                              String createdTime, String updatedTime) {
        BooleanBuilder predicate = new BooleanBuilder();
        if (localAET != null)
            predicate.and(QRetrieveTask.retrieveTask.localAET.eq(localAET));
        if (remoteAET != null)
            predicate.and(QRetrieveTask.retrieveTask.remoteAET.eq(remoteAET));
        if (destinationAET != null)
            predicate.and(QRetrieveTask.retrieveTask.destinationAET.eq(destinationAET));
        if (studyUID != null)
            predicate.and(QRetrieveTask.retrieveTask.studyInstanceUID.eq(studyUID));
        if (createdTime != null)
            predicate.and(MatchDateTimeRange.range(
                    QRetrieveTask.retrieveTask.createdTime, createdTime, MatchDateTimeRange.FormatDate.DT));
        if (updatedTime != null)
            predicate.and(MatchDateTimeRange.range(
                    QRetrieveTask.retrieveTask.updatedTime, updatedTime, MatchDateTimeRange.FormatDate.DT));
        return predicate;
    }

    public static Predicate matchExportTask(List<String> exporterIDs, String deviceName, String studyUID,
                                            String createdTime, String updatedTime) {
        BooleanBuilder predicate = new BooleanBuilder();
        if (!exporterIDs.isEmpty())
            predicate.and(QExportTask.exportTask.exporterID.in(exporterIDs));
        if (deviceName != null)
            predicate.and(QExportTask.exportTask.deviceName.eq(deviceName));
        if (studyUID != null)
            predicate.and(QExportTask.exportTask.studyInstanceUID.eq(studyUID));
        if (createdTime != null)
            predicate.and(MatchDateTimeRange.range(
                    QExportTask.exportTask.createdTime, createdTime, MatchDateTimeRange.FormatDate.DT));
        if (updatedTime != null)
            predicate.and(MatchDateTimeRange.range(
                    QExportTask.exportTask.updatedTime, updatedTime, MatchDateTimeRange.FormatDate.DT));
        return predicate;
    }

    public List<javax.persistence.criteria.Predicate> queueMsgPredicates(
            Root<QueueMessage> queueMsg, TaskQueryParam queueTaskQueryParam) {
        List<javax.persistence.criteria.Predicate> predicates = new ArrayList<>();
        matchQueueMsg(predicates, queueTaskQueryParam, queueMsg);
        return predicates;
    }

    public List<javax.persistence.criteria.Predicate> exportPredicates(
            From<ExportTask, QueueMessage> queueMsg, Root<ExportTask> exportTask,
            TaskQueryParam queueTaskQueryParam, TaskQueryParam exportTaskQueryParam) {
        List<javax.persistence.criteria.Predicate> predicates = new ArrayList<>();
        matchQueueMsg(predicates, queueTaskQueryParam, queueMsg);
        matchExportTask(predicates, exportTaskQueryParam, exportTask);
        return predicates;
    }

    public List<javax.persistence.criteria.Predicate> retrievePredicates(
            From<RetrieveTask, QueueMessage> queueMsg, Root<RetrieveTask> retrieveTask,
            TaskQueryParam queueTaskQueryParam, TaskQueryParam retrieveTaskQueryParam) {
        List<javax.persistence.criteria.Predicate> predicates = new ArrayList<>();
        matchQueueMsg(predicates, queueTaskQueryParam, queueMsg);
        matchRetrieveTask(predicates, retrieveTaskQueryParam, retrieveTask);
        return predicates;
    }

    public List<javax.persistence.criteria.Predicate> diffPredicates(
            From<DiffTask, QueueMessage> queueMsg, Root<DiffTask> diffTask,
            TaskQueryParam queueTaskQueryParam, TaskQueryParam diffTaskQueryParam) {
        List<javax.persistence.criteria.Predicate> predicates = new ArrayList<>();
        matchQueueMsg(predicates, queueTaskQueryParam, queueMsg);
        matchDiffTask(predicates, diffTaskQueryParam, diffTask);
        return predicates;
    }

    public List<javax.persistence.criteria.Predicate> stgVerPredicates(
            From<StorageVerificationTask, QueueMessage> queueMsg, Root<StorageVerificationTask> stgVerTask,
            TaskQueryParam queueTaskQueryParam, TaskQueryParam stgVerTaskQueryParam) {
        List<javax.persistence.criteria.Predicate> predicates = new ArrayList<>();
        matchQueueMsg(predicates, queueTaskQueryParam, queueMsg);
        matchStgVerTask(predicates, stgVerTaskQueryParam, stgVerTask);
        return predicates;
    }

    private <Z> void matchQueueMsg(List<javax.persistence.criteria.Predicate> predicates,
                                   TaskQueryParam taskQueryParam, From<Z, QueueMessage> queueMsg) {
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
            List<javax.persistence.criteria.Predicate> predicates, TaskQueryParam taskQueryParam,
            Root<ExportTask> exportTask) {
        if (taskQueryParam.getStatus() == QueueMessage.Status.TO_SCHEDULE)
            predicates.add(cb.and(exportTask.get(ExportTask_.queueMessage).isNull()));
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

    private void matchRetrieveTask(List<javax.persistence.criteria.Predicate> predicates,
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
            List<javax.persistence.criteria.Predicate> predicates, TaskQueryParam taskQueryParam, Path<DiffTask> diffTask) {
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
            List<javax.persistence.criteria.Predicate> predicates, TaskQueryParam taskQueryParam,
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

    public static Predicate matchDiffTask(String localAET, String primaryAET, String secondaryAET, String checkDifferent,
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

    public static Predicate matchStgVerTask(String localAET, String studyUID,
                                            String createdTime, String updatedTime) {
        BooleanBuilder predicate = new BooleanBuilder();
        if (localAET != null)
            predicate.and(QStorageVerificationTask.storageVerificationTask.localAET.eq(localAET));
        if (studyUID != null)
            predicate.and(QStorageVerificationTask.storageVerificationTask.studyInstanceUID.eq(studyUID));
        if (createdTime != null)
            predicate.and(MatchDateTimeRange.range(
                    QStorageVerificationTask.storageVerificationTask.createdTime, createdTime, MatchDateTimeRange.FormatDate.DT));
        if (updatedTime != null)
            predicate.and(MatchDateTimeRange.range(
                    QStorageVerificationTask.storageVerificationTask.updatedTime, updatedTime, MatchDateTimeRange.FormatDate.DT));
        return predicate;
    }

    public Order exportTaskOrder(String orderby, Path<ExportTask> exportTask) {
        return taskOrder(orderby,
                exportTask.get(ExportTask_.createdTime),
                exportTask.get(ExportTask_.updatedTime),
                cb);
    }

    public Order retrieveTaskOrder(String orderby, Path<RetrieveTask> retrieveTask) {
        return taskOrder(orderby,
                retrieveTask.get(RetrieveTask_.createdTime),
                retrieveTask.get(RetrieveTask_.updatedTime),
                cb);
    }

    public Order stgVerTaskOrder(String orderby, Path<StorageVerificationTask> stgVerTask) {
        return taskOrder(orderby,
                stgVerTask.get(StorageVerificationTask_.createdTime),
                stgVerTask.get(StorageVerificationTask_.updatedTime),
                cb);
    }

    public Order diffTaskOrder(String orderby, Path<DiffTask> diffTask) {
        return taskOrder(orderby,
                diffTask.get(DiffTask_.createdTime),
                diffTask.get(DiffTask_.updatedTime),
                cb);
    }

    public Order queueMessageOrder(String orderBy, Path<QueueMessage> queueMsg) {
        return taskOrder(orderBy,
                queueMsg.get(QueueMessage_.createdTime),
                queueMsg.get(QueueMessage_.updatedTime),
                cb);
    }

    private Order taskOrder(
            String orderby, Path<Date> createdTime, Path<Date> updatedTime, CriteriaBuilder cb) {
        return order(orderby, createdTime, updatedTime, cb);
    }

    private Order order(String orderby, Path<Date> createdTime, Path<Date> updatedTime, CriteriaBuilder cb) {
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

    public Order exportBatchOrder(String orderby, Path<ExportTask> exportTask) {
        return batchOrder(orderby, exportTask.get(ExportTask_.createdTime), exportTask.get(ExportTask_.updatedTime), cb);
    }

    private Order batchOrder(String orderby, Path<Date> createdTime, Path<Date> updatedTime, CriteriaBuilder cb) {
        switch (orderby) {
            case "createdTime":
                cb.least(createdTime);
                return cb.asc(createdTime);
            case "updatedTime":
                cb.least(updatedTime);
                return cb.asc(updatedTime);
            case "-createdTime":
                cb.greatest(createdTime);
                return cb.desc(createdTime);
            case "-updatedTime":
                cb.greatest(updatedTime);
                return cb.desc(updatedTime);
        }

        throw new IllegalArgumentException(orderby);
    }

    public static OrderSpecifier<Date> exportBatchOrder(String orderby) {
        return batchOrder(orderby, QExportTask.exportTask.createdTime, QExportTask.exportTask.updatedTime);
    }

    public static OrderSpecifier<Date> retrieveBatchOrder(String orderby) {
        return batchOrder(orderby, QRetrieveTask.retrieveTask.createdTime, QRetrieveTask.retrieveTask.updatedTime);
    }

    public static OrderSpecifier<Date> diffBatchOrder(String orderby) {
        return batchOrder(orderby, QDiffTask.diffTask.createdTime, QDiffTask.diffTask.updatedTime);
    }

    public static OrderSpecifier<Date> stgCmtBatchOrder(String orderby) {
        return batchOrder(orderby, QStorageVerificationTask.storageVerificationTask.createdTime,
                QStorageVerificationTask.storageVerificationTask.updatedTime);
    }

    private static OrderSpecifier<Date> batchOrder(
            String orderby, DateTimePath<Date> createdTime, DateTimePath<Date> updatedTime) {
        return order(orderby, createdTime, updatedTime,
                ((Function<DateTimeExpression<Date>, DateTimeExpression<Date>>) DateTimeExpression::min)
                        .andThen(ComparableExpressionBase::asc),
                ((Function<DateTimeExpression<Date>, DateTimeExpression<Date>>) DateTimeExpression::max)
                        .andThen(ComparableExpressionBase::desc));
    }

    private static OrderSpecifier<Date> order(
            String orderby,
            DateTimePath<Date> createdTime,
            DateTimePath<Date> updatedTime,
            Function<DateTimeExpression<Date>, OrderSpecifier<Date>> asc,
            Function<DateTimeExpression<Date>, OrderSpecifier<Date>> desc) {
        switch (orderby) {
            case "createdTime":
                return asc.apply(createdTime);
            case "updatedTime":
                return asc.apply(updatedTime);
            case "-createdTime":
                return desc.apply(createdTime);
            case "-updatedTime":
                return desc.apply(updatedTime);
        }
        throw new IllegalArgumentException(orderby);
    }

    public static Predicate matchQueueBatch(String deviceName, QueueMessage.Status status, String batchID) {
        BooleanBuilder predicate = new BooleanBuilder();
        predicate.and(QQueueMessage.queueMessage.batchID.isNotNull());
        if (status != null)
            predicate.and(QQueueMessage.queueMessage.status.eq(status));
        if (deviceName != null)
            predicate.and(QQueueMessage.queueMessage.deviceName.eq(deviceName));
        if (batchID != null)
            predicate.and(QQueueMessage.queueMessage.batchID.eq(batchID));
        return predicate;
    }

    public static Predicate matchExportBatch(
            List<String> exporterIDs, String deviceName, String createdTime, String updatedTime) {
        BooleanBuilder predicate = new BooleanBuilder();
        if (exporterIDs != null)
            predicate.and(QExportTask.exportTask.exporterID.in(exporterIDs));
        if (deviceName != null)
            predicate.and(QExportTask.exportTask.deviceName.eq(deviceName));
        if (createdTime != null)
            predicate.and(ExpressionUtils.anyOf(MatchDateTimeRange.range(
                    QExportTask.exportTask.createdTime, createdTime, MatchDateTimeRange.FormatDate.DT)));
        if (updatedTime != null)
            predicate.and(ExpressionUtils.anyOf(MatchDateTimeRange.range(
                    QExportTask.exportTask.updatedTime, updatedTime, MatchDateTimeRange.FormatDate.DT)));
        return predicate;
    }
    
    public static Predicate matchRetrieveBatch(
            String localAET, String remoteAET, String destinationAET, String createdTime, String updatedTime) {
        BooleanBuilder predicate = new BooleanBuilder();
        if (localAET != null)
            predicate.and(QRetrieveTask.retrieveTask.localAET.eq(localAET));
        if (remoteAET != null)
            predicate.and(QRetrieveTask.retrieveTask.remoteAET.eq(remoteAET));
        if (destinationAET != null)
            predicate.and(QRetrieveTask.retrieveTask.destinationAET.eq(destinationAET));
        if (createdTime != null)
            predicate.and(ExpressionUtils.anyOf(MatchDateTimeRange.range(
                    QRetrieveTask.retrieveTask.createdTime, createdTime, MatchDateTimeRange.FormatDate.DT)));
        if (updatedTime != null)
            predicate.and(ExpressionUtils.anyOf(MatchDateTimeRange.range(
                    QRetrieveTask.retrieveTask.updatedTime, updatedTime, MatchDateTimeRange.FormatDate.DT)));
        return predicate;
    }

    public static Predicate matchDiffBatch(String localAET, String primaryAET, String secondaryAET, String comparefields,
                                           String checkMissing, String checkDifferent, String createdTime, String updatedTime) {
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
            predicate.and(ExpressionUtils.anyOf(MatchDateTimeRange.range(
                    QDiffTask.diffTask.createdTime, createdTime, MatchDateTimeRange.FormatDate.DT)));
        if (updatedTime != null)
            predicate.and(ExpressionUtils.anyOf(MatchDateTimeRange.range(
                    QDiffTask.diffTask.updatedTime, updatedTime, MatchDateTimeRange.FormatDate.DT)));
        return predicate;
    }

    public static Predicate matchStgCmtBatch(String localAET, String createdTime, String updatedTime) {
        BooleanBuilder predicate = new BooleanBuilder();
        if (localAET != null)
            predicate.and(QStorageVerificationTask.storageVerificationTask.localAET.eq(localAET));
        if (createdTime != null)
            predicate.and(ExpressionUtils.anyOf(MatchDateTimeRange.range(
                    QStorageVerificationTask.storageVerificationTask.createdTime, createdTime,
                    MatchDateTimeRange.FormatDate.DT)));
        if (updatedTime != null)
            predicate.and(ExpressionUtils.anyOf(MatchDateTimeRange.range(
                    QStorageVerificationTask.storageVerificationTask.updatedTime, updatedTime,
                    MatchDateTimeRange.FormatDate.DT)));
        return predicate;
    }

    public void matchQueueBatch(List<javax.persistence.criteria.Predicate> predicates, TaskQueryParam taskQueryParam,
                                               Path<QueueMessage> queueMsg) {
        predicates.add(cb.isNotNull(queueMsg.get(QueueMessage_.batchID)));
        if (taskQueryParam.getStatus() != null)
            predicates.add(cb.equal(queueMsg.get(QueueMessage_.status), taskQueryParam.getStatus()));
        if (taskQueryParam.getDeviceName() != null)
            predicates.add(cb.equal(queueMsg.get(QueueMessage_.deviceName), taskQueryParam.getDeviceName()));
        if (taskQueryParam.getBatchID() != null)
            predicates.add(cb.equal(queueMsg.get(QueueMessage_.batchID), taskQueryParam.getBatchID()));
        
    }
    
    public void matchExportBatch(List<javax.persistence.criteria.Predicate> predicates, TaskQueryParam taskQueryParam,
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

    public void matchRetrieveBatch(List<javax.persistence.criteria.Predicate> predicates, TaskQueryParam taskQueryParam,
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

    public void matchDiffBatch(List<javax.persistence.criteria.Predicate> predicates, TaskQueryParam taskQueryParam,
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

    public void matchStgCmtBatch(List<javax.persistence.criteria.Predicate> predicates, TaskQueryParam taskQueryParam,
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
