package org.dcm4chee.archive.storage;

import org.dcm4che3.data.Attributes;
import org.dcm4chee.archive.conf.StorageDescriptor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2015
 */
public interface Storage {
    StorageDescriptor getStorageDescriptor();

    WriteContext createWriteContext();

    ReadContext createReadContext();

    OutputStream openOutputStream(WriteContext ctx) throws IOException;

    void commitStorage(WriteContext ctx) throws IOException;

    void revokeStorage(WriteContext ctx) throws IOException;

    void deleteObject(String storagePath) throws IOException;

    InputStream openInputStream(ReadContext ctx) throws IOException;
}
