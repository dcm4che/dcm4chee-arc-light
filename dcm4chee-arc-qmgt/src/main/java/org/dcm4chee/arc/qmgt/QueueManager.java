/*
 * *** BEGIN LICENSE BLOCK *****
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
 * Portions created by the Initial Developer are Copyright (C) 2015
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
 * *** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.qmgt;

import com.querydsl.core.types.Predicate;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.event.QueueMessageEvent;

import javax.jms.ObjectMessage;
import java.io.Serializable;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Sep 2015
 */
public interface QueueManager {
    ObjectMessage createObjectMessage(Serializable object);

    QueueMessage scheduleMessage(String queueName, ObjectMessage message, int priority)
            throws QueueSizeLimitExceededException;

    QueueMessage onProcessingStart(String msgId);

    QueueMessage onProcessingSuccessful(String msgId, Outcome outcome);

    QueueMessage onProcessingFailed(String msgId, Throwable e);

    boolean cancelTask(String msgId, QueueMessageEvent queueEvent) throws IllegalTaskStateException;

    long cancelTasks(Predicate matchQueueMessage, QueueMessage.Status prev) throws IllegalTaskStateException;

    long cancelExportTasks(Predicate matchQueueMessage, Predicate matchExportTask, QueueMessage.Status prev)
            throws IllegalTaskStateException;

    long cancelRetrieveTasks(Predicate matchQueueMessage, Predicate matchRetrieveTask, QueueMessage.Status prevStatus)
            throws IllegalTaskStateException;

    boolean rescheduleTask(String msgId, String queueName, QueueMessageEvent queueEvent)
            throws IllegalTaskStateException, DifferentDeviceException;

    boolean deleteTask(String msgId, QueueMessageEvent queueEvent);

    boolean rescheduleTask(QueueMessage task, String queueName, QueueMessageEvent queueEvent)
            throws IllegalTaskStateException, DifferentDeviceException;

    int deleteTasks(String queueName, Predicate matchQueueMessage);

    List<QueueMessage> search(Predicate matchQueueMessage, int offset, int limit);

    long countTasks(Predicate matchQueueMessage);

    List<String> getQueueMsgIDs(Predicate matchQueueMessage, int limit);
}
