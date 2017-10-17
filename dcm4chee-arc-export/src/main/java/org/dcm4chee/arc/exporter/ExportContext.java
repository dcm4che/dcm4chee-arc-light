package org.dcm4chee.arc.exporter;

import org.dcm4che3.data.Attributes;
import org.dcm4chee.arc.qmgt.Outcome;
import org.dcm4chee.arc.retrieve.HttpServletRequestInfo;
import org.dcm4chee.arc.xdsi.RegistryResponseType;

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

    String getSubmissionSetUID();

    void setSubmissionSetUID(String submissionSetUID);

    Attributes getXDSiManifest();

    void setXDSiManifest(Attributes xdsiManifest);

    Throwable getException();

    void setException(Throwable exception);

    RegistryResponseType getXDSiRegistryResponse();

    void setXDSiRegistryResponse(RegistryResponseType registryResponse);

    HttpServletRequestInfo getHttpServletRequestInfo();

    void setHttpServletRequestInfo(HttpServletRequestInfo httpServletRequestInfo);
}
