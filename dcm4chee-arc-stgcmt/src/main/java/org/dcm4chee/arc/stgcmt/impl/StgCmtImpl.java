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
 * Portions created by the Initial Developer are Copyright (C) 2016
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

package org.dcm4chee.arc.stgcmt.impl;

import org.dcm4che3.conf.api.ConfigurationNotFoundException;
import org.dcm4che3.conf.api.IApplicationEntityCache;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.*;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.pdu.RoleSelection;
import org.dcm4che3.net.service.AbstractDicomService;
import org.dcm4che3.net.service.DicomService;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.TagUtils;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.entity.StgCmtResult;
import org.dcm4chee.arc.exporter.ExportContext;
import org.dcm4chee.arc.qmgt.Outcome;
import org.dcm4chee.arc.qmgt.QueueManager;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.stgcmt.StgCmtManager;
import org.dcm4chee.arc.stgcmt.StgCmtSCP;
import org.dcm4chee.arc.stgcmt.StgCmtSCU;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Typed;
import javax.inject.Inject;
import javax.jms.JMSException;
import javax.jms.ObjectMessage;
import java.io.IOException;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Sep 2015
 */
@ApplicationScoped
@Typed({ DicomService.class, StgCmtSCP.class, StgCmtSCU.class })
class StgCmtImpl extends AbstractDicomService implements StgCmtSCP, StgCmtSCU {

    private static final Logger LOG = LoggerFactory.getLogger(StgCmtImpl.class);

    @Inject
    private QueueManager queueManager;

    @Inject
    private Device device;

    @Inject
    private IApplicationEntityCache aeCache;

    @Inject
    private QueryService queryService;

    @Inject
    private StgCmtManager ejb;

    public StgCmtImpl() {
        super(UID.StorageCommitmentPushModelSOPClass);
    }

    @Override
    public void onDimseRQ(Association as, PresentationContext pc, Dimse dimse, Attributes rq, Attributes data)
            throws IOException {
        switch (dimse) {
            case N_ACTION_RQ:
                onNActionRQ(as, pc, rq, data);
                return;
            case N_EVENT_REPORT_RQ:
                onNEventReportRQ(as, pc, rq, data);
                return;
        }
        throw new DicomServiceException(Status.UnrecognizedOperation);
    }

    public void onExport(@Observes ExportContext ctx) {
        ExporterDescriptor descriptor = ctx.getExporter().getExporterDescriptor();
        String stgCmtSCPAETitle = descriptor.getStgCmtSCPAETitle();
        if (stgCmtSCPAETitle != null)
            if (ctx.isOnlyStgCmt()
                    || !ctx.isOnlyIAN() && ctx.getOutcome().getStatus() == QueueMessage.Status.COMPLETED)
                triggerStorageCommit(ctx, descriptor, stgCmtSCPAETitle);
        return;
    }

    private void triggerStorageCommit(ExportContext ctx, ExporterDescriptor descriptor, String stgCmtSCPAETitle) {
        Attributes actionInfo = createActionInfo(ctx);
        scheduleNAction(ctx.getAETitle(), stgCmtSCPAETitle, actionInfo, ctx, descriptor.getExporterID());
    }

    private Attributes createActionInfo(ExportContext ctx) {
        try {
            ApplicationEntity ae = aeCache.findApplicationEntity(ctx.getAETitle());
            return queryService.createActionInfo(
                    ctx.getStudyInstanceUID(), ctx.getSeriesInstanceUID(), ctx.getSopInstanceUID(), ae);
        } catch (Exception e) {
            return null;
        }
    }

    private void onNActionRQ(Association as, PresentationContext pc, Attributes rq, Attributes actionInfo)
            throws DicomServiceException {
        int actionTypeID = rq.getInt(Tag.ActionTypeID, 0);
        if (actionTypeID != 1)
            throw new DicomServiceException(Status.NoSuchActionType)
                    .setActionTypeID(actionTypeID);

        String localAET = as.getLocalAET();
        String remoteAET = as.getRemoteAET();
        try {
            as.getApplicationEntity().findCompatibelConnection(aeCache.findApplicationEntity(remoteAET));
            scheduleNEventReport(localAET, remoteAET, actionInfo);
        } catch (ConfigurationNotFoundException e) {
            throw new DicomServiceException(Status.ProcessingFailure, "Unknown Calling AET: " + remoteAET);
        } catch (Exception e) {
            throw new DicomServiceException(Status.ProcessingFailure, e.getMessage());
        }
        try {
            as.writeDimseRSP(pc, Commands.mkNActionRSP(rq, Status.Success), null);
        } catch (Exception e) {
            LOG.warn("{} << N-ACTION-RSP failed: {}", as, e.getMessage());
        }
    }

    private void onNEventReportRQ(Association as, PresentationContext pc, Attributes rq, Attributes eventInfo) {
        ejb.addExternalRetrieveAETs(eventInfo, device);
        try {
            as.writeDimseRSP(pc, Commands.mkNEventReportRSP(rq, Status.Success), null);
        } catch (Exception e) {
            LOG.warn("{} << N-EVENT-REPORT-RSP failed: {}", as, e.getMessage());
        }
    }

    private void scheduleNAction(String localAET, String remoteAET, Attributes actionInfo,
                                 ExportContext ctx, String exporterID) {
        try {
            ObjectMessage msg = queueManager.createObjectMessage(actionInfo);
            msg.setStringProperty("LocalAET", localAET);
            msg.setStringProperty("RemoteAET", remoteAET);
            msg.setStringProperty("StudyInstanceUID", ctx.getStudyInstanceUID());
            msg.setStringProperty("SeriesInstanceUID", ctx.getSeriesInstanceUID());
            msg.setStringProperty("SopInstanceUID", ctx.getSopInstanceUID());
            msg.setStringProperty("ExporterID", exporterID);
            queueManager.scheduleMessage(StgCmtSCU.QUEUE_NAME, msg);
        } catch (JMSException e) {
            throw QueueMessage.toJMSRuntimeException(e);
        }
    }

    private void scheduleNEventReport(String localAET, String remoteAET, Attributes eventInfo) {
        try {
            ObjectMessage msg = queueManager.createObjectMessage(eventInfo);
            msg.setStringProperty("LocalAET", localAET);
            msg.setStringProperty("RemoteAET", remoteAET);
            queueManager.scheduleMessage(StgCmtSCP.QUEUE_NAME, msg);
        } catch (JMSException e) {
            throw QueueMessage.toJMSRuntimeException(e);
        }
    }

    @Override
    public Outcome sendNAction(String localAET, String remoteAET, String studyInstanceUID, String seriesInstanceUID,
                               String sopInstanceUID, String exporterID, Attributes actionInfo) throws Exception  {
        ApplicationEntity localAE = device.getApplicationEntity(localAET, true);
        ApplicationEntity remoteAE = aeCache.findApplicationEntity(remoteAET);
        AAssociateRQ aarq = mkAAssociateRQ(localAE, localAET, TransferCapability.Role.SCU);
        Association as = localAE.connect(remoteAE, aarq);
        try {
            StgCmtResult result = new StgCmtResult();
            result.setStgCmtRequest(actionInfo);
            result.setStudyInstanceUID(studyInstanceUID);
            result.setSeriesInstanceUID(seriesInstanceUID);
            result.setSopInstanceUID(sopInstanceUID);
            result.setExporterID(exporterID);
            result.setDeviceName(device.getDeviceName());
            ejb.persistStgCmtResult(result);
            DimseRSP dimseRSP = as.naction(
                    UID.StorageCommitmentPushModelSOPClass,
                    UID.StorageCommitmentPushModelSOPInstance,
                    1, actionInfo, null);
            dimseRSP.next();
            Attributes cmd = dimseRSP.getCommand();
            int status = cmd.getInt(Tag.Status, -1);
            if (status != Status.Success) {
                ejb.deleteStgCmt(actionInfo.getString(Tag.TransactionUID));
                return new Outcome(QueueMessage.Status.WARNING,
                        "Request Storage Commitment Request from AE: " + remoteAET
                                + " failed with status: " + TagUtils.shortToHexString(status)
                                + "H, error comment: " + cmd.getString(Tag.ErrorComment));
            }
            return new Outcome(QueueMessage.Status.COMPLETED, "Request Storage Commitment Request from AE: " + remoteAET);
        } catch (Exception e) {
            ejb.deleteStgCmt(actionInfo.getString(Tag.TransactionUID));
            throw e;
        }
        finally {
            try {
                as.release();
            } catch (IOException e) {
                LOG.info("{}: Failed to release association to {}", as, remoteAET);
            }
        }
    }

    @Override
    public Outcome sendNEventReport(String localAET, String remoteAET, Attributes eventInfo)
            throws Exception  {
            ApplicationEntity localAE = device.getApplicationEntity(localAET, true);
            ApplicationEntity remoteAE = aeCache.findApplicationEntity(remoteAET);
            AAssociateRQ aarq = mkAAssociateRQ(localAE, localAET, TransferCapability.Role.SCP);
            Association as = localAE.connect(remoteAE, aarq);
            try {
                int successful = sequenceSizeOf(eventInfo, Tag.ReferencedSOPSequence);
                int failed = sequenceSizeOf(eventInfo, Tag.FailedSOPSequence);
                DimseRSP neventReport = as.neventReport(
                        UID.StorageCommitmentPushModelSOPClass,
                        UID.StorageCommitmentPushModelSOPInstance,
                        failed > 0 ? 2 : 1, eventInfo, null);
                neventReport.next();
                return new Outcome(failed > 0 ? QueueMessage.Status.WARNING : QueueMessage.Status.COMPLETED,
                        "Return Storage Commitment Result[successful: " + successful + ", failed: " + failed
                                + "] to AE: " + remoteAET);
            } finally {
                try {
                    as.release();
                } catch (IOException e) {
                    LOG.info("{}: Failed to release association to {}", as, remoteAET);
                }
            }
    }

    private int sequenceSizeOf(Attributes eventInfo, int seqTag) {
        Sequence seq = eventInfo.getSequence(seqTag);
        return seq != null ? seq.size() : 0;
    }

    private AAssociateRQ mkAAssociateRQ(ApplicationEntity localAE, String localAET, TransferCapability.Role role) {
        AAssociateRQ aarq = new AAssociateRQ();
        aarq.setCallingAET(localAET);
        TransferCapability tc = localAE.getTransferCapabilityFor(UID.StorageCommitmentPushModelSOPClass, role);
        aarq.addPresentationContext(new PresentationContext(1, UID.StorageCommitmentPushModelSOPClass,
                tc.getTransferSyntaxes()));
        if (role == TransferCapability.Role.SCP)
            aarq.addRoleSelection(new RoleSelection(UID.StorageCommitmentPushModelSOPClass, false, true));
        return aarq;
    }
}
