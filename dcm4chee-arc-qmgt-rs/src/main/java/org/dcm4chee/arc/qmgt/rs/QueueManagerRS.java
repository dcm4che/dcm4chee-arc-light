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

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.IDeviceCache;
import org.dcm4che3.conf.json.JsonReader;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.HL7DeviceExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.event.BulkQueueMessageEvent;
import org.dcm4chee.arc.event.QueueMessageEvent;
import org.dcm4chee.arc.event.QueueMessageOperation;
import org.dcm4chee.arc.qmgt.IllegalTaskStateException;
import org.dcm4chee.arc.qmgt.QueueManager;
import org.dcm4chee.arc.query.util.TaskQueryParam;
import org.dcm4chee.arc.rs.client.RSClient;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.stream.JsonParser;
import javax.persistence.Tuple;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

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
    private QueueManager mgr;

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

    @PathParam("queueName")
    private String queueName;

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

    @QueryParam("JMSMessageID")
    private String jmsMessageID;

    @QueryParam("orderby")
    @DefaultValue("-updatedTime")
    @Pattern(regexp = "(-?)createdTime|(-?)updatedTime")
    private String orderby;

    @GET
    @NoCache
    @Produces("application/json")
    public Response search() {
        logRequest();
        try {
             return Response.ok(
                     toEntity(
                             mgr.listQueueMessages(taskQueryParam(deviceName), parseInt(offset), parseInt(limit))))
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
        try {
            return count(mgr.countTasks(taskQueryParam(deviceName)));
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @POST
    @Path("{msgId}/cancel")
    public Response cancelProcessing(@PathParam("msgId") String msgId) {
        logRequest();
        QueueMessageEvent queueEvent = new QueueMessageEvent(request, QueueMessageOperation.CancelTasks);
        try {
            return rsp(mgr.cancelTask(msgId, queueEvent), msgId);
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
    public Response cancelTasks() {
        logRequest();
        QueueMessage.Status status = status();
        if (status == null)
            return errResponse("Missing query parameter: status", Response.Status.BAD_REQUEST);
        if (status != QueueMessage.Status.SCHEDULED && status != QueueMessage.Status.IN_PROCESS)
            return errResponse("Cannot cancel tasks with status: " + status, Response.Status.BAD_REQUEST);

        BulkQueueMessageEvent queueEvent = new BulkQueueMessageEvent(request, QueueMessageOperation.CancelTasks);
        try {
            LOG.info("Cancel processing of Tasks with Status {} at Queue {}", this.status, queueName);
            long count = mgr.cancelTasks(taskQueryParam(deviceName));
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
    @Path("{msgId}/reschedule")
    public Response rescheduleMessage(@PathParam("msgId") String msgId) {
        logRequest();
        QueueMessageEvent queueEvent = new QueueMessageEvent(request, QueueMessageOperation.RescheduleTasks);
        try {
            Tuple tuple = mgr.findDeviceNameAndMsgPropsByMsgID(msgId);
            String taskDeviceName;
            if ((taskDeviceName = (String) tuple.get(0)) == null)
                return errResponse("No such Queue Message: " + msgId, Response.Status.NOT_FOUND);

            if (rescheduleValidQueueMsg())
                validateTaskAssociationInitiator((String) tuple.get(1), deviceCache.findDevice(newDeviceName));

            String devName = newDeviceName != null ? newDeviceName : taskDeviceName;
            if (!devName.equals(device.getDeviceName()))
                return rsClient.forward(request, devName, "");

            mgr.rescheduleTask(msgId, null, queueEvent);
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
        QueueMessage.Status status = status();
        if (status == null)
            return errResponse("Missing query parameter: status", Response.Status.BAD_REQUEST);

        try {
            String devName = newDeviceName != null ? newDeviceName : deviceName;
            if (devName != null && !devName.equals(device.getDeviceName()))
                return rsClient.forward(request, devName, "");

            return rescheduleValidQueueMsg()
                    ? rescheduleValidMessages(taskQueryParam(null))
                    : count(devName == null
                        ? rescheduleOnDistinctDevices()
                        : rescheduleMessages(taskQueryParam(newDeviceName != null ? null : devName)));
        } catch (IllegalStateException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private int rescheduleOnDistinctDevices() throws Exception {
        List<String> distinctDeviceNames = mgr.listDistinctDeviceNames(taskQueryParam(null));
        int count = 0;
        for (String devName : distinctDeviceNames)
            count += devName.equals(device.getDeviceName())
                    ? rescheduleMessages(taskQueryParam(devName))
                    : count(rsClient.forward(request, devName, "&dicomDeviceName=" + devName), devName);

        return count;
    }

    private int rescheduleMessages(TaskQueryParam queueTaskQueryParam) {
        BulkQueueMessageEvent queueEvent = new BulkQueueMessageEvent(request, QueueMessageOperation.RescheduleTasks);
        try {
            int count;
            int rescheduleTaskFetchSize = queueTasksFetchSize();
            int rescheduled = 0;
            do {
                List<String> queueMsgIDs = mgr.listQueueMsgIDs(queueTaskQueryParam, rescheduleTaskFetchSize);
                queueMsgIDs.forEach(queueMsgID -> mgr.rescheduleTask(queueMsgID, queueName, null));
                count = queueMsgIDs.size();
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
            bulkQueueMsgEvent.fire(queueEvent);
        }
    }

    private Response rescheduleValidMessages(TaskQueryParam queueTaskQueryParam) {
        BulkQueueMessageEvent queueEvent = new BulkQueueMessageEvent(request, QueueMessageOperation.RescheduleTasks);
        int rescheduled = 0;
        int failed = 0;
        try {
            int count = 0;
            int rescheduleTaskFetchSize = queueTasksFetchSize();
            do {
                List<Tuple> queueMsgTuples = mgr.listQueueMsgIDAndMsgProps(queueTaskQueryParam, rescheduleTaskFetchSize);
                for (Tuple tuple : queueMsgTuples) {
                    String msgId = (String) tuple.get(0);
                    try {
                        if (validateTaskAssociationInitiator((String) tuple.get(1), device)) {
                            mgr.rescheduleTask(msgId, queueName, null);
                            count++;
                        }
                    } catch (ConfigurationException e) {
                        LOG.info("Validation of association initiator failed for Queue Message {} of queue {} : {}",
                                msgId, queueName, e.getMessage());
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
            bulkQueueMsgEvent.fire(queueEvent);
        }

        if (failed == 0)
            return count(rescheduled);

        LOG.info("Failed to reschedule {} Queue Messages of queue {} on device {}",
                failed, queueName, device.getDeviceName());
        return rescheduled > 0
                ? accepted(rescheduled, failed)
                : conflict(failed);
    }

    @DELETE
    @Path("{msgId}")
    public Response deleteMessage(@PathParam("msgId") String msgId) {
        logRequest();
        QueueMessageEvent queueEvent = new QueueMessageEvent(request, QueueMessageOperation.DeleteTasks);
        try {
            return rsp(mgr.deleteTask(msgId, queueEvent), msgId);
        } catch (Exception e) {
            queueEvent.setException(e);
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        } finally {
            queueMsgEvent.fire(queueEvent);
        }
    }

    @DELETE
    @Produces("application/json")
    public Response deleteMessages() {
        logRequest();
        BulkQueueMessageEvent queueEvent = new BulkQueueMessageEvent(request, QueueMessageOperation.DeleteTasks);
        try {
            int deleted = 0;
            int count;
            int deleteTaskFetchSize = queueTasksFetchSize();
            do {
                count = mgr.deleteTasks(taskQueryParam(deviceName), deleteTaskFetchSize);
                deleted += count;
            } while (count >= deleteTaskFetchSize);
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

    private Response rsp(boolean result, String msgID) {
        return result
                ? Response.noContent().build()
                : errResponse("No such Queue Message : " + msgID, Response.Status.NOT_FOUND);
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

    private StreamingOutput toEntity(Iterator<QueueMessage> msgs) {
        return out -> {
                Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8);
                int count = 0;
                w.write('[');
                while (msgs.hasNext()) {
                    if (count++ > 0)
                        w.write(',');
                    msgs.next().writeAsJSON(w);
                }
                w.write(']');
                w.flush();
        };
    }

    private QueueMessage.Status status() {
        return status != null ? QueueMessage.Status.fromString(status) : null;
    }

    private int parseInt(String s) {
        return s != null ? Integer.parseInt(s) : 0;
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
        return device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class).getQueueTasksFetchSize();
    }

    private TaskQueryParam taskQueryParam(String deviceName) {
        TaskQueryParam taskQueryParam = new TaskQueryParam();
        taskQueryParam.setQueueName(Collections.singletonList(queueName));
        taskQueryParam.setDeviceName(deviceName);
        taskQueryParam.setStatus(status());
        taskQueryParam.setBatchID(batchID);
        taskQueryParam.setJmsMessageID(jmsMessageID);
        taskQueryParam.setCreatedTime(createdTime);
        taskQueryParam.setUpdatedTime(updatedTime);
        taskQueryParam.setOrderBy(orderby);
        return taskQueryParam;
    }
}
