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

import com.querydsl.core.types.Predicate;
import org.dcm4che3.net.Device;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.entity.RetrieveTask;
import org.dcm4chee.arc.qmgt.DifferentDeviceException;
import org.dcm4chee.arc.qmgt.IllegalTaskStateException;
import org.dcm4chee.arc.query.util.MatchTask;
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
import javax.ws.rs.core.*;
import java.io.*;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
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

    @Inject
    private Device device;

    @Context
    private HttpServletRequest request;

    @Context
    private HttpHeaders httpHeaders;

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

    @GET
    @NoCache
    public Response listRetrieveTasks(@QueryParam("accept") String accept) {
        logRequest();
        Output output = selectMediaType(accept);
        if (output == null)
            return Response.notAcceptable(
                    Variant.mediaTypes(MediaType.APPLICATION_JSON_TYPE, MediaTypes.TEXT_CSV_UTF8_TYPE).build())
                    .build();

        List<RetrieveTask> tasks = mgr.search(
                MatchTask.matchQueueMessage(
                        null, deviceName, status(), null, null, null),
                MatchTask.matchRetrieveTask(
                        localAET, remoteAET, destinationAET, studyIUID, createdTime, updatedTime),
                parseInt(offset), parseInt(limit));
        return Response.ok(output.entity(tasks), output.type).build();
    }

    @GET
    @NoCache
    @Path("/count")
    @Produces("application/json")
    public Response countRetrieveTasks() {
        logRequest();
        return count( mgr.countRetrieveTasks(
                MatchTask.matchQueueMessage(
                        null, deviceName, status(), null, null, null),
                MatchTask.matchRetrieveTask(
                        localAET, remoteAET, destinationAET, studyIUID, createdTime, updatedTime)));
    }

    @POST
    @Path("{taskPK}/cancel")
    public Response cancelRetrieveTask(@PathParam("taskPK") long pk) {
        logRequest();
        try {
            return Response.status(mgr.cancelRetrieveTask(pk)
                    ? Response.Status.NO_CONTENT
                    : Response.Status.NOT_FOUND)
                    .build();
        } catch (IllegalTaskStateException e) {
            return rsp(Response.Status.CONFLICT, e.getMessage());
        }
    }

    @POST
    @Path("/cancel")
    public Response cancelRetrieveTasks() {
        logRequest();
        QueueMessage.Status status = status();
        if (status == null)
            return rsp(Response.Status.BAD_REQUEST, "Missing query parameter: status");
        if (status != QueueMessage.Status.SCHEDULED && status != QueueMessage.Status.IN_PROCESS)
            return rsp(Response.Status.BAD_REQUEST, "Cannot cancel tasks with status: " + status);

        try {
            LOG.info("Cancel processing of Retrieve Tasks with Status {}", status);
            return count(mgr.cancelRetrieveTasks(
                    MatchTask.matchQueueMessage(
                            null, deviceName, status, null, updatedTime, null),
                    MatchTask.matchRetrieveTask(
                            localAET, remoteAET, destinationAET, studyIUID, createdTime, null),
                    status));
        } catch (IllegalTaskStateException e) {
            return rsp(Response.Status.CONFLICT, e.getMessage());
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
            return rsp(Response.Status.CONFLICT, e.getMessage());
        }
    }

    @POST
    @Path("/reschedule")
    public Response rescheduleRetrieveTasks() {
        logRequest();
        QueueMessage.Status status = status();
        if (status == null)
            return rsp(Response.Status.BAD_REQUEST, "Missing query parameter: status");
        if (status == QueueMessage.Status.SCHEDULED || status == QueueMessage.Status.IN_PROCESS)
            return rsp(Response.Status.BAD_REQUEST, "Cannot reschedule tasks with status: " + status);
        if (deviceName == null)
            return rsp(Response.Status.BAD_REQUEST, "Missing query parameter: dicomDeviceName");
        if (!deviceName.equals(device.getDeviceName()))
            return rsp(Response.Status.CONFLICT,
                    "Cannot reschedule Tasks originally scheduled on Device " + deviceName
                            + " on Device " + device.getDeviceName());

        try {
            Predicate matchQueueMessage = MatchTask.matchQueueMessage(
                    null, deviceName, status, null, null, new Date());
            Predicate matchRetrieveTask = MatchTask.matchRetrieveTask(
                    localAET, remoteAET, destinationAET, studyIUID, createdTime, updatedTime);
            ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
            int fetchSize = arcDev.getQueueTasksFetchSize();
            int count = 0;
            List<Long> retrieveTaskPks;
            do {
                retrieveTaskPks = mgr.getRetrieveTaskPks(matchQueueMessage, matchRetrieveTask, fetchSize);
                for (long pk : retrieveTaskPks)
                    mgr.rescheduleRetrieveTask(pk);
                count += retrieveTaskPks.size();
            } while (retrieveTaskPks.size() >= fetchSize);
            return count(count);
        } catch (IllegalTaskStateException|DifferentDeviceException e) {
            return rsp(Response.Status.CONFLICT, e.getMessage());
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
        int deleted = mgr.deleteTasks(
                MatchTask.matchQueueMessage(
                        null, deviceName, status(), null, null, null),
                MatchTask.matchRetrieveTask(
                        localAET, remoteAET, destinationAET, studyIUID, createdTime, updatedTime));
        return "{\"deleted\":" + deleted + '}';
    }

    private static Response rsp(Response.Status status, Object entity) {
        return Response.status(status).entity(entity).build();
    }

    private static Response count(long count) {
        return rsp(Response.Status.OK, "{\"count\":" + count + '}');
    }

    private Output selectMediaType(String accept) {
        Stream<MediaType> acceptableTypes = httpHeaders.getAcceptableMediaTypes().stream();
        if (accept != null) {
            try {
                MediaType type = MediaType.valueOf(accept);
                return acceptableTypes.anyMatch(type::isCompatible) ? Output.valueOf(type) : null;
            } catch (IllegalArgumentException ae) {
                return null;
            }
        }
        return acceptableTypes.map(Output::valueOf)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private enum Output {
        JSON(MediaType.APPLICATION_JSON_TYPE) {
            @Override
            Object entity(final List<RetrieveTask> tasks) {
                return (StreamingOutput) out -> {
                        JsonGenerator gen = Json.createGenerator(out);
                        gen.writeStartArray();
                        for (RetrieveTask task : tasks)
                            task.writeAsJSONTo(gen);
                        gen.writeEnd();
                        gen.flush();
                };
            }
        },
        CSV(MediaTypes.TEXT_CSV_UTF8_TYPE) {
            @Override
            Object entity(final List<RetrieveTask> tasks) {
                return (StreamingOutput) out -> {
                        Writer writer = new BufferedWriter(new OutputStreamWriter(out, "UTF-8"));
                        writer.write(CSV_HEADER);
                        for (RetrieveTask task : tasks) {
                            task.writeAsCSVTo(writer);
                        }
                        writer.flush();
                };
            }
        };

        final MediaType type;

        Output(MediaType type) {
            this.type = type;
        }

        static Output valueOf(MediaType type) {
            return MediaType.APPLICATION_JSON_TYPE.isCompatible(type) ? Output.JSON
                    : MediaTypes.TEXT_CSV_UTF8_TYPE.isCompatible(type) ? Output.CSV
                    : null;
        }

        abstract Object entity(final List<RetrieveTask> tasks);
    }

    private QueueMessage.Status status() {
        return status != null ? QueueMessage.Status.fromString(status) : null;
    }

    private void logRequest() {
        LOG.info("Process {} {} from {}@{}", request.getMethod(), request.getRequestURI(),
                request.getRemoteUser(), request.getRemoteHost());
    }

    private static int parseInt(String s) {
        return s != null ? Integer.parseInt(s) : 0;
    }
}
