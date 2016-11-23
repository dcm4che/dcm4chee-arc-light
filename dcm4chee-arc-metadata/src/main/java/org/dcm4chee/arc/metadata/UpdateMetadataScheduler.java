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
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
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

import org.dcm4che3.net.Device;
import org.dcm4chee.arc.Scheduler;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.Duration;
import org.dcm4chee.arc.conf.StorageDescriptor;
import org.dcm4chee.arc.entity.Location;
import org.dcm4chee.arc.retrieve.InstanceLocations;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveService;
import org.dcm4chee.arc.storage.Storage;
import org.dcm4chee.arc.storage.StorageFactory;
import org.dcm4chee.arc.storage.WriteContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
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
    private Device device;

    @Inject
    private UpdateMetadataEJB ejb;

    @Inject
    private RetrieveService retrieveService;

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
        return arcDev.getSeriesMetadataStorageID() != null && arcDev.getSeriesMetadataFetchSize() > 0
                ? arcDev.getSeriesMetadataPollingInterval() : null;
    }

    @Override
    protected void execute() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        StorageDescriptor storageDesc = arcDev.getStorageDescriptorNotNull(arcDev.getSeriesMetadataStorageID());
        try (Storage storage = storageFactory.getStorage(storageDesc)) {
            int fetchSize = arcDev.getSeriesMetadataFetchSize();
            List<Long> seriesPks;
            do {
                seriesPks = ejb.findSeriesForScheduledMetadataUpdate(fetchSize);
                for (Long seriesPk : seriesPks) {
                    updateMetadata(retrieveService.newRetrieveContextSeriesMetadata(seriesPk), storage);
                }
            }
            while (seriesPks.size() == fetchSize);
        } catch (IOException e) {
            LOG.error("Failed to store Series Metadata to {}:\n", storageDesc.getStorageURI(), e);
        }
    }

    private void updateMetadata(RetrieveContext ctx, Storage storage) throws IOException {
        if (!retrieveService.calculateMatches(ctx))
            return;

        WriteContext writeCtx = createWriteContext(storage, ctx.getMatches().iterator().next());
        try (ZipOutputStream out = new ZipOutputStream(storage.openOutputStream(writeCtx))) {
            for (InstanceLocations match : ctx.getMatches()) {
                out.putNextEntry(new ZipEntry(match.getSopInstanceUID()));
                out.closeEntry();
            }
            out.finish();
        }

     }

    private WriteContext createWriteContext(Storage storage, InstanceLocations match) {
        WriteContext writeCtx = storage.createWriteContext();
        writeCtx.setAttributes(match.getAttributes());
        writeCtx.setMessageDigest(storage.getStorageDescriptor().getMessageDigest());
        return writeCtx;
    }

}
