package org.dcm4chee.arc.exporter;

import org.dcm4chee.arc.conf.Entity;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
public class DefaultExportContext implements ExportContext {

    private final Exporter exporter;
    private String messageID;
    private String studyInstanceUID;
    private String seriesInstanceUID;
    private String sopInstanceUID;

    public DefaultExportContext(Exporter exporter) {
        this.exporter = exporter;
    }

    @Override
    public Exporter getExporter() {
        return exporter;
    }

    @Override
    public String getMessageID() {
        return messageID;
    }

    @Override
    public void setMessageID(String messageID) {
        this.messageID = messageID;
    }

    @Override
    public String getStudyInstanceUID() {
        return studyInstanceUID;
    }

    @Override
    public void setStudyInstanceUID(String studyInstanceUID) {
        this.studyInstanceUID = studyInstanceUID;
    }

    @Override
    public String getSeriesInstanceUID() {
        return seriesInstanceUID;
    }

    @Override
    public void setSeriesInstanceUID(String seriesInstanceUID) {
        this.seriesInstanceUID = seriesInstanceUID;
    }

    @Override
    public String getSopInstanceUID() {
        return sopInstanceUID;
    }

    @Override
    public void setSopInstanceUID(String sopInstanceUID) {
        this.sopInstanceUID = sopInstanceUID;
    }

}
