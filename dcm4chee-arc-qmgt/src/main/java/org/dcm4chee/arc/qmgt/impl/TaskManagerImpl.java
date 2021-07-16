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

package org.dcm4chee.arc.qmgt.impl;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.entity.Task;
import org.dcm4chee.arc.event.BulkTaskEvent;
import org.dcm4chee.arc.event.TaskEvent;
import org.dcm4chee.arc.event.TaskOperation;
import org.dcm4chee.arc.qmgt.IllegalTaskStateException;
import org.dcm4chee.arc.qmgt.TaskManager;
import org.dcm4chee.arc.query.util.TaskQueryParam1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Jun 2021
 */
@ApplicationScoped
public class TaskManagerImpl implements TaskManager {
    private static final Logger LOG = LoggerFactory.getLogger(TaskManagerImpl.class);

    @Inject
    private Device device;

    @Inject
    private TaskManagerEJB ejb;

    @Inject
    private TaskScheduler scheduler;

    @Inject
    private Event<TaskEvent> taskEventEvent;

    @Inject
    private Event<BulkTaskEvent> bulkTaskEventEvent;

    @Override
    public Task findTask(TaskQueryParam1 taskQueryParam) {
        return ejb.findTask(taskQueryParam);
    }

    @Override
    public void scheduleTask(Task task) {
        ejb.scheduleTask(task);
        if (task.getScheduledTime().getTime() <= System.currentTimeMillis()) {
            processQueue(task.getQueueName());
        }
    }

    @Override
    public void processQueue(String queueName) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
        scheduler.process(arcDev.getQueueDescriptorNotNull(queueName), arcDev.getTaskFetchSize());
    }

    @Override
    public StreamingOutput writeAsJSON(TaskQueryParam1 taskQueryParam, int offset, int limit) {
        return out -> {
            Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8);
            try (JsonGenerator gen = Json.createGenerator(w)) {
                gen.writeStartArray();
                ejb.forEachTask(taskQueryParam, offset, limit, task -> task.writeAsJSON(gen));
                gen.writeEnd();
            }
        };
    }

    @Override
    public StreamingOutput writeAsCSV(TaskQueryParam1 taskQueryParam, int offset, int limit,
                                      String[] headers, char delimiter) {
        return out -> {
            Writer writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
            try (CSVPrinter printer = new CSVPrinter(writer, CSVFormat.RFC4180
                    .withHeader(headers)
                    .withDelimiter(delimiter)
                    .withQuoteMode(QuoteMode.ALL))) {
                ejb.forEachTask(taskQueryParam, offset, limit, task -> task.writeAsCSV(printer));
            }
        };
    }

    @Override
    public Response countTasks(TaskQueryParam1 taskQueryParam) {
        try {
            return count(ejb.countTasks(taskQueryParam));
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public Response cancelTask(TaskQueryParam1 taskQueryParam, HttpServletRequest request) {
        try {
            Task task = ejb.cancelTask(taskQueryParam);
            if (task == null)
                return noSuchTask(taskQueryParam.getTaskPK());
            return Response.noContent().build();
        } catch (IllegalTaskStateException e) {
            return errResponse(e.getMessage(), Response.Status.CONFLICT);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public Response cancelTasks(TaskQueryParam1 taskQueryParam, HttpServletRequest request) {
        Task.Status status = taskQueryParam.getStatus();
        if (status == null)
            return errResponse("Missing query parameter: status", Response.Status.BAD_REQUEST);

        if (status != Task.Status.SCHEDULED && status != Task.Status.IN_PROCESS)
            return errResponse("Cannot cancel tasks with status: " + status, Response.Status.BAD_REQUEST);

        BulkTaskEvent queueEvent = new BulkTaskEvent(request, TaskOperation.CancelTasks);
        try {
            LOG.info("Cancel processing of Tasks with Status {}", status);
            long count = 0;
            if (status == Task.Status.SCHEDULED) {
                count = ejb.cancelTasks(taskQueryParam);
            } else {
                ArchiveDeviceExtension arcDev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
                int taskFetchSize = arcDev.getTaskFetchSize();
                int canceled;
                do {
                    count += canceled = ejb.cancelTasks(taskQueryParam, taskFetchSize);
                } while (canceled >= taskFetchSize);
            }
            queueEvent.setCount(count);
            return count(count);
        } catch (Exception e) {
            queueEvent.setException(e);
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        } finally {
            bulkTaskEventEvent.fire(queueEvent);
        }
    }

    @Override
    public Response deleteTask(TaskQueryParam1 taskQueryParam, HttpServletRequest request) {
        try {
            Task task = ejb.deleteTask(taskQueryParam);
            if (task == null)
                return noSuchTask(taskQueryParam.getTaskPK());
            this.taskEventEvent.fire(new TaskEvent(request, TaskOperation.DeleteTasks, task));
            return Response.noContent().build();
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public Response deleteTasks(TaskQueryParam1 taskQueryParam, HttpServletRequest request) {
        BulkTaskEvent taskEvent = new BulkTaskEvent(request, TaskOperation.DeleteTasks);
        try {
            long count = 0;
            ArchiveDeviceExtension arcDev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
            int taskFetchSize = arcDev.getTaskFetchSize();
            int deleted;
            do {
                count += deleted = ejb.deleteTasks(taskQueryParam, taskFetchSize);
            } while (deleted >= taskFetchSize);
            if (count > 0) {
                taskEvent.setCount(count);
                bulkTaskEventEvent.fire(taskEvent);
            }
            return Response.ok("{\"deleted\":" + count + '}').build();
        } catch (Exception e) {
            taskEvent.setException(e);
            bulkTaskEventEvent.fire(taskEvent);
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private Response noSuchTask(long taskID) {
        return errResponse("No such Task : " + taskID, Response.Status.NOT_FOUND);
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

    private Response count(long count) {
        return Response.ok("{\"count\":" + count + '}').build();
    }

}
