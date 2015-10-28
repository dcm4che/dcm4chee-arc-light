package org.dcm4chee.arc.export.mgt.impl;

import org.dcm4che3.net.Device;
import org.dcm4chee.arc.ArchiveServiceEvent;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.Duration;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
@ApplicationScoped
public class ExportScheduler {

    @Inject
    private Device device;

    @Inject
    private ExportManagerEJB ejb;

    private volatile Duration pollingIntervall;
    private volatile ScheduledFuture<?> task;

    public void onArchiveServiceEvent(@Observes ArchiveServiceEvent event) {
        switch (event) {
            case STARTED:
                start();
                break;
            case STOPPED:
                stop();
                break;
            case RELOADED:
                reload();
                break;
        }
    }

    private void start() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        pollingIntervall = arcDev.getExportTaskPollingInterval();
        if (pollingIntervall != null) {
            final int fetchSize = arcDev.getExportTaskFetchSize();
            task = device.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    while (ejb.scheduleExportTasks(fetchSize) == fetchSize)
                        ;
                }
            }, 0, pollingIntervall.getSeconds(), TimeUnit.SECONDS);
        }
    }

    private void stop() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
    }

    private void reload() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        if (task != null && !pollingIntervall.equals(arcDev.getExportTaskPollingInterval())) {
            stop();
            start();
        }
    }
}
