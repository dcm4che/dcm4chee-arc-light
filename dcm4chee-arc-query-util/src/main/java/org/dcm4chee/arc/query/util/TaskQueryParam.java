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
 * Portions created by the Initial Developer are Copyright (C) 2019
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
 */

package org.dcm4chee.arc.query.util;

import org.dcm4chee.arc.entity.Task;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @author Gunter Zeilinger <gunterze@protonmail.com>
 * @since Mar 2019
 */
public class TaskQueryParam {

    private Long taskPK;
    private List<String> queueNames = new ArrayList<>();
    private String deviceName;
    private Task.Type type;
    private Task.Type notType;
    private Task.Status status;
    private Task.Status notStatus;
    private String batchID;
    private String createdTime;
    private String updatedTime;
    private Date updatedBefore;
    private String orderBy;
    private String localAET;
    private String remoteAET;
    private String destinationAET;
    private String primaryAET;
    private String secondaryAET;
    private String studyIUID;
    private String checkMissing;
    private String checkDifferent;
    private String compareFields;
    private List<String> exporterIDs = new ArrayList<>();

    public Long getTaskPK() {
        return taskPK;
    }

    public void setTaskPK(Long taskPK) {
        this.taskPK = taskPK;
    }

    public List<String> getQueueNames() {
        return queueNames;
    }

    public void setQueueNames(List<String> queueNames) {
        this.queueNames = queueNames;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public Task.Type getNotType() {
        return notType;
    }

    public void setNotType(Task.Type notType) {
        this.notType = notType;
    }

    public Task.Status getNotStatus() {
        return notStatus;
    }

    public void setNotStatus(Task.Status notStatus) {
        this.notStatus = notStatus;
    }

    public Task.Type getType() {
        return type;
    }

    public void setType(Task.Type type) {
        this.type = type;
    }

    public Task.Status getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status != null ? Task.Status.fromString(status) : null;
    }

    public void setStatus(Task.Status status) {
        this.status = status;
    }

    public String getBatchID() {
        return batchID;
    }

    public void setBatchID(String batchID) {
        this.batchID = batchID;
    }

    public String getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(String createdTime) {
        this.createdTime = createdTime;
    }

    public String getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(String updatedTime) {
        this.updatedTime = updatedTime;
    }

    public Date getUpdatedBefore() {
        return updatedBefore;
    }

    public void setUpdatedBefore(Date updatedBefore) {
        this.updatedBefore = updatedBefore;
    }

    public void setUpdatedBeforeNotAfter(Date notAfter) {
        if (updatedBefore == null || updatedBefore.compareTo(notAfter) > 0)
            updatedBefore = notAfter;
    }

    public String getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(String orderBy) {
        this.orderBy = orderBy;
    }

    public String getLocalAET() {
        return localAET;
    }

    public void setLocalAET(String localAET) {
        this.localAET = localAET;
    }

    public String getStudyIUID() {
        return studyIUID;
    }

    public void setStudyIUID(String studyIUID) {
        this.studyIUID = studyIUID;
    }

    public List<String> getExporterIDs() {
        return exporterIDs;
    }

    public void setExporterIDs(List<String> exporterIDs) {
        this.exporterIDs = exporterIDs;
    }

    public String getRemoteAET() {
        return remoteAET;
    }

    public void setRemoteAET(String remoteAET) {
        this.remoteAET = remoteAET;
    }

    public String getDestinationAET() {
        return destinationAET;
    }

    public void setDestinationAET(String destinationAET) {
        this.destinationAET = destinationAET;
    }

    public String getPrimaryAET() {
        return primaryAET;
    }

    public void setPrimaryAET(String primaryAET) {
        this.primaryAET = primaryAET;
    }

    public String getSecondaryAET() {
        return secondaryAET;
    }

    public void setSecondaryAET(String secondaryAET) {
        this.secondaryAET = secondaryAET;
    }

    public String getCompareFields() {
        return compareFields;
    }

    public void setCompareFields(String compareFields) {
        this.compareFields = compareFields;
    }

    public String getCheckMissing() {
        return checkMissing;
    }

    public void setCheckMissing(String checkMissing) {
        this.checkMissing = checkMissing;
    }

    public String getCheckDifferent() {
        return checkDifferent;
    }

    public void setCheckDifferent(String checkDifferent) {
        this.checkDifferent = checkDifferent;
    }

    @Override
    public String toString() {
        return "TaskQueryParam{" +
                "status=" + status +
                ", type=" + type +
                ", queueName=" + String.join(";", queueNames) +
                "}";
    }
}
