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

package org.dcm4chee.arc.wado;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.HttpHeaders;
import org.dcm4che3.data.Tag;
import org.dcm4chee.arc.store.InstanceLocations;

/**
 * @author Gunter Zeilinger <gunterze@protonmail.com>
 * @since Jan 2026
 */
public class ContentRange {
    public final long start;
    public final long end;
    public final long total;

    public ContentRange(long start, long end, long total) {
        this.start = start;
        this.end = end;
        this.total = total;
    }

    public static ContentRange from(HttpServletRequest request, InstanceLocations inst) {
        String rangeValue = request.getHeader("Range");
        if (rangeValue != null && rangeValue.startsWith("bytes=")) {
            int dashPos = rangeValue.indexOf('-', 6);
            if (dashPos != -1) {
                long total = inst.getAttributes().getLong(Tag.EncapsulatedPixelDataValueTotalLength,
                        inst.getLocations().getFirst().getSize());
                try {
                    long start = Long.parseUnsignedLong(rangeValue.substring(6, dashPos));
                    long end = rangeValue.length() > dashPos + 1
                            ? Long.parseUnsignedLong(rangeValue.substring(dashPos + 1))
                            : total - 1;
                    if (start <= end && end < total) {
                        return new ContentRange(start, end, total);
                    }
                } catch (NumberFormatException e) {
                }
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "bytes " + start + '-' + end + '/' + total;
    }

    public long length() {
        return end - start + 1;
    }
}
