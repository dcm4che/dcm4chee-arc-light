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
 *
 * **** END LICENSE BLOCK *****
 *
 */

package org.dcm4chee.arc.wado;

import org.dcm4che3.util.SafeClose;
import org.dcm4che3.util.StreamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2018
 */
public class CDAOutput implements StreamingOutput {

    private static final Logger LOG = LoggerFactory.getLogger(CDAOutput.class);
    private static final int COPY_BUFFER_SIZE = 2048;
    private static final byte[] CLINICAL_DOCUMENT = {
            '<', 'C', 'l', 'i', 'n', 'i', 'c', 'a', 'l', 'D', 'o', 'c', 'u', 'm', 'e', 'n', 't' };
    private static final byte[] XML_STYLESHEET = {
            '<', '?', 'x', 'm', 'l', '-', 's', 't', 'y', 'l', 'e', 's', 'h', 'e', 'e', 't' };
    private static final byte[] TYPE_TEXT_XSL_HREF = {
            ' ', 't', 'y', 'p', 'e', '=', '"', 't', 'e', 'x', 't', '/', 'x', 's', 'l', '"',
            ' ', 'h', 'r', 'e', 'f', '=', '"' };
    private static final byte[] PROCESSING_INSTRUCTION_END = { '"', '?', '>' };

    private final InputStream in;
    private final int length;
    private final String templateURI;

    public CDAOutput(InputStream in, int length, String templateURI) {
        this.in = in;
        this.length = length;
        this.templateURI = templateURI;
    }

    @Override
    public void write(OutputStream out) throws IOException, WebApplicationException {
        try {
            byte[] buf = new byte[Math.min(COPY_BUFFER_SIZE, length)];
            int read = in.read(buf);
            int cdaStart = indexOf(CLINICAL_DOCUMENT, 0, buf, read);
            if (cdaStart < 0) {
                LOG.info("Could not find <ClinicalDocument> root element in encapsulated CDA");
                out.write(buf, 0, read);
            } else {
                int styleSheetStart = indexOf(XML_STYLESHEET, 0, buf, cdaStart);
                out.write(buf, 0, styleSheetStart < 0 ? cdaStart : styleSheetStart);
                out.write(XML_STYLESHEET);
                out.write(TYPE_TEXT_XSL_HREF);
                out.write(templateURI.getBytes("UTF-8"));
                out.write(PROCESSING_INSTRUCTION_END);
                out.write(buf, cdaStart, read - cdaStart);
            }
            StreamUtils.copy(in, out, length - read -1, buf);
            skipPaddedByte(out);
        } finally {
            SafeClose.close(in);
        }
    }

    private void skipPaddedByte(OutputStream out) throws IOException {
        int lastByte;
        if ((lastByte = in.read()) != 0)
            out.write(lastByte);
    }

    private static int indexOf(byte[] b1, int fromIndex, byte[] b2, int length) {
        int max = length - b1.length;
        for (int i = fromIndex; i < max; i++) {
            if (startsWith(b1, i, b2)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean startsWith(byte[] b1, int fromIndex, byte[] b2) {
        int remaining = b1.length;
        int i1 = 0;
        int i2 = fromIndex;
        while (--remaining >= 0) {
            if (b2[i2++] != b1[i1++]) {
                return false;
            }
        }
        return true;
    }
}
