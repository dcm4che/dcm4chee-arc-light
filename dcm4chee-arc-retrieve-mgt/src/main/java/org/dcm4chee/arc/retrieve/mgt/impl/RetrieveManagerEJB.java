/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 *  The contents of this file are subject to the Mozilla Public License Version
 *  1.1 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  http://www.mozilla.org/MPL/
 *
 *  Software distributed under the License is distributed on an "AS IS" basis,
 *  WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 *  for the specific language governing rights and limitations under the
 *  License.
 *
 *  The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 *  Java(TM), hosted at https://github.com/dcm4che.
 *
 *  The Initial Developer of the Original Code is
 *  J4Care.
 *  Portions created by the Initial Developer are Copyright (C) 2015-2017
 *  the Initial Developer. All Rights Reserved.
 *
 *  Contributor(s):
 *  See @authors listed below
 *
 *  Alternatively, the contents of this file may be used under the terms of
 *  either the GNU General Public License Version 2 or later (the "GPL"), or
 *  the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 *  in which case the provisions of the GPL or the LGPL are applicable instead
 *  of those above. If you wish to allow use of your version of this file only
 *  under the terms of either the GPL or the LGPL, and not to allow others to
 *  use your version of this file under the terms of the MPL, indicate your
 *  decision by deleting the provisions above and replace them with the notice
 *  and other provisions required by the GPL or the LGPL. If you do not delete
 *  the provisions above, a recipient may use your version of this file under
 *  the terms of any one of the MPL, the GPL or the LGPL.
 *
 */

package org.dcm4chee.arc.retrieve.mgt.impl;

import com.querydsl.core.types.Predicate;
import com.querydsl.jpa.hibernate.HibernateDeleteClause;
import com.querydsl.jpa.hibernate.HibernateQuery;
import org.dcm4che3.data.*;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.entity.QQueueMessage;
import org.dcm4chee.arc.entity.QRetrieveTask;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.entity.RetrieveTask;
import org.dcm4chee.arc.qmgt.*;
import org.dcm4chee.arc.retrieve.ExternalRetrieveContext;
import org.dcm4chee.arc.retrieve.mgt.RetrieveManager;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2017
 */
@Stateless
public class RetrieveManagerEJB {
    private static final Logger LOG = LoggerFactory.getLogger(RetrieveManagerEJB.class);

    @PersistenceContext(unitName = "dcm4chee-arc")
    private EntityManager em;

    @Inject
    private QueueManager queueManager;

    @Inject
    private Device device;

    public void scheduleRetrieveTask(Device device, int priority, ExternalRetrieveContext ctx)
            throws QueueSizeLimitExceededException {
        try {
            ObjectMessage msg = queueManager.createObjectMessage(ctx.getKeys());
            msg.setStringProperty("LocalAET", ctx.getLocalAET());
            msg.setStringProperty("RemoteAET", ctx.getRemoteAET());
            msg.setIntProperty("Priority", priority);
            msg.setStringProperty("DestinationAET", ctx.getDestinationAET());
            msg.setStringProperty("StudyInstanceUID", ctx.getKeys().getString(Tag.StudyInstanceUID));
            msg.setStringProperty("RequesterUserID", ctx.getRequesterUserID());
            msg.setStringProperty("RequesterHostName", ctx.getRequesterHostName());
            msg.setStringProperty("RequestURI", ctx.getRequestURI());
            QueueMessage queueMessage = queueManager.scheduleMessage(RetrieveManager.QUEUE_NAME, msg,
                    Message.DEFAULT_PRIORITY);
            createRetrieveTask(device, ctx, queueMessage);
        } catch (JMSException e) {
            throw QueueMessage.toJMSRuntimeException(e);
        }
    }

    private void createRetrieveTask(Device device, ExternalRetrieveContext ctx, QueueMessage queueMessage) {
        RetrieveTask task = new RetrieveTask();
        task.setLocalAET(ctx.getLocalAET());
        task.setRemoteAET(ctx.getRemoteAET());
        task.setDestinationAET(ctx.getDestinationAET());
        task.setStudyInstanceUID(ctx.getStudyInstanceUID());
        task.setSeriesInstanceUID(ctx.getSeriesInstanceUID());
        task.setSOPInstanceUID(ctx.getSOPInstanceUID());
        task.setQueueMessage(queueMessage);
        em.persist(task);
    }

    public void updateRetrieveTask(QueueMessage queueMessage, Attributes cmd) {
        em.createNamedQuery(RetrieveTask.UPDATE_BY_QUEUE_MESSAGE)
                .setParameter(1, queueMessage)
                .setParameter(2, cmd.getInt(Tag.NumberOfRemainingSuboperations, 0))
                .setParameter(3, cmd.getInt(Tag.NumberOfCompletedSuboperations, 0))
                .setParameter(4, cmd.getInt(Tag.NumberOfFailedSuboperations, 0))
                .setParameter(5, cmd.getInt(Tag.NumberOfWarningSuboperations, 0))
                .setParameter(6, cmd.getInt(Tag.Status, 0))
                .setParameter(7, cmd.getString(Tag.ErrorComment, null))
                .executeUpdate();
    }

    public void resetRetrieveTask(QueueMessage queueMessage) {
        em.createNamedQuery(RetrieveTask.UPDATE_BY_QUEUE_MESSAGE)
                .setParameter(1, queueMessage)
                .setParameter(2, -1)
                .setParameter(3, 0)
                .setParameter(4, 0)
                .setParameter(5, 0)
                .setParameter(6, -1)
                .setParameter(7, null)
                .executeUpdate();
    }

    public List<RetrieveTask> search(Predicate matchQueueMessage, Predicate matchRetrieveTask, int offset, int limit) {
        HibernateQuery<RetrieveTask> extRetrieveQuery = createQuery(matchQueueMessage, matchRetrieveTask);
        if (limit > 0)
            extRetrieveQuery.limit(limit);
        if (offset > 0)
            extRetrieveQuery.offset(offset);
        extRetrieveQuery.orderBy(QRetrieveTask.retrieveTask.updatedTime.desc());
        return extRetrieveQuery.fetch();
    }

    public long countRetrieveTasks(Predicate matchQueueMessage, Predicate matchRetrieveTask) {
        return createQuery(matchQueueMessage, matchRetrieveTask)
                .fetchCount();
    }

    private HibernateQuery<RetrieveTask> createQuery(
            Predicate matchQueueMessage, Predicate matchRetrieveTask) {
        HibernateQuery<QueueMessage> queueMsgQuery = new HibernateQuery<QueueMessage>(em.unwrap(Session.class))
                .from(QQueueMessage.queueMessage)
                .where(matchQueueMessage);
        return new HibernateQuery<RetrieveTask>(em.unwrap(Session.class))
                .from(QRetrieveTask.retrieveTask)
                .where(matchRetrieveTask, QRetrieveTask.retrieveTask.queueMessage.in(queueMsgQuery));
    }

    public List<Long> getRetrieveTaskPks(Predicate matchQueueMessage, Predicate matchRetrieveTask, int limit) {
        HibernateQuery<Long> retrieveTaskPkQuery = createQuery(matchQueueMessage, matchRetrieveTask)
                .select(QRetrieveTask.retrieveTask.pk);
        if (limit > 0)
            retrieveTaskPkQuery.limit(limit);
        return retrieveTaskPkQuery.fetch();
    }

    public boolean deleteRetrieveTask(Long pk) {
        RetrieveTask task = em.find(RetrieveTask.class, pk);
        if (task == null)
            return false;

        em.remove(task);
        LOG.info("Delete {}", task);
        return true;
    }

    public boolean cancelRetrieveTask(Long pk) throws IllegalTaskStateException {
        RetrieveTask task = em.find(RetrieveTask.class, pk);
        if (task == null)
            return false;

        QueueMessage queueMessage = task.getQueueMessage();
        if (queueMessage == null)
            throw new IllegalTaskStateException("Cannot cancel Task with status: 'TO SCHEDULE'");

        queueManager.cancelTask(queueMessage.getMessageID());
        LOG.info("Cancel {}", task);
        return true;
    }
    
    public long cancelRetrieveTasks(Predicate matchQueueMessage, Predicate matchRetrieveTask, QueueMessage.Status prev)
            throws IllegalTaskStateException {
        return queueManager.cancelRetrieveTasks(matchQueueMessage, matchRetrieveTask, prev);
    }

    public boolean rescheduleRetrieveTask(Long pk)
            throws IllegalTaskStateException, DifferentDeviceException {
        RetrieveTask task = em.find(RetrieveTask.class, pk);
        if (task == null)
            return false;

        QueueMessage queueMessage = task.getQueueMessage();
        if (queueMessage != null)
            queueManager.rescheduleTask(queueMessage, RetrieveManager.QUEUE_NAME);

        LOG.info("Reschedule {}", task);
        return true;
    }

    public int deleteTasks(Predicate matchQueueMessage, Predicate matchRetrieveTask) {
        int count = 0;
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        int deleteTaskFetchSize = arcDev.getQueueTasksFetchSize();
        HibernateQuery<QueueMessage> queueMsgQuery = new HibernateQuery<QueueMessage>(em.unwrap(Session.class))
                .from(QQueueMessage.queueMessage)
                .where(matchQueueMessage);
        List<Long> referencedQueueMsgs;
        do {
            referencedQueueMsgs = new HibernateQuery<RetrieveTask>(em.unwrap(Session.class))
                    .select(QRetrieveTask.retrieveTask.queueMessage.pk)
                    .from(QRetrieveTask.retrieveTask)
                    .where(matchRetrieveTask, QRetrieveTask.retrieveTask.queueMessage.in(queueMsgQuery))
                    .limit(deleteTaskFetchSize).fetch();

            new HibernateDeleteClause(em.unwrap(Session.class), QRetrieveTask.retrieveTask)
                    .where(matchRetrieveTask, QRetrieveTask.retrieveTask.queueMessage.pk.in(referencedQueueMsgs))
                    .execute();

            count += (int) new HibernateDeleteClause(em.unwrap(Session.class), QQueueMessage.queueMessage)
                    .where(matchQueueMessage, QQueueMessage.queueMessage.pk.in(referencedQueueMsgs)).execute();
        } while (referencedQueueMsgs.size() >= deleteTaskFetchSize);
        return count;
    }
}
