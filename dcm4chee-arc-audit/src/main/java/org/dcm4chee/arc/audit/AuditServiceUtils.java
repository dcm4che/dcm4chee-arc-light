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
import org.dcm4che3.data.UID;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.store.StoreContext;
import org.keycloak.KeycloakSecurityContext;
import org.keycloak.adapters.RefreshableKeycloakSecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Feb 2016
 */
public class AuditServiceUtils {
    private static final Logger LOG = LoggerFactory.getLogger(AuditService.class);
    static final String noValue = "<none>";
    static final String keycloakClassName = "org.keycloak.KeycloakSecurityContext";
    static final String studyDate = "StudyDate";

    enum EventClass {
        QUERY, DELETE, PERM_DELETE, STORE_WADOR, CONN_REJECT, RETRIEVE, APPLN_ACTIVITY, HL7
    }
    enum EventType {
        ITRF_WAD_P(EventClass.STORE_WADOR, AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read, AuditMessages.EventOutcomeIndicator.Success,
                AuditMessages.RoleIDCode.Destination, AuditMessages.RoleIDCode.Source, true, false, false, null),
        ITRF_WAD_E(EventClass.STORE_WADOR, AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read, AuditMessages.EventOutcomeIndicator.MinorFailure,
                AuditMessages.RoleIDCode.Destination, AuditMessages.RoleIDCode.Source, true, false, false, null),
        ITRF_S_C_P(EventClass.STORE_WADOR, AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Create, AuditMessages.EventOutcomeIndicator.Success,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, true, false, false, null),
        ITRF_S_C_E(EventClass.STORE_WADOR, AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Create, AuditMessages.EventOutcomeIndicator.MinorFailure,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, true, false, false, null),
        ITRF_S_U_P(EventClass.STORE_WADOR, AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Update, AuditMessages.EventOutcomeIndicator.Success,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, true, false, false, null),
        ITRF_S_U_E(EventClass.STORE_WADOR, AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Update, AuditMessages.EventOutcomeIndicator.MinorFailure,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, true, false, false, null),

        RTRV_B_M_P(EventClass.RETRIEVE, AuditMessages.EventID.BeginTransferringDICOMInstances, AuditMessages.EventActionCode.Execute, AuditMessages.EventOutcomeIndicator.Success,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, false, true, null),
        RTRV_B_M_E(EventClass.RETRIEVE, AuditMessages.EventID.BeginTransferringDICOMInstances, AuditMessages.EventActionCode.Execute, AuditMessages.EventOutcomeIndicator.MinorFailure,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, false, true, null),
        RTRV_B_G_P(EventClass.RETRIEVE, AuditMessages.EventID.BeginTransferringDICOMInstances, AuditMessages.EventActionCode.Execute, AuditMessages.EventOutcomeIndicator.Success,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, true, false, null),
        RTRV_B_G_E(EventClass.RETRIEVE, AuditMessages.EventID.BeginTransferringDICOMInstances, AuditMessages.EventActionCode.Execute, AuditMessages.EventOutcomeIndicator.MinorFailure,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, true, false, null),
        RTRV_B_E_P(EventClass.RETRIEVE, AuditMessages.EventID.BeginTransferringDICOMInstances, AuditMessages.EventActionCode.Execute, AuditMessages.EventOutcomeIndicator.Success,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, true, false, false, null),
        RTRV_B_E_E(EventClass.RETRIEVE, AuditMessages.EventID.BeginTransferringDICOMInstances, AuditMessages.EventActionCode.Execute, AuditMessages.EventOutcomeIndicator.MinorFailure,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, true, false, false, null),
        RTRV_T_M_P(EventClass.RETRIEVE, AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read, AuditMessages.EventOutcomeIndicator.Success,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, false, true, null),
        RTRV_T_M_E(EventClass.RETRIEVE, AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read, AuditMessages.EventOutcomeIndicator.MinorFailure,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, false, true, null),
        RTRV_T_G_P(EventClass.RETRIEVE, AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read, AuditMessages.EventOutcomeIndicator.Success,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, true, false, null),
        RTRV_T_G_E(EventClass.RETRIEVE, AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read, AuditMessages.EventOutcomeIndicator.MinorFailure,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, true, false, null),
        RTRV_T_E_P(EventClass.RETRIEVE, AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read, AuditMessages.EventOutcomeIndicator.Success,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, true, false, false, null),
        RTRV_T_E_E(EventClass.RETRIEVE, AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read, AuditMessages.EventOutcomeIndicator.MinorFailure,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, true, false, false, null),
        RTRV_B_W_P(EventClass.RETRIEVE, AuditMessages.EventID.BeginTransferringDICOMInstances, AuditMessages.EventActionCode.Execute, AuditMessages.EventOutcomeIndicator.Success,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, true, false, null),
        RTRV_B_W_E(EventClass.RETRIEVE, AuditMessages.EventID.BeginTransferringDICOMInstances, AuditMessages.EventActionCode.Execute, AuditMessages.EventOutcomeIndicator.MinorFailure,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, true, false, null),
        RTRV_T_W_P(EventClass.RETRIEVE, AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read, AuditMessages.EventOutcomeIndicator.Success,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, true, false, null),
        RTRV_T_W_E(EventClass.RETRIEVE, AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read, AuditMessages.EventOutcomeIndicator.MinorFailure,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, true, false, null),

        DELETE_PAS(EventClass.DELETE, AuditMessages.EventID.DICOMInstancesAccessed, AuditMessages.EventActionCode.Delete, AuditMessages.EventOutcomeIndicator.Success,
                null, null, true, false, false, null),
        DELETE_ERR(EventClass.DELETE, AuditMessages.EventID.DICOMInstancesAccessed, AuditMessages.EventActionCode.Delete, AuditMessages.EventOutcomeIndicator.MinorFailure,
                null, null, true, false, false, null),

        PERM_DEL_E(EventClass.PERM_DELETE, AuditMessages.EventID.DICOMStudyDeleted, AuditMessages.EventActionCode.Delete, AuditMessages.EventOutcomeIndicator.MinorFailure,
                null, null, false, false, false, null),
        PERM_DEL_S(EventClass.PERM_DELETE, AuditMessages.EventID.DICOMStudyDeleted, AuditMessages.EventActionCode.Delete, AuditMessages.EventOutcomeIndicator.Success,
                null, null, false, false, false, null),

        APPLNSTART(EventClass.APPLN_ACTIVITY, AuditMessages.EventID.ApplicationActivity, AuditMessages.EventActionCode.Execute, AuditMessages.EventOutcomeIndicator.Success,
                null, null, false, false, false, AuditMessages.EventTypeCode.ApplicationStart),
        APPLN_STOP(EventClass.APPLN_ACTIVITY, AuditMessages.EventID.ApplicationActivity, AuditMessages.EventActionCode.Execute, AuditMessages.EventOutcomeIndicator.Success,
                null, null, false, false, false, AuditMessages.EventTypeCode.ApplicationStop),

        QUERY_QIDO(EventClass.QUERY, AuditMessages.EventID.Query, AuditMessages.EventActionCode.Execute, AuditMessages.EventOutcomeIndicator.Success,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, true, false, false, null),
        QUERY_FIND(EventClass.QUERY, AuditMessages.EventID.Query, AuditMessages.EventActionCode.Execute, AuditMessages.EventOutcomeIndicator.Success,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, true, false, false, null),

        CONN__RJCT(EventClass.CONN_REJECT, AuditMessages.EventID.SecurityAlert, AuditMessages.EventActionCode.Execute,
                AuditMessages.EventOutcomeIndicator.MinorFailure, null, null, false, false, false, AuditMessages.EventTypeCode.NodeAuthentication),

        HL7_CREA_P(EventClass.HL7, AuditMessages.EventID.PatientRecord, AuditMessages.EventActionCode.Create,
                AuditMessages.EventOutcomeIndicator.Success, AuditMessages.RoleIDCode.Source,
                AuditMessages.RoleIDCode.Destination, true, false, false,
                AuditMessages.EventTypeCode.ITI_8_PatientIdentityFeed),
        HL7_CREA_E(EventClass.HL7, AuditMessages.EventID.PatientRecord, AuditMessages.EventActionCode.Create,
                AuditMessages.EventOutcomeIndicator.MinorFailure, AuditMessages.RoleIDCode.Source,
                AuditMessages.RoleIDCode.Destination, true, false, false,
                AuditMessages.EventTypeCode.ITI_8_PatientIdentityFeed),
        HL7_UPDA_P(EventClass.HL7, AuditMessages.EventID.PatientRecord, AuditMessages.EventActionCode.Update,
                AuditMessages.EventOutcomeIndicator.Success, AuditMessages.RoleIDCode.Source,
                AuditMessages.RoleIDCode.Destination, true, false, false,
                AuditMessages.EventTypeCode.ITI_8_PatientIdentityFeed),
        HL7_UPDA_E(EventClass.HL7, AuditMessages.EventID.PatientRecord, AuditMessages.EventActionCode.Update,
                AuditMessages.EventOutcomeIndicator.MinorFailure, AuditMessages.RoleIDCode.Source,
                AuditMessages.RoleIDCode.Destination, true, false, false,
                AuditMessages.EventTypeCode.ITI_8_PatientIdentityFeed),
        HL7_DELT_P(EventClass.HL7, AuditMessages.EventID.PatientRecord, AuditMessages.EventActionCode.Delete,
                AuditMessages.EventOutcomeIndicator.Success,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, true, false, false,
                AuditMessages.EventTypeCode.ITI_8_PatientIdentityFeed),
        HL7_DELT_E(EventClass.HL7, AuditMessages.EventID.PatientRecord, AuditMessages.EventActionCode.Delete,
                AuditMessages.EventOutcomeIndicator.MinorFailure,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, true, false, false,
                AuditMessages.EventTypeCode.ITI_8_PatientIdentityFeed);


        final EventClass eventClass;
        final AuditMessages.EventID eventID;
        final String eventActionCode;
        final String outcomeIndicator;
        final AuditMessages.RoleIDCode source;
        final AuditMessages.RoleIDCode destination;
        final boolean isSource;
        final boolean isDest;
        final boolean isOther;
        final EventTypeCode eventTypeCode;


        EventType(EventClass eventClass, AuditMessages.EventID eventID, String eventActionCode, String outcome, AuditMessages.RoleIDCode source,
                  AuditMessages.RoleIDCode destination, boolean isSource, boolean isDest, boolean isOther, EventTypeCode etc) {
            this.eventClass = eventClass;
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

        static EventType forQuery(QueryContext ctx) {
            return (ctx.getHttpRequest() != null) ? QUERY_QIDO : QUERY_FIND;
        }

        static EventType forWADORetrieve(RetrieveContext ctx) {
            return ctx.getException() != null ? ITRF_WAD_E : ITRF_WAD_P;
        }

        static EventType forInstanceStored(StoreContext ctx) {
            return ctx.getException() != null
                    ? ctx.getPreviousInstance() != null ? ITRF_S_U_E : ITRF_S_C_E
                    : ctx.getLocation() != null
                    ? ctx.getPreviousInstance() != null ? ITRF_S_U_P : ITRF_S_C_P
                    : null;
        }

        static HashSet<EventType> forBeginTransfer(RetrieveContext ctx) {
            HashSet<EventType> eventType = new HashSet<>();
            if (null != ctx.getException())
                eventType.add(ctx.isLocalRequestor() ? RTRV_B_E_E
                        : !ctx.isDestinationRequestor() && !ctx.isLocalRequestor() ? RTRV_B_M_E
                        : null != ctx.getRequestAssociation() && null != ctx.getStoreAssociation()
                            && ctx.isDestinationRequestor() ? RTRV_B_G_E
                        : null != ctx.getHttpRequest() ? RTRV_B_W_E : null);
            if (ctx.getException() == null)
                eventType.add(ctx.isLocalRequestor() ? RTRV_B_E_P
                        : !ctx.isDestinationRequestor() && !ctx.isLocalRequestor() ? RTRV_B_M_P
                        : null != ctx.getRequestAssociation() && null != ctx.getStoreAssociation()
                        && ctx.isDestinationRequestor()
                        ? RTRV_B_G_P : null != ctx.getHttpRequest() ? RTRV_B_W_P : null);
            return eventType;
        }

        static HashSet<EventType> forDicomInstTransferred(RetrieveContext ctx) {
            HashSet<EventType> eventType = new HashSet<>();
            if (ctx.failedSOPInstanceUIDs().length != ctx.getMatches().size()) {
                if (null != ctx.getException() || ctx.failedSOPInstanceUIDs().length > 0) {
                    eventType.add(getDicomInstTrfdErrorEventType(ctx));
                }
                if (ctx.getException() == null || ctx.failedSOPInstanceUIDs().length > 0) {
                    eventType.add(ctx.isLocalRequestor() ? RTRV_T_E_P
                            : !ctx.isDestinationRequestor() && !ctx.isLocalRequestor() ? RTRV_T_M_P
                            : null != ctx.getRequestAssociation() && null != ctx.getStoreAssociation()
                            && ctx.isDestinationRequestor()
                            ? RTRV_T_G_P : null != ctx.getHttpRequest() ? RTRV_T_W_P : null);
                }
            }
            else
                eventType.add(getDicomInstTrfdErrorEventType(ctx));
            return eventType;
        }

        static EventType getDicomInstTrfdErrorEventType(RetrieveContext ctx) {
            return ctx.isLocalRequestor() ? RTRV_T_E_E
                    : !ctx.isDestinationRequestor() && !ctx.isLocalRequestor() ? RTRV_T_M_E
                    : null != ctx.getRequestAssociation() && null != ctx.getStoreAssociation()
                    && ctx.isDestinationRequestor() ? RTRV_T_G_E
                    : null != ctx.getHttpRequest() ? RTRV_T_W_E : null;
        }

        static HashSet<EventType> forHL7(PatientMgtContext ctx) {
            HashSet<EventType> eventType = new HashSet<>();
            if (ctx.getException() != null)
                eventType.add(ctx.getEventActionCode().equals(AuditMessages.EventActionCode.Create)
                        ? HL7_CREA_E
                        : ctx.getEventActionCode().equals(AuditMessages.EventActionCode.Update)
                        ? HL7_UPDA_E : null);
            else
                eventType.add(ctx.getEventActionCode().equals(AuditMessages.EventActionCode.Create)
                        ? HL7_CREA_P
                        : ctx.getEventActionCode().equals(AuditMessages.EventActionCode.Update)
                        ? HL7_UPDA_P
                        : null);
            if (ctx.getPreviousAttributes() != null || ctx.getPreviousPatientID() != null)
                eventType.add(ctx.getException() != null ? HL7_DELT_E : HL7_DELT_P);
            return eventType;
        }
    }

    static void emitAuditMessage(EventType eventType, String outcomeDesc, List<ActiveParticipant> apList,
                                 List<ParticipantObjectIdentification> poiList, AuditLogger log, Calendar eventTime) {
        AuditMessage msg = new AuditMessage();
        msg.setEventIdentification(AuditMessages.createEventIdentification(
                eventType.eventID, eventType.eventActionCode, eventTime, eventType.outcomeIndicator,
                outcomeDesc, eventType.eventTypeCode));
        for (ActiveParticipant ap : apList)
            msg.getActiveParticipant().add(ap);
        msg.getAuditSourceIdentification().add(log.createAuditSourceIdentification());
        for (ParticipantObjectIdentification poi : poiList)
            msg.getParticipantObjectIdentification().add(poi);
        try {
            log.write(log.timeStamp(), msg);
        } catch (Exception e) {
            LOG.warn("Failed to emit audit message", e);
        }
    }

    static Calendar getEventTime(Path path, AuditLogger log){
        Calendar eventTime = log.timeStamp();
        try {
            eventTime.setTimeInMillis(Files.getLastModifiedTime(path).toMillis());
        } catch (Exception e) {
            LOG.warn("Failed to get Last Modified Time of Audit Spool File - {} ", path, e);
        }
        return eventTime;
    }

    static String buildAET(Device device) {
        String[] aets = device.getApplicationAETitles().toArray(new String[device.getApplicationAETitles().size()]);
        StringBuilder b = new StringBuilder();
        b.append(aets[0]);
        for (int i = 1; i < aets.length; i++)
            b.append(';').append(aets[i]);
        return b.toString();
    }

    static String getLocalHostName(AuditLogger log) {
        return log.getConnections().get(0).getHostname();
    }

    static String getPreferredUsername(HttpServletRequest req) {
        RefreshableKeycloakSecurityContext securityContext = (RefreshableKeycloakSecurityContext)
                req.getAttribute(KeycloakSecurityContext.class.getName());
        return securityContext.getToken().getPreferredUsername();
    }

    static HashSet<SOPClass> getSopClasses(HashSet<String> instanceLines) {
        HashSet<SOPClass> sopC = new HashSet<>();
        for (String line : instanceLines) {
            InstanceInfo ii = new InstanceInfo(line);
            sopC.add(AuditMessages.createSOPClass(null,
                    ii.getField(InstanceInfo.CLASS_UID),
                    Integer.parseInt(ii.getField(InstanceInfo.INSTANCE_UID))));
        }
        return sopC;
    }

    static HashSet<ParticipantObjectDetail> getParticipantObjectDetail(PatientStudyInfo psi, HL7Info hl7i,
                                     EventType et) {
        HashSet<ParticipantObjectDetail> details = new HashSet<>();
        if (psi != null && psi.getField(PatientStudyInfo.STUDY_DATE) != null)
            details.add(pod(studyDate, psi.getField(PatientStudyInfo.STUDY_DATE).getBytes()));
        if (hl7i != null && hl7i.getField(HL7Info.POD_VALUE) != null)
            details.add(pod(hl7i.getField(HL7Info.POD_TYPE), hl7i.getField(HL7Info.POD_VALUE).getBytes()));
        if (et == EventType.QUERY_QIDO)
            details.add(pod("QueryEncoding", String.valueOf(StandardCharsets.UTF_8).getBytes()));
        if (et == EventType.QUERY_FIND)
            details.add(pod("TransferSyntax", UID.ImplicitVRLittleEndian.getBytes()));
        return details;
    }

     private static ParticipantObjectDetail pod(String s, byte[] b) {
        return AuditMessages.createParticipantObjectDetail(s, b);
    }

    private static ParticipantObjectIdentification patientPOIForDeletion(PatientStudyInfo psi) {
        return AuditMessages.createParticipantObjectIdentification(psi.getField(PatientStudyInfo.PATIENT_ID),
                AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                psi.getField(PatientStudyInfo.PATIENT_NAME), null, AuditMessages.ParticipantObjectTypeCode.Person,
                AuditMessages.ParticipantObjectTypeCodeRole.Patient, null, null, null, null, null, null, null, null,
                null, null);
    }

    private static ParticipantObjectIdentification studyPOIForDeletion(PatientStudyInfo psi, EventType et,
                            HashSet<String> instanceLines) {
        return AuditMessages.createParticipantObjectIdentification(
                psi.getField(PatientStudyInfo.STUDY_UID),
                AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID,
                null, null, AuditMessages.ParticipantObjectTypeCode.SystemObject,
                AuditMessages.ParticipantObjectTypeCodeRole.Report, null, null, null,
                et.eventClass == EventClass.PERM_DELETE
                        ? getAccessions(psi.getField(PatientStudyInfo.ACCESSION_NO)) : null,
                null, getSopClasses(instanceLines), null, null, getParticipantObjectContainsStudy(psi),
                getParticipantObjectDetail(psi, null, et));
    }

    static List<ParticipantObjectIdentification> poiListForDeletion(PatientStudyInfo psi, EventType et,
                                                                    HashSet<String> instanceLines) {
        List<ParticipantObjectIdentification> poiList = new ArrayList<>();
        poiList.add(studyPOIForDeletion(psi, et, instanceLines));
        poiList.add(patientPOIForDeletion(psi));
        return poiList;
    }

    static ParticipantObjectContainsStudy getParticipantObjectContainsStudy(PatientStudyInfo psi) {
        return AuditMessages.createParticipantObjectContainsStudy(
                AuditMessages.createStudyIDs(psi.getField(PatientStudyInfo.STUDY_UID)));
    }

    static HashSet<Accession> getAccessions(String accNum) {
        HashSet<Accession> accList = new HashSet<>();
        if (accNum != null)
            accList.add(AuditMessages.createAccession(accNum));
        return accList;
    }
}
