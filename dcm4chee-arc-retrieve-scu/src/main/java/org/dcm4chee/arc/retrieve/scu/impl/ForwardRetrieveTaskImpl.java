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
import org.dcm4che3.net.*;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.RetrieveTask;
import org.dcm4chee.arc.retrieve.scu.ForwardRetrieveTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Dec 2015
 */
public class ForwardRetrieveTaskImpl implements ForwardRetrieveTask {

    private static final Logger LOG = LoggerFactory.getLogger(ForwardRetrieveTaskImpl.class);

    private final PresentationContext pc;
    private final Attributes rqCmd;
    private final int msgId;
    private final String cuid;
    private final Attributes keys;
    private final Association rqas;
    private final Association fwdas;
    private final CMoveRSPHandler rspHandler;
    private final boolean bwdRSPs;
    private final boolean fwdCancel;
    private Attributes finalMoveRSP;
    private Attributes finalMoveRSPData;

    public ForwardRetrieveTaskImpl(Association rqas, PresentationContext pc, Attributes rqCmd, Attributes keys,
                                   Association fwdas, boolean bwdRSPs, boolean fwdCancel) {
        this.rqas = rqas;
        this.fwdas = fwdas;
        this.pc = pc;
        this.rqCmd = rqCmd;
        this.keys = keys;
        this.msgId = rqCmd.getInt(Tag.MessageID, 0);
        this.cuid = rqCmd.getString(Tag.AffectedSOPClassUID);
        this.rspHandler = new CMoveRSPHandler(msgId);
        this.bwdRSPs = bwdRSPs;
        this.fwdCancel = fwdCancel;
    }

    @Override
    public Attributes getFinalMoveRSP() {
        return finalMoveRSP;
    }

    @Override
    public Attributes getFinalMoveRSPData() {
        return finalMoveRSPData;
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
        if (fwdCancel)
            rqas.addCancelRQHandler(msgId, this);
        try {
            forwardMoveRQ();
            waitForFinalMoveRSP();
        } catch (DicomServiceException e) {
            Attributes rsp = e.mkRSP(0x8021, msgId);
            finalMoveRSP = rsp;
            if (bwdRSPs)
                rqas.tryWriteDimseRSP(pc, rsp);
        } finally {
            releaseAssociation();
            if (fwdCancel)
                rqas.removeCancelRQHandler(msgId);
        }
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
            if (bwdRSPs)
                try {
                    rqas.writeDimseRSP(pc, cmd, data);
                } catch (IOException e) {
                    LOG.warn("{}: Unable to backward C-MOVE RSP on association to {}", rqas, rqas.getRemoteAET(), e);
                }

            if (!Status.isPending(cmd.getInt(Tag.Status, -1))) {
                synchronized (this) {
                    finalMoveRSP = cmd;
                    finalMoveRSPData = data;
                    notify();
                }
            }
        }
    }
}
