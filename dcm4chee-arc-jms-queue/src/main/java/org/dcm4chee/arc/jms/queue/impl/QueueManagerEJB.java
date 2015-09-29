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

package org.dcm4chee.arc.jms.queue.impl;

import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.jms.queue.QueueManager;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import javax.jms.*;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import java.io.Serializable;
import java.util.Date;

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

    @Override
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public ObjectMessage createObjectMessage(Serializable object) {
        return jmsCtx.createObjectMessage(object);
    }

    @Override
    public void scheduleMessage(String queueName, String jndiName, ObjectMessage msg) {
        try {
            jmsCtx.createProducer().send(lookupQueue(jndiName), msg);
            em.persist(new QueueMessage(queueName, jndiName, msg));
        } catch (JMSException e) {
            throw toJMSRuntimeException(e);
        }
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public QueueMessage onProcessingStart(Message msg) {
        try {
            QueueMessage entity = findScheduledQueueMessage(msg.getJMSMessageID());
            if (entity == null)
                return null;

            entity.setDeliveryCount(msg.getIntProperty("JMSXDeliveryCount"));
            entity.setProcessingStartTime(new Date());
            entity.setStatus(QueueMessage.Status.IN_PROCESS);
            return entity;
        } catch (JMSException e) {
            throw toJMSRuntimeException(e);
        }
    }

    private JMSRuntimeException toJMSRuntimeException(JMSException e) {
        return new JMSRuntimeException(e.getMessage(), e.getErrorCode(), e.getCause());
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void onProcessingSuccessful(QueueMessage entity) {
        entity.setProcessingEndTime(new Date());
        entity.setStatus(QueueMessage.Status.COMPLETED);
        em.merge(entity);
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void onProcessingFailed(QueueMessage entity, Exception e) {
        entity.setErrorMessage(e.getMessage());
        entity.setProcessingEndTime(new Date());
        entity.setStatus(QueueMessage.Status.SCHEDULED);
        em.merge(entity);
    }

    @Override
    public void onRedeliveryExhausted(Message msg) {
        try {
            QueueMessage entity = findQueueMessage(msg.getJMSMessageID());
            entity.setStatus(QueueMessage.Status.FAILED);
            em.merge(entity);
        } catch (JMSException e) {
            throw toJMSRuntimeException(e);
        }
    }

    @Override
    public void cancelProcessing(String msgId) {
        QueueMessage entity = findQueueMessage(msgId);
        entity.setStatus(QueueMessage.Status.CANCELED);
        em.merge(entity);
    }

    @Override
    public void rescheduleMessage(String msgId) {
        try {
            QueueMessage entity = findQueueMessage(msgId);
            scheduleMessage(entity.getQueueName(), entity.getJndiName(),
                    entity.initProperties(createObjectMessage(entity.getMessageBody())));
        } catch (JMSException e) {
            throw toJMSRuntimeException(e);
        }
    }

    @Override
    public void deleteMessage(String msgId) {
        em.remove(findQueueMessage(msgId));
    }

    private Queue lookupQueue(String jndiName) {
        try {
            return InitialContext.doLookup(jndiName);
        } catch (NamingException e) {
            throw new RuntimeException(e);
        }
    }

    private QueueMessage findQueueMessage(String msgId) {
        return em.createNamedQuery(QueueMessage.FIND_BY_MSG_ID, QueueMessage.class)
                .setParameter(1, msgId)
                .getSingleResult();
    }

    private QueueMessage findScheduledQueueMessage(String msgId) {
        try {
            QueueMessage entity = em.createNamedQuery(QueueMessage.FIND_BY_MSG_ID, QueueMessage.class)
                    .setParameter(1, msgId)
                    .getSingleResult();
            if (entity.getStatus().equals(QueueMessage.Status.SCHEDULED))
                return entity;
        } catch (NoResultException ignore) {}
        return null;
    }

}