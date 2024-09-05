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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since June 2016
 */

class AuditInfo {

    private final static Logger LOG = LoggerFactory.getLogger(AuditInfo.class);

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
    static final int C_MOVE_ORIGINATOR = 14;
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
    static final int TASK = 27;
    static final int TASK_POID = 28;
    static final int ERROR_CODE = 29;
    static final int PAT_MISMATCH_CODE = 30;
    static final int SERVICE_EVENT_TYPE = 31;
    static final int PAT_VERIFICATION_STATUS = 32;
    static final int PDQ_SERVICE_URI = 33;
    static final int IMPAX_ENDPOINT = 34;
    static final int FAILED = 35;
    static final int EXPIRATION_DATE = 36;
    static final int FIND_SCP = 37;
    static final int QUEUE_NAME = 38;
    static final int STATUS = 39;
    static final int FHIR_WEB_APP_NAME = 40;
    static final int ARCHIVE_USER_ID = 41;
    static final int QR_LEVEL = 42;
    static final int STUDY_DESC = 43;
    static final int SERIES_DESC = 44;
    static final int MODALITY = 45;

    private final String[] fields;

    AuditInfo(AuditInfoBuilder i) {
        fields = new String[] {
                encode(i.callingHost),
                encode(i.callingUserID),
                encode(i.calledUserID),
                encode(i.calledHost),
                encode(i.studyUID),
                encode(i.accNum),
                encode(i.pID),
                encode(i.pName),
                encode(i.outcome),
                encode(i.studyDate),
                encode(i.queryPOID),
                encode(i.queryString),
                encode(i.destUserID),
                encode(i.destNapID),
                encode(i.cMoveOriginator),
                encode(i.warning),
                encode(i.failedIUIDShow ? String.valueOf(true) : null),
                encode(i.sopCUID),
                encode(i.sopIUID),
                encode(i.mppsUID),
                encode(i.submissionSetUID),
                encode(i.isExport ? String.valueOf(true) : null),
                encode(i.isOutgoingHL7 ? String.valueOf(true) : null),
                encode(i.outgoingHL7Sender),
                encode(i.outgoingHL7Receiver),
                encode(i.filters),
                encode(String.valueOf(i.count)),
                encode(i.task),
                encode(i.taskPOID),
                encode(i.errorCode),
                encode(i.patMismatchCode),
                encode(i.serviceEventType),
                encode(i.patVerificationStatus != null ? i.patVerificationStatus.name() : null),
                encode(i.pdqServiceURI),
                encode(i.impaxEndpoint),
                encode(String.valueOf(i.failed)),
                encode(i.expirationDate),
                encode(i.findSCP),
                encode(i.queueName),
                encode(i.status),
                encode(i.fhirWebAppName),
                encode(i.archiveUserID),
                encode(i.qrLevel),
                encode(i.studyDesc),
                encode(i.seriesDesc),
                encode(i.modality)
        };
    }

    AuditInfo(String s) {
        fields = StringUtils.split(s, '\\');
    }

    String getField(int field) {
        return decode(StringUtils.maskEmpty(fields[field], null));
    }

    private static String decode(String val) {
        if (val == null)
            return null;

        return URLDecoder.decode(val, StandardCharsets.UTF_8);
    }

    private static String encode(String val) {
        if (val == null)
            return null;

        return URLEncoder.encode(val, StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return StringUtils.concat(fields, '\\');
    }
}
