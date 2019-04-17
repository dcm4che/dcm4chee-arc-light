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
import org.dcm4che3.util.StringUtils;

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
 * @since Oct 2015
 */
@Entity
@Table(name = "export_task",
    indexes = {
        @Index(columnList = "device_name"),
        @Index(columnList = "created_time"),
        @Index(columnList = "updated_time"),
        @Index(columnList = "scheduled_time"),
        @Index(columnList = "exporter_id"),
        @Index(columnList = "study_iuid, series_iuid, sop_iuid") }
)
@NamedQueries({
        @NamedQuery(name = ExportTask.FIND_SCHEDULED_BY_DEVICE_NAME,
                query = "select o from ExportTask o where o.deviceName=?1 and o.scheduledTime < current_timestamp " +
                        "and o.queueMessage is null"),
        @NamedQuery(name = ExportTask.FIND_BY_EXPORTER_ID_AND_STUDY_IUID,
                query = "select o from ExportTask o where o.exporterID=?1 and o.studyInstanceUID=?2 " +
                        "and o.queueMessage is null"),
        @NamedQuery(name = ExportTask.FIND_BY_EXPORTER_ID_AND_STUDY_IUID_AND_SERIES_IUID,
                query = "select o from ExportTask o where o.exporterID=?1 and o.studyInstanceUID=?2 " +
                        "and o.seriesInstanceUID in ('*',?3) " +
                        "and o.queueMessage is null"),
        @NamedQuery(name = ExportTask.FIND_BY_EXPORTER_ID_AND_STUDY_IUID_AND_SERIES_IUID_AND_SOP_IUID,
                query = "select o from ExportTask o where o.exporterID=?1 and o.studyInstanceUID=?2 " +
                        "and o.seriesInstanceUID in ('*',?3) and o.sopInstanceUID in ('*',?4) " +
                        "and o.queueMessage is null"),
        @NamedQuery(name = ExportTask.FIND_DEVICE_BY_PK,
                query = "select o.deviceName from ExportTask o where o.pk=?1"),
        @NamedQuery(name = ExportTask.FIND_STUDY_EXPORT_AFTER,
                query = "select o from ExportTask o where o.updatedTime > ?1 and o.exporterID=?2 " +
                        "and o.studyInstanceUID=?3 and o.seriesInstanceUID='*'")
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
    public static final String FIND_DEVICE_BY_PK = "ExportTask.FindDeviceByPk";
    public static final String FIND_STUDY_EXPORT_AFTER = "ExportTask.FindStudyExportAfter";

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

    @Basic(optional = false)
    @Column(name = "device_name")
    private String deviceName;

    @Basic(optional = false)
    @Column(name = "exporter_id")
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

    @OneToOne(cascade=CascadeType.ALL, orphanRemoval = true)
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

    public Date getScheduledTime() {
        return scheduledTime;
    }

    public void setScheduledTime(Date scheduledTime) {
        this.scheduledTime = scheduledTime;
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

    public QueueMessage getQueueMessage() {
        return queueMessage;
    }

    public void setQueueMessage(QueueMessage queueMessage) {
        this.queueMessage = queueMessage;
    }

    public void writeAsJSONTo(JsonGenerator gen, String localAET) {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        JsonWriter writer = new JsonWriter(gen);
        gen.writeStartObject();
        writer.writeNotNullOrDef("pk", pk, null);
        writer.writeNotNullOrDef("createdTime", df.format(createdTime), null);
        writer.writeNotNullOrDef("updatedTime", df.format(updatedTime), null);
        writer.writeNotNullOrDef("ExporterID", exporterID, null);
        writer.writeNotNullOrDef("LocalAET", localAET, null);
        writer.writeNotNullOrDef("StudyInstanceUID", studyInstanceUID, null);
        writer.writeNotNullOrDef("SeriesInstanceUID", seriesInstanceUID, "*");
        writer.writeNotNullOrDef("SOPInstanceUID", sopInstanceUID, "*");
        writer.writeNotNullOrDef("NumberOfInstances", numberOfInstances, null);
        writer.writeNotEmpty("Modality", getModalities());
        if (queueMessage == null) {
            writer.writeNotNullOrDef("dicomDeviceName", deviceName, null);
            writer.writeNotNullOrDef("status", QueueMessage.Status.TO_SCHEDULE.toString(), null);
            writer.writeNotNullOrDef("scheduledTime", df.format(scheduledTime), null);
        } else
            queueMessage.writeStatusAsJSONTo(writer, df);
        gen.writeEnd();
        gen.flush();
    }

    public static void writeCSVHeader(Writer writer, char delimiter) throws IOException {
         writer.write("pk" + delimiter +
                "createdTime" + delimiter +
                "updatedTime" + delimiter +
                "ExporterID" + delimiter +
                "LocalAET" + delimiter +
                "StudyInstanceUID" + delimiter +
                "SeriesInstanceUID" + delimiter +
                "SOPInstanceUID" + delimiter +
                "NumberOfInstances" + delimiter +
                "Modality" + delimiter +
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

    public void writeAsCSVTo(Writer writer, char delimiter, String localAET) throws IOException {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        writer.write(String.valueOf(pk));
        writer.write(delimiter);
        writer.write(df.format(createdTime));
        writer.write(delimiter);
        writer.write(df.format(updatedTime));
        writer.write(delimiter);
        writer.write(exporterID);
        writer.write(delimiter);
        writer.write(localAET);
        writer.write(delimiter);
        writer.write(studyInstanceUID);
        writer.write(delimiter);
        if (!seriesInstanceUID.equals("*"))
            writer.write(seriesInstanceUID);
        writer.write(delimiter);
        if (!sopInstanceUID.equals("*"))
            writer.write(sopInstanceUID);
        writer.write(delimiter);
        if (numberOfInstances != null)
            writer.write(numberOfInstances.toString());
        writer.write(delimiter);
        writer.write(modalities);
        writer.write(delimiter);
        if (queueMessage == null) {
            writer.append(delimiter).append(delimiter).write(deviceName);
            writer.append(delimiter).write("TO SCHEDULE");
            writer.append(delimiter).write(df.format(scheduledTime));
            writer.append(delimiter).append(delimiter).append(delimiter).append(delimiter).append(delimiter).append(delimiter);
        } else {
            queueMessage.writeStatusAsCSVTo(writer, df, delimiter);
        }
        writer.write('\r');
        writer.write('\n');
    }

    @Override
    public String toString() {
        return "ExportTask[pk=" + pk
                + ", exporterID=" + exporterID
                + ", studyUID=" + studyInstanceUID
                + ", seriesUID=" + seriesInstanceUID
                + ", objectUID=" + sopInstanceUID
                + "]";
    }
}
