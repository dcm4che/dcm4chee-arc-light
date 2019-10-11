/*
 * **** BEGIN LICENSE BLOCK *****
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
 *
 * **** END LICENSE BLOCK *****
 *
 */

package org.dcm4chee.arc.query.impl;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.dict.archive.PrivateTag;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.Availability;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.query.util.QueryBuilder;
import org.dcm4chee.arc.query.util.QueryParam;

import javax.persistence.EntityManager;
import javax.persistence.Tuple;
import javax.persistence.criteria.*;
import java.util.List;


/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Aug 2015
 */
class StudyQuery extends AbstractQuery {

    private Join<Study, Patient> patient;
    private Root<Study> study;
    private CollectionJoin<Study, StudyQueryAttributes> studyQueryAttributes;
    private Path<byte[]> patientAttrBlob;
    private Path<byte[]> studyAttrBlob;

    StudyQuery(QueryContext context, EntityManager em) {
        super(context, em);
    }

    @Override
    protected CriteriaQuery<javax.persistence.Tuple> multiselect() {
        CriteriaQuery<javax.persistence.Tuple> q = cb.createTupleQuery();
        this.study = q.from(Study.class);
        this.patient = study.join(Study_.patient);
        String viewID = context.getQueryParam().getViewID();
        this.studyQueryAttributes = QueryBuilder.joinStudyQueryAttributes(cb, study, viewID);
        return order(restrict(q, patient, study)).multiselect(
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
                study.get(Study_.externalRetrieveAET),
                study.get(Study_.size),
                studyQueryAttributes.get(StudyQueryAttributes_.numberOfInstances),
                studyQueryAttributes.get(StudyQueryAttributes_.numberOfSeries),
                studyQueryAttributes.get(StudyQueryAttributes_.modalitiesInStudy),
                studyQueryAttributes.get(StudyQueryAttributes_.sopClassesInStudy),
                studyQueryAttributes.get(StudyQueryAttributes_.retrieveAETs),
                studyQueryAttributes.get(StudyQueryAttributes_.availability),
                patientAttrBlob = patient.join(Patient_.attributesBlob).get(AttributesBlob_.encodedAttributes),
                studyAttrBlob = study.join(Study_.attributesBlob).get(AttributesBlob_.encodedAttributes));
    }

    @Override
    protected CriteriaQuery<Long> count() {
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        Root<Study> study = q.from(Study.class);
        return createQuery(q, study, cb.count(study));
    }

    @Override
    protected CriteriaQuery<Long> sumStudySize() {
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        Root<Study> study = q.from(Study.class);
        return createQuery(q, study, cb.sum(study.get(Study_.size)));
    }

    @Override
    protected CriteriaQuery<Long> withUnknownSize() {
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        Root<Study> study = q.from(Study.class);
        return createQuery(q, study, study.get(Study_.pk), cb.equal(study.get(Study_.size), -1L));
    }

    @Override
    protected Attributes toAttributes(Tuple results) {
        Long studyPk = results.get(study.get(Study_.pk));
        long studySize = results.get(study.get(Study_.size));
        if (studySize < 0)
            studySize = context.getQueryService().calculateStudySize(studyPk);
        Integer numberOfInstancesI = results.get(studyQueryAttributes.get(StudyQueryAttributes_.numberOfInstances));
        int numberOfStudyRelatedInstances;
        int numberOfStudyRelatedSeries;
        String modalitiesInStudy;
        String sopClassesInStudy;
        String retrieveAETs;
        Availability availability;
        QueryParam queryParam = context.getQueryParam();
        if (numberOfInstancesI != null) {
            numberOfStudyRelatedInstances = numberOfInstancesI;
            if (numberOfStudyRelatedInstances == 0) {
                return null;
            }
            numberOfStudyRelatedSeries = results.get(studyQueryAttributes.get(StudyQueryAttributes_.numberOfSeries));
            modalitiesInStudy = results.get(studyQueryAttributes.get(StudyQueryAttributes_.modalitiesInStudy));
            sopClassesInStudy = results.get(studyQueryAttributes.get(StudyQueryAttributes_.sopClassesInStudy));
            retrieveAETs = results.get(studyQueryAttributes.get(StudyQueryAttributes_.retrieveAETs));
            availability = results.get(studyQueryAttributes.get(StudyQueryAttributes_.availability));
        } else {
            StudyQueryAttributes studyView = context.getQueryService()
                    .calculateStudyQueryAttributes(studyPk, queryParam.getQueryRetrieveView());
            numberOfStudyRelatedInstances = studyView.getNumberOfInstances();
            if (numberOfStudyRelatedInstances == 0) {
                return null;
            }
            numberOfStudyRelatedSeries = studyView.getNumberOfSeries();
            modalitiesInStudy = studyView.getModalitiesInStudy();
            sopClassesInStudy = studyView.getSOPClassesInStudy();
            retrieveAETs = studyView.getRetrieveAETs();
            availability = studyView.getAvailability();
        }
        Attributes studyAttrs = AttributesBlob.decodeAttributes(results.get(studyAttrBlob), null);
        Attributes patAttrs = AttributesBlob.decodeAttributes(results.get(patientAttrBlob), null);
        Attributes.unifyCharacterSets(patAttrs, studyAttrs);
        Attributes attrs = new Attributes(patAttrs.size() + studyAttrs.size() + 20);
        attrs.addAll(patAttrs);
        attrs.addAll(studyAttrs, true);
        PatientQuery.addPatientQRAttrs(patient, context, results, attrs);
        String externalRetrieveAET = results.get(study.get(Study_.externalRetrieveAET));
        attrs.setString(Tag.RetrieveAETitle, VR.AE, retrieveAETs(retrieveAETs, externalRetrieveAET));
        attrs.setString(Tag.InstanceAvailability, VR.CS,
                StringUtils.maskNull(availability, Availability.UNAVAILABLE).toString());
        StudyQuery.addStudyQRAddrs(study, context, results, studySize,
                numberOfStudyRelatedInstances, numberOfStudyRelatedSeries,
                modalitiesInStudy, sopClassesInStudy, attrs);
        return attrs;
    }

    private CriteriaQuery<Tuple> order(CriteriaQuery<Tuple> q) {
        if (context.getOrderByTags() != null)
            q.orderBy(builder.orderStudies(patient, study, context.getOrderByTags()));
        return q;
    }

    private <T> CriteriaQuery<T> restrict(CriteriaQuery<T> q, Join<Study, Patient> patient, Root<Study> study,
            Predicate... extra) {
        List<Predicate> predicates = builder.studyPredicates(q, patient, study,
                context.getPatientIDs(),
                context.getQueryKeys(),
                context.getQueryParam());
        for (Predicate predicate : extra)
            predicates.add(predicate);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));
        return q;
    }

    private CriteriaQuery<Long> createQuery(CriteriaQuery<Long> q,
            Root<Study> study, Expression<Long> longExpression, Predicate... extra) {
        boolean hasPatientLevelPredicates = QueryBuilder.hasPatientLevelPredicates(
                context.getPatientIDs(),
                context.getQueryKeys(),
                context.getQueryParam());
        Join<Study, Patient> patient = null;
        if (hasPatientLevelPredicates) {
            patient = study.join(Study_.patient);
        }
        return restrict(q, patient, study, extra).select(longExpression);
    }

    static void addStudyQRAddrs(Path<Study> study, QueryContext context, Tuple results, long studySize,
            int numberOfStudyRelatedInstances, int numberOfStudyRelatedSeries,
            String modalitiesInStudy, String sopClassesInStudy, Attributes attrs) {
        attrs.setString(Tag.ModalitiesInStudy, VR.CS, StringUtils.split(modalitiesInStudy, '\\'));
        attrs.setString(Tag.SOPClassesInStudy, VR.UI, StringUtils.split(sopClassesInStudy, '\\'));
        attrs.setInt(Tag.NumberOfStudyRelatedSeries, VR.IS, numberOfStudyRelatedSeries);
        attrs.setInt(Tag.NumberOfStudyRelatedInstances, VR.IS, numberOfStudyRelatedInstances);
        if (!context.isReturnPrivate())
            return;

        attrs.setDate(PrivateTag.PrivateCreator, PrivateTag.StudyReceiveDateTime, VR.DT,
                results.get(study.get(Study_.createdTime)));
        attrs.setDate(PrivateTag.PrivateCreator, PrivateTag.StudyUpdateDateTime, VR.DT,
                results.get(study.get(Study_.updatedTime)));
        attrs.setDate(PrivateTag.PrivateCreator, PrivateTag.StudyAccessDateTime, VR.DT,
                results.get(study.get(Study_.accessTime)));
        attrs.setString(PrivateTag.PrivateCreator, PrivateTag.StudyExpirationState, VR.CS,
                results.get(study.get(Study_.expirationState)).toString());
        if (results.get(study.get(Study_.expirationDate)) != null)
            attrs.setString(PrivateTag.PrivateCreator, PrivateTag.StudyExpirationDate, VR.DA,
                    results.get(study.get(Study_.expirationDate)));
        if (results.get(study.get(Study_.expirationExporterID)) != null)
            attrs.setString(PrivateTag.PrivateCreator, PrivateTag.StudyExpirationExporterID, VR.LO,
                    results.get(study.get(Study_.expirationExporterID)));
        attrs.setString(PrivateTag.PrivateCreator, PrivateTag.StudyRejectionState, VR.CS,
                results.get(study.get(Study_.rejectionState)).toString());
        attrs.setString(PrivateTag.PrivateCreator, PrivateTag.StudyCompleteness, VR.CS,
                results.get(study.get(Study_.completeness)).toString());
        if (results.get(study.get(Study_.failedRetrieves)) != 0)
            attrs.setInt(PrivateTag.PrivateCreator, PrivateTag.FailedRetrievesOfStudy, VR.US,
                    results.get(study.get(Study_.failedRetrieves)));
        if (!results.get(study.get(Study_.accessControlID)).equals("*"))
            attrs.setString(PrivateTag.PrivateCreator, PrivateTag.StudyAccessControlID, VR.LO,
                    results.get(study.get(Study_.accessControlID)));
        attrs.setString(PrivateTag.PrivateCreator, PrivateTag.StorageIDsOfStudy, VR.LO,
                StringUtils.split(results.get(study.get(Study_.storageIDs)), '\\'));
        attrs.setInt(PrivateTag.PrivateCreator, PrivateTag.StudySizeInKB, VR.UL, (int) (studySize / 1000));
        attrs.setInt(PrivateTag.PrivateCreator, PrivateTag.StudySizeBytes, VR.US, (int) (studySize % 1000));
    }

    @Override
    public boolean isOptionalKeysNotSupported() {
        //TODO
        return false;
    }
}
