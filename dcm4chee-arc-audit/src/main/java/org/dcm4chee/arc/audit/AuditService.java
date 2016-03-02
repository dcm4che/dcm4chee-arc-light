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
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * J4Care.
 * Portions created by the Initial Developer are Copyright (C) 2013
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
import org.dcm4che3.data.*;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.delete.StudyDeleteContext;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.retrieve.InstanceLocations;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.security.action.GetPropertyAction;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

import static java.security.AccessController.doPrivileged;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Feb 2016
 */
@ApplicationScoped
public class AuditService {
    private static final Logger LOG = LoggerFactory.getLogger(AuditService.class);
    private static final String tmpdir = doPrivileged(new GetPropertyAction("java.io.tmpdir"));
    private static final String noValue = "<none>";

    @Inject
    private Device device;

    private AuditLogger log() {
        return device.getDeviceExtension(AuditLogger.class);
    }

    public void aggregateAuditMessage(Path path) {
        AuditServiceUtils.EventType eventType = AuditServiceUtils.EventType.fromFile(path);
        String triggerType = String.valueOf(eventType).substring(0, 5);
        switch (triggerType) {
            case "CONN_":
                auditConnectionRejected(path, eventType);
                break;
            case "STORE":
                aggregateStoreOrWADORetrieve(path, eventType);
                break;
            case "WADO_":
                aggregateStoreOrWADORetrieve(path, eventType);
                break;
            case "BEGIN":
                auditRetrieve(path, eventType);
                break;
            case "TRF__":
                auditRetrieve(path, eventType);
                break;
            case "DELET":
                auditInstanceDeletion(path, eventType);
                break;
            case "QUERY":
                auditQuery(path, eventType);
                break;
        }
    }

    private void emitAuditMessage(Calendar timestamp, AuditMessage msg) {
        try {
            log().write(timestamp, msg);
        } catch (Exception e) {
            LOG.warn("Failed to emit audit message", e);
        }
    }


    public void auditApplicationActivity(AuditServiceUtils.EventType eventType, HttpServletRequest req) {
        Calendar timestamp = log().timeStamp();
        AuditMessage msg = new AuditMessage();
        msg.setEventIdentification(AuditMessages.createEventIdentification(
                eventType.eventID, eventType.eventActionCode, timestamp, eventType.outcomeIndicator, null,
                eventType.eventTypeCode));
        msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(
                device.getDeviceName(), AuditMessages.alternativeUserIDForAETitle(
                        device.getApplicationAETitles().toArray(new String[device.getApplicationAETitles().size()])),
                null, false, device.getDeviceName(), AuditMessages.NetworkAccessPointTypeCode.IPAddress, null,
                AuditMessages.RoleIDCode.Application));
        if (req != null) {
            msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(
                    req.getRemoteUser() != null ? req.getRemoteUser() : req.getRemoteAddr(), null, null, true,
                    req.getRemoteAddr(), AuditMessages.NetworkAccessPointTypeCode.IPAddress, null,
                    AuditMessages.RoleIDCode.ApplicationLauncher));
        }
        msg.getAuditSourceIdentification().add(log().createAuditSourceIdentification());
        emitAuditMessage(timestamp, msg);
    }

    public void collateInstancesDeleted(StoreContext ctx) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        boolean auditAggregate = arcDev.isAuditAggregate();
        Path dir = Paths.get(
                auditAggregate ? StringUtils.replaceSystemProperties(arcDev.getAuditSpoolDirectory()) : tmpdir);
        AuditServiceUtils.EventType et = (ctx.getException() != null)
                ? AuditServiceUtils.EventType.DELETE_ERR : AuditServiceUtils.EventType.DELETE_PAS;
        Attributes attrs = ctx.getAttributes();
        try {
            Path file = Files.createTempFile(dir, String.valueOf(et), null);
            boolean append = Files.exists(file);
            if (!append)
                Files.createDirectories(dir);
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.APPEND)) {
                writer.write(new AuditServiceUtils.DeleteInfo(ctx).toString());
                writer.newLine();
                HashMap<String, HashSet<String>> sopClassMap = new HashMap<>();
                for (Attributes studyRef : attrs.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence)) {
                    for (Attributes seriesRef : studyRef.getSequence(Tag.ReferencedSeriesSequence)) {
                        for (Attributes sopRef : seriesRef.getSequence(Tag.ReferencedSOPSequence)) {
                            String cuid = sopRef.getString(Tag.ReferencedSOPClassUID);
                            HashSet<String> iuids = sopClassMap.get(cuid);
                            if (iuids == null) {
                                iuids = new HashSet<>();
                                sopClassMap.put(cuid, iuids);
                            }
                            iuids.add(sopRef.getString(Tag.ReferencedSOPInstanceUID));
                        }
                    }
                }
                for (Map.Entry<String, HashSet<String>> entry : sopClassMap.entrySet()) {
                    writer.write(new AuditServiceUtils.DeleteStudyInfo(entry.getKey(), String.valueOf(entry.getValue().size())).toString());
                    writer.newLine();
                }
            }
            if (!auditAggregate)
                aggregateAuditMessage(file);
        } catch (Exception e) {
            LOG.warn("Failed to write to Audit Spool File - {} ", e);
        }
    }

    public void spoolStudyDeleted(StudyDeleteContext ctx) {
//        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
//        boolean auditAggregate = arcDev.isAuditAggregate();
//        Path dir = Paths.get(
//                auditAggregate ? StringUtils.replaceSystemProperties(arcDev.getAuditSpoolDirectory()) : tmpdir);
//        AuditServiceUtils.EventType eventType = (ctx.getException() != null)
//                ? AuditServiceUtils.EventType.PERM_DEL_E : AuditServiceUtils.EventType.PERM_DEL_S;
//        try {
//            Path file = Files.createTempFile(dir, String.valueOf(eventType), null);
//            boolean append = Files.exists(file);
//            if (!append)
//                Files.createDirectories(dir);
//            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
//                    StandardOpenOption.APPEND)) {
//                writer.write(new AuditServiceUtils.PermanentDeletionInfo(ctx).toString());
//                writer.newLine();
//            }
//        } catch (Exception e) {
//            LOG.warn("Failed to write to Audit Spool File - {} ", e);
//        }
    }


    private void auditInstanceDeletion(Path path, AuditServiceUtils.EventType eventType) {
        Calendar timestamp = log().timeStamp();
        AuditMessage msg = new AuditMessage();
        AuditServiceUtils.DeleteInfo deleteInfo;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            deleteInfo = new AuditServiceUtils.DeleteInfo(reader.readLine());
            msg.setEventIdentification(AuditMessages.createEventIdentification(
                    eventType.eventID, eventType.eventActionCode, timestamp,
                    eventType.outcomeIndicator, deleteInfo.getField(AuditServiceUtils.DeleteInfo.OUTCOME)));
            msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(
                    deleteInfo.getField(AuditServiceUtils.DeleteInfo.REMOTEHOST), AuditMessages.alternativeUserIDForAETitle(
                            deleteInfo.getField(AuditServiceUtils.DeleteInfo.REMOTEAET)), null, eventType.isSource,
                    deleteInfo.getField(AuditServiceUtils.DeleteInfo.REMOTEHOST),
                    AuditMessages.NetworkAccessPointTypeCode.IPAddress, null));
            msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(
                    deleteInfo.getField(AuditServiceUtils.DeleteInfo.LOCALHOST),
                    AuditMessages.alternativeUserIDForAETitle(deleteInfo.getField(AuditServiceUtils.DeleteInfo.LOCALAET)), null,
                    eventType.isDest, null, null, null));
            msg.getAuditSourceIdentification().add(log().createAuditSourceIdentification());
            ParticipantObjectContainsStudy pocs = new ParticipantObjectContainsStudy();
            pocs.getStudyIDs().add(AuditMessages.createStudyIDs(deleteInfo.getField(AuditServiceUtils.DeleteInfo.STUDYUID)));
            String line;
            HashSet<SOPClass> sopC = new HashSet<>();
            while ((line = reader.readLine()) != null) {
                AuditServiceUtils.DeleteStudyInfo dsi = new AuditServiceUtils.DeleteStudyInfo(line);
                dsi.getField(AuditServiceUtils.DeleteStudyInfo.SOPCLASSUID);
                dsi.getField(AuditServiceUtils.DeleteStudyInfo.NUMINSTANCES);
                sopC.add(AuditMessages.createSOPClass(
                        dsi.getField(AuditServiceUtils.DeleteStudyInfo.SOPCLASSUID),
                        Integer.parseInt(dsi.getField(AuditServiceUtils.DeleteStudyInfo.NUMINSTANCES))));
            }
            msg.getParticipantObjectIdentification().add(AuditMessages.createParticipantObjectIdentification(
                    deleteInfo.getField(AuditServiceUtils.DeleteInfo.STUDYUID), AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID,
                    null, null, AuditMessages.ParticipantObjectTypeCode.SystemObject,
                    AuditMessages.ParticipantObjectTypeCodeRole.Report, null, null, null, null, null, sopC, null, null, pocs));
            msg.getParticipantObjectIdentification().add(AuditMessages.createParticipantObjectIdentification(
                    deleteInfo.getField(AuditServiceUtils.DeleteInfo.PATIENTID), AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                    deleteInfo.getField(AuditServiceUtils.DeleteInfo.PATIENTNAME), null, AuditMessages.ParticipantObjectTypeCode.Person,
                    AuditMessages.ParticipantObjectTypeCodeRole.Patient, null, null, null, null, null, null, null, null, null));
        } catch (Exception e) {
            LOG.warn("Failed to read Audit Spool File - {} ", path, e);
            return;
        }
        emitAuditMessage(timestamp, msg);
        AuditServiceUtils.deleteFile(path);
    }

    public void collateConnectionRejected(Socket s, Throwable e) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        boolean auditAggregate = arcDev.isAuditAggregate();
        Path dir = Paths.get(
                auditAggregate ? StringUtils.replaceSystemProperties(arcDev.getAuditSpoolDirectory()) : tmpdir);
        try {
            Path file = Files.createTempFile(dir, String.valueOf(AuditServiceUtils.EventType.CONN__RJCT), null);
            boolean append = Files.exists(file);
            if (!append)
                Files.createDirectories(dir);
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.APPEND)) {
                writer.write(new AuditServiceUtils.ConnectionRejectedInfo(s, e).toString());
                writer.newLine();
            }
            if (!auditAggregate)
                aggregateAuditMessage(file);

        } catch (Exception ex) {
            LOG.warn("Failed to write to Audit Spool File - {} ", ex);
        }
    }

    private void auditConnectionRejected(Path file, AuditServiceUtils.EventType eventType) {
        Calendar timestamp = log().timeStamp();
        AuditMessage msg = new AuditMessage();
        AuditServiceUtils.ConnectionRejectedInfo crInfo;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            crInfo = new AuditServiceUtils.ConnectionRejectedInfo(reader.readLine());
            msg.setEventIdentification(AuditMessages.createEventIdentification(
                    eventType.eventID, eventType.eventActionCode, timestamp,
                    eventType.outcomeIndicator, crInfo.getField(
                            AuditServiceUtils.ConnectionRejectedInfo.OUTCOME_DESC), eventType.eventTypeCode));
            msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(
                    crInfo.getField(AuditServiceUtils.ConnectionRejectedInfo.LOCAL_ADDR), null, null, false,
                    crInfo.getField(AuditServiceUtils.ConnectionRejectedInfo.LOCAL_ADDR),
                    AuditMessages.NetworkAccessPointTypeCode.IPAddress, null));
            msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(
                    crInfo.getField(AuditServiceUtils.ConnectionRejectedInfo.REMOTE_ADDR), null, null, true,
                    crInfo.getField(AuditServiceUtils.ConnectionRejectedInfo.REMOTE_ADDR),
                    AuditMessages.NetworkAccessPointTypeCode.IPAddress, null));
            msg.getAuditSourceIdentification().add(log().createAuditSourceIdentification());
            List<String> desc = new ArrayList<>();
            desc.add(crInfo.getField(AuditServiceUtils.ConnectionRejectedInfo.PO_DESC));
            msg.getParticipantObjectIdentification().add(AuditMessages.createParticipantObjectIdentification(
                    crInfo.getField(AuditServiceUtils.ConnectionRejectedInfo.REMOTE_ADDR), AuditMessages.ParticipantObjectIDTypeCode.NodeID,
                    null, null, AuditMessages.ParticipantObjectTypeCode.SystemObject, null, null, null, desc, null, null,
                    null, null, null, null));
        } catch (Exception e) {
            LOG.warn("Failed to read Audit Spool File - {} ", file, e);
            return;
        }
        emitAuditMessage(timestamp, msg);
        AuditServiceUtils.deleteFile(file);
    }

    public void collateQuery(QueryContext ctx) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        boolean auditAggregate = arcDev.isAuditAggregate();
        Path dir = Paths.get(
                auditAggregate ? StringUtils.replaceSystemProperties(arcDev.getAuditSpoolDirectory()) : tmpdir);
        AuditServiceUtils.EventType eventType = AuditServiceUtils.EventType.forQuery(ctx);
        try {
            Path file = Files.createTempFile(dir, String.valueOf(eventType), null);
            boolean append = Files.exists(file);
            if (!append)
                Files.createDirectories(dir);
            try (BufferedOutputStream out = new BufferedOutputStream(
                    Files.newOutputStream(file, StandardOpenOption.APPEND))) {
                    new DataOutputStream(out).writeUTF(new AuditServiceUtils.QueryInfo(ctx).toString());
                if (ctx.getAssociation() != null) {
                    try (DicomOutputStream dos = new DicomOutputStream(out, UID.ImplicitVRLittleEndian)) {
                        dos.writeDataset(null, ctx.getQueryKeys());
                    } catch (Exception e) {
                        LOG.warn("Failed to create DicomOutputStream : ", e);
                    }
                }
            }
            if (!auditAggregate)
                aggregateAuditMessage(file);
        } catch (Exception e) {
            LOG.warn("Failed to write to Audit Spool File - {} ", e);
        }
    }

    private void auditQuery(Path file, AuditServiceUtils.EventType eventType) {
        Calendar timestamp = log().timeStamp();
        AuditMessage msg = new AuditMessage();
        AuditServiceUtils.QueryInfo qrInfo;
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            qrInfo = new AuditServiceUtils.QueryInfo(new DataInputStream(in).readUTF());
            msg.setEventIdentification(AuditMessages.createEventIdentification(
                    eventType.eventID, eventType.eventActionCode, timestamp,
                    eventType.outcomeIndicator, null));
            msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(qrInfo.getField(AuditServiceUtils.QueryInfo.REMOTE_HOST),
                    AuditMessages.alternativeUserIDForAETitle(qrInfo.getField(AuditServiceUtils.QueryInfo.CALLING_AET)), null,
                    eventType.isSource, qrInfo.getField(AuditServiceUtils.QueryInfo.REMOTE_HOST), AuditMessages.NetworkAccessPointTypeCode.IPAddress,
                    null, eventType.source));
            msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(qrInfo.getField(AuditServiceUtils.QueryInfo.LOCAL_HOST),
                    AuditMessages.alternativeUserIDForAETitle(qrInfo.getField(AuditServiceUtils.QueryInfo.CALLED_AET)), null,
                    eventType.isDest, qrInfo.getField(AuditServiceUtils.QueryInfo.LOCAL_HOST),
                    AuditMessages.NetworkAccessPointTypeCode.IPAddress, null, eventType.destination));
            msg.getAuditSourceIdentification().add(log().createAuditSourceIdentification());
            if (!qrInfo.getField(AuditServiceUtils.QueryInfo.PATIENT_ID).equals(noValue))
                msg.getParticipantObjectIdentification().add(AuditMessages.createParticipantObjectIdentification(
                        qrInfo.getField(AuditServiceUtils.QueryInfo.PATIENT_ID), AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                        null, null, AuditMessages.ParticipantObjectTypeCode.Person, AuditMessages.ParticipantObjectTypeCodeRole.Patient,
                        null, null, null, null, null, null, null, null, null));
            if (String.valueOf(eventType).equals(String.valueOf(AuditServiceUtils.EventType.QUERY_QIDO)))
                msg.getParticipantObjectIdentification().add(AuditMessages.createParticipantObjectIdentification(
                        null, AuditMessages.ParticipantObjectIDTypeCode.SOPClassUID, null,
                        qrInfo.getField(AuditServiceUtils.QueryInfo.QUERY_STRING).getBytes(),
                        AuditMessages.ParticipantObjectTypeCode.SystemObject, AuditMessages.ParticipantObjectTypeCodeRole.Query,
                        null, null, null, null, null, null, null, null, null,
                        AuditMessages.createParticipantObjectDetail(
                                "QueryEncoding", String.valueOf(StandardCharsets.UTF_8).getBytes())));
            else {
                byte[] buffer = new byte[(int) Files.size(file)];
                int len = in.read(buffer);
                byte[] data = new byte[len];
                System.arraycopy(buffer, 0, data, 0, len);
                msg.getParticipantObjectIdentification().add(AuditMessages.createParticipantObjectIdentification(
                        qrInfo.getField(AuditServiceUtils.QueryInfo.SOPCLASSUID), AuditMessages.ParticipantObjectIDTypeCode.SOPClassUID, null,
                        data, AuditMessages.ParticipantObjectTypeCode.SystemObject,
                        AuditMessages.ParticipantObjectTypeCodeRole.Query,
                        null, null, null, null, null, null, null, null, null,
                        AuditMessages.createParticipantObjectDetail(
                                "TransferSyntax", UID.ImplicitVRLittleEndian.getBytes())));
            }
        } catch (Exception e) {
            LOG.warn("Failed to read Audit Spool File - {} ", file, e);
            return;
        }
        emitAuditMessage(timestamp, msg);
        AuditServiceUtils.deleteFile(file);
    }

    public void collateInstanceStored(StoreContext ctx) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        boolean auditAggregate = arcDev.isAuditAggregate();
        Path dir = Paths.get(
                auditAggregate ? StringUtils.replaceSystemProperties(arcDev.getAuditSpoolDirectory()) : tmpdir);
        AuditServiceUtils.EventType eventType = AuditServiceUtils.EventType.forInstanceStored(ctx);
        if (eventType == null)
            return; // no audit message for duplicate received instance
        StoreSession session = ctx.getStoreSession();
        Attributes attrs = ctx.getAttributes();
        Path file = dir.resolve(
                eventType + session.getCallingAET() + '-' + session.getCalledAET() + '-' + ctx.getStudyInstanceUID());
        boolean append = Files.exists(file);
        try {
            if (!append)
                Files.createDirectories(dir);
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    append ? StandardOpenOption.APPEND : StandardOpenOption.CREATE_NEW)) {
                if (!append) {
                    writer.write(new AuditServiceUtils.PatientStudyInfo(ctx, attrs).toString());
                    writer.newLine();
                }
                writer.write(new AuditServiceUtils.InstanceInfo(ctx, attrs).toString());
                writer.newLine();
            }
            if (!auditAggregate)
                aggregateAuditMessage(file);
        } catch (Exception e) {
            LOG.warn("Failed to write to Audit Spool File - {} ", file, e);
        }
    }

    public void collateWADORetrieve(RetrieveContext ctx){
        AuditServiceUtils.EventType aggregationType = AuditServiceUtils.EventType.forWADORetrieve(ctx);
        HttpServletRequest req = ctx.getHttpRequest();
        Collection<InstanceLocations> il = ctx.getMatches();
        Attributes attrs = new Attributes();
        for (InstanceLocations i : il) {
            attrs = i.getAttributes();
        }
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        boolean auditAggregate = arcDev.isAuditAggregate();
        Path dir = Paths.get(
                auditAggregate ? StringUtils.replaceSystemProperties(arcDev.getAuditSpoolDirectory()) : tmpdir);
        Path file = dir.resolve(
                aggregationType + req.getRemoteAddr() + '-' + ctx.getLocalAETitle() + '-' + ctx.getStudyInstanceUIDs()[0]);
        boolean append = Files.exists(file);
        try {
            if (!append)
                Files.createDirectories(dir);
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    append ? StandardOpenOption.APPEND : StandardOpenOption.CREATE_NEW)) {
                if (!append) {
                    writer.write(new AuditServiceUtils.PatientStudyInfo(ctx, attrs).toString());
                    writer.newLine();
                }
                writer.write(new AuditServiceUtils.InstanceInfo(ctx, attrs).toString());
                writer.newLine();
            }
            if (!auditAggregate)
                aggregateAuditMessage(file);
        } catch (Exception ioe) {
            LOG.warn("Failed write to Audit Spool File - {} ", file, ioe);
        }
    }

    private void aggregateStoreOrWADORetrieve(Path path, AuditServiceUtils.EventType eventType) {
        String outcome;
        AuditServiceUtils.PatientStudyInfo patientStudyInfo;
        HashSet<String> accNos = new HashSet<>();
        HashSet<String> mppsUIDs = new HashSet<>();
        HashMap<String, List<String>> sopClassMap = new HashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            patientStudyInfo = new AuditServiceUtils.PatientStudyInfo(reader.readLine());
            outcome = patientStudyInfo.getField(AuditServiceUtils.PatientStudyInfo.OUTCOME);
            accNos.add(patientStudyInfo.getField(AuditServiceUtils.PatientStudyInfo.ACCESSION_NO));
            String line;
            while ((line = reader.readLine()) != null) {
                AuditServiceUtils.InstanceInfo instanceInfo = new AuditServiceUtils.InstanceInfo(line);
                List<String> iuids = sopClassMap.get(instanceInfo.getField(AuditServiceUtils.InstanceInfo.CLASS_UID));
                if (iuids == null) {
                    iuids = new ArrayList<>();
                    sopClassMap.put(instanceInfo.getField(AuditServiceUtils.InstanceInfo.CLASS_UID), iuids);
                }
                iuids.add(instanceInfo.getField(AuditServiceUtils.InstanceInfo.INSTANCE_UID));
                mppsUIDs.add(instanceInfo.getField(AuditServiceUtils.InstanceInfo.MPPS_UID));
                for (int i = AuditServiceUtils.InstanceInfo.ACCESSION_NO; instanceInfo.getField(i) != null; i++)
                    accNos.add(instanceInfo.getField(i));
            }
        } catch (Exception e) {
            LOG.warn("Failed to read Audit Spool File - {} ", path, e);
            return;
        }
        accNos.remove("");
        mppsUIDs.remove("");
        Calendar eventTime = log().timeStamp();
        try {
            eventTime.setTimeInMillis(Files.getLastModifiedTime(path).toMillis());
        } catch (Exception e) {
            LOG.warn("Failed to get Last Modified Time of Audit Spool File - {} ", path, e);
        }
        auditInstancesStoredOrWADORetrieve(patientStudyInfo, accNos, mppsUIDs, sopClassMap, eventTime, eventType, outcome);
        AuditServiceUtils.deleteFile(path);
    }

    private void auditInstancesStoredOrWADORetrieve(AuditServiceUtils.PatientStudyInfo patientStudyInfo, HashSet<String> accNos,
                                                   HashSet<String> mppsUIDs, HashMap<String, List<String>> sopClassMap,
                                                   Calendar eventTime, AuditServiceUtils.EventType eventType, String outcomeDesc) {
        Calendar timestamp = log().timeStamp();
        AuditMessage msg = new AuditMessage();
        msg.setEventIdentification(AuditMessages.createEventIdentification(
                AuditMessages.EventID.DICOMInstancesTransferred, eventType.eventActionCode, eventTime,
                eventType.outcomeIndicator, outcomeDesc));
        msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(
                patientStudyInfo.getField(AuditServiceUtils.PatientStudyInfo.REMOTE_HOSTNAME),
                AuditMessages.alternativeUserIDForAETitle(patientStudyInfo.getField(AuditServiceUtils.PatientStudyInfo.CALLING_AET)),
                null, true, patientStudyInfo.getField(AuditServiceUtils.PatientStudyInfo.REMOTE_HOSTNAME),
                AuditMessages.NetworkAccessPointTypeCode.IPAddress, null, eventType.source));
        msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(
                patientStudyInfo.getField(AuditServiceUtils.PatientStudyInfo.LOCAL_HOSTNAME),
                AuditMessages.alternativeUserIDForAETitle(patientStudyInfo.getField(AuditServiceUtils.PatientStudyInfo.CALLED_AET)),
                null, false, device.getDeviceName(), AuditMessages.NetworkAccessPointTypeCode.IPAddress, null,
                eventType.destination));
        msg.getAuditSourceIdentification().add(log().createAuditSourceIdentification());
        HashSet<Accession> acc = new HashSet<>();
        HashSet<MPPS> mpps = new HashSet<>();
        HashSet<SOPClass> sopC = new HashSet<>();
        for (String accNo : accNos)
            acc.add(AuditMessages.createAccession(accNo));
        for (String mppsUID : mppsUIDs)
            mpps.add(AuditMessages.createMPPS(mppsUID));
        for (Map.Entry<String, List<String>> entry : sopClassMap.entrySet())
            sopC.add(AuditMessages.createSOPClass(entry.getKey(), entry.getValue().size()));
        ParticipantObjectContainsStudy pocs = new ParticipantObjectContainsStudy();
        pocs.getStudyIDs().add(AuditMessages.createStudyIDs(patientStudyInfo.getField(AuditServiceUtils.PatientStudyInfo.STUDY_UID)));
        msg.getParticipantObjectIdentification().add(AuditMessages.createParticipantObjectIdentification(
                patientStudyInfo.getField(AuditServiceUtils.PatientStudyInfo.STUDY_UID),
                AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID, null, null,
                AuditMessages.ParticipantObjectTypeCode.SystemObject, AuditMessages.ParticipantObjectTypeCodeRole.Report,
                null, null, null, acc, mpps, sopC, null, null, pocs));
        msg.getParticipantObjectIdentification().add(AuditMessages.createParticipantObjectIdentification(
                StringUtils.maskEmpty(patientStudyInfo.getField(AuditServiceUtils.PatientStudyInfo.PATIENT_ID), noValue),
                AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                StringUtils.maskEmpty(patientStudyInfo.getField(AuditServiceUtils.PatientStudyInfo.PATIENT_NAME), null), null,
                AuditMessages.ParticipantObjectTypeCode.Person, AuditMessages.ParticipantObjectTypeCodeRole.Patient,
                null, null, null, null, null, null, null, null, null));
        emitAuditMessage(timestamp, msg);
    }

    public void collateRetrieve(RetrieveContext ctx, AuditServiceUtils.EventType et) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        boolean auditAggregate = arcDev.isAuditAggregate();
        Path dir = Paths.get(
                auditAggregate ? StringUtils.replaceSystemProperties(arcDev.getAuditSpoolDirectory()) : tmpdir);
        try {
            Path file = Files.createTempFile(dir, String.valueOf(et), null);
            boolean append = Files.exists(file);
            if (!append)
                Files.createDirectories(dir);
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.APPEND)) {
                writer.write(new AuditServiceUtils.RetrieveInfo(ctx).toString());
                writer.newLine();
                for (InstanceLocations il : ctx.getMatches()) {
                    Attributes attrs = il.getAttributes();
                    writer.write(new AuditServiceUtils.RetrieveStudyInfo(attrs).toString());
                    writer.newLine();
                }
            }
            if (!auditAggregate)
                aggregateAuditMessage(file);
        } catch (Exception e) {
            LOG.warn("Failed write to Audit Spool File - {} ", e);
        }
    }

    private void auditRetrieve(Path file, AuditServiceUtils.EventType eventType) {
        Calendar eventTime = log().timeStamp();
        try {
            eventTime.setTimeInMillis(Files.getLastModifiedTime(file).toMillis());
        } catch (Exception e) {
            LOG.warn("Failed to get Last Modified Time of Audit Spool File - {} ", file, e);
        }
        AuditMessage msg = new AuditMessage();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            AuditServiceUtils.RetrieveInfo ri = new AuditServiceUtils.RetrieveInfo(reader.readLine());
            msg.setEventIdentification(AuditMessages.createEventIdentification(eventType.eventID, eventType.eventActionCode,
                    eventTime, eventType.outcomeIndicator, ri.getField(AuditServiceUtils.RetrieveInfo.OUTCOME)));
            msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(
                    ri.getField(AuditServiceUtils.RetrieveInfo.LOCALHOST),
                    AuditMessages.alternativeUserIDForAETitle(ri.getField(AuditServiceUtils.RetrieveInfo.LOCALAET)),
                    null, eventType.isSource, null, null, null, eventType.source));
            msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(ri.getField(AuditServiceUtils.RetrieveInfo.DESTHOST),
                    AuditMessages.alternativeUserIDForAETitle(ri.getField(AuditServiceUtils.RetrieveInfo.DESTAET)), null, eventType.isDest,
                    ri.getField(AuditServiceUtils.RetrieveInfo.DESTNAPID), ri.getField(AuditServiceUtils.RetrieveInfo.DESTNAPCODE), null, eventType.destination));
            if (eventType.isOther)
                msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(ri.getField(AuditServiceUtils.RetrieveInfo.REQUESTORHOST),
                        AuditMessages.alternativeUserIDForAETitle(ri.getField(AuditServiceUtils.RetrieveInfo.MOVEAET)), null, eventType.isOther,
                        ri.getField(AuditServiceUtils.RetrieveInfo.REQUESTORHOST), AuditMessages.NetworkAccessPointTypeCode.IPAddress, null));
            msg.getAuditSourceIdentification().add(log().createAuditSourceIdentification());
            String line;
            HashMap<String, AuditServiceUtils.AccessionNumSopClassInfo> study_accNumSOPClassInfo = new HashMap<>();
            String pID = noValue;
            String pName = null;
            while ((line = reader.readLine()) != null) {
                AuditServiceUtils.RetrieveStudyInfo rInfo = new AuditServiceUtils.RetrieveStudyInfo(line);
                String studyInstanceUID = rInfo.getField(AuditServiceUtils.RetrieveStudyInfo.STUDYUID);
                AuditServiceUtils.AccessionNumSopClassInfo accessionNumSopClassInfo = study_accNumSOPClassInfo.get(studyInstanceUID);
                if (accessionNumSopClassInfo == null) {
                    accessionNumSopClassInfo = new AuditServiceUtils.AccessionNumSopClassInfo(rInfo.getField(AuditServiceUtils.RetrieveStudyInfo.ACCESSION));
                    study_accNumSOPClassInfo.put(studyInstanceUID, accessionNumSopClassInfo);
                }
                accessionNumSopClassInfo.addSOPInstance(rInfo);
                study_accNumSOPClassInfo.put(studyInstanceUID, accessionNumSopClassInfo);
                pID = rInfo.getField(AuditServiceUtils.RetrieveStudyInfo.PATIENTID);
                pName = rInfo.getField(AuditServiceUtils.RetrieveStudyInfo.PATIENTNAME);
            }
            HashSet<Accession> acc = new HashSet<>();
            HashSet<SOPClass> sopC = new HashSet<>();
            for (Map.Entry<String, AuditServiceUtils.AccessionNumSopClassInfo> entry : study_accNumSOPClassInfo.entrySet()) {
                if (null != entry.getValue().getAccNum())
                    acc.add(AuditMessages.createAccession(entry.getValue().getAccNum()));
                for (Map.Entry<String, HashSet<String>> sopClassMap : entry.getValue().getSopClassMap().entrySet())
                    sopC.add(AuditMessages.createSOPClass(sopClassMap.getKey(), sopClassMap.getValue().size()));
                ParticipantObjectContainsStudy pocs = new ParticipantObjectContainsStudy();
                pocs.getStudyIDs().add(AuditMessages.createStudyIDs(entry.getKey()));
                msg.getParticipantObjectIdentification().add(AuditMessages.createParticipantObjectIdentification(
                        entry.getKey(), AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID, null, null,
                        AuditMessages.ParticipantObjectTypeCode.SystemObject,
                        AuditMessages.ParticipantObjectTypeCodeRole.Report, null, null, null, acc, null, sopC, null, null, pocs));
            }
            msg.getParticipantObjectIdentification().add(AuditMessages.createParticipantObjectIdentification(
                    pID, AuditMessages.ParticipantObjectIDTypeCode.PatientNumber, pName, null,
                    AuditMessages.ParticipantObjectTypeCode.Person,
                    AuditMessages.ParticipantObjectTypeCodeRole.Patient, null, null, null, null, null, null, null, null, null));
        } catch (Exception e) {
            LOG.warn("Failed to read Audit Spool File - {} ", file, e);
            return;
        }
        emitAuditMessage(log().timeStamp(), msg);
        AuditServiceUtils.deleteFile(file);
    }
}
