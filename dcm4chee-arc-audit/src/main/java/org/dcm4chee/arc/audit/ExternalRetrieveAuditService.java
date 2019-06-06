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
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.retrieve.ExternalRetrieveContext;

import java.nio.file.Path;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Feb 2019
 */
class ExternalRetrieveAuditService {

    static AuditInfoBuilder auditInfo(ExternalRetrieveContext ctx, ArchiveDeviceExtension arcDev) {
        return new AuditInfoBuilder.Builder()
                .callingUserID(ctx.getRequesterUserID())
                .callingHost(ctx.getRequesterHostName())
                .calledHost(ctx.getRemoteHostName())
                .calledUserID(ctx.getRemoteAET())
                .moveUserID(ctx.getRequestURI())
                .destUserID(ctx.getDestinationAET())
                .studyUIDAccNumDate(ctx.getKeys(), arcDev)
                .warning(ctx.warning() > 0
                            ? "Number Of Warning Sub operations" + ctx.warning()
                            : null)
                .outcome(ctx.getResponse().getString(Tag.ErrorComment) != null
                            ? ctx.getResponse().getString(Tag.ErrorComment) + ctx.failed()
                            : null)
                .build();
    }
    
    static AuditMessage auditMsg(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        return AuditMessages.createMessage(
                EventID.toEventIdentification(auditLogger, path, eventType, auditInfo),
                activeParticipants(auditLogger, eventType, auditInfo),
                ParticipantObjectID.studyPOI(auditInfo).build());
    }
    
    private static ActiveParticipantBuilder[] activeParticipants(AuditLogger auditLogger, AuditUtils.EventType eventType,
                                                                 AuditInfo auditInfo) {
        ActiveParticipantBuilder[] activeParticipants = new ActiveParticipantBuilder[4];
        String userID = auditInfo.getField(AuditInfo.CALLING_USERID);
        activeParticipants[0] = new ActiveParticipantBuilder.Builder(
                userID,
                auditInfo.getField(AuditInfo.CALLING_HOST))
                .userIDTypeCode(AuditMessages.userIDTypeCode(userID))
                .isRequester()
                .build();
        activeParticipants[1] = new ActiveParticipantBuilder.Builder(
                auditInfo.getField(AuditInfo.MOVE_USER_ID),
                getLocalHostName(auditLogger))
                .userIDTypeCode(AuditMessages.UserIDTypeCode.URI)
                .altUserID(AuditLogger.processID())
                .build();
        activeParticipants[2] = new ActiveParticipantBuilder.Builder(
                auditInfo.getField(AuditInfo.CALLED_USERID),
                auditInfo.getField(AuditInfo.CALLED_HOST))
                .userIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle)
                .roleIDCode(eventType.source)
                .build();
        activeParticipants[3] = new ActiveParticipantBuilder.Builder(
                auditInfo.getField(AuditInfo.DEST_USER_ID),
                auditInfo.getField(AuditInfo.DEST_NAP_ID))
                .userIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle)
                .roleIDCode(eventType.destination)
                .build();
        return activeParticipants;
    }

    private static String getLocalHostName(AuditLogger auditLogger) {
        return auditLogger.getConnections().get(0).getHostname();
    }
}
