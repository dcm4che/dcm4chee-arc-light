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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.*;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.RetrieveTask;
import org.dcm4chee.arc.entity.Location;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveEnd;
import org.dcm4chee.arc.retrieve.RetrieveStart;
import org.dcm4chee.arc.store.InstanceLocations;
import org.dcm4chee.arc.store.scu.CStoreSCU;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.dcm4che3.net.TransferCapability.Role.SCP;
import static org.dcm4che3.net.TransferCapability.Role.SCU;

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
                Association storeas = localAE.connect(ctx.getDestinationAE(), createAARQ(ctx));
                for (Iterator<InstanceLocations> iter = ctx.getMatches().iterator(); iter.hasNext();) {
                    InstanceLocations inst = iter.next();
                    if (storeas.getTransferSyntaxesFor(inst.getSopClassUID()).isEmpty()) {
                        iter.remove();
                        ctx.incrementFailed();
                        ctx.addFailedMatch(inst);
                        LOG.info("{}: failed to send {} to {} - no Presentation Context accepted",
                                ctx.getRequestAssociation(), inst, ctx.getDestinationAETitle());
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

    private AAssociateRQ createAARQ(RetrieveContext ctx) {
        AAssociateRQ aarq = new AAssociateRQ();
        ApplicationEntity localAE = ctx.getLocalApplicationEntity();
        ApplicationEntity destAE = ctx.getDestinationAE();
        if (!localAE.isMasqueradeCallingAETitle(ctx.getDestinationAETitle()))
            aarq.setCallingAET(ctx.getCallingAET());
        boolean noDestinationRestriction = destAE.getTransferCapabilitiesWithRole(SCP).isEmpty();
        for (Iterator<InstanceLocations> iter = ctx.getMatches().iterator(); iter.hasNext();) {
            InstanceLocations inst = iter.next();
            String cuid = inst.getSopClassUID();
            TransferCapability localTC = localAE.getTransferCapabilityFor(cuid, SCU);
            TransferCapability destTC = noDestinationRestriction ? null : destAE.getTransferCapabilityFor(cuid, SCP);
            List<Location> locations = inst.getLocations();
            if (!aarq.containsPresentationContextFor(cuid) && !isVideo(locations)) {
                if (noDestinationRestriction) {
                    addPresentationContext(aarq, cuid, UID.ImplicitVRLittleEndian, localTC);
                    addPresentationContext(aarq, cuid, UID.ExplicitVRLittleEndian, localTC);
                } else {
                    addPresentationContext(aarq, cuid, UID.ImplicitVRLittleEndian, localTC, destTC);
                    addPresentationContext(aarq, cuid, UID.ExplicitVRLittleEndian, localTC, destTC);
                }
            }
            for (Location location : locations) {
                String tsuid = location.getTransferSyntaxUID();
                if (!tsuid.equals(UID.ImplicitVRLittleEndian) &&
                        !tsuid.equals(UID.ExplicitVRLittleEndian))
                    if (noDestinationRestriction) {
                        addPresentationContext(aarq, cuid, tsuid, localTC);
                    } else {
                        addPresentationContext(aarq, cuid, tsuid, localTC, destTC);
                    }
            }
        }
        return aarq;
    }

    private static boolean isVideo(List<Location> locations) {
        if (!locations.isEmpty())
            switch (locations.get(0).getTransferSyntaxUID()) {
                case UID.MPEG2MPML:
                case UID.MPEG2MPMLF:
                case UID.MPEG2MPHL:
                case UID.MPEG2MPHLF:
                case UID.MPEG4HP41:
                case UID.MPEG4HP41F:
                case UID.MPEG4HP41BD:
                case UID.MPEG4HP41BDF:
                case UID.MPEG4HP422D:
                case UID.MPEG4HP422DF:
                case UID.MPEG4HP423D:
                case UID.MPEG4HP423DF:
                case UID.MPEG4HP42STEREO:
                case UID.MPEG4HP42STEREOF:
                case UID.HEVCMP51:
                case UID.HEVCM10P51:
                    return true;
            }
        return false;
    }

    private static void addPresentationContext(AAssociateRQ aarq, String cuid, String ts,
            TransferCapability tc1, TransferCapability tc2) {
        if (tc1 != null && tc1.containsTransferSyntax(ts))
            addPresentationContext(aarq, cuid, ts, tc2);
    }

    private static void addPresentationContext(AAssociateRQ aarq, String cuid, String ts, TransferCapability tc) {
        if (tc != null && tc.containsTransferSyntax(ts))
            aarq.addPresentationContextFor(cuid, ts);
    }

    @Override
    public RetrieveTask newRetrieveTaskSTORE(RetrieveContext ctx) throws DicomServiceException {
        return new RetrieveTaskImpl(ctx, retrieveStart, retrieveEnd, openMultipleAssocations(ctx));
    }

    @Override
    public RetrieveTask newRetrieveTaskMOVE(
            Association as, PresentationContext pc, Attributes rq, RetrieveContext ctx)
            throws DicomServiceException {
        RetrieveTaskImpl retrieveTask = new RetrieveTaskImpl(ctx, retrieveStart, retrieveEnd,
                openMultipleAssocations(ctx));
        retrieveTask.setRequestAssociation(Dimse.C_MOVE_RQ, as, pc, rq);
        return retrieveTask;
    }

    private Association[] openMultipleAssocations(RetrieveContext ctx) throws DicomServiceException {
        Association storeas = openAssociation(ctx);
        ctx.setStoreAssociation(storeas);
        Association[] storeass = new Association[Math.min(
                ctx.getArchiveAEExtension().maxStoreAssociationsTo(ctx.getDestinationAETitle()),
                Math.max(ctx.getNumberOfMatches(), 1))];
        storeass[0] = storeas;
        ApplicationEntity localAE = ctx.getLocalApplicationEntity();
        ApplicationEntity destinationAE = ctx.getDestinationAE();
        AAssociateRQ acrq = storeas.getAAssociateRQ();
        int count = 0;
        while (++count < storeass.length) {
            try {
                storeass[count] = localAE.connect(destinationAE, acrq);
            } catch (Exception e) {
                LOG.warn("failed to open additional association to {}:\n",
                        ctx.getDestinationAETitle());
                return Arrays.copyOf(storeass, count);
            }
        }
        return storeass;
    }

    @Override
    public RetrieveTask newRetrieveTaskGET(
            Association as, PresentationContext pc, Attributes rq, RetrieveContext ctx)
            throws DicomServiceException {
        ctx.setStoreAssociation(as);
        RetrieveTaskImpl retrieveTask = new RetrieveTaskImpl(ctx, retrieveStart, retrieveEnd, as);
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
