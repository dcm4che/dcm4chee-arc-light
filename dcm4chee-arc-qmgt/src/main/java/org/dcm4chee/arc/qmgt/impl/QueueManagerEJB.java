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

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.jpa.hibernate.HibernateDeleteClause;
import com.querydsl.jpa.hibernate.HibernateQuery;
import com.querydsl.jpa.hibernate.HibernateUpdateClause;
import org.dcm4che3.data.DatePrecision;
import org.dcm4che3.data.DateRange;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.QueueDescriptor;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.qmgt.*;
import org.dcm4chee.arc.query.util.MatchDateTimeRange;
import org.hibernate.Session;
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

    public QueueMessage scheduleMessage(String deviceName, String queueName, ObjectMessage msg, int priority)
            throws QueueSizeLimitExceededException {
        QueueDescriptor queueDescriptor = descriptorOf(queueName);
        int maxQueueSize = queueDescriptor.getMaxQueueSize();
        if (maxQueueSize > 0 && maxQueueSize < countByQueueNameAndStatus(queueName))
            throw new QueueSizeLimitExceededException(queueDescriptor);

        sendMessage(queueDescriptor, msg, 0L, priority);
        QueueMessage entity = new QueueMessage(deviceName, queueName, msg);
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
        if (entity == null) {
            LOG.info("Suppress processing of already deleted Task[id={}]", msgId);
        } else switch (entity.getStatus()) {
            case IN_PROCESS:
            case SCHEDULED:
                LOG.info("Start processing Task[id={}] from Queue {} with Status: {}",
                        entity.getMessageID(), entity.getQueueName(), entity.getStatus());
                entity.setProcessingStartTime(new Date());
                entity.setStatus(QueueMessage.Status.IN_PROCESS);
                return entity;
            default:
                LOG.info("Suppress processing of Task[id={}] from Queue {} with Status: {}",
                        msgId, entity.getQueueName(), entity.getStatus());
        }
        return null;
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

    public int cancelTasksInQueue(String queueName, String deviceName, QueueMessage.Status status, String createdTime,
                            String updatedTime, BooleanBuilder exportPredicate, BooleanBuilder extRetrievePredicate) {
        BooleanBuilder predicate = new BooleanBuilder();
        predicate.and(QQueueMessage.queueMessage.queueName.eq(queueName));
        predicate.and(QQueueMessage.queueMessage.status.eq(status));
        addOptionalPredicates(deviceName, createdTime, updatedTime, predicate);

        HibernateQuery<QueueMessage> queueMsgSubQuery = new HibernateQuery<QueueMessage>(em.unwrap(Session.class))
                .from(QQueueMessage.queueMessage)
                .where(predicate);

        if (queueName.equals("Export1") || queueName.equals("Export2") || queueName.equals("Export3"))
            exportUpdateClause(exportPredicate, queueMsgSubQuery).execute();
        if (queueName.equals("CMoveSCU"))
            extRetrieveUpdateClause(extRetrievePredicate, queueMsgSubQuery).execute();

        LOG.info("Cancel processing of Tasks with Status {} at Queue {}", status.toString(), queueName);
        return (int) new HibernateUpdateClause(em.unwrap(Session.class), QQueueMessage.queueMessage)
                .set(QQueueMessage.queueMessage.status, QueueMessage.Status.CANCELED)
                .set(QQueueMessage.queueMessage.updatedTime, new Date())
                .where(predicate).execute();
    }

    private HibernateUpdateClause extRetrieveUpdateClause(
            BooleanBuilder extRetrievePredicate, HibernateQuery<QueueMessage> queueMsgSubQuery) {
        return extRetrievePredicate != null
                ? new HibernateUpdateClause(em.unwrap(Session.class), QRetrieveTask.retrieveTask)
                    .set(QRetrieveTask.retrieveTask.updatedTime, new Date())
                    .where(extRetrievePredicate, QRetrieveTask.retrieveTask.queueMessage.in(queueMsgSubQuery))
                : new HibernateUpdateClause(em.unwrap(Session.class), QRetrieveTask.retrieveTask)
                    .set(QRetrieveTask.retrieveTask.updatedTime, new Date())
                    .where(QRetrieveTask.retrieveTask.queueMessage.in(queueMsgSubQuery));
    }

    private HibernateUpdateClause exportUpdateClause(
            BooleanBuilder exportPredicate, HibernateQuery<QueueMessage> queueMsgSubQuery) {
        return exportPredicate != null
                ? new HibernateUpdateClause(em.unwrap(Session.class), QExportTask.exportTask)
                    .set(QExportTask.exportTask.updatedTime, new Date())
                    .where(exportPredicate, QExportTask.exportTask.queueMessage.in(queueMsgSubQuery))
                : new HibernateUpdateClause(em.unwrap(Session.class), QExportTask.exportTask)
                    .set(QExportTask.exportTask.updatedTime, new Date())
                    .where(QExportTask.exportTask.queueMessage.in(queueMsgSubQuery));
    }

    private void addOptionalPredicates(String deviceName, String createdTime, String updatedTime, BooleanBuilder predicate) {
        if (deviceName != null)
            predicate.and(QQueueMessage.queueMessage.deviceName.eq(deviceName));
        if (createdTime != null)
            predicate.and(ExpressionUtils.and(MatchDateTimeRange.range(
                QQueueMessage.queueMessage.createdTime, getDateRange(createdTime), MatchDateTimeRange.FormatDate.DT),
                QQueueMessage.queueMessage.createdTime.isNotNull()));
        if (updatedTime != null)
            predicate.and(ExpressionUtils.and(MatchDateTimeRange.range(
                QQueueMessage.queueMessage.updatedTime, getDateRange(updatedTime), MatchDateTimeRange.FormatDate.DT),
                QQueueMessage.queueMessage.updatedTime.isNotNull()));
    }

    public boolean rescheduleMessage(String msgId, String queueName)
            throws IllegalTaskStateException, DifferentDeviceException {
        QueueMessage entity = findQueueMessage(msgId);
        if (entity == null)
            return false;

        if (!device.getDeviceName().equals(entity.getDeviceName()))
            throw new DifferentDeviceException("Cannot reschedule Task[id=" + msgId
                    + "] previous scheduled on Device " + entity.getDeviceName()
                    + " on Device " + device.getDeviceName());

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



    public int deleteMessages(String queueName, QueueMessage.Status status, String deviceName, String createdTime, String updatedTime) {
        BooleanBuilder queueMessagePredicate = new BooleanBuilder();
        queueMessagePredicate.and(QQueueMessage.queueMessage.queueName.eq(queueName));
        if (status != null)
            queueMessagePredicate.and(QQueueMessage.queueMessage.status.eq(status));
        addOptionalPredicates(deviceName, createdTime, updatedTime, queueMessagePredicate);

        HibernateQuery<QueueMessage> queueMessageQuery = new HibernateQuery<QueueMessage>(em.unwrap(Session.class))
                                .from(QQueueMessage.queueMessage)
                                .where(queueMessagePredicate);

        new HibernateDeleteClause(em.unwrap(Session.class), QExportTask.exportTask)
                .where(QExportTask.exportTask.queueMessage.in(queueMessageQuery)).execute();
        new HibernateDeleteClause(em.unwrap(Session.class), QRetrieveTask.retrieveTask)
                .where(QRetrieveTask.retrieveTask.queueMessage.in(queueMessageQuery)).execute();

        return (int) new HibernateDeleteClause(em.unwrap(Session.class), QQueueMessage.queueMessage)
                .where(queueMessagePredicate).execute();
    }

    public List<QueueMessage> search(String queueName,
            String deviceName, QueueMessage.Status status, String createdTime, String updatedTime, int offset, int limit) {
        return createQuery(queueName, deviceName, status, createdTime, updatedTime, offset, limit).fetch();
    }

    public long countTasks(String queueName, String deviceName, QueueMessage.Status status, String createdTime, String updatedTime) {
        return createQuery(queueName, deviceName, status, createdTime, updatedTime, 0, 0).fetchCount();
    }

    private HibernateQuery<QueueMessage> createQuery(
            String queueName, String deviceName, QueueMessage.Status status, String createdTime, String updatedTime,
            int offset, int limit) {
        BooleanBuilder builder = new BooleanBuilder();
        builder.and(QQueueMessage.queueMessage.queueName.eq(queueName));
        if (status != null)
            builder.and(QQueueMessage.queueMessage.status.eq(status));
        addOptionalPredicates(deviceName, createdTime, updatedTime, builder);

        HibernateQuery<QueueMessage> query = new HibernateQuery<QueueMessage>(em.unwrap(Session.class))
                .from(QQueueMessage.queueMessage)
                .where(builder);
        if (limit > 0)
            query.limit(limit);
        if (offset > 0)
            query.offset(offset);
        query.orderBy(QQueueMessage.queueMessage.updatedTime.desc());
        return query;
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

    private static DateRange getDateRange(String s) {
        String[] range = splitRange(s);
        DatePrecision precision = new DatePrecision();
        Date start = range[0] == null ? null
                : VR.DT.toDate(range[0], null, 0, false, null, precision);
        Date end = range[1] == null ? null
                : VR.DT.toDate(range[1], null, 0, true, null, precision);
        return new DateRange(start, end);
    }

    private static String[] splitRange(String s) {
        String[] range = new String[2];
        int delim = s.indexOf('-');
        if (delim == -1)
            range[0] = range[1] = s;
        else {
            if (delim > 0)
                range[0] =  s.substring(0, delim);
            if (delim < s.length() - 1)
                range[1] =  s.substring(delim+1);
        }
        return range;
    }

}