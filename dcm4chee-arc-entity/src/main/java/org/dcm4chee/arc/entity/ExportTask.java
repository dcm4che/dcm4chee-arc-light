package org.dcm4chee.arc.entity;

import org.dcm4che3.util.StringUtils;

import javax.persistence.*;
import java.util.Date;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
@Entity
@Table(name = "export_task",
    indexes = {
        @Index(columnList = "device_name"),
        @Index(columnList = "updated_time"),
        @Index(columnList = "scheduled_time"),
        @Index(columnList = "exporter_id"),
        @Index(columnList = "study_iuid, series_iuid, sop_iuid"),
        @Index(columnList = "msg_status") }
)
@NamedQueries({
        @NamedQuery(name = ExportTask.FIND_SCHEDULED_BY_DEVICE_NAME,
                query = "select o from ExportTask o where o.deviceName=?1 and o.scheduledTime < current_timestamp " +
                        "and o.status = org.dcm4chee.arc.entity.QueueMessage$Status.TO_SCHEDULE"),
        @NamedQuery(name = ExportTask.FIND_BY_EXPORTER_ID_AND_STUDY_IUID,
                query = "select o from ExportTask o where o.exporterID=?1 and o.studyInstanceUID=?2 " +
                        "and o.status = org.dcm4chee.arc.entity.QueueMessage$Status.TO_SCHEDULE"),
        @NamedQuery(name = ExportTask.FIND_BY_EXPORTER_ID_AND_STUDY_IUID_AND_SERIES_IUID,
                query = "select o from ExportTask o where o.exporterID=?1 and o.studyInstanceUID=?2 " +
                        "and o.seriesInstanceUID in ('*',?3) " +
                        "and o.status = org.dcm4chee.arc.entity.QueueMessage$Status.TO_SCHEDULE"),
        @NamedQuery(name = ExportTask.FIND_BY_EXPORTER_ID_AND_STUDY_IUID_AND_SERIES_IUID_AND_SOP_IUID,
                query = "select o from ExportTask o where o.exporterID=?1 and o.studyInstanceUID=?2 " +
                        "and o.seriesInstanceUID in ('*',?3) and o.sopInstanceUID in ('*',?4) " +
                        "and o.status = org.dcm4chee.arc.entity.QueueMessage$Status.TO_SCHEDULE")
})
public class ExportTask {

    public static final String FIND_SCHEDULED_BY_DEVICE_NAME =
            "ExportTask.FindScheduledByDeviceName";
    public static final String FIND_BY_EXPORTER_ID_AND_STUDY_IUID =
            "ExportTask.FindByExporterIDAndStudyIUID";
    public static final String FIND_BY_EXPORTER_ID_AND_STUDY_IUID_AND_SERIES_IUID =
            "ExportTask.FindByExporterIDAndStudyIUIDAndSeriesIUID";
    public static final String FIND_BY_EXPORTER_ID_AND_STUDY_IUID_AND_SERIES_IUID_AND_SOP_IUID =
            "ExportTask.FindByExporterIDAndStudyIUIDAndSeriesIUIDAndSopInstanceUID";

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
    @Column(name = "device_name")
    private String deviceName;

    @Basic(optional = false)
    @Column(name = "exporter_id", updatable = false)
    private String exporterID;

    @Basic(optional = false)
    @Column(name = "study_iuid", updatable = false)
    private String studyInstanceUID;

    @Basic(optional = false)
    @Column(name = "series_iuid")
    private String seriesInstanceUID;

    @Basic(optional = false)
    @Column(name = "sop_iuid")
    private String sopInstanceUID;

    @Column(name = "num_instances")
    private Integer numberOfInstances;

    @Column(name = "modalities")
    private String modalities;

    @Column(name = "msg_id")
    private String messageID;

    @Basic(optional = false)
    @Column(name = "msg_status")
    private QueueMessage.Status status;

    @Basic(optional = false)
    @Column(name = "num_failures")
    private int numberOfFailures;

    @Column(name = "error_msg")
    private String errorMessage;

    @Column(name = "outcome_msg")
    private String outcomeMessage;

    public long getPk() {
        return pk;
    }

    public Date getCreatedTime() {
        return createdTime;
    }

    public Date getUpdatedTime() {
        return updatedTime;
    }

    public Date getScheduledTime() {
        return scheduledTime;
    }

    public void setScheduledTime(Date scheduledTime) {
        this.scheduledTime = scheduledTime;
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

    public QueueMessage.Status getStatus() {
        return status;
    }

    public void setStatus(QueueMessage.Status status) {
        this.status = status;
    }

    public int getNumberOfFailures() {
        return numberOfFailures;
    }

    public void setNumberOfFailures(int numberOfFailures) {
        this.numberOfFailures = numberOfFailures;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getOutcomeMessage() {
        return outcomeMessage;
    }

    public void setOutcomeMessage(String outcomeMessage) {
        this.outcomeMessage = outcomeMessage;
    }

    public String getMessageID() {
        return messageID;
    }

    public void setMessageID(String messageID) {
        this.messageID = messageID;
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
}
