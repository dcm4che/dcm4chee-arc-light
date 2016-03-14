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
import org.dcm4che3.net.Device;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4che3.net.Connection;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.store.StoreContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Feb 2016
 */
public class AuditServiceUtils {
    private static final Logger LOG = LoggerFactory.getLogger(AuditService.class);
    static final String noValue = "<none>";
    enum EventClass {
        QUERY, DELETE, PERM_DELETE, STORE_WADOR, CONN_REJECT, RETRIEVE, APPLN_ACTIVITY
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
                AuditMessages.EventOutcomeIndicator.MinorFailure, null, null, false, false, false, AuditMessages.EventTypeCode.NodeAuthentication);

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
            EventType et = null;
            if (null != ctx.getException())
                et = ctx.isLocalRequestor() ? RTRV_B_E_E
                        : !ctx.isDestinationRequestor() && !ctx.isLocalRequestor() ? RTRV_B_M_E
                        : null != ctx.getRequestAssociation() && null != ctx.getStoreAssociation()
                            && ctx.isDestinationRequestor() ? RTRV_B_G_E
                        : null != ctx.getHttpRequest() ? RTRV_B_W_E : null;
            if (ctx.getException() == null)
                et = ctx.isLocalRequestor() ? RTRV_B_E_P
                        : !ctx.isDestinationRequestor() && !ctx.isLocalRequestor() ? RTRV_B_M_P
                        : null != ctx.getRequestAssociation() && null != ctx.getStoreAssociation()
                            && ctx.isDestinationRequestor()
                        ? RTRV_B_G_P : null != ctx.getHttpRequest() ? RTRV_B_W_P : null;
            eventType.add(et);
            return eventType;
        }

        static HashSet<EventType> forDicomInstTransferred(RetrieveContext ctx) {
            HashSet<EventType> eventType = new HashSet<>();
            EventType et;
            if (ctx.failedSOPInstanceUIDs().length != ctx.getMatches().size()) {
                if (null != ctx.getException() || ctx.failedSOPInstanceUIDs().length > 0) {
                    eventType.add(getDicomInstTrfdErrorEventType(ctx));
                }
                if (ctx.getException() == null || ctx.failedSOPInstanceUIDs().length > 0) {
                    et = ctx.isLocalRequestor() ? RTRV_T_E_P
                            : !ctx.isDestinationRequestor() && !ctx.isLocalRequestor() ? RTRV_T_M_P
                            : null != ctx.getRequestAssociation() && null != ctx.getStoreAssociation()
                            && ctx.isDestinationRequestor()
                            ? RTRV_T_G_P : null != ctx.getHttpRequest() ? RTRV_T_W_P : null;
                    eventType.add(et);
                }
            }
            else
                eventType.add(getDicomInstTrfdErrorEventType(ctx));
            return eventType;
        }

        static EventType getDicomInstTrfdErrorEventType(RetrieveContext ctx) {
            EventType et;
            return et = ctx.isLocalRequestor() ? AuditServiceUtils.EventType.RTRV_T_E_E
                    : !ctx.isDestinationRequestor() && !ctx.isLocalRequestor() ? AuditServiceUtils.EventType.RTRV_T_M_E
                    : null != ctx.getRequestAssociation() && null != ctx.getStoreAssociation()
                    && ctx.isDestinationRequestor() ? AuditServiceUtils.EventType.RTRV_T_G_E
                    : null != ctx.getHttpRequest() ? AuditServiceUtils.EventType.RTRV_T_W_E : null;
        }
    }

    static void emitAuditMessage(EventType eventType, String outcomeDesc, List<ActiveParticipant> apList,
                                 List<ParticipantObjectIdentification> poiList, AuditLogger log) {
        AuditMessage msg = new AuditMessage();
        msg.setEventIdentification(AuditMessages.createEventIdentification(
                eventType.eventID, eventType.eventActionCode, log.timeStamp(), eventType.outcomeIndicator,
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

    static String buildAET(Device device) {
        String[] aets = device.getApplicationAETitles().toArray(new String[device.getApplicationAETitles().size()]);
        StringBuilder b = new StringBuilder();
        b.append(aets[0]);
        for (int i = 1; i < aets.length; i++)
            b.append(';').append(aets[i]);
        return b.toString();
    }

    static String getLocalHostName(AuditLogger log) {
        List<Connection> conns = log.getConnections();
        return conns.get(0).getHostname();
    }

    static String buildAltUserID(String s) {
        return (s != null) ? AuditMessages.alternativeUserIDForAETitle(s) : null;
    }
}
