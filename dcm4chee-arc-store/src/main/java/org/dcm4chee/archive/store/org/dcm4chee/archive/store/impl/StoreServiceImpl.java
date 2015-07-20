package org.dcm4chee.archive.store.org.dcm4chee.archive.store.impl;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.UID;
import org.dcm4che3.imageio.codec.Transcoder;
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
import java.io.OutputStream;

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
        String tsuid = ctx.getOriginalTranferSyntaxUID();
        DicomInputStream dis = new DicomInputStream(data, tsuid);
        dis.setIncludeBulkData(DicomInputStream.IncludeBulkData.URI);
        dis.setConcatenateBulkDataFiles(true);
        try ( Transcoder transcoder = new Transcoder(new TranscoderHandler(ctx))) {
            dis.setDicomInputHandler(transcoder);
            dis.readDataset(-1,-1);
        }

        StorageContext storageContext = ctx.getStorageContext();
        Storage storage = storageContext.getStorage();
        LOG.info("Stored object on {} at {}", storage.getStorageURI(), storageContext.getStoragePath());
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

    private final class TranscoderHandler implements Transcoder.Handler {
        private final StoreContext storeContext;

        private TranscoderHandler(StoreContext storeContext) {
            this.storeContext = storeContext;
        }

        @Override
        public DicomOutputStream newDicomOutputStream(Attributes dataset) throws IOException {
            String tsuid = storeContext.getOriginalTranferSyntaxUID();
            Storage storage = getStorage(storeContext);
            StorageContext storageCtx = storage.newStorageContext(dataset);
            storeContext.setStorageContext(storageCtx);
            OutputStream out = storage.newOutputStream(storageCtx);
            DicomOutputStream dos = new DicomOutputStream(out, UID.ExplicitVRLittleEndian);
            dos.writeFileMetaInformation(dataset.createFileMetaInformation(tsuid));
            return dos;
        }
    }
}
