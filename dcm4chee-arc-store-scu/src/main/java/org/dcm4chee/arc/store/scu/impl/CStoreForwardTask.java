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

package org.dcm4chee.arc.store.scu.impl;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.imageio.codec.Transcoder;
import org.dcm4che3.net.*;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.retrieve.InstanceLocations;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveService;
import org.dcm4chee.arc.store.StoreContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since May 2016
 */
class CStoreForwardTask implements Runnable {

    static final Logger LOG = LoggerFactory.getLogger(CStoreForwardTask.class);

    private final RetrieveContext ctx;
    private final Association rqas;
    private final Association storeas;
    private final LinkedBlockingQueue<WrappedStoreContext> queue = new LinkedBlockingQueue();

    public CStoreForwardTask(RetrieveContext ctx, Association storeas) {
        this.ctx = ctx;
        this.rqas = ctx.getRequestAssociation();
        this.storeas = storeas;
    }

    public void onStore(StoreContext storeContext) {
        if (storeas != null)
            queue.offer(new WrappedStoreContext(storeContext));
        else if (storeContext != null) {
            ctx.addFailedSOPInstanceUID(storeContext.getSopInstanceUID());
            ctx.incrementFailed();
        }
    }

    @Override
    public void run() {
        try {
            StoreContext storeCtx;
            while ((storeCtx = queue.take().storeContext) != null) {
                store(storeCtx);
            }
            storeas.waitForOutstandingRSP();
        } catch (InterruptedException e) {
            LOG.warn("{}: failed to wait for outstanding RSP on association to {}", rqas, storeas.getRemoteAET(), e);
        } finally {
            releaseStoreAssociation();
            ctx.decrementPendingCStoreForward();
        }
    }

    private void releaseStoreAssociation() {
        try {
            storeas.release();
        } catch (IOException e) {
            LOG.warn("{}: failed to release association to {}", rqas, storeas.getRemoteAET(), e);
        }
    }

    private void store(StoreContext storeCtx) {
        InstanceLocations inst = createInstanceLocations(storeCtx);
        ctx.addCStoreForward(inst);
        String cuid = inst.getSopClassUID();
        String iuid = inst.getSopInstanceUID();
        Set<String> tsuids = storeas.getTransferSyntaxesFor(cuid);
        try {
            if (tsuids.isEmpty()) {
                throw new NoPresentationContextException(cuid);
            }
            RetrieveService service = ctx.getRetrieveService();
            try (Transcoder transcoder = service.openTranscoder(ctx, inst, tsuids, false)) {
                String tsuid = transcoder.getDestinationTransferSyntax();
                DataWriter data = new TranscoderDataWriter(transcoder,
                        service.getAttributesCoercion(ctx, inst));
                DimseRSPHandler rspHandler = new CStoreRSPHandler(inst);
                storeas.cstore(cuid, iuid, ctx.getPriority(),
                            ctx.getMoveOriginatorAETitle(), ctx.getMoveOriginatorMessageID(),
                            data, tsuid, rspHandler);
            }
        } catch (Exception e) {
            ctx.incrementFailed();
            ctx.addFailedSOPInstanceUID(iuid);
            LOG.info("{}: failed to send {} to {}:", rqas, inst, ctx.getDestinationAETitle(), e);
        }
    }

    private InstanceLocations createInstanceLocations(StoreContext storeCtx) {
        Instance inst = storeCtx.getStoredInstance();
        Series series = inst.getSeries();
        Study study = series.getStudy();
        Patient patient = study.getPatient();
        Attributes instAttrs = inst.getAttributes();
        Attributes seriesAttrs = series.getAttributes();
        Attributes studyAttrs = study.getAttributes();
        Attributes patAttrs = patient.getAttributes();
        Attributes.unifyCharacterSets(patAttrs, studyAttrs, seriesAttrs, instAttrs);
        instAttrs.addAll(seriesAttrs);
        instAttrs.addAll(studyAttrs);
        instAttrs.addAll(patAttrs);
        RetrieveService service = ctx.getRetrieveService();
        InstanceLocations instanceLocations = service.newInstanceLocations(instAttrs);
        instanceLocations.getLocations().addAll(locations(storeCtx));
        return instanceLocations;
    }

    private Collection<Location> locations(StoreContext storeCtx) {
        Collection<Location> locations = storeCtx.getLocations();
        return locations.isEmpty() ? storeCtx.getStoredInstance().getLocations() : locations;
    }

    private static class WrappedStoreContext {
        final StoreContext storeContext;

        private WrappedStoreContext(StoreContext storeContext) {
            this.storeContext = storeContext;
        }
    }

    private final class CStoreRSPHandler extends DimseRSPHandler {

        private final InstanceLocations inst;

        public CStoreRSPHandler(InstanceLocations inst) {
            super(storeas.nextMessageID());
            this.inst = inst;
        }

        @Override
        public void onDimseRSP(Association as, Attributes cmd, Attributes data) {
            super.onDimseRSP(as, cmd, data);
            int storeStatus = cmd.getInt(Tag.Status, -1);
            if (storeStatus == Status.Success) {
                ctx.incrementCompleted();
            } else if ((storeStatus & 0xB000) == 0xB000) {
                ctx.incrementWarning();
            } else {
                ctx.incrementFailed();
                ctx.addFailedSOPInstanceUID(inst.getSopInstanceUID());
            }
        }
    }
}
