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
 * Portions created by the Initial Developer are Copyright (C) 2015-2017
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
import org.dcm4che3.hl7.ERRSegment;
import org.dcm4che3.hl7.HL7Exception;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4che3.net.hl7.service.HL7Service;
import org.dcm4chee.arc.conf.ArchiveHL7ApplicationExtension;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.patient.CircularPatientMergeException;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.patient.PatientService;
import org.dcm4chee.arc.patient.PatientTrackingNotAllowedException;
import org.xml.sax.SAXException;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Typed;
import javax.inject.Inject;
import javax.xml.transform.TransformerConfigurationException;
import java.io.IOException;
import java.net.Socket;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Sep 2015
 */
@ApplicationScoped
@Typed(HL7Service.class)
class PatientUpdateService extends AbstractHL7Service {

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
    protected void process(HL7Application hl7App, Socket s, UnparsedHL7Message msg) throws Exception {
        updatePatient(hl7App, s, msg, patientService);
    }

    static Patient updatePatient(HL7Application hl7App, Socket s, UnparsedHL7Message msg, PatientService patientService)
            throws HL7Exception, IOException, SAXException, TransformerConfigurationException {
        ArchiveHL7ApplicationExtension arcHL7App =
                hl7App.getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class);
        HL7Segment msh = msg.msh();
        String hl7cs = msh.getField(17, hl7App.getHL7DefaultCharacterSet());
        Attributes attrs = SAXTransformer.transform(msg.data(), hl7cs, arcHL7App.patientUpdateTemplateURI(), null);
        PatientMgtContext ctx = patientService.createPatientMgtContextHL7(hl7App, s, msg);
        ctx.setAttributes(attrs);
        if (ctx.getPatientID() == null)
            throw new HL7Exception(
                    new ERRSegment(msg.msh())
                            .setHL7ErrorCode(ERRSegment.RequiredFieldMissing)
                            .setErrorLocation("PID^1^3")
                            .setUserMessage("Missing PID-3"));
        Attributes mrg = attrs.getNestedDataset(Tag.ModifiedAttributesSequence);
        if (mrg == null)
            return patientService.updatePatient(ctx);

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
        }
    }
}
