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
 * Portions created by the Initial Developer are Copyright (C) 2013-2021
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

package org.dcm4chee.arc.coerce.impl;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.dict.archive.PrivateTag;
import org.dcm4chee.arc.coerce.CoercionProcessor;
import org.dcm4chee.arc.conf.ArchiveAttributeCoercion2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Named;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Oct 2022
 */
@ApplicationScoped
@Named("use-calling-aet-as")
public class UseCallingAETAsCoercionProcessor implements CoercionProcessor {
    static final Logger LOG = LoggerFactory.getLogger(UseCallingAETAsCoercionProcessor.class);

    @Override
    public boolean coerce(ArchiveAttributeCoercion2 coercion, String sopClassUID, String sendingHost,
                          String sendingAET, String receivingHost, String receivingAET, Attributes attrs,
                          Attributes coercedAttributes) throws Exception {
        String type = coercion.getSchemeSpecificPart();
        switch (type) {
            case "ScheduledStationAETitle":
                return addScheduledStationAETitle(coercion, sendingAET, attrs);
            case "SendingApplicationEntityTitleOfSeries":
                return addSendingApplicationEntityTitleOfSeries(coercion, sendingAET, attrs);
        }
        LOG.warn("Ignore unsupported {}", coercion);
        return false;
    }

    private boolean addSendingApplicationEntityTitleOfSeries(ArchiveAttributeCoercion2 coercion, String sendingAET,
                                                             Attributes attrs) {
        if (attrs.containsValue(PrivateTag.PrivateCreator, PrivateTag.SendingApplicationEntityTitleOfSeries)) {
            return false;
        }
        attrs.setString(PrivateTag.PrivateCreator, PrivateTag.SendingApplicationEntityTitleOfSeries,
                VR.AE, sendingAET);
        LOG.info("Filter by Calling AET as Sending Application Entity Title Of Series by {}", coercion);
        return true;
    }

    private boolean addScheduledStationAETitle(ArchiveAttributeCoercion2 coercion, String sendingAET,
                                               Attributes attrs) {
        Sequence sq = attrs.ensureSequence(Tag.ScheduledProcedureStepSequence, 1);
        if (sq.isEmpty()) {
            sq.add(new Attributes(20));
        }
        Attributes sps = sq.get(0);
        if (sps.containsValue(Tag.ScheduledStationAETitle)) {
            return false;
        }
        if (sps.isEmpty()) {
            sps.setNull(Tag.Modality, VR.CS);
            sps.setNull(Tag.AnatomicalOrientationType, VR.CS);
            sps.setNull(Tag.ReferencedDefinedProtocolSequence, VR.SQ);
            sps.setNull(Tag.ReferencedPerformedProtocolSequence, VR.SQ);
            sps.setNull(Tag.RequestedContrastAgent, VR.LO);
            sps.setNull(Tag.ScheduledProcedureStepStartDate, VR.DA);
            sps.setNull(Tag.ScheduledProcedureStepStartTime, VR.TM);
            sps.setNull(Tag.ScheduledProcedureStepEndDate, VR.DA);
            sps.setNull(Tag.ScheduledProcedureStepEndTime, VR.TM);
            sps.setNull(Tag.ScheduledPerformingPhysicianName, VR.PN);
            sps.setNull(Tag.ScheduledProcedureStepDescription, VR.LO);
            sps.setNull(Tag.ScheduledProtocolCodeSequence, VR.SQ);
            sps.setNull(Tag.ScheduledProcedureStepID, VR.SH);
            sps.setNull(Tag.ScheduledPerformingPhysicianIdentificationSequence, VR.SQ);
            sps.setNull(Tag.ScheduledStationName, VR.SH);
            sps.setNull(Tag.ScheduledProcedureStepLocation, VR.SH);
            sps.setNull(Tag.PreMedication, VR.LO);
            sps.setNull(Tag.ScheduledProcedureStepStatus, VR.CS);
            sps.setNull(Tag.CommentsOnTheScheduledProcedureStep, VR.LT);
        }
        sps.setString(Tag.ScheduledStationAETitle, VR.AE, sendingAET);
        LOG.info("Filter MWL by Calling AET by {}", coercion);
        return true;
    }
}
