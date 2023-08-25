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
 * Portions created by the Initial Developer are Copyright (C) 2019
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

package org.dcm4chee.arc.ian.rs;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.stream.JsonGenerator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.IApplicationEntityCache;
import org.dcm4che3.conf.json.JsonWriter;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.*;
import org.dcm4che3.util.TagUtils;
import org.dcm4che3.util.UIDUtils;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.ian.scu.IANSCU;
import org.dcm4chee.arc.keycloak.KeycloakContext;
import org.dcm4chee.arc.query.QueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Mar 2019
 */
@RequestScoped
@Path("aets/{aet}/rs")
public class IANSCURS {
    private static final Logger LOG = LoggerFactory.getLogger(IANSCURS.class);
    private static final String SUPER_USER_ROLE = "super-user-role";

    @Inject
    private Device device;

    @Inject
    private IApplicationEntityCache aeCache;

    @Inject
    private IANSCU ianSCU;

    @Inject
    private QueryService queryService;

    @PathParam("aet")
    private String aet;

    @Context
    private HttpServletRequest request;

    @POST
    @Path("/studies/{StudyInstanceUID}/ian/dicom:{externalAET}")
    @Produces("application/json")
    public Response studyIAN(
            @PathParam("StudyInstanceUID") String studyUID,
            @PathParam("externalAET") String externalAET) {
        return ian(studyUID, null, null, externalAET);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}/series/{SeriesInstanceUID}/ian/dicom:{externalAET}")
    @Produces("application/json")
    public Response seriesIAN(
            @PathParam("StudyInstanceUID") String studyUID,
            @PathParam("SeriesInstanceUID") String seriesUID,
            @PathParam("externalAET") String externalAET) {
        return ian(studyUID, seriesUID, null, externalAET);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}/series/{SeriesInstanceUID}/instances/{SOPInstanceUID}/ian/dicom:{externalAET}")
    @Produces("application/json")
    public Response instanceIAN(
            @PathParam("StudyInstanceUID") String studyUID,
            @PathParam("SeriesInstanceUID") String seriesUID,
            @PathParam("SOPInstanceUID") String sopUID,
            @PathParam("externalAET") String externalAET) {
        return ian(studyUID, seriesUID, sopUID, externalAET);
    }

    private Response ian(String studyUID, String seriesUID, String sopUID, String externalAET) {
        logRequest();
        ArchiveAEExtension arcAE = getArchiveAE();
        if (arcAE == null)
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);

        validateAcceptedUserRoles(arcAE);
        ApplicationEntity ae = arcAE.getApplicationEntity();
        if (aet.equals(ae.getAETitle()))
            validateWebAppServiceClass();

        Response.Status rspStatus = Response.Status.BAD_GATEWAY;
        try {
            ApplicationEntity remoteAE = aeCache.findApplicationEntity(externalAET);
            Attributes ian = queryService.createIAN(ae, studyUID, new String[]{ seriesUID }, sopUID,
                    null, null, null);
            if (ian == null)
                return errResponse("No matching instances", Response.Status.NOT_FOUND);

            DimseRSP dimseRSP = ianSCU.sendIANRQ(ae, remoteAE, UIDUtils.createUID(), ian);
            dimseRSP.next();
            if (dimseRSP.getCommand().getInt(Tag.Status, -1) == Status.Success)
                rspStatus = Response.Status.OK;

            return Response.status(rspStatus)
                    .entity(entity(ian, dimseRSP))
                    .build();
        } catch (ConfigurationException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (IOException e) {
            return errResponse(e.getMessage(), rspStatus);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private StreamingOutput entity(Attributes ian, DimseRSP dimseRSP) {
        return out -> {
            try (JsonGenerator gen = Json.createGenerator(out)) {
                JsonWriter writer = new JsonWriter(gen);
                gen.writeStartObject();
                writer.writeNotNullOrDef(
                        "status", TagUtils.shortToHexString(dimseRSP.getCommand().getInt(Tag.Status, -1)), null);
                writer.writeNotNullOrDef(
                        "error", dimseRSP.getCommand().getString(Tag.ErrorComment), null);
                writer.writeNotNullOrDef("instances",
                        ian.getSequence(Tag.ReferencedSeriesSequence).stream()
                                .mapToInt(a -> a.getSequence(Tag.ReferencedSOPSequence).size())
                                .sum(),
                        0);
                gen.writeEnd();
            }
        };
    }

    private void logRequest() {
        LOG.info("Process {} {} from {}@{}",
                request.getMethod(),
                request.getRequestURI(),
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

    private void validateAcceptedUserRoles(ArchiveAEExtension arcAE) {
        KeycloakContext keycloakContext = KeycloakContext.valueOf(request);
        if (keycloakContext.isSecured() && !keycloakContext.isUserInRole(System.getProperty(SUPER_USER_ROLE))) {
            if (!arcAE.isAcceptedUserRole(keycloakContext.getRoles()))
                throw new WebApplicationException(
                        "Application Entity " + arcAE.getApplicationEntity().getAETitle() + " does not list role of accessing user",
                        Response.Status.FORBIDDEN);
        }
    }

    private void validateWebAppServiceClass() {
        device.getWebApplications().stream()
                .filter(webApp -> request.getRequestURI().startsWith(webApp.getServicePath())
                        && Arrays.asList(webApp.getServiceClasses())
                        .contains(WebApplication.ServiceClass.DCM4CHEE_ARC_AET))
                .findFirst()
                .orElseThrow(() -> new WebApplicationException(errResponse(
                        "No Web Application with DCM4CHEE_ARC_AET service class found for Application Entity: " + aet,
                        Response.Status.NOT_FOUND)));
    }

    private ArchiveAEExtension getArchiveAE() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        return ae == null || !ae.isInstalled() ? null : ae.getAEExtension(ArchiveAEExtension.class);
    }
}
