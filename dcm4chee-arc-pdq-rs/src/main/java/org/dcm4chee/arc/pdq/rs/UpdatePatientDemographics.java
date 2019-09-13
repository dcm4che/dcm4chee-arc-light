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

package org.dcm4chee.arc.pdq.rs;

import org.dcm4che3.audit.AuditMessages;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.PDQServiceDescriptor;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.patient.PatientService;
import org.dcm4chee.arc.pdq.PDQServiceException;
import org.dcm4chee.arc.pdq.PDQServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Oct 2018
 */
@RequestScoped
@Path("aets/{AETitle}/rs")
public class UpdatePatientDemographics {
    private static final Logger LOG = LoggerFactory.getLogger(UpdatePatientDemographics.class);

    @Context
    private HttpServletRequest request;

    @Inject
    private Device device;

    @Inject
    private PDQServiceFactory serviceFactory;

    @Inject
    private PatientService patientService;

    @PathParam("AETitle")
    private String aet;

    @QueryParam("adjustIssuerOfPatientID")
    @Pattern(regexp = "true|false")
    private String adjustIssuerOfPatientID;

    @POST
    @Path("/patients/{PatientID}/pdq/{PDQServiceID}")
    @Produces("application/json")
    public Response update(@PathParam("PDQServiceID") String pdqServiceID,
                       @PathParam("PatientID") IDWithIssuer patientID) {
        logRequest();
        try {
            PDQServiceDescriptor descriptor = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class)
                                                .getPDQServiceDescriptorNotNull(pdqServiceID);
            PatientMgtContext ctx = patientService.createPatientMgtContextWEB(HttpServletRequestInfo.valueOf(request));
            ctx.setPatientID(patientID);
            ctx.setPDQServiceURI(descriptor.getPDQServiceURI().toString());
            Attributes attrs;
            boolean adjustIssuerOfPatientID = adjustIssuerOfPatientID();
            try {
                attrs = serviceFactory.getPDQService(descriptor)
                        .query(adjustIssuerOfPatientID
                                ? patientID.withoutIssuer()
                                : patientID);
            } catch (PDQServiceException e) {
                ctx.setPatientVerificationStatus(Patient.VerificationStatus.VERIFICATION_FAILED);
                patientService.updatePatientStatus(ctx);
                return errResponseAsTextPlain(exceptionAsString(e), Response.Status.BAD_GATEWAY);
            }

            if (attrs == null) {
                ctx.setPatientVerificationStatus(Patient.VerificationStatus.NOT_FOUND);
                patientService.updatePatientStatus(ctx);
                return Response.accepted().build();
            }

            ctx.setAttributes(attrs);
            ctx.setPatientVerificationStatus(Patient.VerificationStatus.VERIFIED);
            if (adjustIssuerOfPatientID && !ctx.getPatientID().equals(patientID)) {
                ctx.setPreviousAttributes(patientID.exportPatientIDWithIssuer(null));
                patientService.changePatientID(ctx);
                LOG.info("Updated {} on verification against {}", patientID, descriptor);
            } else {
                patientService.updatePatient(ctx);
                LOG.info(ctx.getEventActionCode().equals(AuditMessages.EventActionCode.Update)
                                ? "Updated {} on verification against {}"
                                : "Verified {} against {}",
                        patientID, descriptor);
            }
            return rsp(ctx);
        } catch(IllegalArgumentException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private Response rsp(PatientMgtContext ctx) {
        return Response.ok("{\"action\":\"" + ctx.getEventActionCode() + "\"}").build();
    }

    private boolean adjustIssuerOfPatientID() {
        return "true".equals(adjustIssuerOfPatientID);
    }

    private void logRequest() {
        LOG.info("Process {} {}?{} from {}@{}",
                request.getMethod(),
                request.getRequestURI(),
                request.getQueryString(),
                request.getRemoteUser(),
                request.getRemoteHost());
    }

    private Response errResponse(String msg, Response.Status status) {
        return errResponseAsTextPlain("{\"errorMessage\":\"" + msg + "\"}", status);
    }

    private Response errResponseAsTextPlain(String errorMsg, Response.Status status) {
        LOG.warn("Response {} caused by {}", status, errorMsg);
        return Response.status(status)
                .entity(errorMsg)
                .type("text/plain")
                .build();
    }

    private String exceptionAsString(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
