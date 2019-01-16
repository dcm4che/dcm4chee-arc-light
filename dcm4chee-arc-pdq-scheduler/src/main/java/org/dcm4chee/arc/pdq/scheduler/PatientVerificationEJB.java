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

package org.dcm4chee.arc.pdq.scheduler;

import org.dcm4chee.arc.entity.Patient;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Date;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2018
 */
@Stateless
public class PatientVerificationEJB {

    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    public List<Patient.IDWithPkAndVerificationStatus> findByVerificationStatus(
            Patient.VerificationStatus status, int limit) {
        return em.createNamedQuery(
                Patient.FIND_BY_VERIFICATION_STATUS, Patient.IDWithPkAndVerificationStatus.class)
                .setParameter(1, status)
                .setMaxResults(limit)
                .getResultList();
    }

    public List<Patient.IDWithPkAndVerificationStatus> findByVerificationStatusAndTime(
            Patient.VerificationStatus status, Date verifiedBefore, int limit) {
        return em.createNamedQuery(
                Patient.FIND_BY_VERIFICATION_STATUS_AND_TIME, Patient.IDWithPkAndVerificationStatus.class)
                .setParameter(1, status)
                .setParameter(2, verifiedBefore)
                .setMaxResults(limit)
                .getResultList();
    }

    public List<Patient.IDWithPkAndVerificationStatus> findByVerificationStatusAndTimeAndRetries(
            Patient.VerificationStatus status, Date verifiedBefore, int maxRetries, int limit) {
        return maxRetries < 0 ? findByVerificationStatusAndTime(status, verifiedBefore, limit)
                : em.createNamedQuery(
                Patient.FIND_BY_VERIFICATION_STATUS_AND_TIME_AND_MAX_RETRIES, Patient.IDWithPkAndVerificationStatus.class)
                .setParameter(1, status)
                .setParameter(2, verifiedBefore)
                .setParameter(3, maxRetries)
                .setMaxResults(limit)
                .getResultList();
    }

    public boolean claimPatientVerification(Patient.IDWithPkAndVerificationStatus patient) {
        return em.createNamedQuery(Patient.CLAIM_PATIENT_VERIFICATION)
                .setParameter(1, patient.pk)
                .setParameter(2, patient.verificationStatus)
                .setParameter(3, Patient.VerificationStatus.IN_PROCESS)
                .executeUpdate() > 0;
    }
}
