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
 * Portions created by the Initial Developer are Copyright (C) 2013
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

package org.dcm4chee.arc.storage.filesystem;

import org.dcm4chee.arc.conf.StorageDescriptor;
import org.dcm4chee.arc.metrics.MetricsService;
import org.dcm4chee.arc.storage.AbstractStorage;
import org.dcm4chee.arc.storage.ReadContext;
import org.dcm4chee.arc.storage.WriteContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.FileAttribute;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2015
 */
public class FileSystemStorage extends AbstractStorage {

    private static final Logger LOG = LoggerFactory.getLogger(FileSystemStorage.class);
    private static final int COPY_BUFFER_SIZE = 8192;

    private final URI rootURI;
    private final Path checkMountFilePath;
    private final CreateDirectories createDirectories;

    @FunctionalInterface
    private interface CreateDirectories {
        Path apply(Path dir, FileAttribute<?>... attrs) throws IOException;
    }

    public FileSystemStorage(StorageDescriptor descriptor, MetricsService metricsService) {
        super(descriptor, metricsService);
        rootURI = ensureTrailingSlash(descriptor.getStorageURI());
        String checkMountFile = descriptor.getCheckMountFilePath();
        checkMountFilePath = checkMountFile != null ?  Paths.get(rootURI.resolve(checkMountFile)) : null;
        createDirectories = descriptor.isAltCreateDirectories()
            ? FileSystemStorage::altCreateDirectories
            : Files::createDirectories;
    }

    @Override
    protected Logger log() {
        return LOG;
    }

    private static Path altCreateDirectories(Path path, FileAttribute<?>... fileAttributes)
            throws IOException {
        try {
            return Files.createDirectory(path, fileAttributes);
        } catch (FileAlreadyExistsException e) {
            return path;
        } catch (NoSuchFileException e) {
            createParentDirectories(path, fileAttributes);
            return Files.createDirectory(path, fileAttributes);
        }
    }

    private static void createParentDirectories(Path path, FileAttribute<?>... fileAttributes)
            throws IOException {
        Path parent = path.getParent();
        if (parent == null)
            throw new FileSystemException(path.toString(), null,
                    "Unable to determine if root directory exists");
        try {
            Files.createDirectory(parent, fileAttributes);
        } catch (NoSuchFileException e) {
            createParentDirectories(parent, fileAttributes);
            Files.createDirectory(parent, fileAttributes);
        }
    }

    private URI ensureTrailingSlash(URI uri) {
        String s = uri.toString();
        return (s.charAt(s.length()-1) == '/') ? uri : URI.create(s + '/');
    }

    @Override
    public boolean isAccessable() {
        return checkMountFilePath == null || Files.notExists(checkMountFilePath);
    }

    @Override
    public boolean exists(ReadContext ctx) {
        Path path = Paths.get(rootURI.resolve(ctx.getStoragePath()));
        return Files.exists(path);
    }

    @Override
    public long getContentLength(ReadContext ctx) throws IOException {
        Path path = Paths.get(rootURI.resolve(ctx.getStoragePath()));
        return Files.size(path);
    }

    @Override
    public long getUsableSpace() throws IOException {
        return getFileStore().getUsableSpace();
    }

    private FileStore getFileStore() throws IOException {
        Path dir = Paths.get(rootURI);
        createDirectories(dir);
        return Files.getFileStore(dir);
    }

    private Path createDirectories(Path path) throws IOException {
        int retries = descriptor.getRetryCreateDirectories();
        for (;;) {
            try {
                return createDirectories.apply(path);
            } catch (NoSuchFileException e) {
                if (--retries < 0)
                    throw e;
                LOG.info("Failed to create directories {} - retry:\n", path, e);
            }
        }
    }

    @Override
    public long getTotalSpace() throws IOException {
        return getFileStore().getTotalSpace();
    }

    @Override
    protected OutputStream openOutputStreamA(WriteContext ctx) throws IOException {
        Path path = Paths.get(rootURI.resolve(ctx.getStoragePath()));
        Path dir = path.getParent();
        createDirectories(dir);
        while (true)
            try {
                ctx.setStoragePath(rootURI.relativize(path.toUri()).toString());
                return Files.newOutputStream(path, descriptor.getFileOpenOptions());
            } catch (FileAlreadyExistsException e) {
                switch (descriptor.getOnStoragePathAlreadyExists()) {
                    case NOOP:
                        ctx.setDeletionLock(true);
                        return OutputStream.nullOutputStream();
                    case FAILURE:
                        ctx.setDeletionLock(true);
                        throw e;
                    case RANDOM_PATH:
                        path = dir.resolve(String.format("%08X", ThreadLocalRandom.current().nextInt()));
                }
            }
    }

    @Override
    protected void copyA(InputStream in, WriteContext ctx) throws IOException {
        try (OutputStream out = openOutputStreamA(ctx)) {
            byte[] b = new byte[COPY_BUFFER_SIZE];
            int read = in.read(b, 0, COPY_BUFFER_SIZE);
            if (read <= 0)
                throw new IOException("No bytes to copy");
            do {
                out.write(b, 0, read);
                read = in.read(b, 0, COPY_BUFFER_SIZE);
            } while (read > 0);
        }
    }

    @Override
    protected InputStream openInputStreamA(ReadContext ctx) throws IOException {
        Path path = Paths.get(rootURI.resolve(ctx.getStoragePath()));
        return Files.newInputStream(path);
    }

    @Override
    protected void deleteObjectA(String storagePath) throws IOException {
        Path path = Paths.get(rootURI.resolve(storagePath));
        Files.delete(path);
        deleteEmptyDirectories(path);
    }

    private void deleteEmptyDirectories(Path path) {
        Path rootPath = Paths.get(rootURI);
        Path dirPath = path.getParent();
        while (!dirPath.equals(rootPath)) {
            try {
                Files.deleteIfExists(dirPath);
            } catch (DirectoryNotEmptyException ignore) {
                break;
            } catch (IOException e) {
                LOG.warn("Failed to delete directory {}", path, e);
                break;
            }
            dirPath = dirPath.getParent();
        }
    }

    @Override
    public String toString() {
        return "FileSystemStorage{" + rootURI + '}';
    }
}
