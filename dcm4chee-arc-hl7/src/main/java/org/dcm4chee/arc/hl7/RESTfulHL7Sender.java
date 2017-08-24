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

package org.dcm4chee.arc.hl7;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.data.Tag;
import org.dcm4che3.hl7.HL7Message;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.patient.PatientMgtContext;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class RESTfulHL7Sender {

    @Inject
    private Device device;

    @Inject
    private HL7Sender hl7Sender;

    public void sendHL7Message(String msgType, PatientMgtContext ctx) throws ConfigurationException {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        if (arcDev.getHl7ADTSendingApplication() != null) {
            HL7Msg msg = new HL7Msg(msgType, ctx);
            msg.setSendingApplicationWithFacility(arcDev.getHl7ADTSendingApplication());
            for (String receiver : arcDev.getHl7ADTReceivingApplication()) {
                msg.setReceivingApplicationWithFacility(receiver);
                hl7Sender.scheduleMessage(msg.getHL7Message());
            }
        }
    }

    public void scheduleHL7Message(String msgType, PatientMgtContext ctx, String sender, String receiver) throws ConfigurationException {
        HL7Msg msg = new HL7Msg(msgType, ctx);
        msg.setSendingApplicationWithFacility(sender);
        msg.setReceivingApplicationWithFacility(receiver);
        hl7Sender.scheduleMessage(msg.getHL7Message());
    }

    public HL7Message sendHL7Message(String msgType, PatientMgtContext ctx, String sender, String receiver) throws Exception {
        HL7Msg msg = new HL7Msg(msgType, ctx);
        msg.setSendingApplicationWithFacility(sender);
        msg.setReceivingApplicationWithFacility(receiver);

        HL7Segment msh = msg.getHL7Message().getSegment("MSH");
        return hl7Sender.sendMessage(
                msh.getField(2, ""),
                msh.getField(3, ""),
                msh.getField(4, ""),
                msh.getField(5, ""),
                msh.getField(8, ""),
                msh.getField(9, ""),
                msg.getHL7Message().getBytes(null));
    }

    private class HL7Msg {
        private final HL7Segment msh;
        private final HL7Segment pid;
        private final HL7Segment mrg;
        private final HL7Message hl7Message;

        HL7Msg(String msgType, PatientMgtContext ctx) {
            IDWithIssuer patientID = IDWithIssuer.pidOf(ctx.getAttributes());
            msh = HL7Segment.makeMSH();
            msh.setField(8, msgType);
            pid = new HL7Segment(8);
            pid.setField(0, "PID");
            pid.setField(3, patientID != null ? patientID.toString() : null);
            pid.setField(5, ctx.getAttributes().getString(Tag.PatientName));
            pid.setField(6, ctx.getAttributes().getString(Tag.PatientMotherBirthName));
            pid.setField(7, ctx.getAttributes().getString(Tag.PatientBirthDate));
            pid.setField(8, ctx.getAttributes().getString(Tag.PatientSex));
            mrg = new HL7Segment(2);
            hl7Message = new HL7Message(3);
            hl7Message.add(msh);
            hl7Message.add(pid);
            if (ctx.getPreviousPatientID() != null) {
                mrg.setField(0, "MRG");
                mrg.setField(1, ctx.getPreviousPatientID().toString());
                mrg.setField(7, ctx.getPreviousAttributes().getString(Tag.PatientName));
                hl7Message.add(mrg);
            }
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
    }
}
