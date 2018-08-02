/*
 * *** BEGIN LICENSE BLOCK *****
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
 * Portions created by the Initial Developer are Copyright (C) 2016
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
 * *** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.stgcmt;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.entity.StgCmtResult;
import org.dcm4chee.arc.entity.StgCmtTask;
import org.dcm4chee.arc.event.QueueMessageEvent;
import org.dcm4chee.arc.qmgt.HttpServletRequestInfo;
import org.dcm4chee.arc.qmgt.IllegalTaskStateException;
import org.dcm4chee.arc.qmgt.Outcome;
import org.dcm4chee.arc.qmgt.QueueSizeLimitExceededException;

import java.io.IOException;
import java.util.Date;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Sep 2016
 */
public interface StgCmtManager {
    String QUEUE_NAME = "StgCmtTasks";

    void addExternalRetrieveAETs(Attributes eventInfo, Device device);

    void persistStgCmtResult(StgCmtResult result);

    List<StgCmtResult> listStgCmts(
            StgCmtResult.Status status, String studyUID, String exporterID, int offset, int limit);

    boolean deleteStgCmt(String transactionUID);

    int deleteStgCmts(StgCmtResult.Status status, Date updatedBefore);

    void calculateResult(StgCmtContext ctx, Sequence refSopSeq);

    void calculateResult(StgCmtContext ctx, String studyIUID, String seriesIUID, String sopIUID) throws IOException;

    boolean scheduleStgCmtTask(StgCmtTask stgCmtTask, HttpServletRequestInfo httpServletRequestInfo,
                               String batchID)
            throws QueueSizeLimitExceededException;

    Outcome executeStgCmtTask(StgCmtTask stgCmtTask, HttpServletRequestInfo httpServletRequestInfo) throws IOException;

    StgCmtTaskQuery listStgCmtTasks(Predicate matchQueueMessage, Predicate matchStgCmtTask,
                                        OrderSpecifier<Date> order, int offset, int limit);

    long countStgCmtTasks(Predicate matchQueueMessage, Predicate matchStgCmtTask);

    boolean cancelStgCmtTask(Long pk, QueueMessageEvent queueEvent) throws IllegalTaskStateException;

    long cancelStgCmtTasks(Predicate matchQueueMessage, Predicate matchStgCmtTask, QueueMessage.Status prev)
            throws IllegalTaskStateException;

    String findDeviceNameByPk(Long pk);

    void rescheduleStgCmtTask(Long pk, QueueMessageEvent queueEvent);

    void rescheduleStgCmtTask(String stgCmtTaskQueueMsgId);

    List<String> listDistinctDeviceNames(Predicate matchQueueMessage, Predicate matchStgCmtTask);

    List<String> listStgCmtTaskQueueMsgIDs(Predicate matchQueueMessage, Predicate matchStgCmtTask, int limit);

    boolean deleteStgCmtTask(Long pk, QueueMessageEvent queueEvent);

    int deleteTasks(Predicate matchQueueMessage, Predicate matchStgCmtTask, int deleteTasksFetchSize);

    List<StgCmtBatch> listStgCmtBatches(Predicate matchQueueBatch, Predicate matchStgCmtBatch,
                                            OrderSpecifier<Date> order, int offset, int limit);
}
