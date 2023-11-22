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
 * Portions created by the Initial Developer are Copyright (C) 2013-2019
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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Issuer;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.hl7.ERRSegment;
import org.dcm4che3.hl7.HL7Exception;
import org.dcm4che3.hl7.HL7Message;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4che3.net.hl7.service.DefaultHL7Service;
import org.dcm4che3.net.hl7.service.HL7Service;
import org.dcm4che3.util.ReverseDNS;
import org.dcm4che3.util.UIDUtils;
import org.dcm4chee.arc.conf.ArchiveHL7ApplicationExtension;
import org.dcm4chee.arc.conf.HL7Fields;
import org.dcm4chee.arc.conf.HL7OrderMissingAdmissionIDPolicy;
import org.dcm4chee.arc.conf.HL7OrderSPSStatus;
import org.dcm4chee.arc.id.IDService;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.patient.PatientService;
import org.dcm4chee.arc.procedure.ProcedureContext;
import org.dcm4chee.arc.procedure.ProcedureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jun 2016
 */
@ApplicationScoped
@Typed(HL7Service.class)
public class ProcedureUpdateService extends DefaultHL7Service {
    private final Logger LOG = LoggerFactory.getLogger(ProcedureUpdateService.class);
    private static final String QUANTITY_TIMING = "ORC^1^7^1^4";
    private static final String START_DATE_TIME = "TQ1^1^7^1^1";
    private static final String GENERAL_ORDER_MSG = "ORM^O01";
    private static final String IMAGING_ORDER_MSG = "OMI^O23";
    private static final String ORDER_CONTROL = "ORC^1^1^1^1";
    private static final String STUDY_UID_IMAGING_ORDER = "IPC^1^3^1^1";
    private static final String STUDY_UID_GENERAL_ORDER = "ZDS^1^1^1^1";
    private static final String ACCESSION_NO_IMAGING_ORDER = "IPC^1^1^1^1";
    private static final String ACCESSION_NO_GENERAL_ORDER = "OBR^1^18^1^1";
    private static final String ADMISSION_ID = "PV1^1^19^1^1";

    @Inject
    private PatientService patientService;

    @Inject
    private ProcedureService procedureService;

    @Inject
    private IDService idService;

    public ProcedureUpdateService() {
        super("ORM^O01", "OMG^O19", "OMI^O23");
    }

    @Override
    public UnparsedHL7Message onMessage(HL7Application hl7App, Connection conn, Socket s, UnparsedHL7Message msg)
            throws HL7Exception {
        ArchiveHL7Message archiveHL7Message = new ArchiveHL7Message(
                HL7Message.makeACK(msg.msh(), HL7Exception.AA, null).getBytes(null));
        PatientMgtContext ctx = patientService.createPatientMgtContextHL7(hl7App, s, msg);
        transform(ctx);
        ctx.setPatient(PatientUpdateService.updatePatient(ctx, patientService, archiveHL7Message));
        try {
            updateProcedure(ctx, archiveHL7Message);
        } catch(HL7Exception e) {
            throw e;
        } catch (Exception e) {
            throw new HL7Exception(new ERRSegment(msg.msh()).setUserMessage(e.getMessage()), e);
        }
        return archiveHL7Message;
    }

    private void updateProcedure(PatientMgtContext ctx, ArchiveHL7Message archiveHL7Message) throws Exception {
        if (ctx.getPatient() == null) {
            LOG.info("Abort MWL create / update, as no patient associated with Hl7 order was created / updated.");
            return;
        }
        UnparsedHL7Message msg = ctx.getUnparsedHL7Message();
        ArchiveHL7ApplicationExtension arcHL7App = ctx.getHL7Application()
                .getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class);
        LOG.info("Update procedure for message type {}", msg.msh().getMessageType());
        adjust(ctx);
        ProcedureContext procCtx = procedureService.createProcedureContext().setSocket(ctx.getSocket()).setHL7Message(msg);
        procCtx.setLocalAET(arcHL7App.getAETitle());
        procCtx.setArchiveHL7AppExtension(arcHL7App);
        procCtx.setPatient(ctx.getPatient());
        procCtx.setAttributes(ctx.getAttributes());
        procedureService.updateProcedure(procCtx);
        archiveHL7Message.setProcRecEventActionCode(ctx.getEventActionCode());
        archiveHL7Message.setStudyAttrs(ctx.getAttributes());
    }

    private static void transform(PatientMgtContext ctx) throws HL7Exception {
        ArchiveHL7ApplicationExtension arcHL7App =
                ctx.getHL7Application().getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class);
        UnparsedHL7Message msg = ctx.getUnparsedHL7Message();
        try {
            Issuer hl7PrimaryAssigningAuthorityOfPatientID = arcHL7App.hl7PrimaryAssigningAuthorityOfPatientID();
            ctx.setAttributes(SAXTransformer.transform(
                    msg,
                    arcHL7App,
                    arcHL7App.scheduleProcedureTemplateURI(),
                    tr -> {
                        tr.setParameter("hl7ScheduledProtocolCodeInOrder",
                                arcHL7App.hl7ScheduledProtocolCodeInOrder().toString());
                        if (arcHL7App.hl7ScheduledStationAETInOrder() != null)
                            tr.setParameter("hl7ScheduledStationAETInOrder",
                                    arcHL7App.hl7ScheduledStationAETInOrder().toString());
                        if (hl7PrimaryAssigningAuthorityOfPatientID != null)
                            tr.setParameter("hl7PrimaryAssigningAuthorityOfPatientID",
                                    hl7PrimaryAssigningAuthorityOfPatientID.toString());
                    }));
        } catch (Exception e) {
            throw new HL7Exception(new ERRSegment(msg.msh()).setUserMessage(e.getMessage()), e);
        }
    }

    private void validateSPSStartDateTime(Attributes sps, HL7Segment msh) throws Exception {
        String spsDTField = msh.getMessageType().equals(GENERAL_ORDER_MSG)
                ? QUANTITY_TIMING : START_DATE_TIME;
        try {
            String spsStartDateTime = sps.getString(Tag.ScheduledProcedureStepStartDate);
            if (spsStartDateTime == null) {
                LOG.info("SPS Start Date / Time is missing in {} - use current date time", spsDTField);
                return;
            }
            new SimpleDateFormat("yyyyMMdd").parse(spsStartDateTime);
        } catch (Exception e) {
            throw new HL7Exception(
                    new ERRSegment(msh)
                            .setHL7ErrorCode(ERRSegment.DATA_TYPE_ERROR)
                            .setErrorLocation(spsDTField)
                            .setUserMessage("Invalid scheduled procedure step start date and/or time"));
        }
    }

    private void adjust(PatientMgtContext ctx) throws Exception {
        HL7Application hl7App = ctx.getHL7Application();
        Attributes attrs = ctx.getAttributes();
        UnparsedHL7Message msg = ctx.getUnparsedHL7Message();
        ArchiveHL7ApplicationExtension arcHL7App = hl7App.getHL7AppExtensionNotNull(ArchiveHL7ApplicationExtension.class);
        if (arcHL7App.getMWLWorklistLabel() != null)
            attrs.setString(Tag.WorklistLabel, VR.LO, arcHL7App.getMWLWorklistLabel());
        HL7Segment msh = msg.msh();
        boolean uidsGenerated = adjustIdentifiers(attrs, arcHL7App, msh);

        Collection<Device> hl7OrderScheduledStations = arcHL7App.hl7OrderScheduledStation(
                ReverseDNS.hostNameOf(ctx.getSocket().getInetAddress()),
                new HL7Fields(msg, hl7App.getHL7DefaultCharacterSet()));

        Iterator<Attributes> spsItems = attrs.getSequence(Tag.ScheduledProcedureStepSequence).iterator();
        while (spsItems.hasNext()) {
            Attributes sps = spsItems.next();
            validateSPSStartDateTime(sps, msh);
            if (!attrs.containsValue(Tag.AdmissionID))
                adjustAdmissionID(arcHL7App, attrs, msg);

            String spsStatus = sps.getString(Tag.ScheduledProcedureStepStatus);
            for (HL7OrderSPSStatus hl7OrderSPSStatus : arcHL7App.hl7OrderSPSStatuses())
                if (Arrays.asList(hl7OrderSPSStatus.getOrderControlStatusCodes()).contains(spsStatus)) {
                    sps.setString(Tag.ScheduledProcedureStepStatus, VR.CS, hl7OrderSPSStatus.getSPSStatus().name());
                    break;
                }

            if (sps.getString(Tag.ScheduledProcedureStepStatus).contains("_")) {
                spsItems.remove();
                LOG.warn("MWL item will not be created/updated; no Scheduled Procedure Step Status configured with ORC-1_ORC-5 : {}",
                        spsStatus);
                throw new HL7Exception(
                        new ERRSegment(msh)
                                .setHL7ErrorCode(ERRSegment.REQUIRED_FIELD_MISSING)
                                .setErrorLocation(ORDER_CONTROL)
                                .setUserMessage("Invalid order control in field 1 and/or invalid order status in field 5"));
            } else {
                if (!sps.containsValue(Tag.ScheduledProcedureStepID)) {
                    LOG.info("Missing Scheduled ProcedureStep ID in HL7 message");
                    if (uidsGenerated) {
                        idService.newScheduledProcedureStepID(arcHL7App.mwlScheduledProcedureStepIDGenerator(), sps);
                        LOG.info("Generate Scheduled ProcedureStep ID {}", sps.getString(Tag.ScheduledProcedureStepID));
                    }
                    else {
                        sps.setString(Tag.ScheduledProcedureStepID, VR.SH, attrs.getString(Tag.RequestedProcedureID));
                        LOG.info("Derived Scheduled Procedure Step ID from Requested Procedure ID {}",
                                sps.getString(Tag.ScheduledProcedureStepID));
                    }
                }

                if (!sps.containsValue(Tag.ScheduledStationAETitle))
                    adjustScheduledStations(hl7OrderScheduledStations, sps);
            }
        }
    }

    private void adjustAdmissionID(ArchiveHL7ApplicationExtension arcHL7App, Attributes attrs, UnparsedHL7Message msg)
            throws HL7Exception {
        HL7OrderMissingAdmissionIDPolicy hl7OrderMissingAdmissionIDPolicy = arcHL7App.hl7OrderMissingAdmissionIDPolicy();
        HL7Segment msh = msg.msh();
        switch (hl7OrderMissingAdmissionIDPolicy) {
            case REJECT:
                throw new HL7Exception(
                        new ERRSegment(msh)
                                .setHL7ErrorCode(ERRSegment.REQUIRED_FIELD_MISSING)
                                .setErrorLocation(ADMISSION_ID)
                                .setUserMessage("Missing admission ID"));
            case ACCESSION_AS_ADMISSION:
                String accNo = attrs.getString(Tag.AccessionNumber);
                if (accNo == null)
                    throw new HL7Exception(
                            new ERRSegment(msh)
                                    .setHL7ErrorCode(ERRSegment.REQUIRED_FIELD_MISSING)
                                    .setErrorLocation(msh.getMessageType().equals(IMAGING_ORDER_MSG)
                                            ? ACCESSION_NO_IMAGING_ORDER : ACCESSION_NO_GENERAL_ORDER)
                                    .setUserMessage("Failed to derive Admission ID from accession number"));
                attrs.setString(Tag.AdmissionID, VR.LO, accNo);
        }
    }

    private boolean adjustIdentifiers(Attributes attrs, ArchiveHL7ApplicationExtension arcHL7App, HL7Segment msh)
            throws HL7Exception {
        boolean uidsGenerated = false;
        String messageType = msh.getMessageType();
        String reqProcID = attrs.getString(Tag.RequestedProcedureID);
        String studyIUID = attrs.getString(Tag.StudyInstanceUID);
        String accessionNum = attrs.getString(Tag.AccessionNumber);

        if (studyIUID == null) {
            LOG.info("StudyInstanceUID missing in HL7 order message {}", messageType);
            if (reqProcID != null) {
                studyIUID = UIDUtils.createNameBasedUID(reqProcID.getBytes());
                LOG.info("Derived StudyInstanceUID from RequestedProcedureID[={}] : {}",
                        reqProcID, studyIUID);
            } else switch (arcHL7App.hl7OrderMissingStudyIUIDPolicy()) {
                case REJECT:
                    throw new HL7Exception(
                            new ERRSegment(msh)
                                    .setHL7ErrorCode(ERRSegment.REQUIRED_FIELD_MISSING)
                                    .setErrorLocation(messageType.equals(IMAGING_ORDER_MSG)
                                            ? STUDY_UID_IMAGING_ORDER : STUDY_UID_GENERAL_ORDER)
                                    .setUserMessage("Missing study instance uid"));
                case ACCESSION_BASED:
                    if (accessionNum == null)
                        throw new HL7Exception(
                                new ERRSegment(msh)
                                        .setHL7ErrorCode(ERRSegment.REQUIRED_FIELD_MISSING)
                                        .setErrorLocation(messageType.equals(IMAGING_ORDER_MSG)
                                                ? ACCESSION_NO_IMAGING_ORDER : ACCESSION_NO_GENERAL_ORDER)
                                        .setUserMessage("Missing accession number"));
                    else {
                        studyIUID = UIDUtils.createNameBasedUID(accessionNum.getBytes());
                        attrs.setString(Tag.RequestedProcedureID, VR.SH, accessionNum);
                        LOG.info("Derived StudyInstanceUID from AccessionNumber[={}] : {}\n"
                                        + " RequestedProcedureID shall be equal to AccessionNumber.",
                                accessionNum, studyIUID);
                    }
                    break;
                case GENERATE:
                    studyIUID = UIDUtils.createUID();
                    idService.newRequestedProcedureID(arcHL7App.mwlRequestedProcedureIDGenerator(), attrs);
                    uidsGenerated = true;
                    break;
            }
            attrs.setString(Tag.StudyInstanceUID, VR.UI, studyIUID);
        } else if (reqProcID == null) {
            reqProcID = UIDUtils.createNameBasedUID(studyIUID.getBytes());
            LOG.info("Derive missing RequestedProcedureID in HL7 message from StudyInstanceUID[={}] : {}",
                    studyIUID, reqProcID);
            attrs.setString(Tag.RequestedProcedureID, VR.SH, reqProcID);
        }
        return uidsGenerated;
    }

    private void adjustScheduledStations(Collection<Device> hl7OrderScheduledStations, Attributes sps) {
        List<String> ssAETs = new ArrayList<>();
        hl7OrderScheduledStations.forEach(device -> ssAETs.addAll(device.getApplicationAETitles()));

        if (!ssAETs.isEmpty())
            sps.setString(Tag.ScheduledStationAETitle, VR.AE, ssAETs.toArray(new String[0]));

        String[] ssNames = hl7OrderScheduledStations.stream()
                            .map(Device::getStationName)
                            .filter(Objects::nonNull)
                            .toArray(String[]::new);
        if (ssNames.length > 0)
            sps.setString(Tag.ScheduledStationName, VR.SH, ssNames);
    }
}
