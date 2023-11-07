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
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.AssociationEvent;
import org.dcm4chee.arc.ConnectionEvent;
import org.dcm4chee.arc.HL7ConnectionEvent;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.ArchiveHL7ApplicationExtension;
import org.dcm4chee.arc.conf.HL7OrderSPSStatus;
import org.dcm4chee.arc.conf.RejectionNote;
import org.dcm4chee.arc.delete.StudyDeleteContext;
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
                auditApplicationActivity(auditLogger, path, eventType);
                break;
            case CONN_FAILURE:
                auditConnectionFailure(auditLogger, path, eventType);
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
                auditQueueMessageEvent(auditLogger, path, eventType);
                break;
            case IMPAX:
                auditPatientMismatch(auditLogger, path, eventType);
                break;
            case ASSOCIATION_FAILURE:
                auditAssociationFailure(auditLogger, path, eventType);
                break;
            case QSTAR:
                QStarVerificationAuditService.auditQStarVerification(auditLogger, path, eventType, getArchiveDevice());
                break;
        }
    }

    void spoolApplicationActivity(ArchiveServiceEvent event) {
        try {
            writeSpoolFile(AuditUtils.EventType.forApplicationActivity(event), null,
                    ApplicationActivityAuditService.auditInfo(event, device.getDeviceName()));
        } catch (Exception e) {
            LOG.info("Failed to spool Application Activity [EventType={}]\n", event.getType(), e);
        }
    }

    private void auditApplicationActivity(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType)
            throws Exception {
        emitAuditMessage(
                ApplicationActivityAuditService.auditMsg(auditLogger, path, eventType),
                auditLogger);
    }

    private void spoolInstancesDeleted(StoreContext ctx, String suffix) {
        AuditUtils.EventType eventType = AuditUtils.EventType.forInstancesDeleted(ctx);
        try {
            writeSpoolFile(eventType,suffix,
                    DeletionAuditService.instancesDeletedAuditInfo(ctx, getArchiveDevice()));
        } catch (Exception e) {
            LOG.info("Failed to spool Instances Deleted [AuditEventType={}]\n", eventType, e);
        }
    }

    void spoolStudyDeleted(StudyDeleteContext ctx) {
        AuditUtils.EventType eventType = AuditUtils.EventType.forStudyDeleted(ctx);
        try {
            writeSpoolFile(eventType, null,
                    DeletionAuditService.studyDeletedAuditInfo(ctx, getArchiveDevice()));
        } catch (Exception e) {
            LOG.info("Failed to spool Study Deleted for [StudyIUID={}, AuditEventType={}]\n",
                    ctx.getStudy().getStudyInstanceUID(), eventType, e);
        }
    }

    void spoolExternalRejection(RejectionNoteSent rejectionNoteSent) {
        AuditUtils.EventType eventType = AuditUtils.EventType.forExternalRejection(rejectionNoteSent);
        try {
            writeSpoolFile(eventType,null,
                    DeletionAuditService.externalRejectionAuditInfo(rejectionNoteSent, getArchiveDevice()));
        } catch (Exception e) {
            LOG.info("Failed to spool External Rejection [AuditEventType={}]\n", eventType, e);
        }
    }

    private void auditDeletion(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) throws Exception {
        emitAuditMessage(
                DeletionAuditService.auditMsg(auditLogger, path, eventType),
                auditLogger);
    }

    void spoolTaskEvent(TaskEvent taskEvent) {
        if (taskEvent.getTask() == null)
            return;

        String callingUser = KeycloakContext.valueOf(taskEvent.getRequest()).getUserName();
        try {
            writeSpoolFile(
                    AuditUtils.EventType.forQueueEvent(taskEvent.getOperation()),
                    null,
                    TaskAuditService.queueMsgAuditInfo(taskEvent));
        } catch (Exception e) {
            LOG.info("Failed to spool Task Event for [Operation={}] of [TaskID={}] "
                            + "triggered by [User={}]\n",
                    taskEvent.getOperation(), taskEvent.getTask().getPk(), callingUser, e);
        }
    }

    void spoolBulkQueueMessageEvent(BulkTaskEvent bulkQueueMsgEvent) {
        HttpServletRequest request = bulkQueueMsgEvent.getRequest();
        String callingUser = request != null ? KeycloakContext.valueOf(request).getUserName() : device.getDeviceName();
        try {
            writeSpoolFile(
                    AuditUtils.EventType.forQueueEvent(bulkQueueMsgEvent.getOperation()),
                    null,
                    TaskAuditService.bulkQueueMsgAuditInfo(bulkQueueMsgEvent, callingUser));
        } catch (Exception e) {
            LOG.info("Failed to spool Bulk Queue Message Event for [QueueOperation={}] triggered by [User={}]\n",
                    bulkQueueMsgEvent.getOperation(), callingUser, e);
        }
    }

    private void auditQueueMessageEvent(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType)
            throws Exception {
        emitAuditMessage(
                TaskAuditService.auditMsg(auditLogger, path, eventType),
                auditLogger);
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
                writeSpoolFile(PDQAuditService.auditInfoFHIR(ctx, getArchiveDevice()),
                        AuditUtils.EventType.FHIR___PDQ);
        } catch (Exception e) {
            LOG.info("Failed to spool PDQ for {}", ctx);
        }
    }

    void spoolQuery(QueryContext ctx) {
        if (ctx.getAssociation() == null && ctx.getHttpRequest() == null)
            return;

        try {
            AuditUtils.EventType eventType = AuditUtils.EventType.QUERY__EVT;
            AuditInfo auditInfo = new AuditInfo(QueryAuditService.auditInfo(ctx));
            FileTime eventTime = null;
            for (AuditLogger auditLogger : auditLoggers(ctx, eventType)) {
                Path directory = toDirPath(auditLogger);
                try {
                    Files.createDirectories(directory);
                    Path file = Files.createTempFile(directory, eventType.name(), null);
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
            LOG.info("Failed to spool Query.\n", e);
        }
    }

    private List<AuditLogger> auditLoggers(QueryContext ctx, AuditUtils.EventType eventType) {
        AuditLoggerDeviceExtension ext = device.getDeviceExtension(AuditLoggerDeviceExtension.class);
        if (ext == null)
            return Collections.emptyList();

        return ext.getAuditLoggers().stream()
                .filter(auditLogger -> auditLogger.isInstalled()
                                        && !auditLogger.isAuditMessageSuppressed(
                                                createMinimalAuditMsg(eventType, ctx.getCallingAET())))
                .collect(Collectors.toList());
    }

    private AuditMessage createMinimalAuditMsg(AuditUtils.EventType eventType, String userID) {
        AuditMessage msg = new AuditMessage();
        msg.setEventIdentification(EventID.toEventIdentification(eventType));
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
        AuditMessage msg = eventType.eventTypeCode == null
                            ? QueryAuditService.auditMsg(auditLogger, path, eventType)
                            : PDQAuditService.auditMsg(auditLogger, path, eventType, hl7AppCache, webAppCache);
        emitAuditMessage(msg, auditLogger);
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
                spoolInstancesDeleted(ctx, null);
                return;
            }

            if (isDuplicateReceivedInstance(ctx)) {
                if (rejectionNote != null && rejectionNote.isRevokeRejection())
                    spoolInstancesStored(ctx);
                return;
            }

            if (ctx.getAttributes() == null) {
                LOG.info("Instances stored is not audited as store context attributes are not set. "
                        + (ctx.getException() != null ? ctx.getException().getMessage() : null));
                return;
            }

            spoolInstancesStored(ctx);
        } catch (Exception e) {
            LOG.info("Failed to spool Store Event.\n", e);
        }
    }

    private void spoolInstancesStored(StoreContext ctx) {
        StoreSession ss = ctx.getStoreSession();
        HttpServletRequestInfo httpServletRequestInfo = ss.getHttpRequest();
        String callingUserID = httpServletRequestInfo != null
                ? httpServletRequestInfo.requesterUserID
                : ss.getCallingAET() != null
                ? ss.getCallingAET() : device.getDeviceName();
        String calledUserID = httpServletRequestInfo != null
                                ? requestURLWithQueryParams(httpServletRequestInfo)
                                : ss.getCalledAET();
        try {
            String outcome = ctx.getException() != null
                    ? ctx.getRejectionNote() != null
                    ? ctx.getRejectionNote().getRejectionNoteCode().getCodeMeaning() + '-' + ctx.getException().getMessage()
                    : ctx.getException().getMessage()
                    : null;

            AuditInfoBuilder instanceInfo = new AuditInfoBuilder.Builder()
                    .sopCUID(ctx.getSopClassUID()).sopIUID(ctx.getSopInstanceUID())
                    .mppsUID(ctx.getMppsInstanceUID())
                    .outcome(outcome)
                    .errorCode(ctx.getException() instanceof DicomServiceException
                            ? ((DicomServiceException) ctx.getException()).getStatus() : 0)
                    .build();

            ArchiveDeviceExtension arcDev = getArchiveDevice();
            Attributes attr = ctx.getAttributes();
            AuditInfoBuilder.Builder infoBuilder = new AuditInfoBuilder.Builder()
                    .callingHost(ss.getRemoteHostName())
                    .callingUserID(callingUserID)
                    .calledUserID(calledUserID)
                    .impaxEndpoint(ss.getImpaxReportEndpoint())
                    .studyUIDAccNumDate(attr, arcDev)
                    .pIDAndName(attr, arcDev);
            AuditInfoBuilder info = infoBuilder
                    .warning(ctx.getRejectionNote() != null
                            ? ctx.getRejectionNote().getRejectionNoteCode().getCodeMeaning() : null)
                    .build();

            String suffix = '-' + callingUserID.replace('|', '-')
                    + '-' + ctx.getStoreSession().getCalledAET()
                    + '-' + ctx.getStudyInstanceUID();
            suffix = outcome != null ? suffix.concat("_ERROR") : suffix;
            writeSpoolFile(AuditUtils.EventType.forInstanceStored(ctx), suffix, info, instanceInfo);
            if (ctx.getPreviousInstance() != null
                    && ctx.getPreviousInstance().getSopInstanceUID().equals(ctx.getStoredInstance().getSopInstanceUID()))
                spoolInstancesDeleted(ctx, suffix);
            if (ctx.getImpaxReportPatientMismatch() != null) {
                AuditInfoBuilder patMismatchInfo = infoBuilder
                        .patMismatchCode(ctx.getImpaxReportPatientMismatch().toString())
                        .build();
                writeSpoolFile(AuditUtils.EventType.IMPAX_MISM, null, patMismatchInfo, instanceInfo);
            }
        } catch (Exception e) {
            LOG.info("Failed to spool Instances Stored for [StudyIUID={}] triggered by [CallingUser={}]\n",
                    ctx.getStudyInstanceUID(), callingUserID, e);
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

    private void auditStoreError(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) throws Exception {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());

        InstanceInfo instanceInfo = new InstanceInfo();
        HashSet<String> outcome = new HashSet<>();
        HashSet<AuditMessages.EventTypeCode> errorCode = new HashSet<>();

        instanceInfo.addAcc(auditInfo);
        reader.getInstanceLines().forEach(line -> {
            AuditInfo info = new AuditInfo(line);
            outcome.add(info.getField(AuditInfo.OUTCOME));
            AuditMessages.EventTypeCode errorEventTypeCode = AuditUtils.errorEventTypeCode(info.getField(AuditInfo.ERROR_CODE));
            if (errorEventTypeCode != null)
                errorCode.add(errorEventTypeCode);

            instanceInfo.addSOPInstance(info);
            instanceInfo.addMpps(info);
        });

        AuditMessage auditMsg = AuditMessages.createMessage(
                EventID.toEventIdentification(auditLogger, path, eventType, outcome, errorCode),
                storeWadoURIActiveParticipants(auditLogger, auditInfo, eventType),
                ParticipantObjectID.studyPatParticipants(auditInfo, instanceInfo));
        emitAuditMessage(auditMsg, auditLogger);
    }

    private void auditStoreOrWADORetrieve(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType)
            throws Exception {
        if (path.toFile().getName().endsWith("_ERROR")) {
            auditStoreError(auditLogger, path, eventType);
            return;
        }

        if (eventType.name().startsWith("WADO")) {
            auditWADORetrieve(auditLogger, path, eventType);
            return;
        }

        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        AuditMessage auditMsg = AuditMessages.createMessage(
                EventID.toEventIdentification(auditLogger, path, eventType, auditInfo),
                storeWadoURIActiveParticipants(auditLogger, auditInfo, eventType),
                ParticipantObjectID.studyPatParticipants(auditInfo, reader.getInstanceLines(), eventType, auditLogger));
        emitAuditMessage(auditMsg, auditLogger);
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
            if (patRecAuditService.isArchiveHL7MsgAndNotOrder()) {
                writeSpoolFile(
                        patRecAuditService.getHL7OutgoingPatInfo(),
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
        writeSpoolFile(auditInfoBuilder,
                eventType,
                limitHL7MsgInAudit(hl7ConnEvent.getHL7Message(), hl7ConnEvent.getType()),
                limitHL7MsgInAudit(hl7ConnEvent.getHL7ResponseMessage(), hl7ConnEvent.getType()));
    }

    private byte[] limitHL7MsgInAudit(UnparsedHL7Message unparsedHL7Msg, HL7ConnectionEvent.Type type) {
        HL7Segment msh = unparsedHL7Msg.msh();
        HL7Application hl7App = device.getDeviceExtensionNotNull(HL7DeviceExtension.class)
                .getHL7Application(type == HL7ConnectionEvent.Type.MESSAGE_PROCESSED
                                    ? msh.getReceivingApplicationWithFacility()
                                    : msh.getSendingApplicationWithFacility(),
                        true);
        ArchiveHL7ApplicationExtension arcHL7App;
        int auditHL7MsgLimit = hl7App == null
                                || ((arcHL7App = hl7App.getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class)) == null)
                                    ? device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class).getAuditHL7MsgLimit()
                                    : arcHL7App.auditHL7MsgLimit();
        return truncateHL7(unparsedHL7Msg, auditHL7MsgLimit);
    }

    private byte[] truncateHL7(UnparsedHL7Message unparsedHL7Msg, int auditHL7MsgLimit) {
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
            AuditInfo qStar = new AuditInfo(new AuditInfoBuilder.Builder()
                                            .callingUserID(device.getDeviceName())
                                            .calledUserID(qStarVerification.filePath)
                                            .outcome(QStarVerificationAuditService.QStarAccessStateEventOutcome.fromQStarVerification(qStarVerification)
                                                    .getDescription())
                                            .studyIUID(qStarVerification.studyInstanceUID)
                                            .unknownPID(arcDev)
                                            .build());
            auditInfos.add(qStar);
            qStarVerification.sopRefs.forEach(sopRef ->
                auditInfos.add(new AuditInfo(new AuditInfoBuilder.Builder()
                                                .sopCUID(sopRef.sopClassUID)
                                                .sopIUID(sopRef.sopInstanceUID)
                                                .build())));
            writeSpoolFile(fileName, auditInfos.toArray(new AuditInfo[0]));
        } catch (Exception e) {
            LOG.info("Failed to spool {}", qStarVerification);
        }
    }

    void writeSpoolFile(String fileName, AuditInfo... auditInfos) {
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
                Path filePath = dir.resolve(fileName);
                boolean append = Files.exists(filePath);
                try (SpoolFileWriter writer = new SpoolFileWriter(Files.newBufferedWriter(
                        filePath, StandardCharsets.UTF_8, append
                                                            ? StandardOpenOption.APPEND
                                                            : StandardOpenOption.CREATE_NEW))) {
                    if (!append)
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