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

package org.dcm4chee.arc.qmgt.rs;

import org.dcm4che3.conf.api.IDeviceCache;
import org.dcm4che3.conf.json.JsonReader;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.qmgt.QueueManager;
import org.dcm4chee.arc.qmgt.TaskManager;
import org.dcm4chee.arc.query.util.TaskQueryParam1;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonParser;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.*;
import java.util.Collections;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Sep 2015
 */
@RequestScoped
@Path("queue/{queueName}")
public class QueueManagerRS {
    private static final Logger LOG = LoggerFactory.getLogger(QueueManagerRS.class);

    @Inject
    private QueueManager queueManager;

    @Inject
    private TaskManager taskManager;

    @Inject
    private Device device;

    @Inject
    private IDeviceCache deviceCache;

    @Context
    private HttpServletRequest request;

    @PathParam("queueName")
    private String queueName;

    @QueryParam("taskID")
    private Long taskID;

    @QueryParam("dicomDeviceName")
    private String deviceName;

    @QueryParam("newDeviceName")
    private String newDeviceName;

    @QueryParam("status")
    @Pattern(regexp = "SCHEDULED|IN PROCESS|COMPLETED|WARNING|FAILED|CANCELED")
    private String status;

    @QueryParam("offset")
    @Pattern(regexp = "0|([1-9]\\d{0,4})")
    private String offset;

    @QueryParam("limit")
    @Pattern(regexp = "[1-9]\\d{0,4}")
    private String limit;

    @QueryParam("createdTime")
    private String createdTime;

    @QueryParam("updatedTime")
    private String updatedTime;

    @QueryParam("batchID")
    private String batchID;

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
    @Produces("application/json")
    public Response search() {
        logRequest();
        try {
            return Response.ok(
                    taskManager.writeAsJSON(taskQueryParam(deviceName), parseInt(offset), parseInt(limit)))
                    .build();
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @GET
    @NoCache
    @Path("/count")
    @Produces("application/json")
    public Response countTasks() {
        logRequest();
        return taskManager.countTasks(taskQueryParam(deviceName));
    }

    @POST
    @Path("{taskID}/cancel")
    public Response cancel(@PathParam("taskID") long taskID) {
        logRequest();
        return taskManager.cancelTask(taskQueryParam(taskID), request);
    }

    @POST
    @Path("/cancel")
    @Produces("application/json")
    public Response cancelTasks() {
        logRequest();
        return taskManager.cancelTasks(taskQueryParam(deviceName), request);
    }

/*
    @POST
    @Path("{taskID}/reschedule")
    public Response rescheduleMessage(@PathParam("taskID") long taskID) {
        logRequest();
        Task task = taskManager.findTask(taskQueryParam(taskID));
        if (task == null)
            return errResponse("No such Task: " + taskID, Response.Status.NOT_FOUND);

        TaskEvent queueEvent = new TaskEvent(request, TaskOperation.RescheduleTasks, taskID);
        try {
            Tuple tuple = queueManager.findDeviceNameAndMsgPropsByMsgID(taskID);
            String taskDeviceName;
            if ((taskDeviceName = (String) tuple.get(0)) == null)
                return errResponse("No such Queue Message: " + taskID, Response.Status.NOT_FOUND);

            if (rescheduleValidQueueMsg())
                validateTaskAssociationInitiator((String) tuple.get(1), deviceCache.findDevice(newDeviceName));

            String devName = newDeviceName != null ? newDeviceName : taskDeviceName;
            if (!devName.equals(device.getDeviceName()))
                return rsClient.forward(request, devName, "");

            queueManager.rescheduleTask(taskID, null, queueEvent, new Date());
            return Response.noContent().build();
        } catch (ConfigurationException e) {
            return errResponse(e.getMessage(), Response.Status.CONFLICT);
        } catch (Exception e) {
            queueEvent.setException(e);
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        } finally {
            taskEventEvent.fire(queueEvent);
        }
    }

    private boolean rescheduleValidQueueMsg() {
        return newDeviceName != null && !queueName.startsWith("Export") && !queueName.equals("RSClient");
    }

    private boolean validateTaskAssociationInitiator(String messageProperties, Device device) throws ConfigurationException {
        javax.json.JsonReader reader = Json.createReader(new StringReader('{' + messageProperties + '}'));
        JsonObject jsonObj = reader.readObject();

        if (queueName.equals("HL7Send")) {
            String sender = jsonObj.getString("SendingApplication") + '|' + jsonObj.getString("SendingFacility");
            HL7Application hl7Application = device.getDeviceExtensionNotNull(HL7DeviceExtension.class)
                    .getHL7Application(sender, true);
            if (hl7Application == null || !hl7Application.isInstalled())
                throw new ConfigurationException("No such HL7 Application " + sender + " on new device: " + newDeviceName);
        } else {
            String localAET = jsonObj.getString("LocalAET");
            ApplicationEntity ae = device.getApplicationEntity(localAET, true);
            if (ae == null || !ae.isInstalled())
                throw new ConfigurationException("No such Application Entity " + localAET + " on new device: " + newDeviceName);
        }
        return true;
    }

    @POST
    @Path("/reschedule")
    @Produces("application/json")
    public Response rescheduleMessages() {
        logRequest();
        if (status == null)
            return errResponse("Missing query parameter: status", Response.Status.BAD_REQUEST);

        try {
            String devName = newDeviceName != null ? newDeviceName : deviceName;
            if (devName != null && !devName.equals(device.getDeviceName()))
                return rsClient.forward(request, devName, "");

            Date scheduledTime = new Date();
            return rescheduleValidQueueMsg()
                    ? rescheduleValidMessages(taskQueryParam(null), scheduledTime)
                    : count(devName == null
                        ? rescheduleOnDistinctDevices(scheduledTime)
                        : rescheduleMessages(taskQueryParam(newDeviceName != null ? null : devName), scheduledTime));
        } catch (IllegalStateException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private int rescheduleOnDistinctDevices(Date scheduledTime) throws Exception {
        List<String> distinctDeviceNames = queueManager.listDistinctDeviceNames(taskQueryParam(null));
        int count = 0;
        for (String devName : distinctDeviceNames)
            count += devName.equals(device.getDeviceName())
                    ? rescheduleMessages(taskQueryParam(devName), scheduledTime)
                    : count(rsClient.forward(request, devName, "&dicomDeviceName=" + devName), devName);

        return count;
    }

    private int rescheduleMessages(TaskQueryParam queueTaskQueryParam, Date scheduledTime) {
        BulkTaskEvent queueEvent = new BulkTaskEvent(request, TaskOperation.RescheduleTasks);
        try {
            int count;
            int rescheduleTaskFetchSize = queueTasksFetchSize();
            int rescheduled = 0;
            do {
                List<Long> queueMsgPKs = queueManager.listQueueMsgIDs(queueTaskQueryParam, rescheduleTaskFetchSize);
                queueMsgPKs.forEach(queueMsgID -> queueManager.rescheduleTask(queueMsgID, queueName, null, scheduledTime));
                count = queueMsgPKs.size();
                rescheduled += count;
            } while (count >= rescheduleTaskFetchSize);
            queueEvent.setCount(rescheduled);
            LOG.info("Rescheduled {} Queue Messages of queue {} on device {}",
                    rescheduled, queueName, device.getDeviceName());
            return rescheduled;
        } catch (Exception e) {
            queueEvent.setException(e);
            throw e;
        } finally {
            bulkTaskEventEvent.fire(queueEvent);
        }
    }

    private Response rescheduleValidMessages(TaskQueryParam queueTaskQueryParam, Date scheduledTime) {
        BulkTaskEvent queueEvent = new BulkTaskEvent(request, TaskOperation.RescheduleTasks);
        int rescheduled = 0;
        int failed = 0;
        try {
            int count = 0;
            int rescheduleTaskFetchSize = queueTasksFetchSize();
            do {
                List<Tuple> queueMsgTuples = queueManager.listQueueMsgIDAndMsgProps(queueTaskQueryParam, rescheduleTaskFetchSize);
                for (Tuple tuple : queueMsgTuples) {
                    Long msgPK = (Long) tuple.get(0);
                    try {
                        if (validateTaskAssociationInitiator((String) tuple.get(1), device)) {
                            queueManager.rescheduleTask(msgPK, queueName, null, scheduledTime);
                            count++;
                        }
                    } catch (ConfigurationException e) {
                        LOG.info("Validation of association initiator failed for Queue Message {} of queue {} : {}",
                                msgPK, queueName, e.getMessage());
                        failed++;
                    }
                }
                rescheduled += count;
            } while (count >= rescheduleTaskFetchSize);
            queueEvent.setCount(rescheduled);
            LOG.info("Rescheduled {} Queue Messages of queue {} on device {}",
                    rescheduled, queueName, device.getDeviceName());
        } catch (Exception e) {
            queueEvent.setException(e);
            throw e;
        } finally {
            queueEvent.setFailed(failed);
            bulkTaskEventEvent.fire(queueEvent);
        }

        if (failed == 0)
            return count(rescheduled);

        LOG.info("Failed to reschedule {} Queue Messages of queue {} on device {}",
                failed, queueName, device.getDeviceName());
        return rescheduled > 0
                ? accepted(rescheduled, failed)
                : conflict(failed);
    }
*/

    @DELETE
    @Path("{taskID}")
    public Response deleteTask(@PathParam("taskID") long taskID) {
        logRequest();
        return taskManager.deleteTask(taskQueryParam(taskID), request);
    }

    @DELETE
    @Produces("application/json")
    public Response deleteTasks() {
        logRequest();
        return taskManager.deleteTasks(taskQueryParam(deviceName), request);
    }

    private Response noSuchTask(long taskID) {
        return errResponse("No such Task : " + taskID + " in Queue: " + queueName, Response.Status.NOT_FOUND);
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

    private QueueMessage.Status status() {
        return status != null ? QueueMessage.Status.fromString(status) : null;
    }

    private int parseInt(String s) {
        return s != null ? Integer.parseInt(s) : 0;
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
        return device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class).getQueueTasksFetchSize();
    }

    private TaskQueryParam1 taskQueryParam(String deviceName) {
        TaskQueryParam1 taskQueryParam = new TaskQueryParam1();
        taskQueryParam.setTaskPK(taskID);
        taskQueryParam.setQueueNames(Collections.singletonList(queueName));
        taskQueryParam.setDeviceName(deviceName);
        taskQueryParam.setStatus(status);
        taskQueryParam.setBatchID(batchID);
        taskQueryParam.setCreatedTime(createdTime);
        taskQueryParam.setUpdatedTime(updatedTime);
        taskQueryParam.setOrderBy(orderby);
        return taskQueryParam;
    }

    private TaskQueryParam1 taskQueryParam(Long taskID) {
        TaskQueryParam1 taskQueryParam = new TaskQueryParam1();
        taskQueryParam.setTaskPK(taskID);
        taskQueryParam.setQueueNames(Collections.singletonList(queueName));
        return taskQueryParam;
    }

}
