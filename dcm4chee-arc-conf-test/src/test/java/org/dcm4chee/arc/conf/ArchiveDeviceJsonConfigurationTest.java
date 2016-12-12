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

package org.dcm4chee.arc.conf;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.ConfigurationNotFoundException;
import org.dcm4che3.conf.api.DicomConfiguration;
import org.dcm4che3.conf.api.hl7.HL7Configuration;
import org.dcm4che3.conf.json.ConfigurationDelegate;
import org.dcm4che3.conf.json.JsonConfiguration;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.SSLManagerFactory;
import org.dcm4che3.util.ResourceLocator;
import org.dcm4chee.arc.conf.json.JsonConfigurationProducer;
import org.dcm4chee.arc.conf.ldap.LdapArchiveConfigurationFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.json.Json;
import javax.json.stream.JsonGenerator;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;

import static org.dcm4chee.arc.conf.Assert.assertDeviceEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jan 2016
 */
public class ArchiveDeviceJsonConfigurationTest {

    @Test
    public void testJsonPersist() throws Exception {
        Device arrDevice = ArchiveDeviceFactory.createARRDevice("logstash", Connection.Protocol.SYSLOG_UDP, 514,
                ArchiveDeviceFactory.ConfigType.TEST);
        Device unknownDevice = ArchiveDeviceFactory.createUnknownDevice("unknown", "UNKNOWN", "localhost", 104);
        Device arc = ArchiveDeviceFactory.createArchiveDevice("dcm4chee-arc", arrDevice, unknownDevice,
                ArchiveDeviceFactory.ConfigType.TEST);
        JsonConfiguration jsonConfig = JsonConfigurationProducer.newJsonConfiguration();
        Path path = Paths.get("target/device.json");
        try ( BufferedWriter w = Files.newBufferedWriter(path, Charset.forName("UTF-8"));
              JsonGenerator gen = Json.createGenerator(w)) {
            jsonConfig.writeTo(arc, gen);
        }
        Device arc2;
        try (BufferedReader reader = Files.newBufferedReader(path, Charset.forName("UTF-8"))) {
            arc2 = jsonConfig.loadDeviceFrom(Json.createParser(reader), configDelegate);
        }
        assertDeviceEquals(arc, arc2);
    }

    private final ConfigurationDelegate configDelegate = new ConfigurationDelegate() {
        @Override
        public Device findDevice(String name) throws ConfigurationException {
            if (name.equals("logstash"))
                return ArchiveDeviceFactory.createARRDevice("logstash", Connection.Protocol.SYSLOG_UDP, 514,
                    ArchiveDeviceFactory.ConfigType.TEST);
            if (name.equals("unknown"))
                return ArchiveDeviceFactory.createUnknownDevice("unknown", "UNKNOWN", "localhost", 104);
            else
                throw new ConfigurationNotFoundException("Unknown Device: " + name);
        }
    };
}
