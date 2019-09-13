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
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.delete.StudyDeleteContext;
import org.dcm4chee.arc.entity.Instance;
import org.dcm4chee.arc.event.RejectionNoteSent;
import org.dcm4chee.arc.keycloak.KeycloakContext;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreSession;

import javax.servlet.http.HttpServletRequest;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Oct 2018
 */
class DeletionAuditService {

    static AuditInfoBuilder[] instancesDeletedAuditInfo(StoreContext ctx, ArchiveDeviceExtension arcDev) {
        StoreSession storeSession = ctx.getStoreSession();
        Attributes attr = ctx.getAttributes();
        boolean isSchedulerDeletedExpiredStudies = storeSession.getAssociation() == null
                && storeSession.getHttpRequest() == null;

        AuditInfoBuilder.Builder infoBuilder = new AuditInfoBuilder.Builder()
                .studyUIDAccNumDate(attr, arcDev)
                .pIDAndName(attr, arcDev)
                .outcome(outcome(ctx))
                .warning(warning(ctx));

        List<AuditInfoBuilder> auditInfoBuilders = new ArrayList<>();
        auditInfoBuilders.add(isSchedulerDeletedExpiredStudies
                ? schedulerRejectedAuditInfo(infoBuilder, arcDev.getDevice().getDeviceName())
                : userRejectedAuditInfo(ctx, infoBuilder));
        buildRejectionSOPAuditInfo(auditInfoBuilders, attr);
        return auditInfoBuilders.toArray(new AuditInfoBuilder[0]);
    }

    private static AuditInfoBuilder schedulerRejectedAuditInfo(AuditInfoBuilder.Builder infoBuilder, String devName) {
        return infoBuilder.callingUserID(devName).build();
    }

    private static AuditInfoBuilder userRejectedAuditInfo(StoreContext ctx, AuditInfoBuilder.Builder infoBuilder) {
        StoreSession storeSession = ctx.getStoreSession();
        HttpServletRequestInfo req = storeSession.getHttpRequest();
        String callingAET = storeSession.getCallingAET();
        return infoBuilder
                .callingHost(storeSession.getRemoteHostName())
                .callingUserID(req != null
                        ? req.requesterUserID
                        : callingAET != null
                        ? callingAET : storeSession.getLocalApplicationEntity().getAETitle())
                .calledUserID(req != null ? req.requestURI : storeSession.getCalledAET())
                .build();
    }

    static AuditInfoBuilder[] externalRejectionAuditInfo(RejectionNoteSent rejectionNoteSent, ArchiveDeviceExtension arcDev) {
        Attributes attrs = rejectionNoteSent.getRejectionNote();
        List<AuditInfoBuilder> auditInfoBuilders = new ArrayList<>();
        auditInfoBuilders.add(externalRejectionClientAuditInfo(rejectionNoteSent, arcDev));
        buildRejectionSOPAuditInfo(auditInfoBuilders, attrs);
        return auditInfoBuilders.toArray(new AuditInfoBuilder[0]);
    }

    private static AuditInfoBuilder externalRejectionClientAuditInfo(
            RejectionNoteSent rejectionNoteSent, ArchiveDeviceExtension arcDev) {
        HttpServletRequest req = rejectionNoteSent.getRequest();
        Attributes attrs = rejectionNoteSent.getRejectionNote();
        Attributes codeItem = attrs.getSequence(Tag.ConceptNameCodeSequence).get(0);
        return new AuditInfoBuilder.Builder()
                .callingUserID(KeycloakContext.valueOf(req).getUserName())
                .callingHost(req.getRemoteHost())
                .calledUserID(req.getRequestURI())
                .calledHost(!rejectionNoteSent.getRemoteAE().getConnections().isEmpty()
                        ? rejectionNoteSent.getRemoteAE().getConnections().stream()
                        .map(Connection::getHostname)
                        .collect(Collectors.joining(";"))
                        : null)
                .outcome(rejectionNoteSent.failed() ? rejectionNoteSent.getErrorComment() : null)
                .warning(codeItem.getString(Tag.CodeMeaning))
                .studyUIDAccNumDate(attrs, arcDev)
                .pIDAndName(attrs, arcDev)
                .build();
    }

    private static void buildRejectionSOPAuditInfo(List<AuditInfoBuilder> auditInfoBuilders, Attributes attrs) {
        attrs.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence).forEach(studyRef ->
                studyRef.getSequence(Tag.ReferencedSeriesSequence).forEach(seriesRef ->
                        seriesRef.getSequence(Tag.ReferencedSOPSequence).forEach(sopRef ->
                                auditInfoBuilders.add(new AuditInfoBuilder.Builder()
                                        .sopCUID(sopRef.getString(Tag.ReferencedSOPClassUID))
                                        .sopIUID(sopRef.getString(Tag.ReferencedSOPInstanceUID)).build())
                        )
                )
        );
    }

    static AuditInfoBuilder[] studyDeletedAuditInfo(StudyDeleteContext ctx, ArchiveDeviceExtension arcDev) {
        HttpServletRequestInfo httpServletRequestInfo = ctx.getHttpServletRequestInfo();
        AuditInfoBuilder.Builder infoBuilder = new AuditInfoBuilder.Builder()
                .studyUIDAccNumDate(ctx.getStudy().getAttributes(), arcDev)
                .pIDAndName(ctx.getPatient().getAttributes(), arcDev)
                .outcome(outcome(ctx.getException()));

        AuditInfoBuilder[] auditInfoBuilders = new AuditInfoBuilder[ctx.getInstances().size() + 1];
        auditInfoBuilders[0] = httpServletRequestInfo != null
                ? userTriggeredPermDeletionAuditInfo(infoBuilder, httpServletRequestInfo)
                : schedulerTriggeredPermDeletionAuditInfo(infoBuilder, arcDev);
        buildSOPInstanceAuditInfo(auditInfoBuilders, ctx.getInstances());
        return auditInfoBuilders;
    }

    private static void buildSOPInstanceAuditInfo(AuditInfoBuilder[] auditInfoBuilder, List<Instance> instances) {
        int i = 1;
        for (Instance instance : instances) {
            auditInfoBuilder[i] = new AuditInfoBuilder.Builder()
                    .sopCUID(instance.getSopClassUID())
                    .sopIUID(instance.getSopInstanceUID()).build();
            i++;
        }
    }

    private static AuditInfoBuilder userTriggeredPermDeletionAuditInfo(
            AuditInfoBuilder.Builder infoBuilder, HttpServletRequestInfo httpServletRequestInfo) {
        return infoBuilder
                .callingUserID(httpServletRequestInfo.requesterUserID)
                .callingHost(httpServletRequestInfo.requesterHost)
                .calledUserID(httpServletRequestInfo.requestURI)
                .build();
    }

    private static AuditInfoBuilder schedulerTriggeredPermDeletionAuditInfo(
            AuditInfoBuilder.Builder infoBuilder, ArchiveDeviceExtension arcDev) {
        return infoBuilder.callingUserID(arcDev.getDevice().getDeviceName()).build();
    }

    private static String outcome(StoreContext ctx) {
        return ctx.getException() != null
                ? ctx.getRejectionNote() != null
                ? ctx.getRejectionNote().getRejectionNoteCode().getCodeMeaning() + " - " + ctx.getException().getMessage()
                : ctx.getException().getMessage()
                : null;
    }

    private static String warning(StoreContext ctx) {
        return ctx.getException() == null && ctx.getRejectionNote() != null
                ? ctx.getRejectionNote().getRejectionNoteCode().getCodeMeaning() : null;
    }

    private static String outcome(Exception e) {
        return e != null ? e.getMessage() : null;
    }

    static AuditMessage auditMsg(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        return AuditMessages.createMessage(
                EventID.toEventIdentification(auditLogger, path, eventType, auditInfo),
                activeParticipants(auditLogger, eventType, auditInfo),
                ParticipantObjectID.studyPatParticipants(auditInfo, reader.getInstanceLines(), eventType, auditLogger));
    }

    private static ActiveParticipantBuilder[] activeParticipants(
            AuditLogger auditLogger, AuditUtils.EventType eventType, AuditInfo auditInfo) {
        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[2];
        String callingUserID = auditInfo.getField(AuditInfo.CALLING_USERID);

        if (eventType.eventClass == AuditUtils.EventClass.USER_DELETED) {
            String archiveUserID = auditInfo.getField(AuditInfo.CALLED_USERID);
            AuditMessages.UserIDTypeCode archiveUserIDTypeCode = archiveUserIDTypeCode(archiveUserID);
            activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                    callingUserID,
                    auditInfo.getField(AuditInfo.CALLING_HOST))
                    .userIDTypeCode(AuditService.remoteUserIDTypeCode(archiveUserIDTypeCode, callingUserID))
                    .isRequester().build();
            activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(
                    archiveUserID,
                    getLocalHostName(auditLogger))
                    .userIDTypeCode(archiveUserIDTypeCode)
                    .altUserID(AuditLogger.processID())
                    .build();
        } else
            activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                    callingUserID,
                    getLocalHostName(auditLogger))
                    .userIDTypeCode(AuditMessages.UserIDTypeCode.DeviceName)
                    .altUserID(AuditLogger.processID())
                    .isRequester().build();
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
}