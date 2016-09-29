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
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
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

package org.dcm4chee.arc.entity;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;

import javax.persistence.*;
import java.util.Date;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Sep 2016
 */
@NamedQueries({
@NamedQuery(
        name = StgCmtResult.FIND_BY_TRANSACTION_UID,
        query = "select result from StgCmtResult result " +
                "where result.transactionUID = ?1"
),
@NamedQuery(
        name = StgCmtResult.FIND_BY_STATUS_AND_UPDATED_BEFORE,
        query = "select result from StgCmtResult result " +
                "where result.status = ?1 " +
                "and result.updatedTime <= ?2"),
@NamedQuery(
        name = StgCmtResult.FIND_BY_STATUS,
        query = "select result from StgCmtResult result " +
                "where result.status = ?1 "),
@NamedQuery(
        name = StgCmtResult.FIND_BY_UPDATED_BEFORE,
        query = "select result from StgCmtResult result " +
                "where result.updatedTime <= ?1"),
@NamedQuery(
        name = StgCmtResult.FIND_ALL,
        query = "select result from StgCmtResult result ")
})

@Entity
@Table(name = "stgcmt_result",
        uniqueConstraints = @UniqueConstraint(columnNames = "transaction_uid"),
        indexes = {
            @Index(columnList = "updated_time"),
            @Index(columnList = "device_name"),
            @Index(columnList = "exporter_id"),
            @Index(columnList = "study_iuid"),
            @Index(columnList = "stgcmt_status")
    }
)
public class StgCmtResult {

    public enum Status { PENDING, COMPLETED, WARNING, FAILED }

    public static final String FIND_BY_TRANSACTION_UID = "StgCmtResult.findByTransactionUID";
    public static final String FIND_BY_STATUS_AND_UPDATED_BEFORE = "StgCmtResult.findByStatusAndUpdatedBefore";
    public static final String FIND_BY_STATUS = "StgCmtResult.findByStatus";
    public static final String FIND_BY_UPDATED_BEFORE = "StgCmtResult.findByUpdatedBefore";
    public static final String FIND_ALL = "StgCmtResult.findAll";

    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;

    @Basic(optional = false)
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_time", updatable = false)
    private Date createdTime;

    @Basic(optional = false)
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_time")
    private Date updatedTime;

    @Basic(optional = false)
    @Column(name = "transaction_uid", updatable = false)
    private String transactionUID;

    @Basic(optional = false)
    @Column(name = "device_name", updatable = false)
    private String deviceName;

    @Basic(optional = false)
    @Column(name = "exporter_id", updatable = false)
    private String exporterID;

    @Basic(optional = false)
    @Column(name = "study_iuid", updatable = false)
    private String studyInstanceUID;

    @Basic
    @Column(name = "series_iuid", updatable = false)
    private String seriesInstanceUID;

    @Basic
    @Column(name = "sop_iuid", updatable = false)
    private String sopInstanceUID;

    @Column(name = "num_instances", updatable = false)
    private int numberOfInstances;

    @Basic(optional = false)
    @Column(name = "stgcmt_status")
    private Status status;

    @Column(name = "num_failures")
    private int numberOfFailures;

    @PrePersist
    public void onPrePersist() {
        Date now = new Date();
        createdTime = now;
        updatedTime = now;
    }

    @PreUpdate
    public void onPreUpdate() {
        updatedTime = new Date();
    }

    public long getPk() {
        return pk;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getExporterID() {
        return exporterID;
    }

    public void setExporterID(String exporterID) {
        this.exporterID = exporterID;
    }

    public String getStudyInstanceUID() {
        return studyInstanceUID;
    }

    public void setStudyInstanceUID(String studyInstanceUID) {
        this.studyInstanceUID = studyInstanceUID;
    }

    public String getSeriesInstanceUID() {
        return seriesInstanceUID;
    }

    public void setSeriesInstanceUID(String seriesInstanceUID) {
        this.seriesInstanceUID = seriesInstanceUID;
    }

    public String getSopInstanceUID() {
        return sopInstanceUID;
    }

    public void setSopInstanceUID(String sopInstanceUID) {
        this.sopInstanceUID = sopInstanceUID;
    }

    public Date getCreatedTime() {
        return createdTime;
    }

    public Date getUpdatedTime() {
        return updatedTime;
    }

    public String getTransactionUID() {
        return transactionUID;
    }

    public int getNumberOfInstances() {
        return numberOfInstances;
    }

    public Status getStatus() {
        return status;
    }

    public int getNumberOfFailures() {
        return numberOfFailures;
    }

    public void setStgCmtRequest(Attributes actionInfo) {
        this.transactionUID = actionInfo.getString(Tag.TransactionUID);
        this.numberOfInstances = actionInfo.getSequence(Tag.ReferencedSOPSequence).size();
        this.status = Status.PENDING;
    }

    public void setStgCmtResult(Attributes eventInfo) {
        Sequence failedSeq = eventInfo.getSequence(Tag.FailedSOPSequence);
        this.numberOfFailures = failedSeq != null ? failedSeq.size() : 0;
        this.status = failedSeq == null ? Status.COMPLETED
                : numberOfFailures < numberOfInstances ? Status.WARNING : Status.FAILED;
    }
}
