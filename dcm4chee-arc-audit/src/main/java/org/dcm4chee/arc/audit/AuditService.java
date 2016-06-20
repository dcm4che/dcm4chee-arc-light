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
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.delete.StudyDeleteContext;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.entity.Study;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.procedure.ProcedureContext;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.retrieve.InstanceLocations;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreSession;
import org.dcm4chee.arc.study.StudyMgtContext;
import org.keycloak.KeycloakSecurityContext;
import org.keycloak.adapters.RefreshableKeycloakSecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.net.Socket;
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
    private final String JBOSS_SERVER_TEMP = "${jboss.server.temp}";
    private final String studyDate = "StudyDate";
    private final String keycloakClassName = "org.keycloak.KeycloakSecurityContext";
    private final String noValue = "<none>";
    @Inject
    private Device device;

    private AuditLogger log() {
        return device.getDeviceExtension(AuditLogger.class);
    }

    boolean isAuditInstalled() {
        return log() != null && log().isInstalled();
    }

    void aggregateAuditMessage(Path path) throws IOException {
        AuditServiceUtils.EventType eventType = AuditServiceUtils.EventType.fromFile(path);
        SpoolFileReader readerObj = eventType.eventClass != AuditServiceUtils.EventClass.QUERY
                ? new SpoolFileReader(path) : null;
        Calendar eventTime = getEventTime(path, log());
        switch (eventType.eventClass) {
            case CONN_REJECT:
                auditConnectionRejected(new AuditInfo(readerObj.getMainInfo()), eventType);
                break;
            case STORE_WADOR:
                auditStoreOrWADORetrieve(readerObj, eventTime, eventType);
                break;
            case RETRIEVE:
                auditRetrieve(readerObj, eventTime, eventType);
                break;
            case DELETE:
            case PERM_DELETE:
                auditDeletion(readerObj, eventType);
                break;
            case QUERY:
                auditQuery(path, eventType);
                break;
            case HL7:
                auditPatientRecord(new AuditInfo(readerObj.getMainInfo()), eventType);
                break;
            case PROC_STUDY:
                auditProcedureRecord(new AuditInfo(readerObj.getMainInfo()), eventType);
                break;
        }
    }

    void auditApplicationActivity(AuditServiceUtils.EventType eventType, HttpServletRequest req) {
        EventIdentification ei = getEI(eventType, null, log().timeStamp());
        BuildActiveParticipant ap1 = new BuildActiveParticipant.Builder(getAET(device),
                getLocalHostName(log())).altUserID(AuditLogger.processID()).requester(false).
                roleIDCode(AuditMessages.RoleIDCode.Application).build();
        BuildActiveParticipant ap2 = null;
        if (req != null) {
            ap2 = new BuildActiveParticipant.Builder(
                    req.getAttribute(keycloakClassName) != null
                            ? getPreferredUsername(req) : req.getRemoteAddr(), req.getRemoteAddr()).
                    requester(true).roleIDCode(AuditMessages.RoleIDCode.ApplicationLauncher).build();
        }
        emitAuditMessage(ei, req != null ? getApList(ap1, ap2) : getApList(ap1), null, log());
    }

    void spoolInstancesDeleted(StoreContext ctx) {
        Attributes attrs = ctx.getAttributes();
        HashMap<String, HashSet<String>> sopClassMap = new HashMap<>();
        for (Attributes studyRef : attrs.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence)) {
            for (Attributes seriesRef : studyRef.getSequence(Tag.ReferencedSeriesSequence)) {
                for (Attributes sopRef : seriesRef.getSequence(Tag.ReferencedSOPSequence)) {
                    String cuid = sopRef.getString(Tag.ReferencedSOPClassUID);
                    HashSet<String> iuids = sopClassMap.get(cuid);
                    if (iuids == null) {
                        iuids = new HashSet<>();
                        sopClassMap.put(cuid, iuids);
                    }
                    iuids.add(sopRef.getString(Tag.ReferencedSOPInstanceUID));
                }
            }
        }
        writeSpoolFile(String.valueOf(AuditServiceUtils.EventType.RJN_DELETE),
                getDeletionObjsForSpooling(sopClassMap, new AuditInfo(getAIStoreCtx(ctx))));
    }

    void spoolStudyDeleted(StudyDeleteContext ctx) {
        HashMap<String, HashSet<String>> sopClassMap = new HashMap<>();
        for (org.dcm4chee.arc.entity.Instance i : ctx.getInstances()) {
            String cuid = i.getSopClassUID();
            HashSet<String> iuids = sopClassMap.get(cuid);
            if (iuids == null) {
                iuids = new HashSet<>();
                sopClassMap.put(cuid, iuids);
            }
            iuids.add(i.getSopInstanceUID());
        }
        Study s = ctx.getStudy();
        Patient p = ctx.getPatient();
        BuildAuditInfo i = new BuildAuditInfo.Builder().studyUID(s.getStudyInstanceUID()).accNum(s.getAccessionNumber())
                            .pID(getPID(p.getAttributes())).outcome(getOD(ctx.getException())).studyDate(s.getStudyDate())
                            .pName(null != ctx.getPatient().getPatientName().toString() ? ctx.getPatient().getPatientName().toString() : null).build();
        writeSpoolFile(String.valueOf(AuditServiceUtils.EventType.PERM_DELET),
                getDeletionObjsForSpooling(sopClassMap, new AuditInfo(i)));
    }

    private void auditDeletion(SpoolFileReader readerObj, AuditServiceUtils.EventType eventType) {
        AuditInfo dI = new AuditInfo(readerObj.getMainInfo());
        EventIdentification ei = getEI(eventType, dI.getField(AuditInfo.OUTCOME), log().timeStamp());
        BuildActiveParticipant ap1 = null;
        if (eventType.eventClass == AuditServiceUtils.EventClass.DELETE) {
            ap1 = new BuildActiveParticipant.Builder(
                    dI.getField(AuditInfo.CALLING_AET), dI.getField(AuditInfo.CALLING_HOST))
                    .requester(eventType.isSource).build();
        }
        BuildActiveParticipant ap2 = new BuildActiveParticipant.Builder(
                eventType.eventClass == AuditServiceUtils.EventClass.DELETE
                        ? dI.getField(AuditInfo.CALLED_AET) : getAET(device),
                getLocalHostName(log())).altUserID(AuditLogger.processID())
                .requester(eventType.eventClass != AuditServiceUtils.EventClass.DELETE || eventType.isDest).build();
        ParticipantObjectContainsStudy pocs = getPocs(dI.getField(AuditInfo.STUDY_UID));
        BuildParticipantObjectDescription desc = new BuildParticipantObjectDescription.Builder(
                getSopClasses(readerObj.getInstanceLines()), pocs)
                .acc(eventType.eventClass == AuditServiceUtils.EventClass.PERM_DELETE
                        ? getAccessions(dI.getField(AuditInfo.ACC_NUM)) : null).build();
        BuildParticipantObjectIdentification poi1 = new BuildParticipantObjectIdentification.Builder(
                dI.getField(AuditInfo.STUDY_UID), AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID,
                AuditMessages.ParticipantObjectTypeCode.SystemObject,AuditMessages.ParticipantObjectTypeCodeRole.Report)
                .desc(getPODesc(desc)).detail(getPod(studyDate, dI.getField(AuditInfo.STUDY_DATE))).build();
        BuildParticipantObjectIdentification poi2 = new BuildParticipantObjectIdentification.Builder(
                dI.getField(AuditInfo.P_ID), AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                AuditMessages.ParticipantObjectTypeCode.Person, AuditMessages.ParticipantObjectTypeCodeRole.Patient)
                .name(dI.getField(AuditInfo.P_NAME)).build();
        emitAuditMessage(ei, eventType.eventClass == AuditServiceUtils.EventClass.DELETE ? getApList(ap1, ap2) : getApList(ap2),
                getPoiList(poi1, poi2), log());
    }

    void spoolConnectionRejected(Connection conn, Socket s, Throwable e) {
        LinkedHashSet<Object> obj = new LinkedHashSet<>();
        BuildAuditInfo i = new BuildAuditInfo.Builder().callingHost(s.getRemoteSocketAddress().toString())
                                .calledHost(conn.getHostname()).outcome(e.getMessage()).build();
        obj.add(new AuditInfo(i));
        writeSpoolFile(String.valueOf(AuditServiceUtils.EventType.CONN__RJCT), obj);
    }

    private void auditConnectionRejected(AuditInfo crI, AuditServiceUtils.EventType eventType) {
        EventIdentification ei = getEI(eventType, crI.getField(AuditInfo.OUTCOME), log().timeStamp());
        BuildActiveParticipant ap1 = new BuildActiveParticipant.Builder(getAET(device),
                crI.getField(AuditInfo.CALLED_HOST)).altUserID(AuditLogger.processID()).requester(false).build();
        String userID, napID;
        userID = napID = crI.getField(AuditInfo.CALLING_HOST);
        BuildActiveParticipant ap2 = new BuildActiveParticipant.Builder(userID, napID).requester(true).build();
        BuildParticipantObjectIdentification poi = new BuildParticipantObjectIdentification.Builder(
                crI.getField(AuditInfo.CALLING_HOST), AuditMessages.ParticipantObjectIDTypeCode.NodeID,
                AuditMessages.ParticipantObjectTypeCode.SystemObject, null).build();
        emitAuditMessage(ei, getApList(ap1, ap2), getPoiList(poi), log());
    }

    void spoolQuery(QueryContext ctx) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        boolean auditAggregate = arcDev.isAuditAggregate();
        Path dir = Paths.get(StringUtils.replaceSystemProperties(
                auditAggregate? arcDev.getAuditSpoolDirectory() : JBOSS_SERVER_TEMP));
        AuditServiceUtils.EventType eventType = AuditServiceUtils.EventType.forQuery(ctx);
        try {
            Files.createDirectories(dir);
            Path file = Files.createTempFile(dir, String.valueOf(eventType), null);
            try (BufferedOutputStream out = new BufferedOutputStream(
                    Files.newOutputStream(file, StandardOpenOption.APPEND))) {
                String callingAET = ctx.getCallingAET() != null ? ctx.getCallingAET()
                                : ctx.getHttpRequest().getAttribute(keycloakClassName) != null
                                ? getPreferredUsername(ctx.getHttpRequest())
                                : ctx.getRemoteHostName();
                BuildAuditInfo i = new BuildAuditInfo.Builder().callingHost(ctx.getRemoteHostName())
                                    .callingAET(callingAET).calledAET(ctx.getHttpRequest() != null
                                    ? ctx.getHttpRequest().getRequestURI() : ctx.getCalledAET())
                                    .queryPOID(ctx.getSOPClassUID() != null ? ctx.getSOPClassUID()
                                            : ctx.getQueryRetrieveLevel().toString())
                                    .queryString(ctx.getHttpRequest() != null ? ctx.getHttpRequest().getRequestURI()
                                            + ctx.getHttpRequest().getQueryString() : null).build();
                new DataOutputStream(out).writeUTF(new AuditInfo(i).toString());
                if (ctx.getAssociation() != null) {
                    try (DicomOutputStream dos = new DicomOutputStream(out, UID.ImplicitVRLittleEndian)) {
                        dos.writeDataset(null, ctx.getQueryKeys());
                    } catch (Exception e) {
                        LOG.warn("Failed to create DicomOutputStream : ", e);
                    }
                }
            }
            if (!auditAggregate)
                aggregateAuditMessage(file);
        } catch (Exception e) {
            LOG.warn("Failed to write to Audit Spool File - {} ", e);
        }
    }

    private void auditQuery(Path file, AuditServiceUtils.EventType eventType) throws IOException {
        AuditInfo qrI;
        List<ActiveParticipant> apList;
        List<ParticipantObjectIdentification> poiList;
        EventIdentification ei = getEI(eventType, null, log().timeStamp());
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            qrI = new AuditInfo(new DataInputStream(in).readUTF());
            BuildActiveParticipant ap1 = new BuildActiveParticipant.Builder(qrI.getField(AuditInfo.CALLING_AET),
                    qrI.getField(AuditInfo.CALLING_HOST)).requester(eventType.isSource).roleIDCode(eventType.source)
                    .build();
            BuildActiveParticipant ap2 = new BuildActiveParticipant.Builder(qrI.getField(AuditInfo.CALLED_AET),
                    getLocalHostName(log())).altUserID(AuditLogger.processID())
                    .requester(eventType.isDest).roleIDCode(eventType.destination).build();
            apList = getApList(ap1, ap2);
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
                        AuditMessages.ParticipantObjectTypeCodeRole.Query).query(data)
                        .detail(getPod("TransferSyntax", UID.ImplicitVRLittleEndian)).build();
            }
            poiList = getPoiList(poi);
        }
        emitAuditMessage(ei, apList, poiList, log());
    }

    void spoolInstanceStoredOrWadoRetrieve(StoreContext sCtx, RetrieveContext rCtx) {
        String fileName;
        Attributes attrs = new Attributes();
        AuditServiceUtils.EventType eventType;
        if (sCtx != null) {
            eventType = AuditServiceUtils.EventType.forInstanceStored(sCtx);
            if (eventType == null)
                return; // no audit message for duplicate received instance
            String callingAET = sCtx.getStoreSession().getHttpRequest() != null
                    ? sCtx.getStoreSession().getHttpRequest().getRemoteAddr() : sCtx.getStoreSession().getCallingAET().replace('|', '-');
            fileName = getFileName(eventType, callingAET, sCtx.getStoreSession().getCalledAET(), sCtx.getStudyInstanceUID());
            BuildAuditInfo i = getAIStoreCtx(sCtx);
            writeSpoolFileStoreOrWadoRetrieve(fileName, new AuditInfo(i), new AuditInstanceInfo(i));
        }
        if (rCtx != null) {
            HttpServletRequest req = rCtx.getHttpRequest();
            Collection<InstanceLocations> il = rCtx.getMatches();
            for (InstanceLocations i : il) {
                attrs = i.getAttributes();
            }
            fileName = getFileName(AuditServiceUtils.EventType.WADO___URI, req.getRemoteAddr(),
                    rCtx.getLocalAETitle(), rCtx.getStudyInstanceUIDs()[0]);
            String callingAET = req.getAttribute(AuditServiceUtils.keycloakClassName) != null
                                ? AuditServiceUtils.getPreferredUsername(req)
                                : req.getRemoteAddr();
            BuildAuditInfo i = new BuildAuditInfo.Builder().callingHost(req.getRemoteAddr()).callingAET(callingAET)
                                .calledAET(req.getRequestURI()).studyUID(rCtx.getStudyInstanceUIDs()[0])
                                .accNum(attrs.getString(Tag.AccessionNumber)).pID(getPID(attrs)).pName(attrs.getString(Tag.PatientName))
                                .outcome(null != rCtx.getException() ? rCtx.getException().getMessage(): null).studyDate(getSD(attrs))
                                .sopCUID(attrs.getString(Tag.SOPClassUID)).sopIUID(rCtx.getSopInstanceUIDs()[0]).mppsUID(" ").build();
            writeSpoolFileStoreOrWadoRetrieve(fileName, new AuditInfo(i), new AuditInstanceInfo(i));
        }
    }

    private String getFileName(AuditServiceUtils.EventType et, String callingAET, String calledAET, String studyIUID) {
        return String.valueOf(et) + '-' + callingAET + '-' + calledAET + '-' + studyIUID;
    }

    private void auditStoreOrWADORetrieve(SpoolFileReader readerObj, Calendar eventTime,
                                          AuditServiceUtils.EventType eventType) {
        HashSet<String> mppsUIDs = new HashSet<>();
        HashMap<String, List<String>> sopClassMap = new HashMap<>();
        AuditInfo i = new AuditInfo(readerObj.getMainInfo());
        for (String line : readerObj.getInstanceLines()) {
            AuditInstanceInfo iI = new AuditInstanceInfo(line);
            List<String> iuids = sopClassMap.get(iI.getField(AuditInstanceInfo.SOP_CUID));
            if (iuids == null) {
                iuids = new ArrayList<>();
                sopClassMap.put(iI.getField(AuditInstanceInfo.SOP_CUID), iuids);
            }
            iuids.add(iI.getField(AuditInstanceInfo.SOP_IUID));
            mppsUIDs.add(iI.getField(AuditInstanceInfo.MPPS_UID));
//                for (int i = AuditInstanceInfo.ACCESSION_NO; iI.getField(i) != null; i++)
//                    accNos.add(iI.getField(i));
        }
        mppsUIDs.remove(" ");
        EventIdentification ei = getEI(eventType, i.getField(AuditInfo.OUTCOME), eventTime);
        BuildActiveParticipant ap1 = new BuildActiveParticipant.Builder(
                i.getField(AuditInfo.CALLING_AET),
                i.getField(AuditInfo.CALLING_HOST)).requester(eventType.isSource)
                .roleIDCode(eventType.source).build();
        BuildActiveParticipant ap2 = new BuildActiveParticipant.Builder(
                i.getField(AuditInfo.CALLED_AET), getLocalHostName(log()))
                .altUserID(AuditLogger.processID()).requester(eventType.isDest).roleIDCode(eventType.destination).build();
        HashSet<SOPClass> sopC = new HashSet<>();
        for (Map.Entry<String, List<String>> entry : sopClassMap.entrySet())
            sopC.add(getSOPC(null, entry.getKey(), entry.getValue().size()));
        ParticipantObjectContainsStudy pocs = getPocs(i.getField(AuditInfo.STUDY_UID));
        BuildParticipantObjectDescription desc = new BuildParticipantObjectDescription.Builder(sopC, pocs)
                .acc(getAccessions(i.getField(AuditInfo.ACC_NUM)))
                .mpps(AuditMessages.getMPPS(mppsUIDs.toArray(new String[mppsUIDs.size()]))).build();
        BuildParticipantObjectIdentification poi1 = new BuildParticipantObjectIdentification.Builder(
                i.getField(AuditInfo.STUDY_UID),
                AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID,
                AuditMessages.ParticipantObjectTypeCode.SystemObject, AuditMessages.ParticipantObjectTypeCodeRole.Report)
                .desc(getPODesc(desc)).detail(getPod(studyDate, i.getField(AuditInfo.STUDY_DATE))).build();
        BuildParticipantObjectIdentification poi2 = new BuildParticipantObjectIdentification.Builder(
                i.getField(AuditInfo.P_ID),
                AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                AuditMessages.ParticipantObjectTypeCode.Person, AuditMessages.ParticipantObjectTypeCodeRole.Patient)
                .name(i.getField(AuditInfo.P_NAME)).build();
        emitAuditMessage(ei, getApList(ap1, ap2), getPoiList(poi1, poi2), log());
    }

    void spoolPartialRetrieve(RetrieveContext ctx, HashSet<AuditServiceUtils.EventType> et) {
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
        String etFile;
        for (AuditServiceUtils.EventType eventType : et) {
            etFile = String.valueOf(eventType);
            if (etFile.substring(9, 10).equals("E"))
                spoolRetrieve(etFile, ctx, failed);
            if (etFile.substring(9, 10).equals("P"))
                spoolRetrieve(etFile, ctx, success);
        }
    }

    void spoolRetrieve(String etFile, RetrieveContext ctx, Collection<InstanceLocations> il) {
        LinkedHashSet<Object> obj = new LinkedHashSet<>();
        obj.add(new RetrieveInfo(ctx, etFile));
        for (InstanceLocations instanceLocation : il) {
            Attributes attrs = instanceLocation.getAttributes();
            obj.add(new RetrieveStudyInfo(attrs));
        }
        writeSpoolFile(etFile, obj);
    }

    private void auditRetrieve(SpoolFileReader readerObj, Calendar eventTime, AuditServiceUtils.EventType eventType) {
        RetrieveInfo ri = new RetrieveInfo(readerObj.getMainInfo());
        EventIdentification ei = getRetrieveEI(eventType, ri.getField(RetrieveInfo.FAILURE_DESC),
                ri.getField(RetrieveInfo.WARNING_DESC), eventTime);
        BuildActiveParticipant ap1 = new BuildActiveParticipant.Builder(ri.getField(RetrieveInfo.LOCALAET),
                getLocalHostName(log())).altUserID(AuditLogger.processID()).requester(eventType.isSource)
                .roleIDCode(eventType.source).build();
        BuildActiveParticipant ap2 = new BuildActiveParticipant.Builder(ri.getField(RetrieveInfo.DESTAET),
                ri.getField(RetrieveInfo.DESTNAPID)).requester(eventType.isDest).roleIDCode(eventType.destination).build();
        BuildActiveParticipant ap3 = null;
        if (eventType.isOther) {
            ap3 = new BuildActiveParticipant.Builder(ri.getField(RetrieveInfo.MOVEAET),
                    ri.getField(RetrieveInfo.REQUESTORHOST)).requester(eventType.isOther).build();
        }
        HashMap<String, AccessionNumSopClassInfo> study_accNumSOPClassInfo = new HashMap<>();
        String pID = noValue;
        String pName = null;
        String studyDt = null;
        for (String line : readerObj.getInstanceLines()) {
            RetrieveStudyInfo rInfo = new RetrieveStudyInfo(line);
            String studyInstanceUID = rInfo.getField(RetrieveStudyInfo.STUDYUID);
            AccessionNumSopClassInfo accNumSopClassInfo = study_accNumSOPClassInfo.get(studyInstanceUID);
            if (accNumSopClassInfo == null) {
                accNumSopClassInfo = new AccessionNumSopClassInfo(
                        rInfo.getField(RetrieveStudyInfo.ACCESSION));
                study_accNumSOPClassInfo.put(studyInstanceUID, accNumSopClassInfo);
            }
            accNumSopClassInfo.addSOPInstance(rInfo);
            study_accNumSOPClassInfo.put(studyInstanceUID, accNumSopClassInfo);
            pID = rInfo.getField(RetrieveStudyInfo.PATIENTID);
            pName = rInfo.getField(RetrieveStudyInfo.PATIENTNAME);
            studyDt = rInfo.getField(RetrieveStudyInfo.STUDY_DATE);
        }
        List<BuildParticipantObjectIdentification> pois = new ArrayList<>();
        for (Map.Entry<String, AccessionNumSopClassInfo> entry : study_accNumSOPClassInfo.entrySet()) {
            HashSet<SOPClass> sopC = new HashSet<>();
            for (Map.Entry<String, HashSet<String>> sopClassMap : entry.getValue().getSopClassMap().entrySet()) {
                if (ri.getField(RetrieveInfo.FAILED_IUIDS_SHOW_FLAG).equals(Boolean.toString(true)))
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
        emitAuditMessage(ei, eventType.isOther ? getApList(ap1, ap2, ap3) : getApList(ap1, ap2),
                getPoiList(pois.toArray(new BuildParticipantObjectIdentification[pois.size()])), log());
    }

    void spoolPatientRecord(PatientMgtContext ctx) {
        HashSet<AuditServiceUtils.EventType> et = AuditServiceUtils.EventType.forHL7(ctx);
        for (AuditServiceUtils.EventType eventType : et) {
            LinkedHashSet<Object> obj = new LinkedHashSet<>();
            String source = null;
            String dest = null;
            if (ctx.getHttpRequest() != null) {
                source = ctx.getHttpRequest().getAttribute(keycloakClassName) != null
                        ? getPreferredUsername(ctx.getHttpRequest())
                        : ctx.getHttpRequest().getRemoteAddr();
                dest = ctx.getCalledAET();
            }
            if (ctx.getHL7MessageHeader() != null) {
                source = ctx.getHL7MessageHeader().getSendingApplicationWithFacility();
                dest = ctx.getHL7MessageHeader().getReceivingApplicationWithFacility();
            }
            if (ctx.getAssociation() != null) {
                source = ctx.getAssociation().getCallingAET();
                dest = ctx.getAssociation().getCalledAET();
            }
            String pID = eventType == AuditServiceUtils.EventType.HL7_DELETE && ctx.getPreviousPatientID() != null
                    ? ctx.getPreviousPatientID().toString()
                    : ctx.getPatientID() != null ? ctx.getPatientID().toString() : noValue;
            String pName = eventType == AuditServiceUtils.EventType.HL7_DELETE
                    ? StringUtils.maskEmpty(ctx.getPreviousAttributes().getString(Tag.PatientName), null)
                    : StringUtils.maskEmpty(ctx.getAttributes().getString(Tag.PatientName), null);
            BuildAuditInfo i = new BuildAuditInfo.Builder().callingHost(ctx.getHttpRequest() != null
                                ? ctx.getHttpRequest().getRemoteAddr() : ctx.getRemoteHostName())
                                .callingAET(source).calledAET(dest).pID(pID).pName(pName)
                                .outcome(getOD(ctx.getException())).build();
            obj.add(new AuditInfo(i));
            writeSpoolFile(String.valueOf(eventType), obj);
        }
    }

    private void auditPatientRecord(AuditInfo hl7I, AuditServiceUtils.EventType et) {
        EventIdentification ei = getEI(et, hl7I.getField(AuditInfo.OUTCOME), log().timeStamp());
        BuildActiveParticipant ap1 = new BuildActiveParticipant.Builder(hl7I.getField(AuditInfo.CALLING_AET),
                hl7I.getField(AuditInfo.CALLING_HOST)).requester(et.isSource).roleIDCode(et.source).build();
        BuildActiveParticipant ap2 = new BuildActiveParticipant.Builder(hl7I.getField(AuditInfo.CALLED_AET),
                getLocalHostName(log())).altUserID(AuditLogger.processID()).requester(et.isDest)
                .roleIDCode(et.destination).build();
        BuildParticipantObjectIdentification poi = new BuildParticipantObjectIdentification.Builder(
                hl7I.getField(AuditInfo.P_ID), AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                AuditMessages.ParticipantObjectTypeCode.Person, AuditMessages.ParticipantObjectTypeCodeRole.Patient)
                .name(hl7I.getField(AuditInfo.P_NAME)).build();
        emitAuditMessage(ei, getApList(ap1, ap2), getPoiList(poi), log());
    }

    void spoolProcedureRecord(ProcedureContext ctx) {
        HashSet<AuditServiceUtils.EventType> et = AuditServiceUtils.EventType.forProcedure(ctx.getEventActionCode());
        for (AuditServiceUtils.EventType eventType : et) {
            LinkedHashSet<Object> obj = new LinkedHashSet<>();
            Attributes attr = ctx.getAttributes();
            BuildAuditInfo i = new BuildAuditInfo.Builder().callingHost(ctx.getRemoteHostName()).callingAET(ctx.getHL7MessageHeader().getSendingApplicationWithFacility())
                                .calledAET(ctx.getHL7MessageHeader().getReceivingApplicationWithFacility()).studyUID(ctx.getStudyInstanceUID())
                                .accNum(attr.getString(Tag.AccessionNumber)).pID(getPID(attr)).pName(ctx.getPatient().getAttributes().getString(Tag.PatientName))
                                .outcome(getOD(ctx.getException())).studyDate(getSD(attr)).build();
            obj.add(new AuditInfo(i));
            writeSpoolFile(String.valueOf(eventType), obj);
        }
    }

    void spoolProcedureRecord(StudyMgtContext ctx) {
        HashSet<AuditServiceUtils.EventType> et = AuditServiceUtils.EventType.forProcedure(ctx.getEventActionCode());
        for (AuditServiceUtils.EventType eventType : et) {
            LinkedHashSet<Object> obj = new LinkedHashSet<>();
            String callingAET = ctx.getHttpRequest().getAttribute(keycloakClassName) != null
                    ? getPreferredUsername(ctx.getHttpRequest()) : ctx.getHttpRequest().getRemoteAddr();
            Attributes sAttr = ctx.getAttributes();
            Attributes pAttr = ctx.getStudy().getPatient().getAttributes();
            BuildAuditInfo i = new BuildAuditInfo.Builder().callingHost(ctx.getHttpRequest().getRemoteHost()).callingAET(callingAET)
                        .calledAET(ctx.getApplicationEntity().getAETitle()).studyUID(ctx.getStudyInstanceUID()).accNum(sAttr.getString(Tag.AccessionNumber))
                        .pID(getPID(pAttr)).pName(pAttr.getString(Tag.PatientName)).outcome(getOD(ctx.getException())).studyDate(getSD(sAttr)).build();
            obj.add(new AuditInfo(i));
            writeSpoolFile(String.valueOf(eventType), obj);
        }
    }

    private void auditProcedureRecord(AuditInfo prI, AuditServiceUtils.EventType et) {
        EventIdentification ei = getEI(et, prI.getField(AuditInfo.OUTCOME), log().timeStamp());
        BuildActiveParticipant ap1 = new BuildActiveParticipant.Builder(prI.getField(AuditInfo.CALLING_AET),
                prI.getField(AuditInfo.CALLING_HOST)).requester(et.isSource).build();
        BuildActiveParticipant ap2 = new BuildActiveParticipant.Builder(prI.getField(AuditInfo.CALLED_AET),
                getLocalHostName(log())).altUserID(AuditLogger.processID()).requester(et.isDest).build();
        ParticipantObjectContainsStudy pocs = getPocs(prI.getField(AuditInfo.STUDY_UID));
        BuildParticipantObjectDescription desc = new BuildParticipantObjectDescription.Builder(null, pocs)
                .acc(getAccessions(prI.getField(AuditInfo.ACC_NUM))).build();
        BuildParticipantObjectIdentification poi1 = new BuildParticipantObjectIdentification.Builder(
                prI.getField(AuditInfo.STUDY_UID), AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID,
                AuditMessages.ParticipantObjectTypeCode.SystemObject, AuditMessages.ParticipantObjectTypeCodeRole.Report)
                .desc(getPODesc(desc)).detail(getPod(studyDate, prI.getField(AuditInfo.STUDY_DATE))).build();
        BuildParticipantObjectIdentification poi2 = new BuildParticipantObjectIdentification.Builder(
                prI.getField(AuditInfo.P_ID), AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                AuditMessages.ParticipantObjectTypeCode.Person, AuditMessages.ParticipantObjectTypeCodeRole.Patient)
                .name(prI.getField(AuditInfo.P_NAME)).build();
        emitAuditMessage(ei, getApList(ap1, ap2), getPoiList(poi1, poi2), log());
    }

    private BuildAuditInfo getAIStoreCtx(StoreContext ctx) {
        StoreSession ss = ctx.getStoreSession();
        HttpServletRequest req = ss.getHttpRequest();
        Attributes attr = ctx.getAttributes();
        String callingAET = ss.getCallingAET() != null ? ss.getCallingAET()
                : req != null && req.getAttribute(keycloakClassName) != null
                ? getPreferredUsername(req) : ss.getRemoteHostName();
        String outcome = null != ctx.getRejectionNote() ? null != ctx.getException()
                ? ctx.getRejectionNote().getRejectionNoteCode().getCodeMeaning() + " - " + ctx.getException().getMessage()
                : ctx.getRejectionNote().getRejectionNoteCode().getCodeMeaning()
                : getOD(ctx.getException());
        boolean rjFlag = ctx.getRejectionNote() != null;
        BuildAuditInfo i = new BuildAuditInfo.Builder().callingHost(ss.getRemoteHostName()).callingAET(callingAET)
                .calledAET(req != null ? req.getRequestURI() : ss.getCalledAET()).studyUID(ctx.getStudyInstanceUID())
                .accNum(attr.getString(Tag.AccessionNumber)).pID(getPID(attr)).pName(attr.getString(Tag.PatientName))
                .outcome(outcome).studyDate(getSD(attr)).sopCUID(rjFlag ? null : ctx.getSopClassUID())
                .sopIUID(rjFlag ? null : ctx.getSopInstanceUID())
                .mppsUID(rjFlag ? null : StringUtils.maskNull(ctx.getMppsInstanceUID(), "")).build();
        return i;
    }

    private String getSD(Attributes attr) {
        return attr.getString(Tag.StudyDate);
    }

    private String getPreferredUsername(HttpServletRequest req) {
        RefreshableKeycloakSecurityContext securityContext = (RefreshableKeycloakSecurityContext)
                req.getAttribute(KeycloakSecurityContext.class.getName());
        return securityContext.getToken().getPreferredUsername();
    }

    private String getPID(Attributes attrs) {
        return attrs.getString(Tag.PatientID) != null ? IDWithIssuer.pidOf(attrs).toString() : noValue;
    }

    private String getOD(Exception e) {
        return e != null ? e.getMessage() : null;
    }

    private ParticipantObjectDetail getPod(String type, String value) {
        return value != null ? AuditMessages.createParticipantObjectDetail(type, value.getBytes()) : null;
    }

    private ParticipantObjectContainsStudy getPocs(String studyId) {
        return AuditMessages.getPocs(studyId);
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
            AuditInstanceInfo ii = new AuditInstanceInfo(line);
            sopC.add(getSOPC(null, ii.getField(AuditInstanceInfo.SOP_CUID),
                    Integer.parseInt(ii.getField(AuditInstanceInfo.SOP_IUID))));
        }
        return sopC;
    }

    private SOPClass getSOPC(HashSet<String> instances, String uid, Integer numI) {
        return AuditMessages.getSOPC(instances, uid, numI);
    }

    private Calendar getEventTime(Path path, AuditLogger log){
        Calendar eventTime = log.timeStamp();
        try {
            eventTime.setTimeInMillis(Files.getLastModifiedTime(path).toMillis());
        } catch (Exception e) {
            LOG.warn("Failed to get Last Modified Time of Audit Spool File - {} ", path, e);
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

    private void writeSpoolFile(String eventType, LinkedHashSet<Object> obj) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        boolean auditAggregate = arcDev.isAuditAggregate();
        Path dir = Paths.get(StringUtils.replaceSystemProperties(
                auditAggregate? arcDev.getAuditSpoolDirectory() : JBOSS_SERVER_TEMP));
        try {
            Files.createDirectories(dir);
            Path file = Files.createTempFile(dir, eventType, null);
            try (SpoolFileWriter writer = new SpoolFileWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.APPEND))) {
                for (Object o : obj)
                    writer.writeLine(o);
            }
            if (!auditAggregate)
                aggregateAuditMessage(file);
        } catch (Exception e) {
            LOG.warn("Failed to write to Audit Spool File - {} ", e);
        }
    }

    private void writeSpoolFileStoreOrWadoRetrieve(String fileName, Object patStudyInfo, Object instanceInfo) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        boolean auditAggregate = arcDev.isAuditAggregate();
        Path dir = Paths.get(StringUtils.replaceSystemProperties(
                auditAggregate? arcDev.getAuditSpoolDirectory() : JBOSS_SERVER_TEMP));
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
                aggregateAuditMessage(file);
        } catch (Exception e) {
            LOG.warn("Failed to write to Audit Spool File - {} ", file, e);
        }
    }

    private LinkedHashSet<Object> getDeletionObjsForSpooling(HashMap<String, HashSet<String>> sopClassMap,
                                                             AuditInfo i) {
        LinkedHashSet<Object> obj = new LinkedHashSet<>();
        obj.add(i);
        for (Map.Entry<String, HashSet<String>> entry : sopClassMap.entrySet()) {
            obj.add(new AuditInstanceInfo(new BuildAuditInfo.Builder().sopCUID(entry.getKey())
                    .sopIUID(String.valueOf(entry.getValue().size())).build()));
        }
        return obj;
    }

    private List<ActiveParticipant> getApList(BuildActiveParticipant... aps) {
        return AuditMessages.getApList(aps);
    }

    private List<ParticipantObjectIdentification> getPoiList(BuildParticipantObjectIdentification... pois) {
        return AuditMessages.getPoiList(pois);
    }

    private void emitAuditMessage(EventIdentification ei, List<ActiveParticipant> apList,
                                  List<ParticipantObjectIdentification> poiList, AuditLogger log) {
        AuditMessage msg = AuditMessages.createMessage(ei, apList, poiList);
        msg.getAuditSourceIdentification().add(log.createAuditSourceIdentification());
        try {
            log.write(log.timeStamp(), msg);
        } catch (Exception e) {
            LOG.warn("Failed to emit audit message", e);
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

    private EventIdentification getRetrieveEI(AuditServiceUtils.EventType et, String failureDesc, String warningDesc, Calendar t) {
        if (failureDesc != null)
            return getEI(et, failureDesc, t);
        else {
            BuildEventIdentification ei = new BuildEventIdentification.Builder(
                    et.eventID, et.eventActionCode, t, AuditMessages.EventOutcomeIndicator.Success)
                    .outcomeDesc(warningDesc).build();
            return AuditMessages.getEI(ei);
        }
    }

}
