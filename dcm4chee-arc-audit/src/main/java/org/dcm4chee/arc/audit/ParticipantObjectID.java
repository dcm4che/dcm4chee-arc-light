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
import org.dcm4che3.audit.ParticipantObjectDetail;
import org.dcm4che3.audit.SOPClass;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.entity.Patient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Feb 2019
 */
class ParticipantObjectID {

    static ParticipantObjectIdentification patientPOI(AuditInfo auditInfo, SpoolFileReader reader) {
        ParticipantObjectIdentificationBuilder patientPOIBuilder = patientPOIBuilder(auditInfo)
                .detail(hl7ParticipantObjectDetail(reader, auditInfo).toArray(new ParticipantObjectDetail[0]));

        String patVerStatus = auditInfo.getField(AuditInfo.PAT_VERIFICATION_STATUS);
        if (patVerStatus != null
                && Patient.VerificationStatus.valueOf(patVerStatus) != Patient.VerificationStatus.UNVERIFIED)
            patientPOIBuilder.lifeCycle(AuditMessages.ParticipantObjectDataLifeCycle.Verification);

        return patientPOIBuilder.build();
    }

    static ParticipantObjectIdentification patientPOI(SpoolFileReader reader) {
        return patientPOIBuilder(new AuditInfo(reader.getInstanceLines().get(0))).build();
    }

    static ParticipantObjectIdentificationBuilder patientPOIBuilder(AuditInfo auditInfo) {
        return new ParticipantObjectIdentificationBuilder(
                auditInfo.getField(AuditInfo.P_ID),
                AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                AuditMessages.ParticipantObjectTypeCode.Person,
                AuditMessages.ParticipantObjectTypeCodeRole.Patient)
                .name(auditInfo.getField(AuditInfo.P_NAME));
    }

    private static List<ParticipantObjectDetail> hl7ParticipantObjectDetail(SpoolFileReader reader, AuditInfo auditInfo) {
        List<ParticipantObjectDetail> detail = new ArrayList<>();
        if (reader.getData().length > 0)
            detail.add(hl7ParticipantObjectDetail("HL7v2 Message", reader.getData()));
        if (reader.getAck().length > 0)
            detail.add(hl7ParticipantObjectDetail("HL7v2 Message", reader.getAck()));
        if (auditInfo.getField(AuditInfo.HL7_MSH_9) != null)
            detail.add(hl7ParticipantObjectDetail(
                    "MSH-9", auditInfo.getField(AuditInfo.HL7_MSH_9).getBytes()));
        if (auditInfo.getField(AuditInfo.HL7_MSH_10) != null)
            detail.add(hl7ParticipantObjectDetail(
                    "MSH-10", auditInfo.getField(AuditInfo.HL7_MSH_10).getBytes()));
        if (auditInfo.getField(AuditInfo.HL7_MSH2_9) != null)
            detail.add(hl7ParticipantObjectDetail(
                    "MSH2-9", auditInfo.getField(AuditInfo.HL7_MSH2_9).getBytes()));
        if (auditInfo.getField(AuditInfo.HL7_MSH2_10) != null)
            detail.add(hl7ParticipantObjectDetail(
                    "MSH2-10", auditInfo.getField(AuditInfo.HL7_MSH2_10).getBytes()));
        return detail;
    }

    private static ParticipantObjectDetail hl7ParticipantObjectDetail(String key, byte[] val) {
        ParticipantObjectDetail detail = new ParticipantObjectDetail();
        detail.setType(key);
        detail.setValue(val);
        return detail;
    }

    private static ParticipantObjectIdentificationBuilder submissionSetPOI(AuditInfo auditInfo) {
        return new ParticipantObjectIdentificationBuilder(
                auditInfo.getField(AuditInfo.SUBMISSION_SET_UID),
                AuditMessages.ParticipantObjectIDTypeCode.IHE_XDS_METADATA,
                AuditMessages.ParticipantObjectTypeCode.SystemObject,
                AuditMessages.ParticipantObjectTypeCodeRole.Job);
    }

    static ParticipantObjectIdentification[] submissionSetParticipants(AuditInfo auditInfo) {
        ParticipantObjectIdentification[] submissionSetParticipants = new ParticipantObjectIdentification[2];
        submissionSetParticipants[0] = submissionSetPOI(auditInfo).build();
        submissionSetParticipants[1] = patientPOIBuilder(auditInfo).build();
        return submissionSetParticipants;
    }

    static ParticipantObjectIdentification[] studyPatParticipants(
            AuditInfo auditInfo, List<String> instanceInfoLines, AuditUtils.EventType eventType, AuditLogger auditLogger) {
        ParticipantObjectIdentification[] studyPatParticipants = new ParticipantObjectIdentification[2];
        ParticipantObjectIdentificationBuilder studyPOIBuilder = studyPOI(auditInfo.getField(AuditInfo.STUDY_UID))
                .detail(AuditMessages.createParticipantObjectDetail("StudyDate", auditInfo.getField(AuditInfo.STUDY_DATE)))
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

    static ParticipantObjectIdentification[] studyPatParticipants(
            AuditInfo auditInfo, InstanceInfo instanceInfo) {
        ParticipantObjectIdentification[] studyPatParticipants = new ParticipantObjectIdentification[2];
        studyPatParticipants[0] = studyPOI(auditInfo.getField(AuditInfo.STUDY_UID))
                                    .detail(AuditMessages.createParticipantObjectDetail(
                                            "StudyDate", auditInfo.getField(AuditInfo.STUDY_DATE)))
                                    .desc(participantObjDesc(instanceInfo, true)
                                            .build())
                                    .lifeCycle(AuditMessages.ParticipantObjectDataLifeCycle.OriginationCreation)
                                    .build();
        studyPatParticipants[1] = patientPOIBuilder(auditInfo).build();
        return studyPatParticipants;
    }

    static ParticipantObjectIdentification[] studyPatParticipants(
            AuditInfo auditInfo, SpoolFileReader reader, AuditLogger auditLogger) {
        InstanceInfo instanceInfo = new InstanceInfo();
        instanceInfo.addAcc(auditInfo);
        instanceInfo.addMpps(auditInfo);

        boolean hasPatient = auditInfo.getField(AuditInfo.P_ID) != null;
        ParticipantObjectIdentification[] studyPatParticipants
                = new ParticipantObjectIdentification[hasPatient ? 2 : 1];
        List<ParticipantObjectDetail> participantObjectDetails = hl7ParticipantObjectDetail(reader, auditInfo);
        participantObjectDetails.add(AuditMessages.createParticipantObjectDetail(
                    "ExpirationDate", auditInfo.getField(AuditInfo.EXPIRATION_DATE)));
        participantObjectDetails.add(AuditMessages.createParticipantObjectDetail(
                "StudyDate", auditInfo.getField(AuditInfo.STUDY_DATE)));
        studyPatParticipants[0] = studyPOI(auditInfo.getField(AuditInfo.STUDY_UID))
                .desc(participantObjDesc(instanceInfo, auditLogger.isIncludeInstanceUID()).build())
                .detail(participantObjectDetails.toArray(new ParticipantObjectDetail[0]))
                .build();
        if (hasPatient)
            studyPatParticipants[1] = patientPOIBuilder(auditInfo).build();
        return studyPatParticipants;
    }

    static ParticipantObjectIdentification[] studyPatParticipants(
            SpoolFileReader reader, AuditInfo auditInfo, AuditLogger auditLogger) {
        ParticipantObjectIdentification[] studyPatParticipants = new ParticipantObjectIdentification[2];
        String[] studyUIDs = StringUtils.split(auditInfo.getField(AuditInfo.STUDY_UID), ';');
        studyPatParticipants[0] = studyPOI(studyUIDs[0])
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

    static ParticipantObjectIdentificationBuilder studyPOI(String uid) {
        return new ParticipantObjectIdentificationBuilder(
                uid,
                AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID,
                AuditMessages.ParticipantObjectTypeCode.SystemObject,
                AuditMessages.ParticipantObjectTypeCodeRole.Report);
    }

    static ParticipantObjectIdentification studyPOI(
            String studyUID, InstanceInfo instanceInfo, boolean showIUID) {
        return studyPOI(studyUID)
                .desc(participantObjDesc(instanceInfo, showIUID).build())
                .detail(instanceInfo.getStudyDate().stream()
                        .map(studyDate -> AuditMessages.createParticipantObjectDetail("StudyDate", studyDate))
                        .toArray(ParticipantObjectDetail[]::new))
                .build();
    }

    private static ParticipantObjectDescriptionBuilder participantObjDesc(
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

    private static ParticipantObjectDescriptionBuilder participantObjDesc(InstanceInfo instanceInfo, boolean showIUID) {
        return new ParticipantObjectDescriptionBuilder()
                .sopC(instanceInfo.getSopClassMap().entrySet().stream().map(entry ->
                        AuditMessages.createSOPClass(
                                showIUID ? entry.getValue() : null,
                                entry.getKey(),
                                entry.getValue().size()))
                        .toArray(SOPClass[]::new))
                .acc(instanceInfo.getAcc())
                .mpps(instanceInfo.getMpps());
    }

    static ParticipantObjectIdentification qidoParticipant(AuditInfo auditInfo) {
        ParticipantObjectIdentificationBuilder qidoParticipant = new ParticipantObjectIdentificationBuilder(
                auditInfo.getField(AuditInfo.Q_POID),
                AuditMessages.ParticipantObjectIDTypeCode.QIDO_QUERY,
                AuditMessages.ParticipantObjectTypeCode.SystemObject,
                AuditMessages.ParticipantObjectTypeCodeRole.Query)
                .detail(AuditMessages.createParticipantObjectDetail("QueryEncoding", StandardCharsets.UTF_8.name()));
        if (auditInfo.getField(AuditInfo.Q_STRING) != null)
            qidoParticipant.query(auditInfo.getField(AuditInfo.Q_STRING).getBytes());
        return qidoParticipant.build();
    }

    static ParticipantObjectIdentification cFindParticipant(AuditInfo auditInfo, byte[] data) {
        return new ParticipantObjectIdentificationBuilder(
                auditInfo.getField(AuditInfo.Q_POID),
                AuditMessages.ParticipantObjectIDTypeCode.SOPClassUID,
                AuditMessages.ParticipantObjectTypeCode.SystemObject,
                AuditMessages.ParticipantObjectTypeCodeRole.Report)
                .query(data)
                .detail(AuditMessages.createParticipantObjectDetail("TransferSyntax", UID.ImplicitVRLittleEndian))
                .build();
    }

    static ParticipantObjectIdentification taskParticipant(AuditInfo auditInfo) {
        if (auditInfo.getField(AuditInfo.QUEUE_MSG) == null)
            return tasksParticipant(auditInfo);

        return new ParticipantObjectIdentificationBuilder(
                auditInfo.getField(AuditInfo.TASK_POID),
                AuditMessages.ParticipantObjectIDTypeCode.TASK,
                AuditMessages.ParticipantObjectTypeCode.SystemObject,
                null)
                .detail(AuditMessages.createParticipantObjectDetail("Task", auditInfo.getField(AuditInfo.QUEUE_MSG)))
                .build();
    }

    private static ParticipantObjectIdentification tasksParticipant(AuditInfo auditInfo) {
        return new ParticipantObjectIdentificationBuilder(
                auditInfo.getField(AuditInfo.TASK_POID),
                AuditMessages.ParticipantObjectIDTypeCode.TASKS,
                AuditMessages.ParticipantObjectTypeCode.SystemObject,
                null)
                .detail(AuditMessages.createParticipantObjectDetail("Filters", auditInfo.getField(AuditInfo.FILTERS)),
                        AuditMessages.createParticipantObjectDetail("QueueName", auditInfo.getField(AuditInfo.QUEUE_NAME)),
                        AuditMessages.createParticipantObjectDetail("Count", auditInfo.getField(AuditInfo.COUNT)),
                        AuditMessages.createParticipantObjectDetail("Failed", auditInfo.getField(AuditInfo.FAILED)))
                .build();
    }

    static ParticipantObjectIdentification softwareConfParticipant(SpoolFileReader reader, AuditInfo auditInfo) {
        return new ParticipantObjectIdentificationBuilder(
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
