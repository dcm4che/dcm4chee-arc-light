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

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.hl7.ERRSegment;
import org.dcm4che3.hl7.HL7Exception;
import org.dcm4che3.hl7.HL7Message;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4che3.net.hl7.service.DefaultHL7Service;
import org.dcm4che3.net.hl7.service.HL7Service;
import org.dcm4chee.arc.conf.ArchiveHL7ApplicationExtension;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.patient.*;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Typed;
import javax.inject.Inject;
import java.net.Socket;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Sep 2015
 */
@ApplicationScoped
@Typed(HL7Service.class)
class PatientUpdateService extends DefaultHL7Service {

    private static final String[] MESSAGE_TYPES = {
            "ADT^A01",
            "ADT^A02",
            "ADT^A03",
            "ADT^A04",
            "ADT^A05",
            "ADT^A06",
            "ADT^A07",
            "ADT^A08",
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

    public PatientUpdateService() {
        super(MESSAGE_TYPES);
    }

    @Override
    public UnparsedHL7Message onMessage(HL7Application hl7App, Connection conn, Socket s, UnparsedHL7Message msg)
            throws HL7Exception {
        ArchiveHL7Message archiveHL7Message = new ArchiveHL7Message(
                HL7Message.makeACK(msg.msh(), HL7Exception.AA, null).getBytes(null));
        updatePatient(hl7App, s, msg, patientService, archiveHL7Message);
        return archiveHL7Message;
    }

    static Patient updatePatient(HL7Application hl7App, Socket s, UnparsedHL7Message msg, PatientService patientService,
                                 ArchiveHL7Message archiveHL7Message)
            throws HL7Exception {
        ArchiveHL7ApplicationExtension arcHL7App =
                hl7App.getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class);
        HL7Segment msh = msg.msh();

        Attributes attrs = transform(msg, arcHL7App);
        if (arcHL7App.hl7VeterinaryUsePatientName())
            useHL7VeterinaryPatientName(attrs);

        PatientMgtContext ctx = patientService.createPatientMgtContextHL7(hl7App, s, msg);
        ctx.setAttributes(attrs);
        if (ctx.getPatientID() == null)
            throw new HL7Exception(
                    new ERRSegment(msg.msh())
                            .setHL7ErrorCode(ERRSegment.RequiredFieldMissing)
                            .setErrorLocation("PID^1^3")
                            .setUserMessage("Missing PID-3"));
        Attributes mrg = attrs.getNestedDataset(Tag.ModifiedAttributesSequence);
        if (mrg == null) {
            try {
                Patient patient = patientService.updatePatient(ctx);
                archiveHL7Message.setPatRecEventActionCode(ctx.getEventActionCode());
                return patient;
            } catch (PatientMergedException e) {
                throw new HL7Exception(
                        new ERRSegment(msg.msh())
                                .setHL7ErrorCode(ERRSegment.UnknownKeyIdentifier)
                                .setUserMessage(e.getMessage()));
            } catch (NonUniquePatientException e) {
                throw new HL7Exception(
                        new ERRSegment(msg.msh())
                                .setHL7ErrorCode(ERRSegment.DuplicateKeyIdentifier)
                                .setUserMessage(e.getMessage()));
            } catch (Exception e) {
                throw new HL7Exception(
                        new ERRSegment(msg.msh())
                                .setHL7ErrorCode(ERRSegment.ApplicationInternalError)
                                .setUserMessage(e.getMessage()));
            }
        }

        ctx.setPreviousAttributes(mrg);
        if (ctx.getPreviousPatientID() == null)
            throw new HL7Exception(
                    new ERRSegment(msg.msh())
                        .setHL7ErrorCode(ERRSegment.RequiredFieldMissing)
                        .setErrorLocation("MRG^1^1")
                        .setUserMessage("Missing MRG-1"));
        try {
            return "ADT^A47".equals(msh.getMessageType())
                    ? patientService.changePatientID(ctx)
                    : patientService.mergePatient(ctx);
        } catch (PatientTrackingNotAllowedException e) {
            throw new HL7Exception(
                    new ERRSegment(msg.msh())
                            .setHL7ErrorCode(ERRSegment.DuplicateKeyIdentifier)
                            .setErrorLocation("PID^1^3")
                            .setUserMessage(e.getMessage()));
        } catch (CircularPatientMergeException e) {
            throw new HL7Exception(
                    new ERRSegment(msg.msh())
                            .setHL7ErrorCode(ERRSegment.DuplicateKeyIdentifier)
                            .setErrorLocation("MRG^1^1")
                            .setUserMessage("MRG-1 matches PID-3"));
        } catch (Exception e) {
            throw new HL7Exception(
                    new ERRSegment(msg.msh())
                            .setHL7ErrorCode(ERRSegment.ApplicationInternalError)
                            .setUserMessage(e.getMessage()));
        } finally {
            archiveHL7Message.setPatRecEventActionCode(ctx.getEventActionCode());
        }
    }

    private static void useHL7VeterinaryPatientName(Attributes attrs) {
        String patientName = attrs.getString(Tag.PatientName);
        String responsiblePerson = attrs.getString(Tag.ResponsiblePerson);
        int index = patientName.indexOf('^', patientName.indexOf('^') + 1);
        patientName = index != -1
                ? patientName.substring(0, index)
                : !patientName.contains("^") && responsiblePerson != null
                    ? (responsiblePerson.contains("^")
                        ? responsiblePerson.substring(0, responsiblePerson.indexOf('^')) : responsiblePerson)
                        + '^' + patientName
                    : patientName;
        attrs.setString(Tag.PatientName, VR.PN, patientName);
    }

    private static Attributes transform(UnparsedHL7Message msg, ArchiveHL7ApplicationExtension arcHL7App) throws HL7Exception {
        try {
            return SAXTransformer.transform(
                    msg,
                    arcHL7App,
                    arcHL7App.patientUpdateTemplateURI(),
                    null);
        } catch (Exception e) {
            throw new HL7Exception(new ERRSegment(msg.msh()).setUserMessage(e.getMessage()), e);
        }
    }
}
