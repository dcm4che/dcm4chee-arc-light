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
 *  Java(TM), hosted at https://github.com/gunterze/dcm4che.
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
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
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
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collection;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since May 2017
 */

@Path("storage")
@RequestScoped
public class StorageRS {

    private static final Logger LOG = LoggerFactory.getLogger(StorageRS.class);
    private ArchiveAEExtension arcAE;

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

    @GET
    @NoCache
    @Produces("application/json")
    public StreamingOutput search() throws Exception {
        return new StreamingOutput() {
            @Override
            public void write (OutputStream out) throws IOException {
                JsonGenerator gen = Json.createGenerator(out);
                gen.writeStartArray();
                for (StorageSystem ss : getStorageSystems()) {
                    StorageDescriptor desc = ss.desc;
                    List<ArchiveAEExtension> arcAEs = ss.arcAEs;
                    JsonWriter writer = new JsonWriter(gen);
                    gen.writeStartObject();
                    gen.write("dcmStorageID", desc.getStorageID());
                    gen.write("dcmURI", desc.getStorageURIStr());
                    writer.writeNotNull("dcmDigestAlgorithm", desc.getDigestAlgorithm());
                    writer.writeNotNull("dcmInstanceAvailability", desc.getInstanceAvailability());
                    writer.writeNotDef("dcmReadOnly", desc.isReadOnly(), false);
                    if (desc.getStorageThreshold() != null)
                        writer.writeNotNull("dcmStorageThreshold", desc.getStorageThreshold().getMinUsableDiskSpace());
                    writer.writeNotEmpty("dcmDeleterThreshold", desc.getDeleterThresholdsAsStrings());
                    writer.writeNotNull("dcmExternalRetrieveAET", desc.getExternalRetrieveAETitle());
                    writer.writeNotEmpty("dcmProperty", descriptorProperties(desc.getProperties()));
                    writeAEInfo(writer, arcAEs);
                    if (isSeriesStorageReferenced(desc))
                        writer.writeNotNull("dcmSeriesMetadataStorageID", true);
                    writeSpaceInfo(writer, desc);
                    gen.writeEnd();
                }
                gen.writeEnd();
                gen.flush();
            }
        };
    }

    enum Case {
        ONLY_USAGE, ONLY_URI, ONLY_AE, URI_USAGE, AET_USAGE, AET_URI, ALL, NO_PARAMS
    }

    private String[] descriptorProperties(Map<String, ?> props) {
        String[] ss = new String[props.size()];
        int i = 0;
        for (Map.Entry<String, ?> entry : props.entrySet())
            ss[i++] = entry.getKey() + '=' + entry.getValue();
        return ss;
    }

    private void writeAEInfo(JsonWriter writer, List<ArchiveAEExtension> arcAEs) {
        if (arcAEs.isEmpty())
            return;
        writer.writeStartArray("dicomNetworkAE");
        for (ArchiveAEExtension arcAE : arcAEs) {
            writer.writeStartObject();
            writer.writeNotNull("dicomAETitle", arcAE.getApplicationEntity().getAETitle());
            writer.writeNotDef("dcmObjectStorageID", arcAE.getObjectStorageIDs().length > 0, false);
            writer.writeNotDef("dcmMetadataStorageID", arcAE.getMetadataStorageIDs().length > 0, false);
            writer.writeEnd();
        }
        writer.writeEnd();
    }

    private void writeSpaceInfo(JsonWriter writer, StorageDescriptor desc) {
        try (Storage storage = storageFactory.getStorage(desc)) {
            if (storage.getUsableSpace() != -1L)
                writer.writeNotNull("usableSpace", storage.getUsableSpace());
            if (storage.getTotalSpace() != -1L)
                writer.writeNotNull("totalSpace", storage.getTotalSpace());
        } catch (IOException e) {
            LOG.warn("Failed to access {}", desc.getStorageURI(), e);
        }
    }

    private List<StorageSystem> getStorageSystems() {
        List<StorageSystem> storageSystems = new ArrayList<>();
        if (isBadRequest())
            throw new WebApplicationException(
                    getResponse("Bad Request. Combination of query parameters not supported.",
                            Response.Status.BAD_REQUEST));
        Case case1 = getCase();
        if (case1 != null) {
            List<StorageDescriptor> sortedStorageDescriptors = getSortedStorageDescriptors();
            for (StorageDescriptor desc : sortedStorageDescriptors) {
                List<ArchiveAEExtension> arcAEs = getMatchingAEs(desc);
                StorageSystem ss = new StorageSystem(desc, arcAEs);
                storageSystems.add(ss);
            }
            if (case1 != Case.NO_PARAMS)
                getStorageSystemsMatchingQueryParams(storageSystems, case1);
        }
        return storageSystems;
    }

    private void getStorageSystemsMatchingQueryParams(List<StorageSystem> storageSystems, Case case1) {
        Iterator<StorageSystem> iter = storageSystems.iterator();
        while (iter.hasNext()) {
            StorageSystem ss = iter.next();
            switch (case1) {
                case ONLY_URI:
                    if (!isMatchingURIScheme(ss.desc))
                        iter.remove();
                    break;
                case ONLY_AE:
                    if (!ss.arcAEs.contains(arcAE))
                        iter.remove();
                    break;
                case ONLY_USAGE:
                    if (!isStorageReferenced(ss))
                        iter.remove();
                    break;
                case AET_USAGE:
                    if (!ss.arcAEs.contains(arcAE) || !isStorageReferenced(ss))
                        iter.remove();
                    break;
                case AET_URI:
                    if (!ss.arcAEs.contains(arcAE) || !isMatchingURIScheme(ss.desc))
                        iter.remove();
                    break;
                case URI_USAGE:
                    if (!isMatchingURIScheme(ss.desc) || !isStorageReferenced(ss))
                        iter.remove();
                    break;
                case ALL:
                    if (!isMatchingURIScheme(ss.desc) || !isStorageReferenced(ss) || !ss.arcAEs.contains(arcAE))
                        iter.remove();
                    break;
            }
        }
    }

    private boolean isMatchingURIScheme(StorageDescriptor desc) {
        return desc.getStorageURI().getScheme().equals(uriScheme);
    }

    private boolean isStorageReferenced(StorageSystem storageSystem) {
        return (!usage.equals("dcmSeriesMetadataStorageID") && !storageSystem.arcAEs.isEmpty()
                && isObjectOrMetadataStorageReferenced(storageSystem.desc))
            || (usage.equals("dcmSeriesMetadataStorageID") && isSeriesStorageReferenced(storageSystem.desc));
    }

    private boolean isObjectOrMetadataStorageReferenced(StorageDescriptor desc) {
        return (usage.equals("dcmObjectStorageID") && arcAE.getObjectStorageIDs().length > 0
                    && Arrays.asList(arcAE.getObjectStorageIDs()).contains(desc.getStorageID()))
                || (usage.equals("dcmMetadataStorageID") && arcAE.getMetadataStorageIDs().length > 0
                    && Arrays.asList(arcAE.getMetadataStorageIDs()).contains(desc.getStorageID()));
    }

    private boolean isSeriesStorageReferenced(StorageDescriptor desc) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        return arcDev.getSeriesMetadataStorageIDs().length > 0
                && Arrays.asList(arcDev.getSeriesMetadataStorageIDs()).contains(desc.getStorageID());
    }

    private List<StorageDescriptor> getSortedStorageDescriptors() {
        ArchiveDeviceExtension ext = device.getDeviceExtension(ArchiveDeviceExtension.class);
        List<StorageDescriptor> storageDescriptors = new ArrayList<>();
        storageDescriptors.addAll(ext.getStorageDescriptors());
        storageDescriptors.sort(Comparator.comparing(StorageDescriptor::getStorageID));
        return storageDescriptors;
    }

    private List<ArchiveAEExtension> getMatchingAEs(StorageDescriptor desc) {
        Collection<String> aets = device.getApplicationAETitles();
        List<ArchiveAEExtension> arcAEs = new ArrayList<>();
        for (String aet : aets) {
            ApplicationEntity ae = device.getApplicationEntity(aet);
            ArchiveAEExtension arcAE = ae.getAEExtension(ArchiveAEExtension.class);
            if (Arrays.asList(arcAE.getObjectStorageIDs()).contains(desc.getStorageID())
                    || Arrays.asList(arcAE.getMetadataStorageIDs()).contains(desc.getStorageID()))
                arcAEs.add(arcAE);
        }
        return arcAEs;
    }

    private boolean isBadRequest() {
        return (dicomAETitle != null && usage != null && usage.equals("dcmSeriesMetadataStorageID"))
            || (dicomAETitle == null && usage != null && (usage.equals("dcmMetadataStorageID") || usage.equals("dcmObjectStorageID")));
    }

    private Response getResponse(String errorMessage, Response.Status status) {
        Object entity = "{\"errorMessage\":\"" + errorMessage + "\"}";
        return Response.status(status).entity(entity).build();
    }

    private Case getCase() {
        if (dicomAETitle != null) {
            ApplicationEntity ae = device.getApplicationEntity(dicomAETitle, true);
            if (!(ae != null && ae.isInstalled())) {
                LOG.info("Dicom AE Title in query param is not installed.");
                return null;
            }
            this.arcAE = ae.getAEExtension(ArchiveAEExtension.class);
        }
        return usage != null
                ? uriScheme != null
                    ? dicomAETitle != null
                        ? Case.ALL
                        : Case.URI_USAGE
                    : dicomAETitle != null
                        ? Case.AET_USAGE
                        : Case.ONLY_USAGE
                : uriScheme != null
                    ? dicomAETitle != null
                        ? Case.AET_URI
                        : Case.ONLY_URI
                : dicomAETitle != null
                    ? Case.ONLY_AE : Case.NO_PARAMS;
    }

    class StorageSystem {
        private StorageDescriptor desc;
        private List<ArchiveAEExtension> arcAEs;

        StorageSystem(StorageDescriptor desc, List<ArchiveAEExtension> arcAEs) {
            this.desc = desc;
            this.arcAEs = arcAEs;
        }
    }
}
