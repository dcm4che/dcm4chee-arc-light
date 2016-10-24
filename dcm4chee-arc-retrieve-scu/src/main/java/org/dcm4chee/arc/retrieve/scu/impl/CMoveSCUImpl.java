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

package org.dcm4chee.arc.retrieve.scu.impl;

import org.dcm4che3.conf.api.IApplicationEntityCache;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.*;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.ExtendedNegotiation;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.RetrieveTask;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveEnd;
import org.dcm4chee.arc.retrieve.scu.CMoveSCU;
import org.dcm4chee.arc.store.scu.CStoreForwardSCU;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import java.io.IOException;
import java.security.GeneralSecurityException;
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

    @Inject @RetrieveEnd
    private Event<RetrieveContext> retrieveEnd;

    @Override
    public RetrieveTask newForwardRetrieveTask(
            final RetrieveContext ctx, PresentationContext pc, Attributes rq, Attributes keys, String otherCMoveSCP,
            String otherMoveDest) throws DicomServiceException {
        try {
            Association fwdas = openAssociation(ctx, pc, otherCMoveSCP);
            if (otherMoveDest == null) {
                return new ForwardRetrieveTask.BackwardCMoveRSP(ctx, pc, rq, keys, fwdas);
            }
            ctx.setFallbackAssociation(fwdas);
            rq.setString(Tag.MoveDestination, VR.AE, otherMoveDest);
            storeForwardSCU.addRetrieveContext(ctx);
            fwdas.addAssociationListener(new AssociationListener() {
                @Override
                public void onClose(Association association) {
                    storeForwardSCU.removeRetrieveContext(ctx);
                }
            });
            return new ForwardRetrieveTask.ForwardCStoreRQ(ctx, pc, rq, keys, fwdas, retrieveEnd);
        } catch (Exception e) {
            throw new DicomServiceException(Status.UnableToPerformSubOperations, e);
        }
    }

    @Override
    public void forwardMoveRQ(
            RetrieveContext ctx, PresentationContext pc, Attributes rq, Attributes keys, String otherCMoveSCP,
            String otherMoveDest) throws DicomServiceException {
        try {
            Association fwdas = openAssociation(ctx, pc, otherCMoveSCP);
            if (otherMoveDest == null) {
                ctx.setForwardAssociation(fwdas);
                new ForwardRetrieveTask.UpdateRetrieveCtx(ctx, pc, rq, keys, fwdas).forwardMoveRQ();
            } else {
                ctx.setFallbackAssociation(fwdas);
                rq.setString(Tag.MoveDestination, VR.AE, otherMoveDest);
                storeForwardSCU.addRetrieveContext(ctx);
                fwdas.addAssociationListener(new AssociationListener() {
                    @Override
                    public void onClose(Association association) {
                        storeForwardSCU.removeRetrieveContext(ctx);
                    }
                });
                new ForwardRetrieveTask.ForwardCStoreRQ(ctx, pc, rq, keys, fwdas, retrieveEnd).forwardMoveRQ();
            }
        } catch (Exception e) {
            throw new DicomServiceException(Status.UnableToPerformSubOperations, e);
        }
    }

    private Association openAssociation(RetrieveContext ctx, PresentationContext pc, String otherCMoveSCP)
            throws Exception {
        ApplicationEntity remoteAE = aeCache.findApplicationEntity(otherCMoveSCP);
        Association as = ctx.getRequestAssociation();
        PresentationContext rqpc = as.getAAssociateRQ().getPresentationContext(pc.getPCID());
        AAssociateRQ aarq = new AAssociateRQ();
        if (!otherCMoveSCP.equals(ctx.getArchiveAEExtension().alternativeCMoveSCP()))
            aarq.setCallingAET(as.getCallingAET());
        aarq.addPresentationContext(rqpc);
        aarq.addExtendedNegotiation(new ExtendedNegotiation(rqpc.getAbstractSyntax(),
                QueryOption.toExtendedNegotiationInformation(EnumSet.of(QueryOption.RELATIONAL))));
        Association fwdas = ctx.getLocalApplicationEntity().connect(remoteAE, aarq);
        fwdas.setProperty("forward-C-MOVE-RQ-for-Study", ctx.getStudyInstanceUID());
        return fwdas;
    }
}
