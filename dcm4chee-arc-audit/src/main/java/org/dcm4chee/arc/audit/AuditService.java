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
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.net.*;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4che3.net.audit.AuditLoggerDeviceExtension;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.HL7DeviceExtension;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.AssociationEvent;
import org.dcm4chee.arc.HL7ConnectionEvent;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.entity.Patient;
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
import org.dcm4chee.arc.store.InstanceLocations;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.stgcmt.StgCmtContext;
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
    private final String studyDate = "StudyDate";

    @Inject
    private Device device;

    @Inject
    private IHL7ApplicationCache hl7AppCache;

    private void aggregateAuditMessage(AuditLogger auditLogger, Path path) {
        AuditUtils.EventType eventType = AuditUtils.EventType.fromFile(path);
        if (path.toFile().length() == 0) {
            LOG.warn("Attempt to read from an empty file.", eventType, path);
            return;
        }
        try {
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
                case IMPAX:
                    auditPatientMismatch(auditLogger, path, eventType);
                    break;
                case ASSOCIATION_FAILURE:
                    auditAssociationFailure(auditLogger, path, eventType);
                    break;
            }
        } catch (Exception e) {
            LOG.warn("Failed in audit with event type {} : {}", eventType, e);
        }
    }

    void spoolApplicationActivity(ArchiveServiceEvent event) {
        try {
            if (event.getType() == ArchiveServiceEvent.Type.RELOADED)
                return;

            HttpServletRequest req = event.getRequest();
            AuditInfoBuilder info = req != null
                    ? restfulTriggeredApplicationActivityInfo(req)
                    : systemTriggeredApplicationActivityInfo();
            writeSpoolFile(AuditUtils.EventType.forApplicationActivity(event), info);
        } catch (Exception e) {
            LOG.warn("Failed to spool Application Activity : {}", e);
        }
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

    private void auditApplicationActivity(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
        SpoolFileReader reader = new SpoolFileReader(path);
        EventIdentificationBuilder eventIdentification = toBuildEventIdentification(eventType, null, getEventTime(path, auditLogger));
        AuditInfo archiveInfo = new AuditInfo(reader.getMainInfo());
        ActiveParticipantBuilder[] activeParticipants = buildApplicationActivityActiveParticipants(auditLogger, eventType, archiveInfo);
        emitAuditMessage(
                AuditMessages.createMessage(eventIdentification, activeParticipants),
                auditLogger);
    }

    private ActiveParticipantBuilder[] buildApplicationActivityActiveParticipants(
            AuditLogger auditLogger, AuditUtils.EventType eventType, AuditInfo archiveInfo) {
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
                    .isRequester()
                    .roleIDCode(eventType.source)
                    .build();
        }
        return activeParticipantBuilder;
    }

    private void spoolInstancesDeleted(StoreContext ctx) {
        try {
            writeSpoolFile(
                    AuditUtils.EventType.forInstancesDeleted(ctx),
                    DeletionAuditService.instancesDeletedAuditInfo(ctx, getArchiveDevice()));
        } catch (Exception e) {
            LOG.warn("Failed to spool Instances Deleted : {}", e);
        }
    }

    void spoolStudyDeleted(StudyDeleteContext ctx) {
        try {
            writeSpoolFile(
                    AuditUtils.EventType.forStudyDeleted(ctx),
                    DeletionAuditService.studyDeletedAuditInfo(ctx, getArchiveDevice()));
        } catch (Exception e) {
            LOG.warn("Failed to spool Study Deleted : {}", e);
        }
    }

    void spoolExternalRejection(RejectionNoteSent rejectionNoteSent) {
        try {
            writeSpoolFile(
                    AuditUtils.EventType.forExternalRejection(rejectionNoteSent),
                    DeletionAuditService.externalRejectionAuditInfo(rejectionNoteSent, getArchiveDevice()));
        } catch (Exception e) {
            LOG.warn("Failed to spool External Rejection : {}", e);
        }
    }

    private void auditDeletion(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());

        String outcome = auditInfo.getField(AuditInfo.OUTCOME);
        EventIdentificationBuilder ei = toCustomBuildEventIdentification(eventType, outcome,
                auditInfo.getField(AuditInfo.WARNING), getEventTime(path, auditLogger));

        emitAuditMessage(
                DeletionAuditService.auditMsg(auditLogger, reader, eventType, ei),
                auditLogger);
    }

    void spoolQueueMessageEvent(QueueMessageEvent queueMsgEvent) {
        if (queueMsgEvent.getQueueMsg() == null)
            return;

        try {
            writeSpoolFile(
                    AuditUtils.EventType.forQueueEvent(queueMsgEvent.getOperation()),
                    QueueMessageAuditService.queueMsgAuditInfo(queueMsgEvent));
        } catch (Exception e) {
            LOG.warn("Failed to spool Queue Message Event : {}", e);
        }
    }

    void spoolBulkQueueMessageEvent(BulkQueueMessageEvent bulkQueueMsgEvent) {
        try {
            writeSpoolFile(
                    AuditUtils.EventType.forQueueEvent(bulkQueueMsgEvent.getOperation()),
                    QueueMessageAuditService.bulkQueueMsgAuditInfo(bulkQueueMsgEvent));
        } catch (Exception e) {
            LOG.warn("Failed to spool Bulk Queue Message Event : {}", e);
        }
    }

    private void auditQueueMessageEvent(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        Calendar eventTime = getEventTime(path, auditLogger);
        emitAuditMessage(
                QueueMessageAuditService.auditMsg(auditInfo, eventType, auditLogger, eventTime),
                auditLogger);
    }

    void spoolSoftwareConfiguration(SoftwareConfiguration softwareConfiguration) {
        try {
            writeSpoolFile(
                    SoftwareConfigurationAuditService.auditInfo(softwareConfiguration),
                    softwareConfiguration.getLdapDiff().toString());
        } catch (Exception e) {
            LOG.warn("Failed to spool Software Configuration Changes : {}", e);
        }
    }

    private void auditSoftwareConfiguration(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
        SpoolFileReader reader = new SpoolFileReader(path);
        Calendar eventTime = getEventTime(path, auditLogger);
        emitAuditMessage(
                SoftwareConfigurationAuditService.auditMsg(auditLogger, reader, eventType, eventTime),
                auditLogger);
    }

    void spoolExternalRetrieve(ExternalRetrieveContext ctx) {
        try {
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
                    .moveUserID(ctx.getRequestURI())
                    .destUserID(ctx.getDestinationAET())
                    .warning(warning)
                    .studyUIDAccNumDate(ctx.getKeys(), getArchiveDevice())
                    .outcome(outcome)
                    .build();
            writeSpoolFile(AuditUtils.EventType.INST_RETRV, info);
        } catch (Exception e) {
            LOG.warn("Failed to spool External Retrieve : {}", e);
        }
    }

    private void auditExternalRetrieve(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo i = new AuditInfo(reader.getMainInfo());
        EventIdentificationBuilder eventIdentification = toCustomBuildEventIdentification(eventType, i.getField(AuditInfo.OUTCOME),
                i.getField(AuditInfo.WARNING), getEventTime(path, auditLogger));

        ActiveParticipantBuilder[] activeParticipants = new ActiveParticipantBuilder[4];
        String userID = i.getField(AuditInfo.CALLING_USERID);
        activeParticipants[0] = new ActiveParticipantBuilder.Builder(
                                userID,
                                i.getField(AuditInfo.CALLING_HOST))
                                .userIDTypeCode(AuditMessages.userIDTypeCode(userID))
                                .isRequester()
                                .build();
        activeParticipants[1] = new ActiveParticipantBuilder.Builder(
                                i.getField(AuditInfo.MOVE_USER_ID),
                                getLocalHostName(auditLogger))
                                .userIDTypeCode(AuditMessages.UserIDTypeCode.URI)
                                .altUserID(AuditLogger.processID())
                                .build();
        activeParticipants[2] = new ActiveParticipantBuilder.Builder(
                                i.getField(AuditInfo.CALLED_USERID),
                                i.getField(AuditInfo.CALLED_HOST))
                                .userIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle)
                                .roleIDCode(eventType.source)
                                .build();
        activeParticipants[3] = new ActiveParticipantBuilder.Builder(
                                i.getField(AuditInfo.DEST_USER_ID),
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
        emitAuditMessage(AuditMessages.createMessage(eventIdentification, activeParticipants, studyPOI),
                auditLogger);
    }

    void spoolConnectionFailure(ConnectionEvent event) {
        try {
            writeSpoolFile(
                    AuditUtils.EventType.CONN_FAILR,
                    ConnectionEventsAuditService.connFailureAuditInfo(event, device.getDeviceName()));
        } catch (Exception e) {
            LOG.warn("Failed to spool Connection Rejected : {}", e);
        }
    }

    private void auditConnectionFailure(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());

        emitAuditMessage(
                ConnectionEventsAuditService.auditMsg(auditInfo, eventType, getEventTime(path, auditLogger)),
                auditLogger);
    }

    void spoolQuery(QueryContext ctx) {
        try {
            boolean auditAggregate = getArchiveDevice().isAuditAggregate();
            AuditLoggerDeviceExtension ext = device.getDeviceExtension(AuditLoggerDeviceExtension.class);
            AuditUtils.EventType eventType = AuditUtils.EventType.QUERY__EVT;
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
                                    LOG.warn("Failed to create DicomOutputStream : {}", e);
                                }
                            }
                        }
                        if (!auditAggregate)
                            auditAndProcessFile(auditLogger, file);
                    } catch (Exception e) {
                        LOG.warn("Failed to write to Query Audit Spool File {} : {}", auditLogger.getCommonName(), e);
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to spool Query : {}", e);
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

    private boolean isSpoolingSuppressed(AuditUtils.EventType eventType, String userID, AuditLogger auditLogger) {
        return !auditLogger.isInstalled()
                || (!auditLogger.getAuditSuppressCriteriaList().isEmpty()
                    && auditLogger.isAuditMessageSuppressed(createMinimalAuditMsg(eventType, userID)));
    }

    private AuditMessage createMinimalAuditMsg(AuditUtils.EventType eventType, String userID) {
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
            LOG.warn("Failed to process Audit Spool File {} of Audit Logger {} : {}",
                    file, auditLogger.getCommonName(), e);
            try {
                Files.move(file, file.resolveSibling(file.getFileName().toString() + ".failed"));
            } catch (IOException e1) {
                LOG.warn("Failed to mark Audit Spool File {} of Audit Logger {} as failed : {}",
                        file, auditLogger.getCommonName(), e);
            }
        }
    }

    private void auditQuery(
            AuditLogger auditLogger, Path file, AuditUtils.EventType eventType) throws IOException {
        AuditInfo qrI;
        ActiveParticipantBuilder[] activeParticipants = new ActiveParticipantBuilder[2];
        EventIdentificationBuilder eventIdentification = toBuildEventIdentification(eventType, null, getEventTime(file, auditLogger));
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            qrI = new AuditInfo(new DataInputStream(in).readUTF());
            String archiveUserID = qrI.getField(AuditInfo.CALLED_USERID);
            String callingUserID = qrI.getField(AuditInfo.CALLING_USERID);
            AuditMessages.UserIDTypeCode archiveUserIDTypeCode = archiveUserIDTypeCode(archiveUserID);
            activeParticipants[0] = new ActiveParticipantBuilder.Builder(
                                    callingUserID,
                                    qrI.getField(AuditInfo.CALLING_HOST))
                                    .userIDTypeCode(remoteUserIDTypeCode(archiveUserIDTypeCode, callingUserID))
                                    .isRequester()
                                    .roleIDCode(eventType.source)
                                    .build();
            activeParticipants[1] = new ActiveParticipantBuilder.Builder(
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
            emitAuditMessage(AuditMessages.createMessage(eventIdentification, activeParticipants, poi),
                    auditLogger);
        }
    }

    void spoolStoreEvent(StoreContext ctx) {
        try {
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
            LOG.warn("Failed to spool Store Event : {}", e);
        }
    }

    private void spoolInstancesStored(StoreContext ctx) {
        try {
            AuditUtils.EventType eventType = AuditUtils.EventType.forInstanceStored(ctx);

            StoreSession ss = ctx.getStoreSession();
            HttpServletRequest req = ss.getHttpRequest();
            String impaxReportEndpoint = ss.getImpaxReportEndpoint();
            String callingUserID = req != null
                    ? KeycloakContext.valueOf(req).getUserName()
                    : ss.getCallingAET();

            String outcome = ctx.getException() != null
                    ? ctx.getRejectionNote() != null
                    ? ctx.getRejectionNote().getRejectionNoteCode().getCodeMeaning() + '-' + ctx.getException().getMessage()
                    : ctx.getException().getMessage()
                    : null;

            AuditInfoBuilder instanceInfo = new AuditInfoBuilder.Builder()
                    .sopCUID(ctx.getSopClassUID()).sopIUID(ctx.getSopInstanceUID())
                    .mppsUID(ctx.getMppsInstanceUID())
                    .outcome(outcome)
                    .errorCode(ctx.getException() instanceof DicomServiceException ? ((DicomServiceException) ctx.getException()).getStatus() : 0)
                    .build();

            ArchiveDeviceExtension arcDev = getArchiveDevice();
            Attributes attr = ctx.getAttributes();
            AuditInfoBuilder info = new AuditInfoBuilder.Builder().callingHost(ss.getRemoteHostName())
                    .callingUserID(impaxReportEndpoint != null ? impaxReportEndpoint : callingUserID)
                    .calledUserID(req != null ? req.getRequestURI() : ss.getCalledAET())
                    .studyUIDAccNumDate(attr, arcDev)
                    .pIDAndName(attr, arcDev)
                    .warning(ctx.getRejectionNote() != null
                            ? ctx.getRejectionNote().getRejectionNoteCode().getCodeMeaning() : null)
                    .build();

            String fileName = eventType.name()
                                + '-' + callingUserID.replace('|', '-')
                                + '-' + ctx.getStoreSession().getCalledAET()
                                + '-' + ctx.getStudyInstanceUID();
            fileName = outcome != null ? fileName.concat("_ERROR") : fileName;
            writeSpoolFileStoreOrWadoRetrieve(fileName, info, instanceInfo);
            if (ctx.getImpaxReportPatientMismatch() != null) {
                AuditInfoBuilder patMismatchInfo = new AuditInfoBuilder.Builder().callingHost(ss.getRemoteHostName())
                        .callingUserID(impaxReportEndpoint != null ? impaxReportEndpoint : callingUserID)
                        .calledUserID(req != null ? req.getRequestURI() : ss.getCalledAET())
                        .studyUIDAccNumDate(attr, arcDev)
                        .pIDAndName(attr, arcDev)
                        .patMismatchCode(ctx.getImpaxReportPatientMismatch().toString())
                        .build();
                writeSpoolFile(AuditUtils.EventType.IMPAX_MISM, patMismatchInfo, instanceInfo);
            }
        } catch (Exception e) {
            LOG.warn("Failed to spool Instances Stored : {}", e);
        }
    }

    private void auditPatientMismatch(AuditLogger logger, Path path, AuditUtils.EventType eventType) {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        AuditMessage auditMsg = AuditMessages.createMessage(
                patientMismatchEventIdentification(logger, path, eventType, auditInfo),
                patientMismatchActiveParticipants(logger, auditInfo),
                storeStudyPOI(auditInfo, studyParticipantObjDesc(reader, auditInfo)),
                patientPOI(auditInfo));
        emitAuditMessage(auditMsg, logger);
    }

    private EventIdentificationBuilder patientMismatchEventIdentification(
            AuditLogger logger, Path path, AuditUtils.EventType eventType, AuditInfo auditInfo) {
        AuditMessages.EventTypeCode eventTypeCode = patMismatchEventTypeCode(auditInfo);
        return new EventIdentificationBuilder.Builder(
                                            eventType.eventID,
                                            eventType.eventActionCode,
                                            getEventTime(path, logger),
                                            AuditMessages.EventOutcomeIndicator.MinorFailure)
                                            .outcomeDesc(eventTypeCode.getOriginalText())
                                            .eventTypeCode(eventTypeCode)
                                            .build();
    }


    private ActiveParticipantBuilder[] patientMismatchActiveParticipants(AuditLogger logger, AuditInfo auditInfo) {
        ActiveParticipantBuilder[] activeParticipants = new ActiveParticipantBuilder[2];
        String callingUserID = auditInfo.getField(AuditInfo.CALLING_USERID);
        String calledUserID = auditInfo.getField(AuditInfo.CALLED_USERID);
        activeParticipants[0] = new ActiveParticipantBuilder.Builder(calledUserID, getLocalHostName(logger))
                .userIDTypeCode(archiveUserIDTypeCode(calledUserID)).build();
        activeParticipants[1] = new ActiveParticipantBuilder.Builder(
                callingUserID, auditInfo.getField(AuditInfo.CALLING_HOST))
                .userIDTypeCode(AuditMessages.userIDTypeCode(callingUserID))
                .isRequester().build();
        return activeParticipants;
    }

    private AuditMessages.EventTypeCode patMismatchEventTypeCode(AuditInfo auditInfo) {
        String patMismatchCode = auditInfo.getField(AuditInfo.PAT_MISMATCH_CODE);
        String[] code = patMismatchCode.substring(1, patMismatchCode.length() - 1).split(",");
        return new AuditMessages.EventTypeCode(code[0], code[1], code[2]);
    }

    private boolean isDuplicateReceivedInstance(StoreContext ctx) {
        return ctx.getLocations().isEmpty() && ctx.getStoredInstance() == null && ctx.getException() == null;
    }
    
    void spoolRetrieveWADO(RetrieveContext ctx) {
        try {
            HttpServletRequestInfo req = ctx.getHttpServletRequestInfo();
            Collection<InstanceLocations> il = ctx.getMatches();
            Attributes attrs = new Attributes();
            for (InstanceLocations i : il)
                attrs = i.getAttributes();
            String fileName = AuditUtils.EventType.WADO___URI.name()
                                + '-' + req.requesterHost
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
            writeSpoolFileStoreOrWadoRetrieve(fileName, info, instanceInfo);
        } catch (Exception e) {
            LOG.warn("Failed to spool Wado Retrieve : {}", e);
        }
    }

    private void auditStoreError(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());

        InstanceInfo instanceInfo = new InstanceInfo(auditInfo.getField(AuditInfo.ACC_NUM));

        HashSet<String> outcome = new HashSet<>();
        HashSet<AuditMessages.EventTypeCode> errorCode = new HashSet<>();

        for (String line : reader.getInstanceLines()) {
            AuditInfo info = new AuditInfo(line);
            outcome.add(info.getField(AuditInfo.OUTCOME));
            AuditMessages.EventTypeCode errorEventTypeCode = AuditUtils.errorEventTypeCode(info.getField(AuditInfo.ERROR_CODE));
            if (errorEventTypeCode != null)
                errorCode.add(errorEventTypeCode);

            instanceInfo.addSOPInstance(info);
            instanceInfo.addMpps(info);
        }

        EventIdentificationBuilder eventIdentification = new EventIdentificationBuilder.Builder(
                eventType.eventID,
                eventType.eventActionCode,
                getEventTime(path, auditLogger),
                AuditMessages.EventOutcomeIndicator.MinorFailure)
                .outcomeDesc(outcome.stream().collect(Collectors.joining("\n")))
                .eventTypeCode(errorCode.toArray(new AuditMessages.EventTypeCode[0]))
                .build();

        ParticipantObjectDescriptionBuilder desc = new ParticipantObjectDescriptionBuilder.Builder()
                .sopC(toSOPClasses(instanceInfo.getSopClassMap(), true))
                .acc(instanceInfo.getAccNum())
                .mpps(instanceInfo.getMpps())
                .build();

        AuditMessage auditMsg = AuditMessages.createMessage(
                eventIdentification,
                storeWadoURIActiveParticipants(auditLogger, auditInfo, eventType),
                storeStudyPOI(auditInfo, desc),
                patientPOI(auditInfo));
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
        EventIdentificationBuilder eventIdentification = toCustomBuildEventIdentification(
                                            eventType,
                                            auditInfo.getField(AuditInfo.OUTCOME),
                                            auditInfo.getField(AuditInfo.WARNING),
                                            getEventTime(path, auditLogger));

        AuditMessage auditMsg = AuditMessages.createMessage(
                eventIdentification,
                storeWadoURIActiveParticipants(auditLogger, auditInfo, eventType),
                storeStudyPOI(auditInfo, studyParticipantObjDesc(reader, auditInfo)),
                patientPOI(auditInfo));
        emitAuditMessage(auditMsg, auditLogger);
    }

    private ParticipantObjectDescriptionBuilder studyParticipantObjDesc(SpoolFileReader reader, AuditInfo auditInfo) {
        InstanceInfo instanceInfo = new InstanceInfo(auditInfo.getField(AuditInfo.ACC_NUM));
        for (String line : reader.getInstanceLines()) {
            AuditInfo info = new AuditInfo(line);
            instanceInfo.addMpps(info);
            instanceInfo.addSOPInstance(info);
        }

        return new ParticipantObjectDescriptionBuilder.Builder()
                .sopC(toSOPClasses(instanceInfo.getSopClassMap(), false))
                .acc(instanceInfo.getAccNum())
                .mpps(instanceInfo.getMpps())
                .build();
    }

    private ParticipantObjectIdentificationBuilder storeStudyPOI(AuditInfo auditInfo, ParticipantObjectDescriptionBuilder desc) {
        return new ParticipantObjectIdentificationBuilder.Builder(
                auditInfo.getField(AuditInfo.STUDY_UID),
                AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID,
                AuditMessages.ParticipantObjectTypeCode.SystemObject,
                AuditMessages.ParticipantObjectTypeCodeRole.Report)
                .desc(desc)
                .detail(getPod(studyDate, auditInfo.getField(AuditInfo.STUDY_DATE)))
                .lifeCycle(AuditMessages.ParticipantObjectDataLifeCycle.OriginationCreation)
                .build();
    }

    private void auditWADORetrieve(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        EventIdentificationBuilder eventIdentification = toBuildEventIdentification(
                eventType,
                auditInfo.getField(AuditInfo.OUTCOME),
                getEventTime(path, auditLogger));

        ParticipantObjectDescriptionBuilder desc = new ParticipantObjectDescriptionBuilder.Builder()
                .sopC(toSOPClasses(reader, auditInfo.getField(AuditInfo.OUTCOME) != null))
                .acc(auditInfo.getField(AuditInfo.ACC_NUM))
                .build();

        ParticipantObjectIdentificationBuilder studyPOI =  new ParticipantObjectIdentificationBuilder.Builder(
                auditInfo.getField(AuditInfo.STUDY_UID),
                AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID,
                AuditMessages.ParticipantObjectTypeCode.SystemObject,
                AuditMessages.ParticipantObjectTypeCodeRole.Report)
                .desc(desc)
                .detail(getPod(studyDate, auditInfo.getField(AuditInfo.STUDY_DATE)))
                .build();
        AuditMessage auditMsg = AuditMessages.createMessage(
                eventIdentification,
                storeWadoURIActiveParticipants(auditLogger, auditInfo, eventType),
                studyPOI,
                patientPOI(auditInfo));
        emitAuditMessage(auditMsg, auditLogger);

    }

    private ActiveParticipantBuilder[] storeWadoURIActiveParticipants(
            AuditLogger auditLogger, AuditInfo auditInfo, AuditUtils.EventType eventType) {
        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[2];
        String callingUserID = auditInfo.getField(AuditInfo.CALLING_USERID);
        String archiveUserID = auditInfo.getField(AuditInfo.CALLED_USERID);
        AuditMessages.UserIDTypeCode archiveUserIDTypeCode = archiveUserIDTypeCode(archiveUserID);
        activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                callingUserID,
                auditInfo.getField(AuditInfo.CALLING_HOST))
                .userIDTypeCode(remoteUserIDTypeCode(archiveUserIDTypeCode, callingUserID))
                .isRequester()
                .roleIDCode(eventType.source)
                .build();
        activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(
                archiveUserID,
                getLocalHostName(auditLogger))
                .userIDTypeCode(archiveUserIDTypeCode)
                .altUserID(AuditLogger.processID())
                .roleIDCode(eventType.destination)
                .build();
        return activeParticipantBuilder;
    }

    void spoolRetrieve(AuditUtils.EventType eventType, RetrieveContext ctx) {
        try {
            RetrieveAuditService retrieveAuditService = new RetrieveAuditService(ctx, getArchiveDevice());
            for (AuditInfoBuilder[] auditInfoBuilder : retrieveAuditService.getAuditInfoBuilder())
                writeSpoolFile(eventType, auditInfoBuilder);
        } catch (Exception e) {
            LOG.warn("Failed to spool Retrieve : {}", e);
        }
    }

    private ArchiveDeviceExtension getArchiveDevice() {
        return device.getDeviceExtension(ArchiveDeviceExtension.class);
    }

    private void auditRetrieve(AuditLogger auditLogger, Path path, AuditUtils.EventType eventType) {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        EventIdentificationBuilder ei = toCustomBuildEventIdentification(eventType, auditInfo.getField(AuditInfo.OUTCOME),
                auditInfo.getField(AuditInfo.WARNING), getEventTime(path, auditLogger));

        emitAuditMessage(
                RetrieveAuditService.auditMsg(eventType, auditInfo, auditLogger, ei, reader),
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
            writeSpoolFile(eventType, patRecAuditService.getHL7IncomingPatInfo(), hl7ConnEvent);

            HL7Segment mrg = HL7AuditUtils.getHL7Segment(hl7ConnEvent.getHL7Message(), "MRG");
            if (mrg != null && eventType != AuditUtils.EventType.PAT___READ) //spool below only for successful changePID or merge
                writeSpoolFile(
                        AuditUtils.EventType.PAT_DELETE,
                        patRecAuditService.getHL7IncomingPrevPatInfo(mrg),
                        hl7ConnEvent);

            if (HL7AuditUtils.isOrderMessage(hl7ConnEvent))
                writeSpoolFile(
                        AuditUtils.EventType.forHL7IncomingOrderMsg(hl7ResponseMessage),
                        new ProcedureRecordAuditService(hl7ConnEvent, getArchiveDevice()).getHL7IncomingOrderInfo(),
                        hl7ConnEvent);
        } catch (Exception e) {
            LOG.warn("Failed to spool HL7 Incoming : {}", e);
        }

    }

    private void spoolOutgoingHL7Msg(HL7ConnectionEvent hl7ConnEvent) {
        try {
            PatientRecordAuditService patRecAuditService = new PatientRecordAuditService(hl7ConnEvent, getArchiveDevice());
            if (patRecAuditService.isArchiveHL7MsgAndNotOrder()) {
                writeSpoolFile(
                        AuditUtils.EventType.forHL7OutgoingPatRec(hl7ConnEvent.getHL7Message().msh().getMessageType()),
                        patRecAuditService.getHL7OutgoingPatInfo(),
                        hl7ConnEvent);

                HL7Segment mrg = HL7AuditUtils.getHL7Segment(hl7ConnEvent.getHL7Message(), "MRG");
                if (mrg != null)
                    writeSpoolFile(
                            AuditUtils.EventType.PAT_DELETE,
                            patRecAuditService.getHL7OutgoingPrevPatInfo(mrg),
                            hl7ConnEvent);
            }

            if (HL7AuditUtils.isOrderMessage(hl7ConnEvent))
                spoolOutgoingHL7OrderMsg(hl7ConnEvent);

        } catch (Exception e) {
            LOG.warn("Failed to spool HL7 Outgoing : {}", e);
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
                AuditUtils.EventType.forHL7OutgoingOrderMsg(orderCtrlStatus, hl7OrderSPSStatuses),
                new ProcedureRecordAuditService(hl7ConnEvent, getArchiveDevice()).getHL7OutgoingOrderInfo(),
                hl7ConnEvent);
    }

    void spoolPatientRecord(PatientMgtContext ctx) {
        if (ctx.getUnparsedHL7Message() != null)
            return;

        try {
            PatientRecordAuditService patRecAuditService = new PatientRecordAuditService(ctx, getArchiveDevice());
            writeSpoolFile(AuditUtils.EventType.forPatRec(ctx), patRecAuditService.getPatAuditInfo());

            if (ctx.getPreviousAttributes() != null)
                writeSpoolFile(AuditUtils.EventType.PAT_DELETE, patRecAuditService.getPrevPatAuditInfo());
        } catch (Exception e) {
            LOG.warn("Failed to spool Patient Record : {}", e);
        }
    }

    private void auditPatientRecord(AuditLogger auditLogger, Path path, AuditUtils.EventType et) throws Exception {
        SpoolFileReader reader = new SpoolFileReader(path.toFile());
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        Calendar eventTime = getEventTime(path, auditLogger);
        String patVerStatus = auditInfo.getField(AuditInfo.PAT_VERIFICATION_STATUS);
        boolean unverifiedPat = patVerStatus == null
                || Patient.VerificationStatus.valueOf(patVerStatus) == Patient.VerificationStatus.UNVERIFIED;

        ActiveParticipantBuilder[] activeParticipantBuilder = auditInfo.getField(AuditInfo.PDQ_SERVICE_URI) != null
                ? patVerActiveParticipants(auditLogger, et, auditInfo)
                : et.source == null
                    ? getSchedulerTriggeredActiveParticipant(auditLogger, auditInfo)
                    : auditInfo.getField(AuditInfo.IS_OUTGOING_HL7) != null
                        ? getOutgoingPatientRecordActiveParticipants(auditLogger, et, auditInfo)
                        : getInternalPatientRecordActiveParticipants(auditLogger, et, auditInfo);

        ParticipantObjectIdentificationBuilder patientPOI = unverifiedPat
            ? patRecPOI(reader, auditInfo): patVerStatusRecPOI(reader, auditInfo);

        AuditMessage auditMsg = AuditMessages.createMessage(
                patVerEventIdentification(et, auditInfo, eventTime),
                activeParticipantBuilder,
                patientPOI);
        emitAuditMessage(auditMsg, auditLogger);
    }

    private Patient.VerificationStatus patVerStatus(String patVerStatus) {
        return patVerStatus != null ? Patient.VerificationStatus.valueOf(patVerStatus) : null;
    }

    private EventIdentificationBuilder patVerEventIdentification(
            AuditUtils.EventType et, AuditInfo auditInfo, Calendar eventTime) {
        Patient.VerificationStatus patVerStatus = patVerStatus(auditInfo.getField(AuditInfo.PAT_VERIFICATION_STATUS));
        String outcome = auditInfo.getField(AuditInfo.OUTCOME);

        String eoi = patVerStatus == Patient.VerificationStatus.VERIFICATION_FAILED
                ? AuditMessages.EventOutcomeIndicator.SeriousFailure
                : patVerStatus == Patient.VerificationStatus.NOT_FOUND || outcome != null
                    ? AuditMessages.EventOutcomeIndicator.MinorFailure
                    : AuditMessages.EventOutcomeIndicator.Success;
        String outcomeDesc = patVerStatus == Patient.VerificationStatus.NOT_FOUND
                || patVerStatus == Patient.VerificationStatus.VERIFICATION_FAILED
                ? patVerStatus.name() : outcome;

        return new EventIdentificationBuilder.Builder(et.eventID, et.eventActionCode, eventTime, eoi)
                .outcomeDesc(outcomeDesc).build();
    }

    private ParticipantObjectIdentificationBuilder patRecPOI(SpoolFileReader reader, AuditInfo auditInfo) {
        return new ParticipantObjectIdentificationBuilder.Builder(
                                                                auditInfo.getField(AuditInfo.P_ID),
                                                                AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                                                                AuditMessages.ParticipantObjectTypeCode.Person,
                                                                AuditMessages.ParticipantObjectTypeCodeRole.Patient)
                                                                .name(auditInfo.getField(AuditInfo.P_NAME))
                                                                .detail(getHL7ParticipantObjectDetail(reader))
                                                                .build();
    }

    private ParticipantObjectIdentificationBuilder patVerStatusRecPOI(SpoolFileReader reader, AuditInfo auditInfo) {
        return new ParticipantObjectIdentificationBuilder.Builder(
                auditInfo.getField(AuditInfo.P_ID),
                AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                AuditMessages.ParticipantObjectTypeCode.Person,
                AuditMessages.ParticipantObjectTypeCodeRole.Patient)
                .name(auditInfo.getField(AuditInfo.P_NAME))
                .lifeCycle(AuditMessages.ParticipantObjectDataLifeCycle.Verification)
                .detail(getHL7ParticipantObjectDetail(reader))
                .build();
    }

    private ParticipantObjectDetail[] getHL7ParticipantObjectDetail(SpoolFileReader reader) {
        ParticipantObjectDetail[] detail = new ParticipantObjectDetail[2];
        setParticipantObjectDetail(reader.getData(), 0, detail);
        setParticipantObjectDetail(reader.getAck(), 1, detail);
        return detail;
    }

    private void setParticipantObjectDetail(byte[] val, int index, ParticipantObjectDetail[] detail) {
        if (val.length > 0) {
            detail[index] = new ParticipantObjectDetail();
            detail[index].setType("HL7v2 Message");
            detail[index].setValue(val);
        }
    }

    private ActiveParticipantBuilder[] getSchedulerTriggeredActiveParticipant(AuditLogger auditLogger, AuditInfo auditInfo) {
        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[1];
        activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                auditInfo.getField(AuditInfo.CALLING_USERID),
                getLocalHostName(auditLogger))
                .userIDTypeCode(AuditMessages.UserIDTypeCode.DeviceName)
                .altUserID(AuditLogger.processID())
                .isRequester()
                .build();
        return activeParticipantBuilder;
    }

    private ActiveParticipantBuilder[] patVerActiveParticipants(
            AuditLogger auditLogger, AuditUtils.EventType et, AuditInfo auditInfo) {
        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[3];
        String calledUserID = auditInfo.getField(AuditInfo.CALLED_USERID);
        String callingUserID = auditInfo.getField(AuditInfo.CALLING_USERID);

        String pdqServiceURI = auditInfo.getField(AuditInfo.PDQ_SERVICE_URI);
        activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                pdqServiceURI, null)
                .userIDTypeCode(pdqServiceURI.indexOf('/') != -1
                        ? AuditMessages.UserIDTypeCode.URI : AuditMessages.UserIDTypeCode.StationAETitle)
                .roleIDCode(et.source)
                .build();

        activeParticipantBuilder[1] = calledUserID == null
                ? new ActiveParticipantBuilder.Builder(
                    callingUserID,
                    getLocalHostName(auditLogger))
                    .userIDTypeCode(AuditMessages.UserIDTypeCode.DeviceName)
                    .altUserID(AuditLogger.processID())
                    .isRequester()
                    .roleIDCode(et.destination)
                    .build()
                : new ActiveParticipantBuilder.Builder(
                    calledUserID,
                    getLocalHostName(auditLogger))
                    .userIDTypeCode(AuditMessages.UserIDTypeCode.StationAETitle)
                    .altUserID(AuditLogger.processID())
                    .roleIDCode(et.destination)
                    .build();

        if (calledUserID != null)
            activeParticipantBuilder[2] = new ActiveParticipantBuilder.Builder(
                callingUserID,
                auditInfo.getField(AuditInfo.CALLING_HOST))
                .userIDTypeCode(AuditMessages.userIDTypeCode(callingUserID))
                .isRequester()
                .build();

        return activeParticipantBuilder;
    }

    private ActiveParticipantBuilder[] getInternalPatientRecordActiveParticipants(
            AuditLogger auditLogger, AuditUtils.EventType et, AuditInfo auditInfo) {
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
                                .userIDTypeCode(remoteUserIDTypeCode(archiveUserIDTypeCode, callingUserID))
                                .isRequester()
                                .roleIDCode(et.source)
                                .build();
        return activeParticipantBuilder;
    }

    private ActiveParticipantBuilder[] getOutgoingPatientRecordActiveParticipants(
            AuditLogger auditLogger, AuditUtils.EventType et, AuditInfo auditInfo) throws ConfigurationException {
        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[4];
        HL7DeviceExtension hl7Dev = device.getDeviceExtension(HL7DeviceExtension.class);

        String calledUserID = auditInfo.getField(AuditInfo.CALLED_USERID);
        String callingUserID = auditInfo.getField(AuditInfo.CALLING_USERID);

        String hl7SendingAppWithFacility = auditInfo.getField(AuditInfo.OUTGOING_HL7_SENDER);
        String hl7ReceivingAppWithFacility = auditInfo.getField(AuditInfo.OUTGOING_HL7_RECEIVER);

        HL7Application hl7AppSender = hl7Dev.getHL7Application(hl7SendingAppWithFacility, true);
        HL7Application hl7AppReceiver = hl7AppCache.findHL7Application(hl7ReceivingAppWithFacility);

        boolean isHL7Forward = hl7SendingAppWithFacility.equals(callingUserID)
                                && hl7ReceivingAppWithFacility.equals(calledUserID);

        activeParticipantBuilder[0] = new ActiveParticipantBuilder.Builder(
                                        hl7SendingAppWithFacility,
                                        hl7AppSender.getConnections().get(0).getHostname())
                                        .userIDTypeCode(AuditMessages.UserIDTypeCode.ApplicationFacility)
                                        .roleIDCode(et.source)
                                        .build();
        activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(
                                        hl7ReceivingAppWithFacility,
                                        hl7AppReceiver.getConnections().get(0).getHostname())
                                        .userIDTypeCode(AuditMessages.UserIDTypeCode.ApplicationFacility)
                                        .roleIDCode(et.destination)
                                        .build();
        if (isHL7Forward)
            activeParticipantBuilder[2] = new ActiveParticipantBuilder.Builder(
                    device.getDeviceName(),
                    getLocalHostName(auditLogger))
                    .userIDTypeCode(AuditMessages.UserIDTypeCode.DeviceName)
                    .altUserID(AuditLogger.processID())
                    .isRequester()
                    .build();
        else {
            activeParticipantBuilder[2] = new ActiveParticipantBuilder.Builder(
                    callingUserID,
                    auditInfo.getField(AuditInfo.CALLING_HOST))
                    .userIDTypeCode(AuditMessages.userIDTypeCode(callingUserID))
                    .isRequester()
                    .build();
            activeParticipantBuilder[3] = new ActiveParticipantBuilder.Builder(
                    calledUserID,
                    getLocalHostName(auditLogger))
                    .userIDTypeCode(AuditMessages.UserIDTypeCode.URI)
                    .altUserID(AuditLogger.processID())
                    .build();
        }

        return activeParticipantBuilder;
    }

    void spoolProcedureRecord(ProcedureContext ctx) {
        if (ctx.getUnparsedHL7Message() != null)
            return;

        try {
            writeSpoolFile(
                    AuditUtils.EventType.forProcedure(ctx.getEventActionCode()),
                    new ProcedureRecordAuditService(ctx, getArchiveDevice()).getProcUpdateAuditInfo());
        } catch (Exception e) {
            LOG.warn("Failed to spool Procedure Update procedure record : {}", e);
        }
    }

    void spoolProcedureRecord(StudyMgtContext ctx) {
        try {
            writeSpoolFile(
                    AuditUtils.EventType.forProcedure(ctx.getEventActionCode()),
                    new ProcedureRecordAuditService(ctx, getArchiveDevice()).getStudyUpdateAuditInfo());
        } catch (Exception e) {
            LOG.warn("Failed to spool Study Update procedure record : {}", e);
        }
    }

    private void auditProcedureRecord(AuditLogger auditLogger, Path path, AuditUtils.EventType et) {
        SpoolFileReader reader = new SpoolFileReader(path.toFile());
        AuditInfo prI = new AuditInfo(reader.getMainInfo());

        EventIdentificationBuilder eventIdentification = toBuildEventIdentification(et, prI.getField(AuditInfo.OUTCOME), getEventTime(path, auditLogger));

        ActiveParticipantBuilder[] activeParticipantBuilder = buildProcedureRecordActiveParticipants(auditLogger, prI);
        
        ParticipantObjectDescriptionBuilder desc = new ParticipantObjectDescriptionBuilder.Builder()
                .acc(prI.getField(AuditInfo.ACC_NUM)).build();

        ParticipantObjectIdentificationBuilder poiStudy = new ParticipantObjectIdentificationBuilder.Builder(
                                                        prI.getField(AuditInfo.STUDY_UID),
                                                        AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID,
                                                        AuditMessages.ParticipantObjectTypeCode.SystemObject,
                                                        AuditMessages.ParticipantObjectTypeCodeRole.Report)
                                                        .desc(desc)
                                                        .detail(getPod(studyDate, prI.getField(AuditInfo.STUDY_DATE)))
                                                        .detail(getHL7ParticipantObjectDetail(reader))
                                                        .build();
        AuditMessage auditMsg;
        if (prI.getField(AuditInfo.P_ID) != null)
            auditMsg = AuditMessages.createMessage(
                    eventIdentification,
                    activeParticipantBuilder,
                    poiStudy, patientPOI(prI));
        else
            auditMsg = AuditMessages.createMessage(
                    eventIdentification,
                    activeParticipantBuilder,
                    poiStudy);

        emitAuditMessage(auditMsg, auditLogger);
    }

    private ActiveParticipantBuilder[] buildProcedureRecordActiveParticipants(AuditLogger auditLogger, AuditInfo prI) {
        ActiveParticipantBuilder[] activeParticipantBuilder = new ActiveParticipantBuilder[3];
        String archiveUserID = prI.getField(AuditInfo.CALLED_USERID);
        AuditMessages.UserIDTypeCode archiveUserIDTypeCode = archiveUserIDTypeCode(archiveUserID);
        String callingUserID = prI.getField(AuditInfo.CALLING_USERID);
        boolean isHL7Forward = prI.getField(AuditInfo.IS_OUTGOING_HL7) != null;
        activeParticipantBuilder[0] = isHL7Forward
                                ? new ActiveParticipantBuilder.Builder(callingUserID, prI.getField(AuditInfo.CALLING_HOST))
                                    .userIDTypeCode(remoteUserIDTypeCode(archiveUserIDTypeCode, callingUserID)).build()
                                : new ActiveParticipantBuilder.Builder(callingUserID, prI.getField(AuditInfo.CALLING_HOST))
                                    .userIDTypeCode(remoteUserIDTypeCode(archiveUserIDTypeCode, callingUserID))
                                    .isRequester().build();
        activeParticipantBuilder[1] = new ActiveParticipantBuilder.Builder(
                                archiveUserID,
                                getLocalHostName(auditLogger))
                                .userIDTypeCode(archiveUserIDTypeCode)
                                .altUserID(AuditLogger.processID())
                                .build();
        if (isHL7Forward)
            activeParticipantBuilder[2] = new ActiveParticipantBuilder.Builder(
                    device.getDeviceName(),
                    getLocalHostName(auditLogger))
                    .userIDTypeCode(AuditMessages.UserIDTypeCode.DeviceName)
                    .altUserID(AuditLogger.processID())
                    .isRequester()
                    .build();
        return activeParticipantBuilder;
    }

    void spoolProvideAndRegister(ExportContext ctx) {
        if (ctx.getXDSiManifest() == null)
            return;

        try {
            writeSpoolFile(AuditUtils.EventType.PROV_REGIS,
                    ProvideAndRegisterAuditService.provideRegisterAuditInfo(ctx, getArchiveDevice()));
        } catch (Exception e) {
            LOG.warn("Failed to spool Provide and Register : {}", e);
        }
    }

    private void auditProvideAndRegister(AuditLogger auditLogger, Path path, AuditUtils.EventType et) {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        emitAuditMessage(
                ProvideAndRegisterAuditService.provideRegisterAuditMsg(auditInfo, auditLogger, et, getEventTime(path, auditLogger)),
                auditLogger);
    }

    private boolean isServiceUserTriggered(Object val) {
        return val != null;
    }

    void spoolStgCmt(StgCmtContext ctx) {
        try {
            Sequence success = ctx.getEventInfo().getSequence(Tag.ReferencedSOPSequence);
            Sequence failed = ctx.getEventInfo().getSequence(Tag.FailedSOPSequence);

            if (success != null && !success.isEmpty())
                writeSpoolFile(
                        AuditUtils.EventType.STG_COMMIT,
                        StorageCommitAuditService.getSuccessAuditInfo(ctx, getArchiveDevice()));

            if (failed != null && !failed.isEmpty())
                writeSpoolFile(
                        AuditUtils.EventType.STG_COMMIT,
                        StorageCommitAuditService.getFailedAuditInfo(ctx, getArchiveDevice()));
        } catch (Exception e) {
            LOG.warn("Failed to spool storage commitment : {}", e);
        }
    }

    private void auditStorageCommit(AuditLogger auditLogger, Path path, AuditUtils.EventType et) {
        SpoolFileReader reader = new SpoolFileReader(path);
        Calendar eventTime = getEventTime(path, auditLogger);

        emitAuditMessage(
                StorageCommitAuditService.auditMsg(reader, et, auditLogger, eventTime),
                auditLogger);
    }

    static SOPClass[] toSOPClasses(HashMap<String, HashSet<String>> sopClassMap, boolean showIUID) {
        SOPClass[] sopClasses = new SOPClass[sopClassMap.size()];
        int count = 0;
        for (Map.Entry<String, HashSet<String>> entry : sopClassMap.entrySet()) {
            sopClasses[count] = AuditMessages.createSOPClass(
                    showIUID ? entry.getValue() : null,
                    entry.getKey(),
                    entry.getValue().size());
            count++;
        }
        return sopClasses;
    }

    static SOPClass[] toSOPClasses(SpoolFileReader reader, boolean showIUID) {
        return toSOPClasses(buildSOPClassMap(reader), showIUID);
    }

    private static HashMap<String, HashSet<String>> buildSOPClassMap(SpoolFileReader reader) {
        HashMap<String, HashSet<String>> sopClassMap = new HashMap<>();
        for (String line : reader.getInstanceLines()) {
            AuditInfo ii = new AuditInfo(line);
            sopClassMap.computeIfAbsent(
                    ii.getField(AuditInfo.SOP_CUID),
                    k -> new HashSet<>()).add(ii.getField(AuditInfo.SOP_IUID));
        }
        return sopClassMap;
    }

    void spoolAssociationFailure(AssociationEvent associationEvent) {
        try {
            writeSpoolFile(
                    AuditUtils.EventType.ASSOC_FAIL,
                    AssociationEventsAuditService.associationFailureAuditInfo(associationEvent));
        } catch (Exception e) {
            LOG.warn("Failed to spool association event failure : {}", e);
        }
    }

    private void auditAssociationFailure(AuditLogger auditLogger, Path path, AuditUtils.EventType et) {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        emitAuditMessage(
                AssociationEventsAuditService.associationFailureAuditMsg(auditInfo, et, getEventTime(path, auditLogger)),
                auditLogger);
    }

    private ParticipantObjectDetail getPod(String type, String value) {
        return AuditMessages.createParticipantObjectDetail(type, value);
    }

    private Calendar getEventTime(Path path, AuditLogger auditLogger){
        Calendar eventTime = auditLogger.timeStamp();
        try {
            eventTime.setTimeInMillis(Files.getLastModifiedTime(path).toMillis());
        } catch (Exception e) {
            LOG.warn("Failed to get Last Modified Time of Audit Spool File {} in Audit Logger {} : {}",
                    path, auditLogger.getCommonName(), e);
        }
        return eventTime;
    }

    private String getLocalHostName(AuditLogger log) {
        return log.getConnections().get(0).getHostname();
    }

    private Path toDirPath(AuditLogger auditLogger) {
        return Paths.get(
                StringUtils.replaceSystemProperties(getArchiveDevice().getAuditSpoolDirectory()),
                auditLogger.getCommonName().replaceAll(" ", "_"));
    }

    private void writeSpoolFile(AuditInfoBuilder auditInfoBuilder, String data) {
        if (auditInfoBuilder == null) {
            LOG.warn("Attempt to write empty file : ", AuditUtils.EventType.LDAP_CHNGS);
            return;
        }
        FileTime eventTime = null;
        boolean auditAggregate = getArchiveDevice().isAuditAggregate();
        AuditLoggerDeviceExtension ext = device.getDeviceExtension(AuditLoggerDeviceExtension.class);
        for (AuditLogger auditLogger : ext.getAuditLoggers()) {
            if (auditLogger.isInstalled()) {
                Path dir = toDirPath(auditLogger);
                try {
                    Files.createDirectories(dir);
                    Path file = Files.createTempFile(dir, AuditUtils.EventType.LDAP_CHNGS.name(), null);
                    try (SpoolFileWriter writer = new SpoolFileWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                            StandardOpenOption.APPEND))) {
                        writer.writeLine(new AuditInfo(auditInfoBuilder), data);
                    }
                    if (eventTime == null)
                        eventTime = Files.getLastModifiedTime(file);
                    else
                        Files.setLastModifiedTime(file, eventTime);
                    if (!auditAggregate)
                        auditAndProcessFile(auditLogger, file);
                } catch (Exception e) {
                    LOG.warn("Failed to write to Audit Spool File {} : {}", auditLogger.getCommonName(), e);
                }
            }
        }
    }

    private void writeSpoolFile(
            AuditUtils.EventType eventType, AuditInfoBuilder auditInfoBuilder, HL7ConnectionEvent hl7ConnEvent) {
        if (auditInfoBuilder == null) {
            LOG.warn("Attempt to write empty file : ", eventType);
            return;
        }
        FileTime eventTime = null;
        boolean auditAggregate = getArchiveDevice().isAuditAggregate();
        AuditLoggerDeviceExtension ext = device.getDeviceExtension(AuditLoggerDeviceExtension.class);
        for (AuditLogger auditLogger : ext.getAuditLoggers()) {
            if (auditLogger.isInstalled()) {
                Path dir = toDirPath(auditLogger);
                try {
                    Files.createDirectories(dir);
                    Path file = Files.createTempFile(dir, String.valueOf(eventType), null);
                    byte[] ack = hl7ConnEvent.getHL7ResponseMessage().data();
                    try (BufferedOutputStream out = new BufferedOutputStream(
                            Files.newOutputStream(file, StandardOpenOption.APPEND))) {
                        out.write(hl7ConnEvent.getHL7Message().data());
                        if (ack.length > 0)
                            out.write(ack);
                        try (SpoolFileWriter writer = new SpoolFileWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                                StandardOpenOption.APPEND))) {
                            writer.writeLine(new AuditInfo(auditInfoBuilder));
                        }
                    }
                    if (eventTime == null)
                        eventTime = Files.getLastModifiedTime(file);
                    else
                        Files.setLastModifiedTime(file, eventTime);
                    if (!auditAggregate)
                        auditAndProcessFile(auditLogger, file);
                } catch (Exception e) {
                    LOG.warn("Failed to write to Audit Spool File {} : {}", auditLogger.getCommonName(), e);
                }
            }
        }
    }

    private void writeSpoolFile(AuditUtils.EventType eventType, AuditInfoBuilder... auditInfoBuilders) {
        if (auditInfoBuilders == null) {
            LOG.warn("Attempt to write empty file : ", eventType);
            return;
        }
        FileTime eventTime = null;
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
                    if (eventTime == null)
                        eventTime = Files.getLastModifiedTime(file);
                    else
                        Files.setLastModifiedTime(file, eventTime);
                    if (!auditAggregate)
                        auditAndProcessFile(auditLogger, file);
                } catch (Exception e) {
                    LOG.warn("Failed to write to Audit Spool File {} : {}", auditLogger.getCommonName(), e);
                }
            }
        }
    }

    private void writeSpoolFileStoreOrWadoRetrieve(String fileName, AuditInfoBuilder patStudyInfo, AuditInfoBuilder instanceInfo) {
        if (patStudyInfo == null && instanceInfo == null) {
            LOG.warn("Attempt to write empty file : " + fileName);
            return;
        }
        FileTime eventTime = null;
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
                    if (eventTime == null)
                        eventTime = Files.getLastModifiedTime(file);
                    else
                        Files.setLastModifiedTime(file, eventTime);
                    if (!auditAggregate)
                        auditAndProcessFile(auditLogger, file);
                } catch (Exception e) {
                    LOG.warn("Failed to write to Audit Spool File {} : {}", auditLogger.getCommonName(), file, e);
                }
            }
        }
    }

    private void emitAuditMessage(AuditMessage msg, AuditLogger logger) {
        msg.getAuditSourceIdentification().add(logger.createAuditSourceIdentification());
        try {
            logger.write(logger.timeStamp(), msg);
        } catch (Exception e) {
            LOG.warn("Failed to emit audit message", logger.getCommonName(), e);
        }
    }

    private EventIdentificationBuilder toCustomBuildEventIdentification(AuditUtils.EventType et, String failureDesc, String warningDesc, Calendar t) {
        return failureDesc != null
                ? toBuildEventIdentification(et, failureDesc, t)
                : new EventIdentificationBuilder.Builder(
                    et.eventID, et.eventActionCode, t, AuditMessages.EventOutcomeIndicator.Success)
                    .outcomeDesc(warningDesc).build();
    }

    private EventIdentificationBuilder toBuildEventIdentification(AuditUtils.EventType et, String desc, Calendar t) {
        return new EventIdentificationBuilder.Builder(
                et.eventID, et.eventActionCode, t,
                desc != null ? AuditMessages.EventOutcomeIndicator.MinorFailure : AuditMessages.EventOutcomeIndicator.Success)
                .outcomeDesc(desc).eventTypeCode(et.eventTypeCode).build();
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

    private AuditMessages.UserIDTypeCode archiveUserIDTypeCode(String userID) {
        return  userID.indexOf('/') != -1
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