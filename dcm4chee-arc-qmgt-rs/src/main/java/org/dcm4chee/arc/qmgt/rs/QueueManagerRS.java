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

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.qmgt.TaskManager;
import org.dcm4chee.arc.query.util.TaskQueryParam;
import org.dcm4chee.arc.validation.ParseDateTime;
import org.dcm4chee.arc.validation.constraints.ValidValueOf;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Date;
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
    private TaskManager taskManager;

    @Inject
    private Device device;

    @Context
    private HttpServletRequest request;

    @PathParam("queueName")
    private String queueName;

    @QueryParam("taskID")
    private Long taskID;

    @QueryParam("dicomDeviceName")
    private String deviceName;

    @QueryParam("newDeviceName")
    private List<String> newDeviceName;

    @QueryParam("status")
    @Pattern(regexp = "SCHEDULED|SCHEDULED FOR RETRY|IN PROCESS|COMPLETED|WARNING|FAILED|CANCELED")
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

    @QueryParam("scheduledTime")
    @ValidValueOf(type = ParseDateTime.class)
    private String scheduledTime;

    @QueryParam("batchID")
    private String batchID;

    @QueryParam("localAET")
    private String localAET;

    @QueryParam("remoteAET")
    private String remoteAET;

    @QueryParam("StudyInstanceUID")
    private String studyInstanceUID;

    @QueryParam("orderby")
    @DefaultValue("-updatedTime")
    @Pattern(regexp = "(-?)createdTime|(-?)updatedTime|(-?)scheduledTime")
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
    public Response cancelTask(@PathParam("taskID") long taskID) {
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

    @POST
    @Path("{taskID}/reschedule")
    public Response rescheduleTask(@PathParam("taskID") long taskID) {
        logRequest();
        return taskManager.rescheduleTask(taskQueryParam(taskID), scheduledTime(), newDeviceName, request);
    }

    @POST
    @Path("/reschedule")
    @Produces("application/json")
    public Response rescheduleTasks() {
        logRequest();
        return taskManager.rescheduleTasks(taskQueryParam(deviceName), scheduledTime(), newDeviceName, request);
    }

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

    private int parseInt(String s) {
        return s != null ? Integer.parseInt(s) : 0;
    }

    private Date scheduledTime() {
        return scheduledTime != null ? ParseDateTime.valueOf(scheduledTime) : null;
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

    private TaskQueryParam taskQueryParam(String deviceName) {
        TaskQueryParam taskQueryParam = new TaskQueryParam();
        taskQueryParam.setTaskPK(taskID);
        taskQueryParam.setQueueNames(Collections.singletonList(queueName));
        taskQueryParam.setDeviceName(deviceName);
        taskQueryParam.setStatus(status);
        taskQueryParam.setBatchID(batchID);
        taskQueryParam.setLocalAET(localAET);
        taskQueryParam.setRemoteAET(remoteAET);
        taskQueryParam.setCreatedTime(createdTime);
        taskQueryParam.setUpdatedTime(updatedTime);
        taskQueryParam.setOrderBy(orderby);
        taskQueryParam.setStudyIUID(studyInstanceUID);
        return taskQueryParam;
    }

    private TaskQueryParam taskQueryParam(Long taskID) {
        TaskQueryParam taskQueryParam = new TaskQueryParam();
        taskQueryParam.setTaskPK(taskID);
        taskQueryParam.setQueueNames(Collections.singletonList(queueName));
        return taskQueryParam;
    }

}
