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

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since May 2016
 */
public class Cache<K,V> {

    public static final class Entry<V> {
        final V value;
        final long fetchTime;
        Entry(V value, long fetchTime) {
            this.value = value;
            this.fetchTime = fetchTime;
        }
        public V value() {
            return value;
        }
    }

    private int maxSize;
    private long staleTimeout;

    private final LinkedHashMap<K,Entry<V>> cache = new LinkedHashMap<K,Entry<V>>(){
        @Override
        protected boolean removeEldestEntry(Map.Entry<K, Cache.Entry<V>> eldest) {
            return maxSize > 0 && size() > maxSize;
        }
    };

    public int getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(int maxSize) {
        if (maxSize > 0) {
            int remove = cache.size() - maxSize;
            if (remove > 0) {
                Iterator<Map.Entry<K, Entry<V>>> iter = cache.entrySet().iterator();
                do {
                    iter.next();
                    iter.remove();
                } while (--remove > 0);
            }
        }
        this.maxSize = maxSize;
    }

    public long getStaleTimeout() {
        return staleTimeout;
    }

    public void setStaleTimeout(long staleTimeout) {
        this.staleTimeout = staleTimeout;
    }

    public Entry<V> getEntry(K key) {
        if (staleTimeout <= 0)
            return cache.get(key);

        long minFetchTime = System.currentTimeMillis() - staleTimeout;
        for (Iterator<Entry<V>> iter = cache.values().iterator(); iter.hasNext(); iter.remove())
            if (iter.next().fetchTime > minFetchTime)
                return cache.get(key);
        return null;
    }

    public V get(K key) {
        Entry<V> entry = getEntry(key);
        return entry != null ? entry.value : null;
    }

    public V put(K key, V value) {
        Entry<V> entry = cache.put(key, new Entry<V>(value, System.currentTimeMillis()));
        return entry != null ? entry.value : null;
    }

    public V remove(K key) {
        Entry<V> entry = cache.remove(key);
        return entry != null ? entry.value : null;
    }

    public void clear() {
        cache.clear();
    }
}
