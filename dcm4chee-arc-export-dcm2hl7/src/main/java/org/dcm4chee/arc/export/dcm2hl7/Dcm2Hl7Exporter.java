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

import org.dcm4che3.data.Attributes;
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
import org.dcm4chee.arc.hl7.SAXTransformer;
import org.dcm4chee.arc.qmgt.Outcome;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveService;

import java.util.Date;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since May 2021
 */
public class Dcm2Hl7Exporter extends AbstractExporter {

    private final String msh3456;
    private final HL7Sender hl7Sender;
    private final Device device;
    private final RetrieveService retrieveService;

    public Dcm2Hl7Exporter(ExporterDescriptor descriptor, HL7Sender hl7Sender,
                           Device device, RetrieveService retrieveService) {
        super(descriptor);
        this.msh3456 = descriptor.getExportURI().getSchemeSpecificPart();
        this.hl7Sender = hl7Sender;
        this.device = device;
        this.retrieveService = retrieveService;
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

        String xslStylesheetURI = descriptor.getProperties().get("XSLStylesheetURI");
        if (xslStylesheetURI == null)
            return new Outcome(QueueMessage.Status.WARNING,
                    "Missing XSL stylesheet to convert DICOM attributes to HL7 message");

        RetrieveContext ctx = retrieveService.newRetrieveContext(
                                exportContext.getAETitle(),
                                exportContext.getStudyInstanceUID(),
                                exportContext.getSeriesInstanceUID(),
                                exportContext.getSopInstanceUID());
        ctx.setHttpServletRequestInfo(exportContext.getHttpServletRequestInfo());
        if (!retrieveService.calculateMatches(ctx))
            return new Outcome(QueueMessage.Status.WARNING, noMatches(exportContext));

        HL7Message hl7MsgRsp = parseRsp(sendHL7Message(sender, ctx, xslStylesheetURI, appFacility));
        return new Outcome(hl7MsgRsp.getSegment("MSA").getField(1, "AA").equals("AA")
                           ? QueueMessage.Status.COMPLETED : QueueMessage.Status.FAILED,
                           hl7MsgRsp.toString());
    }

    private HL7Message parseRsp(UnparsedHL7Message hl7MsgRsp) {
        HL7Segment msh = hl7MsgRsp.msh();
        String charset = msh.getField(17, "ASCII");
        return HL7Message.parse(hl7MsgRsp.unescapeXdddd(), charset);
    }

    private UnparsedHL7Message sendHL7Message(
            HL7Application sender, RetrieveContext ctx, String xslStylesheetURI, String[] appFacility)
            throws Exception {
        Attributes attrs = ctx.getMatches().get(0).getAttributes();
        byte[] data = SAXTransformer.transform(
                attrs, sender.getHL7SendingCharacterSet(), xslStylesheetURI, tr -> {
                    tr.setParameter("sendingApplication", appFacility[0]);
                    tr.setParameter("sendingFacility", appFacility[1]);
                    tr.setParameter("receivingApplication", appFacility[2]);
                    tr.setParameter("receivingFacility", appFacility[3]);
                    tr.setParameter("dateTime", HL7Segment.timeStamp(new Date()));
                    tr.setParameter("msgControlID", HL7Segment.nextMessageControlID());
                    tr.setParameter("charset", sender.getHL7SendingCharacterSet());
                });

        ArchiveHL7Message hl7Msg = new ArchiveHL7Message(data);
        hl7Msg.setHttpServletRequestInfo(ctx.getHttpServletRequestInfo());
        return hl7Sender.sendMessage(sender, appFacility[2], appFacility[3], hl7Msg);
    }
}
