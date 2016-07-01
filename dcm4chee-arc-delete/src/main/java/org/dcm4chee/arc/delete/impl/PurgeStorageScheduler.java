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
import org.dcm4chee.arc.conf.BinaryPrefix;
import org.dcm4chee.arc.conf.Duration;
import org.dcm4chee.arc.conf.StorageDescriptor;
import org.dcm4chee.arc.delete.StudyDeleteContext;
import org.dcm4chee.arc.entity.Location;
import org.dcm4chee.arc.storage.Storage;
import org.dcm4chee.arc.storage.StorageFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import java.io.IOException;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
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

    @Inject
    private Event<StudyDeleteContext> studyDeletedEvent;

    protected PurgeStorageScheduler() {
        super(Mode.scheduleWithFixedDelay);
    }

    @Override
    protected Logger log() {
        return LOG;
    }

    @Override
    protected Duration getPollingInterval() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        return arcDev.getPurgeStoragePollingInterval();
    }

    @Override
    protected void execute() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        int fetchSize = arcDev.getPurgeStorageFetchSize();
        int deleteStudyBatchSize = arcDev.getDeleteStudyBatchSize();
        boolean deletePatient = arcDev.isDeletePatientOnDeleteLastStudy();
        for (StorageDescriptor desc : arcDev.getStorageDescriptors()) {
            if (desc.isReadOnly())
                continue;

            long minUsableSpace = desc.hasDeleterThresholds() ? desc.getMinUsableSpace(Calendar.getInstance()) : -1L;
            long deleteSize = deleteSize(desc, minUsableSpace);
            List<Long> studyPks = Collections.emptyList();
            if (deleteSize > 0L) {
                LOG.info("Usable Space on {} below {} - start deleting {}", desc.getStorageURI(),
                        BinaryPrefix.formatDecimal(minUsableSpace), BinaryPrefix.formatDecimal(deleteSize));
            }
            for (int i = 0; i == 0 || deleteSize > 0L; i++) {
                if (deleteSize > 0) {
                    studyPks = deleteStudy(studyPks, desc, deleteStudyBatchSize, deletePatient);
                    if (studyPks == null)
                        deleteSize = 0L;
                }
                try {
                    while (deleteNextObjectsFromStorage(desc, fetchSize))
                        ;
                } catch (IOException e) {
                    LOG.error("Failed to delete objects from {}", desc.getStorageURI(), e);
                }
                if (deleteSize > 0L)
                    deleteSize = deleteSize(desc, minUsableSpace);
            } while (deleteSize > 0L);
        }
    }

    private long deleteSize(StorageDescriptor desc, long minUsableSpace) {
        if (minUsableSpace < 0L)
            return 0L;

        try (Storage storage = storageFactory.getStorage(desc)) {
            return Math.max(0L, minUsableSpace - storage.getUsableSpace());
        } catch (IOException e) {
            LOG.warn("Failed to determine usable space on {}", desc.getStorageURI(), e);
            return 0;
        }
    }

    private List<Long> deleteStudy(List<Long> studyPks, StorageDescriptor desc, int fetchSize, boolean deletePatient) {
        boolean studyRemoved = false;
        while (!studyRemoved) {
            if (studyPks.isEmpty()) {
                try {
                    studyPks = ejb.findStudiesForDeletionOnStorage(desc.getStorageID(), fetchSize);
                } catch (Exception e) {
                    LOG.warn("Query for studies for deletion on {} failed", desc.getStorageURI(), e);
                    return null;
                }
                if (studyPks.isEmpty()) {
                    LOG.warn("No studies for deletion found on {}", desc.getStorageURI());
                    return null;
                }
            }
            Long studyPk = studyPks.remove(0);
            StudyDeleteContextImpl ctx = new StudyDeleteContextImpl(studyPk, null);
            ctx.setDeletePatientOnDeleteLastStudy(deletePatient);
            try {
                studyRemoved = ejb.removeStudyOnStorage(ctx);
                if (studyRemoved) {
                    LOG.info("Successfully delete {} on {} from database", ctx.getStudy(), desc.getStorageURI());
                } else {
                    LOG.warn("Failed to delete {} on {}", ctx.getStudy(), desc.getStorageURI(), ctx.getException());
                }
                studyDeletedEvent.fire(ctx);
            } catch (Exception e) {
                LOG.warn("Failed to delete {} on {}", ctx.getStudy(), desc.getStorageURI(), e);
                ctx.setException(e);
                studyDeletedEvent.fire(ctx);
                return null;
            }
        }
        return studyPks;
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
