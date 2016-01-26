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

package org.dcm4chee.arc.delete.impl;

import org.dcm4che3.net.Device;
import org.dcm4chee.arc.Scheduler;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.Duration;
import org.dcm4chee.arc.conf.StorageDescriptor;
import org.dcm4chee.arc.entity.Location;
import org.dcm4chee.arc.entity.Study;
import org.dcm4chee.arc.storage.Storage;
import org.dcm4chee.arc.storage.StorageFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.util.Calendar;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
@ApplicationScoped
public class PurgeStorageScheduler extends Scheduler {

    private static final Logger LOG = LoggerFactory.getLogger(PurgeStorageScheduler.class);

    @Inject
    private Device device;

    @Inject
    private DeletionServiceEJB ejb;

    @Inject
    private StorageFactory storageFactory;

    @Override
    protected Duration getPollingInterval() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        return arcDev.getPurgeStoragePollingInterval();
    }

    @Override
    public void run() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        int fetchSize = arcDev.getPurgeStorageFetchSize();
        int deleteStudyBatchSize = arcDev.getDeleteStudyBatchSize();
        boolean deletePatient = arcDev.isDeletePatientOnDeleteLastStudy();
        for (StorageDescriptor desc : arcDev.getStorageDescriptors()) {
            int deletedStudies = 0;
            do {
                try {
                    deletedStudies = deleteStudiesIfDeleterThresholdExceeded(desc, deleteStudyBatchSize, deletePatient);
                } catch (IOException e) {
                    LOG.error("Failed to delete studies from {}", desc.getStorageURI(), e);
                }
                try {
                    while (deleteNextObjectsFromStorage(desc, fetchSize))
                        ;
                } catch (IOException e) {
                    LOG.error("Failed to delete objects from {}", desc.getStorageURI(), e);
                }
            } while (deletedStudies == deleteStudyBatchSize);
        }
    }

    private int deleteStudiesIfDeleterThresholdExceeded(StorageDescriptor desc, int fetchSize, boolean deletePatient)
            throws IOException {
        if (!desc.hasDeleterThresholds())
            return 0;

        long minUsableSpace = desc.getMinUsableSpace(Calendar.getInstance());
        if (minUsableSpace == -1L)
            return 0;

        long usableSpace;
        try (Storage storage = storageFactory.getStorage(desc)) {
            usableSpace = storage.getUsableSpace();
        }
        if (usableSpace > minUsableSpace)
            return 0;

        String storageID = desc.getStorageID();
        int deleted = 0;
        do {
            List<Long> studyPks = ejb.findStudiesForDeletionOnStorage(storageID, fetchSize);
            if (studyPks.isEmpty()) {
                LOG.warn("No studies for deletion found on {} - usableSpace[{}] > minUsableSpace[{}]!",
                        desc.getStorageURI(), usableSpace, minUsableSpace);

                return 0;
            }
            for (Long studyPk : studyPks) {
                Study study = ejb.removeStudyOnStorage(studyPk, storageID, deletePatient);
                if (study != null) {
                    deleted++;
                    LOG.info("Successfully delete {} on {} from database", study, desc.getStorageURI());
                }
            }
        } while (deleted == 0);
        return deleted;
    }

    private boolean deleteNextObjectsFromStorage(StorageDescriptor desc, int fetchSize) throws IOException {
        List<Location> locations = ejb.findLocationsToDelete(desc.getStorageID(), fetchSize);
        if (locations.isEmpty())
            return false;

        try (Storage storage = storageFactory.getStorage(desc)) {
            for (Location location : locations) {
                try {
                    storage.deleteObject(location.getStoragePath());
                    ejb.removeLocation(location);
                    LOG.debug("Successfully delete {} from {}", location, desc.getStorageURI());
                } catch (IOException e) {
                    ejb.failedToDelete(location);
                    LOG.warn("Failed to delete {} from {}", location, desc.getStorageURI(), e);
                }
            }
        }
        return locations.size() == fetchSize;
    }

}
