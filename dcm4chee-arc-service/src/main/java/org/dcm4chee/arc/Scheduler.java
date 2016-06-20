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

package org.dcm4chee.arc;

import org.dcm4chee.arc.conf.Duration;

import javax.annotation.Resource;
import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Nov 2015
 */
public abstract class Scheduler implements Runnable {

    private static final int SECONDS_PER_DAY = 3600 * 24;

    private final Mode mode;
    volatile private long pollingIntervalInSeconds;
    volatile private LocalTime startTime;
    volatile private ScheduledFuture<?> running;

    @Resource
    private ManagedScheduledExecutorService scheduledExecutor;

    protected Scheduler(Mode mode) {
        this.mode = mode;
    }

    @Override
    public void run() {
        try {
            execute();
        } catch (Throwable e) {
            log().warn("execute throws Exception", e);
        }
    }

    public void start() {
        Duration pollingInterval = getPollingInterval();
        if (pollingInterval != null) {
            pollingIntervalInSeconds = pollingInterval.getSeconds();
            startTime = getStartTime();
            running = mode.schedule(
                    this, startTime != null ? until(startTime) : pollingIntervalInSeconds, pollingIntervalInSeconds);
        }
    }

    public void stop() {
        if (running != null) {
            running.cancel(false);
            running = null;
            pollingIntervalInSeconds = 0;
        }
    }

    public void reload() {
        Duration pollingInterval = getPollingInterval();
        if (pollingInterval == null || pollingIntervalInSeconds != pollingInterval.getSeconds()
                || !Objects.equals(startTime, getStartTime())) {
            stop();
            start();
        }
    }

    protected abstract Duration getPollingInterval();

    protected abstract org.slf4j.Logger log();

    protected abstract void execute();

    protected LocalTime getStartTime() {
        return null;
    }

    private long until(LocalTime time) {
        long until = LocalTime.now().until(startTime, ChronoUnit.SECONDS);
        return until > 0 ? until : until + SECONDS_PER_DAY;
    }

    public enum Mode {
        scheduleWithFixedDelay {
            @Override
            public ScheduledFuture schedule(Scheduler scheduler, long initialDelay, long periodOrDelay) {
                return scheduler.scheduledExecutor.scheduleWithFixedDelay(
                        scheduler, initialDelay, periodOrDelay, TimeUnit.SECONDS);
            }
        },
        scheduleAtFixedRate {
            @Override
            public ScheduledFuture schedule(Scheduler scheduler, long initialDelay, long periodOrDelay) {
                return scheduler.scheduledExecutor.scheduleAtFixedRate(
                        scheduler, initialDelay, periodOrDelay, TimeUnit.SECONDS);
            }
        };
        public abstract ScheduledFuture schedule(Scheduler scheduler, long initialDelay, long periodOrDelay);
    }
}
