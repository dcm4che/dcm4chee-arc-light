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

package org.dcm4chee.arc.audit;

import org.dcm4che3.util.StringUtils;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since June 2016
 */

class AuditInfo {
    static final int CALLING_HOST = 0;
    static final int CALLING_AET = 1;
    static final int CALLED_AET = 2;
    static final int CALLED_HOST = 3;
    static final int STUDY_UID = 4;
    static final int ACC_NUM = 5;
    static final int P_ID = 6;
    static final int P_NAME = 7;
    static final int OUTCOME = 8;
    static final int STUDY_DATE = 9;
    static final int Q_POID = 10;
    static final int Q_STRING = 11;
    static final int DEST_AET = 12;
    static final int DEST_NAP_ID = 13;
    static final int MOVEAET = 14;
    static final int WARNING = 15;
    static final int FAILED_IUID_SHOW = 16;
    static final int SOP_CUID = 17;
    static final int SOP_IUID = 18;
    static final int MPPS_UID = 19;
    static final int HL7_MESSAGE_TYPE = 20;

    private final String[] fields;

    AuditInfo(BuildAuditInfo i) {
        fields = new String[] {
                i.callingHost,
                i.callingAET,
                i.calledAET,
                i.calledHost,
                i.studyUID,
                i.accNum,
                i.pID,
                i.pName,
                i.outcome,
                i.studyDate,
                i.queryPOID,
                i.queryString,
                i.destAET,
                i.destNapID,
                i.moveAET,
                i.warning,
                i.failedIUIDShow ? Boolean.toString(i.failedIUIDShow) : null,
                i.sopCUID,
                i.sopIUID,
                i.mppsUID,
                i.hl7MessageType
        };
    }

    AuditInfo(String s) {
        fields = StringUtils.split(s, '\\');
    }
    String getField(int field) {
        return StringUtils.maskEmpty(fields[field], null);
    }
    @Override
    public String toString() {
        return StringUtils.concat(fields, '\\');
    }
}
