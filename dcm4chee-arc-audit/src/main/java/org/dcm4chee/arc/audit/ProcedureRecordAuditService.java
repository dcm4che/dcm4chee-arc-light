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
import org.dcm4che3.conf.api.hl7.IHL7ApplicationCache;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.hl7.HL7Message;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.HL7DeviceExtension;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4che3.util.ReverseDNS;
import org.dcm4che3.util.UIDUtils;
import org.dcm4chee.arc.HL7ConnectionEvent;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.ArchiveHL7ApplicationExtension;
import org.dcm4chee.arc.conf.HL7OrderMissingStudyIUIDPolicy;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.hl7.ArchiveHL7Message;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.procedure.ProcedureContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2018
 */
class ProcedureRecordAuditService extends AuditService {

    private final static Logger LOG = LoggerFactory.getLogger(ProcedureRecordAuditService.class);

    private ArchiveDeviceExtension arcDev;
    private ProcedureContext procCtx;
    private HL7ConnectionEvent hl7ConnEvent;
    private AuditInfoBuilder.Builder infoBuilder;
    private HL7Segment pid;

    ProcedureRecordAuditService(ProcedureContext ctx, ArchiveDeviceExtension arcDev) {
        procCtx = ctx;
        Patient patient = procCtx.getPatient();
        infoBuilder = new AuditInfoBuilder.Builder()
                .studyUIDAccNumDate(procCtx.getAttributes(), arcDev)
                .pIDAndName(patient != null ? patient.getAttributes() : ctx.getAttributes(), arcDev)
                .mppsUID(procCtx.getMppsUID())
                .status(procCtx.getStatus())
                .outcome(procCtx.getOutcomeMsg() != null ? procCtx.getOutcomeMsg() : outcome(procCtx.getException()));
        if (procCtx.getAttributes() == null && procCtx.getStudyInstanceUID() != null)
            infoBuilder.studyIUID(procCtx.getStudyInstanceUID());
    }

    ProcedureRecordAuditService(HL7ConnectionEvent hl7ConnEvent, ArchiveDeviceExtension arcDev) {
        this.arcDev = arcDev;
        this.hl7ConnEvent = hl7ConnEvent;
        this.pid = HL7AuditUtils.getHL7Segment(hl7ConnEvent.getHL7Message(), "PID");
    }

    private static String callingUserID(ProcedureContext ctx, ArchiveDeviceExtension arcDev) {
        HttpServletRequestInfo httpServletRequestInfo = ctx.getHttpRequest();
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

    private static String calledUserID(ProcedureContext ctx) {
        HttpServletRequestInfo httpServletRequestInfo = ctx.getHttpRequest();
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

    static AuditInfo procedureAuditInfo(ProcedureContext ctx, ArchiveDeviceExtension arcDev) {
        Patient patient = ctx.getPatient();
        return new AuditInfoBuilder.Builder()
                .callingUserID(callingUserID(ctx, arcDev))
                .callingHost(ctx.getRemoteHostName())
                .calledUserID(calledUserID(ctx))
                .addAttrs(ctx.getAttributes(), arcDev)
                .pIDAndName(patient == null ? ctx.getAttributes() : patient.getAttributes(), arcDev)
                .outcome(ctx.getException() == null ? null : ctx.getException().getMessage())
                .studyIUID(ctx.getStudyInstanceUID()) //for case : ctx.getAttributes() == null && ctx.getStudyInstanceUID() != null
                .mppsUID(ctx.getMppsUID())
                .status(ctx.getStatus())
                .findSCP(ctx.getSourceMwlScp())
                .destUserID(ctx.getLocalAET())
                .toAuditInfo();
    }

    static AuditInfo procedureAuditInfoForHL7Incoming(
            HL7ConnectionEvent hl7ConnEvent, HL7Message hl7Message, ArchiveDeviceExtension arcDev) {
        UnparsedHL7Message unparsedHL7Message = hl7ConnEvent.getHL7Message();
        Connection connection = hl7ConnEvent.getConnection();
        HL7Segment msh = unparsedHL7Message.msh();
        HL7Segment pid = hl7Message.getSegment(AuditUtils.PID_SEGMENT);
        if (pid != null)
            return procedureAuditInfoWithPIDForHL7Incoming(hl7ConnEvent, hl7Message, msh, pid, arcDev);

        HL7Segment zds = hl7Message.getSegment(AuditUtils.ZDS_SEGMENT);
        HL7Segment ipc = hl7Message.getSegment(AuditUtils.IPC_SEGMENT);
        HL7Segment obr = hl7Message.getSegment(AuditUtils.OBR_SEGMENT);
        String studyIUIDFromHL7 = studyIUIDFromHL7Incoming(zds, ipc);
        String accessionNoFromHL7 = accessionNoFromHL7(obr, ipc);
        String receivingAppFacility = msh.getReceivingApplicationWithFacility();
        return new AuditInfoBuilder.Builder()
                .callingUserID(msh.getSendingApplicationWithFacility())
                .callingHost(connection == null
                        ? ReverseDNS.hostNameOf(hl7ConnEvent.getSocket().getInetAddress())
                        : connection.getHostname())
                .calledUserID(receivingAppFacility)
                .studyIUID(studyIUIDFromHL7 == null
                        ? derivedStudyIUID(accessionNoFromHL7, obr, ipc, arcDev, receivingAppFacility)
                        : studyIUIDFromHL7)
                .accNum(accessionNoFromHL7)
                .outcome(hl7ConnEvent.getException() == null ? null : hl7ConnEvent.getException().getMessage())
                .toAuditInfo();
    }

    private static AuditInfo procedureAuditInfoWithPIDForHL7Incoming(
            HL7ConnectionEvent hl7ConnEvent, HL7Message hl7Message, HL7Segment msh, HL7Segment pid,
            ArchiveDeviceExtension arcDev) {
        Connection connection = hl7ConnEvent.getConnection();
        HL7Segment zds = hl7Message.getSegment(AuditUtils.ZDS_SEGMENT);
        HL7Segment ipc = hl7Message.getSegment(AuditUtils.IPC_SEGMENT);
        HL7Segment obr = hl7Message.getSegment(AuditUtils.OBR_SEGMENT);
        String studyIUIDFromHL7 = studyIUIDFromHL7Incoming(zds, ipc);
        String accessionNoFromHL7 = accessionNoFromHL7(obr, ipc);
        String receivingAppFacility = msh.getReceivingApplicationWithFacility();
        return new AuditInfoBuilder.Builder()
                .callingUserID(msh.getSendingApplicationWithFacility())
                .callingHost(connection == null
                        ? ReverseDNS.hostNameOf(hl7ConnEvent.getSocket().getInetAddress())
                        : connection.getHostname())
                .calledUserID(receivingAppFacility)
                .patID(pid.getField(AuditUtils.PID_SEGMENT_PATIENT_ID, null), arcDev)
                .patName(pid.getField(AuditUtils.PID_SEGMENT_PATIENT_NAME, null), arcDev)
                .studyIUID(studyIUIDFromHL7 == null
                        ? derivedStudyIUID(accessionNoFromHL7, obr, ipc, arcDev, receivingAppFacility)
                        : studyIUIDFromHL7)
                .accNum(accessionNoFromHL7)
                .outcome(hl7ConnEvent.getException() == null ? null : hl7ConnEvent.getException().getMessage())
                .toAuditInfo();
    }

    private static String studyIUIDFromHL7Incoming(HL7Segment zds, HL7Segment ipc) {
        return zds == null
                ? ipc == null
                    ? null
                    : ipc.getField(AuditUtils.IPC_SEGMENT_STUDY_IUID, null)
                : zds.getField(AuditUtils.ZDS_SEGMENT_STUDY_IUID, null);
    }

    private static String studyIUIDInHL7Outgoing(HL7Segment zds, HL7Segment ipc) {
        return zds == null
                ? ipc == null
                    ? null
                    : ipc.getField(AuditUtils.IPC_SEGMENT_STUDY_IUID, null)
                : zds.getField(AuditUtils.ZDS_SEGMENT_STUDY_IUID, null);
    }

    private static String derivedStudyIUID(
            String accessionNoFromHL7, HL7Segment obr, HL7Segment ipc, ArchiveDeviceExtension arcDev,
            String receivingAppFacility) {
        String reqProcIDFromHL7 = reqProcIDFromHL7(obr, ipc);
        HL7Application hl7Receiver = findHL7Application(receivingAppFacility, arcDev);
        return reqProcIDFromHL7 == null
                ? accessionNoFromHL7 == null
                    || hl7Receiver == null
                    || hl7Receiver.getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class)
                                  .hl7OrderMissingStudyIUIDPolicy() != HL7OrderMissingStudyIUIDPolicy.ACCESSION_BASED
                        ? arcDev.auditUnknownStudyInstanceUID()
                        : UIDUtils.createNameBasedUID(accessionNoFromHL7.getBytes())
                : UIDUtils.createNameBasedUID(reqProcIDFromHL7.getBytes());
    }

    private static String accessionNoFromHL7(HL7Segment obr, HL7Segment ipc) {
        return obr == null
                ? ipc == null
                    ? null
                    : ipc.getField(AuditUtils.IPC_SEGMENT_ACCESSION_NO, null)
                : obr.getField(AuditUtils.OBR_SEGMENT_ACCESSION_NO, null);
    }

    private static String reqProcIDFromHL7(HL7Segment obr, HL7Segment ipc) {
        return obr == null
                ? ipc == null
                    ? null
                    : ipc.getField(AuditUtils.IPC_SEGMENT_REQ_PROC_ID, null)
                : obr.getField(AuditUtils.OBR_SEGMENT_REQ_PROC_ID, null);
    }

    private static HL7Application findHL7Application(String appFacility, ArchiveDeviceExtension arcDev) {
        HL7Application hl7Application = arcDev.getDevice().getDeviceExtension(HL7DeviceExtension.class)
                                              .getHL7Application(appFacility, true);
        if (hl7Application == null)
            LOG.info("No HL7 application found in archive device for {}", appFacility);

        return hl7Application;
    }

    //procedure outgoing on : HL7 forwarding / MPPS forwarding / notify HL7 receivers on Study or MPPS trigger / DCM2HL7Exporter with OMI / OMG

    private static String callingUserIDOutgoingHL7(HL7ConnectionEvent hl7ConnEvent) {
        UnparsedHL7Message hl7Message = hl7ConnEvent.getHL7Message();
        HL7Segment msh = hl7Message.msh();
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
        ArchiveHL7Message archiveHL7Message = (ArchiveHL7Message) hl7Message;
        HttpServletRequestInfo httpServletRequestInfo = archiveHL7Message.getHttpServletRequestInfo();
        if (httpServletRequestInfo != null)
            return httpServletRequestInfo.requesterHost;

        return callingHost;
    }

    private static String calledUserIDOutgoingHL7(HL7ConnectionEvent hl7ConnEvent) {
        UnparsedHL7Message hl7Message = hl7ConnEvent.getHL7Message();
        HL7Segment msh = hl7Message.msh();
        ArchiveHL7Message archiveHL7Message = (ArchiveHL7Message) hl7Message;
        HttpServletRequestInfo httpServletRequestInfo = archiveHL7Message.getHttpServletRequestInfo();
        if (httpServletRequestInfo != null)
            return httpServletRequestInfo.requestURIWithQueryStr();

        return msh.getReceivingApplicationWithFacility();
    }

    static AuditInfo procedureAuditInfoForHL7Outgoing(
            HL7ConnectionEvent hl7ConnEvent, HL7Message hl7Message, ArchiveDeviceExtension arcDev) {
        UnparsedHL7Message unparsedHL7Message = hl7ConnEvent.getHL7Message();
        HL7Segment msh = unparsedHL7Message.msh();
        HL7Segment pid = hl7Message.getSegment(AuditUtils.PID_SEGMENT);
        if (pid != null)
            return procedureAuditInfoWithPIDForHL7Outgoing(hl7ConnEvent, hl7Message, pid, arcDev);

        HL7Segment zds = hl7Message.getSegment(AuditUtils.ZDS_SEGMENT);
        HL7Segment ipc = hl7Message.getSegment(AuditUtils.IPC_SEGMENT);
        HL7Segment obr = hl7Message.getSegment(AuditUtils.OBR_SEGMENT);
        String studyIUIDFromHL7 = studyIUIDFromHL7Incoming(zds, ipc);
        String accessionNoFromHL7 = accessionNoFromHL7(obr, ipc);
        String sendingAppFacility = msh.getSendingApplicationWithFacility();
        return new AuditInfoBuilder.Builder()
                .callingUserID(callingUserIDOutgoingHL7(hl7ConnEvent))
                .callingHost(callingHostOutgoingHL7(hl7ConnEvent))
                .calledUserID(calledUserIDOutgoingHL7(hl7ConnEvent))
                .studyIUID(studyIUIDFromHL7 == null
                        ? derivedStudyIUID(accessionNoFromHL7, obr, ipc, arcDev, sendingAppFacility)
                        : studyIUIDFromHL7)
                .accNum(accessionNoFromHL7)
                .outcome(hl7ConnEvent.getException() == null ? null : hl7ConnEvent.getException().getMessage())
                .outgoingHL7Sender(msh.getSendingApplicationWithFacility())
                .outgoingHL7Receiver(msh.getReceivingApplicationWithFacility())
                .toAuditInfo();
    }

    private static AuditInfo procedureAuditInfoWithPIDForHL7Outgoing(
            HL7ConnectionEvent hl7ConnEvent, HL7Message hl7Message, HL7Segment pid, ArchiveDeviceExtension arcDev) {
        UnparsedHL7Message unparsedHL7Message = hl7ConnEvent.getHL7Message();
        HL7Segment msh = unparsedHL7Message.msh();
        HL7Segment zds = hl7Message.getSegment(AuditUtils.ZDS_SEGMENT);
        HL7Segment ipc = hl7Message.getSegment(AuditUtils.IPC_SEGMENT);
        HL7Segment obr = hl7Message.getSegment(AuditUtils.OBR_SEGMENT);
        String studyIUIDFromHL7 = studyIUIDFromHL7Incoming(zds, ipc);
        String accessionNoFromHL7 = accessionNoFromHL7(obr, ipc);
        String sendingAppFacility = msh.getSendingApplicationWithFacility();
        return new AuditInfoBuilder.Builder()
                .callingUserID(callingUserIDOutgoingHL7(hl7ConnEvent))
                .callingHost(callingHostOutgoingHL7(hl7ConnEvent))
                .calledUserID(calledUserIDOutgoingHL7(hl7ConnEvent))
                .patID(pid.getField(AuditUtils.PID_SEGMENT_PATIENT_ID, null), arcDev)
                .patName(pid.getField(AuditUtils.PID_SEGMENT_PATIENT_NAME, null), arcDev)
                .studyIUID(studyIUIDFromHL7 == null
                        ? derivedStudyIUID(accessionNoFromHL7, obr, ipc, arcDev, sendingAppFacility)
                        : studyIUIDFromHL7)
                .accNum(accessionNoFromHL7)
                .outcome(hl7ConnEvent.getException() == null ? null : hl7ConnEvent.getException().getMessage())
                .outgoingHL7Sender(msh.getSendingApplicationWithFacility())
                .outgoingHL7Receiver(msh.getReceivingApplicationWithFacility())
                .toAuditInfo();
    }

    AuditInfoBuilder getProcUpdateAuditInfo() {
        return procCtx.getHttpRequest() != null
                ? procUpdatedByWeb()
                : procCtx.getAssociation() != null
                    ? procUpdatedByMPPS()
                    : procCtx.getUnparsedHL7Message() != null
                        ? procUpdatedByHL7() : procUpdatedByMWLImport();
    }

    AuditInfoBuilder getHL7IncomingOrderInfo() {
        UnparsedHL7Message hl7Message = hl7ConnEvent.getHL7Message();
        HL7Segment msh = hl7Message.msh();
        infoBuilder = new AuditInfoBuilder.Builder()
                .callingHost(hl7ConnEvent.getConnection().getHostname())
                .callingUserID(msh.getSendingApplicationWithFacility())
                .calledUserID(msh.getReceivingApplicationWithFacility())
                .studyIUID(HL7AuditUtils.procRecHL7StudyIUID(hl7Message, arcDev.auditUnknownStudyInstanceUID()))
                .accNum(HL7AuditUtils.procRecHL7Acc(hl7Message))
                .outcome(outcome(hl7ConnEvent.getException()));
        if (hasPIDSegment())
            infoBuilder
                .patID(pid.getField(3, null), arcDev)
                .patName(pid.getField(5, null), arcDev);
        return HL7AuditUtils.isOrderProcessed(hl7ConnEvent) ? orderProcessed() : infoBuilder.build();
    }

    AuditInfoBuilder getHL7OutgoingOrderInfo() {
        HL7Segment pid = HL7AuditUtils.getHL7Segment(hl7ConnEvent.getHL7Message(), "PID");
        UnparsedHL7Message hl7Message = hl7ConnEvent.getHL7Message();
        HL7Segment msh = hl7Message.msh();
        String sendingApplicationWithFacility = msh.getSendingApplicationWithFacility();
        String receivingApplicationWithFacility = msh.getReceivingApplicationWithFacility();
        infoBuilder = new AuditInfoBuilder.Builder()
                .callingHost(ReverseDNS.hostNameOf(hl7ConnEvent.getSocket().getInetAddress()))
                .callingUserID(sendingApplicationWithFacility)
                .calledUserID(receivingApplicationWithFacility)
                .studyIUID(HL7AuditUtils.procRecHL7StudyIUID(hl7Message, arcDev.auditUnknownStudyInstanceUID()))
                .accNum(HL7AuditUtils.procRecHL7Acc(hl7Message))
                .outcome(outcome(hl7ConnEvent.getException()))
                .isOutgoingHL7()
                .outgoingHL7Sender(sendingApplicationWithFacility)
                .outgoingHL7Receiver(receivingApplicationWithFacility);
        return pid != null ? procRecForward(pid) : infoBuilder.build();
    }

    boolean hasPIDSegment() {
        return pid != null;
    }

    private AuditInfoBuilder procUpdatedByMPPS() {
        Association as = procCtx.getAssociation();
        return infoBuilder
                .callingUserID(as.getCallingAET())
                .callingHost(procCtx.getRemoteHostName())
                .calledUserID(as.getCalledAET())
                .build();
    }

    private AuditInfoBuilder procUpdatedByHL7() {
        UnparsedHL7Message msg = procCtx.getUnparsedHL7Message();
        return infoBuilder
                .callingUserID(msg.msh().getSendingApplicationWithFacility())
                .callingHost(procCtx.getRemoteHostName())
                .calledUserID(msg.msh().getReceivingApplicationWithFacility())
                .build();
    }

    private AuditInfoBuilder procUpdatedByWeb() {
        if (procCtx.getSourceMwlScp() != null)
            return procUpdatedByMWLImport();

        HttpServletRequestInfo httpServletRequestInfo  = procCtx.getHttpRequest();
        return infoBuilder
                .callingUserID(httpServletRequestInfo.requesterUserID)
                .callingHost(procCtx.getRemoteHostName())
                .calledUserID(requestURLWithQueryParams(httpServletRequestInfo))
                .build();
    }

    private String requestURLWithQueryParams(HttpServletRequestInfo httpServletRequestInfo) {
        return httpServletRequestInfo.queryString == null
                ? httpServletRequestInfo.requestURI
                : httpServletRequestInfo.requestURI + "?" + httpServletRequestInfo.queryString;
    }

    private AuditInfoBuilder procUpdatedByMWLImport() {
        HttpServletRequestInfo req  = procCtx.getHttpRequest();
        AuditInfoBuilder.Builder auditInfoBuilder = infoBuilder
                .findSCP(procCtx.getSourceMwlScp())
                .destUserID(procCtx.getLocalAET());
        if (req != null)
            auditInfoBuilder.callingUserID(req.requesterUserID).callingHost(procCtx.getRemoteHostName());

        return auditInfoBuilder.build();
    }

    private AuditInfoBuilder orderProcessed() {
        UnparsedHL7Message hl7ResponseMessage = hl7ConnEvent.getHL7ResponseMessage();
        if (hl7ResponseMessage instanceof ArchiveHL7Message) {
            ArchiveHL7Message archiveHL7Message = (ArchiveHL7Message) hl7ResponseMessage;
            Attributes attrs = archiveHL7Message.getStudyAttrs();
            if (attrs != null)
                infoBuilder
                    .studyIUID(attrs.getString(Tag.StudyInstanceUID))
                    .accNum(attrs.getString(Tag.AccessionNumber));
        }
        return infoBuilder.build();
    }

    private AuditInfoBuilder procRecForward(HL7Segment pid) {
        return infoBuilder
                .patID(pid.getField(3, null), arcDev)
                .patName(pid.getField(5, null), arcDev)
                .build();
    }
    
    private static String outcome(Exception e) {
        return e != null ? e.getMessage() : null;
    }

    static void audit(
            AuditLogger auditLogger, Path path, AuditUtils.EventType eventType, Device device,
            IApplicationEntityCache aeCache, IHL7ApplicationCache hl7AppCache) {
        SpoolFileReader reader = new SpoolFileReader(path.toFile());
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        EventIdentification eventIdentification = getEventIdentification(auditInfo, eventType);
        eventIdentification.setEventDateTime(getEventTime(path, auditLogger));
        List<ActiveParticipant> activeParticipants = activeParticipants(
                auditInfo, eventType, auditLogger, device, aeCache, hl7AppCache);

        String patientID = auditInfo.getField(AuditInfo.P_ID);
        if (patientID == null) {
            emitAuditMessage(auditLogger, eventIdentification, activeParticipants, study(auditInfo, reader));
            return;
        }

        emitAuditMessage(auditLogger, eventIdentification, activeParticipants, study(auditInfo, reader), patient(auditInfo));
    }

    private static List<ActiveParticipant> activeParticipants(
            AuditInfo auditInfo, AuditUtils.EventType eventType, AuditLogger auditLogger, Device device,
            IApplicationEntityCache aeCache, IHL7ApplicationCache hl7AppCache) {
        List<ActiveParticipant> activeParticipants = new ArrayList<>();
        String calledUserID = auditInfo.getField(AuditInfo.CALLED_USERID);
        if (calledUserID == null) {
            activeParticipants.add(archiveRequestor(auditInfo, eventType, auditLogger));
        } else if (calledUserID.contains("/")) {
            activeParticipants.add(archiveURI(auditInfo, eventType, auditLogger));
            activeParticipants.add(requestor(auditInfo, eventType));
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

    private static ActiveParticipant requestor(AuditInfo auditInfo, AuditUtils.EventType eventType) {
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
        if (eventType.source != null)
            requestor.getRoleIDCode().add(eventType.source);
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
        if (eventType.destination != null)
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

    private static EventIdentification getEventIdentification(AuditInfo auditInfo, AuditUtils.EventType eventType) {
        String outcome = auditInfo.getField(AuditInfo.OUTCOME);
        EventIdentification ei = new EventIdentification();
        ei.setEventID(eventType.eventID);
        ei.setEventActionCode(eventType.eventActionCode);
        ei.setEventOutcomeDescription(outcome);
        ei.setEventOutcomeIndicator(outcome == null
                ? AuditMessages.EventOutcomeIndicator.Success
                : AuditMessages.EventOutcomeIndicator.MinorFailure);
        return ei;
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

    private static ParticipantObjectIdentification study(AuditInfo auditInfo, SpoolFileReader reader) {
        ParticipantObjectIdentification study = new ParticipantObjectIdentification();
        study.setParticipantObjectID(auditInfo.getField(AuditInfo.STUDY_UID));
        study.setParticipantObjectIDTypeCode(AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID);
        study.setParticipantObjectTypeCode(AuditMessages.ParticipantObjectTypeCode.SystemObject);
        study.setParticipantObjectTypeCodeRole(AuditMessages.ParticipantObjectTypeCodeRole.Report);
        study.getParticipantObjectDetail().add(
                AuditMessages.createParticipantObjectDetail("StudyDate", auditInfo.getField(AuditInfo.STUDY_DATE)));
        study.getParticipantObjectDetail().add(
                AuditMessages.createParticipantObjectDetail("StudyDescription", auditInfo.getField(AuditInfo.STUDY_DESC)));
        study.getParticipantObjectDetail().add(
                AuditMessages.createParticipantObjectDetail("SeriesDescription", auditInfo.getField(AuditInfo.SERIES_DESC)));
        study.getParticipantObjectDetail().add(
                AuditMessages.createParticipantObjectDetail("Modality", auditInfo.getField(AuditInfo.MODALITY)));
        List<ParticipantObjectDetail> hl7ParticipantObjectDetails = hl7ParticipantObjectDetails(reader);
        if (!hl7ParticipantObjectDetails.isEmpty())
            study.getParticipantObjectDetail().addAll(hl7ParticipantObjectDetails);
        return study;
    }

    private static List<ParticipantObjectDetail> hl7ParticipantObjectDetails(SpoolFileReader reader) {
        List<ParticipantObjectDetail> details = new ArrayList<>();
        hl7ParticipantObjectDetail(details, reader.getData());
        hl7ParticipantObjectDetail(details, reader.getAck());
        return details;
    }

    private static void hl7ParticipantObjectDetail(List<ParticipantObjectDetail> detail, byte[] data) {
        if (data.length <= 0)
            return;

        HL7Segment msh = HL7Segment.parseMSH(data, data.length, new ParsePosition(0));
        String messageType = msh.getMessageType();
        String messageControlID = msh.getMessageControlID();
        detail.add(hl7ParticipantObjectDetail("HL7v2 Message", data));
        if (messageType != null)
            detail.add(hl7ParticipantObjectDetail("MSH-9", messageType.getBytes()));
        if (messageControlID != null)
            detail.add(hl7ParticipantObjectDetail("MSH-10", messageControlID.getBytes()));
    }

    private static ParticipantObjectDetail hl7ParticipantObjectDetail(String key, byte[] val) {
        ParticipantObjectDetail detail = new ParticipantObjectDetail();
        detail.setType(key);
        detail.setValue(val);
        return detail;
    }

    static AuditMessage auditMsg(
            AuditLogger auditLogger, Path path, AuditUtils.EventType eventType, IApplicationEntityCache aeCache) {
        SpoolFileReader reader = new SpoolFileReader(path.toFile());
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        return AuditMessages.createMessage(
                EventID.toEventIdentification(auditLogger, path, eventType, auditInfo),
                activeParticipants(auditLogger, auditInfo, aeCache),
                ParticipantObjectID.studyPatParticipants(auditInfo, reader, auditLogger));
    }

    private static ActiveParticipant[] activeParticipants(
            AuditLogger auditLogger, AuditInfo auditInfo, IApplicationEntityCache aeCache) {
        ActiveParticipant[] activeParticipants = new ActiveParticipant[3];
        String callingUserID = auditInfo.getField(AuditInfo.CALLING_USERID);
        String callingHost = auditInfo.getField(AuditInfo.CALLING_HOST);

        String findScp = auditInfo.getField(AuditInfo.FIND_SCP);
        if (findScp != null) {
            activeParticipants[0] = new ActiveParticipantBuilder(findScp, AuditUtils.findScpHost(findScp, aeCache))
                                    .userIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle)
                                    .build();
            ActiveParticipantBuilder destination = new ActiveParticipantBuilder(
                                                    auditInfo.getField(AuditInfo.DEST_USER_ID),
                                                    getLocalHostName(auditLogger))
                                                    .userIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle)
                                                    .altUserID(AuditLogger.processID());
            if (callingUserID != null) {
                activeParticipants[1] = destination.build();
                activeParticipants[2] = new ActiveParticipantBuilder(callingUserID, callingHost)
                        .userIDTypeCode(AuditMessages.userIDTypeCode(callingUserID))
                        .isRequester().build();
            } else
                activeParticipants[1] = destination.isRequester().build();

            return activeParticipants;
        }

        String calledUserID = auditInfo.getField(AuditInfo.CALLED_USERID);
        AuditMessages.UserIDTypeCode calledUserIDTypeCode = userIDTypeCode(calledUserID);
        boolean isHL7Forward = auditInfo.getField(AuditInfo.IS_OUTGOING_HL7) != null;
        ActiveParticipantBuilder callingUserParticipant = new ActiveParticipantBuilder(callingUserID, callingHost)
                .userIDTypeCode(AuditService.remoteUserIDTypeCode(
                        calledUserIDTypeCode, callingUserID));

        activeParticipants[0] = isHL7Forward
                                ? callingUserParticipant.build()
                                : callingUserParticipant.isRequester().build();
        activeParticipants[1] = new ActiveParticipantBuilder(calledUserID, getLocalHostName(auditLogger))
                                    .userIDTypeCode(calledUserIDTypeCode)
                                    .altUserID(AuditLogger.processID())
                                    .build();
        if (isHL7Forward)
            activeParticipants[2] = new ActiveParticipantBuilder(
                    auditLogger.getDevice().getDeviceName(),
                    getLocalHostName(auditLogger))
                    .userIDTypeCode(AuditMessages.UserIDTypeCode.DeviceName)
                    .altUserID(AuditLogger.processID())
                    .isRequester()
                    .build();
        return activeParticipants;
    }

    private static String getLocalHostName(AuditLogger auditLogger) {
        return auditLogger.getConnections().get(0).getHostname();
    }

    private static AuditMessages.UserIDTypeCode userIDTypeCode(String userID) {
        return userID.indexOf('/') != -1
                ? AuditMessages.UserIDTypeCode.URI
                : userID.indexOf('|') != -1
                    ? AuditMessages.UserIDTypeCode.ApplicationFacility
                    : AuditMessages.UserIDTypeCode.StationAETitle;
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
}