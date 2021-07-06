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
import org.dcm4chee.arc.entity.ExportTask;
import org.dcm4chee.arc.entity.Task;
import org.dcm4chee.arc.qmgt.TaskManager;
import org.dcm4chee.arc.query.util.TaskQueryParam1;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Jun 2021
 */
@ApplicationScoped
public class TaskManagerImpl implements TaskManager {

    @Inject
    private Device device;

    @Inject
    private TaskManagerEJB ejb;

    @Inject
    private TaskScheduler scheduler;

    @Override
    public void schedule(Task task) {
        ejb.scheduleTask(task);
        if (task.getScheduledTime().getTime() <= System.currentTimeMillis()) {
            ArchiveDeviceExtension arcDev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
            scheduler.process(arcDev.getQueueDescriptorNotNull(task.getQueueName()), arcDev.getTaskFetchSize());
        }
    }

    @Override
    public void forEachTask(TaskQueryParam1 taskQueryParam, int offset, int limit, Consumer<Task> action) {
        ejb.forEachTask(taskQueryParam, offset, limit, action);
    }

    @Override
    public StreamingOutput writeAsJSON(TaskQueryParam1 taskQueryParam, int offset, int limit) {
        return out -> {
            Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8);
            try (JsonGenerator gen = Json.createGenerator(w)) {
                gen.writeStartArray();
                forEachTask(taskQueryParam, offset, limit, task -> task.writeAsJSON(gen));
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
                forEachTask(taskQueryParam, offset, limit, task -> task.writeAsCSV(printer));
            }
        };
    }

    @Override
    public long countTasks(TaskQueryParam1 taskQueryParam) {
        return ejb.countTasks(taskQueryParam);
    }

}
