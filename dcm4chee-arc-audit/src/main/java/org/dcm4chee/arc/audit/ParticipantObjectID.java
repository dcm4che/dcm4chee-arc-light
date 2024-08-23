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
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4chee.arc.entity.Patient;

import java.text.ParsePosition;
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
                .detail(hl7ParticipantObjectDetail(reader).toArray(new ParticipantObjectDetail[0]));

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

    static List<ParticipantObjectDetail> hl7ParticipantObjectDetail(SpoolFileReader reader) {
        List<ParticipantObjectDetail> detail = new ArrayList<>();
        hl7ParticipantObjectDetail(detail, reader.getData());
        hl7ParticipantObjectDetail(detail, reader.getAck());
        return detail;
    }

    private static void hl7ParticipantObjectDetail(List<ParticipantObjectDetail> detail, byte[] data) {
        if (data.length > 0) {
            HL7Segment msh = HL7Segment.parseMSH(data, data.length, new ParsePosition(0));
            String messageType = msh.getMessageType();
            String messageControlID = msh.getMessageControlID();
            detail.add(hl7ParticipantObjectDetail("HL7v2 Message", data));
            if (messageType != null)
                detail.add(hl7ParticipantObjectDetail("MSH-9", messageType.getBytes()));
            if (messageControlID != null)
                detail.add(hl7ParticipantObjectDetail("MSH-10", messageControlID.getBytes()));
        }
    }

    private static ParticipantObjectDetail hl7ParticipantObjectDetail(String key, byte[] val) {
        ParticipantObjectDetail detail = new ParticipantObjectDetail();
        detail.setType(key);
        detail.setValue(val);
        return detail;
    }


    static ParticipantObjectIdentification[] studyPatParticipants(
            AuditInfo auditInfo, SpoolFileReader reader, AuditLogger auditLogger) {
        InstanceInfo instanceInfo = new InstanceInfo();
        instanceInfo.addAcc(auditInfo);
        instanceInfo.addMpps(auditInfo);

        boolean hasPatient = auditInfo.getField(AuditInfo.P_ID) != null;
        ParticipantObjectIdentification[] studyPatParticipants
                = new ParticipantObjectIdentification[hasPatient ? 2 : 1];
        List<ParticipantObjectDetail> participantObjectDetails = hl7ParticipantObjectDetail(reader);
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

    private static ParticipantObjectDescriptionBuilder participantObjDesc(InstanceInfo instanceInfo, boolean showIUID) {
        return new ParticipantObjectDescriptionBuilder()
                .sopC(instanceInfo.getSopClassMap().entrySet().stream().map(entry ->
                        AuditMessages.createSOPClass(
                                showIUID ? entry.getValue() : null,
                                entry.getKey(),
                                entry.getValue().size()))
                        .toArray(SOPClass[]::new))
                .acc(instanceInfo.getAcc())
                .mpps(instanceInfo.getMppsArray());
    }
}
