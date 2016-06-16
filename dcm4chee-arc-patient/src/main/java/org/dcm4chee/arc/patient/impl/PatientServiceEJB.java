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

package org.dcm4chee.arc.patient.impl;

import org.dcm4che3.audit.AuditMessages;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.data.Issuer;
import org.dcm4chee.arc.conf.AttributeFilter;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.issuer.IssuerService;
import org.dcm4chee.arc.patient.NonUniquePatientException;
import org.dcm4chee.arc.patient.PatientMergedException;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2015
 */
@Stateless
public class PatientServiceEJB {

    private static final Logger LOG = LoggerFactory.getLogger(PatientServiceEJB.class);

    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    @Inject
    private IssuerService issuerService;

    public List<Patient> findPatients(IDWithIssuer pid) {
        List<Patient> list = em.createNamedQuery(Patient.FIND_BY_PATIENT_ID_EAGER, Patient.class)
                .setParameter(1, pid.getID())
                .getResultList();
        Issuer issuer = pid.getIssuer();
        removeNonMatchingIssuer(list, issuer);
        if (list.size() > 1) {
            if (issuer != null) {
                removeWithoutIssuer(list);
            }
        }
        return list;
    }

    private void removeWithoutIssuer(List<Patient> list) {
        for (Iterator<Patient> it = list.iterator(); it.hasNext();) {
            IssuerEntity ie = it.next().getPatientID().getIssuer();
            if (ie == null)
                it.remove();
        }
    }

    private void removeNonMatchingIssuer(List<Patient> list, Issuer issuer) {
        if (issuer != null) {
            for (Iterator<Patient> it = list.iterator(); it.hasNext();) {
                IssuerEntity ie = it.next().getPatientID().getIssuer();
                if (ie != null && !ie.getIssuer().matches(issuer))
                    it.remove();
            }
        }
    }

    public Patient createPatient(PatientMgtContext ctx) {
        ctx.setEventActionCode(AuditMessages.EventActionCode.Create);
        return createPatient(ctx, ctx.getPatientID(), ctx.getAttributes());
    }

    private Patient createPatient(PatientMgtContext ctx, IDWithIssuer patientID, Attributes attributes) {
        Patient patient = new Patient();
        patient.setAttributes(attributes, ctx.getAttributeFilter(), ctx.getFuzzyStr());
        patient.setPatientID(createPatientID(patientID));
        em.persist(patient);
        return patient;
    }

    public Patient updatePatient(PatientMgtContext ctx)
            throws NonUniquePatientException, PatientMergedException {
        Patient pat = findPatient(ctx.getPatientID());
        if (pat == null)
            return createPatient(ctx);

        if (updatePatient(pat, ctx))
            ctx.setEventActionCode(AuditMessages.EventActionCode.Update);
        return pat;
    }

    public Patient findPatient(IDWithIssuer pid)
            throws NonUniquePatientException, PatientMergedException {
        List<Patient> list = findPatients(pid);
        if (list.isEmpty())
            return null;

        if (list.size() > 1)
            throw new NonUniquePatientException("Multiple Patients with ID " + pid);

        Patient pat = list.get(0);
        Patient mergedWith = pat.getMergedWith();
        if (mergedWith != null)
            throw new PatientMergedException("" + pat + " merged with " + mergedWith);

        return pat;
    }

    private boolean updatePatient(Patient pat, PatientMgtContext ctx) {
        Attributes.UpdatePolicy updatePolicy = ctx.getAttributeUpdatePolicy();
        AttributeFilter filter = ctx.getAttributeFilter();
        Attributes attrs = pat.getAttributes();
        Attributes newAttrs = new Attributes(ctx.getAttributes(), filter.getSelection());
        if (updatePolicy == Attributes.UpdatePolicy.REPLACE) {
            if (attrs.equals(newAttrs)) {
                return false;
            }
            attrs = newAttrs;
        } else if (!attrs.update(updatePolicy, newAttrs, null)) {
            return false;
        }
        pat.setAttributes(attrs, filter, ctx.getFuzzyStr());
        return true;
    }

    public Patient mergePatient(PatientMgtContext ctx)
            throws NonUniquePatientException, PatientMergedException {
        Patient pat = findPatient(ctx.getPatientID());
        if (pat == null)
            pat = createPatient(ctx);
        else {
            updatePatient(pat, ctx);
            ctx.setEventActionCode(AuditMessages.EventActionCode.Update);
        }
        Patient prev = findPatient(ctx.getPreviousPatientID());
        if (prev == null) {
            prev = createPatient(ctx, ctx.getPreviousPatientID(), ctx.getPreviousAttributes());
            ctx.setPreviousAttributes(null); // suppress audit message for deletion of merge patient
        } else {
            moveStudies(prev, pat);
            moveMPPS(prev, pat);
        }
        prev.setMergedWith(pat);
        return pat;
    }

    public Patient changePatientID(PatientMgtContext ctx)
            throws NonUniquePatientException, PatientMergedException {
        Patient pat = findPatient(ctx.getPreviousPatientID());
        if (pat == null) {
            ctx.setPreviousAttributes(null); // suppress audit message for deletion of merge patient
            return createPatient(ctx);
        }

        pat.setPatientID(createPatientID(ctx.getPatientID()));
        updatePatient(pat, ctx);
        ctx.setEventActionCode(AuditMessages.EventActionCode.Create);
        return pat;
    }

    public Patient findPatient(PatientMgtContext ctx) {
        if (ctx.getPatientID() == null) {
            LOG.info("{}: No Patient ID in received object", ctx);
            return null;
        }

        List<Patient> list = findPatients(ctx.getPatientID());
        if (list.isEmpty())
            return null;

        if (list.size() > 1) {
            LOG.info("{}: Multiple Patients with ID: {}", ctx, ctx.getPatientID());
            return null;
        }

        Patient pat = list.get(0);
        Patient mergedWith = pat.getMergedWith();
        if (mergedWith == null)
            return pat;

        HashSet<Long> patPks = new HashSet<>();
        do {
            if (!patPks.add(mergedWith.getPk())) {
                LOG.warn("{}: Detected circular merged {}", ctx, ctx.getPatientID());
                return null;
            }

            pat = mergedWith;
            mergedWith = pat.getMergedWith();
        } while (mergedWith != null);
        return pat;
    }

    private void moveStudies(Patient from, Patient to) {
        for (Study study : em.createNamedQuery(Study.FIND_BY_PATIENT, Study.class)
                .setParameter(1, from).getResultList()) {
            study.setPatient(to);
        }
    }

    private void moveMPPS(Patient from, Patient to) {
        for (MPPS mpps : em.createNamedQuery(MPPS.FIND_BY_PATIENT, MPPS.class)
                .setParameter(1, from).getResultList()) {
            mpps.setPatient(to);
        }
    }

    private PatientID createPatientID(IDWithIssuer idWithIssuer) {
        if (idWithIssuer == null)
            return null;

        PatientID patientID = new PatientID();
        patientID.setID(idWithIssuer.getID());
        if (idWithIssuer.getIssuer() != null)
            patientID.setIssuer(issuerService.findOrCreate(idWithIssuer.getIssuer()));

        return patientID;
    }
}
