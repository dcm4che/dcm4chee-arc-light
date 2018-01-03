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

import org.dcm4che3.net.Device;
import org.dcm4chee.arc.entity.QueueMessage;
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
    public boolean cancelProcessing(String msgId) throws IllegalTaskStateException {
        if (!ejb.cancelProcessing(msgId))
            return false;

        messageCanceledEvent.fire(new MessageCanceled(msgId));
        return true;
    }

    @Override
    public int cancelTasks(String queueName, String dicomDeviceName, QueueMessage.Status status, String createdTime,
                               String updatedTime) {
        return ejb.cancelTasks(queueName, dicomDeviceName, status, createdTime, updatedTime);

        //TODO - messageCanceledEvent.fire(new MessageCanceled());
    }

    @Override
    public boolean rescheduleMessage(String msgId, String queueName)
            throws IllegalTaskStateException, DifferentDeviceException {
        return ejb.rescheduleMessage(msgId, queueName);
    }

    @Override
    public boolean deleteMessage(String msgId) {
        return ejb.deleteMessage(msgId);
    }

    @Override
    public int deleteMessages(String queueName, QueueMessage.Status status, String deviceName, String createdTime, String updatedTime) {
        return ejb.deleteMessages(queueName, status, deviceName, createdTime, updatedTime);
    }

    @Override
    public List<QueueMessage> search(
            String queueName, String deviceName, QueueMessage.Status status, String createdTime, String updatedTime, int offset, int limit) {
        return ejb.search(queueName, deviceName, status, createdTime, updatedTime, offset, limit);
    }

    @Override
    public long countTasks(
            String queueName, String deviceName, QueueMessage.Status status, String createdTime, String updatedTime) {
        return ejb.countTasks(queueName, deviceName, status, createdTime, updatedTime);
    }
}
