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
 */

package org.dcm4chee.arc.qmgt.impl;

import com.querydsl.core.types.Predicate;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.event.QueueMessageEvent;
import org.dcm4chee.arc.qmgt.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.jms.ObjectMessage;
import java.io.Serializable;
import java.util.List;

@ApplicationScoped
public class QueueManagerImpl implements QueueManager {

    private static final Logger LOG = LoggerFactory.getLogger(QueueManagerEJB.class);

    @Inject
    private Device device;

    @Inject
    private QueueManagerEJB ejb;

    @Inject
    private Event<MessageCanceled> messageCanceledEvent;

    @Override
    public ObjectMessage createObjectMessage(Serializable object) {
        return ejb.createObjectMessage(object);
    }

    @Override
    public QueueMessage scheduleMessage(String queueName, ObjectMessage message, int priority)
            throws QueueSizeLimitExceededException {
        return ejb.scheduleMessage(device.getDeviceName(), queueName, message, priority);
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
        if (!ejb.cancelTask(msgId, queueEvent))
            return false;

        messageCanceledEvent.fire(new MessageCanceled(msgId));
        return true;
    }

    @Override
    public long cancelTasks(Predicate matchQueueMessage, QueueMessage.Status prev)
            throws IllegalTaskStateException {
        if (prev == QueueMessage.Status.IN_PROCESS) {
            List<String> msgIDs = ejb.getQueueMsgIDs(matchQueueMessage, 0);
            for (String msgID : msgIDs)
                cancelTask(msgID, null);
            return msgIDs.size();
        }
        return ejb.cancelTasks(matchQueueMessage);
    }

    @Override
    public long cancelExportTasks(Predicate matchQueueMessage, Predicate matchExportTask, QueueMessage.Status prev)
            throws IllegalTaskStateException {
        if (prev == QueueMessage.Status.IN_PROCESS) {
            List<String> msgIDs = ejb.getExportTasksReferencedQueueMsgIDs(matchQueueMessage, matchExportTask);
            for (String msgID : msgIDs)
                cancelTask(msgID, null);
            return msgIDs.size();
        }
        return ejb.cancelExportTasks(matchQueueMessage, matchExportTask);
    }

    @Override
    public long cancelRetrieveTasks(Predicate matchQueueMessage, Predicate matchRetrieveTask, QueueMessage.Status prev)
            throws IllegalTaskStateException {
        if (prev == QueueMessage.Status.IN_PROCESS) {
            List<String> msgIDs = ejb.getRetrieveTasksReferencedQueueMsgIDs(matchQueueMessage, matchRetrieveTask);
            for (String msgID : msgIDs)
                cancelTask(msgID, null);
            return msgIDs.size();
        }
        return ejb.cancelRetrieveTasks(matchQueueMessage, matchRetrieveTask);
    }

    @Override
    public boolean rescheduleTask(String msgId, String queueName, QueueMessageEvent queueEvent)
            throws IllegalTaskStateException, DifferentDeviceException {
        return ejb.rescheduleTask(msgId, queueName, queueEvent);
    }

    @Override
    public boolean rescheduleTask(QueueMessage task, String queueName, QueueMessageEvent queueEvent)
            throws IllegalTaskStateException, DifferentDeviceException {
        return ejb.rescheduleTask(task, queueName, queueEvent);
    }

    @Override
    public boolean deleteTask(String msgId, QueueMessageEvent queueEvent) {
        return ejb.deleteTask(msgId, queueEvent);
    }

    @Override
    public int deleteTasks(String queueName, Predicate matchQueueMessage) {
        return ejb.deleteTasks(matchQueueMessage);
    }

    @Override
    public List<QueueMessage> search(Predicate matchQueueMessage, int offset, int limit) {
        return ejb.search(matchQueueMessage, offset, limit);
    }

    @Override
    public long countTasks(Predicate matchQueueMessage) {
        return ejb.countTasks(matchQueueMessage);
    }

    @Override
    public List<String> getQueueMsgIDs(Predicate matchQueueMessage, int limit) {
        return ejb.getQueueMsgIDs(matchQueueMessage, limit);
    }
}
