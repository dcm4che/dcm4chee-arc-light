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

package org.dcm4che.arc.export.storage;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.util.StreamUtils;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.entity.Location;
import org.dcm4chee.arc.entity.Task;
import org.dcm4chee.arc.exporter.AbstractExporter;
import org.dcm4chee.arc.exporter.ExportContext;
import org.dcm4chee.arc.qmgt.Outcome;
import org.dcm4chee.arc.store.InstanceLocations;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveService;
import org.dcm4chee.arc.retrieve.LocationInputStream;
import org.dcm4chee.arc.storage.Storage;
import org.dcm4chee.arc.storage.StorageFactory;
import org.dcm4chee.arc.storage.WriteContext;
import org.dcm4chee.arc.store.StoreService;
import org.dcm4chee.arc.store.StoreSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since April 2018
 */
public class StorageExporter extends AbstractExporter {

    private static Logger LOG = LoggerFactory.getLogger(StorageExporter.class);

    private static final int DEFAULT_BUFFER_SIZE = 8192;

    private final RetrieveService retrieveService;
    private final StoreService storeService;
    private final StorageFactory storageFactory;

    public StorageExporter(ExporterDescriptor descriptor, RetrieveService retrieveService,
                           StoreService storeService, StorageFactory storageFactory) {
        super(descriptor);
        this.retrieveService = retrieveService;
        this.storeService = storeService;
        this.storageFactory = storageFactory;
    }

    @Override
    public Outcome export(ExportContext exportContext) throws Exception {
        String studyIUID = exportContext.getStudyInstanceUID();
        try (RetrieveContext retrieveContext = retrieveService.newRetrieveContext(
                exportContext.getAETitle(),
                studyIUID,
                exportContext.getSeriesInstanceUID(),
                exportContext.getSopInstanceUID())) {
            retrieveContext.setHttpServletRequestInfo(exportContext.getHttpServletRequestInfo());
            String storageID = descriptor.getExportURI().getSchemeSpecificPart();
            ApplicationEntity ae = retrieveContext.getLocalApplicationEntity();
            StoreSession storeSession = storeService.newStoreSession(ae).withObjectStorageID(storageID);
            storeService.restoreInstances(
                    storeSession,
                    studyIUID,
                    exportContext.getSeriesInstanceUID(),
                    ae.getAEExtensionNotNull(ArchiveAEExtension.class).purgeInstanceRecordsDelay());
            if (!retrieveService.calculateMatches(retrieveContext))
                return new Outcome(Task.Status.WARNING, noMatches(exportContext));

            try {
                Storage storage = retrieveService.getStorage(storageID, retrieveContext);
                retrieveContext.setDestinationStorage(storage.getStorageDescriptor());
                Collection<String> seriesIUIDs = storage.getStorageDescriptor().isTarArchiver()
                    ? tarFiles(retrieveContext, storeSession, storage)
                    : copyFiles(retrieveContext, storeSession, storage);
                if (!seriesIUIDs.isEmpty()) {
                    storeService.addStorageID(studyIUID, storageID);
                    for (String seriesIUID : seriesIUIDs) {
                        storeService.scheduleMetadataUpdate(studyIUID, seriesIUID);
                    }
                }
                return new Outcome(retrieveContext.failed() > 0
                        ? Task.Status.FAILED
                        : Task.Status.COMPLETED,
                        outcomeMessage(exportContext, retrieveContext, retrieveContext.getDestinationStorage()));
            } finally {
                retrieveContext.getRetrieveService().updateLocations(retrieveContext);
            }
        }
    }

    private Collection<String> copyFiles(RetrieveContext retrieveContext, StoreSession storeSession, Storage storage) {
        Collection<String> seriesIUIDs = new HashSet<>();
        String storageID = storage.getStorageDescriptor().getStorageID();
        for (InstanceLocations instanceLocations : retrieveContext.getMatches()) {
            Map<Boolean, List<Location>> locationsOnStorageByStatusOK =
                    instanceLocations.getLocations().stream()
                        .filter(l -> l.getStorageID().equals(storageID))
                        .collect(Collectors.partitioningBy(Location::isStatusOK));
            if (!locationsOnStorageByStatusOK.get(Boolean.TRUE).isEmpty()) {
                retrieveContext.setNumberOfMatches(retrieveContext.getNumberOfMatches()-1);
                continue;
            }

            WriteContext writeCtx = storage.createWriteContext(instanceLocations.getAttributes());
            writeCtx.setStudyInstanceUID(retrieveContext.getStudyInstanceUID());
            Location location = null;
            try {
                LOG.debug("Start copying {} to {}", instanceLocations, storage.getStorageDescriptor());
                location = copyTo(retrieveContext, instanceLocations, storage, writeCtx);
                storeService.replaceLocation(storeSession, instanceLocations.getInstancePk(),
                        location, locationsOnStorageByStatusOK.get(Boolean.FALSE));
                storage.commitStorage(writeCtx);
                retrieveContext.incrementCompleted();
                LOG.debug("Finished copying {} to {}", instanceLocations, storage.getStorageDescriptor());
                seriesIUIDs.add(instanceLocations.getAttributes().getString(Tag.SeriesInstanceUID));
            } catch (Exception e) {
                LOG.warn("Failed to copy {} to {}:\n", instanceLocations, storage.getStorageDescriptor(), e);
                retrieveContext.incrementFailed();
                retrieveContext.addFailedMatch(instanceLocations);
                if (location != null)
                    try {
                        storage.revokeStorage(writeCtx);
                    } catch (Exception e2) {
                        LOG.warn("Failed to revoke storage", e2);
                    }
            }
        }
        return seriesIUIDs;
    }

    private Location copyTo(RetrieveContext retrieveContext, InstanceLocations instanceLocations,
                            Storage storage, WriteContext writeCtx) throws IOException {
        try (LocationInputStream locationInputStream = retrieveService.openLocationInputStream(
                retrieveContext, instanceLocations)) {
            writeCtx.setContentLength(locationInputStream.location.getSize());
            storage.copy(locationInputStream.stream, writeCtx);
            Location location = new Location.Builder()
                    .storageID(storage.getStorageDescriptor().getStorageID())
                    .storagePath(writeCtx.getStoragePath())
                    .transferSyntaxUID(locationInputStream.location.getTransferSyntaxUID())
                    .objectType(Location.ObjectType.DICOM_FILE)
                    .size(locationInputStream.location.getSize())
                    .digest(locationInputStream.location.getDigest())
                    .build();
            location.setUidMap(locationInputStream.location.getUidMap());
            return location;
        }
    }

    private Collection<String> tarFiles(RetrieveContext retrieveContext, StoreSession storeSession, Storage storage) {
        Collection<String> seriesIUIDs = new HashSet<>();
        String storageID = storage.getStorageDescriptor().getStorageID();
        Map<String, List<TarEntry>> tars = new HashMap<>();
        for (InstanceLocations instanceLocations : retrieveContext.getMatches()) {
            Map<Boolean, List<Location>> locationsOnStorageByStatusOK =
                    instanceLocations.getLocations().stream()
                            .filter(l -> l.getStorageID().equals(storageID))
                            .collect(Collectors.partitioningBy(Location::isStatusOK));
            if (!locationsOnStorageByStatusOK.get(Boolean.TRUE).isEmpty()) {
                retrieveContext.setNumberOfMatches(retrieveContext.getNumberOfMatches() - 1);
                continue;
            }

            String tarEntryPath = storage.storagePathOf(instanceLocations.getAttributes());
            int tarPathEnd = tarEntryPath.indexOf('!');
            String entryName = tarEntryPath.substring(tarPathEnd + 1);
            if (tarPathEnd <= 0 || entryName.isEmpty()) {
                LOG.warn("Invalid TAR Entry Path {}", tarEntryPath);
                retrieveContext.failed();
                continue;
            }

            tars.computeIfAbsent(tarEntryPath.substring(0, tarPathEnd), key -> new ArrayList<>())
                    .add(new TarEntry(entryName, instanceLocations, locationsOnStorageByStatusOK.get(Boolean.FALSE)));
        }
        byte[] copyBuffer = new byte[DEFAULT_BUFFER_SIZE];
        for (Map.Entry<String, List<TarEntry>> entry : tars.entrySet()) {
            List<TarEntry> tarEntries = entry.getValue();
            WriteContext writeCtx = storage.createWriteContext(entry.getKey());
            writeCtx.setStudyInstanceUID(retrieveContext.getStudyInstanceUID());
            int completed = 0;
            try {
                try (TarArchiveOutputStream tar = new TarArchiveOutputStream(storage.openOutputStream(writeCtx))) {
                    String storagePath = writeCtx.getStoragePath();
                    for (TarEntry tarEntry : tarEntries) {
                        LOG.debug("Start copying {} to TAR {} at {}", tarEntry.instanceLocations,
                                storagePath, storage.getStorageDescriptor());
                        tarEntry.newLocation = copyTo(retrieveContext, tarEntry.instanceLocations,
                                tar, storageID, storagePath, tarEntry.entryName, copyBuffer);
                        LOG.debug("Finished copying {} to TAR {} at {}", tarEntry.instanceLocations,
                                storagePath, storage.getStorageDescriptor());
                    }
                }
                storage.commitStorage(writeCtx);
                for (TarEntry tarEntry : tarEntries) {
                    storeService.replaceLocation(storeSession, tarEntry.instanceLocations.getInstancePk(),
                            tarEntry.newLocation, tarEntry.replaceLocations);
                    completed++;
                    retrieveContext.incrementCompleted();
                    seriesIUIDs.add(tarEntry.instanceLocations.getAttributes().getString(Tag.SeriesInstanceUID));
                }
            } catch (Exception e) {
                retrieveContext.addFailed(tarEntries.size() - completed);
                if (completed == 0)
                    try {
                        storage.revokeStorage(writeCtx);
                    } catch (Exception e2) {
                        LOG.warn("Failed to revoke storage", e2);
                    }
            }
        }
        return seriesIUIDs;
    }

    private Location copyTo(RetrieveContext retrieveContext, InstanceLocations instanceLocations,
                            TarArchiveOutputStream tar, String storageID, String storagePath, String entryName,
                            byte[] copyBuffer)
            throws IOException {
        try (LocationInputStream locationInputStream = retrieveService.openLocationInputStream(
                retrieveContext, instanceLocations)) {
            TarArchiveEntry archiveEntry = new TarArchiveEntry(entryName);
            archiveEntry.setSize(locationInputStream.location.getSize());
            tar.putArchiveEntry(archiveEntry);
            StreamUtils.copy(locationInputStream.stream, tar, copyBuffer);
            tar.closeArchiveEntry();
            Location location = new Location.Builder()
                    .storageID(storageID)
                    .storagePath(storagePath + '!' + entryName)
                    .transferSyntaxUID(locationInputStream.location.getTransferSyntaxUID())
                    .objectType(Location.ObjectType.DICOM_FILE)
                    .size(locationInputStream.location.getSize())
                    .digest(locationInputStream.location.getDigest())
                    .build();
            location.setUidMap(locationInputStream.location.getUidMap());
            return location;
        }
    }

    private static final class TarEntry {
        final String entryName;
        final InstanceLocations instanceLocations;
        final List<Location> replaceLocations;
        Location newLocation;

        public TarEntry(String entryName, InstanceLocations instanceLocations, List<Location> replaceLocations) {
            this.entryName = entryName;
            this.instanceLocations = instanceLocations;
            this.replaceLocations = replaceLocations;
        }
    }
}
