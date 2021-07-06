/*
 * **** BEGIN LICENSE BLOCK *****
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
 * **** END LICENSE BLOCK *****
 *
 */

package org.dcm4chee.arc.qmgt.impl;

import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.QueueDescriptor;
import org.dcm4chee.arc.entity.Task;
import org.dcm4chee.arc.qmgt.Outcome;
import org.dcm4chee.arc.qmgt.TaskManager;
import org.dcm4chee.arc.query.util.MatchTask;
import org.dcm4chee.arc.query.util.QueryBuilder;
import org.dcm4chee.arc.query.util.TaskQueryParam1;
import org.hibernate.annotations.QueryHints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.*;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Jun 2021
 */
@Stateless
public class TaskManagerEJB {

    private static final Logger LOG = LoggerFactory.getLogger(TaskManagerEJB.class);

    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    @Inject
    private Device device;

    public List<Long> findTasksToProcess(String queueName, int maxResults) {
        return em.createNamedQuery(Task.FIND_SCHEDULED_BY_DEVICE_AND_QUEUE_NAME_AND_STATUS, Long.class)
                .setParameter(1, device.getDeviceName())
                .setParameter(2, queueName)
                .setParameter(3, Task.Status.SCHEDULED)
                .setMaxResults(maxResults)
                .getResultList();
    }

    public Task onProcessingStart(Long pk) {
        Task entity = em.find(Task.class, pk);
        if (entity == null) {
            LOG.info("Suppress processing of already deleted Task[pk={}]", pk);
        } else if (entity.getStatus() != Task.Status.SCHEDULED) {
            LOG.info("Suppress processing {}", entity);
        } else {
            LOG.info("Start processing {}", entity);
            entity.setProcessingStartTime(new Date());
            entity.setStatus(Task.Status.IN_PROCESS);
            return entity;
        }
        return null;
    }

    public Task onProcessingSuccessful(Long pk, Outcome outcome) {
        Task entity = em.find(Task.class, pk);
        if (entity == null) {
            LOG.info("Finished processing of Task[pk={}]", pk);
            return null;
        }
        Task.Status status = outcome.getStatus();
        String queueName = entity.getQueueName();
        entity.setProcessingEndTime(new Date());
        entity.setOutcomeMessage(outcome.getDescription());
        entity.setStatus(status);
        QueueDescriptor descriptor = descriptorOf(queueName);
        if (status == Task.Status.COMPLETED
                || status == Task.Status.WARNING && !descriptor.isRetryOnWarning()) {
            LOG.info("Finished processing of {}", entity);
            return entity;
        }
        long delay = descriptor.getRetryDelayInSeconds(entity.incrementNumberOfFailures());
        if (delay >= 0) {
            LOG.info("Failed processing of {} - retry", entity);
            entity.setStatus(Task.Status.SCHEDULED);
            rescheduleTask(entity, new Date(System.currentTimeMillis() + delay * 1000L));
            return entity;
        }
        LOG.warn("Failed processing of {}", entity);
        entity.setStatus(status);
        return entity;
    }

    public Task onProcessingFailed(Long msgId, Throwable e) {
        Task entity = em.find(Task.class, msgId);
        if (entity == null) {
            LOG.warn("Failed processing of Task[pk={}]:\n", msgId, e);
            return null;
        }

        entity.setErrorMessage(e.getMessage());
        entity.setProcessingEndTime(new Date());
        QueueDescriptor descriptor = descriptorOf(entity.getQueueName());
        long delay = descriptor.getRetryDelayInSeconds(entity.incrementNumberOfFailures());
        if (delay < 0) {
            LOG.warn("Failed processing of {}:\n", entity, e);
            entity.setStatus(Task.Status.FAILED);
        } else {
            LOG.info("Failed processing of {} - retry:\n", entity, e);
            rescheduleTask(entity, new Date(System.currentTimeMillis() + delay * 1000L));
        }
        return entity;
    }

    private void rescheduleTask(Task entity, Date scheduledTime) {
        entity.setScheduledTime(scheduledTime);
        entity.setStatus(Task.Status.SCHEDULED);
        entity.setDeviceName(device.getDeviceName());
        LOG.info("Reschedule {}", entity);
    }

    private QueueDescriptor descriptorOf(String queueName) {
        return device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class).getQueueDescriptorNotNull(queueName);
    }

    private int fetchSize() {
        return device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class).getQueryFetchSize();
    }

    public void scheduleTask(Task task) {
        em.persist(task);
        LOG.info("Schedule {}", task);
    }
    public void forEachTask(TaskQueryParam1 taskQueryParam, int offset, int limit, Consumer<Task> action) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        QueryBuilder queryBuilder = new QueryBuilder(cb);
        CriteriaQuery<Task> q = cb.createQuery(Task.class);
        Root<Task> task = q.from(Task.class);
        List<Predicate> predicates = queryBuilder.taskPredicates(task, taskQueryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));
        if (taskQueryParam.getOrderBy() != null)
            q.orderBy(queryBuilder.orderTasks(task, taskQueryParam.getOrderBy()));
        TypedQuery<Task> query = em.createQuery(q);
        if (offset > 0)
            query.setFirstResult(offset);
        if (limit > 0)
            query.setMaxResults(limit);
        try (Stream<Task> resultStream = query.setHint(QueryHints.FETCH_SIZE, fetchSize()).getResultStream()) {
            resultStream.forEach(action);
        }
    }

    public long countTasks(TaskQueryParam1 taskQueryParam) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        QueryBuilder queryBuilder = new QueryBuilder(cb);
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        Root<Task> task = q.from(Task.class);
        List<Predicate> predicates = queryBuilder.taskPredicates(task, taskQueryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));
        return em.createQuery(q.select(cb.count(task))).getSingleResult();
    }
}
