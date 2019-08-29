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

package org.dcm4chee.arc.delete.impl;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.dict.archive.PrivateTag;
import org.dcm4che3.json.JSONReader;
import org.dcm4che3.util.SafeClose;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.Scheduler;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.Duration;
import org.dcm4chee.arc.entity.Location;
import org.dcm4chee.arc.entity.Series;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Dec 2016
 */
@ApplicationScoped
public class PurgeInstanceRecordsScheduler extends Scheduler {

    private static final Logger LOG = LoggerFactory.getLogger(PurgeInstanceRecordsScheduler.class);

    @Inject
    private DeletionServiceEJB ejb;

    @Inject
    private StorageFactory storageFactory;

    protected PurgeInstanceRecordsScheduler() {
        super(Mode.scheduleWithFixedDelay);
    }

    @Override
    protected Logger log() {
        return LOG;
    }

    @Override
    protected Duration getPollingInterval() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        return arcDev.isPurgeInstanceRecords() ? arcDev.getPurgeInstanceRecordsPollingInterval() : null;
    }

    @Override
    protected void execute() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        int fetchSize = arcDev.getPurgeInstanceRecordsFetchSize();
        List<Series.MetadataUpdate> series;
        Map<String, Storage> storageMap = new HashMap<>();
        try {
            do {
                if (getPollingInterval() == null)
                    return;

                series = ejb.findSeriesToPurgeInstances(fetchSize);
                for (Series.MetadataUpdate metadataUpdate : series) {
                    if (getPollingInterval() == null)
                        return;

                    Long seriesPk = metadataUpdate.seriesPk;
                    if (!claim(metadataUpdate))
                        continue;

                    LOG.info("Purging Instance records of Series[pk={}]", seriesPk);
                    Map<String, List<Location>> locationsFromMetadata = null;
                    try {
                        locationsFromMetadata = locationsFromMetadata(
                                getStorage(metadataUpdate.storageID, storageMap),
                                metadataUpdate.storagePath);
                    } catch (IOException e) {
                        LOG.warn("Reading of Metadata of Series[pk={}] failed - schedule recreation", seriesPk, e);
                        try {
                            ejb.scheduleMetadataUpdate(seriesPk);
                        } catch (Exception e1) {
                            LOG.warn("Failed to schedule recreation of Metadata of Series[pk={}]", seriesPk, e1);
                        }
                        continue;
                    }
                    try {
                        if (ejb.purgeInstanceRecordsOfSeries(seriesPk, locationsFromMetadata)) {
                            LOG.info("Purged Instance records of Series[pk={}]", seriesPk);
                        } else {
                            LOG.warn("Verification of Metadata of Series[pk={}] failed - recreation scheduled", seriesPk);
                        }
                    } catch (Exception e) {
                        LOG.warn("Failed to purge Instance records of Series[pk={}]\n", seriesPk, e);
                        try {
                            ejb.updateInstancePurgeState(seriesPk,
                                    Series.InstancePurgeState.NO, Series.InstancePurgeState.FAILED_TO_PURGE);
                        } catch (Exception e1) {
                            LOG.warn("Failed to set Instance Purge State of Series[pk={}] to FAILED", seriesPk, e1);
                        }
                    }
                }
            }
            while (series.size() == fetchSize);
        } finally {
            for (Storage storage : storageMap.values())
                SafeClose.close(storage);
        }
    }

    private boolean claim(Series.MetadataUpdate metadataUpdate) {
        try {
            return ejb.claimPurgeInstanceRecordsOfSeries(metadataUpdate);
        } catch (Exception e) {
            LOG.info("Failed to claim purge of Instance records of Series[pk={}]:\n", metadataUpdate.seriesPk, e);
            return false;
        }
    }

    private Map<String,List<Location>> locationsFromMetadata(Storage storage, String storagePath)
            throws IOException {
        Map<String, List<Location>> map = new HashMap<>();
        try (InputStream in = storage.openInputStream(createReadContext(storage, storagePath))) {
            ZipInputStream zip = new ZipInputStream(in);
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Attributes attrs = parseJSON(zip);
                List<Location> list = new ArrayList<>(2);
                list.add(createLocation(attrs));
                Sequence seq = attrs.getSequence(PrivateTag.PrivateCreator, PrivateTag.OtherStorageSequence);
                if (seq != null) for (Attributes item : seq) list.add(createLocation(item));
                map.put(attrs.getString(Tag.SOPInstanceUID), list);
                zip.closeEntry();
            }
        }
        return map;
    }

    private Location createLocation(Attributes attrs) {
            return new Location.Builder()
                .storageID(attrs.getString(PrivateTag.PrivateCreator, PrivateTag.StorageID))
                .storagePath(StringUtils.concat(attrs.getStrings(PrivateTag.PrivateCreator, PrivateTag.StoragePath), '/'))
                .transferSyntaxUID(attrs.getString(PrivateTag.PrivateCreator, PrivateTag.StorageTransferSyntaxUID))
                .digest(attrs.getString(PrivateTag.PrivateCreator, PrivateTag.StorageObjectDigest))
                .size(attrs.getInt(PrivateTag.PrivateCreator, PrivateTag.StorageObjectSize, -1))
                .status(attrs.getString(PrivateTag.PrivateCreator, PrivateTag.StorageObjectStatus))
                .build();

    }

    private static Attributes parseJSON(InputStream in) throws IOException {
        JSONReader jsonReader = new JSONReader(Json.createParser(new InputStreamReader(in, "UTF-8")));
        jsonReader.setSkipBulkDataURI(true);
        return jsonReader.readDataset(null);
    }

    private static ReadContext createReadContext(Storage storage, String storagePath) {
        ReadContext readContext = storage.createReadContext();
        readContext.setStoragePath(storagePath);
        return readContext;
    }

    private Storage getStorage(String storageID, Map<String,Storage> storageMap) {
        Storage storage = storageMap.get(storageID);
        if (storage == null) {
            ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
            storage = storageFactory.getStorage(arcDev.getStorageDescriptorNotNull(storageID));
            storageMap.put(storageID, storage);
        }
        return storage;
    }
}
