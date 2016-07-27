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

package org.dcm4chee.arc.hl7;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.hl7.HL7Exception;
import org.dcm4che3.hl7.HL7Segment;

import org.dcm4che3.net.Connection;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4che3.net.hl7.service.DefaultHL7Service;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.ArchiveHL7ApplicationExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.inject.Inject;
import java.io.*;
import java.net.Socket;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;


/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jun 2016
 */
abstract class AbstractHL7Service extends DefaultHL7Service {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractHL7Service.class);
    public AbstractHL7Service(String... messageTypes) {
        super(messageTypes);
    }

    @Inject
    HL7Sender hl7sender;

    @Override
    public byte[] onMessage(HL7Application hl7App, Connection conn, Socket s, UnparsedHL7Message msg)
            throws HL7Exception {
        ArchiveHL7ApplicationExtension arcHl7App =
                hl7App.getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class);
        log(msg, arcHl7App.hl7LogFilePattern());
        forwardHL7(arcHl7App, s, msg);
        try {
            try {
                process(hl7App, s, msg);
            } catch (HL7Exception e) {
                throw e;
            } catch (Exception e) {
                new HL7Exception(HL7Exception.AE, e);
            }
        } catch (HL7Exception e) {
            log(msg, arcHl7App.hl7ErrorLogFilePattern());
            throw e;
        }
        return super.onMessage(hl7App, conn, s, msg);
    }

    private void forwardHL7(ArchiveHL7ApplicationExtension arcHL7App, Socket s, UnparsedHL7Message msg) {
        String host = s.getLocalAddress().getHostName();
        HL7Segment msh = msg.msh();
        byte[] hl7msg = msg.data();
        Collection<String> destinations = arcHL7App.forwardDestinations(host, msh);
        if (!destinations.isEmpty())
            hl7sender.forwardMessage(msh, hl7msg,
                    destinations.toArray(new String[destinations.size()]));
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
                out.close();
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
            if (s1.substring(0,4).equalsIgnoreCase("date")) {
                try {
                    Date date = new SimpleDateFormat("yyyyMMdd").parse(msh.getField(6, null).substring(0,8));
                    dateFormat = new SimpleDateFormat(s1.substring(s1.indexOf(",")+1)).format(date);
                } catch (Exception e) {
                    LOG.warn("Failed to format date : ", e);
                }
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

    protected abstract void process(HL7Application hl7App, Socket s, UnparsedHL7Message msg) throws Exception;
}
