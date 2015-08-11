package org.dcm4chee.archive.storage;

import org.dcm4che3.data.Attributes;
import org.dcm4chee.archive.conf.StorageDescriptor;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2015
 */
public interface Storage {
    StorageDescriptor getStorageDescriptor();

    StorageContext newStorageContext(Attributes attrs);

    OutputStream openOutputStream(StorageContext ctx) throws IOException;

    void commitStorage(StorageContext ctx) throws IOException;

    void revokeStorage(StorageContext ctx) throws IOException;

    void deleteObject(String storagePath) throws IOException;
}
