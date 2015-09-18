package org.dcm4chee.arc.storage;

import org.dcm4chee.arc.conf.StorageDescriptor;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2015
 */
public interface StorageProvider {

    Storage openStorage(StorageDescriptor descriptor);
}
