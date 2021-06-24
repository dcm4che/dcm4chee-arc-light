/*
 * **** BEGIN LICENSE BLOCK *****
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
 * Portions created by the Initial Developer are Copyright (C) 2015-2019
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
 * **** END LICENSE BLOCK *****
 *
 */

package org.dcm4chee.arc.stgcmt.impl;

import org.dcm4che3.net.ApplicationEntity;
import org.dcm4chee.arc.Scheduler;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.entity.Series;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.Period;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2018
 */
@ApplicationScoped
public class StgVerScheduler extends Scheduler {

    private static final Logger LOG = LoggerFactory.getLogger(StgVerScheduler.class);

    @Inject
    private StgCmtEJB ejb;

    protected StgVerScheduler() {
        super(Mode.scheduleWithFixedDelay);
    }

    @Override
    protected Logger log() {
        return LOG;
    }

    @Override
    protected Duration getPollingInterval() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        return arcDev != null ? arcDev.getStorageVerificationPollingInterval() : null;
    }

    @Override
    protected void execute() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        if (!ScheduleExpression.emptyOrAnyContains(Calendar.getInstance(), arcDev.getStorageVerificationSchedules()))
            return;

        String aet = arcDev.getStorageVerificationAETitle();
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        if (ae == null || !ae.isInstalled()) {
            LOG.warn("No such Application Entity: " + aet);
            return;
        }
        int maxScheduled = arcDev.getStorageVerificationMaxScheduled();
        int remaining = remaining(maxScheduled, arcDev.firstQueueOf(TaskProcessorName.STG_VERIFIER));
        if (remaining == 0) {
            LOG.info("Maximal number of scheduled Storage Verification Tasks[{}] reached", maxScheduled);
            return;
        }
        int fetchSize = arcDev.getStorageVerificationFetchSize();
        Period period = arcDev.getStorageVerificationPeriod();
        String batchID = arcDev.getStorageVerificationBatchID();
        List<Series.StorageVerification> storageVerifications;
        do {
            storageVerifications = ejb.findSeriesForScheduledStorageVerifications(fetchSize);
            LOG.info("Found {} Series scheduled for Storage Verification", storageVerifications.size());
            for (Series.StorageVerification storageVerification : storageVerifications) {
                if (claim(storageVerification, period)) {
                    try {
                        if (ejb.scheduleStgVerTask(aet,
                                storageVerification.studyInstanceUID, storageVerification.seriesInstanceUID, batchID)) {
                            if (--remaining <= 0) {
                                LOG.info("Maximal number of scheduled Storage Verification Tasks[{}] reached", maxScheduled);
                                return;
                            }
                        }
                    } catch (Exception e) {
                        LOG.warn("Failed to schedule {}:\n", storageVerification, e);
                    }
                }
            }
        }
        while (getPollingInterval() != null && storageVerifications.size() == fetchSize);
    }

    private int remaining(int maxScheduled, QueueDescriptor queueDesc) {
        if (maxScheduled <= 0) {
            return Integer.MAX_VALUE;
        }
        long scheduled = ejb.countScheduledTasksOnThisDevice(queueDesc.getQueueName());
        return (int) Math.max(maxScheduled - scheduled, 0L);
    }

    private boolean claim(Series.StorageVerification storageVerification, Period period) {
        try {
            return ejb.claimForStorageVerification(
                    storageVerification.seriesPk,
                    storageVerification.storageVerificationTime,
                    nextVerificationTime(storageVerification, period))
                    > 0;
        } catch (Exception e) {
            LOG.info("Failed to claim {}:\n", storageVerification, e);
            return false;
        }
    }

    private Date nextVerificationTime(Series.StorageVerification storageVerification, Period period) {
        if (period == null) return null;

        Calendar cal = Calendar.getInstance();
        cal.setTime(storageVerification.storageVerificationTime);
        cal.add(Calendar.YEAR, period.getYears());
        cal.add(Calendar.MONTH, period.getMonths());
        cal.add(Calendar.DAY_OF_MONTH, period.getDays());
        return cal.getTime();
    }
}
