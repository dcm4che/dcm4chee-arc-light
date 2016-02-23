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
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.RejectionNote;
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
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

    private static final String success = "Success";
    private static final String aet = "AETITLE=";
    private static final String store = "store";
    private static final String retrieve = "retrieve";

    @Inject
    private Device device;

    private AuditLogger log() {
        return device.getDeviceExtension(AuditLogger.class);
    }


    public void emitAuditMessage(Calendar timestamp, AuditMessage msg) {
        try {
            log().write(timestamp, msg);
        } catch (Exception e) {
            LOG.warn("Failed to emit audit message", e);
        }
    }

    public void auditApplicationActivity(EventTypeCode eventTypeCode, HttpServletRequest request) {
        Calendar timestamp = log().timeStamp();
        AuditMessage msg = new AuditMessage();
        msg.setEventIdentification(AuditMessages.createEventIdentification(
                AuditMessages.EventID.ApplicationActivity, AuditMessages.EventActionCode.Execute, timestamp,
                AuditMessages.EventOutcomeIndicator.Success, success, eventTypeCode));
        msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(
                device.getDeviceName(), AuditMessages.alternativeUserIDForAETitle(
                        device.getApplicationAETitles().toArray(new String[device.getApplicationAETitles().size()])),
                null, false, device.getDeviceName(), AuditMessages.NetworkAccessPointTypeCode.IPAddress, null, AuditMessages.RoleIDCode.Application));
        if (request != null) {
            String remoteUser = request.getRemoteUser();
            msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(
                    remoteUser != null ? remoteUser : request.getRemoteAddr(), aet, null, true,
                    request.getRemoteAddr(), AuditMessages.NetworkAccessPointTypeCode.IPAddress, null,
                    AuditMessages.RoleIDCode.ApplicationLauncher));
        }
        msg.getAuditSourceIdentification().add(log().createAuditSourceIdentification());
        emitAuditMessage(timestamp, msg);
    }

    public void auditInstancesStoredOrRetrieved(PatientStudyInfo patientStudyInfo, HashSet<String> accNos,
                                     HashSet<String> mppsUIDs, HashMap<String, List<String>> sopClassMap,
                                     Calendar eventTime, String eventType, String outcome) {
        Calendar timestamp = log().timeStamp();
        AuditMessage msg = new AuditMessage();
        String eac = "";
        if (eventType.equals(store))
            eac = AuditMessages.EventActionCode.Create;
        if (eventType.equals(retrieve))
            eac = AuditMessages.EventActionCode.Read;
        if (outcome.equals(success))
            msg.setEventIdentification(AuditMessages.createEventIdentification(
                AuditMessages.EventID.DICOMInstancesTransferred, eac, eventTime,
                AuditMessages.EventOutcomeIndicator.Success, success));
        else
            msg.setEventIdentification(AuditMessages.createEventIdentification(
                    AuditMessages.EventID.DICOMInstancesTransferred, eac, eventTime,
                    AuditMessages.EventOutcomeIndicator.MinorFailure, outcome));
        if (eventType.equals(store)) {
            msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(
                    patientStudyInfo.getField(PatientStudyInfo.REMOTE_HOSTNAME),
                    aet + patientStudyInfo.getField(PatientStudyInfo.CALLING_AET), null,
                    true, patientStudyInfo.getField(PatientStudyInfo.REMOTE_HOSTNAME),
                    AuditMessages.NetworkAccessPointTypeCode.IPAddress, null, AuditMessages.RoleIDCode.Source));
            msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(
                    patientStudyInfo.getField(PatientStudyInfo.LOCAL_HOSTNAME), aet + patientStudyInfo.getField(PatientStudyInfo.CALLED_AET), null,
                    false, device.getDeviceName(), AuditMessages.NetworkAccessPointTypeCode.IPAddress, null, AuditMessages.RoleIDCode.Destination));
        }
        if (eventType.equals(retrieve)) {
            msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(
                    patientStudyInfo.getField(PatientStudyInfo.LOCAL_HOSTNAME),
                    aet + patientStudyInfo.getField(PatientStudyInfo.CALLED_AET), null, false, device.getDeviceName(),
                    AuditMessages.NetworkAccessPointTypeCode.IPAddress, null, AuditMessages.RoleIDCode.Source));
            msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(
                    patientStudyInfo.getField(PatientStudyInfo.REMOTE_HOSTNAME),
                    aet + patientStudyInfo.getField(PatientStudyInfo.CALLING_AET), null,
                    true, patientStudyInfo.getField(PatientStudyInfo.REMOTE_HOSTNAME),
                    AuditMessages.NetworkAccessPointTypeCode.IPAddress, null, AuditMessages.RoleIDCode.Destination));
        }
        msg.getAuditSourceIdentification().add(log().createAuditSourceIdentification());
        ParticipantObjectDescriptionType poiStudyDesc = new ParticipantObjectDescriptionType();
        for (String accNo : accNos)
            poiStudyDesc.getAccession().add(AuditMessages.createAccession(accNo));
        for (String mppsUID : mppsUIDs)
            poiStudyDesc.getMPPS().add(AuditMessages.createMPPS(mppsUID));
        for (Map.Entry<String, List<String>> entry : sopClassMap.entrySet())
            poiStudyDesc.getSOPClass().add(AuditMessages.createSOPClass(entry.getKey(), entry.getValue().size()));
        msg.getParticipantObjectIdentification().add(AuditMessages.createParticipantObjectIdentification(
                patientStudyInfo.getField(PatientStudyInfo.STUDY_UID),
                AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID, null, null,
                AuditMessages.ParticipantObjectTypeCode.SystemObject, AuditMessages.ParticipantObjectTypeCodeRole.Report,
                null, null, null, poiStudyDesc));
        msg.getParticipantObjectIdentification().add(AuditMessages.createParticipantObjectIdentification(
                StringUtils.maskEmpty(patientStudyInfo.getField(PatientStudyInfo.PATIENT_ID), "<none>"),
                AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                StringUtils.maskEmpty(patientStudyInfo.getField(PatientStudyInfo.PATIENT_NAME), null), null,
                AuditMessages.ParticipantObjectTypeCode.Person, AuditMessages.ParticipantObjectTypeCodeRole.Patient,
                null, null, null, null));
        emitAuditMessage(timestamp, msg);
    }

    public void auditInstancesDeleted(StoreContext ctx) {
        Calendar timestamp = log().timeStamp();
        RejectionNote rn = ctx.getRejectionNote();
        Attributes attrs = ctx.getAttributes();
        AuditMessage msg = new AuditMessage();
        String eoi, eod = "";
        if (null != ctx.getException()) {
            eoi = AuditMessages.EventOutcomeIndicator.MinorFailure;
            eod = rn.getRejectionNoteCode().getCodeMeaning() + " - " + ctx.getException().getMessage();
        } else {
            eoi = AuditMessages.EventOutcomeIndicator.Success;
            eod = rn.getRejectionNoteCode().getCodeMeaning();
        }
        msg.setEventIdentification(AuditMessages.createEventIdentification(
                AuditMessages.EventID.DICOMInstancesAccessed, AuditMessages.EventActionCode.Delete, timestamp,
                eoi, eod));
        msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(
                ctx.getStoreSession().getRemoteHostName(), aet + ctx.getStoreSession().getCallingAET(), null,
                true, ctx.getStoreSession().getRemoteHostName(), AuditMessages.NetworkAccessPointTypeCode.IPAddress,
                null));
        msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(
                ctx.getStoreSession().getArchiveAEExtension().getApplicationEntity().getDevice().getDeviceName(),
                aet + ctx.getStoreSession().getCalledAET(), null, false,
                ctx.getStoreSession().getArchiveAEExtension().getApplicationEntity().getDevice().getDeviceName(),
                AuditMessages.NetworkAccessPointTypeCode.IPAddress, null));
        msg.getAuditSourceIdentification().add(log().createAuditSourceIdentification());
        ParticipantObjectDescriptionType poiStudyDesc = new ParticipantObjectDescriptionType();
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
            poiStudyDesc.getSOPClass().add(AuditMessages.createSOPClass(entry.getKey(), entry.getValue().size()));
        }
        msg.getParticipantObjectIdentification().add(AuditMessages.createParticipantObjectIdentification(
                ctx.getStudyInstanceUID(), AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID, null, null,
                AuditMessages.ParticipantObjectTypeCode.SystemObject, AuditMessages.ParticipantObjectTypeCodeRole.Report,
                null, null, null, poiStudyDesc));
        msg.getParticipantObjectIdentification().add(AuditMessages.createParticipantObjectIdentification(
                StringUtils.maskEmpty(attrs.getString(Tag.PatientID), "<none>"),
                AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                StringUtils.maskEmpty(attrs.getString(Tag.PatientName), null), null,
                AuditMessages.ParticipantObjectTypeCode.Person, AuditMessages.ParticipantObjectTypeCodeRole.Patient,
                null, null, null, null));
        emitAuditMessage(timestamp, msg);
    }

    public void auditConnectionRejected(Socket s, Throwable e) {
        Calendar timestamp = log().timeStamp();
        AuditMessage msg = new AuditMessage();
        msg.setEventIdentification(AuditMessages.createEventIdentification(
                AuditMessages.EventID.SecurityAlert, AuditMessages.EventActionCode.Execute, timestamp,
                AuditMessages.EventOutcomeIndicator.MinorFailure, "MinorFailure", AuditMessages.EventTypeCode.NodeAuthentication));
        msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(
                s.getLocalSocketAddress().toString(), aet + "", null, false, s.getLocalSocketAddress().toString(),
                AuditMessages.NetworkAccessPointTypeCode.IPAddress, null));
        msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(
                s.getRemoteSocketAddress().toString(), aet + "", null, true, s.getRemoteSocketAddress().toString(),
                AuditMessages.NetworkAccessPointTypeCode.IPAddress, null));
        msg.getAuditSourceIdentification().add(log().createAuditSourceIdentification());
        msg.getParticipantObjectIdentification().add(AuditMessages.createParticipantObjectIdentification(
                s.getRemoteSocketAddress().toString(),
                AuditMessages.ParticipantObjectIDTypeCode.NodeID, null, null,
                AuditMessages.ParticipantObjectTypeCode.SystemObject, null, null, null, e.getMessage(), null));
        emitAuditMessage(timestamp, msg);
    }

    public void auditInstanceStored(StoreContext ctx) {
        StoreSession session = ctx.getStoreSession();
        Attributes attrs = ctx.getAttributes();
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        boolean auditAggregate = arcDev.isAuditAggregate();
        Path dir = Paths.get(
                auditAggregate ? StringUtils.replaceSystemProperties(arcDev.getAuditSpoolDirectory()) : tmpdir);
        Path file = dir.resolve(
                "onstore-" + session.getCallingAET() + '-' + session.getCalledAET() + '-' + ctx.getStudyInstanceUID());
        boolean append = Files.exists(file);
        try {
            if (!append)
                Files.createDirectories(dir);
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    append ? StandardOpenOption.APPEND : StandardOpenOption.CREATE_NEW)) {
                if (!append) {
                    writer.write(new PatientStudyInfo(ctx, attrs).toString());
                    writer.newLine();
                }
                writer.write(new InstanceInfo(ctx, attrs).toString());
                writer.newLine();
            }
            if (!auditAggregate)
                aggregateAuditMessage(file);
        } catch (IOException e) {
            LOG.warn("Failed write to Audit Spool File - {} ", file, e);
        }
    }

    public void auditWADORetrieve(RetrieveContext ctx){
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
                "onretrieve-" + req.getRemoteAddr() + '-' + ctx.getLocalAETitle() + '-' + ctx.getStudyInstanceUIDs()[0]);
        boolean append = Files.exists(file);
        try {
            if (!append)
                Files.createDirectories(dir);
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    append ? StandardOpenOption.APPEND : StandardOpenOption.CREATE_NEW)) {
                if (!append) {
                    writer.write(new PatientStudyInfo(ctx, attrs).toString());
                    writer.newLine();
                }
                writer.write(new InstanceInfo(ctx, attrs).toString());
                writer.newLine();
            }
            if (!auditAggregate)
                aggregateAuditMessage(file);
        } catch (IOException ioe) {
            LOG.warn("Failed write to Audit Spool File - {} ", file, ioe);
        }
    }

    public void aggregateAuditMessage(Path path) {
        String eventType = "";
        String file = path.getFileName().toString();
        if (file.startsWith("onretrieve"))
            eventType = retrieve;
        if (file.startsWith("onstore"))
            eventType = store;
        String outcome = "";
        PatientStudyInfo patientStudyInfo;
        HashSet<String> accNos = new HashSet<>();
        HashSet<String> mppsUIDs = new HashSet<>();
        HashMap<String, List<String>> sopClassMap = new HashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            patientStudyInfo = new PatientStudyInfo(reader.readLine());
            outcome = patientStudyInfo.getField(PatientStudyInfo.OUTCOME);
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
                for (int i = InstanceInfo.ACCESSION_NO; instanceInfo.getField(i) != null; i++)
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
        } catch (IOException e) {
            LOG.warn("Failed to get Last Modified Time of Audit Spool File - {} ", path, e);
        }
        auditInstancesStoredOrRetrieved(patientStudyInfo, accNos, mppsUIDs, sopClassMap, eventTime, eventType, outcome);
        try {
            Files.delete(path);
        } catch (IOException e) {
            LOG.warn("Failed to delete Audit Spool File - {}", path, e);
        }
    }

    public void auditQuery(Association as, HttpServletRequest request,
                           Attributes queryKeys, String callingAET, String calledAET,
                           String remoteHostName, String localDevice, String sopClassUID) {
        Calendar timestamp = log().timeStamp();
        AuditMessage msg = new AuditMessage();
        msg.setEventIdentification(AuditMessages.createEventIdentification(
                AuditMessages.EventID.Query, AuditMessages.EventActionCode.Execute, timestamp,
                AuditMessages.EventOutcomeIndicator.Success, success));
        msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(remoteHostName, aet + callingAET, null, true,
                remoteHostName, AuditMessages.NetworkAccessPointTypeCode.IPAddress, null,
                AuditMessages.RoleIDCode.Source));
        msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(localDevice, aet + calledAET, null, false,
                localDevice, AuditMessages.NetworkAccessPointTypeCode.IPAddress, null,
                AuditMessages.RoleIDCode.Destination));
        msg.getAuditSourceIdentification().add(log().createAuditSourceIdentification());
        byte[] b = new byte[]{};
        ParticipantObjectDetail pod = new ParticipantObjectDetail();
        if (request != null) {
            String queryString = request.getRequestURI() + request.getQueryString();
            b = queryString.getBytes();
            pod = null;
        }
        if (as != null) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (DicomOutputStream dos = new DicomOutputStream(bos, UID.ImplicitVRLittleEndian)) {
                dos.writeDataset(null, queryKeys);
            } catch (Exception e) {
                LOG.warn("Failed to create DicomOutputStream : ", e);
            }
            b = bos.toByteArray();
            pod = AuditMessages.createParticipantObjectDetail("TransferSyntax", UID.ImplicitVRLittleEndian.getBytes());
        }
        msg.getParticipantObjectIdentification().add(AuditMessages.createParticipantObjectIdentification(sopClassUID,
                AuditMessages.ParticipantObjectIDTypeCode.SOPClassUID, null, b,
                AuditMessages.ParticipantObjectTypeCode.SystemObject, AuditMessages.ParticipantObjectTypeCodeRole.Report,
                null, null, null, null, pod));
        emitAuditMessage(timestamp, msg);
    }

    public void auditDICOMInstancesTransfer(RetrieveContext ctx, AuditMessages.EventID eventID, Exception e) {
        Calendar timestamp = log().timeStamp();
        AuditMessage msg = new AuditMessage();
        String eac, eoi, eod = "";
        if (null != e) {
            eac = AuditMessages.EventActionCode.Execute;
            eoi = AuditMessages.EventOutcomeIndicator.MinorFailure;
            if (ctx.isLocalRequestor())
                eod = "Failed on Export Forwarding : " + e.getMessage();
            if (null != ctx.getRequestAssociation() && null != ctx.getStoreAssociation()
                    && ctx.getRequestAssociation() == ctx.getStoreAssociation())
                eod = "C-GET Failed : " + e.getMessage();
            if (!ctx.isDestinationRequestor() && !ctx.isLocalRequestor())
                eod = "C-MOVE Failed : " + e.getMessage();
        }
        else {
            eac = AuditMessages.EventActionCode.Execute;
            eoi = AuditMessages.EventOutcomeIndicator.Success;
            eod = success;
            if (eventID.equals(AuditMessages.EventID.BeginTransferringDICOMInstances))
                eac = AuditMessages.EventActionCode.Execute;
            if (eventID.equals(AuditMessages.EventID.DICOMInstancesTransferred))
                eac = AuditMessages.EventActionCode.Read;
        }
        msg.setEventIdentification(AuditMessages.createEventIdentification(eventID, eac, timestamp, eoi, eod));
        boolean sender = false;
        boolean receiver = false;
        if (ctx.isLocalRequestor())
            sender = true;
        msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(
                ctx.getLocalApplicationEntity().getDevice().getDeviceName(),
                aet + ctx.getLocalAETitle(), null, sender, ctx.getLocalApplicationEntity().getDevice().getDeviceName(),
                AuditMessages.NetworkAccessPointTypeCode.IPAddress, null, AuditMessages.RoleIDCode.Source));
        if (null != ctx.getRequestAssociation() && null != ctx.getStoreAssociation() && ctx.getRequestAssociation().equals(ctx.getStoreAssociation()))
            receiver = true;
        if (null != ctx.getDestinationHostName())
            msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(
                ctx.getDestinationHostName(), aet + ctx.getDestinationAETitle(), null, receiver, ctx.getDestinationAETitle(),
                AuditMessages.NetworkAccessPointTypeCode.IPAddress, null, AuditMessages.RoleIDCode.Destination));
        else
            msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(
                    ctx.getDestinationAETitle(), aet + ctx.getDestinationAETitle(), null, receiver, null,
                    null, null, AuditMessages.RoleIDCode.Destination));
        if (!ctx.isDestinationRequestor() && !ctx.isLocalRequestor()) {
            msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(
                    ctx.getRequestorHostName(), aet + ctx.getMoveOriginatorAETitle(), null, true,
                    ctx.getRequestorHostName(), AuditMessages.NetworkAccessPointTypeCode.IPAddress, null));
        }
        msg.getAuditSourceIdentification().add(log().createAuditSourceIdentification());
        HashMap<String, String> patientID_NameMap = new HashMap<>();
        HashMap<String, AccessionNumSopClassInfo> study_accNumSOPClassInfo = new HashMap<>();
        for (InstanceLocations il : ctx.getMatches()) {
            Attributes attrs = il.getAttributes();
            String studyInstanceUID = attrs.getString(Tag.StudyInstanceUID);
            AccessionNumSopClassInfo accessionNumSopClassInfo = study_accNumSOPClassInfo.get(studyInstanceUID);
            if (accessionNumSopClassInfo == null) {
                accessionNumSopClassInfo = new AccessionNumSopClassInfo(attrs.getString(Tag.AccessionNumber, ""));
                study_accNumSOPClassInfo.put(studyInstanceUID, accessionNumSopClassInfo);
            }
            accessionNumSopClassInfo.addSOPInstance(attrs);
            study_accNumSOPClassInfo.put(studyInstanceUID, accessionNumSopClassInfo);
            patientID_NameMap.put(attrs.getString(Tag.PatientID, ""), attrs.getString(Tag.PatientName, ""));
        }
        for (Map.Entry<String, AccessionNumSopClassInfo> entry : study_accNumSOPClassInfo.entrySet()) {
            ParticipantObjectDescriptionType poiStudyDesc = new ParticipantObjectDescriptionType();
            poiStudyDesc.getAccession().add(AuditMessages.createAccession(entry.getValue().getAccNum()));
            for (Map.Entry<String, HashSet<String>> sopClassMap : entry.getValue().getSopClassMap().entrySet())
                poiStudyDesc.getSOPClass().add(AuditMessages.createSOPClass(
                        sopClassMap.getKey(), sopClassMap.getValue().size()));
            msg.getParticipantObjectIdentification().add(AuditMessages.createParticipantObjectIdentification(
                    entry.getKey(), AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID, null, null,
                    AuditMessages.ParticipantObjectTypeCode.SystemObject,
                    AuditMessages.ParticipantObjectTypeCodeRole.Report, null, null, null, poiStudyDesc));
        }
        String pID = "";
        String pName = "";
        for (Map.Entry<String, String> entry : patientID_NameMap.entrySet()) {
            pID = entry.getKey();
            pName = entry.getValue();
        }
        msg.getParticipantObjectIdentification().add(AuditMessages.createParticipantObjectIdentification(
                pID, AuditMessages.ParticipantObjectIDTypeCode.PatientNumber, pName, null,
                AuditMessages.ParticipantObjectTypeCode.Person,
                AuditMessages.ParticipantObjectTypeCodeRole.Patient, null, null, null, null));
        emitAuditMessage(timestamp, msg);
    }

    private static class PatientStudyInfo {
        public static final int LOCAL_HOSTNAME = 0;
        public static final int REMOTE_HOSTNAME = 1;
        public static final int CALLING_AET = 2;
        public static final int CALLED_AET = 3;
        public static final int STUDY_UID = 4;
        public static final int ACCESSION_NO = 5;
        public static final int PATIENT_ID = 6;
        public static final int PATIENT_NAME = 7;
        public static final int OUTCOME = 8;
        String outcome = success;

        private final String[] fields;

        public PatientStudyInfo(StoreContext ctx, Attributes attrs) {
            StoreSession session = ctx.getStoreSession();
            if (null != ctx.getException())
                outcome = ctx.getException().getMessage();
            fields = new String[] {
                    session.getArchiveAEExtension().getApplicationEntity().getDevice().getDeviceName(),
                    session.getRemoteHostName(),
                    session.getCallingAET(),
                    session.getCalledAET(),
                    ctx.getStudyInstanceUID(),
                    attrs.getString(Tag.AccessionNumber, ""),
                    attrs.getString(Tag.PatientID, ""),
                    attrs.getString(Tag.PatientName, ""),
                    outcome
            };
        }

        public PatientStudyInfo(RetrieveContext ctx, Attributes attrs) {
            if (null != ctx.getException())
                outcome = ctx.getException().getMessage();
            fields = new String[] {
                    ctx.getArchiveAEExtension().getApplicationEntity().getDevice().getDeviceName(),
                    ctx.getHttpRequest().getRemoteAddr(),
                    "",
                    ctx.getLocalAETitle(),
                    ctx.getStudyInstanceUIDs()[0],
                    attrs.getString(Tag.AccessionNumber, ""),
                    attrs.getString(Tag.PatientID, ""),
                    attrs.getString(Tag.PatientName, ""),
                    outcome
            };
        }

        public PatientStudyInfo(String s) {
            fields = StringUtils.split(s, '\\');
        }

        public String getField(int field) {
            return fields[field];
        }

        @Override
        public String toString() {
            return StringUtils.concat(fields, '\\');
        }
    }

    private static class InstanceInfo {
        public static final int CLASS_UID = 0;
        public static final int INSTANCE_UID = 1;
        public static final int MPPS_UID = 2;
        public static final int ACCESSION_NO = 3;

        private final String[] fields;

        public InstanceInfo(StoreContext ctx, Attributes attrs) {
            ArrayList<String> list = new ArrayList<>();
            list.add(ctx.getSopClassUID());
            list.add(ctx.getSopInstanceUID());
            list.add(StringUtils.maskNull(ctx.getMppsInstanceUID(), ""));
            Sequence reqAttrs = attrs.getSequence(Tag.RequestAttributesSequence);
            if (reqAttrs != null)
                for (Attributes reqAttr : reqAttrs) {
                    String accno = reqAttr.getString(Tag.AccessionNumber);
                    if (accno != null)
                        list.add(accno);
                }
            this.fields = list.toArray(new String[list.size()]);
        }

        public InstanceInfo(RetrieveContext ctx, Attributes attrs) {
            ArrayList<String> list = new ArrayList<>();
            list.add(attrs.getString(Tag.SOPClassUID, ""));
            list.add(ctx.getSopInstanceUIDs()[0]);
            list.add("");
            Sequence reqAttrs = attrs.getSequence(Tag.RequestAttributesSequence);
            if (reqAttrs != null)
                for (Attributes reqAttr : reqAttrs) {
                    String accno = reqAttr.getString(Tag.AccessionNumber);
                    if (accno != null)
                        list.add(accno);
                }
            this.fields = list.toArray(new String[list.size()]);
        }

        public InstanceInfo(String s) {
            fields = StringUtils.split(s, '\\');
        }

        public String getField(int field) {
            return field < fields.length ? fields[field] : null;
        }

        @Override
        public String toString() {
            return StringUtils.concat(fields, '\\');
        }
    }

    private static class AccessionNumSopClassInfo {
        private final String accNum;
        private HashMap<String, HashSet<String>> sopClassMap = new HashMap<>();

        public AccessionNumSopClassInfo(String accNum) {
            this.accNum = accNum;
        }

        public String getAccNum() {
            return accNum;
        }
        public HashMap<String, HashSet<String>> getSopClassMap() {
            return sopClassMap;
        }
        public void addSOPInstance(Attributes attrs) {
            String cuid = attrs.getString(Tag.SOPClassUID);
            HashSet<String> iuids = sopClassMap.get(cuid);
            if (iuids == null) {
                iuids = new HashSet<>();
                sopClassMap.put(cuid, iuids);
            }
            iuids.add(attrs.getString(Tag.SOPInstanceUID));
        }
    }
}
