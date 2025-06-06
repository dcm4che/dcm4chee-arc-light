/*
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
 * Portions created by the Initial Developer are Copyright (C) 2015-2018
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
 */

package org.dcm4chee.arc.update.impl;

import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.*;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.data.Issuer;
import org.dcm4chee.arc.code.CodeCache;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.query.util.QueryBuilder;
import org.dcm4chee.arc.query.util.QueryParam;

import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@protonmail.com>
 * @since Apr 2025
 */
@Stateless
public class UpdateServiceEJB {
    @PersistenceContext(unitName = "dcm4chee-arc")
    EntityManager em;

    @Inject
    CodeCache codeCache;

    public int updateAccessControlIDOfStudies(Attributes keys, QueryParam queryParam, String accessControlID) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaUpdate<Study> update = cb.createCriteriaUpdate(Study.class);
        Root<Study> root = update.from(Study.class);
        update.set(root.get(Study_.accessControlID), accessControlID);
        Subquery<Study> sq = update.subquery(Study.class);
        Root<Study> study = sq.correlate(root);
        Join<Study, Patient> patient = study.join(Study_.patient);
        QueryBuilder builder = new QueryBuilder(cb);
        IDWithIssuer idWithIssuer = IDWithIssuer.pidOf(keys);
        List<Predicate> predicates = builder.studyPredicates(update, patient, study,
                idWithIssuer != null
                        ? new IDWithIssuer[] { idWithIssuer } : IDWithIssuer.EMPTY,
                idWithIssuer == null && queryParam.isFilterByIssuerOfPatientID()
                        ? Issuer.fromIssuerOfPatientID(keys) : null,
                keys, queryParam,
                codeCache.findOrCreateEntities(
                        queryParam.getQueryRetrieveView().getShowInstancesRejectedByCodes()));
        update.where(cb.exists(sq.select(study).where(predicates.toArray(new Predicate[0]))));
        return em.createQuery(update).executeUpdate();
    }

    public int updateAccessControlIDOfSeries(Attributes keys, QueryParam queryParam, String accessControlID) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaUpdate<Series> update = cb.createCriteriaUpdate(Series.class);
        Root<Series> root = update.from(Series.class);
        update.set(root.get(Series_.accessControlID), accessControlID);
        Subquery<Series> sq = update.subquery(Series.class);
        Root<Series> series = sq.correlate(root);
        Join<Series, Study> study = series.join(Series_.study);
        Join<Study, Patient> patient = study.join(Study_.patient);
        QueryBuilder builder = new QueryBuilder(cb);
        IDWithIssuer idWithIssuer = IDWithIssuer.pidOf(keys);
        List<Predicate> predicates = builder.seriesPredicates(update, patient, study, series,
                idWithIssuer != null
                        ? new IDWithIssuer[] { idWithIssuer } : IDWithIssuer.EMPTY,
                idWithIssuer == null && queryParam.isFilterByIssuerOfPatientID()
                        ? Issuer.fromIssuerOfPatientID(keys) : null,
                keys, queryParam,
                codeCache.findOrCreateEntities(
                        queryParam.getQueryRetrieveView().getShowInstancesRejectedByCodes()));
        update.where(cb.exists(sq.select(series).where(predicates.toArray(new Predicate[0]))));
        return em.createQuery(update).executeUpdate();
    }
}
