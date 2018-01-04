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
 * Portions created by the Initial Developer are Copyright (C) 2017
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

package org.dcm4chee.arc.retrieve.rs;

import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.entity.RetrieveTask;
import org.dcm4chee.arc.qmgt.DifferentDeviceException;
import org.dcm4chee.arc.qmgt.IllegalTaskRequestException;
import org.dcm4chee.arc.qmgt.IllegalTaskStateException;
import org.dcm4chee.arc.retrieve.mgt.RetrieveManager;
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
import java.io.*;
import java.util.List;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Oct 2017
 */
@RequestScoped
@Path("/monitor/retrieve")
public class RetrieveTaskRS {

    private static final Logger LOG = LoggerFactory.getLogger(RetrieveTaskRS.class);
    private static final String CSV_HEADER =
            "pk," +
            "createdTime," +
            "updatedTime," +
            "LocalAET," +
            "RemoteAET," +
            "DestinationAET," +
            "StudyInstanceUID," +
            "SeriesInstanceUID," +
            "SOPInstanceUID," +
            "remaining," +
            "completed," +
            "failed," +
            "warning," +
            "statusCode," +
            "errorComment," +
            "dicomDeviceName," +
            "status,scheduledTime," +
            "failures," +
            "processingStartTime," +
            "processingEndTime," +
            "errorMessage," +
            "outcomeMessage\r\n";

    @Inject
    private RetrieveManager mgr;

    @Context
    private HttpServletRequest request;

    @QueryParam("dicomDeviceName")
    private String deviceName;

    @QueryParam("LocalAET")
    private String localAET;

    @QueryParam("RemoteAET")
    private String remoteAET;

    @QueryParam("DestinationAET")
    private String destinationAET;

    @QueryParam("StudyInstanceUID")
    private String studyIUID;

    @QueryParam("status")
    @Pattern(regexp = "TO SCHEDULE|SCHEDULED|IN PROCESS|COMPLETED|WARNING|FAILED|CANCELED")
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

    @GET
    @NoCache
    @Produces("application/json")
    public Response listRetrieveTasks() {
        logRequest();
        return Response.ok(toEntity(
                mgr.search(deviceName, localAET, remoteAET, destinationAET, studyIUID, createdTime, updatedTime,
                        parseStatus(status), parseInt(offset), parseInt(limit))))
                .build();
    }

    @GET
    @NoCache
    @Produces("text/csv; charset=UTF-8")
    public Response listRetrieveTasksAsCSV() {
        logRequest();
        return Response.ok(toEntityAsCSV(
                mgr.search(deviceName, localAET, remoteAET, destinationAET, studyIUID, createdTime, updatedTime,
                        parseStatus(status), parseInt(offset), parseInt(limit))))
                .build();
    }

    @GET
    @NoCache
    @Path("/count")
    @Produces("application/json")
    public Response countRetrieveTasks() {
        logRequest();
        return Response.ok("{\"count\":" +
                mgr.countRetrieveTasks(deviceName, localAET, remoteAET, destinationAET, studyIUID, createdTime, updatedTime,
                        parseStatus(status)) + '}')
                .build();
    }

    @POST
    @Path("{taskPK}/cancel")
    public Response cancelProcessing(@PathParam("taskPK") long pk) {
        logRequest();
        try {
            return Response.status(mgr.cancelProcessing(pk)
                    ? Response.Status.NO_CONTENT
                    : Response.Status.NOT_FOUND)
                    .build();
        } catch (IllegalTaskStateException e) {
            return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("/cancel")
    public Response cancelRetrieveTasks() {
        logRequest();
        try {
            return Response.status(Response.Status.OK)
                    .entity("{\"count\":"
                            + mgr.cancelRetrieveTasks(
                                    localAET, remoteAET, destinationAET, studyIUID, deviceName, parseStatus(status),
                                    createdTime, updatedTime)
                            + '}')
                    .build();
        } catch (IllegalTaskRequestException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("{taskPK}/reschedule")
    public Response rescheduleTask(@PathParam("taskPK") long pk) {
        logRequest();
        try {
            return Response.status(mgr.rescheduleRetrieveTask(pk)
                    ? Response.Status.NO_CONTENT
                    : Response.Status.NOT_FOUND)
                    .build();
        } catch (IllegalTaskStateException|DifferentDeviceException e) {
            return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
        }
    }

    @DELETE
    @Path("/{taskPK}")
    public Response deleteTask(@PathParam("taskPK") long pk) {
        logRequest();
        return Response.status(mgr.deleteRetrieveTask(pk)
                ? Response.Status.NO_CONTENT
                : Response.Status.NOT_FOUND)
                .build();
    }

    @DELETE
    public String deleteTasks() {
        logRequest();
        return "{\"deleted\":"
                + mgr.deleteTasks(deviceName, localAET, remoteAET, destinationAET, studyIUID, createdTime, updatedTime,
                        parseStatus(status))
                + '}';
    }

    private Object toEntity(final List<RetrieveTask> tasks) {
        return new StreamingOutput() {
            @Override
            public void write(OutputStream out) throws IOException {
                JsonGenerator gen = Json.createGenerator(out);
                gen.writeStartArray();
                for (RetrieveTask task : tasks)
                    task.writeAsJSONTo(gen);
                gen.writeEnd();
                gen.flush();
            }
        };
    }

    private Object toEntityAsCSV(final List<RetrieveTask> tasks) {
        return new StreamingOutput() {
            @Override
            public void write(OutputStream out) throws IOException {
                Writer writer = new BufferedWriter(new OutputStreamWriter(out, "UTF-8"));
                writer.write(CSV_HEADER);
                for (RetrieveTask task : tasks) {
                    task.writeAsCSVTo(writer);
                }
                writer.flush();
            }
        };
    }

    private void logRequest() {
        LOG.info("Process {} {} from {}@{}", request.getMethod(), request.getRequestURI(),
                request.getRemoteUser(), request.getRemoteHost());
    }

    private static int parseInt(String s) {
        return s != null ? Integer.parseInt(s) : 0;
    }

    private static QueueMessage.Status parseStatus(String s) {
        return s != null ? QueueMessage.Status.fromString(s) : null;
    }
}
