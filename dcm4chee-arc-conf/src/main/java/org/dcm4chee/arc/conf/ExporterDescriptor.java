package org.dcm4chee.arc.conf;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
public class ExporterDescriptor {

    private final String exporterID;
    private URI exportURI;
    private String queueName;
    private String aeTitle;
    private ScheduleExpression[] schedules = {};
    private final Map<String, String> properties = new HashMap<>();

    public ExporterDescriptor(String exporterID) {
        this(exporterID, null);
    }

    public ExporterDescriptor(String exporterID, URI exportURI) {
        this.exporterID = exporterID;
        this.exportURI = exportURI;
    }

    public String getExporterID() {
        return exporterID;
    }


    public URI getExportURI() {
        return exportURI;
    }

    public void setExportURI(URI exportURI) {
        this.exportURI = exportURI;
    }

    public String getQueueName() {
        return queueName;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public String getAETitle() {
        return aeTitle;
    }

    public void setAETitle(String aeTitle) {
        this.aeTitle = aeTitle;
    }

    public ScheduleExpression[] getSchedules() {
        return schedules;
    }

    public void setSchedules(ScheduleExpression... schedules) {
        this.schedules = schedules;
    }

    public void setProperty(String name, String value) {
        properties.put(name, value);
    }

    public String getProperty(String name, String defValue) {
        String value = properties.get(name);
        return value != null ? value : defValue;
    }

    public Map<String,String> getProperties() {
        return properties;
    }

    public void setProperties(String[] ss) {
        properties.clear();
        for (String s : ss) {
            int index = s.indexOf('=');
            if (index < 0)
                throw new IllegalArgumentException(s);
            setProperty(s.substring(0, index), s.substring(index+1));
        }
    }

    @Override
    public String toString() {
        return "ExporterDescriptor{" +
                "exporterID=" + exporterID +
                ", exportURI=" + exportURI +
                ", queueName=" + queueName +
                ", schedules=" + Arrays.toString(schedules) +
                ", properties=" + properties +
                '}';
    }


}
