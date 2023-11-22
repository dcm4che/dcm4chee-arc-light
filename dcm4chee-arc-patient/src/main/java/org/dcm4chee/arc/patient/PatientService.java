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
 * Portions created by the Initial Developer are Copyright (C) 2015-2017
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

package org.dcm4chee.arc.patient;

import jakarta.persistence.criteria.CriteriaQuery;
import org.dcm4che3.data.*;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4che3.util.AttributesFormat;
import org.dcm4chee.arc.conf.AttributeFilter;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.entity.PatientID;
import org.dcm4chee.arc.entity.Study;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;

import java.net.Socket;
import java.util.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jul 2015
 */
public interface PatientService {

    static Attributes exportPatientIDsWithIssuer(Collection<PatientID> patientIDs) {
        Attributes attrs = new Attributes(3);
        attrs.setNull(Tag.IssuerOfPatientID, VR.LO);
        Iterator<PatientID> iter = patientIDs.iterator();
        iter.next().getIDWithIssuer().exportPatientIDWithIssuer(attrs);
        Sequence otherPatientIDsSequence = attrs.newSequence(Tag.OtherPatientIDsSequence, patientIDs.size() - 1);
        while (iter.hasNext()) {
            otherPatientIDsSequence.add(iter.next().getIDWithIssuer().exportPatientIDWithIssuer(null));
        }
        return attrs;
    }

    PatientMgtContext createPatientMgtContextDIMSE(Association as);

    PatientMgtContext createPatientMgtContextWEB(HttpServletRequestInfo httpRequest);

    PatientMgtContext createPatientMgtContextHL7(HL7Application hl7App, Connection conn, Socket socket, UnparsedHL7Message msg);

    PatientMgtContext createPatientMgtContextScheduler();

    Collection<Patient> findPatients(Collection<IDWithIssuer> pids);

    Patient findPatient(Collection<IDWithIssuer> pids);

    Patient createPatient(PatientMgtContext ctx);

    Patient updatePatient(PatientMgtContext ctx)
            throws NonUniquePatientException, PatientMergedException;

    void updatePatientIDs(Patient pat, Collection<IDWithIssuer> patientIDs);

    boolean updatePatientAttrs(Attributes attrs, Attributes.UpdatePolicy updatePolicy,
                               Attributes newAttrs, Attributes modified, AttributeFilter filter);

    boolean deleteDuplicateCreatedPatient(Collection<IDWithIssuer> pids, Patient patient, Study createdStudy);

    Patient mergePatient(PatientMgtContext ctx)
            throws NonUniquePatientException, PatientMergedException, CircularPatientMergeException;

    Patient changePatientID(PatientMgtContext ctx)
            throws NonUniquePatientException, PatientMergedException, PatientTrackingNotAllowedException;

    boolean unmergePatient(PatientMgtContext ctx)
            throws NonUniquePatientException, PatientUnmergedException;

    Patient findPatient(PatientMgtContext ctx);

    void deletePatient(PatientMgtContext ctx);

    Patient updatePatientStatus(PatientMgtContext ctx);

    List<String> studyInstanceUIDsOf(Patient patient);

    boolean supplementIssuer(PatientMgtContext ctx, PatientID patientID, IDWithIssuer idWithIssuer,
            Map<IDWithIssuer, Long> ambiguous);

    <T> T merge(T entity);

    void testSupplementIssuers(CriteriaQuery<PatientID> query, int fetchSize,
                               Set<IDWithIssuer> success, Map<IDWithIssuer, Long> ambiguous, AttributesFormat issuer);

    <T> List<T> queryWithOffsetAndLimit(CriteriaQuery<T> query, int offset, int limit);
}
