/*
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
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
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
 */

package org.dcm4chee.arc.qmgt.impl;

import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.QueueDescriptor;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.event.ArchiveServiceEvent;
import org.dcm4chee.arc.event.QueueMessageEvent;
import org.dcm4chee.arc.qmgt.*;
import org.dcm4chee.arc.query.util.TaskQueryParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.persistence.Tuple;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Sep 2015
 */
@ApplicationScoped
public class QueueManagerImpl implements QueueManager {

    private static final Logger LOG = LoggerFactory.getLogger(QueueManagerEJB.class);

    @Inject
    private Device device;

    @Inject
    private QueueManagerEJB ejb;

    @Inject
    private TaskScheduler scheduler;

    @Override
    public QueueMessage scheduleMessage(String deviceName, String queueName, Date scheduledTime,
                                        String messageProperties, Serializable messageBody, String batchID) {
        QueueMessage queueMessage = ejb.scheduleMessage(deviceName, queueName, scheduledTime, messageProperties, messageBody, batchID);
        scheduler.process(queueName, scheduledTime);
        return queueMessage;
    }

    @Override
    public long countScheduledMessagesOnThisDevice(String queueName) {
        return ejb.countScheduledMessagesOnThisDevice(queueName);
    }

    @Override
    public boolean cancelTask(Long msgID, QueueMessageEvent queueEvent) throws IllegalTaskStateException {
        return ejb.cancelTask(msgID, queueEvent);
    }

    @Override
    public long cancelTasks(TaskQueryParam queueTaskQueryParam) {
        return ejb.cancelTasks(queueTaskQueryParam);
    }

    @Override
    public long cancelExportTasks(TaskQueryParam queueTaskQueryParam, TaskQueryParam exportTaskQueryParam) {
        return ejb.cancelExportTasks(queueTaskQueryParam, exportTaskQueryParam);
    }

    @Override
    public long cancelRetrieveTasks(TaskQueryParam queueTaskQueryParam, TaskQueryParam retrieveTaskQueryParam) {
        return ejb.cancelRetrieveTasks(queueTaskQueryParam, retrieveTaskQueryParam);
    }

    @Override
    public long cancelDiffTasks(TaskQueryParam queueTaskQueryParam, TaskQueryParam diffTaskQueryParam) {
        return ejb.cancelDiffTasks(queueTaskQueryParam, diffTaskQueryParam);
    }

    @Override
    public long cancelStgVerTasks(TaskQueryParam queueTaskQueryParam, TaskQueryParam stgVerTaskQueryParam) {
        return ejb.cancelStgVerTasks(queueTaskQueryParam, stgVerTaskQueryParam);
    }

    @Override
    public void rescheduleTask(Long msgID, String queueName, QueueMessageEvent queueEvent, Date scheduledTime) {
        ejb.rescheduleTask(msgID, queueName, queueEvent, scheduledTime);
        scheduler.process(queueName, scheduledTime);
    }

    @Override
    public boolean deleteTask(Long msgId, QueueMessageEvent queueEvent) {
        return ejb.deleteTask(msgId, queueEvent);
    }

    @Override
    public boolean deleteTask(Long msgId, QueueMessageEvent queueEvent, boolean deleteAssociated) {
        return ejb.deleteTask(msgId, queueEvent, deleteAssociated);
    }

    @Override
    public int deleteTasks(TaskQueryParam taskQueryParam, int deleteTasksFetchSize) {
        return ejb.deleteTasks(taskQueryParam, deleteTasksFetchSize);
    }

    @Override
    public long countTasks(TaskQueryParam taskQueryParam) {
        return ejb.countTasks(taskQueryParam);
    }

    @Override
    public List<QueueMessage> listQueueMessages(TaskQueryParam taskQueryParam, int offset, int limit) {
        return ejb.listQueueMessages(taskQueryParam, offset, limit);
    }

    @Override
    public List<String> listDistinctDeviceNames(TaskQueryParam queueTaskQueryParam) {
        return ejb.listDistinctDeviceNames(queueTaskQueryParam);
    }

    @Override
    public List<Long> listQueueMsgIDs(TaskQueryParam queueTaskQueryParam, int limit) {
        return ejb.getQueueMsgIDs(queueTaskQueryParam, limit);
    }

    @Override
    public List<Tuple> listQueueMsgIDAndMsgProps(TaskQueryParam queueTaskQueryParam, int limit) {
        return ejb.listQueueMsgIDAndMsgProps(queueTaskQueryParam, limit);
    }

    @Override
    public Tuple findDeviceNameAndMsgPropsByMsgID(Long msgID) {
        return ejb.findDeviceNameAndMsgPropsByMsgID(msgID);
    }

    public void onArchiveServiceEvent(@Observes ArchiveServiceEvent event) {
        switch (event.getType()) {
            case STARTED:
                retryInProcessTasks();
            case STOPPED:
            case RELOADED:
                break;
        }
    }

    private void retryInProcessTasks() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        for (QueueDescriptor queueDescriptor : arcDev.getQueueDescriptors()) {
            ejb.retryInProcessTasks(queueDescriptor);
        }
    }
}
