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

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.*;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.RetrieveTask;
import org.dcm4che3.util.SafeClose;
import org.dcm4chee.arc.conf.Duration;
import org.dcm4chee.arc.retrieve.ExternalRetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.event.Event;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Dec 2015
 */
abstract class ForwardRetrieveTask implements RetrieveTask {

    private static final Logger LOG = LoggerFactory.getLogger(ForwardRetrieveTask.class);

    protected final RetrieveContext ctx;
    private final PresentationContext pc;
    protected final Attributes rqCmd;
    private final int msgId;
    private final String cuid;
    protected final Attributes keys;
    private final Association rqas;
    private final Association fwdas;
    private final CMoveRSPHandler rspHandler;
    private final Event<ExternalRetrieveContext> instancesRetrievedEvent;

    public ForwardRetrieveTask(RetrieveContext ctx, PresentationContext pc, Attributes rqCmd, Attributes keys,
                               Association fwdas, Event<ExternalRetrieveContext> instancesRetrievedEvent) {
        this.ctx = ctx;
        this.rqas = ctx.getRequestAssociation();
        this.fwdas = fwdas;
        this.pc = pc;
        this.rqCmd = rqCmd;
        this.keys = keys;
        this.msgId = rqCmd.getInt(Tag.MessageID, 0);
        this.cuid = rqCmd.getString(Tag.AffectedSOPClassUID);
        this.rspHandler = new CMoveRSPHandler(msgId);
        this.instancesRetrievedEvent = instancesRetrievedEvent;
    }

    @Override
    public void onCancelRQ(Association rqas) {
        try {
            rspHandler.cancel(fwdas);
        } catch (IOException e) {
            LOG.info("{}: failed to forward C-CANCEL-RQ on association to {}", rqas, fwdas.getRemoteAET(), e);
        }
    }

    @Override
    public void run() {
        rqas.addCancelRQHandler(msgId, this);
        try {
            forwardMoveRQ();
        } catch (DicomServiceException e) {
            onFailure(e);
        } finally {
            ctx.getRetrieveService().waitForPendingCMoveForward(ctx);
            rqas.removeCancelRQHandler(msgId);
            onFinished();
        }
    }

    protected void onFailure(DicomServiceException e) {};

    protected void onFinished() {}

    public void forwardMoveRQ() throws DicomServiceException {
        try {
            fwdas.invoke(fwdas.pcFor(cuid, null), rqCmd, new DataWriterAdapter(keys), rspHandler,
                    fwdas.getConnection().getSendTimeout(), fwdas.getConnection().getRetrieveTimeout());
        } catch (Exception e) {
            LOG.info("{}: failed to forward C-MOVE-RQ on association to {}", rqas, fwdas.getRemoteAET(), e);
            throw new DicomServiceException(Status.ProcessingFailure, e);
        }
    }

    protected void writeMoveRSP(Attributes cmd, Attributes data) {
        try {
            if (rqas.isReadyForDataTransfer())
                rqas.writeDimseRSP(pc, cmd, data);
        } catch (IOException e) {
            LOG.warn("{}: Unable to return C-MOVE RSP on association to {}", rqas, rqas.getRemoteAET(), e);
        }
    }

    protected Attributes mkMoveRSP(DicomServiceException e) {
        return e.mkRSP(0x8021, msgId);
    }

    protected void writeMoveRSP(int status, int remaining, int completed, int failed, int warning, Attributes data) {
        Attributes cmd = Commands.mkCMoveRSP(rqCmd, status);
        if (remaining > 0)
            cmd.setInt(Tag.NumberOfRemainingSuboperations, VR.US, remaining);
        cmd.setInt(Tag.NumberOfCompletedSuboperations, VR.US, completed);
        cmd.setInt(Tag.NumberOfFailedSuboperations, VR.US, failed);
        cmd.setInt(Tag.NumberOfWarningSuboperations, VR.US, warning);
        writeMoveRSP(cmd, data);
    }

    private class CMoveRSPHandler extends DimseRSPHandler {
        public CMoveRSPHandler(int msgId) {
            super(msgId);
        }

        @Override
        public void onDimseRSP(Association as, Attributes cmd, Attributes data) {
            super.onDimseRSP(as, cmd, data);
            onCMoveRSP(as, cmd, data);
        }
    }

    protected void onCMoveRSP(Association as, Attributes cmd, Attributes data) {
        if (!Status.isPending(cmd.getInt(Tag.Status, -1))) {
            Device device = ctx.getLocalApplicationEntity().getDevice();
            ExternalRetrieveContext event = new ExternalRetrieveContext()
                    .setDeviceName(device.getDeviceName())
                    .setLocalAET(as.getLocalAET())
                    .setRemoteAET(as.getRemoteAET())
                    .setDestinationAET(rqCmd.getString(Tag.MoveDestination))
                    .setKeys(keys)
                    .setRemoteHostName(as.getRemoteHostName())
                    .setResponse(cmd);
            instancesRetrievedEvent.fire(event);
        }
    }

    static class BackwardCMoveRSP extends ForwardRetrieveTask {
        public BackwardCMoveRSP(RetrieveContext ctx, PresentationContext pc, Attributes rqCmd, Attributes keys,
                                Association fwdas, Event<ExternalRetrieveContext> instancesRetrievedEvent) {
            super(ctx, pc, rqCmd, keys, fwdas, instancesRetrievedEvent);
        }

        @Override
        protected void onFailure(DicomServiceException e) {
            writeMoveRSP(mkMoveRSP(e), null);
        }

        @Override
        protected void onCMoveRSP(Association as, Attributes cmd, Attributes data) {
            super.onCMoveRSP(as, cmd, data);
            writeMoveRSP(cmd, data);
        }
    }

    static class UpdateRetrieveCtx extends ForwardRetrieveTask {
        private int completed;
        private int failed;
        private int warnings;

        public UpdateRetrieveCtx(RetrieveContext ctx, PresentationContext pc, Attributes rqCmd, Attributes keys,
                                 Association fwdas, Event<ExternalRetrieveContext> instancesRetrievedEvent) {
            super(ctx, pc, rqCmd, keys, fwdas, instancesRetrievedEvent);
        }

        @Override
        protected void onCMoveRSP(Association as, Attributes cmd, Attributes data) {
            super.onCMoveRSP(as, cmd, data);
            int completed0 = cmd.getInt(Tag.NumberOfCompletedSuboperations, 0);
            int failed0 = cmd.getInt(Tag.NumberOfFailedSuboperations, 0);
            int warnigs0 = cmd.getInt(Tag.NumberOfWarningSuboperations, 0);
            ctx.addCompleted(completed0 - completed);
            ctx.addWarning(warnigs0 - warnings);
            ctx.addFailed(failed0 - failed);
            completed = completed0;
            failed = failed0;
            warnings = warnigs0;
            if (data != null) {
                String[] iuids = data.getStrings(Tag.FailedSOPInstanceUIDList);
                if (iuids != null)
                    for (String iuid : iuids)
                        ctx.addFailedSOPInstanceUID(iuid);
            }
        }
    }

    static class ForwardCStoreRQ extends ForwardRetrieveTask {
        private final Duration pendingRSPInterval;
        private final Event<RetrieveContext> retrieveStart;
        private final Event<RetrieveContext> retrieveEnd;
        private int rspCount;

        public ForwardCStoreRQ(RetrieveContext ctx, PresentationContext pc, Attributes rqCmd, Attributes keys,
                Association fwdas, Event<ExternalRetrieveContext> instancesRetrievedEvent,
                Event<RetrieveContext> retrieveStart, Event<RetrieveContext> retrieveEnd) {
            super(ctx, pc, rqCmd, keys, fwdas, instancesRetrievedEvent);
            this.pendingRSPInterval = ctx.getArchiveAEExtension().sendPendingCMoveInterval();
            this.retrieveStart = retrieveStart;
            this.retrieveEnd = retrieveEnd;
        }

        @Override
        public void run() {
            retrieveStart.fire(ctx);
            super.run();
        }

        @Override
        public void onCancelRQ(Association rqas) {
            //TODO
        }

        @Override
        protected void onFailure(DicomServiceException e) {
 //           ctx.setFallbackMoveRSP(mkMoveRSP(e));
        }

        @Override
        protected void onCMoveRSP(Association as, Attributes cmd, Attributes data) {
            super.onCMoveRSP(as, cmd, data);
            ctx.setFallbackMoveRSP(cmd, data);
            if (rspCount++ == 0 && Status.isPending(cmd.getInt(Tag.Status, -1))) {
                startWritePendingRSP();
            }
        }

        @Override
        protected void onFinished() {
            ctx.getRetrieveService().waitForPendingCStoreForward(ctx);
            ctx.getRetrieveService().updateCompleteness(ctx);
            ctx.stopWritePendingRSP();
            writeFinalRSP();
            SafeClose.close(ctx);
            retrieveEnd.fire(ctx);
        }

        private void startWritePendingRSP() {
            if (pendingRSPInterval != null)
                ctx.setWritePendingRSP(ctx.getLocalApplicationEntity().getDevice()
                        .scheduleAtFixedRate(
                                () -> writePendingRSP(),
                                0, pendingRSPInterval.getSeconds(), TimeUnit.SECONDS));
        }

        private void writeFinalRSP() {
            Attributes fallbackMoveRSPCommand = ctx.getFallbackMoveRSPCommand();
            if (ctx.getNumberOfMatches() == 0 // no local matches
                    && fallbackMoveRSPCommand != null // nothing received from fallbackMoveRSP
                    && fallbackMoveRSPCommand.getInt(Tag.NumberOfCompletedSuboperations, 0) == 0
                    && fallbackMoveRSPCommand.getInt(Tag.NumberOfWarningSuboperations, 0) == 0) {
                writeMoveRSP(fallbackMoveRSPCommand, ctx.getFallbackMoveRSPData());
            } else {
                int status = fallbackMoveRSPCommand != null
                        ? fallbackMoveRSPCommand.getInt(Tag.Status, 0)
                        : Status.UnableToPerformSubOperations;
                int failed = ctx.failed();
                int warning = ctx.warning();
                int completed = ctx.completed();
                if (failed > 0) {
                    status = completed == 0 && warning == 0
                            ? Status.UnableToPerformSubOperations
                            : Status.OneOrMoreFailures;
                }
                Attributes cmd = Commands.mkCMoveRSP(rqCmd, status);
                if (completed > 0)
                    cmd.setInt(Tag.NumberOfCompletedSuboperations, VR.US, completed);
                if (failed > 0)
                    cmd.setInt(Tag.NumberOfFailedSuboperations, VR.US, failed);
                if (warning > 0)
                    cmd.setInt(Tag.NumberOfWarningSuboperations, VR.US, warning);
                writeMoveRSP(cmd, finalRSPDataset());
            }
        }

        private Attributes finalRSPDataset() {
            String[] failedIUIDs;
            if (ctx.failed() == 0 || (failedIUIDs = ctx.failedSOPInstanceUIDs()).length == 0)
                return null;

            Attributes data = new Attributes(1);
            data.setString(Tag.FailedSOPInstanceUIDList, VR.UI, failedIUIDs);
            return data;
        }

        private void writePendingRSP() {
            int remaining = ctx.remaining();
            if (remaining > 0) {
                Attributes cmd = Commands.mkCMoveRSP(rqCmd, Status.Pending);
                cmd.setInt(Tag.NumberOfRemainingSuboperations, VR.US, remaining);
                cmd.setInt(Tag.NumberOfCompletedSuboperations, VR.US, ctx.completed());
                cmd.setInt(Tag.NumberOfFailedSuboperations, VR.US, ctx.failed());
                cmd.setInt(Tag.NumberOfWarningSuboperations, VR.US, ctx.warning());
                writeMoveRSP(cmd, null);
            }
        }
    }
}
