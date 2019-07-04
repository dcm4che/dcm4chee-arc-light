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

import org.dcm4che3.data.Tag;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.entity.Location;
import org.dcm4chee.arc.entity.QueueMessage;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since April 2018
 */
public class StorageExporter extends AbstractExporter {

    private static Logger LOG = LoggerFactory.getLogger(StorageExporter.class);

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
                return new Outcome(QueueMessage.Status.WARNING, noMatches(exportContext));

            try {
                Set<String> seriesIUIDs = new HashSet<>();
                Storage storage = retrieveService.getStorage(storageID, retrieveContext);
                retrieveContext.setDestinationStorage(storage.getStorageDescriptor());
                for (InstanceLocations instanceLocations : retrieveContext.getMatches()) {
                    Map<Boolean, List<Location>> locationsOnStorageByStatusOK =
                            instanceLocations.getLocations().stream()
                                .filter(l -> l.getStorageID().equals(storageID))
                                .collect(Collectors.partitioningBy(Location::isStatusOK));
                    if (!locationsOnStorageByStatusOK.get(Boolean.TRUE).isEmpty()) {
                        retrieveContext.setNumberOfMatches(retrieveContext.getNumberOfMatches()-1);
                        continue;
                    }

                    WriteContext writeCtx = storage.createWriteContext();
                    writeCtx.setAttributes(instanceLocations.getAttributes());
                    writeCtx.setStudyInstanceUID(studyIUID);
                    Location location = null;
                    try {
                        LOG.debug("Start copying {} to {}:\n", instanceLocations, storage.getStorageDescriptor());
                        location = copyTo(retrieveContext, instanceLocations, storage, writeCtx);
                        storeService.replaceLocation(storeSession, instanceLocations.getInstancePk(),
                                location, locationsOnStorageByStatusOK.get(Boolean.FALSE));
                        storage.commitStorage(writeCtx);
                        retrieveContext.incrementCompleted();
                        LOG.debug("Finished copying {} to {}:\n", instanceLocations, storage.getStorageDescriptor());
                        seriesIUIDs.add(instanceLocations.getAttributes().getString(Tag.SeriesInstanceUID));
                    } catch (Exception e) {
                        LOG.warn("Failed to copy {} to {}:\n", instanceLocations, storage.getStorageDescriptor(), e);
                        retrieveContext.incrementFailed();
                        retrieveContext.addFailedSOPInstanceUID(instanceLocations.getSopInstanceUID());
                        if (location != null)
                            try {
                                storage.revokeStorage(writeCtx);
                            } catch (Exception e2) {
                                LOG.warn("Failed to revoke storage", e2);
                            }
                    }
                }
                if (!seriesIUIDs.isEmpty()) {
                    storeService.addStorageID(studyIUID, storageID);
                    for (String seriesIUID : seriesIUIDs) {
                        storeService.scheduleMetadataUpdate(studyIUID, seriesIUID);
                    }
                }
                return new Outcome(retrieveContext.failed() > 0
                        ? QueueMessage.Status.FAILED
                        : QueueMessage.Status.COMPLETED,
                        outcomeMessage(exportContext, retrieveContext));
            } finally {
                retrieveContext.getRetrieveService().updateLocations(retrieveContext);
            }
        }
    }

    private Location copyTo(RetrieveContext retrieveContext, InstanceLocations instanceLocations,
                            Storage storage, WriteContext writeCtx) throws IOException {
        try (LocationInputStream locationInputStream = retrieveService.openLocationInputStream(
                retrieveContext, instanceLocations)) {
            writeCtx.setContentLength(locationInputStream.location.getSize());
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

    private String noMatches(ExportContext exportContext) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("Could not find ");
        appendEntity(exportContext, sb);
        return sb.toString();
    }

    private String outcomeMessage(ExportContext exportContext, RetrieveContext retrieveContext) {
        int completed = retrieveContext.completed();
        int failed = retrieveContext.failed();
        StringBuilder sb = new StringBuilder(256);
        sb.append("Export ");
        appendEntity(exportContext, sb);
        sb.append(" to ").append(retrieveContext.getDestinationStorage());
        sb.append(" - completed:").append(completed);
        if (failed > 0)
            sb.append(", ").append("failed:").append(failed);
        return sb.toString();
    }

    private StringBuilder appendEntity(ExportContext exportContext, StringBuilder sb) {
        String studyInstanceUID = exportContext.getStudyInstanceUID();
        String seriesInstanceUID = exportContext.getSeriesInstanceUID();
        String sopInstanceUID = exportContext.getSopInstanceUID();
        if (sopInstanceUID != null && !sopInstanceUID.equals("*"))
            sb.append("Instance[uid=").append(sopInstanceUID).append("] of ");
        if (seriesInstanceUID != null && !seriesInstanceUID.equals("*"))
            sb.append("Series[uid=").append(seriesInstanceUID).append("] of ");
        return sb.append("Study[uid=").append(studyInstanceUID).append("]");
    }

}
