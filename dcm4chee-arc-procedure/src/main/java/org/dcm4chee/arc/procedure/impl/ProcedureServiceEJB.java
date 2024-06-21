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

import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.persistence.*;
import jakarta.persistence.criteria.*;
import org.dcm4che3.audit.AuditMessages;
import org.dcm4che3.data.*;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.soundex.FuzzyStr;
import org.dcm4che3.util.TagUtils;
import org.dcm4chee.arc.code.CodeCache;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.conf.Entity;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.patient.PatientMismatchException;
import org.dcm4chee.arc.patient.PatientService;
import org.dcm4chee.arc.procedure.ProcedureContext;
import org.dcm4chee.arc.query.util.QueryBuilder;
import org.dcm4chee.arc.query.util.QueryParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private PatientService patientService;

    @Inject
    private CodeCache codeCache;

    public void updateProcedure(ProcedureContext ctx) {
        if (ctx.getHttpRequest() != null)
            updateProcedureForWeb(ctx);
        else
            updateProcedureForHL7(ctx);
        updateStudySeriesAttributesFromMWL(ctx);
    }

    private void updateProcedureForHL7(ProcedureContext ctx) {
        Map<String, Attributes> mwlAttrsMap = createMWLAttrsMap(ctx.getAttributes());
        List<MWLItem> prevMWLItems = findMWLItems(ctx.getStudyInstanceUID());
        for (MWLItem mwlItem : prevMWLItems) {
            Attributes mwlAttrs = mwlAttrsMap.remove(mwlItem.getScheduledProcedureStepID());
            if (mwlAttrs == null)
                em.remove(mwlItem);
            else {
                if (mwlItem.getPatient().getPk() != ctx.getPatient().getPk())
                    throw new PatientMismatchException("" + ctx.getPatient() + " does not match " +
                            mwlItem.getPatient() + " in previous " + mwlItem);

                Attributes attrs = mwlItem.getAttributes();
                Attributes spsItem = attrs.getNestedDataset(Tag.ScheduledProcedureStepSequence);
                Attributes mwlSPSItem = mwlAttrs.getNestedDataset(Tag.ScheduledProcedureStepSequence);
                Attributes.unifyCharacterSets(attrs, mwlAttrs);
                boolean updateSPS = spsItem.update(ctx.getAttributeUpdatePolicy(), mwlSPSItem, null);
                attrs.remove(Tag.ScheduledProcedureStepSequence);
                mwlAttrs.remove(Tag.ScheduledProcedureStepSequence);
                if (!updateSPS
                        && !attrs.updateSelected(ctx.getAttributeUpdatePolicy(), mwlAttrs, null,
                                device.getDeviceExtension(ArchiveDeviceExtension.class)
                                      .getAttributeFilter(Entity.MWL)
                                      .getSelection(false)))
                    return;
                attrs.newSequence(Tag.ScheduledProcedureStepSequence, 1).add(spsItem);
                ctx.setAttributes(attrs);
                updateMWL(ctx, mwlItem);
            }
        }
        for (Attributes mwlAttrs : mwlAttrsMap.values()) {
            ctx.setAttributes(mwlAttrs);
            createMWL(ctx);
        }
    }

    private void updateMWL(ProcedureContext ctx, MWLItem mwlItem) {
        Attributes mwlAttrs = ctx.getAttributes();
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        mwlItem.setAttributes(mwlAttrs, arcDev.getAttributeFilter(Entity.MWL), arcDev.getFuzzyStr());
        mwlItem.setInstitutionCode(findOrCreateCode(mwlAttrs, Tag.InstitutionCodeSequence));
        mwlItem.setInstitutionalDepartmentTypeCode(findOrCreateCode(mwlAttrs, Tag.InstitutionalDepartmentTypeCodeSequence));
        ctx.setEventActionCode(AuditMessages.EventActionCode.Update);
        LOG.info("{}: Update {}", ctx, mwlItem);
    }

    private void updateProcedureForWeb(ProcedureContext ctx) {
        Attributes attrs = ctx.getAttributes();
        ctx.setSpsID(attrs.getNestedDataset(Tag.ScheduledProcedureStepSequence).getString(Tag.ScheduledProcedureStepID));

        MWLItem mwlItem = findMWLItem(ctx);
        if (mwlItem == null) {
            createMWL(ctx);
            return;
        }

        if (mwlItem.getPatient().getPk() != ctx.getPatient().getPk())
            throw new PatientMismatchException(ctx.getPatient()
                    + " found using patient identifiers sent in request payload does not match with "
                    + mwlItem.getPatient() + " of " + mwlItem);

        updateMWL(ctx, mwlItem);
    }

    public MWLItem findMWLItem(ProcedureContext ctx) {
        try {
            return em.createNamedQuery(MWLItem.FIND_BY_STUDY_UID_AND_SPS_ID_EAGER, MWLItem.class)
                    .setParameter(1, ctx.getStudyInstanceUID())
                    .setParameter(2, ctx.getSpsID())
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    public Attributes getMWLItemAttrs(String studyInstanceUID, String spsID) {
        try {
            Tuple results = em.createNamedQuery(MWLItem.ATTRS_BY_STUDY_UID_AND_SPS_ID, Tuple.class)
                    .setParameter(1, studyInstanceUID)
                    .setParameter(2, spsID)
                    .getSingleResult();
            Attributes mwlAttrs = AttributesBlob.decodeAttributes(results.get(0, byte[].class), null);
            Attributes patAttrs = AttributesBlob.decodeAttributes(results.get(1, byte[].class), null);
            Attributes.unifyCharacterSets(patAttrs, mwlAttrs);
            Attributes attrs = new Attributes(patAttrs.size() + mwlAttrs.size() + 1);
            attrs.addAll(patAttrs);
            attrs.addAll(mwlAttrs);
            return attrs;
        } catch (NoResultException e) {
            return null;
        }
    }

    private void createMWL(ProcedureContext ctx) {
        Attributes attrs = ctx.getAttributes();
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        MWLItem mwlItem = new MWLItem();
        mwlItem.setPatient(ctx.getPatient());
        Attributes spsItem = attrs.getNestedDataset(Tag.ScheduledProcedureStepSequence);
        if (!spsItem.containsValue(Tag.ScheduledProcedureStepStartDate))
            spsItem.setDate(Tag.ScheduledProcedureStepStartDateAndTime, new Date());
        if (!spsItem.contains(Tag.ScheduledPerformingPhysicianName))
            spsItem.setNull(Tag.ScheduledPerformingPhysicianName, VR.PN);
        if (!spsItem.contains(Tag.ScheduledStationName))
            spsItem.setNull(Tag.ScheduledStationName, VR.SH);
        if (!spsItem.contains(Tag.ScheduledProcedureStepLocation))
            spsItem.setNull(Tag.ScheduledProcedureStepLocation, VR.SH);
        mwlItem.setAttributes(attrs, arcDev.getAttributeFilter(Entity.MWL), arcDev.getFuzzyStr());
        mwlItem.setInstitutionCode(findOrCreateCode(attrs, Tag.InstitutionCodeSequence));
        mwlItem.setInstitutionalDepartmentTypeCode(findOrCreateCode(attrs, Tag.InstitutionalDepartmentTypeCodeSequence));
        em.persist(mwlItem);
        ctx.setEventActionCode(AuditMessages.EventActionCode.Create);
        LOG.info("{}: Create {}", ctx, mwlItem);
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
        List<MWLItem> mwlItems = findMWLItems(ctx.getStudyInstanceUID());
        for (MWLItem mwl : mwlItems)
            if (mwl.getScheduledProcedureStepID().equals(ctx.getSpsID())) {
                ctx.setEventActionCode(mwlItems.size() > 1
                        ? AuditMessages.EventActionCode.Update
                        : AuditMessages.EventActionCode.Delete);
                ctx.setAttributes(mwl.getAttributes());
                ctx.setPatient(mwl.getPatient());
                em.remove(mwl);
                break;
            }
    }

    public List<Long> mwlItemPKs(SPSStatus status, Date before, int mwlFetchSize) {
        return em.createNamedQuery(MWLItem.FIND_PK_BY_STATUS_AND_UPDATED_BEFORE, Long.class)
                .setParameter(1, status)
                .setParameter(2, before)
                .setMaxResults(mwlFetchSize)
                .getResultList();
    }

    public boolean deleteMWL(long pk) {
        try {
            em.remove(em.createNamedQuery(MWLItem.FIND_BY_PK, MWLItem.class)
                    .setParameter(1, pk)
                    .getSingleResult());
        } catch (NoResultException e) {
            LOG.warn("Failed to delete MWL[pk={}]:{}", pk, System.lineSeparator(), e);
            return false;
        }
        return true;
    }

    public void updateSPSStatus(ProcedureContext ctx) {
        MWLItem mwlItem = findMWLItem(ctx);
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        if (mwlItem == null)
            return;

        Attributes mwlAttrs = mwlItem.getAttributes();
        Attributes spsItemMWL = mwlAttrs.getNestedDataset(Tag.ScheduledProcedureStepSequence);
        if (!spsItemMWL.getString(Tag.ScheduledProcedureStepStatus).equals(ctx.getSpsStatus().name())) {
            spsItemMWL.setString(Tag.ScheduledProcedureStepStatus, VR.CS, ctx.getSpsStatus().name());
            mwlItem.setAttributes(mwlAttrs, arcDev.getAttributeFilter(Entity.MWL), arcDev.getFuzzyStr());
            ctx.setEventActionCode(AuditMessages.EventActionCode.Update);
        }
    }

    public List<MWLItem> updateMWLStatus(String studyIUID, SPSStatus status) {
        return updateMWLStatus(status, findMWLItems(studyIUID));
    }

    public List<MWLItem> updateMWLStatus(ArchiveAEExtension arcAE, HL7PSUTask task, SPSStatus status) {
        return updateMWLStatus(status, findMWLItems(arcAE, task));
    }

    private List<MWLItem> updateMWLStatus(SPSStatus status, List<MWLItem> mwlItems) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        mwlItems.forEach(mwl -> {
            Attributes mwlAttrs = mwl.getAttributes();
            Iterator<Attributes> spsItems = mwlAttrs.getSequence(Tag.ScheduledProcedureStepSequence).iterator();
            while (spsItems.hasNext()) {
                Attributes sps = spsItems.next();
                spsItems.remove();
                sps.setString(Tag.ScheduledProcedureStepStatus, VR.CS, status.name());
                mwlAttrs.newSequence(Tag.ScheduledProcedureStepSequence, 1).add(sps);
            }
            mwl.setAttributes(mwlAttrs, arcDev.getAttributeFilter(Entity.MWL), arcDev.getFuzzyStr());
        });
        return mwlItems;
    }

    private List<MWLItem> findMWLItems(String studyIUID) {
        return em.createNamedQuery(MWLItem.FIND_BY_STUDY_IUID_EAGER, MWLItem.class)
                .setParameter(1, studyIUID)
                .getResultList();
    }

    private List<MWLItem> findMWLItems(ArchiveAEExtension arcAE, HL7PSUTask task) {
        return arcAE.hl7PSUMWLMatchingKey() == HL7PSUMWLMatchingKey.AccessionNumber
                ? em.createNamedQuery(MWLItem.FIND_BY_ACCESSION_NO_EAGER, MWLItem.class)
                    .setParameter(1, task.getAccessionNumber())
                    .getResultList()
                : findMWLItems(task.getStudyInstanceUID());
    }

    public void updateMWLStatus(ProcedureContext ctx) {
        MWLItem mwlItem = findMWLItem(ctx);
        if (mwlItem == null)
            return;

        updateMWLSPS(ctx.getSpsStatus(), mwlItem);
        ctx.setEventActionCode(AuditMessages.EventActionCode.Update);
        ctx.setPatient(mwlItem.getPatient());
    }

    public int updateMatchingSPS(SPSStatus spsStatus, Attributes queryKeys, QueryParam queryParam, int mwlFetchSize) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<MWLItem> q = cb.createQuery(MWLItem.class);
        QueryBuilder builder = new QueryBuilder(cb);
        Root<MWLItem> mwlItem = q.from(MWLItem.class);
        Join<MWLItem, Patient> patient = mwlItem.join(MWLItem_.patient);
        IDWithIssuer idWithIssuer = IDWithIssuer.pidOf(queryKeys);
        List<Predicate> predicates = builder.mwlItemPredicates(
                                                q,
                                                patient,
                                                mwlItem,
                                                idWithIssuer != null ? new IDWithIssuer[]{ idWithIssuer } : IDWithIssuer.EMPTY,
                                                null,
                                                queryKeys,
                                                queryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));

        TypedQuery<MWLItem> query = em.createQuery(q);
        if (mwlFetchSize > 0)
            query.setMaxResults(mwlFetchSize);

        List<MWLItem> mwlItems = query.getResultList();
        mwlItems.forEach(mwl -> updateMWLSPS(spsStatus, mwl));
        return mwlItems.size();
    }

    private void updateMWLSPS(SPSStatus spsStatus, MWLItem mwl) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        Attributes attrs = mwl.getAttributes();
        Attributes sps = attrs.getNestedDataset(Tag.ScheduledProcedureStepSequence);
        attrs.remove(Tag.ScheduledProcedureStepSequence);
        sps.setString(Tag.ScheduledProcedureStepStatus, VR.CS, spsStatus.name());
        attrs.newSequence(Tag.ScheduledProcedureStepSequence, 1).add(sps);
        mwl.setAttributes(attrs, arcDev.getAttributeFilter(Entity.MWL), arcDev.getFuzzyStr());
    }

    private boolean updateStudySeriesAttributesFromMWL(ProcedureContext ctx) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        Attributes mwlAttr = ctx.getAttributes();
        List<Series> seriesList = em.createNamedQuery(Series.FIND_SERIES_OF_STUDY_BY_STUDY_IUID_EAGER, Series.class)
                                    .setParameter(1, ctx.getStudyInstanceUIDInstRefs() == null
                                                                ? ctx.getStudyInstanceUID()
                                                                : ctx.getStudyInstanceUIDInstRefs())
                                    .getResultList();
        if (seriesList.isEmpty())
            return false;

        Date now = new Date();
        Study study = seriesList.get(0).getStudy();
        Attributes studyAttr = study.getAttributes();
        Attributes modified = new Attributes();
        AttributeFilter studyFilter = arcDev.getAttributeFilter(Entity.Study);
        LOG.info("Update attributes of Study[uid={}], triggered by {}", study.getStudyInstanceUID(), ctx);
        Attributes.unifyCharacterSets(studyAttr, mwlAttr);
        if (studyAttr.updateSelected(Attributes.UpdatePolicy.MERGE,
                mwlAttr, modified, studyFilter.getSelection())) {
            modified.setString(Tag.StudyDescription, VR.LO, studyAttr.getString(Tag.StudyDescription));
            modified.setString(Tag.StudyID, VR.SH, studyAttr.getString(Tag.StudyID));
            studyAttr.setString(Tag.StudyDescription, VR.LO, mwlAttr.getString(Tag.RequestedProcedureDescription));
            studyAttr.setString(Tag.StudyID, VR.SH, mwlAttr.getString(Tag.RequestedProcedureID));
            if (ctx.getStudyInstanceUIDInstRefs() != null) {
                studyAttr.setString(Tag.StudyInstanceUID, VR.UI, ctx.getStudyInstanceUIDInstRefs());
                modified.remove(Tag.StudyInstanceUID);
            }
            study.setAttributes(recordAttributeModification(ctx)
                    ? studyAttr.addOriginalAttributes(
                        null,
                        now,
                        Attributes.CORRECT,
                        device.getDeviceName(),
                        modified)
                    : studyAttr,
                    studyFilter, true, arcDev.getFuzzyStr());
            em.createNamedQuery(Series.SCHEDULE_METADATA_UPDATE_FOR_STUDY)
                    .setParameter(1, study)
                    .executeUpdate();
        }
        Set<String> sourceSeriesIUIDs = ctx.getSourceSeriesInstanceUIDs();
        for (Series series : seriesList)
            if (sourceSeriesIUIDs == null || sourceSeriesIUIDs.contains(series.getSeriesInstanceUID()))
                updateSeriesAttributes(series, mwlAttr,
                        arcDev.getAttributeFilter(Entity.Series), arcDev.getFuzzyStr(), now, ctx);

        LOG.info("Study and series attributes updated successfully for study : "
                + (ctx.getStudyInstanceUIDInstRefs() == null
                    ? ctx.getStudyInstanceUID()
                    : ctx.getStudyInstanceUIDInstRefs()));
        return true;
    }

    private void updateSeriesAttributes(Series series, Attributes mwlAttr,
                                        AttributeFilter filter, FuzzyStr fuzzyStr, Date now, ProcedureContext ctx) {
        Attributes seriesAttr = series.getAttributes();
        Attributes.unifyCharacterSets(seriesAttr, mwlAttr);
        Attributes modified = new Attributes(seriesAttr, Tag.RequestAttributesSequence);
        if (modified.containsValue(Tag.RequestAttributesSequence) && recordAttributeModification(ctx))
            seriesAttr.addOriginalAttributes(
                    null,
                    now,
                    Attributes.CORRECT,
                    device.getDeviceName(),
                    modified);
        Sequence rqAttrsSeq = seriesAttr.newSequence(Tag.RequestAttributesSequence, 1);
        Sequence spsSeq = mwlAttr.getSequence(Tag.ScheduledProcedureStepSequence);
        Collection<SeriesRequestAttributes> requestAttributes = series.getRequestAttributes();
        requestAttributes.clear();
        for (Attributes spsItem : spsSeq) {
            Attributes rqAttrsItem = MWLItem.addItemToRequestAttributesSequence(rqAttrsSeq, mwlAttr, spsItem);
            SeriesRequestAttributes request = new SeriesRequestAttributes(rqAttrsItem, fuzzyStr);
            requestAttributes.add(request);
        }
        series.setAttributes(seriesAttr, filter, true, fuzzyStr);
        em.createNamedQuery(Series.SCHEDULE_METADATA_UPDATE_FOR_SERIES)
                .setParameter(1, series.getPk())
                .executeUpdate();
    }

    public void updateStudySeriesAttributes(ProcedureContext ctx) {
        boolean result = false;
        try {
            result = updateStudySeriesAttributesFromMWL(ctx);
        } catch (Exception e) {
            ctx.setException(e);
        } finally {
            if (result || ctx.getException() != null)
                ctx.setEventActionCode(AuditMessages.EventActionCode.Update);
        }
    }

    private boolean recordAttributeModification(ProcedureContext ctx) {
        return ctx.getArchiveAEExtension() != null
                ? ctx.getArchiveAEExtension().recordAttributeModification()
                : ctx.getArchiveHL7AppExtension() != null
                ? ctx.getArchiveHL7AppExtension().recordAttributeModification()
                : device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class).isRecordAttributeModification();
    }

    public List<MWLItem.IDs> spsOfPatientWithStatus(Patient patient, SPSStatus status) {
        return em.createNamedQuery(MWLItem.IDS_BY_PATIENT_AND_STATUS, MWLItem.IDs.class)
                .setParameter(1, patient)
                .setParameter(2, status)
                .getResultList();
    }

    public Set<MWLItem.IDs> findMWLItemIDs(ApplicationEntity ae, Attributes keys, boolean fuzzymatching) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        Root<MWLItem> mwlItem = q.from(MWLItem.class);
        Join<MWLItem, Patient> patient = mwlItem.join(MWLItem_.patient);
        IDWithIssuer idWithIssuer = IDWithIssuer.pidOf(keys);
        QueryParam queryParam = new QueryParam(ae);
        queryParam.setCombinedDatetimeMatching(true);
        queryParam.setFuzzySemanticMatching(fuzzymatching);
        List<Predicate> predicates = new QueryBuilder(cb).mwlItemPredicates(q, patient, mwlItem,
                idWithIssuer != null ? new IDWithIssuer[]{ idWithIssuer } : IDWithIssuer.EMPTY,
                null, keys, queryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));
        Path<String> studyIUID = mwlItem.get(MWLItem_.studyInstanceUID);
        Path<String> spsID = mwlItem.get(MWLItem_.scheduledProcedureStepID);
        try (Stream<Tuple> resultStream = em.createQuery(q.multiselect(studyIUID, spsID)).getResultStream()) {
            return resultStream.map(t -> new MWLItem.IDs(t.get(spsID), t.get(studyIUID))).collect(Collectors.toSet());
        }
    }

    public void createOrUpdateMWLItem(ProcedureContext ctx, boolean simulate) {
        Attributes attrs = ctx.getAttributes();
        PatientMgtContext patMgtCtx = patientService.createPatientMgtContextWEB(ctx.getHttpRequest());
        patMgtCtx.setAttributes(attrs);
        patMgtCtx.setPatientIDs(IDWithIssuer.pidsOf(attrs));
        patMgtCtx.setLocalAET(ctx.getLocalAET());
        patMgtCtx.setSourceMwlScp(ctx.getSourceMwlScp());
        ctx.setPatient(patientService.findPatient(patMgtCtx));
        ArchiveDeviceExtension arcdev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
        MWLItem mwlItem = findMWLItem(ctx);
        if (mwlItem != null) {
            if (ctx.getPatient() == null || ctx.getPatient().getPk() != mwlItem.getPatient().getPk())
                throw new PatientMismatchException("Patient IDs: " + patMgtCtx.getPatientIDs() + " does not match " +
                        mwlItem.getPatient() + " in previous " + mwlItem);

            int[] mwlTags = arcdev.getAttributeFilter(Entity.MWL).getSelection(false);
            Attributes prevMWLAttrs = new Attributes(mwlItem.getAttributes(), mwlTags);
            if (!prevMWLAttrs.equals(new Attributes(attrs, mwlTags))) {
                if (!simulate) {
                    updateMWL(ctx, mwlItem);
                }
            }
        } else {
            if (!simulate) {
                if (ctx.getPatient() == null) {
                    ctx.setPatient(patientService.createPatient(patMgtCtx));
                }
                createMWL(ctx);
            }
        }
    }
}
