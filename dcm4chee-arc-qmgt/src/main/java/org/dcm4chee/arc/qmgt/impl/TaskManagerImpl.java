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
import org.dcm4che3.conf.api.ConfigurationNotFoundException;
import org.dcm4che3.conf.api.IDeviceCache;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.HL7DeviceExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.entity.Task;
import org.dcm4chee.arc.event.BulkTaskEvent;
import org.dcm4chee.arc.event.TaskEvent;
import org.dcm4chee.arc.event.TaskOperation;
import org.dcm4chee.arc.qmgt.TaskCanceled;
import org.dcm4chee.arc.qmgt.TaskManager;
import org.dcm4chee.arc.query.util.TaskQueryParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

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
    private IDeviceCache deviceCache;

    @Inject
    private Event<TaskEvent> taskEventEvent;

    @Inject
    private Event<BulkTaskEvent> bulkTaskEventEvent;

    @Inject
    private Event<TaskCanceled> taskCanceledEvent;

    @Override
    public Task findTask(TaskQueryParam taskQueryParam) {
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
    public StreamingOutput writeAsJSON(TaskQueryParam taskQueryParam, int offset, int limit) {
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
    public StreamingOutput writeAsCSV(TaskQueryParam taskQueryParam, int offset, int limit,
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
    public Response countTasks(TaskQueryParam taskQueryParam) {
        try {
            return count(ejb.countTasks(taskQueryParam));
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public Response cancelTask(TaskQueryParam taskQueryParam, HttpServletRequest request) {
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
            task.setStatus(Task.Status.CANCELED);
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
    public Response cancelTasks(TaskQueryParam taskQueryParam, HttpServletRequest request) {
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
                        task.setStatus(Task.Status.CANCELED);
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
    public Response rescheduleTask(TaskQueryParam taskQueryParam, Date scheduledTime,
                                   List<String> newDeviceName, HttpServletRequest request) {
        List<ArchiveDeviceExtension> targetDevices = targetDevices(newDeviceName);
        Task task = ejb.findTask(taskQueryParam);
        if (task == null)
            return noSuchTask(taskQueryParam.getTaskPK());

        if (!newDeviceName.isEmpty()) {
            try {
                adjustDeviceName(task, targetDevices.get(0), null, null);
            } catch (IllegalStateException e) {
                return errResponse(e.getMessage(), Response.Status.CONFLICT);
            }
        }
        TaskEvent taskEvent = new TaskEvent(request, TaskOperation.RescheduleTasks);
        taskEvent.setTask(task);
        try {
            rescheduleTask(task, scheduledTime != null ? scheduledTime : new Date());
            if (scheduledTime == null && newDeviceName.isEmpty())
                processQueue(task.getQueueName());
            return Response.noContent().build();
        } catch (Exception e) {
            taskEvent.setException(e);
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        } finally {
            taskEventEvent.fire(taskEvent);
        }
    }

    @Override
    public Response rescheduleTasks(TaskQueryParam taskQueryParam, Date scheduledTime,
                                    List<String> newDeviceName, HttpServletRequest request) {
        List<ArchiveDeviceExtension> targetDevices = targetDevices(newDeviceName);
        int count = 0;
        int failed = 0;
        Set<String> queueNames = new HashSet<>();
        BulkTaskEvent taskEvent = new BulkTaskEvent(request, TaskOperation.RescheduleTasks);
        Date scheduledTime1 = scheduledTime != null ? scheduledTime : new Date();
        try {
            ArchiveDeviceExtension arcDev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
            int taskFetchSize = arcDev.getTaskFetchSize();
            List<Task> list = ejb.findTasks(taskQueryParam, taskFetchSize);
            do {
                for (Task task : list) {
                    if (!newDeviceName.isEmpty()) {
                        try {
                            adjustDeviceName(task, targetDevices.get(count % targetDevices.size()),
                                    null, null);
                        } catch (IllegalStateException e) {
                            LOG.info(e.getMessage());
                            failed++;
                            continue;
                        }
                    }
                    try {
                        rescheduleTask(task, scheduledTime1);
                        queueNames.add(task.getQueueName());
                        count++;
                    } catch (Exception e) {
                        LOG.info("Failed to reschedule {}", task, e);
                        failed++;
                    }
                }
            } while (list.size() >= taskFetchSize);
            if (scheduledTime == null && newDeviceName.isEmpty())
                queueNames.forEach(this::processQueue);
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
    public Response rescheduleExportTask(TaskQueryParam taskQueryParam, Date scheduledTime,
                                         List<String> newDeviceName, String newExporterID,
                                         HttpServletRequest request) {
        List<ArchiveDeviceExtension> targetDevices = targetDevices(newDeviceName);
        ArchiveDeviceExtension targetDevice = targetDevices.get(0);
        if (newExporterID != null) {
            try {
                targetDevice.getExporterDescriptorNotNull(newExporterID);
            } catch (IllegalArgumentException e) {
                return errResponse(e.getMessage(), Response.Status.BAD_REQUEST);
            }
        }
        Task task = ejb.findTask(taskQueryParam);
        if (task == null)
            return noSuchTask(taskQueryParam.getTaskPK());

        if (!newDeviceName.isEmpty() || newExporterID != null) {
            try {
                adjustDeviceName(task, targetDevices.get(0), newExporterID, null);
            } catch (IllegalStateException e) {
                return errResponse(e.getMessage(), Response.Status.CONFLICT);
            }
        }
        TaskEvent taskEvent = new TaskEvent(request, TaskOperation.RescheduleTasks);
        taskEvent.setTask(task);
        try {
            rescheduleTask(task, scheduledTime != null ? scheduledTime : new Date());
            if (scheduledTime == null && newDeviceName.isEmpty())
                processQueue(task.getQueueName());
            return Response.noContent().build();
        } catch (Exception e) {
            taskEvent.setException(e);
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        } finally {
            taskEventEvent.fire(taskEvent);
        }
    }

    @Override
    public Response rescheduleExportTasks(TaskQueryParam taskQueryParam, Date scheduledTime,
                                          List<String> newDeviceName, String newExporterID,
                                          HttpServletRequest request) {
        List<ArchiveDeviceExtension> targetDevices = targetDevices(newDeviceName);
        if (newExporterID != null) {
            try {
                for (ArchiveDeviceExtension targetDevice : targetDevices) {
                    targetDevice.getExporterDescriptorNotNull(newExporterID);
                }
            } catch (IllegalArgumentException e) {
                return errResponse(e.getMessage(), Response.Status.BAD_REQUEST);
            }
        }
        int count = 0;
        int failed = 0;
        Set<String> queueNames = new HashSet<>();
        BulkTaskEvent taskEvent = new BulkTaskEvent(request, TaskOperation.RescheduleTasks);
        Date scheduledTime1 = scheduledTime != null ? scheduledTime : new Date();
        try {
            ArchiveDeviceExtension arcDev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
            int taskFetchSize = arcDev.getTaskFetchSize();
            List<Task> list = ejb.findTasks(taskQueryParam, taskFetchSize);
            do {
                for (Task task : list) {
                    if (!newDeviceName.isEmpty() || newExporterID != null) {
                        try {
                            adjustDeviceName(task, targetDevices.get(count % targetDevices.size()),
                                    newExporterID, null);
                        } catch (IllegalStateException e) {
                            LOG.info(e.getMessage());
                            failed++;
                            continue;
                        }
                    }
                    try {
                        rescheduleTask(task, scheduledTime1);
                        queueNames.add(task.getQueueName());
                        count++;
                    } catch (Exception e) {
                        LOG.info("Failed to reschedule {}", task, e);
                        failed++;
                    }
                }
            } while (list.size() >= taskFetchSize);
            if (scheduledTime == null && newDeviceName.isEmpty())
                queueNames.forEach(this::processQueue);
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
    public Response rescheduleRetrieveTask(TaskQueryParam taskQueryParam, Date scheduledTime,
                                           List<String> newDeviceName, String newQueueName,
                                           HttpServletRequest request) {
        List<ArchiveDeviceExtension> targetDevices = targetDevices(newDeviceName);
        ArchiveDeviceExtension targetDevice = targetDevices.get(0);
        if (newQueueName != null) {
            try {
                targetDevice.getQueueDescriptorNotNull(newQueueName);
            } catch (IllegalArgumentException e) {
                return errResponse(e.getMessage(), Response.Status.BAD_REQUEST);
            }
        }
        Task task = ejb.findTask(taskQueryParam);
        if (task == null)
            return noSuchTask(taskQueryParam.getTaskPK());

        if (!newDeviceName.isEmpty() || newQueueName != null) {
            try {
                adjustDeviceName(task, targetDevices.get(0), null, newQueueName);
            } catch (IllegalStateException e) {
                return errResponse(e.getMessage(), Response.Status.CONFLICT);
            }
        }
        TaskEvent taskEvent = new TaskEvent(request, TaskOperation.RescheduleTasks);
        taskEvent.setTask(task);
        try {
            rescheduleTask(task, scheduledTime != null ? scheduledTime : new Date());
            if (scheduledTime == null && newDeviceName.isEmpty())
                processQueue(task.getQueueName());
            return Response.noContent().build();
        } catch (Exception e) {
            taskEvent.setException(e);
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        } finally {
            taskEventEvent.fire(taskEvent);
        }
    }

    @Override
    public Response rescheduleRetrieveTasks(TaskQueryParam taskQueryParam, Date scheduledTime,
                                            List<String> newDeviceName, String newQueueName,
                                            HttpServletRequest request) {
        List<ArchiveDeviceExtension> targetDevices = targetDevices(newDeviceName);
        if (newQueueName != null) {
            try {
                for (ArchiveDeviceExtension targetDevice : targetDevices) {
                    targetDevice.getQueueDescriptorNotNull(newQueueName);
                }
            } catch (IllegalArgumentException e) {
                return errResponse(e.getMessage(), Response.Status.BAD_REQUEST);
            }
        }
        int count = 0;
        int failed = 0;
        Set<String> queueNames = new HashSet<>();
        BulkTaskEvent taskEvent = new BulkTaskEvent(request, TaskOperation.RescheduleTasks);
        Date scheduledTime1 = scheduledTime != null ? scheduledTime : new Date();
        try {
            ArchiveDeviceExtension arcDev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
            int taskFetchSize = arcDev.getTaskFetchSize();
            List<Task> list = ejb.findTasks(taskQueryParam, taskFetchSize);
            do {
                for (Task task : list) {
                    if (!newDeviceName.isEmpty() || newQueueName != null) {
                        try {
                            adjustDeviceName(task, targetDevices.get(count % targetDevices.size()),
                                    null, newQueueName);
                        } catch (IllegalStateException e) {
                            LOG.info(e.getMessage());
                            failed++;
                            continue;
                        }
                    }
                    try {
                        rescheduleTask(task, scheduledTime1);
                        queueNames.add(task.getQueueName());
                        count++;
                    } catch (Exception e) {
                        LOG.info("Failed to reschedule {}", task, e);
                        failed++;
                    }
                }
            } while (list.size() >= taskFetchSize);
            if (scheduledTime == null && newDeviceName.isEmpty())
                queueNames.forEach(this::processQueue);
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
    public Response deleteTask(TaskQueryParam taskQueryParam, HttpServletRequest request) {
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
    public Response deleteTasks(TaskQueryParam taskQueryParam, HttpServletRequest request) {
        BulkTaskEvent taskEvent = new BulkTaskEvent(request, TaskOperation.DeleteTasks);
        deleteTasks(taskQueryParam, taskEvent);
        return taskEvent.getException() == null
                ? response(taskEvent.getCount(), taskEvent.getFailed())
                : errResponseAsTextPlain(
                exceptionAsString(taskEvent.getException()), Response.Status.INTERNAL_SERVER_ERROR);
    }

    @Override
    public void deleteTasks(TaskQueryParam taskQueryParam, String queueName) {
        deleteTasks(taskQueryParam, new BulkTaskEvent(queueName, TaskOperation.DeleteTasks));
    }

    private void deleteTasks(TaskQueryParam taskQueryParam, BulkTaskEvent taskEvent) {
        int count = 0;
        int failed = 0;
        Task.Status status = taskQueryParam.getStatus();
        try {
            Task.Type type = taskQueryParam.getType();
            if (status != Task.Status.IN_PROCESS && type != Task.Type.DIFF) {
                if (status == null)
                    taskQueryParam.setNotStatus(Task.Status.IN_PROCESS);
                if (type == null)
                    taskQueryParam.setNotType(Task.Type.DIFF);
                count = ejb.deleteTasks(taskQueryParam);
                taskQueryParam.setNotStatus(null);
                taskQueryParam.setNotType(null);
            }
            if (status == null || status == Task.Status.IN_PROCESS || type == null || type == Task.Type.DIFF) {
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
            if (count > 0)
                LOG.info("Deleted {} tasks of {}", count, taskQueryParam);
        }
    }

    private List<ArchiveDeviceExtension> targetDevices(List<String> deviceNames) {
        if (deviceNames.isEmpty())
            return Collections.singletonList(device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class));
        List<ArchiveDeviceExtension> list = new ArrayList<>(deviceNames.size());
        for (String deviceName : deviceNames) {
            try {
                list.add(deviceCache.findDevice(deviceName).getDeviceExtensionNotNull(ArchiveDeviceExtension.class));
            } catch (ConfigurationNotFoundException|IllegalStateException e) {
                throw new WebApplicationException(errResponse(e.getMessage(), Response.Status.BAD_REQUEST));
            } catch (Exception e) {
                throw new WebApplicationException(
                        errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
            }
        }
        return list;
    }

    private void adjustDeviceName(Task task, ArchiveDeviceExtension targetDevice, String newExporterID,
                                  String newQueueName) {
        String deviceName = targetDevice.getDevice().getDeviceName();
        String exporterID = newExporterID != null ? newExporterID : task.getExporterID();
        if (exporterID != null && task.getType() == Task.Type.EXPORT) {
            ExporterDescriptor exporterDescriptor = targetDevice.getExporterDescriptor(exporterID);
            if (exporterDescriptor == null)
                throw new IllegalStateException("Cannot reschedule Export Task{id=" + task.getPk()
                        + "} to Exporter{id=" + exporterID
                        + "} not configured at Device{name=" + deviceName + '}');

            task.setExporterID(exporterID);
            task.setQueueName(exporterDescriptor.getQueueName());
            task.setLocalAET(exporterDescriptor.getAETitle());
        } else {
            String queueName = newQueueName != null ? newQueueName : task.getQueueName();
            if (targetDevice.getQueueDescriptor(queueName) == null)
                throw new IllegalStateException("Cannot reschedule Taskid=" + task.getPk()
                        + "} on Queue{name=" + queueName
                        + "} not configured at Device{name=" + deviceName + '}');
            task.setQueueName(queueName);
        }
        validateTaskAssociationInitiator(task, targetDevice);
        task.setDeviceName(deviceName);
    }

    private void validateTaskAssociationInitiator(Task task, ArchiveDeviceExtension targetDevice) {
        switch (task.getType()) {
            case EXPORT:
            case REST:
                break;
            case HL7:
                HL7Application hl7Application = targetDevice.getDevice()
                        .getDeviceExtensionNotNull(HL7DeviceExtension.class)
                        .getHL7Application(task.getSendingApplicationWithFacility(),
                                true);
                if (hl7Application == null || !hl7Application.isInstalled())
                    throw new IllegalStateException("No such HL7 Application{name=" + task.getSendingApplicationWithFacility()
                            + "} on new device{name=" + targetDevice.getDevice().getDeviceName() + "}");
                break;
            default:
                ApplicationEntity ae = targetDevice.getDevice().getApplicationEntity(task.getLocalAET(), true);
                if (ae == null || !ae.isInstalled())
                    throw new IllegalStateException("No such Application Entity{dicomAETitle=" + task.getLocalAET()
                            + "} on new device{name=" + device.getDeviceName() + "}");
        }
    }

    private void rescheduleTask(Task task, Date scheduledTime) {
        if (task.getStatus() == Task.Status.IN_PROCESS)
            taskCanceledEvent.fire(new TaskCanceled(task));
        task.setStatus(Task.Status.SCHEDULED);
        task.setScheduledTime(scheduledTime);
        task.setProcessingStartTime(null);
        task.setProcessingEndTime(null);
        task.setNumberOfFailures(0);
        task.setOutcomeMessage(null);
        task.setErrorMessage(null);
        ejb.merge(task);
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
