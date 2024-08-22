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
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.stgcmt.StgCmtContext;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2018
 */
class StorageCommitAuditService extends AuditService {

    static List<AuditInfo> successAuditInfos(StgCmtContext ctx, ArchiveDeviceExtension arcDev) {
        Attributes eventInfo = ctx.getEventInfo();
        Sequence successSOPSeq = eventInfo.getSequence(Tag.ReferencedSOPSequence);
        if (successSOPSeq == null || successSOPSeq.isEmpty())
            return Collections.emptyList();

        List<AuditInfo> auditInfos = new ArrayList<>();
        auditInfos.add(successAuditInfo(ctx, arcDev));
        auditInfos.addAll(successSOPSeq.stream()
                                       .map(StorageCommitAuditService::refSopAuditInfo)
                                       .collect(Collectors.toList()));
        return auditInfos;
    }

    static List<AuditInfo> failedAuditInfos(StgCmtContext ctx, ArchiveDeviceExtension arcDev) {
        Attributes eventInfo = ctx.getEventInfo();
        Sequence failedSOPSeq = eventInfo.getSequence(Tag.FailedSOPSequence);
        if (failedSOPSeq == null || failedSOPSeq.isEmpty())
            return Collections.emptyList();

        List<AuditInfo> failedSOPAuditInfos = new ArrayList<>();
        Set<String> failureReasons = new HashSet<>();
        for (Attributes failedSOP : failedSOPSeq) {
            failureReasons.add(failedSOP.getInt(Tag.FailureReason, 0) == Status.NoSuchObjectInstance
                    ? "NoSuchObjectInstance"
                    : failedSOP.getInt(Tag.FailureReason, 0) == Status.ClassInstanceConflict
                    ? "ClassInstanceConflict" : "ProcessingFailure");
            failedSOPAuditInfos.add(refSopAuditInfo(failedSOP));
        }

        List<AuditInfo> auditInfos = new ArrayList<>();
        auditInfos.add(failureAuditInfo(ctx, arcDev, failureReasons));
        auditInfos.addAll(failedSOPAuditInfos);
        return auditInfos;
    }

    private static AuditInfo refSopAuditInfo(Attributes item) {
        return new AuditInfoBuilder.Builder()
                .sopCUID(item.getString(Tag.ReferencedSOPClassUID))
                .sopIUID(item.getString(Tag.ReferencedSOPInstanceUID))
                .toAuditInfo();
    }

    private static AuditInfo successAuditInfo(StgCmtContext ctx, ArchiveDeviceExtension arcDev) {
        HttpServletRequestInfo httpServletRequestInfo = ctx.getRequest();
        if (httpServletRequestInfo == null)
            return successAuditInfoDICOM(ctx, arcDev);

        Attributes eventInfo = ctx.getEventInfo();
        return new AuditInfoBuilder.Builder()
                .callingUserID(httpServletRequestInfo.requesterUserID)
                .callingHost(httpServletRequestInfo.requesterHost)
                .calledUserID(httpServletRequestInfo.requestURI)
                .queryString(httpServletRequestInfo.queryString)
                .pIDAndName(eventInfo, arcDev)
                .studyIUID(studyIUID(eventInfo, arcDev))
                .toAuditInfo();
    }

    private static AuditInfo successAuditInfoDICOM(StgCmtContext ctx, ArchiveDeviceExtension arcDev) {
        ApplicationEntity remoteAE = ctx.getRemoteAE();
        if (remoteAE == null)
            return successAuditInfoScheduler(ctx, arcDev);

        Attributes eventInfo = ctx.getEventInfo();
        return new AuditInfoBuilder.Builder()
                .callingUserID(remoteAE.getAETitle())
                .callingHost(remoteAE.getConnections().get(0).getHostname())
                .calledUserID(ctx.getLocalAET())
                .pIDAndName(eventInfo, arcDev)
                .studyIUID(studyIUID(eventInfo, arcDev))
                .toAuditInfo();
    }

    private static AuditInfo successAuditInfoScheduler(StgCmtContext ctx, ArchiveDeviceExtension arcDev) {
        Attributes eventInfo = ctx.getEventInfo();
        return new AuditInfoBuilder.Builder()
                .callingUserID(arcDev.getDevice().getDeviceName())
                .pIDAndName(eventInfo, arcDev)
                .studyIUID(studyIUID(eventInfo, arcDev))
                .toAuditInfo();
    }

    private static AuditInfo failureAuditInfo(
            StgCmtContext ctx, ArchiveDeviceExtension arcDev, Set<String> failureReasons) {
        HttpServletRequestInfo httpServletRequestInfo = ctx.getRequest();
        if (httpServletRequestInfo == null)
            return failureAuditInfoDICOM(ctx, arcDev, failureReasons);

        Attributes eventInfo = ctx.getEventInfo();
        return new AuditInfoBuilder.Builder()
                .callingUserID(httpServletRequestInfo.requesterUserID)
                .callingHost(httpServletRequestInfo.requesterHost)
                .calledUserID(httpServletRequestInfo.requestURI)
                .queryString(httpServletRequestInfo.queryString)
                .pIDAndName(eventInfo, arcDev)
                .studyIUID(studyIUID(eventInfo, arcDev))
                .outcome(String.join(";", failureReasons))
                .toAuditInfo();
    }

    private static AuditInfo failureAuditInfoDICOM(
            StgCmtContext ctx, ArchiveDeviceExtension arcDev, Set<String> failureReasons) {
        ApplicationEntity remoteAE = ctx.getRemoteAE();
        if (remoteAE == null)
            return failureAuditInfoScheduler(ctx, arcDev, failureReasons);

        Attributes eventInfo = ctx.getEventInfo();
        return new AuditInfoBuilder.Builder()
                .callingUserID(remoteAE.getAETitle())
                .callingHost(remoteAE.getConnections().get(0).getHostname())
                .calledUserID(ctx.getLocalAET())
                .pIDAndName(eventInfo, arcDev)
                .studyIUID(studyIUID(eventInfo, arcDev))
                .outcome(String.join(";", failureReasons))
                .toAuditInfo();
    }

    private static AuditInfo failureAuditInfoScheduler(
            StgCmtContext ctx, ArchiveDeviceExtension arcDev, Set<String> failureReasons) {
        Attributes eventInfo = ctx.getEventInfo();
        return new AuditInfoBuilder.Builder()
                .callingUserID(arcDev.getDevice().getDeviceName())
                .pIDAndName(eventInfo, arcDev)
                .studyIUID(studyIUID(eventInfo, arcDev))
                .outcome(String.join(";", failureReasons))
                .toAuditInfo();
    }

    private static String studyIUID(Attributes eventInfo, ArchiveDeviceExtension arcDev) {
        return eventInfo.getStrings(Tag.StudyInstanceUID) == null
                ? arcDev.auditUnknownStudyInstanceUID()
                : String.join(";", eventInfo.getStrings(Tag.StudyInstanceUID));
    }

    static void audit(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        EventIdentification eventIdentification = getEventIdentification(auditInfo, eventType);
        eventIdentification.setEventDateTime(getEventTime(path, auditLogger));
        String calledUserID = auditInfo.getField(AuditInfo.CALLED_USERID);
        if (calledUserID == null) {
            emitAuditMessage(auditLogger, eventIdentification,
                    Collections.singletonList(archiveRequestor(auditInfo, auditLogger)),
                    study(auditInfo, reader, auditLogger),
                    patient(auditInfo));
            return;
        }

        List<ActiveParticipant> activeParticipants = new ArrayList<>();
        if (calledUserID.startsWith("http")) {
            activeParticipants.add(requestor(auditInfo));
            activeParticipants.add(archiveURI(auditInfo, auditLogger));
        } else {
            activeParticipants.add(remoteAE(auditInfo));
            activeParticipants.add(archiveAE(auditInfo, auditLogger));
        }

        emitAuditMessage(auditLogger, eventIdentification, activeParticipants,
                study(auditInfo, reader, auditLogger),
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

    private static ParticipantObjectIdentification study(AuditInfo auditInfo, SpoolFileReader reader, AuditLogger auditLogger) {
        ParticipantObjectIdentification study = new ParticipantObjectIdentification();
        String[] studyIUIDs = StringUtils.split(auditInfo.getField(AuditInfo.STUDY_UID), ';');
        study.setParticipantObjectID(studyIUIDs[0]);
        study.setParticipantObjectIDTypeCode(AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID);
        study.setParticipantObjectTypeCode(AuditMessages.ParticipantObjectTypeCode.SystemObject);
        study.setParticipantObjectTypeCodeRole(AuditMessages.ParticipantObjectTypeCodeRole.Report);
        study.setParticipantObjectDataLifeCycle(AuditMessages.ParticipantObjectDataLifeCycle.Verification);
        study.setParticipantObjectDescription(participantObjDesc(auditInfo, reader, auditLogger));
        return study;
    }

    private static ParticipantObjectDescription participantObjDesc(
            AuditInfo auditInfo, SpoolFileReader reader, AuditLogger auditLogger) {
        String[] studyIUIDs = StringUtils.split(auditInfo.getField(AuditInfo.STUDY_UID), ';');
        boolean showIUID = studyIUIDs.length > 1
                            || auditInfo.getField(AuditInfo.OUTCOME) != null
                            || auditLogger.isIncludeInstanceUID();
        ParticipantObjectDescription participantObjDesc = new ParticipantObjectDescription();

        if (studyIUIDs.length > 1) {
            ParticipantObjectContainsStudy participantObjectContainsStudy = new ParticipantObjectContainsStudy();
            for (String studyIUID : studyIUIDs) {
                StudyIDs studyIDs = new StudyIDs();
                studyIDs.setUID(studyIUID);
                participantObjectContainsStudy.getStudyIDs().add(studyIDs);
            }
            participantObjDesc.setParticipantObjectContainsStudy(participantObjectContainsStudy);
        }

        InstanceInfo instanceInfo = new InstanceInfo();
        instanceInfo.addAcc(auditInfo);
        reader.getInstanceLines().forEach(line -> {
            AuditInfo info = new AuditInfo(line);
            instanceInfo.addSOPInstance(info);
        });
        instanceInfo.getSopClassMap().forEach((key, value) -> {
            SOPClass sopClass = AuditMessages.createSOPClass(showIUID ? value : null, key, value.size());
            participantObjDesc.getSOPClass().add(sopClass);
        });
        return participantObjDesc;
    }

    private static ActiveParticipant requestor(AuditInfo auditInfo) {
        ActiveParticipant requestor = new ActiveParticipant();
        String requestorUserID = auditInfo.getField(AuditInfo.CALLING_USERID);
        requestor.setUserID(requestorUserID);
        boolean requestorUserIsIP = AuditMessages.isIP(requestorUserID);
        requestor.setUserIDTypeCode(
                requestorUserIsIP
                    ? AuditMessages.UserIDTypeCode.NodeID
                    : AuditMessages.UserIDTypeCode.PersonID);
        requestor.setUserTypeCode(
                requestorUserIsIP
                    ? AuditMessages.UserTypeCode.Application
                    : AuditMessages.UserTypeCode.Person);
        String requestorHost = auditInfo.getField(AuditInfo.CALLING_HOST);
        requestor.setNetworkAccessPointID(requestorHost);
        requestor.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(requestorHost)
                    ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                    : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        requestor.setUserIsRequestor(true);
        return requestor;
    }

    private static ActiveParticipant archiveURI(AuditInfo auditInfo, AuditLogger auditLogger) {
        ActiveParticipant archiveURI = new ActiveParticipant();
        String archiveReqURI = auditInfo.getField(AuditInfo.CALLED_USERID);
        String queryStr = auditInfo.getField(AuditInfo.Q_STRING);
        archiveURI.setUserID(queryStr == null ? archiveReqURI : archiveReqURI + "?" + queryStr);
        archiveURI.setUserIDTypeCode(AuditMessages.UserIDTypeCode.URI);
        archiveURI.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        archiveURI.setAlternativeUserID(AuditLogger.processID());
        String archiveURIHost = auditLogger.getConnections().get(0).getHostname();
        archiveURI.setNetworkAccessPointID(archiveURIHost);
        archiveURI.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(archiveURIHost)
                    ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                    : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        return archiveURI;
    }

    private static ActiveParticipant remoteAE(AuditInfo auditInfo) {
        ActiveParticipant remoteAE = new ActiveParticipant();
        remoteAE.setUserID(auditInfo.getField(AuditInfo.CALLING_USERID));
        remoteAE.setUserIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle);
        remoteAE.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        String remoteAEHost = auditInfo.getField(AuditInfo.CALLING_HOST);
        remoteAE.setNetworkAccessPointID(remoteAEHost);
        remoteAE.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(remoteAEHost)
                    ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                    : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        remoteAE.setUserIsRequestor(true);
        return remoteAE;
    }

    private static ActiveParticipant archiveAE(AuditInfo auditInfo, AuditLogger auditLogger) {
        ActiveParticipant archiveAE = new ActiveParticipant();
        archiveAE.setUserID(auditInfo.getField(AuditInfo.CALLED_USERID));
        archiveAE.setUserIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle);
        archiveAE.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        archiveAE.setAlternativeUserID(AuditLogger.processID());
        String archiveAEHost = auditLogger.getConnections().get(0).getHostname();
        archiveAE.setNetworkAccessPointID(archiveAEHost);
        archiveAE.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(archiveAEHost)
                    ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                    : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        return archiveAE;
    }

    private static ActiveParticipant archiveRequestor(AuditInfo auditInfo, AuditLogger auditLogger) {
        ActiveParticipant archiveRequestor = new ActiveParticipant();
        archiveRequestor.setUserID(auditInfo.getField(AuditInfo.CALLING_USERID));
        archiveRequestor.setUserIDTypeCode(AuditMessages.UserIDTypeCode.DeviceName);
        archiveRequestor.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        archiveRequestor.setAlternativeUserID(AuditLogger.processID());
        String archiveRequestorHost = auditLogger.getConnections().get(0).getHostname();
        archiveRequestor.setNetworkAccessPointID(archiveRequestorHost);
        archiveRequestor.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(archiveRequestorHost)
                    ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                    : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        archiveRequestor.setUserIsRequestor(true);
        return archiveRequestor;
    }

}
