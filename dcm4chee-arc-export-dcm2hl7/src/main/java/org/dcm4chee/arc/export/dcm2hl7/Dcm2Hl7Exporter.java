/*
 * **** BEGIN LICENSE BLOCK *****
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
 * Portions created by the Initial Developer are Copyright (C) 2015-2019
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
 * **** END LICENSE BLOCK *****
 *
 */

package org.dcm4chee.arc.export.dcm2hl7;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.hl7.IHL7ApplicationCache;
import org.dcm4che3.hl7.HL7Message;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.HL7DeviceExtension;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.exporter.AbstractExporter;
import org.dcm4chee.arc.exporter.ExportContext;
import org.dcm4chee.arc.hl7.ArchiveHL7Message;
import org.dcm4chee.arc.hl7.HL7Sender;
import org.dcm4chee.arc.hl7.HL7SenderUtils;
import org.dcm4chee.arc.qmgt.Outcome;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveService;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since May 2021
 */
public class Dcm2Hl7Exporter extends AbstractExporter {
    private final String msh3456;
    private final HL7Sender hl7Sender;
    private final Device device;
    private final RetrieveService retrieveService;
    private final IHL7ApplicationCache hl7AppCache;

    public Dcm2Hl7Exporter(ExporterDescriptor descriptor, HL7Sender hl7Sender, Device device,
                           RetrieveService retrieveService, IHL7ApplicationCache hl7AppCache) {
        super(descriptor);
        this.msh3456 = descriptor.getExportURI().getSchemeSpecificPart();
        this.hl7Sender = hl7Sender;
        this.device = device;
        this.retrieveService = retrieveService;
        this.hl7AppCache = hl7AppCache;
    }

    @Override
    public Outcome export(ExportContext exportContext) throws Exception {
        String[] appFacility = msh3456.split("_");
        if (appFacility.length != 4)
            return new Outcome(QueueMessage.Status.WARNING,
                    "Sending and/or Receiving application and facility not specified");

        HL7Application sender = device.getDeviceExtension(HL7DeviceExtension.class)
                .getHL7Application(appFacility[0] + '|' + appFacility[1], true);
        if (sender == null)
            return new Outcome(QueueMessage.Status.WARNING,
                    "Sending HL7 Application not configured : " + appFacility[0] + '|' + appFacility[1]);

        HL7Application receiver;
        try {
            receiver = hl7AppCache.findHL7Application(appFacility[2] + '|' + appFacility[3]);
        } catch (ConfigurationException e) {
            return new Outcome(QueueMessage.Status.WARNING,
                    "Unknown Receiving HL7 Application : " + appFacility[2] + '|' + appFacility[3]);
        }

        return scheduleMessage(exportContext, sender, receiver);
    }

    private Outcome scheduleMessage(ExportContext exportContext, HL7Application sender, HL7Application receiver)
            throws Exception {
        String xslStylesheetURI = descriptor.getProperties().get("XSLStylesheetURI");
        if (xslStylesheetURI == null)
            return new Outcome(QueueMessage.Status.WARNING,
                    "Missing XSL stylesheet to convert DICOM attributes to HL7 message");

        String msgType = descriptor.getProperties().get("MessageType");
        if (msgType == null)
            return new Outcome(QueueMessage.Status.WARNING, "Missing HL7 message type");

        RetrieveContext ctx = retrieveService.newRetrieveContext(
                                                exportContext.getAETitle(),
                                                exportContext.getStudyInstanceUID(),
                                                exportContext.getSeriesInstanceUID(),
                                                exportContext.getSopInstanceUID());
        ctx.setHttpServletRequestInfo(exportContext.getHttpServletRequestInfo());
        if (!retrieveService.calculateMatches(ctx))
            return new Outcome(QueueMessage.Status.WARNING, noMatches(exportContext));

        ArchiveHL7Message hl7Msg = hl7Message(sender, receiver, ctx, msgType, xslStylesheetURI);
        HL7Message hl7MsgRsp = parseRsp(hl7Sender.sendMessage(sender, receiver, hl7Msg));
        return new Outcome(statusOf(hl7MsgRsp), hl7MsgRsp.toString());
    }

    private QueueMessage.Status statusOf(HL7Message hl7MsgRsp) {
        return hl7MsgRsp.getSegment("MSA").getField(1, "AA").equals("AA")
                ? QueueMessage.Status.COMPLETED
                : QueueMessage.Status.FAILED;
    }

    private HL7Message parseRsp(UnparsedHL7Message hl7MsgRsp) {
        HL7Segment msh = hl7MsgRsp.msh();
        String charset = msh.getField(17, "ASCII");
        return HL7Message.parse(hl7MsgRsp.unescapeXdddd(), charset);
    }

    private ArchiveHL7Message hl7Message(HL7Application sender, HL7Application receiver, RetrieveContext ctx,
                                     String msgType, String uri) throws Exception {
        byte[] data = HL7SenderUtils.data(sender,
                                        receiver,
                                        ctx.getMatches().get(0).getAttributes(),
                                        null,
                                        msgType,
                                        uri);
        ArchiveHL7Message hl7Msg = new ArchiveHL7Message(data);
        hl7Msg.setHttpServletRequestInfo(ctx.getHttpServletRequestInfo());
        return hl7Msg;
    }
}
