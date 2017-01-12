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
import org.dcm4che3.dict.archive.ArchiveTag;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.Availability;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.query.util.QueryBuilder;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.query.util.QueryParam;
import org.hibernate.StatelessSession;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Aug 2015
 */
class SeriesQuery extends AbstractQuery {

    private static final Expression<?>[] SELECT = {
            QStudy.study.pk,
            QSeries.series.pk,
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
            QSeries.series.externalRetrieveAET,
            QSeries.series.metadataScheduledUpdateTime,
            QSeries.series.instancePurgeTime,
            QSeries.series.instancePurgeState,
            QMetadata.metadata.storageID,
            QMetadata.metadata.storagePath,
            QMetadata.metadata.digest,
            QMetadata.metadata.size,
            QSeriesQueryAttributes.seriesQueryAttributes.numberOfInstances,
            QStudyQueryAttributes.studyQueryAttributes.numberOfInstances,
            QStudyQueryAttributes.studyQueryAttributes.numberOfSeries,
            QStudyQueryAttributes.studyQueryAttributes.modalitiesInStudy,
            QStudyQueryAttributes.studyQueryAttributes.sopClassesInStudy,
            QSeriesQueryAttributes.seriesQueryAttributes.retrieveAETs,
            QSeriesQueryAttributes.seriesQueryAttributes.availability,
            QueryBuilder.seriesAttributesBlob.encodedAttributes,
            QueryBuilder.studyAttributesBlob.encodedAttributes,
            QueryBuilder.patientAttributesBlob.encodedAttributes 
    };

    private Long studyPk;
    private Attributes studyAttrs;

    public SeriesQuery(QueryContext context, StatelessSession session) {
        super(context, session);
    }

    @Override
    protected HibernateQuery<Tuple> newHibernateQuery() {
        HibernateQuery<Tuple> q = new HibernateQuery<Void>(session).select(SELECT).from(QSeries.series);
        q = QueryBuilder.applySeriesLevelJoins(q,
                context.getQueryKeys(),
                context.getQueryParam());
        q = QueryBuilder.applyStudyLevelJoins(q,
                context.getQueryKeys(),
                context.getQueryParam());
        q = QueryBuilder.applyPatientLevelJoins(q,
                context.getPatientIDs(),
                context.getQueryKeys(),
                context.getQueryParam(),
                context.isOrderByPatientName());
        q = q.leftJoin(QSeries.series.metadata, QMetadata.metadata);
        BooleanBuilder predicates = new BooleanBuilder();
        QueryBuilder.addPatientLevelPredicates(predicates,
                context.getPatientIDs(),
                context.getQueryKeys(),
                context.getQueryParam());
        QueryBuilder.addStudyLevelPredicates(predicates,
                context.getQueryKeys(),
                context.getQueryParam(), QueryRetrieveLevel2.SERIES);
        QueryBuilder.addSeriesLevelPredicates(predicates,
                context.getQueryKeys(),
                context.getQueryParam(), QueryRetrieveLevel2.SERIES);
        return q.where(predicates);
    }

    @Override
    protected Attributes toAttributes(Tuple results) {
        Long studyPk = results.get(QStudy.study.pk);
        Long seriesPk = results.get(QSeries.series.pk);
        Integer numberOfInstancesI = results.get(QSeriesQueryAttributes.seriesQueryAttributes.numberOfInstances);
        int numberOfSeriesRelatedInstances;
        String retrieveAETs;
        Availability availability;
        QueryParam queryParam = context.getQueryParam();
        if (numberOfInstancesI != null) {
            numberOfSeriesRelatedInstances = numberOfInstancesI;
            if (numberOfSeriesRelatedInstances == 0 && !queryParam.isReturnEmpty()) {
                return null;
            }
            retrieveAETs = results.get(QSeriesQueryAttributes.seriesQueryAttributes.retrieveAETs);
            availability = results.get(QSeriesQueryAttributes.seriesQueryAttributes.availability);
        } else {
            SeriesQueryAttributes seriesView = context.getQueryService()
                    .calculateSeriesQueryAttributesIfNotExists(seriesPk, queryParam);
            numberOfSeriesRelatedInstances = seriesView.getNumberOfInstances();
            if (numberOfSeriesRelatedInstances == 0 && !queryParam.isReturnEmpty()) {
                return null;
            }
            retrieveAETs = seriesView.getRetrieveAETs();
            availability = seriesView.getAvailability();
        }

        if (!studyPk.equals(this.studyPk)) {
            this.studyAttrs = toStudyAttributes(studyPk, results);
            this.studyPk = studyPk;
        }
        Attributes seriesAttrs = AttributesBlob.decodeAttributes(
                results.get(QueryBuilder.seriesAttributesBlob.encodedAttributes), null);
        Attributes.unifyCharacterSets(studyAttrs, seriesAttrs);
        Attributes attrs = new Attributes(studyAttrs.size() + seriesAttrs.size() + 3);
        attrs.addAll(studyAttrs);
        attrs.addAll(seriesAttrs);
        String externalRetrieveAET = results.get(QSeries.series.externalRetrieveAET);
        attrs.setString(Tag.RetrieveAETitle, VR.AE, splitAndAppend(retrieveAETs, externalRetrieveAET));
        attrs.setString(Tag.InstanceAvailability, VR.CS,
            StringUtils.maskNull(availability, Availability.UNAVAILABLE).toString());
        attrs.setInt(Tag.NumberOfSeriesRelatedInstances, VR.IS, numberOfSeriesRelatedInstances);
        attrs.setDate(ArchiveTag.PrivateCreator, ArchiveTag.SeriesReceiveDateTime, VR.DT,
                results.get(QSeries.series.createdTime));
        attrs.setDate(ArchiveTag.PrivateCreator, ArchiveTag.SeriesUpdateDateTime, VR.DT,
                results.get(QSeries.series.updatedTime));
        if (results.get(QSeries.series.expirationDate) != null)
            attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.SeriesExpirationDate, VR.DA,
                    results.get(QSeries.series.expirationDate));
        attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.SeriesRejectionState, VR.CS,
                results.get(QSeries.series.rejectionState).toString());
        if (results.get(QSeries.series.failedSOPInstanceUIDList) != null)
            attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.MissingSOPInstanceUIDListOfSeries, VR.UI,
                    results.get(QSeries.series.failedSOPInstanceUIDList));
        if (results.get(QSeries.series.failedRetrieves) != 0)
            attrs.setInt(ArchiveTag.PrivateCreator, ArchiveTag.FailedRetrievesOfSeries, VR.US,
                    results.get(QSeries.series.failedRetrieves));
        attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.SendingApplicationEntityTitleOfSeries, VR.AE,
                results.get(QSeries.series.sourceAET));
        if (results.get(QSeries.series.metadataScheduledUpdateTime) != null)
            attrs.setDate(ArchiveTag.PrivateCreator, ArchiveTag.ScheduledMetadataUpdateDateTimeOfSeries, VR.DT,
                    results.get(QSeries.series.metadataScheduledUpdateTime));
        if (results.get(QSeries.series.instancePurgeTime) != null)
            attrs.setDate(ArchiveTag.PrivateCreator, ArchiveTag.ScheduledInstanceRecordPurgeDateTimeOfSeries, VR.DT,
                    results.get(QSeries.series.instancePurgeTime));
        attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.InstanceRecordPurgeStateOfSeries, VR.CS,
                results.get(QSeries.series.instancePurgeState).name());
        if (results.get(QMetadata.metadata.storageID) != null) {
            attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.SeriesMetadataStorageID, VR.LO,
                    results.get(QMetadata.metadata.storageID));
            attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.SeriesMetadataStoragePath, VR.LO,
                    StringUtils.split(results.get(QMetadata.metadata.storagePath), '/'));
            attrs.setInt(ArchiveTag.PrivateCreator, ArchiveTag.SeriesMetadataStorageObjectSize, VR.UL,
                    results.get(QMetadata.metadata.size).intValue());
            if (results.get(QMetadata.metadata.digest) != null)
                attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.SeriesMetadataStorageObjectDigest, VR.LO,
                        results.get(QMetadata.metadata.digest));
        }
        return attrs;
    }

    private Attributes toStudyAttributes(Long studyPk, Tuple results) {
        Integer numberOfInstancesI = results.get(QStudyQueryAttributes.studyQueryAttributes.numberOfInstances);
        int numberOfStudyRelatedInstances;
        int numberOfStudyRelatedSeries;
        String modalitiesInStudy;
        String sopClassesInStudy;
        if (numberOfInstancesI != null) {
            numberOfStudyRelatedInstances = numberOfInstancesI;
            numberOfStudyRelatedSeries = results.get(QStudyQueryAttributes.studyQueryAttributes.numberOfSeries);
            modalitiesInStudy = results.get(QStudyQueryAttributes.studyQueryAttributes.modalitiesInStudy);
            sopClassesInStudy = results.get(QStudyQueryAttributes.studyQueryAttributes.sopClassesInStudy);
        } else {
            StudyQueryAttributes studyView = context.getQueryService()
                    .calculateStudyQueryAttributes(studyPk, context.getQueryParam());
            numberOfStudyRelatedInstances = studyView.getNumberOfInstances();
            numberOfStudyRelatedSeries = studyView.getNumberOfSeries();
            modalitiesInStudy = studyView.getModalitiesInStudy();
            sopClassesInStudy = studyView.getSOPClassesInStudy();
        }

        Attributes patAttrs = AttributesBlob.decodeAttributes(
                results.get(QueryBuilder.patientAttributesBlob.encodedAttributes), null);
        Attributes studyAttrs = AttributesBlob.decodeAttributes(
                results.get(QueryBuilder.studyAttributesBlob.encodedAttributes), null);
        Attributes.unifyCharacterSets(patAttrs, studyAttrs);
        Attributes attrs = new Attributes(patAttrs.size() + studyAttrs.size() + 4);
        attrs.addAll(patAttrs);
        attrs.addAll(studyAttrs);
        attrs.setString(Tag.ModalitiesInStudy, VR.CS, modalitiesInStudy);
        attrs.setString(Tag.SOPClassesInStudy, VR.UI, sopClassesInStudy);
        attrs.setInt(Tag.NumberOfPatientRelatedStudies, VR.IS, results.get(QPatient.patient.numberOfStudies));
        attrs.setInt(Tag.NumberOfStudyRelatedSeries, VR.IS, numberOfStudyRelatedSeries);
        attrs.setInt(Tag.NumberOfStudyRelatedInstances, VR.IS, numberOfStudyRelatedInstances);
        attrs.setDate(ArchiveTag.PrivateCreator, ArchiveTag.PatientCreateDateTime, VR.DT,
                results.get(QPatient.patient.createdTime));
        attrs.setDate(ArchiveTag.PrivateCreator, ArchiveTag.PatientUpdateDateTime, VR.DT,
                results.get(QPatient.patient.updatedTime));
        attrs.setDate(ArchiveTag.PrivateCreator, ArchiveTag.StudyReceiveDateTime, VR.DT,
                results.get(QStudy.study.createdTime));
        attrs.setDate(ArchiveTag.PrivateCreator, ArchiveTag.StudyUpdateDateTime, VR.DT,
                results.get(QStudy.study.updatedTime));
        attrs.setDate(ArchiveTag.PrivateCreator, ArchiveTag.StudyAccessDateTime, VR.DT,
                results.get(QStudy.study.accessTime));
        if (results.get(QStudy.study.expirationDate) != null)
            attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.StudyExpirationDate, VR.DA,
                    results.get(QStudy.study.expirationDate));
        attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.StudyRejectionState, VR.CS,
                results.get(QStudy.study.rejectionState).toString());
        if (results.get(QStudy.study.failedSOPInstanceUIDList) != null)
            attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.MissingSOPInstanceUIDListOfStudy, VR.UI,
                    results.get(QStudy.study.failedSOPInstanceUIDList));
        if (results.get(QStudy.study.failedRetrieves) != 0)
            attrs.setInt(ArchiveTag.PrivateCreator, ArchiveTag.FailedRetrievesOfStudy, VR.US,
                    results.get(QStudy.study.failedRetrieves));
        attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.StudyAccessControlID, VR.LO,
                results.get(QStudy.study.accessControlID));
        attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.StorageIDsOfStudy, VR.LO,
                results.get(QStudy.study.storageIDs));
        return attrs;
    }

    @Override
    public boolean isOptionalKeysNotSupported() {
        //TODO
        return false;
    }
}
