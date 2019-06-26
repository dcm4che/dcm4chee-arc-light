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
 * Portions created by the Initial Developer are Copyright (C) 2017-2019
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

package org.dcm4chee.arc.retrieve.mgt;

import java.util.Date;
import java.util.List;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Feb 2018
 */

public class RetrieveBatch {
    private String batchID;
    private Date[] createdTimeRange = {};
    private Date[] updatedTimeRange = {};
    private Date[] scheduledTimeRange = {};
    private Date[] processingStartTimeRange = {};
    private Date[] processingEndTimeRange = {};
    private String[] deviceNames = {};
    private String[] queueNames = {};
    private String[] localAETs = {};
    private String[] remoteAETs = {};
    private String[] destinationAETs = {};
    private long toSchedule;
    private long completed;
    private long canceled;
    private long warning;
    private long failed;
    private long scheduled;
    private long inProcess;

    public RetrieveBatch(String batchID) {
        this.batchID = batchID;
    }

    public String getBatchID() {
        return batchID;
    }

    public Date[] getCreatedTimeRange() {
        return createdTimeRange;
    }

    public void setCreatedTimeRange(Date... createdTimeRange) {
        this.createdTimeRange = createdTimeRange;
    }

    public Date[] getUpdatedTimeRange() {
        return updatedTimeRange;
    }

    public void setUpdatedTimeRange(Date... updatedTimeRange) {
        this.updatedTimeRange = updatedTimeRange;
    }

    public Date[] getScheduledTimeRange() {
        return scheduledTimeRange;
    }

    public void setScheduledTimeRange(Date minScheduledTime, Date maxScheduledTime) {
        if (minScheduledTime == null && maxScheduledTime == null)
            return;

        this.scheduledTimeRange = new Date[2];
        this.scheduledTimeRange[0] = minScheduledTime;
        this.scheduledTimeRange[1] = maxScheduledTime;
    }

    public Date[] getProcessingStartTimeRange() {
        return processingStartTimeRange;
    }

    public void setProcessingStartTimeRange(Date minProcessingStartTime, Date maxProcessingStartTime) {
        if (minProcessingStartTime == null && maxProcessingStartTime == null)
            return;

        this.processingStartTimeRange = new Date[2];
        this.processingStartTimeRange[0] = minProcessingStartTime;
        this.processingStartTimeRange[1] = maxProcessingStartTime;
    }

    public Date[] getProcessingEndTimeRange() {
        return processingEndTimeRange;
    }

    public void setProcessingEndTimeRange(Date minProcessingEndTime, Date maxProcessingEndTime) {
        if (minProcessingEndTime == null && maxProcessingEndTime == null)
            return;

        this.processingEndTimeRange = new Date[2];
        this.processingEndTimeRange[0] = minProcessingEndTime;
        this.processingEndTimeRange[1] = maxProcessingEndTime;
    }

    public String[] getDeviceNames() {
        return deviceNames;
    }

    public void setDeviceNames(List<String> deviceNames) {
        this.deviceNames = deviceNames.toArray(new String[0]);
    }

    public String[] getQueueNames() {
        return queueNames;
    }

    public void setQueueNames(List<String> queueNames) {
        this.queueNames = queueNames.toArray(new String[0]);
    }

    public String[] getLocalAETs() {
        return localAETs;
    }

    public void setLocalAETs(List<String> localAETs) {
        this.localAETs = localAETs.toArray(new String[0]);
    }

    public String[] getRemoteAETs() {
        return remoteAETs;
    }

    public void setRemoteAETs(List<String> remoteAETs) {
        this.remoteAETs = remoteAETs.toArray(new String[0]);
    }

    public String[] getDestinationAETs() {
        return destinationAETs;
    }

    public void setDestinationAETs(List<String> destinationAETs) {
        this.destinationAETs = destinationAETs.toArray(new String[0]);
    }

    public int getCompleted() {
        return (int) completed;
    }

    public void setCompleted(long completed) {
        this.completed = completed;
    }

    public int getCanceled() {
        return (int) canceled;
    }

    public void setCanceled(long canceled) {
        this.canceled = canceled;
    }

    public int getWarning() {
        return (int) warning;
    }

    public void setWarning(long warning) {
        this.warning = warning;
    }

    public int getFailed() {
        return (int) failed;
    }

    public void setFailed(long failed) {
        this.failed = failed;
    }

    public int getScheduled() {
        return (int) scheduled;
    }

    public void setScheduled(long scheduled) {
        this.scheduled = scheduled;
    }

    public int getInProcess() {
        return (int) inProcess;
    }

    public void setInProcess(long inProcess) {
        this.inProcess = inProcess;
    }

    public int getToSchedule() {
        return (int) toSchedule;
    }

    public void setToSchedule(long toSchedule) {
        this.toSchedule = toSchedule;
    }
}
