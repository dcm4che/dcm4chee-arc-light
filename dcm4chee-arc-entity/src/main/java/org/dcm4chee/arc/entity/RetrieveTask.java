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
 *  Portions created by the Initial Developer are Copyright (C) 2015-2017
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

import org.dcm4che3.util.TagUtils;

import javax.json.stream.JsonGenerator;
import javax.persistence.*;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2017
 */
@Entity
@Table(name = "retrieve_task",
        indexes = {
                @Index(columnList = "device_name"),
                @Index(columnList = "local_aet"),
                @Index(columnList = "remote_aet"),
                @Index(columnList = "destination_aet"),
                @Index(columnList = "study_iuid") }
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
                        "where o.queueMessage=?1"),
        @NamedQuery(name = RetrieveTask.DELETE_BY_QUEUE_NAME,
                query = "delete from RetrieveTask t where t.queueMessage in " +
                        "(select o from QueueMessage o where o.queueName=?1)"),
        @NamedQuery(name = RetrieveTask.DELETE_BY_QUEUE_NAME_AND_STATUS,
                query = "delete from RetrieveTask t where t.queueMessage in " +
                        "(select o from QueueMessage o where o.queueName=?1 and o.status=?2)"),
        @NamedQuery(name = RetrieveTask.DELETE_BY_QUEUE_NAME_AND_UPDATED_BEFORE,
                query = "delete from RetrieveTask t where t.queueMessage in " +
                        "(select o from QueueMessage o where o.queueName=?1 and o.updatedTime<?2)"),
        @NamedQuery(name = RetrieveTask.DELETE_BY_QUEUE_NAME_AND_STATUS_AND_UPDATED_BEFORE,
                query = "delete from RetrieveTask t where t.queueMessage in " +
                        "(select o from QueueMessage o where o.queueName=?1 and o.status=?2 and o.updatedTime<?3)")
})
public class RetrieveTask {

    public static final String UPDATE_BY_QUEUE_MESSAGE = "RetrieveTask.UpdateByQueueMessage";
    public static final String DELETE_BY_QUEUE_NAME = "RetrieveTask.DeleteByQueueName";
    public static final String DELETE_BY_QUEUE_NAME_AND_STATUS = "RetrieveTask.DeleteByQueueNameAndStatus";
    public static final String DELETE_BY_QUEUE_NAME_AND_UPDATED_BEFORE =
            "RetrieveTask.DeleteByQueueNameAndUpdatedBefore";
    public static final String DELETE_BY_QUEUE_NAME_AND_STATUS_AND_UPDATED_BEFORE =
            "RetrieveTask.DeleteByQueueNameAndStatusAndUpdatedBefore";

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
    @Column(name = "device_name", updatable = false)
    private String deviceName;

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

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
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

    public void writeAsJSONTo(JsonGenerator gen) throws IOException {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        gen.writeStartObject();
        gen.write("pk", pk);
        gen.write("createdTime", df.format(createdTime));
        gen.write("updatedTime", df.format(updatedTime));
        gen.write("dicomDeviceName", deviceName);
        gen.write("StudyInstanceUID", studyInstanceUID);
        if (seriesInstanceUID != null) {
            gen.write("SeriesInstanceUID", seriesInstanceUID);
            if (sopInstanceUID != null) {
                gen.write("SOPInstanceUID", sopInstanceUID);
            }
        }
        if (remaining > 0)
            gen.write("remaining", remaining);
        if (completed > 0)
            gen.write("completed", completed);
        if (failed > 0)
            gen.write("failed", failed);
        if (warning > 0)
            gen.write("warning", warning);
        if (statusCode != -1)
            gen.write("statusCode", TagUtils.shortToHexString(statusCode));
        if (errorComment != null)
            gen.write("errorComment", errorComment);

        queueMessage.writeStatusAsJSONTo(gen, df);
        gen.writeEnd();
        gen.flush();
    }

    @Override
    public String toString() {
        return "RetrieveTask[pk=" + pk
                + ", RetrieveAET=" + remoteAET
                + ", DestinationAET=" + destinationAET
                + "]";
    }
}
