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

package org.dcm4chee.arc.audit;

import org.dcm4che3.util.ByteUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since May 2016
 */
class SpoolFileReader {
    private static final Logger LOG = LoggerFactory.getLogger(SpoolFileReader.class);
    private String mainInfo;
    private List<String> instanceLines = new ArrayList<>();
    private byte[] data = ByteUtils.EMPTY_BYTES;
    private byte[] ack = ByteUtils.EMPTY_BYTES;

    SpoolFileReader(Path p) {
        try (BufferedReader reader = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            this.mainInfo = reader.readLine();
            String line;
            while ((line = reader.readLine()) != null)
                this.instanceLines.add(line);
        } catch (Exception e) {
            LOG.warn("Failed to read audit spool file", e);
        }
    }

    SpoolFileReader(File file) {
        byte[] MSH = {'M', 'S', 'H'};
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {
            int readMain;
            int readData;
            ByteArrayOutputStream mainInfo = new ByteArrayOutputStream();
            ByteArrayOutputStream data = new ByteArrayOutputStream();

            int skipChar = 0;

            while ((readMain = in.read()) != -1) {
                if (readMain == (int)'\n') {
                    skipChar++;
                    break;
                }
                else
                    mainInfo.write(readMain);
            }
            this.mainInfo = new String(mainInfo.toByteArray());

            if ((readData = in.read()) != -1) {
                data.write(readData);
                skipChar++;
            }

            if (skipChar == 2) {
                ByteArrayOutputStream ack = new ByteArrayOutputStream();
                int bufLength = (int) file.length() - skipChar - this.mainInfo.length(); //skip first char of MSH of data and \n above
                byte[] buf = new byte[bufLength];
                int read = in.read(buf);
                int mshStart = indexOf(MSH, 0, buf, read);

                if (mshStart > 0) {
                    data.write(buf, 0, mshStart);
                    this.data = data.toByteArray();
                    int ackLength = buf.length - this.data.length;
                    ack.write(buf, mshStart, ackLength);
                } else {
                    data.write(buf);
                    this.data = data.toByteArray();
                }
                this.ack = ack.toByteArray();
                ack.close();
            }

            mainInfo.close();
            data.close();
        } catch (Exception e) {
            LOG.warn("Failed to read audit spool file", e);
        }
    }

    String getMainInfo() {
        return mainInfo;
    }

    List<String> getInstanceLines() {
        return instanceLines;
    }

    byte[] getData() {
        return data;
    }

    byte[] getAck() {
        return ack;
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
