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
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4chee.arc.ConnectionEvent;

import java.nio.file.Path;
import java.util.Calendar;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Oct 2018
 */
class ConnectionEventsAuditService {

    static AuditInfoBuilder connFailureAuditInfo(ConnectionEvent event) {
        return event.getType() == ConnectionEvent.Type.FAILED
                ? connFailedAuditInfo(event) : connRejectedAuditInfo(event);
    }

    private static AuditInfoBuilder connRejectedAuditInfo(ConnectionEvent event) {
        String callingUser = event.getSocket().getRemoteSocketAddress().toString();
        return new AuditInfoBuilder.Builder()
                .callingUserID(callingUser)
                .callingHost(callingUser)
                .calledUserID(event.getConnection().getDevice().getDeviceName())
                .calledHost(event.getConnection().getHostname())
                .outcome(event.getException().getMessage())
                .connType(event.getType())
                .build();
    }

    private static AuditInfoBuilder connFailedAuditInfo(ConnectionEvent event) {
        return new AuditInfoBuilder.Builder()
                .callingUserID(event.getConnection().getDevice().getDeviceName())
                .callingHost(event.getConnection().getHostname())
                .calledUserID(event.getRemoteConnection().getHostname())
                .calledHost(event.getRemoteConnection().getHostname())
                .outcome(event.getException().getMessage())
                .connType(event.getType())
                .build();
    }

    static AuditMessage auditMsg(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        return AuditMessages.createMessage(
                EventID.toEventIdentification(auditLogger, path, eventType, auditInfo),
                activeParticipants(auditInfo));
    }

    private static ActiveParticipantBuilder[] activeParticipants(AuditInfo auditInfo) {
        ActiveParticipantBuilder[] activeParticipants = new ActiveParticipantBuilder[2];
        if (ConnectionEvent.Type.valueOf(auditInfo.getField(AuditInfo.CONN_TYPE)) == ConnectionEvent.Type.REJECTED) {
            activeParticipants[0] = new ActiveParticipantBuilder.Builder(
                    auditInfo.getField(AuditInfo.CALLED_USERID),
                    auditInfo.getField(AuditInfo.CALLED_HOST))
                    .userIDTypeCode(AuditMessages.UserIDTypeCode.DeviceName)
                    .altUserID(AuditLogger.processID())
                    .build();
            activeParticipants[1] = new ActiveParticipantBuilder.Builder(
                    auditInfo.getField(AuditInfo.CALLING_USERID),
                    auditInfo.getField(AuditInfo.CALLING_HOST))
                    .userIDTypeCode(AuditMessages.UserIDTypeCode.NodeID)
                    .isRequester().build();
        } else {
            activeParticipants[0] = new ActiveParticipantBuilder.Builder(
                    auditInfo.getField(AuditInfo.CALLED_USERID),
                    auditInfo.getField(AuditInfo.CALLED_HOST))
                    .userIDTypeCode(AuditMessages.UserIDTypeCode.NodeID)
                    .build();
            activeParticipants[1] = new ActiveParticipantBuilder.Builder(
                    auditInfo.getField(AuditInfo.CALLING_USERID),
                    auditInfo.getField(AuditInfo.CALLING_HOST))
                    .userIDTypeCode(AuditMessages.UserIDTypeCode.DeviceName)
                    .altUserID(AuditLogger.processID())
                    .isRequester().build();
        }
        return activeParticipants;
    }
}
