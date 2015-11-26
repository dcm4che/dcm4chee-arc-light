package org.dcm4chee.arc.export.mgt.impl;

import org.dcm4che3.net.Device;
import org.dcm4chee.arc.Scheduler;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.Duration;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
@ApplicationScoped
public class ExportScheduler extends Scheduler {

    @Inject
    private Device device;

    @Inject
    private ExportManagerEJB ejb;

    @Override
    protected Duration getPollingInterval() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        return arcDev.getExportTaskPollingInterval();
    }

    @Override
    public void run() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        int fetchSize = arcDev.getExportTaskFetchSize();
        while (ejb.scheduleExportTasks(fetchSize) == fetchSize)
            ;
    }
}
