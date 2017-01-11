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

package org.dcm4chee.arc.ian.scu.impl;

import org.dcm4che3.data.*;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.Scheduler;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.entity.IanTask;
import org.dcm4chee.arc.entity.MPPS;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.exporter.ExportContext;
import org.dcm4chee.arc.mpps.MPPSContext;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Apr 2016
 */
@ApplicationScoped
public class IANScheduler extends Scheduler {
    private static final Logger LOG = LoggerFactory.getLogger(IANScheduler.class);

    @Inject
    private Device device;

    @Inject
    private IANEJB ejb;

    @Inject
    private QueryService queryService;

    protected IANScheduler() {
        super(Mode.scheduleWithFixedDelay);
    }

    @Override
    protected Logger log() {
        return LOG;
    }

    @Override
    protected Duration getPollingInterval() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        return arcDev.getIanTaskPollingInterval();
    }

    @Override
    protected void execute() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        int fetchSize = arcDev.getIanTaskFetchSize();
        long ianTaskPk = 0;
        List<IanTask> ianTasks;
        Attributes ian;
        do {
            ianTasks = ejb.fetchIANTasksForMPPS(device.getDeviceName(), ianTaskPk, fetchSize);
            for (IanTask ianTask : ianTasks)
                try {
                    ianTaskPk = ianTask.getPk();
                    ApplicationEntity ae = device.getApplicationEntity(ianTask.getCallingAET(), true);
                    LOG.info("Check availability of {}", ianTask.getMpps());
                    ian = createIANForMPPS(ae, ianTask.getMpps());
                    if (ian != null) {
                        LOG.info("Schedule {}", ianTask);
                        ejb.scheduleIANTask(ianTask, ian);
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to process {}", ianTask, e);
                }
        } while (ianTasks.size() == fetchSize);
        do {
            ianTasks = ejb.fetchIANTasksForStudy(device.getDeviceName(), fetchSize);
            for (IanTask ianTask : ianTasks)
                try {
                    ApplicationEntity ae = device.getApplicationEntity(ianTask.getCallingAET(), true);
                    if (ianTask.getMpps() == null) {
                        LOG.info("Schedule {}", ianTask);
                        ejb.scheduleIANTask(ianTask, createIAN(ae, ianTask.getStudyInstanceUID(), null, null));
                    } else {
                        if (ae.getAEExtension(ArchiveAEExtension.class).ianOnTimeout()
                                && (ian = createIAN(ae, ianTask.getMpps().getStudyInstanceUID(), null, null)) != null) {
                            LOG.warn("Timeout for {} exceeded - schedule IAN for available instances", ianTask);
                            ejb.scheduleIANTask(ianTask, ian);
                        } else {
                            LOG.warn("Timeout for {} exceeded - no IAN", ianTask);
                            ejb.removeIANTask(ianTask);
                        }
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to process {}", ianTask, e);
                }
        } while (ianTasks.size() == fetchSize);
    }

    void onMPPSReceive(@Observes MPPSContext ctx) {
        MPPS mpps = ctx.getMPPS();
        if (mpps.getStatus() == MPPS.Status.COMPLETED) {
            ApplicationEntity ae = ctx.getLocalApplicationEntity();
            ArchiveAEExtension arcAE = ctx.getArchiveAEExtension();
            String[] ianDestinations = arcAE.ianDestinations();
            if (ianDestinations.length != 0 && arcAE.ianDelay() == null) {
                try {
                    IanTask ianTaskForMPPS = ejb.createIANTaskForMPPS(arcAE, ctx.getCalledAET(), mpps);
                    LOG.info("{}: Created {}", ctx, ianTaskForMPPS);
                } catch (Exception e) {
                    LOG.warn("{}: Failed to create IanTask", ctx, e);
                }
            }
        }
    }

    public void onStore(@Observes StoreContext ctx) {
        if (ctx.getLocations().isEmpty() || ctx.getException() != null)
            return;

        StoreSession session = ctx.getStoreSession();
        ArchiveAEExtension arcAE = session.getArchiveAEExtension();
        String[] ianDestinations = arcAE.ianDestinations();
        Duration ianDelay = arcAE.ianDelay();
        if (ianDestinations.length != 0 && ianDelay != null) {
            try {
                IANEJB.IanTaskAction ianTaskAction =
                        ejb.createOrUpdateIANTaskForStudy(arcAE, session.getCalledAET(), ctx.getStudyInstanceUID());
                switch (ianTaskAction.action) {
                    case CREATED:
                        LOG.info("{}: Created {}", ctx, ianTaskAction.ianTask);
                        break;
                    case UPDATED:
                        LOG.debug("{}: Updated {}", ctx, ianTaskAction.ianTask);
                        break;
                }
            } catch (Exception e) {
                LOG.warn("{}: Failed to create or update IanTask", ctx, e);
            }
        }
    }

    public void onExport(@Observes ExportContext ctx) {
        ExporterDescriptor descriptor = ctx.getExporter().getExporterDescriptor();
        if (descriptor.getIanDestinations().length != 0)
            if (ctx.isOnlyIAN()
                    || !ctx.isOnlyStgCmt() && ctx.getOutcome().getStatus() == QueueMessage.Status.COMPLETED)
                sendIAN(ctx, descriptor);
        return;
    }

    private void sendIAN(ExportContext ctx, ExporterDescriptor descriptor) {
        ApplicationEntity ae = device.getApplicationEntity(ctx.getAETitle(), true);
        Attributes attrs = createIAN(ae, ctx.getStudyInstanceUID(), null,
                descriptor.getInstanceAvailability(), descriptor.getRetrieveAETitles());
        for (String remoteAET : descriptor.getIanDestinations())
            ejb.scheduleMessage(ctx.getAETitle(), attrs, remoteAET);
    }

    private Attributes createIANForMPPS(ApplicationEntity ae, MPPS mpps) {
        Attributes mppsAttrs = mpps.getAttributes();
        String studyInstanceUID = mpps.getStudyInstanceUID();
        Sequence perfSeriesSeq = mppsAttrs.getSequence(Tag.PerformedSeriesSequence);
        Attributes ian = new Attributes(3);
        Sequence refSeriesSeq = ian.newSequence(Tag.ReferencedSeriesSequence, perfSeriesSeq.size());
        ian.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUID);
        for (Attributes perfSeries : perfSeriesSeq) {
            String seriesInstanceUID = perfSeries.getString(Tag.SeriesInstanceUID);
            Attributes ianForSeries = queryService.createIAN(ae, studyInstanceUID, seriesInstanceUID, null);
            if (ianForSeries == null)
                return null;

            Attributes refSeries = ianForSeries.getSequence(Tag.ReferencedSeriesSequence).remove(0);
            Sequence available = refSeries.getSequence(Tag.ReferencedSOPSequence);
            if (!allAvailable(perfSeries.getSequence(Tag.ReferencedImageSequence), available) ||
                !allAvailable(perfSeries.getSequence(Tag.ReferencedNonImageCompositeSOPInstanceSequence), available))
                return null;

            refSeriesSeq.add(refSeries);
        }
        ian.newSequence(Tag.ReferencedPerformedProcedureStepSequence, 1).add(refMPPS(mpps));
        return ian;
    }

    private Attributes refMPPS(MPPS mpps) {
        Attributes refMPPS = new Attributes(3);
        refMPPS.setString(Tag.ReferencedSOPClassUID, VR.UI, UID.ModalityPerformedProcedureStepSOPClass);
        refMPPS.setString(Tag.ReferencedSOPInstanceUID, VR.UI, mpps.getSopInstanceUID());
        refMPPS.setNull(Tag.PerformedWorkitemCodeSequence, VR.SQ);
        return refMPPS;
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

    private Attributes createIAN(ApplicationEntity ae, String studyInstanceUID, String seriesInstanceUID,
                                 Availability availability, String... retrieveAETs) {
        Attributes ian = queryService.createIAN(ae, studyInstanceUID, seriesInstanceUID, availability, retrieveAETs);
        if (ian != null)
            ian.setNull(Tag.ReferencedPerformedProcedureStepSequence, VR.SQ);

        return ian;
    }

}
