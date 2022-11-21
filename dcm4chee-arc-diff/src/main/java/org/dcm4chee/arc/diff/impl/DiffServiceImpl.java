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

package org.dcm4chee.arc.diff.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.IApplicationEntityCache;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.Duration;
import org.dcm4chee.arc.diff.DiffBatch;
import org.dcm4chee.arc.diff.DiffContext;
import org.dcm4chee.arc.diff.DiffSCU;
import org.dcm4chee.arc.diff.DiffService;
import org.dcm4chee.arc.entity.Task;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.qmgt.TaskCanceled;
import org.dcm4chee.arc.qmgt.Outcome;
import org.dcm4chee.arc.qmgt.TaskManager;
import org.dcm4chee.arc.query.scu.CFindSCU;
import org.dcm4chee.arc.query.util.TaskQueryParam;

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

    private Map<Long,DiffSCU> diffSCUMap = Collections.synchronizedMap(new HashMap<>());;

    @Inject
    private Device device;

    @Inject
    private IApplicationEntityCache aeCache;

    @Inject
    private CFindSCU findSCU;

    @Inject
    private DiffServiceEJB ejb;

    @Inject
    private TaskManager taskManager;

    @Override
    public DiffSCU createDiffSCU(DiffContext ctx) {
        return new DiffSCUImpl(ctx, findSCU, device);
    }

    @Override
    public void scheduleDiffTask(DiffContext ctx) {
        scheduleDiffTask(ctx, ctx.getQueryString());
    }

    @Override
    public void scheduleDiffTasks(DiffContext ctx, List<String> studyUIDs) {
        studyUIDs.forEach(studyUID -> scheduleDiffTask(ctx, ctx.getQueryString() + studyUID));
    }

    @Override
    public Outcome executeDiffTask(Task diffTask, HttpServletRequestInfo httpServletRequestInfo)
            throws Exception {
        ejb.resetDiffTask(diffTask);
        Long taskPK = diffTask.getPk();
        ScheduledFuture<?> updateDiffTask = null;
        try (DiffSCU diffSCU = createDiffSCU(toDiffContext(diffTask, httpServletRequestInfo))) {
            diffSCUMap.put(taskPK, diffSCU);
            diffSCU.init();
            Attributes diff;
            updateDiffTask = updateDiffTaskAtFixRate(diffTask, diffSCU);
            while ((diff = diffSCU.nextDiff()) != null) {
                ejb.addDiffTaskAttributes(diffTask, diff);
            }
            updateDiffTask.cancel(false);
            ejb.updateDiffTask(diffTask, diffSCU);
            return toOutcome(diffSCU);
        } finally {
            if (updateDiffTask != null)
                updateDiffTask.cancel(false);
            diffSCUMap.remove(taskPK);
        }
    }

    private void scheduleDiffTask(DiffContext ctx, String queryString) {
        Task task = new Task();
        task.setDeviceName(device.getDeviceName());
        task.setQueueName(QUEUE_NAME);
        task.setType(Task.Type.DIFF);
        task.setScheduledTime(new Date());
        if (ctx.getHttpServletRequestInfo() != null) {
            task.setRequesterUserID(ctx.getHttpServletRequestInfo().requesterUserID);
            task.setRequesterHost(ctx.getHttpServletRequestInfo().requesterHost);
            task.setRequestURI(ctx.getHttpServletRequestInfo().requestURI);
        }
        task.setBatchID(ctx.getBatchID());
        task.setStatus(Task.Status.SCHEDULED);
        task.setLocalAET(ctx.getLocalAET());
        task.setPrimaryAET(ctx.getPrimaryAE().getAETitle());
        task.setSecondaryAET(ctx.getSecondaryAE().getAETitle());
        task.setQueryString(queryString);
        task.setCheckMissing(ctx.isCheckMissing());
        task.setCheckDifferent(ctx.isCheckDifferent());
        task.setCompareFields(ctx.getCompareFields());
        taskManager.scheduleTask(task);
    }

    private ScheduledFuture<?> updateDiffTaskAtFixRate(Task diffTask, DiffSCU diffSCU) {
        Duration interval = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class)
                .getDiffTaskProgressUpdateInterval();
        return interval != null
                ? device.scheduleAtFixedRate(
                () -> ejb.updateDiffTask(diffTask, diffSCU),
                    interval.getSeconds(), interval.getSeconds(), TimeUnit.SECONDS)
                : null;
    }

    public void cancelDiffTask(@Observes TaskCanceled event) {
        DiffSCU diffSCU = diffSCUMap.get(event.task.getPk());
        if (diffSCU != null)
            diffSCU.cancel();
    }

    private Outcome toOutcome(DiffSCU diffSCU) {
        Task.Status status = Task.Status.COMPLETED;
        StringBuilder sb = new StringBuilder();
        sb.append(diffSCU.matches()).append(" studies compared");
        status = check(", missing: ", diffSCU.missing(), status, sb);
        status = check(", different: ", diffSCU.different(), status, sb);
        return new Outcome(diffSCU.isCancelled() ? Task.Status.CANCELED : status, sb.toString());
    }

    @Override
    public List<byte[]> getDiffTaskAttributes(Task task, int offset, int limit) {
        return ejb.getDiffTaskAttributes(task, offset, limit);
    }

    @Override
    public List<byte[]> getDiffTaskAttributes(TaskQueryParam taskQueryParam, int offset, int limit) {
        return ejb.getDiffTaskAttributes(taskQueryParam, offset, limit);
    }

    @Override
    public List<DiffBatch> listDiffBatches(TaskQueryParam taskQueryPara, int offset, int limit) {
        return ejb.listDiffBatches(taskQueryPara, offset, limit);
    }

    @Override
    public long diffTasksOfBatch(String batchID) {
        return ejb.diffTasksOfBatch(batchID);
    }


    private Task.Status check(String prompt, int failures, Task.Status status, StringBuilder sb) {
        if (failures == 0)
            return status;

        sb.append(prompt).append(failures);
        return Task.Status.WARNING;
    }

    private DiffContext toDiffContext(Task diffTask, HttpServletRequestInfo httpServletRequestInfo)
            throws ConfigurationException {
        return new DiffContext()
                .setLocalAET(diffTask.getLocalAET())
                .setPrimaryAE(aeCache.get(diffTask.getPrimaryAET()))
                .setSecondaryAE(aeCache.get(diffTask.getSecondaryAET()))
                .setArcDev(device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class))
                .setQueryString(diffTask.getQueryString())
                .setHttpServletRequestInfo(httpServletRequestInfo);
    }

}
