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
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.store.InstanceLocations;
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

    static List<AuditInfo> successAuditInfos(RetrieveContext ctx, ArchiveDeviceExtension arcDev) {
        Collection<InstanceLocations> completedMatches = completedMatches(ctx);
        List<AuditInfo> auditInfos = new ArrayList<>();
        auditInfos.add(retrieveAuditInfo(ctx, 0));
        auditInfos.addAll(cStoreForwardAuditInfos(ctx, arcDev));
        ctx.getCStoreForwards().forEach(cStoreFwd -> auditInfos.add(instanceAuditInfo(cStoreFwd, arcDev)));
        completedMatches.forEach(completedMatch -> auditInfos.add(instanceAuditInfo(completedMatch, arcDev)));
        return auditInfos;
    }

    static List<AuditInfo> failedAuditInfos(RetrieveContext ctx, ArchiveDeviceExtension arcDev) {
        Collection<InstanceLocations> failedMatches = failedMatches(ctx);
        if (failedMatches.isEmpty()) {
            if (ctx.failedSOPInstanceUIDs().length > 0)
                LOG.info("InstanceLocations not available for instances failed to be retrieved {}. Exit spooling of retrieve failures",
                    Arrays.asList(ctx.failedSOPInstanceUIDs()));
            return Collections.emptyList();
        }

        List<AuditInfo> failedAuditInfos = new ArrayList<>();
        failedAuditInfos.add(retrieveAuditInfo(ctx, failedMatches.size()));
        failedAuditInfos.addAll(cStoreForwardAuditInfos(ctx, arcDev));
        ctx.getCStoreForwards().forEach(cStoreFwd -> failedAuditInfos.add(instanceAuditInfo(cStoreFwd, arcDev)));
        failedMatches.forEach(failedMatch -> failedAuditInfos.add(instanceAuditInfo(failedMatch, arcDev)));
        return failedAuditInfos;
    }

    private static AuditInfo retrieveAuditInfo(RetrieveContext ctx, int failedMatches) {
        return isExportTriggered(ctx) ? export(ctx, failedMatches) : retrieve(ctx, failedMatches);
    }

    private static AuditInfo export(RetrieveContext ctx, int failedMatches) {
        HttpServletRequestInfo httpServletRequestInfo = ctx.getHttpServletRequestInfo();
        if (httpServletRequestInfo == null)
            return exportByScheduler(ctx, failedMatches);

        return exportByREST(ctx, failedMatches);
    }

    private static AuditInfo exportByREST(RetrieveContext ctx, int failedMatches) {
        HttpServletRequestInfo httpServletRequestInfo = ctx.getHttpServletRequestInfo();
        return new AuditInfoBuilder.Builder()
                .callingUserID(httpServletRequestInfo.requesterUserID)
                .callingHost(httpServletRequestInfo.requesterHost)
                .calledUserID(httpServletRequestInfo.requestURIWithQueryStr())
                .archiveUserID(ctx.getLocalAETitle())
                .destUserID(ctx.getDestinationAETitle())
                .destNapID(ctx.getDestinationHostName())
                .warning(ctx.warning() == 0 ? null : "Warnings on retrieve of " + ctx.warning() + " instances")
                .outcome(outcomeDesc(ctx, failedMatches))
                .toAuditInfo();
    }

    private static AuditInfo exportByScheduler(RetrieveContext ctx, int failedMatches) {
        return new AuditInfoBuilder.Builder()
                .callingUserID(ctx.getLocalAETitle())
                .callingHost(ctx.getRequestorHostName())
                .destUserID(ctx.getDestinationAETitle())
                .destNapID(ctx.getDestinationHostName())
                .warning(ctx.warning() == 0 ? null : "Warnings on retrieve of " + ctx.warning() + " instances")
                .outcome(outcomeDesc(ctx, failedMatches))
                .toAuditInfo();
    }

    private static AuditInfo retrieve(RetrieveContext ctx, int failedMatches) {
        HttpServletRequestInfo httpServletRequestInfo = ctx.getHttpServletRequestInfo();
        if (httpServletRequestInfo == null)
            return retrieveByCMoveOrCGet(ctx, failedMatches);

        return retrieveByWADORSOrRAD69(ctx, failedMatches);
    }

    private static AuditInfo retrieveByWADORSOrRAD69(RetrieveContext ctx, int failedMatches) {
        HttpServletRequestInfo httpServletRequestInfo = ctx.getHttpServletRequestInfo();
        return new AuditInfoBuilder.Builder()
                .callingUserID(httpServletRequestInfo.requesterUserID)
                .callingHost(httpServletRequestInfo.requesterHost)
                .calledUserID(httpServletRequestInfo.requestURIWithQueryStr())
                .warning(ctx.warning() == 0 ? null : "Warnings on retrieve of " + ctx.warning() + " instances")
                .outcome(outcomeDesc(ctx, failedMatches))
                .qrLevel(ctx.getQueryRetrieveLevel().name())
                .toAuditInfo();
    }

    private static AuditInfo retrieveByCMoveOrCGet(RetrieveContext ctx, int failedMatches) {
        return new AuditInfoBuilder.Builder()
                .callingHost(ctx.getRequestorHostName())
                .calledUserID(ctx.getLocalAETitle())
                .cMoveOriginator(ctx.getMoveOriginatorAETitle())
                .destUserID(ctx.getDestinationAETitle())
                .destNapID(ctx.getDestinationHostName())
                .warning(ctx.warning() == 0 ? null : "Warnings on retrieve of " + ctx.warning() + " instances")
                .outcome(outcomeDesc(ctx, failedMatches))
                .qrLevel(ctx.getQueryRetrieveLevel().name())
                .toAuditInfo();
    }

    private static List<AuditInfo> cStoreForwardAuditInfos(RetrieveContext ctx, ArchiveDeviceExtension arcDev) {
        List<AuditInfo> cStoreForwardAuditInfos = new ArrayList<>();
        ctx.getCStoreForwards().forEach(cStoreFwd -> cStoreForwardAuditInfos.add(instanceAuditInfo(cStoreFwd, arcDev)));
        return cStoreForwardAuditInfos;
    }

    private static Collection<InstanceLocations> failedMatches(RetrieveContext ctx) {
        Collection<InstanceLocations> failedMatches = ctx.getFailedMatches();
        if (failedMatches.isEmpty()) {
            List<String> failedSOPIUIDs = Arrays.asList(ctx.failedSOPInstanceUIDs());
            return ctx.getMatches().stream()
                       .filter(il -> failedSOPIUIDs.contains(il.getSopInstanceUID()))
                       .collect(Collectors.toList());
        }
        return failedMatches;
    }

    private static Collection<InstanceLocations> completedMatches(RetrieveContext ctx) {
        List<String> failedSOPIUIDs = Arrays.asList(ctx.failedSOPInstanceUIDs());
        return ctx.getMatches().stream()
                    .filter(il -> !failedSOPIUIDs.contains(il.getSopInstanceUID()))
                    .collect(Collectors.toList());
    }

    private static AuditInfo instanceAuditInfo(InstanceLocations il, ArchiveDeviceExtension arcDev) {
        Attributes attrs = il.getAttributes();
        return new AuditInfoBuilder.Builder()
                .studyUIDAccNumDate(attrs, arcDev)
                .sopCUID(attrs.getString(Tag.SOPClassUID))
                .sopIUID(attrs.getString(Tag.SOPInstanceUID))
                .pIDAndName(attrs, arcDev)
                .toAuditInfo();
    }

    private static boolean isExportTriggered(RetrieveContext ctx) {
        return (ctx.getRequestAssociation() == null && ctx.getStoreAssociation() != null)
                || (ctx.getRequestAssociation() == null && ctx.getStoreAssociation() == null && ctx.getException() != null);
    }

    private static String outcomeDesc(RetrieveContext ctx, int failedMatches) {
        String failureMsg = failedMatches > 0
                                ? "Retrieve of " + failedMatches + " objects failed."
                                : null;
        if (failureMsg == null && ctx.getException() == null)
            return null;

        StringBuilder sb = new StringBuilder();
        if (failureMsg != null)
            sb.append(failureMsg).append("\n");
        if (ctx.getException() != null)
            sb.append(ctx.getException().toString());
        return sb.toString();
    }

    static void audit(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        boolean showIUIDs = auditInfo.getField(AuditInfo.OUTCOME) != null || auditLogger.isIncludeInstanceUID();

        EventIdentification eventIdentification = getEventIdentification(auditInfo, eventType);
        eventIdentification.setEventDateTime(getEventTime(path, auditLogger));

        List<ParticipantObjectIdentification> participantObjectIdentifications = new ArrayList<>();
        String qrLevel = auditInfo.getField(AuditInfo.QR_LEVEL);
        AuditInfo auditInfoInstance = new AuditInfo(reader.getInstanceLines().get(0));
        participantObjectIdentifications.add(patient(auditInfoInstance));
        if (qrLevel == null || !qrLevel.equals(QueryRetrieveLevel2.PATIENT.name())) {
            InstanceInfo instanceInfo = instanceInfo(auditInfoInstance, reader);
            participantObjectIdentifications.add(study(auditInfoInstance, instanceInfo, showIUIDs));
        } else {
            HashMap<String, InstanceInfo> studyInstanceInfo = studyInstanceInfo(reader);
            studyInstanceInfo.forEach((studyUID, instanceInfo) ->
                    participantObjectIdentifications.add(study(studyUID, instanceInfo, showIUIDs)));
        }

        emitAuditMessage(auditLogger, eventIdentification,
                activeParticipants(auditInfo, auditLogger, eventType),
                participantObjectIdentifications.toArray(new ParticipantObjectIdentification[0]));
    }

    private static List<ActiveParticipant> activeParticipants(
            AuditInfo auditInfo, AuditLogger auditLogger, AuditUtils.EventType eventType) {
        String calledUserID = auditInfo.getField(AuditInfo.CALLED_USERID);
        String cMoveOriginator = auditInfo.getField(AuditInfo.C_MOVE_ORIGINATOR);
        String destination = auditInfo.getField(AuditInfo.DEST_USER_ID);
        return calledUserID == null
                ? exportBySchedulerActiveParticipants(auditInfo, auditLogger, eventType)
                : calledUserID.contains("/")
                    ? destination == null
                        ? retrieveByWADORSOrRAD69ActiveParticipants(auditInfo, auditLogger, eventType)
                        : exportByRESTActiveParticipants(auditInfo, auditLogger, eventType)
                    : cMoveOriginator == null
                        ? cGetActiveParticipants(auditInfo, auditLogger, eventType)
                        : cMoveActiveParticipants(auditInfo, auditLogger, eventType);
    }

    private static List<ActiveParticipant> cMoveActiveParticipants(
            AuditInfo auditInfo, AuditLogger auditLogger, AuditUtils.EventType eventType) {
        String archiveAET = auditInfo.getField(AuditInfo.CALLED_USERID);
        List<ActiveParticipant> cMoveActiveParticipants = new ArrayList<>();
        cMoveActiveParticipants.add(cMoveOriginator(auditInfo));
        cMoveActiveParticipants.add(archiveAE(archiveAET, auditLogger, eventType, false));
        cMoveActiveParticipants.add(destinationAE(auditInfo, eventType, false));
        return cMoveActiveParticipants;
    }

    private static List<ActiveParticipant> cGetActiveParticipants(
            AuditInfo auditInfo, AuditLogger auditLogger, AuditUtils.EventType eventType) {
        String archiveAET = auditInfo.getField(AuditInfo.CALLED_USERID);
        List<ActiveParticipant> cGetActiveParticipants = new ArrayList<>();
        cGetActiveParticipants.add(archiveAE(archiveAET, auditLogger, eventType, false));
        cGetActiveParticipants.add(destinationAE(auditInfo, eventType, true));
        return cGetActiveParticipants;
    }

    private static List<ActiveParticipant> exportBySchedulerActiveParticipants(
            AuditInfo auditInfo, AuditLogger auditLogger, AuditUtils.EventType eventType) {
        String archiveAET = auditInfo.getField(AuditInfo.CALLING_USERID);
        List<ActiveParticipant> exportBySchedulerActiveParticipants = new ArrayList<>();
        exportBySchedulerActiveParticipants.add(archiveAE(archiveAET, auditLogger, eventType, true));
        exportBySchedulerActiveParticipants.add(destinationAE(auditInfo, eventType, false));
        return exportBySchedulerActiveParticipants;
    }

    private static List<ActiveParticipant> exportByRESTActiveParticipants(
            AuditInfo auditInfo, AuditLogger auditLogger, AuditUtils.EventType eventType) {
        String archiveAET = auditInfo.getField(AuditInfo.ARCHIVE_USER_ID);
        List<ActiveParticipant> exportByRESTActiveParticipants = new ArrayList<>();
        exportByRESTActiveParticipants.add(requestor(auditInfo, eventType, false));
        exportByRESTActiveParticipants.add(archiveURI(auditInfo, auditLogger, eventType, false));
        exportByRESTActiveParticipants.add(archiveAE(archiveAET, auditLogger, eventType, false));
        exportByRESTActiveParticipants.add(destinationAE(auditInfo, eventType, false));
        return exportByRESTActiveParticipants;
    }

    private static List<ActiveParticipant> retrieveByWADORSOrRAD69ActiveParticipants(
            AuditInfo auditInfo, AuditLogger auditLogger, AuditUtils.EventType eventType) {
        List<ActiveParticipant> retrieveByWADORSOrRAD69ActiveParticipants = new ArrayList<>();
        retrieveByWADORSOrRAD69ActiveParticipants.add(requestor(auditInfo, eventType, true));
        retrieveByWADORSOrRAD69ActiveParticipants.add(archiveURI(auditInfo, auditLogger, eventType, true));
        return retrieveByWADORSOrRAD69ActiveParticipants;
    }

    private static HashMap<String, InstanceInfo> studyInstanceInfo(SpoolFileReader reader) {
        HashMap<String, InstanceInfo> studyInstanceInfo = new HashMap<>();
        for (String line : reader.getInstanceLines()) {
            AuditInfo rInfo = new AuditInfo(line);
            String studyInstanceUID = rInfo.getField(AuditInfo.STUDY_UID);
            InstanceInfo instanceInfo = studyInstanceInfo.get(studyInstanceUID);
            if (instanceInfo == null) {
                instanceInfo = new InstanceInfo();
                instanceInfo.setAccessionNo(rInfo.getField(AuditInfo.ACC_NUM));
                studyInstanceInfo.put(studyInstanceUID, instanceInfo);
            }
            instanceInfo.addSOPInstance(rInfo);
            instanceInfo.addStudyDate(rInfo);
            studyInstanceInfo.put(studyInstanceUID, instanceInfo);
        }
        return studyInstanceInfo;
    }

    private static ActiveParticipant cMoveOriginator(AuditInfo auditInfo) {
        ActiveParticipant cMoveOriginator = new ActiveParticipant();
        String cMoveOriginatorUserID = auditInfo.getField(AuditInfo.C_MOVE_ORIGINATOR);
        cMoveOriginator.setUserID(cMoveOriginatorUserID);
        cMoveOriginator.setUserIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle);
        cMoveOriginator.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        String cMoveOriginatorHost = auditInfo.getField(AuditInfo.CALLING_HOST);
        cMoveOriginator.setNetworkAccessPointID(cMoveOriginatorHost);
        cMoveOriginator.setNetworkAccessPointTypeCode(AuditMessages.isIP(cMoveOriginatorHost)
                                                        ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                                                        : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        cMoveOriginator.setUserIsRequestor(true);
        return cMoveOriginator;
    }

    private static ActiveParticipant destinationAE(AuditInfo auditInfo, AuditUtils.EventType eventType, boolean requestor) {
        ActiveParticipant destinationAE = new ActiveParticipant();
        destinationAE.setUserID(auditInfo.getField(AuditInfo.DEST_USER_ID));
        destinationAE.setUserIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle);
        destinationAE.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        String destinationAEHost = auditInfo.getField(AuditInfo.DEST_NAP_ID);
        destinationAE.setNetworkAccessPointID(destinationAEHost);
        destinationAE.setNetworkAccessPointTypeCode(AuditMessages.isIP(destinationAEHost)
                                                    ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                                                    : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        destinationAE.getRoleIDCode().add(eventType.destination);
        destinationAE.setUserIsRequestor(requestor);
        return destinationAE;
    }

    private static ActiveParticipant archiveAE(
            String archiveAET, AuditLogger auditLogger, AuditUtils.EventType eventType, boolean requestor) {
        ActiveParticipant archiveAE = new ActiveParticipant();
        archiveAE.setUserID(archiveAET);
        archiveAE.setUserIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle);
        archiveAE.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        archiveAE.setAlternativeUserID(AuditLogger.processID());
        String archiveAEHost = auditLogger.getConnections().get(0).getHostname();
        archiveAE.setNetworkAccessPointID(archiveAEHost);
        archiveAE.setNetworkAccessPointTypeCode(AuditMessages.isIP(archiveAEHost)
                ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        archiveAE.getRoleIDCode().add(eventType.source);
        archiveAE.setUserIsRequestor(requestor);
        return archiveAE;
    }

    static void auditWADOURI(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        EventIdentification eventIdentification = getEventIdentification(auditInfo, eventType);
        eventIdentification.setEventDateTime(getEventTime(path, auditLogger));
        List<ActiveParticipant> activeParticipants = new ArrayList<>();
        activeParticipants.add(requestor(auditInfo, eventType, true));
        activeParticipants.add(archiveURI(auditInfo, auditLogger, eventType, true));
        InstanceInfo instanceInfo = instanceInfo(auditInfo, reader);
        boolean showIUIDs = auditInfo.getField(AuditInfo.OUTCOME) != null || auditLogger.isIncludeInstanceUID();
        emitAuditMessage(auditLogger, eventIdentification, activeParticipants,
                study(auditInfo, instanceInfo, showIUIDs),
                patient(auditInfo));
    }

    private static EventIdentification getEventIdentification(AuditInfo auditInfo, AuditUtils.EventType eventType) {
        String warning = auditInfo.getField(AuditInfo.WARNING);
        String outcome = auditInfo.getField(AuditInfo.OUTCOME);
        EventIdentification ei = new EventIdentification();
        ei.setEventID(eventType.eventID);
        ei.setEventActionCode(eventType.eventActionCode);
        ei.setEventOutcomeDescription(warning == null ? outcome : warning + "\n" + outcome);
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

    private static ParticipantObjectIdentification study(
            String studyIUID, InstanceInfo instanceInfo, boolean showSOPIUIDs) {
        ParticipantObjectIdentification study = new ParticipantObjectIdentification();
        study.setParticipantObjectID(studyIUID);
        study.setParticipantObjectIDTypeCode(AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID);
        study.setParticipantObjectTypeCode(AuditMessages.ParticipantObjectTypeCode.SystemObject);
        study.setParticipantObjectTypeCodeRole(AuditMessages.ParticipantObjectTypeCodeRole.Report);
        study.getParticipantObjectDetail()
                .add(AuditMessages.createParticipantObjectDetail("StudyDate", instanceInfo.getStudyDate()));
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
            AuditInfo auditInfo, AuditLogger auditLogger, AuditUtils.EventType eventType, boolean isSource) {
        ActiveParticipant archiveURI = new ActiveParticipant();
        archiveURI.setUserID(auditInfo.getField(AuditInfo.CALLED_USERID));
        archiveURI.setUserIDTypeCode(AuditMessages.UserIDTypeCode.URI);
        archiveURI.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        archiveURI.setAlternativeUserID(AuditLogger.processID());
        if (isSource)
            archiveURI.getRoleIDCode().add(eventType.source);   //The process that sent the data. - DICOM PS3.15
        String archiveURIHost = auditLogger.getConnections().get(0).getHostname();
        archiveURI.setNetworkAccessPointID(archiveURIHost);
        archiveURI.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(archiveURIHost)
                        ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                        : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        return archiveURI;
    }

    private static ActiveParticipant requestor(AuditInfo auditInfo, AuditUtils.EventType eventType, boolean isDestination) {
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
        if (isDestination)
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