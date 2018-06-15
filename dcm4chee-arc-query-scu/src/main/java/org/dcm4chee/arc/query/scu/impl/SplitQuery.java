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
 * Portions created by the Initial Developer are Copyright (C) 2015-2018
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
 *
 */

package org.dcm4chee.arc.query.scu.impl;

import org.dcm4che3.data.*;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.DimseRSP;
import org.dcm4che3.net.Status;
import org.dcm4chee.arc.conf.Duration;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.stream.IntStream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jun 2018
 */
class SplitQuery implements DimseRSP {
    private final Association as;
    private final String cuid;
    private final int priority;
    private final Attributes keys;
    private int autoCancel;
    private long startDate;
    private final long endDate;
    private final long maxRange;
    private final RangeType rangeType;
    private DimseRSP dimseRSP;

    public SplitQuery(Association as, String cuid, int priority, Attributes keys, int autoCancel,
                      long startDate, long endDate, Duration splitStudyDateRange)
            throws IOException, InterruptedException {
        this.as = as;
        this.cuid = cuid;
        this.priority = priority;
        this.keys = keys;
        this.autoCancel = autoCancel;
        this.rangeType = RangeType.valueOf(splitStudyDateRange);
        this.maxRange = rangeType.rangeInMillis(splitStudyDateRange);
        this.startDate = startDate;
        this.endDate = endDate;
        nextQuery();
    }

    private boolean nextQuery() throws IOException, InterruptedException {
        if (startDate >= endDate) {
            return false;
        }
        startDate = rangeType.adjustKeys(keys, startDate, maxRange, endDate);
        dimseRSP = as.cfind(cuid, priority, keys, UID.ImplicitVRLittleEndian, autoCancel);
        return true;
    }

    @Override
    public boolean next() throws IOException, InterruptedException {
        do {
            if (!dimseRSP.next()) {
                return false;
            }
            int status = dimseRSP.getCommand().getInt(Tag.Status, -1);
            if (status != Status.Success) {
                // adjust auto cancel of next query
                // decrementing to 0 would deactivate auto cancel
                if (autoCancel > 1 && Status.isPending(status)) {
                    autoCancel--;
                }
                return true;
            }
        } while (nextQuery());
        return true;
    }

    @Override
    public Attributes getCommand() {
        return dimseRSP.getCommand();
    }

    @Override
    public Attributes getDataset() {
        return dimseRSP.getDataset();
    }

    @Override
    public void cancel(Association a) throws IOException {
        dimseRSP.cancel(a);
    }

    enum RangeType {
        DA {
            @Override
            long rangeInMillis(Duration duration) {
                return (duration.getSeconds() / SECS_PER_DAY) * MILLIS_PER_DAY;
            }

            @Override
            long adjustKeys(Attributes keys, long startDate, long maxRange, long totEndDate) {
                long endDate = startDate + maxRange;
                DateRange range = new DateRange(
                        new Date(startDate),
                        new Date((endDate < totEndDate) ? endDate - MILLIS_PER_DAY : totEndDate));
                keys.setDateRange(Tag.StudyDate, VR.DA, range);
                return endDate;
            }
        },
        DT {
            @Override
            long rangeInMillis(Duration duration) {
                int maxMins = ((int) duration.getSeconds()) / 60;
                return maxMins > 0
                        ? IntStream.of(DIVS_OF_MINS_OF_DAY).filter(i -> i <= maxMins).findFirst().getAsInt()
                            * MILLIS_PER_MINUTE
                        : MILLIS_PER_MINUTE;
            }

            @Override
            long adjustKeys(Attributes keys, long startDate, long maxRange, long totEndDate) {
                long endDate = startDate + maxRange;
                DateRange range = new DateRange(
                        new Date(startDate),
                        new Date((endDate < totEndDate) ? endDate - MILLIS_PER_MINUTE : totEndDate));
                keys.setDate(Tag.StudyDate, VR.DA, range.getStartDate());
                keys.setDateRange(Tag.StudyTime, VR.TM, PRECISION_MINUTES, range);
                return endDate;
            }
        };

        static final int SECS_PER_DAY = 86400;
        static final long MILLIS_PER_DAY = 86400_000L;
        static final long MILLIS_PER_MINUTE = 60_000L;
        static final int[] DIVS_OF_MINS_OF_DAY = {
                720, 480, 360, 288, 240, 180, 160, 144, 120, 96,
                90, 80, 72, 60, 48, 45, 40, 36, 32, 30,
                24, 20, 18, 16, 15, 12, 10, 9, 8, 6,
                5, 4, 3, 2, 1 };
        static final DatePrecision PRECISION_MINUTES = new DatePrecision(Calendar.MINUTE);


        static RangeType valueOf(Duration duration) {
            return duration.getSeconds() >= SECS_PER_DAY ? DA : DT;
        }
        abstract long rangeInMillis(Duration duration);
        abstract long adjustKeys(Attributes keys, long startDate, long maxRange, long totEndDate);
    }
}
