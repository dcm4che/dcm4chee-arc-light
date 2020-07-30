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
 * Portions created by the Initial Developer are Copyright (C) 2015-2020
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

package org.dcm4chee.arc.ups.impl;

import org.dcm4che3.data.*;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.Duration;
import org.dcm4chee.arc.conf.UPSTemplate;
import org.dcm4chee.arc.ups.UPSContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.Date;
import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Sep 2019
 */

public class UPSUtils {
    private static final Logger LOG = LoggerFactory.getLogger(UPSUtils.class);
    private static final String unknownSeriesIUID = "1.2.40.0.13.1.15.110.3.165.2";
    private static final String unknownSOPCUID = "1.2.40.0.13.1.15.110.3.165.3";
    private static final String unknownSOPIUID = "1.2.40.0.13.1.15.110.3.165.4";

    static Attributes upsAttrsByTemplate(UPSContext ctx, UPSTemplate upsTemplate, Map.Entry<String,
            IDWithIssuer> studyPatient, Date upsScheduledTime, Calendar now, String upsLabel) {
        ArchiveAEExtension arcAE = ctx.getArchiveAEExtension();
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
        if (upsTemplate.isIncludeStudyInstanceUID())
            attrs.setString(Tag.StudyInstanceUID, VR.UI, studyPatient.getKey());
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
        updateIncludeInputInformation(
                attrs.newSequence(Tag.InputInformationSequence, 1),
                studyPatient.getKey(),
                arcAE.getApplicationEntity().getAETitle());
        studyPatient.getValue().exportPatientIDWithIssuer(attrs);
        return attrs;
    }

    private static Attributes outputStorage(String destinationAE) {
        Attributes dicomStorage = new Attributes(1);
        dicomStorage.setString(Tag.DestinationAE, VR.AE, destinationAE);
        Attributes outputDestination = new Attributes(1);
        outputDestination.newSequence(Tag.DICOMStorageSequence, 1).add(dicomStorage);
        return outputDestination;
    }

    private static void updateIncludeInputInformation(Sequence sq, String studyUID, String retrieveAET) {
        refSOPSequence(sq, studyUID, retrieveAET).add(sopRef());
    }

    private static Attributes sopRef() {
        Attributes item = new Attributes(2);
        item.setString(Tag.ReferencedSOPClassUID, VR.UI, unknownSOPCUID);
        item.setString(Tag.ReferencedSOPInstanceUID, VR.UI, unknownSOPIUID);
        return item;
    }

    private static Sequence refSOPSequence(Sequence sq, String studyUID, String retrieveAET) {
        Attributes item = new Attributes(5);
        sq.add(item);
        Sequence refSOPSequence = item.newSequence(Tag.ReferencedSOPSequence, 10);
        item.setString(Tag.StudyInstanceUID, VR.UI, studyUID);
        item.setString(Tag.SeriesInstanceUID, VR.UI, unknownSeriesIUID);
        item.setString(Tag.TypeOfInstances, VR.CS, "DICOM");
        item.newSequence(Tag.DICOMRetrievalSequence, 1).add(retrieveAETItem(retrieveAET));
        return refSOPSequence;
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
