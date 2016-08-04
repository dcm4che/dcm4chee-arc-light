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

package org.dcm4chee.arc.store.scu.impl;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Dimse;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.RetrieveTask;
import org.dcm4chee.arc.entity.Location;
import org.dcm4chee.arc.retrieve.InstanceLocations;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveEnd;
import org.dcm4chee.arc.retrieve.RetrieveStart;
import org.dcm4chee.arc.store.scu.CStoreSCU;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2015
 */
@ApplicationScoped
public class CStoreSCUImpl implements CStoreSCU {

    @Inject @RetrieveStart
    private Event<RetrieveContext> retrieveStart;

    @Inject @RetrieveEnd
    private Event<RetrieveContext> retrieveEnd;

    private Association openAssociation(RetrieveContext ctx)
            throws DicomServiceException {
        try {
            try {
                ApplicationEntity localAE = ctx.getLocalApplicationEntity();
                return localAE.connect(ctx.getDestinationAE(), createAARQ(ctx));
            } catch (Exception e) {
                throw new DicomServiceException(Status.UnableToPerformSubOperations, e);
            }
        } catch (DicomServiceException e) {
            ctx.setException(e);
            retrieveStart.fire(ctx);
            throw e;
        }
    }

    private AAssociateRQ createAARQ(RetrieveContext ctx) {
        AAssociateRQ aarq = new AAssociateRQ();
        ApplicationEntity localAE = ctx.getLocalApplicationEntity();
        if (!localAE.isMasqueradeCallingAETitle(ctx.getDestinationAETitle()))
            aarq.setCallingAET(ctx.getLocalAETitle());
        for (InstanceLocations inst : ctx.getMatches()) {
            String cuid = inst.getSopClassUID();
            if (!aarq.containsPresentationContextFor(cuid)) {
                aarq.addPresentationContextFor(cuid, UID.ImplicitVRLittleEndian);
                aarq.addPresentationContextFor(cuid, UID.ExplicitVRLittleEndian);
            }
            for (Location location : inst.getLocations()) {
                String tsuid = location.getTransferSyntaxUID();
                if (!tsuid.equals(UID.ImplicitVRLittleEndian) &&
                        !tsuid.equals(UID.ExplicitVRLittleEndian))
                    aarq.addPresentationContextFor(cuid, tsuid);
            }
        }
        return aarq;
    }

    @Override
    public RetrieveTask newRetrieveTaskSTORE(RetrieveContext ctx) throws DicomServiceException {
        Association storeas = openAssociation(ctx);
        ctx.setStoreAssociation(storeas);
        return new RetrieveTaskImpl(ctx, storeas, retrieveStart, retrieveEnd);
    }

    @Override
    public RetrieveTask newRetrieveTaskMOVE(
            Association as, PresentationContext pc, Attributes rq, RetrieveContext ctx)
            throws DicomServiceException {
        Association storeas = openAssociation(ctx);
        ctx.setStoreAssociation(storeas);
        RetrieveTaskImpl retrieveTask = new RetrieveTaskImpl(ctx, storeas, retrieveStart, retrieveEnd);
        retrieveTask.setRequestAssociation(Dimse.C_MOVE_RQ, as, pc, rq);
        return retrieveTask;
    }

    @Override
    public RetrieveTask newRetrieveTaskGET(
            Association as, PresentationContext pc, Attributes rq, RetrieveContext ctx)
            throws DicomServiceException {
        ctx.setStoreAssociation(as);
        RetrieveTaskImpl retrieveTask = new RetrieveTaskImpl(ctx, as, retrieveStart, retrieveEnd);
        retrieveTask.setRequestAssociation(Dimse.C_GET_RQ, as, pc, rq);
        return retrieveTask;
    }
}
