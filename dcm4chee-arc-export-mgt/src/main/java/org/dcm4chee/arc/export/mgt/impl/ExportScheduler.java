package org.dcm4chee.arc.export.mgt.impl;

import org.dcm4chee.arc.Scheduler;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.export.mgt.ExportManager;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.EJBException;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
@ApplicationScoped
public class ExportScheduler extends Scheduler {

    private static final Logger LOG = LoggerFactory.getLogger(ExportScheduler.class);

    @Inject
    private ExportManager ejb;

    protected ExportScheduler() {
        super(Mode.scheduleWithFixedDelay);
    }

    @Override
    protected Logger log() {
        return LOG;
    }

    @Override
    protected Duration getPollingInterval() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        return arcDev.getExportTaskPollingInterval();
    }

    @Override
    protected void execute() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        int fetchSize = arcDev.getExportTaskFetchSize();
        while (getPollingInterval() != null && ejb.scheduleExportTasks(fetchSize) == fetchSize)
            ;
    }

    public void onStore(@Observes StoreContext ctx) {
        if (ctx.getException() != null)
            return;

        StoreSession session = ctx.getStoreSession();
        String hostname = session.getRemoteHostName();
        String sendingAET = session.getCallingAET();
        String receivingAET = session.getCalledAET();
        Calendar now = Calendar.getInstance();
        ArchiveAEExtension arcAE = session.getArchiveAEExtension();
        ArchiveDeviceExtension arcDev = arcAE.getArchiveDeviceExtension();
        for (Map.Entry<String, ExportRule> entry
                : arcAE.findExportRules(hostname, sendingAET, receivingAET, ctx.getAttributes(), now).entrySet()) {
            String exporterID = entry.getKey();
            ExportRule rule = entry.getValue();
            switch(rule.getExportReoccurredInstances()) {
                case NEVER:
                    if (ctx.getPreviousInstance() != null) {
                        continue;
                    }
                case REPLACE:
                    if (ctx.getLocations().isEmpty()) {
                        continue;
                    }
            }
            ExporterDescriptor desc = arcDev.getExporterDescriptor(exporterID);
            if (desc == null) {
                LOG.warn("{}: No Exporter configured with ID:{} - cannot schedule Export Task triggered by {}",
                        session, exporterID, rule);
                continue;
            }
            Date scheduledTime = scheduledTime(now, rule.getExportDelay(), desc.getSchedules());
            switch (rule.getEntity()) {
                case Study:
                    createOrUpdateStudyExportTask(session, exporterID,
                            ctx.getStudyInstanceUID(),
                            scheduledTime);
                    if (rule.isExportPreviousEntity() && ctx.isPreviousDifferentStudy())
                        createOrUpdateStudyExportTask(session, exporterID,
                                ctx.getPreviousInstance().getSeries().getStudy().getStudyInstanceUID(),
                                scheduledTime);
                    break;
                case Series:
                    createOrUpdateSeriesExportTask(session, exporterID,
                            ctx.getStudyInstanceUID(),
                            ctx.getSeriesInstanceUID(),
                            scheduledTime);
                    if (rule.isExportPreviousEntity() && ctx.isPreviousDifferentSeries())
                        createOrUpdateSeriesExportTask(session, exporterID,
                                ctx.getPreviousInstance().getSeries().getStudy().getStudyInstanceUID(),
                                ctx.getPreviousInstance().getSeries().getSeriesInstanceUID(),
                                scheduledTime);
                    break;
                case Instance:
                    createOrUpdateInstanceExportTask(session, exporterID,
                            ctx.getStudyInstanceUID(),
                            ctx.getSeriesInstanceUID(),
                            ctx.getSopInstanceUID(),
                            scheduledTime);
                    break;
            }
        }
    }

    private Date scheduledTime(Calendar cal, Duration exportDelay, ScheduleExpression[] schedules) {
        if (exportDelay != null) {
            cal = (Calendar) cal.clone();
            cal.add(Calendar.SECOND, (int) exportDelay.getSeconds());
        }
        cal = ScheduleExpression.ceil(cal, schedules);
        return cal.getTime();
    }

    private boolean createOrUpdateStudyExportTask(StoreSession session, String exporterID,
            String studyIUID, Date scheduledTime) {
        ArchiveAEExtension arcAE = session.getArchiveAEExtension();
        ArchiveDeviceExtension arcDev = arcAE.getArchiveDeviceExtension();
        int retries = arcDev.getStoreUpdateDBMaxRetries();
        for (;;) {
            try {
                ejb.createOrUpdateStudyExportTask(exporterID, studyIUID, scheduledTime);
                return true;
            } catch (EJBException e) {
                if (retries-- > 0) {
                    LOG.info("{}: Failed to update Study Export Task - retry:\n", session, e);
                } else {
                    LOG.warn("{}: Failed to update Study Export Task:\n", session, e);
                    return false;
                }
            }
            try {
                Thread.sleep(arcDev.storeUpdateDBRetryDelay());
            } catch (InterruptedException e) {
                LOG.info("{}: Failed to delay retry to update Study Export Task:\n", session, e);
            }
        }
    }

    private boolean createOrUpdateSeriesExportTask(StoreSession session, String exporterID,
            String studyIUID, String seriesIUID, Date scheduledTime) {
        ArchiveAEExtension arcAE = session.getArchiveAEExtension();
        ArchiveDeviceExtension arcDev = arcAE.getArchiveDeviceExtension();
        int retries = arcDev.getStoreUpdateDBMaxRetries();
        for (;;) {
            try {
                ejb.createOrUpdateSeriesExportTask(exporterID, studyIUID, seriesIUID, scheduledTime);
                return true;
            } catch (EJBException e) {
                if (retries-- > 0) {
                    LOG.info("{}: Failed to update Series Export Task - retry:\n", session, e);
                } else {
                    LOG.warn("{}: Failed to update Series Export Task:\n", session, e);
                    return false;
                }
            }
            try {
                Thread.sleep(arcDev.storeUpdateDBRetryDelay());
            } catch (InterruptedException e) {
                LOG.info("{}: Failed to delay retry to update Series Export Task:\n", session, e);
            }
        }
    }

    private boolean createOrUpdateInstanceExportTask(StoreSession session, String exporterID,
            String studyIUID, String seriesIUID, String sopIUID, Date scheduledTime) {
        ejb.createOrUpdateInstanceExportTask(exporterID, studyIUID, seriesIUID, sopIUID, scheduledTime);
        ArchiveAEExtension arcAE = session.getArchiveAEExtension();
        ArchiveDeviceExtension arcDev = arcAE.getArchiveDeviceExtension();
        int retries = arcDev.getStoreUpdateDBMaxRetries();
        for (;;) {
            try {
                ejb.createOrUpdateInstanceExportTask(exporterID, studyIUID, seriesIUID, sopIUID, scheduledTime);
                return true;
            } catch (EJBException e) {
                if (retries-- > 0) {
                    LOG.info("{}: Failed to update Instance Export Task - retry:\n", session, e);
                } else {
                    LOG.warn("{}: Failed to update Instance Export Task:\n", session, e);
                    return false;
                }
            }
            try {
                Thread.sleep(arcDev.storeUpdateDBRetryDelay());
            } catch (InterruptedException e) {
                LOG.info("{}: Failed to delay retry to update Instance Export Task:\n", session, e);
            }
        }
    }
}
