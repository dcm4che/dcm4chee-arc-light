package org.dcm4chee.arc.storage;

import org.dcm4chee.arc.conf.NamedQualifier;
import org.dcm4chee.arc.conf.StorageDescriptor;
import org.dcm4chee.arc.conf.StorageThreshold;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2015
 */
@ApplicationScoped
public class StorageFactory {
    private static final Logger LOG = LoggerFactory.getLogger(StorageFactory.class);

    @Inject
    private Instance<StorageProvider> providers;

    public Storage getStorage(StorageDescriptor descriptor) {
        String scheme = descriptor.getStorageURI().getScheme();
        StorageProvider provider = providers.select(new NamedQualifier(scheme)).get();
        return provider.openStorage(descriptor);
    }

    public Storage getUsableStorage(List<StorageDescriptor> descriptors) throws IOException {
        Iterator<StorageDescriptor> iter = descriptors.iterator();
        while (iter.hasNext()) {
            Storage storage = getStorage(iter.next());
            if (hasMinUsableSpace(storage)) {
                return storage;
            }
            LOG.info("No space left on {}", storage);
            storage.close();
            iter.remove();
        }
        throw new IOException("No space left on configured storage systems");
    }

    private boolean hasMinUsableSpace(Storage storage) throws IOException {
        StorageDescriptor descriptor = storage.getStorageDescriptor();
        StorageThreshold storageThreshold = descriptor.getStorageThreshold();
        if (storageThreshold == null)
            return true;

        long usableSpace = storage.getUsableSpace();
        return usableSpace < 0 || usableSpace >= storageThreshold.getMinUsableDiskSpace();
    }
}
