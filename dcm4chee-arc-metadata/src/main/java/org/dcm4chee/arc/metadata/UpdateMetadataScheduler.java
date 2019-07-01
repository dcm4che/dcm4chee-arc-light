/*
 * ** BEGIN LICENSE BLOCK *****
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
 * Portions created by the Initial Developer are Copyright (C) 2016
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
 * ** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.metadata;

import org.dcm4che3.conf.api.ConfigurationChanges;
import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.DicomConfiguration;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.json.JSONWriter;
import org.dcm4chee.arc.Scheduler;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.Duration;
import org.dcm4chee.arc.conf.StorageDescriptor;
import org.dcm4chee.arc.entity.Metadata;
import org.dcm4chee.arc.entity.Series;
import org.dcm4chee.arc.event.SoftwareConfiguration;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveService;
import org.dcm4chee.arc.storage.Storage;
import org.dcm4chee.arc.storage.StorageFactory;
import org.dcm4chee.arc.storage.WriteContext;
import org.dcm4chee.arc.store.InstanceLocations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import java.io.IOException;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Nov 2016
 */
@ApplicationScoped
public class UpdateMetadataScheduler extends Scheduler {

    private static final Logger LOG = LoggerFactory.getLogger(UpdateMetadataScheduler.class);

    @Inject
    private DicomConfiguration conf;

    @Inject
    private UpdateMetadataEJB ejb;

    @Inject
    private RetrieveService retrieveService;

    @Inject
    private Event<SoftwareConfiguration> softwareConfigurationEvent;

    @Inject
    private StorageFactory storageFactory;

    protected UpdateMetadataScheduler() {
        super(Mode.scheduleWithFixedDelay);
    }

    @Override
    protected Logger log() {
        return LOG;
    }

    @Override
    protected Duration getPollingInterval() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        String[] seriesMetadataStorageIDs = arcDev.getSeriesMetadataStorageIDs();
        if (seriesMetadataStorageIDs.length > 0)
            try {
                arcDev.getStorageDescriptorNotNull(seriesMetadataStorageIDs[0]);
                return arcDev.getSeriesMetadataPollingInterval();
            } catch (IllegalArgumentException e) {
                LOG.warn(e.getMessage());
            }
        return null;
    }

    @Override
    protected void execute() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        String[] storageIDs = arcDev.getSeriesMetadataStorageIDs();
        if (storageIDs.length == 0)
            return;

        List<StorageDescriptor> descriptors = arcDev.getStorageDescriptors(storageIDs);
        int fetchSize = arcDev.getSeriesMetadataFetchSize();
        int threads = arcDev.getSeriesMetadataThreads();
        Semaphore semaphore = threads > 1 ? new Semaphore(threads) : null;
        List<Series.MetadataUpdate> metadataUpdates;
        do {
            if (getPollingInterval() == null) return;
            LOG.debug("Query for Series scheduled for Creating/Updating Metadata");
            metadataUpdates = ejb.findSeriesForScheduledMetadataUpdate(fetchSize);
            if (metadataUpdates.isEmpty()) {
                LOG.debug("No Series scheduled for Creating/Updating Metadata");
                break;
            }
            LOG.info("Start Creating/Updating Metadata of {} Series", metadataUpdates.size());
            AtomicInteger success = new AtomicInteger();
            AtomicInteger skipped = new AtomicInteger();
            try (Storage storage = storageFactory.getUsableStorage(descriptors)) {
                for (Series.MetadataUpdate metadataUpdate : metadataUpdates) {
                    if (semaphore == null) {
                        updateMetadata(arcDev, storage, metadataUpdate, success, skipped);
                    } else {
                        semaphore.acquire();
                        device.execute(() -> {
                            try {
                                updateMetadata(arcDev, storage, metadataUpdate, success, skipped);
                            } finally {
                                semaphore.release();
                            }
                        });
                    }
                }
                if (semaphore != null) {
                    LOG.debug("Waiting for finishing Creating/Updating Metadata of {} Series", metadataUpdates.size());
                    semaphore.acquire(threads);
                    semaphore.release(threads);
                }
            } catch (Exception e) {
                LOG.error("Failed to access Storage:\n", e);
            } finally {
                LOG.info("Finished Creating/Updating Metadata of {} (skipped={}, failed={}) Series",
                        success, skipped, metadataUpdates.size() - success.get() - skipped.get());
            }
        }
        while (metadataUpdates.size() == fetchSize);
        if (descriptors.size() < storageIDs.length) {
            arcDev.setSeriesMetadataStorageIDs(StorageDescriptor.storageIDsOf(descriptors));
            updateDeviceConfiguration(arcDev);
        }
    }

    private void updateDeviceConfiguration(ArchiveDeviceExtension arcDev) {
        try {
            LOG.info("Update Storage configuration of Device: {}:\n", device.getDeviceName());
            ConfigurationChanges diffs = conf.merge(device, EnumSet.of(
                    DicomConfiguration.Option.PRESERVE_VENDOR_DATA,
                    DicomConfiguration.Option.PRESERVE_CERTIFICATE,
                    arcDev.isAuditSoftwareConfigurationVerbose()
                            ? DicomConfiguration.Option.CONFIGURATION_CHANGES_VERBOSE
                            : DicomConfiguration.Option.CONFIGURATION_CHANGES));
            softwareConfigurationEvent.fire(new SoftwareConfiguration(null, device.getDeviceName(), diffs));
        } catch (ConfigurationException e) {
            LOG.warn("Failed to update Storage configuration of Device: {}:\n", device.getDeviceName(), e);
        }
    }

    private void updateMetadata(ArchiveDeviceExtension arcDev, Storage storage, Series.MetadataUpdate metadataUpdate,
                                AtomicInteger success, AtomicInteger skipped) {
        try (RetrieveContext ctx = retrieveService.newRetrieveContextSeriesMetadata(metadataUpdate)) {
            if (claim(metadataUpdate, storage) && retrieveService.calculateMatches(ctx)) {
                LOG.debug("Creating/Updating Metadata for Series[pk={}] on {}",
                        metadataUpdate.seriesPk,
                        storage.getStorageDescriptor());
                WriteContext writeCtx = createWriteContext(storage, ctx.getMatches().iterator().next());
                try {
                    try (ZipOutputStream out = new ZipOutputStream(storage.openOutputStream(writeCtx))) {
                        for (InstanceLocations match : ctx.getMatches()) {
                            out.putNextEntry(new ZipEntry(match.getSopInstanceUID()));
                            JsonGenerator gen = Json.createGenerator(out);
                            new JSONWriter(gen).write(loadMetadata(ctx, match));
                            gen.flush();
                            out.closeEntry();
                        }
                        out.finish();
                    }
                    storage.commitStorage(writeCtx);
                    ejb.commit(metadataUpdate.seriesPk, createMetadata(writeCtx));
                } catch (Exception e) {
                    LOG.warn("Failed to Create/Update Metadata for Series[pk={}] on {}:\n",
                            metadataUpdate.seriesPk,
                            storage.getStorageDescriptor(),
                            e);
                    try {
                        ejb.incrementMetadataUpdateFailures(
                                metadataUpdate.seriesPk,
                                nextRetry(arcDev, metadataUpdate.updateFailures));
                    } catch (Exception e1) {
                        LOG.warn("Failed to update Metadata Update time", e1);
                    }
                    try {
                        storage.revokeStorage(writeCtx);
                    } catch (Exception e1) {
                        LOG.warn("Failed to revoke storage", e1);
                    }
                    return;
                }
                LOG.debug("Created/Updated Metadata for Series[pk={}] on {}",
                        metadataUpdate.seriesPk,
                        storage.getStorageDescriptor());
                success.getAndIncrement();
            } else {
                skipped.getAndIncrement();
            }
        } catch (Exception e) {
            LOG.error("Unexpected exception on closing Retrieve Context for {}:\n", metadataUpdate, e);
        }
    }

    private static Date nextRetry(ArchiveDeviceExtension arcDev, int updateFailures) {
        int maxRetries = arcDev.getSeriesMetadataMaxRetries();
        Duration retryInterval = arcDev.getSeriesMetadataRetryInterval();
        return retryInterval != null && (maxRetries < 0 || maxRetries > updateFailures)
                ? new Date(System.currentTimeMillis() + retryInterval.getSeconds() * 1000L)
                : null;
    }

    private boolean claim(Series.MetadataUpdate metadataUpdate, Storage storage) {
        try {
            return ejb.claim(metadataUpdate);
        } catch (Exception e) {
            LOG.info("Failed to claim create/update Metadata for Series[pk={}] on {}]:\n",
                    metadataUpdate.seriesPk,
                    storage.getStorageDescriptor(),
                    e);
            return false;
        }
    }

    private Attributes loadMetadata(RetrieveContext ctx, InstanceLocations match) throws IOException {
        return match.isContainsMetadata() ? match.getAttributes() : retrieveService.loadMetadata(ctx, match);
    }

    private Metadata createMetadata(WriteContext writeContext) {
        Metadata metadata = new Metadata();
        metadata.setStorageID(writeContext.getStorage().getStorageDescriptor().getStorageID());
        metadata.setStoragePath(writeContext.getStoragePath());
        metadata.setSize(writeContext.getSize());
        metadata.setDigest(writeContext.getDigest());
        return metadata;
    }

    private WriteContext createWriteContext(Storage storage, InstanceLocations match) {
        WriteContext writeCtx = storage.createWriteContext();
        writeCtx.setAttributes(match.getAttributes());
        writeCtx.setMessageDigest(storage.getStorageDescriptor().getMessageDigest());
        return writeCtx;
    }

}
