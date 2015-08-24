package org.dcm4chee.archive.storage;

import org.dcm4chee.archive.conf.NamedQualifier;
import org.dcm4chee.archive.conf.StorageDescriptor;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2015
 */
@ApplicationScoped
public class StorageFactory {
    @Inject
    private Instance<StorageProvider> providers;

    public Storage getStorage(StorageDescriptor descriptor) {
        String scheme = descriptor.getStorageURI().getScheme();
        StorageProvider provider = providers.select(new NamedQualifier(scheme)).get();
        return provider.getStorage(descriptor);
    }
}
