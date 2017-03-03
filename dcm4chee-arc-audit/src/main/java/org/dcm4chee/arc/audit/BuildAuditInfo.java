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

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since June 2016
 */

class BuildAuditInfo {
    final String callingHost;
    final String callingAET;
    final String calledAET;
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
    final String destAET;
    final String destNapID;
    final String moveAET;
    final String warning;
    final boolean failedIUIDShow;
    final String hl7MessageType;

    static class Builder {
        private String callingHost;
        private String callingAET;
        private String calledAET;
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
        private String destAET;
        private String destNapID;
        private String moveAET;
        private String warning;
        private boolean failedIUIDShow;
        private String hl7MessageType;

        Builder callingHost(String val) {
            callingHost = val;
            return this;
        }
        Builder callingAET(String val) {
            callingAET = val;
            return this;
        }
        Builder calledAET(String val) {
            calledAET = val;
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
        Builder accNum(String val) {
            accNum = val;
            return this;
        }
        Builder pID(String val) {
            pID = val;
            return this;
        }
        Builder pName(String val) {
            pName = val;
            return this;
        }
        Builder outcome(String val) {
            outcome = val;
            return this;
        }
        Builder studyDate(String val) {
            studyDate = val;
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
        Builder destAET(String val) {
            destAET = val;
            return this;
        }
        Builder destNapID(String val) {
            destNapID = val;
            return this;
        }
        Builder moveAET(String val) {
            moveAET = val;
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
        Builder hl7MessageType(String val) {
            hl7MessageType = val;
            return this;
        }
        BuildAuditInfo build() {
            return new BuildAuditInfo(this);
        }
    }

    private BuildAuditInfo(Builder builder) {
        callingHost = builder.callingHost;
        callingAET = builder.callingAET;
        calledAET = builder.calledAET;
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
        destAET = builder.destAET;
        destNapID = builder.destNapID;
        moveAET = builder.moveAET;
        warning = builder.warning;
        failedIUIDShow = builder.failedIUIDShow;
        hl7MessageType = builder.hl7MessageType;
    }
}
