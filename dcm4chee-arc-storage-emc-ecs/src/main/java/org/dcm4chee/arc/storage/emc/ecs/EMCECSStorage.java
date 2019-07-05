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

package org.dcm4chee.arc.storage.emc.ecs;

import com.emc.object.s3.S3Client;
import com.emc.object.s3.S3Config;
import com.emc.object.s3.S3Exception;
import com.emc.object.s3.S3ObjectMetadata;
import com.emc.object.s3.bean.GetObjectResult;
import com.emc.object.s3.jersey.S3JerseyClient;
import com.emc.object.s3.request.PutObjectRequest;
import com.sun.jersey.client.urlconnection.URLConnectionClientHandler;
import org.dcm4che3.net.Device;
import org.dcm4che3.util.AttributesFormat;
import org.dcm4che3.util.TagUtils;
import org.dcm4chee.arc.conf.BinaryPrefix;
import org.dcm4chee.arc.conf.StorageDescriptor;
import org.dcm4chee.arc.metrics.MetricsService;
import org.dcm4chee.arc.storage.AbstractStorage;
import org.dcm4chee.arc.storage.ReadContext;
import org.dcm4chee.arc.storage.WriteContext;

import java.io.*;
import java.net.URI;
import java.nio.file.NoSuchFileException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2016
 */
public class EMCECSStorage extends AbstractStorage {

    public static final String PROPERTY_URL_CONNECTION_CLIENT_HANDLER = "emc-ecs-s3.URLConnectionClientHandler";

    private static final String DEFAULT_CONTAINER = "org.dcm4chee.arc";
    private static final Uploader STREAMING_UPLOADER = new Uploader() {
        @Override
        public void upload(S3Client s3, InputStream in, long length, String container, String storagePath) {
            PutObjectRequest payload = new PutObjectRequest(container, storagePath, this);
            if (length >= 0)
                payload.withObjectMetadata(new S3ObjectMetadata().withContentLength(length));
            s3.putObject(payload);
        }
    };

    private final Device device;
    private final AttributesFormat pathFormat;
    private final String container;
    private final S3Client s3;
    private final boolean streamingUpload;
    private final long maxPartSize;
    private int count;

    public EMCECSStorage(StorageDescriptor descriptor, MetricsService metricsService, Device device) {
        super(descriptor, metricsService);
        this.device = device;
        pathFormat = new AttributesFormat(descriptor.getProperty("pathFormat", DEFAULT_PATH_FORMAT));
        container = descriptor.getProperty("container", DEFAULT_CONTAINER);
        if (Boolean.parseBoolean(descriptor.getProperty("containerExists", null))) count++;
        String endpoint = descriptor.getStorageURI().getSchemeSpecificPart();
        S3Config config = new S3Config(URI.create(endpoint));
        String identity = descriptor.getProperty("identity", null);
        if (identity != null)
            config.withIdentity(identity).withSecretKey(descriptor.getProperty("credential", null));
        this.streamingUpload = Boolean.parseBoolean(descriptor.getProperty("streamingUpload", null));
        this.maxPartSize = BinaryPrefix.parse(descriptor.getProperty("maxPartSize", "5G"));
        s3 = new S3JerseyClient(config,
                Boolean.parseBoolean(descriptor.getProperty(PROPERTY_URL_CONNECTION_CLIENT_HANDLER, null))
                        ? new URLConnectionClientHandler()
                        : null);
    }

    @Override
    public WriteContext createWriteContext() {
        return new EMCECSWriteContext(this);
    }

    @Override
    public boolean exists(ReadContext ctx) {
        return exists(ctx.getStoragePath());
    }

    @Override
    public long getContentLength(ReadContext ctx) throws IOException {
        return getObjectMetadata(ctx.getStoragePath()).getContentLength();
    }

    @Override
    public byte[] getContentMD5(ReadContext ctx) throws IOException {
        String contentMd5 = getObjectMetadata(ctx.getStoragePath()).getContentMd5();
        return contentMd5 != null ? TagUtils.fromHexString(contentMd5) : null;
    }

    @Override
    protected OutputStream openOutputStreamA(final WriteContext ctx) throws IOException {
        final PipedInputStream in = new PipedInputStream();
        copy(in, ctx);
        return new PipedOutputStream(in);
    }

    @Override
    protected void copyA(InputStream in, WriteContext ctx) throws IOException {
        upload(ctx, in);
    }

    @Override
    protected void afterOutputStreamClosed(WriteContext ctx) throws IOException {
        FutureTask<Void> task = ((EMCECSWriteContext) ctx).getUploadTask();
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
        String storagePath = pathFormat.format(ctx.getAttributes());
        if (count++ == 0 && !s3.bucketExists(container))
            s3.createBucket(container);
        else while (exists(storagePath)) {
            storagePath = storagePath.substring(0, storagePath.lastIndexOf('/') + 1)
                    .concat(String.format("%08X", ThreadLocalRandom.current().nextInt()));
        }
        long length = ctx.getContentLength();
        Uploader uploader = streamingUpload || length >= 0 && length <= maxPartSize
                ? STREAMING_UPLOADER : new S3Uploader();
        uploader.upload(s3, in, length, container, storagePath);
        ctx.setStoragePath(storagePath);
    }

    private boolean exists(String storagePath) {
        try {
            return s3.getObjectMetadata(container, storagePath) != null;
        } catch (S3Exception e) {
        }
        return false;
    }

    private S3ObjectMetadata getObjectMetadata(String storagePath) throws IOException {
        try {
            S3ObjectMetadata metadata = s3.getObjectMetadata(container, storagePath);
            if (metadata == null)
                throw objectNotFound(storagePath);

            return metadata;
        } catch (S3Exception e) {
            throw failedToAccess(storagePath, e);
        }
    }

    @Override
    protected InputStream openInputStreamA(ReadContext readContext) throws IOException {
        GetObjectResult<InputStream> s3Object = s3.getObject(container, readContext.getStoragePath());
        if (s3Object == null)
            throw objectNotFound(readContext.getStoragePath());

        return s3Object.getObject();
    }

    private IOException objectNotFound(String storagePath) {
        return new NoSuchFileException("No Object[" + storagePath
                + "] in Container[" + container
                + "] on " + getStorageDescriptor());
    }

    private IOException failedToAccess(String storagePath, S3Exception e) {
        return new IOException("Failed to access Object[" + storagePath
                + "] in Container[" + container
                + "] on " + getStorageDescriptor(),
                e);
    }

    @Override
    protected void deleteObjectA(String storagePath) throws IOException {
        s3.deleteObject(container, storagePath);
    }

    @Override
    public void close() throws IOException {
        s3.destroy();
    }
}
