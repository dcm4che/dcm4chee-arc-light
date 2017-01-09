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

package org.dcm4chee.arc.procedure.impl;

import org.dcm4che3.audit.AuditMessages;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Issuer;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.soundex.FuzzyStr;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.issuer.IssuerService;
import org.dcm4chee.arc.patient.PatientMismatchException;
import org.dcm4chee.arc.procedure.ProcedureContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import java.util.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jun 2016
 */
@Stateless
public class ProcedureServiceEJB {

    private static final Logger LOG = LoggerFactory.getLogger(ProcedureServiceEJB.class);

    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    @Inject
    private IssuerService issuerService;


    public void updateProcedure(ProcedureContext ctx) {
        Patient patient = ctx.getPatient();
        Attributes attrs = ctx.getAttributes();
        IssuerEntity issuerOfAccessionNumber = findOrCreateIssuer(
                attrs.getNestedDataset(Tag.IssuerOfAccessionNumberSequence));
        updateStudyAndSeriesAttributes(ctx, issuerOfAccessionNumber);
        if (ctx.getHttpRequest() != null)
            updateProcedureForWeb(ctx, patient, attrs, issuerOfAccessionNumber);
        else
            updateProcedureForHL7(ctx, patient, attrs, issuerOfAccessionNumber);
    }


    private void updateProcedureForHL7(ProcedureContext ctx, Patient patient, Attributes attrs,
                                       IssuerEntity issuerOfAccessionNumber) {
        Map<String, Attributes> mwlAttrsMap = createMWLAttrsMap(attrs);
        List<MWLItem> prevMWLItems = em.createNamedQuery(MWLItem.FIND_BY_STUDY_IUID_EAGER, MWLItem.class)
                .setParameter(1, ctx.getStudyInstanceUID())
                .getResultList();
        for (MWLItem mwlItem : prevMWLItems) {
            Attributes mwlAttrs = mwlAttrsMap.remove(mwlItem.getScheduledProcedureStepID());
            if (mwlAttrs == null)
                em.remove(mwlItem);
            else {
                if (mwlItem.getPatient().getPk() != patient.getPk())
                    throw new PatientMismatchException("" + patient + " does not match " +
                            mwlItem.getPatient() + " in previous " + mwlItem);
                mwlItem.setAttributes(mwlAttrs, ctx.getAttributeFilter(), ctx.getFuzzyStr());
                mwlItem.setIssuerOfAccessionNumber(issuerOfAccessionNumber);
            }
        }
        for (Attributes mwlAttrs : mwlAttrsMap.values())
            persistMWL(ctx, patient, mwlAttrs, issuerOfAccessionNumber);
        ctx.setEventActionCode(prevMWLItems.isEmpty()
                ? AuditMessages.EventActionCode.Create
                : AuditMessages.EventActionCode.Update);
    }

    private void updateProcedureForWeb(ProcedureContext ctx, Patient patient, Attributes attrs,
                                       IssuerEntity issuerOfAccessionNumber) {
        Sequence spsSeq = attrs.getSequence(Tag.ScheduledProcedureStepSequence);
        String spsID = null;
        for (Attributes item : spsSeq)
            spsID = item.getString(Tag.ScheduledProcedureStepID);
        try {
            MWLItem mwlItem = em.createNamedQuery(MWLItem.FIND_BY_STUDY_UID_AND_SPS_ID_EAGER, MWLItem.class)
                    .setParameter(1, ctx.getStudyInstanceUID())
                    .setParameter(2, spsID).getSingleResult();
            mwlItem.setAttributes(attrs, ctx.getAttributeFilter(), ctx.getFuzzyStr());
            mwlItem.setIssuerOfAccessionNumber(issuerOfAccessionNumber);
            ctx.setEventActionCode(AuditMessages.EventActionCode.Update);
        } catch (NoResultException e) {
            persistMWL(ctx, patient, attrs, issuerOfAccessionNumber);
            ctx.setEventActionCode(AuditMessages.EventActionCode.Create);
        }
    }

    private void persistMWL(ProcedureContext ctx, Patient patient, Attributes attrs,
                            IssuerEntity issuerOfAccessionNumber) {
        MWLItem mwlItem = new MWLItem();
        mwlItem.setPatient(patient);
        mwlItem.setAttributes(attrs, ctx.getAttributeFilter(), ctx.getFuzzyStr());
        mwlItem.setIssuerOfAccessionNumber(issuerOfAccessionNumber);
        em.persist(mwlItem);
        LOG.info("{}: Create {}", ctx, mwlItem);
    }

    private IssuerEntity findOrCreateIssuer(Attributes item) {
        return item != null && !item.isEmpty() ? issuerService.mergeOrCreate(new Issuer(item)) : null;
    }

    private Map<String, Attributes> createMWLAttrsMap(Attributes attrs) {
        Attributes root = new Attributes(attrs);
        Iterator<Attributes> spsItems = root.getSequence(Tag.ScheduledProcedureStepSequence).iterator();
        root.setNull(Tag.ScheduledProcedureStepSequence, VR.SQ);
        Map<String, Attributes> map = new HashMap<>(4);
        while (spsItems.hasNext()) {
            Attributes sps = spsItems.next();
            spsItems.remove();
            Attributes mwlAttrs = new Attributes(root);
            mwlAttrs.newSequence(Tag.ScheduledProcedureStepSequence, 1).add(sps);
            map.put(sps.getString(Tag.ScheduledProcedureStepID), mwlAttrs);
        }
        return map;
    }

    public void deleteProcedure(ProcedureContext ctx) {
        List<MWLItem> mwlItems = em.createNamedQuery(MWLItem.FIND_BY_STUDY_IUID_EAGER, MWLItem.class)
                .setParameter(1, ctx.getStudyInstanceUID()).getResultList();
        if (mwlItems.isEmpty())
            return;
        int mwlSize = mwlItems.size();
        for (MWLItem mwl : mwlItems)
            if (mwl.getScheduledProcedureStepID().equals(ctx.getSpsID())) {
                if(mwlSize > 1)
                    ctx.setEventActionCode(AuditMessages.EventActionCode.Update);
                else
                    ctx.setEventActionCode(AuditMessages.EventActionCode.Delete);
                ctx.setAttributes(mwl.getAttributes());
                ctx.setPatient(mwl.getPatient());
                em.remove(mwl);
            }
    }

    public void updateSPSStatus(ProcedureContext ctx) {
        List<MWLItem> mwlItems = em.createNamedQuery(MWLItem.FIND_BY_STUDY_IUID_EAGER, MWLItem.class)
                .setParameter(1, ctx.getStudyInstanceUID()).getResultList();
        for (MWLItem mwl : mwlItems) {
            Attributes spsItemMWL = mwl.getAttributes()
                    .getNestedDataset(Tag.ScheduledProcedureStepSequence);
            Attributes spsItemMPPS = ctx.getAttributes()
                    .getNestedDataset(Tag.ScheduledProcedureStepSequence);
            if (!spsItemMWL.getString(Tag.ScheduledProcedureStepStatus)
                    .equals(spsItemMPPS.getString(Tag.ScheduledProcedureStepStatus))) {
                mwl.setAttributes(ctx.getAttributes(), ctx.getAttributeFilter(), ctx.getFuzzyStr());
                ctx.setEventActionCode(AuditMessages.EventActionCode.Update);
            }
        }
    }

    private void updateStudyAndSeriesAttributes(ProcedureContext ctx, IssuerEntity issuerOfAccessionNumber) {
        Attributes mwlAttr = ctx.getAttributes();
        try {
            List<Series> seriesList = em.createNamedQuery(Series.FIND_SERIES_OF_STUDY_BY_STUDY_IUID_EAGER, Series.class)
                    .setParameter(1, ctx.getStudyInstanceUID()).getResultList();
            if (!seriesList.isEmpty()) {
                Study study = seriesList.get(0).getStudy();
                Attributes studyAttr = study.getAttributes();
                Attributes attr = new Attributes();
                if (studyAttr.updateSelected(Attributes.UpdatePolicy.MERGE, mwlAttr, attr, ctx.getAttributeFilter().getSelection())) {
                    if (study.getIssuerOfAccessionNumber() != null && !study.getIssuerOfAccessionNumber().equals(issuerOfAccessionNumber))
                        study.setIssuerOfAccessionNumber(issuerOfAccessionNumber);
                    study.setAttributes(studyAttr, ctx.getAttributeFilter(), ctx.getFuzzyStr());
                    for (Series series : seriesList) {
                        Attributes seriesAttr = series.getAttributes();
                        Sequence rqAttrsSeq = seriesAttr.newSequence(Tag.RequestAttributesSequence, 1);
                        Sequence spsSeq = mwlAttr.getSequence(Tag.ScheduledProcedureStepSequence);
                        for (Attributes item : spsSeq) {
                            Attributes reqAttr = createRequestAttrs(mwlAttr, item);
                            rqAttrsSeq.add(reqAttr);
                        }
                        setRequestAttributes(series, seriesAttr, ctx.getFuzzyStr(), issuerOfAccessionNumber);
                    }
                }
            }
        } catch (NoResultException e) {}
    }

    private Attributes createRequestAttrs(Attributes mwlAttr, Attributes item) {
        Attributes attr = new Attributes();
        attr.addSelected(mwlAttr, SERIES_MWL_ATTR);
        attr.setString(Tag.ScheduledProcedureStepID, VR.SH, item.getString(Tag.ScheduledProcedureStepID));
        return attr;
    }

    private final int[] SERIES_MWL_ATTR = {
            Tag.AccessionNumber,
            Tag.RequestedProcedureID,
            Tag.StudyInstanceUID,
            Tag.RequestedProcedureDescription
    };

    private void setRequestAttributes(Series series, Attributes attrs, FuzzyStr fuzzyStr, IssuerEntity issuerOfAccNum) {
        Sequence seq = attrs.getSequence(Tag.RequestAttributesSequence);
        Collection<SeriesRequestAttributes> requestAttributes = series.getRequestAttributes();
        requestAttributes.clear();
        if (seq != null)
            for (Attributes item : seq) {
                SeriesRequestAttributes request = new SeriesRequestAttributes(item, issuerOfAccNum, fuzzyStr);
                requestAttributes.add(request);
            }
    }
}
