/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 *  The contents of this file are subject to the Mozilla Public License Version
 *  1.1 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  http://www.mozilla.org/MPL/
 *
 *  Software distributed under the License is distributed on an "AS IS" basis,
 *  WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 *  for the specific language governing rights and limitations under the
 *  License.
 *
 *  The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 *  Java(TM), hosted at https://github.com/dcm4che.
 *
 *  The Initial Developer of the Original Code is
 *  J4Care.
 *  Portions created by the Initial Developer are Copyright (C) 2015-2017
 *  the Initial Developer. All Rights Reserved.
 *
 *  Contributor(s):
 *  See @authors listed below
 *
 *  Alternatively, the contents of this file may be used under the terms of
 *  either the GNU General Public License Version 2 or later (the "GPL"), or
 *  the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 *  in which case the provisions of the GPL or the LGPL are applicable instead
 *  of those above. If you wish to allow use of your version of this file only
 *  under the terms of either the GPL or the LGPL, and not to allow others to
 *  use your version of this file under the terms of the MPL, indicate your
 *  decision by deleting the provisions above and replace them with the notice
 *  and other provisions required by the GPL or the LGPL. If you do not delete
 *  the provisions above, a recipient may use your version of this file under
 *  the terms of any one of the MPL, the GPL or the LGPL.
 *
 */

package org.dcm4chee.arc.ian.scu;

import org.dcm4che3.data.*;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.util.UIDUtils;
import org.dcm4chee.arc.Scheduler;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.exporter.ExportContext;
import org.dcm4chee.arc.ian.scu.impl.IANEJB;
import org.dcm4chee.arc.mpps.MPPSContext;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Apr 2016
 */
@ApplicationScoped
public class IANScheduler extends Scheduler {
    private static final Logger LOG = LoggerFactory.getLogger(IANScheduler.class);

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
            for (IanTask ianTask : ianTasks) {
                if (getPollingInterval() == null)
                    return;

                try {
                    ianTaskPk = ianTask.getPk();
                    ApplicationEntity ae = device.getApplicationEntity(ianTask.getCallingAET(), true);
                    LOG.info("Check availability of {}", ianTask.getMpps());
                    ian = createIANForMPPS(ae, ianTask.getMpps(), true);
                    if (ian != null) {
                        LOG.info("Schedule {}", ianTask);
                        ejb.scheduleIANTask(ianTask, ian);
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to process {}", ianTask, e);
                }
            }
        } while (ianTasks.size() == fetchSize);
        do {
            ianTasks = ejb.fetchIANTasksForStudy(device.getDeviceName(), fetchSize);
            for (IanTask ianTask : ianTasks) {
                if (getPollingInterval() == null)
                    return;

                try {
                    ApplicationEntity ae = device.getApplicationEntity(ianTask.getCallingAET(), true);
                    if (ianTask.getMpps() == null) {
                        ian = queryService.createIAN(ae, ianTask.getStudyInstanceUID(), null,
                                null, null,null, null);
                        if (ian != null) {
                            LOG.info("Schedule {}", ianTask);
                            ejb.scheduleIANTask(ianTask, ian);
                        } else {
                            LOG.info("Ignore {} without referenced objects", ianTask);
                            ejb.removeIANTask(ianTask);
                        }
                    } else {
                        if (ae.getAEExtension(ArchiveAEExtension.class).ianOnTimeout()
                                && (ian = createIANForMPPS(ae, ianTask.getMpps(), false)) != null) {
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
            }
        } while (ianTasks.size() == fetchSize);
    }

    void onMPPSReceive(@Observes MPPSContext ctx) {
        if (ctx.getException() != null)
            return;

        MPPS mpps = ctx.getMPPS();
        if (mpps.getStatus() == MPPS.Status.COMPLETED) {
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
        if (ianDestinations.length == 0)
            return;

        RejectionNote rejectionNote = ctx.getRejectionNote();
        if (rejectionNote != null) {
            if (!rejectionNote.isRevokeRejection()) {
                Attributes ian = createIANOnReject(ctx);
                for (String ianDestination : ianDestinations) {
                    ejb.scheduleMessage(session.getCalledAET(), ian, ianDestination, new Date());
                }
            }
            return;
        }
        if (arcAE.ianDelay() != null) {
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

    private Attributes createIANOnReject(StoreContext ctx) {
        StoreSession session = ctx.getStoreSession();
        boolean addPPSRef = ctx.getRejectionNote().isIncorrectModalityWorklistEntry();
        String studyUID = ctx.getStudyInstanceUID();
        Attributes ian = queryService.createIAN(session.getLocalApplicationEntity(), studyUID,
                StringUtils.EMPTY_STRING, null, null, null, null);
        if (ian == null) {
            ian = new Attributes(2);
            ian.newSequence(Tag.ReferencedSeriesSequence, 10);
            ian.setString(Tag.StudyInstanceUID, VR.UI, studyUID);
            ian.setNull(Tag.ReferencedPerformedProcedureStepSequence, VR.SQ);
        }
        for (Attributes studyRef : ctx.getAttributes().getSequence(Tag.CurrentRequestedProcedureEvidenceSequence)) {
            if (studyUID.equals(studyRef.getString(Tag.StudyInstanceUID))) {
                for (Attributes seriesRef : studyRef.getSequence(Tag.ReferencedSeriesSequence)) {
                    String seriesUID = seriesRef.getString(Tag.SeriesInstanceUID);
                    Sequence sopSeq = seriesRef.getSequence(Tag.ReferencedSOPSequence);
                    Sequence ianSeriesSOPSeq = ianSeriesSOPSeq(ian, seriesUID, sopSeq);
                    List<Attributes> rejected = sopSeq.stream()
                            .filter(sopRef -> !contains(ianSeriesSOPSeq, sopRef))
                            .collect(Collectors.toList());
                    if (!rejected.isEmpty()) {
                        rejected.stream()
                                .map(sopRef -> ianUnavailableSOPRef(sopRef, session.getCalledAET()))
                                .forEach(ianSOPRef -> ianSeriesSOPSeq.add(ianSOPRef));
                        if (addPPSRef) {
                            try {
                                if (ejb.addPPSRef(studyUID, seriesUID, ian)) {
                                    addPPSRef = false;
                                }
                            } catch (Exception e) {
                               LOG.info("{}: Failed to determine referenced Performed Procedure Step in rejected objects",
                                       session, e);
                            }
                        }
                    }
                 }
            } else {
                LOG.info("{}: Rejection Note[uid={}] of Study[uid={}] refers objects of different Study[uid={}] - ignored in emitted IAN",
                        session,
                        ctx.getSopInstanceUID(),
                        studyUID,
                        studyRef.getString(Tag.StudyInstanceUID));
            }
            if (addPPSRef) {
                String mppsUID = UIDUtils.createUID();
                ian.newSequence(Tag.ReferencedPerformedProcedureStepSequence, 1).add(refMPPS(mppsUID));
                LOG.info("{}: No referenced Performed Procedure Step in rejected objects - create random UID[{}] for IAN",
                    session, mppsUID);
            }
        }
        return ian;
    }

    private static Attributes ianUnavailableSOPRef(Attributes sopRef, String retrieveAET) {
        Attributes refSOP = new Attributes(4);
        refSOP.setString(Tag.RetrieveAETitle, VR.AE, retrieveAET);
        refSOP.setString(Tag.InstanceAvailability, VR.CS, "UNAVAILABLE");
        refSOP.setString(Tag.ReferencedSOPClassUID, VR.UI, sopRef.getString(Tag.ReferencedSOPClassUID));
        refSOP.setString(Tag.ReferencedSOPInstanceUID, VR.UI, sopRef.getString(Tag.ReferencedSOPInstanceUID));
        return refSOP;
    }

    private static Sequence ianSeriesSOPSeq(Attributes ian, String seriesUID, Sequence sopSeq) {
        Sequence refSeriesSeq = ian.getSequence(Tag.ReferencedSeriesSequence);
        Optional<Attributes> seriesRefOpt = refSeriesSeq.stream()
                .filter(seriesRef -> seriesRef.getString(Tag.SeriesInstanceUID).equals(seriesUID))
                .findFirst();
        if (seriesRefOpt.isPresent())
            return seriesRefOpt.get().getSequence(Tag.ReferencedSOPSequence);

        Attributes seriesRef = new Attributes(2);
        seriesRef.setString(Tag.SeriesInstanceUID, VR.UI, seriesUID);
        refSeriesSeq.add(seriesRef);
        return seriesRef.newSequence(Tag.ReferencedSOPSequence, sopSeq.size());
    }

    public void onExport(@Observes ExportContext ctx) {
        if (ctx.getException() != null)
            return;

        ExporterDescriptor descriptor = ctx.getExporter().getExporterDescriptor();
        if (descriptor.getIanDestinations().length != 0
                && ctx.getOutcome().getStatus() == Task.Status.COMPLETED)
            scheduleIAN(ctx, descriptor);
    }

    public void scheduleIAN(ExportContext ctx, ExporterDescriptor descriptor) {
        ApplicationEntity ae = device.getApplicationEntity(ctx.getAETitle(), true);
        Attributes ian = queryService.createIAN(ae, ctx.getStudyInstanceUID(), null,
                null, descriptor.getRetrieveAETitles(),
                descriptor.getRetrieveLocationUID(),
                descriptor.getInstanceAvailability());
        if (ian != null)
            for (String remoteAET : descriptor.getIanDestinations())
                ejb.scheduleMessage(ctx.getAETitle(), ian, remoteAET, new Date());
    }

    public void scheduleIAN(ApplicationEntity ae, String remoteAET, String studyUID, String seriesUID, Date scheduledTime) {
        Attributes ian = queryService.createIAN(ae, studyUID, new String[]{ seriesUID }, null,
                null, null, null);
        if (ian != null)
            ejb.scheduleMessage(ae.getAETitle(), ian, remoteAET, scheduledTime);
    }

    private Attributes createIANForMPPS(ApplicationEntity ae, MPPS mpps, boolean allAvailable) {
        Attributes mppsAttrs = mpps.getAttributes();
        String studyInstanceUID = mpps.getStudyInstanceUID();
        Sequence perfSeriesSeq = mppsAttrs.getSequence(Tag.PerformedSeriesSequence);
        Attributes ian = new Attributes(3);
        Sequence refSeriesSeq = ian.newSequence(Tag.ReferencedSeriesSequence, perfSeriesSeq.size());
        ian.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUID);
        for (Attributes perfSeries : perfSeriesSeq) {
            String seriesInstanceUID = perfSeries.getString(Tag.SeriesInstanceUID);
            Attributes ianForSeries = queryService.createIAN(ae, studyInstanceUID, new String[]{ seriesInstanceUID },
                    null, null, null, null);
            if (ianForSeries == null) {
                if (allAvailable)
                    return null;

                continue;
            }

            Attributes refSeries = ianForSeries.getSequence(Tag.ReferencedSeriesSequence).remove(0);
            Sequence refImageSeq = perfSeries.getSequence(Tag.ReferencedImageSequence);
            Sequence refNonImageSeq = perfSeries.getSequence(Tag.ReferencedNonImageCompositeSOPInstanceSequence);
            Sequence available = refSeries.getSequence(Tag.ReferencedSOPSequence);
            removeNotReferred(refImageSeq, refNonImageSeq, available);
            if (allAvailable && !(containsAll(available, refImageSeq) && containsAll(available, refNonImageSeq))) {
                return null;
            }
            if (!available.isEmpty()) {
                refSeriesSeq.add(refSeries);
            }
        }
        if (refSeriesSeq.isEmpty()) {
            return null;
        }
        ian.newSequence(Tag.ReferencedPerformedProcedureStepSequence, 1).add(refMPPS(mpps.getSopInstanceUID()));
        return ian;
    }

    private static void removeNotReferred(Sequence refImageSeq, Sequence refNonImageSeq, Sequence available) {
        Iterator<Attributes> iterator = available.iterator();
        while (iterator.hasNext()) {
            Attributes refSOP = iterator.next();
            if (!contains(refImageSeq, refSOP) && !contains(refNonImageSeq, refSOP))
                iterator.remove();
        }
    }

    private static Attributes refMPPS(String mppsUID) {
        Attributes refMPPS = new Attributes(3);
        refMPPS.setString(Tag.ReferencedSOPClassUID, VR.UI, UID.ModalityPerformedProcedureStep);
        refMPPS.setString(Tag.ReferencedSOPInstanceUID, VR.UI, mppsUID);
        refMPPS.setNull(Tag.PerformedWorkitemCodeSequence, VR.SQ);
        return refMPPS;
    }

    private static boolean containsAll(Sequence available, Sequence performed) {
        if (performed != null)
            for (Attributes ref : performed) {
                if (!contains(available, ref))
                    return false;
            }
        return true;
    }

    private static boolean contains(Sequence refSOPSeq, Attributes refSOP) {
        String iuid = refSOP.getString(Tag.ReferencedSOPInstanceUID);
        for (Attributes ref : refSOPSeq) {
            if (iuid.equals(ref.getString(Tag.ReferencedSOPInstanceUID)))
                return true;
        }
        return false;
    }

}
