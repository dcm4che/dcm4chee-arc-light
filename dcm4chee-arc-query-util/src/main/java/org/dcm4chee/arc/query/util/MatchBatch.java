package org.dcm4chee.arc.query.util;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Predicate;
import org.dcm4chee.arc.entity.QExportTask;
import org.dcm4chee.arc.entity.QQueueMessage;
import org.dcm4chee.arc.entity.QRetrieveTask;
import org.dcm4chee.arc.entity.QueueMessage;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Feb 2018
 */

public class MatchBatch {
    public static Predicate matchQueueBatch(
            String deviceName, QueueMessage.Status status, String createdTime, String updatedTime) {
        BooleanBuilder predicate = new BooleanBuilder();
        predicate.and(QQueueMessage.queueMessage.batchID.isNotNull());
        if (status != null)
            predicate.and(QQueueMessage.queueMessage.status.in(status));
        if (deviceName != null)
            predicate.and(QQueueMessage.queueMessage.deviceName.in(deviceName));
        if (createdTime != null)
            predicate.and(ExpressionUtils.anyOf(MatchDateTimeRange.range(
                    QQueueMessage.queueMessage.createdTime, createdTime, MatchDateTimeRange.FormatDate.DT)));
        if (updatedTime != null)
            predicate.and(ExpressionUtils.anyOf(MatchDateTimeRange.range(
                    QQueueMessage.queueMessage.updatedTime, updatedTime, MatchDateTimeRange.FormatDate.DT)));
        return predicate;
    }

    public static Predicate matchExportBatch(
            String exporterID, String deviceName, String createdTime, String updatedTime) {
        BooleanBuilder predicate = new BooleanBuilder();
        if (exporterID != null)
            predicate.and(QExportTask.exportTask.exporterID.in(exporterID));
        if (deviceName != null)
            predicate.and(QExportTask.exportTask.deviceName.in(deviceName));
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
}
