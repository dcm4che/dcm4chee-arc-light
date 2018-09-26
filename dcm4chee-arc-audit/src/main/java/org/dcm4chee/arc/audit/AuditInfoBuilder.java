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

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.data.Tag;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.ShowPatientInfo;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since June 2016
 */

class AuditInfoBuilder {
    final String callingHost;
    final String callingUserID;
    final String calledUserID;
    final String calledHost;
    final String studyUID;
    final String accNum;
    final String pID;
    final String pName;
    final String outcome;
    final String studyDate;
    final String sopCUID;
    final String sopIUID;
    final String mppsUID;
    final String queryPOID;
    final String queryString;
    final String destUserID;
    final String destNapID;
    final String moveUserID;
    final String warning;
    final boolean failedIUIDShow;
    final String submissionSetUID;
    final boolean isExport;
    final boolean isOutgoingHL7;
    final String outgoingHL7Sender;
    final String outgoingHL7Receiver;
    final String filters;
    final int count;
    final String queueMsg;
    final String taskPOID;
    final String errorCode;

    static class Builder {
        private String callingHost;
        private String callingUserID;
        private String calledUserID;
        private String calledHost;
        private String studyUID;
        private String accNum;
        private String pID;
        private String pName;
        private String outcome;
        private String studyDate;
        private String sopCUID;
        private String sopIUID;
        private String mppsUID;
        private String queryPOID;
        private String queryString;
        private String destUserID;
        private String destNapID;
        private String moveUserID;
        private String warning;
        private boolean failedIUIDShow;
        private String submissionSetUID;
        private boolean isExport;
        private boolean isOutgoingHL7;
        private String outgoingHL7Sender;
        private String outgoingHL7Receiver;
        private String filters;
        private int count;
        private String queueMsg;
        private String taskPOID;
        private String errorCode;

        Builder callingHost(String val) {
            callingHost = val;
            return this;
        }
        Builder callingUserID(String val) {
            callingUserID = val;
            return this;
        }
        Builder calledUserID(String val) {
            calledUserID = val;
            return this;
        }
        Builder calledHost(String val) {
            calledHost = val;
            return this;
        }
        Builder studyUID(String val) {
            studyUID = val;
            return this;
        }
        Builder pIDAndName(Attributes attr, ArchiveDeviceExtension arcDev) {
            IDWithIssuer pidWithIssuer = IDWithIssuer.pidOf(attr);
            pID = pidWithIssuer == null ? arcDev.auditUnknownPatientID() : toPID(pidWithIssuer, arcDev);
            pName = toPatName(attr.getString(Tag.PatientName), arcDev);
            return this;
        }
        Builder patID(String pid, ArchiveDeviceExtension arcDev) {
            pID = pid == null ? arcDev.auditUnknownPatientID() : toPID(new IDWithIssuer(pid), arcDev);
            return this;
        }
        Builder patName(String patName, ArchiveDeviceExtension arcDev) {
            pName = toPatName(patName, arcDev);
            return this;
        }
        Builder studyUIDAccNumDate(Attributes attrs) {
            studyUID = attrs.getString(Tag.StudyInstanceUID);
            accNum = attrs.getString(Tag.AccessionNumber);
            studyDate = attrs.getString(Tag.StudyDate);
            return this;
        }
        Builder studyIUID(String studyIUID) {
            studyUID = studyIUID;
            return this;
        }
        Builder accNum(String acc) {
            accNum = acc;
            return this;
        }
        Builder outcome(String val) {
            outcome = val;
            return this;
        }
        Builder sopCUID(String val) {
            sopCUID = val;
            return this;
        }
        Builder sopIUID(String val) {
            sopIUID = val;
            return this;
        }
        Builder mppsUID(String val) {
            mppsUID = val;
            return this;
        }
        Builder queryPOID(String val) {
            queryPOID = val;
            return this;
        }
        Builder queryString(String val) {
            queryString = val;
            return this;
        }
        Builder destUserID(String val) {
            destUserID = val;
            return this;
        }
        Builder destNapID(String val) {
            destNapID = val;
            return this;
        }
        Builder moveUserID(String val) {
            moveUserID = val;
            return this;
        }
        Builder warning(String val) {
            warning = val;
            return this;
        }
        Builder failedIUIDShow(boolean val) {
            failedIUIDShow = val;
            return this;
        }
        Builder submissionSetUID(String val) {
            submissionSetUID = val;
            return this;
        }
        Builder isExport() {
            isExport = true;
            return this;
        }
        Builder isOutgoingHL7() {
            isOutgoingHL7 = true;
            return this;
        }
        Builder outgoingHL7Sender(String val) {
            outgoingHL7Sender = val;
            return this;
        }
        Builder outgoingHL7Receiver(String val) {
            outgoingHL7Receiver = val;
            return this;
        }
        Builder filters(String val) {
            filters = val;
            return this;
        }
        Builder count(int val) {
            count = val;
            return this;
        }
        Builder queueMsg(String val) {
            queueMsg = val;
            return this;
        }
        Builder taskPOID(String val) {
            taskPOID = val;
            return this;
        }
        Builder errorCode(int val) {
            errorCode = errorCodeAsString(val);
            return this;
        }
        AuditInfoBuilder build() {
            return new AuditInfoBuilder(this);
        }
    }

    private AuditInfoBuilder(Builder builder) {
        callingHost = builder.callingHost;
        callingUserID = builder.callingUserID;
        calledUserID = builder.calledUserID;
        calledHost = builder.calledHost;
        studyUID = builder.studyUID;
        accNum = builder.accNum;
        pID = builder.pID;
        pName = builder.pName;
        outcome = builder.outcome;
        studyDate = builder.studyDate;
        sopCUID = builder.sopCUID;
        sopIUID = builder.sopIUID;
        mppsUID = builder.mppsUID;
        queryPOID = builder.queryPOID;
        queryString = builder.queryString;
        destUserID = builder.destUserID;
        destNapID = builder.destNapID;
        moveUserID = builder.moveUserID;
        warning = builder.warning;
        failedIUIDShow = builder.failedIUIDShow;
        submissionSetUID = builder.submissionSetUID;
        isExport = builder.isExport;
        isOutgoingHL7 = builder.isOutgoingHL7;
        outgoingHL7Sender = builder.outgoingHL7Sender;
        outgoingHL7Receiver = builder.outgoingHL7Receiver;
        filters = builder.filters;
        count = builder.count;
        queueMsg = builder.queueMsg;
        taskPOID = builder.taskPOID;
        errorCode = builder.errorCode;
    }

    private static String toPID(IDWithIssuer pidWithIssuer, ArchiveDeviceExtension arcDev) {
        return arcDev.showPatientInfoInAuditLog() == ShowPatientInfo.HASH_NAME_AND_ID
                ? String.valueOf(pidWithIssuer.hashCode())
                : pidWithIssuer.toString();
    }

    private static String toPatName(String pName, ArchiveDeviceExtension arcDev) {
        return pName != null && arcDev.showPatientInfoInAuditLog() != ShowPatientInfo.PLAIN_TEXT
                ? String.valueOf(pName.hashCode())
                : pName;
    }

    private static String errorCodeAsString(int errorCode) {
        String errorCodeAsString = Integer.toHexString(errorCode).toUpperCase();
        return errorCodeAsString.length() == 3 ? "x0" + errorCodeAsString : errorCodeAsString;
    }
}
