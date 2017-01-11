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
 * Portions created by the Initial Developer are Copyright (C) 2015
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

package org.dcm4chee.arc.mpps.impl;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Code;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.service.BasicMPPSSCP;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4chee.arc.code.CodeCache;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.AttributeFilter;
import org.dcm4chee.arc.conf.Entity;
import org.dcm4chee.arc.entity.CodeEntity;
import org.dcm4chee.arc.entity.MPPS;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.issuer.IssuerService;
import org.dcm4chee.arc.mpps.MPPSContext;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.patient.PatientService;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Sep 2015
 */
@Stateless
public class MPPSServiceEJB {

    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    @Inject
    private PatientService patientService;

    @Inject
    private CodeCache codeCache;

    @Inject
    private IssuerService issuerService;

    public MPPS createMPPS(MPPSContext ctx) throws DicomServiceException {
        ArchiveAEExtension arcAE = ctx.getArchiveAEExtension();
        ArchiveDeviceExtension arcDev = arcAE.getArchiveDeviceExtension();
        AttributeFilter filter = arcDev.getAttributeFilter(Entity.MPPS);
        Attributes attrs = ctx.getAttributes();
        MPPS mpps = new MPPS();
        mpps.setSopInstanceUID(ctx.getSopInstanceUID());
        PatientMgtContext patMgtCtx = patientService.createPatientMgtContextDIMSE(ctx.getAssociation());
        patMgtCtx.setAttributes(attrs);
        Patient pat = patientService.findPatient(patMgtCtx);
        if (pat == null) {
            pat = patientService.createPatient(patMgtCtx);
        }
        mpps.setPatient(pat);
        mpps.setDiscontinuationReasonCode(discontinuationReasonCodeOf(attrs));
        mpps.setAttributes(attrs, filter);
        em.persist(mpps);
        return mpps;
    }

    public MPPS updateMPPS(MPPSContext ctx) throws DicomServiceException {
        ArchiveAEExtension arcAE = ctx.getArchiveAEExtension();
        ArchiveDeviceExtension arcDev = arcAE.getArchiveDeviceExtension();
        AttributeFilter filter = arcDev.getAttributeFilter(Entity.MPPS);
        MPPS mpps = findMPPS(ctx);
        if (mpps.getStatus() != MPPS.Status.IN_PROGRESS)
            BasicMPPSSCP.mayNoLongerBeUpdated();
        Attributes attrs = mpps.getAttributes();
        if (attrs.updateSelected(Attributes.UpdatePolicy.OVERWRITE, ctx.getAttributes(), null, filter.getSelection())) {
            mpps.setDiscontinuationReasonCode(discontinuationReasonCodeOf(attrs));
            mpps.setAttributes(attrs, filter);
        }
        return mpps;
    }

    public MPPS findMPPS(MPPSContext ctx) throws DicomServiceException {
        try {
            return em.createNamedQuery(MPPS.FIND_BY_SOP_INSTANCE_UID_EAGER, MPPS.class)
                        .setParameter(1, ctx.getSopInstanceUID())
                        .getSingleResult();
        } catch (NoResultException e) {
            throw new DicomServiceException(Status.NoSuchObjectInstance);
        }
    }

    private CodeEntity discontinuationReasonCodeOf(Attributes attrs) {
        Attributes item = attrs.getNestedDataset(Tag.ProcedureStepDiscontinuationReasonCodeSequence);
        return item != null ? codeCache.findOrCreate(new Code(item)) : null;
    }
}
