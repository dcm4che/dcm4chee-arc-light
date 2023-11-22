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

import org.dcm4che3.audit.ActiveParticipant;
import org.dcm4che3.audit.AuditMessages;
import org.dcm4che3.audit.EventIdentification;
import org.dcm4che3.net.audit.AuditLogger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jan 2019
 */
class ApplicationActivityAuditService extends AuditService {

    static void auditMsg(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        EventIdentification eventIdentification = getEventIdentification(auditInfo, eventType);
        eventIdentification.setEventDateTime(getEventTime(path, auditLogger));
        ActiveParticipant archive = archive(auditInfo, eventType, auditLogger);
        if (auditInfo.getField(AuditInfo.CALLING_USERID) == null) {
            emitAuditMessage(auditLogger, eventIdentification, Collections.singletonList(archive));
            return;
        }

        List<ActiveParticipant> activeParticipants = new ArrayList<>();
        activeParticipants.add(archive);
        activeParticipants.add(personOrProcess(auditInfo, eventType));
        emitAuditMessage(auditLogger, eventIdentification, activeParticipants);
    }

    private static EventIdentification getEventIdentification(AuditInfo auditInfo, AuditUtils.EventType eventType) {
        String outcomeDesc = auditInfo.getField(AuditInfo.OUTCOME);
        EventIdentification ei = new EventIdentification();
        ei.setEventID(eventType.eventID);
        ei.setEventActionCode(eventType.eventActionCode);
        ei.setEventOutcomeDescription(outcomeDesc);
        ei.setEventOutcomeIndicator(outcomeDesc == null
                ? AuditMessages.EventOutcomeIndicator.Success
                : AuditMessages.EventOutcomeIndicator.MinorFailure);
        ei.getEventTypeCode().add(eventType.eventTypeCode);
        return ei;
    }

    private static ActiveParticipant archive(AuditInfo auditInfo, AuditUtils.EventType eventType, AuditLogger auditLogger) {
        ActiveParticipant archive = new ActiveParticipant();
        String archiveUserID = auditInfo.getField(AuditInfo.CALLED_USERID);
        archive.setUserID(archiveUserID);
        archive.setUserIsRequestor(!archiveUserID.contains("/"));
        archive.setUserIDTypeCode(archiveUserID.contains("/")
                                    ? AuditMessages.UserIDTypeCode.URI
                                    : AuditMessages.UserIDTypeCode.DeviceName);
        archive.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        archive.setAlternativeUserID(AuditLogger.processID());
        archive.getRoleIDCode().add(eventType.destination);
        
        String auditLoggerHostName = auditLogger.getConnections().get(0).getHostname();
        archive.setNetworkAccessPointID(auditLoggerHostName);
        archive.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(auditLoggerHostName)
                        ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                        : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        return archive;
    }

    private static ActiveParticipant personOrProcess(AuditInfo auditInfo, AuditUtils.EventType eventType) {
        ActiveParticipant personOrProcess = new ActiveParticipant();
        String personOrProcessID = auditInfo.getField(AuditInfo.CALLING_USERID);
        String networkAccessPointID = auditInfo.getField(AuditInfo.CALLING_HOST);
        personOrProcess.setUserID(personOrProcessID);
        personOrProcess.setAlternativeUserID(AuditLogger.processID());
        personOrProcess.setNetworkAccessPointID(networkAccessPointID);
        personOrProcess.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(networkAccessPointID)
                    ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                    : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        personOrProcess.setUserIsRequestor(true);
        personOrProcess.setUserIDTypeCode(
                AuditMessages.isIP(personOrProcessID)
                    ? AuditMessages.UserIDTypeCode.NodeID
                    : AuditMessages.UserIDTypeCode.PersonID);
        personOrProcess.setUserTypeCode(AuditMessages.UserTypeCode.Person);
        personOrProcess.getRoleIDCode().add(eventType.source);
        return personOrProcess;
    }
}
