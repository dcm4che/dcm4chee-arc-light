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
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.store.StoreContext;
import java.nio.file.Path;
import java.util.*;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Feb 2016
 */

class AuditServiceUtils {
    enum EventClass {
        QUERY, DELETE, PERM_DELETE, STORE_WADOR, CONN_REJECT, RETRIEVE, APPLN_ACTIVITY, HL7, PROC_STUDY
    }
    enum EventType {
        WADO___URI(EventClass.STORE_WADOR, AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read,
                AuditMessages.RoleIDCode.Destination, AuditMessages.RoleIDCode.Source, true, false, false, null),
        STORE_CREA(EventClass.STORE_WADOR, AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Create,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, true, false, false, null),
        STORE_UPDT(EventClass.STORE_WADOR, AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Update,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, true, false, false, null),

        RTRV_BGN_M(EventClass.RETRIEVE, AuditMessages.EventID.BeginTransferringDICOMInstances, AuditMessages.EventActionCode.Execute,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, false, true, null),
        RTRV_BGN_G(EventClass.RETRIEVE, AuditMessages.EventID.BeginTransferringDICOMInstances, AuditMessages.EventActionCode.Execute,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, true, false, null),
        RTRV_BGN_E(EventClass.RETRIEVE, AuditMessages.EventID.BeginTransferringDICOMInstances, AuditMessages.EventActionCode.Execute,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, true, false, false, null),
        RTRV_BGN_W(EventClass.RETRIEVE, AuditMessages.EventID.BeginTransferringDICOMInstances, AuditMessages.EventActionCode.Execute,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, true, false, null),

        RTRV_T_M_P(EventClass.RETRIEVE, AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, false, true, null),
        RTRV_T_M_E(EventClass.RETRIEVE, AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, false, true, null),
        RTRV_T_G_P(EventClass.RETRIEVE, AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, true, false, null),
        RTRV_T_G_E(EventClass.RETRIEVE, AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, true, false, null),
        RTRV_T_E_P(EventClass.RETRIEVE, AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, true, false, false, null),
        RTRV_T_E_E(EventClass.RETRIEVE, AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, true, false, false, null),
        RTRV_T_W_P(EventClass.RETRIEVE, AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, true, false, null),
        RTRV_T_W_E(EventClass.RETRIEVE, AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, true, false, null),

        RJN_DELETE(EventClass.DELETE, AuditMessages.EventID.DICOMInstancesAccessed, AuditMessages.EventActionCode.Delete,
                null, null, true, false, false, null),

        PRMDLT_SCH(EventClass.PERM_DELETE, AuditMessages.EventID.DICOMStudyDeleted, AuditMessages.EventActionCode.Delete,
                null, null, false, true, false, null),
        PRMDLT_WEB(EventClass.PERM_DELETE, AuditMessages.EventID.DICOMStudyDeleted, AuditMessages.EventActionCode.Delete,
                null, null, true, false, false, null),

        APPLNSTART(EventClass.APPLN_ACTIVITY, AuditMessages.EventID.ApplicationActivity, AuditMessages.EventActionCode.Execute,
                null, null, false, false, false, AuditMessages.EventTypeCode.ApplicationStart),
        APPLN_STOP(EventClass.APPLN_ACTIVITY, AuditMessages.EventID.ApplicationActivity, AuditMessages.EventActionCode.Execute,
                null, null, false, false, false, AuditMessages.EventTypeCode.ApplicationStop),

        QUERY_QIDO(EventClass.QUERY, AuditMessages.EventID.Query, AuditMessages.EventActionCode.Execute,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, true, false, false, null),
        QUERY_FIND(EventClass.QUERY, AuditMessages.EventID.Query, AuditMessages.EventActionCode.Execute,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, true, false, false, null),

        CONN__RJCT(EventClass.CONN_REJECT, AuditMessages.EventID.SecurityAlert, AuditMessages.EventActionCode.Execute,
                null, null, false, false, false, AuditMessages.EventTypeCode.NodeAuthentication),

        PAT_CREATE(EventClass.HL7, AuditMessages.EventID.PatientRecord, AuditMessages.EventActionCode.Create,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, true, false, false, null),
        PAT_UPDATE(EventClass.HL7, AuditMessages.EventID.PatientRecord, AuditMessages.EventActionCode.Update,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, true, false, false, null),
        PAT_DELETE(EventClass.HL7, AuditMessages.EventID.PatientRecord, AuditMessages.EventActionCode.Delete,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, true, false, false, null),
        PAT_DLT_SC(EventClass.HL7, AuditMessages.EventID.PatientRecord, AuditMessages.EventActionCode.Delete,
                null, null, false, true, false, null),

        PROC_STD_C(EventClass.PROC_STUDY, AuditMessages.EventID.ProcedureRecord, AuditMessages.EventActionCode.Create,
                null, null, true, false, false, null),
        PROC_STD_U(EventClass.PROC_STUDY, AuditMessages.EventID.ProcedureRecord, AuditMessages.EventActionCode.Update,
                 null, null, true, false, false, null),
        PROC_STD_D(EventClass.PROC_STUDY, AuditMessages.EventID.ProcedureRecord, AuditMessages.EventActionCode.Delete,
                 null, null, true, false, false, null);


        final EventClass eventClass;
        final AuditMessages.EventID eventID;
        final String eventActionCode;
        final AuditMessages.RoleIDCode source;
        final AuditMessages.RoleIDCode destination;
        final boolean isSource;
        final boolean isDest;
        final boolean isOther;
        final EventTypeCode eventTypeCode;


        EventType(EventClass eventClass, AuditMessages.EventID eventID, String eventActionCode, AuditMessages.RoleIDCode source,
                  AuditMessages.RoleIDCode destination, boolean isSource, boolean isDest, boolean isOther, EventTypeCode etc) {
            this.eventClass = eventClass;
            this.eventID = eventID;
            this.eventActionCode = eventActionCode;
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

        static EventType forInstanceStored(StoreContext ctx) {
            return !ctx.getLocations().isEmpty()
                    ? ctx.getPreviousInstance() != null ? STORE_UPDT : STORE_CREA
                    : ctx.getStoredInstance() != null ? STORE_CREA : null;
        }

        static HashSet<EventType> forBeginTransfer(RetrieveContext ctx) {
            HashSet<EventType> eventType = new HashSet<>();
            eventType.add(ctx.isLocalRequestor() ? RTRV_BGN_E
                    : !ctx.isDestinationRequestor() && !ctx.isLocalRequestor() ? RTRV_BGN_M
                    : null != ctx.getRequestAssociation() && null != ctx.getStoreAssociation()
                    && ctx.isDestinationRequestor()
                    ? RTRV_BGN_G : null != ctx.getHttpRequest() ? RTRV_BGN_W : null);
            return eventType;
        }

        static HashSet<EventType> forDicomInstTransferred(RetrieveContext ctx) {
            HashSet<EventType> eventType = new HashSet<>();
            HashSet<DicomInstancesTransferredOutcomeType> outcomes = getInstancesTransferredOutcomeType(ctx);
            for (DicomInstancesTransferredOutcomeType ot : outcomes)
                switch (ot) {
                    case PARTIAL_TRANSFERRED_EXCEPTION:
                    case ALL_FAIL:
                        eventType.add(getDicomInstTrfdErrorEventType(ctx));
                        break;
                    case ALL_SUCCESS:
                    case CSTOREFWD:
                    case PARTIAL_TRANSFERRED:
                        eventType.add(getDicomInstTrfdSuccessEventType(ctx));
                        break;
                }
            return eventType;
        }

        static HashSet<DicomInstancesTransferredOutcomeType> getInstancesTransferredOutcomeType(RetrieveContext ctx) {
            HashSet<DicomInstancesTransferredOutcomeType> ot = new HashSet<>();
            if (ctx.failedSOPInstanceUIDs().length == ctx.getMatches().size() && !ctx.getMatches().isEmpty())
                ot.add(DicomInstancesTransferredOutcomeType.ALL_FAIL);
            if (ctx.failedSOPInstanceUIDs().length == 0 && !ctx.getMatches().isEmpty())
                ot.add(DicomInstancesTransferredOutcomeType.ALL_SUCCESS);
            if (ctx.getMatches().isEmpty() && !ctx.getCStoreForwards().isEmpty())
                ot.add(DicomInstancesTransferredOutcomeType.CSTOREFWD);
            if (ctx.failedSOPInstanceUIDs().length != ctx.getMatches().size() && ctx.failedSOPInstanceUIDs().length > 0) {
                ot.add(DicomInstancesTransferredOutcomeType.PARTIAL_TRANSFERRED_EXCEPTION);
                ot.add(DicomInstancesTransferredOutcomeType.PARTIAL_TRANSFERRED);
            }
            return ot;
        }

        static EventType getDicomInstTrfdSuccessEventType(RetrieveContext ctx) {
            return ctx.isLocalRequestor() ? RTRV_T_E_P
                    : !ctx.isDestinationRequestor() && !ctx.isLocalRequestor() ? RTRV_T_M_P
                    : null != ctx.getRequestAssociation() && null != ctx.getStoreAssociation()
                    && ctx.isDestinationRequestor()
                    ? RTRV_T_G_P : null != ctx.getHttpRequest() ? RTRV_T_W_P : null;
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
            eventType.add(ctx.getEventActionCode().equals(AuditMessages.EventActionCode.Create)
                    ? PAT_CREATE
                    : ctx.getEventActionCode().equals(AuditMessages.EventActionCode.Update)
                    ? PAT_UPDATE
                    : ctx.getEventActionCode().equals(AuditMessages.EventActionCode.Delete)
                    ? ctx.getHttpRequest() != null ? PAT_DELETE : PAT_DLT_SC : null);
            if (ctx.getPreviousAttributes() != null || ctx.getPreviousPatientID() != null)
                eventType.add(PAT_DELETE);
            return eventType;
        }

        static HashSet<EventType> forProcedure(String eac) {
            HashSet<EventType> et = new HashSet<>();
            et.add(eac.equals(AuditMessages.EventActionCode.Create)
                    ? EventType.PROC_STD_C : eac.equals(AuditMessages.EventActionCode.Update)
                    ? EventType.PROC_STD_U : PROC_STD_D);
            return et;
        }
    }

    enum DicomInstancesTransferredOutcomeType {
        ALL_FAIL, ALL_SUCCESS, PARTIAL_TRANSFERRED, PARTIAL_TRANSFERRED_EXCEPTION, CSTOREFWD
    }

}
