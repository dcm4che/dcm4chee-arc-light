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
 * Portions created by the Initial Developer are Copyright (C) 2016-2019
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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
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
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4che3.util.TagUtils;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.entity.StgCmtResult;
import org.dcm4chee.arc.entity.Task;
import org.dcm4chee.arc.exporter.DefaultExportContext;
import org.dcm4chee.arc.exporter.ExportContext;
import org.dcm4chee.arc.qmgt.Outcome;
import org.dcm4chee.arc.qmgt.TaskManager;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.stgcmt.StgCmtManager;
import org.dcm4chee.arc.stgcmt.StgCmtSCP;
import org.dcm4chee.arc.stgcmt.StgCmtSCU;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;

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
    private TaskManager taskManager;

    @Inject
    private Device device;

    @Inject
    private IApplicationEntityCache aeCache;

    @Inject
    private QueryService queryService;

    @Inject
    private StgCmtManager stgCmtManager;

    @Inject
    private StgCmtEJB ejb;

    public StgCmtImpl() {
        super(UID.StorageCommitmentPushModel);
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
        if (ctx.getException() != null)
            return;

        ExporterDescriptor descriptor = ctx.getExporter().getExporterDescriptor();
        String stgCmtSCPAETitle = descriptor.getStgCmtSCPAETitle();
        if (stgCmtSCPAETitle != null && ctx.getOutcome().getStatus() == Task.Status.COMPLETED)
            scheduleStorageCommit(ctx, descriptor);
     }

    @Override
    public void scheduleStorageCommit(ExportContext ctx, ExporterDescriptor descriptor) {
        String stgCmtSCPAETitle = descriptor.getStgCmtSCPAETitle();
        if (stgCmtSCPAETitle != null) {
            ApplicationEntity ae = device.getApplicationEntity(ctx.getAETitle(), true);
            Attributes actionInfo = createActionInfo(ctx, ae);
            scheduleNAction(ae.getCallingAETitle(stgCmtSCPAETitle), stgCmtSCPAETitle, actionInfo, ctx,
                    descriptor.getExporterID(), new Date());
        }
    }

    @Override
    public void scheduleStorageCommit(
            String localAET, String remoteAET, Attributes match, String batchID, QueryRetrieveLevel2 qrLevel,
            Date scheduledTime) {
        ExportContext ctx = createExportContext(localAET, match, batchID, qrLevel);
        ApplicationEntity ae = device.getApplicationEntity(localAET, true);
        Attributes actionInfo = createActionInfo(ctx, ae);
        scheduleNAction(ae.getCallingAETitle(remoteAET), remoteAET, actionInfo, ctx, null, scheduledTime);
    }

    private ExportContext createExportContext(String localAET, Attributes match, String batchID, QueryRetrieveLevel2 qrLevel) {
        ExportContext ctx = new DefaultExportContext(null);
        ctx.setStudyInstanceUID(match.getString(Tag.StudyInstanceUID));
        switch (qrLevel) {
            case IMAGE:
                ctx.setSopInstanceUID(match.getString(Tag.SOPInstanceUID));
            case SERIES:
                ctx.setSeriesInstanceUID(match.getString(Tag.SeriesInstanceUID));
        }
        ctx.setAETitle(localAET);
        ctx.setBatchID(batchID);
        return ctx;
    }

    private Attributes createActionInfo(ExportContext ctx, ApplicationEntity ae) {
        return queryService.createActionInfo(
                ctx.getStudyInstanceUID(), ctx.getSeriesInstanceUID(), ctx.getSopInstanceUID(), ae);
    }

    private void onNActionRQ(Association as, PresentationContext pc, Attributes rq, Attributes actionInfo)
            throws DicomServiceException {
        int actionTypeID = rq.getInt(Tag.ActionTypeID, 0);
        if (actionTypeID != 1)
            throw new DicomServiceException(Status.NoSuchActionType)
                    .setActionTypeID(actionTypeID);
        validateActionInfo(actionInfo);

        String localAET = as.getLocalAET();
        String remoteAET = as.getRemoteAET();
        try {
            as.getApplicationEntity().findCompatibleConnection(aeCache.findApplicationEntity(remoteAET));
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

    private void validateActionInfo(Attributes actionInfo) throws DicomServiceException {
        if (!actionInfo.containsValue(Tag.TransactionUID))
            throw new DicomServiceException(Status.InvalidArgumentValue,
                    "Missing Transaction UID (0008,1195)", false);
        Sequence refSopSeq = actionInfo.getSequence( Tag.ReferencedSOPSequence );
        if (refSopSeq == null || refSopSeq.isEmpty())
            throw new DicomServiceException(Status.InvalidArgumentValue,
                    "Missing Referenced SOP Sequence (0008,1199)", false);
        if (!refSopSeq.stream().allMatch(refSop -> refSop.containsValue(Tag.ReferencedSOPInstanceUID)))
            throw new DicomServiceException(Status.InvalidArgumentValue,
                    "Missing Referenced SOP Instance UID (0008,1155) in item of Referenced SOP Sequence (0008,1199)",
                    false);
    }

    private void onNEventReportRQ(Association as, PresentationContext pc, Attributes rq, Attributes eventInfo)
            throws DicomServiceException {
        try {
            stgCmtManager.addExternalRetrieveAETs(eventInfo, device);
        } catch (Exception e) {
            throw new DicomServiceException(Status.ProcessingFailure, e);
        }
        try {
            as.writeDimseRSP(pc, Commands.mkNEventReportRSP(rq, Status.Success), null);
        } catch (Exception e) {
            LOG.warn("{} << N-EVENT-REPORT-RSP failed: {}", as, e.getMessage());
        }
    }

    private void scheduleNAction(String localAET, String remoteAET, Attributes actionInfo,
                                 ExportContext ctx, String exporterID, Date scheduledTime) {
        Task task = new Task();
        task.setDeviceName(device.getDeviceName());
        task.setQueueName(StgCmtSCU.QUEUE_NAME);
        task.setType(Task.Type.STGCMT_SCU);
        task.setScheduledTime(scheduledTime);
        task.setLocalAET(localAET);
        task.setRemoteAET(remoteAET);
        task.setStudyInstanceUID(ctx.getStudyInstanceUID());
        task.setSeriesInstanceUID(ctx.getSeriesInstanceUID());
        task.setSOPInstanceUID(ctx.getSopInstanceUID());
        task.setExporterID(exporterID);
        task.setPayload(actionInfo);
        task.setStatus(Task.Status.SCHEDULED);
        task.setBatchID(ctx.getBatchID());
        taskManager.scheduleTask(task);
    }

    private void scheduleNEventReport(String localAET, String remoteAET, Attributes eventInfo) {
        Task task = new Task();
        task.setDeviceName(device.getDeviceName());
        task.setQueueName(StgCmtSCP.QUEUE_NAME);
        task.setType(Task.Type.STGCMT_SCP);
        task.setScheduledTime(new Date());
        task.setLocalAET(localAET);
        task.setRemoteAET(remoteAET);
        task.setPayload(eventInfo);
        task.setStatus(Task.Status.SCHEDULED);
        taskManager.scheduleTask(task);
    }

    @Override
    public Outcome sendNAction(String localAET, String remoteAET, String studyInstanceUID, String seriesInstanceUID,
            String sopInstanceUID, String exporterID, Long taskPK, String batchID, Attributes actionInfo)
            throws Exception  {
            DimseRSP dimseRSP = sendNActionRQ(localAET, remoteAET, studyInstanceUID, seriesInstanceUID, sopInstanceUID,
                    exporterID, taskPK, batchID, actionInfo);
            Attributes cmd = dimseRSP.getCommand();
            int status = cmd.getInt(Tag.Status, -1);
            if (status != Status.Success) {
                return new Outcome(Task.Status.WARNING,
                        "Request Storage Commitment from AE: " + remoteAET
                                + " failed with status: " + TagUtils.shortToHexString(status)
                                + "H, error comment: " + cmd.getString(Tag.ErrorComment));
            }
            return new Outcome(Task.Status.COMPLETED, "Request Storage Commitment from AE: " + remoteAET);
    }

    @Override
    public DimseRSP sendNActionRQ(String localAET, String remoteAET, String studyInstanceUID, String seriesInstanceUID,
            String sopInstanceUID, String exporterID, Long taskPK, String batchID, Attributes actionInfo)
            throws Exception  {
        ApplicationEntity localAE = device.getApplicationEntity(localAET, true);
        ApplicationEntity remoteAE = aeCache.findApplicationEntity(remoteAET);
        AAssociateRQ aarq = mkAAssociateRQ(localAE, TransferCapability.Role.SCU);
        if (!localAE.isMasqueradeCallingAETitle(remoteAET))
            aarq.setCallingAET(localAET);
        Association as = localAE.connect(remoteAE, aarq);
        try {
            StgCmtResult result = new StgCmtResult();
            result.setStgCmtRequest(actionInfo);
            result.setStudyInstanceUID(studyInstanceUID);
            result.setSeriesInstanceUID(seriesInstanceUID);
            result.setSopInstanceUID(sopInstanceUID);
            result.setExporterID(exporterID);
            result.setTaskPK(taskPK);
            result.setBatchID(batchID);
            result.setDeviceName(device.getDeviceName());
            stgCmtManager.persistStgCmtResult(result);
            DimseRSP dimseRSP = as.naction(
                    UID.StorageCommitmentPushModel,
                    UID.StorageCommitmentPushModelInstance,
                    1, actionInfo, null);
            dimseRSP.next();
            if (dimseRSP.getCommand().getInt(Tag.Status, -1) != Status.Success)
                stgCmtManager.deleteStgCmt(actionInfo.getString(Tag.TransactionUID));
            return dimseRSP;
        } catch (Exception e) {
            stgCmtManager.deleteStgCmt(actionInfo.getString(Tag.TransactionUID));
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
    public Outcome sendNEventReport(String localAET, ApplicationEntity remoteAE, Attributes eventInfo)
            throws Exception  {
            ApplicationEntity localAE = device.getApplicationEntity(localAET, true);
            AAssociateRQ aarq = mkAAssociateRQ(localAE, TransferCapability.Role.SCP);
            aarq.setCallingAET(localAET);
            Association as = localAE.connect(remoteAE, aarq);
            try {
                int successful = sequenceSizeOf(eventInfo, Tag.ReferencedSOPSequence);
                int failed = sequenceSizeOf(eventInfo, Tag.FailedSOPSequence);
                DimseRSP neventReport = as.neventReport(
                        UID.StorageCommitmentPushModel,
                        UID.StorageCommitmentPushModelInstance,
                        failed > 0 ? 2 : 1, eventInfo, null);
                neventReport.next();
                return new Outcome(failed > 0 ? Task.Status.WARNING : Task.Status.COMPLETED,
                        "Return Storage Commitment Result[successful: " + successful + ", failed: " + failed
                                + "] to AE: " + remoteAE.getAETitle());
            } finally {
                try {
                    as.release();
                } catch (IOException e) {
                    LOG.info("{}: Failed to release association to {}", as, remoteAE.getAETitle());
                }
            }
    }

    private int sequenceSizeOf(Attributes eventInfo, int seqTag) {
        Sequence seq = eventInfo.getSequence(seqTag);
        return seq != null ? seq.size() : 0;
    }

    private AAssociateRQ mkAAssociateRQ(ApplicationEntity localAE, TransferCapability.Role role) {
        AAssociateRQ aarq = new AAssociateRQ();
        TransferCapability tc = localAE.getTransferCapabilityFor(UID.StorageCommitmentPushModel, role);
        aarq.addPresentationContext(new PresentationContext(1, UID.StorageCommitmentPushModel,
                tc.getTransferSyntaxes()));
        if (role == TransferCapability.Role.SCP)
            aarq.addRoleSelection(new RoleSelection(UID.StorageCommitmentPushModel, false, true));
        return aarq;
    }
}
