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
import org.dcm4chee.arc.ArchiveServiceEvent;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.Duration;
import org.dcm4chee.arc.conf.StorageDescriptor;
import org.dcm4chee.arc.entity.Location;
import org.dcm4chee.arc.storage.Storage;
import org.dcm4chee.arc.storage.StorageFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
@ApplicationScoped
public class Deleter {

    private static final Logger LOG = LoggerFactory.getLogger(Deleter.class);

    @Inject
    private Device device;

    @Inject
    private DeleterEJB ejb;

    @Inject
    private StorageFactory storageFactory;

    private final HashMap<String,Task> tasks = new HashMap<>();

    public void onArchiveServiceEvent(@Observes ArchiveServiceEvent event) {
        switch (event) {
            case STARTED:
                start();
                break;
            case STOPPED:
                stop();
                break;
            case RELOADED:
                reload();
                break;
        }
    }

    private void start() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        for (StorageDescriptor desc : arcDev.getStorageDescriptors())
            start(desc);
    }

    private void start(final StorageDescriptor desc) {
        Duration pollingIntervall = desc.getDeletionPollingInterval();
        if (pollingIntervall != null) {
            ScheduledFuture<?> task = device.scheduleWithFixedDelay(new Runnable() {
                @Override
                public void run() {
                    try {
                        while (deleteNext(desc))
                            ;
                    } catch (IOException e) {
                        LOG.error("Failed to delete objects from {}", desc.getStorageURI(), e);
                    }
                }
            }, 0, pollingIntervall.getSeconds(), TimeUnit.SECONDS);
            tasks.put(desc.getStorageID(), new Task(desc.getStorageID(), pollingIntervall, task));
        }
    }

    private boolean deleteNext(StorageDescriptor desc) throws IOException {
        List<Location> locations = ejb.findLocationsToDelete(desc);
        if (locations.isEmpty())
            return false;

        try (Storage storage = storageFactory.getStorage(desc)) {
            for (Location location : locations) {
                try {
                    storage.deleteObject(location.getStoragePath());
                    ejb.remove(location);
                    LOG.info("Successfully delete {} from {}", location, desc.getStorageURI());
                } catch (IOException e) {
                    ejb.failedToDelete(location);
                    LOG.warn("Failed to delete {} from {}", location, desc.getStorageURI(), e);
                }
            }
        }
        return locations.size() == desc.getDeletionTaskSize();
    }

    private void stop() {
        for(Iterator<Task> it = tasks.values().iterator(); it.hasNext();) {
            Task next = it.next();
            next.task.cancel(false);
            it.remove();
        }
    }

    private void reload() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        for(Iterator<Task> it = tasks.values().iterator(); it.hasNext();) {
            Task next = it.next();
            StorageDescriptor desc = arcDev.getStorageDescriptor(next.storageID);
            if (desc == null || !next.pollingIntervall.equals(desc.getDeletionPollingInterval())) {
                next.task.cancel(false);
                it.remove();
            }
        }
        for (StorageDescriptor desc : arcDev.getStorageDescriptors())
            if (!tasks.containsKey(desc.getStorageID()))
                start(desc);
    }

    private static class Task {
        String storageID;
        Duration pollingIntervall;
        ScheduledFuture<?> task;

        public Task(String storageID, Duration pollingIntervall, ScheduledFuture<?> task) {
            this.storageID = storageID;
            this.pollingIntervall = pollingIntervall;
            this.task = task;
        }
    }

}
