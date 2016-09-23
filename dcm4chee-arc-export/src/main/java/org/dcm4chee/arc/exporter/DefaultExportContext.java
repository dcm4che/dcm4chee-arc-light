package org.dcm4chee.arc.exporter;


import org.dcm4chee.arc.qmgt.Outcome;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Oct 2015
 */
public class DefaultExportContext implements ExportContext {

    private final Exporter exporter;
    private String messageID;
    private String studyInstanceUID;
    private String seriesInstanceUID;
    private String sopInstanceUID;
    private String aeTitle;
    private Outcome outcome;
    private boolean onlyStgCmt;
    private boolean onlyIAN;

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

    @Override
    public String getAETitle() {
        return aeTitle;
    }

    @Override
    public void setAETitle(String aeTitle) {
        this.aeTitle = aeTitle;
    }

    @Override
    public Outcome getOutcome() {
        return outcome;
    }

    @Override
    public void setOutcome(Outcome outcome) {
        this.outcome = outcome;
    }

    @Override
    public boolean isOnlyStgCmt() {
        return onlyStgCmt;
    }

    @Override
    public void setOnlyStgCmt(boolean onlyStgCmt) {
        this.onlyStgCmt = onlyStgCmt;
    }

    @Override
    public boolean isOnlyIAN() {
        return onlyIAN;
    }

    @Override
    public void setOnlyIAN(boolean onlyIAN) {
        this.onlyIAN = onlyIAN;
    }
}
