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
 * Portions created by the Initial Developer are Copyright (C) 2015-2017
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
import org.dcm4che3.data.DatePrecision;
import org.dcm4che3.data.DateRange;
import org.dcm4che3.data.VR;
import org.dcm4chee.arc.entity.QExportTask;
import org.dcm4chee.arc.entity.QQueueMessage;
import org.dcm4chee.arc.entity.QRetrieveTask;
import org.dcm4chee.arc.entity.QueueMessage;

import java.util.Date;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jan 2018
 */

public class PredicateUtils {

    public static BooleanBuilder queueMsgPredicate(String queueName, String deviceName, QueueMessage.Status status, String createdTime,
                                    String updatedTime) {
        BooleanBuilder predicate = new BooleanBuilder();
        if (queueName != null)
            predicate.and(QQueueMessage.queueMessage.queueName.eq(queueName));
        if (status != null)
            predicate.and(status == QueueMessage.Status.TO_SCHEDULE
                    ? QExportTask.exportTask.queueMessage.isNull()
                    : QQueueMessage.queueMessage.status.eq(status));
        if (deviceName != null)
            predicate.and(QQueueMessage.queueMessage.deviceName.eq(deviceName));
        if (createdTime != null)
            predicate.and(ExpressionUtils.and(MatchDateTimeRange.range(
                    QQueueMessage.queueMessage.createdTime, getDateRange(createdTime), MatchDateTimeRange.FormatDate.DT),
                    QQueueMessage.queueMessage.createdTime.isNotNull()));
        if (updatedTime != null)
            predicate.and(ExpressionUtils.and(MatchDateTimeRange.range(
                    QQueueMessage.queueMessage.updatedTime, getDateRange(updatedTime), MatchDateTimeRange.FormatDate.DT),
                    QQueueMessage.queueMessage.updatedTime.isNotNull()));
        return predicate;
    }

    public static BooleanBuilder extRetrievePredicate(String localAET, String remoteAET, String destinationAET, String studyUID,
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
            predicate.and(ExpressionUtils.and(MatchDateTimeRange.range(
                    QRetrieveTask.retrieveTask.createdTime, getDateRange(createdTime), MatchDateTimeRange.FormatDate.DT),
                    QRetrieveTask.retrieveTask.createdTime.isNotNull()));
        if (updatedTime != null)
            predicate.and(ExpressionUtils.and(MatchDateTimeRange.range(
                    QRetrieveTask.retrieveTask.updatedTime, getDateRange(updatedTime), MatchDateTimeRange.FormatDate.DT),
                    QRetrieveTask.retrieveTask.updatedTime.isNotNull()));
        return predicate;
    }

    public static BooleanBuilder exportPredicate(String exporterID, String deviceName, String studyUID, String createdTime, String updatedTime) {
        BooleanBuilder predicate = new BooleanBuilder();
        if (exporterID != null)
            predicate.and(QExportTask.exportTask.exporterID.eq(exporterID));
        if (deviceName != null)
            predicate.and(QExportTask.exportTask.deviceName.eq(deviceName));
        if (studyUID != null)
            predicate.and(QExportTask.exportTask.studyInstanceUID.eq(studyUID));
        if (createdTime != null)
            predicate.and(ExpressionUtils.and(MatchDateTimeRange.range(
                    QExportTask.exportTask.createdTime, getDateRange(createdTime), MatchDateTimeRange.FormatDate.DT),
                    QExportTask.exportTask.createdTime.isNotNull()));
        if (updatedTime != null)
            predicate.and(ExpressionUtils.and(MatchDateTimeRange.range(
                    QExportTask.exportTask.updatedTime, getDateRange(updatedTime), MatchDateTimeRange.FormatDate.DT),
                    QExportTask.exportTask.updatedTime.isNotNull()));
        return predicate;
    }

    private static DateRange getDateRange(String s) {
        String[] range = splitRange(s);
        DatePrecision precision = new DatePrecision();
        Date start = range[0] == null ? null
                : VR.DT.toDate(range[0], null, 0, false, null, precision);
        Date end = range[1] == null ? null
                : VR.DT.toDate(range[1], null, 0, true, null, precision);
        return new DateRange(start, end);
    }

    private static String[] splitRange(String s) {
        String[] range = new String[2];
        int delim = s.indexOf('-');
        if (delim == -1)
            range[0] = range[1] = s;
        else {
            if (delim > 0)
                range[0] =  s.substring(0, delim);
            if (delim < s.length() - 1)
                range[1] =  s.substring(delim+1);
        }
        return range;
    }
}
