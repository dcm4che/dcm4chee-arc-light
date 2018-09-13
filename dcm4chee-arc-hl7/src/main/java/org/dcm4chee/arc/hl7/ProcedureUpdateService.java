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

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.hl7.HL7Exception;
import org.dcm4che3.hl7.HL7Message;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4che3.net.hl7.service.HL7Service;
import org.dcm4che3.util.ReverseDNS;
import org.dcm4che3.util.UIDUtils;
import org.dcm4chee.arc.conf.ArchiveHL7ApplicationExtension;
import org.dcm4chee.arc.conf.HL7Fields;
import org.dcm4chee.arc.conf.HL7OrderSPSStatus;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.patient.PatientService;
import org.dcm4chee.arc.procedure.ProcedureContext;
import org.dcm4chee.arc.procedure.ProcedureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Typed;
import javax.inject.Inject;
import javax.xml.transform.TransformerConfigurationException;
import java.io.IOException;
import java.net.Socket;
import java.util.*;
import java.util.stream.Stream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jun 2016
 */
@ApplicationScoped
@Typed(HL7Service.class)
public class ProcedureUpdateService extends AbstractHL7Service {
    private final Logger LOG = LoggerFactory.getLogger(ProcedureUpdateService.class);
    @Inject
    private PatientService patientService;

    @Inject
    private ProcedureService procedureService;

    public ProcedureUpdateService() {
        super("ORM^O01", "OMG^O19", "OMI^O23");
    }

    @Override
    protected UnparsedHL7Message process(HL7Application hl7App, Socket s, UnparsedHL7Message msg) throws Exception {
        ArchiveHL7Message archiveHL7Message = new ArchiveHL7Message(
                HL7Message.makeACK(msg.msh(), HL7Exception.AA, null).getBytes(null));
        Patient pat = PatientUpdateService.updatePatient(hl7App, s, msg, patientService, archiveHL7Message);
        if (pat != null)
            updateProcedure(hl7App, s, msg, pat, archiveHL7Message);

        return archiveHL7Message;
    }

    private void updateProcedure(HL7Application hl7App, Socket s, UnparsedHL7Message msg, Patient pat,
                                 ArchiveHL7Message archiveHL7Message)
            throws IOException, SAXException, TransformerConfigurationException {
        ArchiveHL7ApplicationExtension arcHL7App =
                hl7App.getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class);
        HL7Segment msh = msg.msh();
        String hl7cs = msh.getField(17, hl7App.getHL7DefaultCharacterSet());
        Attributes attrs = SAXTransformer.transform(
                msg.data(), hl7cs, arcHL7App.scheduleProcedureTemplateURI(), tr -> {
                    tr.setParameter("hl7ScheduledProtocolCodeInOrder", arcHL7App.hl7ScheduledProtocolCodeInOrder().toString());
                    if (arcHL7App.hl7ScheduledStationAETInOrder() != null)
                        tr.setParameter("hl7ScheduledStationAETInOrder", arcHL7App.hl7ScheduledStationAETInOrder().toString());
                });
        boolean result = adjust(attrs, arcHL7App, new HL7Fields(msg, hl7App.getHL7DefaultCharacterSet()), s);
        if (!result) {
            LOG.warn("MWL item not created/updated for HL7 message : " + msh.getMessageType()
                    + " as no mapping to a Scheduled Procedure Step Status is configured with ORC-1_ORC-5 : "
                    + attrs.getNestedDataset(Tag.ScheduledProcedureStepSequence).getString(Tag.ScheduledProcedureStepStatus));
            return;
        }
        ProcedureContext ctx = procedureService.createProcedureContextHL7(s, msg);
        ctx.setPatient(pat);
        ctx.setAttributes(attrs);
        procedureService.updateProcedure(ctx);
        archiveHL7Message.setProcRecEventActionCode(ctx.getEventActionCode());
        archiveHL7Message.setStudyAttrs(ctx.getAttributes());
    }

    private boolean adjust(Attributes attrs, ArchiveHL7ApplicationExtension arcHL7App, HL7Fields hl7Fields,
                           Socket socket) {
        if (!attrs.containsValue(Tag.StudyInstanceUID))
            attrs.setString(Tag.StudyInstanceUID, VR.UI, UIDUtils.createUID());
        Sequence spsItems = attrs.getSequence(Tag.ScheduledProcedureStepSequence);
        for (Attributes sps : spsItems) {
            if (sps.getString(Tag.ScheduledStationAETitle) == null) {
                List<String> ssAETs = new ArrayList<>();
                Collection<Device> devices = arcHL7App.hl7OrderScheduledStation(
                        ReverseDNS.hostNameOf(socket.getLocalAddress()), hl7Fields);
                for (Device device : devices)
                    ssAETs.addAll(device.getApplicationAETitles());

                if (!ssAETs.isEmpty())
                    sps.setString(Tag.ScheduledStationAETitle, VR.AE, ssAETs.toArray(new String[ssAETs.size()]));

                String[] ssNames = devices.stream().filter(x -> x.getStationName() != null).map(Device::getStationName).toArray(String[]::new);
                if (ssNames.length > 0)
                    sps.setString(Tag.ScheduledStationName, VR.SH, ssNames);
            }

            for (HL7OrderSPSStatus hl7OrderSPSStatus : arcHL7App.hl7OrderSPSStatuses()) {
                if (Stream.of(hl7OrderSPSStatus.getOrderControlStatusCodes())
                        .anyMatch(x -> x.equals(sps.getString(Tag.ScheduledProcedureStepStatus)))) {
                    sps.setString(Tag.ScheduledProcedureStepStatus, VR.CS, hl7OrderSPSStatus.getSPSStatus().name());
                    return true;
                }
            }
        }
        return false;
    }
}
