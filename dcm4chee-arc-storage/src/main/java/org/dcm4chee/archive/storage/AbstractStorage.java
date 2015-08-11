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

package org.dcm4chee.archive.storage;

import org.dcm4che3.data.Attributes;
import org.dcm4chee.archive.conf.StorageDescriptor;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2015
 */
public abstract class AbstractStorage implements Storage {

    protected final StorageDescriptor descriptor;

    protected AbstractStorage(StorageDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    @Override
    public StorageDescriptor getStorageDescriptor() {
        return descriptor;
    }

    @Override
    public StorageContext newStorageContext(Attributes attrs) {
        StorageContext ctx = newStorageContextA(attrs);
        ctx.setMessageDigest(descriptor.getDigestAlgorithm());
        return ctx;
    }

    protected StorageContext newStorageContextA(Attributes attrs) {
        return new DefaultStorageContext(this, attrs);
    }

    @Override
    public OutputStream openOutputStream(final StorageContext ctx) throws IOException {
        OutputStream stream = openOutputStreamA(ctx);
        if (ctx.getMessageDigest() != null) {
            stream = new DigestOutputStream(stream, ctx.getMessageDigest());
        }
        return new FilterOutputStream(stream) {
            @Override
            public void write(int b) throws IOException {
                out.write(b);
                ctx.incrementSize(1);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                out.write(b, off, len);
                ctx.incrementSize(len);
            }

            @Override
            public void close() throws IOException {
                try {
                    beforeOutputStreamClosed(ctx, this);
                } finally {
                    try {
                        super.close();
                    } finally {
                        afterOutputStreamClosed(ctx);
                    }
                }
            }
        };
    }

    protected abstract OutputStream openOutputStreamA(StorageContext ctx) throws IOException;

    protected void beforeOutputStreamClosed(StorageContext ctx, OutputStream stream) throws IOException {}

    protected void afterOutputStreamClosed(StorageContext ctx) throws IOException {}

    @Override
    public void commitStorage(StorageContext ctx) throws IOException {
    }

    @Override
    public void revokeStorage(StorageContext ctx) throws IOException {
        deleteObject(ctx.getStoragePath());
    }
}