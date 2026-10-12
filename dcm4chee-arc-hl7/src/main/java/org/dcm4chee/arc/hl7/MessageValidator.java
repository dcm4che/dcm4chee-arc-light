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

package org.dcm4chee.arc.hl7;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.hl7.IHL7ApplicationCache;
import org.dcm4che3.hl7.ERRSegment;
import org.dcm4che3.hl7.HL7Exception;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.ArchiveHL7ApplicationExtension;
import org.slf4j.Logger;
import java.net.InetAddress;
import java.net.Socket;

/**
 * @author Gunter Zeilinger <gunterze@protonmail.com>
 * @since Oct 2026
 */
class MessageValidator {
    static void validate(HL7Application hl7App, Connection conn, Socket s, UnparsedHL7Message msg,
                         IHL7ApplicationCache hl7AppCache, Logger LOG) throws HL7Exception {
        ArchiveHL7ApplicationExtension arcHl7App = hl7App.getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class);
        if (arcHl7App != null && arcHl7App.isValidateSendingApplicationHostname()) {
            HL7Segment msh = msg.msh();
            String appName = msh.getSendingApplicationWithFacility();
            try {
                if (!validateApplicationHostname(s.getInetAddress(), appName, hl7AppCache, LOG)) {
                    throw new HL7Exception(
                            new ERRSegment(msh)
                                    .setHL7ErrorCode(ERRSegment.TABLE_VALUE_NOT_FOUND)
                                    .setErrorLocation(ERRSegment.SENDING_APPLICATION)
                                    .setUserMessage("Sending Application and/or Facility not recognized"));
                }
            } catch (ConfigurationException e) {
                LOG.warn("Failed to lookup configuration for HL7 Application {}:\n", appName, e);
                throw new HL7Exception(new ERRSegment(msh)
                        .setHL7ErrorCode(ERRSegment.APPLICATION_INTERNAL_ERROR)
                        .setUserMessage("Failed to lookup configuration for HL7 Application"));
            }
        }
    }

    private static boolean validateApplicationHostname(InetAddress inetAddress, String appName,
                                                       IHL7ApplicationCache hl7AppCache, Logger LOG)
            throws ConfigurationException {
        HL7Application hl7App = hl7AppCache.get(appName);
        if (hl7App == null) {
            LOG.info("HL7 Application {} not configured", appName);
            return false;
        }
        if (!containsHostname(hl7App, inetAddress)) {
            LOG.info("Host {} of HL7 Application {} does not match configuration", inetAddress, appName);
            return false;
        }
        return true;
    }

    private static boolean containsHostname(HL7Application remoteHl7App, InetAddress inetAddress) {
        for (Connection c : remoteHl7App.getConnections())
            if (equalsHost(c.getHostname(), inetAddress))
                return true;

            return false;
    }

    private static boolean equalsHost(String configuredHostname, InetAddress inetAddress) {
        if (StringUtils.isIPAddr(configuredHostname))
            return configuredHostname.equals(inetAddress.getHostAddress());

        String canonicalHostName = inetAddress.getCanonicalHostName();
        return configuredHostname.equalsIgnoreCase(
                configuredHostname.indexOf('.') < 0
                        ? StringUtils.split(canonicalHostName, '.')[0]
                        : canonicalHostName);
    }
}
