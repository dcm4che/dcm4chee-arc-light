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

import org.dcm4che3.data.Attributes;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.QueryOption;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.query.util.QueryParam;
import org.dcm4chee.arc.code.CodeCache;
import org.dcm4chee.arc.conf.QueryRetrieveView;
import org.dcm4chee.arc.entity.SeriesQueryAttributes;
import org.dcm4chee.arc.entity.StudyQueryAttributes;
import org.dcm4chee.arc.query.Query;
import org.hibernate.Session;
import org.hibernate.StatelessSession;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.EnumSet;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
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
    private Event<QueryContext> queryEvent;

    StatelessSession openStatelessSession() {
        return em.unwrap(Session.class).getSessionFactory().openStatelessSession();
    }

    @Override
    public QueryContext newQueryContextFIND(Association as, String sopClassUID, EnumSet<QueryOption> queryOpts) {
        ApplicationEntity ae = as.getApplicationEntity();
        return new QueryContextImpl(as, sopClassUID, ae, newQueryParam(ae,
                queryOpts.contains(QueryOption.DATETIME),
                queryOpts.contains(QueryOption.FUZZY), false),
                this);
    }

    @Override
    public QueryContext newQueryContextQIDO(HttpServletRequest httpRequest, ApplicationEntity ae,
                                            boolean fuzzyMatching, boolean returnEmpty) {
        return new QueryContextImpl(httpRequest, ae, newQueryParam(ae, true, fuzzyMatching, returnEmpty), this);
    }

    private QueryParam newQueryParam(ApplicationEntity ae, boolean datetimeMatching,
                                     boolean fuzzyMatching, boolean returnEmpty) {
        QueryParam queryParam = new QueryParam(ae, datetimeMatching, fuzzyMatching, returnEmpty);
        QueryRetrieveView qrView = queryParam.getQueryRetrieveView();
        queryParam.setHideRejectionNotesWithCode(
                codeCache.findOrCreateEntities(qrView.getHideRejectionNotesWithCodes()));
        queryParam.setShowInstancesRejectedByCode(
                codeCache.findOrCreateEntities(qrView.getShowInstancesRejectedByCodes()));
        return queryParam;
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
    public SeriesQueryAttributes calculateSeriesQueryAttributes(Long seriesPk, QueryParam queryParam) {
        return ejb.calculateSeriesQueryAttributes(seriesPk, queryParam);
    }

    @Override
    public Attributes getStudyAttributesWithSOPInstanceRefs(
            String studyUID, ApplicationEntity ae, Collection<Attributes> seriesAttrs) {
        return ejb.getStudyAttributesWithSOPInstanceRefs(
                studyUID, null, null, newQueryParam(ae, false, false, false), seriesAttrs, false);
    }

    @Override
    public Attributes getStudyAttributesWithSOPInstanceRefs(
            String studyUID, String seriesUID, String objectUID, ApplicationEntity ae, boolean availability) {
        return ejb.getStudyAttributesWithSOPInstanceRefs(
                studyUID, seriesUID, objectUID, newQueryParam(ae, false, false, false), null, availability);
    }
}
