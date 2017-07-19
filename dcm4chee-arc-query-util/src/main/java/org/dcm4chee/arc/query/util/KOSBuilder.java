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

import org.dcm4che3.data.*;
import org.dcm4che3.util.UIDUtils;

import java.util.Date;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2017
 */
public class KOSBuilder {

    private final Attributes attrs;

    public KOSBuilder(Code conceptNameCode, int seriesNumber, int instanceNumber) {
        this.attrs = new Attributes(30);
        attrs.setString(Tag.SOPClassUID, VR.UI, UID.KeyObjectSelectionDocumentStorage);
        attrs.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
        attrs.setDate(Tag.ContentDateAndTime, new Date());
        attrs.setString(Tag.Modality, VR.CS, "KO");
        attrs.setNull(Tag.ReferencedPerformedProcedureStepSequence, VR.SQ);
        attrs.setString(Tag.SeriesInstanceUID, VR.UI, UIDUtils.createUID());
        attrs.setInt(Tag.SeriesNumber, VR.IS, seriesNumber);
        attrs.setInt(Tag.InstanceNumber, VR.IS, instanceNumber);
        attrs.setString(Tag.ValueType, VR.CS, "CONTAINER");
        attrs.setString(Tag.ContinuityOfContent, VR.CS, "SEPARATE");
        attrs.newSequence(Tag.ConceptNameCodeSequence, 1).add(conceptNameCode.toItem());
        attrs.newSequence(Tag.ContentTemplateSequence, 1).add(templateIdentifier());
        attrs.newSequence(Tag.CurrentRequestedProcedureEvidenceSequence, 1);
        attrs.newSequence(Tag.ContentSequence, 1);
    }

    public Attributes getAttributes() {
        return attrs;
    }

    public KOSBuilder addInstanceRef(Attributes inst) {
        Sequence contentSeq = attrs.getSequence(Tag.ContentSequence);
        if (contentSeq.isEmpty()) {
            attrs.addSelected(inst,
                    Tag.SpecificCharacterSet,
                    Tag.StudyDate,
                    Tag.StudyTime,
                    Tag.AccessionNumber,
                    Tag.IssuerOfAccessionNumberSequence,
                    Tag.PatientName,
                    Tag.PatientID,
                    Tag.IssuerOfPatientID,
                    Tag.PatientBirthDate,
                    Tag.PatientSex,
                    Tag.StudyInstanceUID,
                    Tag.StudyID);
        }
        String cuid = inst.getString(Tag.SOPClassUID);
        String iuid = inst.getString(Tag.SOPInstanceUID);
        refSeries(inst.getString(Tag.StudyInstanceUID), inst.getString(Tag.SeriesInstanceUID))
                .getSequence(Tag.ReferencedSOPSequence)
                .add(refSOP(cuid, iuid));
        contentSeq.add(contentItem(typeOf(cuid), refSOP(cuid, iuid)));
        return this;
    }

    private Attributes refSeries(String studyIUID, String seriesIUID) {
        Sequence refSeriess = refStudy(studyIUID).getSequence(Tag.ReferencedSeriesSequence);
        for (Attributes refSeries : refSeriess) {
            if (seriesIUID.equals(refSeries.getString(Tag.SeriesInstanceUID)))
                return refSeries;
        }
        Attributes refSeries = new Attributes(2);
        refSeries.newSequence(Tag.ReferencedSOPSequence, 100);
        refSeries.setString(Tag.SeriesInstanceUID, VR.UI, seriesIUID);
        refSeriess.add(refSeries);
        return refSeries;
    }

    private Attributes refStudy(String studyIUID) {
        Sequence refStudies = attrs.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence);
        for (Attributes refStudy : refStudies) {
            if (studyIUID.equals(refStudy.getString(Tag.StudyInstanceUID)))
                return refStudy;
        }
        Attributes refStudy = new Attributes(2);
        refStudy.newSequence(Tag.ReferencedSeriesSequence, 10);
        refStudy.setString(Tag.StudyInstanceUID, VR.UI, studyIUID);
        refStudies.add(refStudy);
        return refStudy;
    }

    private String typeOf(String cuid) {
        return "COMPOSITE";
    }

    private Attributes templateIdentifier() {
        Attributes attrs = new Attributes(2);
        attrs.setString(Tag.MappingResource, VR.CS, "DCMR");
        attrs.setString(Tag.TemplateIdentifier, VR.CS, "2010");
        return attrs;
    }

    private Attributes contentItem(String valueType, Attributes refSOP) {
        Attributes item = new Attributes(3);
        item.setString(Tag.RelationshipType, VR.CS, "CONTAINS");
        item.setString(Tag.ValueType, VR.CS, valueType);
        item.newSequence(Tag.ReferencedSOPSequence, 1).add(refSOP);
        return item;
    }

    private Attributes refSOP(String cuid, String iuid) {
        Attributes item = new Attributes(2);
        item.setString(Tag.ReferencedSOPClassUID, VR.UI, cuid);
        item.setString(Tag.ReferencedSOPInstanceUID, VR.UI, iuid);
        return item;
    }
}
