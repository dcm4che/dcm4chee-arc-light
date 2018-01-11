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
 * **** END LICENSE BLOCK *****
 */
package org.dcm4chee.arc.export.rs;

import com.querydsl.core.BooleanBuilder;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.entity.ExportTask;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.export.mgt.ExportManager;
import org.dcm4chee.arc.qmgt.DifferentDeviceException;
import org.dcm4chee.arc.qmgt.IllegalTaskStateException;
import org.dcm4chee.arc.query.util.PredicateUtils;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Mar 2017
 */
@RequestScoped
@Path("monitor/export")
public class ExportTaskRS {
    private static final Logger LOG = LoggerFactory.getLogger(ExportTaskRS.class);
    private static final String CSV_HEADER =
            "pk," +
            "createdTime," +
            "updatedTime," +
            "ExporterID," +
            "StudyInstanceUID," +
            "SeriesInstanceUID," +
            "SOPInstanceUID," +
            "NumberOfInstances," +
            "Modality," +
            "dicomDeviceName," +
            "status," +
            "scheduledTime," +
            "failures," +
            "processingStartTime," +
            "processingEndTime," +
            "errorMessage," +
            "outcomeMessage\r\n";

    @Inject
    private ExportManager mgr;

    @Inject
    private Device device;

    @Context
    private HttpHeaders httpHeaders;

    @QueryParam("StudyInstanceUID")
    private String studyUID;

    @QueryParam("dicomDeviceName")
    private String deviceName;

    @QueryParam("ExporterID")
    private String exporterID;

    @QueryParam("status")
    @Pattern(regexp = "TO SCHEDULE|SCHEDULED|IN PROCESS|COMPLETED|WARNING|FAILED|CANCELED")
    private String status;

    @QueryParam("createdTime")
    private String createdTime;

    @QueryParam("updatedTime")
    private String updatedTime;

    @QueryParam("offset")
    @Pattern(regexp = "0|([1-9]\\d{0,4})")
    private String offset;

    @QueryParam("limit")
    @Pattern(regexp = "[1-9]\\d{0,4}")
    private String limit;

    @Context
    private HttpServletRequest request;

    @GET
    @NoCache
    @Produces({"application/json", "text/csv; charset=UTF-8"})
    public Response listTasks(@QueryParam("accept") String accept) throws Exception {
        logRequest();
        if (accept != null)
            return accept.equals("application/json") ? search(accept) : listAsCSV(accept);

        return search(null);
    }

    @GET
    @NoCache
    @Produces("application/json")
    public Response search(@QueryParam("accept") String accept) throws Exception {
        logRequest();
        return accept != null && !isCompatible(accept)
                ? Response.status(Response.Status.NOT_ACCEPTABLE).build()
                : Response.ok(toEntity(mgr.search(deviceName, exporterID, studyUID, createdTime, updatedTime, parseStatus(status),
                    parseInt(offset), parseInt(limit))))
                    .build();
    }

    @GET
    @NoCache
    @Path("/count")
    @Produces("application/json")
    public Response countExportTasks() throws Exception {
        logRequest();
        return Response.ok("{\"count\":" +
                mgr.countExportTasks(deviceName, exporterID, studyUID, createdTime, updatedTime, parseStatus(status)) + '}')
                .build();
    }

    @GET
    @NoCache
    @Produces("text/csv; charset=UTF-8")
    public Response listAsCSV(@QueryParam("accept") String accept) throws Exception {
        logRequest();
        return accept != null && !isCompatible(accept)
                ? Response.status(Response.Status.NOT_ACCEPTABLE).build()
                : Response.ok(toEntityAsCSV(mgr.search(deviceName, exporterID, studyUID, createdTime, updatedTime, parseStatus(status),
                    parseInt(offset), parseInt(limit))))
                    .build();
    }

    @POST
    @Path("{taskPK}/cancel")
    public Response cancelProcessing(@PathParam("taskPK") long pk) {
        logRequest();
        try {
            return Response.status(mgr.cancelProcessing(pk)
                    ? Response.Status.NO_CONTENT
                    : Response.Status.NOT_FOUND)
                    .build();
        } catch (IllegalTaskStateException e) {
            return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("/cancel")
    public Response cancelExportTasks() {
        logRequest();
        QueueMessage.Status cancelStatus = parseStatus(status);
        if (cancelStatus == null || !(cancelStatus == QueueMessage.Status.SCHEDULED || cancelStatus == QueueMessage.Status.IN_PROCESS))
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Cannot cancel tasks with Status : " + status)
                    .build();

        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        ExporterDescriptor exporter = arcDev.getExporterDescriptor(exporterID);
        String[] exportQueues = {"Export1", "Export2", "Export3"};
        BooleanBuilder exportPredicate = PredicateUtils.exportPredicate(exporterID, deviceName, studyUID, createdTime, null);

        try {
            BooleanBuilder queueMsgPredicate;
            int count = 0;
            if (exporter != null) {
                String queueName = exporter.getQueueName();
                queueMsgPredicate = PredicateUtils.queueMsgPredicate(queueName, deviceName, cancelStatus, null, updatedTime);
                count = mgr.cancelExportTasks(cancelStatus, queueMsgPredicate, exportPredicate);
                LOG.info("Cancel processing of Tasks with Status {} at Queue {}", status, queueName);
            }
            else {
                for (String queueName : exportQueues) {
                    queueMsgPredicate = PredicateUtils.queueMsgPredicate(queueName, deviceName, cancelStatus, null, updatedTime);
                    count += mgr.cancelExportTasks(cancelStatus, queueMsgPredicate, exportPredicate);
                    LOG.info("Cancel processing of Tasks with Status {} at Queue {}", status, queueName);
                }
            }
            return Response.status(Response.Status.OK).entity("{\"count\":" + count + '}').build();
        } catch (IllegalTaskStateException e) {
            return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("{taskPK}/reschedule/{ExporterID}")
    public Response rescheduleTask(@PathParam("taskPK") long pk, @PathParam("ExporterID") String exporterID) {
        logRequest();
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        ExporterDescriptor exporter = arcDev.getExporterDescriptor(exporterID);
        if (exporter == null)
            return Response.status(Response.Status.NOT_FOUND).entity("No such exporter - " + exporterID).build();

        try {
            return Response.status(mgr.rescheduleExportTask(pk, exporter)
                    ? Response.Status.NO_CONTENT
                    : Response.Status.NOT_FOUND)
                    .build();
        } catch (IllegalTaskStateException|DifferentDeviceException e) {
            return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("/reschedule")
    public Response rescheduleExportTasks() {
        logRequest();
        QueueMessage.Status rescheduleTaskStatus = parseStatus(status);
        if (rescheduleTaskStatus == null || deviceName == null
                || rescheduleTaskStatus == QueueMessage.Status.IN_PROCESS || rescheduleTaskStatus == QueueMessage.Status.SCHEDULED)
            return Response.status(Response.Status.CONFLICT)
                    .entity("Cannot cancel tasks with Status : " + status + " and device name : " + deviceName)
                    .build();

        if (!device.getDeviceName().equals(deviceName))
            return Response.status(Response.Status.CONFLICT)
                    .entity("Cannot reschedule Tasks of Device " + device.getDeviceName() + " on Device " + deviceName)
                    .build();

        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        int rescheduleTasksFetchSize = arcDev.getQueueTasksFetchSize();
        ExporterDescriptor exporter = arcDev.getExporterDescriptor(exporterID);
        String updtTime = updatedTime != null ? updatedTime : new SimpleDateFormat("-yyyyMMddHHmmss.SSS").format(new Date());

        try {
            List<ExportTask> exportTasks;
            int count = 0;
            do {
                exportTasks = mgr.search(
                        deviceName, exporterID, studyUID, createdTime, updtTime, rescheduleTaskStatus, 0, rescheduleTasksFetchSize);
                for (ExportTask task : exportTasks)
                    mgr.rescheduleExportTask(
                            task.getPk(),
                            exporter != null ? exporter : arcDev.getExporterDescriptor(task.getExporterID()));
                count += exportTasks.size();
            } while (exportTasks.size() >= rescheduleTasksFetchSize);

            return Response.status(Response.Status.OK).entity("{\"count\":" + count + '}').build();
        } catch (IllegalTaskStateException|DifferentDeviceException e) {
            return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
        }
    }


    @DELETE
    @Path("/{taskPK}")
    public Response deleteTask(@PathParam("taskPK") long pk) {
        logRequest();
        return Response.status(mgr.deleteExportTask(pk)
                ? Response.Status.NO_CONTENT
                : Response.Status.NOT_FOUND)
                .build();
    }

    @DELETE
    public String deleteTasks() {
        logRequest();
        BooleanBuilder exportPredicate = PredicateUtils.exportPredicate(exporterID, deviceName, studyUID, createdTime, updatedTime);
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        ExporterDescriptor exporter = arcDev.getExporterDescriptor(exporterID);
        String[] exportQueues = {"Export1", "Export2", "Export3"};
        QueueMessage.Status deleteStatus = parseStatus(status);
        BooleanBuilder queueMsgPredicate;
        int count = 0;
        if (exporter != null) {
            String queueName = exporter.getQueueName();
            queueMsgPredicate = PredicateUtils.queueMsgPredicate(queueName, deviceName, deleteStatus, createdTime, updatedTime);
            count = mgr.deleteTasks(exportPredicate, queueMsgPredicate);
        } else {
            for (String queueName : exportQueues) {
                queueMsgPredicate = PredicateUtils.queueMsgPredicate(queueName, deviceName, deleteStatus, createdTime, updatedTime);
                count += mgr.deleteTasks(exportPredicate, queueMsgPredicate);
            }
        }
        return "{\"deleted\":" + count + '}';
    }

    private boolean isCompatible(String accept) {
        try {
            List<MediaType> acceptableMediaTypes = httpHeaders.getAcceptableMediaTypes();
            for (MediaType mediaType : acceptableMediaTypes)
                if (mediaType.isCompatible(MediaType.valueOf(accept)))
                    return true;
        } catch (IllegalArgumentException e) {
            LOG.warn(e.getMessage());
            return false;
        }
        return false;
    }

    private Object toEntity(final List<ExportTask> tasks) {
        return new StreamingOutput() {
            @Override
            public void write(OutputStream out) throws IOException {
                JsonGenerator gen = Json.createGenerator(out);
                gen.writeStartArray();
                for (ExportTask task : tasks)
                    task.writeAsJSONTo(gen);
                gen.writeEnd();
                gen.flush();
            }
        };
    }

    private Object toEntityAsCSV(final List<ExportTask> tasks) {
        return new StreamingOutput() {
            @Override
            public void write(OutputStream out) throws IOException {
                Writer writer = new BufferedWriter(new OutputStreamWriter(out, "UTF-8"));
                writer.write(CSV_HEADER);
                for (ExportTask task : tasks) {
                    task.writeAsCSVTo(writer);
                }
                writer.flush();
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
}
