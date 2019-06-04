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

import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.event.QueueMessageEvent;
import org.dcm4chee.arc.qmgt.*;
import org.dcm4chee.arc.query.util.TaskQueryParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.jms.ObjectMessage;
import javax.persistence.Tuple;
import java.io.Serializable;
import java.util.Iterator;
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
    private QueueManagerEJB ejb;

    @Override
    public ObjectMessage createObjectMessage(Serializable object) {
        return ejb.createObjectMessage(object);
    }

    @Override
    public QueueMessage scheduleMessage(String queueName, ObjectMessage message, int priority, String batchID,
                                        long delay)
            throws QueueSizeLimitExceededException {
        return ejb.scheduleMessage(queueName, message, priority, batchID, delay);
    }

    @Override
    public long countScheduledMessagesOnThisDevice(String queueName) {
        return ejb.countScheduledMessagesOnThisDevice(queueName);
    }

    @Override
    public QueueMessage onProcessingStart(String msgId) {
        try {
            return ejb.onProcessingStart(msgId);
        } catch (Throwable e) {
            logDBUpdateFailed("onProcessingStart", msgId, e);
            return null;
        }
    }

    @Override
    public QueueMessage onProcessingSuccessful(String msgId, Outcome outcome) {
        try {
            return ejb.onProcessingSuccessful(msgId, outcome);
        } catch (Throwable e) {
            logDBUpdateFailed("onProcessingSuccessful", msgId, e);
            return null;
        }
    }

    @Override
    public QueueMessage onProcessingFailed(String msgId, Throwable e) {
        try {
            return ejb.onProcessingFailed(msgId, e);
        } catch (Throwable e1) {
            logDBUpdateFailed("onProcessingFailed", msgId, e1);
            return null;
        }
    }

    private static void logDBUpdateFailed(String method, String msgId, Throwable e) {
        LOG.error("Failed to update status of Task[id={}] in DB {}:\n", msgId, method, e);
    }

    @Override
    public boolean cancelTask(String msgId, QueueMessageEvent queueEvent) throws IllegalTaskStateException {
        return ejb.cancelTask(msgId, queueEvent);
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
    public void rescheduleTask(String msgId, String queueName, QueueMessageEvent queueEvent) {
        ejb.rescheduleTask(msgId, queueName, queueEvent);
    }

    @Override
    public boolean deleteTask(String msgId, QueueMessageEvent queueEvent) {
        return ejb.deleteTask(msgId, queueEvent);
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
    public Iterator<QueueMessage> listQueueMessages(TaskQueryParam taskQueryParam, int offset, int limit) {
        return ejb.listQueueMessages(taskQueryParam, offset, limit);
    }

    @Override
    public List<String> listDistinctDeviceNames(TaskQueryParam queueTaskQueryParam) {
        return ejb.listDistinctDeviceNames(queueTaskQueryParam);
    }

    @Override
    public List<String> listQueueMsgIDs(TaskQueryParam queueTaskQueryParam, int limit) {
        return ejb.getQueueMsgIDs(queueTaskQueryParam, limit);
    }

    @Override
    public List<Tuple> listQueueMsgIDAndMsgProps(TaskQueryParam queueTaskQueryParam, int limit) {
        return ejb.listQueueMsgIDAndMsgProps(queueTaskQueryParam, limit);
    }

    @Override
    public Tuple findDeviceNameAndMsgPropsByMsgID(String msgID) {
        return ejb.findDeviceNameAndMsgPropsByMsgID(msgID);
    }
}
