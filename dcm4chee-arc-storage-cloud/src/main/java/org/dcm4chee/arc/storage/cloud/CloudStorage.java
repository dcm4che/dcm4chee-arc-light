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
 * Portions created by the Initial Developer are Copyright (C) 2015
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

package org.dcm4chee.arc.storage.cloud;

import com.google.common.hash.HashCode;
import org.dcm4che3.net.Device;
import org.dcm4che3.util.AttributesFormat;
import org.dcm4chee.arc.conf.BinaryPrefix;
import org.dcm4chee.arc.conf.StorageDescriptor;
import org.dcm4chee.arc.metrics.MetricsService;
import org.dcm4chee.arc.storage.AbstractStorage;
import org.dcm4chee.arc.storage.ReadContext;
import org.dcm4chee.arc.storage.WriteContext;
import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.blobstore.domain.BlobMetadata;
import org.jclouds.io.Payload;
import org.jclouds.io.payloads.InputStreamPayload;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;

import java.io.*;
import java.nio.file.NoSuchFileException;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author Steve Kroetsch<stevekroetsch@hotmail.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
public class CloudStorage extends AbstractStorage {

    private static final String DEFAULT_CONTAINER = "org.dcm4chee.arc";
    private static final Uploader STREAMING_UPLOADER = new Uploader() {
        @Override
        public void upload(BlobStoreContext context, InputStream in, long length,
                           BlobStore blobStore, String container, String storagePath) {
            Payload payload = new InputStreamPayload(in);
            if (length >= 0)
                payload.getContentMetadata().setContentLength(length);
            Blob blob = blobStore.blobBuilder(storagePath).payload(payload).build();
            blobStore.putBlob(container, blob);
        }
    };
    private final Device device;
    private final AttributesFormat pathFormat;
    private final String container;
    private final BlobStoreContext context;
    private final boolean streamingUpload;
    private final long maxPartSize;
    private int count;

    @Override
    public WriteContext createWriteContext() {
        return new CloudWriteContext(this);
    }

    protected CloudStorage(StorageDescriptor descriptor, MetricsService metricsService, Device device) {
        super(descriptor, metricsService);
        this.device = device;
        pathFormat = new AttributesFormat(descriptor.getProperty("pathFormat", DEFAULT_PATH_FORMAT));
        container = descriptor.getProperty("container", DEFAULT_CONTAINER);
        if (Boolean.parseBoolean(descriptor.getProperty("containerExists", null))) count++;
        String api = descriptor.getStorageURI().getSchemeSpecificPart();
        String endpoint = null;
        int endApi = api.indexOf(':');
        if (endApi != -1) {
            endpoint = api.substring(endApi + 1);
            api = api.substring(0, endApi);
        }
        this.streamingUpload = Boolean.parseBoolean(descriptor.getProperty("streamingUpload", null));
        this.maxPartSize = BinaryPrefix.parse(descriptor.getProperty("maxPartSize", "5G"));
        ContextBuilder ctxBuilder = ContextBuilder.newBuilder(api);
        String identity = descriptor.getProperty("identity", null);
        if (identity != null)
            ctxBuilder.credentials(identity, descriptor.getProperty("credential", null));
        if (endpoint != null)
            ctxBuilder.endpoint(endpoint);
        Properties overrides = new Properties();
        for (Map.Entry<String, String> entry : descriptor.getProperties().entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("jclouds."))
                overrides.setProperty(key, entry.getValue());
        }
        ctxBuilder.overrides(overrides);
        ctxBuilder.modules(Collections.singleton(new SLF4JLoggingModule()));
        context = ctxBuilder.buildView(BlobStoreContext.class);
    }

    @Override
    protected OutputStream openOutputStreamA(final WriteContext ctx) throws IOException {
        final PipedInputStream in = new PipedInputStream();
        PipedOutputStream out = new PipedOutputStream(in);
        FutureTask<Void> task = new FutureTask<>(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                try {
                    upload(ctx, in);
                } finally {
                    in.close();
                }
                return null;
            }
        });
        ((CloudWriteContext) ctx).setUploadTask(task);
        device.execute(task);
        return out;
    }

    @Override
    protected void copyA(InputStream in, WriteContext ctx) throws IOException {
        upload(ctx, in);
    }

    @Override
    protected void afterOutputStreamClosed(WriteContext ctx) throws IOException {
        FutureTask<Void> task = ((CloudWriteContext) ctx).getUploadTask();
        try {
            task.get();
        } catch (InterruptedException e) {
            throw new InterruptedIOException();
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            if (c instanceof IOException)
                throw (IOException) c;
            throw new IOException("Upload failed", c);
        }
    }

    private void upload(WriteContext ctx, InputStream in) throws IOException {
        if (isSynchronizeUpload())
            synchronized (descriptor) {
                upload(in, ctx);
            }
        else
            upload(in, ctx);
    }

    private void upload(InputStream in, WriteContext ctx) throws IOException {
        BlobStore blobStore = context.getBlobStore();
        String storagePath = pathFormat.format(ctx.getAttributes());
        if (count++ == 0 && !blobStore.containerExists(container))
            blobStore.createContainerInLocation(null, container);
        else {
            while (blobStore.blobExists(container, storagePath))
                storagePath = storagePath.substring(0, storagePath.lastIndexOf('/') + 1)
                        .concat(String.format("%08X", ThreadLocalRandom.current().nextInt()));
        }
        long length = ctx.getContentLength();
        Uploader uploader = streamingUpload || length >= 0 && length <= maxPartSize
                ? STREAMING_UPLOADER : new S3Uploader();
        uploader.upload(context, in, length, blobStore, container, storagePath);
        ctx.setStoragePath(storagePath);
    }

    private boolean isSynchronizeUpload() {
        return "true".equals(descriptor.getProperty("synchronizeUpload", "false"));
    }

    @Override
    protected InputStream openInputStreamA(ReadContext ctx) throws IOException {
        BlobStore blobStore = context.getBlobStore();
        Blob blob = blobStore.getBlob(container, ctx.getStoragePath());
        if (blob == null)
            throw objectNotFound(ctx.getStoragePath());
        return blob.getPayload().openStream();
    }

    @Override
    public boolean exists(ReadContext ctx) {
        BlobStore blobStore = context.getBlobStore();
        return blobStore.blobExists(container, ctx.getStoragePath());
    }

    @Override
    public long getContentLength(ReadContext ctx) throws IOException {
        BlobStore blobStore = context.getBlobStore();
        BlobMetadata blobMetadata = blobStore.blobMetadata(container, ctx.getStoragePath());
        if (blobMetadata == null)
            throw objectNotFound(ctx.getStoragePath());

        return blobMetadata.getContentMetadata().getContentLength();
    }

    @Override
    public byte[] getContentMD5(ReadContext ctx) throws IOException {
        BlobStore blobStore = context.getBlobStore();
        BlobMetadata blobMetadata = blobStore.blobMetadata(container, ctx.getStoragePath());
        if (blobMetadata == null)
            throw objectNotFound(ctx.getStoragePath());

        HashCode hashCode = blobMetadata.getContentMetadata().getContentMD5AsHashCode();
        return hashCode != null ? hashCode.asBytes() : null;
    }

    @Override
    protected void deleteObjectA(String storagePath) throws IOException {
        BlobStore blobStore = context.getBlobStore();
        if (!blobStore.blobExists(container, storagePath))
            throw objectNotFound(storagePath);
        blobStore.removeBlob(container, storagePath);
    }

    private IOException objectNotFound(String storagePath) {
        return new NoSuchFileException("No Object[" + storagePath
                + "] in Container[" + container
                + "] on " + getStorageDescriptor());
    }

    @Override
    public void close() throws IOException {
        context.close();
    }
}
