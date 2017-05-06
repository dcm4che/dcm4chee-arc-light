package org.dcm4chee.arc.storage;

import org.dcm4chee.arc.conf.NamedQualifier;
import org.dcm4chee.arc.conf.StorageDescriptor;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import java.io.IOException;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;

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
        return provider.openStorage(descriptor);
    }

    public Storage getUsableStorage(List<StorageDescriptor> descriptors) throws IOException {
        Iterator<StorageDescriptor> iter = descriptors.iterator();
        while (iter.hasNext()) {
            Storage storage = getStorage(iter.next());
            if (hasMinUsableSpace(storage)) {
                return storage;
            }
            storage.close();
            iter.remove();
        }
        throw new IOException("No space left on configured storage systems");
    }

    private boolean hasMinUsableSpace(Storage storage) throws IOException {
        StorageDescriptor descriptor = storage.getStorageDescriptor();
        if (!descriptor.hasStorageThresholds())
            return true;

        long minUsableSpace = descriptor.getMinUsableSpace(Calendar.getInstance());
        if (minUsableSpace < 0)
            return true;

        long usableSpace = storage.getUsableSpace();
        return usableSpace < 0 || usableSpace >= minUsableSpace;
    }
}
