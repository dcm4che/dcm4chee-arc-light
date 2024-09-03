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
import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.IApplicationEntityCache;
import org.dcm4che3.conf.api.IWebApplicationCache;
import org.dcm4che3.conf.api.hl7.IHL7ApplicationCache;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.net.*;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.HL7DeviceExtension;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4che3.util.ReverseDNS;
import org.dcm4chee.arc.HL7ConnectionEvent;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.hl7.ArchiveHL7Message;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.file.Path;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.List;


/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2018
 */
class PatientRecordAuditService extends AuditService {

    private final static Logger LOG = LoggerFactory.getLogger(PatientRecordAuditService.class);

    private final static String PID_SEGMENT = "PID";
    private final static int PID_SEGMENT_PATIENT_ID = 3;
    private final static int PID_SEGMENT_PATIENT_NAME = 5;
    private final static String MRG_SEGMENT = "MRG";
    private final static int MRG_SEGMENT_PATIENT_ID = 1;
    private final static int MRG_SEGMENT_PATIENT_NAME = 7;

    private static String callingUserID(PatientMgtContext ctx, ArchiveDeviceExtension arcDev) {
        HttpServletRequestInfo httpServletRequestInfo = ctx.getHttpServletRequestInfo();
        Association association = ctx.getAssociation();
        UnparsedHL7Message hl7Message = ctx.getUnparsedHL7Message();
        if (httpServletRequestInfo != null)
            return httpServletRequestInfo.requesterUserID;

        if (association != null)
            return association.getCallingAET();

        if (hl7Message != null)
            return hl7Message.msh().getSendingApplicationWithFacility();

        return arcDev.getDevice().getDeviceName();
    }

    private static String calledUserID(PatientMgtContext ctx) {
        HttpServletRequestInfo httpServletRequestInfo = ctx.getHttpServletRequestInfo();
        Association association = ctx.getAssociation();
        UnparsedHL7Message hl7Message = ctx.getUnparsedHL7Message();
        if (httpServletRequestInfo != null)
            return httpServletRequestInfo.requestURIWithQueryStr();

        if (association != null)
            return association.getCalledAET();

        if (hl7Message != null)
            return hl7Message.msh().getReceivingApplicationWithFacility();

        return ctx.getLocalAET();
    }

    static AuditInfo patientAuditInfo(PatientMgtContext ctx, ArchiveDeviceExtension arcDev) {
        return new AuditInfoBuilder.Builder()
                    .callingUserID(callingUserID(ctx, arcDev))
                    .callingHost(ctx.getRemoteHostName())
                    .calledUserID(calledUserID(ctx))
                    .patVerificationStatus(ctx.getPatientVerificationStatus())
                    .pdqServiceURI(ctx.getPDQServiceURI())
                    .findSCP(ctx.getSourceMwlScp())
                    .destUserID(ctx.getLocalAET())
                    .outcome(ctx.getException() == null ? null : ctx.getException().getMessage())
                    .pIDAndName(ctx.getAttributes(), arcDev)
                    .toAuditInfo();
    }

    static AuditInfo prevPatientAuditInfo(PatientMgtContext ctx, ArchiveDeviceExtension arcDev) {
        return new AuditInfoBuilder.Builder()
                    .callingUserID(callingUserID(ctx, arcDev))
                    .callingHost(ctx.getRemoteHostName())
                    .calledUserID(calledUserID(ctx))
                    .patVerificationStatus(ctx.getPatientVerificationStatus())
                    .pdqServiceURI(ctx.getPDQServiceURI())
                    .findSCP(ctx.getSourceMwlScp())
                    .destUserID(ctx.getLocalAET())
                    .outcome(ctx.getException() == null ? null : ctx.getException().getMessage())
                    .pIDAndName(ctx.getPreviousAttributes(), arcDev)
                    .toAuditInfo();
    }

    static AuditInfo patientAuditInfoHL7(HL7ConnectionEvent hl7ConnEvent, ArchiveDeviceExtension arcDev) {
        UnparsedHL7Message hl7Message = hl7ConnEvent.getHL7Message();
        Connection connection = hl7ConnEvent.getConnection();
        HL7Segment msh = hl7Message.msh();
        HL7Segment pid = HL7AuditUtils.getHL7Segment(hl7ConnEvent.getHL7Message(), PID_SEGMENT);
        return new AuditInfoBuilder.Builder()
                .callingUserID(msh.getSendingApplicationWithFacility())
                .callingHost(connection == null
                                ? ReverseDNS.hostNameOf(hl7ConnEvent.getSocket().getInetAddress())
                                : connection.getHostname())
                .calledUserID(msh.getReceivingApplicationWithFacility())
                .outcome(hl7ConnEvent.getException() == null ? null : hl7ConnEvent.getException().getMessage())
                .patID(pid.getField(PID_SEGMENT_PATIENT_ID, null), arcDev)
                .patName(pid.getField(PID_SEGMENT_PATIENT_NAME, null), arcDev)
                .toAuditInfo();
    }

    static AuditInfo prevPatientAuditInfoHL7(HL7ConnectionEvent hl7ConnEvent, ArchiveDeviceExtension arcDev) {
        UnparsedHL7Message hl7Message = hl7ConnEvent.getHL7Message();
        Connection connection = hl7ConnEvent.getConnection();
        HL7Segment msh = hl7Message.msh();
        HL7Segment mrg = HL7AuditUtils.getHL7Segment(hl7ConnEvent.getHL7Message(), MRG_SEGMENT);
        return new AuditInfoBuilder.Builder()
                .callingUserID(msh.getSendingApplicationWithFacility())
                .callingHost(connection == null
                                ? ReverseDNS.hostNameOf(hl7ConnEvent.getSocket().getInetAddress())
                                : connection.getHostname())
                .calledUserID(msh.getReceivingApplicationWithFacility())
                .outcome(hl7ConnEvent.getException() == null ? null : hl7ConnEvent.getException().getMessage())
                .patID(mrg.getField(MRG_SEGMENT_PATIENT_ID, null), arcDev)
                .patName(mrg.getField(MRG_SEGMENT_PATIENT_NAME, null), arcDev)
                .toAuditInfo();
    }

    //patient ADTs outgoing on : forwarding / notify HL7 receivers on PAM RS / HL7 RS

    private static String callingUserIDOutgoingHL7(HL7ConnectionEvent hl7ConnEvent) {
        UnparsedHL7Message hl7Message = hl7ConnEvent.getHL7Message();
        HL7Segment msh = hl7Message.msh();
        String messageType = msh.getMessageType();
        if (messageType.startsWith(AuditUtils.APPOINTMENTS))
            return msh.getSendingApplicationWithFacility();

        ArchiveHL7Message archiveHL7Message = (ArchiveHL7Message) hl7Message;
        HttpServletRequestInfo httpServletRequestInfo = archiveHL7Message.getHttpServletRequestInfo();
        if (httpServletRequestInfo != null)
            return httpServletRequestInfo.requesterUserID;

        return msh.getSendingApplicationWithFacility();
    }

    private static String callingHostOutgoingHL7(HL7ConnectionEvent hl7ConnEvent) {
        Connection connection = hl7ConnEvent.getConnection();
        String callingHost = connection == null
                                ? ReverseDNS.hostNameOf(hl7ConnEvent.getSocket().getInetAddress())
                                : connection.getHostname();
        UnparsedHL7Message hl7Message = hl7ConnEvent.getHL7Message();
        HL7Segment msh = hl7Message.msh();
        String messageType = msh.getMessageType();
        if (messageType.startsWith(AuditUtils.APPOINTMENTS))
            return callingHost;

        ArchiveHL7Message archiveHL7Message = (ArchiveHL7Message) hl7Message;
        HttpServletRequestInfo httpServletRequestInfo = archiveHL7Message.getHttpServletRequestInfo();
        if (httpServletRequestInfo != null)
            return httpServletRequestInfo.requesterHost;

        return callingHost;
    }

    private static String calledUserIDOutgoingHL7(HL7ConnectionEvent hl7ConnEvent) {
        UnparsedHL7Message hl7Message = hl7ConnEvent.getHL7Message();
        HL7Segment msh = hl7Message.msh();
        String messageType = msh.getMessageType();
        if (messageType.startsWith(AuditUtils.APPOINTMENTS))
            return msh.getReceivingApplicationWithFacility();

        ArchiveHL7Message archiveHL7Message = (ArchiveHL7Message) hl7Message;
        HttpServletRequestInfo httpServletRequestInfo = archiveHL7Message.getHttpServletRequestInfo();
        if (httpServletRequestInfo != null)
            return httpServletRequestInfo.requestURIWithQueryStr();

        return msh.getReceivingApplicationWithFacility();
    }

    static AuditInfo patientAuditInfoHL7Outgoing(HL7ConnectionEvent hl7ConnEvent, ArchiveDeviceExtension arcDev) {
        UnparsedHL7Message hl7Message = hl7ConnEvent.getHL7Message();
        HL7Segment msh = hl7Message.msh();
        HL7Segment pid = HL7AuditUtils.getHL7Segment(hl7ConnEvent.getHL7Message(), PID_SEGMENT);
        return new AuditInfoBuilder.Builder()
                .callingUserID(callingUserIDOutgoingHL7(hl7ConnEvent))
                .callingHost(callingHostOutgoingHL7(hl7ConnEvent))
                .calledUserID(calledUserIDOutgoingHL7(hl7ConnEvent))
                .outcome(hl7ConnEvent.getException() == null ? null : hl7ConnEvent.getException().getMessage())
                .patID(pid.getField(PID_SEGMENT_PATIENT_ID, null), arcDev)
                .patName(pid.getField(PID_SEGMENT_PATIENT_NAME, null), arcDev)
                .outgoingHL7Sender(msh.getSendingApplicationWithFacility())
                .outgoingHL7Receiver(msh.getReceivingApplicationWithFacility())
                .toAuditInfo();
    }

    static AuditInfo prevPatientAuditInfoHL7Outgoing(HL7ConnectionEvent hl7ConnEvent, ArchiveDeviceExtension arcDev) {
        UnparsedHL7Message hl7Message = hl7ConnEvent.getHL7Message();
        HL7Segment msh = hl7Message.msh();
        HL7Segment mrg = HL7AuditUtils.getHL7Segment(hl7ConnEvent.getHL7Message(), MRG_SEGMENT);
        return new AuditInfoBuilder.Builder()
                .callingUserID(callingUserIDOutgoingHL7(hl7ConnEvent))
                .callingHost(callingHostOutgoingHL7(hl7ConnEvent))
                .calledUserID(calledUserIDOutgoingHL7(hl7ConnEvent))
                .outcome(hl7ConnEvent.getException() == null ? null : hl7ConnEvent.getException().getMessage())
                .patID(mrg.getField(MRG_SEGMENT_PATIENT_ID, null), arcDev)
                .patName(mrg.getField(MRG_SEGMENT_PATIENT_NAME, null), arcDev)
                .outgoingHL7Sender(msh.getSendingApplicationWithFacility())
                .outgoingHL7Receiver(msh.getReceivingApplicationWithFacility())
                .toAuditInfo();
    }

    static void audit(
            AuditLogger auditLogger, Path path, AuditUtils.EventType eventType, Device device,
            IApplicationEntityCache aeCache, IHL7ApplicationCache hl7AppCache, IWebApplicationCache webAppCache)
            throws ConfigurationException {
        SpoolFileReader reader = new SpoolFileReader(path.toFile());
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        EventIdentification eventIdentification = getEventIdentification(auditInfo, eventType);
        eventIdentification.setEventDateTime(getEventTime(path, auditLogger));
        List<ActiveParticipant> activeParticipants = activeParticipants(
                auditInfo, eventType, auditLogger, device, aeCache, hl7AppCache, webAppCache);
        emitAuditMessage(auditLogger, eventIdentification, activeParticipants, patient(auditInfo, reader));
    }

    private static List<ActiveParticipant> activeParticipants(
            AuditInfo auditInfo, AuditUtils.EventType eventType, AuditLogger auditLogger, Device device,
            IApplicationEntityCache aeCache, IHL7ApplicationCache hl7AppCache, IWebApplicationCache webAppCache) {
        List<ActiveParticipant> activeParticipants = new ArrayList<>();
        String calledUserID = auditInfo.getField(AuditInfo.CALLED_USERID);
        if (calledUserID == null) {
            activeParticipants.add(archiveRequestor(auditInfo, eventType, auditLogger));
            if (auditInfo.getField(AuditInfo.PDQ_SERVICE_URI) != null)
                activeParticipants.add(pdqService(auditInfo, eventType, aeCache, hl7AppCache, webAppCache));
        } else if (calledUserID.contains("/")) {
            activeParticipants.add(archiveURI(auditInfo, eventType, auditLogger));
            if (auditInfo.getField(AuditInfo.PDQ_SERVICE_URI) != null) {
                activeParticipants.add(requestor(auditInfo, null));
                activeParticipants.add(pdqService(auditInfo, eventType, aeCache, hl7AppCache, webAppCache));
            } else
                activeParticipants.add(requestor(auditInfo, eventType.source));
        } else if (calledUserID.contains("|")) {
            activeParticipants.add(hl7Application(auditInfo, eventType.source));
            HL7Application hl7Receiver = findHL7Application(calledUserID, device);
            if (hl7Receiver != null)
                activeParticipants.add(hl7Application(hl7Receiver, calledUserID, eventType.destination));
        } else {
            activeParticipants.add(remoteAE(auditInfo, eventType));
            activeParticipants.add(archiveAE(auditInfo, eventType, auditLogger));
        }

        if (addOutgoingHL7ActiveParticipants(auditInfo))
            addOutgoingHL7ActiveParticipants(activeParticipants, auditInfo, eventType, device, hl7AppCache);

        ApplicationEntity findSCPAE = findApplicationEntity(auditInfo.getField(AuditInfo.FIND_SCP), aeCache);
        if (findSCPAE != null)
            activeParticipants.add(findSCP(findSCPAE, eventType));
        return activeParticipants;
    }

    private static boolean addOutgoingHL7ActiveParticipants(AuditInfo auditInfo) {
        String calledUserID = auditInfo.getField(AuditInfo.CALLED_USERID);
        return calledUserID != null
                && !auditInfo.getField(AuditInfo.CALLING_USERID).equals(auditInfo.getField(AuditInfo.OUTGOING_HL7_SENDER))
                && !calledUserID.equals(auditInfo.getField(AuditInfo.OUTGOING_HL7_RECEIVER));
    }

    private static void addOutgoingHL7ActiveParticipants(
            List<ActiveParticipant> activeParticipants, AuditInfo auditInfo, AuditUtils.EventType eventType,
            Device device, IHL7ApplicationCache hl7AppCache) {
        String outgoingHL7SenderAppFacility = auditInfo.getField(AuditInfo.OUTGOING_HL7_SENDER);
        if (outgoingHL7SenderAppFacility == null)
            return;

        HL7Application outgoingHL7Sender = findHL7Application(outgoingHL7SenderAppFacility, device);
        HL7Application outgoingHL7Receiver = findHL7Application(auditInfo.getField(AuditInfo.OUTGOING_HL7_RECEIVER), hl7AppCache);
        if (outgoingHL7Sender != null)
            activeParticipants.add(hl7Application(outgoingHL7Sender, eventType.source, activeParticipants.isEmpty()));
        if (outgoingHL7Receiver != null)
            activeParticipants.add(hl7Application(outgoingHL7Receiver, eventType.destination, false));
    }

    private static ParticipantObjectIdentification patient(AuditInfo auditInfo, SpoolFileReader reader) {
        ParticipantObjectIdentification patient = new ParticipantObjectIdentification();
        patient.setParticipantObjectID(auditInfo.getField(AuditInfo.P_ID));
        patient.setParticipantObjectIDTypeCode(AuditMessages.ParticipantObjectIDTypeCode.PatientNumber);
        patient.setParticipantObjectTypeCode(AuditMessages.ParticipantObjectTypeCode.Person);
        patient.setParticipantObjectTypeCodeRole(AuditMessages.ParticipantObjectTypeCodeRole.Patient);
        patient.setParticipantObjectName(auditInfo.getField(AuditInfo.P_NAME));

        List<ParticipantObjectDetail> hl7ParticipantObjectDetails = hl7ParticipantObjectDetails(reader);
        if (!hl7ParticipantObjectDetails.isEmpty())
            patient.getParticipantObjectDetail().addAll(hl7ParticipantObjectDetails);

        Patient.VerificationStatus patientVerificationStatus = patientVerificationStatus(auditInfo);
        if (patientVerificationStatus != Patient.VerificationStatus.UNVERIFIED) {
            patient.setParticipantObjectDataLifeCycle(AuditMessages.ParticipantObjectDataLifeCycle.Verification);
            patient.getParticipantObjectDetail()
                   .add(AuditMessages.createParticipantObjectDetail(
                           "PatientVerificationStatus", patientVerificationStatus.name()));
        }
        return patient;
    }

    private static List<ParticipantObjectDetail> hl7ParticipantObjectDetails(SpoolFileReader reader) {
        List<ParticipantObjectDetail> detail = new ArrayList<>();
        hl7ParticipantObjectDetail(detail, reader.getData());
        hl7ParticipantObjectDetail(detail, reader.getAck());
        return detail;
    }

    private static void hl7ParticipantObjectDetail(List<ParticipantObjectDetail> detail, byte[] data) {
        if (data.length > 0) {
            HL7Segment msh = HL7Segment.parseMSH(data, data.length, new ParsePosition(0));
            String messageType = msh.getMessageType();
            String messageControlID = msh.getMessageControlID();
            detail.add(hl7ParticipantObjectDetail("HL7v2 Message", data));
            if (messageType != null)
                detail.add(hl7ParticipantObjectDetail("MSH-9", messageType.getBytes()));
            if (messageControlID != null)
                detail.add(hl7ParticipantObjectDetail("MSH-10", messageControlID.getBytes()));
        }
    }

    private static ParticipantObjectDetail hl7ParticipantObjectDetail(String key, byte[] val) {
        ParticipantObjectDetail detail = new ParticipantObjectDetail();
        detail.setType(key);
        detail.setValue(val);
        return detail;
    }

    private static EventIdentification getEventIdentification(AuditInfo auditInfo, AuditUtils.EventType eventType) {
        Patient.VerificationStatus patientVerificationStatus = patientVerificationStatus(auditInfo);
        String outcome = auditInfo.getField(AuditInfo.OUTCOME);
        EventIdentification ei = new EventIdentification();
        ei.setEventID(eventType.eventID);
        ei.setEventActionCode(eventType.eventActionCode);
        ei.setEventOutcomeDescription(outcomeDescription(patientVerificationStatus, outcome));
        ei.setEventOutcomeIndicator(outcomeIndicator(patientVerificationStatus, outcome));
        return ei;
    }

    private static String outcomeIndicator(Patient.VerificationStatus patientVerificationStatus, String outcome) {
        return patientVerificationStatus == Patient.VerificationStatus.VERIFICATION_FAILED
                ? AuditMessages.EventOutcomeIndicator.SeriousFailure
                : patientVerificationStatus == Patient.VerificationStatus.NOT_FOUND || outcome != null
                        ? AuditMessages.EventOutcomeIndicator.MinorFailure
                        : AuditMessages.EventOutcomeIndicator.Success;
    }

    private static String outcomeDescription(Patient.VerificationStatus patientVerificationStatus, String outcome) {
        return patientVerificationStatus == Patient.VerificationStatus.UNVERIFIED
                 ? outcome
                 : outcome == null
                    ? patientVerificationStatus.name()
                    : patientVerificationStatus.name() + "\n" + outcome;
    }

    private static Patient.VerificationStatus patientVerificationStatus(AuditInfo auditInfo) {
        String patientVerificationStatus = auditInfo.getField(AuditInfo.PAT_VERIFICATION_STATUS);
        return patientVerificationStatus == null
                    ? Patient.VerificationStatus.UNVERIFIED
                    : Patient.VerificationStatus.valueOf(patientVerificationStatus);
    }

    private static ActiveParticipant hl7Application(AuditInfo auditInfo, RoleIDCode roleIDCode) {
        ActiveParticipant hl7Application = new ActiveParticipant();
        hl7Application.setUserID(auditInfo.getField(AuditInfo.CALLING_USERID));
        hl7Application.setUserIDTypeCode(AuditMessages.UserIDTypeCode.ApplicationFacility);
        hl7Application.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        String hl7AppFacilityHost = auditInfo.getField(AuditInfo.CALLING_HOST);
        hl7Application.setNetworkAccessPointID(hl7AppFacilityHost);
        hl7Application.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(hl7AppFacilityHost)
                        ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                        : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        hl7Application.getRoleIDCode().add(roleIDCode);
        hl7Application.setUserIsRequestor(true);
        return hl7Application;
    }

    private static ActiveParticipant hl7Application(HL7Application hl7App, String defVal, RoleIDCode roleIDCode) {
        ActiveParticipant hl7Application = new ActiveParticipant();
        String appFacility = hl7App.getApplicationName();
        hl7Application.setUserID(appFacility.equals("*") ? defVal : appFacility);
        hl7Application.setUserIDTypeCode(AuditMessages.UserIDTypeCode.ApplicationFacility);
        hl7Application.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        String hl7AppFacilityHost = hl7App.getConnections().get(0).getHostname();
        hl7Application.setNetworkAccessPointID(hl7AppFacilityHost);
        hl7Application.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(hl7AppFacilityHost)
                        ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                        : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        hl7Application.getRoleIDCode().add(roleIDCode);
        return hl7Application;
    }

    private static ActiveParticipant hl7Application(HL7Application hl7App, RoleIDCode roleIDCode, boolean requestor) {
        ActiveParticipant hl7Application = new ActiveParticipant();
        hl7Application.setUserID(hl7App.getApplicationName());
        hl7Application.setUserIDTypeCode(AuditMessages.UserIDTypeCode.ApplicationFacility);
        hl7Application.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        String hl7AppFacilityHost = hl7App.getConnections().get(0).getHostname();
        hl7Application.setNetworkAccessPointID(hl7AppFacilityHost);
        hl7Application.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(hl7AppFacilityHost)
                    ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                    : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        hl7Application.getRoleIDCode().add(roleIDCode);
        hl7Application.setUserIsRequestor(requestor);
        return hl7Application;
    }

    private static ActiveParticipant archiveRequestor(
            AuditInfo auditInfo, AuditUtils.EventType eventType, AuditLogger auditLogger) {
        ActiveParticipant archiveRequestor = new ActiveParticipant();
        archiveRequestor.setUserID(auditInfo.getField(AuditInfo.CALLING_USERID));
        archiveRequestor.setUserIDTypeCode(AuditMessages.UserIDTypeCode.DeviceName);
        archiveRequestor.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        archiveRequestor.setAlternativeUserID(AuditLogger.processID());
        String archiveRequestorHost = auditLogger.getConnections().get(0).getHostname();
        archiveRequestor.setNetworkAccessPointID(archiveRequestorHost);
        archiveRequestor.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(archiveRequestorHost)
                        ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                        : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        archiveRequestor.setUserIsRequestor(true);
        if (eventType.destination != null)
            archiveRequestor.getRoleIDCode().add(eventType.destination);
        return archiveRequestor;
    }

    private static ActiveParticipant requestor(AuditInfo auditInfo, RoleIDCode roleIDCode) {
        ActiveParticipant requestor = new ActiveParticipant();
        String requestorUserID = auditInfo.getField(AuditInfo.CALLING_USERID);
        requestor.setUserID(requestorUserID);
        boolean requestorUserIsIP = AuditMessages.isIP(requestorUserID);
        requestor.setUserIDTypeCode(
                requestorUserIsIP
                        ? AuditMessages.UserIDTypeCode.NodeID
                        : AuditMessages.UserIDTypeCode.PersonID);
        requestor.setUserTypeCode(
                requestorUserIsIP
                        ? AuditMessages.UserTypeCode.Application
                        : AuditMessages.UserTypeCode.Person);
        String requestorHost = auditInfo.getField(AuditInfo.CALLING_HOST);
        requestor.setNetworkAccessPointID(requestorHost);
        requestor.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(requestorHost)
                        ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                        : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        requestor.setUserIsRequestor(true);
        if (roleIDCode != null)
            requestor.getRoleIDCode().add(roleIDCode);
        return requestor;
    }

    private static ActiveParticipant archiveURI(AuditInfo auditInfo, AuditUtils.EventType eventType, AuditLogger auditLogger) {
        ActiveParticipant archiveURI = new ActiveParticipant();
        String archiveReqURI = auditInfo.getField(AuditInfo.CALLED_USERID);
        archiveURI.setUserID(archiveReqURI);
        archiveURI.setUserIDTypeCode(AuditMessages.UserIDTypeCode.URI);
        archiveURI.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        archiveURI.setAlternativeUserID(AuditLogger.processID());
        String archiveURIHost = auditLogger.getConnections().get(0).getHostname();
        archiveURI.setNetworkAccessPointID(archiveURIHost);
        archiveURI.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(archiveURIHost)
                        ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                        : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        archiveURI.getRoleIDCode().add(eventType.destination);
        return archiveURI;
    }

    private static ActiveParticipant remoteAE(AuditInfo auditInfo, AuditUtils.EventType eventType) {
        ActiveParticipant remoteAE = new ActiveParticipant();
        remoteAE.setUserID(auditInfo.getField(AuditInfo.CALLING_USERID));
        remoteAE.setUserIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle);
        remoteAE.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        String remoteAEHost = auditInfo.getField(AuditInfo.CALLING_HOST);
        remoteAE.setNetworkAccessPointID(remoteAEHost);
        remoteAE.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(remoteAEHost)
                        ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                        : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        remoteAE.setUserIsRequestor(true);
        remoteAE.getRoleIDCode().add(eventType.source);
        return remoteAE;
    }

    private static ActiveParticipant archiveAE(
            AuditInfo auditInfo, AuditUtils.EventType eventType, AuditLogger auditLogger) {
        ActiveParticipant archiveAE = new ActiveParticipant();
        archiveAE.setUserID(auditInfo.getField(AuditInfo.CALLED_USERID));
        archiveAE.setUserIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle);
        archiveAE.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        archiveAE.setAlternativeUserID(AuditLogger.processID());
        String archiveAEHost = auditLogger.getConnections().get(0).getHostname();
        archiveAE.setNetworkAccessPointID(archiveAEHost);
        archiveAE.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(archiveAEHost)
                        ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                        : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        archiveAE.getRoleIDCode().add(eventType.destination);
        return archiveAE;
    }

    private static ActiveParticipant findSCP(
            ApplicationEntity findSCPAE, AuditUtils.EventType eventType) {
        ActiveParticipant findSCP = new ActiveParticipant();
        findSCP.setUserID(findSCPAE.getAETitle());
        findSCP.setUserIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle);
        findSCP.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        String findSCPHost = findSCPAE.getConnections().get(0).getHostname();;
        findSCP.setNetworkAccessPointID(findSCPHost);
        findSCP.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(findSCPHost)
                        ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                        : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        findSCP.getRoleIDCode().add(eventType.source);
        return findSCP;
    }

    private static ActiveParticipant pdqService(
            AuditInfo auditInfo, AuditUtils.EventType eventType, IApplicationEntityCache aeCache,
            IHL7ApplicationCache hl7AppCache, IWebApplicationCache webAppCache) {
        String pdqServiceURIStr = auditInfo.getField(AuditInfo.PDQ_SERVICE_URI);
        ActiveParticipant pdqService = new ActiveParticipant();
        pdqService.setUserID(pdqServiceURIStr);
        pdqService.setUserIDTypeCode(AuditMessages.UserIDTypeCode.URI);
        pdqService.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        String pdqServiceHost = pdqServiceHost(pdqServiceURIStr, aeCache, hl7AppCache, webAppCache);
        pdqService.setNetworkAccessPointID(pdqServiceHost);
        pdqService.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(pdqServiceHost)
                    ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                    : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        pdqService.getRoleIDCode().add(eventType.source);
        return pdqService;
    }

    private static String pdqServiceHost(
            String pdqServiceURIStr, IApplicationEntityCache aeCache, IHL7ApplicationCache hl7AppCache,
            IWebApplicationCache webAppCache) {
        URI pdqServiceURI = URI.create(pdqServiceURIStr);
        String scheme = pdqServiceURI.getScheme();
        String schemeSpecificPart = pdqServiceURI.getSchemeSpecificPart();
        switch (scheme) {
            case AuditUtils.PDQ_DICOM:
                return pdqDICOMHost(findApplicationEntity(schemeSpecificPart, aeCache));
            case AuditUtils.PDQ_HL7:
                return pdqHL7ReceiverAppHost(pdqServiceURI, hl7AppCache);
            case AuditUtils.PDQ_FHIR:
                return pdqFHIRWebAppHost(findWebApplication(schemeSpecificPart, webAppCache));
            default:
                return null;
        }
    }

    static ApplicationEntity findApplicationEntity(String aet, IApplicationEntityCache aeCache) {
        if (aet == null)
            return null;

        try {
            return aeCache.findApplicationEntity(aet);
        } catch (ConfigurationException e) {
            LOG.info("Skip adding Application Entity in patient record audit active participants - {}", e.getMessage());
        }
        return null;
    }

    private static HL7Application findHL7Application(String appFacility, IHL7ApplicationCache hl7AppCache) {
        try {
            return hl7AppCache.findHL7Application(appFacility);
        } catch (ConfigurationException e) {
            LOG.info("Skip adding HL7 Application in patient record audit active participants - {}", e.getMessage());
        }
        return null;
    }

    private static HL7Application findHL7Application(String appFacility, Device device) {
        HL7Application hl7Application = device.getDeviceExtension(HL7DeviceExtension.class)
                                               .getHL7Application(appFacility, true);
        if (hl7Application == null)
            LOG.info("Skip adding HL7 Application in patient record audit active participants - {}", appFacility);

        return hl7Application;
    }

    private static WebApplication findWebApplication(String webAppName, IWebApplicationCache webAppCache) {
        try {
            return webAppCache.findWebApplication(webAppName);
        } catch (ConfigurationException e) {
            LOG.info("Skip adding Web Application in patient record audit active participants - {}", e.getMessage());
        }
        return null;
    }

    private static String pdqDICOMHost(ApplicationEntity ae) {
        return ae == null ? null : ae.getConnections().get(0).getHostname();
    }

    private static String pdqHL7ReceiverAppHost(URI pdqServiceURI, IHL7ApplicationCache hl7AppCache) {
        String msh3456 = pdqServiceURI.getSchemeSpecificPart();
        String[] appFacility = msh3456.split(":");
        if (appFacility.length != 2) {
            LOG.info("Sending and/or Receiving application and facility not specified in PDQ Service URI {}", pdqServiceURI);
            return null;
        }
        HL7Application hl7Application = findHL7Application(appFacility[1].replace("/","|"), hl7AppCache);
        return hl7Application == null ? null : hl7Application.getConnections().get(0).getHostname();
    }

    private static String pdqFHIRWebAppHost(WebApplication webApplication) {
        return webApplication == null ? null : webApplication.getConnections().get(0).getHostname();
    }

}