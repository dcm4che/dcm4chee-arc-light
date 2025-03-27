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

import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.*;
import org.dcm4che3.audit.AuditMessages;
import org.dcm4che3.data.*;
import org.dcm4che3.net.Device;
import org.dcm4che3.util.AttributesFormat;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.ArchiveHL7ApplicationExtension;
import org.dcm4chee.arc.conf.AttributeFilter;
import org.dcm4chee.arc.conf.HL7ReferredMergedPatientPolicy;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.patient.*;
import org.hibernate.annotations.QueryHints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jul 2015
 */
@Stateless
public class PatientServiceEJB {

    private static final Logger LOG = LoggerFactory.getLogger(PatientServiceEJB.class);

    @PersistenceContext(unitName = "dcm4chee-arc")
    private EntityManager em;

    @Inject
    private Device device;

    private static boolean matchingIssuer(Issuer issuer, Issuer other) {
        return issuer != null && other != null
                && (issuer.getLocalNamespaceEntityID() != null && other.getLocalNamespaceEntityID() != null
                || issuer.getUniversalEntityID() != null && other.getUniversalEntityID() != null);
     }

    public Collection<Patient> findPatients(Collection<IDWithIssuer> pids) {
        IdentityHashMap<Patient,Object> withoutMatchingIssuer = new IdentityHashMap<>();
        IdentityHashMap<Patient,Object> withMatchingIssuer = new IdentityHashMap<>();
        findPatients(pids, withoutMatchingIssuer, withMatchingIssuer);
        return (withMatchingIssuer.isEmpty() ? withoutMatchingIssuer : withMatchingIssuer).keySet();
    }

    private void findPatients(Collection<IDWithIssuer> pids, IdentityHashMap<Patient, Object> withoutMatchingIssuer,
                           IdentityHashMap<Patient, Object> withMatchingIssuer) {
        for (IDWithIssuer pid : pids) {
            for (PatientID patientID : findPatientIDs(pid)) {
                (matchingIssuer(pid.getIssuer(), patientID.getIssuer()) ? withMatchingIssuer : withoutMatchingIssuer)
                        .put(patientID.getPatient(), null);
            }
        }
    }

    private List<PatientID> findPatientIDs(IDWithIssuer pid) {
        List<PatientID> list = em.createNamedQuery(PatientID.FIND_BY_PATIENT_ID_EAGER, PatientID.class)
                .setParameter(1, pid.getID())
                .getResultList();
        if (pid.getIssuer() != null)
            list.removeIf(id -> id.getIssuer() != null && !id.getIssuer().matches(pid.getIssuer(), true, true));
        return list;
    }

    public Patient createPatient(PatientMgtContext ctx) {
        ctx.setEventActionCode(AuditMessages.EventActionCode.Create);
        return createPatient(ctx, ctx.getPatientIDs(), ctx.getAttributes());
    }

    private Patient createPatient(PatientMgtContext ctx, Collection<IDWithIssuer> patientIDs, Attributes attributes) {
        Patient patient = new Patient();
        patient.setVerificationStatus(ctx.getPatientVerificationStatus());
        if (ctx.getPatientVerificationStatus() != Patient.VerificationStatus.UNVERIFIED)
            patient.setVerificationTime(new Date());
        patient.setAttributes(attributes, ctx.getAttributeFilter(), false, ctx.getFuzzyStr());
        em.persist(patient);
        for (IDWithIssuer patientID : patientIDs) {
            patient.getPatientIDs().add(createPatientID(patientID, patient));
        }
        LOG.info("{}: Create {}", ctx, patient);
        return patient;
    }

    public Patient updatePatient(PatientMgtContext ctx)
            throws NonUniquePatientException, PatientMergedException {
        Patient pat = findNotMergedPatient(ctx.getPatientIDs());
        if (pat == null) {
            if (ctx.isNoPatientCreate()) {
                logSuppressPatientCreate(ctx);
                return null;
            }
            return createPatient(ctx);
        }
        if (ctx.isNoPatientUpdate()) {
            logSuppressPatientUpdate(ctx);
            return pat;
        }
        updatePatient(pat, ctx);
        return pat;
    }

    private void logSuppressPatientCreate(PatientMgtContext ctx) {
        LOG.info("{}: Suppress creation of Patient[id={}] by {}", ctx, ctx.getPatientIDs(), ctx.getUnparsedHL7Message().msh());
    }

    private void logSuppressPatientUpdate(PatientMgtContext ctx) {
        LOG.info("{}: Suppress update of Patient[id={}] by {}", ctx, ctx.getPatientIDs(), ctx.getUnparsedHL7Message().msh());
    }

    public Patient findPatient(Collection<IDWithIssuer> pids) {
        Collection<Patient> list = findPatients(pids);
        if (list.isEmpty())
            return null;

        if (list.size() > 1)
            throw new NonUniquePatientException("Multiple Patients with ID " + pids);

        return list.iterator().next();
    }

    public Patient findNotMergedPatient(Collection<IDWithIssuer> pids)
            throws NonUniquePatientException, PatientMergedException {
        Patient pat = findPatient(pids);
        Patient mergedWith;
        if (pat != null && (mergedWith = pat.getMergedWith()) != null)
            throw new PatientMergedException("" + pat + " merged with " + mergedWith);

        return pat;
    }

    public boolean unmergePatient(PatientMgtContext ctx)
            throws NonUniquePatientException, PatientUnmergedException {
        Collection<IDWithIssuer> pids = ctx.getPatientIDs();
        Collection<Patient> list = findPatients(pids);
        if (list.isEmpty())
            return false;

        if (list.size() > 1)
            throw new NonUniquePatientException("Multiple Patients with ID : " + pids);

        Patient pat = list.iterator().next();
        Patient mergedWith = pat.getMergedWith();
        if (mergedWith == null)
            throw new PatientUnmergedException("Patient is not merged : " + pids);

        ctx.setAttributes(pat.getAttributes());
        ctx.setEventActionCode(AuditMessages.EventActionCode.Create);
        pat.setMergedWith(null);
        return true;
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

            Sequence prevOrigAttrsSeq = attrs.getSequence(Tag.OriginalAttributesSequence);
            if (prevOrigAttrsSeq != null) {
                String[] prevSpecificCharacterSet = attrs.getStrings(Tag.SpecificCharacterSet);
                boolean compatibleCS = newAttrs.getSpecificCharacterSet().contains(attrs.getSpecificCharacterSet());
                Sequence newOrigAttrsSeq = newAttrs.newSequence(Tag.OriginalAttributesSequence, prevOrigAttrsSeq.size());
                for (Attributes item : prevOrigAttrsSeq) {
                    Attributes copy = new Attributes(item);
                    Attributes prevModified = item.getNestedDataset(Tag.ModifiedAttributesSequence);
                    if (!compatibleCS && !prevModified.contains(Tag.SpecificCharacterSet)) {
                        prevModified.setString(Tag.SpecificCharacterSet, VR.CS, prevSpecificCharacterSet);
                    }
                    newOrigAttrsSeq.add(copy);
                }
            }
            attrs = newAttrs;
        } else {
            Attributes.unifyCharacterSets(attrs, newAttrs);
            if (!updatePatientAttrs(attrs, updatePolicy, newAttrs, modified, filter))
                return;
        }

        updatePatientAttrs(pat, ctx, attrs, modified);
        LOG.info("{} updated successfully.", pat);
    }

    private void updatePatientAttrs(Patient pat, PatientMgtContext ctx, Attributes attrs, Attributes modified) {
        updatePatientIDs(pat,
                getArchiveDeviceExtension().retainTrustedPatientIDs(IDWithIssuer.pidsOf(attrs)));

        ctx.setEventActionCode(AuditMessages.EventActionCode.Update);
        pat.setAttributes(recordAttributeModification(ctx)
                ? attrs.addOriginalAttributes(
                        null,
                        new Date(),
                        Attributes.CORRECT,
                        device.getDeviceName(),
                        modified)
                : attrs,
                ctx.getAttributeFilter(), true, ctx.getFuzzyStr());
        if (ctx.isUpdateSeriesMetadata()) {
            em.createNamedQuery(Series.SCHEDULE_METADATA_UPDATE_FOR_PATIENT)
                    .setParameter(1, pat)
                    .executeUpdate();
        }
    }

    public void updatePatientIDs(Patient pat, Collection<IDWithIssuer> patientIDs) {
        Collection<IDWithIssuer> newPatientIDs = new LinkedList<>(patientIDs);
        for (Iterator<PatientID> iter = pat.getPatientIDs().iterator(); iter.hasNext();) {
            PatientID patientID = iter.next();
            IDWithIssuer newPatientID = removeByID(newPatientIDs, patientID.getID());
            if (newPatientID == null) {
                em.remove(patientID);
                iter.remove();
            } else {
                patientID.setIssuer(newPatientID.getIssuer());
            }
        }
        for (IDWithIssuer newPatientID : newPatientIDs) {
            pat.getPatientIDs().add(createPatientID(newPatientID, pat));
        }
    }

    private static IDWithIssuer removeByID(Collection<IDWithIssuer> newPatientIDs, String id) {
        Iterator<IDWithIssuer> iter = newPatientIDs.iterator();
        while (iter.hasNext()) {
            IDWithIssuer next = iter.next();
            if (next.getID().equals(id)) {
                iter.remove();
                return next;
            }
        }
        return null;
    }

    public Patient mergePatient(PatientMgtContext ctx)
            throws NonUniquePatientException, PatientMergedException {
        Patient pat = findPatient(ctx.getPatientIDs());
        Patient mergedWith;
        if (pat != null && (mergedWith = pat.getMergedWith()) != null) {
            if (ctx.getHl7ReferredMergedPatientPolicy() != HL7ReferredMergedPatientPolicy.ACCEPT_INVERSE_MERGE
                    || mergedWith.getPatientIDs().stream()
                    .map(PatientID::getIDWithIssuer)
                    .noneMatch(ctx.getPreviousPatientIDs()::contains)) {
                throw new PatientMergedException("" + pat + " merged with " + mergedWith);
            }
            pat.setMergedWith(null);
        }
        Patient prev = findNotMergedPatient(ctx.getPreviousPatientIDs());
        if (pat == null && prev == null && ctx.isNoPatientCreate()) {
            logSuppressPatientCreate(ctx);
            return null;
        }
        if (pat == null)
            pat = createPatient(ctx);
        else {
            updatePatient(pat, ctx);
            ctx.setEventActionCode(AuditMessages.EventActionCode.Update);
        }
        if (prev == null) {
            prev = createPatient(ctx, ctx.getPreviousPatientIDs(), ctx.getPreviousAttributes());
            suppressMergedPatientDeletionAudit(ctx);
        } else {
            moveStudies(ctx, prev, pat);
            moveMPPS(prev, pat);
            moveMWLItems(prev, pat);
            moveUPS(prev, pat);
        }
        if (ctx.getHttpServletRequestInfo() != null) {
            ctx.setAttributes(pat.getAttributes());
            ctx.setPreviousAttributes(prev.getAttributes());
        }
        prev.setMergedWith(pat);
        return pat;
    }

    private void suppressMergedPatientDeletionAudit(PatientMgtContext ctx) {
        ctx.setPreviousAttributes(null);
    }

    public Patient changePatientID(PatientMgtContext ctx)
            throws NonUniquePatientException, PatientMergedException, PatientAlreadyExistsException {
        Patient pat = findNotMergedPatient(ctx.getPreviousPatientIDs());
        if (pat == null) {
            if (ctx.isNoPatientCreate()) {
                logSuppressPatientCreate(ctx);
                return null;
            }
            suppressMergedPatientDeletionAudit(ctx);
            return createPatient(ctx);
        }
        if (ctx.getPreviousAttributes() == null)
            ctx.setPreviousAttributes(new Attributes(pat.getAttributes()));

        Collection<IDWithIssuer> patientIDs = ctx.getPatientIDs();
        Patient pat2 = findNotMergedPatient(patientIDs);
        if (pat2 != null && pat2 != pat)
            throw new PatientAlreadyExistsException("Patient with Patient IDs " + pat2.getPatientIDs() + "already exists");

        if (ctx.getPatientVerificationStatus() != Patient.VerificationStatus.UNVERIFIED) {
            pat.setVerificationStatus(ctx.getPatientVerificationStatus());
            pat.setVerificationTime(new Date());
            pat.resetFailedVerifications();
        }

        updatePatientIDAttrs(ctx, pat);
        ctx.setEventActionCode(AuditMessages.EventActionCode.Update);
        return pat;
    }

    private void updatePatientIDAttrs(PatientMgtContext ctx, Patient pat) {
        Attributes attrs = pat.getAttributes();
        Attributes newIDAttrs = new Attributes(ctx.getAttributes());
        setNullIfMissing(newIDAttrs, Tag.IssuerOfPatientID, VR.LO);
        setNullIfMissing(newIDAttrs, Tag.IssuerOfPatientIDQualifiersSequence, VR.SQ);
        setNullIfMissing(newIDAttrs, Tag.OtherPatientIDsSequence, VR.SQ);
        Attributes modified = new Attributes();
        attrs.update(Attributes.UpdatePolicy.OVERWRITE, false, newIDAttrs, modified);
        updatePatientAttrs(pat, ctx, attrs, modified);
    }

    private static void setNullIfMissing(Attributes attrs, int tag, VR vr) {
        if (!attrs.contains(tag)) attrs.setNull(tag, vr);
    }

    public Patient findNotMergedPatient(PatientMgtContext ctx) {
        Collection<IDWithIssuer> patientIDs = ctx.getPatientIDs();
        if (patientIDs.isEmpty()) {
            LOG.info("{}: No Patient IDs in received object", ctx);
            return null;
        }

        IdentityHashMap<Patient,Object> withoutMatchingIssuer = new IdentityHashMap<>();
        IdentityHashMap<Patient,Object> withMatchingIssuer = new IdentityHashMap<>();
        findPatients(patientIDs, withoutMatchingIssuer, withMatchingIssuer);
        Collection<Patient> list = (withMatchingIssuer.isEmpty() ? withoutMatchingIssuer : withMatchingIssuer).keySet();
        if (withMatchingIssuer.isEmpty() && getArchiveDeviceExtension().isIdentifyPatientByAllAttributes()) {
            removeNonMatching(ctx, list);
            if (list.size() > 1) {
                LOG.info("{}: Found {} Patients with IDs: {} and matching attributes", ctx, list.size(), patientIDs);
                return null;
            }
        }
        if (list.isEmpty())
            return null;

        if (list.size() > 1) {
            LOG.info("{}: Found {} Patients with IDs: {}", ctx, list.size(), patientIDs);
            removeNonMatching(ctx, list);
            if (list.size() != 1)
                return null;
            LOG.info("{}: Select {} with matching attributes", ctx, list.iterator().next());
        }

        Patient pat = list.iterator().next();
        Patient mergedWith = pat.getMergedWith();
        if (mergedWith == null)
            return pat;

        HashSet<Long> patPks = new HashSet<>();
        do {
            if (!patPks.add(mergedWith.getPk())) {
                LOG.warn("{}: Detected circular merged {}", ctx, patientIDs);
                return null;
            }

            pat = mergedWith;
            mergedWith = pat.getMergedWith();
        } while (mergedWith != null);
        return pat;
    }

    private static void removeNonMatching(PatientMgtContext ctx, Collection<Patient> list) {
        Attributes attrs = ctx.getAttributes();
        int before = list.size();
        list.removeIf(p -> !attrs.matches(p.getAttributes(), false, true));
        int removed = before - list.size();
        if (removed > 0) {
            LOG.info("{}: Found {} Patients with IDs: {} but non-matching other attributes",
                    ctx, removed, ctx.getPatientIDs());
        }
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
                    study.setAttributes(recordAttributeModification(ctx)
                                    ? study.getAttributes()
                                        .addOriginalAttributes(
                                            null,
                                            new Date(),
                                            Attributes.CORRECT,
                                            device.getDeviceName(),
                                            modified)
                                    : study.getAttributes(),
                            ctx.getStudyAttributeFilter(), true, ctx.getFuzzyStr());
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

    private PatientID createPatientID(IDWithIssuer idWithIssuer, Patient patient) {
        if (idWithIssuer == null)
            return null;

        PatientID patientID = new PatientID();
        patientID.setPatient(patient);
        patientID.setID(idWithIssuer.getID());
        patientID.setIssuer(ensureFullQualified(idWithIssuer.getIssuer()));
        em.persist(patientID);
        return patientID;
    }

    private Issuer ensureFullQualified(Issuer issuer) {
        return (issuer == null)
            ? new Issuer(
                    "*",
                    "iss:*",
                    "URI")
            : (issuer.getLocalNamespaceEntityID() == null)
            ? new Issuer(
                    issuer.getUniversalEntityID(),
                    issuer.getUniversalEntityID(),
                    issuer.getUniversalEntityIDType())
            : (issuer.getUniversalEntityID() == null)
            ? new Issuer(
                    issuer.getLocalNamespaceEntityID(),
                    "iss:" + issuer.getLocalNamespaceEntityID(),
                    "URI")
            : issuer;
    }

    public void deletePatient(Patient patient) {
        List<Patient> patients = em.createNamedQuery(Patient.FIND_BY_MERGED_WITH, Patient.class)
                .setParameter(1, patient)
                .getResultList();
        for (Patient p : patients)
            deletePatient(p);
        removeMPPSMWLUWLAndPatient(patient);
    }

    private void removeMPPSMWLUWLAndPatient(Patient patient) {
        em.createNamedQuery(MPPS.DELETE_BY_PATIENT)
                .setParameter(1, patient)
                .executeUpdate();
        em.createNamedQuery(MWLItem.FIND_BY_PATIENT, MWLItem.class)
                .setParameter(1, patient)
                .getResultList()
                .forEach(mwl -> em.remove(mwl));
        em.createNamedQuery(UPS.FIND_BY_PATIENT, UPS.class)
                .setParameter(1, patient)
                .getResultList()
                .forEach(ups -> em.remove(ups));
        em.remove(em.contains(patient) ? patient : em.getReference(Patient.class, patient.getPk()));
        LOG.info("Successfully removed {} from database along with any of its MPPS, MWLs and UPS", patient);
    }

    public Patient updatePatientStatus(PatientMgtContext ctx) {
        Patient pat = findNotMergedPatient(ctx.getPatientIDs());
        if (pat != null) {
            pat.setVerificationStatus(ctx.getPatientVerificationStatus());
            pat.setVerificationTime(new Date());
            if (ctx.getPatientVerificationStatus() == Patient.VerificationStatus.VERIFICATION_FAILED)
                pat.incrementFailedVerifications();
            else
                pat.resetFailedVerifications();
            ctx.setEventActionCode(AuditMessages.EventActionCode.Update);
        }
        return pat;
    }

    private boolean recordAttributeModification(PatientMgtContext ctx) {
        return ctx.getArchiveAEExtension() != null
                ? ctx.getArchiveAEExtension().recordAttributeModification()
                : ctx.getHL7Application() != null
                ? ctx.getHL7Application()
                    .getHL7AppExtensionNotNull(ArchiveHL7ApplicationExtension.class)
                    .recordAttributeModification()
                : getArchiveDeviceExtension().isRecordAttributeModification();
    }

    private ArchiveDeviceExtension getArchiveDeviceExtension() {
        return device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
    }

    public List<String> studyInstanceUIDsOf(Patient patient) {
        return em.createNamedQuery(Study.STUDY_IUIDS_BY_PATIENT, String.class)
                .setParameter(1, patient)
                .getResultList();
    }

    public boolean supplementIssuer(
            PatientMgtContext ctx, PatientID patientID, IDWithIssuer idWithIssuer, Map<IDWithIssuer, Long> ambiguous) {
        Long count = countPatientIDWithIssuers(idWithIssuer);
        if (count != null && count != 0L) {
            ambiguous.put(idWithIssuer, count);
            return false;
        }

        patientID = em.merge(patientID);
        patientID.setIssuer(idWithIssuer.getIssuer());
        Patient patient = patientID.getPatient();
        Attributes patAttrs = patient.getAttributes();
        supplementIssuer(patAttrs, idWithIssuer);
        ctx.setAttributes(patAttrs);
        updatePatientIDAttrs(ctx, patient);
        ctx.setEventActionCode(AuditMessages.EventActionCode.Update);
        return true;
    }

    private void supplementIssuer(Attributes patAttrs, IDWithIssuer idWithIssuer) {
        supplementIssuer1(patAttrs, idWithIssuer);
        Sequence sequence = patAttrs.getSequence(Tag.OtherPatientIDsSequence);
        if (sequence != null) {
            for (Attributes item : sequence) {
                supplementIssuer1(item, idWithIssuer);
            }
        }
    }

    private void supplementIssuer1(Attributes attrs, IDWithIssuer idWithIssuer) {
        IDWithIssuer idWithIssuer0 = IDWithIssuer.pidOf(attrs);
        if (idWithIssuer0.getIssuer() == null && idWithIssuer0.getID().equals(idWithIssuer.getID()))
            idWithIssuer.getIssuer().toIssuerOfPatientID(attrs);
    }

    public Long countPatientIDWithIssuers(IDWithIssuer idWithIssuer) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        Root<PatientID> patientID = q.from(PatientID.class);
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(patientID.get(PatientID_.id), idWithIssuer.getID()));
        Issuer issuer = idWithIssuer.getIssuer();
        String entityID = issuer.getLocalNamespaceEntityID();
        String entityUID = issuer.getUniversalEntityID();
        String entityUIDType = issuer.getUniversalEntityIDType();
        if (entityID == null) {
            predicates.add(cb.equal(patientID.get(PatientID_.universalEntityID), entityUID));
            predicates.add(cb.equal(patientID.get(PatientID_.universalEntityIDType), entityUIDType));
        } else if (entityUID == null) {
            predicates.add(cb.equal(patientID.get(PatientID_.localNamespaceEntityID), entityID));
        } else {
            predicates.add(cb.or(
                    cb.equal(patientID.get(PatientID_.localNamespaceEntityID), entityID),
                    cb.and(
                            cb.equal(patientID.get(PatientID_.universalEntityID), entityUID),
                            cb.equal(patientID.get(PatientID_.universalEntityIDType), entityUIDType)
                    )));
        }
        return em.createQuery(q.where(predicates.toArray(new Predicate[0])).select(cb.count(patientID))).getSingleResult();
    }

    public <T> List<T> queryWithOffsetAndLimit(CriteriaQuery<T> query, int offset, int limit) {
        return em.createQuery(query).setFirstResult(offset).setMaxResults(limit).getResultList();
    }

    public <T> T merge(T entity) {
        return em.merge(entity);
    }

    public void testSupplementIssuers(CriteriaQuery<PatientID> query, int fetchSize,
            Set<IDWithIssuer> success, Map<IDWithIssuer, Long> ambiguous, AttributesFormat issuer) {
        try (Stream<PatientID> resultStream =
                     em.createQuery(query).setHint(QueryHints.FETCH_SIZE, fetchSize).getResultStream()) {
            resultStream
                    .map(pid -> new IDWithIssuer(pid.getID(), issuer.format(pid.getPatient().getAttributes())))
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                    .forEach((idWithIssuer, count) -> {
                        if (count == 1) {
                            Long countIDWithIssuers = countPatientIDWithIssuers(idWithIssuer);
                            if (countIDWithIssuers != null && countIDWithIssuers != 0L)
                                ambiguous.put(idWithIssuer, countIDWithIssuers);
                            else success.add(idWithIssuer);
                        } else ambiguous.put(idWithIssuer, count);
                    });
        }
    }

    public boolean deleteDuplicateCreatedPatient(Collection<IDWithIssuer> pids, Patient createdPatient, Study createdStudy) {
        Collection<Patient> patients = findPatients(pids);
        if (patients.size() == 1) {
            LOG.info("No duplicate record with equal Patient ID found {}", createdPatient);
            return false;
        }

        long createdPatientPk = createdPatient.getPk();
        Optional<Patient> createdPatientFound =
                patients.stream().filter(p -> p.getPk() == createdPatientPk).findFirst();
        if (createdPatientFound.isEmpty()) {
            LOG.warn("Failed to find created {}", createdPatient);
            return false;
        }

        byte[] encodedAttrs = createdPatient.getEncodedAttributes();
        Optional<Patient> otherPatientFound =
                patients.stream().filter(p ->
                                p.getPk() != createdPatientPk && Arrays.equals(p.getEncodedAttributes(), encodedAttrs))
                        .findFirst();
        if (otherPatientFound.isEmpty()) {
            LOG.info("No duplicate record with equal Patient attributes found {}", createdPatient);
            return false;
        }

        Patient otherPatient = otherPatientFound.get();
        if (otherPatient.getMergedWith() != null) {
            LOG.warn("Keep duplicate created {} because existing {} is circular merged",
                    createdPatient, otherPatient);
            return false;
        }
        LOG.info("Delete duplicate created {}", createdPatient);
        if (createdStudy != null) {
            em.merge(createdStudy).setPatient(otherPatient);
            otherPatient.incrementNumberOfStudies();
        }
        em.remove(createdPatientFound.get());
        return true;
    }

    public boolean updatePatientAttrs(Attributes attrs, Attributes.UpdatePolicy updatePolicy,
                                             Attributes newAttrs, Attributes modified, AttributeFilter filter) {
        int[] selection = without(filter.getSelection(false),
                Tag.PatientID,
                Tag.IssuerOfPatientID,
                Tag.TypeOfPatientID,
                Tag.IssuerOfPatientIDQualifiersSequence,
                Tag.OtherPatientIDsSequence);
        int pidUpdated = 0;
        Sequence otherPIDs = attrs.getSequence(Tag.OtherPatientIDsSequence);
        Set<IDWithIssuer> updatedPids = IDWithIssuer.pidsOf(newAttrs);
        if (mergeIssuer(attrs, updatedPids)) pidUpdated++;
        if (otherPIDs != null)
            for (Attributes item : otherPIDs) {
                if (mergeIssuer(item, updatedPids)) pidUpdated++;
            }
        Set<IDWithIssuer> pids = IDWithIssuer.pidsOf(attrs);
        if (addOtherPID(attrs, newAttrs, pids)) pidUpdated++;
        Sequence newOtherPIDs = newAttrs.getSequence(Tag.OtherPatientIDsSequence);
        if (newOtherPIDs != null)
            for (Attributes item : newOtherPIDs) {
                if (addOtherPID(attrs, item, pids)) pidUpdated++;
            }
        return attrs.updateSelected(updatePolicy, newAttrs, modified, selection) || pidUpdated > 0;
    }

    private boolean addOtherPID(Attributes attrs, Attributes newAttrs, Set<IDWithIssuer> pids) {
        IDWithIssuer newPID = IDWithIssuer.pidOf(newAttrs);
        if (newPID == null) return false;
        for (IDWithIssuer pid : pids) {
            if (pid.matches(newPID, true, true))
                return false;
        }
        if (!findPatientIDs(newPID).isEmpty()) {
            LOG.info("Ignore Patient ID {} already associated with different Patient", newPID);
            return false;
        }
        attrs.ensureSequence(Tag.OtherPatientIDsSequence, 1).add(
                new Attributes(newAttrs,
                        Tag.PatientID,
                        Tag.IssuerOfPatientID,
                        Tag.TypeOfPatientID,
                        Tag.IssuerOfPatientIDQualifiersSequence ));
        return true;
    }

    private static int[] without(int[] src, int... tags) {
        int[] index = new int[tags.length];
        int d = 0;
        for (int i = 0; i < index.length; i++) {
            if ((index[i] = Arrays.binarySearch(src, tags[i])) >= 0) d++;
        }
        if (d == 0) return src;
        int[] dest = new int[src.length-d];
        int srcPos = 0;
        int destPos = 0;
        for (int i : index) {
            if (i < 0) continue;
            int length = i - srcPos;
            System.arraycopy(src, srcPos, dest, destPos, length);
            srcPos = i+1;
            destPos += length;
        }
        System.arraycopy(src, srcPos, dest, destPos, dest.length - destPos);
        return dest;
    }

    private static boolean mergeIssuer(Attributes attrs, Set<IDWithIssuer> updatedPids) {
        IDWithIssuer pid = IDWithIssuer.pidOf(attrs);
        if (pid != null) {
            for (IDWithIssuer updatedPid : updatedPids) {
                if (pid.matches(updatedPid, true, true)) {
                    if (updatedPid.getIssuer() == null) return false;
                    Issuer issuer = pid.getIssuer();
                    if (issuer == null)
                        issuer = updatedPid.getIssuer();
                    else if (!issuer.merge(updatedPid.getIssuer()))
                        return false;
                    issuer.toIssuerOfPatientID(attrs);
                    return true;
                }
            }
        }
        return false;
    }
}
