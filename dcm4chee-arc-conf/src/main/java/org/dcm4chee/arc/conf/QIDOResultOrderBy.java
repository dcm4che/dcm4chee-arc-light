/*
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
 * Portions created by the Initial Developer are Copyright (C) 2015-2018
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
 */

package org.dcm4chee.arc.conf;

import org.dcm4che3.util.TagUtils;

import java.util.*;

/**
 * @author Gunter Zeilinger <gunterze@protonmail.com>
 * @since Jan 2025
 */
public class QIDOResultOrderBy {
    private final int[] tagPath;
    private final boolean descending;
    private final String s;

    public enum QIDOService {
        patients, studies, series, instances, workitems, mwlitems, mpps
    }

    public static EnumMap<QIDOService, QIDOResultOrderBy[]> parse(String[] ss) {
        EnumMap<QIDOService, QIDOResultOrderBy[]> result = new EnumMap<>(QIDOService.class);
        for (String s : ss) {
            try {
                int index = s.indexOf(':');
                result.put(QIDOService.valueOf(s.substring(0, index)), parseValues(s.substring(index + 1)));
            } catch (IllegalArgumentException iae) {
                throw new IllegalArgumentException(s);
            }
        }
        return result;
    }

    private static QIDOResultOrderBy[] parseValues(String s) {
        if (s.indexOf(',') < 0) {
            return new QIDOResultOrderBy[]{ new QIDOResultOrderBy(s.trim()) };
        }
        List<QIDOResultOrderBy> list = new ArrayList<>();
        StringTokenizer st = new StringTokenizer(s, ",");
        while (st.hasMoreTokens()) {
            list.add(new QIDOResultOrderBy(st.nextToken().trim()));
        }
        return list.toArray(new QIDOResultOrderBy[list.size()]);
    }

    public static String[] toStrings(EnumMap<QIDOService, QIDOResultOrderBy[]> qidoResultOrderBy) {
        String[] result = new String[qidoResultOrderBy.size()];
        int i = 0;
        for (Map.Entry<QIDOService, QIDOResultOrderBy[]> entry : qidoResultOrderBy.entrySet()) {
            StringBuilder sb = new StringBuilder(entry.getKey().toString());
            char delim = ':';
            for (QIDOResultOrderBy value: entry.getValue()) {
                sb.append(delim).append(value);
                delim = ',';
            }
            result[i++] = sb.toString();
        }
        return result;
    }

    private QIDOResultOrderBy(String s) {
        boolean descending = s.charAt(0) == '-';
        this.tagPath = TagUtils.parseTagPath(descending ? s.substring(1) : s);
        this.descending = descending;
        this.s = s;
    }

    @Override
    public String toString() {
        return s;
    }

    public int getTag(int index) {
        return tagPath[index];
    }

    public int getTagPathLength() {
        return tagPath.length;
    }

    public int getTag() {
        return tagPath[tagPath.length - 1];
    }

    public boolean isDescending() {
        return descending;
    }
}
