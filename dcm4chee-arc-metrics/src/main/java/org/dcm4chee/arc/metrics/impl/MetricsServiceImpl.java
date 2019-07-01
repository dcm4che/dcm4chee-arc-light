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

import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.metrics.MetricsService;

import javax.enterprise.context.ApplicationScoped;
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
    private static final int MINS_PER_HOUR = 60;
    private static final int MILLIS_PER_MIN = 60000;
    private static final DoubleSummaryStatistics EMPTY = new DoubleSummaryStatistics() {
        @Override
        public void accept(double value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void combine(DoubleSummaryStatistics other) {
            throw new UnsupportedOperationException();
        }
    };

    private final Map<String, DoubleSummaryStatistics[]> map = new HashMap<>();
    private volatile long currentTimeMins = currentTimeMins();

    @Override
    public void accept(String name, double value) {
        DoubleSummaryStatistics[] a = map.computeIfAbsent(name, x -> new DoubleSummaryStatistics[MINS_PER_HOUR]);
        int i = sync();
        if (a[i] == null)
            a[i] = new DoubleSummaryStatistics();
        a[i].accept(value);
    }

    @Override
    public DoubleSummaryStatistics get(String name, int index) {
        if (index < 0 || index >= MINS_PER_HOUR)
            throw new IndexOutOfBoundsException("Index out of range: " + index);

        DoubleSummaryStatistics[] a = map.get(name);
        if (a == null)
            return EMPTY;

        return StringUtils.maskNull(a[(sync() - index + MINS_PER_HOUR) % MINS_PER_HOUR], EMPTY);
    }

    @Override
    public DoubleSummaryStatistics combine(String name, int fromIndex, int toIndex) {
        checkRange(fromIndex, toIndex);

        DoubleSummaryStatistics[] a = map.get(name);
        if (a == null)
            return EMPTY;

        DoubleSummaryStatistics tot = null;
        int index = (sync() - fromIndex + MINS_PER_HOUR) % MINS_PER_HOUR;
        int n = toIndex - fromIndex;
        while (n-- > 0) {
            DoubleSummaryStatistics other = a[index++ % MINS_PER_HOUR];
            if (tot == null)
                tot = other;
            else if (other != null)
                tot.combine(other);
        }
        return StringUtils.maskNull(tot, EMPTY);
    }

    @Override
    public void forEach(String name, int fromIndex, int toIndex, Consumer<DoubleSummaryStatistics> consumer) {
        checkRange(fromIndex, toIndex);

        DoubleSummaryStatistics[] a = map.get(name);
        if (a == null)
            return;

        int index = (sync() - fromIndex + MINS_PER_HOUR) % MINS_PER_HOUR;
        int n = toIndex - fromIndex;
        while (n-- > 0) {
            consumer.accept(StringUtils.maskNull(a[index++ % MINS_PER_HOUR], EMPTY));
        }
    }

    private static long currentTimeMins() {
        return System.currentTimeMillis() / MILLIS_PER_MIN;
    }

    private int sync() {
        long currentTimeMins = currentTimeMins();
        if (this.currentTimeMins < currentTimeMins)
            setCurrentTimeMins(currentTimeMins);
        return (int) (currentTimeMins % MINS_PER_HOUR);
    }

    private synchronized void setCurrentTimeMins(long currentTimeMins) {
        int index = (int) (this.currentTimeMins % MINS_PER_HOUR);
        int n = (int) Math.min(currentTimeMins - this.currentTimeMins, MINS_PER_HOUR);
        while (n-- > 0) {
            int i = ++index % MINS_PER_HOUR;
            map.values().forEach(x -> x[i] = null);
        }
        this.currentTimeMins = currentTimeMins;
    }

    private static void checkRange(int fromIndex, int toIndex) {
        if (fromIndex < 0 || fromIndex > toIndex || toIndex > MINS_PER_HOUR)
            throw new IndexOutOfBoundsException("Range [" + fromIndex + ", " + toIndex + ") out of bounds");
    }
}
