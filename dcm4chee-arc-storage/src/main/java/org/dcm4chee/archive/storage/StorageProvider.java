package org.dcm4chee.archive.storage;

import org.dcm4chee.archive.conf.StorageDescriptor;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2015
 */
public interface StorageProvider {

    Storage openStorage(StorageDescriptor descriptor);
}
