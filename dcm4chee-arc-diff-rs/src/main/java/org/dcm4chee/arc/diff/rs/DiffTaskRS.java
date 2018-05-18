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
 * **** END LICENSE BLOCK *****
 *
 */

package org.dcm4chee.arc.diff.rs;

import com.querydsl.core.types.Predicate;
import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.IDeviceCache;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.WebApplication;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.diff.DiffService;
import org.dcm4chee.arc.diff.DiffTaskQuery;
import org.dcm4chee.arc.entity.AttributesBlob;
import org.dcm4chee.arc.entity.DiffTask;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.event.BulkQueueMessageEvent;
import org.dcm4chee.arc.event.QueueMessageEvent;
import org.dcm4chee.arc.event.QueueMessageOperation;
import org.dcm4chee.arc.qmgt.IllegalTaskStateException;
import org.dcm4chee.arc.query.util.MatchTask;
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
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

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
    private Device device;

    @Inject
    private IDeviceCache iDeviceCache;

    @Context
    private HttpHeaders httpHeaders;

    @Inject
    private Event<QueueMessageEvent> queueMsgEvent;

    @Inject
    private Event<BulkQueueMessageEvent> bulkQueueMsgEvent;

    @Context
    private HttpServletRequest request;

    @QueryParam("dicomDeviceName")
    private String deviceName;

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

    @GET
    @NoCache
    public Response listDiffTasks(@QueryParam("accept") String accept) {
        logRequest();
        Output output = selectMediaType(accept);
        if (output == null)
            return Response.notAcceptable(
                    Variant.mediaTypes(MediaType.APPLICATION_JSON_TYPE, MediaTypes.TEXT_CSV_UTF8_TYPE).build())
                    .build();

        DiffTaskQuery diffTasks = diffService.listDiffTasks(
                MatchTask.matchQueueMessage(
                        null, deviceName, status(), batchID, null, null, null, null),
                MatchTask.matchDiffTask(localAET, primaryAET, secondaryAET, checkDifferent, checkMissing,
                        comparefields, createdTime, updatedTime),
                MatchTask.diffTaskOrder(orderby),
                parseInt(offset), parseInt(limit));

        return Response.ok(output.entity(diffTasks), output.type).build();
    }

    @GET
    @NoCache
    @Path("/count")
    @Produces("application/json")
    public Response countDiffTasks() {
        logRequest();
        return count(diffService.countDiffTasks(
                MatchTask.matchQueueMessage(
                        null, deviceName, status(), batchID, null, null, null, null),
                MatchTask.matchDiffTask(localAET, primaryAET, secondaryAET, checkDifferent, checkMissing,
                        comparefields, createdTime, updatedTime)));
    }

    @GET
    @NoCache
    @Path("/{taskPK}/studies")
    @Produces("application/dicom+json,application/json")
    public Response getDiffTaskResult(@PathParam("taskPK") long taskPK) {
        logRequest();
        DiffTask diffTask = diffService.getDiffTask(taskPK);
        if (diffTask == null)
            return Response.status(Response.Status.NOT_FOUND).build();

        if (diffTask.getMatches() == 0)
            return Response.noContent().build();

        return Response.ok(entity(diffService.getDiffTaskAttributes(diffTask, parseInt(offset), parseInt(limit))))
                .build();
    }

    @POST
    @Path("{taskPK}/cancel")
    public Response cancelDiffTask(@PathParam("taskPK") long pk) {
        logRequest();
        QueueMessageEvent queueEvent = new QueueMessageEvent(request, QueueMessageOperation.CancelTasks);
        try {
            return rsp(diffService.cancelDiffTask(pk, queueEvent));
        } catch (IllegalTaskStateException e) {
            queueEvent.setException(e);
            return rsp(Response.Status.CONFLICT, e.getMessage());
        } finally {
            queueMsgEvent.fire(queueEvent);
        }
    }

    @POST
    @Path("/cancel")
    public Response cancelDiffTasks() {
        logRequest();
        QueueMessage.Status status = status();
        if (status == null)
            return rsp(Response.Status.BAD_REQUEST, "Missing query parameter: status");
        if (status != QueueMessage.Status.SCHEDULED && status != QueueMessage.Status.IN_PROCESS)
            return rsp(Response.Status.BAD_REQUEST, "Cannot cancel tasks with status: " + status);

        BulkQueueMessageEvent queueEvent = new BulkQueueMessageEvent(request, QueueMessageOperation.CancelTasks);
        try {
            LOG.info("Cancel processing of Diff Tasks with Status {}", status);
            long count = diffService.cancelDiffTasks(
                    MatchTask.matchQueueMessage(
                            null, deviceName, status, batchID, null,null, updatedTime, null),
                    MatchTask.matchDiffTask(
                            localAET, primaryAET, secondaryAET, checkDifferent, checkMissing,
                            comparefields, createdTime, updatedTime),
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
            String devName = diffService.rescheduleDiffTask(pk, queueEvent);
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
    public Response rescheduleDiffTasks() {
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

        BulkQueueMessageEvent queueEvent = new BulkQueueMessageEvent(request, QueueMessageOperation.RescheduleTasks);
        try {
            Predicate matchQueueMessage = MatchTask.matchQueueMessage(
                    null, deviceName, status, batchID, null, null, null, new Date());
            Predicate matchDiffTask = MatchTask.matchDiffTask(
                    localAET, primaryAET, secondaryAET, checkDifferent, checkMissing,
                    comparefields, createdTime, updatedTime);
            ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
            int fetchSize = arcDev.getQueueTasksFetchSize();
            int count = 0;
            List<Long> diffTaskPks;
            do {
                diffTaskPks = diffService.getDiffTaskPks(matchQueueMessage, matchDiffTask, fetchSize);
                for (long pk : diffTaskPks)
                    diffService.rescheduleDiffTask(pk, null);
                count += diffTaskPks.size();
            } while (diffTaskPks.size() >= fetchSize);
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
        boolean deleteDiffTask = diffService.deleteDiffTask(pk, queueEvent);
        queueMsgEvent.fire(queueEvent);
        return rsp(deleteDiffTask);
    }

    @DELETE
    public String deleteTasks() {
        logRequest();
        BulkQueueMessageEvent queueEvent = new BulkQueueMessageEvent(request, QueueMessageOperation.DeleteTasks);
        int deleted = diffService.deleteTasks(
                MatchTask.matchQueueMessage(
                        null, deviceName, status(), batchID, null, null, null, null),
                MatchTask.matchDiffTask(
                        localAET, primaryAET, secondaryAET, checkDifferent, checkMissing,
                        comparefields, createdTime, updatedTime));
        queueEvent.setCount(deleted);
        bulkQueueMsgEvent.fire(queueEvent);
        return "{\"deleted\":" + deleted + '}';
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
            Object entity(final DiffTaskQuery tasks) {
                return (StreamingOutput) out -> {
                    try (DiffTaskQuery  t = tasks) {
                        JsonGenerator gen = Json.createGenerator(out);
                        gen.writeStartArray();
                        for (DiffTask task : t)
                            task.writeAsJSONTo(gen);
                        gen.writeEnd();
                        gen.flush();
                    }
                };
            }
        },
        CSV(MediaTypes.TEXT_CSV_UTF8_TYPE) {
            @Override
            Object entity(final DiffTaskQuery tasks) {
                return (StreamingOutput) out -> {
                    try (DiffTaskQuery  t = tasks) {
                        Writer writer = new BufferedWriter(new OutputStreamWriter(out, "UTF-8"));
                        DiffTask.writeCSVHeader(writer, delimiter);
                        for (DiffTask task : t)
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

        abstract Object entity(final DiffTaskQuery tasks);
    }

    private StreamingOutput entity(List<AttributesBlob> diffTaskAttributesList) {
        return output -> {
            try (JsonGenerator gen = Json.createGenerator(output)) {
                JSONWriter writer = new JSONWriter(gen);
                gen.writeStartArray();
                for (AttributesBlob diffTaskAttributes : diffTaskAttributesList)
                    writer.write(AttributesBlob.decodeAttributes(diffTaskAttributes.getEncodedAttributes(), null));
                gen.writeEnd();
            }
        };
    }

    private void logRequest() {
        LOG.info("Process GET {} from {}@{}", request.getRequestURI(),
                request.getRemoteUser(), request.getRemoteHost());
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

    private Response forwardTask(String devName) throws ConfigurationException {
        ResteasyClient client = new ResteasyClientBuilder().build();
        Device device = iDeviceCache.get(devName);
        for (WebApplication webApplication : device.getWebApplications()) {
            for (WebApplication.ServiceClass serviceClass : webApplication.getServiceClasses()) {
                if (serviceClass == WebApplication.ServiceClass.DCM4CHEE_ARC) {
                    String uri = toURI(webApplication);
                    if (uri == null)
                        return Response.status(Response.Status.BAD_REQUEST)
                                .entity("HTTP connection not configured for WebApplication " + webApplication)
                                .build();

                    WebTarget target = client.target(uri);
                    Invocation.Builder req = target.request();
                    String authorization = request.getHeader("Authorization");
                    if (authorization != null)
                        req.header("Authorization", authorization);
                    return req.post(Entity.json(""));
                }
            }
        }
        return Response.status(Response.Status.BAD_REQUEST)
                .entity("No Web Application with Service Class DCM4CHEE_ARC configured for device " + devName)
                .build();
    }

    private String toURI(WebApplication webApplication) {
        for (Connection connection : webApplication.getConnections())
            if (connection.getProtocol() == Connection.Protocol.HTTP) {
                String requestURI = request.getRequestURI();
                return "http://"
                        + connection.getHostname()
                        + ":"
                        + connection.getPort()
                        + webApplication.getServicePath()
                        + requestURI.substring(requestURI.indexOf("/monitor"));
            }
        return null;
    }

    private static int parseInt(String s) {
        return s != null ? Integer.parseInt(s) : 0;
    }

    private QueueMessage.Status status() {
        return status != null ? QueueMessage.Status.fromString(status) : null;
    }
}
