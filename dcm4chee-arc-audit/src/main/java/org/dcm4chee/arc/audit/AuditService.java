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
import org.dcm4che3.data.UID;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.delete.StudyDeleteContext;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.retrieve.InstanceLocations;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.store.StoreContext;
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
    private static final Logger LOG = LoggerFactory.getLogger(AuditService.class);
    private static final String JBOSS_SERVER_TEMP = "${jboss.server.temp}";
    private static final String studyDate = "StudyDate";
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
        LineReader readerObj = eventType.eventClass != AuditServiceUtils.EventClass.QUERY
                ? new LineReader(path) : null;
        Calendar eventTime = getEventTime(path, log());
        switch (eventType.eventClass) {
            case CONN_REJECT:
                auditConnectionRejected(readerObj, eventType);
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
                auditPatientRecord(readerObj, eventType);
                break;
        }
    }

    void auditApplicationActivity(AuditServiceUtils.EventType eventType, HttpServletRequest req) {
        BuildActiveParticipant ap1 = new BuildActiveParticipant.Builder(getAET(device),
                getLocalHostName(log())).altUserID(AuditLogger.processID()).requester(false).
                roleIDCode(AuditMessages.RoleIDCode.Application).build();
        BuildActiveParticipant ap2 = null;
        if (req != null) {
            ap2 = new BuildActiveParticipant.Builder(
                    req.getAttribute(AuditServiceUtils.keycloakClassName) != null
                    ? AuditServiceUtils.getPreferredUsername(req) : req.getRemoteAddr(), req.getRemoteAddr()).
                    requester(true).roleIDCode(AuditMessages.RoleIDCode.ApplicationLauncher).build();
        }
        emitAuditMessage(eventType, null,
                req != null ? getApList(ap1, ap2) : getApList(ap1),
                null, log(), log().timeStamp());
    }

    void spoolInstancesDeleted(StoreContext ctx) {
        AuditServiceUtils.EventType et = (ctx.getException() != null)
                ? AuditServiceUtils.EventType.DELETE_ERR : AuditServiceUtils.EventType.DELETE_PAS;
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
        writeSpoolFile(String.valueOf(et), getDeletionObjsForSpooling(sopClassMap, new PatientStudyInfo(ctx)));
    }

    void spoolStudyDeleted(StudyDeleteContext ctx) {
        AuditServiceUtils.EventType eventType = (ctx.getException() != null)
                ? AuditServiceUtils.EventType.PERM_DEL_E : AuditServiceUtils.EventType.PERM_DEL_S;
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
        writeSpoolFile(String.valueOf(eventType), getDeletionObjsForSpooling(sopClassMap, new PatientStudyInfo(ctx)));
    }

    private void auditDeletion(LineReader readerObj, AuditServiceUtils.EventType eventType) {
        PatientStudyInfo deleteInfo = new PatientStudyInfo(readerObj.getMainInfo());
        BuildActiveParticipant ap1 = null;
        if (eventType.eventClass == AuditServiceUtils.EventClass.DELETE) {
            ap1 = new BuildActiveParticipant.Builder(
                    deleteInfo.getField(PatientStudyInfo.CALLING_AET), deleteInfo.getField(PatientStudyInfo.CALLING_HOSTNAME))
                    .requester(eventType.isSource).build();
        }
        BuildActiveParticipant ap2 = new BuildActiveParticipant.Builder(
                eventType.eventClass == AuditServiceUtils.EventClass.DELETE
                        ? deleteInfo.getField(PatientStudyInfo.CALLED_AET) : getAET(device),
                getLocalHostName(log())).altUserID(AuditLogger.processID())
                .requester(eventType.eventClass != AuditServiceUtils.EventClass.DELETE || eventType.isDest).build();
        ParticipantObjectContainsStudy pocs = getPocs(deleteInfo.getField(PatientStudyInfo.STUDY_UID));
        BuildParticipantObjectDescription desc = new BuildParticipantObjectDescription.Builder(
                getSopClasses(readerObj.getInstanceLines()), pocs)
                .acc(eventType.eventClass == AuditServiceUtils.EventClass.PERM_DELETE
                        ? getAccessions(deleteInfo.getField(PatientStudyInfo.ACCESSION_NO)) : null).build();
        BuildParticipantObjectIdentification poi1 = new BuildParticipantObjectIdentification.Builder(
                deleteInfo.getField(PatientStudyInfo.STUDY_UID), AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID,
                AuditMessages.ParticipantObjectTypeCode.SystemObject,AuditMessages.ParticipantObjectTypeCodeRole.Report)
                .desc(getPODesc(desc)).detail(getPod(studyDate, deleteInfo.getField(PatientStudyInfo.STUDY_DATE))).build();
        BuildParticipantObjectIdentification poi2 = new BuildParticipantObjectIdentification.Builder(
                deleteInfo.getField(PatientStudyInfo.PATIENT_ID), AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                AuditMessages.ParticipantObjectTypeCode.Person, AuditMessages.ParticipantObjectTypeCodeRole.Patient)
                .name(deleteInfo.getField(PatientStudyInfo.PATIENT_NAME)).build();
        emitAuditMessage(eventType, deleteInfo.getField(PatientStudyInfo.OUTCOME),
                eventType.eventClass == AuditServiceUtils.EventClass.DELETE
                        ? getApList(ap1, ap2) : getApList(ap2),
                getPoiList(poi1, poi2),
                log(), log().timeStamp());
    }

    void spoolConnectionRejected(Connection conn, Socket s, Throwable e) {
        LinkedHashSet<Object> obj = new LinkedHashSet<>();
        obj.add(new ConnectionRejectedInfo(conn, s, e));
        writeSpoolFile(String.valueOf(AuditServiceUtils.EventType.CONN__RJCT), obj);
    }

    private void auditConnectionRejected(LineReader readerObj, AuditServiceUtils.EventType eventType) {
        ConnectionRejectedInfo crInfo = new ConnectionRejectedInfo(readerObj.getMainInfo());
        BuildActiveParticipant ap1 = new BuildActiveParticipant.Builder(getAET(device),
                crInfo.getField(ConnectionRejectedInfo.LOCAL_ADDR)).altUserID(AuditLogger.processID()).requester(false).build();
        String userID, napID;
        userID = napID = crInfo.getField(ConnectionRejectedInfo.REMOTE_ADDR);
        BuildActiveParticipant ap2 = new BuildActiveParticipant.Builder(userID, napID).requester(true).build();
        BuildParticipantObjectIdentification poi = new BuildParticipantObjectIdentification.Builder(
                crInfo.getField(ConnectionRejectedInfo.REMOTE_ADDR), AuditMessages.ParticipantObjectIDTypeCode.NodeID,
                AuditMessages.ParticipantObjectTypeCode.SystemObject, null).build();
        emitAuditMessage(eventType,
            crInfo.getField(ConnectionRejectedInfo.OUTCOME_DESC), getApList(ap1, ap2),
            getPoiList(poi), log(), log().timeStamp());
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
                new DataOutputStream(out).writeUTF(new QueryInfo(ctx).toString());
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

    private void auditQuery(Path file, AuditServiceUtils.EventType eventType)
            throws IOException {
        QueryInfo qrInfo;
        List<ActiveParticipant> apList;
        List<ParticipantObjectIdentification> poiList;
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            qrInfo = new QueryInfo(new DataInputStream(in).readUTF());
            BuildActiveParticipant ap1 = new BuildActiveParticipant.Builder(qrInfo.getField(QueryInfo.CALLING_AET),
                    qrInfo.getField(QueryInfo.REMOTE_HOST)).requester(eventType.isSource).roleIDCode(eventType.source)
                    .build();
            BuildActiveParticipant ap2 = new BuildActiveParticipant.Builder(qrInfo.getField(QueryInfo.CALLED_AET),
                    getLocalHostName(log())).altUserID(AuditLogger.processID())
                    .requester(eventType.isDest).roleIDCode(eventType.destination).build();
            apList = getApList(ap1, ap2);
            BuildParticipantObjectIdentification poi1, poi2, poi3;
            poi1 = poi2 = poi3 = null;
            if (!qrInfo.getField(QueryInfo.PATIENT_ID).equals(AuditServiceUtils.noValue)) {
                poi1 = new BuildParticipantObjectIdentification.Builder(
                        qrInfo.getField(QueryInfo.PATIENT_ID), AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                        AuditMessages.ParticipantObjectTypeCode.Person, AuditMessages.ParticipantObjectTypeCodeRole.Patient)
                        .build();

            }
            if (eventType == AuditServiceUtils.EventType.QUERY_QIDO) {
                poi2 = new BuildParticipantObjectIdentification.Builder(
                        null, AuditMessages.ParticipantObjectIDTypeCode.SOPClassUID,
                        AuditMessages.ParticipantObjectTypeCode.SystemObject,
                        AuditMessages.ParticipantObjectTypeCodeRole.Query)
                        .query(qrInfo.getField(QueryInfo.QUERY_STRING).getBytes())
                        .detail(getPod("QueryEncoding", String.valueOf(StandardCharsets.UTF_8))).build();
                poiList = (!qrInfo.getField(QueryInfo.PATIENT_ID).equals(AuditServiceUtils.noValue))
                            ? getPoiList(poi1, poi2) : getPoiList(poi2);
            }
            else {
                byte[] buffer = new byte[(int) Files.size(file)];
                int len = in.read(buffer);
                byte[] data = new byte[len];
                System.arraycopy(buffer, 0, data, 0, len);
                poi3 = new BuildParticipantObjectIdentification.Builder(
                        qrInfo.getField(QueryInfo.SOPCLASSUID), AuditMessages.ParticipantObjectIDTypeCode.SOPClassUID,
                        AuditMessages.ParticipantObjectTypeCode.SystemObject,
                        AuditMessages.ParticipantObjectTypeCodeRole.Query).query(data)
                        .detail(getPod("TransferSyntax", UID.ImplicitVRLittleEndian)).build();
                poiList = (!qrInfo.getField(QueryInfo.PATIENT_ID).equals(AuditServiceUtils.noValue))
                        ? getPoiList(poi1, poi3) : getPoiList(poi3);
            }
        }
        emitAuditMessage(eventType, null, apList, poiList, log(), log().timeStamp());
    }

    void spoolInstanceStoredOrWadoRetrieve(StoreContext storeCtx, RetrieveContext retrieveCtx) {
        String fileName;
        Attributes attrs = new Attributes();
        AuditServiceUtils.EventType eventType;
        if (storeCtx != null) {
            eventType = AuditServiceUtils.EventType.forInstanceStored(storeCtx);
            if (eventType == null)
                return; // no audit message for duplicate received instance
            fileName = String.valueOf(eventType) + '-' + storeCtx.getStoreSession().getCallingAET().replace('|', '-')
                + '-' + storeCtx.getStoreSession().getCalledAET() + '-' + storeCtx.getStudyInstanceUID();
            writeSpoolFileStoreOrWadoRetrieve(fileName, new PatientStudyInfo(storeCtx), new InstanceInfo(storeCtx));
        }
        if (retrieveCtx != null) {
            eventType = AuditServiceUtils.EventType.forWADORetrieve(retrieveCtx);
            HttpServletRequest req = retrieveCtx.getHttpRequest();
            Collection<InstanceLocations> il = retrieveCtx.getMatches();
            for (InstanceLocations i : il) {
                attrs = i.getAttributes();
            }
            fileName = String.valueOf(eventType) + '-' + req.getRemoteAddr() + '-' + retrieveCtx.getLocalAETitle() + '-'
                + retrieveCtx.getStudyInstanceUIDs()[0];
            writeSpoolFileStoreOrWadoRetrieve(fileName, new PatientStudyInfo(retrieveCtx, attrs),
                new InstanceInfo(retrieveCtx, attrs));
        }
    }

    private void auditStoreOrWADORetrieve(LineReader readerObj, Calendar eventTime,
                                          AuditServiceUtils.EventType eventType) {
        HashSet<String> mppsUIDs = new HashSet<>();
        HashMap<String, List<String>> sopClassMap = new HashMap<>();
        PatientStudyInfo patientStudyInfo = new PatientStudyInfo(readerObj.getMainInfo());
        for (String line : readerObj.getInstanceLines()) {
            InstanceInfo instanceInfo = new InstanceInfo(line);
            List<String> iuids = sopClassMap.get(instanceInfo.getField(InstanceInfo.CLASS_UID));
            if (iuids == null) {
                iuids = new ArrayList<>();
                sopClassMap.put(instanceInfo.getField(InstanceInfo.CLASS_UID), iuids);
            }
            iuids.add(instanceInfo.getField(InstanceInfo.INSTANCE_UID));
            mppsUIDs.add(instanceInfo.getField(InstanceInfo.MPPS_UID));
//                for (int i = InstanceInfo.ACCESSION_NO; instanceInfo.getField(i) != null; i++)
//                    accNos.add(instanceInfo.getField(i));
        }
        mppsUIDs.remove("");
        BuildActiveParticipant ap1 = new BuildActiveParticipant.Builder(
                patientStudyInfo.getField(PatientStudyInfo.CALLING_AET),
                patientStudyInfo.getField(PatientStudyInfo.CALLING_HOSTNAME)).requester(eventType.isSource)
                .roleIDCode(eventType.source).build();
        BuildActiveParticipant ap2 = new BuildActiveParticipant.Builder(
                patientStudyInfo.getField(PatientStudyInfo.CALLED_AET), getLocalHostName(log()))
                .altUserID(AuditLogger.processID()).requester(eventType.isDest).roleIDCode(eventType.destination).build();
        HashSet<MPPS> mpps = new HashSet<>();
        HashSet<SOPClass> sopC = new HashSet<>();
        for (String mppsUID : mppsUIDs)
            mpps.add(AuditMessages.createMPPS(mppsUID));
        for (Map.Entry<String, List<String>> entry : sopClassMap.entrySet())
            sopC.add(getSOPC(null, entry.getKey(), entry.getValue().size()));
        ParticipantObjectContainsStudy pocs = getPocs(patientStudyInfo.getField(PatientStudyInfo.STUDY_UID));
        BuildParticipantObjectDescription desc = new BuildParticipantObjectDescription.Builder(sopC, pocs)
                .acc(getAccessions(patientStudyInfo.getField(PatientStudyInfo.ACCESSION_NO))).mpps(mpps).build();
        BuildParticipantObjectIdentification poi1 = new BuildParticipantObjectIdentification.Builder(
                patientStudyInfo.getField(PatientStudyInfo.STUDY_UID),
                AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID,
                AuditMessages.ParticipantObjectTypeCode.SystemObject, AuditMessages.ParticipantObjectTypeCodeRole.Report)
                .desc(getPODesc(desc)).detail(getPod(studyDate, patientStudyInfo.getField(PatientStudyInfo.STUDY_DATE))).build();
        BuildParticipantObjectIdentification poi2 = new BuildParticipantObjectIdentification.Builder(
                patientStudyInfo.getField(PatientStudyInfo.PATIENT_ID),
                AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                AuditMessages.ParticipantObjectTypeCode.Person, AuditMessages.ParticipantObjectTypeCodeRole.Patient)
                .name(patientStudyInfo.getField(PatientStudyInfo.PATIENT_NAME)).build();
        emitAuditMessage(eventType, patientStudyInfo.getField(PatientStudyInfo.OUTCOME),
                getApList(ap1, ap2), getPoiList(poi1, poi2), log(), eventTime);
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

    private void auditRetrieve(LineReader readerObj, Calendar eventTime, AuditServiceUtils.EventType eventType) {
        RetrieveInfo ri = new RetrieveInfo(readerObj.getMainInfo());
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
        String pID = AuditServiceUtils.noValue;
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
                if (ri.getField(RetrieveInfo.PARTIAL_ERROR).equals(Boolean.toString(true))
                        && eventType.outcomeIndicator.equals(AuditMessages.EventOutcomeIndicator.MinorFailure))
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
        emitAuditMessage(eventType, ri.getField(RetrieveInfo.OUTCOME),
            eventType.isOther ? getApList(ap1, ap2, ap3) : getApList(ap1, ap2),
            getPoiList(pois.toArray(new BuildParticipantObjectIdentification[pois.size()])), log(), eventTime);
    }

    void spoolPatientRecord(PatientMgtContext ctx) {
        HashSet<AuditServiceUtils.EventType> et = AuditServiceUtils.EventType.forHL7(ctx);
        for (AuditServiceUtils.EventType eventType : et) {
            LinkedHashSet<Object> obj = new LinkedHashSet<>();
            obj.add(new HL7Info(ctx, eventType));
            writeSpoolFile(String.valueOf(eventType), obj);
        }
    }

    private void auditPatientRecord(LineReader readerObj, AuditServiceUtils.EventType et) {
        HL7Info hl7psi = new HL7Info(readerObj.getMainInfo());
        BuildActiveParticipant ap1 = new BuildActiveParticipant.Builder(hl7psi.getField(HL7Info.CALLING_AET),
                hl7psi.getField(HL7Info.CALLING_HOSTNAME)).requester(et.isSource).roleIDCode(et.source).build();
        BuildActiveParticipant ap2 = new BuildActiveParticipant.Builder(hl7psi.getField(HL7Info.CALLED_AET),
                getLocalHostName(log())).altUserID(AuditLogger.processID()).requester(et.isDest)
                .roleIDCode(et.destination).build();
        BuildParticipantObjectIdentification poi = new BuildParticipantObjectIdentification.Builder(
                hl7psi.getField(HL7Info.PATIENT_ID), AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                AuditMessages.ParticipantObjectTypeCode.Person, AuditMessages.ParticipantObjectTypeCodeRole.Patient)
                .name(hl7psi.getField(HL7Info.PATIENT_NAME)).build();
        emitAuditMessage(et, hl7psi.getField(HL7Info.OUTCOME),
            getApList(ap1, ap2), getPoiList(poi), log(), log().timeStamp());
    }

    private ParticipantObjectDetail getPod(String type, String value) {
        return value != null ? AuditMessages.createParticipantObjectDetail(type, value.getBytes()) : null;
    }

    private ParticipantObjectContainsStudy getPocs(String studyId) {
        return AuditMessages.createParticipantObjectContainsStudy(AuditMessages.createStudyIDs(studyId));
    }

    private ParticipantObjectDescription getPODesc(BuildParticipantObjectDescription desc) {
        return AuditMessages.createParticipantObjectDescription(desc.acc, desc.mpps, desc.sopC, desc.encrypted,
                desc.anonymized, desc.pocs);
    }

    private HashSet<Accession> getAccessions(String accNum) {
        HashSet<Accession> accList = new HashSet<>();
        if (accNum != null)
            accList.add(AuditMessages.createAccession(accNum));
        return accList;
    }

    private HashSet<SOPClass> getSopClasses(HashSet<String> instanceLines) {
        HashSet<SOPClass> sopC = new HashSet<>();
        for (String line : instanceLines) {
            InstanceInfo ii = new InstanceInfo(line);
            sopC.add(getSOPC(null, ii.getField(InstanceInfo.CLASS_UID),
                    Integer.parseInt(ii.getField(InstanceInfo.INSTANCE_UID))));
        }
        return sopC;
    }

    private SOPClass getSOPC(HashSet<String> instances, String uid, Integer numI) {
        return AuditMessages.createSOPClass(instances, uid, numI);
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
        String[] aets = device.getApplicationAETitles().toArray(new String[device.getApplicationAETitles().size()]);
        StringBuilder b = new StringBuilder();
        b.append(aets[0]);
        for (int i = 1; i < aets.length; i++)
            b.append(';').append(aets[i]);
        return b.toString();
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
            try (LineWriter writer = new LineWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8,
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
            try (LineWriter writer = new LineWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8,
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
                                                            PatientStudyInfo psi) {
        LinkedHashSet<Object> obj = new LinkedHashSet<>();
        obj.add(psi);
        for (Map.Entry<String, HashSet<String>> entry : sopClassMap.entrySet()) {
            obj.add(new InstanceInfo(entry.getKey(), String.valueOf(entry.getValue().size())));
        }
        return obj;
    }

    private List<ActiveParticipant> getApList (BuildActiveParticipant... aps) {
        List<ActiveParticipant> apList = new ArrayList<>();
        for (BuildActiveParticipant ap: aps)
            apList.add(AuditMessages.createActiveParticipant(ap.userID, ap.altUserID, ap.userName, ap.requester,
                    ap.napID, ap.napTypeCode, ap.mediaType, ap.roleIDCode));
        return apList;
    }

    private List<ParticipantObjectIdentification> getPoiList (BuildParticipantObjectIdentification... pois) {
        List<ParticipantObjectIdentification> poiList = new ArrayList<>();
        if (pois != null)
            for (BuildParticipantObjectIdentification poi: pois)
                poiList.add(AuditMessages.createParticipantObjectIdentification(poi.id, poi.idType, poi.name, poi.query, poi.type,
                        poi.role, poi.lifeCycle, poi.sensitivity, poi.desc, poi.detail));
        return poiList;
    }

    private void emitAuditMessage(AuditServiceUtils.EventType eventType, String outcomeDesc, List<ActiveParticipant> apList,
                                  List<ParticipantObjectIdentification> poiList, AuditLogger log, Calendar eventTime) {
        AuditMessage msg = new AuditMessage();
        msg.setEventIdentification(AuditMessages.createEventIdentification(
                eventType.eventID, eventType.eventActionCode, eventTime, eventType.outcomeIndicator,
                outcomeDesc, eventType.eventTypeCode));
        for (ActiveParticipant ap : apList)
            msg.getActiveParticipant().add(ap);
        msg.getAuditSourceIdentification().add(log.createAuditSourceIdentification());
        if (poiList != null)
            for (ParticipantObjectIdentification poi : poiList)
                msg.getParticipantObjectIdentification().add(poi);
        try {
            log.write(log.timeStamp(), msg);
        } catch (Exception e) {
            LOG.warn("Failed to emit audit message", e);
        }
    }

}
