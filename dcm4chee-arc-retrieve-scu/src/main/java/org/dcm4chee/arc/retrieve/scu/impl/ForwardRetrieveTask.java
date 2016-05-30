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

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.*;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.RetrieveTask;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Dec 2015
 */
class ForwardRetrieveTask implements RetrieveTask {

    private static final Logger LOG = LoggerFactory.getLogger(ForwardRetrieveTask.class);

    private static final int MAX_FAILED_IUIDS_LEN = 4000;

    private final RetrieveContext ctx;
    private final PresentationContext pc;
    private final Attributes rqCmd;
    private final int msgId;
    private final String cuid;
    private final Attributes keys;
    private final Association rqas;
    private final Association fwdas;
    private final CMoveRSPHandler rspHandler;
    private final String fallbackCMoveSCPDestination;
    private Attributes finalMoveRSP;
    private Attributes finalMoveRSPData;

    public ForwardRetrieveTask(RetrieveContext ctx, PresentationContext pc,
                               Attributes rqCmd, Attributes keys,
                               String fallbackCMoveSCPDestination, Association fwdas) {
        this.ctx = ctx;
        this.rqas = ctx.getRequestAssociation();
        this.fwdas = fwdas;
        this.pc = pc;
        this.rqCmd = rqCmd;
        this.keys = keys;
        this.msgId = rqCmd.getInt(Tag.MessageID, 0);
        this.cuid = rqCmd.getString(Tag.AffectedSOPClassUID);
        this.fallbackCMoveSCPDestination = fallbackCMoveSCPDestination;
        this.rspHandler = new CMoveRSPHandler(msgId);
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
            waitForFinalMoveRSP();
        } catch (DicomServiceException e) {
            Attributes rsp = e.mkRSP(0x8021, msgId);
            finalMoveRSP = rsp;
            rqas.tryWriteDimseRSP(pc, rsp);
        } finally {
            releaseAssociation();
            rqas.removeCancelRQHandler(msgId);
        }
        if (fallbackCMoveSCPDestination != null)
            updateFailedSOPInstanceUIDList();
    }

    private void forwardMoveRQ() throws DicomServiceException{
        try {
            fwdas.invoke(fwdas.pcFor(cuid, null), rqCmd, new DataWriterAdapter(keys), rspHandler,
                    fwdas.getConnection().getRetrieveTimeout());
        } catch (Exception e) {
            LOG.info("{}: failed to forward C-MOVE-RQ on association to {}", rqas, fwdas.getRemoteAET(), e);
            throw new DicomServiceException(Status.ProcessingFailure, e);
        }
    }

    private void waitForFinalMoveRSP() throws DicomServiceException {
        try {
            synchronized (rspHandler) {
                while (finalMoveRSP == null)
                    rspHandler.wait();
            }
        } catch (InterruptedException e) {
            LOG.warn("{}: failed to wait for outstanding RSP on association to {}", rqas, fwdas.getRemoteAET(), e);
            throw new DicomServiceException(Status.ProcessingFailure, e);
        }
    }

    private void releaseAssociation() {
        try {
            fwdas.release();
        } catch (IOException e) {
            LOG.warn("{}: failed to release association to {}", rqas, fwdas.getRemoteAET(), e);
        }
    }

    private class CMoveRSPHandler extends DimseRSPHandler {
        public CMoveRSPHandler(int msgId) {
            super(msgId);
        }

        @Override
        public void onDimseRSP(Association as, Attributes cmd, Attributes data) {
            super.onDimseRSP(as, cmd, data);
            int failed = ctx.failed();
            if (!Status.isPending(cmd.getInt(Tag.Status, -1))) {
                synchronized (this) {
                    finalMoveRSP = cmd;
                    finalMoveRSPData = data;
                    notify();
                }
                if (fallbackCMoveSCPDestination != null) {
                    try {
                        LOG.info("{}: Wait for pending forward of C-STORE-RQs from {} to {}",
                                rqas, fwdas.getRemoteAET(), ctx.getDestinationAETitle());
                        ctx.waitForPendingCStoreForward();
                        LOG.info("{}: Complete forward of C-STORE-RQs from {} to {}",
                                rqas, fwdas.getRemoteAET(), ctx.getDestinationAETitle());
                    } catch (InterruptedException e) {
                        LOG.warn("{}: failed to wait for pending forward of C-STORE-RQs from {} to {}",
                                rqas, fwdas.getRemoteAET(), ctx.getDestinationAETitle(), e);
                    }
                    if (failed > 0) {
                        String[] failedIUIDs = ctx.failedSOPInstanceUIDs();
                        if (data != null)
                            failedIUIDs = cat(failedIUIDs, data.getStrings(Tag.FailedSOPInstanceUIDList));
                        data = new Attributes(1);
                        data.setString(Tag.FailedSOPInstanceUIDList, VR.UI, failedIUIDs);
                    }
                }
            }
            if (fallbackCMoveSCPDestination != null && failed > 0) {
                cmd = new Attributes(cmd);
                cmd.setInt(Tag.NumberOfCompletedSuboperations, VR.US,
                        cmd.getInt(Tag.NumberOfCompletedSuboperations, 0) - failed);
                cmd.setInt(Tag.NumberOfFailedSuboperations, VR.US,
                        cmd.getInt(Tag.NumberOfFailedSuboperations, 0) + failed);
            }
            try {
                rqas.writeDimseRSP(pc, cmd, data);
            } catch (IOException e) {
                LOG.warn("{}: Unable to backward C-MOVE RSP on association to {}", rqas, rqas.getRemoteAET(), e);
            }
        }
    }

    private static String[] cat(String[] ss1, String[] ss2) {
        if (ss2 == null || ss2.length == 0)
            return ss1;

        String[] ss = new String[ss1.length + ss2.length];
        System.arraycopy(ss1, 0, ss, 0, ss1.length);
        System.arraycopy(ss2, 0, ss, ss1.length, ss2.length);
        return ss;
    }

    private void updateFailedSOPInstanceUIDList() {
        int retrieved = finalMoveRSP.getInt(Tag.NumberOfCompletedSuboperations, 0) +
                finalMoveRSP.getInt(Tag.NumberOfWarningSuboperations, 0);
        if (retrieved > 0)
            ctx.getRetrieveService().updateFailedSOPInstanceUIDList(ctx, failedIUIDList(
                    ctx, finalMoveRSP.getInt(Tag.NumberOfFailedSuboperations, 0), retrieved, finalMoveRSPData));
    }

    private String failedIUIDList(RetrieveContext ctx, int failed, int retrieved, Attributes data) {
        if (failed == 0)
            return null;
        Association as = ctx.getRequestAssociation();
        String fallbackCMoveSCP = fwdas.getRemoteAET();
        LOG.warn("{}: Failed to retrieve {} from {} objects of study{} from {}",
                as, failed, failed + retrieved,
                Arrays.toString(ctx.getStudyInstanceUIDs()), fallbackCMoveSCP);
        String[] failedSOPInstanceUIDList = data != null
                ? data.getStrings(Tag.FailedSOPInstanceUIDList)
                : null;
        if (failedSOPInstanceUIDList == null || failedSOPInstanceUIDList.length == 0) {
            LOG.warn("{}: Missing Failed SOP Instance UID List in C-MOVE-RSP from {}", as, fallbackCMoveSCP);
            return "*";
        }
        if (failedSOPInstanceUIDList.length != failed) {
            LOG.warn("{}: Number Of Failed Suboperations [{}] does not match " +
                            "size of Failed SOP Instance UID List [{}] in C-MOVE-RSP from {}",
                    as, failed, failedSOPInstanceUIDList.length, fallbackCMoveSCP);
        }
        String concat = StringUtils.concat(failedSOPInstanceUIDList, '\\');
        if (concat.length() > MAX_FAILED_IUIDS_LEN) {
            LOG.warn("{}: Failed SOP Instance UID List [{}] in C-MOVE-RSP from {} too large to persist in DB",
                    as, failed, fallbackCMoveSCP);
            return "*";
        }
        return concat;
    }
}
