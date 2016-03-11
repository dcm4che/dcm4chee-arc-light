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
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.delete.StudyDeleteContext;
import org.dcm4che3.net.Connection;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 */
@ApplicationScoped
public class AuditServiceUtils {
    private static final Logger LOG = LoggerFactory.getLogger(AuditService.class);
    private static final String noValue = "<none>";
    protected enum EventType {
        ITRF_WAD_P(AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read, AuditMessages.EventOutcomeIndicator.Success,
                AuditMessages.RoleIDCode.Destination, AuditMessages.RoleIDCode.Source, true, false, false, null),
        ITRF_WAD_E(AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read, AuditMessages.EventOutcomeIndicator.MinorFailure,
                AuditMessages.RoleIDCode.Destination, AuditMessages.RoleIDCode.Source, true, false, false, null),
        ITRF_S_C_P(AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Create, AuditMessages.EventOutcomeIndicator.Success,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, true, false, false, null),
        ITRF_S_C_E(AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Create, AuditMessages.EventOutcomeIndicator.MinorFailure,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, true, false, false, null),
        ITRF_S_U_P(AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Update, AuditMessages.EventOutcomeIndicator.Success,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, true, false, false, null),
        ITRF_S_U_E(AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Update, AuditMessages.EventOutcomeIndicator.MinorFailure,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, true, false, false, null),

        RTRV_B_M_P(AuditMessages.EventID.BeginTransferringDICOMInstances, AuditMessages.EventActionCode.Execute, AuditMessages.EventOutcomeIndicator.Success,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, false, true, null),
        RTRV_B_M_E(AuditMessages.EventID.BeginTransferringDICOMInstances, AuditMessages.EventActionCode.Execute, AuditMessages.EventOutcomeIndicator.MinorFailure,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, false, true, null),
        RTRV_B_G_P(AuditMessages.EventID.BeginTransferringDICOMInstances, AuditMessages.EventActionCode.Execute, AuditMessages.EventOutcomeIndicator.Success,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, true, false, null),
        RTRV_B_G_E(AuditMessages.EventID.BeginTransferringDICOMInstances, AuditMessages.EventActionCode.Execute, AuditMessages.EventOutcomeIndicator.MinorFailure,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, true, false, null),
        RTRV_B_E_P(AuditMessages.EventID.BeginTransferringDICOMInstances, AuditMessages.EventActionCode.Execute, AuditMessages.EventOutcomeIndicator.Success,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, true, false, false, null),
        RTRV_B_E_E(AuditMessages.EventID.BeginTransferringDICOMInstances, AuditMessages.EventActionCode.Execute, AuditMessages.EventOutcomeIndicator.MinorFailure,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, true, false, false, null),
        RTRV_T_M_P(AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read, AuditMessages.EventOutcomeIndicator.Success,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, false, true, null),
        RTRV_T_M_E(AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read, AuditMessages.EventOutcomeIndicator.MinorFailure,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, false, true, null),
        RTRV_T_G_P(AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read, AuditMessages.EventOutcomeIndicator.Success,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, true, false, null),
        RTRV_T_G_E(AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read, AuditMessages.EventOutcomeIndicator.MinorFailure,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, true, false, null),
        RTRV_T_E_P(AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read, AuditMessages.EventOutcomeIndicator.Success,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, true, false, false, null),
        RTRV_T_E_E(AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read, AuditMessages.EventOutcomeIndicator.MinorFailure,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, true, false, false, null),

        RTRV_B_W_P(AuditMessages.EventID.BeginTransferringDICOMInstances, AuditMessages.EventActionCode.Execute, AuditMessages.EventOutcomeIndicator.Success,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, true, false, null),
        RTRV_B_W_E(AuditMessages.EventID.BeginTransferringDICOMInstances, AuditMessages.EventActionCode.Execute, AuditMessages.EventOutcomeIndicator.MinorFailure,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, true, false, null),
        RTRV_T_W_P(AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read, AuditMessages.EventOutcomeIndicator.Success,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, true, false, null),
        RTRV_T_W_E(AuditMessages.EventID.DICOMInstancesTransferred, AuditMessages.EventActionCode.Read, AuditMessages.EventOutcomeIndicator.MinorFailure,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, false, true, false, null),

        DELETE_PAS(AuditMessages.EventID.DICOMInstancesAccessed, AuditMessages.EventActionCode.Delete, AuditMessages.EventOutcomeIndicator.Success,
                null, null, true, false, false, null),
        DELETE_ERR(AuditMessages.EventID.DICOMInstancesAccessed, AuditMessages.EventActionCode.Delete, AuditMessages.EventOutcomeIndicator.MinorFailure,
                null, null, true, false, false, null),

        PERM_DEL_E(AuditMessages.EventID.DICOMStudyDeleted, AuditMessages.EventActionCode.Delete, AuditMessages.EventOutcomeIndicator.MinorFailure,
                null, null, false, false, false, null),
        PERM_DEL_S(AuditMessages.EventID.DICOMStudyDeleted, AuditMessages.EventActionCode.Delete, AuditMessages.EventOutcomeIndicator.Success,
                null, null, false, false, false, null),

        APPLNSTART(AuditMessages.EventID.ApplicationActivity, AuditMessages.EventActionCode.Execute, AuditMessages.EventOutcomeIndicator.Success,
                null, null, false, false, false, AuditMessages.EventTypeCode.ApplicationStart),
        APPLN_STOP(AuditMessages.EventID.ApplicationActivity, AuditMessages.EventActionCode.Execute, AuditMessages.EventOutcomeIndicator.Success,
                null, null, false, false, false, AuditMessages.EventTypeCode.ApplicationStop),

        QUERY_QIDO(AuditMessages.EventID.Query, AuditMessages.EventActionCode.Execute, AuditMessages.EventOutcomeIndicator.Success,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, true, false, false, null),
        QUERY_FIND(AuditMessages.EventID.Query, AuditMessages.EventActionCode.Execute, AuditMessages.EventOutcomeIndicator.Success,
                AuditMessages.RoleIDCode.Source, AuditMessages.RoleIDCode.Destination, true, false, false, null),


        CONN__RJCT(AuditMessages.EventID.SecurityAlert, AuditMessages.EventActionCode.Execute,
                AuditMessages.EventOutcomeIndicator.MinorFailure, null, null, false, false, false, AuditMessages.EventTypeCode.NodeAuthentication);

        final AuditMessages.EventID eventID;
        final String eventActionCode;
        final String outcomeIndicator;
        final AuditMessages.RoleIDCode source;
        final AuditMessages.RoleIDCode destination;
        final boolean isSource;
        final boolean isDest;
        final boolean isOther;
        final EventTypeCode eventTypeCode;


        EventType(AuditMessages.EventID eventID, String eventActionCode, String outcome, AuditMessages.RoleIDCode source,
                  AuditMessages.RoleIDCode destination, boolean isSource, boolean isDest, boolean isOther, EventTypeCode etc) {
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

    protected void emitAuditMessage(Calendar timestamp, EventIdentification ei, List<ActiveParticipant> apList,
                                 List<ParticipantObjectIdentification> poiList, AuditLogger log) {
        AuditMessage msg = new AuditMessage();
        msg.setEventIdentification(ei);
        for (ActiveParticipant ap : apList)
            msg.getActiveParticipant().add(ap);
        msg.getAuditSourceIdentification().add(log.createAuditSourceIdentification());
        for (ParticipantObjectIdentification poi : poiList)
            msg.getParticipantObjectIdentification().add(poi);
        try {
            log.write(timestamp, msg);
        } catch (Exception e) {
            LOG.warn("Failed to emit audit message", e);
        }
    }

    protected static void deleteFile(Path file) {
        try {
            Files.delete(file);
        } catch (IOException e) {
            LOG.warn("Failed to delete Audit Spool File - {}", file, e);
        }
    }

    protected String buildAET(Device device) {
        String[] aets = device.getApplicationAETitles().toArray(new String[device.getApplicationAETitles().size()]);
        StringBuilder b = new StringBuilder();
        b.append(aets[0]);
        for (int i = 1; i < aets.length; i++)
            b.append(';').append(aets[i]);
        return b.toString();
    }

    protected String getLocalHostName(AuditLogger log) {
        List<Connection> conns = log.getConnections();
        return conns.get(0).getHostname();
    }

    protected String buildAltUserID(String s) {
        return (s != null) ? AuditMessages.alternativeUserIDForAETitle(s) : null;
    }

    protected static class PatientStudyInfo {
        protected static final int REMOTE_HOSTNAME = 0;
        protected static final int CALLING_AET = 1;
        protected static final int CALLED_AET = 2;
        protected static final int STUDY_UID = 3;
        protected static final int ACCESSION_NO = 4;
        protected static final int PATIENT_ID = 5;
        protected static final int PATIENT_NAME = 6;
        protected static final int OUTCOME = 7;

        private final String[] fields;

        protected PatientStudyInfo(StoreContext ctx, Attributes attrs) {
            StoreSession session = ctx.getStoreSession();
            String outcome = (null != ctx.getException()) ? ctx.getException().getMessage(): null;
            fields = new String[] {
                    session.getRemoteHostName(),
                    session.getCallingAET(),
                    session.getCalledAET(),
                    ctx.getStudyInstanceUID(),
                    attrs.getString(Tag.AccessionNumber),
                    attrs.getString(Tag.PatientID, noValue),
                    attrs.getString(Tag.PatientName),
                    outcome
            };
        }

        protected PatientStudyInfo(RetrieveContext ctx, Attributes attrs) {
            String outcome = (null != ctx.getException()) ? ctx.getException().getMessage(): null;
            fields = new String[] {
                    ctx.getHttpRequest().getRemoteAddr(),
                    null,
                    ctx.getLocalAETitle(),
                    ctx.getStudyInstanceUIDs()[0],
                    attrs.getString(Tag.AccessionNumber),
                    attrs.getString(Tag.PatientID, noValue),
                    attrs.getString(Tag.PatientName),
                    outcome
            };
        }

        protected PatientStudyInfo(String s) {
            fields = StringUtils.split(s, '\\');
        }

        protected String getField(int field) {
            return StringUtils.maskEmpty(fields[field], null);
        }

        @Override
        public String toString() {
            return StringUtils.concat(fields, '\\');
        }
    }

    protected static class InstanceInfo {
        protected static final int CLASS_UID = 0;
        protected static final int INSTANCE_UID = 1;
        protected static final int MPPS_UID = 2;
//        protected static final int ACCESSION_NO = 3;

        private final String[] fields;

        protected InstanceInfo(StoreContext ctx, Attributes attrs) {
            ArrayList<String> list = new ArrayList<>();
            list.add(ctx.getSopClassUID());
            list.add(ctx.getSopInstanceUID());
            list.add(StringUtils.maskNull(ctx.getMppsInstanceUID(), ""));
//            Sequence reqAttrs = attrs.getSequence(Tag.RequestAttributesSequence);
//            if (reqAttrs != null)
//                for (Attributes reqAttr : reqAttrs) {
//                    String accno = reqAttr.getString(Tag.AccessionNumber);
//                    if (accno != null)
//                        list.add(accno);
//                }
            this.fields = list.toArray(new String[list.size()]);
        }

        protected InstanceInfo(RetrieveContext ctx, Attributes attrs) {
            ArrayList<String> list = new ArrayList<>();
            list.add(attrs.getString(Tag.SOPClassUID));
            list.add(ctx.getSopInstanceUIDs()[0]);
            list.add("");
//            Sequence reqAttrs = attrs.getSequence(Tag.RequestAttributesSequence);
//            if (reqAttrs != null)
//                for (Attributes reqAttr : reqAttrs) {
//                String accno = reqAttr.getString(Tag.AccessionNumber, "");
//                    if (accno != null)
//                        list.add(accno);
//                }
            this.fields = list.toArray(new String[list.size()]);
        }

        protected InstanceInfo(String s) {
            fields = StringUtils.split(s, '\\');
        }

        protected String getField(int field) {
            return field < fields.length ? fields[field] : null;
        }

        @Override
        public String toString() {
            return StringUtils.concat(fields, '\\');
        }
    }

    protected static class AccessionNumSopClassInfo {
        private final String accNum;
        private HashMap<String, HashSet<String>> sopClassMap = new HashMap<>();

        protected AccessionNumSopClassInfo(String accNum) {
            this.accNum = accNum;
        }

        protected String getAccNum() {
            return accNum;
        }
        protected HashMap<String, HashSet<String>> getSopClassMap() {
            return sopClassMap;
        }
        protected void addSOPInstance(RetrieveStudyInfo rInfo) {
            String cuid = rInfo.getField(RetrieveStudyInfo.SOPCLASSUID);
            HashSet<String> iuids = sopClassMap.get(cuid);
            if (iuids == null) {
                iuids = new HashSet<>();
                sopClassMap.put(cuid, iuids);
            }
            iuids.add(rInfo.getField(RetrieveStudyInfo.SOPINSTANCEUID));
        }
    }

    protected static class RetrieveStudyInfo {
        protected static final int STUDYUID = 0;
        protected static final int ACCESSION = 1;
        protected static final int SOPCLASSUID = 2;
        protected static final int SOPINSTANCEUID = 3;
        protected static final int PATIENTID = 4;
        protected static final int PATIENTNAME = 5;

        private final String[] fields;
        protected RetrieveStudyInfo(Attributes attrs) {
            fields = new String[] {
                    attrs.getString(Tag.StudyInstanceUID),
                    attrs.getString(Tag.AccessionNumber),
                    attrs.getString(Tag.SOPClassUID),
                    attrs.getString(Tag.SOPInstanceUID),
                    attrs.getString(Tag.PatientID, noValue),
                    StringUtils.maskEmpty(attrs.getString(Tag.PatientName), null)
            };
        }
        protected RetrieveStudyInfo(String s) {
            fields = StringUtils.split(s, '\\');
        }
        protected String getField(int field) {
            return StringUtils.maskEmpty(fields[field], null);
        }
        @Override
        public String toString() {
            return StringUtils.concat(fields, '\\');
        }
    }

    protected static class RetrieveInfo {
        protected static final int LOCALAET = 0;
        protected static final int DESTHOST = 1;
        protected static final int DESTAET = 2;
        protected static final int DESTNAPID = 3;
        protected static final int DESTNAPCODE = 4;
        protected static final int REQUESTORHOST = 5;
        protected static final int MOVEAET = 6;
        protected static final int OUTCOME = 7;
        protected static final int PARTIAL_ERROR = 8;

        private final String[] fields;

        protected RetrieveInfo(RetrieveContext ctx, String etFile) {
            String outcome = null != ctx.getException()
                                ? ctx.getException().getMessage()
                                : ctx.warning() != 0 ? ctx.getOutcomeDescription()
                                : ctx.failedSOPInstanceUIDs().length > 0 && etFile.substring(9,10).equals("E")
                                ? ctx.getOutcomeDescription() : null;
            String partialError = ctx.failedSOPInstanceUIDs().length > 0 && etFile.substring(9,10).equals("E")
                                    ? Boolean.toString(true) : Boolean.toString(false);
            String destHost = (null == ctx.getHttpRequest())
                    ? null != ctx.getDestinationHostName() ? ctx.getDestinationHostName() : ctx.getDestinationAETitle()
                    : ctx.getHttpRequest().getRemoteHost();
            String destNapID = (null == ctx.getHttpRequest())
                    ? (null != ctx.getDestinationHostName()) ? ctx.getDestinationHostName() : null
                    : ctx.getHttpRequest().getRemoteAddr();
            String destNapCode = (null != ctx.getDestinationHostName() || null != ctx.getHttpRequest())
                    ? AuditMessages.NetworkAccessPointTypeCode.IPAddress : null;
            fields = new String[] {
                    ctx.getLocalAETitle(),
                    destHost,
                    ctx.getDestinationAETitle(),
                    destNapID,
                    destNapCode,
                    ctx.getRequestorHostName(),
                    ctx.getMoveOriginatorAETitle(),
                    outcome,
                    partialError
            };
        }

        protected RetrieveInfo(String s) {
            fields = StringUtils.split(s, '\\');
        }

        protected String getField(int field) {
            return StringUtils.maskEmpty(fields[field], null);
        }

        @Override
        public String toString() {
            return StringUtils.concat(fields, '\\');
        }
    }

    protected static class DeleteInfo {
        protected static final int LOCALAET = 0;
        protected static final int REMOTEHOST = 1;
        protected static final int REMOTEAET = 2;
        protected static final int STUDYUID = 3;
        protected static final int PATIENTID = 4;
        protected static final int PATIENTNAME = 5;
        protected static final int OUTCOME = 6;

        private final String[] fields;

        protected DeleteInfo(StoreContext ctx) {
            String outcomeDesc = (ctx.getException() != null)
                    ? ctx.getRejectionNote().getRejectionNoteCode().getCodeMeaning() + " - " + ctx.getException().getMessage()
                    : ctx.getRejectionNote().getRejectionNoteCode().getCodeMeaning();
            fields = new String[]{
                    ctx.getStoreSession().getCalledAET(),
                    ctx.getStoreSession().getRemoteHostName(),
                    ctx.getStoreSession().getCallingAET(),
                    ctx.getStudyInstanceUID(),
                    ctx.getAttributes().getString(Tag.PatientID, noValue),
                    StringUtils.maskEmpty(ctx.getAttributes().getString(Tag.PatientName), null),
                    outcomeDesc
            };
        }

        protected DeleteInfo(String s) {
            fields = StringUtils.split(s, '\\');
        }

        protected String getField(int field) {
            return StringUtils.maskEmpty(fields[field], null);
        }

        @Override
        public String toString() {
            return StringUtils.concat(fields, '\\');
        }
    }

    protected static class DeleteStudyInfo {
        protected static final int SOPCLASSUID = 0;
        protected static final int NUMINSTANCES = 1;

        private final String[] fields;
        protected DeleteStudyInfo(String cuid, String numInst) {
            ArrayList<String> list = new ArrayList<>();
            list.add(cuid);
            list.add(numInst);
            this.fields = list.toArray(new String[list.size()]);
        }
        protected DeleteStudyInfo(String s) {
            fields = StringUtils.split(s, '\\');
        }
        protected String getField(int field) {
            return field < fields.length ? fields[field] : null;
        }
        @Override
        public String toString() {
            return StringUtils.concat(fields, '\\');
        }
    }

    protected static class PermanentDeletionInfo {
        protected static final int STUDY_UID = 0;
        protected static final int ACCESSION = 1;
        protected static final int PATIENT_ID = 2;
        protected static final int PATIENT_NAME = 3;
        protected static final int OUTCOME_DESC = 4;

        private final String[] fields;

        protected PermanentDeletionInfo (StudyDeleteContext ctx) {
            String outcomeDesc = (ctx.getException() != null) ? ctx.getException().getMessage() : null;
            String patientName = (null != ctx.getPatient().getPatientName())
                                    ? ctx.getPatient().getPatientName().toString() : null;
            String accessionNo = (ctx.getStudy().getAccessionNumber() != null) ? ctx.getStudy().getAccessionNumber() : null;
            fields = new String[] {
                    ctx.getStudy().getStudyInstanceUID(),
                    accessionNo,
                    ctx.getPatient().getPatientID().getID(),
                    patientName,
                    outcomeDesc
            };
        }
        protected PermanentDeletionInfo(String s) {
            fields = StringUtils.split(s, '\\');
        }

        protected String getField(int field) {
            return StringUtils.maskEmpty(fields[field], null);
        }

        @Override
        public String toString() {
            return StringUtils.concat(fields, '\\');
        }
    }

    protected static class ConnectionRejectedInfo {
        protected static final int REMOTE_ADDR = 0;
        protected static final int LOCAL_ADDR = 1;
        protected static final int OUTCOME_DESC = 2;
        private final String[] fields;

        protected ConnectionRejectedInfo(Connection conn, Socket s, Throwable e) {
            fields = new String[] {
                    s.getRemoteSocketAddress().toString(),
                    conn.getHostname(),
                    e.getMessage(),
            };
        }

        protected ConnectionRejectedInfo(String s) {
            fields = StringUtils.split(s, '\\');
        }
        protected String getField(int field) {
            return StringUtils.maskEmpty(fields[field], null);
        }
        @Override
        public String toString() {
            return StringUtils.concat(fields, '\\');
        }
    }

    protected static class QueryInfo {
        protected static final int CALLING_AET = 0;
        protected static final int REMOTE_HOST = 1;
        protected static final int CALLED_AET = 2;
        protected static final int SOPCLASSUID = 3;
        protected static final int PATIENT_ID = 4;
        protected static final int QUERY_STRING = 5;

        private final String[] fields;

        protected QueryInfo(QueryContext ctx) {
            String queryString = (ctx.getHttpRequest() != null)
                    ? ctx.getHttpRequest().getRequestURI() + ctx.getHttpRequest().getQueryString()
                    : null;
            String patientID = (ctx.getQueryKeys() != null && ctx.getQueryKeys().getString(Tag.PatientID) != null)
                    ? ctx.getQueryKeys().getString(Tag.PatientID) : noValue;
            fields = new String[] {
                    ctx.getCallingAET(),
                    ctx.getRemoteHostName(),
                    ctx.getCalledAET(),
                    ctx.getSOPClassUID(),
                    patientID,
                    queryString
            };
        }

        protected QueryInfo(String s) {
            fields = StringUtils.split(s, '\\');
        }
        protected String getField(int field) {
            return StringUtils.maskEmpty(fields[field], null);
        }
        @Override
        public String toString() {
            return StringUtils.concat(fields, '\\');
        }
    }
}
