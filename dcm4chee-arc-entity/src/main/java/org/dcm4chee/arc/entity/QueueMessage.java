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

import org.dcm4che3.util.StringUtils;

import javax.jms.JMSException;
import javax.jms.JMSRuntimeException;
import javax.jms.ObjectMessage;
import javax.json.Json;
import javax.json.stream.JsonParser;
import javax.persistence.*;
import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Sep 2015
 */
@Entity
@Table(name = "queue_msg", uniqueConstraints = @UniqueConstraint(columnNames = "msg_id"),
    indexes = {
        @Index(columnList = "queue_name"),
        @Index(columnList = "msg_status"),
        @Index(columnList = "updated_time")
})
@NamedQueries({
        @NamedQuery(name = QueueMessage.FIND_BY_MSG_ID,
                query = "select o from QueueMessage o where o.messageID=?1"),
        @NamedQuery(name = QueueMessage.FIND_BY_QUEUE_NAME,
                query = "select o from QueueMessage o where o.queueName=?1 order by o.scheduledTime desc"),
        @NamedQuery(name = QueueMessage.FIND_BY_QUEUE_NAME_AND_STATUS,
                query = "select o from QueueMessage o where o.queueName=?1 and o.status=?2 order by o.pk desc"),
        @NamedQuery(name = QueueMessage.DELETE_BY_QUEUE_NAME,
                query = "delete from QueueMessage o where o.queueName=?1"),
        @NamedQuery(name = QueueMessage.DELETE_BY_QUEUE_NAME_AND_STATUS,
                query = "delete from QueueMessage o where o.queueName=?1 and o.status=?2"),
        @NamedQuery(name = QueueMessage.DELETE_BY_QUEUE_NAME_AND_UPDATED_BEFORE,
                query = "delete from QueueMessage o where o.queueName=?1 and o.updatedTime<?2"),
        @NamedQuery(name = QueueMessage.DELETE_BY_QUEUE_NAME_AND_STATUS_AND_UPDATED_BEFORE,
                query = "delete from QueueMessage o where o.queueName=?1 and o.status=?2 and o.updatedTime<?3 ")
})
public class QueueMessage {

    public static final String FIND_BY_MSG_ID = "QueueMessage.FindByMsgId";
    public static final String FIND_BY_QUEUE_NAME = "QueueMessage.FindByQueueName";
    public static final String FIND_BY_QUEUE_NAME_AND_STATUS = "QueueMessage.FindByQueueNameAndStatus";
    public static final String DELETE_BY_QUEUE_NAME = "QueueMessage.DeleteByQueueName";
    public static final String DELETE_BY_QUEUE_NAME_AND_STATUS = "QueueMessage.DeleteByQueueNameAndStatus";
    public static final String DELETE_BY_QUEUE_NAME_AND_UPDATED_BEFORE =
            "QueueMessage.DeleteByQueueNameAndUpdatedBefore";
    public static final String DELETE_BY_QUEUE_NAME_AND_STATUS_AND_UPDATED_BEFORE =
            "QueueMessage.DeleteByQueueNameAndStatusAndUpdatedBefore";

    public enum Status {
        SCHEDULED, IN_PROCESS, COMPLETED, WARNING, FAILED, CANCELED
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

    @Column(name = "queue_name", updatable = false)
    private String queueName;

    @Basic(optional = false)
    @Column(name = "msg_id")
    private String messageID;

    @Basic(optional = false)
    @Column(name = "msg_props", updatable = false, length = 4000)
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

    @Column(name = "error_msg")
    private String errorMessage;

    @Column(name = "outcome_msg")
    private String outcomeMessage;


    public QueueMessage() {
    }

    public QueueMessage(String queueName, ObjectMessage msg) {
        try {
            this.queueName = queueName;
            this.messageID = msg.getJMSMessageID();
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

    public String getMessageID() {
        return messageID;
    }

    public void setMessageID(String messageID) {
        this.messageID = messageID;
    }

    public Date getScheduledTime() {
        return scheduledTime;
    }

    public String getOutcomeMessage() {
        return outcomeMessage;
    }

    public void setOutcomeMessage(String outcomeMessage) {
        this.outcomeMessage = outcomeMessage != null ? StringUtils.truncate(outcomeMessage, 255) : null;
    }

    public void reschedule(ObjectMessage msg, Date date) {
        try {
            this.messageID = msg.getJMSMessageID();
            this.scheduledTime = date;
            this.status = Status.SCHEDULED;
        } catch (JMSException e) {
            throw toJMSRuntimeException(e);
        }
    }

    public void writeAsJSON(Writer out) throws IOException {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
        out.write("{\"id\":\"");
        out.write(messageID);
        out.write("\",\"queue\":\"");
        out.write(queueName);
        out.write("\",\"status\":\"");
        out.write(status.toString());
        out.write("\",\"failures\":");
        out.write(String.valueOf(numberOfFailures));
        out.write(",\"createdTime\":\"");
        out.write(df.format(createdTime));
        out.write("\",\"updatedTime\":\"");
        out.write(df.format(updatedTime));
        out.write("\",\"scheduledTime\":\"");
        out.write(df.format(scheduledTime));
        if (processingStartTime != null) {
            out.write("\",\"processingStartTime\":\"");
            out.write(df.format(processingStartTime));
        }
        if (processingEndTime != null) {
            out.write("\",\"processingEndTime\":\"");
            out.write(df.format(processingEndTime));
        }
        if (errorMessage != null) {
            out.write("\",\"errorMessage\":\"");
            out.write(errorMessage.replace('"', '\''));
        }
        if (outcomeMessage != null) {
            out.write("\",\"outcomeMessage\":\"");
            out.write(outcomeMessage.replace('"', '\''));
        }
        out.write("\",");
        out.write(messageProperties);
        out.write('}');
    }


    private String propertiesOf(ObjectMessage msg) throws JMSException {
        StringBuilder sb = new StringBuilder(512);
        Enumeration<String> names = msg.getPropertyNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (!name.startsWith("JMS")) {
                Object o = msg.getObjectProperty(name);
                boolean quote = o instanceof String;
                sb.append('"').append(name).append('"').append(':');
                if (quote) sb.append('"');
                sb.append(o);
                if (quote) sb.append('"');
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
        scheduledTime = now;
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
