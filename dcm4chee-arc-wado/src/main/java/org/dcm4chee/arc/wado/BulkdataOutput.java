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

package org.dcm4chee.arc.wado;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.util.StreamUtils;
import org.dcm4che3.util.TagUtils;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveService;
import org.dcm4chee.arc.store.InstanceLocations;

import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Apr 2016
 */
public class BulkdataOutput implements StreamingOutput {

    private final RetrieveContext ctx;
    private final InstanceLocations inst;
    private final int[] attributePath;

    public BulkdataOutput(RetrieveContext ctx, InstanceLocations inst, int... attributePath) {
        this.ctx = ctx;
        this.inst = inst;
        this.attributePath = attributePath;
    }

    @Override
    public void write(final OutputStream out) throws IOException {
        RetrieveService service = ctx.getRetrieveService();
        try (DicomInputStream dis = service.openDicomInputStream(ctx, inst)) {
            Attributes attrs = null;
            for (int level = 0; level < attributePath.length; level++) {
                if ((level & 1) == 0) {
                    int stopTag = attributePath[level];
                    if (attrs == null)
                        attrs = dis.readDataset(-1, stopTag);
                    else
                        dis.readAttributes(attrs, -1, stopTag);
                    if (dis.tag() != stopTag)
                        throw new IOException(missingBulkdata());
                } else {
                    int index = attributePath[level];
                    int i = 0;
                    while (i < index && dis.readItemHeader()) {
                        int len = dis.length();
                        boolean undefLen = len == -1;
                        if (undefLen) {
                            Attributes item = new Attributes(attrs.bigEndian());
                            dis.readAttributes(item, len, Tag.ItemDelimitationItem);
                        } else {
                            dis.skipFully(len);
                        }
                        ++i;
                    }
                    if (i < index || !dis.readItemHeader())
                        throw new IOException(missingBulkdata());
                }
            }
            StreamUtils.copy(dis, out, dis.length());
        }
    }

    private String missingBulkdata() {
        StringBuilder sb = new StringBuilder();
        sb.append("No bulkdata ");
        for (int i = 0; i < attributePath.length; i++) {
            if ((i & 1) == 0) {
                sb.append(TagUtils.toString(attributePath[i]));
            } else {
                sb.append('[').append(attributePath[i]).append(']');
            }
        }
        sb.append(" in requested object");
        return sb.toString();
    }
}
