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

import org.dcm4che3.audit.ActiveParticipantBuilder;
import org.dcm4che3.audit.AuditMessage;
import org.dcm4che3.audit.AuditMessages;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.keycloak.KeycloakContext;
import org.dcm4chee.arc.study.StudyMgtContext;

import javax.servlet.http.HttpServletRequest;
import java.nio.file.Path;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Oct 2019
 */
class StudyRecordAuditService {
    private StudyMgtContext studyMgtCtx;
    private AuditInfoBuilder.Builder infoBuilder;

    StudyRecordAuditService(StudyMgtContext ctx, ArchiveDeviceExtension arcDev) {
        studyMgtCtx = ctx;
        infoBuilder = new AuditInfoBuilder.Builder()
                .callingHost(studyMgtCtx.getRemoteHostName())
                .studyUIDAccNumDate(studyMgtCtx.getAttributes(), arcDev)
                .pIDAndName(studyMgtCtx.getAttributes(), arcDev)
                .outcome(outcome(studyMgtCtx.getException()))
                .expirationDate(ctx.getExpirationDate() != null ? ctx.getExpirationDate().toString() : null)
                .studyAccessCtrlID(ctx.getAccessControlID());
    }

    AuditInfoBuilder getStudyUpdateAuditInfo() {
        return studyMgtCtx.getHttpRequest() != null ? studyExpiredByWeb() : studyExpiredByHL7();
    }

    private AuditInfoBuilder studyExpiredByHL7() {
        HL7Segment msh = studyMgtCtx.getUnparsedHL7Message().msh();
        return infoBuilder
                .callingUserID(msh.getSendingApplicationWithFacility())
                .calledUserID(msh.getReceivingApplicationWithFacility())
                .build();
    }

    private AuditInfoBuilder studyExpiredByWeb() {
        HttpServletRequest request = studyMgtCtx.getHttpRequest();
        return infoBuilder
                .callingUserID(KeycloakContext.valueOf(request).getUserName())
                .calledUserID(request.getRequestURI())
                .build();
    }

    static AuditMessage auditMsg(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
        SpoolFileReader reader = new SpoolFileReader(path.toFile());
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        return AuditMessages.createMessage(
                EventID.toEventIdentification(auditLogger, path, eventType, auditInfo),
                activeParticipants(auditLogger, auditInfo),
                ParticipantObjectID.studyPatParticipants(auditInfo, reader, auditLogger));
    }

    private static ActiveParticipantBuilder[] activeParticipants(AuditLogger auditLogger, AuditInfo auditInfo) {
        ActiveParticipantBuilder[] activeParticipants = new ActiveParticipantBuilder[2];
        String callingUserID = auditInfo.getField(AuditInfo.CALLING_USERID);
        String calledUserID = auditInfo.getField(AuditInfo.CALLED_USERID);
        AuditMessages.UserIDTypeCode calledUserIDTypeCode = userIDTypeCode(calledUserID);
        activeParticipants[0] = new ActiveParticipantBuilder.Builder(callingUserID,
                auditInfo.getField(AuditInfo.CALLING_HOST))
                .userIDTypeCode(AuditService.remoteUserIDTypeCode(calledUserIDTypeCode, callingUserID))
                .isRequester()
                .build();
        activeParticipants[1] = new ActiveParticipantBuilder.Builder(
                calledUserID,
                getLocalHostName(auditLogger))
                .userIDTypeCode(calledUserIDTypeCode)
                .altUserID(AuditLogger.processID())
                .build();
        return activeParticipants;
    }

    private static AuditMessages.UserIDTypeCode userIDTypeCode(String userID) {
        return userID.indexOf('/') != -1
                ? AuditMessages.UserIDTypeCode.URI
                : AuditMessages.UserIDTypeCode.ApplicationFacility;
    }

    private static String getLocalHostName(AuditLogger auditLogger) {
        return auditLogger.getConnections().get(0).getHostname();
    }

    private static String outcome(Exception e) {
        return e != null ? e.getMessage() : null;
    }
}
