/*
 * *** BEGIN LICENSE BLOCK *****
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
 * *** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.export.wado;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.IWebApplicationCache;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.WebApplication;
import org.dcm4che3.util.SafeClose;
import org.dcm4che3.util.StreamUtils;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.conf.StorageDescriptor;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.exporter.AbstractExporter;
import org.dcm4chee.arc.exporter.ExportContext;
import org.dcm4chee.arc.keycloak.AccessTokenRequestor;
import org.dcm4chee.arc.qmgt.Outcome;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.storage.Storage;
import org.dcm4chee.arc.storage.StorageFactory;
import org.dcm4chee.arc.storage.WriteContext;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Sep 2016
 */
public class WadoExporter extends AbstractExporter {

    private static final Logger LOG = LoggerFactory.getLogger(WadoExporter.class);

    private static final int COPY_BUFFER_SIZE = 8192;
    private final QueryService queryService;
    private final StorageFactory storageFactory;
    private final AccessTokenRequestor accessTokenRequestor;
    private final Device device;
    private final IWebApplicationCache webApplicationCache;
    private final EnumMap<Entity,List<WadoRequest>> wadoRequests = new EnumMap<>(Entity.class);
    private WebApplication wadoWebApp;

    public WadoExporter(
            ExporterDescriptor descriptor, QueryService queryService, StorageFactory storageFactory,
            Device device, AccessTokenRequestor accessTokenRequestor, IWebApplicationCache webApplicationCache) {
        super(descriptor);
        this.queryService = queryService;
        this.storageFactory = storageFactory;
        this.accessTokenRequestor = accessTokenRequestor;
        this.device = device;
        this.webApplicationCache = webApplicationCache;
        addWadoRequest();
    }

    private String token() {
        if (wadoWebApp.getKeycloakClient() == null)
            return null;

        String token = null;
        try {
            token = accessTokenRequestor.getAccessToken2(wadoWebApp).getToken();
            LOG.debug("Access token retrieved for Web Application[name={}, {}] is {}",
                    wadoWebApp.getApplicationName(), wadoWebApp.getKeycloakClient(), token);
        } catch (Exception e) {
            LOG.info("Failed to get access token for Web Application[name={}, KeycloakClientID={}] \n",
                    wadoWebApp.getApplicationName(), wadoWebApp.getKeycloakClient(), e);
        }
        return token;
    }

    private void addWadoRequest() {
        try {
            wadoWebApp = webApplicationCache.findWebApplication(descriptor.getExportURI().getSchemeSpecificPart());
            if (!wadoWebApp.containsServiceClass(WebApplication.ServiceClass.WADO_URI)
                    && !wadoWebApp.containsServiceClass(WebApplication.ServiceClass.WADO_RS)) {
                LOG.info("WADO web application does not contain any WADO service classes {}", wadoWebApp);
                return;
            }
            String wadoService = descriptor.getProperty("WadoService", null);
            if (wadoService == null) {
                LOG.info("WADO web application does not contain WADO service to be invoked {}", wadoWebApp);
                return;
            }
            String targetURI = wadoWebApp.getServiceURL() + wadoService;
            MessageFormat format = new MessageFormat(targetURI.replace('[', '{').replace(']', '}'));
            Entity entity = Entity.values()[format.getFormats().length];
            List<WadoRequest> list = wadoRequests.computeIfAbsent(entity, k -> new ArrayList<>(2));
            list.add(new WadoRequest(format, token(), device, descriptor));
        } catch (ConfigurationException e) {
            LOG.info("Failed to find Web Application for WADO request invocation : {}", e.getMessage());
        }
    }

    @Override
    public Outcome export(ExportContext exportContext) throws Exception {
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        int count = 0;
        int failed = 0;
        Exception ex = null;
        HashMap<String, Storage> storageMap = new HashMap<>();
        try {
            for (Map.Entry<Entity, List<WadoRequest>> entry : wadoRequests.entrySet()) {
                for (Object[] params : entry.getKey().queryParams(exportContext, queryService)) {
                    for (WadoRequest wadoRequest : entry.getValue()) {
                        try {
                            if (invoke(wadoRequest, params, buffer, storageMap))
                                count++;
                        } catch (Exception e) {
                            failed++;
                            ex = e;
                        }
                    }
                }
            }
        } finally {
            for (Storage storage : storageMap.values())
                SafeClose.close(storage);
        }

        String exporterID = exportContext.getExporter().getExporterDescriptor().getExporterID();
        if (failed == 0) {
            return new Outcome(QueueMessage.Status.COMPLETED,
                    "Fetched " + count + " objects by WADO Exporter " + exporterID);
        }
        if (count > 0) {
            return new Outcome(QueueMessage.Status.WARNING,
                    "Fetched " + count + " objects by WADO Exporter " + exporterID
                            + ", failed: " + failed + " - " + ex.getMessage());
        }
        throw ex;
    }

    private boolean invoke(WadoRequest wadoRequest, Object[] params, byte[] buffer, Map<String, Storage> storageMap)
            throws Exception {
        Invocation.Builder request = wadoRequest.openConnection(params, accessTokenRequestor);
        Response response = request.get();
        int responseStatus = response.getStatus();
        if (responseStatus == Response.Status.NOT_FOUND.getStatusCode()
                || responseStatus == Response.Status.UNAUTHORIZED.getStatusCode()) {
            LOG.info("Invocation of WADO request {} failed with status {}", wadoRequest.getTargetURL(), responseStatus);
            return false;
        }
        try (InputStream in = response.readEntity(InputStream.class);
             OutputStream out = getOutputStream(wadoRequest.storageDescriptor, params, storageMap)) {
            StreamUtils.copy(in, out, buffer);
        }
        return true;
    }

    private OutputStream getOutputStream(
            StorageDescriptor storageDescriptor, Object[] params, Map<String, Storage> storageMap) throws IOException {
        if (storageDescriptor == null)
            return null;

        Storage storage = storageMap.get(storageDescriptor.getStorageID());
        if (storage == null) {
            storage = storageFactory.getStorage(storageDescriptor);
            storageMap.put(storageDescriptor.getStorageID(), storage);
        }
        WriteContext ctx = storage.createWriteContext();
        Attributes attrs = new Attributes(params.length);
        switch (params.length) {
            case 4:
                attrs.setInt(Tag.ReferencedFrameNumber, VR.IS, (Integer) params[3]);
            case 3:
                attrs.setString(Tag.SOPInstanceUID, VR.UI, (String) params[2]);
            case 2:
                attrs.setString(Tag.SeriesInstanceUID, VR.UI, (String) params[1]);
            case 1:
                attrs.setString(Tag.StudyInstanceUID, VR.UI, (String) params[0]);
        }
        ctx.setAttributes(attrs);
        return storage.openOutputStream(ctx);
    }

    private enum Entity {
        CONST {
            @Override
            List<Object[]> queryParams(ExportContext ctx, QueryService queryService) {
                return Collections.singletonList(new Object[]{});
            }
        },
        STUDY {
            @Override
            List<Object[]> queryParams(ExportContext ctx, QueryService queryService) {
                return Collections.singletonList(new Object[]{ctx.getStudyInstanceUID()});
            }
        },
        SERIES {
            @Override
            List<Object[]> queryParams(ExportContext ctx, QueryService queryService) {
                return ctx.getSeriesInstanceUID() == null
                        ? queryService.getSeriesInstanceUIDs(ctx.getStudyInstanceUID())
                        : Collections.singletonList(new Object[]{
                            ctx.getStudyInstanceUID(),
                            ctx.getSeriesInstanceUID()});
            }
        },
        INSTANCE {
            @Override
            List<Object[]> queryParams(ExportContext ctx, QueryService queryService) {
                return ctx.getSeriesInstanceUID() == null
                        ? queryService.getSOPInstanceUIDs(ctx.getStudyInstanceUID())
                        : ctx.getSopInstanceUID() == null
                        ? queryService.getSOPInstanceUIDs(ctx.getStudyInstanceUID(), ctx.getSeriesInstanceUID())
                        : Collections.singletonList(new Object[]{
                            ctx.getStudyInstanceUID(),
                            ctx.getSeriesInstanceUID(),
                            ctx.getSopInstanceUID(),
                            queryService.getNumberOfFrames(
                                    ctx.getStudyInstanceUID(),
                                    ctx.getSeriesInstanceUID(),
                                    ctx.getSopInstanceUID())});
            }
        },
        FRAME {
            @Override
            List<Object[]> queryParams(ExportContext ctx, QueryService queryService) {
                List<Object[]> insts = INSTANCE.queryParams(ctx, queryService);
                ArrayList<Object[]> frames = new ArrayList<>(insts.size());
                for (Object[] inst : insts) {
                    Integer numFrames = (Integer) inst[3];
                    int n = numFrames != null ? numFrames.intValue() : 1;
                    for (int i = 1; i <= n; i++) {
                        Object[] frame = inst.clone();
                        frame[3] = i;
                        frames.add(frame);
                    }
                }
                return frames;
            }
        };

        abstract List<Object[]> queryParams(ExportContext ctx, QueryService queryService);
    }

    private static class WadoRequest {
        final MessageFormat format;
        final EnumMap<HeaderField,String> headerFields;
        final StorageDescriptor storageDescriptor;
        final String token;
        final boolean tlsAllowAnyHostname;
        final boolean tlsDisableTrustManager;
        final ExporterDescriptor exporterDescriptor;
        final Device device;
        String targetURL;

        WadoRequest(MessageFormat format, String token, Device device, ExporterDescriptor exporterDescriptor) {
            this.format = format;
            this.exporterDescriptor = exporterDescriptor;
            this.device = device;
            this.storageDescriptor = toStorageDescriptor();
            this.headerFields = toHeaderFields();
            this.token = token;
            this.tlsAllowAnyHostname = setTLSFields("TLSAllowAnyHostName");
            this.tlsDisableTrustManager = setTLSFields("TLSDisableTrustManager");
        }

        private enum HeaderField {
            Accept, Accept_Encoding, Cache_Control;

            @Override
            public String toString() {
                return name().replace('_', '-');
            }
        }

        private boolean setTLSFields(String key) {
            return Boolean.parseBoolean(exporterDescriptor.getProperty(key, null));
        }

        private StorageDescriptor toStorageDescriptor() {
            String storageID = exporterDescriptor.getProperty("StorageID", null);
            if (storageID == null)
                return null;

            ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
            StorageDescriptor storageDescriptor = arcDev.getStorageDescriptor(storageID);
            if (storageDescriptor == null)
                LOG.warn("WADO Exporter {} refers not configured StorageID={} - cannot store fetched objects",
                        exporterDescriptor.getExporterID(), storageID);
            return storageDescriptor;
        }

        private EnumMap<HeaderField, String> toHeaderFields() {
            EnumMap<HeaderField, String> headerFields = new EnumMap<>(HeaderField.class);
            for (HeaderField headerField : HeaderField.values()) {
                String value = exporterDescriptor.getProperty(headerField.toString(), null);
                if (value != null)
                    headerFields.put(headerField, value);
            }
            return headerFields;
        }

        String getTargetURL() {
            return targetURL;
        }

        Invocation.Builder openConnection(Object[] params, AccessTokenRequestor accessTokenRequestor)
                throws Exception {
            targetURL = format.format(params);
            ResteasyClient client = accessTokenRequestor.resteasyClientBuilder(
                    targetURL, tlsAllowAnyHostname, tlsDisableTrustManager).build();
            WebTarget target = client.target(targetURL);
            Invocation.Builder request = target.request();
            headerFields.forEach((k,v) -> request.header(k.toString(), v));
            if (token != null)
                request.header("Authorization", "Bearer " + token);
            return request;
        }
    }
}
