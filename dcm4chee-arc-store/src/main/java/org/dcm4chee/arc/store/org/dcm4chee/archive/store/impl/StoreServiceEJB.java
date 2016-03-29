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

package org.dcm4chee.arc.store.org.dcm4chee.archive.store.impl;

import org.dcm4che3.data.*;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.soundex.FuzzyStr;
import org.dcm4che3.util.TagUtils;
import org.dcm4chee.arc.code.CodeService;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.issuer.IssuerService;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.patient.PatientService;
import org.dcm4chee.arc.storage.Storage;
import org.dcm4chee.arc.storage.WriteContext;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2015
 */
@Stateless
public class StoreServiceEJB {

    private static final Logger LOG = LoggerFactory.getLogger(StoreServiceImpl.class);
    private static final String IGNORE = "{}: Ignore received Instance[studyUID={},seriesUID={},objectUID={}]";
    private static final String IGNORE_FROM_DIFFERENT_SOURCE = IGNORE + " from different source";
    private static final String IGNORE_PREVIOUS_REJECTED = IGNORE + " previous rejected by {}";
    private static final String IGNORE_WITH_EQUAL_DIGEST = IGNORE + " with equal digest";
    private static final String REVOKE_REJECTION =
            "{}: Revoke rejection of Instance[studyUID={},seriesUID={},objectUID={}] by {}";
    private static final int DUPLICATE_REJECTION_NOTE = 0xA770;
    private static final int SUBSEQUENT_OCCURENCE_OF_REJECTED_OBJECT = 0xA771;;
    private static final int REJECTION_FAILED_NO_SUCH_INSTANCE = 0xA772;
    private static final int REJECTION_FAILED_CLASS_INSTANCE_CONFLICT  = 0xA773;
    private static final int REJECTION_FAILED_ALREADY_REJECTED  = 0xA774;

    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    @Inject
    private CodeService codeService;

    @Inject
    private IssuerService issuerService;

    @Inject
    private PatientService patientService;

    public UpdateDBResult updateDB(StoreContext ctx, UpdateDBResult result) throws DicomServiceException {
        StoreSession session = ctx.getStoreSession();
        ArchiveAEExtension arcAE = session.getArchiveAEExtension();
        ArchiveDeviceExtension arcDev = arcAE.getArchiveDeviceExtension();
        Instance prevInstance = findPreviousInstance(ctx);
        if (prevInstance != null) {
            result.setPreviousInstance(prevInstance);
            LOG.info("{}: Found previous received {}", session, prevInstance);
            if (prevInstance.getSopClassUID().equals(UID.KeyObjectSelectionDocumentStorage)
                    && getRejectionNote(arcDev, prevInstance.getConceptNameCode()) != null)
                throw new DicomServiceException(DUPLICATE_REJECTION_NOTE,
                        "Rejection Note [uid=" + prevInstance.getSopInstanceUID() + "] already received");
            RejectionNote rjNote = getRejectionNote(arcDev, prevInstance.getRejectionNoteCode());
            if (rjNote != null) {
                RejectionNote.AcceptPreviousRejectedInstance accept = rjNote.getAcceptPreviousRejectedInstance();
                switch(accept) {
                    case IGNORE:
                        logInfo(IGNORE_PREVIOUS_REJECTED, ctx, rjNote.getRejectionNoteCode());
                        return result;
                    case REJECT:
                        throw new DicomServiceException(SUBSEQUENT_OCCURENCE_OF_REJECTED_OBJECT,
                                "Subsequent occurrence of rejected Object [uid=" + prevInstance.getSopInstanceUID()
                                        + ", rejection=" + rjNote.getRejectionNoteCode() + "]");
                    case RESTORE:
                        break;
                }
            } else {
                switch (arcAE.overwritePolicy()) {
                    case NEVER:
                        logInfo(IGNORE, ctx);
                        return result;
                    case SAME_SOURCE:
                    case SAME_SOURCE_AND_SERIES:
                        if (!isSameSource(ctx, prevInstance)) {
                            logInfo(IGNORE_FROM_DIFFERENT_SOURCE, ctx);
                            return result;
                        }
                }
            }
            List<Location> locations = findLocations(prevInstance);
            if (containsWithEqualDigest(locations, ctx.getWriteContext().getDigest())) {
                logInfo(IGNORE_WITH_EQUAL_DIGEST, ctx);
                if (rjNote != null) {
                    prevInstance.setRejectionNoteCode(null);
                    deleteQueryAttributes(prevInstance);
                    logInfo(REVOKE_REJECTION, ctx, rjNote.getRejectionNoteCode());
                }
                return result;
            }
            LOG.info("{}: Replace previous received {}", session, prevInstance);
            deleteLocations(locations);
            deleteInstance(prevInstance, ctx);
        }

        CodeEntity conceptNameCode = findOrCreateCode(ctx.getAttributes(), Tag.ConceptNameCodeSequence);
        if (conceptNameCode != null && ctx.getSopClassUID().equals(UID.KeyObjectSelectionDocumentStorage)) {
            RejectionNote rjNote = arcDev.getRejectionNote(conceptNameCode.getCode());
            if (rjNote != null) {
                result.setRejectionNote(rjNote);
                boolean revokeRejection = rjNote.isRevokeRejection();
                rejectInstances(ctx, rjNote, revokeRejection ? null : conceptNameCode);
                if (revokeRejection)
                    return result;
            }
        }

        Instance instance = createInstance(ctx, conceptNameCode, result);
        Location location = createLocation(ctx, instance);
        deleteQueryAttributes(instance);
        result.setLocation(location);
        return result;
    }

    private void rejectInstances(StoreContext ctx, RejectionNote rjNote, CodeEntity rejectionCode)
            throws DicomServiceException {
        for (Attributes studyRef : ctx.getAttributes().getSequence(Tag.CurrentRequestedProcedureEvidenceSequence)) {
            Instance inst = null;
            String studyUID = studyRef.getString(Tag.StudyInstanceUID);
            for (Attributes seriesRef : studyRef.getSequence(Tag.ReferencedSeriesSequence)) {
                String seriesUID = seriesRef.getString(Tag.SeriesInstanceUID);
                for (Attributes sopRef : seriesRef.getSequence(Tag.ReferencedSOPSequence)) {
                    String classUID = sopRef.getString(Tag.ReferencedSOPClassUID);
                    String objectUID = sopRef.getString(Tag.ReferencedSOPInstanceUID);
                    inst = rejectInstance(ctx, studyUID, seriesUID, objectUID, classUID, rjNote, rejectionCode);
                }
                if (inst != null)
                    deleteSeriesQueryAttributes(inst.getSeries());
            }
            if (inst != null)
                deleteStudyQueryAttributes(inst.getSeries().getStudy());
        }
    }

    private Instance rejectInstance(StoreContext ctx, String studyUID, String seriesUID,
                                    String objectUID, String classUID, RejectionNote rjNote,
                                    CodeEntity rejectionCode) throws DicomServiceException {
        Instance inst = findInstance(studyUID, seriesUID, objectUID);
        if (inst == null)
            throw new DicomServiceException(REJECTION_FAILED_NO_SUCH_INSTANCE,
                    "Failed to reject Instance[uid=" + objectUID + "] - no such Instance");
        if (!inst.getSopClassUID().equals(classUID))
            throw new DicomServiceException(REJECTION_FAILED_CLASS_INSTANCE_CONFLICT,
                    "Failed to reject Instance[uid=" + objectUID + "] - class-instance conflict");
        CodeEntity prevRjNoteCode = inst.getRejectionNoteCode();
        if (prevRjNoteCode != null) {
            if (rejectionCode != null && rejectionCode.getPk() == prevRjNoteCode.getPk())
                return inst;
            if (!rjNote.canOverwritePreviousRejection(prevRjNoteCode.getCode()))
                throw new DicomServiceException(REJECTION_FAILED_ALREADY_REJECTED,
                        "Failed to reject Instance[uid=" + objectUID + "] - already rejected");
        }
        inst.setRejectionNoteCode(rejectionCode);
        if (rejectionCode != null)
            LOG.info("{}: Reject {} by {}", ctx.getStoreSession(), inst, rejectionCode.getCode());
        else if (prevRjNoteCode != null)
            LOG.info("{}: Revoke Rejection of {} by {}", ctx.getStoreSession(), inst, prevRjNoteCode.getCode());
        return inst;
    }

    private RejectionNote getRejectionNote(ArchiveDeviceExtension arcDev, CodeEntity codeEntry) {
        return codeEntry != null ? arcDev.getRejectionNote(codeEntry.getCode()) : null;
    }

    private void logInfo(String format, StoreContext ctx) {
        LOG.info(format, ctx.getStoreSession(),
                ctx.getStudyInstanceUID(),
                ctx.getSeriesInstanceUID(),
                ctx.getSopInstanceUID());
    }

    private void logInfo(String format, StoreContext ctx, Object arg) {
        LOG.info(format, ctx.getStoreSession(),
                ctx.getStudyInstanceUID(),
                ctx.getSeriesInstanceUID(),
                ctx.getSopInstanceUID(),
                arg);
    }

    private void deleteInstance(Instance instance, StoreContext ctx) {
        Series series = instance.getSeries();
        Study study = series.getStudy();
        em.remove(instance);
        em.flush(); // to avoid ERROR: duplicate key value violates unique constraint on re-insert
        boolean sameStudy = ctx.getStudyInstanceUID().equals(study.getStudyInstanceUID());
        boolean sameSeries = sameStudy && ctx.getSeriesInstanceUID().equals(series.getSeriesInstanceUID());
        if (!sameSeries) {
            deleteQueryAttributes(instance);
            if (deleteSeriesIfEmpty(series, ctx) && !sameStudy)
                deleteStudyIfEmpty(study, ctx);
        }
    }

    private boolean deleteStudyIfEmpty(Study study, StoreContext ctx) {
        if (em.createNamedQuery(Series.COUNT_SERIES_OF_STUDY, Long.class)
                .setParameter(1, study)
                .getSingleResult() != 0L)
            return false;

        LOG.info("{}: Delete {}", ctx.getStoreSession(), study);
        em.remove(study);
        return true;
    }

    private boolean deleteSeriesIfEmpty(Series series, StoreContext ctx) {
        if (em.createNamedQuery(Instance.COUNT_INSTANCES_OF_SERIES, Long.class)
                .setParameter(1, series)
                .getSingleResult() != 0L)
            return false;

        LOG.info("{}: Delete {}", ctx.getStoreSession(), series);
        em.remove(series);
        return true;
    }

    private void deleteLocations(List<Location> locations) {
        for (Location location : locations) {
            location.setInstance(null);
            location.setStatus(Location.Status.TO_DELETE);
        }
    }

    private List<Location> findLocations(Instance inst) {
        return em.createNamedQuery(Location.FIND_BY_INSTANCE, Location.class)
                .setParameter(1, inst)
                .getResultList();
    }

    private boolean containsWithEqualDigest(List<Location> locations, byte[] digest) {
        if (digest == null)
            return false;

        for (Location location : locations) {
            byte[] digest2 = location.getDigest();
            if (digest2 != null && Arrays.equals(digest, digest2))
                return true;
        }
        return false;
    }

    private boolean isSameSource(StoreContext ctx, Instance prevInstance) {
        String sourceAET = ctx.getStoreSession().getCallingAET();
        String prevSourceAET = prevInstance.getSeries().getSourceAET();
        return sourceAET != null && sourceAET.equals(prevSourceAET);
    }

    private void deleteQueryAttributes(Instance instance) {
        Series series = instance.getSeries();
        Study study = series.getStudy();
        deleteSeriesQueryAttributes(series);
        deleteStudyQueryAttributes(study);
    }

    private int deleteStudyQueryAttributes(Study study) {
        return em.createNamedQuery(StudyQueryAttributes.DELETE_FOR_STUDY).setParameter(1, study).executeUpdate();
    }

    private int deleteSeriesQueryAttributes(Series series) {
        return em.createNamedQuery(SeriesQueryAttributes.DELETE_FOR_SERIES).setParameter(1, series).executeUpdate();
    }

    private Instance createInstance(StoreContext ctx, CodeEntity conceptNameCode, UpdateDBResult result) {
        Series series = findSeries(ctx);
        if (series == null) {
            Study study = findStudy(ctx);
            if (study == null) {
                IDWithIssuer pid = IDWithIssuer.pidOf(ctx.getAttributes());
                PatientMgtContext patMgtCtx = patientService.createPatientMgtContextDICOM(
                        ctx.getStoreSession().getAssociation());
                patMgtCtx.setAttributes(ctx.getAttributes());
                Patient pat = patientService.findPatient(patMgtCtx);
                if (pat == null) {
                    pat = patientService.createPatient(patMgtCtx);
                    result.setCreatedPatient(pat);
                }
                study = createStudy(ctx, pat);
                result.setCreatedStudy(study);
            }
            series = createSeries(ctx, study);
        }
        return createInstance(ctx, series, conceptNameCode);
    }


    private Study findStudy(StoreContext ctx) {
        StoreSession storeSession = ctx.getStoreSession();
        Study study = storeSession.getCachedStudy(ctx.getStudyInstanceUID());
        if (study != null)
            return study;
        try {
            return em.createNamedQuery(Study.FIND_BY_STUDY_IUID_EAGER, Study.class)
                    .setParameter(1, ctx.getStudyInstanceUID())
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    private Series findSeries(StoreContext ctx) {
        StoreSession storeSession = ctx.getStoreSession();
        Series series = storeSession.getCachedSeries(ctx.getStudyInstanceUID(), ctx.getSeriesInstanceUID());
        if (series != null)
            return series;
        try {
            return em.createNamedQuery(Series.FIND_BY_SERIES_IUID_EAGER, Series.class)
                    .setParameter(1, ctx.getStudyInstanceUID())
                    .setParameter(2, ctx.getSeriesInstanceUID())
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    private Instance findPreviousInstance(StoreContext ctx) {
        switch (ctx.getStoreSession().getArchiveAEExtension().overwritePolicy()) {
            case ALWAYS:
            case SAME_SOURCE:
                return findInstance(ctx.getSopInstanceUID());
            default:
                return findInstance(
                        ctx.getStudyInstanceUID(),
                        ctx.getSeriesInstanceUID(),
                        ctx.getSopInstanceUID());
        }
    }

    private Instance findInstance(String objectUID) {
        try {
            return em.createNamedQuery(Instance.FIND_BY_SOP_IUID, Instance.class)
                    .setParameter(1, objectUID)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    private Instance findInstance(String studyUID, String seriesUID, String objectUID) {
        try {
            return em.createNamedQuery(Instance.FIND_BY_STUDY_SERIES_SOP_IUID, Instance.class)
                    .setParameter(1, studyUID)
                    .setParameter(2, seriesUID)
                    .setParameter(3, objectUID)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    private Study createStudy(StoreContext ctx, Patient patient) {
        StoreSession session = ctx.getStoreSession();
        ArchiveAEExtension arcAE = session.getArchiveAEExtension();
        ArchiveDeviceExtension arcDev = arcAE.getArchiveDeviceExtension();
        FuzzyStr fuzzyStr = arcDev.getFuzzyStr();
        Attributes attrs = ctx.getAttributes();
        Study study = new Study();
        study.setAccessControlID(arcAE.getStoreAccessControlID());
        study.setAttributes(attrs, arcDev.getAttributeFilter(Entity.Study), fuzzyStr);
        study.setIssuerOfAccessionNumber(findOrCreateIssuer(attrs, Tag.IssuerOfAccessionNumberSequence));
        setCodes(study.getProcedureCodes(), attrs, Tag.ProcedureCodeSequence);
        study.setPatient(patient);
        em.persist(study);
        LOG.info("{}: Create {}", ctx.getStoreSession(), study);
        return study;
    }

    private Series createSeries(StoreContext ctx, Study study) {
        StoreSession session = ctx.getStoreSession();
        ArchiveDeviceExtension arcDev = session.getArchiveAEExtension().getArchiveDeviceExtension();
        FuzzyStr fuzzyStr = arcDev.getFuzzyStr();
        Attributes attrs = ctx.getAttributes();
        Series series = new Series();
        series.setAttributes(attrs, arcDev.getAttributeFilter(Entity.Series), fuzzyStr);
        series.setInstitutionCode(findOrCreateCode(attrs, Tag.InstitutionCodeSequence));
        setRequestAttributes(series, attrs, fuzzyStr);
        series.setSourceAET(session.getCallingAET());
        series.setStudy(study);
        em.persist(series);
        LOG.info("{}: Create {}", ctx.getStoreSession(), series);
        return series;
    }

    private Instance createInstance(StoreContext ctx, Series series, CodeEntity conceptNameCode) {
        StoreSession session = ctx.getStoreSession();
        ArchiveDeviceExtension arcDev = session.getArchiveAEExtension().getArchiveDeviceExtension();
        FuzzyStr fuzzyStr = arcDev.getFuzzyStr();
        Attributes attrs = ctx.getAttributes();
        Instance instance = new Instance();
        instance.setAttributes(attrs, arcDev.getAttributeFilter(Entity.Instance), fuzzyStr);
        setVerifyingObservers(instance, attrs, fuzzyStr);
        instance.setConceptNameCode(conceptNameCode);
        setContentItems(instance, attrs);

        WriteContext storageContext = ctx.getWriteContext();
        Storage storage = storageContext.getStorage();
        StorageDescriptor descriptor = storage.getStorageDescriptor();
        String[] retrieveAETs = descriptor.getRetrieveAETitles();
        Availability availability = descriptor.getInstanceAvailability();
        instance.setRetrieveAETs(
                retrieveAETs.length > 0
                        ? retrieveAETs
                        : new String[] { session.getLocalApplicationEntity().getAETitle() });
        instance.setAvailability(availability != null ? availability : Availability.ONLINE);

        instance.setSeries(series);
        em.persist(instance);
        LOG.info("{}: Create {}", ctx.getStoreSession(), instance);
        return instance;
    }

    private Location createLocation(StoreContext ctx, Instance instance) {
        WriteContext writeContext = ctx.getWriteContext();
        Storage storage = writeContext.getStorage();
        StorageDescriptor descriptor = storage.getStorageDescriptor();
        Location location = new Location.Builder()
                .storageID(descriptor.getStorageID())
                .storagePath(writeContext.getStoragePath())
                .transferSyntaxUID(ctx.getStoreTranferSyntax())
                .size(writeContext.getSize())
                .digest(writeContext.getDigest())
                .build();
        location.setInstance(instance);
        em.persist(location);
        return location;
    }

    private void setRequestAttributes(Series series, Attributes attrs, FuzzyStr fuzzyStr) {
        Sequence seq = attrs.getSequence(Tag.RequestAttributesSequence);
        Collection<SeriesRequestAttributes> requestAttributes = series.getRequestAttributes();
        requestAttributes.clear();
        if (seq != null)
            for (Attributes item : seq) {
                SeriesRequestAttributes request = new SeriesRequestAttributes(
                        item,
                        findOrCreateIssuer(item, Tag.IssuerOfAccessionNumberSequence),
                        fuzzyStr);
                requestAttributes.add(request);
            }
    }

    private void setVerifyingObservers(Instance instance, Attributes attrs, FuzzyStr fuzzyStr) {
        Collection<VerifyingObserver> list = instance.getVerifyingObservers();
        list.clear();
        Sequence seq = attrs.getSequence(Tag.VerifyingObserverSequence);
        if (seq != null)
            for (Attributes item : seq)
                list.add(new VerifyingObserver(item, fuzzyStr));
    }

    private void setContentItems(Instance inst, Attributes attrs) {
        Collection<ContentItem> contentItems = inst.getContentItems();
        contentItems.clear();
        Sequence seq = attrs.getSequence(Tag.ContentSequence);
        if (seq != null)
            for (Attributes item : seq) {
                String type = item.getString(Tag.ValueType);
                if ("CODE".equals(type)) {
                    contentItems.add(new ContentItem(
                            item.getString(Tag.RelationshipType).toUpperCase(),
                            findOrCreateCode(item, Tag.ConceptNameCodeSequence),
                            findOrCreateCode(item, Tag.ConceptCodeSequence)));
                } else if ("TEXT".equals(type)) {
                    String text = item.getString(Tag.TextValue, "*");
                    if (text.length() <= ContentItem.MAX_TEXT_LENGTH) {
                        contentItems.add(new ContentItem(
                                item.getString(Tag.RelationshipType).toUpperCase(),
                                findOrCreateCode(item, Tag.ConceptNameCodeSequence),
                                text));
                    }
                }
            }
    }

    private IssuerEntity findOrCreateIssuer(Attributes attrs, int tag) {
        Attributes item = attrs.getNestedDataset(tag);
        return item != null ? issuerService.findOrCreate(new Issuer(item)) : null;
    }

    private CodeEntity findOrCreateCode(Attributes attrs, int seqTag) {
        Attributes item = attrs.getNestedDataset(seqTag);
        if (item != null)
            try {
                return codeService.findOrCreate(new Code(item));
            } catch (Exception e) {
                LOG.info("Illegal code item in Sequence {}:\n{}", TagUtils.toString(seqTag), item);
            }
        return null;
    }

    private void setCodes(Collection<CodeEntity> codes, Attributes attrs, int seqTag) {
        Sequence seq = attrs.getSequence(seqTag);
        codes.clear();
        if (seq != null)
            for (Attributes item : seq) {
                try {
                    codes.add(codeService.findOrCreate(new Code(item)));
                } catch (Exception e) {
                    LOG.info("Illegal code item in Sequence {}:\n{}", TagUtils.toString(seqTag), item);
                }
            }
    }

    public void checkDuplicatePatientCreated(StoreContext ctx, UpdateDBResult result) {
        IDWithIssuer pid = IDWithIssuer.pidOf(ctx.getAttributes());
        if (pid == null)
            return;

        List<Patient> patients = patientService.findPatients(pid);
        switch (patients.size()) {
            case 1:
                return;
            case 2:
                break;
            default:
                LOG.warn("{}: Multiple({}) Patients with ID {}", ctx.getStoreSession(), patients.size(), pid);
                return;
        }

        int index = patients.get(0).getPk() == result.getCreatedPatient().getPk() ? 0 : 1;
        Patient createdPatient = patients.get(index);
        Patient otherPatient = patients.get(1-index);
        if (otherPatient.getMergedWith() != null) {
            LOG.warn("{}: Keep duplicate created {} because existing {} is circular merged",
                    ctx.getStoreSession(), createdPatient, otherPatient, pid);
            return;
        }
        LOG.info("{}: Delete duplicate created {}", ctx.getStoreSession(), createdPatient);
        em.merge(result.getCreatedStudy()).setPatient(otherPatient);
        em.remove(createdPatient);
        result.setCreatedPatient(null);
    }
}
