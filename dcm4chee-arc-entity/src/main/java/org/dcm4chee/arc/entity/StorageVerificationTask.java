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

package org.dcm4chee.arc.entity;

import org.dcm4che3.conf.json.JsonWriter;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.StorageVerificationPolicy;

import javax.json.stream.JsonGenerator;
import javax.persistence.*;
import java.io.IOException;
import java.io.Writer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jul 2018
 */
@Entity
@Table(name = "stgver_task",
        indexes = {
                @Index(columnList = "created_time"),
                @Index(columnList = "updated_time"),
                @Index(columnList = "study_iuid, series_iuid, sop_iuid")}
)
@NamedQueries({
        @NamedQuery(name = StorageVerificationTask.UPDATE_RESULT_BY_PK,
                query = "update StorageVerificationTask o set " +
                        "o.updatedTime=current_timestamp, " +
                        "o.completed=?2, " +
                        "o.failed=?3 " +
                        "where pk=?1")
})
public class StorageVerificationTask {
    public static final String UPDATE_RESULT_BY_PK = "StorageVerificationTask.UpdateResultByPk";

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
    @Column(name = "local_aet", updatable = false)
    private String localAET;

    @Column(name = "stgcmt_policy", updatable = false)
    private StorageVerificationPolicy storageVerificationPolicy;

    @Column(name = "update_location_status", updatable = false)
    private Boolean updateLocationStatus;

    @Column(name = "storage_ids", updatable = false)
    private String storageIDs;

    @Basic(optional = false)
    @Column(name = "study_iuid", updatable = false)
    private String studyInstanceUID;

    @Column(name = "series_iuid", updatable = false)
    private String seriesInstanceUID;

    @Column(name = "sop_iuid", updatable = false)
    private String sopInstanceUID;

    @Basic(optional = false)
    @Column(name = "completed")
    private int completed;

    @Basic(optional = false)
    @Column(name = "failed")
    private int failed;

    @OneToOne(cascade= CascadeType.ALL, orphanRemoval = true, optional = false)
    @JoinColumn(name = "queue_msg_fk", updatable = false)
    private QueueMessage queueMessage;

    @PrePersist
    public void onPrePersist() {
        Date now = new Date();
        createdTime = now;
        updatedTime = now;
    }

    @PreUpdate
    public void onPreUpdate() {
        setUpdatedTime();
    }

    public void setUpdatedTime() {
        updatedTime = new Date();
    }

    public long getPk() {
        return pk;
    }

    public Date getCreatedTime() {
        return createdTime;
    }

    public Date getUpdatedTime() {
        return updatedTime;
    }

    public String getLocalAET() {
        return localAET;
    }

    public void setLocalAET(String localAET) {
        this.localAET = localAET;
    }

    public StorageVerificationPolicy getStorageVerificationPolicy() {
        return storageVerificationPolicy;
    }

    public void setStorageVerificationPolicy(StorageVerificationPolicy storageVerificationPolicy) {
        this.storageVerificationPolicy = storageVerificationPolicy;
    }

    public Boolean getUpdateLocationStatus() {
        return updateLocationStatus;
    }

    public void setUpdateLocationStatus(Boolean updateLocationStatus) {
        this.updateLocationStatus = updateLocationStatus;
    }

    public String getStorageIDsAsString() {
        return storageIDs;
    }

    public String[] getStorageIDs() {
        return StringUtils.split(storageIDs, '\\');
    }

    public void setStorageIDs(String[] storageIDs) {
        if (storageIDs != null && storageIDs.length > 0) {
            Arrays.sort(storageIDs);
            this.storageIDs = StringUtils.concat(storageIDs, '\\');
        } else {
            this.storageIDs = null;
        }
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

    public String getSOPInstanceUID() {
        return sopInstanceUID;
    }

    public void setSOPInstanceUID(String sopInstanceUID) {
        this.sopInstanceUID = sopInstanceUID;
    }

    public int getCompleted() {
        return completed;
    }

    public void setCompleted(int completed) {
        this.completed = completed;
    }

    public int getFailed() {
        return failed;
    }

    public void setFailed(int failed) {
        this.failed = failed;
    }

    public QueueMessage getQueueMessage() {
        return queueMessage;
    }

    public void setQueueMessage(QueueMessage queueMessage) {
        this.queueMessage = queueMessage;
    }

    public void writeAsJSONTo(JsonGenerator gen) {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        JsonWriter writer = new JsonWriter(gen);
        gen.writeStartObject();
        writer.writeNotNullOrDef("pk", pk, null);
        writer.writeNotNullOrDef("createdTime", df.format(createdTime), null);
        writer.writeNotNullOrDef("updatedTime", df.format(updatedTime), null);
        writer.writeNotNullOrDef("LocalAET", localAET, null);
        writer.writeNotNullOrDef("StgCmtPolicy", storageVerificationPolicy, null);
        writer.writeNotNull("UpdateLocationStatus", updateLocationStatus);
        writer.writeNotEmpty("StorageID", getStorageIDs());
        writer.writeNotNullOrDef("StudyInstanceUID", studyInstanceUID, null);
        writer.writeNotNullOrDef("SeriesInstanceUID", seriesInstanceUID, null);
        writer.writeNotNullOrDef("SOPInstanceUID", sopInstanceUID, null);
        writer.writeNotNullOrDef("completed", completed, 0);
        writer.writeNotNullOrDef("failed", failed, 0);
        queueMessage.writeStatusAsJSONTo(writer, df);
        gen.writeEnd();
        gen.flush();
    }

    public static void writeCSVHeader(Writer writer, char delimiter) throws IOException {
        writer.write("pk" + delimiter +
                "createdTime" + delimiter +
                "updatedTime" + delimiter +
                "LocalAET" + delimiter +
                "StgCmtPolicy" + delimiter +
                "UpdateLocationStatus" + delimiter +
                "StorageID" + delimiter +
                "StudyInstanceUID" + delimiter +
                "SeriesInstanceUID" + delimiter +
                "SOPInstanceUID" + delimiter +
                "completed" + delimiter +
                "failed" + delimiter +
                "JMSMessageID" + delimiter +
                "queue" + delimiter +
                "dicomDeviceName" + delimiter +
                "status" + delimiter +
                "scheduledTime" + delimiter +
                "failures" + delimiter +
                "batchID" + delimiter +
                "processingStartTime" + delimiter +
                "processingEndTime" + delimiter +
                "errorMessage" + delimiter +
                "outcomeMessage\r\n");
    }

    public void writeAsCSVTo(Writer writer, char delimiter) throws IOException {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        writer.write(String.valueOf(pk));
        writer.write(delimiter);
        writer.write(df.format(createdTime));
        writer.write(delimiter);
        writer.write(df.format(updatedTime));
        writer.write(delimiter);
        writer.write(localAET);
        writer.write(delimiter);
        if (storageVerificationPolicy != null)
            writer.write(storageVerificationPolicy.name());
        writer.write(delimiter);
        writer.write(String.valueOf(updateLocationStatus));
        writer.write(delimiter);
        if (storageIDs != null)
            writer.write(storageIDs);
        writer.write(delimiter);
        writer.write(studyInstanceUID);
        writer.write(delimiter);
        if (seriesInstanceUID != null)
            writer.write(seriesInstanceUID);
        writer.write(delimiter);
        if (sopInstanceUID != null)
            writer.write(sopInstanceUID);
        writer.write(delimiter);
        writer.write(String.valueOf(completed));
        writer.write(delimiter);
        writer.write(String.valueOf(failed));
        writer.write(delimiter);
        queueMessage.writeStatusAsCSVTo(writer, df, delimiter);
        writer.write('\r');
        writer.write('\n');
    }

    @Override
    public String toString() {
        return "StgVerTask[pk=" + pk
                + ", LocalAET=" + localAET
                + ", StudyIUID=" + studyInstanceUID
                + ", SeriesIUID=" + seriesInstanceUID
                + "]";
    }
}
