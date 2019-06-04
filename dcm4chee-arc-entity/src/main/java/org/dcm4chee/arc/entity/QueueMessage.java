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
 * Portions created by the Initial Developer are Copyright (C) 2015
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

import org.dcm4che3.conf.json.JsonWriter;
import org.dcm4che3.util.StringUtils;

import javax.jms.JMSException;
import javax.jms.JMSRuntimeException;
import javax.jms.ObjectMessage;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonParser;
import javax.persistence.*;
import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Sep 2015
 */
@Entity
@Table(name = "queue_msg", uniqueConstraints = @UniqueConstraint(columnNames = "msg_id"),
    indexes = {
        @Index(columnList = "device_name"),
        @Index(columnList = "queue_name"),
        @Index(columnList = "msg_status"),
        @Index(columnList = "created_time"),
        @Index(columnList = "updated_time"),
        @Index(columnList = "batch_id")
})
@NamedQueries({
        @NamedQuery(name = QueueMessage.FIND_BY_MSG_ID,
                query = "select o from QueueMessage o where o.messageID=?1"),
        @NamedQuery(name = QueueMessage.FIND_DEVICE_BY_BATCH_ID,
                query = "select distinct o.deviceName from QueueMessage o where o.batchID=?1 order by o.deviceName"),
        @NamedQuery(name = QueueMessage.COUNT_BY_DEVICE_AND_QUEUE_NAME_AND_STATUS,
                query = "select count(o) from QueueMessage o where o.deviceName=?1 and o.queueName=?2 and o.status=?3"),
        @NamedQuery(name = QueueMessage.COUNT_BY_BATCH_ID_AND_STATUS,
                query = "select count(o) from QueueMessage o where o.batchID=?1 and o.status=?2")
})
public class QueueMessage {

    public static final String FIND_BY_MSG_ID = "QueueMessage.FindByMsgId";
    public static final String FIND_DEVICE_BY_BATCH_ID = "QueueMessage.FindDeviceByBatchId";
    public static final String COUNT_BY_DEVICE_AND_QUEUE_NAME_AND_STATUS = "QueueMessage.CountByDeviceAndQueueNameAndStatus";
    public static final String COUNT_BY_BATCH_ID_AND_STATUS = "QueueMessage.CountByBatchIdAndStatus";

    public enum Status {
        SCHEDULED, IN_PROCESS, COMPLETED, WARNING, FAILED, CANCELED, TO_SCHEDULE;

        public static Status fromString(String s) {
            return Status.valueOf(s.replace(' ', '_'));
        }

        @Override
        public String toString() {
            return name().replace('_', ' ');
        }
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
    @Column(name = "priority")
    private int priority;

    @Basic(optional = false)
    @Column(name = "msg_id")
    private String messageID;

    @Basic(optional = false)
    @Column(name = "msg_props", length = 4000)
    private String messageProperties;

    @Basic(optional = false)
    @Column(name = "msg_body", updatable = false)
    private byte[] messageBody;

    @Basic(optional = false)
    @Column(name = "msg_status")
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

    @OneToOne(mappedBy = "queueMessage")
    private ExportTask exportTask;

    @OneToOne(mappedBy = "queueMessage")
    private RetrieveTask retrieveTask;

    @OneToOne(mappedBy = "queueMessage")
    private DiffTask diffTask;

    @OneToOne(mappedBy = "queueMessage")
    private StorageVerificationTask storageVerificationTask;

    public QueueMessage() {
    }

    public QueueMessage(String deviceName, String queueName, ObjectMessage msg, long delay) {
        try {
            this.deviceName = deviceName;
            this.queueName = queueName;
            this.messageID = msg.getJMSMessageID();
            this.priority = msg.getJMSPriority();
            this.scheduledTime = new Date(System.currentTimeMillis() + delay);
            this.messageProperties = propertiesOf(msg);
            this.messageBody = serialize(msg.getObject());
            this.status = Status.SCHEDULED;
        } catch (JMSException e) {
            throw toJMSRuntimeException(e);
        }
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

    public int getPriority() {
        return priority;
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

    public String getQueueName() {
        return queueName;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public String getMessageID() {
        return messageID;
    }

    public void setMessageID(String messageID) {
        this.messageID = messageID;
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

    public ExportTask getExportTask() {
        return exportTask;
    }

    public RetrieveTask getRetrieveTask() {
        return retrieveTask;
    }

    public DiffTask getDiffTask() {
        return diffTask;
    }

    public StorageVerificationTask getStorageVerificationTask() {
        return storageVerificationTask;
    }

    public String getBatchID() {
        return batchID;
    }

    public void setBatchID(String batchID) {
        this.batchID = batchID;
    }

    public void writeAsJSON(Writer out) throws IOException {
        JsonGenerator gen = Json.createGenerator(out);
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        JsonWriter writer = new JsonWriter(gen);
        gen.writeStartObject();
        writer.writeNotNullOrDef("priority", priority, 0);
        writer.writeNotNullOrDef("createdTime", df.format(createdTime), null);
        writer.writeNotNullOrDef("updatedTime", df.format(updatedTime), null);
        writeStatusAsJSONTo(writer, df);
        gen.flush();
        out.write(',');
        out.write(messageProperties);
        gen.writeEnd();
        gen.flush();
    }

    public void writeStatusAsJSONTo(JsonWriter writer, DateFormat df) {
        writer.writeNotNullOrDef("queue", queueName, null);
        writer.writeNotNullOrDef("JMSMessageID", messageID, null);
        writer.writeNotNullOrDef("dicomDeviceName", deviceName, null);
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
    }

    public void writeStatusAsCSVTo(Writer writer, DateFormat df, char delimiter) throws IOException {
        writer.write(messageID);
        writer.write(delimiter);
        writer.write(queueName);
        writer.write(delimiter);
        writer.write(deviceName);
        writer.write(delimiter);
        writer.write(status.toString());
        writer.append(delimiter);
        writer.write(df.format(scheduledTime));
        writer.append(delimiter);
        if (numberOfFailures > 0)
            writer.write(String.valueOf(numberOfFailures));
        writer.append(delimiter);
        if (batchID != null)
            writer.write(batchID);
        writer.append(delimiter);
        if (processingStartTime != null)
            writer.write(df.format(processingStartTime));
        writer.append(delimiter);
        if (processingEndTime != null)
            writer.write(df.format(processingEndTime));
        writer.append(delimiter);
        if (errorMessage != null) {
            writer.write('"');
            writer.write(errorMessage.replace("\"", "\"\""));
            writer.write('"');
        }
        writer.append(delimiter);
        if (outcomeMessage != null) {
            writer.write('"');
            writer.write(outcomeMessage.replace("\"", "\"\""));
            writer.write('"');
        }
    }

    private String propertiesOf(ObjectMessage msg) throws JMSException {
        StringBuilder sb = new StringBuilder(512);
        Enumeration<String> names = msg.getPropertyNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (!name.startsWith("JMS")) {
                Object o = msg.getObjectProperty(name);
                sb.append('"').append(name).append('"').append(':');
                if (o instanceof String)
                    sb.append('"').append(((String) o).replace("\"", "\\\"")).append('"');
                else
                    sb.append(o);
                if (names.hasMoreElements()) sb.append(',');
            }
        }
        return sb.toString();
    }

    public ObjectMessage initProperties(ObjectMessage msg) {
        try {
            int len = messageProperties.length();
            char[] buf = new char[len + 2];
            buf[0] = '{';
            messageProperties.getChars(0, len, buf, 1);
            buf[len+1] = '}';
            try (JsonParser parser = Json.createParser(new CharArrayReader(buf))) {
                parser.next();
                while (parser.next() == JsonParser.Event.KEY_NAME) {
                    String key = parser.getString();
                    switch (parser.next()) {
                        case VALUE_STRING:
                            msg.setStringProperty(key, parser.getString());
                            break;
                        case VALUE_NUMBER:
                            msg.setIntProperty(key, parser.getInt());
                            break;
                        case VALUE_FALSE:
                            msg.setBooleanProperty(key, false);
                            break;
                        case VALUE_TRUE:
                            msg.setBooleanProperty(key, true);
                            break;
                        case VALUE_NULL:
                            msg.setStringProperty(key, null);
                            break;
                        default:
                            throw new IllegalStateException(messageProperties);
                    }
                }
            }
            return msg;
        } catch (JMSException e) {
            throw toJMSRuntimeException(e);
        }
    }

    public Serializable getMessageBody() {
        ByteArrayInputStream bais = new ByteArrayInputStream(messageBody);
        try (ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (Serializable) ois.readObject();
        } catch (Exception e) {
            throw new RuntimeException("Unexpected Exception", e);
        }
    }

    private byte[] serialize(Serializable obj) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
        try {
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(obj);
            }
        } catch (IOException e) {
            throw new RuntimeException("Unexpected Exception", e);
        }
        return baos.toByteArray();
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
        StringWriter w = new StringWriter(256);
        w.write("QueueMessage");
        try {
            writeAsJSON(w);
        } catch (IOException e) {
            return "" + e;
        }
        return w.toString();
    }

    public static JMSRuntimeException toJMSRuntimeException(JMSException e) {
        return new JMSRuntimeException(e.getMessage(), e.getErrorCode(), e.getCause());
    }

}
