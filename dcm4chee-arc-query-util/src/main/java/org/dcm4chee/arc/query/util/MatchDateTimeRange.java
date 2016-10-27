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
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * J4Care.
 * Portions created by the Initial Developer are Copyright (C) 2015
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

package org.dcm4chee.arc.query.util;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.DateTimePath;
import com.querydsl.core.types.dsl.StringPath;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.DateRange;
import org.dcm4che3.util.DateUtils;

import java.util.Date;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Michael Backhaus <michael.backhaus@agfa.com>
 * @author Hesham Elbadawi <bsdreko@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 */
class MatchDateTimeRange {
    
    static enum FormatDate {
        DA {
            @Override
            String format(Date date) {
                return DateUtils.formatDA(null, date);
            }
        },
        TM {
            @Override
            String format(Date date) {
                return DateUtils.formatTM(null, date);
            }
        },
        DT {
            @Override
            String format(Date date) {
                return DateUtils.formatDT(null, date);
            }
        };
        abstract String format(Date date);
    }

    static Predicate rangeMatch(StringPath path,
            Attributes keys, int tag, FormatDate dt) {
        DateRange dateRange = keys.getDateRange(tag, null);
        if (dateRange == null)
            return null;

        return ExpressionUtils.and(range(path, dateRange, dt), path.ne("*"));
    }

    static Predicate rangeMatch(StringPath dateField, StringPath timeField,
            int dateTag, int timeTag, long dateAndTimeTag,
            Attributes keys, boolean combinedDatetimeMatching) {
        DateRange dateRange = keys.getDateRange(dateTag, null);
        DateRange timeRange = keys.getDateRange(timeTag, null);
        if (dateRange == null && timeRange == null)
            return null;

        BooleanBuilder predicates = new BooleanBuilder();
        if (dateRange != null && timeRange != null && combinedDatetimeMatching) {
            predicates.and(ExpressionUtils.and(combinedRange(
                    dateField, timeField, keys.getDateRange(dateAndTimeTag, null)), dateField.ne("*")));
        } else {
            if (dateRange != null) {
                predicates.and(ExpressionUtils.and(range(dateField, dateRange, FormatDate.DA), dateField.ne("*")));
            }
            if (timeRange != null) {
                predicates.and(ExpressionUtils.and(range(timeField, timeRange, FormatDate.TM), timeField.ne("*")));
            }
        }
        return predicates;
    }

    private static Predicate range(StringPath field, DateRange range, FormatDate dt) {
        Date startDate = range.getStartDate();
        Date endDate = range.getEndDate();
        if (startDate == null)
            return field.loe(dt.format(endDate));
        if (endDate == null)
            return field.goe(dt.format(startDate));
        return rangeInterval(field, startDate, endDate, dt, range);
    }

    static Predicate range(DateTimePath field, DateRange range, FormatDate dt) {
        Date startDate = range.getStartDate();
        Date endDate = range.getEndDate();
        if (startDate == null)
            return field.loe(endDate);
        if (endDate == null)
            return field.goe(startDate);
        return rangeInterval(field, startDate, endDate, dt, range);
    }

    private static Predicate rangeInterval(StringPath field, Date startDate,
            Date endDate, FormatDate dt, DateRange range) {
        String start = dt.format(startDate);
        String end = dt.format(endDate);
        if(dt.equals(FormatDate.TM) && range.isStartDateExeedsEndDate()){
            String midnightLow = "115959.999";
            String midnightHigh = "000000.000";
            return ExpressionUtils.or(field.between(start, midnightLow),field.between(midnightHigh, end));
        }
        else
        {
             return end.equals(start)
                     ? field.eq(start)
                     : field.between(start, end);
        }
    }

    private static Predicate rangeInterval(DateTimePath field, Date startDate,
                                           Date endDate, FormatDate dt, DateRange range) {
        if(dt.equals(FormatDate.TM) && range.isStartDateExeedsEndDate()){
            String midnightLow = "115959.999";
            String midnightHigh = "000000.000";
            return ExpressionUtils.or(field.between(startDate, midnightLow),field.between(midnightHigh, endDate));
        }
        else
        {
            return endDate.equals(startDate)
                    ? field.eq(startDate)
                    : field.between(startDate, endDate);
        }
    }

    private static Predicate combinedRange(StringPath dateField, StringPath timeField, DateRange dateRange) {
        if (dateRange.getStartDate() == null)
            return combinedRangeEnd(dateField, timeField, 
                    DateUtils.formatDA(null, dateRange.getEndDate()), 
                    DateUtils.formatTM(null, dateRange.getEndDate()));
        if (dateRange.getEndDate() == null)
            return combinedRangeStart(dateField, timeField, 
                    DateUtils.formatDA(null, dateRange.getStartDate()), 
                    DateUtils.formatTM(null, dateRange.getStartDate()));
        return combinedRangeInterval(dateField, timeField, 
                    dateRange.getStartDate(), dateRange.getEndDate());
    }

    private static Predicate combinedRangeInterval(StringPath dateField,
            StringPath timeField, Date startDateRange, Date endDateRange) {
        String startTime = DateUtils.formatTM(null, startDateRange);
        String endTime = DateUtils.formatTM(null, endDateRange);
        String startDate = DateUtils.formatDA(null, startDateRange);
        String endDate = DateUtils.formatDA(null, endDateRange);
        return endDate.equals(startDate)
            ? ExpressionUtils.allOf(dateField.eq(startDate), 
                    timeField.goe(startTime), timeField.loe(endTime))
            : ExpressionUtils.and(
                    combinedRangeStart(dateField, timeField, startDate, startTime), 
                    combinedRangeEnd(dateField, timeField, endDate, endTime));
    }

    private static Predicate combinedRangeEnd(StringPath dateField,
            StringPath timeField, String endDate, String endTime) {
        Predicate endDayTime =
            ExpressionUtils.and(dateField.eq(endDate), timeField.loe(endTime));
        Predicate endDayTimeUnknown =
            ExpressionUtils.and(dateField.eq(endDate), timeField.eq("*"));
        Predicate endDayPrevious = dateField.lt(endDate);
        return ExpressionUtils.anyOf(endDayTime, endDayTimeUnknown, endDayPrevious);
    }

    private static Predicate combinedRangeStart(StringPath dateField,
            StringPath timeField, String startDate, String startTime) {
        Predicate startDayTime = 
            ExpressionUtils.and(dateField.eq(startDate), timeField.goe(startTime));
        Predicate startDayTimeUnknown = 
            ExpressionUtils.and(dateField.eq(startDate), timeField.eq("*"));
        Predicate startDayFollowing = dateField.gt(startDate);
        return ExpressionUtils.anyOf(startDayTime, startDayTimeUnknown, startDayFollowing);
    }
}
