package org.dcm4chee.archive.store.org.dcm4chee.archive.store.impl;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.UID;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Device;
import org.dcm4chee.archive.conf.ArchiveAEExtension;
import org.dcm4chee.archive.conf.ArchiveDeviceExtension;
import org.dcm4chee.archive.conf.StorageDescriptor;
import org.dcm4chee.archive.storage.Storage;
import org.dcm4chee.archive.storage.StorageContext;
import org.dcm4chee.archive.storage.StorageFactory;
import org.dcm4chee.archive.store.StoreContext;
import org.dcm4chee.archive.store.StoreService;
import org.dcm4chee.archive.store.StoreSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2015
 */
@ApplicationScoped
class StoreServiceImpl implements StoreService {

    static final Logger LOG = LoggerFactory.getLogger(StoreServiceImpl.class);

    @Inject
    private Device device;

    @Inject
    private StorageFactory storageFactory;

    @Override
    public StoreSession newStoreSession(Association as) {
        return new StoreSessionImpl(as);
    }

    @Override
    public StoreContext newStoreContext(StoreSession session) {
        return new StoreContextImpl(session);
    }

    @Override
    public void store(StoreContext ctx, InputStream data) throws IOException {
        Storage storage = getStorage(ctx);
        String tsuid = ctx.getOriginalTranferSyntaxUID();
        Attributes dataset = readAttributes(data, tsuid);
        StorageContext storageContext = storage.newStorageContext(dataset);
        try ( DicomOutputStream stream =
                      new DicomOutputStream(storage.newOutputStream(storageContext), UID.ExplicitVRLittleEndian)) {
            Attributes fmi = dataset.createFileMetaInformation(tsuid);
            stream.writeDataset(fmi, dataset);
        }
        LOG.info("Stored object on {} at {}", storage.getStorageURI(), storageContext.getStoragePath());
    }

    private Attributes readAttributes(InputStream data, String tsuid) throws IOException {
        DicomInputStream dis = new DicomInputStream(data, tsuid);
        return dis.readDataset(-1, -1);
    }

    private Storage getStorage(StoreContext ctx) {
        StoreSession session = ctx.getStoreSession();
        Storage storage = session.getStorage();
        if (storage == null) {
            ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
            ArchiveAEExtension arcAE = session.getLocalApplicationEntity().getAEExtension(ArchiveAEExtension.class);
            String storageID = arcAE.getStorageID();
            StorageDescriptor descriptor = arcDev.getStorageDescriptor(storageID);
            storage = storageFactory.getStorage(descriptor);
            session.setStorage(storage);
        }
        return storage;
    }

}
