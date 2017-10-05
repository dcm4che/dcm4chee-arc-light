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
 * Portions created by the Initial Developer are Copyright (C) 2013
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

package org.dcm4chee.arc.procedure.impl;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.procedure.ProcedureContext;

import javax.servlet.http.HttpServletRequest;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jun 2016
 */
public class ProcedureContextImpl implements ProcedureContext {
    private final HttpServletRequest httpRequest;
    private final Socket socket;
    private final UnparsedHL7Message hl7msg;
    private Patient patient;
    private String studyInstanceUID;
    private Attributes attributes;
    private String eventActionCode;
    private Exception exception;
    private String spsID;
    private Association as;
    private Attributes sourceInstanceRefs;
    private Attributes.UpdatePolicy attributeUpdatePolicy = Attributes.UpdatePolicy.OVERWRITE;

    ProcedureContextImpl(HttpServletRequest httpRequest, Association as, Socket socket,
                         UnparsedHL7Message hl7msg) {
        this.httpRequest = httpRequest;
        this.socket = socket;
        this.as = as;
        this.hl7msg = hl7msg;
    }

    @Override
    public String toString() {
        return httpRequest != null
                ? httpRequest.getRemoteAddr()
                : as != null ? as.toString() : socket.toString();
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
    public HttpServletRequest getHttpRequest() {
        return httpRequest;
    }

    @Override
    public UnparsedHL7Message getUnparsedHL7Message() {
        return hl7msg;
    }

    @Override
    public String getRemoteHostName() {
        return httpRequest != null ? httpRequest.getRemoteHost() : socket.getInetAddress().getHostName();
    }

    @Override
    public String getStudyInstanceUID() {
        return studyInstanceUID;
    }

    @Override
    public Attributes getAttributes() {
        return attributes;
    }

    @Override
    public void setAttributes(Attributes attrs) {
        this.attributes = attrs;
        this.studyInstanceUID = attrs.getString(Tag.StudyInstanceUID);
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
    public void setStudyInstanceUID(String studyUID) {
        this.studyInstanceUID = studyUID;
    }

    @Override
    public Association getAssociation() {
        return as;
    }

    @Override
    public String getSpsID() {
        return spsID;
    }

    @Override
    public void setSpsID(String spsID) {
        this.spsID = spsID;
    }

    @Override
    public Attributes getSourceInstanceRefs() {
        return sourceInstanceRefs;
    }

    @Override
    public void setSourceInstanceRefs(Attributes sourceInstanceRefs) {
        this.sourceInstanceRefs = sourceInstanceRefs;
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
    public Set<String> getSourceSeriesInstanceUIDs() {
        Sequence seq = sourceInstanceRefs != null ? sourceInstanceRefs.getSequence(Tag.ReferencedSeriesSequence) : null;
        if (seq == null)
            return null;

        Set<String> uids = new HashSet<>(seq.size() * 3 / 4 + 1);
        for (Attributes item : seq)
            uids.add(item.getString(Tag.SeriesInstanceUID));

        return uids;
    }
}
