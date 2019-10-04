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
import org.dcm4che3.data.*;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.AttributeFilter;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.issuer.IssuerService;
import org.dcm4chee.arc.patient.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jul 2015
 */
@Stateless
public class PatientServiceEJB {

    private static final Logger LOG = LoggerFactory.getLogger(PatientServiceEJB.class);

    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    @Inject
    private IssuerService issuerService;

    @Inject
    private Device device;

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
                Issuer other = ie != null ? ie.getIssuer() : null;
                if (other != null && !other.matches(issuer))
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
        patient.setVerificationStatus(ctx.getPatientVerificationStatus());
        if (ctx.getPatientVerificationStatus() != Patient.VerificationStatus.UNVERIFIED)
            patient.setVerificationTime(new Date());
        patient.setAttributes(attributes, ctx.getAttributeFilter(), ctx.getFuzzyStr());
        patient.setPatientID(createPatientID(patientID));
        em.persist(patient);
        LOG.info("{}: Create {}", ctx, patient);
        return patient;
    }

    public Patient updatePatient(PatientMgtContext ctx)
            throws NonUniquePatientException, PatientMergedException {
        Patient pat = findPatient(ctx.getPatientID());
        if (pat == null) {
            if (ctx.isNoPatientCreate()) {
                logSuppressPatientCreate(ctx);
                return null;
            }
            return createPatient(ctx);
        }
        updatePatient(pat, ctx);
        return pat;
    }

    private void logSuppressPatientCreate(PatientMgtContext ctx) {
        LOG.info("{}: Suppress creation of Patient[id={}] by {}", ctx, ctx.getPatientID(), ctx.getUnparsedHL7Message().msh());
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

    private void updatePatient(Patient pat, PatientMgtContext ctx) {
        if (ctx.getPatientVerificationStatus() != Patient.VerificationStatus.UNVERIFIED) {
            pat.setVerificationStatus(ctx.getPatientVerificationStatus());
            pat.setVerificationTime(new Date());
            pat.resetFailedVerifications();
        }
        Attributes.UpdatePolicy updatePolicy = ctx.getAttributeUpdatePolicy();
        AttributeFilter filter = ctx.getAttributeFilter();
        Attributes attrs = pat.getAttributes();
        Attributes newAttrs = new Attributes(ctx.getAttributes(), filter.getSelection());
        Attributes modified = new Attributes();
        if (updatePolicy == Attributes.UpdatePolicy.REPLACE) {
            if (attrs.diff(newAttrs, filter.getSelection(false), modified, true) == 0)
                return;

            newAttrs.addSelected(attrs, null, Tag.OriginalAttributesSequence);
            attrs = newAttrs;
        } else if (!attrs.updateSelected(updatePolicy, newAttrs, modified, filter.getSelection(false)))
            return;

        ctx.setEventActionCode(AuditMessages.EventActionCode.Update);
        pat.setAttributes(
                attrs.addOriginalAttributes(
                        null,
                        new Date(),
                        Attributes.CORRECT,
                        device.getDeviceName(),
                        modified),
                filter, ctx.getFuzzyStr());
        em.createNamedQuery(Series.SCHEDULE_METADATA_UPDATE_FOR_PATIENT)
                .setParameter(1, pat)
                .executeUpdate();
    }

    public Patient mergePatient(PatientMgtContext ctx)
            throws NonUniquePatientException, PatientMergedException {
        Patient pat = findPatient(ctx.getPatientID());
        Patient prev = findPatient(ctx.getPreviousPatientID());
        if (pat == null && prev == null && ctx.isNoPatientCreate()) {
            logSuppressPatientCreate(ctx);
            return null;
        }
        if (pat == null)
            pat = createPatient(ctx);
        else
            updatePatient(pat, ctx);
        if (prev == null) {
            prev = createPatient(ctx, ctx.getPreviousPatientID(), ctx.getPreviousAttributes());
            suppressMergedPatientDeletionAudit(ctx);
        } else {
            moveStudies(ctx, prev, pat);
            moveMPPS(prev, pat);
            moveMWLItems(prev, pat);
            moveUPS(prev, pat);
        }
        if (ctx.getHttpServletRequestInfo() != null) {
            if (pat.getPatientName() != null)
                ctx.getAttributes().setString(Tag.PatientName, VR.PN, pat.getPatientName().toString());
            if (prev.getPatientName() != null)
                ctx.getPreviousAttributes().setString(Tag.PatientName, VR.PN, prev.getPatientName().toString());
        }
        prev.setMergedWith(pat);
        return pat;
    }

    private void suppressMergedPatientDeletionAudit(PatientMgtContext ctx) {
        ctx.setPreviousAttributes(null);
    }

    public Patient changePatientID(PatientMgtContext ctx)
            throws NonUniquePatientException, PatientMergedException, PatientAlreadyExistsException {
        Patient pat = findPatient(ctx.getPreviousPatientID());
        if (pat == null) {
            if (ctx.isNoPatientCreate()) {
                logSuppressPatientCreate(ctx);
                return null;
            }
            suppressMergedPatientDeletionAudit(ctx);
            return createPatient(ctx);
        }

        IDWithIssuer patientID = ctx.getPatientID();
        Patient pat2 = findPatient(patientID);
        if (pat2 == null)
            pat.setPatientID(createPatientID(patientID));
        else if (pat2 == pat)
            updateIssuer(pat.getPatientID(), patientID.getIssuer());
        else
            throw new PatientAlreadyExistsException("Patient with Patient ID " + pat2.getPatientID() + "already exists");
        ctx.setEventActionCode(AuditMessages.EventActionCode.Update);
        updatePatientAttrs(ctx, pat);
        return pat;
    }

    private void updatePatientAttrs(PatientMgtContext ctx, Patient pat) {
        IDWithIssuer patientID = ctx.getPatientID();
        Attributes patientAttrs = pat.getAttributes();
        Attributes modified = new Attributes(patientAttrs,
                Tag.PatientID,
                Tag.IssuerOfPatientID,
                Tag.IssuerOfPatientIDQualifiersSequence);
        if (patientAttrs.getString(Tag.IssuerOfPatientID) != null) {
            Issuer patientIDIssuer = patientID.getIssuer();
            if (patientIDIssuer == null) {
                patientAttrs.remove(Tag.IssuerOfPatientID);
                patientAttrs.remove(Tag.IssuerOfPatientIDQualifiersSequence);
            } else if (patientIDIssuer.getUniversalEntityID() == null) {
                patientAttrs.remove(Tag.IssuerOfPatientIDQualifiersSequence);
            }
        }
        pat.setAttributes(patientID.exportPatientIDWithIssuer(patientAttrs)
                .addOriginalAttributes(
                        null,
                        new Date(),
                        Attributes.CORRECT,
                        device.getDeviceName(),
                        modified),
                ctx.getAttributeFilter(), ctx.getFuzzyStr());
        em.createNamedQuery(Series.SCHEDULE_METADATA_UPDATE_FOR_PATIENT)
                .setParameter(1, pat)
                .executeUpdate();
    }

    private void updateIssuer(PatientID patientID, Issuer issuer) {
        if (issuer == null) {
            patientID.setIssuer(null);
        } else {
            IssuerEntity entity = patientID.getIssuer();
            if (entity == null)
                patientID.setIssuer(issuerService.updateOrCreate(issuer));
            else
                entity.setIssuer(issuer);
        }
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

    private void moveStudies(PatientMgtContext ctx, Patient from, Patient to) {
        em.createNamedQuery(Study.FIND_BY_PATIENT, Study.class)
                .setParameter(1, from)
                .getResultList()
                .forEach(study -> {
                    Attributes modified = new Attributes();
                    from.getAttributes().diff(
                            to.getAttributes(),
                            ctx.getAttributeFilter().getSelection(false),
                            modified);
                    study.setPatient(to);
                    study.setAttributes(study.getAttributes()
                                    .addOriginalAttributes(
                                            null,
                                            new Date(),
                                            Attributes.CORRECT,
                                            device.getDeviceName(),
                                            modified),
                            ctx.getStudyAttributeFilter(), ctx.getFuzzyStr());
                    to.incrementNumberOfStudies();
                    from.decrementNumberOfStudies();
                });
    }

    private void moveMPPS(Patient from, Patient to) {
        em.createNamedQuery(MPPS.FIND_BY_PATIENT, MPPS.class)
                .setParameter(1, from)
                .getResultList()
                .forEach(mpps -> mpps.setPatient(to));
    }

    private void moveMWLItems(Patient from, Patient to) {
        em.createNamedQuery(MWLItem.FIND_BY_PATIENT, MWLItem.class)
                .setParameter(1, from)
                .getResultList()
                .forEach(mwl -> mwl.setPatient(to));
    }

    private void moveUPS(Patient from, Patient to) {
        em.createNamedQuery(UPS.FIND_BY_PATIENT, UPS.class)
                .setParameter(1, from)
                .getResultList()
                .forEach(ups -> ups.setPatient(to));
    }

    private PatientID createPatientID(IDWithIssuer idWithIssuer) {
        if (idWithIssuer == null)
            return null;

        PatientID patientID = new PatientID();
        patientID.setID(idWithIssuer.getID());
        if (idWithIssuer.getIssuer() != null)
            patientID.setIssuer(issuerService.mergeOrCreate(idWithIssuer.getIssuer()));

        return patientID;
    }

    public void deletePatient(Patient patient) {
        List<Patient> patients = em.createNamedQuery(Patient.FIND_BY_MERGED_WITH, Patient.class)
                .setParameter(1, patient)
                .getResultList();
        for (Patient p : patients)
            deletePatient(p);
        removeMPPSMWLAndPatient(patient);
    }

    private void removeMPPSMWLAndPatient(Patient patient) {
        em.createNamedQuery(MPPS.DELETE_BY_PATIENT)
                .setParameter(1, patient)
                .executeUpdate();
        em.createNamedQuery(MWLItem.FIND_BY_PATIENT, MWLItem.class)
                .setParameter(1, patient)
                .getResultList()
                .forEach(mwl -> em.remove(mwl));
        em.remove(em.contains(patient) ? patient : em.getReference(Patient.class, patient.getPk()));
        LOG.info("Successfully removed {} from database along with any of its MPPS and MWLs", patient);
    }

    public Patient updatePatientStatus(PatientMgtContext ctx) {
        Patient pat = findPatient(ctx.getPatientID());
        if (pat != null) {
            pat.setVerificationStatus(ctx.getPatientVerificationStatus());
            pat.setVerificationTime(new Date());
            if (ctx.getPatientVerificationStatus() == Patient.VerificationStatus.VERIFICATION_FAILED)
                pat.incrementFailedVerifications();
            else
                pat.resetFailedVerifications();
        }
        return pat;
    }
}