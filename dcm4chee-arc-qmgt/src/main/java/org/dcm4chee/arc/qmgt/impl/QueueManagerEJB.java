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
 * *** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.qmgt.impl;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.persistence.criteria.Predicate;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.HL7DeviceExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.QueueDescriptor;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.event.QueueMessageEvent;
import org.dcm4chee.arc.qmgt.*;
import org.dcm4chee.arc.query.util.MatchTask;
import org.dcm4chee.arc.query.util.QueryBuilder;
import org.dcm4chee.arc.query.util.TaskQueryParam;
import org.hibernate.annotations.QueryHints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.jms.*;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.persistence.*;
import javax.persistence.criteria.*;
import javax.persistence.metamodel.SingularAttribute;
import java.io.Serializable;
import java.io.StringReader;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
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

    @Inject
    private Event<MessageCanceled> messageCanceledEvent;

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public ObjectMessage createObjectMessage(Serializable object) {
        return jmsCtx.createObjectMessage(object);
    }

    public QueueMessage scheduleMessage(String queueName, ObjectMessage msg, int priority, String batchID, long delay)
            throws QueueSizeLimitExceededException {
        QueueDescriptor queueDescriptor = descriptorOf(queueName);
        int maxQueueSize = queueDescriptor.getMaxQueueSize();
        if (maxQueueSize > 0 && maxQueueSize < countScheduledMessagesOnThisDevice(queueName))
            throw new QueueSizeLimitExceededException(queueDescriptor);

        sendMessage(queueDescriptor, msg, delay, priority);
        QueueMessage entity = new QueueMessage(device.getDeviceName(), queueName, msg, delay);
        entity.setBatchID(batchID);
        em.persist(entity);
        LOG.info("Schedule Task[id={}] at Queue {}", entity.getMessageID(), entity.getQueueName());
        return entity;
    }

    public long countScheduledMessagesOnThisDevice(String queueName) {
        return em.createNamedQuery(QueueMessage.COUNT_BY_DEVICE_AND_QUEUE_NAME_AND_STATUS, Long.class)
                .setParameter(1, device.getDeviceName())
                .setParameter(2, queueName)
                .setParameter(3, QueueMessage.Status.SCHEDULED).getSingleResult();
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
                setUpdateTime(entity);
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
        entity.setStatus(status);
        setUpdateTime(entity);
        if (status == QueueMessage.Status.COMPLETED
                || status == QueueMessage.Status.WARNING && !descriptorOf(queueName).isRetryOnWarning()) {
            LOG.info("Finished processing of Task[id={}] at Queue {} with Status {}", msgId, queueName, status);
            return entity;
        }
        QueueDescriptor descriptor = descriptorOf(queueName);
        long delay = descriptor.getRetryDelayInSeconds(entity.incrementNumberOfFailures());
        if (delay >= 0) {
            LOG.info("Failed processing of Task[id={}] at Queue {} with Status {} - retry",
                    msgId, queueName, status);
            entity.setStatus(QueueMessage.Status.SCHEDULED);
            rescheduleTask(entity, descriptor, delay * 1000L);
            return entity;
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
            setUpdateTime(entity);
        } else {
            LOG.info("Failed processing of Task[id={}] at Queue {} - retry:\n", msgId, entity.getQueueName(), e);
            rescheduleTask(entity, descriptor, delay * 1000L);
        }
        return entity;
    }

    public boolean cancelTask(String msgId, QueueMessageEvent queueEvent) throws IllegalTaskStateException {
        QueueMessage entity = findQueueMessage(msgId);
        if (entity == null)
            return false;

        if (queueEvent != null)
            queueEvent.setQueueMsg(entity);

        switch (entity.getStatus()) {
            case COMPLETED:
            case CANCELED:
            case WARNING:
            case FAILED:
                throw new IllegalTaskStateException(
                        "Cannot cancel Task[id=" + msgId + "] with Status: " + entity.getStatus());
        }
        cancelTask(entity);
        return true;
    }

    private void cancelTask(QueueMessage entity) {
        entity.setStatus(QueueMessage.Status.CANCELED);
        setUpdateTime(entity);
        LOG.info("Cancel processing of Task[id={}] at Queue {}", entity.getMessageID(), entity.getQueueName());
        messageCanceledEvent.fire(new MessageCanceled(entity.getMessageID()));
    }

    private void setUpdateTime(QueueMessage entity) {
        if (entity.getExportTask() != null)
            entity.getExportTask().setUpdatedTime();
        else if (entity.getRetrieveTask() != null)
            entity.getRetrieveTask().setUpdatedTime();
        else if (entity.getDiffTask() != null)
            entity.getDiffTask().setUpdatedTime();
        else if (entity.getStorageVerificationTask() != null)
            entity.getStorageVerificationTask().setUpdatedTime();
    }

    public long cancelTasks(TaskQueryParam queueTaskQueryParam) {
        Date now = new Date();
        Subquery<QueueMessage> sq = queueMsgQuery(queueTaskQueryParam);

        if (queueTaskQueryParam.getStatus() == QueueMessage.Status.IN_PROCESS) {
            CriteriaQuery<QueueMessage> q = em.getCriteriaBuilder().createQuery(QueueMessage.class);
            Root<QueueMessage> queueMsg = q.from(QueueMessage.class);
            AtomicInteger count = new AtomicInteger(0);
            em.createQuery(q.where(queueMsg.in(sq)).select(queueMsg))
                    .getResultStream()
                    .forEach(qMsg -> {
                        cancelTask(qMsg);
                        count.getAndIncrement();
                    });
            return count.get();
        }

        updateExportTaskUpdatedTime(sq, now);
        updateRetrieveTaskUpdatedTime(sq, now);
        updateDiffTaskUpdatedTime(sq, now);
        updateStgVerTaskUpdatedTime(sq, now);
        return updateStatus(sq, QueueMessage.Status.CANCELED, now);
    }

    private Subquery<QueueMessage> queueMsgQuery(TaskQueryParam queueTaskQueryParam) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        MatchTask matchTask = new MatchTask(cb);
        CriteriaQuery<QueueMessage> q = cb.createQuery(QueueMessage.class);
        Subquery<QueueMessage> sq = q.subquery(QueueMessage.class);
        Root<QueueMessage> queueMsg = sq.from(QueueMessage.class);
        List<Predicate> predicates = matchTask.queueMsgPredicates(queueMsg, queueTaskQueryParam);
        if (!predicates.isEmpty())
            sq.where(predicates.toArray(new Predicate[0]));

        return sq.select(queueMsg);
    }

    public long cancelExportTasks(TaskQueryParam queueTaskQueryParam, TaskQueryParam exportTaskQueryParam) {
        Date now = new Date();
        Subquery<QueueMessage> sq = exportTaskQuery(queueTaskQueryParam, exportTaskQueryParam);

        if (queueTaskQueryParam.getStatus() == QueueMessage.Status.IN_PROCESS)
            return cancelInProcessTasks(sq, ExportTask_.queueMessage, ExportTask.class);

        updateExportTaskUpdatedTime(sq, now);
        return updateStatus(sq, QueueMessage.Status.CANCELED, now);
    }

    private <T> long cancelInProcessTasks(Subquery<QueueMessage> sq, SingularAttribute<T, QueueMessage> queueMsg,
                                          Class<T> clazz) {
        CriteriaQuery<QueueMessage> q = em.getCriteriaBuilder().createQuery(QueueMessage.class);
        Root<T> task = q.from(clazz);
        AtomicInteger count = new AtomicInteger(0);
        em.createQuery(q.where(task.get(queueMsg).in(sq)).select(task.get(queueMsg)))
                .getResultStream()
                .forEach(qMsg -> {
                    cancelTask(qMsg);
                    count.getAndIncrement();
                });
        return count.get();
    }

    private void updateExportTaskUpdatedTime(Subquery<QueueMessage> sq, Date now) {
        CriteriaUpdate<ExportTask> q = em.getCriteriaBuilder().createCriteriaUpdate(ExportTask.class);
        Root<ExportTask> exportTask = q.from(ExportTask.class);
        em.createQuery(q.where(exportTask.get(ExportTask_.queueMessage).in(sq))
                .set(exportTask.get(ExportTask_.updatedTime), now))
                .executeUpdate();
    }

    private long updateStatus(Subquery<QueueMessage> sq, QueueMessage.Status status, Date now) {
        CriteriaUpdate<QueueMessage> q = em.getCriteriaBuilder().createCriteriaUpdate(QueueMessage.class);
        Root<QueueMessage> queueMsg = q.from(QueueMessage.class);
        return em.createQuery(q.where(queueMsg.get(QueueMessage_.pk).in(sq))
                .set(queueMsg.get(QueueMessage_.updatedTime), now)
                .set(queueMsg.get(QueueMessage_.status), status))
                .executeUpdate();
    }

    private Subquery<QueueMessage> exportTaskQuery(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam exportTaskQueryParam) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        MatchTask matchTask = new MatchTask(cb);

        CriteriaQuery<ExportTask> q = cb.createQuery(ExportTask.class);
        Subquery<QueueMessage> sq = q.subquery(QueueMessage.class);
        Root<ExportTask> exportTask = sq.from(ExportTask.class);
        Join<ExportTask, QueueMessage> queueMsg = sq.correlate(exportTask.join(ExportTask_.queueMessage));

        List<Predicate> predicates = matchTask.exportPredicates(
                queueMsg, exportTask, queueTaskQueryParam, exportTaskQueryParam);
        if (!predicates.isEmpty())
            sq.where(predicates.toArray(new Predicate[0]));
        return sq.select(exportTask.get(ExportTask_.queueMessage));
    }

    public long cancelRetrieveTasks(TaskQueryParam queueTaskQueryParam, TaskQueryParam retrieveTaskQueryParam) {
        Date now = new Date();
        Subquery<QueueMessage> sq = retrieveTaskQuery(queueTaskQueryParam, retrieveTaskQueryParam);

        if (queueTaskQueryParam.getStatus() == QueueMessage.Status.IN_PROCESS)
            return cancelInProcessTasks(sq, RetrieveTask_.queueMessage, RetrieveTask.class);

        updateRetrieveTaskUpdatedTime(sq, now);
        return updateStatus(sq, QueueMessage.Status.CANCELED, now);
    }

    private void updateRetrieveTaskUpdatedTime(Subquery<QueueMessage> sq, Date now) {
        CriteriaUpdate<RetrieveTask> q = em.getCriteriaBuilder().createCriteriaUpdate(RetrieveTask.class);
        Root<RetrieveTask> retrieveTask = q.from(RetrieveTask.class);
        em.createQuery(q.where(retrieveTask.get(RetrieveTask_.queueMessage).in(sq))
                .set(retrieveTask.get(RetrieveTask_.updatedTime), now))
                .executeUpdate();
    }

    private Subquery<QueueMessage> retrieveTaskQuery(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam retrieveTaskQueryParam) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        MatchTask matchTask = new MatchTask(cb);

        CriteriaQuery<RetrieveTask> q = cb.createQuery(RetrieveTask.class);
        Subquery<QueueMessage> sq = q.subquery(QueueMessage.class);
        Root<RetrieveTask> retrieveTask = sq.from(RetrieveTask.class);
        Join<RetrieveTask, QueueMessage> queueMsg = sq.correlate(retrieveTask.join(RetrieveTask_.queueMessage));

        List<Predicate> predicates = matchTask.retrievePredicates(
                queueMsg, retrieveTask, queueTaskQueryParam, retrieveTaskQueryParam);
        if (!predicates.isEmpty())
            sq.where(predicates.toArray(new Predicate[0]));
        return sq.select(retrieveTask.get(RetrieveTask_.queueMessage));
    }

    public long cancelStgVerTasks(TaskQueryParam queueTaskQueryParam, TaskQueryParam stgVerTaskQueryParam) {
        Date now = new Date();
        Subquery<QueueMessage> sq = stgVerTaskQuery(queueTaskQueryParam, stgVerTaskQueryParam);

        if (queueTaskQueryParam.getStatus() == QueueMessage.Status.IN_PROCESS)
            return cancelInProcessTasks(sq, StorageVerificationTask_.queueMessage, StorageVerificationTask.class);

        updateStgVerTaskUpdatedTime(sq, now);
        return updateStatus(sq, QueueMessage.Status.CANCELED, now);
    }

    private void updateStgVerTaskUpdatedTime(Subquery<QueueMessage> sq, Date now) {
        CriteriaUpdate<StorageVerificationTask> q = em.getCriteriaBuilder().createCriteriaUpdate(StorageVerificationTask.class);
        Root<StorageVerificationTask> stgVerTask = q.from(StorageVerificationTask.class);
        em.createQuery(q.where(stgVerTask.get(StorageVerificationTask_.queueMessage).in(sq))
                .set(stgVerTask.get(StorageVerificationTask_.updatedTime), now))
                .executeUpdate();
    }

    private Subquery<QueueMessage> stgVerTaskQuery(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam stgVerTaskQueryParam) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        MatchTask matchTask = new MatchTask(cb);

        CriteriaQuery<StorageVerificationTask> q = cb.createQuery(StorageVerificationTask.class);
        Subquery<QueueMessage> sq = q.subquery(QueueMessage.class);
        Root<StorageVerificationTask> stgVerTask = sq.from(StorageVerificationTask.class);
        Join<StorageVerificationTask, QueueMessage> queueMsg = sq.correlate(stgVerTask.join(StorageVerificationTask_.queueMessage));

        List<Predicate> predicates = matchTask.stgVerPredicates(
                queueMsg, stgVerTask, queueTaskQueryParam, stgVerTaskQueryParam);
        if (!predicates.isEmpty())
            sq.where(predicates.toArray(new Predicate[0]));
        return sq.select(stgVerTask.get(StorageVerificationTask_.queueMessage));
    }

    public long cancelDiffTasks(TaskQueryParam queueTaskQueryParam, TaskQueryParam diffTaskQueryParam) {
        Date now = new Date();
        Subquery<QueueMessage> sq = diffTaskQuery(queueTaskQueryParam, diffTaskQueryParam);

        if (queueTaskQueryParam.getStatus() == QueueMessage.Status.IN_PROCESS)
            return cancelInProcessTasks(sq, DiffTask_.queueMessage, DiffTask.class);

        updateDiffTaskUpdatedTime(sq, now);
        return updateStatus(sq, QueueMessage.Status.CANCELED, now);
    }

    private void updateDiffTaskUpdatedTime(Subquery<QueueMessage> sq, Date now) {
        CriteriaUpdate<DiffTask> q = em.getCriteriaBuilder().createCriteriaUpdate(DiffTask.class);
        Root<DiffTask> diffTask = q.from(DiffTask.class);
        em.createQuery(q.where(diffTask.get(DiffTask_.queueMessage).in(sq))
                .set(diffTask.get(DiffTask_.updatedTime), now))
                .executeUpdate();
    }

    private Subquery<QueueMessage> diffTaskQuery(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam diffTaskQueryParam) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        MatchTask matchTask = new MatchTask(cb);

        CriteriaQuery<DiffTask> q = cb.createQuery(DiffTask.class);
        Subquery<QueueMessage> sq = q.subquery(QueueMessage.class);
        Root<DiffTask> diffTask = sq.from(DiffTask.class);
        Join<DiffTask, QueueMessage> queueMsg = sq.correlate(diffTask.join(DiffTask_.queueMessage));

        List<Predicate> predicates = matchTask.diffPredicates(
                queueMsg, diffTask, queueTaskQueryParam, diffTaskQueryParam);
        if (!predicates.isEmpty())
            sq.where(predicates.toArray(new Predicate[0]));
        return sq.select(diffTask.get(DiffTask_.queueMessage));
    }

    public Tuple findDeviceNameAndMsgPropsByMsgID(String msgID) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> tupleQuery = cb.createTupleQuery();
        Root<QueueMessage> queueMsg = tupleQuery.from(QueueMessage.class);
        tupleQuery.where(cb.equal(queueMsg.get(QueueMessage_.messageID), msgID));
        tupleQuery.multiselect(
                queueMsg.get(QueueMessage_.deviceName),
                queueMsg.get(QueueMessage_.messageProperties));
        return em.createQuery(tupleQuery).getSingleResult();
    }

    public void rescheduleTask(String msgId, String queueName, QueueMessageEvent queueEvent) {
        QueueMessage entity = findQueueMessage(msgId);
        if (entity == null)
            return;

        if (queueEvent != null)
            queueEvent.setQueueMsg(entity);

        switch (entity.getStatus()) {
            case SCHEDULED:
            case IN_PROCESS:
                cancelTask(entity);
        }
        if (queueName != null)
            entity.setQueueName(queueName);
        entity.setNumberOfFailures(0);
        entity.setErrorMessage(null);
        entity.setOutcomeMessage(null);
        rescheduleTask(entity, descriptorOf(entity.getQueueName()), 0L);
    }

    private void rescheduleTask(QueueMessage entity, QueueDescriptor descriptor, long delay) {
        try {
            ObjectMessage msg = entity.initProperties(createObjectMessage(entity.getMessageBody()));
            sendMessage(descriptor, msg, delay, entity.getPriority());
            entity.setMessageID(msg.getJMSMessageID());
            entity.setScheduledTime(new Date(System.currentTimeMillis() + delay));
            entity.setStatus(QueueMessage.Status.SCHEDULED);
            entity.setDeviceName(device.getDeviceName());
            setUpdateTime(entity);
            LOG.info("Reschedule Task[id={}] at Queue {}", entity.getMessageID(), entity.getQueueName());
        } catch (JMSException e) {
            throw toJMSRuntimeException(e);
        }
    }

    private JMSRuntimeException toJMSRuntimeException(JMSException e) {
        return new JMSRuntimeException(e.getMessage(), e.getErrorCode(), e.getCause());
    }

    public boolean deleteTask(String msgId, QueueMessageEvent queueEvent) {
        QueueMessage entity = findQueueMessage(msgId);
        if (entity == null)
            return false;

        if (queueEvent != null)
            queueEvent.setQueueMsg(entity);
        deleteTask(entity);
        return true;
    }

    private void deleteTask(QueueMessage entity) {
        if (entity.getStatus() == QueueMessage.Status.IN_PROCESS)
            messageCanceledEvent.fire(new MessageCanceled(entity.getMessageID()));

        if (entity.getExportTask() != null)
            em.remove(entity.getExportTask());
        else if (entity.getRetrieveTask() != null)
            em.remove(entity.getRetrieveTask());
        else if (entity.getDiffTask() != null)
            em.remove(entity.getDiffTask());
        else if (entity.getStorageVerificationTask() != null)
            em.remove(entity.getStorageVerificationTask());
        else
            em.remove(entity);
        LOG.info("Delete Task[id={}] from Queue {}", entity.getMessageID(), entity.getQueueName());
    }

    private CriteriaQuery<String> queueMsgQuery(
            TaskQueryParam queueTaskQueryParam, SingularAttribute<QueueMessage, String> attribute) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        MatchTask matchTask = new MatchTask(cb);
        CriteriaQuery<String> q = cb.createQuery(String.class);
        Root<QueueMessage> queueMsg = q.from(QueueMessage.class);
        List<Predicate> predicates = matchTask.queueMsgPredicates(queueMsg, queueTaskQueryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));
        return q.select(queueMsg.get(attribute));
    }

    public List<String> getQueueMsgIDs(TaskQueryParam queueTaskQueryParam, int limit) {
        return em.createQuery(queueMsgQuery(queueTaskQueryParam, QueueMessage_.messageID))
                .setMaxResults(limit)
                .getResultList();
    }

    public List<Tuple> listQueueMsgIDAndMsgProps(TaskQueryParam queueTaskQueryParam, int limit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        MatchTask matchTask = new MatchTask(cb);
        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        Root<QueueMessage> queueMsg = q.from(QueueMessage.class);
        List<Predicate> predicates = matchTask.queueMsgPredicates(queueMsg, queueTaskQueryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));
        return em.createQuery(
                q.multiselect(
                    queueMsg.get(QueueMessage_.messageID),
                    queueMsg.get(QueueMessage_.messageProperties)))
                .setMaxResults(limit)
                .getResultList();
    }

    public List<String> listDistinctDeviceNames(TaskQueryParam queueTaskQueryParam) {
        return em.createQuery(queueMsgQuery(queueTaskQueryParam, QueueMessage_.deviceName)
                .distinct(true))
                .getResultList();
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
        return device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class).getQueueDescriptorNotNull(queueName);
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

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Iterator<QueueMessage> listQueueMessages(
            TaskQueryParam queueTaskQueryParam, int offset, int limit) {
        return listQueueMessages(queueTaskQueryParam, queryFetchSize(), offset, limit);
    }

    private Iterator<QueueMessage> listQueueMessages(
            TaskQueryParam queueTaskQueryParam, int fetchSize, int offset, int limit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        MatchTask matchTask = new MatchTask(cb);
        TypedQuery<QueueMessage> query = em.createQuery(select(cb, matchTask, queueTaskQueryParam));
        if (fetchSize > 0)
            query.setHint(QueryHints.FETCH_SIZE, fetchSize);
        if (offset > 0)
            query.setFirstResult(offset);
        if (limit > 0)
            query.setMaxResults(limit);
        return query.getResultStream().iterator();
    }

    public long countTasks(TaskQueryParam queueTaskQueryParam) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        MatchTask matchTask = new MatchTask(cb);

        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        Root<QueueMessage> queueMsg = q.from(QueueMessage.class);
        List<Predicate> predicates = matchTask.queueMsgPredicates(
                queueMsg,
                queueTaskQueryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));

        return QueryBuilder.unbox(em.createQuery(q.select(cb.count(queueMsg))).getSingleResult(), 0L);
    }

    private int queryFetchSize() {
        return device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class).getQueryFetchSize();
    }

    private CriteriaQuery<QueueMessage> select(CriteriaBuilder cb,
                                               MatchTask matchTask, TaskQueryParam queueTaskQueryParam) {
        CriteriaQuery<QueueMessage> q = cb.createQuery(QueueMessage.class);
        Root<QueueMessage> queueMsg = q.from(QueueMessage.class);

        List<Predicate> predicates = matchTask.queueMsgPredicates(
                queueMsg,
                queueTaskQueryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));

        if (queueTaskQueryParam.getOrderBy() != null)
            q.orderBy(matchTask.queueMessageOrder(queueTaskQueryParam.getOrderBy(), queueMsg));

        return q.select(queueMsg);
    }

    public int deleteTasks(
            TaskQueryParam queueTaskQueryParam, int deleteTasksFetchSize) {
        Iterator<QueueMessage> queueMsgs = listQueueMessages(queueTaskQueryParam, 0, 0, deleteTasksFetchSize);
        int count = 0;
        while (queueMsgs.hasNext()) {
            deleteTask(queueMsgs.next());
            count++;
        }
        return count;
    }
}