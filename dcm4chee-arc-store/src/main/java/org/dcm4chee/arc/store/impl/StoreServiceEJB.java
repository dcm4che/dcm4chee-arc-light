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
 * Portions created by the Initial Developer are Copyright (C) 2013-2019
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
import org.dcm4che3.dict.archive.PrivateTag;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.json.JSONReader;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.soundex.FuzzyStr;
import org.dcm4che3.util.AttributesFormat;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.util.TagUtils;
import org.dcm4chee.arc.MergeMWLQueryParam;
import org.dcm4chee.arc.StorePermission;
import org.dcm4chee.arc.StorePermissionCache;
import org.dcm4chee.arc.code.CodeCache;
import org.dcm4chee.arc.conf.Entity;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.id.IDService;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.patient.PatientService;
import org.dcm4chee.arc.storage.ReadContext;
import org.dcm4chee.arc.storage.Storage;
import org.dcm4chee.arc.storage.WriteContext;
import org.dcm4chee.arc.store.InstanceLocations;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreService;
import org.dcm4chee.arc.store.StoreSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.*;
import javax.inject.Inject;
import javax.json.Json;
import javax.persistence.*;
import javax.persistence.criteria.*;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
    private static final Logger LOG = LoggerFactory.getLogger(StoreServiceEJB.class);
    private static final String IGNORE = "{}: Ignore received Instance[studyUID={},seriesUID={},objectUID={}]";
    private static final String IGNORE_FROM_DIFFERENT_SOURCE = IGNORE + " from different source";
    private static final String IGNORE_PREVIOUS_REJECTED = IGNORE + " previous rejected by {}";
    private static final String IGNORE_WITH_EQUAL_DIGEST = IGNORE + " with equal digest";
    private static final String IGNORE_DUPLICATE_IMPORT =
            "{}: Ignore duplicate imported Instance[studyUID={},seriesUID={},objectUID={}]";
    private static final String REVOKE_REJECTION =
            "{}: Revoke rejection of Instance[studyUID={},seriesUID={},objectUID={}] by {}";
    private static final String MISSING_REJECTION_NOTE_CONFIGURATION =
            "{}: No Rejection Note configured with code of {}";
    private static final String MISSING_ACCEPT_REJECTION_BEFORE_STORAGE_CONFIGURATION =
            "{}: No Rejection Note with Accept Rejection before Storage configured with code of {}";

    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    @Inject
    private CodeCache codeCache;

    @Inject
    private PatientService patientService;

    @Inject
    private StorePermissionCache storePermissionCache;

    @Inject
    private IDService idService;

    @Inject
    private Device device;

    @Inject
    private StoreServiceEJB ejb;

    private enum LocationOp {
        CREATE(Attributes.COERCE),
        ORPHANED(Attributes.COERCE),
        COPY(Attributes.CORRECT);

        final String reasonForTheAttributeModification;

        LocationOp(String reasonForTheAttributeModification) {
            this.reasonForTheAttributeModification = reasonForTheAttributeModification;
        }

        static LocationOp valueOf(List<Location> locations) {
            return locations.isEmpty() ? CREATE
                    : locations.get(0).getStatus() == Location.Status.ORPHANED
                        ? ORPHANED
                        : COPY;
        }
    }

    public UpdateDBResult updateDB(StoreContext ctx, UpdateDBResult result)
            throws DicomServiceException {
        StoreSession session = ctx.getStoreSession();
        ArchiveAEExtension arcAE = session.getArchiveAEExtension();
        ArchiveDeviceExtension arcDev = getArchiveDeviceExtension();
        restoreInstances(session, findSeries(ctx), ctx.getStudyInstanceUID(), null, null);
        Instance prevInstance = findPreviousInstance(ctx);
        RejectedInstance rejectedInstance = findRejectedInstance(
                ctx.getStudyInstanceUID(),
                ctx.getSeriesInstanceUID(),
                ctx.getSopInstanceUID());
        if (prevInstance != null) {
            result.setPreviousInstance(prevInstance);
            LOG.info("{}: Found previous received {}", session, prevInstance);
            if (isDuplicateImport(ctx, prevInstance)) {
                logInfo(IGNORE_DUPLICATE_IMPORT, ctx);
                return result;
            }
            String callingAET = session.getCallingAET();
            if (callingAET != null && callingAET.equals(prevInstance.getExternalRetrieveAET())) {
                if (containsDicomFile(prevInstance.getLocations())) {
                    logInfo(IGNORE, ctx);
                    return result;
                }
                Series prevSeries = prevInstance.getSeries();
                Study prevStudy = prevSeries.getStudy();
                prevStudy.addStorageID(session.getObjectStorageID());
                prevStudy.updateAccessTime(arcDev.getMaxAccessTimeStaleness());
                createDicomFileLocation(ctx, prevInstance, result);
                prevSeries.resetSize();
                prevStudy.resetSize();
                result.setStoredInstance(prevInstance);
                return result;
            }
            if (prevInstance.getSopClassUID().equals(UID.KeyObjectSelectionDocumentStorage)
                    && getRejectionNote(arcDev, prevInstance.getConceptNameCode()) != null) {
                if (hasLocationWithEqualDigest(ctx, prevInstance)) {
                    logInfo(IGNORE_WITH_EQUAL_DIGEST, ctx);
                    return result;
                }
                throw new DicomServiceException(StoreService.DUPLICATE_REJECTION_NOTE,
                        MessageFormat.format(StoreService.DUPLICATE_REJECTION_NOTE_MSG, prevInstance.getSopInstanceUID()));
            }
            if (rejectedInstance == null) {
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
                if (hasLocationWithEqualDigest(ctx, prevInstance)) {
                    logInfo(IGNORE_WITH_EQUAL_DIGEST, ctx);
                    return result;
                }
            }
        }
        if (rejectedInstance != null) {
            RejectionNote prevRejectionNote = arcDev.getRejectionNote(rejectedInstance.getRejectionNoteCode().getCode());
            if (prevRejectionNote == null) {
                LOG.warn(MISSING_REJECTION_NOTE_CONFIGURATION, session, rejectedInstance);
            } else if (prevInstance != null) {
                switch (prevRejectionNote.getAcceptPreviousRejectedInstance()) {
                    case IGNORE:
                        logInfo(IGNORE_PREVIOUS_REJECTED, ctx, prevRejectionNote.getRejectionNoteCode());
                        return result;
                    case REJECT:
                        throw subsequentOccurrenceOfRejectedObject(rejectedInstance);
                    case RESTORE:
                        logInfo(REVOKE_REJECTION, ctx, prevRejectionNote.getRejectionNoteCode());
                        em.remove(rejectedInstance);
                        rejectedInstance = null;
                        if (hasLocationWithEqualDigest(ctx, prevInstance)) {
                            result.setStoredInstance(prevInstance);
                            deleteQueryAttributes(prevInstance);
                            Series prevSeries = prevInstance.getSeries();
                            Study prevStudy = prevSeries.getStudy();
                            prevSeries.scheduleMetadataUpdate(arcAE.seriesMetadataDelay());
                            prevStudy.setExternalRetrieveAET("*");
                            prevStudy.updateAccessTime(arcDev.getMaxAccessTimeStaleness());
                            return result;
                        }
                        break;
                }
            } else if (treatAsSubsequentOccurrence(session, rejectedInstance, prevRejectionNote)) {
                switch (prevRejectionNote.getAcceptPreviousRejectedInstance()) {
                    case IGNORE:
                        break;
                    case REJECT:
                        result.setException(subsequentOccurrenceOfRejectedObject(rejectedInstance));
                        break;
                    case RESTORE:
                        logInfo(REVOKE_REJECTION, ctx, prevRejectionNote.getRejectionNoteCode());
                        em.remove(rejectedInstance);
                        rejectedInstance = null;
                        break;
                }
            }
            result.setRejectedInstance(rejectedInstance);
        }
        boolean replaceLocationOnDifferentStorage = false;
        if (prevInstance != null) {
            LOG.info("{}: Replace previous received {}", session, prevInstance);
            replaceLocationOnDifferentStorage = replaceLocationOnDifferentStorage(session, prevInstance);
            deleteInstance(prevInstance, ctx);
        }
        RejectionNote rjNote = null;
        CodeEntity conceptNameCode = findOrCreateCode(ctx.getAttributes(), Tag.ConceptNameCodeSequence);
        if (conceptNameCode != null && ctx.getSopClassUID().equals(UID.KeyObjectSelectionDocumentStorage)) {
            rjNote = arcDev.getRejectionNote(conceptNameCode.getCode());
            if (rjNote != null) {
                result.setRejectionNote(rjNote);
                if (rjNote.isRevokeRejection()) {
                    revokeRejection(ctx, arcAE);
                    return result;
                }
                rejectInstances(ctx, rjNote, conceptNameCode, arcAE);
            }
        }
        LocationOp locationOp = LocationOp.valueOf(ctx.getLocations());
        Instance instance = createInstance(ctx, conceptNameCode, result, new Date(),
                locationOp.reasonForTheAttributeModification);
        if (locationOp == LocationOp.COPY) {
            copyLocations(ctx, instance, result);
        } else {
            if (locationOp == LocationOp.CREATE) {
                createDicomFileLocation(ctx, instance, result);
            } else { // locationOp == LocationOp.ORPHANED
                updateDicomFileLocation(ctx, instance, result);
            }
            createMetadataLocation(ctx, instance, result);
        }
        result.setStoredInstance(instance);
        deleteQueryAttributes(instance);
        Series series = instance.getSeries();
        Study study = series.getStudy();
        study.resetSize();
        if (replaceLocationOnDifferentStorage) {
            String prevStorageIDs = study.getEncodedStorageIDs();
            study.setStorageIDs(queryStorageIDsOfStudy(study).toArray(StringUtils.EMPTY_STRING));
            String newStorageIDs = study.getEncodedStorageIDs();
            if (!newStorageIDs.equals(prevStorageIDs) && !em.contains(study)) { // not attached
                    em.createNamedQuery(Study.SET_STORAGE_IDS)
                            .setParameter(1, study.getPk())
                            .setParameter(2, study.getEncodedStorageIDs())
                            .executeUpdate();
            }
        }
        series.scheduleMetadataUpdate(arcAE.seriesMetadataDelay());
        series.scheduleStorageVerification(arcAE.storageVerificationInitialDelay());
        if (locationOp != LocationOp.COPY) {
            series.scheduleInstancePurge(arcAE.purgeInstanceRecordsDelay());
        }
        if (rjNote == null) {
            updateSeriesRejectionState(ctx, series, rejectedInstance);
            updateStudyRejectionState(ctx, study, rejectedInstance);
            study.setExternalRetrieveAET("*");
            study.updateAccessTime(arcDev.getMaxAccessTimeStaleness());
            Patient patient = study.getPatient();
            if (isPatientVerificationStale(patient, arcDev.getPatientVerificationMaxStaleness())) {
                patient.setVerificationStatus(Patient.VerificationStatus.UNVERIFIED);
                LOG.info("Schedule verification of {}", patient);
            }
        }
        return result;
    }

    private static boolean isDuplicateImport(StoreContext ctx, Instance prevInstance) {
        return ctx.getWriteContext(Location.ObjectType.DICOM_FILE) == null
                && contains(prevInstance.getLocations(), ctx.getReadContext());
    }

    private static boolean contains(Collection<Location> locations, ReadContext readContext) {
        String storageID = readContext.getStorage().getStorageDescriptor().getStorageID();
        String storagePath = readContext.getStoragePath();
        for (Location location : locations) {
            if (storageID.equals(location.getStorageID())
                    && storagePath.equals(location.getStoragePath()))
                return true;
        }
        return false;
    }

    private static DicomServiceException subsequentOccurrenceOfRejectedObject(RejectedInstance rejectedInstance) {
        return new DicomServiceException(StoreService.SUBSEQUENT_OCCURRENCE_OF_REJECTED_OBJECT,
                MessageFormat.format(StoreService.SUBSEQUENT_OCCURRENCE_OF_REJECTED_OBJECT_MSG,
                        rejectedInstance.getSopInstanceUID(), rejectedInstance.getRejectionNoteCode()));
    }

    private static boolean hasLocationWithEqualDigest(StoreContext ctx, Instance prevInstance) {
        return containsWithEqualDigest(prevInstance.getLocations(),
                ctx.getWriteContext(Location.ObjectType.DICOM_FILE).getDigest());
    }

    private boolean treatAsSubsequentOccurrence(StoreSession session, RejectedInstance rejectedInstance,
            RejectionNote rjNote) {
        Duration acceptRejectionBeforeStorage = rjNote.getAcceptRejectionBeforeStorage();
        if (acceptRejectionBeforeStorage == null) {
            LOG.warn(MISSING_ACCEPT_REJECTION_BEFORE_STORAGE_CONFIGURATION, session, rejectedInstance);
            return false;
        }
        return System.currentTimeMillis() - rejectedInstance.getCreatedTime().getTime() >
                acceptRejectionBeforeStorage.getSeconds() * 1000L;
    }

    private static boolean isPatientVerificationStale(Patient patient, Duration maxStaleness) {
        if (maxStaleness != null)
            switch (patient.getVerificationStatus()) {
                case VERIFIED:
                case NOT_FOUND:
                    return isBefore(patient.getVerificationTime(), maxStaleness);
                case VERIFICATION_FAILED:
                    return true;
            }
        return false;
    }

    private static boolean isBefore(Date time, Duration duration) {
        return time.getTime() + duration.getSeconds() * 1000L < System.currentTimeMillis();
    }

    public List<Instance> restoreInstances(StoreSession session, String studyUID, String seriesUID, Duration duration)
            throws DicomServiceException {
        List<Series> seriesList = (seriesUID == null
                ? em.createNamedQuery(Series.FIND_SERIES_OF_STUDY_BY_INSTANCE_PURGE_STATE, Series.class)
                .setParameter(1, studyUID)
                .setParameter(2, Series.InstancePurgeState.PURGED)
                : em.createNamedQuery(Series.FIND_BY_SERIES_IUID_AND_INSTANCE_PURGE_STATE, Series.class)
                .setParameter(1, studyUID)
                .setParameter(2, seriesUID)
                .setParameter(3, Series.InstancePurgeState.PURGED))
                .getResultList();
        List<Instance> instList = new ArrayList<>();
        for (Series series : seriesList) {
            restoreInstances(session, series, studyUID, duration, instList);
        }
        return instList;
    }

    private void restoreInstances(StoreSession session, Series series, String studyUID, Duration duration,
                                  List <Instance> instList)
            throws DicomServiceException {
        if (series == null || series.getInstancePurgeState() == Series.InstancePurgeState.NO)
            return;

        LOG.info("Restore Instance records of Series[pk={}]", series.getPk());
        Metadata metadata = series.getMetadata();
        try ( ZipInputStream zip = session.getStoreService()
                .openZipInputStream(session, metadata.getStorageID(), metadata.getStoragePath(), studyUID)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                JSONReader jsonReader = new JSONReader(Json.createParser(
                        new InputStreamReader(zip, StandardCharsets.UTF_8)));
                jsonReader.setSkipBulkDataURI(true);
                Instance inst = restoreInstance(session, series, jsonReader.readDataset(null));
                if (instList != null)
                    instList.add(inst);
            }
        } catch (IOException e) {
            LOG.warn("Failed to restore Instance records of Series[pk={}]", series.getPk(), e);
            throw new DicomServiceException(Status.ProcessingFailure, e);
        }
        series.setInstancePurgeState(Series.InstancePurgeState.NO);
        series.scheduleInstancePurge(duration);
    }

    private Instance restoreInstance(StoreSession session, Series series, Attributes attrs) {
        Instance inst = createInstance(session, series, findOrCreateCode(attrs, Tag.ConceptNameCodeSequence), attrs,
                attrs.getStrings(Tag.RetrieveAETitle), Availability.valueOf(attrs.getString(Tag.InstanceAvailability)));
        restoreLocation(session, inst, attrs);
        Sequence otherStorageSeq = attrs.getSequence(PrivateTag.PrivateCreator, PrivateTag.OtherStorageSequence);
        if (otherStorageSeq != null) {
            for (Attributes otherStorageItem : otherStorageSeq) {
                restoreLocation(session, inst, otherStorageItem);
            }
        }
        return inst;
    }

    private void restoreLocation(StoreSession session, Instance inst, Attributes attrs) {
        Location location = new Location.Builder()
                .storageID(attrs.getString(PrivateTag.PrivateCreator, PrivateTag.StorageID))
                .storagePath(StringUtils.concat(attrs.getStrings(PrivateTag.PrivateCreator, PrivateTag.StoragePath), '/'))
                .transferSyntaxUID(attrs.getString(PrivateTag.PrivateCreator, PrivateTag.StorageTransferSyntaxUID))
                .digest(attrs.getString(PrivateTag.PrivateCreator, PrivateTag.StorageObjectDigest))
                .size(attrs.getInt(PrivateTag.PrivateCreator, PrivateTag.StorageObjectSize, -1))
                .status(attrs.getString(PrivateTag.PrivateCreator, PrivateTag.StorageObjectStatus))
                .build();
        location.setInstance(inst);
        inst.getLocations().add(location);
        em.persist(location);
        LOG.info("{}: Create {}", session, location);
    }

    private void rejectInstances(StoreContext ctx, RejectionNote rjNote, CodeEntity rejectionCode,
                                 ArchiveAEExtension arcAE)
            throws DicomServiceException {
        StoreSession session = ctx.getStoreSession();
        Duration seriesMetadataDelay = arcAE.seriesMetadataDelay();
        Duration purgeInstanceRecordsDelay = arcAE.purgeInstanceRecordsDelay();
        boolean acceptRejectionBeforeStorage = rjNote.getAcceptRejectionBeforeStorage() != null;
        for (Attributes studyRef : ctx.getAttributes().getSequence(Tag.CurrentRequestedProcedureEvidenceSequence)) {
            String studyUID = studyRef.getString(Tag.StudyInstanceUID);
            Series series = null;
            for (Attributes seriesRef : studyRef.getSequence(Tag.ReferencedSeriesSequence)) {
                String seriesUID = seriesRef.getString(Tag.SeriesInstanceUID);
                series = findSeries(studyUID, seriesUID);
                restoreInstances(session, series, studyUID, purgeInstanceRecordsDelay, null);
                List<String> sopIUIDsOfSeries = null;
                if (!acceptRejectionBeforeStorage) {
                    if (series == null)
                        throw new DicomServiceException(StoreService.REJECTION_FAILED_NO_SUCH_INSTANCE,
                                MessageFormat.format(StoreService.REJECTION_FAILED_NO_SUCH_SERIES_MSG, seriesUID));
                    sopIUIDsOfSeries = sopIUIDsOfSeries(series);
                }
                if (series != null && rjNote.getRejectionNoteType() == RejectionNote.Type.DATA_RETENTION_POLICY_EXPIRED)
                    checkExpirationDate(series, arcAE);

                for (Attributes sopRef : seriesRef.getSequence(Tag.ReferencedSOPSequence)) {
                    String classUID = sopRef.getString(Tag.ReferencedSOPClassUID);
                    String objectUID = sopRef.getString(Tag.ReferencedSOPInstanceUID);
                    if (!acceptRejectionBeforeStorage && !sopIUIDsOfSeries.contains(objectUID))
                        throw new DicomServiceException(StoreService.REJECTION_FAILED_NO_SUCH_INSTANCE,
                                MessageFormat.format(StoreService.REJECTION_FAILED_NO_SUCH_INSTANCE_MSG, objectUID));

                    try {
                        ejb.rejectInstance(session, rjNote, rejectionCode, studyUID, seriesUID, classUID, objectUID);
                    } catch (EJBException e) {
                        if (e.getCausedByException() instanceof DicomServiceException)
                            throw (DicomServiceException) e.getCausedByException();
                        throw e;
                    }
                }
                if (series != null) {
                    RejectionState rejectionState = hasNotRejectedInstances(series)
                            ? RejectionState.PARTIAL : RejectionState.COMPLETE;
                    series.setRejectionState(rejectionState);
                    if (rejectionState == RejectionState.COMPLETE)
                        series.setExpirationDate(null);
                    deleteSeriesQueryAttributes(series);
                    series.scheduleMetadataUpdate(seriesMetadataDelay);
                }
            }
            if (series != null) {
                Study study = series.getStudy();
                if (hasSeriesWithOtherRejectionState(study, RejectionState.COMPLETE))
                    study.setRejectionState(RejectionState.PARTIAL);
                else {
                    study.setRejectionState(RejectionState.COMPLETE);
                    study.setExpirationDate(null);
                    study.getPatient().decrementNumberOfStudies();
                }
                deleteStudyQueryAttributes(study);
            }
        }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void rejectInstance(StoreSession session, RejectionNote rjNote, CodeEntity rejectionCode, String studyUID,
                               String seriesUID, String classUID, String objectUID) throws DicomServiceException {
        RejectedInstance rejectedInstance = findRejectedInstance(studyUID, seriesUID, objectUID);
        if (rejectedInstance != null) {
            LOG.info("{}: Detect previous {}", session, rejectedInstance);
            CodeEntity prevRjNoteCode = rejectedInstance.getRejectionNoteCode();
            if (rejectionCode.getPk() != prevRjNoteCode.getPk()) {
                if (!rjNote.canOverwritePreviousRejection(prevRjNoteCode.getCode()))
                    throw new DicomServiceException(StoreService.REJECTION_FAILED_ALREADY_REJECTED,
                            MessageFormat.format(StoreService.REJECTION_FAILED_ALREADY_REJECTED_MSG, objectUID));
                rejectedInstance.setRejectionNoteCode(rejectionCode);
                LOG.info("{}: {}", session, rejectedInstance);
            }
        } else {
            rejectedInstance = new RejectedInstance(studyUID, seriesUID, objectUID, classUID, rejectionCode);
            em.persist(rejectedInstance);
            LOG.info("{}: {}", session, rejectedInstance);
        }
    }

    private void revokeRejection(StoreContext ctx, ArchiveAEExtension arcAE) throws DicomServiceException {
        StoreSession session = ctx.getStoreSession();
        Duration seriesMetadataDelay = arcAE.seriesMetadataDelay();
        Duration purgeInstanceRecordsDelay = arcAE.purgeInstanceRecordsDelay();
        for (Attributes studyRef : ctx.getAttributes().getSequence(Tag.CurrentRequestedProcedureEvidenceSequence)) {
            String studyUID = studyRef.getString(Tag.StudyInstanceUID);
            Series series = null;
            for (Attributes seriesRef : studyRef.getSequence(Tag.ReferencedSeriesSequence)) {
                int revoked = 0;
                String seriesUID = seriesRef.getString(Tag.SeriesInstanceUID);
                for (Attributes sopRef : seriesRef.getSequence(Tag.ReferencedSOPSequence)) {
                    String objectUID = sopRef.getString(Tag.ReferencedSOPInstanceUID);
                    String classUID = sopRef.getString(Tag.ReferencedSOPClassUID);
                    RejectedInstance rejectedInstance = findRejectedInstance(studyUID, seriesUID, objectUID);
                    if (rejectedInstance != null) {
                        em.remove(rejectedInstance);
                        revoked++;
                        LOG.info("{}: Revoke {}", session, rejectedInstance);
                    } else {
                        LOG.info("{}: Ignore Revoke Rejection of Instance[uid={},class={}] of Series[uid={}] " +
                                        "of Study[uid={}]", session, objectUID, classUID, seriesUID, studyUID);
                    }
                }
                if (revoked > 0) {
                    series = findSeries(studyUID, seriesUID);
                    if (series != null) {
                        restoreInstances(session, series, studyUID, purgeInstanceRecordsDelay, null);
                        series.setRejectionState(
                                hasRejectedInstances(series) ? RejectionState.PARTIAL : RejectionState.NONE);
                        deleteSeriesQueryAttributes(series);
                        series.scheduleMetadataUpdate(seriesMetadataDelay);
                    }
                }
            }
            if (series != null) {
                Study study = series.getStudy();
                if (study.getRejectionState() == RejectionState.COMPLETE)
                    study.getPatient().incrementNumberOfStudies();
                study.setRejectionState(
                        hasSeriesWithOtherRejectionState(study, RejectionState.NONE)
                                ? RejectionState.PARTIAL
                                : RejectionState.NONE);
                study.setModifiedTime(new Date());
                deleteStudyQueryAttributes(study);
            }
        }
    }

    private RejectedInstance findRejectedInstance(String studyIUID, String seriesIUID, String sopIUID) {
        try {
            return em.createNamedQuery(RejectedInstance.FIND_BY_UIDS, RejectedInstance.class)
                    .setParameter(1, studyIUID)
                    .setParameter(2, seriesIUID)
                    .setParameter(3, sopIUID)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    private void checkExpirationDate(Series series, ArchiveAEExtension arcAE) throws DicomServiceException {
        switch (arcAE.allowRejectionForDataRetentionPolicyExpired()) {
            case NEVER:
                throw new DicomServiceException(StoreService.REJECTION_FOR_RETENTION_POLICY_EXPIRED_NOT_ALLOWED,
                    StoreService.REJECTION_FOR_RETENTION_POLICY_EXPIRED_NOT_ALLOWED_MSG);
            case ONLY_EXPIRED:
                if (!isExpired(series, false))
                    throw new DicomServiceException(StoreService.RETENTION_PERIOD_OF_STUDY_NOT_YET_EXPIRED,
                            StoreService.RETENTION_PERIOD_OF_STUDY_NOT_YET_EXPIRED_MSG);
                break;
            case EXPIRED_UNSET:
                if (!isExpired(series, true))
                    throw new DicomServiceException(StoreService.RETENTION_PERIOD_OF_STUDY_NOT_YET_EXPIRED,
                            StoreService.RETENTION_PERIOD_OF_STUDY_NOT_YET_EXPIRED_MSG);
                break;
        }
    }

    private static boolean isExpired(Series series, boolean matchUnset) {
        if (series.getStudy().getExpirationDate() == null)
            return matchUnset;

        LocalDate currentDate = LocalDate.now();
        LocalDate expirationDate = series.getExpirationDate() != null
                                    ? series.getExpirationDate()
                                    : series.getStudy().getExpirationDate();
        return expirationDate.isBefore(currentDate) || expirationDate.equals(currentDate);
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

    public void removeOrMarkLocationAs(Location location, Location.Status status) {
        if (countLocationsByMultiRef(location.getMultiReference()) > 1)
            em.remove(location);
        else
            markLocationAs(location, status);
    }
    
    private void markLocationAs(Location location, Location.Status status) {
        location.setMultiReference(null);
        location.setUidMap(null);
        location.setInstance(null);
        location.setStatus(status);
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
            removeOrMarkLocationAs(location, Location.Status.TO_DELETE);
        }
        for (UIDMap uidMap : uidMaps.values())
            removeOrphaned(uidMap);
        Series series = instance.getSeries();
        Study study = series.getStudy();
        series.resetSize();
        study.resetSize();
        em.remove(instance);
        em.createNamedQuery(RejectedInstance.DELETE_BY_UIDS)
                .setParameter(1, study.getStudyInstanceUID())
                .setParameter(2, series.getSeriesInstanceUID())
                .setParameter(3, instance.getSopInstanceUID())
                .executeUpdate();
        locations.clear();
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

    private static boolean replaceLocationOnDifferentStorage(StoreSession session, Instance prevInstance) {
        String storageID = session.getObjectStorageID();
        return prevInstance.getLocations().stream().anyMatch(
                l -> Location.isDicomFile(l) && !l.getStorageID().equals(storageID));
    }

    private List<String> queryStorageIDsOfStudy(Study study) {
        return em.createNamedQuery(Location.STORAGE_IDS_BY_STUDY_PK_AND_OBJECT_TYPE, String.class)
                .setParameter(1, study.getPk())
                .setParameter(2, Location.ObjectType.DICOM_FILE)
                .getResultList();
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

    private static boolean containsDicomFile(Collection<Location> locations) {
        for (Location location : locations) {
            if (location.getObjectType() == Location.ObjectType.DICOM_FILE)
                return true;
        }
        return false;
    }

    private static boolean containsWithEqualDigest(Collection<Location> locations, byte[] digest) {
        if (digest == null)
            return false;

        for (Location location : locations) {
            if (location.getStatus() != Location.Status.OK)
                continue;

            byte[] digest2 = location.getDigest();
            if (digest2 != null && Arrays.equals(digest, digest2))
                return true;
        }
        return false;
    }

    private static boolean isSameSource(StoreContext ctx, Instance prevInstance) {
        String callingAET = ctx.getStoreSession().getCallingAET();
        return callingAET != null && callingAET.equals(prevInstance.getSeries().getSendingAET());
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

    private Instance createInstance(StoreContext ctx, CodeEntity conceptNameCode, UpdateDBResult result,
                                    Date now, String reasonForTheAttributeModification)
            throws DicomServiceException {
        StoreSession session = ctx.getStoreSession();
        Series series = session.getCachedSeries(ctx.getStudyInstanceUID(), ctx.getSeriesInstanceUID());
        HttpServletRequestInfo httpRequest = session.getHttpRequest();
        Association as = session.getAssociation();
        PatientMgtContext patMgtCtx = as != null
                ? patientService.createPatientMgtContextDIMSE(as)
                : httpRequest != null
                ? patientService.createPatientMgtContextWEB(httpRequest)
                : patientService.createPatientMgtContextHL7(
                        session.getLocalHL7Application(), session.getSocket(), session.getUnparsedHL7Message());
        patMgtCtx.setAttributes(ctx.getAttributes());
        if (series == null) {
            Study study = findStudy(ctx);
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
                    checkConflictingPatientAttrs(session, ctx, pat);
                    pat = updatePatient(ctx, pat, now, reasonForTheAttributeModification);
                }
                study = createStudy(ctx, pat, result);
                if (ctx.getExpirationDate() != null)
                    study.setExpirationDate(ctx.getExpirationDate());
                result.setCreatedStudy(study);
            } else {
                checkConflictingPID(patMgtCtx, ctx, study.getPatient());
                checkStorePermission(ctx, study.getPatient());
                study = updateStudy(ctx, study, now, reasonForTheAttributeModification);
                updatePatient(ctx, study.getPatient(), now, reasonForTheAttributeModification);
            }
            series = createSeries(ctx, study, result);
        } else {
            checkConflictingPID(patMgtCtx, ctx, series.getStudy().getPatient());
            checkStorePermission(ctx, series.getStudy().getPatient());
            series = updateSeries(ctx, series, now, reasonForTheAttributeModification);
            updateStudy(ctx, series.getStudy(), now, reasonForTheAttributeModification);
            updatePatient(ctx, series.getStudy().getPatient(), now, reasonForTheAttributeModification);
        }
        coerceAttributes(series, now, reasonForTheAttributeModification, result, ctx);
        Instance instance = createInstance(session, series, conceptNameCode,
                result.getStoredAttributes(), ctx.getRetrieveAETs(), ctx.getAvailability());
        result.setCreatedInstance(instance);
        return instance;
    }

    private void coerceAttributes(Series series, Date now, String reason, UpdateDBResult result, StoreContext ctx) {
        Study study = series.getStudy();
        Patient patient = study.getPatient();
        Attributes storedAttrs = new Attributes(result.getStoredAttributes());
        Attributes seriesAttrs = new Attributes(series.getAttributes());
        Attributes studyAttrs = new Attributes(study.getAttributes());
        Attributes patAttrs = new Attributes(patient.getAttributes());
        Attributes.unifyCharacterSets(patAttrs, studyAttrs, seriesAttrs, storedAttrs);
        Attributes modified = result.getCoercedAttributes();
        storedAttrs.updateNotSelected(Attributes.UpdatePolicy.OVERWRITE, patAttrs, modified,
                Tag.SpecificCharacterSet, Tag.OriginalAttributesSequence);
        storedAttrs.updateNotSelected(Attributes.UpdatePolicy.OVERWRITE, studyAttrs, modified,
                Tag.SpecificCharacterSet, Tag.OriginalAttributesSequence);
        storedAttrs.updateNotSelected(Attributes.UpdatePolicy.OVERWRITE, seriesAttrs, modified,
                Tag.SpecificCharacterSet, Tag.OriginalAttributesSequence);
        if (!modified.isEmpty() && recordAttributeModification(ctx))
            result.setStoredAttributes(storedAttrs.addOriginalAttributes(
                    null, now, reason, device.getDeviceName(), modified));
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

    private void checkConflictingPatientAttrs(StoreSession session, StoreContext ctx, Patient pat)
            throws DicomServiceException {
        int[] rejectConflictingPatientAttribute = session.getArchiveAEExtension().rejectConflictingPatientAttribute();
        if (rejectConflictingPatientAttribute.length == 0)
            return;

        for (int tag : rejectConflictingPatientAttribute)
            if (!Objects.equals(ctx.getAttributes().getString(tag), pat.getAttributes().getString(tag)))
                throw new DicomServiceException(StoreService.CONFLICTING_PATIENT_ATTRS_REJECTED,
                        MessageFormat.format(StoreService.CONFLICTING_PATIENT_ATTRS_REJECTED_MSG,
                                pat.getPatientID(),
                                Keyword.valueOf(tag),
                                TagUtils.toString(tag)));
    }


    private Patient updatePatient(StoreContext ctx, Patient pat, Date now, String reason) {
        StoreSession session = ctx.getStoreSession();
        Attributes.UpdatePolicy updatePolicy = session.getPatientUpdatePolicy();
        ArchiveDeviceExtension arcDev = getArchiveDeviceExtension();
        AttributeFilter filter = arcDev.getAttributeFilter(Entity.Patient);
        Attributes attrs = pat.getAttributes();
        UpdateInfo updateInfo = new UpdateInfo(attrs);
        Attributes.unifyCharacterSets(attrs, ctx.getAttributes());
        if (!attrs.updateSelected(updatePolicy, ctx.getAttributes(), null, filter.getSelection(false)))
            return pat;

        updateInfo.log(session, pat, attrs);
        pat = em.find(Patient.class, pat.getPk());
        IDWithIssuer idWithIssuer = IDWithIssuer.pidOf(attrs);
        if (idWithIssuer != null) {
            pat.getPatientID().setIssuer(idWithIssuer.getIssuer());
        }
        pat.setAttributes(recordAttributeModification(ctx)
                    ? attrs.addOriginalAttributes(null, now, reason, device.getDeviceName(), updateInfo.modified)
                    : attrs,
                filter, true, arcDev.getFuzzyStr());
        em.createNamedQuery(Series.SCHEDULE_METADATA_UPDATE_FOR_PATIENT)
                .setParameter(1, pat)
                .executeUpdate();
        return pat;
    }

    private Study updateStudy(StoreContext ctx, Study study, Date now, String reason) {
        StoreSession session = ctx.getStoreSession();
        Attributes.UpdatePolicy updatePolicy = study.getRejectionState() == RejectionState.EMPTY
                ? Attributes.UpdatePolicy.OVERWRITE
                : session.getStudyUpdatePolicy();
        ArchiveDeviceExtension arcDev = getArchiveDeviceExtension();
        AttributeFilter filter = arcDev.getAttributeFilter(Entity.Study);
        Attributes attrs = study.getAttributes();
        UpdateInfo updateInfo = new UpdateInfo(attrs);
        Attributes.unifyCharacterSets(attrs, ctx.getAttributes());
        boolean updated = false;
        if (updatePolicy == Attributes.UpdatePolicy.SUPPLEMENT) {
            updated = supplementIssuer(attrs, ctx.getAttributes(), updateInfo.modified,
                    Tag.AccessionNumber, Tag.IssuerOfAccessionNumberSequence);
            updated = supplementIssuer(attrs, ctx.getAttributes(), updateInfo.modified,
                    Tag.AdmissionID, Tag.IssuerOfAdmissionIDSequence)
                    || updated;
        }
        updated = attrs.updateSelected(updatePolicy,
                ctx.getAttributes(), updateInfo.modified, filter.getSelection(false))
                || updated;
        if (!updated)
            return study;

        updateInfo.log(session, study, attrs);
        study = em.find(Study.class, study.getPk());
        study.setAttributes(recordAttributeModification(ctx)
                    ? attrs.addOriginalAttributes(null, now, reason, device.getDeviceName(), updateInfo.modified)
                    : attrs,
                filter, true, arcDev.getFuzzyStr());
        setCodes(study.getProcedureCodes(), attrs, Tag.ProcedureCodeSequence);
        em.createNamedQuery(Series.SCHEDULE_METADATA_UPDATE_FOR_STUDY)
                .setParameter(1, study)
                .executeUpdate();
        return study;
    }

    private static boolean supplementIssuer(Attributes attrs, Attributes newAttrs, Attributes modified,
                                            int idtag, int seqtag) {
        String id = attrs.getString(idtag);
        if (id != null && id.equals(newAttrs.getString(idtag))) {
            Attributes item = attrs.getNestedDataset(seqtag);
            if (item != null) {
                Attributes newItem = newAttrs.getNestedDataset(seqtag);
                if (newItem != null) {
                    Attributes origItem = new Attributes(item);
                    if (item.update(Attributes.UpdatePolicy.SUPPLEMENT, newItem, null)) {
                        if (Issuer.valueOf(item).matches(Issuer.valueOf(origItem))) {
                            modified.newSequence(seqtag, 1).add(origItem);
                            return true;
                        } else { // revert update
                            Sequence seq = attrs.getSequence(seqtag);
                            seq.remove(item);
                            seq.add(origItem);
                        }
                    }
                }
            }
        }
        return false;
    }

    private Series updateSeries(StoreContext ctx, Series series, Date now, String reason) {
        StoreSession session = ctx.getStoreSession();
        ArchiveDeviceExtension arcDev = getArchiveDeviceExtension();
        AttributeFilter filter = arcDev.getAttributeFilter(Entity.Series);
        Attributes.UpdatePolicy updatePolicy = filter.getAttributeUpdatePolicy();
        if (updatePolicy == null)
            return series;

        Attributes attrs = series.getAttributes();
        UpdateInfo updateInfo = new UpdateInfo(attrs);
        Attributes.unifyCharacterSets(attrs, ctx.getAttributes());
        if (!attrs.updateSelected(updatePolicy, ctx.getAttributes(), updateInfo.modified, filter.getSelection(false)))
            return series;

        updateInfo.log(session, series, attrs);
        series = em.find(Series.class, series.getPk());
        FuzzyStr fuzzyStr = arcDev.getFuzzyStr();
        series.setAttributes(recordAttributeModification(ctx)
                    ? attrs.addOriginalAttributes(null, now, reason, device.getDeviceName(), updateInfo.modified)
                    : attrs,
                filter, true, fuzzyStr);
        series.setInstitutionCode(findOrCreateCode(attrs, Tag.InstitutionCodeSequence));
        series.setInstitutionalDepartmentTypeCode(findOrCreateCode(attrs, Tag.InstitutionalDepartmentTypeCodeSequence));
        setRequestAttributes(series, attrs, fuzzyStr);
        return series;
    }

    public List<Attributes> queryMWL(StoreContext ctx, MergeMWLQueryParam queryParam) {
        LOG.info("{}: Query for MWL Items with {}", ctx.getStoreSession(), queryParam);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<javax.persistence.Tuple> q = cb.createTupleQuery();
        Root<MWLItem> mwlItem = q.from(MWLItem.class);
        Join<MWLItem, Patient> patient = mwlItem.join(MWLItem_.patient);
        Join<Patient, PatientID> patientID = patient.join(Patient_.patientID);
        List<Predicate> predicates = new ArrayList<>();
        if (queryParam.localMwlSCPs.length > 0)
            predicates.add(cb.or(mwlItem.get(MWLItem_.localAET).in(queryParam.localMwlSCPs)));
        if (queryParam.patientID != null)
            predicates.add(cb.equal(patientID.get(PatientID_.id), queryParam.patientID));
        if (queryParam.accessionNumber != null)
            predicates.add(cb.equal(mwlItem.get(MWLItem_.accessionNumber), queryParam.accessionNumber));
        if (queryParam.studyIUID != null)
            predicates.add(cb.equal(mwlItem.get(MWLItem_.studyInstanceUID), queryParam.studyIUID));
        if (queryParam.spsID != null)
            predicates.add(cb.equal(mwlItem.get(MWLItem_.scheduledProcedureStepID), queryParam.spsID));
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));
        q.multiselect(
                mwlItem.get(MWLItem_.attributesBlob).get(AttributesBlob_.encodedAttributes),
                patient.get(Patient_.attributesBlob).get(AttributesBlob_.encodedAttributes));
        TypedQuery<Tuple> query = em.createQuery(q);
        List<Tuple> resultList = query.getResultList();
        if (resultList.isEmpty()) {
            LOG.info("{}: No matching MWL Items found", ctx.getStoreSession());
            return null;
        }

        LOG.info("{}: Found {} matching MWL Items", ctx.getStoreSession(), resultList.size());
        List<Attributes> mwlItems = new ArrayList<>(resultList.size());
        for (Tuple result : resultList) {
            Attributes mwlAttrs = AttributesBlob.decodeAttributes(result.get(0, byte[].class), null);
            Attributes patAttrs = AttributesBlob.decodeAttributes(result.get(1, byte[].class), null);
            Attributes.unifyCharacterSets(patAttrs, mwlAttrs);
            mwlAttrs.addAll(patAttrs);
            mwlItems.add(mwlAttrs);
        }
        return mwlItems;
    }

    public void replaceLocation(StoreContext ctx, InstanceLocations inst) {
        Instance instance = new Instance();
        instance.setPk(inst.getInstancePk());
        createLocation(ctx, instance, Location.ObjectType.DICOM_FILE,
                ctx.getWriteContext(Location.ObjectType.DICOM_FILE), ctx.getStoreTranferSyntax());
        for (Location location : inst.getLocations()) {
            removeOrMarkLocationAs(em.find(Location.class, location.getPk()), Location.Status.TO_DELETE);
        }
    }

    private static class UpdateInfo {
        int[] prevTags;
        Attributes modified;
        UpdateInfo(Attributes attrs) {
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

    private Study findStudy(StoreContext ctx) {
        StoreSession storeSession = ctx.getStoreSession();
        Study study = storeSession.getCachedStudy(ctx.getStudyInstanceUID());
        if (study == null)
            try {
                study = em.createNamedQuery(Study.FIND_BY_STUDY_IUID_EAGER, Study.class)
                        .setParameter(1, ctx.getStudyInstanceUID())
                        .getSingleResult();
                addStorageIDsToStudy(ctx, study);
            } catch (NoResultException e) {}
        return study;
    }

    private void addStorageIDsToStudy(StoreContext ctx, Study study) {
        for (Location l : ctx.getLocations())
            if (l.getObjectType() == Location.ObjectType.DICOM_FILE)
                study.addStorageID(l.getStorageID());
    }

    private void updateStudyRejectionState(StoreContext ctx, Study study, RejectedInstance rejectedInstance) {
        if (rejectedInstance == null)
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
        else switch (study.getRejectionState()) {
            case NONE:
                study.setRejectionState(RejectionState.PARTIAL);
                break;
            case EMPTY:
                study.setRejectionState(RejectionState.COMPLETE);
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

    private void updateSeriesRejectionState(StoreContext ctx, Series series, RejectedInstance rejectedInstance) {
        if (rejectedInstance == null) {
            if (series.getRejectionState() == RejectionState.COMPLETE) {
                series.setRejectionState(RejectionState.PARTIAL);
                setSeriesAttributes(ctx, series);
            }
        } else if (series.getRejectionState() == RejectionState.NONE) {
            series.setRejectionState(RejectionState.PARTIAL);
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

    private List<String> sopIUIDsOfSeries(Series series) {
        return em.createNamedQuery(Instance.IUIDS_OF_SERIES2, String.class)
                    .setParameter(1, series)
                    .getResultList();
    }

    private Study createStudy(StoreContext ctx, Patient patient, UpdateDBResult result) {
        StoreSession session = ctx.getStoreSession();
        ArchiveAEExtension arcAE = session.getArchiveAEExtension();
        Study study = new Study();
        study.addStorageID(objectStorageID(ctx));
        study.setAccessControlID(arcAE.storeAccessControlIDRules()
                .filter(rule -> rule.match(
                                session.getRemoteHostName(),
                                session.getCallingAET(),
                                session.getLocalHostName(),
                                session.getCalledAET(),
                                ctx.getAttributes()))
                .map(StoreAccessControlIDRule::getStoreAccessControlID)
                .findFirst()
                .orElse(arcAE.getStoreAccessControlID()));
        study.setCompleteness(Completeness.COMPLETE);
        study.setExpirationState(ExpirationState.UPDATEABLE);
        setStudyAttributes(ctx, study);
        study.setPatient(patient);
        if (result.getRejectionNote() == null && result.getRejectedInstance() == null) {
            study.setRejectionState(RejectionState.NONE);
            patient.incrementNumberOfStudies();
        } else {
            study.setRejectionState(RejectionState.COMPLETE);
        }
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
        if (serviceURL == null) {
            emulateStorePermissionResponse(ctx, pat);
            return;
        }

        Attributes attrs = ctx.getAttributes();
        if (pat != null) {
            Attributes patAttrs = new Attributes(pat.getAttributes());
            Attributes.unifyCharacterSets(attrs, patAttrs);
            attrs.addAll(patAttrs);
        }
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

    private void emulateStorePermissionResponse(StoreContext ctx, Patient pat) throws DicomServiceException {
        StoreSession session = ctx.getStoreSession();
        String storePermissionServiceResponse = session.getArchiveAEExtension().storePermissionServiceResponse();
        if (storePermissionServiceResponse == null)
            return;

        Attributes attrs = ctx.getAttributes();
        if (pat != null) {
            Attributes patAttrs = new Attributes(pat.getAttributes());
            Attributes.unifyCharacterSets(attrs, patAttrs);
            attrs.addAll(patAttrs);
        }

        String response = new AttributesFormat(storePermissionServiceResponse).format(attrs);
        Pattern responsePattern = session.getArchiveAEExtension().storePermissionServiceResponsePattern();
        if (responsePattern != null && !responsePattern.matcher(response).find())
            throw selectErrorCodeComment(session, null, response);
        else
            ctx.setExpirationDate(selectExpirationDate(session, null, response));
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
                LOG.info("{}: Store Permission Service [{}] response does not contains expiration date",
                        session,
                        url != null ? url : response);
        }
        return null;
    }

    private String selectErrorComment(StoreSession session, String url, String response, Pattern pattern) {
        if (pattern != null) {
            Matcher matcher = pattern.matcher(response);
            if (matcher.find())
                return matcher.group(1);
            else
                LOG.info("{}: Store Permission Service [{}] response does not contain error comment",
                        session,
                        url != null ? url : response);
        }
        return StoreService.NOT_AUTHORIZED;
    }

    private int selectErrorCode(StoreSession session, String url, String response, Pattern pattern) {
        if (pattern != null) {
            Matcher matcher = pattern.matcher(response);
            if (matcher.find())
                return Integer.parseInt(matcher.group(1), 16);
            else
                LOG.info("{}: Store Permission Service [{}] response does not contain error code ",
                        session,
                        url != null ? url : response);
        }
        return Status.NotAuthorized;
    }

    private DicomServiceException selectErrorCodeComment(StoreSession session, String url, String response) {
        ArchiveAEExtension arcAE = session.getArchiveAEExtension();
        return new DicomServiceException(
                selectErrorCode(session, url, response, arcAE.storePermissionServiceErrorCodePattern()),
                selectErrorComment(session, url, response, arcAE.storePermissionServiceErrorCommentPattern()));
    }

    private ArchiveDeviceExtension getArchiveDeviceExtension() {
        return device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
    }

    private void setStudyAttributes(StoreContext ctx, Study study) {
        ArchiveDeviceExtension arcDev = getArchiveDeviceExtension();
        Attributes attrs = ctx.getAttributes();
        study.setAttributes(attrs, arcDev.getAttributeFilter(Entity.Study), false, arcDev.getFuzzyStr());
        setCodes(study.getProcedureCodes(), attrs, Tag.ProcedureCodeSequence);
    }

    private Series createSeries(StoreContext ctx, Study study, UpdateDBResult result) {
        Series series = new Series();
        setSeriesAttributes(ctx, series);
        series.setSopClassUID(ctx.getSopClassUID());
        series.setTransferSyntaxUID(ctx.getStoreTranferSyntax());
        series.setStudy(study);
        series.setInstancePurgeState(Series.InstancePurgeState.NO);
        series.setExpirationState(ExpirationState.UPDATEABLE);
        ArchiveCompressionRule compressionRule = ctx.getCompressionRule();
        if (compressionRule != null && compressionRule.getDelay() != null) {
            series.setCompressionTime(
                    new Date(System.currentTimeMillis() + compressionRule.getDelay().getSeconds() * 1000L));
            series.setCompressionTransferSyntaxUID(compressionRule.getTransferSyntax());
            series.setCompressionImageWriteParams(compressionRule.getImageWriteParams());
        }
        if (result.getRejectionNote() == null) {
            if (markOldStudiesAsIncomplete(ctx, study)) {
                series.setCompleteness(Completeness.UNKNOWN);
                study.setCompleteness(Completeness.UNKNOWN);
            } else {
                series.setCompleteness(Completeness.COMPLETE);
            }
            if (ctx.getExpirationDate() == null)
                applyStudyRetentionPolicy(ctx, series);
            series.setRejectionState(result.getRejectedInstance() == null ? RejectionState.NONE : RejectionState.COMPLETE);
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
        Study study = series.getStudy();
        LocalDate studyExpirationDate = study.getExpirationDate();
        StoreSession session = ctx.getStoreSession();
        ArchiveAEExtension arcAE = session.getArchiveAEExtension();
        StudyRetentionPolicy retentionPolicy = arcAE.findStudyRetentionPolicy(
                session.getRemoteHostName(),
                session.getCallingAET(),
                session.getLocalHostName(),
                session.getCalledAET(),
                ctx.getAttributes());

        if (retentionPolicy != null && retentionPolicy.isFreezeExpirationDate() && retentionPolicy.isRevokeExpiration()) {
            LOG.info("Protect Study[UID={}] from being expired, triggered by {}. Set ExpirationDate[=null] and " +
                            "ExpirationState[={FROZEN}].",
                    study.getStudyInstanceUID(), retentionPolicy);
            freezeStudyAndItsSeries(series, study, null, "Protect");
            return;
        }

        if (study.getExpirationState() == ExpirationState.FROZEN) {
            freezeSeries(series, study, studyExpirationDate, "Freeze");
            return;
        }

        if (retentionPolicy == null)
            return;

        study.setExpirationExporterID(retentionPolicy.getExporterID());
        LocalDate expirationDate = retentionPolicy.expirationDate(ctx.getAttributes());
        if (retentionPolicy.isFreezeExpirationDate()) {
            LOG.info("Freeze Study[UID={}] with ExpirationDate[={}] and ExpirationState[=FROZEN], triggered by {}",
                    study.getStudyInstanceUID(), expirationDate, retentionPolicy);
            freezeStudyAndItsSeries(series, study, expirationDate, "Freeze");
        }
        else {
            if (studyExpirationDate == null || studyExpirationDate.compareTo(expirationDate) < 0)
                study.setExpirationDate(expirationDate);

            if (retentionPolicy.isExpireSeriesIndividually())
                series.setExpirationDate(expirationDate);
        }
    }

    private void freezeStudyAndItsSeries(Series series, Study study, LocalDate expirationDate, String logPrefix) {
        study.setExpirationState(ExpirationState.FROZEN);
        study.setExpirationDate(expirationDate);
        freezeSeries(series, study, expirationDate, logPrefix);
        LOG.info(logPrefix + " {} remaining Series of Study[UID={}] with ExpirationDate[{}] and ExpirationState[=FROZEN]",
                em.createNamedQuery(Series.EXPIRE_SERIES)
                .setParameter(1, study.getPk())
                .setParameter(2, ExpirationState.FROZEN)
                .setParameter(3,
                        expirationDate != null ? DateTimeFormatter.BASIC_ISO_DATE.format(expirationDate) : null)
                .executeUpdate(),
                study.getStudyInstanceUID(),
                expirationDate);
    }

    private void freezeSeries(Series series, Study study, LocalDate expirationDate, String logPrefix) {
        LOG.info(logPrefix + " Series[UID={}] of Study[UID={}] with ExpirationDate[={}] and ExpirationState[=FROZEN]",
                series.getSeriesInstanceUID(), study.getStudyInstanceUID(), expirationDate);
        series.setExpirationDate(expirationDate);
        series.setExpirationState(ExpirationState.FROZEN);
        series.setExpirationExporterID(study.getExpirationExporterID());
    }

    private void setSeriesAttributes(StoreContext ctx, Series series) {
        StoreSession session = ctx.getStoreSession();
        ArchiveDeviceExtension arcDev = getArchiveDeviceExtension();
        FuzzyStr fuzzyStr = arcDev.getFuzzyStr();
        Attributes attrs = ctx.getAttributes();
        series.setAttributes(attrs, arcDev.getAttributeFilter(Entity.Series), false, fuzzyStr);
        series.setInstitutionCode(findOrCreateCode(attrs, Tag.InstitutionCodeSequence));
        series.setInstitutionalDepartmentTypeCode(findOrCreateCode(attrs, Tag.InstitutionalDepartmentTypeCodeSequence));
        setRequestAttributes(series, attrs, fuzzyStr);
        series.setSendingAET(session.getCallingAET());
        series.setReceivingAET(session.getCalledAET());
        series.setSendingPresentationAddress(session.getSendingPresentationAddress());
        series.setReceivingPresentationAddress(session.getReceivingPresentationAddress());
        UnparsedHL7Message hl7msg = session.getUnparsedHL7Message();
        if (hl7msg != null) {
            HL7Segment msh = hl7msg.msh();
            series.setSendingHL7Application(msh.getField(2, null));
            series.setSendingHL7Facility(msh.getField(3, null));
            series.setReceivingHL7Application(msh.getField(4, null));
            series.setReceivingHL7Facility(msh.getField(5, null));
        }
    }

    private Instance createInstance(StoreSession session, Series series, CodeEntity conceptNameCode, Attributes attrs,
                                    String[] retrieveAETs, Availability availability) {
        ArchiveDeviceExtension arcDev = getArchiveDeviceExtension();
        FuzzyStr fuzzyStr = arcDev.getFuzzyStr();
        Instance instance = new Instance();
        instance.setAttributes(attrs, arcDev.getAttributeFilter(Entity.Instance), true, fuzzyStr);
        setRequestAttributes(instance, attrs, fuzzyStr);
        setVerifyingObservers(instance, attrs, fuzzyStr);
        instance.setConceptNameCode(conceptNameCode);
        setContentItems(session, instance, attrs);
        instance.setRetrieveAETs(retrieveAETs);
        instance.setAvailability(availability);
        instance.setSeries(series);
        em.persist(instance);
        LOG.info("{}: Create {}", session, instance);
        return instance;
    }

    private void createDicomFileLocation(StoreContext ctx, Instance instance, UpdateDBResult result) {
        ReadContext readContext = ctx.getReadContext();
        result.getLocations().add(createLocation(ctx, instance, Location.ObjectType.DICOM_FILE,
                readContext, ctx.getStoreTranferSyntax()));
        instance.getSeries().getStudy().addStorageID(ctx.getStoreSession().getObjectStorageID());
        if (readContext instanceof WriteContext)
            result.getWriteContexts().add((WriteContext) readContext);
    }

    private void createMetadataLocation(StoreContext ctx, Instance instance, UpdateDBResult result) {
        WriteContext writeContext = ctx.getWriteContext(Location.ObjectType.METADATA);
        if (writeContext == null)
            return;

        result.getLocations().add(createLocation(ctx, instance, Location.ObjectType.METADATA,
                writeContext, null));
        result.getWriteContexts().add(writeContext);
    }

    private void updateDicomFileLocation(StoreContext ctx, Instance instance, UpdateDBResult result) {
        result.getLocations().add(updateLocation(ctx, instance));
        instance.getSeries().getStudy().addStorageID(ctx.getStoreSession().getObjectStorageID());
    }

    private Location updateLocation(StoreContext ctx, Instance instance) {
        Location location = ctx.getLocations().get(0);
        location.setStatus(Location.Status.OK);
        location.setInstance(instance);
        em.merge(location);
        LOG.info("{}: Update {}", ctx.getStoreSession(), location);
        return location;
    }

    private Location createLocation(
            StoreContext ctx, Instance instance, Location.ObjectType objectType, ReadContext readContext,
            String transferSyntaxUID) {
        Storage storage = readContext.getStorage();
        StorageDescriptor descriptor = storage.getStorageDescriptor();
        Location location = new Location.Builder()
                .storageID(descriptor.getStorageID())
                .storagePath(readContext.getStoragePath())
                .transferSyntaxUID(transferSyntaxUID)
                .objectType(objectType)
                .size(readContext.getSize())
                .digest(readContext.getDigest())
                .build();
        location.setInstance(instance);
        em.persist(location);
        LOG.info("{}: Create {}", ctx.getStoreSession(), location);
        return location;
    }

    private void copyLocations(StoreContext ctx, Instance instance, UpdateDBResult result) {
        StoreSession session = ctx.getStoreSession();
        Map<Long, UIDMap> uidMapCache = session.getUIDMapCache();
        Map<String, String> uidMap = session.getUIDMap();
        for (Location prevLocation : ctx.getLocations()) {
            result.getLocations().add(copyLocation(session, prevLocation, instance, uidMap, uidMapCache));
            if (prevLocation.getObjectType() == Location.ObjectType.DICOM_FILE)
                instance.getSeries().getStudy().addStorageID(prevLocation.getStorageID());
        }
    }

    private Location copyLocation(StoreSession session,
            Location prevLocation, Instance instance, Map<String, String> uidMap, Map<Long, UIDMap> uidMapCache) {
        if (prevLocation.getMultiReference() == null) {
            prevLocation = em.find(Location.class, prevLocation.getPk());
            prevLocation.setMultiReference(idService.newLocationMultiReference());
        }
        Location newLocation = new Location(prevLocation);
        newLocation.setUidMap(createUIDMap(uidMap, prevLocation.getUidMap(), uidMapCache));
        newLocation.setInstance(instance);
        em.persist(newLocation);
        LOG.info("{}: Create {}", session, newLocation);
        return newLocation;
    }

    public void addLocation(StoreSession session, Long instancePk, Location location) {
        Instance instance = em.find(Instance.class, instancePk);
        location.setInstance(instance);
        instance.getLocations().add(location);
        em.persist(location);
        LOG.info("{}: Create {}", session, location);
    }

    public void replaceLocation(StoreSession session, Long instancePk, Location newLocation,
            List<Location> replaceLocations) {
        addLocation(session, instancePk, newLocation);
        for (Location location : replaceLocations) {
            LOG.info("{}: Mark to delete {}", session, location);
            removeOrMarkLocationAs(em.find(Location.class, location.getPk()), Location.Status.TO_DELETE);
        }
    }

    public void addStorageID(String studyIUID, String storageID) {
        Tuple tuple = em.createNamedQuery(Study.STORAGE_IDS_BY_STUDY_UID, Tuple.class)
                .setParameter(1, studyIUID)
                .getSingleResult();
        Long studyPk = tuple.get(0, Long.class);
        String prevStorageIDs = tuple.get(1, String.class);
        String newStorageIDs = Study.addStorageID(prevStorageIDs, storageID);
        if (!newStorageIDs.equals(prevStorageIDs)) {
            em.createNamedQuery(Study.SET_STORAGE_IDS)
                    .setParameter(1, studyPk)
                    .setParameter(2, newStorageIDs)
                    .executeUpdate();
            LOG.info("Associate Study[uid={}] with Storage[id:{}]", studyIUID, storageID);
        }
    }

    public void scheduleMetadataUpdate(String studyIUID, String seriesIUID) {
        if (em.createNamedQuery(Series.SCHEDULE_METADATA_UPDATE_FOR_SERIES_UID)
                .setParameter(1, studyIUID)
                .setParameter(2, seriesIUID)
                .executeUpdate() > 0) {
            LOG.info("Schedule update of metadata of Series[uid={}] of Study[uid={}]", seriesIUID, studyIUID);
        }
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
                SeriesRequestAttributes request = new SeriesRequestAttributes(item, fuzzyStr);
                requestAttributes.add(request);
            }
    }

    private void setRequestAttributes(Instance instance, Attributes attrs, FuzzyStr fuzzyStr) {
        Sequence seq = attrs.getSequence(Tag.ReferencedRequestSequence);
        Collection<InstanceRequestAttributes> requestAttributes = instance.getRequestAttributes();
        requestAttributes.clear();
        if (seq != null)
            for (Attributes item : seq) {
                InstanceRequestAttributes request = new InstanceRequestAttributes(item, fuzzyStr);
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

    private void setContentItems(StoreSession session, Instance inst, Attributes attrs) {
        Collection<ContentItem> contentItems = inst.getContentItems();
        contentItems.clear();
        Sequence seq = attrs.getSequence(Tag.ContentSequence);
        if (seq != null)
            for (Attributes item : seq) {
                String type = item.getString(Tag.ValueType);
                try {
                    if ("CODE".equals(type)) {
                        contentItems.add(new ContentItem(
                                Objects.requireNonNull(
                                        item.getString(Tag.RelationshipType),
                                        "Missing Relationship Type")
                                        .toUpperCase(),
                                Objects.requireNonNull(
                                        findOrCreateCode(item, Tag.ConceptNameCodeSequence),
                                        "Missing Concept Name Code"),
                                Objects.requireNonNull(
                                        findOrCreateCode(item, Tag.ConceptCodeSequence),
                                        "Missing Concept Code")));
                    } else if ("TEXT".equals(type)) {
                        String text = Objects.requireNonNull(
                                item.getString(Tag.TextValue), "Missing Text Value");
                        if (text.length() <= ContentItem.MAX_TEXT_LENGTH) {
                            contentItems.add(new ContentItem(
                                    Objects.requireNonNull(
                                            item.getString(Tag.RelationshipType),
                                            "Missing Relationship Type")
                                            .toUpperCase(),
                                    Objects.requireNonNull(
                                            findOrCreateCode(item, Tag.ConceptNameCodeSequence),
                                            "Missing Concept Name Code"),
                                    text));
                        }
                    }
                } catch (NullPointerException e) {
                    LOG.info("{}: Invalid Content Item: {}", session, e.getMessage());
                }
            }
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

    public void checkDuplicatePatientCreated(StoreContext ctx, IDWithIssuer pid, UpdateDBResult result, Date after) {
        List<Patient> patients = patientService.findPatientsAfter(pid, after);
        if (patients.size() == 1)
            return;

        Patient createdPatient = result.getCreatedPatient();
        long createdPatientPk = createdPatient.getPk();
        Patient otherPatient = patients.get(0);
        if (otherPatient.getPk() == createdPatientPk) {
            LOG.info("{}: Keep duplicate created {} because {} was created after",
                    ctx.getStoreSession(), createdPatient, patients.get(1));
            return;
        }

        Optional<Patient> createdPatientFound =
                patients.stream().filter(p -> p.getPk() == createdPatientPk).findFirst();
        if (!createdPatientFound.isPresent()) {
            LOG.warn("{}: Failed to find created {}", ctx.getStoreSession(), createdPatient);
            return;
        }

        if (otherPatient.getMergedWith() != null) {
            LOG.warn("{}: Keep duplicate created {} because existing {} is circular merged",
                    ctx.getStoreSession(), createdPatient, otherPatient);
            return;
        }
        LOG.info("{}: Delete duplicate created {}", ctx.getStoreSession(), createdPatient);
        otherPatient.incrementNumberOfStudies();
        em.merge(result.getCreatedStudy()).setPatient(otherPatient);
        em.remove(createdPatientFound.get());
        result.setCreatedPatient(null);
    }

    public List<String> studyIUIDsByAccessionNo(String accNo) {
        return em.createNamedQuery(Study.STUDY_IUIDS_BY_ACCESSION_NUMBER, String.class)
                .setParameter(1, accNo).getResultList();
    }

    public int setDigest(Long pk, String digest) {
        return em.createNamedQuery(Location.SET_DIGEST)
                .setParameter(1, pk)
                .setParameter(2, digest)
                .executeUpdate();
    }

    public int setStatus(Long pk, Location.Status status) {
        return em.createNamedQuery(Location.SET_STATUS)
                .setParameter(1, pk)
                .setParameter(2, status)
                .executeUpdate();
    }

    private boolean recordAttributeModification(StoreContext ctx) {
        StoreSession storeSession = ctx.getStoreSession();
        return storeSession.getLocalHL7Application() != null
                ? storeSession.getLocalHL7Application()
                    .getHL7AppExtensionNotNull(ArchiveHL7ApplicationExtension.class)
                    .recordAttributeModification()
                : storeSession.getArchiveAEExtension() != null
                ? storeSession.getArchiveAEExtension().recordAttributeModification()
                : device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class).isRecordAttributeModification();
    }

}
