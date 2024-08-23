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
 * Portions created by the Initial Developer are Copyright (C) 2015-2023
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
import org.dcm4che3.data.Code;
import org.dcm4che3.net.audit.AuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Nov 2023
 */
class StoreAuditService extends AuditService {
    private final static Logger LOG = LoggerFactory.getLogger(StoreAuditService.class);

    static void audit(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());

        List<ActiveParticipant> activeParticipants = new ArrayList<>();
        activeParticipants.add(archive(auditInfo, eventType, auditLogger));
        if (auditInfo.getField(AuditInfo.CALLING_HOST) != null)
            activeParticipants.add(requestor(auditInfo, eventType));
        if (auditInfo.getField(AuditInfo.IMPAX_ENDPOINT) != null)
            activeParticipants.add(impax(auditInfo, eventType));

        boolean error = path.toFile().getName().endsWith("_ERROR");
        InstanceInfo instanceInfo = error
                                        ? instanceInfoForError(auditInfo, reader)
                                        : instanceInfo(auditInfo, reader);
        EventIdentification eventIdentification = error
                                                    ? getEventIdentificationForError(eventType, instanceInfo)
                                                    : getEventIdentification(eventType);
        eventIdentification.setEventDateTime(getEventTime(path, auditLogger));

        ParticipantObjectIdentification study = study(auditInfo, instanceInfo, auditLogger.isIncludeInstanceUID());
        ParticipantObjectIdentification patient = patient(auditInfo);
        emitAuditMessage(auditLogger, eventIdentification, activeParticipants, study, patient);
    }

    private static EventIdentification getEventIdentification(AuditUtils.EventType eventType) {
        EventIdentification ei = new EventIdentification();
        ei.setEventID(eventType.eventID);
        ei.setEventActionCode(eventType.eventActionCode);
        ei.setEventOutcomeIndicator(AuditMessages.EventOutcomeIndicator.Success);
        return ei;
    }

    private static EventIdentification getEventIdentificationForError(AuditUtils.EventType eventType, InstanceInfo instanceInfo) {
        EventIdentification ei = new EventIdentification();
        ei.setEventID(eventType.eventID);
        ei.setEventActionCode(eventType.eventActionCode);
        ei.setEventOutcomeIndicator(AuditMessages.EventOutcomeIndicator.MinorFailure);
        ei.setEventOutcomeDescription(String.join("\n", instanceInfo.getOutcomes()));
        ei.getEventTypeCode().addAll(instanceInfo.getErrorCodes());
        return ei;
    }

    private static ActiveParticipant archive(AuditInfo auditInfo, AuditUtils.EventType eventType, AuditLogger auditLogger) {
        String archiveUserID = auditInfo.getField(AuditInfo.CALLED_USERID);
        boolean impaxReportExportRuleTrigger = auditInfo.getField(AuditInfo.CALLING_HOST) == null;
        AuditMessages.UserIDTypeCode archiveUserIDTypeCode = impaxReportExportRuleTrigger
                                                               ? AuditMessages.UserIDTypeCode.DeviceName
                                                               : archiveUserID.contains("/")
                                                                 ? AuditMessages.UserIDTypeCode.URI
                                                                 : archiveUserID.contains("|")
                                                                   ? AuditMessages.UserIDTypeCode.ApplicationFacility
                                                                   : AuditMessages.UserIDTypeCode.StationAETitle;

        ActiveParticipant archive = new ActiveParticipant();
        archive.setUserID(archiveUserID);
        archive.setAlternativeUserID(AuditLogger.processID());
        archive.setUserIsRequestor(impaxReportExportRuleTrigger);
        archive.setUserIDTypeCode(archiveUserIDTypeCode);
        archive.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        archive.getRoleIDCode().add(eventType.destination);

        String auditLoggerHostName = auditLogger.getConnections().get(0).getHostname();
        archive.setNetworkAccessPointID(auditLoggerHostName);
        archive.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(auditLoggerHostName)
                        ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                        : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        return archive;
    }

    private static ActiveParticipant requestor(AuditInfo auditInfo, AuditUtils.EventType eventType) {
        String archiveUserID = auditInfo.getField(AuditInfo.CALLED_USERID);
        String requestorUserID = auditInfo.getField(AuditInfo.CALLING_USERID);
        String requestorHost = auditInfo.getField(AuditInfo.CALLING_HOST);

        AuditMessages.UserIDTypeCode requestorUserIDTypeCode = requestorUserID.contains("|")
                ? AuditMessages.UserIDTypeCode.ApplicationFacility
                : archiveUserID.contains("/")
                    ? AuditMessages.isIP(requestorUserID)
                        ? AuditMessages.UserIDTypeCode.NodeID
                        : AuditMessages.UserIDTypeCode.PersonID
                    : AuditMessages.UserIDTypeCode.StationAETitle;

        ActiveParticipant requestor = new ActiveParticipant();
        requestor.setUserID(requestorUserID);
        requestor.setNetworkAccessPointID(requestorHost);
        requestor.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(requestorHost)
                        ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                        : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        requestor.setUserIsRequestor(true);
        requestor.setUserIDTypeCode(requestorUserIDTypeCode);
        requestor.setUserTypeCode(requestorUserIDTypeCode == AuditMessages.UserIDTypeCode.PersonID
                                    ? AuditMessages.UserTypeCode.Person
                                    : AuditMessages.UserTypeCode.Application);
        if (auditInfo.getField(AuditInfo.IMPAX_ENDPOINT) == null)
            requestor.getRoleIDCode().add(eventType.source);

        return requestor;
    }

    private static ActiveParticipant impax(AuditInfo auditInfo, AuditUtils.EventType eventType) {
        ActiveParticipant impax = new ActiveParticipant();
        String impaxEndpoint = auditInfo.getField(AuditInfo.IMPAX_ENDPOINT);
        String impaxEndpointRelative = impaxEndpoint.substring(impaxEndpoint.indexOf("//") + 2);
        String impaxHost = impaxEndpointRelative.substring(0, impaxEndpointRelative.indexOf('/'));
        impax.setUserID(impaxEndpoint);
        impax.setNetworkAccessPointID(impaxHost);
        impax.setNetworkAccessPointTypeCode(AuditMessages.isIP(impaxHost)
                                                ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                                                : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        impax.setUserIsRequestor(false);
        impax.setUserIDTypeCode(AuditMessages.UserIDTypeCode.URI);
        impax.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        impax.getRoleIDCode().add(eventType.source);
        return impax;
    }

    private static ParticipantObjectIdentification study(AuditInfo auditInfo, InstanceInfo instanceInfo, boolean showSOPIUIDs) {
        ParticipantObjectIdentification study = new ParticipantObjectIdentification();
        study.setParticipantObjectID(auditInfo.getField(AuditInfo.STUDY_UID));
        study.setParticipantObjectIDTypeCode(AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID);
        study.setParticipantObjectTypeCode(AuditMessages.ParticipantObjectTypeCode.SystemObject);
        study.setParticipantObjectTypeCodeRole(AuditMessages.ParticipantObjectTypeCodeRole.Report);
        study.getParticipantObjectDetail()
                .add(AuditMessages.createParticipantObjectDetail("StudyDate", auditInfo.getField(AuditInfo.STUDY_DATE)));
        study.setParticipantObjectDescription(studyParticipantObjDesc(instanceInfo, showSOPIUIDs));
        study.setParticipantObjectDataLifeCycle(AuditMessages.ParticipantObjectDataLifeCycle.OriginationCreation);
        return study;
    }

    private static InstanceInfo instanceInfo(AuditInfo auditInfo, SpoolFileReader reader) {
        InstanceInfo instanceInfo = new InstanceInfo();
        instanceInfo.setAccessionNo(auditInfo.getField(AuditInfo.ACC_NUM));
        reader.getInstanceLines().forEach(instanceLine -> {
            AuditInfo info = new AuditInfo(instanceLine);
            instanceInfo.addMpps(info);
            instanceInfo.addSOPInstance(info);
        });
        return instanceInfo;
    }

    private static InstanceInfo instanceInfoForError(AuditInfo auditInfo, SpoolFileReader reader) {
        InstanceInfo instanceInfo = new InstanceInfo();
        instanceInfo.setAccessionNo(auditInfo.getField(AuditInfo.ACC_NUM));
        reader.getInstanceLines().forEach(instanceLine -> {
            AuditInfo info = new AuditInfo(instanceLine);
            instanceInfo.getOutcomes().add(info.getField(AuditInfo.OUTCOME));
            AuditMessages.EventTypeCode errorEventTypeCode = AuditUtils.errorEventTypeCode(info.getField(AuditInfo.ERROR_CODE));
            if (errorEventTypeCode != null)
                instanceInfo.getErrorCodes().add(errorEventTypeCode);
            
            instanceInfo.addMpps(info);
            instanceInfo.addSOPInstance(info);
        });
        return instanceInfo;
    }

    private static ParticipantObjectDescription studyParticipantObjDesc(InstanceInfo instanceInfo, boolean showSOPIUIDs) {
        ParticipantObjectDescription studyParticipantObjDesc = new ParticipantObjectDescription();
        studyParticipantObjDesc.getAccession().add(accession(instanceInfo));
        studyParticipantObjDesc.getSOPClass().addAll(sopClasses(instanceInfo, showSOPIUIDs));
        instanceInfo.getMpps().forEach(mppsUID -> studyParticipantObjDesc.getMPPS().add(mpps(mppsUID)));
        return studyParticipantObjDesc;
    }

    private static Accession accession(InstanceInfo instanceInfo) {
        Accession accession = new Accession();
        accession.setNumber(instanceInfo.getAccessionNo());
        return accession;
    }

    private static MPPS mpps(String mppsUID) {
        MPPS mpps = new MPPS();
        mpps.setUID(mppsUID);
        return mpps;
    }

    private static List<SOPClass> sopClasses(InstanceInfo instanceInfo, boolean showSOPIUIDs) {
        return instanceInfo.getSopClassMap()
                .entrySet()
                .stream()
                .map(entry ->
                        AuditMessages.createSOPClass(
                                showSOPIUIDs ? entry.getValue() : null,
                                entry.getKey(),
                                entry.getValue().size()))
                .collect(Collectors.toList());
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

    static void auditImpaxReportPatientMismatch(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        EventIdentification eventIdentification = getEventIdentificationPatientMismatch(auditInfo, eventType);
        eventIdentification.setEventDateTime(getEventTime(path, auditLogger));
        List<ActiveParticipant> activeParticipants = new ArrayList<>();
        activeParticipants.add(archive(auditInfo, eventType, auditLogger));
        activeParticipants.add(impax(auditInfo));
        if (auditInfo.getField(AuditInfo.CALLING_HOST) != null)
            activeParticipants.add(requestor(auditInfo, eventType));
        InstanceInfo instanceInfo = instanceInfo(auditInfo, reader);
        ParticipantObjectIdentification study = study(auditInfo, instanceInfo, auditLogger.isIncludeInstanceUID());
        ParticipantObjectIdentification patient = patient(auditInfo);
        emitAuditMessage(auditLogger, eventIdentification, activeParticipants, study, patient);
    }

    private static EventIdentification getEventIdentificationPatientMismatch(
            AuditInfo auditInfo, AuditUtils.EventType eventType) {
        EventIdentification ei = new EventIdentification();
        ei.setEventID(eventType.eventID);
        ei.setEventActionCode(eventType.eventActionCode);
        ei.setEventOutcomeIndicator(AuditMessages.EventOutcomeIndicator.MinorFailure);
        ei.getEventTypeCode().add(patMismatchEventTypeCode(auditInfo));
        return ei;
    }

    private static AuditMessages.EventTypeCode patMismatchEventTypeCode(AuditInfo auditInfo) {
        String patMismatchCode = auditInfo.getField(AuditInfo.PAT_MISMATCH_CODE);
        try {
            Code code = new Code(patMismatchCode);
            return new AuditMessages.EventTypeCode(
                    code.getCodeValue(),
                    code.getCodingSchemeDesignator(),
                    code.getCodeMeaning());
        } catch (Exception e) {
            LOG.info("Invalid patient mismatch code: {}", patMismatchCode);
        }
        return null;
    }

    private static ActiveParticipant impax(AuditInfo auditInfo){
        ActiveParticipant impax = new ActiveParticipant();
        String impaxEndpoint = auditInfo.getField(AuditInfo.IMPAX_ENDPOINT);
        String impaxEndpointRelative = impaxEndpoint.substring(impaxEndpoint.indexOf("//") + 2);
        String impaxEndpointHost = impaxEndpointRelative.substring(0, impaxEndpointRelative.indexOf('/'));
        impax.setUserID(impaxEndpoint);
        impax.setUserIDTypeCode(AuditMessages.UserIDTypeCode.URI);
        impax.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        impax.setNetworkAccessPointID(impaxEndpointHost);
        impax.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(impaxEndpointHost)
                    ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                    : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        return impax;
    }
}
