/*
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
 */

package org.dcm4chee.arc.storage.awss3;

import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.StorageDescriptor;
import org.dcm4chee.arc.metrics.MetricsService;
import org.dcm4chee.arc.storage.AbstractStorage;
import org.dcm4chee.arc.storage.ReadContext;
import org.dcm4chee.arc.storage.WriteContext;
import org.dcm4chee.arc.storage.UploadTaskWriteContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.*;

import java.io.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author Gunter Zeilinger <gunterze@protonmail.com>
 * @since Nov 2025
 */
public class AWSS3Storage extends AbstractStorage {

    private static final Logger LOG = LoggerFactory.getLogger(AWSS3Storage.class);

    private static final int MIN_PART_SIZE = 1024 * 1024 * 5;
    private static final int MIN_INIT_BUFFER_SIZE = 1024 * 10;
    private static final int DEF_INIT_BUFFER_SIZE = 1024 * 640;

    private final Device device;
    private final S3Client s3;
    private final String bucket;
    private final int partSize;
    private final int initBufferSize;
    private volatile boolean createBucket;

    public AWSS3Storage(StorageDescriptor descriptor, MetricsService metricsService, Device device) {
        super(descriptor, metricsService);
        this.device = device;
        this.bucket = bucket(descriptor.getStorageURI());
        this.s3 = s3Client(descriptor.getStorageURI(),
                descriptor.getProperty("aws_access_key_id", null),
                descriptor.getProperty("aws_secret_access_key", null));
        this.partSize = Math.max(
                getIntProperty(descriptor, "partSize", MIN_PART_SIZE),
                MIN_PART_SIZE);
        this.initBufferSize = Math.min(Math.max(
                getIntProperty(descriptor, "initBufferSize", DEF_INIT_BUFFER_SIZE),
                MIN_INIT_BUFFER_SIZE), partSize);
        this.createBucket = Boolean.parseBoolean(descriptor.getProperty("createBucket", null));
    }

    private static int getIntProperty(StorageDescriptor descriptor, String name, int defVal) {
        String val = descriptor.getProperty(name, null);
        return val != null ? Integer.parseInt(val) : defVal;
    }

    private static S3Client s3Client(URI storageURI, String accessKey, String secretKey) {
        S3ClientBuilder builder = S3Client.builder();
        String host = storageURI.getHost();
        if (host != null) {
            builder.region(Region.of(host));
        }
        if (accessKey != null && secretKey != null) {
            AwsCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
            builder.credentialsProvider(StaticCredentialsProvider.create(credentials));
        }
        return builder.build();
    }

    private static String bucket(URI storageURI) {
        String path = storageURI.getPath();
        if (path == null || path.length() < 2) {
            throw new IllegalArgumentException("Missing path/bucket name in " + storageURI);
        }
        return path.substring(1);
    }

    @Override
    protected Logger log() {
        return LOG;
    }

    @Override
    public WriteContext createWriteContext(String storagePath) {
        UploadTaskWriteContext writeContext = new UploadTaskWriteContext(this);
        writeContext.setStoragePath(storagePath);
        return writeContext;
    }

    @Override
    protected boolean existsA(ReadContext ctx) {
        return exists(ctx.getStoragePath());
    }

    @Override
    protected long getContentLengthA(ReadContext ctx) throws IOException {
        return headObjectThrowIOException(ctx.getStoragePath()).contentLength();
    }

    @Override
    protected OutputStream openOutputStreamA(final WriteContext ctx) throws IOException {
        if (createBucket) ensureBucketExists();
        if (!ensureStoragePathNotExists(ctx)) {
            return OutputStream.nullOutputStream();
        }
        final PipedInputStream in = new PipedInputStream();
        FutureTask<Void> task = new FutureTask<>(() -> {
            try {
                upload(ctx, in);
            } finally {
                in.close();
            }
            return null;
        });
        ((UploadTaskWriteContext) ctx).setUploadTask(task);
        device.execute(task);
        return new PipedOutputStream(in);
    }

    @Override
    protected void copyA(InputStream in, WriteContext ctx) throws IOException {
        if (createBucket) ensureBucketExists();
        if (ensureStoragePathNotExists(ctx))
            upload(ctx, in);
    }

    @Override
    protected void afterOutputStreamClosed(WriteContext ctx) throws IOException {
        FutureTask<Void> task = ((UploadTaskWriteContext) ctx).getUploadTask();
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

    private boolean exists(String storagePath) {
        try {
            headObject(storagePath);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    private HeadObjectResponse headObjectThrowIOException(String storagePath) throws IOException {
        try {
            return headObject(storagePath);
        } catch (NoSuchBucketException|NoSuchKeyException e) {
            throw objectNotFound(storagePath);
        } catch (S3Exception e) {
            throw failedToAccess(storagePath, e);
        }
    }

    private HeadObjectResponse headObject(String storagePath) {
        return s3.headObject(response -> response.bucket(bucket).key(storagePath));
    }

    private void ensureBucketExists() {
        try {
            s3.headBucket(request1 -> request1.bucket(bucket));
        } catch (NoSuchBucketException e) {
            s3.createBucket(request1 -> request1.bucket(bucket));
        }
        createBucket = false;
    }

    private boolean ensureStoragePathNotExists(WriteContext ctx) throws IOException {
        String storagePath = ctx.getStoragePath();
        if (exists(storagePath)) {
            switch (descriptor.getOnStoragePathAlreadyExists()) {
                case NOOP:
                    ctx.setDeletionLock(true);
                    return false;
                case FAILURE:
                    ctx.setDeletionLock(true);
                    throw alreadyExists(storagePath);
                default: // case RANDOM_PATH
                    do {
                        ctx.setStoragePath(storagePath = storagePath.substring(0, storagePath.lastIndexOf('/') + 1)
                                .concat(String.format("%08X", ThreadLocalRandom.current().nextInt())));
                    } while (exists(storagePath));
            }
        }
        return true;
    }

    private void upload(WriteContext ctx, InputStream in) throws IOException {
        try {
            byte[] bb = new byte[initBufferSize];
            int off = 0;
            int read;
            while (off + (read = in.readNBytes(bb, off, bb.length - off)) == bb.length) {
                if (bb.length == partSize) {
                    uploadMultipleParts(in, ctx.getStoragePath(), bb);
                    return;
                }
                off = bb.length;
                bb = Arrays.copyOf(bb, Math.min(bb.length * 2, partSize));
            }
            s3.putObject(
                    builder -> builder.bucket(bucket).key(ctx.getStoragePath()),
                    RequestBody.fromByteBuffer(ByteBuffer.wrap(bb, 0, off + read)));
        } catch (S3Exception e) {
            throw failedToUpload(ctx.getStoragePath(), e);
        }
    }

    private void uploadMultipleParts(InputStream in, String key, byte[] bb) throws IOException {
        String uploadId = s3.createMultipartUpload(builder -> builder.bucket(bucket).key(key)).uploadId();
        List<CompletedPart> completedParts = new ArrayList<>();
        int read;
        int partNumber = 1;
        do {
            completedParts.add(uploadPart(key, uploadId, partNumber++,
                    RequestBody.fromBytes(bb)));
        } while ((read = in.readNBytes(bb, 0, bb.length)) == bb.length);
        if (read > 0) {
            completedParts.add(uploadPart(key, uploadId, partNumber,
                    RequestBody.fromByteBuffer(ByteBuffer.wrap(bb, 0, read))));
        }
        s3.completeMultipartUpload(builder -> builder
                .bucket(bucket)
                .key(key)
                .uploadId(uploadId)
                .multipartUpload(upload -> upload.parts(completedParts)));
    }

    private CompletedPart uploadPart(String key, String uploadId, int partNumber, RequestBody requestBody) {
        UploadPartResponse partResponse = s3.uploadPart(builder -> builder
                        .bucket(bucket)
                        .key(key)
                        .uploadId(uploadId)
                        .partNumber(partNumber),
                requestBody);
        return CompletedPart.builder()
                .partNumber(partNumber)
                .eTag(partResponse.eTag())
                .build();
    }

    @Override
    protected InputStream openInputStreamA(ReadContext readContext) throws IOException {
        try {
            return s3.getObject(
                    request -> request.bucket(bucket).key(readContext.getStoragePath()));
        } catch (NoSuchBucketException|NoSuchKeyException e) {
            throw objectNotFound(readContext.getStoragePath());
        } catch (S3Exception e) {
            throw failedToAccess(readContext.getStoragePath(), e);
        }
    }

    @Override
    protected void deleteObjectA(String storagePath) throws IOException {
        try {
            s3.deleteObject(request -> request.bucket(bucket).key(storagePath));
        } catch (S3Exception e) {
            throw failedToDelete(storagePath, e);
        }
    }

    @Override
    public void close() throws IOException {
        s3.close();
    }

    private IOException objectNotFound(String storagePath) {
        return new NoSuchFileException("No Object[" + storagePath
                + "] on " + getStorageDescriptor());
    }

    private IOException failedToAccess(String storagePath, S3Exception e) {
        return new IOException("Failed to access Object[" + storagePath
                + "] on " + getStorageDescriptor(),
                e);
    }

    private IOException failedToUpload(String storagePath, S3Exception e) {
        return new IOException("Failed to upload Object[" + storagePath
                + "] to " + getStorageDescriptor(),
                e);
    }

    private IOException alreadyExists(String storagePath) {
        return new IOException("Object[" + storagePath
                + "] on " + getStorageDescriptor() + " already exists");
    }

    private IOException failedToDelete(String storagePath, S3Exception e) {
        return new IOException("Failed to delete Object[" + storagePath
                + "] from " + getStorageDescriptor(),
                e);
    }

}
