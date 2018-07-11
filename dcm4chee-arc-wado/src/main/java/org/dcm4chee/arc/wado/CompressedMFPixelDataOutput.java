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

import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.util.SafeClose;
import org.dcm4che3.util.StreamUtils;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveService;
import org.dcm4chee.arc.store.InstanceLocations;

import javax.ws.rs.core.StreamingOutput;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Apr 2016
 */
public class CompressedMFPixelDataOutput implements StreamingOutput, Closeable {

    private final RetrieveContext ctx;
    private final InstanceLocations inst;
    private DicomInputStream dis;
    private int remainingFrames;

    public CompressedMFPixelDataOutput(RetrieveContext ctx, InstanceLocations inst, int numFrames) {
        this.ctx = ctx;
        this.inst = inst;
        this.remainingFrames = numFrames;
    }

    @Override
    public void write(OutputStream out) throws IOException {
        try {
            if (dis == null)
                initDicomInputStream();

            if (!dis.readItemHeader())
                throw new IOException(
                        "Number of data fragments not sufficient for number of frames in requested object");

            StreamUtils.copy(dis, out, dis.length());
            if (--remainingFrames <= 0)
                close();
        } catch (IOException e) {
            close();
            throw e;
        }
    }

    private void initDicomInputStream() throws IOException {
        RetrieveService service = ctx.getRetrieveService();
        dis = service.openDicomInputStream(ctx, inst);
        dis.readDataset(-1, Tag.PixelData);
        if (dis.tag() != Tag.PixelData || dis.length() != -1 || !dis.readItemHeader()) {
            throw new IOException("No or incorrect encapsulated compressed pixel data in requested object");
        }
        dis.skipFully(dis.length());
    }

    @Override
    public void close() {
        SafeClose.close(dis);
        dis = null;
    }
}
