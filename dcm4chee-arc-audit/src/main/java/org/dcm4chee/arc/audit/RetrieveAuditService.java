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
import org.dcm4che3.audit.AuditMessage;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.store.InstanceLocations;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2017
 */

class RetrieveAuditService extends AuditService {
    private final static Logger LOG = LoggerFactory.getLogger(RetrieveAuditService.class);
    private final RetrieveContext ctx;
    private final ArchiveDeviceExtension arcDev;
    private final HttpServletRequestInfo httpServletRequestInfo;
    private final String warningMsg;
    private final String failureMsg;

    RetrieveAuditService(RetrieveContext ctx, ArchiveDeviceExtension arcDev) {
        this.ctx = ctx;
        this.arcDev = arcDev;
        this.httpServletRequestInfo = ctx.getHttpServletRequestInfo();
        this.warningMsg = warningMsg();
        this.failureMsg = failureMsg();
    }

    Collection<InstanceLocations> failedMatches() {
        if (ctx.getFailedMatches().isEmpty()) {
            List<String> failedSOPIUIDs = Arrays.asList(ctx.failedSOPInstanceUIDs());
            List<InstanceLocations> failedMatches = ctx.getMatches().stream()
                                                        .filter(il -> failedSOPIUIDs.contains(il.getSopInstanceUID()))
                                                        .collect(Collectors.toList());
            if (failedMatches.isEmpty())
                LOG.info("Instance information not available for instances failed to be retrieved {}. Exit spooling of retrieve failures",
                        failedSOPIUIDs);

            return failedMatches;
        }
        return ctx.getFailedMatches();
    }

    Collection<InstanceLocations> completedMatches() {
        List<String> failedSOPIUIDs = Arrays.asList(ctx.failedSOPInstanceUIDs());
        return ctx.getMatches().stream()
                .filter(il -> !failedSOPIUIDs.contains(il.getSopInstanceUID()))
                .collect(Collectors.toList());
    }

    List<AuditInfoBuilder> createRetrieveSuccessAuditInfo(Collection<InstanceLocations> completedRetrieves) {
        List<AuditInfoBuilder> retrieveSuccess = new ArrayList<>();
        retrieveSuccess.add(createCompletedRetrieveInfo());
        ctx.getCStoreForwards().forEach(cStoreFwd -> retrieveSuccess.add(createInstanceAuditInfo(cStoreFwd)));
        completedRetrieves.forEach(completedRetrieve -> retrieveSuccess.add(createInstanceAuditInfo(completedRetrieve)));
        return retrieveSuccess;
    }

    private AuditInfoBuilder createCompletedRetrieveInfo() {
        AuditInfoBuilder.Builder retrieveInfo = new AuditInfoBuilder.Builder();
        retrieveInfo.warning(warningMsg);
        retrieveInfo.outcome(outcomeDesc());
        return addUserParticipantDetails(retrieveInfo);
    }

    List<AuditInfoBuilder> createRetrieveFailureAuditInfo(Collection<InstanceLocations> failedRetrieves) {
        List<AuditInfoBuilder> retrieveFailure = new ArrayList<>();
        retrieveFailure.add(createFailedRetrieveInfo());
        ctx.getCStoreForwards().forEach(cStoreFwd -> retrieveFailure.add(createInstanceAuditInfo(cStoreFwd)));
        failedRetrieves.forEach(failedRetrieve -> retrieveFailure.add(createInstanceAuditInfo(failedRetrieve)));
        return retrieveFailure;
    }

    private AuditInfoBuilder createFailedRetrieveInfo() {
        AuditInfoBuilder.Builder retrieveInfo = new AuditInfoBuilder.Builder();
        retrieveInfo.outcome(outcomeDesc());
        retrieveInfo.failedIUIDShow(true);
        return addUserParticipantDetails(retrieveInfo);
    }

    private AuditInfoBuilder addUserParticipantDetails(AuditInfoBuilder.Builder retrieveInfo) {
        return isExportTriggered(ctx)
                ? httpServletRequestInfo != null
                    ? restfulTriggeredExport(retrieveInfo)
                    : schedulerTriggeredExport(retrieveInfo)
                : httpServletRequestInfo != null
                    ? rad69OrWadoRS(retrieveInfo)
                    : cMoveCGet(retrieveInfo);
    }

    private AuditInfoBuilder createInstanceAuditInfo(InstanceLocations il) {
        Attributes attrs = il.getAttributes();
        return new AuditInfoBuilder.Builder()
                .studyUIDAccNumDate(attrs, arcDev)
                .sopCUID(attrs.getString(Tag.SOPClassUID))
                .sopIUID(attrs.getString(Tag.SOPInstanceUID))
                .pIDAndName(attrs, arcDev)
                .build();
    }

    private AuditInfoBuilder cMoveCGet(AuditInfoBuilder.Builder infoBuilder) {
        return infoBuilder
            .calledUserID(ctx.getLocalAETitle())
            .destUserID(ctx.getDestinationAETitle())
            .destNapID(ctx.getDestinationHostName())
            .callingHost(ctx.getRequestorHostName())
            .cMoveOriginator(ctx.getMoveOriginatorAETitle())
            .build();
    }

    private AuditInfoBuilder rad69OrWadoRS(AuditInfoBuilder.Builder infoBuilder) {
        return infoBuilder
            .calledUserID(requestURLWithQueryParams(httpServletRequestInfo))
            .destUserID(httpServletRequestInfo.requesterUserID)
            .destNapID(ctx.getDestinationHostName())
            .build();
    }

    private AuditInfoBuilder schedulerTriggeredExport(AuditInfoBuilder.Builder infoBuilder) {
        return infoBuilder
            .calledUserID(ctx.getLocalAETitle())
            .destUserID(ctx.getDestinationAETitle())
            .destNapID(ctx.getDestinationHostName())
            .callingHost(ctx.getRequestorHostName())
            .isExport()
            .build();
    }

    private AuditInfoBuilder restfulTriggeredExport(AuditInfoBuilder.Builder infoBuilder) {
        return infoBuilder
            .callingUserID(httpServletRequestInfo.requesterUserID)
            .callingHost(ctx.getRequestorHostName())
            .calledUserID(requestURLWithQueryParams(httpServletRequestInfo))
            .destUserID(ctx.getDestinationAETitle())
            .destNapID(ctx.getDestinationHostName())
            .isExport()
            .build();
    }

    private static String requestURLWithQueryParams(HttpServletRequestInfo httpServletRequestInfo) {
        return httpServletRequestInfo.queryString == null
                ? httpServletRequestInfo.requestURI
                : httpServletRequestInfo.requestURI + "?" + httpServletRequestInfo.queryString;
    }

    private boolean isExportTriggered(RetrieveContext ctx) {
        return (ctx.getRequestAssociation() == null && ctx.getStoreAssociation() != null)
                || (ctx.getRequestAssociation() == null && ctx.getStoreAssociation() == null && ctx.getException() != null);
    }

    private String warningMsg() {
        return ctx.warning() > 0
                ? "Warnings on retrieve of " + ctx.warning() + " instances"
                : null;
    }

    private String failureMsg() {
        return ctx.failed() > 0 || !ctx.getFailedMatches().isEmpty()
                ? "Retrieve of " + ctx.failed() + " objects failed"
                : null;
    }

    private String outcomeDesc() {
        if (warningMsg == null && failureMsg == null && ctx.getException() == null)
            return null;

        StringBuilder sb = new StringBuilder();
        if (warningMsg != null)
            sb.append(warningMsg).append("\n");
        if (failureMsg != null)
            sb.append(failureMsg).append("\n");
        if (ctx.getException() != null)
            sb.append(ctx.getException().toString()).append("\n");
        return sb.toString();
    }

    static AuditMessage auditMsg(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        HashMap<String, InstanceInfo> study_instanceInfo = new HashMap<>();
        for (String line : reader.getInstanceLines()) {
            AuditInfo rInfo = new AuditInfo(line);
            String studyInstanceUID = rInfo.getField(AuditInfo.STUDY_UID);
            InstanceInfo instanceInfo = study_instanceInfo.get(studyInstanceUID);
            if (instanceInfo == null) {
                instanceInfo = new InstanceInfo();
                instanceInfo.addAcc(rInfo);
                study_instanceInfo.put(studyInstanceUID, instanceInfo);
            }
            instanceInfo.addSOPInstance(rInfo);
            instanceInfo.addStudyDate(rInfo);
            study_instanceInfo.put(studyInstanceUID, instanceInfo);
        }
        List<ParticipantObjectIdentification> pois = new ArrayList<>();
        boolean showIUID = auditInfo.getField(AuditInfo.FAILED_IUID_SHOW) != null || auditLogger.isIncludeInstanceUID();
        study_instanceInfo.forEach(
                (studyUID, instanceInfo) -> pois.add(ParticipantObjectID.studyPOI(studyUID, instanceInfo, showIUID)));
        pois.add(ParticipantObjectID.patientPOI(reader));

        return AuditMessages.createMessage(
                EventID.toEventIdentification(auditLogger, path, eventType, auditInfo),
                activeParticipants(eventType, auditInfo, auditLogger),
                pois.toArray(new ParticipantObjectIdentification[0]));
    }

    private static ActiveParticipant[] activeParticipants(
            AuditUtils.EventType eventType, AuditInfo auditInfo, AuditLogger auditLogger) {
        return auditInfo.getField(AuditInfo.C_MOVE_ORIGINATOR) != null
                ? cMoveActiveParticipants(eventType, auditInfo, auditLogger)
                : auditInfo.getField(AuditInfo.IS_EXPORT) != null
                    ? exportActiveParticipants(eventType, auditInfo, auditLogger)
                    : cGetOrWadoRSOrRAD69ActiveParticipants(eventType, auditInfo, auditLogger);
    }

    private static ActiveParticipant[] cMoveActiveParticipants(
            AuditUtils.EventType eventType, AuditInfo auditInfo, AuditLogger auditLogger) {
        ActiveParticipant[] activeParticipants = new ActiveParticipant[3];
        activeParticipants[0] = new ActiveParticipantBuilder(
                auditInfo.getField(AuditInfo.CALLED_USERID),
                getLocalHostName(auditLogger))
                .userIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle)
                .altUserID(AuditLogger.processID())
                .roleIDCode(eventType.source)
                .build();
        activeParticipants[1] = new ActiveParticipantBuilder(
                auditInfo.getField(AuditInfo.DEST_USER_ID),
                auditInfo.getField(AuditInfo.DEST_NAP_ID))
                .userIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle)
                .roleIDCode(eventType.destination)
                .build();
        activeParticipants[2] = new ActiveParticipantBuilder(
                auditInfo.getField(AuditInfo.C_MOVE_ORIGINATOR),
                auditInfo.getField(AuditInfo.CALLING_HOST))
                .userIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle)
                .isRequester()
                .build();
        return activeParticipants;
    }

    private static ActiveParticipant[] exportActiveParticipants(
            AuditUtils.EventType eventType, AuditInfo auditInfo, AuditLogger auditLogger) {
        ActiveParticipant[] activeParticipants = new ActiveParticipant[3];
        activeParticipants[0] = new ActiveParticipantBuilder(
                auditInfo.getField(AuditInfo.DEST_USER_ID),
                auditInfo.getField(AuditInfo.DEST_NAP_ID))
                .userIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle)
                .roleIDCode(eventType.destination).build();
        String archiveUserID = auditInfo.getField(AuditInfo.CALLED_USERID);
        AuditMessages.UserIDTypeCode archiveUserIDTypeCode = archiveUserIDTypeCode(archiveUserID);
        if (auditInfo.getField(AuditInfo.CALLING_USERID) == null)
            activeParticipants[1] = new ActiveParticipantBuilder(
                    archiveUserID,
                    getLocalHostName(auditLogger))
                    .userIDTypeCode(archiveUserIDTypeCode)
                    .altUserID(AuditLogger.processID())
                    .isRequester()
                    .roleIDCode(eventType.source)
                    .build();

        else {
            activeParticipants[1] = new ActiveParticipantBuilder(
                    archiveUserID,
                    getLocalHostName(auditLogger))
                    .userIDTypeCode(archiveUserIDTypeCode)
                    .altUserID(AuditLogger.processID())
                    .roleIDCode(eventType.source)
                    .build();
            String callingUserID = auditInfo.getField(AuditInfo.CALLING_USERID);
            activeParticipants[2] = new ActiveParticipantBuilder(
                    callingUserID,
                    auditInfo.getField(AuditInfo.CALLING_HOST))
                    .userIDTypeCode(AuditMessages.userIDTypeCode(callingUserID))
                    .isRequester()
                    .build();
        }
        return activeParticipants;
    }

    private static ActiveParticipant[] cGetOrWadoRSOrRAD69ActiveParticipants(
            AuditUtils.EventType eventType, AuditInfo auditInfo, AuditLogger auditLogger) {
        ActiveParticipant[] activeParticipants = new ActiveParticipant[2];
        String archiveUserID = auditInfo.getField(AuditInfo.CALLED_USERID);
        AuditMessages.UserIDTypeCode archiveUserIDTypeCode = archiveUserIDTypeCode(archiveUserID);
        activeParticipants[0] = new ActiveParticipantBuilder(
                archiveUserID,
                getLocalHostName(auditLogger))
                .userIDTypeCode(archiveUserIDTypeCode)
                .altUserID(AuditLogger.processID())
                .roleIDCode(eventType.source)
                .build();
        String callingUserID = auditInfo.getField(AuditInfo.DEST_USER_ID);
        activeParticipants[1] = new ActiveParticipantBuilder(
                callingUserID,
                auditInfo.getField(AuditInfo.DEST_NAP_ID))
                .userIDTypeCode(AuditService.remoteUserIDTypeCode(archiveUserIDTypeCode, callingUserID))
                .isRequester()
                .roleIDCode(eventType.destination)
                .build();
        return activeParticipants;
    }

    private static AuditMessages.UserIDTypeCode archiveUserIDTypeCode(String userID) {
        return  userID.indexOf('/') != -1
                ? AuditMessages.UserIDTypeCode.URI
                : AuditMessages.UserIDTypeCode.StationAETitle;
    }

    private static String getLocalHostName(AuditLogger auditLogger) {
        return auditLogger.getConnections().get(0).getHostname();
    }

    static void auditWADOURI(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        EventIdentification eventIdentification = getEventIdentification(auditInfo, eventType);
        eventIdentification.setEventDateTime(getEventTime(path, auditLogger));
        List<ActiveParticipant> activeParticipants = new ArrayList<>();
        activeParticipants.add(requestor(auditInfo, eventType));
        activeParticipants.add(archiveURI(auditInfo, eventType, auditLogger));
        InstanceInfo instanceInfo = instanceInfo(auditInfo, reader);
        boolean showIUIDs = auditInfo.getField(AuditInfo.OUTCOME) != null || auditLogger.isIncludeInstanceUID();
        emitAuditMessage(auditLogger, eventIdentification, activeParticipants,
                study(auditInfo, instanceInfo, showIUIDs),
                patient(auditInfo));
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

    private static ParticipantObjectIdentification study(
            AuditInfo auditInfo, InstanceInfo instanceInfo, boolean showSOPIUIDs) {
        ParticipantObjectIdentification study = new ParticipantObjectIdentification();
        study.setParticipantObjectID(auditInfo.getField(AuditInfo.STUDY_UID));
        study.setParticipantObjectIDTypeCode(AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID);
        study.setParticipantObjectTypeCode(AuditMessages.ParticipantObjectTypeCode.SystemObject);
        study.setParticipantObjectTypeCodeRole(AuditMessages.ParticipantObjectTypeCodeRole.Report);
        study.getParticipantObjectDetail()
                .add(AuditMessages.createParticipantObjectDetail("StudyDate", auditInfo.getField(AuditInfo.STUDY_DATE)));
        study.setParticipantObjectDescription(studyParticipantObjDesc(instanceInfo, showSOPIUIDs));
        return study;
    }

    private static ParticipantObjectDescription studyParticipantObjDesc(InstanceInfo instanceInfo, boolean showSOPIUIDs) {
        ParticipantObjectDescription studyParticipantObjDesc = new ParticipantObjectDescription();
        studyParticipantObjDesc.getAccession().add(accession(instanceInfo));
        studyParticipantObjDesc.getSOPClass().addAll(sopClasses(instanceInfo, showSOPIUIDs));
        return studyParticipantObjDesc;
    }

    private static Accession accession(InstanceInfo instanceInfo) {
        Accession accession = new Accession();
        accession.setNumber(instanceInfo.getAccessionNo());
        return accession;
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

    private static ActiveParticipant archiveURI(
            AuditInfo auditInfo, AuditUtils.EventType eventType, AuditLogger auditLogger) {
        ActiveParticipant archiveURI = new ActiveParticipant();
        archiveURI.setUserID(auditInfo.getField(AuditInfo.CALLED_USERID));
        archiveURI.setUserIDTypeCode(AuditMessages.UserIDTypeCode.URI);
        archiveURI.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        archiveURI.setAlternativeUserID(AuditLogger.processID());
        archiveURI.getRoleIDCode().add(eventType.source);   //The process that sent the data. - DICOM PS3.15
        String archiveURIHost = auditLogger.getConnections().get(0).getHostname();
        archiveURI.setNetworkAccessPointID(archiveURIHost);
        archiveURI.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(archiveURIHost)
                        ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                        : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        return archiveURI;
    }

    private static ActiveParticipant requestor(AuditInfo auditInfo, AuditUtils.EventType eventType) {
        ActiveParticipant requestor = new ActiveParticipant();
        String requestorUserID = auditInfo.getField(AuditInfo.CALLING_USERID);
        requestor.setUserID(requestorUserID);
        boolean requestorIsIP = AuditMessages.isIP(requestorUserID);
        requestor.setUserIDTypeCode(
                requestorIsIP
                        ? AuditMessages.UserIDTypeCode.NodeID
                        : AuditMessages.UserIDTypeCode.PersonID);
        requestor.setUserTypeCode(
                requestorIsIP
                        ? AuditMessages.UserTypeCode.Application
                        : AuditMessages.UserTypeCode.Person);
        requestor.getRoleIDCode().add(eventType.destination);   //The process that received the data. - DICOM PS3.15
        requestor.setUserIsRequestor(true);
        String requestorHost = auditInfo.getField(AuditInfo.CALLING_HOST);
        requestor.setNetworkAccessPointID(requestorHost);
        requestor.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(requestorHost)
                        ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                        : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        return requestor;
    }

}