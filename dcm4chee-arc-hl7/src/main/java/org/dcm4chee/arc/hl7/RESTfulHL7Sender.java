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

import org.dcm4che3.hl7.HL7Message;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.HL7DeviceExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.ArchiveHL7ApplicationExtension;
import org.dcm4chee.arc.patient.PatientMgtContext;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.xml.transform.Transformer;
import java.util.Date;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Aug 2017
 */

@ApplicationScoped
public class RESTfulHL7Sender {

    @Inject
    private Device device;

    @Inject
    private HL7Sender hl7Sender;

    public void sendHL7Message(String msgType, PatientMgtContext ctx) throws Exception {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        String sender = arcDev.getHl7ADTSendingApplication();
        if (sender != null) 
            for (String receiver : arcDev.getHl7ADTReceivingApplication()) 
                scheduleHL7Message(msgType, ctx, sender, receiver);
    }

    public void scheduleHL7Message(String msgType, PatientMgtContext ctx, String sender, String receiver) throws Exception {
        HL7Msg msg = new HL7Msg(sender, receiver);
        byte[] hl7MsgData = hl7MsgData(msgType, ctx, msg);

        hl7Sender.scheduleMessage(
                msg.sendingAppWithFacility[0],
                msg.sendingAppWithFacility[1],
                msg.receivingAppWithFacility[0],
                msg.receivingAppWithFacility[1],
                msgType,
                msg.msgControlID,
                hl7MsgData);
    }

    public HL7Message sendHL7Message(String msgType, PatientMgtContext ctx, String sender, String receiver) throws Exception {
        HL7Msg msg = new HL7Msg(sender, receiver);
        byte[] hl7MsgData = hl7MsgData(msgType, ctx, msg);

        return hl7Sender.sendMessage(
                msg.sendingAppWithFacility[0],
                msg.sendingAppWithFacility[1],
                msg.receivingAppWithFacility[0],
                msg.receivingAppWithFacility[1],
                msgType,
                msg.msgControlID,
                hl7MsgData);
    }

    private byte[] hl7MsgData(String msgType, PatientMgtContext ctx, HL7Msg msg) throws Exception {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        return SAXTransformer.transform(
                ctx.getAttributes(), msg.hl7cs, arcDev.getOutgoingPatientUpdateTemplateURI(), new org.dcm4che3.io.SAXTransformer.SetupTransformer() {
                    @Override
                    public void setup(Transformer tr) {
                        tr.setParameter("sendingApplication", msg.sendingAppWithFacility[0]);
                        tr.setParameter("sendingFacility", msg.sendingAppWithFacility[1]);
                        tr.setParameter("receivingApplication", msg.receivingAppWithFacility[0]);
                        tr.setParameter("receivingFacility", msg.receivingAppWithFacility[1]);
                        tr.setParameter("dateTime", msg.msgTimestamp);
                        tr.setParameter("msgType", msgType);
                        tr.setParameter("msgControlID", msg.msgControlID);
                        tr.setParameter("charset", msg.hl7cs);
                        if (ctx.getPreviousPatientID() != null)
                            tr.setParameter("priorPatientID", ctx.getPreviousPatientID().toString());
                        if (msg.hl7IncludeNullValues && msgType.equals("ADT^A31^ADT_A05"))
                            tr.setParameter("includeNullValues", "\"\"");
                    }
                });
    }

    private class HL7Msg {
        private final String[] sendingAppWithFacility;
        private final String[] receivingAppWithFacility;
        private final String hl7cs;
        private final boolean hl7IncludeNullValues;
        private final String msgControlID;
        private final String msgTimestamp;
        private final ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);

        HL7Msg(String sender, String receiver) {
            sendingAppWithFacility = appWithFacility(sender);
            receivingAppWithFacility = appWithFacility(receiver);
            HL7Application hl7Application = device.getDeviceExtension(HL7DeviceExtension.class)
                                                .getHL7Application(sender, true);
            ArchiveHL7ApplicationExtension arcHL7AppExt = hl7Application.getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class);
            hl7cs = hl7Application.getHL7SendingCharacterSet();
            hl7IncludeNullValues = arcHL7AppExt != null ? arcHL7AppExt.hl7IncludeNullValues() : arcDev.isHl7IncludeNullValues();
            msgControlID = HL7Segment.nextMessageControlID();
            msgTimestamp = HL7Segment.timeStamp(new Date());
        }

        private String[] appWithFacility(String applicationWithFacility) {
            String[] appWithFacility = new String[2];
            int pipeIndex = applicationWithFacility.indexOf('|');
            appWithFacility[0] = applicationWithFacility.substring(0, pipeIndex);
            appWithFacility[1] = applicationWithFacility.substring(pipeIndex + 1);
            return appWithFacility;
        }
    }
}
