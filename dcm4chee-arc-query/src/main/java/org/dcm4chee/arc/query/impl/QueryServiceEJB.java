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


import com.mysema.commons.lang.CloseableIterator;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Predicate;
import com.querydsl.jpa.hibernate.HibernateQuery;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.dict.archive.ArchiveTag;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.Availability;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.query.util.QueryBuilder;
import org.dcm4chee.arc.query.util.QueryParam;
import org.hibernate.Session;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 *
 */
@Stateless
public class QueryServiceEJB {

    static final Expression<?>[] PATIENT_STUDY_SERIES_ATTRS = {
        QStudy.study.pk,
        QPatient.patient.numberOfStudies,
        QPatient.patient.createdTime,
        QPatient.patient.updatedTime,
        QStudy.study.createdTime,
        QStudy.study.updatedTime,
        QStudy.study.accessTime,
        QStudy.study.expirationDate,
        QStudy.study.rejectionState,
        QStudy.study.failedSOPInstanceUIDList,
        QStudy.study.failedRetrieves,
        QStudy.study.accessControlID,
        QStudy.study.storageIDs,
        QSeries.series.createdTime,
        QSeries.series.updatedTime,
        QSeries.series.expirationDate,
        QSeries.series.rejectionState,
        QSeries.series.failedSOPInstanceUIDList,
        QSeries.series.failedRetrieves,
        QSeries.series.sourceAET,
        QSeriesQueryAttributes.seriesQueryAttributes.numberOfInstances,
        QStudyQueryAttributes.studyQueryAttributes.numberOfInstances,
        QStudyQueryAttributes.studyQueryAttributes.numberOfSeries,
        QStudyQueryAttributes.studyQueryAttributes.modalitiesInStudy,
        QStudyQueryAttributes.studyQueryAttributes.sopClassesInStudy,
        QueryBuilder.seriesAttributesBlob.encodedAttributes,
        QueryBuilder.studyAttributesBlob.encodedAttributes,
        QueryBuilder.patientAttributesBlob.encodedAttributes
    };

    static final Expression<?>[] CALC_STUDY_QUERY_ATTRS = {
        QSeries.series.pk,
        QSeries.series.modality,
        QInstance.instance.sopClassUID,
        QInstance.instance.retrieveAETs,
        QInstance.instance.availability
    };

    static final Expression<?>[] CALC_SERIES_QUERY_ATTRS = {
        QInstance.instance.retrieveAETs,
        QInstance.instance.availability
    };

    static final Expression<?>[] SOP_REFS_OF_STUDY = {
            QStudy.study.pk,
            QSeries.series.pk,
            QSeries.series.seriesInstanceUID,
            QInstance.instance.sopInstanceUID,
            QInstance.instance.sopClassUID,
            QInstance.instance.availability
    };

    static final Expression<?>[] PATIENT_STUDY_ATTRS = {
            QueryBuilder.studyAttributesBlob.encodedAttributes,
            QueryBuilder.patientAttributesBlob.encodedAttributes
    };

    @PersistenceContext(unitName = "dcm4chee-arc")
    EntityManager em;

    public Attributes getSeriesAttributes(Long seriesPk, QueryParam queryParam) {
        String viewID = queryParam.getViewID();
        Tuple result = new HibernateQuery<Void>(em.unwrap(Session.class))
                .select(PATIENT_STUDY_SERIES_ATTRS)
                .from(QSeries.series)
                .join(QSeries.series.attributesBlob, QueryBuilder.seriesAttributesBlob)
                .leftJoin(QSeries.series.queryAttributes, QSeriesQueryAttributes.seriesQueryAttributes)
                .on(QSeriesQueryAttributes.seriesQueryAttributes.viewID.eq(viewID))
                .join(QSeries.series.study, QStudy.study)
                .join(QStudy.study.attributesBlob, QueryBuilder.studyAttributesBlob)
                .leftJoin(QStudy.study.queryAttributes, QStudyQueryAttributes.studyQueryAttributes)
                .on(QStudyQueryAttributes.studyQueryAttributes.viewID.eq(viewID))
                .join(QStudy.study.patient, QPatient.patient)
                .join(QPatient.patient.attributesBlob, QueryBuilder.patientAttributesBlob)
                .where(QSeries.series.pk.eq(seriesPk))
                .fetchOne();

        Integer numberOfSeriesRelatedInstances =
                result.get(QSeriesQueryAttributes.seriesQueryAttributes.numberOfInstances);
        if (numberOfSeriesRelatedInstances == null) {
            SeriesQueryAttributes seriesQueryAttributes =
                    calculateSeriesQueryAttributes(seriesPk, queryParam);
            numberOfSeriesRelatedInstances = seriesQueryAttributes.getNumberOfInstances();
        }

        int numberOfStudyRelatedSeries;
        String modalitiesInStudy;
        String sopClassesInStudy;
        Integer numberOfStudyRelatedInstances =
                result.get(QStudyQueryAttributes.studyQueryAttributes.numberOfInstances);
        if (numberOfStudyRelatedInstances == null) {
            StudyQueryAttributes studyQueryAttributes =
                    calculateStudyQueryAttributes(result.get(QStudy.study.pk), queryParam);
            numberOfStudyRelatedInstances = studyQueryAttributes.getNumberOfInstances();
            numberOfStudyRelatedSeries = studyQueryAttributes.getNumberOfSeries();
            modalitiesInStudy = studyQueryAttributes.getRawModalitiesInStudy();
            sopClassesInStudy = studyQueryAttributes.getRawSOPClassesInStudy();
        } else {
            numberOfStudyRelatedSeries =
                    result.get(QStudyQueryAttributes.studyQueryAttributes.numberOfSeries);
            modalitiesInStudy = 
                    result.get(QStudyQueryAttributes.studyQueryAttributes.modalitiesInStudy);
            sopClassesInStudy = 
                    result.get(QStudyQueryAttributes.studyQueryAttributes.sopClassesInStudy);
        }
        Attributes patAttrs = AttributesBlob.decodeAttributes(
                result.get(QueryBuilder.patientAttributesBlob.encodedAttributes), null);
        Attributes studyAttrs = AttributesBlob.decodeAttributes(
                result.get(QueryBuilder.studyAttributesBlob.encodedAttributes), null);
        Attributes seriesAttrs = AttributesBlob.decodeAttributes(
                result.get(QueryBuilder.seriesAttributesBlob.encodedAttributes), null);
        Attributes.unifyCharacterSets(patAttrs, studyAttrs, seriesAttrs);
        Attributes attrs = new Attributes(patAttrs.size() + studyAttrs.size() + seriesAttrs.size() + 5);
        attrs.addAll(patAttrs);
        attrs.addAll(studyAttrs);
        attrs.addAll(seriesAttrs);
        attrs.setString(Tag.ModalitiesInStudy, VR.CS, modalitiesInStudy);
        attrs.setString(Tag.SOPClassesInStudy, VR.UI, sopClassesInStudy);
        attrs.setInt(Tag.NumberOfPatientRelatedStudies, VR.IS, result.get(QPatient.patient.numberOfStudies));
        attrs.setInt(Tag.NumberOfStudyRelatedSeries, VR.IS, numberOfStudyRelatedSeries);
        attrs.setInt(Tag.NumberOfStudyRelatedInstances, VR.IS, numberOfStudyRelatedInstances);
        attrs.setInt(Tag.NumberOfSeriesRelatedInstances, VR.IS, numberOfSeriesRelatedInstances);
        attrs.setDate(ArchiveTag.PrivateCreator, ArchiveTag.PatientCreateDateTime, VR.DT,
                result.get(QPatient.patient.createdTime));
        attrs.setDate(ArchiveTag.PrivateCreator, ArchiveTag.PatientUpdateDateTime, VR.DT,
                result.get(QPatient.patient.updatedTime));
        attrs.setDate(ArchiveTag.PrivateCreator, ArchiveTag.StudyReceiveDateTime, VR.DT,
                result.get(QStudy.study.createdTime));
        attrs.setDate(ArchiveTag.PrivateCreator, ArchiveTag.StudyUpdateDateTime, VR.DT,
                result.get(QStudy.study.updatedTime));
        attrs.setDate(ArchiveTag.PrivateCreator, ArchiveTag.StudyAccessDateTime, VR.DT,
                result.get(QStudy.study.accessTime));
        if (result.get(QStudy.study.expirationDate) != null)
            attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.StudyExpirationDate, VR.DA,
                    result.get(QStudy.study.expirationDate));
        attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.StudyRejectionState, VR.CS,
                result.get(QStudy.study.rejectionState).toString());
        if (result.get(QStudy.study.failedSOPInstanceUIDList) != null)
            attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.MissingSOPInstanceUIDListOfStudy, VR.UI,
                    result.get(QStudy.study.failedSOPInstanceUIDList));
        attrs.setInt(ArchiveTag.PrivateCreator, ArchiveTag.FailedRetrievesOfStudy, VR.US,
                result.get(QStudy.study.failedRetrieves));
        attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.StudyAccessControlID, VR.LO,
                result.get(QStudy.study.accessControlID));
        attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.StorageIDsOfStudy, VR.LO,
                result.get(QStudy.study.storageIDs));
        attrs.setDate(ArchiveTag.PrivateCreator, ArchiveTag.SeriesReceiveDateTime, VR.DT,
                result.get(QSeries.series.createdTime));
        attrs.setDate(ArchiveTag.PrivateCreator, ArchiveTag.SeriesUpdateDateTime, VR.DT,
                result.get(QSeries.series.updatedTime));
        if (result.get(QSeries.series.expirationDate) != null)
            attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.SeriesExpirationDate, VR.DA,
                    result.get(QSeries.series.expirationDate));
        attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.SeriesRejectionState, VR.CS,
                result.get(QSeries.series.rejectionState).toString());
        if (result.get(QSeries.series.failedSOPInstanceUIDList) != null)
            attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.MissingSOPInstanceUIDListOfSeries, VR.UI,
                    result.get(QSeries.series.failedSOPInstanceUIDList));
        attrs.setInt(ArchiveTag.PrivateCreator, ArchiveTag.FailedRetrievesOfSeries, VR.US,
                result.get(QSeries.series.failedRetrieves));
        attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.SendingApplicationEntityOfSeries, VR.AE,
                result.get(QSeries.series.sourceAET));
        return attrs;
    }

    public Attributes getStudyAttributesWithSOPInstanceRefs(
            String studyUID, String seriesUID, String objectUID, QueryParam queryParam,
            Collection<Attributes> seriesAttrs, boolean availability) {
        Attributes attrs = getStudyAttributes(studyUID);
        if (attrs == null)
            return null;

        Attributes sopInstanceRefs = getSOPInstanceRefs(
                studyUID, seriesUID, objectUID, queryParam, seriesAttrs, availability);
        if (sopInstanceRefs != null)
            attrs.newSequence(Tag.CurrentRequestedProcedureEvidenceSequence, 1).add(sopInstanceRefs);
        return attrs;
    }

    public Attributes getSOPInstanceRefs(
            String studyUID, String seriesUID, String objectUID, QueryParam queryParam,
            Collection<Attributes> seriesAttrs, boolean availability) {
        BooleanBuilder predicate = new BooleanBuilder(QStudy.study.studyInstanceUID.eq(studyUID));
        if (seriesUID != null) {
            predicate.and(QSeries.series.seriesInstanceUID.eq(seriesUID));
            if (objectUID != null)
                predicate.and(QInstance.instance.sopInstanceUID.eq(objectUID));
        }
        hideRejectedInstancesAndRejectionNotes(predicate, queryParam);
        List<Tuple> tuples = new HibernateQuery<Void>(em.unwrap(Session.class))
                .select(SOP_REFS_OF_STUDY)
                .from(QInstance.instance)
                .join(QInstance.instance.series, QSeries.series)
                .join(QSeries.series.study, QStudy.study)
                .where(predicate)
                .fetch();

        if (tuples.isEmpty())
            return null;

        Attributes refStudy = new Attributes(2);
        Sequence refSeriesSeq = refStudy.newSequence(Tag.ReferencedSeriesSequence, 10);
        refStudy.setString(Tag.StudyInstanceUID, VR.UI, studyUID);
        HashMap<Long, Sequence> seriesMap = new HashMap<>();
        for (Tuple tuple : tuples) {
            Long seriesPk = tuple.get(QSeries.series.pk);
            Sequence refSOPSeq = seriesMap.get(seriesPk);
            if (refSOPSeq == null) {
                Attributes refSeries = new Attributes(4);
                refSeries.setString(Tag.RetrieveAETitle, VR.AE, queryParam.getAETitle());
                refSOPSeq = refSeries.newSequence(Tag.ReferencedSOPSequence, 10);
                refSeries.setString(Tag.SeriesInstanceUID, VR.UI, tuple.get(QSeries.series.seriesInstanceUID));
                seriesMap.put(seriesPk, refSOPSeq);
                refSeriesSeq.add(refSeries);
                if (seriesAttrs != null)
                    seriesAttrs.add(getSeriesAttributes(seriesPk));
            }
            Attributes refSOP = new Attributes(3);
            if (availability)
                refSOP.setString(Tag.InstanceAvailability, VR.CS,
                        tuple.get(QInstance.instance.availability).toString());
            refSOP.setString(Tag.ReferencedSOPClassUID, VR.UI, tuple.get(QInstance.instance.sopClassUID));
            refSOP.setString(Tag.ReferencedSOPInstanceUID, VR.UI, tuple.get(QInstance.instance.sopInstanceUID));
            refSOPSeq.add(refSOP);
        }
        return refStudy;
    }

    public Attributes getStudyAttributes(String studyInstanceUID) {
        Tuple result = new HibernateQuery<Void>(em.unwrap(Session.class))
                .select(PATIENT_STUDY_ATTRS)
                .from(QStudy.study)
                .join(QStudy.study.attributesBlob, QueryBuilder.studyAttributesBlob)
                .join(QStudy.study.patient, QPatient.patient)
                .join(QPatient.patient.attributesBlob, QueryBuilder.patientAttributesBlob)
                .where(QStudy.study.studyInstanceUID.eq(studyInstanceUID))
                .fetchOne();
        if (result == null)
            return null;
        Attributes patAttrs = AttributesBlob.decodeAttributes(
                result.get(QueryBuilder.patientAttributesBlob.encodedAttributes), null);
        Attributes studyAttrs = AttributesBlob.decodeAttributes(
                result.get(QueryBuilder.studyAttributesBlob.encodedAttributes), null);
        Attributes.unifyCharacterSets(patAttrs, studyAttrs);
        Attributes attrs = new Attributes(patAttrs.size() + studyAttrs.size());
        attrs.addAll(patAttrs);
        attrs.addAll(studyAttrs);
        return attrs;
    }

    private Attributes getSeriesAttributes(Long seriesPk) {
        return AttributesBlob.decodeAttributes(new HibernateQuery<Void>(em.unwrap(Session.class))
                .select(QueryBuilder.seriesAttributesBlob.encodedAttributes)
                .from(QSeries.series)
                .join(QSeries.series.attributesBlob, QueryBuilder.seriesAttributesBlob)
                .where(QSeries.series.pk.eq(seriesPk))
                .fetchOne(), null);
    }

    void hideRejectedInstancesAndRejectionNotes(BooleanBuilder predicate, QueryParam queryParam) {
        predicate.and(QueryBuilder.hideRejectedInstance(queryParam));
        predicate.and(QueryBuilder.hideRejectionNote(queryParam));
    }

    public StudyQueryAttributes calculateStudyQueryAttributes(Long studyPk, QueryParam queryParam) {
        StudyQueryAttributesBuilder builder = new StudyQueryAttributesBuilder();
        BooleanBuilder predicate = new BooleanBuilder(QSeries.series.study.pk.eq(studyPk));
        hideRejectedInstancesAndRejectionNotes(predicate, queryParam);
        try (
            CloseableIterator<Tuple> results = new HibernateQuery<Void>(em.unwrap(Session.class))
                    .select(CALC_STUDY_QUERY_ATTRS)
                    .from(QInstance.instance)
                    .innerJoin(QInstance.instance.series, QSeries.series)
                    .where(predicate)
                    .iterate()) {

            while (results.hasNext()) {
                builder.addInstance(results.next());
            }
        }
        StudyQueryAttributes queryAttrs = builder.build();
        queryAttrs.setViewID(queryParam.getViewID());
        queryAttrs.setStudy(em.getReference(Study.class, studyPk));
        em.persist(queryAttrs);
        return queryAttrs;
    }

    public SeriesQueryAttributes calculateSeriesQueryAttributes(Long seriesPk, QueryParam queryParam) {
        SeriesQueryAttributesBuilder builder = new SeriesQueryAttributesBuilder();
        BooleanBuilder predicate = new BooleanBuilder(QInstance.instance.series.pk.eq(seriesPk));
        hideRejectedInstancesAndRejectionNotes(predicate, queryParam);
        try (
            CloseableIterator<Tuple> results = new HibernateQuery<Void>(em.unwrap(Session.class))
                    .select(CALC_SERIES_QUERY_ATTRS)
                    .from(QInstance.instance)
                    .where(predicate)
                    .iterate()) {

            while (results.hasNext()) {
                builder.addInstance(results.next());
            }
        }
        SeriesQueryAttributes queryAttrs = builder.build();
        queryAttrs.setViewID(queryParam.getViewID());
        queryAttrs.setSeries(em.getReference(Series.class, seriesPk));
        em.persist(queryAttrs);
        return queryAttrs;
    }

    private static class CommonStudySeriesQueryAttributesBuilder {

        protected int numberOfInstances;
        protected String[] retrieveAETs;
        protected Availability availability;

        public void addInstance(Tuple result) {
            String[] retrieveAETs1 = StringUtils.split(result.get(QInstance.instance.retrieveAETs), '\\');
            Availability availability1 = result.get(QInstance.instance.availability);
            if (numberOfInstances++ == 0) {
                retrieveAETs = retrieveAETs1;
                availability = availability1;
            } else {
                retrieveAETs = intersection(retrieveAETs, retrieveAETs1);
                if (availability.compareTo(availability1) < 0)
                    availability = availability1;
            }
        }
    }

    private static String[] intersection(String[] ss1, String[] ss2) {
        int l = 0;
        for (int i = 0; i < ss1.length; i++)
            if (contains(ss2, ss1[i]))
                ss1[l++] = ss1[i];

        if (l == ss1.length)
            return ss1;

        String[] ss = new String[l];
        System.arraycopy(ss1, 0, ss, 0, l);
        return ss;
    }

    private static boolean contains(String[] ss, String s0) {
        for (String s : ss)
            if (s0.equals(s))
                return true;
        return false;
    }

    private static class SeriesQueryAttributesBuilder extends CommonStudySeriesQueryAttributesBuilder {

        public SeriesQueryAttributes build() {
            SeriesQueryAttributes queryAttrs = new SeriesQueryAttributes();
            queryAttrs.setNumberOfInstances(numberOfInstances);
            if (numberOfInstances > 0) {
                queryAttrs.setRetrieveAETs(retrieveAETs);
                queryAttrs.setAvailability(availability);
            }
            return queryAttrs;
        }
    }

    private static class StudyQueryAttributesBuilder extends CommonStudySeriesQueryAttributesBuilder {
    
        private Set<Long> seriesPKs = new HashSet<>();
        private Set<String> mods = new HashSet<>();
        private Set<String> cuids = new HashSet<>();

        @Override
        public void addInstance(Tuple result) {
            super.addInstance(result);
            if (seriesPKs.add(result.get(QSeries.series.pk))) {
                String modality1 = result.get(QSeries.series.modality);
                if (modality1 != null)
                    mods.add(modality1);
            }
            cuids.add(result.get(QInstance.instance.sopClassUID));
        }
    
        public StudyQueryAttributes build() {
            StudyQueryAttributes queryAttrs = new StudyQueryAttributes();
            queryAttrs.setNumberOfInstances(numberOfInstances);
            if (numberOfInstances > 0) {
                queryAttrs.setNumberOfSeries(seriesPKs.size());
                queryAttrs.setModalitiesInStudy(mods.toArray(new String[mods.size()]));
                queryAttrs.setSOPClassesInStudy(cuids.toArray(new String[cuids.size()]));
                queryAttrs.setRetrieveAETs(retrieveAETs);
                queryAttrs.setAvailability(availability);
            }
            return queryAttrs;
        }
    }
}
