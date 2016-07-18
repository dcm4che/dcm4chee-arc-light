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

package org.dcm4chee.arc.id.impl;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Issuer;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.IDGenerator;
import org.dcm4chee.arc.id.IDService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jun 2016
 */
@ApplicationScoped
public class IDServiceImpl implements IDService {

    private static final Logger LOG = LoggerFactory.getLogger(IDServiceImpl.class);

    @Inject
    private Device device;

    @Inject
    private IDServiceEJB ejb;

    @Override
    public String createID(IDGenerator.Name name) {
        IDGenerator generator = device.getDeviceExtension(ArchiveDeviceExtension.class).getIDGenerator(name);
        return String.format(generator.getFormat(), nextValue(generator.getName(), generator.getInitialValue()));
    }

    @Override
    public void newPatientID(Attributes attrs) {
        attrs.setString(Tag.PatientID, VR.LO, createID(IDGenerator.Name.PatientID));
        Issuer issuer = device.getIssuerOfPatientID();
        if (issuer != null)
            issuer.toIssuerOfPatientID(attrs);

    }

    @Override
    public void newAccessionNumber(Attributes attrs) {
        attrs.setString(Tag.AccessionNumber, VR.SH, createID(IDGenerator.Name.AccessionNumber));
        Issuer issuer = device.getIssuerOfAccessionNumber();
        if (issuer != null)
            attrs.newSequence(Tag.IssuerOfAccessionNumberSequence, 1).add(issuer.toItem());
    }

    @Override
    public void newRequestedProcedureID(Attributes attrs) {
        attrs.setString(Tag.RequestedProcedureID, VR.SH, createID(IDGenerator.Name.RequestedProcedureID));
    }

    @Override
    public void newScheduledProcedureStepID(Attributes attrs) {
        attrs.setString(Tag.ScheduledProcedureStepID, VR.SH, createID(IDGenerator.Name.ScheduledProcedureStepID));
    }

    @Override
    public int newLocationMultiReference() {
        return nextValue(IDGenerator.Name.LocationMultiReference, 0);
    }

    private int nextValue(IDGenerator.Name name, int initalValue) {
        try {
            return ejb.nextValue(name, initalValue);
        } catch (RuntimeException e) {
            LOG.info("Failed to create {} - retry\n", name, e);
            return ejb.nextValue(name, initalValue);
        }
    }
}
