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

package org.dcm4chee.arc.stgcmt.rs;

import org.dcm4che3.conf.json.JsonWriter;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.query.util.TaskQueryParam;
import org.dcm4chee.arc.stgcmt.StgVerBatch;
import org.dcm4chee.arc.stgcmt.StgCmtManager;
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
 * @since Aug 2018
 */
@RequestScoped
@Path("monitor/stgver/batch")
public class StgVerBatchRS {
    private static final Logger LOG = LoggerFactory.getLogger(StgVerBatchRS.class);

    @Inject
    private StgCmtManager stgCmtMgr;

    @QueryParam("dicomDeviceName")
    private String deviceName;

    @QueryParam("localAET")
    private String localAET;

    @QueryParam("status")
    @Pattern(regexp = "SCHEDULED|IN PROCESS|COMPLETED|WARNING|FAILED|CANCELED")
    private String status;

    @QueryParam("createdTime")
    private String createdTime;

    @QueryParam("updatedTime")
    private String updatedTime;

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

    @QueryParam("batchID")
    private String batchID;

    @Context
    private HttpServletRequest request;

    @GET
    @NoCache
    @Produces("application/json")
    public Response listStgVerBatches() {
        logRequest();
        try {
            return Response.ok(
                    Output.JSON.entity(stgCmtMgr.listStgVerBatches(
                        queueBatchQueryParam(),
                        stgVerBatchQueryParam(),
                        parseInt(offset), parseInt(limit))))
                    .build();
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private enum Output {
        JSON {
            @Override
            Object entity(final List<StgVerBatch> stgVerBatches) {
                return (StreamingOutput) out -> {
                    JsonGenerator gen = Json.createGenerator(out);
                    gen.writeStartArray();
                    stgVerBatches.forEach(stgVerBatch -> {
                        JsonWriter writer = new JsonWriter(gen);
                        gen.writeStartObject();
                        writer.writeNotNullOrDef("batchID", stgVerBatch.getBatchID(), null);
                        writeTasks(stgVerBatch, writer);
                        writer.writeNotEmpty("dicomDeviceName", stgVerBatch.getDeviceNames());
                        writer.writeNotEmpty("LocalAET", stgVerBatch.getLocalAETs());
                        writer.writeNotEmpty("createdTimeRange",
                                datesAsStrings(stgVerBatch.getCreatedTimeRange()));
                        writer.writeNotEmpty("updatedTimeRange",
                                datesAsStrings(stgVerBatch.getUpdatedTimeRange()));
                        writer.writeNotEmpty("scheduledTimeRange",
                                datesAsStrings(stgVerBatch.getScheduledTimeRange()));
                        writer.writeNotEmpty("processingStartTimeRange",
                                datesAsStrings(stgVerBatch.getProcessingStartTimeRange()));
                        writer.writeNotEmpty("processingEndTimeRange",
                                datesAsStrings(stgVerBatch.getProcessingEndTimeRange()));
                        gen.writeEnd();
                    });
                    gen.writeEnd();
                    gen.flush();
                };
            }

            private void writeTasks(StgVerBatch stgVerBatch, JsonWriter writer) {
                writer.writeStartObject("tasks");
                writer.writeNotNullOrDef("scheduled", stgVerBatch.getScheduled(), 0);
                writer.writeNotNullOrDef("in-process", stgVerBatch.getInProcess(), 0);
                writer.writeNotNullOrDef("warning", stgVerBatch.getWarning(), 0);
                writer.writeNotNullOrDef("failed", stgVerBatch.getFailed(), 0);
                writer.writeNotNullOrDef("canceled", stgVerBatch.getCanceled(), 0);
                writer.writeNotNullOrDef("completed", stgVerBatch.getCompleted(), 0);
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

        abstract Object entity(final List<StgVerBatch> stgVerBatches);
    }
    
    private QueueMessage.Status status() {
        return status != null ? QueueMessage.Status.fromString(status) : null;
    }

    private static int parseInt(String s) {
        return s != null ? Integer.parseInt(s) : 0;
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

    private TaskQueryParam queueBatchQueryParam() {
        TaskQueryParam taskQueryParam = new TaskQueryParam();
        taskQueryParam.setDeviceName(deviceName);
        taskQueryParam.setStatus(status());
        taskQueryParam.setBatchID(batchID);
        return taskQueryParam;
    }

    private TaskQueryParam stgVerBatchQueryParam() {
        TaskQueryParam taskQueryParam = new TaskQueryParam();
        taskQueryParam.setCreatedTime(createdTime);
        taskQueryParam.setUpdatedTime(updatedTime);
        taskQueryParam.setOrderBy(orderby);
        taskQueryParam.setLocalAET(localAET);
        return taskQueryParam;
    }
}
