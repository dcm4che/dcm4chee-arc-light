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
import org.dcm4chee.arc.qmgt.TaskCanceled;
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
import java.util.List;

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

    @Inject
    private Event<TaskCanceled> taskCanceledEvent;

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
        Task task = ejb.findTask(taskQueryParam);
        if (task == null)
            return noSuchTask(taskQueryParam.getTaskPK());

        if (task.getStatus().done)
            return errResponse("Cannot cancel Task with status: " + task.getStatus(), Response.Status.CONFLICT);

        TaskEvent taskEvent = new TaskEvent(request, TaskOperation.CancelTasks);
        taskEvent.setTask(task);
        try {
            if (task.getStatus() == Task.Status.IN_PROCESS)
                taskCanceledEvent.fire(new TaskCanceled(task));
            ejb.merge(task);
            return Response.noContent().build();
        } catch (Exception e) {
            taskEvent.setException(e);
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        } finally {
            taskEventEvent.fire(taskEvent);
        }
    }

    @Override
    public Response cancelTasks(TaskQueryParam1 taskQueryParam, HttpServletRequest request) {
        Task.Status status = taskQueryParam.getStatus();
        if (status == null)
            return errResponse("Missing query parameter: status", Response.Status.BAD_REQUEST);

        if (status.done)
            return errResponse("Cannot cancel tasks with status: " + status, Response.Status.BAD_REQUEST);

        int count = 0;
        int failed = 0;
        BulkTaskEvent taskEvent = new BulkTaskEvent(request, TaskOperation.CancelTasks);
        try {
            LOG.info("Cancel processing of Tasks with Status {}", status);
            if (status == Task.Status.SCHEDULED) {
                return count(count = ejb.cancelTasks(taskQueryParam));
            }
            ArchiveDeviceExtension arcDev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
            int taskFetchSize = arcDev.getTaskFetchSize();
            List<Task> list = ejb.findTasks(taskQueryParam, taskFetchSize);
            do {
                for (Task task : list) {
                    try {
                        taskCanceledEvent.fire(new TaskCanceled(task));
                        ejb.merge(task);
                        count++;
                    } catch (Exception e) {
                        LOG.info("Failed to cancel {}", task, e);
                        failed++;
                    }
                }
            } while (list.size() >= taskFetchSize);
            return response(count, failed);
        } catch (Exception e) {
            taskEvent.setException(e);
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        } finally {
            taskEvent.setCount(count);
            taskEvent.setFailed(failed);
            bulkTaskEventEvent.fire(taskEvent);
        }
    }

    @Override
    public Response rescheduleTask(TaskQueryParam1 taskQueryParam, String scheduledTime,
                                   List<String> newDeviceName, HttpServletRequest request) {
        return null;
    }

    @Override
    public Response rescheduleTasks(TaskQueryParam1 taskQueryParam, String scheduledTime,
                                    List<String> newDeviceName, HttpServletRequest request) {
        Task.Status status = taskQueryParam.getStatus();
        if (status == null)
            return errResponse("Missing query parameter: status", Response.Status.BAD_REQUEST);


        return null;
    }

    @Override
    public Response rescheduleExportTask(TaskQueryParam1 taskQueryParam, String scheduledTime,
                                         List<String> newDeviceName, String newExporterID,
                                         HttpServletRequest request) {
        return null;
    }

    @Override
    public Response rescheduleExportTasks(TaskQueryParam1 taskQueryParam, String scheduledTime,
                                          List<String> newDeviceName, String newExporterID,
                                          HttpServletRequest request) {
        Task.Status status = taskQueryParam.getStatus();
        if (status == null)
            return errResponse("Missing query parameter: status", Response.Status.BAD_REQUEST);

        return null;
    }

    @Override
    public Response rescheduleRetrieveTask(TaskQueryParam1 taskQueryParam, String scheduledTime,
                                           List<String> newDeviceName, String newQueueName,
                                           HttpServletRequest request) {
        return null;
    }

    @Override
    public Response rescheduleRetrieveTasks(TaskQueryParam1 taskQueryParam, String scheduledTime,
                                            List<String> newDeviceName, String newQueueName,
                                            HttpServletRequest request) {
        Task.Status status = taskQueryParam.getStatus();
        if (status == null)
            return errResponse("Missing query parameter: status", Response.Status.BAD_REQUEST);

        return null;
    }

    @Override
    public Response deleteTask(TaskQueryParam1 taskQueryParam, HttpServletRequest request) {
        Task task = ejb.findTask(taskQueryParam);
        if (task == null)
            return noSuchTask(taskQueryParam.getTaskPK());

        TaskEvent taskEvent = new TaskEvent(request, TaskOperation.DeleteTasks);
        taskEvent.setTask(task);
        try {
            if (task.getStatus() == Task.Status.IN_PROCESS)
                taskCanceledEvent.fire(new TaskCanceled(task));
            ejb.remove(task);
            return Response.noContent().build();
        } catch (Exception e) {
            taskEvent.setException(e);
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        } finally {
            taskEventEvent.fire(taskEvent);
        }
    }

    @Override
    public Response deleteTasks(TaskQueryParam1 taskQueryParam, HttpServletRequest request) {
        if (taskQueryParam.getStatus() == null)
            return errResponse("Missing query parameter: status", Response.Status.BAD_REQUEST);

        BulkTaskEvent taskEvent = new BulkTaskEvent(request, TaskOperation.DeleteTasks);
        deleteTasks(taskQueryParam, taskEvent);
        return taskEvent.getException() == null
                ? response(taskEvent.getCount(), taskEvent.getFailed())
                : errResponseAsTextPlain(
                exceptionAsString(taskEvent.getException()), Response.Status.INTERNAL_SERVER_ERROR);
    }

    @Override
    public void deleteTasks(TaskQueryParam1 taskQueryParam, String queueName) {
        deleteTasks(taskQueryParam, new BulkTaskEvent(queueName, TaskOperation.DeleteTasks));
    }

    private void deleteTasks(TaskQueryParam1 taskQueryParam, BulkTaskEvent taskEvent) {
        int count = 0;
        int failed = 0;
        try {
            Task.Status status = taskQueryParam.getStatus();
            LOG.info("Delete Tasks with Status {}", status);
            Task.Type type = taskQueryParam.getType();
            if (status != Task.Status.IN_PROCESS && type != Task.Type.DIFF) {
                if (type == null)
                    taskQueryParam.setNotType(Task.Type.DIFF);
                count = ejb.deleteTasks(taskQueryParam);
                taskQueryParam.setNotType(null);
            }
            if (status == Task.Status.IN_PROCESS || type == null || type == Task.Type.DIFF) {
                ArchiveDeviceExtension arcDev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
                int taskFetchSize = arcDev.getTaskFetchSize();
                List<Task> list = ejb.findTasks(taskQueryParam, taskFetchSize);
                do {
                    for (Task task : list) {
                        try {
                            if (task.getStatus() == Task.Status.IN_PROCESS) {
                                taskCanceledEvent.fire(new TaskCanceled(task));
                            }
                            ejb.remove(task);
                            count++;
                        } catch (Exception e) {
                            LOG.info("Failed to delete {}", task, e);
                            failed++;
                        }
                    }
                } while (list.size() >= taskFetchSize);
            }
        } catch (Exception e) {
            taskEvent.setException(e);
        } finally {
            taskEvent.setCount(count);
            taskEvent.setFailed(failed);
            bulkTaskEventEvent.fire(taskEvent);
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

    private Response response(int count, int failed) {
        return failed == 0 ? count(count) : count == 0 ? conflict(failed) : accepted(count, failed);
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

}
