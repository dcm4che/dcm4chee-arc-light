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
 * Portions created by the Initial Developer are Copyright (C) 2015-2018
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
import org.dcm4che3.net.Status;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.stgcmt.StgCmtContext;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Oct 2018
 */
class StorageCommitAuditService {

    private final StgCmtContext ctx;
    private final ArchiveDeviceExtension arcDev;
    private AuditInfoBuilder[][] auditInfoBuilder;
    
    StorageCommitAuditService(StgCmtContext ctx, ArchiveDeviceExtension arcDev) {
        this.ctx = ctx;
        this.arcDev = arcDev;
        buildAuditInfos();
    }
    
    private void buildAuditInfos() {
        Attributes eventInfo = ctx.getEventInfo();
        String studyUID = eventInfo.getStrings(Tag.StudyInstanceUID) != null
                ? Stream.of(eventInfo.getStrings(Tag.StudyInstanceUID)).collect(Collectors.joining(";"))
                : arcDev.auditUnknownStudyInstanceUID();

        auditInfoBuilder = new AuditInfoBuilder[2][];
        buildSuccessAuditInfo(ctx, studyUID);
        buildFailedAuditInfo(ctx, studyUID);
    }

    private void buildSuccessAuditInfo(StgCmtContext stgCmtContext, String studyUID) {
        Sequence success = stgCmtContext.getEventInfo().getSequence(Tag.ReferencedSOPSequence);
        if (success != null && !success.isEmpty()) {
            AuditInfoBuilder[] successAuditInfo = new AuditInfoBuilder[success.size()+1];
            successAuditInfo[0] = new AuditInfoBuilder.Builder()
                    .callingUserID(storageCmtCallingAET(stgCmtContext))
                    .callingHost(storageCmtCallingHost(stgCmtContext))
                    .calledUserID(storageCmtCalledAET(stgCmtContext))
                    .pIDAndName(stgCmtContext.getEventInfo(), arcDev)
                    .studyUID(studyUID)
                    .build();
            for (int i = 1; i <= success.size(); i++)
                successAuditInfo[i] = buildRefSopAuditInfo(success.get(i-1));

            auditInfoBuilder[0] = successAuditInfo;
        }
    }

    private void buildFailedAuditInfo(StgCmtContext stgCmtContext, String studyUID) {
        Sequence failed = stgCmtContext.getEventInfo().getSequence(Tag.FailedSOPSequence);
        if (failed != null && !failed.isEmpty()) {
            AuditInfoBuilder[] failedAuditInfo = new AuditInfoBuilder[failed.size()+1];
            Set<String> failureReasons = new HashSet<>();
            for (int i = 1; i <= failed.size(); i++) {
                Attributes item = failed.get(i-1);
                failedAuditInfo[i] = buildRefSopAuditInfo(item);
                failureReasons.add(
                        item.getInt(Tag.FailureReason, 0) == Status.NoSuchObjectInstance
                                ? "NoSuchObjectInstance"
                                : item.getInt(Tag.FailureReason, 0) == Status.ClassInstanceConflict
                                ? "ClassInstanceConflict" : "ProcessingFailure");
            }
            failedAuditInfo[0] = new AuditInfoBuilder.Builder()
                    .callingUserID(storageCmtCallingAET(stgCmtContext))
                    .callingHost(storageCmtCallingHost(stgCmtContext))
                    .calledUserID(storageCmtCalledAET(stgCmtContext))
                    .pIDAndName(stgCmtContext.getEventInfo(), arcDev)
                    .studyUID(studyUID)
                    .outcome(failureReasons.stream().collect(Collectors.joining(";")))
                    .build();

            auditInfoBuilder[1] = failedAuditInfo;
        }
    }

    private AuditInfoBuilder buildRefSopAuditInfo(Attributes item) {
        return new AuditInfoBuilder.Builder()
                .sopCUID(item.getString(Tag.ReferencedSOPClassUID))
                .sopIUID(item.getString(Tag.ReferencedSOPInstanceUID)).build();
    }

    private String storageCmtCallingHost(StgCmtContext stgCmtContext) {
        return stgCmtContext.getRequest() != null
                ? stgCmtContext.getRequest().requesterHost
                : stgCmtContext.getRemoteAE() != null
                    ? stgCmtContext.getRemoteAE().getConnections().get(0).getHostname() : null;
    }

    private String storageCmtCalledAET(StgCmtContext stgCmtContext) {
        return stgCmtContext.getRequest() != null
                ? stgCmtContext.getRequest().requestURI
                : stgCmtContext.getLocalAET();
    }

    private String storageCmtCallingAET(StgCmtContext stgCmtContext) {
        return stgCmtContext.getRequest() != null
                ? stgCmtContext.getRequest().requesterUserID
                : stgCmtContext.getRemoteAE() != null
                    ? stgCmtContext.getRemoteAE().getAETitle() : null;
    }

    static AuditMessage auditMsg(SpoolFileReader reader, AuditServiceUtils.EventType eventType, AuditLogger auditLogger,
                                 Calendar eventTime) {
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        String outcome = auditInfo.getField(AuditInfo.OUTCOME);

        return AuditMessages.createMessage(
            eventIdentification(eventType, eventTime, outcome),
            activeParticipants(eventType, auditLogger, auditInfo),
            poiStudy(reader, auditInfo));
    }

    private static ActiveParticipantBuilder[] activeParticipants(AuditServiceUtils.EventType eventType, AuditLogger auditLogger, AuditInfo auditInfo) {
        ActiveParticipantBuilder[] activeParticipants = new ActiveParticipantBuilder[2];
        String archiveUserID = auditInfo.getField(AuditInfo.CALLED_USERID);
        AuditMessages.UserIDTypeCode archiveUserIDTypeCode = archiveUserIDTypeCode(archiveUserID);
        activeParticipants[0] = new ActiveParticipantBuilder.Builder(
                archiveUserID,
                auditLogger.getConnections().get(0).getHostname())
                .userIDTypeCode(archiveUserIDTypeCode)
                .altUserID(AuditLogger.processID())
                .roleIDCode(eventType.destination).build();
        String callingUserID = auditInfo.getField(AuditInfo.CALLING_USERID);
        if (callingUserID != null)
            activeParticipants[1] = new ActiveParticipantBuilder.Builder(
                    callingUserID,
                    auditInfo.getField(AuditInfo.CALLING_HOST))
                    .userIDTypeCode(AuditService.remoteUserIDTypeCode(archiveUserIDTypeCode, callingUserID))
                    .isRequester()
                    .roleIDCode(eventType.source).build();

        return activeParticipants;
    }

    private static EventIdentificationBuilder eventIdentification(AuditServiceUtils.EventType eventType, Calendar eventTime, String outcome) {
        return new EventIdentificationBuilder.Builder(
                eventType.eventID,
                eventType.eventActionCode,
                eventTime,
                outcome != null ? AuditMessages.EventOutcomeIndicator.MinorFailure : AuditMessages.EventOutcomeIndicator.Success)
                .outcomeDesc(outcome).build();
    }

    private static ParticipantObjectIdentificationBuilder poiStudy(SpoolFileReader reader, AuditInfo auditInfo) {
        String[] studyUIDs = StringUtils.split(auditInfo.getField(AuditInfo.STUDY_UID), ';');
        ParticipantObjectDescriptionBuilder poDesc = new ParticipantObjectDescriptionBuilder.Builder()
                .sopC(AuditService.toSOPClasses(reader, studyUIDs.length > 1 || auditInfo.getField(AuditInfo.OUTCOME) != null))
                .pocsStudyUIDs(studyUIDs).build();

        return new ParticipantObjectIdentificationBuilder.Builder(studyUIDs[0],
                AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID,
                AuditMessages.ParticipantObjectTypeCode.SystemObject, AuditMessages.ParticipantObjectTypeCodeRole.Report)
                .desc(poDesc).lifeCycle(AuditMessages.ParticipantObjectDataLifeCycle.Verification).build();
    }

    private static AuditMessages.UserIDTypeCode archiveUserIDTypeCode(String userID) {
        return  userID.indexOf('/') != -1
                ? AuditMessages.UserIDTypeCode.URI
                : AuditMessages.UserIDTypeCode.StationAETitle;
    }

    AuditInfoBuilder[][] getAuditInfoBuilder() {
        return auditInfoBuilder;
    }
}
