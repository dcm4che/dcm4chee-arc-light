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
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.delete.StudyDeleteContext;
import org.dcm4chee.arc.patient.PatientMgtContext;
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
    private static final String TMPDIR = doPrivileged(new GetPropertyAction("java.io.tmpdir"));
    private static final String NO_VALUE = "<none>";

    @Inject
    private Device device;

    private AuditLogger log() {
        return device.getDeviceExtension(AuditLogger.class);
    }

    void aggregateAuditMessage(Path path) throws IOException {
        AuditServiceUtils.EventType eventType = AuditServiceUtils.EventType.fromFile(path);
        switch (eventType.eventClass) {
            case CONN_REJECT:
                auditConnectionRejected(path, eventType);
                break;
            case STORE_WADOR:
                aggregateStoreOrWADORetrieve(path, eventType);
                break;
            case RETRIEVE:
                auditRetrieve(path, eventType);
                break;
            case DELETE:
                auditInstanceDeletion(path, eventType);
                break;
            case PERM_DELETE:
                auditPermanentDeletion(path, eventType);
                break;
            case QUERY:
                auditQuery(path, eventType);
                break;
            case HL7:
                auditPatientRecord(path, eventType);
                break;
        }
    }

    void auditApplicationActivity(AuditServiceUtils.EventType eventType, HttpServletRequest req) {
        List<ActiveParticipant> apList = new ArrayList<>();
        List<ParticipantObjectIdentification> poiList = new ArrayList<>();
        apList.add(AuditMessages.createActiveParticipant(AuditServiceUtils.buildAET(device), AuditLogger.processID(), null, false,
                AuditServiceUtils.getLocalHostName(log()), AuditMessages.NetworkAccessPointTypeCode.IPAddress, null,
                AuditMessages.RoleIDCode.Application));
        if (req != null) {
            apList.add(AuditMessages.createActiveParticipant(
                    req.getRemoteUser() != null ? req.getRemoteUser() : req.getRemoteAddr(), null, null, true,
                    req.getRemoteAddr(), AuditMessages.NetworkAccessPointTypeCode.IPAddress, null,
                    AuditMessages.RoleIDCode.ApplicationLauncher));
        }
        AuditServiceUtils.emitAuditMessage(eventType, null, apList, poiList, log());
    }

    void spoolInstancesDeleted(StoreContext ctx) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        boolean auditAggregate = arcDev.isAuditAggregate();
        Path dir = Paths.get(
                auditAggregate ? StringUtils.replaceSystemProperties(arcDev.getAuditSpoolDirectory()) : TMPDIR);
        boolean append = Files.exists(dir);
        AuditServiceUtils.EventType et = (ctx.getException() != null)
                ? AuditServiceUtils.EventType.DELETE_ERR : AuditServiceUtils.EventType.DELETE_PAS;
        Attributes attrs = ctx.getAttributes();
        try {
            if (!append)
                Files.createDirectories(dir);
            Path file = Files.createTempFile(dir, String.valueOf(et), null);
            try (LineWriter writer = new LineWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.APPEND))) {
                writer.writeLine(new PatientStudyInfo(ctx));
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
                    writer.writeLine(new InstanceInfo(entry.getKey(), String.valueOf(entry.getValue().size())));
                }
            }
            if (!auditAggregate)
                aggregateAuditMessage(file);
        } catch (Exception e) {
            LOG.warn("Failed to write to Audit Spool File - {} ", e);
        }
    }

    private void auditInstanceDeletion(Path path, AuditServiceUtils.EventType eventType) throws IOException {
        List<ActiveParticipant> apList = new ArrayList<>();
        List<ParticipantObjectIdentification> poiList = new ArrayList<>();
        PatientStudyInfo deleteInfo;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            deleteInfo = new PatientStudyInfo(reader.readLine());
            apList.add(AuditMessages.createActiveParticipant(
                    deleteInfo.getField(PatientStudyInfo.CALLING_AET), null, null, eventType.isSource,
                    deleteInfo.getField(PatientStudyInfo.CALLING_HOSTNAME),
                    AuditMessages.NetworkAccessPointTypeCode.IPAddress, null));
            apList.add(AuditMessages.createActiveParticipant(
                    deleteInfo.getField(PatientStudyInfo.CALLED_AET),
                    AuditLogger.processID(), null, eventType.isDest, AuditServiceUtils.getLocalHostName(log()),
                    AuditMessages.NetworkAccessPointTypeCode.IPAddress, null));
            ParticipantObjectContainsStudy pocs = new ParticipantObjectContainsStudy();
            pocs.getStudyIDs().add(AuditMessages.createStudyIDs(deleteInfo.getField(PatientStudyInfo.STUDY_UID)));
            String line;
            HashSet<SOPClass> sopC = new HashSet<>();
            while ((line = reader.readLine()) != null) {
                InstanceInfo dsi = new InstanceInfo(line);
                sopC.add(AuditMessages.createSOPClass(null,
                        dsi.getField(InstanceInfo.CLASS_UID),
                        Integer.parseInt(dsi.getField(InstanceInfo.INSTANCE_UID))));
            }
            if (deleteInfo.getField(PatientStudyInfo.STUDY_DATE) != null)
                poiList.add(AuditMessages.createParticipantObjectIdentification(
                        deleteInfo.getField(PatientStudyInfo.STUDY_UID),
                        AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID,
                        null, null, AuditMessages.ParticipantObjectTypeCode.SystemObject,
                        AuditMessages.ParticipantObjectTypeCodeRole.Report, null, null, null, null, null, sopC, null,
                        null, pocs, AuditMessages.createParticipantObjectDetail("StudyDate", deleteInfo.getField(
                                PatientStudyInfo.STUDY_DATE).getBytes())));
            else
                poiList.add(AuditMessages.createParticipantObjectIdentification(
                        deleteInfo.getField(PatientStudyInfo.STUDY_UID), AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID,
                        null, null, AuditMessages.ParticipantObjectTypeCode.SystemObject,
                        AuditMessages.ParticipantObjectTypeCodeRole.Report, null, null, null, null, null, sopC, null, null, pocs));
            poiList.add(AuditMessages.createParticipantObjectIdentification(
                    deleteInfo.getField(PatientStudyInfo.PATIENT_ID), AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                    deleteInfo.getField(PatientStudyInfo.PATIENT_NAME), null, AuditMessages.ParticipantObjectTypeCode.Person,
                    AuditMessages.ParticipantObjectTypeCodeRole.Patient, null, null, null, null, null, null, null, null, null));
        }
        AuditServiceUtils.emitAuditMessage(eventType, deleteInfo.getField(PatientStudyInfo.OUTCOME),
                apList, poiList, log());
    }

    void spoolStudyDeleted(StudyDeleteContext ctx) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        boolean auditAggregate = arcDev.isAuditAggregate();
        Path dir = Paths.get(
                auditAggregate ? StringUtils.replaceSystemProperties(arcDev.getAuditSpoolDirectory()) : TMPDIR);
        boolean append = Files.exists(dir);
        AuditServiceUtils.EventType eventType = (ctx.getException() != null)
                ? AuditServiceUtils.EventType.PERM_DEL_E : AuditServiceUtils.EventType.PERM_DEL_S;
        try {
            if (!append)
                Files.createDirectories(dir);
            Path file = Files.createTempFile(dir, String.valueOf(eventType), null);
            try (LineWriter writer = new LineWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.APPEND))) {
                writer.writeLine(new PatientStudyInfo(ctx));
                HashMap<String, HashSet<String>> sopClassMap = new HashMap<>();
                for (org.dcm4chee.arc.entity.Instance i : ctx.getInstances()) {
                    String cuid = i.getSopClassUID();
                    HashSet<String> iuids = sopClassMap.get(cuid);
                    if (iuids == null) {
                        iuids = new HashSet<>();
                        sopClassMap.put(cuid, iuids);
                    }
                    iuids.add(i.getSopInstanceUID());
                }
                for (Map.Entry<String, HashSet<String>> entry : sopClassMap.entrySet()) {
                    writer.writeLine(new InstanceInfo(entry.getKey(),
                            String.valueOf(entry.getValue().size())));
                }
            }
            if (!auditAggregate)
                aggregateAuditMessage(file);
        } catch (Exception e) {
            LOG.warn("Failed to write to Audit Spool File - {} ", e);
        }
    }

    private void auditPermanentDeletion(Path file, AuditServiceUtils.EventType eventType) throws IOException {
        List<ActiveParticipant> apList = new ArrayList<>();
        List<ParticipantObjectIdentification> poiList = new ArrayList<>();
        PatientStudyInfo pdi;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            pdi = new PatientStudyInfo(reader.readLine());
            apList.add(AuditMessages.createActiveParticipant(AuditServiceUtils.buildAET(device), AuditLogger.processID(), null, true,
                    AuditServiceUtils.getLocalHostName(log()), AuditMessages.NetworkAccessPointTypeCode.IPAddress, null));
            ParticipantObjectContainsStudy pocs = new ParticipantObjectContainsStudy();
                    pocs.getStudyIDs().add(AuditMessages.createStudyIDs(pdi.getField(PatientStudyInfo.STUDY_UID)));
            HashSet<Accession> acc = new HashSet<>();
            acc.add(AuditMessages.createAccession(pdi.getField(PatientStudyInfo.ACCESSION_NO)));
            String line;
            HashSet<SOPClass> sopC = new HashSet<>();
            while ((line = reader.readLine()) != null) {
                InstanceInfo dsi = new InstanceInfo(line);
                sopC.add(AuditMessages.createSOPClass(null,
                        dsi.getField(InstanceInfo.CLASS_UID),
                        Integer.parseInt(dsi.getField(InstanceInfo.INSTANCE_UID))));
            }
            if (pdi.getField(PatientStudyInfo.STUDY_DATE) != null)
                poiList.add(AuditMessages.createParticipantObjectIdentification(
                    pdi.getField(PatientStudyInfo.STUDY_UID),
                    AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID, null, null,
                    AuditMessages.ParticipantObjectTypeCode.SystemObject,
                    AuditMessages.ParticipantObjectTypeCodeRole.Report, null, null, null, acc, null, sopC, null,
                    null, pocs, AuditMessages.createParticipantObjectDetail(
                            "StudyDate", pdi.getField(PatientStudyInfo.STUDY_DATE).getBytes())));
            else
                poiList.add(AuditMessages.createParticipantObjectIdentification(
                    pdi.getField(PatientStudyInfo.STUDY_UID),
                    AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID, null, null,
                    AuditMessages.ParticipantObjectTypeCode.SystemObject, AuditMessages.ParticipantObjectTypeCodeRole.Report,
                    null, null, null, acc, null, sopC, null, null, pocs));
            poiList.add(AuditMessages.createParticipantObjectIdentification(
                    pdi.getField(PatientStudyInfo.PATIENT_ID),
                    AuditMessages.ParticipantObjectIDTypeCode.PatientNumber, pdi.getField(PatientStudyInfo.PATIENT_NAME),
                    null, AuditMessages.ParticipantObjectTypeCode.Person, AuditMessages.ParticipantObjectTypeCodeRole.Patient,
                    null, null, null, null, null, null, null, null, null));
        }
        AuditServiceUtils.emitAuditMessage(eventType, pdi.getField(PatientStudyInfo.OUTCOME),
                apList, poiList, log());
    }

    void spoolConnectionRejected(Connection conn, Socket s, Throwable e) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        boolean auditAggregate = arcDev.isAuditAggregate();
        Path dir = Paths.get(
                auditAggregate ? StringUtils.replaceSystemProperties(arcDev.getAuditSpoolDirectory()) : TMPDIR);
        boolean append = Files.exists(dir);
        try {
            if (!append)
                Files.createDirectories(dir);
            Path file = Files.createTempFile(dir, String.valueOf(AuditServiceUtils.EventType.CONN__RJCT), null);
            try (LineWriter writer = new LineWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.APPEND))) {
                writer.writeLine(new ConnectionRejectedInfo(conn, s, e));
            }
            if (!auditAggregate)
                aggregateAuditMessage(file);

        } catch (Exception ex) {
            LOG.warn("Failed to write to Audit Spool File - {} ", ex);
        }
    }

    private void auditConnectionRejected(Path file, AuditServiceUtils.EventType eventType) throws IOException {
        List<ActiveParticipant> apList = new ArrayList<>();
        List<ParticipantObjectIdentification> poiList = new ArrayList<>();
        ConnectionRejectedInfo crInfo;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            crInfo = new ConnectionRejectedInfo(reader.readLine());
            apList.add(AuditMessages.createActiveParticipant(
                    AuditServiceUtils.buildAET(device), AuditLogger.processID(), null, false,
                    crInfo.getField(ConnectionRejectedInfo.LOCAL_ADDR),
                    AuditMessages.NetworkAccessPointTypeCode.IPAddress, null));
            apList.add(AuditMessages.createActiveParticipant(
                    crInfo.getField(ConnectionRejectedInfo.REMOTE_ADDR), null, null, true,
                    crInfo.getField(ConnectionRejectedInfo.REMOTE_ADDR),
                    AuditMessages.NetworkAccessPointTypeCode.IPAddress, null));
            poiList.add(AuditMessages.createParticipantObjectIdentification(
                    crInfo.getField(ConnectionRejectedInfo.REMOTE_ADDR), AuditMessages.ParticipantObjectIDTypeCode.NodeID,
                    null, null, AuditMessages.ParticipantObjectTypeCode.SystemObject, null, null, null, null, null, null,
                    null, null, null, null));
        }
        AuditServiceUtils.emitAuditMessage(eventType,
                crInfo.getField(ConnectionRejectedInfo.OUTCOME_DESC), apList, poiList, log());
    }

    void spoolQuery(QueryContext ctx) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        boolean auditAggregate = arcDev.isAuditAggregate();
        Path dir = Paths.get(
                auditAggregate ? StringUtils.replaceSystemProperties(arcDev.getAuditSpoolDirectory()) : TMPDIR);
        boolean append = Files.exists(dir);
        AuditServiceUtils.EventType eventType = AuditServiceUtils.EventType.forQuery(ctx);
        try {
            if (!append)
                Files.createDirectories(dir);
            Path file = Files.createTempFile(dir, String.valueOf(eventType), null);
            try (BufferedOutputStream out = new BufferedOutputStream(
                    Files.newOutputStream(file, StandardOpenOption.APPEND))) {
                    new DataOutputStream(out).writeUTF(new QueryInfo(ctx).toString());
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

    private void auditQuery(Path file, AuditServiceUtils.EventType eventType) throws IOException {
        List<ActiveParticipant> apList = new ArrayList<>();
        List<ParticipantObjectIdentification> poiList = new ArrayList<>();
        QueryInfo qrInfo;
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            qrInfo = new QueryInfo(new DataInputStream(in).readUTF());
            apList.add(AuditMessages.createActiveParticipant(qrInfo.getField(QueryInfo.CALLING_AET),
                    null, null, eventType.isSource, qrInfo.getField(QueryInfo.REMOTE_HOST),
                    AuditMessages.NetworkAccessPointTypeCode.IPAddress, null, eventType.source));
            apList.add(AuditMessages.createActiveParticipant(qrInfo.getField(QueryInfo.CALLED_AET),
                    AuditLogger.processID(), null, eventType.isDest, AuditServiceUtils.getLocalHostName(log()),
                    AuditMessages.NetworkAccessPointTypeCode.IPAddress, null, eventType.destination));
            if (!qrInfo.getField(QueryInfo.PATIENT_ID).equals(NO_VALUE))
                poiList.add(AuditMessages.createParticipantObjectIdentification(
                        qrInfo.getField(QueryInfo.PATIENT_ID), AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                        null, null, AuditMessages.ParticipantObjectTypeCode.Person, AuditMessages.ParticipantObjectTypeCodeRole.Patient,
                        null, null, null, null, null, null, null, null, null));
            if (String.valueOf(eventType).equals(String.valueOf(AuditServiceUtils.EventType.QUERY_QIDO)))
                poiList.add(AuditMessages.createParticipantObjectIdentification(
                        null, AuditMessages.ParticipantObjectIDTypeCode.SOPClassUID, null,
                        qrInfo.getField(QueryInfo.QUERY_STRING).getBytes(),
                        AuditMessages.ParticipantObjectTypeCode.SystemObject, AuditMessages.ParticipantObjectTypeCodeRole.Query,
                        null, null, null, null, null, null, null, null, null,
                        AuditMessages.createParticipantObjectDetail(
                                "QueryEncoding", String.valueOf(StandardCharsets.UTF_8).getBytes())));
            else {
                byte[] buffer = new byte[(int) Files.size(file)];
                int len = in.read(buffer);
                byte[] data = new byte[len];
                System.arraycopy(buffer, 0, data, 0, len);
                poiList.add(AuditMessages.createParticipantObjectIdentification(
                        qrInfo.getField(QueryInfo.SOPCLASSUID), AuditMessages.ParticipantObjectIDTypeCode.SOPClassUID, null,
                        data, AuditMessages.ParticipantObjectTypeCode.SystemObject,
                        AuditMessages.ParticipantObjectTypeCodeRole.Query,
                        null, null, null, null, null, null, null, null, null,
                        AuditMessages.createParticipantObjectDetail(
                                "TransferSyntax", UID.ImplicitVRLittleEndian.getBytes())));
            }
        }
        AuditServiceUtils.emitAuditMessage(eventType, null, apList, poiList, log());
    }

    void spoolInstanceStored(StoreContext ctx) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        boolean auditAggregate = arcDev.isAuditAggregate();
        Path dir = Paths.get(
                auditAggregate ? StringUtils.replaceSystemProperties(arcDev.getAuditSpoolDirectory()) : TMPDIR);
        AuditServiceUtils.EventType eventType = AuditServiceUtils.EventType.forInstanceStored(ctx);
        if (eventType == null)
            return; // no audit message for duplicate received instance
        StoreSession session = ctx.getStoreSession();
        Path file = dir.resolve(
                String.valueOf(eventType) + '-' + session.getCallingAET() + '-' + session.getCalledAET() + '-'
                        + ctx.getStudyInstanceUID());
        boolean append = Files.exists(file);
        try {
            if (!append)
                Files.createDirectories(dir);
            try (LineWriter writer = new LineWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    append ? StandardOpenOption.APPEND : StandardOpenOption.CREATE_NEW))) {
                if (!append) {
                    writer.writeLine(new PatientStudyInfo(ctx));
                }
                writer.writeLine(new InstanceInfo(ctx));
            }
            if (!auditAggregate)
                aggregateAuditMessage(file);
        } catch (Exception e) {
            LOG.warn("Failed to write to Audit Spool File - {} ", file, e);
        }
    }

    void spoolWADORetrieve(RetrieveContext ctx){
        AuditServiceUtils.EventType eventType = AuditServiceUtils.EventType.forWADORetrieve(ctx);
        HttpServletRequest req = ctx.getHttpRequest();
        Collection<InstanceLocations> il = ctx.getMatches();
        Attributes attrs = new Attributes();
        for (InstanceLocations i : il) {
            attrs = i.getAttributes();
        }
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        boolean auditAggregate = arcDev.isAuditAggregate();
        Path dir = Paths.get(
                auditAggregate ? StringUtils.replaceSystemProperties(arcDev.getAuditSpoolDirectory()) : TMPDIR);
        Path file = dir.resolve(
                String.valueOf(eventType) + '-' + req.getRemoteAddr() + '-' + ctx.getLocalAETitle() + '-'
                        + ctx.getStudyInstanceUIDs()[0]);
        boolean append = Files.exists(file);
        try {
            if (!append)
                Files.createDirectories(dir);
            try (LineWriter writer = new LineWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    append ? StandardOpenOption.APPEND : StandardOpenOption.CREATE_NEW))) {
                if (!append) {
                    writer.writeLine(new PatientStudyInfo(ctx, attrs));
                }
                writer.writeLine(new InstanceInfo(ctx, attrs));
            }
            if (!auditAggregate)
                aggregateAuditMessage(file);
        } catch (Exception ioe) {
            LOG.warn("Failed write to Audit Spool File - {} ", file, ioe);
        }
    }

    private void aggregateStoreOrWADORetrieve(Path path, AuditServiceUtils.EventType eventType) throws IOException {
        String outcome;
        PatientStudyInfo patientStudyInfo;
        HashSet<String> accNos = new HashSet<>();
        HashSet<String> mppsUIDs = new HashSet<>();
        HashMap<String, List<String>> sopClassMap = new HashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            patientStudyInfo = new PatientStudyInfo(reader.readLine());
            outcome = patientStudyInfo.getField(PatientStudyInfo.OUTCOME);
            if (null != patientStudyInfo.getField(PatientStudyInfo.ACCESSION_NO))
                accNos.add(patientStudyInfo.getField(PatientStudyInfo.ACCESSION_NO));
            String line;
            while ((line = reader.readLine()) != null) {
                InstanceInfo instanceInfo = new InstanceInfo(line);
                List<String> iuids = sopClassMap.get(instanceInfo.getField(InstanceInfo.CLASS_UID));
                if (iuids == null) {
                    iuids = new ArrayList<>();
                    sopClassMap.put(instanceInfo.getField(InstanceInfo.CLASS_UID), iuids);
                }
                iuids.add(instanceInfo.getField(InstanceInfo.INSTANCE_UID));
                mppsUIDs.add(instanceInfo.getField(InstanceInfo.MPPS_UID));
//                for (int i = InstanceInfo.ACCESSION_NO; instanceInfo.getField(i) != null; i++)
//                    accNos.add(instanceInfo.getField(i));
            }
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
    }

    private void auditInstancesStoredOrWADORetrieve(PatientStudyInfo patientStudyInfo, HashSet<String> accNos,
                                                   HashSet<String> mppsUIDs, HashMap<String, List<String>> sopClassMap,
                                                   Calendar eventTime, AuditServiceUtils.EventType eventType, String outcomeDesc) {
        List<ActiveParticipant> apList = new ArrayList<>();
        List<ParticipantObjectIdentification> poiList = new ArrayList<>();
        apList.add(AuditMessages.createActiveParticipant(
                patientStudyInfo.getField(PatientStudyInfo.CALLING_AET), null, null, eventType.isSource,
                patientStudyInfo.getField(PatientStudyInfo.CALLING_HOSTNAME),
                AuditMessages.NetworkAccessPointTypeCode.IPAddress, null, eventType.source));
        apList.add(AuditMessages.createActiveParticipant(
                patientStudyInfo.getField(PatientStudyInfo.CALLED_AET), AuditLogger.processID(),
                null, eventType.isDest, AuditServiceUtils.getLocalHostName(log()),
                AuditMessages.NetworkAccessPointTypeCode.IPAddress, null, eventType.destination));
        HashSet<Accession> acc = new HashSet<>();
        HashSet<MPPS> mpps = new HashSet<>();
        HashSet<SOPClass> sopC = new HashSet<>();
        for (String accNo : accNos)
            acc.add(AuditMessages.createAccession(accNo));
        for (String mppsUID : mppsUIDs)
            mpps.add(AuditMessages.createMPPS(mppsUID));
        for (Map.Entry<String, List<String>> entry : sopClassMap.entrySet())
            sopC.add(AuditMessages.createSOPClass(null, entry.getKey(), entry.getValue().size()));
        ParticipantObjectContainsStudy pocs = new ParticipantObjectContainsStudy();
        pocs.getStudyIDs().add(AuditMessages.createStudyIDs(patientStudyInfo.getField(PatientStudyInfo.STUDY_UID)));
        if (patientStudyInfo.getField(PatientStudyInfo.STUDY_DATE) != null)
            poiList.add(AuditMessages.createParticipantObjectIdentification(
                patientStudyInfo.getField(PatientStudyInfo.STUDY_UID),
                AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID, null, null,
                AuditMessages.ParticipantObjectTypeCode.SystemObject, AuditMessages.ParticipantObjectTypeCodeRole.Report,
                null, null, null, acc, mpps, sopC, null, null, pocs, AuditMessages.createParticipantObjectDetail(
                            "StudyDate", patientStudyInfo.getField(PatientStudyInfo.STUDY_DATE).getBytes())));
        else
            poiList.add(AuditMessages.createParticipantObjectIdentification(
                patientStudyInfo.getField(PatientStudyInfo.STUDY_UID),
                AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID, null, null,
                AuditMessages.ParticipantObjectTypeCode.SystemObject, AuditMessages.ParticipantObjectTypeCodeRole.Report,
                null, null, null, acc, mpps, sopC, null, null, pocs));
        poiList.add(AuditMessages.createParticipantObjectIdentification(
                StringUtils.maskEmpty(patientStudyInfo.getField(PatientStudyInfo.PATIENT_ID), NO_VALUE),
                AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                StringUtils.maskEmpty(patientStudyInfo.getField(PatientStudyInfo.PATIENT_NAME), null), null,
                AuditMessages.ParticipantObjectTypeCode.Person, AuditMessages.ParticipantObjectTypeCodeRole.Patient,
                null, null, null, null, null, null, null, null, null));
        AuditServiceUtils.emitAuditMessage(eventType, outcomeDesc, apList, poiList, log());
    }

    void spoolPartialRetrieve(RetrieveContext ctx, HashSet<AuditServiceUtils.EventType> et) {
        List<String> failedList = Arrays.asList(ctx.failedSOPInstanceUIDs());
        Collection<InstanceLocations> instanceLocations = ctx.getMatches();
        HashSet<InstanceLocations> failed = new HashSet<>();
        HashSet<InstanceLocations> success = new HashSet<>();
        success.addAll(instanceLocations);
        for (InstanceLocations il : instanceLocations) {
            if (failedList.contains(il.getSopInstanceUID())) {
                failed.add(il);
                success.remove(il);
            }
        }
        String etFile;
        for (AuditServiceUtils.EventType eventType : et) {
            etFile = String.valueOf(eventType);
            if (etFile.substring(9, 10).equals("E"))
                spoolRetrieve(etFile, ctx, failed);
            if (etFile.substring(9, 10).equals("P"))
                spoolRetrieve(etFile, ctx, success);
        }
    }

    void spoolRetrieve(String etFile, RetrieveContext ctx, Collection<InstanceLocations> il) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        boolean auditAggregate = arcDev.isAuditAggregate();
        Path dir = Paths.get(
                auditAggregate ? StringUtils.replaceSystemProperties(arcDev.getAuditSpoolDirectory()) : TMPDIR);
        boolean append = Files.exists(dir);
        try {
            if (!append)
                Files.createDirectories(dir);
            Path file = Files.createTempFile(dir, etFile, null);
            try (LineWriter writer = new LineWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.APPEND))) {
                writer.writeLine(new RetrieveInfo(ctx, etFile));
                for (InstanceLocations instanceLocation : il) {
                    Attributes attrs = instanceLocation.getAttributes();
                    writer.writeLine(new RetrieveStudyInfo(attrs));
                }
            }
            if (!auditAggregate)
                aggregateAuditMessage(file);
        } catch (Exception e) {
            LOG.warn("Failed write to Audit Spool File - {} ", e);
        }
    }

    private void auditRetrieve(Path file, AuditServiceUtils.EventType eventType) throws IOException {
        Calendar eventTime = log().timeStamp();
        try {
            eventTime.setTimeInMillis(Files.getLastModifiedTime(file).toMillis());
        } catch (Exception e) {
            LOG.warn("Failed to get Last Modified Time of Audit Spool File - {} ", file, e);
        }
        List<ActiveParticipant> apList = new ArrayList<>();
        List<ParticipantObjectIdentification> poiList = new ArrayList<>();
        RetrieveInfo ri;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            ri = new RetrieveInfo(reader.readLine());
            apList.add(AuditMessages.createActiveParticipant(
                    ri.getField(RetrieveInfo.LOCALAET), AuditLogger.processID(),
                    null, eventType.isSource, AuditServiceUtils.getLocalHostName(log()),
                    AuditMessages.NetworkAccessPointTypeCode.IPAddress, null, eventType.source));
            apList.add(AuditMessages.createActiveParticipant(ri.getField(RetrieveInfo.DESTAET),
                    null, null, eventType.isDest, ri.getField(RetrieveInfo.DESTNAPID),
                    ri.getField(RetrieveInfo.DESTNAPCODE), null, eventType.destination));
            if (eventType.isOther)
                apList.add(AuditMessages.createActiveParticipant(ri.getField(RetrieveInfo.MOVEAET),
                        null, null, eventType.isOther, ri.getField(RetrieveInfo.REQUESTORHOST),
                        AuditMessages.NetworkAccessPointTypeCode.IPAddress, null));
            String line;
            HashMap<String, AccessionNumSopClassInfo> study_accNumSOPClassInfo = new HashMap<>();
            String pID = NO_VALUE;
            String pName = null;
            String studyDate = null;
            while ((line = reader.readLine()) != null) {
                RetrieveStudyInfo rInfo = new RetrieveStudyInfo(line);
                String studyInstanceUID = rInfo.getField(RetrieveStudyInfo.STUDYUID);
                AccessionNumSopClassInfo accNumSopClassInfo = study_accNumSOPClassInfo.get(studyInstanceUID);
                if (accNumSopClassInfo == null) {
                    accNumSopClassInfo = new AccessionNumSopClassInfo(
                            rInfo.getField(RetrieveStudyInfo.ACCESSION));
                    study_accNumSOPClassInfo.put(studyInstanceUID, accNumSopClassInfo);
                }
                accNumSopClassInfo.addSOPInstance(rInfo);
                study_accNumSOPClassInfo.put(studyInstanceUID, accNumSopClassInfo);
                pID = rInfo.getField(RetrieveStudyInfo.PATIENTID);
                pName = rInfo.getField(RetrieveStudyInfo.PATIENTNAME);
                studyDate = rInfo.getField(RetrieveStudyInfo.STUDY_DATE);
            }
            HashSet<Accession> acc = new HashSet<>();
            HashSet<SOPClass> sopC = new HashSet<>();
            for (Map.Entry<String, AccessionNumSopClassInfo> entry : study_accNumSOPClassInfo.entrySet()) {
                if (null != entry.getValue().getAccNum())
                    acc.add(AuditMessages.createAccession(entry.getValue().getAccNum()));
                for (Map.Entry<String, HashSet<String>> sopClassMap : entry.getValue().getSopClassMap().entrySet()) {
                    if (ri.getField(RetrieveInfo.PARTIAL_ERROR).equals(Boolean.toString(true))
                            && eventType.outcomeIndicator.equals(AuditMessages.EventOutcomeIndicator.MinorFailure))
                        sopC.add(AuditMessages.createSOPClass(
                                sopClassMap.getValue(), sopClassMap.getKey(), sopClassMap.getValue().size()));
                    else
                        sopC.add(AuditMessages.createSOPClass(null, sopClassMap.getKey(), sopClassMap.getValue().size()));
                }
                ParticipantObjectContainsStudy pocs = new ParticipantObjectContainsStudy();
                pocs.getStudyIDs().add(AuditMessages.createStudyIDs(entry.getKey()));
                if (studyDate != null)
                    poiList.add(AuditMessages.createParticipantObjectIdentification(
                        entry.getKey(), AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID, null, null,
                        AuditMessages.ParticipantObjectTypeCode.SystemObject,
                        AuditMessages.ParticipantObjectTypeCodeRole.Report, null, null, null, acc, null, sopC, null,
                        null, pocs, AuditMessages.createParticipantObjectDetail("StudyDate", studyDate.getBytes())));
                else
                    poiList.add(AuditMessages.createParticipantObjectIdentification(
                        entry.getKey(), AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID, null, null,
                        AuditMessages.ParticipantObjectTypeCode.SystemObject,
                        AuditMessages.ParticipantObjectTypeCodeRole.Report, null, null, null, acc, null, sopC, null, null, pocs));
            }
            poiList.add(AuditMessages.createParticipantObjectIdentification(
                    pID, AuditMessages.ParticipantObjectIDTypeCode.PatientNumber, pName, null,
                    AuditMessages.ParticipantObjectTypeCode.Person,
                    AuditMessages.ParticipantObjectTypeCodeRole.Patient, null, null, null, null, null, null, null, null, null));
        }
        AuditServiceUtils.emitAuditMessage(eventType, ri.getField(RetrieveInfo.OUTCOME),
                apList, poiList, log());
    }

    void detectPatientRecordEvent(PatientMgtContext ctx) {
        HashSet<AuditServiceUtils.EventType> et = AuditServiceUtils.EventType.forHL7(ctx);
        for (AuditServiceUtils.EventType eventType : et)
            spoolPatientRecord(ctx, String.valueOf(eventType));
    }

    private void spoolPatientRecord(PatientMgtContext ctx, String et) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        boolean auditAggregate = arcDev.isAuditAggregate();
        Path dir = Paths.get(
                auditAggregate ? StringUtils.replaceSystemProperties(arcDev.getAuditSpoolDirectory()) : TMPDIR);
        boolean append = Files.exists(dir);
        try {
            if (!append)
                Files.createDirectories(dir);
            Path file = Files.createTempFile(dir, et, null);
            try (LineWriter writer = new LineWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.APPEND))) {
                String outcome = ctx.getException() != null ? ctx.getException().getMessage() : null;
                HL7Info hl7i;
                if (ctx.getHL7MessageHeader() != null) {
                    hl7i = new HL7Info(outcome, ctx.getRemoteHostName(),
                            ctx.getHL7MessageHeader().getSendingApplicationWithFacility(),
                            ctx.getHL7MessageHeader().getReceivingApplicationWithFacility(), "MSH-10",
                            ctx.getHL7MessageHeader().getField(9, ""));
                    if (et.equals(String.valueOf(AuditServiceUtils.EventType.HL7_DELT_E))
                            || et.equals(String.valueOf(AuditServiceUtils.EventType.HL7_DELT_P)))
                        writer.writeLine(new HL7Info(ctx.getPreviousPatientID(), ctx.getPreviousAttributes(), hl7i));
                    else
                        writer.writeLine(new HL7Info(ctx.getPatientID(), ctx.getAttributes(), hl7i));
                }
                if (ctx.getAssociation() != null)
                    writer.writeLine(new HL7Info(ctx.getPatientID(), ctx.getAttributes(),
                        new HL7Info(outcome, ctx.getRemoteHostName(), ctx.getAssociation().getCallingAET(),
                                ctx.getAssociation().getCalledAET(), null, null)));
            }
            if (!auditAggregate)
                aggregateAuditMessage(file);
        } catch (Exception e) {
            LOG.warn("Failed write to Audit Spool File - {} ", e);
        }
    }

    private void auditPatientRecord(Path file, AuditServiceUtils.EventType et) {
        List<ActiveParticipant> apList = new ArrayList<>();
        List<ParticipantObjectIdentification> poiList = new ArrayList<>();
        HL7Info hl7psi;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            hl7psi = new HL7Info(reader.readLine());
            apList.add(AuditMessages.createActiveParticipant(hl7psi.getField(HL7Info.CALLING_AET), null, null,
                    et.isSource, hl7psi.getField(HL7Info.CALLING_HOSTNAME),
                    AuditMessages.NetworkAccessPointTypeCode.IPAddress, null, et.source));
            apList.add(AuditMessages.createActiveParticipant(hl7psi.getField(HL7Info.CALLED_AET),
                    AuditLogger.processID(), null, et.isDest, AuditServiceUtils.getLocalHostName(log()),
                    AuditMessages.NetworkAccessPointTypeCode.IPAddress, null, et.destination));
            if (hl7psi.getField(HL7Info.POD_VALUE) != null)
                poiList.add(AuditMessages.createParticipantObjectIdentification(hl7psi.getField(HL7Info.PATIENT_ID),
                        AuditMessages.ParticipantObjectIDTypeCode.PatientNumber, hl7psi.getField(HL7Info.PATIENT_NAME),
                        null, AuditMessages.ParticipantObjectTypeCode.Person,
                        AuditMessages.ParticipantObjectTypeCodeRole.Patient,
                        null, null, null, null, null, null, null, null, null,
                        AuditMessages.createParticipantObjectDetail(hl7psi.getField(HL7Info.POD_TYPE),
                                hl7psi.getField(HL7Info.POD_VALUE).getBytes())));
            else
                poiList.add(AuditMessages.createParticipantObjectIdentification(hl7psi.getField(HL7Info.PATIENT_ID),
                        AuditMessages.ParticipantObjectIDTypeCode.PatientNumber, hl7psi.getField(HL7Info.PATIENT_NAME),
                        null, AuditMessages.ParticipantObjectTypeCode.Person,
                        AuditMessages.ParticipantObjectTypeCodeRole.Patient,
                        null, null, null, null, null, null, null, null, null));
            AuditServiceUtils.emitAuditMessage(et, hl7psi.getField(HL7Info.OUTCOME),
                    apList, poiList, log());
        } catch (Exception e) {
            LOG.warn("Failed to read audit spool file ", e);
        }
    }
}
