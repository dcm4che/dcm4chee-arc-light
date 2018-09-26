/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 *  The contents of this file are subject to the Mozilla Public License Version
 *  1.1 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  http://www.mozilla.org/MPL/
 *
 *  Software distributed under the License is distributed on an "AS IS" basis,
 *  WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 *  for the specific language governing rights and limitations under the
 *  License.
 *
 *  The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 *  Java(TM), hosted at https://github.com/dcm4che.
 *
 *  The Initial Developer of the Original Code is
 *  J4Care.
 *  Portions created by the Initial Developer are Copyright (C) 2015-2017
 *  the Initial Developer. All Rights Reserved.
 *
 *  Contributor(s):
 *  See @authors listed below
 *
 *  Alternatively, the contents of this file may be used under the terms of
 *  either the GNU General Public License Version 2 or later (the "GPL"), or
 *  the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 *  in which case the provisions of the GPL or the LGPL are applicable instead
 *  of those above. If you wish to allow use of your version of this file only
 *  under the terms of either the GPL or the LGPL, and not to allow others to
 *  use your version of this file under the terms of the MPL, indicate your
 *  decision by deleting the provisions above and replace them with the notice
 *  and other provisions required by the GPL or the LGPL. If you do not delete
 *  the provisions above, a recipient may use your version of this file under
 *  the terms of any one of the MPL, the GPL or the LGPL.
 *
 */

package org.dcm4chee.arc.query.impl;

import com.mysema.commons.lang.CloseableIterator;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.jpa.hibernate.HibernateQuery;
import org.dcm4che3.net.Device;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.code.CodeCache;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.Availability;
import org.dcm4chee.arc.conf.QueryRetrieveView;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.query.util.QueryBuilder;
import org.hibernate.Session;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Oct 2017
 */
@Stateless
@TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
public class QueryAttributesEJB {

    static final Expression<?>[] CALC_STUDY_QUERY_ATTRS = {
            QSeries.series.pk,
            QSeries.series.modality,
            QSeriesQueryAttributes.seriesQueryAttributes.numberOfInstances,
            QSeriesQueryAttributes.seriesQueryAttributes.sopClassesInSeries,
            QSeriesQueryAttributes.seriesQueryAttributes.retrieveAETs,
            QSeriesQueryAttributes.seriesQueryAttributes.availability,
    };

    static final Expression<?>[] CALC_SERIES_QUERY_ATTRS = {
            QInstance.instance.sopClassUID,
            QInstance.instance.retrieveAETs,
            QInstance.instance.availability
    };

    @Inject
    private CodeCache codeCache;

    @Inject
    private Device device;

    @PersistenceContext(unitName = "dcm4chee-arc")
    EntityManager em;

    public StudyQueryAttributes calculateStudyQueryAttributes(Long studyPk, QueryRetrieveView qrView) {
        StudyQueryAttributesBuilder builder = new StudyQueryAttributesBuilder();
        for (Tuple tuple : new HibernateQuery<Void>(em.unwrap(Session.class))
                .select(CALC_STUDY_QUERY_ATTRS)
                .from(QSeries.series)
                .leftJoin(QSeries.series.queryAttributes, QSeriesQueryAttributes.seriesQueryAttributes)
                .on(QSeriesQueryAttributes.seriesQueryAttributes.viewID.eq(qrView.getViewID()))
                .where(QSeries.series.study.pk.eq(studyPk))
                .fetch()) {
            Integer numberOfInstancesI = tuple.get(QSeriesQueryAttributes.seriesQueryAttributes.numberOfInstances);
            if (numberOfInstancesI == null)
                builder.add(tuple, calculateSeriesQueryAttributes(tuple.get(QSeries.series.pk), qrView));
            else
                builder.add(tuple);
        }
        StudyQueryAttributes queryAttrs = builder.build();
        queryAttrs.setViewID(qrView.getViewID());
        queryAttrs.setStudy(em.getReference(Study.class, studyPk));
        em.persist(queryAttrs);
        return queryAttrs;
    }

    public SeriesQueryAttributes calculateSeriesQueryAttributes(Long seriesPk, QueryRetrieveView qrView) {
        SeriesQueryAttributesBuilder builder = new SeriesQueryAttributesBuilder();
        BooleanBuilder predicate = new BooleanBuilder(QInstance.instance.series.pk.eq(seriesPk));
        predicate.and(QueryBuilder.hideRejectedInstance(
                codeCache.findOrCreateEntities(qrView.getShowInstancesRejectedByCodes()),
                qrView.isHideNotRejectedInstances()));
        predicate.and(QueryBuilder.hideRejectionNote(
                codeCache.findOrCreateEntities(qrView.getHideRejectionNotesWithCodes())));
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
        queryAttrs.setViewID(qrView.getViewID());
        queryAttrs.setSeries(em.getReference(Series.class, seriesPk));
        em.persist(queryAttrs);
        return queryAttrs;
    }

    public boolean calculateStudyQueryAttributes(String studyUID) {
        Long studyPk;
        try {
            studyPk = em.createNamedQuery(Study.FIND_PK_BY_STUDY_UID, Long.class)
                    .setParameter(1, studyUID)
                    .getSingleResult();
        } catch (NoResultException e) {
            return false;
        }
        ArchiveDeviceExtension arcDev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
        Set<String> viewIDs = new HashSet<>(arcDev.getQueryRetrieveViewIDs());
        viewIDs.removeAll(em.createNamedQuery(StudyQueryAttributes.VIEW_IDS_FOR_STUDY_PK, String.class)
                .setParameter(1, studyPk)
                .getResultList());

        for (String viewID : viewIDs) {
            calculateStudyQueryAttributes(studyPk, arcDev.getQueryRetrieveView(viewID));
        }
        return true;
    }

    private static class SeriesQueryAttributesBuilder {
        private int numberOfInstances;
        private String[] retrieveAETs;
        private Availability availability;
        private Set<String> cuids = new HashSet<>();

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
            cuids.add(result.get(QInstance.instance.sopClassUID));
        }

        public SeriesQueryAttributes build() {
            SeriesQueryAttributes queryAttrs = new SeriesQueryAttributes();
            queryAttrs.setNumberOfInstances(numberOfInstances);
            if (numberOfInstances > 0) {
                queryAttrs.setSOPClassesInSeries(StringUtils.concat(cuids, '\\'));
                queryAttrs.setRetrieveAETs(StringUtils.concat(retrieveAETs, '\\'));
                queryAttrs.setAvailability(availability);
            }
            return queryAttrs;
        }
    }

    private static class StudyQueryAttributesBuilder {

        private int numberOfSeries;
        private int numberOfInstances;
        private String[] retrieveAETs;
        private Availability availability;
        private Set<String> mods = new HashSet<>();
        private Set<String> cuids = new HashSet<>();

        public void add(Tuple tuple) {
            add(tuple.get(QSeriesQueryAttributes.seriesQueryAttributes.numberOfInstances),
                    tuple.get(QSeries.series.modality),
                    tuple.get(QSeriesQueryAttributes.seriesQueryAttributes.sopClassesInSeries),
                    tuple.get(QSeriesQueryAttributes.seriesQueryAttributes.retrieveAETs),
                    tuple.get(QSeriesQueryAttributes.seriesQueryAttributes.availability));
        }

        public void add(Tuple tuple, SeriesQueryAttributes series) {
            add(series.getNumberOfInstances(),
                    tuple.get(QSeries.series.modality),
                    series.getSOPClassesInSeries(),
                    series.getRetrieveAETs(),
                    series.getAvailability());
        }

        private void add(int numInstances, String modality, String sopClassesInSeries, String retrieveAETs,
                         Availability availability) {
            if (numInstances == 0)
                return;

            String[] retrieveAETs1 = StringUtils.split(retrieveAETs, '\\');
            numberOfInstances += numInstances;
            if (numberOfSeries++ == 0) {
                this.retrieveAETs = retrieveAETs1;
                this.availability = availability;
            } else {
                this.retrieveAETs = intersection(this.retrieveAETs, retrieveAETs1);
                if (this.availability.compareTo(availability) < 0)
                    this.availability = availability;
            }
            if (!modality.equals("*"))
                mods.add(modality);
            for (String cuid : StringUtils.split(sopClassesInSeries, '\\'))
                cuids.add(cuid);
        }

        public StudyQueryAttributes build() {
            StudyQueryAttributes queryAttrs = new StudyQueryAttributes();
            queryAttrs.setNumberOfInstances(numberOfInstances);
            if (numberOfInstances > 0) {
                queryAttrs.setNumberOfSeries(numberOfSeries);
                queryAttrs.setModalitiesInStudy(StringUtils.concat(mods, '\\'));
                queryAttrs.setSOPClassesInStudy(StringUtils.concat(cuids, '\\'));
                queryAttrs.setRetrieveAETs(StringUtils.concat(retrieveAETs, '\\'));
                queryAttrs.setAvailability(availability);
            }
            return queryAttrs;
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

}
