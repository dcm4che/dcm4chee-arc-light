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
import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.IDeviceCache;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.WebApplication;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.entity.RetrieveTask;
import org.dcm4chee.arc.event.BulkQueueMessageEvent;
import org.dcm4chee.arc.event.QueueMessageEvent;
import org.dcm4chee.arc.event.QueueMessageOperation;
import org.dcm4chee.arc.qmgt.IllegalTaskStateException;
import org.dcm4chee.arc.query.util.MatchTask;
import org.dcm4chee.arc.retrieve.mgt.RetrieveManager;
import org.dcm4chee.arc.retrieve.mgt.RetrieveTaskQuery;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
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
@Path("monitor/retrieve")
public class RetrieveTaskRS {

    private static final Logger LOG = LoggerFactory.getLogger(RetrieveTaskRS.class);

    @Inject
    private RetrieveManager mgr;

    @Inject
    private Device device;

    @Inject
    private IDeviceCache iDeviceCache;

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
    public Response listRetrieveTasks(@QueryParam("accept") String accept) {
        logRequest();
        Output output = selectMediaType(accept);
        if (output == null)
            return Response.notAcceptable(
                    Variant.mediaTypes(MediaType.APPLICATION_JSON_TYPE, MediaTypes.TEXT_CSV_UTF8_TYPE).build())
                    .build();

        RetrieveTaskQuery tasks = mgr.listRetrieveTasks(
                MatchTask.matchQueueMessage(
                        null, deviceName, status(), batchID, null, null, null, null),
                MatchTask.matchRetrieveTask(
                        localAET, remoteAET, destinationAET, studyIUID, createdTime, updatedTime),
                MatchTask.retrieveTaskOrder(orderby), parseInt(offset), parseInt(limit));
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
                        null, deviceName, status(), batchID, null, null, null, null),
                MatchTask.matchRetrieveTask(
                        localAET, remoteAET, destinationAET, studyIUID, createdTime, updatedTime)));
    }

    @POST
    @Path("{taskPK}/cancel")
    public Response cancelRetrieveTask(@PathParam("taskPK") long pk) {
        logRequest();
        QueueMessageEvent queueEvent = new QueueMessageEvent(request, QueueMessageOperation.CancelTasks);
        try {
            return rsp(mgr.cancelRetrieveTask(pk, queueEvent));
        } catch (IllegalTaskStateException e) {
            queueEvent.setException(e);
            return rsp(Response.Status.CONFLICT, e.getMessage());
        } finally {
            queueMsgEvent.fire(queueEvent);
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

        BulkQueueMessageEvent queueEvent = new BulkQueueMessageEvent(request, QueueMessageOperation.CancelTasks);
        try {
            LOG.info("Cancel processing of Retrieve Tasks with Status {}", status);
            long count = mgr.cancelRetrieveTasks(
                    MatchTask.matchQueueMessage(
                            null, deviceName, status, batchID, null,null, updatedTime, null),
                    MatchTask.matchRetrieveTask(
                            localAET, remoteAET, destinationAET, studyIUID, createdTime, null),
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
    public Response rescheduleTask(@PathParam("taskPK") long pk) throws ConfigurationException {
        logRequest();
        QueueMessageEvent queueEvent = new QueueMessageEvent(request, QueueMessageOperation.RescheduleTasks);
        try {
            String devName = mgr.rescheduleRetrieveTask(pk, queueEvent);
            return devName == null
                    ? Response.status(Response.Status.NOT_FOUND).build()
                    : devName.equals("")
                        ? Response.status(Response.Status.NO_CONTENT).build()
                        : forwardTask(devName);
        } catch (IllegalTaskStateException e) {
            queueEvent.setException(e);
            return rsp(Response.Status.CONFLICT, e.getMessage());
        } finally {
            queueMsgEvent.fire(queueEvent);
        }
    }

    @POST
    @Path("/reschedule")
    public Response rescheduleRetrieveTasks() throws ConfigurationException {
        logRequest();
        QueueMessage.Status status = status();
        if (status == null)
            return rsp(Response.Status.BAD_REQUEST, "Missing query parameter: status");
        if (status == QueueMessage.Status.SCHEDULED || status == QueueMessage.Status.IN_PROCESS)
            return rsp(Response.Status.BAD_REQUEST, "Cannot reschedule tasks with status: " + status);
        if (deviceName == null)
            return rsp(Response.Status.BAD_REQUEST, "Missing query parameter: dicomDeviceName");
        if (!deviceName.equals(device.getDeviceName()))
            return forwardTasks();

        BulkQueueMessageEvent queueEvent = new BulkQueueMessageEvent(request, QueueMessageOperation.RescheduleTasks);
        try {
            Predicate matchQueueMessage = MatchTask.matchQueueMessage(
                    null, deviceName, status, batchID, null, null, null, new Date());
            Predicate matchRetrieveTask = MatchTask.matchRetrieveTask(
                    localAET, remoteAET, destinationAET, studyIUID, createdTime, updatedTime);
            ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
            int fetchSize = arcDev.getQueueTasksFetchSize();
            int count = 0;
            List<Long> retrieveTaskPks;
            do {
                retrieveTaskPks = mgr.getRetrieveTaskPks(matchQueueMessage, matchRetrieveTask, fetchSize);
                for (long pk : retrieveTaskPks)
                    mgr.rescheduleRetrieveTask(pk, null);
                count += retrieveTaskPks.size();
            } while (retrieveTaskPks.size() >= fetchSize);
            queueEvent.setCount(count);
            return count(count);
        } catch (IllegalTaskStateException e) {
            queueEvent.setException(e);
            return rsp(Response.Status.CONFLICT, e.getMessage());
        } finally {
            bulkQueueMsgEvent.fire(queueEvent);
        }
    }

    @DELETE
    @Path("/{taskPK}")
    public Response deleteTask(@PathParam("taskPK") long pk) {
        logRequest();
        QueueMessageEvent queueEvent = new QueueMessageEvent(request, QueueMessageOperation.DeleteTasks);
        boolean deleteRetrieveTask = mgr.deleteRetrieveTask(pk, queueEvent);
        queueMsgEvent.fire(queueEvent);
        return rsp(deleteRetrieveTask);
    }

    @DELETE
    public String deleteTasks() {
        logRequest();
        BulkQueueMessageEvent queueEvent = new BulkQueueMessageEvent(request, QueueMessageOperation.DeleteTasks);
        int deleted = mgr.deleteTasks(
                MatchTask.matchQueueMessage(
                        null, deviceName, status(), batchID, null, null, null, null),
                MatchTask.matchRetrieveTask(
                        localAET, remoteAET, destinationAET, studyIUID, createdTime, updatedTime));
        queueEvent.setCount(deleted);
        bulkQueueMsgEvent.fire(queueEvent);
        return "{\"deleted\":" + deleted + '}';
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

    private Response forwardTask(String devName) throws ConfigurationException {
        Device device = iDeviceCache.get(devName);
        WebApplicationInfo webApplicationInfo = new WebApplicationInfo(device);

        return webApplicationInfo.baseURI == null
                ? webApplicationInfo.errRsp()
                : webApplicationInfo.forwardTask();
    }

    private Response forwardTasks() throws ConfigurationException {
        Device device = iDeviceCache.get(deviceName);
        WebApplicationInfo webApplicationInfo = new WebApplicationInfo(device);

        return webApplicationInfo.baseURI == null
                ? webApplicationInfo.errRsp()
                : webApplicationInfo.forwardTasks();
    }

    class WebApplicationInfo {
        private String webAppName;
        private String baseURI;
        private String devName;

        WebApplicationInfo(Device dev) {
            devName = dev.getDeviceName();
            for (WebApplication webApplication : dev.getWebApplications()) {
                for (WebApplication.ServiceClass serviceClass : webApplication.getServiceClasses()) {
                    if (serviceClass == WebApplication.ServiceClass.DCM4CHEE_ARC) {
                        webAppName = webApplication.getApplicationName();
                        baseURI = toBaseURI(webApplication);
                    }
                }
            }
        }

        private String toBaseURI(WebApplication webApplication) {
            for (Connection connection : webApplication.getConnections())
                if (connection.getProtocol() == Connection.Protocol.HTTP) {
                    return "http://"
                            + connection.getHostname()
                            + ":"
                            + connection.getPort()
                            + webApplication.getServicePath();
                }
            return null;
        }

        Response forwardTask() {
            String requestURI = request.getRequestURI();
            ResteasyClient client = new ResteasyClientBuilder().build();
            WebTarget target = client.target(baseURI + requestURI.substring(requestURI.indexOf("/monitor")));
            Invocation.Builder req = target.request();
            String authorization = request.getHeader("Authorization");
            if (authorization != null)
                req.header("Authorization", authorization);
            return req.post(Entity.json(""));
        }

        Response forwardTasks() {
            String requestURI = request.getRequestURI();
            ResteasyClient client = new ResteasyClientBuilder().build();
            String targetURI = baseURI
                    + requestURI.substring(requestURI.indexOf("/monitor"))
                    + "?"
                    + request.getQueryString();
            WebTarget target = client.target(targetURI);
            Invocation.Builder req = target.request();
            String authorization = request.getHeader("Authorization");
            if (authorization != null)
                req.header("Authorization", authorization);
            return req.post(Entity.json(""));
        }

        Response errRsp() {
            String entity = webAppName == null
                    ? "No Web Application with Service Class 'DCM4CHEE_ARC' configured for device " + devName
                    : "HTTP connection not configured for WebApplication " + webAppName;
            return rsp(Response.Status.INTERNAL_SERVER_ERROR, entity);
        }
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
            Object entity(final RetrieveTaskQuery tasks) {
                return (StreamingOutput) out -> {
                    try (RetrieveTaskQuery t = tasks) {
                        JsonGenerator gen = Json.createGenerator(out);
                        gen.writeStartArray();
                        for (RetrieveTask task : t)
                            task.writeAsJSONTo(gen);
                        gen.writeEnd();
                        gen.flush();
                    }
                };
            }
        },
        CSV(MediaTypes.TEXT_CSV_UTF8_TYPE) {
            @Override
            Object entity(final RetrieveTaskQuery tasks) {
                return (StreamingOutput) out -> {
                    try (RetrieveTaskQuery t = tasks) {
                        Writer writer = new BufferedWriter(new OutputStreamWriter(out, "UTF-8"));
                        RetrieveTask.writeCSVHeader(writer, delimiter);
                        for (RetrieveTask task : t)
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

        abstract Object entity(final RetrieveTaskQuery tasks);
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
