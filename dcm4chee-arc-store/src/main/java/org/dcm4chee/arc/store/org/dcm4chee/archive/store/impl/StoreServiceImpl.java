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
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.id.IDService;
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
import java.util.*;


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

    @Inject
    private IDService idService;

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
        UpdateDBResult result = null;
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
            result = updateDB(ctx);
            postUpdateDB(ctx, result);
        } catch (DicomServiceException e) {
            ctx.setException(e);
            throw e;
        } catch (Exception e) {
            DicomServiceException dse = new DicomServiceException(Status.ProcessingFailure, e);
            ctx.setException(dse);
            throw dse;
        } finally {
            if (result != null) {
                ctx.setLocation(result.getLocation());
                ctx.setRejectionNote(result.getRejectionNote());
                ctx.setPreviousInstance(result.getPreviousInstance());
            }
            storeEvent.fire(ctx);
            cleanup(ctx);
        }
    }

    private UpdateDBResult updateDB(StoreContext ctx) throws DicomServiceException {
        StoreSession session = ctx.getStoreSession();
        ArchiveAEExtension arcAE = session.getArchiveAEExtension();
        ArchiveDeviceExtension arcDev = arcAE.getArchiveDeviceExtension();
        int retries = arcDev.getStoreUpdateDBMaxRetries();
        for (;;)
            try {
                UpdateDBResult result = new UpdateDBResult();
                ejb.updateDB(ctx, result);
                return result;
            } catch (EJBException e) {
                if (retries-- > 0) {
                    LOG.info("Failed to update DB - retry:\n", e);
                } else {
                    LOG.warn("Failed to update DB:\n", e);
                    throw e;
                }
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
        ctx.setAttributes(attrs);
        UpdateDBResult result = null;
        try {
            if (ctx.getLocation() == null) {
                try (DicomOutputStream dos = new DicomOutputStream(openOutputStream(ctx), UID.ExplicitVRLittleEndian)) {
                    dos.writeDataset(attrs.createFileMetaInformation(ctx.getStoreTranferSyntax()), attrs);
                }
                coerceAttributes(ctx);
            }
            result = updateDB(ctx);
            postUpdateDB(ctx, result);
        } catch (DicomServiceException e) {
            ctx.setException(e);
            throw e;
        } catch (Exception e) {
            DicomServiceException dse = new DicomServiceException(Status.ProcessingFailure, e);
            ctx.setException(dse);
            throw dse;
        } finally {
            if (result != null) {
                if (ctx.getLocation() == null)
                    ctx.setLocation(result.getLocation());
                ctx.setRejectionNote(result.getRejectionNote());
                ctx.setPreviousInstance(result.getPreviousInstance());
            }
            storeEvent.fire(ctx);
            cleanup(ctx);
        }
    }

    @Override
    public Attributes copyInstances(
            StoreSession session, Collection<InstanceLocations> instances, Map<String, String> uidMap)
            throws IOException {
        Attributes result = new Attributes();
        UIDMap map = new UIDMap();
        map.setUIDMap(uidMap);
        if (instances != null) {
            Sequence refSOPSeq = result.newSequence(Tag.ReferencedSOPSequence, 10);
            Sequence failedSOPSeq = result.newSequence(Tag.FailedSOPSequence, 10);
            for (InstanceLocations il : instances) {
                Attributes attr = il.getAttributes();
                UIDUtils.remapUIDs(attr, uidMap);
                StoreContext ctx = newStoreContext(session);
                Location locationOld = il.getLocations().get(0);
                if (locationOld.getMultiReference() != null) {
                    Integer locationMultiRef = Integer.valueOf(idService.createID(IDGenerator.Name.LocationMultiReference));
                    locationOld.setMultiReference(locationMultiRef);
                }
                ctx.setLocation(locationOld);
                attr.setString(Tag.RetrieveAETitle, VR.AE, il.getRetrieveAETs());
                attr.setString(Tag.InstanceAvailability, VR.CS, il.getAvailability().toString());
                try {
                    store(ctx, attr);
                    populateResult(refSOPSeq, attr);
                } catch (DicomServiceException e) {
                    result.setString(Tag.FailureReason, VR.US, Integer.toString(e.getStatus()) + e.getMessage());
                    populateResult(failedSOPSeq, attr);
                }
            }
        }
        return result;
    }

    private void populateResult(Sequence refSOPSeq, Attributes ilAttr) {
        Attributes refSOP = new Attributes(2);
        refSOP.setString(Tag.ReferencedSOPClassUID, VR.UI, ilAttr.getString(Tag.SOPClassUID));
        refSOP.setString(Tag.ReferencedSOPInstanceUID, VR.UI, ilAttr.getString(Tag.SOPInstanceUID));
        refSOPSeq.add(refSOP);
    }

    @Override
    public Collection<InstanceLocations> queryInstances(
            StoreSession session, Attributes instanceRefs, String targetStudyIUID, Map<String, String> uidMap) {
        String sourceStudyUID = instanceRefs.getString(Tag.StudyInstanceUID);
        uidMap.put(sourceStudyUID, targetStudyIUID);
        Sequence refSeriesSeq = instanceRefs.getSequence(Tag.ReferencedSeriesSequence);
        Map<String, Set<String>> refIUIDsBySeriesIUID = new HashMap<>();
        RetrieveContext ctx;
        if (refSeriesSeq == null) {
             ctx = retrieveService.newRetrieveContextIOCM(session.getHttpRequest(), session.getCalledAET(),
                    sourceStudyUID);
        } else {
            for (Attributes item : refSeriesSeq) {
                String seriesIUID = item.getString(Tag.SeriesInstanceUID);
                uidMap.put(seriesIUID, UIDUtils.createUID());
                refIUIDsBySeriesIUID.put(seriesIUID, refIUIDs(item.getSequence(Tag.ReferencedSOPSequence)));
            }
            ctx = retrieveService.newRetrieveContextIOCM(session.getHttpRequest(), session.getCalledAET(),
                    sourceStudyUID, refIUIDsBySeriesIUID.keySet().toArray(new String[refIUIDsBySeriesIUID.size()]));
        }
        if (!retrieveService.calculateMatches(ctx))
            return null;
        Collection<InstanceLocations> matches = ctx.getMatches();
        Iterator<InstanceLocations> matchesIter = matches.iterator();
        while (matchesIter.hasNext()) {
            InstanceLocations il = matchesIter.next();
            if (contains(refIUIDsBySeriesIUID, il))
                uidMap.put(il.getSopInstanceUID(), UIDUtils.createUID());
            else
                matchesIter.remove();
        }
        return matches;
    }

    private Set<String> refIUIDs(Sequence refSOPSeq) {
        Set<String> iuids = new HashSet<>(refSOPSeq.size() * 4 / 3 + 1);
        for (Attributes refSOP : refSOPSeq)
            iuids.add(refSOP.getString(Tag.ReferencedSOPInstanceUID));
        return iuids;
    }

    private boolean contains(Map<String, Set<String>> refIUIDsBySeriesIUID, InstanceLocations il) {
        Set<String> iuids = refIUIDsBySeriesIUID.get(il.getAttributes().getString(Tag.SeriesInstanceUID));
        return iuids == null || iuids.contains(il.getSopInstanceUID());
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
