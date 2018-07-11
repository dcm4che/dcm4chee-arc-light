/*
 *  ** BEGIN LICENSE BLOCK *****
 *  Version: MPL 1./GPL 2.0/LGPL 2.1
 *
 *  The contents of this file are subject to the Mozilla Public License Version
 *  1.1 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  http://www.mozilla.org/MPL/
 *
 *  Software distributed under the License is distributed on an "AS IS" basis,
 *  WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 *  for the specific language governing rights and limitations under the
 *  License.
 *
 *  The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 *  Java(TM), hosted at https://github.com/dcm4che.
 *
 *  The Initial Developer of the Original Code is
 *  J4Care.
 *  Portions created by the Initial Developer are Copyright (C) 2017
 *  the Initial Developer. All Rights Reserved.
 *
 *  Contributor(s):
 *  See @authors listed below
 *
 *  Alternatively, the contents of this file may be used under the terms of
 *  either the GNU General Public License Version 2 or later (the "GPL"), or
 *  the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 *  in which case the provisions of the GPL or the LGPL are applicable instead
 *  of those above. If you wish to allow use of your version of this file only
 *  under the terms of either the GPL or the LGPL, and not to allow others to
 *  use your version of this file under the terms of the MPL, indicate your
 *  decision by deleting the provisions above and replace them with the notice
 *  and other provisions required by the GPL or the LGPL. If you do not delete
 *  the provisions above, a recipient may use your version of this file under
 *  the terms of any one of the MPL, the GPL or the LGPL.
 *
 *  ** END LICENSE BLOCK *****
 *
 */

package org.dcm4chee.arc.storage.rs;

import org.dcm4che3.conf.json.JsonWriter;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.DeleterThreshold;
import org.dcm4chee.arc.conf.StorageDescriptor;
import org.dcm4chee.arc.storage.Storage;
import org.dcm4chee.arc.storage.StorageFactory;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.Path;
import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.util.*;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since May 2017
 */

@Path("storage")
@RequestScoped
public class StorageRS {

    private static final Logger LOG = LoggerFactory.getLogger(StorageRS.class);

    @Inject
    private Device device;

    @Inject
    private StorageFactory storageFactory;

    @Context
    private HttpServletRequest request;

    @QueryParam("uriScheme")
    @Pattern(regexp = "file|jclouds|emc-ecs-s3|hcp|documentum")
    private String uriScheme;

    @QueryParam("dicomAETitle")
    private String dicomAETitle;

    @QueryParam("usage")
    @Pattern(regexp = "dcmObjectStorageID|dcmMetadataStorageID|dcmSeriesMetadataStorageID")
    private String usage;

    @QueryParam("usableSpaceBelow")
    private Long usableSpaceBelow;

    @QueryParam("dcmStorageClusterID")
    private String storageClusterID;

    @GET
    @NoCache
    @Produces("application/json")
    public StreamingOutput search() {
        LOG.info("Process GET {} from {}@{}", request.getRequestURI(), request.getRemoteUser(), request.getRemoteHost());
        return out -> {
                JsonGenerator gen = Json.createGenerator(out);
                gen.writeStartArray();
                for (StorageSystem ss : getStorageSystems()) {
                    StorageDescriptor desc = ss.desc;
                    JsonWriter writer = new JsonWriter(gen);
                    gen.writeStartObject();
                    gen.write("dcmStorageID", desc.getStorageID());
                    gen.write("dcmURI", desc.getStorageURIStr());
                    writer.writeNotNullOrDef("dcmDigestAlgorithm", desc.getDigestAlgorithm(), null);
                    writer.writeNotNullOrDef("dcmInstanceAvailability", desc.getInstanceAvailability(), null);
                    writer.writeNotDef("dcmReadOnly", desc.isReadOnly(), false);
                    writer.writeNotDef("dcmNoDeletionConstraint", desc.isNoDeletionConstraint(), false);
                    if (desc.getStorageThreshold() != null)
                        gen.write("storageThreshold", desc.getStorageThreshold().getMinUsableDiskSpace());
                    writeDeleterThresholds(writer, gen, desc.getDeleterThresholds());
                    writer.writeNotNullOrDef("dcmExternalRetrieveAET", desc.getExternalRetrieveAETitle(), null);
                    writer.writeNotNullOrDef("dcmExportStorageID", desc.getExportStorageID(), null);
                    if (desc.getRetrieveCacheStorageID() != null) {
                        gen.write("dcmRetrieveCacheStorageID", desc.getRetrieveCacheStorageID());
                        gen.write("dcmRetrieveCacheMaxParallel", desc.getRetrieveCacheMaxParallel());
                    }
                    writer.writeNotEmpty("dcmProperty", descriptorProperties(desc.getProperties()));
                    writer.writeNotEmpty("dicomAETitle", ss.aets);
                    writer.writeNotNullOrDef("dcmStorageClusterID", desc.getStorageClusterID(), null);
                    writer.writeNotEmpty("usages", ss.usages);
                    if (ss.usableSpace > 0L)
                        gen.write("usableSpace", ss.usableSpace);
                    if (ss.usableSpace > 0L)
                        gen.write("totalSpace", ss.totalSpace);
                    gen.writeEnd();
                }
                gen.writeEnd();
                gen.flush();
        };
    }

    private String[] descriptorProperties(Map<String, ?> props) {
        String[] ss = new String[props.size()];
        int i = 0;
        for (Map.Entry<String, ?> entry : props.entrySet())
            ss[i++] = entry.getKey() + '=' + entry.getValue();
        return ss;
    }

    private void writeDeleterThresholds(JsonWriter writer, JsonGenerator gen, List<DeleterThreshold> deleterThresholds) {
        if (deleterThresholds.isEmpty())
            return;
        writer.writeStartArray("deleterThreshold");
        for (DeleterThreshold deleterThreshold : deleterThresholds) {
            writer.writeStartObject();
            gen.write(deleterThreshold.getPrefix(), deleterThreshold.getMinUsableDiskSpace());
            writer.writeEnd();
        }
        writer.writeEnd();
    }

    private List<StorageSystem> getStorageSystems() {
        if (dicomAETitle != null) {
            ApplicationEntity ae = device.getApplicationEntity(dicomAETitle, true);
            if (ae == null || !ae.isInstalled()) {
                LOG.info("Archive AE {} not provided by Device {}", dicomAETitle, device.getDeviceName());
                return Collections.EMPTY_LIST;
            }
        }
        List<StorageSystem> storageSystems = new ArrayList<>();
        ArchiveDeviceExtension arcdev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
        for (StorageDescriptor desc : arcdev.getStorageDescriptors()) {
            String storageID = desc.getStorageID();
            Set<String> usages = new HashSet<>();
            Set<String> aets = new HashSet<>();
            if (StringUtils.contains(arcdev.getSeriesMetadataStorageIDs(), storageID)) {
                usages.add("dcmSeriesMetadataStorageID");
            }
            for (ApplicationEntity ae : device.getApplicationEntities()) {
                ArchiveAEExtension arcAE = ae.getAEExtension(ArchiveAEExtension.class);
                if (StringUtils.contains(arcAE.getObjectStorageIDs(), desc.getStorageID())) {
                    usages.add("dcmObjectStorageID");
                    aets.add(ae.getAETitle());
                }
                if (StringUtils.contains(arcAE.getMetadataStorageIDs(), desc.getStorageID())) {
                    usages.add("dcmMetadataStorageID");
                    aets.add(ae.getAETitle());
                }
            }
            if ((dicomAETitle == null || aets.contains(dicomAETitle))
                && (usage == null || usages.contains(usage))
                && (storageClusterID == null || storageClusterID.equals(desc.getStorageClusterID()))
                && (uriScheme == null || desc.getStorageURI().getScheme().equals(uriScheme))) {
                try (Storage storage = storageFactory.getStorage(desc)) {
                    long usableSpace = storage.getUsableSpace();
                    if (usableSpaceBelow == null || usableSpace < usableSpaceBelow) {
                        long totalSpace = storage.getTotalSpace();
                        storageSystems.add(new StorageSystem(desc, usableSpace, totalSpace, usages, aets));
                    }
                } catch (IOException e) {
                    LOG.warn("Failed to access {}", desc, e);
                }
            }
        }
        Collections.sort(storageSystems, Comparator.comparing(storageSystem -> storageSystem.desc.getStorageID()));
        return storageSystems;
    }

    private static class StorageSystem {
        final StorageDescriptor desc;
        final long usableSpace;
        final long totalSpace;
        final String[] usages;
        final String[] aets;

        StorageSystem(StorageDescriptor desc, long usableSpace, long totalSpace,
                      Collection<String> usages, Collection<String> aets) {
            this.desc = desc;
            this.usableSpace = usableSpace;
            this.totalSpace = totalSpace;
            this.usages = usages.toArray(StringUtils.EMPTY_STRING);
            this.aets = aets.toArray(StringUtils.EMPTY_STRING);
            Arrays.sort(this.usages);
            Arrays.sort(this.aets);
        }
    }
}
