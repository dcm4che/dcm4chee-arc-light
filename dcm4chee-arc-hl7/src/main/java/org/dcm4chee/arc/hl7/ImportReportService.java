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
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
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

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.hl7.HL7Exception;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4che3.net.hl7.service.DefaultHL7Service;
import org.dcm4che3.net.hl7.service.HL7Service;
import org.dcm4che3.util.UIDUtils;
import org.dcm4chee.arc.conf.ArchiveHL7ApplicationExtension;
import org.dcm4chee.arc.patient.PatientService;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreService;
import org.dcm4chee.arc.store.StoreSession;
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
 * @since May 2016
 */
@ApplicationScoped
@Typed(HL7Service.class)
class ImportReportService extends AbstractHL7Service {

    @Inject
    private PatientService patientService;

    @Inject
    private StoreService storeService;

    public ImportReportService() {
        super("ORU^R01");
    }

    @Override
    protected void process(HL7Application hl7App, Socket s, UnparsedHL7Message msg) throws Exception {
        PatientUpdateService.updatePatient(hl7App, s, msg, patientService);
        importReport(hl7App, s, msg);
    }

    private void importReport(HL7Application hl7App, Socket s, UnparsedHL7Message msg)
            throws ConfigurationException, IOException, SAXException, TransformerConfigurationException {
        ArchiveHL7ApplicationExtension arcHL7App =
                hl7App.getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class);
        String aet = arcHL7App.getAETitle();
        if (aet == null) {
            throw new ConfigurationException("No AE Title associated with HL7 Application: "
                    + hl7App.getApplicationName());
        }
        ApplicationEntity ae = hl7App.getDevice().getApplicationEntity(aet);
        if (ae == null) {
            throw new ConfigurationException("No local AE with AE Title " + aet
                    + " associated with HL7 Application: " + hl7App.getApplicationName());
        }
        HL7Segment msh = msg.msh();
        String hl7cs = msh.getField(17, hl7App.getHL7DefaultCharacterSet());
        Attributes attrs = SAXTransformer.transform(
                msg.data(), hl7cs, arcHL7App.importReportTemplateURI(), null);
        adjust(attrs);
        try (StoreSession session = storeService.newStoreSession(s, msh, ae)) {
            StoreContext ctx = storeService.newStoreContext(session);
            ctx.setSopClassUID(attrs.getString(Tag.SOPClassUID));
            ctx.setSopInstanceUID(attrs.getString(Tag.SOPInstanceUID));
            ctx.setReceiveTransferSyntax(UID.ExplicitVRLittleEndian);
            storeService.store(ctx, attrs);
        }
    }

    private void adjust(Attributes attrs) {
        if (attrs.getString(Tag.StudyInstanceUID) == null)
            attrs.setString(Tag.StudyInstanceUID, VR.valueOf("UI"), UIDUtils.createUID());
        if (attrs.getString(Tag.SOPInstanceUID) == null)
            attrs.setString(Tag.SOPInstanceUID, VR.valueOf("UI"), UIDUtils.createUID());
        if (attrs.getString(Tag.SeriesInstanceUID) == null)
            attrs.setString(Tag.SeriesInstanceUID, VR.valueOf("UI"),
                    UIDUtils.createNameBasedUID(attrs.getString(Tag.SOPInstanceUID).getBytes()));
    }
}
