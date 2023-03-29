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
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.util.StreamUtils;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.conf.StorageDescriptor;
import org.dcm4chee.arc.entity.Location;
import org.dcm4chee.arc.entity.Task;
import org.dcm4chee.arc.exporter.AbstractExporter;
import org.dcm4chee.arc.exporter.ExportContext;
import org.dcm4chee.arc.qmgt.Outcome;
import org.dcm4chee.arc.storage.ReadContext;
import org.dcm4chee.arc.store.InstanceLocations;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveService;
import org.dcm4chee.arc.retrieve.LocationInputStream;
import org.dcm4chee.arc.storage.Storage;
import org.dcm4chee.arc.storage.WriteContext;
import org.dcm4chee.arc.store.StoreService;
import org.dcm4chee.arc.store.StoreSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since April 2018
 */
public class StorageExporter extends AbstractExporter {

    private static final Logger LOG = LoggerFactory.getLogger(StorageExporter.class);

    private static final int DEFAULT_BUFFER_SIZE = 8192;

    private final RetrieveService retrieveService;
    private final StoreService storeService;

    public StorageExporter(ExporterDescriptor descriptor, RetrieveService retrieveService,
                           StoreService storeService) {
        super(descriptor);
        this.retrieveService = retrieveService;
        this.storeService = storeService;
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
                boolean tarArchiver = storage.getStorageDescriptor().isTarArchiver();
                Map<String,Map<String,Map<String, InstanceLocations>>> matchesByTar =
                        tarArchiver ? null : new HashMap<>();
                for (Iterator<InstanceLocations> it = retrieveContext.getMatches().iterator(); it.hasNext();) {
                    InstanceLocations match = it.next();
                    if (locationsOnStorage(match, storageID).anyMatch(Location::isStatusOK)) {
                        it.remove();
                        retrieveContext.setNumberOfMatches(retrieveContext.getNumberOfMatches() - 1);
                    } else {
                        if (matchesByTar != null && !addMatchByTar(retrieveContext, match, matchesByTar))
                            matchesByTar = null;
                    }
                }
                Collection<String> seriesIUIDs = tarArchiver
                        ? tarFiles(retrieveContext, storeSession, storage)
                        : matchesByTar != null
                        ? untarFiles(retrieveContext, storeSession, storage, matchesByTar)
                        : copyFiles(retrieveContext, storeSession, storage);
                retrieveContext.getRetrieveService().updateInstanceAvailability(retrieveContext);
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

    private boolean addMatchByTar(RetrieveContext retrieveContext, InstanceLocations match,
                                  Map<String, Map<String, Map<String, InstanceLocations>>> matchesByTar) {
        List<Location> locations = match.getLocations();
        if (locations.size() != 1)
            return false;
        Location location = locations.get(0);
        String storageID = location.getStorageID();
        if (!retrieveContext.getRetrieveService().getStorage(storageID, retrieveContext).getStorageDescriptor().isTarArchiver())
            return false;
        String storagePath = location.getStoragePath();
        int tarPathEnd = storagePath.indexOf('!');
        if (tarPathEnd < 0)
            return false;
        String tarPath = storagePath.substring(0, tarPathEnd);
        String tarEntryName = storagePath.substring(tarPathEnd + 1);
        matchesByTar.computeIfAbsent(storageID, key -> new HashMap<>())
                .computeIfAbsent(tarPath, key -> new HashMap<>())
                .put(tarEntryName, match);
        return true;
    }

    private Collection<String> untarFiles(
            RetrieveContext retrieveContext, StoreSession storeSession, Storage storage,
            Map<String, Map<String, Map<String, InstanceLocations>>> matchesByTar) {
        Collection<String> seriesIUIDs = new HashSet<>();
        String storageID = storage.getStorageDescriptor().getStorageID();
        for (Map.Entry<String, Map<String, Map<String, InstanceLocations>>> entry : matchesByTar.entrySet()) {
            String tarStorageID = entry.getKey();
            Map<String, Map<String, InstanceLocations>> matchesByTar2 = entry.getValue();
            try (Storage tarStorage = retrieveContext.getRetrieveService().getStorage(tarStorageID, retrieveContext)) {
                for (Map.Entry<String, Map<String, InstanceLocations>> mapEntry : matchesByTar2.entrySet()) {
                    String tarPath = mapEntry.getKey();
                    Map<String, InstanceLocations> matchByTarEntry = mapEntry.getValue();
                    ReadContext readContext = tarStorage.createReadContext();
                    readContext.setStoragePath(tarPath);
                    untarFiles(retrieveContext, storeSession, storage, seriesIUIDs, storageID, tarStorage, tarPath,
                            matchByTarEntry, readContext);
                }
            } catch (IOException e) {
                LOG.warn("Failed to close {}", storage.getStorageDescriptor());
            }
        }
        return seriesIUIDs;
    }

    private void untarFiles(RetrieveContext retrieveContext, StoreSession storeSession, Storage storage,
                           Collection<String> seriesIUIDs, String storageID, Storage tarStorage, String tarPath,
                           Map<String, InstanceLocations> matchByTarEntry, ReadContext readContext) {
        try (TarArchiveInputStream tar = new TarArchiveInputStream(
                new BufferedInputStream(tarStorage.openInputStream(readContext)))) {
            TarArchiveEntry tarEntry;
            while ((tarEntry = tar.getNextTarEntry()) != null) {
                InstanceLocations match = matchByTarEntry.remove(tarEntry.getName());
                if (match != null)
                    copyTo(retrieveContext, storeSession, storage, seriesIUIDs, storageID, tarStorage, tar,
                            tarEntry, match);
            }
            for (InstanceLocations match : matchByTarEntry.values()) {
                LOG.warn("Failed to copy {} to {}: No such ", match, storage.getStorageDescriptor());
                retrieveContext.incrementFailed();
                retrieveContext.addFailedMatch(match);
            }
        } catch (IOException e) {
            LOG.warn("Failed to open {} at {}:\n", tarPath, storage.getStorageDescriptor(), e);
            for (InstanceLocations match : matchByTarEntry.values()) {
                retrieveContext.incrementFailed();
                retrieveContext.addFailedMatch(match);
            }
        }
    }

    private void copyTo(RetrieveContext retrieveContext, StoreSession storeSession, Storage storage,
                           Collection<String> seriesIUIDs, String storageID, Storage tarStorage,
                           TarArchiveInputStream tar, TarArchiveEntry tarEntry, InstanceLocations match) {
        WriteContext writeCtx = storage.createWriteContext(match.getAttributes());
        writeCtx.setContentLength(tarEntry.getSize());
        LOG.debug("Start copying {} to {}", match, storage.getStorageDescriptor());
        try {
            storage.copy(tar, writeCtx);
            Location location = mkLocation(match.getLocations().get(0), storageID, writeCtx.getStoragePath());
            storeService.replaceLocation(storeSession, match.getInstancePk(), location,
                    locationsOnStorage(match, storageID).collect(Collectors.toList()));
            storage.commitStorage(writeCtx);
            retrieveContext.incrementCompleted();
            LOG.debug("Finished copying {} to {}", match, storage.getStorageDescriptor());
            seriesIUIDs.add(match.getAttributes().getString(Tag.SeriesInstanceUID));
            StorageDescriptor retrieveCache = storage.getStorageDescriptor();
            if (retrieveContext.getUpdateInstanceAvailability() == null
                    && retrieveCache.getInstanceAvailability().compareTo(
                    tarStorage.getStorageDescriptor().getInstanceAvailability()) < 0) {
                retrieveContext.setUpdateInstanceAvailability(retrieveCache.getInstanceAvailability());
            }
        } catch (Exception e) {
            LOG.warn("Failed to copy {} to {}:\n", match, storage.getStorageDescriptor(), e);
            retrieveContext.incrementFailed();
            retrieveContext.addFailedMatch(match);
            try {
                storage.revokeStorage(writeCtx);
            } catch (Exception e2) {
                LOG.warn("Failed to revoke storage", e2);
            }
        }
    }

    private static Location mkLocation(Location srcLocation, String storageID, String storagePath) {
        return new Location.Builder()
                .storageID(storageID)
                .storagePath(storagePath)
                .transferSyntaxUID(srcLocation.getTransferSyntaxUID())
                .objectType(Location.ObjectType.DICOM_FILE)
                .size(srcLocation.getSize())
                .digest(srcLocation.getDigest())
                .uidMap(srcLocation.getUidMap())
                .build();
    }

    private Collection<String> copyFiles(RetrieveContext retrieveContext, StoreSession storeSession, Storage storage) {
        Collection<String> seriesIUIDs = new HashSet<>();
        String storageID = storage.getStorageDescriptor().getStorageID();
        for (InstanceLocations match : retrieveContext.getMatches()) {
            WriteContext writeCtx = storage.createWriteContext(match.getAttributes());
            writeCtx.setStudyInstanceUID(retrieveContext.getStudyInstanceUID());
            try (LocationInputStream locationInputStream = retrieveService.openLocationInputStream(
                    retrieveContext, match)) {
                LOG.debug("Start copying {} to {}", match, storage.getStorageDescriptor());
                writeCtx.setContentLength(locationInputStream.location.getSize());
                storage.copy(locationInputStream.stream, writeCtx);
                Location location = mkLocation(locationInputStream.location, storageID, writeCtx.getStoragePath());
                storeService.replaceLocation(storeSession, match.getInstancePk(), location,
                        locationsOnStorage(match, storageID).collect(Collectors.toList()));
                storage.commitStorage(writeCtx);
                retrieveContext.incrementCompleted();
                LOG.debug("Finished copying {} to {}", match, storage.getStorageDescriptor());
                seriesIUIDs.add(match.getAttributes().getString(Tag.SeriesInstanceUID));
            } catch (Exception e) {
                LOG.warn("Failed to copy {} to {}:\n", match, storage.getStorageDescriptor(), e);
                retrieveContext.incrementFailed();
                retrieveContext.addFailedMatch(match);
                try {
                    storage.revokeStorage(writeCtx);
                } catch (Exception e2) {
                    LOG.warn("Failed to revoke storage", e2);
                }
            }
        }
        return seriesIUIDs;
    }

    private static Stream<Location> locationsOnStorage(InstanceLocations match, String storageID) {
        return match.getLocations().stream().filter(l -> l.getStorageID().equals(storageID));
     }

    private Collection<String> tarFiles(RetrieveContext retrieveContext, StoreSession storeSession, Storage storage) {
        Collection<String> seriesIUIDs = new HashSet<>();
        String storageID = storage.getStorageDescriptor().getStorageID();
        Map<String, List<TarEntry>> tars = new HashMap<>();
        for (InstanceLocations instanceLocations : retrieveContext.getMatches()) {
            String tarEntryPath = storage.storagePathOf(instanceLocations.getAttributes());
            int tarPathEnd = tarEntryPath.indexOf('!');
            String entryName = tarEntryPath.substring(tarPathEnd + 1);
            if (tarPathEnd <= 0 || entryName.isEmpty()) {
                LOG.warn("Invalid TAR Entry Path {}", tarEntryPath);
                retrieveContext.failed();
                continue;
            }

            tars.computeIfAbsent(tarEntryPath.substring(0, tarPathEnd), key -> new ArrayList<>())
                    .add(new TarEntry(entryName, instanceLocations));
        }
        byte[] copyBuffer = new byte[DEFAULT_BUFFER_SIZE];
        for (Map.Entry<String, List<TarEntry>> entry : tars.entrySet()) {
            List<TarEntry> tarEntries = entry.getValue();
            WriteContext writeCtx = storage.createWriteContext(entry.getKey());
            writeCtx.setStudyInstanceUID(retrieveContext.getStudyInstanceUID());
            int completed = 0;
            try {
                try (TarArchiveOutputStream tar = new TarArchiveOutputStream(
                        new BufferedOutputStream(storage.openOutputStream(writeCtx)))) {
                    String storagePath = writeCtx.getStoragePath();
                    ByteArrayOutputStream md5sum = new ByteArrayOutputStream();
                    for (TarEntry tarEntry : tarEntries) {
                        LOG.debug("Start copying {} to TAR {} at {}", tarEntry.match,
                                storagePath, storage.getStorageDescriptor());
                        tarEntry.newLocation = copyTo(retrieveContext, tarEntry.match,
                                tar, storageID, storagePath, tarEntry.entryName, copyBuffer);
                        LOG.debug("Finished copying {} to TAR {} at {}", tarEntry.match,
                                storagePath, storage.getStorageDescriptor());
                        if (tarEntry.newLocation.getDigestAsHexString() != null) {
                            md5sum.write(tarEntry.newLocation.getDigestAsHexString().getBytes());
                            md5sum.write(' ');
                            md5sum.write(' ');
                            md5sum.write(tarEntry.entryName.getBytes());
                            md5sum.write('\n');
                        }
                    }
                    if (md5sum.size() > 0) {
                        TarArchiveEntry archiveEntry = new TarArchiveEntry("MD5SUM");
                        archiveEntry.setSize(md5sum.size());
                        tar.putArchiveEntry(archiveEntry);
                        tar.write(md5sum.toByteArray());
                        tar.closeArchiveEntry();
                    }
                }
                storage.commitStorage(writeCtx);
                for (TarEntry tarEntry : tarEntries) {
                    storeService.replaceLocation(storeSession, tarEntry.match.getInstancePk(),
                            tarEntry.newLocation, locationsOnStorage(tarEntry.match, storageID).collect(Collectors.toList()));
                    completed++;
                    retrieveContext.incrementCompleted();
                    seriesIUIDs.add(tarEntry.match.getAttributes().getString(Tag.SeriesInstanceUID));
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
            return mkLocation(locationInputStream.location, storageID, storagePath + '!' + entryName);
        }
    }

    private static final class TarEntry {
        final String entryName;
        final InstanceLocations match;
        Location newLocation;

        public TarEntry(String entryName, InstanceLocations match) {
            this.entryName = entryName;
            this.match = match;
        }
    }
}
