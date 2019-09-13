/*
 * ** BEGIN LICENSE BLOCK *****
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
 * Portions created by the Initial Developer are Copyright (C) 2015-2017
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
 * ** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.patient.impl;

import org.dcm4che3.audit.AuditMessages;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4che3.soundex.FuzzyStr;
import org.dcm4che3.util.ReverseDNS;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.ArchiveHL7ApplicationExtension;
import org.dcm4chee.arc.conf.AttributeFilter;
import org.dcm4chee.arc.conf.Entity;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.patient.PatientMgtContext;

import java.net.Socket;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Mar 2016
 */
public class PatientMgtContextImpl implements PatientMgtContext {

    private final AttributeFilter attributeFilter;
    private final AttributeFilter studyAttributeFilter;
    private final FuzzyStr fuzzyStr;
    private HL7Application hl7app;
    private Association as;
    private Socket socket;
    private UnparsedHL7Message msg;
    private IDWithIssuer patientID;
    private Attributes attributes;
    private IDWithIssuer previousPatientID;
    private Attributes previousAttributes;
    private Attributes.UpdatePolicy attributeUpdatePolicy = Attributes.UpdatePolicy.OVERWRITE;
    private String eventActionCode = AuditMessages.EventActionCode.Read;
    private Exception exception;
    private Patient patient;
    private HttpServletRequestInfo httpServletRequestInfo;
    private Patient.VerificationStatus patientVerificationStatus = Patient.VerificationStatus.UNVERIFIED;
    private String pdqServiceURI;

    PatientMgtContextImpl(Device device) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        this.attributeFilter = arcDev.getAttributeFilter(Entity.Patient);
        this.studyAttributeFilter = arcDev.getAttributeFilter(Entity.Study);
        this.fuzzyStr = arcDev.getFuzzyStr();
    }

    void setHL7Application(HL7Application hl7app) {
        this.hl7app = hl7app;
    }

    void setSocket(Socket socket) {
        this.socket = socket;
    }

    void setAssociation(Association as) {
        this.as = as;
        this.socket = as.getSocket();
    }

    @Override
    public String toString() {
        return as != null
                ? as.toString()
                : httpServletRequestInfo != null
                    ? httpServletRequestInfo.requesterHost
                    : socket != null
                        ? socket.toString()
                        : "PatientMgtContext";
    }

    @Override
    public AttributeFilter getAttributeFilter() {
        return attributeFilter;
    }

    @Override
    public AttributeFilter getStudyAttributeFilter() {
        return studyAttributeFilter;
    }

    @Override
    public FuzzyStr getFuzzyStr() {
        return fuzzyStr;
    }

    @Override
    public Association getAssociation() {
        return as;
    }

    @Override
    public UnparsedHL7Message getUnparsedHL7Message() {
        return msg;
    }

    @Override
    public void setUnparsedHL7Message(UnparsedHL7Message msg) {
        this.msg = msg;
    }

    @Override
    public String getRemoteHostName() {
        return httpServletRequestInfo != null
                ? httpServletRequestInfo.requesterHost
                : socket != null
                    ? ReverseDNS.hostNameOf(socket.getInetAddress()) : null;
    }

    @Override
    public boolean isNoPatientCreate() {
        if (hl7app == null)
            return false;

        ArchiveHL7ApplicationExtension arcHL7App =
                hl7app.getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class);
        return arcHL7App != null && arcHL7App.isHL7NoPatientCreateMessageType(msg.msh().getMessageType());
    }

    @Override
    public IDWithIssuer getPatientID() {
        return patientID;
    }

    @Override
    public Attributes getAttributes() {
        return attributes;
    }

    @Override
    public void setAttributes(Attributes attrs) {
        this.attributes = attrs;
        this.patientID = IDWithIssuer.pidOf(attrs);
    }

    @Override
    public IDWithIssuer getPreviousPatientID() {
        return previousPatientID;
    }

    @Override
    public Attributes getPreviousAttributes() {
        return previousAttributes;
    }

    @Override
    public void setPreviousAttributes(Attributes attrs) {
        this.previousAttributes = attrs;
        this.previousPatientID = attrs != null ? IDWithIssuer.pidOf(attrs) : null;
    }

    @Override
    public Attributes.UpdatePolicy getAttributeUpdatePolicy() {
        return attributeUpdatePolicy;
    }

    @Override
    public void setAttributeUpdatePolicy(Attributes.UpdatePolicy updatePolicy) {
        this.attributeUpdatePolicy = updatePolicy;
    }

    @Override
    public String getEventActionCode() {
        return eventActionCode;
    }

    @Override
    public void setEventActionCode(String eventActionCode) {
        this.eventActionCode = eventActionCode;
    }

    @Override
    public Exception getException() {
        return exception;
    }

    @Override
    public void setException(Exception exception) {
        this.exception = exception;
    }

    @Override
    public void setPatientID(IDWithIssuer patientID) {
        this.patientID = patientID;
    }

    @Override
    public Patient getPatient() {
        return patient;
    }

    @Override
    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    @Override
    public HttpServletRequestInfo getHttpServletRequestInfo() {
        return httpServletRequestInfo;
    }

    @Override
    public void setHttpServletRequestInfo(HttpServletRequestInfo httpServletRequestInfo) {
        this.httpServletRequestInfo = httpServletRequestInfo;
    }

    @Override
    public Patient.VerificationStatus getPatientVerificationStatus() {
        return patientVerificationStatus;
    }

    @Override
    public void setPatientVerificationStatus(Patient.VerificationStatus patientVerificationStatus) {
        this.patientVerificationStatus = patientVerificationStatus;
    }

    @Override
    public String getPDQServiceURI() {
        return pdqServiceURI;
    }

    @Override
    public void setPDQServiceURI(String pdqServiceURI) {
        this.pdqServiceURI = pdqServiceURI;
    }
}
