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
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * J4Care.
 * Portions created by the Initial Developer are Copyright (C) 2015
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

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Device;
import org.dcm4che3.soundex.FuzzyStr;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.AttributeFilter;
import org.dcm4chee.arc.conf.Entity;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.patient.PatientMgtContext;

import javax.servlet.http.HttpServletRequest;
import java.net.Socket;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Mar 2016
 */
public class PatientMgtContextImpl implements PatientMgtContext {

    private final AttributeFilter attributeFilter;
    private final FuzzyStr fuzzyStr;
    private final HttpServletRequest httpRequest;
    private final ApplicationEntity ae;
    private final Association as;
    private final Socket socket;
    private final HL7Segment msh;
    private IDWithIssuer patientID;
    private Attributes attributes;
    private IDWithIssuer previousPatientID;
    private Attributes previousAttributes;
    private Attributes.UpdatePolicy attributeUpdatePolicy = Attributes.UpdatePolicy.OVERWRITE;
    private String eventActionCode;
    private Exception exception;
    private Patient patient;

    PatientMgtContextImpl(Device device, HttpServletRequest httpRequest, Association as, ApplicationEntity ae,
                          Socket socket, HL7Segment msh) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        this.attributeFilter = arcDev.getAttributeFilter(Entity.Patient);
        this.fuzzyStr = arcDev.getFuzzyStr();
        this.httpRequest = httpRequest;
        this.ae = ae;
        this.as = as;
        this.socket = socket;
        this.msh = msh;
    }

    @Override
    public String toString() {
        return as != null ? as.toString()
                : httpRequest != null ? httpRequest.getRemoteAddr()
                : socket.toString();
    }

    @Override
    public AttributeFilter getAttributeFilter() {
        return attributeFilter;
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
    public HttpServletRequest getHttpRequest() {
        return httpRequest;
    }

    @Override
    public HL7Segment getHL7MessageHeader() {
        return msh;
    }

    @Override
    public String getCalledAET() {
        return as != null ? as.getCalledAET() : ae != null ? ae.getAETitle() : null;
    }

    @Override
    public String getCallingAET() {
        return as != null ? as.getCallingAET() : null;
    }

    @Override
    public String getRemoteHostName() {
        return httpRequest != null ? httpRequest.getRemoteHost() : socket.getInetAddress().getHostName();
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
}
