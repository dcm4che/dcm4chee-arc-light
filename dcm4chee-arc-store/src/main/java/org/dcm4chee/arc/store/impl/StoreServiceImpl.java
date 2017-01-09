/*
 * *** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * J4Care.
 * Portions created by the Initial Developer are Copyright (C) 2013
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * *** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.store.impl;


import org.dcm4che3.data.*;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.imageio.codec.ImageDescriptor;
import org.dcm4che3.imageio.codec.Transcoder;
import org.dcm4che3.imageio.codec.TransferSyntaxType;
import org.dcm4che3.io.*;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.net.*;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.util.UIDUtils;
import org.dcm4chee.arc.Cache;
import org.dcm4chee.arc.MergeMWLQueryParam;
import org.dcm4chee.arc.MergeMWLCache;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.entity.*;
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
import org.xml.sax.SAXException;

import javax.ejb.EJBException;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.servlet.http.HttpServletRequest;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;


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
    private MergeMWLCache mergeMWLCache;

    @Override
    public StoreSession newStoreSession(Association as) {
        return new StoreSessionImpl(null, null, as, as.getApplicationEntity(), as.getSocket(), null);
    }

    @Override
    public StoreSession newStoreSession(HttpServletRequest httpRequest, String pathParam, ApplicationEntity ae) {
        return new StoreSessionImpl(httpRequest, pathParam, null, ae, null, null);
    }

    @Override
    public StoreSession newStoreSession(ApplicationEntity ae) {
        return new StoreSessionImpl(null, null, null, ae, null, null);
    }

    @Override
    public StoreSession newStoreSession(Socket socket, HL7Segment msh, ApplicationEntity ae) {
        return new StoreSessionImpl(null, null, null, ae, socket, msh);
    }

    @Override
    public StoreContext newStoreContext(StoreSession session) {
        return new StoreContextImpl(session);
    }

    @Override
    public void store(StoreContext ctx, InputStream data) throws IOException {
        UpdateDBResult result = null;
        List<File> bulkDataFiles = Collections.emptyList();
        try {
            String receiveTranferSyntax = ctx.getReceiveTranferSyntax();
            try (Transcoder transcoder = receiveTranferSyntax != null
                    ? new Transcoder(data, receiveTranferSyntax)
                    : new Transcoder(data)) {
                ctx.setReceiveTransferSyntax(transcoder.getSourceTransferSyntax());
                transcoder.setIncludeBulkData(DicomInputStream.IncludeBulkData.URI);
                transcoder.setPixelDataBulkDataURI("");
                transcoder.setConcatenateBulkDataFiles(true);
                transcoder.setBulkDataDirectory(
                        ctx.getStoreSession().getArchiveAEExtension().getBulkDataSpoolDirectoryFile());
                transcoder.setIncludeFileMetaInformation(true);
                transcoder.setDeleteBulkDataFiles(false);
                transcoder.transcode(new TranscoderHandler(ctx));
                bulkDataFiles = transcoder.getBulkDataFiles();
            } catch (StorageException e) {
                LOG.warn("{}: Failed to store received object:\n", ctx.getStoreSession(), e);
                throw new DicomServiceException(Status.OutOfResources, e);
            } catch (Throwable e) {
                LOG.warn("{}: Failed to parse received object:\n", ctx.getStoreSession(), e);
                throw new DicomServiceException(Status.ProcessingFailure, e);
            }
            if (ctx.getAcceptedStudyInstanceUID() != null
                    && !ctx.getAcceptedStudyInstanceUID().equals(ctx.getStudyInstanceUID())) {
                LOG.info("{}: Received Instance[studyUID={},seriesUID={},objectUID={}]" +
                        " does not match requested studyUID={}", ctx.getStoreSession(), ctx.getStudyInstanceUID(),
                        ctx.getSeriesInstanceUID(), ctx.getSopInstanceUID(), ctx.getAcceptedStudyInstanceUID());
                throw new DicomServiceException(DIFF_STUDY_INSTANCE_UID);
            }
            checkCharacterSet(ctx);
            storeMetadata(ctx);
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
            for (File tmpFile : bulkDataFiles)
                tmpFile.delete();
            revokeStorage(ctx, result);
            storeEvent.fire(ctx);
        }
    }

    private UpdateDBResult updateDB(StoreContext ctx) throws DicomServiceException {
        StoreSession session = ctx.getStoreSession();
        ArchiveAEExtension arcAE = session.getArchiveAEExtension();
        ArchiveDeviceExtension arcDev = arcAE.getArchiveDeviceExtension();
        int retries = arcDev.getStoreUpdateDBMaxRetries();
        for (;;) {
            try {
                UpdateDBResult result = new UpdateDBResult();
                ejb.updateDB(ctx, result);
                return result;
            } catch (EJBException e) {
                if (retries-- > 0) {
                    LOG.info("{}: Failed to update DB - retry:\n", session, e);
                } else {
                    LOG.warn("{}: Failed to update DB:\n", session, e);
                    throw e;
                }
            }
            try {
                Thread.sleep(ThreadLocalRandom.current().nextInt(arcDev.getStoreUpdateDBMaxRetryDelay()));
            } catch (InterruptedException e) {
                LOG.info("{}: Failed to delay retry to update DB:\n", session, e);
            }
        }
    }

    private void postUpdateDB(StoreContext ctx, UpdateDBResult result) throws IOException {
        Instance instance = result.getCreatedInstance();
        if (instance != null) {
            if (result.getCreatedPatient() != null) {
                synchronized (this) {
                    try {
                        ejb.checkDuplicatePatientCreated(ctx, result);
                    } catch (Exception e) {
                        LOG.warn("{}: Failed to remove duplicate created {}:\n",
                                ctx.getStoreSession(), result.getCreatedPatient(), e);
                    }
                }
            }
            Series series = instance.getSeries();
            updateAttributes(ctx, series);
            ctx.getStoreSession().cacheSeries(series);
        }
        commitStorage(result);
        ctx.getLocations().clear();
        ctx.getLocations().addAll(result.getLocations());
        ctx.setRejectionNote(result.getRejectionNote());
        ctx.setPreviousInstance(result.getPreviousInstance());
        ctx.setStoredInstance(result.getStoredInstance());
    }

    private void commitStorage(UpdateDBResult result) throws IOException {
        for (WriteContext writeContext : result.getWriteContexts()) {
            Storage storage = writeContext.getStorage();
            storage.commitStorage(writeContext);
        }
    }

    @Override
    public void store(StoreContext ctx, Attributes attrs) throws IOException {
        ctx.setAttributes(attrs);
        List<Location> locations = ctx.getLocations();
        UpdateDBResult result = null;
        try {
            if (locations.isEmpty()) {
                try (DicomOutputStream dos = new DicomOutputStream(
                        openOutputStream(ctx, Location.ObjectType.DICOM_FILE), UID.ExplicitVRLittleEndian)) {
                    dos.writeDataset(attrs.createFileMetaInformation(ctx.getStoreTranferSyntax()), attrs);
                }
                checkCharacterSet(ctx);
                storeMetadata(ctx);
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
            revokeStorage(ctx, result);
            storeEvent.fire(ctx);
        }
    }

    @Override
    public Attributes copyInstances(
            StoreSession session, Collection<InstanceLocations> instances, Map<String, String> uidMap)
            throws IOException {
        Attributes result = new Attributes();
        session.setUIDMap(uidMap);
        if (instances != null) {
            Sequence refSOPSeq = result.newSequence(Tag.ReferencedSOPSequence, 10);
            Sequence failedSOPSeq = result.newSequence(Tag.FailedSOPSequence, 10);
            for (InstanceLocations il : instances) {
                Attributes attr = il.getAttributes();
                UIDUtils.remapUIDs(attr, uidMap);
                StoreContext ctx = newStoreContext(session);
                for (Location location : il.getLocations()) {
                    ctx.getLocations().add(location);
                }
                ctx.setRetrieveAETs(il.getRetrieveAETs());
                ctx.setAvailability(il.getAvailability());
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
            StoreSession session, Attributes instanceRefs, String targetStudyIUID, Map<String, String> uidMap)
            throws IOException {
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
        ctx.setObjectType(null);
        if (!retrieveService.calculateMatches(ctx))
            return null;
        Collection<InstanceLocations> matches = ctx.getMatches();
        Iterator<InstanceLocations> matchesIter = matches.iterator();
        while (matchesIter.hasNext()) {
            InstanceLocations il = matchesIter.next();
            if (contains(refIUIDsBySeriesIUID, il)) {
                uidMap.put(il.getSopInstanceUID(), UIDUtils.createUID());
                if (refSeriesSeq == null)
                    if (!uidMap.containsKey(il.getAttributes().getString(Tag.SeriesInstanceUID)))
                        uidMap.put(il.getAttributes().getString(Tag.SeriesInstanceUID), UIDUtils.createUID());
            } else
                matchesIter.remove();
        }
        return matches;
    }

    private void checkCharacterSet(StoreContext ctx) {
        Attributes attrs = ctx.getAttributes();
        if (attrs.contains(Tag.SpecificCharacterSet))
            return;

        StoreSession session = ctx.getStoreSession();
        String characterSet = session.getArchiveAEExtension().defaultCharacterSet();
        if (characterSet != null) {
            LOG.debug("{}: No Specific Character Set (0008,0005) in received data set - " +
                            "supplement configured Default Character Set: {}", session, characterSet);
            attrs.setString(Tag.SpecificCharacterSet, VR.CS, characterSet);
        }
    }

    private void storeMetadata(StoreContext ctx) throws IOException {
        OutputStream out = openOutputStream(ctx, Location.ObjectType.METADATA);
        if (out != null) {
            try (JsonGenerator gen = Json.createGenerator(out)) {
                JSONWriter jsonWriter = new JSONWriter(gen);
                jsonWriter.setReplaceBulkDataURI("");
                jsonWriter.write(ctx.getAttributes());
            }
        }
    }

    private Set<String> refIUIDs(Sequence refSOPSeq) {
        if (refSOPSeq == null)
            return null;
        Set<String> iuids = new HashSet<>(refSOPSeq.size() * 4 / 3 + 1);
        for (Attributes refSOP : refSOPSeq)
            iuids.add(refSOP.getString(Tag.ReferencedSOPInstanceUID));
        return iuids;
    }

    private boolean contains(Map<String, Set<String>> refIUIDsBySeriesIUID, InstanceLocations il) {
        Set<String> iuids = refIUIDsBySeriesIUID.get(il.getAttributes().getString(Tag.SeriesInstanceUID));
        return iuids == null || iuids.contains(il.getSopInstanceUID());
    }

    private static void revokeStorage(StoreContext ctx, UpdateDBResult result) {
        for (WriteContext writeCtx : ctx.getWriteContexts()) {
            if ((result == null || !result.getWriteContexts().contains(writeCtx))
                    && writeCtx.getStoragePath() != null) {
                Storage storage = writeCtx.getStorage();
                try {
                    storage.revokeStorage(writeCtx);
                } catch (IOException e) {
                    LOG.warn("Failed to revoke storage", e);
                }
            }
        }
    }

    private void coerceAttributes(StoreContext ctx) {
        StoreSession session = ctx.getStoreSession();
        ArchiveAttributeCoercion rule = session.getArchiveAEExtension().findAttributeCoercion(
                session.getRemoteHostName(),
                session.getCallingAET(),
                TransferCapability.Role.SCU,
                Dimse.C_STORE_RQ,
                ctx.getSopClassUID());
        if (rule == null)
            return;

        AttributesCoercion coercion = null;
        String xsltStylesheetURI = rule.getXSLTStylesheetURI();
        if (xsltStylesheetURI != null)
            try {
                Templates tpls = TemplatesCache.getDefault().get(StringUtils.replaceSystemProperties(xsltStylesheetURI));
                coercion = new XSLTAttributesCoercion(tpls, null).includeKeyword(!rule.isNoKeywords());
            } catch (TransformerConfigurationException e) {
                LOG.error("{}: Failed to compile XSL: {}", session, xsltStylesheetURI, e);
            }
        Attributes requestAttrs = queryMWL(ctx, rule);
        if (requestAttrs != null) {
            LOG.info("{}: Coerce Request Attributes from matching MWL item(s)", session);
            coercion = new MergeAttributesCoercion(requestAttrs, coercion);
        }

        if (coercion != null)
            coercion.coerce(ctx.getAttributes(), ctx.getCoercedAttributes());
    }

    private Attributes queryMWL(StoreContext ctx, ArchiveAttributeCoercion rule) {
        MergeMWLMatchingKey mergeMWLMatchingKey = rule.getMergeMWLMatchingKey();
        String tplURI = rule.getMergeMWLTemplateURI();
        if (mergeMWLMatchingKey == null || tplURI == null)
            return null;

        MergeMWLQueryParam queryParam =
                MergeMWLQueryParam.valueOf(mergeMWLMatchingKey, ctx.getAttributes());

        Cache.Entry<Attributes> entry = mergeMWLCache.getEntry(queryParam);
        if (entry != null)
            return entry.value();

        List<Attributes> mwlItems = ejb.queryMWL(ctx, queryParam);
        if (mwlItems == null) {
            mergeMWLCache.put(queryParam, null);
            return null;
        }
        Attributes result = null;
        Sequence reqAttrsSeq = null;
        try {
            Templates tpls = TemplatesCache.getDefault().get(StringUtils.replaceSystemProperties(tplURI));
            for (Attributes mwlItem : mwlItems) {
                Attributes attrs = SAXTransformer.transform(mwlItem, tpls, false, false);
                if (reqAttrsSeq == null) {
                    result = attrs;
                    reqAttrsSeq = attrs.getSequence(Tag.RequestAttributesSequence);
                } else {
                    reqAttrsSeq.add(attrs.getNestedDataset(Tag.RequestAttributesSequence));
                }
            }
        } catch (TransformerConfigurationException tce) {
            LOG.error("{}: Failed to compile XSL: {}", ctx.getStoreSession(), tplURI, tce);
        } catch (SAXException e) {
            LOG.error("{}: Failed to apply XSL: {}", ctx.getStoreSession(), tplURI, e);
        }
        mergeMWLCache.put(queryParam, result);
        return result;
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

    private Storage getStorage(StoreSession session, StorageDescriptor descriptor) {
        Storage storage = session.getStorage(descriptor.getStorageID());
        if (storage == null) {
            storage = storageFactory.getStorage(descriptor);
            session.putStorage(descriptor.getStorageID(), storage);
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
            return openOutputStream(storeContext, Location.ObjectType.DICOM_FILE);
        }

    }

    private OutputStream openOutputStream(StoreContext storeContext, Location.ObjectType objectType)
            throws IOException {
        StoreSession session = storeContext.getStoreSession();
        ArchiveAEExtension arcAE = session.getArchiveAEExtension();
        StorageDescriptor descriptor = objectType == Location.ObjectType.DICOM_FILE
                ? arcAE.getStorageDescriptor()
                : arcAE.getMetadataStorageDescriptor();
        if (descriptor == null)
            return null;

        Storage storage = getStorage(session, descriptor);
        WriteContext writeCtx = storage.createWriteContext();
        writeCtx.setAttributes(storeContext.getAttributes());
        writeCtx.setStudyInstanceUID(storeContext.getStudyInstanceUID());
        writeCtx.setMessageDigest(storage.getStorageDescriptor().getMessageDigest());
        storeContext.setWriteContext(objectType, writeCtx);
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
