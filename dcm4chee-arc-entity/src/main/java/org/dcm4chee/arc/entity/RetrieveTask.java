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

package org.dcm4chee.arc.entity;

import org.dcm4che3.conf.json.JsonWriter;
import org.dcm4che3.util.TagUtils;

import javax.json.stream.JsonGenerator;
import javax.persistence.*;
import java.io.IOException;
import java.io.Writer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Oct 2017
 */
@Entity
@Table(name = "retrieve_task",
        indexes = {
                @Index(columnList = "device_name"),
                @Index(columnList = "queue_name"),
                @Index(columnList = "local_aet"),
                @Index(columnList = "remote_aet"),
                @Index(columnList = "destination_aet"),
                @Index(columnList = "created_time"),
                @Index(columnList = "updated_time"),
                @Index(columnList = "study_iuid"),
                @Index(columnList = "batch_id") }
)
@NamedQueries({
        @NamedQuery(name = RetrieveTask.UPDATE_BY_QUEUE_MESSAGE,
                query = "update RetrieveTask o set " +
                        "o.updatedTime=current_timestamp, " +
                        "o.remaining=?2, " +
                        "o.completed=?3, " +
                        "o.failed=?4, " +
                        "o.warning=?5, " +
                        "o.statusCode=?6, " +
                        "o.errorComment=?7 " +
                        "where o.queueMessage=?1")
})
public class RetrieveTask {

    public static final String UPDATE_BY_QUEUE_MESSAGE = "RetrieveTask.UpdateByQueueMessage";

    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;

    @Basic(optional = false)
    @Column(name = "device_name")
    private String deviceName;

    @Basic(optional = false)
    @Column(name = "queue_name")
    private String queueName;

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

    @Basic(optional = false)
    @Column(name = "remote_aet", updatable = false)
    private String remoteAET;

    @Basic(optional = false)
    @Column(name = "destination_aet", updatable = false)
    private String destinationAET;

    @Basic(optional = false)
    @Column(name = "study_iuid", updatable = false)
    private String studyInstanceUID;

    @Column(name = "series_iuid", updatable = false)
    private String seriesInstanceUID;

    @Column(name = "sop_iuid", updatable = false)
    private String sopInstanceUID;

    @Column(name = "batch_id", updatable = false)
    private String batchID;

    @Basic(optional = false)
    @Column(name = "remaining")
    private int remaining = -1;

    @Basic(optional = false)
    @Column(name = "completed")
    private int completed;

    @Basic(optional = false)
    @Column(name = "failed")
    private int failed;

    @Basic(optional = false)
    @Column(name = "warning")
    private int warning;

    @Basic(optional = false)
    @Column(name = "status_code")
    private int statusCode = -1;

    @Column(name = "error_comment")
    private String errorComment;

    @OneToOne(cascade= CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "queue_msg_fk")
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

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getQueueName() {
        return queueName;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public String getBatchID() {
        return batchID;
    }

    public void setBatchID(String batchID) {
        this.batchID = batchID;
    }

    public int getNumberOfRemainingSubOperations() {
        return remaining;
    }

    public void setNumberOfRemainingSubOperations(int remaining) {
        this.remaining = remaining;
    }

    public int getNumberOfCompletedSubOperations() {
        return completed;
    }

    public void setNumberOfCompletedSubOperations(int completed) {
        this.completed = completed;
    }

    public int getNumberOfWarningSubOperations() {
        return warning;
    }

    public void setNumberOfWarningSubOperations(int warning) {
        this.warning = warning;
    }

    public int getNumberOfFailedSubOperations() {
        return failed;
    }

    public void setNumberOfFailedSubOperations(int failed) {
        this.failed = failed;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public String getErrorComment() {
        return errorComment;
    }

    public void setErrorComment(String errorComment) {
        this.errorComment = errorComment;
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
        writer.writeNotNullOrDef("RemoteAET", remoteAET, null);
        writer.writeNotNullOrDef("DestinationAET", destinationAET, null);
        writer.writeNotNullOrDef("StudyInstanceUID", studyInstanceUID, null);
        writer.writeNotNullOrDef("SeriesInstanceUID", seriesInstanceUID, null);
        writer.writeNotNullOrDef("SOPInstanceUID", sopInstanceUID, null);
        writer.writeNotNullOrDef("remaining", remaining, 0);
        writer.writeNotNullOrDef("completed", completed, 0);
        writer.writeNotNullOrDef("failed", failed, 0);
        writer.writeNotNullOrDef("warning", warning, 0);
        writer.writeNotNullOrDef("statusCode", TagUtils.shortToHexString(statusCode), -1);
        writer.writeNotNullOrDef("errorComment", errorComment, null);
        if (queueMessage == null) {
            writer.writeNotNullOrDef("batchID", batchID, null);
            writer.writeNotNullOrDef("status", QueueMessage.Status.TO_SCHEDULE.toString(), null);
            writer.writeNotNullOrDef("dicomDeviceName", deviceName, null);
            writer.writeNotNullOrDef("queue", queueName, null);
        }
        else
            queueMessage.writeStatusAsJSONTo(writer, df);
        gen.writeEnd();
        gen.flush();
    }

    public static void writeCSVHeader(Writer writer, char delimiter) throws IOException {
        writer.write("pk" + delimiter +
                "createdTime" + delimiter +
                "updatedTime" + delimiter +
                "LocalAET" + delimiter +
                "RemoteAET" + delimiter +
                "DestinationAET" + delimiter +
                "StudyInstanceUID" + delimiter +
                "SeriesInstanceUID" + delimiter +
                "SOPInstanceUID" + delimiter +
                "remaining" + delimiter +
                "completed" + delimiter +
                "failed" + delimiter +
                "warning" + delimiter +
                "statusCode" + delimiter +
                "errorComment" + delimiter +
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
        writer.write(remoteAET);
        writer.write(delimiter);
        writer.write(destinationAET);
        writer.write(delimiter);
        writer.write(studyInstanceUID);
        writer.write(delimiter);
        if (seriesInstanceUID != null)
            writer.write(seriesInstanceUID);
        writer.write(delimiter);
        if (sopInstanceUID != null)
            writer.write(sopInstanceUID);
        writer.write(delimiter);
        writer.write(String.valueOf(remaining));
        writer.write(delimiter);
        writer.write(String.valueOf(completed));
        writer.write(delimiter);
        writer.write(String.valueOf(failed));
        writer.write(delimiter);
        writer.write(String.valueOf(warning));
        writer.write(delimiter);
        if (statusCode != -1)
            writer.write(TagUtils.shortToHexString(statusCode));
        writer.write(delimiter);
        if (errorComment != null) {
            writer.write('"');
            writer.write(errorComment.replace("\"", "\"\""));
            writer.write('"');
        }
        writer.write(delimiter);
        if (queueMessage == null) {
            writer.append(delimiter).write(queueName);
            writer.append(delimiter).write(deviceName);
            writer.append(delimiter).write("TO SCHEDULE");
            writer.append(delimiter).append(delimiter);
            if (batchID != null)
                writer.write(batchID);
            writer.append(delimiter).append(delimiter).append(delimiter).append(delimiter).append(delimiter);
        } else
            queueMessage.writeStatusAsCSVTo(writer, df, delimiter);
        writer.write('\r');
        writer.write('\n');
    }

    @Override
    public String toString() {
        return "RetrieveTask[pk=" + pk
                + ", RetrieveAET=" + remoteAET
                + ", DestinationAET=" + destinationAET
                + "]";
    }
}
