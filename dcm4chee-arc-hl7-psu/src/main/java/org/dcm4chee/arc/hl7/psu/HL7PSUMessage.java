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
 * Portions created by the Initial Developer are Copyright (C) 2016
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

package org.dcm4chee.arc.hl7.psu;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.data.Tag;
import org.dcm4che3.hl7.HL7Message;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.util.AttributesFormat;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.entity.HL7PSUTask;
import org.dcm4chee.arc.entity.MPPS;
import org.dcm4chee.arc.entity.Patient;

import java.util.Date;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jan 2017
 */
class HL7PSUMessage {
    private final HL7Segment msh;
    private final HL7Segment orc;
    private final HL7Segment tq1;
    private final HL7Segment obr;
    private HL7Segment pid;
    private HL7Segment pv1;
    private final HL7Message hl7Message;

    HL7PSUMessage(HL7PSUTask task) {
        msh = HL7Segment.makeMSH();
        msh.setField(8, "OMG^O19^OMG_O19");
        orc = new HL7Segment(6);
        orc.setField(0, "ORC");
        orc.setField(1, "SC");
        orc.setField(5, "CM");
        tq1 = new HL7Segment(8);
        tq1.setField(0, "TQ1");
        obr = new HL7Segment(20);
        obr.setField(0, "OBR");
        hl7Message = new HL7Message(4);
        hl7Message.add(msh);
        hl7Message.add(orc);
        hl7Message.add(tq1);
        hl7Message.add(obr);
        MPPS mpps = task.getMpps();
        if (mpps != null)
            setMPPS(mpps.getAttributes());
        else
            setStartDateTime(task.getCreatedTime());
    }

    HL7Message getHL7Message() {
        return hl7Message;
    }

    void setSendingApplicationWithFacility(String sendingApp) {
        msh.setSendingApplicationWithFacility(sendingApp);
    }

    void setReceivingApplicationWithFacility(String receivingApp) {
        msh.setReceivingApplicationWithFacility(receivingApp);
    }

    void setCharacterSet(String hl7cs) {
        msh.setField(17, hl7cs);
    }

    void setPIDSegment(Patient patient) {
        pid = new HL7Segment(9);
        pid.setField(0, "PID");
        pid.setField(3, patient.getPatientID() != null ? patient.getPatientID().getIDWithIssuer().toString() : null);
        pid.setField(5, patient.getPatientName() != null ? patient.getPatientName().toString() : null);
        pid.setField(7, patient.getPatientBirthDate());
        pid.setField(8, patient.getPatientSex());
        hl7Message.add(pid);
    }

    void setPV1Segment() {
        pv1 = new HL7Segment(3);
        pv1.setField(0, "PV1");
        pv1.setField(2, "U");
        hl7Message.add(pv1);
    }

    void setStudy(Attributes studyAttrs, ArchiveAEExtension arcAE) {
        setPlacerOrder(new AttributesFormat(arcAE.hl7PSUPlacerOrderNumber()).format(studyAttrs));
        setFillerOrder(new AttributesFormat(arcAE.hl7PSUFillerOrderNumber()).format(studyAttrs));
        setAccessionNumber(new AttributesFormat(arcAE.hl7PSUAccessionNumber()).format(studyAttrs));
        setRequestedProcedureID(new AttributesFormat(arcAE.hl7PSURequestedProcedureID()).format(studyAttrs));
    }

    private void setMPPS(Attributes mppsAttrs) {
        Attributes ssaAttrs = mppsAttrs.getNestedDataset(Tag.ScheduledStepAttributesSequence);
        setStartDateTime(mppsAttrs.getDate(Tag.PerformedProcedureStepStartDateAndTime));
        setAttributes(ssaAttrs);
    }

    void setAttributes(Attributes attrs) {
        setPlacerOrder(idWithIssuer(attrs, Tag.PlacerOrderNumberImagingServiceRequest, Tag.OrderPlacerIdentifierSequence));
        setFillerOrder(idWithIssuer(attrs, Tag.FillerOrderNumberImagingServiceRequest, Tag.OrderFillerIdentifierSequence));
        setAccessionNumber(idWithIssuer(attrs, Tag.AccessionNumber, Tag.IssuerOfAccessionNumberSequence));
        setRequestedProcedureID(attrs.getString(Tag.RequestedProcedureID));
    }

    private void setStartDateTime(Date dt) {
        tq1.setField(7, HL7Segment.timeStamp(dt));
    }

    private String idWithIssuer(Attributes attrs, int tag, int seqTag) {
        IDWithIssuer value = IDWithIssuer.valueOf(attrs, tag, seqTag);
        return value != null ? value.toString() : null;
    }

    private void setPlacerOrder(String placerOrder) {
        orc.setField(2,  placerOrder);
        obr.setField(2,  placerOrder);
    }

    private void setFillerOrder(String fillerOrder) {
        orc.setField(3,  fillerOrder);
        obr.setField(3,  fillerOrder);
    }

    private void setAccessionNumber(String accessionNumber) {
        obr.setField(18, accessionNumber);
    }

    private void setRequestedProcedureID(String requestedProcedureID) {
        obr.setField(19, requestedProcedureID);
    }

}
