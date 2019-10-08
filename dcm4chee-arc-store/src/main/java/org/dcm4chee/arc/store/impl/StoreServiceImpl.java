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
 * Java(TM), hosted at https://github.com/dcm4che.
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


import org.dcm4che3.conf.api.ConfigurationChanges;
import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.DicomConfiguration;
import org.dcm4che3.data.*;
import org.dcm4che3.imageio.codec.ImageDescriptor;
import org.dcm4che3.imageio.codec.Transcoder;
import org.dcm4che3.imageio.codec.TransferSyntaxType;
import org.dcm4che3.io.*;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.net.*;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.CountingInputStream;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.util.UIDUtils;
import org.dcm4chee.arc.Cache;
import org.dcm4chee.arc.MergeMWLQueryParam;
import org.dcm4chee.arc.MergeMWLCache;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.event.SoftwareConfiguration;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.metrics.MetricsService;
import org.dcm4chee.arc.mima.SupplementAssigningAuthorities;
import org.dcm4chee.arc.store.*;
import org.dcm4chee.arc.storage.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.ejb.EJBException;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipInputStream;


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
    private DicomConfiguration conf;

    @Inject
    private StorageFactory storageFactory;

    @Inject
    private StoreServiceEJB ejb;

    @Inject
    private Event<StoreContext> storeEvent;

    @Inject
    private Event<SoftwareConfiguration> softwareConfigurationEvent;

    @Inject
    private MergeMWLCache mergeMWLCache;

    @Inject
    private MetricsService metricsService;

    @Override
    public StoreSession newStoreSession(Association as) {
        StoreSessionImpl session = new StoreSessionImpl(this);
        session.setAssociation(as);
        return session;
    }

    @Override
    public StoreSession newStoreSession(HttpServletRequestInfo httpRequest, ApplicationEntity ae, String sourceAET) {
        StoreSessionImpl session = new StoreSessionImpl(this);
        session.setHttpRequest(httpRequest);
        session.setApplicationEntity(ae);
        session.setCallingAET(sourceAET);
        return session;
    }

    @Override
    public StoreSession newStoreSession(ApplicationEntity ae) {
        StoreSessionImpl session = new StoreSessionImpl(this);
        session.setApplicationEntity(ae);
        return session;
    }

    @Override
    public StoreSession newStoreSession(HL7Application hl7App, Socket socket, UnparsedHL7Message msg, ApplicationEntity ae) {
        StoreSessionImpl session = new StoreSessionImpl(this);
        session.setApplicationEntity(ae);
        session.setSocket(socket);
        session.setMsg(msg);
        session.setHL7Application(hl7App);
        return session;
    }

    @Override
    public StoreContext newStoreContext(StoreSession session) {
        return new StoreContextImpl(session);
    }

    @Override
    public void store(StoreContext ctx, InputStream data) throws IOException {
        UpdateDBResult result = null;
        try {
            CountingInputStream countingInputStream = new CountingInputStream(data);
            long startTime = System.nanoTime();
            writeToStorage(ctx, countingInputStream);
            String callingAET = ctx.getStoreSession().getCallingAET();
            if (callingAET != null) {
                metricsService.acceptDataRate("receive-from-" + callingAET,
                        countingInputStream.getCount(), startTime);
            }
            if (ctx.getAcceptedStudyInstanceUID() != null
                    && !ctx.getAcceptedStudyInstanceUID().equals(ctx.getStudyInstanceUID())) {
                LOG.info("{}: Received Instance[studyUID={},seriesUID={},objectUID={}]" +
                                " does not match requested studyUID={}", ctx.getStoreSession(), ctx.getStudyInstanceUID(),
                        ctx.getSeriesInstanceUID(), ctx.getSopInstanceUID(), ctx.getAcceptedStudyInstanceUID());
                throw new DicomServiceException(DIFF_STUDY_INSTANCE_UID);
            }
            supplementDefaultCharacterSet(ctx);
            storeMetadata(ctx);
            coerceAttributes(ctx);
            result = updateDB(ctx);
            postUpdateDB(ctx, result);
        } catch (DicomServiceException e) {
            ctx.setException(e);
            throw e;
        } catch (Exception e) {
            LOG.info("{}: Unexpected Exception: ", ctx.getStoreSession(), e);
            DicomServiceException dse = new DicomServiceException(Status.ProcessingFailure, e);
            ctx.setException(dse);
            throw dse;
        } finally {
            revokeStorage(ctx, result);
            fireStoreEvent(ctx);
        }
    }

    private void writeToStorage(StoreContext ctx, InputStream data) throws DicomServiceException {
        List<File> bulkDataFiles = Collections.emptyList();
        String receiveTranferSyntax = ctx.getReceiveTranferSyntax();
        ArchiveAEExtension arcAE = ctx.getStoreSession().getArchiveAEExtension();
        try (Transcoder transcoder = receiveTranferSyntax != null
                ? new Transcoder(data, receiveTranferSyntax)
                : new Transcoder(data)) {
            ctx.setReceiveTransferSyntax(transcoder.getSourceTransferSyntax());
            transcoder.setIncludeBulkData(DicomInputStream.IncludeBulkData.URI);
            transcoder.setBulkDataDescriptor(arcAE.getBulkDataDescriptor());
            transcoder.setPixelDataBulkDataURI("");
            transcoder.setConcatenateBulkDataFiles(true);
            transcoder.setBulkDataDirectory(arcAE.getBulkDataSpoolDirectoryFile());
            transcoder.setIncludeFileMetaInformation(true);
            transcoder.setDeleteBulkDataFiles(false);
            transcoder.transcode(new TranscoderHandler(ctx));
            bulkDataFiles = transcoder.getBulkDataFiles();
        } catch (StorageException e) {
            LOG.warn("{}: Failed to store received object:\n", ctx.getStoreSession(), e);
            throw new DicomServiceException(Status.OutOfResources, e);
        } catch (Throwable e) {
            LOG.warn("{}: Failed to store received object:\n", ctx.getStoreSession(), e);
            throw new DicomServiceException(Status.ProcessingFailure, e);
        } finally {
            for (File tmpFile : bulkDataFiles)
                tmpFile.delete();
        }
    }

    private UpdateDBResult updateDB(StoreContext ctx) throws DicomServiceException {
        StoreSession session = ctx.getStoreSession();
        ArchiveAEExtension arcAE = session.getArchiveAEExtension();
        ArchiveDeviceExtension arcDev = arcAE.getArchiveDeviceExtension();
        int retries = arcDev.getStoreUpdateDBMaxRetries();
        for (;;) {
            try {
                UpdateDBResult result = new UpdateDBResult(ctx);
                long start = System.currentTimeMillis();
                ejb.updateDB(ctx, result);
                long time = System.currentTimeMillis() - start;
                LOG.info("{}: Updated DB in {} ms", session, time);
                metricsService.accept("db-update-on-store", time);
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
                Thread.sleep(arcDev.storeUpdateDBRetryDelay());
            } catch (InterruptedException e) {
                LOG.info("{}: Failed to delay retry to update DB:\n", session, e);
            }
        }
    }

    private void postUpdateDB(StoreContext ctx, UpdateDBResult result) throws IOException {
        StoreSession storeSession = ctx.getStoreSession();
        LOG.debug("{}: Enter postUpdateDB", storeSession);
        Instance instance = result.getCreatedInstance();
        if (instance != null) {
            if (result.getCreatedPatient() != null) {
                synchronized (this) {
                    try {
                        ejb.checkDuplicatePatientCreated(ctx, result);
                    } catch (Exception e) {
                        LOG.warn("{}: Failed to remove duplicate created {}:\n",
                                storeSession, result.getCreatedPatient(), e);
                    }
                }
            }
            storeSession.cacheSeries(instance.getSeries());
        }
        commitStorage(result);
        ctx.getLocations().clear();
        ctx.getLocations().addAll(result.getLocations());
        ctx.setRejectionNote(result.getRejectionNote());
        ctx.setRejectedInstance(result.getRejectedInstance());
        ctx.setPreviousInstance(result.getPreviousInstance());
        ctx.setStoredInstance(result.getStoredInstance());
        ctx.setAttributes(result.getStoredAttributes());
        ctx.setCoercedAttributes(result.getCoercedAttributes());
        LOG.debug("{}: Leave postUpdateDB", storeSession);
        if (result.getException() != null)
            throw result.getException();
    }

    private void commitStorage(UpdateDBResult result) throws IOException {
        for (WriteContext writeContext : result.getWriteContexts()) {
            Storage storage = writeContext.getStorage();
            storage.commitStorage(writeContext);
        }
    }

    @Override
    public List<String> studyIUIDsByAccessionNo(String accNo) {
        return accNo != null ? ejb.studyIUIDsByAccessionNo(accNo) : Collections.emptyList();
    }

    @Override
    public void addLocation(StoreSession session, Long instancePk, Location location) {
        ejb.addLocation(session, instancePk, location);
    }

    @Override
    public void replaceLocation(StoreSession session, Long instancePk, Location newLocation,
            List<Location> replaceLoactions) {
        ejb.replaceLocation(session, instancePk, newLocation, replaceLoactions);
    }

    @Override
    public void compress(StoreContext ctx, InstanceLocations inst, InputStream data)
            throws IOException {
        writeToStorage(ctx, data);
        ejb.replaceLocation(ctx, inst);
    }

    @Override
    public void addStorageID(String studyIUID, String storageID) {
        ejb.addStorageID(studyIUID, storageID);
    }

    @Override
    public void scheduleMetadataUpdate(String studyIUID, String seriesIUID) {
        ejb.scheduleMetadataUpdate(studyIUID, seriesIUID);
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
                adjustPixelDataBulkData(attrs);
                supplementDefaultCharacterSet(ctx);
                storeMetadata(ctx);
                coerceAttributes(ctx);
            }
            result = updateDB(ctx);
            postUpdateDB(ctx, result);
        } catch (DicomServiceException e) {
            ctx.setException(e);
            throw e;
        } catch (Exception e) {
            LOG.info("{}: Unexpected Exception:\n", ctx.getStoreSession(), e);
            DicomServiceException dse = new DicomServiceException(Status.ProcessingFailure, e);
            ctx.setException(dse);
            throw dse;
        } finally {
            revokeStorage(ctx, result);
            fireStoreEvent(ctx);
        }
    }

    @Override
    public void importInstanceOnStorage(StoreContext ctx, Attributes attrs, ReadContext readCtx) throws IOException {
        ctx.setAttributes(Objects.requireNonNull(attrs));
        ctx.setReadContext(Objects.requireNonNull(readCtx));
        UpdateDBResult result = null;
        try {
            adjustPixelDataBulkData(attrs);
            supplementDefaultCharacterSet(ctx);
            storeMetadata(ctx);
            coerceAttributes(ctx);
            result = updateDB(ctx);
            postUpdateDB(ctx, result);
        } catch (DicomServiceException e) {
            ctx.setException(e);
            throw e;
        } catch (Exception e) {
            LOG.info("{}: Unexpected Exception:\n", ctx.getStoreSession(), e);
            DicomServiceException dse = new DicomServiceException(Status.ProcessingFailure, e);
            ctx.setException(dse);
            throw dse;
        } finally {
            revokeStorage(ctx, result);
            fireStoreEvent(ctx);
        }
    }

    public void fireStoreEvent(StoreContext ctx) throws DicomServiceException {
        try {
            LOG.debug("{}: Firing Store Event", ctx.getStoreSession());
            storeEvent.fire(ctx);
            LOG.debug("{}: Fired Store Event", ctx.getStoreSession());
        } catch (RuntimeException e) {
            LOG.warn("{}: Firing Store Event throws Exception:\n", ctx.getStoreSession(), e);
            DicomServiceException dse = new DicomServiceException(Status.ProcessingFailure, e);
            ctx.setException(dse);
            throw dse;
        }
    }

    private void adjustPixelDataBulkData(Attributes attrs) {
        Object value = attrs.getValue(Tag.PixelData);
        if (value instanceof Fragments)
            attrs.setValue(Tag.PixelData, VR.OB, new BulkData(null, "", false));
    }

    @Override
    public Attributes copyInstances(StoreSession session, Collection<InstanceLocations> instances,
                                    Attributes coerceAttrs, Attributes.UpdatePolicy updatePolicy)
            throws Exception {
        Attributes result = new Attributes();
        if (instances != null) {
            Sequence refSOPSeq = result.newSequence(Tag.ReferencedSOPSequence, 10);
            Sequence failedSOPSeq = result.newSequence(Tag.FailedSOPSequence, 10);
            for (InstanceLocations il : instances) {
                Attributes attr = il.getAttributes();
                StoreContext ctx = newStoreContext(session);
                UIDUtils.remapUIDs(attr, session.getUIDMap(), ctx.getCoercedAttributes());
                coerceAttrs(ctx, attr, coerceAttrs, updatePolicy);
                for (Location location : il.getLocations()) {
                    ctx.getLocations().add(location);
                    if (location.getObjectType() == Location.ObjectType.DICOM_FILE)
                        ctx.setStoreTranferSyntax(location.getTransferSyntaxUID());
                }
                ctx.setRetrieveAETs(il.getRetrieveAETs());
                ctx.setAvailability(il.getAvailability());
                try {
                    store(ctx, attr);
                    populateResult(refSOPSeq, attr);
                } catch (DicomServiceException e) {
                    result.setString(Tag.FailureReason, VR.US, Integer.toString(e.getStatus()));
                    attr.setString(Tag.SOPInstanceUID, VR.UI, il.getSopInstanceUID());
                    populateResult(failedSOPSeq, attr);
                }
            }
        }
        return result;
    }

    private void coerceAttrs(StoreContext ctx, Attributes attrs, Attributes coerceAttrs,
                             Attributes.UpdatePolicy updatePolicy) {
        if (coerceAttrs == null)
            return;

        StoreSession session = ctx.getStoreSession();
        ArchiveAEExtension arcAE = session.getArchiveAEExtension();
        ArchiveDeviceExtension arcDev = arcAE.getArchiveDeviceExtension();
        coerceAttrs = new Attributes(coerceAttrs);
        coerceAttrs.updateSelected(session.getStudyUpdatePolicy(), attrs, null,
                arcDev.getAttributeFilter(Entity.Study).getSelection(false));
        attrs.update(updatePolicy, coerceAttrs, ctx.getCoercedAttributes());
    }

    private void populateResult(Sequence refSOPSeq, Attributes ilAttr) {
        Attributes refSOP = new Attributes(2);
        refSOP.setString(Tag.ReferencedSOPClassUID, VR.UI, ilAttr.getString(Tag.SOPClassUID));
        refSOP.setString(Tag.ReferencedSOPInstanceUID, VR.UI, ilAttr.getString(Tag.SOPInstanceUID));
        refSOPSeq.add(refSOP);
    }

    private void supplementDefaultCharacterSet(StoreContext ctx) {
        Attributes attrs = ctx.getAttributes();
        if (attrs.containsValue(Tag.SpecificCharacterSet))
            return;

        StoreSession session = ctx.getStoreSession();
        String defaultCharacterSet = session.getArchiveAEExtension().defaultCharacterSet();
        if (defaultCharacterSet != null) {
            LOG.info("{}: No Specific Character Set (0008,0005) in received data set - " +
                    "supplement configured Default Character Set: {}", session, defaultCharacterSet);
            attrs.setString(Tag.SpecificCharacterSet, VR.CS, defaultCharacterSet);
        }
    }

    private void storeMetadata(StoreContext ctx) throws IOException {
        if (ctx.getStoreSession().getArchiveAEExtension().getMetadataStorageIDs().length > 0) {
            try (JsonGenerator gen = Json.createGenerator(openOutputStream(ctx, Location.ObjectType.METADATA))) {
                JSONWriter jsonWriter = new JSONWriter(gen);
                jsonWriter.setReplaceBulkDataURI("");
                jsonWriter.write(ctx.getAttributes());
            }
        }
    }

    private static void revokeStorage(StoreContext ctx, UpdateDBResult result) {
        for (WriteContext writeCtx : ctx.getWriteContexts()) {
            if ((result == null || !result.getWriteContexts().contains(writeCtx))
                    && writeCtx.getStoragePath() != null) {
                Storage storage = writeCtx.getStorage();
                try {
                    storage.revokeStorage(writeCtx);
                } catch (Exception e) {
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
        coercion = coerceAttributesByXSL(ctx, rule, coercion);
        coercion = mergeAttributesFromMWL(ctx, rule, coercion);
        coercion = SupplementAssigningAuthorities.forInstance(rule.getSupplementFromDevice(), coercion);
        coercion = rule.supplementIssuerOfPatientID(coercion);
        coercion = rule.nullifyIssuerOfPatientID(ctx.getAttributes(), coercion);
        coercion = NullifyAttributesCoercion.valueOf(rule.getNullifyTags(), coercion);
        if (rule.isTrimISO2022CharacterSet())
            coercion = new TrimISO2020CharacterSetAttributesCoercion(coercion);
        coercion = UseCallingAETitleAsCoercion.of(rule.getUseCallingAETitleAs(), session.getCallingAET(), coercion);
        if (coercion != null)
            coercion.coerce(ctx.getAttributes(), ctx.getCoercedAttributes());
    }

    private AttributesCoercion coerceAttributesByXSL(
            StoreContext ctx, ArchiveAttributeCoercion rule, AttributesCoercion next) {
        String xsltStylesheetURI = rule.getXSLTStylesheetURI();
        if (xsltStylesheetURI != null)
            try {
                Templates tpls = TemplatesCache.getDefault().get(StringUtils.replaceSystemProperties(xsltStylesheetURI));
                LOG.info("Coerce Attributes from rule: {}", rule);
                return new XSLTAttributesCoercion(tpls, null)
                        .includeKeyword(!rule.isNoKeywords())
                        .setupTransformer(setupTransformer(ctx.getStoreSession()));
            } catch (TransformerConfigurationException e) {
                LOG.error("{}: Failed to compile XSL: {}", ctx.getStoreSession(), xsltStylesheetURI, e);
            }
        return next;
    }

    private SAXTransformer.SetupTransformer setupTransformer(StoreSession session) {
        return t -> {
            t.setParameter("LocalAET", session.getCalledAET());
            if (session.getCallingAET() != null)
                t.setParameter("RemoteAET", session.getCallingAET());
            if (session.getRemoteHostName() != null)
                t.setParameter("RemoteHost", session.getRemoteHostName());
        };
    }

    private AttributesCoercion mergeAttributesFromMWL(
            StoreContext ctx, ArchiveAttributeCoercion rule, AttributesCoercion next) {
        Attributes requestAttrs = queryMWL(ctx, rule);
        if (requestAttrs == null)
            return next;

        LOG.info("{}: Coerce Request Attributes from matching MWL item(s) using rule: {}", ctx.getStoreSession(), rule);
        return new MergeAttributesCoercion(requestAttrs, next);
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
                Attributes attrs = SAXTransformer.transform(mwlItem, tpls, false, rule.isNoKeywords());
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

    private final class TranscoderHandler implements Transcoder.Handler {
        private final StoreContext storeContext;

        private TranscoderHandler(StoreContext storeContext) {
            this.storeContext = storeContext;
        }

        @Override
        public OutputStream newOutputStream(Transcoder transcoder, Attributes dataset) throws IOException {
            storeContext.setAttributes(dataset);
            ArchiveCompressionRule compressionRule = storeContext.getCompressionRule();
            if (compressionRule == null) {
                storeContext.setCompressionRule(compressionRule = selectCompressionRule(transcoder, storeContext));
            }
            if (compressionRule != null && compressionRule.getDelay() == null) {
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
        Storage storage = objectType == Location.ObjectType.DICOM_FILE
                ? selectObjectStorage(session)
                : selectMetadataStorage(session);

        WriteContext writeCtx = storage.createWriteContext();
        writeCtx.setAttributes(storeContext.getAttributes());
        writeCtx.setStudyInstanceUID(storeContext.getStudyInstanceUID());
        writeCtx.setMessageDigest(storage.getStorageDescriptor().getMessageDigest());
        storeContext.setWriteContext(objectType, writeCtx);
        return storage.openOutputStream(writeCtx);
    }

    private Storage selectObjectStorage(StoreSession session) throws IOException {
        if (session.getObjectStorageID() != null)
            return session.getStorage(session.getObjectStorageID(), storageFactory);

        ArchiveAEExtension arcAE = session.getArchiveAEExtension();
        ArchiveDeviceExtension arcDev = arcAE.getArchiveDeviceExtension();
        String[] storageIDs = arcAE.getObjectStorageIDs();
        List<StorageDescriptor> descriptors = arcDev.getStorageDescriptors(storageIDs);
        int storageCount = Math.min(arcAE.getObjectStorageCount(), descriptors.size());
        if (storageCount > 1) {
            int index = session.getSerialNo() % storageCount;
            descriptors.add(0, descriptors.remove(index));
        }
        Storage storage = storageFactory.getUsableStorage(descriptors);
        String storageID = storage.getStorageDescriptor().getStorageID();
        session.putStorage(storageID, storage);
        session.withObjectStorageID(storageID);
        if (descriptors.size() < storageIDs.length) {
            arcAE.setObjectStorageIDs(StorageDescriptor.storageIDsOf(descriptors));
            updateDeviceConfiguration(arcDev);
        }
        return storage;
    }

    private void updateDeviceConfiguration(ArchiveDeviceExtension arcDev) {
        Device device = arcDev.getDevice();
        try {
            LOG.info("Update Storage configuration of Device: {}", device.getDeviceName());
            ConfigurationChanges diffs = conf.merge(device, EnumSet.of(
                    DicomConfiguration.Option.PRESERVE_VENDOR_DATA,
                    DicomConfiguration.Option.PRESERVE_CERTIFICATE,
                    arcDev.isAuditSoftwareConfigurationVerbose()
                            ? DicomConfiguration.Option.CONFIGURATION_CHANGES_VERBOSE
                            : DicomConfiguration.Option.CONFIGURATION_CHANGES));
            softwareConfigurationEvent.fire(new SoftwareConfiguration(null, device.getDeviceName(), diffs));
        } catch (ConfigurationException e) {
            LOG.warn("Failed to update Storage configuration of Device: {}:\n", device.getDeviceName(), e);
        }
    }

    private Storage selectMetadataStorage(StoreSession session) throws IOException {
        if (session.getMetadataStorageID() != null)
            return session.getStorage(session.getMetadataStorageID(), storageFactory);

        ArchiveAEExtension arcAE = session.getArchiveAEExtension();
        ArchiveDeviceExtension arcDev = arcAE.getArchiveDeviceExtension();
        String[] storageIDs = arcAE.getMetadataStorageIDs();
        List<StorageDescriptor> descriptors = arcDev.getStorageDescriptors(storageIDs);
        Storage storage = storageFactory.getUsableStorage(descriptors);
        String storageID = storage.getStorageDescriptor().getStorageID();
        session.putStorage(storageID, storage);
        session.setMetadataStorageID(storageID);
        if (descriptors.size() < storageIDs.length) {
            arcAE.setMetadataStorageIDs(StorageDescriptor.storageIDsOf(descriptors));
            updateDeviceConfiguration(arcDev);
        }
        return storage;
    }



    @Override
    public ZipInputStream openZipInputStream(
            StoreSession session, String storageID, String storagePath, String studyUID)
            throws IOException {
        Storage storage = session.getStorage(storageID, storageFactory);
        ReadContext readContext = storage.createReadContext();
        readContext.setStoragePath(storagePath);
        readContext.setStudyInstanceUID(studyUID);
        return new ZipInputStream(storage.openInputStream(readContext));
    }

    @Override
    public List<Instance> restoreInstances(StoreSession session, String studyUID, String seriesUID, Duration duration)
            throws IOException {
        return ejb.restoreInstances(session, studyUID, seriesUID, duration);
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

    @Override
    public void updateLocations(ArchiveAEExtension arcAE, List<UpdateLocation> updateLocations) {
        Map<String, Map<String, List<UpdateLocation>>> updateLocationsByStudyAndSeriesIUID = updateLocations.stream()
                .collect(Collectors.groupingBy(
                        x -> x.instanceLocation.getAttributes().getString(Tag.StudyInstanceUID),
                        Collectors.groupingBy(
                                x -> x.instanceLocation.getAttributes().getString(Tag.SeriesInstanceUID))));
        updateLocationsByStudyAndSeriesIUID.forEach(
                (studyIUID, seriesMap) -> seriesMap.forEach(
                        (seriesIUID, updateLocationsOfSeries) ->
                            updateLocationsOfSeries(arcAE, studyIUID, seriesIUID, updateLocationsOfSeries)));
    }

    private void updateLocationsOfSeries(ArchiveAEExtension arcAE, String studyIUID, String seriesIUID,
                                         List<UpdateLocation> updateLocationsOfSeries) {
        boolean instancesPurged = updateLocationsOfSeries.get(0).location.getPk() == 0;
        if (instancesPurged) {
            try {
                restoreInstances(arcAE, studyIUID, seriesIUID, updateLocationsOfSeries);
                instancesPurged = false;
            } catch (Exception e) {
                LOG.warn("Failed to restore Instance records of Series[uid={}] of Study[uid={}]" +
                                " - cannot update Location records\n",
                        seriesIUID, studyIUID, e);
            }
        }
        if (!instancesPurged) {
            for (UpdateLocation updateLocation : updateLocationsOfSeries) {
                if (updateLocation.newStatus != null) {
                    LOG.debug("Update status of {} of Instance[uid={}] of Study[uid={}] to {}",
                            updateLocation.location,
                            updateLocation.instanceLocation.getSopInstanceUID(),
                            studyIUID,
                            updateLocation.newStatus);
                    ejb.setStatus(updateLocation.location.getPk(), updateLocation.newStatus);
                } else {
                    LOG.debug("Set missing digest of {} of Instance[uid={}] of Study[uid={}]",
                            updateLocation.location,
                            updateLocation.instanceLocation.getSopInstanceUID(),
                            studyIUID);
                    ejb.setDigest(updateLocation.location.getPk(), updateLocation.newDigest);
                }
            }
            scheduleMetadataUpdate(studyIUID, seriesIUID);
        }
    }

    private void restoreInstances(ArchiveAEExtension arcAE, String studyIUID, String seriesIUID,
                                  List<UpdateLocation> updateLocations) throws IOException {
        List<Instance> instances = ejb.restoreInstances(newStoreSession(arcAE.getApplicationEntity()),
                studyIUID, seriesIUID, arcAE.getPurgeInstanceRecordsDelay());
        Map<String, Map<String, Location>> restoredLocations = instances.stream()
                .flatMap(inst -> inst.getLocations().stream())
                .collect(Collectors.groupingBy(l -> l.getStorageID(),
                        Collectors.toMap(l -> l.getStoragePath(), Function.identity())));
        for (Iterator<UpdateLocation> iter = updateLocations.iterator(); iter.hasNext(); ) {
            UpdateLocation updateLocation = iter.next();
            Location l = updateLocation.location;
            updateLocation.location = restoredLocations.get(l.getStorageID()).get(l
                    .getStoragePath());
            if (updateLocation.location == null) {
                LOG.warn("Failed to find {} record of Instance[uid={}] of Series[uid={}] of Study[uid={}]" +
                                " - cannot update Location record",
                        l, updateLocation.instanceLocation.getSopInstanceUID(), seriesIUID,
                        studyIUID);
                iter.remove();
            }
        }
    }

}
