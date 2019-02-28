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
import org.dcm4che3.dict.archive.ArchiveTag;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4che3.soundex.FuzzyStr;
import org.dcm4che3.util.DateUtils;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.AttributeFilter;
import org.dcm4chee.arc.conf.Entity;
import org.dcm4chee.arc.conf.QueryRetrieveView;
import org.dcm4chee.arc.conf.SPSStatus;
import org.dcm4chee.arc.entity.*;

import javax.persistence.criteria.*;
import java.util.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Aug 2015
 */
public class QueryBuilder2 {

    private final CriteriaBuilder cb;

    public QueryBuilder2(CriteriaBuilder cb) {
        this.cb = Objects.requireNonNull(cb);
    }

    public List<Order> orderPatients(Path<Patient> patient, List<OrderByTag> orderByTags) {
        List<Order> result = new ArrayList<>(orderByTags.size());
        for (OrderByTag orderByTag : orderByTags)
            orderPatients(patient, orderByTag, result);
        return result;
    }

    public List<Order> orderStudies(Path<Patient> patient, Path<Study> study, List<OrderByTag> orderByTags) {
        List<Order> result = new ArrayList<>(orderByTags.size());
        for (OrderByTag orderByTag : orderByTags)
            orderStudies(patient, study, orderByTag, result);
        return result;
    }

    public List<Order> orderSeries(Path<Patient> patient, Path<Study> study, Path<Series> series,
            List<OrderByTag> orderByTags) {
        List<Order> result = new ArrayList<>(orderByTags.size());
        for (OrderByTag orderByTag : orderByTags)
            orderSeries(patient, study, series, orderByTag, result);
        return result;
    }

    public List<Order> orderInstances(Path<Patient> patient, Path<Study> study, Path<Series> series,
            Path<Instance> instance, List<OrderByTag> orderByTags) {
        List<Order> result = new ArrayList<>(orderByTags.size());
        for (OrderByTag orderByTag : orderByTags)
            orderInstances(patient, study, series, instance, orderByTag, result);
        return result;
    }

    public List<Order> orderMWLItems(Path<Patient> patient, Path<MWLItem> mwlItem, List<OrderByTag> orderByTags) {
        List<Order> result = new ArrayList<>(orderByTags.size());
        for (OrderByTag orderByTag : orderByTags)
            orderMWLItems(patient, mwlItem, orderByTag, result);
        return result;
    }

    private boolean orderPatients(Path<Patient> patient, OrderByTag orderByTag, List<Order> result) {
        switch (orderByTag.tag) {
            case Tag.PatientName:
                return orderPersonName(patient.get(Patient_.patientName), orderByTag, result);
            case Tag.PatientSex:
                return result.add(orderByTag.order(cb, patient.get(Patient_.patientSex)));
            case Tag.PatientBirthDate:
                return result.add(orderByTag.order(cb, patient.get(Patient_.patientBirthDate)));
        }
        return false;
    }

    private boolean orderStudies(Path<Patient> patient, Path<Study> study,
            OrderByTag orderByTag, List<Order> result) {
        if (patient != null && orderPatients(patient, orderByTag, result))
            return true;

        switch (orderByTag.tag) {
            case Tag.StudyInstanceUID:
                return result.add(orderByTag.order(cb, study.get(Study_.studyInstanceUID)));
            case Tag.StudyID:
                return result.add(orderByTag.order(cb, study.get(Study_.studyID)));
            case Tag.StudyDate:
                return result.add(orderByTag.order(cb, study.get(Study_.studyDate)));
            case Tag.StudyTime:
                return result.add(orderByTag.order(cb, study.get(Study_.studyTime)));
            case Tag.ReferringPhysicianName:
                return orderPersonName(study.get(Study_.referringPhysicianName), orderByTag, result);
            case Tag.StudyDescription:
                return result.add(orderByTag.order(cb, study.get(Study_.studyDescription)));
            case Tag.AccessionNumber:
                return result.add(orderByTag.order(cb, study.get(Study_.accessionNumber)));
        }
        return false;
    }

    private boolean orderSeries(Path<Patient> patient, Path<Study> study, Path<Series> series,
            OrderByTag orderByTag, List<Order> result) {
        if (study != null && orderStudies(patient, study, orderByTag, result))
            return true;

        switch (orderByTag.tag) {
            case Tag.SeriesInstanceUID:
                return result.add(orderByTag.order(cb, series.get(Series_.seriesInstanceUID)));
            case Tag.SeriesNumber:
                return result.add(orderByTag.order(cb, series.get(Series_.seriesNumber)));
            case Tag.Modality:
                return result.add(orderByTag.order(cb, series.get(Series_.modality)));
            case Tag.BodyPartExamined:
                return result.add(orderByTag.order(cb, series.get(Series_.bodyPartExamined)));
            case Tag.Laterality:
                return result.add(orderByTag.order(cb, series.get(Series_.laterality)));
            case Tag.PerformedProcedureStepStartDate:
                return result.add(orderByTag.order(cb, series.get(Series_.performedProcedureStepStartDate)));
            case Tag.PerformedProcedureStepStartTime:
                return result.add(orderByTag.order(cb, series.get(Series_.performedProcedureStepStartTime)));
            case Tag.PerformingPhysicianName:
                return orderPersonName(series.get(Series_.performingPhysicianName), orderByTag, result);
            case Tag.SeriesDescription:
                return result.add(orderByTag.order(cb, series.get(Series_.seriesDescription)));
            case Tag.StationName:
                return result.add(orderByTag.order(cb, series.get(Series_.stationName)));
            case Tag.InstitutionName:
                return result.add(orderByTag.order(cb, series.get(Series_.institutionName)));
            case Tag.InstitutionalDepartmentName:
                return result.add(orderByTag.order(cb, series.get(Series_.institutionalDepartmentName)));
        }
        return false;
    }

    private boolean orderInstances(Path<Patient> patient, Path<Study> study, Path<Series> series,
            Path<Instance> instance, OrderByTag orderByTag, List<Order> result) {
        if (series != null && orderSeries(patient, study, series, orderByTag, result))
            return true;

        switch (orderByTag.tag) {
            case Tag.SOPInstanceUID:
                return result.add(orderByTag.order(cb, instance.get(Instance_.sopInstanceUID)));
            case Tag.SOPClassUID:
                return result.add(orderByTag.order(cb, instance.get(Instance_.sopClassUID)));
            case Tag.InstanceNumber:
                return result.add(orderByTag.order(cb, instance.get(Instance_.instanceNumber)));
            case Tag.VerificationFlag:
                return result.add(orderByTag.order(cb, instance.get(Instance_.verificationFlag)));
            case Tag.CompletionFlag:
                return result.add(orderByTag.order(cb, instance.get(Instance_.completionFlag)));
            case Tag.ContentDate:
                return result.add(orderByTag.order(cb, instance.get(Instance_.contentDate)));
            case Tag.ContentTime:
                return result.add(orderByTag.order(cb, instance.get(Instance_.contentTime)));
        }
        return false;
    }

    private boolean orderMWLItems(Path<Patient> patient, Path<MWLItem> mwlItem,
            OrderByTag orderByTag, List<Order> result) {
        if (patient != null && orderPatients(patient, orderByTag, result))
            return true;

        switch (orderByTag.tag) {
            case Tag.AccessionNumber:
                return result.add(orderByTag.order(cb, mwlItem.get(MWLItem_.accessionNumber)));
            case Tag.Modality:
                return result.add(orderByTag.order(cb, mwlItem.get(MWLItem_.modality)));
            case Tag.StudyInstanceUID:
                return result.add(orderByTag.order(cb, mwlItem.get(MWLItem_.studyInstanceUID)));
            case Tag.ScheduledProcedureStepStartDate:
                return result.add(orderByTag.order(cb, mwlItem.get(MWLItem_.scheduledStartDate)));
            case Tag.ScheduledProcedureStepStartTime:
                return result.add(orderByTag.order(cb, mwlItem.get(MWLItem_.scheduledStartTime)));
            case Tag.ScheduledPerformingPhysicianName:
                return orderPersonName(mwlItem.get(MWLItem_.scheduledPerformingPhysicianName), orderByTag, result);
            case Tag.ScheduledProcedureStepID:
                return result.add(orderByTag.order(cb, mwlItem.get(MWLItem_.scheduledProcedureStepID)));
            case Tag.RequestedProcedureID:
                return result.add(orderByTag.order(cb, mwlItem.get(MWLItem_.requestedProcedureID)));
        }
        return false;
    }

    private boolean orderPersonName(Path<org.dcm4chee.arc.entity.PersonName> personName,
            OrderByTag orderByTag, List<Order> result) {
        result.add(orderByTag.order(cb, personName.get(PersonName_.familyName)));
        result.add(orderByTag.order(cb, personName.get(PersonName_.givenName)));
        result.add(orderByTag.order(cb, personName.get(PersonName_.middleName)));
        return true;
    }

    public static <X> void applyPatientLevelJoins(From<X, Patient> patient, IDWithIssuer[] pids, Attributes keys,
            boolean orderByPatientName) {
        applyPatientIDJoins(patient, pids);
        if (!isUniversalMatching(keys.getString(Tag.PatientName)))
            patient.join(Patient_.patientName);
        else if (orderByPatientName)
            patient.join(Patient_.patientName, JoinType.LEFT);
        if (!isUniversalMatching(keys.getString(Tag.ResponsiblePerson)))
            patient.join(Patient_.responsiblePerson);
    }

    public static <X> void applyPatientLevelJoinsForCount(From<X, Patient> patient, IDWithIssuer[] pids,
            Attributes keys) {
        applyPatientIDJoins(patient, pids);
        if (!isUniversalMatching(keys.getString(Tag.PatientName)))
            patient.join(Patient_.patientName);
        if (!isUniversalMatching(keys.getString(Tag.ResponsiblePerson)))
            patient.join(Patient_.responsiblePerson);
    }

    public static <X> void applyPatientIDJoins(From<X, Patient> patient, IDWithIssuer[] pids) {
        if (pids.length > 0) {
            Join<Patient, PatientID> patientID = patient.join(Patient_.patientID);
            if (containsIssuer(pids))
                patientID.join(PatientID_.issuer);
        }
    }

    private Expression<Boolean> and(Expression<Boolean> x, Expression<Boolean> y) {
        return y == null ? x : x == null ? y : cb.and(x, y);
    }

    private Expression<Boolean> or(Expression<Boolean> x, Expression<Boolean> y) {
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

    public <T> Expression<Boolean> patientPredicates(CriteriaQuery<T> q,
            Expression<Boolean> x, Path<Patient> patient, IDWithIssuer[] pids, Attributes keys, QueryParam queryParam) {
        return patientLevelPredicates(q, x, patient, pids, keys, queryParam, QueryRetrieveLevel2.PATIENT);
    }

    public <T> Expression<Boolean> studyPredicates(CriteriaQuery<T> q, Expression<Boolean> x,
            Path<Patient> patient, Path<Study> study,
            IDWithIssuer[] pids, Attributes keys, QueryParam queryParam) {
        return studyLevelPredicates(q,
                patientLevelPredicates(q, x, patient, pids, keys, queryParam, QueryRetrieveLevel2.STUDY),
                study, keys, queryParam, QueryRetrieveLevel2.STUDY);
    }

    public <T> Expression<Boolean> seriesPredicates(CriteriaQuery<T> q, Expression<Boolean> x,
            Path<Patient> patient, Path<Study> study, Path<Series> series,
            IDWithIssuer[] pids, Attributes keys, QueryParam queryParam) {
        return seriesLevelPredicates(q,
                studyLevelPredicates(q,
                    patientLevelPredicates(q, x, patient, pids, keys, queryParam, QueryRetrieveLevel2.SERIES),
                    study, keys, queryParam, QueryRetrieveLevel2.SERIES),
                series, keys, queryParam);
    }

    public <T> Expression<Boolean> instancePredicates(CriteriaQuery<T> q, Expression<Boolean> x,
            Path<Patient> patient, Path<Study> study, Path<Series> series, Path<Instance> instance,
            IDWithIssuer[] pids, Attributes keys, QueryParam queryParam,
            CodeEntity[] showInstancesRejectedByCodes, CodeEntity[] hideRejectionNoteWithCodes) {
        return instanceLevelPredicates(q,
                seriesLevelPredicates(q,
                    studyLevelPredicates(q,
                        patientLevelPredicates(q, x, patient, pids, keys, queryParam, QueryRetrieveLevel2.IMAGE),
                        study, keys, queryParam, QueryRetrieveLevel2.IMAGE),
                    series, keys, queryParam),
                instance, keys, queryParam, showInstancesRejectedByCodes, hideRejectionNoteWithCodes);
    }

    public <T> Expression<Boolean> mwlItemPredicates(CriteriaQuery<T> q, Expression<Boolean> x,
            Path<Patient> patient, Path<MWLItem> mwlItem,
            IDWithIssuer[] pids, Attributes keys, QueryParam queryParam) {
        return mwlItemLevelPredicates(q,
                patientLevelPredicates(q, x, patient, pids, keys, queryParam, QueryRetrieveLevel2.STUDY),
                mwlItem, keys, queryParam);
    }

    private <T> Expression<Boolean> patientLevelPredicates(CriteriaQuery<T> q, Expression<Boolean> x,
            Path<Patient> patient, IDWithIssuer[] pids, Attributes keys, QueryParam queryParam,
            QueryRetrieveLevel2 queryRetrieveLevel) {
        if (patient == null)
            return x;
        if (queryRetrieveLevel == QueryRetrieveLevel2.PATIENT) {
            x = and(x, patient.get(Patient_.mergedWith).isNull());
            if (!queryParam.isWithoutStudies())
                x = cb.and(x, cb.greaterThan(patient.get(Patient_.numberOfStudies), 0));
        }
        x = patientIDPredicate(x, patient.get(Patient_.patientID), pids);
        x = personName(q, x, patient.get(Patient_.patientName),
                keys.getString(Tag.PatientName, "*"), queryParam);
        x = wildCard(x, patient.get(Patient_.patientSex),
                keys.getString(Tag.PatientSex, "*").toUpperCase());
        x = dateRange(x, patient.get(Patient_.patientBirthDate),
                keys.getDateRange(Tag.PatientBirthDate), FormatDate.DA);
        x = personName(q, x, patient.get(Patient_.responsiblePerson),
                keys.getString(Tag.ResponsiblePerson, "*"), queryParam);
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

    public static boolean hasPatientLevelPredicates(IDWithIssuer[] pids, Attributes keys, QueryParam queryParam) {
        for (IDWithIssuer pid : pids) {
            if (!isUniversalMatching(pid.getID()))
                return true;
        }
        AttributeFilter attrFilter = queryParam.getAttributeFilter(Entity.Patient);
        return queryParam.getPatientVerificationStatus() != null
                || !isUniversalMatching(keys.getString(Tag.PatientName))
                || !isUniversalMatching(keys.getString(Tag.PatientSex))
                || !isUniversalMatching(keys.getString(Tag.PatientSex))
                || !isUniversalMatching(keys.getString(Tag.PatientBirthDate))
                || !isUniversalMatching(keys.getString(Tag.ResponsiblePerson))
                || !isUniversalMatching(AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute1(), null))
                || !isUniversalMatching(AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute2(), null))
                || !isUniversalMatching(AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute3(), null));
    }

    public static <X> void applyStudyLevelJoins(From<X, Study> study, Attributes keys) {
        if (!isUniversalMatching(keys.getString(Tag.AccessionNumber))
                && !isUniversalMatching(keys.getNestedDataset(Tag.IssuerOfAccessionNumberSequence)))
            study.join(Study_.issuerOfAccessionNumber);
        if (!isUniversalMatching(keys.getString(Tag.ReferringPhysicianName)))
            study.join(Study_.referringPhysicianName);
    }

    public static <X> CollectionJoin<Study, StudyQueryAttributes> joinStudyQueryAttributes(
            CriteriaBuilder cb, From<X, Study> study, String viewID) {
        CollectionJoin<Study, StudyQueryAttributes> join = study.join(Study_.queryAttributes, JoinType.LEFT);
        return join.on(cb.equal(join.get(StudyQueryAttributes_.viewID), viewID));
    }

    public static <X> CollectionJoin<Series, SeriesQueryAttributes> joinSeriesQueryAttributes(
            CriteriaBuilder cb, From<X, Series> series, String viewID) {
        CollectionJoin<Series, SeriesQueryAttributes> join = series.join(Series_.queryAttributes, JoinType.LEFT);
        return join.on(cb.equal(join.get(SeriesQueryAttributes_.viewID), viewID));
    }

    public static Expression<Boolean> uidsPredicate(Expression<Boolean> x, Path<String> path, String[] values) {
        return isUniversalMatching(values) ? x : path.in(values);
    }

    private <T> Expression<Boolean> studyLevelPredicates(CriteriaQuery<T> q, Expression<Boolean> x,
            Path<Study> study, Attributes keys, QueryParam queryParam, QueryRetrieveLevel2 queryRetrieveLevel) {
        boolean combinedDatetimeMatching = queryParam.isCombinedDatetimeMatching();
        x = accessControl(x, study, queryParam.getAccessControlIDs());
        x = uidsPredicate(x, study.get(Study_.studyInstanceUID), keys.getStrings(Tag.StudyInstanceUID));
        x = wildCard(x, study.get(Study_.studyID), keys.getString(Tag.StudyID, "*"));
        x = dateRange(x, study.get(Study_.studyDate), study.get(Study_.studyTime),
                Tag.StudyDate, Tag.StudyTime, Tag.StudyDateAndTime,
                keys, combinedDatetimeMatching);
        x = personName(q, x, study.get(Study_.referringPhysicianName),
                keys.getString(Tag.ReferringPhysicianName, "*"), queryParam);
        x = wildCard(x, study.get(Study_.studyDescription),
                keys.getString(Tag.StudyDescription, "*"), true);
        String accNo = keys.getString(Tag.AccessionNumber, "*");
        if (!accNo.equals("*")) {
            Issuer issuer = Issuer.valueOf(keys.getNestedDataset(Tag.IssuerOfAccessionNumberSequence));
            if (issuer == null)
                issuer = queryParam.getDefaultIssuerOfAccessionNumber();
            x = idWithIssuer(x, study.get(Study_.accessionNumber), study.get(Study_.issuerOfAccessionNumber),
                    accNo, issuer);
        }
        x = seriesAttributesInStudy(q, x, study, keys, queryParam);
        Attributes procedureCode = keys.getNestedDataset(Tag.ProcedureCodeSequence);
        if (!isUniversalMatching(procedureCode))
            x = codes(q, x, study.get(Study_.procedureCodes), procedureCode);
        if (queryParam.isHideNotRejectedInstances())
            x = and(x, cb.notEqual(study.get(Study_.rejectionState), RejectionState.NONE));
        AttributeFilter attrFilter = queryParam.getAttributeFilter(Entity.Study);
        x = wildCard(x, study.get(Study_.studyCustomAttribute1),
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute1(), "*"), true);
        x = wildCard(x, study.get(Study_.studyCustomAttribute2),
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute2(), "*"), true);
        x = wildCard(x, study.get(Study_.studyCustomAttribute3),
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute3(), "*"), true);
        x = dateRange(x, study.get(Study_.createdTime),
                keys.getDateRange(ArchiveTag.PrivateCreator, ArchiveTag.StudyReceiveDateTime, VR.DT));
        if (queryParam.getExternalRetrieveAET() != null)
            x = and(x, cb.equal(study.get(Study_.externalRetrieveAET), queryParam.getExternalRetrieveAET()));
        if (queryParam.getExternalRetrieveAETNot() != null)
            x = and(x, cb.notEqual(study.get(Study_.externalRetrieveAET), queryParam.getExternalRetrieveAETNot()));
        if (queryRetrieveLevel == QueryRetrieveLevel2.STUDY) {
            if (queryParam.isIncomplete())
                x = and(x, cb.notEqual(study.get(Study_.completeness), Completeness.COMPLETE));
            if (queryParam.isRetrieveFailed())
                x = and(x, cb.greaterThan(study.get(Study_.failedRetrieves), 0));
        }
        if (queryParam.getExpirationDate() != null)
            x = dateRange(x, study.get(Study_.expirationDate), queryParam.getExpirationDate(), FormatDate.DA);
        if (queryParam.getStudyStorageIDs() != null)
            x = and(x, study.get(Study_.storageIDs).in(queryParam.getStudyStorageIDs()));
        if (queryParam.getMinStudySize() != 0)
            x = and(x, cb.greaterThanOrEqualTo(study.get(Study_.size), queryParam.getMinStudySize()));
        if (queryParam.getMaxStudySize() != 0)
            x = and(x, cb.lessThanOrEqualTo(study.get(Study_.size), queryParam.getMaxStudySize()));
        if (queryParam.getExpirationState() != null)
            x = and(x, study.get(Study_.expirationState).in(queryParam.getExpirationState()));
        return x;
    }

    public Expression<Boolean> accessControl(Expression<Boolean> x, Path<Study> study, String[] accessControlIDs) {
        if (accessControlIDs.length == 0)
            return x;

        String[] a = new String[accessControlIDs.length + 1];
        a[0] = "*";
        System.arraycopy(accessControlIDs, 0, a, 1, accessControlIDs.length);
        return study.get(Study_.accessControlID).in(a);
    }

    public static <X> void applySeriesLevelJoins(From<X, Series> series, Attributes keys) {
        if (!isUniversalMatching(keys.getString(Tag.PerformingPhysicianName))) {
            series.join(Series_.performingPhysicianName);
        }
    }

    private <T> Expression<Boolean> seriesLevelPredicates(CriteriaQuery<T> q, Expression<Boolean> x,
            Path<Series> series, Attributes keys, QueryParam queryParam) {
        x = uidsPredicate(x, series.get(Series_.seriesInstanceUID), keys.getStrings(Tag.SeriesInstanceUID));
        x = numberPredicate(x, series.get(Series_.seriesNumber), keys.getString(Tag.SeriesNumber, "*"));
        x = wildCard(x, series.get(Series_.modality),
                keys.getString(Tag.Modality, "*").toUpperCase());
        x = wildCard(x, series.get(Series_.bodyPartExamined),
                keys.getString(Tag.BodyPartExamined, "*").toUpperCase());
        x = wildCard(x, series.get(Series_.laterality),
                keys.getString(Tag.Laterality, "*").toUpperCase());
        x = dateRange(x,
                series.get(Series_.performedProcedureStepStartDate),
                series.get(Series_.performedProcedureStepStartTime),
                Tag.PerformedProcedureStepStartDate, Tag.PerformedProcedureStepStartTime,
                Tag.PerformedProcedureStepStartDateAndTime,
                keys, queryParam.isCombinedDatetimeMatching());
        x = personName(q, x, series.get(Series_.performingPhysicianName),
                keys.getString(Tag.PerformingPhysicianName, "*"), queryParam);
        x = wildCard(x, series.get(Series_.seriesDescription),
                keys.getString(Tag.SeriesDescription, "*"), true);
        x = wildCard(x, series.get(Series_.stationName), keys.getString(Tag.StationName, "*"), true);
        x = wildCard(x, series.get(Series_.institutionalDepartmentName),
                keys.getString(Tag.InstitutionalDepartmentName, "*"), true);
        x = wildCard(x, series.get(Series_.institutionName),
                keys.getString(Tag.InstitutionName, "*"), true);
        Attributes reqAttrs = keys.getNestedDataset(Tag.RequestAttributesSequence);
        if (!isUniversalMatching(reqAttrs))
            x = requestAttributes(q, x, series.get(Series_.requestAttributes), reqAttrs, queryParam);
        x = code(x, series.get(Series_.institutionCode), keys.getNestedDataset(Tag.InstitutionCodeSequence));
        if (queryParam.isHideNotRejectedInstances())
            x = and(x, cb.notEqual(series.get(Series_.rejectionState), RejectionState.NONE));
        AttributeFilter attrFilter = queryParam.getAttributeFilter(Entity.Series);
        x = wildCard(x, series.get(Series_.seriesCustomAttribute1),
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute1(), "*"), true);
        x = wildCard(x, series.get(Series_.seriesCustomAttribute2),
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute2(), "*"), true);
        x = wildCard(x, series.get(Series_.seriesCustomAttribute3),
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute3(), "*"), true);
        if (queryParam.isIncomplete())
            x = and(x, cb.notEqual(series.get(Series_.completeness), Completeness.COMPLETE));
        if (queryParam.isRetrieveFailed())
            x = and(x, cb.greaterThan(series.get(Series_.failedRetrieves), 0));
        if (queryParam.isStorageVerificationFailed())
            x = and(x, cb.greaterThan(series.get(Series_.failuresOfLastStorageVerification), 0));
        if (queryParam.isCompressionFailed())
            x = and(x, cb.greaterThan(series.get(Series_.compressionFailures), 0));
        x = wildCard(x, series.get(Series_.sourceAET),
                keys.getString(ArchiveTag.PrivateCreator, ArchiveTag.SendingApplicationEntityTitleOfSeries,
                        VR.AE, "*"),
                false);
        if (queryParam.getExpirationDate() != null)
            x = dateRange(x, series.get(Series_.expirationDate), queryParam.getExpirationDate(), FormatDate.DA);
        return x;
    }

    private <T> Expression<Boolean> instanceLevelPredicates(CriteriaQuery<T> q, Expression<Boolean> x,
            Path<Instance> instance, Attributes keys, QueryParam queryParam,
            CodeEntity[] showInstancesRejectedByCodes, CodeEntity[] hideRejectionNoteWithCodes) {
        boolean combinedDatetimeMatching = queryParam.isCombinedDatetimeMatching();
        x = uidsPredicate(x, instance.get(Instance_.sopInstanceUID), keys.getStrings(Tag.SOPInstanceUID));
        x = uidsPredicate(x, instance.get(Instance_.sopClassUID), keys.getStrings(Tag.SOPClassUID));
        x = numberPredicate(x, instance.get(Instance_.instanceNumber), keys.getString(Tag.InstanceNumber, "*"));
        x = wildCard(x, instance.get(Instance_.verificationFlag),
                keys.getString(Tag.VerificationFlag, "*").toUpperCase());
        x = wildCard(x, instance.get(Instance_.completionFlag),
                keys.getString(Tag.CompletionFlag, "*").toUpperCase());
        x = dateRange(x,
                instance.get(Instance_.contentDate),
                instance.get(Instance_.contentTime),
                Tag.ContentDate, Tag.ContentTime, Tag.ContentDateAndTime,
                keys, combinedDatetimeMatching);
        x = code(x, instance.get(Instance_.conceptNameCode), keys.getNestedDataset(Tag.ConceptNameCodeSequence));
        Attributes verifyingObserver = keys.getNestedDataset(Tag.VerifyingObserverSequence);
        if (!isUniversalMatching(verifyingObserver))
            x = verifyingObserver(q, x, instance.get(Instance_.verifyingObservers), verifyingObserver, queryParam);
        Sequence contentSeq = keys.getSequence(Tag.ContentSequence);
        if (contentSeq != null)
            for (Attributes item : contentSeq)
                x = contentItem(q, x, instance.get(Instance_.contentItems), item);
        AttributeFilter attrFilter = queryParam
                .getAttributeFilter(Entity.Instance);
        x = wildCard(x,
                instance.get(Instance_.instanceCustomAttribute1),
                AttributeFilter.selectStringValue(keys,
                        attrFilter.getCustomAttribute1(), "*"),
                true);
        x = wildCard(x,
                instance.get(Instance_.instanceCustomAttribute2),
                AttributeFilter.selectStringValue(keys,
                        attrFilter.getCustomAttribute2(), "*"),
                true);
        x = wildCard(x,
                instance.get(Instance_.instanceCustomAttribute3),
                AttributeFilter.selectStringValue(keys,
                        attrFilter.getCustomAttribute3(), "*"),
                true);
        x = hideRejectedInstance(x, instance, showInstancesRejectedByCodes, queryParam.isHideNotRejectedInstances());
        x = hideRejectionNote(x, instance, hideRejectionNoteWithCodes);
        return x;
    }

    public <T> Expression<Boolean> sopInstanceRefs(CriteriaQuery<T> q, Expression<Boolean> x,
            Path<Study> study, Path<Series> series, Root<Instance> instance,
            String studyIUID, String seriesUID, String objectUID, QueryRetrieveView qrView,
            CodeEntity[] showInstancesRejectedByCodes, CodeEntity[] hideRejectionNoteWithCodes) {
        x = and(x, cb.equal(study.get(Study_.studyInstanceUID), studyIUID));
        if (!isUniversalMatching(seriesUID))
            cb.and(x, cb.equal(series.get(Series_.seriesInstanceUID), seriesUID));
        if (!isUniversalMatching(objectUID))
            cb.and(x, cb.equal(instance.get(Instance_.sopInstanceUID), objectUID));
        x = hideRejectedInstance(x, instance, showInstancesRejectedByCodes, qrView.isHideNotRejectedInstances());
        x = hideRejectionNote(x, instance, hideRejectionNoteWithCodes);
        return x;
    }

    public static <X> void applyMWLItemJoins(From<X, MWLItem> mwlItem, Attributes keys) {
        if (!isUniversalMatching(keys.getString(Tag.AccessionNumber))
                && !isUniversalMatching(keys.getNestedDataset(Tag.IssuerOfAccessionNumberSequence))) {
            mwlItem.join(MWLItem_.issuerOfAccessionNumber, JoinType.LEFT);
        }

        Attributes sps = keys.getNestedDataset(Tag.ScheduledProcedureStepSequence);
        if (sps != null && !isUniversalMatching(sps.getString(Tag.ScheduledPerformingPhysicianName))) {
            mwlItem.join(MWLItem_.scheduledPerformingPhysicianName);
        }
    }

    private <T> Expression<Boolean> mwlItemLevelPredicates(CriteriaQuery<T> q, Expression<Boolean> x, Path<MWLItem> mwlItem,
            Attributes keys, QueryParam queryParam) {
        x = uidsPredicate(x, mwlItem.get(MWLItem_.studyInstanceUID), keys.getStrings(Tag.StudyInstanceUID));
        x = wildCard(x, mwlItem.get(MWLItem_.requestedProcedureID), keys.getString(Tag.RequestedProcedureID, "*"));
        String accNo = keys.getString(Tag.AccessionNumber, "*");
        if (!accNo.equals("*")) {
            Issuer issuer = Issuer.valueOf(keys.getNestedDataset(Tag.IssuerOfAccessionNumberSequence));
            if (issuer == null) {
                issuer = queryParam.getDefaultIssuerOfAccessionNumber();
            }
            x = idWithIssuer(x,
                    mwlItem.get(MWLItem_.accessionNumber),
                    mwlItem.get(MWLItem_.issuerOfAccessionNumber),
                    accNo, issuer);
        }
        Attributes sps = keys.getNestedDataset(Tag.ScheduledProcedureStepSequence);
        if (sps != null) {
            x = wildCard(x, mwlItem.get(MWLItem_.scheduledProcedureStepID),
                    sps.getString(Tag.ScheduledProcedureStepID, "*"));
            x = dateRange(x,
                    mwlItem.get(MWLItem_.scheduledStartDate),
                    mwlItem.get(MWLItem_.scheduledStartTime),
                    Tag.ScheduledProcedureStepStartDate,
                    Tag.ScheduledProcedureStepStartDate,
                    Tag.ScheduledProcedureStepStartDateAndTime,
                    sps, true);
            x = personName(q, x, mwlItem.get(MWLItem_.scheduledPerformingPhysicianName),
                    sps.getString(Tag.ScheduledPerformingPhysicianName, "*"), queryParam);
            x = wildCard(x, mwlItem.get(MWLItem_.modality), sps.getString(Tag.Modality, "*").toUpperCase());
            x = showSPSWithStatus(x, mwlItem, sps);
            String spsAET = sps.getString(Tag.ScheduledStationAETitle, "*");
            if (!isUniversalMatching(spsAET))
                x = and(x, cb.isMember(spsAET, mwlItem.get(MWLItem_.scheduledStationAETs)));
        }
        x = hideSPSWithStatus(x, mwlItem, queryParam);
        return x;
    }

    private Expression<Boolean> showSPSWithStatus(Expression<Boolean> x, Path<MWLItem> mwlItem, Attributes sps) {
        String status = sps.getString(Tag.ScheduledProcedureStepStatus, "*").toUpperCase();
        switch(status) {
            case "SCHEDULED":
            case "ARRIVED":
            case "READY":
                return and(x, cb.equal(mwlItem.get(MWLItem_.status), SPSStatus.valueOf(status)));
            default:
                return x;
        }
    }

    private Expression<Boolean> hideSPSWithStatus(Expression<Boolean> x, Path<MWLItem> mwlItem, QueryParam queryParam) {
        SPSStatus[] hideSPSWithStatusFromMWL = queryParam.getHideSPSWithStatusFromMWL();
        return (hideSPSWithStatusFromMWL.length > 0)
                ? and(x, mwlItem.get(MWLItem_.status).in(hideSPSWithStatusFromMWL).not())
                : x;
    }

    public Expression<Boolean> hideRejectedInstance(Expression<Boolean> x, Path<Instance> instance, CodeEntity[] codes,
            boolean hideNotRejectedInstances) {
        return and(x, hideRejectedInstance(instance, codes, hideNotRejectedInstances));
    }

    private Expression<Boolean> hideRejectedInstance(Path<Instance> instance, CodeEntity[] codes,
            boolean hideNotRejectedInstances) {
        if (codes.length == 0)
            return hideNotRejectedInstances
                    ? instance.get(Instance_.rejectionNoteCode).isNotNull()
                    : instance.get(Instance_.rejectionNoteCode).isNull();

        Expression<Boolean> showRejected = instance.get(Instance_.rejectionNoteCode).in(codes);
        return hideNotRejectedInstances
                ? showRejected
                : cb.or(instance.get(Instance_.rejectionNoteCode).isNull(), showRejected);
    }

    public Expression<Boolean> hideRejectionNote(Expression<Boolean> x, Path<Instance> instance, CodeEntity[] codes) {
        return codes.length == 0 ? x
                : and(x, cb.or(
                        instance.get(Instance_.conceptNameCode).isNull(),
                        instance.get(Instance_.conceptNameCode).in(codes).not()));
    }

    private static boolean containsIssuer(IDWithIssuer[] pids) {
        for (IDWithIssuer pid : pids)
            if (pid.getIssuer() != null)
                return true;
        return false;
    }

    private Expression<Boolean> idWithIssuer(Expression<Boolean> x, Expression<String> idPath,
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

    private static boolean isUniversalMatching(Attributes item) {
        return item == null || item.isEmpty();
    }

    private static boolean isUniversalMatching(String value) {
        return value == null || value.equals("*");
    }

    private static boolean isUniversalMatching(String[] values) {
        return values == null || values.length == 0 || values[0].equals("*");
    }

    private static boolean isUniversalMatching(DateRange range) {
        return range == null || (range.getStartDate() == null && range.getEndDate() == null);
    }

    private Expression<Boolean> wildCard(Expression<Boolean> x, Expression<String> path, String value) {
        return wildCard(x, path, value, false);
    }

    private Expression<Boolean> wildCard(Expression<Boolean> x, Expression<String> path, String value,
            boolean ignoreCase) {
        if (isUniversalMatching(value))
            return x;

        if (ignoreCase && StringUtils.isUpperCase(value))
            path = cb.upper(path);

        if (!containsWildcard(value))
            return and(x, cb.equal(path, value));

        String pattern = toLikePattern(value);
        return pattern.equals("%") ? x : and(x, cb.like(path, pattern, '!'));
    }

    private Expression<Boolean> numberPredicate(Expression<Boolean> x, Expression<Integer> path, String value) {
        if (isUniversalMatching(value))
            return x;

        try {
            return and(x, cb.equal(path, Integer.parseInt(value)));
        } catch (NumberFormatException e) {
            return x;
        }
    }

    private static boolean containsWildcard(String s) {
        return s.indexOf('*') >= 0 || s.indexOf('?') >= 0;
    }

    private static String toLikePattern(String s) {
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

    private <T> Expression<Boolean> seriesAttributesInStudy(CriteriaQuery<T> q, Expression<Boolean> x,
            Path<Study> study, Attributes keys, QueryParam queryParam) {
        Subquery<Series> sq = q.subquery(Series.class);
        Root<Series> series = sq.from(Series.class);
        Expression<Boolean> y = null;
        y = wildCard(y, series.get(Series_.institutionName),
                keys.getString(Tag.InstitutionName), true);
        y = wildCard(y, series.get(Series_.institutionalDepartmentName),
                keys.getString(Tag.InstitutionalDepartmentName), true);
        y = wildCard(y, series.get(Series_.stationName),
                keys.getString(Tag.StationName), true);
        y = wildCard(y, series.get(Series_.seriesDescription),
                keys.getString(Tag.SeriesDescription), true);
        y = wildCard(y, series.get(Series_.modality),
                keys.getString(Tag.ModalitiesInStudy, "*").toUpperCase());
        y = wildCard(y, series.get(Series_.sopClassUID),
                keys.getString(Tag.SOPClassesInStudy, "*"));
        y = wildCard(y, series.get(Series_.bodyPartExamined),
                keys.getString(Tag.BodyPartExamined, "*").toUpperCase());
        y = wildCard(y, series.get(Series_.sourceAET),
                keys.getString(ArchiveTag.PrivateCreator, ArchiveTag.SendingApplicationEntityTitleOfSeries,
                        VR.AE, "*"));
        if (queryParam.isStorageVerificationFailed())
            y = and(y, cb.greaterThan(series.get(Series_.failuresOfLastStorageVerification), 0));
        if (queryParam.isCompressionFailed())
            y = and(y, cb.greaterThan(series.get(Series_.compressionFailures), 0));
        return y == null ? x : and(x, cb.exists(sq.where(cb.and(cb.equal(series.get(Series_.study), study), y))));
    }


    private Expression<Boolean> code(Expression<Boolean> x, Path<CodeEntity> code, Attributes item) {
        if (isUniversalMatching(item))
            return x;

        x = wildCard(x, code.get(CodeEntity_.codeValue),
                item.getString(Tag.CodeValue, "*"));
        x = wildCard(x, code.get(CodeEntity_.codingSchemeDesignator),
                item.getString(Tag.CodingSchemeDesignator, "*"));
        x = wildCard(x, code.get(CodeEntity_.codingSchemeVersion),
                item.getString(Tag.CodingSchemeVersion, "*"));
        return x;
    }

    private <T> Expression<Boolean> codes(CriteriaQuery<T> q, Expression<Boolean> x,
            Expression<Collection<CodeEntity>> codes, Attributes item) {
        Subquery<CodeEntity> sq = q.subquery(CodeEntity.class);
        Root<CodeEntity> code = sq.from(CodeEntity.class);
        Expression<Boolean> y = code(null, code, item);
        return y == null ? x : and(x, cb.exists(sq.where(cb.and(code.in(codes), y))));
    }

    private <T> Expression<Boolean> requestAttributes(CriteriaQuery<T> q, Expression<Boolean> x,
            Expression<Collection<SeriesRequestAttributes>> requests, Attributes item, QueryParam queryParam) {
        if (isUniversalMatching(item))
            return x;

        Subquery<SeriesRequestAttributes> sq = q.subquery(SeriesRequestAttributes.class);
        Root<SeriesRequestAttributes> request = sq.from(SeriesRequestAttributes.class);
        Expression<Boolean> y = null;
        String accNo = item.getString(Tag.AccessionNumber, "*");
        if (!isUniversalMatching(accNo)) {
            Issuer issuerOfAccessionNumber = Issuer.valueOf(item
                    .getNestedDataset(Tag.IssuerOfAccessionNumberSequence));
            if (issuerOfAccessionNumber == null)
                issuerOfAccessionNumber = queryParam.getDefaultIssuerOfAccessionNumber();
            if (issuerOfAccessionNumber != null)
                request.join(SeriesRequestAttributes_.issuerOfAccessionNumber, JoinType.LEFT);
            y = idWithIssuer(y,
                    request.get(SeriesRequestAttributes_.accessionNumber),
                    request.get(SeriesRequestAttributes_.issuerOfAccessionNumber),
                    accNo, issuerOfAccessionNumber);
        }
        y = wildCard(y, 
                request.get(SeriesRequestAttributes_.requestingService),
                item.getString(Tag.RequestingService, "*"), true);
        String requestingPhysician = item.getString(Tag.RequestingPhysician, "*");
        if (!isUniversalMatching(requestingPhysician)) {
            request.join(SeriesRequestAttributes_.requestingPhysician);
            y = personName(q, y,
                    request.get(SeriesRequestAttributes_.requestingPhysician),
                    requestingPhysician, queryParam);
        }
        y = wildCard(y,
                request.get(SeriesRequestAttributes_.requestedProcedureID),
                item.getString(Tag.RequestedProcedureID, "*"));
        y = uidsPredicate(y, request.get(SeriesRequestAttributes_.studyInstanceUID),
                item.getStrings(Tag.StudyInstanceUID));
        y = wildCard(y,
                request.get(SeriesRequestAttributes_.scheduledProcedureStepID),
                item.getString(Tag.ScheduledProcedureStepID, "*"),
                false);
        return y == null ? x : and(x, cb.exists(sq.where(cb.and(request.in(requests), y))));
    }

    private <T> Expression<Boolean> verifyingObserver(CriteriaQuery<T> q, Expression<Boolean> x,
            Expression<Collection<VerifyingObserver>> observers, Attributes item, QueryParam queryParam) {
        Subquery<VerifyingObserver> sq = q.subquery(VerifyingObserver.class);
        Root<VerifyingObserver> observer = sq.from(VerifyingObserver.class);
        Expression<Boolean> y = null;
        String observerName = item.getString(Tag.VerifyingObserverName, "*");
        if (!isUniversalMatching(observerName)) {
            observer.join(VerifyingObserver_.verifyingObserverName);
            y = personName(q, y, observer.get(VerifyingObserver_.verifyingObserverName), observerName, queryParam);
        }
        y = dateRange(y, observer.get(VerifyingObserver_.verificationDateTime),
                item.getDateRange(Tag.VerificationDateTime), FormatDate.DT);
        return y == null ? x : and(x, cb.exists(sq.where(cb.and(observer.in(observers), y))));
    }

    private <T> Expression<Boolean> contentItem(CriteriaQuery<T> q, Expression<Boolean> x,
            Expression<Collection<ContentItem>> contentItems, Attributes item) {
        String valueType = item.getString(Tag.ValueType);
        if (!("CODE".equals(valueType) || "TEXT".equals(valueType)))
            return x;

        Subquery<ContentItem> sq = q.subquery(ContentItem.class);
        Root<ContentItem> contentItem = sq.from(ContentItem.class);
        Expression<Boolean> y = null;

        y = code(y, contentItem.get(ContentItem_.conceptName),
                item.getNestedDataset(Tag.ConceptNameCodeSequence));
        y = wildCard(y, contentItem.get(ContentItem_.relationshipType),
                item.getString(Tag.RelationshipType, "*").toUpperCase());
        y = code(y, contentItem.get(ContentItem_.conceptCode),
                item.getNestedDataset(Tag.ConceptCodeSequence));
        y = wildCard(y, contentItem.get(ContentItem_.textValue),
                item.getString(Tag.TextValue, "*"), true);
        return y == null ? x : and(x, cb.exists(sq.where(cb.and(contentItem.in(contentItems), y))));
    }

    private <T> Expression<Boolean> personName(CriteriaQuery<T> q, Expression<Boolean> x,
            Path<org.dcm4chee.arc.entity.PersonName> qpn, String value, QueryParam queryParam) {
        if (value.equals("*"))
            return x;

        PersonName pn = new PersonName(value, true);
        return and(x, queryParam.isFuzzySemanticMatching()
                ? fuzzyMatch(q, qpn, pn, queryParam)
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
            PersonName pn, PersonName.Group group, boolean ignoreCase) {
        if (!pn.contains(group))
            return null;

        Expression<Boolean> x = null;
        x = wildCard(x, familyName, pn.get(group, PersonName.Component.FamilyName), ignoreCase);
        x = wildCard(x, givenName, pn.get(group, PersonName.Component.GivenName), ignoreCase);
        x = wildCard(x, middleName, pn.get(group, PersonName.Component.MiddleName), ignoreCase);
        return x;
    }

    private <T> Expression<Boolean> fuzzyMatch(CriteriaQuery<T> q, Path<org.dcm4chee.arc.entity.PersonName> qpn,
            PersonName pn, QueryParam param) {
        Expression<Boolean> x = null;
        x = fuzzyMatch(q, x, qpn, pn, PersonName.Component.FamilyName, param);
        x = fuzzyMatch(q, x, qpn, pn, PersonName.Component.GivenName, param);
        x = fuzzyMatch(q, x, qpn, pn, PersonName.Component.MiddleName, param);
        return x;
    }

    private <T> Expression<Boolean> fuzzyMatch(CriteriaQuery<T> q, Expression<Boolean> x,
            Path<org.dcm4chee.arc.entity.PersonName> qpn, PersonName pn, PersonName.Component c, QueryParam param) {
        String name = StringUtils.maskNull(pn.get(c), "*");
        if (name.equals("*"))
            return x;

        Iterator<String> parts = SoundexCode.tokenizePersonNameComponent(name);
        for (int i = 0; parts.hasNext(); ++i)
            x = fuzzyMatch(q, x, qpn, c, i, parts.next(), param);

        return x;
    }

    private <T> Expression<Boolean> fuzzyMatch(CriteriaQuery<T> q, Expression<Boolean> x,
            Path<org.dcm4chee.arc.entity.PersonName> qpn, PersonName.Component c, int partIndex, String name,
            QueryParam param) {
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
                   : cb.equal(soundexCode.get(SoundexCode_.codeValue), fuzzyName));
        if (!param.isPersonNameComponentOrderInsensitiveMatching()) {
            y = cb.and(y, cb.and(
                    cb.equal(soundexCode.get(SoundexCode_.personNameComponent), c),
                    cb.equal(soundexCode.get(SoundexCode_.componentPartIndex), partIndex)));
        }
        return and(x, cb.exists(sq.where(y)));
    }

    private enum FormatDate {
        DA {
            @Override
            String format(Date date) {
                return DateUtils.formatDA(null, date);
            }
        },
        TM {
            @Override
            String format(Date date) {
                return DateUtils.formatTM(null, date);
            }
        },
        DT {
            @Override
            String format(Date date) {
                return DateUtils.formatDT(null, date);
            }
        };
        abstract String format(Date date);
    }

    private Expression<Boolean> dateRange(Expression<Boolean> x, Path<String> path, String s, FormatDate dt) {
        return dateRange(x, path, parseDateRange(s), dt);
    }

    private Expression<Boolean> dateRange(Expression<Boolean> x, Path<String> path, DateRange range, FormatDate dt) {
        return isUniversalMatching(range) ? x : and(x, cb.and(dateRange(path, range, dt), cb.notEqual(path, "*")));
    }

    private Expression<Boolean> dateRange(Path<String> path, DateRange range, FormatDate dt) {
        String start = format(range.getStartDate(), dt);
        String end = format(range.getEndDate(), dt);
        return start == null ? cb.lessThanOrEqualTo(path, end)
                : end == null ? cb.greaterThanOrEqualTo(path, start)
                : start.equals(end) ? cb.equal(path, start)
                : (dt.equals(FormatDate.TM) && range.isStartDateExeedsEndDate())
                ? cb.or(cb.between(path, start, "115959.999"), cb.between(path, "000000.000", end))
                : cb.between(path, start, end);
    }

    private Expression<Boolean> dateRange(Expression<Boolean> x, Path<Date> path, String s) {
        DateRange range = parseDateRange(s);
        return dateRange(x, path, range);
    }

    private Expression<Boolean> dateRange(Expression<Boolean> x, Path<Date> path, DateRange range) {
        return isUniversalMatching(range) ? x : and(x, dateRange(path, range));
    }

    private Expression<Boolean> dateRange(Path<Date> path, DateRange range) {
        Date startDate = range.getStartDate();
        Date endDate = range.getEndDate();
        return startDate == null ? cb.lessThanOrEqualTo(path, endDate)
                : endDate == null ? cb.greaterThanOrEqualTo(path, startDate)
                : startDate.equals(endDate) ? cb.equal(path, startDate)
                : cb.between(path, startDate, endDate);
    }

    private static String format(Date date, FormatDate dt) {
        return date != null ? dt.format(date) : null;
    }

    private Expression<Boolean> dateRange(Expression<Boolean> x, Path<String> datePath, Path<String> timePath,
            int dateTag, int timeTag, long dateAndTimeTag, Attributes keys, boolean combinedDatetimeMatching) {
        DateRange dateRange = keys.getDateRange(dateTag, null);
        DateRange timeRange = keys.getDateRange(timeTag, null);
        if (combinedDatetimeMatching && !isUniversalMatching(dateRange) && !isUniversalMatching(timeRange)) {
            x = and(x, cb.and(combinedRange(
                    datePath, timePath, keys.getDateRange(dateAndTimeTag, null)), cb.notEqual(datePath, "*")));
        } else {
            x = dateRange(x, datePath, dateRange, FormatDate.DA);
            x = dateRange(x, timePath, timeRange, FormatDate.TM);
        }
        return x;
    }

    private Expression<Boolean> combinedRange(Path<String> datePath, Path<String> timePath, DateRange dateRange) {
        if (dateRange.getStartDate() == null)
            return combinedRangeEnd(datePath, timePath,
                    DateUtils.formatDA(null, dateRange.getEndDate()),
                    DateUtils.formatTM(null, dateRange.getEndDate()));
        if (dateRange.getEndDate() == null)
            return combinedRangeStart(datePath, timePath,
                    DateUtils.formatDA(null, dateRange.getStartDate()),
                    DateUtils.formatTM(null, dateRange.getStartDate()));
        return combinedRangeInterval(datePath, timePath,
                dateRange.getStartDate(), dateRange.getEndDate());
    }

    private Expression<Boolean> combinedRangeInterval(Path<String> datePath, Path<String> timePath,
            Date startDateRange, Date endDateRange) {
        String startTime = DateUtils.formatTM(null, startDateRange);
        String endTime = DateUtils.formatTM(null, endDateRange);
        String startDate = DateUtils.formatDA(null, startDateRange);
        String endDate = DateUtils.formatDA(null, endDateRange);
        return endDate.equals(startDate)
                ? cb.and(
                        cb.equal(datePath, startDate),
                        cb.greaterThanOrEqualTo(timePath, startTime),
                        cb.lessThanOrEqualTo(timePath, endTime))
                : cb.and(
                        combinedRangeStart(datePath, timePath, startDate, startTime),
                        combinedRangeEnd(datePath, timePath, endDate, endTime));
    }

    private Expression<Boolean> combinedRangeEnd(Path<String> datePath, Path<String> timePath,
            String endDate, String endTime) {
        return cb.or(
                cb.lessThan(datePath, endDate),
                cb.and(
                        cb.equal(datePath, endDate),
                        cb.or(
                                cb.lessThanOrEqualTo(timePath, endTime),
                                cb.equal(timePath, "*"))));
    }

    private Expression<Boolean> combinedRangeStart(Path<String> datePath, Path<String> timePath,
            String startDate, String startTime) {
        return cb.or(
                cb.greaterThan(datePath, startDate),
                cb.and(
                        cb.equal(datePath, startDate),
                        cb.or(
                                cb.greaterThanOrEqualTo(timePath, startTime),
                                cb.equal(timePath, "*"))));
    }

    private static DateRange parseDateRange(String s) {
        if (s == null)
            return null;

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
