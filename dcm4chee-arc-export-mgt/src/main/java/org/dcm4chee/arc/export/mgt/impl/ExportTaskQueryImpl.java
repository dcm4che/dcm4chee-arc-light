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

package org.dcm4chee.arc.export.mgt.impl;

import com.mysema.commons.lang.CloseableIterator;
import com.querydsl.core.types.Predicate;
import com.querydsl.jpa.hibernate.HibernateQuery;
import org.dcm4che3.util.SafeClose;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.export.mgt.ExportTaskOrder;
import org.dcm4chee.arc.export.mgt.ExportTaskQuery;
import org.hibernate.StatelessSession;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Mar 2018
 */
class ExportTaskQueryImpl implements ExportTaskQuery {
    private static final Logger LOG = LoggerFactory.getLogger(ExportTaskQueryImpl.class);
    private final StatelessSession session;
    private final HibernateQuery<ExportTask> query;
    private Transaction transaction;
    private CloseableIterator<ExportTask> iterate;

    public ExportTaskQueryImpl(StatelessSession session, int fetchSize,
                               Predicate matchQueueMessage,
                               Predicate matchExportTask,
                               ExportTaskOrder order,
                               int offset, int limit) {
        this.session = session;
        HibernateQuery<QueueMessage> queueMsgQuery = new HibernateQuery<QueueMessage>(session)
                .from(QQueueMessage.queueMessage)
                .where(matchQueueMessage);

        query = new HibernateQuery<ExportTask>(session)
                .from(QExportTask.exportTask)
                .leftJoin(QExportTask.exportTask.queueMessage, QQueueMessage.queueMessage)
                .where(matchExportTask, QExportTask.exportTask.queueMessage.in(queueMsgQuery));
        if (limit > 0)
            query.limit(limit);
        if (offset > 0)
            query.offset(offset);
        if (order != null)
            query.orderBy(order.specifier);
        query.setFetchSize(fetchSize);
    }


    @Override
    public void close() {
        SafeClose.close(iterate);
        if (transaction != null) {
            try {
                transaction.commit();
            } catch (Exception e) {
                LOG.warn("Failed to commit transaction:\n{}", e);
            }
        }
        SafeClose.close(session);
    }

    @Override
    public Iterator<ExportTask> iterator() {
        transaction = session.beginTransaction();
        iterate = query.iterate();
        return iterate;
    }
}
