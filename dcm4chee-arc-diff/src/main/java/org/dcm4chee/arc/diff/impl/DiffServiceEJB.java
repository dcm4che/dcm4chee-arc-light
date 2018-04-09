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
 * Portions created by the Initial Developer are Copyright (C) 2015-2018
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

package org.dcm4chee.arc.diff.impl;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.jpa.hibernate.HibernateQuery;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.diff.*;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.qmgt.QueueManager;
import org.dcm4chee.arc.qmgt.QueueSizeLimitExceededException;
import org.hibernate.Session;
import org.hibernate.StatelessSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Mar 2018
 */
@Stateless
public class DiffServiceEJB {

    static final Logger LOG = LoggerFactory.getLogger(DiffServiceEJB.class);

    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    @Inject
    private QueueManager queueManager;

    private static final Expression<?>[] SELECT = {
            QQueueMessage.queueMessage.processingStartTime.min(),
            QQueueMessage.queueMessage.processingStartTime.max(),
            QQueueMessage.queueMessage.processingEndTime.min(),
            QQueueMessage.queueMessage.processingEndTime.max(),
            QQueueMessage.queueMessage.scheduledTime.min(),
            QQueueMessage.queueMessage.scheduledTime.max(),
            QDiffTask.diffTask.createdTime.min(),
            QDiffTask.diffTask.createdTime.max(),
            QDiffTask.diffTask.updatedTime.min(),
            QDiffTask.diffTask.updatedTime.max(),
            QDiffTask.diffTask.matches.sum(),
            QDiffTask.diffTask.missing.sum(),
            QDiffTask.diffTask.different.sum(),
            QQueueMessage.queueMessage.batchID
    };

    @Inject
    private Device device;

    public void scheduleDiffTask(DiffContext ctx) throws QueueSizeLimitExceededException {
        try {
            ObjectMessage msg = queueManager.createObjectMessage(0);
            msg.setStringProperty("LocalAET", ctx.getLocalAE().getAETitle());
            msg.setStringProperty("PrimaryAET", ctx.getPrimaryAE().getAETitle());
            msg.setStringProperty("SecondaryAET", ctx.getSecondaryAE().getAETitle());
            msg.setIntProperty("Priority", ctx.priority());
            msg.setStringProperty("QueryString", ctx.getQueryString());
            if (ctx.getHttpServletRequestInfo() != null)
                ctx.getHttpServletRequestInfo().copyTo(msg);
            QueueMessage queueMessage = queueManager.scheduleMessage(DiffService.QUEUE_NAME, msg,
                    Message.DEFAULT_PRIORITY, ctx.getBatchID());
            createDiffTask(ctx, queueMessage);
        } catch (JMSException e) {
            throw QueueMessage.toJMSRuntimeException(e);
        }
    }

    private void createDiffTask(DiffContext ctx, QueueMessage queueMessage) {
        DiffTask task = new DiffTask();
        task.setLocalAET(ctx.getLocalAE().getAETitle());
        task.setPrimaryAET(ctx.getPrimaryAE().getAETitle());
        task.setSecondaryAET(ctx.getSecondaryAE().getAETitle());
        task.setQueryString(ctx.getQueryString());
        task.setCheckMissing(ctx.isCheckMissing());
        task.setCheckDifferent(ctx.isCheckDifferent());
        task.setCompareFields(ctx.getCompareFields());
        task.setQueueMessage(queueMessage);
        em.persist(task);
    }

    public void resetDiffTask(DiffTask diffTask) {
        diffTask = em.find(DiffTask.class, diffTask.getPk());
        diffTask.reset();
        diffTask.getDiffTaskAttributes().forEach(entity -> em.remove(entity));
    }

    public void addDiffTaskAttributes(DiffTask diffTask, Attributes attrs) {
        DiffTaskAttributes entity = new DiffTaskAttributes();
        entity.setDiffTask(diffTask);
        entity.setAttributes(attrs);
        em.persist(entity);
    }

    public void updateDiffTask(DiffTask diffTask, DiffSCU diffSCU) {
        diffTask = em.find(DiffTask.class, diffTask.getPk());
        diffTask.setMatches(diffSCU.matches());
        diffTask.setMissing(diffSCU.missing());
        diffTask.setDifferent(diffSCU.different());
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public DiffTaskQuery listDiffTasks(
            Predicate matchQueueMessage, Predicate matchDiffTask, OrderSpecifier<Date> order, int offset, int limit) {
        return new DiffTaskQueryImpl(
                openStatelessSession(), queryFetchSize(), matchQueueMessage, matchDiffTask, order, offset, limit);
    }

    public long countDiffTasks(Predicate matchQueueMessage, Predicate matchDiffTask) {
        return createQuery(matchQueueMessage, matchDiffTask).fetchCount();
    }

    private HibernateQuery<DiffTask> createQuery(Predicate matchQueueMessage, Predicate matchDiffTask) {
        HibernateQuery<QueueMessage> queueMsgQuery = new HibernateQuery<QueueMessage>(em.unwrap(Session.class))
                .from(QQueueMessage.queueMessage)
                .where(matchQueueMessage);
        return new HibernateQuery<DiffTask>(em.unwrap(Session.class))
                .from(QDiffTask.diffTask)
                .where(matchDiffTask, QDiffTask.diffTask.queueMessage.in(queueMsgQuery));
    }

    public DiffTask getDiffTask(long taskPK) {
        return em.find(DiffTask.class, taskPK);
    }

    public List<AttributesBlob> getDiffTaskAttributes(DiffTask diffTask, int offset, int limit) {
        return em.createNamedQuery(DiffTaskAttributes.FIND_BY_DIFF_TASK, AttributesBlob.class)
                .setParameter(1, diffTask)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }

    public List<DiffBatch> listDiffBatches(Predicate matchQueueBatch, Predicate matchDiffBatch, OrderSpecifier<Date> order,
                                           int offset, int limit) {
        HibernateQuery<DiffTask> diffTaskQuery = createQuery(matchQueueBatch, matchDiffBatch);
        if (limit > 0)
            diffTaskQuery.limit(limit);
        if (offset > 0)
            diffTaskQuery.offset(offset);

        List<Tuple> batches = diffTaskQuery.select(SELECT)
                                .groupBy(QQueueMessage.queueMessage.batchID)
                                .orderBy(order)
                                .fetch();

        List<DiffBatch> diffBatches = new ArrayList<>();
        for (Tuple batch : batches) {
            DiffBatch diffBatch = new DiffBatch();
            String batchID = batch.get(QQueueMessage.queueMessage.batchID);
            diffBatch.setBatchID(batchID);

            diffBatch.setCreatedTimeRange(
                    batch.get(QDiffTask.diffTask.createdTime.min()),
                    batch.get(QDiffTask.diffTask.createdTime.max()));
            diffBatch.setUpdatedTimeRange(
                    batch.get(QDiffTask.diffTask.updatedTime.min()),
                    batch.get(QDiffTask.diffTask.updatedTime.max()));
            diffBatch.setScheduledTimeRange(
                    batch.get(QQueueMessage.queueMessage.scheduledTime.min()),
                    batch.get(QQueueMessage.queueMessage.scheduledTime.max()));
            diffBatch.setProcessingStartTimeRange(
                    batch.get(QQueueMessage.queueMessage.processingStartTime.min()),
                    batch.get(QQueueMessage.queueMessage.processingStartTime.max()));
            diffBatch.setProcessingEndTimeRange(
                    batch.get(QQueueMessage.queueMessage.processingEndTime.min()),
                    batch.get(QQueueMessage.queueMessage.processingEndTime.max()));
            diffBatch.setMatches(batch.get(QDiffTask.diffTask.matches.sum()));
            diffBatch.setMissing(batch.get(QDiffTask.diffTask.missing.sum()));
            diffBatch.setDifferent(batch.get(QDiffTask.diffTask.different.sum()));

            diffBatch.setDeviceNames(
                    batchIDQuery(batchID)
                    .select(QQueueMessage.queueMessage.deviceName)
                    .distinct()
                    .fetch()
                    .stream()
                    .sorted()
                    .toArray(String[]::new));
            diffBatch.setLocalAETs(
                    batchIDQuery(batchID)
                            .select(QDiffTask.diffTask.localAET)
                            .distinct()
                            .fetch()
                            .stream()
                            .sorted()
                            .toArray(String[]::new));
            diffBatch.setPrimaryAETs(
                    batchIDQuery(batchID)
                            .select(QDiffTask.diffTask.primaryAET)
                            .distinct()
                            .fetch()
                            .stream()
                            .sorted()
                            .toArray(String[]::new));
            diffBatch.setSecondaryAETs(
                    batchIDQuery(batchID)
                            .select(QDiffTask.diffTask.secondaryAET)
                            .distinct()
                            .fetch()
                            .stream()
                            .sorted()
                            .toArray(String[]::new));
            diffBatch.setComparefields(
                    batchIDQuery(batchID)
                            .select(QDiffTask.diffTask.compareFields)
                            .distinct()
                            .fetch());
            diffBatch.setCheckMissing(
                    batchIDQuery(batchID)
                            .select(QDiffTask.diffTask.checkMissing)
                            .distinct()
                            .fetch());
            diffBatch.setCheckDifferent(
                    batchIDQuery(batchID)
                            .select(QDiffTask.diffTask.checkDifferent)
                            .distinct()
                            .fetch());

            diffBatch.setCompleted(
                    batchIDQuery(batchID)
                            .where(QQueueMessage.queueMessage.status.eq(QueueMessage.Status.COMPLETED))
                            .fetchCount());
            diffBatch.setCanceled(
                    batchIDQuery(batchID)
                            .where(QQueueMessage.queueMessage.status.eq(QueueMessage.Status.CANCELED))
                            .fetchCount());
            diffBatch.setWarning(
                    batchIDQuery(batchID)
                            .where(QQueueMessage.queueMessage.status.eq(QueueMessage.Status.WARNING))
                            .fetchCount());
            diffBatch.setFailed(
                    batchIDQuery(batchID)
                            .where(QQueueMessage.queueMessage.status.eq(QueueMessage.Status.FAILED))
                            .fetchCount());
            diffBatch.setScheduled(
                    batchIDQuery(batchID)
                            .where(QQueueMessage.queueMessage.status.eq(QueueMessage.Status.SCHEDULED))
                            .fetchCount());
            diffBatch.setInProcess(
                    batchIDQuery(batchID)
                            .where(QQueueMessage.queueMessage.status.eq(QueueMessage.Status.IN_PROCESS))
                            .fetchCount());

            diffBatches.add(diffBatch);
        }

        return diffBatches;
    }

    private HibernateQuery<DiffTask> batchIDQuery(String batchID) {
        return new HibernateQuery<DiffTask>(em.unwrap(Session.class))
                .from(QDiffTask.diffTask)
                .leftJoin(QDiffTask.diffTask.queueMessage, QQueueMessage.queueMessage)
                .where(QQueueMessage.queueMessage.batchID.eq(batchID));
    }

    private StatelessSession openStatelessSession() {
        return em.unwrap(Session.class).getSessionFactory().openStatelessSession();
    }

    private int queryFetchSize() {
        return device.getDeviceExtension(ArchiveDeviceExtension.class).getQueryFetchSize();
    }
}
