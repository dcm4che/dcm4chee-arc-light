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

package org.dcm4chee.arc.compress.impl;

import org.dcm4che3.data.UID;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4chee.arc.Scheduler;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.entity.Location;
import org.dcm4chee.arc.entity.Series;
import org.dcm4chee.arc.retrieve.LocationInputStream;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveService;
import org.dcm4chee.arc.store.InstanceLocations;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreService;
import org.dcm4chee.arc.store.StoreSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2018
 */
@ApplicationScoped
public class CompressionScheduler extends Scheduler {

    private static final Logger LOG = LoggerFactory.getLogger(CompressionScheduler.class);

    @Inject
    private CompressionEJB ejb;

    @Inject
    private RetrieveService retrieveService;

    @Inject
    private StoreService storeService;

    protected CompressionScheduler() {
        super(Mode.scheduleWithFixedDelay);
    }

    @Override
    protected Duration getPollingInterval() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        return arcDev != null && arcDev.getCompressionAETitle() != null
                ? arcDev.getCompressionPollingInterval()
                : null;
    }

    @Override
    protected Logger log() {
        return LOG;
    }

    @Override
    protected void execute() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        if (!ScheduleExpression.emptyOrAnyContains(Calendar.getInstance(), arcDev.getCompressionSchedules()))
            return;

        String aet = arcDev.getCompressionAETitle();
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        if (ae == null || !ae.isInstalled()) {
            LOG.warn("No such Application Entity: " + aet);
            return;
        }
        int fetchSize = arcDev.getCompressionFetchSize();
        int permits = arcDev.getCompressionThreads();
        Semaphore semaphore = new Semaphore(permits);
        List<Series.Compression> compressions;
        do {
            for (Series.Compression compression : compressions = ejb.findSeriesForCompression(fetchSize)) {
                if (ejb.claimForCompression(compression)) {
                    acquire(semaphore, 1);
                    device.execute(() -> {
                        process(ae, compression);
                        semaphore.release();
                    });
                }
            }
            int acquire = permits - (permits = arcDev.getCompressionThreads());
            if (acquire > 0)
                acquire(semaphore, acquire);
            else if (acquire < 0)
                semaphore.release(-acquire);
        }
        while (arcDev.getCompressionPollingInterval() != null && compressions.size() == fetchSize);
        acquire(semaphore, permits);
    }

    private static void acquire(Semaphore semaphore, int permits) {
        if (!semaphore.tryAcquire(permits)) {
            try {
                int n = permits - semaphore.availablePermits();
                LOG.debug("Wait for completion of compression of {} Series", n);
                semaphore.acquire(permits);
                LOG.debug("Compression of {} Series completed", n);
            } catch (InterruptedException e) {
                LOG.warn("Failed to wait for completion of compression of Series", e);
            }
        }
    }

    private void process(ApplicationEntity ae, Series.Compression compr) {
        ArchiveAEExtension arcAE = ae.getAEExtensionNotNull(ArchiveAEExtension.class);
        if (compr.instancePurgeState == Series.InstancePurgeState.PURGED) {
            try (StoreSession session = storeService.newStoreSession(ae)) {
                storeService.restoreInstances(session,
                        compr.studyInstanceUID,
                        compr.seriesInstanceUID,
                        arcAE.getPurgeInstanceRecordsDelay());
            } catch (Exception e) {
                LOG.warn("Failed to restore Instance records for compression of Series[iuid={}] of Study[iuid={}]:\n",
                        compr.seriesInstanceUID, compr.studyInstanceUID, e);
                return;
            }
        }
        try (
            RetrieveContext retrCtx = retrieveService.newRetrieveContext(
                ae.getAETitle(), compr.studyInstanceUID, compr.seriesInstanceUID, null);
            StoreSession session = storeService.newStoreSession(ae)) {
            retrieveService.calculateMatches(retrCtx);
            LOG.info("Start compression of {} Instances of Series[iuid={}] of Study[iuid={}]",
                    retrCtx.getNumberOfMatches(), compr.seriesInstanceUID, compr.studyInstanceUID);
            int failures = 0;
            int completed = 0;
            int skipped = 0;
            ArchiveCompressionRule compressionRule = new ArchiveCompressionRule();
            compressionRule.setTransferSyntax(compr.transferSyntaxUID);
            compressionRule.setImageWriteParams(compr.imageWriteParams());
            for (InstanceLocations inst : retrCtx.getMatches()) {
                if (alreadyCompressed(inst.getLocations(), compr.transferSyntaxUID)) {
                    LOG.info("{} of Series[iuid={}] of Study[iuid={}] already compressed with {} - skipped",
                            inst, compr.seriesInstanceUID, compr.studyInstanceUID, UID.nameOf(compr.transferSyntaxUID));
                    skipped++;
                    continue;
                }
                try (LocationInputStream lis = retrieveService.openLocationInputStream(retrCtx, inst)) {
                    StoreContext ctx = storeService.newStoreContext(session);
                    ctx.setCompressionRule(compressionRule);
                    storeService.compress(ctx, inst, lis.stream);
                    completed++;
                } catch (Exception e) {
                    LOG.info("Failed to compress {} of Series[iuid={}] of Study[iuid={}]:\n",
                            inst, compr.seriesInstanceUID, compr.studyInstanceUID, e);
                    failures++;
                }
            }
            ejb.updateDB(compr, completed, failures);
            LOG.info("Finished compression of {} Instances of Series[iuid={}] of Study[iuid={}] - {} failures, {} skipped",
                    completed, compr.seriesInstanceUID, compr.studyInstanceUID, failures, skipped);
            retrieveService.updateLocations(retrCtx);
        } catch (IOException e) {
            LOG.warn("Failed to calculate Instances for compression of Series[iuid={}] of Study[iuid={}]:\n",
                    compr.seriesInstanceUID, compr.studyInstanceUID, e);
        }
    }

    private boolean alreadyCompressed(List<Location> locations, String tsuid) {
        return locations.stream().anyMatch(l -> Location.isDicomFile(l) && l.getTransferSyntaxUID().equals(tsuid));
    }
}
