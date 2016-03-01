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
    enum EventType {
        WADO_R_P__(AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read, AuditMessages.EventOutcomeIndicator.Success,
                AuditMessages.RoleIDCode.Destination, AuditMessages.RoleIDCode.Source, true, false, false, null),
        WADO_R_E__(AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read, AuditMessages.EventOutcomeIndicator.MinorFailure,
                AuditMessages.RoleIDCode.Destination, AuditMessages.RoleIDCode.Source, true, false, false, null),
        STORE_C_P_(AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Create, AuditMessages.EventOutcomeIndicator.Success,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, true, false, false, null),
        STORE_C_E_(AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Create, AuditMessages.EventOutcomeIndicator.MinorFailure,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, true, false, false, null),
        STORE_U_P_(AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Update, AuditMessages.EventOutcomeIndicator.Success,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, true, false, false, null),
        STORE_U_E_(AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Update, AuditMessages.EventOutcomeIndicator.MinorFailure,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, true, false, false, null),

        BEGIN__M_P(AuditMessages.EventID.BeginTransferringDICOMInstances, AuditMessages.EventActionCode.Execute, AuditMessages.EventOutcomeIndicator.Success,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, false, true, null),
        BEGIN__M_E(AuditMessages.EventID.BeginTransferringDICOMInstances, AuditMessages.EventActionCode.Execute, AuditMessages.EventOutcomeIndicator.MinorFailure,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, false, true, null),
        BEGIN__G_P(AuditMessages.EventID.BeginTransferringDICOMInstances, AuditMessages.EventActionCode.Execute, AuditMessages.EventOutcomeIndicator.Success,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, true, false, null),
        BEGIN__G_E(AuditMessages.EventID.BeginTransferringDICOMInstances, AuditMessages.EventActionCode.Execute, AuditMessages.EventOutcomeIndicator.MinorFailure,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, true, false, null),
        BEGIN__E_P(AuditMessages.EventID.BeginTransferringDICOMInstances, AuditMessages.EventActionCode.Execute, AuditMessages.EventOutcomeIndicator.Success,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, true, false, false, null),
        BEGIN__E_E(AuditMessages.EventID.BeginTransferringDICOMInstances, AuditMessages.EventActionCode.Execute, AuditMessages.EventOutcomeIndicator.MinorFailure,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, true, false, null),
        TRF__MVE_P(AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read, AuditMessages.EventOutcomeIndicator.Success,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, false, true, null),
        TRF__MVE_E(AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read, AuditMessages.EventOutcomeIndicator.MinorFailure,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, false, true, null),
        TRF__GET_P(AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read, AuditMessages.EventOutcomeIndicator.Success,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, true, false, null),
        TRF__GET_E(AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read, AuditMessages.EventOutcomeIndicator.MinorFailure,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, true, false, null),
        TRF__EXP_P(AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read, AuditMessages.EventOutcomeIndicator.Success,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, true, false, false, null),
        TRF__EXP_E(AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read, AuditMessages.EventOutcomeIndicator.MinorFailure,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, true, false, false, null),

        DELETE_PAS(AuditMessages.EventID.DICOMInstancesAccessed, AuditMessages.EventActionCode.Delete, AuditMessages.EventOutcomeIndicator.Success,
                null, null, true, false, false, null),
        DELETE_ERR(AuditMessages.EventID.DICOMInstancesAccessed, AuditMessages.EventActionCode.Delete, AuditMessages.EventOutcomeIndicator.MinorFailure,
                null, null, true, false, false, null),

        APPLNSTART(AuditMessages.EventID.ApplicationActivity, AuditMessages.EventActionCode.Execute, AuditMessages.EventOutcomeIndicator.Success,
                   null, null, false, false, false, AuditMessages.EventTypeCode.ApplicationStart),
        APPLN_STOP(AuditMessages.EventID.ApplicationActivity, AuditMessages.EventActionCode.Execute, AuditMessages.EventOutcomeIndicator.Success,
                   null, null, false, false, false, AuditMessages.EventTypeCode.ApplicationStop),

        CONN__RJCT(AuditMessages.EventID.SecurityAlert, AuditMessages.EventActionCode.Execute,
                AuditMessages.EventOutcomeIndicator.MinorFailure, null, null, false, false, false, AuditMessages.EventTypeCode.NodeAuthentication);

        final AuditMessages.EventID eventID;
        final String eventActionCode;
        final String outcomeIndicator;
        final AuditMessages.RoleIDCode source;
        final AuditMessages.RoleIDCode destination;
        final boolean isSource;
        final boolean isDest;
        final boolean isOther;
        final EventTypeCode eventTypeCode;


        EventType(AuditMessages.EventID eventID, String eventActionCode, String outcome, AuditMessages.RoleIDCode source,
                        AuditMessages.RoleIDCode destination, boolean isSource, boolean isDest, boolean isOther, EventTypeCode etc) {
            this.eventID = eventID;
            this.eventActionCode = eventActionCode;
            this.outcomeIndicator = outcome;
            this.source = source;
            this.destination = destination;
            this.isSource = isSource;
            this.isDest = isDest;
            this.isOther = isOther;
            this.eventTypeCode = etc;
        }

        static EventType fromFile(Path file) {
            return valueOf(file.getFileName().toString().substring(0, 10));
        }

        static EventType forWADORetrieve(RetrieveContext ctx) {
            return ctx.getException() != null ? WADO_R_E__ : WADO_R_P__;
        }

        static EventType forInstanceStored(StoreContext ctx) {
            return ctx.getException() != null
                    ? ctx.getPreviousInstance() != null ? STORE_U_E_ : STORE_C_E_
                    : ctx.getLocation() != null
                    ? ctx.getPreviousInstance() != null ? STORE_U_P_ : STORE_C_P_
                    : null;
        }

        static EventType forBeginTransfer(RetrieveContext ctx) {
            EventType at = null;
            if (ctx.getException() != null) {
                if (ctx.isLocalRequestor())
                    at = BEGIN__E_E;
                if (!ctx.isDestinationRequestor() && !ctx.isLocalRequestor())
                    at = BEGIN__M_E;
                if (ctx.getRequestAssociation() != null && ctx.getStoreAssociation() != null && ctx.isDestinationRequestor())
                    at = BEGIN__G_E;
            } else {
                if (ctx.isLocalRequestor())
                    at = BEGIN__E_P;
                if (!ctx.isDestinationRequestor() && !ctx.isLocalRequestor())
                    at = BEGIN__M_P;
                if (ctx.getRequestAssociation() != null && ctx.getStoreAssociation() != null && ctx.isDestinationRequestor())
                    at = BEGIN__G_P;
            }
            return at;
        }

        static EventType forDicomInstTransferred(RetrieveContext ctx) {
            EventType at = null;
            if (ctx.getException() != null) {
                if (ctx.isLocalRequestor())
                    at = TRF__EXP_E;
                if (!ctx.isDestinationRequestor() && !ctx.isLocalRequestor())
                    at = TRF__MVE_E;
                if (ctx.getRequestAssociation() != null && ctx.getStoreAssociation() != null && ctx.isDestinationRequestor())
                    at = TRF__GET_E;
            } else {
                if (ctx.isLocalRequestor())
                    at = TRF__EXP_P;
                if (!ctx.isDestinationRequestor() && !ctx.isLocalRequestor())
                    at = TRF__MVE_P;
                if (ctx.getRequestAssociation() != null && ctx.getStoreAssociation() != null && ctx.isDestinationRequestor())
                    at = TRF__GET_P;
            }
            return at;
        }
    }

    @Inject
    private Device device;

    private AuditLogger log() {
        return device.getDeviceExtension(AuditLogger.class);
    }

    public void aggregateAuditMessage(Path path) {
        EventType eventType = EventType.fromFile(path);
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
        }
    }

    private void emitAuditMessage(Calendar timestamp, AuditMessage msg) {
        try {
            log().write(timestamp, msg);
        } catch (Exception e) {
            LOG.warn("Failed to emit audit message", e);
        }
    }

    private static void deleteFile(Path file) {
        try {
            Files.delete(file);
        } catch (IOException e) {
            LOG.warn("Failed to delete Audit Spool File - {}", file, e);
        }
    }

    public void auditApplicationActivity(EventType eventType, HttpServletRequest req) {
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
        EventType et = (ctx.getException() != null) ? EventType.DELETE_ERR : EventType.DELETE_PAS;
        Path file = dir.resolve(String.valueOf(et));
        Attributes attrs = ctx.getAttributes();
        boolean append = Files.exists(file);
        try {
            if (!append)
                Files.createDirectories(dir);
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    append ? StandardOpenOption.APPEND : StandardOpenOption.CREATE_NEW)) {
                if (!append) {
                    writer.write(new DeleteInfo(ctx).toString());
                    writer.newLine();
                }
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
                    writer.write(new DeleteStudyInfo(entry.getKey(), String.valueOf(entry.getValue().size())).toString());
                    writer.newLine();
                }
            }
            if (!auditAggregate)
                aggregateAuditMessage(file);
        } catch (IOException e) {
            LOG.warn("Failed to write to Audit Spool File - {} ", file, e);
        }
    }

    private void auditInstanceDeletion(Path path, EventType eventType) {
        Calendar timestamp = log().timeStamp();
        AuditMessage msg = new AuditMessage();
        DeleteInfo deleteInfo;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            deleteInfo = new DeleteInfo(reader.readLine());
            msg.setEventIdentification(AuditMessages.createEventIdentification(
                    eventType.eventID, eventType.eventActionCode, timestamp,
                    eventType.outcomeIndicator, deleteInfo.getField(DeleteInfo.OUTCOME)));
            msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(
                    deleteInfo.getField(DeleteInfo.REMOTEHOST), AuditMessages.alternativeUserIDForAETitle(
                            deleteInfo.getField(DeleteInfo.REMOTEAET)), null, eventType.isSource, deleteInfo.getField(DeleteInfo.REMOTEHOST),
                    AuditMessages.NetworkAccessPointTypeCode.IPAddress, null));
            msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(
                    deleteInfo.getField(DeleteInfo.LOCALHOST),
                    AuditMessages.alternativeUserIDForAETitle(deleteInfo.getField(DeleteInfo.LOCALAET)), null,
                    eventType.isDest, null, null, null));
            msg.getAuditSourceIdentification().add(log().createAuditSourceIdentification());
            ParticipantObjectContainsStudy pocs = new ParticipantObjectContainsStudy();
            pocs.getStudyIDs().add(AuditMessages.createStudyIDs(deleteInfo.getField(DeleteInfo.STUDYUID)));
            String line;
            HashSet<SOPClass> sopC = new HashSet<>();
            while ((line = reader.readLine()) != null) {
                DeleteStudyInfo dsi = new DeleteStudyInfo(line);
                dsi.getField(DeleteStudyInfo.SOPCLASSUID);
                dsi.getField(DeleteStudyInfo.NUMINSTANCES);
                sopC.add(AuditMessages.createSOPClass(
                        dsi.getField(DeleteStudyInfo.SOPCLASSUID), Integer.parseInt(dsi.getField(DeleteStudyInfo.NUMINSTANCES))));
            }
            msg.getParticipantObjectIdentification().add(AuditMessages.createParticipantObjectIdentification(
                    deleteInfo.getField(DeleteInfo.STUDYUID), AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID,
                    null, null, AuditMessages.ParticipantObjectTypeCode.SystemObject,
                    AuditMessages.ParticipantObjectTypeCodeRole.Report, null, null, null, null, null, sopC, null, null, pocs));
            msg.getParticipantObjectIdentification().add(AuditMessages.createParticipantObjectIdentification(
                    deleteInfo.getField(DeleteInfo.PATIENTID), AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                    deleteInfo.getField(DeleteInfo.PATIENTNAME), null, AuditMessages.ParticipantObjectTypeCode.Person,
                    AuditMessages.ParticipantObjectTypeCodeRole.Patient, null, null, null, null, null, null, null, null, null));
        } catch (Exception e) {
            LOG.warn("Failed to read Audit Spool File - {} ", path, e);
            return;
        }
        emitAuditMessage(timestamp, msg);
        deleteFile(path);
    }

    public void collateConnectionRejected(Socket s, Throwable e) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        boolean auditAggregate = arcDev.isAuditAggregate();
        Path dir = Paths.get(
                auditAggregate ? StringUtils.replaceSystemProperties(arcDev.getAuditSpoolDirectory()) : tmpdir);
        Path file = dir.resolve(String.valueOf(EventType.CONN__RJCT));
        boolean append = Files.exists(file);
        try {
            if (!append)
                Files.createDirectories(dir);
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    append ? StandardOpenOption.APPEND : StandardOpenOption.CREATE_NEW)) {
                if (!append) {
                    writer.write(new ConnectionRejectedInfo(s, e).toString());
                    writer.newLine();
                }
            }
            if (!auditAggregate)
                aggregateAuditMessage(file);

        } catch (Exception ex) {
            LOG.warn("Failed to write to Audit Spool File - {} ", file, ex);
        }
    }

    private void auditConnectionRejected(Path file, EventType eventType) {
        Calendar timestamp = log().timeStamp();
        AuditMessage msg = new AuditMessage();
        ConnectionRejectedInfo crInfo;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            crInfo = new ConnectionRejectedInfo(reader.readLine());
            msg.setEventIdentification(AuditMessages.createEventIdentification(
                    eventType.eventID, eventType.eventActionCode, timestamp,
                    eventType.outcomeIndicator, crInfo.getField(ConnectionRejectedInfo.OUTCOME_DESC), eventType.eventTypeCode));
            msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(
                    crInfo.getField(ConnectionRejectedInfo.LOCAL_ADDR), null, null, false,
                    crInfo.getField(ConnectionRejectedInfo.LOCAL_ADDR),
                    AuditMessages.NetworkAccessPointTypeCode.IPAddress, null));
            msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(
                    crInfo.getField(ConnectionRejectedInfo.REMOTE_ADDR), null, null, true,
                    crInfo.getField(ConnectionRejectedInfo.REMOTE_ADDR),
                    AuditMessages.NetworkAccessPointTypeCode.IPAddress, null));
            msg.getAuditSourceIdentification().add(log().createAuditSourceIdentification());
            List<String> desc = new ArrayList<>();
            desc.add(crInfo.getField(ConnectionRejectedInfo.PO_DESC));
            msg.getParticipantObjectIdentification().add(AuditMessages.createParticipantObjectIdentification(
                    crInfo.getField(ConnectionRejectedInfo.REMOTE_ADDR), AuditMessages.ParticipantObjectIDTypeCode.NodeID,
                    null, null, AuditMessages.ParticipantObjectTypeCode.SystemObject, null, null, null, desc, null, null,
                    null, null, null, null));
        } catch (Exception e) {
            LOG.warn("Failed to read Audit Spool File - {} ", file, e);
            return;
        }
        emitAuditMessage(timestamp, msg);
        deleteFile(file);
    }

    public void collateQuery(Association as, HttpServletRequest request,
                           Attributes queryKeys, String callingAET, String calledAET,
                           String remoteHostName, String localDevice, String sopClassUID) {
        Calendar timestamp = log().timeStamp();
        AuditMessage msg = new AuditMessage();
        msg.setEventIdentification(AuditMessages.createEventIdentification(
                AuditMessages.EventID.Query, AuditMessages.EventActionCode.Execute, timestamp,
                AuditMessages.EventOutcomeIndicator.Success, null));
        msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(remoteHostName,
                AuditMessages.alternativeUserIDForAETitle(callingAET), null, true, remoteHostName,
                AuditMessages.NetworkAccessPointTypeCode.IPAddress, null, AuditMessages.RoleIDCode.Source));
        msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(localDevice,
                AuditMessages.alternativeUserIDForAETitle(calledAET), null, false, localDevice,
                AuditMessages.NetworkAccessPointTypeCode.IPAddress, null, AuditMessages.RoleIDCode.Destination));
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
                null, null, null, null, null, null, null, null, null, pod));
        emitAuditMessage(timestamp, msg);
    }

    public void collateInstanceStored(StoreContext ctx) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        boolean auditAggregate = arcDev.isAuditAggregate();
        Path dir = Paths.get(
                auditAggregate ? StringUtils.replaceSystemProperties(arcDev.getAuditSpoolDirectory()) : tmpdir);
        EventType eventType = EventType.forInstanceStored(ctx);
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
                    writer.write(new PatientStudyInfo(ctx, attrs).toString());
                    writer.newLine();
                }
                writer.write(new InstanceInfo(ctx, attrs).toString());
                writer.newLine();
            }
            if (!auditAggregate)
                aggregateAuditMessage(file);
        } catch (IOException e) {
            LOG.warn("Failed to write to Audit Spool File - {} ", file, e);
        }
    }

    public void collateWADORetrieve(RetrieveContext ctx){
        EventType aggregationType = EventType.forWADORetrieve(ctx);
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

    private void aggregateStoreOrWADORetrieve(Path path, EventType eventType) {
        String outcome;
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
        auditInstancesStoredOrWADORetrieve(patientStudyInfo, accNos, mppsUIDs, sopClassMap, eventTime, eventType, outcome);
        deleteFile(path);
    }

    public void auditInstancesStoredOrWADORetrieve(PatientStudyInfo patientStudyInfo, HashSet<String> accNos,
                                                   HashSet<String> mppsUIDs, HashMap<String, List<String>> sopClassMap,
                                                   Calendar eventTime, EventType eventType, String outcomeDesc) {
        Calendar timestamp = log().timeStamp();
        AuditMessage msg = new AuditMessage();
        msg.setEventIdentification(AuditMessages.createEventIdentification(
                AuditMessages.EventID.DICOMInstancesTransferred, eventType.eventActionCode, eventTime,
                eventType.outcomeIndicator, outcomeDesc));
        msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(
                patientStudyInfo.getField(PatientStudyInfo.REMOTE_HOSTNAME),
                AuditMessages.alternativeUserIDForAETitle(patientStudyInfo.getField(PatientStudyInfo.CALLING_AET)),
                null, true, patientStudyInfo.getField(PatientStudyInfo.REMOTE_HOSTNAME),
                AuditMessages.NetworkAccessPointTypeCode.IPAddress, null, eventType.source));
        msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(
                patientStudyInfo.getField(PatientStudyInfo.LOCAL_HOSTNAME),
                AuditMessages.alternativeUserIDForAETitle(patientStudyInfo.getField(PatientStudyInfo.CALLED_AET)),
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
        pocs.getStudyIDs().add(AuditMessages.createStudyIDs(patientStudyInfo.getField(PatientStudyInfo.STUDY_UID)));
        msg.getParticipantObjectIdentification().add(AuditMessages.createParticipantObjectIdentification(
                patientStudyInfo.getField(PatientStudyInfo.STUDY_UID),
                AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID, null, null,
                AuditMessages.ParticipantObjectTypeCode.SystemObject, AuditMessages.ParticipantObjectTypeCodeRole.Report,
                null, null, null, acc, mpps, sopC, null, null, pocs));
        msg.getParticipantObjectIdentification().add(AuditMessages.createParticipantObjectIdentification(
                StringUtils.maskEmpty(patientStudyInfo.getField(PatientStudyInfo.PATIENT_ID), "<none>"),
                AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                StringUtils.maskEmpty(patientStudyInfo.getField(PatientStudyInfo.PATIENT_NAME), null), null,
                AuditMessages.ParticipantObjectTypeCode.Person, AuditMessages.ParticipantObjectTypeCodeRole.Patient,
                null, null, null, null, null, null, null, null, null));
        emitAuditMessage(timestamp, msg);
    }

    public void collateRetrieve(RetrieveContext ctx, EventType et) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        boolean auditAggregate = arcDev.isAuditAggregate();
        Path dir = Paths.get(
                auditAggregate ? StringUtils.replaceSystemProperties(arcDev.getAuditSpoolDirectory()) : tmpdir);
        Path file = dir.resolve(String.valueOf(et));
        boolean append = Files.exists(file);
        try {
            if (!append)
                Files.createDirectories(dir);
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    append ? StandardOpenOption.APPEND : StandardOpenOption.CREATE_NEW)) {
                if (!append) {
                    writer.write(new RetrieveInfo(ctx).toString());
                    writer.newLine();
                    for (InstanceLocations il : ctx.getMatches()) {
                        Attributes attrs = il.getAttributes();
                        writer.write(new RetrieveStudyInfo(attrs).toString());
                        writer.newLine();
                    }
                }
            }
            if (!auditAggregate)
                aggregateAuditMessage(file);
        } catch (IOException e) {
            LOG.warn("Failed write to Audit Spool File - {} ", file, e);
        }
    }

    private void auditRetrieve(Path file, EventType eventType) {
        Calendar eventTime = log().timeStamp();
        try {
            eventTime.setTimeInMillis(Files.getLastModifiedTime(file).toMillis());
        } catch (IOException e) {
            LOG.warn("Failed to get Last Modified Time of Audit Spool File - {} ", file, e);
        }
        AuditMessage msg = new AuditMessage();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            RetrieveInfo ri = new RetrieveInfo(reader.readLine());
            msg.setEventIdentification(AuditMessages.createEventIdentification(eventType.eventID, eventType.eventActionCode,
                    eventTime, eventType.outcomeIndicator, ri.getField(RetrieveInfo.OUTCOME)));
            msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(
                    ri.getField(RetrieveInfo.LOCALHOST), AuditMessages.alternativeUserIDForAETitle(ri.getField(RetrieveInfo.LOCALAET)),
                    null, eventType.isSource, null, null, null, eventType.source));
            msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(ri.getField(RetrieveInfo.DESTHOST),
                    AuditMessages.alternativeUserIDForAETitle(ri.getField(RetrieveInfo.DESTAET)), null, eventType.isDest,
                    ri.getField(RetrieveInfo.DESTNAPID), ri.getField(RetrieveInfo.DESTNAPCODE), null, eventType.destination));
            if (eventType.isOther)
                msg.getActiveParticipant().add(AuditMessages.createActiveParticipant(ri.getField(RetrieveInfo.REQUESTORHOST),
                        AuditMessages.alternativeUserIDForAETitle(ri.getField(RetrieveInfo.MOVEAET)), null, eventType.isOther,
                        ri.getField(RetrieveInfo.REQUESTORHOST), AuditMessages.NetworkAccessPointTypeCode.IPAddress, null));
            msg.getAuditSourceIdentification().add(log().createAuditSourceIdentification());
            String line;
            HashMap<String, AccessionNumSopClassInfo> study_accNumSOPClassInfo = new HashMap<>();
            String pID = "<none>";
            String pName = null;
            while ((line = reader.readLine()) != null) {
                RetrieveStudyInfo rInfo = new RetrieveStudyInfo(line);
                String studyInstanceUID = rInfo.getField(RetrieveStudyInfo.STUDYUID);
                AccessionNumSopClassInfo accessionNumSopClassInfo = study_accNumSOPClassInfo.get(studyInstanceUID);
                if (accessionNumSopClassInfo == null) {
                    accessionNumSopClassInfo = new AccessionNumSopClassInfo(rInfo.getField(RetrieveStudyInfo.ACCESSION));
                    study_accNumSOPClassInfo.put(studyInstanceUID, accessionNumSopClassInfo);
                }
                accessionNumSopClassInfo.addSOPInstance(rInfo);
                study_accNumSOPClassInfo.put(studyInstanceUID, accessionNumSopClassInfo);
                pID = rInfo.getField(RetrieveStudyInfo.PATIENTID);
                pName = rInfo.getField(RetrieveStudyInfo.PATIENTNAME);
            }
            HashSet<Accession> acc = new HashSet<>();
            HashSet<SOPClass> sopC = new HashSet<>();
            for (Map.Entry<String, AccessionNumSopClassInfo> entry : study_accNumSOPClassInfo.entrySet()) {
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
        deleteFile(file);
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

        private final String[] fields;

        public PatientStudyInfo(StoreContext ctx, Attributes attrs) {
            StoreSession session = ctx.getStoreSession();
            String outcome = (null != ctx.getException()) ? ctx.getException().getMessage(): null;
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
            String outcome = (null != ctx.getException()) ? ctx.getException().getMessage(): null;
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
            list.add(attrs.getString(Tag.SOPClassUID));
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
        public void addSOPInstance(RetrieveStudyInfo rInfo) {
            String cuid = rInfo.getField(RetrieveStudyInfo.SOPCLASSUID);
            HashSet<String> iuids = sopClassMap.get(cuid);
            if (iuids == null) {
                iuids = new HashSet<>();
                sopClassMap.put(cuid, iuids);
            }
            iuids.add(rInfo.getField(RetrieveStudyInfo.SOPINSTANCEUID));
        }
    }

    private static class RetrieveStudyInfo {
        public static final int STUDYUID = 0;
        public static final int ACCESSION = 1;
        public static final int SOPCLASSUID = 2;
        public static final int SOPINSTANCEUID = 3;
        public static final int PATIENTID = 4;
        public static final int PATIENTNAME = 5;

        private final String[] fields;
        public RetrieveStudyInfo(Attributes attrs) {
            fields = new String[] {
                    attrs.getString(Tag.StudyInstanceUID),
                    attrs.getString(Tag.AccessionNumber),
                    attrs.getString(Tag.SOPClassUID),
                    attrs.getString(Tag.SOPInstanceUID),
                    attrs.getString(Tag.PatientID, "<none>"),
                    attrs.getString(Tag.PatientName, "<none>")
            };
        }
        public RetrieveStudyInfo(String s) {
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

    private static class RetrieveInfo {
        public static final int LOCALHOST = 0;
        public static final int LOCALAET = 1;
        public static final int DESTHOST = 2;
        public static final int DESTAET = 3;
        public static final int DESTNAPID = 4;
        public static final int DESTNAPCODE = 5;
        public static final int REQUESTORHOST = 6;
        public static final int MOVEAET = 7;
        public static final int OUTCOME = 8;

        private final String[] fields;

        public RetrieveInfo(RetrieveContext ctx) {
            String outcome = (null != ctx.getException()) ? ctx.getException().getMessage() : null;
            String destHost = (null != ctx.getDestinationHostName()) ? ctx.getDestinationHostName() : ctx.getDestinationAETitle();
            String destNapID = (null != ctx.getDestinationHostName()) ? ctx.getDestinationHostName() : null;
            String destNapCode = (null != ctx.getDestinationHostName()) ? AuditMessages.NetworkAccessPointTypeCode.IPAddress : null;
            fields = new String[] {
                    ctx.getLocalApplicationEntity().getDevice().getDeviceName(),
                    ctx.getLocalAETitle(),
                    destHost,
                    ctx.getDestinationAETitle(),
                    destNapID,
                    destNapCode,
                    ctx.getRequestorHostName(),
                    ctx.getMoveOriginatorAETitle(),
                    outcome
            };
        }

        public RetrieveInfo(String s) {
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

    private static class DeleteInfo {
        public static final int LOCALHOST = 0;
        public static final int LOCALAET = 1;
        public static final int REMOTEHOST = 2;
        public static final int REMOTEAET = 3;
        public static final int REMOTENAPID = 4;
        public static final int STUDYUID = 5;
        public static final int PATIENTID = 6;
        public static final int PATIENTNAME = 7;
        public static final int OUTCOME = 8;

        private final String[] fields;

        public DeleteInfo(StoreContext ctx) {
            String outcomeDesc = (ctx.getException() != null)
                    ? ctx.getRejectionNote().getRejectionNoteCode().getCodeMeaning() + " - " + ctx.getException().getMessage()
                    : ctx.getRejectionNote().getRejectionNoteCode().getCodeMeaning();
            fields = new String[]{
                    ctx.getStoreSession().getArchiveAEExtension().getApplicationEntity().getDevice().getDeviceName(),
                    ctx.getStoreSession().getCalledAET(),
                    ctx.getStoreSession().getRemoteHostName(),
                    ctx.getStoreSession().getCallingAET(),
                    ctx.getStoreSession().getRemoteHostName(),
                    ctx.getStudyInstanceUID(),
                    ctx.getAttributes().getString(Tag.PatientID, "<none>"),
                    ctx.getAttributes().getString(Tag.PatientID, "<none>"),
                    outcomeDesc
            };
        }

        public DeleteInfo(String s) {
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

    private static class DeleteStudyInfo {
        public static final int SOPCLASSUID = 0;
        public static final int NUMINSTANCES = 1;

        private final String[] fields;
        public DeleteStudyInfo(String cuid, String numInst) {
            ArrayList<String> list = new ArrayList<>();
            list.add(cuid);
            list.add(numInst);
            this.fields = list.toArray(new String[list.size()]);
        }
        public DeleteStudyInfo(String s) {
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


    private static class ConnectionRejectedInfo {
        public static final int REMOTE_ADDR = 0;
        public static final int LOCAL_ADDR = 1;
        public static final int PO_DESC = 2;
        public static final int OUTCOME_DESC = 3;
        private final String[] fields;

        public ConnectionRejectedInfo(Socket s, Throwable e) {
            fields = new String[] {
                    s.getRemoteSocketAddress().toString(),
                    s.getLocalSocketAddress().toString(),
                    e.getMessage(),
                    "MinorFailure"
            };
        }

        public ConnectionRejectedInfo(String s) {
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
}
