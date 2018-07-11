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
 * Portions created by the Initial Developer are Copyright (C) 2017
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
import org.dcm4chee.arc.event.ArchiveServiceEvent;
import org.dcm4chee.arc.event.QueueMessageOperation;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.store.StoreContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Feb 2016
 */

class AuditServiceUtils {
    private static final Logger LOG = LoggerFactory.getLogger(AuditServiceUtils.class);

    enum EventClass {
        QUERY, USER_DELETED, SCHEDULER_DELETED, STORE_WADOR, CONN_REJECT, RETRIEVE, APPLN_ACTIVITY, HL7, PROC_STUDY, PROV_REGISTER,
        STGCMT, INST_RETRIEVED, LDAP_CHANGES, QUEUE_EVENT
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

        CONN__RJCT(EventClass.CONN_REJECT, AuditMessages.EventID.SecurityAlert, AuditMessages.EventActionCode.Execute,
                null, null, AuditMessages.EventTypeCode.NodeAuthentication),

        PAT_CREATE(EventClass.HL7, AuditMessages.EventID.PatientRecord, AuditMessages.EventActionCode.Create,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, null),
        PAT_UPDATE(EventClass.HL7, AuditMessages.EventID.PatientRecord, AuditMessages.EventActionCode.Update,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, null),
        PAT_DELETE(EventClass.HL7, AuditMessages.EventID.PatientRecord, AuditMessages.EventActionCode.Delete,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, null),
        PAT_DLT_SC(EventClass.HL7, AuditMessages.EventID.PatientRecord, AuditMessages.EventActionCode.Delete,
                null, null, null),

        PROC_STD_C(EventClass.PROC_STUDY, AuditMessages.EventID.ProcedureRecord, AuditMessages.EventActionCode.Create,
                null, null, null),
        PROC_STD_U(EventClass.PROC_STUDY, AuditMessages.EventID.ProcedureRecord, AuditMessages.EventActionCode.Update,
                 null, null, null),
        PROC_STD_D(EventClass.PROC_STUDY, AuditMessages.EventID.ProcedureRecord, AuditMessages.EventActionCode.Delete,
                 null, null, null),

        PROV_REGIS(EventClass.PROV_REGISTER, AuditMessages.EventID.Export, AuditMessages.EventActionCode.Read,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination,
                AuditMessages.EventTypeCode.ITI_41_ProvideAndRegisterDocumentSetB),

        LDAP_CHNGS(EventClass.LDAP_CHANGES, AuditMessages.EventID.SecurityAlert, AuditMessages.EventActionCode.Execute,
                null, null, AuditMessages.EventTypeCode.SoftwareConfiguration),

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
        final EventTypeCode eventTypeCode;


        EventType(EventClass eventClass, AuditMessages.EventID eventID, String eventActionCode, AuditMessages.RoleIDCode source,
                  AuditMessages.RoleIDCode destination, EventTypeCode etc) {
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
                    ? AuditServiceUtils.EventType.APPLNSTART
                    : AuditServiceUtils.EventType.APPLN_STOP;
        }

        static EventType forInstanceStored(StoreContext ctx) {
            return !ctx.getLocations().isEmpty() && ctx.getPreviousInstance() != null
                        ? STORE_UPDT : STORE_CREA;
        }

        static EventType forHL7(PatientMgtContext ctx) {
            return ctx.getEventActionCode().equals(AuditMessages.EventActionCode.Create)
                    ? PAT_CREATE
                    : ctx.getEventActionCode().equals(AuditMessages.EventActionCode.Update)
                        ? PAT_UPDATE
                        : ctx.getEventActionCode().equals(AuditMessages.EventActionCode.Delete)
                            ? ctx.getHttpServletRequestInfo() != null ? PAT_DELETE : PAT_DLT_SC : null;
        }

        static EventType forProcedure(String eventActionCode) {
            return eventActionCode.equals(AuditMessages.EventActionCode.Create)
                    ? EventType.PROC_STD_C
                    : eventActionCode.equals(AuditMessages.EventActionCode.Update)
                        ? EventType.PROC_STD_U
                        : PROC_STD_D;
        }

        static EventType forQueueEvent(QueueMessageOperation operation) {
            return operation == QueueMessageOperation.CancelTasks
                    ? EventType.CANCEL_TSK
                    : operation == QueueMessageOperation.RescheduleTasks
                        ? EventType.RESCHD_TSK : EventType.DELETE_TSK;
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
