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

package org.dcm4chee.arc.retrieve.mgt.impl;

import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.query.util.MatchTask;
import org.dcm4chee.arc.query.util.TaskQueryParam;
import org.dcm4chee.arc.retrieve.mgt.RetrieveTaskQuery;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.*;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Mar 2018
 */
class RetrieveTaskQueryImpl implements RetrieveTaskQuery {
    private Join<RetrieveTask, QueueMessage> queueMsg;
    private Root<RetrieveTask> retrieveTask;

    private final MatchTask matchTask;
    private final TaskQueryParam queueTaskQueryParam;
    private final TaskQueryParam retrieveTaskQueryParam;
    private final EntityManager em;
    private final CriteriaBuilder cb;

    public RetrieveTaskQueryImpl(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam retrieveTaskQueryParam, EntityManager em) {
        this.em = em;
        this.cb = em.getCriteriaBuilder();
        this.matchTask = new MatchTask(cb);
        this.queueTaskQueryParam = queueTaskQueryParam;
        this.retrieveTaskQueryParam = retrieveTaskQueryParam;
    }

    @Override
    public void beginTransaction() {}

    private <T> CriteriaQuery<T> orderBy(CriteriaQuery<T> q) {
        if (retrieveTaskQueryParam.getOrderBy() != null)
            q.orderBy(matchTask.retrieveTaskOrder(retrieveTaskQueryParam.getOrderBy(), retrieveTask));
        return q;
    }

    private <T> CriteriaQuery<T> restrict(CriteriaQuery<T> q, Join<RetrieveTask, QueueMessage> queueMsg,
                                          Root<RetrieveTask> retrieveTask) {
        List<Predicate> predicates = matchTask.retrievePredicates(
                queueMsg,
                retrieveTask,
                queueTaskQueryParam,
                retrieveTaskQueryParam);
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));
        return q;
    }

    @Override
    public List<String> executeQuery(int limit) {
        TypedQuery<String> query = em.createQuery(referencedQueueMsgs());
        if (limit > 0)
            query.setMaxResults(limit);
        return query.getResultList();
    }
    
    private CriteriaQuery<String> referencedQueueMsgs() {
        CriteriaQuery<String> q = cb.createQuery(String.class);
        retrieveTask = q.from(RetrieveTask.class);
        queueMsg = retrieveTask.join(RetrieveTask_.queueMessage);
        return orderBy(restrict(q, queueMsg, retrieveTask))
                .multiselect(queueMsg.get(QueueMessage_.messageID));
    }

    @Override
    public void close() {}
}
