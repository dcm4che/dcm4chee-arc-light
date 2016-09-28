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
 * *** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.export.wado;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.Device;
import org.dcm4che3.util.SafeClose;
import org.dcm4che3.util.StreamUtils;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.conf.StorageDescriptor;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.exporter.AbstractExporter;
import org.dcm4chee.arc.exporter.ExportContext;
import org.dcm4chee.arc.qmgt.Outcome;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.storage.Storage;
import org.dcm4chee.arc.storage.StorageFactory;
import org.dcm4chee.arc.storage.WriteContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.MessageFormat;
import java.util.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Sep 2016
 */
public class WadoExporter extends AbstractExporter {

    private static Logger LOG = LoggerFactory.getLogger(WadoExporter.class);

    private static final int COPY_BUFFER_SIZE = 8192;
    private final QueryService queryService;
    private final StorageFactory storageFactory;
    private final EnumMap<Entity,List<WadoRequest>> wadoRequests = new EnumMap<>(Entity.class);

    public WadoExporter(ExporterDescriptor descriptor, QueryService queryService, StorageFactory storageFactory, Device device) {
        super(descriptor);
        this.queryService = queryService;
        this.storageFactory = storageFactory;
        EnumMap<HeaderField, String> headerFields0 = getHeaderFields(0, new EnumMap<>(HeaderField.class));
        String storageID = descriptor.getProperty("StorageID", null);
        addWadoRequest(descriptor.getExportURI().getSchemeSpecificPart(), headerFields0,
                storageDescriptor(device, storageID));
        String pattern;
        for (int i = 1; (pattern = descriptor.getProperty("URL." + i, null)) != null; ++i) {
            addWadoRequest(pattern,
                    getHeaderFields(i, new EnumMap<>(headerFields0)),
                    storageDescriptor(device, descriptor.getProperty("StorageID." + i, storageID)));
        }
    }

    private EnumMap<HeaderField, String> getHeaderFields(int i, EnumMap<HeaderField, String> headerFields) {
        for (HeaderField headerField : HeaderField.values()) {
            String name = headerField.toString();
            String value = descriptor.getProperty(i > 0 ? name + '.' + i : name, null);
            if (value != null)
                headerFields.put(headerField, value);
        }
        return headerFields;
    }

    private StorageDescriptor storageDescriptor(Device device, String storageID) {
        if (storageID == null)
            return null;

        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        StorageDescriptor storageDescriptor = arcDev.getStorageDescriptor(storageID);
        if (storageDescriptor == null)
            LOG.warn("WADO Exporter {} refers not configured StorageID={} - cannot store fetched objects",
                    descriptor.getExporterID(), storageID);
        return storageDescriptor;
    }

    private void addWadoRequest(String pattern, EnumMap<HeaderField,String> headerFields,
                                StorageDescriptor storageDescriptor) {
        MessageFormat format = new MessageFormat(pattern.replace('[', '{').replace(']', '}'));
        Entity entity = Entity.values()[format.getFormats().length];
        List<WadoRequest> list = wadoRequests.get(entity);
        if (list == null)
            wadoRequests.put(entity, list = new ArrayList<WadoRequest>(2));
        list.add(new WadoRequest(format, headerFields, storageDescriptor));
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

    private boolean invoke(WadoRequest request, Object[] params, byte[] buffer, Map<String, Storage> storageMap)
            throws Exception {
        HttpURLConnection httpConn = request.openConnection(params);
        int responseCode = httpConn.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_NOT_FOUND)
            return false;
        try (InputStream in = httpConn.getInputStream();
             OutputStream out = getOutputStream(request.storageDescriptor, params, storageMap)) {
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
        }, STUDY {
            @Override
            List<Object[]> queryParams(ExportContext ctx, QueryService queryService) {
                return Collections.singletonList(new Object[]{ctx.getStudyInstanceUID()});
            }
        }, SERIES {
            @Override
            List<Object[]> queryParams(ExportContext ctx, QueryService queryService) {
                return ctx.getSeriesInstanceUID().equals("*")
                        ? queryService.getSeriesInstanceUIDs(ctx.getStudyInstanceUID())
                        : Collections.singletonList(new Object[]{
                            ctx.getStudyInstanceUID(),
                            ctx.getSeriesInstanceUID()});
            }
        }, INSTANCE {
            @Override
            List<Object[]> queryParams(ExportContext ctx, QueryService queryService) {
                return ctx.getSeriesInstanceUID().equals("*")
                        ? queryService.getSOPInstanceUIDs(ctx.getStudyInstanceUID())
                        : ctx.getSopInstanceUID().equals("*")
                        ? queryService.getSOPInstanceUIDs(ctx.getStudyInstanceUID(), ctx.getSeriesInstanceUID())
                        : Collections.singletonList(new Object[]{
                            ctx.getStudyInstanceUID(),
                            ctx.getSeriesInstanceUID(),
                            ctx.getSopInstanceUID()});
            }
        }, FRAME {
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

    private enum HeaderField {
        Accept, Cache_Control;

        @Override
        public String toString() {
            return name().replace('_', '-');
        }
    }

    private static class WadoRequest {
        final MessageFormat format;
        final EnumMap<HeaderField,String> headerFields;
        final StorageDescriptor storageDescriptor;

        public WadoRequest(MessageFormat format, EnumMap<HeaderField,String> headerFields,
                            StorageDescriptor storageDescriptor) {
            this.format = format;
            this.headerFields = headerFields;
            this.storageDescriptor = storageDescriptor;
        }

        public HttpURLConnection openConnection(Object[] params) throws Exception {
            URL url = new URL(format.format(params));
            HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
            for (Map.Entry<HeaderField, String> entry : headerFields.entrySet()) {
                httpConn.setRequestProperty(entry.getKey().toString(), entry.getValue());
            }
            return httpConn;
        }
    }
}
