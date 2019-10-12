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
 * **** END LICENSE BLOCK *****
 */
package org.dcm4chee.arc.export.rs;

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
import org.dcm4chee.arc.qmgt.IllegalTaskStateException;
import org.dcm4chee.arc.query.util.TaskQueryParam;
import org.dcm4chee.arc.rs.client.RSClient;
import org.dcm4chee.arc.rs.util.MediaTypeUtils;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonParser;
import javax.persistence.Tuple;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
    public Response listExportTasks(@QueryParam("accept") List<String> accept) {
        logRequest();
        Output output = selectMediaType(accept);
        if (output == null)
            return notAcceptable();

        QueueMessage.Status status = status();
        if (status == QueueMessage.Status.TO_SCHEDULE && batchID != null)
            return Response.ok().build();

        try {
            return Response.ok(
                    output.entity(
                            mgr.listExportTasks(
                                    queueTaskQueryParam(status),
                                    exportTaskQueryParam(deviceName, updatedTime),
                                    parseInt(offset),
                                    parseInt(limit)),
                            arcDev()),
                    output.type)
                    .build();
        } catch (IllegalStateException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @GET
    @NoCache
    @Path("/count")
    @Produces("application/json")
    public Response countExportTasks() {
        logRequest();
        QueueMessage.Status status = status();
        if (status == QueueMessage.Status.TO_SCHEDULE && batchID != null)
            return count(0);

        try {
            return count(mgr.countTasks(queueTaskQueryParam(status),
                    exportTaskQueryParam(deviceName, updatedTime)));
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @POST
    @Path("{taskPK}/cancel")
    public Response cancelExportTask(@PathParam("taskPK") long pk) {
        logRequest();
        QueueMessageEvent queueEvent = new QueueMessageEvent(request, QueueMessageOperation.CancelTasks);

        try {
            return rsp(mgr.cancelExportTask(pk, queueEvent), pk);
        } catch (IllegalTaskStateException e) {
            queueEvent.setException(e);
            return errResponse(e.getMessage(), Response.Status.CONFLICT);
        } catch (Exception e) {
            queueEvent.setException(e);
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        } finally {
            queueMsgEvent.fire(queueEvent);
        }
    }

    @POST
    @Path("/cancel")
    @Produces("application/json")
    public Response cancelExportTasks() {
        logRequest();
        QueueMessage.Status status = status();
        if (status == null)
            return errResponse("Missing query parameter: status", Response.Status.BAD_REQUEST);
        if (status != QueueMessage.Status.SCHEDULED && status != QueueMessage.Status.IN_PROCESS)
            return errResponse("Cannot cancel tasks with status: " + status, Response.Status.BAD_REQUEST);

        BulkQueueMessageEvent queueEvent = new BulkQueueMessageEvent(request, QueueMessageOperation.CancelTasks);
        try {
            LOG.info("Cancel processing of Export Tasks with Status {}", status);
            TaskQueryParam queueTaskQueryParam = queueTaskQueryParam(status);
            queueTaskQueryParam.setUpdatedTime(updatedTime);
            long count = mgr.cancelExportTasks(queueTaskQueryParam, exportTaskQueryParam(deviceName, null));
            queueEvent.setCount(count);
            return count(count);
        } catch (Exception e) {
            queueEvent.setException(e);
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
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
            String taskDeviceName;
            if ((taskDeviceName = mgr.findDeviceNameByPk(pk)) == null)
                return errResponse("No such Export Task : " + pk, Response.Status.NOT_FOUND);

            String devName = newDeviceName != null ? newDeviceName : taskDeviceName;
            if (!devName.equals(device.getDeviceName()))
                return rsClient.forward(request, devName, "");

            mgr.rescheduleExportTask(pk, exporter(exporterID), queueEvent);
            return Response.noContent().build();
        } catch (IllegalStateException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (IllegalTaskStateException e) {
            queueEvent.setException(e);
            return errResponse(e.getMessage(), Response.Status.CONFLICT);
        } catch (Exception e) {
            queueEvent.setException(e);
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        } finally {
            queueMsgEvent.fire(queueEvent);
        }
    }

    @POST
    @Path("/reschedule")
    @Produces("application/json")
    public Response rescheduleExportTasks() {
        return rescheduleTasks(null);
    }

    @POST
    @Path("/reschedule/{ExporterID}")
    @Produces("application/json")
    public Response rescheduleExportTasks(@PathParam("ExporterID") String newExporterID) {
        return rescheduleTasks(newExporterID);
    }

    private Response rescheduleTasks(String newExporterID) {
        logRequest();
        QueueMessage.Status status = status();
        if (status == null)
            return errResponse("Missing query parameter: status", Response.Status.BAD_REQUEST);
        if (status == QueueMessage.Status.TO_SCHEDULE)
            return errResponse("Cannot reschedule tasks with status : TO SCHEDULE", Response.Status.CONFLICT);

        try {
            ExporterDescriptor newExporter = null;
            if (newExporterID != null)
                newExporter = exporter(newExporterID);

            String devName = newDeviceName != null ? newDeviceName : deviceName;
            if (devName != null && !devName.equals(device.getDeviceName()))
                return rsClient.forward(request, devName, "");

            return count(devName == null
                    ? rescheduleOnDistinctDevices(newExporter, status)
                    : rescheduleTasks(newExporter, newDeviceName != null ? null : devName, status));
        } catch (IllegalStateException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private int rescheduleOnDistinctDevices(ExporterDescriptor newExporter, QueueMessage.Status status) throws Exception {
        List<String> distinctDeviceNames = mgr.listDistinctDeviceNames(exportTaskQueryParam(null, updatedTime));
        int count = 0;
        for (String devName : distinctDeviceNames)
            count += devName.equals(device.getDeviceName())
                    ? rescheduleTasks(newExporter, devName, status)
                    : count(rsClient.forward(request, devName, "&dicomDeviceName=" + devName), devName);

        return count;
    }

    private int rescheduleTasks(ExporterDescriptor newExporter, String devName, QueueMessage.Status status) {
        BulkQueueMessageEvent queueEvent = new BulkQueueMessageEvent(request, QueueMessageOperation.RescheduleTasks);
        try {
            int rescheduled = 0;
            int count;
            int rescheduleTasksFetchSize = queueTasksFetchSize();
            do {
                List<Tuple> exportTasks = mgr.exportTaskPksAndExporterIDs(
                    queueTaskQueryParam(status), exportTaskQueryParam(devName, updatedTime), rescheduleTasksFetchSize);
                exportTasks.forEach(exportTask -> {
                    long pk = (long) exportTask.get(0);
                    try {
                        mgr.rescheduleExportTask(pk,
                                newExporter != null ? newExporter : exporter((String) exportTask.get(1)),
                                null);
                    } catch (IllegalTaskStateException e) {
                        LOG.warn("Failed rescheduling of task [pk={}]\n", pk, e);
                    }
                });
                count = exportTasks.size();
                rescheduled += count;
            } while (count >= rescheduleTasksFetchSize);
            queueEvent.setCount(rescheduled);
            LOG.info("Rescheduled {} tasks on device {}", rescheduled, device.getDeviceName());
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
        try {
            return rsp(mgr.deleteExportTask(pk, queueEvent), pk);
        } catch (Exception e) {
            queueEvent.setException(e);
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        } finally {
            queueMsgEvent.fire(queueEvent);
        }
    }

    @DELETE
    @Produces("application/json")
    public Response deleteTasks() {
        logRequest();
        BulkQueueMessageEvent queueEvent = new BulkQueueMessageEvent(request, QueueMessageOperation.DeleteTasks);
        QueueMessage.Status status = status();
        int deleted = 0;

        if (status == QueueMessage.Status.TO_SCHEDULE && batchID != null)
            return deleted(deleted);

        try {
            int count;
            int deleteTasksFetchSize = queueTasksFetchSize();
            do {
                count = mgr.deleteTasks(queueTaskQueryParam(status),
                        exportTaskQueryParam(deviceName, updatedTime),
                        deleteTasksFetchSize);
                deleted += count;
            } while (count >= deleteTasksFetchSize);
            queueEvent.setCount(deleted);
            return deleted(deleted);
        } catch (IllegalStateException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (Exception e) {
            queueEvent.setException(e);
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        } finally {
            bulkQueueMsgEvent.fire(queueEvent);
        }
    }

    private Response rsp(boolean result, long pk) {
        return result
                ? Response.noContent().build()
                : errResponse("No such Export Task : " + pk, Response.Status.NOT_FOUND);
    }

    private static Response count(long count) {
        return Response.ok("{\"count\":" + count + '}').build();
    }

    private static Response deleted(int deleted) {
        return Response.ok("{\"deleted\":" + deleted + '}').build();
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

    private Output selectMediaType(List<String> accept) {
        return  MediaTypeUtils.acceptableMediaTypesOf(httpHeaders, accept)
                .stream()
                .map(Output::valueOf)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private enum Output {
        JSON(MediaType.APPLICATION_JSON_TYPE) {
            @Override
            Object entity(final Iterator<ExportTask> tasks, ArchiveDeviceExtension arcDev) {
                return (StreamingOutput) out -> {
                    JsonGenerator gen = Json.createGenerator(out);
                    gen.writeStartArray();
                    tasks.forEachRemaining(task -> task.writeAsJSONTo(gen, localAETitleOf(arcDev, task)));
                    gen.writeEnd();
                    gen.flush();
                };
            }
        },
        CSV(MediaTypes.TEXT_CSV_UTF8_TYPE) {
            @Override
            Object entity(final Iterator<ExportTask> tasks, ArchiveDeviceExtension arcDev) {
                return (StreamingOutput) out -> {
                    Writer writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
                    ExportTask.writeCSVHeader(writer, delimiter);
                    tasks.forEachRemaining(task -> writeTaskToCSV(arcDev, writer, task));
                    writer.flush();
                };
            }

            private void writeTaskToCSV(ArchiveDeviceExtension arcDev, Writer writer, ExportTask task) {
                try {
                    task.writeAsCSVTo(writer, delimiter, localAETitleOf(arcDev, task));
                } catch (IOException e) {
                    LOG.warn("{}", e);
                }
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

        abstract Object entity(final Iterator<ExportTask> tasks, ArchiveDeviceExtension arcDev);
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

    private Response notAcceptable() {
        LOG.warn("Response Status : Not Acceptable. Accept Media Type(s) in request : \n{}",
                httpHeaders.getAcceptableMediaTypes().stream()
                        .map(MediaType::toString)
                        .collect(Collectors.joining("\n")));
        return Response.notAcceptable(
                Variant.mediaTypes(MediaType.APPLICATION_JSON_TYPE, MediaTypes.TEXT_CSV_UTF8_TYPE).build())
                .build();
    }

    private static int parseInt(String s) {
        return s != null ? Integer.parseInt(s) : 0;
    }

    private ExporterDescriptor exporter(String exporterID) {
        return arcDev().getExporterDescriptorNotNull(exporterID);
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

    private int queueTasksFetchSize() {
        return arcDev().getQueueTasksFetchSize();
    }

    private ArchiveDeviceExtension arcDev() {
        return device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
    }

    private TaskQueryParam queueTaskQueryParam(QueueMessage.Status status) {
        TaskQueryParam taskQueryParam = new TaskQueryParam();
        taskQueryParam.setStatus(status);
        taskQueryParam.setBatchID(batchID);
        return taskQueryParam;
    }

    private TaskQueryParam exportTaskQueryParam(String deviceName, String updatedTime) {
        TaskQueryParam taskQueryParam = new TaskQueryParam();
        taskQueryParam.setExporterIDs(exporterIDs);
        taskQueryParam.setDeviceName(deviceName);
        taskQueryParam.setCreatedTime(createdTime);
        taskQueryParam.setUpdatedTime(updatedTime);
        taskQueryParam.setOrderBy(orderby);
        taskQueryParam.setStudyIUID(studyUID);
        return taskQueryParam;
    }
}
