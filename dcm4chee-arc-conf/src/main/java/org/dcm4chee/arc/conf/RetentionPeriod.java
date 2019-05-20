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
 *
 */

package org.dcm4chee.arc.conf;

import org.dcm4che3.util.StringUtils;

import java.time.Period;
import java.util.Calendar;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since May 2019
 */
public class RetentionPeriod implements Comparable<RetentionPeriod> {
    private final String value;
    private final Period period;
    private final ScheduleExpression schedule;

    public RetentionPeriod(String value, Period period, ScheduleExpression schedule) {
        this.value = value;
        this.period = period;
        this.schedule = schedule;
    }

    public static RetentionPeriod valueOf(String s) {
        String[] split1 = StringUtils.split(s, ']');
        switch (split1.length) {
            case 1:
                return new RetentionPeriod(s, Period.parse(s), null);
            case 2:
                String[] split2 = StringUtils.split(split1[0], '[');
                if (split2.length == 2)
                    return new RetentionPeriod(s,
                            Period.parse(split1[split1.length-1]),
                            ScheduleExpression.valueOf(split2[1]));
        }
        throw new IllegalArgumentException(s);
    }

    @Override
    public String toString() {
        return value;
    }

    public Period getPeriod() {
        return period;
    }

    public String getPrefix() {
        return value.substring(0, value.indexOf(']')+1);
    }

    public boolean match(Calendar cal) {
        return schedule == null || schedule.contains(cal);
    }

    @Override
    public int compareTo(RetentionPeriod o) {
        return schedule != null ? o.schedule != null ? value.compareTo(o.value) : -1 : o.schedule != null ? 1 : 0;
    }

    public enum DeleteStudies {
        OlderThan, ReceivedBefore, NotUsedSince
    }
}
