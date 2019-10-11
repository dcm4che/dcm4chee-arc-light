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
 * Portions created by the Initial Developer are Copyright (C) 2015-2019
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
import org.dcm4che3.data.UID;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.entity.Patient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Feb 2019
 */
class ParticipantObjectID {

    static ParticipantObjectIdentificationBuilder patientPOI(AuditInfo auditInfo, SpoolFileReader reader) {
        ParticipantObjectIdentificationBuilder.Builder patientPOIBuilder = patientPOIBuilder(auditInfo)
                .detail(hl7ParticipantObjectDetail(reader).toArray(new ParticipantObjectDetail[0]));

        String patVerStatus = auditInfo.getField(AuditInfo.PAT_VERIFICATION_STATUS);
        if (patVerStatus != null
                && Patient.VerificationStatus.valueOf(patVerStatus) != Patient.VerificationStatus.UNVERIFIED)
            patientPOIBuilder.lifeCycle(AuditMessages.ParticipantObjectDataLifeCycle.Verification);

        return patientPOIBuilder.build();
    }

    static ParticipantObjectIdentificationBuilder patientPOI(SpoolFileReader reader) {
        return patientPOIBuilder(new AuditInfo(reader.getInstanceLines().get(0))).build();
    }

    private static ParticipantObjectIdentificationBuilder.Builder patientPOIBuilder(AuditInfo auditInfo) {
        return new ParticipantObjectIdentificationBuilder.Builder(
                auditInfo.getField(AuditInfo.P_ID),
                AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                AuditMessages.ParticipantObjectTypeCode.Person,
                AuditMessages.ParticipantObjectTypeCodeRole.Patient)
                .name(auditInfo.getField(AuditInfo.P_NAME));
    }

    private static List<ParticipantObjectDetail> hl7ParticipantObjectDetail(SpoolFileReader reader) {
        List<ParticipantObjectDetail> detail = new ArrayList<>();
        if (reader.getData().length > 0)
            detail.add(hl7ParticipantObjectDetail(reader.getData()));
        if (reader.getAck().length > 0)
            detail.add(hl7ParticipantObjectDetail(reader.getAck()));
        return detail;
    }

    private static ParticipantObjectDetail hl7ParticipantObjectDetail(byte[] val) {
        ParticipantObjectDetail detail = new ParticipantObjectDetail();
        detail.setType("HL7v2 Message");
        detail.setValue(val);
        return detail;
    }

    private static ParticipantObjectIdentificationBuilder submissionSetPOI(AuditInfo auditInfo) {
        return new ParticipantObjectIdentificationBuilder.Builder(
                auditInfo.getField(AuditInfo.SUBMISSION_SET_UID),
                AuditMessages.ParticipantObjectIDTypeCode.IHE_XDS_METADATA,
                AuditMessages.ParticipantObjectTypeCode.SystemObject,
                AuditMessages.ParticipantObjectTypeCodeRole.Job)
                .build();
    }

    static ParticipantObjectIdentificationBuilder[] submissionSetParticipants(AuditInfo auditInfo) {
        ParticipantObjectIdentificationBuilder[] submissionSetParticipants = new ParticipantObjectIdentificationBuilder[2];
        submissionSetParticipants[0] = submissionSetPOI(auditInfo);
        submissionSetParticipants[1] = patientPOIBuilder(auditInfo).build();
        return submissionSetParticipants;
    }

    static ParticipantObjectIdentificationBuilder[] studyPatParticipants(
            AuditInfo auditInfo, List<String> instanceInfoLines, AuditUtils.EventType eventType, AuditLogger auditLogger) {
        ParticipantObjectIdentificationBuilder[] studyPatParticipants = new ParticipantObjectIdentificationBuilder[2];
        ParticipantObjectIdentificationBuilder.Builder studyPOIBuilder = studyPOI(auditInfo)
                .desc(participantObjDesc(
                        auditInfo,
                        instanceInfoLines,
                        auditInfo.getField(AuditInfo.OUTCOME) != null || auditLogger.isIncludeInstanceUID())
                        .build());

        if ((eventType.eventClass == AuditUtils.EventClass.STORE_WADOR
                && !eventType.eventActionCode.equals(AuditMessages.EventActionCode.Read)
                || eventType.eventClass == AuditUtils.EventClass.IMPAX))
            studyPOIBuilder.lifeCycle(AuditMessages.ParticipantObjectDataLifeCycle.OriginationCreation);

        studyPatParticipants[0] = studyPOIBuilder.build();
        studyPatParticipants[1] = patientPOIBuilder(auditInfo).build();
        return studyPatParticipants;
    }

    static ParticipantObjectIdentificationBuilder[] studyPatParticipants(
            AuditInfo auditInfo, InstanceInfo instanceInfo) {
        ParticipantObjectIdentificationBuilder[] studyPatParticipants = new ParticipantObjectIdentificationBuilder[2];
        studyPatParticipants[0] = studyPOI(auditInfo)
                                    .desc(participantObjDesc(instanceInfo, true)
                                            .build())
                                    .lifeCycle(AuditMessages.ParticipantObjectDataLifeCycle.OriginationCreation)
                                    .build();
        studyPatParticipants[1] = patientPOIBuilder(auditInfo).build();
        return studyPatParticipants;
    }

    static ParticipantObjectIdentificationBuilder[] studyPatParticipants(
            AuditInfo auditInfo, SpoolFileReader reader, AuditLogger auditLogger) {
        InstanceInfo instanceInfo = new InstanceInfo();
        instanceInfo.addAcc(auditInfo);

        ParticipantObjectIdentificationBuilder[] studyPatParticipants = new ParticipantObjectIdentificationBuilder[2];
        List<ParticipantObjectDetail> participantObjectDetails = hl7ParticipantObjectDetail(reader);
        if (auditInfo.getField(AuditInfo.EXPIRATION_DATE) != null)
            participantObjectDetails.add(AuditMessages.createParticipantObjectDetail(
                    "Expiration Date",
                    auditInfo.getField(AuditInfo.EXPIRATION_DATE)));
        if (auditInfo.getField(AuditInfo.STUDY_ACCESS_CTRL_ID) != null)
            participantObjectDetails.add(AuditMessages.createParticipantObjectDetail(
                    "Study Access Control ID",
                    auditInfo.getField(AuditInfo.STUDY_ACCESS_CTRL_ID)));
        studyPatParticipants[0] = studyPOI(auditInfo)
                .desc(participantObjDesc(instanceInfo, auditLogger.isIncludeInstanceUID()).build())
                .detail(participantObjectDetails.toArray(new ParticipantObjectDetail[0]))
                .build();
        if (auditInfo.getField(AuditInfo.P_ID) != null)
            studyPatParticipants[1] = patientPOIBuilder(auditInfo).build();
        return studyPatParticipants;
    }

    static ParticipantObjectIdentificationBuilder[] studyPatParticipants(
            SpoolFileReader reader, AuditInfo auditInfo, AuditLogger auditLogger) {
        ParticipantObjectIdentificationBuilder[] studyPatParticipants = new ParticipantObjectIdentificationBuilder[2];
        String[] studyUIDs = StringUtils.split(auditInfo.getField(AuditInfo.STUDY_UID), ';');
        studyPatParticipants[0] = studyPOI(studyUIDs[0], null)
                                    .desc(participantObjDesc(auditInfo, reader.getInstanceLines(),
                                            studyUIDs.length > 1 || auditInfo.getField(AuditInfo.OUTCOME) != null
                                                    || auditLogger.isIncludeInstanceUID())
                                            .pocsStudyUIDs(studyUIDs)
                                            .build())
                                    .lifeCycle(AuditMessages.ParticipantObjectDataLifeCycle.Verification)
                                    .build();
        studyPatParticipants[1] = patientPOIBuilder(auditInfo).build();
        return studyPatParticipants;
    }

    static ParticipantObjectIdentificationBuilder.Builder studyPOI(AuditInfo auditInfo) {
        return studyPOI(
                auditInfo.getField(AuditInfo.STUDY_UID),
                auditInfo.getField(AuditInfo.STUDY_DATE));
    }

    private static ParticipantObjectIdentificationBuilder.Builder studyPOI(String uid, String studyDate) {
        return new ParticipantObjectIdentificationBuilder.Builder(
                uid,
                AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID,
                AuditMessages.ParticipantObjectTypeCode.SystemObject,
                AuditMessages.ParticipantObjectTypeCodeRole.Report)
                .detail(AuditMessages.createParticipantObjectDetail("StudyDate", studyDate));
    }

    static ParticipantObjectIdentificationBuilder studyPOI(
            String studyUID, InstanceInfo instanceInfo, boolean showIUID) {
        return studyPOI(studyUID, null)
                .desc(participantObjDesc(instanceInfo, showIUID).build())
                .detail(instanceInfo.getStudyDate().stream()
                        .map(studyDate -> AuditMessages.createParticipantObjectDetail("StudyDate", studyDate))
                        .toArray(ParticipantObjectDetail[]::new))
                .build();
    }

    private static ParticipantObjectDescriptionBuilder.Builder participantObjDesc(
            AuditInfo auditInfo, List<String> instanceInfoLines, boolean showIUID) {
        InstanceInfo instanceInfo = new InstanceInfo();
        instanceInfo.addAcc(auditInfo);
        instanceInfoLines.forEach(line -> {
            AuditInfo info = new AuditInfo(line);
            instanceInfo.addMpps(info);
            instanceInfo.addSOPInstance(info);
        });

        return participantObjDesc(instanceInfo, showIUID);
    }

    private static ParticipantObjectDescriptionBuilder.Builder participantObjDesc(InstanceInfo instanceInfo, boolean showIUID) {
        return new ParticipantObjectDescriptionBuilder.Builder()
                .sopC(instanceInfo.getSopClassMap().entrySet().stream().map(entry ->
                        AuditMessages.createSOPClass(
                                showIUID ? entry.getValue() : null,
                                entry.getKey(),
                                entry.getValue().size()))
                        .toArray(SOPClass[]::new))
                .acc(instanceInfo.getAcc())
                .mpps(instanceInfo.getMpps());
    }

    static ParticipantObjectIdentificationBuilder qidoParticipant(AuditInfo auditInfo) {
        ParticipantObjectIdentificationBuilder.Builder qidoParticipant = new ParticipantObjectIdentificationBuilder.Builder(
                auditInfo.getField(AuditInfo.Q_POID),
                AuditMessages.ParticipantObjectIDTypeCode.QIDO_QUERY,
                AuditMessages.ParticipantObjectTypeCode.SystemObject,
                AuditMessages.ParticipantObjectTypeCodeRole.Query)
                .detail(AuditMessages.createParticipantObjectDetail("QueryEncoding", StandardCharsets.UTF_8.name()));
        if (auditInfo.getField(AuditInfo.Q_STRING) != null)
            qidoParticipant.query(auditInfo.getField(AuditInfo.Q_STRING).getBytes());
        return qidoParticipant.build();
    }

    static ParticipantObjectIdentificationBuilder cFindParticipant(AuditInfo auditInfo, byte[] data) {
        return new ParticipantObjectIdentificationBuilder.Builder(
                auditInfo.getField(AuditInfo.Q_POID),
                AuditMessages.ParticipantObjectIDTypeCode.SOPClassUID,
                AuditMessages.ParticipantObjectTypeCode.SystemObject,
                AuditMessages.ParticipantObjectTypeCodeRole.Report)
                .query(data)
                .detail(AuditMessages.createParticipantObjectDetail("TransferSyntax", UID.ImplicitVRLittleEndian))
                .build();
    }

    static ParticipantObjectIdentificationBuilder taskParticipant(AuditInfo auditInfo) {
        if (auditInfo.getField(AuditInfo.QUEUE_MSG) == null)
            return tasksParticipant(auditInfo);

        return new ParticipantObjectIdentificationBuilder.Builder(
                auditInfo.getField(AuditInfo.TASK_POID),
                AuditMessages.ParticipantObjectIDTypeCode.TASK,
                AuditMessages.ParticipantObjectTypeCode.SystemObject,
                null)
                .detail(AuditMessages.createParticipantObjectDetail("Task", auditInfo.getField(AuditInfo.QUEUE_MSG)))
                .build();
    }

    private static ParticipantObjectIdentificationBuilder tasksParticipant(AuditInfo auditInfo) {
        return new ParticipantObjectIdentificationBuilder.Builder(
                auditInfo.getField(AuditInfo.TASK_POID),
                AuditMessages.ParticipantObjectIDTypeCode.TASKS,
                AuditMessages.ParticipantObjectTypeCode.SystemObject,
                null)
                .detail(AuditMessages.createParticipantObjectDetail("Filters", auditInfo.getField(AuditInfo.FILTERS)),
                        AuditMessages.createParticipantObjectDetail("Count", auditInfo.getField(AuditInfo.COUNT)),
                        AuditMessages.createParticipantObjectDetail("Failed", auditInfo.getField(AuditInfo.FAILED)))
                .build();
    }

    static ParticipantObjectIdentificationBuilder softwareConfParticipant(SpoolFileReader reader, AuditInfo auditInfo) {
        return new ParticipantObjectIdentificationBuilder.Builder(
                auditInfo.getField(AuditInfo.CALLED_USERID),
                AuditMessages.ParticipantObjectIDTypeCode.DeviceName,
                AuditMessages.ParticipantObjectTypeCode.SystemObject,
                null)
                .detail(AuditMessages.createParticipantObjectDetail("Alert Description",
                        !reader.getInstanceLines().isEmpty()
                                ? String.join("\n", reader.getInstanceLines())
                                : null))
                .build();
    }
}
