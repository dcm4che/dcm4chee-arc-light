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
 * either the GNU General Public License Version 2 or later (the "GPL", sendingApplication); or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL", sendingApplication);
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

package org.dcm4chee.arc.hl7.impl;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.ConfigurationNotFoundException;
import org.dcm4che3.conf.api.hl7.IHL7ApplicationCache;
import org.dcm4che3.hl7.HL7Message;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.hl7.MLLPConnection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.HL7DeviceExtension;
import org.dcm4chee.arc.hl7.HL7Sender;
import org.dcm4chee.arc.qmgt.QueueManager;
import org.dcm4chee.arc.qmgt.QueueSizeLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.jms.JMSException;
import javax.jms.JMSRuntimeException;
import javax.jms.ObjectMessage;
import java.io.IOException;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2016
 */
@ApplicationScoped
public class HL7SenderImpl implements HL7Sender {
    private static final Logger LOG = LoggerFactory.getLogger(HL7SenderImpl.class);

    @Inject
    private Device device;

    @Inject
    private IHL7ApplicationCache hl7AppCache;

    @Inject
    private QueueManager queueManager;

    @Override
    public void forwardMessage(HL7Segment msh, byte[] orighl7msg, String... dests) {
        int field23Len = msh.getField(2, "").length() + msh.getField(3, "").length() + 2;
        int field45Len = msh.getField(4, "").length() + msh.getField(5, "").length() + 2;
        for (String dest : dests) {
            String[] ss = HL7Segment.split(dest, '|');
            try {
                scheduleMessage(
                        msh.getField(4, ""),
                        msh.getField(5, ""),
                        ss[0],
                        ss.length > 1 ? ss[1] : "",
                        msh.getField(8, ""),
                        msh.getField(9, ""),
                        replaceField2345(orighl7msg, dest.replace('|', msh.getFieldSeparator()), field23Len, field45Len));
            } catch (Exception e) {
                LOG.warn("Failed to schedule forward of HL7 message to {}:\n", dest, e);
            }
        }
    }

    private byte[] replaceField2345(byte[] orighl7msg, String dest, int field23Len, int field45Len) {
        byte[] b = dest.getBytes();
        byte[] hl7msg = new byte[orighl7msg.length + b.length + 1 - field23Len];
        int srcPos = 0;
        int destPos = 0;
        System.arraycopy(orighl7msg, 0, hl7msg, 0, srcPos += 9);
        System.arraycopy(orighl7msg, srcPos += field23Len, hl7msg, destPos += 9, field45Len);
        System.arraycopy(b, 0, hl7msg, destPos += field45Len, b.length);
        System.arraycopy(orighl7msg, srcPos += field45Len - 1, hl7msg, destPos + b.length, orighl7msg.length - srcPos);
        return hl7msg;
    }

    @Override
    public void scheduleMessage(String sendingApplication, String sendingFacility, String receivingApplication,
                                String receivingFacility, String messageType, String messageControlID, byte[] hl7msg)
            throws ConfigurationException, QueueSizeLimitExceededException {
        getSendingHl7Application(sendingApplication, sendingFacility);
        hl7AppCache.findHL7Application(receivingApplication + '|' + receivingFacility);
        try {
            ObjectMessage msg = queueManager.createObjectMessage(hl7msg);
            msg.setStringProperty("SendingApplication", sendingApplication);
            msg.setStringProperty("SendingFacility", sendingFacility);
            msg.setStringProperty("ReceivingApplication", receivingApplication);
            msg.setStringProperty("ReceivingFacility", receivingFacility);
            msg.setStringProperty("MessageType", messageType);
            msg.setStringProperty("MessageControlID", messageControlID);
            queueManager.scheduleMessage(QUEUE_NAME, msg);
        } catch (JMSException e) {
            throw new JMSRuntimeException(e.getMessage(), e.getErrorCode(), e.getCause());
        }
    }

    @Override
    public HL7Message sendMessage(String sendingApplication, String sendingFacility, String receivingApplication,
                              String receivingFacility, String messageType, String messageControlID, byte[] hl7msg)
            throws Exception {
        HL7Application sender = getSendingHl7Application(sendingApplication, sendingFacility);
        HL7Application receiver = hl7AppCache.findHL7Application(receivingApplication + '|' + receivingFacility);
        return getAcknowledgeHL7Msg(sender, receiver, hl7msg);
    }

    private HL7Application getSendingHl7Application(String sendingApplication, String sendingFacility) throws ConfigurationNotFoundException {
        HL7DeviceExtension hl7Dev = device.getDeviceExtension(HL7DeviceExtension.class);
        String sendingAppWithFacility = sendingApplication + '|' + sendingFacility;
        HL7Application sender = hl7Dev.getHL7Application(sendingAppWithFacility, true);
        if (sender == null)
            throw new ConfigurationNotFoundException("Sending HL7 Application not configured : " + sendingAppWithFacility);
        return sender;
    }

    private HL7Message getAcknowledgeHL7Msg(HL7Application sender, HL7Application receiver, byte[] hl7msg) throws Exception {
        try (MLLPConnection conn = sender.connect(receiver)) {
            conn.writeMessage(hl7msg);
            byte[] rsp = conn.readMessage();
            if (rsp == null)
                throw new IOException("TCP connection dropped while waiting for response");

            return HL7Message.parse(rsp, sender.getHL7DefaultCharacterSet());
        }
    }
}
