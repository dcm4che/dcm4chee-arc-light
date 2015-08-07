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

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.jpa.hibernate.HibernateQuery;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4chee.archive.conf.Availability;
import org.dcm4chee.archive.entity.*;
import org.dcm4chee.archive.query.QueryContext;
import org.dcm4chee.archive.query.util.QueryBuilder;
import org.hibernate.ScrollableResults;
import org.hibernate.StatelessSession;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2015
 */
class StudyQuery extends AbstractQuery {

    static final Expression<?>[] SELECT = {
            QStudy.study.pk,                                                // (0)
            QStudyQueryAttributes.studyQueryAttributes.numberOfInstances,   // (1)
            QStudyQueryAttributes.studyQueryAttributes.numberOfSeries,      // (2)
            QStudyQueryAttributes.studyQueryAttributes.modalitiesInStudy,   // (3)
            QStudyQueryAttributes.studyQueryAttributes.sopClassesInStudy,   // (4)
            QStudyQueryAttributes.studyQueryAttributes.retrieveAETs,        // (5)
            QStudyQueryAttributes.studyQueryAttributes.availability,        // (6)
            QueryBuilder.studyAttributesBlob.encodedAttributes,             // (7)
            QueryBuilder.patientAttributesBlob.encodedAttributes            // (8)
    };

    public StudyQuery(QueryContext context, StatelessSession session) {
        super(context, session);
    }

    @Override
    protected HibernateQuery<Tuple> newHibernateQuery() {
        HibernateQuery<Tuple> q = new HibernateQuery<Void>(session).select(SELECT).from(QStudy.study);
        q = QueryBuilder.applyPatientLevelJoins(q,
                context.getPatientIDs(),
                context.getQueryKeys(),
                context.getQueryParam());
        q = QueryBuilder.applyStudyLevelJoins(q,
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
        return q.where(predicates);
    }

    @Override
    protected Attributes toAttributes(ScrollableResults results) {
        Long studyPk = results.getLong(0);
        Integer numberOfInstancesI = results.getInteger(1);
        int numberOfStudyRelatedInstances;
        int numberOfStudyRelatedSeries;
        String modalitiesInStudy;
        String sopClassesInStudy;
        String retrieveAETs;
        Availability availability;
        if (numberOfInstancesI != null) {
            numberOfStudyRelatedInstances = numberOfInstancesI;
            if (numberOfStudyRelatedInstances == 0)
                return null;

            numberOfStudyRelatedSeries = results.getInteger(2);
            modalitiesInStudy = results.getString(3);
            sopClassesInStudy = results.getString(4);
            retrieveAETs = results.getString(5);
            availability = (Availability) results.get(6);
        } else {
            StudyQueryAttributes studyView = context.getQueryService()
                    .calculateStudyQueryAttributes(studyPk, context.getQueryParam());
            numberOfStudyRelatedInstances = studyView.getNumberOfInstances();
            if (numberOfStudyRelatedInstances == 0)
                return null;

            numberOfStudyRelatedSeries = studyView.getNumberOfSeries();
            modalitiesInStudy = studyView.getRawModalitiesInStudy();
            sopClassesInStudy = studyView.getRawSOPClassesInStudy();
            retrieveAETs = studyView.getRawRetrieveAETs();
            availability = studyView.getAvailability();
        }
        Attributes studyAttrs = AttributesBlob.decodeAttributes(results.getBinary(7), null);
        Attributes patAttrs = AttributesBlob.decodeAttributes(results.getBinary(8), null);
        Attributes.unifyCharacterSets(patAttrs, studyAttrs);
        Attributes attrs = new Attributes(patAttrs.size() + studyAttrs.size() + 6);
        attrs.addAll(patAttrs);
        attrs.addAll(studyAttrs);
        attrs.setString(Tag.RetrieveAETitle, VR.AE, retrieveAETs);
        attrs.setString(Tag.InstanceAvailability, VR.CS, availability.toString());
        attrs.setString(Tag.ModalitiesInStudy, VR.CS, modalitiesInStudy);
        attrs.setString(Tag.SOPClassesInStudy, VR.UI, sopClassesInStudy);
        attrs.setInt(Tag.NumberOfStudyRelatedSeries, VR.US, numberOfStudyRelatedSeries);
        attrs.setInt(Tag.NumberOfStudyRelatedInstances, VR.US, numberOfStudyRelatedInstances);
        return attrs;
    }

    @Override
    public boolean isOptionalKeysNotSupported() {
        //TODO
        return false;
    }
}
