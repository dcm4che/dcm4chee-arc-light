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

package org.dcm4chee.arc.store.impl;

import org.dcm4che3.data.*;
import org.dcm4che3.dict.archive.ArchiveTag;
import org.dcm4che3.json.JSONReader;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.soundex.FuzzyStr;
import org.dcm4che3.util.AttributesFormat;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.util.TagUtils;
import org.dcm4chee.arc.MergeMWLQueryParam;
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
import javax.json.Json;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
        restoreInstances(session, findSeries(ctx), ctx.getStudyInstanceUID());
        Instance prevInstance = findPreviousInstance(ctx);
        if (prevInstance != null) {
            Collection<Location> locations = prevInstance.getLocations();
            result.setPreviousInstance(prevInstance);
            LOG.info("{}: Found previous received {}", session, prevInstance);
            Series prevSeries = prevInstance.getSeries();
            Study prevStudy = prevSeries.getStudy();
            String callingAET = session.getCallingAET();
            if (callingAET != null && callingAET.equals(prevInstance.getExternalRetrieveAET())) {
                if (containsDicomFile(locations)) {
                    logInfo(IGNORE, ctx);
                    return result;
                }
                prevStudy.addStorageID(session.getObjectStorageID());
                prevStudy.updateAccessTime(arcDev.getMaxAccessTimeStaleness());
                createLocation(ctx, prevInstance, result, Location.ObjectType.DICOM_FILE);
                result.setStoredInstance(prevInstance);
                return result;
            }
            if (prevInstance.getSopClassUID().equals(UID.KeyObjectSelectionDocumentStorage)
                    && getRejectionNote(arcDev, prevInstance.getConceptNameCode()) != null)
                throw new DicomServiceException(StoreService.DUPLICATE_REJECTION_NOTE,
                        MessageFormat.format(StoreService.DUPLICATE_REJECTION_NOTE_MSG, prevInstance.getSopInstanceUID()));
            RejectionNote rjNote = getRejectionNote(arcDev, prevInstance.getRejectionNoteCode());
            if (rjNote != null) {
                RejectionNote.AcceptPreviousRejectedInstance accept = rjNote.getAcceptPreviousRejectedInstance();
                switch(accept) {
                    case IGNORE:
                        logInfo(IGNORE_PREVIOUS_REJECTED, ctx, rjNote.getRejectionNoteCode());
                        return result;
                    case REJECT:
                        throw new DicomServiceException(StoreService.SUBSEQUENT_OCCURENCE_OF_REJECTED_OBJECT,
                                MessageFormat.format(StoreService.SUBSEQUENT_OCCURENCE_OF_REJECTED_OBJECT_MSG,
                                        prevInstance.getSopInstanceUID(), rjNote.getRejectionNoteCode()));
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
                    prevSeries.scheduleMetadataUpdate(arcAE.seriesMetadataDelay());
                    prevStudy.setExternalRetrieveAET("*");
                    prevStudy.updateAccessTime(arcDev.getMaxAccessTimeStaleness());
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
                            StoreService.REJECTION_FOR_RETENTION_POLICY_EXPIRED_NOT_AUTHORIZED_MSG);
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
        Series series = instance.getSeries();
        series.scheduleMetadataUpdate(arcAE.seriesMetadataDelay());
        if(rjNote == null) {
            updateSeriesRejectionState(ctx, series);
            if (series.getRejectionState() == RejectionState.NONE) {
                series.scheduleInstancePurge(arcAE.purgeInstanceRecordsDelay());
            }
            Study study = series.getStudy();
            study.setExternalRetrieveAET("*");
            study.updateAccessTime(arcDev.getMaxAccessTimeStaleness());
        }
        return result;
    }

    public void restoreInstances(StoreSession session, String studyUID, String seriesUID) throws DicomServiceException {
        List<Series> resultList = (seriesUID == null
                ? em.createNamedQuery(Series.FIND_SERIES_OF_STUDY_BY_INSTANCE_PURGE_STATE, Series.class)
                .setParameter(1, studyUID)
                .setParameter(2, Series.InstancePurgeState.PURGED)
                : em.createNamedQuery(Series.FIND_BY_SERIES_IUID_AND_INSTANCE_PURGE_STATE, Series.class)
                .setParameter(1, studyUID)
                .setParameter(2, seriesUID)
                .setParameter(3, Series.InstancePurgeState.PURGED))
                .getResultList();
        for (Series series : resultList) {
            restoreInstances(session, series, studyUID);
        }
    }

    private void restoreInstances(StoreSession session, Series series, String studyUID) throws DicomServiceException {
        if (series == null || series.getInstancePurgeState() == Series.InstancePurgeState.NO)
            return;

        Metadata metadata = series.getMetadata();
        try ( ZipInputStream zip = session.getStoreService()
                .openZipInputStream(session, metadata.getStorageID(), metadata.getStoragePath(), studyUID)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                JSONReader jsonReader = new JSONReader(Json.createParser(new InputStreamReader(zip, "UTF-8")));
                jsonReader.setSkipBulkDataURI(true);
                restoreInstance(session, series, jsonReader.readDataset(null));
            }
        } catch (IOException e) {
            throw new DicomServiceException(Status.ProcessingFailure, e);
        }
        series.setInstancePurgeState(Series.InstancePurgeState.NO);
    }

    private void restoreInstance(StoreSession session, Series series, Attributes attrs) {
        Instance inst = createInstance(session, series, findOrCreateCode(attrs, Tag.ConceptNameCodeSequence), attrs,
                attrs.getStrings(Tag.RetrieveAETitle), Availability.valueOf(attrs.getString(Tag.InstanceAvailability)));
        Location location = new Location.Builder()
                .storageID(attrs.getString(ArchiveTag.PrivateCreator, ArchiveTag.StorageID))
                .storagePath(StringUtils.concat(attrs.getStrings(ArchiveTag.PrivateCreator, ArchiveTag.StoragePath), '/'))
                .transferSyntaxUID(attrs.getString(ArchiveTag.PrivateCreator, ArchiveTag.StorageTransferSyntaxUID))
                .digest(attrs.getString(ArchiveTag.PrivateCreator, ArchiveTag.StorageObjectDigest))
                .size(attrs.getInt(ArchiveTag.PrivateCreator, ArchiveTag.StorageObjectSize, -1))
                .build();
        location.setInstance(inst);
        em.persist(location);
    }

    private void rejectInstances(StoreContext ctx, RejectionNote rjNote, CodeEntity rejectionCode,
                                 AllowRejectionForDataRetentionPolicyExpired policy)
            throws DicomServiceException {
        StoreSession session = ctx.getStoreSession();
        Duration seriesMetadataDelay = session.getArchiveAEExtension().seriesMetadataDelay();
        for (Attributes studyRef : ctx.getAttributes().getSequence(Tag.CurrentRequestedProcedureEvidenceSequence)) {
            String studyUID = studyRef.getString(Tag.StudyInstanceUID);
            Series series = null;
            for (Attributes seriesRef : studyRef.getSequence(Tag.ReferencedSeriesSequence)) {
                Instance inst = null;
                String seriesUID = seriesRef.getString(Tag.SeriesInstanceUID);
                series = findSeries(studyUID, seriesUID);
                if (series == null)
                    throw new DicomServiceException(StoreService.REJECTION_FAILED_NO_SUCH_INSTANCE,
                            MessageFormat.format(StoreService.REJECTION_FAILED_NO_SUCH_SERIES_MSG, seriesUID));
                restoreInstances(session, series, studyUID);
                for (Attributes sopRef : seriesRef.getSequence(Tag.ReferencedSOPSequence)) {
                    String classUID = sopRef.getString(Tag.ReferencedSOPClassUID);
                    String objectUID = sopRef.getString(Tag.ReferencedSOPInstanceUID);
                    inst = rejectInstance(session, series, objectUID, classUID, rjNote, rejectionCode);
                }
                if (inst != null) {
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
                    series.scheduleMetadataUpdate(seriesMetadataDelay);
                    series.setInstancePurgeTime(null);
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
                    StoreService.RETENTION_PERIOD_OF_STUDY_NOT_YET_EXPIRED_MSG);
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

    private Instance rejectInstance(StoreSession session, Series series,
                                    String objectUID, String classUID, RejectionNote rjNote,
                                    CodeEntity rejectionCode) throws DicomServiceException {
        Instance inst = findInstance(series, objectUID);
        if (inst == null)
            throw new DicomServiceException(StoreService.REJECTION_FAILED_NO_SUCH_INSTANCE,
                    MessageFormat.format(StoreService.REJECTION_FAILED_NO_SUCH_INSTANCE_MSG, objectUID));
        if (!inst.getSopClassUID().equals(classUID))
            throw new DicomServiceException(StoreService.REJECTION_FAILED_CLASS_INSTANCE_CONFLICT,
                    MessageFormat.format(StoreService.REJECTION_FAILED_CLASS_INSTANCE_CONFLICT_MSG, objectUID));
        CodeEntity prevRjNoteCode = inst.getRejectionNoteCode();
        if (prevRjNoteCode != null) {
            if (!rjNote.isRevokeRejection() && rejectionCode.getPk() == prevRjNoteCode.getPk())
                return inst;
            if (!rjNote.canOverwritePreviousRejection(prevRjNoteCode.getCode()))
                throw new DicomServiceException(StoreService.REJECTION_FAILED_ALREADY_REJECTED,
                        MessageFormat.format(StoreService.REJECTION_FAILED_ALREADY_REJECTED_MSG, objectUID));
        }
        inst.setRejectionNoteCode(rjNote.isRevokeRejection() ? null : rejectionCode);
        if (!rjNote.isRevokeRejection())
            LOG.info("{}: Reject {} by {}", session, inst, rejectionCode.getCode());
        else if (prevRjNoteCode != null)
            LOG.info("{}: Revoke Rejection of {} by {}", session, inst, prevRjNoteCode.getCode());
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

    private long countLocationsByMultiRef(Integer multiRef) {
        return multiRef != null
                ? em.createNamedQuery(Location.COUNT_BY_MULTI_REF, Long.class)
                .setParameter(1, multiRef).getSingleResult()
                : 0L;
    }

    private Long countLocationsByUIDMap(UIDMap uidMap) {
        return em.createNamedQuery(Location.COUNT_BY_UIDMAP, Long.class)
                .setParameter(1, uidMap).getSingleResult();
    }

    public void removeOrMarkToDelete(Location location) {
        if (countLocationsByMultiRef(location.getMultiReference()) > 1)
            em.remove(location);
        else
            markToDelete(location);
    }

    private void markToDelete(Location location) {
        location.setMultiReference(null);
        location.setUidMap(null);
        location.setInstance(null);
        location.setStatus(Location.Status.TO_DELETE);
    }

    public void removeOrphaned(UIDMap uidMap) {
        if (countLocationsByUIDMap(uidMap) == 0)
            em.remove(uidMap);
    }

    private void deleteInstance(Instance instance, StoreContext ctx) {
        Collection<Location> locations = instance.getLocations();
        HashMap<Long, UIDMap> uidMaps = new HashMap<>();
        for (Location location : locations) {
            UIDMap uidMap = location.getUidMap();
            if (uidMap != null)
                uidMaps.put(uidMap.getPk(), uidMap);
            removeOrMarkToDelete(location);
        }
        for (UIDMap uidMap : uidMaps.values())
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
            if (deleteSeriesIfEmpty(series, ctx))
                deleteStudyIfEmpty(study, ctx);
            else
                series.scheduleMetadataUpdate(ctx.getStoreSession().getArchiveAEExtension().seriesMetadataDelay());
        }
    }

    private void deleteStudyIfEmpty(Study study, StoreContext ctx) {
        if (em.createNamedQuery(Series.COUNT_SERIES_OF_STUDY, Long.class)
                .setParameter(1, study)
                .getSingleResult() != 0L)
            return;

        LOG.info("{}: Delete {}", ctx.getStoreSession(), study);
        study.getPatient().decrementNumberOfStudies();
        em.remove(study);
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

    private boolean containsDicomFile(Collection<Location> locations) {
        for (Location location : locations) {
            if (location.getObjectType() == Location.ObjectType.DICOM_FILE)
                return true;
        }
        return false;
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

    private void deleteStudyQueryAttributes(Study study) {
        em.createNamedQuery(StudyQueryAttributes.DELETE_FOR_STUDY).setParameter(1, study).executeUpdate();
    }

    private void deleteSeriesQueryAttributes(Series series) {
        em.createNamedQuery(SeriesQueryAttributes.DELETE_FOR_SERIES).setParameter(1, series).executeUpdate();
    }

    private Instance createInstance(StoreContext ctx, CodeEntity conceptNameCode, UpdateDBResult result)
            throws DicomServiceException {
        StoreSession session = ctx.getStoreSession();
        Series series = session.getCachedSeries(ctx.getStudyInstanceUID(), ctx.getSeriesInstanceUID());
        HttpServletRequest httpRequest = session.getHttpRequest();
        Association as = session.getAssociation();
        PatientMgtContext patMgtCtx = as != null
                ? patientService.createPatientMgtContextDIMSE(as)
                : httpRequest != null
                ? patientService.createPatientMgtContextWEB(httpRequest)
                : patientService.createPatientMgtContextHL7(
                        session.getLocalHL7Application(), session.getSocket(), session.getUnparsedHL7Message());
        patMgtCtx.setAttributes(ctx.getAttributes());
        if (series == null) {
            Study study = findStudy(ctx, result);
            if (study == null) {
                if (!checkMissingPatientID(ctx))
                    throw new DicomServiceException(StoreService.PATIENT_ID_MISSING_IN_OBJECT,
                            StoreService.PATIENT_ID_MISSING_IN_OBJECT_MSG);

                Patient pat = patientService.findPatient(patMgtCtx);
                checkStorePermission(ctx, pat);

                if (pat == null) {
                    patMgtCtx.setPatientID(IDWithIssuer.pidOf(ctx.getAttributes()));
                    pat = patientService.createPatient(patMgtCtx);
                    result.setCreatedPatient(pat);
                } else {
                    pat = updatePatient(ctx, pat);
                }
                study = createStudy(ctx, pat);
                if (ctx.getExpirationDate() != null)
                    study.setExpirationDate(ctx.getExpirationDate());
                result.setCreatedStudy(study);
            } else {
                checkConflictingPID(patMgtCtx, ctx, study.getPatient());
                checkStorePermission(ctx, study.getPatient());
                study = updateStudy(ctx, study);
                updatePatient(ctx, study.getPatient());
            }
            series = createSeries(ctx, study, result);
        } else {
            checkConflictingPID(patMgtCtx, ctx, series.getStudy().getPatient());
            checkStorePermission(ctx, series.getStudy().getPatient());
            series = updateSeries(ctx, series);
            updateStudy(ctx, series.getStudy());
            updatePatient(ctx, series.getStudy().getPatient());
        }
        Instance instance = createInstance(session, series, conceptNameCode,
                ctx.getAttributes(), ctx.getRetrieveAETs(), ctx.getAvailability());
        result.setCreatedInstance(instance);
        return instance;
    }

    private boolean checkMissingPatientID(StoreContext ctx) {
        AcceptMissingPatientID acceptMissingPatientID = ctx.getStoreSession().getAcceptMissingPatientID();
        if (acceptMissingPatientID == AcceptMissingPatientID.YES
                || ctx.getAttributes().containsValue(Tag.PatientID))
            return true;

        if (acceptMissingPatientID == AcceptMissingPatientID.NO)
            return false;

        idService.newPatientID(ctx.getAttributes());
        return true;
    }

    private void checkConflictingPID(PatientMgtContext patMgtCtx, StoreContext ctx, Patient patientOfStudy)
            throws DicomServiceException {
        StoreSession session = ctx.getStoreSession();
        AcceptConflictingPatientID acceptConflictingPatientID = session.getAcceptConflictingPatientID();
        if (acceptConflictingPatientID == AcceptConflictingPatientID.YES)
            return;

        IDWithIssuer pid = patMgtCtx.getPatientID();
        IDWithIssuer pidOfStudy = IDWithIssuer.pidOf(patientOfStudy.getAttributes());
        if (pid == null) {
            if (pidOfStudy == null || session.getAcceptMissingPatientID() == AcceptMissingPatientID.CREATE)
                return;
        } else if (pidOfStudy != null) {
            if (pidOfStudy.matches(pid))
                return;

            if (acceptConflictingPatientID == AcceptConflictingPatientID.MERGED) {
                Patient p = patientService.findPatient(patMgtCtx);
                if (p != null && p.getPk() == patientOfStudy.getPk())
                    return;
            }
        }
        String errorMsg = MessageFormat.format(StoreService.CONFLICTING_PID_NOT_ACCEPTED_MSG,
                pid, pidOfStudy, ctx.getStudyInstanceUID());

        LOG.warn(errorMsg);
        throw new DicomServiceException(StoreService.CONFLICTING_PID_NOT_ACCEPTED, errorMsg);
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
        UpdateInfo updateInfo = new UpdateInfo(attrs);
        if (!attrs.updateSelected(updatePolicy, ctx.getAttributes(), null, filter.getSelection()))
            return pat;

        updateInfo.log(session, pat, attrs);
        pat = em.find(Patient.class, pat.getPk());
        IDWithIssuer idWithIssuer = IDWithIssuer.pidOf(attrs);
        if (idWithIssuer != null) {
            Issuer issuer = idWithIssuer.getIssuer();
            if (issuer != null) {
                PatientID patientID = pat.getPatientID();
                IssuerEntity issuerEntity = patientID.getIssuer();
                if (issuerEntity == null)
                    patientID.setIssuer(issuerService.mergeOrCreate(issuer));
                else
                    issuerEntity.getIssuer().merge(issuer);
            }
        }
        pat.setAttributes(attrs, filter, arcDev.getFuzzyStr());
        em.createNamedQuery(Series.SCHEDULE_METADATA_UPDATE_FOR_PATIENT)
                .setParameter(1, pat)
                .executeUpdate();
        return pat;
    }

    private Study updateStudy(StoreContext ctx, Study study) {
        StoreSession session = ctx.getStoreSession();
        Attributes.UpdatePolicy updatePolicy = session.getStudyUpdatePolicy();
        if (updatePolicy == null)
            return study;

        ArchiveAEExtension arcAE = session.getArchiveAEExtension();
        ArchiveDeviceExtension arcDev = arcAE.getArchiveDeviceExtension();
        AttributeFilter filter = arcDev.getAttributeFilter(Entity.Study);
        Attributes attrs = study.getAttributes();
        UpdateInfo updateInfo = new UpdateInfo(attrs);
        if (!attrs.updateSelected(updatePolicy, ctx.getAttributes(), updateInfo.modified,
                filter.getSelection()))
            return study;

        updateInfo.log(session, study, attrs);
        study = em.find(Study.class, study.getPk());
        study.setAttributes(attrs, filter, arcDev.getFuzzyStr());
        study.setIssuerOfAccessionNumber(findOrCreateIssuer(attrs, Tag.IssuerOfAccessionNumberSequence));
        setCodes(study.getProcedureCodes(), attrs, Tag.ProcedureCodeSequence);
        em.createNamedQuery(Series.SCHEDULE_METADATA_UPDATE_FOR_STUDY)
                .setParameter(1, study)
                .executeUpdate();
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
        UpdateInfo updateInfo = new UpdateInfo(attrs);
        if (!attrs.updateSelected(updatePolicy, ctx.getAttributes(), updateInfo.modified, filter.getSelection()))
            return series;

        updateInfo.log(session, series, attrs);
        series = em.find(Series.class, series.getPk());
        FuzzyStr fuzzyStr = arcDev.getFuzzyStr();
        series.setAttributes(attrs, arcDev.getAttributeFilter(Entity.Series), fuzzyStr);
        series.setInstitutionCode(findOrCreateCode(attrs, Tag.InstitutionCodeSequence));
        setRequestAttributes(series, attrs, fuzzyStr);
        return series;
    }

    public List<Attributes> queryMWL(StoreContext ctx, MergeMWLQueryParam queryParam) {
        LOG.info("{}: Query for MWL Items with {}", ctx.getStoreSession(), queryParam);
        TypedQuery<byte[]> namedQuery = queryParam.accessionNumber != null
                ? em.createNamedQuery(MWLItem.ATTRS_BY_ACCESSION_NO, byte[].class)
                .setParameter(1, queryParam.accessionNumber)
                : queryParam.spsID != null
                ? em.createNamedQuery(MWLItem.ATTRS_BY_STUDY_UID_AND_SPS_ID, byte[].class)
                .setParameter(1, queryParam.studyIUID)
                .setParameter(1, queryParam.spsID)
                : em.createNamedQuery(MWLItem.ATTRS_BY_STUDY_IUID, byte[].class)
                .setParameter(1, queryParam.studyIUID);
        List<byte[]> resultList = namedQuery.getResultList();
        if (resultList.isEmpty()) {
            LOG.info("{}: No matching MWL Items found", ctx.getStoreSession());
            return null;
        }

        LOG.info("{}: Found {} matching MWL Items", ctx.getStoreSession(), resultList.size());
        List<Attributes> mwlItems = new ArrayList<>(resultList.size());
        for (byte[] bytes : resultList) {
            mwlItems.add(AttributesBlob.decodeAttributes(bytes, null));
        }
        return mwlItems;
    }

    private static class UpdateInfo {
        int[] prevTags;
        Attributes modified;
        UpdateInfo(Attributes attrs) {
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
                ArchiveAEExtension arcAE = storeSession.getArchiveAEExtension();
                ArchiveDeviceExtension arcDev = arcAE.getArchiveDeviceExtension();
                study = em.createNamedQuery(Study.FIND_BY_STUDY_IUID_EAGER, Study.class)
                        .setParameter(1, ctx.getStudyInstanceUID())
                        .getSingleResult();
                addStorageIDsToStudy(ctx, study);
                study.updateAccessTime(arcDev.getMaxAccessTimeStaleness());
                if (result.getRejectionNote() == null)
                    updateStudyRejectionState(ctx, study);
            } catch (NoResultException e) {}
        return study;
    }

    private void addStorageIDsToStudy(StoreContext ctx, Study study) {
        for (Location l : ctx.getLocations())
            if (l.getObjectType() == Location.ObjectType.DICOM_FILE)
                study.addStorageID(l.getStorageID());
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

    private Series findSeries(StoreContext ctx) {
        StoreSession storeSession = ctx.getStoreSession();
        String studyInstanceUID = ctx.getStudyInstanceUID();
        String seriesInstanceUID = ctx.getSeriesInstanceUID();
        Series series = storeSession.getCachedSeries(studyInstanceUID, seriesInstanceUID);
        if (series == null) {
            series = findSeries(studyInstanceUID, seriesInstanceUID);
            if (series != null)
                storeSession.cacheSeries(series);
        }
        return series;
    }

    private Series findSeries(String studyInstanceUID, String seriesInstanceUID) {
        try {
            return em.createNamedQuery(Series.FIND_BY_SERIES_IUID_EAGER, Series.class)
                    .setParameter(1, studyInstanceUID)
                    .setParameter(2, seriesInstanceUID)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
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

    private Instance findInstance(Series series, String objectUID) {
        try {
            return em.createNamedQuery(Instance.FIND_BY_SERIES_AND_SOP_IUID, Instance.class)
                    .setParameter(1, series)
                    .setParameter(2, objectUID)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    private Study createStudy(StoreContext ctx, Patient patient) {
        StoreSession session = ctx.getStoreSession();
        ArchiveAEExtension arcAE = session.getArchiveAEExtension();
        Study study = new Study();
        study.addStorageID(objectStorageID(ctx));
        study.setAccessControlID(arcAE.storeAccessControlID(
                session.getRemoteHostName(), session.getCallingAET(), session.getCalledAET(), ctx.getAttributes()));
        study.setCompleteness(Completeness.COMPLETE);
        study.setRejectionState(RejectionState.NONE);
        setStudyAttributes(ctx, study);
        study.setPatient(patient);
        patient.incrementNumberOfStudies();
        em.persist(study);
        LOG.info("{}: Create {}", session, study);
        return study;
    }

    private String objectStorageID(StoreContext ctx) {
        for (Location location : ctx.getLocations())
            if (location.getObjectType() == Location.ObjectType.DICOM_FILE)
                return location.getStorageID();

        return ctx.getStoreSession().getObjectStorageID();
    }

    private void checkStorePermission(StoreContext ctx, Patient pat) throws DicomServiceException {
        StoreSession session = ctx.getStoreSession();
        String serviceURL = session.getArchiveAEExtension().storePermissionServiceURL();
        if (serviceURL == null)
            return;

        Attributes attrs = ctx.getAttributes();
        if (pat != null)
            attrs.addAll(pat.getAttributes());
        String urlspec = new AttributesFormat(serviceURL).format(attrs);
        StorePermission storePermission = storePermissionCache.get(urlspec);
        if (storePermission == null) {
            storePermission = queryStorePermission(session, urlspec);
            storePermissionCache.put(urlspec, storePermission);
        } else
            LOG.debug("{}: Use cached result of Query Store Permission Service {} - {}",
                    session, urlspec, storePermission);

        if (storePermission.exception != null)
            throw storePermission.exception;

        ctx.setExpirationDate(storePermission.expirationDate);
    }

    private StorePermission queryStorePermission(StoreSession session, String urlspec) throws DicomServiceException {
        LOG.info("{}: Query Store Permission Service {}", session, urlspec);
        LocalDate expirationDate = null;
        DicomServiceException exception = null;
        try {
            WebTarget target = ClientBuilder.newBuilder().build().target(urlspec);
            Response resp = target.request().get();
            Pattern responsePattern = session.getArchiveAEExtension().storePermissionServiceResponsePattern();
            switch (resp.getStatus()) {
                case 200:
                    String responseContent = resp.readEntity(String.class);
                    LOG.debug("{}: Store Permission Service {} response:\n{}", session, urlspec, responseContent);
                    if (responsePattern == null || responsePattern.matcher(responseContent).find() )
                        expirationDate = selectExpirationDate(session, urlspec, responseContent);
                    else
                        exception = selectErrorCodeComment(session, urlspec, responseContent);
                    break;
                case 204:
                    if (responsePattern == null)
                        break;
                default:
                    exception = new DicomServiceException(Status.NotAuthorized, StoreService.NOT_AUTHORIZED);
                    break;
            }
        } catch (Exception e) {
            LOG.warn("{}: Failed to query Store Permission Service {}:\n", session, urlspec, e);
            throw new DicomServiceException(Status.ProcessingFailure,
                    StoreService.FAILED_TO_QUERY_STORE_PERMISSION_SERVICE);
        }
        StorePermission result = new StorePermission(expirationDate, exception);
        LOG.info("{}: Store Permission Service {} returns {}", session, urlspec, result);
        return result;
    }

    private LocalDate selectExpirationDate(StoreSession session, String url, String response) {
        Pattern pattern = session.getArchiveAEExtension().storePermissionServiceExpirationDatePattern();
        if (pattern != null) {
            Matcher matcher = pattern.matcher(response);
            if (matcher.find()) {
                String s = matcher.group(1);
                try {
                    return LocalDate.parse(s, DateTimeFormatter.BASIC_ISO_DATE);
                } catch (DateTimeParseException e) {
                    LOG.warn("{}: Store Permission Service {} returns invalid Expiration Date: {} - ignored",
                            session, url, s);
                }
            } else
                LOG.info("{}: Store Permission Service {} response does not contains expiration date", session, url);
        }
        return null;
    }

    private String selectErrorComment(StoreSession session, String url, String response, Pattern pattern) {
        if (pattern != null) {
            Matcher matcher = pattern.matcher(response);
            if (matcher.find())
                return matcher.group(1);
            else
                LOG.info("{}: Store Permission Service {} response does not contain error comment", session, url);
        }
        return StoreService.NOT_AUTHORIZED;
    }

    private int selectErrorCode(StoreSession session, String url, String response, Pattern pattern) {
        if (pattern != null) {
            Matcher matcher = pattern.matcher(response);
            if (matcher.find())
                return Integer.parseInt(matcher.group(1), 16);
            else
                LOG.info("{}: Store Permission Service {} response does not contain error code ", session, url);
        }
        return Status.NotAuthorized;
    }

    private DicomServiceException selectErrorCodeComment(StoreSession session, String url, String response) {
        ArchiveAEExtension arcAE = session.getArchiveAEExtension();
        return new DicomServiceException(
                selectErrorCode(session, url, response, arcAE.storePermissionServiceErrorCodePattern()),
                selectErrorComment(session, url, response, arcAE.storePermissionServiceErrorCommentPattern()));
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
        series.setInstancePurgeState(Series.InstancePurgeState.NO);
        if (result.getRejectionNote() == null) {
            if (markOldStudiesAsIncomplete(ctx, study)) {
                series.setCompleteness(Completeness.UNKNOWN);
                study.setCompleteness(Completeness.UNKNOWN);
            } else {
                series.setCompleteness(Completeness.COMPLETE);
            }
            if (ctx.getExpirationDate() == null)
                applyStudyRetentionPolicy(ctx, series);
            series.setRejectionState(RejectionState.NONE);
        } else {
            series.setCompleteness(Completeness.COMPLETE);
            series.setRejectionState(RejectionState.COMPLETE);
        }
        em.persist(series);
        LOG.info("{}: Create {}", ctx.getStoreSession(), series);
        return series;
    }

    private boolean markOldStudiesAsIncomplete(StoreContext ctx, Study study) {
        String studyDateThreshold = ctx.getStoreSession().getArchiveAEExtension().fallbackCMoveSCPStudyOlderThan();
        return studyDateThreshold != null && study.getStudyDate().compareTo(studyDateThreshold) < 0;
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

    private Instance createInstance(StoreSession session, Series series, CodeEntity conceptNameCode, Attributes attrs,
                                    String[] retrieveAETs, Availability availability) {
        ArchiveDeviceExtension arcDev = session.getArchiveAEExtension().getArchiveDeviceExtension();
        FuzzyStr fuzzyStr = arcDev.getFuzzyStr();
        Instance instance = new Instance();
        instance.setAttributes(attrs, arcDev.getAttributeFilter(Entity.Instance), fuzzyStr);
        setVerifyingObservers(instance, attrs, fuzzyStr);
        instance.setConceptNameCode(conceptNameCode);
        setContentItems(instance, attrs);
        instance.setRetrieveAETs(retrieveAETs);
        instance.setAvailability(availability);
        instance.setSeries(series);
        em.persist(instance);
        LOG.info("{}: Create {}", session, instance);
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
        result.getWriteContexts().add(writeContext);
        if (objectType == Location.ObjectType.DICOM_FILE)
            instance.getSeries().getStudy().addStorageID(descriptor.getStorageID());
    }

    private void copyLocations(StoreContext ctx, Instance instance, UpdateDBResult result) {
        StoreSession session = ctx.getStoreSession();
        Map<Long, UIDMap> uidMapCache = session.getUIDMapCache();
        Map<String, String> uidMap = session.getUIDMap();
        for (Location prevLocation : ctx.getLocations()) {
            result.getLocations().add(copyLocation(prevLocation, instance, uidMap, uidMapCache));
            if (prevLocation.getObjectType() == Location.ObjectType.DICOM_FILE)
                instance.getSeries().getStudy().addStorageID(prevLocation.getStorageID());
        }
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

    public List<String> studyIUIDsByAccessionNo(String accNo) {
        return em.createNamedQuery(Study.STUDY_IUIDS_BY_ACCESSION_NUMBER, String.class)
                .setParameter(1, accNo).getResultList();
    }
}
