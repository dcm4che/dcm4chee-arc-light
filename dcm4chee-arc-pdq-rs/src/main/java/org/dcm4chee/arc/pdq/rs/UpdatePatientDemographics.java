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

package org.dcm4chee.arc.pdq.rs;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.PDQServiceDescriptor;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.patient.PatientService;
import org.dcm4chee.arc.pdq.PDQServiceException;
import org.dcm4chee.arc.pdq.PDQServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
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

    @POST
    @Path("/patients/{PatientID}/pdq/{PDQServiceID}")
    public Response update(@PathParam("PDQServiceID") String pdqServiceID,
                       @PathParam("PatientID") IDWithIssuer patientID) {
        logRequest();
        try {
            ArchiveDeviceExtension arcdev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
            PDQServiceDescriptor descriptor = arcdev.getPDQServiceDescriptor(pdqServiceID);
            if (descriptor == null)
                return errResponse("No such PDQ Service: " + pdqServiceID, Response.Status.NOT_FOUND);

            PatientMgtContext ctx = patientService.createPatientMgtContextWEB(request);
            ctx.setPatientID(patientID);
            ctx.setPDQServiceURI(descriptor.getPDQServiceURI().toString());
            Attributes attrs;
            try {
                attrs = serviceFactory.getPDQService(descriptor).query(patientID);
            } catch (PDQServiceException e) {
                ctx.setPatientVerificationStatus(Patient.VerificationStatus.VERIFICATION_FAILED);
                patientService.updatePatientStatus(ctx);
                return errResponseAsTextPlain(e, Response.Status.BAD_GATEWAY);
            }
            if (attrs == null) {
                ctx.setPatientVerificationStatus(Patient.VerificationStatus.NOT_FOUND);
                patientService.updatePatientStatus(ctx);
                return Response.status(Response.Status.ACCEPTED).build();
            }
            ctx.setAttributes(attrs);
            ctx.setPatientVerificationStatus(Patient.VerificationStatus.VERIFIED);
            patientService.updatePatient(ctx);
            return Response.ok()
                    .entity("{\"action\":\"" + ctx.getEventActionCode() + "\"}")
                    .type("application/json")
                    .build();
        } catch (Exception e) {
            return errResponseAsTextPlain(e, Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private void logRequest() {
        LOG.info("Process {} {} from {}@{}", request.getMethod(), request.getRequestURI(),
                request.getRemoteUser(), request.getRemoteHost());
    }

    private Response errResponse(String errorMessage, Response.Status status) {
        return Response.status(status).entity("{\"errorMessage\":\"" + errorMessage + "\"}").build();
    }

    private Response errResponseAsTextPlain(Exception e, Response.Status status) {
        return Response.status(status)
                .entity(exceptionAsString(e))
                .type("text/plain")
                .build();
    }

    private String exceptionAsString(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
