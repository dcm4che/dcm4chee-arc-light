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
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.*;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.RetrieveTask;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.RestrictRetrieveAccordingTransferCapabilities;
import org.dcm4chee.arc.entity.Location;
import org.dcm4chee.arc.store.InstanceLocations;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveEnd;
import org.dcm4chee.arc.retrieve.RetrieveStart;
import org.dcm4chee.arc.store.scu.CStoreSCU;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.dcm4che3.net.TransferCapability.Role.SCP;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2015
 */
@ApplicationScoped
public class CStoreSCUImpl implements CStoreSCU {

    static final Logger LOG = LoggerFactory.getLogger(CStoreSCUImpl.class);

    @Inject @RetrieveStart
    private Event<RetrieveContext> retrieveStart;

    @Inject @RetrieveEnd
    private Event<RetrieveContext> retrieveEnd;

    private Association openAssociation(RetrieveContext ctx)
            throws DicomServiceException {
        try {
            try {
                ApplicationEntity localAE = ctx.getLocalApplicationEntity();
                RestrictRetrieveAccordingTransferCapabilities restrictRetrieveAccordingTransferCapabilities
                        = localAE.getAEExtension(ArchiveAEExtension.class).restrictRetrieveAccordingTransferCapabilities();
                List<InstanceLocations> noPresentationContextOffered = new ArrayList<>();
                Association storeas = localAE.connect(ctx.getDestinationAE(),
                        createAARQ(ctx, noPresentationContextOffered));
                for (InstanceLocations inst : noPresentationContextOffered) {
                    if (restrictRetrieveAccordingTransferCapabilities
                            == RestrictRetrieveAccordingTransferCapabilities.NO) {
                        ctx.incrementFailed();
                        ctx.addFailedSOPInstanceUID(inst.getSopInstanceUID());
                    } else {
                        ctx.decrementNumberOfMatches();
                    }
                    LOG.info("{}: failed to send {} - no Presentation Context offered",
                            storeas, inst);
                }
                for (Iterator<InstanceLocations> iter = ctx.getMatches().iterator(); iter.hasNext();) {
                    InstanceLocations inst = iter.next();
                    if (storeas.getTransferSyntaxesFor(inst.getSopClassUID()).isEmpty()) {
                        iter.remove();
                        if (restrictRetrieveAccordingTransferCapabilities
                                != RestrictRetrieveAccordingTransferCapabilities.YES) {
                            ctx.incrementFailed();
                            ctx.addFailedSOPInstanceUID(inst.getSopInstanceUID());
                        } else
                            ctx.decrementNumberOfMatches();
                        LOG.info("{}: failed to send {} - no Presentation Context accepted",
                                storeas, inst);
                    }
                }
                return storeas;
            } catch (Exception e) {
                throw new DicomServiceException(Status.UnableToPerformSubOperations, e);
            }
        } catch (DicomServiceException e) {
            ctx.setException(e);
            retrieveStart.fire(ctx);
            throw e;
        }
    }

    private AAssociateRQ createAARQ(RetrieveContext ctx, List<InstanceLocations> noPresentationContextOffered) {
        AAssociateRQ aarq = new AAssociateRQ();
        ApplicationEntity localAE = ctx.getLocalApplicationEntity();
        ApplicationEntity destAE = ctx.getDestinationAE();
        if (!localAE.isMasqueradeCallingAETitle(ctx.getDestinationAETitle()))
            aarq.setCallingAET(ctx.getLocalAETitle());
        boolean considerConfiguredTCs = !destAE.getTransferCapabilitiesWithRole(SCP).isEmpty();
        for (Iterator<InstanceLocations> iter = ctx.getMatches().iterator(); iter.hasNext();) {
            InstanceLocations inst = iter.next();
            String cuid = inst.getSopClassUID();
            TransferCapability configuredTCs = null;
            if (considerConfiguredTCs && ((configuredTCs = destAE.getTransferCapabilityFor(cuid, SCP)) == null)) {
                iter.remove();
                noPresentationContextOffered.add(inst);
                continue;
            }
            if (!aarq.containsPresentationContextFor(cuid) && !isVideo(inst)) {
                addPresentationContext(aarq, cuid, UID.ImplicitVRLittleEndian, configuredTCs);
                addPresentationContext(aarq, cuid, UID.ExplicitVRLittleEndian, configuredTCs);
            }
            for (Location location : inst.getLocations()) {
                String tsuid = location.getTransferSyntaxUID();
                if (!tsuid.equals(UID.ImplicitVRLittleEndian) &&
                        !tsuid.equals(UID.ExplicitVRLittleEndian))
                    addPresentationContext(aarq, cuid, tsuid, configuredTCs);
            }
        }
        return aarq;
    }

    private boolean isVideo(InstanceLocations inst) {
        switch (inst.getLocations().get(0).getTransferSyntaxUID()) {
            case UID.MPEG2:
            case UID.MPEG2MainProfileHighLevel:
            case UID.MPEG4AVCH264HighProfileLevel41:
            case UID.MPEG4AVCH264BDCompatibleHighProfileLevel41:
            case UID.MPEG4AVCH264HighProfileLevel42For2DVideo:
            case UID.MPEG4AVCH264HighProfileLevel42For3DVideo:
            case UID.MPEG4AVCH264StereoHighProfileLevel42:
            case UID.HEVCH265MainProfileLevel51:
            case UID.HEVCH265Main10ProfileLevel51:
                return true;
        }
        return false;
    }

    private void addPresentationContext(AAssociateRQ aarq, String cuid, String ts, TransferCapability configuredTCs) {
        if (configuredTCs == null || configuredTCs.containsTransferSyntax(ts))
            aarq.addPresentationContextFor(cuid, ts);
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

    @Override
    public Attributes store(ApplicationEntity localAE, ApplicationEntity remoteAE, int priority, Attributes inst)
            throws Exception {
        String cuid = inst.getString(Tag.SOPClassUID);
        String iuid = inst.getString(Tag.SOPInstanceUID);
        Association as = localAE.connect(remoteAE, createAARQ(localAE, remoteAE.getAETitle(), cuid));
        try {
            DimseRSP rsp = as.cstore(cuid, iuid, priority, new DataWriterAdapter(inst),
                    selectTransferSyntax(as.getTransferSyntaxesFor(cuid)));
            rsp.next();
            return rsp.getCommand();
        } finally {
            as.release();
        }
    }

    private String selectTransferSyntax(Set<String> accepted) {
        return accepted.contains(UID.ExplicitVRLittleEndian) ? UID.ExplicitVRLittleEndian : UID.ImplicitVRLittleEndian;
    }

    private AAssociateRQ createAARQ(ApplicationEntity localAE, String calledAET, String cuid) {
        AAssociateRQ aarq = new AAssociateRQ();
        if (!localAE.isMasqueradeCallingAETitle(calledAET))
            aarq.setCallingAET(localAE.getAETitle());
        aarq.addPresentationContextFor(cuid, UID.ExplicitVRLittleEndian);
        aarq.addPresentationContextFor(cuid, UID.ImplicitVRLittleEndian);
        return aarq;
    }
}
