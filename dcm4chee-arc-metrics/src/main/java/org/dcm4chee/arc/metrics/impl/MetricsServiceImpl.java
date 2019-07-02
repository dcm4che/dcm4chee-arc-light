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
import org.dcm4chee.arc.metrics.MetricsService;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.DoubleSummaryStatistics;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2019
 */
@ApplicationScoped
public class MetricsServiceImpl implements MetricsService {
    private static final int BUFFER_SIZE = 61;
    private static final int MILLIS_PER_MIN = 60000;
    private final Map<String, DoubleSummaryStatistics[]> map = new HashMap<>();
    private volatile long currentTimeMins = currentTimeMins();

    @Inject
    private Device device;

    @Override
    public void accept(String name, double value) {
        DoubleSummaryStatistics[] a = map.computeIfAbsent(name, x -> new DoubleSummaryStatistics[BUFFER_SIZE]);
        int i = sync();
        if (a[i] == null)
            a[i] = new DoubleSummaryStatistics();
        a[i].accept(value);
    }

    @Override
    public boolean exists(String name) {
        //TODO

        return false;
    }

    @Override
    public void forEach(String name, int start, int limit, int binSize, Consumer<DoubleSummaryStatistics> consumer) {
        if (start <= 0 || start > 60)
            throw new IllegalArgumentException("start not in range 0..60: " + start);

        if (binSize <= 0)
            throw new IllegalArgumentException("binSize not > 0: " + binSize);

        DoubleSummaryStatistics[] a = map.get(name);
        if (a == null)
            return;

        int offset = (sync() - start + BUFFER_SIZE) % BUFFER_SIZE;
        DoubleSummaryStatistics bin = null;
        for (int i = 0, n = limit > 0 ? Math.min(binSize * limit, start) : start; i < n; i++) {
            if (i % binSize == 0) {
                if (bin != null)
                    consumer.accept(bin);
                bin = new DoubleSummaryStatistics();
            }
            DoubleSummaryStatistics other = a[(offset + i) % BUFFER_SIZE];
            if (other != null)
                bin.combine(other);
        }
        consumer.accept(bin);
    }

    private static long currentTimeMins() {
        return System.currentTimeMillis() / MILLIS_PER_MIN;
    }

    private int sync() {
        long currentTimeMins = currentTimeMins();
        int index = (int) (currentTimeMins % BUFFER_SIZE);
        if (this.currentTimeMins < currentTimeMins) {
            synchronized (this) {
                long diff = currentTimeMins - this.currentTimeMins;
                if (diff > 0) {
                    map.values().forEach(nullify(index, diff));
                    this.currentTimeMins = currentTimeMins;
                }
            }
        }
        return index;
    }

    private static Consumer<? super DoubleSummaryStatistics[]> nullify(int index, long diff) {
        if (diff >= BUFFER_SIZE)
            return x -> Arrays.fill(x, null);

        int toIndex = index + 1;
        int fromIndex = toIndex - (int) diff;
        if (fromIndex >= 0)
            return x -> Arrays.fill(x, fromIndex, toIndex, null);

        return x -> {
            Arrays.fill(x, 0, toIndex, null);
            Arrays.fill(x, fromIndex + BUFFER_SIZE, BUFFER_SIZE, null);
        };
    }
}
