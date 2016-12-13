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

import org.dcm4che3.conf.api.ConfigurationNotFoundException;
import org.dcm4che3.conf.api.DicomConfiguration;
import org.dcm4che3.conf.api.hl7.HL7Configuration;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.SSLManagerFactory;
import org.dcm4che3.util.ResourceLocator;
import org.dcm4chee.arc.conf.ldap.LdapArchiveConfigurationFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.security.KeyStore;
import java.security.cert.X509Certificate;

import static org.dcm4chee.arc.conf.Assert.assertDeviceEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jul 2015
 */
public class ArchiveDeviceConfigurationTest {
    private KeyStore keyStore;
    private DicomConfiguration config;
    private HL7Configuration hl7Config;

    @Before
    public void setUp() throws Exception {
        keyStore = SSLManagerFactory.loadKeyStore("JKS", ResourceLocator.resourceURL("cacerts.jks"), "secret");
        config = LdapArchiveConfigurationFactory.newLdapDicomConfiguration(
                ArchiveDeviceConfigurationTest.class.getResource("/ldap.properties"));
        hl7Config = config.getDicomConfigurationExtension(HL7Configuration.class);
        cleanUp();
    }

    @After
    public void tearDown() throws Exception {
        config.close();
    }

    @Test
    public void testPersist() throws Exception {
        ArchiveDeviceFactory.ConfigType configType =
                ArchiveDeviceFactory.ConfigType.valueOf(
                        System.getProperty("configType", ArchiveDeviceFactory.ConfigType.DEFAULT.name()));
        if (configType == ArchiveDeviceFactory.ConfigType.SAMPLE) {
            for (int i = 0; i < ArchiveDeviceFactory.OTHER_AES.length; i++) {
                String aet = ArchiveDeviceFactory.OTHER_AES[i];
                config.registerAETitle(aet);
                config.persist(setThisNodeCertificates(
                        ArchiveDeviceFactory.createDevice(ArchiveDeviceFactory.OTHER_DEVICES[i], ArchiveDeviceFactory.DEVICE_TYPES[i],
                            ArchiveDeviceFactory.OTHER_ISSUER[i],
                            ArchiveDeviceFactory.OTHER_INST_CODES[i],
                            aet, "localhost",
                            ArchiveDeviceFactory.OTHER_PORTS[i << 1],
                            ArchiveDeviceFactory.OTHER_PORTS[(i << 1) + 1])));
            }
            hl7Config.registerHL7Application(ArchiveDeviceFactory.PIX_MANAGER);
            for (int i = ArchiveDeviceFactory.OTHER_AES.length; i < ArchiveDeviceFactory.OTHER_DEVICES.length; i++)
                config.persist(setThisNodeCertificates(
                        ArchiveDeviceFactory.createDevice(ArchiveDeviceFactory.OTHER_DEVICES[i], configType)));
            config.persist(setThisNodeCertificates(
                    ArchiveDeviceFactory.createHL7Device("hl7rcv",
                        ArchiveDeviceFactory.SITE_A,
                        ArchiveDeviceFactory.INST_A,
                        ArchiveDeviceFactory.PIX_MANAGER,
                        "localhost", 2576, 12576)));
        }
        Device arrDevice = ArchiveDeviceFactory.createARRDevice("logstash", Connection.Protocol.SYSLOG_UDP, 514, configType);
        Device unknown = ArchiveDeviceFactory.createUnknownDevice("unknown", "UNKNOWN", "localhost", 104);
        config.persist(arrDevice);
        config.persist(unknown);
        config.registerAETitle("DCM4CHEE");
        config.registerAETitle("DCM4CHEE_ADMIN");
        config.registerAETitle("DCM4CHEE_TRASH");
        config.registerAETitle("UNKNOWN");

        Device arc = setThisNodeCertificates(
                ArchiveDeviceFactory.createArchiveDevice("dcm4chee-arc", arrDevice, unknown, configType));
        if (configType == ArchiveDeviceFactory.ConfigType.SAMPLE)
            setAuthorizedNodeCertificates(arc);
        config.persist(arc);

        Device keycloak = ArchiveDeviceFactory.createKeycloakDevice("keycloak", arrDevice, configType);
        config.persist(keycloak);
        ApplicationEntity ae = config.findApplicationEntity("DCM4CHEE");
        assertNotNull(ae);
        assertDeviceEquals(arc, ae.getDevice());
    }

    private Device setThisNodeCertificates(Device device) throws Exception {
        String name = device.getDeviceName();
        device.setThisNodeCertificates(config.deviceRef(name), (X509Certificate) keyStore.getCertificate(name));
        return device;
    }

    private Device setAuthorizedNodeCertificates(Device device) throws Exception {
        for (String other : ArchiveDeviceFactory.OTHER_DEVICES)
            device.setAuthorizedNodeCertificates(config.deviceRef(other),
                    (X509Certificate) keyStore.getCertificate(other));
        return device;
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
            config.removeDevice("logstash");
        } catch (ConfigurationNotFoundException e) {}
        try {
            config.removeDevice("keycloak");
        } catch (ConfigurationNotFoundException e) {}
        try {
            config.removeDevice("unknown");
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
