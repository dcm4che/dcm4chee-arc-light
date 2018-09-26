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

package org.dcm4chee.arc.conf;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.DateRange;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.util.TagUtils;

import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.TemporalAmount;
import java.util.Date;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Sep 2018
 */
public class EntitySelector {
    private static final long MILLIS_PER_DAY = 24 * 3600_000;
    private final String value;
    private final Attributes keys = new Attributes();
    private final int numberOfPriors;

    public static EntitySelector[] valuesOf(String... ss) {
        EntitySelector[] selectors = new EntitySelector[ss.length];
        for (int i = 0; i < ss.length; i++)
            selectors[i] = new EntitySelector(ss[i]);
        return selectors;
    }

    public EntitySelector(String value) {
        this.numberOfPriors = parseKeys(value);
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }

    private int parseKeys(String queryParams) {
        AttributesBuilder builder = new AttributesBuilder(keys);
        int priors = -1;
        for (String queryParam : StringUtils.split(queryParams, '&')) {
            String[] keyValue = StringUtils.split(queryParam, '=');
            if (keyValue.length != 2)
                throw new IllegalArgumentException(queryParam);

            try {
                if (keyValue[0].equals("priors")) {
                    priors = Integer.parseInt(keyValue[1]);
                } else if (keyValue[0].equals("StudyAge")) {
                    String[] studyAge = StringUtils.split(keyValue[1], '-');
                    if (studyAge.length != 2)
                        throw new IllegalArgumentException();

                    keys.setDateRange(Tag.StudyDate, VR.DA,
                            new DateRange(age2Date(studyAge[1]), age2Date(studyAge[0])));
                } else {
                    builder.setString(TagUtils.parseTagPath(keyValue[0]), keyValue[1]);
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(queryParam);
            }
        }
        return priors;
    }

    private static Date age2Date(String age) {
        return age.isEmpty() ? null : new Date(LocalDate.now().minus(age2Period(age)).toEpochDay() * MILLIS_PER_DAY);
    }

    private static Period age2Period(String age) {
        int last, n;
        if ((last = age.length() - 1) > 0 && (n = Integer.parseInt(age.substring(0, last))) > 0)
            switch (age.charAt(last)) {
                case 'D':
                    return Period.ofDays(n);
                case 'M':
                    return Period.ofMonths(n);
                case 'W':
                    return Period.ofWeeks(n);
                case 'Y':
                    return Period.ofYears(n);
            }
        throw new IllegalArgumentException(age);
    }

    public Attributes getQueryKeys() {
        return keys;
    }

    public int getNumberOfPriors() {
        return numberOfPriors;
    }
}
