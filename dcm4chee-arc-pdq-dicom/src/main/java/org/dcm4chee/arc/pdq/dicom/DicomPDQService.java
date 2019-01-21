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
 * Portions created by the Initial Developer are Copyright (C) 2015-2018
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

package org.dcm4chee.arc.pdq.dicom;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Priority;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.Entity;
import org.dcm4chee.arc.conf.PDQServiceDescriptor;
import org.dcm4chee.arc.pdq.AbstractPDQService;
import org.dcm4chee.arc.pdq.PDQServiceException;
import org.dcm4chee.arc.query.scu.CFindSCU;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2018
 */
public class DicomPDQService extends AbstractPDQService {
    private final Device device;
    private final CFindSCU cFindSCU;

    public DicomPDQService(PDQServiceDescriptor descriptor, Device device, CFindSCU cFindSCU) {
        super(descriptor);
        this.device = device;
        this.cFindSCU = cFindSCU;
    }

    @Override
    public Attributes query(IDWithIssuer pid) throws PDQServiceException {
        return descriptor.getEntity() == Entity.Patient
                ? queryPatient(pid)
                : queryStudiesOfPatient(pid);
    }

    private Attributes queryPatient(IDWithIssuer pid) throws PDQServiceException {
        List<Attributes> attrs = findPatient(localAE(), calledAET(), pid, returnKeys());
        switch (attrs.size()) {
            case 0:
                return null;
            case 1:
                return attrs.get(0);
            default:
                throw new PDQServiceException("Patient ID '" + pid + "' not unique at " + descriptor);
        }
    }

    private Attributes queryStudiesOfPatient(IDWithIssuer pid) throws PDQServiceException {
        return findStudiesOfPatient(localAE(), calledAET(), pid, addStudyDate(returnKeys()))
                .stream()
                .max(Comparator.comparing(s -> s.getString(Tag.StudyDate, "")))
                .orElse(null);
    }

    private ApplicationEntity localAE() throws PDQServiceException {
        String aet = descriptor.getProperty("LocalAET", null);
        if (aet == null)
            throw new PDQServiceException("No property 'LocalAET' configured in " + descriptor);

        ApplicationEntity ae = device.getApplicationEntity(aet);
        if (ae == null)
            throw new PDQServiceException("Device '" + device.getDeviceName() +
                    "' does not provide AE '" + aet +
                    "' configured in property 'LocalAET' of " + descriptor);

        return ae;
    }

    private String calledAET() {
        return descriptor.getPDQServiceURI().getSchemeSpecificPart();
    }

    private int[] returnKeys() {
        return descriptor.getSelection().length > 0 ? descriptor.getSelection()
                : device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class)
                    .getAttributeFilter(Entity.Patient).getSelection();
    }

    private static int[] addStudyDate(int[] original) {
        int[] result = Arrays.copyOf(original, original.length + 1);
        result[original.length] = Tag.StudyDate;
        return result;
    }

    private List<Attributes> findPatient(ApplicationEntity localAE, String calledAET, IDWithIssuer pid,
                                         int[] returnKeys) throws PDQServiceException {
        try {
            return cFindSCU.findPatient(localAE, calledAET, Priority.NORMAL, pid, returnKeys);
        } catch (Exception e) {
            throw new PDQServiceException(e);
        }
    }

    private List<Attributes> findStudiesOfPatient(ApplicationEntity localAE, String calledAET, IDWithIssuer pid,
                                                  int... returnKeys) throws PDQServiceException {
        try {
            return cFindSCU.findStudiesOfPatient(localAE, calledAET, Priority.NORMAL, pid, returnKeys);
        } catch (Exception e) {
            throw new PDQServiceException(e);
        }
    }

}
