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
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2018
 */
class DeletionAuditService extends AuditService {

    static void audit(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        EventIdentification eventIdentification = getEventIdentification(auditInfo, eventType);
        eventIdentification.setEventDateTime(getEventTime(path, auditLogger));

        ParticipantObjectIdentification study = study(auditInfo, reader, auditLogger);
        ParticipantObjectIdentification patient = patient(auditInfo);

        if (eventType.eventClass == AuditUtils.EventClass.SCHEDULER_DELETED) {
            ActiveParticipant archive = archive(auditInfo.getField(AuditInfo.CALLING_USERID),
                                                AuditMessages.UserIDTypeCode.DeviceName,
                                                auditLogger);
            emitAuditMessage(auditLogger, eventIdentification, Collections.singletonList(archive), study, patient);
            return;
        }

        List<ActiveParticipant> activeParticipants = new ArrayList<>();
        String archiveCalledUserID = auditInfo.getField(AuditInfo.CALLED_USERID);
        activeParticipants.add(archive(archiveCalledUserID,
                                archiveCalledUserID.contains("/")
                                    ? AuditMessages.UserIDTypeCode.URI
                                    : AuditMessages.UserIDTypeCode.StationAETitle,
                                auditLogger));
        activeParticipants.add(archiveCalledUserID.contains("/")
                                ? requestor(auditInfo)
                                : requestorAE(auditInfo));
        if (auditInfo.getField(AuditInfo.DEST_USER_ID) != null)
            activeParticipants.add(remoteAE(auditInfo));

        emitAuditMessage(auditLogger, eventIdentification, activeParticipants, study, patient);
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

    private static ParticipantObjectIdentification study(AuditInfo auditInfo, SpoolFileReader reader, AuditLogger auditLogger) {
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
        InstanceInfo instanceInfo = instanceInfo(auditInfo, reader);
        boolean showSOPIUIDs = auditInfo.getField(AuditInfo.OUTCOME) != null || auditLogger.isIncludeInstanceUID();
        study.setParticipantObjectDescription(studyParticipantObjDesc(instanceInfo, showSOPIUIDs));
        return study;
    }

    private static InstanceInfo instanceInfo(AuditInfo auditInfo, SpoolFileReader reader) {
        InstanceInfo instanceInfo = new InstanceInfo();
        instanceInfo.setAccessionNo(auditInfo.getField(AuditInfo.ACC_NUM));
        reader.getInstanceLines().forEach(instanceLine -> {
            AuditInfo info = new AuditInfo(instanceLine);
            instanceInfo.addSOPInstance(info);
        });
        return instanceInfo;
    }

    private static ParticipantObjectDescription studyParticipantObjDesc(InstanceInfo instanceInfo, boolean showSOPIUIDs) {
        Accession accession = new Accession();
        accession.setNumber(instanceInfo.getAccessionNo());
        List<SOPClass> sopClasses = instanceInfo.getSopClassMap()
                                                .entrySet()
                                                .stream()
                                                .map(entry ->
                                                        AuditMessages.createSOPClass(
                                                                showSOPIUIDs ? entry.getValue() : null,
                                                                entry.getKey(),
                                                                entry.getValue().size()))
                                                .collect(Collectors.toList());

        ParticipantObjectDescription studyParticipantObjDesc = new ParticipantObjectDescription();
        studyParticipantObjDesc.getAccession().add(accession);
        studyParticipantObjDesc.getSOPClass().addAll(sopClasses);
        return studyParticipantObjDesc;
    }

    private static ActiveParticipant archive(
            String archiveUserID, AuditMessages.UserIDTypeCode archiveUserIDTypeCode, AuditLogger auditLogger) {
        ActiveParticipant archive = new ActiveParticipant();
        archive.setUserID(archiveUserID);
        archive.setUserIDTypeCode(archiveUserIDTypeCode);
        archive.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        archive.setAlternativeUserID(AuditLogger.processID());
        archive.setUserIsRequestor(archiveUserIDTypeCode == AuditMessages.UserIDTypeCode.DeviceName);

        String auditLoggerHostName = auditLogger.getConnections().get(0).getHostname();
        archive.setNetworkAccessPointID(auditLoggerHostName);
        archive.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(auditLoggerHostName)
                        ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                        : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        return archive;
    }

    private static ActiveParticipant requestor(AuditInfo auditInfo) {
        ActiveParticipant requestor = new ActiveParticipant();
        String requestorID = auditInfo.getField(AuditInfo.CALLING_USERID);
        requestor.setUserID(requestorID);
        requestor.setUserIsRequestor(true);
        boolean requestorIsIP = AuditMessages.isIP(requestorID);
        requestor.setUserIDTypeCode(requestorIsIP
                    ? AuditMessages.UserIDTypeCode.NodeID
                    : AuditMessages.UserIDTypeCode.PersonID);
        requestor.setUserTypeCode(requestorIsIP
                    ? AuditMessages.UserTypeCode.Application
                    : AuditMessages.UserTypeCode.Person);

        String requestorHost = auditInfo.getField(AuditInfo.CALLING_HOST);
        requestor.setNetworkAccessPointID(requestorHost);
        requestor.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(requestorHost)
                        ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                        : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        return requestor;
    }

    private static ActiveParticipant requestorAE(AuditInfo auditInfo) {
        ActiveParticipant requestorAE = new ActiveParticipant();
        String requestorID = auditInfo.getField(AuditInfo.CALLING_USERID);
        requestorAE.setUserID(requestorID);
        requestorAE.setUserIsRequestor(true);
        requestorAE.setUserIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle);
        requestorAE.setUserTypeCode(AuditMessages.UserTypeCode.Application);

        String requestorHost = auditInfo.getField(AuditInfo.CALLING_HOST);
        requestorAE.setNetworkAccessPointID(requestorHost);
        requestorAE.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(requestorHost)
                        ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                        : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        return requestorAE;
    }

    private static ActiveParticipant remoteAE(AuditInfo auditInfo) {
        ActiveParticipant remoteAE = new ActiveParticipant();
        remoteAE.setUserID(auditInfo.getField(AuditInfo.DEST_USER_ID));
        remoteAE.setUserIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle);
        remoteAE.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        String remoteAEHost = auditInfo.getField(AuditInfo.DEST_NAP_ID);
        remoteAE.setNetworkAccessPointID(remoteAEHost);
        remoteAE.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(remoteAEHost)
                        ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                        : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        remoteAE.setUserIsRequestor(false);
        return remoteAE;
    }

    private static EventIdentification getEventIdentification(AuditInfo auditInfo, AuditUtils.EventType eventType) {
        String outcome = auditInfo.getField(AuditInfo.OUTCOME);
        String warning = auditInfo.getField(AuditInfo.WARNING);
        String outcomeDesc = warning == null
                ? outcome
                : outcome == null
                    ? warning
                    : warning + " - " + outcome;

        EventIdentification ei = new EventIdentification();
        ei.setEventID(eventType.eventID);
        ei.setEventActionCode(eventType.eventActionCode);
        ei.setEventOutcomeDescription(outcomeDesc);
        ei.setEventOutcomeIndicator(outcome == null
                ? AuditMessages.EventOutcomeIndicator.Success
                : AuditMessages.EventOutcomeIndicator.MinorFailure);
        return ei;
    }
}