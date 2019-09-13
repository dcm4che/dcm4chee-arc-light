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
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.store.InstanceLocations;
import org.dcm4chee.arc.retrieve.RetrieveContext;

import java.nio.file.Path;
import java.util.*;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Aug 2017
 */

class RetrieveAuditService {

    private final RetrieveContext ctx;
    private final ArchiveDeviceExtension arcDev;
    private HttpServletRequestInfo httpServletRequestInfo;
    private AuditInfoBuilder[][] auditInfoBuilder;

    RetrieveAuditService(RetrieveContext ctx, ArchiveDeviceExtension arcDev) {
        this.ctx = ctx;
        this.arcDev = arcDev;
        httpServletRequestInfo = ctx.getHttpServletRequestInfo();
        processRetrieve();
    }

    private void processRetrieve() {
        if (someInstancesRetrieveFailed())
            processPartialRetrieve();
        else {
            auditInfoBuilder = new AuditInfoBuilder[1][];
            auditInfoBuilder[0] = buildAuditInfos(toBuildAuditInfo(true), ctx.getMatches());
        }
    }

    private void processPartialRetrieve() {
        auditInfoBuilder = new AuditInfoBuilder[2][];
        HashSet<InstanceLocations> failed = new HashSet<>();
        List<String> failedList = Arrays.asList(ctx.failedSOPInstanceUIDs());
        HashSet<InstanceLocations> success = new HashSet<>(ctx.getMatches());
        ctx.getMatches().forEach(instanceLocation -> {
            if (failedList.contains(instanceLocation.getSopInstanceUID())) {
                failed.add(instanceLocation);
                success.remove(instanceLocation);
            }
        });
        auditInfoBuilder[0] = buildAuditInfos(toBuildAuditInfo(true), failed);
        auditInfoBuilder[1] = buildAuditInfos(toBuildAuditInfo(false), success);
    }

    private AuditInfoBuilder[] buildAuditInfos(AuditInfoBuilder auditInfoBuilder, Collection<InstanceLocations> il) {
        LinkedHashSet<AuditInfoBuilder> objs = new LinkedHashSet<>();
        objs.add(auditInfoBuilder);
        objs.addAll(buildInstanceInfos(ctx.getCStoreForwards()));
        objs.addAll(buildInstanceInfos(il));
        return objs.toArray(new AuditInfoBuilder[0]);
    }

    private LinkedHashSet<AuditInfoBuilder> buildInstanceInfos(Collection<InstanceLocations> instanceLocations) {
        LinkedHashSet<AuditInfoBuilder> objs = new LinkedHashSet<>();
        instanceLocations.forEach(instanceLocation -> {
            Attributes attrs = instanceLocation.getAttributes();
            AuditInfoBuilder iI = new AuditInfoBuilder.Builder()
                    .studyUIDAccNumDate(attrs, arcDev)
                    .sopCUID(attrs.getString(Tag.SOPClassUID))
                    .sopIUID(attrs.getString(Tag.SOPInstanceUID))
                    .pIDAndName(attrs, arcDev)
                    .build();
            objs.add(iI);
        });
        return objs;
    }

    private AuditInfoBuilder toBuildAuditInfo(boolean checkForFailures) {
        AuditInfoBuilder.Builder infoBuilder = new AuditInfoBuilder.Builder()
                .warning(warning())
                .outcome(checkForFailures ? outcome() : null)
                .failedIUIDShow(isFailedIUIDShow(checkForFailures));

        return isExportTriggered(ctx)
                ? httpServletRequestInfo != null
                    ? restfulTriggeredExport(infoBuilder)
                    : schedulerTriggeredExport(infoBuilder)
                : httpServletRequestInfo != null
                    ? rad69OrWadoRS(infoBuilder)
                    : cMoveCGet(infoBuilder);
    }

    private AuditInfoBuilder cMoveCGet(AuditInfoBuilder.Builder infoBuilder) {
        return infoBuilder
            .calledUserID(ctx.getLocalAETitle())
            .destUserID(ctx.getDestinationAETitle())
            .destNapID(ctx.getDestinationHostName())
            .callingHost(ctx.getRequestorHostName())
            .moveUserID(ctx.getMoveOriginatorAETitle())
            .build();
    }

    private AuditInfoBuilder rad69OrWadoRS(AuditInfoBuilder.Builder infoBuilder) {
        return infoBuilder
            .calledUserID(httpServletRequestInfo.requestURI)
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
            .calledUserID(httpServletRequestInfo.requestURI)
            .destUserID(ctx.getDestinationAETitle())
            .destNapID(ctx.getDestinationHostName())
            .isExport()
            .build();
    }

    private boolean isFailedIUIDShow(boolean checkForFailures) {
        return checkForFailures && (allInstancesRetrieveFailed() || someInstancesRetrieveFailed());
    }

    private boolean isExportTriggered(RetrieveContext ctx) {
        return (ctx.getRequestAssociation() == null && ctx.getStoreAssociation() != null)
                || (ctx.getRequestAssociation() == null && ctx.getStoreAssociation() == null && ctx.getException() != null);
    }

    private boolean someInstancesRetrieveFailed() {
        return ctx.failedSOPInstanceUIDs().length != ctx.getMatches().size() && ctx.failedSOPInstanceUIDs().length > 0;
    }

    private boolean allInstancesRetrieveCompleted() {
        return (ctx.failedSOPInstanceUIDs().length == 0 && !ctx.getMatches().isEmpty())
                || (ctx.getMatches().isEmpty() && !ctx.getCStoreForwards().isEmpty());
    }

    private boolean allInstancesRetrieveFailed() {
        return ctx.failedSOPInstanceUIDs().length == ctx.getMatches().size() && !ctx.getMatches().isEmpty();
    }

    private String warning() {
        return allInstancesRetrieveCompleted() && ctx.warning() != 0
                ? ctx.warning() == ctx.getMatches().size()
                    ? "Warnings on retrieve of all instances"
                    : "Warnings on retrieve of " + ctx.warning() + " instances"
                : null;
    }

    private String outcome() {
        return ctx.getException() != null
                ? ctx.getException().getMessage() != null
                    ? ctx.getException().getMessage()
                    : ctx.getException().toString()
                : allInstancesRetrieveFailed()
                    ? "Unable to perform sub-operations on all instances"
                    : someInstancesRetrieveFailed()
                        ? "Retrieve of " + ctx.failed() + " objects failed"
                        : null;
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
        List<ParticipantObjectIdentificationBuilder> pois = new ArrayList<>();
        boolean showIUID = auditInfo.getField(AuditInfo.FAILED_IUID_SHOW) != null || auditLogger.isIncludeInstanceUID();
        study_instanceInfo.forEach(
                (studyUID, instanceInfo) -> pois.add(ParticipantObjectID.studyPOI(studyUID, instanceInfo, showIUID)));
        pois.add(ParticipantObjectID.patientPOI(reader));

        return AuditMessages.createMessage(
                EventID.toEventIdentification(auditLogger, path, eventType, auditInfo),
                activeParticipants(eventType, auditInfo, auditLogger),
                pois.toArray(new ParticipantObjectIdentificationBuilder[0]));
    }

    private static ActiveParticipantBuilder[] activeParticipants(
            AuditUtils.EventType eventType, AuditInfo auditInfo, AuditLogger auditLogger) {
        return auditInfo.getField(AuditInfo.MOVE_USER_ID) != null
                ? cMoveActiveParticipants(eventType, auditInfo, auditLogger)
                : auditInfo.getField(AuditInfo.IS_EXPORT) != null
                    ? exportActiveParticipants(eventType, auditInfo, auditLogger)
                    : cGetOrWadoRSOrRAD69ActiveParticipants(eventType, auditInfo, auditLogger);
    }

    private static ActiveParticipantBuilder[] cMoveActiveParticipants(
            AuditUtils.EventType eventType, AuditInfo auditInfo, AuditLogger auditLogger) {
        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[3];
        activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                auditInfo.getField(AuditInfo.CALLED_USERID),
                getLocalHostName(auditLogger))
                .userIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle)
                .altUserID(AuditLogger.processID())
                .roleIDCode(eventType.source)
                .build();
        activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(
                auditInfo.getField(AuditInfo.DEST_USER_ID),
                auditInfo.getField(AuditInfo.DEST_NAP_ID))
                .userIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle)
                .roleIDCode(eventType.destination)
                .build();
        activeParticipantBuilder[2] = new ActiveParticipantBuilder.Builder(
                auditInfo.getField(AuditInfo.MOVE_USER_ID),
                auditInfo.getField(AuditInfo.CALLING_HOST))
                .userIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle)
                .isRequester()
                .build();
        return activeParticipantBuilder;
    }

    private static ActiveParticipantBuilder[] exportActiveParticipants(
            AuditUtils.EventType eventType, AuditInfo auditInfo, AuditLogger auditLogger) {
        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[3];
        activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                auditInfo.getField(AuditInfo.DEST_USER_ID),
                auditInfo.getField(AuditInfo.DEST_NAP_ID))
                .userIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle)
                .roleIDCode(eventType.destination).build();
        String archiveUserID = auditInfo.getField(AuditInfo.CALLED_USERID);
        AuditMessages.UserIDTypeCode archiveUserIDTypeCode = archiveUserIDTypeCode(archiveUserID);
        if (auditInfo.getField(AuditInfo.CALLING_USERID) == null)
            activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(
                    archiveUserID,
                    getLocalHostName(auditLogger))
                    .userIDTypeCode(archiveUserIDTypeCode)
                    .altUserID(AuditLogger.processID())
                    .isRequester()
                    .roleIDCode(eventType.source)
                    .build();

        else {
            activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(
                    archiveUserID,
                    getLocalHostName(auditLogger))
                    .userIDTypeCode(archiveUserIDTypeCode)
                    .altUserID(AuditLogger.processID())
                    .roleIDCode(eventType.source)
                    .build();
            String callingUserID = auditInfo.getField(AuditInfo.CALLING_USERID);
            activeParticipantBuilder[2] = new ActiveParticipantBuilder.Builder(
                    callingUserID,
                    auditInfo.getField(AuditInfo.CALLING_HOST))
                    .userIDTypeCode(AuditMessages.userIDTypeCode(callingUserID))
                    .isRequester()
                    .build();
        }
        return activeParticipantBuilder;
    }

    private static ActiveParticipantBuilder[] cGetOrWadoRSOrRAD69ActiveParticipants(
            AuditUtils.EventType eventType, AuditInfo auditInfo, AuditLogger auditLogger) {
        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[2];
        String archiveUserID = auditInfo.getField(AuditInfo.CALLED_USERID);
        AuditMessages.UserIDTypeCode archiveUserIDTypeCode = archiveUserIDTypeCode(archiveUserID);
        activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                archiveUserID,
                getLocalHostName(auditLogger))
                .userIDTypeCode(archiveUserIDTypeCode)
                .altUserID(AuditLogger.processID())
                .roleIDCode(eventType.source)
                .build();
        String callingUserID = auditInfo.getField(AuditInfo.DEST_USER_ID);
        activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(
                callingUserID,
                auditInfo.getField(AuditInfo.DEST_NAP_ID))
                .userIDTypeCode(AuditService.remoteUserIDTypeCode(archiveUserIDTypeCode, callingUserID))
                .isRequester()
                .roleIDCode(eventType.destination)
                .build();
        return activeParticipantBuilder;
    }

    private static AuditMessages.UserIDTypeCode archiveUserIDTypeCode(String userID) {
        return  userID.indexOf('/') != -1
                ? AuditMessages.UserIDTypeCode.URI
                : AuditMessages.UserIDTypeCode.StationAETitle;
    }

    private static String getLocalHostName(AuditLogger auditLogger) {
        return auditLogger.getConnections().get(0).getHostname();
    }

    AuditInfoBuilder[][] getAuditInfoBuilder() {
        return auditInfoBuilder;
    }
}