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
 * *** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.qmgt.rs;

import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.qmgt.DifferentDeviceException;
import org.dcm4chee.arc.qmgt.IllegalTaskStateException;
import org.dcm4chee.arc.qmgt.QueueManager;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
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

    @Context
    private HttpServletRequest request;

    @PathParam("queueName")
    private String queueName;

    @QueryParam("dicomDeviceName")
    private String dicomDeviceName;

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


    @GET
    @NoCache
    @Produces("application/json")
    public Response search() throws Exception {
        logRequest();
        return Response.ok(toEntity(mgr.search(queueName, dicomDeviceName, parseStatus(status), createdTime, updatedTime, parseInt(offset), parseInt(limit))))
                .build();
    }

    @GET
    @NoCache
    @Path("/count")
    @Produces("application/json")
    public Response countTasks() throws Exception {
        logRequest();
        return Response.ok("{\"count\":"
                + mgr.countTasks(queueName, dicomDeviceName, parseStatus(status), createdTime, updatedTime) + '}')
                .build();
    }

    @POST
    @Path("{msgId}/cancel")
    public Response cancelProcessing(@PathParam("msgId") String msgId) {
        logRequest();
        try {
            return Response.status(mgr.cancelProcessing(msgId)
                    ? Response.Status.NO_CONTENT
                    : Response.Status.NOT_FOUND)
                    .build();
        } catch (IllegalTaskStateException e) {
            return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("/cancel")
    public Response cancelTasks() {
        logRequest();
        QueueMessage.Status cancelTasksStatus = parseStatus(status);
        if (cancelTasksStatus != null
                && (cancelTasksStatus == QueueMessage.Status.IN_PROCESS
                    || cancelTasksStatus == QueueMessage.Status.SCHEDULED)) {
            int count = mgr.cancelTasks(queueName, dicomDeviceName, cancelTasksStatus, createdTime, updatedTime);
            return Response.status(count > 0 ? Response.Status.OK : Response.Status.NOT_FOUND)
                    .entity("{\"count\":" + count + '}')
                    .build();
        }

        throw new WebApplicationException(
                getResponse("Cannot cancel tasks with Status : " + status, Response.Status.BAD_REQUEST));
    }

    @POST
    @Path("{msgId}/reschedule")
    public Response rescheduleMessage(@PathParam("msgId") String msgId) {
        logRequest();
        try {
            return Response.status(mgr.rescheduleMessage(msgId, null)
                    ? Response.Status.NO_CONTENT
                    : Response.Status.NOT_FOUND)
                    .build();
        } catch (IllegalTaskStateException|DifferentDeviceException e) {
            return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
        }
    }

    @DELETE
    @Path("{msgId}")
    public Response deleteMessage(@PathParam("msgId") String msgId) {
        logRequest();
        return Response.status(mgr.deleteMessage(msgId)
                ? Response.Status.NO_CONTENT
                : Response.Status.NOT_FOUND)
                .build();
    }

    @DELETE
    @Produces("application/json")
    public String deleteMessages() {
        logRequest();
        return "{\"deleted\":"
                + mgr.deleteMessages(queueName, parseStatus(status), dicomDeviceName, createdTime, updatedTime)
                + '}';
    }

    private Object toEntity(final List<QueueMessage> msgs) {
        return new StreamingOutput() {
            @Override
            public void write(OutputStream out) throws IOException {
                Writer w = new OutputStreamWriter(out, "UTF-8");
                int count = 0;
                w.write('[');
                for (QueueMessage msg : msgs) {
                    if (count++ > 0)
                        w.write(',');
                    msg.writeAsJSON(w);
                }
                w.write(']');
                w.flush();
            }
        };
    }

    private static QueueMessage.Status parseStatus(String s) {
        return s != null ? QueueMessage.Status.fromString(s) : null;
    }

    private static int parseInt(String s) {
        return s != null ? Integer.parseInt(s) : 0;
    }

    private void logRequest() {
        LOG.info("Process {} {} from {}@{}", request.getMethod(), request.getRequestURI(),
                request.getRemoteUser(), request.getRemoteHost());
    }

    private Response getResponse(String errorMessage, Response.Status status) {
        Object entity = "{\"errorMessage\":\"" + errorMessage + "\"}";
        return Response.status(status).entity(entity).build();
    }
}
