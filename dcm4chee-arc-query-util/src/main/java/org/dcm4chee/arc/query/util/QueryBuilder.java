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

package org.dcm4chee.arc.query.util;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.*;
import com.querydsl.core.types.dsl.*;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.hibernate.HibernateQuery;
import org.dcm4che3.data.*;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.AttributeFilter;
import org.dcm4chee.arc.conf.Entity;
import org.dcm4chee.arc.conf.SPSStatus;
import org.dcm4chee.arc.entity.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Aug 2015
 */
public class QueryBuilder {

    public static final QPersonName patientName = new QPersonName("patientName");
    public static final QPersonName referringPhysicianName = new QPersonName("referringPhysicianName");
    public static final QPersonName performingPhysicianName = new QPersonName("performingPhysicianName");
    public static final QPersonName verifyingObserverName = new QPersonName("verifyingObserverName");
    public static final QPersonName requestingPhysician = new QPersonName("requestingPhysician");
    public static final QPersonName responsiblePerson = new QPersonName("responsiblePerson");
    public static final QAttributesBlob patientAttributesBlob = new QAttributesBlob("patientAttributesBlob");
    public static final QAttributesBlob studyAttributesBlob = new QAttributesBlob("studyAttributesBlob");
    public static final QAttributesBlob seriesAttributesBlob =  new QAttributesBlob("seriesAttributesBlob");
    public static final QAttributesBlob instanceAttributesBlob = new QAttributesBlob("instanceAttributesBlob");
    public static final QAttributesBlob mwlAttributesBlob = new QAttributesBlob("mwlAttributesBlob");
    public static final QIssuerEntity patientIDIssuer = new QIssuerEntity("patientIDIssuer");

    private QueryBuilder() {}

    public static boolean addOrderSpecifier(QueryRetrieveLevel2 level, int tag, Order order,
                                            List<OrderSpecifier<?>> result) {
        switch (level) {
            case IMAGE:
                switch (tag) {
                    case Tag.SOPInstanceUID:
                        return result.add(orderSpecifierOf(QInstance.instance.sopInstanceUID, order));
                    case Tag.SOPClassUID:
                        return result.add(orderSpecifierOf(QInstance.instance.sopClassUID, order));
                    case Tag.InstanceNumber:
                        return result.add(orderSpecifierOf(QInstance.instance.instanceNumber, order));
                    case Tag.VerificationFlag:
                        return result.add(orderSpecifierOf(QInstance.instance.verificationFlag, order));
                    case Tag.CompletionFlag:
                        return result.add(orderSpecifierOf(QInstance.instance.completionFlag, order));
                    case Tag.ContentDate:
                        return result.add(orderSpecifierOf(QInstance.instance.contentDate, order));
                    case Tag.ContentTime:
                        return result.add(orderSpecifierOf(QInstance.instance.contentTime, order));
                }
            case SERIES:
                switch (tag) {
                    case Tag.SeriesInstanceUID:
                        return result.add(orderSpecifierOf(QSeries.series.seriesInstanceUID, order));
                    case Tag.SeriesNumber:
                        return result.add(orderSpecifierOf(QSeries.series.seriesNumber, order));
                    case Tag.Modality:
                        return result.add(orderSpecifierOf(QSeries.series.modality, order));
                    case Tag.BodyPartExamined:
                        return result.add(orderSpecifierOf(QSeries.series.bodyPartExamined, order));
                    case Tag.Laterality:
                        return result.add(orderSpecifierOf(QSeries.series.laterality, order));
                    case Tag.PerformedProcedureStepStartDate:
                        return result.add(orderSpecifierOf(QSeries.series.performedProcedureStepStartDate, order));
                    case Tag.PerformedProcedureStepStartTime:
                        return result.add(orderSpecifierOf(QSeries.series.performedProcedureStepStartTime, order));
                    case Tag.PerformingPhysicianName:
                        result.add(orderSpecifierOf(performingPhysicianName.familyName, order));
                        result.add(orderSpecifierOf(performingPhysicianName.givenName, order));
                        return result.add(orderSpecifierOf(performingPhysicianName.middleName, order));
                    case Tag.SeriesDescription:
                        return result.add(orderSpecifierOf(QSeries.series.seriesDescription, order));
                    case Tag.StationName:
                        return result.add(orderSpecifierOf(QSeries.series.stationName, order));
                    case Tag.InstitutionName:
                        return result.add(orderSpecifierOf(QSeries.series.institutionName, order));
                    case Tag.InstitutionalDepartmentName:
                        return result.add(orderSpecifierOf(QSeries.series.institutionalDepartmentName, order));
                }
            case STUDY:
                switch (tag) {
                    case Tag.StudyInstanceUID:
                        return result.add(orderSpecifierOf(QStudy.study.studyInstanceUID, order));
                    case Tag.StudyID:
                        return result.add(orderSpecifierOf(QStudy.study.studyID, order));
                    case Tag.StudyDate:
                        return result.add(orderSpecifierOf(QStudy.study.studyDate, order));
                    case Tag.StudyTime:
                        return result.add(orderSpecifierOf(QStudy.study.studyTime, order));
                    case Tag.ReferringPhysicianName:
                        result.add(orderSpecifierOf(referringPhysicianName.familyName, order));
                        result.add(orderSpecifierOf(referringPhysicianName.givenName, order));
                        return result.add(orderSpecifierOf(referringPhysicianName.middleName, order));
                    case Tag.StudyDescription:
                        return result.add(orderSpecifierOf(QStudy.study.studyDescription, order));
                    case Tag.AccessionNumber:
                        return result.add(orderSpecifierOf(QStudy.study.accessionNumber, order));
                }
            case PATIENT:
                switch (tag) {
                    case Tag.PatientName:
                        result.add(orderSpecifierOf(patientName.familyName, order));
                        result.add(orderSpecifierOf(patientName.givenName, order));
                        return result.add(orderSpecifierOf(patientName.middleName, order));
                    case Tag.PatientSex:
                        return result.add(orderSpecifierOf(QPatient.patient.patientSex, order));
                    case Tag.PatientBirthDate:
                        return result.add(orderSpecifierOf(QPatient.patient.patientBirthDate, order));
                }
        }
        return false;
    }

    public static boolean addMWLOrderSpecifier(int tag, Order order, List<OrderSpecifier<?>> result) {
        if (addOrderSpecifier(QueryRetrieveLevel2.PATIENT, tag, order, result))
            return true;

        switch (tag) {
            case Tag.AccessionNumber:
                return result.add(orderSpecifierOf(QMWLItem.mWLItem.accessionNumber, order));
            case Tag.Modality:
                return result.add(orderSpecifierOf(QMWLItem.mWLItem.modality, order));
            case Tag.StudyInstanceUID:
                return result.add(orderSpecifierOf(QMWLItem.mWLItem.studyInstanceUID, order));
             case Tag.ScheduledProcedureStepStartDate:
                return result.add(orderSpecifierOf(QMWLItem.mWLItem.scheduledStartDate, order));
            case Tag.ScheduledProcedureStepStartTime:
                return result.add(orderSpecifierOf(QMWLItem.mWLItem.scheduledStartTime, order));
            case Tag.ScheduledPerformingPhysicianName:
                result.add(orderSpecifierOf(performingPhysicianName.familyName, order));
                result.add(orderSpecifierOf(performingPhysicianName.givenName, order));
                return result.add(orderSpecifierOf(performingPhysicianName.middleName, order));
            case Tag.ScheduledProcedureStepID:
                return result.add(orderSpecifierOf(QMWLItem.mWLItem.scheduledProcedureStepID, order));
            case Tag.RequestedProcedureID:
                return result.add(orderSpecifierOf(QMWLItem.mWLItem.requestedProcedureID, order));
        }
        return false;
    }

    private static OrderSpecifier orderSpecifierOf(StringPath path, Order order) {
        return order == Order.ASC ? path.asc() : path.desc();
    }

    private static OrderSpecifier orderSpecifierOf(NumberPath<Integer> path, Order order) {
        return order == Order.ASC ? path.asc() : path.desc();
    }

    public static HibernateQuery<Tuple> applyPatientLevelJoins(
            HibernateQuery<Tuple> query, IDWithIssuer[] pids, Attributes keys, QueryParam queryParam,
            boolean orderByPatientName) {
        query = applyPatientIDJoins(query, pids);
        if (!isUniversalMatching(keys.getString(Tag.PatientName)))
            query = query.join(QPatient.patient.patientName, QueryBuilder.patientName);
        else if (orderByPatientName)
            query = query.leftJoin(QPatient.patient.patientName, QueryBuilder.patientName);
        if (!isUniversalMatching(keys.getString(Tag.ResponsiblePerson)))
            query = query.join(QPatient.patient.responsiblePerson, QueryBuilder.responsiblePerson);
        query = query.join(QPatient.patient.attributesBlob, QueryBuilder.patientAttributesBlob);
        return query;

    }

    public static HibernateQuery<Tuple> applyPatientIDJoins(
            HibernateQuery<Tuple> query, IDWithIssuer[] pids) {
        if (pids.length > 0) {
            query = query.join(QPatient.patient.patientID, QPatientID.patientID);
            if (containsIssuer(pids))
                query.leftJoin(QPatientID.patientID.issuer, patientIDIssuer);
        }
        return query;
    }

    public static Predicate patientIDPredicate(IDWithIssuer[] pids) {
        if (pids.length == 0)
            return null;

        BooleanBuilder result = new BooleanBuilder();
        for (IDWithIssuer pid : pids)
            result.or(idWithIssuer(QPatient.patient.patientID.id, patientIDIssuer, pid.getID(), pid.getIssuer()));

        if (!result.hasValue())
            return null;

        return result;
    }

    public static void addPatientLevelPredicates(
            BooleanBuilder builder, IDWithIssuer[] pids, Attributes keys, QueryParam queryParam) {
        builder.and(QPatient.patient.mergedWith.isNull());
        builder.and(patientIDPredicate(pids));
        builder.and(MatchPersonName.match(QueryBuilder.patientName, keys.getString(Tag.PatientName, "*"), queryParam));
        builder.and(wildCard(QPatient.patient.patientSex, keys.getString(Tag.PatientSex, "*").toUpperCase(),
                false));
        builder.and(MatchDateTimeRange.rangeMatch(QPatient.patient.patientBirthDate, keys, Tag.PatientBirthDate,
                MatchDateTimeRange.FormatDate.DA));
        AttributeFilter attrFilter = queryParam.getAttributeFilter(Entity.Patient);
        builder.and(wildCard(QPatient.patient.patientCustomAttribute1,
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute1(), "*"), true));
        builder.and(wildCard(QPatient.patient.patientCustomAttribute2,
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute2(), "*"), true));
        builder.and(wildCard(QPatient.patient.patientCustomAttribute3,
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute3(), "*"), true));
        if (!queryParam.isWithoutStudies())
            builder.and(QPatient.patient.numberOfStudies.gt(0));
        builder.and(MatchPersonName.match(QueryBuilder.responsiblePerson, keys.getString(Tag.ResponsiblePerson, "*"), queryParam));
    }

    public static HibernateQuery<Tuple> applyStudyLevelJoins(
            HibernateQuery<Tuple> query, Attributes keys, QueryParam queryParam) {
        query = query.innerJoin(QStudy.study.patient, QPatient.patient);
        query = query.leftJoin(QStudy.study.queryAttributes, QStudyQueryAttributes.studyQueryAttributes)
                .on(QStudyQueryAttributes.studyQueryAttributes.viewID.eq(queryParam.getViewID()));

        if (!isUniversalMatching(keys.getString(Tag.AccessionNumber))
                && !isUniversalMatching(keys.getNestedDataset(Tag.IssuerOfAccessionNumberSequence))) {
            query = query.leftJoin(QStudy.study.issuerOfAccessionNumber);
        }

        if (!isUniversalMatching(keys.getString(Tag.ReferringPhysicianName))) {
            query = query.join(QStudy.study.referringPhysicianName, QueryBuilder.referringPhysicianName);
        }
        query = query.join(QStudy.study.attributesBlob, QueryBuilder.studyAttributesBlob);
        return query;
    }

    public static Predicate uidsPredicate(StringPath path, String[] values) {
        if (values == null || values.length == 0 || values[0].equals("*"))
            return null;

        return path.in(values);
    }

    public static void addStudyLevelPredicates(BooleanBuilder builder, Attributes keys,
                                               QueryParam queryParam, QueryRetrieveLevel2 queryRetrieveLevel) {
        boolean combinedDatetimeMatching = queryParam.isCombinedDatetimeMatching();
        builder.and(accessControl(queryParam.getAccessControlIDs()));
        builder.and(uidsPredicate(QStudy.study.studyInstanceUID, keys.getStrings(Tag.StudyInstanceUID)));
        builder.and(wildCard(QStudy.study.studyID, keys.getString(Tag.StudyID, "*"), false));
        builder.and(MatchDateTimeRange.rangeMatch(
                QStudy.study.studyDate, QStudy.study.studyTime,
                Tag.StudyDate, Tag.StudyTime, Tag.StudyDateAndTime,
                keys, combinedDatetimeMatching));
        builder.and(MatchPersonName.match(QueryBuilder.referringPhysicianName,
                keys.getString(Tag.ReferringPhysicianName, "*"), queryParam));
        builder.and(wildCard(QStudy.study.studyDescription,
                keys.getString(Tag.StudyDescription, "*"), true));
        String accNo = keys.getString(Tag.AccessionNumber, "*");
        if (!accNo.equals("*")) {
            Issuer issuer = Issuer.valueOf(keys.getNestedDataset(Tag.IssuerOfAccessionNumberSequence));
            if (issuer == null)
                issuer = queryParam.getDefaultIssuerOfAccessionNumber();
            builder.and(idWithIssuer(QStudy.study.accessionNumber, QStudy.study.issuerOfAccessionNumber, accNo, issuer));
        }
        builder.and(seriesAttributesInStudy(keys, queryParam));
        builder.and(sopClassInStudy(keys.getString(Tag.SOPClassesInStudy, "*")));
        builder.and(code(QStudy.study.procedureCodes, keys.getNestedDataset(Tag.ProcedureCodeSequence)));
        if (queryParam.isHideNotRejectedInstances())
            builder.and(QStudy.study.rejectionState.ne(RejectionState.NONE));
        AttributeFilter attrFilter = queryParam.getAttributeFilter(Entity.Study);
        builder.and(wildCard(QStudy.study.studyCustomAttribute1,
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute1(), "*"), true));
        builder.and(wildCard(QStudy.study.studyCustomAttribute2,
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute2(), "*"), true));
        builder.and(wildCard(QStudy.study.studyCustomAttribute3,
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute3(), "*"), true));
        if (queryParam.getStudyReceiveDateTime() != null)
            builder.and(ExpressionUtils.and(MatchDateTimeRange.range(
                    QStudy.study.createdTime, getDateRange(queryParam.getStudyReceiveDateTime()), MatchDateTimeRange.FormatDate.DT),
                    QStudy.study.createdTime.isNotNull()));
        if (queryRetrieveLevel == QueryRetrieveLevel2.STUDY) {
            if (queryParam.isExpired())
                builder.and(QStudy.study.expirationDate.loe(DateTimeFormatter.BASIC_ISO_DATE.format(LocalDate.now())));
            if (queryParam.isIncomplete())
                builder.and(QStudy.study.failedSOPInstanceUIDList.isNotNull());
            if (queryParam.isRetrieveFailed())
                builder.and(QStudy.study.failedRetrieves.gt(0));
        }
    }

    public static Predicate accessControl(String[] accessControlIDs) {
        if (accessControlIDs.length == 0) {
            return null;
        }
        String[] a = new String[accessControlIDs.length + 1];
        a[0] = "*";
        System.arraycopy(accessControlIDs, 0, a, 1, accessControlIDs.length);
        return QStudy.study.accessControlID.in(accessControlIDs);
    }

    public static HibernateQuery<Tuple> applySeriesLevelJoins(
            HibernateQuery<Tuple> query, Attributes keys, QueryParam queryParam) {
        query = query.innerJoin(QSeries.series.study, QStudy.study);
        query = query.leftJoin(QSeries.series.queryAttributes, QSeriesQueryAttributes.seriesQueryAttributes)
                .on(QSeriesQueryAttributes.seriesQueryAttributes.viewID.eq(
                        queryParam.getViewID()));
        if (!isUniversalMatching(keys.getString(Tag.PerformingPhysicianName))) {
            query = query.join(QSeries.series.performingPhysicianName, QueryBuilder.performingPhysicianName);
        }
        query = query.join(QSeries.series.attributesBlob, QueryBuilder.seriesAttributesBlob);
        return query;
    }

    public static void addSeriesLevelPredicates(BooleanBuilder builder, Attributes keys,
                                                QueryParam queryParam, QueryRetrieveLevel2 queryRetrieveLevel) {
        builder.and(uidsPredicate(QSeries.series.seriesInstanceUID, keys.getStrings(Tag.SeriesInstanceUID)));
        builder.and(numberPredicate(QSeries.series.seriesNumber, keys.getString(Tag.SeriesNumber, "0")));
        builder.and(wildCard(QSeries.series.modality, keys.getString(Tag.Modality, "*").toUpperCase(),
                false));
        builder.and(wildCard(QSeries.series.bodyPartExamined, keys.getString(Tag.BodyPartExamined, "*").toUpperCase(),
                false));
        builder.and(wildCard(QSeries.series.laterality, keys.getString(Tag.Laterality, "*").toUpperCase(),
                false));
        builder.and(MatchDateTimeRange.rangeMatch(
                QSeries.series.performedProcedureStepStartDate, QSeries.series.performedProcedureStepStartTime,
                Tag.PerformedProcedureStepStartDate, Tag.PerformedProcedureStepStartTime,
                Tag.PerformedProcedureStepStartDateAndTime,
                keys, queryParam.isCombinedDatetimeMatching()));
        builder.and(MatchPersonName.match(QueryBuilder.performingPhysicianName,
                keys.getString(Tag.PerformingPhysicianName, "*"), queryParam));
        builder.and(wildCard(QSeries.series.seriesDescription, keys.getString(Tag.SeriesDescription, "*"),
                true));
        builder.and(wildCard(QSeries.series.stationName, keys.getString(Tag.StationName, "*"), true));
        builder.and(wildCard(QSeries.series.institutionalDepartmentName, keys.getString(Tag.InstitutionalDepartmentName, "*"),
                true));
        builder.and(wildCard(QSeries.series.institutionName, keys.getString(Tag.InstitutionName, "*"),
                true));
        builder.and(requestAttributes(keys.getNestedDataset(Tag.RequestAttributesSequence), queryParam));
        builder.and(code(QSeries.series.institutionCode, keys.getNestedDataset(Tag.InstitutionCodeSequence)));
        if (queryParam.isHideNotRejectedInstances())
            builder.and(QSeries.series.rejectionState.ne(RejectionState.NONE));
        AttributeFilter attrFilter = queryParam.getAttributeFilter(Entity.Series);
        builder.and(wildCard(QSeries.series.seriesCustomAttribute1,
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute1(), "*"), true));
        builder.and(wildCard(QSeries.series.seriesCustomAttribute2,
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute2(), "*"), true));
        builder.and(wildCard(QSeries.series.seriesCustomAttribute3,
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute3(), "*"), true));
        if (queryRetrieveLevel == QueryRetrieveLevel2.SERIES) {
            if (queryParam.isExpired())
                builder.and(QSeries.series.expirationDate.loe(DateTimeFormatter.BASIC_ISO_DATE.format(LocalDate.now())));
            if (queryParam.isIncomplete())
                builder.and(QSeries.series.failedSOPInstanceUIDList.isNotNull());
            if (queryParam.isRetrieveFailed())
                builder.and(QSeries.series.failedRetrieves.gt(0));
            if (queryParam.getSendingApplicationEntityTitleOfSeries() != null)
                builder.and(QSeries.series.sourceAET.eq(queryParam.getSendingApplicationEntityTitleOfSeries()));
        }
    }

    public static HibernateQuery<Tuple> applyInstanceLevelJoins(
            HibernateQuery<Tuple> query, Attributes keys, QueryParam queryParam) {
        query = query.innerJoin(QInstance.instance.series, QSeries.series);
        query = query.join(QInstance.instance.attributesBlob, QueryBuilder.instanceAttributesBlob);
        query = query.leftJoin(QInstance.instance.rejectionNoteCode, QCodeEntity.codeEntity);
        return query;
    }

    public static void addInstanceLevelPredicates(BooleanBuilder builder, Attributes keys, QueryParam queryParam) {
        boolean combinedDatetimeMatching = queryParam.isCombinedDatetimeMatching();
        builder.and(uidsPredicate(QInstance.instance.sopInstanceUID, keys.getStrings(Tag.SOPInstanceUID)));
        builder.and(uidsPredicate(QInstance.instance.sopClassUID, keys.getStrings(Tag.SOPClassUID)));
        builder.and(numberPredicate(QInstance.instance.instanceNumber, keys.getString(Tag.InstanceNumber, "0")));
        builder.and(wildCard(QInstance.instance.verificationFlag,
                keys.getString(Tag.VerificationFlag, "*").toUpperCase(), false));
        builder.and(wildCard(QInstance.instance.completionFlag,
                keys.getString(Tag.CompletionFlag, "*").toUpperCase(), false));
        builder.and(MatchDateTimeRange.rangeMatch(
                QInstance.instance.contentDate, QInstance.instance.contentTime,
                Tag.ContentDate, Tag.ContentTime, Tag.ContentDateAndTime,
                keys, combinedDatetimeMatching));
        builder.and(code(QInstance.instance.conceptNameCode, keys.getNestedDataset(Tag.ConceptNameCodeSequence)));
        builder.and(verifyingObserver(keys.getNestedDataset(Tag.VerifyingObserverSequence),
                queryParam));
        Sequence contentSeq = keys.getSequence(Tag.ContentSequence);
        if (contentSeq != null)
            for (Attributes item : contentSeq)
                builder.and(contentItem(item));
        AttributeFilter attrFilter = queryParam
                .getAttributeFilter(Entity.Instance);
        builder.and(wildCard(
                QInstance.instance.instanceCustomAttribute1,
                AttributeFilter.selectStringValue(keys,
                        attrFilter.getCustomAttribute1(), "*"),
                true));
        builder.and(wildCard(
                QInstance.instance.instanceCustomAttribute2,
                AttributeFilter.selectStringValue(keys,
                        attrFilter.getCustomAttribute2(), "*"),
                true));
        builder.and(wildCard(
                QInstance.instance.instanceCustomAttribute3,
                AttributeFilter.selectStringValue(keys,
                        attrFilter.getCustomAttribute3(), "*"),
                true));
        builder.and(hideRejectedInstance(queryParam));
        builder.and(hideRejectionNote(queryParam));
    }

    public static HibernateQuery<Tuple> applyMWLJoins(
            HibernateQuery<Tuple> query, Attributes keys, QueryParam queryParam) {
        query = query.innerJoin(QMWLItem.mWLItem.patient, QPatient.patient);

        if (!isUniversalMatching(keys.getString(Tag.AccessionNumber))
                && !isUniversalMatching(keys.getNestedDataset(Tag.IssuerOfAccessionNumberSequence))) {
            query = query.leftJoin(QMWLItem.mWLItem.issuerOfAccessionNumber);
        }

        Attributes sps = keys.getNestedDataset(Tag.ScheduledProcedureStepSequence);
        if (sps != null && !isUniversalMatching(sps.getString(Tag.ScheduledPerformingPhysicianName))) {
            query = query.join(QMWLItem.mWLItem.scheduledPerformingPhysicianName, QueryBuilder.performingPhysicianName);
        }
        query = query.join(QMWLItem.mWLItem.attributesBlob, QueryBuilder.mwlAttributesBlob);
        return query;
    }

    public static void addMWLPredicates(BooleanBuilder builder, Attributes keys, QueryParam queryParam) {
        builder.and(uidsPredicate(QMWLItem.mWLItem.studyInstanceUID, keys.getStrings(Tag.StudyInstanceUID)));
        builder.and(wildCard(QMWLItem.mWLItem.requestedProcedureID, keys.getString(Tag.RequestedProcedureID, "*"),
                false));
        String accNo = keys.getString(Tag.AccessionNumber, "*");
        if (!accNo.equals("*")) {
            Issuer issuer = Issuer.valueOf(keys.getNestedDataset(Tag.IssuerOfAccessionNumberSequence));
            if (issuer == null) {
                issuer = queryParam.getDefaultIssuerOfAccessionNumber();
            }
            builder.and(idWithIssuer(
                    QMWLItem.mWLItem.accessionNumber, QMWLItem.mWLItem.issuerOfAccessionNumber, accNo, issuer));
        }
        Attributes sps = keys.getNestedDataset(Tag.ScheduledProcedureStepSequence);
        if (sps != null) {
            builder.and(wildCard(QMWLItem.mWLItem.scheduledProcedureStepID,
                    sps.getString(Tag.ScheduledProcedureStepID, "*"), false));
            builder.and(MatchDateTimeRange.rangeMatch(
                    QMWLItem.mWLItem.scheduledStartDate, QMWLItem.mWLItem.scheduledStartTime,
                    Tag.ScheduledProcedureStepStartDate, Tag.ScheduledProcedureStepStartDate,
                    Tag.ScheduledProcedureStepStartDateAndTime,
                    sps, true));
            builder.and(MatchPersonName.match(QueryBuilder.performingPhysicianName,
                    sps.getString(Tag.ScheduledPerformingPhysicianName, "*"), queryParam));
            builder.and(wildCard(QMWLItem.mWLItem.modality, sps.getString(Tag.Modality, "*").toUpperCase(),
                    false));
            if (sps.getString(Tag.ScheduledProcedureStepStatus) != null)
                builder.and(showSPSWithStatus(sps));
            if (sps.getString(Tag.ScheduledStationAETitle) != null)
                builder.and(QMWLItem.mWLItem.scheduledStationAETs.contains(sps.getString(Tag.ScheduledStationAETitle)));
        }
        builder.and(hideSPSWithStatus(queryParam));
    }

    private static Predicate hideSPSWithStatus(QueryParam queryParam) {
        SPSStatus[] hideSPSWithStatusFromMWL = queryParam.getHideSPSWithStatusFromMWL();
        return (hideSPSWithStatusFromMWL.length > 0)
                ? QMWLItem.mWLItem.status.notIn(hideSPSWithStatusFromMWL)
                : null;
    }

    private static Predicate showSPSWithStatus(Attributes sps) {
        SPSStatus status = SPSStatus.valueOf(sps.getString(Tag.ScheduledProcedureStepStatus).toUpperCase());
        switch(status) {
            case SCHEDULED:
            case ARRIVED:
            case READY:
                return QMWLItem.mWLItem.status.eq(status);
            default:
                return null;
        }
    }

    public static Predicate hideRejectedInstance(QueryParam queryParam) {
        return hideRejectedInstance(
                queryParam.getShowInstancesRejectedByCode(),
                queryParam.isHideNotRejectedInstances());
    }

    public static Predicate hideRejectedInstance(CodeEntity[] codes, boolean hideNotRejectedInstances) {
        if (codes.length == 0)
            return hideNotRejectedInstances
                    ? QInstance.instance.rejectionNoteCode.isNotNull()
                    : QInstance.instance.rejectionNoteCode.isNull();

        BooleanExpression showRejected = QInstance.instance.rejectionNoteCode.in(codes);
        return hideNotRejectedInstances
                ? showRejected
                : QInstance.instance.rejectionNoteCode.isNull().or(showRejected);
    }

    public static Predicate hideRejectionNote(QueryParam queryParam) {
        return hideRejectionNote(queryParam.getHideRejectionNotesWithCode());
    }

    public static Predicate hideRejectionNote(CodeEntity[] codes) {
        if (codes.length == 0)
            return null;

        return QInstance.instance.conceptNameCode.isNull()
                .or(QInstance.instance.conceptNameCode.notIn(codes));
    }

    static boolean containsIssuer(IDWithIssuer[] pids) {
        for (IDWithIssuer pid : pids)
            if (pid.getIssuer() != null)
                return true;
        return false;
    }

    static Predicate idWithIssuer(StringPath idPath, QIssuerEntity issuerPath, String id, Issuer issuer) {
        Predicate predicate = wildCard(idPath, id);
        if (predicate == null)
            return null;

        if (issuer != null) {
            String entityID = issuer.getLocalNamespaceEntityID();
            String entityUID = issuer.getUniversalEntityID();
            String entityUIDType = issuer.getUniversalEntityIDType();
            if (!isUniversalMatching(entityID))
                predicate = ExpressionUtils.and(predicate,
                        ExpressionUtils.or(issuerPath.issuer.localNamespaceEntityID.isNull(),
                            issuerPath.issuer.localNamespaceEntityID.eq(entityID)));
            if (!isUniversalMatching(entityUID))
                predicate = ExpressionUtils.and(predicate,
                        ExpressionUtils.or(issuerPath.issuer.universalEntityID.isNull(),
                            ExpressionUtils.and(QIssuerEntity.issuerEntity.issuer.universalEntityID.eq(entityUID),
                                    issuerPath.issuer.universalEntityIDType.eq(entityUIDType))));
        }
        return predicate;
    }

    static boolean isUniversalMatching(Attributes item) {
        return item == null || item.isEmpty();
    }

    static boolean isUniversalMatching(String value) {
        return value == null || value.equals("*");
    }

    private static boolean isUniversalMatching(Integer value) {
        return value == null || value.equals(0);
    }

    static Predicate wildCard(StringPath path, String value) {
        return wildCard(path, value, false);
    }
    
    static Predicate wildCard(StringPath path, String value, boolean ignoreCase) {
        if (isUniversalMatching(value))
            return null;

        Predicate predicate;
        StringExpression expr = ignoreCase && StringUtils.isUpperCase(value) ? path.toUpperCase() : path;
        if (containsWildcard(value)) {
            String pattern = toLikePattern(value);
            if (pattern.equals("%"))
                return null;

            predicate = expr.like(pattern, '!');
        } else
            predicate = expr.eq(value);

        return predicate;
    }

    static Predicate numberPredicate(NumberPath<Integer> path, String value) {
        try {
            Integer v = Integer.parseInt(value);
            if (isUniversalMatching(v))
                return null;
            return path.eq(v);
        } catch (NumberFormatException e) {
            return null;
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

    static Predicate seriesAttributesInStudy(Attributes keys, QueryParam queryParam) {
        BooleanBuilder result = new BooleanBuilder();
        result.and(wildCard(QSeries.series.institutionName, keys.getString(Tag.InstitutionName), true))
            .and(wildCard(QSeries.series.institutionalDepartmentName, keys.getString(Tag.InstitutionalDepartmentName), true))
            .and(wildCard(QSeries.series.stationName, keys.getString(Tag.StationName), true))
            .and(wildCard(QSeries.series.seriesDescription, keys.getString(Tag.SeriesDescription), true))
            .and(wildCard(QSeries.series.modality, keys.getString(Tag.ModalitiesInStudy, "*").toUpperCase(), false))
            .and(wildCard(QSeries.series.bodyPartExamined, keys.getString(Tag.BodyPartExamined, "*").toUpperCase(), false));
        if (queryParam.getSendingApplicationEntityTitleOfSeries() != null)
            result.and(wildCard(QSeries.series.sourceAET, queryParam.getSendingApplicationEntityTitleOfSeries().toUpperCase(), false));
        if (!result.hasValue())
            return null;
        return JPAExpressions.selectFrom(QSeries.series)
                .where(QSeries.series.study.eq(QStudy.study), result).exists();
    }

    static Predicate sopClassInStudy(String sopClass) {
        if (sopClass.equals("*"))
            return null;

        return JPAExpressions.selectFrom(QInstance.instance)
                .join(QInstance.instance.series, QSeries.series)
                .where(QSeries.series.study.eq(QStudy.study),
                        wildCard(QInstance.instance.sopClassUID, sopClass,
                                false)).exists();
    }

    static Predicate code(Attributes item) {
        if (item == null || item.isEmpty())
            return null;

        return ExpressionUtils.allOf(
                wildCard(QCodeEntity.codeEntity.codeValue,
                        item.getString(Tag.CodeValue, "*")),
                wildCard(QCodeEntity.codeEntity.codingSchemeDesignator,
                        item.getString(Tag.CodingSchemeDesignator, "*")),
                wildCard(QCodeEntity.codeEntity.codingSchemeVersion,
                        item.getString(Tag.CodingSchemeVersion, "*")));
    }

    static Predicate code(QCodeEntity code, Attributes item) {
        Predicate predicate = code(item);
        if (predicate == null)
            return null;

        return JPAExpressions.selectFrom(QCodeEntity.codeEntity)
                .where(QCodeEntity.codeEntity.eq(code), predicate).exists();
    }


    static Predicate code(CollectionPath<CodeEntity, QCodeEntity> codes, Attributes item) {
        Predicate predicate = code(item);
        if (predicate == null)
            return null;

        return JPAExpressions.selectFrom(QCodeEntity.codeEntity)
                        .where(codes.contains(QCodeEntity.codeEntity), predicate).exists();
    }

    static Predicate requestAttributes(Attributes item, QueryParam queryParam) {
        if (item == null || item.isEmpty())
            return null;

        BooleanBuilder builder = new BooleanBuilder();
        String accNo = item.getString(Tag.AccessionNumber, "*");
        Issuer issuerOfAccessionNumber = null;
        if (!accNo.equals("*")) {
            issuerOfAccessionNumber = Issuer.valueOf(item
                    .getNestedDataset(Tag.IssuerOfAccessionNumberSequence));
            if (issuerOfAccessionNumber == null)
                issuerOfAccessionNumber = queryParam.getDefaultIssuerOfAccessionNumber();
            builder.and(idWithIssuer(
                            QSeriesRequestAttributes.seriesRequestAttributes.accessionNumber,
                            QSeriesRequestAttributes.seriesRequestAttributes.issuerOfAccessionNumber,
                            accNo, issuerOfAccessionNumber));
        }
        builder.and(wildCard(
                QSeriesRequestAttributes.seriesRequestAttributes.requestingService,
                item.getString(Tag.RequestingService, "*"), true));
        Predicate matchRequestingPhysician = MatchPersonName.match(
                QueryBuilder.requestingPhysician,
                item.getString(Tag.RequestingPhysician, "*"), queryParam);
        builder.and(matchRequestingPhysician);
        builder.and(wildCard(
                QSeriesRequestAttributes.seriesRequestAttributes.requestedProcedureID,
                item.getString(Tag.RequestedProcedureID, "*"), false));
        builder.and(uidsPredicate(QSeriesRequestAttributes.seriesRequestAttributes.studyInstanceUID,
                item.getStrings(Tag.StudyInstanceUID)));
        builder.and(wildCard(
                QSeriesRequestAttributes.seriesRequestAttributes.scheduledProcedureStepID,
                item.getString(Tag.ScheduledProcedureStepID, "*"),
                false));

        if (!builder.hasValue())
            return null;

        JPQLQuery<SeriesRequestAttributes> subQuery = JPAExpressions.selectFrom(QSeriesRequestAttributes.seriesRequestAttributes);

        if (issuerOfAccessionNumber != null)
            subQuery.leftJoin(
                    QSeriesRequestAttributes.seriesRequestAttributes.issuerOfAccessionNumber,
                    QIssuerEntity.issuerEntity);

        if (matchRequestingPhysician != null)
            subQuery = subQuery.join(QueryBuilder.requestingPhysician, QueryBuilder.requestingPhysician);

        return subQuery.where(QSeriesRequestAttributes.seriesRequestAttributes.in(QSeries.series.requestAttributes),
                                builder).exists();
    }

    static Predicate verifyingObserver(Attributes item, QueryParam queryParam) {
        if (item == null || item.isEmpty())
            return null;

        Predicate matchVerifyingObserverName = MatchPersonName.match(
                QueryBuilder.verifyingObserverName,
                item.getString(Tag.VerifyingObserverName, "*"),
                queryParam);
        Predicate predicate = ExpressionUtils
                .allOf(MatchDateTimeRange.rangeMatch(
                                QVerifyingObserver.verifyingObserver.verificationDateTime,
                                item, Tag.VerificationDateTime,
                                MatchDateTimeRange.FormatDate.DT),
                        matchVerifyingObserverName);

        if (predicate == null)
            return null;

        JPQLQuery<VerifyingObserver> query = JPAExpressions.selectFrom(QVerifyingObserver.verifyingObserver);

        if (matchVerifyingObserverName != null)
            query = query.join(QVerifyingObserver.verifyingObserver.verifyingObserverName,
                    QueryBuilder.verifyingObserverName);

        return query.where(QVerifyingObserver.verifyingObserver.in(QInstance.instance.verifyingObservers),
                        predicate).exists();
    }

    static Predicate contentItem(Attributes item) {
        String valueType = item.getString(Tag.ValueType);
        if (!("CODE".equals(valueType) || "TEXT".equals(valueType)))
            return null;

        Predicate predicate = ExpressionUtils.allOf(
                code(QContentItem.contentItem.conceptName,
                        item.getNestedDataset(Tag.ConceptNameCodeSequence)),
                wildCard(QContentItem.contentItem.relationshipType, item
                        .getString(Tag.RelationshipType, "*").toUpperCase()),
                code(QContentItem.contentItem.conceptCode,
                        item.getNestedDataset(Tag.ConceptCodeSequence)),
                wildCard(QContentItem.contentItem.textValue,
                        item.getString(Tag.TextValue, "*"), true));
        if (predicate == null)
            return null;

        return JPAExpressions.selectFrom(QContentItem.contentItem)
                .where(QInstance.instance.contentItems
                                .contains(QContentItem.contentItem),
                        predicate).exists();
    }

    private static DateRange getDateRange(String s) {
        String[] range = splitRange(s);
        DatePrecision precision = new DatePrecision();
        Date start = range[0] == null ? null
                : VR.DT.toDate(range[0], null, 0, false, null, precision);
        Date end = range[1] == null ? null
                : VR.DT.toDate(range[1], null, 0, true, null, precision);
        return new DateRange(start, end);
    }

    private static String[] splitRange(String s) {
        String[] range = new String[2];
        int delim = s.indexOf('-');
        if (delim == -1)
            range[0] = range[1] = s;
        else {
            if (delim > 0)
                range[0] =  s.substring(0, delim);
            if (delim < s.length() - 1)
                range[1] =  s.substring(delim+1);
        }
        return range;
    }
}
