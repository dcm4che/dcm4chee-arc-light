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
 * Portions created by the Initial Developer are Copyright (C) 2013-2025
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

package org.dcm4chee.arc.hl7.psu;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4chee.arc.Scheduler;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.entity.HL7PSUTask;
import org.dcm4chee.arc.entity.MPPS;
import org.dcm4chee.arc.mpps.MPPSContext;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jan 2017
 */
@ApplicationScoped
public class HL7PSUScheduler extends Scheduler {
    private static final Logger LOG = LoggerFactory.getLogger(HL7PSUScheduler.class);
    private static final String MISSING_HL7PSU_DELAY = "HL7 Procedure Status Update Delay not configured";
    private static final String MISSING_STUDY_TRIGGER = "HL7 Procedure Status Update Trigger STUDY_RECEIVED not configured";
    private static final String MISSING_MPPS_TRIGGER = "HL7 Procedure Status Update Trigger MPPS_RECEIVED not configured";
    private static final String HL7PSU_CONDITIONS_NO_MATCH = "HL7 Procedure Status Update Conditions didn't match.";
    private static final String MISSING_HL7PSU_TIMEOUT = "HL7 Procedure Status Update Timeout not configured";
    private static final String INVALID_SSA = "Missing MPPS attributes PlacerOrderNumberImagingServiceRequest and FillerOrderNumberImagingServiceRequest in ScheduledStepAttributesSequence.";
    private static final String MISSING_HL7PSU_ACTION = "HL7 Procedure Status Update Action not configured.";
    private static final String HL7PSU_TASK_ABORT_MSG = "Abort HL7 Procedure Status Update Task creation.";

    @Inject
    private HL7PSUEJB ejb;

    @Inject
    private QueryService queryService;

    protected HL7PSUScheduler() {
        super(Mode.scheduleWithFixedDelay);
    }

    @Override
    protected Logger log() {
        return LOG;
    }

    @Override
    protected Duration getPollingInterval() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        return arcDev.getHL7PSUTaskPollingInterval();
    }

    @Override
    protected void execute() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        int fetchSize = arcDev.getHL7PSUTaskFetchSize();
        executeHL7PSUTasksForMPPS(fetchSize);
        executeHL7PSUTasksForStudy(fetchSize);
    }

    private void executeHL7PSUTasksForStudy(int fetchSize) {
        List<HL7PSUTask> hl7psuTasks;
        do {
            hl7psuTasks = ejb.fetchHL7PSUTasksForStudy(device.getDeviceName(), fetchSize);
            for (HL7PSUTask hl7psuTask : hl7psuTasks) {
                if (getPollingInterval() == null)
                    return;

                try {
                    if (hl7psuTask.getMpps() == null)
                        ejb.scheduleHL7PSUTask(hl7psuTask);
                    else {
                        if (device.getApplicationEntity(hl7psuTask.getAETitle())
                                .getAEExtension(ArchiveAEExtension.class)
                                .hl7PSUOnTimeout()) {
                            LOG.warn("Timeout for {} exceeded - schedule HL7 Procedure Status Update anyway", hl7psuTask);
                            ejb.scheduleHL7PSUTask(hl7psuTask);
                        } else {
                            LOG.warn("Timeout for {} exceeded - no HL7 Procedure Status Update", hl7psuTask);
                            ejb.removeHL7PSUTask(hl7psuTask);
                        }
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to process {}:\n", hl7psuTask, e);
                }
            }
        } while (hl7psuTasks.size() == fetchSize);
    }

    private void executeHL7PSUTasksForMPPS(int fetchSize) {
        long hl7psuTaskPk = 0;
        List<HL7PSUTask> hl7psuTasks;
        do {
            hl7psuTasks = ejb.fetchHL7PSUTasksForMPPS(device.getDeviceName(), hl7psuTaskPk, fetchSize);
            for (HL7PSUTask hl7psuTask : hl7psuTasks) {
                if (getPollingInterval() == null)
                    return;

                try {
                    hl7psuTaskPk = hl7psuTask.getPk();
                    ApplicationEntity ae = device.getApplicationEntity(hl7psuTask.getAETitle());
                    LOG.info("Check availability of {}", hl7psuTask.getMpps());
                    if (checkAllRefInMpps(ae, hl7psuTask.getMpps()))
                        ejb.scheduleHL7PSUTask(hl7psuTask);
                } catch (Exception e) {
                    LOG.warn("Failed to process {}:\n", hl7psuTask, e);
                }
            }
        } while (hl7psuTasks.size() == fetchSize);
    }

    public void onStore(@Observes StoreContext ctx) {
        if (ctx.getException() != null || ctx.getLocations().isEmpty())
            return;

        StoreSession session = ctx.getStoreSession();
        ArchiveAEExtension arcAE = session.getArchiveAEExtension();

        HL7PSUAction[] hl7PSUActions = arcAE.hl7PSUAction();
        if (hl7PSUActions.length == 0) {
            LOG.info(MISSING_HL7PSU_ACTION + HL7PSU_TASK_ABORT_MSG);
            return;
        }

        RejectionNote rejectionNote = ctx.getRejectionNote();
        if (rejectionNote != null) {
            if (!rejectionNote.isRevokeRejection()
                    && arcAE.isHL7PSUTrigger(HL7PSUTrigger.REJECTION_NOTE_RECEIVED))
                ejb.scheduleHL7Msg(arcAE, ctx.getAttributes());

            return;
        }

        if (ctx.getCreatedStudy() != null
                && arcAE.isHL7PSUTrigger(HL7PSUTrigger.FIRST_OBJECT_OF_STUDY_RECEIVED)
                && arcAE.hl7PSUConditions().match(
                        session.getRemoteHostName(),
                        session.getCallingAET(),
                        session.getLocalHostName(),
                        session.getCalledAET(),
                        ctx.getAttributes()))
            ejb.scheduleHL7Msg(arcAE, ctx.getAttributes());

        if (!arcAE.isHL7PSUTrigger(HL7PSUTrigger.STUDY_RECEIVED)
                || arcAE.hl7PSUDelay() == null
                || !arcAE.hl7PSUConditions().match(
                    session.getRemoteHostName(),
                    session.getCallingAET(),
                    session.getLocalHostName(),
                    session.getCalledAET(),
                    ctx.getAttributes())) {
            LOG.info(MISSING_HL7PSU_DELAY
                    + " or " + MISSING_STUDY_TRIGGER
                    + " or " + HL7PSU_CONDITIONS_NO_MATCH + HL7PSU_TASK_ABORT_MSG);
            return;
        }

        try {
            ejb.createOrUpdateHL7PSUTaskForStudy(arcAE, ctx);
        } catch (Exception e) {
            LOG.warn("{}: Failed to create or update HL7PSUTask:\n", ctx, e);
        }
    }

    private boolean validScheduledStepAttrs(MPPSContext ctx) {
        Attributes ssaAttrs = ctx.getMPPS().getAttributes().getNestedDataset(Tag.ScheduledStepAttributesSequence);
        return ssaAttrs.getString(Tag.PlacerOrderNumberImagingServiceRequest) != null
                && ssaAttrs.getString(Tag.FillerOrderNumberImagingServiceRequest) != null;
    }

    void onMPPSReceive(@Observes MPPSContext ctx) {
        if (ctx.getException() != null)
            return;

        ArchiveAEExtension arcAE = ctx.getArchiveAEExtension();
        HL7PSUAction[] hl7PSUActions = arcAE.hl7PSUAction();
        if (hl7PSUActions.length == 0) {
            LOG.info(MISSING_HL7PSU_ACTION + HL7PSU_TASK_ABORT_MSG);
            return;
        }


        if (!validScheduledStepAttrs(ctx)
                || !arcAE.isHL7PSUTrigger(HL7PSUTrigger.MPPS_RECEIVED)
                || arcAE.hl7PSUTimeout() == null) {
            LOG.info(MISSING_HL7PSU_TIMEOUT
                    + " or " + MISSING_MPPS_TRIGGER
                    + " or " + INVALID_SSA + HL7PSU_TASK_ABORT_MSG);
            return;
        }

        try {
            ejb.createHL7PSUTaskForMPPS(arcAE, ctx);
        } catch (Exception e) {
            LOG.warn("{}: Failed to create or update HL7PSUTask:\n", ctx, e);
        }

    }

    private boolean checkAllRefInMpps(ApplicationEntity ae, MPPS mpps) {
        Attributes mppsAttrs = mpps.getAttributes();
        String studyInstanceUID = mpps.getStudyInstanceUID();
        Sequence perfSeriesSeq = mppsAttrs.getSequence(Tag.PerformedSeriesSequence);
        for (Attributes perfSeries : perfSeriesSeq) {
            String seriesInstanceUID = perfSeries.getString(Tag.SeriesInstanceUID);
            Attributes ianForSeries = queryService.createIAN(ae, studyInstanceUID, new String[]{ seriesInstanceUID },
                    null, null, null, null);
            if (ianForSeries == null)
                return false;

            Attributes refSeries = ianForSeries.getSequence(Tag.ReferencedSeriesSequence).get(0);
            Sequence available = refSeries.getSequence(Tag.ReferencedSOPSequence);
            if (!allAvailable(perfSeries.getSequence(Tag.ReferencedImageSequence), available) ||
                    !allAvailable(perfSeries.getSequence(Tag.ReferencedNonImageCompositeSOPInstanceSequence), available))
                return false;
        }
        return true;
    }

    private boolean allAvailable(Sequence performed, Sequence available) {
        if (performed != null)
            for (Attributes ref : performed) {
                if (!available(ref, available))
                    return false;
            }
        return true;
    }

    private boolean available(Attributes performed, Sequence available) {
        String iuid = performed.getString(Tag.ReferencedSOPInstanceUID);
        for (Attributes ref : available) {
            if (iuid.equals(ref.getString(Tag.ReferencedSOPInstanceUID)))
                return true;
        }
        return false;
    }

}
