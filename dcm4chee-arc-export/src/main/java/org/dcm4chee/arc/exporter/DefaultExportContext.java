package org.dcm4chee.arc.exporter;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
public class DefaultExportContext implements ExportContext {

    private final Exporter exporter;
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
