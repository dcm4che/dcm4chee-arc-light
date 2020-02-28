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
 * Portions created by the Initial Developer are Copyright (C) 2015-2020
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

package org.dcm4chee.arc.procedure.impl;

import org.dcm4chee.arc.Scheduler;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.Duration;
import org.dcm4chee.arc.conf.SPSStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jan 2020
 */
@ApplicationScoped
public class MWLScheduler extends Scheduler {

    private static final Logger LOG = LoggerFactory.getLogger(MWLScheduler.class);

    protected MWLScheduler() {
        super(Mode.scheduleWithFixedDelay);
    }

    @Inject
    private ProcedureServiceEJB ejb;

    @Override
    protected Logger log() {
        return LOG;
    }

    @Override
    protected Duration getPollingInterval() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        return arcDev.getMWLPollingInterval();
    }

    @Override
    protected void execute() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        List<SPSStatus> specifiedSPSStatus = new ArrayList<>();
        Duration unspecifiedSPSStatusDelay = null;
        for (String deleteMWLDelay : arcDev.getDeleteMWLDelay()) {
            String[] spsStatusWithDelay = deleteMWLDelay.split(":");
            if (spsStatusWithDelay.length == 1)
                unspecifiedSPSStatusDelay = delay(spsStatusWithDelay[0]);
            else {
                SPSStatus spsStatus = spsStatus(spsStatusWithDelay[0]);
                Duration delay = delay(spsStatusWithDelay[1]);
                if (spsStatus == null || delay == null)
                    continue;

                specifiedSPSStatus.add(spsStatus);
                deleteMWL(spsStatus, delay);
            }
        }
        if (unspecifiedSPSStatusDelay != null)
            for (SPSStatus status : SPSStatus.values())
                if (!specifiedSPSStatus.contains(status))
                    deleteMWL(status, unspecifiedSPSStatusDelay);

    }

    private void deleteMWL(SPSStatus spsStatus, Duration delay) {
        Date before = new Date(System.currentTimeMillis() - delay.getSeconds() * 1000);
        int deleted = 0;
        int count;
        int mwlFetchSize = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class)
                                    .getMWLFetchSize();
        do {
            count = ejb.deleteMWLItems(spsStatus, before, mwlFetchSize);
            deleted += count;
        } while (count >= mwlFetchSize);
        if (deleted > 0)
            LOG.info("Deleted {} MWL Items with SPS Status {}", deleted, spsStatus);
    }

    private SPSStatus spsStatus(String name) {
        try {
            return SPSStatus.valueOf(name);
        } catch (Exception e) {
            LOG.info("Invalid SPS Status: {}", name);
        }
        return null;
    }

    private Duration delay(String val) {
        try {
            return Duration.valueOf(val);
        } catch (Exception e) {
            LOG.info("Invalid duration: {}", val);
        }
        return null;
    }
}
