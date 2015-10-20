package org.dcm4chee.arc.store.org.dcm4chee.archive.store.impl;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.imageio.codec.ImageDescriptor;
import org.dcm4che3.imageio.codec.Transcoder;
import org.dcm4che3.imageio.codec.TransferSyntaxType;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4chee.arc.conf.ArchiveCompressionRule;
import org.dcm4chee.arc.entity.Location;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.entity.Series;
import org.dcm4chee.arc.entity.Study;
import org.dcm4chee.arc.store.StoreService;
import org.dcm4chee.arc.storage.Storage;
import org.dcm4chee.arc.storage.WriteContext;
import org.dcm4chee.arc.storage.StorageFactory;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
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
    private StorageFactory storageFactory;

    @Inject
    private StoreServiceEJB ejb;

    @Inject
    private Event<StoreContext> storeEvent;

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
        Location location = null;
        try {
            try (Transcoder transcoder = new Transcoder(data, ctx.getReceiveTranferSyntax())) {
                transcoder.setIncludeBulkData(DicomInputStream.IncludeBulkData.URI);
                transcoder.setConcatenateBulkDataFiles(true);
                transcoder.setBulkDataDirectory(
                        ctx.getStoreSession().getArchiveAEExtension().getBulkDataSpoolDirectoryFile());
                transcoder.setIncludeFileMetaInformation(true);
                transcoder.transcode(new TranscoderHandler(ctx));
            }

            UpdateDBResult result = ejb.updateDB(ctx);
            location = result.getLocation();
            if (location != null) {
                Series series = location.getInstance().getSeries();
                WriteContext writeContext = ctx.getWriteContext();
                Storage storage = writeContext.getStorage();
                updateAttributes(writeContext.getAttributes(), series);
                storage.commitStorage(writeContext);
                ctx.getStoreSession().cacheSeries(series);
                storeEvent.fire(ctx);
            }
        } catch (Exception e) {
            throw new DicomServiceException(Status.ProcessingFailure, e);
        } finally {
            if (location == null) {
                WriteContext storageContext = ctx.getWriteContext();
                if (storageContext != null) {
                    Storage storage = storageContext.getStorage();
                    storage.revokeStorage(storageContext);
                }
            }
        }
    }

    private void updateAttributes(Attributes attrs, Series series) {
        Study study = series.getStudy();
        Patient patient = study.getPatient();
        Attributes seriesAttrs = series.getAttributes();
        Attributes studyAttrs = study.getAttributes();
        Attributes patAttrs = patient.getAttributes();
        Attributes.unifyCharacterSets(patAttrs, studyAttrs, seriesAttrs, attrs);
        attrs.addAll(patAttrs);
        attrs.addAll(studyAttrs);
        attrs.addAll(seriesAttrs);
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
        public OutputStream newOutputStream(Transcoder transcoder, Attributes dataset) throws IOException {
            storeContext.setAttributes(dataset);
            ArchiveCompressionRule compressionRule = selectCompressionRule(transcoder, storeContext);
            if (compressionRule != null) {
                transcoder.setDestinationTransferSyntax(compressionRule.getTransferSyntax());
                transcoder.setCompressParams(compressionRule.getImageWriteParams());
                storeContext.setStoreTranferSyntax(compressionRule.getTransferSyntax());
            }
            Storage storage = getStorage(storeContext);
            WriteContext writeCtx = storage.createWriteContext();
            writeCtx.setAttributes(dataset);
            writeCtx.setStudyInstanceUID(dataset.getString(Tag.StudyInstanceUID));
            writeCtx.setMessageDigest(storage.getStorageDescriptor().getMessageDigest());
            storeContext.setWriteContext(writeCtx);
            return storage.openOutputStream(writeCtx);
        }
    }

    private ArchiveCompressionRule selectCompressionRule(Transcoder transcoder, StoreContext storeContext) {
        ImageDescriptor imageDescriptor = transcoder.getImageDescriptor();
        if (imageDescriptor == null) // not an image
            return null;

        if (transcoder.getSourceTransferSyntaxType() != TransferSyntaxType.NATIVE) // already compressed
            return null;

        StoreSession session = storeContext.getStoreSession();
        String hostname = session.getRemoteHostName();
        String aet = session.getRemoteApplicationEntityTitle();
        ArchiveCompressionRule rule = session.getArchiveAEExtension()
                .findCompressionRule(hostname, aet, storeContext.getAttributes());
        if (rule != null && imageDescriptor.isMultiframeWithEmbeddedOverlays()) {
            LOG.info("Compression of multi-frame image with embedded overlays not supported");
            return null;
        }
        return rule;
    }
}
