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
class SeriesQuery extends AbstractQuery {

    private Root<Series> series;
    private Join<Series, Study> study;
    private Join<Study, Patient> patient;
    private Join<Series, Metadata> metadata;
    private CollectionJoin<Study, StudyQueryAttributes> studyQueryAttributes;
    private CollectionJoin<Series, SeriesQueryAttributes> seriesQueryAttributes;
    private Path<byte[]> patientAttrBlob;
    private Path<byte[]> studyAttrBlob;
    private Path<byte[]> seriesAttrBlob;
    private Long studyPk;
    private Attributes studyAttrs;

    SeriesQuery(QueryContext context, EntityManager em) {
        super(context, em);
    }

    @Override
    protected CriteriaQuery<Tuple> multiselect() {
        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        this.series = q.from(Series.class);
        this.study = series.join(Series_.study);
        this.patient = study.join(Study_.patient);
        this.metadata = series.join(Series_.metadata, JoinType.LEFT);
        this.studyQueryAttributes = QueryBuilder.joinStudyQueryAttributes(cb, study,
                context.getQueryParam().getViewID());
        this.seriesQueryAttributes = QueryBuilder.joinSeriesQueryAttributes(cb, series,
                context.getQueryParam().getViewID());
        return order(restrict(q, patient, study, series)).multiselect(
                study.get(Study_.pk),
                series.get(Series_.pk),
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
                series.get(Series_.externalRetrieveAET),
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
                studyQueryAttributes.get(StudyQueryAttributes_.numberOfInstances),
                studyQueryAttributes.get(StudyQueryAttributes_.numberOfSeries),
                studyQueryAttributes.get(StudyQueryAttributes_.modalitiesInStudy),
                studyQueryAttributes.get(StudyQueryAttributes_.sopClassesInStudy),
                seriesQueryAttributes.get(SeriesQueryAttributes_.numberOfInstances),
                seriesQueryAttributes.get(SeriesQueryAttributes_.retrieveAETs),
                seriesQueryAttributes.get(SeriesQueryAttributes_.availability),
                patientAttrBlob = patient.join(Patient_.attributesBlob).get(AttributesBlob_.encodedAttributes),
                studyAttrBlob = study.join(Study_.attributesBlob).get(AttributesBlob_.encodedAttributes),
                seriesAttrBlob = series.join(Series_.attributesBlob).get(AttributesBlob_.encodedAttributes));
    }

    @Override
    protected CriteriaQuery<Long> count() {
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        Root<Series> series = q.from(Series.class);
        Join<Series, Study> study = series.join(Series_.study);
        Join<Study, Patient> patient = study.join(Study_.patient);
        return restrict(q, patient, study, series).select(cb.count(patient));
    }

    @Override
    protected Attributes toAttributes(Tuple results) {
        Long studyPk = results.get(study.get(Study_.pk));
        Long seriesPk = results.get(series.get(Series_.pk));
        Integer numberOfInstancesI = results.get(seriesQueryAttributes.get(SeriesQueryAttributes_.numberOfInstances));
        int numberOfSeriesRelatedInstances;
        String retrieveAETs;
        Availability availability;
        QueryParam queryParam = context.getQueryParam();
        if (numberOfInstancesI != null) {
            numberOfSeriesRelatedInstances = numberOfInstancesI;
            if (numberOfSeriesRelatedInstances == 0) {
                return null;
            }
            retrieveAETs = results.get(seriesQueryAttributes.get(SeriesQueryAttributes_.retrieveAETs));
            availability = results.get(seriesQueryAttributes.get(SeriesQueryAttributes_.availability));
        } else {
            SeriesQueryAttributes seriesView = context.getQueryService()
                    .calculateSeriesQueryAttributesIfNotExists(seriesPk, queryParam.getQueryRetrieveView());
            numberOfSeriesRelatedInstances = seriesView.getNumberOfInstances();
            if (numberOfSeriesRelatedInstances == 0) {
                return null;
            }
            retrieveAETs = seriesView.getRetrieveAETs();
            availability = seriesView.getAvailability();
        }

        if (!studyPk.equals(this.studyPk)) {
            this.studyAttrs = toStudyAttributes(studyPk, results);
            this.studyPk = studyPk;
        }
        Attributes seriesAttrs = AttributesBlob.decodeAttributes(results.get(seriesAttrBlob), null);
        Attributes.unifyCharacterSets(studyAttrs, seriesAttrs);
        Attributes attrs = new Attributes(studyAttrs.size() + seriesAttrs.size() + 20);
        attrs.addAll(studyAttrs);
        attrs.addAll(seriesAttrs, true);
        String externalRetrieveAET = results.get(series.get(Series_.externalRetrieveAET));
        attrs.setString(Tag.RetrieveAETitle, VR.AE, retrieveAETs(retrieveAETs, externalRetrieveAET));
        attrs.setString(Tag.InstanceAvailability, VR.CS,
                StringUtils.maskNull(availability, Availability.UNAVAILABLE).toString());
        addSeriesQRAttrs(series, metadata, context, results, numberOfSeriesRelatedInstances, attrs);
        return attrs;
    }

    private CriteriaQuery<Tuple> order(CriteriaQuery<Tuple> q) {
        if (context.getOrderByTags() != null)
            q.orderBy(builder.orderSeries(patient, study, series, context.getOrderByTags()));
        return q;
    }

    private <T> CriteriaQuery<T> restrict(CriteriaQuery<T> q, Join<Study, Patient> patient,
            Join<Series, Study> study, Root<Series> series) {
        List<Predicate> predicates = builder.seriesPredicates(q, patient, study, series,
                context.getPatientIDs(),
                context.getQueryKeys(),
                context.getQueryParam());
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));
        return q;
    }

    static void addSeriesQRAttrs(Root<Series> series, Join<Series, Metadata> metadata, QueryContext context,
            Tuple results, int numberOfSeriesRelatedInstances, Attributes attrs) {
        attrs.setInt(Tag.NumberOfSeriesRelatedInstances, VR.IS, numberOfSeriesRelatedInstances);
        if (!context.isReturnPrivate())
            return;

        attrs.setDate(PrivateTag.PrivateCreator, PrivateTag.SeriesReceiveDateTime, VR.DT,
                results.get(series.get(Series_.createdTime)));
        attrs.setDate(PrivateTag.PrivateCreator, PrivateTag.SeriesUpdateDateTime, VR.DT,
                results.get(series.get(Series_.updatedTime)));
        attrs.setString(PrivateTag.PrivateCreator, PrivateTag.SeriesExpirationState, VR.CS,
                results.get(series.get(Series_.expirationState)).toString());
        if (results.get(series.get(Series_.expirationDate)) != null)
            attrs.setString(PrivateTag.PrivateCreator, PrivateTag.SeriesExpirationDate, VR.DA,
                    results.get(series.get(Series_.expirationDate)));
        if (results.get(series.get(Series_.expirationExporterID)) != null)
            attrs.setString(PrivateTag.PrivateCreator, PrivateTag.SeriesExpirationExporterID, VR.LO,
                    results.get(series.get(Series_.expirationExporterID)));
        attrs.setString(PrivateTag.PrivateCreator, PrivateTag.SeriesRejectionState, VR.CS,
                results.get(series.get(Series_.rejectionState)).toString());
        attrs.setString(PrivateTag.PrivateCreator, PrivateTag.SeriesCompleteness, VR.CS,
                results.get(series.get(Series_.completeness)).toString());
        if (results.get(series.get(Series_.failedRetrieves)) != 0)
            attrs.setInt(PrivateTag.PrivateCreator, PrivateTag.FailedRetrievesOfSeries, VR.US,
                    results.get(series.get(Series_.failedRetrieves)));
        attrs.setString(PrivateTag.PrivateCreator, PrivateTag.SendingApplicationEntityTitleOfSeries, VR.AE,
                results.get(series.get(Series_.sourceAET)));
        if (results.get(series.get(Series_.metadataScheduledUpdateTime))!= null)
            attrs.setDate(PrivateTag.PrivateCreator, PrivateTag.ScheduledMetadataUpdateDateTimeOfSeries, VR.DT,
                    results.get(series.get(Series_.metadataScheduledUpdateTime)));
        if (results.get(series.get(Series_.metadataUpdateFailures))!= 0)
            attrs.setInt(PrivateTag.PrivateCreator, PrivateTag.SeriesMetadataUpdateFailures, VR.US,
                    results.get(series.get(Series_.metadataUpdateFailures)));
        if (results.get(series.get(Series_.instancePurgeTime)) != null)
            attrs.setDate(PrivateTag.PrivateCreator, PrivateTag.ScheduledInstanceRecordPurgeDateTimeOfSeries, VR.DT,
                    results.get(series.get(Series_.instancePurgeTime)));
        attrs.setString(PrivateTag.PrivateCreator, PrivateTag.InstanceRecordPurgeStateOfSeries, VR.CS,
                results.get(series.get(Series_.instancePurgeState)).name());
        if (results.get(series.get(Series_.storageVerificationTime)) != null)
            attrs.setDate(PrivateTag.PrivateCreator, PrivateTag.ScheduledStorageVerificationDateTimeOfSeries, VR.DT,
                    results.get(series.get(Series_.storageVerificationTime)));
        if (results.get(series.get(Series_.failuresOfLastStorageVerification)) != 0)
            attrs.setInt(PrivateTag.PrivateCreator, PrivateTag.FailuresOfLastStorageVerificationOfSeries, VR.US,
                    results.get(series.get(Series_.failuresOfLastStorageVerification)));
        if (results.get(series.get(Series_.compressionTime)) != null)
            attrs.setDate(PrivateTag.PrivateCreator, PrivateTag.ScheduledCompressionDateTimeOfSeries, VR.DT,
                    results.get(series.get(Series_.compressionTime)));
        if (results.get(series.get(Series_.compressionFailures)) != 0)
            attrs.setInt(PrivateTag.PrivateCreator, PrivateTag.FailuresOfLastCompressionOfSeries, VR.US,
                    results.get(series.get(Series_.compressionFailures)));
        if (results.get(metadata.get(Metadata_.storageID)) != null) {
            attrs.setDate(PrivateTag.PrivateCreator, PrivateTag.SeriesMetadataCreationDateTime, VR.DT,
                    results.get(metadata.get(Metadata_.createdTime)));
            attrs.setString(PrivateTag.PrivateCreator, PrivateTag.SeriesMetadataStorageID, VR.LO,
                    results.get(metadata.get(Metadata_.storageID)));
            attrs.setString(PrivateTag.PrivateCreator, PrivateTag.SeriesMetadataStoragePath, VR.LO,
                    StringUtils.split(results.get(metadata.get(Metadata_.storagePath)), '/'));
            attrs.setInt(PrivateTag.PrivateCreator, PrivateTag.SeriesMetadataStorageObjectSize, VR.UL,
                    results.get(metadata.get(Metadata_.size)).intValue());
            if (results.get(metadata.get(Metadata_.digest)) != null)
                attrs.setString(PrivateTag.PrivateCreator, PrivateTag.SeriesMetadataStorageObjectDigest, VR.LO,
                        results.get(metadata.get(Metadata_.digest)));
            if (results.get(metadata.get(Metadata_.status)) != Metadata.Status.OK)
                attrs.setString(PrivateTag.PrivateCreator, PrivateTag.SeriesMetadataStorageObjectStatus, VR.CS,
                        results.get(metadata.get(Metadata_.status)).name());
        }
    }

    private Attributes toStudyAttributes(Long studyPk, Tuple results) {
        long studySize = results.get(study.get(Study_.size));
        if (studySize < 0)
            studySize = context.getQueryService().calculateStudySize(studyPk);
        Integer numberOfInstancesI = results.get(studyQueryAttributes.get(StudyQueryAttributes_.numberOfInstances));
        int numberOfStudyRelatedInstances;
        int numberOfStudyRelatedSeries;
        String modalitiesInStudy;
        String sopClassesInStudy;
        if (numberOfInstancesI != null) {
            numberOfStudyRelatedInstances = numberOfInstancesI;
            numberOfStudyRelatedSeries = results.get(studyQueryAttributes.get(StudyQueryAttributes_.numberOfSeries));
            modalitiesInStudy = results.get(studyQueryAttributes.get(StudyQueryAttributes_.modalitiesInStudy));
            sopClassesInStudy = results.get(studyQueryAttributes.get(StudyQueryAttributes_.sopClassesInStudy));
        } else {
            StudyQueryAttributes studyView = context.getQueryService()
                    .calculateStudyQueryAttributes(studyPk, context.getQueryParam().getQueryRetrieveView());
            numberOfStudyRelatedInstances = studyView.getNumberOfInstances();
            numberOfStudyRelatedSeries = studyView.getNumberOfSeries();
            modalitiesInStudy = studyView.getModalitiesInStudy();
            sopClassesInStudy = studyView.getSOPClassesInStudy();
        }

        Attributes studyAttrs = AttributesBlob.decodeAttributes(results.get(studyAttrBlob), null);
        Attributes patAttrs = AttributesBlob.decodeAttributes(results.get(patientAttrBlob), null);
        Attributes.unifyCharacterSets(patAttrs, studyAttrs);
        Attributes attrs = new Attributes(patAttrs.size() + studyAttrs.size() + 20);
        attrs.addAll(patAttrs);
        attrs.addAll(studyAttrs, true);
        PatientQuery.addPatientQRAttrs(patient, context,results, attrs);
        StudyQuery.addStudyQRAddrs(study, context, results, studySize,
                numberOfStudyRelatedInstances, numberOfStudyRelatedSeries,
                modalitiesInStudy, sopClassesInStudy, attrs);
        return attrs;
    }

    @Override
    public boolean isOptionalKeysNotSupported() {
        //TODO
        return false;
    }
}
