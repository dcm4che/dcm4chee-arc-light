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

package org.dcm4chee.arc.mpps.scu.impl;

import org.dcm4che3.audit.AuditMessages;
import org.dcm4che3.conf.api.IApplicationEntityCache;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.*;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.MPPSForwardRule;
import org.dcm4chee.arc.entity.Task;
import org.dcm4chee.arc.mpps.MPPSContext;
import org.dcm4chee.arc.mpps.scu.MPPSSCU;
import org.dcm4chee.arc.procedure.ProcedureContext;
import org.dcm4chee.arc.procedure.ProcedureService;
import org.dcm4chee.arc.qmgt.Outcome;
import org.dcm4chee.arc.qmgt.TaskManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Sep 2015
 */
@ApplicationScoped
class MPPSSCUImpl implements MPPSSCU {

    private static final Logger LOG = LoggerFactory.getLogger(MPPSSCUImpl.class);

    @Inject
    private TaskManager taskManager;

    @Inject
    private Device device;

    @Inject
    private ProcedureService procService;

    @Inject
    private Event<ProcedureContext> procedureEvent;

    @Inject
    private IApplicationEntityCache aeCache;

    void onMPPSReceive(@Observes MPPSContext ctx) {
        if (ctx.getException() != null)
            return;

        ArchiveAEExtension arcAE = ctx.getArchiveAEExtension();
        Calendar now = Calendar.getInstance();
        Attributes mppsAttrs = ctx.getMPPS().getAttributes();
        Set<String> remoteAETs = arcAE.mppsForwardRule()
                .filter(rule -> rule.match(now,
                        ctx.getRemoteHostName(),
                        ctx.getCallingAET(),
                        ctx.getLocalHostName(),
                        ctx.getCalledAET(),
                        mppsAttrs))
                .map(MPPSForwardRule::getDestinations)
                .flatMap(Stream::of)
                .collect(Collectors.toSet());
        for (String remoteAET : arcAE.mppsForwardDestinations()) {
            remoteAETs.add(remoteAET);
        }
        Attributes ssAttrs = mppsAttrs.getNestedDataset(Tag.ScheduledStepAttributesSequence);
        Attributes patAttrs = ctx.getMPPS().getPatient().getAttributes();
        IDWithIssuer idWithIssuer = IDWithIssuer.pidOf(patAttrs);
        for (String remoteAET : remoteAETs) {
            try {
                Task task = new Task();
                task.setDeviceName(device.getDeviceName());
                task.setQueueName(QUEUE_NAME);
                task.setScheduledTime(new Date());
                task.setLocalAET(ctx.getLocalApplicationEntity().getAETitle());
                task.setRemoteAET(remoteAET);
                task.setDIMSE(ctx.getDimse().name());
                task.setSOPInstanceUID(ctx.getSopInstanceUID());
                task.setAccessionNumber(ssAttrs.getString(Tag.AccessionNumber));
                task.setStudyInstanceUID(ssAttrs.getString(Tag.StudyInstanceUID));
                task.setPatientID(idWithIssuer != null ? idWithIssuer.toString() : null);
                task.setPatientName(patAttrs.getString(Tag.PatientName));
                task.setPayload(ctx.getAttributes());
                task.setStatus(Task.Status.SCHEDULED);
                taskManager.schedule(task);
            } catch (Exception e) {
                LOG.warn("Failed to Schedule Forward of {} MPPS[uid={}] to AE: {}",
                        ctx.getDimse(), ctx.getSopInstanceUID(), remoteAET, e);
            }
        }
    }

    @Override
    public Outcome forwardMPPS(String localAET, String remoteAET, Dimse dimse, String sopInstanceUID, Attributes attrs,
                               Attributes procAttrs)
            throws Exception {
        ApplicationEntity localAE = device.getApplicationEntity(localAET, true);
        ApplicationEntity remoteAE = aeCache.findApplicationEntity(remoteAET);
        AAssociateRQ aarq = mkAAssociateRQ(localAE);
        Association as = localAE.connect(remoteAE, aarq);
        ProcedureContext pCtx = createProcedureCtx(sopInstanceUID,
                                    attrs.getString(Tag.PerformedProcedureStepStatus),
                                    as,
                                    dimse,
                                    procAttrs);
        try {
            DimseRSP rsp = dimse == Dimse.N_CREATE_RQ
                    ? as.ncreate(UID.ModalityPerformedProcedureStep, sopInstanceUID, attrs, null)
                    : as.nset(UID.ModalityPerformedProcedureStep, sopInstanceUID, attrs, null);
            rsp.next();
            return outcome(rsp.getCommand().getInt(Tag.Status, -1),
                            dimse,
                            sopInstanceUID,
                            remoteAET,
                            pCtx);
        } finally {
            try {
                as.release();
            } catch (IOException e) {
                LOG.info("{}: Failed to release association to {}", as, remoteAET);
            } finally {
                procedureEvent.fire(pCtx);
            }
        }
    }

    private Outcome outcome(int status, Dimse dimse, String sopInstanceUID, String remoteAET, ProcedureContext pCtx) {
        if (status != Status.Success) {
            String warning = "Forward " + dimse + " MPPS[uid=" + sopInstanceUID + "] to AE: " + remoteAET
                    + " failed with error status: " + Integer.toHexString(status) + 'H';
            pCtx.setOutcomeMsg(warning);
            return new Outcome(Task.Status.WARNING, warning);
        }

        return new Outcome(Task.Status.COMPLETED,
                    "Forward " + dimse +  " MPPS[uid=" + sopInstanceUID + "] to AE: " + remoteAET);
    }

    private ProcedureContext createProcedureCtx(
            String sopInstanceUID, String status, Association as, Dimse dimse, Attributes procAttrs) {
        ProcedureContext pCtx = procService.createProcedureContext().setAssociation(as);
        pCtx.setAttributes(procAttrs);
        pCtx.setMppsUID(sopInstanceUID);
        pCtx.setStatus(status);
        pCtx.setEventActionCode(dimse == Dimse.N_CREATE_RQ
                ? AuditMessages.EventActionCode.Create : AuditMessages.EventActionCode.Update);
        return pCtx;
    }

    private AAssociateRQ mkAAssociateRQ(ApplicationEntity localAE) {
        AAssociateRQ aarq = new AAssociateRQ();
        TransferCapability tc = localAE.getTransferCapabilityFor(UID.ModalityPerformedProcedureStep,
                TransferCapability.Role.SCU);
        aarq.addPresentationContext(new PresentationContext(1, UID.ModalityPerformedProcedureStep,
                tc.getTransferSyntaxes()));
        return aarq;
    }
}
