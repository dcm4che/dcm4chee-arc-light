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

package org.dcm4chee.arc.qmgt.impl;

import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.QueueDescriptor;
import org.dcm4chee.arc.entity.ExportTask;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.entity.RetrieveTask;
import org.dcm4chee.arc.qmgt.IllegalTaskStateException;
import org.dcm4chee.arc.qmgt.Outcome;
import org.dcm4chee.arc.qmgt.QueueSizeLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import javax.jms.JMSContext;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Sep 2015
 */
@Stateless
public class QueueManagerEJB {

    private static final Logger LOG = LoggerFactory.getLogger(QueueManagerEJB.class);

    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    @Inject
    private JMSContext jmsCtx;

    @Inject
    private Device device;

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public ObjectMessage createObjectMessage(Serializable object) {
        return jmsCtx.createObjectMessage(object);
    }

    public QueueMessage scheduleMessage(String queueName, ObjectMessage msg, int priority)
            throws QueueSizeLimitExceededException {
        QueueDescriptor queueDescriptor = descriptorOf(queueName);
        int maxQueueSize = queueDescriptor.getMaxQueueSize();
        if (maxQueueSize > 0 && maxQueueSize < countByQueueNameAndStatus(queueName))
            throw new QueueSizeLimitExceededException(queueDescriptor);

        sendMessage(queueDescriptor, msg, 0L, priority);
        QueueMessage entity = new QueueMessage(queueName, msg);
        em.persist(entity);
        LOG.info("Schedule Task[id={}] at Queue {}", entity.getMessageID(), entity.getQueueName());
        return entity;
    }

    private long countByQueueNameAndStatus(String queueName) {
        return em.createNamedQuery(QueueMessage.COUNT_BY_QUEUE_NAME_AND_STATUS, Long.class)
                .setParameter(1, queueName)
                .setParameter(2, QueueMessage.Status.SCHEDULED).getSingleResult();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public QueueMessage onProcessingStart(String msgId) {
        QueueMessage entity = findQueueMessage(msgId);
        if (entity == null || !entity.getStatus().equals(QueueMessage.Status.SCHEDULED)) {
            if (entity == null)
                LOG.info("Suppress processing of Task[id={}]", msgId);
            else
                LOG.info("Suppress processing of Task[id={}] at Queue {} with Status: {}",
                        msgId, entity.getQueueName(), entity.getStatus());
            return null;
        }
        entity.setProcessingStartTime(new Date());
        entity.setStatus(QueueMessage.Status.IN_PROCESS);
        LOG.info("Start processing Task[id={}] from Queue {}", entity.getMessageID(), entity.getQueueName());
        return entity;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public QueueMessage onProcessingSuccessful(String msgId, Outcome outcome) {
        QueueMessage entity = findQueueMessage(msgId);
        if (entity == null) {
            LOG.info("Finished processing of Task[id={}]", msgId);
            return null;
        }
        QueueMessage.Status status = outcome.getStatus();
        String queueName = entity.getQueueName();
        entity.setProcessingEndTime(new Date());
        entity.setOutcomeMessage(outcome.getDescription());
        if (status == QueueMessage.Status.COMPLETED) {
            LOG.info("Finished processing of Task[id={}] at Queue {}", msgId, queueName);
            entity.setStatus(status);
            return entity;
        }
        if (status == QueueMessage.Status.FAILED || status == QueueMessage.Status.WARNING) {
            QueueDescriptor descriptor = descriptorOf(queueName);
            long delay = status == QueueMessage.Status.FAILED || descriptor.isRetryOnWarning()
                    ? descriptor.getRetryDelayInSeconds(entity.incrementNumberOfFailures())
                    : -1L;
            if (delay >= 0) {
                LOG.info("Failed processing of Task[id={}] at Queue {} with Status {} - retry",
                        msgId, queueName, status);
                entity.setStatus(QueueMessage.Status.SCHEDULED);
                rescheduleMessage(entity, descriptor, delay * 1000L);
                return entity;
            }
        }
        LOG.warn("Failed processing of Task[id={}] at Queue {} with Status {}", msgId, queueName, status);
        entity.setStatus(status);
        return entity;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public QueueMessage onProcessingFailed(String msgId, Throwable e) {
        QueueMessage entity = findQueueMessage(msgId);
        if (entity == null) {
            LOG.warn("Failed processing of Task[id={}]:\n", msgId, e);
            return null;
        }

        entity.setErrorMessage(e.getMessage());
        entity.setProcessingEndTime(new Date());
        QueueDescriptor descriptor = descriptorOf(entity.getQueueName());
        long delay = descriptor.getRetryDelayInSeconds(entity.incrementNumberOfFailures());
        if (delay < 0) {
            LOG.warn("Failed processing of Task[id={}] at Queue {}:\n", msgId, entity.getQueueName(), e);
            entity.setStatus(QueueMessage.Status.FAILED);
        } else {
            LOG.info("Failed processing of Task[id={}] at Queue {} - retry:\n", msgId, entity.getQueueName(), e);
            rescheduleMessage(entity, descriptor, delay * 1000L);
        }
        return entity;
    }

    public boolean cancelProcessing(String msgId) throws IllegalTaskStateException {
        QueueMessage entity = findQueueMessage(msgId);
        if (entity == null)
            return false;

        switch (entity.getStatus()) {
            case COMPLETED:
            case CANCELED:
            case WARNING:
            case FAILED:
                throw new IllegalTaskStateException(
                        "Cannot cancel Task[id=" + msgId + "] with Status: " + entity.getStatus());
        }

        entity.setStatus(QueueMessage.Status.CANCELED);
        if (entity.getExportTask() != null)
            entity.getExportTask().setUpdatedTime();
        if (entity.getRetrieveTask() != null)
            entity.getRetrieveTask().setUpdatedTime();
        LOG.info("Cancel processing of Task[id={}] at Queue {}", msgId, entity.getQueueName());
        return true;
    }

    public boolean rescheduleMessage(String msgId, String queueName) throws IllegalTaskStateException {
        QueueMessage entity = findQueueMessage(msgId);
        if (entity == null)
            return false;

        switch (entity.getStatus()) {
            case SCHEDULED:
            case IN_PROCESS:
                throw new IllegalTaskStateException(
                        "Cannot reschedule Task[id=" + msgId + "] with Status: " + entity.getStatus());
        }
        if (queueName != null)
            entity.setQueueName(queueName);
        entity.setNumberOfFailures(0);
        entity.setErrorMessage(null);
        entity.setOutcomeMessage(null);
        rescheduleMessage(entity, descriptorOf(entity.getQueueName()), 0L);
        return true;
    }

    private void rescheduleMessage(QueueMessage entity, QueueDescriptor descriptor, long delay) {
        ObjectMessage msg = entity.initProperties(createObjectMessage(entity.getMessageBody()));
        sendMessage(descriptor, msg, delay, entity.getPriority());
        entity.reschedule(msg, new Date(System.currentTimeMillis() + delay));
        if (entity.getExportTask() != null)
            entity.getExportTask().setUpdatedTime();
        if (entity.getRetrieveTask() != null)
            entity.getRetrieveTask().setUpdatedTime();
        LOG.info("Reschedule Task[id={}] at Queue {}", entity.getMessageID(), entity.getQueueName());
    }

    public boolean deleteMessage(String msgId) {
        QueueMessage entity = findQueueMessage(msgId);
        if (entity == null)
            return false;

        if (entity.getExportTask() != null)
            em.remove(entity.getExportTask());
        else if (entity.getRetrieveTask() != null)
            em.remove(entity.getRetrieveTask());
        else
            em.remove(entity);
        LOG.info("Delete Task[id={}] from Queue {}", entity.getMessageID(), entity.getQueueName());
        return true;
    }

    public int deleteMessages(String queueName, QueueMessage.Status status, Date updatedBefore) {
        if (status != null) {
            if (updatedBefore != null) {
                em.createNamedQuery(ExportTask.DELETE_BY_QUEUE_NAME_AND_STATUS_AND_UPDATED_BEFORE)
                        .setParameter(1, queueName)
                        .setParameter(2, status)
                        .setParameter(3, updatedBefore)
                        .executeUpdate();
                em.createNamedQuery(RetrieveTask.DELETE_BY_QUEUE_NAME_AND_STATUS_AND_UPDATED_BEFORE)
                        .setParameter(1, queueName)
                        .setParameter(2, status)
                        .setParameter(3, updatedBefore)
                        .executeUpdate();
                return em.createNamedQuery(QueueMessage.DELETE_BY_QUEUE_NAME_AND_STATUS_AND_UPDATED_BEFORE)
                        .setParameter(1, queueName)
                        .setParameter(2, status)
                        .setParameter(3, updatedBefore)
                        .executeUpdate();
            }
            em.createNamedQuery(ExportTask.DELETE_BY_QUEUE_NAME_AND_STATUS)
                    .setParameter(1, queueName)
                    .setParameter(2, status)
                    .executeUpdate();
            em.createNamedQuery(RetrieveTask.DELETE_BY_QUEUE_NAME_AND_STATUS)
                    .setParameter(1, queueName)
                    .setParameter(2, status)
                    .executeUpdate();
            return em.createNamedQuery(QueueMessage.DELETE_BY_QUEUE_NAME_AND_STATUS)
                    .setParameter(1, queueName)
                    .setParameter(2, status)
                    .executeUpdate();
        }
        if (updatedBefore != null) {
            em.createNamedQuery(ExportTask.DELETE_BY_QUEUE_NAME_AND_UPDATED_BEFORE)
                    .setParameter(1, queueName)
                    .setParameter(2, updatedBefore)
                    .executeUpdate();
            em.createNamedQuery(RetrieveTask.DELETE_BY_QUEUE_NAME_AND_UPDATED_BEFORE)
                    .setParameter(1, queueName)
                    .setParameter(2, updatedBefore)
                    .executeUpdate();
            return em.createNamedQuery(QueueMessage.DELETE_BY_QUEUE_NAME_AND_UPDATED_BEFORE)
                    .setParameter(1, queueName)
                    .setParameter(2, updatedBefore)
                    .executeUpdate();
        }
        em.createNamedQuery(ExportTask.DELETE_BY_QUEUE_NAME)
                .setParameter(1, queueName)
                .executeUpdate();
        em.createNamedQuery(RetrieveTask.DELETE_BY_QUEUE_NAME)
                .setParameter(1, queueName)
                .executeUpdate();
        return em.createNamedQuery(QueueMessage.DELETE_BY_QUEUE_NAME)
                .setParameter(1, queueName)
                .executeUpdate();
    }

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

    private void sendMessage(QueueDescriptor desc, ObjectMessage msg, long delay, int priority) {
        jmsCtx.createProducer().setDeliveryDelay(delay).setPriority(priority).send(lookup(desc.getJndiName()), msg);
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
            return em.createNamedQuery(QueueMessage.FIND_BY_MSG_ID, QueueMessage.class)
                    .setParameter(1, msgId)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

}