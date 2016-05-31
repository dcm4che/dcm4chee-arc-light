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

import org.dcm4che3.audit.AuditMessages;
import org.dcm4che3.audit.ParticipantObjectContainsStudy;
import org.dcm4che3.audit.ParticipantObjectIdentification;
import org.dcm4che3.audit.SOPClass;

import java.util.*;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since May 2016
 */

public class PatientStudyInfoUtils extends AuditServiceUtils {
    static LinkedHashSet<Object> getDeletionObjForSpooling(HashMap<String, HashSet<String>> sopClassMap,
                                                           PatientStudyInfo psi) {
        LinkedHashSet<Object> obj = new LinkedHashSet<>();
        obj.add(psi);
        for (Map.Entry<String, HashSet<String>> entry : sopClassMap.entrySet()) {
            obj.add(new InstanceInfo(entry.getKey(), String.valueOf(entry.getValue().size())));
        }
        return obj;
    }

    static ParticipantObjectContainsStudy getParticipantObjectContainsStudy(PatientStudyInfo psi) {
        return AuditMessages.createParticipantObjectContainsStudy(
                AuditMessages.createStudyIDs(psi.getField(PatientStudyInfo.STUDY_UID)));
    }

    static List<ParticipantObjectIdentification> poiListForDeletion(PatientStudyInfo psi, AuditServiceUtils.EventType et,
                                                                    HashSet<String> instanceLines) {
        List<ParticipantObjectIdentification> poiList = new ArrayList<>();
        poiList.add(studyPOIForDeletion(psi, et, instanceLines));
        poiList.add(patientPOIForDeletion(psi));
        return poiList;
    }

    private static ParticipantObjectIdentification studyPOIForDeletion(PatientStudyInfo psi, AuditServiceUtils.EventType et,
                                                                       HashSet<String> instanceLines) {
        return AuditMessages.createParticipantObjectIdentification(
                psi.getField(PatientStudyInfo.STUDY_UID),
                AuditMessages.ParticipantObjectIDTypeCode.StudyInstanceUID,
                null, null, AuditMessages.ParticipantObjectTypeCode.SystemObject,
                AuditMessages.ParticipantObjectTypeCodeRole.Report, null, null,
                AuditMessages.createParticipantObjectDescription(
                    et.eventClass == AuditServiceUtils.EventClass.PERM_DELETE
                        ? getAccessions(psi.getField(PatientStudyInfo.ACCESSION_NO)) : null,
                    null, getSopClasses(instanceLines), null, null, getParticipantObjectContainsStudy(psi)),
                getParticipantObjectDetail(psi, null, et));
    }

    private static ParticipantObjectIdentification patientPOIForDeletion(PatientStudyInfo psi) {
        return AuditMessages.createParticipantObjectIdentification(
                psi.getField(PatientStudyInfo.PATIENT_ID),
                AuditMessages.ParticipantObjectIDTypeCode.PatientNumber,
                psi.getField(PatientStudyInfo.PATIENT_NAME), null, AuditMessages.ParticipantObjectTypeCode.Person,
                AuditMessages.ParticipantObjectTypeCodeRole.Patient, null, null, null, null);
    }

    private static HashSet<SOPClass> getSopClasses(HashSet<String> instanceLines) {
        HashSet<SOPClass> sopC = new HashSet<>();
        for (String line : instanceLines) {
            InstanceInfo ii = new InstanceInfo(line);
            sopC.add(AuditMessages.createSOPClass(null,
                ii.getField(InstanceInfo.CLASS_UID),
                Integer.parseInt(ii.getField(InstanceInfo.INSTANCE_UID))));
        }
        return sopC;
    }

}
