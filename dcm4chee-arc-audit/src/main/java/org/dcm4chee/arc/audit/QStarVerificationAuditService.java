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
 * Portions created by the Initial Developer are Copyright (C) 2015-2023
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
import org.dcm4che3.audit.AuditMessages.EventOutcomeIndicator;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4chee.arc.audit.AuditUtils.EventType;
import org.dcm4chee.arc.qstar.QStarVerification;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Oct 2023
 */
class QStarVerificationAuditService extends AuditService {

    enum QStarAccessStateEventOutcome {
        OK(""),
        NONE("QStar Access State: Not present"),
        EMPTY("QStar Access State: Created but no data written"),
        UNSTABLE("QStar Access State: Created, primary only"),
        OUT_OF_CACHE( "QStar Access State: Fully migrated, out of cache"),
        OFFLINE("QStar Access State: Offline"),
        ERROR_STATUS("Failed to get QStar Access State");

        private final String description;

        QStarAccessStateEventOutcome(String description) {
            this.description = description;
        }

        static QStarAccessStateEventOutcome fromQStarVerification(QStarVerification qStarVerification) {
            switch (qStarVerification.status) {
                case QSTAR_ACCESS_STATE_NONE:
                    return NONE;
                case QSTAR_ACCESS_STATE_EMPTY:
                    return EMPTY;
                case QSTAR_ACCESS_STATE_UNSTABLE:
                    return UNSTABLE;
                case QSTAR_ACCESS_STATE_OUT_OF_CACHE:
                    return OUT_OF_CACHE;
                case QSTAR_ACCESS_STATE_OFFLINE:
                    return OFFLINE;
                case QSTAR_ACCESS_STATE_ERROR_STATUS:
                    return ERROR_STATUS;
            }
            return OK;
        }

        String getDescription() {
            return description;
        }

        static String fromOutcome(String outcome) {
            if (outcome == null)
                return EventOutcomeIndicator.Success;

            switch (outcome) {
                case "QStar Access State: Created, primary only":
                case "QStar Access State: Fully migrated, out of cache":
                case "QStar Access State: Offline":
                case "Failed to get QStar Access State":
                    return EventOutcomeIndicator.MinorFailure;
                case "QStar Access State: Not present":
                case "QStar Access State: Created but no data written":
                    return EventOutcomeIndicator.SeriousFailure;
                default:
                    return EventOutcomeIndicator.Success;
            }
        }
    }

    static void auditQStarVerification(AuditLogger auditLogger, Path path, EventType eventType) {
        SpoolFileReader reader = new SpoolFileReader(path);
        AuditInfo auditInfo = new AuditInfo(reader.getMainInfo());
        EventIdentification eventIdentification = getEventIdentification(auditInfo, eventType);
        eventIdentification.setEventDateTime(getEventTime(path, auditLogger));
        List<ActiveParticipant> activeParticipants = new ArrayList<>();
        activeParticipants.add(getDeviceActiveParticipant(auditInfo, eventType));
        activeParticipants.add(getQStarActiveParticipant(auditInfo, eventType));
        ParticipantObjectIdentification study = getStudyParticipantObjectIdentification(auditInfo);
        boolean showSOPIUIDs = auditLogger.isIncludeInstanceUID() || !eventIdentification.getEventOutcomeIndicator().equals("0");
        study.setParticipantObjectDescription(getStudyParticipantObjDesc(reader, showSOPIUIDs));
        ParticipantObjectIdentification patient = getPatientParticipantObjectIdentification(auditInfo);
        emitAuditMessage(auditLogger, eventIdentification, activeParticipants, study, patient);
    }

    private static EventIdentification getEventIdentification(AuditInfo auditInfo, EventType eventType) {
        String outcomeDescription = auditInfo.getField(AuditInfo.OUTCOME);
        EventIdentification ei = new EventIdentification();
        ei.setEventID(eventType.eventID);
        ei.setEventActionCode(eventType.eventActionCode);
        ei.setEventOutcomeDescription(outcomeDescription);
        ei.setEventOutcomeIndicator(QStarAccessStateEventOutcome.fromOutcome(outcomeDescription));
        return ei;
    }

    private static ActiveParticipant getDeviceActiveParticipant(AuditInfo auditInfo, EventType eventType) {
        ActiveParticipant deviceActiveParticipant = new ActiveParticipant();
        deviceActiveParticipant.setUserID(auditInfo.getField(AuditInfo.CALLING_USERID));
        deviceActiveParticipant.setUserIsRequestor(true);
        deviceActiveParticipant.setUserIDTypeCode(AuditMessages.UserIDTypeCode.DeviceName);
        deviceActiveParticipant.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        deviceActiveParticipant.getRoleIDCode().add(eventType.source);
        return deviceActiveParticipant;
    }

    private static ActiveParticipant getQStarActiveParticipant(AuditInfo auditInfo, EventType eventType) {
        ActiveParticipant qStarActiveParticipant = new ActiveParticipant();
        qStarActiveParticipant.setUserID(auditInfo.getField(AuditInfo.CALLED_USERID));
        qStarActiveParticipant.setUserIsRequestor(false);
        qStarActiveParticipant.setUserIDTypeCode(AuditMessages.UserIDTypeCode.URI);
        qStarActiveParticipant.setUserTypeCode(AuditMessages.UserTypeCode.Application);
        qStarActiveParticipant.getRoleIDCode().add(eventType.destination);
        return qStarActiveParticipant;
    }

    private static ParticipantObjectIdentification getStudyParticipantObjectIdentification(AuditInfo auditInfo) {
        ParticipantObjectIdentification study = new ParticipantObjectIdentification();
        study.setParticipantObjectID(auditInfo.getField(AuditInfo.STUDY_UID));
        study.setParticipantObjectDataLifeCycle(AuditMessages.ParticipantObjectDataLifeCycle.Verification);
        study.setParticipantObjectIDTypeCode(AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID);
        study.setParticipantObjectTypeCode(AuditMessages.ParticipantObjectTypeCode.SystemObject);
        study.setParticipantObjectTypeCodeRole(AuditMessages.ParticipantObjectTypeCodeRole.Report);
        return study;
    }

    private static ParticipantObjectDescription getStudyParticipantObjDesc(SpoolFileReader reader, boolean showSOPIUIDs) {
        ParticipantObjectDescription studyParticipantObjDesc = new ParticipantObjectDescription();
        InstanceInfo instances = new InstanceInfo();
        reader.getInstanceLines().forEach(instanceLine -> {
            AuditInfo info = new AuditInfo(instanceLine);
            instances.addSOPInstance(info);
        });
        instances.getSopClassMap()
                .forEach((sopCUID, sopIUIDs) ->
                        studyParticipantObjDesc.getSOPClass().add(
                                AuditMessages.createSOPClass(
                                        showSOPIUIDs ? sopIUIDs : null,
                                        sopCUID,
                                        sopIUIDs.size())
                        )
                );
        return studyParticipantObjDesc;
    }

    private static ParticipantObjectIdentification getPatientParticipantObjectIdentification(AuditInfo auditInfo) {
        ParticipantObjectIdentification patient = new ParticipantObjectIdentification();
        patient.setParticipantObjectID(auditInfo.getField(AuditInfo.P_ID));
        patient.setParticipantObjectIDTypeCode(AuditMessages.ParticipantObjectIDTypeCode.PatientNumber);
        patient.setParticipantObjectTypeCode(AuditMessages.ParticipantObjectTypeCode.Person);
        patient.setParticipantObjectTypeCodeRole(AuditMessages.ParticipantObjectTypeCodeRole.Patient);
        return patient;
    }
}
