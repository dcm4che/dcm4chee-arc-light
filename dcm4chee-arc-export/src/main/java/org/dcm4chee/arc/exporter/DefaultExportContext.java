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
public class DefaultExportContext implements ExportContext {

    private final Exporter exporter;
    private String messageID;
    private String studyInstanceUID;
    private String seriesInstanceUID;
    private String sopInstanceUID;
    private String aeTitle;
    private Outcome outcome;
    private String submissionSetUID;
    private Attributes xdsiManifest;
    private RegistryResponseType registryResponse;
    private Throwable exception;
    private HttpServletRequestInfo httpServletRequestInfo;

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
    public String getSubmissionSetUID() {
        return submissionSetUID;
    }

    @Override
    public void setSubmissionSetUID(String submissionSetUID) {
        this.submissionSetUID = submissionSetUID;
    }

    @Override
    public Attributes getXDSiManifest() {
        return xdsiManifest;
    }

    @Override
    public void setXDSiManifest(Attributes xdsiManifest) {
        this.xdsiManifest = xdsiManifest;
    }

    @Override
    public RegistryResponseType getXDSiRegistryResponse() {
        return registryResponse;
    }

    @Override
    public void setXDSiRegistryResponse(RegistryResponseType registryResponse) {
        this.registryResponse = registryResponse;
    }

    @Override
    public Throwable getException() {
        return exception;
    }

    @Override
    public void setException(Throwable exception) {
        this.exception = exception;
    }

    @Override
    public HttpServletRequestInfo getHttpServletRequestInfo() {
        return httpServletRequestInfo;
    }

    @Override
    public void setHttpServletRequestInfo(HttpServletRequestInfo httpServletRequestInfo) {
        this.httpServletRequestInfo = httpServletRequestInfo;
    }
}
