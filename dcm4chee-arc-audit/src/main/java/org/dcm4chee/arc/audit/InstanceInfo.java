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
 * *** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.audit;

import java.util.HashMap;
import java.util.HashSet;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since March 2016
 */
class InstanceInfo {
    private HashMap<String, HashSet<String>> sopClassMap = new HashMap<>();
    private HashSet<String> mpps = new HashSet<>();
    private HashSet<String> acc = new HashSet<>();
    private HashSet<String> studyDate = new HashSet<>();

    InstanceInfo() {}

    HashMap<String, HashSet<String>> getSopClassMap() {
        return sopClassMap;
    }

    void addSOPInstance(AuditInfo info) {
        sopClassMap.computeIfAbsent(
                info.getField(AuditInfo.SOP_CUID),
                k -> new HashSet<>()).add(info.getField(AuditInfo.SOP_IUID));
    }

    String[] getMpps() {
        return mpps.toArray(new String[0]);
    }

    void addMpps(AuditInfo info) {
        String mppsUID = info.getField(AuditInfo.MPPS_UID);
        if (mppsUID != null)
            mpps.add(mppsUID);
    }

    String[] getAcc() {
        return acc.toArray(new String[0]);
    }

    void addAcc(AuditInfo info) {
        String accNum = info.getField(AuditInfo.ACC_NUM);
        if (accNum != null)
            acc.add(accNum);
    }

    HashSet<String> getStudyDate() {
        return studyDate;
    }

    void addStudyDate(AuditInfo info) {
        String studyDt = info.getField(AuditInfo.ACC_NUM);
        if (studyDt != null)
            studyDate.add(studyDt);
    }
}
