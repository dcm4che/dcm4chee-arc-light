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
import java.util.*;

import static org.junit.Assert.assertArrayEquals;
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

    enum ConfigType {
        INIT,
        SAMPLE,
        TEST
    }

    @Test
    public void testPersist() throws Exception {
        ConfigType configType = ConfigType.valueOf(System.getProperty("configType", ConfigType.INIT.toString()));
        if (configType == ConfigType.SAMPLE) {
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

        Device arc = factory.createArchiveDevice("dcm4chee-arc", arrDevice, ConfigType.SAMPLE);
        config.persist(arc);
        ApplicationEntity ae = config.findApplicationEntity("DCM4CHEE");
        assertNotNull(ae);
        assertDeviceEquals(arc, ae.getDevice());
    }

    @Test
    public void testJsonPersist() throws Exception {
        Device arrDevice = factory.createARRDevice("syslog", Connection.Protocol.SYSLOG_UDP, 514);
        Device arc = factory.createArchiveDevice("dcm4chee-arc", arrDevice, ConfigType.TEST);
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
            if (!name.equals("syslog"))
                throw new ConfigurationNotFoundException("Unknown Device: " + name);
            return factory.createARRDevice("syslog", Connection.Protocol.SYSLOG_UDP, 514);
        }
    };

    private void assertDeviceEquals(Device expected, Device actual) {
        assertEquals(expected.getDeviceName(), actual.getDeviceName());
        assertEqualsArchiveDeviceExtension(expected.getDeviceExtension(ArchiveDeviceExtension.class),
                actual.getDeviceExtension(ArchiveDeviceExtension.class));
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
        assertEquals(expected.getOverwritePolicy(), actual.getOverwritePolicy());
        assertEquals(expected.getQueryRetrieveViewID(), actual.getQueryRetrieveViewID());
        assertEquals(expected.getBulkDataSpoolDirectory(), actual.getBulkDataSpoolDirectory());
        assertEquals(expected.getQueryMatchUnknown(), actual.getQueryMatchUnknown());
        assertEquals(expected.getPersonNameComponentOrderInsensitiveMatching(), actual.getPersonNameComponentOrderInsensitiveMatching());
        assertEquals(expected.getSendPendingCGet(), actual.getSendPendingCGet());
        assertEquals(expected.getSendPendingCMoveInterval(), actual.getSendPendingCMoveInterval());
        assertEquals(expected.getWadoSR2HtmlTemplateURI(), actual.getWadoSR2HtmlTemplateURI());
        assertEquals(expected.getWadoSR2TextTemplateURI(), actual.getWadoSR2TextTemplateURI());
        assertEquals(expected.getQidoMaxNumberOfResults(), actual.getQidoMaxNumberOfResults());
        assertArrayEquals(expected.getMppsForwardDestinations(), actual.getMppsForwardDestinations());
        assertEquals(expected.getFallbackCMoveSCP(), actual.getFallbackCMoveSCP());
        assertEquals(expected.getFallbackCMoveSCPDestination(), actual.getFallbackCMoveSCPDestination());
        assertEquals(expected.getFallbackCMoveSCPLevel(), actual.getFallbackCMoveSCPLevel());
        assertEquals(expected.getAlternativeCMoveSCP(), actual.getAlternativeCMoveSCP());
    }

    private void assertEqualsArchiveDeviceExtension(ArchiveDeviceExtension expected, ArchiveDeviceExtension actual) {
        assertNotNull(actual);
        assertEquals(expected.getStorageID(), actual.getStorageID());
        assertEquals(expected.getFuzzyAlgorithmClass(), actual.getFuzzyAlgorithmClass());
        assertEquals(expected.getOverwritePolicy(), actual.getOverwritePolicy());
        assertEquals(expected.getQueryRetrieveViewID(), actual.getQueryRetrieveViewID());
        assertEquals(expected.getBulkDataSpoolDirectory(), actual.getBulkDataSpoolDirectory());
        assertEquals(expected.isQueryMatchUnknown(), actual.isQueryMatchUnknown());
        assertEquals(expected.isPersonNameComponentOrderInsensitiveMatching(), actual.isPersonNameComponentOrderInsensitiveMatching());
        assertEquals(expected.isSendPendingCGet(), actual.isSendPendingCGet());
        assertEquals(expected.getSendPendingCMoveInterval(), actual.getSendPendingCMoveInterval());
        assertArrayEquals(expected.getWadoSupportedSRClasses(), actual.getWadoSupportedSRClasses());
        assertEquals(expected.getWadoSR2HtmlTemplateURI(), actual.getWadoSR2HtmlTemplateURI());
        assertEquals(expected.getWadoSR2TextTemplateURI(), actual.getWadoSR2TextTemplateURI());
        assertEquals(expected.getQidoMaxNumberOfResults(), actual.getQidoMaxNumberOfResults());
        assertArrayEquals(expected.getMppsForwardDestinations(), actual.getMppsForwardDestinations());
        assertEquals(expected.getFallbackCMoveSCP(), actual.getFallbackCMoveSCP());
        assertEquals(expected.getFallbackCMoveSCPDestination(), actual.getFallbackCMoveSCPDestination());
        assertEquals(expected.getFallbackCMoveSCPLevel(), actual.getFallbackCMoveSCPLevel());
        assertEquals(expected.getAlternativeCMoveSCP(), actual.getAlternativeCMoveSCP());
        assertEquals(expected.getExportTaskPollingInterval(), actual.getExportTaskPollingInterval());
        assertEquals(expected.getExportTaskFetchSize(), actual.getExportTaskFetchSize());
        assertEquals(expected.getPurgeStoragePollingInterval(), actual.getPurgeStoragePollingInterval());
        assertEquals(expected.getPurgeStorageFetchSize(), actual.getPurgeStorageFetchSize());
        assertEquals(expected.getDeleteStudyBatchSize(), actual.getDeleteStudyBatchSize());
        assertEquals(expected.isDeletePatientOnDeleteLastStudy(), actual.isDeletePatientOnDeleteLastStudy());
        assertEquals(expected.getDeleteRejectedPollingInterval(), actual.getDeleteRejectedPollingInterval());
        assertEquals(expected.getDeleteRejectedFetchSize(), actual.getDeleteRejectedFetchSize());
        assertEquals(expected.getMaxAccessTimeStaleness(), actual.getMaxAccessTimeStaleness());
        assertEquals(expected.getPatientUpdateTemplateURI(), actual.getPatientUpdateTemplateURI());
        assertEquals(expected.getUnzipVendorDataToURI(), actual.getUnzipVendorDataToURI());
        assertChildren(expected, actual);
        assertExportRule(expected, actual);
        assertArchiveCompressionRule(expected, actual);
        assertAttributeCoercion(expected, actual);
    }

    private String[] toStrings(Map<String, ?> props) {
        String[] ss = new String[props.size()];
        int i = 0;
        for (Map.Entry<String, ?> entry : props.entrySet())
            ss[i++] = entry.getKey() + '=' + entry.getValue();
        return ss;
    }

    private void assertChildren(ArchiveDeviceExtension expected, ArchiveDeviceExtension actual) {
        for (Entity entity : Entity.values()) {
            AttributeFilter expectedAF = expected.getAttributeFilter(entity);
            AttributeFilter actualAF = actual.getAttributeFilter(entity);
            assertArrayEquals(expectedAF.getSelection(), actualAF.getSelection());
            assertEquals(expectedAF.getCustomAttribute1().toString(), actualAF.getCustomAttribute1().toString());
            assertEquals(expectedAF.getCustomAttribute2().toString(), actualAF.getCustomAttribute2().toString());
            assertEquals(expectedAF.getCustomAttribute3().toString(), actualAF.getCustomAttribute3().toString());
        }
        for (StorageDescriptor sd : expected.getStorageDescriptors()) {
            StorageDescriptor expectedSD = expected.getStorageDescriptor(sd.getStorageID());
            StorageDescriptor actualSD = actual.getStorageDescriptor(sd.getStorageID());
            assertEquals(expectedSD.getStorageID(), actualSD.getStorageID());
            assertEquals(expectedSD.getStorageURI(), actualSD.getStorageURI());
            assertEquals(expectedSD.getDigestAlgorithm(), actualSD.getDigestAlgorithm());
            assertArrayEquals(expectedSD.getRetrieveAETitles(), actualSD.getRetrieveAETitles());
            assertEquals(expectedSD.getInstanceAvailability(), actualSD.getInstanceAvailability());
            assertArrayEquals(expectedSD.getDeleterThresholdsAsStrings(), actualSD.getDeleterThresholdsAsStrings());
            assertArrayEquals(toStrings(expectedSD.getProperties()), toStrings(actualSD.getProperties()));
        }

        for (QueryRetrieveView qrv : actual.getQueryRetrieveViews()) {
            QueryRetrieveView expectedQRV = expected.getQueryRetrieveView(qrv.getViewID());
            QueryRetrieveView actualQRV = actual.getQueryRetrieveView(qrv.getViewID());
            assertEquals(expectedQRV.getViewID(), actualQRV.getViewID());
            assertArrayEquals(expectedQRV.getShowInstancesRejectedByCodes(), actualQRV.getShowInstancesRejectedByCodes());
            assertArrayEquals(expectedQRV.getHideRejectionNotesWithCodes(), actualQRV.getHideRejectionNotesWithCodes());
            assertEquals(expectedQRV.isHideNotRejectedInstances(), actualQRV.isHideNotRejectedInstances());
        }

        for (QueueDescriptor qd : expected.getQueueDescriptors()) {
            QueueDescriptor expectedQD = expected.getQueueDescriptor(qd.getQueueName());
            QueueDescriptor actualQD = actual.getQueueDescriptor(qd.getQueueName());
            assertEquals(expectedQD.getQueueName(), actualQD.getQueueName());
            assertEquals(expectedQD.getJndiName(), actualQD.getJndiName());
            assertEquals(expectedQD.getDescription(), actualQD.getDescription());
            assertEquals(expectedQD.getMaxRetries(), actualQD.getMaxRetries());
            assertEquals(expectedQD.getRetryDelay(), actualQD.getRetryDelay());
            assertEquals(expectedQD.getMaxRetryDelay(), actualQD.getMaxRetryDelay());
            assertEquals(expectedQD.getRetryDelayMultiplier(), actualQD.getRetryDelayMultiplier());
        }

        for (ExporterDescriptor ed : expected.getExporterDescriptors()) {
            ExporterDescriptor expectedED = expected.getExporterDescriptor(ed.getExporterID());
            ExporterDescriptor actualED = actual.getExporterDescriptor(ed.getExporterID());
            assertEquals(expectedED.getExporterID(), actualED.getExporterID());
            assertEquals(expectedED.getExportURI(), actualED.getExportURI());
            assertEquals(expectedED.getQueueName(), actualED.getQueueName());
            assertEquals(expectedED.getAETitle(), actualED.getAETitle());
            assertArrayEquals(expectedED.getSchedules(), actualED.getSchedules());
            assertArrayEquals(toStrings(expectedED.getProperties()), toStrings(actualED.getProperties()));
        }
        
        for (RejectionNote rn : expected.getRejectionNotes()) {
            RejectionNote expectedRN = expected.getRejectionNote(rn.getRejectionNoteLabel());
            RejectionNote actualRN = actual.getRejectionNote(rn.getRejectionNoteLabel());
            assertEquals(expectedRN.getRejectionNoteCode(), actualRN.getRejectionNoteCode());
            assertEquals(expectedRN.getRejectionNoteLabel(), actualRN.getRejectionNoteLabel());
            assertEquals(expectedRN.isRevokeRejection(), actualRN.isRevokeRejection());
            assertEquals(expectedRN.getAcceptPreviousRejectedInstance(), actualRN.getAcceptPreviousRejectedInstance());
            assertArrayEquals(expectedRN.getOverwritePreviousRejection(), actualRN.getOverwritePreviousRejection());
            assertEquals(expectedRN.getDeleteRejectedInstanceDelay(), actualRN.getDeleteRejectedInstanceDelay());
            assertEquals(expectedRN.getDeleteRejectionNoteDelay(), actualRN.getDeleteRejectionNoteDelay());
        }
    }

    private void assertExportRule(ArchiveDeviceExtension expected, ArchiveDeviceExtension actual) {
        List<ExportRule> expectedERList = new ArrayList<>(expected.getExportRules());
        List<ExportRule> actualERList = new ArrayList<>(actual.getExportRules());
        Comparator<ExportRule> exportRule = new Comparator<ExportRule>() {
            @Override
            public int compare(ExportRule expectedER, ExportRule actualER) {
                return expectedER.hashCode() - actualER.hashCode();
            }
        };
        Collections.sort(expectedERList, exportRule);
        Collections.sort(actualERList, exportRule);
        for (int i = 0; i < expectedERList.size(); i++) {
            ExportRule expectedER = expectedERList.get(i);
            ExportRule actualER = actualERList.get(i);
            assertEquals(expectedER.getCommonName(), actualER.getCommonName());
            assertEquals(expectedER.getEntity(), actualER.getEntity());
            assertArrayEquals(expectedER.getExporterIDs(), actualER.getExporterIDs());
            assertEquals(expectedER.getConditions().toString(), actualER.getConditions().toString());
            assertArrayEquals(expectedER.getSchedules(), actualER.getSchedules());
            assertEquals(expectedER.getExportDelay(), actualER.getExportDelay());
        }
    }

    private void assertArchiveCompressionRule(ArchiveDeviceExtension expected, ArchiveDeviceExtension actual) {
        List<ArchiveCompressionRule> expectedACRList = new ArrayList<>(expected.getCompressionRules());
        List<ArchiveCompressionRule> actualACRList = new ArrayList<>(actual.getCompressionRules());
        Comparator<ArchiveCompressionRule> archiveCompressionRule = new Comparator<ArchiveCompressionRule>() {
            @Override
            public int compare(ArchiveCompressionRule expectedACR, ArchiveCompressionRule actualACR) {
                return expectedACR.getPriority() - actualACR.getPriority();
            }
        };
        Collections.sort(expectedACRList, archiveCompressionRule);
        Collections.sort(actualACRList, archiveCompressionRule);
        for (int i = 0; i < expectedACRList.size(); i++) {
            ArchiveCompressionRule expectedACR = expectedACRList.get(i);
            ArchiveCompressionRule actualACR = actualACRList.get(i);
            assertEquals(expectedACR.getCommonName(), actualACR.getCommonName());
            assertEquals(expectedACR.getTransferSyntax(), actualACR.getTransferSyntax());
            assertEquals(expectedACR.getPriority(), actualACR.getPriority());
            assertEquals(expectedACR.getConditions().toString(), actualACR.getConditions().toString());
            assertArrayEquals(expectedACR.getImageWriteParams(), actualACR.getImageWriteParams());
        }
    }

    private void assertAttributeCoercion(ArchiveDeviceExtension expected, ArchiveDeviceExtension actual) {
        List<ArchiveAttributeCoercion> expectedAACList = new ArrayList<>(expected.getAttributeCoercions());
        List<ArchiveAttributeCoercion> actualAACList = new ArrayList<>(actual.getAttributeCoercions());
        Comparator<ArchiveAttributeCoercion> archiveAttributeCoercion = new Comparator<ArchiveAttributeCoercion>() {
            @Override
            public int compare(ArchiveAttributeCoercion expectedAAC, ArchiveAttributeCoercion actualAAC) {
                return expectedAAC.hashCode() - actualAAC.hashCode();
            }
        };
        Collections.sort(expectedAACList, archiveAttributeCoercion);
        Collections.sort(actualAACList, archiveAttributeCoercion);
        for (int i = 0; i < expectedAACList.size(); i++) {
            ArchiveAttributeCoercion expectedAAC = expectedAACList.get(i);
            ArchiveAttributeCoercion actualAAC = actualAACList.get(i);
            assertEquals(expectedAAC.getCommonName(), actualAAC.getCommonName());
            assertEquals(expectedAAC.getXSLTStylesheetURI(), actualAAC.getXSLTStylesheetURI());
            assertEquals(expectedAAC.getPriority(), actualAAC.getPriority());
            assertArrayEquals(expectedAAC.getAETitles(), actualAAC.getAETitles());
            assertArrayEquals(expectedAAC.getHostNames(), actualAAC.getHostNames());
            assertArrayEquals(expectedAAC.getSOPClasses(), actualAAC.getSOPClasses());
            assertEquals(expectedAAC.isNoKeywords(), actualAAC.isNoKeywords());
        }
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
