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
 *
 */

package org.dcm4chee.arc.diff.rs;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;
import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.IDeviceCache;
import org.dcm4che3.conf.json.JsonReader;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.diff.DiffService;
import org.dcm4chee.arc.entity.AttributesBlob;
import org.dcm4chee.arc.entity.DiffTask;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.entity.Task;
import org.dcm4chee.arc.event.BulkQueueMessageEvent;
import org.dcm4chee.arc.event.QueueMessageEvent;
import org.dcm4chee.arc.event.QueueMessageOperation;
import org.dcm4chee.arc.qmgt.IllegalTaskStateException;
import org.dcm4chee.arc.qmgt.TaskManager;
import org.dcm4chee.arc.query.util.TaskQueryParam;
import org.dcm4chee.arc.query.util.TaskQueryParam1;
import org.dcm4chee.arc.rs.client.RSClient;
import org.dcm4chee.arc.rs.util.MediaTypeUtils;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonParser;
import javax.persistence.Tuple;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.*;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Mar 2018
 */
@RequestScoped
@Path("monitor/diff")
public class DiffTaskRS {
    private static final Logger LOG = LoggerFactory.getLogger(DiffTaskRS.class);

    @Inject
    private DiffService diffService;

    @Inject
    private TaskManager taskManager;

    @Inject
    private Device device;

    @Inject
    private IDeviceCache deviceCache;

    @Inject
    private RSClient rsClient;

    @Context
    private HttpHeaders httpHeaders;

    @Inject
    private Event<QueueMessageEvent> queueMsgEvent;

    @Inject
    private Event<BulkQueueMessageEvent> bulkQueueMsgEvent;

    @Context
    private HttpServletRequest request;

    @QueryParam("taskID")
    private Long taskID;

    @QueryParam("dicomDeviceName")
    private String deviceName;

    @QueryParam("newDeviceName")
    private String newDeviceName;

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

    @Override
    public String toString() {
        String requestURI = request.getRequestURI();
        String queryString = request.getQueryString();
        return queryString == null ? requestURI : requestURI + '?' + queryString;
    }

    @GET
    @NoCache
    public Response listDiffTasks(@QueryParam("accept") List<String> accept) {
        logRequest();
        Output output = selectMediaType(accept);
        if (output == null)
            return notAcceptable();

        try {
            return Response.ok(
                    output.entity(taskManager, taskQueryParam1(deviceName), parseInt(offset), parseInt(limit)),
                    output.type)
                    .build();
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @GET
    @NoCache
    @Path("/count")
    @Produces("application/json")
    public Response countDiffTasks() {
        logRequest();
        try {
            return count(taskManager.countTasks(taskQueryParam1(deviceName)));
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @GET
    @NoCache
    @Path("/{taskPK}/studies")
    @Produces("application/dicom+json,application/json")
    public Response getDiffTaskResult(@PathParam("taskPK") long taskPK) {
        logRequest();
        DiffTask diffTask = diffService.getDiffTask(taskPK);
        if (diffTask == null)
            return errResponse("No such Diff Task : " + taskPK, Response.Status.NOT_FOUND);

        if (diffTask.getMatches() == 0)
            return Response.noContent().build();

        try {
            return Response.ok(
                    entity(diffService.getDiffTaskAttributes(diffTask, parseInt(offset), parseInt(limit))))
                    .build();
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @POST
    @Path("{taskPK}/cancel")
    public Response cancelDiffTask(@PathParam("taskPK") long pk) {
        logRequest();
        QueueMessageEvent queueEvent = new QueueMessageEvent(request, QueueMessageOperation.CancelTasks);
        try {
            return rsp(diffService.cancelDiffTask(pk, queueEvent), pk);
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
    public Response cancelDiffTasks() {
        logRequest();
        QueueMessage.Status status = status();
        if (status == null)
            return errResponse("Missing query parameter: status", Response.Status.BAD_REQUEST);
        if (status != QueueMessage.Status.SCHEDULED && status != QueueMessage.Status.IN_PROCESS)
            return errResponse("Cannot cancel tasks with status: " + status, Response.Status.BAD_REQUEST);

        BulkQueueMessageEvent queueEvent = new BulkQueueMessageEvent(request, QueueMessageOperation.CancelTasks);
        try {
            LOG.info("Cancel processing of Diff Tasks with Status {}", status);
            TaskQueryParam queueTaskQueryParam = queueTaskQueryParam(deviceName, status);
            queueTaskQueryParam.setUpdatedTime(updatedTime);
            long count = diffService.cancelDiffTasks(queueTaskQueryParam, diffTaskQueryParam(null));
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
    @Path("{taskPK}/reschedule")
    public Response rescheduleTask(@PathParam("taskPK") long pk) {
        logRequest();
        QueueMessageEvent queueEvent = new QueueMessageEvent(request, QueueMessageOperation.RescheduleTasks);
        try {
            Tuple tuple = diffService.findDeviceNameAndMsgPropsByPk(pk);
            String taskDeviceName;
            if ((taskDeviceName = (String) tuple.get(0)) == null)
                return errResponse("No such Diff Task : " + pk, Response.Status.NOT_FOUND);

            if (newDeviceName != null)
                validateTaskAssociationInitiator((String) tuple.get(1), deviceCache.findDevice(newDeviceName));

            String devName = newDeviceName != null ? newDeviceName : taskDeviceName;
            if (!devName.equals(device.getDeviceName()))
                return rsClient.forward(request, devName, "");

            Date scheduledTime = new Date();
            diffService.rescheduleDiffTask(pk, queueEvent, scheduledTime);
            return Response.noContent().build();
        } catch (ConfigurationException e) {
            return errResponse(e.getMessage(), Response.Status.CONFLICT);
        } catch (Exception e) {
            queueEvent.setException(e);
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        } finally {
            queueMsgEvent.fire(queueEvent);
        }
    }

    private boolean validateTaskAssociationInitiator(String messageProperties, Device device) throws ConfigurationException {
       javax.json.JsonReader reader = Json.createReader(new StringReader('{' + messageProperties + '}'));
        JsonObject jsonObj = reader.readObject();

        String localAET = jsonObj.getString("LocalAET");
        ApplicationEntity ae = device.getApplicationEntity(localAET, true);
        if (ae == null || !ae.isInstalled())
            throw new ConfigurationException("No such Application Entity " + localAET + " on new device: " + newDeviceName);

        return true;
    }

    @POST
    @Path("/reschedule")
    @Produces("application/json")
    public Response rescheduleDiffTasks() {
        logRequest();
        QueueMessage.Status status = status();
        if (status == null)
            return errResponse("Missing query parameter: status", Response.Status.BAD_REQUEST);

        try {
            String devName = newDeviceName != null ? newDeviceName : deviceName;
            if (devName != null && !devName.equals(device.getDeviceName()))
                return rsClient.forward(request, devName, "");

            TaskQueryParam diffTaskQueryParam = diffTaskQueryParam(updatedTime);
            Date scheduledTime = new Date();
            return newDeviceName != null
                    ? rescheduleValidTasks(queueTaskQueryParam(null, status), diffTaskQueryParam, scheduledTime)
                    : count(devName == null
                        ? rescheduleOnDistinctDevices(diffTaskQueryParam, status, scheduledTime)
                        : rescheduleTasks(
                                queueTaskQueryParam(devName, status),
                                diffTaskQueryParam, scheduledTime));
        } catch (IllegalStateException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private int rescheduleOnDistinctDevices(TaskQueryParam diffTaskQueryParam, QueueMessage.Status status, Date scheduledTime) throws Exception {
        List<String> distinctDeviceNames = diffService.listDistinctDeviceNames(
                                                        queueTaskQueryParam(null, status), diffTaskQueryParam);
        int count = 0;
        for (String devName : distinctDeviceNames)
            count += devName.equals(device.getDeviceName())
                    ? rescheduleTasks(
                        queueTaskQueryParam(devName, status),
                        diffTaskQueryParam, scheduledTime)
                    : count(rsClient.forward(request, devName, "&dicomDeviceName=" + devName), devName);

        return count;
    }

    private int rescheduleTasks(TaskQueryParam queueTaskQueryParam, TaskQueryParam diffTaskQueryParam, Date scheduledTime) {
        BulkQueueMessageEvent queueEvent = new BulkQueueMessageEvent(request, QueueMessageOperation.RescheduleTasks);
        try {
            int rescheduled = 0;
            int count;
            int rescheduleTasksFetchSize = queueTasksFetchSize();
            do {
                List<Long> diffTaskQueueMsgPKs = diffService.listDiffTaskQueueMsgIDs(
                                                                queueTaskQueryParam,
                                                                diffTaskQueryParam,
                                                                rescheduleTasksFetchSize);
                diffTaskQueueMsgPKs.forEach(pk -> diffService.rescheduleDiffTaskByMsgID(pk, scheduledTime));
                count = diffTaskQueueMsgPKs.size();
                rescheduled += count;
            } while (count >= rescheduleTasksFetchSize);
            LOG.info("Rescheduled {} Diff tasks on device {}", rescheduled, device.getDeviceName());
            queueEvent.setCount(rescheduled);
            return rescheduled;
        } catch (Exception e) {
            queueEvent.setException(e);
            throw e;
        } finally {
            bulkQueueMsgEvent.fire(queueEvent);
        }
    }

    private Response rescheduleValidTasks(TaskQueryParam queueTaskQueryParam, TaskQueryParam diffTaskQueryParam, Date scheduledTime) {
        BulkQueueMessageEvent queueEvent = new BulkQueueMessageEvent(request, QueueMessageOperation.RescheduleTasks);
        int rescheduled = 0;
        int failed = 0;
        try {
            int count = 0;
            int rescheduleTaskFetchSize = queueTasksFetchSize();
            do {
                List<Tuple> diffTaskTuples = diffService.listDiffTaskQueueMsgIDAndMsgProps(
                        queueTaskQueryParam, diffTaskQueryParam, rescheduleTaskFetchSize);
                for (Tuple tuple : diffTaskTuples) {
                    Long queueMessagePK = (Long) tuple.get(0);
                    try {
                        if (validateTaskAssociationInitiator((String) tuple.get(1), device)) {
                            diffService.rescheduleDiffTaskByMsgID(queueMessagePK, scheduledTime);
                            count++;
                        }
                    } catch (ConfigurationException e) {
                        LOG.info("Validation of association initiator failed for Diff Task queue message id {} : {}",
                                queueMessagePK, e.getMessage());
                        failed++;
                    }
                }
                rescheduled += count;
            } while (count >= rescheduleTaskFetchSize);
            queueEvent.setCount(rescheduled);
            LOG.info("Rescheduled {} Diff tasks on device {}", rescheduled, device.getDeviceName());
        } catch (Exception e) {
            queueEvent.setException(e);
            throw e;
        } finally {
            queueEvent.setFailed(failed);
            bulkQueueMsgEvent.fire(queueEvent);
        }

        if (failed == 0)
            return count(rescheduled);

        LOG.info("Failed to reschedule {} Diff tasks on device {}", failed, device.getDeviceName());
        return rescheduled > 0
                ? accepted(rescheduled, failed)
                : conflict(failed);
    }

    @DELETE
    @Path("/{taskPK}")
    public Response deleteTask(@PathParam("taskPK") long pk) {
        logRequest();
        QueueMessageEvent queueEvent = new QueueMessageEvent(request, QueueMessageOperation.DeleteTasks);
        try {
            return rsp(diffService.deleteDiffTask(pk, queueEvent), pk);
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
        try {
            int deleted = 0;
            int count;
            int deleteTasksFetchSize = queueTasksFetchSize();
            do {
                count = diffService.deleteTasks(
                        queueTaskQueryParam(deviceName, status()),
                        diffTaskQueryParam(updatedTime),
                        deleteTasksFetchSize);
                deleted += count;
            } while (count >= deleteTasksFetchSize);
            queueEvent.setCount(deleted);
            return Response.ok("{\"deleted\":" + deleted + '}').build();
        } catch (IllegalStateException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (Exception e) {
            queueEvent.setException(e);
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        } finally {
            bulkQueueMsgEvent.fire(queueEvent);
        }
    }

    private Output selectMediaType(List<String> accept) {
        return MediaTypeUtils.acceptableMediaTypesOf(httpHeaders, accept)
                .stream()
                .map(Output::valueOf)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private enum Output {
        JSON(MediaType.APPLICATION_JSON_TYPE) {
            @Override
            Object entity(TaskManager taskManager, TaskQueryParam1 taskQueryParam, int offset, int limit) {
                return taskManager.writeAsJSON(taskQueryParam, offset, limit);
            }
        },
        CSV(MediaTypes.TEXT_CSV_UTF8_TYPE) {
            @Override
            Object entity(TaskManager taskManager, TaskQueryParam1 taskQueryParam, int offset, int limit) {
                return taskManager.writeAsCSV(taskQueryParam, offset, limit, Task.DIFF_CSV_HEADERS, delimiter);
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

        abstract Object entity(TaskManager taskManager, TaskQueryParam1 taskQueryParam, int offset, int limit);
    }

    private StreamingOutput entity(List<byte[]> diffTaskAttributesList) {
        return output -> {
            try (JsonGenerator gen = Json.createGenerator(output)) {
                JSONWriter writer = device.getDeviceExtension(ArchiveDeviceExtension.class)
                                        .encodeAsJSONNumber(new JSONWriter(gen));
                gen.writeStartArray();
                diffTaskAttributesList.forEach(diffTaskAttributes ->
                    writer.write(AttributesBlob.decodeAttributes(diffTaskAttributes, null)));
                gen.writeEnd();
            }
        };
    }

    private void logRequest() {
        LOG.info("Process {} {} from {}@{}",
                request.getMethod(),
                toString(),
                request.getRemoteUser(),
                request.getRemoteHost());
    }

    private Response count(long count) {
        return Response.ok("{\"count\":" + count + '}').build();
    }

    private Response accepted(int rescheduled, int failed) {
        return Response.accepted("{\"count\":" + rescheduled + ", \"failed\":" + failed + '}').build();
    }

    private Response conflict(int failed) {
        return Response.status(Response.Status.CONFLICT).entity("{\"failed\":" + failed + '}').build();
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

    private Response errResponse(String msg, Response.Status status) {
        return errResponseAsTextPlain("{\"errorMessage\":\"" + msg + "\"}", status);
    }

    private Response rsp(boolean result, long pk) {
        return result
                ? Response.noContent().build()
                : errResponse("No such Diff Task : " + pk, Response.Status.NOT_FOUND);
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

    private int queueTasksFetchSize() {
        return device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class).getQueueTasksFetchSize();
    }

    private TaskQueryParam queueTaskQueryParam(String deviceName, QueueMessage.Status status) {
        TaskQueryParam taskQueryParam = new TaskQueryParam();
        taskQueryParam.setStatus(status);
        taskQueryParam.setDeviceName(deviceName);
        taskQueryParam.setBatchID(batchID);
        return taskQueryParam;
    }

    private TaskQueryParam diffTaskQueryParam(String updatedTime) {
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

    private TaskQueryParam1 taskQueryParam1(String deviceName) {
        TaskQueryParam1 taskQueryParam = new TaskQueryParam1();
        taskQueryParam.setTaskPK(taskID);
        taskQueryParam.setDeviceName(deviceName);
        taskQueryParam.setStatus(status);
        taskQueryParam.setBatchID(batchID);
        taskQueryParam.setCreatedTime(createdTime);
        taskQueryParam.setUpdatedTime(updatedTime);
        taskQueryParam.setOrderBy(orderby);
        taskQueryParam.setType(Task.Type.DIFF);
        taskQueryParam.setLocalAET(localAET);
        taskQueryParam.setPrimaryAET(primaryAET);
        taskQueryParam.setSecondaryAET(secondaryAET);
        taskQueryParam.setCompareFields(comparefields);
        taskQueryParam.setCheckMissing(checkMissing);
        taskQueryParam.setCheckDifferent(checkDifferent);
        return taskQueryParam;
    }

}
