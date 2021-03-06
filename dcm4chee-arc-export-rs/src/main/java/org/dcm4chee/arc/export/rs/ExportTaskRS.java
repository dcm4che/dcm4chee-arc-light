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

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;
import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.IDeviceCache;
import org.dcm4che3.conf.json.JsonReader;
import org.dcm4che3.net.Device;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.entity.ExportTask;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.event.BulkQueueMessageEvent;
import org.dcm4chee.arc.event.QueueMessageEvent;
import org.dcm4chee.arc.event.QueueMessageOperation;
import org.dcm4chee.arc.export.mgt.ExportManager;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
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
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private IDeviceCache deviceCache;

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
    private List<String> newDeviceName;

    @QueryParam("ExporterID")
    private List<String> exporterIDs;

    @QueryParam("status")
    @Pattern(regexp = "TO SCHEDULE|SCHEDULED|IN PROCESS|COMPLETED|WARNING|FAILED|CANCELED")
    private String status;

    @QueryParam("createdTime")
    private String createdTime;

    @QueryParam("updatedTime")
    private String updatedTime;

    @QueryParam("scheduledTime")
    private String scheduledTime;

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

    @Override
    public String toString() {
        String requestURI = request.getRequestURI();
        String queryString = request.getQueryString();
        return queryString == null ? requestURI : requestURI + '?' + queryString;
    }

    @GET
    @NoCache
    public Response listExportTasks(@QueryParam("accept") List<String> accept) {
        logRequest();
        Output output = selectMediaType(accept);
        if (output == null)
            return notAcceptable();

        QueueMessage.Status status = status();
        try {
            return Response.ok(
                    output.entity(
                            mgr.listExportTasks(
                                    queueTaskQueryParam(status),
                                    exportTaskQueryParam(deviceName, updatedTime),
                                    parseInt(offset),
                                    parseInt(limit)),
                            deviceCache),
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

            String devName = !newDeviceName.isEmpty() ? newDeviceName.get(0) : taskDeviceName;
            if (!devName.equals(device.getDeviceName()))
                return rsClient.forward(request, devName, "");

            mgr.rescheduleExportTask(pk, exporter(exporterID), HttpServletRequestInfo.valueOf(request), queueEvent,
                    scheduledTime());
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
    @Path("{taskPK}/mark4export/{ExporterID}")
    public Response markTaskForExport(@PathParam("taskPK") long pk, @PathParam("ExporterID") String exporterID) {
        logRequest();
        QueueMessageEvent queueEvent = new QueueMessageEvent(request, QueueMessageOperation.MarkTasksForScheduling);
        try {
            String taskDeviceName;
            if ((taskDeviceName = mgr.findDeviceNameByPk(pk)) == null)
                return errResponse("No such Export Task : " + pk, Response.Status.NOT_FOUND);

            String devName = !newDeviceName.isEmpty() ? newDeviceName.get(0) : taskDeviceName;
            mgr.markForExportTask(
                    pk, devName, exporter(exporterID), HttpServletRequestInfo.valueOf(request), queueEvent, scheduledTime());
            return Response.noContent().build();
        } catch (IllegalStateException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (Exception e) {
            queueEvent.setException(e);
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        } finally {
            queueMsgEvent.fire(queueEvent);
        }
    }


    private Date scheduledTime() {
        if (scheduledTime != null)
            try {
                return new SimpleDateFormat("yyyyMMddhhmmss").parse(scheduledTime);
            } catch (Exception e) {
                LOG.info(e.getMessage());
            }
        return null;
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

        try {
            ExporterDescriptor newExporter = null;
            if (newExporterID != null)
                newExporter = exporter(newExporterID);

            String devName = !newDeviceName.isEmpty() ? newDeviceName.get(0) : deviceName;
            if (devName != null && !devName.equals(device.getDeviceName()))
                return rsClient.forward(request, devName, "");

            return count(devName == null
                    ? rescheduleOnDistinctDevices(newExporter, status)
                    : rescheduleTasks(newExporter,
                                       !newDeviceName.isEmpty()
                                        ? deviceName == null
                                            ? null : deviceName
                                        : devName,
                                    status));
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
            HttpServletRequestInfo httpServletRequestInfo = HttpServletRequestInfo.valueOf(request);
            Date scheduledTime = scheduledTime();
            do {
                List<Tuple> exportTasks = mgr.exportTaskPksAndExporterIDs(
                    queueTaskQueryParam(status), exportTaskQueryParam(devName, updatedTime), rescheduleTasksFetchSize);
                exportTasks.forEach(exportTask -> {
                    long pk = (long) exportTask.get(0);
                    try {
                        mgr.rescheduleExportTask(pk,
                                newExporter != null ? newExporter : exporter((String) exportTask.get(1)),
                                httpServletRequestInfo,
                                null,
                                scheduledTime);
                    } catch (Exception e) {
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

    @POST
    @Path("/mark4export")
    @Produces("application/json")
    public Response mark4Export() {
        return mark4ExportTasks(null);
    }

    @POST
    @Path("/mark4export/{ExporterID}")
    @Produces("application/json")
    public Response mark4Export(@PathParam("ExporterID") String newExporterID) {
        return mark4ExportTasks(newExporterID);
    }

    private Response mark4ExportTasks(String newExporterID) {
        logRequest();
        QueueMessage.Status status = status();
        if (status == null)
            return errResponse("Missing query parameter: status", Response.Status.BAD_REQUEST);

        if (status == QueueMessage.Status.TO_SCHEDULE)
            return errResponse("Cannot mark tasks for export with status: " + status, Response.Status.FORBIDDEN);

        List<String> newDeviceNames = newDeviceName.stream()
                                        .flatMap(newDeviceName -> Stream.of(StringUtils.split(newDeviceName, ',')))
                                        .collect(Collectors.toList());
        if (newDeviceNames.size() > 1)
            return distributeMarkForExport(newExporterID, status, newDeviceNames);

        try {
            ExporterDescriptor newExporter = null;
            if (newExporterID != null)
                newExporter = exporter(newExporterID);

            String devName = !newDeviceNames.isEmpty() ? newDeviceNames.get(0) : deviceName;
            return count(devName == null
                    ? mark4ExportOnDistinctDevices(newExporter, status)
                    : mark4ExportTasks(newExporter,
                        exportTaskQueryParam(!newDeviceNames.isEmpty()
                            ? deviceName == null
                                ? null : deviceName
                            : devName,
                            updatedTime),
                        devName,
                        status));
        }  catch (IllegalStateException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private Response distributeMarkForExport(String newExporterID, QueueMessage.Status status, List<String> newDeviceNames) {
        if (deviceName == null)
            return errResponse("Missing query parameter: deviceName", Response.Status.BAD_REQUEST);

        BulkQueueMessageEvent queueEvent = new BulkQueueMessageEvent(request, QueueMessageOperation.MarkTasksForScheduling);
        int markedForExport = 0;
        try {
            int count;
            int markForExportTasksFetchSize = queueTasksFetchSize();
            HttpServletRequestInfo httpServletRequestInfo = HttpServletRequestInfo.valueOf(request);
            Date scheduledTime = scheduledTime();
            int distributeCount = newDeviceNames.size();
            do {
                List<Tuple> exportTasks = mgr.exportTaskPksAndExporterIDs(
                                                queueTaskQueryParam(status),
                                                exportTaskQueryParam(deviceName, updatedTime),
                                                markForExportTasksFetchSize);
                count = exportTasks.size();
                Iterator<Tuple> iterator = exportTasks.iterator();
                while (iterator.hasNext()) {
                    int index = markedForExport % distributeCount;
                    newDeviceNames.add(0, newDeviceNames.remove(index));
                    String newDevName = newDeviceNames.get(0);

                    Tuple exportTask = iterator.next();
                    long pk = (long) exportTask.get(0);
                    try {
                        mgr.markForExportTask(pk, newDevName,
                                exporterOnDevice(newDevName,
                                        newExporterID != null ? newExporterID : (String) exportTask.get(1)),
                                httpServletRequestInfo,
                                null,
                                scheduledTime);
                        markedForExport++;
                    } catch (Exception e) {
                        LOG.warn("Failed to mark task [pk={}] for export \n", pk, e);
                    }
                    iterator.remove();
                }
            } while (count >= markForExportTasksFetchSize);
            queueEvent.setCount(markedForExport);
        } catch (Exception e) {
            queueEvent.setException(e);
            throw e;
        } finally {
            bulkQueueMsgEvent.fire(queueEvent);
        }
        return count(markedForExport);
    }

    private int mark4ExportOnDistinctDevices(ExporterDescriptor newExporter, QueueMessage.Status status) {
        List<String> distinctDeviceNames = mgr.listDistinctDeviceNames(exportTaskQueryParam(null, updatedTime));
        int count = 0;
        for (String devName : distinctDeviceNames)
            count += mark4ExportTasks(newExporter,
                    exportTaskQueryParam(devName, updatedTime),
                    devName,
                    status);

        return count;
    }

    private int mark4ExportTasks(ExporterDescriptor newExporter, TaskQueryParam exportTaskQueryParam, String devName,
                                 QueueMessage.Status status) {
        BulkQueueMessageEvent queueEvent = new BulkQueueMessageEvent(request, QueueMessageOperation.MarkTasksForScheduling);
        try {
            int markedForExport = 0;
            int count;
            int markForExportTasksFetchSize = queueTasksFetchSize();
            HttpServletRequestInfo httpServletRequestInfo = HttpServletRequestInfo.valueOf(request);
            Date scheduledTime = scheduledTime();
            do {
                List<Tuple> exportTasks = mgr.exportTaskPksAndExporterIDs(
                        queueTaskQueryParam(status), exportTaskQueryParam, markForExportTasksFetchSize);
                exportTasks.forEach(exportTask -> {
                    long pk = (long) exportTask.get(0);
                    try {
                        mgr.markForExportTask(pk, devName,
                                newExporter != null ? newExporter : exporter((String) exportTask.get(1)),
                                httpServletRequestInfo,
                                null,
                                scheduledTime);
                    } catch (Exception e) {
                        LOG.warn("Failed to mark task [pk={}] for export \n", pk, e);
                    }
                });
                count = exportTasks.size();
                markedForExport += count;
            } while (count >= markForExportTasksFetchSize);
            queueEvent.setCount(markedForExport);
            LOG.info("Marked {} tasks on device {} for export", markedForExport, devName);
            return markedForExport;
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
            Object entity(final Iterator<ExportTask> tasks, IDeviceCache deviceCache) {
                return (StreamingOutput) out -> {
                    JsonGenerator gen = Json.createGenerator(out);
                    gen.writeStartArray();
                    tasks.forEachRemaining(task -> task.writeAsJSONTo(gen, localAETitleOf(deviceCache, task)));
                    gen.writeEnd();
                    gen.flush();
                };
            }
        },
        CSV(MediaTypes.TEXT_CSV_UTF8_TYPE) {
            @Override
            Object entity(final Iterator<ExportTask> tasks, IDeviceCache deviceCache) {
                return (StreamingOutput) out -> {
                    Writer writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
                    CSVPrinter printer = new CSVPrinter(writer, CSVFormat.RFC4180
                            .withHeader(ExportTask.header)
                            .withDelimiter(delimiter)
                            .withQuoteMode(QuoteMode.ALL));
                    tasks.forEachRemaining(task -> writeTaskToCSV(printer, task, deviceCache));
                    writer.flush();
                };
            }

            private void writeTaskToCSV(CSVPrinter printer, ExportTask task, IDeviceCache deviceCache) {
                try {
                    task.printRecord(printer, localAETitleOf(deviceCache, task));
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
                            && type.getParameters().containsKey("delimiter")
                            && type.getParameters().get("delimiter").equals("semicolon")
                        ? ';' : ',';
            return csvCompatible;
        }

        private static String localAETitleOf(IDeviceCache deviceCache, ExportTask task) {
            try {
                return deviceCache.findDevice(task.getDeviceName())
                        .getDeviceExtension(ArchiveDeviceExtension.class)
                        .getExporterDescriptorNotNull(task.getExporterID())
                        .getAETitle();
            } catch (ConfigurationException | IllegalArgumentException e) {
                LOG.info(e.getMessage());
            }
            return null;
        }

        abstract Object entity(final Iterator<ExportTask> tasks, IDeviceCache deviceCache);
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

    private ExporterDescriptor exporterOnDevice(String deviceName, String exporterID) throws ConfigurationException {
        if (deviceName.equals(device.getDeviceName()))
            return exporter(exporterID);

        return deviceCache.findDevice(deviceName)
                .getDeviceExtensionNotNull(ArchiveDeviceExtension.class)
                .getExporterDescriptorNotNull(exporterID);
    }

    private void logRequest() {
        LOG.info("Process {} {} from {}@{}",
                request.getMethod(),
                toString(),
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
        return taskQueryParam;
    }

    private TaskQueryParam exportTaskQueryParam(String deviceName, String updatedTime) {
        TaskQueryParam taskQueryParam = new TaskQueryParam();
        taskQueryParam.setBatchID(batchID);
        taskQueryParam.setExporterIDs(exporterIDs.stream()
                                        .flatMap(exporterID -> Stream.of(StringUtils.split(exporterID, ',')))
                                        .collect(Collectors.toList()));
        taskQueryParam.setDeviceName(deviceName);
        taskQueryParam.setCreatedTime(createdTime);
        taskQueryParam.setUpdatedTime(updatedTime);
        taskQueryParam.setOrderBy(orderby);
        taskQueryParam.setStudyIUID(studyUID);
        return taskQueryParam;
    }
}