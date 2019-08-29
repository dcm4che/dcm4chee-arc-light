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
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.query.QueryContext;

import javax.persistence.EntityManager;
import javax.persistence.Tuple;
import javax.persistence.criteria.*;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2015
 */
class PatientQuery extends AbstractQuery {

    private Root<Patient> patient;
    private Path<byte[]> patientAttrBlob;

    PatientQuery(QueryContext context, EntityManager em) {
        super(context, em);
    }

    @Override
    protected CriteriaQuery<Tuple> multiselect() {
        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        this.patient = q.from(Patient.class);
        return order(restrict(q, patient)).multiselect(
                patient.get(Patient_.pk),
                patient.get(Patient_.numberOfStudies),
                patient.get(Patient_.createdTime),
                patient.get(Patient_.updatedTime),
                patient.get(Patient_.verificationTime),
                patient.get(Patient_.verificationStatus),
                patient.get(Patient_.failedVerifications),
                patientAttrBlob = patient.join(Patient_.attributesBlob).get(AttributesBlob_.encodedAttributes));
    }

    @Override
    protected CriteriaQuery<Long> count() {
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        Root<Patient> patient = q.from(Patient.class);
        return restrict(q, patient).select(cb.count(patient));
    }

    @Override
    protected Attributes toAttributes(Tuple results) {
        Attributes patAttrs = AttributesBlob.decodeAttributes(results.get(patientAttrBlob), null);
        addPatientQRAttrs(patient, context, results, patAttrs);
        return patAttrs;
    }

    private CriteriaQuery<Tuple> order(CriteriaQuery<Tuple> q) {
        if (context.getOrderByTags() != null) {
            q.orderBy(builder.orderPatients(patient, context.getOrderByTags()));
        }
        return q;
    }

    private <T> CriteriaQuery<T> restrict(CriteriaQuery<T> q, Root<Patient> patient) {
        List<Predicate> predicates = builder.patientPredicates(q, patient,
                context.getPatientIDs(),
                context.getQueryKeys(),
                context.getQueryParam());
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));
        return q;
    }

    static void addPatientQRAttrs(Path<Patient> patient, QueryContext context, Tuple results, Attributes attrs) {
        attrs.setInt(Tag.NumberOfPatientRelatedStudies, VR.IS,
                results.get(patient.get(Patient_.numberOfStudies)));
        if (!context.isReturnPrivate())
            return;

        attrs.setDate(PrivateTag.PrivateCreator, PrivateTag.PatientCreateDateTime, VR.DT,
                results.get(patient.get(Patient_.createdTime)));
        attrs.setDate(PrivateTag.PrivateCreator, PrivateTag.PatientUpdateDateTime, VR.DT,
                results.get(patient.get(Patient_.updatedTime)));
        Date verificationTime = results.get(patient.get(Patient_.verificationTime));
        if (verificationTime != null) {
            attrs.setDate(PrivateTag.PrivateCreator, PrivateTag.PatientVerificationDateTime, VR.DT,
                    verificationTime);
        }
        Patient.VerificationStatus verificationStatus = results.get(patient.get(Patient_.verificationStatus));
        if (verificationStatus != Patient.VerificationStatus.UNVERIFIED || verificationTime != null) {
            attrs.setString(PrivateTag.PrivateCreator, PrivateTag.PatientVerificationStatus, VR.CS,
                    verificationStatus.name());
        }
        int failures = results.get(patient.get(Patient_.failedVerifications));
        if (failures > 0) {
            attrs.setInt(PrivateTag.PrivateCreator, PrivateTag.FailedVerificationsOfPatient, VR.US, failures);
        }
    }

    @Override
    public boolean isOptionalKeysNotSupported() {
        //TODO
        return false;
    }
}
