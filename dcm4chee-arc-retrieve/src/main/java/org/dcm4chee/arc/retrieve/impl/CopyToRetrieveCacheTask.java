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

import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.StorageDescriptor;
import org.dcm4chee.arc.entity.Location;
import org.dcm4chee.arc.store.InstanceLocations;
import org.dcm4chee.arc.retrieve.LocationInputStream;
import org.dcm4chee.arc.storage.Storage;
import org.dcm4chee.arc.storage.WriteContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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

    public CopyToRetrieveCacheTask(RetrieveContextImpl ctx, InstanceLocations match) {
        this.ctx = ctx;
        this.arcdev = ctx.getRetrieveService().getArchiveDeviceExtension();
        StorageDescriptor storageDescriptor = arcdev.getStorageDescriptor(match.getLocations().get(0).getStorageID());
        this.storageID = storageDescriptor.getRetrieveCacheStorageID();
        this.maxParallel = storageDescriptor.getRetrieveCacheMaxParallel();
        this.semaphore = new Semaphore(maxParallel);
    }

    public void schedule(InstanceLocations match) {
        scheduled.offer(new WrappedInstanceLocations(match));
    }

    @Override
    public void run() {
        try {
            InstanceLocations instanceLocations;
            while ((instanceLocations = scheduled.take().instanceLocations) != null) {
                final InstanceLocations inst = instanceLocations;
                semaphore.acquire();
                arcdev.getDevice().execute(() -> {
                    try {
                        if (copy(inst))
                            completed.offer(new WrappedInstanceLocations(inst));
                    } finally {
                        semaphore.release();
                    }
                });
            }
            semaphore.acquire(maxParallel);
        } catch (InterruptedException e) {
            LOG.error("Failed to schedule copy to retrieve cache:", e);
        }
        completed.offer(new WrappedInstanceLocations(null));
    }

    private boolean copy(InstanceLocations match) {
        Storage storage = ctx.getRetrieveService().getStorage(storageID, ctx);
        WriteContext writeCtx = storage.createWriteContext();
        writeCtx.setAttributes(match.getAttributes());
        Location location = null;
        try {
            LOG.debug("Start copying {} to {}:\n", match, storage.getStorageDescriptor());
            location = copyTo(match, storage, writeCtx);
            ctx.getRetrieveService().getStoreService().addLocation(match.getInstancePk(), location);
            storage.commitStorage(writeCtx);
            match.getLocations().add(location);
            LOG.debug("Finished copying {} to {}:\n", match, storage.getStorageDescriptor());
            return true;
        } catch (Exception e) {
            LOG.warn("Failed to copy {} to {}:\n", match, storage.getStorageDescriptor(), e);
            ctx.addFailedSOPInstanceUID(match.getSopInstanceUID());
            if (location != null)
                try {
                    storage.revokeStorage(writeCtx);
                } catch (Exception e1) {
                    LOG.warn("Failed to revoke storage", e1);
                }
            return false;
        }
    }

    private Location copyTo(InstanceLocations match, Storage storage, WriteContext writeCtx) throws IOException {
        try (LocationInputStream locationInputStream = ctx.getRetrieveService().openLocationInputStream(
                ctx, match)) {
            storage.copy(locationInputStream.stream, writeCtx);
            return new Location.Builder()
                    .storageID(storage.getStorageDescriptor().getStorageID())
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
            LOG.debug("Got next finished copy to retrieve cache");
            return inst;
        } catch (InterruptedException e) {
            LOG.error("Failed to wait for next finished copy to retrieve cache:", e);
            return null;
        }
    }

    private class WrappedInstanceLocations {
        final InstanceLocations instanceLocations;

        private WrappedInstanceLocations(InstanceLocations instanceLocations) {
            this.instanceLocations = instanceLocations;
        }
    }
}
