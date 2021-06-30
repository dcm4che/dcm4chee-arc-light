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
import org.dcm4chee.arc.conf.QueueDescriptor;
import org.dcm4chee.arc.conf.StorageVerificationPolicy;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.stream.JsonGenerator;
import javax.persistence.*;
import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Jun 2021
 */
@Entity
@Table(name = "task",
        indexes = {
                @Index(columnList = "device_name"),
                @Index(columnList = "queue_name"),
                @Index(columnList = "task_type"),
                @Index(columnList = "task_status"),
                @Index(columnList = "created_time"),
                @Index(columnList = "updated_time"),
                @Index(columnList = "scheduled_time"),
                @Index(columnList = "batch_id"),
                @Index(columnList = "local_aet"),
                @Index(columnList = "remote_aet"),
                @Index(columnList = "destination_aet"),
                @Index(columnList = "check_missing"),
                @Index(columnList = "check_different"),
                @Index(columnList = "compare_fields"),
                @Index(columnList = "study_iuid, series_iuid, sop_iuid")
        })
@NamedQuery(name = Task.FIND_SCHEDULED_BY_DEVICE_AND_QUEUE_NAME_AND_STATUS,
        query = "select o.pk from Task o where o.deviceName=?1 and o.queueName=?2 and o.status=?3 " +
                "and o.scheduledTime < current_timestamp order by o.scheduledTime")
@NamedQuery(name = Task.FIND_BY_EXPORTER_ID_AND_STUDY_IUID,
        query = "select o from Task o where o.exporterID=?1 and o.studyInstanceUID=?2 " +
                "and o.status=?3")
@NamedQuery(name = Task.FIND_BY_EXPORTER_ID_AND_STUDY_IUID_AND_SERIES_IUID,
        query = "select o from Task o where o.exporterID=?1 and o.studyInstanceUID=?2 " +
                "and o.seriesInstanceUID in ('*',?3) " +
                "and o.status=?4")
@NamedQuery(name = Task.FIND_BY_EXPORTER_ID_AND_STUDY_IUID_AND_SERIES_IUID_AND_SOP_IUID,
        query = "select o from Task o where o.exporterID=?1 and o.studyInstanceUID=?2 " +
                "and o.seriesInstanceUID in ('*',?3) and o.sopInstanceUID in ('*',?4) " +
                "and o.status=?5")
@NamedQuery(name = Task.FIND_DEVICE_BY_BATCH_ID,
        query = "select distinct o.deviceName from QueueMessage o where o.batchID=?1 order by o.deviceName")
@NamedQuery(name = Task.COUNT_BY_DEVICE_AND_QUEUE_NAME_AND_STATUS,
        query = "select count(o) from Task o where o.deviceName=?1 and o.queueName=?2 and o.status=?3")
@NamedQuery(name = Task.COUNT_BY_BATCH_ID_AND_STATUS,
        query = "select count(o) from Task o where o.batchID=?1 and o.status=?2")
@NamedQuery(name = Task.UPDATE_STATUS,
        query = "update Task o set o.status = ?1 where o.status=?2 and o.queueName=?3 and o.deviceName=?4")
@NamedQuery(name = Task.UPDATE_STGVER_RESULT_BY_PK,
        query = "update Task o set o.updatedTime=current_timestamp, o.completed=?2, o.failed=?3 where pk=?1")
@NamedQuery(name = Task.UPDATE_RETRIEVE_RESULT_BY_PK,
        query = "update Task o set o.updatedTime=current_timestamp, o.remaining=?2, o.completed=?3, o.failed=?4, " +
                "o.warning=?5, o.statusCode=?6, o.errorComment=?7 where o.pk=?1")
public class Task {
    public static final String FIND_SCHEDULED_BY_DEVICE_AND_QUEUE_NAME_AND_STATUS =
            "Task.FindScheduledByDeviceAndQueueNameAndStatus";
    public static final String FIND_DEVICE_BY_BATCH_ID = "Task.FindDeviceByBatchId";
    public static final String FIND_BY_EXPORTER_ID_AND_STUDY_IUID =
            "Task.FindByExporterIDAndStudyIUID";
    public static final String FIND_BY_EXPORTER_ID_AND_STUDY_IUID_AND_SERIES_IUID =
            "Task.FindByExporterIDAndStudyIUIDAndSeriesIUID";
    public static final String FIND_BY_EXPORTER_ID_AND_STUDY_IUID_AND_SERIES_IUID_AND_SOP_IUID =
            "Task.FindByExporterIDAndStudyIUIDAndSeriesIUIDAndSopInstanceUID";
    public static final String COUNT_BY_DEVICE_AND_QUEUE_NAME_AND_STATUS = "Task.CountByDeviceAndQueueNameAndStatus";
    public static final String COUNT_BY_BATCH_ID_AND_STATUS = "Task.CountByBatchIdAndStatus";
    public static final String UPDATE_STATUS = "Task.UpdateStatus";
    public static final String UPDATE_STGVER_RESULT_BY_PK = "Task.UpdateStgVerResultByPk";
    public static final String UPDATE_RETRIEVE_RESULT_BY_PK = "Task.UpdateRetrieveResultByPk";

    public enum Status {
        SCHEDULED, IN_PROCESS, COMPLETED, WARNING, FAILED, CANCELED;

        public static Status fromString(String s) {
            return Status.valueOf(s.replace(' ', '_'));
        }

        @Override
        public String toString() {
            return name().replace('_', ' ');
        }
    }

    public enum Type {
        EXPORT,
        RETRIEVE,
        MPPS,
        IAN,
        STGCMT_SCP,
        STGCMT_SCU,
        STGVER,
        HL7,
        REST,
        REJECT,
        DIFF
    }

    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;

    @Version
    @Column(name = "version")
    private long version;

    @Basic(optional = false)
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_time", updatable = false)
    private Date createdTime;

    @Basic(optional = false)
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_time")
    private Date updatedTime;

    @Basic(optional = false)
    @Column(name = "device_name")
    private String deviceName;

    @Basic(optional = false)
    @Column(name = "queue_name")
    private String queueName;

    @Basic(optional = false)
    @Column(name = "task_type")
    private Type type;

    @Basic(optional = false)
    @Column(name = "task_status")
    private Status status;

    @Basic(optional = false)
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "scheduled_time")
    private Date scheduledTime;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "proc_start_time")
    private Date processingStartTime;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "proc_end_time")
    private Date processingEndTime;

    @Basic(optional = false)
    @Column(name = "num_failures")
    private int numberOfFailures;

    @Column(name = "batch_id", updatable = false)
    private String batchID;

    @Column(name = "error_msg")
    private String errorMessage;

    @Column(name = "outcome_msg")
    private String outcomeMessage;

    @Column(name = "local_aet")
    private String localAET;

    @Column(name = "remote_aet")
    private String remoteAET;

    @Column(name = "destination_aet")
    private String destinationAET;

    @Column(name = "exporter_id")
    private String exporterID;

    @Column(name = "stgcmt_policy", updatable = false)
    private StorageVerificationPolicy storageVerificationPolicy;

    @Column(name = "update_location_status", updatable = false)
    private Boolean updateLocationStatus;

    @Column(name = "storage_ids", updatable = false)
    private String storageIDs;

    @Column(name = "study_iuid")
    private String studyInstanceUID;

    @Column(name = "series_iuid")
    private String seriesInstanceUID;

    @Column(name = "sop_iuid")
    private String sopInstanceUID;

    @Column(name = "num_instances")
    private Integer numberOfInstances;

    @Column(name = "modalities")
    private String modalities;

    @Column(name = "remaining")
    private int remaining;

    @Column(name = "completed")
    private int completed;

    @Column(name = "failed")
    private int failed;

    @Basic(optional = false)
    @Column(name = "warning")
    private int warning;

    @Column(name = "status_code")
    private int statusCode;

    @Column(name = "error_comment")
    private String errorComment;

    @Column(name = "query_str", updatable = false)
    private String queryString;

    @Column(name = "check_missing", updatable = false)
    private boolean checkMissing;

    @Column(name = "check_different", updatable = false)
    private boolean checkDifferent;

    @Column(name = "compare_fields", updatable = false)
    private String compareFields;

    @Basic(optional = false)
    @Column(name = "matches")
    private int matches;

    @Basic(optional = false)
    @Column(name = "missing")
    private int missing;

    @Basic(optional = false)
    @Column(name = "different")
    private int different;

    @Basic(optional = false)
    @Column(name = "params", length = 4000)
    private String parameters;

    @Column(name = "payload", updatable = false)
    private byte[] payload;

    @OneToMany(cascade= CascadeType.ALL, orphanRemoval = true)
    @JoinTable(
            name = "diff_task_attrs2",
            joinColumns = @JoinColumn(
                    name = "diff_task_fk",
                    referencedColumnName = "pk"),
            inverseJoinColumns = @JoinColumn(
                    name = "dicomattrs_fk",
                    referencedColumnName = "pk")
    )
    private Collection<AttributesBlob> diffTaskAttributes;

    public long getPk() {
        return pk;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Date getProcessingStartTime() {
        return processingStartTime;
    }

    public void setProcessingStartTime(Date processingStartTime) {
        this.processingStartTime = processingStartTime;
    }

    public Date getProcessingEndTime() {
        return processingEndTime;
    }

    public void setProcessingEndTime(Date processingEndTime) {
        this.processingEndTime = processingEndTime;
    }

    public int getNumberOfFailures() {
        return numberOfFailures;
    }

    public int incrementNumberOfFailures() {
        return ++numberOfFailures;
    }

    public void setNumberOfFailures(int numberOfFailures) {
        this.numberOfFailures = numberOfFailures;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage != null ? StringUtils.truncate(errorMessage, 255) : null;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getQueueName() {
        return queueName;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public Date getScheduledTime() {
        return scheduledTime;
    }

    public void setScheduledTime(Date scheduledTime) {
        this.scheduledTime = scheduledTime;
    }

    public String getOutcomeMessage() {
        return outcomeMessage;
    }

    public void setOutcomeMessage(String outcomeMessage) {
        this.outcomeMessage = outcomeMessage != null ? StringUtils.truncate(outcomeMessage, 255) : null;
    }

    public String getBatchID() {
        return batchID;
    }

    public void setBatchID(String batchID) {
        this.batchID = batchID;
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

    public String getPrimaryAET() {
        return remoteAET;
    }

    public void setPrimaryAET(String remoteAET) {
        this.remoteAET = remoteAET;
    }

    public String getSecondaryAET() {
        return destinationAET;
    }

    public void setSecondaryAET(String destinationAET) {
        this.destinationAET = destinationAET;
    }

    public String getExporterID() {
        return exporterID;
    }

    public void setExporterID(String exporterID) {
        this.exporterID = exporterID;
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

    public String getSopInstanceUID() {
        return sopInstanceUID;
    }

    public void setSopInstanceUID(String sopInstanceUID) {
        this.sopInstanceUID = sopInstanceUID;
    }

    public Integer getNumberOfInstances() {
        return numberOfInstances;
    }

    public void setNumberOfInstances(Integer numberOfInstances) {
        this.numberOfInstances = numberOfInstances;
    }

    public String[] getModalities() {
        return StringUtils.split(modalities, '\\');
    }

    public void setModalities(String[] modalities) {
        this.modalities = StringUtils.concat(modalities, '\\');
    }

    public int getRemaining() {
        return remaining;
    }

    public void setRemaining(int remaining) {
        this.remaining = remaining;
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

    public int getWarning() {
        return warning;
    }

    public void setWarning(int warning) {
        this.warning = warning;
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

    public String getQueryString() {
        return queryString;
    }

    public void setQueryString(String queryString) {
        this.queryString = queryString;
    }

    public boolean isCheckMissing() {
        return checkMissing;
    }

    public void setCheckMissing(boolean checkMissing) {
        this.checkMissing = checkMissing;
    }

    public boolean isCheckDifferent() {
        return checkDifferent;
    }

    public void setCheckDifferent(boolean checkDifferent) {
        this.checkDifferent = checkDifferent;
    }

    public String getCompareFields() {
        return compareFields;
    }

    public void setCompareFields(String compareFields) {
        this.compareFields = compareFields;
    }

    public int getMatches() {
        return matches;
    }

    public void setMatches(int matches) {
        this.matches = matches;
    }

    public int getMissing() {
        return missing;
    }

    public void setMissing(int missing) {
        this.missing = missing;
    }

    public int getDifferent() {
        return different;
    }

    public void setDifferent(int different) {
        this.different = different;
    }

    public void resetDiffTask() {
        matches = 0;
        missing = 0;
        different = 0;
    }

    public String getParameters() {
        return parameters;
    }

    public JsonObject getParametersAsJSON() {
        try (JsonReader r = Json.createReader(new StringReader(parameters))) {
            return r.readObject();
        }
    }

    public void setParameters(String parameters) {
        this.parameters = parameters;
    }

    public <T extends Serializable> T getPayload(Class<T> clazz) {
        if (payload == null) return null;
        ByteArrayInputStream bais = new ByteArrayInputStream(payload);
        try (ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (T) ois.readObject();
        } catch (Exception e) {
            throw new RuntimeException("Unexpected Exception", e);
        }
    }

    public void setPayload(Serializable obj) {
        if (obj == null) {
            payload = null;
        } else {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
            try {
                try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                    oos.writeObject(obj);
                }
            } catch (IOException e) {
                throw new RuntimeException("Unexpected Exception", e);
            }
            payload = baos.toByteArray();
        }
    }

    public Collection<AttributesBlob> getDiffTaskAttributes() {
        return diffTaskAttributes;
    }

    public void writeAsJSON(Writer out) throws IOException {
        JsonGenerator gen = Json.createGenerator(out);
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        JsonWriter writer = new JsonWriter(gen);
        gen.writeStartObject();
        writer.writeNotNullOrDef("createdTime", df.format(createdTime), null);
        writer.writeNotNullOrDef("updatedTime", df.format(updatedTime), null);
        writer.writeNotNullOrDef("pk", pk, null);
        writer.writeNotNullOrDef("dicomDeviceName", deviceName, null);
        writer.writeNotNullOrDef("queue", queueName, null);
        writer.writeNotNullOrDef("type", type.toString(), null);
        writer.writeNotNullOrDef("status", status.toString(), null);
        writer.writeNotNullOrDef("batchID", batchID, null);
        writer.writeNotNullOrDef("failures", numberOfFailures, 0);
        writer.writeNotNullOrDef("scheduledTime", df.format(scheduledTime), null);
        if (processingStartTime != null)
            writer.writeNotNullOrDef("processingStartTime", df.format(processingStartTime), null);
        if (processingEndTime != null)
            writer.writeNotNullOrDef("processingEndTime", df.format(processingEndTime), null);
        writer.writeNotNullOrDef("errorMessage", errorMessage, null);
        writer.writeNotNullOrDef("outcomeMessage", outcomeMessage, null);
        gen.flush();
        out.write(',');
        out.write(parameters);
        gen.writeEnd();
        gen.flush();
    }

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

    @Override
    public String toString() {
        return "Task{" +
                "deviceName='" + deviceName + '\'' +
                ", queueName='" + queueName + '\'' +
                ", type=" + type +
                ", status=" + status +
                ", scheduledTime=" + scheduledTime +
                ", numberOfFailures=" + numberOfFailures +
                '}';
    }
}
