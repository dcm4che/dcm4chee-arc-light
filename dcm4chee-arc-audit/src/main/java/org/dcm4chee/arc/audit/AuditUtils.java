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
 * Portions created by the Initial Developer are Copyright (C) 2017-2019
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
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4chee.arc.conf.HL7OrderSPSStatus;
import org.dcm4chee.arc.conf.SPSStatus;
import org.dcm4chee.arc.delete.StudyDeleteContext;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.entity.RejectionState;
import org.dcm4chee.arc.event.ArchiveServiceEvent;
import org.dcm4chee.arc.event.QueueMessageOperation;
import org.dcm4chee.arc.event.RejectionNoteSent;
import org.dcm4chee.arc.hl7.ArchiveHL7Message;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Feb 2016
 */

class AuditUtils {
    private static final Logger LOG = LoggerFactory.getLogger(AuditUtils.class);

    enum EventClass {
        QUERY, USER_DELETED, SCHEDULER_DELETED, STORE_WADOR, CONN_FAILURE, RETRIEVE, APPLN_ACTIVITY, PATIENT,
        PROCEDURE, STUDY, PROV_REGISTER, STGCMT, INST_RETRIEVED, LDAP_CHANGES, QUEUE_EVENT, IMPAX, ASSOCIATION_FAILURE
    }
    enum EventType {
        WADO___URI(EventClass.STORE_WADOR, AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read,
                AuditMessages.RoleIDCode.Destination, AuditMessages.RoleIDCode.Source, null),
        STORE_CREA(EventClass.STORE_WADOR, AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Create,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, null),
        STORE_UPDT(EventClass.STORE_WADOR, AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Update,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, null),

        RTRV_BEGIN(EventClass.RETRIEVE, AuditMessages.EventID.BeginTransferringDICOMInstances, AuditMessages.EventActionCode.Execute,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, null),

        RTRV___TRF(EventClass.RETRIEVE, AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, null),

        STG_COMMIT(EventClass.STGCMT, AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, null),

        INST_RETRV(EventClass.INST_RETRIEVED, AuditMessages.EventID.DICOMInstancesAccessed, AuditMessages.EventActionCode.Read,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, null),

        RJ_PARTIAL(EventClass.USER_DELETED, AuditMessages.EventID.DICOMInstancesAccessed, AuditMessages.EventActionCode.Delete,
                null, null, null),
        RJ_COMPLET(EventClass.USER_DELETED, AuditMessages.EventID.DICOMStudyDeleted, AuditMessages.EventActionCode.Delete,
                null, null, null),

        RJ_SCH_FEW(EventClass.SCHEDULER_DELETED, AuditMessages.EventID.DICOMInstancesAccessed, AuditMessages.EventActionCode.Delete,
                null, null, null),
        PRMDLT_SCH(EventClass.SCHEDULER_DELETED, AuditMessages.EventID.DICOMStudyDeleted, AuditMessages.EventActionCode.Delete,
                null, null, null),

        APPLNSTART(EventClass.APPLN_ACTIVITY, AuditMessages.EventID.ApplicationActivity, AuditMessages.EventActionCode.Execute,
                AuditMessages.RoleIDCode.ApplicationLauncher, AuditMessages.RoleIDCode.Application,
                AuditMessages.EventTypeCode.ApplicationStart),
        APPLN_STOP(EventClass.APPLN_ACTIVITY, AuditMessages.EventID.ApplicationActivity, AuditMessages.EventActionCode.Execute,
                AuditMessages.RoleIDCode.ApplicationLauncher, AuditMessages.RoleIDCode.Application,
                AuditMessages.EventTypeCode.ApplicationStop),

        QUERY__EVT(EventClass.QUERY, AuditMessages.EventID.Query, AuditMessages.EventActionCode.Execute,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, null),

        CONN_FAILR(EventClass.CONN_FAILURE, AuditMessages.EventID.SecurityAlert, AuditMessages.EventActionCode.Execute,
                null, null, AuditMessages.EventTypeCode.NodeAuthentication),

        PAT_CREATE(EventClass.PATIENT, AuditMessages.EventID.PatientRecord, AuditMessages.EventActionCode.Create,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, null),
        PAT_UPDATE(EventClass.PATIENT, AuditMessages.EventID.PatientRecord, AuditMessages.EventActionCode.Update,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, null),
        PAT_DELETE(EventClass.PATIENT, AuditMessages.EventID.PatientRecord, AuditMessages.EventActionCode.Delete,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, null),
        PAT_DLT_SC(EventClass.PATIENT, AuditMessages.EventID.PatientRecord, AuditMessages.EventActionCode.Delete,
                null, null, null),
        PAT___READ(EventClass.PATIENT, AuditMessages.EventID.PatientRecord, AuditMessages.EventActionCode.Read,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, null),
        PAT_UPD_SC(EventClass.PATIENT, AuditMessages.EventID.PatientRecord, AuditMessages.EventActionCode.Update,
                null, null, null),
        PAT_RD__SC(EventClass.PATIENT, AuditMessages.EventID.PatientRecord, AuditMessages.EventActionCode.Read,
                null, null, null),

        PROC_STD_C(EventClass.PROCEDURE, AuditMessages.EventID.ProcedureRecord, AuditMessages.EventActionCode.Create,
                null, null, null),
        PROC_STD_U(EventClass.PROCEDURE, AuditMessages.EventID.ProcedureRecord, AuditMessages.EventActionCode.Update,
                 null, null, null),
        PROC_STD_R(EventClass.PROCEDURE, AuditMessages.EventID.ProcedureRecord, AuditMessages.EventActionCode.Read,
                null, null, null),
        PROC_STD_D(EventClass.PROCEDURE, AuditMessages.EventID.ProcedureRecord, AuditMessages.EventActionCode.Delete,
                null, null, null),

        STUDY_UPDT(EventClass.STUDY, AuditMessages.EventID.DICOMInstancesAccessed, AuditMessages.EventActionCode.Update,
                null, null, null),
        STUDY_READ(EventClass.STUDY, AuditMessages.EventID.DICOMInstancesAccessed, AuditMessages.EventActionCode.Read,
                null, null, null),

        PROV_REGIS(EventClass.PROV_REGISTER, AuditMessages.EventID.Export, AuditMessages.EventActionCode.Read,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination,
                AuditMessages.EventTypeCode.ITI_41_ProvideAndRegisterDocumentSetB),

        LDAP_CHNGS(EventClass.LDAP_CHANGES, AuditMessages.EventID.SecurityAlert, AuditMessages.EventActionCode.Execute,
                null, null, AuditMessages.EventTypeCode.SoftwareConfiguration),

        IMPAX_MISM(EventClass.IMPAX, AuditMessages.EventID.SecurityAlert, AuditMessages.EventActionCode.Execute,
                null, null, null),

        ASSOC_FAIL(EventClass.ASSOCIATION_FAILURE, AuditMessages.EventID.SecurityAlert, AuditMessages.EventActionCode.Execute,
                null, null, AuditMessages.EventTypeCode.AssociationFailure),

        CANCEL_TSK(EventClass.QUEUE_EVENT, AuditMessages.EventID.SecurityAlert, AuditMessages.EventActionCode.Execute,
                null, null, AuditMessages.EventTypeCode.CancelTask),
        RESCHD_TSK(EventClass.QUEUE_EVENT, AuditMessages.EventID.SecurityAlert, AuditMessages.EventActionCode.Execute,
                null, null, AuditMessages.EventTypeCode.RescheduleTask),
        DELETE_TSK(EventClass.QUEUE_EVENT, AuditMessages.EventID.SecurityAlert, AuditMessages.EventActionCode.Execute,
                null, null, AuditMessages.EventTypeCode.DeleteTask);


        final EventClass eventClass;
        final AuditMessages.EventID eventID;
        final String eventActionCode;
        final AuditMessages.RoleIDCode source;
        final AuditMessages.RoleIDCode destination;
        final AuditMessages.EventTypeCode eventTypeCode;


        EventType(EventClass eventClass, AuditMessages.EventID eventID, String eventActionCode, AuditMessages.RoleIDCode source,
                  AuditMessages.RoleIDCode destination, AuditMessages.EventTypeCode etc) {
            this.eventClass = eventClass;
            this.eventID = eventID;
            this.eventActionCode = eventActionCode;
            this.source = source;
            this.destination = destination;
            this.eventTypeCode = etc;
        }

        static EventType fromFile(Path file) {
            return valueOf(file.getFileName().toString().substring(0, 10));
        }

        static EventType forApplicationActivity(ArchiveServiceEvent event) {
            return event.getType() == ArchiveServiceEvent.Type.STARTED
                    ? APPLNSTART
                    : APPLN_STOP;
        }

        static EventType forInstanceStored(StoreContext ctx) {
            return !ctx.getLocations().isEmpty() && ctx.getPreviousInstance() != null
                        ? STORE_UPDT : STORE_CREA;
        }

        static EventType forInstancesDeleted(StoreContext ctx) {
            StoreSession storeSession = ctx.getStoreSession();
            boolean isSchedulerDeletedExpiredStudies = storeSession.getAssociation() == null
                                                        && storeSession.getHttpRequest() == null;
            return ctx.getStoredInstance().getSeries().getStudy().getRejectionState() == RejectionState.COMPLETE
                    ? isSchedulerDeletedExpiredStudies
                        ? PRMDLT_SCH
                        : RJ_COMPLET
                    : isSchedulerDeletedExpiredStudies
                        ? RJ_SCH_FEW
                        : RJ_PARTIAL;
        }

        static EventType forStudyDeleted(StudyDeleteContext ctx) {
            return ctx.getHttpServletRequestInfo() != null ? RJ_COMPLET : PRMDLT_SCH;
        }

        static EventType forExternalRejection(RejectionNoteSent rejectionNoteSent) {
            return rejectionNoteSent.isStudyDeleted() ? RJ_COMPLET : RJ_PARTIAL;
        }

        static EventType forPatRec(PatientMgtContext ctx) {
            if (!ctx.getPatientVerificationStatus().equals(Patient.VerificationStatus.UNVERIFIED))
                return forPatVer(ctx);

            return ctx.getEventActionCode().equals(AuditMessages.EventActionCode.Create)
                    ? PAT_CREATE
                    : ctx.getEventActionCode().equals(AuditMessages.EventActionCode.Update)
                        ? PAT_UPDATE
                        : ctx.getEventActionCode().equals(AuditMessages.EventActionCode.Delete)
                            ? ctx.getHttpServletRequestInfo() != null ? PAT_DELETE : PAT_DLT_SC
                            : PAT___READ;
        }

        private static EventType forPatVer(PatientMgtContext ctx) {
            String eac = ctx.getEventActionCode();
            return ctx.getHttpServletRequestInfo() == null
                    ? eac.equals(AuditMessages.EventActionCode.Update)
                        ? PAT_UPD_SC : PAT_RD__SC
                    : eac.equals(AuditMessages.EventActionCode.Update)
                        ? PAT_UPDATE
                        : eac.equals(AuditMessages.EventActionCode.Create)
                            ? PAT_CREATE
                            : PAT___READ;
        }

        static EventType forHL7OutgoingPatRec(String messageType) {
            return messageType.equals("ADT^A28") || messageType.equals("ORU^R01")
                    ? PAT_CREATE
                    : PAT_UPDATE;
        }

        static EventType forHL7IncomingPatRec(UnparsedHL7Message hl7ResponseMessage) {
            if (hl7ResponseMessage instanceof ArchiveHL7Message) {
                ArchiveHL7Message archiveHL7Message = (ArchiveHL7Message) hl7ResponseMessage;
                return archiveHL7Message.getPatRecEventActionCode().equals(AuditMessages.EventActionCode.Create)
                        ? PAT_CREATE
                        : PAT_UPDATE;
            }
            return PAT___READ;
        }

        static EventType forProcedure(String eventActionCode) {
            return eventActionCode == null
                    ? PROC_STD_R
                    : eventActionCode.equals(AuditMessages.EventActionCode.Create)
                        ? PROC_STD_C
                        : eventActionCode.equals(AuditMessages.EventActionCode.Update)
                            ? PROC_STD_U
                            : PROC_STD_D;
        }

        static EventType forStudy(String eventActionCode) {
            return eventActionCode == null ? STUDY_READ : STUDY_UPDT;
        }

        static EventType forHL7IncomingOrderMsg(UnparsedHL7Message hl7ResponseMessage) {
            if (hl7ResponseMessage instanceof ArchiveHL7Message) {
                ArchiveHL7Message archiveHL7Message = (ArchiveHL7Message) hl7ResponseMessage;
                String procRecEventActionCode = archiveHL7Message.getProcRecEventActionCode();
                return procRecEventActionCode == null
                        ? PROC_STD_R
                        : procRecEventActionCode.equals(AuditMessages.EventActionCode.Create)
                            ? PROC_STD_C
                            : PROC_STD_U;
            }
            return PROC_STD_R;
        }

        static EventType forHL7OutgoingOrderMsg(String orderCtrlStatus,
                Collection<HL7OrderSPSStatus> hl7OrderSPSStatuses) {
            EventType eventType = PROC_STD_R;
            for (HL7OrderSPSStatus hl7OrderSPSStatus : hl7OrderSPSStatuses) {
                String[] orderControlStatusCodes = hl7OrderSPSStatus.getOrderControlStatusCodes();
                if (hl7OrderSPSStatus.getSPSStatus() == SPSStatus.SCHEDULED) {
                    if (Arrays.asList(orderControlStatusCodes).contains(orderCtrlStatus)) {
                        eventType = PROC_STD_C;
                        break;
                    }
                } else {
                    if (Arrays.asList(orderControlStatusCodes).contains(orderCtrlStatus)
                            || orderCtrlStatus.equals("SC_CM")) {//SC_CM = archive sends proc update msg mpps or study receive trigger
                        eventType = PROC_STD_U;
                        break;
                    }
                }
            }
            return eventType;
        }

        static EventType forQueueEvent(QueueMessageOperation operation) {
            return operation == QueueMessageOperation.CancelTasks
                    ? CANCEL_TSK
                    : operation == QueueMessageOperation.RescheduleTasks
                        ? RESCHD_TSK : DELETE_TSK;
        }
    }

    static AuditMessages.EventTypeCode errorEventTypeCode(String errorCode) {
        AuditMessages.EventTypeCode errorEventTypeCode = null;
        switch (errorCode) {
            case "x0110":
                errorEventTypeCode = AuditMessages.EventTypeCode.x0110;
                break;
            case "x0118":
                errorEventTypeCode = AuditMessages.EventTypeCode.x0118;
                break;
            case "x0122":
                errorEventTypeCode = AuditMessages.EventTypeCode.x0122;
                break;
            case "x0124":
                errorEventTypeCode = AuditMessages.EventTypeCode.x0124;
                break;
            case "x0211":
                errorEventTypeCode = AuditMessages.EventTypeCode.x0211;
                break;
            case "x0212":
                errorEventTypeCode = AuditMessages.EventTypeCode.x0212;
                break;
            case "A700":
                errorEventTypeCode = AuditMessages.EventTypeCode.A700;
                break;
            case "A770":
                errorEventTypeCode = AuditMessages.EventTypeCode.A770;
                break;
            case "A771":
                errorEventTypeCode = AuditMessages.EventTypeCode.A771;
                break;
            case "A772":
                errorEventTypeCode = AuditMessages.EventTypeCode.A772;
                break;
            case "A773":
                errorEventTypeCode = AuditMessages.EventTypeCode.A773;
                break;
            case "A774":
                errorEventTypeCode = AuditMessages.EventTypeCode.A774;
                break;
            case "A775":
                errorEventTypeCode = AuditMessages.EventTypeCode.A775;
                break;
            case "A776":
                errorEventTypeCode = AuditMessages.EventTypeCode.A776;
                break;
            case "A777":
                errorEventTypeCode = AuditMessages.EventTypeCode.A777;
                break;
            case "A778":
                errorEventTypeCode = AuditMessages.EventTypeCode.A778;
                break;
            case "A779":
                errorEventTypeCode = AuditMessages.EventTypeCode.A779;
                break;
            case "A900":
                errorEventTypeCode = AuditMessages.EventTypeCode.A900;
                break;
            case "C409":
                errorEventTypeCode = AuditMessages.EventTypeCode.C409;
                break;
            default:
                LOG.warn("Unknown DICOM error code");
        }

        return errorEventTypeCode;
    }

}
