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
import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.hl7.IHL7ApplicationCache;
import org.dcm4che3.data.*;
import org.dcm4che3.hl7.HL7Message;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.net.*;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4che3.net.audit.AuditLoggerDeviceExtension;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.HL7DeviceExtension;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.ArchiveServiceEvent;
import org.dcm4chee.arc.ConnectionEvent;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.event.BulkQueueMessageEvent;
import org.dcm4chee.arc.event.QueueMessageEvent;
import org.dcm4chee.arc.event.SoftwareConfiguration;
import org.dcm4chee.arc.keycloak.KeycloakContext;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.delete.StudyDeleteContext;
import org.dcm4chee.arc.entity.RejectionState;
import org.dcm4chee.arc.retrieve.ExternalRetrieveContext;
import org.dcm4chee.arc.event.RejectionNoteSent;
import org.dcm4chee.arc.exporter.ExportContext;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.procedure.ProcedureContext;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.retrieve.InstanceLocations;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.stgcmt.StgCmtEventInfo;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreSession;
import org.dcm4chee.arc.study.StudyMgtContext;
import org.dcm4chee.arc.qmgt.HttpServletRequestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Feb 2016
 */
@ApplicationScoped
public class AuditService {
    private final Logger LOG = LoggerFactory.getLogger(AuditService.class);
    private final String studyDate = "StudyDate";

    @Inject
    private Device device;

    @Inject
    private IHL7ApplicationCache hl7AppCache;

    private void aggregateAuditMessage(AuditLogger auditLogger, Path path) throws Exception {
        AuditServiceUtils.EventType eventType = AuditServiceUtils.EventType.fromFile(path);
        if (path.toFile().length() == 0) {
            LOG.warn("Attempt to read from an empty file.", eventType, path);
            return;
        }
        switch (eventType.eventClass) {
            case APPLN_ACTIVITY:
                auditApplicationActivity(auditLogger, path, eventType);
                break;
            case CONN_REJECT:
                auditConnectionRejected(auditLogger, path, eventType);
                break;
            case STORE_WADOR:
                auditStoreOrWADORetrieve(auditLogger, path, eventType);
                break;
            case RETRIEVE:
                auditRetrieve(auditLogger, path, eventType);
                break;
            case USER_DELETED:
            case SCHEDULER_DELETED:
                auditDeletion(auditLogger, path, eventType);
                break;
            case QUERY:
                auditQuery(auditLogger, path, eventType);
                break;
            case HL7:
                auditPatientRecord(auditLogger, path, eventType);
                break;
            case PROC_STUDY:
                auditProcedureRecord(auditLogger, path, eventType);
                break;
            case PROV_REGISTER:
                auditProvideAndRegister(auditLogger, path, eventType);
                break;
            case STGCMT:
                auditStorageCommit(auditLogger, path, eventType);
                break;
            case INST_RETRIEVED:
                auditExternalRetrieve(auditLogger, path, eventType);
                break;
            case LDAP_CHANGES:
                auditSoftwareConfiguration(auditLogger, path, eventType);
                break;
            case QUEUE_EVENT:
                auditQueueMessageEvent(auditLogger, path, eventType);
                break;
        }
    }

    void spoolApplicationActivity(ArchiveServiceEvent event) {
        if (event.getType() == ArchiveServiceEvent.Type.RELOADED)
            return;

        HttpServletRequest req = event.getRequest();
        AuditInfoBuilder info = req != null
                              ? restfulTriggeredApplicationActivityInfo(req)
                              : systemTriggeredApplicationActivityInfo();
        writeSpoolFile(AuditServiceUtils.EventType.forApplicationActivity(event), info);
    }

    private AuditInfoBuilder systemTriggeredApplicationActivityInfo() {
        return new AuditInfoBuilder.Builder().calledUserID(device.getDeviceName()).build();
    }

    private AuditInfoBuilder restfulTriggeredApplicationActivityInfo(HttpServletRequest req) {
        return new AuditInfoBuilder.Builder()
                .calledUserID(req.getRequestURI())
                .callingUserID(KeycloakContext.valueOf(req).getUserName())
                .callingHost(req.getRemoteAddr())
                .build();
    }

    private void auditApplicationActivity(AuditLogger auditLogger, Path path, AuditServiceUtils.EventType eventType)
            throws IOException {
        SpoolFileReader reader = new SpoolFileReader(path);
        EventIdentificationBuilder ei = toBuildEventIdentification(eventType, null, getEventTime(path, auditLogger));
        AuditInfo archiveInfo = new AuditInfo(reader.getMainInfo());
        ActiveParticipantBuilder[] activeParticipantBuilder = buildApplicationActivityActiveParticipants(auditLogger, eventType, archiveInfo);
        emitAuditMessage(auditLogger, ei, activeParticipantBuilder);
    }

    private ActiveParticipantBuilder[] buildApplicationActivityActiveParticipants(
            AuditLogger auditLogger, AuditServiceUtils.EventType eventType, AuditInfo archiveInfo) {
        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[2];
        String archiveUserID = archiveInfo.getField(AuditInfo.CALLED_USERID);
        activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                                archiveUserID,
                                getLocalHostName(auditLogger))
                                .userIDTypeCode(archiveUserIDTypeCode(archiveUserID))
                                .altUserID(AuditLogger.processID())
                                .roleIDCode(eventType.destination)
                                .build();
        if (isServiceUserTriggered(archiveInfo.getField(AuditInfo.CALLING_USERID))) {
            String userID = archiveInfo.getField(AuditInfo.CALLING_USERID);
            activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(
                    userID,
                    archiveInfo.getField(AuditInfo.CALLING_HOST))
                    .userIDTypeCode(AuditMessages.userIDTypeCode(userID))
                    .requester(true)
                    .roleIDCode(eventType.source)
                    .build();
        }
        return activeParticipantBuilder;
    }

    private void spoolInstancesDeleted(StoreContext ctx) {
        Attributes attrs = ctx.getAttributes();
        HashMap<String, HashSet<String>> sopClassMap = buildRejectionSOPClassMap(attrs);
        LinkedHashSet<Object> deleteObjs = getDeletionObjsForSpooling(sopClassMap, new AuditInfo(getAIStoreCtx(ctx)));
        AuditServiceUtils.EventType eventType = ctx.getStoredInstance().getSeries().getStudy().getRejectionState() == RejectionState.COMPLETE
                                                    ? AuditServiceUtils.EventType.RJ_COMPLET
                                                    : AuditServiceUtils.EventType.RJ_PARTIAL;
        writeSpoolFile(eventType, deleteObjs);
    }

    private HashMap<String, HashSet<String>> buildRejectionSOPClassMap(Attributes attrs) {
        HashMap<String, HashSet<String>> sopClassMap = new HashMap<>();
        for (Attributes studyRef : attrs.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence))
            for (Attributes seriesRef : studyRef.getSequence(Tag.ReferencedSeriesSequence))
                for (Attributes sopRef : seriesRef.getSequence(Tag.ReferencedSOPSequence))
                    buildSOPClassMap(sopClassMap, sopRef.getString(Tag.ReferencedSOPClassUID),
                            sopRef.getString(Tag.ReferencedSOPInstanceUID));
        return sopClassMap;
    }

    void spoolStudyDeleted(StudyDeleteContext ctx) {
        HashMap<String, HashSet<String>> sopClassMap = new HashMap<>();
        for (org.dcm4chee.arc.entity.Instance i : ctx.getInstances())
            buildSOPClassMap(sopClassMap, i.getSopClassUID(), i.getSopInstanceUID());
        HttpServletRequestInfo httpServletRequestInfo = ctx.getHttpServletRequestInfo();
        AuditInfoBuilder i = httpServletRequestInfo != null ? buildPermDeletionAuditInfoForWeb(httpServletRequestInfo, ctx)
                : buildPermDeletionAuditInfoForScheduler(ctx);
        AuditServiceUtils.EventType eventType = httpServletRequestInfo != null
                                                ? AuditServiceUtils.EventType.RJ_COMPLET
                                                : AuditServiceUtils.EventType.PRMDLT_SCH;
        LinkedHashSet<Object> deleteObjs = getDeletionObjsForSpooling(sopClassMap, new AuditInfo(i));
        writeSpoolFile(eventType, deleteObjs);
    }

    void spoolExternalRejection(RejectionNoteSent rejectionNoteSent) {
        Attributes attrs = rejectionNoteSent.getRejectionNote();

        LinkedHashSet<AuditInfoBuilder> externalRejectionClientAuditInfo = new LinkedHashSet<>();
        externalRejectionClientAuditInfo.add(externalRejectionClientAuditInfo(rejectionNoteSent));
        externalRejectionClientAuditInfo.addAll(rejectionSOPRefs(attrs));

        AuditServiceUtils.EventType clientET = rejectionNoteSent.isStudyDeleted()
                ? AuditServiceUtils.EventType.RJ_COMPLET
                : AuditServiceUtils.EventType.RJ_PARTIAL;
        writeSpoolFile(clientET, externalRejectionClientAuditInfo.toArray(
                new AuditInfoBuilder[externalRejectionClientAuditInfo.size()]));
    }

    private LinkedHashSet<AuditInfoBuilder> rejectionSOPRefs(Attributes attrs) {
        LinkedHashSet<AuditInfoBuilder> rejectionSOPRefs = new LinkedHashSet<>();
        HashMap<String, HashSet<String>> sopClassMap = buildRejectionSOPClassMap(attrs);
        for (Map.Entry<String, HashSet<String>> entry : sopClassMap.entrySet())
            rejectionSOPRefs.add(new AuditInfoBuilder.Builder().sopCUID(entry.getKey())
                    .sopIUID(String.valueOf(entry.getValue().size())).build());

        return rejectionSOPRefs;
    }

    private AuditInfoBuilder externalRejectionClientAuditInfo(RejectionNoteSent rejectionNoteSent) {
        HttpServletRequest req = rejectionNoteSent.getRequest();
        Attributes attrs = rejectionNoteSent.getRejectionNote();
        Attributes codeItem = attrs.getSequence(Tag.ConceptNameCodeSequence).get(0);
        return new AuditInfoBuilder.Builder()
                .callingUserID(KeycloakContext.valueOf(req).getUserName())
                .callingHost(req.getRemoteHost())
                .calledUserID(req.getRequestURI())
                .calledHost(toHost(rejectionNoteSent.getRemoteAE()))
                .outcome(codeItem.getString(Tag.CodeMeaning))
                .studyUIDAccNumDate(attrs)
                .pIDAndName(attrs, getArchiveDevice())
                .build();
    }

    private String toHost(ApplicationEntity ae) {
        StringBuilder b = new StringBuilder();
        List<Connection> connections = ae.getConnections();
        if (!connections.isEmpty()) {
            b.append(connections.get(0).getHostname());
            for (int i = 1; i < connections.size(); i++)
                b.append(';').append(connections.get(i).getHostname());
        }
        return b.toString();
    }

    void spoolQueueMessageEvent(QueueMessageEvent queueMsgEvent) {
        QueueMessage queueMsg = queueMsgEvent.getQueueMsg();
        if (queueMsg == null)
            return;

        HttpServletRequest req = queueMsgEvent.getRequest();
        AuditInfoBuilder info = new AuditInfoBuilder.Builder()
                .callingUserID(KeycloakContext.valueOf(req).getUserName())
                .callingHost(req.getRemoteHost())
                .calledUserID(req.getRequestURI())
                .outcome(getOD(queueMsgEvent.getException()))
                .queueMsg(toString(queueMsg))
                .taskPOID(queueMsg.getMessageID())
                .build();
        writeSpoolFile(AuditServiceUtils.EventType.forQueueEvent(queueMsgEvent.getOperation()), info);
    }

    void spoolBulkQueueMessageEvent(BulkQueueMessageEvent bulkQueueMsgEvent) {
        HttpServletRequest req = bulkQueueMsgEvent.getRequest();
        AuditInfoBuilder info = new AuditInfoBuilder.Builder()
                .callingUserID(KeycloakContext.valueOf(req).getUserName())
                .callingHost(req.getRemoteHost())
                .calledUserID(req.getRequestURI())
                .outcome(getOD(bulkQueueMsgEvent.getException()))
                .filters(buildStrings(bulkQueueMsgEvent.getFilters()))
                .count(bulkQueueMsgEvent.getCount())
                .taskPOID(bulkQueueMsgEvent.getOperation().name())
                .build();
        writeSpoolFile(AuditServiceUtils.EventType.forQueueEvent(bulkQueueMsgEvent.getOperation()), info);
    }

    private void auditQueueMessageEvent(AuditLogger auditLogger, Path path, AuditServiceUtils.EventType eventType)
            throws IOException {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo info = new AuditInfo(reader.getMainInfo());

        EventIdentificationBuilder eiBuilder = toBuildEventIdentification(
                eventType, info.getField(AuditInfo.OUTCOME), getEventTime(path, auditLogger));
        ActiveParticipantBuilder[] apBuilder = new ActiveParticipantBuilder[2];
        String callingUserID = info.getField(AuditInfo.CALLING_USERID);
        String calledUserID = info.getField(AuditInfo.CALLED_USERID);
        AuditMessages.UserIDTypeCode archiveUserIDTypeCode = archiveUserIDTypeCode(calledUserID);
        apBuilder[0] = new ActiveParticipantBuilder.Builder(
                callingUserID,
                info.getField(AuditInfo.CALLING_HOST))
                .userIDTypeCode(callingUserIDTypeCode(archiveUserIDTypeCode, callingUserID))
                .requester(true).build();
        apBuilder[1] = new ActiveParticipantBuilder.Builder(
                calledUserID,
                getLocalHostName(auditLogger))
                .userIDTypeCode(archiveUserIDTypeCode)
                .build();
        ParticipantObjectIdentificationBuilder poiBuilder = info.getField(AuditInfo.QUEUE_MSG) != null
                ? buildTaskPOI(info)
                : buildTasksPOI(info);
        emitAuditMessage(auditLogger, eiBuilder, apBuilder, poiBuilder);
    }

    private ParticipantObjectIdentificationBuilder buildTaskPOI(AuditInfo info) {
        return new ParticipantObjectIdentificationBuilder.Builder(
                info.getField(AuditInfo.TASK_POID),
                AuditMessages.ParticipantObjectIDTypeCode.TASK,
                AuditMessages.ParticipantObjectTypeCode.SystemObject,
                null)
                .detail(getPod("Task", info.getField(AuditInfo.QUEUE_MSG)))
                .build();
    }

    private ParticipantObjectIdentificationBuilder buildTasksPOI(AuditInfo info) {
        return new ParticipantObjectIdentificationBuilder.Builder(
                info.getField(AuditInfo.TASK_POID),
                AuditMessages.ParticipantObjectIDTypeCode.TASKS,
                AuditMessages.ParticipantObjectTypeCode.SystemObject,
                null)
                .detail(getPod("Filters", info.getField(AuditInfo.FILTERS)),
                        getPod("Count", info.getField(AuditInfo.COUNT)))
                .build();
    }

    void spoolSoftwareConfiguration(SoftwareConfiguration softwareConfiguration) {
        HttpServletRequest request = softwareConfiguration.getRequest();
        AuditInfoBuilder info = new AuditInfoBuilder.Builder()
                                .callingUserID(KeycloakContext.valueOf(request).getUserName())
                                .callingHost(request.getRemoteAddr())
                                .calledUserID(softwareConfiguration.getDeviceName())
                                .build();
        writeSpoolFile(AuditServiceUtils.EventType.LDAP_CHNGS, info, softwareConfiguration.getLdapDiff().toString());
    }

    private void auditSoftwareConfiguration(AuditLogger auditLogger, Path path, AuditServiceUtils.EventType eventType)
            throws IOException {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());

        EventIdentificationBuilder ei = toBuildEventIdentification(eventType, null, getEventTime(path, auditLogger));
        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[1];
        String callingUserID = auditInfo.getField(AuditInfo.CALLING_USERID);
        activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                callingUserID,
                auditInfo.getField(AuditInfo.CALLING_HOST))
                .userIDTypeCode(AuditMessages.userIDTypeCode(callingUserID))
                .requester(true).build();
        ParticipantObjectIdentificationBuilder poiLDAPDiff = new ParticipantObjectIdentificationBuilder.Builder(auditInfo.getField(
                                                    AuditInfo.CALLED_USERID),
                                                    AuditMessages.ParticipantObjectIDTypeCode.DeviceName,
                                                    AuditMessages.ParticipantObjectTypeCode.SystemObject,
                                                    null).detail(getPod("Alert Description", getData(reader)))
                                                    .build();
        emitAuditMessage(auditLogger, ei, activeParticipantBuilder, poiLDAPDiff);
    }

    private AuditInfoBuilder buildPermDeletionAuditInfoForWeb(
            HttpServletRequestInfo httpServletRequestInfo, StudyDeleteContext ctx) {
        return new AuditInfoBuilder.Builder()
                .callingUserID(httpServletRequestInfo.requesterUserID)
                .callingHost(httpServletRequestInfo.requesterHost)
                .calledUserID(httpServletRequestInfo.requestURI)
                .studyUIDAccNumDate(ctx.getStudy().getAttributes())
                .pIDAndName(ctx.getPatient().getAttributes(), getArchiveDevice())
                .outcome(getOD(ctx.getException()))
                .build();
    }

    private AuditInfoBuilder buildPermDeletionAuditInfoForScheduler(StudyDeleteContext ctx) {
        return new AuditInfoBuilder.Builder()
                .studyUIDAccNumDate(ctx.getStudy().getAttributes())
                .pIDAndName(ctx.getPatient().getAttributes(), getArchiveDevice())
                .outcome(getOD(ctx.getException()))
                .build();
    }

    private void auditDeletion(AuditLogger auditLogger, Path path, AuditServiceUtils.EventType eventType) throws IOException {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        boolean userDeleted = eventType.eventClass == AuditServiceUtils.EventClass.USER_DELETED;
        EventIdentificationBuilder ei = toCustomBuildEventIdentification(eventType, auditInfo.getField(AuditInfo.OUTCOME),
                auditInfo.getField(AuditInfo.WARNING), getEventTime(path, auditLogger));
        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[2];
        if (userDeleted) {
            String archiveUserID = auditInfo.getField(AuditInfo.CALLED_USERID);
            AuditMessages.UserIDTypeCode archiveUserIDTypeCode = archiveUserIDTypeCode(archiveUserID);
            String callingUserID = auditInfo.getField(AuditInfo.CALLING_USERID);
            activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                    callingUserID,
                    auditInfo.getField(AuditInfo.CALLING_HOST))
                    .userIDTypeCode(callingUserIDTypeCode(archiveUserIDTypeCode, callingUserID))
                    .requester(true).build();
            activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(
                    archiveUserID,
                    getLocalHostName(auditLogger))
                    .userIDTypeCode(archiveUserIDTypeCode)
                    .altUserID(AuditLogger.processID())
                    .build();
        } else
            activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                    device.getDeviceName(),
                    getLocalHostName(auditLogger))
                    .userIDTypeCode(AuditMessages.UserIDTypeCode.DeviceName)
                    .altUserID(AuditLogger.processID())
                    .requester(true).build();

        
        ParticipantObjectDescriptionBuilder desc = new ParticipantObjectDescriptionBuilder.Builder()
                .sopC(sopClasses(reader.getInstanceLines()))
                .acc(accessions(auditInfo.getField(AuditInfo.ACC_NUM))).build();
        
        ParticipantObjectIdentificationBuilder poiStudy = new ParticipantObjectIdentificationBuilder.Builder(
                auditInfo.getField(AuditInfo.STUDY_UID), 
                AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID,
                AuditMessages.ParticipantObjectTypeCode.SystemObject,
                AuditMessages.ParticipantObjectTypeCodeRole.Report)
                .desc(desc)
                .detail(getPod(studyDate, auditInfo.getField(AuditInfo.STUDY_DATE)))
                .build();
        emitAuditMessage(auditLogger, ei, activeParticipantBuilder, poiStudy, patientPOI(auditInfo));
    }

    void spoolExternalRetrieve(ExternalRetrieveContext ctx) {
        String outcome = ctx.getResponse().getString(Tag.ErrorComment) != null
                            ? ctx.getResponse().getString(Tag.ErrorComment) + ctx.failed()
                            : null;
        String warning = ctx.warning() > 0
                            ? "Number Of Warning Sub operations" + ctx.warning()
                            : null;
        AuditInfoBuilder info = new AuditInfoBuilder.Builder()
                                .callingUserID(ctx.getRequesterUserID())
                                .callingHost(ctx.getRequesterHostName())
                                .calledHost(ctx.getRemoteHostName())
                                .calledUserID(ctx.getRemoteAET())
                                .moveAET(ctx.getRequestURI())
                                .destAET(ctx.getDestinationAET())
                                .warning(warning)
                                .studyUIDAccNumDate(ctx.getKeys())
                                .outcome(outcome)
                                .build();
        writeSpoolFile(AuditServiceUtils.EventType.INST_RETRV, info);
    }

    private void auditExternalRetrieve(AuditLogger auditLogger, Path path, AuditServiceUtils.EventType eventType)
            throws IOException {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo i = new AuditInfo(reader.getMainInfo());
        EventIdentificationBuilder ei = toCustomBuildEventIdentification(eventType, i.getField(AuditInfo.OUTCOME),
                i.getField(AuditInfo.WARNING), getEventTime(path, auditLogger));

        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[4];
        String userID = i.getField(AuditInfo.CALLING_USERID);
        activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                                userID,
                                i.getField(AuditInfo.CALLING_HOST))
                                .userIDTypeCode(AuditMessages.userIDTypeCode(userID))
                                .requester(true)
                                .build();
        activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(
                                i.getField(AuditInfo.MOVEAET),
                                getLocalHostName(auditLogger))
                                .userIDTypeCode(AuditMessages.UserIDTypeCode.URI)
                                .altUserID(AuditLogger.processID())
                                .build();
        activeParticipantBuilder[2] = new ActiveParticipantBuilder.Builder(
                                i.getField(AuditInfo.CALLED_USERID),
                                i.getField(AuditInfo.CALLED_HOST))
                                .userIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle)
                                .roleIDCode(eventType.source)
                                .build();
        activeParticipantBuilder[3] = new ActiveParticipantBuilder.Builder(
                                i.getField(AuditInfo.DEST_AET),
                                i.getField(AuditInfo.DEST_NAP_ID))
                                .userIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle)
                                .roleIDCode(eventType.destination)
                                .build();
        ParticipantObjectIdentificationBuilder studyPOI = new ParticipantObjectIdentificationBuilder.Builder(
                                                            i.getField(AuditInfo.STUDY_UID),
                                                            AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID,
                                                            AuditMessages.ParticipantObjectTypeCode.SystemObject,
                                                            AuditMessages.ParticipantObjectTypeCodeRole.Report)
                                                            .build();
        emitAuditMessage(auditLogger, ei, activeParticipantBuilder, studyPOI);
    }

    void spoolConnectionRejected(ConnectionEvent event) {
        AuditInfoBuilder info = new AuditInfoBuilder.Builder()
                            .callingHost(event.getSocket().getRemoteSocketAddress().toString())
                            .calledHost(event.getConnection().getHostname())
                            .outcome(event.getException().getMessage())
                            .build();
        writeSpoolFile(AuditServiceUtils.EventType.CONN__RJCT, info);
    }

    private void auditConnectionRejected(AuditLogger auditLogger, Path path, AuditServiceUtils.EventType eventType)
            throws IOException {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo crI = new AuditInfo(reader.getMainInfo());
        EventIdentificationBuilder ei = toBuildEventIdentification(eventType, crI.getField(AuditInfo.OUTCOME), getEventTime(path, auditLogger));
        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[2];
        activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                                device.getDeviceName(),
                                crI.getField(AuditInfo.CALLED_HOST))
                                .userIDTypeCode(AuditMessages.UserIDTypeCode.DeviceName)
                                .altUserID(AuditLogger.processID())
                                .build();
        String userID, napID;
        userID = napID = crI.getField(AuditInfo.CALLING_HOST);
        activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(userID, napID)
                                        .userIDTypeCode(AuditMessages.UserIDTypeCode.NodeID)
                                        .requester(true).build();

        ParticipantObjectIdentificationBuilder poi = new ParticipantObjectIdentificationBuilder.Builder(
                                                    crI.getField(AuditInfo.CALLING_HOST),
                                                    AuditMessages.ParticipantObjectIDTypeCode.NodeID,
                                                    AuditMessages.ParticipantObjectTypeCode.SystemObject,
                                                    null)
                                                    .build();
        emitAuditMessage(auditLogger, ei, activeParticipantBuilder, poi);
    }

    void spoolQuery(QueryContext ctx) {
        boolean auditAggregate = getArchiveDevice().isAuditAggregate();
        AuditLoggerDeviceExtension ext = device.getDeviceExtension(AuditLoggerDeviceExtension.class);
        AuditServiceUtils.EventType eventType = AuditServiceUtils.EventType.QUERY__EVT;
        AuditInfo auditInfo = ctx.getHttpRequest() != null ? createAuditInfoForQIDO(ctx) : createAuditInfoForFIND(ctx);
        for (AuditLogger auditLogger : ext.getAuditLoggers()) {
            if (!isSpoolingSuppressed(eventType, ctx.getCallingAET(), auditLogger)) {
                Path directory = toDirPath(auditLogger);
                try {
                    Files.createDirectories(directory);
                    Path file = Files.createTempFile(directory, String.valueOf(eventType), null);
                    try (BufferedOutputStream out = new BufferedOutputStream(
                            Files.newOutputStream(file, StandardOpenOption.APPEND))) {
                        new DataOutputStream(out).writeUTF(auditInfo.toString());
                        if (ctx.getAssociation() != null) {
                            try (DicomOutputStream dos = new DicomOutputStream(out, UID.ImplicitVRLittleEndian)) {
                                dos.writeDataset(null, ctx.getQueryKeys());
                            } catch (Exception e) {
                                LOG.warn("Failed to create DicomOutputStream : ", e);
                            }
                        }
                    }
                    if (!auditAggregate)
                        auditAndProcessFile(auditLogger, file);
                } catch (Exception e) {
                    LOG.warn("Failed to write to Audit Spool File - {}", auditLogger.getCommonName(), e);
                }
            }
        }
    }

    private AuditInfo createAuditInfoForFIND(QueryContext ctx) {
        return new AuditInfo(
                new AuditInfoBuilder.Builder()
                        .callingHost(ctx.getRemoteHostName())
                        .callingUserID(ctx.getCallingAET())
                        .calledUserID(ctx.getCalledAET())
                        .queryPOID(ctx.getSOPClassUID())
                        .build());
    }

    private AuditInfo createAuditInfoForQIDO(QueryContext ctx) {
        HttpServletRequest httpRequest = ctx.getHttpRequest();
        return new AuditInfo(
                new AuditInfoBuilder.Builder()
                        .callingHost(ctx.getRemoteHostName())
                        .callingUserID(KeycloakContext.valueOf(ctx.getHttpRequest()).getUserName())
                        .calledUserID(httpRequest.getRequestURI())
                        .queryPOID(ctx.getSearchMethod())
                        .queryString(httpRequest.getRequestURI() + httpRequest.getQueryString())
                        .build());
    }

    private boolean isSpoolingSuppressed(AuditServiceUtils.EventType eventType, String userID, AuditLogger auditLogger) {
        return !auditLogger.isInstalled()
                || (!auditLogger.getAuditSuppressCriteriaList().isEmpty()
                    && auditLogger.isAuditMessageSuppressed(createMinimalAuditMsg(eventType, userID)));
    }

    private AuditMessage createMinimalAuditMsg(AuditServiceUtils.EventType eventType, String userID) {
        AuditMessage msg = new AuditMessage();
        msg.setEventIdentification(
                AuditMessages.toEventIdentification(toBuildEventIdentification(eventType, null, null)));
        ActiveParticipant ap = new ActiveParticipant();
        ap.setUserID(userID);
        ap.setUserIsRequestor(true);
        msg.getActiveParticipant().add(ap);
        return msg;
    }

    void auditAndProcessFile(AuditLogger auditLogger, Path file) {
        try {
            aggregateAuditMessage(auditLogger, file);
            Files.delete(file);
        } catch (Exception e) {
            LOG.warn("Failed to process Audit Spool File - {}", auditLogger.getCommonName(), file, e);
            try {
                Files.move(file, file.resolveSibling(file.getFileName().toString() + ".failed"));
            } catch (IOException e1) {
                LOG.warn("Failed to mark Audit Spool File - {} as failed", auditLogger.getCommonName(), file, e);
            }
        }
    }

    private void auditQuery(
            AuditLogger auditLogger, Path file, AuditServiceUtils.EventType eventType) throws IOException {
        AuditInfo qrI;
        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[2];
        EventIdentificationBuilder ei = toBuildEventIdentification(eventType, null, getEventTime(file, auditLogger));
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            qrI = new AuditInfo(new DataInputStream(in).readUTF());
            String archiveUserID = qrI.getField(AuditInfo.CALLED_USERID);
            String callingUserID = qrI.getField(AuditInfo.CALLING_USERID);
            AuditMessages.UserIDTypeCode archiveUserIDTypeCode = archiveUserIDTypeCode(archiveUserID);
            activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                                    callingUserID,
                                    qrI.getField(AuditInfo.CALLING_HOST))
                                    .userIDTypeCode(callingUserIDTypeCode(archiveUserIDTypeCode, callingUserID))
                                    .requester(true)
                                    .roleIDCode(eventType.source)
                                    .build();
            activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(
                                    archiveUserID,
                                    getLocalHostName(auditLogger))
                                    .userIDTypeCode(archiveUserIDTypeCode)
                                    .altUserID(AuditLogger.processID())
                                    .roleIDCode(eventType.destination)
                                    .build();
            ParticipantObjectIdentificationBuilder poi;
            if (archiveUserIDTypeCode == AuditMessages.UserIDTypeCode.URI) {
                poi = new ParticipantObjectIdentificationBuilder.Builder(
                        qrI.getField(AuditInfo.Q_POID),
                        AuditMessages.ParticipantObjectIDTypeCode.QIDO_QUERY,
                        AuditMessages.ParticipantObjectTypeCode.SystemObject,
                        AuditMessages.ParticipantObjectTypeCodeRole.Query)
                        .query(qrI.getField(AuditInfo.Q_STRING).getBytes())
                        .detail(getPod("QueryEncoding", String.valueOf(StandardCharsets.UTF_8)))
                        .build();
            }
            else {
                byte[] buffer = new byte[(int) Files.size(file)];
                int len = in.read(buffer);
                byte[] data;
                if (len != -1) {
                    data = new byte[len];
                    System.arraycopy(buffer, 0, data, 0, len);
                }
                else {
                    data = new byte[0];
                }
                poi = new ParticipantObjectIdentificationBuilder.Builder(
                        qrI.getField(AuditInfo.Q_POID),
                        AuditMessages.ParticipantObjectIDTypeCode.SOPClassUID,
                        AuditMessages.ParticipantObjectTypeCode.SystemObject,
                        AuditMessages.ParticipantObjectTypeCodeRole.Report)
                        .query(data)
                        .detail(getPod("TransferSyntax", UID.ImplicitVRLittleEndian))
                        .build();
            }
            emitAuditMessage(auditLogger, ei, activeParticipantBuilder, poi);
        }
    }

    void spoolInstanceStored(StoreContext ctx) {
        if (ctx.getRejectionNote() != null) {
            spoolInstancesDeleted(ctx);
            return;
        }

        if (isDuplicateReceivedInstance(ctx))
            return;

        AuditServiceUtils.EventType eventType = AuditServiceUtils.EventType.forInstanceStored(ctx);

        StoreSession ss = ctx.getStoreSession();
        HttpServletRequest req = ss.getHttpRequest();
        String callingUserID = req != null
                ? KeycloakContext.valueOf(req).getUserName()
                : ss.getCallingAET();

        String rjNoteMeaning = ctx.getException() == null && null != ctx.getRejectionNote()
                            ? ctx.getRejectionNote().getRejectionNoteCode().getCodeMeaning() : null;
        String exception = getOD(ctx.getException());
        String outcome = ctx.getRejectionNote() == null
                            ? exception
                            : exception + " - " + rjNoteMeaning;

        AuditInfoBuilder instanceInfo = new AuditInfoBuilder.Builder()
                .sopCUID(ctx.getSopClassUID()).sopIUID(ctx.getSopInstanceUID())
                .mppsUID(ctx.getMppsInstanceUID())
                .build();

        ArchiveDeviceExtension arcDev = getArchiveDevice();
        Attributes attr = ctx.getAttributes();
        AuditInfoBuilder info = new AuditInfoBuilder.Builder().callingHost(ss.getRemoteHostName())
                .callingUserID(callingUserID)
                .calledUserID(req != null ? req.getRequestURI() : ss.getCalledAET())
                .studyUIDAccNumDate(attr)
                .pIDAndName(attr, arcDev)
                .outcome(outcome)
                .warning(rjNoteMeaning)
                .build();

        if (ctx.getException() != null)
            writeSpoolFile(eventType, info, instanceInfo);
        else {
            String fileName = getFileName(
                    eventType, callingUserID.replace('|', '-'),
                    ctx.getStoreSession().getCalledAET(), ctx.getStudyInstanceUID());
            writeSpoolFileStoreOrWadoRetrieve(fileName, info, instanceInfo);
        }
    }

    private boolean isDuplicateReceivedInstance(StoreContext ctx) {
        return ctx.getLocations().isEmpty() && ctx.getStoredInstance() == null && ctx.getException() == null;
    }
    
    void spoolRetrieveWADO(RetrieveContext ctx) {
        HttpServletRequestInfo req = ctx.getHttpServletRequestInfo();
        Collection<InstanceLocations> il = ctx.getMatches();
        Attributes attrs = new Attributes();
        for (InstanceLocations i : il)
            attrs = i.getAttributes();
        String fileName = getFileName(AuditServiceUtils.EventType.WADO___URI, req.requesterHost,
                ctx.getLocalAETitle(), ctx.getStudyInstanceUIDs()[0]);
        AuditInfoBuilder info = new AuditInfoBuilder.Builder()
                                .callingHost(req.requesterHost)
                                .callingUserID(req.requesterUserID)
                                .calledUserID(req.requestURI)
                                .studyUIDAccNumDate(attrs)
                                .pIDAndName(attrs, getArchiveDevice())
                                .outcome(null != ctx.getException() ? ctx.getException().getMessage() : null)
                                .build();
        AuditInfoBuilder instanceInfo = new AuditInfoBuilder.Builder()
                                        .sopCUID(attrs.getString(Tag.SOPClassUID))
                                        .sopIUID(ctx.getSopInstanceUIDs()[0])
                                        .build();
        writeSpoolFileStoreOrWadoRetrieve(fileName, info, instanceInfo);
    }

    private void buildSOPClassMap(HashMap<String, HashSet<String>> sopClassMap, String cuid, String iuid) {
        HashSet<String> iuids = sopClassMap.get(cuid);
        if (iuids == null) {
            iuids = new HashSet<>();
            sopClassMap.put(cuid, iuids);
        }
        iuids.add(iuid);
    }

    private void auditStoreOrWADORetrieve(AuditLogger auditLogger, Path path,
                                          AuditServiceUtils.EventType eventType) throws IOException {
        SpoolFileReader reader = new SpoolFileReader(path);
        Set<String> mppsUIDs = new HashSet<>();
        HashMap<String, HashSet<String>> sopClassMap = new HashMap<>();
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        for (String line : reader.getInstanceLines()) {
            AuditInfo iI = new AuditInfo(line);
            buildSOPClassMap(sopClassMap, iI.getField(AuditInfo.SOP_CUID), iI.getField(AuditInfo.SOP_IUID));
            String mppsUID = iI.getField(AuditInfo.MPPS_UID);
            if (mppsUID != null)
                mppsUIDs.add(mppsUID);
        }

        EventIdentificationBuilder ei = toBuildEventIdentification(eventType, auditInfo.getField(AuditInfo.OUTCOME), getEventTime(path, auditLogger));

        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[2];
        String callingUserID = auditInfo.getField(AuditInfo.CALLING_USERID);
        String archiveUserID = auditInfo.getField(AuditInfo.CALLED_USERID);
        AuditMessages.UserIDTypeCode archiveUserIDTypeCode = archiveUserIDTypeCode(archiveUserID);
        activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                                callingUserID,
                                auditInfo.getField(AuditInfo.CALLING_HOST))
                                .userIDTypeCode(callingUserIDTypeCode(archiveUserIDTypeCode, callingUserID))
                                .requester(true)
                                .roleIDCode(eventType.source)
                                .build();
        activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(
                                archiveUserID,
                                getLocalHostName(auditLogger))
                                .userIDTypeCode(archiveUserIDTypeCode)
                                .altUserID(AuditLogger.processID())
                                .roleIDCode(eventType.destination)
                                .build();

        SOPClass[] sopC = new SOPClass[sopClassMap.size()];
        int i = 0;
        for (Map.Entry<String, HashSet<String>> entry : sopClassMap.entrySet()) {
            sopC[i] = AuditMessages.createSOPClass(null, entry.getKey(), entry.getValue().size());
            i++;
        }

        ParticipantObjectDescriptionBuilder desc = new ParticipantObjectDescriptionBuilder.Builder()
                                                .sopC(sopC)
                                                .acc(accessions(auditInfo.getField(AuditInfo.ACC_NUM)))
                                                .mpps(mppsUIDs.toArray(new String[mppsUIDs.size()]))
                                                .build();

        String lifecycle = (eventType == AuditServiceUtils.EventType.STORE_CREA
                || eventType == AuditServiceUtils.EventType.STORE_UPDT)
                ? AuditMessages.ParticipantObjectDataLifeCycle.OriginationCreation : null;
        ParticipantObjectIdentificationBuilder poiStudy = new ParticipantObjectIdentificationBuilder.Builder(
                                                        auditInfo.getField(AuditInfo.STUDY_UID),
                                                        AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID,
                                                        AuditMessages.ParticipantObjectTypeCode.SystemObject,
                                                        AuditMessages.ParticipantObjectTypeCodeRole.Report)
                                                        .desc(desc)
                                                        .detail(getPod(studyDate, auditInfo.getField(AuditInfo.STUDY_DATE)))
                                                        .lifeCycle(lifecycle)
                                                        .build();
        emitAuditMessage(auditLogger, ei, activeParticipantBuilder, poiStudy, patientPOI(auditInfo));
    }

    void spoolRetrieve(AuditServiceUtils.EventType eventType, RetrieveContext ctx) {
        RetrieveContextAuditInfoBuilder builder = new RetrieveContextAuditInfoBuilder(ctx, getArchiveDevice(), eventType);
        for (AuditInfoBuilder[] auditInfoBuilder : builder.getAuditInfoBuilder())
            writeSpoolFile(builder.getEventType(), auditInfoBuilder);
    }

    private ArchiveDeviceExtension getArchiveDevice() {
        return device.getDeviceExtension(ArchiveDeviceExtension.class);
    }

    private void auditRetrieve(AuditLogger auditLogger, Path path, AuditServiceUtils.EventType eventType)
            throws IOException {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo ri = new AuditInfo(reader.getMainInfo());
        EventIdentificationBuilder ei = toCustomBuildEventIdentification(eventType, ri.getField(AuditInfo.OUTCOME),
                ri.getField(AuditInfo.WARNING), getEventTime(path, auditLogger));

        HashMap<String, AccessionNumSopClassInfo> study_accNumSOPClassInfo = new HashMap<>();
        String pID = getArchiveDevice().auditUnknownPatientID();
        String pName = null;
        String studyDt = null;
        for (String line : reader.getInstanceLines()) {
            AuditInfo rInfo = new AuditInfo(line);
            String studyInstanceUID = rInfo.getField(AuditInfo.STUDY_UID);
            AccessionNumSopClassInfo accNumSopClassInfo = study_accNumSOPClassInfo.get(studyInstanceUID);
            if (accNumSopClassInfo == null) {
                accNumSopClassInfo = new AccessionNumSopClassInfo(
                        rInfo.getField(AuditInfo.ACC_NUM));
                study_accNumSOPClassInfo.put(studyInstanceUID, accNumSopClassInfo);
            }
            accNumSopClassInfo.addSOPInstance(rInfo);
            study_accNumSOPClassInfo.put(studyInstanceUID, accNumSopClassInfo);
            pID = rInfo.getField(AuditInfo.P_ID);
            pName = rInfo.getField(AuditInfo.P_NAME);
            studyDt = rInfo.getField(AuditInfo.STUDY_DATE);
        }
        List<ParticipantObjectIdentificationBuilder> pois = new ArrayList<>();
        for (Map.Entry<String, AccessionNumSopClassInfo> entry : study_accNumSOPClassInfo.entrySet()) {
            HashSet<SOPClass> sopC = new HashSet<>();
            for (Map.Entry<String, HashSet<String>> sopClassMap : entry.getValue().getSopClassMap().entrySet())
                if (ri.getField(AuditInfo.FAILED_IUID_SHOW) != null)
                    sopC.add(getSOPC(sopClassMap.getValue(), sopClassMap.getKey(), sopClassMap.getValue().size()));
                else
                    sopC.add(getSOPC(null, sopClassMap.getKey(), sopClassMap.getValue().size()));

            ParticipantObjectDescriptionBuilder desc = new ParticipantObjectDescriptionBuilder.Builder()
                                                        .sopC(sopC.toArray(new SOPClass[sopC.size()]))
                                                        .acc(accessions(entry.getValue().getAccNum()))
                                                        .build();
            ParticipantObjectIdentificationBuilder poi = new ParticipantObjectIdentificationBuilder.Builder(
                                                        entry.getKey(),
                                                        AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID,
                                                        AuditMessages.ParticipantObjectTypeCode.SystemObject,
                                                        AuditMessages.ParticipantObjectTypeCodeRole.Report)
                                                        .desc(desc)
                                                        .detail(getPod(studyDate, studyDt))
                                                        .build();
            pois.add(poi);
        }
        ParticipantObjectIdentificationBuilder poiPatient = new ParticipantObjectIdentificationBuilder.Builder(
                                                            pID,
                                                            AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                                                            AuditMessages.ParticipantObjectTypeCode.Person,
                                                            AuditMessages.ParticipantObjectTypeCodeRole.Patient)
                                                            .name(pName)
                                                            .build();
        pois.add(poiPatient);
        emitAuditMessage(auditLogger, ei,
                        getApsForRetrieve(eventType, ri, auditLogger),
                        pois.toArray(new ParticipantObjectIdentificationBuilder[pois.size()]));
    }

    private ActiveParticipantBuilder[] getApsForRetrieve(AuditServiceUtils.EventType eventType, AuditInfo ri, AuditLogger auditLogger) {
        return ri.getField(AuditInfo.MOVEAET) != null
                ? getApsForMove(eventType, ri, auditLogger)
                : ri.getField(AuditInfo.IS_EXPORT) != null
                    ? getApsForExport(eventType, ri, auditLogger)
                    : getApsForGetOrWadoRS(eventType, ri, auditLogger);
    }

    private ActiveParticipantBuilder[] getApsForMove(AuditServiceUtils.EventType eventType, AuditInfo ri, AuditLogger auditLogger) {
        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[3];
        activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                                ri.getField(AuditInfo.CALLED_USERID),
                                getLocalHostName(auditLogger))
                                .userIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle)
                                .altUserID(AuditLogger.processID())
                                .roleIDCode(eventType.source)
                                .build();
        activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(
                                ri.getField(AuditInfo.DEST_AET),
                                ri.getField(AuditInfo.DEST_NAP_ID))
                                .userIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle)
                                .roleIDCode(eventType.destination)
                                .build();
        activeParticipantBuilder[2] = new ActiveParticipantBuilder.Builder(
                                ri.getField(AuditInfo.MOVEAET),
                                ri.getField(AuditInfo.CALLING_HOST))
                                .userIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle)
                                .requester(true)
                                .build();
        return activeParticipantBuilder;
    }

    private ActiveParticipantBuilder[] getApsForExport(AuditServiceUtils.EventType eventType, AuditInfo ri, AuditLogger auditLogger) {
        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[3];
        activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                ri.getField(AuditInfo.DEST_AET),
                ri.getField(AuditInfo.DEST_NAP_ID))
                .userIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle)
                .roleIDCode(eventType.destination).build();
        String archiveUserID = ri.getField(AuditInfo.CALLED_USERID);
        AuditMessages.UserIDTypeCode archiveUserIDTypeCode = archiveUserIDTypeCode(archiveUserID);
        if (ri.getField(AuditInfo.CALLING_USERID) == null)
            activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(
                                    archiveUserID,
                                    getLocalHostName(auditLogger))
                                    .userIDTypeCode(archiveUserIDTypeCode)
                                    .altUserID(AuditLogger.processID())
                                    .requester(true)
                                    .roleIDCode(eventType.source)
                                    .build();

        else {
            activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(
                                    archiveUserID,
                                    getLocalHostName(auditLogger))
                                    .userIDTypeCode(archiveUserIDTypeCode)
                                    .altUserID(AuditLogger.processID())
                                    .roleIDCode(eventType.source)
                                    .build();
            String callingUserID = ri.getField(AuditInfo.CALLING_USERID);
            activeParticipantBuilder[2] = new ActiveParticipantBuilder.Builder(
                                    callingUserID,
                                    ri.getField(AuditInfo.CALLING_HOST))
                                    .userIDTypeCode(AuditMessages.userIDTypeCode(callingUserID))
                                    .requester(true)
                                    .build();
        }
        return activeParticipantBuilder;
    }

    private ActiveParticipantBuilder[] getApsForGetOrWadoRS(AuditServiceUtils.EventType eventType, AuditInfo ri, AuditLogger auditLogger) {
        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[2];
        String archiveUserID = ri.getField(AuditInfo.CALLED_USERID);
        AuditMessages.UserIDTypeCode archiveUserIDTypeCode = archiveUserIDTypeCode(archiveUserID);
        activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                                archiveUserID,
                                getLocalHostName(auditLogger))
                                .userIDTypeCode(archiveUserIDTypeCode)
                                .altUserID(AuditLogger.processID())
                                .roleIDCode(eventType.source)
                                .build();
        String callingUserID = ri.getField(AuditInfo.DEST_AET);
        activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(
                                callingUserID,
                                ri.getField(AuditInfo.DEST_NAP_ID))
                                .userIDTypeCode(callingUserIDTypeCode(archiveUserIDTypeCode, callingUserID))
                                .requester(true)
                                .roleIDCode(eventType.destination)
                                .build();
        return activeParticipantBuilder;
    }

    void spoolPatientRecord(PatientMgtContext ctx) {
        UnparsedHL7Message hl7msg = ctx.getUnparsedHL7Message();
        String[] callingCalledUserIDs = callingCalledUserIDsForPatientRecord(ctx);
        boolean isExternalHL7 = ctx.getHttpServletRequestInfo() != null && ctx.getUnparsedHL7Message() != null;
        AuditInfoBuilder auditInfoBuilder = isExternalHL7
                                            ? externalHL7PatientRecord(ctx, callingCalledUserIDs) : internalPatientRecord(ctx, callingCalledUserIDs);
        AuditServiceUtils.EventType eventType = AuditServiceUtils.EventType.forHL7(ctx);
        byte[] data = hl7msg != null ? hl7msg.data() : null;
        if (data != null)
            writeSpoolFile(eventType, auditInfoBuilder, data);
        else
            writeSpoolFile(eventType, auditInfoBuilder);
        if (ctx.getPreviousAttributes() != null) {
            AuditInfoBuilder prevAuditInfoBuilder = isExternalHL7
                                                    ? externalHL7PreviousPatientRecord(ctx, callingCalledUserIDs)
                                                    : internalPreviousPatientRecord(ctx, callingCalledUserIDs);
            AuditServiceUtils.EventType prevEventType = AuditServiceUtils.EventType.PAT_DELETE;
            if (data != null)
                writeSpoolFile(prevEventType, prevAuditInfoBuilder, data);
            else
                writeSpoolFile(prevEventType, prevAuditInfoBuilder);
        }
    }

    private AuditInfoBuilder internalPreviousPatientRecord(PatientMgtContext ctx, String[] callingCalledUserIDs) {
        return new AuditInfoBuilder.Builder()
                                .callingHost(ctx.getRemoteHostName())
                                .callingUserID(callingCalledUserIDs[0])
                                .calledUserID(callingCalledUserIDs[1])
                                .pIDAndName(ctx.getPreviousAttributes(), getArchiveDevice())
                                .outcome(getOD(ctx.getException()))
                                .build();
    }

    private AuditInfoBuilder internalPatientRecord(PatientMgtContext ctx, String[] callingCalledUserIDs) {
        return new AuditInfoBuilder.Builder()
                            .callingHost(ctx.getRemoteHostName())
                            .callingUserID(callingCalledUserIDs[0])
                            .calledUserID(callingCalledUserIDs[1])
                            .pIDAndName(ctx.getAttributes(), getArchiveDevice())
                            .outcome(getOD(ctx.getException()))
                            .build();
    }

    private AuditInfoBuilder externalHL7PatientRecord(PatientMgtContext ctx, String[] callingCalledUserIDs) {
        UnparsedHL7Message unparsedHL7Message = ctx.getUnparsedHL7Message();
        HL7Segment msh = unparsedHL7Message.msh();
        Attributes attrs = ctx.getAttributes() != null
                            ? ctx.getAttributes() : populateAttributes(unparsedHL7Message, "PID", 3);
        return new AuditInfoBuilder.Builder()
                .callingHost(ctx.getRemoteHostName())
                .callingUserID(callingCalledUserIDs[0])
                .calledUserID(callingCalledUserIDs[1])
                .pIDAndName(attrs, getArchiveDevice())
                .outcome(getOD(ctx.getException()))
                .isExternalHL7()
                .hl7SenderExternal(msh.getSendingApplicationWithFacility())
                .hl7ReceiverExternal(msh.getReceivingApplicationWithFacility())
                .build();
    }

    private AuditInfoBuilder externalHL7PreviousPatientRecord(PatientMgtContext ctx, String[] callingCalledUserIDs) {
        UnparsedHL7Message unparsedHL7Message = ctx.getUnparsedHL7Message();
        HL7Segment msh = unparsedHL7Message.msh();
        Attributes attrs = ctx.getPreviousAttributes() != null
                            ? ctx.getPreviousAttributes() : populateAttributes(unparsedHL7Message, "MRG", 1);
        return new AuditInfoBuilder.Builder()
                .callingHost(ctx.getRemoteHostName())
                .callingUserID(callingCalledUserIDs[0])
                .calledUserID(callingCalledUserIDs[1])
                .pIDAndName(attrs, getArchiveDevice())
                .outcome(getOD(ctx.getException()))
                .isExternalHL7()
                .hl7SenderExternal(msh.getSendingApplicationWithFacility())
                .hl7ReceiverExternal(msh.getReceivingApplicationWithFacility())
                .build();
    }

    private Attributes populateAttributes(UnparsedHL7Message unparsedHL7Message, String segName, int pos) {
        Attributes attrs = new Attributes(4);
        String charset = unparsedHL7Message.msh().getField(17, "ASCII");
        HL7Message hl7msg = HL7Message.parse(unparsedHL7Message.data(), unparsedHL7Message.data().length, charset);
        HL7Segment hl7Segment = hl7msg.getSegment(segName);
        new IDWithIssuer(hl7Segment.getField(pos, "")).exportPatientIDWithIssuer(attrs);
        if (segName.equals("PID"))
            attrs.setString(Tag.PatientName, VR.PN, hl7Segment.getField(5, ""));
        return attrs;
    }

    private String[] callingCalledUserIDsForPatientRecord(PatientMgtContext ctx) {
        String[] callingCalledUserIDs = new String[2];
        HttpServletRequestInfo httpRequest = ctx.getHttpServletRequestInfo();
        Association association = ctx.getAssociation();
        UnparsedHL7Message hl7msg = ctx.getUnparsedHL7Message();
        callingCalledUserIDs[0] = httpRequest != null
                        ? httpRequest.requesterUserID
                        : ctx.getHttpServletRequestInfo() != null
                            ? ctx.getHttpServletRequestInfo().requesterUserID
                            : hl7msg != null
                                ? hl7msg.msh().getSendingApplicationWithFacility()
                                : association != null
                                    ? association.getCallingAET() : null;
        callingCalledUserIDs[1] = httpRequest != null
                        ? httpRequest.requestURI
                        : ctx.getHttpServletRequestInfo() != null
                            ? ctx.getHttpServletRequestInfo().requestURI
                            : hl7msg != null
                                ? hl7msg.msh().getReceivingApplicationWithFacility()
                                : association != null
                                    ? association.getCalledAET() : null;
        return callingCalledUserIDs;
    }

    private void auditPatientRecord(AuditLogger auditLogger, Path path, AuditServiceUtils.EventType et) throws Exception {
        SpoolFileReader reader = new SpoolFileReader(path.toFile());
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        EventIdentificationBuilder ei = toBuildEventIdentification(et, auditInfo.getField(AuditInfo.OUTCOME), getEventTime(path, auditLogger));
        ActiveParticipantBuilder[] activeParticipantBuilder = buildPatientRecordActiveParticipants(auditLogger, et, auditInfo);

        ParticipantObjectIdentificationBuilder patientPOI = new ParticipantObjectIdentificationBuilder.Builder(
                                                                auditInfo.getField(AuditInfo.P_ID),
                                                                AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                                                                AuditMessages.ParticipantObjectTypeCode.Person,
                                                                AuditMessages.ParticipantObjectTypeCodeRole.Patient)
                                                                .name(auditInfo.getField(AuditInfo.P_NAME))
                                                                .detail(getHL7ParticipantObjectDetail(reader))
                                                                .build();

        emitAuditMessage(auditLogger, ei, activeParticipantBuilder, patientPOI);
    }

    private ParticipantObjectDetail getHL7ParticipantObjectDetail(SpoolFileReader reader) {
        ParticipantObjectDetail detail = null;
        if (reader.getData().length > 0) {
            detail = new ParticipantObjectDetail();
            detail.setType("HL7v2 Message");
            detail.setValue(reader.getData());
        }
        return detail;
    }

    private ActiveParticipantBuilder[] buildPatientRecordActiveParticipants(
            AuditLogger auditLogger, AuditServiceUtils.EventType et, AuditInfo auditInfo) throws ConfigurationException {
        if (isServiceUserTriggered(et.source)) {
            if (auditInfo.getField(AuditInfo.IS_EXTERNAL_HL7) != null) {
                return getExternalPatientRecordActiveParticipants(auditLogger, et, auditInfo);
            }
            else
                return getInternalPatientRecordActiveParticipants(auditLogger, et, auditInfo);
        } else
            return getSchedulerTriggeredActiveParticipant(auditLogger, et);
    }

    private ActiveParticipantBuilder[] getSchedulerTriggeredActiveParticipant(AuditLogger auditLogger, AuditServiceUtils.EventType et) {
        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[1];
        activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                device.getDeviceName(),
                getLocalHostName(auditLogger))
                .userIDTypeCode(AuditMessages.UserIDTypeCode.DeviceName)
                .altUserID(AuditLogger.processID())
                .requester(true)
                .roleIDCode(et.destination)
                .build();
        return activeParticipantBuilder;
    }

    private ActiveParticipantBuilder[] getInternalPatientRecordActiveParticipants(
            AuditLogger auditLogger, AuditServiceUtils.EventType et, AuditInfo auditInfo) {
        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[2];
        String archiveUserID = auditInfo.getField(AuditInfo.CALLED_USERID);
        String callingUserID = auditInfo.getField(AuditInfo.CALLING_USERID);
        AuditMessages.UserIDTypeCode archiveUserIDTypeCode = archiveUserIDTypeCode(archiveUserID);

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
                                .userIDTypeCode(callingUserIDTypeCode(archiveUserIDTypeCode, callingUserID))
                                .requester(true)
                                .roleIDCode(et.source)
                                .build();
        return activeParticipantBuilder;
    }

    private ActiveParticipantBuilder[] getExternalPatientRecordActiveParticipants(
            AuditLogger auditLogger, AuditServiceUtils.EventType et, AuditInfo auditInfo) throws ConfigurationException {
        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[4];
        HL7DeviceExtension hl7Dev = device.getDeviceExtension(HL7DeviceExtension.class);

        String archiveUserID = auditInfo.getField(AuditInfo.CALLED_USERID);
        String callingUserID = auditInfo.getField(AuditInfo.CALLING_USERID);

        String hl7SendingAppWithFacility = auditInfo.getField(AuditInfo.HL7_SENDER_EXTERNAL);
        String hl7ReceivingAppWithFacility = auditInfo.getField(AuditInfo.HL7_RECEIVER_EXTERNAL);

        HL7Application hl7AppSender = hl7Dev.getHL7Application(hl7SendingAppWithFacility, true);
        HL7Application hl7AppReceiver = hl7AppCache.findHL7Application(hl7ReceivingAppWithFacility);

        activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                                        callingUserID,
                                        auditInfo.getField(AuditInfo.CALLING_HOST))
                                        .userIDTypeCode(AuditMessages.userIDTypeCode(callingUserID))
                                        .requester(true)
                                        .build();
        activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(
                                        archiveUserID,
                                        getLocalHostName(auditLogger))
                                        .userIDTypeCode(AuditMessages.UserIDTypeCode.URI)
                                        .altUserID(AuditLogger.processID())
                                        .build();
        activeParticipantBuilder[2] = new ActiveParticipantBuilder.Builder(
                                        hl7SendingAppWithFacility,
                                        hl7AppSender.getConnections().get(0).getHostname())
                                        .userIDTypeCode(AuditMessages.UserIDTypeCode.ApplicationFacility)
                                        .roleIDCode(et.source)
                                        .build();
        activeParticipantBuilder[3] = new ActiveParticipantBuilder.Builder(
                                        hl7ReceivingAppWithFacility,
                                        hl7AppReceiver.getConnections().get(0).getHostname())
                                        .userIDTypeCode(AuditMessages.UserIDTypeCode.ApplicationFacility)
                                        .roleIDCode(et.destination)
                                        .build();

        return activeParticipantBuilder;
    }

    void spoolProcedureRecord(ProcedureContext ctx) {
        UnparsedHL7Message hl7msg = ctx.getUnparsedHL7Message();
        AuditInfoBuilder info = ctx.getHttpRequest() != null
                                ? buildAuditInfoFORRestful(ctx)
                                : hl7msg != null
                                    ? buildAuditInfoFORHL7(ctx, hl7msg.msh()) : buildAuditInfoForAssociation(ctx);
        AuditServiceUtils.EventType eventType = AuditServiceUtils.EventType.forProcedure(ctx.getEventActionCode());
        if (hl7msg != null)
            writeSpoolFile(eventType, info, hl7msg.data());
        else
            writeSpoolFile(eventType, info);
    }

    private AuditInfoBuilder buildAuditInfoForAssociation(ProcedureContext ctx) {
        Association as = ctx.getAssociation();
        return new AuditInfoBuilder.Builder()
                .callingHost(ctx.getRemoteHostName())
                .callingUserID(as.getCallingAET())
                .calledUserID(as.getCalledAET())
                .studyUIDAccNumDate(ctx.getAttributes())
                .pIDAndName(ctx.getPatient().getAttributes(), getArchiveDevice())
                .outcome(getOD(ctx.getException()))
                .build();
    }

    private AuditInfoBuilder buildAuditInfoFORRestful(ProcedureContext ctx) {
        HttpServletRequest req  = ctx.getHttpRequest();
        return new AuditInfoBuilder.Builder()
                .callingHost(ctx.getRemoteHostName())
                .callingUserID(KeycloakContext.valueOf(req).getUserName())
                .calledUserID(req.getRequestURI())
                .studyUIDAccNumDate(ctx.getAttributes())
                .pIDAndName(ctx.getPatient().getAttributes(), getArchiveDevice())
                .outcome(getOD(ctx.getException()))
                .build();
    }

    private AuditInfoBuilder buildAuditInfoFORHL7(ProcedureContext ctx, HL7Segment msh) {
        return new AuditInfoBuilder.Builder()
                .callingHost(ctx.getRemoteHostName())
                .callingUserID(msh.getSendingApplicationWithFacility())
                .calledUserID(msh.getReceivingApplicationWithFacility())
                .studyUIDAccNumDate(ctx.getAttributes())
                .pIDAndName(ctx.getPatient().getAttributes(), getArchiveDevice())
                .outcome(getOD(ctx.getException()))
                .build();
    }

    void spoolProcedureRecord(StudyMgtContext ctx) {
        String callingAET = KeycloakContext.valueOf(ctx.getHttpRequest()).getUserName();
        Attributes pAttr = ctx.getStudy() != null ? ctx.getStudy().getPatient().getAttributes() : null;
        AuditInfoBuilder info = new AuditInfoBuilder.Builder().callingHost(
                                ctx.getHttpRequest().getRemoteHost())
                                .callingUserID(callingAET)
                                .calledUserID(ctx.getHttpRequest().getRequestURI())
                                .studyUIDAccNumDate(ctx.getAttributes())
                                .pIDAndName(pAttr, getArchiveDevice())
                                .outcome(getOD(ctx.getException()))
                                .build();
        writeSpoolFile(AuditServiceUtils.EventType.forProcedure(ctx.getEventActionCode()), info);
    }

    private void auditProcedureRecord(AuditLogger auditLogger, Path path, AuditServiceUtils.EventType et) throws IOException {
        SpoolFileReader reader = new SpoolFileReader(path.toFile());
        AuditInfo prI = new AuditInfo(reader.getMainInfo());

        EventIdentificationBuilder ei = toBuildEventIdentification(et, prI.getField(AuditInfo.OUTCOME), getEventTime(path, auditLogger));

        ActiveParticipantBuilder[] activeParticipantBuilder = buildProcedureRecordActiveParticipants(auditLogger, prI);
        
        ParticipantObjectDescriptionBuilder desc = new ParticipantObjectDescriptionBuilder.Builder()
                .acc(accessions(prI.getField(AuditInfo.ACC_NUM))).build();

        ParticipantObjectIdentificationBuilder poiStudy = new ParticipantObjectIdentificationBuilder.Builder(
                                                        prI.getField(AuditInfo.STUDY_UID),
                                                        AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID,
                                                        AuditMessages.ParticipantObjectTypeCode.SystemObject,
                                                        AuditMessages.ParticipantObjectTypeCodeRole.Report)
                                                        .desc(desc)
                                                        .detail(getPod(studyDate, prI.getField(AuditInfo.STUDY_DATE)))
                                                        .detail(getHL7ParticipantObjectDetail(reader))
                                                        .build();
        emitAuditMessage(auditLogger, ei, activeParticipantBuilder, poiStudy, patientPOI(prI));
    }

    private ActiveParticipantBuilder[] buildProcedureRecordActiveParticipants(AuditLogger auditLogger, AuditInfo prI) {
        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[2];
        String archiveUserID = prI.getField(AuditInfo.CALLED_USERID);
        AuditMessages.UserIDTypeCode archiveUserIDTypeCode = archiveUserIDTypeCode(archiveUserID);
        String callingUserID = prI.getField(AuditInfo.CALLING_USERID);
        activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                                callingUserID,
                                prI.getField(AuditInfo.CALLING_HOST))
                                .userIDTypeCode(callingUserIDTypeCode(archiveUserIDTypeCode, callingUserID))
                                .requester(true)
                                .build();
        activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(
                                archiveUserID,
                                getLocalHostName(auditLogger))
                                .userIDTypeCode(archiveUserIDTypeCode)
                                .altUserID(AuditLogger.processID())
                                .build();
        return activeParticipantBuilder;
    }

    void spoolProvideAndRegister(ExportContext ctx) {
        ProvideAndRegisterAuditInfoBuilder provideAndRegisterInfo = new ProvideAndRegisterAuditInfoBuilder(ctx, getArchiveDevice());
        AuditInfoBuilder auditInfoBuilder = provideAndRegisterInfo.getAuditInfoBuilder();
        if (auditInfoBuilder == null)
            return;

        writeSpoolFile(AuditServiceUtils.EventType.PROV_REGIS, auditInfoBuilder);
    }

    private void auditProvideAndRegister(AuditLogger auditLogger, Path path, AuditServiceUtils.EventType et)
            throws IOException {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        EventIdentificationBuilder ei = toBuildEventIdentification(et, auditInfo.getField(AuditInfo.OUTCOME), getEventTime(path, auditLogger));

        ActiveParticipantBuilder[] activeParticipantBuilder = buildProvideRegisterActiveParticipants(auditLogger, et, auditInfo);

        emitAuditMessage(auditLogger, ei, activeParticipantBuilder, patientPOI(auditInfo), submissionSetPOI(auditInfo));
    }

    private ActiveParticipantBuilder[] buildProvideRegisterActiveParticipants(
            AuditLogger auditLogger, AuditServiceUtils.EventType et, AuditInfo ai) {
        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[3];
        activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                                ai.getField(AuditInfo.DEST_AET),
                                ai.getField(AuditInfo.DEST_NAP_ID))
                                .userIDTypeCode(AuditMessages.UserIDTypeCode.URI)
                                .roleIDCode(et.destination)
                                .build();
        if (isServiceUserTriggered(ai.getField(AuditInfo.CALLING_USERID))) {
            activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(
                                    ai.getField(AuditInfo.CALLED_USERID),
                                    getLocalHostName(auditLogger))
                                    .userIDTypeCode(AuditMessages.UserIDTypeCode.URI)
                                    .altUserID(AuditLogger.processID())
                                    .roleIDCode(et.source)
                                    .build();
            String callingUserID = ai.getField(AuditInfo.CALLING_USERID);
            activeParticipantBuilder[2] = new ActiveParticipantBuilder.Builder(
                                    callingUserID,
                                    ai.getField(AuditInfo.CALLING_HOST))
                                    .userIDTypeCode(AuditMessages.userIDTypeCode(callingUserID))
                                    .requester(true)
                                    .build();
        } else
            activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(
                                    device.getDeviceName(),
                                    getLocalHostName(auditLogger))
                                    .altUserID(AuditLogger.processID())
                                    .userIDTypeCode(AuditMessages.UserIDTypeCode.DeviceName)
                                    .requester(true)
                                    .roleIDCode(et.source)
                                    .build();
        return activeParticipantBuilder;
    }

    private boolean isServiceUserTriggered(Object val) {
        return val != null;
    }

    void spoolStgCmt(StgCmtEventInfo stgCmtEventInfo) {
        Attributes eventInfo = stgCmtEventInfo.getExtendedEventInfo();
        Sequence failed = eventInfo.getSequence(Tag.FailedSOPSequence);
        Sequence success = eventInfo.getSequence(Tag.ReferencedSOPSequence);
        String studyUID = eventInfo.getStrings(Tag.StudyInstanceUID) != null
                ? buildStrings(eventInfo.getStrings(Tag.StudyInstanceUID)) : getArchiveDevice().auditUnknownStudyInstanceUID();
        if (failed != null && !failed.isEmpty()) {
            Set<String> failureReasons = new HashSet<>();
            Set<AuditInfo> aiSet = new HashSet<>();
            LinkedHashSet<Object> objs = new LinkedHashSet<>();
            for (Attributes item : failed) {
                AuditInfoBuilder ii = new AuditInfoBuilder.Builder()
                        .sopCUID(item.getString(Tag.ReferencedSOPClassUID))
                        .sopIUID(item.getString(Tag.ReferencedSOPInstanceUID)).build();
                String outcome = item.getInt(Tag.FailureReason, 0) == Status.NoSuchObjectInstance
                        ? "NoSuchObjectInstance" : item.getInt(Tag.FailureReason, 0) == Status.ClassInstanceConflict
                        ? "ClassInstanceConflict" : "ProcessingFailure";
                failureReasons.add(outcome);
                aiSet.add(new AuditInfo(ii));
            }
            AuditInfoBuilder i = new AuditInfoBuilder.Builder()
                                .callingUserID(storageCmtCallingAET(stgCmtEventInfo))
                                .callingHost(storageCmtCallingHost(stgCmtEventInfo))
                                .calledUserID(storageCmtCalledAET(stgCmtEventInfo))
                                .pIDAndName(eventInfo, getArchiveDevice())
                                .studyUID(studyUID)
                                .outcome(buildStrings(failureReasons.toArray(new String[failureReasons.size()])))
                                .build();
            objs.add(new AuditInfo(i));
            objs.addAll(aiSet);
            writeSpoolFile(AuditServiceUtils.EventType.STG_COMMIT, objs);
        }
        if (success != null && !success.isEmpty()) {
            AuditInfoBuilder[] auditInfoBuilder = new AuditInfoBuilder[success.size()+1];
            auditInfoBuilder[0] = new AuditInfoBuilder.Builder()
                                .callingUserID(storageCmtCallingAET(stgCmtEventInfo))
                                .callingHost(storageCmtCallingHost(stgCmtEventInfo))
                                .calledUserID(storageCmtCalledAET(stgCmtEventInfo))
                                .pIDAndName(eventInfo, getArchiveDevice())
                                .studyUID(studyUID)
                                .build();
            int i = 0;
            for (Attributes item : success) {
                auditInfoBuilder[i+1] = new AuditInfoBuilder.Builder()
                                    .sopCUID(item.getString(Tag.ReferencedSOPClassUID))
                                    .sopIUID(item.getString(Tag.ReferencedSOPInstanceUID))
                                    .build();
                i++;
            }
            writeSpoolFile(AuditServiceUtils.EventType.STG_COMMIT, auditInfoBuilder);
        }
    }

    private String storageCmtCallingHost(StgCmtEventInfo stgCmtEventInfo) {
        return stgCmtEventInfo.getRequest() != null
                ? stgCmtEventInfo.getRequest().getRemoteHost()
                : stgCmtEventInfo.getRemoteAE().getConnections().get(0).getHostname();
    }

    private String storageCmtCalledAET(StgCmtEventInfo stgCmtEventInfo) {
        return stgCmtEventInfo.getRequest() != null
                ? stgCmtEventInfo.getRequest().getRequestURI()
                : stgCmtEventInfo.getLocalAET();
    }

    private String storageCmtCallingAET(StgCmtEventInfo stgCmtEventInfo) {
        return stgCmtEventInfo.getRequest() != null
                ? KeycloakContext.valueOf(stgCmtEventInfo.getRequest()).getUserName()
                : stgCmtEventInfo.getRemoteAE().getAETitle();
    }

    private void auditStorageCommit(AuditLogger auditLogger, Path path, AuditServiceUtils.EventType et) throws IOException {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        EventIdentificationBuilder ei = toBuildEventIdentification(et, auditInfo.getField(AuditInfo.OUTCOME), getEventTime(path, auditLogger));
        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[2];
        String archiveUserID = auditInfo.getField(AuditInfo.CALLED_USERID);
        AuditMessages.UserIDTypeCode archiveUserIDTypeCode = archiveUserIDTypeCode(archiveUserID);
        activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                archiveUserID,
                getLocalHostName(auditLogger))
                .userIDTypeCode(archiveUserIDTypeCode)
                .altUserID(AuditLogger.processID())
                .roleIDCode(et.destination).build();
        String callingUserID = auditInfo.getField(AuditInfo.CALLING_USERID);
        activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(
                callingUserID,
                auditInfo.getField(AuditInfo.CALLING_HOST))
                .userIDTypeCode(callingUserIDTypeCode(archiveUserIDTypeCode, callingUserID))
                .requester(true)
                .roleIDCode(et.source).build();
       
        String[] studyUIDs = StringUtils.split(auditInfo.getField(AuditInfo.STUDY_UID), ';');
        
        HashMap<String, HashSet<String>> sopClassMap = new HashMap<>();
        for (String line : reader.getInstanceLines()) {
            AuditInfo ii = new AuditInfo(line);
            buildSOPClassMap(sopClassMap, ii.getField(AuditInfo.SOP_CUID), ii.getField(AuditInfo.SOP_IUID));
        }
        HashSet<SOPClass> sopC = new HashSet<>();
        if (studyUIDs.length > 1 || auditInfo.getField(AuditInfo.OUTCOME) != null)
            for (Map.Entry<String, HashSet<String>> entry : sopClassMap.entrySet())
                sopC.add(getSOPC(entry.getValue(), entry.getKey(), entry.getValue().size()));
        else
            for (Map.Entry<String, HashSet<String>> entry : sopClassMap.entrySet())
                sopC.add(getSOPC(null, entry.getKey(), entry.getValue().size()));

        ParticipantObjectDescriptionBuilder poDesc = new ParticipantObjectDescriptionBuilder.Builder()
                .sopC(sopC.toArray(new SOPClass[sopC.size()]))
                .pocsStudyUIDs(studyUIDs).build();
        
        ParticipantObjectIdentificationBuilder poiStudy = new ParticipantObjectIdentificationBuilder.Builder(studyUIDs[0],
                AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID,
                AuditMessages.ParticipantObjectTypeCode.SystemObject, AuditMessages.ParticipantObjectTypeCodeRole.Report)
                .desc(poDesc).lifeCycle(AuditMessages.ParticipantObjectDataLifeCycle.Verification).build();
        emitAuditMessage(auditLogger, ei, activeParticipantBuilder, poiStudy, patientPOI(auditInfo));
    }

    private String buildStrings(String[] strings) {
        if (strings.length == 0)
            return null;

        StringBuilder b = new StringBuilder();
        b.append(strings[0]);
        for (int i = 1; i < strings.length; i++)
            b.append(';').append(strings[i]);
        return b.toString();
    }

    private AuditInfoBuilder getAIStoreCtx(StoreContext ctx) {
        StoreSession ss = ctx.getStoreSession();
        HttpServletRequest req = ss.getHttpRequest();
        Attributes attr = ctx.getAttributes();
        String callingHost = ss.getRemoteHostName();
        String callingAET = req != null ? KeycloakContext.valueOf(req).getUserName() : ss.getCallingAET();
        if (callingAET == null && callingHost == null)
            callingAET = ss.toString();
        String outcome = null != ctx.getException() ? null != ctx.getRejectionNote()
                ? ctx.getRejectionNote().getRejectionNoteCode().getCodeMeaning() + " - " + ctx.getException().getMessage()
                : getOD(ctx.getException()) : null;
        String warning = ctx.getException() == null && null != ctx.getRejectionNote()
                ? ctx.getRejectionNote().getRejectionNoteCode().getCodeMeaning() : null;
        return new AuditInfoBuilder.Builder().callingHost(callingHost)
                .callingUserID(callingAET)
                .calledUserID(req != null ? req.getRequestURI() : ss.getCalledAET())
                .studyUIDAccNumDate(attr)
                .pIDAndName(attr, getArchiveDevice())
                .outcome(outcome)
                .warning(warning)
                .build();
    }

    private String getFileName(AuditServiceUtils.EventType et, String callingAET, String calledAET, String studyIUID) {
        return String.valueOf(et) + '-' + callingAET + '-' + calledAET + '-' + studyIUID;
    }

    private String getOD(Exception e) {
        return e != null ? e.getMessage() : null;
    }

    private ParticipantObjectDetail getPod(String type, String value) {
        return AuditMessages.createParticipantObjectDetail(type, value);
    }

    private SOPClass[] sopClasses(List<String> instanceLines) {
        SOPClass[] sopClasses = new SOPClass[instanceLines.size()];
        for (int i = 0; i < instanceLines.size(); i++) {
            AuditInfo ii = new AuditInfo(instanceLines.get(i));
            sopClasses[i] = AuditMessages.createSOPClass(
                    null, ii.getField(AuditInfo.SOP_CUID), Integer.parseInt(ii.getField(AuditInfo.SOP_IUID)));
        }
        return sopClasses;
    }

    private String[] accessions(String accession) {
        String[] accessions = {};
        if (accession != null) {
            accessions = new String[1];
            accessions[0] = accession;
        }
        return accessions;
    }

    private SOPClass getSOPC(HashSet<String> instances, String uid, Integer numI) {
        return AuditMessages.createSOPClass(instances, uid, numI);
    }

    private Calendar getEventTime(Path path, AuditLogger auditLogger){
        Calendar eventTime = auditLogger.timeStamp();
        try {
            eventTime.setTimeInMillis(Files.getLastModifiedTime(path).toMillis());
        } catch (Exception e) {
            LOG.warn("Failed to get Last Modified Time of Audit Spool File - {} ", auditLogger.getCommonName(), path, e);
        }
        return eventTime;
    }

    private String getLocalHostName(AuditLogger log) {
        return log.getConnections().get(0).getHostname();
    }

    private String getData(SpoolFileReader reader) {
        List<String> data = reader.getInstanceLines();
        if (data.isEmpty())
            return null;
        StringBuilder sb = new StringBuilder();
        sb.append(data.get(0));
        for (int i = 1; i < data.size(); i++)
            sb.append('\n').append(data.get(i));
        return sb.toString();
    }

    private void writeSpoolFile(AuditServiceUtils.EventType eventType, LinkedHashSet<Object> obj) {
        if (obj.isEmpty()) {
            LOG.warn("Attempt to write empty file : ", eventType);
            return;
        }
        ArchiveDeviceExtension arcDev = getArchiveDevice();
        boolean auditAggregate = arcDev.isAuditAggregate();
        AuditLoggerDeviceExtension ext = device.getDeviceExtension(AuditLoggerDeviceExtension.class);
        for (AuditLogger auditLogger : ext.getAuditLoggers()) {
            if (auditLogger.isInstalled()) {
                Path dir = toDirPath(auditLogger);
                try {
                    Files.createDirectories(dir);
                    Path file = Files.createTempFile(dir, String.valueOf(eventType), null);
                    try (SpoolFileWriter writer = new SpoolFileWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                            StandardOpenOption.APPEND))) {
                        for (Object o : obj)
                            writer.writeLine(o);
                    }
                    if (!auditAggregate)
                        auditAndProcessFile(auditLogger, file);
                } catch (Exception e) {
                    LOG.warn("Failed to write to Audit Spool File - {} ", auditLogger.getCommonName(), e);
                }
            }
        }
    }

    private Path toDirPath(AuditLogger auditLogger) {
        return Paths.get(
                StringUtils.replaceSystemProperties(getArchiveDevice().getAuditSpoolDirectory()),
                auditLogger.getCommonName().replaceAll(" ", "_"));
    }

    private void writeSpoolFile(AuditServiceUtils.EventType eventType, AuditInfoBuilder auditInfoBuilder, String data) {
        if (auditInfoBuilder == null) {
            LOG.warn("Attempt to write empty file : ", eventType);
            return;
        }
        boolean auditAggregate = getArchiveDevice().isAuditAggregate();
        AuditLoggerDeviceExtension ext = device.getDeviceExtension(AuditLoggerDeviceExtension.class);
        for (AuditLogger auditLogger : ext.getAuditLoggers()) {
            if (auditLogger.isInstalled()) {
                Path dir = toDirPath(auditLogger);
                try {
                    Files.createDirectories(dir);
                    Path file = Files.createTempFile(dir, String.valueOf(eventType), null);
                    try (SpoolFileWriter writer = new SpoolFileWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                            StandardOpenOption.APPEND))) {
                        writer.writeLine(new AuditInfo(auditInfoBuilder), data);
                    }
                    if (!auditAggregate)
                        auditAndProcessFile(auditLogger, file);
                } catch (Exception e) {
                    LOG.warn("Failed to write to Audit Spool File - {} ", auditLogger.getCommonName(), e);
                }
            }
        }
    }

    private void writeSpoolFile(AuditServiceUtils.EventType eventType, AuditInfoBuilder auditInfoBuilder, byte[] data) {
        if (auditInfoBuilder == null) {
            LOG.warn("Attempt to write empty file : ", eventType);
            return;
        }
        boolean auditAggregate = getArchiveDevice().isAuditAggregate();
        AuditLoggerDeviceExtension ext = device.getDeviceExtension(AuditLoggerDeviceExtension.class);
        for (AuditLogger auditLogger : ext.getAuditLoggers()) {
            if (auditLogger.isInstalled()) {
                Path dir = toDirPath(auditLogger);
                try {
                    Files.createDirectories(dir);
                    Path file = Files.createTempFile(dir, String.valueOf(eventType), null);
                    try (BufferedOutputStream out = new BufferedOutputStream(
                            Files.newOutputStream(file, StandardOpenOption.APPEND))) {
                        out.write(data);
                        try (SpoolFileWriter writer = new SpoolFileWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                                StandardOpenOption.APPEND))) {
                            writer.writeLine(new AuditInfo(auditInfoBuilder));
                        }
                        out.close();
                    }
                    if (!auditAggregate)
                        auditAndProcessFile(auditLogger, file);
                } catch (Exception e) {
                    LOG.warn("Failed to write to Audit Spool File - {} ", auditLogger.getCommonName(), e);
                }
            }
        }
    }

    private void writeSpoolFile(AuditServiceUtils.EventType eventType, AuditInfoBuilder... auditInfoBuilders) {
        if (auditInfoBuilders == null) {
            LOG.warn("Attempt to write empty file : ", eventType);
            return;
        }
        boolean auditAggregate = getArchiveDevice().isAuditAggregate();
        AuditLoggerDeviceExtension ext = device.getDeviceExtension(AuditLoggerDeviceExtension.class);
        for (AuditLogger auditLogger : ext.getAuditLoggers()) {
            if (auditLogger.isInstalled()) {
                Path dir = toDirPath(auditLogger);
                try {
                    Files.createDirectories(dir);
                    Path file = Files.createTempFile(dir, String.valueOf(eventType), null);
                    try (SpoolFileWriter writer = new SpoolFileWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                            StandardOpenOption.APPEND))) {
                        for (AuditInfoBuilder auditInfoBuilder : auditInfoBuilders)
                            writer.writeLine(new AuditInfo(auditInfoBuilder));
                    }
                    if (!auditAggregate)
                        auditAndProcessFile(auditLogger, file);
                } catch (Exception e) {
                    LOG.warn("Failed to write to Audit Spool File - {} ", auditLogger.getCommonName(), e);
                }
            }
        }
    }

    private void writeSpoolFileStoreOrWadoRetrieve(String fileName, AuditInfoBuilder patStudyInfo, AuditInfoBuilder instanceInfo) {
        if (patStudyInfo == null && instanceInfo == null) {
            LOG.warn("Attempt to write empty file : " + fileName);
            return;
        }
        boolean auditAggregate = getArchiveDevice().isAuditAggregate();
        AuditLoggerDeviceExtension ext = device.getDeviceExtension(AuditLoggerDeviceExtension.class);
        for (AuditLogger auditLogger : ext.getAuditLoggers()) {
            if (auditLogger.isInstalled()) {
                Path dir = toDirPath(auditLogger);
                Path file = dir.resolve(fileName);
                boolean append = Files.exists(file);
                try {
                    if (!append)
                        Files.createDirectories(dir);
                    try (SpoolFileWriter writer = new SpoolFileWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                            append ? StandardOpenOption.APPEND : StandardOpenOption.CREATE_NEW))) {
                        if (!append) {
                            writer.writeLine(new AuditInfo(patStudyInfo));
                        }
                        writer.writeLine(new AuditInfo(instanceInfo));
                    }
                    if (!auditAggregate)
                        auditAndProcessFile(auditLogger, file);
                } catch (Exception e) {
                    LOG.warn("Failed to write to Audit Spool File - {} ", auditLogger.getCommonName(), file, e);
                }
            }
        }
    }

    private LinkedHashSet<Object> getDeletionObjsForSpooling(HashMap<String, HashSet<String>> sopClassMap,
                                                             AuditInfo i) {
        LinkedHashSet<Object> obj = new LinkedHashSet<>();
        obj.add(i);
        for (Map.Entry<String, HashSet<String>> entry : sopClassMap.entrySet()) {
            obj.add(new AuditInfo(new AuditInfoBuilder.Builder().sopCUID(entry.getKey())
                    .sopIUID(String.valueOf(entry.getValue().size())).build()));
        }
        return obj;
    }

    private void emitAuditMessage(
            AuditLogger logger, EventIdentificationBuilder eventIdentificationBuilder, ActiveParticipantBuilder[] activeParticipantBuilder,
            ParticipantObjectIdentificationBuilder... participantObjectIdentificationBuilder) {
        AuditMessage msg = AuditMessages.createMessage(eventIdentificationBuilder, activeParticipantBuilder, participantObjectIdentificationBuilder);
        msg.getAuditSourceIdentification().add(logger.createAuditSourceIdentification());
        try {
            logger.write(logger.timeStamp(), msg);
        } catch (Exception e) {
            LOG.warn("Failed to emit audit message", logger.getCommonName(), e);
        }
    }

    private String getEOI(String outcomeDesc) {
        return outcomeDesc != null ? AuditMessages.EventOutcomeIndicator.MinorFailure : AuditMessages.EventOutcomeIndicator.Success;
    }

    private EventIdentificationBuilder toCustomBuildEventIdentification(AuditServiceUtils.EventType et, String failureDesc, String warningDesc, Calendar t) {
        return failureDesc != null
                ? toBuildEventIdentification(et, failureDesc, t)
                : new EventIdentificationBuilder.Builder(
                    et.eventID, et.eventActionCode, t, AuditMessages.EventOutcomeIndicator.Success)
                    .outcomeDesc(warningDesc).build();
    }

    private EventIdentificationBuilder toBuildEventIdentification(AuditServiceUtils.EventType et, String desc, Calendar t) {
        return new EventIdentificationBuilder.Builder(
                et.eventID, et.eventActionCode, t, getEOI(desc)).outcomeDesc(desc).eventTypeCode(et.eventTypeCode).build();
    }
    
    private ParticipantObjectIdentificationBuilder patientPOI(AuditInfo auditInfo) {
        return new ParticipantObjectIdentificationBuilder.Builder(
                auditInfo.getField(AuditInfo.P_ID),
                AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                AuditMessages.ParticipantObjectTypeCode.Person,
                AuditMessages.ParticipantObjectTypeCodeRole.Patient)
                .name(auditInfo.getField(AuditInfo.P_NAME))
                .build();
    }

    private ParticipantObjectIdentificationBuilder submissionSetPOI(AuditInfo auditInfo) {
        return new ParticipantObjectIdentificationBuilder.Builder(
                auditInfo.getField(AuditInfo.SUBMISSION_SET_UID),
                AuditMessages.ParticipantObjectIDTypeCode.IHE_XDS_METADATA,
                AuditMessages.ParticipantObjectTypeCode.SystemObject,
                AuditMessages.ParticipantObjectTypeCodeRole.Job)
                .build();
    }

    private AuditMessages.UserIDTypeCode archiveUserIDTypeCode(String userID) {
        return  userID.indexOf('/') != -1
                ? AuditMessages.UserIDTypeCode.URI
                : userID.indexOf('|') != -1
                    ? AuditMessages.UserIDTypeCode.ApplicationFacility
                    : userID.equals(device.getDeviceName())
                        ? AuditMessages.UserIDTypeCode.DeviceName
                        : AuditMessages.UserIDTypeCode.StationAETitle;
    }

    private AuditMessages.UserIDTypeCode callingUserIDTypeCode(AuditMessages.UserIDTypeCode archiveUserIDTypeCode, String callingUserID) {
        if (callingUserID != null)
            return callingUserID.indexOf('|') != -1
                ? AuditMessages.UserIDTypeCode.ApplicationFacility
                : archiveUserIDTypeCode == AuditMessages.UserIDTypeCode.URI
                    ? AuditMessages.userIDTypeCode(callingUserID)
                    : AuditMessages.UserIDTypeCode.StationAETitle;

        LOG.warn("Calling user ID was not set during spooling.");
        return null;
    }

    public String toString(QueueMessage queueMsg) {
        StringWriter w = new StringWriter(256);
        try {
            queueMsg.writeAsJSON(w);
        } catch (IOException e) {
            LOG.warn(e.getMessage());
        }
        return w.toString();
    }
}
