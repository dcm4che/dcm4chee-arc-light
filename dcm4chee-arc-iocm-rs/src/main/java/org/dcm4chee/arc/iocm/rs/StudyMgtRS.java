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
 * Portions created by the Initial Developer are Copyright (C) 2017-2019
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

package org.dcm4chee.arc.iocm.rs;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.data.Tag;
import org.dcm4che3.json.JSONReader;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.WebApplication;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.RSOperation;
import org.dcm4chee.arc.delete.DeletionService;
import org.dcm4chee.arc.delete.StudyNotEmptyException;
import org.dcm4chee.arc.delete.StudyNotFoundException;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.keycloak.KeycloakContext;
import org.dcm4chee.arc.patient.*;
import org.dcm4chee.arc.query.util.QueryAttributes;
import org.dcm4chee.arc.rs.client.RSForward;
import org.dcm4chee.arc.study.StudyMgtContext;
import org.dcm4chee.arc.study.StudyMissingException;
import org.dcm4chee.arc.study.StudyService;
import org.dcm4chee.arc.validation.constraints.InvokeValidate;
import org.dcm4chee.arc.validation.constraints.ValidValueOf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonParsingException;
import javax.persistence.NoResultException;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Apr 2021
 */

@RequestScoped
@Path("aets/{AETitle}/rs")
@InvokeValidate(type = StudyMgtRS.class)
public class StudyMgtRS {
    private static final Logger LOG = LoggerFactory.getLogger(StudyMgtRS.class);
    private static final String SUPER_USER_ROLE = "super-user-role";

    @Inject
    private Device device;

    @Inject
    private RSForward rsForward;

    @Inject
    private DeletionService deletionService;

    @Inject
    private StudyService studyService;

    @Inject
    private PatientService patientService;

    @PathParam("AETitle")
    private String aet;

    @Context
    private HttpServletRequest request;

    @Context
    private UriInfo uriInfo;

    @Override
    public String toString() {
        String requestURI = request.getRequestURI();
        String queryString = request.getQueryString();
        return queryString == null ? requestURI : requestURI + '?' + queryString;
    }

    public void validate() {
        logRequest();
        new QueryAttributes(uriInfo, null);
    }

    @DELETE
    @Path("/studies/{StudyUID}")
    public Response deleteStudy(
            @PathParam("StudyUID") String studyUID,
            @QueryParam("retainObj") @Pattern(regexp = "true|false") @DefaultValue("false") String retainObj) {
        ArchiveAEExtension arcAE = getArchiveAE();
        if (arcAE == null)
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);

        validateAcceptedUserRoles(arcAE);
        if (aet.equals(arcAE.getApplicationEntity().getAETitle()))
            validateWebAppServiceClass();

        try {
            deletionService.deleteStudy(
                    studyUID, HttpServletRequestInfo.valueOf(request), arcAE, Boolean.parseBoolean(retainObj));
            rsForward.forward(RSOperation.DeleteStudy, arcAE, null, request);
            return Response.noContent().build();
        } catch (StudyNotFoundException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (StudyNotEmptyException e) {
            return errResponse(e.getMessage(), Response.Status.FORBIDDEN);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @PUT
    @Path("/studies/{study}")
    @Consumes("application/dicom+json,application/json")
    @Produces("application/json")
    public Response updateStudy(
            @PathParam("study") String studyUID,
            InputStream in) {
        ArchiveAEExtension arcAE = getArchiveAE();
        if (arcAE == null)
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);

        validateAcceptedUserRoles(arcAE);
        if (aet.equals(arcAE.getApplicationEntity().getAETitle()))
            validateWebAppServiceClass();

        final Attributes attrs = toAttributes(in);
        IDWithIssuer patientID = IDWithIssuer.pidOf(attrs);
        if (patientID == null || !attrs.containsValue(Tag.StudyInstanceUID)
                || !studyUID.equals(attrs.getString(Tag.StudyInstanceUID)))
            return errResponse("Missing Patient ID or Study Instance UID in request payload or Study UID in request does not match Study UID in request payload",
                            Response.Status.BAD_REQUEST);

        Patient patient = patientService.findPatient(patientID);
        if (patient == null)
            return errResponse("Patient[id=" + patientID + "] does not exist.", Response.Status.NOT_FOUND);

        try {
            StudyMgtContext ctx = studyService.createStudyMgtContextWEB(
                    HttpServletRequestInfo.valueOf(request), arcAE.getApplicationEntity());
            ctx.setPatient(patient);
            ctx.setAttributes(attrs);
            studyService.updateStudy(ctx);
            rsForward.forward(RSOperation.UpdateStudy, arcAE, attrs, request);
            return Response.ok((StreamingOutput) out -> {
                                    try (JsonGenerator gen = Json.createGenerator(out)) {
                                        arcAE.encodeAsJSONNumber(new JSONWriter(gen)).write(attrs);
                                    }
                                })
                            .build();
        } catch (StudyMissingException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (PatientMismatchException e) {
            return errResponse(e.getMessage(), Response.Status.BAD_REQUEST);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @PUT
    @Path("/studies/{study}/series/{series}")
    @Consumes("application/dicom+json,application/json")
    @Produces("application/json")
    public Response updateSeries(
            @PathParam("study") String studyUID,
            @PathParam("series") String seriesUID,
            InputStream in) {
        ArchiveAEExtension arcAE = getArchiveAE();
        if (arcAE == null)
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);

        validateAcceptedUserRoles(arcAE);
        if (aet.equals(arcAE.getApplicationEntity().getAETitle()))
            validateWebAppServiceClass();

        final Attributes attrs = toAttributes(in);
        IDWithIssuer patientID = IDWithIssuer.pidOf(attrs);
        if (patientID == null || !attrs.containsValue(Tag.SeriesInstanceUID))
            return errResponse("Missing Patient ID or Series Instance UID in request payload",
                            Response.Status.BAD_REQUEST);

        if (!seriesUID.equals(attrs.getString(Tag.SeriesInstanceUID)))
            return errResponse("Series UID in request does not match Series UID in request payload",
                            Response.Status.BAD_REQUEST);

        Patient patient = patientService.findPatient(patientID);
        if (patient == null)
            return errResponse("Patient[id=" + patientID + "] does not exist.", Response.Status.NOT_FOUND);

        try {
            StudyMgtContext ctx = studyService.createStudyMgtContextWEB(
                    HttpServletRequestInfo.valueOf(request), arcAE.getApplicationEntity());
            ctx.setPatient(patient);
            ctx.setAttributes(attrs);
            ctx.setStudyInstanceUID(studyUID);
            ctx.setSeriesInstanceUID(seriesUID);
            studyService.updateSeries(ctx);
            rsForward.forward(RSOperation.UpdateSeries, arcAE, attrs, request);
            return Response.ok((StreamingOutput)out -> {
                                    try (JsonGenerator gen = Json.createGenerator(out)) {
                                        arcAE.encodeAsJSONNumber(new JSONWriter(gen)).write(attrs);
                                    }
                                })
                            .build();
        } catch (StudyMissingException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (PatientMismatchException e) {
            return errResponse(e.getMessage(), Response.Status.BAD_REQUEST);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @PUT
    @Path("/studies/{StudyInstanceUID}/access/{accessControlID}")
    public Response updateStudyAccessControlID(
            @PathParam("StudyInstanceUID") String studyUID,
            @PathParam("accessControlID") String accessControlID) {
        ArchiveAEExtension arcAE = getArchiveAE();
        if (arcAE == null)
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);

        validateAcceptedUserRoles(arcAE);
        if (aet.equals(arcAE.getApplicationEntity().getAETitle()))
            validateWebAppServiceClass();

        try {
            StudyMgtContext ctx = studyService.createStudyMgtContextWEB(
                    HttpServletRequestInfo.valueOf(request), arcAE.getApplicationEntity());
            ctx.setStudyInstanceUID(studyUID);
            ctx.setAccessControlID("null".equals(accessControlID) ? "*" :  accessControlID);
            studyService.updateAccessControlID(ctx);
            rsForward.forward(RSOperation.UpdateStudyAccessControlID, arcAE, null, request);
            return Response.noContent().build();
        } catch (StudyMissingException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @POST
    @Path("/studies/{study}/patient")
    public Response moveStudyToPatient(
            @PathParam("study") String studyUID,
            @QueryParam("updatePolicy")
            @ValidValueOf(type = Attributes.UpdatePolicy.class, message = "Invalid attribute update policy")
                    String updatePolicy) {
        ArchiveAEExtension arcAE = getArchiveAE();
        if (arcAE == null)
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);

        validateAcceptedUserRoles(arcAE);
        if (aet.equals(arcAE.getApplicationEntity().getAETitle()))
            validateWebAppServiceClass();

        QueryAttributes queryAttrs = new QueryAttributes(uriInfo, null);
        Attributes queryKeys = queryAttrs.getQueryKeys();
        if (queryKeys.getString(Tag.PatientID) == null)
            return errResponse("Missing Patient ID in query filters", Response.Status.BAD_REQUEST);

        IDWithIssuer pid = IDWithIssuer.pidOf(queryKeys);
        try {
            PatientMgtContext ctx = patientService.createPatientMgtContextWEB(HttpServletRequestInfo.valueOf(request));
            ctx.setAttributeUpdatePolicy(Attributes.UpdatePolicy.REPLACE);
            ctx.setPatientID(pid);
            ctx.setAttributes(queryKeys);
            if (updatePolicy != null)
                ctx.setAttributeUpdatePolicy(Attributes.UpdatePolicy.valueOf(updatePolicy));
            studyService.moveStudyToPatient(studyUID, ctx);
            rsForward.forward(RSOperation.MoveStudyToPatient, arcAE, null, request);
            return Response.noContent().build();
        } catch (StudyMissingException e) {
            return errResponse(e.getMessage(), Response.Status.BAD_REQUEST);
        } catch (NonUniquePatientException e) {
            return errResponse(e.getMessage(), Response.Status.CONFLICT);
        } catch (PatientMergedException e) {
            return errResponse(e.getMessage(), Response.Status.FORBIDDEN);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @PUT
    @Path("/studies/{studyUID}/expire/{expirationDate}")
    public Response updateStudyExpirationDate(
            @PathParam("studyUID") String studyUID,
            @PathParam("expirationDate") String expirationDate,
            @QueryParam("ExporterID") String expirationExporterID,
            @QueryParam("FreezeExpirationDate") @Pattern(regexp = "true|false") String freezeExpirationDate) {
        return updateExpirationDate(RSOperation.UpdateStudyExpirationDate, studyUID, null, expirationDate,
                expirationExporterID, freezeExpirationDate);
    }

    @PUT
    @Path("/studies/{studyUID}/series/{seriesUID}/expire/{expirationDate}")
    public Response updateSeriesExpirationDate(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @PathParam("expirationDate") String expirationDate,
            @QueryParam("ExporterID") String expirationExporterID) {
        return updateExpirationDate(RSOperation.UpdateSeriesExpirationDate, studyUID, seriesUID, expirationDate,
                expirationExporterID, null);
    }

    private Response updateExpirationDate(RSOperation op, String studyUID, String seriesUID, String expirationDate,
                                          String expirationExporterID, String freezeExpirationDate) {
        boolean updateSeriesExpirationDate = seriesUID != null;
        ArchiveAEExtension arcAE = getArchiveAE();
        if (arcAE == null)
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);

        validateAcceptedUserRoles(arcAE);
        if (aet.equals(arcAE.getApplicationEntity().getAETitle()))
            validateWebAppServiceClass();

        try {
            StudyMgtContext ctx = studyService.createStudyMgtContextWEB(
                    HttpServletRequestInfo.valueOf(request), arcAE.getApplicationEntity());
            ctx.setStudyInstanceUID(studyUID);
            boolean revokeExpiration = expirationDate.equals("never");
            if (revokeExpiration && seriesUID != null)
                return errResponse("Revoke expiration on Series not allowed.", Response.Status.BAD_REQUEST);

            ctx.setExpirationDate(
                    revokeExpiration ? null : LocalDate.parse(expirationDate, DateTimeFormatter.BASIC_ISO_DATE));
            ctx.setExpirationExporterID(expirationExporterID);
            ctx.setFreezeExpirationDate(Boolean.parseBoolean(freezeExpirationDate));
            if ("false".equals(freezeExpirationDate))
                ctx.setUnfreezeExpirationDate(true);
            ctx.setSeriesInstanceUID(seriesUID);
            studyService.updateExpirationDate(ctx);
            rsForward.forward(op, arcAE, null, request);
            return Response.noContent().build();
        } catch (DateTimeParseException e) {
            return errResponse("Expiration date cannot be parsed.", Response.Status.BAD_REQUEST);
        } catch (NoResultException e) {
            return errResponse(
                    updateSeriesExpirationDate ? "Series not found. " + seriesUID : "Study not found. " + studyUID,
                    Response.Status.NOT_FOUND);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private Attributes toAttributes(InputStream in) {
        try {
            return new JSONReader(Json.createParser(new InputStreamReader(in, StandardCharsets.UTF_8)))
                    .readDataset(null);
        } catch (JsonParsingException e) {
            throw new WebApplicationException(
                    errResponse(e.getMessage() + " at location : " + e.getLocation(), Response.Status.BAD_REQUEST));
        } catch (Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    private ArchiveAEExtension getArchiveAE() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        return ae == null || !ae.isInstalled() ? null : ae.getAEExtension(ArchiveAEExtension.class);
    }

    private void logRequest() {
        LOG.info("Process {} {} from {}@{}",
                request.getMethod(),
                this,
                request.getRemoteUser(),
                request.getRemoteHost());
    }

    private Response errResponse(String msg, Response.Status status) {
        return errResponseAsTextPlain("{\"errorMessage\":\"" + msg + "\"}", status);
    }

    private Response errResponseAsTextPlain(String errorMsg, Response.Status status) {
        LOG.info("Response {} caused by {}", status, errorMsg);
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
}
