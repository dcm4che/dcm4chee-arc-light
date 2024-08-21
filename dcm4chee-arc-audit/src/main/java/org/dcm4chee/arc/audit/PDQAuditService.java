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
 * Portions created by the Initial Developer are Copyright (C) 2015-2021
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
import org.dcm4che3.conf.api.IWebApplicationCache;
import org.dcm4che3.conf.api.hl7.IHL7ApplicationCache;
import org.dcm4che3.data.Tag;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.WebApplication;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.HL7DeviceExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.pdq.PDQServiceContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jun 2021
 */
class PDQAuditService extends AuditService {

    static AuditInfo auditInfo(PDQServiceContext ctx, ArchiveDeviceExtension arcDev) {
        return ctx.getHttpServletRequestInfo() == null
                ? schedulerTriggeredPDQ(ctx, arcDev)
                : restfulTriggeredPDQ(ctx, arcDev);
    }

    static AuditInfo auditInfoFHIR(PDQServiceContext ctx, ArchiveDeviceExtension arcDev) {
        return ctx.getHttpServletRequestInfo() == null
                ? schedulerTriggeredFHIRPDQ(ctx, arcDev)
                : restfulTriggeredFHIRPDQ(ctx, arcDev);
    }

    private static AuditInfo schedulerTriggeredPDQ(PDQServiceContext ctx, ArchiveDeviceExtension arcDev) {
        return new AuditInfoBuilder.Builder()
                .callingUserID(arcDev.getDevice().getDeviceName())
                .outgoingHL7Sender(ctx.getSendingAppFacility())
                .outgoingHL7Receiver(ctx.getReceivingAppFacility())
                .queryPOID(ctx.getSearchMethod())
                .patID(ctx.getPatientID(), arcDev)
                .patName(patientName(ctx), arcDev)
                .outcome(outcome(ctx))
                .toAuditInfo();
    }

    private static AuditInfo restfulTriggeredPDQ(PDQServiceContext ctx, ArchiveDeviceExtension arcDev) {
        HttpServletRequestInfo httpRequest = ctx.getHttpServletRequestInfo();
        return new AuditInfoBuilder.Builder()
                .outgoingHL7Sender(ctx.getSendingAppFacility())
                .outgoingHL7Receiver(ctx.getReceivingAppFacility())
                .callingHost(httpRequest.requesterHost)
                .callingUserID(httpRequest.requesterUserID)
                .calledUserID(httpRequest.requestURI)
                .queryPOID(ctx.getSearchMethod())
                .queryString(httpRequest.queryString)
                .patID(ctx.getPatientID(), arcDev)
                .patName(patientName(ctx), arcDev)
                .outcome(outcome(ctx))
                .toAuditInfo();
    }

    private static AuditInfo schedulerTriggeredFHIRPDQ(PDQServiceContext ctx, ArchiveDeviceExtension arcDev) {
        return new AuditInfoBuilder.Builder()
                .callingUserID(arcDev.getDevice().getDeviceName())
                .queryString(ctx.getFhirQueryParams())
                .fhirWebAppName(ctx.getFhirWebAppName())
                .queryPOID(ctx.getSearchMethod())
                .patID(ctx.getPatientID(), arcDev)
                .patName(patientName(ctx), arcDev)
                .outcome(outcome(ctx))
                .toAuditInfo();
    }

    private static AuditInfo restfulTriggeredFHIRPDQ(PDQServiceContext ctx, ArchiveDeviceExtension arcDev) {
        HttpServletRequestInfo httpRequest = ctx.getHttpServletRequestInfo();
        return new AuditInfoBuilder.Builder()
                .callingHost(httpRequest.requesterHost)
                .callingUserID(httpRequest.requesterUserID)
                .calledUserID(httpRequest.requestURI)
                .queryPOID(ctx.getSearchMethod())
                .queryString(ctx.getFhirQueryParams())
                .fhirWebAppName(ctx.getFhirWebAppName())
                .patID(ctx.getPatientID(), arcDev)
                .patName(patientName(ctx), arcDev)
                .outcome(outcome(ctx))
                .toAuditInfo();
    }

    private static String patientName(PDQServiceContext ctx) {
        return ctx.getPatientAttrs() == null
                ? null
                : ctx.getPatientAttrs().getString(Tag.PatientName);
    }

    private static String outcome(PDQServiceContext ctx) {
        return ctx.getException() == null
                ? null
                : ctx.getException().getMessage();
    }

    static void auditHL7PDQ(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType,
                               IHL7ApplicationCache hl7AppCache, Device device)
            throws Exception {
        SpoolFileReader reader = new SpoolFileReader(path.toFile());
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        EventIdentification eventIdentification = getEventIdentification(auditInfo, eventType);
        byte[] data = reader.getData();
        HL7Segment msh = HL7Segment.parseMSH(data, data.length, new ParsePosition(0));
        ParticipantObjectIdentification pdq = hl7PDQQuery(auditInfo, msh, data);
        ParticipantObjectIdentification patient = hl7PDQPatient(auditInfo, reader);

        List<ActiveParticipant> activeParticipants = new ArrayList<>();
        activeParticipants.add(hl7PDQConsumer(auditInfo, eventType, device));
        activeParticipants.add(hl7PDQSupplier(auditInfo, eventType, hl7AppCache));
        String archiveCalledUserID = auditInfo.getField(AuditInfo.CALLED_USERID);
        if (archiveCalledUserID == null) {
            activeParticipants.add(archive(
                    auditInfo.getField(AuditInfo.CALLING_USERID),
                    AuditMessages.UserIDTypeCode.DeviceName,
                    eventType,
                    auditLogger));
            emitAuditMessage(auditLogger, eventIdentification, activeParticipants, patient, pdq);
            return;
        }

        String queryString = auditInfo.getField(AuditInfo.Q_STRING);
        if (queryString != null)
            archiveCalledUserID += "?" + queryString;
        activeParticipants.add(archive(archiveCalledUserID, AuditMessages.UserIDTypeCode.URI, eventType, auditLogger));
        activeParticipants.add(requestor(auditInfo));
        emitAuditMessage(auditLogger, eventIdentification, activeParticipants, patient, pdq);
    }

    static void auditFHIRPDQ(
            AuditLogger auditLogger, Path path, AuditUtils.EventType eventType, IWebApplicationCache webAppCache)
            throws Exception {
        SpoolFileReader reader = new SpoolFileReader(path.toFile());
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        EventIdentification eventIdentification = getEventIdentification(auditInfo, eventType);
        ParticipantObjectIdentification pdq = fhirPDQQuery(auditInfo);
        ParticipantObjectIdentification patient = fhirPDQPatient(auditInfo);

        String archiveCalledUserID = auditInfo.getField(AuditInfo.CALLED_USERID);
        List<ActiveParticipant> activeParticipants = new ArrayList<>();
        activeParticipants.add(fhirPDQ(auditInfo, eventType, webAppCache));
        if (archiveCalledUserID == null)
            activeParticipants.add(archive(
                    auditInfo.getField(AuditInfo.CALLING_USERID),
                    AuditMessages.UserIDTypeCode.DeviceName,
                    eventType,
                    auditLogger));
        else {
            activeParticipants.add(archive(archiveCalledUserID, AuditMessages.UserIDTypeCode.URI, eventType, auditLogger));
            activeParticipants.add(requestor(auditInfo));
        }

        emitAuditMessage(auditLogger, eventIdentification, activeParticipants, patient, pdq);
    }

    private static ActiveParticipant fhirPDQ(
            AuditInfo auditInfo, AuditUtils.EventType eventType, IWebApplicationCache webAppCache) throws Exception {
        WebApplication fhirWebApp = webAppCache.findWebApplication(auditInfo.getField(AuditInfo.FHIR_WEB_APP_NAME));
        ActiveParticipant fhirPDQ = new ActiveParticipant();
        fhirPDQ.setUserID(fhirWebApp.getServiceURL().toString());
        fhirPDQ.setUserIDTypeCode(AuditMessages.UserIDTypeCode.URI);
        fhirPDQ.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        fhirPDQ.setNetworkAccessPointID(fhirWebApp.getConnections().get(0).getHostname());
        fhirPDQ.getRoleIDCode().add(eventType.destination);
        return fhirPDQ;
    }

    private static ActiveParticipant archive(
            String archiveUserID, AuditMessages.UserIDTypeCode archiveUserIDTypeCode, AuditUtils.EventType eventType,
            AuditLogger auditLogger) {
        ActiveParticipant archive = new ActiveParticipant();
        archive.setUserID(archiveUserID);
        archive.setAlternativeUserID(AuditLogger.processID());
        archive.setUserIsRequestor(archiveUserIDTypeCode == AuditMessages.UserIDTypeCode.DeviceName);
        archive.setUserIDTypeCode(archiveUserIDTypeCode);
        archive.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        archive.getRoleIDCode().add(eventType.source);

        String auditLoggerHostName = auditLogger.getConnections().get(0).getHostname();
        archive.setNetworkAccessPointID(auditLoggerHostName);
        archive.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(auditLoggerHostName)
                        ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                        : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        return archive;
    }

    private static ActiveParticipant requestor(AuditInfo auditInfo) {
        ActiveParticipant requestor = new ActiveParticipant();
        String requestorID = auditInfo.getField(AuditInfo.CALLING_USERID);
        requestor.setUserID(requestorID);
        requestor.setUserIsRequestor(true);
        requestor.setUserIDTypeCode(AuditMessages.isIP(requestorID)
                ? AuditMessages.UserIDTypeCode.NodeID
                : AuditMessages.UserIDTypeCode.PersonID);
        requestor.setUserTypeCode(AuditMessages.UserTypeCode.Person);

        String requestorHost = auditInfo.getField(AuditInfo.CALLING_HOST);
        requestor.setNetworkAccessPointID(requestorHost);
        requestor.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(requestorHost)
                        ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                        : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        return requestor;
    }

    private static ActiveParticipant hl7PDQSupplier(
            AuditInfo auditInfo, AuditUtils.EventType eventType, IHL7ApplicationCache hl7AppCache) throws Exception {
        String hl7ReceivingAppWithFacility = auditInfo.getField(AuditInfo.OUTGOING_HL7_RECEIVER);
        HL7Application hl7AppReceiver = hl7AppCache.findHL7Application(hl7ReceivingAppWithFacility);
        ActiveParticipant hl7PDQSupplier = new ActiveParticipant();
        hl7PDQSupplier.setUserID(hl7ReceivingAppWithFacility);
        hl7PDQSupplier.setUserIDTypeCode(AuditMessages.UserIDTypeCode.ApplicationFacility);
        hl7PDQSupplier.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        hl7PDQSupplier.setNetworkAccessPointID(hl7AppReceiver.getConnections().get(0).getHostname());
        hl7PDQSupplier.getRoleIDCode().add(eventType.destination);
        return hl7PDQSupplier;
    }

    private static ActiveParticipant hl7PDQConsumer(AuditInfo auditInfo, AuditUtils.EventType eventType, Device device) {
        ActiveParticipant hl7PDQConsumer = new ActiveParticipant();
        String hl7SendingAppWithFacility = auditInfo.getField(AuditInfo.OUTGOING_HL7_SENDER);
        HL7Application sender = device.getDeviceExtension(HL7DeviceExtension.class)
                .getHL7Application(hl7SendingAppWithFacility, true);
        hl7PDQConsumer.setUserID(hl7SendingAppWithFacility);
        hl7PDQConsumer.setAlternativeUserID(AuditLogger.processID());
        hl7PDQConsumer.setUserIDTypeCode(AuditMessages.UserIDTypeCode.ApplicationFacility);
        hl7PDQConsumer.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        hl7PDQConsumer.getRoleIDCode().add(eventType.source);
        String senderHostName = sender.getConnections().get(0).getHostname();
        hl7PDQConsumer.setNetworkAccessPointID(senderHostName);
        hl7PDQConsumer.setNetworkAccessPointTypeCode(
                AuditMessages.isIP(senderHostName)
                        ? AuditMessages.NetworkAccessPointTypeCode.IPAddress
                        : AuditMessages.NetworkAccessPointTypeCode.MachineName);
        return hl7PDQConsumer;
    }

    private static ParticipantObjectIdentification fhirPDQQuery(AuditInfo auditInfo) {
        ParticipantObjectIdentification pdq = new ParticipantObjectIdentification();
        pdq.setParticipantObjectID(auditInfo.getField(AuditInfo.Q_POID));
        pdq.setParticipantObjectIDTypeCode(AuditMessages.ParticipantObjectIDTypeCode.ITI_MobilePatientDemographicsQuery);
        pdq.setParticipantObjectTypeCode(AuditMessages.ParticipantObjectTypeCode.SystemObject);
        pdq.setParticipantObjectTypeCodeRole(AuditMessages.ParticipantObjectTypeCodeRole.Query);
        pdq.getParticipantObjectDetail()
                .add(AuditMessages.createParticipantObjectDetail("QueryEncoding", StandardCharsets.UTF_8.name()));
        pdq.setParticipantObjectQuery(auditInfo.getField(AuditInfo.Q_STRING).getBytes(StandardCharsets.UTF_8));
        return pdq;
    }

    private static ParticipantObjectIdentification fhirPDQPatient(AuditInfo auditInfo) {
        ParticipantObjectIdentification patient = new ParticipantObjectIdentification();
        patient.setParticipantObjectID(auditInfo.getField(AuditInfo.P_ID));
        patient.setParticipantObjectIDTypeCode(AuditMessages.ParticipantObjectIDTypeCode.PatientNumber);
        patient.setParticipantObjectTypeCode(AuditMessages.ParticipantObjectTypeCode.Person);
        patient.setParticipantObjectTypeCodeRole(AuditMessages.ParticipantObjectTypeCodeRole.Patient);
        patient.setParticipantObjectName(auditInfo.getField(AuditInfo.P_NAME));
        return patient;
    }

    private static EventIdentification getEventIdentification(AuditInfo auditInfo, AuditUtils.EventType eventType) {
        String patientName = auditInfo.getField(AuditInfo.P_NAME);
        String outcome = auditInfo.getField(AuditInfo.OUTCOME);
        String outcomeDesc = outcome == null
                                ? patientName == null
                                    ? "Querying the PDQ Service for patient identifier was unsuccessful"
                                    : null
                                : outcome;

        EventIdentification ei = new EventIdentification();
        ei.setEventID(eventType.eventID);
        ei.setEventActionCode(eventType.eventActionCode);
        ei.setEventOutcomeDescription(outcomeDesc);
        ei.setEventOutcomeIndicator(outcomeDesc == null
                ? AuditMessages.EventOutcomeIndicator.Success
                : AuditMessages.EventOutcomeIndicator.MinorFailure);

        ei.getEventTypeCode().add(eventType.eventTypeCode);
        return ei;
    }

    private static ParticipantObjectIdentification hl7PDQQuery(AuditInfo auditInfo, HL7Segment msh, byte[] data) {
        ParticipantObjectIdentification pdq = new ParticipantObjectIdentification();
        pdq.setParticipantObjectID(auditInfo.getField(AuditInfo.Q_POID));
        pdq.setParticipantObjectIDTypeCode(AuditMessages.ParticipantObjectIDTypeCode.ITI_PatientDemographicsQuery);
        pdq.setParticipantObjectTypeCode(AuditMessages.ParticipantObjectTypeCode.SystemObject);
        pdq.setParticipantObjectTypeCodeRole(AuditMessages.ParticipantObjectTypeCodeRole.Query);
        pdq.getParticipantObjectDetail()
                .add(AuditMessages.createParticipantObjectDetail("MSH-10", msh.getMessageControlID()));
        pdq.setParticipantObjectQuery(data);
        return pdq;
    }

    private static ParticipantObjectIdentification hl7PDQPatient(AuditInfo auditInfo, SpoolFileReader reader) {
        ParticipantObjectIdentification patient = new ParticipantObjectIdentification();
        patient.setParticipantObjectID(auditInfo.getField(AuditInfo.P_ID));
        patient.setParticipantObjectIDTypeCode(AuditMessages.ParticipantObjectIDTypeCode.PatientNumber);
        patient.setParticipantObjectTypeCode(AuditMessages.ParticipantObjectTypeCode.Person);
        patient.setParticipantObjectTypeCodeRole(AuditMessages.ParticipantObjectTypeCodeRole.Patient);
        patient.setParticipantObjectName(auditInfo.getField(AuditInfo.P_NAME));
        patient.getParticipantObjectDetail().addAll(hl7ParticipantObjectDetails(reader));
        return patient;
    }

    static List<ParticipantObjectDetail> hl7ParticipantObjectDetails(SpoolFileReader reader) {
        List<ParticipantObjectDetail> details = new ArrayList<>();
        hl7ParticipantObjectDetails(details, reader.getData());
        hl7ParticipantObjectDetails(details, reader.getAck());
        return details;
    }

    private static void hl7ParticipantObjectDetails(List<ParticipantObjectDetail> details, byte[] data) {
        HL7Segment msh = HL7Segment.parseMSH(data, data.length, new ParsePosition(0));
        String messageType = msh.getMessageType();
        String messageControlID = msh.getMessageControlID();
        details.add(AuditMessages.toParticipantObjectDetail("HL7v2 Message", data));
        if (messageType != null)
            details.add(AuditMessages.createParticipantObjectDetail("MSH-9", messageType));
        if (messageControlID != null)
            details.add(AuditMessages.createParticipantObjectDetail("MSH-10", messageControlID));
    }
}
