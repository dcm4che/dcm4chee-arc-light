package org.dcm4chee.arc.storage;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.dcm4chee.arc.NamedCDIBeanCache;
import org.dcm4chee.arc.conf.StorageDescriptor;
import org.dcm4chee.arc.conf.StorageThreshold;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2015
 */
@ApplicationScoped
public class StorageFactory {
    private static final Logger LOG = LoggerFactory.getLogger(StorageFactory.class);

    @Inject
    private NamedCDIBeanCache namedCDIBeanCache;

    @Inject
    private Instance<StorageProvider> providers;

    public Storage getStorage(StorageDescriptor descriptor) {
        String scheme = descriptor.getStorageURI().getScheme();
        StorageProvider provider = namedCDIBeanCache.get(providers, scheme);
        return provider.openStorage(descriptor);
    }

    public UsableStorage getUsableStorage(List<StorageDescriptor> free,  List<StorageDescriptor> full)
            throws IOException {
        Iterator<StorageDescriptor> freeIter = free.iterator();
        Iterator<StorageDescriptor> fullIter = null;
        Date now = null;
        while (freeIter.hasNext()) {
            StorageDescriptor desc = freeIter.next();
            Storage storage = getStorage(desc);
            if (hasMinUsableSpace(storage)) {
                return new UsableStorage(storage, now == null ? null : updateStorageIDs(free, full));
            }
            LOG.info(desc.isStorageThresholdExceedsPermanently()
                    ? "No space left on {} - disable storage"
                    : "No space left on {} - suspend storage", storage);
            storage.close();
            if (now == null) {
                now = new Date();
            }
            desc.setStorageThresholdExceeded(now);
            if (desc.isStorageThresholdExceedsPermanently()) {
                freeIter.remove();
            }
            if (fullIter == null) {
                Collections.sort(full, Comparator.comparing(StorageDescriptor::getStorageThresholdExceeded));
                fullIter = full.iterator();
                while (fullIter.hasNext()) {
                    desc = fullIter.next();
                    storage = getStorage(desc);
                    if (hasMinUsableSpace(storage)) {
                        LOG.info("Free space on {} - resume storage", storage);
                        desc.setStorageThresholdExceeded(null);
                        return new UsableStorage(storage, updateStorageIDs(free, full));
                    }
                    storage.close();
                    desc.setStorageThresholdExceeded(now);
                    if (desc.isStorageThresholdExceedsPermanently()) {
                        fullIter.remove();
                    }
                }
            }
        }
        throw new IOException("No space left on configured storage systems");
    }

    private boolean hasMinUsableSpace(Storage storage) throws IOException {
        StorageDescriptor descriptor = storage.getStorageDescriptor();
        StorageThreshold storageThreshold = descriptor.getStorageThreshold();
        if (storageThreshold == null)
            return true;

        long usableSpace = storage.getUsableSpace();
        return usableSpace < 0 || usableSpace >= storageThreshold.getDiskSpace();
    }

    private static String[] updateStorageIDs(List<StorageDescriptor> free, List<StorageDescriptor> full) {
        return Stream.of(free, full)
                .flatMap(List::stream)
                .map(StorageDescriptor::getStorageID)
                .toArray(String[]::new);
    }

    public static class UsableStorage implements Closeable  {
        public final Storage storage;
        public final String[] updateStorageIDs;

        public UsableStorage(Storage storage, String[] updateStorageIDs) {
            this.storage = storage;
            this.updateStorageIDs = updateStorageIDs;
        }

        @Override
        public void close() throws IOException {
            storage.close();
        }
    }
}
