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
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
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

package org.dcm4chee.arc.qmgt.impl;

import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.QueueDescriptor;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.qmgt.*;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.jms.*;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Sep 2015
 */
@Stateless
public class QueueManagerEJB implements QueueManager {

    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    @Inject
    private JMSContext jmsCtx;

    @Inject
    private Device device;

    @Inject
    private Event<MessageCanceled> messageCanceledEvent;

    @Override
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public ObjectMessage createObjectMessage(Serializable object) {
        return jmsCtx.createObjectMessage(object);
    }

    @Override
    public void scheduleMessage(String queueName, ObjectMessage msg) {
        sendMessage(descriptorOf(queueName), msg, 0L);
        try {
            em.persist(new QueueMessage(queueName, msg));
        } catch (JMSException e) {
            throw toJMSRuntimeException(e);
        }
    }

    @Override
    public boolean onProcessingStart(String msgId) {
        QueueMessage entity = findQueueMessage(msgId);
        if (entity == null || !entity.getStatus().equals(QueueMessage.Status.SCHEDULED))
            return false;
        entity.setProcessingStartTime(new Date());
        entity.setStatus(QueueMessage.Status.IN_PROCESS);
        return true;
    }

    @Override
    public void onProcessingSuccessful(String msgId, Outcome outcome) {
        QueueMessage entity = findQueueMessage(msgId);
        if (entity == null)
            return;

        entity.setProcessingEndTime(new Date());
        entity.setStatus(outcome.getStatus());
        entity.setOutcomeMessage(outcome.getDescription());
    }

    @Override
    public void onProcessingFailed(String msgId, Exception e) {
        QueueMessage entity = findQueueMessage(msgId);
        if (entity == null)
            return;

        entity.setErrorMessage(e.getMessage());
        entity.setProcessingEndTime(new Date());
        QueueDescriptor descriptor = descriptorOf(entity.getQueueName());
        long delay = descriptor.getRetryDelayInSeconds(entity.incrementNumberOfFailures());
        if (delay < 0)
            entity.setStatus(QueueMessage.Status.FAILED);
        else
            rescheduleMessage(entity, descriptor, delay * 1000L);
    }

    @Override
    public void cancelProcessing(String msgId) throws MessageAlreadyDeletedException {
        QueueMessage entity = getQueueMessage(msgId);
        entity.setStatus(QueueMessage.Status.CANCELED);
        messageCanceledEvent.fire(new MessageCanceled(msgId));
    }

    @Override
    public void rescheduleMessage(String msgId)
            throws MessageAlreadyDeletedException, IllegalMessageStatusException {
        QueueMessage entity = getQueueMessage(msgId);
        switch (entity.getStatus()) {
            case SCHEDULED:
            case IN_PROCESS:
                throw new IllegalMessageStatusException(
                        "Cannot reschedule Message[id=" + msgId + "] with status: " + entity.getStatus());
        }
        entity.setNumberOfFailures(0);
        entity.setErrorMessage(null);
        entity.setOutcomeMessage(null);
        rescheduleMessage(entity, descriptorOf(entity.getQueueName()), 0L);
    }

    private void rescheduleMessage(QueueMessage entity, QueueDescriptor descriptor, long delay) {
        try {
            ObjectMessage msg = entity.initProperties(createObjectMessage(entity.getMessageBody()));
            sendMessage(descriptor, msg, delay);
            entity.setMessageID(msg.getJMSMessageID());
            entity.setScheduledTime(new Date(System.currentTimeMillis() + delay));
            entity.setStatus(QueueMessage.Status.SCHEDULED);
        } catch (JMSException e) {
            throw toJMSRuntimeException(e);
        }

    }

    @Override
    public void deleteMessage(String msgId) throws MessageAlreadyDeletedException {
        em.remove(getQueueMessage(msgId));
    }

    @Override
    public int deleteMessages(String queueName, QueueMessage.Status status, Date updatedBefore) {
        Query query = status != null
                ? updatedBefore != null
                    ? em.createNamedQuery(QueueMessage.DELETE_BY_QUEUE_NAME_AND_STATUS_AND_UPDATED_BEFORE)
                        .setParameter(1, queueName)
                        .setParameter(2, status)
                        .setParameter(3, updatedBefore)
                    : em.createNamedQuery(QueueMessage.DELETE_BY_QUEUE_NAME_AND_STATUS)
                        .setParameter(1, queueName)
                        .setParameter(2, status)
                : updatedBefore != null
                    ? em.createNamedQuery(QueueMessage.DELETE_BY_QUEUE_NAME_AND_UPDATED_BEFORE)
                        .setParameter(1, queueName)
                        .setParameter(2, updatedBefore)
                    : em.createNamedQuery(QueueMessage.DELETE_BY_QUEUE_NAME)
                        .setParameter(1, queueName);
        return query.executeUpdate();
    }

    @Override
    public List<QueueMessage> search(String queueName, QueueMessage.Status status, int offset, int limit) {
        TypedQuery<QueueMessage> query = status != null
                ? em.createNamedQuery(QueueMessage.FIND_BY_QUEUE_NAME_AND_STATUS, QueueMessage.class)
                    .setParameter(1, queueName)
                    .setParameter(2, status)
                : em.createNamedQuery(QueueMessage.FIND_BY_QUEUE_NAME, QueueMessage.class)
                    .setParameter(1, queueName);
        if (offset > 0)
            query.setFirstResult(offset);
        if (limit > 0)
            query.setMaxResults(limit);
        return query.getResultList();
    }

    private void sendMessage(QueueDescriptor desc, ObjectMessage msg, long delay) {
        jmsCtx.createProducer().setDeliveryDelay(delay).send(lookup(desc.getJndiName()), msg);
    }

    private JMSRuntimeException toJMSRuntimeException(JMSException e) {
        return new JMSRuntimeException(e.getMessage(), e.getErrorCode(), e.getCause());
    }

    private Queue lookup(String jndiName) {
        try {
            return InitialContext.doLookup(jndiName);
        } catch (NamingException e) {
            throw new RuntimeException(e);
        }
    }

    private QueueDescriptor descriptorOf(String queueName) {
        return device.getDeviceExtension(ArchiveDeviceExtension.class).getQueueDescriptorNotNull(queueName);
    }

    private QueueMessage findQueueMessage(String msgId) {
        try {
            return queryQueueMessage(msgId);
        } catch (NoResultException e) {
            return null;
        }
    }

    private QueueMessage getQueueMessage(String msgId) throws MessageAlreadyDeletedException {
        try {
            return queryQueueMessage(msgId);
        } catch (NoResultException e) {
            throw new MessageAlreadyDeletedException("Message[id=" + msgId + "] already deleted");
        }
    }

    private QueueMessage queryQueueMessage(String msgId) {
        return em.createNamedQuery(QueueMessage.FIND_BY_MSG_ID, QueueMessage.class)
                .setParameter(1, msgId)
                .getSingleResult();
    }

}