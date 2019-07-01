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

import org.dcm4che3.audit.AuditMessages;
import org.dcm4che3.data.Attributes;
import org.dcm4chee.arc.Scheduler;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.Duration;
import org.dcm4chee.arc.conf.PDQServiceDescriptor;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.patient.PatientService;
import org.dcm4chee.arc.pdq.PDQService;
import org.dcm4chee.arc.pdq.PDQServiceException;
import org.dcm4chee.arc.pdq.PDQServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Date;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2018
 */
@ApplicationScoped
public class PatientVerificationScheduler extends Scheduler {

    private static final Logger LOG = LoggerFactory.getLogger(PatientVerificationScheduler.class);

    @Inject
    private PDQServiceFactory serviceFactory;

    @Inject
    private PatientService patientService;

    @Inject
    private PatientVerificationEJB ejb;

    protected PatientVerificationScheduler() {
        super(Mode.scheduleWithFixedDelay);
    }

    @Override
    protected Logger log() {
        return LOG;
    }

    @Override
    protected Duration getPollingInterval() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        return arcDev != null && arcDev.getPatientVerificationPDQServiceID() != null
                ? arcDev.getPatientVerificationPollingInterval()
                : null;
    }

    @Override
    protected void execute() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        String serviceID = arcDev.getPatientVerificationPDQServiceID();
        PDQServiceDescriptor descriptor = arcDev.getPDQServiceDescriptor(serviceID);
        if (descriptor == null) {
            LOG.warn("No such PDQ Service: {}", serviceID);
            return;
        }

        PDQService pdqService;
        try {
            pdqService = serviceFactory.getPDQService(descriptor);
        } catch (Exception e) {
            LOG.warn("Failed to initialize {}:\n", descriptor, e);
            return;
        }

        int fetchSize;
        boolean adjustIssuerOfPatientID = arcDev.isPatientVerificationAdjustIssuerOfPatientID();
        while (arcDev.getPatientVerificationPollingInterval() != null
                && verifyPatients(pdqService,
                ejb.findByVerificationStatus(Patient.VerificationStatus.UNVERIFIED,
                    fetchSize = arcDev.getPatientVerificationFetchSize()),
                fetchSize, adjustIssuerOfPatientID));
        Period period;
        while (arcDev.getPatientVerificationPollingInterval() != null
            && (period = arcDev.getPatientVerificationPeriod()) != null
            && verifyPatients(pdqService,
                ejb.findByVerificationStatusAndTime(Patient.VerificationStatus.VERIFIED,
                        Timestamp.valueOf(LocalDateTime.now().minus(period)),
                        fetchSize = arcDev.getPatientVerificationFetchSize()),
                fetchSize, adjustIssuerOfPatientID));
        while (arcDev.getPatientVerificationPollingInterval() != null
            && (period = arcDev.getPatientVerificationPeriodOnNotFound()) != null
            && verifyPatients(pdqService,
                ejb.findByVerificationStatusAndTime(Patient.VerificationStatus.NOT_FOUND,
                        Timestamp.valueOf(LocalDateTime.now().minus(period)),
                        fetchSize = arcDev.getPatientVerificationFetchSize()),
                fetchSize, adjustIssuerOfPatientID));
        Duration interval;
        int maxRetries;
        while(arcDev.getPatientVerificationPollingInterval() != null
            && (interval = arcDev.getPatientVerificationRetryInterval()) != null
            && (maxRetries = arcDev.getPatientVerificationMaxRetries()) != 0
            && verifyPatients(pdqService,
                ejb.findByVerificationStatusAndTimeAndRetries(Patient.VerificationStatus.VERIFICATION_FAILED,
                    new Date(System.currentTimeMillis() - interval.getSeconds() * 1000L),
                    maxRetries,
                    fetchSize = arcDev.getPatientVerificationFetchSize()),
                fetchSize, adjustIssuerOfPatientID));
    }

    private boolean verifyPatients(PDQService pdqService, List<Patient.IDWithPkAndVerificationStatus> patients, int fetchSize,
                                   boolean adjustIssuerOfPatientID) {
        for (Patient.IDWithPkAndVerificationStatus patient : patients) {
            try {
                if (ejb.claimPatientVerification(patient))
                    verifyPatient(pdqService, patient, adjustIssuerOfPatientID);
            } catch (Exception e) {
                LOG.warn("Verification of {} failed:\n", patient, e);
            }
        }
        return patients.size() == fetchSize;
    }

    private void verifyPatient(PDQService pdqService, Patient.IDWithPkAndVerificationStatus patient,
                               boolean adjustIssuerOfPatientID) {
        PatientMgtContext ctx = patientService.createPatientMgtContextScheduler();
        ctx.setPatientID(patient.idWithIssuer);
        ctx.setPDQServiceURI(pdqService.getPDQServiceDescriptor().getPDQServiceURI().toString());
        Attributes attrs;
        try {
            attrs = pdqService.query(adjustIssuerOfPatientID
                    ? patient.idWithIssuer.withoutIssuer()
                    : patient.idWithIssuer);
        } catch (PDQServiceException e) {
            ctx.setPatientVerificationStatus(Patient.VerificationStatus.VERIFICATION_FAILED);
            patientService.updatePatientStatus(ctx);
            LOG.info("Verification of {} failed against {}\n:", patient, pdqService.getPDQServiceDescriptor(), e);
            return;
        }
        if (attrs == null) {
            ctx.setPatientVerificationStatus(Patient.VerificationStatus.NOT_FOUND);
            patientService.updatePatientStatus(ctx);
            LOG.info("{} not found at {} - no verification", patient, pdqService.getPDQServiceDescriptor());
            return;
        }
        ctx.setAttributes(attrs);
        ctx.setPatientVerificationStatus(Patient.VerificationStatus.VERIFIED);
        if (adjustIssuerOfPatientID && !ctx.getPatientID().equals(patient.idWithIssuer)) {
            ctx.setPreviousAttributes(patient.idWithIssuer.exportPatientIDWithIssuer(null));
            patientService.changePatientID(ctx);
            LOG.info("Updated {} on verification against {}",
                    patient,
                    pdqService.getPDQServiceDescriptor());
        } else {
            patientService.updatePatient(ctx);
            LOG.info(ctx.getEventActionCode().equals(AuditMessages.EventActionCode.Update)
                            ? "Updated {} on verification against {}"
                            : "Verified {} against {}",
                    patient,
                    pdqService.getPDQServiceDescriptor());
        }
    }
}
