/*
 * ** BEGIN LICENSE BLOCK *****
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
 * Portions created by the Initial Developer are Copyright (C) 2016
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
 * ** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4chee.arc.conf.AttributesBuilder;
import org.dcm4chee.arc.conf.MergeMWLMatchingKey;
import org.dcm4chee.arc.conf.SPSStatus;

import java.util.Arrays;
import java.util.Objects;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Nov 2016
 */
public class MergeMWLQueryParam {
    public final String mwlSCP;
    public final String[] localMwlWorklistLabels;
    public final SPSStatus[] localMwlStatus;
    public final String patientID;
    public final String accessionNumber;
    public final String studyIUID;
    public final String spsID;
    private final String tplURI;

    public MergeMWLQueryParam(String mwlSCP, String[] localMwlWorklistLabels, SPSStatus[] localMwlStatus, String patientID,
                              String accessionNumber, String studyIUID, String spsID, String tplURI) {
        this.mwlSCP = mwlSCP;
        this.localMwlWorklistLabels = localMwlWorklistLabels;
        this.localMwlStatus = localMwlStatus;
        this.patientID = patientID;
        this.accessionNumber = accessionNumber;
        this.studyIUID = studyIUID;
        this.spsID = spsID;
        this.tplURI = tplURI;
    }

    public static MergeMWLQueryParam valueOf(String mwlSCP, String[] localMwlWorklistLabels, SPSStatus[] localMwlStatus,
                                             MergeMWLMatchingKey matchingKey, Attributes attrs, String tplURI) {
        String patientID = null;
        String accessionNumber = null;
        String studyIUID = null;
        String spsID = null;
        switch (matchingKey == MergeMWLMatchingKey.PatientID
                ? attrs.containsValue(Tag.AccessionNumber)
                ? MergeMWLMatchingKey.PatientIDAccessionNumber
                : MergeMWLMatchingKey.PatientIDOnly
                : matchingKey) {
            case PatientIDOnly:
                patientID = attrs.getString(Tag.PatientID);
                break;
            case PatientIDAccessionNumber:
                patientID = attrs.getString(Tag.PatientID);
            case AccessionNumber:
                accessionNumber = attrs.getString(Tag.AccessionNumber);
                if (accessionNumber == null)
                    studyIUID = attrs.getString(Tag.StudyInstanceUID);
                break;
            case ScheduledProcedureStepID:
                Attributes item = attrs.getNestedDataset(Tag.RequestAttributesSequence);
                spsID = item != null ? item.getString(Tag.ScheduledProcedureStepID) : null;
            case StudyInstanceUID:
                studyIUID = attrs.getString(Tag.StudyInstanceUID);
                break;
        }
        return new MergeMWLQueryParam(
                mwlSCP, localMwlWorklistLabels, localMwlStatus, patientID, accessionNumber, studyIUID, spsID, tplURI);
    }

    public Attributes setMatchingKeys(Attributes keys) {
        if (patientID != null) keys.setString(Tag.PatientID, VR.LO, patientID);
        if (accessionNumber != null) keys.setString(Tag.AccessionNumber, VR.SH, accessionNumber);
        if (studyIUID != null) keys.setString(Tag.StudyInstanceUID, VR.UI, studyIUID);
        if (spsID != null) {
            Attributes sps = keys.getNestedDataset(Tag.ScheduledProcedureStepSequence);
            AttributesBuilder.setNullIfAbsent(sps,
                    Tag.ScheduledPerformingPhysicianName,
                    Tag.ScheduledProcedureStepDescription,
                    Tag.ScheduledProtocolCodeSequence);
            sps.setString(Tag.ScheduledProcedureStepID, VR.SH, spsID);
        }
        return keys;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MergeMWLQueryParam that = (MergeMWLQueryParam) o;
        return Objects.equals(mwlSCP, that.mwlSCP) &&
                Arrays.equals(localMwlWorklistLabels, that.localMwlWorklistLabels) &&
                Objects.equals(patientID, that.patientID) &&
                Objects.equals(accessionNumber, that.accessionNumber) &&
                Objects.equals(studyIUID, that.studyIUID) &&
                Objects.equals(spsID, that.spsID) &&
                Objects.equals(tplURI, that.tplURI);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(mwlSCP, patientID, accessionNumber, studyIUID, spsID, tplURI);
        result = 31 * result + Arrays.hashCode(localMwlWorklistLabels);
        result = 31 * result + Arrays.hashCode(localMwlStatus);
        return result;
    }

    @Override
    public String toString() {
        return "MergeMWLQueryParam{" +
                "mwlSCP='" + mwlSCP +
                "', localMwlWorklistLabels='" + Arrays.toString(localMwlWorklistLabels) +
                "', localMwlStatus='" + Arrays.toString(localMwlStatus) +
                "', patientID='" + patientID +
                "', accessionNumber='" + accessionNumber +
                "', studyIUID='" + studyIUID +
                "', spsID='" + spsID +
                "'}";
    }
}
