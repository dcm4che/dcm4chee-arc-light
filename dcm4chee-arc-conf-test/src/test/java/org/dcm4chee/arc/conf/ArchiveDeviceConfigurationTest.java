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
 * **** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.conf;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.ConfigurationNotFoundException;
import org.dcm4che3.conf.api.DicomConfiguration;
import org.dcm4che3.conf.api.hl7.HL7Configuration;
import org.dcm4che3.conf.json.ConfigurationDelegate;
import org.dcm4che3.conf.json.JsonConfiguration;
import org.dcm4che3.conf.json.audit.JsonAuditLoggerConfiguration;
import org.dcm4che3.conf.json.audit.JsonAuditRecordRepositoryConfiguration;
import org.dcm4che3.conf.json.hl7.JsonHL7Configuration;
import org.dcm4che3.conf.json.imageio.JsonImageReaderConfiguration;
import org.dcm4che3.conf.json.imageio.JsonImageWriterConfiguration;
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
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2015
 */
public class ArchiveDeviceConfigurationTest {
    private KeyStore keyStore;
    private DicomConfiguration config;
    private ArchiveDeviceFactory factory;
    private HL7Configuration hl7Config;

    @Before
    public void setUp() throws Exception {
        keyStore = SSLManagerFactory.loadKeyStore("JKS", ResourceLocator.resourceURL("cacerts.jks"), "secret");
        config = LdapArchiveConfigurationFactory.newLdapDicomConfiguration(
                ArchiveDeviceConfigurationTest.class.getResource("/ldap.properties"));
        hl7Config = config.getDicomConfigurationExtension(HL7Configuration.class);
        factory = new ArchiveDeviceFactory(keyStore, config);
        cleanUp();
    }

    @After
    public void tearDown() throws Exception {
        config.close();
    }

    @Test
    public void testPersist() throws Exception {
        boolean sampleConfig = Boolean.getBoolean("sampleConfig");
        if (sampleConfig) {
            for (int i = 0; i < ArchiveDeviceFactory.OTHER_AES.length; i++) {
                String aet = ArchiveDeviceFactory.OTHER_AES[i];
                config.registerAETitle(aet);
                config.persist(factory.createDevice(ArchiveDeviceFactory.OTHER_DEVICES[i],
                        ArchiveDeviceFactory.OTHER_ISSUER[i],
                        ArchiveDeviceFactory.OTHER_INST_CODES[i],
                        aet, "localhost",
                        ArchiveDeviceFactory.OTHER_PORTS[i << 1],
                        ArchiveDeviceFactory.OTHER_PORTS[(i << 1) + 1]));
            }
            hl7Config.registerHL7Application(ArchiveDeviceFactory.PIX_MANAGER);
            for (int i = ArchiveDeviceFactory.OTHER_AES.length; i < ArchiveDeviceFactory.OTHER_DEVICES.length; i++)
                config.persist(factory.createDevice(ArchiveDeviceFactory.OTHER_DEVICES[i]));
            config.persist(factory.createHL7Device("hl7rcv",
                    ArchiveDeviceFactory.SITE_A,
                    ArchiveDeviceFactory.INST_A,
                    ArchiveDeviceFactory.PIX_MANAGER,
                    "localhost", 2576, 12576));
        }
        Device arrDevice = factory.createARRDevice("syslog", Connection.Protocol.SYSLOG_UDP, 514);
        config.persist(arrDevice);
        config.registerAETitle("DCM4CHEE");

        Device arc = factory.createArchiveDevice("dcm4chee-arc", arrDevice, sampleConfig);
        config.persist(arc);
        ApplicationEntity ae = config.findApplicationEntity("DCM4CHEE");
        assertNotNull(ae);
        assertDeviceEquals(arc, ae.getDevice());
    }

    @Test
    public void testJsonPersist() throws Exception {
        Device arrDevice = factory.createARRDevice("syslog", Connection.Protocol.SYSLOG_UDP, 514);
        Device arc = factory.createArchiveDevice("dcm4chee-arc", arrDevice, false);
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
//        assertDeviceEquals(arc, arc2);
    }

    private final ConfigurationDelegate configDelegate = new ConfigurationDelegate() {
        @Override
        public Device findDevice(String name) throws ConfigurationException {
            if (!name.equals("syslog"))
                throw new ConfigurationNotFoundException("Unknown Device: " + name);
            return factory.createARRDevice("syslog", Connection.Protocol.SYSLOG_UDP, 514);
        }
    };

    private void assertDeviceEquals(Device expected, Device actual) {
        assertEquals(expected.getDeviceName(), actual.getDeviceName());
        assertEqualsArchiveDeviceExtension(expected.getDeviceExtension(ArchiveDeviceExtension.class),
                expected.getDeviceExtension(ArchiveDeviceExtension.class));
        assertAEEquals(expected.getApplicationEntity("DCM4CHEE"), actual.getApplicationEntity("DCM4CHEE"));
    }

    private void assertAEEquals(ApplicationEntity expected, ApplicationEntity actual) {
        assertEqualsArchiveAEExtension(expected.getAEExtension(ArchiveAEExtension.class),
                actual.getAEExtension(ArchiveAEExtension.class));
    }

    private void assertEqualsArchiveAEExtension(ArchiveAEExtension expected, ArchiveAEExtension actual) {
        if (expected == null)
            return;
        assertNotNull(actual);
        assertEquals(expected.getStorageID(), actual.getStorageID());
    }

    private void assertEqualsArchiveDeviceExtension(ArchiveDeviceExtension expected, ArchiveDeviceExtension actual) {
        assertNotNull(actual);
        assertEquals(expected.getStorageDescriptor(ArchiveDeviceFactory.STORAGE_ID),
                actual.getStorageDescriptor(ArchiveDeviceFactory.STORAGE_ID));
        assertEquals(expected.isSendPendingCGet(), actual.isQueryMatchUnknown());
        assertEquals(expected.getSendPendingCMoveInterval(), actual.getSendPendingCMoveInterval());
    }

    private void cleanUp() throws Exception {
        config.unregisterAETitle("DCM4CHEE");
        config.unregisterAETitle("DCM4CHEE_ADMIN");
        config.unregisterAETitle("DCM4CHEE_TRASH");
        for (String aet : ArchiveDeviceFactory.OTHER_AES)
            config.unregisterAETitle(aet);
        hl7Config.unregisterHL7Application(ArchiveDeviceFactory.PIX_MANAGER);
        try {
            config.removeDevice("dcm4chee-arc");
        } catch (ConfigurationNotFoundException e) {}
        try {
            config.removeDevice("syslog");
        } catch (ConfigurationNotFoundException e) {}
        try {
            config.removeDevice("hl7rcv");
        } catch (ConfigurationNotFoundException e) {}
        for (String name : ArchiveDeviceFactory.OTHER_DEVICES)
            try {
                config.removeDevice(name);
            }  catch (ConfigurationNotFoundException e) {}
    }
}
