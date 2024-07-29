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
 * Portions created by the Initial Developer are Copyright (C) 2015-2020
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

import jakarta.json.Json;
import jakarta.json.stream.JsonGenerator;
import org.dcm4che3.audit.ActiveParticipant;
import org.dcm4che3.audit.AuditMessages;
import org.dcm4che3.audit.EventIdentification;
import org.dcm4che3.audit.ParticipantObjectIdentification;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4chee.arc.entity.Task;

import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2018
 */
class TaskAuditService extends AuditService {

    static void audit(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        EventIdentification eventIdentification = getEventIdentification(auditInfo, eventType);
        eventIdentification.setEventDateTime(getEventTime(path, auditLogger));

        if (auditInfo.getField(AuditInfo.TASK) == null) {
            emitBulkTasksAudit(auditInfo, auditLogger, eventIdentification);
            return;
        }

        List<ActiveParticipant> activeParticipants = new ArrayList<>();
        activeParticipants.add(archive(auditInfo.getField(AuditInfo.CALLED_USERID),
                                        AuditMessages.UserIDTypeCode.URI,
                                        auditLogger));
        activeParticipants.add(requestor(auditInfo));
        emitAuditMessage(auditLogger, eventIdentification, activeParticipants, task(auditInfo));
    }

    private static void emitBulkTasksAudit(
            AuditInfo auditInfo, AuditLogger auditLogger, EventIdentification eventIdentification) {
        ParticipantObjectIdentification bulkTasks = bulkTasks(auditInfo);
        if (auditInfo.getField(AuditInfo.CALLED_USERID) == null) {
            ActiveParticipant archive = archive(auditInfo.getField(AuditInfo.CALLING_USERID),
                                                AuditMessages.UserIDTypeCode.DeviceName,
                                                auditLogger);
            emitAuditMessage(auditLogger, eventIdentification, Collections.singletonList(archive), bulkTasks);
            return;
        }

        List<ActiveParticipant> activeParticipants = new ArrayList<>();
        activeParticipants.add(archive(auditInfo.getField(AuditInfo.CALLED_USERID),
                                AuditMessages.UserIDTypeCode.URI,
                                auditLogger));
        activeParticipants.add(requestor(auditInfo));
        emitAuditMessage(auditLogger, eventIdentification, activeParticipants, bulkTasks);
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

    private static ParticipantObjectIdentification task(AuditInfo auditInfo) {
        ParticipantObjectIdentification task = new ParticipantObjectIdentification();
        task.setParticipantObjectID(auditInfo.getField(AuditInfo.TASK_POID));
        task.setParticipantObjectIDTypeCode(AuditMessages.ParticipantObjectIDTypeCode.TASKS);
        task.setParticipantObjectTypeCode(AuditMessages.ParticipantObjectTypeCode.SystemObject);
        task.getParticipantObjectDetail()
            .add(AuditMessages.createParticipantObjectDetail("Task", auditInfo.getField(AuditInfo.TASK)));
        return task;
    }

    private static ParticipantObjectIdentification bulkTasks(AuditInfo auditInfo) {
        ParticipantObjectIdentification bulkTasks = new ParticipantObjectIdentification();
        bulkTasks.setParticipantObjectID(auditInfo.getField(AuditInfo.TASK_POID));
        bulkTasks.setParticipantObjectIDTypeCode(AuditMessages.ParticipantObjectIDTypeCode.TASKS);
        bulkTasks.setParticipantObjectTypeCode(AuditMessages.ParticipantObjectTypeCode.SystemObject);
        bulkTasks.getParticipantObjectDetail()
                 .add(AuditMessages.createParticipantObjectDetail("Filters", auditInfo.getField(AuditInfo.FILTERS)));
        bulkTasks.getParticipantObjectDetail()
                 .add(AuditMessages.createParticipantObjectDetail("QueueName", auditInfo.getField(AuditInfo.QUEUE_NAME)));
        bulkTasks.getParticipantObjectDetail()
                 .add(AuditMessages.createParticipantObjectDetail("Count", auditInfo.getField(AuditInfo.COUNT)));
        bulkTasks.getParticipantObjectDetail()
                 .add(AuditMessages.createParticipantObjectDetail("Failed", auditInfo.getField(AuditInfo.FAILED)));
        return bulkTasks;
    }

    private static ActiveParticipant archive(
            String archiveUserID, AuditMessages.UserIDTypeCode archiveUserIDTypeCode, AuditLogger auditLogger) {
        ActiveParticipant archive = new ActiveParticipant();
        archive.setUserID(archiveUserID);
        archive.setUserIDTypeCode(archiveUserIDTypeCode);
        archive.setAlternativeUserID(AuditLogger.processID());
        archive.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        archive.setUserIsRequestor(archiveUserIDTypeCode == AuditMessages.UserIDTypeCode.DeviceName);
        String auditLoggerHostName = auditLogger.getConnections().get(0).getHostname();
        archive.setNetworkAccessPointID(auditLoggerHostName);
        archive.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(auditLoggerHostName)
                        ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                        : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        return archive;
    }

    private static ActiveParticipant requestor(AuditInfo auditInfo) {
        String requestorID = auditInfo.getField(AuditInfo.CALLING_USERID);
        String requestorHost = auditInfo.getField(AuditInfo.CALLING_HOST);
        ActiveParticipant requestor = new ActiveParticipant();
        requestor.setUserID(requestorID);
        requestor.setUserIDTypeCode(
                AuditMessages.isIP(requestorID)
                    ? AuditMessages.UserIDTypeCode.NodeID
                    : AuditMessages.UserIDTypeCode.PersonID);
        requestor.setUserIsRequestor(true);
        requestor.setNetworkAccessPointID(requestorHost);
        requestor.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(requestorHost)
                    ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                    : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        return requestor;
    }

    static String toString(Task task) {
        StringWriter w = new StringWriter(256);
        try (JsonGenerator gen = Json.createGenerator(w)){
            task.writeAsJSON(gen);
        }
        return w.toString();
    }
}
