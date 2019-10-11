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

package org.dcm4chee.arc.audit;

import org.dcm4che3.util.StringUtils;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since June 2016
 */

class AuditInfo {
    static final int CALLING_HOST = 0;
    static final int CALLING_USERID = 1;
    static final int CALLED_USERID = 2;
    static final int CALLED_HOST = 3;
    static final int STUDY_UID = 4;
    static final int ACC_NUM = 5;
    static final int P_ID = 6;
    static final int P_NAME = 7;
    static final int OUTCOME = 8;
    static final int STUDY_DATE = 9;
    static final int Q_POID = 10;
    static final int Q_STRING = 11;
    static final int DEST_USER_ID = 12;
    static final int DEST_NAP_ID = 13;
    static final int MOVE_USER_ID = 14;
    static final int WARNING = 15;
    static final int FAILED_IUID_SHOW = 16;
    static final int SOP_CUID = 17;
    static final int SOP_IUID = 18;
    static final int MPPS_UID = 19;
    static final int SUBMISSION_SET_UID = 20;
    static final int IS_EXPORT = 21;
    static final int IS_OUTGOING_HL7 = 22;
    static final int OUTGOING_HL7_SENDER = 23;
    static final int OUTGOING_HL7_RECEIVER = 24;
    static final int FILTERS = 25;
    static final int COUNT = 26;
    static final int QUEUE_MSG = 27;
    static final int TASK_POID = 28;
    static final int ERROR_CODE = 29;
    static final int PAT_MISMATCH_CODE = 30;
    static final int CONN_TYPE = 31;
    static final int PAT_VERIFICATION_STATUS = 32;
    static final int PDQ_SERVICE_URI = 33;
    static final int IMPAX_ENDPOINT = 34;
    static final int FAILED = 35;
    static final int EXPIRATION_DATE = 36;
    static final int STUDY_ACCESS_CTRL_ID = 37;

    private final String[] fields;

    AuditInfo(AuditInfoBuilder i) {
        fields = new String[] {
                i.callingHost,
                i.callingUserID,
                i.calledUserID,
                i.calledHost,
                i.studyUID,
                i.accNum,
                i.pID,
                i.pName,
                i.outcome,
                i.studyDate,
                i.queryPOID,
                i.queryString,
                i.destUserID,
                i.destNapID,
                i.moveUserID,
                i.warning,
                i.failedIUIDShow ? String.valueOf(true) : null,
                i.sopCUID,
                i.sopIUID,
                i.mppsUID,
                i.submissionSetUID,
                i.isExport ? String.valueOf(true) : null,
                i.isOutgoingHL7 ? String.valueOf(true) : null,
                i.outgoingHL7Sender,
                i.outgoingHL7Receiver,
                i.filters,
                String.valueOf(i.count),
                i.queueMsg,
                i.taskPOID,
                i.errorCode,
                i.patMismatchCode,
                i.connType != null ? i.connType.name() : null,
                i.patVerificationStatus != null ? i.patVerificationStatus.name() : null,
                i.pdqServiceURI,
                i.impaxEndpoint,
                String.valueOf(i.failed),
                i.expirationDate,
                i.studyAccessCtrlID
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
