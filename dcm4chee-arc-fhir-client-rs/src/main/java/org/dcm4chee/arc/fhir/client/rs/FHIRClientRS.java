/*
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
 */

package org.dcm4chee.arc.fhir.client.rs;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.IWebApplicationCache;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.WebApplication;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.fhir.client.FHIRClient;
import org.dcm4chee.arc.fhir.client.ImagingStudy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Gunter Zeilinger <gunterze@protonmail.com>
 * @since Oct 2025
 */
@RequestScoped
@Path("aets/{aet}/rs")
public class FHIRClientRS {
    private static final Logger LOG = LoggerFactory.getLogger(FHIRClientRS.class);

    @Inject
    private Device device;

    @Inject
    private IWebApplicationCache webAppCache;

    @PathParam("aet")
    private String aet;

    @Inject
    private FHIRClient fhirClient;

    @Context
    private HttpServletRequest request;

    @Context
    private HttpHeaders headers;

    @POST
    @Path("/studies/{StudyInstanceUID}/fhir/{webAppName}")
    public Response createImagingStudy(
            @PathParam("StudyInstanceUID") String studyUID,
            @PathParam("webAppName") String webAppName) {
        LOG.info("Process {} {} from {}@{}",
                request.getMethod(),
                toString(),
                request.getRemoteUser(),
                request.getRemoteHost());
        ApplicationEntity ae = getApplicationEntity();
        if (ae == null)
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);
        WebApplication webApp = null;
        try {
            webApp = getWebApplication(webAppName);
        } catch (ConfigurationException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        }
        if (webApp == null)
            return errResponse("No such Web Application: " + webApp, Response.Status.NOT_FOUND);
        return fhirClient.create(ae, studyUID, webApp, headers.getAcceptableMediaTypes().toArray(new MediaType[0]));
    }

    private ApplicationEntity getApplicationEntity() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        return ae == null || !ae.isInstalled() ? null : ae;
    }

    private WebApplication getWebApplication(String webAppName) throws ConfigurationException {
        WebApplication webApp = webAppCache.get(webAppName);
        return webApp == null || !webApp.isInstalled()
                || !webApp.containsServiceClass(WebApplication.ServiceClass.FHIR)
                ? null
                : webApp;
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

}
