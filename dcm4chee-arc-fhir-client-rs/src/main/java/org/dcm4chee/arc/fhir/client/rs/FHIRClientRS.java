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
import jakarta.ws.rs.core.*;
import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.IWebApplicationCache;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.WebApplication;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4chee.arc.fhir.client.FHIRClient;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.query.util.QueryParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.util.List;

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

    @Inject
    private QueryService queryService;

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
                request.getRequestURI(),
                request.getRemoteUser(),
                request.getRemoteHost());
        ApplicationEntity ae = getApplicationEntity();
        if (ae == null)
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("No such Application Entity: " + aet)
                    .type(MediaType.TEXT_PLAIN_TYPE)
                    .build();
        try {
            WebApplication webApp = getWebApplication(webAppName);
            if (webApp == null)
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("No such Web Application: " + webAppName)
                        .type(MediaType.TEXT_PLAIN_TYPE)
                        .build();

            List<Attributes> instances = queryService.queryInstances(queryContext(ae, studyUID));
            return fhirClient.create(ae, instances, webApp, headers.getAcceptableMediaTypes().toArray(new MediaType[0]));
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity((StreamingOutput) output -> {
                        try (PrintWriter printWriter = new PrintWriter(output)) {
                            printWriter.println(e.getMessage());
                            e.printStackTrace(printWriter);
                        }
                    })
                    .type(MediaType.TEXT_PLAIN_TYPE)
                    .build();
        }
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

    private QueryContext queryContext(ApplicationEntity ae, String studyUID) {
        QueryParam queryParam = new QueryParam(ae);
        QueryContext queryContext = queryService.newQueryContext(ae, queryParam);
        queryContext.setQueryRetrieveLevel(QueryRetrieveLevel2.IMAGE);
        Attributes keys = new Attributes(1);
        keys.setString(Tag.StudyInstanceUID, VR.UI, studyUID);
        queryContext.setQueryKeys(keys);
        queryContext.setReturnPrivate(true);
        return queryContext;
    }

}
