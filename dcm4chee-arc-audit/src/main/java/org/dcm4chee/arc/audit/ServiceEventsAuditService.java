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

import org.dcm4che3.audit.ActiveParticipant;
import org.dcm4che3.audit.AuditMessages;
import org.dcm4che3.audit.EventIdentification;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4chee.arc.AssociationEvent;
import org.dcm4chee.arc.ConnectionEvent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2018
 */
class ServiceEventsAuditService extends AuditService {

    static void auditConnectionEvent(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        EventIdentification eventIdentification = getEventIdentification(auditInfo, eventType);
        eventIdentification.setEventDateTime(getEventTime(path, auditLogger));
        List<ActiveParticipant> activeParticipants = new ArrayList<>();
        if (auditInfo.getField(AuditInfo.SERVICE_EVENT_TYPE).equals(ConnectionEvent.Type.FAILED.name())) {
            activeParticipants.add(archiveRequestor(auditInfo));
            activeParticipants.add(remote(auditInfo));
            emitAuditMessage(auditLogger, eventIdentification, activeParticipants);
            return;
        }

        activeParticipants.add(remoteRequestor(auditInfo));
        activeParticipants.add(archive(auditInfo));
        emitAuditMessage(auditLogger, eventIdentification, activeParticipants);
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
        ei.getEventTypeCode().add(eventType.eventTypeCode);
        return ei;
    }

    private static ActiveParticipant remote(AuditInfo auditInfo) {
        ActiveParticipant remote = new ActiveParticipant();
        remote.setUserID(auditInfo.getField(AuditInfo.CALLED_USERID));
        remote.setUserIDTypeCode(AuditMessages.UserIDTypeCode.DeviceName);
        remote.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        String remoteHost = auditInfo.getField(AuditInfo.CALLED_HOST);
        remote.setNetworkAccessPointID(remoteHost);
        remote.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(remoteHost)
                        ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                        : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        return remote;
    }

    private static ActiveParticipant archiveRequestor(AuditInfo auditInfo) {
        ActiveParticipant archiveRequestor = new ActiveParticipant();
        archiveRequestor.setUserID(auditInfo.getField(AuditInfo.CALLING_USERID));
        archiveRequestor.setUserIDTypeCode(AuditMessages.UserIDTypeCode.DeviceName);
        archiveRequestor.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        archiveRequestor.setAlternativeUserID(AuditLogger.processID());
        String archiveRequestorHost = auditInfo.getField(AuditInfo.CALLING_HOST);
        archiveRequestor.setNetworkAccessPointID(archiveRequestorHost);
        archiveRequestor.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(archiveRequestorHost)
                        ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                        : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        archiveRequestor.setUserIsRequestor(true);
        return archiveRequestor;
    }

    private static ActiveParticipant archive(AuditInfo auditInfo) {
        ActiveParticipant archive = new ActiveParticipant();
        archive.setUserID(auditInfo.getField(AuditInfo.CALLED_USERID));
        archive.setUserIDTypeCode(AuditMessages.UserIDTypeCode.DeviceName);
        archive.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        archive.setAlternativeUserID(AuditLogger.processID());
        String archiveHost = auditInfo.getField(AuditInfo.CALLED_HOST);
        archive.setNetworkAccessPointID(archiveHost);
        archive.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(archiveHost)
                    ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                    : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        return archive;
    }

    private static ActiveParticipant remoteRequestor(AuditInfo auditInfo) {
        ActiveParticipant remoteRequestor = new ActiveParticipant();
        remoteRequestor.setUserID(auditInfo.getField(AuditInfo.CALLING_USERID));
        remoteRequestor.setUserIDTypeCode(AuditMessages.UserIDTypeCode.NodeID);
        remoteRequestor.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        remoteRequestor.setAlternativeUserID(AuditLogger.processID());
        String remoteRequestorHost = auditInfo.getField(AuditInfo.CALLING_HOST);
        remoteRequestor.setNetworkAccessPointID(remoteRequestorHost);
        remoteRequestor.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(remoteRequestorHost)
                        ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                        : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        remoteRequestor.setUserIsRequestor(true);
        return remoteRequestor;
    }

    static void auditAssociationEvent(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        EventIdentification eventIdentification = getEventIdentification(auditInfo, eventType);
        eventIdentification.setEventDateTime(getEventTime(path, auditLogger));
        List<ActiveParticipant> activeParticipants = new ArrayList<>();
        if (auditInfo.getField(AuditInfo.SERVICE_EVENT_TYPE).equals(AssociationEvent.Type.FAILED.name())) {
            activeParticipants.add(archiveAERequestor(auditInfo));
            activeParticipants.add(remoteAE(auditInfo));
            emitAuditMessage(auditLogger, eventIdentification, activeParticipants);
            return;
        }

        activeParticipants.add(archiveAE(auditInfo));
        activeParticipants.add(remoteAERequestor(auditInfo));
        emitAuditMessage(auditLogger, eventIdentification, activeParticipants);
    }

    private static ActiveParticipant archiveAERequestor(AuditInfo auditInfo) {
        ActiveParticipant archiveAERequestor = new ActiveParticipant();
        archiveAERequestor.setUserID(auditInfo.getField(AuditInfo.CALLING_USERID));
        archiveAERequestor.setUserIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle);
        archiveAERequestor.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        archiveAERequestor.setAlternativeUserID(AuditLogger.processID());
        String archiveAERequestorHost = auditInfo.getField(AuditInfo.CALLING_HOST);
        archiveAERequestor.setNetworkAccessPointID(archiveAERequestorHost);
        archiveAERequestor.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(archiveAERequestorHost)
                        ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                        : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        archiveAERequestor.setUserIsRequestor(true);
        return archiveAERequestor;
    }

    private static ActiveParticipant remoteAE(AuditInfo auditInfo) {
        ActiveParticipant remoteAE = new ActiveParticipant();
        remoteAE.setUserID(auditInfo.getField(AuditInfo.CALLED_USERID));
        remoteAE.setUserIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle);
        remoteAE.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        String remoteAEHost = auditInfo.getField(AuditInfo.CALLED_HOST);
        remoteAE.setNetworkAccessPointID(remoteAEHost);
        remoteAE.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(remoteAEHost)
                        ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                        : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        return remoteAE;
    }

    private static ActiveParticipant archiveAE(AuditInfo auditInfo) {
        ActiveParticipant archiveAE = new ActiveParticipant();
        archiveAE.setUserID(auditInfo.getField(AuditInfo.CALLED_USERID));
        archiveAE.setUserIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle);
        archiveAE.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        archiveAE.setAlternativeUserID(AuditLogger.processID());
        String archiveAEHost = auditInfo.getField(AuditInfo.CALLED_HOST);
        archiveAE.setNetworkAccessPointID(archiveAEHost);
        archiveAE.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(archiveAEHost)
                        ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                        : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        return archiveAE;
    }

    private static ActiveParticipant remoteAERequestor(AuditInfo auditInfo) {
        ActiveParticipant remoteAERequestor = new ActiveParticipant();
        remoteAERequestor.setUserID(auditInfo.getField(AuditInfo.CALLING_USERID));
        remoteAERequestor.setUserIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle);
        remoteAERequestor.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        String remoteAERequestorHost = auditInfo.getField(AuditInfo.CALLING_HOST);
        remoteAERequestor.setNetworkAccessPointID(remoteAERequestorHost);
        remoteAERequestor.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(remoteAERequestorHost)
                        ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                        : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        remoteAERequestor.setUserIsRequestor(true);
        return remoteAERequestor;
    }

}
