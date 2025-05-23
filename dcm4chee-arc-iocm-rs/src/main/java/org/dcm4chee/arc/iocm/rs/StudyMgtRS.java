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

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParsingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import jakarta.ws.rs.core.UriInfo;
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
import org.dcm4chee.arc.delete.StudyDeletionInProgressException;
import org.dcm4chee.arc.delete.StudyNotEmptyException;
import org.dcm4chee.arc.delete.StudyNotFoundException;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.keycloak.KeycloakContext;
import org.dcm4chee.arc.patient.*;
import org.dcm4chee.arc.query.util.QueryAttributes;
import org.dcm4chee.arc.rs.client.RSForward;
import org.dcm4chee.arc.store.StoreService;
import org.dcm4chee.arc.store.StoreSession;
import org.dcm4chee.arc.study.StudyMgtContext;
import org.dcm4chee.arc.study.StudyMissingException;
import org.dcm4chee.arc.study.StudyService;
import org.dcm4chee.arc.validation.constraints.InvokeValidate;
import org.dcm4chee.arc.validation.constraints.ValidValueOf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

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
    private static final String UPDATE_STUDY_CONFLICTING_UIDS_MSG = "[StudyUID={}] in request URL does not match [StudyUID={}] in request payload";
    private static final String UPDATE_SERIES_CONFLICTING_UIDS_MSG = "[SeriesUID={}] in request URL do not match [SeriesUID={}] in request payload";
    private static final String UPDATE_INSTANCE_CONFLICTING_UIDS_MSG = "[SOPUID={}] in request URL do not match [SOPUID={}] in request payload";

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

    @Inject
    private StoreService storeService;

    @PathParam("AETitle")
    private String aet;

    @QueryParam("reasonForModification")
    @Pattern(regexp = "COERCE|CORRECT")
    private String reasonForModification;

    @QueryParam("sourceOfPreviousValues")
    private String sourceOfPreviousValues;

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
        } catch (StudyDeletionInProgressException e) {
            return errResponse(e.getMessage(), Response.Status.CONFLICT);
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
        Set<IDWithIssuer> patientIDs = IDWithIssuer.pidsOf(attrs);
        Collection<IDWithIssuer> trustedPatientIDs = arcAE.getArchiveDeviceExtension().retainTrustedPatientIDs(patientIDs);
        if (trustedPatientIDs.isEmpty())
            return errResponse("Missing patient identifiers with trusted assigning authority in " + patientIDs,
                    Response.Status.BAD_REQUEST);

        if (!studyUID.equals(attrs.getString(Tag.StudyInstanceUID)))
            return errResponse(MessageFormat.format(UPDATE_STUDY_CONFLICTING_UIDS_MSG, studyUID, attrs.getString(Tag.StudyInstanceUID)),
                    Response.Status.BAD_REQUEST);

        try {
            Patient patient = patientService.findPatient(trustedPatientIDs);
            if (patient == null)
                return errResponse("Patient with patient identifiers " + trustedPatientIDs + " not found.",
                        Response.Status.NOT_FOUND);

            StudyMgtContext ctx = studyService.createStudyMgtContextWEB(
                    HttpServletRequestInfo.valueOf(request), arcAE.getApplicationEntity());
            ctx.setPatient(patient);
            ctx.setAttributes(attrs);
            ctx.setReasonForModification(reasonForModification);
            ctx.setSourceOfPreviousValues(sourceOfPreviousValues);
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
        } catch (NonUniquePatientException e) {
            return errResponse(e.getMessage(), Response.Status.CONFLICT);
        } catch (PatientMergedException e) {
            return errResponse(e.getMessage(), Response.Status.FORBIDDEN);
        } catch (Exception e) {
            return e.getCause() instanceof PatientMismatchException
                    ? errResponse(e.getCause().getMessage(), Response.Status.BAD_REQUEST)
                    : errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
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
        Set<IDWithIssuer> patientIDs = IDWithIssuer.pidsOf(attrs);
        Collection<IDWithIssuer> trustedPatientIDs = arcAE.getArchiveDeviceExtension().retainTrustedPatientIDs(patientIDs);
        if (trustedPatientIDs.isEmpty())
            return errResponse("Missing patient identifiers with trusted assigning authority in " + patientIDs,
                    Response.Status.BAD_REQUEST);

        if (!seriesUID.equals(attrs.getString(Tag.SeriesInstanceUID)))
            return errResponse(MessageFormat.format(UPDATE_SERIES_CONFLICTING_UIDS_MSG,
                                                    seriesUID,
                                                    attrs.getString(Tag.SeriesInstanceUID)),
                    Response.Status.BAD_REQUEST);

        try {
            Patient patient = patientService.findPatient(trustedPatientIDs);
            if (patient == null)
                return errResponse("Patient with patient identifiers " + trustedPatientIDs + " not found.",
                        Response.Status.NOT_FOUND);

            StudyMgtContext ctx = studyService.createStudyMgtContextWEB(
                    HttpServletRequestInfo.valueOf(request), arcAE.getApplicationEntity());
            ctx.setPatient(patient);
            ctx.setAttributes(attrs);
            ctx.setStudyInstanceUID(studyUID);
            ctx.setSeriesInstanceUID(seriesUID);
            ctx.setReasonForModification(reasonForModification);
            ctx.setSourceOfPreviousValues(sourceOfPreviousValues);
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
        } catch (NonUniquePatientException e) {
            return errResponse(e.getMessage(), Response.Status.CONFLICT);
        } catch (PatientMergedException e) {
            return errResponse(e.getMessage(), Response.Status.FORBIDDEN);
        } catch (Exception e) {
            return e.getCause() instanceof PatientMismatchException
                    ? errResponse(e.getCause().getMessage(), Response.Status.BAD_REQUEST)
                    : errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @PUT
    @Path("/studies/{study}/series/{series}/instances/{instance}")
    @Consumes("application/dicom+json,application/json")
    @Produces("application/json")
    public Response updateInstance(
            @PathParam("study") String studyUID,
            @PathParam("series") String seriesUID,
            @PathParam("instance") String sopUID,
            InputStream in) {
        ArchiveAEExtension arcAE = getArchiveAE();
        if (arcAE == null)
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);

        validateAcceptedUserRoles(arcAE);
        if (aet.equals(arcAE.getApplicationEntity().getAETitle()))
            validateWebAppServiceClass();

        final Attributes attrs = toAttributes(in);
        Set<IDWithIssuer> patientIDs = IDWithIssuer.pidsOf(attrs);
        Collection<IDWithIssuer> trustedPatientIDs = arcAE.getArchiveDeviceExtension().retainTrustedPatientIDs(patientIDs);
        if (trustedPatientIDs.isEmpty())
            return errResponse("Missing patient identifiers with trusted assigning authority in " + patientIDs,
                    Response.Status.BAD_REQUEST);

        if (!sopUID.equals(attrs.getString(Tag.SOPInstanceUID)))
            return errResponse(MessageFormat.format(UPDATE_INSTANCE_CONFLICTING_UIDS_MSG,
                                                    sopUID,
                                                    attrs.getString(Tag.SOPInstanceUID)),
                    Response.Status.BAD_REQUEST);

        try {
            Patient patient = patientService.findPatient(trustedPatientIDs);
            if (patient == null)
                return errResponse("Patient with patient identifiers " + trustedPatientIDs + " not found.",
                        Response.Status.NOT_FOUND);

            restoreInstances(studyUID, seriesUID, arcAE);
            StudyMgtContext ctx = studyService.createStudyMgtContextWEB(
                    HttpServletRequestInfo.valueOf(request), arcAE.getApplicationEntity());
            ctx.setPatient(patient);
            ctx.setAttributes(attrs);
            ctx.setStudyInstanceUID(studyUID);
            ctx.setSeriesInstanceUID(seriesUID);
            ctx.setSOPInstanceUID(sopUID);
            ctx.setReasonForModification(reasonForModification);
            ctx.setSourceOfPreviousValues(sourceOfPreviousValues);
            studyService.updateInstance(ctx);
            rsForward.forward(RSOperation.UpdateInstance, arcAE, attrs, request);
            return Response.ok((StreamingOutput)out -> {
                        try (JsonGenerator gen = Json.createGenerator(out)) {
                            arcAE.encodeAsJSONNumber(new JSONWriter(gen)).write(attrs);
                        }
                    })
                    .build();
        } catch (StudyMissingException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (NonUniquePatientException e) {
            return errResponse(e.getMessage(), Response.Status.CONFLICT);
        } catch (PatientMergedException e) {
            return errResponse(e.getMessage(), Response.Status.FORBIDDEN);
        } catch (Exception e) {
            return e.getCause() instanceof PatientMismatchException
                    ? errResponse(e.getCause().getMessage(), Response.Status.BAD_REQUEST)
                    : errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private void restoreInstances(String studyUID, String seriesUID, ArchiveAEExtension arcAE) {
        try {
            StoreSession session = storeService.newStoreSession(
                    HttpServletRequestInfo.valueOf(request), arcAE.getApplicationEntity(), aet, null);
            int count = storeService.restoreInstances(
                    session, studyUID, seriesUID, arcAE.purgeInstanceRecordsDelay(), null);
            if (count > 0)
                LOG.info("Restored {} Series[UID={}] of Study[UID={}]", count, seriesUID, studyUID);
        } catch (IOException e) {
            LOG.info("Failed to restore Instance records of Series[UID={}] of Study[UID={}]\n",
                    studyUID, seriesUID, e);
        }
    }

    @PUT
    @Path("/studies/{study}/request")
    @Consumes("application/dicom+json,application/json")
    @Produces("application/json")
    public Response updateStudyRequestAttrs(
            @PathParam("study") String studyUID,
            InputStream in) {
        ArchiveAEExtension arcAE = getArchiveAE();
        if (arcAE == null)
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);

        validateAcceptedUserRoles(arcAE);
        if (aet.equals(arcAE.getApplicationEntity().getAETitle()))
            validateWebAppServiceClass();

        final List<Attributes> requestAttrs = toRequestAttributes(in);
        try {
            StudyMgtContext ctx = studyService.createStudyMgtContextWEB(
                    HttpServletRequestInfo.valueOf(request), arcAE.getApplicationEntity());
            ctx.setStudyInstanceUID(studyUID);
            ctx.setRequestAttributes(requestAttrs);
            ctx.setReasonForModification(reasonForModification);
            ctx.setSourceOfPreviousValues(sourceOfPreviousValues);
            studyService.updateStudyRequest(ctx);
            rsForward.forward(RSOperation.UpdateStudyRequest, arcAE, request, requestAttrs);
            return Response.accepted().build();
        } catch (StudyMissingException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @PUT
    @Path("/studies/{study}/series/{series}/request")
    @Consumes("application/dicom+json,application/json")
    @Produces("application/json")
    public Response updateSeriesRequestAttrs(
            @PathParam("study") String studyUID,
            @PathParam("series") String seriesUID,
            InputStream in) {
        ArchiveAEExtension arcAE = getArchiveAE();
        if (arcAE == null)
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);

        validateAcceptedUserRoles(arcAE);
        if (aet.equals(arcAE.getApplicationEntity().getAETitle()))
            validateWebAppServiceClass();

        final List<Attributes> requestAttrs = toRequestAttributes(in);
        try {
            StudyMgtContext ctx = studyService.createStudyMgtContextWEB(
                    HttpServletRequestInfo.valueOf(request), arcAE.getApplicationEntity());
            ctx.setStudyInstanceUID(studyUID);
            ctx.setSeriesInstanceUID(seriesUID);
            ctx.setRequestAttributes(requestAttrs);
            ctx.setReasonForModification(reasonForModification);
            ctx.setSourceOfPreviousValues(sourceOfPreviousValues);
            studyService.updateSeriesRequest(ctx);
            rsForward.forward(RSOperation.UpdateSeriesRequest, arcAE, request, requestAttrs);
            return Response.accepted().build();
        } catch (StudyMissingException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @PUT
    @Path("/studies/{StudyInstanceUID}/access/{accessControlID}")
    public Response updateStudyAccessControlID(
            @PathParam("StudyInstanceUID") String studyUID,
            @PathParam("accessControlID") String accessControlID) {
        return updateAccessControlID(RSOperation.UpdateStudyAccessControlID, studyUID, null, accessControlID);
    }

    @PUT
    @Path("/studies/{StudyInstanceUID}/series/{SeriesInstanceUID}/access/{accessControlID}")
    public Response updateSeriesAccessControlID(
            @PathParam("StudyInstanceUID") String studyUID,
            @PathParam("SeriesInstanceUID") String seriesUID,
            @PathParam("accessControlID") String accessControlID) {
        return updateAccessControlID(RSOperation.UpdateSeriesAccessControlID, studyUID, seriesUID, accessControlID);
    }

    private Response updateAccessControlID(RSOperation rsOp, String studyUID, String seriesUID, String accessControlID) {
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
            ctx.setSeriesInstanceUID(seriesUID);
            ctx.setAccessControlID("null".equals(accessControlID) ? "*" :  accessControlID);
            studyService.updateAccessControlID(ctx);
            rsForward.forward(rsOp, arcAE, null, request);
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

        Collection<IDWithIssuer> pids = IDWithIssuer.pidsOf(queryKeys);
        try {
            PatientMgtContext ctx = patientService.createPatientMgtContextWEB(HttpServletRequestInfo.valueOf(request));
            ctx.setAttributeUpdatePolicy(Attributes.UpdatePolicy.REPLACE);
            ctx.setPatientIDs(pids);
            ctx.setArchiveAEExtension(arcAE);
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

    private Response updateExpirationDate(
            RSOperation op, String studyUID, String seriesUID, String expirationDate,
            String expirationExporterID, String freezeExpirationDate) {
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
            return errResponse("Expiration date [" + expirationDate + "] cannot be parsed.",
                    Response.Status.BAD_REQUEST);
        } catch (StudyMissingException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
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

    private List<Attributes> toRequestAttributes(InputStream in) {
        try {
            List<Attributes> items = new ArrayList<>();
            PushbackInputStream pushbackInputStream = new PushbackInputStream(in);
            int ch1 = pushbackInputStream.read();
            if (ch1 == -1) return Collections.emptyList();
            pushbackInputStream.unread(ch1);
            new JSONReader(Json.createParser(new InputStreamReader(pushbackInputStream, StandardCharsets.UTF_8)))
                    .readDatasets((fmi, dataset) -> items.add(dataset));
            return items;
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
