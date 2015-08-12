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

package org.dcm4chee.archive.query.util;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.*;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.hibernate.HibernateQuery;
import org.dcm4che3.data.*;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.archive.conf.AttributeFilter;
import org.dcm4chee.archive.conf.Entity;
import org.dcm4chee.archive.entity.*;

import java.util.Collection;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2015
 */
public class QueryBuilder {

    public static final QPersonName patientName = new QPersonName("patientName");
    public static final QPersonName referringPhysicianName = new QPersonName("referringPhysicianName");
    public static final QPersonName performingPhysicianName = new QPersonName("performingPhysicianName");
    public static final QPersonName verifyingObserverName = new QPersonName("verifyingObserverName");
    public static final QPersonName requestingPhysician = new QPersonName("requestingPhysician");
    public static final QAttributesBlob patientAttributesBlob = new QAttributesBlob("patientAttributesBlob");
    public static final QAttributesBlob studyAttributesBlob = new QAttributesBlob("studyAttributesBlob");
    public static final QAttributesBlob seriesAttributesBlob =  new QAttributesBlob("seriesAttributesBlob");
    public static final QAttributesBlob instanceAttributesBlob = new QAttributesBlob("instanceAttributesBlob");
    public static final QIssuerEntity patientIDIssuer = new QIssuerEntity("patientIDIssuer");

    private QueryBuilder() {}

    public static HibernateQuery<Tuple> applyPatientLevelJoins(
            HibernateQuery<Tuple> query, IDWithIssuer[] pids, Attributes keys, QueryParam queryParam) {
        boolean matchUnknown = queryParam.isMatchUnknown();
        if (pids.length > 0) {
            query = matchUnknown
                    ? query.leftJoin(QPatient.patient.patientID, QPatientID.patientID)
                    : query.join(QPatient.patient.patientID, QPatientID.patientID);
            if (containsIssuer(pids))
                query.leftJoin(QPatientID.patientID.issuer, patientIDIssuer);
        }
        if (!isUniversalMatching(keys.getString(Tag.PatientName)))
            query = matchUnknown
                    ? query.leftJoin(QPatient.patient.patientName, QueryBuilder.patientName)
                    : query.join(QPatient.patient.patientName, QueryBuilder.patientName);

        query = query.join(QPatient.patient.attributesBlob, QueryBuilder.patientAttributesBlob);
        return query;

    }

    public static Predicate patientIDPredicate(IDWithIssuer[] pids, boolean matchUnknown) {
        if (pids.length == 0)
            return null;

        BooleanBuilder result = new BooleanBuilder();
        for (IDWithIssuer pid : pids)
            result.or(idWithIssuer(QPatient.patient.patientID.id, patientIDIssuer, pid.getID(), pid.getIssuer()));

        if (!result.hasValue())
            return null;

        return matchUnknown(result, QPatient.patient.patientID, matchUnknown);
    }

    public static void addPatientLevelPredicates(
            BooleanBuilder builder, IDWithIssuer[] pids, Attributes keys, QueryParam queryParam) {
        boolean matchUnknown = queryParam.isMatchUnknown();
        builder.and(patientIDPredicate(pids, matchUnknown));
        builder.and(MatchPersonName.match(QueryBuilder.patientName, keys.getString(Tag.PatientName, "*"), queryParam));
        builder.and(wildCard(QPatient.patient.patientSex, keys.getString(Tag.PatientSex, "*").toUpperCase(),
                matchUnknown, false));
        builder.and(MatchDateTimeRange.rangeMatch(QPatient.patient.patientBirthDate, keys, Tag.PatientBirthDate,
                MatchDateTimeRange.FormatDate.DA, matchUnknown));
        AttributeFilter attrFilter = queryParam.getAttributeFilter(Entity.Patient);
        builder.and(wildCard(QPatient.patient.patientCustomAttribute1,
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute1(), "*"), matchUnknown, true));
        builder.and(wildCard(QPatient.patient.patientCustomAttribute2,
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute2(), "*"), matchUnknown, true));
        builder.and(wildCard(QPatient.patient.patientCustomAttribute3,
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute3(), "*"), matchUnknown, true));
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
            query = queryParam.isMatchUnknown()
                    ? query.leftJoin(QStudy.study.referringPhysicianName, QueryBuilder.referringPhysicianName)
                    : query.join(QStudy.study.referringPhysicianName, QueryBuilder.referringPhysicianName);
        }
        query = query.join(QStudy.study.attributesBlob, QueryBuilder.studyAttributesBlob);
        return query;
    }

    public static Predicate uidsPredicate(StringPath path, String[] values, boolean matchUnknown) {
        if (values == null || values.length == 0 || values[0].equals("*"))
            return null;

        return matchUnknown(path.in(values), path, matchUnknown);
    }

    public static void addStudyLevelPredicates(BooleanBuilder builder, Attributes keys, QueryParam queryParam) {
        boolean matchUnknown = queryParam.isMatchUnknown();
        boolean combinedDatetimeMatching = queryParam.isCombinedDatetimeMatching();
        builder.and(uidsPredicate(QStudy.study.studyInstanceUID, keys.getStrings(Tag.StudyInstanceUID), false));
        builder.and(wildCard(QStudy.study.studyID, keys.getString(Tag.StudyID, "*"), matchUnknown, false));
        builder.and(MatchDateTimeRange.rangeMatch(
                QStudy.study.studyDate, QStudy.study.studyTime,
                Tag.StudyDate, Tag.StudyTime, Tag.StudyDateAndTime,
                keys, combinedDatetimeMatching, matchUnknown));
        builder.and(MatchPersonName.match(QueryBuilder.referringPhysicianName,
                keys.getString(Tag.ReferringPhysicianName, "*"), queryParam));
        builder.and(wildCard(QStudy.study.studyDescription,
                keys.getString(Tag.StudyDescription, "*"), matchUnknown, true));
        String accNo = keys.getString(Tag.AccessionNumber, "*");
        if (!accNo.equals("*")) {
            Issuer issuer = Issuer.valueOf(keys.getNestedDataset(Tag.IssuerOfAccessionNumberSequence));
            if (issuer == null)
                issuer = queryParam.getDefaultIssuerOfAccessionNumber();
            builder.and(matchUnknown(
                    idWithIssuer(QStudy.study.accessionNumber, QStudy.study.issuerOfAccessionNumber, accNo, issuer),
                    QStudy.study.accessionNumber, matchUnknown));
        }
        builder.and(modalitiesInStudy(keys.getString(Tag.ModalitiesInStudy, "*").toUpperCase(), matchUnknown));
        builder.and(code(QStudy.study.procedureCodes, keys.getNestedDataset(Tag.ProcedureCodeSequence), matchUnknown));
        AttributeFilter attrFilter = queryParam.getAttributeFilter(Entity.Study);
        builder.and(wildCard(QStudy.study.studyCustomAttribute1,
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute1(), "*"), matchUnknown, true));
        builder.and(wildCard(QStudy.study.studyCustomAttribute2,
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute2(), "*"), matchUnknown, true));
        builder.and(wildCard(QStudy.study.studyCustomAttribute3,
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute3(), "*"), matchUnknown, true));

    }

    public static HibernateQuery<Tuple> applySeriesLevelJoins(
            HibernateQuery<Tuple> query, Attributes keys, QueryParam queryParam) {
        query = query.innerJoin(QSeries.series.study, QStudy.study);
        query = query.leftJoin(QSeries.series.queryAttributes, QSeriesQueryAttributes.seriesQueryAttributes)
                .on(QSeriesQueryAttributes.seriesQueryAttributes.viewID.eq(
                        queryParam.getViewID()));
        if (!isUniversalMatching(keys.getString(Tag.PerformingPhysicianName))) {
            query = queryParam.isMatchUnknown()
                    ? query.leftJoin(QSeries.series.performingPhysicianName, QueryBuilder.performingPhysicianName)
                    : query.join(QSeries.series.performingPhysicianName, QueryBuilder.performingPhysicianName);
        }
        query = query.join(QSeries.series.attributesBlob, QueryBuilder.seriesAttributesBlob);
        return query;
    }

    public static void addSeriesLevelPredicates(BooleanBuilder builder, Attributes keys, QueryParam queryParam) {
        boolean matchUnknown = queryParam.isMatchUnknown();
        builder.and(uidsPredicate(QSeries.series.seriesInstanceUID, keys.getStrings(Tag.SeriesInstanceUID), false));
        builder.and(wildCard(QSeries.series.seriesNumber, keys.getString(Tag.SeriesNumber, "*"), matchUnknown, false));
        builder.and(wildCard(QSeries.series.modality, keys.getString(Tag.Modality, "*").toUpperCase(),
                matchUnknown, false));
        builder.and(wildCard(QSeries.series.bodyPartExamined, keys.getString(Tag.BodyPartExamined, "*").toUpperCase(),
                matchUnknown, false));
        builder.and(wildCard(QSeries.series.laterality, keys.getString(Tag.Laterality, "*").toUpperCase(),
                matchUnknown, false));
        builder.and(MatchDateTimeRange.rangeMatch(
                QSeries.series.performedProcedureStepStartDate, QSeries.series.performedProcedureStepStartTime,
                Tag.PerformedProcedureStepStartDate, Tag.PerformedProcedureStepStartTime,
                Tag.PerformedProcedureStepStartDateAndTime,
                keys, queryParam.isCombinedDatetimeMatching(), matchUnknown));
        builder.and(MatchPersonName.match(QueryBuilder.performingPhysicianName,
                keys.getString(Tag.PerformingPhysicianName, "*"), queryParam));
        builder.and(wildCard(QSeries.series.seriesDescription, keys.getString(Tag.SeriesDescription, "*"),
                matchUnknown, true));
        builder.and(wildCard(QSeries.series.stationName, keys.getString(Tag.StationName, "*"), matchUnknown, true));
        builder.and(wildCard(QSeries.series.institutionName, keys.getString(Tag.InstitutionalDepartmentName, "*"),
                matchUnknown, true));
        builder.and(wildCard(QSeries.series.institutionalDepartmentName, keys.getString(Tag.InstitutionName, "*"),
                matchUnknown, true));
        builder.and(requestAttributes(keys.getNestedDataset(Tag.RequestAttributesSequence), queryParam));
        builder.and(code(QSeries.series.institutionCode, keys.getNestedDataset(Tag.InstitutionCodeSequence),
                matchUnknown));
        AttributeFilter attrFilter = queryParam.getAttributeFilter(Entity.Series);
        builder.and(wildCard(QSeries.series.seriesCustomAttribute1,
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute1(), "*"), matchUnknown, true));
        builder.and(wildCard(QSeries.series.seriesCustomAttribute2,
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute2(), "*"), matchUnknown, true));
        builder.and(wildCard(QSeries.series.seriesCustomAttribute3,
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute3(), "*"), matchUnknown, true));
    }

    public static HibernateQuery<Tuple> applyInstanceLevelJoins(
            HibernateQuery<Tuple> query, Attributes keys, QueryParam queryParam) {
        query = query.innerJoin(QInstance.instance.series, QSeries.series);
        query = query.join(QInstance.instance.attributesBlob, QueryBuilder.instanceAttributesBlob);
        return query;
    }

    public static void addInstanceLevelPredicates(BooleanBuilder builder, Attributes keys, QueryParam queryParam) {
        boolean matchUnknown = queryParam.isMatchUnknown();
        boolean combinedDatetimeMatching = queryParam.isCombinedDatetimeMatching();
        builder.and(uidsPredicate(QInstance.instance.sopInstanceUID, keys.getStrings(Tag.SOPInstanceUID), false));
        builder.and(uidsPredicate(QInstance.instance.sopClassUID, keys.getStrings(Tag.SOPClassUID), false));
        builder.and(wildCard(QInstance.instance.instanceNumber, keys.getString(Tag.InstanceNumber, "*"),
                matchUnknown, false));
        builder.and(wildCard(QInstance.instance.verificationFlag,
                keys.getString(Tag.VerificationFlag, "*").toUpperCase(), matchUnknown, false));
        builder.and(wildCard(QInstance.instance.completionFlag,
                keys.getString(Tag.CompletionFlag, "*").toUpperCase(), matchUnknown, false));
        builder.and(MatchDateTimeRange.rangeMatch(
                QInstance.instance.contentDate, QInstance.instance.contentTime,
                Tag.ContentDate, Tag.ContentTime, Tag.ContentDateAndTime,
                keys, combinedDatetimeMatching, matchUnknown));
        builder.and(code(QInstance.instance.conceptNameCode, keys.getNestedDataset(Tag.ConceptNameCodeSequence),
                matchUnknown));
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
                        attrFilter.getCustomAttribute1(), "*"), matchUnknown,
                true));
        builder.and(wildCard(
                QInstance.instance.instanceCustomAttribute2,
                AttributeFilter.selectStringValue(keys,
                        attrFilter.getCustomAttribute2(), "*"), matchUnknown,
                true));
        builder.and(wildCard(
                QInstance.instance.instanceCustomAttribute3,
                AttributeFilter.selectStringValue(keys,
                        attrFilter.getCustomAttribute3(), "*"), matchUnknown,
                true));
        builder.and(hideRejectedInstance(queryParam));
        builder.and(hideRejectionNote(queryParam));
    }

    public static Predicate hideRejectedInstance(QueryParam queryParam) {
        CodeEntity[] codes = queryParam.getShowInstancesRejectedByCode();
        if (codes.length == 0)
            return queryParam.isHideNotRejectedInstances()
                    ? QInstance.instance.rejectionNoteCode.isNotNull()
                    : QInstance.instance.rejectionNoteCode.isNull();

        BooleanExpression showRejected = QInstance.instance.rejectionNoteCode.in(codes);
        return queryParam.isHideNotRejectedInstances()
                ? showRejected
                : QInstance.instance.rejectionNoteCode.isNull().or(showRejected);
    }

    public static Predicate hideRejectionNote(QueryParam queryParam) {
        CodeEntity[] codes = queryParam.getHideRejectionNotesWithCode();
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

    static Predicate wildCard(StringPath path, String value) {
        return wildCard(path, value, false, false);
    }
    
    static Predicate wildCard(StringPath path, String value, boolean matchUnknown, boolean ignoreCase) {
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

        return matchUnknown(predicate, path, matchUnknown);
    }

    static boolean containsWildcard(String s) {
        return s.indexOf('*') >= 0 || s.indexOf('?') >= 0;
    }

    static Predicate matchUnknown(Predicate predicate, StringPath path, boolean matchUnknown) {
        return matchUnknown ? ExpressionUtils.or(predicate, path.eq("*")) : predicate;
    }

    static <T> Predicate matchUnknown(Predicate predicate, BeanPath<T> path, boolean matchUnknown) {
        return matchUnknown ? ExpressionUtils.or(predicate, path.isNull()) : predicate;
    }

    static <E, Q extends SimpleExpression<? super E>> Predicate matchUnknown(
            Predicate predicate, CollectionPath<E, Q> path, boolean matchUnknown) {
        return matchUnknown ? ExpressionUtils.or(predicate, path.isEmpty()) : predicate;
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

    static Predicate modalitiesInStudy(String modality, boolean matchUnknown) {
        if (modality.equals("*"))
            return null;

        return JPAExpressions.selectFrom(QSeries.series)
                .where(QSeries.series.study.eq(QStudy.study),
                        wildCard(QSeries.series.modality, modality,
                                matchUnknown, false)).exists();
    }

    static Predicate code(Attributes item) {
        if (item == null || item.isEmpty())
            return null;

        return ExpressionUtils.allOf(
                wildCard(QCodeEntity.codeEntity.code.codeValue,
                        item.getString(Tag.CodeValue, "*")),
                wildCard(QCodeEntity.codeEntity.code.codingSchemeDesignator,
                        item.getString(Tag.CodingSchemeDesignator, "*")),
                wildCard(QCodeEntity.codeEntity.code.codingSchemeVersion,
                        item.getString(Tag.CodingSchemeVersion, "*")));
    }

    static Predicate code(QCodeEntity code, Attributes item, boolean matchUnknown) {
        Predicate predicate = code(item);
        if (predicate == null)
            return null;

        return matchUnknown(JPAExpressions.selectFrom(QCodeEntity.codeEntity)
                        .where(QCodeEntity.codeEntity.eq(code), predicate).exists(),
                code, matchUnknown);
    }


    static Predicate code(CollectionPath<CodeEntity, QCodeEntity> codes, Attributes item, boolean matchUnknown) {
        Predicate predicate = code(item);
        if (predicate == null)
            return null;

        return matchUnknown(JPAExpressions.selectFrom(QCodeEntity.codeEntity)
                        .where(codes.contains(QCodeEntity.codeEntity), predicate).exists(),
                codes, matchUnknown);
    }

    public static void andNotInCodes(BooleanBuilder builder, QCode code,
                                     List<Code> codes) {
        if (codes != null && !codes.isEmpty())
            builder.and(ExpressionUtils.or(code.isNull(), code.notIn(codes)));
    }

    static Predicate requestAttributes(Attributes item, QueryParam queryParam) {
        if (item == null || item.isEmpty())
            return null;

        boolean matchUnknown = queryParam.isMatchUnknown();
        BooleanBuilder builder = new BooleanBuilder();
        String accNo = item.getString(Tag.AccessionNumber, "*");
        Issuer issuerOfAccessionNumber = null;
        if (!accNo.equals("*")) {
            issuerOfAccessionNumber = Issuer.valueOf(item
                    .getNestedDataset(Tag.IssuerOfAccessionNumberSequence));
            if (issuerOfAccessionNumber == null)
                issuerOfAccessionNumber = queryParam.getDefaultIssuerOfAccessionNumber();
            builder.and(matchUnknown(
                    idWithIssuer(
                            QSeriesRequestAttributes.seriesRequestAttributes.accessionNumber,
                            QSeriesRequestAttributes.seriesRequestAttributes.issuerOfAccessionNumber,
                            accNo, issuerOfAccessionNumber),
                    QSeriesRequestAttributes.seriesRequestAttributes.accessionNumber,
                    matchUnknown));
        }
        builder.and(wildCard(
                QSeriesRequestAttributes.seriesRequestAttributes.requestingService,
                item.getString(Tag.RequestingService, "*"), matchUnknown, true));
        Predicate matchRequestingPhysician = MatchPersonName.match(
                QueryBuilder.requestingPhysician,
                item.getString(Tag.RequestingPhysician, "*"), queryParam);
        builder.and(matchRequestingPhysician);
        builder.and(wildCard(
                QSeriesRequestAttributes.seriesRequestAttributes.requestedProcedureID,
                item.getString(Tag.RequestedProcedureID, "*"), matchUnknown,
                false));
        builder.and(uidsPredicate(QSeriesRequestAttributes.seriesRequestAttributes.studyInstanceUID,
                item.getStrings(Tag.StudyInstanceUID), matchUnknown));
        builder.and(wildCard(
                QSeriesRequestAttributes.seriesRequestAttributes.scheduledProcedureStepID,
                item.getString(Tag.ScheduledProcedureStepID, "*"),
                matchUnknown, false));

        if (!builder.hasValue())
            return null;

        JPQLQuery<SeriesRequestAttributes> subQuery = JPAExpressions.selectFrom(QSeriesRequestAttributes.seriesRequestAttributes);

        if (issuerOfAccessionNumber != null)
            subQuery.leftJoin(
                    QSeriesRequestAttributes.seriesRequestAttributes.issuerOfAccessionNumber,
                    QIssuerEntity.issuerEntity);

        if (matchRequestingPhysician != null)
            subQuery = matchUnknown
                    ? subQuery.leftJoin(QueryBuilder.requestingPhysician, QueryBuilder.requestingPhysician)
                    : subQuery.join(QueryBuilder.requestingPhysician, QueryBuilder.requestingPhysician);

        return matchUnknown(
                subQuery
                        .where(QSeriesRequestAttributes.seriesRequestAttributes.in(QSeries.series.requestAttributes),
                                builder).exists(),
                QSeries.series.requestAttributes, matchUnknown);
    }

    static Predicate verifyingObserver(Attributes item, QueryParam queryParam) {
        if (item == null || item.isEmpty())
            return null;

        boolean matchUnknown = queryParam.isMatchUnknown();
        Predicate matchVerifyingObserverName = MatchPersonName.match(
                QueryBuilder.verifyingObserverName,
                item.getString(Tag.VerifyingObserverName, "*"),
                queryParam);
        Predicate predicate = ExpressionUtils
                .allOf(MatchDateTimeRange.rangeMatch(
                                QVerifyingObserver.verifyingObserver.verificationDateTime,
                                item, Tag.VerificationDateTime,
                                MatchDateTimeRange.FormatDate.DT, matchUnknown),
                        matchVerifyingObserverName);

        if (predicate == null)
            return null;

        JPQLQuery<VerifyingObserver> query = JPAExpressions.selectFrom(QVerifyingObserver.verifyingObserver);

        if (matchVerifyingObserverName != null)
            query = matchUnknown
                    ? query.leftJoin(
                    QVerifyingObserver.verifyingObserver.verifyingObserverName,
                    QueryBuilder.verifyingObserverName)
                    : query.join(QVerifyingObserver.verifyingObserver.verifyingObserverName,
                    QueryBuilder.verifyingObserverName);

        return matchUnknown(
                query.where(QVerifyingObserver.verifyingObserver.in(QInstance.instance.verifyingObservers),
                        predicate).exists(),
                QInstance.instance.verifyingObservers, matchUnknown);
    }

    static Predicate contentItem(Attributes item) {
        String valueType = item.getString(Tag.ValueType);
        if (!("CODE".equals(valueType) || "TEXT".equals(valueType)))
            return null;

        Predicate predicate = ExpressionUtils.allOf(
                code(QContentItem.contentItem.conceptName,
                        item.getNestedDataset(Tag.ConceptNameCodeSequence),
                        false),
                wildCard(QContentItem.contentItem.relationshipType, item
                        .getString(Tag.RelationshipType, "*").toUpperCase()),
                code(QContentItem.contentItem.conceptCode,
                        item.getNestedDataset(Tag.ConceptCodeSequence), false),
                wildCard(QContentItem.contentItem.textValue,
                        item.getString(Tag.TextValue, "*"), false, true));
        if (predicate == null)
            return null;

        return JPAExpressions.selectFrom(QContentItem.contentItem)
                .where(QInstance.instance.contentItems
                                .contains(QContentItem.contentItem),
                        predicate).exists();
    }
}
