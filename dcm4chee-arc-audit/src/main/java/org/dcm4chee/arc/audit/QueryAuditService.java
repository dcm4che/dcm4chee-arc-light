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
import org.dcm4che3.audit.ParticipantObjectIdentification;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.UID;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.net.audit.AuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jan 2019
 */
class QueryAuditService extends AuditService {

    private final static Logger LOG = LoggerFactory.getLogger(QueryAuditService.class);

    static void audit(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(path))) {
            AuditInfo auditInfo = new AuditInfo(new DataInputStream(in).readUTF());
            EventIdentification eventIdentification = getEventIdentification(auditInfo, eventType);
            eventIdentification.setEventDateTime(getEventTime(path, auditLogger));

            List<ActiveParticipant> activeParticipants = new ArrayList<>();
            String archiveCalledUserID = auditInfo.getField(AuditInfo.CALLED_USERID);
            boolean qidoTrigger = archiveCalledUserID.contains("/");
            activeParticipants.add(archive(archiveCalledUserID,
                                            qidoTrigger
                                                ? AuditMessages.UserIDTypeCode.URI
                                                : AuditMessages.UserIDTypeCode.StationAETitle,
                                            eventType,
                                            auditLogger));
            activeParticipants.add(qidoTrigger
                                    ? requestor(auditInfo, eventType)
                                    : requestorAE(auditInfo, eventType));

            ParticipantObjectIdentification query = qidoTrigger
                                                    ? qidoQuery(auditInfo)
                                                    : cFindQuery(auditInfo, cFindQueryData(path, in));
            emitAuditMessage(auditLogger, eventIdentification, activeParticipants, query);
        } catch (Exception e) {
            LOG.info("Failed to audit query event {} for {} \n", eventType, path, e);
        }
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
        return ei;
    }

    static ParticipantObjectIdentification qidoQuery(AuditInfo auditInfo) {
        ParticipantObjectIdentification poi = new ParticipantObjectIdentification();
        poi.setParticipantObjectID(auditInfo.getField(AuditInfo.Q_POID));
        poi.setParticipantObjectIDTypeCode(AuditMessages.ParticipantObjectIDTypeCode.REST);
        poi.setParticipantObjectTypeCode(AuditMessages.ParticipantObjectTypeCode.SystemObject);
        poi.setParticipantObjectTypeCodeRole(AuditMessages.ParticipantObjectTypeCodeRole.Query);
        poi.getParticipantObjectDetail()
            .add(AuditMessages.createParticipantObjectDetail("QueryEncoding", StandardCharsets.UTF_8.name()));
        if (auditInfo.getField(AuditInfo.Q_STRING) != null)
            poi.setParticipantObjectQuery(auditInfo.getField(AuditInfo.Q_STRING).getBytes());
        return poi;
    }

    static ParticipantObjectIdentification cFindQuery(AuditInfo auditInfo, byte[] data) {
        ParticipantObjectIdentification poi = new ParticipantObjectIdentification();
        poi.setParticipantObjectID(auditInfo.getField(AuditInfo.Q_POID));
        poi.setParticipantObjectIDTypeCode(AuditMessages.ParticipantObjectIDTypeCode.SOPClassUID);
        poi.setParticipantObjectTypeCode(AuditMessages.ParticipantObjectTypeCode.SystemObject);
        poi.setParticipantObjectTypeCodeRole(AuditMessages.ParticipantObjectTypeCodeRole.Report);
        poi.getParticipantObjectDetail()
            .add(AuditMessages.createParticipantObjectDetail("TransferSyntax", UID.ImplicitVRLittleEndian));
        poi.setParticipantObjectQuery(data);
        Attributes cFindQueryKeys = cFindQueryKeys(data);
        if (cFindQueryKeys != null)
            poi.getParticipantObjectDetail()
                .add(AuditMessages.createParticipantObjectDetail("CFindQueryReturnKeys", cFindQueryKeys.toString()));
        return poi;
    }

    private static byte[] cFindQueryData(Path path, InputStream in) throws IOException {
        byte[] buffer = new byte[(int) Files.size(path)];
        int len = in.read(buffer);
        byte[] data;
        if (len != -1) {
            data = new byte[len];
            System.arraycopy(buffer, 0, data, 0, len);
        } else {
            data = new byte[0];
        }
        return data;
    }

    private static Attributes cFindQueryKeys(byte[] data) {
        try {
            DicomInputStream dis = new DicomInputStream(new ByteArrayInputStream(data));
            return dis.readDataset();
        } catch (IOException e) {
            LOG.info("Not a DICOM stream \n", e);
        }
        return null;
    }

    private static ActiveParticipant archive(
            String archiveUserID, AuditMessages.UserIDTypeCode archiveUserIDTypeCode, AuditUtils.EventType eventType,
            AuditLogger auditLogger) {
        ActiveParticipant archive = new ActiveParticipant();
        archive.setUserID(archiveUserID);
        archive.setAlternativeUserID(AuditLogger.processID());
        archive.setUserIsRequestor(archiveUserIDTypeCode == AuditMessages.UserIDTypeCode.DeviceName);
        archive.setUserIDTypeCode(archiveUserIDTypeCode);
        archive.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        String auditLoggerHostName = auditLogger.getConnections().get(0).getHostname();
        archive.setNetworkAccessPointID(auditLoggerHostName);
        archive.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(auditLoggerHostName)
                        ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                        : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        if (archiveUserIDTypeCode != AuditMessages.UserIDTypeCode.DeviceName)
            archive.getRoleIDCode().add(eventType.destination);
        return archive;
    }

    private static ActiveParticipant requestor(AuditInfo auditInfo, AuditUtils.EventType eventType) {
        ActiveParticipant requestor = new ActiveParticipant();
        String requestorID = auditInfo.getField(AuditInfo.CALLING_USERID);
        requestor.setUserID(requestorID);
        requestor.setUserIsRequestor(true);
        requestor.setUserIDTypeCode(AuditMessages.isIP(requestorID)
                ? AuditMessages.UserIDTypeCode.NodeID
                : AuditMessages.UserIDTypeCode.PersonID);
        requestor.setUserTypeCode(AuditMessages.UserTypeCode.Person);
        requestor.getRoleIDCode().add(eventType.source);

        String requestorHost = auditInfo.getField(AuditInfo.CALLING_HOST);
        requestor.setNetworkAccessPointID(requestorHost);
        requestor.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(requestorHost)
                        ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                        : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        return requestor;
    }

    private static ActiveParticipant requestorAE(AuditInfo auditInfo, AuditUtils.EventType eventType) {
        ActiveParticipant requestorAE = new ActiveParticipant();
        String requestorID = auditInfo.getField(AuditInfo.CALLING_USERID);
        requestorAE.setUserID(requestorID);
        requestorAE.setUserIsRequestor(true);
        requestorAE.setUserIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle);
        requestorAE.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        requestorAE.getRoleIDCode().add(eventType.source);

        String requestorHost = auditInfo.getField(AuditInfo.CALLING_HOST);
        requestorAE.setNetworkAccessPointID(requestorHost);
        requestorAE.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(requestorHost)
                        ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                        : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        return requestorAE;
    }

    static void auditStudySize(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
        SpoolFileReader reader = new SpoolFileReader(path.toFile());
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        EventIdentification eventIdentification = getEventIdentification(auditInfo, eventType);
        eventIdentification.setEventDateTime(getEventTime(path, auditLogger));
        ParticipantObjectIdentification study = study(auditInfo);
        ParticipantObjectIdentification patient = patient(auditInfo);
        ActiveParticipant archive = archive(auditInfo.getField(AuditInfo.CALLING_USERID),
                                            AuditMessages.UserIDTypeCode.DeviceName,
                                            eventType,
                                            auditLogger);
        emitAuditMessage(auditLogger, eventIdentification, Collections.singletonList(archive), study, patient);
    }

    private static ParticipantObjectIdentification study(AuditInfo auditInfo) {
        ParticipantObjectIdentification study = new ParticipantObjectIdentification();
        study.setParticipantObjectID(auditInfo.getField(AuditInfo.STUDY_UID));
        study.setParticipantObjectIDTypeCode(AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID);
        study.setParticipantObjectTypeCode(AuditMessages.ParticipantObjectTypeCode.SystemObject);
        study.setParticipantObjectTypeCodeRole(AuditMessages.ParticipantObjectTypeCodeRole.Report);
        study.setParticipantObjectDataLifeCycle(AuditMessages.ParticipantObjectDataLifeCycle.AggregationSummarizationDerivation);
        return study;
    }

    private static ParticipantObjectIdentification patient(AuditInfo auditInfo) {
        ParticipantObjectIdentification patient = new ParticipantObjectIdentification();
        patient.setParticipantObjectID(auditInfo.getField(AuditInfo.P_ID));
        patient.setParticipantObjectIDTypeCode(AuditMessages.ParticipantObjectIDTypeCode.PatientNumber);
        patient.setParticipantObjectTypeCode(AuditMessages.ParticipantObjectTypeCode.Person);
        patient.setParticipantObjectTypeCodeRole(AuditMessages.ParticipantObjectTypeCodeRole.Patient);
        patient.setParticipantObjectName(auditInfo.getField(AuditInfo.P_NAME));
        return patient;
    }

}
