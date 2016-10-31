/*
 * ** BEGIN LICENSE BLOCK *****
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
 * Portions created by the Initial Developer are Copyright (C) 2016
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
 * ** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2016
 */
public class CacheInputStream extends InputStream {

    private static int BUFFER_SIZE  = 5120;
    private static int MAX_BUFFERS  = 1024;

    private final ArrayList<byte[]> buffers = new ArrayList<>();
    private int pos;
    private int count;

    public int available() {
        return count - pos;
    }

    @Override
    public int read() throws IOException {
        if (available() <= 0) {
            return -1;
        }
        int index = pos++;
        return buffers.get(index / BUFFER_SIZE)[index % BUFFER_SIZE];
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        }

        if (pos >= count) {
            return -1;
        }

        int srcOff = pos % BUFFER_SIZE;
        int avail = Math.min(count - pos, BUFFER_SIZE - srcOff);
        if (len > avail)
            len = avail;

        byte[] src = buffers.get(pos / BUFFER_SIZE);
        System.arraycopy(src, srcOff, b, 0, len);
        pos += len;
        return len;
    }

    public boolean fillBuffers(InputStream in) throws IOException {
        pos = 0;
        count = 0;
        for (int i = 0; i < MAX_BUFFERS; i++) {
            byte[] b = getOrCreateBuffer(i);
            int r;
            int off = 0;
            int len = BUFFER_SIZE;
            do {
                r = in.read(b, off, len);
                if (r < 0)
                    return false;
                off += r;
                count += r;
            } while ((len -= r) > 0);
        }
        return true;
    }

    private byte[] getOrCreateBuffer(int i) {
        if (buffers.size() > i)
            return buffers.get(i);

        byte[] b = new byte[BUFFER_SIZE];
        buffers.add(b);
        return b;
    }

}
