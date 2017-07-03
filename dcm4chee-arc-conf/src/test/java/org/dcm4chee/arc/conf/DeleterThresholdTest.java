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
 * Portions created by the Initial Developer are Copyright (C) 2013
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

package org.dcm4chee.arc.conf;

import org.junit.Test;

import java.util.Arrays;
import java.util.GregorianCalendar;

import static org.junit.Assert.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jan 2016
 */
public class DeleterThresholdTest {

    @Test
    public void testCompare() throws Exception {
        DeleterThreshold threshold1 = DeleterThreshold.valueOf("10_[hour=18-6]10GB");
        DeleterThreshold threshold2 = DeleterThreshold.valueOf("20_[dayOfWeek=0,6]10GB");
        DeleterThreshold threshold3 = DeleterThreshold.valueOf("1GB");
        DeleterThreshold[] thresholds = { threshold3, threshold2, threshold1 };
        Arrays.sort(thresholds);
        assertSame(threshold1, thresholds[0]);
        assertSame(threshold2, thresholds[1]);
        assertSame(threshold3, thresholds[2]);
    }

    @Test
    public void testMatch() throws Exception {
        DeleterThreshold threshold = DeleterThreshold.valueOf("10_[hour=18-6]10GB");
        assertFalse(threshold.match(new GregorianCalendar(2015, 9, 8, 10, 0)));
        assertTrue(threshold.match(new GregorianCalendar(2015, 9, 8, 18, 0)));
    }
}