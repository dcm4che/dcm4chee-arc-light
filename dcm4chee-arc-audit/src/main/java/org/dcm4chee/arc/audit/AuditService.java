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
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.RejectionNote;
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
 * @since Feb 2016
 */
@ApplicationScoped
public class AuditService {
    private static final Logger LOG = LoggerFactory.getLogger(AuditService.class);
    private static final String tmpdir = doPrivileged(new GetPropertyAction("java.io.tmpdir"));

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
        AuditLogger log = log();
        Calendar timestamp = log.timeStamp();
        AuditMessage msg = new AuditMessage();
        EventIdentification ei = new EventIdentification();
        ei.setEventID(AuditMessages.EventID.ApplicationActivity);
        ei.getEventTypeCode().add(eventTypeCode);
        ei.setEventActionCode(AuditMessages.EventActionCode.Execute);
        ei.setEventOutcomeIndicator(AuditMessages.EventOutcomeIndicator.Success);
        ei.setEventDateTime(timestamp);
        msg.setEventIdentification(ei);
        ActiveParticipant apApplication = new ActiveParticipant();
        apApplication.getRoleIDCode().add(AuditMessages.RoleIDCode.Application);
        apApplication.setUserID(device.getDeviceName());
        StringBuilder aets = new StringBuilder();
        for (ApplicationEntity ae : device.getApplicationEntities()) {
            if (aets.length() == 0)
                aets.append("AETITLE=");
            else
                aets.append(';');
            aets.append(ae.getAETitle());
        }
        apApplication.setAlternativeUserID(aets.toString());
        apApplication.setUserIsRequestor(false);
        msg.getActiveParticipant().add(apApplication);

        if (request != null) {
            ActiveParticipant apUser = new ActiveParticipant();
            apUser.getRoleIDCode().add(AuditMessages.RoleIDCode.ApplicationLauncher);
            String remoteUser = request.getRemoteUser();
            apUser.setUserID(remoteUser != null ? remoteUser : request.getRemoteAddr());
            apUser.setNetworkAccessPointTypeCode(AuditMessages.NetworkAccessPointTypeCode.IPAddress);
            apUser.setNetworkAccessPointID(request.getRemoteAddr());
            apUser.setUserIsRequestor(true);
            msg.getActiveParticipant().add(apUser);
        }
        msg.getAuditSourceIdentification().add(log.createAuditSourceIdentification());
        emitAuditMessage(timestamp, msg);
    }

    public void auditInstancesStored(PatientStudyInfo patientStudyInfo, HashSet<String> accNos,
                                     HashSet<String> mppsUIDs, HashMap<String, List<String>> sopClassMap,
                                     Calendar eventTime) {
        Calendar timestamp = log().timeStamp();
        AuditMessage msg = new AuditMessage();
        EventIdentification ei = new EventIdentification();
        ei.setEventID(AuditMessages.EventID.DICOMInstancesTransferred);
        ei.setEventActionCode(AuditMessages.EventActionCode.Create);
        ActiveParticipant apSender = new ActiveParticipant();
        apSender.setUserID(patientStudyInfo.getField(PatientStudyInfo.REMOTE_HOSTNAME));
        apSender.setAlternativeUserID("AETITLE=" + patientStudyInfo.getField(PatientStudyInfo.CALLING_AET));
        apSender.setUserIsRequestor(true);
        apSender.getRoleIDCode().add(AuditMessages.RoleIDCode.Source);
        apSender.setNetworkAccessPointID(patientStudyInfo.getField(PatientStudyInfo.REMOTE_HOSTNAME));
        apSender.setNetworkAccessPointTypeCode(AuditMessages.NetworkAccessPointTypeCode.IPAddress);
        msg.getActiveParticipant().add(apSender);
        ActiveParticipant apReceiver = new ActiveParticipant();
        apReceiver.setUserID(device.getDeviceName());
        apReceiver.setAlternativeUserID("AETITLE=" + patientStudyInfo.getField(PatientStudyInfo.CALLED_AET));
        apReceiver.setUserIsRequestor(false);
        apReceiver.getRoleIDCode().add(AuditMessages.RoleIDCode.Destination);
        msg.getActiveParticipant().add(apReceiver);
        ei.setEventDateTime(eventTime);
        ei.setEventOutcomeIndicator(AuditMessages.EventOutcomeIndicator.Success);
        msg.setEventIdentification(ei);
        msg.getAuditSourceIdentification().add(log().createAuditSourceIdentification());
        ParticipantObjectIdentification poiStudy = new ParticipantObjectIdentification();
        poiStudy.setParticipantObjectTypeCode(AuditMessages.ParticipantObjectTypeCode.SystemObject);
        poiStudy.setParticipantObjectTypeCodeRole(AuditMessages.ParticipantObjectTypeCodeRole.Report);
        poiStudy.setParticipantObjectIDTypeCode(AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID);
        poiStudy.setParticipantObjectID(patientStudyInfo.getField(PatientStudyInfo.STUDY_UID));
        ParticipantObjectDescriptionType poiStudyDesc = new ParticipantObjectDescriptionType();
        for (String accNo : accNos)
            poiStudyDesc.getAccession().add(AuditMessages.createAccession(accNo));
        for (String mppsUID : mppsUIDs)
            poiStudyDesc.getMPPS().add(AuditMessages.createMPPS(mppsUID));
        for (Map.Entry<String, List<String>> entry : sopClassMap.entrySet())
            poiStudyDesc.getSOPClass().add(AuditMessages.createSOPClass(entry.getKey(), entry.getValue().size()));
        poiStudy.setParticipantObjectDescriptionType(poiStudyDesc);
        msg.getParticipantObjectIdentification().add(poiStudy);
        ParticipantObjectIdentification poiPatient = new ParticipantObjectIdentification();
        poiPatient.setParticipantObjectTypeCode(AuditMessages.ParticipantObjectTypeCode.Person);
        poiPatient.setParticipantObjectTypeCodeRole(AuditMessages.ParticipantObjectTypeCodeRole.Patient);
        poiPatient.setParticipantObjectIDTypeCode(AuditMessages.ParticipantObjectIDTypeCode.PatientNumber);
        poiPatient.setParticipantObjectID(
                StringUtils.maskEmpty(patientStudyInfo.getField(PatientStudyInfo.PATIENT_ID), "<none>"));
        poiPatient.setParticipantObjectName(
                StringUtils.maskEmpty(patientStudyInfo.getField(PatientStudyInfo.PATIENT_NAME), null));
        msg.getParticipantObjectIdentification().add(poiPatient);
        emitAuditMessage(timestamp, msg);
    }

    public void auditInstancesDeleted(StoreContext ctx) {
        AuditLogger log = log();
        Calendar timestamp = log.timeStamp();
        RejectionNote rn = ctx.getRejectionNote();
        Attributes attrs = ctx.getAttributes();
        AuditMessage msg = new AuditMessage();
        EventIdentification ei = new EventIdentification();
        ei.setEventID(AuditMessages.EventID.DICOMInstancesAccessed);
        ei.setEventActionCode(AuditMessages.EventActionCode.Delete);
        ei.setEventDateTime(timestamp);
        if (null != ctx.getException()) {
            ei.setEventOutcomeIndicator(AuditMessages.EventOutcomeIndicator.MinorFailure);
            ei.setEventOutcomeDescription(rn.getRejectionNoteCode().getCodeMeaning()
                    + " - " + ctx.getException().getMessage());
        } else {
            ei.setEventOutcomeIndicator(AuditMessages.EventOutcomeIndicator.Success);
            ei.setEventOutcomeDescription(rn.getRejectionNoteCode().getCodeMeaning());
        }
        msg.setEventIdentification(ei);
        ActiveParticipant ap = new ActiveParticipant();
        ap.setUserID(ctx.getStoreSession().getRemoteHostName());
        ap.setUserIsRequestor(true);
        msg.getActiveParticipant().add(ap);
        msg.getAuditSourceIdentification().add(log.createAuditSourceIdentification());
        ParticipantObjectIdentification poiStudy = new ParticipantObjectIdentification();
        poiStudy.setParticipantObjectTypeCode(AuditMessages.ParticipantObjectTypeCode.SystemObject);
        poiStudy.setParticipantObjectTypeCodeRole(AuditMessages.ParticipantObjectTypeCodeRole.Report);
        poiStudy.setParticipantObjectIDTypeCode(AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID);
        poiStudy.setParticipantObjectID(ctx.getStudyInstanceUID());
        ParticipantObjectDescriptionType poiStudyDesc = new ParticipantObjectDescriptionType();
        Sequence currentRequestedProcedureEvidenceSequences =
                attrs.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence);
        if (currentRequestedProcedureEvidenceSequences != null) {
            for (Attributes currentRequestedProcedureEvidenceSequence : currentRequestedProcedureEvidenceSequences ) {
                Sequence referencedSeriesSequences =
                        currentRequestedProcedureEvidenceSequence.getSequence(Tag.ReferencedSeriesSequence);
                if (referencedSeriesSequences != null) {
                    for (Attributes referencedSeriesSequence : referencedSeriesSequences) {
                        Sequence referencedSOPSequences =
                                referencedSeriesSequence.getSequence(Tag.ReferencedSOPSequence);
                        if (referencedSOPSequences != null) {
                            for (Attributes referencedSOPSequence : referencedSOPSequences) {
                                poiStudyDesc.getSOPClass().add((AuditMessages.createSOPClass(
                                        referencedSOPSequence.getString(Tag.ReferencedSOPClassUID), referencedSOPSequences.size())));
                            }
                        }
                    }
                }
            }
        }
        poiStudy.setParticipantObjectDescriptionType(poiStudyDesc);
        msg.getParticipantObjectIdentification().add(poiStudy);
        ParticipantObjectIdentification poiPatient = new ParticipantObjectIdentification();
        poiPatient.setParticipantObjectTypeCode(AuditMessages.ParticipantObjectTypeCode.Person);
        poiPatient.setParticipantObjectTypeCodeRole(AuditMessages.ParticipantObjectTypeCodeRole.Patient);
        poiPatient.setParticipantObjectIDTypeCode(AuditMessages.ParticipantObjectIDTypeCode.PatientNumber);
        poiPatient.setParticipantObjectID(attrs.getString(Tag.PatientID, ""));
        poiPatient.setParticipantObjectName(attrs.getString(Tag.PatientName, ""));
        msg.getParticipantObjectIdentification().add(poiPatient);
        emitAuditMessage(timestamp, msg);
    }

    public void auditConnectionRejected(Socket s, Throwable e) {
        AuditLogger log = log();
        Calendar timestamp = log.timeStamp();
        AuditMessage msg = new AuditMessage();
        EventIdentification ei = new EventIdentification();
        ei.setEventID(AuditMessages.EventID.SecurityAlert);
        ei.setEventActionCode(AuditMessages.EventActionCode.Execute);
        ei.setEventDateTime(timestamp);
        ei.setEventOutcomeIndicator(AuditMessages.EventOutcomeIndicator.MinorFailure);
        ei.getEventTypeCode().add(AuditMessages.EventTypeCode.NodeAuthentication);
        msg.setEventIdentification(ei);
        ActiveParticipant apReporter = new ActiveParticipant();
        apReporter.setUserID(s.getLocalSocketAddress().toString());
        apReporter.setUserIsRequestor(false);
        msg.getActiveParticipant().add(apReporter);
        ActiveParticipant apPerformer = new ActiveParticipant();
        apPerformer.setUserID(s.getRemoteSocketAddress().toString());
        apPerformer.setUserIsRequestor(true);
        msg.getActiveParticipant().add(apPerformer);
        msg.getAuditSourceIdentification().add(log.createAuditSourceIdentification());
        ParticipantObjectIdentification poi = new ParticipantObjectIdentification();
        poi.setParticipantObjectTypeCode(AuditMessages.ParticipantObjectTypeCode.SystemObject);
        poi.setParticipantObjectIDTypeCode(AuditMessages.ParticipantObjectIDTypeCode.NodeID);
        poi.setParticipantObjectID(s.getRemoteSocketAddress().toString());
        poi.setParticipantObjectDescription(e.getMessage());
        msg.getParticipantObjectIdentification().add(poi);
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

    public void aggregateAuditMessage(Path path) {
        PatientStudyInfo patientStudyInfo;
        HashSet<String> accNos = new HashSet<>();
        HashSet<String> mppsUIDs = new HashSet<>();
        HashMap<String, List<String>> sopClassMap = new HashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            patientStudyInfo = new PatientStudyInfo(reader.readLine());
            accNos.add(patientStudyInfo.getField(PatientStudyInfo.ACCESSION_NO));
            String line;
            while ((line = reader.readLine()) != null) {
                InstanceInfo instanceInfo = new InstanceInfo(line);
                List<String> iuids = sopClassMap.get(instanceInfo.getField(InstanceInfo.CLASS_UID));
                if (iuids == null)
                    sopClassMap.put(instanceInfo.getField(InstanceInfo.CLASS_UID), iuids = new ArrayList<>());
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
        auditInstancesStored(patientStudyInfo, accNos, mppsUIDs, sopClassMap, eventTime);
        try {
            Files.delete(path);
        } catch (IOException e) {
            LOG.warn("Failed to delete Audit Spool File - {}", path, e);
        }
    }

    private static class PatientStudyInfo {
        public static final int REMOTE_HOSTNAME = 0;
        public static final int CALLING_AET = 1;
        public static final int CALLED_AET = 2;
        public static final int STUDY_UID = 3;
        public static final int ACCESSION_NO = 4;
        public static final int PATIENT_ID = 5;
        public static final int PATIENT_NAME = 6;

        private final String[] fields;

        public PatientStudyInfo(StoreContext ctx, Attributes attrs) {
            StoreSession session = ctx.getStoreSession();
            fields = new String[] {
                    session.getRemoteHostName(),
                    session.getCallingAET(),
                    session.getCalledAET(),
                    ctx.getStudyInstanceUID(),
                    attrs.getString(Tag.AccessionNumber, ""),
                    attrs.getString(Tag.PatientID, ""),
                    attrs.getString(Tag.PatientName, "")
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
}
