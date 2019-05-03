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

import java.util.EnumSet;

import static org.dcm4chee.arc.conf.Assert.assertDeviceEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jul 2015
 */
public class ArchiveDeviceConfigurationTest {
    private DicomConfiguration config;
    private HL7Configuration hl7Config;

    @Before
    public void setUp() throws Exception {
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
        Device arrDevice = ArchiveDeviceFactory.createARRDevice("logstash", Connection.Protocol.SYSLOG_UDP, 514, configType);
        Device[] otherDevices = new Device[ArchiveDeviceFactory.OTHER_DEVICES.length];
        EnumSet<DicomConfiguration.Option> register = EnumSet.of(DicomConfiguration.Option.REGISTER);
        config.persist(arrDevice, register);
        config.persist(otherDevices[0] = ArchiveDeviceFactory.createOtherDevice(0), register);
        if (configType == ArchiveDeviceFactory.ConfigType.SAMPLE) {
            for (int i = 1; i < ArchiveDeviceFactory.OTHER_DEVICES.length; i++) {
                config.persist(otherDevices[i] = ArchiveDeviceFactory.createOtherDevice(i), register);
            }
            config.persist(
                    ArchiveDeviceFactory.qualifyDevice(
                            ArchiveDeviceFactory.createHL7Device(
                                    "hl7rcv",
                                    ArchiveDeviceFactory.PIX_MANAGER,
                                    "localhost",
                                    2576,
                                    12576),
                            "DSS",
                            ArchiveDeviceFactory.SITE_A,
                            ArchiveDeviceFactory.INST_A),
                    register);
        }

        Device arc = ArchiveDeviceFactory.createArchiveDevice("dcm4chee-arc", configType,
                arrDevice,
                otherDevices[ArchiveDeviceFactory.SCHEDULED_STATION_INDEX],
                otherDevices[ArchiveDeviceFactory.STORESCU_INDEX],
                otherDevices[ArchiveDeviceFactory.MPPSSCU_INDEX]
        );
        config.persist(arc, register);

        Device keycloak = ArchiveDeviceFactory.createKeycloakDevice("keycloak", arrDevice, configType);
        config.persist(keycloak, null);
        ApplicationEntity ae = config.findApplicationEntity("DCM4CHEE");
        assertNotNull(ae);
        assertDeviceEquals(arc, ae.getDevice());
    }

    private void cleanUp() throws Exception {
        config.unregisterAETitle("DCM4CHEE");
        config.unregisterAETitle("IOCM_REGULAR_USE");
        config.unregisterAETitle("IOCM_EXPIRED");
        config.unregisterAETitle("IOCM_QUALITY");
        config.unregisterAETitle("IOCM_PAT_SAFETY");
        config.unregisterAETitle("IOCM_WRONG_MWL");
        config.unregisterAETitle("AS_RECEIVED");
        config.unregisterAETitle("SCHEDULEDSTATION");
        hl7Config.unregisterHL7Application("HL7RCV|DCM4CHEE");
        config.unregisterWebAppName("DCM4CHEE");
        config.unregisterWebAppName("DCM4CHEE-WADO");
        config.unregisterWebAppName("IOCM_REGULAR_USE");
        config.unregisterWebAppName("IOCM_REGULAR_USE-WADO");
        config.unregisterWebAppName("IOCM_EXPIRED");
        config.unregisterWebAppName("IOCM_EXPIRED-WADO");
        config.unregisterWebAppName("IOCM_QUALITY");
        config.unregisterWebAppName("IOCM_QUALITY-WADO");
        config.unregisterWebAppName("IOCM_PAT_SAFETY");
        config.unregisterWebAppName("IOCM_PAT_SAFETY-WADO");
        config.unregisterWebAppName("IOCM_WRONG_MWL");
        config.unregisterWebAppName("IOCM_WRONG_MWL-WADO");
        config.unregisterWebAppName("AS_RECEIVED");
        config.unregisterWebAppName("AS_RECEIVED-WADO");
        config.unregisterWebAppName("dcm4chee-arc");
        for (String aet : ArchiveDeviceFactory.OTHER_AES)
            config.unregisterAETitle(aet);
        try {
            config.removeDevice("dcm4chee-arc", null);
        } catch (ConfigurationNotFoundException e) {}
        try {
            config.removeDevice("logstash", null);
        } catch (ConfigurationNotFoundException e) {}
        try {
            config.removeDevice("keycloak", null);
        } catch (ConfigurationNotFoundException e) {}
        try {
            config.removeDevice("scheduledstation", null);
        } catch (ConfigurationNotFoundException e) {}
        try {
            config.removeDevice("hl7rcv", null);
        } catch (ConfigurationNotFoundException e) {}
        for (String name : ArchiveDeviceFactory.OTHER_DEVICES)
            try {
                config.removeDevice(name, null);
            }  catch (ConfigurationNotFoundException e) {}
    }
}
