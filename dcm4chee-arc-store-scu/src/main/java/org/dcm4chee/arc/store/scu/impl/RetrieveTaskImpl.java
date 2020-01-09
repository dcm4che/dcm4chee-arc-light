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
import org.dcm4che3.data.AttributesCoercion;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.imageio.codec.Transcoder;
import org.dcm4che3.net.*;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.RetrieveTask;
import org.dcm4che3.util.SafeClose;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.Availability;
import org.dcm4chee.arc.conf.Duration;
import org.dcm4chee.arc.entity.Location;
import org.dcm4chee.arc.store.InstanceLocations;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.event.Event;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2015
 */
final class RetrieveTaskImpl implements RetrieveTask {

    static final Logger LOG = LoggerFactory.getLogger(RetrieveTaskImpl.class);
    private static InstanceLocations NO_MORE_MATCHES = new NoMoreMatches();

    private final Event<RetrieveContext> retrieveStart;
    private final Event<RetrieveContext> retrieveEnd;
    private final RetrieveContext ctx;
    private final Association[] storeass;
    private final ArchiveAEExtension aeExt;
    private final BlockingQueue<InstanceLocations> matches = new LinkedBlockingQueue<>();
    private final CountDownLatch doneSignal;
    private Dimse dimserq;
    private Association rqas;
    private PresentationContext pc;
    private Attributes rqCmd;
    private int msgId;
    private boolean pendingRSP;
    private Duration pendingRSPInterval;
    private volatile boolean canceled;

    RetrieveTaskImpl(RetrieveContext ctx, Event<RetrieveContext> retrieveStart, Event<RetrieveContext> retrieveEnd,
            Association... storeass) {
        this.retrieveStart = retrieveStart;
        this.retrieveEnd = retrieveEnd;
        this.ctx = ctx;
        this.storeass = storeass;
        this.aeExt = ctx.getArchiveAEExtension();
        this.doneSignal = new CountDownLatch(storeass.length);
    }

    void setRequestAssociation(Dimse dimserq, Association rqas, PresentationContext pc, Attributes rqCmd) {
        this.dimserq = dimserq;
        this.rqas = rqas;
        this.pc = pc;
        this.rqCmd = rqCmd;
        this.msgId = rqCmd.getInt(Tag.MessageID, 0);
        this.pendingRSP = dimserq == Dimse.C_GET_RQ && aeExt.sendPendingCGet();
        this.pendingRSPInterval = dimserq == Dimse.C_MOVE_RQ ? aeExt.sendPendingCMoveInterval() : null;
        rqas.addCancelRQHandler(msgId, this);
    }

    @Override
    public void onCancelRQ(Association association) {
        canceled = true;
    }

    @Override
    public void run() {
        retrieveStart.fire(ctx);
        try {
            if (ctx.getFallbackAssociation() == null) startWritePendingRSP();
            if (storeass.length > 1) startStoreOperations();
            for (InstanceLocations match : ctx.getMatches()) {
                if (!ctx.copyToRetrieveCache(match)) {
                    matches.offer(match);
                }
            }
            ctx.copyToRetrieveCache(null);
            if (storeass.length == 1) {
                matches.offer(NO_MORE_MATCHES);
                runStoreOperations(storeass[0]);
            } else {
                InstanceLocations match;
                while ((match = ctx.copiedToRetrieveCache()) != null && !canceled) {
                    matches.offer(match);
                }
                for (int i = 0; i < storeass.length; i++) {
                    matches.offer(NO_MORE_MATCHES);
                }
            }
            waitForPendingStoreOperations();
        } finally {
            waitForPendingCMoveForward();
            waitForPendingCStoreForward();
            updateCompleteness();
            ctx.getRetrieveService().updateLocations(ctx);
            ctx.stopWritePendingRSP();
            if (rqas != null) {
                writeFinalRSP();
                rqas.removeCancelRQHandler(msgId);
            }
            SafeClose.close(ctx);
        }
        retrieveEnd.fire(ctx);
    }

    private void startStoreOperations() {
        Device device = ctx.getArchiveAEExtension().getApplicationEntity().getDevice();
        for (Association storeas : storeass) {
            device.execute(() -> runStoreOperations(storeas));
        }
    }

    private void waitForPendingStoreOperations() {
        try {
            doneSignal.await();
        } catch (InterruptedException e) {
            LOG.warn("{}: failed to wait for outstanding store operations", rqas, e);
        }
    }

    private void runStoreOperations(Association storeas) {
        Collection<InstanceLocations> outstandingRSPs = Collections.synchronizedList(new ArrayList<>());
        try {
            InstanceLocations match = null;
            while (!canceled && (match = matches.take()) != NO_MORE_MATCHES) {
                store(match, storeas, outstandingRSPs);
                waitForNonBlockingInvoke(storeas);
            }
            while (!canceled && (match = ctx.copiedToRetrieveCache()) != null) {
                store(match, storeas, outstandingRSPs);
            }
        } catch (InterruptedException e) {
            LOG.warn("{}: failed to fetch next match from queue:\n",
                    rqas, storeas.getRemoteAET(), e);
        } finally {
            waitForOutstandingCStoreRSP(storeas, outstandingRSPs);
            releaseStoreAssociation(storeas);
            doneSignal.countDown();
        }
    }

    private void waitForNonBlockingInvoke(Association storeas) {
        try {
            storeas.waitForNonBlockingInvoke();
        } catch (InterruptedException e) {
            LOG.warn("{}: failed to wait for outstanding C-STORE RSP(s) on association to {}:\n",
                    rqas != null ? rqas : storeas, storeas.getRemoteAET(), e);
        }
    }

    private void store(InstanceLocations inst, Association storeas, Collection<InstanceLocations> outstandingRSP) {
        CStoreRSPHandler rspHandler = new CStoreRSPHandler(inst, storeas, outstandingRSP);
        String iuid = inst.getSopInstanceUID();
        String cuid = inst.getSopClassUID();
        int priority = ctx.getPriority();
        Set<String> tsuids = storeas.getTransferSyntaxesFor(cuid);
        try {
            RetrieveService service = ctx.getRetrieveService();
            try (Transcoder transcoder = service.openTranscoder(ctx, inst, tsuids, false)) {
                String tsuid = transcoder.getDestinationTransferSyntax();
                AttributesCoercion coerce = service.getAttributesCoercion(ctx, inst);
                if (coerce != null)
                    iuid = coerce.remapUID(iuid);

                TranscoderDataWriter data = new TranscoderDataWriter(transcoder, coerce);
                outstandingRSP.add(inst);
                long startTime = System.nanoTime();
                if (ctx.getMoveOriginatorAETitle() != null) {
                    storeas.cstore(cuid, iuid, priority,
                            ctx.getMoveOriginatorAETitle(), ctx.getMoveOriginatorMessageID(),
                            data, tsuid, rspHandler);
                } else {
                    storeas.cstore(cuid, iuid, priority,
                            data, tsuid, rspHandler);
                }
                service.getMetricsService().acceptDataRate("send-to-" + storeas.getRemoteAET(),
                        data.getCount(), startTime);
            }
        } catch (Exception e) {
            outstandingRSP.remove(inst);
            ctx.incrementFailed();
            ctx.addFailedSOPInstanceUID(iuid);
            LOG.warn("{}: failed to send {} to {}:", rqas != null ? rqas : storeas, inst, ctx.getDestinationAETitle(), e);
        }
    }

    private void writeFinalRSP() {
        int remaining = ctx.remaining();
        if (!canceled) {
            ctx.addFailed(remaining);
            remaining = 0;
        }
        writeRSP(remaining > 0 ? Status.Cancel : ctx.status(), remaining, finalRSPDataset());
    }

    private Attributes finalRSPDataset() {
        if (ctx.failed() == 0)
            return null;

        String[] failedIUIDs = cat(ctx.failedSOPInstanceUIDs(), ctx.getFallbackMoveRSPFailedIUIDs());
        Attributes attrs = new Attributes(1);
        attrs.setString(Tag.FailedSOPInstanceUIDList, VR.UI, failedIUIDs);
        return attrs;
    }

    private static String[] cat(String[] ss1, String[] ss2) {
        if (ss1.length == 0)
            return ss2;

        if (ss2.length == 0)
            return ss1;

        String[] ss = new String[ss1.length + ss2.length];
        System.arraycopy(ss1, 0, ss, 0, ss1.length);
        System.arraycopy(ss2, 0, ss, ss1.length, ss2.length);
        return ss;
    }

    private void writePendingRSP() {
        if (canceled)
            return;

        int remaining = ctx.remaining();
        if (remaining > 0)
            writeRSP(Status.Pending, remaining, null);
    }

    private void writeRSP(int status, int remaining, Attributes data) {
        Attributes cmd = Commands.mkRSP(rqCmd, status, dimserq);
        if (remaining > 0)
            cmd.setInt(Tag.NumberOfRemainingSuboperations, VR.US, remaining);
        cmd.setInt(Tag.NumberOfCompletedSuboperations, VR.US, ctx.completed());
        cmd.setInt(Tag.NumberOfFailedSuboperations, VR.US, ctx.failed());
        cmd.setInt(Tag.NumberOfWarningSuboperations, VR.US, ctx.warning());
        try {
            rqas.writeDimseRSP(pc, cmd, data);
        } catch (IOException e) {
            LOG.warn("{}: Unable to send C-GET or C-MOVE RSP on association to {}",
                    rqas, rqas.getRemoteAET(), e);
        }
    }

    private void startWritePendingRSP() {
        if (pendingRSP)
            writePendingRSP();
        if (pendingRSPInterval != null)
            ctx.setWritePendingRSP(rqas.getApplicationEntity().getDevice()
                    .scheduleAtFixedRate(
                            new Runnable() {
                                @Override
                                public void run() {
                                    writePendingRSP();
                                }
                            },
                            0, pendingRSPInterval.getSeconds(), TimeUnit.SECONDS));
    }

    private void waitForOutstandingCStoreRSP(Association storeas, Collection<InstanceLocations> outstandingRSP) {
        if (storeas.isReadyForDataTransfer() && !outstandingRSP.isEmpty())
            try {
                LOG.debug("{}: wait for {} outstanding C-STORE RSP(s) on association to {}",
                        rqas != null ? rqas : storeas, outstandingRSP.size(), storeas.getRemoteAET());
                synchronized (outstandingRSP) {
                    while (storeas.isReadyForDataTransfer() && !outstandingRSP.isEmpty())
                        outstandingRSP.wait();
                }
                LOG.debug("{}: received outstanding C-STORE RSP(s) on association to {}",
                        rqas != null ? rqas : storeas, storeas.getRemoteAET());
            } catch (InterruptedException e) {
                LOG.warn("{}: failed to wait for outstanding C-STORE RSP(s) on association to {}",
                        rqas != null ? rqas : storeas, storeas.getRemoteAET(), e);
            }
    }

    private void waitForPendingCMoveForward() {
        ctx.getRetrieveService().waitForPendingCMoveForward(ctx);
    }

    private void waitForPendingCStoreForward() {
        if (ctx.getFallbackAssociation() != null)
            ctx.getRetrieveService().waitForPendingCStoreForward(ctx);
    }

    private void updateCompleteness() {
        if (ctx.getFallbackAssociation() != null)
            ctx.getRetrieveService().updateCompleteness(ctx);
    }

    private void removeOutstandingRSP(InstanceLocations inst,
            Association storeas, Collection<InstanceLocations> outstandingRSP) {
        outstandingRSP.remove(inst);
        synchronized (outstandingRSP) {
            outstandingRSP.notifyAll();
        }
    }

    protected void releaseStoreAssociation(Association storeas) {
        if (dimserq != Dimse.C_GET_RQ && storeas.isReadyForDataTransfer())
            try {
                storeas.release();
            } catch (IOException e) {
                LOG.warn("{}: failed to release association to {}",
                        rqas != null ? rqas : storeas, storeas.getRemoteAET(), e);
            }
    }
    private final class CStoreRSPHandler extends DimseRSPHandler {

        private final InstanceLocations inst;
        private final Association storeas;
        private final Collection<InstanceLocations> outstandingRSP;

        public CStoreRSPHandler(InstanceLocations inst, Association storeas,
                Collection<InstanceLocations> outstandingRSP) {
            super(storeas.nextMessageID());
            this.inst = inst;
            this.storeas = storeas;
            this.outstandingRSP = outstandingRSP;
        }

        @Override
        public void onDimseRSP(Association as, Attributes cmd, Attributes data) {
            super.onDimseRSP(as, cmd, data);
            int storeStatus = cmd.getInt(Tag.Status, -1);
            if (storeStatus == Status.Success)
                ctx.incrementCompleted();
            else if ((storeStatus & 0xB000) == 0xB000)
                ctx.incrementWarning();
            else {
                ctx.incrementFailed();
                ctx.addFailedSOPInstanceUID(inst.getSopInstanceUID());
            }
            if (pendingRSP)
                writePendingRSP();
            removeOutstandingRSP(inst, storeas, outstandingRSP);
        }

        @Override
        public void onClose(Association as) {
            super.onClose(as);
            removeOutstandingRSP(inst, storeas, outstandingRSP);
        }
    }

    private static final class NoMoreMatches implements InstanceLocations {
        @Override
        public Long getInstancePk() {
            return null;
        }

        @Override
        public void setInstancePk(Long pk) {
        }

        @Override
        public String getSopInstanceUID() {
            return null;
        }

        @Override
        public String getSopClassUID() {
            return null;
        }

        @Override
        public List<Location> getLocations() {
            return null;
        }

        @Override
        public Attributes getAttributes() {
            return null;
        }

        @Override
        public String getRetrieveAETs() {
            return null;
        }

        @Override
        public String getExternalRetrieveAET() {
            return null;
        }

        @Override
        public Availability getAvailability() {
            return null;
        }

        @Override
        public Date getCreatedTime() {
            return null;
        }

        @Override
        public Date getUpdatedTime() {
            return null;
        }

        @Override
        public Attributes getRejectionCode() {
            return null;
        }

        @Override
        public boolean isContainsMetadata() {
            return false;
        }

        @Override
        public boolean isImage() {
            return false;
        }

        @Override
        public boolean isVideo() {
            return false;
        }

        @Override
        public boolean isMultiframe() {
            return false;
        }
    }

}

