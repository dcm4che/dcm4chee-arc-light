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
 * *** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.audit;

import org.dcm4che3.audit.*;
import org.dcm4che3.net.audit.AuditLogger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2019
 */
class StudyRecordAuditService extends AuditService {

    static void audit(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
        if (eventType.eventActionCode.equals(AuditMessages.EventActionCode.Read)) {
            QueryAuditService.auditStudySize(auditLogger, path, eventType);
            return;
        }

        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        EventIdentification eventIdentification = getEventIdentification(auditInfo, eventType);
        eventIdentification.setEventDateTime(getEventTime(path, auditLogger));
        List<ActiveParticipant> activeParticipants = new ArrayList<>();
        if (auditInfo.getField(AuditInfo.CALLING_USERID).contains("|")) {
            activeParticipants.add(requestorHL7App(auditInfo));
            activeParticipants.add(archiveHL7App(auditInfo, auditLogger));
        } else {
            activeParticipants.add(requestor(auditInfo));
            activeParticipants.add(archiveURI(auditInfo, auditLogger));
        }
        emitAuditMessage(auditLogger, eventIdentification, activeParticipants, study(auditInfo), patient(auditInfo));
    }

    private static EventIdentification getEventIdentification(AuditInfo auditInfo, AuditUtils.EventType eventType) {
        String outcome = auditInfo.getField(AuditInfo.OUTCOME);
        EventIdentification ei = new EventIdentification();
        ei.setEventID(eventType.eventID);
        ei.setEventActionCode(eventType.eventActionCode);
        ei.setEventOutcomeDescription(outcome);
        ei.setEventOutcomeIndicator(outcome == null
                ? AuditMessages.EventOutcomeIndicator.Success
                : AuditMessages.EventOutcomeIndicator.MinorFailure);
        return ei;
    }

    private static ParticipantObjectIdentification patient(AuditInfo auditInfo) {
        ParticipantObjectIdentification patient = new ParticipantObjectIdentification();
        patient.setParticipantObjectID(auditInfo.getField(AuditInfo.P_ID));
        patient.setParticipantObjectIDTypeCode(AuditMessages.ParticipantObjectIDTypeCode.PatientNumber);
        patient.setParticipantObjectTypeCode(AuditMessages.ParticipantObjectTypeCode.Person);
        patient.setParticipantObjectTypeCodeRole(AuditMessages.ParticipantObjectTypeCodeRole.Patient);
        patient.setParticipantObjectName(auditInfo.getField(AuditInfo.P_NAME));
        return patient;
    }

    private static ParticipantObjectIdentification study(AuditInfo auditInfo) {
        ParticipantObjectIdentification study = new ParticipantObjectIdentification();
        study.setParticipantObjectID(auditInfo.getField(AuditInfo.STUDY_UID));
        study.setParticipantObjectIDTypeCode(AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID);
        study.setParticipantObjectTypeCode(AuditMessages.ParticipantObjectTypeCode.SystemObject);
        study.setParticipantObjectTypeCodeRole(AuditMessages.ParticipantObjectTypeCodeRole.Report);
        study.getParticipantObjectDetail().add(
                AuditMessages.createParticipantObjectDetail("StudyDate", auditInfo.getField(AuditInfo.STUDY_DATE)));
        study.getParticipantObjectDetail().add(
                AuditMessages.createParticipantObjectDetail("StudyDescription", auditInfo.getField(AuditInfo.STUDY_DESC)));
        study.getParticipantObjectDetail().add(
                AuditMessages.createParticipantObjectDetail("SeriesDescription", auditInfo.getField(AuditInfo.SERIES_DESC)));
        study.getParticipantObjectDetail().add(
                AuditMessages.createParticipantObjectDetail("Modality", auditInfo.getField(AuditInfo.MODALITY)));
        String expirationDate = auditInfo.getField(AuditInfo.EXPIRATION_DATE);
        if (expirationDate != null)
            study.getParticipantObjectDetail()
                .add(AuditMessages.createParticipantObjectDetail("ExpirationDate", expirationDate));
        String accessionNo = auditInfo.getField(AuditInfo.ACC_NUM);
        if (accessionNo != null)
            study.setParticipantObjectDescription(studyParticipantObjDesc(accessionNo));
        return study;
    }

    private static ParticipantObjectDescription studyParticipantObjDesc(String accessionNo) {
        ParticipantObjectDescription studyParticipantObjDesc = new ParticipantObjectDescription();
        studyParticipantObjDesc.getAccession().add(AuditMessages.createAccession(accessionNo));
        return studyParticipantObjDesc;
    }

    private static ActiveParticipant requestor(AuditInfo auditInfo) {
        ActiveParticipant requestor = new ActiveParticipant();
        String requestorUserID = auditInfo.getField(AuditInfo.CALLING_USERID);
        requestor.setUserID(requestorUserID);
        boolean requestorIsIP = AuditMessages.isIP(requestorUserID);
        requestor.setUserIDTypeCode(requestorIsIP
                                        ? AuditMessages.UserIDTypeCode.NodeID
                                        : AuditMessages.UserIDTypeCode.PersonID);
        requestor.setUserTypeCode(requestorIsIP
                                    ? AuditMessages.UserTypeCode.Application
                                    : AuditMessages.UserTypeCode.Person);
        String requestorHL7AppHost = auditInfo.getField(AuditInfo.CALLING_HOST);
        requestor.setNetworkAccessPointID(requestorHL7AppHost);
        requestor.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(requestorHL7AppHost)
                        ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                        : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        requestor.setUserIsRequestor(true);
        return requestor;
    }

    private static ActiveParticipant archiveURI(AuditInfo auditInfo, AuditLogger auditLogger) {
        ActiveParticipant archive = new ActiveParticipant();
        archive.setUserID(auditInfo.getField(AuditInfo.CALLED_USERID));
        archive.setUserIDTypeCode(AuditMessages.UserIDTypeCode.URI);
        archive.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        archive.setAlternativeUserID(AuditLogger.processID());
        String auditLoggerHostName = auditLogger.getConnections().get(0).getHostname();
        archive.setNetworkAccessPointID(auditLoggerHostName);
        archive.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(auditLoggerHostName)
                        ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                        : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        return archive;
    }

    private static ActiveParticipant requestorHL7App(AuditInfo auditInfo) {
        ActiveParticipant requestorHL7App = new ActiveParticipant();
        requestorHL7App.setUserID(auditInfo.getField(AuditInfo.CALLING_USERID));
        requestorHL7App.setUserIDTypeCode(AuditMessages.UserIDTypeCode.ApplicationFacility);
        requestorHL7App.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        String requestorHL7AppHost = auditInfo.getField(AuditInfo.CALLING_HOST);
        requestorHL7App.setNetworkAccessPointID(requestorHL7AppHost);
        requestorHL7App.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(requestorHL7AppHost)
                        ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                        : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        requestorHL7App.setUserIsRequestor(true);
        return requestorHL7App;
    }

    private static ActiveParticipant archiveHL7App(AuditInfo auditInfo, AuditLogger auditLogger) {
        ActiveParticipant archive = new ActiveParticipant();
        archive.setUserID(auditInfo.getField(AuditInfo.CALLED_USERID));
        archive.setUserIDTypeCode(AuditMessages.UserIDTypeCode.ApplicationFacility);
        archive.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        archive.setAlternativeUserID(AuditLogger.processID());
        String auditLoggerHostName = auditLogger.getConnections().get(0).getHostname();
        archive.setNetworkAccessPointID(auditLoggerHostName);
        archive.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(auditLoggerHostName)
                        ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                        : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        return archive;
    }
}
