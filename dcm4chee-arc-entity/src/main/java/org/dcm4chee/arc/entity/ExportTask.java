package org.dcm4chee.arc.entity;

import javax.persistence.*;
import java.util.Date;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
@Entity
@Table(name = "export_task",
    uniqueConstraints = @UniqueConstraint(columnNames = {"exporter_id", "study_iuid", "series_iuid", "sop_iuid"}),
    indexes = @Index(columnList = "device_name, scheduled_time")
)
@NamedQueries({
        @NamedQuery(name = ExportTask.FIND_SCHEDULED_BY_DEVICE_NAME,
                query = "select o from ExportTask o where o.deviceName=?1 and o.scheduledTime < current_timestamp"),
        @NamedQuery(name = ExportTask.FIND_BY_EXPORTER_ID_AND_STUDY_IUID,
                query = "select o from ExportTask o where o.exporterID=?1 and o.studyInstanceUID=?2"),
        @NamedQuery(name = ExportTask.FIND_BY_EXPORTER_ID_AND_STUDY_IUID_AND_SERIES_IUID,
                query = "select o from ExportTask o where o.exporterID=?1 and o.studyInstanceUID=?2 " +
                        "and o.seriesInstanceUID in ('*',?3)"),
        @NamedQuery(name = ExportTask.FIND_BY_EXPORTER_ID_AND_STUDY_IUID_AND_SERIES_IUID_AND_SOP_IUID,
                query = "select o from ExportTask o where o.exporterID=?1 and o.studyInstanceUID=?2 " +
                        "and o.seriesInstanceUID in ('*',?3) and o.sopInstanceUID in ('*',?4)")
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
    @Column(name = "device_name")
    private String deviceName;

    @Basic(optional = false)
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "scheduled_time")
    private Date scheduledTime;

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

    public long getPk() {
        return pk;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public Date getScheduledTime() {
        return scheduledTime;
    }

    public void setScheduledTime(Date scheduledTime) {
        this.scheduledTime = scheduledTime;
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
}
