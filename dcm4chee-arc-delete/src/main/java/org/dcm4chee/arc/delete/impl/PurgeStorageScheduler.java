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

package org.dcm4chee.arc.delete.impl;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.dict.archive.ArchiveTag;
import org.dcm4che3.json.JSONReader;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.util.SafeClose;
import org.dcm4chee.arc.Scheduler;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.BinaryPrefix;
import org.dcm4chee.arc.conf.Duration;
import org.dcm4chee.arc.conf.StorageDescriptor;
import org.dcm4chee.arc.delete.StudyDeleteContext;
import org.dcm4chee.arc.entity.Location;
import org.dcm4chee.arc.entity.Metadata;
import org.dcm4chee.arc.entity.Series;
import org.dcm4chee.arc.entity.Study;
import org.dcm4chee.arc.storage.ReadContext;
import org.dcm4chee.arc.storage.Storage;
import org.dcm4chee.arc.storage.StorageFactory;
import org.dcm4chee.arc.store.StoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.json.Json;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.zip.ZipInputStream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Oct 2015
 */
@ApplicationScoped
public class PurgeStorageScheduler extends Scheduler {

    private static final Logger LOG = LoggerFactory.getLogger(PurgeStorageScheduler.class);

    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    @Inject
    private Device device;

    @Inject
    private DeletionServiceEJB ejb;

    @Inject
    private StoreService storeService;

    @Inject
    private StorageFactory storageFactory;

    @Inject
    private Event<StudyDeleteContext> studyDeletedEvent;

    private Set<String> inProcess = Collections.synchronizedSet(new HashSet<>());

    protected PurgeStorageScheduler() {
        super(Mode.scheduleAtFixedRate);
    }

    @Override
    protected Logger log() {
        return LOG;
    }

    @Override
    protected Duration getPollingInterval() {
        return arcdev().getPurgeStoragePollingInterval();
    }

    private ArchiveDeviceExtension arcdev() {
        return device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
    }

    @Override
    protected void execute() {
        ArchiveDeviceExtension arcDev = arcdev();
        int fetchSize = arcDev.getPurgeStorageFetchSize();
        int deleteStudyBatchSize = arcDev.getDeleteStudyBatchSize();
        boolean deletePatient = arcDev.isDeletePatientOnDeleteLastStudy();
        for (StorageDescriptor desc : arcDev.getStorageDescriptors()) {
            if (desc.isReadOnly())
                continue;

            if (!inProcess.add(desc.getStorageID()))
                continue;

            try {
                long minUsableSpace = desc.hasDeleterThresholds() ? desc.getDeleterThresholdMinUsableSpace(Calendar.getInstance()) : -1L;
                long deleteSize = deleteSize(desc, minUsableSpace);
                if (deleteSize > 0L) {
                    LOG.info("Usable Space on {} below {} - start deleting {}", desc,
                            BinaryPrefix.formatDecimal(minUsableSpace), BinaryPrefix.formatDecimal(deleteSize));
                }
                for (int i = 0; i == 0 || deleteSize > 0L; i++) {
                    if (getPollingInterval() == null)
                        return;
                    if (deleteSize > 0L) {
                        if (deleteStudies(desc, deleteStudyBatchSize, deletePatient) == 0)
                            deleteSize = 0L;
                    }
                    while (deleteNextObjectsFromStorage(desc, fetchSize)) ;
                    if (deleteSize > 0L) {
                        deleteSize = deleteSize(desc, minUsableSpace);
                    }
                }
                while (deleteSize > 0L) ;
                while (deleteSeriesMetadata(desc, fetchSize)) ;
            } finally {
                inProcess.remove(desc.getStorageID());
            }
        }
    }

    private long deleteSize(StorageDescriptor desc, long minUsableSpace) {
        if (minUsableSpace < 0L)
            return 0L;

        try (Storage storage = storageFactory.getStorage(desc)) {
            return Math.max(0L, minUsableSpace - storage.getUsableSpace());
        } catch (IOException e) {
            LOG.warn("Failed to determine usable space on {}", desc, e);
            return 0;
        }
    }

    private int deleteStudies(StorageDescriptor desc, int fetchSize, boolean deletePatient) {
        List<Long> studyPks;
        try {
           studyPks = findStudiesForDeletion(desc, fetchSize);
        } catch (Exception e) {
            LOG.warn("Query for studies for deletion on {} failed", desc, e);
            return 0;
        }
        if (studyPks.isEmpty()) {
            LOG.warn("No studies for deletion found on {}", desc);
            return 0;
        }
        return desc.getExternalRetrieveAETitle() != null || desc.getExportStorageID() != null
                ? deleteObjectsOfStudies(desc, studyPks)
                : deleteStudiesFromDB(desc, studyPks, deletePatient);
    }

    private List<Long> findStudiesForDeletion(StorageDescriptor desc, int fetchSize) {
        List<Long> studyPks = desc.getExternalRetrieveAETitle() != null
                ? ejb.findStudiesForDeletionOnStorageWithExternalRetrieveAET(desc, fetchSize)
                : ejb.findStudiesForDeletionOnStorage(desc, fetchSize);

        String storageID = desc.getStorageID();
        String exportStorageID = desc.getExportStorageID();
        if (exportStorageID != null) {
            for (Iterator<Long> iter = studyPks.iterator(); iter.hasNext();) {
                Long studyPk = iter.next();
                int notStoredOnOtherStorage = ejb.instancesNotStoredOnExportStorage(studyPk, desc);
                Map<String,Storage> storageMap = new HashMap<>();
                List<Series> seriesWithPurgedInstances = null;
                try {
                    seriesWithPurgedInstances = ejb.findSeriesWithPurgedInstances(studyPk);
                    for (Series series : seriesWithPurgedInstances) {
                        Storage storage = getStorage(series.getMetadata().getStorageID(), storageMap);
                        ReadContext readContext = storage.createReadContext();
                        readContext.setStoragePath(series.getMetadata().getStoragePath());
                        notStoredOnOtherStorage += instancesNotStoredOnOtherStorage(readContext, storageID, exportStorageID);
                    }
                } finally {
                    for (Storage storage : storageMap.values()) {
                        SafeClose.close(storage);
                    }
                }
                if (notStoredOnOtherStorage > 0) {
                    LOG.warn("{} of instances of Study[pk={}] on {} not stored on Storage[id={}] - defer deletion of objects",
                            notStoredOnOtherStorage, studyPk, desc, exportStorageID);
                    ejb.updateStudyAccessTime(studyPk);
                    iter.remove();
                } else if (!seriesWithPurgedInstances.isEmpty()){
                    ApplicationEntity ae = device.getApplicationEntities().iterator().next();
                    Duration purgeInstanceRecordsDelay = device.getDeviceExtension(ArchiveDeviceExtension.class)
                            .getPurgeInstanceRecordsDelay();
                    for (Series series : seriesWithPurgedInstances) {
                        try {
                            storeService.restoreInstances(storeService.newStoreSession(ae, storageID),
                                    series.getStudy().getStudyInstanceUID(),
                                    series.getSeriesInstanceUID(),
                                    purgeInstanceRecordsDelay);
                        } catch (Exception e) {
                            LOG.warn("Failed to restore Instance records of Series[pk={}] - defer deletion of objects from Storage[id={}]\n",
                                    series.getPk(), desc, e);
                            ejb.updateStudyAccessTime(studyPk);
                            iter.remove();
                            break;
                        }
                    }
                }
            }
        }
        return studyPks;
    }

    private Storage getStorage(String storageID, Map<String,Storage> storageMap) {
        Storage storage = storageMap.get(storageID);
        if (storage == null) {
            storageMap.put(storageID,
                    storage = storageFactory.getStorage(arcdev().getStorageDescriptorNotNull(storageID)));
        }
        return storage;
    }

    private static int instancesNotStoredOnOtherStorage(ReadContext ctx, String storageID, String exportStorageID) {
        int count = 0;
        try (InputStream in = ctx.getStorage().openInputStream(ctx)) {
            ZipInputStream zip = new ZipInputStream(in);
            while (zip.getNextEntry() != null) {
                JSONReader jsonReader = new JSONReader(Json.createParser(
                        new InputStreamReader(zip, "UTF-8")));
                Attributes metadata = jsonReader.readDataset(null);
                if (containsStorageID(metadata, storageID) && !containsStorageID(metadata, exportStorageID))
                    count++;
                zip.closeEntry();
            }
        } catch (Exception e) {
            LOG.error("Failed to read Metadata {} from {}",
                    ctx.getStoragePath(), ctx.getStorage().getStorageDescriptor());
            count++;
        }
        return count;
    }

    private static boolean containsStorageID(Attributes attrs, String storageID) {
        if (matchStorageID(attrs, storageID))
            return true;

        Sequence otherStorageSeq = attrs.getSequence(ArchiveTag.PrivateCreator, ArchiveTag.OtherStorageSequence);
        if (otherStorageSeq != null)
            for (Attributes otherStorageItem : otherStorageSeq)
                if (matchStorageID(otherStorageItem, storageID))
                    return true;

        return false;
    }

    private static boolean matchStorageID(Attributes attrs, String storageID) {
        return storageID.equals(attrs.getString(ArchiveTag.PrivateCreator, ArchiveTag.StorageID));
    }

    private int deleteStudiesFromDB(StorageDescriptor desc, List<Long> studyPks, boolean deletePatient) {
        int removed = 0;
        for (Long studyPk : studyPks) {
            if (getPollingInterval() == null)
                break;
            StudyDeleteContextImpl ctx = new StudyDeleteContextImpl(studyPk);
            ctx.setDeletePatientOnDeleteLastStudy(deletePatient);
            try {
                Study study = ejb.deleteStudy(ctx);
                removed++;
                LOG.info("Successfully delete {} on {}", study, desc);
            } catch (Exception e) {
                LOG.warn("Failed to delete Study[pk={}] on {}", studyPk, desc, e);
                ctx.setException(e);
            } finally {
                try {
                    studyDeletedEvent.fire(ctx);
                } catch (Exception e) {
                    LOG.warn("Unexpected exception in audit : " + e.getMessage());
                }
            }
        }
        return removed;
    }

    private int deleteObjectsOfStudies(StorageDescriptor desc, List<Long> studyPks) {
        int removed = 0;
        for (Long studyPk : studyPks) {
            if (getPollingInterval() == null)
                break;
            try {
                Study study = ejb.deleteObjectsOfStudy(studyPk, desc);
                removed++;
                LOG.info("Successfully delete objects of {} on {}", study, desc);
            } catch (Exception e) {
                LOG.warn("Failed to delete objects of Study[pk={}] on {}", studyPk, desc, e);
            }
        }
        return removed;
    }

    private boolean deleteSeriesMetadata(StorageDescriptor desc, int fetchSize) {
        List<Metadata> metadata = ejb.findMetadataToDelete(desc.getStorageID(), fetchSize);
        if (metadata.isEmpty())
            return false;

        try (Storage storage = storageFactory.getStorage(desc)) {
            for (Metadata m : metadata) {
                if (getPollingInterval() == null)
                    return false;
                try {
                    storage.deleteObject(m.getStoragePath());
                    ejb.removeMetadata(m);
                    LOG.debug("Successfully delete {} from {}", m, desc);
                } catch (Exception e) {
                    ejb.failedToDelete(m);
                    LOG.warn("Failed to delete {} from {}", m, desc, e);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to access {}", desc, e);
        }
        return metadata.size() == fetchSize;
    }

    private boolean deleteNextObjectsFromStorage(StorageDescriptor desc, int fetchSize) {
        List<Location> locations = ejb.findLocationsToDelete(desc.getStorageID(), fetchSize);
        if (locations.isEmpty())
            return false;

        try (Storage storage = storageFactory.getStorage(desc)) {
            for (Location location : locations) {
                if (getPollingInterval() == null)
                    return false;
                try {
                    storage.deleteObject(location.getStoragePath());
                    ejb.removeLocation(location);
                    LOG.debug("Successfully delete {} from {}", location, desc);
                } catch (Exception e) {
                    ejb.failedToDelete(location);
                    LOG.warn("Failed to delete {} from {}", location, desc, e);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to access {}", desc, e);
        }
        return locations.size() == fetchSize;
    }

}
