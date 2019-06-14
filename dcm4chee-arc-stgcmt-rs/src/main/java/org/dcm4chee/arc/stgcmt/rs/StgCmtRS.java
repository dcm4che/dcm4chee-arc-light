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

package org.dcm4chee.arc.stgcmt.rs;

import org.dcm4che3.conf.json.JsonWriter;
import org.dcm4chee.arc.entity.StgCmtResult;
import org.dcm4chee.arc.query.util.TaskQueryParam;
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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Sep 2016
 */
@RequestScoped
@Path("stgcmt")
public class StgCmtRS {
    private static final Logger LOG = LoggerFactory.getLogger(StgCmtRS.class);

    @Inject
    private StgCmtManager mgr;

    @Context
    private HttpServletRequest request;

    @QueryParam("status")
    @Pattern(regexp = "PENDING|COMPLETED|WARNING|FAILED")
    private String status;

    @QueryParam("studyUID")
    private String studyUID;

    @QueryParam("ExporterID")
    private String exporterID;

    @QueryParam("batchID")
    private String batchID;

    @QueryParam("JMSMessageID")
    private String msgID;

    @QueryParam("updatedBefore")
    @Pattern(regexp = "(19|20)\\d{2}\\-\\d{2}\\-\\d{2}")
    private String updatedBefore;

    @QueryParam("offset")
    @Pattern(regexp = "0|([1-9]\\d{0,4})")
    private String offset;

    @QueryParam("limit")
    @Pattern(regexp = "[1-9]\\d{0,4}")
    private String limit;

    @GET
    @NoCache
    @Produces("application/json")
    public Response listStgCmts() {
        logRequest();
        try {
            final List<StgCmtResult> stgCmtResults = mgr.listStgCmts(
                    stgCmtResultQueryParam(), parseInt(offset), parseInt(limit));
            return Response.ok((StreamingOutput) out -> {
                JsonGenerator gen = Json.createGenerator(out);
                gen.writeStartArray();
                stgCmtResults.forEach(stgCmtResult -> {
                    JsonWriter writer = new JsonWriter(gen);
                    gen.writeStartObject();
                    writer.writeNotNullOrDef("dicomDeviceName", stgCmtResult.getDeviceName(), null);
                    writer.writeNotNullOrDef("transactionUID", stgCmtResult.getTransactionUID(), null);
                    writer.writeNotNullOrDef("status", stgCmtResult.getStatus().name(), null);
                    writer.writeNotNullOrDef("studyUID", stgCmtResult.getStudyInstanceUID(), null);
                    writer.writeNotNullOrDef("seriesUID", stgCmtResult.getSeriesInstanceUID(), null);
                    writer.writeNotNullOrDef("objectUID", stgCmtResult.getSopInstanceUID(), null);
                    writer.writeNotNullOrDef("exporterID", stgCmtResult.getExporterID(), null);
                    writer.writeNotNullOrDef("JMSMessageID", stgCmtResult.getMessageID(), null);
                    writer.writeNotNullOrDef("batchID", stgCmtResult.getBatchID(), null);
                    writer.writeNotNullOrDef("requested", stgCmtResult.getNumberOfInstances(), 0);
                    writer.writeNotNullOrDef("failures", stgCmtResult.getNumberOfFailures(), 0);
                    writer.writeNotNullOrDef("createdTime", stgCmtResult.getCreatedTime().toString(), null);
                    writer.writeNotNullOrDef("updatedTime", stgCmtResult.getUpdatedTime().toString(), null);
                    gen.writeEnd();
                });
                gen.writeEnd();
                gen.flush();
            }).build();
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @DELETE
    @Path("{transactionUID}")
    public void deleteStgCmt(@PathParam("transactionUID") String transactionUID) {
        logRequest();
        try {
            if (!mgr.deleteStgCmt(transactionUID))
                throw new WebApplicationException(
                        errResponse("No such Storage Commitment Result " + transactionUID, Response.Status.NOT_FOUND));
        } catch (Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    @DELETE
    @Produces("application/json")
    public Response deleteStgCmts() {
        logRequest();
        try {
            return Response.ok(
                    "{\"deleted\":" + mgr.deleteStgCmts(statusOf(status), parseDate(updatedBefore)) + '}')
                    .build();
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private static StgCmtResult.Status statusOf(String status) {
        return status != null ? StgCmtResult.Status.valueOf(status) : null;
    }

    private static int parseInt(String s) {
        return s != null ? Integer.parseInt(s) : 0;
    }

    private static Date parseDate(String s) throws Exception {
        return s != null
                ? new SimpleDateFormat("yyyy-MM-dd").parse(s)
                : null;
    }

    private void logRequest() {
        LOG.info("Process {} {}?{} from {}@{}",
                request.getMethod(),
                request.getRequestURI(),
                request.getQueryString(),
                request.getRemoteUser(),
                request.getRemoteHost());
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

    private TaskQueryParam stgCmtResultQueryParam() {
        TaskQueryParam taskQueryParam = new TaskQueryParam();
        taskQueryParam.setStgCmtStatus(statusOf(status));
        taskQueryParam.setStgCmtExporterID(exporterID);
        taskQueryParam.setStudyIUID(studyUID);
        taskQueryParam.setStudyIUID(batchID);
        taskQueryParam.setJmsMessageID(msgID);
        return taskQueryParam;
    }
}
