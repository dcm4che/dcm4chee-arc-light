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

package org.dcm4chee.archive.storage.filesystem;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.util.AttributesFormat;
import org.dcm4chee.archive.conf.StorageDescriptor;
import org.dcm4chee.archive.storage.AbstractStorage;
import org.dcm4chee.archive.storage.DefaultStorageContext;
import org.dcm4chee.archive.storage.StorageContext;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2015
 */
public class FileSystemStorage extends AbstractStorage {

    private static final String DEFAULT_PATH_FORMAT =
            "{now,date,yyyy/MM/dd}/{0020000D,hash}/{0020000E,hash}/{00080018,hash}";

    private final URI rootURI;
    private final AttributesFormat pathFormat;

    public FileSystemStorage(StorageDescriptor descriptor) {
        super(descriptor);
        rootURI = ensureTrailingSlash(descriptor.getStorageURI());
        pathFormat = new AttributesFormat((String) descriptor.getProperty("pathFormat", DEFAULT_PATH_FORMAT));
    }

    private URI ensureTrailingSlash(URI uri) {
        String s = uri.toString();
        return (s.charAt(s.length()-1) == '/') ? uri : URI.create(s + '/');
    }

    @Override
    public StorageContext newStorageContext(Attributes attrs) {
        return new DefaultStorageContext(this, attrs);
    }

    @Override
    protected OutputStream openOutputStreamA(StorageContext ctx) throws IOException {
        Path path = Paths.get(rootURI.resolve(pathFormat.format(ctx.getAttributes())));
        Path dir = path.getParent();
        Files.createDirectories(dir);
        OutputStream stream = null;
        while (stream == null)
            try {
                stream = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW);
            } catch (FileAlreadyExistsException e) {
                path = dir.resolve(String.format("%08X", ThreadLocalRandom.current().nextInt()));
            }
        ctx.setStoragePath(rootURI.relativize(path.toUri()).toString());
        return stream;
    }

    @Override
    protected void onOutputStreamClosed(StorageContext ctx) throws IOException {
        Path path = Paths.get(rootURI.resolve(ctx.getStoragePath()));
        ctx.setSize(Files.size(path));
    }

    @Override
    public void deleteObject(String storagePath) throws IOException {
        Path path = Paths.get(rootURI.resolve(storagePath));
        Files.delete(path);
    }

    @Override
    public String toString() {
        return "FileSystemStorage{" + rootURI + '}';
    }
}
