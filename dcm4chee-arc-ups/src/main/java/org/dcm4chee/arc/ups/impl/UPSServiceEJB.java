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

package org.dcm4chee.arc.ups.impl;

import org.dcm4che3.data.*;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.soundex.FuzzyStr;
import org.dcm4che3.util.TagUtils;
import org.dcm4chee.arc.code.CodeCache;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.Entity;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.issuer.IssuerService;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.patient.PatientService;
import org.dcm4chee.arc.ups.UPSContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import java.util.Collection;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Sep 2019
 */
@Stateless
public class UPSServiceEJB {

    private static Logger LOG = LoggerFactory.getLogger(UPSServiceEJB.class);

    @PersistenceContext(unitName = "dcm4chee-arc")
    EntityManager em;

    @Inject
    private PatientService patientService;

    @Inject
    private CodeCache codeCache;

    @Inject
    private IssuerService issuerService;

    public Workitem createWorkitem(UPSContext ctx) throws DicomServiceException {
        checkDuplicate(ctx);
        ArchiveAEExtension arcAE = ctx.getArchiveAEExtension();
        ArchiveDeviceExtension arcDev = arcAE.getArchiveDeviceExtension();
        Attributes attrs = ctx.getAttributes();
        if (!attrs.containsValue(Tag.WorklistLabel)) {
            attrs.setString(Tag.WorklistLabel, VR.LO, arcAE.defaultWorklistLabel());
        }
        Workitem workitem = new Workitem();
        workitem.setSopInstanceUID(ctx.getSopInstanceUID());
        PatientMgtContext patMgtCtx = patientService.createPatientMgtContextWEB(ctx.getHttpRequestInfo());
        patMgtCtx.setAttributes(attrs);
        Patient pat = patientService.findPatient(patMgtCtx);
        if (pat == null) {
            pat = patientService.createPatient(patMgtCtx);
        }
        workitem.setPatient(pat);
        workitem.setIssuerOfAdmissionID(
                findOrCreateIssuer(attrs, Tag.IssuerOfAdmissionIDSequence));
        workitem.setScheduledWorkitemCode(
                findOrCreateCode(attrs, Tag.ScheduledWorkitemCodeSequence));
        workitem.setScheduledStationNameCode(
                findOrCreateCode(attrs, Tag.ScheduledStationNameCodeSequence));
        workitem.setScheduledStationClassCode(
                findOrCreateCode(attrs, Tag.ScheduledStationClassCodeSequence));
        workitem.setScheduledStationGeographicLocationCode(
                findOrCreateCode(attrs, Tag.ScheduledStationGeographicLocationCodeSequence));
        setHumanPerformerCodes(workitem.getHumanPerformerCodes(),
                attrs.getSequence(Tag.ScheduledHumanPerformersSequence));
        setReferencedRequests(workitem.getReferencedRequests(),
                attrs.getSequence(Tag.ReferencedRequestSequence),
                arcDev.getFuzzyStr());
        workitem.setAttributes(attrs, arcDev.getAttributeFilter(Entity.UPS));
        em.persist(workitem);
        LOG.info("{}: Create {}", ctx, workitem);
        return workitem;
    }

    public Workitem findWorkitem(UPSContext ctx) throws DicomServiceException {
        try {
            return em.createNamedQuery(Workitem.FIND_BY_SOP_IUID_EAGER, Workitem.class)
                    .setParameter(1, ctx.getSopInstanceUID())
                    .getSingleResult();
        } catch (NoResultException e) {
            throw new DicomServiceException(Status.NoSuchUPSInstance, "No such UPS Instance");
        }
    }

    private void checkDuplicate(UPSContext ctx) throws DicomServiceException {
        try {
            em.createNamedQuery(Workitem.FIND_BY_SOP_IUID, Workitem.class)
                    .setParameter(1, ctx.getSopInstanceUID())
                    .getSingleResult();
            throw new DicomServiceException(Status.DuplicateSOPinstance, "Duplicate UPS Instance");
        } catch (NoResultException e) {
        }
    }

    private void setReferencedRequests(Collection<WorkitemRequest> referencedRequests,
            Sequence seq, FuzzyStr fuzzyStr) {
        referencedRequests.clear();
        if (seq != null) {
            for (Attributes item : seq) {
                referencedRequests.add(new WorkitemRequest(
                        item,
                        findOrCreateIssuer(item, Tag.IssuerOfAccessionNumberSequence),
                        fuzzyStr));
            }
        }
    }

    private IssuerEntity findOrCreateIssuer(Attributes attrs, int tag) {
        Issuer issuer = Issuer.valueOf(attrs.getNestedDataset(tag));
        return issuer != null ? issuerService.mergeOrCreate(issuer) : null;
    }

    private CodeEntity findOrCreateCode(Attributes attrs, int seqTag) {
        Attributes item = attrs.getNestedDataset(seqTag);
        if (item != null)
            try {
                return codeCache.findOrCreate(new Code(item));
            } catch (Exception e) {
                LOG.info("Illegal code item in Sequence {}:\n{}", TagUtils.toString(seqTag), item);
            }
        return null;
    }

    private void setHumanPerformerCodes(Collection<CodeEntity> codes, Sequence seq) {
        codes.clear();
        if (seq != null) {
            for (Attributes item : seq) {
                try {
                    codes.add(codeCache.findOrCreate(
                            new Code(item.getNestedDataset(Tag.HumanPerformerCodeSequence))));
                } catch (Exception e) {
                    LOG.info("Missing or invalid Human Performer Code:\n{}", item);
                }
            }
        }
    }
}
