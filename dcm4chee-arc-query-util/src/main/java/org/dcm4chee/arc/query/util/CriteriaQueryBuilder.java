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

package org.dcm4chee.arc.query.util;

import org.dcm4che3.data.*;
import org.dcm4che3.data.PersonName;
import org.dcm4che3.soundex.FuzzyStr;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.AttributeFilter;
import org.dcm4chee.arc.conf.Entity;
import org.dcm4chee.arc.entity.*;

import javax.persistence.criteria.*;
import java.util.Iterator;
import java.util.Objects;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jan 2019
 */
public class CriteriaQueryBuilder<T> {

    private final CriteriaBuilder cb;
    private final CriteriaQuery<T> q;

    public CriteriaQueryBuilder(CriteriaBuilder cb, Class<T> resultClass) {
        this.cb = Objects.requireNonNull(cb);
        this.q = cb.createQuery(resultClass);
    }

    //    public static final QPersonName patientName = new QPersonName("patientName");
//    public static final QPersonName referringPhysicianName = new QPersonName("referringPhysicianName");
//    public static final QPersonName performingPhysicianName = new QPersonName("performingPhysicianName");
//    public static final QPersonName verifyingObserverName = new QPersonName("verifyingObserverName");
//    public static final QPersonName requestingPhysician = new QPersonName("requestingPhysician");
//    public static final QPersonName responsiblePerson = new QPersonName("responsiblePerson");
//    public static final QAttributesBlob patientAttributesBlob = new QAttributesBlob("patientAttributesBlob");
//    public static final QAttributesBlob studyAttributesBlob = new QAttributesBlob("studyAttributesBlob");
//    public static final QAttributesBlob seriesAttributesBlob =  new QAttributesBlob("seriesAttributesBlob");
//    public static final QAttributesBlob instanceAttributesBlob = new QAttributesBlob("instanceAttributesBlob");
//    public static final QAttributesBlob mwlAttributesBlob = new QAttributesBlob("mwlAttributesBlob");
//    public static final QIssuerEntity patientIDIssuer = new QIssuerEntity("patientIDIssuer");
//
//    private QueryBuilder() {}
//
//    public static boolean addOrderSpecifier(QueryRetrieveLevel2 level, int tag, Order order,
//                                            List<OrderSpecifier<?>> result) {
//        switch (level) {
//            case IMAGE:
//                switch (tag) {
//                    case Tag.SOPInstanceUID:
//                        return result.add(orderSpecifierOf(QInstance.instance.sopInstanceUID, order));
//                    case Tag.SOPClassUID:
//                        return result.add(orderSpecifierOf(QInstance.instance.sopClassUID, order));
//                    case Tag.InstanceNumber:
//                        return result.add(orderSpecifierOf(QInstance.instance.instanceNumber, order));
//                    case Tag.VerificationFlag:
//                        return result.add(orderSpecifierOf(QInstance.instance.verificationFlag, order));
//                    case Tag.CompletionFlag:
//                        return result.add(orderSpecifierOf(QInstance.instance.completionFlag, order));
//                    case Tag.ContentDate:
//                        return result.add(orderSpecifierOf(QInstance.instance.contentDate, order));
//                    case Tag.ContentTime:
//                        return result.add(orderSpecifierOf(QInstance.instance.contentTime, order));
//                }
//            case SERIES:
//                switch (tag) {
//                    case Tag.SeriesInstanceUID:
//                        return result.add(orderSpecifierOf(QSeries.series.seriesInstanceUID, order));
//                    case Tag.SeriesNumber:
//                        return result.add(orderSpecifierOf(QSeries.series.seriesNumber, order));
//                    case Tag.Modality:
//                        return result.add(orderSpecifierOf(QSeries.series.modality, order));
//                    case Tag.BodyPartExamined:
//                        return result.add(orderSpecifierOf(QSeries.series.bodyPartExamined, order));
//                    case Tag.Laterality:
//                        return result.add(orderSpecifierOf(QSeries.series.laterality, order));
//                    case Tag.PerformedProcedureStepStartDate:
//                        return result.add(orderSpecifierOf(QSeries.series.performedProcedureStepStartDate, order));
//                    case Tag.PerformedProcedureStepStartTime:
//                        return result.add(orderSpecifierOf(QSeries.series.performedProcedureStepStartTime, order));
//                    case Tag.PerformingPhysicianName:
//                        result.add(orderSpecifierOf(performingPhysicianName.familyName, order));
//                        result.add(orderSpecifierOf(performingPhysicianName.givenName, order));
//                        return result.add(orderSpecifierOf(performingPhysicianName.middleName, order));
//                    case Tag.SeriesDescription:
//                        return result.add(orderSpecifierOf(QSeries.series.seriesDescription, order));
//                    case Tag.StationName:
//                        return result.add(orderSpecifierOf(QSeries.series.stationName, order));
//                    case Tag.InstitutionName:
//                        return result.add(orderSpecifierOf(QSeries.series.institutionName, order));
//                    case Tag.InstitutionalDepartmentName:
//                        return result.add(orderSpecifierOf(QSeries.series.institutionalDepartmentName, order));
//                }
//            case STUDY:
//                switch (tag) {
//                    case Tag.StudyInstanceUID:
//                        return result.add(orderSpecifierOf(QStudy.study.studyInstanceUID, order));
//                    case Tag.StudyID:
//                        return result.add(orderSpecifierOf(QStudy.study.studyID, order));
//                    case Tag.StudyDate:
//                        return result.add(orderSpecifierOf(QStudy.study.studyDate, order));
//                    case Tag.StudyTime:
//                        return result.add(orderSpecifierOf(QStudy.study.studyTime, order));
//                    case Tag.ReferringPhysicianName:
//                        result.add(orderSpecifierOf(referringPhysicianName.familyName, order));
//                        result.add(orderSpecifierOf(referringPhysicianName.givenName, order));
//                        return result.add(orderSpecifierOf(referringPhysicianName.middleName, order));
//                    case Tag.StudyDescription:
//                        return result.add(orderSpecifierOf(QStudy.study.studyDescription, order));
//                    case Tag.AccessionNumber:
//                        return result.add(orderSpecifierOf(QStudy.study.accessionNumber, order));
//                }
//            case PATIENT:
//                switch (tag) {
//                    case Tag.PatientName:
//                        result.add(orderSpecifierOf(patientName.familyName, order));
//                        result.add(orderSpecifierOf(patientName.givenName, order));
//                        return result.add(orderSpecifierOf(patientName.middleName, order));
//                    case Tag.PatientSex:
//                        return result.add(orderSpecifierOf(QPatient.patient.patientSex, order));
//                    case Tag.PatientBirthDate:
//                        return result.add(orderSpecifierOf(QPatient.patient.patientBirthDate, order));
//                }
//        }
//        return false;
//    }
//
//    public static boolean addMWLOrderSpecifier(int tag, Order order, List<OrderSpecifier<?>> result) {
//        if (addOrderSpecifier(QueryRetrieveLevel2.PATIENT, tag, order, result))
//            return true;
//
//        switch (tag) {
//            case Tag.AccessionNumber:
//                return result.add(orderSpecifierOf(QMWLItem.mWLItem.accessionNumber, order));
//            case Tag.Modality:
//                return result.add(orderSpecifierOf(QMWLItem.mWLItem.modality, order));
//            case Tag.StudyInstanceUID:
//                return result.add(orderSpecifierOf(QMWLItem.mWLItem.studyInstanceUID, order));
//            case Tag.ScheduledProcedureStepStartDate:
//                return result.add(orderSpecifierOf(QMWLItem.mWLItem.scheduledStartDate, order));
//            case Tag.ScheduledProcedureStepStartTime:
//                return result.add(orderSpecifierOf(QMWLItem.mWLItem.scheduledStartTime, order));
//            case Tag.ScheduledPerformingPhysicianName:
//                result.add(orderSpecifierOf(performingPhysicianName.familyName, order));
//                result.add(orderSpecifierOf(performingPhysicianName.givenName, order));
//                return result.add(orderSpecifierOf(performingPhysicianName.middleName, order));
//            case Tag.ScheduledProcedureStepID:
//                return result.add(orderSpecifierOf(QMWLItem.mWLItem.scheduledProcedureStepID, order));
//            case Tag.RequestedProcedureID:
//                return result.add(orderSpecifierOf(QMWLItem.mWLItem.requestedProcedureID, order));
//        }
//        return false;
//    }
//
//    private static OrderSpecifier orderSpecifierOf(StringPath path, Order order) {
//        return order == Order.ASC ? path.asc() : path.desc();
//    }
//
//    private static OrderSpecifier orderSpecifierOf(NumberPath<Integer> path, Order order) {
//        return order == Order.ASC ? path.asc() : path.desc();
//    }
//
    public static void applyPatientLevelJoins(Root<Patient> patient, IDWithIssuer[] pids, Attributes keys,
                                       QueryParam queryParam,
            boolean orderByPatientName, boolean forCount) {
        applyPatientIDJoins(patient, pids);
        if (!isUniversalMatching(keys.getString(Tag.PatientName)))
            patient.join(Patient_.patientName);
        else if (!forCount && orderByPatientName)
            patient.join(Patient_.patientName, JoinType.LEFT);
        if (!isUniversalMatching(keys.getString(Tag.ResponsiblePerson)))
            patient.join(Patient_.responsiblePerson);
        if (!forCount)
            patient.join(Patient_.attributesBlob);

    }

    public static void applyPatientIDJoins(Root<Patient> patient, IDWithIssuer[] pids) {
        if (pids.length > 0) {
            Join<Patient, PatientID> patientID = patient.join(Patient_.patientID);
            if (containsIssuer(pids))
                patientID.join(PatientID_.issuer);
        }
    }

    Expression<Boolean> and(Expression<Boolean> x, Expression<Boolean> y) {
        return y == null ? x : x == null ? y : cb.and(x, y);
    }

    Expression<Boolean> or(Expression<Boolean> x, Expression<Boolean> y) {
        return y == null ? x : x == null ? y : cb.or(x, y);
    }

    public Expression<Boolean> patientIDPredicate(Expression<Boolean> x, Path<PatientID> patient, IDWithIssuer[] pids) {
        if (pids.length == 0)
            return x;

        Expression<Boolean> y = null;
        for (IDWithIssuer pid : pids)
            y = or(y, idWithIssuer(x, patient.get(PatientID_.id), patient.get(PatientID_.issuer), pid.getID(), pid.getIssuer()));

        return and(x, y);
    }

    public Expression<Boolean> patientLevelPredicates(Expression<Boolean> x, Path<Patient> patient,
                                                      IDWithIssuer[] pids, Attributes keys, QueryParam queryParam) {
        x = patientIDPredicate(x, patient.get(Patient_.patientID), pids);
        x = personName(x, patient.get(Patient_.patientName), keys.getString(Tag.PatientName, "*"), queryParam);
        x = wildCard(x, patient.get(Patient_.patientSex), keys.getString(Tag.PatientSex, "*").toUpperCase(),
                false);
        builder.and(MatchDateTimeRange.rangeMatch(patient.get(Patient_.patientBirthDate, keys, Tag.PatientBirthDate,
                MatchDateTimeRange.FormatDate.DA));
        builder.and(MatchPersonName.match(
                QueryBuilder.responsiblePerson, keys.getString(Tag.ResponsiblePerson, "*"), queryParam));
        AttributeFilter attrFilter = queryParam.getAttributeFilter(Entity.Patient);
        x = wildCard(x, patient.get(Patient_.patientCustomAttribute1),
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute1(), "*"), true);
        x = wildCard(x, patient.get(Patient_.patientCustomAttribute2),
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute2(), "*"), true);
        x = wildCard(x, patient.get(Patient_.patientCustomAttribute3),
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute3(), "*"), true);
        if (queryParam.getPatientVerificationStatus() != null)
            x = and(x, cb.equal(patient.get(Patient_.verificationStatus), queryParam.getPatientVerificationStatus()));
        return x;
    }

//    public static boolean hasPatientLevelPredicates(
//            IDWithIssuer[] pids, Attributes keys, QueryParam queryParam) {
//        for (IDWithIssuer pid : pids) {
//            if (!isUniversalMatching(pid.getID()))
//                return true;
//        }
//        AttributeFilter attrFilter = queryParam.getAttributeFilter(Entity.Patient);
//        return queryParam.getPatientVerificationStatus() != null
//                || !isUniversalMatching(keys.getString(Tag.PatientName))
//                || !isUniversalMatching(keys.getString(Tag.PatientSex))
//                || !isUniversalMatching(keys.getString(Tag.PatientSex))
//                || !isUniversalMatching(keys.getString(Tag.PatientBirthDate))
//                || !isUniversalMatching(keys.getString(Tag.ResponsiblePerson))
//                || !isUniversalMatching(AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute1(), null))
//                || !isUniversalMatching(AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute2(), null))
//                || !isUniversalMatching(AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute3(), null));
//    }
//
//    public static <T> HibernateQuery<T> applyStudyLevelJoins(
//            HibernateQuery<T> query, Attributes keys, QueryParam queryParam,
//            boolean forCount, boolean hasPatientLevelPredicates) {
//        if (!forCount || hasPatientLevelPredicates)
//            query = query.innerJoin(QStudy.study.patient, QPatient.patient);
//        if (!forCount)
//            query = query.leftJoin(QStudy.study.queryAttributes, QStudyQueryAttributes.studyQueryAttributes)
//                    .on(QStudyQueryAttributes.studyQueryAttributes.viewID.eq(queryParam.getViewID()));
//
//        if (!isUniversalMatching(keys.getString(Tag.AccessionNumber))
//                && !isUniversalMatching(keys.getNestedDataset(Tag.IssuerOfAccessionNumberSequence))) {
//            query = query.leftJoin(QStudy.study.issuerOfAccessionNumber);
//        }
//
//        if (!isUniversalMatching(keys.getString(Tag.ReferringPhysicianName))) {
//            query = query.join(QStudy.study.referringPhysicianName, QueryBuilder.referringPhysicianName);
//        }
//        if (!forCount)
//            query = query.join(QStudy.study.attributesBlob, QueryBuilder.studyAttributesBlob);
//        return query;
//    }
//
//    public static Predicate uidsPredicate(StringPath path, String[] values) {
//        if (isUniversalMatching(values))
//            return null;
//
//        return path.in(values);
//    }
//
//    public static void addStudyLevelPredicates(BooleanBuilder builder, Attributes keys,
//                                               QueryParam queryParam, QueryRetrieveLevel2 queryRetrieveLevel) {
//        boolean combinedDatetimeMatching = queryParam.isCombinedDatetimeMatching();
//        builder.and(accessControl(queryParam.getAccessControlIDs()));
//        builder.and(uidsPredicate(QStudy.study.studyInstanceUID, keys.getStrings(Tag.StudyInstanceUID)));
//        builder.and(wildCard(QStudy.study.studyID, keys.getString(Tag.StudyID, "*"), false));
//        builder.and(MatchDateTimeRange.rangeMatch(
//                QStudy.study.studyDate, QStudy.study.studyTime,
//                Tag.StudyDate, Tag.StudyTime, Tag.StudyDateAndTime,
//                keys, combinedDatetimeMatching));
//        builder.and(MatchPersonName.match(QueryBuilder.referringPhysicianName,
//                keys.getString(Tag.ReferringPhysicianName, "*"), queryParam));
//        builder.and(wildCard(QStudy.study.studyDescription,
//                keys.getString(Tag.StudyDescription, "*"), true));
//        String accNo = keys.getString(Tag.AccessionNumber, "*");
//        if (!accNo.equals("*")) {
//            Issuer issuer = Issuer.valueOf(keys.getNestedDataset(Tag.IssuerOfAccessionNumberSequence));
//            if (issuer == null)
//                issuer = queryParam.getDefaultIssuerOfAccessionNumber();
//            builder.and(idWithIssuer(QStudy.study.accessionNumber, QStudy.study.issuerOfAccessionNumber, accNo, issuer));
//        }
//        builder.and(seriesAttributesInStudy(keys, queryParam));
//        builder.and(code(QStudy.study.procedureCodes, keys.getNestedDataset(Tag.ProcedureCodeSequence)));
//        if (queryParam.isHideNotRejectedInstances())
//            builder.and(QStudy.study.rejectionState.ne(RejectionState.NONE));
//        AttributeFilter attrFilter = queryParam.getAttributeFilter(Entity.Study);
//        builder.and(wildCard(QStudy.study.studyCustomAttribute1,
//                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute1(), "*"), true));
//        builder.and(wildCard(QStudy.study.studyCustomAttribute2,
//                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute2(), "*"), true));
//        builder.and(wildCard(QStudy.study.studyCustomAttribute3,
//                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute3(), "*"), true));
//        DateRange studyReceiveDateTime =
//                keys.getDateRange(ArchiveTag.PrivateCreator, ArchiveTag.StudyReceiveDateTime, VR.DT);
//        if (studyReceiveDateTime != null)
//            builder.and(MatchDateTimeRange.range(
//                    QStudy.study.createdTime, studyReceiveDateTime, MatchDateTimeRange.FormatDate.DT));
//        if (queryParam.getExternalRetrieveAET() != null)
//            builder.and(QStudy.study.externalRetrieveAET.eq(queryParam.getExternalRetrieveAET()));
//        if (queryParam.getExternalRetrieveAETNot() != null)
//            builder.and(QStudy.study.externalRetrieveAET.ne(queryParam.getExternalRetrieveAETNot()));
//        if (queryRetrieveLevel == QueryRetrieveLevel2.STUDY) {
//            if (queryParam.isExpired())
//                builder.and(QStudy.study.expirationDate.loe(DateTimeFormatter.BASIC_ISO_DATE.format(LocalDate.now())));
//            if (queryParam.isIncomplete())
//                builder.and(QStudy.study.completeness.ne(Completeness.COMPLETE));
//            if (queryParam.isRetrieveFailed())
//                builder.and(QStudy.study.failedRetrieves.gt(0));
//        }
//        if (queryParam.getExpirationDate() != null)
//            builder.and(MatchDateTimeRange.range(
//                    QStudy.study.expirationDate, queryParam.getExpirationDate(), MatchDateTimeRange.FormatDate.DA));
//        if (queryParam.getStudyStorageIDs() != null)
//            builder.and(QStudy.study.storageIDs.in(queryParam.getStudyStorageIDs()));
//    }
//
//    public static Predicate accessControl(String[] accessControlIDs) {
//        if (accessControlIDs.length == 0) {
//            return null;
//        }
//        String[] a = new String[accessControlIDs.length + 1];
//        a[0] = "*";
//        System.arraycopy(accessControlIDs, 0, a, 1, accessControlIDs.length);
//        return QStudy.study.accessControlID.in(a);
//    }
//
//    public static <T> HibernateQuery<T> applySeriesLevelJoins(
//            HibernateQuery<T> query, Attributes keys, QueryParam queryParam, boolean forCount) {
//        query = query.innerJoin(QSeries.series.study, QStudy.study);
//        if (!forCount)
//            query = query.leftJoin(QSeries.series.queryAttributes, QSeriesQueryAttributes.seriesQueryAttributes)
//                    .on(QSeriesQueryAttributes.seriesQueryAttributes.viewID.eq(
//                            queryParam.getViewID()));
//        if (!isUniversalMatching(keys.getString(Tag.PerformingPhysicianName))) {
//            query = query.join(QSeries.series.performingPhysicianName, QueryBuilder.performingPhysicianName);
//        }
//        if (!forCount)
//            query = query.join(QSeries.series.attributesBlob, QueryBuilder.seriesAttributesBlob);
//        return query;
//    }
//
//    public static void addSeriesLevelPredicates(BooleanBuilder builder, Attributes keys,
//                                                QueryParam queryParam, QueryRetrieveLevel2 queryRetrieveLevel) {
//        builder.and(uidsPredicate(QSeries.series.seriesInstanceUID, keys.getStrings(Tag.SeriesInstanceUID)));
//        builder.and(numberPredicate(QSeries.series.seriesNumber, keys.getString(Tag.SeriesNumber, "0")));
//        builder.and(wildCard(QSeries.series.modality, keys.getString(Tag.Modality, "*").toUpperCase(),
//                false));
//        builder.and(wildCard(QSeries.series.bodyPartExamined, keys.getString(Tag.BodyPartExamined, "*").toUpperCase(),
//                false));
//        builder.and(wildCard(QSeries.series.laterality, keys.getString(Tag.Laterality, "*").toUpperCase(),
//                false));
//        builder.and(MatchDateTimeRange.rangeMatch(
//                QSeries.series.performedProcedureStepStartDate, QSeries.series.performedProcedureStepStartTime,
//                Tag.PerformedProcedureStepStartDate, Tag.PerformedProcedureStepStartTime,
//                Tag.PerformedProcedureStepStartDateAndTime,
//                keys, queryParam.isCombinedDatetimeMatching()));
//        builder.and(MatchPersonName.match(QueryBuilder.performingPhysicianName,
//                keys.getString(Tag.PerformingPhysicianName, "*"), queryParam));
//        builder.and(wildCard(QSeries.series.seriesDescription, keys.getString(Tag.SeriesDescription, "*"),
//                true));
//        builder.and(wildCard(QSeries.series.stationName, keys.getString(Tag.StationName, "*"), true));
//        builder.and(wildCard(QSeries.series.institutionalDepartmentName, keys.getString(Tag.InstitutionalDepartmentName, "*"),
//                true));
//        builder.and(wildCard(QSeries.series.institutionName, keys.getString(Tag.InstitutionName, "*"),
//                true));
//        builder.and(requestAttributes(keys.getNestedDataset(Tag.RequestAttributesSequence), queryParam));
//        builder.and(code(QSeries.series.institutionCode, keys.getNestedDataset(Tag.InstitutionCodeSequence)));
//        if (queryParam.isHideNotRejectedInstances())
//            builder.and(QSeries.series.rejectionState.ne(RejectionState.NONE));
//        AttributeFilter attrFilter = queryParam.getAttributeFilter(Entity.Series);
//        builder.and(wildCard(QSeries.series.seriesCustomAttribute1,
//                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute1(), "*"), true));
//        builder.and(wildCard(QSeries.series.seriesCustomAttribute2,
//                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute2(), "*"), true));
//        builder.and(wildCard(QSeries.series.seriesCustomAttribute3,
//                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute3(), "*"), true));
//        if (queryRetrieveLevel == QueryRetrieveLevel2.SERIES) {
//            if (queryParam.isExpired())
//                builder.and(QSeries.series.expirationDate.loe(DateTimeFormatter.BASIC_ISO_DATE.format(LocalDate.now())));
//            if (queryParam.isIncomplete())
//                builder.and(QSeries.series.completeness.ne(Completeness.COMPLETE));
//            if (queryParam.isRetrieveFailed())
//                builder.and(QSeries.series.failedRetrieves.gt(0));
//            if (queryParam.isStorageVerificationFailed())
//                builder.and(QSeries.series.failuresOfLastStorageVerification.gt(0));
//            if (queryParam.isCompressionFailed())
//                builder.and(QSeries.series.compressionFailures.gt(0));
//            builder.and(wildCard(QSeries.series.sourceAET,
//                    keys.getString(ArchiveTag.PrivateCreator, ArchiveTag.SendingApplicationEntityTitleOfSeries, VR.AE, "*"),
//                    false));
//        }
//        if (queryParam.getExpirationDate() != null)
//            builder.and(MatchDateTimeRange.range(
//                    QSeries.series.expirationDate, queryParam.getExpirationDate(), MatchDateTimeRange.FormatDate.DA));
//    }
//
//    public static <T> HibernateQuery<T> applyInstanceLevelJoins(
//            HibernateQuery<T> query, Attributes keys, QueryParam queryParam, boolean forCount) {
//        query = query.innerJoin(QInstance.instance.series, QSeries.series);
//        if (!forCount)
//            query = query.join(QInstance.instance.attributesBlob, QueryBuilder.instanceAttributesBlob);
//        query = query.leftJoin(QInstance.instance.rejectionNoteCode, QCodeEntity.codeEntity);
//        return query;
//    }
//
//    public static void addInstanceLevelPredicates(BooleanBuilder builder, Attributes keys, QueryParam queryParam,
//                                                  CodeEntity[] showInstancesRejectedByCodes,
//                                                  CodeEntity[] hideRejectionNoteWithCodes) {
//        boolean combinedDatetimeMatching = queryParam.isCombinedDatetimeMatching();
//        builder.and(uidsPredicate(QInstance.instance.sopInstanceUID, keys.getStrings(Tag.SOPInstanceUID)));
//        builder.and(uidsPredicate(QInstance.instance.sopClassUID, keys.getStrings(Tag.SOPClassUID)));
//        builder.and(numberPredicate(QInstance.instance.instanceNumber, keys.getString(Tag.InstanceNumber, "0")));
//        builder.and(wildCard(QInstance.instance.verificationFlag,
//                keys.getString(Tag.VerificationFlag, "*").toUpperCase(), false));
//        builder.and(wildCard(QInstance.instance.completionFlag,
//                keys.getString(Tag.CompletionFlag, "*").toUpperCase(), false));
//        builder.and(MatchDateTimeRange.rangeMatch(
//                QInstance.instance.contentDate, QInstance.instance.contentTime,
//                Tag.ContentDate, Tag.ContentTime, Tag.ContentDateAndTime,
//                keys, combinedDatetimeMatching));
//        builder.and(code(QInstance.instance.conceptNameCode, keys.getNestedDataset(Tag.ConceptNameCodeSequence)));
//        builder.and(verifyingObserver(keys.getNestedDataset(Tag.VerifyingObserverSequence),
//                queryParam));
//        Sequence contentSeq = keys.getSequence(Tag.ContentSequence);
//        if (contentSeq != null)
//            for (Attributes item : contentSeq)
//                builder.and(contentItem(item));
//        AttributeFilter attrFilter = queryParam
//                .getAttributeFilter(Entity.Instance);
//        builder.and(wildCard(
//                QInstance.instance.instanceCustomAttribute1,
//                AttributeFilter.selectStringValue(keys,
//                        attrFilter.getCustomAttribute1(), "*"),
//                true));
//        builder.and(wildCard(
//                QInstance.instance.instanceCustomAttribute2,
//                AttributeFilter.selectStringValue(keys,
//                        attrFilter.getCustomAttribute2(), "*"),
//                true));
//        builder.and(wildCard(
//                QInstance.instance.instanceCustomAttribute3,
//                AttributeFilter.selectStringValue(keys,
//                        attrFilter.getCustomAttribute3(), "*"),
//                true));
//        builder.and(hideRejectedInstance(showInstancesRejectedByCodes, queryParam.isHideNotRejectedInstances()));
//        builder.and(hideRejectionNote(hideRejectionNoteWithCodes));
//    }
//
//    public static <T> HibernateQuery<T> applyMWLJoins(
//            HibernateQuery<T> query, Attributes keys, QueryParam queryParam, boolean forCount) {
//        query = query.innerJoin(QMWLItem.mWLItem.patient, QPatient.patient);
//
//        if (!isUniversalMatching(keys.getString(Tag.AccessionNumber))
//                && !isUniversalMatching(keys.getNestedDataset(Tag.IssuerOfAccessionNumberSequence))) {
//            query = query.leftJoin(QMWLItem.mWLItem.issuerOfAccessionNumber);
//        }
//
//        Attributes sps = keys.getNestedDataset(Tag.ScheduledProcedureStepSequence);
//        if (sps != null && !isUniversalMatching(sps.getString(Tag.ScheduledPerformingPhysicianName))) {
//            query = query.join(QMWLItem.mWLItem.scheduledPerformingPhysicianName, QueryBuilder.performingPhysicianName);
//        }
//        if (!forCount)
//            query = query.join(QMWLItem.mWLItem.attributesBlob, QueryBuilder.mwlAttributesBlob);
//        return query;
//    }
//
//    public static void addMWLPredicates(BooleanBuilder builder, Attributes keys, QueryParam queryParam) {
//        builder.and(uidsPredicate(QMWLItem.mWLItem.studyInstanceUID, keys.getStrings(Tag.StudyInstanceUID)));
//        builder.and(wildCard(QMWLItem.mWLItem.requestedProcedureID, keys.getString(Tag.RequestedProcedureID, "*"),
//                false));
//        String accNo = keys.getString(Tag.AccessionNumber, "*");
//        if (!accNo.equals("*")) {
//            Issuer issuer = Issuer.valueOf(keys.getNestedDataset(Tag.IssuerOfAccessionNumberSequence));
//            if (issuer == null) {
//                issuer = queryParam.getDefaultIssuerOfAccessionNumber();
//            }
//            builder.and(idWithIssuer(
//                    QMWLItem.mWLItem.accessionNumber, QMWLItem.mWLItem.issuerOfAccessionNumber, accNo, issuer));
//        }
//        Attributes sps = keys.getNestedDataset(Tag.ScheduledProcedureStepSequence);
//        if (sps != null) {
//            builder.and(wildCard(QMWLItem.mWLItem.scheduledProcedureStepID,
//                    sps.getString(Tag.ScheduledProcedureStepID, "*"), false));
//            builder.and(MatchDateTimeRange.rangeMatch(
//                    QMWLItem.mWLItem.scheduledStartDate, QMWLItem.mWLItem.scheduledStartTime,
//                    Tag.ScheduledProcedureStepStartDate, Tag.ScheduledProcedureStepStartDate,
//                    Tag.ScheduledProcedureStepStartDateAndTime,
//                    sps, true));
//            builder.and(MatchPersonName.match(QueryBuilder.performingPhysicianName,
//                    sps.getString(Tag.ScheduledPerformingPhysicianName, "*"), queryParam));
//            builder.and(wildCard(QMWLItem.mWLItem.modality, sps.getString(Tag.Modality, "*").toUpperCase(),
//                    false));
//            if (sps.getString(Tag.ScheduledProcedureStepStatus) != null)
//                builder.and(showSPSWithStatus(sps));
//            if (sps.getString(Tag.ScheduledStationAETitle) != null)
//                builder.and(QMWLItem.mWLItem.scheduledStationAETs.contains(sps.getString(Tag.ScheduledStationAETitle)));
//        }
//        builder.and(hideSPSWithStatus(queryParam));
//    }
//
//    private static Predicate hideSPSWithStatus(QueryParam queryParam) {
//        SPSStatus[] hideSPSWithStatusFromMWL = queryParam.getHideSPSWithStatusFromMWL();
//        return (hideSPSWithStatusFromMWL.length > 0)
//                ? QMWLItem.mWLItem.status.notIn(hideSPSWithStatusFromMWL)
//                : null;
//    }
//
//    private static Predicate showSPSWithStatus(Attributes sps) {
//        SPSStatus status = SPSStatus.valueOf(sps.getString(Tag.ScheduledProcedureStepStatus).toUpperCase());
//        switch(status) {
//            case SCHEDULED:
//            case ARRIVED:
//            case READY:
//                return QMWLItem.mWLItem.status.eq(status);
//            default:
//                return null;
//        }
//    }
//
//    public static Predicate hideRejectedInstance(CodeEntity[] codes, boolean hideNotRejectedInstances) {
//        if (codes.length == 0)
//            return hideNotRejectedInstances
//                    ? QInstance.instance.rejectionNoteCode.isNotNull()
//                    : QInstance.instance.rejectionNoteCode.isNull();
//
//        BooleanExpression showRejected = QInstance.instance.rejectionNoteCode.in(codes);
//        return hideNotRejectedInstances
//                ? showRejected
//                : QInstance.instance.rejectionNoteCode.isNull().or(showRejected);
//    }
//
//    public static Predicate hideRejectionNote(CodeEntity[] codes) {
//        if (codes.length == 0)
//            return null;
//
//        return QInstance.instance.conceptNameCode.isNull()
//                .or(QInstance.instance.conceptNameCode.notIn(codes));
//    }
//
    static boolean containsIssuer(IDWithIssuer[] pids) {
        for (IDWithIssuer pid : pids)
            if (pid.getIssuer() != null)
                return true;
        return false;
    }

    Expression<Boolean> idWithIssuer(Expression<Boolean> x, Expression<String> idPath,
                                     Path<IssuerEntity> issuerEnityPath, String id, Issuer issuer) {
        Expression<Boolean> y = wildCard(x, idPath, id);
        if (x == y || issuer == null)
            return y;

        String entityID = issuer.getLocalNamespaceEntityID();
        String entityUID = issuer.getUniversalEntityID();
        String entityUIDType = issuer.getUniversalEntityIDType();
        Path<Issuer> issuerPath = issuerEnityPath.get(IssuerEntity_.issuer);
        if (!isUniversalMatching(entityID))
            y = and(y, cb.or(issuerPath.get(Issuer_.localNamespaceEntityID).isNull(),
                            cb.equal(issuerPath.get(Issuer_.localNamespaceEntityID), entityID)));
        if (!isUniversalMatching(entityUID))
            y = and(y, cb.or(issuerPath.get(Issuer_.universalEntityID).isNull(),
                            cb.and(cb.equal(issuerPath.get(Issuer_.universalEntityID), entityUID),
                                    cb.equal(issuerPath.get(Issuer_.universalEntityIDType), entityUIDType))));
        return y;
    }

    static boolean isUniversalMatching(Attributes item) {
        return item == null || item.isEmpty();
    }

    static boolean isUniversalMatching(String value) {
        return value == null || value.equals("*");
    }

    public static boolean isUniversalMatching(String[] values) {
        return values == null || values.length == 0 || values[0].equals("*");
    }

    private static boolean isUniversalMatching(Integer value) {
        return value == null || value.equals(0);
    }

    Expression<Boolean> wildCard(Expression<Boolean> x, Expression<String> path, String value) {
        return wildCard(x, path, value, false);
    }

    Expression<Boolean> wildCard(Expression<Boolean> x, Expression<String> path, String value, boolean ignoreCase) {
        if (isUniversalMatching(value))
            return x;

        if (ignoreCase && StringUtils.isUpperCase(value))
            path = cb.upper(path);

        if (!containsWildcard(value))
            return and(x, cb.equal(path, value));

        String pattern = toLikePattern(value);
        return pattern.equals("%") ? x : and(x, cb.like(path, pattern, '!'));
    }

    Expression<Boolean> numberPredicate(Expression<Boolean> x, Expression<Integer> path, String value) {
        try {
            Integer v = Integer.parseInt(value);
            if (isUniversalMatching(v))
                return x;
            return and(x, cb.equal(path, v));
        } catch (NumberFormatException e) {
            return x;
        }
    }

    static boolean containsWildcard(String s) {
        return s.indexOf('*') >= 0 || s.indexOf('?') >= 0;
    }

    static String toLikePattern(String s) {
        StringBuilder like = new StringBuilder(s.length());
        char[] cs = s.toCharArray();
        char p = 0;
        for (char c : cs) {
            switch (c) {
                case '*':
                    if (c != p)
                        like.append('%');
                    break;
                case '?':
                    like.append('_');
                    break;
                case '_':
                case '%':
                case '!':
                    like.append('!');
                    // fall through
                default:
                    like.append(c);
            }
            p = c;
        }
        return like.toString();
    }

//
//    static Predicate seriesAttributesInStudy(Attributes keys, QueryParam queryParam) {
//        BooleanBuilder result = new BooleanBuilder();
//        result.and(wildCard(QSeries.series.institutionName, keys.getString(Tag.InstitutionName), true))
//                .and(wildCard(QSeries.series.institutionalDepartmentName, keys.getString(Tag.InstitutionalDepartmentName), true))
//                .and(wildCard(QSeries.series.stationName, keys.getString(Tag.StationName), true))
//                .and(wildCard(QSeries.series.seriesDescription, keys.getString(Tag.SeriesDescription), true))
//                .and(wildCard(QSeries.series.modality, keys.getString(Tag.ModalitiesInStudy, "*").toUpperCase(), false))
//                .and(wildCard(QSeries.series.sopClassUID, keys.getString(Tag.SOPClassesInStudy, "*"), false))
//                .and(wildCard(QSeries.series.bodyPartExamined, keys.getString(Tag.BodyPartExamined, "*").toUpperCase(), false))
//                .and(wildCard(QSeries.series.sourceAET,
//                        keys.getString(ArchiveTag.PrivateCreator, ArchiveTag.SendingApplicationEntityTitleOfSeries, VR.AE, "*"),
//                        false));
//        if (queryParam.isStorageVerificationFailed())
//            result.and(QSeries.series.failuresOfLastStorageVerification.gt(0));
//        if (queryParam.isCompressionFailed())
//            result.and(QSeries.series.compressionFailures.gt(0));
//        if (!result.hasValue())
//            return null;
//        return JPAExpressions.selectFrom(QSeries.series)
//                .where(QSeries.series.study.eq(QStudy.study), result).exists();
//    }
//
//    static Predicate code(Attributes item) {
//        if (item == null || item.isEmpty())
//            return null;
//
//        return ExpressionUtils.allOf(
//                wildCard(QCodeEntity.codeEntity.codeValue,
//                        item.getString(Tag.CodeValue, "*")),
//                wildCard(QCodeEntity.codeEntity.codingSchemeDesignator,
//                        item.getString(Tag.CodingSchemeDesignator, "*")),
//                wildCard(QCodeEntity.codeEntity.codingSchemeVersion,
//                        item.getString(Tag.CodingSchemeVersion, "*")));
//    }
//
    Predicate code(Path<CodeEntity> code, Attributes item) {
        if (item == null || item.isEmpty())
            return null;

        return cb.and(
                wildCard(cb, code.get(CodeEntity_.codeValue),
                        item.getString(Tag.CodeValue, "*")),
                wildCard(cb, code.get(CodeEntity_.codingSchemeDesignator),
                        item.getString(Tag.CodingSchemeDesignator, "*")),
                wildCard(cb, code.get(CodeEntity_.codingSchemeVersion),
                        item.getString(Tag.CodingSchemeVersion, "*")));
    }
//    static Predicate code(QCodeEntity code, Attributes item) {
//        Predicate predicate = code(item);
//        if (predicate == null)
//            return null;
//
//        return JPAExpressions.selectFrom(QCodeEntity.codeEntity)
//                .where(QCodeEntity.codeEntity.eq(code), predicate).exists();
//    }
//
//
//    static Predicate code(CollectionPath<CodeEntity, QCodeEntity> codes, Attributes item) {
//        Predicate predicate = code(item);
//        if (predicate == null)
//            return null;
//
//        return JPAExpressions.selectFrom(QCodeEntity.codeEntity)
//                .where(codes.contains(QCodeEntity.codeEntity), predicate).exists();
//    }
//
//    static Predicate requestAttributes(Attributes item, QueryParam queryParam) {
//        if (item == null || item.isEmpty())
//            return null;
//
//        BooleanBuilder builder = new BooleanBuilder();
//        String accNo = item.getString(Tag.AccessionNumber, "*");
//        Issuer issuerOfAccessionNumber = null;
//        if (!accNo.equals("*")) {
//            issuerOfAccessionNumber = Issuer.valueOf(item
//                    .getNestedDataset(Tag.IssuerOfAccessionNumberSequence));
//            if (issuerOfAccessionNumber == null)
//                issuerOfAccessionNumber = queryParam.getDefaultIssuerOfAccessionNumber();
//            builder.and(idWithIssuer(
//                    QSeriesRequestAttributes.seriesRequestAttributes.accessionNumber,
//                    QSeriesRequestAttributes.seriesRequestAttributes.issuerOfAccessionNumber,
//                    accNo, issuerOfAccessionNumber));
//        }
//        builder.and(wildCard(
//                QSeriesRequestAttributes.seriesRequestAttributes.requestingService,
//                item.getString(Tag.RequestingService, "*"), true));
//        Predicate matchRequestingPhysician = MatchPersonName.match(
//                QueryBuilder.requestingPhysician,
//                item.getString(Tag.RequestingPhysician, "*"), queryParam);
//        builder.and(matchRequestingPhysician);
//        builder.and(wildCard(
//                QSeriesRequestAttributes.seriesRequestAttributes.requestedProcedureID,
//                item.getString(Tag.RequestedProcedureID, "*"), false));
//        builder.and(uidsPredicate(QSeriesRequestAttributes.seriesRequestAttributes.studyInstanceUID,
//                item.getStrings(Tag.StudyInstanceUID)));
//        builder.and(wildCard(
//                QSeriesRequestAttributes.seriesRequestAttributes.scheduledProcedureStepID,
//                item.getString(Tag.ScheduledProcedureStepID, "*"),
//                false));
//
//        if (!builder.hasValue())
//            return null;
//
//        JPQLQuery<SeriesRequestAttributes> subQuery = JPAExpressions.selectFrom(QSeriesRequestAttributes.seriesRequestAttributes);
//
//        if (issuerOfAccessionNumber != null)
//            subQuery.leftJoin(
//                    QSeriesRequestAttributes.seriesRequestAttributes.issuerOfAccessionNumber,
//                    QIssuerEntity.issuerEntity);
//
//        if (matchRequestingPhysician != null)
//            subQuery = subQuery.join(QueryBuilder.requestingPhysician, QueryBuilder.requestingPhysician);
//
//        return subQuery.where(QSeriesRequestAttributes.seriesRequestAttributes.in(QSeries.series.requestAttributes),
//                builder).exists();
//    }
//
//    static Predicate verifyingObserver(Attributes item, QueryParam queryParam) {
//        if (item == null || item.isEmpty())
//            return null;
//
//        Predicate matchVerifyingObserverName = MatchPersonName.match(
//                QueryBuilder.verifyingObserverName,
//                item.getString(Tag.VerifyingObserverName, "*"),
//                queryParam);
//        Predicate predicate = ExpressionUtils
//                .allOf(MatchDateTimeRange.rangeMatch(
//                        QVerifyingObserver.verifyingObserver.verificationDateTime,
//                        item, Tag.VerificationDateTime,
//                        MatchDateTimeRange.FormatDate.DT),
//                        matchVerifyingObserverName);
//
//        if (predicate == null)
//            return null;
//
//        JPQLQuery<VerifyingObserver> query = JPAExpressions.selectFrom(QVerifyingObserver.verifyingObserver);
//
//        if (matchVerifyingObserverName != null)
//            query = query.join(QVerifyingObserver.verifyingObserver.verifyingObserverName,
//                    QueryBuilder.verifyingObserverName);
//
//        return query.where(QVerifyingObserver.verifyingObserver.in(QInstance.instance.verifyingObservers),
//                predicate).exists();
//    }
//
//    static Predicate contentItem(Attributes item) {
//        String valueType = item.getString(Tag.ValueType);
//        if (!("CODE".equals(valueType) || "TEXT".equals(valueType)))
//            return null;
//
//        Predicate predicate = ExpressionUtils.allOf(
//                code(QContentItem.contentItem.conceptName,
//                        item.getNestedDataset(Tag.ConceptNameCodeSequence)),
//                wildCard(QContentItem.contentItem.relationshipType, item
//                        .getString(Tag.RelationshipType, "*").toUpperCase()),
//                code(QContentItem.contentItem.conceptCode,
//                        item.getNestedDataset(Tag.ConceptCodeSequence)),
//                wildCard(QContentItem.contentItem.textValue,
//                        item.getString(Tag.TextValue, "*"), true));
//        if (predicate == null)
//            return null;
//
//        return JPAExpressions.selectFrom(QContentItem.contentItem)
//                .where(QInstance.instance.contentItems
//                                .contains(QContentItem.contentItem),
//                        predicate).exists();
//    }
//

    private Expression<Boolean> personName(Expression<Boolean> x, Path<org.dcm4chee.arc.entity.PersonName> qpn,
                                          String value, QueryParam queryParam) {
        if (value.equals("*"))
            return x;

        PersonName pn = new PersonName(value, true);
        return and(x, queryParam.isFuzzySemanticMatching()
                ? fuzzyMatch(qpn, pn, queryParam)
                : literalMatch(qpn, pn, queryParam));
    }

    private Expression<Boolean> literalMatch(Path<org.dcm4chee.arc.entity.PersonName> qpn,
                                             PersonName pn, QueryParam param) {
        Expression<Boolean> y = null;
        if (!pn.contains(PersonName.Group.Ideographic)
                && !pn.contains(PersonName.Group.Phonetic)) {
            y = or(y, match(
                    qpn.get(PersonName_.familyName),
                    qpn.get(PersonName_.givenName),
                    qpn.get(PersonName_.middleName),
                    pn, PersonName.Group.Alphabetic, true));
            y = or(y, match(
                    qpn.get(PersonName_.ideographicFamilyName),
                    qpn.get(PersonName_.ideographicGivenName),
                    qpn.get(PersonName_.ideographicMiddleName),
                    pn, PersonName.Group.Alphabetic, false));
            y = or(y, match(
                    qpn.get(PersonName_.phoneticFamilyName),
                    qpn.get(PersonName_.phoneticGivenName),
                    qpn.get(PersonName_.phoneticMiddleName),
                    pn, PersonName.Group.Alphabetic, false));
        } else {
            y = and(y, match(
                    qpn.get(PersonName_.familyName),
                    qpn.get(PersonName_.givenName),
                    qpn.get(PersonName_.middleName),
                    pn, PersonName.Group.Alphabetic, true));
            y = and(y, match(
                    qpn.get(PersonName_.ideographicFamilyName),
                    qpn.get(PersonName_.ideographicGivenName),
                    qpn.get(PersonName_.ideographicMiddleName),
                    pn, PersonName.Group.Ideographic, false));
            y = and(y, match(
                    qpn.get(PersonName_.phoneticFamilyName),
                    qpn.get(PersonName_.phoneticGivenName),
                    qpn.get(PersonName_.phoneticMiddleName),
                    pn, PersonName.Group.Phonetic, false));
        }
        return y;
    }

    private Expression<Boolean> match(Path<String> familyName, Path<String> givenName, Path<String> middleName,
                                   PersonName pn, PersonName.Group group,
                                   boolean ignoreCase) {
        if (!pn.contains(group))
            return null;

        Expression<Boolean> x = null;
        x = wildCard(x, familyName, pn.get(group, PersonName.Component.FamilyName), ignoreCase);
        x = wildCard(x, givenName, pn.get(group, PersonName.Component.GivenName), ignoreCase);
        x = wildCard(x, middleName, pn.get(group, PersonName.Component.MiddleName), ignoreCase);
        return x;
    }

    private Expression<Boolean> fuzzyMatch(Path<org.dcm4chee.arc.entity.PersonName> qpn,
                                                  PersonName pn, QueryParam param) {
        Expression<Boolean> x = null;
        x = fuzzyMatch(x, qpn, pn, PersonName.Component.FamilyName, param);
        x = fuzzyMatch(x, qpn, pn, PersonName.Component.GivenName, param);
        x = fuzzyMatch(x, qpn, pn, PersonName.Component.MiddleName, param);
        return x;
    }

    private Expression<Boolean> fuzzyMatch(Expression<Boolean> x, Path<org.dcm4chee.arc.entity.PersonName> qpn,
                                                  PersonName pn, PersonName.Component c, QueryParam param) {
        String name = StringUtils.maskNull(pn.get(c), "*");
        if (name.equals("*"))
            return x;

        Iterator<String> parts = SoundexCode.tokenizePersonNameComponent(name);
        for (int i = 0; parts.hasNext(); ++i)
            x = fuzzyMatch(x, qpn, c, i, parts.next(), param);

        return x;
    }

    private Expression<Boolean> fuzzyMatch(Expression<Boolean> x, Path<org.dcm4chee.arc.entity.PersonName> qpn,
                                           PersonName.Component c, int partIndex, String name, QueryParam param) {
        boolean wc = name.endsWith("*");
        if (wc) {
            name = name.substring(0, name.length()-1);
            if (name.isEmpty())
                return x;
        }
        FuzzyStr fuzzyStr = param.getFuzzyStr();
        String fuzzyName = fuzzyStr.toFuzzy(name);
        if (fuzzyName.isEmpty())
            if (wc)
                return x;
            else // code "" is stored as "*"
                fuzzyName = "*";

        Subquery<SoundexCode> sq = q.subquery(SoundexCode.class);
        Root<SoundexCode> soundexCode = sq.from(SoundexCode.class);
        Expression<Boolean> y = cb.and(cb.equal(soundexCode.get(SoundexCode_.personName), qpn),
                wc ? cb.like(soundexCode.get(SoundexCode_.codeValue), fuzzyName + '%')
                   : cb.equal(soundexCode.get(SoundexCode_.codeValue), fuzzyName);
        if (!param.isPersonNameComponentOrderInsensitiveMatching()) {
            y = cb.and(y, cb.and(
                    cb.equal(soundexCode.get(SoundexCode_.personNameComponent), c),
                    cb.equal(soundexCode.get(SoundexCode_.componentPartIndex), partIndex)));
        }
        return and(x, cb.exists(sq.where(y)));
    }

}
