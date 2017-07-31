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
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.qmgt.Outcome;
import org.dcm4chee.arc.qmgt.QueueManager;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveEnd;
import org.dcm4chee.arc.retrieve.scu.CMoveSCU;
import org.dcm4chee.arc.store.scu.CStoreForwardSCU;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.jms.JMSException;
import javax.jms.ObjectMessage;
import java.io.IOException;
import java.util.EnumSet;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Dec 2015
 */
@ApplicationScoped
public class CMoveSCUImpl implements CMoveSCU {

    private static final Logger LOG = LoggerFactory.getLogger(CMoveSCUImpl.class);

    @Inject
    private QueueManager queueManager;

    @Inject
    private Device device;

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

    @Override
    public Outcome cmove(String localAET, String remoteAET, int priority, String destAET, Attributes keys)
            throws Exception {
        ApplicationEntity localAE = device.getApplicationEntity(localAET, true);
        Association as = openAssociation(localAE, remoteAET);
        try {
            final DimseRSP rsp = cmove(as, priority, destAET, keys);
            while (rsp.next());
            Attributes cmd = rsp.getCommand();
            int status = cmd.getInt(Tag.Status, -1);
            if (status == Status.Success || status == Status.OneOrMoreFailures) {
                    return new Outcome(
                            status == Status.Success ? QueueMessage.Status.COMPLETED : QueueMessage.Status.WARNING,
                            toOutcomeMessage(remoteAET, destAET, keys, cmd));
            }
            throw new DicomServiceException(status, cmd.getString(Tag.ErrorComment));
        } finally {
            try {
                as.release();
            } catch (IOException e) {
                LOG.info("{}: Failed to release association:\\n", as, e);
            }
        }
    }

    private String toOutcomeMessage(String remoteAET, String destAET, Attributes keys, Attributes rsp) {
        int completed = rsp.getInt(Tag.NumberOfCompletedSuboperations, 0);
        int warning = rsp.getInt(Tag.NumberOfWarningSuboperations, 0);
        int failed = rsp.getInt(Tag.NumberOfFailedSuboperations, 0);
        StringBuilder sb = new StringBuilder(256)
                .append("Export ")
                .append(keys.getString(Tag.QueryRetrieveLevel))
                .append("[suid:")
                .append(keys.getString(Tag.StudyInstanceUID))
                .append("] from ")
                .append(remoteAET)
                .append(" to ")
                .append(destAET)
                .append(" - completed:")
                .append(completed);
        if (warning > 0)
            sb.append(", ").append("warning:").append(warning);
        if (failed > 0)
            sb.append(", ").append("failed:").append(failed);
        return sb.toString();
    }

    @Override
    public void scheduleCMove(String localAET, String remoteAET, int priority, String destAET, Attributes keys) {
        try {
            ObjectMessage msg = queueManager.createObjectMessage(keys);
            msg.setStringProperty("LocalAET", localAET);
            msg.setStringProperty("RemoteAET", remoteAET);
            msg.setIntProperty("Priority", priority);
            msg.setStringProperty("DestinationAET", destAET);
            msg.setStringProperty("StudyInstanceUID", keys.getString(Tag.StudyInstanceUID));
            queueManager.scheduleMessage(QUEUE_NAME, msg);
        } catch (JMSException e) {
            throw QueueMessage.toJMSRuntimeException(e);
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
