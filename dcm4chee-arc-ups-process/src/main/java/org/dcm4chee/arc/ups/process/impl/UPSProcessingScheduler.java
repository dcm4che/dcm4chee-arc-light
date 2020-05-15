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

package org.dcm4chee.arc.ups.process.impl;

import org.dcm4che3.data.*;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4chee.arc.Scheduler;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.query.Query;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.query.util.OrderByTag;
import org.dcm4chee.arc.query.util.QueryParam;
import org.dcm4chee.arc.ups.UPSService;
import org.dcm4chee.arc.ups.process.UPSProcessor;
import org.dcm4chee.arc.ups.process.UPSProcessorProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Mar 2020
 */
@ApplicationScoped
public class UPSProcessingScheduler extends Scheduler {

    private static final Logger LOG = LoggerFactory.getLogger(UPSProcessingScheduler.class);
    private static final List<OrderByTag> orderByTags = Arrays.asList(
            OrderByTag.desc(Tag.ScheduledProcedureStepPriority),
            OrderByTag.asc(Tag.ScheduledProcedureStepStartDateTime));

    @Inject
    private UPSService upsService;

    @Inject
    private QueryService queryService;

    @Inject
    private UPSProcessorProvider processorProvider;

    private Map<String, ProcessWorkitems> inProcess = new ConcurrentHashMap<>();

    protected UPSProcessingScheduler() {
        super(Mode.scheduleWithFixedDelay);
    }

    @Override
    protected Logger log() {
        return LOG;
    }

    @Override
    protected Duration getPollingInterval() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        return arcDev.getUPSProcessingPollingInterval();
    }

    @Override
    protected void execute() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
        if (arcDev.getUPSProcessingPollingInterval() == null) return;
        Calendar now = Calendar.getInstance();
        for (UPSProcessingRule rule : arcDev.getUPSProcessingRules()) {
            if (!inProcess.containsKey(rule.getCommonName())
                    && ScheduleExpression.emptyOrAnyContains(now, rule.getSchedules())) {
                try {
                    device.execute(new ProcessWorkitems(rule));
                } catch (Exception e){
                    LOG.warn("Failed to process {}: ", rule, e);
                }
            }
        }
    }

    private static Attributes getQueryKeys(UPSProcessingRule rule) {
        Attributes keys = new Attributes(10);
        setCode(keys, Tag.ScheduledWorkitemCodeSequence, rule.getScheduledWorkitemCode());
        setCode(keys, Tag.ScheduledStationNameCodeSequence, rule.getScheduledStationName());
        setCode(keys, Tag.ScheduledStationClassCodeSequence, rule.getScheduledStationClass());
        setCode(keys, Tag.ScheduledStationGeographicLocationCodeSequence, rule.getScheduledStationLocation());
        keys.setString(Tag.InputReadinessState, VR.CS, rule.getInputReadinessState().toString());
        keys.setString(Tag.ProcedureStepState, VR.CS, "SCHEDULED");
        setString(keys, Tag.ScheduledProcedureStepPriority, VR.CS, rule.getUPSPriority());
        setString(keys, Tag.WorklistLabel, VR.LO, rule.getWorklistLabel());
        setString(keys, Tag.ProcedureStepLabel, VR.LO, rule.getProcedureStepLabel());
        return keys;
    }

    private static void setString(Attributes keys, int tag, VR vr, Object value) {
        if (value != null) {
            keys.setString(tag, vr, value.toString());
        }
    }

    private static void setCode(Attributes attrs, int sqtag, Code code) {
        if (code != null) {
            attrs.newSequence(sqtag, 1).add(code.toItem());
        }
    }

    private class ProcessWorkitems implements Runnable {
        private final ArchiveAEExtension arcAE;
        private final UPSProcessor processor;
        private final QueryContext queryContext;

        public ProcessWorkitems(UPSProcessingRule rule) {
            ApplicationEntity ae = Objects.requireNonNull(
                    device.getApplicationEntity(rule.getAETitle(), true),
                    () -> String.format("No such Archive AE - %s", rule.getAETitle()));
            arcAE = ae.getAEExtensionNotNull(ArchiveAEExtension.class);
            this.processor = processorProvider.getUPSProcessor(rule);
            QueryParam queryParam = new QueryParam(arcAE);
            this.queryContext = queryService.newQueryContext(ae, queryParam);
            queryContext.setQueryKeys(getQueryKeys(rule));
            queryContext.setOrderByTags(orderByTags);
        }

        @Override
        public void run() {
            ArchiveDeviceExtension arcDev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
            UPSProcessingRule rule = processor.getUPSProcessingRule();
            int permits = rule.getMaxThreads();
            Semaphore semaphore = permits > 1 ? new Semaphore(permits) : null;
            inProcess.put(rule.getCommonName(), this);
            try {
                while (processMatching(arcDev, semaphore));
                if (semaphore != null) {
                    semaphore.acquire(permits);
                }
            } catch (Exception e) {
                LOG.warn("Failure on processing {}:/n", rule, e);
            } finally {
                inProcess.remove(rule.getCommonName());
            }
        }

        private boolean processMatching(ArchiveDeviceExtension arcDev, Semaphore semaphore)
                throws DicomServiceException, InterruptedException {
            queryContext.getQueryKeys().setDateRange(Tag.ScheduledProcedureStepStartDateTime, VR.DT,
                    new DateRange(null, Calendar.getInstance().getTime()));
            try (Query query = queryService.createUPSQuery(queryContext)) {
                query.executeQuery(arcDev.getUPSProcessingFetchSize());
                if (!query.hasMoreMatches()) {
                    return false;
                }
                do {
                    if (arcDev.getUPSProcessingPollingInterval() == null) {
                        return false;
                    }
                    Attributes match = query.nextMatch();
                    if (semaphore != null) {
                        semaphore.acquire();
                        device.execute(() -> {
                            try {
                                processor.process(arcAE, match);
                            } finally {
                                semaphore.release();
                            }
                        });
                    } else {
                        processor.process(arcAE, match);
                    }
                } while (query.hasMoreMatches());
            }
            return true;
        }
    }
}
