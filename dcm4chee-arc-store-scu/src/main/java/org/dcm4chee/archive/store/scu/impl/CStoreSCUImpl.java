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
 * Portions created by the Initial Developer are Copyright (C) 2015
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

package org.dcm4chee.archive.store.scu.impl;

import org.dcm4che3.conf.api.ConfigurationNotFoundException;
import org.dcm4che3.conf.api.IApplicationEntityCache;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4chee.archive.entity.Location;
import org.dcm4chee.archive.retrieve.InstanceLocations;
import org.dcm4chee.archive.retrieve.RetrieveContext;
import org.dcm4chee.archive.store.scu.CStoreSCU;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2015
 */
@ApplicationScoped
public class CStoreSCUImpl implements CStoreSCU {

    @Inject
    private IApplicationEntityCache aeCache;

    @Override
    public Association openAssociation(RetrieveContext ctx, String destAET) throws DicomServiceException {
        try {
            ApplicationEntity remoteAE = aeCache.findApplicationEntity(destAET);
            ApplicationEntity localAE = ctx.getLocalApplicationEntity();
            return localAE.connect(remoteAE, createAARQ(ctx));
        } catch (ConfigurationNotFoundException e) {
            throw new DicomServiceException(Status.MoveDestinationUnknown, "Unknown Destination: " + destAET);
        } catch (Exception e) {
            throw new DicomServiceException(Status.UnableToPerformSubOperations, e);
        }
    }

    private AAssociateRQ createAARQ(RetrieveContext ctx) {
        AAssociateRQ aarq = new AAssociateRQ();
        for (InstanceLocations inst : ctx.getInstances()) {
            String cuid = inst.getSopClassUID();
            if (!aarq.containsPresentationContextFor(cuid)) {
                aarq.addPresentationContextFor(cuid, UID.ImplicitVRLittleEndian);
                aarq.addPresentationContextFor(cuid, UID.ExplicitVRLittleEndian);
            }
            for (Location location : inst.getLocations()) {
                String tsuid = location.getTransferSyntaxUID();
                if (tsuid != null &&
                        !tsuid.equals(UID.ImplicitVRLittleEndian) &&
                        !tsuid.equals(UID.ExplicitVRLittleEndian))
                    aarq.addPresentationContextFor(cuid, tsuid);
            }
        }
        return aarq;
    }
}
