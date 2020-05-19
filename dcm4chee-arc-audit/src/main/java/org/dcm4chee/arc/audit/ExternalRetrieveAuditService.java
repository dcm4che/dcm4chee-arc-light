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
import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.IApplicationEntityCache;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.retrieve.ExternalRetrieveContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Feb 2019
 */
class ExternalRetrieveAuditService {
    private final static Logger LOG = LoggerFactory.getLogger(ExternalRetrieveAuditService.class);

    static AuditInfoBuilder auditInfo(ExternalRetrieveContext ctx, ArchiveDeviceExtension arcDev) {
        AuditInfoBuilder.Builder auditInfo = ctx.getHttpServletRequestInfo() == null
                                                ? auditInfoForScheduler(ctx)
                                                : auditInfoForRESTful(ctx);
        return auditInfo.callingHost(ctx.getRequesterHostName())
                .calledHost(ctx.getRemoteHostName())
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

    private static AuditInfoBuilder.Builder auditInfoForRESTful(ExternalRetrieveContext ctx) {
        return new AuditInfoBuilder.Builder()
                .callingUserID(ctx.getRequesterUserID())
                .calledUserID(ctx.getRemoteAET())
                .moveUserID(ctx.getRequestURI());
    }

    private static AuditInfoBuilder.Builder auditInfoForScheduler(ExternalRetrieveContext ctx) {
        return new AuditInfoBuilder.Builder()
                .callingUserID(ctx.getLocalAET())
                .findSCP(ctx.getFindSCP())
                .moveUserID(ctx.getRemoteAET());
    }
    
    static AuditMessage auditMsg(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType,
                                 IApplicationEntityCache aeCache) {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        return AuditMessages.createMessage(
                EventID.toEventIdentification(auditLogger, path, eventType, auditInfo),
                activeParticipants(auditLogger, eventType, auditInfo, aeCache),
                ParticipantObjectID.studyPOI(auditInfo.getField(AuditInfo.STUDY_UID))
                        .detail(AuditMessages.createParticipantObjectDetail(
                                "StudyDate", auditInfo.getField(AuditInfo.STUDY_DATE)))
                        .build());
    }
    
    private static ActiveParticipantBuilder[] activeParticipants(AuditLogger auditLogger, AuditUtils.EventType eventType,
                                                                 AuditInfo auditInfo, IApplicationEntityCache aeCache) {
        return auditInfo.getField(AuditInfo.CALLING_HOST) == null
                ? schedulerTriggeredAPs(auditLogger, eventType, auditInfo, aeCache)
                : restfulTriggeredAPs(auditLogger, eventType, auditInfo);
    }

    private static ActiveParticipantBuilder[] restfulTriggeredAPs(AuditLogger auditLogger, AuditUtils.EventType eventType,
                                                                 AuditInfo auditInfo) {
        ActiveParticipantBuilder[] activeParticipants = new ActiveParticipantBuilder[4];
        String userID = auditInfo.getField(AuditInfo.CALLING_USERID);
        activeParticipants[0] = new ActiveParticipantBuilder.Builder(userID, auditInfo.getField(AuditInfo.CALLING_HOST))
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

    private static ActiveParticipantBuilder[] schedulerTriggeredAPs(
            AuditLogger auditLogger, AuditUtils.EventType eventType, AuditInfo auditInfo, IApplicationEntityCache aeCache) {
        ActiveParticipantBuilder[] activeParticipants = new ActiveParticipantBuilder[4];
        String userID = auditInfo.getField(AuditInfo.CALLING_USERID);
        String findSCP = auditInfo.getField(AuditInfo.FIND_SCP);
        activeParticipants[0] = new ActiveParticipantBuilder.Builder(
                userID,
                getLocalHostName(auditLogger))
                .userIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle)
                .altUserID(AuditLogger.processID())
                .isRequester()
                .build();
        activeParticipants[1] = new ActiveParticipantBuilder.Builder(
                auditInfo.getField(AuditInfo.MOVE_USER_ID),
                auditInfo.getField(AuditInfo.CALLED_HOST))
                .userIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle)
                .roleIDCode(eventType.source)
                .build();
        activeParticipants[2] = new ActiveParticipantBuilder.Builder(
                auditInfo.getField(AuditInfo.DEST_USER_ID),
                auditInfo.getField(AuditInfo.DEST_NAP_ID))
                .userIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle)
                .roleIDCode(eventType.destination)
                .build();
        try {
            activeParticipants[3] = new ActiveParticipantBuilder.Builder(
                    findSCP,
                    aeCache.findApplicationEntity(findSCP).getConnections().get(0).getHostname())
                    .userIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle)
                    .build();
        } catch (ConfigurationException e) {
            LOG.info("Exception caught on getting hostname for C-FINDSCP : {}", e.getMessage());
        }
        return activeParticipants;
    }

    private static String getLocalHostName(AuditLogger auditLogger) {
        return auditLogger.getConnections().get(0).getHostname();
    }
}
