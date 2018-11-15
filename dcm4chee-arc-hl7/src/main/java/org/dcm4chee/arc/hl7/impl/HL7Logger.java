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

package org.dcm4chee.arc.hl7.impl;

import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.HL7DeviceExtension;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.HL7ConnectionEvent;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.ArchiveHL7ApplicationExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Sep 2018
 */
@ApplicationScoped
public class HL7Logger {

    private static final Logger LOG = LoggerFactory.getLogger(HL7Logger.class);

    @Inject
    private Device device;

    public void onHL7Connection(@Observes HL7ConnectionEvent event) {
        UnparsedHL7Message msg = event.getHL7Message();
        switch (event.getType()) {
            case MESSAGE_RECEIVED:
                log(msg, hl7LogFilePattern(msg));
                break;
            case MESSAGE_PROCESSED:
                if (event.getException() != null)
                    log(msg, hl7ErrorLogFilePattern(msg));
                break;
        }
    }

    private ArchiveDeviceExtension arcdev() {
        return device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
    }

    private ArchiveHL7ApplicationExtension arcHL7App(UnparsedHL7Message msg) {
        HL7Application hl7App = device.getDeviceExtensionNotNull(HL7DeviceExtension.class)
                .getHL7Application(msg.msh().getReceivingApplicationWithFacility(), true);
        return hl7App != null ? hl7App.getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class) : null;
    }

    private String hl7LogFilePattern(UnparsedHL7Message msg) {
        ArchiveHL7ApplicationExtension arcHL7App = arcHL7App(msg);
        return arcHL7App != null ? arcHL7App.hl7LogFilePattern() : arcdev().getHL7LogFilePattern();
    }

    private String hl7ErrorLogFilePattern(UnparsedHL7Message msg) {
        ArchiveHL7ApplicationExtension arcHL7App = arcHL7App(msg);
        return arcHL7App != null ? arcHL7App.hl7ErrorLogFilePattern() : arcdev().getHL7ErrorLogFilePattern();
    }

    private void log(UnparsedHL7Message msg, String dirpath) {
        if (dirpath == null)
            return;

        String filePath = getPath(StringUtils.replaceSystemProperties(dirpath), msg.getSerialNo(), msg.msh());
        Path dir = Paths.get(filePath.substring(0,filePath.lastIndexOf("/")));
        Path file = dir.resolve(filePath.substring(filePath.lastIndexOf("/")+1));
        try {
            if (!Files.exists(dir))
                Files.createDirectories(dir);
            if (!Files.exists(file))
                Files.createFile(file);
            try (BufferedOutputStream out = new BufferedOutputStream(
                    Files.newOutputStream(file, StandardOpenOption.APPEND))) {
                new DataOutputStream(out);
                out.write(msg.data());
            }
        } catch (Exception e) {
            LOG.warn("Failed to write log file : ", dir, file, e);
        }
    }

    private String getPath(String s, int serialNo, HL7Segment msh) {
        int i = s.indexOf("${");
        if (i == -1)
            return s;

        StringBuilder sb = new StringBuilder(s.length());
        int j = -1;
        do {
            sb.append(s.substring(j+1, i));
            if ((j = s.indexOf('}', i+2)) == -1) {
                j = i-1;
                break;
            }
            String s1 = s.substring(i+2, j);
            String dateFormat = null;
            Date date = new Date();
            if (s1.substring(0,4).equalsIgnoreCase("date")) {
                try {
                    date = new SimpleDateFormat("yyyyMMdd").parse(msh.getField(6, null).substring(0,8));
                } catch (Exception e) {
                    LOG.warn("Failed to format date : ", e);
                }
                dateFormat = new SimpleDateFormat(s1.substring(s1.indexOf(",")+1)).format(date);
            }
            String prop = s1.equalsIgnoreCase("SerialNo")
                    ? String.valueOf(serialNo)
                    : s1.substring(0,4).equalsIgnoreCase("MSH-")
                    ? msh.getField(Integer.parseInt(s1.substring(4))-1, null)
                    : s1.substring(0,4).equalsIgnoreCase("date")
                    ? dateFormat : null;
            String s2 = s.substring(i, j+1);
            String val = s.startsWith("env.", i+2)
                    ? System.getenv(s.substring(i+6, j))
                    : prop;
            sb.append(val != null ? val : s2);
            i = s.indexOf("${", j+1);
        } while (i != -1);
        sb.append(s.substring(j+1));
        return sb.toString();
    }
}
