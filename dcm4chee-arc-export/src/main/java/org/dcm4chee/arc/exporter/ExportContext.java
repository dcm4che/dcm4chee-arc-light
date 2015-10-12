package org.dcm4chee.arc.exporter;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
public interface ExportContext {
    Exporter getExporter();

    String getStudyInstanceUID();

    void setStudyInstanceUID(String studyInstanceUID);

    String getSeriesInstanceUID();

    void setSeriesInstanceUID(String seriesInstanceUID);

    String getSopInstanceUID();

    void setSopInstanceUID(String sopInstanceUID);
}
