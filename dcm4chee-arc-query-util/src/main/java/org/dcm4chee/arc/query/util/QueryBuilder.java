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
import org.dcm4che3.dict.archive.PrivateTag;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4che3.soundex.FuzzyStr;
import org.dcm4che3.util.DateUtils;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.entity.*;

import javax.persistence.criteria.*;
import javax.persistence.metamodel.SingularAttribute;
import java.util.*;
import java.util.function.Function;

import static org.dcm4chee.arc.entity.Instance_.contentItems;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Aug 2015
 */
public class QueryBuilder {

    private final CriteriaBuilder cb;

    public QueryBuilder(CriteriaBuilder cb) {
        this.cb = Objects.requireNonNull(cb);
    }

    public List<Order> orderPatients(Root<Patient> patient, List<OrderByTag> orderByTags) {
        List<Order> result = new ArrayList<>(orderByTags.size());
        for (OrderByTag orderByTag : orderByTags)
            orderPatients(patient, orderByTag, result);
        return result;
    }

    public List<Order> orderStudies(From<Study, Patient> patient, Root<Study> study,
            List<OrderByTag> orderByTags) {
        List<Order> result = new ArrayList<>(orderByTags.size());
        for (OrderByTag orderByTag : orderByTags)
            orderStudies(patient, study, orderByTag, result);
        return result;
    }

    public List<Order> orderSeries(From<Study, Patient> patient, From<Series, Study> study, Root<Series> series,
            List<OrderByTag> orderByTags) {
        List<Order> result = new ArrayList<>(orderByTags.size());
        for (OrderByTag orderByTag : orderByTags)
            orderSeries(patient, study, series, orderByTag, result);
        return result;
    }

    public List<Order> orderInstances(From<Study, Patient> patient, From<Series, Study> study,
            From<Instance, Series> series, Root<Instance> instance, List<OrderByTag> orderByTags) {
        List<Order> result = new ArrayList<>(orderByTags.size());
        for (OrderByTag orderByTag : orderByTags)
            orderInstances(patient, study, series, instance, orderByTag, result);
        return result;
    }

    public List<Order> orderMWLItems(From<MWLItem, Patient> patient, Root<MWLItem> mwlItem,
            List<OrderByTag> orderByTags) {
        List<Order> result = new ArrayList<>(orderByTags.size());
        for (OrderByTag orderByTag : orderByTags)
            orderMWLItems(patient, mwlItem, orderByTag, result);
        return result;
    }

    public List<Order> orderWorkitems(Join<UPS, Patient> patient, Root<UPS> ups, List<OrderByTag> orderByTags) {
        List<Order> result = new ArrayList<>(orderByTags.size());
        for (OrderByTag orderByTag : orderByTags)
            orderWorkitems(patient, ups, orderByTag, result);
        return result;
    }

    private <Z> boolean orderPatients(From<Z, Patient> patient, OrderByTag orderByTag, List<Order> result) {
        switch (orderByTag.tag) {
            case Tag.PatientName:
                return orderPersonName(patient, Patient_.patientName, orderByTag, result);
            case Tag.PatientSex:
                return result.add(orderByTag.order(cb, patient.get(Patient_.patientSex)));
            case Tag.PatientBirthDate:
                return result.add(orderByTag.order(cb, patient.get(Patient_.patientBirthDate)));
        }
        return false;
    }

    private <Z> boolean orderStudies(From<Study, Patient> patient, From<Z, Study> study,
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
                return orderPersonName(study, Study_.referringPhysicianName, orderByTag, result);
            case Tag.StudyDescription:
                return result.add(orderByTag.order(cb, study.get(Study_.studyDescription)));
            case Tag.AccessionNumber:
                return result.add(orderByTag.order(cb, study.get(Study_.accessionNumber)));
        }
        return false;
    }

    private <Z> boolean orderSeries(From<Study, Patient> patient, From<Series, Study> study, From<Z, Series> series,
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
                return orderPersonName(series, Series_.performingPhysicianName, orderByTag, result);
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

    private <Z> boolean orderInstances(From<Study, Patient> patient, From<Series, Study> study,
            From<Instance, Series> series, From<Z, Instance> instance, OrderByTag orderByTag, List<Order> result) {
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

    private <Z> boolean orderMWLItems(From<MWLItem, Patient> patient, From<Z, MWLItem> mwlItem,
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
                return orderPersonName(mwlItem, MWLItem_.scheduledPerformingPhysicianName, orderByTag, result);
            case Tag.ScheduledProcedureStepID:
                return result.add(orderByTag.order(cb, mwlItem.get(MWLItem_.scheduledProcedureStepID)));
            case Tag.RequestedProcedureID:
                return result.add(orderByTag.order(cb, mwlItem.get(MWLItem_.requestedProcedureID)));
        }
        return false;
    }

    private <Z> boolean orderWorkitems(Join<UPS, Patient> patient, Root<UPS> ups, OrderByTag orderByTag, List<Order> result) {
        if (patient != null && orderPatients(patient, orderByTag, result))
            return true;

        switch (orderByTag.tag) {
            case Tag.ScheduledProcedureStepPriority:
                return result.add(orderByTag.order(cb, ups.get(UPS_.upsPriority)));
            case Tag.ScheduledProcedureStepModificationDateTime:
                return result.add(orderByTag.order(cb, ups.get(UPS_.updatedTime)));
            case Tag.ProcedureStepLabel:
                return result.add(orderByTag.order(cb, ups.get(UPS_.upsLabel)));
            case Tag.WorklistLabel:
                return result.add(orderByTag.order(cb, ups.get(UPS_.worklistLabel)));
            case Tag.ScheduledProcedureStepStartDateTime:
                return result.add(orderByTag.order(cb, ups.get(UPS_.scheduledStartDateAndTime)));
            case Tag.ExpectedCompletionDateTime:
                return result.add(orderByTag.order(cb, ups.get(UPS_.expectedCompletionDateAndTime)));
            case Tag.ScheduledProcedureStepExpirationDateTime:
                return result.add(orderByTag.order(cb, ups.get(UPS_.scheduledProcedureStepExpirationDateTime)));
            case Tag.InputReadinessState:
                return result.add(orderByTag.order(cb, ups.get(UPS_.inputReadinessState)));
            case Tag.AdmissionID:
                return result.add(orderByTag.order(cb, ups.get(UPS_.admissionID)));
            case Tag.ProcedureStepState:
                return result.add(orderByTag.order(cb, ups.get(UPS_.procedureStepState)));
        }
        return false;
    }

    private <Z, X> boolean orderPersonName(
            From<Z, X> entity, SingularAttribute<X, org.dcm4chee.arc.entity.PersonName> attribute,
            OrderByTag orderByTag, List<Order> result) {
        return orderPersonName(joinPersonName(entity, attribute), orderByTag, result);
    }

    private <Z, X> Path<org.dcm4chee.arc.entity.PersonName> joinPersonName(
            From<Z, X> entity, SingularAttribute<X, org.dcm4chee.arc.entity.PersonName> attribute) {
        return (Path<org.dcm4chee.arc.entity.PersonName>) entity.getJoins().stream()
                .filter(j -> j.getAttribute().equals(attribute))
                .findFirst()
                .orElseGet(() -> entity.join(attribute, JoinType.LEFT));
    }

    private boolean orderPersonName(Path<org.dcm4chee.arc.entity.PersonName> personName,
            OrderByTag orderByTag, List<Order> result) {
        result.add(orderByTag.order(cb, personName.get(PersonName_.familyName)));
        result.add(orderByTag.order(cb, personName.get(PersonName_.givenName)));
        result.add(orderByTag.order(cb, personName.get(PersonName_.middleName)));
        return true;
    }

    public <Z> void patientIDPredicate(List<Predicate> predicates, From<Z, Patient> patient, IDWithIssuer[] pids) {
        if (isUniversalMatching(pids))
            return;

        Join<Patient, PatientID> patientID = patient.join(Patient_.patientID);
        Join<PatientID, IssuerEntity> issuer = containsIssuer(pids)
                ? patientID.join(PatientID_.issuer, JoinType.LEFT)
                : null;
        List<Predicate> idPredicates = new ArrayList<>(pids.length);
        for (IDWithIssuer pid : pids) {
            if (!isUniversalMatching(pid.getID())) {
                List<Predicate> idPredicate = new ArrayList<>(3);
                if (wildCard(idPredicate, patientID.get(PatientID_.id), pid.getID())) {
                    if (pid.getIssuer() != null)
                        issuer(idPredicate, issuer, pid.getIssuer());
                    idPredicates.add(cb.and(idPredicate.toArray(new Predicate[0])));
                }
            }
        }
        if (!idPredicates.isEmpty())
            predicates.add(cb.or(idPredicates.toArray(new Predicate[0])));
    }

    public <T> List<Predicate> patientPredicates(CriteriaQuery<T> q,
            Root<Patient> patient, IDWithIssuer[] pids, Attributes keys, QueryParam queryParam) {
        List<Predicate> predicates = new ArrayList<>();
        patientLevelPredicates(predicates, q, patient, pids, keys, queryParam, QueryRetrieveLevel2.PATIENT);
        return predicates;
    }

    public <T> List<Predicate> studyPredicates(CriteriaQuery<T> q,
            From<Study, Patient> patient, Root<Study> study,
            IDWithIssuer[] pids, Attributes keys, QueryParam queryParam) {
        List<Predicate> predicates = new ArrayList<>();
        patientLevelPredicates(predicates, q, patient, pids, keys, queryParam, QueryRetrieveLevel2.STUDY);
        studyLevelPredicates(predicates, q, study, keys, queryParam, QueryRetrieveLevel2.STUDY);
        return predicates;
    }

    public <T> List<Predicate> seriesPredicates(CriteriaQuery<T> q,
            From<Study, Patient> patient, From<Series, Study> study, Root<Series> series,
            IDWithIssuer[] pids, Attributes keys, QueryParam queryParam) {
        List<Predicate> predicates = new ArrayList<>();
        patientLevelPredicates(predicates, q, patient, pids, keys, queryParam, QueryRetrieveLevel2.SERIES);
        studyLevelPredicates(predicates, q, study, keys, queryParam, QueryRetrieveLevel2.SERIES);
        seriesLevelPredicates(predicates, q, series, keys, queryParam);
        return predicates;
    }

    public <T> List<Predicate> instancePredicates(CriteriaQuery<T> q,
            From<Study, Patient> patient, From<Series, Study> study, From<Instance, Series> series,
            Root<Instance> instance, IDWithIssuer[] pids, Attributes keys, QueryParam queryParam,
            CodeEntity[] showInstancesRejectedByCodes, CodeEntity[] hideRejectionNoteWithCodes) {
        List<Predicate> predicates = new ArrayList<>();
        patientLevelPredicates(predicates, q, patient, pids, keys, queryParam, QueryRetrieveLevel2.IMAGE);
        studyLevelPredicates(predicates, q, study, keys, queryParam, QueryRetrieveLevel2.IMAGE);
        seriesLevelPredicates(predicates, q, series, keys, queryParam);
        instanceLevelPredicates(predicates, q, study, series, instance, keys, queryParam,
                showInstancesRejectedByCodes, hideRejectionNoteWithCodes);
        return predicates;
    }

    public <T> List<Predicate> mwlItemPredicates(CriteriaQuery<T> q,
            From<MWLItem, Patient> patient, Root<MWLItem> mwlItem,
            IDWithIssuer[] pids, Attributes keys, QueryParam queryParam) {
        List<Predicate> predicates = new ArrayList<>();
        patientLevelPredicates(predicates, q, patient, pids, keys, queryParam, null);
        mwlItemLevelPredicates(predicates, q, mwlItem, keys, queryParam);
        return predicates;
    }

    public <T> List<Predicate> upsPredicates(CriteriaQuery<T> q,
            Join<UPS, Patient> patient, Root<UPS> ups,
            IDWithIssuer[] pids, Attributes keys, QueryParam queryParam) {
        List<Predicate> predicates = new ArrayList<>();
        patientLevelPredicates(predicates, q, patient, pids, keys, queryParam, null);
        upsLevelPredicates(predicates, q, ups, keys, queryParam);
        return predicates;
    }

    private <T, Z> void patientLevelPredicates(List<Predicate> predicates, CriteriaQuery<T> q,
            From<Z, Patient> patient, IDWithIssuer[] pids, Attributes keys, QueryParam queryParam,
            QueryRetrieveLevel2 queryRetrieveLevel) {
        if (patient == null)
            return;

        if (queryRetrieveLevel == QueryRetrieveLevel2.PATIENT) {
            predicates.add(patient.get(Patient_.mergedWith).isNull());
            if (!queryParam.isWithoutStudies())
                predicates.add(cb.greaterThan(patient.get(Patient_.numberOfStudies), 0));
        }
        patientIDPredicate(predicates, patient, pids);
        personName(predicates, q, patient, Patient_.patientName,
                keys.getString(Tag.PatientName, "*"), queryParam);
        anyOf(predicates, patient.get(Patient_.patientSex),
                toUpperCase(keys.getStrings(Tag.PatientSex)), false);
        dateRange(predicates, patient.get(Patient_.patientBirthDate),
                keys.getDateRange(Tag.PatientBirthDate), FormatDate.DA);
        personName(predicates, q, patient, Patient_.responsiblePerson,
                keys.getString(Tag.ResponsiblePerson, "*"), queryParam);
        AttributeFilter attrFilter = queryParam.getAttributeFilter(Entity.Patient);
        wildCard(predicates, patient.get(Patient_.patientCustomAttribute1),
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute1(), "*"), true);
        wildCard(predicates, patient.get(Patient_.patientCustomAttribute2),
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute2(), "*"), true);
        wildCard(predicates, patient.get(Patient_.patientCustomAttribute3),
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute3(), "*"), true);
        if (queryParam.getPatientVerificationStatus() != null)
            predicates.add(cb.equal(patient.get(Patient_.verificationStatus), queryParam.getPatientVerificationStatus()));
    }

    public static boolean hasPatientLevelPredicates(IDWithIssuer[] pids, Attributes keys, QueryParam queryParam) {
        if (!isUniversalMatching(pids))
            return true;

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

    public void uidsPredicate(List<Predicate> x, Path<String> path, String... values) {
        if (!isUniversalMatching(values))
            x.add(values.length == 1 ? cb.equal(path, values[0]) : path.in(values));
    }

    public Predicate[] splitUIDPredicates(Path<String> path, String[] values, int inExpressionCountLimit) {
        if (isUniversalMatching(values))
            return new Predicate[0];

        if (values.length <= inExpressionCountLimit)
            return new Predicate[] { path.in(values) };

        Predicate[] predicates = new Predicate[(values.length - 1) / inExpressionCountLimit + 1];
        int remaining = values.length % inExpressionCountLimit;
        int last = predicates.length;
        if (remaining > 0) {
            String[] dst = new String[remaining];
            System.arraycopy(values, values.length - remaining, dst, 0, remaining);
            predicates[--last] = path.in(dst);
        }
        String[] dst = new String[inExpressionCountLimit];
        for (int i = 0; i < last; i++) {
            System.arraycopy(values, i * inExpressionCountLimit, dst, 0, inExpressionCountLimit);
            predicates[i] = path.in(dst);
        }
        return predicates;
    }

    private <T, Z> List<Predicate> studyLevelPredicates(List<Predicate> predicates, CriteriaQuery<T> q,
            From<Z, Study> study, Attributes keys, QueryParam queryParam, QueryRetrieveLevel2 queryRetrieveLevel) {
        boolean combinedDatetimeMatching = queryParam.isCombinedDatetimeMatching();
        accessControl(predicates, study, queryParam.getAccessControlIDs());
        uidsPredicate(predicates, study.get(Study_.studyInstanceUID), keys.getStrings(Tag.StudyInstanceUID));
        anyOf(predicates, study.get(Study_.studyID), keys.getStrings(Tag.StudyID), false);
        dateRange(predicates, study.get(Study_.studyDate), study.get(Study_.studyTime),
                Tag.StudyDate, Tag.StudyTime, Tag.StudyDateAndTime,
                keys, combinedDatetimeMatching);
        personName(predicates, q, study, Study_.referringPhysicianName,
                keys.getString(Tag.ReferringPhysicianName, "*"), queryParam);
        anyOf(predicates, study.get(Study_.studyDescription),
                keys.getStrings(Tag.StudyDescription), true);
        String accNo = keys.getString(Tag.AccessionNumber, "*");
        if (!isUniversalMatching(accNo)) {
            Issuer issuer = Issuer.valueOf(keys.getNestedDataset(Tag.IssuerOfAccessionNumberSequence));
            if (issuer == null)
                issuer = queryParam.getDefaultIssuerOfAccessionNumber();
            idWithIssuer(predicates, study, Study_.accessionNumber, Study_.issuerOfAccessionNumber, accNo, issuer);
        }
        String[] modalitiesInStudy = keys.getStrings(Tag.ModalitiesInStudy);
        if (queryParam.isAllOfModalitiesInStudy() && modalitiesInStudy != null && modalitiesInStudy.length > 1) {
            for (String modality : modalitiesInStudy) {
                seriesAttributesInStudy(predicates, q, study, keys, queryParam, queryRetrieveLevel, modality);
            }
        } else {
            seriesAttributesInStudy(predicates, q, study, keys, queryParam, queryRetrieveLevel, modalitiesInStudy);
        }
        procedureCode(predicates, q, study, keys.getNestedDataset(Tag.ProcedureCodeSequence));
        if (queryParam.isHideNotRejectedInstances())
            predicates.add(cb.notEqual(study.get(Study_.rejectionState), RejectionState.NONE));
        AttributeFilter attrFilter = queryParam.getAttributeFilter(Entity.Study);
        wildCard(predicates, study.get(Study_.studyCustomAttribute1),
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute1(), "*"), true);
        wildCard(predicates, study.get(Study_.studyCustomAttribute2),
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute2(), "*"), true);
        wildCard(predicates, study.get(Study_.studyCustomAttribute3),
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute3(), "*"), true);
        dateRange(predicates, study.get(Study_.createdTime),
                keys.getDateRange(PrivateTag.PrivateCreator, PrivateTag.StudyReceiveDateTime, VR.DT));
        if (queryParam.getExternalRetrieveAET() != null)
            predicates.add(cb.equal(study.get(Study_.externalRetrieveAET), queryParam.getExternalRetrieveAET()));
        if (queryParam.getExternalRetrieveAETNot() != null)
            predicates.add(cb.notEqual(study.get(Study_.externalRetrieveAET), queryParam.getExternalRetrieveAETNot()));
        if (queryRetrieveLevel == QueryRetrieveLevel2.STUDY) {
            if (queryParam.isIncomplete())
                predicates.add(cb.notEqual(study.get(Study_.completeness), Completeness.COMPLETE));
            if (queryParam.isRetrieveFailed())
                predicates.add(cb.greaterThan(study.get(Study_.failedRetrieves), 0));
        }
        if (queryParam.getExpirationDate() != null)
            dateRange(predicates, study.get(Study_.expirationDate), queryParam.getExpirationDate(), FormatDate.DA);
        if (queryParam.getStudyStorageIDs() != null)
            predicates.add(study.get(Study_.storageIDs).in(queryParam.getStudyStorageIDs()));
        if (queryParam.getMinStudySize() != 0)
            predicates.add(cb.greaterThanOrEqualTo(study.get(Study_.size), queryParam.getMinStudySize()));
        if (queryParam.getMaxStudySize() != 0)
            predicates.add(cb.lessThanOrEqualTo(study.get(Study_.size), queryParam.getMaxStudySize()));
        if (queryParam.getExpirationState() != null)
            predicates.add(study.get(Study_.expirationState).in(queryParam.getExpirationState()));
        return predicates;
    }

    public static void accessControl(List<Predicate> predicates, Path<Study> study, String[] accessControlIDs) {
        if (accessControlIDs.length == 0)
            return;

        String[] a = new String[accessControlIDs.length + 1];
        a[0] = "*";
        System.arraycopy(accessControlIDs, 0, a, 1, accessControlIDs.length);
        predicates.add(study.get(Study_.accessControlID).in(a));
    }

    private <T, Z> List<Predicate> seriesLevelPredicates(List<Predicate> predicates, CriteriaQuery<T> q,
            From<Z, Series> series, Attributes keys, QueryParam queryParam) {
        uidsPredicate(predicates, series.get(Series_.seriesInstanceUID), keys.getStrings(Tag.SeriesInstanceUID));
        numberPredicate(predicates, series.get(Series_.seriesNumber), keys.getString(Tag.SeriesNumber, "*"));
        anyOf(predicates, series.get(Series_.modality),
                toUpperCase(keys.getStrings(Tag.Modality)), false);
        anyOf(predicates, series.get(Series_.bodyPartExamined),
                toUpperCase(keys.getStrings(Tag.BodyPartExamined)), false);
        anyOf(predicates, series.get(Series_.laterality),
                toUpperCase(keys.getStrings(Tag.Laterality)), false);
        dateRange(predicates,
                series.get(Series_.performedProcedureStepStartDate),
                series.get(Series_.performedProcedureStepStartTime),
                Tag.PerformedProcedureStepStartDate, Tag.PerformedProcedureStepStartTime,
                Tag.PerformedProcedureStepStartDateAndTime,
                keys, queryParam.isCombinedDatetimeMatching());
        personName(predicates, q, series, Series_.performingPhysicianName,
                keys.getString(Tag.PerformingPhysicianName), queryParam);
        anyOf(predicates, series.get(Series_.seriesDescription),
                keys.getStrings(Tag.SeriesDescription), true);
        anyOf(predicates, series.get(Series_.stationName),
                keys.getStrings(Tag.StationName), true);
        anyOf(predicates, series.get(Series_.institutionalDepartmentName),
                keys.getStrings(Tag.InstitutionalDepartmentName), true);
        anyOf(predicates, series.get(Series_.institutionName),
                keys.getStrings(Tag.InstitutionName), true);
        Attributes reqAttrs = keys.getNestedDataset(Tag.RequestAttributesSequence);
        requestAttributes(predicates, q, series, reqAttrs, queryParam);
        code(predicates, series.get(Series_.institutionCode), keys.getNestedDataset(Tag.InstitutionCodeSequence));
        if (queryParam.isHideNotRejectedInstances())
            predicates.add(cb.notEqual(series.get(Series_.rejectionState), RejectionState.NONE));
        AttributeFilter attrFilter = queryParam.getAttributeFilter(Entity.Series);
        wildCard(predicates, series.get(Series_.seriesCustomAttribute1),
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute1(), "*"), true);
        wildCard(predicates, series.get(Series_.seriesCustomAttribute2),
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute2(), "*"), true);
        wildCard(predicates, series.get(Series_.seriesCustomAttribute3),
                AttributeFilter.selectStringValue(keys, attrFilter.getCustomAttribute3(), "*"), true);
        if (queryParam.isIncomplete())
            predicates.add(cb.notEqual(series.get(Series_.completeness), Completeness.COMPLETE));
        if (queryParam.isRetrieveFailed())
            predicates.add(cb.greaterThan(series.get(Series_.failedRetrieves), 0));
        if (queryParam.isStorageVerificationFailed())
            predicates.add(cb.greaterThan(series.get(Series_.failuresOfLastStorageVerification), 0));
        if (queryParam.isMetadataUpdateFailed())
            predicates.add(cb.greaterThan(series.get(Series_.metadataUpdateFailures), 0));
        if (queryParam.isCompressionFailed())
            predicates.add(cb.greaterThan(series.get(Series_.compressionFailures), 0));
        anyOf(predicates, series.get(Series_.sourceAET),
                keys.getStrings(PrivateTag.PrivateCreator, PrivateTag.SendingApplicationEntityTitleOfSeries, VR.AE),
                false);
        if (queryParam.getExpirationDate() != null)
            dateRange(predicates, series.get(Series_.expirationDate), queryParam.getExpirationDate(), FormatDate.DA);
        return predicates;
    }

    private <T> void instanceLevelPredicates(List<Predicate> predicates, CriteriaQuery<T> q,
            Path<Study> study, Path<Series> series, Root<Instance> instance, Attributes keys, QueryParam queryParam,
            CodeEntity[] showInstancesRejectedByCodes, CodeEntity[] hideRejectionNoteWithCodes) {
        boolean combinedDatetimeMatching = queryParam.isCombinedDatetimeMatching();
        uidsPredicate(predicates, instance.get(Instance_.sopInstanceUID), keys.getStrings(Tag.SOPInstanceUID));
        uidsPredicate(predicates, instance.get(Instance_.sopClassUID), keys.getStrings(Tag.SOPClassUID));
        numberPredicate(predicates, instance.get(Instance_.instanceNumber), keys.getString(Tag.InstanceNumber, "*"));
        anyOf(predicates, instance.get(Instance_.verificationFlag),
                toUpperCase(keys.getStrings(Tag.VerificationFlag)), false);
        anyOf(predicates, instance.get(Instance_.completionFlag),
                toUpperCase(keys.getStrings(Tag.CompletionFlag)), false);
        dateRange(predicates,
                instance.get(Instance_.contentDate),
                instance.get(Instance_.contentTime),
                Tag.ContentDate, Tag.ContentTime, Tag.ContentDateAndTime,
                keys, combinedDatetimeMatching);
        if (!code(predicates, instance.get(Instance_.conceptNameCode),
                keys.getNestedDataset(Tag.ConceptNameCodeSequence)))
            hideRejectionNote(predicates, instance, hideRejectionNoteWithCodes);
        verifyingObserver(predicates, q, instance, keys.getNestedDataset(Tag.VerifyingObserverSequence), queryParam);
        Sequence contentSeq = keys.getSequence(Tag.ContentSequence);
        if (contentSeq != null)
            for (Attributes item : contentSeq)
                contentItem(predicates, q, instance, item);
        AttributeFilter attrFilter = queryParam
                .getAttributeFilter(Entity.Instance);
        wildCard(predicates,
                instance.get(Instance_.instanceCustomAttribute1),
                AttributeFilter.selectStringValue(keys,
                        attrFilter.getCustomAttribute1(), "*"),
                true);
        wildCard(predicates,
                instance.get(Instance_.instanceCustomAttribute2),
                AttributeFilter.selectStringValue(keys,
                        attrFilter.getCustomAttribute2(), "*"),
                true);
        wildCard(predicates,
                instance.get(Instance_.instanceCustomAttribute3),
                AttributeFilter.selectStringValue(keys,
                        attrFilter.getCustomAttribute3(), "*"),
                true);
        hideRejectedInstance(predicates, q, study, series, instance,
                showInstancesRejectedByCodes, queryParam.isHideNotRejectedInstances());
    }

    public <T> List<Predicate> sopInstanceRefs(CriteriaQuery<T> q,
            Path<Study> study, Path<Series> series, Root<Instance> instance,
            String studyIUID, String seriesUID, String objectUID, QueryRetrieveView qrView,
            CodeEntity[] showInstancesRejectedByCodes, CodeEntity[] hideRejectionNoteWithCodes) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(study.get(Study_.studyInstanceUID), studyIUID));
        if (!isUniversalMatching(seriesUID))
            predicates.add(cb.equal(series.get(Series_.seriesInstanceUID), seriesUID));
        if (!isUniversalMatching(objectUID))
            predicates.add(cb.equal(instance.get(Instance_.sopInstanceUID), objectUID));
        hideRejectedInstance(predicates, q, study, series, instance,
                showInstancesRejectedByCodes, qrView.isHideNotRejectedInstances());
        hideRejectionNote(predicates, instance, hideRejectionNoteWithCodes);
        return predicates;
    }

    private <T> void mwlItemLevelPredicates(List<Predicate> predicates, CriteriaQuery<T> q, Root<MWLItem> mwlItem,
            Attributes keys, QueryParam queryParam) {
        uidsPredicate(predicates, mwlItem.get(MWLItem_.studyInstanceUID), keys.getStrings(Tag.StudyInstanceUID));
        anyOf(predicates, mwlItem.get(MWLItem_.requestedProcedureID),
                keys.getStrings(Tag.RequestedProcedureID), false);
        String accNo = keys.getString(Tag.AccessionNumber, "*");
        if (!isUniversalMatching(accNo)) {
            Issuer issuer = Issuer.valueOf(keys.getNestedDataset(Tag.IssuerOfAccessionNumberSequence));
            if (issuer == null) {
                issuer = queryParam.getDefaultIssuerOfAccessionNumber();
            }
            idWithIssuer(predicates, mwlItem, MWLItem_.accessionNumber, MWLItem_.issuerOfAccessionNumber, accNo, issuer);
        }
        Attributes sps = keys.getNestedDataset(Tag.ScheduledProcedureStepSequence);
        if (sps != null) {
            anyOf(predicates, mwlItem.get(MWLItem_.scheduledProcedureStepID),
                    sps.getStrings(Tag.ScheduledProcedureStepID), false);
            dateRange(predicates,
                    mwlItem.get(MWLItem_.scheduledStartDate),
                    mwlItem.get(MWLItem_.scheduledStartTime),
                    Tag.ScheduledProcedureStepStartDate,
                    Tag.ScheduledProcedureStepStartDate,
                    Tag.ScheduledProcedureStepStartDateAndTime,
                    sps, true);
            personName(predicates, q, mwlItem, MWLItem_.scheduledPerformingPhysicianName,
                    sps.getString(Tag.ScheduledPerformingPhysicianName, "*"), queryParam);
            anyOf(predicates, mwlItem.get(MWLItem_.modality),
                    toUpperCase(sps.getStrings(Tag.Modality)), false);
            anyOf(predicates, mwlItem.get(MWLItem_.status), SPSStatus::valueOf,
                    toUpperCase(sps.getStrings(Tag.ScheduledProcedureStepStatus)));
            String spsAET = sps.getString(Tag.ScheduledStationAETitle, "*");
            if (!isUniversalMatching(spsAET))
                predicates.add(cb.isMember(spsAET, mwlItem.get(MWLItem_.scheduledStationAETs)));
        }
        hideSPSWithStatus(predicates, mwlItem, queryParam);
    }

    private <T> boolean anyOf(List<Predicate> predicates, Path<T> path, Function<String, T> valueOf,
            String[] names) {
        if (isUniversalMatching(names))
            return false;

        if (names.length == 1) {
            try {
                predicates.add(cb.equal(path, valueOf.apply(names[0])));
                return true;
            } catch (IllegalArgumentException e) {}
            return false;
        }

        List<Predicate> y = new ArrayList<>(names.length);
        for (String name : names) {
            if (isUniversalMatching(name))
                return false;
            try {
                y.add(cb.equal(path, valueOf.apply(name)));
            } catch (IllegalArgumentException e) {}
        }
        if (y.isEmpty())
            return false;

        predicates.add(cb.or(y.toArray(new Predicate[0])));
        return true;
    }

    private void hideSPSWithStatus(List<Predicate> predicates, Path<MWLItem> mwlItem, QueryParam queryParam) {
        SPSStatus[] hideSPSWithStatusFromMWL = queryParam.getHideSPSWithStatusFromMWL();
        if (hideSPSWithStatusFromMWL.length > 0)
            predicates.add(mwlItem.get(MWLItem_.status).in(hideSPSWithStatusFromMWL).not());
    }

    private <T> void upsLevelPredicates(List<Predicate> predicates, CriteriaQuery<T> q,
            Root<UPS> ups, Attributes keys, QueryParam queryParam) {
        uidsPredicate(predicates, ups.get(UPS_.upsInstanceUID), keys.getStrings(Tag.SOPInstanceUID));
        anyOf(predicates, ups.get(UPS_.upsPriority), UPSPriority::valueOf,
                toUpperCase(keys.getStrings(Tag.ScheduledProcedureStepPriority)));
        dateRange(predicates, ups.get(UPS_.updatedTime),
                keys.getDateRange(Tag.ScheduledProcedureStepModificationDateTime));
        anyOf(predicates, ups.get(UPS_.upsLabel), keys.getStrings(Tag.ProcedureStepLabel), true);
        anyOf(predicates, ups.get(UPS_.worklistLabel), keys.getStrings(Tag.WorklistLabel), true);
        code(predicates, ups.get(UPS_.scheduledStationNameCode),
                keys.getNestedDataset(Tag.ScheduledStationNameCodeSequence));
        code(predicates, ups.get(UPS_.scheduledStationClassCode),
                keys.getNestedDataset(Tag.ScheduledStationClassCodeSequence));
        code(predicates, ups.get(UPS_.scheduledStationGeographicLocationCode),
                keys.getNestedDataset(Tag.ScheduledStationGeographicLocationCodeSequence));
        Attributes scheduledHumanPerformersSequence = keys.getNestedDataset(Tag.ScheduledHumanPerformersSequence);
        if (scheduledHumanPerformersSequence != null)
            performerCode(predicates, q, ups,
                scheduledHumanPerformersSequence.getSequence(Tag.HumanPerformerCodeSequence).get(0));
        dateRange(predicates, ups.get(UPS_.scheduledStartDateAndTime),
                keys.getDateRange(Tag.ScheduledProcedureStepStartDateAndTime), FormatDate.DT);
        dateRange(predicates, ups.get(UPS_.expectedCompletionDateAndTime),
                keys.getDateRange(Tag.ExpectedCompletionDateTime), FormatDate.DT);
        dateRange(predicates, ups.get(UPS_.scheduledProcedureStepExpirationDateTime),
                keys.getDateRange(Tag.ScheduledProcedureStepExpirationDateTime), FormatDate.DT);
        code(predicates, ups.get(UPS_.scheduledWorkitemCode),
                keys.getNestedDataset(Tag.ScheduledWorkitemCodeSequence));
        anyOf(predicates, ups.get(UPS_.inputReadinessState), InputReadinessState::valueOf,
                toUpperCase(keys.getStrings(Tag.InputReadinessState)));
        String admissionID = keys.getString(Tag.AdmissionID, "*");
        if (!isUniversalMatching(admissionID)) {
            Issuer issuer = Issuer.valueOf(keys.getNestedDataset(Tag.IssuerOfAdmissionIDSequence));
            if (issuer != null)
              idWithIssuer(predicates, ups, UPS_.admissionID, UPS_.issuerOfAdmissionID, admissionID, issuer);
            else
                predicates.add(cb.equal(ups.get(UPS_.admissionID), admissionID));
        }
        upsRequestAttributes(predicates, q, ups, keys.getNestedDataset(Tag.ReferencedRequestSequence), queryParam);
        uidsPredicate(predicates, ups.get(UPS_.replacedSOPInstanceUID),
                getString(keys.getNestedDataset(Tag.ReplacedProcedureStepSequence),
                        Tag.ReferencedSOPInstanceUID, "*"));
        anyOf(predicates, ups.get(UPS_.procedureStepState), UPSState::valueOf,
                toUpperCase(keys.getStrings(Tag.ProcedureStepState)));
        notSubscribedBy(predicates, q, ups, queryParam.getNotSubscribedByAET());
    }

    private String getString(Attributes item, int tag, String defVal) {
        return item != null ? item.getString(tag, defVal) : defVal;
    }

    public <T> void hideRejectedInstance(List<Predicate> predicates, CriteriaQuery<T> q,
            Path<Study> study, Path<Series> series, Path<Instance> instance, CodeEntity[] codes,
            boolean hideNotRejectedInstances) {
        Subquery<RejectedInstance> sq = q.subquery(RejectedInstance.class);
        Root<RejectedInstance> ri = sq.from(RejectedInstance.class);
        Predicate[] y = new Predicate[codes.length > 0 ? 4 : 3];
        y[0] = cb.equal(ri.get(RejectedInstance_.studyInstanceUID), study.get(Study_.studyInstanceUID));
        y[1] = cb.equal(ri.get(RejectedInstance_.seriesInstanceUID), series.get(Series_.seriesInstanceUID));
        y[2] = cb.equal(ri.get(RejectedInstance_.sopInstanceUID), instance.get(Instance_.sopInstanceUID));
        if (codes.length > 0) {
            y[3] = ri.get(RejectedInstance_.rejectionNoteCode).in(codes);
            if (!hideNotRejectedInstances)
                y[3] = y[3].not();
        }
        Predicate exists = cb.exists(sq.select(ri).where(y));
        predicates.add(hideNotRejectedInstances ? exists : exists.not());
    }

    public void hideRejectionNote(List<Predicate> predicates, Path<Instance> instance, CodeEntity[] codes) {
        if (codes.length > 0)
            predicates.add(
                cb.or(
                    instance.get(Instance_.conceptNameCode).isNull(),
                    instance.get(Instance_.conceptNameCode).in(codes).not()));
    }

    private static boolean containsIssuer(IDWithIssuer[] pids) {
        for (IDWithIssuer pid : pids)
            if (pid.getIssuer() != null)
                return true;
        return false;
    }

    private <Z, X> void idWithIssuer(List<Predicate> predicates, From<Z, X> entity,
            SingularAttribute<X, String> idAttribute, SingularAttribute<X, IssuerEntity> issuerAttribute,
            String id, Issuer issuer) {
        if (wildCard(predicates, entity.get(idAttribute), id) && issuer != null)
            issuer(predicates, entity.join(issuerAttribute, JoinType.LEFT), issuer);
    }

    private void issuer(List<Predicate> predicates, Path<IssuerEntity> issuerPath, Issuer issuer) {
        String entityID = issuer.getLocalNamespaceEntityID();
        String entityUID = issuer.getUniversalEntityID();
        String entityUIDType = issuer.getUniversalEntityIDType();
        if (!isUniversalMatching(entityID))
            predicates.add(cb.or(issuerPath.get(IssuerEntity_.localNamespaceEntityID).isNull(),
                    cb.equal(issuerPath.get(IssuerEntity_.localNamespaceEntityID), entityID)));
        if (!isUniversalMatching(entityUID))
            predicates.add(cb.or(issuerPath.get(IssuerEntity_.universalEntityID).isNull(),
                    cb.and(cb.equal(issuerPath.get(IssuerEntity_.universalEntityID), entityUID),
                            cb.equal(issuerPath.get(IssuerEntity_.universalEntityIDType), entityUIDType))));
    }

    private static boolean isUniversalMatching(Attributes item) {
        return item == null || item.isEmpty();
    }

    private static boolean isUniversalMatching(String value) {
        return value == null || value.equals("*");
    }

    public static boolean isUniversalMatching(String[] values) {
        return values == null || values.length == 0 || values[0].equals("*");
    }

    public static boolean isUniversalMatching(IDWithIssuer[] pids) {
        for (IDWithIssuer pid : pids) {
            if (!isUniversalMatching(pid.getID()))
                return false;
        }
        return true;
    }

    private static boolean isUniversalMatching(DateRange range) {
        return range == null || (range.getStartDate() == null && range.getEndDate() == null);
    }

    private boolean anyOf(List<Predicate> predicates, Expression<String> path, String[] values, boolean ignoreCase) {
        if (isUniversalMatching(values))
            return false;

        if (values.length == 1)
            return wildCard(predicates, path, values[0], ignoreCase);

        List<Predicate> y = new ArrayList<>(values.length);
        for (String value : values) {
            if (!wildCard(y, path, value, ignoreCase))
                return false;
        }
        predicates.add(cb.or(y.toArray(new Predicate[0])));
        return true;
    }

    private boolean wildCard(List<Predicate> predicates, Expression<String> path, String value) {
        return wildCard(predicates, path, value, false);
    }

    private boolean wildCard(List<Predicate> predicates, Expression<String> path, String value, boolean ignoreCase) {
        if (isUniversalMatching(value))
            return false;

        if (ignoreCase && StringUtils.isUpperCase(value))
            path = cb.upper(path);

        if (containsWildcard(value)) {
            String pattern = toLikePattern(value);
            if (pattern.equals("%"))
                return false;

            predicates.add(cb.like(path, pattern, '!'));
        } else {
            predicates.add(cb.equal(path, value));
        }
        return true;
    }

    private void numberPredicate(List<Predicate> predicates, Expression<Integer> path, String value) {
        if (!isUniversalMatching(value))
            try {
                predicates.add(cb.equal(path, Integer.parseInt(value)));
            } catch (NumberFormatException e) {
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

    private static String[] toUpperCase(String[] ss) {
        if (ss != null)
            for (int i = 0; i < ss.length; i++)
                ss[i] = ss[i].toUpperCase();
        return ss;
    }

    private <T> void seriesAttributesInStudy(List<Predicate> predicates, CriteriaQuery<T> q,
            Path<Study> study, Attributes keys, QueryParam queryParam, QueryRetrieveLevel2 queryRetrieveLevel,
            String... modalitiesInStudy) {
        Subquery<Series> sq = q.subquery(Series.class);
        Root<Series> series = sq.from(Series.class);
        List<Predicate> y = new ArrayList<>();
        anyOf(y, series.get(Series_.modality), toUpperCase(modalitiesInStudy), false);
        uidsPredicate(y, series.get(Series_.sopClassUID), keys.getStrings(Tag.SOPClassesInStudy));
        if (queryRetrieveLevel == QueryRetrieveLevel2.STUDY) {
            anyOf(y, series.get(Series_.institutionName),
                    keys.getStrings(Tag.InstitutionName), true);
            anyOf(y, series.get(Series_.institutionalDepartmentName),
                    keys.getStrings(Tag.InstitutionalDepartmentName), true);
            anyOf(y, series.get(Series_.stationName),
                    keys.getStrings(Tag.StationName), true);
            anyOf(y, series.get(Series_.seriesDescription),
                    keys.getStrings(Tag.SeriesDescription), true);
            anyOf(y, series.get(Series_.bodyPartExamined),
                    toUpperCase(keys.getStrings(Tag.BodyPartExamined)), false);
            anyOf(y, series.get(Series_.laterality),
                    toUpperCase(keys.getStrings(Tag.Laterality)), false);
            anyOf(y, series.get(Series_.sourceAET),
                    keys.getStrings(PrivateTag.PrivateCreator, PrivateTag.SendingApplicationEntityTitleOfSeries, VR.AE),
                    false);
            if (queryParam.isStorageVerificationFailed())
                y.add(cb.greaterThan(series.get(Series_.failuresOfLastStorageVerification), 0));
            if (queryParam.isMetadataUpdateFailed())
                y.add(cb.greaterThan(series.get(Series_.metadataUpdateFailures), 0));
            if (queryParam.isCompressionFailed())
                y.add(cb.greaterThan(series.get(Series_.compressionFailures), 0));
        }
        if (!y.isEmpty()) {
            y.add(cb.equal(series.get(Series_.study), study));
            predicates.add(cb.exists(sq.select(series).where(y.toArray(new Predicate[0]))));
        }
    }

    private boolean code(List<Predicate> predicates, Path<CodeEntity> code, Attributes item) {
        if (isUniversalMatching(item))
            return false;

        boolean result = wildCard(predicates, code.get(CodeEntity_.codeValue),
                item.getString(Tag.CodeValue, "*"));
        result = wildCard(predicates, code.get(CodeEntity_.codingSchemeDesignator),
                item.getString(Tag.CodingSchemeDesignator, "*")) || result;
        result = wildCard(predicates, code.get(CodeEntity_.codingSchemeVersion),
                item.getString(Tag.CodingSchemeVersion, "*")) || result;
        return result;
    }

    private <T, Z> void procedureCode(List<Predicate> predicates, CriteriaQuery<T> q,
            From<Z, Study> study, Attributes item) {
        if (isUniversalMatching(item))
            return;

        Subquery<CodeEntity> sq = q.subquery(CodeEntity.class);
        From<Z, Study> sqStudy = correlate(sq, study);
        CollectionJoin<Study, CodeEntity> code = sqStudy.join(Study_.procedureCodes);
        List<Predicate> y = new ArrayList<>();
        code(y, code, item);
        if (!y.isEmpty()) {
            predicates.add(cb.exists(sq.select(code).where(y.toArray(new Predicate[0]))));
        }
    }

    private <T, Z> void performerCode(List<Predicate> predicates, CriteriaQuery<T> q,
                                      From<Z, UPS> ups, Attributes item) {
        if (isUniversalMatching(item))
            return;

        Subquery<CodeEntity> sq = q.subquery(CodeEntity.class);
        From<Z, UPS> sqUPS = correlate(sq, ups);
        CollectionJoin<UPS, CodeEntity> code = sqUPS.join(UPS_.humanPerformerCodes);
        List<Predicate> y = new ArrayList<>();
        code(y, code, item);
        if (!y.isEmpty())
            predicates.add(cb.exists(sq.select(code).where(y.toArray(new Predicate[0]))));
    }

    private <T, Z> void notSubscribedBy(List<Predicate> predicates, CriteriaQuery<T> q,
            From<Z, UPS> ups, String subscriberAET) {
        if (subscriberAET == null)
            return;

        Subquery<Subscription> sq = q.subquery(Subscription.class);
        From<Z, UPS> sqUPS = correlate(sq, ups);
        CollectionJoin<UPS, Subscription> sub = sqUPS.join(UPS_.subscriptions);
        predicates.add(cb.exists(
                sq.select(sub).where(cb.equal(sub.get(Subscription_.subscriberAET), subscriberAET)))
                .not());
    }

    private <T, Z> void requestAttributes(List<Predicate> predicates, CriteriaQuery<T> q, From<Z, Series> series,
            Attributes item, QueryParam queryParam) {
        if (isUniversalMatching(item))
            return;

        Subquery<SeriesRequestAttributes> sq = q.subquery(SeriesRequestAttributes.class);
        From<Z, Series> sqSeries = correlate(sq, series);
        Join<Series, SeriesRequestAttributes> request = sqSeries.join(Series_.requestAttributes);
        List<Predicate> requestPredicates = new ArrayList<>();
        String accNo = item.getString(Tag.AccessionNumber, "*");
        if (!isUniversalMatching(accNo)) {
            Issuer issuerOfAccessionNumber = Issuer.valueOf(item
                    .getNestedDataset(Tag.IssuerOfAccessionNumberSequence));
            if (issuerOfAccessionNumber == null)
                issuerOfAccessionNumber = queryParam.getDefaultIssuerOfAccessionNumber();
            idWithIssuer(requestPredicates, request,
                    SeriesRequestAttributes_.accessionNumber, SeriesRequestAttributes_.issuerOfAccessionNumber,
                    accNo, issuerOfAccessionNumber);
        }
        anyOf(requestPredicates,
                request.get(SeriesRequestAttributes_.requestingService),
                item.getStrings(Tag.RequestingService), true);
        personName(requestPredicates, q, request, SeriesRequestAttributes_.requestingPhysician,
                item.getString(Tag.RequestingPhysician, "*"), queryParam);
        anyOf(requestPredicates,
                request.get(SeriesRequestAttributes_.requestedProcedureID),
                item.getStrings(Tag.RequestedProcedureID), false);
        uidsPredicate(requestPredicates, request.get(SeriesRequestAttributes_.studyInstanceUID),
                item.getStrings(Tag.StudyInstanceUID));
        anyOf(requestPredicates,
                request.get(SeriesRequestAttributes_.scheduledProcedureStepID),
                item.getStrings(Tag.ScheduledProcedureStepID), false);
        if (!requestPredicates.isEmpty()) {
            predicates.add(cb.exists(sq.select(request).where(requestPredicates.toArray(new Predicate[0]))));
        }
    }

    private <T, Z> void upsRequestAttributes(List<Predicate> predicates, CriteriaQuery<T> q, From<Z, UPS> ups,
                                          Attributes item, QueryParam queryParam) {
        if (isUniversalMatching(item))
            return;

        Subquery<UPSRequest> sq = q.subquery(UPSRequest.class);
        From<Z, UPS> sqUPS = correlate(sq, ups);
        Join<UPS, UPSRequest> request = sqUPS.join(UPS_.referencedRequests);
        List<Predicate> requestPredicates = new ArrayList<>();
        String accNo = item.getString(Tag.AccessionNumber, "*");
        if (!isUniversalMatching(accNo)) {
            Issuer issuerOfAccessionNumber = Issuer.valueOf(item
                    .getNestedDataset(Tag.IssuerOfAccessionNumberSequence));
            if (issuerOfAccessionNumber == null)
                issuerOfAccessionNumber = queryParam.getDefaultIssuerOfAccessionNumber();
            idWithIssuer(requestPredicates, request,
                    UPSRequest_.accessionNumber, UPSRequest_.issuerOfAccessionNumber,
                    accNo, issuerOfAccessionNumber);
        }
        anyOf(requestPredicates,
                request.get(UPSRequest_.requestingService),
                item.getStrings(Tag.RequestingService), true);
        personName(requestPredicates, q, request, UPSRequest_.requestingPhysician,
                item.getString(Tag.RequestingPhysician, "*"), queryParam);
        anyOf(requestPredicates,
                request.get(UPSRequest_.requestedProcedureID),
                item.getStrings(Tag.RequestedProcedureID), false);
        uidsPredicate(requestPredicates, request.get(UPSRequest_.studyInstanceUID),
                item.getStrings(Tag.StudyInstanceUID));
        if (!requestPredicates.isEmpty())
            predicates.add(cb.exists(sq.select(request).where(requestPredicates.toArray(new Predicate[0]))));
    }

    private static <T, Z, X> From<Z, X> correlate(Subquery<T> sq, From<Z, X> parent) {
        return parent instanceof Root ? sq.correlate((Root) parent) : sq.correlate((Join) parent);
    }

    private <T> void verifyingObserver(List<Predicate> predicates, CriteriaQuery<T> q,
            Root<Instance> instance, Attributes item, QueryParam queryParam) {
        if (isUniversalMatching(item))
            return;

        Subquery<VerifyingObserver> sq = q.subquery(VerifyingObserver.class);
        Root<Instance> sqInstance = sq.correlate(instance);
        Join<Instance, VerifyingObserver> observer = sqInstance.join(Instance_.verifyingObservers);
        List<Predicate> y = new ArrayList<>();
        personName(y, q, observer, VerifyingObserver_.verifyingObserverName,
                item.getString(Tag.VerifyingObserverName, "*"), queryParam);
        dateRange(y, observer.get(VerifyingObserver_.verificationDateTime),
                item.getDateRange(Tag.VerificationDateTime), FormatDate.DT);
        if (!y.isEmpty()) {
            predicates.add(cb.exists(sq.select(observer).where(y.toArray(new Predicate[0]))));
        }
    }

    private <T> void contentItem(List<Predicate> predicates, CriteriaQuery<T> q,
            Root<Instance> instance, Attributes item) {
        String valueType = item.getString(Tag.ValueType);
        if (!("CODE".equals(valueType) || "TEXT".equals(valueType)))
            return;

        Subquery<ContentItem> sq = q.subquery(ContentItem.class);
        Root<Instance> sqInstance = sq.correlate(instance);
        CollectionJoin<Instance, ContentItem> contentItem = sqInstance.join(contentItems);
        List<Predicate> y = new ArrayList<>();
        code(y, contentItem.get(ContentItem_.conceptName),
                item.getNestedDataset(Tag.ConceptNameCodeSequence));
        wildCard(y, contentItem.get(ContentItem_.relationshipType),
                item.getString(Tag.RelationshipType, "*").toUpperCase());
        code(y, contentItem.get(ContentItem_.conceptCode),
                item.getNestedDataset(Tag.ConceptCodeSequence));
        wildCard(y, contentItem.get(ContentItem_.textValue),
                item.getString(Tag.TextValue, "*"), true);
        if (!y.isEmpty()) {
            predicates.add(cb.exists(sq.select(contentItem).where(y.toArray(new Predicate[0]))));
        }
    }

    private <T, Z, X> void personName(List<Predicate> predicates, CriteriaQuery<T> q, From<Z, X> patient,
            SingularAttribute<X, org.dcm4chee.arc.entity.PersonName> attribute, String value,
            QueryParam queryParam) {
        if (!isUniversalMatching(value)) {
            Path<org.dcm4chee.arc.entity.PersonName> qpn = patient.join(attribute);
            PersonName pn = new PersonName(value, true);
            if (queryParam.isFuzzySemanticMatching())
                fuzzyMatch(predicates, q, qpn, pn, queryParam);
            else
                literalMatch(predicates, qpn, pn, queryParam);
        }
    }

    private void literalMatch(List<Predicate> predicates, Path<org.dcm4chee.arc.entity.PersonName> qpn,
            PersonName pn, QueryParam param) {
        if (!pn.contains(PersonName.Group.Ideographic)
                && !pn.contains(PersonName.Group.Phonetic)) {
            predicates.add(
                cb.or(
                    match(
                        qpn.get(PersonName_.familyName),
                        qpn.get(PersonName_.givenName),
                        qpn.get(PersonName_.middleName),
                        pn, PersonName.Group.Alphabetic, true),
                    match(
                        qpn.get(PersonName_.ideographicFamilyName),
                        qpn.get(PersonName_.ideographicGivenName),
                        qpn.get(PersonName_.ideographicMiddleName),
                        pn, PersonName.Group.Alphabetic, true),
                    match(
                        qpn.get(PersonName_.phoneticFamilyName),
                        qpn.get(PersonName_.phoneticGivenName),
                        qpn.get(PersonName_.phoneticMiddleName),
                        pn, PersonName.Group.Alphabetic, true)
                )
            );
        } else {
            if (pn.contains(PersonName.Group.Alphabetic))
                match(predicates,
                    qpn.get(PersonName_.familyName),
                    qpn.get(PersonName_.givenName),
                    qpn.get(PersonName_.middleName),
                    pn, PersonName.Group.Alphabetic, true);
            if (pn.contains(PersonName.Group.Ideographic))
                match(predicates,
                    qpn.get(PersonName_.ideographicFamilyName),
                    qpn.get(PersonName_.ideographicGivenName),
                    qpn.get(PersonName_.ideographicMiddleName),
                    pn, PersonName.Group.Ideographic, false);
            if (pn.contains(PersonName.Group.Phonetic))
                match(predicates,
                    qpn.get(PersonName_.phoneticFamilyName),
                    qpn.get(PersonName_.phoneticGivenName),
                    qpn.get(PersonName_.phoneticMiddleName),
                    pn, PersonName.Group.Phonetic, false);
        }
    }

    private Predicate match(Path<String> familyName, Path<String> givenName, Path<String> middleName,
            PersonName pn, PersonName.Group group, boolean ignoreCase) {
        List<Predicate> x = new ArrayList<>(3);
        match(x, familyName, givenName, middleName, pn, group, ignoreCase);
        return cb.and(x.toArray(new Predicate[0]));
    }

    private void match(List<Predicate> predicates, Path<String> familyName, Path<String> givenName, Path<String> middleName,
            PersonName pn, PersonName.Group group, boolean ignoreCase) {
        wildCard(predicates, familyName, pn.get(group, PersonName.Component.FamilyName), ignoreCase);
        wildCard(predicates, givenName, pn.get(group, PersonName.Component.GivenName), ignoreCase);
        wildCard(predicates, middleName, pn.get(group, PersonName.Component.MiddleName), ignoreCase);
    }

    private <T> void fuzzyMatch(List<Predicate> predicates, CriteriaQuery<T> q, Path<org.dcm4chee.arc.entity.PersonName> qpn,
            PersonName pn, QueryParam param) {
        fuzzyMatch(predicates, q, qpn, pn, PersonName.Component.FamilyName, param);
        fuzzyMatch(predicates, q, qpn, pn, PersonName.Component.GivenName, param);
        fuzzyMatch(predicates, q, qpn, pn, PersonName.Component.MiddleName, param);
    }

    private <T> void fuzzyMatch(List<Predicate> predicates, CriteriaQuery<T> q,
            Path<org.dcm4chee.arc.entity.PersonName> qpn, PersonName pn, PersonName.Component c, QueryParam param) {
        String name = pn.get(c);
        if (isUniversalMatching(name))
            return;

        Iterator<String> parts = SoundexCode.tokenizePersonNameComponent(name);
        for (int i = 0; parts.hasNext(); ++i)
            fuzzyMatch(predicates, q, qpn, c, i, parts.next(), param);
    }

    private <T> void fuzzyMatch(List<Predicate> predicates, CriteriaQuery<T> q,
            Path<org.dcm4chee.arc.entity.PersonName> qpn, PersonName.Component c, int partIndex, String name,
            QueryParam param) {
        boolean wc = name.endsWith("*");
        if (wc) {
            name = name.substring(0, name.length()-1);
            if (name.isEmpty())
                return;
        }
        FuzzyStr fuzzyStr = param.getFuzzyStr();
        String fuzzyName = fuzzyStr.toFuzzy(name);
        if (fuzzyName.isEmpty())
            if (wc)
                return;
            else // code "" is stored as "*"
                fuzzyName = "*";

        Subquery<SoundexCode> sq = q.subquery(SoundexCode.class);
        Root<SoundexCode> soundexCode = sq.from(SoundexCode.class);
        Predicate y = cb.and(cb.equal(soundexCode.get(SoundexCode_.personName), qpn),
                wc ? cb.like(soundexCode.get(SoundexCode_.codeValue), fuzzyName + '%')
                   : cb.equal(soundexCode.get(SoundexCode_.codeValue), fuzzyName));
        if (!param.isPersonNameComponentOrderInsensitiveMatching()) {
            y = cb.and(y, cb.and(
                    cb.equal(soundexCode.get(SoundexCode_.personNameComponent), c),
                    cb.equal(soundexCode.get(SoundexCode_.componentPartIndex), partIndex)));
        }
        predicates.add(cb.exists(sq.select(soundexCode).where(y)));
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

    private void dateRange(List<Predicate> predicates, Path<String> path, String s, FormatDate dt) {
        dateRange(predicates, path, parseDateRange(s), dt);
    }

    private void dateRange(List<Predicate> predicates, Path<String> path, DateRange range, FormatDate dt) {
        if (!isUniversalMatching(range)) {
            predicates.add(dateRange(path, range, dt));
            predicates.add(cb.notEqual(path, "*"));
        }
    }

    private Predicate dateRange(Path<String> path, DateRange range, FormatDate dt) {
        String start = format(range.getStartDate(), dt);
        String end = format(range.getEndDate(), dt);
        return start == null ? cb.lessThanOrEqualTo(path, end)
                : end == null ? cb.greaterThanOrEqualTo(path, start)
                : start.equals(end) ? cb.equal(path, start)
                : (dt.equals(FormatDate.TM) && range.isStartDateExeedsEndDate())
                ? cb.or(cb.between(path, start, "115959.999"), cb.between(path, "000000.000", end))
                : cb.between(path, start, end);
    }

    void dateRange(List<Predicate> predicates, Path<Date> path, String s) {
        DateRange range = parseDateRange(s);
        dateRange(predicates, path, range);
    }

    private void dateRange(List<Predicate> x, Path<Date> path, DateRange range) {
        if (!isUniversalMatching(range))
            x.add(dateRange(path, range));
    }

    private Predicate dateRange(Path<Date> path, DateRange range) {
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

    private void dateRange(List<Predicate> predicates, Path<String> datePath, Path<String> timePath,
            int dateTag, int timeTag, long dateAndTimeTag, Attributes keys, boolean combinedDatetimeMatching) {
        DateRange dateRange = keys.getDateRange(dateTag, null);
        DateRange timeRange = keys.getDateRange(timeTag, null);
        if (combinedDatetimeMatching && !isUniversalMatching(dateRange) && !isUniversalMatching(timeRange)) {
            predicates.add(combinedRange(datePath, timePath, keys.getDateRange(dateAndTimeTag, null)));
            predicates.add(cb.notEqual(datePath, "*"));
        } else {
            dateRange(predicates, datePath, dateRange, FormatDate.DA);
            dateRange(predicates, timePath, timeRange, FormatDate.TM);
        }
    }

    private Predicate combinedRange(Path<String> datePath, Path<String> timePath, DateRange dateRange) {
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

    private Predicate combinedRangeInterval(Path<String> datePath, Path<String> timePath,
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

    private Predicate combinedRangeEnd(Path<String> datePath, Path<String> timePath,
            String endDate, String endTime) {
        return cb.or(
                cb.lessThan(datePath, endDate),
                cb.and(
                        cb.equal(datePath, endDate),
                        cb.or(
                                cb.lessThanOrEqualTo(timePath, endTime),
                                cb.equal(timePath, "*"))));
    }

    private Predicate combinedRangeStart(Path<String> datePath, Path<String> timePath,
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

    public static long unbox(Long value, long defaultValue) {
        return value != null ? value.longValue() : defaultValue;
    }
}
