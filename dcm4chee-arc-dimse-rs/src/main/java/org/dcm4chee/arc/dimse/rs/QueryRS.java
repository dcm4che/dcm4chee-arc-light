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

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.IApplicationEntityCache;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.net.*;
import org.dcm4che3.util.TagUtils;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.Duration;
import org.dcm4chee.arc.conf.Entity;
import org.dcm4chee.arc.query.scu.CFindSCU;
import org.dcm4chee.arc.query.util.QIDO;
import org.dcm4chee.arc.query.util.QueryAttributes;
import org.dcm4chee.arc.validation.constraints.InvokeValidate;
import org.dcm4chee.arc.validation.constraints.ValidValueOf;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.CompletionCallback;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.ConnectException;
import java.util.EnumSet;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2017
 */
@RequestScoped
@Path("aets/{AETitle}/dimse/{ExternalAET}")
@InvokeValidate(type = QueryRS.class)
public class QueryRS {

    private static final Logger LOG = LoggerFactory.getLogger(QueryRS.class);

    @Context
    private HttpServletRequest request;

    @Context
    private UriInfo uriInfo;

    @Inject
    private Device device;

    @Inject
    private IApplicationEntityCache aeCache;

    @PathParam("AETitle")
    private String aet;

    @PathParam("ExternalAET")
    private String externalAET;

    @QueryParam("fuzzymatching")
    @Pattern(regexp = "true|false")
    private String fuzzymatching;

    @QueryParam("offset")
    @Pattern(regexp = "0|([1-9]\\d{0,4})")
    private String offset;

    @QueryParam("limit")
    @Pattern(regexp = "[1-9]\\d{0,4}")
    private String limit;

    @QueryParam("priority")
    @Pattern(regexp = "0|1|2")
    private String priority;

    @QueryParam("SplitStudyDateRange")
    @ValidValueOf(type = Duration.class)
    private String splitStudyDateRange;

    @Inject
    private CFindSCU findSCU;

    private Association as;

    @Override
    public String toString() {
        return request.getRequestURI() + '?' + request.getQueryString();
    }

    public void validate() {
        logRequest();
        new QueryAttributes(uriInfo, null);
    }

    @GET
    @NoCache
    @Path("/patients")
    @Produces("application/dicom+json,application/json")
    public void searchForPatientsJSON(@Suspended AsyncResponse ar) {
        search(ar, Level.PATIENT, null, null, QIDO.PATIENT, false);
    }

    @GET
    @NoCache
    @Path("/studies")
    @Produces("application/dicom+json,application/json")
    public void searchForStudiesJSON(@Suspended AsyncResponse ar) {
        search(ar, Level.STUDY, null, null, QIDO.STUDY, false);
    }

    @GET
    @NoCache
    @Path("/studies/{StudyInstanceUID}/series")
    @Produces("application/dicom+json,application/json")
    public void searchForSeriesOfStudyJSON(
            @Suspended AsyncResponse ar,
            @PathParam("StudyInstanceUID") String studyInstanceUID) {
        search(ar, Level.SERIES, studyInstanceUID, null, QIDO.SERIES, false);
    }

    @GET
    @NoCache
    @Path("/studies/{StudyInstanceUID}/series/{SeriesInstanceUID}/instances")
    @Produces("application/dicom+json,application/json")
    public void searchForInstancesOfSeriesJSON(
            @Suspended AsyncResponse ar,
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @PathParam("SeriesInstanceUID") String seriesInstanceUID) {
        search(ar, Level.IMAGE, studyInstanceUID, seriesInstanceUID, QIDO.INSTANCE, false);
    }

    @GET
    @NoCache
    @Path("/patients/count")
    @Produces("application/json")
    public void countPatients(@Suspended AsyncResponse ar) {
        search(ar, Level.PATIENT, null, null, QIDO.PATIENT, true);
    }

    @GET
    @NoCache
    @Path("/studies/count")
    @Produces("application/json")
    public void countStudies(@Suspended AsyncResponse ar) {
        search(ar, Level.STUDY, null, null, QIDO.STUDY, true);
    }

    @GET
    @NoCache
    @Path("/studies/{StudyInstanceUID}/series/count")
    @Produces("application/json")
    public void countSeriesOfStudy(
            @Suspended AsyncResponse ar,
            @PathParam("StudyInstanceUID") String studyInstanceUID) {
        search(ar, Level.SERIES, studyInstanceUID, null, QIDO.SERIES, true);
    }

    @GET
    @NoCache
    @Path("/studies/{StudyInstanceUID}/series/{SeriesInstanceUID}/instances/count")
    @Produces("application/json")
    public void countInstancesOfSeries(
            @Suspended AsyncResponse ar,
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @PathParam("SeriesInstanceUID") String seriesInstanceUID) {
        search(ar, Level.IMAGE, studyInstanceUID, seriesInstanceUID, QIDO.INSTANCE, true);
    }

    private int offset() {
        return parseInt(offset, 0);
    }

    private int limit() {
        return parseInt(limit, 0);
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

    private void search(AsyncResponse ar, Level level, String studyInstanceUID, String seriesInstanceUID, QIDO qido,
                        boolean count) {
        ApplicationEntity localAE = device.getApplicationEntity(aet, true);
        if (localAE == null || !localAE.isInstalled())
            throw new WebApplicationException(errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND));

        try {
            aeCache.findApplicationEntity(externalAET);
            QueryAttributes queryAttributes = new QueryAttributes(uriInfo, null);
            Attributes keys = queryAttributes.getQueryKeys();
            if (!count) {
                queryAttributes.addReturnTags(qido.includetags);
                if (queryAttributes.isIncludeAll()) {
                    ArchiveDeviceExtension arcdev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
                    switch (level) {
                        case IMAGE:
                            queryAttributes.addReturnTags(arcdev.getAttributeFilter(Entity.Instance).getSelection(false));
                            break;
                        case SERIES:
                            queryAttributes.addReturnTags(arcdev.getAttributeFilter(Entity.Series).getSelection(false));
                            break;
                        case STUDY:
                            queryAttributes.addReturnTags(arcdev.getAttributeFilter(Entity.Study).getSelection(false));
                        case PATIENT:
                            queryAttributes.addReturnTags(arcdev.getAttributeFilter(Entity.Patient).getSelection(false));
                    }
                    keys.remove(Tag.TimezoneOffsetFromUTC);
                }
            }
            keys.setString(Tag.QueryRetrieveLevel, VR.CS, level.name());
            if (studyInstanceUID != null)
                keys.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUID);
            if (seriesInstanceUID != null)
                keys.setString(Tag.SeriesInstanceUID, VR.UI, seriesInstanceUID);
            EnumSet<QueryOption> queryOptions = EnumSet.of(QueryOption.DATETIME);
            if (Boolean.parseBoolean(fuzzymatching))
                queryOptions.add(QueryOption.FUZZY);
            ar.register((CompletionCallback) throwable -> {
                    if (as != null)
                        try {
                            as.release();
                        } catch (IOException e) {
                            LOG.info("{}: Failed to release association:\\n", as, e);
                        }
            });
            as = findSCU.openAssociation(localAE, externalAET, level.cuid, queryOptions);
            DimseRSP dimseRSP = findSCU.query(as, priority(), keys, !count && limit != null ? offset() + limit() : 0,
                    1, splitStudyDateRange());
            dimseRSP.next();
            ar.resume((count ? countResponse(dimseRSP) : responseBuilder(dimseRSP)).build());
        } catch (IllegalStateException| ConfigurationException e) {
            throw new WebApplicationException(errResponse(e.getMessage(), Response.Status.NOT_FOUND));
        } catch (ConnectException e) {
            throw new WebApplicationException(
                    errResponse(e.getMessage(), Response.Status.BAD_GATEWAY));
        } catch (Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    private void logRequest() {
        LOG.info("Process {} {}?{} from {}@{}",
                request.getMethod(),
                request.getRequestURI(),
                request.getQueryString(),
                request.getRemoteUser(),
                request.getRemoteHost());
    }

    private Response.ResponseBuilder responseBuilder(DimseRSP dimseRSP) {
        int status = dimseRSP.getCommand().getInt(Tag.Status, -1);
        switch (status) {
            case 0:
                return Response.ok("[]");
            case Status.Pending:
            case Status.PendingWarning:
                return Response.ok(writeJSON(dimseRSP));
        }
        return warning(warning(status));
    }

    private Response.ResponseBuilder countResponse(DimseRSP dimseRSP) {
        int count = 0;
        try {
            while (dimseRSP.next()) {
                count++;
            }
        } catch (Exception e) {
            return warning(e.getMessage());
        }
        int status = dimseRSP.getCommand().getInt(Tag.Status, -1);
        return status == 0
                ? Response.ok("{\"count\":" + count + '}')
                : warning(warning(status));
    }

    private Response.ResponseBuilder warning(String warning) {
        LOG.warn("Response Bad Gateway caused by {}", warning);
        return Response.status(Response.Status.BAD_GATEWAY).header("Warning", warning);
    }

    private String warning(int status) {
        switch (status) {
            case Status.OutOfResources:
                return "A700: Refused: Out of Resources";
            case Status.IdentifierDoesNotMatchSOPClass:
                return "A900: Identifier does not match SOP Class";
        }
        return TagUtils.shortToHexString(status)
                + ((status & 0xF000) == Status.UnableToProcess
                ? ": Unable to Process"
                : ": Unexpected status code");
    }

    private enum Level {
        PATIENT(UID.PatientRootQueryRetrieveInformationModelFIND),
        STUDY(UID.StudyRootQueryRetrieveInformationModelFIND),
        SERIES(UID.StudyRootQueryRetrieveInformationModelFIND),
        IMAGE(UID.StudyRootQueryRetrieveInformationModelFIND);
        final String cuid;

        Level(String cuid) {
            this.cuid = cuid;
        }
    }

    private Object writeJSON(final DimseRSP dimseRSP) {
        return (StreamingOutput) out -> {
                JsonGenerator gen = Json.createGenerator(out);
                JSONWriter writer = new JSONWriter(gen);
                gen.writeStartArray();
                int skip = offset();
                int remaining = limit();
                try {
                    Attributes dataset = dimseRSP.getDataset();
                    dimseRSP.next();
                    do {
                        if (skip > 0)
                            skip--;
                        else  {
                            writer.write(dataset);
                            if (limit != null && --remaining == 0)
                                break;
                        }
                        dataset = dimseRSP.getDataset();
                    } while (dimseRSP.next());
                } catch (InterruptedException e) {
                    LOG.warn("Failed to read next C-FIND RSP from {}:\\n", externalAET, e);
                }
                gen.writeEnd();
                gen.flush();
        };
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
}
