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
 * Portions created by the Initial Developer are Copyright (C) 2015-2026
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

package org.dcm4chee.arc.export.dcm2hl7oru;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.hl7.IHL7ApplicationCache;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.UID;
import org.dcm4che3.hl7.HL7Message;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.HL7DeviceExtension;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.entity.Task;
import org.dcm4chee.arc.exporter.AbstractExporter;
import org.dcm4chee.arc.exporter.ExportContext;
import org.dcm4chee.arc.hl7.ArchiveHL7Message;
import org.dcm4chee.arc.hl7.HL7Sender;
import org.dcm4chee.arc.hl7.HL7SenderUtils;
import org.dcm4chee.arc.qmgt.Outcome;
import org.dcm4chee.arc.retrieve.LocationInputStream;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveService;
import org.dcm4chee.arc.store.InstanceLocations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.Map;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since May 2026
 */
public class Dcm2HL7ORUExporter extends AbstractExporter {
    private final HL7Sender hl7Sender;
    private final Device device;
    private final RetrieveService retrieveService;
    private final IHL7ApplicationCache hl7AppCache;


    private static final Logger LOG = LoggerFactory.getLogger(Dcm2HL7ORUExporter.class);
    private static final String ORU_MSG_TYPE = "ORU^R01^ORU_R01";
    private String OUTCOME_MSG = "Outgoing HL7 notification with report content - [failed={0}, warning={1}, completed={2}]";

    public Dcm2HL7ORUExporter(ExporterDescriptor descriptor, HL7Sender hl7Sender, Device device,
                              RetrieveService retrieveService, IHL7ApplicationCache hl7AppCache) {
        super(descriptor);
        this.hl7Sender = hl7Sender;
        this.device = device;
        this.retrieveService = retrieveService;
        this.hl7AppCache = hl7AppCache;
    }

    @Override
    public Outcome export(ExportContext exportContext) throws Exception {
        String[][] sendingAndReceiving = parseSendingAndReceivingApplicationAndFacility(
                descriptor.getExportURI().getSchemeSpecificPart());
        if (sendingAndReceiving == null) {
            return new Outcome(Task.Status.WARNING,
                    "Export URI: " + descriptor.getExportURI() + " not in format: " +
                            "hl7oru:{Sending Application}/{Sending Facility}:{Receiving Application}/{Receiving Facility}");

        }
        String sendingAppFacility = sendingAndReceiving[0][0] + '|' + sendingAndReceiving[0][1];
        HL7Application sender = device.getDeviceExtension(HL7DeviceExtension.class)
                .getHL7Application(sendingAppFacility, true);
        if (sender == null)
            return new Outcome(Task.Status.WARNING,
                    "Sending HL7 Application not configured : " + sendingAppFacility);

        String receivingAppFacility = sendingAndReceiving[1][0] + '|' + sendingAndReceiving[1][1];
        HL7Application receiver;
        try {
            receiver = hl7AppCache.findHL7Application(receivingAppFacility);
        } catch (ConfigurationException e) {
            return new Outcome(Task.Status.WARNING,
                    "Unknown Receiving HL7 Application : " + receivingAppFacility);
        }

        return scheduleMessage(exportContext, sender, sendingAppFacility, receiver);
    }

    private static String[][] parseSendingAndReceivingApplicationAndFacility(String schemeSpecificPart) {
        String[] ss = StringUtils.split(schemeSpecificPart, ':');
        if (ss.length == 2) {
            String[][] result = {
                    StringUtils.split(ss[0], '/'),
                    StringUtils.split(ss[1], '/')
            };
            if (result[0].length == 2 && result[1].length == 2) {
                return result;
            }
        }
        return null;
    }

    private Outcome scheduleMessage(
            ExportContext exportContext, HL7Application sender, String hl7SenderOtherAppName, HL7Application receiver)
            throws Exception {
        String xslStylesheetURI = descriptor.getProperties().get("XSLStylesheetURI");
        if (xslStylesheetURI == null)
            return new Outcome(Task.Status.WARNING,
                    "Missing XSL stylesheet to convert DICOM attributes to HL7 message");

        RetrieveContext ctx = retrieveService.newRetrieveContext(
                                                exportContext.getAETitle(),
                                                exportContext.getStudyInstanceUID(),
                                                exportContext.getSeriesInstanceUID(),
                                                exportContext.getSopInstanceUID());
        ctx.setHttpServletRequestInfo(exportContext.getHttpServletRequestInfo());
        if (!retrieveService.calculateMatches(ctx))
            return new Outcome(Task.Status.WARNING, noMatches(exportContext));

        Map<String, Collection<InstanceLocations>> notAccessible = retrieveService.removeNotAccessableMatches(ctx);
        if (!notAccessible.isEmpty()) {
            return new Outcome(Task.Status.WARNING, notAccessable(exportContext, notAccessible));
        }

        int completed = 0;
        int warning = 0;
        int failed = 0;
        HL7Message hl7MsgRsp = null;
        for (InstanceLocations instLoc : ctx.getMatches()) {
            if (!isSROrPDFReport(instLoc)) {
                LOG.info("DICOM Object[StudyIUID={}, SeriesIUID={}, SOPClassUID={}, SOPIUID={}] not supported for this exporter. Only SR / PDF reports supported.",
                        exportContext.getStudyInstanceUID(),
                        exportContext.getSeriesInstanceUID(),
                        instLoc.getSopClassUID(),
                        instLoc.getSopInstanceUID());
                warning++;
                continue;
            }

            LocationInputStream locationInputStream = retrieveService.openLocationInputStream(ctx, instLoc);
            try (DicomInputStream dis = new DicomInputStream(locationInputStream.stream)) {
                Attributes attrs = dis.readDataset();
                ArchiveAEExtension arcAE = device.getApplicationEntity(descriptor.getAETitle(), true)
                                                 .getAEExtensionNotNull(ArchiveAEExtension.class);
                byte[] data = HL7SenderUtils.hl7PSUDataExporter(sender, hl7SenderOtherAppName, receiver,
                            attrs, ORU_MSG_TYPE, xslStylesheetURI, arcAE);
                ArchiveHL7Message hl7Msg = new ArchiveHL7Message(data);
                hl7Msg.setHttpServletRequestInfo(ctx.getHttpServletRequestInfo());
                hl7MsgRsp = parseRsp(hl7Sender.sendMessage(sender, receiver, hl7Msg));
                completed++;
            } catch (Exception e) {
                failed++;
                LOG.info("Failed to process and send out report DICOM Object[StudyIUID={}, SeriesIUID={}, SOPClassUID={}, SOPIUID={}] \n",
                        exportContext.getStudyInstanceUID(),
                        exportContext.getSeriesInstanceUID(),
                        instLoc.getSopClassUID(), instLoc.getSopInstanceUID(), e);
            }
        }

        return outcome(hl7MsgRsp, ctx, completed, warning, failed);
    }

    private boolean isSROrPDFReport(InstanceLocations instLoc) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        String sopClassUID = instLoc.getSopClassUID();
        return arcDev.isWadoSupportedSRClass(sopClassUID) || sopClassUID.equals(UID.EncapsulatedPDFStorage);
    }

    private Outcome outcome(
            HL7Message hl7MsgRsp, RetrieveContext ctx, int completed, int warning, int failed) {
        if (ctx.getMatches().size() == 1 && hl7MsgRsp != null) {
            return new Outcome(hl7MsgRsp.getSegment("MSA").getField(1, "AA").equals("AA")
                            ? Task.Status.COMPLETED
                            : Task.Status.FAILED,
                        hl7MsgRsp.toString());
        }

        return new Outcome(failed > 0
                            ? Task.Status.FAILED
                            : warning > 0
                                ? Task.Status.WARNING
                                : Task.Status.COMPLETED,
                           MessageFormat.format(OUTCOME_MSG, failed, warning, completed));
    }

    private Task.Status statusOf(HL7Message hl7MsgRsp) {
        return hl7MsgRsp.getSegment("MSA").getField(1, "AA").equals("AA")
                ? Task.Status.COMPLETED
                : Task.Status.FAILED;
    }

    private HL7Message parseRsp(UnparsedHL7Message hl7MsgRsp) {
        HL7Segment msh = hl7MsgRsp.msh();
        String charset = msh.getField(17, "ASCII");
        return HL7Message.parse(hl7MsgRsp.unescapeXdddd(), charset);
    }
}
