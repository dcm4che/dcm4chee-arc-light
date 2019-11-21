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
import org.dcm4chee.arc.entity.HL7PSUTask;
import org.dcm4chee.arc.entity.MPPS;

import java.util.Date;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jan 2017
 */
class HL7PSUMessage {
    private final HL7Segment msh;
    private final HL7Segment orc;
    private final HL7Segment tq1;
    private final HL7Segment obr;
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

    void setMWLItem(Attributes mwlAttrs) {
        setPlacerOrder(mwlAttrs);
        setFillerOrder(mwlAttrs);
        setAccessionNumber(mwlAttrs);
        setRequestedProcedureID(mwlAttrs);
    }

    private void setMPPS(Attributes mppsAttrs) {
        Attributes ssaAttrs = mppsAttrs.getNestedDataset(Tag.ScheduledStepAttributesSequence);
        setStartDateTime(mppsAttrs.getDate(Tag.PerformedProcedureStepStartDateAndTime));
        setPlacerOrder(ssaAttrs);
        setFillerOrder(ssaAttrs);
        setAccessionNumber(ssaAttrs);
        setRequestedProcedureID(ssaAttrs);
    }

    private void setStartDateTime(Date dt) {
        tq1.setField(7, HL7Segment.timeStamp(dt));
    }

    private void setPlacerOrder(Attributes attrs) {
        IDWithIssuer placer = IDWithIssuer.valueOf(
                attrs, Tag.PlacerOrderNumberImagingServiceRequest, Tag.OrderPlacerIdentifierSequence);
        String value = placer != null ? placer.toString() : null;
        orc.setField(2,  value);
        obr.setField(2,  value);
    }

    private void setFillerOrder(Attributes attrs) {
        IDWithIssuer filler = IDWithIssuer.valueOf(
                attrs, Tag.FillerOrderNumberImagingServiceRequest, Tag.OrderFillerIdentifierSequence);
        String value = filler != null ? filler.toString() : null;
        orc.setField(3,  value);
        obr.setField(3,  value);
    }

    private void setAccessionNumber(Attributes attrs) {
        IDWithIssuer accession = IDWithIssuer.valueOf(attrs, Tag.AccessionNumber, Tag.IssuerOfAccessionNumberSequence);
        obr.setField(18, accession != null ? accession.toString() : null);
    }

    private void setRequestedProcedureID(Attributes attrs) {
        obr.setField(19, attrs.getString(Tag.RequestedProcedureID));
    }

}
