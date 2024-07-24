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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import org.dcm4che3.audit.*;
import org.dcm4che3.conf.api.IApplicationEntityCache;
import org.dcm4che3.conf.api.IWebApplicationCache;
import org.dcm4che3.conf.api.hl7.IHL7ApplicationCache;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4che3.net.audit.AuditLoggerDeviceExtension;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.HL7DeviceExtension;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.AssociationEvent;
import org.dcm4chee.arc.ConnectionEvent;
import org.dcm4chee.arc.HL7ConnectionEvent;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.ArchiveHL7ApplicationExtension;
import org.dcm4chee.arc.conf.HL7OrderSPSStatus;
import org.dcm4chee.arc.conf.RejectionNote;
import org.dcm4chee.arc.delete.StudyDeleteContext;
import org.dcm4chee.arc.entity.Study;
import org.dcm4chee.arc.entity.Task;
import org.dcm4chee.arc.event.*;
import org.dcm4chee.arc.exporter.ExportContext;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.keycloak.KeycloakContext;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.pdq.PDQServiceContext;
import org.dcm4chee.arc.procedure.ProcedureContext;
import org.dcm4chee.arc.qstar.QStarVerification;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.retrieve.ExternalRetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.stgcmt.StgCmtContext;
import org.dcm4chee.arc.store.InstanceLocations;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreSession;
import org.dcm4chee.arc.study.StudyMgtContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Feb 2016
 */
@ApplicationScoped
public class AuditService {
    private final static Logger LOG = LoggerFactory.getLogger(AuditService.class);

    @Inject
    private Device device;

    @Inject
    private IHL7ApplicationCache hl7AppCache;

    @Inject
    private IWebApplicationCache webAppCache;

    @Inject
    private IApplicationEntityCache aeCache;

    private void aggregateAuditMessage(AuditLogger auditLogger, Path path) throws Exception {
        AuditUtils.EventType eventType = AuditUtils.EventType.fromFile(path);
        if (path.toFile().length() == 0) {
            LOG.info("Attempt to read from an empty file {} by {}.", path, eventType);
            return;
        }
        switch (eventType.eventClass) {
            case APPLN_ACTIVITY:
                ApplicationActivityAuditService.audit(auditLogger, path, eventType);
                break;
            case CONN_FAILURE:
                auditConnectionFailure(auditLogger, path, eventType);
                break;
            case STORE_WADOR:
                if (eventType.name().startsWith("WADO")) auditWADORetrieve(auditLogger, path, eventType);
                else StoreAuditService.audit(auditLogger, path, eventType);
                break;
            case RETRIEVE:
                auditRetrieve(auditLogger, path, eventType);
                break;
            case USER_DELETED:
            case SCHEDULER_DELETED:
                DeletionAuditService.audit(auditLogger, path, eventType);
                break;
            case QUERY:
                auditQuery(auditLogger, path, eventType);
                break;
            case PATIENT:
                auditPatientRecord(auditLogger, path, eventType);
                break;
            case PROCEDURE:
                auditProcedureRecord(auditLogger, path, eventType);
                break;
            case STUDY:
                auditStudyRecord(auditLogger, path, eventType);
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
                TaskAuditService.audit(auditLogger, path, eventType);
                break;
            case IMPAX:
                auditPatientMismatch(auditLogger, path, eventType);
                break;
            case ASSOCIATION_FAILURE:
                auditAssociationFailure(auditLogger, path, eventType);
                break;
            case QSTAR:
                QStarVerificationAuditService.audit(auditLogger, path, eventType, getArchiveDevice());
                break;
        }
    }

    void spoolApplicationActivity(ArchiveServiceEvent event) {
        String fileName = AuditUtils.EventType.forApplicationActivity(event).name();
        try {
            HttpServletRequest request = event.getRequest();
            if (request == null) {
                writeSpoolFile(fileName, false, new AuditInfoBuilder.Builder()
                                                .calledUserID(device.getDeviceName())
                                                .toAuditInfo());
                return;
            }

            writeSpoolFile(fileName, false, new AuditInfoBuilder.Builder()
                                        .calledUserID(request.getRequestURI())
                                        .callingUserID(KeycloakContext.valueOf(request).getUserName())
                                        .callingHost(request.getRemoteAddr())
                                        .toAuditInfo());
        } catch (Exception e) {
            LOG.info("Failed to spool Application Activity {}\n", event, e);
        }
    }

    private void spoolInstancesRejected(StoreContext ctx) {
        StoreSession storeSession = ctx.getStoreSession();
        boolean schedulerDeletedExpiredStudies = storeSession.getAssociation() == null
                                                    && storeSession.getHttpRequest() == null;
        String fileName = AuditUtils.EventType.forInstancesRejected(ctx).name();
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        try {
            List<AuditInfo> studyRejected = new ArrayList<>();
            List<AuditInfo> sopInstancesRejected = sopInstancesRejectionNote(ctx.getAttributes());
            if (schedulerDeletedExpiredStudies) {
                studyRejected.add(new AuditInfoBuilder.Builder()
                                        .callingUserID(device.getDeviceName())
                                        .studyUIDAccNumDate(ctx.getAttributes(), arcDev)
                                        .pIDAndName(ctx.getAttributes(), arcDev)
                                        .outcome(ctx.getException() == null ? null : ctx.getException().getMessage())
                                        .warning(ctx.getRejectionNote().getRejectionNoteCode().getCodeMeaning())
                                        .toAuditInfo());
                studyRejected.addAll(sopInstancesRejected);
                writeSpoolFile(fileName, false, studyRejected.toArray(new AuditInfo[0]));
                return;
            }

            HttpServletRequestInfo httpServletRequestInfo = storeSession.getHttpRequest();
            String callingUserID = httpServletRequestInfo == null
                                    ? storeSession.getCallingAET() == null
                                        ? storeSession.getLocalApplicationEntity().getAETitle()
                                        : storeSession.getCallingAET()
                                    : httpServletRequestInfo.requesterUserID;
            String calledUserID = httpServletRequestInfo == null
                                    ? storeSession.getCalledAET()
                                    : httpServletRequestInfo.requestURIWithQueryStr();

            studyRejected.add(new AuditInfoBuilder.Builder()
                                    .callingUserID(callingUserID)
                                    .callingHost(storeSession.getRemoteHostName())
                                    .calledUserID(calledUserID)
                                    .studyUIDAccNumDate(ctx.getAttributes(), arcDev)
                                    .pIDAndName(ctx.getAttributes(), arcDev)
                                    .outcome(ctx.getException() == null ? null : ctx.getException().getMessage())
                                    .warning(ctx.getRejectionNote().getRejectionNoteCode().getCodeMeaning())
                                    .toAuditInfo());
            studyRejected.addAll(sopInstancesRejected);
            writeSpoolFile(fileName, false, studyRejected.toArray(new AuditInfo[0]));
        } catch (Exception e) {
            LOG.info("Failed to spool Instances Rejected {}\n", ctx.getStoreSession(), e);
        }
    }

    private void spoolPreviousInstancesDeleted(StoreContext ctx) {
        StoreSession storeSession = ctx.getStoreSession();
        Study prevStudy = ctx.getPreviousInstance().getSeries().getStudy();
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        String fileName = AuditUtils.EventType.forPreviousInstancesDeleted(ctx).name()
                            + '-' + storeSession.getCallingAET()
                            + '-' + storeSession.getCalledAET()
                            + '-' + ctx.getStudyInstanceUID();
        try {
            List<AuditInfo> prevInstancesDeleted = new ArrayList<>();
            prevInstancesDeleted.add(new AuditInfoBuilder.Builder()
                                            .callingHost(storeSession.getRemoteHostName())
                                            .callingUserID(storeSession.getCallingAET())
                                            .calledUserID(storeSession.getCalledAET())
                                            .outcome(ctx.getException() == null ? null : ctx.getException().getMessage())
                                            .studyUIDAccNumDate(prevStudy.getAttributes(), arcDev)
                                            .pIDAndName(prevStudy.getPatient().getAttributes(), arcDev)
                                            .toAuditInfo());
            prevInstancesDeleted.add(new AuditInfoBuilder.Builder()
                                            .sopCUID(ctx.getAttributes().getString(Tag.SOPClassUID))
                                            .sopIUID(ctx.getAttributes().getString(Tag.SOPInstanceUID))
                                            .toAuditInfo());
            writeSpoolFile(fileName, true, prevInstancesDeleted.toArray(new AuditInfo[0]));
        } catch (Exception e) {
            LOG.info("Failed to spool previous instances deleted {}\n", storeSession, e);
        }
    }

    void spoolStudyDeleted(StudyDeleteContext ctx) {
        String fileName = AuditUtils.EventType.forStudyDeleted(ctx).name();
        HttpServletRequestInfo httpServletRequestInfo = ctx.getHttpServletRequestInfo();
        Study study = ctx.getStudy();
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        try {
            List<AuditInfo> studyDeleted = new ArrayList<>();
            List<AuditInfo> sopInstancesDeleted = sopInstancesDeleted(ctx);
            if (httpServletRequestInfo == null) {
                studyDeleted.add(new AuditInfoBuilder.Builder()
                                                .callingUserID(device.getDeviceName())
                                                .studyUIDAccNumDate(study.getAttributes(), arcDev)
                                                .pIDAndName(study.getPatient().getAttributes(), arcDev)
                                                .outcome(ctx.getException() == null ? null : ctx.getException().getMessage())
                                                .toAuditInfo());
                studyDeleted.addAll(sopInstancesDeleted);
                writeSpoolFile(fileName, false, studyDeleted.toArray(new AuditInfo[0]));
                return;
            }

            studyDeleted.add(new AuditInfoBuilder.Builder()
                    .callingUserID(httpServletRequestInfo.requesterUserID)
                    .callingHost(httpServletRequestInfo.requesterHost)
                    .calledUserID(requestURLWithQueryParams(httpServletRequestInfo))
                    .studyUIDAccNumDate(study.getAttributes(), arcDev)
                    .pIDAndName(study.getPatient().getAttributes(), arcDev)
                    .outcome(ctx.getException() == null ? null : ctx.getException().getMessage())
                    .toAuditInfo());
            studyDeleted.addAll(sopInstancesDeleted);
            writeSpoolFile(fileName, false, studyDeleted.toArray(new AuditInfo[0]));
        } catch (Exception e) {
            LOG.info("Failed to spool Study Deleted for {}\n", ctx.getStudy(), e);
        }
    }

    private List<AuditInfo> sopInstancesDeleted(StudyDeleteContext ctx) {
        List<AuditInfo> sopInstancesDeleted = new ArrayList<>();
        ctx.getInstances().forEach(instance -> {
            sopInstancesDeleted.add(new AuditInfoBuilder.Builder()
                    .sopCUID(instance.getSopClassUID())
                    .sopIUID(instance.getSopInstanceUID())
                    .toAuditInfo());
        });
        return sopInstancesDeleted;
    }

    void spoolExternalRejection(RejectionNoteSent rejectionNoteSent) {
        String fileName = AuditUtils.EventType.forExternalRejection(rejectionNoteSent).name();
        Attributes attrs = rejectionNoteSent.getRejectionNote();
        HttpServletRequest req = rejectionNoteSent.getRequest();
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        try {
            Attributes codeItem = attrs.getSequence(Tag.ConceptNameCodeSequence).get(0);
            List<AuditInfo> studyRejectionNoteSent = new ArrayList<>();
            studyRejectionNoteSent.add(new AuditInfoBuilder.Builder()
                                        .callingUserID(KeycloakContext.valueOf(req).getUserName())
                                        .callingHost(req.getRemoteHost())
                                        .calledUserID(req.getRequestURI())
                                        .destUserID(rejectionNoteSent.getRemoteAE().getAETitle())
                                        .destNapID(rejectionNoteSent.getRemoteAE().getConnections().get(0).getHostname())
                                        .outcome(rejectionNoteSent.getErrorComment())
                                        .warning(codeItem.getString(Tag.CodeMeaning))
                                        .studyUIDAccNumDate(attrs, arcDev)
                                        .pIDAndName(attrs, arcDev)
                                        .toAuditInfo());
            studyRejectionNoteSent.addAll(sopInstancesRejectionNote(rejectionNoteSent.getRejectionNote()));
            writeSpoolFile(fileName, false, studyRejectionNoteSent.toArray(new AuditInfo[0]));
        } catch (Exception e) {
            LOG.info("Failed to spool External Rejection {}\n", rejectionNoteSent, e);
        }
    }

    private List<AuditInfo> sopInstancesRejectionNote(Attributes attrs) {
        List<AuditInfo> sopInstancesRejectionNoteSent = new ArrayList<>();
        attrs.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence)
                .forEach(studyRef ->
                    studyRef.getSequence(Tag.ReferencedSeriesSequence).forEach(seriesRef ->
                        seriesRef.getSequence(Tag.ReferencedSOPSequence).forEach(sopRef ->
                                sopInstancesRejectionNoteSent.add(new AuditInfoBuilder.Builder()
                                        .sopCUID(sopRef.getString(Tag.ReferencedSOPClassUID))
                                        .sopIUID(sopRef.getString(Tag.ReferencedSOPInstanceUID))
                                        .toAuditInfo())
                        )
                )
        );
        return sopInstancesRejectionNoteSent;
    }

    void spoolTaskEvent(TaskEvent taskEvent) {
        if (taskEvent.getTask() == null)
            return;

        Task task = taskEvent.getTask();
        try {
            String fileName = AuditUtils.EventType.forTaskEvent(taskEvent.getOperation()).name();
            HttpServletRequest req = taskEvent.getRequest();
            AuditInfo taskAuditInfo = new AuditInfoBuilder.Builder()
                    .callingUserID(KeycloakContext.valueOf(req).getUserName())
                    .callingHost(req.getRemoteHost())
                    .calledUserID(req.getRequestURI())
                    .outcome(taskEvent.getException() == null ? null : taskEvent.getException().getMessage())
                    .task(TaskAuditService.toString(task))
                    .taskPOID(Long.toString(task.getPk()))
                    .toAuditInfo();
            writeSpoolFile(fileName, false, taskAuditInfo);
        } catch (Exception e) {
            LOG.info("Failed to spool {} for {} \n", taskEvent, task, e);
        }
    }

    void spoolBulkTasksEvent(BulkTaskEvent bulkTasksEvent) {
        HttpServletRequest req = bulkTasksEvent.getRequest();
        try {
            String fileName = AuditUtils.EventType.forTaskEvent(bulkTasksEvent.getOperation()).name();
            if (req == null) {
                writeSpoolFile(fileName, false,
                        new AuditInfoBuilder.Builder()
                            .callingUserID(device.getDeviceName())
                            .outcome(bulkTasksEvent.getException() == null ? null : bulkTasksEvent.getException().getMessage())
                            .count(bulkTasksEvent.getCount())
                            .failed(bulkTasksEvent.getFailed())
                            .taskPOID(bulkTasksEvent.getOperation().name())
                            .queueName(bulkTasksEvent.getQueueName())
                            .toAuditInfo());
                return;
            }

            writeSpoolFile(fileName, false,
                    new AuditInfoBuilder.Builder()
                            .callingUserID(KeycloakContext.valueOf(req).getUserName())
                            .callingHost(req.getRemoteHost())
                            .calledUserID(req.getRequestURI())
                            .outcome(bulkTasksEvent.getException() == null ? null : bulkTasksEvent.getException().getMessage())
                            .count(bulkTasksEvent.getCount())
                            .failed(bulkTasksEvent.getFailed())
                            .taskPOID(bulkTasksEvent.getOperation().name())
                            .filters(req.getQueryString())
                            .toAuditInfo());
        } catch (Exception e) {
            LOG.info("Failed to spool {} \n", bulkTasksEvent, e);
        }
    }

    void spoolSoftwareConfiguration(SoftwareConfiguration softwareConfiguration) {
        String callingUser = softwareConfiguration.getRequest() != null
                ? KeycloakContext.valueOf(softwareConfiguration.getRequest()).getUserName()
                : softwareConfiguration.getDeviceName();
        try {
            writeSpoolFile(
                    SoftwareConfigurationAuditService.auditInfo(softwareConfiguration, callingUser),
                    AuditUtils.EventType.LDAP_CHNGS,
                    softwareConfiguration.getLdapDiff().toString().getBytes());
        } catch (Exception e) {
            LOG.info("Failed to spool Software Configuration Changes for [Device={}] done by [CallingUser={}]\n",
                    softwareConfiguration.getDeviceName(), callingUser, e);
        }
    }

    private void auditSoftwareConfiguration(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType)
            throws Exception {
        emitAuditMessage(
                SoftwareConfigurationAuditService.auditMsg(auditLogger, path, eventType),
                auditLogger);
    }

    void spoolExternalRetrieve(ExternalRetrieveContext ctx) {
        try {
            writeSpoolFile(AuditUtils.EventType.INST_RETRV, null,
                    ExternalRetrieveAuditService.auditInfo(ctx, getArchiveDevice()));
        } catch (Exception e) {
            LOG.info("Failed to spool External Retrieve for [StudyIUID={}] triggered by [Requester={}]\n",
                    ctx.getStudyInstanceUID(), ctx.getRequesterUserID(), e);
        }
    }

    private void auditExternalRetrieve(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType)
            throws Exception {
        emitAuditMessage(
                ExternalRetrieveAuditService.auditMsg(auditLogger, path, eventType, aeCache),
                auditLogger);
    }

    void spoolConnectionFailure(ConnectionEvent event) {
        if (event.getRemoteConnection().getProtocol().name().startsWith("SYSLOG")) {
            LOG.info("Suppress audits of connection failures to audit record repository : {}",
                    event.getRemoteConnection().getDevice());
            return;
        }

        try {
            writeSpoolFile(
                    AuditUtils.EventType.CONN_FAILR,
                    null,
                    ConnectionEventsAuditService.connFailureAuditInfo(event));
        } catch (Exception e) {
            LOG.info("Failed to spool Connection Failure for [EventType={}]\n", event.getType(), e);
        }
    }

    private void auditConnectionFailure(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType)
            throws Exception {
        emitAuditMessage(
                ConnectionEventsAuditService.auditMsg(auditLogger, path, eventType),
                auditLogger);
    }

    void spoolStudySizeEvent(StudySizeEvent event) {
        try {
            AuditInfoBuilder auditInfoBuilder = new AuditInfoBuilder.Builder()
                                                    .callingUserID(device.getDeviceName())
                                                    .studyUIDAccNumDate(event.getStudy().getAttributes(), getArchiveDevice())
                                                    .pIDAndName(event.getStudy().getPatient().getAttributes(), getArchiveDevice())
                                                    .build();
            writeSpoolFile(AuditUtils.EventType.STUDY_READ, null, auditInfoBuilder);
        } catch (Exception e) {
            LOG.info("Failed to spool study size info for {}\n", event, e);
        }
    }

    private void auditStudySize(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) throws Exception {
        SpoolFileReader reader = new SpoolFileReader(path.toFile());
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        EventIdentification ei = EventID.toEventIdentification(auditLogger, path, eventType, auditInfo);
        ActiveParticipant[] activeParticipants = new ActiveParticipant[1];
        activeParticipants[0] = new ActiveParticipantBuilder(auditInfo.getField(AuditInfo.CALLING_USERID),
                                        getLocalHostName(auditLogger))
                                        .userIDTypeCode(AuditMessages.UserIDTypeCode.DeviceName)
                                        .altUserID(AuditLogger.processID())
                                        .isRequester()
                                        .build();
        ParticipantObjectIdentificationBuilder studyPOI = ParticipantObjectID.studyPOI(
                                                                    auditInfo.getField(AuditInfo.STUDY_UID));
        studyPOI.lifeCycle(AuditMessages.ParticipantObjectDataLifeCycle.AggregationSummarizationDerivation);
        ParticipantObjectIdentificationBuilder patientPOI = ParticipantObjectID.patientPOIBuilder(auditInfo);
        AuditMessage auditMsg = AuditMessages.createMessage(
                                                ei,
                                                activeParticipants,
                                                studyPOI.build(), patientPOI.build());
        emitAuditMessage(auditMsg, auditLogger);
    }

    void spoolPDQ(PDQServiceContext ctx) {
        try {
            if (ctx.getFhirWebAppName() == null)
                writeSpoolFile(PDQAuditService.auditInfo(ctx, getArchiveDevice()),
                        AuditUtils.EventType.PAT_DEMO_Q,
                        ctx.getHl7Msg().data(),
                        ctx.getRsp().data());
            else
                writeSpoolFile(AuditUtils.EventType.FHIR___PDQ.name(), false,
                        PDQAuditService.auditInfoFHIR(ctx, getArchiveDevice()));
        } catch (Exception e) {
            LOG.info("Failed to spool PDQ for {}", ctx);
        }
    }

    void spoolQuery(QueryContext ctx) {
        if (ctx.getAssociation() == null && ctx.getHttpRequest() == null)
            return;

        HttpServletRequestInfo httpServletRequestInfo = ctx.getHttpRequest();
        AuditInfo auditInfo = httpServletRequestInfo == null
                                ? new AuditInfoBuilder.Builder()
                                        .callingHost(ctx.getRemoteHostName())
                                        .callingUserID(ctx.getCallingAET())
                                        .calledUserID(ctx.getCalledAET())
                                        .queryPOID(ctx.getSOPClassUID())
                                        .toAuditInfo()
                                : new AuditInfoBuilder.Builder()
                                        .callingHost(ctx.getRemoteHostName())
                                        .callingUserID(httpServletRequestInfo.requesterUserID)
                                        .calledUserID(httpServletRequestInfo.requestURI)
                                        .queryPOID(ctx.getSearchMethod())
                                        .queryString(httpServletRequestInfo.queryString)
                                        .toAuditInfo();
        writeQuerySpoolFile(auditInfo, ctx);
    }

    private void writeQuerySpoolFile(AuditInfo auditInfo, QueryContext ctx) {
        FileTime eventTime = null;
        try {
            for (AuditLogger auditLogger : auditLoggersForQuery(ctx)) {
                Path directory = toDirPath(auditLogger);
                try {
                    Files.createDirectories(directory);
                    Path file = Files.createTempFile(directory, AuditUtils.EventType.QUERY__EVT.name(), null);
                    try (BufferedOutputStream out = new BufferedOutputStream(
                            Files.newOutputStream(file, StandardOpenOption.APPEND))) {
                        new DataOutputStream(out).writeUTF(auditInfo.toString());
                        if (ctx.getAssociation() != null) {
                            try (DicomOutputStream dos = new DicomOutputStream(out, UID.ImplicitVRLittleEndian)) {
                                dos.writeDataset(null, ctx.getQueryKeys());
                            } catch (Exception e) {
                                LOG.info("Failed to create DicomOutputStream.\n", e);
                            }
                        }
                    }

                    if (eventTime == null)
                        eventTime = Files.getLastModifiedTime(file);
                    else
                        Files.setLastModifiedTime(file, eventTime);

                    if (!getArchiveDevice().isAuditAggregate())
                        auditAndProcessFile(auditLogger, file);
                } catch (Exception e) {
                    LOG.info("Failed to write to Query Audit Spool File at [AuditLogger={}]\n",
                            auditLogger.getCommonName(), e);
                }
            }
        } catch (Exception e) {
            LOG.info("Failed to spool Query\n", e);
        }
    }

    private List<AuditLogger> auditLoggersForQuery(QueryContext ctx) {
        AuditLoggerDeviceExtension ext = device.getDeviceExtension(AuditLoggerDeviceExtension.class);
        if (ext == null)
            return Collections.emptyList();

        return ext.getAuditLoggers().stream()
                .filter(auditLogger -> auditLogger.isInstalled()
                                        && !auditLogger.isAuditMessageSuppressed(minimalAuditMsgForQuery(ctx)))
                .collect(Collectors.toList());
    }

    private AuditMessage minimalAuditMsgForQuery(QueryContext ctx) {
        AuditMessage msg = new AuditMessage();
        EventIdentification ei = new EventIdentification();
        ei.setEventID(AuditMessages.EventID.Query);
        ei.setEventActionCode(AuditMessages.EventActionCode.Execute);
        ei.setEventOutcomeIndicator(AuditMessages.EventOutcomeIndicator.Success);
        msg.setEventIdentification(ei);

        ActiveParticipant ap = new ActiveParticipant();
        ap.setUserID(ctx.getCallingAET());
        ap.setUserIsRequestor(true);
        msg.getActiveParticipant().add(ap);
        return msg;
    }

    void auditAndProcessFile(AuditLogger auditLogger, Path file) {
        try {
            aggregateAuditMessage(auditLogger, file);
            Files.delete(file);
        } catch (Exception e) {
            LOG.info("Failed to process [AuditSpoolFile={}] of [AuditLogger={}].\n",
                    file, auditLogger.getCommonName(), e);
            try {
                Files.move(file, file.resolveSibling(file.getFileName().toString() + ".failed"));
            } catch (IOException e1) {
                LOG.info("Failed to mark [AuditSpoolFile={}] of [AuditLogger={}] as failed.\n",
                        file, auditLogger.getCommonName(), e1);
            }
        }
    }

    private void auditQuery(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) throws Exception {
        if (eventType.eventTypeCode == null) {
            QueryAuditService.auditMsg(auditLogger, path, eventType);
            return;
        }

        if (eventType.eventTypeCode == AuditMessages.EventTypeCode.ITI_78_MobilePDQ) {
            PDQAuditService.auditFHIRPDQMsg(auditLogger, path, eventType, webAppCache);
            return;
        }

        PDQAuditService.auditHL7PDQMsg(auditLogger, path, eventType, hl7AppCache, device);
    }

    void spoolStoreEvent(StoreContext ctx) {
        try {
            if (ctx.getRejectedInstance() != null) {
                LOG.info("Suppress audit on receive of instances rejected by a previous received Rejection Note : {}",
                        ctx.getRejectedInstance());
                return;
            }

            RejectionNote rejectionNote = ctx.getRejectionNote();
            if (rejectionNote != null && !rejectionNote.isRevokeRejection()) {
                spoolInstancesRejected(ctx);
                return;
            }

            if (isDuplicateReceivedInstance(ctx)) {
                if (rejectionNote != null && rejectionNote.isRevokeRejection())
                    spoolInstancesStored(ctx);
                return;
            }

            if (ctx.getAttributes() == null) {
                LOG.info("Instances stored is not audited as store context attributes are not set.\n",
                        ctx.getException());
                return;
            }

            spoolInstancesStored(ctx);
        } catch (Exception e) {
            LOG.info("Failed to spool Store Event.\n", e);
        }
    }

    private void spoolInstancesStored(StoreContext ctx) {
        if (ctx.getPreviousInstance() != null
                && ctx.getPreviousInstance().getSopInstanceUID().equals(ctx.getStoredInstance().getSopInstanceUID()))
            spoolPreviousInstancesDeleted(ctx);

        AuditUtils.EventType eventType = AuditUtils.EventType.forInstanceStored(ctx);
        StoreSession storeSession = ctx.getStoreSession();
        try {
            AuditInfo instanceInfo = new AuditInfoBuilder.Builder()
                    .sopCUID(ctx.getSopClassUID())
                    .sopIUID(ctx.getSopInstanceUID())
                    .mppsUID(ctx.getMppsInstanceUID())
                    .outcome(ctx.getException() == null ? null : ctx.getException().getMessage())
                    .errorCode(ctx.getException())
                    .toAuditInfo();

            if (storeSession.getImpaxReportEndpoint() != null) {
                spoolInstancesStoredImpaxReport(instanceInfo, eventType, ctx);
                return;
            }

            if (storeSession.getHttpRequest() != null)
                spoolInstancesStoredBySTOW(instanceInfo, eventType, ctx);
            else if (storeSession.getUnparsedHL7Message() != null)
                spoolInstancesStoredByHL7(instanceInfo, eventType, ctx);
            else
                spoolInstancesStoredByCStore(instanceInfo, eventType, ctx);
        } catch (Exception e) {
            LOG.info("Failed to spool Instances Stored for [StudyIUID={}] triggered by {}\n",
                    ctx.getStudyInstanceUID(), storeSession, e);
        }
    }

    private void spoolInstancesStoredByCStore(AuditInfo instanceInfo, AuditUtils.EventType eventType, StoreContext ctx) {
        StoreSession storeSession = ctx.getStoreSession();
        Attributes attrs = ctx.getAttributes();
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        AuditInfo auditInfo = new AuditInfoBuilder.Builder()
                                    .callingHost(storeSession.getRemoteHostName())
                                    .callingUserID(storeSession.getCallingAET())
                                    .calledUserID(storeSession.getCalledAET())
                                    .studyUIDAccNumDate(attrs, arcDev)
                                    .pIDAndName(attrs, arcDev)
                                    .toAuditInfo();
        String fileName = eventType.name()
                            + '-' + storeSession.getCallingAET()
                            + '-' + storeSession.getCalledAET()
                            + '-' + ctx.getStudyInstanceUID();
        if (ctx.getException() != null)
            fileName += "_ERROR";

        writeSpoolFile(fileName, true, auditInfo, instanceInfo);
    }

    private void spoolInstancesStoredBySTOW(AuditInfo instanceInfo, AuditUtils.EventType eventType, StoreContext ctx) {
        StoreSession storeSession = ctx.getStoreSession();
        HttpServletRequestInfo httpServletRequestInfo = storeSession.getHttpRequest();
        Attributes attrs = ctx.getAttributes();
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        AuditInfo auditInfo = new AuditInfoBuilder.Builder()
                                    .callingHost(storeSession.getRemoteHostName())
                                    .callingUserID(httpServletRequestInfo.requesterUserID)
                                    .calledUserID(httpServletRequestInfo.requestURIWithQueryStr())
                                    .studyUIDAccNumDate(attrs, arcDev)
                                    .pIDAndName(attrs, arcDev)
                                    .toAuditInfo();
        String fileName = eventType.name()
                            + '-' + httpServletRequestInfo.requesterUserID
                            + '-' + storeSession.getCalledAET()
                            + '-' + ctx.getStudyInstanceUID();
        if (ctx.getException() != null)
            fileName += "_ERROR";

        writeSpoolFile(fileName, true, auditInfo, instanceInfo);
    }

    private void spoolInstancesStoredByHL7(AuditInfo instanceInfo, AuditUtils.EventType eventType, StoreContext ctx) {
        StoreSession storeSession = ctx.getStoreSession();
        Attributes attrs = ctx.getAttributes();
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        HL7Segment msh = storeSession.getUnparsedHL7Message().msh();
        AuditInfo auditInfo = new AuditInfoBuilder.Builder()
                                    .callingHost(storeSession.getRemoteHostName())
                                    .callingUserID(msh.getSendingApplicationWithFacility())
                                    .calledUserID(msh.getReceivingApplicationWithFacility())
                                    .studyUIDAccNumDate(attrs, arcDev)
                                    .pIDAndName(attrs, arcDev)
                                    .toAuditInfo();
        String fileName = eventType.name()
                            + '-' + msh.getSendingApplicationWithFacility().replace('|', '-')
                            + '-' + msh.getReceivingApplicationWithFacility().replace('|', '-')
                            + '-' + ctx.getStudyInstanceUID();
        if (ctx.getException() != null)
            fileName += "_ERROR";

        writeSpoolFile(fileName, true, auditInfo, instanceInfo);
    }

    private void spoolInstancesStoredImpaxReport(AuditInfo instanceInfo, AuditUtils.EventType eventType, StoreContext ctx) {
        StoreSession storeSession = ctx.getStoreSession();
        HttpServletRequestInfo httpServletRequestInfo = storeSession.getHttpRequest();
        Attributes attrs = ctx.getAttributes();
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);

        AuditInfo auditInfoStore = httpServletRequestInfo == null
                                    ? new AuditInfoBuilder.Builder()
                                        .callingUserID(device.getDeviceName())
                                        .calledUserID(storeSession.getCalledAET())
                                        .studyUIDAccNumDate(attrs, arcDev)
                                        .pIDAndName(attrs, arcDev)
                                        .impaxEndpoint(storeSession.getImpaxReportEndpoint())
                                        .toAuditInfo()
                                    : new AuditInfoBuilder.Builder()
                                        .callingHost(storeSession.getRemoteHostName())
                                        .callingUserID(httpServletRequestInfo.requesterUserID)
                                        .calledUserID(httpServletRequestInfo.requestURIWithQueryStr())
                                        .studyUIDAccNumDate(attrs, arcDev)
                                        .pIDAndName(attrs, arcDev)
                                        .impaxEndpoint(storeSession.getImpaxReportEndpoint())
                                        .toAuditInfo();

        String fileName = eventType.name()
                            + '-' + (httpServletRequestInfo == null
                                        ? device.getDeviceName()
                                        : httpServletRequestInfo.requesterUserID)
                            + '-' + storeSession.getCalledAET()
                            + '-' + ctx.getStudyInstanceUID();
        if (ctx.getException() != null)
            fileName += "_ERROR";

        writeSpoolFile(fileName, true, auditInfoStore, instanceInfo);
        if (ctx.getImpaxReportPatientMismatch() != null) {
            AuditInfo auditInfoPatientMismatch = httpServletRequestInfo == null
                                                    ? new AuditInfoBuilder.Builder()
                                                        .callingUserID(device.getDeviceName())
                                                        .calledUserID(storeSession.getCalledAET())
                                                        .studyUIDAccNumDate(attrs, arcDev)
                                                        .pIDAndName(attrs, arcDev)
                                                        .impaxEndpoint(storeSession.getImpaxReportEndpoint())
                                                        .patMismatchCode(ctx.getImpaxReportPatientMismatch().toString())
                                                        .toAuditInfo()
                                                    : new AuditInfoBuilder.Builder()
                                                        .callingHost(storeSession.getRemoteHostName())
                                                        .callingUserID(httpServletRequestInfo.requesterUserID)
                                                        .calledUserID(httpServletRequestInfo.requestURIWithQueryStr())
                                                        .studyUIDAccNumDate(attrs, arcDev)
                                                        .pIDAndName(attrs, arcDev)
                                                        .impaxEndpoint(storeSession.getImpaxReportEndpoint())
                                                        .patMismatchCode(ctx.getImpaxReportPatientMismatch().toString())
                                                        .toAuditInfo();
            writeSpoolFile(AuditUtils.EventType.IMPAX_MISM.name(), false, auditInfoPatientMismatch, instanceInfo);
        }
    }

    private void auditPatientMismatch(AuditLogger logger, Path path, AuditUtils.EventType eventType) throws Exception {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        AuditMessage auditMsg = AuditMessages.createMessage(
                EventID.toEventIdentification(logger, path, eventType, auditInfo),
                patientMismatchActiveParticipants(logger, auditInfo),
                ParticipantObjectID.studyPatParticipants(auditInfo, reader.getInstanceLines(), eventType, logger));
        emitAuditMessage(auditMsg, logger);
    }


    private ActiveParticipant[] patientMismatchActiveParticipants(AuditLogger logger, AuditInfo auditInfo) {
        ActiveParticipant[] activeParticipants = new ActiveParticipant[3];
        String callingUserID = auditInfo.getField(AuditInfo.CALLING_USERID);
        String callingHost = auditInfo.getField(AuditInfo.CALLING_HOST);
        String calledUserID = auditInfo.getField(AuditInfo.CALLED_USERID);
        activeParticipants[0] = new ActiveParticipantBuilder(calledUserID, getLocalHostName(logger))
                .userIDTypeCode(userIDTypeCode(calledUserID)).build();
        activeParticipants[1] = new ActiveParticipantBuilder(
                callingUserID, callingHost != null ? callingHost : getLocalHostName(logger))
                .userIDTypeCode(callingHost != null
                        ? AuditMessages.userIDTypeCode(callingUserID) : AuditMessages.UserIDTypeCode.DeviceName)
                .isRequester().build();
        String impaxEndpoint = auditInfo.getField(AuditInfo.IMPAX_ENDPOINT);
        activeParticipants[2] = new ActiveParticipantBuilder(
                impaxEndpoint, impaxEndpointHost(impaxEndpoint))
                .userIDTypeCode(userIDTypeCode(impaxEndpoint))
                .build();
        return activeParticipants;
    }

    private String impaxEndpointHost(String impaxEndpoint) {
        String impaxEndpointRelative = impaxEndpoint.substring(impaxEndpoint.indexOf("//") + 2);
        return impaxEndpointRelative.substring(0, impaxEndpointRelative.indexOf('/'));
    }

    private boolean isDuplicateReceivedInstance(StoreContext ctx) {
        return ctx.getLocations().isEmpty() && ctx.getStoredInstance() == null && ctx.getException() == null;
    }

    void spoolRetrieveWADO(RetrieveContext ctx) {
        if (ctx.getSopInstanceUIDs().length == 0) {
            LOG.info("SOP Instance for Retrieve object by WADO URI audit not available in retrieve context, exit spooling");
            return;
        }

        HttpServletRequestInfo httpServletRequestInfo = ctx.getHttpServletRequestInfo();
        try {
            Attributes attrs = ctx.getMatches().get(0).getAttributes();
            String suffix = '-' + httpServletRequestInfo.requesterHost
                    + '-' + ctx.getLocalAETitle()
                    + '-' + ctx.getStudyInstanceUIDs()[0];
            AuditInfoBuilder info = new AuditInfoBuilder.Builder()
                    .callingHost(httpServletRequestInfo.requesterHost)
                    .callingUserID(httpServletRequestInfo.requesterUserID)
                    .calledUserID(requestURLWithQueryParams(httpServletRequestInfo))
                    .studyUIDAccNumDate(attrs, getArchiveDevice())
                    .pIDAndName(attrs, getArchiveDevice())
                    .outcome(null != ctx.getException() ? ctx.getException().getMessage() : null)
                    .build();
            AuditInfoBuilder instanceInfo = new AuditInfoBuilder.Builder()
                    .sopCUID(attrs.getString(Tag.SOPClassUID))
                    .sopIUID(ctx.getSopInstanceUIDs()[0])
                    .build();
            writeSpoolFile(AuditUtils.EventType.WADO___URI, suffix, info, instanceInfo);
        } catch (Exception e) {
            LOG.info("Failed to spool Wado Retrieve for [StudyIUID={}] triggered by [User={}]\n",
                    ctx.getStudyInstanceUID(), httpServletRequestInfo.requesterUserID, e);
        }
    }

    private String requestURLWithQueryParams(HttpServletRequestInfo httpServletRequestInfo) {
        return httpServletRequestInfo.queryString == null
                ? httpServletRequestInfo.requestURI
                : httpServletRequestInfo.requestURI + "?" + httpServletRequestInfo.queryString;
    }

    private void auditWADORetrieve(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) throws Exception {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());

        AuditMessage auditMsg = AuditMessages.createMessage(
                EventID.toEventIdentification(auditLogger, path, eventType, auditInfo),
                storeWadoURIActiveParticipants(auditLogger, auditInfo, eventType),
                ParticipantObjectID.studyPatParticipants(auditInfo, reader.getInstanceLines(), eventType, auditLogger));
        emitAuditMessage(auditMsg, auditLogger);
    }

    private ActiveParticipant[] storeWadoURIActiveParticipants(
            AuditLogger auditLogger, AuditInfo auditInfo, AuditUtils.EventType eventType) {
        ActiveParticipant[] activeParticipants = new ActiveParticipant[3];
        String callingUserID = auditInfo.getField(AuditInfo.CALLING_USERID);
        String archiveUserID = auditInfo.getField(AuditInfo.CALLED_USERID);
        AuditMessages.UserIDTypeCode archiveUserIDTypeCode = userIDTypeCode(archiveUserID);
        activeParticipants[0] = new ActiveParticipantBuilder(
                archiveUserID,
                getLocalHostName(auditLogger))
                .userIDTypeCode(archiveUserIDTypeCode)
                .altUserID(AuditLogger.processID())
                .roleIDCode(eventType.destination)
                .build();
        String impaxEndpoint = auditInfo.getField(AuditInfo.IMPAX_ENDPOINT);
        String callingHost = auditInfo.getField(AuditInfo.CALLING_HOST);
        ActiveParticipantBuilder requester = new ActiveParticipantBuilder(
                callingUserID, callingHost != null ? callingHost : getLocalHostName(auditLogger))
                .userIDTypeCode(callingHost != null
                        ? remoteUserIDTypeCode(archiveUserIDTypeCode, callingUserID)
                        : AuditMessages.UserIDTypeCode.DeviceName)
                .isRequester();
        if (impaxEndpoint != null) {
            activeParticipants[1] = requester.build();
            activeParticipants[2] = new ActiveParticipantBuilder(
                    impaxEndpoint, impaxEndpointHost(impaxEndpoint))
                    .userIDTypeCode(userIDTypeCode(impaxEndpoint))
                    .roleIDCode(eventType.source)
                    .build();
        } else
            activeParticipants[1] = requester.roleIDCode(eventType.source).build();
        return activeParticipants;
    }

    void spoolRetrieve(AuditUtils.EventType eventType, RetrieveContext ctx) {
        if (ctx.getMatches().isEmpty() && ctx.getCStoreForwards().isEmpty()
                && (ctx.failed() == 0 || ctx.getFailedMatches().isEmpty())) {
            LOG.info("Neither matches nor C-Store Forwards nor failed matches present. Exit spooling retrieve event {}",
                    eventType);
            return;
        }

        try {
            RetrieveAuditService retrieveAuditService = new RetrieveAuditService(ctx, getArchiveDevice());
            if (ctx.failed() > 0) {
                Collection<InstanceLocations> failedRetrieves = retrieveAuditService.failedMatches();
                if (!failedRetrieves.isEmpty())
                    writeSpoolFile(eventType, null,
                            retrieveAuditService.createRetrieveFailureAuditInfo(failedRetrieves)
                                    .toArray(new AuditInfoBuilder[0]));
            }
            Collection<InstanceLocations> completedMatches = retrieveAuditService.completedMatches();
            if (!completedMatches.isEmpty())
                writeSpoolFile(eventType, null,
                        retrieveAuditService.createRetrieveSuccessAuditInfo(completedMatches)
                                .toArray(new AuditInfoBuilder[0]));
        } catch (Exception e) {
            LOG.info("Failed to spool Retrieve of [StudyIUID={}]\n", ctx.getStudyInstanceUID(), e);
        }
    }

    private ArchiveDeviceExtension getArchiveDevice() {
        return device.getDeviceExtension(ArchiveDeviceExtension.class);
    }

    private void auditRetrieve(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) throws Exception {
        emitAuditMessage(
                RetrieveAuditService.auditMsg(auditLogger, path, eventType),
                auditLogger);
    }

    void spoolHL7Message(HL7ConnectionEvent hl7ConnEvent) {
        if (hl7ConnEvent.getHL7ResponseMessage() == null)
            return;

        HL7ConnectionEvent.Type type = hl7ConnEvent.getType();
        if (type == HL7ConnectionEvent.Type.MESSAGE_PROCESSED)
            spoolIncomingHL7Msg(hl7ConnEvent);
        if (type == HL7ConnectionEvent.Type.MESSAGE_RESPONSE)
            spoolOutgoingHL7Msg(hl7ConnEvent);
    }

    private void spoolIncomingHL7Msg(HL7ConnectionEvent hl7ConnEvent) {
        UnparsedHL7Message hl7Message = hl7ConnEvent.getHL7Message();
        HL7Segment pid = HL7AuditUtils.getHL7Segment(hl7Message, "PID");
        if (pid == null) {
            LOG.info("Exit spooling incoming HL7 message for message type {} as there is no PID segment.",
                    hl7Message.msh().getMessageType());
            return;
        }
        try {
            UnparsedHL7Message hl7ResponseMessage = hl7ConnEvent.getHL7ResponseMessage();
            if (HL7AuditUtils.isOrderMessage(hl7ConnEvent)) {
                ProcedureRecordAuditService procedureRecordAuditService
                        = new ProcedureRecordAuditService(hl7ConnEvent, getArchiveDevice());
                writeSpoolFile(
                        procedureRecordAuditService.getHL7IncomingOrderInfo(),
                        AuditUtils.EventType.forHL7IncomingOrderMsg(hl7ResponseMessage),
                        hl7ConnEvent);
                if (!procedureRecordAuditService.hasPIDSegment()) {
                    LOG.info("Missing PID segment. Abort patient audit of incoming HL7 order message.");
                    return;
                }
            }

            PatientRecordAuditService patRecAuditService = new PatientRecordAuditService(hl7ConnEvent, getArchiveDevice());
            AuditUtils.EventType eventType = AuditUtils.EventType.forHL7IncomingPatRec(hl7ResponseMessage);
            writeSpoolFile(
                    patRecAuditService.getHL7IncomingPatInfo(),
                    eventType,
                    hl7ConnEvent);

            HL7Segment mrg = HL7AuditUtils.getHL7Segment(hl7Message, "MRG");
            if (mrg != null && eventType != AuditUtils.EventType.PAT___READ) //spool below only for successful changePID or merge
                writeSpoolFile(
                        patRecAuditService.getHL7IncomingPrevPatInfo(mrg),
                        AuditUtils.EventType.PAT_DELETE,
                        hl7ConnEvent);
        } catch (Exception e) {
            LOG.info("Failed to spool HL7 Incoming for [Message={}]\n", hl7Message, e);
        }

    }

    private void spoolOutgoingHL7Msg(HL7ConnectionEvent hl7ConnEvent) {
        try {
            PatientRecordAuditService patRecAuditService = new PatientRecordAuditService(hl7ConnEvent, getArchiveDevice());
            HL7Segment pid = HL7AuditUtils.getHL7Segment(hl7ConnEvent.getHL7Message(), "PID");
            if (pid != null) {
                writeSpoolFile(
                        patRecAuditService.getHL7OutgoingPatInfo(pid),
                        AuditUtils.EventType.forHL7OutgoingPatRec(hl7ConnEvent.getHL7Message().msh().getMessageType()),
                        hl7ConnEvent);

                HL7Segment mrg = HL7AuditUtils.getHL7Segment(hl7ConnEvent.getHL7Message(), "MRG");
                if (mrg != null)
                    writeSpoolFile(
                            patRecAuditService.getHL7OutgoingPrevPatInfo(mrg),
                            AuditUtils.EventType.PAT_DELETE,
                            hl7ConnEvent);
            }

            if (HL7AuditUtils.isOrderMessage(hl7ConnEvent))
                spoolOutgoingHL7OrderMsg(hl7ConnEvent);

        } catch (Exception e) {
            LOG.info("Failed to spool HL7 Outgoing for [Message={}]\n", hl7ConnEvent.getHL7Message(), e);
        }
    }

    private void spoolOutgoingHL7OrderMsg(HL7ConnectionEvent hl7ConnEvent) {
        UnparsedHL7Message hl7Message = hl7ConnEvent.getHL7Message();
        Collection<HL7OrderSPSStatus> hl7OrderSPSStatuses = device.getDeviceExtension(HL7DeviceExtension.class)
                .getHL7Application(hl7Message.msh().getSendingApplicationWithFacility(), true)
                .getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class)
                .hl7OrderSPSStatuses();
        HL7Segment orc = HL7AuditUtils.getHL7Segment(hl7Message, "ORC");
        String orderCtrlStatus = orc.getField(1, null) + "_" + orc.getField(5, null);

        writeSpoolFile(
                new ProcedureRecordAuditService(hl7ConnEvent, getArchiveDevice()).getHL7OutgoingOrderInfo(),
                AuditUtils.EventType.forHL7OutgoingOrderMsg(orderCtrlStatus, hl7OrderSPSStatuses),
                hl7ConnEvent);
    }

    void spoolPatientRecord(PatientMgtContext ctx) {
        if (ctx.getUnparsedHL7Message() != null)
            return;

        try {
            PatientRecordAuditService patRecAuditService = new PatientRecordAuditService(ctx, getArchiveDevice());
            writeSpoolFile(AuditUtils.EventType.forPatRec(ctx), null, patRecAuditService.getPatAuditInfo());

            if (ctx.getPreviousAttributes() != null)
                writeSpoolFile(AuditUtils.EventType.PAT_DELETE, null, patRecAuditService.getPrevPatAuditInfo());
        } catch (Exception e) {
            LOG.info("Failed to spool Patient Record for [PatientID={}]\n", ctx.getPatientIDs(), e);
        }
    }

    private void auditPatientRecord(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType)
            throws Exception {
        emitAuditMessage(
                PatientRecordAuditService.auditMsg(auditLogger, path, eventType, hl7AppCache, aeCache),
                auditLogger);
    }

    void spoolProcedureRecord(ProcedureContext ctx) {
        if (ctx.getUnparsedHL7Message() != null && !ctx.getUnparsedHL7Message().msh().getMessageType().equals("ADT^A10"))
            return;

        try {
            writeSpoolFile(
                    AuditUtils.EventType.forProcedure(ctx.getEventActionCode()),
                    null,
                    new ProcedureRecordAuditService(ctx, getArchiveDevice()).getProcUpdateAuditInfo());
        } catch (Exception e) {
            LOG.info("Failed to spool Procedure Update procedure record for [Attributes={}, EventActionCode={}]\n",
                    ctx.getAttributes(), ctx.getEventActionCode(), e);
        }
    }

    void spoolStudyRecord(StudyMgtContext ctx) {
        try {
            writeSpoolFile(
                    AuditUtils.EventType.forStudy(ctx.getEventActionCode()),
                    null,
                    new StudyRecordAuditService(ctx, getArchiveDevice()).getStudyUpdateAuditInfo());
        } catch (Exception e) {
            LOG.info("Failed to spool Study Update procedure record for [StudyIUID={}, EventActionCode={}]\n",
                    ctx.getStudy(), ctx.getEventActionCode(), e);
        }
    }

    private void auditStudyRecord(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) throws Exception {
        if (eventType.eventActionCode.equals(AuditMessages.EventActionCode.Read)) {
            auditStudySize(auditLogger, path, eventType);
            return;
        }

        emitAuditMessage(
                StudyRecordAuditService.auditMsg(auditLogger, path, eventType),
                auditLogger);
    }

    private void auditProcedureRecord(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType)
            throws Exception {
        emitAuditMessage(
                ProcedureRecordAuditService.auditMsg(auditLogger, path, eventType, aeCache),
                auditLogger);
    }

    void spoolProvideAndRegister(ExportContext ctx) {
        try {
            writeSpoolFile(
                    AuditUtils.EventType.PROV_REGIS,
                    null,
                    ProvideAndRegisterAuditService.provideRegisterAuditInfo(ctx, getArchiveDevice()));
        } catch (Exception e) {
            LOG.info("Failed to spool Provide and Register for [SubmissionSetUID={}, XDSiManifest={}]\n",
                    ctx.getSubmissionSetUID(), ctx.getXDSiManifest(), e);
        }
    }

    private void auditProvideAndRegister(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType)
            throws Exception {
        emitAuditMessage(
                ProvideAndRegisterAuditService.provideRegisterAuditMsg(auditLogger, path, eventType),
                auditLogger);
    }

    void spoolStgCmt(StgCmtContext ctx) {
        try {
            Sequence success = ctx.getEventInfo().getSequence(Tag.ReferencedSOPSequence);
            Sequence failed = ctx.getEventInfo().getSequence(Tag.FailedSOPSequence);

            if (success != null && !success.isEmpty())
                writeSpoolFile(
                        AuditUtils.EventType.STG_COMMIT,
                        null,
                        StorageCommitAuditService.getSuccessAuditInfo(ctx, getArchiveDevice()));

            if (failed != null && !failed.isEmpty())
                writeSpoolFile(
                        AuditUtils.EventType.STG_COMMIT,
                        null,
                        StorageCommitAuditService.getFailedAuditInfo(ctx, getArchiveDevice()));
        } catch (Exception e) {
            LOG.info("Failed to spool storage commitment.\n", e);
        }
    }

    private void auditStorageCommit(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) throws Exception {
        emitAuditMessage(
                StorageCommitAuditService.auditMsg(auditLogger, path, eventType),
                auditLogger);
    }

    void spoolAssociationFailure(AssociationEvent associationEvent) {
        try {
            writeSpoolFile(
                    AuditUtils.EventType.ASSOC_FAIL,
                    null,
                    AssociationEventsAuditService.associationFailureAuditInfo(associationEvent));
        } catch (Exception e) {
            LOG.info("Failed to spool association event failure for [AssociationEventType={}]\n",
                    associationEvent.getType(), e);
        }
    }

    private void auditAssociationFailure(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType)
            throws Exception {
        emitAuditMessage(
                AssociationEventsAuditService.associationFailureAuditMsg(auditLogger, path, eventType),
                auditLogger);
    }

    private String getLocalHostName(AuditLogger log) {
        return log.getConnections().get(0).getHostname();
    }

    private Path toDirPath(AuditLogger auditLogger) throws UnsupportedEncodingException {
        return Paths.get(
                StringUtils.replaceSystemProperties(getArchiveDevice().getAuditSpoolDirectory()),
                URLEncoder.encode(auditLogger.getCommonName(), "UTF-8"));
    }

    private void writeSpoolFile(
            AuditInfoBuilder auditInfoBuilder, AuditUtils.EventType eventType, HL7ConnectionEvent hl7ConnEvent) {
        UnparsedHL7Message unparsedHL7Message = hl7ConnEvent.getHL7Message();
        HL7Segment msh = unparsedHL7Message.msh();
        int auditHL7MsgLimit;
        HL7Application hl7Application = device.getDeviceExtension(HL7DeviceExtension.class)
                                        .getHL7Application(hl7ConnEvent.getType() == HL7ConnectionEvent.Type.MESSAGE_PROCESSED
                                                        ? msh.getReceivingApplicationWithFacility()
                                                        : msh.getSendingApplicationWithFacility(),
                                                true);
         if (hl7Application == null) {
             LOG.info("No HL7 Application found for HL7ConnectionEvent.Type [name={}] - {}. Use auditHL7MsgLimit value from device.",
                     hl7ConnEvent.getType().name(), msh);
             auditHL7MsgLimit = device.getDeviceExtension(ArchiveDeviceExtension.class).getAuditHL7MsgLimit();
         } else auditHL7MsgLimit = hl7Application.getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class).auditHL7MsgLimit();

        writeSpoolFile(auditInfoBuilder,
                eventType,
                limitHL7MsgInAudit(hl7ConnEvent.getHL7Message(), auditHL7MsgLimit),
                limitHL7MsgInAudit(hl7ConnEvent.getHL7ResponseMessage(), auditHL7MsgLimit));
    }

    private byte[] limitHL7MsgInAudit(UnparsedHL7Message unparsedHL7Msg, int auditHL7MsgLimit) {
        byte[] data = unparsedHL7Msg.data();
        if (data.length <= auditHL7MsgLimit)
            return data;

        LOG.info("HL7 message [MessageHeader={}] length {} greater configured Audit HL7 Message Limit {} - truncate HL7 message in emitted audit",
                unparsedHL7Msg.msh(), data.length, auditHL7MsgLimit);
        byte[] truncatedHL7 = new byte[auditHL7MsgLimit];
        System.arraycopy(data, 0, truncatedHL7, 0, auditHL7MsgLimit - 3);
        System.arraycopy("...".getBytes(), 0, truncatedHL7, auditHL7MsgLimit - 3, 3);
        return truncatedHL7;
    }

    private void writeSpoolFile(
            AuditInfoBuilder auditInfoBuilder, AuditUtils.EventType eventType, byte[]... data) {
        if (auditInfoBuilder == null) {
            LOG.info("Attempt to write empty file by : {}", eventType);
            return;
        }
        FileTime eventTime = null;
        AuditLoggerDeviceExtension ext = device.getDeviceExtension(AuditLoggerDeviceExtension.class);
        for (AuditLogger auditLogger : ext.getAuditLoggers())
            if (auditLogger.isInstalled()) {
                try {
                    Path dir = toDirPath(auditLogger);
                    Files.createDirectories(dir);
                    Path file = Files.createTempFile(dir, eventType.name(), null);
                    try (BufferedOutputStream out = new BufferedOutputStream(
                            Files.newOutputStream(file, StandardOpenOption.APPEND))) {
                        try (SpoolFileWriter writer = new SpoolFileWriter(Files.newBufferedWriter(
                                file, StandardCharsets.UTF_8, StandardOpenOption.APPEND))) {
                            writer.writeLine(new AuditInfo(auditInfoBuilder));
                        }
                        out.write(data[0]);
                        if (data.length > 1 && data[1].length > 0)
                            out.write(data[1]);
                    }
                    if (eventTime == null)
                        eventTime = Files.getLastModifiedTime(file);
                    else
                        Files.setLastModifiedTime(file, eventTime);
                    if (!getArchiveDevice().isAuditAggregate())
                        auditAndProcessFile(auditLogger, file);
                } catch (Exception e) {
                    LOG.info("Failed to write audit spool file for [AuditEventType={}] at [AuditLogger={}]\n",
                            eventType, auditLogger.getCommonName(), e);
                }
            }
    }

    void writeSpoolFile(AuditUtils.EventType eventType, String suffix, AuditInfoBuilder... auditInfoBuilders) {
        String file = suffix != null ? eventType.name().concat(suffix) : eventType.name();
        if (auditInfoBuilders == null) {
            LOG.info("Attempt to write empty file : " + file);
            return;
        }
        FileTime eventTime = null;
        AuditLoggerDeviceExtension ext = device.getDeviceExtension(AuditLoggerDeviceExtension.class);
        for (AuditLogger auditLogger : ext.getAuditLoggers()) {
            if (!auditLogger.isInstalled())
                continue;

            try {
                Path dir = toDirPath(auditLogger);
                Files.createDirectories(dir);
                Path filePath = eventType.eventClass == AuditUtils.EventClass.STORE_WADOR
                        || (suffix != null && eventType.eventClass == AuditUtils.EventClass.USER_DELETED)
                        ? filePath(file, dir, auditInfoBuilders)
                        : filePath(eventType, dir, auditInfoBuilders);
                if (eventTime == null)
                    eventTime = Files.getLastModifiedTime(filePath);
                else
                    Files.setLastModifiedTime(filePath, eventTime);
                if (!getArchiveDevice().isAuditAggregate())
                    auditAndProcessFile(auditLogger, filePath);
            } catch (Exception e) {
                LOG.info("Failed to write [AuditSpoolFile={}] at [AuditLogger={}]\n",
                        file, auditLogger.getCommonName(), e);
            }
        }
    }

    private Path filePath(AuditUtils.EventType eventType, Path dir, AuditInfoBuilder... auditInfoBuilders)
            throws IOException {
        Path file = Files.createTempFile(dir, eventType.name(), null);
        try (SpoolFileWriter writer = new SpoolFileWriter(
                Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.APPEND))) {
            for (AuditInfoBuilder auditInfoBuilder : auditInfoBuilders)
                writer.writeLine(new AuditInfo(auditInfoBuilder));
        }
        return file;
    }

    private Path filePath(String fileName, Path dir, AuditInfoBuilder... auditInfoBuilders) throws IOException {
        Path file = dir.resolve(fileName);
        boolean append = Files.exists(file);
        try (SpoolFileWriter writer = new SpoolFileWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                append ? StandardOpenOption.APPEND : StandardOpenOption.CREATE_NEW))) {
            if (!append)
                writer.writeLine(new AuditInfo(auditInfoBuilders[0]));

            writer.writeLine(new AuditInfo(auditInfoBuilders[1]));
        }
        return file;
    }

    private void emitAuditMessage(AuditMessage msg, AuditLogger logger) throws Exception {
        msg.getAuditSourceIdentification().add(logger.createAuditSourceIdentification());
        try {
            logger.write(logger.timeStamp(), msg);
        } catch (Exception e) {
            LOG.info("Failed to emit audit message for [AuditLogger={}]\n", logger.getCommonName(), e);
            throw e;
        }
    }

    static void emitAuditMessage(
            AuditLogger auditLogger, EventIdentification eventIdentification, List<ActiveParticipant> activeParticipants,
            ParticipantObjectIdentification... participantObjectIdentifications) {
        AuditMessage msg = AuditMessages.createMessage(eventIdentification, activeParticipants, participantObjectIdentifications);
        msg.getAuditSourceIdentification().add(auditLogger.createAuditSourceIdentification());
        try {
            auditLogger.write(auditLogger.timeStamp(), msg);
        } catch (Exception e) {
            LOG.info("Failed to emit audit message for [AuditLogger={}]\n", auditLogger.getCommonName(), e);
        }
    }

    private AuditMessages.UserIDTypeCode userIDTypeCode(String userID) {
        return userID.indexOf('/') != -1
                ? AuditMessages.UserIDTypeCode.URI
                : userID.indexOf('|') != -1
                ? AuditMessages.UserIDTypeCode.ApplicationFacility
                : userID.equals(device.getDeviceName())
                ? AuditMessages.UserIDTypeCode.DeviceName
                : AuditMessages.UserIDTypeCode.StationAETitle;
    }

    static AuditMessages.UserIDTypeCode remoteUserIDTypeCode(
            AuditMessages.UserIDTypeCode archiveUserIDTypeCode, String remoteUserID) {
        if (remoteUserID != null)
            return remoteUserID.indexOf('|') != -1
                    ? AuditMessages.UserIDTypeCode.ApplicationFacility
                    : archiveUserIDTypeCode == AuditMessages.UserIDTypeCode.URI
                    ? AuditMessages.userIDTypeCode(remoteUserID)
                    : AuditMessages.UserIDTypeCode.StationAETitle;

        LOG.info("Remote user ID was not set during spooling.");
        return null;
    }

    static Calendar getEventTime(Path path, AuditLogger auditLogger){
        Calendar eventTime = auditLogger.timeStamp();
        try {
            eventTime.setTimeInMillis(Files.getLastModifiedTime(path).toMillis());
        } catch (Exception e) {
            LOG.info("Failed to get Last Modified Time of [AuditSpoolFile={}] of [AuditLogger={}]\n",
                    path, auditLogger.getCommonName(), e);
        }
        return eventTime;
    }

    void spoolQStarVerification(QStarVerification qStarVerification) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        try {
            String fileName = AuditUtils.EventType.QSTAR_VERI.name()
                                + "-" + qStarVerification.status
                                + "-" + qStarVerification.seriesPk;
            Set<AuditInfo> auditInfos = new LinkedHashSet<>();
            AuditInfo qStar = new AuditInfoBuilder.Builder()
                                    .callingUserID(device.getDeviceName())
                                    .calledUserID(qStarVerification.filePath)
                                    .outcome(QStarVerificationAuditService.QStarAccessStateEventOutcome.fromQStarVerification(qStarVerification)
                                            .getDescription())
                                    .studyIUID(qStarVerification.studyInstanceUID)
                                    .unknownPID(arcDev)
                                    .toAuditInfo();
            auditInfos.add(qStar);
            qStarVerification.sopRefs.forEach(sopRef ->
                auditInfos.add(new AuditInfoBuilder.Builder()
                                    .sopCUID(sopRef.sopClassUID)
                                    .sopIUID(sopRef.sopInstanceUID)
                                    .toAuditInfo()));
            writeSpoolFile(fileName, true, auditInfos.toArray(new AuditInfo[0]));
        } catch (Exception e) {
            LOG.info("Failed to spool {}", qStarVerification);
        }
    }

    void writeSpoolFile(String fileName, boolean aggregate, AuditInfo... auditInfos) {
        if (auditInfos == null) {
            LOG.info("Attempt to write empty file : " + fileName);
            return;
        }

        FileTime eventTime = null;
        AuditLoggerDeviceExtension ext = device.getDeviceExtension(AuditLoggerDeviceExtension.class);
        for (AuditLogger auditLogger : ext.getAuditLoggers()) {
            if (!auditLogger.isInstalled())
                continue;

            try {
                Path dir = toDirPath(auditLogger);
                Files.createDirectories(dir);
                Path filePath = aggregate ? dir.resolve(fileName) : Files.createTempFile(dir, fileName, null);
                boolean append = Files.exists(filePath);
                try (SpoolFileWriter writer = new SpoolFileWriter(Files.newBufferedWriter(
                        filePath, StandardCharsets.UTF_8, append
                                                            ? StandardOpenOption.APPEND
                                                            : StandardOpenOption.CREATE_NEW))) {
                    if (!append || !aggregate)
                        writer.writeLine(auditInfos[0]);

                    for (int i = 1; i < auditInfos.length; i++)
                        writer.writeLine(auditInfos[i]);
                }

                if (eventTime == null)
                    eventTime = Files.getLastModifiedTime(filePath);
                else
                    Files.setLastModifiedTime(filePath, eventTime);

                if (!getArchiveDevice().isAuditAggregate())
                    auditAndProcessFile(auditLogger, filePath);
            } catch (Exception e) {
                LOG.info("Failed to write [AuditSpoolFile={}] at [AuditLogger={}]\n",
                        fileName, auditLogger.getCommonName(), e);
            }
        }
    }

}