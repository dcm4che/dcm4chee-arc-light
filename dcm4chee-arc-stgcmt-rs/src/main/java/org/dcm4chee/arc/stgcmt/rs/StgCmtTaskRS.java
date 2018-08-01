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
 * *** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.stgcmt.rs;

import com.querydsl.core.types.Predicate;
import org.dcm4che3.conf.json.JsonReader;
import org.dcm4che3.net.Device;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.entity.StgCmtTask;
import org.dcm4chee.arc.event.BulkQueueMessageEvent;
import org.dcm4chee.arc.event.QueueMessageEvent;
import org.dcm4chee.arc.event.QueueMessageOperation;
import org.dcm4chee.arc.qmgt.IllegalTaskStateException;
import org.dcm4chee.arc.query.util.MatchTask;
import org.dcm4chee.arc.rs.client.RSClient;
import org.dcm4chee.arc.stgcmt.StgCmtManager;
import org.dcm4chee.arc.stgcmt.StgCmtTaskQuery;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonParser;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.*;
import java.util.List;
import java.util.Objects;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Aug 2018
 */
@RequestScoped
@Path("monitor/stgcmt")
public class StgCmtTaskRS {

    private static final Logger LOG = LoggerFactory.getLogger(StgCmtTaskRS.class);

    @Inject
    private Device device;

    @Inject
    private StgCmtManager stgCmtMgr;

    @Inject
    private RSClient rsClient;

    @Inject
    private Event<QueueMessageEvent> queueMsgEvent;

    @Inject
    private Event<BulkQueueMessageEvent> bulkQueueMsgEvent;

    @Context
    private HttpServletRequest request;

    @Context
    private HttpHeaders httpHeaders;

    @QueryParam("dicomDeviceName")
    private String deviceName;

    @QueryParam("newDeviceName")
    private String newDeviceName;

    @QueryParam("LocalAET")
    private String localAET;

    @QueryParam("StudyInstanceUID")
    private String studyIUID;

    @QueryParam("status")
    @Pattern(regexp = "SCHEDULED|IN PROCESS|COMPLETED|WARNING|FAILED|CANCELED")
    private String status;

    @QueryParam("createdTime")
    private String createdTime;

    @QueryParam("updatedTime")
    private String updatedTime;

    @QueryParam("batchID")
    private String batchID;

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
    public Response listStgCmtTasks(@QueryParam("accept") String accept) {
        logRequest();
        Output output = selectMediaType(accept);
        if (output == null)
            return notAcceptable();

        StgCmtTaskQuery tasks = stgCmtMgr.listStgCmtTasks(
                matchQueueMessage(status(), deviceName, null),
                matchStgCmtTask(updatedTime),
                MatchTask.stgCmtTaskOrder(orderby), parseInt(offset), parseInt(limit));
        return Response.ok(output.entity(tasks), output.type).build();
    }

    @GET
    @NoCache
    @Path("/count")
    @Produces("application/json")
    public Response countStgCmtTasks() {
        logRequest();
        return count(stgCmtMgr.countStgCmtTasks(
                matchQueueMessage(status(), deviceName, null),
                matchStgCmtTask(updatedTime)));
    }

    @POST
    @Path("{taskPK}/cancel")
    public Response cancelStgCmtTask(@PathParam("taskPK") long pk) {
        logRequest();
        QueueMessageEvent queueEvent = new QueueMessageEvent(request, QueueMessageOperation.CancelTasks);
        try {
            return rsp(stgCmtMgr.cancelStgCmtTask(pk, queueEvent));
        } catch (IllegalTaskStateException e) {
            queueEvent.setException(e);
            return rsp(Response.Status.CONFLICT, e.getMessage());
        } finally {
            queueMsgEvent.fire(queueEvent);
        }
    }

    @POST
    @Path("/cancel")
    public Response cancelStgCmtTasks() {
        logRequest();
        QueueMessage.Status status = status();
        if (status == null)
            return rsp(Response.Status.BAD_REQUEST, "Missing query parameter: status");
        if (status != QueueMessage.Status.SCHEDULED && status != QueueMessage.Status.IN_PROCESS)
            return rsp(Response.Status.BAD_REQUEST, "Cannot cancel tasks with status: " + status);

        BulkQueueMessageEvent queueEvent = new BulkQueueMessageEvent(request, QueueMessageOperation.CancelTasks);
        try {
            LOG.info("Cancel processing of Storage Commitment Tasks with Status {}", status);
            long count = stgCmtMgr.cancelStgCmtTasks(
                    matchQueueMessage(status, deviceName, updatedTime),
                    matchStgCmtTask(null),
                    status);
            queueEvent.setCount(count);
            return count(count);
        } catch (IllegalTaskStateException e) {
            queueEvent.setException(e);
            return rsp(Response.Status.CONFLICT, e.getMessage());
        } finally {
            bulkQueueMsgEvent.fire(queueEvent);
        }
    }

    @POST
    @Path("{taskPK}/reschedule")
    public Response rescheduleTask(@PathParam("taskPK") long pk) {
        logRequest();
        QueueMessageEvent queueEvent = new QueueMessageEvent(request, QueueMessageOperation.RescheduleTasks);
        try {
            String devName = newDeviceName != null ? newDeviceName : stgCmtMgr.findDeviceNameByPk(pk);
            if (devName == null)
                return rsp(Response.Status.NOT_FOUND, "Task not found");

            if (!devName.equals(device.getDeviceName()))
                return rsClient.forward(request, newDeviceName, "");

            stgCmtMgr.rescheduleStgCmtTask(pk, queueEvent);
            return rsp(Response.Status.NO_CONTENT);
        } catch (Exception e) {
            queueEvent.setException(e);
            return errResponseAsTextPlain(e);
        } finally {
            queueMsgEvent.fire(queueEvent);
        }
    }

    @POST
    @Path("/reschedule")
    public Response rescheduleStgCmtTasks() {
        logRequest();
        QueueMessage.Status status = status();
        if (status == null)
            return rsp(Response.Status.BAD_REQUEST, "Missing query parameter: status");

        try {
            String devName = newDeviceName != null ? newDeviceName : deviceName;
            if (devName != null && !devName.equals(device.getDeviceName()))
                return rsClient.forward(request, devName, "");

            Predicate matchStgCmtTask = matchStgCmtTask(updatedTime);
            return count(devName == null
                    ? rescheduleOnDistinctDevices(status, matchStgCmtTask)
                    : rescheduleTasks(matchQueueMessage(status, devName, null), matchStgCmtTask));
        } catch (Exception e) {
            return errResponseAsTextPlain(e);
        }
    }

    private int rescheduleOnDistinctDevices(QueueMessage.Status status, Predicate matchStgCmtTask) throws Exception {
        List<String> distinctDeviceNames = stgCmtMgr.listDistinctDeviceNames(
                matchQueueMessage(status, null, null),
                matchStgCmtTask);
        int count = 0;
        for (String devName : distinctDeviceNames)
            count += devName.equals(device.getDeviceName())
                    ? rescheduleTasks(matchQueueMessage(status, devName, null), matchStgCmtTask)
                    : count(rsClient.forward(request, devName, "&dicomDeviceName=" + devName), devName);
        return count;
    }

    private int rescheduleTasks(Predicate matchQueueMessage, Predicate matchStgCmtTask) {
        BulkQueueMessageEvent queueEvent = new BulkQueueMessageEvent(request, QueueMessageOperation.RescheduleTasks);
        try {
            int rescheduled = 0;
            int count;
            int rescheduleTasksFetchSize = queueTasksFetchSize();
            do {
                List<String> stgCmtTaskQueueMsgIDs = stgCmtMgr.listStgCmtTaskQueueMsgIDs(matchQueueMessage, matchStgCmtTask, rescheduleTasksFetchSize);
                for (String stgCmtTaskQueueMsgID : stgCmtTaskQueueMsgIDs)
                    stgCmtMgr.rescheduleStgCmtTask(stgCmtTaskQueueMsgID);
                count = stgCmtTaskQueueMsgIDs.size();
                rescheduled += count;
            } while (count >= rescheduleTasksFetchSize);
            queueEvent.setCount(rescheduled);
            LOG.info("Successfully rescheduled {} tasks on device: {}.", rescheduled, device.getDeviceName());
            return rescheduled;
        } catch (Exception e) {
            queueEvent.setException(e);
            throw e;
        } finally {
            bulkQueueMsgEvent.fire(queueEvent);
        }
    }

    @DELETE
    @Path("/{taskPK}")
    public Response deleteTask(@PathParam("taskPK") long pk) {
        logRequest();
        QueueMessageEvent queueEvent = new QueueMessageEvent(request, QueueMessageOperation.DeleteTasks);
        boolean deleteStgCmtTask = stgCmtMgr.deleteStgCmtTask(pk, queueEvent);
        queueMsgEvent.fire(queueEvent);
        return rsp(deleteStgCmtTask);
    }

    @DELETE
    public String deleteTasks() {
        logRequest();
        BulkQueueMessageEvent queueEvent = new BulkQueueMessageEvent(request, QueueMessageOperation.DeleteTasks);
        int deleted = 0;
        int count;
        int deleteTasksFetchSize = queueTasksFetchSize();
        do {
            count = stgCmtMgr.deleteTasks(
                    matchQueueMessage(status(), deviceName, null),
                    matchStgCmtTask(updatedTime),
                    deleteTasksFetchSize);
            deleted += count;
        } while (count >= deleteTasksFetchSize);
        queueEvent.setCount(deleted);
        bulkQueueMsgEvent.fire(queueEvent);
        return "{\"deleted\":" + deleted + '}';
    }

    private Output selectMediaType(String accept) {
        if (accept != null)
            httpHeaders.getRequestHeaders().putSingle("Accept", accept);

        return httpHeaders.getAcceptableMediaTypes().stream()
                .map(Output::valueOf)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private enum Output {
        JSON(MediaType.APPLICATION_JSON_TYPE) {
            @Override
            Object entity(final StgCmtTaskQuery tasks) {
                return (StreamingOutput) out -> {
                    try (StgCmtTaskQuery t = tasks) {
                        JsonGenerator gen = Json.createGenerator(out);
                        gen.writeStartArray();
                        for (StgCmtTask task : t)
                            task.writeAsJSONTo(gen);
                        gen.writeEnd();
                        gen.flush();
                    }
                };
            }
        },
        CSV(MediaTypes.TEXT_CSV_UTF8_TYPE) {
            @Override
            Object entity(final StgCmtTaskQuery tasks) {
                return (StreamingOutput) out -> {
                    try (StgCmtTaskQuery t = tasks) {
                        Writer writer = new BufferedWriter(new OutputStreamWriter(out, "UTF-8"));
                        StgCmtTask.writeCSVHeader(writer, delimiter);
                        for (StgCmtTask task : t)
                            task.writeAsCSVTo(writer, delimiter);
                        writer.flush();
                    }
                };
            }
        };

        private static char delimiter;
        final MediaType type;

        Output(MediaType type) {
            this.type = type;
        }

        static Output valueOf(MediaType type) {
            return MediaType.APPLICATION_JSON_TYPE.isCompatible(type) ? Output.JSON
                    : isCSV(type) ? Output.CSV
                    : null;
        }

        private static boolean isCSV(MediaType type) {
            boolean csvCompatible = MediaTypes.TEXT_CSV_UTF8_TYPE.isCompatible(type);
            delimiter = csvCompatible
                    && type.getParameters().keySet().contains("delimiter")
                    && type.getParameters().get("delimiter").equals("semicolon")
                    ? ';' : ',';
            return csvCompatible;
        }

        abstract Object entity(final StgCmtTaskQuery tasks);
    }

    private int count(Response response, String devName) {
        int count = 0;
        if (response.getStatus() == Response.Status.OK.getStatusCode()) {
            JsonParser parser = Json.createParser(new StringReader(response.readEntity(String.class)));
            JsonReader reader = new JsonReader(parser);
            reader.next();
            reader.expect(JsonParser.Event.START_OBJECT);
            while (reader.next() == JsonParser.Event.KEY_NAME)
                count = reader.intValue();
            LOG.info("Successfully rescheduled {} tasks on device {}", count, devName);
        } else {
            LOG.warn("Failed rescheduling of tasks on device {}. Response received with status: {} and entity: {}",
                    devName, response.getStatus(), response.getEntity());
        }
        return count;
    }

    private static Response count(long count) {
        return rsp(Response.Status.OK, "{\"count\":" + count + '}');
    }

    private static Response rsp(Response.Status status, Object entity) {
        return Response.status(status).entity(entity).build();
    }

    private static Response rsp(boolean result) {
        return Response.status(result
                ? Response.Status.NO_CONTENT
                : Response.Status.NOT_FOUND)
                .build();
    }

    private Response rsp(Response.Status status) {
        return Response.status(status).build();
    }

    private QueueMessage.Status status() {
        return status != null ? QueueMessage.Status.fromString(status) : null;
    }

    private static int parseInt(String s) {
        return s != null ? Integer.parseInt(s) : 0;
    }

    private Predicate matchQueueMessage(QueueMessage.Status status, String devName, String updatedTime) {
        return MatchTask.matchQueueMessage(
                null, devName, status, batchID, null, null, updatedTime, null);
    }

    private Predicate matchStgCmtTask(String updatedTime) {
        return MatchTask.matchStgCmtTask(localAET, studyIUID, createdTime, updatedTime);
    }

    private Response notAcceptable() {
        return Response.notAcceptable(
                Variant.mediaTypes(MediaType.APPLICATION_JSON_TYPE, MediaTypes.TEXT_CSV_UTF8_TYPE).build())
                .build();
    }

    private Response errResponseAsTextPlain(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String exceptionAsString = sw.toString();
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(exceptionAsString).type("text/plain").build();
    }

    private int queueTasksFetchSize() {
        return device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class).getQueueTasksFetchSize();
    }

    private void logRequest() {
        LOG.info("Process {} {} from {}@{}", request.getMethod(), request.getRequestURI(),
                request.getRemoteUser(), request.getRemoteHost());
    }
}
