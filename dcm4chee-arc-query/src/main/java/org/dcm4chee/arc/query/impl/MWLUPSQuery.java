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

package org.dcm4chee.arc.query.impl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.*;
import org.dcm4che3.data.*;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.query.QueryContext;

import java.util.List;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Sep 2019
 */
public class MWLUPSQuery extends UPSQuery {

    MWLUPSQuery(QueryContext context, EntityManager em) {
        super(context, em);
    }

    @Override
    protected Attributes toAttributes(Tuple results) {
        Attributes attrs = super.toAttributes(results);
        Attributes req = attrs.getNestedDataset(Tag.ReferencedRequestSequence);
        if (req != null) {
            copyString(req, Tag.AccessionNumber, attrs, Tag.AccessionNumber, VR.SH);
            copyItem(req, Tag.IssuerOfAccessionNumberSequence, attrs, Tag.IssuerOfAccessionNumberSequence);
            copyString(req, Tag.ReferringPhysicianName, attrs, Tag.ReferringPhysicianName, VR.PN);
            copyString(req, Tag.RequestingService, attrs, Tag.RequestingService, VR.LO);
            copyItem(req, Tag.RequestingServiceCodeSequence, attrs, Tag.RequestingServiceCodeSequence);
            copyString(req, Tag.RequestedProcedureDescription, attrs, Tag.RequestedProcedureDescription, VR.LO);
            copyItem(req, Tag.ReasonForRequestedProcedureCodeSequence, attrs, Tag.ReasonForRequestedProcedureCodeSequence);
            copyString(req, Tag.ReasonForTheRequestedProcedure, attrs, Tag.ReasonForTheRequestedProcedure, VR.LO);
        }
        Attributes sps = new Attributes();
        Attributes stationNameCode = attrs.getNestedDataset(Tag.ScheduledStationNameCodeSequence);
        if (stationNameCode != null) {
            if (context.getQueryParam().isUPS2MWLScheduledStationNameCodeValueAsAET()) {
                sps.setString(Tag.ScheduledStationAETitle, VR.AE, stationNameCode.getString(Tag.CodeValue));
                sps.setString(Tag.ScheduledStationName, VR.SH, stationNameCode.getString(Tag.CodeMeaning));
            } else {
                sps.setString(Tag.ScheduledStationAETitle, VR.AE, stationNameCode.getString(Tag.CodeMeaning));
            }
        }
        sps.setDate(Tag.ScheduledProcedureStepStartDateAndTime,
                attrs.getDate(Tag.ScheduledProcedureStepStartDateTime));
        copyString(attrs.getNestedDataset(Tag.ScheduledProcedureStepLocation), Tag.CodeMeaning,
                sps, Tag.ScheduledProcedureStepLocation, VR.SH);
        copyString(attrs.getNestedDataset(Tag.ScheduledStationClassCodeSequence), Tag.CodeMeaning,
                sps, Tag.Modality, VR.CS);
        Attributes performer = attrs.getNestedDataset(Tag.ScheduledHumanPerformersSequence);
        if (performer != null) {
            copyItem(performer, Tag.HumanPerformerCodeSequence,
                    sps, Tag.ScheduledPerformingPhysicianIdentificationSequence);
            sps.setString(Tag.ScheduledPerformingPhysicianName, VR.PN,
                    performer.getString(Tag.HumanPerformerName));
        }
        sps.setString(Tag.CommentsOnTheScheduledProcedureStep, VR.LT,
                attrs.getString(Tag.CommentsOnTheScheduledProcedureStep));
        copyItem(attrs, Tag.ScheduledWorkitemCodeSequence, sps, Tag.ScheduledProtocolCodeSequence);
        sps.setString(Tag.ScheduledProcedureStepDescription, VR.LO,
                attrs.getString(Tag.ProcedureStepLabel));
        attrs.newSequence(Tag.ScheduledProcedureStepSequence, 1).add(sps);
        return attrs;
    }

    @Override
    protected CriteriaQuery<Tuple> order(CriteriaQuery<Tuple> q) {
        if (context.getOrderByTags() != null)
            q.orderBy(builder.orderWorkitems(patient, ups, context.getOrderByTags()));
        return q;
    }

    @Override
    protected <T> CriteriaQuery<T> restrict(CriteriaQuery<T> q, Join<UPS, Patient> patient, Root<UPS> ups) {
        List<Predicate> predicates = builder.mwlupsPredicates(q, patient, ups,
                context.getPatientIDs(),
                context.getIssuerOfPatientID(),
                context.getQueryKeys(),
                context.getQueryParam());
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));
        return q;
    }

    private static void copyString(Attributes src, int srctag, Attributes dst, int dsttag, VR vr) {
        if (src != null) dst.setString(dsttag, vr, src.getString(srctag));
    }

    private static void copyItem(Attributes src, int srctag, Attributes dst, int dsttag) {
        Attributes item = src.getNestedDataset(srctag);
        if (item != null) dst.newSequence(dsttag, 1).add(new Attributes(item));
    }

}
