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
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.HL7Connection;
import org.dcm4che3.net.hl7.HL7DeviceExtension;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4che3.util.ReverseDNS;
import org.dcm4chee.arc.HL7ConnectionEvent;
import org.dcm4chee.arc.conf.ArchiveHL7ApplicationExtension;
import org.dcm4chee.arc.conf.HL7Fields;
import org.dcm4chee.arc.conf.HL7ForwardRule;
import org.dcm4chee.arc.entity.Task;
import org.dcm4chee.arc.hl7.HL7Sender;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.qmgt.TaskManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.io.IOException;
import java.util.Date;
import java.util.stream.Stream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
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
    private TaskManager taskManager;

    public void onHL7Connection(@Observes HL7ConnectionEvent event) {
        if (event.getType() != HL7ConnectionEvent.Type.MESSAGE_PROCESSED || event.getException() != null)
            return;

        UnparsedHL7Message msg = event.getHL7Message();
        HL7Application hl7App = device.getDeviceExtension(HL7DeviceExtension.class)
                .getHL7Application(msg.msh().getReceivingApplicationWithFacility(), true);
        if (hl7App == null)
            return;

        ArchiveHL7ApplicationExtension arcHL7App =
                hl7App.getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class);
        if (arcHL7App == null || !arcHL7App.hasHL7ForwardRules())
            return;

        String host = ReverseDNS.hostNameOf(event.getSocket().getInetAddress());
        HL7Fields hl7Fields = new HL7Fields(msg, hl7App.getHL7DefaultCharacterSet());
        arcHL7App.hl7ForwardRules()
                .filter(rule -> rule.match(host, hl7Fields))
                .map(HL7ForwardRule::getDestinations)
                .flatMap(Stream::of)
                .distinct()
                .forEach(dest -> forwardMessage(msg, dest));
    }

    private void forwardMessage(UnparsedHL7Message msg, String dest) {
        HL7Segment msh = msg.msh();
        int field23Len = msh.getField(2, "").length() + msh.getField(3, "").length() + 2;
        int field45Len = msh.getField(4, "").length() + msh.getField(5, "").length() + 2;
        try {
            byte[] data = replaceField2345(msg.data(), dest.replace('|', msh.getFieldSeparator()), field23Len, field45Len);
            scheduleMessage(null, data);
        } catch (Exception e) {
            LOG.warn("Failed to schedule forward of HL7 message to {}:\n", dest, e);
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
                                String receivingFacility, String messageType, String messageControlID, byte[] hl7msg,
                                HttpServletRequestInfo httpServletRequestInfo)
            throws ConfigurationException {
        getSendingHl7Application(sendingApplication, sendingFacility);
        hl7AppCache.findHL7Application(receivingApplication + '|' + receivingFacility);
        scheduleMessage(null, hl7msg);
    }

    @Override
    public UnparsedHL7Message sendMessage(HL7Application sender, String receivingApplication, String receivingFacility,
                                          String messageType, String messageControlID, UnparsedHL7Message hl7msg)
            throws Exception {
        HL7Application receiver = hl7AppCache.findHL7Application(receivingApplication + '|' + receivingFacility);
        return sendMessage(sender, receiver, hl7msg);
    }

    @Override
    public UnparsedHL7Message sendMessage(HL7Application sender, HL7Application receiver, UnparsedHL7Message hl7Msg)
            throws Exception {
        try (HL7Connection conn = sender.open(receiver)) {
            conn.writeMessage(hl7Msg);
            UnparsedHL7Message rsp = conn.readMessage(hl7Msg);
            if (rsp == null)
                throw new IOException("TCP connection dropped while waiting for response");

            return rsp;
        }
    }

    private HL7Application getSendingHl7Application(String sendingApplication, String sendingFacility)
            throws ConfigurationNotFoundException {
        HL7DeviceExtension hl7Dev = device.getDeviceExtension(HL7DeviceExtension.class);
        String sendingAppWithFacility = sendingApplication + '|' + sendingFacility;
        HL7Application sender = hl7Dev.getHL7Application(sendingAppWithFacility, true);
        if (sender == null)
            throw new ConfigurationNotFoundException(
                    "Sending HL7 Application not configured : " + sendingAppWithFacility);
        return sender;
    }

    @Override
    public void scheduleMessage(HttpServletRequestInfo httpServletRequestInfo, byte[] data) {
        UnparsedHL7Message hl7Msg = new UnparsedHL7Message(data);
        HL7Segment msh = hl7Msg.msh();
        Task task = new Task();
        task.setDeviceName(device.getDeviceName());
        task.setQueueName(HL7Sender.QUEUE_NAME);
        task.setType(Task.Type.HL7);
        task.setScheduledTime(new Date());
        task.setSendingApplicationWithFacility(msh.getSendingApplicationWithFacility());
        task.setReceivingApplicationWithFacility(msh.getReceivingApplicationWithFacility());
        task.setMessageType(msh.getMessageType());
        task.setMessageControlID(msh.getMessageControlID());
        if (httpServletRequestInfo != null) {
            task.setRequesterUserID(httpServletRequestInfo.requesterUserID);
            task.setRequesterHost(httpServletRequestInfo.requesterHost);
            task.setRequestURI(requestURL(httpServletRequestInfo));
        }
        task.setPayload(data);
        task.setStatus(Task.Status.SCHEDULED);
        taskManager.scheduleTask(task);
    }

    private String requestURL(HttpServletRequestInfo httpServletRequestInfo) {
        String requestURI = httpServletRequestInfo.requestURI;
        String queryString = httpServletRequestInfo.queryString;
        return queryString == null ? requestURI : requestURI + '?' + queryString;
    }
}
