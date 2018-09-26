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
import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.IApplicationEntityCache;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.Duration;
import org.dcm4chee.arc.diff.*;
import org.dcm4chee.arc.entity.AttributesBlob;
import org.dcm4chee.arc.entity.DiffTask;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.event.QueueMessageEvent;
import org.dcm4chee.arc.qmgt.*;
import org.dcm4chee.arc.query.scu.CFindSCU;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Feb 2018
 */
@ApplicationScoped
public class DiffServiceImpl implements DiffService {

    private Map<String,DiffSCU> diffSCUMap = Collections.synchronizedMap(new HashMap<>());;

    @Inject
    private Device device;

    @Inject
    private IApplicationEntityCache aeCache;

    @Inject
    private CFindSCU findSCU;

    @Inject
    private DiffServiceEJB ejb;

    @Override
    public DiffSCU createDiffSCU(DiffContext ctx) {
        return new DiffSCUImpl(ctx, findSCU);
    }

    @Override
    public void scheduleDiffTask(DiffContext ctx) throws QueueSizeLimitExceededException {
        ejb.scheduleDiffTask(ctx);
    }

    @Override
    public Outcome executeDiffTask(DiffTask diffTask, HttpServletRequestInfo httpServletRequestInfo)
            throws Exception {
        ejb.resetDiffTask(diffTask);
        String messageID = diffTask.getQueueMessage().getMessageID();
        ScheduledFuture<?> updateDiffTask = null;
        try (DiffSCU diffSCU = createDiffSCU(toDiffContext(diffTask, httpServletRequestInfo))) {
            diffSCUMap.put(messageID, diffSCU);
            diffSCU.init();
            Attributes diff;
            updateDiffTask = updateDiffTask(diffTask, diffSCU);
            while ((diff = diffSCU.nextDiff()) != null)
                ejb.addDiffTaskAttributes(diffTask, diff);
            ejb.updateDiffTask(diffTask, diffSCU);
            return toOutcome(diffSCU);
        } finally {
            diffSCUMap.remove(messageID);
            if (updateDiffTask != null)
                updateDiffTask.cancel(false);
        }
    }

    private ScheduledFuture<?> updateDiffTask(DiffTask diffTask, DiffSCU diffSCU) {
        Duration interval = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class)
                .getDiffTaskProgressUpdateInterval();
        return interval != null
                ? device.scheduleAtFixedRate(
                    () -> ejb.updateDiffTask(diffTask, diffSCU),
                    interval.getSeconds(), interval.getSeconds(), TimeUnit.SECONDS)
                : null;
    }

    public void cancelDiffTask(@Observes MessageCanceled event) {
        DiffSCU diffSCU = diffSCUMap.get(event.getMessageID());
        if (diffSCU != null)
            diffSCU.cancel();
    }

    @Override
    public DiffTaskQuery listDiffTasks(Predicate matchQueueMessage, Predicate matchDiffTask,
                                       OrderSpecifier<Date> order, int offset, int limit) {
        return ejb.listDiffTasks(matchQueueMessage, matchDiffTask, order, offset, limit);
    }

    @Override
    public long countDiffTasks(Predicate matchQueueMessage, Predicate matchDiffTask) {
        return ejb.countDiffTasks(matchQueueMessage, matchDiffTask);
    }

    private Outcome toOutcome(DiffSCU diffSCU) {
        QueueMessage.Status status = QueueMessage.Status.COMPLETED;
        StringBuilder sb = new StringBuilder();
        sb.append(diffSCU.matches()).append(" studies compared");
        status = check(", missing: ", diffSCU.missing(), status, sb);
        status = check(", different: ", diffSCU.different(), status, sb);
        return new Outcome(status, sb.toString());
    }

    @Override
    public DiffTask getDiffTask(long taskPK) {
        return ejb.getDiffTask(taskPK);
    }

    @Override
    public List<AttributesBlob> getDiffTaskAttributes(DiffTask diffTask, int offset, int limit) {
        return ejb.getDiffTaskAttributes(diffTask, offset, limit);
    }

    @Override
    public List<AttributesBlob> getDiffTaskAttributes(Predicate matchQueueBatch, Predicate matchDiffBatch, int offset, int limit) {
        return ejb.getDiffTaskAttributes(matchQueueBatch, matchDiffBatch, offset, limit);
    }

    @Override
    public List<DiffBatch> listDiffBatches(Predicate matchQueueBatch, Predicate matchDiffBatch, OrderSpecifier<Date> order,
                                           int offset, int limit) {
        return ejb.listDiffBatches(matchQueueBatch, matchDiffBatch, order, offset, limit);
    }

    @Override
    public long diffTasksOfBatch(String batchID) {
        return ejb.diffTasksOfBatch(batchID);
    }

    @Override
    public boolean cancelDiffTask(Long pk, QueueMessageEvent queueEvent) throws IllegalTaskStateException {
        return ejb.cancelDiffTask(pk, queueEvent);
    }

    @Override
    public long cancelDiffTasks(Predicate matchQueueMessage, Predicate matchDiffTask, QueueMessage.Status prev)
            throws IllegalTaskStateException {
        return ejb.cancelDiffTasks(matchQueueMessage, matchDiffTask, prev);
    }

    @Override
    public void rescheduleDiffTask(Long pk, QueueMessageEvent queueEvent) {
        ejb.rescheduleDiffTask(pk, queueEvent);
    }

    @Override
    public void rescheduleDiffTask(String diffTaskQueueMsgId) {
        ejb.rescheduleDiffTask(diffTaskQueueMsgId, null);
    }

    @Override
    public List<String> listDiffTaskQueueMsgIDs(Predicate matchQueueMessage, Predicate matchDiffTask, int limit) {
        return ejb.listDiffTaskQueueMsgIDs(matchQueueMessage, matchDiffTask, limit);
    }

    @Override
    public String findDeviceNameByPk(Long pk) {
        return ejb.findDeviceNameByPk(pk);
    }

    @Override
    public boolean deleteDiffTask(Long pk, QueueMessageEvent queueEvent) {
        return ejb.deleteDiffTask(pk, queueEvent);
    }

    @Override
    public int deleteTasks(Predicate matchQueueMessage, Predicate matchDiffTask, int deleteTasksFetchSize) {
        return ejb.deleteTasks(matchQueueMessage, matchDiffTask, deleteTasksFetchSize);
    }

    @Override
    public List<String> listDistinctDeviceNames(Predicate matchQueueMessage, Predicate matchDiffTask) {
        return ejb.listDistinctDeviceNames(matchQueueMessage, matchDiffTask);
    }

    private QueueMessage.Status check(String prompt, int failures, QueueMessage.Status status, StringBuilder sb) {
        if (failures == 0)
            return status;

        sb.append(prompt).append(failures);
        return QueueMessage.Status.WARNING;
    }

    private DiffContext toDiffContext(DiffTask diffTask, HttpServletRequestInfo httpServletRequestInfo)
            throws ConfigurationException {
        return new DiffContext()
                .setLocalAE(device.getApplicationEntity(diffTask.getLocalAET(), true))
                .setPrimaryAE(aeCache.get(diffTask.getPrimaryAET()))
                .setSecondaryAE(aeCache.get(diffTask.getSecondaryAET()))
                .setQueryString(diffTask.getQueryString())
                .setHttpServletRequestInfo(httpServletRequestInfo);
    }
}
