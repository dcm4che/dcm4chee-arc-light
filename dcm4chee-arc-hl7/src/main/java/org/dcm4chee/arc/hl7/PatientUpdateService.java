/*
 * ** BEGIN LICENSE BLOCK *****
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
 * ** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.hl7;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import org.dcm4che3.data.*;
import org.dcm4che3.hl7.ERRSegment;
import org.dcm4che3.hl7.HL7Exception;
import org.dcm4che3.hl7.HL7Message;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4che3.net.hl7.service.DefaultHL7Service;
import org.dcm4che3.net.hl7.service.HL7Service;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.ArchiveHL7ApplicationExtension;
import org.dcm4chee.arc.conf.HL7ReferredMergedPatientPolicy;
import org.dcm4chee.arc.conf.SPSStatus;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.patient.*;
import org.dcm4chee.arc.procedure.ProcedureContext;
import org.dcm4chee.arc.procedure.ProcedureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Socket;
import java.util.Iterator;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Sep 2015
 */
@ApplicationScoped
@Typed(HL7Service.class)
class PatientUpdateService extends DefaultHL7Service {
    private static final Logger LOG = LoggerFactory.getLogger(PatientUpdateService.class);
    private static final String PATIENT_IDENTIFIER = "PID^1^3";
    private static final String PRIOR_PATIENT_IDENTIFIER = "MRG^1^1";
    private static final String CHANGE_PATIENT_IDENTIFIER = "ADT^A47";
    private static final String MERGE_PATIENT_IDENTIFIER = "ADT^A40";

    private static final String[] MESSAGE_TYPES = {
            "ADT^A01",
            "ADT^A02",
            "ADT^A03",
            "ADT^A04",
            "ADT^A05",
            "ADT^A06",
            "ADT^A07",
            "ADT^A08",
            "ADT^A10",
            "ADT^A11",
            "ADT^A12",
            "ADT^A13",
            "ADT^A28",
            "ADT^A31",
            "ADT^A38",
            "ADT^A40",
            "ADT^A47",
    };

    @Inject
    private PatientService patientService;

    @Inject
    private ProcedureService procedureService;

    public PatientUpdateService() {
        super(MESSAGE_TYPES);
    }

    @Override
    public UnparsedHL7Message onMessage(HL7Application hl7App, Connection conn, Socket s, UnparsedHL7Message msg)
            throws HL7Exception {
        ArchiveHL7Message archiveHL7Message = new ArchiveHL7Message(
                HL7Message.makeACK(msg.msh(), HL7Exception.AA, null).getBytes(null));
        PatientMgtContext ctx = patientService.createPatientMgtContextHL7(hl7App, s, msg);
        transform(ctx);
        Patient patient = updatePatient(ctx, patientService, archiveHL7Message);
        ctx.setPatient(patient);
        updateProcedure(ctx);

        return archiveHL7Message;
    }

    static Patient updatePatient(PatientMgtContext ctx, PatientService patientService, ArchiveHL7Message archiveHL7Message)
            throws HL7Exception {
        Attributes attrs = ctx.getAttributes();
        HL7Application hl7App = ctx.getHL7Application();
        ArchiveHL7ApplicationExtension arcHL7App = ctx.getHL7Application()
                .getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class);
        adjustOtherPIDs(attrs, arcHL7App);
        if (arcHL7App.hl7VeterinaryUsePatientName())
            useHL7VeterinaryPatientName(attrs);

        UnparsedHL7Message msg = ctx.getUnparsedHL7Message();
        ctx.setAttributes(attrs);
        if (ctx.getPatientIDs().isEmpty())
            throw new HL7Exception(
                    new ERRSegment(msg.msh())
                            .setHL7ErrorCode(ERRSegment.REQUIRED_FIELD_MISSING)
                            .setErrorLocation(PATIENT_IDENTIFIER)
                            .setUserMessage("Missing patient identifier"));

        ArchiveDeviceExtension arcdev = hl7App.getDevice().getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
        ctx.setPatientIDs(arcdev.withTrustedIssuerOfPatientID(ctx.getPatientIDs()));
        if (ctx.getPatientIDs().isEmpty()) {
            throw new HL7Exception(
                    new ERRSegment(msg.msh())
                            .setHL7ErrorCode(ERRSegment.UNKNOWN_KEY_IDENTIFIER)
                            .setErrorLocation(PATIENT_IDENTIFIER)
                            .setUserMessage("Missing patient identifier with trusted assigning authority"));
        }
        Attributes mrg = attrs.getNestedDataset(Tag.ModifiedAttributesSequence);
        if (mrg == null)
            return createOrUpdatePatient(patientService, ctx, archiveHL7Message, arcHL7App);

        ctx.setPreviousAttributes(mrg);
        if (ctx.getPreviousPatientIDs().isEmpty())
            throw new HL7Exception(
                    new ERRSegment(msg.msh())
                            .setHL7ErrorCode(ERRSegment.REQUIRED_FIELD_MISSING)
                            .setErrorLocation(PRIOR_PATIENT_IDENTIFIER)
                            .setUserMessage("Missing prior patient identifier"));

        ctx.setPreviousPatientIDs(arcdev.withTrustedIssuerOfPatientID(ctx.getPreviousPatientIDs()));
        if (ctx.getPreviousPatientIDs().isEmpty()) {
            throw new HL7Exception(
                    new ERRSegment(msg.msh())
                            .setHL7ErrorCode(ERRSegment.UNKNOWN_KEY_IDENTIFIER)
                            .setErrorLocation(PRIOR_PATIENT_IDENTIFIER)
                            .setUserMessage("Missing prior patient identifier with trusted assigning authority"));
        }
        return changePIDOrMergePatient(patientService, ctx, archiveHL7Message, arcHL7App);
    }

    private static Patient createOrUpdatePatient(
            PatientService patientService, PatientMgtContext ctx, ArchiveHL7Message archiveHL7Message,
            ArchiveHL7ApplicationExtension arcHL7App) throws HL7Exception {
        try {
            return patientService.updatePatient(ctx);
        } catch (NonUniquePatientException e) {
            throw new HL7Exception(
                    new ERRSegment(ctx.getUnparsedHL7Message().msh())
                            .setHL7ErrorCode(ERRSegment.DUPLICATE_KEY_IDENTIFIER)
                            .setUserMessage(e.getMessage()));
        } catch (Exception e) {
            if (reject(e, arcHL7App, ctx.getUnparsedHL7Message()))
                throw new HL7Exception(
                        new ERRSegment(ctx.getUnparsedHL7Message().msh())
                                .setHL7ErrorCode(ERRSegment.APPLICATION_INTERNAL_ERROR)
                                .setUserMessage(e.getMessage()));
            else
                return null;
        } finally {
            archiveHL7Message.setPatRecEventActionCode(ctx.getEventActionCode());
        }
    }

    private static Patient changePIDOrMergePatient(
            PatientService patientService, PatientMgtContext ctx, ArchiveHL7Message archiveHL7Message,
            ArchiveHL7ApplicationExtension arcHL7App) throws HL7Exception {
        UnparsedHL7Message msg = ctx.getUnparsedHL7Message();
        try {
            return msg.msh().getMessageType().equals(CHANGE_PATIENT_IDENTIFIER)
                    ? patientService.changePatientID(ctx)
                    : patientService.mergePatient(ctx);
        } catch (PatientTrackingNotAllowedException|PatientAlreadyExistsException e) {
            throw new HL7Exception(
                    new ERRSegment(msg.msh())
                            .setHL7ErrorCode(ERRSegment.DUPLICATE_KEY_IDENTIFIER)
                            .setErrorLocation(PATIENT_IDENTIFIER)
                            .setUserMessage(e.getMessage()));
        } catch (CircularPatientMergeException e) {
            throw new HL7Exception(
                    new ERRSegment(msg.msh())
                            .setHL7ErrorCode(ERRSegment.DUPLICATE_KEY_IDENTIFIER)
                            .setErrorLocation(PRIOR_PATIENT_IDENTIFIER)
                            .setUserMessage("Prior patient identifier matches patient identifier"));
        } catch (Exception e) {
            if (reject(e, arcHL7App, msg))
                throw new HL7Exception(
                        new ERRSegment(msg.msh())
                                .setHL7ErrorCode(ERRSegment.APPLICATION_INTERNAL_ERROR)
                                .setUserMessage(e.getMessage()));
            else
                return null;
        } finally {
            archiveHL7Message.setPatRecEventActionCode(ctx.getEventActionCode());
        }
    }

    private static boolean reject(Exception e, ArchiveHL7ApplicationExtension arcHL7App, UnparsedHL7Message msg)
            throws HL7Exception {
        if (e instanceof PatientMergedException) {
            String messageType = msg.msh().getMessageType();
            HL7ReferredMergedPatientPolicy hl7ReferredMergedPatientPolicy = arcHL7App.hl7ReferredMergedPatientPolicy();
            if (hl7ReferredMergedPatientPolicy == HL7ReferredMergedPatientPolicy.REJECT)
                throw new HL7Exception(
                        new ERRSegment(msg.msh())
                                .setHL7ErrorCode(ERRSegment.UNKNOWN_KEY_IDENTIFIER)
                                .setErrorLocation(messageType.equals(MERGE_PATIENT_IDENTIFIER)
                                        ? PRIOR_PATIENT_IDENTIFIER
                                        : PATIENT_IDENTIFIER)
                                .setUserMessage(e.getMessage()));
            if (hl7ReferredMergedPatientPolicy == HL7ReferredMergedPatientPolicy.IGNORE_DUPLICATE_MERGE
                    && !messageType.equals(MERGE_PATIENT_IDENTIFIER))
                throw new HL7Exception(
                        new ERRSegment(msg.msh())
                                .setHL7ErrorCode(ERRSegment.UNKNOWN_KEY_IDENTIFIER)
                                .setErrorLocation(PATIENT_IDENTIFIER)
                                .setUserMessage(e.getMessage()));

            LOG.info("Ignore HL7ReferredMergedPatientPolicy[{}] for message type {} : {}",
                    hl7ReferredMergedPatientPolicy,
                    messageType,
                    e.getMessage());
            return false;
        }
        return true;
    }

    private static void useHL7VeterinaryPatientName(Attributes attrs) {
        String patientName = attrs.getString(Tag.PatientName);
        String responsiblePerson = attrs.getString(Tag.ResponsiblePerson);
        int index = patientName.indexOf('^', patientName.indexOf('^') + 1);
        patientName = index != -1
                ? patientName.substring(0, index)
                : !patientName.contains("^") && responsiblePerson != null
                ? (responsiblePerson.contains("^")
                ? responsiblePerson.substring(0, responsiblePerson.indexOf('^'))
                : responsiblePerson)
                + '^' + patientName
                : patientName;
        attrs.setString(Tag.PatientName, VR.PN, patientName);
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
                    arcHL7App.patientUpdateTemplateURI(),
                    tr -> {
                        if (hl7PrimaryAssigningAuthorityOfPatientID != null)
                            tr.setParameter("hl7PrimaryAssigningAuthorityOfPatientID",
                                    hl7PrimaryAssigningAuthorityOfPatientID.toString());
                    }));
        } catch (Exception e) {
            throw new HL7Exception(new ERRSegment(msg.msh()).setUserMessage(e.getMessage()), e);
        }
    }

    static void adjustOtherPIDs(Attributes attrs, ArchiveHL7ApplicationExtension arcHL7App) {
        Issuer hl7PrimaryAssigningAuthorityOfPatientID = arcHL7App.hl7PrimaryAssigningAuthorityOfPatientID();
        IDWithIssuer primaryPatIdentifier = IDWithIssuer.pidOf(attrs);
        adjustOtherPatientIDs(attrs, arcHL7App);
        Attributes mergedPatientAttrs = attrs.getNestedDataset(Tag.ModifiedAttributesSequence);
        if (mergedPatientAttrs != null)
            adjustOtherPatientIDs(mergedPatientAttrs, arcHL7App);

        if (hl7PrimaryAssigningAuthorityOfPatientID == null
                || primaryPatIdentifier == null
                || hl7PrimaryAssigningAuthorityOfPatientID.equals(primaryPatIdentifier.getIssuer()))
            return;

        LOG.info("None of the patient identifier pairs in PID-3 match with configured " +
                "Primary Assigning Authority of Patient ID : {}", hl7PrimaryAssigningAuthorityOfPatientID);
    }

    private static void adjustOtherPatientIDs(Attributes attrs, ArchiveHL7ApplicationExtension arcHL7App) {
        IDWithIssuer primaryPatIdentifier = IDWithIssuer.pidOf(attrs);
        switch (arcHL7App.hl7OtherPatientIDs()) {
            case ALL:
                break;
            case NONE:
                attrs.remove(Tag.OtherPatientIDsSequence);
                break;
            default:
                Sequence seq = attrs.getSequence(Tag.OtherPatientIDsSequence);
                if (seq == null) break;
                Iterator<Attributes> otherPIDs = seq.iterator();
                while (otherPIDs.hasNext()) {
                    IDWithIssuer otherPID = IDWithIssuer.pidOf(otherPIDs.next());
                    if (otherPID == null || !otherPID.equals(primaryPatIdentifier))
                        continue;

                    otherPIDs.remove();
                }
                if (seq.isEmpty())
                    attrs.remove(Tag.OtherPatientIDsSequence);
        }
    }

    private void updateProcedure(PatientMgtContext ctx) {
        ArchiveHL7ApplicationExtension arcHL7App =
                ctx.getHL7Application().getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class);
        String messageType = arcHL7App.hl7PatientArrivalMessageType();
        UnparsedHL7Message msg = ctx.getUnparsedHL7Message();
        if (messageType != null && messageType.equals(msg.msh().getMessageType())) {
            ProcedureContext procCtx = procedureService.createProcedureContext()
                    .setSocket(ctx.getSocket())
                    .setHL7Message(msg);
            procCtx.setArchiveHL7AppExtension(arcHL7App);
            procCtx.setPatient(ctx.getPatient());
            procCtx.setSpsStatus(SPSStatus.ARRIVED);
            procedureService.updateMWLStatus(procCtx, SPSStatus.SCHEDULED);
        }
    }
}
