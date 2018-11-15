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

import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4che3.util.ReverseDNS;
import org.dcm4chee.arc.HL7ConnectionEvent;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.hl7.ArchiveHL7Message;
import org.dcm4chee.arc.keycloak.KeycloakContext;
import org.dcm4chee.arc.procedure.ProcedureContext;
import org.dcm4chee.arc.study.StudyMgtContext;

import javax.servlet.http.HttpServletRequest;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Oct 2018
 */
class ProcedureRecordAuditService {
    
    private final ArchiveDeviceExtension arcDev;
    private StudyMgtContext studyMgtCtx;
    private ProcedureContext procCtx;
    private HL7ConnectionEvent hl7ConnEvent;

    ProcedureRecordAuditService(StudyMgtContext ctx, ArchiveDeviceExtension arcDev) {
        this.arcDev = arcDev;
        this.studyMgtCtx = ctx;
    }

    ProcedureRecordAuditService(ProcedureContext ctx, ArchiveDeviceExtension arcDev) {
        this.arcDev = arcDev;
        this.procCtx = ctx;
    }

    ProcedureRecordAuditService(HL7ConnectionEvent hl7ConnEvent, ArchiveDeviceExtension arcDev) {
        this.arcDev = arcDev;
        this.hl7ConnEvent = hl7ConnEvent;
    }

    AuditInfoBuilder getStudyUpdateAuditInfo() {
        return studyMgtCtx.getHttpRequest() != null ? studyExpiredByWeb() : studyExpiredByHL7();
    }

    AuditInfoBuilder getProcUpdateAuditInfo() {
        return procCtx.getHttpRequest() != null ? procUpdatedByWeb() : procUpdatedByMPPS();
    }

    AuditInfoBuilder getHL7IncomingOrderInfo() {
        return HL7AuditUtils.isOrderProcessed(hl7ConnEvent) ? orderProcessed() : orderAcknowledged();
    }

    AuditInfoBuilder getHL7OutgoingOrderInfo() {
        HL7Segment pid = HL7AuditUtils.getHL7Segment(hl7ConnEvent.getHL7Message(), "PID");
        return pid != null ? procRecForward(pid) : procRecUpdate();
    }

    private AuditInfoBuilder studyExpiredByHL7() {
        HL7Segment msh = studyMgtCtx.getUnparsedHL7Message().msh();
        return new AuditInfoBuilder.Builder()
                .callingHost(studyMgtCtx.getRemoteHostName())
                .callingUserID(msh.getSendingApplicationWithFacility())
                .calledUserID(msh.getReceivingApplicationWithFacility())
                .studyUIDAccNumDate(studyMgtCtx.getAttributes(), arcDev)
                .pIDAndName(studyMgtCtx.getStudy().getPatient().getAttributes(), arcDev)
                .outcome(outcome(studyMgtCtx.getException()))
                .build();
    }

    private AuditInfoBuilder studyExpiredByWeb() {
        HttpServletRequest request = studyMgtCtx.getHttpRequest();
        return new AuditInfoBuilder.Builder()
                .callingHost(studyMgtCtx.getRemoteHostName())
                .callingUserID(KeycloakContext.valueOf(request).getUserName())
                .calledUserID(studyMgtCtx.getHttpRequest().getRequestURI())
                .studyUIDAccNumDate(studyMgtCtx.getAttributes(), arcDev)
                .pIDAndName(studyMgtCtx.getStudy().getPatient().getAttributes(), arcDev)
                .outcome(outcome(studyMgtCtx.getException()))
                .build();
    }

    private AuditInfoBuilder procUpdatedByMPPS() {
        Association as = procCtx.getAssociation();
        return new AuditInfoBuilder.Builder()
                .callingHost(procCtx.getRemoteHostName())
                .callingUserID(as.getCallingAET())
                .calledUserID(as.getCalledAET())
                .studyUIDAccNumDate(procCtx.getAttributes(), arcDev)
                .pIDAndName(procCtx.getPatient().getAttributes(), arcDev)
                .outcome(outcome(procCtx.getException()))
                .build();
    }

    private AuditInfoBuilder procUpdatedByWeb() {
        HttpServletRequest req  = procCtx.getHttpRequest();
        return new AuditInfoBuilder.Builder()
                .callingHost(procCtx.getRemoteHostName())
                .callingUserID(KeycloakContext.valueOf(req).getUserName())
                .calledUserID(req.getRequestURI())
                .studyUIDAccNumDate(procCtx.getAttributes(), arcDev)
                .pIDAndName(procCtx.getPatient().getAttributes(), arcDev)
                .outcome(outcome(procCtx.getException()))
                .build();
    }

    private AuditInfoBuilder orderProcessed() {
        ArchiveHL7Message archiveHL7Message = (ArchiveHL7Message) hl7ConnEvent.getHL7ResponseMessage();
        UnparsedHL7Message hl7Message = hl7ConnEvent.getHL7Message();
        HL7Segment msh = hl7Message.msh();
        HL7Segment pid = HL7AuditUtils.getHL7Segment(hl7Message, "PID");
        return new AuditInfoBuilder.Builder()
                .callingHost(hl7ConnEvent.getConnection().getHostname())
                .callingUserID(msh.getSendingApplicationWithFacility())
                .calledUserID(msh.getReceivingApplicationWithFacility())
                .studyUIDAccNumDate(archiveHL7Message.getStudyAttrs(), arcDev)
                .patID(pid.getField(3, null), arcDev)
                .patName(pid.getField(5, null), arcDev)
                .outcome(outcome(hl7ConnEvent.getException()))
                .build();
    }

    private AuditInfoBuilder orderAcknowledged() {
        UnparsedHL7Message hl7Message = hl7ConnEvent.getHL7Message();
        HL7Segment msh = hl7Message.msh();
        HL7Segment pid = HL7AuditUtils.getHL7Segment(hl7Message, "PID");
        return new AuditInfoBuilder.Builder()
                .callingHost(hl7ConnEvent.getConnection().getHostname())
                .callingUserID(msh.getSendingApplicationWithFacility())
                .calledUserID(msh.getReceivingApplicationWithFacility())
                .studyIUID(arcDev.auditUnknownStudyInstanceUID())
                .patID(pid.getField(3, null), arcDev)
                .patName(pid.getField(5, null), arcDev)
                .outcome(outcome(hl7ConnEvent.getException()))
                .build();
    }

    private AuditInfoBuilder procRecForward(HL7Segment pid) {
        UnparsedHL7Message hl7Message = hl7ConnEvent.getHL7Message();
        HL7Segment msh = hl7Message.msh();
        String sendingApplicationWithFacility = msh.getSendingApplicationWithFacility();
        String receivingApplicationWithFacility = msh.getReceivingApplicationWithFacility();
        return new AuditInfoBuilder.Builder()
                .callingHost(ReverseDNS.hostNameOf(hl7ConnEvent.getSocket().getInetAddress()))
                .callingUserID(sendingApplicationWithFacility)
                .calledUserID(receivingApplicationWithFacility)
                .studyIUID(HL7AuditUtils.procRecHL7StudyIUID(hl7Message, arcDev.auditUnknownStudyInstanceUID()))
                .accNum(HL7AuditUtils.procRecHL7Acc(hl7Message))
                .patID(pid.getField(3, null), arcDev)
                .patName(pid.getField(5, null), arcDev)
                .outcome(outcome(hl7ConnEvent.getException()))
                .isOutgoingHL7()
                .outgoingHL7Sender(sendingApplicationWithFacility)
                .outgoingHL7Receiver(receivingApplicationWithFacility)
                .build();
    }

    private AuditInfoBuilder procRecUpdate() {
        UnparsedHL7Message hl7Message = hl7ConnEvent.getHL7Message();
        HL7Segment msh = hl7Message.msh();
        String sendingApplicationWithFacility = msh.getSendingApplicationWithFacility();
        String receivingApplicationWithFacility = msh.getReceivingApplicationWithFacility();
        return new AuditInfoBuilder.Builder()
                .callingHost(ReverseDNS.hostNameOf(hl7ConnEvent.getSocket().getInetAddress()))
                .callingUserID(sendingApplicationWithFacility)
                .calledUserID(receivingApplicationWithFacility)
                .studyIUID(HL7AuditUtils.procRecHL7StudyIUID(hl7Message, arcDev.auditUnknownStudyInstanceUID()))
                .accNum(HL7AuditUtils.procRecHL7Acc(hl7Message))
                .outcome(outcome(hl7ConnEvent.getException()))
                .isOutgoingHL7()
                .outgoingHL7Sender(sendingApplicationWithFacility)
                .outgoingHL7Receiver(receivingApplicationWithFacility)
                .build();
    }
    
    private static String outcome(Exception e) {
        return e != null ? e.getMessage() : null;
    }
}
