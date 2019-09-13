/*
 * ** BEGIN LICENSE BLOCK *****
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
 * ** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.patient.impl;

import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.patient.*;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import java.net.Socket;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jul 2015
 */
@ApplicationScoped
public class PatientServiceImpl implements PatientService {

    @Inject
    private PatientServiceEJB ejb;

    @Inject
    private Device device;

    @Inject
    private Event<PatientMgtContext> patientMgtEvent;

    @Override
    public PatientMgtContext createPatientMgtContextDIMSE(Association as) {
        PatientMgtContextImpl ctx = new PatientMgtContextImpl(device);
        ctx.setAssociation(as);
        return ctx;
    }

    @Override
    public PatientMgtContext createPatientMgtContextWEB(HttpServletRequestInfo httpRequest) {
        PatientMgtContextImpl ctx = new PatientMgtContextImpl(device);
        ctx.setHttpServletRequestInfo(httpRequest);
        return ctx;
    }

    @Override
    public PatientMgtContext createPatientMgtContextHL7(HL7Application hl7App, Socket socket, UnparsedHL7Message msg) {
        PatientMgtContextImpl ctx = new PatientMgtContextImpl(device);
        ctx.setSocket(socket);
        ctx.setUnparsedHL7Message(msg);
        ctx.setHL7Application(hl7App);
        return ctx;
    }

    @Override
    public PatientMgtContext createPatientMgtContextScheduler() {
        return new PatientMgtContextImpl(device);
    }

    @Override
    public List<Patient> findPatients(IDWithIssuer pid) {
        return ejb.findPatients(pid);
    }

    @Override
    public Patient findPatient(IDWithIssuer pid) {
        return ejb.findPatient(pid);
    }

    @Override
    public Patient createPatient(PatientMgtContext ctx) {
        try {
            return ejb.createPatient(ctx);
        } catch (RuntimeException e) {
            ctx.setException(e);
            throw e;
        } finally {
            if (ctx.getEventActionCode() != null)
                patientMgtEvent.fire(ctx);
        }
    }

    @Override
    public Patient updatePatient(PatientMgtContext ctx)
            throws NonUniquePatientException, PatientMergedException {
        try {
            return ejb.updatePatient(ctx);
        } catch (RuntimeException e) {
            ctx.setException(e);
            throw e;
        } finally {
            if (ctx.getEventActionCode() != null)
                patientMgtEvent.fire(ctx);
        }
    }

    @Override
    public Patient mergePatient(PatientMgtContext ctx)
            throws NonUniquePatientException, PatientMergedException, CircularPatientMergeException {
        if (ctx.getPatientID().matches(ctx.getPreviousPatientID()))
            throw new CircularPatientMergeException("PriorPatientID same as target PatientID");

        try {
            return ejb.mergePatient(ctx);
        } catch (RuntimeException e) {
            ctx.setException(e);
            throw e;
        } finally {
            if (ctx.getEventActionCode() != null)
                patientMgtEvent.fire(ctx);
        }
    }

    @Override
    public Patient changePatientID(PatientMgtContext ctx)
            throws NonUniquePatientException, PatientMergedException, PatientTrackingNotAllowedException {
        if (device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class).isHL7TrackChangedPatientID()) {
            if (isEitherHavingNoIssuer(ctx))
                throw new PatientTrackingNotAllowedException(
                        "Either previous or new Patient ID has missing issuer and change patient id tracking is enabled. "
                                + "Disable change patient id tracking feature and retry update");
            if (ctx.getPatientID().equals(ctx.getPreviousPatientID()))
                throw new CircularPatientMergeException("PriorPatientID same as target PatientID");
            createPatient(ctx);
            return mergePatient(ctx);
        }
        try {
            return ejb.changePatientID(ctx);
        } catch (RuntimeException e) {
            ctx.setException(e);
            throw e;
        } finally {
            if (ctx.getEventActionCode() != null)
                patientMgtEvent.fire(ctx);
        }
    }

    private boolean isEitherHavingNoIssuer(PatientMgtContext ctx) {
        IDWithIssuer prevPatientID = ctx.getPreviousPatientID();
        IDWithIssuer newPatientID = ctx.getPatientID();
        return (prevPatientID.getIssuer() == null && newPatientID.getIssuer() != null)
                || (prevPatientID.getIssuer() != null && newPatientID.getIssuer() == null);
    }

    @Override
    public Patient findPatient(PatientMgtContext ctx) {
        return ejb.findPatient(ctx);
    }

    @Override
    public void deletePatient(PatientMgtContext ctx) {
        ejb.deletePatient(ctx.getPatient());
        patientMgtEvent.fire(ctx);
    }

    @Override
    public Patient updatePatientStatus(PatientMgtContext ctx) {
        try {
            return ejb.updatePatientStatus(ctx);
        } finally {
            patientMgtEvent.fire(ctx);
        }
    }
}
