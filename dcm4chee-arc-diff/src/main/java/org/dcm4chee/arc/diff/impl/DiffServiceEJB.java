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

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.jpa.hibernate.HibernateQuery;
import org.dcm4che3.data.Attributes;
import org.dcm4chee.arc.diff.DiffContext;
import org.dcm4chee.arc.diff.DiffSCU;
import org.dcm4chee.arc.diff.DiffService;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.qmgt.QueueManager;
import org.dcm4chee.arc.qmgt.QueueSizeLimitExceededException;
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

    public List<DiffTask> listDiffTasks(
            Predicate matchQueueMessage, Predicate matchDiffTask, int offset, int limit, String orderby) {
        HibernateQuery<DiffTask> diffTaskQuery = createQuery(matchQueueMessage, matchDiffTask);
        if (limit > 0)
            diffTaskQuery.limit(limit);
        if (offset > 0)
            diffTaskQuery.offset(offset);
        OrderSpecifier<Date> orderSpecifier = orderby == null || orderby.equals("-updatedTime")
                                                ? QDiffTask.diffTask.updatedTime.desc()
                                                : orderby.equals("updatedTime")
                                                    ? QDiffTask.diffTask.updatedTime.asc()
                                                    : orderby.equals("createdTime")
                                                        ? QDiffTask.diffTask.createdTime.asc()
                                                        : QDiffTask.diffTask.createdTime.desc();
        diffTaskQuery.orderBy(orderSpecifier);
        return diffTaskQuery.fetch();
    }

    public long countDiffTasks(Predicate matchQueueMessage, Predicate matchDiffTask) {
        return createQuery(matchQueueMessage, matchDiffTask).fetchCount();
    }

    private HibernateQuery<DiffTask> createQuery(
            Predicate matchQueueMessage, Predicate matchDiffTask) {
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
}
