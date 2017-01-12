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

import com.querydsl.core.BooleanBuilder;
import org.dcm4che3.data.*;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.QueryOption;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.util.UIDUtils;
import org.dcm4chee.arc.code.CodeCache;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.Availability;
import org.dcm4chee.arc.conf.QueryRetrieveView;
import org.dcm4chee.arc.conf.RejectionNote;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.query.Query;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.query.util.QueryBuilder;
import org.dcm4chee.arc.query.util.QueryParam;
import org.dcm4chee.arc.storage.ReadContext;
import org.dcm4chee.arc.storage.Storage;
import org.dcm4chee.arc.storage.StorageFactory;
import org.hibernate.Session;
import org.hibernate.StatelessSession;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.servlet.http.HttpServletRequest;
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

    @PersistenceContext(unitName = "dcm4chee-arc")
    private EntityManager em;

    @Inject
    private QueryServiceEJB ejb;

    @Inject
    private CodeCache codeCache;

    @Inject
    private StorageFactory storageFactory;

    @Inject
    private Event<QueryContext> queryEvent;

    StatelessSession openStatelessSession() {
        return em.unwrap(Session.class).getSessionFactory().openStatelessSession();
    }

    @Override
    public QueryContext newQueryContextFIND(Association as, String sopClassUID, EnumSet<QueryOption> queryOpts) {
        ApplicationEntity ae = as.getApplicationEntity();
        QueryParam queryParam = new QueryParam(ae);
        queryParam.setCombinedDatetimeMatching(queryOpts.contains(QueryOption.DATETIME));
        queryParam.setFuzzySemanticMatching(queryOpts.contains(QueryOption.FUZZY));
        return new QueryContextImpl(as, sopClassUID, ae, initCodeEntities(queryParam), this);
    }

    @Override
    public QueryContext newQueryContextQIDO(
            HttpServletRequest httpRequest, String searchMethod, ApplicationEntity ae, QueryParam queryParam) {
        return new QueryContextImpl(httpRequest, searchMethod, ae, initCodeEntities(queryParam), this);
    }

    private QueryParam initCodeEntities(QueryParam param) {
        QueryRetrieveView qrView = param.getQueryRetrieveView();
        param.setHideRejectionNotesWithCode(
                codeCache.findOrCreateEntities(qrView.getHideRejectionNotesWithCodes()));
        param.setShowInstancesRejectedByCode(
                codeCache.findOrCreateEntities(qrView.getShowInstancesRejectedByCodes()));
        return param;
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
        return new PatientQuery(ctx, openStatelessSession());
    }

    @Override
    public Query createStudyQuery(QueryContext ctx) {
        return new StudyQuery(ctx, openStatelessSession());
    }

    @Override
    public Query createSeriesQuery(QueryContext ctx) {
        return new SeriesQuery(ctx, openStatelessSession());
    }

    @Override
    public Query createInstanceQuery(QueryContext ctx) {
        return new InstanceQuery(ctx, openStatelessSession());
    }

    @Override
    public Query createMWLQuery(QueryContext ctx) {
        queryEvent.fire(ctx);
        return new MWLQuery(ctx, openStatelessSession());
    }

    @Override
    public Attributes getSeriesAttributes(Long seriesPk, QueryParam queryParam) {
        return ejb.getSeriesAttributes(seriesPk, queryParam);
    }

    @Override
    public StudyQueryAttributes calculateStudyQueryAttributes(Long studyPk, QueryParam queryParam) {
        return ejb.calculateStudyQueryAttributes(studyPk, queryParam);
    }

    @Override
    public SeriesQueryAttributes calculateSeriesQueryAttributesIfNotExists(Long seriesPk, QueryParam queryParam) {
        return ejb.calculateSeriesQueryAttributesIfNotExists(seriesPk, queryParam);
    }

    @Override
    public SeriesQueryAttributes calculateSeriesQueryAttributes(Long seriesPk, QueryRetrieveView qrView) {
        return ejb.calculateSeriesQueryAttributes(seriesPk, qrView,
                codeCache.findOrCreateEntities(qrView.getHideRejectionNotesWithCodes()),
                codeCache.findOrCreateEntities(qrView.getShowInstancesRejectedByCodes()));
    }

    @Override
    public Attributes getStudyAttributesWithSOPInstanceRefs(
            String studyUID, ApplicationEntity ae, Collection<Attributes> seriesAttrs) {
        SOPInstanceRefsPredicateBuilder builder = new SOPInstanceRefsPredicateBuilder(studyUID);
        return ejb.getStudyAttributesWithSOPInstanceRefs(
                QueryServiceEJB.SOPInstanceRefsType.KOS_XDSI, studyUID, builder.build(ae), seriesAttrs);
    }

    @Override
    public Attributes createIAN(ApplicationEntity ae, String studyUID, String seriesUID,
                                Availability availability, String... retrieveAETs) {
        SOPInstanceRefsPredicateBuilder builder = new SOPInstanceRefsPredicateBuilder(studyUID);
        if (seriesUID != null)
            builder.setSeriesInstanceUID(seriesUID);
        return ejb.getSOPInstanceRefs(
                QueryServiceEJB.SOPInstanceRefsType.IAN, studyUID, builder.build(ae), null,
                retrieveAETs.length > 0 ? StringUtils.concat(retrieveAETs, '\\') : null, availability);
    }

    @Override
    public Attributes createActionInfo(String studyIUID, String seriesIUID, String sopIUID, ApplicationEntity ae) {
        SOPInstanceRefsPredicateBuilder builder = new SOPInstanceRefsPredicateBuilder(studyIUID);
        if (seriesIUID != null && !seriesIUID.equals("*"))
            builder.setSeriesInstanceUID(seriesIUID);
        if (sopIUID != null && !sopIUID.equals("*"))
            builder.setSOPInstanceUID(sopIUID);
        return ejb.getSOPInstanceRefs(QueryServiceEJB.SOPInstanceRefsType.STGCMT, studyIUID, builder.build(ae), null, null, null);
    }

    @Override
    public Attributes createRejectionNote(
            ApplicationEntity ae, String studyUID, String seriesUID, String objectUID, RejectionNote rjNote) {
        SOPInstanceRefsPredicateBuilder builder = new SOPInstanceRefsPredicateBuilder(studyUID);
        if (seriesUID != null) {
            builder.setSeriesInstanceUID(seriesUID);
            if (objectUID != null)
                builder.setSOPInstanceUID(objectUID);
        }
        Attributes attrs = ejb.getStudyAttributesWithSOPInstanceRefs(
                QueryServiceEJB.SOPInstanceRefsType.KOS_IOCM, studyUID, builder.build(ae), null);
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

    private void mkKOS(Attributes attrs, RejectionNote rjNote) {
        Attributes studyRef =  attrs.getNestedDataset(Tag.CurrentRequestedProcedureEvidenceSequence);
        attrs.setString(Tag.SOPClassUID, VR.UI, UID.KeyObjectSelectionDocumentStorage);
        attrs.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
        attrs.setDate(Tag.ContentDateAndTime, new Date());
        attrs.setString(Tag.Modality, VR.CS, "KO");
        attrs.setNull(Tag.ReferencedPerformedProcedureStepSequence, VR.SQ);
        attrs.setString(Tag.SeriesInstanceUID, VR.UI, UIDUtils.createUID());
        attrs.setInt(Tag.SeriesNumber, VR.IS, rjNote.getSeriesNumber());
        attrs.setInt(Tag.InstanceNumber, VR.IS, rjNote.getInstanceNumber());
        attrs.setString(Tag.ValueType, VR.CS, "CONTAINER");
        attrs.setString(Tag.ContinuityOfContent, VR.CS, "SEPARATE");
        attrs.newSequence(Tag.ConceptNameCodeSequence, 1).add(rjNote.getRejectionNoteCode().toItem());
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
        return "COMPOSITE";
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

    private class SOPInstanceRefsPredicateBuilder {
        private final BooleanBuilder predicate;

        private SOPInstanceRefsPredicateBuilder(String studyUID) {
            predicate = new BooleanBuilder(QStudy.study.studyInstanceUID.eq(studyUID));
        }

        public void setSeriesInstanceUID(String seriesUID) {
            predicate.and(QSeries.series.seriesInstanceUID.eq(seriesUID));
        }

        public void setSOPInstanceUID(String objectUID) {
            predicate.and(QInstance.instance.sopInstanceUID.eq(objectUID));
        }

        public BooleanBuilder build(ApplicationEntity ae) {
            QueryParam queryParam = initCodeEntities(new QueryParam(ae));
            predicate.and(QueryBuilder.hideRejectedInstance(queryParam));
            predicate.and(QueryBuilder.hideRejectionNote(queryParam));
            return predicate;
        }
    }
}
