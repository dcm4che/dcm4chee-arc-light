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

import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;

import java.util.*;

import static org.junit.Assert.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jan 2016
 */
class Assert {
    static void assertDeviceEquals(Device expected, Device actual) {
        assertEquals(expected.getDeviceName(), actual.getDeviceName());
        assertEqualsArchiveDeviceExtension(expected.getDeviceExtension(ArchiveDeviceExtension.class),
                actual.getDeviceExtension(ArchiveDeviceExtension.class));
        assertAEEquals(expected.getApplicationEntity("DCM4CHEE"), actual.getApplicationEntity("DCM4CHEE"));
    }

    static void assertAEEquals(ApplicationEntity expected, ApplicationEntity actual) {
        assertEqualsArchiveAEExtension(expected.getAEExtension(ArchiveAEExtension.class),
                actual.getAEExtension(ArchiveAEExtension.class));
    }

    static void assertEqualsArchiveAEExtension(ArchiveAEExtension expected, ArchiveAEExtension actual) {
        if (expected == null)
            return;
        assertNotNull(actual);
        assertEquals(expected.getStorageID(), actual.getStorageID());
        assertEquals(expected.getStoreAccessControlID(), actual.getStoreAccessControlID());
//        assertArrayEquals(expected.getAccessControlIDs(), actual.getAccessControlIDs()); //assert failing here
        assertEquals(expected.getOverwritePolicy(), actual.getOverwritePolicy());
        assertEquals(expected.getQueryRetrieveViewID(), actual.getQueryRetrieveViewID());
        assertEquals(expected.getBulkDataSpoolDirectory(), actual.getBulkDataSpoolDirectory());
        assertEquals(expected.getPersonNameComponentOrderInsensitiveMatching(), actual.getPersonNameComponentOrderInsensitiveMatching());
        assertEquals(expected.getSendPendingCGet(), actual.getSendPendingCGet());
        assertEquals(expected.getSendPendingCMoveInterval(), actual.getSendPendingCMoveInterval());
        assertEquals(expected.getWadoSR2HtmlTemplateURI(), actual.getWadoSR2HtmlTemplateURI());
        assertEquals(expected.getWadoSR2TextTemplateURI(), actual.getWadoSR2TextTemplateURI());
        assertEquals(expected.getQidoMaxNumberOfResults(), actual.getQidoMaxNumberOfResults());
        assertArrayEquals(expected.getMppsForwardDestinations(), actual.getMppsForwardDestinations());
        assertEquals(expected.getFallbackCMoveSCP(), actual.getFallbackCMoveSCP());
        assertEquals(expected.getFallbackCMoveSCPDestination(), actual.getFallbackCMoveSCPDestination());
        assertEquals(expected.getAlternativeCMoveSCP(), actual.getAlternativeCMoveSCP());
        assertArrayEquals(expected.getRetrieveAETitles(), actual.getRetrieveAETitles());
    }

    static void assertEqualsArchiveDeviceExtension(ArchiveDeviceExtension expected, ArchiveDeviceExtension actual) {
        assertNotNull(actual);
        assertEquals(expected.getStorageID(), actual.getStorageID());
        assertEquals(expected.getFuzzyAlgorithmClass(), actual.getFuzzyAlgorithmClass());
        assertEquals(expected.getOverwritePolicy(), actual.getOverwritePolicy());
        assertEquals(expected.getQueryRetrieveViewID(), actual.getQueryRetrieveViewID());
        assertEquals(expected.getBulkDataSpoolDirectory(), actual.getBulkDataSpoolDirectory());
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
        assertArrayEquals(expected.getRetrieveAETitles(), actual.getRetrieveAETitles());
        assertChildren(expected, actual);
        assertExportRule(expected, actual);
        assertArchiveCompressionRule(expected, actual);
        assertAttributeCoercion(expected, actual);
    }

    static String[] toStrings(Map<String, ?> props) {
        String[] ss = new String[props.size()];
        int i = 0;
        for (Map.Entry<String, ?> entry : props.entrySet())
            ss[i++] = entry.getKey() + '=' + entry.getValue();
        return ss;
    }

    static void assertChildren(ArchiveDeviceExtension expected, ArchiveDeviceExtension actual) {
        for (Entity entity : Entity.values()) {
            AttributeFilter expectedAF = expected.getAttributeFilter(entity);
            AttributeFilter actualAF = actual.getAttributeFilter(entity);
            assertArrayEquals(expectedAF.getSelection(), actualAF.getSelection());
            assertEquals(expectedAF.getCustomAttribute1(), actualAF.getCustomAttribute1());
            assertEquals(expectedAF.getCustomAttribute2(), actualAF.getCustomAttribute2());
            assertEquals(expectedAF.getCustomAttribute3(), actualAF.getCustomAttribute3());
        }
        for (StorageDescriptor sd : expected.getStorageDescriptors()) {
            StorageDescriptor expectedSD = expected.getStorageDescriptor(sd.getStorageID());
            StorageDescriptor actualSD = actual.getStorageDescriptor(sd.getStorageID());
            assertEquals(expectedSD.getStorageID(), actualSD.getStorageID());
            assertEquals(expectedSD.getStorageURI(), actualSD.getStorageURI());
            assertEquals(expectedSD.getDigestAlgorithm(), actualSD.getDigestAlgorithm());
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

    static void assertExportRule(ArchiveDeviceExtension expected, ArchiveDeviceExtension actual) {
        List<ExportRule> expectedERList = new ArrayList<>(expected.getExportRules());
        List<ExportRule> actualERList = new ArrayList<>(actual.getExportRules());
        Comparator<ExportRule> exportRule = new Comparator<ExportRule>() {
            @Override
            public int compare(ExportRule expectedER, ExportRule actualER) {
                return expectedER.getCommonName().compareTo(actualER.getCommonName());
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

    static void assertArchiveCompressionRule(ArchiveDeviceExtension expected, ArchiveDeviceExtension actual) {
        List<ArchiveCompressionRule> expectedACRList = new ArrayList<>(expected.getCompressionRules());
        List<ArchiveCompressionRule> actualACRList = new ArrayList<>(actual.getCompressionRules());
        Comparator<ArchiveCompressionRule> archiveCompressionRule = new Comparator<ArchiveCompressionRule>() {
            @Override
            public int compare(ArchiveCompressionRule expectedACR, ArchiveCompressionRule actualACR) {
                return expectedACR.getCommonName().compareTo(actualACR.getCommonName());
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
            assertEquals(expectedACR.getConditions(), actualACR.getConditions());
            assertArrayEquals(expectedACR.getImageWriteParams(), actualACR.getImageWriteParams());
        }
    }

    static void assertAttributeCoercion(ArchiveDeviceExtension expected, ArchiveDeviceExtension actual) {
        List<ArchiveAttributeCoercion> expectedAACList = new ArrayList<>(expected.getAttributeCoercions());
        List<ArchiveAttributeCoercion> actualAACList = new ArrayList<>(actual.getAttributeCoercions());
        Comparator<ArchiveAttributeCoercion> archiveAttributeCoercion = new Comparator<ArchiveAttributeCoercion>() {
            @Override
            public int compare(ArchiveAttributeCoercion expectedAAC, ArchiveAttributeCoercion actualAAC) {
                return expectedAAC.getCommonName().compareTo(actualAAC.getCommonName());
            }
        };
        Collections.sort(expectedAACList, archiveAttributeCoercion);
        Collections.sort(actualAACList, archiveAttributeCoercion);
        for (int i = 0; i < expectedAACList.size(); i++) {
            ArchiveAttributeCoercion expectedAAC = expectedAACList.get(i);
            ArchiveAttributeCoercion actualAAC = actualAACList.get(i);
            assertEquals(expectedAAC.getCommonName(), actualAAC.getCommonName());
            assertEquals(expectedAAC.getDIMSE(), actualAAC.getDIMSE());
            assertEquals(expectedAAC.getXSLTStylesheetURI(), actualAAC.getXSLTStylesheetURI());
            assertEquals(expectedAAC.getPriority(), actualAAC.getPriority());
            assertArrayEquals(expectedAAC.getAETitles(), actualAAC.getAETitles());
            assertArrayEquals(expectedAAC.getHostNames(), actualAAC.getHostNames());
            assertArrayEquals(expectedAAC.getSOPClasses(), actualAAC.getSOPClasses());
            assertEquals(expectedAAC.isNoKeywords(), actualAAC.isNoKeywords());
        }
    }
}
