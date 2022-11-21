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

package org.dcm4chee.arc.pdq.db;

import jakarta.persistence.EntityManager;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4chee.arc.conf.Entity;
import org.dcm4chee.arc.conf.PDQServiceDescriptor;
import org.dcm4chee.arc.entity.PatientDemographics;
import org.dcm4chee.arc.pdq.AbstractPDQService;
import org.dcm4chee.arc.pdq.PDQServiceContext;
import org.dcm4chee.arc.pdq.PDQServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Oct 2021
 */
public class DBTablePDQService extends AbstractPDQService {
    private static final Logger LOG = LoggerFactory.getLogger(DBTablePDQService.class);
    private final EntityManager em;

    public DBTablePDQService(PDQServiceDescriptor descriptor, EntityManager em) {
        super(descriptor);
        this.em = em;
    }

    @Override
    public Attributes query(PDQServiceContext ctx) throws PDQServiceException {
        requireQueryEntity(Entity.Patient);
        IDWithIssuer patientID = ctx.getPatientID();
        List<PatientDemographics> patientDemographics = em.createNamedQuery(PatientDemographics.FIND_BY_PATIENT_ID,
                                                                            PatientDemographics.class)
                                                            .setParameter(1, toString(patientID))
                                                            .getResultList();
        switch (patientDemographics.size()) {
            case 0:
                LOG.info("No Patient Demographics found for {}", patientID);
                return null;
            case 1:
                return demographics(patientID, patientDemographics.get(0));
            default:
                throw new PDQServiceException("Patient ID '" + patientID + "' not unique at " + descriptor);
        }
    }

    private String toString(IDWithIssuer patientID) {
        if (patientID.getIssuer() == null)
            return patientID + "%";

        return patientID.toString();
    }

    private Attributes demographics(IDWithIssuer patientID, PatientDemographics pd) {
        Attributes attrs = patientID.exportPatientIDWithIssuer(null);
        attrs.setString(Tag.PatientName, VR.PN, pd.getName());
        attrs.setString(Tag.PatientBirthDate, VR.DA, pd.getBirthDate());
        attrs.setString(Tag.PatientSex, VR.CS, pd.getSex());
        return attrs;
    }

}
