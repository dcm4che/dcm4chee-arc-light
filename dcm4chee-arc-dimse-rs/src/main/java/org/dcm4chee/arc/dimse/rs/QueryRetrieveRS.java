/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 *  The contents of this file are subject to the Mozilla Public License Version
 *  1.1 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  http://www.mozilla.org/MPL/
 *
 *  Software distributed under the License is distributed on an "AS IS" basis,
 *  WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 *  for the specific language governing rights and limitations under the
 *  License.
 *
 *  The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 *  Java(TM), hosted at https://github.com/dcm4che.
 *
 *  The Initial Developer of the Original Code is
 *  J4Care.
 *  Portions created by the Initial Developer are Copyright (C) 2015-2019
 *  the Initial Developer. All Rights Reserved.
 *
 *  Contributor(s):
 *  See @authors listed below
 *
 *  Alternatively, the contents of this file may be used under the terms of
 *  either the GNU General Public License Version 2 or later (the "GPL"), or
 *  the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 *  in which case the provisions of the GPL or the LGPL are applicable instead
 *  of those above. If you wish to allow use of your version of this file only
 *  under the terms of either the GPL or the LGPL, and not to allow others to
 *  use your version of this file under the terms of the MPL, indicate your
 *  decision by deleting the provisions above and replace them with the notice
 *  and other provisions required by the GPL or the LGPL. If you do not delete
 *  the provisions above, a recipient may use your version of this file under
 *  the terms of any one of the MPL, the GPL or the LGPL.
 *
 */

package org.dcm4chee.arc.dimse.rs;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.IApplicationEntityCache;
import org.dcm4che3.conf.api.IDeviceCache;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.*;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.util.TagUtils;
import org.dcm4che3.util.UIDUtils;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.Duration;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.qmgt.QueueSizeLimitExceededException;
import org.dcm4chee.arc.query.scu.CFindSCU;
import org.dcm4chee.arc.query.util.QueryAttributes;
import org.dcm4chee.arc.retrieve.ExternalRetrieveContext;
import org.dcm4chee.arc.retrieve.mgt.RetrieveManager;
import org.dcm4chee.arc.validation.constraints.InvokeValidate;
import org.dcm4chee.arc.validation.constraints.ValidValueOf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.*;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Function;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jul 2017
 */
@RequestScoped
@Path("aets/{AETitle}/dimse/{movescp}")
@InvokeValidate(type = QueryRetrieveRS.class)
public class QueryRetrieveRS {

    private static final Logger LOG = LoggerFactory.getLogger(QueryRetrieveRS.class);

    @Context
    private HttpServletRequest request;

    @Context
    private UriInfo uriInfo;

    @Inject
    private Device device;

    @Inject
    private IDeviceCache deviceCache;

    @PathParam("AETitle")
    private String aet;

    @PathParam("movescp")
    private String movescp;

    @QueryParam("batchID")
    private String batchID;

    @QueryParam("dicomDeviceName")
    private String deviceName;

    @QueryParam("dcmQueueName")
    @DefaultValue("Retrieve1")
    @Pattern(regexp =
            "Retrieve1|" +
            "Retrieve2|" +
            "Retrieve3|" +
            "Retrieve4|" +
            "Retrieve5|" +
            "Retrieve6|" +
            "Retrieve7|" +
            "Retrieve8|" +
            "Retrieve9|" +
            "Retrieve10|" +
            "Retrieve11|" +
            "Retrieve12|" +
            "Retrieve13")
    private String queueName;

    @QueryParam("fuzzymatching")
    @Pattern(regexp = "true|false")
    private String fuzzymatching;

    @QueryParam("priority")
    @Pattern(regexp = "0|1|2")
    private String priority;

    @QueryParam("SplitStudyDateRange")
    @ValidValueOf(type = Duration.class)
    private String splitStudyDateRange;

    @Inject
    private CFindSCU findSCU;

    @Inject
    private RetrieveManager retrieveManager;

    @Inject
    private IApplicationEntityCache aeCache;

    @HeaderParam("Content-Type")
    private MediaType contentType;

    @Override
    public String toString() {
        return request.getRequestURI() + '?' + request.getQueryString();
    }

    public void validate() {
        logRequest();
        new QueryAttributes(uriInfo, null);
    }

    @POST
    @Path("/query:{QueryAET}/studies/export/dicom:{DestinationAET}")
    @Produces("application/json")
    public Response queryRetrieveMatchingStudies(
            @PathParam("QueryAET") String queryAET,
            @PathParam("DestinationAET") String destAET) {
        return process(QueryRetrieveLevel2.STUDY, null, null, queryAET, destAET,
                this::scheduleRetrieveTask);
    }

    @POST
    @Path("/query:{QueryAET}/studies/{StudyInstanceUID}/series/export/dicom:{DestinationAET}")
    @Produces("application/json")
    public Response queryRetrieveMatchingSeries(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @PathParam("QueryAET") String queryAET,
            @PathParam("DestinationAET") String destAET)
    {
        return process(QueryRetrieveLevel2.SERIES, studyInstanceUID, null, queryAET, destAET,
                this::scheduleRetrieveTask);
    }

    @POST
    @Path("/query:{QueryAET}/studies/{StudyInstanceUID}/series/{SeriesInstanceUID}/instances/export/dicom:{DestinationAET}")
    @Produces("application/json")
    public Response queryRetrieveMatchingInstances(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @PathParam("SeriesInstanceUID") String seriesInstanceUID,
            @PathParam("QueryAET") String queryAET,
            @PathParam("DestinationAET") String destAET)
    {
        return process(QueryRetrieveLevel2.IMAGE, studyInstanceUID, seriesInstanceUID, queryAET, destAET,
                this::scheduleRetrieveTask);
    }

    @POST
    @Path("/query:{QueryAET}/studies/mark4retrieve/dicom:{DestinationAET}")
    @Produces("application/json")
    public Response markMatchingStudiesForQueryRetrieve(
            @PathParam("QueryAET") String queryAET,
            @PathParam("DestinationAET") String destAET) {
        return process(QueryRetrieveLevel2.STUDY, null, null, queryAET, destAET,
                this::createRetrieveTask);
    }

    @POST
    @Path("/query:{QueryAET}/studies/{StudyInstanceUID}/series/mark4retrieve/dicom:{DestinationAET}")
    @Produces("application/json")
    public Response markMatchingSeriesForQueryRetrieve(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @PathParam("QueryAET") String queryAET,
            @PathParam("DestinationAET") String destAET)
    {
        return process(QueryRetrieveLevel2.SERIES, studyInstanceUID, null, queryAET, destAET,
                this::createRetrieveTask);
    }

    @POST
    @Path("/query:{QueryAET}/studies/{StudyInstanceUID}/series/{SeriesInstanceUID}/instances/mark4retrieve/dicom:{DestinationAET}")
    @Produces("application/json")
    public Response markMatchingInstancesForQueryRetrieve(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @PathParam("SeriesInstanceUID") String seriesInstanceUID,
            @PathParam("QueryAET") String queryAET,
            @PathParam("DestinationAET") String destAET)
    {
        return process(QueryRetrieveLevel2.IMAGE, studyInstanceUID, seriesInstanceUID, queryAET, destAET,
                this::createRetrieveTask);
    }

    @POST
    @Path("/studies/export/dicom:{DestinationAET}")
    @Produces("application/json")
    public Response retrieveMatchingStudies(
            @PathParam("DestinationAET") String destAET) {
        return process(QueryRetrieveLevel2.STUDY, null, null, destAET,
                this::scheduleRetrieveTask);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}/series/export/dicom:{DestinationAET}")
    @Produces("application/json")
    public Response retrieveMatchingSeries(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @PathParam("DestinationAET") String destAET)
    {
        return process(QueryRetrieveLevel2.SERIES, studyInstanceUID, null, destAET,
                this::scheduleRetrieveTask);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}/series/{SeriesInstanceUID}/instances/export/dicom:{DestinationAET}")
    @Produces("application/json")
    public Response retrieveMatchingInstances(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @PathParam("SeriesInstanceUID") String seriesInstanceUID,
            @PathParam("DestinationAET") String destAET)
    {
        return process(QueryRetrieveLevel2.IMAGE, studyInstanceUID, seriesInstanceUID, destAET,
                this::scheduleRetrieveTask);
    }

    @POST
    @Path("/studies/mark4retrieve/dicom:{DestinationAET}")
    @Produces("application/json")
    public Response markMatchingStudiesForRetrieve(
            @PathParam("DestinationAET") String destAET) {
        return process(QueryRetrieveLevel2.STUDY, null, null, destAET,
                this::createRetrieveTask);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}/series/mark4retrieve/dicom:{DestinationAET}")
    @Produces("application/json")
    public Response markMatchingSeriesForRetrieve(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @PathParam("DestinationAET") String destAET)
    {
        return process(QueryRetrieveLevel2.SERIES, studyInstanceUID, null, destAET,
                this::createRetrieveTask);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}/series/{SeriesInstanceUID}/instances/mark4retrieve/dicom:{DestinationAET}")
    @Produces("application/json")
    public Response markMatchingInstancesForRetrieve(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @PathParam("SeriesInstanceUID") String seriesInstanceUID,
            @PathParam("DestinationAET") String destAET)
    {
        return process(QueryRetrieveLevel2.IMAGE, studyInstanceUID, seriesInstanceUID, destAET,
                this::createRetrieveTask);
    }

    @POST
    @Path("/studies/query:{QueryAET}/export/dicom:{DestinationAET}")
    @Produces("application/json")
    public Response retrieveMatchingStudiesLegacy(
            @PathParam("QueryAET") String queryAET,
            @PathParam("DestinationAET") String destAET) {
        return process(QueryRetrieveLevel2.STUDY, null, null, queryAET, destAET,
                this::scheduleRetrieveTask);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}/series/query:{QueryAET}/export/dicom:{DestinationAET}")
    @Produces("application/json")
    public Response retrieveMatchingSeriesLegacy(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @PathParam("QueryAET") String queryAET,
            @PathParam("DestinationAET") String destAET)
            {
        return process(QueryRetrieveLevel2.SERIES, studyInstanceUID, null, queryAET, destAET,
                this::scheduleRetrieveTask);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}/series/{SeriesInstanceUID}/instances/query:{QueryAET}/export/dicom:{DestinationAET}")
    @Produces("application/json")
    public Response retrieveMatchingInstancesLegacy(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @PathParam("SeriesInstanceUID") String seriesInstanceUID,
            @PathParam("QueryAET") String queryAET,
            @PathParam("DestinationAET") String destAET)
            {
        return process(QueryRetrieveLevel2.IMAGE, studyInstanceUID, seriesInstanceUID, queryAET, destAET,
                this::scheduleRetrieveTask);
    }

    @POST
    @Path("/studies/query:{QueryAET}/mark4retrieve/dicom:{DestinationAET}")
    @Produces("application/json")
    public Response markMatchingStudiesForRetrieveLegacy(
            @PathParam("QueryAET") String queryAET,
            @PathParam("DestinationAET") String destAET) {
        return process(QueryRetrieveLevel2.STUDY, null, null, queryAET, destAET,
                this::createRetrieveTask);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}/series/query:{QueryAET}/mark4retrieve/dicom:{DestinationAET}")
    @Produces("application/json")
    public Response markMatchingSeriesForRetrieveLegacy(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @PathParam("QueryAET") String queryAET,
            @PathParam("DestinationAET") String destAET)
    {
        return process(QueryRetrieveLevel2.SERIES, studyInstanceUID, null, queryAET, destAET,
                this::createRetrieveTask);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}/series/{SeriesInstanceUID}/instances/query:{QueryAET}/mark4retrieve/dicom:{DestinationAET}")
    @Produces("application/json")
    public Response markMatchingInstancesForRetrieveLegacy(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @PathParam("SeriesInstanceUID") String seriesInstanceUID,
            @PathParam("QueryAET") String queryAET,
            @PathParam("DestinationAET") String destAET)
    {
        return process(QueryRetrieveLevel2.IMAGE, studyInstanceUID, seriesInstanceUID, queryAET, destAET,
                this::createRetrieveTask);
    }

    @POST
    @Path("/studies/csv:{field}/mark4retrieve/dicom:{destinationAET}")
    @Consumes("text/csv")
    @Produces("application/json")
    public Response markMatchingStudiesFromCSVForRetrieve(
            @PathParam("field") int field,
            @PathParam("destinationAET") String destAET,
            InputStream in) {
        return processCSV(field, destAET, in, this::createRetrieveTask);
    }


    @POST
    @Path("/studies/csv:{field}/export/dicom:{destinationAET}")
    @Consumes("text/csv")
    @Produces("application/json")
    public Response retrieveMatchingStudiesFromCSV(
            @PathParam("field") int field,
            @PathParam("destinationAET") String destAET,
            InputStream in) {
        return processCSV(field, destAET, in, this::scheduleRetrieveTask);
    }

    private Response processCSV(int field, String destAET, InputStream in, Function<ExternalRetrieveContext, Integer> action) {
        try {
            validate(null);
            Response.Status status = Response.Status.BAD_REQUEST;
            if (field < 1)
                return errResponse(
                        "CSV field for Study Instance UID should be greater than or equal to 1", status);

            char csvDelimiter = csvDelimiter();
            priorityAsInt = parseInt(priority, 0);
            int count = 0;
            String warning = null;
            ArchiveDeviceExtension arcDev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
            int csvUploadChunkSize = arcDev.getCSVUploadChunkSize();
            List<String> studyUIDs = new ArrayList<>();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                String line = reader.readLine();
                while (line != null) {
                    if (line.chars().allMatch(Character::isWhitespace)) {
                        line = reader.readLine();
                        continue;
                    }

                    String studyUID = StringUtils.split(line, csvDelimiter)[field - 1].replaceAll("\"", "");
                    line = reader.readLine();
                    if (count == 0 && studyUID.chars().allMatch(Character::isLetter))
                        continue;

                    if (count > 0
                            || !arcDev.isValidateUID()
                            || validateUID(studyUID))
                        studyUIDs.add(studyUID);

                    if (studyUIDs.size() == csvUploadChunkSize || line == null) {
                        count += action.apply(createExtRetrieveCtx(destAET, studyUIDs.toArray(new String[0])));
                        studyUIDs.clear();
                    }
                }

                if (count == 0) {
                    warning = "Empty file or Incorrect field position or Not a CSV file or Invalid UIDs or Duplicate Retrieves suppressed.";
                    status = Response.Status.NO_CONTENT;
                }

            } catch (QueueSizeLimitExceededException e) {
                status = Response.Status.SERVICE_UNAVAILABLE;
                warning = e.getMessage();
            } catch (Exception e) {
                warning = e.getMessage();
                status = Response.Status.INTERNAL_SERVER_ERROR;
            }

            if (warning == null && count > 0)
                return Response.accepted(count(count)).build();

            LOG.warn("Response {} caused by {}", status, warning);
            Response.ResponseBuilder builder = Response.status(status)
                    .header("Warning", warning);
            if (count > 0)
                builder.entity(count(count));

            return builder.build();
        } catch (IllegalStateException | IllegalArgumentException | ConfigurationException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private boolean validateUID(String studyUID) {
        boolean valid = UIDUtils.isValid(studyUID);
        if (!valid)
            LOG.warn("Invalid UID in CSV file: " + studyUID);
        return valid;
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

    private int priorityAsInt;

    private static int parseInt(String s, int defval) {
        return s != null ? Integer.parseInt(s) : defval;
    }

    private char csvDelimiter() {
        return ("semicolon".equals(contentType.getParameters().get("delimiter"))) ? ';' : ',';
    }

    private Duration splitStudyDateRange() {
        return splitStudyDateRange != null ? Duration.valueOf(splitStudyDateRange) : null;
    }

    private Response process(QueryRetrieveLevel2 level, String studyInstanceUID, String seriesInstanceUID,
                             String destAET, Function<ExternalRetrieveContext, Integer> action) {
        return process(level, studyInstanceUID, seriesInstanceUID, movescp, destAET, action);
    }
    
    private Response process(QueryRetrieveLevel2 level, String studyInstanceUID, String seriesInstanceUID,
            String queryAET, String destAET, Function<ExternalRetrieveContext, Integer> action) {
        ApplicationEntity localAE = device.getApplicationEntity(aet, true);
        if (localAE == null || !localAE.isInstalled())
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);

        try {
            validate(queryAET);
            QueryAttributes queryAttributes = new QueryAttributes(uriInfo, null);
            queryAttributes.addReturnTags(level.uniqueKey());
            Attributes keys = queryAttributes.getQueryKeys();
            keys.setString(Tag.QueryRetrieveLevel, VR.CS, level.name());
            if (studyInstanceUID != null)
                keys.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUID);
            if (seriesInstanceUID != null)
                keys.setString(Tag.SeriesInstanceUID, VR.UI, seriesInstanceUID);
            EnumSet<QueryOption> queryOptions = EnumSet.of(QueryOption.DATETIME);
            if (Boolean.parseBoolean(fuzzymatching))
                queryOptions.add(QueryOption.FUZZY);
            Association as = null;
            String warning;
            int count = 0;
            Response.Status errorStatus = Response.Status.BAD_GATEWAY;
            try {
                as = findSCU.openAssociation(localAE, queryAET, UID.StudyRootQueryRetrieveInformationModelFIND, queryOptions);
                priorityAsInt = parseInt(priority, 0);
                DimseRSP dimseRSP = findSCU.query(as, priorityAsInt, keys, 0, 1, splitStudyDateRange());
                dimseRSP.next();
                int status;
                do {
                    status = dimseRSP.getCommand().getInt(Tag.Status, -1);
                    if (Status.isPending(status))
                        count += action.apply(createExtRetrieveCtx(destAET, dimseRSP));
                } while (dimseRSP.next());
                warning = warning(status);
            } catch (IllegalStateException | IllegalArgumentException | ConfigurationException e) {
                errorStatus = Response.Status.NOT_FOUND;
                warning = e.getMessage();
            } catch (QueueSizeLimitExceededException e) {
                errorStatus = Response.Status.SERVICE_UNAVAILABLE;
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
            if (warning == null)
                return Response.accepted(count(count)).build();

            LOG.warn("Response {} caused by {}", errorStatus, warning);
            Response.ResponseBuilder builder = Response.status(errorStatus)
                    .header("Warning", warning);
            if (count > 0)
                builder.entity(count(count));
            return builder.build();
        } catch (IllegalStateException | IllegalArgumentException | ConfigurationException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private void validate(String queryAET) throws ConfigurationException {
        if (queryAET != null && !queryAET.equals(movescp))
            aeCache.findApplicationEntity(queryAET);

        aeCache.findApplicationEntity(movescp);
        if (deviceName != null) {
            Device device = deviceCache.findDevice(deviceName);
            ApplicationEntity ae = device.getApplicationEntity(aet, true);
            if (ae == null || !ae.isInstalled())
                throw new ConfigurationException("No such Application Entity: " + aet + " found in device: " + deviceName);

            validateQueue(device);
        } else
            validateQueue(device);
    }

    private void validateQueue(Device device) {
        device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class).getQueueDescriptorNotNull(queueName);
    }

    private int scheduleRetrieveTask(ExternalRetrieveContext ctx) {
        return retrieveManager.scheduleRetrieveTask(priorityAsInt, ctx, null, 0L);
    }

    private int createRetrieveTask(ExternalRetrieveContext ctx) {
        return retrieveManager.createRetrieveTask(ctx);
    }

    private void logRequest() {
        LOG.info("Process {} {}?{} from {}@{}",
                request.getMethod(),
                request.getRequestURI(),
                request.getQueryString(),
                request.getRemoteUser(),
                request.getRemoteHost());
    }

    private ExternalRetrieveContext createExtRetrieveCtx(String destAET, DimseRSP dimseRSP) {
        Attributes keys = new Attributes(dimseRSP.getDataset(),
                Tag.QueryRetrieveLevel, Tag.StudyInstanceUID, Tag.SeriesInstanceUID, Tag.SOPInstanceUID);
        return createExtRetrieveCtx(destAET, keys);
    }

    private ExternalRetrieveContext createExtRetrieveCtx(String destAET, String... studyIUID) {
        Attributes keys = new Attributes(2);
        keys.setString(Tag.QueryRetrieveLevel, VR.CS, QueryRetrieveLevel2.STUDY.name());
        keys.setString(Tag.StudyInstanceUID, VR.UI, studyIUID);
        return createExtRetrieveCtx(destAET, keys);
    }

    private ExternalRetrieveContext createExtRetrieveCtx(String destAET, Attributes keys) {
        return new ExternalRetrieveContext()
                .setDeviceName(deviceName != null ? deviceName : device.getDeviceName())
                .setQueueName(queueName)
                .setBatchID(batchID)
                .setLocalAET(aet)
                .setRemoteAET(movescp)
                .setDestinationAET(destAET)
                .setHttpServletRequestInfo(HttpServletRequestInfo.valueOf(request))
                .setKeys(keys);
    }

    private static String count(int count) {
        return "{\"count\":" + count + '}';
    }

    private static String warning(int status) {
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
}
