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
import org.dcm4che3.net.*;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.util.UIDUtils;
import org.dcm4chee.arc.LeadingCFindSCPQueryCache;
import org.dcm4chee.arc.code.CodeCache;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.entity.Instance;
import org.dcm4chee.arc.entity.Series;
import org.dcm4chee.arc.entity.SeriesQueryAttributes;
import org.dcm4chee.arc.entity.StudyQueryAttributes;
import org.dcm4chee.arc.query.Query;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.query.scu.CFindSCU;
import org.dcm4chee.arc.query.scu.CFindSCUAttributeCoercion;
import org.dcm4chee.arc.query.util.QueryParam;
import org.dcm4chee.arc.storage.ReadContext;
import org.dcm4chee.arc.storage.Storage;
import org.dcm4chee.arc.storage.StorageFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.servlet.http.HttpServletRequest;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
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
        return new QueryContextImpl(ae, queryParam, this).find(as, sopClassUID);
    }

    @Override
    public QueryContext newQueryContextQIDO(
            HttpServletRequest httpRequest, String searchMethod, ApplicationEntity ae, QueryParam queryParam) {
        return new QueryContextImpl(ae, queryParam, this).qido(httpRequest, searchMethod);
    }

    @Override
    public QueryContext newQueryContext(ApplicationEntity ae, QueryParam queryParam) {
        return new QueryContextImpl(ae, queryParam, this);
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
    public Attributes createIAN(ApplicationEntity ae, String studyUID, String seriesUID,
                                String[] retrieveAETs, String retrieveLocationUID, Availability availability) {
        QueryRetrieveView qrView = ae.getAEExtensionNotNull(ArchiveAEExtension.class).getQueryRetrieveView();
        return ejb.getSOPInstanceRefs(
                QueryServiceEJB.SOPInstanceRefsType.IAN, studyUID, seriesUID, null, qrView, null,
                retrieveAETs, retrieveLocationUID, availability);
    }

    @Override
    public Attributes createIAN(ApplicationEntity ae, String studyUID, String seriesUID, String sopUID) {
        QueryRetrieveView qrView = ae.getAEExtensionNotNull(ArchiveAEExtension.class).getQueryRetrieveView();
        return ejb.getSOPInstanceRefs(
                QueryServiceEJB.SOPInstanceRefsType.IAN, studyUID, seriesUID, sopUID, qrView, null,
                null, null, null);
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
    public Attributes createActionInfo(String studyIUID, String seriesIUID, String sopIUID, ApplicationEntity ae) {
        QueryRetrieveView qrView = ae.getAEExtensionNotNull(ArchiveAEExtension.class).getQueryRetrieveView();
        return ejb.getSOPInstanceRefs(QueryServiceEJB.SOPInstanceRefsType.STGCMT, studyIUID, seriesIUID, sopIUID, qrView,
                null, null, null, null);
    }

    @Override
    public Attributes queryExportTaskInfo(String studyIUID, String seriesIUID, String sopIUID, ApplicationEntity ae) {
        QueryRetrieveView qrView = ae.getAEExtensionNotNull(ArchiveAEExtension.class).getQueryRetrieveView();
        if (seriesIUID == null || seriesIUID.equals("*"))
            return ejb.queryStudyExportTaskInfo(studyIUID, qrView);
        Attributes attrs = ejb.querySeriesExportTaskInfo(studyIUID, seriesIUID, qrView);
        if (sopIUID != null && !sopIUID.equals("*"))
            attrs.setInt(Tag.NumberOfStudyRelatedInstances, VR.IS, 1);
        return attrs;
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
    public ZipInputStream openZipInputStream(QueryContext ctx, String storageID, String storagePath)
            throws IOException {
        Storage storage = getStorage(storageID, ctx);
        return new ZipInputStream(storage.openInputStream(
                createReadContext(storage, storagePath, ctx.getQueryKeys().getString(Tag.StudyInstanceUID))));
    }

    private Storage getStorage(String storageID, QueryContext ctx) {
        Storage storage = ctx.getStorage(storageID);
        if (storage == null) {
            ArchiveDeviceExtension arcDev = ctx.getArchiveAEExtension().getArchiveDeviceExtension();
            storage = storageFactory.getStorage(arcDev.getStorageDescriptorNotNull(storageID));
            ctx.putStorage(storageID, storage);
        }
        return storage;
    }

    private ReadContext createReadContext(Storage storage, String storagePath, String studyInstanceUID) {
        ReadContext readContext = storage.createReadContext();
        readContext.setStoragePath(storagePath);
        readContext.setStudyInstanceUID(studyInstanceUID);
        return readContext;
    }

    private void mkKOS(Attributes attrs, Code conceptNameCode, int seriesNumber, int instanceNumber) {
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

    private String typeOf(String cuid) {
        String uidName = UID.nameOf(cuid);
        int index = uidName.lastIndexOf(" Storage");
        return uidName.startsWith("Image", index - 5) ? "IMAGE"
            : uidName.startsWith("Waveform", index - 8) ? "WAVEFORM"
            : "COMPOSITE";
    }

    private Attributes templateIdentifier() {
        Attributes attrs = new Attributes(2);
        attrs.setString(Tag.MappingResource, VR.CS, "DCMR");
        attrs.setString(Tag.TemplateIdentifier, VR.CS, "2010");
        return attrs;
    }

    private Attributes contentItem(String valueType, Attributes refSOP) {
        Attributes item = new Attributes(3);
        item.setString(Tag.RelationshipType, VR.CS, "CONTAINS");
        item.setString(Tag.ValueType, VR.CS, valueType);
        item.newSequence(Tag.ReferencedSOPSequence, 1).add(refSOP);
        return item;
    }

    private Attributes refSOP(String cuid, String iuid) {
        Attributes item = new Attributes(2);
        item.setString(Tag.ReferencedSOPClassUID, VR.UI, cuid);
        item.setString(Tag.ReferencedSOPInstanceUID, VR.UI, iuid);
        return item;
    }

    @Override
    public AttributesCoercion getAttributesCoercion(QueryContext ctx) {
        ArchiveAEExtension aeExt = ctx.getArchiveAEExtension();
        ArchiveAttributeCoercion rule = aeExt.findAttributeCoercion(
                ctx.getRemoteHostName(), ctx.getCallingAET(), TransferCapability.Role.SCU, Dimse.C_FIND_RSP,
                ctx.getSOPClassUID());
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
}
