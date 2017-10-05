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
import org.dcm4che3.net.Device;
import org.dcm4che3.soundex.FuzzyStr;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.AttributeFilter;
import org.dcm4chee.arc.conf.Entity;
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
    private Device device;

    @Inject
    private IssuerService issuerService;


    public void updateProcedure(ProcedureContext ctx) {
        Patient patient = ctx.getPatient();
        Attributes attrs = ctx.getAttributes();
        IssuerEntity issuerOfAccessionNumber = findOrCreateIssuer(attrs);
        if (ctx.getHttpRequest() != null)
            updateProcedureForWeb(ctx, patient, issuerOfAccessionNumber);
        else
            updateProcedureForHL7(ctx, patient, issuerOfAccessionNumber);
        updateStudySeriesAttributesFromMWL(ctx, issuerOfAccessionNumber);
    }


    private void updateProcedureForHL7(ProcedureContext ctx, Patient patient,
                                       IssuerEntity issuerOfAccessionNumber) {
        Map<String, Attributes> mwlAttrsMap = createMWLAttrsMap(ctx.getAttributes());
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

                Attributes attrs = mwlItem.getAttributes();
                if (!attrs.update(ctx.getAttributeUpdatePolicy(), mwlAttrs, null))
                    return;
                updateMWL(ctx, issuerOfAccessionNumber, mwlItem, attrs);
            }
        }
        for (Attributes mwlAttrs : mwlAttrsMap.values())
            createMWL(ctx, patient, mwlAttrs, issuerOfAccessionNumber);
    }

    private void updateMWL(ProcedureContext ctx, IssuerEntity issuerOfAccessionNumber, MWLItem mwlItem, Attributes mwlAttrs) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        mwlItem.setAttributes(mwlAttrs, arcDev.getAttributeFilter(Entity.MWL), arcDev.getFuzzyStr());
        mwlItem.setIssuerOfAccessionNumber(issuerOfAccessionNumber);
        ctx.setEventActionCode(AuditMessages.EventActionCode.Update);
    }

    private void updateProcedureForWeb(ProcedureContext ctx, Patient patient,
                                       IssuerEntity issuerOfAccessionNumber) {
        Attributes attrs = ctx.getAttributes();
        ctx.setSpsID(attrs.getSequence(Tag.ScheduledProcedureStepSequence).get(0).getString(Tag.ScheduledProcedureStepID));

        MWLItem mwlItem = findMWLItem(ctx);
        if (mwlItem == null)
            createMWL(ctx, patient, attrs, issuerOfAccessionNumber);
        else
            updateMWL(ctx, issuerOfAccessionNumber, mwlItem, attrs);
    }

    public MWLItem findMWLItem(ProcedureContext ctx) {
        try {
            return em.createNamedQuery(MWLItem.FIND_BY_STUDY_UID_AND_SPS_ID_EAGER, MWLItem.class)
                    .setParameter(1, ctx.getStudyInstanceUID())
                    .setParameter(2, ctx.getSpsID()).getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    private void createMWL(ProcedureContext ctx, Patient patient, Attributes attrs,
                            IssuerEntity issuerOfAccessionNumber) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        MWLItem mwlItem = new MWLItem();
        mwlItem.setPatient(patient);
        mwlItem.setAttributes(attrs, arcDev.getAttributeFilter(Entity.MWL), arcDev.getFuzzyStr());
        mwlItem.setIssuerOfAccessionNumber(issuerOfAccessionNumber);
        em.persist(mwlItem);
        ctx.setEventActionCode(AuditMessages.EventActionCode.Create);
        LOG.info("{}: Create {}", ctx, mwlItem);
    }

    private IssuerEntity findOrCreateIssuer(Attributes attrs) {
        Attributes item = attrs.getNestedDataset(Tag.IssuerOfAccessionNumberSequence);
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

    public void updateSPSStatus(ProcedureContext ctx, String status) {
        List<MWLItem> mwlItems = em.createNamedQuery(MWLItem.FIND_BY_STUDY_IUID_EAGER, MWLItem.class)
                .setParameter(1, ctx.getStudyInstanceUID()).getResultList();
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        for (MWLItem mwl : mwlItems) {
            Attributes mwlAttrs = mwl.getAttributes();
            Attributes spsItemMWL = mwlAttrs
                    .getNestedDataset(Tag.ScheduledProcedureStepSequence);
            if (!spsItemMWL.getString(Tag.ScheduledProcedureStepStatus).equals(status)) {
                spsItemMWL.setString(Tag.ScheduledProcedureStepStatus, VR.CS, status);
                mwl.setAttributes(mwlAttrs, arcDev.getAttributeFilter(Entity.MWL), arcDev.getFuzzyStr());
                ctx.setEventActionCode(AuditMessages.EventActionCode.Update);
            }
        }
    }

    private boolean updateStudySeriesAttributesFromMWL(ProcedureContext ctx, IssuerEntity issuerOfAccessionNumber) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        Attributes mwlAttr = ctx.getAttributes();
        List<Series> seriesList = em.createNamedQuery(Series.FIND_SERIES_OF_STUDY_BY_STUDY_IUID_EAGER, Series.class)
                .setParameter(1, ctx.getStudyInstanceUID()).getResultList();
        if (seriesList.isEmpty())
            return false;

        Study study = seriesList.get(0).getStudy();
        Attributes studyAttr = study.getAttributes();
        Attributes attr = new Attributes();
        if (!studyAttr.updateSelected(Attributes.UpdatePolicy.MERGE,
                mwlAttr, attr, arcDev.getAttributeFilter(Entity.Study).getSelection()))
            return false;

        study.setIssuerOfAccessionNumber(issuerOfAccessionNumber);
        study.setAttributes(studyAttr, arcDev.getAttributeFilter(Entity.Study), arcDev.getFuzzyStr());
        Set<String> sourceSeriesIUIDs = ctx.getSourceSeriesInstanceUIDs();
        for (Series series : seriesList)
            if (sourceSeriesIUIDs == null || sourceSeriesIUIDs.contains(series.getSeriesInstanceUID()))
                updateSeriesAttributes(series, mwlAttr, issuerOfAccessionNumber,
                        arcDev.getAttributeFilter(Entity.Series), arcDev.getFuzzyStr());

        LOG.info("Study and series attributes updated successfully : " + ctx.getStudyInstanceUID());
        return true;
    }

    private void updateSeriesAttributes(Series series, Attributes mwlAttr, IssuerEntity issuerOfAccessionNumber,
                                        AttributeFilter filter, FuzzyStr fuzzyStr) {
        Attributes seriesAttr = series.getAttributes();
        Sequence rqAttrsSeq = seriesAttr.newSequence(Tag.RequestAttributesSequence, 1);
        Sequence spsSeq = mwlAttr.getSequence(Tag.ScheduledProcedureStepSequence);
        Collection<SeriesRequestAttributes> requestAttributes = series.getRequestAttributes();
        requestAttributes.clear();
        for (Attributes spsItem : spsSeq) {
            Attributes rqAttrsItem = MWLItem.toRequestAttributesSequenceItem(mwlAttr, spsItem);
            rqAttrsSeq.add(rqAttrsItem);
            SeriesRequestAttributes request = new SeriesRequestAttributes(rqAttrsItem, issuerOfAccessionNumber, fuzzyStr);
            requestAttributes.add(request);
        }
        series.setAttributes(seriesAttr, filter, fuzzyStr);
    }

    public void updateStudySeriesAttributes(ProcedureContext ctx) {
        boolean result = false;
        try {
            IssuerEntity issuerOfAccessionNumber = findOrCreateIssuer(ctx.getAttributes());
            result = updateStudySeriesAttributesFromMWL(ctx, issuerOfAccessionNumber);
        } catch (Exception e) {
            ctx.setException(e);
        } finally {
            if (result || ctx.getException() != null)
                ctx.setEventActionCode(AuditMessages.EventActionCode.Update);
        }
    }
}
