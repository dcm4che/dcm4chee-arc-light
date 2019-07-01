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
 * **** END LICENSE BLOCK *****
 *
 */

package org.dcm4chee.arc.delete.impl;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.json.JSONReader;
import org.dcm4chee.arc.Scheduler;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.Duration;
import org.dcm4chee.arc.conf.StorageDescriptor;
import org.dcm4chee.arc.entity.Location;
import org.dcm4chee.arc.entity.Metadata;
import org.dcm4chee.arc.storage.ReadContext;
import org.dcm4chee.arc.storage.Storage;
import org.dcm4chee.arc.storage.StorageFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.Json;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipInputStream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Nov 2018
 */
@ApplicationScoped
public class FailedToDeleteScheduler extends Scheduler {

    private static final Logger LOG = LoggerFactory.getLogger(FailedToDeleteScheduler.class);

    @Inject
    private DeletionServiceEJB ejb;

    @Inject
    private StorageFactory storageFactory;

    private Set<String> inProcess = Collections.synchronizedSet(new HashSet<>());

    protected FailedToDeleteScheduler() {
        super(Mode.scheduleAtFixedRate);
    }

    @Override
    protected Logger log() {
        return LOG;
    }

    @Override
    protected Duration getPollingInterval() {
        return device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class).getFailedToDeletePollingInterval();
    }

    @Override
    protected void execute() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
        for (StorageDescriptor desc : arcDev.getStorageDescriptors()) {
            if (arcDev.getFailedToDeletePollingInterval() == null) return;
            if (!desc.isReadOnly() && inProcess.add(desc.getStorageID())) {
                device.execute(() -> {
                    LOG.info("Start resolving deletion failures on {}", desc);
                    try {
                        resolveObjectDeletionFailures(arcDev, desc);
                        resolveSeriesMetadatDeletionFailures(arcDev, desc);
                    } catch (Throwable e) {
                        LOG.warn("Resolving deletion failures on {} throws:\n", desc, e);
                    } finally {
                        inProcess.remove(desc.getStorageID());
                        LOG.info("Finished resolving deletion failures on {}", desc);
                    }
                });
            }
        }
    }

    private enum Result {
        SKIPPED("Skipped {} deletion failures on {} already processed by another node."),
        NO_OBJECT("Resolved {} deletion failures on {}: object was already deleted."),
        IN_USE("Resolved {} deletion failures on {}: object in-use - do not delete."),
        TO_DELETE("Resolved {} deletion failures on {}: deletion rescheduled."),
        FAILED("Failed to resolve {} deletion failures on {}.");

        final String format;

        Result(String format) {
            this.format = format;
        }
    }

    private void resolveSeriesMetadatDeletionFailures(ArchiveDeviceExtension arcDev, StorageDescriptor desc) {
        List<Metadata> locations;
        int fetchSize = arcDev.getFailedToDeleteFetchSize();
        do {
            if (arcDev.getFailedToDeletePollingInterval() == null) return;
            LOG.debug("Query for Metadata deletion failures on {}", desc);
            locations = ejb.findMetadataWithStatus(desc.getStorageID(), Metadata.Status.FAILED_TO_DELETE, fetchSize);
            if (locations.isEmpty()) {
                LOG.debug("No Metadata deletion failures found on {}", desc);
                break;
            }

            int[] results = new int[5];
            LOG.info("Start resolving {} Metadata deletion failures on {}", locations.size(), desc);
            try (Storage storage = storageFactory.getStorage(desc)) {
                for (Metadata location : locations) {
                    results[processMetadata(storage, location).ordinal()]++;
                }
            } catch (Exception e) {
                LOG.warn("Failed to access {}:\n", desc, e);
            } finally {
                for (Result value : Result.values()) {
                    int n = results[value.ordinal()];
                    if (n > 0)
                        LOG.info(value.format, n, desc);
                }
            }
        } while (locations.size() == fetchSize);
    }

    private Result processMetadata(Storage storage, Metadata location) {
        try {
            if (!ejb.claimResolveFailedToDeleteMetadata(location)) return Result.SKIPPED;
            ReadContext ctx = storage.createReadContext();
            ctx.setStoragePath(location.getStoragePath());
            if (!storage.exists(ctx)) {
                LOG.debug("{} does not exists on {} - delete record from DB", location, storage);
                ejb.removeMetadata(location);
                return Result.NO_OBJECT;
            }
            LOG.debug("Search for other records with equal path as {} on {}", location, storage);
            Attributes attrs;
            try (InputStream in = storage.openInputStream(ctx)) {
                ZipInputStream zip = new ZipInputStream(in);
                zip.getNextEntry();
                attrs = parseJSON(zip);
            }
            for (Metadata other : ejb.findMetadataForSeriesOnStorage(attrs.getString(Tag.SeriesInstanceUID),
                    location.getStorageID())) {
                if (location.getStoragePath().equals(other.getStoragePath()) && inUse(other.getStatus())) {
                    LOG.debug("Found other {} with equal path as {} on {} - delete this record from DB",
                            other, location, storage);
                    ejb.removeMetadata(location);
                    return Result.IN_USE;
                }
            }
            LOG.debug("No other record with equal path as {} on {} found - reschedule deletion", location, storage);
            ejb.rescheduleDeleteMetadata(location);
            return Result.TO_DELETE;
        } catch (Exception e) {
            LOG.warn("Failed to resolve {} from {}:\n", location, storage, e);
            return Result.FAILED;
        }
    }

    private static Attributes parseJSON(InputStream in) throws IOException {
        JSONReader jsonReader = new JSONReader(Json.createParser(new InputStreamReader(in, "UTF-8")));
        jsonReader.setSkipBulkDataURI(true);
        return jsonReader.readDataset(null);
    }

    private void resolveObjectDeletionFailures(ArchiveDeviceExtension arcDev, StorageDescriptor desc) {
        List<Location> locations;
        int fetchSize = arcDev.getFailedToDeleteFetchSize();
        do {
            if (arcDev.getFailedToDeletePollingInterval() == null) return;
            LOG.debug("Query for deletion failures on {}", desc);
            locations = ejb.findLocationsWithStatus(desc.getStorageID(), Location.Status.FAILED_TO_DELETE, fetchSize);
            if (locations.isEmpty()) {
                LOG.debug("No deletion failures found on {}", desc);
                break;
            }

            int[] results = new int[5];
            LOG.info("Start resolving {} deletion failures on {}", locations.size(), desc);
            try (Storage storage = storageFactory.getStorage(desc)) {
                for (Location location : locations) {
                    results[processLocation(storage, location).ordinal()]++;
                }
            } catch (Exception e) {
                LOG.warn("Failed to access {}:\n", desc, e);
            } finally {
                for (Result value : Result.values()) {
                    int n = results[value.ordinal()];
                    if (n > 0)
                        LOG.info(value.format, n, desc);
                }
            }
        } while (locations.size() == fetchSize);
    }

    private Result processLocation(Storage storage, Location location) {
        try {
            if (!ejb.claimResolveFailedToDelete(location)) return Result.SKIPPED;
            ReadContext ctx = storage.createReadContext();
            ctx.setStoragePath(location.getStoragePath());
            if (!storage.exists(ctx)) {
                LOG.debug("{} does not exists on {} - delete record from DB", location, storage);
                ejb.removeLocation(location);
                return Result.NO_OBJECT;
            }
            if (location.getObjectType() == Location.ObjectType.DICOM_FILE) {
                LOG.debug("Search for other records with equal path as {} on {}", location, storage);
                Attributes attrs;
                try (DicomInputStream din = new DicomInputStream(storage.openInputStream(ctx))) {
                    attrs = din.readDataset(-1, Tag.PixelData);
                }
                for (Location other : ejb.findLocationsForInstanceOnStorage(attrs.getString(Tag.SOPInstanceUID),
                        location.getStorageID())) {
                    if (location.getStoragePath().equals(other.getStoragePath()) && inUse(other.getStatus())) {
                        LOG.debug("Found other {} with equal path as {} on {} - delete this record from DB",
                                other, location, storage);
                        ejb.removeLocation(location);
                        return Result.IN_USE;
                    }
                }
                ejb.rescheduleDeleteObject(location);
                LOG.debug("No other record with equal path as {} on {} found - reschedule deletion", location, storage);
            } else {
                ejb.rescheduleDeleteObject(location);
                LOG.debug("Reschedule deletion of {} on {}", location, storage);
            }
            return Result.TO_DELETE;
        } catch (Exception e) {
            LOG.warn("Failed to resolve {} from {}:\n", location, storage, e);
            return Result.FAILED;
        }
    }

    private static boolean inUse(Location.Status status) {
        switch (status) {
            case TO_DELETE:
            case FAILED_TO_DELETE:
            case FAILED_TO_DELETE2:
                return false;
        }
        return true;
    }

    private static boolean inUse(Metadata.Status status) {
        switch (status) {
            case TO_DELETE:
            case FAILED_TO_DELETE:
            case FAILED_TO_DELETE2:
                return false;
        }
        return true;
    }
}
