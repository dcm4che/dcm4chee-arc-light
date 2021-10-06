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

package org.dcm4chee.arc.retrieve.impl;

import org.dcm4chee.arc.conf.Availability;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Tuple;
import javax.persistence.criteria.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jan 2016
 */
@Stateless
public class RetrieveServiceEJB {
    private static Logger LOG = LoggerFactory.getLogger(RetrieveServiceEJB.class);

    @PersistenceContext(unitName = "dcm4chee-arc")
    private EntityManager em;

    public void updateStudyAccessTime(Long studyPk) {
        em.createNamedQuery(Study.UPDATE_ACCESS_TIME)
                .setParameter(1, studyPk)
                .executeUpdate();
    }

    public void updateCompleteness(RetrieveContext ctx, Completeness completeness) {
        String[] studyIUIDs = ctx.getStudyInstanceUIDs();
        String[] seriesIUIDs = ctx.getSeriesInstanceUIDs();
        switch (ctx.getQueryRetrieveLevel()) {
            case STUDY:
                for (String studyIUID : studyIUIDs)
                    setCompletenessOfStudy(studyIUID, completeness);
                break;
            case SERIES:
                for (String seriesIUID : seriesIUIDs) {
                    setCompletenessOfSeries(studyIUIDs[0], seriesIUID, completeness);
                }
                setCompletenessOfStudy(studyIUIDs[0], Completeness.UNKNOWN);
                break;
            case IMAGE:
                setCompletenessOfSeries(studyIUIDs[0], seriesIUIDs[0], Completeness.UNKNOWN);
                setCompletenessOfStudy(studyIUIDs[0], Completeness.UNKNOWN);
                break;
        }
    }

    private void setCompletenessOfStudy(String studyInstanceUID, Completeness completeness) {
        em.createNamedQuery(completeness == Completeness.PARTIAL
                ? Study.INCREMENT_FAILED_RETRIEVES
                : Study.SET_COMPLETENESS)
                .setParameter(1, studyInstanceUID)
                .setParameter(2, completeness)
                .executeUpdate();
        if (completeness != Completeness.UNKNOWN)
            em.createNamedQuery(Series.SET_COMPLETENESS_OF_STUDY)
                    .setParameter(1, studyInstanceUID)
                    .setParameter(2, Completeness.COMPLETE)
                    .executeUpdate();
    }

    private void setCompletenessOfSeries(String studyInstanceUID, String seriesInstanceUID,
                                         Completeness completeness) {
        em.createNamedQuery(completeness == Completeness.PARTIAL
                ? Series.INCREMENT_FAILED_RETRIEVES
                : Series.SET_COMPLETENESS)
                .setParameter(1, studyInstanceUID)
                .setParameter(2, seriesInstanceUID)
                .setParameter(3, completeness)
                .executeUpdate();
    }

    public void updateInstanceAvailability(RetrieveContext ctx) {
        switch (ctx.getQueryRetrieveLevel()) {
            case STUDY:
                updateInstanceAvailability(StudyQueryAttributes.UPDATE_AVAILABILITY_BY_STUDY_IUID,
                        ctx.getStudyInstanceUIDs(), ctx.getUpdateInstanceAvailability());
                updateInstanceAvailability(SeriesQueryAttributes.UPDATE_AVAILABILITY_BY_STUDY_IUID,
                        ctx.getStudyInstanceUIDs(), ctx.getUpdateInstanceAvailability());
                updateInstanceAvailability(Instance.UPDATE_AVAILABILITY_BY_STUDY_IUID,
                        ctx.getStudyInstanceUIDs(), ctx.getUpdateInstanceAvailability());
                break;
            case SERIES:
                updateInstanceAvailability(SeriesQueryAttributes.UPDATE_AVAILABILITY_BY_SERIES_IUID,
                        ctx.getSeriesInstanceUIDs(), ctx.getUpdateInstanceAvailability());
                updateInstanceAvailability(Instance.UPDATE_AVAILABILITY_BY_SERIES_IUID,
                        ctx.getSeriesInstanceUIDs(), ctx.getUpdateInstanceAvailability());
                break;
            case IMAGE:
                updateInstanceAvailability(Instance.UPDATE_AVAILABILITY_BY_SOP_IUID,
                        ctx.getSopInstanceUIDs(), ctx.getUpdateInstanceAvailability());
                break;
        }
    }

    private void updateInstanceAvailability(String name, String[] uids, Availability availability) {
        for (String uid : uids) {
            em.createNamedQuery(name)
                    .setParameter(1, uid)
                    .setParameter(2, availability)
                    .executeUpdate();
        }
    }

    public List<Date> queryLastModified(RetrieveContext ctx) {
        String seriesUID = ctx.getSeriesInstanceUID();
        String sopUID = ctx.getSopInstanceUID();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();

        Root<Instance> instance = query.from(Instance.class);
        Join<Instance, Series> series = instance.join(Instance_.series);
        Join<Series, Study> study = series.join(Series_.study);
        Join<Study, Patient> patient = null;
        List<Predicate> predicates = new ArrayList<>();
        List<Selection> selections = new ArrayList<>();
        Expression<Date> maxInstUpdatedTime = cb.greatest(instance.get(Instance_.updatedTime));
        Expression<Date> maxSeriesUpdatedTime = cb.greatest(series.get(Series_.updatedTime));

        if (ctx.isPatientUpdatedTime4LastModified()) {
            patient = study.join(Study_.patient);
            selections.add(patient.get(Patient_.updatedTime));
        }
        predicates.add(cb.equal(study.get(Study_.studyInstanceUID), ctx.getStudyInstanceUID()));
        selections.add(study.get(Study_.modifiedTime));
        if (seriesUID == null)
            selections.add(maxSeriesUpdatedTime);
        else {
            predicates.add(cb.equal(series.get(Series_.seriesInstanceUID), seriesUID));
            selections.add(series.get(Series_.updatedTime));
        }

        if (sopUID == null) {
            List<Expression> groupBy = new ArrayList<>();
            if (patient != null)
                groupBy.add(patient.get(Patient_.pk));
            groupBy.add(study.get(Study_.pk));
            if (seriesUID != null)
                groupBy.add(series.get(Series_.pk));
            query.groupBy(groupBy.toArray(new Expression[0]));
            selections.add(maxInstUpdatedTime);
        } else {
            predicates.add(cb.equal(instance.get(Instance_.sopInstanceUID), sopUID));
            selections.add(instance.get(Instance_.updatedTime));
        }

        query.where(predicates.toArray(new Predicate[0]))
              .multiselect(selections.toArray(new Selection[0]));

        Tuple result = em.createQuery(query).getSingleResult();
        List<Date> dates = new ArrayList<>();
        if (patient != null)
            dates.add(result.get(patient.get(Patient_.updatedTime)));
        dates.add(result.get(study.get(Study_.modifiedTime)));
        dates.add(result.get(seriesUID == null ? maxSeriesUpdatedTime : series.get(Series_.updatedTime)));
        dates.add(result.get(sopUID == null ? maxInstUpdatedTime : instance.get(Instance_.updatedTime)));
        return dates;
    }
}
