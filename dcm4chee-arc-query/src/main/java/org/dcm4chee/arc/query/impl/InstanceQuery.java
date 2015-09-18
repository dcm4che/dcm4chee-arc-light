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
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.jpa.hibernate.HibernateQuery;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4chee.arc.conf.Availability;
import org.dcm4chee.arc.entity.AttributesBlob;
import org.dcm4chee.arc.entity.QInstance;
import org.dcm4chee.arc.entity.QSeries;
import org.dcm4chee.arc.query.util.QueryBuilder;
import org.dcm4chee.arc.query.QueryContext;
import org.hibernate.StatelessSession;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2015
 */
class InstanceQuery extends AbstractQuery {

    private static final Expression<?>[] SELECT = {
            QSeries.series.pk,
            QInstance.instance.retrieveAETs,
            QInstance.instance.availability,
            QInstance.instance.attributesBlob.encodedAttributes
    };

    private Long seriesPk;
    private Attributes seriesAttrs;

    public InstanceQuery(QueryContext context, StatelessSession session) {
        super(context, session);
    }

    @Override
    protected HibernateQuery<Tuple> newHibernateQuery() {
        HibernateQuery<Tuple> q = new HibernateQuery<Void>(session).select(SELECT).from(QInstance.instance);
        q = QueryBuilder.applyInstanceLevelJoins(q,
                context.getQueryKeys(),
                context.getQueryParam());
        q = QueryBuilder.applySeriesLevelJoins(q,
                context.getQueryKeys(),
                context.getQueryParam());
        q = QueryBuilder.applyStudyLevelJoins(q,
                context.getQueryKeys(),
                context.getQueryParam());
        q = QueryBuilder.applyPatientLevelJoins(q,
                context.getPatientIDs(),
                context.getQueryKeys(),
                context.getQueryParam());
        BooleanBuilder predicates = new BooleanBuilder();
        QueryBuilder.addPatientLevelPredicates(predicates,
                context.getPatientIDs(),
                context.getQueryKeys(),
                context.getQueryParam());
        QueryBuilder.addStudyLevelPredicates(predicates,
                context.getQueryKeys(),
                context.getQueryParam());
        QueryBuilder.addSeriesLevelPredicates(predicates,
                context.getQueryKeys(),
                context.getQueryParam());
        QueryBuilder.addInstanceLevelPredicates(predicates,
                context.getQueryKeys(),
                context.getQueryParam());
        return q.where(predicates);
    }

    @Override
    protected Attributes toAttributes(Tuple results) {
        Long seriesPk = results.get(QSeries.series.pk);
        String retrieveAETs = results.get(QInstance.instance.retrieveAETs);
        Availability availability = results.get(QInstance.instance.availability);
        if (!seriesPk.equals(this.seriesPk)) {
            this.seriesAttrs = context.getQueryService()
                    .getSeriesAttributes(seriesPk, context.getQueryParam());
            this.seriesPk = seriesPk;
        }
        Attributes instAtts = AttributesBlob.decodeAttributes(
                results.get(QInstance.instance.attributesBlob.encodedAttributes), null);
        Attributes.unifyCharacterSets(seriesAttrs, instAtts);
        Attributes attrs = new Attributes(seriesAttrs.size() + instAtts.size() + 2);
        attrs.addAll(seriesAttrs);
        attrs.addAll(instAtts);
        attrs.setString(Tag.RetrieveAETitle, VR.AE, retrieveAETs);
        attrs.setString(Tag.InstanceAvailability, VR.CS, availability.toString());
        return attrs;
    }

    @Override
    public boolean isOptionalKeysNotSupported() {
        //TODO
        return false;
    }
}
