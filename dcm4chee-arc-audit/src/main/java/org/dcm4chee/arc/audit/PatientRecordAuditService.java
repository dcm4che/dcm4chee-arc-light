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

import org.dcm4che3.audit.ActiveParticipantBuilder;
import org.dcm4che3.audit.AuditMessage;
import org.dcm4che3.audit.AuditMessages;
import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.hl7.IHL7ApplicationCache;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.HL7DeviceExtension;
import org.dcm4che3.util.ReverseDNS;
import org.dcm4chee.arc.HL7ConnectionEvent;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.hl7.ArchiveHL7Message;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;

import java.nio.file.Path;


/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Oct 2018
 */
class PatientRecordAuditService {
    
    private final ArchiveDeviceExtension arcDev;
    private PatientMgtContext ctx;
    private HL7ConnectionEvent hl7ConnEvent;
    private AuditInfoBuilder.Builder infoBuilder;

    PatientRecordAuditService(PatientMgtContext ctx, ArchiveDeviceExtension arcDev) {
        this.ctx = ctx;
        this.arcDev = arcDev;
        HttpServletRequestInfo httpRequest = ctx.getHttpServletRequestInfo();
        Association association = ctx.getAssociation();
        String callingUserID = httpRequest != null
                ? httpRequest.requesterUserID
                : association != null
                    ? association.getCallingAET() : arcDev.getDevice().getDeviceName();
        String calledUserID = httpRequest != null
                ? httpRequest.requestURI
                : association != null
                    ? association.getCalledAET() : null;
        infoBuilder = new AuditInfoBuilder.Builder()
                .callingHost(ctx.getRemoteHostName())
                .callingUserID(callingUserID)
                .calledUserID(calledUserID)
                .outcome(outcome(ctx.getException()))
                .patVerificationStatus(ctx.getPatientVerificationStatus())
                .pdqServiceURI(ctx.getPDQServiceURI());
    }

    PatientRecordAuditService(HL7ConnectionEvent hl7ConnEvent, ArchiveDeviceExtension arcDev) {
        this.arcDev = arcDev;
        this.hl7ConnEvent = hl7ConnEvent;
        HL7Segment msh = hl7ConnEvent.getHL7Message().msh();
        String callingUserID = msh.getSendingApplicationWithFacility();
        String calledUserID = msh.getReceivingApplicationWithFacility();
        String callingHost = hl7ConnEvent.getConnection() != null
                ? hl7ConnEvent.getConnection().getHostname()
                : ReverseDNS.hostNameOf(hl7ConnEvent.getSocket().getInetAddress());

        if (isArchiveHL7MsgAndNotOrder()) { // will occur only for outgoing
            ArchiveHL7Message archiveHL7Message = (ArchiveHL7Message) hl7ConnEvent.getHL7Message();
            HttpServletRequestInfo httpServletRequestInfo = archiveHL7Message.getHttpServletRequestInfo();
            if (httpServletRequestInfo != null) {
                callingHost = httpServletRequestInfo.requesterHost;
                callingUserID = httpServletRequestInfo.requesterUserID;
                calledUserID = httpServletRequestInfo.requestURI;
            }
        }

        infoBuilder = new AuditInfoBuilder.Builder()
                .callingHost(callingHost)
                .callingUserID(callingUserID)
                .calledUserID(calledUserID)
                .outcome(outcome(hl7ConnEvent.getException()));
    }

    AuditInfoBuilder getPatAuditInfo() {
        return infoBuilder.pIDAndName(ctx.getAttributes(), arcDev).build();
    }
    
    AuditInfoBuilder getPrevPatAuditInfo() {
        return infoBuilder.pIDAndName(ctx.getPreviousAttributes(), arcDev).build();
    }

    AuditInfoBuilder getHL7IncomingPatInfo() {
        HL7Segment pid = HL7AuditUtils.getHL7Segment(hl7ConnEvent.getHL7Message(), "PID");
        return infoBuilder
                .patID(pid.getField(3, null), arcDev)
                .patName(pid.getField(5, null), arcDev)
                .build();
    }

    AuditInfoBuilder getHL7IncomingPrevPatInfo(HL7Segment mrg) {
        return infoBuilder
                .patID(mrg.getField(1, null), arcDev)
                .patName(mrg.getField(7, null), arcDev)
                .build();
    }

    AuditInfoBuilder getHL7OutgoingPatInfo() {
        HL7Segment msh = hl7ConnEvent.getHL7Message().msh();
        HL7Segment pid = HL7AuditUtils.getHL7Segment(hl7ConnEvent.getHL7Message(), "PID");
        return infoBuilder
                .patID(pid.getField(3, null), arcDev)
                .patName(pid.getField(5, null), arcDev)
                .isOutgoingHL7()
                .outgoingHL7Sender(msh.getSendingApplicationWithFacility())
                .outgoingHL7Receiver(msh.getReceivingApplicationWithFacility())
                .build();
    }

    AuditInfoBuilder getHL7OutgoingPrevPatInfo(HL7Segment mrg) {
        HL7Segment msh = hl7ConnEvent.getHL7Message().msh();
        return infoBuilder
                .patID(mrg.getField(1, null), arcDev)
                .patName(mrg.getField(7, null), arcDev)
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

    static AuditMessage auditMsg(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType,
                                 IHL7ApplicationCache hl7AppCache) throws ConfigurationException {
        SpoolFileReader reader = new SpoolFileReader(path.toFile());
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        ActiveParticipantBuilder[] activeParticipantBuilder = auditInfo.getField(AuditInfo.PDQ_SERVICE_URI) != null
                ? patVerActiveParticipants(auditLogger, eventType, auditInfo)
                : eventType.source == null
                    ? new ActiveParticipantBuilder[]{schedulerTriggered(auditLogger, auditInfo).build()}
                    : auditInfo.getField(AuditInfo.IS_OUTGOING_HL7) != null
                        ? outgoingActiveParticipants(auditLogger, eventType, auditInfo, hl7AppCache)
                        : incomingActiveParticipants(auditLogger, eventType, auditInfo);

        return AuditMessages.createMessage(
                EventID.toEventIdentification(auditLogger, path, eventType, auditInfo),
                activeParticipantBuilder,
                ParticipantObjectID.patientPOI(auditInfo, reader));
    }

    private static ActiveParticipantBuilder.Builder schedulerTriggered(AuditLogger auditLogger, AuditInfo auditInfo) {
        return new ActiveParticipantBuilder.Builder(
                auditInfo.getField(AuditInfo.CALLING_USERID),
                getLocalHostName(auditLogger))
                .userIDTypeCode(AuditMessages.UserIDTypeCode.DeviceName)
                .altUserID(AuditLogger.processID())
                .isRequester();
    }

    private static ActiveParticipantBuilder[] patVerActiveParticipants(
            AuditLogger auditLogger, AuditUtils.EventType et, AuditInfo auditInfo) {
        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[3];
        String calledUserID = auditInfo.getField(AuditInfo.CALLED_USERID);
        String callingUserID = auditInfo.getField(AuditInfo.CALLING_USERID);

        ActiveParticipantBuilder.Builder calledUser = calledUserID == null
                ? schedulerTriggered(auditLogger, auditInfo)
                : new ActiveParticipantBuilder.Builder(
                    calledUserID,
                    getLocalHostName(auditLogger))
                    .userIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle)
                    .altUserID(AuditLogger.processID());

        activeParticipantBuilder[0] = pdqService(et, auditInfo);
        activeParticipantBuilder[1] = calledUser.roleIDCode(et.destination).build();
        if (calledUserID != null)
            activeParticipantBuilder[2] = new ActiveParticipantBuilder.Builder(
                    callingUserID,
                    auditInfo.getField(AuditInfo.CALLING_HOST))
                    .userIDTypeCode(AuditMessages.userIDTypeCode(callingUserID))
                    .isRequester()
                    .build();

        return activeParticipantBuilder;
    }

    private static ActiveParticipantBuilder pdqService(AuditUtils.EventType et, AuditInfo auditInfo) {
        String pdqServiceURI = auditInfo.getField(AuditInfo.PDQ_SERVICE_URI);
        return new ActiveParticipantBuilder.Builder(
                pdqServiceURI, null)
                .userIDTypeCode(pdqServiceURI.indexOf('/') != -1
                        ? AuditMessages.UserIDTypeCode.URI : AuditMessages.UserIDTypeCode.StationAETitle)
                .roleIDCode(et.source)
                .build();
    }

    private static ActiveParticipantBuilder[] incomingActiveParticipants(
            AuditLogger auditLogger, AuditUtils.EventType et, AuditInfo auditInfo) {
        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[2];
        String archiveUserID = auditInfo.getField(AuditInfo.CALLED_USERID);
        String callingUserID = auditInfo.getField(AuditInfo.CALLING_USERID);
        AuditMessages.UserIDTypeCode archiveUserIDTypeCode = userIDTypeCode(archiveUserID);

        activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                archiveUserID,
                getLocalHostName(auditLogger))
                .userIDTypeCode(archiveUserIDTypeCode)
                .altUserID(AuditLogger.processID())
                .roleIDCode(et.destination)
                .build();
        activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(
                callingUserID,
                auditInfo.getField(AuditInfo.CALLING_HOST))
                .userIDTypeCode(AuditService.remoteUserIDTypeCode(archiveUserIDTypeCode, callingUserID))
                .isRequester()
                .roleIDCode(et.source)
                .build();
        return activeParticipantBuilder;
    }

    private static ActiveParticipantBuilder[] outgoingActiveParticipants(
            AuditLogger auditLogger, AuditUtils.EventType et, AuditInfo auditInfo, IHL7ApplicationCache hl7AppCache)
            throws ConfigurationException {
        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[4];
        HL7DeviceExtension hl7Dev = auditLogger.getDevice().getDeviceExtension(HL7DeviceExtension.class);

        String calledUserID = auditInfo.getField(AuditInfo.CALLED_USERID);
        String callingUserID = auditInfo.getField(AuditInfo.CALLING_USERID);

        String hl7SendingAppWithFacility = auditInfo.getField(AuditInfo.OUTGOING_HL7_SENDER);
        String hl7ReceivingAppWithFacility = auditInfo.getField(AuditInfo.OUTGOING_HL7_RECEIVER);

        HL7Application hl7AppSender = hl7Dev.getHL7Application(hl7SendingAppWithFacility, true);

        boolean isHL7Forward = hl7SendingAppWithFacility.equals(callingUserID)
                && hl7ReceivingAppWithFacility.equals(calledUserID);

        activeParticipantBuilder[0] = hl7Sender(et, hl7SendingAppWithFacility, hl7AppSender);
        activeParticipantBuilder[1] = hl7Receiver(et, hl7AppCache, hl7ReceivingAppWithFacility);
        if (isHL7Forward)
            activeParticipantBuilder[2] = new ActiveParticipantBuilder.Builder(
                    auditLogger.getDevice().getDeviceName(),
                    getLocalHostName(auditLogger))
                    .userIDTypeCode(AuditMessages.UserIDTypeCode.DeviceName)
                    .altUserID(AuditLogger.processID())
                    .isRequester()
                    .build();
        else {
            activeParticipantBuilder[2] = new ActiveParticipantBuilder.Builder(
                    callingUserID,
                    auditInfo.getField(AuditInfo.CALLING_HOST))
                    .userIDTypeCode(AuditMessages.userIDTypeCode(callingUserID))
                    .isRequester()
                    .build();
            activeParticipantBuilder[3] = new ActiveParticipantBuilder.Builder(
                    calledUserID,
                    getLocalHostName(auditLogger))
                    .userIDTypeCode(AuditMessages.UserIDTypeCode.URI)
                    .altUserID(AuditLogger.processID())
                    .build();
        }

        return activeParticipantBuilder;
    }

    private static ActiveParticipantBuilder hl7Receiver(
            AuditUtils.EventType et, IHL7ApplicationCache hl7AppCache, String hl7ReceivingAppWithFacility)
            throws ConfigurationException{
        HL7Application hl7AppReceiver = hl7AppCache.findHL7Application(hl7ReceivingAppWithFacility);
        return new ActiveParticipantBuilder.Builder(
                hl7ReceivingAppWithFacility,
                hl7AppReceiver.getConnections().get(0).getHostname())
                .userIDTypeCode(AuditMessages.UserIDTypeCode.ApplicationFacility)
                .roleIDCode(et.destination)
                .build();
    }

    private static ActiveParticipantBuilder hl7Sender(
            AuditUtils.EventType et, String hl7SendingAppWithFacility, HL7Application hl7AppSender) {
        return new ActiveParticipantBuilder.Builder(
                hl7SendingAppWithFacility,
                hl7AppSender.getConnections().get(0).getHostname())
                .userIDTypeCode(AuditMessages.UserIDTypeCode.ApplicationFacility)
                .roleIDCode(et.source)
                .build();
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
}