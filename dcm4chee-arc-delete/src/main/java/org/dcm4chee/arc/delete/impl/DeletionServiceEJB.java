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
 * *** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.delete.impl;

import org.dcm4che3.audit.AuditMessages;
import org.dcm4che3.data.Code;
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.code.CodeCache;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.RetentionPeriod;
import org.dcm4chee.arc.conf.StorageDescriptor;
import org.dcm4chee.arc.delete.RejectionService;
import org.dcm4chee.arc.delete.StudyDeleteContext;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.patient.PatientService;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.qmgt.QueueManager;
import org.dcm4chee.arc.qmgt.QueueSizeLimitExceededException;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.store.impl.StoreServiceEJB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.jms.JMSException;
import javax.jms.JMSRuntimeException;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.*;
import java.time.LocalDate;
import java.time.Period;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.time.format.DateTimeFormatter.BASIC_ISO_DATE;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Oct 2015
 */
@Stateless
public class DeletionServiceEJB {

    private static final Logger LOG = LoggerFactory.getLogger(DeletionServiceEJB.class);

    public static final int MAX_LOCATIONS_PER_INSTANCE = 3;

    @PersistenceContext(unitName = "dcm4chee-arc")
    private EntityManager em;

    @Inject
    private CodeCache codeCache;

    @Inject
    private Device device;

    @Inject
    private PatientService patientService;

    @Inject
    private StoreServiceEJB storeEjb;

    @Inject
    private QueryService queryService;

    @Inject
    private QueueManager queueManager;

    public List<Location> findLocationsWithStatus(String storageID, Location.Status status, int limit) {
        return em.createNamedQuery(Location.FIND_BY_STORAGE_ID_AND_STATUS, Location.class)
                .setParameter(1, storageID)
                .setParameter(2, status)
                .setMaxResults(limit)
                .getResultList();
    }

    public List<Metadata> findMetadataWithStatus(String storageID, Metadata.Status status, int limit) {
        return em.createNamedQuery(Metadata.FIND_BY_STORAGE_ID_AND_STATUS, Metadata.class)
                .setParameter(1, storageID)
                .setParameter(2, status)
                .setMaxResults(limit)
                .getResultList();
    }

    public List<Study.PKUID> findStudiesForDeletionOnStorage(StorageDescriptor desc, boolean retentionPeriods,
            int limit) {
        List<String> studyStorageIDs = getStudyStorageIDs(desc);
        LOG.debug("Query for Studies for deletion on {} with StorageIDs={}", desc, studyStorageIDs);
        return em.createQuery(queryStudiesForDeletionOnStorage(desc, studyStorageIDs, retentionPeriods))
                .setMaxResults(limit)
                .getResultList();
    }

    private CriteriaQuery<Study.PKUID> queryStudiesForDeletionOnStorage(
            StorageDescriptor desc, List<String> studyStorageIDs, boolean retentionPeriods) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Study.PKUID> query = cb.createQuery(Study.PKUID.class);
        Root<Study> study = query.from(Study.class);
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(study.get(Study_.storageIDs).in(studyStorageIDs));
        String[] externalRetrieveAETitles = desc.getExternalRetrieveAETitles();
        if (externalRetrieveAETitles.length > 0)
            predicates.add(study.get(Study_.externalRetrieveAET).in(Arrays.asList(externalRetrieveAETitles)));
        if (retentionPeriods)
            retentionPeriods(predicates, cb, study, desc);
        return query.select(cb.construct(Study.PKUID.class, study.get(Study_.pk), study.get(Study_.studyInstanceUID)))
                .where(predicates.toArray(new Predicate[0]))
                .orderBy(cb.asc(study.get(Study_.accessTime)));
    }

    private void retentionPeriods(List<Predicate> predicates, CriteriaBuilder cb, Root<Study> study, StorageDescriptor desc) {
        Calendar now = Calendar.getInstance();
        List<Predicate> list = new ArrayList<>();
        desc.getRetentionPeriod(RetentionPeriod.DeleteStudies.ReceivedBefore, now).ifPresent(
                period -> list.add(beforeDate(cb, study.get(Study_.createdTime), period)));
        desc.getRetentionPeriod(RetentionPeriod.DeleteStudies.OlderThan, now).ifPresent(
                period -> list.add(beforeDA(cb, study.get(Study_.studyDate), period)));
        desc.getRetentionPeriod(RetentionPeriod.DeleteStudies.NotUsedSince, now).ifPresent(
                period -> list.add(beforeDate(cb, study.get(Study_.accessTime), period)));
        switch (list.size()) {
            case 0:
                break;
            case 1:
                predicates.add(list.get(0));
                break;
            default:
                predicates.add(cb.or(list.toArray(new Predicate[0])));
                break;
        }
    }

    private Predicate beforeDA(CriteriaBuilder cb, Path<String> stringPath, Period period) {
        return cb.lessThanOrEqualTo(stringPath, BASIC_ISO_DATE.format(LocalDate.now().minus(period)));
    }

    private Predicate beforeDate(CriteriaBuilder cb, Path<Date> datePath, Period period) {
        return cb.lessThanOrEqualTo(datePath, java.sql.Date.valueOf(LocalDate.now().minus(period)));
    }

    public int instancesNotStoredOnExportStorage(Long studyPk, StorageDescriptor desc) {
        List<String> storageIDsOfCluster = getStorageIDsOfCluster(desc);
        LOG.debug("Query for Instances of Study[pk={}] on Storages{} not stored on Storage[{}]",
                studyPk, storageIDsOfCluster, desc.getExportStorageID());
        Set<Long> onStorage = new HashSet<>(em.createNamedQuery(Location.INSTANCE_PKS_BY_STUDY_PK_AND_STORAGE_IDS, Long.class)
                .setParameter(1, studyPk)
                .setParameter(2, storageIDsOfCluster)
                .getResultList());
        onStorage.removeAll(em.createNamedQuery(Location.INSTANCE_PKS_BY_STUDY_PK_AND_STORAGE_IDS_AND_STATUS, Long.class)
                .setParameter(1, studyPk)
                .setParameter(2, Collections.singletonList(desc.getExportStorageID()))
                .setParameter(3, Location.Status.OK)
                .getResultList());
        return onStorage.size();
    }

    public List<Series> findSeriesWithPurgedInstances(Long studyPk) {
        LOG.debug("Query for Series with purged Instance records of Study[pk={}]", studyPk);
        return em.createNamedQuery(Series.FIND_BY_STUDY_PK_AND_INSTANCE_PURGE_STATE, Series.class)
                .setParameter(1, studyPk)
                .setParameter(2, Series.InstancePurgeState.PURGED)
                .getResultList();
    }

    public boolean claimDeleteObject(Location location) {
        return em.createNamedQuery(Location.UPDATE_STATUS_FROM)
                .setParameter(1, location.getPk())
                .setParameter(2, Location.Status.TO_DELETE)
                .setParameter(3, Location.Status.FAILED_TO_DELETE)
                .executeUpdate() > 0;
    }

    public boolean claimResolveFailedToDelete(Location location) {
        return em.createNamedQuery(Location.UPDATE_STATUS_FROM)
                .setParameter(1, location.getPk())
                .setParameter(2, Location.Status.FAILED_TO_DELETE)
                .setParameter(3, Location.Status.FAILED_TO_DELETE2)
                .executeUpdate() > 0;
    }

    public boolean rescheduleDeleteMetadata(Metadata metadata) {
        return em.createNamedQuery(Metadata.UPDATE_STATUS_FROM)
                .setParameter(1, metadata.getPk())
                .setParameter(2, Metadata.Status.FAILED_TO_DELETE2)
                .setParameter(3, Metadata.Status.TO_DELETE)
                .executeUpdate() > 0;
    }

    public boolean rescheduleDeleteObject(Location location) {
        return em.createNamedQuery(Location.UPDATE_STATUS_FROM)
                .setParameter(1, location.getPk())
                .setParameter(2, Location.Status.FAILED_TO_DELETE2)
                .setParameter(3, Location.Status.TO_DELETE)
                .executeUpdate() > 0;
    }

    public List<Location> findLocationsForInstanceOnStorage(String iuid, String storageID) {
        return em.createNamedQuery(Location.FIND_BY_SOP_IUID_AND_STORAGE_ID)
                .setParameter(1, iuid)
                .setParameter(2, storageID)
                .getResultList();
    }

    public List<Metadata> findMetadataForSeriesOnStorage(String iuid, String storageID) {
        return em.createNamedQuery(Metadata.FIND_BY_SERIES_IUID_AND_STORAGE_ID)
                .setParameter(1, iuid)
                .setParameter(2, storageID)
                .getResultList();
    }

    public boolean claimDeleteMetadata(Metadata metadata) {
        return em.createNamedQuery(Metadata.UPDATE_STATUS_FROM)
                .setParameter(1, metadata.getPk())
                .setParameter(2, Metadata.Status.TO_DELETE)
                .setParameter(3, Metadata.Status.FAILED_TO_DELETE)
                .executeUpdate() > 0;
    }

    public boolean claimResolveFailedToDeleteMetadata(Metadata metadata) {
        return em.createNamedQuery(Metadata.UPDATE_STATUS_FROM)
                .setParameter(1, metadata.getPk())
                .setParameter(2, Metadata.Status.FAILED_TO_DELETE)
                .setParameter(3, Metadata.Status.FAILED_TO_DELETE2)
                .executeUpdate() > 0;
    }

    public void removeLocation(Location location) {
        em.createNamedQuery(Location.DELETE_BY_PK)
                .setParameter(1, location.getPk())
                .executeUpdate();
    }

    public void removeMetadata(Metadata metadata) {
        em.createNamedQuery(Metadata.DELETE_BY_PK)
                .setParameter(1, metadata.getPk())
                .executeUpdate();
    }

    public Study deleteStudy(StudyDeleteContext ctx) {
        Long studyPk = ctx.getStudyPk();
        List<Location> locations = em.createNamedQuery(Location.FIND_BY_STUDY_PK, Location.class)
                .setParameter(1, studyPk)
                .getResultList();
        return deleteStudy(removeOrMarkToDelete(locations, Integer.MAX_VALUE, false), ctx);
    }

    public boolean hasObjectsOnStorage(Long studyPk, StorageDescriptor desc) {
        Study study = em.find(Study.class, studyPk);
        return Stream.of(study.getStorageIDs())
                .anyMatch(storageID -> storageID.equals(desc.getStorageID()));
    }

    public boolean deleteObjectsOfStudy(Long studyPk, StorageDescriptor desc) {
        return deleteObjectsOfStudy(em.find(Study.class, studyPk), desc);
    }

    public boolean deleteObjectsOfStudy(String suid, StorageDescriptor desc) {
        return deleteObjectsOfStudy(findByStudyIUID(suid), desc);
    }

    private Study findByStudyIUID(String suid) {
        return em.createNamedQuery(Study.FIND_BY_STUDY_IUID, Study.class)
                .setParameter(1, suid)
                .getSingleResult();
    }

    private Series findBySeriesIUID(String studyUID, String seriesUID) {
        return em.createNamedQuery(Series.FIND_BY_SERIES_IUID, Series.class)
                .setParameter(1, studyUID)
                .setParameter(2, seriesUID)
                .getSingleResult();
    }

    private boolean deleteObjectsOfStudy(Study study, StorageDescriptor desc) {
        List<String> storageIDs = getStorageIDsOfCluster(desc);
        if (!Stream.of(study.getStorageIDs()).anyMatch(storageIDs::contains)) {
            LOG.info("{} does not contain objects at Storage{}", study, storageIDs);
            return false;
        }
        LOG.debug("Query for objects of {} at Storage{}", study, storageIDs);
        List<Location> locations = em.createNamedQuery(Location.FIND_BY_STUDY_PK_AND_STORAGE_IDS, Location.class)
                .setParameter(1, study.getPk())
                .setParameter(2, storageIDs)
                .getResultList();
        if (locations.isEmpty()) {
            LOG.warn("{} does not contain objects at Storage{}", study, storageIDs);
            updateStorageIDs(study, storageIDs);
            return false;
        }
        LOG.debug("Start marking {} objects of {} for deletion at Storage{}", locations.size(), study, storageIDs);
        Collection<Instance> insts = removeOrMarkToDelete(locations, Integer.MAX_VALUE, false);
        LOG.debug("Finish marking {}/{} objects/instances of {} for deletion at Storage{}",
                locations.size(), insts.size(), study, storageIDs);
        Set<Long> seriesPks = new HashSet<>();
        for (Instance inst : insts) {
            Series series = inst.getSeries();
            if (seriesPks.add(series.getPk())
                    && series.getMetadataScheduledUpdateTime() == null
                    && series.getMetadata() != null)
                scheduleMetadataUpdate(series.getPk());
        }
        updateStorageIDs(study, storageIDs);
        return true;
    }

    private void updateStorageIDs(Study study, List<String> storageIDs) {
        String studyEncodedStorageIDs = study.getEncodedStorageIDs();
        for (String storageID : storageIDs) {
            study.removeStorageID(storageID);
        }
        LOG.info("Update Storage IDs of {} from {} to {}", study, studyEncodedStorageIDs, study.getEncodedStorageIDs());
    }

    @FunctionalInterface
    interface DeleteRejectedInstancesOrRejectionNotes {
        int delete(Code rjCode, Date before, int fetchSize);
    }

    public int deleteRejectedInstances(Code rejectionCode, Date before, int limit) {
        return deleteRejectedInstancesOrRejectionNotes(
                before != null ? Location.FIND_BY_REJECTION_CODE_BEFORE : Location.FIND_BY_REJECTION_CODE,
                rejectionCode, before, limit);
    }

    public int deleteRejectionNotes(Code rejectionCode, Date before, int limit) {
        return deleteRejectedInstancesOrRejectionNotes(
                before != null ? Location.FIND_BY_CONCEPT_NAME_CODE_BEFORE : Location.FIND_BY_CONCEPT_NAME_CODE,
                rejectionCode, before, limit);
    }

    private int deleteRejectedInstancesOrRejectionNotes(String queryName, Code rejectionCode, Date before, int limit) {
        CodeEntity codeEntity = codeCache.findOrCreate(rejectionCode);
        TypedQuery<Location> query = em.createNamedQuery(queryName, Location.class).setParameter(1, codeEntity);
        if (before != null)
            query.setParameter(2, before);

        List<Location> locations = query.setMaxResults(limit).getResultList();
        if (!locations.isEmpty())
            deleteInstances(removeOrMarkToDelete(locations, limit, true));
        return locations.size();
    }

    public void deleteEmptyStudy(StudyDeleteContext ctx) {
        Study study = ctx.getStudy();
        study.getPatient().decrementNumberOfStudies();
        em.remove(em.contains(study) ? study : em.merge(study));
    }

    private Collection<Instance> removeOrMarkToDelete(List<Location> locations, int limit, boolean resetSize) {
        int size = locations.size();
        int initialCapacity = size * 4 / 3;
        HashMap<Long, Instance> insts = new HashMap<>(initialCapacity);
        HashMap<Long, UIDMap> uidMaps = new HashMap<>();
        Instance prev = null;
        int n = limit - (MAX_LOCATIONS_PER_INSTANCE - 1);
        for (Location location : locations) {
            Instance inst = location.getInstance();
            if (n-- <= 0 && prev != inst) // ensure to detach all locations of returned instances
                break;
            insts.put(inst.getPk(), prev = inst);
            UIDMap uidMap = location.getUidMap();
            if (uidMap != null)
                uidMaps.put(uidMap.getPk(), uidMap);

            if (resetSize) {
                Series series = inst.getSeries();
                series.resetSize();
                series.getStudy().resetSize();
            }
            storeEjb.removeOrMarkToDelete(location);
        }
        for (UIDMap uidMap : uidMaps.values())
            storeEjb.removeOrphaned(uidMap);
        return insts.values();
    }

    private void deleteInstances(Collection<Instance> insts) {
        HashMap<Long, Series> series = new HashMap<>();
        for (Instance inst : insts) {
            Series ser = inst.getSeries();
            if (!series.containsKey(ser.getPk())) {
                series.put(ser.getPk(), ser);
                deleteSeriesQueryAttributes(ser);
            }
            em.remove(inst);
            em.createNamedQuery(RejectedInstance.DELETE_BY_UIDS)
                    .setParameter(1, ser.getStudy().getStudyInstanceUID())
                    .setParameter(2, ser.getSeriesInstanceUID())
                    .setParameter(3, inst.getSopInstanceUID())
                    .executeUpdate();
        }
        HashMap<Long, Study> studies = new HashMap<>();
        for (Series ser : series.values()) {
            Study study = ser.getStudy();
            if (!studies.containsKey(study.getPk())) {
                studies.put(study.getPk(), study);
                deleteStudyQueryAttributes(study);
            }
            if (countInstancesOfSeries(ser) == 0) {
                if (ser.getMetadata() != null)
                    ser.getMetadata().setStatus(Metadata.Status.TO_DELETE);
                em.remove(ser);
            } else {
                studies.put(study.getPk(), null);
                if (ser.getRejectionState() == RejectionState.PARTIAL && !hasRejectedInstances(ser))
                    ser.setRejectionState(RejectionState.NONE);
            }
        }
        for (Study study : studies.values()) {
            if (study == null)
                continue;

            if (countSeriesOfStudy(study) == 0) {
                em.remove(study);
            } else {
                if (study.getRejectionState() == RejectionState.PARTIAL
                        && !hasSeriesWithOtherRejectionState(study, RejectionState.NONE))
                    study.setRejectionState(RejectionState.NONE);
            }
        }
    }

    private Study deleteStudy(Collection<Instance> insts, StudyDeleteContext ctx) {
        Patient patient = null;
        Study study = null;
        HashMap<Long, Series> series = new HashMap<>();
        for (Instance inst : insts) {
            Series ser = inst.getSeries();
            if (!series.containsKey(ser.getPk())) {
                series.put(ser.getPk(), ser);
            }
            ctx.addInstance(inst);
            em.remove(inst);
            em.createNamedQuery(RejectedInstance.DELETE_BY_UIDS)
                    .setParameter(1, ser.getStudy().getStudyInstanceUID())
                    .setParameter(2, ser.getSeriesInstanceUID())
                    .setParameter(3, inst.getSopInstanceUID())
                    .executeUpdate();
        }
        for (Series ser : series.values()) {
            if (study == null) {
                ctx.setStudy(study = ser.getStudy());
                ctx.setPatient(patient = study.getPatient());
            }
            if (ser.getMetadata() != null)
                ser.getMetadata().setStatus(Metadata.Status.TO_DELETE);
            em.remove(ser);
        }
        study.getPatient().decrementNumberOfStudies();
        em.remove(study);
        if (ctx.isDeletePatientOnDeleteLastStudy() && countStudiesOfPatient(patient) == 0) {
            PatientMgtContext patMgtCtx = patientService.createPatientMgtContextScheduler();
            patMgtCtx.setPatient(patient);
            patMgtCtx.setEventActionCode(AuditMessages.EventActionCode.Delete);
            patMgtCtx.setAttributes(patient.getAttributes());
            patMgtCtx.setPatientID(IDWithIssuer.pidOf(patient.getAttributes()));
            patientService.deletePatient(patMgtCtx);
        }
        return study;
    }

    private boolean hasRejectedInstances(Series series) {
        return em.createNamedQuery(Instance.COUNT_REJECTED_INSTANCES_OF_SERIES, Long.class)
                .setParameter(1, series)
                .getSingleResult() > 0;
    }

    private boolean hasSeriesWithOtherRejectionState(Study study, RejectionState rejectionState) {
        return em.createNamedQuery(Series.COUNT_SERIES_OF_STUDY_WITH_OTHER_REJECTION_STATE, Long.class)
                .setParameter(1, study)
                .setParameter(2, rejectionState)
                .getSingleResult() > 0;
    }

    private long countStudiesOfPatient(Patient patient) {
        return em.createNamedQuery(Study.COUNT_STUDIES_OF_PATIENT, Long.class).setParameter(1, patient)
                .getSingleResult();
    }

    private long countSeriesOfStudy(Study study) {
        return em.createNamedQuery(Series.COUNT_SERIES_OF_STUDY, Long.class).setParameter(1, study)
                .getSingleResult();
    }

    private long countInstancesOfSeries(Series series) {
        return em.createNamedQuery(Instance.COUNT_INSTANCES_OF_SERIES, Long.class).setParameter(1, series).
                getSingleResult();
    }

    private int deleteStudyQueryAttributes(Study study) {
        return em.createNamedQuery(StudyQueryAttributes.DELETE_FOR_STUDY).setParameter(1, study).executeUpdate();
    }

    private int deleteSeriesQueryAttributes(Series series) {
        return em.createNamedQuery(SeriesQueryAttributes.DELETE_FOR_SERIES).setParameter(1, series).executeUpdate();
    }

    public List<Series.MetadataUpdate> findSeriesToPurgeInstances(int fetchSize) {
        return em.createNamedQuery(Series.SCHEDULED_PURGE_INSTANCES, Series.MetadataUpdate.class)
                .setMaxResults(fetchSize)
                .getResultList();
    }

    public boolean purgeInstanceRecordsOfSeries(Long seriesPk, Map<String, List<Location>> locationsFromMetadata) {
        Series series = em.find(Series.class, seriesPk);
        List<Location> locations = em.createNamedQuery(Location.FIND_BY_SERIES_PK, Location.class)
                .setParameter(1, seriesPk)
                .getResultList();
        if (!verifyMetadata(locationsFromMetadata, locations)) {
            Date now = new Date();
            series.setMetadataScheduledUpdateTime(now);
            series.setInstancePurgeTime(now);
            return false;
        }
        calculateMissingSeriesQueryAttributes(seriesPk);
        Map<String,Long> sizeOfInst = new HashMap<>();
        for (Location location : locations) {
            switch (location.getObjectType()) {
                case DICOM_FILE:
                    sizeOfInst.merge(location.getInstance().getSopInstanceUID(), location.getSize(),
                            (v1,v2) -> Math.max(v1, v2));
                    em.remove(location);
                    em.remove(location.getInstance());
                    break;
                case METADATA:
                    location.setInstance(null);
                    location.setStatus(Location.Status.TO_DELETE);
                    break;
            }
        }
        series.setSize(sizeOfInst.values().stream().mapToLong(Long::longValue).sum());
        series.setInstancePurgeTime(null);
        series.setInstancePurgeState(Series.InstancePurgeState.PURGED);
        return true;
    }

    private boolean verifyMetadata(Map<String, List<Location>> locationsFromMetadata, List<Location> locations) {
        for (Location location : locations) {
            if (location.getObjectType() == Location.ObjectType.DICOM_FILE) {
                if (!verifyMetadata(locationsFromMetadata, location))
                    return false;
            }
        }
        return locationsFromMetadata.isEmpty();
    }

    private boolean verifyMetadata(Map<String, List<Location>> locationsFromMetadata, Location location) {
        String iuid = location.getInstance().getSopInstanceUID();
        List<Location> locations = locationsFromMetadata.get(iuid);
        if (locations == null || !locations.removeIf(l ->
            Objects.equals(location.getStorageID(), l.getStorageID())
                    && Objects.equals(location.getStoragePath(), l.getStoragePath())
                    && Objects.equals(location.getDigestAsHexString(), l.getDigestAsHexString())
                    && Objects.equals(location.getSize(), l.getSize())
                    && Objects.equals(location.getStatus(), l.getStatus())))
            return false;

        if (locations.isEmpty())
            locationsFromMetadata.remove(iuid);
        return true;
    }

    public boolean claimPurgeInstanceRecordsOfSeries(Series.MetadataUpdate metadataUpdate) {
        return em.createNamedQuery(Series.CLAIM_PURGE_INSTANCE_RECORDS)
                .setParameter(1, metadataUpdate.seriesPk)
                .setParameter(2, metadataUpdate.instancePurgeTime)
                .executeUpdate() > 0;
    }

    public boolean scheduleMetadataUpdate(Long seriesPk) {
        return em.createNamedQuery(Series.SCHEDULE_METADATA_UPDATE_FOR_SERIES)
                .setParameter(1, seriesPk)
                .executeUpdate() > 0;
    }

    public boolean updateInstancePurgeState(
            Long seriesPk, Series.InstancePurgeState from, Series.InstancePurgeState to) {
        return em.createNamedQuery(Series.UPDATE_INSTANCE_PURGE_STATE)
                .setParameter(1, seriesPk)
                .setParameter(2, from)
                .setParameter(3, to)
                .executeUpdate() > 0;
    }

    private void calculateMissingSeriesQueryAttributes(Long seriesPk) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        Set<String> viewIDs = new HashSet<>(arcDev.getQueryRetrieveViewIDs());
        viewIDs.removeAll(em.createNamedQuery(SeriesQueryAttributes.VIEW_IDS_FOR_SERIES_PK, String.class)
                .setParameter(1, seriesPk)
                .getResultList());
        for (String viewID : viewIDs) {
            queryService.calculateSeriesQueryAttributes(seriesPk, arcDev.getQueryRetrieveView(viewID));
        }
    }

    public void updateStudyAccessTime(Long studyPk) {
        em.createNamedQuery(Study.UPDATE_ACCESS_TIME)
                .setParameter(1, studyPk)
                .executeUpdate();
    }

    private List<String> getStorageIDsOfCluster(StorageDescriptor desc) {
        return desc.getStorageClusterID() != null
            ? device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class)
                .getStorageIDsOfCluster(desc.getStorageClusterID())
                .filter(storageID -> !storageID.equals(desc.getExportStorageID()))
                .collect(Collectors.toList())
            : Collections.singletonList(desc.getStorageID());
    }

    private List<String> getStudyStorageIDs(StorageDescriptor desc) {
        return desc.getStudyStorageIDs(
                device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class)
                        .getOtherStorageIDs(desc));
    }

    public List<Study> findExpiredStudies(int studyFetchSize) {
        return em.createNamedQuery(Study.GET_EXPIRED_STUDIES, Study.class)
                .setParameter(1, BASIC_ISO_DATE.format(LocalDate.now()))
                .setParameter(2, EnumSet.of(ExpirationState.UPDATEABLE, ExpirationState.FROZEN))
                .setMaxResults(studyFetchSize)
                .getResultList();
    }

    public List<Series> findExpiredSeries(int seriesFetchSize) {
        return em.createNamedQuery(Series.GET_EXPIRED_SERIES, Series.class)
                .setParameter(1, BASIC_ISO_DATE.format(LocalDate.now()))
                .setParameter(2, EnumSet.of(ExpirationState.UPDATEABLE, ExpirationState.FROZEN))
                .setMaxResults(seriesFetchSize)
                .getResultList();
    }

    public boolean claimExpiredStudyFor(Study study, ExpirationState expirationState) {
        return em.createNamedQuery(Study.CLAIM_EXPIRED_STUDY)
                .setParameter(1, study.getPk())
                .setParameter(2, study.getExpirationState())
                .setParameter(3, expirationState)
                .executeUpdate() > 0;
    }

    public boolean claimExpiredSeriesFor(Series series, ExpirationState expirationState) {
        return em.createNamedQuery(Series.CLAIM_EXPIRED_SERIES)
                .setParameter(1, series.getPk())
                .setParameter(2, series.getExpirationState())
                .setParameter(3, expirationState)
                .executeUpdate() > 0;
    }

    public boolean claimExpired(String studyIUID, String seriesIUID, ExpirationState expirationState) {
        return seriesIUID != null
                ? claimExpiredSeriesFor(findBySeriesIUID(studyIUID, seriesIUID), expirationState)
                : claimExpiredStudyFor(findByStudyIUID(studyIUID), expirationState);
    }

    public void scheduleRejection(String aet, String studyIUID, String seriesIUID, String sopIUID, Code code,
                                  HttpServletRequestInfo httpRequest, String batchID)
            throws QueueSizeLimitExceededException {
        try {
            ObjectMessage msg = queueManager.createObjectMessage("");
            msg.setStringProperty("LocalAET", aet);
            msg.setStringProperty("StudyInstanceUID", studyIUID);
            msg.setStringProperty("SeriesInstanceUID", seriesIUID);
            msg.setStringProperty("SOPInstanceUID", sopIUID);
            msg.setStringProperty("Code", code.toString());
            httpRequest.copyTo(msg);
            queueManager.scheduleMessage(RejectionService.QUEUE_NAME, msg, Message.DEFAULT_PRIORITY, batchID, 0L);
        } catch (JMSException e) {
            throw new JMSRuntimeException(e.getMessage(), e.getErrorCode(), e.getCause());
        }
    }
}
