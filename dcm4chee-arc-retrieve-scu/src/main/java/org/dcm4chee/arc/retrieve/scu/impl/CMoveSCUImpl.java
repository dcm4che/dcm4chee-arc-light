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

package org.dcm4chee.arc.retrieve.scu.impl;

import org.dcm4che3.conf.api.IApplicationEntityCache;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.*;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.ExtendedNegotiation;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.RetrieveTask;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveEnd;
import org.dcm4chee.arc.retrieve.RetrieveStart;
import org.dcm4chee.arc.retrieve.scu.CMoveSCU;
import org.dcm4chee.arc.store.scu.CStoreForwardSCU;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import java.util.EnumSet;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Dec 2015
 */
@ApplicationScoped
public class CMoveSCUImpl implements CMoveSCU {

    @Inject
    private IApplicationEntityCache aeCache;

    @Inject
    private CStoreForwardSCU storeForwardSCU;

    @Inject @RetrieveStart
    private Event<RetrieveContext> retrieveStart;

    @Inject @RetrieveEnd
    private Event<RetrieveContext> retrieveEnd;

    @Override
    public RetrieveTask newForwardRetrieveTask(
            final RetrieveContext ctx, PresentationContext pc, Attributes rq, Attributes keys,
            String callingAET, String otherCMoveSCP, String otherMoveDest) throws DicomServiceException {
        try {
            Association fwdas = openAssociation(ctx, pc, callingAET, otherCMoveSCP);
            if (otherMoveDest == null) {
                ctx.setForwardAssociation(fwdas);
                return new ForwardRetrieveTask.BackwardCMoveRSP(ctx, pc, rq, keys, fwdas);
            }
            ctx.setFallbackAssociation(fwdas);
            rq.setString(Tag.MoveDestination, VR.AE, otherMoveDest);
            storeForwardSCU.addRetrieveContext(ctx, callingAET);
            fwdas.addAssociationListener(new AssociationListener() {
                @Override
                public void onClose(Association association) {
                    storeForwardSCU.removeRetrieveContext(ctx, callingAET);
                }
            });
            return new ForwardRetrieveTask.ForwardCStoreRQ(ctx, pc, rq, keys, fwdas, retrieveStart, retrieveEnd);
        } catch (Exception e) {
            throw new DicomServiceException(Status.UnableToPerformSubOperations, e);
        }
    }

    @Override
    public void forwardMoveRQ(
            RetrieveContext ctx, PresentationContext pc, Attributes rq, Attributes keys,
            String callingAET, String otherCMoveSCP, String otherMoveDest) throws DicomServiceException {
        try {
            Association fwdas = openAssociation(ctx, pc, callingAET, otherCMoveSCP);
            if (otherMoveDest == null) {
                ctx.setForwardAssociation(fwdas);
                new ForwardRetrieveTask.UpdateRetrieveCtx(ctx, pc, rq, keys, fwdas).forwardMoveRQ();
            } else {
                ctx.setFallbackAssociation(fwdas);
                rq.setString(Tag.MoveDestination, VR.AE, otherMoveDest);
                storeForwardSCU.addRetrieveContext(ctx, callingAET);
                fwdas.addAssociationListener(new AssociationListener() {
                    @Override
                    public void onClose(Association association) {
                        storeForwardSCU.removeRetrieveContext(ctx, callingAET);
                    }
                });
                new ForwardRetrieveTask.ForwardCStoreRQ(ctx, pc, rq, keys, fwdas, retrieveStart, retrieveEnd).forwardMoveRQ();
            }
        } catch (Exception e) {
            throw new DicomServiceException(Status.UnableToPerformSubOperations, e);
        }
    }

    @Override
    public Association openAssociation(ApplicationEntity localAE, String calledAET) throws Exception {
        return localAE.connect(aeCache.get(calledAET), createAARQ());
    }

    private AAssociateRQ createAARQ() {
        AAssociateRQ aarq = new AAssociateRQ();
        aarq.addPresentationContext(new PresentationContext(
                1, UID.StudyRootQueryRetrieveInformationModelMOVE, UID.ImplicitVRLittleEndian));
        return aarq;
    }

    @Override
    public DimseRSP cmove(Association as, int priority, String destAET, Attributes keys) throws Exception {
        return as.cmove(
                UID.StudyRootQueryRetrieveInformationModelMOVE,
                priority,
                keys,
                UID.ImplicitVRLittleEndian,
                destAET);
    }

    private Association openAssociation(RetrieveContext ctx, PresentationContext pc,
            String callingAET, String otherCMoveSCP)
            throws Exception {
        ApplicationEntity remoteAE = aeCache.findApplicationEntity(otherCMoveSCP);
        Association as = ctx.getRequestAssociation();
        PresentationContext rqpc = as.getAAssociateRQ().getPresentationContext(pc.getPCID());
        AAssociateRQ aarq = new AAssociateRQ();
        if (callingAET != null)
            aarq.setCallingAET(callingAET);
        aarq.addPresentationContext(rqpc);
        aarq.addExtendedNegotiation(new ExtendedNegotiation(rqpc.getAbstractSyntax(),
                QueryOption.toExtendedNegotiationInformation(EnumSet.of(QueryOption.RELATIONAL))));
        Association fwdas = ctx.getLocalApplicationEntity().connect(remoteAE, aarq);
        fwdas.setProperty("forward-C-MOVE-RQ-for-Study", ctx.getStudyInstanceUID());
        return fwdas;
    }
}
