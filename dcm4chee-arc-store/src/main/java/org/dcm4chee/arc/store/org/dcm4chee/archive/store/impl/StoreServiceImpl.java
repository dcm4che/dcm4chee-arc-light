package org.dcm4chee.arc.store.org.dcm4chee.archive.store.impl;


import org.dcm4che3.data.*;
import org.dcm4che3.hl7.HL7Segment;
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
import org.dcm4che3.util.UIDUtils;
import org.dcm4chee.arc.conf.ArchiveAttributeCoercion;
import org.dcm4chee.arc.conf.ArchiveCompressionRule;
import org.dcm4chee.arc.entity.Location;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.entity.Series;
import org.dcm4chee.arc.entity.Study;
import org.dcm4chee.arc.retrieve.InstanceLocations;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveService;
import org.dcm4chee.arc.storage.Storage;
import org.dcm4chee.arc.storage.StorageException;
import org.dcm4chee.arc.storage.StorageFactory;
import org.dcm4chee.arc.storage.WriteContext;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreService;
import org.dcm4chee.arc.store.StoreSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.EJBException;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.xml.transform.Templates;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;


/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jul 2015
 */
@ApplicationScoped
class StoreServiceImpl implements StoreService {

    static final Logger LOG = LoggerFactory.getLogger(StoreServiceImpl.class);
    static final int DIFF_STUDY_INSTANCE_UID = 0xC409;

    @Inject
    private StorageFactory storageFactory;

    @Inject
    private StoreServiceEJB ejb;

    @Inject
    private Event<StoreContext> storeEvent;

    @Inject
    private RetrieveService retrieveService;

    @Override
    public StoreSession newStoreSession(Association as) {
        return new StoreSessionImpl(null, as, as.getApplicationEntity(), as.getSocket(), null);
    }

    @Override
    public StoreSession newStoreSession(HttpServletRequest httpRequest, ApplicationEntity ae) {
        return new StoreSessionImpl(httpRequest, null, ae, null, null);
    }

    @Override
    public StoreSession newStoreSession(ApplicationEntity ae) {
        return new StoreSessionImpl(null, null, ae, null, null);
    }

    @Override
    public StoreSession newStoreSession(Socket socket, HL7Segment msh, ApplicationEntity ae) {
        return new StoreSessionImpl(null, null, ae, socket, msh);
    }

    @Override
    public StoreContext newStoreContext(StoreSession session) {
        return new StoreContextImpl(session);
    }

    @Override
    public void store(StoreContext ctx, InputStream data) throws IOException {
        UpdateDBResult result = new UpdateDBResult();
        try {
            String receiveTranferSyntax = ctx.getReceiveTranferSyntax();
            try (Transcoder transcoder = receiveTranferSyntax != null
                    ? new Transcoder(data, receiveTranferSyntax)
                    : new Transcoder(data)) {
                ctx.setReceiveTransferSyntax(transcoder.getSourceTransferSyntax());
                transcoder.setIncludeBulkData(DicomInputStream.IncludeBulkData.URI);
                transcoder.setConcatenateBulkDataFiles(true);
                transcoder.setBulkDataDirectory(
                        ctx.getStoreSession().getArchiveAEExtension().getBulkDataSpoolDirectoryFile());
                transcoder.setIncludeFileMetaInformation(true);
                transcoder.transcode(new TranscoderHandler(ctx));
            } catch (StorageException e) {
                LOG.warn("{}: Failed to store received object:", ctx.getStoreSession(), e);
                throw new DicomServiceException(Status.OutOfResources, e);
            } catch (Exception e) {
                LOG.warn("{}: Failed to parse received object:", ctx.getStoreSession(), e);
                throw new DicomServiceException(Status.ProcessingFailure, e);
            }
            if (ctx.getAcceptedStudyInstanceUID() != null
                    && !ctx.getAcceptedStudyInstanceUID().equals(ctx.getStudyInstanceUID())) {
                LOG.info("{}: Received Instance[studyUID={},seriesUID={},objectUID={}]" +
                        " does not match requested studyUID={}", ctx.getStoreSession(), ctx.getStudyInstanceUID(),
                        ctx.getSeriesInstanceUID(), ctx.getSopInstanceUID(), ctx.getAcceptedStudyInstanceUID());
                throw new DicomServiceException(DIFF_STUDY_INSTANCE_UID);
            }
            coerceAttributes(ctx);
            try {
                ejb.updateDB(ctx, result);
            } catch (EJBException e) {
                LOG.info("Failed to update DB - retry", e);
                UpdateDBResult result2 = new UpdateDBResult();
                ejb.updateDB(ctx, result2);
                result = result2;
            }
            postUpdateDB(ctx, result);
        } catch (DicomServiceException e) {
            ctx.setException(e);
            throw e;
        } catch (Exception e) {
            DicomServiceException dse = new DicomServiceException(Status.ProcessingFailure, e);
            ctx.setException(dse);
            throw dse;
        } finally {
            ctx.setLocation(result.getLocation());
            ctx.setRejectionNote(result.getRejectionNote());
            ctx.setPreviousInstance(result.getPreviousInstance());
            storeEvent.fire(ctx);
            cleanup(ctx);
        }
    }

    private void postUpdateDB(StoreContext ctx, UpdateDBResult result) throws IOException {
        if (result.getLocation() == null)
            return;

        if (result.getCreatedPatient() != null) {
            synchronized (this) {
                try {
                    ejb.checkDuplicatePatientCreated(ctx, result);
                } catch (Exception e) {
                    LOG.warn("{}: Failed to remove duplicate created {}",
                            ctx.getStoreSession(), result.getCreatedPatient(), e);
                }
            }
        }

        Series series = result.getLocation().getInstance().getSeries();
        if (ctx.getWriteContext() != null)
            commitStorage(ctx);
        updateAttributes(ctx, series);
        ctx.getStoreSession().cacheSeries(series);
    }

    private void commitStorage(StoreContext ctx) throws IOException {
        WriteContext writeContext = ctx.getWriteContext();
        Storage storage = writeContext.getStorage();
        storage.commitStorage(writeContext);
    }

    @Override
    public void store(StoreContext ctx, Attributes attrs) throws IOException {
        UpdateDBResult result = new UpdateDBResult();
        ctx.setAttributes(attrs);
        try {
            if (ctx.getLocation() == null) {
                try (DicomOutputStream dos = new DicomOutputStream(openOutputStream(ctx), UID.ExplicitVRLittleEndian)) {
                    dos.writeDataset(attrs.createFileMetaInformation(ctx.getStoreTranferSyntax()), attrs);
                }
                coerceAttributes(ctx);
            }
            try {
                ejb.updateDB(ctx, result);
            } catch (EJBException e) {
                LOG.info("Failed to update DB - retry", e);
                UpdateDBResult result2 = new UpdateDBResult();
                ejb.updateDB(ctx, result2);
                result = result2;
            }
            postUpdateDB(ctx, result);
        } catch (DicomServiceException e) {
            ctx.setException(e);
            throw e;
        } catch (Exception e) {
            DicomServiceException dse = new DicomServiceException(Status.ProcessingFailure, e);
            ctx.setException(dse);
            throw dse;
        } finally {
            if (ctx.getLocation() == null)
                ctx.setLocation(result.getLocation());
            ctx.setRejectionNote(result.getRejectionNote());
            ctx.setPreviousInstance(result.getPreviousInstance());
            storeEvent.fire(ctx);
            cleanup(ctx);
        }
    }

    @Override
    public Attributes copyInstances(StoreSession session, Attributes instanceRefs, String targetStudyIUID)
            throws IOException {
        Attributes newAttr = new Attributes();
        String sourceStudyUID = instanceRefs.getString(Tag.StudyInstanceUID);
        HashMap<String, String> sourceTarget = new HashMap<>();
        sourceTarget.put(sourceStudyUID, targetStudyIUID);
        Sequence referencedSeriesSequence = instanceRefs.getSequence(Tag.ReferencedSeriesSequence);
        Collection<InstanceLocations> filteredILList = populateMap(sourceStudyUID, sourceTarget, session, referencedSeriesSequence);
        if (filteredILList != null)
            for (InstanceLocations il : filteredILList) {
                 storeNew(il, session, sourceTarget);
            }
        return newAttr;
    }

    private Collection<InstanceLocations> populateMap(String sourceStudyUID,
            HashMap<String, String> sourceTarget, StoreSession session, Sequence referencedSeriesSequence) {
        List<String> seriesUIDs = new ArrayList<>();
        for (Attributes item : referencedSeriesSequence) {
            String seriesUID = item.getString(Tag.SeriesInstanceUID);
            sourceTarget.put(seriesUID, UIDUtils.createUID());
            seriesUIDs.add(seriesUID);
        }
        RetrieveContext ctx = retrieveService.newRetrieveContextIOCM(session.getHttpRequest(), session.getCalledAET(),
                sourceStudyUID, seriesUIDs.toArray(new String[seriesUIDs.size()]));
        if (retrieveService.calculateMatches(ctx)) {
            Collection<InstanceLocations> instLocations = ctx.getMatches();
            Collection<InstanceLocations> filteredILList = filterInstanceLocations(instLocations, referencedSeriesSequence);
            for (InstanceLocations il : filteredILList) {
                sourceTarget.put(il.getSopInstanceUID(), UIDUtils.createUID());
            }
            return filteredILList;
        }
        else
            return null;
    }

    private void storeNew(InstanceLocations il, StoreSession session, HashMap<String, String> sourceTarget)
        throws IOException {
        Attributes attr = il.getAttributes();
        UIDUtils.remapUIDs(attr, sourceTarget);
        StoreContext ctx = newStoreContext(session);
        ctx.setLocation(il.getLocations().get(0));
        attr.setString(Tag.RetrieveAETitle, VR.AE, il.getRetrieveAETs());
        attr.setString(Tag.InstanceAvailability, VR.CS, il.getAvailability().toString());
        store(ctx, attr);
    }

    private Collection<InstanceLocations> filterInstanceLocations(
            Collection<InstanceLocations> ilList, Sequence referencedSeriesSequence) {
        Collection<InstanceLocations> filteredILList = new ArrayList<>();
        HashMap<String, InstanceLocations> sopIUIDInstanceLocation = new HashMap<>();
        for (InstanceLocations il : ilList) {
            sopIUIDInstanceLocation.put(il.getSopInstanceUID(), il);
        }
        for (Attributes seriesItem : referencedSeriesSequence) {
            Sequence seq = seriesItem.getSequence(Tag.ReferencedSOPSequence);
            for (Attributes item : seq) {
                String sopIUID = item.getString(Tag.ReferencedSOPInstanceUID);
                if (sopIUIDInstanceLocation.containsKey(sopIUID))
                    filteredILList.add(sopIUIDInstanceLocation.get(sopIUID));
            }
        }
        return filteredILList;
    }

    private static void cleanup(StoreContext ctx) {
        if (ctx.getLocation() == null) {
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
        attrs.update(Attributes.UpdatePolicy.OVERWRITE, patAttrs, modified);
        attrs.update(Attributes.UpdatePolicy.OVERWRITE, studyAttrs, modified);
        attrs.update(Attributes.UpdatePolicy.OVERWRITE, seriesAttrs, modified);
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
