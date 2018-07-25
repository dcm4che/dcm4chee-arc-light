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
 * Portions created by the Initial Developer are Copyright (C) 2015-2017
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
 */
package org.dcm4chee.arc.export.rs;

import com.querydsl.core.types.Predicate;
import org.dcm4che3.conf.json.JsonReader;
import org.dcm4che3.net.Device;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.entity.ExportTask;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.event.BulkQueueMessageEvent;
import org.dcm4chee.arc.event.QueueMessageEvent;
import org.dcm4chee.arc.event.QueueMessageOperation;
import org.dcm4chee.arc.export.mgt.ExportManager;
import org.dcm4chee.arc.export.mgt.ExportTaskQuery;
import org.dcm4chee.arc.qmgt.IllegalTaskStateException;
import org.dcm4chee.arc.qmgt.QueueManager;
import org.dcm4chee.arc.query.util.MatchTask;
import org.dcm4chee.arc.rs.client.RSClient;
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
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Mar 2017
 */
@RequestScoped
@Path("monitor/export")
public class ExportTaskRS {
    private static final Logger LOG = LoggerFactory.getLogger(ExportTaskRS.class);            

    @Inject
    private ExportManager mgr;

    @Inject
    private QueueManager queueMgr;

    @Inject
    private Device device;

    @Inject
    private RSClient rsClient;

    @Inject
    private Event<QueueMessageEvent> queueMsgEvent;

    @Inject
    private Event<BulkQueueMessageEvent> bulkQueueMsgEvent;

    @Context
    private HttpHeaders httpHeaders;

    @QueryParam("StudyInstanceUID")
    private String studyUID;

    @QueryParam("dicomDeviceName")
    private String deviceName;

    @QueryParam("newDeviceName")
    private String newDeviceName;

    @QueryParam("ExporterID")
    private List<String> exporterIDs;

    @QueryParam("status")
    @Pattern(regexp = "TO SCHEDULE|SCHEDULED|IN PROCESS|COMPLETED|WARNING|FAILED|CANCELED")
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

    @Context
    private HttpServletRequest request;

    @GET
    @NoCache
    public Response listExportTasks(@QueryParam("accept") String accept) {
        logRequest();
        Output output = selectMediaType(accept);
        if (output == null)
            return notAcceptable();

        QueueMessage.Status status = status();
        ExportTaskQuery tasks = mgr.listExportTasks(status,
                matchQueueMessage(status, deviceName, null),
                matchExportTask(updatedTime),
                MatchTask.exportTaskOrder(orderby),
                parseInt(offset), parseInt(limit)
        );
        return Response.ok(output.entity(tasks, device.getDeviceExtension(ArchiveDeviceExtension.class)), output.type).build();
    }

    @GET
    @NoCache
    @Path("/count")
    @Produces("application/json")
    public Response countExportTasks() {
        logRequest();
        QueueMessage.Status status = status();
        return count(mgr.countExportTasks(status,
                matchQueueMessage(status, deviceName, null),
                matchExportTask(updatedTime)));
    }

    @POST
    @Path("{taskPK}/cancel")
    public Response cancelExportTask(@PathParam("taskPK") long pk) {
        logRequest();
        QueueMessageEvent queueEvent = new QueueMessageEvent(request, QueueMessageOperation.CancelTasks);

        try {
            return rsp(mgr.cancelExportTask(pk, queueEvent));
        } catch (IllegalTaskStateException e) {
            queueEvent.setException(e);
            return rsp(Response.Status.CONFLICT, e.getMessage());
        } finally {
            queueMsgEvent.fire(queueEvent);
        }
    }

    @POST
    @Path("/cancel")
    public Response cancelExportTasks() {
        logRequest();
        QueueMessage.Status status = status();
        if (status == null)
            return rsp(Response.Status.BAD_REQUEST, "Missing query parameter: status");
        if (status != QueueMessage.Status.SCHEDULED && status != QueueMessage.Status.IN_PROCESS)
            return rsp(Response.Status.BAD_REQUEST, "Cannot cancel tasks with status: " + status);

        BulkQueueMessageEvent queueEvent = new BulkQueueMessageEvent(request, QueueMessageOperation.CancelTasks);
        try {
            LOG.info("Cancel processing of Export Tasks with Status {}", status);
            long count = mgr.cancelExportTasks(
                    matchQueueMessage(status, deviceName, updatedTime),
                    matchExportTask(null),
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
    @Path("{taskPK}/reschedule/{ExporterID}")
    public Response rescheduleTask(@PathParam("taskPK") long pk, @PathParam("ExporterID") String exporterID) {
        logRequest();
        QueueMessageEvent queueEvent = new QueueMessageEvent(request, QueueMessageOperation.RescheduleTasks);
        try {
            String devName = newDeviceName != null ? newDeviceName : mgr.findDeviceNameByPk(pk);
            if (devName == null)
                return rsp(Response.Status.NOT_FOUND, "Task not found");

            if (!devName.equals(device.getDeviceName()))
                return rsClient.forward(request, newDeviceName, "");

            ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
            ExporterDescriptor exporter = arcDev.getExporterDescriptor(exporterID);
            if (exporter == null)
                return rsp(Response.Status.NOT_FOUND, "No such exporter - " + exporterID);

            mgr.rescheduleExportTask(pk, exporter, queueEvent);
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
    public Response rescheduleExportTasks() {
        return rescheduleTasks(null);
    }

    @POST
    @Path("/reschedule/{ExporterID}")
    public Response rescheduleExportTasks(@PathParam("ExporterID") String newExporterID) {
        return rescheduleTasks(newExporterID);
    }

    private Response rescheduleTasks(String newExporterID) {
        logRequest();
        QueueMessage.Status status = status();
        if (status == null)
            return rsp(Response.Status.BAD_REQUEST, "Missing query parameter: status");

        try {
            ExporterDescriptor newExporter = null;
            if (newExporterID != null)
                newExporter = exporter(newExporterID);

            String devName = newDeviceName != null ? newDeviceName : deviceName;
            if (devName != null && !devName.equals(device.getDeviceName()))
                return rsClient.forward(request, devName, "");

            Predicate matchExportTask = matchExportTask(updatedTime);
            return count(devName == null
                    ? rescheduleOnDistinctDevices(newExporter, status, matchExportTask)
                    : rescheduleTasks(newExporter, status, matchQueueMessage(status, devName, null), matchExportTask));
        } catch (Exception e) {
            return errResponseAsTextPlain(e);
        }
    }

    private int rescheduleOnDistinctDevices(
            ExporterDescriptor newExporter, QueueMessage.Status status, Predicate matchExportTask) throws Exception {
        List<String> distinctDeviceNames = mgr.listDistinctDeviceNames(
                matchQueueMessage(status, null, null),
                matchExportTask);
        int count = 0;
        for (String devName : distinctDeviceNames)
            count += devName.equals(device.getDeviceName())
                    ? rescheduleTasks(newExporter, status, matchQueueMessage(status, devName, null), matchExportTask)
                    : count(rsClient.forward(request, devName, "&dicomDeviceName=" + devName), devName);

        return count;
    }

    private int rescheduleTasks(
            ExporterDescriptor newExporter, QueueMessage.Status status, Predicate matchQueueMessage, Predicate matchExportTask)
            throws Exception {
        BulkQueueMessageEvent queueEvent = new BulkQueueMessageEvent(request, QueueMessageOperation.RescheduleTasks);
        try {
            int count = 0;
            try (ExportTaskQuery exportTasks = mgr.listExportTasks(status,
                    matchQueueMessage, matchExportTask, null, 0, 0)) {
                for (ExportTask task : exportTasks) {
                    mgr.rescheduleExportTask(
                            task,
                            newExporter != null ? newExporter : exporter(task.getExporterID()),
                            null);
                    count++;
                }
            }
            queueEvent.setCount(count);
            LOG.info("Successfully rescheduled {} tasks on device: {}.", count, device.getDeviceName());
            return count;
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
        boolean deleteExportTask = mgr.deleteExportTask(pk, queueEvent);
        queueMsgEvent.fire(queueEvent);
        return rsp(deleteExportTask);
    }

    @DELETE
    public String deleteTasks() {
        logRequest();
        BulkQueueMessageEvent queueEvent = new BulkQueueMessageEvent(request, QueueMessageOperation.DeleteTasks);
        QueueMessage.Status status = status();
        int deleted = mgr.deleteTasks(status,
                matchQueueMessage(status, deviceName, null),
                matchExportTask(updatedTime));
        queueEvent.setCount(deleted);
        bulkQueueMsgEvent.fire(queueEvent);
        return "{\"deleted\":" + deleted + '}';
    }

    private static Response rsp(Response.Status status, Object entity) {
        return Response.status(status).entity(entity).build();
    }

    private Response rsp(Response.Status status) {
        return Response.status(status).build();
    }

    private static Response rsp(boolean result) {
        return Response.status(result
                ? Response.Status.NO_CONTENT
                : Response.Status.NOT_FOUND)
                .build();
    }

    private static Response count(long count) {
        return rsp(Response.Status.OK, "{\"count\":" + count + '}');
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
            Object entity(final ExportTaskQuery tasks, ArchiveDeviceExtension arcDev) {
                return (StreamingOutput) out -> {
                    try (ExportTaskQuery t = tasks) {
                        JsonGenerator gen = Json.createGenerator(out);
                        gen.writeStartArray();
                        for (ExportTask task : t)
                            task.writeAsJSONTo(gen, localAETitleOf(arcDev, task));
                        gen.writeEnd();
                        gen.flush();
                    }
                };
            }
        },
        CSV(MediaTypes.TEXT_CSV_UTF8_TYPE) {
            @Override
            Object entity(final ExportTaskQuery tasks, ArchiveDeviceExtension arcDev) {
                return (StreamingOutput) out -> {
                    try (ExportTaskQuery t = tasks) {
                        Writer writer = new BufferedWriter(new OutputStreamWriter(out, "UTF-8"));
                        ExportTask.writeCSVHeader(writer, delimiter);
                        for (ExportTask task : t)
                            task.writeAsCSVTo(writer, delimiter, localAETitleOf(arcDev, task));
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

        abstract Object entity(final ExportTaskQuery tasks, ArchiveDeviceExtension arcDev);
    }

    private static String localAETitleOf(ArchiveDeviceExtension arcDev, ExportTask task) {
        try {
            return arcDev.getExporterDescriptorNotNull(task.getExporterID()).getAETitle();
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    private QueueMessage.Status status() {
        return status != null ? QueueMessage.Status.fromString(status) : null;
    }

    private Predicate matchExportTask(String updatedTime) {
        return MatchTask.matchExportTask(exporterIDs, deviceName, studyUID, createdTime, updatedTime);
    }

    private Predicate matchQueueMessage(QueueMessage.Status status, String devName, String updatedTime) {
        return MatchTask.matchQueueMessage(
                null, devName, status, batchID, null, null, updatedTime, null);
    }

    private Response notAcceptable() {
        return Response.notAcceptable(
                Variant.mediaTypes(MediaType.APPLICATION_JSON_TYPE, MediaTypes.TEXT_CSV_UTF8_TYPE).build())
                .build();
    }

    private static int parseInt(String s) {
        return s != null ? Integer.parseInt(s) : 0;
    }

    private ExporterDescriptor exporter(String exporterID) {
        return device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class).getExporterDescriptorNotNull(exporterID);
    }

    private void logRequest() {
        LOG.info("Process {} {} from {}@{}", request.getMethod(), request.getRequestURI(),
                request.getRemoteUser(), request.getRemoteHost());
    }

    private Response errResponseAsTextPlain(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String exceptionAsString = sw.toString();
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(exceptionAsString).type("text/plain").build();
    }
}
