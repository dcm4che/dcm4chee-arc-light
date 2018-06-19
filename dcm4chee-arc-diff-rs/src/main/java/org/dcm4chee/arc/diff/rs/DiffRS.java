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
 *
 * **** END LICENSE BLOCK *****
 *
 */

package org.dcm4chee.arc.diff.rs;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.IApplicationEntityCache;
import org.dcm4che3.data.*;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.net.*;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.SafeClose;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.util.TagUtils;
import org.dcm4che3.util.UIDUtils;
import org.dcm4chee.arc.conf.Duration;
import org.dcm4chee.arc.diff.DiffContext;
import org.dcm4chee.arc.diff.DiffService;
import org.dcm4chee.arc.diff.DiffSCU;
import org.dcm4chee.arc.qmgt.HttpServletRequestInfo;
import org.dcm4chee.arc.qmgt.QueueSizeLimitExceededException;
import org.dcm4chee.arc.query.util.QueryAttributes;
import org.dcm4chee.arc.validation.constraints.ValidUriInfo;
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
import java.io.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since May 2017
 */
@RequestScoped
@Path("aets/{AETitle}/dimse/{ExternalAET}/diff/{OriginalAET}")
@ValidUriInfo(type = QueryAttributes.class)
public class DiffRS {

    private static final Logger LOG = LoggerFactory.getLogger(DiffRS.class);

    @Inject
    private DiffService diffService;

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

    @PathParam("OriginalAET")
    private String originalAET;

    @QueryParam("isFuzzymatching")
    @Pattern(regexp = "true|false")
    private String fuzzymatching;

    @QueryParam("offset")
    @Pattern(regexp = "0|([1-9]\\d{0,4})")
    private String offset;

    @QueryParam("limit")
    @Pattern(regexp = "[1-9]\\d{0,4}")
    private String limit;

    @QueryParam("different")
    @Pattern(regexp = "true|false")
    private String different;

    @QueryParam("missing")
    @Pattern(regexp = "true|false")
    private String missing;

    @QueryParam("priority")
    @Pattern(regexp = "0|1|2")
    private String priority;

    @QueryParam("queue")
    @Pattern(regexp = "true|false")
    private String queue;

    @QueryParam("SplitStudyDateRange")
    @ValidValueOf(type = Duration.class)
    private String splitStudyDateRange;

    @Override
    public String toString() {
        return request.getRequestURI() + '?' + request.getQueryString();
    }

    @GET
    @NoCache
    @Path("/studies")
    @Produces("application/dicom+json,application/json")
    public void compareStudies(@Suspended AsyncResponse ar) {
        logRequest();
        try {
            DiffContext ctx = createDiffContext();
            ctx.setQueryString(request.getQueryString(), uriInfo.getQueryParameters());
            if (Boolean.parseBoolean(queue)) {
                diffService.scheduleDiffTask(ctx);
                ar.resume(Response.accepted().build());
                return;
            }
            DiffSCU diffSCU = diffService.createDiffSCU(ctx);
            ar.register((CompletionCallback) throwable -> {
                SafeClose.close(diffSCU);
            });
            if (!Status.isPending(diffSCU.init())) {
                ar.resume(Response.noContent().build());
                return;
            }
            int skip = offset();
            Attributes diff1;
            while ((diff1 = diffSCU.nextDiff()) != null && skip-- > 0);
            if (diff1 == null)
                ar.resume(Response.ok("[]").build());
            else
                ar.resume(Response.ok(entity(diff1, diffSCU)).build());
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(e.getMessage(),
                    Response.status(Response.Status.BAD_REQUEST).encoding(e.getMessage()).build());
        } catch (DicomServiceException e) {
            throw new WebApplicationException(errResponse(
                    errorMessage(e.getStatus(), e.getMessage()), Response.Status.BAD_GATEWAY));
        } catch (Exception e) {
            throw new WebApplicationException(errResponseAsTextPlain(e));
        }
    }

    @GET
    @NoCache
    @Path("/studies/count")
    @Produces("application/json")
    public void countDiffs(@Suspended AsyncResponse ar) {
        logRequest();
        try {
            DiffContext ctx = createDiffContext();
            ctx.setQueryString(request.getQueryString(), uriInfo.getQueryParameters());
            DiffSCU diffSCU = diffService.createDiffSCU(ctx);
            ar.register((CompletionCallback) throwable -> {
                SafeClose.close(diffSCU);
            });
            diffSCU.init();
            diffSCU.countDiffs();
            ar.resume(Response.ok(
                    "{\"count\":" + diffSCU.matches() +
                            ",\"missing\":" + diffSCU.missing() +
                            ",\"different\":" + diffSCU.different() +
                            "}")
                    .build());
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(e.getMessage(),
                    Response.status(Response.Status.BAD_REQUEST).encoding(e.getMessage()).build());
        } catch (DicomServiceException e) {
            throw new WebApplicationException(errResponse(
                    errorMessage(e.getStatus(), e.getMessage()), Response.Status.BAD_GATEWAY));
        } catch (Exception e) {
            throw new WebApplicationException(errResponseAsTextPlain(e));
        }
    }

    @POST
    @Path("/studies/csv:{field}")
    @Consumes("text/csv")
    @Produces("application/json")
    public Response compareStudiesFromCSV(
            @PathParam("field") int field,
            InputStream in) {
        logRequest();
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
                DiffContext ctx = createDiffContext();
                if (count > 0 || UIDUtils.isValid(studyUID)) {
                    ctx.setQueryString(
                            "StudyInstanceUID=" + studyUID,
                            uriInfo.getQueryParameters());
                    diffService.scheduleDiffTask(ctx);
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
                    : Response.noContent().header("Warning", "Empty file").build();

        Response.ResponseBuilder builder = Response.status(errorStatus)
                .header("Warning", warning);
        if (count > 0)
            builder.entity(count(count));
        return builder.build();
    }

    private DiffContext createDiffContext() throws ConfigurationException {
        return new DiffContext()
                .setHttpServletRequestInfo(HttpServletRequestInfo.valueOf(request))
                .setLocalAE(checkAE(aet, device.getApplicationEntity(aet, true)))
                .setPrimaryAE(checkAE(externalAET, aeCache.get(externalAET)))
                .setSecondaryAE(checkAE(originalAET, aeCache.get(originalAET)));
    }

    private static String errorMessage(int status, String errorComment) {
        String statusAsString = statusAsString(status);
        return errorComment == null ? statusAsString : statusAsString + " - " + errorComment;
    }

    private static String count(int count) {
        return "{\"count\":" + count + '}';
    }

    private static String statusAsString(int status) {
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

    private static ApplicationEntity checkAE(String aet, ApplicationEntity ae) {
        if (ae == null || !ae.isInstalled())
            throw new WebApplicationException(errResponse(
                    "No such Application Entity: " + aet,
                    Response.Status.NOT_FOUND));
        return ae;
    }

    private static Response errResponse(String errorMessage, Response.Status status) {
        return Response.status(status).entity("{\"errorMessage\":\"" + errorMessage + "\"}").build();
    }

    private int offset() {
        return parseInt(offset, 0);
    }

    private int limit() {
        return parseInt(limit, Integer.MAX_VALUE);
    }

    private static int parseInt(String s, int defval) {
        return s != null ? Integer.parseInt(s) : defval;
    }

    private StreamingOutput entity(final Attributes diff1, final DiffSCU diffSCU) {
        return output -> {
            try (JsonGenerator gen = Json.createGenerator(output)) {
                JSONWriter writer = new JSONWriter(gen);
                gen.writeStartArray();
                int remaining = limit();
                Attributes diff = diff1;
                while (diff != null) {
                    writer.write(diff);
                    try {
                        diff = --remaining > 0 ? diffSCU.nextDiff() : null;
                    } catch (Exception e) {
                        LOG.info("Failure on query for matching studies:\\n", e);
                        writer.write(new Attributes());
                        break;
                    }
                }
                gen.writeEnd();
            }
        };
    }

    private void logRequest() {
        LOG.info("Process {} {} from {}@{}", request.getMethod(), request.getRequestURI(), request.getRemoteUser(), request.getRemoteHost());
    }

    private Response errResponseAsTextPlain(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String exceptionAsString = sw.toString();
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(exceptionAsString).type("text/plain").build();
    }

}
