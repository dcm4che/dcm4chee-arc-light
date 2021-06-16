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

import org.dcm4chee.arc.Scheduler;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.qmgt.Outcome;
import org.dcm4chee.arc.qmgt.TaskProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.Semaphore;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Jun 2021
 */
@ApplicationScoped
public class TaskScheduler extends Scheduler {
     private static final Logger LOG = LoggerFactory.getLogger(TaskScheduler.class);

    @Inject
    private QueueManagerEJB ejb;

    @Inject
    private Instance<TaskProcessor> taskProcessors;

    private Set<String> inProcess = Collections.synchronizedSet(new HashSet<>());

    protected TaskScheduler() {
        super(Mode.scheduleAtFixedRate);
    }

    @Override
    protected Logger log() {
        return LOG;
    }

    @Override
    protected Duration getPollingInterval() {
        return device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class).getTaskProcessingPollingInterval();
    }

    @Override
    protected void execute() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
        for (QueueDescriptor desc : arcDev.getQueueDescriptors()) {
            process(arcDev, desc);
        }
    }

    public void process(String queueName, Date scheduledTime) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
        if (arcDev.getTaskProcessingPollingInterval().getSeconds() * 1000L >
                scheduledTime.getTime() - System.currentTimeMillis()) {
            process(arcDev, arcDev.getQueueDescriptor(queueName));
        }
    }

    private void process(ArchiveDeviceExtension arcDev, QueueDescriptor desc) {
        String queueName = desc.getQueueName();
        if (desc.isInstalled() && !inProcess.contains(queueName)) {
            List<Long> pks = ejb.findTasksToProcess(queueName, arcDev.getTaskProcessingFetchSize());
            if (!pks.isEmpty()) {
                device.execute(() -> process(desc, pks));
            }
        }
    }

    private void process(QueueDescriptor desc, List<Long> pks) {
        if (inProcess.add(desc.getQueueName()))
            try {
                TaskProcessor processor = taskProcessors.select(
                        new NamedQualifier(desc.getTaskProcessorName().name())).get();
                if (desc.getMaxTasksParallel() > 1)
                    processTasksParallel(desc, processor, pks);
                else
                    processTasksSequential(desc, processor, pks);
            } catch (Throwable e) {
                LOG.warn("Processing Tasks from {} throws:\n", desc, e);
            } finally {
                inProcess.remove(desc.getQueueName());
            }
    }

    private void processTasksSequential(QueueDescriptor desc, TaskProcessor processor, List<Long> pks) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
        String queueName = desc.getQueueName();
        do {
            for (Long pk : pks) {
                if (arcDev.getTaskProcessingPollingInterval() == null
                        || !(desc = arcDev.getQueueDescriptor(queueName)).isInstalled()) {
                    return;
                }
                QueueMessage task = onProcessingStart(pk);
                if (task != null) {
                    processTask(desc, processor, task);
                }
            }
        } while (!(pks = ejb.findTasksToProcess(queueName, arcDev.getTaskProcessingFetchSize())).isEmpty());
    }

    private QueueMessage onProcessingStart(Long pk) {
        try {
            return ejb.onProcessingStart(pk);
        } catch (Exception e) {
            LOG.info("Suppress processing of Task[pk={}] caused by:\n", pk, e);
            return null;
        }
    }

    private void processTasksParallel(QueueDescriptor desc, TaskProcessor processor, List<Long> pks)
            throws InterruptedException {
        ArchiveDeviceExtension arcDev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
        String queueName = desc.getQueueName();
        int maxTasksParallel = desc.getMaxTasksParallel();
        Semaphore semaphore = new Semaphore(maxTasksParallel);
        do {
            try {
                do {
                    for (Long pk : pks) {
                        semaphore.acquire();
                        QueueDescriptor desc1;
                        if (arcDev.getTaskProcessingPollingInterval() == null
                                || !(desc1 = arcDev.getQueueDescriptor(queueName)).isInstalled()) {
                            semaphore.release();
                            return;
                        }
                        QueueMessage task = onProcessingStart(pk);
                        if (task != null) {
                            device.execute(() -> {
                                try {
                                    processTask(desc1, processor, task);
                                } finally {
                                    semaphore.release();
                                }
                            });
                        } else {
                            semaphore.release();
                        }
                    }
                }
                while (!(pks = ejb.findTasksToProcess(queueName, arcDev.getTaskProcessingFetchSize())).isEmpty());
            } finally {
                semaphore.acquire(maxTasksParallel);
                semaphore.release(maxTasksParallel);
            }
        } while (!(pks = ejb.findTasksToProcess(queueName, arcDev.getTaskProcessingFetchSize())).isEmpty());
    }

    private void processTask(QueueDescriptor desc, TaskProcessor processor, QueueMessage task) {
        try {
            Outcome outcome = processor.process(task);
            ejb.onProcessingSuccessful(task.getPk(), outcome);
        } catch (Exception e) {
            ejb.onProcessingFailed(task.getPk(), e);
        }
    }
}
