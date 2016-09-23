package org.dcm4chee.arc.exporter;

import org.dcm4chee.arc.qmgt.Outcome;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Oct 2015
 */
public interface ExportContext {
    Exporter getExporter();

    String getMessageID();

    void setMessageID(String messageID);

    String getStudyInstanceUID();

    void setStudyInstanceUID(String studyInstanceUID);

    String getSeriesInstanceUID();

    void setSeriesInstanceUID(String seriesInstanceUID);

    String getSopInstanceUID();

    void setSopInstanceUID(String sopInstanceUID);

    String getAETitle();

    void setAETitle(String aeTitle);

    Outcome getOutcome();

    void setOutcome(Outcome outcome);

    boolean isOnlyStgCmt();

    void setOnlyStgCmt(boolean onlyStgCmt);

    boolean isOnlyIAN();

    void setOnlyIAN(boolean onlyIAN);
}
