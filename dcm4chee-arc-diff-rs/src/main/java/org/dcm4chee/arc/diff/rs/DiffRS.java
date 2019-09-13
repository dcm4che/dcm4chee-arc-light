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
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.Duration;
import org.dcm4chee.arc.diff.DiffContext;
import org.dcm4chee.arc.diff.DiffService;
import org.dcm4chee.arc.diff.DiffSCU;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.qmgt.QueueSizeLimitExceededException;
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
import javax.ws.rs.core.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since May 2017
 */
@RequestScoped
@Path("aets/{AETitle}/dimse/{ExternalAET}/diff/{OriginalAET}")
@InvokeValidate(type = DiffRS.class)
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

    @QueryParam("ForceQueryByStudyUID")
    @Pattern(regexp = "true|false")
    private String forceQueryByStudyUID;

    @QueryParam("SplitStudyDateRange")
    @ValidValueOf(type = Duration.class)
    private String splitStudyDateRange;

    @Override
    public String toString() {
        return request.getRequestURI() + '?' + request.getQueryString();
    }

    public void validate() {
        logRequest();
        new QueryAttributes(uriInfo, null);
    }

    @HeaderParam("Content-Type")
    private MediaType contentType;

    private char csvDelimiter = ',';

    @GET
    @NoCache
    @Path("/studies")
    @Produces("application/dicom+json,application/json")
    public void compareStudies(@Suspended AsyncResponse ar) {
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
        } catch (ConfigurationException e) {
            throw new WebApplicationException(errResponse(e.getMessage(), Response.Status.NOT_FOUND));
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(errResponse(e.getMessage(), Response.Status.BAD_REQUEST));
        } catch (DicomServiceException e) {
            throw new WebApplicationException(errResponse(
                    errorMessage(e.getStatus(), e.getMessage()), Response.Status.BAD_GATEWAY));
        } catch (Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    @GET
    @NoCache
    @Path("/studies/count")
    @Produces("application/json")
    public void countDiffs(@Suspended AsyncResponse ar) {
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
        } catch (ConfigurationException e) {
            throw new WebApplicationException(errResponse(e.getMessage(), Response.Status.NOT_FOUND));
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(errResponse(e.getMessage(), Response.Status.BAD_REQUEST));
        } catch (DicomServiceException e) {
            throw new WebApplicationException(errResponse(
                    errorMessage(e.getStatus(), e.getMessage()), Response.Status.BAD_GATEWAY));
        } catch (Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    @POST
    @Path("/studies/csv:{field}")
    @Consumes("text/csv")
    @Produces("application/json")
    public Response compareStudiesFromCSV(
            @PathParam("field") int field,
            InputStream in) {
        Response.Status status = Response.Status.BAD_REQUEST;
        if (field < 1)
            return errResponse(
                    "CSV field for Study Instance UID should be greater than or equal to 1", status);

        if ("semicolon".equals(contentType.getParameters().get("delimiter")))
            csvDelimiter = ';';

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
                    DiffContext ctx = createDiffContext();
                    ctx.setQueryString(
                            "StudyInstanceUID=",
                            uriInfo.getQueryParameters());
                    diffService.scheduleDiffTasks(ctx, studyUIDs);
                    count += studyUIDs.size();
                    studyUIDs.clear();
                }
            }
            if (count == 0) {
                warning = "Empty file or Incorrect field position or Not a CSV file or Invalid UIDs.";
                status = Response.Status.NO_CONTENT;
            }

        } catch (ConfigurationException e) {
            warning = e.getMessage();
            status = Response.Status.NOT_FOUND;
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
    }

    private boolean validateUID(String studyUID) {
        boolean valid = UIDUtils.isValid(studyUID);
        if (!valid)
            LOG.warn("Invalid UID in CSV file: " + studyUID);
        return valid;
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

    private ApplicationEntity checkAE(String aet, ApplicationEntity ae) {
        if (ae == null || !ae.isInstalled())
            throw new WebApplicationException(
                    errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND));
        return ae;
    }

    private Response errResponse(String msg, Response.Status status) {
        return errResponseAsTextPlain("{\"errorMessage\":\"" + msg + "\"}", status);
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
                        Attributes attrs = new Attributes(1);
                        attrs.setString(Tag.ErrorComment, VR.LO, e.getMessage());
                        writer.write(attrs);
                        break;
                    }
                }
                gen.writeEnd();
            }
        };
    }

    private void logRequest() {
        LOG.info("Process {} {}?{} from {}@{}",
                request.getMethod(),
                request.getRequestURI(),
                request.getQueryString(),
                request.getRemoteUser(),
                request.getRemoteHost());
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
