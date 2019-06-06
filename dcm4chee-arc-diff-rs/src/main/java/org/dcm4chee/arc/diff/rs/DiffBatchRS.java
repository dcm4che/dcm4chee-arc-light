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
 * *** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.diff.rs;

import org.dcm4che3.conf.json.JsonWriter;
import org.dcm4che3.json.JSONWriter;
import org.dcm4chee.arc.diff.DiffBatch;
import org.dcm4chee.arc.diff.DiffService;
import org.dcm4chee.arc.entity.AttributesBlob;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.query.util.TaskQueryParam;
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
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Mar 2018
 */
@RequestScoped
@Path("monitor/diff/batch")
public class DiffBatchRS {

    private static final Logger LOG = LoggerFactory.getLogger(DiffBatchRS.class);

    @Inject
    private DiffService diffService;

    @Context
    private HttpServletRequest request;

    @QueryParam("dicomDeviceName")
    private String deviceName;

    @QueryParam("LocalAET")
    private String localAET;

    @QueryParam("PrimaryAET")
    private String primaryAET;

    @QueryParam("SecondaryAET")
    private String secondaryAET;

    @QueryParam("checkDifferent")
    @Pattern(regexp = "true|false")
    private String checkDifferent;

    @QueryParam("checkMissing")
    @Pattern(regexp = "true|false")
    private String checkMissing;

    @QueryParam("status")
    @Pattern(regexp = "SCHEDULED|IN PROCESS|COMPLETED|WARNING|FAILED|CANCELED")
    private String status;

    @QueryParam("createdTime")
    private String createdTime;

    @QueryParam("updatedTime")
    private String updatedTime;

    @QueryParam("batchID")
    private String batchID;

    @QueryParam("comparefield")
    private String comparefields;

    @QueryParam("offset")
    @Pattern(regexp = "0|([1-9]\\d{0,4})")
    private String offset;

    @QueryParam("limit")
    @Pattern(regexp = "[1-9]\\d{0,4}")
    private String limit;

    @QueryParam("orderby")
    @DefaultValue("-updatedTime")
    @Pattern(regexp = "(-?)createdTime|(-?)updatedTime")
    private String orderby;

    @GET
    @NoCache
    @Produces("application/json")
    public Response listDiffBatches() {
        logRequest();
        try {
            List<DiffBatch> diffBatches = diffService.listDiffBatches(
                    queueBatchQueryParam(batchID),
                    diffBatchQueryParam(),
                    parseInt(offset), parseInt(limit));
            return Response.ok().entity(Output.JSON.entity(diffBatches)).build();
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @GET
    @NoCache
    @Path("/{batchID}/studies")
    @Produces("application/dicom+json,application/json")
    public Response getDiffBatchResult(@PathParam("batchID") String batchID) {
        logRequest();
        try {
            if (diffService.diffTasksOfBatch(batchID) == 0)
                return Response.status(Response.Status.NOT_FOUND).build();

            return Response.ok(entity(diffService.getDiffTaskAttributes(
                    queueBatchQueryParam(batchID),
                    diffBatchQueryParam(),
                    parseInt(offset),
                    parseInt(limit))))
                    .build();
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private enum Output {
        JSON {
            @Override
            Object entity(final List<DiffBatch> diffBatches) {
                return (StreamingOutput) out -> {
                    JsonGenerator gen = Json.createGenerator(out);
                    gen.writeStartArray();
                    diffBatches.forEach(diffBatch -> {
                        JsonWriter writer = new JsonWriter(gen);
                        gen.writeStartObject();
                        writer.writeNotNullOrDef("batchID", diffBatch.getBatchID(), null);
                        writeTasks(diffBatch, writer);
                        writer.writeNotEmpty("dicomDeviceName", diffBatch.getDeviceNames());
                        writer.writeNotEmpty("LocalAET", diffBatch.getLocalAETs());
                        writer.writeNotEmpty("PrimaryAET", diffBatch.getPrimaryAETs());
                        writer.writeNotEmpty("SecondaryAET", diffBatch.getSecondaryAETs());
                        writer.writeNotEmpty("comparefield", diffBatch.getComparefields());
                        writer.writeNotEmpty("checkMissing", diffBatch.getCheckMissing());
                        writer.writeNotEmpty("checkDifferent", diffBatch.getCheckDifferent());
                        writer.writeNotDef("matches", diffBatch.getMatches(), 0);
                        writer.writeNotDef("missing", diffBatch.getMissing(), 0);
                        writer.writeNotDef("different", diffBatch.getDifferent(), 0);
                        writer.writeNotEmpty("createdTimeRange", datesAsStrings(diffBatch.getCreatedTimeRange()));
                        writer.writeNotEmpty("updatedTimeRange", datesAsStrings(diffBatch.getUpdatedTimeRange()));
                        writer.writeNotEmpty("scheduledTimeRange", datesAsStrings(diffBatch.getScheduledTimeRange()));
                        writer.writeNotEmpty("processingStartTimeRange", datesAsStrings(diffBatch.getProcessingStartTimeRange()));
                        writer.writeNotEmpty("processingEndTimeRange", datesAsStrings(diffBatch.getProcessingEndTimeRange()));
                        gen.writeEnd();
                    });
                    gen.writeEnd();
                    gen.flush();
                };
            }

            private void writeTasks(DiffBatch diffBatch, JsonWriter writer) {
                writer.writeStartObject("tasks");
                writer.writeNotNullOrDef("scheduled", diffBatch.getScheduled(), 0);
                writer.writeNotNullOrDef("in-process", diffBatch.getInProcess(), 0);
                writer.writeNotNullOrDef("warning", diffBatch.getWarning(), 0);
                writer.writeNotNullOrDef("failed", diffBatch.getFailed(), 0);
                writer.writeNotNullOrDef("canceled", diffBatch.getCanceled(), 0);
                writer.writeNotNullOrDef("completed", diffBatch.getCompleted(), 0);
                writer.writeEnd();
            }

            private String[] datesAsStrings(Date[] dates) {
                String[] datesAsStrings = new String[dates.length];
                DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
                for (int i = 0; i < dates.length; i++)
                    datesAsStrings[i] = df.format(dates[i]);
                return datesAsStrings;
            }
        };

        abstract Object entity(final List<DiffBatch> diffBatches);
    }

    private StreamingOutput entity(List<byte[]> diffTaskAttributesList) {
        return output -> {
            try (JsonGenerator gen = Json.createGenerator(output)) {
                JSONWriter writer = new JSONWriter(gen);
                gen.writeStartArray();
                for (byte[] diffTaskAttributes : diffTaskAttributesList)
                    writer.write(AttributesBlob.decodeAttributes(diffTaskAttributes, null));
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

    private static int parseInt(String s) {
        return s != null ? Integer.parseInt(s) : 0;
    }

    private QueueMessage.Status status() {
        return status != null ? QueueMessage.Status.fromString(status) : null;
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

    private TaskQueryParam queueBatchQueryParam(String batchID) {
        TaskQueryParam taskQueryParam = new TaskQueryParam();
        taskQueryParam.setStatus(status());
        taskQueryParam.setDeviceName(deviceName);
        taskQueryParam.setBatchID(batchID);
        return taskQueryParam;
    }

    private TaskQueryParam diffBatchQueryParam() {
        TaskQueryParam taskQueryParam = new TaskQueryParam();
        taskQueryParam.setLocalAET(localAET);
        taskQueryParam.setPrimaryAET(primaryAET);
        taskQueryParam.setSecondaryAET(secondaryAET);
        taskQueryParam.setCompareFields(comparefields);
        taskQueryParam.setCheckMissing(checkMissing);
        taskQueryParam.setCheckDifferent(checkDifferent);
        taskQueryParam.setCreatedTime(createdTime);
        taskQueryParam.setUpdatedTime(updatedTime);
        taskQueryParam.setOrderBy(orderby);
        return taskQueryParam;
    }
}
