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
 * Portions created by the Initial Developer are Copyright (C) 2017-2019
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

package org.dcm4chee.arc.hl7.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.dcm4che3.conf.api.hl7.IHL7ApplicationCache;
import org.dcm4che3.hl7.HL7Exception;
import org.dcm4che3.hl7.HL7Message;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.HL7DeviceExtension;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4chee.arc.entity.Task;
import org.dcm4chee.arc.hl7.ArchiveHL7Message;
import org.dcm4chee.arc.hl7.HL7Sender;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.qmgt.Outcome;
import org.dcm4chee.arc.qmgt.TaskProcessor;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jul 2021
 */
@ApplicationScoped
@Named("HL7")
public class HL7SenderTaskProcessor implements TaskProcessor {

    @Inject
    private Device device;

    @Inject
    private IHL7ApplicationCache hl7AppCache;

    @Inject
    private HL7Sender hl7Sender;

    @Override
    public Outcome process(Task task) throws Exception {
        ArchiveHL7Message hl7Msg = new ArchiveHL7Message(task.getPayload());
        HL7Application sender = device.getDeviceExtension(HL7DeviceExtension.class)
                .getHL7Application(task.getSendingApplicationWithFacility(),true);
        HL7Application receiver = hl7AppCache.findHL7Application(task.getReceivingApplicationWithFacility());
        hl7Msg.setHttpServletRequestInfo(HttpServletRequestInfo.valueOf(
                task.getRequesterUserID(),
                task.getRequesterHost(),
                task.getRequestURI()));
        UnparsedHL7Message rsp = hl7Sender.sendMessage(sender, receiver, hl7Msg);
        return toOutcome(rsp.data(), sender);
    }

    private Outcome toOutcome(byte[] rsp, HL7Application sender) {
        HL7Segment msa = HL7Message.parse(rsp, sender.getHL7DefaultCharacterSet()).getSegment("MSA");
        if (msa == null)
            return new Outcome(Task.Status.WARNING, "Missing MSA segment in response message");

        return new Outcome(status(msa), msa.toString());
    }

    private Task.Status status(HL7Segment msa) {
        return HL7Exception.AA.equals(msa.getField(1, null))
                ? Task.Status.COMPLETED
                : Task.Status.WARNING;
    }
}
