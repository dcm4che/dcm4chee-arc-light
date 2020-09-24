/*
 * **** BEGIN LICENSE BLOCK *****
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
 * **** END LICENSE BLOCK *****
 *
 */

package org.dcm4chee.arc.ups;

import org.dcm4che3.data.*;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.Duration;
import org.dcm4chee.arc.conf.UPSTemplate;

import java.util.*;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Aug 2020
 */
public class UPSUtils {

    private UPSUtils() {}

    public static Optional<Attributes> getScheduledProcessingParameter(Attributes ups, Code conceptName) {
        Sequence seq = ups.getSequence(Tag.ScheduledProcessingParametersSequence);
        if (seq == null)
            return Optional.empty();

        return seq.stream()
                .filter(item -> conceptName.equalsIgnoreMeaning(
                        new Code(item.getNestedDataset(Tag.ConceptNameCodeSequence))))
                .findFirst();
    }

    public static Optional<Code> getScheduledProcessingCodeParameter(Attributes ups, Code conceptName) {
        return getScheduledProcessingParameter(ups, conceptName)
                .map(item -> new Code(item.getNestedDataset(Tag.ConceptCodeSequence)));
    }

    public static Attributes upsAttrsByTemplate(ArchiveAEExtension arcAE,
                                                UPSTemplate upsTemplate,
                                                Date upsScheduledTime,
                                                Calendar now,
                                                String upsLabel) {
        Attributes attrs = new Attributes();
        attrs.setDate(Tag.ScheduledProcedureStepStartDateTime,
                VR.DT,
                upsScheduledTime != null ? upsScheduledTime : add(now, upsTemplate.getStartDateTimeDelay()));
        attrs.setString(Tag.ProcedureStepLabel,
                VR.LO,
                upsLabel != null ? upsLabel : upsTemplate.getProcedureStepLabel());
        attrs.setString(Tag.WorklistLabel,
                VR.LO,
                upsTemplate.getWorklistLabel() != null ? upsTemplate.getWorklistLabel() : arcAE.upsWorklistLabel());
        attrs.setString(Tag.InputReadinessState, VR.CS, upsTemplate.getInputReadinessState().name());
        attrs.setString(Tag.ProcedureStepState, VR.CS, "SCHEDULED");
        attrs.setString(Tag.ScheduledProcedureStepPriority, VR.CS, upsTemplate.getUPSPriority().name());
        attrs.setNull(Tag.ReferencedRequestSequence, VR.SQ);
        if (upsTemplate.getCompletionDateTimeDelay() != null)
            attrs.setDate(Tag.ExpectedCompletionDateTime, VR.DT, add(now, upsTemplate.getCompletionDateTimeDelay()));
        if (upsTemplate.getScheduledHumanPerformer() != null)
            attrs.newSequence(Tag.ScheduledHumanPerformersSequence, 1)
                    .add(upsTemplate.getScheduledHumanPerformerItem());
        if (upsTemplate.getScheduledWorkitemCode() != null)
            setCode(attrs, Tag.ScheduledWorkitemCodeSequence, upsTemplate.getScheduledWorkitemCode());
        if (upsTemplate.getScheduledStationName() != null)
            setCode(attrs, Tag.ScheduledStationNameCodeSequence, upsTemplate.getScheduledStationName());
        if (upsTemplate.getScheduledStationClass() != null)
            setCode(attrs, Tag.ScheduledStationClassCodeSequence, upsTemplate.getScheduledStationClass());
        if (upsTemplate.getScheduledStationLocation() != null)
            setCode(attrs, Tag.ScheduledStationGeographicLocationCodeSequence, upsTemplate.getScheduledStationLocation());
        if (upsTemplate.getDestinationAE() != null)
            attrs.newSequence(Tag.OutputDestinationSequence, 1)
                    .add(outputStorage(upsTemplate.getDestinationAE()));
        attrs.newSequence(Tag.InputInformationSequence, 1);
        return attrs;
    }

    private static Attributes outputStorage(String destinationAE) {
        Attributes dicomStorage = new Attributes(1);
        dicomStorage.setString(Tag.DestinationAE, VR.AE, destinationAE);
        Attributes outputDestination = new Attributes(1);
        outputDestination.newSequence(Tag.DICOMStorageSequence, 1).add(dicomStorage);
        return outputDestination;
    }

    public static void updateUPSAttributes(
            UPSTemplate upsTemplate, Attributes ups, Attributes match, String studyUID, String seriesUID,
            String retrieveAET) {
        addSOPRef(
                refSOPSequence(upsTemplate, ups, match, studyUID, retrieveAET),
                match,
                studyUID != null && seriesUID != null);
        setPatientAttrs(ups, match);
    }

    private static Sequence refSOPSequence(UPSTemplate upsTemplate, Attributes ups, Attributes match, String studyUID,
                                           String retrieveAET) {
        for (Attributes item : ups.getSequence(Tag.InputInformationSequence))
            if (match.getString(Tag.StudyInstanceUID).equals(item.getString(Tag.StudyInstanceUID))
                    && match.getString(Tag.SeriesInstanceUID).equals(item.getString(Tag.SeriesInstanceUID)))
                return item.getSequence(Tag.ReferencedSOPSequence);

        if (upsTemplate.isIncludeStudyInstanceUID())
            ups.setString(Tag.StudyInstanceUID, VR.UI, match.getString(Tag.StudyInstanceUID));
        Attributes item = new Attributes(5);
        ups.getSequence(Tag.InputInformationSequence).add(item);
        Sequence refSOPSequence = item.newSequence(Tag.ReferencedSOPSequence, 10);
        item.setString(Tag.StudyInstanceUID, VR.UI, match.getString(Tag.StudyInstanceUID));
        item.setString(Tag.SeriesInstanceUID, VR.UI,
                studyUID == null ? null : match.getString(Tag.SeriesInstanceUID));
        item.setString(Tag.TypeOfInstances, VR.CS, "DICOM");
        item.newSequence(Tag.DICOMRetrievalSequence, 1).add(retrieveAETItem(retrieveAET));
        return refSOPSequence;
    }

    private static void setPatientAttrs(Attributes ups, Attributes match) {
        setPatientID(ups, match);
        ups.setString(Tag.PatientName, VR.PN, match.getString(Tag.PatientName));
        ups.setString(Tag.PatientBirthDate, VR.DA, match.getString(Tag.PatientBirthDate));
        ups.setString(Tag.PatientSex, VR.CS, match.getString(Tag.PatientSex));
    }

    private static void setPatientID(Attributes ups, Attributes match) {
        IDWithIssuer pidWithIssuer = IDWithIssuer.pidOf(match);
        if (pidWithIssuer == null || ups.getString(Tag.PatientID) != null)
            return;

        pidWithIssuer.exportPatientIDWithIssuer(ups);
    }

    private static void addSOPRef(Sequence refSOPSeq, Attributes match, boolean upsMatchingInstances) {
        if (!upsMatchingInstances)
            return;

        Attributes item = new Attributes(2);
        refSOPSeq.add(item);
        item.setString(Tag.ReferencedSOPClassUID, VR.UI, match.getString(Tag.SOPClassUID));
        item.setString(Tag.ReferencedSOPInstanceUID, VR.UI, match.getString(Tag.SOPInstanceUID));
    }

    private static Attributes retrieveAETItem(String... retrieveAET) {
        Attributes item = new Attributes(1);
        item.setString(Tag.RetrieveAETitle, VR.AE, retrieveAET);
        return item;
    }

    private static void setCode(Attributes attrs, int sqtag, Code code) {
        if (code != null) {
            attrs.newSequence(sqtag, 1).add(code.toItem());
        } else {
            attrs.setNull(sqtag, VR.SQ);
        }
    }

    private static Date add(Calendar now, Duration delay) {
        return delay != null ? new Date(now.getTimeInMillis() + delay.getSeconds() * 1000) : now.getTime();
    }
}
