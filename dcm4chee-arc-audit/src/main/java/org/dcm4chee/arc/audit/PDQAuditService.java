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
import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.IWebApplicationCache;
import org.dcm4che3.conf.api.hl7.IHL7ApplicationCache;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.WebApplication;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.pdq.PDQServiceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jun 2021
 */
class PDQAuditService {

    private final static Logger LOG = LoggerFactory.getLogger(PDQAuditService.class);

    static AuditInfoBuilder auditInfo(PDQServiceContext ctx, ArchiveDeviceExtension arcDev) {
        return ctx.getHttpServletRequestInfo() == null
                ? schedulerTriggeredPDQ(ctx, arcDev)
                : restfulTriggeredPDQ(ctx, arcDev);
    }

    static AuditInfo auditInfoFHIR(PDQServiceContext ctx, ArchiveDeviceExtension arcDev) {
        return ctx.getHttpServletRequestInfo() == null
                ? schedulerTriggeredFHIRPDQ(ctx, arcDev)
                : restfulTriggeredFHIRPDQ(ctx, arcDev);
    }

    private static AuditInfoBuilder schedulerTriggeredPDQ(PDQServiceContext ctx, ArchiveDeviceExtension arcDev) {
        return new AuditInfoBuilder.Builder()
                .outgoingHL7Sender(ctx.getSendingAppFacility())
                .outgoingHL7Receiver(ctx.getReceivingAppFacility())
                .queryPOID(ctx.getSearchMethod())
                .patID(ctx.getPatientID(), arcDev)
                .patName(ctx.getPatientAttrs().getString(Tag.PatientName), arcDev)
                .build();
    }

    private static AuditInfoBuilder restfulTriggeredPDQ(PDQServiceContext ctx, ArchiveDeviceExtension arcDev) {
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
                .patName(ctx.getPatientAttrs().getString(Tag.PatientName), arcDev)
                .build();
    }

    private static AuditInfo schedulerTriggeredFHIRPDQ(PDQServiceContext ctx, ArchiveDeviceExtension arcDev) {
        return new AuditInfoBuilder.Builder()
                .callingUserID(arcDev.getDevice().getDeviceName())
                .queryString(ctx.getFhirQueryParams())
                .fhirWebAppName(ctx.getFhirWebAppName())
                .queryPOID(ctx.getSearchMethod())
                .patID(ctx.getPatientID(), arcDev)
                .patName(ctx.getPatientAttrs().getString(Tag.PatientName), arcDev)
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
                .patName(ctx.getPatientAttrs().getString(Tag.PatientName), arcDev)
                .toAuditInfo();
    }

    static AuditMessage auditMsg(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType,
                                    IHL7ApplicationCache hl7AppCache, IWebApplicationCache webAppCache)
            throws Exception {
        if (eventType.eventTypeCode == AuditMessages.EventTypeCode.ITI_78_MobilePDQ)
            return auditMsg(auditLogger, path, eventType, webAppCache);

        SpoolFileReader reader = new SpoolFileReader(path.toFile());
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        EventIdentification ei = EventID.toEventIdentification(auditLogger, path, eventType, auditInfo);
        List<ActiveParticipant> activeParticipants = activeParticipants(auditLogger, auditInfo, hl7AppCache, eventType);
        List<ParticipantObjectIdentification> participantObjects = participantObjects(auditInfo, reader);

        return AuditMessages.createMessage(ei, activeParticipants.toArray(new ActiveParticipant[0]),
                participantObjects.toArray(new ParticipantObjectIdentification[0]));
    }

    private static List<ParticipantObjectIdentification> participantObjects(AuditInfo auditInfo, SpoolFileReader reader) {
        List<ParticipantObjectIdentification> participantObjects = new ArrayList<>();
        ParticipantObjectIdentificationBuilder pdq = new ParticipantObjectIdentificationBuilder(
                auditInfo.getField(AuditInfo.Q_POID),
                AuditMessages.ParticipantObjectIDTypeCode.ITI_PatientDemographicsQuery,
                AuditMessages.ParticipantObjectTypeCode.SystemObject,
                AuditMessages.ParticipantObjectTypeCodeRole.Query);
        if (auditInfo.getField(AuditInfo.Q_STRING) != null)
            pdq.detail(AuditMessages.createParticipantObjectDetail(
                    "QueryString", auditInfo.getField(AuditInfo.Q_STRING)));
        pdq.query(reader.getData());
        participantObjects.add(pdq.build());
        participantObjects.add(ParticipantObjectID.patientPOI(auditInfo, reader));
        return participantObjects;
    }

    private static List<ActiveParticipant> activeParticipants(
            AuditLogger auditLogger, AuditInfo auditInfo, IHL7ApplicationCache hl7AppCache, AuditUtils.EventType eventType)
        throws ConfigurationException {
        List<ActiveParticipant> activeParticipants = new ArrayList<>();
        String hl7ReceivingAppWithFacility = auditInfo.getField(AuditInfo.OUTGOING_HL7_RECEIVER);
        HL7Application hl7AppReceiver = hl7AppCache.findHL7Application(hl7ReceivingAppWithFacility);
        activeParticipants.add(new ActiveParticipantBuilder(
                hl7ReceivingAppWithFacility,
                hl7AppReceiver.getConnections().get(0).getHostname())
                .userIDTypeCode(AuditMessages.UserIDTypeCode.ApplicationFacility)
                .roleIDCode(eventType.destination)
                .build());
        String callingUser = auditInfo.getField(AuditInfo.CALLING_USERID);
        if (callingUser != null) {
            activeParticipants.add(new ActiveParticipantBuilder(
                    callingUser,
                    auditInfo.getField(AuditInfo.CALLING_HOST))
                    .userIDTypeCode(AuditMessages.userIDTypeCode(callingUser))
                    .isRequester()
                    .build());
            activeParticipants.add(new ActiveParticipantBuilder(
                    auditInfo.getField(AuditInfo.CALLED_USERID),
                    getLocalHostName(auditLogger))
                    .userIDTypeCode(AuditMessages.UserIDTypeCode.URI)
                    .build());
            activeParticipants.add(new ActiveParticipantBuilder(
                    auditInfo.getField(AuditInfo.OUTGOING_HL7_SENDER),
                    getLocalHostName(auditLogger))
                    .userIDTypeCode(AuditMessages.UserIDTypeCode.ApplicationFacility)
                    .roleIDCode(eventType.source)
                    .build());
        } else {
            activeParticipants.add(new ActiveParticipantBuilder(
                    auditInfo.getField(AuditInfo.OUTGOING_HL7_SENDER),
                    getLocalHostName(auditLogger))
                    .userIDTypeCode(AuditMessages.UserIDTypeCode.ApplicationFacility)
                    .isRequester()
                    .roleIDCode(eventType.source)
                    .build());
        }

        return activeParticipants;
    }

    private static AuditMessage auditMsg(
            AuditLogger auditLogger, Path path, AuditUtils.EventType eventType, IWebApplicationCache webAppCache)
            throws Exception {
        SpoolFileReader reader = new SpoolFileReader(path.toFile());
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        EventIdentification ei = EventID.toEventIdentification(auditLogger, path, eventType, auditInfo);
        List<ActiveParticipant> activeParticipants = activeParticipants(auditLogger, auditInfo, eventType, webAppCache);
        List<ParticipantObjectIdentification> participantObjects = participantObjects(auditInfo);

        return AuditMessages.createMessage(ei, activeParticipants.toArray(new ActiveParticipant[0]),
                participantObjects.toArray(new ParticipantObjectIdentification[0]));
    }

    private static List<ActiveParticipant> activeParticipants(
            AuditLogger auditLogger, AuditInfo auditInfo, AuditUtils.EventType eventType, IWebApplicationCache webAppCache)
            throws ConfigurationException {
        List<ActiveParticipant> activeParticipants = new ArrayList<>();
        WebApplication fhirWebApp = webAppCache.findWebApplication(auditInfo.getField(AuditInfo.FHIR_WEB_APP_NAME));
        activeParticipants.add(new ActiveParticipantBuilder(
                fhirWebApp.getServiceURL().toString(),
                fhirWebApp.getConnections().get(0).getHostname())
                .userIDTypeCode(AuditMessages.UserIDTypeCode.URI)
                .roleIDCode(eventType.destination)
                .build());
        String calledUser = auditInfo.getField(AuditInfo.CALLED_USERID);
        if (calledUser != null) {
            activeParticipants.add(new ActiveParticipantBuilder(
                    auditInfo.getField(AuditInfo.CALLING_USERID),
                    auditInfo.getField(AuditInfo.CALLING_HOST))
                    .userIDTypeCode(AuditMessages.userIDTypeCode(auditInfo.getField(AuditInfo.CALLING_USERID)))
                    .isRequester()
                    .build());
            activeParticipants.add(new ActiveParticipantBuilder(
                    auditInfo.getField(AuditInfo.CALLED_USERID),
                    getLocalHostName(auditLogger))
                    .userIDTypeCode(AuditMessages.UserIDTypeCode.URI)
                    .roleIDCode(eventType.source)
                    .build());
        } else {
            activeParticipants.add(new ActiveParticipantBuilder(
                    auditInfo.getField(AuditInfo.CALLING_USERID),
                    getLocalHostName(auditLogger))
                    .userIDTypeCode(AuditMessages.UserIDTypeCode.DeviceName)
                    .roleIDCode(eventType.source)
                    .isRequester()
                    .build());
        }
        return activeParticipants;
    }

    private static List<ParticipantObjectIdentification> participantObjects(AuditInfo auditInfo) {
        List<ParticipantObjectIdentification> participantObjects = new ArrayList<>();
        participantObjects.add(new ParticipantObjectIdentificationBuilder(
                                auditInfo.getField(AuditInfo.Q_POID),
                                AuditMessages.ParticipantObjectIDTypeCode.ITI_MobilePatientDemographicsQuery,
                                AuditMessages.ParticipantObjectTypeCode.SystemObject,
                                AuditMessages.ParticipantObjectTypeCodeRole.Query)
                                .detail(AuditMessages.createParticipantObjectDetail("QueryEncoding", StandardCharsets.UTF_8.name()))
                                .query(auditInfo.getField(AuditInfo.Q_STRING).getBytes(StandardCharsets.UTF_8))
                                .build());
        participantObjects.add(ParticipantObjectID.patientPOIBuilder(auditInfo).build());
        return participantObjects;
    }

    private static String getLocalHostName(AuditLogger auditLogger) {
        return auditLogger.getConnections().get(0).getHostname();
    }
}
