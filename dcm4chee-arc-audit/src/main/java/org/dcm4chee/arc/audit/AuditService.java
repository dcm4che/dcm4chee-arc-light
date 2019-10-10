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
import org.dcm4che3.conf.api.hl7.IHL7ApplicationCache;
import org.dcm4che3.data.*;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.net.*;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4che3.net.audit.AuditLoggerDeviceExtension;
import org.dcm4che3.net.hl7.HL7DeviceExtension;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.AssociationEvent;
import org.dcm4chee.arc.HL7ConnectionEvent;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.event.ArchiveServiceEvent;
import org.dcm4chee.arc.ConnectionEvent;
import org.dcm4chee.arc.event.BulkQueueMessageEvent;
import org.dcm4chee.arc.event.QueueMessageEvent;
import org.dcm4chee.arc.event.SoftwareConfiguration;
import org.dcm4chee.arc.keycloak.KeycloakContext;
import org.dcm4chee.arc.delete.StudyDeleteContext;
import org.dcm4chee.arc.retrieve.ExternalRetrieveContext;
import org.dcm4chee.arc.event.RejectionNoteSent;
import org.dcm4chee.arc.exporter.ExportContext;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.procedure.ProcedureContext;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.stgcmt.StgCmtContext;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreSession;
import org.dcm4chee.arc.study.StudyMgtContext;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
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

    private void aggregateAuditMessage(AuditLogger auditLogger, Path path) throws Exception {
        AuditUtils.EventType eventType = AuditUtils.EventType.fromFile(path);
        if (path.toFile().length() == 0) {
            LOG.warn("Attempt to read from an empty file {} by {}.", path, eventType);
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
        }
    }

    void spoolApplicationActivity(ArchiveServiceEvent event) {
        try {
            writeSpoolFile(AuditUtils.EventType.forApplicationActivity(event), null,
                    ApplicationActivityAuditService.auditInfo(event, device.getDeviceName()));
        } catch (Exception e) {
            LOG.warn("Failed to spool Application Activity [EventType={}]\n", event.getType(), e);
        }
    }

    private void auditApplicationActivity(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
        emitAuditMessage(
                ApplicationActivityAuditService.auditMsg(auditLogger, path, eventType),
                auditLogger);
    }

    private void spoolInstancesDeleted(StoreContext ctx) {
        AuditUtils.EventType eventType = AuditUtils.EventType.forInstancesDeleted(ctx);
        try {
            writeSpoolFile(eventType,null,
                    DeletionAuditService.instancesDeletedAuditInfo(ctx, getArchiveDevice()));
        } catch (Exception e) {
            LOG.warn("Failed to spool Instances Deleted [AuditEventType={}]\n", eventType, e);
        }
    }

    void spoolStudyDeleted(StudyDeleteContext ctx) {
        AuditUtils.EventType eventType = AuditUtils.EventType.forStudyDeleted(ctx);
        try {
            writeSpoolFile(eventType, null,
                    DeletionAuditService.studyDeletedAuditInfo(ctx, getArchiveDevice()));
        } catch (Exception e) {
            LOG.warn("Failed to spool Study Deleted for [StudyIUID={}, AuditEventType={}]\n",
                    ctx.getStudy().getStudyInstanceUID(), eventType, e);
        }
    }

    void spoolExternalRejection(RejectionNoteSent rejectionNoteSent) {
        AuditUtils.EventType eventType = AuditUtils.EventType.forExternalRejection(rejectionNoteSent);
        try {
            writeSpoolFile(eventType,null,
                    DeletionAuditService.externalRejectionAuditInfo(rejectionNoteSent, getArchiveDevice()));
        } catch (Exception e) {
            LOG.warn("Failed to spool External Rejection [AuditEventType={}]\n", eventType, e);
        }
    }

    private void auditDeletion(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
        emitAuditMessage(
                DeletionAuditService.auditMsg(auditLogger, path, eventType),
                auditLogger);
    }

    void spoolQueueMessageEvent(QueueMessageEvent queueMsgEvent) {
        if (queueMsgEvent.getQueueMsg() == null)
            return;

        String callingUser = KeycloakContext.valueOf(queueMsgEvent.getRequest()).getUserName();
        try {
            writeSpoolFile(
                    AuditUtils.EventType.forQueueEvent(queueMsgEvent.getOperation()),
                    null,
                    QueueMessageAuditService.queueMsgAuditInfo(queueMsgEvent));
        } catch (Exception e) {
            LOG.warn("Failed to spool Queue Message Event for [QueueOperation={}] of [QueueMsgID={}] "
                            + "triggered by [User={}]\n",
                    queueMsgEvent.getOperation(), queueMsgEvent.getQueueMsg().getMessageID(), callingUser, e);
        }
    }

    void spoolBulkQueueMessageEvent(BulkQueueMessageEvent bulkQueueMsgEvent) {
        HttpServletRequest request = bulkQueueMsgEvent.getRequest();
        String callingUser = KeycloakContext.valueOf(request).getUserName();
        try {
            writeSpoolFile(
                    AuditUtils.EventType.forQueueEvent(bulkQueueMsgEvent.getOperation()),
                    null,
                    QueueMessageAuditService.bulkQueueMsgAuditInfo(bulkQueueMsgEvent, callingUser));
        } catch (Exception e) {
            LOG.warn("Failed to spool Bulk Queue Message Event for [QueueOperation={}] of "
                            + "Request[url={}, queryString={}] triggered by [User={}]\n",
                    bulkQueueMsgEvent.getOperation(), request.getRequestURI(), request.getQueryString(), callingUser, e);
        }
    }

    private void auditQueueMessageEvent(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
        emitAuditMessage(
                QueueMessageAuditService.auditMsg(auditLogger, path, eventType),
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
            LOG.warn("Failed to spool Software Configuration Changes for [Device={}] done by [CallingUser={}]\n",
                    softwareConfiguration.getDeviceName(), callingUser, e);
        }
    }

    private void auditSoftwareConfiguration(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
        emitAuditMessage(
                SoftwareConfigurationAuditService.auditMsg(auditLogger, path, eventType),
                auditLogger);
    }

    void spoolExternalRetrieve(ExternalRetrieveContext ctx) {
        try {
            writeSpoolFile(AuditUtils.EventType.INST_RETRV, null,
                    ExternalRetrieveAuditService.auditInfo(ctx, getArchiveDevice()));
        } catch (Exception e) {
            LOG.warn("Failed to spool External Retrieve for [StudyIUID={}] triggered by [Requester={}]\n",
                    ctx.getStudyInstanceUID(), ctx.getRequesterUserID(), e);
        }
    }

    private void auditExternalRetrieve(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
        emitAuditMessage(
                ExternalRetrieveAuditService.auditMsg(auditLogger, path, eventType),
                auditLogger);
    }

    void spoolConnectionFailure(ConnectionEvent event) {
        try {
            writeSpoolFile(
                    AuditUtils.EventType.CONN_FAILR,
                    null,
                    ConnectionEventsAuditService.connFailureAuditInfo(event));
        } catch (Exception e) {
            LOG.warn("Failed to spool Connection Failure for [EventType={}]\n", event.getType(), e);
        }
    }

    private void auditConnectionFailure(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
        emitAuditMessage(
                ConnectionEventsAuditService.auditMsg(auditLogger, path, eventType),
                auditLogger);
    }

    void spoolQuery(QueryContext ctx) {
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
                                LOG.warn("Failed to create DicomOutputStream.\n", e);
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
                    LOG.warn("Failed to write to Query Audit Spool File at [AuditLogger={}]\n",
                            auditLogger.getCommonName(), e);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to spool Query.\n", e);
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
        msg.setEventIdentification(AuditMessages.toEventIdentification(EventID.toEventIdentification(eventType)));
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
            LOG.warn("Failed to process [AuditSpoolFile={}] of [AuditLogger={}].\n",
                    file, auditLogger.getCommonName(), e);
            try {
                Files.move(file, file.resolveSibling(file.getFileName().toString() + ".failed"));
            } catch (IOException e1) {
                LOG.warn("Failed to mark [AuditSpoolFile={}] of [AuditLogger={}] as failed.\n",
                        file, auditLogger.getCommonName(), e);
            }
        }
    }

    private void auditQuery(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) throws IOException {
        emitAuditMessage(
                QueryAuditService.auditMsg(auditLogger, path, eventType),
                auditLogger);
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
                spoolInstancesDeleted(ctx);
                return;
            }

            if (isDuplicateReceivedInstance(ctx)) {
                if (rejectionNote != null && rejectionNote.isRevokeRejection())
                    spoolInstancesStored(ctx);
                return;
            }

            if (ctx.getAttributes() == null) {
                LOG.warn("Instances stored is not audited as store context attributes are not set. "
                        + (ctx.getException() != null ? ctx.getException().getMessage() : null));
                return;
            }

            spoolInstancesStored(ctx);
        } catch (Exception e) {
            LOG.warn("Failed to spool Store Event.\n", e);
        }
    }

    private void spoolInstancesStored(StoreContext ctx) {
        StoreSession ss = ctx.getStoreSession();
        HttpServletRequestInfo req = ss.getHttpRequest();
        String callingUserID = req != null
                ? req.requesterUserID
                : ss.getCallingAET() != null
                ? ss.getCallingAET() : device.getDeviceName();
        String calledUserID = req != null ? req.requestURI : ss.getCalledAET();
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
            if (ctx.getImpaxReportPatientMismatch() != null) {
                AuditInfoBuilder patMismatchInfo = infoBuilder
                        .patMismatchCode(ctx.getImpaxReportPatientMismatch().toString())
                        .build();
                writeSpoolFile(AuditUtils.EventType.IMPAX_MISM, null, patMismatchInfo, instanceInfo);
            }
        } catch (Exception e) {
            LOG.warn("Failed to spool Instances Stored for [StudyIUID={}] triggered by [CallingUser={}]\n",
                    ctx.getStudyInstanceUID(), callingUserID, e);
        }
    }

    private void auditPatientMismatch(AuditLogger logger, Path path, AuditUtils.EventType eventType) {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        AuditMessage auditMsg = AuditMessages.createMessage(
                EventID.toEventIdentification(logger, path, eventType, auditInfo),
                patientMismatchActiveParticipants(logger, auditInfo),
                ParticipantObjectID.studyPatParticipants(auditInfo, reader.getInstanceLines(), eventType, logger));
        emitAuditMessage(auditMsg, logger);
    }


    private ActiveParticipantBuilder[] patientMismatchActiveParticipants(AuditLogger logger, AuditInfo auditInfo) {
        ActiveParticipantBuilder[] activeParticipants = new ActiveParticipantBuilder[3];
        String callingUserID = auditInfo.getField(AuditInfo.CALLING_USERID);
        String callingHost = auditInfo.getField(AuditInfo.CALLING_HOST);
        String calledUserID = auditInfo.getField(AuditInfo.CALLED_USERID);
        activeParticipants[0] = new ActiveParticipantBuilder.Builder(calledUserID, getLocalHostName(logger))
                .userIDTypeCode(userIDTypeCode(calledUserID)).build();
        activeParticipants[1] = new ActiveParticipantBuilder.Builder(
                callingUserID, callingHost != null ? callingHost : getLocalHostName(logger))
                .userIDTypeCode(callingHost != null
                        ? AuditMessages.userIDTypeCode(callingUserID) : AuditMessages.UserIDTypeCode.DeviceName)
                .isRequester().build();
        String impaxEndpoint = auditInfo.getField(AuditInfo.IMPAX_ENDPOINT);
        activeParticipants[2] = new ActiveParticipantBuilder.Builder(
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
        HttpServletRequestInfo req = ctx.getHttpServletRequestInfo();
        try {
            Attributes attrs = ctx.getMatches().get(0).getAttributes();
            String suffix = '-' + req.requesterHost
                    + '-' + ctx.getLocalAETitle()
                    + '-' + ctx.getStudyInstanceUIDs()[0];
            AuditInfoBuilder info = new AuditInfoBuilder.Builder()
                    .callingHost(req.requesterHost)
                    .callingUserID(req.requesterUserID)
                    .calledUserID(req.requestURI)
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
            LOG.warn("Failed to spool Wado Retrieve for [StudyIUID={}] triggered by [User={}]\n",
                    ctx.getStudyInstanceUID(), req.requesterUserID, e);
        }
    }

    private void auditStoreError(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
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

    private void auditStoreOrWADORetrieve(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
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

    private void auditWADORetrieve(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());

        AuditMessage auditMsg = AuditMessages.createMessage(
                EventID.toEventIdentification(auditLogger, path, eventType, auditInfo),
                storeWadoURIActiveParticipants(auditLogger, auditInfo, eventType),
                ParticipantObjectID.studyPatParticipants(auditInfo, reader.getInstanceLines(), eventType, auditLogger));
        emitAuditMessage(auditMsg, auditLogger);
    }

    private ActiveParticipantBuilder[] storeWadoURIActiveParticipants(
            AuditLogger auditLogger, AuditInfo auditInfo, AuditUtils.EventType eventType) {
        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[3];
        String callingUserID = auditInfo.getField(AuditInfo.CALLING_USERID);
        String archiveUserID = auditInfo.getField(AuditInfo.CALLED_USERID);
        AuditMessages.UserIDTypeCode archiveUserIDTypeCode = userIDTypeCode(archiveUserID);
        activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                archiveUserID,
                getLocalHostName(auditLogger))
                .userIDTypeCode(archiveUserIDTypeCode)
                .altUserID(AuditLogger.processID())
                .roleIDCode(eventType.destination)
                .build();
        String impaxEndpoint = auditInfo.getField(AuditInfo.IMPAX_ENDPOINT);
        String callingHost = auditInfo.getField(AuditInfo.CALLING_HOST);
        ActiveParticipantBuilder.Builder requester = new ActiveParticipantBuilder.Builder(
                callingUserID, callingHost != null ? callingHost : getLocalHostName(auditLogger))
                .userIDTypeCode(callingHost != null
                        ? remoteUserIDTypeCode(archiveUserIDTypeCode, callingUserID)
                        : AuditMessages.UserIDTypeCode.DeviceName)
                .isRequester();
        if (impaxEndpoint != null) {
            activeParticipantBuilder[1] = requester.build();
            activeParticipantBuilder[2] = new ActiveParticipantBuilder.Builder(
                    impaxEndpoint, impaxEndpointHost(impaxEndpoint))
                    .userIDTypeCode(userIDTypeCode(impaxEndpoint))
                    .roleIDCode(eventType.source)
                    .build();
        } else
            activeParticipantBuilder[1] = requester.roleIDCode(eventType.source).build();
        return activeParticipantBuilder;
    }

    void spoolRetrieve(AuditUtils.EventType eventType, RetrieveContext ctx) {
        if (ctx.getMatches().size() == 0 && ctx.getCStoreForwards().size() == 0) {
            LOG.info("Retrieve context has no matches and no C-Store Forwards. Exit spooling retrieve event.");
            return;
        }

        try {
            RetrieveAuditService retrieveAuditService = new RetrieveAuditService(ctx, getArchiveDevice());
            for (AuditInfoBuilder[] auditInfoBuilder : retrieveAuditService.getAuditInfoBuilder())
                writeSpoolFile(eventType, null, auditInfoBuilder);
        } catch (Exception e) {
            LOG.warn("Failed to spool Retrieve of [StudyIUID={}]\n", ctx.getStudyInstanceUID(), e);
        }
    }

    private ArchiveDeviceExtension getArchiveDevice() {
        return device.getDeviceExtension(ArchiveDeviceExtension.class);
    }

    private void auditRetrieve(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
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
        try {
            PatientRecordAuditService patRecAuditService = new PatientRecordAuditService(hl7ConnEvent, getArchiveDevice());
            UnparsedHL7Message hl7ResponseMessage = hl7ConnEvent.getHL7ResponseMessage();
            AuditUtils.EventType eventType = AuditUtils.EventType.forHL7IncomingPatRec(hl7ResponseMessage);
            writeSpoolFile(
                    patRecAuditService.getHL7IncomingPatInfo(),
                    eventType,
                    hl7ConnEvent);

            HL7Segment mrg = HL7AuditUtils.getHL7Segment(hl7ConnEvent.getHL7Message(), "MRG");
            if (mrg != null && eventType != AuditUtils.EventType.PAT___READ) //spool below only for successful changePID or merge
                writeSpoolFile(
                        patRecAuditService.getHL7IncomingPrevPatInfo(mrg),
                        AuditUtils.EventType.PAT_DELETE,
                        hl7ConnEvent);

            if (HL7AuditUtils.isOrderMessage(hl7ConnEvent))
                writeSpoolFile(
                        new ProcedureRecordAuditService(hl7ConnEvent, getArchiveDevice()).getHL7IncomingOrderInfo(),
                        AuditUtils.EventType.forHL7IncomingOrderMsg(hl7ResponseMessage),
                        hl7ConnEvent);
        } catch (Exception e) {
            LOG.warn("Failed to spool HL7 Incoming for [Message={}]\n", hl7ConnEvent.getHL7Message(), e);
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
            LOG.warn("Failed to spool HL7 Outgoing for [Message={}]\n", hl7ConnEvent.getHL7Message(), e);
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
            LOG.warn("Failed to spool Patient Record for [PatientID={}]\n", ctx.getPatientID(), e);
        }
    }

    private void auditPatientRecord(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType)
            throws Exception {
        emitAuditMessage(
                PatientRecordAuditService.auditMsg(auditLogger, path, eventType, hl7AppCache),
                auditLogger);
    }

    void spoolProcedureRecord(ProcedureContext ctx) {
        if (ctx.getUnparsedHL7Message() != null)
            return;

        try {
            writeSpoolFile(
                    AuditUtils.EventType.forProcedure(ctx.getEventActionCode()),
                    null,
                    new ProcedureRecordAuditService(ctx, getArchiveDevice()).getProcUpdateAuditInfo());
        } catch (Exception e) {
            LOG.warn("Failed to spool Procedure Update procedure record for [Attributes={}, EventActionCode={}]\n",
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
            LOG.warn("Failed to spool Study Update procedure record for [StudyIUID={}, EventActionCode={}]\n",
                    ctx.getStudy(), ctx.getEventActionCode(), e);
        }
    }

    private void auditStudyRecord(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
        emitAuditMessage(
                StudyRecordAuditService.auditMsg(auditLogger, path, eventType),
                auditLogger);
    }

    private void auditProcedureRecord(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
        emitAuditMessage(
                ProcedureRecordAuditService.auditMsg(auditLogger, path, eventType),
                auditLogger);
    }

    void spoolProvideAndRegister(ExportContext ctx) {
        try {
            writeSpoolFile(
                    AuditUtils.EventType.PROV_REGIS,
                    null,
                    ProvideAndRegisterAuditService.provideRegisterAuditInfo(ctx, getArchiveDevice()));
        } catch (Exception e) {
            LOG.warn("Failed to spool Provide and Register for [SubmissionSetUID={}, XDSiManifest={}]\n",
                    ctx.getSubmissionSetUID(), ctx.getXDSiManifest(), e);
        }
    }

    private void auditProvideAndRegister(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
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
            LOG.warn("Failed to spool storage commitment.\n", e);
        }
    }

    private void auditStorageCommit(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
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
            LOG.warn("Failed to spool association event failure for [AssociationEventType={}]\n",
                    associationEvent.getType(), e);
        }
    }

    private void auditAssociationFailure(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
        emitAuditMessage(
                AssociationEventsAuditService.associationFailureAuditMsg(auditLogger, path, eventType),
                auditLogger);
    }

    private String getLocalHostName(AuditLogger log) {
        return log.getConnections().get(0).getHostname();
    }

    private Path toDirPath(AuditLogger auditLogger) {
        return Paths.get(
                StringUtils.replaceSystemProperties(getArchiveDevice().getAuditSpoolDirectory()),
                auditLogger.getCommonName().replaceAll(" ", "_"));
    }

    private void writeSpoolFile(
            AuditInfoBuilder auditInfoBuilder, AuditUtils.EventType eventType, HL7ConnectionEvent hl7ConnEvent) {
        writeSpoolFile(auditInfoBuilder,
                eventType,
                hl7ConnEvent.getHL7Message().data(),
                hl7ConnEvent.getHL7ResponseMessage().data());
    }

    private void writeSpoolFile(
            AuditInfoBuilder auditInfoBuilder, AuditUtils.EventType eventType, byte[]... data) {
        if (auditInfoBuilder == null) {
            LOG.warn("Attempt to write empty file by : {}", eventType);
            return;
        }
        FileTime eventTime = null;
        AuditLoggerDeviceExtension ext = device.getDeviceExtension(AuditLoggerDeviceExtension.class);
        for (AuditLogger auditLogger : ext.getAuditLoggers()) {
            if (auditLogger.isInstalled()) {
                Path dir = toDirPath(auditLogger);
                try {
                    Files.createDirectories(dir);
                    Path file = Files.createTempFile(dir, eventType.name(), null);
                    try (BufferedOutputStream out = new BufferedOutputStream(
                            Files.newOutputStream(file, StandardOpenOption.APPEND))) {
                        out.write(data[0]);
                        if (data.length > 1 && data[1].length > 0)
                            out.write(data[1]);
                        try (SpoolFileWriter writer = new SpoolFileWriter(Files.newBufferedWriter(
                                file, StandardCharsets.UTF_8, StandardOpenOption.APPEND))) {
                            writer.writeLine(new AuditInfo(auditInfoBuilder));
                        }
                    }
                    if (eventTime == null)
                        eventTime = Files.getLastModifiedTime(file);
                    else
                        Files.setLastModifiedTime(file, eventTime);
                    if (!getArchiveDevice().isAuditAggregate())
                        auditAndProcessFile(auditLogger, file);
                } catch (Exception e) {
                    LOG.warn("Failed to write audit spool file for [AuditEventType={}] at [AuditLogger={}]\n",
                            eventType, auditLogger.getCommonName(), e);
                }
            }
        }
    }

    private void writeSpoolFile(AuditUtils.EventType eventType, String suffix, AuditInfoBuilder... auditInfoBuilders) {
        String file = suffix != null ? eventType.name().concat(suffix) : eventType.name();
        if (auditInfoBuilders == null) {
            LOG.warn("Attempt to write empty file : " + file);
            return;
        }
        FileTime eventTime = null;
        AuditLoggerDeviceExtension ext = device.getDeviceExtension(AuditLoggerDeviceExtension.class);
        for (AuditLogger auditLogger : ext.getAuditLoggers()) {
            if (auditLogger.isInstalled()) {
                Path dir = toDirPath(auditLogger);
                try {
                    Files.createDirectories(dir);
                    Path filePath = eventType.eventClass == AuditUtils.EventClass.STORE_WADOR
                            ? filePath(file, dir, auditInfoBuilders)
                            : filePath(eventType, dir, auditInfoBuilders);
                    if (eventTime == null)
                        eventTime = Files.getLastModifiedTime(filePath);
                    else
                        Files.setLastModifiedTime(filePath, eventTime);
                    if (!getArchiveDevice().isAuditAggregate())
                        auditAndProcessFile(auditLogger, filePath);
                } catch (Exception e) {
                    LOG.warn("Failed to write [AuditSpoolFile={}] at [AuditLogger={}]\n",
                            file, auditLogger.getCommonName(), e);
                }
            }
        }
    }

    private Path filePath(AuditUtils.EventType eventType, Path dir, AuditInfoBuilder... auditInfoBuilders)
            throws IOException {
        Path file = Files.createTempFile(dir, eventType.name(), null);
        try (SpoolFileWriter writer = new SpoolFileWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.APPEND))) {
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

    private void emitAuditMessage(AuditMessage msg, AuditLogger logger) {
        msg.getAuditSourceIdentification().add(logger.createAuditSourceIdentification());
        try {
            logger.write(logger.timeStamp(), msg);
        } catch (Exception e) {
            LOG.warn("Failed to emit audit message for [AuditLogger={}]\n", logger.getCommonName(), e);
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

        LOG.warn("Remote user ID was not set during spooling.");
        return null;
    }
}