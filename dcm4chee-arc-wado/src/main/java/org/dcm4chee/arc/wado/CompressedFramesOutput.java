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
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Apr 2016
 */
public class CompressedFramesOutput implements StreamingOutput, Closeable {

    private final RetrieveContext ctx;
    private final InstanceLocations inst;
    private DicomInputStream dis;
    private final int[] frameList;
    private final Path[] spoolFiles;
    private final Path spoolDirectory;
    private int frame = 1;
    private int frameListIndex;

    public CompressedFramesOutput(RetrieveContext ctx, InstanceLocations inst, int[] frameList, Path spoolDirectory) {
        this.ctx = ctx;
        this.inst = inst;
        this.frameList = frameList;
        this.spoolDirectory = spoolDirectory;
        this.spoolFiles = spoolDirectory != null ? new Path[frameList.length] : null;
    }

    @Override
    public void write(OutputStream out) throws IOException {
        try {
            if (frameListIndex == 0)
                initDicomInputStream();

            if (dis == null) {
                Files.copy(spoolFiles[frameListIndex++], out);
                return;
            }
            int nextFrame =  frameList[frameListIndex++];
            while (frame < nextFrame) {
                skipFrame();
                frame++;
            }
            if (!dis.readItemHeader())
                throw new IOException(
                        "Number of data fragments not sufficient for number of frames in requested object");

            StreamUtils.copy(dis, out, dis.length());
            frame++;
            if (allFramesRead())
                close();
        } catch (IOException e) {
            close();
            throw e;
        }
    }

    private void skipFrame() throws IOException {
        if (!dis.readItemHeader())
            throw new IOException(
                    "Number of data fragments not sufficient for number of frames in requested object");

        for (int i = frameListIndex; i < frameList.length; i++) {
            if (frame == frameList[i]) {
                spoolFiles[i] = Files.createTempFile(spoolDirectory, null, null);
                try (OutputStream o = Files.newOutputStream(spoolFiles[i])) {
                    StreamUtils.copy(dis, o, dis.length());
                }
                return;
            }
        }
        dis.skipFully(dis.length());
    }

    private boolean allFramesRead() {
        for (int i = frameListIndex; i < frameList.length; i++) {
            if (frame <= frameList[i])
                return false;
        }
        return true;
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
