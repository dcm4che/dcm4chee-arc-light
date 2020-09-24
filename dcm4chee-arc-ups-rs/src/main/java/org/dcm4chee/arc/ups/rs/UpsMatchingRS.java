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
 * Portions created by the Initial Developer are Copyright (C) 2015-2020
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

import org.dcm4che3.data.*;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4che3.util.UIDUtils;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.entity.ExpirationState;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.query.Query;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.query.util.QueryAttributes;
import org.dcm4chee.arc.ups.UPSContext;
import org.dcm4chee.arc.ups.UPSService;
import org.dcm4chee.arc.ups.UPSUtils;
import org.dcm4chee.arc.validation.constraints.InvokeValidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since July 2020
 */
@RequestScoped
@Path("aets/{AETitle}/rs")
@InvokeValidate(type = UpsMatchingRS.class)
public class UpsMatchingRS {

    private static final Logger LOG = LoggerFactory.getLogger(UpsMatchingRS.class);

    @PathParam("AETitle")
    private String aet;

    @Context
    private HttpServletRequest request;

    @Context
    private UriInfo uriInfo;

    @Context
    private HttpHeaders headers;

    @Inject
    private Device device;

    @Inject
    private QueryService queryService;

    @Inject
    private UPSService upsService;

    @QueryParam("upsLabel")
    private String upsLabel;

    @QueryParam("upsScheduledTime")
    private String upsScheduledTime;

    @QueryParam("fuzzymatching")
    @Pattern(regexp = "true|false")
    private String fuzzymatching;

    @QueryParam("patientVerificationStatus")
    @Pattern(regexp = "UNVERIFIED|VERIFIED|NOT_FOUND|VERIFICATION_FAILED")
    private String patientVerificationStatus;

    @QueryParam("incomplete")
    @Pattern(regexp = "true|false")
    private String incomplete;

    @QueryParam("retrievefailed")
    @Pattern(regexp = "true|false")
    private String retrievefailed;

    @QueryParam("storageVerificationFailed")
    @Pattern(regexp = "true|false")
    private String storageVerificationFailed;

    @QueryParam("metadataUpdateFailed")
    @Pattern(regexp = "true|false")
    private String metadataUpdateFailed;

    @QueryParam("compressionfailed")
    @Pattern(regexp = "true|false")
    private String compressionfailed;

    @QueryParam("StudySizeInKB")
    @Pattern(regexp = "\\d{1,9}(-\\d{0,9})?|-\\d{1,9}")
    private String studySizeInKB;

    @QueryParam("ExternalRetrieveAET")
    private String externalRetrieveAET;

    @QueryParam("ExternalRetrieveAET!")
    private String externalRetrieveAETNot;

    @QueryParam("ExpirationDate")
    private String expirationDate;

    @QueryParam("ExpirationState")
    @Pattern(regexp = "UPDATEABLE|FROZEN|REJECTED|EXPORT_SCHEDULED|FAILED_TO_EXPORT|FAILED_TO_REJECT")
    private String expirationState;

    @QueryParam("allOfModalitiesInStudy")
    @Pattern(regexp = "true|false")
    private String allOfModalitiesInStudy;

    @QueryParam("storageID")
    private String storageID;

    @QueryParam("storageClustered")
    @Pattern(regexp = "true|false")
    private String storageClustered;

    @QueryParam("storageExported")
    @Pattern(regexp = "true|false")
    private String storageExported;

    @POST
    @Path("/studies/workitems")
    @Produces("application/json")
    public Response upsMatchingStudies(InputStream in) {
        return createWorkitemsMatching(
                "upsMatchingStudies", QueryRetrieveLevel2.STUDY, null, null, in);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}/series/workitems")
    @Produces("application/json")
    public Response upsMatchingStudies(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            InputStream in) {
        return createWorkitemsMatching(
                "upsMatchingSeriesOfStudy", QueryRetrieveLevel2.SERIES, studyInstanceUID, null, in);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}/series/{SeriesInstanceUID}/instances/workitems")
    @Produces("application/json")
    public Response upsMatchingStudies(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @PathParam("SeriesInstanceUID") String seriesInstanceUID,
            InputStream in) {
        return createWorkitemsMatching(
                "upsMatchingInstancesOfSeries", QueryRetrieveLevel2.IMAGE, studyInstanceUID, seriesInstanceUID, in);
    }

    @POST
    @Path("/studies/csv:{field}/workitems/{upsTemplateUID}")
    public Response createWorkitems(
            @PathParam("field") int field,
            @PathParam("upsTemplateUID") String upsTemplateUID,
            @QueryParam("csvPatientID") String csvPatientIDField,
            InputStream in) {
        return createWorkitemsFromCSV(field, upsTemplateUID, csvPatientIDField, in);
    }
    
    private Response createWorkitemsMatching(
            String method, QueryRetrieveLevel2 qrlevel, String studyIUID, String seriesIUID, InputStream in) {
        InputType inputType = InputType.valueOf(headers.getMediaType());
        if (inputType == null)
            return notAcceptable();

        try {
            ArchiveDeviceExtension arcDev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
            ArchiveAEExtension arcAE = getArchiveAE();
            Attributes upsTemplateAttrs = inputType.parse(in);
            upsTemplateAttrs.setDate(Tag.ScheduledProcedureStepStartDateTime, VR.DT, scheduledTime());
            if (upsLabel != null)
                upsTemplateAttrs.setString(Tag.ProcedureStepLabel, VR.LO, upsLabel);

            QueryContext ctx = queryContext(method, qrlevel, studyIUID, seriesIUID, arcAE.getApplicationEntity());
            String warning = null;
            AtomicInteger count = new AtomicInteger();
            Response.Status status = Response.Status.ACCEPTED;
            Attributes ups = new Attributes(upsTemplateAttrs);
            int matches = 0;
            try (Query query = queryService.createQuery(ctx)) {
                try {
                    query.executeQuery(arcDev.getQueryFetchSize());
                    while (query.hasMoreMatches()) {
                        Attributes match = query.nextMatch();
                        if (match == null)
                            continue;

                        ups = studyIUID == null ? new Attributes(upsTemplateAttrs) : ups;
                        UPSUtils.updateUPSAttributes(ups, match, studyIUID, seriesIUID, aet);
                        matches++;
                        if (studyIUID == null)
                            createUPS(arcAE, ups, count);
                    }
                    if (matches > 0 && studyIUID != null)
                        createUPS(arcAE, ups, count);
                } catch (Exception e) {
                    warning = e.getMessage();
                    status = Response.Status.INTERNAL_SERVER_ERROR;
                }
            }

            if (count.get() == 0) {
                warning = "No matching Instances found. No Workitem was created.";
                status = Response.Status.NO_CONTENT;
            }

            Response.ResponseBuilder builder = Response.status(status);
            if (warning != null) {
                LOG.warn("Response {} caused by {}", status, warning);
                builder.header("Warning", warning);
            }
            return builder.entity("{\"count\":" + count + '}').build();
        } catch (IllegalStateException e) {
            return errResponse(Response.Status.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private Attributes upsTemplateAttrs(String upsTemplateUID, ArchiveAEExtension arcAE) throws DicomServiceException {
        UPSContext ctx = upsService.newUPSContext(HttpServletRequestInfo.valueOf(request), arcAE);
        ctx.setUPSInstanceUID(upsTemplateUID);
        Attributes upsAttrs = upsService.findUPS(ctx).getAttributes();
        upsAttrs.setDate(Tag.ScheduledProcedureStepStartDateTime, VR.DT, scheduledTime());
        if (upsLabel != null)
            upsAttrs.setString(Tag.ProcedureStepLabel, VR.LO, upsLabel);
        return upsAttrs;
    }

    private void createUPS(ArchiveAEExtension arcAE, Attributes ups, AtomicInteger count) {
        UPSContext ctx = upsService.newUPSContext(HttpServletRequestInfo.valueOf(request), arcAE);
        ctx.setUPSInstanceUID(UIDUtils.createUID());
        ctx.setAttributes(ups);
        try {
            upsService.createUPS(ctx);
            count.getAndIncrement();
        } catch (DicomServiceException e) {
            LOG.info("Failed to create UPS record for Study[uid={}]\n",
                    ups.getSequence(Tag.InputInformationSequence).get(0).getString(Tag.StudyInstanceUID), e);
        }
    }

    private Response createWorkitemsFromCSV(
            int studyUIDField, String upsTemplateUID, String csvPatientIDField, InputStream in) {
        Response.Status status = Response.Status.BAD_REQUEST;
        if (studyUIDField < 1)
            return errResponse(status, "CSV field for Study Instance UID should be greater than or equal to 1");

        int patientIDField = 0;
        if (csvPatientIDField != null && (patientIDField = patientIDField(csvPatientIDField)) < 1)
            return errResponse(status, "CSV field for Patient ID should be greater than or equal to 1");

        try {
            ArchiveAEExtension arcAE = getArchiveAE();
            UpsCSV upsCSV = new UpsCSV(upsService,
                                        HttpServletRequestInfo.valueOf(request).setContentType(headers),
                                        arcAE,
                                        upsTemplateAttrs(upsTemplateUID, arcAE));
            return upsCSV.createWorkitems(studyUIDField, patientIDField, null, in);
        } catch (DicomServiceException e) {
            return errResponse(UpsMatchingRS::createFailed, e);
        } catch (IllegalStateException e) {
            return errResponse(Response.Status.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private ArchiveAEExtension getArchiveAE() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        if (ae == null || !ae.isInstalled())
            throw new WebApplicationException("No such Archive Application Entity: " + aet, Response.Status.NOT_FOUND);

        return ae.getAEExtensionNotNull(ArchiveAEExtension.class);
    }

    private int patientIDField(String csvPatientIDField) {
        try {
            return Integer.parseInt(csvPatientIDField);
        } catch (NumberFormatException e) {
            LOG.info("CSV Patient ID Field {} cannot be parsed", csvPatientIDField);
        }
        return 0;
    }

    private QueryContext queryContext(
            String method, QueryRetrieveLevel2 qrlevel, String studyInstanceUID, String seriesInstanceUID,
            ApplicationEntity ae) {
        QueryContext ctx = queryService.newQueryContextQIDO(
                HttpServletRequestInfo.valueOf(request), method, ae, queryParam(ae));
        ctx.setQueryRetrieveLevel(qrlevel);
        QueryAttributes queryAttrs = new QueryAttributes(uriInfo, null);
        Attributes keys = queryAttrs.getQueryKeys();
        IDWithIssuer idWithIssuer = IDWithIssuer.pidOf(keys);
        if (idWithIssuer != null)
            ctx.setPatientIDs(idWithIssuer);
        if (studyInstanceUID != null)
            keys.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUID);
        if (seriesInstanceUID != null)
            keys.setString(Tag.SeriesInstanceUID, VR.UI, seriesInstanceUID);
        ctx.setQueryKeys(keys);
        Attributes returnKeys = new Attributes(3);
        returnKeys.setNull(Tag.StudyInstanceUID, VR.UI);
        switch (qrlevel) {
            case IMAGE:
                returnKeys.setNull(Tag.SOPInstanceUID, VR.UI);
            case SERIES:
                returnKeys.setNull(Tag.SeriesInstanceUID, VR.UI);
        }
        ctx.setReturnKeys(returnKeys);
        return ctx;
    }

    private org.dcm4chee.arc.query.util.QueryParam queryParam(ApplicationEntity ae) {
        org.dcm4chee.arc.query.util.QueryParam queryParam = new org.dcm4chee.arc.query.util.QueryParam(ae);
        queryParam.setCombinedDatetimeMatching(true);
        queryParam.setFuzzySemanticMatching(Boolean.parseBoolean(fuzzymatching));
        queryParam.setIncomplete(Boolean.parseBoolean(incomplete));
        queryParam.setRetrieveFailed(Boolean.parseBoolean(retrievefailed));
        queryParam.setStorageVerificationFailed(Boolean.parseBoolean(storageVerificationFailed));
        queryParam.setMetadataUpdateFailed(Boolean.parseBoolean(metadataUpdateFailed));
        queryParam.setCompressionFailed(Boolean.parseBoolean(compressionfailed));
        queryParam.setExternalRetrieveAET(externalRetrieveAET);
        queryParam.setExternalRetrieveAETNot(externalRetrieveAETNot);
        queryParam.setExpirationDate(expirationDate);
        if (patientVerificationStatus != null)
            queryParam.setPatientVerificationStatus(Patient.VerificationStatus.valueOf(patientVerificationStatus));
        queryParam.setStudySizeRange(studySizeInKB);
        if (expirationState != null)
            queryParam.setExpirationState(ExpirationState.valueOf(expirationState));
        return queryParam;
    }

    public void validate() {
        LOG.info("Process {} {} from {}@{}",
                request.getMethod(), toString(), request.getRemoteUser(), request.getRemoteHost());
        new QueryAttributes(uriInfo, null);
    }

    private Date scheduledTime() {
        if (upsScheduledTime != null)
            try {
                return new SimpleDateFormat("yyyyMMddhhmmss").parse(upsScheduledTime);
            } catch (Exception e) {
                LOG.info(e.getMessage());
            }
        return new Date();
    }

    private static Response.Status createFailed(int status) {
        switch (status) {
            case Status.UPSDoesNotExist:
                return Response.Status.NOT_FOUND;
            case Status.DuplicateSOPinstance:
                return Response.Status.CONFLICT;
            case Status.UPSNotScheduled:
            case Status.NoSuchAttribute:
            case Status.MissingAttribute:
            case Status.MissingAttributeValue:
            case Status.InvalidAttributeValue:
                return Response.Status.BAD_REQUEST;
        }
        return Response.Status.INTERNAL_SERVER_ERROR;
    }

    private Response notAcceptable() {
        LOG.info("Response Status : Not Acceptable. Content Type in request : \n{}", headers.getMediaType());
        return Response.notAcceptable(
                Variant.mediaTypes(
                        MediaTypes.APPLICATION_DICOM_JSON_TYPE, MediaTypes.APPLICATION_DICOM_XML_TYPE)
                        .build())
                .build();
    }

    private Response errResponse(IntFunction<Response.Status> httpStatusOf, DicomServiceException e) {
        return errResponse(httpStatusOf.apply(e.getStatus()), e.getMessage());
    }

    private Response errResponse(Response.Status status, String message) {
        return errResponseAsTextPlain("{\"errorMessage\":\"" + message + "\"}", status);
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

    @Override
    public String toString() {
        String requestURI = request.getRequestURI();
        String queryString = request.getQueryString();
        return queryString == null ? requestURI : requestURI + '?' + queryString;
    }
}
