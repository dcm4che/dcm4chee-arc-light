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
 *  Portions created by the Initial Developer are Copyright (C) 2015-2017
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

import org.dcm4che3.conf.api.IApplicationEntityCache;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.*;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.util.TagUtils;
import org.dcm4che3.util.UIDUtils;
import org.dcm4chee.arc.conf.Duration;
import org.dcm4chee.arc.qmgt.HttpServletRequestInfo;
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
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.*;
import java.util.EnumSet;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2017
 */
@RequestScoped
@Path("aets/{AETitle}/dimse/{ExternalAET}")
@InvokeValidate(type = QueryRetrieveRS.class)
public class QueryRetrieveRS {

    private static final Logger LOG = LoggerFactory.getLogger(QueryRetrieveRS.class);

    @Context
    private HttpServletRequest request;

    @Context
    private UriInfo uriInfo;

    @Inject
    private Device device;

    @PathParam("AETitle")
    private String aet;

    @PathParam("ExternalAET")
    private String externalAET;

    @QueryParam("batchID")
    private String batchID;

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

    @Override
    public String toString() {
        return request.getRequestURI() + '?' + request.getQueryString();
    }

    public void validate() {
        new QueryAttributes(uriInfo);
    }

    @POST
    @Path("/studies/query:{QueryAET}/export/dicom:{DestinationAET}")
    @Produces("application/json")
    public Response retrieveMatchingStudies(
            @PathParam("QueryAET") String queryAET,
            @PathParam("DestinationAET") String destAET) {
        return retrieveMatching(QueryRetrieveLevel2.STUDY, null, null, queryAET, destAET);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}/series/query:{QueryAET}/export/dicom:{DestinationAET}")
    @Produces("application/json")
    public Response retrieveMatchingSeriesOfStudy(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @PathParam("QueryAET") String queryAET,
            @PathParam("DestinationAET") String destAET)
            {
        return retrieveMatching(QueryRetrieveLevel2.SERIES, studyInstanceUID, null, queryAET, destAET);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}/series/{SeriesInstanceUID}/instances/query:{QueryAET}/export/dicom:{DestinationAET}")
    @Produces("application/json")
    public Response retrieveMatchingInstancesOfSeriesJSON(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @PathParam("SeriesInstanceUID") String seriesInstanceUID,
            @PathParam("QueryAET") String queryAET,
            @PathParam("DestinationAET") String destAET)
            {
        return retrieveMatching(QueryRetrieveLevel2.IMAGE, studyInstanceUID, seriesInstanceUID, queryAET, destAET);
    }

    @POST
    @Path("/studies/csv:{field}/export/dicom:{destinationAET}")
    @Consumes("text/csv")
    @Produces("application/json")
    public Response retrieveMatchingStudiesFromCSV(
            @PathParam("field") int field,
            @PathParam("destinationAET") String destAET,
            InputStream in) {
        logRequest();
        checkAE(aet, device.getApplicationEntity(aet, true));
        try {
            checkAE(externalAET, aeCache.get(externalAET));
            Response.Status errorStatus = Response.Status.BAD_REQUEST;
            if (field < 1)
                return Response.status(errorStatus)
                        .entity("CSV field for Study Instance UID should be greater than or equal to 1").build();

            int count = 0;
            String warning = null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String studyUID = StringUtils.split(line, ',')[field - 1].replaceAll("\"", "");
                    if (count > 0 || UIDUtils.isValid(studyUID)) {
                        if (retrieveManager.scheduleRetrieveTask(
                                priority(), createExtRetrieveCtx(destAET, studyUID), batchID, null, 0L))
                            count++;
                    }
                }
            } catch (QueueSizeLimitExceededException e) {
                errorStatus = Response.Status.SERVICE_UNAVAILABLE;
                warning = e.getMessage();
            } catch (Exception e) {
                warning = e.getMessage();
            }

            if (warning == null)
                return count > 0
                        ? Response.accepted(count(count)).build()
                        : Response.noContent().header("Warning", "Empty file or Field position incorrect").build();

            Response.ResponseBuilder builder = Response.status(errorStatus)
                    .header("Warning", warning);
            if (count > 0)
                builder.entity(count(count));
            return builder.build();
        } catch (Exception e) {
            return errResponseAsTextPlain(e);
        }
    }

    private ApplicationEntity checkAE(String aet, ApplicationEntity ae) {
        if (ae == null || !ae.isInstalled())
            throw new WebApplicationException(errResponse(
                    "No such Application Entity: " + aet,
                    Response.Status.NOT_FOUND));
        return ae;
    }

    private Response errResponse(String errorMessage, Response.Status status) {
        return Response.status(status).entity("{\"errorMessage\":\"" + errorMessage + "\"}").build();
    }

    private Response errResponseAsTextPlain(Exception e) {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(exceptionAsString(e))
                .type("text/plain")
                .build();
    }

    private String exceptionAsString(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private int priority() {
        return parseInt(priority, 0);
    }

    private static int parseInt(String s, int defval) {
        return s != null ? Integer.parseInt(s) : defval;
    }

    private Duration splitStudyDateRange() {
        return splitStudyDateRange != null ? Duration.valueOf(splitStudyDateRange) : null;
    }

    private Response retrieveMatching(QueryRetrieveLevel2 level, String studyInstanceUID, String seriesInstanceUID,
                                      String queryAET, String destAET) {
        logRequest();
        ApplicationEntity localAE = checkAE(aet, device.getApplicationEntity(aet, true));
        try {
            checkAE(externalAET, aeCache.get(externalAET));
            checkAE(queryAET, aeCache.get(queryAET));
            QueryAttributes queryAttributes = new QueryAttributes(uriInfo);
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
                int priority = priority();
                DimseRSP dimseRSP = findSCU.query(as, priority, keys, 0, 1, splitStudyDateRange());
                dimseRSP.next();
                int status;
                do {
                    status = dimseRSP.getCommand().getInt(Tag.Status, -1);
                    if (Status.isPending(status)) {
                        if (retrieveManager.scheduleRetrieveTask(priority,
                                createExtRetrieveCtx(destAET, dimseRSP), batchID, null, 0L))
                            count++;
                    }
                } while (dimseRSP.next());
                warning = warning(status);
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

            Response.ResponseBuilder builder = Response.status(errorStatus)
                    .header("Warning", warning);
            if (count > 0)
                builder.entity(count(count));
            return builder.build();
        } catch (Exception e) {
            return errResponseAsTextPlain(e);
        }
    }

    private void logRequest() {
        LOG.info("Process POST {} from {}@{}", request.getRequestURI(), request.getRemoteUser(), request.getRemoteHost());
    }

    private ExternalRetrieveContext createExtRetrieveCtx(String destAET, DimseRSP dimseRSP) {
        Attributes keys = new Attributes(dimseRSP.getDataset(),
                Tag.QueryRetrieveLevel, Tag.StudyInstanceUID, Tag.SeriesInstanceUID, Tag.SOPInstanceUID);
        return new ExternalRetrieveContext()
                .setLocalAET(aet)
                .setRemoteAET(externalAET)
                .setDestinationAET(destAET)
                .setHttpServletRequestInfo(HttpServletRequestInfo.valueOf(request))
                .setKeys(keys);
    }

    private ExternalRetrieveContext createExtRetrieveCtx(String destAET, String studyIUID) {
        Attributes keys = new Attributes(2);
        keys.setString(Tag.QueryRetrieveLevel, VR.CS, QueryRetrieveLevel2.STUDY.name());
        keys.setString(Tag.StudyInstanceUID, VR.UI, studyIUID);
        return new ExternalRetrieveContext()
                .setLocalAET(aet)
                .setRemoteAET(externalAET)
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
