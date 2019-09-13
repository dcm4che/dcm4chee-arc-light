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
 * Java(TM), hosted at https://github.com/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * J4Care.
 * Portions created by the Initial Developer are Copyright (C) 2017
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

package org.dcm4chee.arc.exporter;


import org.dcm4che3.data.Attributes;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.qmgt.Outcome;
import org.dcm4che3.xdsi.RegistryResponseType;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Oct 2015
 */
public class DefaultExportContext implements ExportContext {

    private final Exporter exporter;
    private String messageID;
    private String batchID;
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
    public String getBatchID() {
        return batchID;
    }

    @Override
    public void setBatchID(String batchID) {
        this.batchID = batchID;
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
