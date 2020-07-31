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

package org.dcm4chee.arc.ups.rs;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.IApplicationEntityCache;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.UPSTemplate;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.ups.UPSService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since July 2020
 */
@RequestScoped
@Path("aets/{AETitle}/dimse/{movescp}")
public class UpsDimseRS {
    private static final Logger LOG = LoggerFactory.getLogger(UpsDimseRS.class);

    @Context
    private HttpServletRequest request;

    @Inject
    private Device device;

    @Inject
    private UPSService upsService;

    @Inject
    private IApplicationEntityCache aeCache;

    @PathParam("AETitle")
    private String aet;

    @PathParam("movescp")
    private String movescp;

    @QueryParam("upsLabel")
    private String upsLabel;

    @QueryParam("upsScheduledTime")
    private String upsScheduledTime;

    @HeaderParam("Content-Type")
    private MediaType contentType;

    @POST
    @Path("/studies/csv:{field}/workitems/{upsTemplateID}")
    public Response createWorkitems(
            @PathParam("field") int field,
            @PathParam("upsTemplateID") String upsTemplateID,
            @QueryParam("csvPatientID") String csvPatientIDField,
            InputStream in) {
        return createWorkitemsFromCSV(field, upsTemplateID, upsLabel, upsScheduledTime, csvPatientIDField, in);
    }

    private Response createWorkitemsFromCSV(int studyUIDField, String upsTemplateID, String upsLabel, String scheduledTime,
                                            String csvPatientIDField, InputStream in) {
        logRequest();
        if (studyUIDField < 1)
            return errResponse(Response.Status.BAD_REQUEST,
                    "CSV field for Study Instance UID should be greater than or equal to 1");

        int patientIDField = 0;
        if (csvPatientIDField != null && (patientIDField = patientIDField(csvPatientIDField)) < 1)
            return errResponse(Response.Status.BAD_REQUEST,
                    "CSV field for Patient ID should be greater than or equal to 1");

        try {
            aeCache.findApplicationEntity(movescp);
            ArchiveDeviceExtension arcDev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
            UPSTemplate upsTemplate = arcDev.getUPSTemplate(upsTemplateID);
            if (upsTemplate == null)
                return errResponse(Response.Status.NOT_FOUND, "No such UPS Template: " + upsTemplateID);

            UpsCSV upsCSV = new UpsCSV(device,
                                        upsService,
                                        HttpServletRequestInfo.valueOf(request),
                                        getArchiveAE(),
                                        studyUIDField,
                                        upsTemplate,
                                        csvDelimiter());
            return upsCSV.createWorkitems(upsLabel, scheduledTime, patientIDField, movescp, in);
        } catch (IllegalStateException | ConfigurationException e) {
            return errResponse(Response.Status.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private ArchiveAEExtension getArchiveAE() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        if (ae == null || !ae.isInstalled()) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        return ae.getAEExtensionNotNull(ArchiveAEExtension.class);
    }

    private char csvDelimiter() {
        return ("semicolon".equals(contentType.getParameters().get("delimiter"))) ? ';' : ',';
    }

    private int patientIDField(String csvPatientIDField) {
        try {
            return Integer.parseInt(csvPatientIDField);
        } catch (NumberFormatException e) {
            LOG.info("CSV Patient ID Field {} cannot be parsed", csvPatientIDField);
        }
        return 0;
    }

    private void logRequest() {
        LOG.info("Process {} {}?{} from {}@{}",
                request.getMethod(),
                request.getRequestURI(),
                request.getQueryString(),
                request.getRemoteUser(),
                request.getRemoteHost());
    }

    private Response errResponse(Response.Status status, String msg) {
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
