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

package org.dcm4chee.arc.retrieve.mgt;

import org.dcm4chee.arc.entity.RetrieveTask;
import org.dcm4chee.arc.entity.Task;
import org.dcm4chee.arc.event.TaskEvent;
import org.dcm4chee.arc.qmgt.*;
import org.dcm4chee.arc.query.util.TaskQueryParam;
import org.dcm4chee.arc.query.util.TaskQueryParam1;
import org.dcm4chee.arc.retrieve.ExternalRetrieveContext;

import javax.persistence.Tuple;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Oct 2017
 */
public interface RetrieveManager {
    Outcome cmove(ExternalRetrieveContext ctx, Task queueMessage) throws Exception;

    int scheduleRetrieveTask(ExternalRetrieveContext ctx, Date notRetrievedAfter);

    void rescheduleRetrieveTask(Long pk, String newQueueName, TaskEvent queueEvent);

    void rescheduleRetrieveTask(Long pk, String newQueueName, TaskEvent queueEvent, Date scheduledTime);

    List<RetrieveBatch> listRetrieveBatches(TaskQueryParam1 taskQueryParam, int offset, int limit);

    List<String> listDistinctDeviceNames(TaskQueryParam retrieveTaskQueryParam);

    List<Long> listRetrieveTaskPks(TaskQueryParam queueTaskQueryParam, TaskQueryParam retrieveTaskQueryParam, int limit);

    List<Tuple> listRetrieveTaskPkAndLocalAETs(TaskQueryParam queueTaskQueryParam, TaskQueryParam retrieveTaskQueryParam, int limit);

    Tuple findDeviceNameAndLocalAETByPk(Long pk);

    boolean scheduleRetrieveTask(Long pk);
}
