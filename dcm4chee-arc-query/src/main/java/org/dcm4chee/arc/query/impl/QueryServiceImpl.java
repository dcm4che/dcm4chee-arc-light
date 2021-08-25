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
 * Portions created by the Initial Developer are Copyright (C) 2015
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

package org.dcm4chee.arc.query.impl;

import org.dcm4che3.data.*;
import org.dcm4che3.io.SAXTransformer;
import org.dcm4che3.io.TemplatesCache;
import org.dcm4che3.io.XSLTAttributesCoercion;
import org.dcm4che3.json.JSONReader;
import org.dcm4che3.net.*;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.SafeClose;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.util.UIDUtils;
import org.dcm4chee.arc.LeadingCFindSCPQueryCache;
import org.dcm4chee.arc.code.CodeCache;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.mima.SupplementAssigningAuthorities;
import org.dcm4chee.arc.query.Query;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.query.scu.CFindSCU;
import org.dcm4chee.arc.query.scu.CFindSCUAttributeCoercion;
import org.dcm4chee.arc.query.util.OrderByTag;
import org.dcm4chee.arc.query.util.QueryBuilder;
import org.dcm4chee.arc.query.util.QueryParam;
import org.dcm4chee.arc.storage.ReadContext;
import org.dcm4chee.arc.storage.Storage;
import org.dcm4chee.arc.storage.StorageFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.EJBException;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.json.Json;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Tuple;
import javax.persistence.criteria.*;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Aug 2015
 */
@ApplicationScoped
class QueryServiceImpl implements QueryService {

    private static Logger LOG = LoggerFactory.getLogger(QueryServiceImpl.class);

    @PersistenceContext(unitName = "dcm4chee-arc")
    private EntityManager em;

    @Inject
    private Device device;

    @Inject
    private QueryServiceEJB ejb;

    @Inject
    QuerySizeEJB querySizeEJB;

    @Inject
    QueryAttributesEJB queryAttributesEJB;

    @Inject
    private CFindSCU cfindscu;

    @Inject
    private LeadingCFindSCPQueryCache leadingCFindSCPQueryCache;

    @Inject
    private CodeCache codeCache;

    @Inject
    private StorageFactory storageFactory;

    @Inject
    private Event<QueryContext> queryEvent;

    @Override
    public QueryContext newQueryContextFIND(Association as, String sopClassUID, EnumSet<QueryOption> queryOpts) {
        ApplicationEntity ae = as.getApplicationEntity();
        QueryParam queryParam = new QueryParam(ae);
        queryParam.setCombinedDatetimeMatching(queryOpts.contains(QueryOption.DATETIME));
        queryParam.setFuzzySemanticMatching(queryOpts.contains(QueryOption.FUZZY));
        queryParam.setCalledAET(as.getCalledAET());
        QueryContextImpl ctx = new QueryContextImpl(ae, queryParam, this).find(as, sopClassUID);
        queryParam.setHideSPSWithStatusFromMWL(ctx.getArchiveAEExtension().hideSPSWithStatusFromMWL());
        return ctx;
    }

    @Override
    public QueryContext newQueryContextQIDO(
            HttpServletRequestInfo httpRequest, String searchMethod, String aet,
            ApplicationEntity ae, QueryParam queryParam) {
        QueryContextImpl ctx = new QueryContextImpl(ae, queryParam, this)
                .qido(httpRequest, searchMethod, aet);
        queryParam.setHideSPSWithStatusFromMWL(ctx.getArchiveAEExtension().hideSPSWithStatusFromMWLRS());
        return ctx;
    }

    @Override
    public QueryContext newQueryContext(ApplicationEntity ae, QueryParam queryParam) {
        return new QueryContextImpl(ae, queryParam, this);
    }

    @Override
    public void coerceAttributes(QueryContext ctx) {
        ArchiveAttributeCoercion rule = ctx.getArchiveAEExtension().findAttributeCoercion(
                Dimse.C_FIND_RQ,
                TransferCapability.Role.SCU,
                ctx.getSOPClassUID(),
                ctx.getRemoteHostName(),
                ctx.getCallingAET(),
                ctx.getLocalHostName(),
                ctx.getCalledAET(),
                ctx.getQueryKeys());
        if (rule == null)
            return;

        LOG.info("Apply {} to Search Attributes", rule);
        AttributesCoercion coercion = null;
        coercion = coerceAttributesByXSL(ctx, rule, coercion);
        switch (ctx.getSOPClassUID()) {
            case UID.PatientRootQueryRetrieveInformationModelFind:
            case UID.StudyRootQueryRetrieveInformationModelFind:
            case UID.PatientStudyOnlyQueryRetrieveInformationModelFind:
                coercion = SupplementAssigningAuthorities.forQuery(rule.getSupplementFromDevice(), coercion);
                break;
            case UID.ModalityWorklistInformationModelFind:
                coercion = SupplementAssigningAuthorities.forMWL(rule.getSupplementFromDevice(), coercion);
                break;
        }
        coercion = rule.supplementIssuerOfPatientID(coercion);
        coercion = rule.nullifyIssuerOfPatientID(ctx.getQueryKeys(), coercion);
        coercion = rule.mergeAttributes(coercion);
        coercion = NullifyAttributesCoercion.valueOf(rule.getNullifyTags(), coercion);
        if (rule.isTrimISO2022CharacterSet())
            coercion = new TrimISO2020CharacterSetAttributesCoercion(coercion);
        coercion = UseCallingAETitleAsCoercion.of(rule.getUseCallingAETitleAs(), ctx.getCallingAET(), coercion);
        if (coercion != null) {
            coercion.coerce(ctx.getQueryKeys(), ctx.getCoercedQueryKeys());
            if (LOG.isDebugEnabled() && !ctx.getCoercedQueryKeys().isEmpty())
                LOG.debug("Coerced Search Attributes:\n{} to:\n{}", ctx.getCoercedQueryKeys(),
                        new Attributes(ctx.getQueryKeys(), false, ctx.getCoercedQueryKeys()));
        }
    }

    private AttributesCoercion coerceAttributesByXSL(
            QueryContext ctx, ArchiveAttributeCoercion rule, AttributesCoercion next) {
        String xsltStylesheetURI = rule.getXSLTStylesheetURI();
        if (xsltStylesheetURI != null)
            try {
                Templates tpls = TemplatesCache.getDefault().get(StringUtils.replaceSystemProperties(xsltStylesheetURI));
                return new XSLTAttributesCoercion(tpls, null)
                        .includeKeyword(!rule.isNoKeywords())
                        .setupTransformer(setupTransformer(ctx));
            } catch (TransformerConfigurationException e) {
                LOG.error("{}: Failed to compile XSL: {}", ctx, xsltStylesheetURI, e);
            }
        return next;
    }

    @Override
    public Query createQuery(QueryContext ctx) {
        queryEvent.fire(ctx);
        switch (ctx.getQueryRetrieveLevel()) {
            case PATIENT:
                return createPatientQuery(ctx);
            case STUDY:
                return createStudyQuery(ctx);
            case SERIES:
                return createSeriesQuery(ctx);
            default: // case IMAGE
                return createInstanceQuery(ctx);
        }
    }

    @Override
    public Query createPatientQuery(QueryContext ctx) {
        return new PatientQuery(ctx, em);
    }

    @Override
    public Query createStudyQuery(QueryContext ctx) {
        return new StudyQuery(ctx, em);
    }

    @Override
    public Query createSeriesQuery(QueryContext ctx) {
        return new SeriesQuery(ctx, em);
    }

    @Override
    public Query createInstanceQuery(QueryContext ctx) {
        return new InstanceQuery(ctx, em, codeCache);
    }

    @Override
    public Query createMWLQuery(QueryContext ctx) {
        queryEvent.fire(ctx);
        return new MWLQuery(ctx, em);
    }

    @Override
    public Query createMPPSQuery(QueryContext ctx) {
        queryEvent.fire(ctx);
        return new MPPSQuery(ctx, em);
    }

    @Override
    public Query createUPSQuery(QueryContext ctx) {
        queryEvent.fire(ctx);
        return createUPSWithoutQueryEvent(ctx);
    }

    @Override
    public Query createUPSWithoutQueryEvent(QueryContext ctx) {
        return new UPSQuery(ctx, em);
    }

    @Override
    public Attributes getSeriesAttributes(QueryContext context, Long seriesPk) {
        return ejb.getSeriesAttributes(seriesPk, context);
    }

    @Override
    public void addLocationAttributes(Attributes attrs, Long instancePk) {
        ejb.addLocationAttributes(attrs, instancePk);
    }

    @Override
    public long calculateStudySize(Long studyPk) {
        return querySizeEJB.calculateStudySize(studyPk);
    }

    @Override
    public StudyQueryAttributes calculateStudyQueryAttributes(Long studyPk, QueryRetrieveView qrView) {
        return queryAttributesEJB.calculateStudyQueryAttributes(studyPk, qrView);
    }

    @Override
    public SeriesQueryAttributes calculateSeriesQueryAttributesIfNotExists(Long seriesPk, QueryRetrieveView qrView) {
        return ejb.calculateSeriesQueryAttributesIfNotExists(seriesPk, qrView);
    }

    @Override
    public SeriesQueryAttributes calculateSeriesQueryAttributes(Long seriesPk, QueryRetrieveView qrView) {
        return queryAttributesEJB.calculateSeriesQueryAttributes(seriesPk, qrView);
    }

    @Override
    public Attributes getStudyAttributesWithSOPInstanceRefs(
            String studyUID, ApplicationEntity ae, Collection<Attributes> seriesAttrs) {
        QueryRetrieveView qrView = ae.getAEExtensionNotNull(ArchiveAEExtension.class).getQueryRetrieveView();
        return ejb.getStudyAttributesWithSOPInstanceRefs(
                QueryServiceEJB.SOPInstanceRefsType.KOS_XDSI, studyUID, null, null, qrView, seriesAttrs,
                null, null);
    }

    @Override
    public Attributes createIAN(ApplicationEntity ae, String studyUID, String[] seriesUID, String sopUID,
            String[] retrieveAETs, String retrieveLocationUID, Availability availability) {
        QueryRetrieveView qrView = ae.getAEExtensionNotNull(ArchiveAEExtension.class).getQueryRetrieveView();
        Attributes refStudy = new Attributes(2);
        Sequence refSeriesSeq = refStudy.newSequence(Tag.ReferencedSeriesSequence, 10);
        refStudy.setString(Tag.StudyInstanceUID, VR.UI, studyUID);
        addPurgedSOPInstanceRefs(refSeriesSeq, studyUID, seriesUID, sopUID);
        return ejb.getSOPInstanceRefs(refStudy, QueryServiceEJB.SOPInstanceRefsType.IAN, studyUID, sopUID, qrView, null, retrieveAETs,
                retrieveLocationUID, availability, seriesUID);
    }

    @Override
    public Attributes createXDSiManifest(ApplicationEntity ae, String studyUID,
                                         String[] retrieveAETs, String retrieveLocationUID,
                                         Code conceptNameCode, int seriesNumber, int instanceNumber,
                                         Collection<Attributes> seriesAttrs) {
        QueryRetrieveView qrView = ae.getAEExtensionNotNull(ArchiveAEExtension.class).getQueryRetrieveView();
        Attributes attrs = ejb.getStudyAttributesWithSOPInstanceRefs(
                QueryServiceEJB.SOPInstanceRefsType.KOS_XDSI, studyUID, null, null, qrView, seriesAttrs,
                retrieveAETs, retrieveLocationUID);
        if (attrs == null || !attrs.containsValue(Tag.CurrentRequestedProcedureEvidenceSequence))
            return null;

        mkKOS(attrs, conceptNameCode, seriesNumber, instanceNumber);
        return attrs;

    }

    @Override
    public Attributes createUPSInfo(ApplicationEntity ae, String studyIUID, String seriesIUID, String sopIUID,
                                    ExporterDescriptor exporterDescriptor) {
        QueryRetrieveView qrView = ae.getAEExtensionNotNull(ArchiveAEExtension.class).getQueryRetrieveView();
        Attributes attrs = getStudyAttributes(studyIUID);
        if (attrs == null)
            return null;

        Attributes sopInstanceRefs = ejb.getSOPInstanceRefs(
                QueryServiceEJB.SOPInstanceRefsType.UPS,
                studyIUID, sopIUID, qrView,
                null,
                exporterDescriptor.getRetrieveAETitles(),
                exporterDescriptor.getRetrieveLocationUID(),
                exporterDescriptor.getInstanceAvailability(),
                seriesIUID);
        if (sopInstanceRefs != null)
            attrs.addAll(sopInstanceRefs);
        return attrs;
    }

    @Override
    public Attributes createActionInfo(String studyIUID, String seriesIUID, String sopIUID, ApplicationEntity ae) {
        QueryRetrieveView qrView = ae.getAEExtensionNotNull(ArchiveAEExtension.class).getQueryRetrieveView();
        return ejb.getSOPInstanceRefs(QueryServiceEJB.SOPInstanceRefsType.STGCMT, studyIUID, sopIUID, qrView,
                null, null, null, null, seriesIUID);
    }

    @Override
    public Attributes queryExportTaskInfo(Task exportTask, ApplicationEntity ae) {
        QueryRetrieveView qrView = ae.getAEExtensionNotNull(ArchiveAEExtension.class).getQueryRetrieveView();
        ArchiveDeviceExtension arcDev = ae.getDevice().getDeviceExtension(ArchiveDeviceExtension.class);
        int retries = arcDev.getStoreUpdateDBMaxRetries();
        for (;;) {
            try {
                if (exportTask.getSeriesInstanceUID().equals("*")) {
                    return ejb.queryStudyExportTaskInfo(exportTask.getStudyInstanceUID(), qrView);
                }
                Attributes attrs = ejb.querySeriesExportTaskInfo(
                        exportTask.getStudyInstanceUID(),
                        exportTask.getSeriesInstanceUID(),
                        qrView);
                if (!exportTask.getSOPInstanceUID().equals("*")) {
                    attrs.setInt(Tag.NumberOfStudyRelatedInstances, VR.IS, 1);
                }
                return attrs;
            } catch (EJBException e) {
                if (retries-- > 0) {
                    LOG.info("Failed to query Export Task Info for {} caused by {} - retry",
                            exportTask, DicomServiceException.initialCauseOf(e));
                } else {
                    LOG.warn("Failed to query Export Task Info for {}:\n", exportTask, e);
                    return null;
                }
            }
            try {
                Thread.sleep(arcDev.storeUpdateDBRetryDelay());
            } catch (InterruptedException e) {
                LOG.info("Failed to delay retry to query Export Task Info for {}:\n", exportTask, e);
            }
        }
    }

    @Override
    public Attributes createRejectionNote(
            ApplicationEntity ae, String studyUID, String seriesUID, String objectUID, RejectionNote rjNote) {
        QueryRetrieveView qrView = ae.getAEExtensionNotNull(ArchiveAEExtension.class).getQueryRetrieveView();
        Attributes attrs = ejb.getStudyAttributesWithSOPInstanceRefs(
                QueryServiceEJB.SOPInstanceRefsType.KOS_IOCM, studyUID, seriesUID, objectUID, qrView,
                null,null, null);
        if (attrs == null || !attrs.containsValue(Tag.CurrentRequestedProcedureEvidenceSequence))
            return null;

        mkKOS(attrs, rjNote);
        return attrs;
    }

    @Override
    public Attributes createRejectionNote(Attributes sopInstanceRefs, RejectionNote rjNote) {
        Attributes attrs = ejb.getStudyAttributes(sopInstanceRefs.getString(Tag.StudyInstanceUID));
        attrs.newSequence(Tag.CurrentRequestedProcedureEvidenceSequence, 1).add(sopInstanceRefs);
        mkKOS(attrs, rjNote);
        return attrs;
    }

    private void mkKOS(Attributes attrs, RejectionNote rjNote) {
        mkKOS(attrs, rjNote.getRejectionNoteCode(), rjNote.getSeriesNumber(), rjNote.getInstanceNumber());
    }

    @Override
    public Attributes getStudyAttributes(String studyUID) {
        return ejb.getStudyAttributes(studyUID);
    }

    @Override
    public List<Object[]> getSeriesInstanceUIDs(String studyUID) {
        return em.createNamedQuery(Series.SERIES_IUIDS_OF_STUDY, Object[].class)
                .setParameter(1, studyUID)
                .getResultList();
    }

    @Override
    public List<Object[]> getSOPInstanceUIDs(String studyUID) {
        return em.createNamedQuery(Instance.IUIDS_OF_STUDY, Object[].class)
                    .setParameter(1, studyUID)
                    .getResultList();
    }

    @Override
    public List<Object[]> getSOPInstanceUIDs(String studyUID, String seriesUID) {
        return em.createNamedQuery(Instance.IUIDS_OF_SERIES, Object[].class)
                    .setParameter(1, studyUID)
                    .setParameter(2, seriesUID).getResultList();
    }

    @Override
    public Integer getNumberOfFrames(String studyUID, String seriesUID, String objUID) {
        return em.createNamedQuery(Instance.NUMBER_OF_FRAMES, Integer.class)
                .setParameter(1, studyUID)
                .setParameter(2, seriesUID)
                .setParameter(3, objUID)
                .getSingleResult();
    }

    @Override
    public ZipInputStream openZipInputStream(QueryContext ctx, String storageID, String storagePath)
            throws IOException {
        return openZipInputStream(getStorage(storageID, ctx), storagePath,
                ctx.getQueryKeys().getString(Tag.StudyInstanceUID));
    }

    private static ZipInputStream openZipInputStream(Storage storage, String storagePath, String studyInstanceUID)
            throws IOException {
        return new ZipInputStream(storage.openInputStream(
                createReadContext(storage, storagePath, studyInstanceUID)));
    }

    private Storage getStorage(String storageID, QueryContext ctx) {
        Storage storage = ctx.getStorage(storageID);
        if (storage == null) {
            storage = getStorage(storageID);
            ctx.putStorage(storageID, storage);
        }
        return storage;
    }

    private Storage getStorage(String storageID) {
        return storageFactory.getStorage(arcDev().getStorageDescriptorNotNull(storageID));
    }

    private ArchiveDeviceExtension arcDev() {
        return device.getDeviceExtension(ArchiveDeviceExtension.class);
    }

    private class StorageCache implements Closeable {
        private final Map<String, Storage> storageMap = new HashMap<>();

        public Storage getStorage(String storageID) {
            return storageMap.computeIfAbsent(storageID, QueryServiceImpl.this::getStorage);
        }

        @Override
        public void close() {
            for (Storage storage : storageMap.values())
                SafeClose.close(storage);
        }
    }

    private void addPurgedSOPInstanceRefs(Sequence refSeriesSeq, String studyUID, String[] seriesUIDs, String sopUID) {
        if (!arcDev().isPurgeInstanceRecords())
            return;

        try (StorageCache storageCache = new StorageCache()) {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<Tuple> q = cb.createTupleQuery();
            Root<Series> series = q.from(Series.class);
            Join<Series, Metadata> metadata = series.join(Series_.metadata);
            Join<Series, Study> study = series.join(Series_.study);
            em.createQuery(
                    q.where(
                            purgedSeriesPredicate(cb, studyUID, seriesUIDs, series, study))
                            .multiselect(
                                    series.get(Series_.seriesInstanceUID),
                                    metadata.get(Metadata_.storageID),
                                    metadata.get(Metadata_.storagePath)))
                    .getResultList()
                    .stream()
                    .forEach(tuple -> addSOPInstanceRefsFromMetadata(refSeriesSeq,
                            studyUID,
                            tuple.get(series.get(Series_.seriesInstanceUID)),
                            sopUID,
                            tuple.get(metadata.get(Metadata_.storageID)),
                            tuple.get(metadata.get(Metadata_.storagePath)),
                            storageCache));
        }
    }

    private static Predicate[] purgedSeriesPredicate(CriteriaBuilder cb, String studyIUID, String[] seriesUID,
            Root<Series> series, Join<Series, Study> study) {
        List<Predicate> predicates = new ArrayList<>(3);
        predicates.add(cb.equal(study.get(Study_.studyInstanceUID), studyIUID));
        if (!QueryBuilder.isUniversalMatching(seriesUID)) {
            Path<String> path = series.get(Series_.seriesInstanceUID);
            predicates.add(seriesUID.length == 1 ? cb.equal(path, seriesUID[0]) : path.in(seriesUID));
        }
        predicates.add(cb.equal(series.get(Series_.instancePurgeState), Series.InstancePurgeState.PURGED));
        return predicates.toArray(new Predicate[0]);
    }

    private static void addSOPInstanceRefsFromMetadata(Sequence refSeriesSeq, String studyUID, String seriesUID,
            String sopUID, String storageID, String storagePath, StorageCache storageCache) {
        Sequence refSOPSeq = null;
        Storage storage = storageCache.getStorage(storageID);
        try (ZipInputStream seriesMetadataStream = openZipInputStream(storage, storagePath, studyUID)) {
            ZipEntry entry;
            while ((entry = seriesMetadataStream.getNextEntry()) != null) {
                if (sopUID == null || sopUID.equals(entry.getName())) {
                    JSONReader jsonReader = new JSONReader(Json.createParser(
                            new InputStreamReader(seriesMetadataStream, StandardCharsets.UTF_8)));
                    jsonReader.setSkipBulkDataURI(true);
                    Attributes metadata = jsonReader.readDataset(null);
                    if (refSOPSeq == null) {
                        Attributes refSeries = new Attributes(2);
                        refSeries.setString(Tag.SeriesInstanceUID, VR.UI, seriesUID);
                        refSOPSeq = refSeries.newSequence(Tag.ReferencedSOPSequence, 10);
                        refSeriesSeq.add(refSeries);
                    }
                    Attributes refSOP = new Attributes(4);
                    refSOP.setString(Tag.RetrieveAETitle, VR.AE, metadata.getString(Tag.RetrieveAETitle));
                    refSOP.setString(Tag.InstanceAvailability, VR.CS, metadata.getString(Tag.InstanceAvailability));
                    refSOP.setString(Tag.ReferencedSOPClassUID, VR.UI, metadata.getString(Tag.SOPClassUID));
                    refSOP.setString(Tag.ReferencedSOPInstanceUID, VR.UI, entry.getName());
                    refSOPSeq.add(refSOP);
                }
                seriesMetadataStream.closeEntry();
            }
        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to read Series Metadata " + storagePath + "@" + storage.getStorageDescriptor(),
                    e);
        }
    }

    private static ReadContext createReadContext(Storage storage, String storagePath, String studyInstanceUID) {
        ReadContext readContext = storage.createReadContext();
        readContext.setStoragePath(storagePath);
        readContext.setStudyInstanceUID(studyInstanceUID);
        return readContext;
    }

    private static void mkKOS(Attributes attrs, Code conceptNameCode, int seriesNumber, int instanceNumber) {
        Attributes studyRef =  attrs.getNestedDataset(Tag.CurrentRequestedProcedureEvidenceSequence);
        attrs.setString(Tag.SOPClassUID, VR.UI, UID.KeyObjectSelectionDocumentStorage);
        attrs.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
        attrs.setDate(Tag.ContentDateAndTime, new Date());
        attrs.setString(Tag.Modality, VR.CS, "KO");
        attrs.setNull(Tag.ReferencedPerformedProcedureStepSequence, VR.SQ);
        attrs.setString(Tag.SeriesInstanceUID, VR.UI, UIDUtils.createUID());
        attrs.setInt(Tag.SeriesNumber, VR.IS, seriesNumber);
        attrs.setInt(Tag.InstanceNumber, VR.IS, instanceNumber);
        attrs.setString(Tag.ValueType, VR.CS, "CONTAINER");
        attrs.setString(Tag.ContinuityOfContent, VR.CS, "SEPARATE");
        attrs.newSequence(Tag.ConceptNameCodeSequence, 1).add(conceptNameCode.toItem());
        attrs.newSequence(Tag.ContentTemplateSequence, 1).add(templateIdentifier());
        Sequence contentSeq = attrs.newSequence(Tag.ContentSequence, 1);
        for (Attributes seriesRef : studyRef.getSequence(Tag.ReferencedSeriesSequence)) {
            for (Attributes sopRef : seriesRef.getSequence(Tag.ReferencedSOPSequence)) {
                String cuid = sopRef.getString(Tag.ReferencedSOPClassUID);
                String iuid = sopRef.getString(Tag.ReferencedSOPInstanceUID);
                contentSeq.add(contentItem(typeOf(cuid), refSOP(cuid, iuid)));
            }
        }
    }

    private static String typeOf(String cuid) {
        String uidName = UID.nameOf(cuid);
        int index = uidName.lastIndexOf(" Storage");
        return uidName.startsWith("Image", index - 5) ? "IMAGE"
            : uidName.startsWith("Waveform", index - 8) ? "WAVEFORM"
            : "COMPOSITE";
    }

    private static Attributes templateIdentifier() {
        Attributes attrs = new Attributes(2);
        attrs.setString(Tag.MappingResource, VR.CS, "DCMR");
        attrs.setString(Tag.TemplateIdentifier, VR.CS, "2010");
        return attrs;
    }

    private static Attributes contentItem(String valueType, Attributes refSOP) {
        Attributes item = new Attributes(3);
        item.setString(Tag.RelationshipType, VR.CS, "CONTAINS");
        item.setString(Tag.ValueType, VR.CS, valueType);
        item.newSequence(Tag.ReferencedSOPSequence, 1).add(refSOP);
        return item;
    }

    private static Attributes refSOP(String cuid, String iuid) {
        Attributes item = new Attributes(2);
        item.setString(Tag.ReferencedSOPClassUID, VR.UI, cuid);
        item.setString(Tag.ReferencedSOPInstanceUID, VR.UI, iuid);
        return item;
    }

    @Override
    public AttributesCoercion getAttributesCoercion(QueryContext ctx) {
        ArchiveAEExtension aeExt = ctx.getArchiveAEExtension();
        ArchiveAttributeCoercion rule = aeExt.findAttributeCoercion(
                Dimse.C_FIND_RSP,
                TransferCapability.Role.SCU,
                ctx.getSOPClassUID(),
                ctx.getRemoteHostName(),
                ctx.getCallingAET(),
                ctx.getLocalHostName(),
                ctx.getCalledAET(),
                ctx.getQueryKeys());
        if (rule == null)
            return null;

        AttributesCoercion coercion = null;
        String xsltStylesheetURI = rule.getXSLTStylesheetURI();
        if (xsltStylesheetURI != null)
            try {
                Templates tpls = TemplatesCache.getDefault().get(StringUtils.replaceSystemProperties(xsltStylesheetURI));
                coercion = new XSLTAttributesCoercion(tpls, null)
                        .includeKeyword(!rule.isNoKeywords())
                        .setupTransformer(setupTransformer(ctx));
            } catch (TransformerConfigurationException e) {
                LOG.error("{}: Failed to compile XSL: {}", ctx.getAssociation(), xsltStylesheetURI, e);
            }
        coercion = rule.mergeAttributes(coercion);
        coercion = NullifyAttributesCoercion.valueOf(rule.getNullifyTags(), coercion);
        String leadingCFindSCP = rule.getLeadingCFindSCP();
        if (leadingCFindSCP != null) {
            coercion = new CFindSCUAttributeCoercion(ctx.getLocalApplicationEntity(), leadingCFindSCP,
                    rule.getAttributeUpdatePolicy(), cfindscu, leadingCFindSCPQueryCache, coercion);
        }
        LOG.info("Coerce Attributes from rule: {}", rule);
        return coercion;
    }

    private SAXTransformer.SetupTransformer setupTransformer(QueryContext ctx) {
        return t -> {
            t.setParameter("LocalAET", ctx.getCalledAET());
            if (ctx.getCallingAET() != null)
                t.setParameter("RemoteAET", ctx.getCallingAET());
            if (ctx.getRemoteHostName() != null)
                t.setParameter("RemoteHost", ctx.getRemoteHostName());
        };
    }

    @Override
    public CFindSCU cfindSCU() {
        return cfindscu;
    }

    @Override
    public List<String> getDistinctModalities() {
        return em.createNamedQuery(Series.FIND_DISTINCT_MODALITIES, String.class)
                .getResultList();
    }

    @Override
    public List<Tuple> unknownSizeStudies(Date dt, int fetchSize) {
        return ejb.unknownSizeStudies(dt, fetchSize);
    }

    @Override
    public CriteriaQuery<Patient> createPatientWithUnknownIssuerQuery(QueryParam queryParam, Attributes queryKeys) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        QueryBuilder builder = new QueryBuilder(cb);
        CriteriaQuery<Patient> q = cb.createQuery(Patient.class);
        Root<Patient> patient = q.from(Patient.class);
        patient.join(Patient_.attributesBlob);
        patient.join(Patient_.patientID);

        IDWithIssuer idWithIssuer = IDWithIssuer.pidOf(queryKeys);
        List<Predicate> predicates = builder.patientPredicates(q, patient,
                idWithIssuer != null ? new IDWithIssuer[] { idWithIssuer } : IDWithIssuer.EMPTY,
                queryKeys,
                queryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));
        q.orderBy(builder.orderPatients(patient, Collections.singletonList(OrderByTag.asc(Tag.PatientID))));
        return q;
    }

    @Override
    public Date getLastModified(String studyUID, String seriesUID) {
        List<Object[]> dates = queryLastModified(studyUID, seriesUID);
        Date lastModified = null;
        for (Object[] objs : dates) {
            for (Object obj : objs) {
                Date date = (Date) obj;
                if (lastModified == null || lastModified.compareTo(date) < 0)
                    lastModified = date;
            }
        }
        return lastModified;
    }

    private List<Object[]> queryLastModified(String studyIUID, String seriesIUID) {
        return (seriesIUID != null
                ? em.createNamedQuery(Instance.FIND_LAST_MODIFIED_SERIES_LEVEL, Object[].class)
                    .setParameter(1, studyIUID)
                    .setParameter(2, seriesIUID)
                : em.createNamedQuery(Instance.FIND_LAST_MODIFIED_STUDY_LEVEL, Object[].class)
                    .setParameter(1, studyIUID))
                .getResultList();
    }
}
