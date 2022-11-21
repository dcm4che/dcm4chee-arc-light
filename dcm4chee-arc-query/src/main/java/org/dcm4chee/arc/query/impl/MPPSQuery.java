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
 * Portions created by the Initial Developer are Copyright (C) 2015-2019
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

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.*;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.dict.archive.PrivateTag;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.query.util.QueryBuilder;

import java.util.List;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Mar 2021
 */
public class MPPSQuery extends AbstractQuery {

    private Join<MPPS, Patient> patient;
    private Root<MPPS> mpps;
    private Path<byte[]> patientAttrBlob;
    private Path<byte[]> mppsAttrBlob;

    public MPPSQuery(QueryContext context, EntityManager em) {
        super(context, em);
    }

    @Override
    protected CriteriaQuery<Tuple> multiselect() {
        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        this.mpps = q.from(MPPS.class);
        this.patient = mpps.join(MPPS_.patient);
        return order(restrict(q, patient, mpps)).multiselect(
                mpps.get(MPPS_.pk),
                patient.get(Patient_.numberOfStudies),
                patient.get(Patient_.createdTime),
                patient.get(Patient_.updatedTime),
                patient.get(Patient_.verificationTime),
                patient.get(Patient_.verificationStatus),
                patient.get(Patient_.failedVerifications),
                mpps.get(MPPS_.createdTime),
                mpps.get(MPPS_.updatedTime),
                mpps.get(MPPS_.performedProcedureStepStartDate),
                mpps.get(MPPS_.performedProcedureStepStartTime),
                mpps.get(MPPS_.status),
                patientAttrBlob = patient.join(Patient_.attributesBlob).get(AttributesBlob_.encodedAttributes),
                mppsAttrBlob = mpps.join(MPPS_.attributesBlob).get(AttributesBlob_.encodedAttributes)
        );
    }

    @Override
    protected CriteriaQuery<Long> count() {
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        Root<MPPS> mpps = q.from(MPPS.class);
        return createQuery(q, mpps, cb.count(mpps));
    }

    @Override
    protected Attributes toAttributes(Tuple results) {
        Attributes mppsAttrs = AttributesBlob.decodeAttributes(results.get(mppsAttrBlob), null);
        Attributes patAttrs = AttributesBlob.decodeAttributes(results.get(patientAttrBlob), null);
        Attributes.unifyCharacterSets(patAttrs, mppsAttrs);
        Attributes attrs = new Attributes(patAttrs.size() + mppsAttrs.size() + 20);
        attrs.addAll(patAttrs);
        attrs.addAll(mppsAttrs, true);
        PatientQuery.addPatientQRAttrs(patient, context, results, attrs);
        addMPPSQRAttrs(mpps, context, results, attrs);
        return attrs;
    }

    @Override
    public boolean isOptionalKeysNotSupported() {
        //TODO
        return false;
    }

    private CriteriaQuery<Long> createQuery(
            CriteriaQuery<Long> q, Root<MPPS> mpps, Expression<Long> longExpression, Predicate... extra) {
        boolean hasPatientLevelPredicates = QueryBuilder.hasPatientLevelPredicates(
                context.getPatientIDs(),
                context.getIssuerOfPatientID(),
                context.getQueryKeys(),
                context.getQueryParam());
        Join<MPPS, Patient> patient = null;
        if (hasPatientLevelPredicates) {
            patient = mpps.join(MPPS_.patient);
        }
        return restrict(q, patient, mpps, extra).select(longExpression);
    }

    private CriteriaQuery<Tuple> order(CriteriaQuery<Tuple> q) {
        if (context.getOrderByTags() != null)
            q.orderBy(builder.orderMPPS(patient, mpps, context.getOrderByTags()));
        return q;
    }

    private <T> CriteriaQuery<T> restrict(CriteriaQuery<T> q, Join<MPPS, Patient> patient, Root<MPPS> mpps,
                                          Predicate... extra) {
        List<Predicate> predicates = builder.mppsPredicates(q, patient, mpps,
                context.getPatientIDs(),
                context.getIssuerOfPatientID(),
                context.getQueryKeys(),
                context.getQueryParam());
        for (Predicate predicate : extra)
            predicates.add(predicate);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));
        return q;
    }

    private void addMPPSQRAttrs(Path<MPPS> mpps, QueryContext context, Tuple results, Attributes attrs) {
        if (!context.isReturnPrivate())
            return;

        setDTwTZ(attrs, PrivateTag.MPPSCreateDateTime,
                results.get(mpps.get(MPPS_.createdTime)));
        setDTwTZ(attrs, PrivateTag.MPPSUpdateDateTime,
                results.get(mpps.get(MPPS_.updatedTime)));
    }
}
