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
 * Portions created by the Initial Developer are Copyright (C) 2017-2019
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


import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.dict.archive.PrivateTag;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.util.UIDUtils;
import org.dcm4chee.arc.code.CodeCache;
import org.dcm4chee.arc.conf.Availability;
import org.dcm4chee.arc.conf.QueryRetrieveView;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.query.util.QueryBuilder;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.*;
import javax.persistence.criteria.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Aug 2015
 */
@Stateless
public class QueryServiceEJB {

    @PersistenceContext(unitName = "dcm4chee-arc")
    EntityManager em;

    @Inject
    private CodeCache codeCache;

    @Inject
    QuerySizeEJB querySizeEJB;

    @Inject
    QueryAttributesEJB queryAttributesEJB;

    public Attributes getSeriesAttributes(Long seriesPk, QueryContext context) {
        QueryRetrieveView qrView = context.getQueryParam().getQueryRetrieveView();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        Root<Series> series = q.from(Series.class);
        Join<Series, Study> study = series.join(Series_.study);
        Join<Study, Patient> patient = study.join(Study_.patient);
        Join<Series, Metadata> metadata = series.join(Series_.metadata, JoinType.LEFT);
        String viewID = context.getQueryParam().getViewID();
        CollectionJoin<Study, StudyQueryAttributes> studyQueryAttributesPath =
                QueryBuilder.joinStudyQueryAttributes(cb, study, viewID);
        CollectionJoin<Series, SeriesQueryAttributes> seriesQueryAttributesPath =
                QueryBuilder.joinSeriesQueryAttributes(cb, series, viewID);
        Path<byte[]> seriesAttrBlob = series.join(Series_.attributesBlob).get(AttributesBlob_.encodedAttributes);
        Path<byte[]> studyAttrBlob = study.join(Study_.attributesBlob).get(AttributesBlob_.encodedAttributes);
        Path<byte[]> patAttrBlob = patient.join(Patient_.attributesBlob).get(AttributesBlob_.encodedAttributes);
        Tuple result;
        try {
            result = em.createQuery(q.multiselect(
                    study.get(Study_.pk),
                    patient.get(Patient_.numberOfStudies),
                    patient.get(Patient_.createdTime),
                    patient.get(Patient_.updatedTime),
                    patient.get(Patient_.verificationTime),
                    patient.get(Patient_.verificationStatus),
                    patient.get(Patient_.failedVerifications),
                    study.get(Study_.createdTime),
                    study.get(Study_.updatedTime),
                    study.get(Study_.accessTime),
                    study.get(Study_.expirationState),
                    study.get(Study_.expirationDate),
                    study.get(Study_.expirationExporterID),
                    study.get(Study_.rejectionState),
                    study.get(Study_.completeness),
                    study.get(Study_.failedRetrieves),
                    study.get(Study_.accessControlID),
                    study.get(Study_.storageIDs),
                    study.get(Study_.size),
                    series.get(Series_.createdTime),
                    series.get(Series_.updatedTime),
                    series.get(Series_.expirationState),
                    series.get(Series_.expirationDate),
                    series.get(Series_.expirationExporterID),
                    series.get(Series_.rejectionState),
                    series.get(Series_.completeness),
                    series.get(Series_.failedRetrieves),
                    series.get(Series_.sourceAET),
                    series.get(Series_.metadataScheduledUpdateTime),
                    series.get(Series_.metadataUpdateFailures),
                    series.get(Series_.instancePurgeTime),
                    series.get(Series_.instancePurgeState),
                    series.get(Series_.storageVerificationTime),
                    series.get(Series_.failuresOfLastStorageVerification),
                    series.get(Series_.compressionTime),
                    series.get(Series_.compressionFailures),
                    metadata.get(Metadata_.createdTime),
                    metadata.get(Metadata_.storageID),
                    metadata.get(Metadata_.storagePath),
                    metadata.get(Metadata_.digest),
                    metadata.get(Metadata_.size),
                    metadata.get(Metadata_.status),
                    seriesQueryAttributesPath.get(SeriesQueryAttributes_.numberOfInstances),
                    studyQueryAttributesPath.get(StudyQueryAttributes_.numberOfInstances),
                    studyQueryAttributesPath.get(StudyQueryAttributes_.numberOfSeries),
                    studyQueryAttributesPath.get(StudyQueryAttributes_.modalitiesInStudy),
                    studyQueryAttributesPath.get(StudyQueryAttributes_.sopClassesInStudy),
                    seriesAttrBlob,
                    studyAttrBlob,
                    patAttrBlob)
                    .where(cb.equal(series.get(Series_.pk), seriesPk))
            ).getSingleResult();
        } catch (NoResultException e) {
            return null;
        }

        Long studySize = result.get(study.get(Study_.size));
        if (studySize < 0)
            studySize = querySizeEJB.calculateStudySize(result.get(study.get(Study_.pk)));
        Integer numberOfSeriesRelatedInstances =
                result.get(seriesQueryAttributesPath.get(SeriesQueryAttributes_.numberOfInstances));
        if (numberOfSeriesRelatedInstances == null) {
            SeriesQueryAttributes seriesQueryAttributes =
                    queryAttributesEJB.calculateSeriesQueryAttributes(seriesPk, qrView);
            numberOfSeriesRelatedInstances = seriesQueryAttributes.getNumberOfInstances();
        }

        int numberOfStudyRelatedSeries;
        String modalitiesInStudy;
        String sopClassesInStudy;
        Integer numberOfStudyRelatedInstances =
                result.get(studyQueryAttributesPath.get(StudyQueryAttributes_.numberOfInstances));
        if (numberOfStudyRelatedInstances == null) {
            StudyQueryAttributes studyQueryAttributes =
                    queryAttributesEJB.calculateStudyQueryAttributes(result.get(study.get(Study_.pk)), qrView);
            numberOfStudyRelatedInstances = studyQueryAttributes.getNumberOfInstances();
            numberOfStudyRelatedSeries = studyQueryAttributes.getNumberOfSeries();
            modalitiesInStudy = studyQueryAttributes.getModalitiesInStudy();
            sopClassesInStudy = studyQueryAttributes.getSOPClassesInStudy();
        } else {
            numberOfStudyRelatedSeries =
                    result.get(studyQueryAttributesPath.get(StudyQueryAttributes_.numberOfSeries));
            modalitiesInStudy =
                    result.get(studyQueryAttributesPath.get(StudyQueryAttributes_.modalitiesInStudy));
            sopClassesInStudy =
                    result.get(studyQueryAttributesPath.get(StudyQueryAttributes_.sopClassesInStudy));
        }
        Attributes patAttrs = AttributesBlob.decodeAttributes(result.get(patAttrBlob), null);
        Attributes studyAttrs = AttributesBlob.decodeAttributes(result.get(studyAttrBlob), null);
        Attributes seriesAttrs = AttributesBlob.decodeAttributes(result.get(seriesAttrBlob), null);
        Attributes.unifyCharacterSets(patAttrs, studyAttrs, seriesAttrs);
        Attributes attrs = new Attributes(patAttrs.size() + studyAttrs.size() + seriesAttrs.size() + 20);
        attrs.addAll(patAttrs);
        attrs.addAll(studyAttrs, true);
        attrs.addAll(seriesAttrs, true);
        PatientQuery.addPatientQRAttrs(patient, context, result, attrs);
        StudyQuery.addStudyQRAddrs(study, context, result, studySize, numberOfStudyRelatedInstances,
                numberOfStudyRelatedSeries, modalitiesInStudy, sopClassesInStudy, attrs);
        SeriesQuery.addSeriesQRAttrs(series, metadata, context, result, numberOfSeriesRelatedInstances, attrs);
        return attrs;
    }

    public void addLocationAttributes(Attributes attrs, Long instancePk) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        Root<Location> location = q.from(Location.class);
        TypedQuery<Tuple> query = em.createQuery(q.multiselect(
                location.get(Location_.storageID),
                location.get(Location_.storagePath),
                location.get(Location_.transferSyntaxUID),
                location.get(Location_.digest),
                location.get(Location_.size),
                location.get(Location_.status)
        ).where(
                cb.equal(location.get(Location_.instance).get(Instance_.pk), instancePk),
                cb.equal(location.get(Location_.objectType), Location.ObjectType.DICOM_FILE)));

        try (Stream<Tuple> resultStream = query.getResultStream()) {
            Iterator<Tuple> iterate = resultStream.iterator();
            Attributes item = null;
            while (iterate.hasNext()) {
                Tuple results = iterate.next();
                if (item == null)
                    item = attrs;
                else
                    attrs.ensureSequence(PrivateTag.PrivateCreator, PrivateTag.OtherStorageSequence, 1)
                            .add(item = new Attributes(5));
                item.setString(PrivateTag.PrivateCreator, PrivateTag.StorageID, VR.LO,
                        results.get(location.get(Location_.storageID)));
                item.setString(PrivateTag.PrivateCreator, PrivateTag.StoragePath, VR.LO,
                        StringUtils.split(results.get(location.get(Location_.storagePath)), '/'));
                item.setString(PrivateTag.PrivateCreator, PrivateTag.StorageTransferSyntaxUID, VR.UI,
                        results.get(location.get(Location_.transferSyntaxUID)));
                item.setInt(PrivateTag.PrivateCreator, PrivateTag.StorageObjectSize, VR.UL,
                        results.get(location.get(Location_.size)).intValue());
                if (results.get(location.get(Location_.digest)) != null)
                    item.setString(PrivateTag.PrivateCreator, PrivateTag.StorageObjectDigest, VR.LO,
                            results.get(location.get(Location_.digest)));
                if (results.get(location.get(Location_.status)) != Location.Status.OK)
                    attrs.setString(PrivateTag.PrivateCreator, PrivateTag.StorageObjectStatus, VR.CS,
                            results.get(location.get(Location_.status)).name());
            }
        }
    }

    public Attributes queryStudyExportTaskInfo(String studyIUID, QueryRetrieveView qrView) {
        String viewID = qrView.getViewID();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        Root<Study> study = q.from(Study.class);
        CollectionJoin<Study, StudyQueryAttributes> studyQueryAttributesPath =
                QueryBuilder.joinStudyQueryAttributes(cb, study, viewID);
        Tuple result;
        try {
            result = em.createQuery(q
                    .multiselect(
                        study.get(Study_.pk),
                        studyQueryAttributesPath.get(StudyQueryAttributes_.numberOfInstances),
                        studyQueryAttributesPath.get(StudyQueryAttributes_.modalitiesInStudy))
                    .where(cb.equal(study.get(Study_.studyInstanceUID), studyIUID)))
                .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }

        String modalitiesInStudy;
        Integer numberOfStudyRelatedInstances =
                result.get(studyQueryAttributesPath.get(StudyQueryAttributes_.numberOfInstances));
        if (numberOfStudyRelatedInstances == null) {
            StudyQueryAttributes studyQueryAttributes =
                    queryAttributesEJB.calculateStudyQueryAttributes(result.get(study.get(Study_.pk)), qrView);
            numberOfStudyRelatedInstances = studyQueryAttributes.getNumberOfInstances();
            modalitiesInStudy = studyQueryAttributes.getModalitiesInStudy();
        } else {
            modalitiesInStudy =
                    result.get(studyQueryAttributesPath.get(StudyQueryAttributes_.modalitiesInStudy));
        }
        Attributes attrs = new Attributes(2);
        attrs.setInt(Tag.NumberOfStudyRelatedInstances, VR.IS, numberOfStudyRelatedInstances);
        attrs.setString(Tag.ModalitiesInStudy, VR.CS, modalitiesInStudy);
        return attrs;
    }

    public Attributes querySeriesExportTaskInfo(String studyIUID, String seriesIUID, QueryRetrieveView qrView) {
        String viewID = qrView.getViewID();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        Root<Series> series = q.from(Series.class);
        Join<Series, Study> study = series.join(Series_.study);
        CollectionJoin<Series, SeriesQueryAttributes> seriesQueryAttributesPath =
                QueryBuilder.joinSeriesQueryAttributes(cb, series, viewID);
        Tuple result;
        try {
            result = em.createQuery(q
                .multiselect(
                    series.get(Series_.pk),
                    series.get(Series_.modality),
                    seriesQueryAttributesPath.get(SeriesQueryAttributes_.numberOfInstances))
                .where(
                    cb.equal(study.get(Study_.studyInstanceUID), studyIUID),
                    cb.equal(series.get(Series_.seriesInstanceUID), seriesIUID)))
            .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }

        Integer numberOfSeriesRelatedInstances =
                result.get(seriesQueryAttributesPath.get(SeriesQueryAttributes_.numberOfInstances));
        if (numberOfSeriesRelatedInstances == null) {
            Long seriesPk = result.get(series.get(Series_.pk));
            SeriesQueryAttributes seriesQueryAttributes =
                    queryAttributesEJB.calculateSeriesQueryAttributes(seriesPk, qrView);
            numberOfSeriesRelatedInstances = seriesQueryAttributes.getNumberOfInstances();
        }
        Attributes attrs = new Attributes(2);
        attrs.setInt(Tag.NumberOfStudyRelatedInstances, VR.IS, numberOfSeriesRelatedInstances);
        attrs.setString(Tag.ModalitiesInStudy, VR.CS, result.get(series.get(Series_.modality)));
        return attrs;
    }

    public enum SOPInstanceRefsType { IAN, KOS_IOCM, KOS_XDSI, STGCMT }

    public Attributes getStudyAttributesWithSOPInstanceRefs(
            SOPInstanceRefsType type, String studyIUID, String seriesIUID, String objectUID, QueryRetrieveView qrView,
            Collection<Attributes> seriesAttrs, String[] retrieveAETs, String retrieveLocationUID) {
        Attributes attrs = getStudyAttributes(studyIUID);
        if (attrs == null)
            return null;

        Attributes sopInstanceRefs = getSOPInstanceRefs(type, studyIUID, seriesIUID, objectUID, qrView,
                seriesAttrs, retrieveAETs, retrieveLocationUID, null);
        if (sopInstanceRefs != null)
            attrs.newSequence(Tag.CurrentRequestedProcedureEvidenceSequence, 1).add(sopInstanceRefs);
        return attrs;
    }

    public Attributes getSOPInstanceRefs(
            SOPInstanceRefsType type, String studyIUID, String seriesUID, String objectUID, QueryRetrieveView qrView,
            Collection<Attributes> seriesAttrs, String[] retrieveAETs, String retrieveLocationUID,
            Availability availability) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        Root<Instance> instance = q.from(Instance.class);
        Join<Instance, Series> series = instance.join(Instance_.series);
        Join<Series, Study> study = series.join(Series_.study);
        List<Tuple> tuples = em.createQuery(
                restrict(new QueryBuilder(cb), q, study, series, instance, studyIUID, seriesUID, objectUID, qrView)
                .multiselect(
                        study.get(Study_.pk),
                        series.get(Series_.pk),
                        series.get(Series_.seriesInstanceUID),
                        instance.get(Instance_.sopInstanceUID),
                        instance.get(Instance_.sopClassUID),
                        instance.get(Instance_.retrieveAETs),
                        instance.get(Instance_.availability)))
                .getResultList();

        if (tuples.isEmpty() && type != SOPInstanceRefsType.KOS_XDSI)
            return null;

        if (type == SOPInstanceRefsType.STGCMT)
            return getStgCmtRqstAttr(tuples, instance);

        Attributes refStudy = new Attributes(2);
        Sequence refSeriesSeq = refStudy.newSequence(Tag.ReferencedSeriesSequence, 10);
        refStudy.setString(Tag.StudyInstanceUID, VR.UI, studyIUID);
        HashMap<Long, Sequence> seriesMap = new HashMap<>();
        for (Tuple tuple : tuples) {
            Long seriesPk = tuple.get(series.get(Series_.pk));
            Sequence refSOPSeq = seriesMap.get(seriesPk);
            if (refSOPSeq == null) {
                Attributes refSeries = new Attributes(4);
                if (type == SOPInstanceRefsType.KOS_XDSI) {
                    if (retrieveAETs != null)
                        refSeries.setString(Tag.RetrieveAETitle, VR.AE, retrieveAETs);
                    if (retrieveLocationUID != null)
                        refSeries.setString(Tag.RetrieveLocationUID, VR.UI, retrieveLocationUID);
                }
                refSOPSeq = refSeries.newSequence(Tag.ReferencedSOPSequence, 10);
                refSeries.setString(Tag.SeriesInstanceUID, VR.UI, tuple.get(series.get(Series_.seriesInstanceUID)));
                seriesMap.put(seriesPk, refSOPSeq);
                refSeriesSeq.add(refSeries);
                if (seriesAttrs != null)
                    seriesAttrs.add(getSeriesAttributes(seriesPk));
            }
            Attributes refSOP = new Attributes(4);
            if (type == SOPInstanceRefsType.IAN) {
                refSOP.setString(Tag.RetrieveAETitle, VR.AE, StringUtils.maskNull(
                        retrieveAETs, StringUtils.split(tuple.get(instance.get(Instance_.retrieveAETs)), '\\')));
                refSOP.setString(Tag.InstanceAvailability, VR.CS,
                        StringUtils.maskNull(availability, tuple.get(instance.get(Instance_.availability))).toString());
                if (retrieveLocationUID != null)
                    refSOP.setString(Tag.RetrieveLocationUID, VR.UI, retrieveLocationUID);
            }
            refSOP.setString(Tag.ReferencedSOPClassUID, VR.UI,
                    tuple.get(instance.get(Instance_.sopClassUID)));
            refSOP.setString(Tag.ReferencedSOPInstanceUID, VR.UI,
                    tuple.get(instance.get(Instance_.sopInstanceUID)));
            refSOPSeq.add(refSOP);
        }
        if (type == SOPInstanceRefsType.IAN)
            refStudy.setNull(Tag.ReferencedPerformedProcedureStepSequence, VR.SQ);
        return refStudy;
    }

    private CriteriaQuery<Tuple> restrict(QueryBuilder builder, CriteriaQuery<Tuple> q, Join<Series, Study> study,
            Join<Instance, Series> series, Root<Instance> instance,
            String studyIUID, String seriesUID, String objectUID, QueryRetrieveView qrView) {
        return q.where(
                builder.sopInstanceRefs(q, study, series, instance, studyIUID, seriesUID, objectUID, qrView,
                    codeCache.findOrCreateEntities(qrView.getShowInstancesRejectedByCodes()),
                    codeCache.findOrCreateEntities(qrView.getHideRejectionNotesWithCodes()))
                .toArray(new Predicate[0]));
    }

    private Attributes getStgCmtRqstAttr(List<Tuple> tuples, Path<Instance> instance) {
        Attributes refStgcmt = new Attributes(2);
        refStgcmt.setString(Tag.TransactionUID, VR.UI, UIDUtils.createUID());
        Sequence refSOPSeq = refStgcmt.newSequence(Tag.ReferencedSOPSequence, 10);
        for (Tuple tuple : tuples) {
            Attributes refSOP = new Attributes(2);
            refSOP.setString(Tag.ReferencedSOPClassUID, VR.UI,
                    tuple.get(instance.get(Instance_.sopClassUID)));
            refSOP.setString(Tag.ReferencedSOPInstanceUID, VR.UI,
                    tuple.get(instance.get(Instance_.sopInstanceUID)));
            refSOPSeq.add(refSOP);
        }
        return refStgcmt;
    }

    public Attributes getStudyAttributes(String studyInstanceUID) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        Root<Study> study = q.from(Study.class);
        Join<Study, Patient> patient = study.join(Study_.patient);
        Path<byte[]> studyAttrBlob = study.join(Study_.attributesBlob).get(AttributesBlob_.encodedAttributes);
        Path<byte[]> patAttrBlob = patient.join(Patient_.attributesBlob).get(AttributesBlob_.encodedAttributes);
        Tuple result;
        try {
            result = em.createQuery(q
                        .multiselect(studyAttrBlob, patAttrBlob)
                        .where(cb.equal(study.get(Study_.studyInstanceUID), studyInstanceUID)))
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
        Attributes patAttrs = AttributesBlob.decodeAttributes(result.get(patAttrBlob), null);
        Attributes studyAttrs = AttributesBlob.decodeAttributes(result.get(studyAttrBlob), null);
        Attributes.unifyCharacterSets(patAttrs, studyAttrs);
        Attributes attrs = new Attributes(patAttrs.size() + studyAttrs.size());
        attrs.addAll(patAttrs);
        attrs.addAll(studyAttrs, true);
        return attrs;
    }

    private Attributes getSeriesAttributes(Long seriesPk) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<byte[]> q = cb.createQuery(byte[].class);
        Root<Series> series = q.from(Series.class);
        Path<byte[]> seriesAttrBlob = series.join(Series_.attributesBlob).get(AttributesBlob_.encodedAttributes);
        return AttributesBlob.decodeAttributes(
                em.createQuery(q
                    .select(seriesAttrBlob)
                    .where(cb.equal(series.get(Series_.pk), seriesPk)))
                .getSingleResult(),
            null);
    }

    public SeriesQueryAttributes calculateSeriesQueryAttributesIfNotExists(Long seriesPk, QueryRetrieveView qrView) {
        try {
            return em.createNamedQuery(
                    SeriesQueryAttributes.FIND_BY_VIEW_ID_AND_SERIES_PK, SeriesQueryAttributes.class)
                    .setParameter(1, qrView.getViewID())
                    .setParameter(2, seriesPk)
                    .getSingleResult();
        } catch (NoResultException e) {
            return queryAttributesEJB.calculateSeriesQueryAttributes(seriesPk, qrView);
        }
    }

}
