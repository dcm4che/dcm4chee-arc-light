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

package org.dcm4chee.arc.diff;

import org.dcm4chee.arc.entity.DiffTask;
import org.dcm4chee.arc.event.QueueMessageEvent;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.qmgt.*;
import org.dcm4chee.arc.query.util.TaskQueryParam;

import javax.persistence.Tuple;
import java.util.Iterator;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Feb 2018
 */
public interface DiffService {
    String QUEUE_NAME = "DiffTasks";

    DiffSCU createDiffSCU(DiffContext ctx);

    void scheduleDiffTask(DiffContext ctx) throws QueueSizeLimitExceededException;

    void scheduleDiffTasks(DiffContext ctx, List<String> studyUIDs) throws QueueSizeLimitExceededException;

    Outcome executeDiffTask(DiffTask diffTask, HttpServletRequestInfo httpServletRequestInfo) throws Exception;

    DiffTask getDiffTask(long taskPK);

    List<byte[]> getDiffTaskAttributes(DiffTask diffTask, int offset, int limit);

    List<byte[]> getDiffTaskAttributes(
            TaskQueryParam queueBatchQueryParam, TaskQueryParam diffBatchQueryParam, int offset, int limit);

    List<DiffBatch> listDiffBatches(
            TaskQueryParam queueBatchQueryParam, TaskQueryParam diffBatchQueryParam, int offset, int limit);

    long diffTasksOfBatch(String batchID);

    boolean cancelDiffTask(Long pk, QueueMessageEvent queueEvent) throws IllegalTaskStateException;

    long cancelDiffTasks(TaskQueryParam queueTaskQueryParam, TaskQueryParam diffTaskQueryParam);

    void rescheduleDiffTask(Long pk, QueueMessageEvent queueEvent);

    void rescheduleDiffTask(String diffTaskQueueMsgId);

    Tuple findDeviceNameAndMsgPropsByPk(Long pk);

    boolean deleteDiffTask(Long pk, QueueMessageEvent queueEvent);

    int deleteTasks(TaskQueryParam queueTaskQueryParam, TaskQueryParam diffTaskQueryParam, int deleteTasksFetchSize);

    List<String> listDistinctDeviceNames(TaskQueryParam queueTaskQueryParam, TaskQueryParam diffTaskQueryParam);

    List<String> listDiffTaskQueueMsgIDs(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam diffTaskQueryParam, int limit);

    List<Tuple> listDiffTaskQueueMsgIDAndMsgProps(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam diffTaskQueryParam, int limit);

    long countTasks(TaskQueryParam queueTaskQueryParam, TaskQueryParam diffTaskQueryParam);

    Iterator<DiffTask> listDiffTasks(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam diffTaskQueryParam, int offset, int limit);
}
