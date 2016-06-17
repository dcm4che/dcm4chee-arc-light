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

package org.dcm4chee.arc.storage;

import org.dcm4chee.arc.conf.StorageDescriptor;

import java.io.*;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2015
 */
public abstract class AbstractStorage implements Storage {

    protected static final String DEFAULT_PATH_FORMAT =
            "{now,date,yyyy/MM/dd}/{0020000D,hash}/{0020000E,hash}/{00080018,hash}";

    protected final StorageDescriptor descriptor;

    protected AbstractStorage(StorageDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    @Override
    public StorageDescriptor getStorageDescriptor() {
        return descriptor;
    }

    @Override
    public WriteContext createWriteContext() {
        return new DefaultWriteContext(this);
    }

    @Override
    public ReadContext createReadContext() {
        return new DefaultReadContext(this);
    }

    @Override
    public boolean isAccessable() {
        return true;
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public OutputStream openOutputStream(final WriteContext ctx) throws IOException {
        checkAccessable();
        OutputStream stream = openOutputStreamA(ctx);
        if (ctx.getMessageDigest() != null) {
            stream = new DigestOutputStream(stream, ctx.getMessageDigest());
        }
        return new FilterOutputStream(stream) {
            @Override
            public void write(int b) throws IOException {
                try {
                    out.write(b);
                } catch (IOException e) {
                    throw new StorageException(e);
                }
                ctx.incrementSize(1);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                try {
                    out.write(b, off, len);
                } catch (IOException e) {
                    throw new StorageException(e);
                }
                ctx.incrementSize(len);
            }

            @Override
            public void close() throws IOException {
                try {
                    beforeOutputStreamClosed(ctx, this);
                } finally {
                    try {
                        super.close();
                    } catch (IOException e) {
                        throw new StorageException(e);
                    } finally {
                        afterOutputStreamClosed(ctx);
                    }
                }
            }
        };
    }

    private void checkAccessable() throws IOException {
        if (!isAccessable())
            throw new IOException(descriptor.getStorageURI() + " not accessable");
    }

    @Override
    public long getUsableSpace() throws IOException {
        throw new IOException(descriptor.getStorageURI() + " does not support getUsableSpace()");
    }

    protected abstract OutputStream openOutputStreamA(WriteContext ctx) throws IOException;

    protected void beforeOutputStreamClosed(WriteContext ctx, OutputStream stream) throws IOException {}

    protected void afterOutputStreamClosed(WriteContext ctx) throws IOException {}

    @Override
    public void commitStorage(WriteContext ctx) throws IOException {
    }

    @Override
    public void revokeStorage(WriteContext ctx) throws IOException {
        deleteObject(ctx.getStoragePath());
    }

    @Override
    public InputStream openInputStream(final ReadContext ctx) throws IOException {
        checkAccessable();
        InputStream stream = openInputStreamA(ctx);
        if (ctx.getMessageDigest() != null) {
            stream = new DigestInputStream(stream, ctx.getMessageDigest());
        }
        return new FilterInputStream(stream) {
            @Override
            public int read() throws IOException {
                int read = 0;
                try {
                    read = in.read();
                } catch (IOException e) {
                    throw new StorageException(e);
                }
                if (read >= 0)
                    ctx.incrementSize(1);
                return read;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                int read = 0;
                try {
                    read = in.read(b, off, len);
                } catch (IOException e) {
                    throw new StorageException(e);
                }
                if (read > 0)
                    ctx.incrementSize(read);
                return read;
            }

            @Override
            public long skip(long n) throws IOException {
                long skip = 0;
                try {
                    skip = in.skip(n);
                } catch (IOException e) {
                    throw new StorageException(e);
                }
                ctx.incrementSize(skip);
                return skip;
            }

            @Override
            public boolean markSupported() {
                return false;
            }

            @Override
            public void close() throws IOException {
                try {
                    beforeInputStreamClosed(ctx, this);
                } finally {
                    try {
                        super.close();
                    } catch (IOException e) {
                        throw new StorageException(e);
                    } finally {
                        afterInputStreamClosed(ctx);
                    }
                }
            }
        };
    }

    protected abstract InputStream openInputStreamA(ReadContext ctx) throws IOException;

    protected void beforeInputStreamClosed(ReadContext ctx, InputStream stream)  throws IOException {}

    protected void afterInputStreamClosed(ReadContext ctx)  throws IOException {}

}