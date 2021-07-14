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
 *  Portions created by the Initial Developer are Copyright (C) 2015-2019
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

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.*;
import org.dcm4che3.util.ReverseDNS;
import org.dcm4che3.util.TagUtils;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.entity.RetrieveTask;
import org.dcm4chee.arc.entity.Task;
import org.dcm4chee.arc.event.QueueMessageEvent;
import org.dcm4chee.arc.qmgt.IllegalTaskStateException;
import org.dcm4chee.arc.qmgt.Outcome;
import org.dcm4chee.arc.query.util.TaskQueryParam;
import org.dcm4chee.arc.query.util.TaskQueryParam1;
import org.dcm4chee.arc.retrieve.ExternalRetrieveContext;
import org.dcm4chee.arc.retrieve.mgt.RetrieveBatch;
import org.dcm4chee.arc.retrieve.mgt.RetrieveManager;
import org.dcm4chee.arc.retrieve.scu.CMoveSCU;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.persistence.Tuple;
import java.io.IOException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Oct 2017
 */
@ApplicationScoped
public class RetrieveManagerImpl implements RetrieveManager {
    private static final Logger LOG = LoggerFactory.getLogger(RetrieveManagerImpl.class);

    @Inject
    private Device device;

    @Inject
    private Event<ExternalRetrieveContext> externalRetrieve;

    @Inject
    private CMoveSCU moveSCU;

    @Inject
    private RetrieveManagerEJB ejb;

    @Override
    public Outcome cmove(ExternalRetrieveContext ctx, Task task) throws Exception {
        ApplicationEntity localAE = device.getApplicationEntity(ctx.getLocalAET(), true);
        Association as = moveSCU.openAssociation(localAE, ctx.getRemoteAET());
        ctx.setRemoteHostName(ReverseDNS.hostNameOf(as.getSocket().getInetAddress()));
        try {
            ejb.resetRetrieveTask(task);
            final DimseRSP rsp = moveSCU.cmove(as, Priority.NORMAL, ctx.getDestinationAET(), ctx.getKeys());
            while (rsp.next()) {
                ejb.updateRetrieveTask(task, rsp.getCommand());
            }
            externalRetrieve.fire(ctx.setResponse(rsp.getCommand()));
            return toOutcome(ctx, localAE.getAEExtensionNotNull(ArchiveAEExtension.class));
        } finally {
            try {
                as.release();
            } catch (IOException e) {
                LOG.info("{}: Failed to release association:\\n", as, e);
            }
        }
    }

    private Outcome toOutcome(ExternalRetrieveContext ctx, ArchiveAEExtension arcAE) {
        int status = ctx.getStatus();
        Attributes keys = ctx.getKeys();
        StringBuilder sb = new StringBuilder(256)
                .append("Export ")
                .append(keys.getString(Tag.QueryRetrieveLevel))
                .append("[suid:")
                .append(keys.getString(Tag.StudyInstanceUID))
                .append("] from ")
                .append(ctx.getRemoteAET())
                .append(" to ")
                .append(ctx.getDestinationAET());
        if (status == Status.Success || status == Status.OneOrMoreFailures) {
            sb.append(" - completed:").append(ctx.completed());
            int warning = ctx.warning();
            if (warning > 0)
                sb.append(", warning:").append(warning);
            int failed = ctx.failed();
            if (failed > 0)
                sb.append(", failed:").append(failed);
        } else {
            sb.append(" failed - status:").append(TagUtils.shortToHexString(status)).append('H');
            String errorComment = ctx.getErrorComment();
            if (errorComment != null)
                sb.append(", error:").append(errorComment);
        }
        return new Outcome(
                status == Status.Success
                    ? arcAE.retrieveTaskWarningOnNoMatch() && ctx.completed() == 0 && ctx.warning() == 0
                        || arcAE.retrieveTaskWarningOnWarnings() && ctx.warning() == 0
                        ? Task.Status.WARNING
                        : Task.Status.COMPLETED
                    : status == Status.OneOrMoreFailures && (ctx.completed() > 0 || ctx.warning() > 0)
                        ? Task.Status.WARNING
                        : Task.Status.FAILED,
                sb.toString());
    }

    @Override
    public int scheduleRetrieveTask(ExternalRetrieveContext ctx, Date notRetrievedAfter) {
        return ejb.scheduleRetrieveTask(ctx, notRetrievedAfter);
    }

    @Override
    public boolean deleteRetrieveTask(Long pk, QueueMessageEvent queueEvent) {
        return ejb.deleteRetrieveTask(pk, queueEvent);
    }

    @Override
    public boolean cancelRetrieveTask(Long pk, QueueMessageEvent queueEvent) throws IllegalTaskStateException {
        return ejb.cancelRetrieveTask(pk, queueEvent);
    }

    @Override
    public long cancelRetrieveTasks(TaskQueryParam queueTaskQueryParam, TaskQueryParam retrieveTaskQueryParam){
        return ejb.cancelRetrieveTasks(queueTaskQueryParam, retrieveTaskQueryParam);
    }

    @Override
    public void rescheduleRetrieveTask(Long pk, String newQueueName, QueueMessageEvent queueEvent) {
        ejb.rescheduleRetrieveTask(pk, newQueueName, queueEvent);
    }

    @Override
    public void rescheduleRetrieveTask(Long pk, String newQueueName, QueueMessageEvent queueEvent, Date scheduledTime) {
        ejb.rescheduleRetrieveTask(pk, newQueueName, queueEvent, scheduledTime);
    }

    @Override
    public void markTaskForRetrieve(
            Long pk, String devName, String newQueueName, QueueMessageEvent queueEvent, Date scheduledTime) {
        ejb.markTaskForRetrieve(pk, devName, newQueueName, queueEvent, scheduledTime);
    }

    @Override
    public int deleteTasks(TaskQueryParam queueTaskQueryParam, TaskQueryParam retrieveTaskQueryParam, int deleteTasksFetchSize) {
        return ejb.deleteTasks(queueTaskQueryParam, retrieveTaskQueryParam, deleteTasksFetchSize);
    }

    @Override
    public List<RetrieveBatch> listRetrieveBatches(TaskQueryParam1 queryParam, int offset, int limit) {
        return ejb.listRetrieveBatches(queryParam, offset, limit);
    }

    @Override
    public List<String> listDistinctDeviceNames(TaskQueryParam retrieveTaskQueryParam) {
        return ejb.listDistinctDeviceNames(retrieveTaskQueryParam);
    }

    @Override
    public List<Long> listRetrieveTaskPks(TaskQueryParam queueTaskQueryParam, TaskQueryParam retrieveTaskQueryParam,
                                          int limit) {
        return ejb.listRetrieveTaskPks(queueTaskQueryParam, retrieveTaskQueryParam, limit);
    }

    @Override
    public List<Tuple> listRetrieveTaskPkAndLocalAETs(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam retrieveTaskQueryParam, int limit) {
        return ejb.listRetrieveTaskPkAndLocalAETs(queueTaskQueryParam, retrieveTaskQueryParam, limit);
    }

    @Override
    public Iterator<RetrieveTask> listRetrieveTasks(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam retrieveTaskQueryParam, int offset, int limit) {
        return ejb.listRetrieveTasks(queueTaskQueryParam, retrieveTaskQueryParam, offset, limit);
    }

    @Override
    public long countTasks(TaskQueryParam queueTaskQueryParam, TaskQueryParam retrieveTaskQueryParam) {
        return ejb.countTasks(queueTaskQueryParam, retrieveTaskQueryParam);
    }

    @Override
    public Tuple findDeviceNameAndLocalAETByPk(Long pk) {
        return ejb.findDeviceNameAndLocalAETByPk(pk);
    }

    @Override
    public List<RetrieveTask.PkAndQueueName> findRetrieveTasksToSchedule(int fetchSize, Set<String> suspendedQueues) {
        return ejb.findRetrieveTasksToSchedule(fetchSize, suspendedQueues);
    }

    @Override
    public boolean scheduleRetrieveTask(Long pk) {
        return ejb.scheduleRetrieveTask(pk);
    }
}
