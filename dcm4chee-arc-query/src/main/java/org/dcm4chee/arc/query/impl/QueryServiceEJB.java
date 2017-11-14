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
 * Portions created by the Initial Developer are Copyright (C) 2017
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


import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Predicate;
import com.querydsl.jpa.hibernate.HibernateQuery;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.dict.archive.ArchiveTag;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.util.UIDUtils;
import org.dcm4chee.arc.conf.Availability;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.query.util.QueryBuilder;
import org.dcm4chee.arc.query.util.QueryParam;
import org.hibernate.Session;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import java.util.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 *
 */
@Stateless
public class QueryServiceEJB {

    static final Expression<?>[] PATIENT_STUDY_SERIES_ATTRS = {
        QStudy.study.pk,
        QPatient.patient.numberOfStudies,
        QPatient.patient.createdTime,
        QPatient.patient.updatedTime,
        QStudy.study.createdTime,
        QStudy.study.updatedTime,
        QStudy.study.accessTime,
        QStudy.study.expirationDate,
        QStudy.study.rejectionState,
        QStudy.study.completeness,
        QStudy.study.failedRetrieves,
        QStudy.study.accessControlID,
        QStudy.study.storageIDs,
        QStudy.study.size,
        QSeries.series.createdTime,
        QSeries.series.updatedTime,
        QSeries.series.expirationDate,
        QSeries.series.rejectionState,
        QSeries.series.completeness,
        QSeries.series.failedRetrieves,
        QSeries.series.sourceAET,
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
        QueryBuilder.seriesAttributesBlob.encodedAttributes,
        QueryBuilder.studyAttributesBlob.encodedAttributes,
        QueryBuilder.patientAttributesBlob.encodedAttributes
    };

    static final Expression<?>[] SOP_REFS_OF_STUDY = {
            QStudy.study.pk,
            QSeries.series.pk,
            QSeries.series.seriesInstanceUID,
            QInstance.instance.sopInstanceUID,
            QInstance.instance.sopClassUID,
            QInstance.instance.retrieveAETs,
            QInstance.instance.availability
    };

    static final Expression<?>[] PATIENT_STUDY_ATTRS = {
            QueryBuilder.studyAttributesBlob.encodedAttributes,
            QueryBuilder.patientAttributesBlob.encodedAttributes
    };

    static final Expression<?>[] EXPORT_STUDY_INFO = {
            QStudy.study.pk,
            QStudyQueryAttributes.studyQueryAttributes.numberOfInstances,
            QStudyQueryAttributes.studyQueryAttributes.modalitiesInStudy
    };

    static final Expression<?>[] EXPORT_SERIES_INFO = {
            QSeries.series.pk,
            QSeries.series.modality,
            QSeriesQueryAttributes.seriesQueryAttributes.numberOfInstances
    };

    @PersistenceContext(unitName = "dcm4chee-arc")
    EntityManager em;

    @Inject
    QuerySizeEJB querySizeEJB;

    @Inject
    QueryAttributesEJB queryAttributesEJB;

    public Attributes getSeriesAttributes(Long seriesPk, QueryParam queryParam) {
        String viewID = queryParam.getViewID();
        Tuple result = new HibernateQuery<Void>(em.unwrap(Session.class))
                .select(PATIENT_STUDY_SERIES_ATTRS)
                .from(QSeries.series)
                .join(QSeries.series.attributesBlob, QueryBuilder.seriesAttributesBlob)
                .leftJoin(QSeries.series.queryAttributes, QSeriesQueryAttributes.seriesQueryAttributes)
                .on(QSeriesQueryAttributes.seriesQueryAttributes.viewID.eq(viewID))
                .leftJoin(QSeries.series.metadata, QMetadata.metadata)
                .join(QSeries.series.study, QStudy.study)
                .join(QStudy.study.attributesBlob, QueryBuilder.studyAttributesBlob)
                .leftJoin(QStudy.study.queryAttributes, QStudyQueryAttributes.studyQueryAttributes)
                .on(QStudyQueryAttributes.studyQueryAttributes.viewID.eq(viewID))
                .join(QStudy.study.patient, QPatient.patient)
                .join(QPatient.patient.attributesBlob, QueryBuilder.patientAttributesBlob)
                .where(QSeries.series.pk.eq(seriesPk))
                .fetchOne();

        Long studySize = result.get(QStudy.study.size);
        if (studySize < 0)
            studySize = querySizeEJB.calculateStudySize(result.get(QStudy.study.pk));
        Integer numberOfSeriesRelatedInstances =
                result.get(QSeriesQueryAttributes.seriesQueryAttributes.numberOfInstances);
        if (numberOfSeriesRelatedInstances == null) {
            SeriesQueryAttributes seriesQueryAttributes =
                    queryAttributesEJB.calculateSeriesQueryAttributes(
                            seriesPk,
                            queryParam.getQueryRetrieveView(),
                            queryParam.getHideRejectionNotesWithCode(),
                            queryParam.getShowInstancesRejectedByCode());
            numberOfSeriesRelatedInstances = seriesQueryAttributes.getNumberOfInstances();
        }

        int numberOfStudyRelatedSeries;
        String modalitiesInStudy;
        String sopClassesInStudy;
        Integer numberOfStudyRelatedInstances =
                result.get(QStudyQueryAttributes.studyQueryAttributes.numberOfInstances);
        if (numberOfStudyRelatedInstances == null) {
            StudyQueryAttributes studyQueryAttributes =
                    queryAttributesEJB.calculateStudyQueryAttributes(result.get(QStudy.study.pk), queryParam);
            numberOfStudyRelatedInstances = studyQueryAttributes.getNumberOfInstances();
            numberOfStudyRelatedSeries = studyQueryAttributes.getNumberOfSeries();
            modalitiesInStudy = studyQueryAttributes.getModalitiesInStudy();
            sopClassesInStudy = studyQueryAttributes.getSOPClassesInStudy();
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
        attrs.setInt(Tag.NumberOfPatientRelatedStudies, VR.IS, result.get(QPatient.patient.numberOfStudies));
        attrs.setInt(Tag.NumberOfStudyRelatedSeries, VR.IS, numberOfStudyRelatedSeries);
        attrs.setInt(Tag.NumberOfStudyRelatedInstances, VR.IS, numberOfStudyRelatedInstances);
        attrs.setInt(Tag.NumberOfSeriesRelatedInstances, VR.IS, numberOfSeriesRelatedInstances);
        attrs.setDate(ArchiveTag.PrivateCreator, ArchiveTag.PatientCreateDateTime, VR.DT,
                result.get(QPatient.patient.createdTime));
        attrs.setDate(ArchiveTag.PrivateCreator, ArchiveTag.PatientUpdateDateTime, VR.DT,
                result.get(QPatient.patient.updatedTime));
        attrs.setDate(ArchiveTag.PrivateCreator, ArchiveTag.StudyReceiveDateTime, VR.DT,
                result.get(QStudy.study.createdTime));
        attrs.setDate(ArchiveTag.PrivateCreator, ArchiveTag.StudyUpdateDateTime, VR.DT,
                result.get(QStudy.study.updatedTime));
        attrs.setDate(ArchiveTag.PrivateCreator, ArchiveTag.StudyAccessDateTime, VR.DT,
                result.get(QStudy.study.accessTime));
        if (result.get(QStudy.study.expirationDate) != null)
            attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.StudyExpirationDate, VR.DA,
                    result.get(QStudy.study.expirationDate));
        attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.StudyRejectionState, VR.CS,
                result.get(QStudy.study.rejectionState).toString());
        attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.StudyCompleteness, VR.CS,
                result.get(QStudy.study.completeness).toString());
        if (result.get(QStudy.study.failedRetrieves) != 0)
            attrs.setInt(ArchiveTag.PrivateCreator, ArchiveTag.FailedRetrievesOfStudy, VR.US,
                    result.get(QStudy.study.failedRetrieves));
        if (!result.get(QStudy.study.accessControlID).equals("*"))
            attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.StudyAccessControlID, VR.LO,
                    result.get(QStudy.study.accessControlID));
        attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.StorageIDsOfStudy, VR.LO,
                result.get(QStudy.study.storageIDs));
        attrs.setInt(ArchiveTag.PrivateCreator, ArchiveTag.StudySizeInKB, VR.UL, (int) (studySize / 1000));
        attrs.setInt(ArchiveTag.PrivateCreator, ArchiveTag.StudySizeBytes, VR.US, (int) (studySize % 1000));
        attrs.setDate(ArchiveTag.PrivateCreator, ArchiveTag.SeriesReceiveDateTime, VR.DT,
                result.get(QSeries.series.createdTime));
        attrs.setDate(ArchiveTag.PrivateCreator, ArchiveTag.SeriesUpdateDateTime, VR.DT,
                result.get(QSeries.series.updatedTime));
        if (result.get(QSeries.series.expirationDate) != null)
            attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.SeriesExpirationDate, VR.DA,
                    result.get(QSeries.series.expirationDate));
        attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.SeriesRejectionState, VR.CS,
                result.get(QSeries.series.rejectionState).toString());
        attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.SeriesCompleteness, VR.CS,
                result.get(QSeries.series.completeness).toString());
        if (result.get(QSeries.series.failedRetrieves) != 0)
            attrs.setInt(ArchiveTag.PrivateCreator, ArchiveTag.FailedRetrievesOfSeries, VR.US,
                    result.get(QSeries.series.failedRetrieves));
        attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.SendingApplicationEntityTitleOfSeries, VR.AE,
                result.get(QSeries.series.sourceAET));
        if (result.get(QSeries.series.metadataScheduledUpdateTime) != null)
            attrs.setDate(ArchiveTag.PrivateCreator, ArchiveTag.ScheduledMetadataUpdateDateTimeOfSeries, VR.DT,
                    result.get(QSeries.series.metadataScheduledUpdateTime));
        if (result.get(QSeries.series.instancePurgeTime) != null)
            attrs.setDate(ArchiveTag.PrivateCreator, ArchiveTag.ScheduledInstanceRecordPurgeDateTimeOfSeries, VR.DT,
                    result.get(QSeries.series.instancePurgeTime));
        attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.InstanceRecordPurgeStateOfSeries, VR.CS,
                result.get(QSeries.series.instancePurgeState).name());
        if (result.get(QMetadata.metadata.storageID) != null) {
            attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.SeriesMetadataStorageID, VR.LO,
                    result.get(QMetadata.metadata.storageID));
            attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.SeriesMetadataStoragePath, VR.LO,
                    StringUtils.split(result.get(QMetadata.metadata.storagePath), '/'));
            attrs.setInt(ArchiveTag.PrivateCreator, ArchiveTag.SeriesMetadataStorageObjectSize, VR.UL,
                    result.get(QMetadata.metadata.size).intValue());
            if (result.get(QMetadata.metadata.digest) != null)
                attrs.setString(ArchiveTag.PrivateCreator, ArchiveTag.SeriesMetadataStorageObjectDigest, VR.LO,
                        result.get(QMetadata.metadata.digest));
        }
        return attrs;
    }

    public Attributes queryStudyExportTaskInfo(String studyIUID, QueryParam queryParam) {
        String viewID = queryParam.getViewID();
        Tuple result = new HibernateQuery<Void>(em.unwrap(Session.class))
                .select(EXPORT_STUDY_INFO)
                .from(QStudy.study)
                .leftJoin(QStudy.study.queryAttributes, QStudyQueryAttributes.studyQueryAttributes)
                .on(QStudyQueryAttributes.studyQueryAttributes.viewID.eq(viewID))
                .where(QStudy.study.studyInstanceUID.eq(studyIUID))
                .fetchOne();
        String modalitiesInStudy;
        Integer numberOfStudyRelatedInstances =
                result.get(QStudyQueryAttributes.studyQueryAttributes.numberOfInstances);
        if (numberOfStudyRelatedInstances == null) {
            StudyQueryAttributes studyQueryAttributes =
                    queryAttributesEJB.calculateStudyQueryAttributes(result.get(QStudy.study.pk), queryParam);
            numberOfStudyRelatedInstances = studyQueryAttributes.getNumberOfInstances();
            modalitiesInStudy = studyQueryAttributes.getModalitiesInStudy();
        } else {
            modalitiesInStudy =
                    result.get(QStudyQueryAttributes.studyQueryAttributes.modalitiesInStudy);
        }
        Attributes attrs = new Attributes(2);
        attrs.setInt(Tag.NumberOfStudyRelatedInstances, VR.IS, numberOfStudyRelatedInstances);
        attrs.setString(Tag.ModalitiesInStudy, VR.CS, modalitiesInStudy);
        return attrs;
    }

    public Attributes querySeriesExportTaskInfo(String studyIUID, String seriesIUID, QueryParam queryParam) {
        String viewID = queryParam.getViewID();
        Tuple result = new HibernateQuery<Void>(em.unwrap(Session.class))
                .select(EXPORT_SERIES_INFO)
                .from(QSeries.series)
                .leftJoin(QSeries.series.queryAttributes, QSeriesQueryAttributes.seriesQueryAttributes)
                .on(QSeriesQueryAttributes.seriesQueryAttributes.viewID.eq(viewID))
                .join(QSeries.series.study, QStudy.study)
                .where(QStudy.study.studyInstanceUID.eq(studyIUID), QSeries.series.seriesInstanceUID.eq(seriesIUID))
                .fetchOne();

        if (result == null)
            return null;
        Integer numberOfSeriesRelatedInstances =
                result.get(QSeriesQueryAttributes.seriesQueryAttributes.numberOfInstances);
        if (numberOfSeriesRelatedInstances == null) {
            Long seriesPk = result.get(QSeries.series.pk);
            SeriesQueryAttributes seriesQueryAttributes =
                    queryAttributesEJB.calculateSeriesQueryAttributes(
                            seriesPk,
                            queryParam.getQueryRetrieveView(),
                            queryParam.getHideRejectionNotesWithCode(),
                            queryParam.getShowInstancesRejectedByCode());
            numberOfSeriesRelatedInstances = seriesQueryAttributes.getNumberOfInstances();
        }
        Attributes attrs = new Attributes(2);
        attrs.setInt(Tag.NumberOfStudyRelatedInstances, VR.IS, numberOfSeriesRelatedInstances);
        attrs.setString(Tag.ModalitiesInStudy, VR.CS, result.get(QSeries.series.modality));
        return attrs;
    }

    public Attributes queryObjectExportTaskInfo(String studyIUID, String seriesIUID, String sopIUID) {
        String modality = new HibernateQuery<Void>(em.unwrap(Session.class))
                .select(QSeries.series.modality)
                .from(QInstance.instance)
                .join(QInstance.instance.series, QSeries.series)
                .join(QSeries.series.study, QStudy.study)
                .where(QStudy.study.studyInstanceUID.eq(studyIUID),
                        QSeries.series.seriesInstanceUID.eq(seriesIUID),
                        QInstance.instance.sopInstanceUID.eq(sopIUID))
                .fetchOne();

        Attributes attrs = new Attributes(2);
        attrs.setInt(Tag.NumberOfStudyRelatedInstances, VR.IS, 1);
        attrs.setString(Tag.ModalitiesInStudy, VR.CS, modality);
        return attrs;
    }

    public enum SOPInstanceRefsType { IAN, KOS_IOCM, KOS_XDSI, STGCMT }

    public Attributes getStudyAttributesWithSOPInstanceRefs(
            SOPInstanceRefsType type, String studyIUID, Predicate predicate,
            Collection<Attributes> seriesAttrs, String[] retrieveAETs, String retrieveLocationUID) {
        Attributes attrs = getStudyAttributes(studyIUID);
        if (attrs == null)
            return null;

        Attributes sopInstanceRefs = getSOPInstanceRefs(
                type, studyIUID, predicate, seriesAttrs, retrieveAETs, retrieveLocationUID, null);
        if (sopInstanceRefs != null)
            attrs.newSequence(Tag.CurrentRequestedProcedureEvidenceSequence, 1).add(sopInstanceRefs);
        return attrs;
    }

    public Attributes getSOPInstanceRefs(
            SOPInstanceRefsType type, String studyIUID, Predicate predicate,
            Collection<Attributes> seriesAttrs, String[] retrieveAETs, String retrieveLocationUID,
            Availability availability) {
        List<Tuple> tuples = new HibernateQuery<Void>(em.unwrap(Session.class))
                .select(SOP_REFS_OF_STUDY)
                .from(QInstance.instance)
                .join(QInstance.instance.series, QSeries.series)
                .join(QSeries.series.study, QStudy.study)
                .where(predicate)
                .fetch();

        if (tuples.isEmpty() && type != SOPInstanceRefsType.KOS_XDSI)
            return null;

        if (type == SOPInstanceRefsType.STGCMT)
            return getStgCmtRqstAttr(tuples);

        Attributes refStudy = new Attributes(2);
        Sequence refSeriesSeq = refStudy.newSequence(Tag.ReferencedSeriesSequence, 10);
        refStudy.setString(Tag.StudyInstanceUID, VR.UI, studyIUID);
        HashMap<Long, Sequence> seriesMap = new HashMap<>();
        for (Tuple tuple : tuples) {
            Long seriesPk = tuple.get(QSeries.series.pk);
            Sequence refSOPSeq = seriesMap.get(seriesPk);
            if (refSOPSeq == null) {
                Attributes refSeries = new Attributes(4);
                if (type == SOPInstanceRefsType.KOS_XDSI) {
                    if (retrieveAETs != null)
                        refSeries.setString(Tag.RetrieveAETitle, VR.AE, retrieveAETs);
                    if (retrieveLocationUID != null)
                        refSeries.setString(Tag.RetrieveLocationUID, VR.UI, retrieveLocationUID);
                }
                refSOPSeq = refSeries.newSequence(Tag.ReferencedSOPSequence, 10);
                refSeries.setString(Tag.SeriesInstanceUID, VR.UI, tuple.get(QSeries.series.seriesInstanceUID));
                seriesMap.put(seriesPk, refSOPSeq);
                refSeriesSeq.add(refSeries);
                if (seriesAttrs != null)
                    seriesAttrs.add(getSeriesAttributes(seriesPk));
            }
            Attributes refSOP = new Attributes(4);
            if (type == SOPInstanceRefsType.IAN) {
                refSOP.setString(Tag.RetrieveAETitle, VR.AE, StringUtils.maskNull(
                        retrieveAETs, StringUtils.split(tuple.get(QInstance.instance.retrieveAETs), '\\')));
                refSOP.setString(Tag.InstanceAvailability, VR.CS,
                        StringUtils.maskNull(availability, tuple.get(QInstance.instance.availability)).toString());
                if (retrieveLocationUID != null)
                    refSOP.setString(Tag.RetrieveLocationUID, VR.UI, retrieveLocationUID);
            }
            refSOP.setString(Tag.ReferencedSOPClassUID, VR.UI, tuple.get(QInstance.instance.sopClassUID));
            refSOP.setString(Tag.ReferencedSOPInstanceUID, VR.UI, tuple.get(QInstance.instance.sopInstanceUID));
            refSOPSeq.add(refSOP);
        }
        if (type == SOPInstanceRefsType.IAN)
            refStudy.setNull(Tag.ReferencedPerformedProcedureStepSequence, VR.SQ);
        return refStudy;
    }

    private Attributes getStgCmtRqstAttr(List<Tuple> tuples) {
        Attributes refStgcmt = new Attributes(2);
        refStgcmt.setString(Tag.TransactionUID, VR.UI, UIDUtils.createUID());
        Sequence refSOPSeq = refStgcmt.newSequence(Tag.ReferencedSOPSequence, 10);
        for (Tuple tuple : tuples) {
            Attributes refSOP = new Attributes(2);
            refSOP.setString(Tag.ReferencedSOPClassUID, VR.UI, tuple.get(QInstance.instance.sopClassUID));
            refSOP.setString(Tag.ReferencedSOPInstanceUID, VR.UI, tuple.get(QInstance.instance.sopInstanceUID));
            refSOPSeq.add(refSOP);
        }
        return refStgcmt;
    }

    public Attributes getStudyAttributes(String studyInstanceUID) {
        Tuple result = new HibernateQuery<Void>(em.unwrap(Session.class))
                .select(PATIENT_STUDY_ATTRS)
                .from(QStudy.study)
                .join(QStudy.study.attributesBlob, QueryBuilder.studyAttributesBlob)
                .join(QStudy.study.patient, QPatient.patient)
                .join(QPatient.patient.attributesBlob, QueryBuilder.patientAttributesBlob)
                .where(QStudy.study.studyInstanceUID.eq(studyInstanceUID))
                .fetchOne();
        if (result == null)
            return null;
        Attributes patAttrs = AttributesBlob.decodeAttributes(
                result.get(QueryBuilder.patientAttributesBlob.encodedAttributes), null);
        Attributes studyAttrs = AttributesBlob.decodeAttributes(
                result.get(QueryBuilder.studyAttributesBlob.encodedAttributes), null);
        Attributes.unifyCharacterSets(patAttrs, studyAttrs);
        Attributes attrs = new Attributes(patAttrs.size() + studyAttrs.size());
        attrs.addAll(patAttrs);
        attrs.addAll(studyAttrs);
        return attrs;
    }

    private Attributes getSeriesAttributes(Long seriesPk) {
        return AttributesBlob.decodeAttributes(new HibernateQuery<Void>(em.unwrap(Session.class))
                .select(QueryBuilder.seriesAttributesBlob.encodedAttributes)
                .from(QSeries.series)
                .join(QSeries.series.attributesBlob, QueryBuilder.seriesAttributesBlob)
                .where(QSeries.series.pk.eq(seriesPk))
                .fetchOne(), null);
    }

    public SeriesQueryAttributes calculateSeriesQueryAttributesIfNotExists(Long seriesPk, QueryParam queryParam) {
        try {
            return em.createNamedQuery(
                    SeriesQueryAttributes.FIND_BY_VIEW_ID_AND_SERIES_PK, SeriesQueryAttributes.class)
                    .setParameter(1, queryParam.getViewID())
                    .setParameter(2, seriesPk)
                    .getSingleResult();
        } catch (NoResultException e) {
            return queryAttributesEJB.calculateSeriesQueryAttributes(
                    seriesPk,
                    queryParam.getQueryRetrieveView(),
                    queryParam.getHideRejectionNotesWithCode(),
                    queryParam.getShowInstancesRejectedByCode());
        }
    }

}
