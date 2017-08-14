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
import org.dcm4che3.conf.api.IApplicationEntityCache;
import org.dcm4che3.data.*;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.net.*;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4che3.net.audit.AuditLoggerDeviceExtension;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.ArchiveServiceEvent;
import org.dcm4chee.arc.ConnectionEvent;
import org.dcm4chee.arc.keycloak.KeycloakUtils;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.RejectionNote;
import org.dcm4chee.arc.conf.ShowPatientInfo;
import org.dcm4chee.arc.delete.StudyDeleteContext;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.entity.RejectionState;
import org.dcm4chee.arc.entity.Study;
import org.dcm4chee.arc.retrieve.ExternalRetrieveContext;
import org.dcm4chee.arc.event.RejectionNoteSent;
import org.dcm4chee.arc.exporter.ExportContext;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.procedure.ProcedureContext;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.retrieve.HttpServletRequestInfo;
import org.dcm4chee.arc.retrieve.InstanceLocations;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.stgcmt.StgCmtEventInfo;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreSession;
import org.dcm4chee.arc.study.StudyMgtContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.net.URI;
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
    private IApplicationEntityCache aeCache;

    private void aggregateAuditMessage(AuditLogger auditLogger, Path path) throws IOException {
        AuditServiceUtils.EventType eventType = AuditServiceUtils.EventType.fromFile(path);
        if (path.toFile().length() == 0)
            throw new IOException("Attempt to read from an empty file. ");
        SpoolFileReader readerObj = eventType.eventClass != AuditServiceUtils.EventClass.QUERY
                ? new SpoolFileReader(path) : null;
        Calendar eventTime = getEventTime(path, auditLogger);
        switch (eventType.eventClass) {
            case APPLN_ACTIVITY:
                auditApplicationActivity(auditLogger, readerObj, eventTime, eventType);
                break;
            case CONN_REJECT:
                auditConnectionRejected(auditLogger, readerObj, eventTime, eventType);
                break;
            case STORE_WADOR:
                auditStoreOrWADORetrieve(auditLogger, readerObj, eventTime, eventType);
                break;
            case BEGIN_TRF:
            case RETRIEVE:
            case RETRIEVE_ERR:
                auditRetrieve(auditLogger, readerObj, eventTime, eventType);
                break;
            case USER_DELETED:
            case SCHEDULER_DELETED:
                auditDeletion(auditLogger, readerObj, eventTime, eventType);
                break;
            case QUERY:
                auditQuery(auditLogger, path, eventTime, eventType);
                break;
            case HL7:
                auditPatientRecord(auditLogger, readerObj, eventTime, eventType);
                break;
            case PROC_STUDY:
                auditProcedureRecord(auditLogger, readerObj, eventTime, eventType);
                break;
            case PROV_REGISTER:
                auditProvideAndRegister(auditLogger, readerObj, eventTime, eventType);
                break;
            case STGCMT:
                auditStorageCommit(auditLogger, readerObj, eventTime, eventType);
                break;
            case INST_RETRIEVED:
                auditExternalRetrieve(auditLogger, path, eventType);
                break;
        }
    }

    void spoolApplicationActivity(ArchiveServiceEvent event) {
        if (event.getType() == ArchiveServiceEvent.Type.RELOADED)
            return;
        HttpServletRequest req = event.getRequest();
        LinkedHashSet<Object> objs = new LinkedHashSet<>();
        objs.add(new AuditInfo(new BuildAuditInfo.Builder().calledAET(getAET(device)).build()));
        if (req != null) {
            String callingUser = KeycloakUtils.getUserName(req);
            objs.add(new AuditInfo(
                    new BuildAuditInfo.Builder().callingAET(callingUser).callingHost(req.getRemoteAddr()).build()));
        }
        writeSpoolFile(AuditServiceUtils.EventType.forApplicationActivity(event), objs);
    }

    private void auditApplicationActivity(AuditLogger auditLogger, SpoolFileReader readerObj, Calendar eventTime, AuditServiceUtils.EventType eventType) {
        BuildEventIdentification ei = toBuildEventIdentification(eventType, null, eventTime);
        AuditInfo archiveInfo = new AuditInfo(readerObj.getMainInfo());
        BuildActiveParticipant[] activeParticipants = new BuildActiveParticipant[2];
        activeParticipants[0] = new BuildActiveParticipant.Builder(archiveInfo.getField(AuditInfo.CALLED_AET),
                getLocalHostName(auditLogger)).altUserID(AuditLogger.processID())
                .roleIDCode(eventType.destination).build();
        if (!readerObj.getInstanceLines().isEmpty()) {
            AuditInfo callerInfo = new AuditInfo(readerObj.getInstanceLines().iterator().next());
            activeParticipants[1] = new BuildActiveParticipant.Builder(
                    callerInfo.getField(AuditInfo.CALLING_AET), callerInfo.getField(AuditInfo.CALLING_HOST)).
                    requester(true).roleIDCode(eventType.source).build();
        }
        emitAuditMessage(auditLogger, ei, activeParticipants);
    }

    void spoolInstancesDeleted(StoreContext ctx) {
        if (isExternalRejectionSourceDestSame(ctx))
            return;
        Attributes attrs = ctx.getAttributes();
        HashMap<String, HashSet<String>> sopClassMap = new HashMap<>();
        for (Attributes studyRef : attrs.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence))
            for (Attributes seriesRef : studyRef.getSequence(Tag.ReferencedSeriesSequence))
                for (Attributes sopRef : seriesRef.getSequence(Tag.ReferencedSOPSequence))
                    buildSOPClassMap(sopClassMap, sopRef.getString(Tag.ReferencedSOPClassUID),
                            sopRef.getString(Tag.ReferencedSOPInstanceUID));
        LinkedHashSet<Object> deleteObjs = getDeletionObjsForSpooling(sopClassMap, new AuditInfo(getAIStoreCtx(ctx)));
        AuditServiceUtils.EventType eventType = ctx.getStoredInstance().getSeries().getStudy().getRejectionState()== RejectionState.COMPLETE
                                                    ? AuditServiceUtils.EventType.RJ_COMPLET
                                                    : AuditServiceUtils.EventType.RJ_PARTIAL;
        writeSpoolFile(eventType, deleteObjs);
    }

    private boolean isExternalRejectionSourceDestSame(StoreContext ctx) {
        StoreSession ss = ctx.getStoreSession();
        return ctx.getRejectionNote() != null && ss.getHttpRequest() == null && ss.getCallingAET().equals(ss.getCalledAET());
    }

    void spoolStudyDeleted(StudyDeleteContext ctx) {
        HashMap<String, HashSet<String>> sopClassMap = new HashMap<>();
        for (org.dcm4chee.arc.entity.Instance i : ctx.getInstances())
            buildSOPClassMap(sopClassMap, i.getSopClassUID(), i.getSopInstanceUID());
        Study s = ctx.getStudy();
        Patient p = ctx.getPatient();
        HttpServletRequest request = ctx.getHttpRequest();
        BuildAuditInfo i = request != null ? buildPermDeletionAuditInfoForWeb(request, ctx, s, p)
                : buildPermDeletionAuditInfoForScheduler(ctx, s, p);
        AuditServiceUtils.EventType eventType = request != null
                                                ? AuditServiceUtils.EventType.PRMDLT_WEB
                                                : AuditServiceUtils.EventType.PRMDLT_SCH;
        LinkedHashSet<Object> deleteObjs = getDeletionObjsForSpooling(sopClassMap, new AuditInfo(i));
        writeSpoolFile(eventType, deleteObjs);
    }

    void spoolExternalRejection(RejectionNoteSent rejectionNoteSent) throws ConfigurationException {
        LinkedHashSet<Object> deleteObjs = new LinkedHashSet<>();
        Attributes attrs = rejectionNoteSent.getRejectionNote();
        Attributes codeItem = attrs.getSequence(Tag.ConceptNameCodeSequence).get(0);
        Code code = new Code(codeItem.getString(Tag.CodeValue), codeItem.getString(Tag.CodingSchemeDesignator), null, "?");
        RejectionNote rjNote = device.getDeviceExtension(ArchiveDeviceExtension.class).getRejectionNote(code);
        HttpServletRequest req = rejectionNoteSent.getRequest();
        String callingAET = req != null
                ? KeycloakUtils.getUserName(req)
                : rejectionNoteSent.getLocalAET();
        String calledAET = req != null
                ? req.getRequestURI() : rejectionNoteSent.getRemoteAET();
        String callingHost = req != null
                ? req.getRemoteHost() : toHost(rejectionNoteSent.getLocalAET());
        deleteObjs.add(new AuditInfo(new BuildAuditInfo.Builder()
                .callingAET(callingAET)
                .callingHost(callingHost)
                .calledAET(calledAET)
                .calledHost(toHost(rejectionNoteSent.getRemoteAET()))
                .outcome(String.valueOf(rjNote.getRejectionNoteType()))
                .studyDate(getSD(attrs))
                .accNum(getAcc(attrs))
                .studyUID(attrs.getString(Tag.StudyInstanceUID))
                .pID(getPID(attrs))
                .pName(pName(attrs))
                .build()));
        HashMap<String, HashSet<String>> sopClassMap = new HashMap<>();
        for (Attributes studyRef : attrs.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence))
            for (Attributes refSer : studyRef.getSequence(Tag.ReferencedSeriesSequence))
                for (Attributes refSop : refSer.getSequence(Tag.ReferencedSOPSequence))
                    buildSOPClassMap(sopClassMap, refSop.getString(Tag.ReferencedSOPClassUID),
                            refSop.getString(Tag.ReferencedSOPInstanceUID));
        for (Map.Entry<String, HashSet<String>> entry : sopClassMap.entrySet()) {
            deleteObjs.add(new AuditInfo(new BuildAuditInfo.Builder().sopCUID(entry.getKey())
                    .sopIUID(String.valueOf(entry.getValue().size())).build()));
        }
        AuditServiceUtils.EventType clientET = rejectionNoteSent.isStudyDeleted()
                ? AuditServiceUtils.EventType.PRMDLT_WEB
                : AuditServiceUtils.EventType.RJ_PARTIAL;
        writeSpoolFile(clientET, deleteObjs);
        if (rejectionNoteSent.getLocalAET().equals(rejectionNoteSent.getRemoteAET())) {
            AuditServiceUtils.EventType serverET = rejectionNoteSent.isStudyDeleted()
                    ? AuditServiceUtils.EventType.RJ_COMPLET
                    : AuditServiceUtils.EventType.RJ_PARTIAL;
            writeSpoolFile(serverET, deleteObjs);
        }
    }
    private String toHost(String aet) throws ConfigurationException {
        ApplicationEntity ae = aeCache.findApplicationEntity(aet);
        StringBuilder b = new StringBuilder();
        if (ae != null) {
            List<Connection> conns = ae.getConnections();
            b.append(conns.get(0).getHostname());
            for (int i = 1; i < conns.size(); i++)
                b.append(';').append(conns.get(i).getHostname());
        }
        return b.toString();
    }


    private BuildAuditInfo buildPermDeletionAuditInfoForWeb(HttpServletRequest req, StudyDeleteContext ctx, Study s, Patient p) {
        String callingAET = KeycloakUtils.getUserName(req);
        return new BuildAuditInfo.Builder().callingAET(callingAET).callingHost(req.getRemoteHost()).calledAET(req.getRequestURI())
                .studyUID(s.getStudyInstanceUID()).accNum(s.getAccessionNumber())
                .pID(getPID(p.getAttributes())).outcome(getOD(ctx.getException())).studyDate(s.getStudyDate())
                .pName(pName(p.getAttributes())).build();
    }

    private BuildAuditInfo buildPermDeletionAuditInfoForScheduler(StudyDeleteContext ctx, Study s, Patient p) {
        return new BuildAuditInfo.Builder().studyUID(s.getStudyInstanceUID()).accNum(s.getAccessionNumber())
                .pID(getPID(p.getAttributes())).outcome(getOD(ctx.getException())).studyDate(s.getStudyDate())
                .pName(pName(p.getAttributes())).build();
    }

    private void auditDeletion(AuditLogger auditLogger, SpoolFileReader readerObj, Calendar eventTime, AuditServiceUtils.EventType eventType) {
        AuditInfo dI = new AuditInfo(readerObj.getMainInfo());
        boolean userDeleted = eventType.eventClass == AuditServiceUtils.EventClass.USER_DELETED;
        BuildEventIdentification ei = toCustomBuildEventIdentification(eventType, dI.getField(AuditInfo.OUTCOME),
                dI.getField(AuditInfo.WARNING), eventTime);
        BuildActiveParticipant[] activeParticipants = new BuildActiveParticipant[2];
        if (userDeleted) {
            activeParticipants[0] = new BuildActiveParticipant.Builder(
                    dI.getField(AuditInfo.CALLING_AET), dI.getField(AuditInfo.CALLING_HOST))
                    .requester(true).build();
            activeParticipants[1] = new BuildActiveParticipant.Builder(
                    dI.getField(AuditInfo.CALLED_AET),
                    getLocalHostName(auditLogger)).altUserID(AuditLogger.processID())
                    .build();
        } else
            activeParticipants[0] = new BuildActiveParticipant.Builder(
                    getAET(device),
                    getLocalHostName(auditLogger)).altUserID(AuditLogger.processID())
                    .requester(true).build();

        ParticipantObjectContainsStudy pocs = getPocs(dI.getField(AuditInfo.STUDY_UID));
        BuildParticipantObjectDescription desc = new BuildParticipantObjectDescription.Builder(
                getSopClasses(readerObj.getInstanceLines()), pocs)
                .acc(getAccessions(dI.getField(AuditInfo.ACC_NUM))).build();
        BuildParticipantObjectIdentification poiStudy = new BuildParticipantObjectIdentification.Builder(
                dI.getField(AuditInfo.STUDY_UID), AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID,
                AuditMessages.ParticipantObjectTypeCode.SystemObject,AuditMessages.ParticipantObjectTypeCodeRole.Report)
                .desc(getPODesc(desc)).detail(getPod(studyDate, dI.getField(AuditInfo.STUDY_DATE))).build();
        BuildParticipantObjectIdentification poiPatient = new BuildParticipantObjectIdentification.Builder(
                dI.getField(AuditInfo.P_ID), AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                AuditMessages.ParticipantObjectTypeCode.Person, AuditMessages.ParticipantObjectTypeCodeRole.Patient)
                .name(dI.getField(AuditInfo.P_NAME)).build();
        emitAuditMessage(auditLogger, ei, activeParticipants, poiStudy, poiPatient);
    }

    void spoolExternalRetrieve(ExternalRetrieveContext ctx) {
        String outcome = ctx.getResponse().getString(Tag.ErrorComment) != null
                            ? ctx.getResponse().getString(Tag.ErrorComment) + ctx.failed()
                            : null;
        String warning = ctx.warning() > 0
                            ? "Number Of Warning Sub operations" + ctx.warning()
                            : null;
        Attributes keys = ctx.getKeys();
        LinkedHashSet<Object> obj = new LinkedHashSet<>();
        BuildAuditInfo i = new BuildAuditInfo.Builder()
                .callingAET(ctx.getRequesterUserID())
                .callingHost(ctx.getRequesterHostName())
                .calledHost(ctx.getRemoteHostName())
                .calledAET(ctx.getRemoteAET())
                .moveAET(ctx.getRequestURI())
                .destAET(ctx.getDestinationAET())
                .warning(warning)
                .studyUID(keys.getString(Tag.StudyInstanceUID))
                .outcome(outcome)
                .build();
        obj.add(new AuditInfo(i));
        writeSpoolFile(AuditServiceUtils.EventType.INST_RETRV, obj);
    }

    private void auditExternalRetrieve(AuditLogger auditLogger, Path path, AuditServiceUtils.EventType eventType)
            throws IOException {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo i = new AuditInfo(reader.getMainInfo());
        BuildEventIdentification ei = toCustomBuildEventIdentification(eventType, i.getField(AuditInfo.OUTCOME),
                i.getField(AuditInfo.WARNING), getEventTime(path, auditLogger));
        BuildActiveParticipant[] activeParticipants = new BuildActiveParticipant[4];
        activeParticipants[0] = new BuildActiveParticipant.Builder(
                i.getField(AuditInfo.CALLING_AET),
                i.getField(AuditInfo.CALLING_HOST))
                .requester(true)
                .build();
        activeParticipants[1] = new BuildActiveParticipant.Builder(
                i.getField(AuditInfo.MOVEAET),
                getLocalHostName(auditLogger))
                .altUserID(AuditLogger.processID())
                .build();
        activeParticipants[2] = new BuildActiveParticipant.Builder(
                i.getField(AuditInfo.CALLED_AET),
                i.getField(AuditInfo.CALLED_HOST))
                .roleIDCode(eventType.source)
                .build();
        activeParticipants[3] = new BuildActiveParticipant.Builder(
                i.getField(AuditInfo.DEST_AET),
                i.getField(AuditInfo.DEST_NAP_ID))
                .roleIDCode(eventType.destination)
                .build();
        BuildParticipantObjectIdentification studyPOI = new BuildParticipantObjectIdentification.Builder(
                i.getField(AuditInfo.STUDY_UID), AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID,
                AuditMessages.ParticipantObjectTypeCode.SystemObject, AuditMessages.ParticipantObjectTypeCodeRole.Report)
                .build();
        emitAuditMessage(auditLogger, ei, activeParticipants, studyPOI);
    }

    void spoolConnectionRejected(ConnectionEvent event) {
        LinkedHashSet<Object> obj = new LinkedHashSet<>();
        BuildAuditInfo i = new BuildAuditInfo.Builder().callingHost(event.getSocket().getRemoteSocketAddress().toString())
                .calledHost(event.getConnection().getHostname()).outcome(event.getException().getMessage()).build();
        obj.add(new AuditInfo(i));
        writeSpoolFile(AuditServiceUtils.EventType.CONN__RJCT, obj);
    }

    private void auditConnectionRejected(AuditLogger auditLogger, SpoolFileReader readerObj, Calendar eventTime, AuditServiceUtils.EventType eventType) {
        AuditInfo crI = new AuditInfo(readerObj.getMainInfo());
        BuildEventIdentification ei = toBuildEventIdentification(eventType, crI.getField(AuditInfo.OUTCOME), eventTime);
        BuildActiveParticipant[] activeParticipants = new BuildActiveParticipant[2];
        activeParticipants[0] = new BuildActiveParticipant.Builder(getAET(device),
                crI.getField(AuditInfo.CALLED_HOST)).altUserID(AuditLogger.processID()).build();
        String userID, napID;
        userID = napID = crI.getField(AuditInfo.CALLING_HOST);
        activeParticipants[1] = new BuildActiveParticipant.Builder(userID, napID).requester(true).build();
        BuildParticipantObjectIdentification poi = new BuildParticipantObjectIdentification.Builder(
                crI.getField(AuditInfo.CALLING_HOST), AuditMessages.ParticipantObjectIDTypeCode.NodeID,
                AuditMessages.ParticipantObjectTypeCode.SystemObject, null).build();
        emitAuditMessage(auditLogger, ei, activeParticipants, poi);
    }

    void spoolQuery(QueryContext ctx) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        boolean auditAggregate = arcDev.isAuditAggregate();
        AuditLoggerDeviceExtension ext = device.getDeviceExtension(AuditLoggerDeviceExtension.class);
        AuditServiceUtils.EventType eventType = AuditServiceUtils.EventType.forQuery(ctx);
        AuditInfo auditInfo = ctx.getHttpRequest() != null ? createAuditInfoForQIDO(ctx) : createAuditInfoForFIND(ctx);
        for (AuditLogger auditLogger : ext.getAuditLoggers()) {
            if (!isSpoolingSuppressed(eventType, ctx.getCallingAET(), auditLogger)) {
                Path directory = Paths.get(StringUtils.replaceSystemProperties(arcDev.getAuditSpoolDirectory()),
                                auditLogger.getCommonName().replaceAll(" ", "_"));
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

    private boolean isSpoolingSuppressed(AuditServiceUtils.EventType eventType, String userID, AuditLogger auditLogger) {
        return !auditLogger.isInstalled()
                || (!auditLogger.getAuditSuppressCriteriaList().isEmpty()
                    && auditLogger.isAuditMessageSuppressed(createMinimalAuditMsg(eventType, userID)));
    }

    private AuditMessage createMinimalAuditMsg(AuditServiceUtils.EventType eventType, String userID) {
        AuditMessage msg = new AuditMessage();
        msg.setEventIdentification(getEI(eventType, null, null));
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

    private AuditInfo createAuditInfoForFIND(QueryContext ctx) {
        return new AuditInfo(
                new BuildAuditInfo.Builder()
                        .callingHost(ctx.getRemoteHostName())
                        .callingAET(ctx.getCallingAET())
                        .calledAET(ctx.getCalledAET())
                        .queryPOID(ctx.getSOPClassUID())
                        .build());
    }

    private AuditInfo createAuditInfoForQIDO(QueryContext ctx) {
        HttpServletRequest httpRequest = ctx.getHttpRequest();
        return new AuditInfo(
                new BuildAuditInfo.Builder()
                        .callingHost(ctx.getRemoteHostName())
                        .callingAET(KeycloakUtils.getUserName(ctx.getHttpRequest()))
                        .calledAET(httpRequest.getRequestURI())
                        .queryPOID(ctx.getSearchMethod())
                        .queryString(httpRequest.getRequestURI() + httpRequest.getQueryString())
                        .build());
    }

    private void auditQuery(
            AuditLogger auditLogger, Path file, Calendar eventTime, AuditServiceUtils.EventType eventType) throws IOException {
        AuditInfo qrI;
        BuildActiveParticipant[] activeParticipants = new BuildActiveParticipant[2];
        BuildEventIdentification ei = toBuildEventIdentification(eventType, null, eventTime);
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            qrI = new AuditInfo(new DataInputStream(in).readUTF());
            activeParticipants[0] = new BuildActiveParticipant.Builder(qrI.getField(AuditInfo.CALLING_AET),
                    qrI.getField(AuditInfo.CALLING_HOST)).requester(true).roleIDCode(eventType.source)
                    .build();
            activeParticipants[1] = new BuildActiveParticipant.Builder(qrI.getField(AuditInfo.CALLED_AET),
                    getLocalHostName(auditLogger)).altUserID(AuditLogger.processID())
                    .roleIDCode(eventType.destination).build();
            BuildParticipantObjectIdentification poi;
            if (eventType == AuditServiceUtils.EventType.QUERY_QIDO) {
                poi = new BuildParticipantObjectIdentification.Builder(
                        qrI.getField(AuditInfo.Q_POID), AuditMessages.ParticipantObjectIDTypeCode.QIDO_QUERY,
                        AuditMessages.ParticipantObjectTypeCode.SystemObject,
                        AuditMessages.ParticipantObjectTypeCodeRole.Query)
                        .query(qrI.getField(AuditInfo.Q_STRING).getBytes())
                        .detail(getPod("QueryEncoding", String.valueOf(StandardCharsets.UTF_8))).build();
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
                poi = new BuildParticipantObjectIdentification.Builder(
                        qrI.getField(AuditInfo.Q_POID), AuditMessages.ParticipantObjectIDTypeCode.SOPClassUID,
                        AuditMessages.ParticipantObjectTypeCode.SystemObject,
                        AuditMessages.ParticipantObjectTypeCodeRole.Report).query(data)
                        .detail(getPod("TransferSyntax", UID.ImplicitVRLittleEndian)).build();
            }
            emitAuditMessage(auditLogger, ei, activeParticipants, poi);
        }
    }

    void spoolInstanceStored(StoreContext ctx) {
        AuditServiceUtils.EventType eventType = AuditServiceUtils.EventType.forInstanceStored(ctx);
        if (eventType == null)
            return; // no audit message for duplicate received instance
        String callingAET = ctx.getStoreSession().getHttpRequest() != null
                ? ctx.getStoreSession().getHttpRequest().getRemoteAddr() : ctx.getStoreSession().getCallingAET().replace('|', '-');
        String fileName = getFileName(eventType, callingAET, ctx.getStoreSession().getCalledAET(), ctx.getStudyInstanceUID());
        BuildAuditInfo i = getAIStoreCtx(ctx);
        BuildAuditInfo iI = new BuildAuditInfo.Builder().sopCUID(ctx.getSopClassUID()).sopIUID(ctx.getSopInstanceUID())
                .mppsUID(StringUtils.maskNull(ctx.getMppsInstanceUID(), " ")).build();
        writeSpoolFileStoreOrWadoRetrieve(fileName, new AuditInfo(i), new AuditInfo(iI));
    }
    
        void spoolRetrieveWADO(RetrieveContext ctx) {
        HttpServletRequestInfo req = ctx.getHttpServletRequestInfo();
        Collection<InstanceLocations> il = ctx.getMatches();
        Attributes attrs = new Attributes();
        for (InstanceLocations i : il)
            attrs = i.getAttributes();
        String fileName = getFileName(AuditServiceUtils.EventType.WADO___URI, req.requesterHost,
                ctx.getLocalAETitle(), ctx.getStudyInstanceUIDs()[0]);
        AuditInfo i = new AuditInfo(new BuildAuditInfo.Builder().callingHost(req.requesterHost).callingAET(req.requesterUserID)
                .calledAET(req.requestURI).studyUID(ctx.getStudyInstanceUIDs()[0])
                .accNum(getAcc(attrs)).pID(getPID(attrs)).pName(pName(attrs)).studyDate(getSD(attrs))
                .outcome(null != ctx.getException() ? ctx.getException().getMessage() : null).build());
        AuditInfo iI = new AuditInfo(
                new BuildAuditInfo.Builder().sopCUID(sopCUID(attrs)).sopIUID(ctx.getSopInstanceUIDs()[0]).mppsUID(" ").build());
        writeSpoolFileStoreOrWadoRetrieve(fileName, i, iI);
    }

    private void buildSOPClassMap(HashMap<String, HashSet<String>> sopClassMap, String cuid, String iuid) {
        HashSet<String> iuids = sopClassMap.get(cuid);
        if (iuids == null) {
            iuids = new HashSet<>();
            sopClassMap.put(cuid, iuids);
        }
        iuids.add(iuid);
    }

    private void auditStoreOrWADORetrieve(AuditLogger auditLogger, SpoolFileReader readerObj, Calendar eventTime,
                                          AuditServiceUtils.EventType eventType) {
        HashSet<String> mppsUIDs = new HashSet<>();
        HashMap<String, HashSet<String>> sopClassMap = new HashMap<>();
        AuditInfo i = new AuditInfo(readerObj.getMainInfo());
        for (String line : readerObj.getInstanceLines()) {
            AuditInfo iI = new AuditInfo(line);
            buildSOPClassMap(sopClassMap, iI.getField(AuditInfo.SOP_CUID), iI.getField(AuditInfo.SOP_IUID));
            mppsUIDs.add(iI.getField(AuditInfo.MPPS_UID));
        }
        mppsUIDs.remove(" ");
        BuildEventIdentification ei = toBuildEventIdentification(eventType, i.getField(AuditInfo.OUTCOME), eventTime);
        BuildActiveParticipant[] activeParticipants = new BuildActiveParticipant[2];
        activeParticipants[0] = new BuildActiveParticipant.Builder(
                i.getField(AuditInfo.CALLING_AET),
                i.getField(AuditInfo.CALLING_HOST)).requester(true)
                .roleIDCode(eventType.source).build();
        activeParticipants[1] = new BuildActiveParticipant.Builder(
                i.getField(AuditInfo.CALLED_AET), getLocalHostName(auditLogger))
                .altUserID(AuditLogger.processID()).roleIDCode(eventType.destination).build();
        HashSet<SOPClass> sopC = new HashSet<>();
        for (Map.Entry<String, HashSet<String>> entry : sopClassMap.entrySet())
            sopC.add(getSOPC(null, entry.getKey(), entry.getValue().size()));
        ParticipantObjectContainsStudy pocs = getPocs(i.getField(AuditInfo.STUDY_UID));
        BuildParticipantObjectDescription desc = new BuildParticipantObjectDescription.Builder(sopC, pocs)
                .acc(getAccessions(i.getField(AuditInfo.ACC_NUM)))
                .mpps(AuditMessages.getMPPS(mppsUIDs.toArray(new String[mppsUIDs.size()]))).build();
        String lifecycle = (eventType == AuditServiceUtils.EventType.STORE_CREA
                || eventType == AuditServiceUtils.EventType.STORE_UPDT)
                ? AuditMessages.ParticipantObjectDataLifeCycle.OriginationCreation : null;
        BuildParticipantObjectIdentification poiStudy = new BuildParticipantObjectIdentification.Builder(
                i.getField(AuditInfo.STUDY_UID),
                AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID,
                AuditMessages.ParticipantObjectTypeCode.SystemObject, AuditMessages.ParticipantObjectTypeCodeRole.Report)
                .desc(getPODesc(desc)).detail(getPod(studyDate, i.getField(AuditInfo.STUDY_DATE))).lifeCycle(lifecycle).build();
        BuildParticipantObjectIdentification poiPatient = new BuildParticipantObjectIdentification.Builder(
                i.getField(AuditInfo.P_ID),
                AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                AuditMessages.ParticipantObjectTypeCode.Person, AuditMessages.ParticipantObjectTypeCodeRole.Patient)
                .name(i.getField(AuditInfo.P_NAME)).build();
        emitAuditMessage(auditLogger, ei, activeParticipants, poiStudy, poiPatient);
    }

    void spoolPartialRetrieve(RetrieveContext ctx) {
        List<String> failedList = Arrays.asList(ctx.failedSOPInstanceUIDs());
        Collection<InstanceLocations> instanceLocations = ctx.getMatches();
        HashSet<InstanceLocations> failed = new HashSet<>();
        HashSet<InstanceLocations> success = new HashSet<>();
        success.addAll(instanceLocations);
        for (InstanceLocations il : instanceLocations) {
            if (failedList.contains(il.getSopInstanceUID())) {
                failed.add(il);
                success.remove(il);
            }
        }
        spoolRetrieve(AuditServiceUtils.EventType.RTRV_TRF_E, ctx, failed);
        spoolRetrieve(AuditServiceUtils.EventType.RTRV_TRF_P, ctx, success);
    }

    void spoolRetrieve(AuditServiceUtils.EventType eventType, RetrieveContext ctx, Collection<InstanceLocations> il) {
        LinkedHashSet<Object> obj = new LinkedHashSet<>();

        HttpServletRequestInfo httpServletRequestInfo = ctx.getHttpServletRequestInfo();
        boolean failedIUIDShow = eventType.eventClass == AuditServiceUtils.EventClass.RETRIEVE_ERR && ctx.failedSOPInstanceUIDs().length > 0;
        String outcome = createOutcome(eventType, ctx);
        String warning = createWarning(eventType, ctx);

        BuildAuditInfo i = ctx.isLocalRequestor()
                            ? httpServletRequestInfo != null
                                ? new BuildAuditInfo.Builder()
                                    .callingAET(ctx.getHttpServletRequestInfo().requesterUserID)
                                    .callingHost(ctx.getRequestorHostName())
                                    .calledAET(ctx.getHttpServletRequestInfo().requestURI)
                                    .destAET(ctx.getDestinationAETitle())
                                    .destNapID(ctx.getDestinationHostName())
                                    .warning(warning)
                                    .outcome(outcome)
                                    .failedIUIDShow(failedIUIDShow)
                                    .isExport(true)
                                    .build()
                                : new BuildAuditInfo.Builder()
                                    .calledAET(ctx.getLocalAETitle())
                                    .destAET(ctx.getDestinationAETitle())
                                    .destNapID(ctx.getDestinationHostName())
                                    .warning(warning)
                                    .callingHost(ctx.getRequestorHostName())
                                    .outcome(outcome)
                                    .failedIUIDShow(failedIUIDShow)
                                    .isExport(true)
                                    .build()
                            : httpServletRequestInfo != null
                                ? new BuildAuditInfo.Builder()
                                    .calledAET(ctx.getHttpServletRequestInfo().requestURI)
                                    .destAET(ctx.getHttpServletRequestInfo().requesterUserID)
                                    .destNapID(ctx.getDestinationHostName())
                                    .warning(warning)
                                    .callingHost(ctx.getRequestorHostName())
                                    .outcome(outcome)
                                    .failedIUIDShow(failedIUIDShow)
                                    .build()
                                : new BuildAuditInfo.Builder()
                                    .calledAET(ctx.getLocalAETitle())
                                    .destAET(ctx.getDestinationAETitle())
                                    .destNapID(ctx.getDestinationHostName())
                                    .warning(warning)
                                    .callingHost(ctx.getRequestorHostName())
                                    .moveAET(ctx.getMoveOriginatorAETitle())
                                    .outcome(outcome)
                                    .failedIUIDShow(failedIUIDShow)
                                    .build();


        obj.add(new AuditInfo(i));

        addInstanceInfoForRetrieve(obj, il);
        addInstanceInfoForRetrieve(obj, ctx.getCStoreForwards());
        writeSpoolFile(eventType, obj);
    }

    private String createOutcome(AuditServiceUtils.EventType eventType, RetrieveContext ctx) {
        return (eventType.eventClass == AuditServiceUtils.EventClass.BEGIN_TRF && ctx.getException() != null)
                || eventType.eventClass == AuditServiceUtils.EventClass.RETRIEVE_ERR
                ? getFailOutcomeDesc(ctx) : null;
    }

    private String createWarning(AuditServiceUtils.EventType eventType, RetrieveContext ctx) {
        return eventType.eventClass == AuditServiceUtils.EventClass.RETRIEVE && ctx.warning() != 0
                ? ctx.warning() == ctx.getMatches().size() ? "Warnings on retrieve of all instances"
                : "Warnings on retrieve of " + ctx.warning() + " instances" : null;
    }

    private void addInstanceInfoForRetrieve(LinkedHashSet<Object> obj, Collection<InstanceLocations> instanceLocations) {
        for (InstanceLocations instanceLocation : instanceLocations) {
            Attributes attrs = instanceLocation.getAttributes();
            BuildAuditInfo iI = new BuildAuditInfo.Builder().studyUID(attrs.getString(Tag.StudyInstanceUID)).accNum(getAcc(attrs))
                    .sopCUID(sopCUID(attrs)).sopIUID(attrs.getString(Tag.SOPInstanceUID)).pID(getPID(attrs))
                    .pName(pName(attrs)).studyDate(getSD(attrs)).build();
            obj.add(new AuditInfo(iI));
        }
    }

    private void auditRetrieve(AuditLogger auditLogger, SpoolFileReader readerObj, Calendar eventTime, AuditServiceUtils.EventType eventType) {
        AuditInfo ri = new AuditInfo(readerObj.getMainInfo());
        BuildEventIdentification ei = toCustomBuildEventIdentification(eventType, ri.getField(AuditInfo.OUTCOME),
                ri.getField(AuditInfo.WARNING), eventTime);

        HashMap<String, AccessionNumSopClassInfo> study_accNumSOPClassInfo = new HashMap<>();
        String pID = device.getDeviceExtension(ArchiveDeviceExtension.class).auditUnknownPatientID();
        String pName = null;
        String studyDt = null;
        for (String line : readerObj.getInstanceLines()) {
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
        List<BuildParticipantObjectIdentification> pois = new ArrayList<>();
        for (Map.Entry<String, AccessionNumSopClassInfo> entry : study_accNumSOPClassInfo.entrySet()) {
            HashSet<SOPClass> sopC = new HashSet<>();
            for (Map.Entry<String, HashSet<String>> sopClassMap : entry.getValue().getSopClassMap().entrySet()) {
                if (ri.getField(AuditInfo.FAILED_IUID_SHOW) != null)
                    sopC.add(getSOPC(sopClassMap.getValue(), sopClassMap.getKey(), sopClassMap.getValue().size()));
                else
                    sopC.add(getSOPC(null, sopClassMap.getKey(), sopClassMap.getValue().size()));
            }
            BuildParticipantObjectDescription desc = new BuildParticipantObjectDescription.Builder(sopC, getPocs(entry.getKey()))
                    .acc(getAccessions(entry.getValue().getAccNum())).build();
            BuildParticipantObjectIdentification poi = new BuildParticipantObjectIdentification.Builder(
                    entry.getKey(), AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID,
                    AuditMessages.ParticipantObjectTypeCode.SystemObject, AuditMessages.ParticipantObjectTypeCodeRole.Report)
                    .desc(getPODesc(desc)).detail(getPod(studyDate, studyDt)).build();
            pois.add(poi);
        }
        BuildParticipantObjectIdentification poiPatient = new BuildParticipantObjectIdentification.Builder(
                pID, AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                AuditMessages.ParticipantObjectTypeCode.Person, AuditMessages.ParticipantObjectTypeCodeRole.Patient)
                .name(pName).build();
        pois.add(poiPatient);
        emitAuditMessage(auditLogger, ei, getApsForRetrieve(eventType, ri, auditLogger), pois.toArray(new BuildParticipantObjectIdentification[pois.size()]));
    }

    private BuildActiveParticipant[] getApsForRetrieve(AuditServiceUtils.EventType eventType, AuditInfo ri, AuditLogger auditLogger) {
        return ri.getField(AuditInfo.MOVEAET) != null
                ? getApsForMove(eventType, ri, auditLogger)
                : ri.getField(AuditInfo.IS_EXPORT) != null
                    ? getApsForExport(eventType, ri, auditLogger)
                    : getApsForGetOrWadoRS(eventType, ri, auditLogger);
    }

    private BuildActiveParticipant[] getApsForMove(AuditServiceUtils.EventType eventType, AuditInfo ri, AuditLogger auditLogger) {
        BuildActiveParticipant[] activeParticipants = new BuildActiveParticipant[3];
        activeParticipants[0] = new BuildActiveParticipant.Builder(ri.getField(AuditInfo.CALLED_AET),
                getLocalHostName(auditLogger)).altUserID(AuditLogger.processID())
                .roleIDCode(eventType.source).build();
        activeParticipants[1] = new BuildActiveParticipant.Builder(ri.getField(AuditInfo.DEST_AET),
                ri.getField(AuditInfo.DEST_NAP_ID)).roleIDCode(eventType.destination).build();
        activeParticipants[2] = new BuildActiveParticipant.Builder(ri.getField(AuditInfo.MOVEAET),
                ri.getField(AuditInfo.CALLING_HOST)).requester(true).build();
        return activeParticipants;
    }

    private BuildActiveParticipant[] getApsForExport(AuditServiceUtils.EventType eventType, AuditInfo ri, AuditLogger auditLogger) {
        BuildActiveParticipant[] activeParticipants = new BuildActiveParticipant[3];
        activeParticipants[0] = new BuildActiveParticipant.Builder(ri.getField(AuditInfo.DEST_AET),
                ri.getField(AuditInfo.DEST_NAP_ID)).roleIDCode(eventType.destination).build();
        if (ri.getField(AuditInfo.CALLING_AET) == null) {
            activeParticipants[1] = new BuildActiveParticipant.Builder(ri.getField(AuditInfo.CALLED_AET),
                    getLocalHostName(auditLogger)).altUserID(AuditLogger.processID()).requester(true)
                    .roleIDCode(eventType.source).build();
        }
        else {
            activeParticipants[1] = new BuildActiveParticipant.Builder(ri.getField(AuditInfo.CALLED_AET),
                    getLocalHostName(auditLogger)).altUserID(AuditLogger.processID())
                    .roleIDCode(eventType.source).build();
            activeParticipants[2] = new BuildActiveParticipant.Builder(
                    ri.getField(AuditInfo.CALLING_AET),
                    ri.getField(AuditInfo.CALLING_HOST))
                    .requester(true).build();
        }
        return activeParticipants;
    }

    private BuildActiveParticipant[] getApsForGetOrWadoRS(AuditServiceUtils.EventType eventType, AuditInfo ri, AuditLogger auditLogger) {
        BuildActiveParticipant[] activeParticipants = new BuildActiveParticipant[2];
        activeParticipants[0] = new BuildActiveParticipant.Builder(ri.getField(AuditInfo.CALLED_AET),
                getLocalHostName(auditLogger)).altUserID(AuditLogger.processID())
                .roleIDCode(eventType.source).build();
        activeParticipants[1] = new BuildActiveParticipant.Builder(ri.getField(AuditInfo.DEST_AET),
                ri.getField(AuditInfo.DEST_NAP_ID)).requester(true).roleIDCode(eventType.destination).build();
        return activeParticipants;
    }

    void spoolPatientRecord(PatientMgtContext ctx) {
        HashSet<AuditServiceUtils.EventType> et = AuditServiceUtils.EventType.forHL7(ctx);
        for (AuditServiceUtils.EventType eventType : et) {
            LinkedHashSet<Object> obj = new LinkedHashSet<>();
            String source = null;
            String dest = null;
            String hl7MessageType = null;
            HL7Segment msh = ctx.getHL7MessageHeader();
            if (ctx.getHttpRequest() != null) {
                source = KeycloakUtils.getUserName(ctx.getHttpRequest());
                dest = ctx.getCalledAET();
            }
            if (msh != null) {
                source = msh.getSendingApplicationWithFacility();
                dest = msh.getReceivingApplicationWithFacility();
                hl7MessageType = msh.getMessageType();
            }
            if (ctx.getAssociation() != null) {
                source = ctx.getAssociation().getCallingAET();
                dest = ctx.getAssociation().getCalledAET();
            }
            String pID = eventType == AuditServiceUtils.EventType.PAT_DELETE && ctx.getPreviousPatientID() != null
                    ? getPlainOrHashedPatientID(ctx.getPreviousPatientID().toString())
                    : ctx.getPatientID() != null ? getPlainOrHashedPatientID(ctx.getPatientID().toString())
                    : device.getDeviceExtension(ArchiveDeviceExtension.class).auditUnknownPatientID();
            String pName = eventType == AuditServiceUtils.EventType.PAT_DELETE && ctx.getPreviousAttributes() != null
                    ? StringUtils.maskEmpty(pName(ctx.getPreviousAttributes()), null)
                    : StringUtils.maskEmpty(pName(ctx.getAttributes()), null);
            String callingHost = ctx.getHttpRequest() != null
                    ? ctx.getHttpRequest().getRemoteAddr()
                    : msh != null || ctx.getAssociation() != null
                    ? ctx.getRemoteHostName() : null;
            BuildAuditInfo i = new BuildAuditInfo.Builder().callingHost(callingHost)
                    .callingAET(source).calledAET(dest).pID(pID).pName(pName)
                    .outcome(getOD(ctx.getException())).hl7MessageType(hl7MessageType).build();
            obj.add(new AuditInfo(i));
            writeSpoolFile(eventType, obj);
        }
    }

    private void auditPatientRecord(
            AuditLogger auditLogger, SpoolFileReader readerObj, Calendar eventTime, AuditServiceUtils.EventType et) {
        AuditInfo hl7I = new AuditInfo(readerObj.getMainInfo());
        BuildEventIdentification ei = toBuildEventIdentification(et, hl7I.getField(AuditInfo.OUTCOME), eventTime);
        BuildActiveParticipant[] activeParticipants = new BuildActiveParticipant[2];
        boolean userTriggered = et.source != null;
        if (userTriggered) {
            activeParticipants[0] = new BuildActiveParticipant.Builder(
                    hl7I.getField(AuditInfo.CALLED_AET),
                    getLocalHostName(auditLogger)).altUserID(AuditLogger.processID())
                    .roleIDCode(et.destination).build();
            activeParticipants[1] = new BuildActiveParticipant.Builder(hl7I.getField(AuditInfo.CALLING_AET),
               hl7I.getField(AuditInfo.CALLING_HOST)).requester(true).roleIDCode(et.source).build();
        } else
            activeParticipants[0] = new BuildActiveParticipant.Builder(
                    getAET(device),
                    getLocalHostName(auditLogger)).altUserID(AuditLogger.processID()).requester(true)
                    .roleIDCode(et.destination).build();
        BuildParticipantObjectIdentification poi = new BuildParticipantObjectIdentification.Builder(
                hl7I.getField(AuditInfo.P_ID), AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                AuditMessages.ParticipantObjectTypeCode.Person, AuditMessages.ParticipantObjectTypeCodeRole.Patient)
                .name(hl7I.getField(AuditInfo.P_NAME)).detail(getPod("HL7MessageType", hl7I.getField(AuditInfo.HL7_MESSAGE_TYPE))).build();
        emitAuditMessage(auditLogger, ei, activeParticipants, poi);
    }

    void spoolProcedureRecord(ProcedureContext ctx) {
        LinkedHashSet<Object> obj = new LinkedHashSet<>();
        BuildAuditInfo i = ctx.getHttpRequest() != null
                ? buildAuditInfoFORRestful(ctx)
                : ctx.getAssociation() != null ? buildAuditInfoForAssociation(ctx) : buildAuditInfoFORHL7(ctx);
        obj.add(new AuditInfo(i));
        writeSpoolFile(AuditServiceUtils.EventType.forProcedure(ctx.getEventActionCode()), obj);
    }

    private BuildAuditInfo buildAuditInfoForAssociation(ProcedureContext ctx) {
        Association as = ctx.getAssociation();
        Attributes attr = ctx.getAttributes();
        Patient p = ctx.getPatient();
        Attributes pAttr = p.getAttributes();
        BuildAuditInfo i = new BuildAuditInfo.Builder().callingHost(ctx.getRemoteHostName()).callingAET(as.getCallingAET())
                .calledAET(as.getCalledAET()).studyUID(ctx.getStudyInstanceUID()).accNum(getAcc(attr))
                .pID(getPID(pAttr)).pName(pName(pAttr)).outcome(getOD(ctx.getException())).studyDate(getSD(attr)).build();
        return i;
    }

    private BuildAuditInfo buildAuditInfoFORRestful(ProcedureContext ctx) {
        Attributes attr = ctx.getAttributes();
        HttpServletRequest req  = ctx.getHttpRequest();
        BuildAuditInfo i = new BuildAuditInfo.Builder().callingHost(ctx.getRemoteHostName())
                .callingAET(KeycloakUtils.getUserName(req))
                .calledAET(req.getRequestURI()).studyUID(ctx.getStudyInstanceUID())
                .accNum(getAcc(attr)).pID(getPID(attr)).pName(pName(ctx.getPatient().getAttributes()))
                .outcome(getOD(ctx.getException())).studyDate(getSD(attr)).build();
        return i;
    }

    private BuildAuditInfo buildAuditInfoFORHL7(ProcedureContext ctx) {
        Attributes attr = ctx.getAttributes();
        HL7Segment msh = ctx.getHL7MessageHeader();
        BuildAuditInfo i = new BuildAuditInfo.Builder().callingHost(ctx.getRemoteHostName())
                .callingAET(msh.getSendingApplicationWithFacility())
                .calledAET(msh.getReceivingApplicationWithFacility()).studyUID(ctx.getStudyInstanceUID())
                .accNum(getAcc(attr)).pID(getPID(attr)).pName(pName(ctx.getPatient().getAttributes()))
                .outcome(getOD(ctx.getException())).hl7MessageType(msh.getMessageType()).studyDate(getSD(attr)).build();
        return i;
    }

    void spoolProcedureRecord(StudyMgtContext ctx) {
        LinkedHashSet<Object> obj = new LinkedHashSet<>();
        String callingAET = KeycloakUtils.getUserName(ctx.getHttpRequest());
        Attributes sAttr = ctx.getAttributes();
        Attributes pAttr = ctx.getStudy() != null ? ctx.getStudy().getPatient().getAttributes() : null;
        BuildAuditInfo i = new BuildAuditInfo.Builder().callingHost(ctx.getHttpRequest().getRemoteHost()).callingAET(callingAET)
                .calledAET(ctx.getHttpRequest().getRequestURI()).studyUID(ctx.getStudyInstanceUID()).accNum(getAcc(sAttr))
                .pID(getPID(pAttr)).pName(pName(pAttr)).outcome(getOD(ctx.getException())).studyDate(getSD(sAttr)).build();
        obj.add(new AuditInfo(i));
        writeSpoolFile(AuditServiceUtils.EventType.forProcedure(ctx.getEventActionCode()), obj);
    }

    private void auditProcedureRecord(AuditLogger auditLogger, SpoolFileReader readerObj, Calendar eventTime, AuditServiceUtils.EventType et) {
        AuditInfo prI = new AuditInfo(readerObj.getMainInfo());
        BuildEventIdentification ei = toBuildEventIdentification(et, prI.getField(AuditInfo.OUTCOME), eventTime);
        BuildActiveParticipant[] activeParticipants = new BuildActiveParticipant[2];
        activeParticipants[0] = new BuildActiveParticipant.Builder(prI.getField(AuditInfo.CALLING_AET),
                prI.getField(AuditInfo.CALLING_HOST)).requester(true).build();
        activeParticipants[1] = new BuildActiveParticipant.Builder(prI.getField(AuditInfo.CALLED_AET),
                getLocalHostName(auditLogger)).altUserID(AuditLogger.processID()).build();
        ParticipantObjectContainsStudy pocs = getPocs(prI.getField(AuditInfo.STUDY_UID));
        BuildParticipantObjectDescription desc = new BuildParticipantObjectDescription.Builder(null, pocs)
                .acc(getAccessions(prI.getField(AuditInfo.ACC_NUM))).build();
        BuildParticipantObjectIdentification poiStudy = new BuildParticipantObjectIdentification.Builder(
                prI.getField(AuditInfo.STUDY_UID), AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID,
                AuditMessages.ParticipantObjectTypeCode.SystemObject, AuditMessages.ParticipantObjectTypeCodeRole.Report)
                .desc(getPODesc(desc)).detail(getPod(studyDate, prI.getField(AuditInfo.STUDY_DATE))).build();
        BuildParticipantObjectIdentification poiPatient = new BuildParticipantObjectIdentification.Builder(
                prI.getField(AuditInfo.P_ID), AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                AuditMessages.ParticipantObjectTypeCode.Person, AuditMessages.ParticipantObjectTypeCodeRole.Patient)
                .name(prI.getField(AuditInfo.P_NAME)).detail(getPod("HL7MessageType", prI.getField(AuditInfo.HL7_MESSAGE_TYPE))).build();
        emitAuditMessage(auditLogger, ei, activeParticipants, poiStudy, poiPatient);
    }

    void spoolProvideAndRegister(ExportContext ctx) {
        LinkedHashSet<Object> obj = new LinkedHashSet<>();
        Attributes xdsiManifest = ctx.getXDSiManifest();
        if (xdsiManifest == null)
            return;
        URI dest = ctx.getExporter().getExporterDescriptor().getExportURI();
        String schemeSpecificPart = dest.getSchemeSpecificPart();
        HttpServletRequestInfo httpServletRequestInfo = ctx.getHttpServletRequestInfo();
        String destHost = schemeSpecificPart.substring(schemeSpecificPart.indexOf("://")+3, schemeSpecificPart.lastIndexOf(":"));
        BuildAuditInfo i = new BuildAuditInfo.Builder()
                .callingAET(httpServletRequestInfo.requesterUserID)
                .callingHost(httpServletRequestInfo.requesterHost)
                .calledAET(httpServletRequestInfo.requestURI)
                .destAET(dest.toString())
                .destNapID(destHost)
                .outcome(null != ctx.getException() ? ctx.getException().getMessage() : null)
                .pID(getPID(xdsiManifest))
                .pName(pName(xdsiManifest))
                .submissionSetUID(ctx.getSubmissionSetUID()).build();
        obj.add(new AuditInfo(i));
        writeSpoolFile(AuditServiceUtils.EventType.PROV_REGIS, obj);
    }

    private void auditProvideAndRegister(AuditLogger auditLogger, SpoolFileReader readerObj, Calendar eventTime, AuditServiceUtils.EventType et) {
        AuditInfo ai = new AuditInfo(readerObj.getMainInfo());
        BuildEventIdentification ei = toBuildEventIdentification(et, ai.getField(AuditInfo.OUTCOME), eventTime);
        BuildActiveParticipant[] activeParticipants = new BuildActiveParticipant[3];
        activeParticipants[0] = new BuildActiveParticipant.Builder(ai.getField(AuditInfo.DEST_AET),
                ai.getField(AuditInfo.DEST_NAP_ID)).roleIDCode(et.destination).build();
        if (ai.getField(AuditInfo.CALLING_AET) != null) {
            activeParticipants[1] = new BuildActiveParticipant.Builder(ai.getField(AuditInfo.CALLED_AET),
                    getLocalHostName(auditLogger)).altUserID(AuditLogger.processID()).roleIDCode(et.source).build();
            activeParticipants[2] = new BuildActiveParticipant.Builder(
                    ai.getField(AuditInfo.CALLING_AET),
                    ai.getField(AuditInfo.CALLING_HOST))
                    .requester(true).build();
        } else
            activeParticipants[1] = new BuildActiveParticipant.Builder(ai.getField(AuditInfo.CALLED_AET),
                    getLocalHostName(auditLogger)).altUserID(AuditLogger.processID()).requester(true).roleIDCode(et.source).build();

        BuildParticipantObjectIdentification poiPatient = new BuildParticipantObjectIdentification.Builder(
                ai.getField(AuditInfo.P_ID), AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                AuditMessages.ParticipantObjectTypeCode.Person, AuditMessages.ParticipantObjectTypeCodeRole.Patient)
                .build();
        BuildParticipantObjectIdentification poiSubmissionSet = new BuildParticipantObjectIdentification.Builder(
                ai.getField(AuditInfo.SUBMISSION_SET_UID), AuditMessages.ParticipantObjectIDTypeCode.IHE_XDS_METADATA,
                AuditMessages.ParticipantObjectTypeCode.SystemObject, AuditMessages.ParticipantObjectTypeCodeRole.Job)
                .build();
        emitAuditMessage(auditLogger, ei, activeParticipants, poiPatient, poiSubmissionSet);
    }

    void spoolStgCmt(StgCmtEventInfo stgCmtEventInfo) {
        try {
            ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
            String callingAET = stgCmtEventInfo.getRequest() != null
                                    ? KeycloakUtils.getUserName(stgCmtEventInfo.getRequest())
                                    : stgCmtEventInfo.getRemoteAET();
            String calledAET = stgCmtEventInfo.getRequest() != null
                                ? stgCmtEventInfo.getRequest().getRequestURI()
                                : stgCmtEventInfo.getLocalAET();
            ApplicationEntity remoteAE = stgCmtEventInfo.getRemoteAET() != null
                    ? aeCache.findApplicationEntity(stgCmtEventInfo.getRemoteAET()) : null;
            String callingHost = remoteAE != null
                    ? remoteAE.getConnections().get(0).getHostname() : stgCmtEventInfo.getRequest().getRemoteHost();
            Attributes eventInfo = stgCmtEventInfo.getExtendedEventInfo();
            Sequence failed = eventInfo.getSequence(Tag.FailedSOPSequence);
            Sequence success = eventInfo.getSequence(Tag.ReferencedSOPSequence);
            String pID = eventInfo.getString(Tag.PatientID) != null ? getPID(eventInfo) : arcDev.auditUnknownPatientID();
            String studyUID = eventInfo.getStrings(Tag.StudyInstanceUID) != null
                    ? buildStrings(eventInfo.getStrings(Tag.StudyInstanceUID)) : arcDev.auditUnknownStudyInstanceUID();
            if (failed != null && !failed.isEmpty()) {
                Set<String> failureReasons = new HashSet<>();
                Set<AuditInfo> aiSet = new HashSet<>();
                LinkedHashSet<Object> objs = new LinkedHashSet<>();
                for (Attributes item : failed) {
                    BuildAuditInfo ii = new BuildAuditInfo.Builder()
                            .sopCUID(item.getString(Tag.ReferencedSOPClassUID))
                            .sopIUID(item.getString(Tag.ReferencedSOPInstanceUID)).build();
                    String outcome = item.getInt(Tag.FailureReason, 0) == Status.NoSuchObjectInstance
                            ? "NoSuchObjectInstance" : item.getInt(Tag.FailureReason, 0) == Status.ClassInstanceConflict
                            ? "ClassInstanceConflict" : "ProcessingFailure";
                    failureReasons.add(outcome);
                    aiSet.add(new AuditInfo(ii));
                }
                BuildAuditInfo i = new BuildAuditInfo.Builder().callingAET(callingAET).callingHost(callingHost)
                        .calledAET(calledAET).pID(pID).pName(pName(eventInfo)).studyUID(studyUID)
                        .outcome(buildStrings(failureReasons.toArray(new String[failureReasons.size()]))).build();
                objs.add(new AuditInfo(i));
                for (AuditInfo ai : aiSet)
                    objs.add(ai);
                writeSpoolFile(AuditServiceUtils.EventType.STG_CMT__E, objs);
            }
            if (success != null && !success.isEmpty()) {
                LinkedHashSet<Object> objs = new LinkedHashSet<>();
                BuildAuditInfo i = new BuildAuditInfo.Builder().callingAET(callingAET)
                        .callingHost(callingHost).calledAET(calledAET).pID(pID)
                        .pName(pName(eventInfo)).studyUID(studyUID).build();
                objs.add(new AuditInfo(i));
                for (Attributes item : success) {
                    BuildAuditInfo ii = new BuildAuditInfo.Builder().sopCUID(item.getString(Tag.ReferencedSOPClassUID))
                            .sopIUID(item.getString(Tag.ReferencedSOPInstanceUID)).build();
                    objs.add(new AuditInfo(ii));
                }
                writeSpoolFile(AuditServiceUtils.EventType.STG_CMT__P, objs);
            }
        } catch (ConfigurationException e) {
            LOG.error(e.getMessage(), stgCmtEventInfo.getRemoteAET());
        }
    }

    private void auditStorageCommit(AuditLogger auditLogger, SpoolFileReader readerObj, Calendar eventTime, AuditServiceUtils.EventType et) {
        AuditInfo stgCmtI = new AuditInfo(readerObj.getMainInfo());
        BuildEventIdentification ei = toBuildEventIdentification(et, stgCmtI.getField(AuditInfo.OUTCOME), eventTime);
        BuildActiveParticipant[] activeParticipants = new BuildActiveParticipant[2];
        activeParticipants[0] = new BuildActiveParticipant.Builder(stgCmtI.getField(AuditInfo.CALLED_AET),
                getLocalHostName(auditLogger)).altUserID(AuditLogger.processID())
                .roleIDCode(et.destination).build();
        activeParticipants[1] = new BuildActiveParticipant.Builder(stgCmtI.getField(AuditInfo.CALLING_AET),
                stgCmtI.getField(AuditInfo.CALLING_HOST)).requester(true).roleIDCode(et.source).build();
        BuildParticipantObjectIdentification poiPat = new BuildParticipantObjectIdentification.Builder(
                stgCmtI.getField(AuditInfo.P_ID), AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                AuditMessages.ParticipantObjectTypeCode.Person, AuditMessages.ParticipantObjectTypeCodeRole.Patient)
                .name(stgCmtI.getField(AuditInfo.P_NAME)).build();
        String[] studyUIDs = StringUtils.split(stgCmtI.getField(AuditInfo.STUDY_UID), ';');
        ParticipantObjectContainsStudy pocs = getPocs(studyUIDs);
        HashMap<String, HashSet<String>> sopClassMap = new HashMap<>();
        for (String line : readerObj.getInstanceLines()) {
            AuditInfo ii = new AuditInfo(line);
            buildSOPClassMap(sopClassMap, ii.getField(AuditInfo.SOP_CUID), ii.getField(AuditInfo.SOP_IUID));
        }
        HashSet<SOPClass> sopC = new HashSet<>();
        if (studyUIDs.length>1 || stgCmtI.getField(AuditInfo.OUTCOME) != null)
            for (Map.Entry<String, HashSet<String>> entry : sopClassMap.entrySet())
                sopC.add(getSOPC(entry.getValue(), entry.getKey(), entry.getValue().size()));
        else
            for (Map.Entry<String, HashSet<String>> entry : sopClassMap.entrySet())
                sopC.add(getSOPC(null, entry.getKey(), entry.getValue().size()));
        BuildParticipantObjectDescription poDesc = new BuildParticipantObjectDescription.Builder(sopC, pocs).build();
        BuildParticipantObjectIdentification poiStudy = new BuildParticipantObjectIdentification.Builder(studyUIDs[0],
                AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID,
                AuditMessages.ParticipantObjectTypeCode.SystemObject, AuditMessages.ParticipantObjectTypeCodeRole.Report)
                .desc(getPODesc(poDesc)).lifeCycle(AuditMessages.ParticipantObjectDataLifeCycle.Verification).build();
        emitAuditMessage(auditLogger, ei, activeParticipants, poiStudy, poiPat);
    }

    private String buildStrings(String[] strings) {
        StringBuilder b = new StringBuilder();
        b.append(strings[0]);
        for (int i = 1; i < strings.length; i++)
            b.append(';').append(strings[i]);
        return b.toString();
    }

    private BuildAuditInfo getAIStoreCtx(StoreContext ctx) {
        StoreSession ss = ctx.getStoreSession();
        HttpServletRequest req = ss.getHttpRequest();
        Attributes attr = ctx.getAttributes();
        String callingHost = ss.getRemoteHostName();
        String callingAET = ss.getCallingAET() != null ? ss.getCallingAET()
                : req != null ? KeycloakUtils.getUserName(req) : callingHost;
        if (callingAET == null && callingHost == null)
            callingAET = ss.toString();
        String outcome = null != ctx.getException() ? null != ctx.getRejectionNote()
                ? ctx.getRejectionNote().getRejectionNoteCode().getCodeMeaning() + " - " + ctx.getException().getMessage()
                : getOD(ctx.getException()) : null;
        String warning = ctx.getException() == null && null != ctx.getRejectionNote()
                ? ctx.getRejectionNote().getRejectionNoteCode().getCodeMeaning() : null;
        BuildAuditInfo i = new BuildAuditInfo.Builder().callingHost(callingHost).callingAET(callingAET)
                .calledAET(req != null ? req.getRequestURI() : ss.getCalledAET()).studyUID(ctx.getStudyInstanceUID())
                .accNum(getAcc(attr)).pID(getPID(attr)).pName(pName(attr))
                .outcome(outcome).warning(warning).studyDate(getSD(attr)).build();
        return i;
    }

    private String getFileName(AuditServiceUtils.EventType et, String callingAET, String calledAET, String studyIUID) {
        return String.valueOf(et) + '-' + callingAET + '-' + calledAET + '-' + studyIUID;
    }

    private String getFailOutcomeDesc(RetrieveContext ctx) {
        return null != ctx.getException()
                ? ctx.getException().getMessage() != null ? ctx.getException().getMessage() : ctx.getException().toString()
                : (ctx.failedSOPInstanceUIDs().length > 0 && (ctx.completed() == 0 && ctx.warning() == 0))
                ? "Unable to perform sub-operations on all instances"
                : (ctx.failedSOPInstanceUIDs().length > 0 && !(ctx.completed() == 0 && ctx.warning() == 0))
                ? "Retrieve of " + ctx.failed() + " objects failed" : null;
    }

    private String getSD(Attributes attr) {
        return attr != null ? attr.getString(Tag.StudyDate) : null;
    }

    private String getAcc(Attributes attr) {
        return attr != null ? attr.getString(Tag.AccessionNumber) : null;
    }

    private String sopCUID(Attributes attrs) {
        return attrs != null ? attrs.getString(Tag.SOPClassUID) : null;
    }

    private String getPID(Attributes attrs) {
        return attrs != null
                ? attrs.getString(Tag.PatientID) != null
                ? getPlainOrHashedPatientID(IDWithIssuer.pidOf(attrs).toString())
                : device.getDeviceExtension(ArchiveDeviceExtension.class).auditUnknownPatientID() : null;
    }

    private String pName(Attributes attr) {
        if (attr != null && attr.getString(Tag.PatientName) != null) {
            ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
            ShowPatientInfo showPatientInfo = arcDev.showPatientInfoInAuditLog();
            StringBuilder sb = new StringBuilder(256);
            if (showPatientInfo != ShowPatientInfo.PLAIN_TEXT)
                sb.append(attr.getString(Tag.PatientName).hashCode());
            else
                sb.append(attr.getString(Tag.PatientName));
            return sb.toString();
        }
        return null;
    }

    private String getPlainOrHashedPatientID(String pID) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        ShowPatientInfo showPatientInfo = arcDev.showPatientInfoInAuditLog();
        StringBuilder sb = new StringBuilder(256);
        if (showPatientInfo == ShowPatientInfo.HASH_NAME_AND_ID)
            sb.append(pID.hashCode());
        else
            sb.append(pID);
        return sb.toString();
    }

    private String getOD(Exception e) {
        return e != null ? e.getMessage() : null;
    }

    private ParticipantObjectDetail getPod(String type, String value) {
        return value != null ? AuditMessages.createParticipantObjectDetail(type, value.getBytes()) : null;
    }

    private ParticipantObjectContainsStudy getPocs(String... studyUIDs) {
        return AuditMessages.getPocs(studyUIDs);
    }

    private ParticipantObjectDescription getPODesc(BuildParticipantObjectDescription desc) {
        return AuditMessages.getPODesc(desc);
    }

    private HashSet<Accession> getAccessions(String accNum) {
        return AuditMessages.getAccessions(accNum);
    }

    private HashSet<SOPClass> getSopClasses(HashSet<String> instanceLines) {
        HashSet<SOPClass> sopC = new HashSet<>();
        for (String line : instanceLines) {
            AuditInfo ii = new AuditInfo(line);
            sopC.add(getSOPC(null, ii.getField(AuditInfo.SOP_CUID),
                    Integer.parseInt(ii.getField(AuditInfo.SOP_IUID))));
        }
        return sopC;
    }

    private SOPClass getSOPC(HashSet<String> instances, String uid, Integer numI) {
        return AuditMessages.getSOPC(instances, uid, numI);
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

    private String getAET(Device device) {
        return AuditMessages.getAET(
                device.getApplicationAETitles().toArray(new String[device.getApplicationAETitles().size()]));
    }

    private String getLocalHostName(AuditLogger log) {
        return log.getConnections().get(0).getHostname();
    }

    private void writeSpoolFile(AuditServiceUtils.EventType eventType, LinkedHashSet<Object> obj) {
        if (obj.isEmpty()) {
            LOG.warn("Attempt to write empty file : ", eventType);
            return;
        }
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        boolean auditAggregate = arcDev.isAuditAggregate();
        AuditLoggerDeviceExtension ext = device.getDeviceExtension(AuditLoggerDeviceExtension.class);
        for (AuditLogger auditLogger : ext.getAuditLoggers()) {
            if (auditLogger.isInstalled()) {
                Path dir = Paths.get(StringUtils.replaceSystemProperties(arcDev.getAuditSpoolDirectory()),
                        auditLogger.getCommonName().replaceAll(" ", "_"));
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

    private void writeSpoolFileStoreOrWadoRetrieve(String fileName, Object patStudyInfo, Object instanceInfo) {
        if (patStudyInfo == null && instanceInfo == null) {
            LOG.warn("Attempt to write empty file : " + fileName);
            return;
        }
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        boolean auditAggregate = arcDev.isAuditAggregate();
        AuditLoggerDeviceExtension ext = device.getDeviceExtension(AuditLoggerDeviceExtension.class);
        for (AuditLogger auditLogger : ext.getAuditLoggers()) {
            if (auditLogger.isInstalled()) {
                Path dir = Paths.get(StringUtils.replaceSystemProperties(arcDev.getAuditSpoolDirectory()),
                        auditLogger.getCommonName().replaceAll(" ", "_"));
                Path file = dir.resolve(fileName);
                boolean append = Files.exists(file);
                try {
                    if (!append)
                        Files.createDirectories(dir);
                    try (SpoolFileWriter writer = new SpoolFileWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                            append ? StandardOpenOption.APPEND : StandardOpenOption.CREATE_NEW))) {
                        if (!append) {
                            writer.writeLine(patStudyInfo);
                        }
                        writer.writeLine(instanceInfo);
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
            obj.add(new AuditInfo(new BuildAuditInfo.Builder().sopCUID(entry.getKey())
                    .sopIUID(String.valueOf(entry.getValue().size())).build()));
        }
        return obj;
    }

    private void emitAuditMessage(
            AuditLogger logger, BuildEventIdentification buildEventIdentification, BuildActiveParticipant[] buildActiveParticipants,
            BuildParticipantObjectIdentification... buildParticipantObjectIdentification) {
        AuditMessage msg = AuditMessages.createMessage(buildEventIdentification, buildActiveParticipants, buildParticipantObjectIdentification);
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

    private EventIdentification getEI(AuditServiceUtils.EventType et, String desc, Calendar t) {
        BuildEventIdentification ei =  new BuildEventIdentification.Builder(
                    et.eventID, et.eventActionCode, t, getEOI(desc)).outcomeDesc(desc).eventTypeCode(et.eventTypeCode).build();
        return AuditMessages.getEI(ei);
    }

    private BuildEventIdentification toCustomBuildEventIdentification(AuditServiceUtils.EventType et, String failureDesc, String warningDesc, Calendar t) {
        if (failureDesc != null)
            return new BuildEventIdentification.Builder(
                    et.eventID, et.eventActionCode, t, getEOI(failureDesc)).outcomeDesc(failureDesc).eventTypeCode(et.eventTypeCode).build();
        else
            return new BuildEventIdentification.Builder(
                    et.eventID, et.eventActionCode, t, AuditMessages.EventOutcomeIndicator.Success)
                    .outcomeDesc(warningDesc).build();
    }

    private BuildEventIdentification toBuildEventIdentification(AuditServiceUtils.EventType et, String desc, Calendar t) {
        return new BuildEventIdentification.Builder(
                et.eventID, et.eventActionCode, t, getEOI(desc)).outcomeDesc(desc).eventTypeCode(et.eventTypeCode).build();
    }
}
