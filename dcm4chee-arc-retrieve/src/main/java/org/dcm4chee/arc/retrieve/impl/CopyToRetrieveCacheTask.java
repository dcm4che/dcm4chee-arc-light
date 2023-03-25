/*
 * **** BEGIN LICENSE BLOCK *****
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
 * Portions created by the Initial Developer are Copyright (C) 2015-2018
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
 * **** END LICENSE BLOCK *****
 *
 */

package org.dcm4chee.arc.retrieve.impl;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.StorageDescriptor;
import org.dcm4chee.arc.entity.Location;
import org.dcm4chee.arc.retrieve.LocationInputStream;
import org.dcm4chee.arc.storage.ReadContext;
import org.dcm4chee.arc.storage.Storage;
import org.dcm4chee.arc.storage.WriteContext;
import org.dcm4chee.arc.store.InstanceLocations;
import org.dcm4chee.arc.store.StoreService;
import org.dcm4chee.arc.store.StoreSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since May 2018
 */
public class CopyToRetrieveCacheTask implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(CopyToRetrieveCacheTask.class);

    private final RetrieveContextImpl ctx;
    private final ArchiveDeviceExtension arcdev;
    private final String storageID;
    private final int maxParallel;
    private final Semaphore semaphore;
    private final LinkedBlockingQueue<WrappedInstanceLocations> scheduled = new LinkedBlockingQueue();
    private final LinkedBlockingQueue<WrappedInstanceLocations> completed = new LinkedBlockingQueue();
    private final Map<String,Set<String>> uidMap = new HashMap<>();
    private final Map<String,Map<String,Map<String, InstanceLocations>>> matchesByTar;

    public CopyToRetrieveCacheTask(RetrieveContextImpl ctx, StorageDescriptor storageDescriptor) {
        this.ctx = ctx;
        this.arcdev = ctx.getRetrieveService().getArchiveDeviceExtension();
        this.storageID = storageDescriptor.getRetrieveCacheStorageID();
        this.maxParallel = storageDescriptor.getRetrieveCacheMaxParallel();
        this.semaphore = new Semaphore(maxParallel);
        this.matchesByTar = storageDescriptor.isTarArchiver() ? new HashMap<>() : null;
    }

    public boolean schedule(InstanceLocations match) {
        if (matchesByTar == null) {
            scheduled.offer(new WrappedInstanceLocations(match));
        } else {
            Location location = match.getLocations().get(0);
            String storagePath = location.getStoragePath();
            int tarPathEnd = storagePath.indexOf('!');
            if (tarPathEnd < 0) return false;
            matchesByTar.computeIfAbsent(location.getStorageID(), storageID -> new HashMap<>())
                    .computeIfAbsent(storagePath.substring(0, tarPathEnd), tarPath -> new HashMap<>())
                    .put(storagePath.substring(tarPathEnd + 1), match);
        }
        return true;
    }

    boolean isTarArchiver() {
        return matchesByTar != null;
    }

    @Override
    public void run() {
        if (matchesByTar != null) {
            matchesByTar.forEach((storageID, matchesByTar) -> {
                        Storage storage = ctx.getRetrieveService().getStorage(storageID, ctx);
                        matchesByTar.forEach((tarPath, matchByTarEntry) ->
                                untarFiles(storage, tarPath, matchByTarEntry));
                    });
        } else {
            copyFiles();
        }
        StoreService storeService = ctx.getRetrieveService().getStoreService();
        for (Map.Entry<String, Set<String>> entry : uidMap.entrySet()) {
            String studyIUID = entry.getKey();
            storeService.addStorageID(studyIUID, storageID);
            for (String seriesIUID : entry.getValue()) {
                storeService.scheduleMetadataUpdate(studyIUID, seriesIUID);
            }
        }
        completed.offer(new WrappedInstanceLocations(null));
        ctx.getRetrieveService().updateLocations(ctx);
        LOG.debug("Leave run()");
    }

    private void untarFiles(Storage tarStorage, String tarPath, Map<String, InstanceLocations> matchByTarEntry) {
        ReadContext readContext = tarStorage.createReadContext();
        readContext.setStoragePath(tarPath);
        try (TarArchiveInputStream tar = new TarArchiveInputStream(tarStorage.openInputStream(readContext))) {
            TarArchiveEntry tarEntry;
            while ((tarEntry = tar.getNextTarEntry()) != null) {
                InstanceLocations inst = matchByTarEntry.remove(tarEntry.getName());
                if (inst != null) {
                    if (copy(tarStorage, tar, tarEntry, inst)) {
                        completed(inst);
                    }
                }
            }
        } catch (IOException e) {
            ctx.addFailuresOnCopyToRetrieveCache(matchByTarEntry.size());
        }
    }

    private boolean copy(Storage tarStorage, TarArchiveInputStream tar, TarArchiveEntry tarEntry,
                           InstanceLocations match) throws IOException {
        Storage storage = ctx.getRetrieveService().getStorage(storageID, ctx);
        WriteContext writeCtx = storage.createWriteContext(match.getAttributes());
        writeCtx.setContentLength(tarEntry.getSize());
        try {
            LOG.debug("Start copying {} to {}", match, storage.getStorageDescriptor());
            Location location = copyTo(tarStorage, tar, match, storage, writeCtx);
            addLocation(match, location);
            storage.commitStorage(writeCtx);
            LOG.debug("Finished copying {} to {}:\n", match, storage.getStorageDescriptor());
            return true;
        } catch (Exception e) {
            LOG.warn("Failed to copy {} to {}:\n", match, storage.getStorageDescriptor(), e);
            try {
                storage.revokeStorage(writeCtx);
            } catch (Exception e1) {
                LOG.warn("Failed to revoke storage", e1);
            }
            ctx.incrementFailuresOnCopyToRetrieveCache();
            return false;
        }
    }

    private void completed(InstanceLocations inst) {
        String studyIUID = inst.getAttributes().getString(Tag.StudyInstanceUID);
        String seriesIUID = inst.getAttributes().getString(Tag.SeriesInstanceUID);
        synchronized (uidMap) {
            uidMap.computeIfAbsent(studyIUID, key -> new HashSet<>()).add(seriesIUID);
        }
        completed.offer(new WrappedInstanceLocations(inst));
    }

    private void addLocation(InstanceLocations match, Location location) {
        StoreService storeService = ctx.getRetrieveService().getStoreService();
        ApplicationEntity ae = ctx.getLocalApplicationEntity();
        StoreSession storeSession = storeService.newStoreSession(ae).withObjectStorageID(storageID);
        storeService.addLocation(storeSession, match.getInstancePk(), location);
        match.getLocations().add(location);
    }

    private Location copyTo(Storage tarStorage, TarArchiveInputStream tar, InstanceLocations match,
                            Storage storage, WriteContext writeCtx) throws IOException {
        storage.copy(tar, writeCtx);
        StorageDescriptor retrieveCache = storage.getStorageDescriptor();
        if (ctx.getUpdateInstanceAvailability() == null
                && retrieveCache.getInstanceAvailability().compareTo(
                tarStorage.getStorageDescriptor().getInstanceAvailability()) < 0) {
            ctx.setUpdateInstanceAvailability(retrieveCache.getInstanceAvailability());
        }
        Location tarLocation = match.getLocations().get(0);
        return new Location.Builder()
                .storageID(retrieveCache.getStorageID())
                .storagePath(writeCtx.getStoragePath())
                .transferSyntaxUID(tarLocation.getTransferSyntaxUID())
                .objectType(Location.ObjectType.DICOM_FILE)
                .size(tarLocation.getSize())
                .digest(tarLocation.getDigest())
                .build();
    }

    private void copyFiles() {
        try {
            InstanceLocations instanceLocations;
            while ((instanceLocations = scheduled.take().instanceLocations) != null) {
                final InstanceLocations inst = instanceLocations;
                semaphore.acquire();
                arcdev.getDevice().execute(() -> {
                    try {
                        if (copy(inst)) {
                            completed(inst);
                        }
                    } finally {
                        semaphore.release();
                    }
                });
            }
            LOG.debug("Wait for finishing copying {} instances to retrieve cache",
                    maxParallel - semaphore.availablePermits());
            semaphore.acquire(maxParallel);
            LOG.debug("All instances copied to retrieve cache");
        } catch (InterruptedException e) {
            LOG.error("Failed to schedule copy to retrieve cache:\n", e);
        }
    }

    private boolean copy(InstanceLocations match) {
        Storage storage = ctx.getRetrieveService().getStorage(storageID, ctx);
        WriteContext writeCtx = storage.createWriteContext(match.getAttributes());
        try {
            LOG.debug("Start copying {} to {}", match, storage.getStorageDescriptor());
            Location location = copyTo(match, storage, writeCtx);
            addLocation(match, location);
            storage.commitStorage(writeCtx);
            LOG.debug("Finished copying {} to {}:\n", match, storage.getStorageDescriptor());
            return true;
        } catch (Exception e) {
            LOG.warn("Failed to copy {} to {}:\n", match, storage.getStorageDescriptor(), e);
            try {
                storage.revokeStorage(writeCtx);
            } catch (Exception e1) {
                LOG.warn("Failed to revoke storage", e1);
            }
            ctx.incrementFailuresOnCopyToRetrieveCache();
            return false;
        }
    }

    private Location copyTo(InstanceLocations match, Storage storage, WriteContext writeCtx) throws IOException {
        try (LocationInputStream locationInputStream = ctx.getRetrieveService().openLocationInputStream(
                ctx, match)) {
            writeCtx.setContentLength(locationInputStream.location.getSize());
            storage.copy(locationInputStream.stream, writeCtx);
            StorageDescriptor retrieveCache = storage.getStorageDescriptor();
            if (ctx.getUpdateInstanceAvailability() == null
                    && retrieveCache.getInstanceAvailability().compareTo(
                        locationInputStream.ctx.getStorage().getStorageDescriptor().getInstanceAvailability()) < 0) {
                ctx.setUpdateInstanceAvailability(retrieveCache.getInstanceAvailability());
            }
            return new Location.Builder()
                    .storageID(retrieveCache.getStorageID())
                    .storagePath(writeCtx.getStoragePath())
                    .transferSyntaxUID(locationInputStream.location.getTransferSyntaxUID())
                    .objectType(Location.ObjectType.DICOM_FILE)
                    .size(locationInputStream.location.getSize())
                    .digest(locationInputStream.location.getDigest())
                    .build();
        }
    }

    public InstanceLocations copiedToRetrieveCache() {
        try {
            LOG.debug("Wait for next finished copy to retrieve cache");
            InstanceLocations inst = completed.take().instanceLocations;
            if (inst == null)
                LOG.debug("No more copy to retrieve cache");
            else
                LOG.debug("Got next finished copy to retrieve cache");
            return inst;
        } catch (InterruptedException e) {
            LOG.error("Failed to wait for next finished copy to retrieve cache:", e);
            return null;
        }
    }

    private static class WrappedInstanceLocations {
        final InstanceLocations instanceLocations;

        private WrappedInstanceLocations(InstanceLocations instanceLocations) {
            this.instanceLocations = instanceLocations;
        }
    }
}
