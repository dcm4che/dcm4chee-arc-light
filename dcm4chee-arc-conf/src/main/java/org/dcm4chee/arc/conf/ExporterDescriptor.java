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

    private String exporterID;
    private String description;
    private URI exportURI;
    private String queueName;
    private String aeTitle;
    private String[] ianDestinations = {};
    private String[] retrieveAETitles = {};
    private Availability instanceAvailability;
    private String stgCmtSCPAETitle;
    private ScheduleExpression[] schedules = {};
    private final Map<String, String> properties = new HashMap<>();

    public ExporterDescriptor() {
    }

    public ExporterDescriptor(String exporterID) {
        setExporterID(exporterID);
    }

    public ExporterDescriptor(String exporterID, URI exportURI) {
        this.exporterID = exporterID;
        this.exportURI = exportURI;
    }

    public String getExporterID() {
        return exporterID;
    }

    public void setExporterID(String exporterID) {
        this.exporterID = exporterID;
    }

    public URI getExportURI() {
        return exportURI;
    }

    public void setExportURI(URI exportURI) {
        this.exportURI = exportURI;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public String[] getIanDestinations() {
        return ianDestinations;
    }

    public void setIanDestinations(String... ianDestinations) {
        this.ianDestinations = ianDestinations;
    }

    public String[] getRetrieveAETitles() {
        return retrieveAETitles;
    }

    public void setRetrieveAETitles(String... retrieveAETitles) {
        this.retrieveAETitles = retrieveAETitles;
    }

    public Availability getInstanceAvailability() {
        return instanceAvailability;
    }

    public void setInstanceAvailability(Availability instanceAvailability) {
        this.instanceAvailability = instanceAvailability;
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

    public String getStgCmtSCPAETitle() {
        return stgCmtSCPAETitle;
    }

    public void setStgCmtSCPAETitle(String stgCmtSCPAETitle) {
        this.stgCmtSCPAETitle = stgCmtSCPAETitle;
    }

    @Override
    public String toString() {
        return "ExporterDescriptor{" +
                "exporterID=" + exporterID +
                ", exportURI=" + exportURI +
                ", queueName=" + queueName +
                ", ianDests=" + Arrays.toString(ianDestinations) +
                ", retrieveAETs=" + Arrays.toString(retrieveAETitles) +
                ", availability=" + instanceAvailability +
                ", schedules=" + Arrays.toString(schedules) +
                ", properties=" + properties +
                ", stgCmtSCPAETitle=" + stgCmtSCPAETitle +
                '}';
    }
}
