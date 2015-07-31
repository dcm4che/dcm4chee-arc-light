package org.dcm4chee.archive.store.org.dcm4chee.archive.store.impl;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.imageio.codec.CompressionRule;
import org.dcm4che3.imageio.codec.ImageDescriptor;
import org.dcm4che3.imageio.codec.Transcoder;
import org.dcm4che3.imageio.codec.TransferSyntaxType;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.net.Association;
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
import java.io.File;
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
    private StorageFactory storageFactory;

    @Inject
    private StoreServiceEJB ejb;

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
        try ( Transcoder transcoder = new Transcoder(data, ctx.getReceiveTranferSyntax())) {
            transcoder.setIncludeBulkData(DicomInputStream.IncludeBulkData.URI);
            transcoder.setConcatenateBulkDataFiles(true);
            transcoder.setBulkDataDirectory(
                    ctx.getStoreSession().getArchiveAEExtension().getBulkDataSpoolDirectoryFile());
            transcoder.setIncludeFileMetaInformation(true);
            transcoder.transcode(new TranscoderHandler(ctx));
        }

        StorageContext storageContext = ctx.getStorageContext();
        Storage storage = storageContext.getStorage();
        LOG.info("Stored object on {} at {}",
                storage.getStorageDescriptor().getStorageURI(),
                storageContext.getStoragePath());
        ejb.updateDB(ctx);
    }

    private Storage getStorage(StoreContext ctx) {
        // could be extended to support selection of Storage dependent on dataset
        StoreSession session = ctx.getStoreSession();
        Storage storage = session.getStorage();
        if (storage == null) {
            storage = storageFactory.getStorage(session.getArchiveAEExtension().getStorageDescriptor());
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
        public OutputStream newOutputStream(Transcoder transcoder, Attributes dataset)
                throws IOException {
            storeContext.setAttributes(dataset);
            CompressionRule compressionRule = selectCompressionRule(transcoder, storeContext);
            if (compressionRule != null) {
                transcoder.setDestinationTransferSyntax(compressionRule.getTransferSyntax());
                transcoder.setCompressParams(compressionRule.getImageWriteParams());
                storeContext.setStoreTranferSyntax(compressionRule.getTransferSyntax());
            }
            Storage storage = getStorage(storeContext);
            StorageContext storageCtx = storage.newStorageContext(dataset);
            storeContext.setStorageContext(storageCtx);
            return storage.newOutputStream(storageCtx);
        }
    }

    private CompressionRule selectCompressionRule(Transcoder transcoder, StoreContext storeContext) {
        ImageDescriptor imageDescriptor = transcoder.getImageDescriptor();
        if (imageDescriptor == null) // not an image
            return null;

        if (transcoder.getSourceTransferSyntaxType() != TransferSyntaxType.NATIVE) // already compressed
            return null;

        StoreSession session = storeContext.getStoreSession();
        String aet = session.getRemoteApplicationEntityTitle();
        CompressionRule rule = session.getArchiveAEExtension().findCompressionRule(aet, imageDescriptor);
        if (rule != null && imageDescriptor.isMultiframeWithEmbeddedOverlays()) {
            LOG.info("Compression of multi-frame image with embedded overlays not supported");
            return null;
        }
        return rule;
    }
}
