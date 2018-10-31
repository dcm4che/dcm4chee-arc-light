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
import org.dcm4che3.util.ReverseDNS;
import org.dcm4chee.arc.HL7ConnectionEvent;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.hl7.ArchiveHL7Message;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.qmgt.HttpServletRequestInfo;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Oct 2018
 */
class PatientRecordAuditService {
    
    private final ArchiveDeviceExtension arcDev;
    private PatientMgtContext ctx;
    private HL7ConnectionEvent hl7ConnEvent;

    private String callingHost;
    private String callingUserID;
    private String calledUserID;
    
    PatientRecordAuditService(PatientMgtContext ctx, ArchiveDeviceExtension arcDev) {
        this.ctx = ctx;
        this.arcDev = arcDev;
        HttpServletRequestInfo httpRequest = ctx.getHttpServletRequestInfo();
        Association association = ctx.getAssociation();
        this.callingHost = ctx.getRemoteHostName();
        this.callingUserID = httpRequest != null
                ? httpRequest.requesterUserID
                : association != null
                    ? association.getCallingAET() : null;
        this.calledUserID = httpRequest != null
                ? httpRequest.requestURI
                : association != null
                    ? association.getCalledAET() : null;
    }

    PatientRecordAuditService(HL7ConnectionEvent hl7ConnEvent, ArchiveDeviceExtension arcDev) {
        this.arcDev = arcDev;
        this.hl7ConnEvent = hl7ConnEvent;
        HL7Segment msh = hl7ConnEvent.getHL7Message().msh();
        this.callingUserID = msh.getSendingApplicationWithFacility();
        this.calledUserID = msh.getReceivingApplicationWithFacility();
        this.callingHost = hl7ConnEvent.getConnection() != null
                ? hl7ConnEvent.getConnection().getHostname()
                : ReverseDNS.hostNameOf(hl7ConnEvent.getSocket().getInetAddress());
        if (isArchiveHL7MsgAndNotOrder()) { // will occur only for outgoing
            ArchiveHL7Message archiveHL7Message = (ArchiveHL7Message) hl7ConnEvent.getHL7Message();
            HttpServletRequestInfo httpServletRequestInfo = archiveHL7Message.getHttpServletRequestInfo();
            if (httpServletRequestInfo != null) {
                this.callingHost = httpServletRequestInfo.requesterHost;
                this.callingUserID = httpServletRequestInfo.requesterUserID;
                this.calledUserID = httpServletRequestInfo.requestURI;
            }
        }
    }
    
    AuditInfoBuilder getPatAuditInfo() {
        return new AuditInfoBuilder.Builder()
                .callingHost(callingHost)
                .callingUserID(callingUserID)
                .calledUserID(calledUserID)
                .pIDAndName(ctx.getAttributes(), arcDev)
                .outcome(outcome(ctx.getException()))
                .build();
    }
    
    AuditInfoBuilder getPrevPatAuditInfo() {
        return new AuditInfoBuilder.Builder()
                .callingHost(callingHost)
                .callingUserID(callingUserID)
                .calledUserID(calledUserID)
                .pIDAndName(ctx.getPreviousAttributes(), arcDev)
                .outcome(outcome(ctx.getException()))
                .build();
    }

    AuditInfoBuilder getHL7IncomingPatInfo() {
        HL7Segment pid = HL7AuditUtils.getHL7Segment(hl7ConnEvent.getHL7Message(), "PID");
        return new AuditInfoBuilder.Builder()
                .callingHost(callingHost)
                .callingUserID(callingUserID)
                .calledUserID(calledUserID)
                .patID(pid.getField(3, null), arcDev)
                .patName(pid.getField(5, null), arcDev)
                .outcome(outcome(hl7ConnEvent.getException()))
                .build();
    }

    AuditInfoBuilder getHL7IncomingPrevPatInfo(HL7Segment mrg) {
        return new AuditInfoBuilder.Builder()
                .callingHost(callingHost)
                .callingUserID(callingUserID)
                .calledUserID(calledUserID)
                .patID(mrg.getField(1, null), arcDev)
                .patName(mrg.getField(7, null), arcDev)
                .outcome(outcome(hl7ConnEvent.getException()))
                .build();
    }

    AuditInfoBuilder getHL7OutgoingPatInfo() {
        HL7Segment msh = hl7ConnEvent.getHL7Message().msh();
        HL7Segment pid = HL7AuditUtils.getHL7Segment(hl7ConnEvent.getHL7Message(), "PID");
        return new AuditInfoBuilder.Builder()
                .callingHost(callingHost)
                .callingUserID(callingUserID)
                .calledUserID(calledUserID)
                .patID(pid.getField(3, null), arcDev)
                .patName(pid.getField(5, null), arcDev)
                .outcome(outcome(hl7ConnEvent.getException()))
                .isOutgoingHL7()
                .outgoingHL7Sender(msh.getSendingApplicationWithFacility())
                .outgoingHL7Receiver(msh.getReceivingApplicationWithFacility())
                .build();
    }

    AuditInfoBuilder getHL7OutgoingPrevPatInfo(HL7Segment mrg) {
        HL7Segment msh = hl7ConnEvent.getHL7Message().msh();
        return new AuditInfoBuilder.Builder()
                .callingHost(callingHost)
                .callingUserID(callingUserID)
                .calledUserID(calledUserID)
                .patID(mrg.getField(1, null), arcDev)
                .patName(mrg.getField(7, null), arcDev)
                .outcome(outcome(hl7ConnEvent.getException()))
                .isOutgoingHL7()
                .outgoingHL7Sender(msh.getSendingApplicationWithFacility())
                .outgoingHL7Receiver(msh.getReceivingApplicationWithFacility())
                .build();
    }

    boolean isArchiveHL7MsgAndNotOrder() { //pat audit for all cases except proc rec update
        return hl7ConnEvent.getHL7Message() instanceof ArchiveHL7Message && !HL7AuditUtils.isOrderProcessed(hl7ConnEvent);
    }

    private static String outcome(Exception e) {
        return e != null ? e.getMessage() : null;
    }
}
