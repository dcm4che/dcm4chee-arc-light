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
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.*;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4che3.util.TagUtils;
import org.dcm4che3.util.UIDUtils;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.query.scu.CFindSCU;
import org.dcm4chee.arc.query.util.QueryAttributes;
import org.dcm4chee.arc.ups.UPSContext;
import org.dcm4chee.arc.ups.UPSService;
import org.dcm4chee.arc.ups.UPSUtils;
import org.dcm4chee.arc.validation.constraints.InvokeValidate;
import org.dcm4chee.arc.validation.constraints.ValidValueOf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since July 2020
 */
@RequestScoped
@Path("aets/{AETitle}/dimse/{moveSCP}")
@InvokeValidate(type = UpsDimseRS.class)
public class UpsDimseRS {
    private static final Logger LOG = LoggerFactory.getLogger(UpsDimseRS.class);

    @Context
    private HttpServletRequest request;

    @Context
    private UriInfo uriInfo;

    @Context
    private HttpHeaders headers;

    @Inject
    private Device device;

    @Inject
    private UPSService upsService;

    @Inject
    private IApplicationEntityCache aeCache;

    @Inject
    private CFindSCU findSCU;

    @PathParam("AETitle")
    private String aet;

    @PathParam("moveSCP")
    private String moveSCP;

    @QueryParam("upsLabel")
    private String upsLabel;

    @QueryParam("upsScheduledTime")
    private String upsScheduledTime;

    @QueryParam("fuzzymatching")
    @Pattern(regexp = "true|false")
    private String fuzzymatching;

    @QueryParam("priority")
    @Pattern(regexp = "0|1|2")
    private String priority;

    @QueryParam("SplitStudyDateRange")
    @ValidValueOf(type = Duration.class)
    private String splitStudyDateRange;

    @POST
    @Path("/studies/workitems")
    @Produces("application/json")
    public Response upsMatchingStudies(InputStream in) {
        return upsMatching(QueryRetrieveLevel2.STUDY, null, null, in);
    }

    @POST
    @Path("/studies/{studyInstanceUID}/series/workitems")
    @Produces("application/json")
    public Response upsMatchingSeries(
            @PathParam("studyInstanceUID") String studyInstanceUID,
            InputStream in) {
        return upsMatching(QueryRetrieveLevel2.SERIES, studyInstanceUID, null, in);
    }

    @POST
    @Path("/studies/{studyInstanceUID}/series/{seriesInstanceUID}/instances/workitems")
    @Produces("application/json")
    public Response upsMatchingInstances(
            @PathParam("studyInstanceUID") String studyInstanceUID,
            @PathParam("seriesInstanceUID") String seriesInstanceUID,
            InputStream in) {
        return upsMatching(QueryRetrieveLevel2.IMAGE, studyInstanceUID, seriesInstanceUID, in);
    }

    @POST
    @Path("/query:{findSCP}/studies/workitems")
    @Produces("application/json")
    public Response upsQueryFindSCPMatchingStudies(
            @PathParam("findSCP") String findSCP,
            InputStream in) {
        return upsMatching(QueryRetrieveLevel2.STUDY, findSCP, null, null, in);
    }

    @POST
    @Path("/query:{findSCP}/studies/{studyInstanceUID}/series/workitems")
    @Produces("application/json")
    public Response upsQueryFindSCPMatchingSeries(
            @PathParam("findSCP") String findSCP,
            @PathParam("studyInstanceUID") String studyInstanceUID,
            InputStream in) {
        return upsMatching(QueryRetrieveLevel2.SERIES, findSCP, studyInstanceUID, null, in);
    }

    @POST
    @Path("/query:{findSCP}/studies/{studyInstanceUID}/series/{seriesInstanceUID}/instances/workitems")
    @Produces("application/json")
    public Response upsQueryFindSCPMatchingInstances(
            @PathParam("findSCP") String findSCP,
            @PathParam("studyInstanceUID") String studyInstanceUID,
            @PathParam("seriesInstanceUID") String seriesInstanceUID,
            InputStream in) {
        return upsMatching(QueryRetrieveLevel2.IMAGE, findSCP, studyInstanceUID, seriesInstanceUID, in);
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

    private Response upsMatching(
            QueryRetrieveLevel2 level, String studyInstanceUID, String seriesInstanceUID, InputStream in) {
        return upsMatching(level, moveSCP, studyInstanceUID, seriesInstanceUID, in);
    }

    private Response upsMatching(QueryRetrieveLevel2 level, String queryAET,
                                 String studyInstanceUID, String seriesInstanceUID, InputStream in) {
        InputType inputType = InputType.valueOf(headers.getMediaType());
        if (inputType == null)
            return notAcceptable();

        try {
            aeCache.findApplicationEntity(moveSCP);
            ArchiveAEExtension arcAE = getArchiveAE();
            Attributes upsTemplateAttrs = inputType.parse(in);
            upsTemplateAttrs.setDate(Tag.ScheduledProcedureStepStartDateTime, VR.DT, scheduledTime());
            if (upsLabel != null)
                upsTemplateAttrs.setString(Tag.ProcedureStepLabel, VR.LO, upsLabel);

            if (queryAET != null && !queryAET.equals(moveSCP))
                aeCache.findApplicationEntity(queryAET);

            Attributes keys = queryKeys(level, studyInstanceUID, seriesInstanceUID);
            EnumSet<QueryOption> queryOptions = EnumSet.of(QueryOption.DATETIME);
            if (Boolean.parseBoolean(fuzzymatching))
                queryOptions.add(QueryOption.FUZZY);
            Association as = null;
            String warning;
            AtomicInteger count = new AtomicInteger();
            Response.Status rspStatus = Response.Status.BAD_GATEWAY;

            int matches = 0;
            try {
                as = findSCU.openAssociation(
                                arcAE.getApplicationEntity(),
                                queryAET,
                                UID.StudyRootQueryRetrieveInformationModelFind,
                                queryOptions);
                DimseRSP dimseRSP = findSCU.query(
                        as, parseInt(priority, 0), keys, 0, 1, splitStudyDateRange());
                dimseRSP.next();
                int status;
                Attributes ups = new Attributes(upsTemplateAttrs);
                do {
                    status = dimseRSP.getCommand().getInt(Tag.Status, -1);
                    if (Status.isPending(status)) {
                        ups = studyInstanceUID == null ? new Attributes(upsTemplateAttrs) : ups;
                        UPSUtils.updateUPSAttributes(
                                ups, dimseRSP.getDataset(), studyInstanceUID, seriesInstanceUID, moveSCP);
                        matches++;
                        if (studyInstanceUID == null)
                            createUPS(arcAE, ups, count);
                    }
                } while (dimseRSP.next());
                if (matches > 0 && studyInstanceUID != null)
                    createUPS(arcAE, ups, count);
                warning = warning(status);
            } catch (IllegalStateException | IllegalArgumentException | ConfigurationException e) {
                rspStatus = Response.Status.NOT_FOUND;
                warning = e.getMessage();
            } catch (Exception e) {
                warning = e.getMessage();
            } finally {
                if (as != null)
                    try {
                        as.release();
                    } catch (IOException e) {
                        LOG.info("{}: Failed to release association:\\n", as, e);
                    }
            }
            if (warning == null && count.get() > 0)
                return Response.accepted(count(count.get())).build();

            if (count.get() == 0) {
                warning = "No matching Instances found. No Workitem was created.";
                rspStatus = Response.Status.NO_CONTENT;
            }

            Response.ResponseBuilder builder = Response.status(rspStatus)
                                                       .header("Warning", warning);
            if (count.get() > 0)
                builder.entity(count(count.get()));
            return builder.build();
        } catch (IllegalStateException | IllegalArgumentException | ConfigurationException e) {
            return errResponse(Response.Status.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
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
        if (studyUIDField < 1)
            return errResponse(Response.Status.BAD_REQUEST,
                    "CSV field for Study Instance UID should be greater than or equal to 1");

        int patientIDField = 0;
        if (csvPatientIDField != null && (patientIDField = patientIDField(csvPatientIDField)) < 1)
            return errResponse(Response.Status.BAD_REQUEST,
                    "CSV field for Patient ID should be greater than or equal to 1");

        try {
            aeCache.findApplicationEntity(moveSCP);
            ArchiveAEExtension arcAE = getArchiveAE();
            UpsCSV upsCSV = new UpsCSV(upsService,
                                        HttpServletRequestInfo.valueOf(request).setContentType(headers),
                                        arcAE,
                                        upsTemplateAttrs(upsTemplateUID, arcAE));
            return upsCSV.createWorkitems(studyUIDField, patientIDField, moveSCP, in);
        } catch (DicomServiceException e) {
            return errResponse(UpsDimseRS::createFailed, e);
        } catch (IllegalStateException | ConfigurationException e) {
            return errResponse(Response.Status.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private Attributes queryKeys(QueryRetrieveLevel2 level, String studyInstanceUID, String seriesInstanceUID) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
        QueryAttributes queryAttributes = new QueryAttributes(uriInfo, null);
        queryAttributes.addReturnTags(level.uniqueKey());
        queryAttributes.addReturnTags(arcDev.getAttributeFilter(Entity.Patient).getSelection());
        if (level == QueryRetrieveLevel2.IMAGE)
            queryAttributes.addReturnTags(arcDev.getAttributeFilter(Entity.Instance).getSelection());
        Attributes keys = queryAttributes.getQueryKeys();
        keys.setString(Tag.QueryRetrieveLevel, VR.CS, level.name());
        if (studyInstanceUID != null)
            keys.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUID);
        if (seriesInstanceUID != null)
            keys.setString(Tag.SeriesInstanceUID, VR.UI, seriesInstanceUID);
        return keys;
    }

    public void validate() {
        LOG.info("Process {} {} from {}@{}",
                request.getMethod(), toString(), request.getRemoteUser(), request.getRemoteHost());
        new QueryAttributes(uriInfo, null);
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

    private ArchiveAEExtension getArchiveAE() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        if (ae == null || !ae.isInstalled())
            throw new WebApplicationException("No such Archive Application Entity: " + aet, Response.Status.NOT_FOUND);

        return ae.getAEExtensionNotNull(ArchiveAEExtension.class);
    }

    private Duration splitStudyDateRange() {
        return splitStudyDateRange != null ? Duration.valueOf(splitStudyDateRange) : null;
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

    private static int parseInt(String s, int defval) {
        return s != null ? Integer.parseInt(s) : defval;
    }

    private String count(int count) {
        return "{\"count\":" + count + '}';
    }

    private String warning(int status) {
        switch (status) {
            case Status.Success:
                return null;
            case Status.OutOfResources:
                return "A700: Refused: Out of Resources";
            case Status.IdentifierDoesNotMatchSOPClass:
                return "A900: Identifier does not match SOP Class";
        }
        return TagUtils.shortToHexString(status)
                + ((status & Status.UnableToProcess) == Status.UnableToProcess
                ? ": Unable to Process"
                : ": Unexpected status code");
    }

    private int patientIDField(String csvPatientIDField) {
        try {
            return Integer.parseInt(csvPatientIDField);
        } catch (NumberFormatException e) {
            LOG.info("CSV Patient ID Field {} cannot be parsed", csvPatientIDField);
        }
        return 0;
    }

    @Override
    public String toString() {
        String requestURI = request.getRequestURI();
        String queryString = request.getQueryString();
        return queryString == null ? requestURI : requestURI + '?' + queryString;
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
