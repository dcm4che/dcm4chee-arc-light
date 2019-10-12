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

package org.dcm4chee.arc.retrieve.rs;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.IDeviceCache;
import org.dcm4che3.conf.json.JsonReader;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.entity.RetrieveTask;
import org.dcm4chee.arc.event.BulkQueueMessageEvent;
import org.dcm4chee.arc.event.QueueMessageEvent;
import org.dcm4chee.arc.event.QueueMessageOperation;
import org.dcm4chee.arc.qmgt.IllegalTaskStateException;
import org.dcm4chee.arc.query.util.TaskQueryParam;
import org.dcm4chee.arc.retrieve.mgt.RetrieveManager;
import org.dcm4chee.arc.rs.client.RSClient;
import org.dcm4chee.arc.rs.util.MediaTypeUtils;
import org.dcm4chee.arc.validation.constraints.ValidList;
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
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2017
 */
@RequestScoped
@Path("monitor/retrieve")
public class RetrieveTaskRS {

    private static final Logger LOG = LoggerFactory.getLogger(RetrieveTaskRS.class);

    @Inject
    private RetrieveManager mgr;

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
    private HttpServletRequest request;

    @Context
    private HttpHeaders httpHeaders;

    @QueryParam("dicomDeviceName")
    private String deviceName;

    @QueryParam("newDeviceName")
    private String newDeviceName;

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

    @QueryParam("dcmQueueName")
    @ValidList(allowed = {"Retrieve1",
            "Retrieve2",
            "Retrieve3",
            "Retrieve4",
            "Retrieve5",
            "Retrieve6",
            "Retrieve7",
            "Retrieve8",
            "Retrieve9",
            "Retrieve10",
            "Retrieve11",
            "Retrieve12",
            "Retrieve13"},
            message = "Invalid Retrieve Queue selected")
    private List<String> dcmQueueName;

    @QueryParam("newQueueName")
    @Pattern(regexp =
            "Retrieve1|" +
            "Retrieve2|" +
            "Retrieve3|" +
            "Retrieve4|" +
            "Retrieve5|" +
            "Retrieve6|" +
            "Retrieve7|" +
            "Retrieve8|" +
            "Retrieve9|" +
            "Retrieve10|" +
            "Retrieve11|" +
            "Retrieve12|" +
            "Retrieve13")
    private String newQueueName;

    @GET
    @NoCache
    public Response listRetrieveTasks(@QueryParam("accept") List<String> accept) {
        logRequest();
        Output output = selectMediaType(accept);
        if (output == null)
            return notAcceptable();

        try {
            return Response.ok(output.entity(mgr.listRetrieveTasks(
                                    queueTaskQueryParam(status()),
                                    retrieveTaskQueryParam(deviceName, updatedTime),
                                    parseInt(offset),
                                    parseInt(limit))),
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
    public Response countRetrieveTasks() {
        logRequest();
        try {
            return count(mgr.countTasks(
                    queueTaskQueryParam(status()),
                    retrieveTaskQueryParam(deviceName, updatedTime)));
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @POST
    @Path("{taskPK}/cancel")
    public Response cancelRetrieveTask(@PathParam("taskPK") long pk) {
        logRequest();
        QueueMessageEvent queueEvent = new QueueMessageEvent(request, QueueMessageOperation.CancelTasks);
        try {
            return rsp(mgr.cancelRetrieveTask(pk, queueEvent), pk);
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
    public Response cancelRetrieveTasks() {
        logRequest();
        QueueMessage.Status status = status();
        if (status == null)
            return errResponse("Missing query parameter: status", Response.Status.BAD_REQUEST);
        if (status != QueueMessage.Status.SCHEDULED && status != QueueMessage.Status.IN_PROCESS)
            return errResponse("Cannot cancel tasks with status: " + status, Response.Status.BAD_REQUEST);

        BulkQueueMessageEvent queueEvent = new BulkQueueMessageEvent(request, QueueMessageOperation.CancelTasks);
        try {
            LOG.info("Cancel processing of Retrieve Tasks with Status {}", status);
            TaskQueryParam queueTaskQueryParam = queueTaskQueryParam(status);
            queueTaskQueryParam.setUpdatedTime(updatedTime);
            long count = mgr.cancelRetrieveTasks(queueTaskQueryParam, retrieveTaskQueryParam(deviceName, null));
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
            Tuple tuple = mgr.findDeviceNameAndLocalAETByPk(pk);
            String taskDeviceName;
            if ((taskDeviceName = (String) tuple.get(0)) == null)
                return errResponse("No such Retrieve Task : " + pk, Response.Status.NOT_FOUND);

            if (newQueueName != null && arcDev().getQueueDescriptor(newQueueName) == null)
                return errResponse("No such Queue : " + newQueueName, Response.Status.NOT_FOUND);

            if (newDeviceName != null)
                validateTaskAssociationInitiator((String) tuple.get(1), deviceCache.findDevice(newDeviceName));

            String devName = newDeviceName != null ? newDeviceName : taskDeviceName;
            if (!devName.equals(device.getDeviceName()))
                return rsClient.forward(request, devName, "");

            mgr.rescheduleRetrieveTask(pk, newQueueName, queueEvent);
            return Response.noContent().build();
        } catch (IllegalStateException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (ConfigurationException e) {
            return errResponse(e.getMessage(), Response.Status.CONFLICT);
        } catch (Exception e) {
            queueEvent.setException(e);
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        } finally {
            queueMsgEvent.fire(queueEvent);
        }
    }

    private boolean validateTaskAssociationInitiator(String localAET, Device device) throws ConfigurationException {
        ApplicationEntity ae = device.getApplicationEntity(localAET, true);
        if (ae == null || !ae.isInstalled())
            throw new ConfigurationException("No such Application Entity " + localAET + " on new device: " + newDeviceName);

        return true;
    }

    @POST
    @Path("/reschedule")
    @Produces("application/json")
    public Response rescheduleRetrieveTasks() {
        logRequest();
        QueueMessage.Status status = status();
        if (status == null)
            return errResponse("Missing query parameter: status", Response.Status.BAD_REQUEST);

        try {
            if (newQueueName != null && arcDev().getQueueDescriptor(newQueueName) == null)
                return errResponse("No such Queue : " + newQueueName, Response.Status.NOT_FOUND);

            String devName = newDeviceName != null ? newDeviceName : deviceName;
            if (devName != null && !devName.equals(device.getDeviceName()))
                return rsClient.forward(request, devName, "");


            return newDeviceName != null
                    ? rescheduleValidTasks(queueTaskQueryParam(status), retrieveTaskQueryParam(null, updatedTime))
                    : count(devName == null
                        ? rescheduleOnDistinctDevices(status)
                        : rescheduleTasks(
                            queueTaskQueryParam(status),
                            retrieveTaskQueryParam(devName, updatedTime)));
        } catch (IllegalStateException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private int rescheduleOnDistinctDevices(QueueMessage.Status status)
            throws Exception {
        List<String> distinctDeviceNames = mgr.listDistinctDeviceNames(retrieveTaskQueryParam(null, updatedTime));
        int count = 0;
        for (String devName : distinctDeviceNames)
            count += devName.equals(device.getDeviceName())
                    ? rescheduleTasks(
                            queueTaskQueryParam(status),
                            retrieveTaskQueryParam(devName, updatedTime))
                    : count(rsClient.forward(request, devName, "&dicomDeviceName=" + devName), devName);
        return count;
    }

    private int rescheduleTasks(TaskQueryParam queueTaskQueryParam, TaskQueryParam retrieveTaskQueryParam) {
        BulkQueueMessageEvent bulkMsgQueueEvent = new BulkQueueMessageEvent(request, QueueMessageOperation.RescheduleTasks);
        try {
            int rescheduled = 0;
            int rescheduleTasksFetchSize = queueTasksFetchSize();
            do {
                List<Long> retrieveTaskPks = mgr.listRetrieveTaskPks(
                        queueTaskQueryParam, retrieveTaskQueryParam, rescheduleTasksFetchSize);
                retrieveTaskPks.forEach(
                        pk -> mgr.rescheduleRetrieveTask(
                                pk,
                                newQueueName,
                                new QueueMessageEvent(request, QueueMessageOperation.RescheduleTasks)));

                rescheduled += retrieveTaskPks.size();
            } while (rescheduled >= rescheduleTasksFetchSize);
            bulkMsgQueueEvent.setCount(rescheduled);
            LOG.info("Rescheduled {} tasks on device {}", rescheduled, device.getDeviceName());
            return rescheduled;
        } catch (Exception e) {
            bulkMsgQueueEvent.setException(e);
            throw e;
        } finally {
            bulkQueueMsgEvent.fire(bulkMsgQueueEvent);
        }
    }

    private Response rescheduleValidTasks(TaskQueryParam queueTaskQueryParam, TaskQueryParam retrieveTaskQueryParam) {
        BulkQueueMessageEvent queueEvent = new BulkQueueMessageEvent(request, QueueMessageOperation.RescheduleTasks);
        int rescheduled = 0;
        int failed = 0;
        try {
            int count = 0;
            int rescheduleTaskFetchSize = queueTasksFetchSize();
            do {
                List<Tuple> retrieveTaskTuples = mgr.listRetrieveTaskPkAndLocalAETs(
                        queueTaskQueryParam, retrieveTaskQueryParam, rescheduleTaskFetchSize);
                for (Tuple tuple : retrieveTaskTuples) {
                    Long retrieveTaskPk = (Long) tuple.get(0);
                    try {
                        if (validateTaskAssociationInitiator((String) tuple.get(1), device)) {
                            mgr.rescheduleRetrieveTask(
                                    retrieveTaskPk,
                                    newQueueName,
                                    new QueueMessageEvent(request, QueueMessageOperation.RescheduleTasks));
                            count++;
                        }
                    } catch (ConfigurationException e) {
                        LOG.info("Validation of association initiator failed for Retrieve Task [pk={}] : {}",
                                retrieveTaskPk, e.getMessage());
                        failed++;
                    }
                }
                rescheduled += count;
            } while (count >= rescheduleTaskFetchSize);
            queueEvent.setCount(rescheduled);
            LOG.info("Rescheduled {} Retrieve tasks on device {}", rescheduled, device.getDeviceName());
        } catch (Exception e) {
            queueEvent.setException(e);
            throw e;
        } finally {
            queueEvent.setFailed(failed);
            bulkQueueMsgEvent.fire(queueEvent);
        }

        if (failed == 0)
            return count(rescheduled);

        LOG.info("Failed to reschedule {} Retrieve tasks on device {}", failed, device.getDeviceName());
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
            return rsp(mgr.deleteRetrieveTask(pk, queueEvent), pk);
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
                count = mgr.deleteTasks(
                        queueTaskQueryParam(status()),
                        retrieveTaskQueryParam(deviceName, updatedTime),
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

    private Response rsp(boolean result, long pk) {
        return result
                ? Response.noContent().build()
                : errResponse("No such Retrieve Task : " + pk, Response.Status.NOT_FOUND);
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
            Object entity(final Iterator<RetrieveTask> tasks) {
                return (StreamingOutput) out -> {
                    JsonGenerator gen = Json.createGenerator(out);
                    gen.writeStartArray();
                    tasks.forEachRemaining(task -> task.writeAsJSONTo(gen));
                    gen.writeEnd();
                    gen.flush();
                };
            }
        },
        CSV(MediaTypes.TEXT_CSV_UTF8_TYPE) {
            @Override
            Object entity(final Iterator<RetrieveTask> tasks) {
                return (StreamingOutput) out -> {
                    Writer writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
                    RetrieveTask.writeCSVHeader(writer, delimiter);
                    tasks.forEachRemaining(task -> writeTaskToCSV(writer, task));
                    writer.flush();
                };
            }

            private void writeTaskToCSV(Writer writer, RetrieveTask task) {
                try {
                    task.writeAsCSVTo(writer, delimiter);
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

        abstract Object entity(final Iterator<RetrieveTask> tasks);
    }

    private QueueMessage.Status status() {
        return status != null ? QueueMessage.Status.fromString(status) : null;
    }

    private Response notAcceptable() {
        LOG.warn("Response Not Acceptable caused by Accept Media Type(s) in HTTP request : \n{}",
                httpHeaders.getAcceptableMediaTypes().stream()
                        .map(MediaType::toString)
                        .collect(Collectors.joining("\n")));
        return Response.notAcceptable(
                Variant.mediaTypes(MediaType.APPLICATION_JSON_TYPE, MediaTypes.TEXT_CSV_UTF8_TYPE).build())
                .build();
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

    private TaskQueryParam retrieveTaskQueryParam(String deviceName, String updatedTime) {
        TaskQueryParam taskQueryParam = new TaskQueryParam();
        taskQueryParam.setDeviceName(deviceName);
        taskQueryParam.setQueueName(dcmQueueName);
        taskQueryParam.setLocalAET(localAET);
        taskQueryParam.setRemoteAET(remoteAET);
        taskQueryParam.setBatchID(batchID);
        taskQueryParam.setDestinationAET(destinationAET);
        taskQueryParam.setStudyIUID(studyIUID);
        taskQueryParam.setCreatedTime(createdTime);
        taskQueryParam.setUpdatedTime(updatedTime);
        taskQueryParam.setOrderBy(orderby);
        return taskQueryParam;
    }
}
