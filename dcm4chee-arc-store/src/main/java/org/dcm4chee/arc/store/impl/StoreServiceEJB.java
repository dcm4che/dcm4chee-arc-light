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

package org.dcm4chee.arc.store.impl;

import org.dcm4che3.data.*;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.soundex.FuzzyStr;
import org.dcm4che3.util.AttributesFormat;
import org.dcm4che3.util.StreamUtils;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.util.TagUtils;
import org.dcm4chee.arc.StorePermission;
import org.dcm4chee.arc.StorePermissionCache;
import org.dcm4chee.arc.code.CodeCache;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.id.IDService;
import org.dcm4chee.arc.issuer.IssuerService;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.patient.PatientService;
import org.dcm4chee.arc.storage.Storage;
import org.dcm4chee.arc.storage.WriteContext;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreService;
import org.dcm4chee.arc.store.StoreSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
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

    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    @Inject
    private CodeCache codeCache;

    @Inject
    private IssuerService issuerService;

    @Inject
    private PatientService patientService;

    @Inject
    private StorePermissionCache storePermissionCache;

    @Inject
    private IDService idService;

    public UpdateDBResult updateDB(StoreContext ctx, UpdateDBResult result)
            throws DicomServiceException {
        StoreSession session = ctx.getStoreSession();
        ArchiveAEExtension arcAE = session.getArchiveAEExtension();
        ArchiveDeviceExtension arcDev = arcAE.getArchiveDeviceExtension();
        Instance prevInstance = findPreviousInstance(ctx);
        if (prevInstance != null) {
            Collection<Location> locations = prevInstance.getLocations();
            result.setPreviousInstance(prevInstance);
            LOG.info("{}: Found previous received {}", session, prevInstance);
            if (prevInstance.getSopClassUID().equals(UID.KeyObjectSelectionDocumentStorage)
                    && getRejectionNote(arcDev, prevInstance.getConceptNameCode()) != null)
                throw new DicomServiceException(StoreService.DUPLICATE_REJECTION_NOTE,
                        "Rejection Note [uid=" + prevInstance.getSopInstanceUID() + "] already received");
            RejectionNote rjNote = getRejectionNote(arcDev, prevInstance.getRejectionNoteCode());
            if (rjNote != null) {
                RejectionNote.AcceptPreviousRejectedInstance accept = rjNote.getAcceptPreviousRejectedInstance();
                switch(accept) {
                    case IGNORE:
                        logInfo(IGNORE_PREVIOUS_REJECTED, ctx, rjNote.getRejectionNoteCode());
                        return result;
                    case REJECT:
                        throw new DicomServiceException(StoreService.SUBSEQUENT_OCCURENCE_OF_REJECTED_OBJECT,
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
            if (containsWithEqualDigest(locations, ctx.getWriteContext(Location.ObjectType.DICOM_FILE).getDigest())) {
                logInfo(IGNORE_WITH_EQUAL_DIGEST, ctx);
                if (rjNote != null) {
                    prevInstance.setRejectionNoteCode(null);
                    result.setStoredInstance(prevInstance);
                    deleteQueryAttributes(prevInstance);
                    prevInstance.getSeries().getStudy().clearExternalRetrieveAETs();
                    logInfo(REVOKE_REJECTION, ctx, rjNote.getRejectionNoteCode());
                }
                return result;
            }
            LOG.info("{}: Replace previous received {}", session, prevInstance);
            deleteInstance(prevInstance, ctx);
        }
        RejectionNote rjNote = null;
        CodeEntity conceptNameCode = findOrCreateCode(ctx.getAttributes(), Tag.ConceptNameCodeSequence);
        if (conceptNameCode != null && ctx.getSopClassUID().equals(UID.KeyObjectSelectionDocumentStorage)) {
            rjNote = arcDev.getRejectionNote(conceptNameCode.getCode());
            if (rjNote != null) {
                result.setRejectionNote(rjNote);
                AllowRejectionForDataRetentionPolicyExpired policy =
                        arcAE.allowRejectionForDataRetentionPolicyExpired();
                if (rjNote.getRejectionNoteType() == RejectionNote.Type.DATA_RETENTION_POLICY_EXPIRED
                        && policy == AllowRejectionForDataRetentionPolicyExpired.NEVER) {
                    throw new DicomServiceException(StoreService.REJECTION_FOR_RETENTION_POLICY_EXPIRED_NOT_AUTHORIZED,
                            "Rejection for Retentation Policy Expired not authorized");
                }
                rejectInstances(ctx, rjNote, conceptNameCode, policy);
                if (rjNote.isRevokeRejection())
                    return result;
            }
        }
        Instance instance = createInstance(ctx, conceptNameCode, result);
        if (ctx.getLocations().isEmpty())
            createLocations(ctx, instance, result);
        else
            copyLocations(ctx, instance, result);

        result.setStoredInstance(instance);
        deleteQueryAttributes(instance);
        if(rjNote == null || !rjNote.isDataRetentionPolicyExpired())
            instance.getSeries().getStudy().clearExternalRetrieveAETs();
        return result;
    }

    private void rejectInstances(StoreContext ctx, RejectionNote rjNote, CodeEntity rejectionCode,
                                 AllowRejectionForDataRetentionPolicyExpired policy)
            throws DicomServiceException {
        for (Attributes studyRef : ctx.getAttributes().getSequence(Tag.CurrentRequestedProcedureEvidenceSequence)) {
            Series series = null;
            String studyUID = studyRef.getString(Tag.StudyInstanceUID);
            for (Attributes seriesRef : studyRef.getSequence(Tag.ReferencedSeriesSequence)) {
                Instance inst = null;
                String seriesUID = seriesRef.getString(Tag.SeriesInstanceUID);
                for (Attributes sopRef : seriesRef.getSequence(Tag.ReferencedSOPSequence)) {
                    String classUID = sopRef.getString(Tag.ReferencedSOPClassUID);
                    String objectUID = sopRef.getString(Tag.ReferencedSOPInstanceUID);
                    inst = rejectInstance(ctx, studyUID, seriesUID, objectUID, classUID, rjNote, rejectionCode);
                }
                if (inst != null) {
                    series = inst.getSeries();
                    if (rjNote.getRejectionNoteType() == RejectionNote.Type.DATA_RETENTION_POLICY_EXPIRED
                            && policy == AllowRejectionForDataRetentionPolicyExpired.STUDY_RETENTION_POLICY)
                        checkExpirationDate(series);
                    RejectionState rejectionState = rjNote.isRevokeRejection()
                            ? hasRejectedInstances(series) ? RejectionState.PARTIAL : RejectionState.NONE
                            : hasNotRejectedInstances(series) ? RejectionState.PARTIAL : RejectionState.COMPLETE;
                    series.setRejectionState(rejectionState);
                    if (rejectionState == RejectionState.COMPLETE)
                        series.setExpirationDate(null);
                    deleteSeriesQueryAttributes(series);
                }
            }
            if (series != null) {
                Study study = series.getStudy();
                if (rjNote.isRevokeRejection()) {
                    if (study.getRejectionState() == RejectionState.COMPLETE)
                        study.getPatient().incrementNumberOfStudies();
                    study.setRejectionState(
                            hasSeriesWithOtherRejectionState(study, RejectionState.NONE)
                                    ? RejectionState.PARTIAL
                                    : RejectionState.NONE);
                } else {
                    if (hasSeriesWithOtherRejectionState(study, RejectionState.COMPLETE))
                        study.setRejectionState(RejectionState.PARTIAL);
                    else {
                        study.setRejectionState(RejectionState.COMPLETE);
                        study.setExpirationDate(null);
                        study.getPatient().decrementNumberOfStudies();
                    }
                }
                deleteStudyQueryAttributes(study);
            }
        }
    }

    private void checkExpirationDate(Series series)
            throws DicomServiceException {
        LocalDate studyExpirationDate = series.getStudy().getExpirationDate();
        if (studyExpirationDate == null)
            return;

        LocalDate seriesExpirationDate = series.getExpirationDate();
        if ((seriesExpirationDate != null ? seriesExpirationDate : studyExpirationDate).isAfter(LocalDate.now())) {
            throw new DicomServiceException(StoreService.RETENTION_PERIOD_OF_STUDY_NOT_YET_EXPIRED,
                    "Retention Period of Study not yet expired");
        }
    }

    private boolean hasRejectedInstances(Series series) {
        return em.createNamedQuery(Instance.COUNT_REJECTED_INSTANCES_OF_SERIES, Long.class)
                .setParameter(1, series)
                .getSingleResult() > 0;
    }

    private boolean hasNotRejectedInstances(Series series) {
        return em.createNamedQuery(Instance.COUNT_NOT_REJECTED_INSTANCES_OF_SERIES, Long.class)
                .setParameter(1, series)
                .getSingleResult() > 0;
    }

    private boolean hasSeriesWithOtherRejectionState(Study study, RejectionState rejectionState) {
        return em.createNamedQuery(Series.COUNT_SERIES_OF_STUDY_WITH_OTHER_REJECTION_STATE, Long.class)
                .setParameter(1, study)
                .setParameter(2, rejectionState)
                .getSingleResult() > 0;
    }

    private Instance rejectInstance(StoreContext ctx, String studyUID, String seriesUID,
                                    String objectUID, String classUID, RejectionNote rjNote,
                                    CodeEntity rejectionCode) throws DicomServiceException {
        Instance inst = findInstance(studyUID, seriesUID, objectUID);
        if (inst == null)
            throw new DicomServiceException(StoreService.REJECTION_FAILED_NO_SUCH_INSTANCE,
                    "Failed to reject Instance[uid=" + objectUID + "] - no such Instance");
        if (!inst.getSopClassUID().equals(classUID))
            throw new DicomServiceException(StoreService.REJECTION_FAILED_CLASS_INSTANCE_CONFLICT,
                    "Failed to reject Instance[uid=" + objectUID + "] - class-instance conflict");
        CodeEntity prevRjNoteCode = inst.getRejectionNoteCode();
        if (prevRjNoteCode != null) {
            if (!rjNote.isRevokeRejection() && rejectionCode.getPk() == prevRjNoteCode.getPk())
                return inst;
            if (!rjNote.canOverwritePreviousRejection(prevRjNoteCode.getCode()))
                throw new DicomServiceException(StoreService.REJECTION_FAILED_ALREADY_REJECTED,
                        "Failed to reject Instance[uid=" + objectUID + "] - already rejected");
        }
        inst.setRejectionNoteCode(rjNote.isRevokeRejection() ? null : rejectionCode);
        if (!rjNote.isRevokeRejection())
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

    private Long countLocationsByMultiRef(Integer multiRef) {
        return em.createNamedQuery(Location.COUNT_BY_MULTI_REF, Long.class)
                .setParameter(1, multiRef).getSingleResult();
    }

    private Long countLocationsByUIDMap(UIDMap uidMap) {
        Long result = em.createNamedQuery(Location.COUNT_BY_UIDMAP, Long.class)
                .setParameter(1, uidMap).getSingleResult();
        return result;
    }

    public Location processLocation(Location location, HashSet<UIDMap> uidMaps) {
        if (location.getMultiReference() != null) {
            UIDMap uidMap = location.getUidMap();
            if (uidMap != null)
                uidMaps.add(uidMap);
            if (countLocationsByMultiRef(location.getMultiReference()) > 1)
                em.remove(location);
            else {
                if (uidMap != null)
                    location.setUidMap(null);
                markToDelete(location);
            }
        } else
            markToDelete(location);
        return location;
    }

    public void removeOrphaned(UIDMap uidMap) {
        if (countLocationsByUIDMap(uidMap) == 0)
            em.remove(uidMap);
    }

    private Location markToDelete(Location location) {
        location.setInstance(null);
        location.setStatus(Location.Status.TO_DELETE);
        return location;
    }

    private void deleteInstance(Instance instance, StoreContext ctx) {
        Collection<Location> locations = instance.getLocations();
        HashSet<UIDMap> uidMaps = new HashSet<>();
        for (Location location : locations) {
            processLocation(location, uidMaps);
        }
        for (UIDMap uidMap : uidMaps)
            removeOrphaned(uidMap);
        locations.clear();
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

    private boolean containsWithEqualDigest(Collection<Location> locations, byte[] digest) {
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

    private Instance createInstance(StoreContext ctx, CodeEntity conceptNameCode, UpdateDBResult result)
            throws DicomServiceException {
        Series series = findSeries(ctx, result);
        if (series == null) {
            Study study = findStudy(ctx, result);
            if (study == null) {
                if (!checkMissingPatientID(ctx))
                    throw new DicomServiceException(StoreService.PATIENT_ID_MISSING_IN_OBJECT,
                            "Storage denied as Patient ID missing in object");

                StoreSession session = ctx.getStoreSession();
                HttpServletRequest httpRequest = session.getHttpRequest();
                Association as = session.getAssociation();
                PatientMgtContext patMgtCtx = as != null ? patientService.createPatientMgtContextWEB(as)
                        : httpRequest != null
                        ? patientService.createPatientMgtContextWEB(httpRequest, session.getLocalApplicationEntity())
                        : patientService.createPatientMgtContextHL7(session.getSocket(), session.getHL7MessageHeader());
                patMgtCtx.setAttributes(ctx.getAttributes());
                Patient pat = patientService.findPatient(patMgtCtx);
                if (!checkStorePermission(ctx, pat))
                    throw new DicomServiceException(Status.NotAuthorized, "Storage denied");

                if (pat == null) {
                    pat = patientService.createPatient(patMgtCtx);
                    result.setCreatedPatient(pat);
                } else {
                    pat = updatePatient(ctx, pat);
                }
                study = createStudy(ctx, pat);
                if (ctx.getExpirationDate() != null)
                    study.setExpirationDate(ctx.getExpirationDate());
                result.setCreatedStudy(study);
            } else if (checkStorePermission(ctx, study.getPatient())) {
                study = updateStudy(ctx, study);
                updatePatient(ctx, study.getPatient());
            }
            series = createSeries(ctx, study, result);
        } else if (checkStorePermission(ctx, series.getStudy().getPatient())) {
            series = updateSeries(ctx, series);
            updateStudy(ctx, series.getStudy());
            updatePatient(ctx, series.getStudy().getPatient());
        }
        Instance instance = createInstance(ctx, series, conceptNameCode);
        result.setCreatedInstance(instance);
        return instance;
    }

    private boolean checkMissingPatientID(StoreContext ctx) {
        StoreSession session = ctx.getStoreSession();
        ArchiveAEExtension arcAE = session.getArchiveAEExtension();
        AcceptMissingPatientID acceptMissingPatientID = arcAE.acceptMissingPatientID();
        if (acceptMissingPatientID == AcceptMissingPatientID.YES
                || ctx.getAttributes().containsValue(Tag.PatientID))
            return true;

        if (acceptMissingPatientID == AcceptMissingPatientID.NO)
            return false;

        idService.newPatientID(ctx.getAttributes());
        return true;
    }

    private Patient updatePatient(StoreContext ctx, Patient pat) {
        StoreSession session = ctx.getStoreSession();
        ArchiveAEExtension arcAE = session.getArchiveAEExtension();
        ArchiveDeviceExtension arcDev = arcAE.getArchiveDeviceExtension();
        AttributeFilter filter = arcDev.getAttributeFilter(Entity.Patient);
        Attributes.UpdatePolicy updatePolicy = filter.getAttributeUpdatePolicy();
        if (updatePolicy == null)
            return pat;

        Attributes attrs = pat.getAttributes();
        UpdateInfo updateInfo = new UpdateInfo(attrs, updatePolicy);
        if (!attrs.updateSelected(updatePolicy, ctx.getAttributes(), null, filter.getSelection()))
            return pat;

        updateInfo.log(session, pat, attrs);
        pat = em.find(Patient.class, pat.getPk());
        IDWithIssuer idWithIssuer = IDWithIssuer.pidOf(attrs);
        Issuer issuer = idWithIssuer.getIssuer();
        if (issuer != null) {
            PatientID patientID = pat.getPatientID();
            IssuerEntity issuerEntity = patientID.getIssuer();
            if (issuerEntity == null)
                patientID.setIssuer(issuerService.mergeOrCreate(issuer));
            else
                issuerEntity.getIssuer().merge(issuer);
        }
        pat.setAttributes(attrs, filter, arcDev.getFuzzyStr());
        return pat;
    }

    private Study updateStudy(StoreContext ctx, Study study) {
        StoreSession session = ctx.getStoreSession();
        ArchiveAEExtension arcAE = session.getArchiveAEExtension();
        ArchiveDeviceExtension arcDev = arcAE.getArchiveDeviceExtension();
        AttributeFilter filter = arcDev.getAttributeFilter(Entity.Study);
        Attributes.UpdatePolicy updatePolicy = filter.getAttributeUpdatePolicy();
        if (updatePolicy == null)
            return study;

        Attributes attrs = study.getAttributes();
        UpdateInfo updateInfo = new UpdateInfo(attrs, updatePolicy);
        if (!attrs.updateSelected(updatePolicy, ctx.getAttributes(), updateInfo.modified, filter.getSelection()))
            return study;

        updateInfo.log(session, study, attrs);
        study = em.find(Study.class, study.getPk());
        study.setAttributes(attrs, filter, arcDev.getFuzzyStr());
        study.setIssuerOfAccessionNumber(findOrCreateIssuer(attrs, Tag.IssuerOfAccessionNumberSequence));
        setCodes(study.getProcedureCodes(), attrs, Tag.ProcedureCodeSequence);
        return study;
    }

    private Series updateSeries(StoreContext ctx, Series series) {
        StoreSession session = ctx.getStoreSession();
        ArchiveAEExtension arcAE = session.getArchiveAEExtension();
        ArchiveDeviceExtension arcDev = arcAE.getArchiveDeviceExtension();
        AttributeFilter filter = arcDev.getAttributeFilter(Entity.Series);
        Attributes.UpdatePolicy updatePolicy = filter.getAttributeUpdatePolicy();
        if (updatePolicy == null)
            return series;

        Attributes attrs = series.getAttributes();
        UpdateInfo updateInfo = new UpdateInfo(attrs, updatePolicy);
        if (!attrs.updateSelected(updatePolicy, ctx.getAttributes(), null, filter.getSelection()))
            return series;

        updateInfo.log(session, series, attrs);
        series = em.find(Series.class, series.getPk());
        FuzzyStr fuzzyStr = arcDev.getFuzzyStr();
        series.setAttributes(attrs, arcDev.getAttributeFilter(Entity.Series), fuzzyStr);
        series.setInstitutionCode(findOrCreateCode(attrs, Tag.InstitutionCodeSequence));
        setRequestAttributes(series, attrs, fuzzyStr);
        return series;
    }

    private static class UpdateInfo {
        int[] prevTags;
        Attributes modified;
        UpdateInfo(Attributes attrs, Attributes.UpdatePolicy updatePolicy) {
            if (!LOG.isInfoEnabled())
                return;

            prevTags = attrs.tags();
            modified = new Attributes();
        }

        public void log(StoreSession session, Object entity, Attributes attrs) {
            if (!LOG.isInfoEnabled())
                return;

            if (!modified.isEmpty()) {
                Attributes changedTo = new Attributes(modified.size());
                changedTo.addSelected(attrs, modified.tags());
                LOG.info("{}: Modify attributes of {}\nFrom:\n{}To:\n{}", session, entity, modified, changedTo);
            }
            Attributes supplemented = new Attributes();
            supplemented.addNotSelected(attrs, prevTags);
            if (!supplemented.isEmpty())
                LOG.info("{}: Supplement attributes of {}:\n{}", session, entity, supplemented);
        }
    }

    private Study findStudy(StoreContext ctx, UpdateDBResult result) {
        StoreSession storeSession = ctx.getStoreSession();
        Study study = storeSession.getCachedStudy(ctx.getStudyInstanceUID());
        if (study == null)
            try {
                study = em.createNamedQuery(Study.FIND_BY_STUDY_IUID_EAGER, Study.class)
                        .setParameter(1, ctx.getStudyInstanceUID())
                        .getSingleResult();
                study.addStorageID(storeSession.getArchiveAEExtension().storageID());
                if (result.getRejectionNote() == null)
                    updateStudyRejectionState(ctx, study);
            } catch (NoResultException e) {}
        return study;
    }

    private void updateStudyRejectionState(StoreContext ctx, Study study) {
        switch (study.getRejectionState()) {
            case COMPLETE:
                study.setRejectionState(RejectionState.PARTIAL);
                study.getPatient().incrementNumberOfStudies();
                setStudyAttributes(ctx, study);
                break;
            case EMPTY:
                study.setRejectionState(RejectionState.NONE);
                study.getPatient().incrementNumberOfStudies();
                break;
        }
    }

    private Series findSeries(StoreContext ctx, UpdateDBResult result) {
        StoreSession storeSession = ctx.getStoreSession();
        Series series = storeSession.getCachedSeries(ctx.getStudyInstanceUID(), ctx.getSeriesInstanceUID());
        if (series == null)
            try {
                series = em.createNamedQuery(Series.FIND_BY_SERIES_IUID_EAGER, Series.class)
                        .setParameter(1, ctx.getStudyInstanceUID())
                        .setParameter(2, ctx.getSeriesInstanceUID())
                        .getSingleResult();
                series.getStudy().addStorageID(storeSession.getArchiveAEExtension().storageID());
                if (result.getRejectionNote() == null)
                    updateSeriesRejectionState(ctx, series);
            } catch (NoResultException e) {}
        return series;
    }

    private void updateSeriesRejectionState(StoreContext ctx, Series series) {
        if (series.getRejectionState() == RejectionState.COMPLETE) {
            series.setRejectionState(RejectionState.PARTIAL);
            setSeriesAttributes(ctx, series);
            updateStudyRejectionState(ctx, series.getStudy());
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
            return em.createNamedQuery(Instance.FIND_BY_SOP_IUID_EAGER, Instance.class)
                    .setParameter(1, objectUID)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    private Instance findInstance(String studyUID, String seriesUID, String objectUID) {
        try {
            return em.createNamedQuery(Instance.FIND_BY_STUDY_SERIES_SOP_IUID_EAGER, Instance.class)
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
        Study study = new Study();
        study.addStorageID(arcAE.storageID());
        study.setAccessControlID(arcAE.getStoreAccessControlID());
        study.setRejectionState(RejectionState.NONE);
        setStudyAttributes(ctx, study);
        study.setPatient(patient);
        patient.incrementNumberOfStudies();
        em.persist(study);
        LOG.info("{}: Create {}", session, study);
        return study;
    }

    private boolean checkStorePermission(StoreContext ctx, Patient pat) {
        StoreSession session = ctx.getStoreSession();
        ArchiveAEExtension arcAE = session.getArchiveAEExtension();
        String serviceURL = arcAE.storePermissionServiceURL();
        if (serviceURL == null)
            return true;

        Attributes attrs = ctx.getAttributes();
        if (pat != null)
            attrs.addAll(pat.getAttributes());
        String urlspec = new AttributesFormat(serviceURL).format(attrs);
        StorePermission storePermission = storePermissionCache.get(urlspec);
        if (storePermission != null) {
            LOG.debug("{}: Use cached result of Query Store Permission Service {} - {}", session, urlspec, storePermission);
            ctx.setExpirationDate(storePermission.expirationDate);
            return storePermission.granted;
        }

        LOG.info("{}: Query Store Permission Service {}", session, urlspec);
        boolean granted = false;
        LocalDate expirationDate = null;
        try {
            URL url = new URL(urlspec);
            HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
            int responseCode = httpConn.getResponseCode();
            String responseContent = null;
            Pattern responsePattern = arcAE.storePermissionServiceResponsePattern();
            Pattern expirationDatePattern = arcAE.storePermissionServiceExpirationDatePattern();
            switch (responseCode) {
                case HttpURLConnection.HTTP_OK:
                    responseContent = readContent(httpConn);
                    granted = responsePattern == null || responsePattern.matcher(responseContent).find();
                    if (granted && expirationDatePattern != null)
                        expirationDate = selectExpirationDate(session, urlspec, responseContent, expirationDatePattern);
                    break;
                case HttpURLConnection.HTTP_NO_CONTENT:
                    granted = responsePattern == null;
                    break;
            }
            if (!granted) {
                if (responseContent == null || responsePattern == null)
                    LOG.info("{}: Store Permission Service {} returns HTTP Status: {}",
                            session, urlspec, responseCode);
                else
                    LOG.info("{}: Store Permission Service {} response:\n{}\ndoes not match {}",
                            session, urlspec, responseContent, responsePattern);
            }
        } catch (Exception e) {
            LOG.warn("{}: Failed to query Store Permission Service {}:\n", session, urlspec, e);
        }
        storePermissionCache.put(urlspec, new StorePermission(granted, expirationDate));
        ctx.setExpirationDate(expirationDate);
        return granted;
    }

    private static LocalDate selectExpirationDate(StoreSession session, String url, String response, Pattern pattern) {
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            String s = matcher.group(1);
            try {
                return LocalDate.parse(s, DateTimeFormatter.BASIC_ISO_DATE);
            } catch (DateTimeParseException e) {
                LOG.warn("{}: Store Permission Service {} returns invalid Expiration Date: {} - ignored",
                        session, url, s);
            }
        } else {
            LOG.info("{}: Store Permission Service {} response:\n{}\ndoes not contains expiration date {}",
                    session, url, response, pattern);
        }
        return null;
    }

    private static String readContent(HttpURLConnection httpConn) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(512);
        try (InputStream in = httpConn.getInputStream()) {
            StreamUtils.copy(in, out);
        }
        return new String(out.toByteArray(), charsetOf(httpConn));
    }

    private static String charsetOf(HttpURLConnection httpConn) {
        String contentType = httpConn.getContentType().toUpperCase();
        int index = contentType.lastIndexOf("CHARSET=");
        return index >= 0 ? contentType.substring(index + 8) : "UTF-8";
    }

    private void setStudyAttributes(StoreContext ctx, Study study) {
        ArchiveAEExtension arcAE = ctx.getStoreSession().getArchiveAEExtension();
        ArchiveDeviceExtension arcDev = arcAE.getArchiveDeviceExtension();
        Attributes attrs = ctx.getAttributes();
        study.setAttributes(attrs, arcDev.getAttributeFilter(Entity.Study), arcDev.getFuzzyStr());
        study.setIssuerOfAccessionNumber(findOrCreateIssuer(attrs, Tag.IssuerOfAccessionNumberSequence));
        setCodes(study.getProcedureCodes(), attrs, Tag.ProcedureCodeSequence);
    }

    private Series createSeries(StoreContext ctx, Study study, UpdateDBResult result) {
        Series series = new Series();
        setSeriesAttributes(ctx, series);
        series.setStudy(study);
        if (result.getRejectionNote() == null) {
            markOldStudiesAsIncomplete(ctx, series);
            if (ctx.getExpirationDate() == null)
                applyStudyRetentionPolicy(ctx, series);
            series.setRejectionState(RejectionState.NONE);
        } else {
            series.setRejectionState(RejectionState.COMPLETE);
        }
        em.persist(series);
        LOG.info("{}: Create {}", ctx.getStoreSession(), series);
        return series;
    }

    private void markOldStudiesAsIncomplete(StoreContext ctx, Series series) {
        String studyDateThreshold = ctx.getStoreSession().getArchiveAEExtension().fallbackCMoveSCPStudyOlderThan();
        if (studyDateThreshold == null)
            return;

        Study study = series.getStudy();
        if (study.getStudyDate().compareTo(studyDateThreshold) < 0) {
            series.setFailedSOPInstanceUIDList("*");
            study.setFailedSOPInstanceUIDList("*");
        }
    }

    private void applyStudyRetentionPolicy(StoreContext ctx, Series series) {
        StoreSession session = ctx.getStoreSession();
        ArchiveAEExtension arcAE = session.getArchiveAEExtension();
        StudyRetentionPolicy retentionPolicy = arcAE.findStudyRetentionPolicy(session.getRemoteHostName(),
            session.getCallingAET(), session.getCalledAET(), ctx.getAttributes());
        if (retentionPolicy == null)
            return;

        Study study = series.getStudy();
        LocalDate expirationDate = LocalDate.now().plus(retentionPolicy.getRetentionPeriod());
        LocalDate studyExpirationDate = study.getExpirationDate();
        if (studyExpirationDate == null || studyExpirationDate.compareTo(expirationDate) < 0)
            study.setExpirationDate(expirationDate);

        if (retentionPolicy.isExpireSeriesIndividually())
            series.setExpirationDate(expirationDate);
    }

    private void setSeriesAttributes(StoreContext ctx, Series series) {
        StoreSession session = ctx.getStoreSession();
        ArchiveDeviceExtension arcDev = session.getArchiveAEExtension().getArchiveDeviceExtension();
        FuzzyStr fuzzyStr = arcDev.getFuzzyStr();
        Attributes attrs = ctx.getAttributes();
        series.setAttributes(attrs, arcDev.getAttributeFilter(Entity.Series), fuzzyStr);
        series.setInstitutionCode(findOrCreateCode(attrs, Tag.InstitutionCodeSequence));
        setRequestAttributes(series, attrs, fuzzyStr);
        series.setSourceAET(session.getCallingAET());
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
        instance.setRetrieveAETs(ctx.getRetrieveAETs());
        instance.setAvailability(ctx.getAvailability());
        instance.setSeries(series);
        em.persist(instance);
        LOG.info("{}: Create {}", ctx.getStoreSession(), instance);
        return instance;
    }

    private void createLocations(StoreContext ctx, Instance instance, UpdateDBResult result) {
        createLocation(ctx, instance, result, Location.ObjectType.DICOM_FILE);
        createLocation(ctx, instance, result, Location.ObjectType.METADATA);
    }

    private void createLocation(StoreContext ctx, Instance instance, UpdateDBResult result,
                                Location.ObjectType objectType) {
        WriteContext writeContext = ctx.getWriteContext(objectType);
        if (writeContext == null)
            return;

        Storage storage = writeContext.getStorage();
        StorageDescriptor descriptor = storage.getStorageDescriptor();
        Location location = new Location.Builder()
                .storageID(descriptor.getStorageID())
                .storagePath(writeContext.getStoragePath())
                .transferSyntaxUID(objectType == Location.ObjectType.DICOM_FILE ? ctx.getStoreTranferSyntax() : null)
                .objectType(objectType)
                .size(writeContext.getSize())
                .digest(writeContext.getDigest())
                .build();
        location.setInstance(instance);
        em.persist(location);
        result.getLocations().add(location);
    }

    private void copyLocations(StoreContext ctx, Instance instance, UpdateDBResult result) {
        StoreSession session = ctx.getStoreSession();
        Map<Long, UIDMap> uidMapCache = session.getUIDMapCache();
        Map<String, String> uidMap = session.getUIDMap();
        for (Location prevLocation : ctx.getLocations())
            result.getLocations().add(copyLocation(prevLocation, instance, uidMap, uidMapCache));
    }

    private Location copyLocation(
            Location prevLocation, Instance instance, Map<String, String> uidMap, Map<Long, UIDMap> uidMapCache) {
        if (prevLocation.getMultiReference() == null) {
            prevLocation = em.find(Location.class, prevLocation.getPk());
            prevLocation.setMultiReference(idService.newLocationMultiReference());
        }
        Location newLocation = new Location(prevLocation);
        newLocation.setUidMap(createUIDMap(uidMap, prevLocation.getUidMap(), uidMapCache));
        newLocation.setInstance(instance);
        em.persist(newLocation);
        return newLocation;
    }

    private UIDMap createUIDMap(Map<String, String> uidMap, UIDMap prevUIDMap, Map<Long, UIDMap> uidMapCache) {
        Long key = prevUIDMap != null ? prevUIDMap.getPk() : null;
        UIDMap result = uidMapCache.get(key);
        if (result != null)
            return result;

        uidMap = foldUIDMaps(uidMap, prevUIDMap);
        UIDMap newUIDMap = new UIDMap();
        newUIDMap.setUIDMap(uidMap);
        em.persist(newUIDMap);
        LOG.debug("Persisted uid map is " + newUIDMap + "..." + newUIDMap.toString());
        em.flush();
        uidMapCache.put(key, newUIDMap);
        return newUIDMap;
    }

    private Map<String, String> foldUIDMaps(Map<String, String> uidMap, UIDMap prevUIDMap) {
        if (prevUIDMap == null)
            return uidMap;

        Map<String, String> prevUidMap = prevUIDMap.getUIDMap();
        Map<String, String> result = new HashMap<>(uidMap);
        for (Map.Entry<String, String> entry : prevUidMap.entrySet()) {
            String key = entry.getKey();
            String prevValue = entry.getValue();
            String value = uidMap.get(prevValue);
            result.put(key, value != null ? value : prevValue);
        }
        return result;
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
        return item != null && !item.isEmpty() ? issuerService.mergeOrCreate(new Issuer(item)) : null;
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

    private void setCodes(Collection<CodeEntity> codes, Attributes attrs, int seqTag) {
        Sequence seq = attrs.getSequence(seqTag);
        codes.clear();
        if (seq != null)
            for (Attributes item : seq) {
                try {
                    codes.add(codeCache.findOrCreate(new Code(item)));
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
        if (patients.size() == 1)
            return;

        if (patients.isEmpty()) {
            LOG.warn("{}: Failed to find created {}", ctx.getStoreSession(), result.getCreatedPatient());
            return;
        }
        Patient createdPatient = null;
        Patient otherPatient = null;
        for (Patient patient : patients) {
            if (createdPatient == null && patient.getPk() == result.getCreatedPatient().getPk())
                createdPatient = patient;
            else if (otherPatient == null || otherPatient.getPk() > patient.getPk())
                otherPatient = patient;
        }

        if (otherPatient.getMergedWith() != null) {
            LOG.warn("{}: Keep duplicate created {} because existing {} is circular merged",
                    ctx.getStoreSession(), createdPatient, otherPatient, pid);
            return;
        }
        LOG.info("{}: Delete duplicate created {}", ctx.getStoreSession(), createdPatient);
        otherPatient.incrementNumberOfStudies();
        em.merge(result.getCreatedStudy()).setPatient(otherPatient);
        em.remove(createdPatient);
        result.setCreatedPatient(null);
    }
}
