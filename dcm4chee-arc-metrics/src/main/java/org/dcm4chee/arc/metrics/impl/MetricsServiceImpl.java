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

package org.dcm4chee.arc.metrics.impl;

import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.MetricsDescriptor;
import org.dcm4chee.arc.event.ArchiveServiceEvent;
import org.dcm4chee.arc.metrics.MetricsService;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.DoubleSummaryStatistics;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jul 2019
 */
@ApplicationScoped
public class MetricsServiceImpl implements MetricsService {
    private static final int MILLIS_PER_MIN = 60000;
    private final Map<String, DataBins> map = new ConcurrentHashMap<>();

    @Inject
    private Device device;

    @Override
    public boolean exists(String name) {
        return device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class).hasMetricsDescriptor(name);
    }

    @Override
    public void accept(String name, double value) {
        accept(name, () -> value);
    }

    @Override
    public void acceptNanoTime(String name, long startTime) {
        accept(name, () -> (System.nanoTime() - startTime) / 1000000.);
    }

    @Override
    public void acceptDataRate(String name, long bytes, long startTime) {
        accept(name, () -> bytes * 1000. / (System.nanoTime() - startTime));
    }

    @Override
    public void accept(String name, DoubleSupplier valueSupplier) {
        MetricsDescriptor descriptor = getMetricsDescriptor(name);
        if (descriptor == null)
            return;

        long time = currentTimeMins();
        map.computeIfAbsent(name, x -> new DataBins(time, descriptor.getRetentionPeriod()))
                .accept(time, valueSupplier.getAsDouble());
    }

    public void onReload(@Observes ArchiveServiceEvent event) {
        if (event.getType() != ArchiveServiceEvent.Type.RELOADED)
            return;

        map.entrySet().removeIf(entry -> {
            MetricsDescriptor metricsDescriptor = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class)
                    .getMetricsDescriptor(entry.getKey());
            return metricsDescriptor == null
                    || metricsDescriptor.getRetentionPeriod() != entry.getValue().getRetentionPeriod();
        });
    }

    @Override
    public void forEach(String name, int limit, int binSize, Consumer<DoubleSummaryStatistics> consumer) {
        MetricsDescriptor descriptor = getMetricsDescriptor(name);
        if (descriptor == null)
            return;

        if (binSize <= 0)
            throw new IllegalArgumentException("binSize not > 0: " + binSize);

        int retentionPeriod = descriptor.getRetentionPeriod();
        if (binSize > retentionPeriod)
            binSize = retentionPeriod;
        int n = (retentionPeriod - 1) / binSize + 1;
        if (limit > 0 && n > limit)
            n = limit;

        DataBins dataBins = map.get(name);
        for (long time = currentTimeMins(); n-- > 0; time -= binSize) {
            consumer.accept(dataBins != null ? dataBins.getBin(time, binSize) : null);
        }
    }

    private static long currentTimeMins() {
        return System.currentTimeMillis() / MILLIS_PER_MIN;
    }

    private MetricsDescriptor getMetricsDescriptor(String name) {
        return device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class).getMetricsDescriptor(name);
    }

    private static class DataBins {
        volatile long acceptTime;
        final DoubleSummaryStatistics[] statistics;

        DataBins(long time, int retentionPeriod) {
            this.acceptTime = time;
            this.statistics = new DoubleSummaryStatistics[retentionPeriod];
            statistics[(int) (time % statistics.length)] = new DoubleSummaryStatistics();
        }

        int getRetentionPeriod() {
            return statistics.length;
        }

        void accept(long time, double value) {
            int i = (int) (time % statistics.length);
            if (this.acceptTime < time) {
                synchronized (this) {
                    long diff = time - this.acceptTime;
                    if (diff > 0) {
                        if (diff > 1) {
                            if (diff >= statistics.length) {
                                Arrays.fill(statistics, null);
                            } else {
                                int fromIndex = i + 1 - (int) diff;
                                if (fromIndex >= 0) {
                                    Arrays.fill(statistics, fromIndex, i, null);
                                } else {
                                    Arrays.fill(statistics, 0, i, null);
                                    Arrays.fill(statistics,
                                            fromIndex + statistics.length, statistics.length, null);
                                }
                            }
                        }
                        statistics[i] = new DoubleSummaryStatistics();
                        this.acceptTime = time;
                    }
                }
            }
            statistics[i].accept(value);
        }

        DoubleSummaryStatistics getBin(long time, int binSize) {
            long beforeAcceptTime = this.acceptTime - time;
            if (beforeAcceptTime < 0) {
                if (beforeAcceptTime + binSize <= 0)
                    return null;

                time = this.acceptTime;
                binSize += beforeAcceptTime;
            } else if (binSize > statistics.length - beforeAcceptTime) {
                binSize = (int) (statistics.length - beforeAcceptTime);
            }
            DoubleSummaryStatistics bin = null;
            for (int i = statistics.length + (int) (time % statistics.length); binSize-- > 0; i--) {
                DoubleSummaryStatistics other = statistics[i % statistics.length];
                if (other != null) {
                    if (bin == null)
                        bin = new DoubleSummaryStatistics();

                    bin.combine(other);
                }
            }
            return bin;
        }
    }
}
