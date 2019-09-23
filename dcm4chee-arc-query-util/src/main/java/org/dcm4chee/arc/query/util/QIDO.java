/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 *  The contents of this file are subject to the Mozilla Public License Version
 *  1.1 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  http://www.mozilla.org/MPL/
 *
 *  Software distributed under the License is distributed on an "AS IS" basis,
 *  WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 *  for the specific language governing rights and limitations under the
 *  License.
 *
 *  The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 *  Java(TM), hosted at https://github.com/dcm4che.
 *
 *  The Initial Developer of the Original Code is
 *  J4Care.
 *  Portions created by the Initial Developer are Copyright (C) 2015-2017
 *  the Initial Developer. All Rights Reserved.
 *
 *  Contributor(s):
 *  See @authors listed below
 *
 *  Alternatively, the contents of this file may be used under the terms of
 *  either the GNU General Public License Version 2 or later (the "GPL"), or
 *  the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 *  in which case the provisions of the GPL or the LGPL are applicable instead
 *  of those above. If you wish to allow use of your version of this file only
 *  under the terms of either the GPL or the LGPL, and not to allow others to
 *  use your version of this file under the terms of the MPL, indicate your
 *  decision by deleting the provisions above and replace them with the notice
 *  and other provisions required by the GPL or the LGPL. If you do not delete
 *  the provisions above, a recipient may use your version of this file under
 *  the terms of any one of the MPL, the GPL or the LGPL.
 *
 */

package org.dcm4chee.arc.query.util;

import org.dcm4che3.data.Tag;

import java.util.Arrays;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2017
 */
public enum QIDO {
    PATIENT(
        Tag.PatientName,
        Tag.PatientID,
        Tag.PatientBirthDate,
        Tag.PatientSex
    ),
    STUDY(
        Tag.StudyDate,
        Tag.StudyTime,
        Tag.AccessionNumber,
        Tag.ModalitiesInStudy,
        Tag.ReferringPhysicianName,
        Tag.PatientName,
        Tag.PatientID,
        Tag.PatientBirthDate,
        Tag.PatientSex,
        Tag.StudyID,
        Tag.StudyInstanceUID,
        Tag.NumberOfStudyRelatedSeries,
        Tag.NumberOfStudyRelatedInstances
    ),
    SERIES(
        Tag.Modality,
        Tag.SeriesDescription,
        Tag.SeriesNumber,
        Tag.SeriesInstanceUID,
        Tag.NumberOfSeriesRelatedInstances,
        Tag.PerformedProcedureStepStartDate,
        Tag.PerformedProcedureStepStartTime,
        Tag.RequestAttributesSequence
    ),
    INSTANCE(
        Tag.SOPClassUID,
        Tag.SOPInstanceUID,
        Tag.InstanceNumber,
        Tag.Rows,
        Tag.Columns,
        Tag.BitsAllocated,
        Tag.NumberOfFrames
    ),
    MWL(
        Tag.AccessionNumber,
        Tag.ReferringPhysicianName,
        Tag.ReferencedStudySequence,
        Tag.ReferencedPatientSequence,
        Tag.PatientName,
        Tag.PatientID,
        Tag.PatientBirthDate,
        Tag.PatientSex,
        Tag.PatientWeight,
        Tag.MedicalAlerts,
        Tag.Allergies,
        Tag.PregnancyStatus,
        Tag.StudyInstanceUID,
        Tag.RequestingPhysician,
        Tag.RequestedProcedureDescription,
        Tag.RequestedProcedureCodeSequence,
        Tag.AdmissionID,
        Tag.SpecialNeeds,
        Tag.CurrentPatientLocation,
        Tag.PatientState,
        Tag.ScheduledProcedureStepSequence,
        Tag.RequestedProcedureID,
        Tag.RequestedProcedurePriority,
        Tag.PatientTransportArrangements,
        Tag.ConfidentialityConstraintOnPatientDataDescription
    ),
    UPS(
        Tag.SOPClassUID,
        Tag.SOPInstanceUID,
        Tag.AdmittingDiagnosesDescription,
        Tag.AdmittingDiagnosesCodeSequence,
        Tag.PatientName,
        Tag.PatientID,
        Tag.IssuerOfPatientID,
        Tag.IssuerOfPatientIDQualifiersSequence,
        Tag.PatientBirthDate,
        Tag.PatientSex,
        Tag.OtherPatientIDsSequence,
        Tag.MedicalAlerts,
        Tag.Allergies,
        Tag.PregnancyStatus,
        Tag.StudyInstanceUID,
        Tag.AdmissionID,
        Tag.IssuerOfAdmissionIDSequence,
        Tag.SpecialNeeds,
        Tag.ScheduledProcedureStepStartDateTime,
        Tag.ScheduledWorkitemCodeSequence,
        Tag.InputInformationSequence,
        Tag.ScheduledStationNameCodeSequence,
        Tag.ScheduledStationClassCodeSequence,
        Tag.ScheduledStationGeographicLocationCodeSequence,
        Tag.ScheduledHumanPerformersSequence,
        Tag.InputReadinessState,
        Tag.ReferencedRequestSequence,
        Tag.ProcedureStepState,
        Tag.ProcedureStepProgressInformationSequence,
        Tag.ScheduledProcedureStepPriority,
        Tag.WorklistLabel,
        Tag.ProcedureStepLabel,
        Tag.ScheduledProcessingParametersSequence,
        Tag.UnifiedProcedureStepPerformedProcedureSequence
    ),
    STUDY_SERIES(catAndSort(STUDY.includetags, SERIES.includetags)),
    STUDY_SERIES_INSTANCE(catAndSort(STUDY.includetags, SERIES.includetags, INSTANCE.includetags)),
    SERIES_INSTANCE(catAndSort(SERIES.includetags, INSTANCE.includetags));

    public final int[] includetags;

    QIDO(int... includetags) {
        this.includetags = includetags;
    }

    private static int[] catAndSort(int[]... srcs) {
        int totlen = 0;
        for (int[] src : srcs)
            totlen += src.length;

        int[] dest = new int[totlen];
        int off = 0;
        for (int[] src : srcs) {
            System.arraycopy(src, 0, dest, off, src.length);
            off += src.length;
        }
        Arrays.sort(dest);
        return dest;
    }
}
