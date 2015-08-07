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

package org.dcm4chee.archive.query.impl;


import com.mysema.commons.lang.CloseableIterator;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Predicate;
import com.querydsl.jpa.hibernate.HibernateQuery;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.archive.conf.Availability;
import org.dcm4chee.archive.entity.*;
import org.dcm4chee.archive.query.util.QueryBuilder;
import org.dcm4chee.archive.query.util.QueryParam;
import org.hibernate.Session;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 *
 */
@Stateless
public class QueryServiceEJB {

    static final Expression<?>[] PATIENT_STUDY_SERIES_ATTRS = {
        QStudy.study.pk,
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

    @PersistenceContext(unitName = "dcm4chee-arc")
    EntityManager em;

    public Attributes getSeriesAttributes(Long seriesPk, QueryParam queryParam) {
        String viewID = queryParam.getViewID();
        Tuple result = new HibernateQuery<Void>(em.unwrap(Session.class))
                .select(PATIENT_STUDY_SERIES_ATTRS)
                .from(QSeries.series)
                .join(QSeries.series.attributesBlob, QueryBuilder.seriesAttributesBlob)
                .leftJoin(QSeries.series.queryAttributes)
                .on(QSeriesQueryAttributes.seriesQueryAttributes.viewID.eq(viewID))
                .join(QSeries.series.study, QStudy.study)
                .join(QStudy.study.attributesBlob, QueryBuilder.studyAttributesBlob)
                .leftJoin(QStudy.study.queryAttributes)
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
        attrs.setInt(Tag.NumberOfStudyRelatedSeries, VR.US, numberOfStudyRelatedSeries);
        attrs.setInt(Tag.NumberOfStudyRelatedInstances, VR.US, numberOfStudyRelatedInstances);
        attrs.setInt(Tag.NumberOfSeriesRelatedInstances, VR.US, numberOfSeriesRelatedInstances);
        return attrs;
    }

    Predicate createPredicate(Predicate initial, QueryParam queryParam) {
        BooleanBuilder builder = new BooleanBuilder(initial);
        builder.and(QueryBuilder.hideRejectedInstance(queryParam));
        builder.and(QueryBuilder.hideRejectionNote(queryParam));
        return builder;
    }

    public StudyQueryAttributes calculateStudyQueryAttributes(Long studyPk, QueryParam queryParam) {
        StudyQueryAttributesBuilder builder = new StudyQueryAttributesBuilder();
        try (
            CloseableIterator<Tuple> results = new HibernateQuery<Void>(em.unwrap(Session.class))
                    .select(CALC_STUDY_QUERY_ATTRS)
                    .from(QInstance.instance)
                    .innerJoin(QInstance.instance.series, QSeries.series)
                    .where(createPredicate(QSeries.series.study.pk.eq(studyPk), queryParam))
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

    public SeriesQueryAttributes calculateSeriesQueryAttributes(
            Long seriesPk, QueryParam queryParam) {
        SeriesQueryAttributesBuilder builder = new SeriesQueryAttributesBuilder();
        try (
            CloseableIterator<Tuple> results = new HibernateQuery<Void>(em.unwrap(Session.class))
                    .select(CALC_SERIES_QUERY_ATTRS)
                    .from(QInstance.instance)
                    .where(createPredicate(QInstance.instance.series.pk.eq(seriesPk), queryParam))
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
