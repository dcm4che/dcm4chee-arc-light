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

import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.qmgt.QueueMessageQuery;
import org.hibernate.annotations.QueryHints;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.*;
import java.util.Iterator;
import java.util.stream.Stream;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since June 2018
 */

class QueueMessageQueryImpl implements QueueMessageQuery {
    private Root<QueueMessage> queueMsg;

    private Stream<QueueMessage> resultStream;
    private Iterator<QueueMessage> results;
    private int offset;
    private int limit;
    private int fetchSize;
    private Expression<Boolean> matchQueueMessage;
    private Order order;

    protected final EntityManager em;
    protected final CriteriaBuilder cb;

    public QueueMessageQueryImpl(EntityManager em, Expression<Boolean> matchQueueMessage, Order order) {
      //  this.session = session;
        this.em = em;
        this.cb = em.getCriteriaBuilder();
        this.matchQueueMessage = matchQueueMessage;
        this.order = order;
    }

    @Override
    public void close() {}

    @Override
    public void beginTransaction() {}

    @Override
    public void executeQuery(int fetchSize, int offset, int limit) {
        this.fetchSize = fetchSize;
        this.offset = offset;
        this.limit = limit;
        close(resultStream);
        TypedQuery<QueueMessage> query = em.createQuery(select())
                .setHint(QueryHints.FETCH_SIZE, fetchSize);
        if (offset > 0)
            query.setFirstResult(offset);
        if (limit > 0)
            query.setMaxResults(limit);
        resultStream = query.getResultStream();
        results = resultStream.iterator();
    }


    private CriteriaQuery<QueueMessage> select() {
        CriteriaQuery<QueueMessage> q = cb.createQuery(QueueMessage.class);
        this.queueMsg = q.from(QueueMessage.class);
        q = q.select(queueMsg);
//        q.where(cb.equal(queueMsg.get(QueueMessage_.queueName), "Export4"));
//        q.orderBy(cb.desc(queueMsg.get(QueueMessage_.updatedTime)));
//        if (matchQueueMessage != null)
//            q.where(matchQueueMessage);
//        if (order != null)
//            q.orderBy(order);
        return q;
    }

    private void close(Stream<QueueMessage> resultStream) {
        if (resultStream != null)
            resultStream.close();
    }

    @Override
    public boolean hasMoreMatches() {
        return results.hasNext();
    }

    @Override
    public QueueMessage nextMatch() {
        return results.next();
    }
}
