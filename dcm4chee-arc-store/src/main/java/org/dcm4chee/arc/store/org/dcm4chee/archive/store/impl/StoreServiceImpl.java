package org.dcm4chee.arc.store.org.dcm4chee.archive.store.impl;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.UID;
import org.dcm4che3.imageio.codec.ImageDescriptor;
import org.dcm4che3.imageio.codec.Transcoder;
import org.dcm4che3.imageio.codec.TransferSyntaxType;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.io.TemplatesCache;
import org.dcm4che3.io.XSLTAttributesCoercion;
import org.dcm4che3.net.*;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.ArchiveAttributeCoercion;
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
import javax.servlet.http.HttpServletRequest;
import javax.xml.transform.Templates;
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
    public StoreSession newStoreSession(HttpServletRequest httpRequest, ApplicationEntity ae) {
        return new StoreSessionImpl(httpRequest, ae);
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
            } catch (Exception e) {
                throw new DicomServiceException(Status.ProcessingFailure, e);
            }
            coerceAttributes(ctx);
            UpdateDBResult result = ejb.updateDB(ctx);
            location = result.getLocation();
            postUpdateDB(ctx, location);
        } catch (DicomServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new DicomServiceException(Status.ProcessingFailure, e);
        } finally {
            cleanup(ctx, location);
        }
    }

    private void postUpdateDB(StoreContext ctx, Location location) throws IOException {
        if (location != null) {
            Series series = location.getInstance().getSeries();
            WriteContext writeContext = ctx.getWriteContext();
            Storage storage = writeContext.getStorage();
            updateAttributes(ctx, series);
            storage.commitStorage(writeContext);
            ctx.getStoreSession().cacheSeries(series);
            storeEvent.fire(ctx);
        }
    }

    @Override
    public void store(StoreContext ctx, Attributes attrs) throws IOException {
        ctx.setAttributes(attrs);
        Location location = null;
        try {
            try ( DicomOutputStream dos = new DicomOutputStream(openOutputStream(ctx), UID.ExplicitVRLittleEndian) ) {
                dos.writeDataset(attrs.createFileMetaInformation(ctx.getStoreTranferSyntax()), attrs);
            }
            coerceAttributes(ctx);
            UpdateDBResult result = ejb.updateDB(ctx);
            location = result.getLocation();
            postUpdateDB(ctx, location);
        } catch (DicomServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new DicomServiceException(Status.ProcessingFailure, e);
        } finally {
            cleanup(ctx, location);
        }
    }

    private static void cleanup(StoreContext ctx, Location location) {
        if (location == null) {
            WriteContext writeCtx = ctx.getWriteContext();
            if (writeCtx != null && writeCtx.getStoragePath() != null) {
                Storage storage = writeCtx.getStorage();
                try {
                    storage.revokeStorage(writeCtx);
                } catch (IOException e) {
                    LOG.warn("Failed to revoke storage", e);
                }
            }
        }
    }

    private void coerceAttributes(StoreContext ctx) throws Exception {
        StoreSession session = ctx.getStoreSession();
        ArchiveAttributeCoercion coercion = session.getArchiveAEExtension().findAttributeCoercion(
                session.getRemoteHostName(),
                session.getCallingAET(),
                TransferCapability.Role.SCU,
                Dimse.C_STORE_RQ,
                ctx.getSopClassUID());
        if (coercion != null) {
            LOG.debug("{}: Apply {}", session, coercion);
            Attributes attrs = ctx.getAttributes();
            Attributes modified = ctx.getCoercedAttributes();
            String uri = StringUtils.replaceSystemProperties(coercion.getXSLTStylesheetURI());
            Templates tpls = TemplatesCache.getDefault().get(uri);
            new XSLTAttributesCoercion(tpls, null)
                    .includeKeyword(!coercion.isNoKeywords())
                    .coerce(attrs, modified);
        }
    }

    private void updateAttributes(StoreContext ctx, Series series) {
        Attributes attrs = ctx.getAttributes();
        Attributes modified = ctx.getCoercedAttributes();
        Study study = series.getStudy();
        Patient patient = study.getPatient();
        Attributes seriesAttrs = series.getAttributes();
        Attributes studyAttrs = study.getAttributes();
        Attributes patAttrs = patient.getAttributes();
        Attributes.unifyCharacterSets(patAttrs, studyAttrs, seriesAttrs, attrs);
        attrs.update(patAttrs, modified);
        attrs.update(studyAttrs, modified);
        attrs.update(seriesAttrs, modified);
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
            return openOutputStream(storeContext);
        }

    }

    private OutputStream openOutputStream(StoreContext storeContext) throws IOException {
        Storage storage = getStorage(storeContext);
        WriteContext writeCtx = storage.createWriteContext();
        writeCtx.setAttributes(storeContext.getAttributes());
        writeCtx.setStudyInstanceUID(storeContext.getStudyInstanceUID());
        writeCtx.setMessageDigest(storage.getStorageDescriptor().getMessageDigest());
        storeContext.setWriteContext(writeCtx);
        return storage.openOutputStream(writeCtx);
    }

    private ArchiveCompressionRule selectCompressionRule(Transcoder transcoder, StoreContext storeContext) {
        ImageDescriptor imageDescriptor = transcoder.getImageDescriptor();
        if (imageDescriptor == null) // not an image
            return null;

        if (transcoder.getSourceTransferSyntaxType() != TransferSyntaxType.NATIVE) // already compressed
            return null;

        StoreSession session = storeContext.getStoreSession();
        String hostname = session.getRemoteHostName();
        String callingAET = session.getCallingAET();
        String calledAET = session.getCalledAET();
        ArchiveCompressionRule rule = session.getArchiveAEExtension()
                .findCompressionRule(hostname, callingAET, calledAET, storeContext.getAttributes());
        if (rule != null && imageDescriptor.isMultiframeWithEmbeddedOverlays()) {
            LOG.info("Compression of multi-frame image with embedded overlays not supported");
            return null;
        }
        return rule;
    }
}
