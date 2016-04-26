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

package org.dcm4chee.arc.conf.ldap;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.ldap.LdapDicomConfigurationExtension;
import org.dcm4che3.conf.ldap.LdapUtils;
import org.dcm4che3.data.*;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Dimse;
import org.dcm4che3.net.TransferCapability;
import org.dcm4che3.util.Property;
import org.dcm4che3.util.TagUtils;
import org.dcm4chee.arc.conf.*;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.*;
import javax.naming.directory.Attributes;
import java.net.URI;
import java.security.cert.CertificateException;
import java.util.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jul 2015
 */
class LdapArchiveConfiguration extends LdapDicomConfigurationExtension {

    @Override
    protected void storeTo(Device device, Attributes attrs) {
        ArchiveDeviceExtension ext = device.getDeviceExtension(ArchiveDeviceExtension.class);
        if (ext == null)
            return;

        attrs.get("objectclass").add("dcmArchiveDevice");
        LdapUtils.storeNotNull(attrs, "dcmFuzzyAlgorithmClass", ext.getFuzzyAlgorithmClass());
        LdapUtils.storeNotNull(attrs, "dcmStorageID", ext.getStorageID());
        LdapUtils.storeNotNull(attrs, "dcmOverwritePolicy", ext.getOverwritePolicy());
        LdapUtils.storeNotNull(attrs, "dcmBulkDataSpoolDirectory", ext.getBulkDataSpoolDirectory());
        LdapUtils.storeNotNull(attrs, "dcmQueryRetrieveViewID", ext.getQueryRetrieveViewID());
        LdapUtils.storeNotDef(attrs, "dcmPersonNameComponentOrderInsensitiveMatching",
                ext.isPersonNameComponentOrderInsensitiveMatching(), false);
        LdapUtils.storeNotDef(attrs, "dcmSendPendingCGet", ext.isSendPendingCGet(), false);
        LdapUtils.storeNotNull(attrs, "dcmSendPendingCMoveInterval", ext.getSendPendingCMoveInterval());
        LdapUtils.storeNotNull(attrs, "dcmFallbackCMoveSCP", ext.getFallbackCMoveSCP());
        LdapUtils.storeNotNull(attrs, "dcmFallbackCMoveSCPDestination", ext.getFallbackCMoveSCPDestination());
        LdapUtils.storeNotNull(attrs, "dcmFallbackCMoveSCPLevel", ext.getFallbackCMoveSCPLevel());
        LdapUtils.storeNotDef(attrs, "dcmFallbackCMoveSCPRetries", ext.getFallbackCMoveSCPRetries(), 0);
        LdapUtils.storeNotNull(attrs, "dcmAltCMoveSCP", ext.getAlternativeCMoveSCP());
        LdapUtils.storeNotNull(attrs, "dcmWadoSR2HtmlTemplateURI", ext.getWadoSR2HtmlTemplateURI());
        LdapUtils.storeNotNull(attrs, "dcmWadoSR2TextTemplateURI", ext.getWadoSR2TextTemplateURI());
        LdapUtils.storeNotNull(attrs, "hl7PatientUpdateTemplateURI", ext.getPatientUpdateTemplateURI());
        LdapUtils.storeNotNull(attrs, "dcmUnzipVendorDataToURI", ext.getUnzipVendorDataToURI());
        LdapUtils.storeNotEmpty(attrs, "dcmWadoSupportedSRClasses", ext.getWadoSupportedSRClasses());
        LdapUtils.storeNotDef(attrs, "dcmQidoMaxNumberOfResults", ext.getQidoMaxNumberOfResults(), 0);
        LdapUtils.storeNotEmpty(attrs, "dcmFwdMppsDestination", ext.getMppsForwardDestinations());
        LdapUtils.storeNotEmpty(attrs, "dcmIanDestination", ext.getIanDestinations());
        LdapUtils.storeNotNull(attrs, "dcmIanDelay", ext.getIanDelay());
        LdapUtils.storeNotNull(attrs, "dcmIanTimeout", ext.getIanTimeout());
        LdapUtils.storeNotDef(attrs, "dcmIanOnTimeout", ext.isIanOnTimeout(), false);
        LdapUtils.storeNotNull(attrs, "dcmIanTaskPollingInterval", ext.getIanTaskPollingInterval());
        LdapUtils.storeNotDef(attrs, "dcmIanTaskFetchSize", ext.getIanTaskFetchSize(), 100);
        LdapUtils.storeNotNull(attrs, "dcmExportTaskPollingInterval", ext.getExportTaskPollingInterval());
        LdapUtils.storeNotDef(attrs, "dcmExportTaskFetchSize", ext.getExportTaskFetchSize(), 5);
        LdapUtils.storeNotNull(attrs, "dcmPurgeStoragePollingInterval", ext.getPurgeStoragePollingInterval());
        LdapUtils.storeNotDef(attrs, "dcmPurgeStorageFetchSize", ext.getPurgeStorageFetchSize(), 100);
        LdapUtils.storeNotNull(attrs, "dcmDeleteRejectedPollingInterval", ext.getDeleteRejectedPollingInterval());
        LdapUtils.storeNotDef(attrs, "dcmDeleteRejectedFetchSize", ext.getDeleteRejectedFetchSize(), 100);
        LdapUtils.storeNotDef(attrs, "dcmDeleteStudyBatchSize", ext.getDeleteStudyBatchSize(), 10);
        LdapUtils.storeNotDef(attrs, "dcmDeletePatientOnDeleteLastStudy",
                ext.isDeletePatientOnDeleteLastStudy(), false);
        LdapUtils.storeNotNull(attrs, "dcmMaxAccessTimeStaleness", ext.getMaxAccessTimeStaleness());
        LdapUtils.storeNotNull(attrs, "dcmAuditSpoolDirectory", ext.getAuditSpoolDirectory());
        LdapUtils.storeNotNull(attrs, "dcmAuditPollingInterval", ext.getAuditPollingInterval());
        LdapUtils.storeNotNull(attrs, "dcmAuditAggregateDuration", ext.getAuditAggregateDuration());
        LdapUtils.storeNotNull(attrs, "dcmStowSpoolDirectory", ext.getStowSpoolDirectory());
        LdapUtils.storeNotNull(attrs, "dcmPurgeQueueMessagePollingInterval", ext.getPurgeQueueMessagePollingInterval());
        LdapUtils.storeNotDef(attrs, "dcmPurgeQueueMessageFetchSize", ext.getPurgeQueueMessageFetchSize(), 100);
    }

    @Override
    protected void loadFrom(Device device, Attributes attrs) throws NamingException, CertificateException {
        if (!LdapUtils.hasObjectClass(attrs, "dcmArchiveDevice"))
            return;

        ArchiveDeviceExtension ext = new ArchiveDeviceExtension();
        device.addDeviceExtension(ext);
        ext.setFuzzyAlgorithmClass(LdapUtils.stringValue(attrs.get("dcmFuzzyAlgorithmClass"), null));
        ext.setStorageID(LdapUtils.stringValue(attrs.get("dcmStorageID"), null));
        ext.setOverwritePolicy(LdapUtils.enumValue(OverwritePolicy.class, attrs.get("dcmOverwritePolicy"), null));
        ext.setBulkDataSpoolDirectory(LdapUtils.stringValue(attrs.get("dcmBulkDataSpoolDirectory"), null));
        ext.setQueryRetrieveViewID(LdapUtils.stringValue(attrs.get("dcmQueryRetrieveViewID"), null));
        ext.setPersonNameComponentOrderInsensitiveMatching(
                LdapUtils.booleanValue(attrs.get("dcmPersonNameComponentOrderInsensitiveMatching"), false));
        ext.setSendPendingCGet(LdapUtils.booleanValue(attrs.get("dcmSendPendingCGet"), false));
        ext.setSendPendingCMoveInterval(
                toDuration(LdapUtils.stringValue(attrs.get("dcmSendPendingCMoveInterval"), null)));
        ext.setFallbackCMoveSCP(LdapUtils.stringValue(attrs.get("dcmFallbackCMoveSCP"), null));
        ext.setFallbackCMoveSCPDestination(LdapUtils.stringValue(attrs.get("dcmFallbackCMoveSCPDestination"), null));
        ext.setFallbackCMoveSCPLevel(
                LdapUtils.enumValue(MoveForwardLevel.class, attrs.get("dcmFallbackCMoveSCPLevel"), null));
        ext.setFallbackCMoveSCPRetries(LdapUtils.intValue(attrs.get("dcmFallbackCMoveSCPRetries"), 0));
        ext.setAlternativeCMoveSCP(LdapUtils.stringValue(attrs.get("dcmAltCMoveSCP"), null));
        ext.setWadoSR2HtmlTemplateURI(LdapUtils.stringValue(attrs.get("dcmWadoSR2HtmlTemplateURI"), null));
        ext.setWadoSR2TextTemplateURI(LdapUtils.stringValue(attrs.get("dcmWadoSR2TextTemplateURI"), null));
        ext.setPatientUpdateTemplateURI(LdapUtils.stringValue(attrs.get("hl7PatientUpdateTemplateURI"), null));
        ext.setUnzipVendorDataToURI(LdapUtils.stringValue(attrs.get("dcmUnzipVendorDataToURI"), null));
        ext.setWadoSupportedSRClasses(LdapUtils.stringArray(attrs.get("dcmWadoSupportedSRClasses")));
        ext.setQidoMaxNumberOfResults(LdapUtils.intValue(attrs.get("dcmQidoMaxNumberOfResults"), 0));
        ext.setMppsForwardDestinations(LdapUtils.stringArray(attrs.get("dcmFwdMppsDestination")));
        ext.setIanDestinations(LdapUtils.stringArray(attrs.get("dcmIanDestination")));
        ext.setIanDelay(toDuration(LdapUtils.stringValue(attrs.get("dcmIanDelay"), null)));
        ext.setIanTimeout(toDuration(LdapUtils.stringValue(attrs.get("dcmIanTimeout"), null)));
        ext.setIanOnTimeout(LdapUtils.booleanValue(attrs.get("dcmIanOnTimeout"), false));
        ext.setIanTaskPollingInterval(
                toDuration(LdapUtils.stringValue(attrs.get("dcmIanTaskPollingInterval"), null)));
        ext.setIanTaskFetchSize(LdapUtils.intValue(attrs.get("dcmIanTaskFetchSize"), 100));
        ext.setExportTaskPollingInterval(
                toDuration(LdapUtils.stringValue(attrs.get("dcmExportTaskPollingInterval"), null)));
        ext.setExportTaskFetchSize(LdapUtils.intValue(attrs.get("dcmExportTaskFetchSize"), 5));
        ext.setPurgeStoragePollingInterval(
                toDuration(LdapUtils.stringValue(attrs.get("dcmPurgeStoragePollingInterval"), null)));
        ext.setPurgeStorageFetchSize(LdapUtils.intValue(attrs.get("dcmPurgeStorageFetchSize"), 100));
        ext.setDeleteRejectedPollingInterval(
                toDuration(LdapUtils.stringValue(attrs.get("dcmDeleteRejectedPollingInterval"), null)));
        ext.setDeleteRejectedFetchSize(LdapUtils.intValue(attrs.get("dcmDeleteRejectedFetchSize"), 100));
        ext.setDeleteStudyBatchSize(LdapUtils.intValue(attrs.get("dcmDeleteStudyBatchSize"), 10));
        ext.setDeletePatientOnDeleteLastStudy(
                LdapUtils.booleanValue(attrs.get("dcmDeletePatientOnDeleteLastStudy"), false));
        ext.setMaxAccessTimeStaleness(toDuration(LdapUtils.stringValue(attrs.get("dcmMaxAccessTimeStaleness"), null)));
        ext.setAuditSpoolDirectory(LdapUtils.stringValue(attrs.get("dcmAuditSpoolDirectory"), null));
        ext.setAuditPollingInterval(toDuration(LdapUtils.stringValue(attrs.get("dcmAuditPollingInterval"), null)));
        ext.setAuditAggregateDuration(toDuration(LdapUtils.stringValue(attrs.get("dcmAuditAggregateDuration"), null)));
        ext.setStowSpoolDirectory(LdapUtils.stringValue(attrs.get("dcmStowSpoolDirectory"), null));
        ext.setPurgeQueueMessagePollingInterval(toDuration(LdapUtils.stringValue(
                attrs.get("dcmPurgeQueueMessagePollingInterval"), null)));
        ext.setPurgeQueueMessageFetchSize(LdapUtils.intValue(attrs.get("dcmPurgeQueueMessageFetchSize"), 100));
    }

    @Override
    protected void storeDiffs(Device prev, Device device, List<ModificationItem> mods) {
        ArchiveDeviceExtension aa = prev.getDeviceExtension(ArchiveDeviceExtension.class);
        ArchiveDeviceExtension bb = device.getDeviceExtension(ArchiveDeviceExtension.class);
        if (aa == null || bb == null)
            return;

        LdapUtils.storeDiff(mods, "dcmFuzzyAlgorithmClass", aa.getFuzzyAlgorithmClass(), bb.getFuzzyAlgorithmClass());
        LdapUtils.storeDiff(mods, "dcmStorageID", aa.getStorageID(), bb.getStorageID());
        LdapUtils.storeDiff(mods, "dcmOverwritePolicy", aa.getBulkDataSpoolDirectory(), bb.getBulkDataSpoolDirectory());
        LdapUtils.storeDiff(mods, "dcmBulkDataSpoolDirectory",
                aa.getBulkDataSpoolDirectory(), bb.getBulkDataSpoolDirectory());
        LdapUtils.storeDiff(mods, "dcmQueryRetrieveViewID", aa.getQueryRetrieveViewID(), bb.getQueryRetrieveViewID());
        LdapUtils.storeDiff(mods, "dcmPersonNameComponentOrderInsensitiveMatching",
                aa.isPersonNameComponentOrderInsensitiveMatching(),
                bb.isPersonNameComponentOrderInsensitiveMatching(),
                false);
        LdapUtils.storeDiff(mods, "dcmSendPendingCGet", aa.isSendPendingCGet(), bb.isSendPendingCGet(), false);
        LdapUtils.storeDiff(mods, "dcmSendPendingCMoveInterval",
                aa.getSendPendingCMoveInterval(), bb.getSendPendingCMoveInterval());
        LdapUtils.storeDiff(mods, "dcmFallbackCMoveSCP", aa.getFallbackCMoveSCP(), bb.getFallbackCMoveSCP());
        LdapUtils.storeDiff(mods, "dcmFallbackCMoveSCPDestination",
                aa.getFallbackCMoveSCPDestination(), bb.getFallbackCMoveSCPDestination());
        LdapUtils.storeDiff(mods, "dcmFallbackCMoveSCPLevel",
                aa.getFallbackCMoveSCPLevel(), bb.getFallbackCMoveSCPLevel());
        LdapUtils.storeDiff(mods, "dcmFallbackCMoveSCPRetries",
                aa.getFallbackCMoveSCPRetries(), bb.getFallbackCMoveSCPRetries(),  0);
        LdapUtils.storeDiff(mods, "dcmAltCMoveSCP", aa.getAlternativeCMoveSCP(), bb.getAlternativeCMoveSCP());
        LdapUtils.storeDiff(mods, "dcmWadoSR2HtmlTemplateURI",
                aa.getWadoSR2HtmlTemplateURI(), bb.getWadoSR2HtmlTemplateURI());
        LdapUtils.storeDiff(mods, "dcmWadoSR2TextTemplateURI",
                aa.getWadoSR2TextTemplateURI(), bb.getWadoSR2TextTemplateURI());
        LdapUtils.storeDiff(mods, "hl7PatientUpdateTemplateURI",
                aa.getPatientUpdateTemplateURI(), bb.getPatientUpdateTemplateURI());
        LdapUtils.storeDiff(mods, "dcmUnzipVendorDataToURI",
                aa.getUnzipVendorDataToURI(), bb.getUnzipVendorDataToURI());
        LdapUtils.storeDiff(mods, "dcmWadoSupportedSRClasses",
                aa.getWadoSupportedSRClasses(), bb.getWadoSupportedSRClasses());
        LdapUtils.storeDiff(mods, "dcmQidoMaxNumberOfResults",
                aa.getQidoMaxNumberOfResults(), bb.getQidoMaxNumberOfResults(),  0);
        LdapUtils.storeDiff(mods, "dcmFwdMppsDestination",
                aa.getMppsForwardDestinations(), bb.getMppsForwardDestinations());
        LdapUtils.storeDiff(mods, "dcmIanDestination", aa.getIanDestinations(), bb.getIanDestinations());
        LdapUtils.storeDiff(mods, "dcmIanDelay", aa.getIanDelay(), bb.getIanDelay());
        LdapUtils.storeDiff(mods, "dcmIanTimeout", aa.getIanTimeout(), bb.getIanTimeout());
        LdapUtils.storeDiff(mods, "dcmIanOnTimeout", aa.isIanOnTimeout(), bb.isIanOnTimeout(), false);
        LdapUtils.storeDiff(mods, "dcmIanTaskPollingInterval",
                aa.getIanTaskPollingInterval(), bb.getIanTaskPollingInterval());
        LdapUtils.storeDiff(mods, "dcmIanTaskFetchSize", aa.getIanTaskFetchSize(), bb.getIanTaskFetchSize(), 100);
        LdapUtils.storeDiff(mods, "dcmExportTaskPollingInterval",
                aa.getExportTaskPollingInterval(), bb.getExportTaskPollingInterval());
        LdapUtils.storeDiff(mods, "dcmExportTaskFetchSize",
                aa.getExportTaskFetchSize(), bb.getExportTaskFetchSize(), 5);
        LdapUtils.storeDiff(mods, "dcmPurgeStoragePollingInterval",
                aa.getPurgeStoragePollingInterval(), bb.getPurgeStoragePollingInterval());
        LdapUtils.storeDiff(mods, "dcmPurgeStorageFetchSize",
                aa.getPurgeStorageFetchSize(), bb.getPurgeStorageFetchSize(), 100);
        LdapUtils.storeDiff(mods, "dcmDeleteRejectedPollingInterval",
                aa.getDeleteRejectedPollingInterval(), bb.getDeleteRejectedPollingInterval());
        LdapUtils.storeDiff(mods, "dcmDeleteRejectedFetchSize",
                aa.getDeleteRejectedFetchSize(), bb.getDeleteRejectedFetchSize(), 100);
        LdapUtils.storeDiff(mods, "dcmDeleteStudyBatchSize",
                aa.getDeleteStudyBatchSize(), bb.getDeleteStudyBatchSize(), 10);
        LdapUtils.storeDiff(mods, "dcmDeletePatientOnDeleteLastStudy",
                aa.isDeletePatientOnDeleteLastStudy(), bb.isDeletePatientOnDeleteLastStudy(), false);
        LdapUtils.storeDiff(mods, "dcmMaxAccessTimeStaleness",
                aa.getMaxAccessTimeStaleness(), bb.getMaxAccessTimeStaleness());
        LdapUtils.storeDiff(mods, "dcmAuditSpoolDirectory",
                aa.getAuditSpoolDirectory(), bb.getAuditSpoolDirectory());
        LdapUtils.storeDiff(mods, "dcmAuditPollingInterval",
                aa.getAuditPollingInterval(), bb.getAuditPollingInterval());
        LdapUtils.storeDiff(mods, "dcmAuditAggregateDuration",
                aa.getAuditAggregateDuration(), bb.getAuditAggregateDuration());
        LdapUtils.storeDiff(mods, "dcmStowSpoolDirectory",
                aa.getStowSpoolDirectory(), bb.getStowSpoolDirectory());
        LdapUtils.storeDiff(mods, "dcmPurgeQueueMessageFetchSize", aa.getPurgeQueueMessageFetchSize(),
                bb.getPurgeQueueMessageFetchSize());
        LdapUtils.storeDiff(mods, "dcmPurgeQueueMessagePollingInterval", aa.getPurgeQueueMessagePollingInterval(),
                bb.getPurgeQueueMessagePollingInterval());
    }

    @Override
    protected void storeChilds(String deviceDN, Device device)
            throws NamingException, ConfigurationException {
        ArchiveDeviceExtension arcDev = device
                .getDeviceExtension(ArchiveDeviceExtension.class);
        if (arcDev == null)
            return;

        storeAttributeFilter(deviceDN, arcDev);
        storeStorageDescriptors(deviceDN, arcDev);
        storeQueueDescriptors(deviceDN, arcDev);
        storeExporterDescriptors(deviceDN, arcDev);
        storeExportRules(arcDev.getExportRules(), deviceDN);
        storeCompressionRules(arcDev.getCompressionRules(), deviceDN);
        storeAttributeCoercions(arcDev.getAttributeCoercions(), deviceDN);
        storeQueryRetrieveViews(deviceDN, arcDev);
        storeRejectNotes(deviceDN, arcDev);
    }

    @Override
    protected void loadChilds(Device device, String deviceDN)
            throws NamingException, ConfigurationException {
        ArchiveDeviceExtension arcdev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        if (arcdev == null)
            return;

        loadAttributeFilters(arcdev, deviceDN);
        loadStorageDescriptors(arcdev, deviceDN);
        loadQueueDescriptors(arcdev, deviceDN);
        loadExporterDescriptors(arcdev, deviceDN);
        loadExportRules(arcdev.getExportRules(), deviceDN);
        loadCompressionRules(arcdev.getCompressionRules(), deviceDN);
        loadAttributeCoercions(arcdev.getAttributeCoercions(), deviceDN);
        loadQueryRetrieveViews(arcdev, deviceDN);
        loadRejectNotes(arcdev, deviceDN);
    }

    @Override
    protected void mergeChilds(Device prev, Device device, String deviceDN)
            throws NamingException, ConfigurationException {
        ArchiveDeviceExtension aa = prev
                .getDeviceExtension(ArchiveDeviceExtension.class);
        ArchiveDeviceExtension bb = device
                .getDeviceExtension(ArchiveDeviceExtension.class);
        if (aa == null || bb == null)
            return;

        mergeAttributeFilters(aa, bb, deviceDN);
        mergeStorageDescriptors(aa, bb, deviceDN);
        mergeQueueDescriptors(aa, bb, deviceDN);
        mergeExportDescriptors(aa, bb, deviceDN);
        mergeExportRules(aa.getExportRules(), bb.getExportRules(), deviceDN);
        mergeCompressionRules(aa.getCompressionRules(), bb.getCompressionRules(), deviceDN);
        mergeAttributeCoercions(aa.getAttributeCoercions(), bb.getAttributeCoercions(), deviceDN);
        mergeQueryRetrieveViews(aa, bb, deviceDN);
        mergeRejectNotes(aa, bb, deviceDN);
    }

    @Override
    protected void storeTo(ApplicationEntity ae, Attributes attrs) {
        ArchiveAEExtension ext = ae.getAEExtension(ArchiveAEExtension.class);
        if (ext == null)
            return;

        attrs.get("objectclass").add("dcmArchiveNetworkAE");
        LdapUtils.storeNotNull(attrs, "dcmStorageID", ext.getStorageID());
        LdapUtils.storeNotNull(attrs, "dcmStoreAccessControlID", ext.getStoreAccessControlID());
        LdapUtils.storeNotEmpty(attrs, "dcmAccessControlID", ext.getAccessControlIDs());
        LdapUtils.storeNotNull(attrs, "dcmOverwritePolicy", ext.getOverwritePolicy());
        LdapUtils.storeNotNull(attrs, "dcmBulkDataSpoolDirectory", ext.getBulkDataSpoolDirectory());
        LdapUtils.storeNotNull(attrs, "dcmQueryRetrieveViewID", ext.getQueryRetrieveViewID());
        LdapUtils.storeNotNull(attrs, "dcmPersonNameComponentOrderInsensitiveMatching",
                ext.getPersonNameComponentOrderInsensitiveMatching());
        LdapUtils.storeNotNull(attrs, "dcmSendPendingCGet", ext.getSendPendingCGet());
        LdapUtils.storeNotNull(attrs, "dcmSendPendingCMoveInterval", ext.getSendPendingCMoveInterval());
        LdapUtils.storeNotNull(attrs, "dcmFallbackCMoveSCP", ext.getFallbackCMoveSCP());
        LdapUtils.storeNotNull(attrs, "dcmFallbackCMoveSCPDestination", ext.getFallbackCMoveSCPDestination());
        LdapUtils.storeNotNull(attrs, "dcmFallbackCMoveSCPLevel", ext.getFallbackCMoveSCPLevel());
        LdapUtils.storeNotDef(attrs, "dcmFallbackCMoveSCPRetries", ext.getFallbackCMoveSCPRetries(), 0);
        LdapUtils.storeNotNull(attrs, "dcmAltCMoveSCP", ext.getAlternativeCMoveSCP());
        LdapUtils.storeNotNull(attrs, "dcmWadoSR2HtmlTemplateURI", ext.getWadoSR2HtmlTemplateURI());
        LdapUtils.storeNotNull(attrs, "dcmWadoSR2TextTemplateURI", ext.getWadoSR2TextTemplateURI());
        LdapUtils.storeNotDef(attrs, "dcmQidoMaxNumberOfResults", ext.getQidoMaxNumberOfResults(), 0);
        LdapUtils.storeNotEmpty(attrs, "dcmFwdMppsDestination", ext.getMppsForwardDestinations());
        LdapUtils.storeNotEmpty(attrs, "dcmIanDestination", ext.getIanDestinations());
        LdapUtils.storeNotNull(attrs, "dcmIanDelay", ext.getIanDelay());
        LdapUtils.storeNotNull(attrs, "dcmIanTimeout", ext.getIanTimeout());
        LdapUtils.storeNotNull(attrs, "dcmIanOnTimeout", ext.getIanOnTimeout());
    }

    @Override
    protected void loadFrom(ApplicationEntity ae, Attributes attrs) throws NamingException {
        if (!LdapUtils.hasObjectClass(attrs, "dcmArchiveNetworkAE"))
            return;

        ArchiveAEExtension ext = new ArchiveAEExtension();
        ae.addAEExtension(ext);
        ext.setStorageID(LdapUtils.stringValue(attrs.get("dcmStorageID"), null));
        ext.setStoreAccessControlID(LdapUtils.stringValue(attrs.get("dcmStoreAccessControlID"), null));
        ext.setAccessControlIDs(LdapUtils.stringArray(attrs.get("dcmAccessControlID")));
        ext.setOverwritePolicy(LdapUtils.enumValue(OverwritePolicy.class, attrs.get("dcmOverwritePolicy"), null));
        ext.setBulkDataSpoolDirectory(LdapUtils.stringValue(attrs.get("dcmBulkDataSpoolDirectory"), null));
        ext.setQueryRetrieveViewID(LdapUtils.stringValue(attrs.get("dcmQueryRetrieveViewID"), null));
        ext.setPersonNameComponentOrderInsensitiveMatching(
                LdapUtils.booleanValue(attrs.get("dcmPersonNameComponentOrderInsensitiveMatching"), null));
        ext.setSendPendingCGet(LdapUtils.booleanValue(attrs.get("dcmSendPendingCGet"), null));
        ext.setSendPendingCMoveInterval(
                toDuration(LdapUtils.stringValue(attrs.get("dcmSendPendingCMoveInterval"), null)));
        ext.setFallbackCMoveSCP(LdapUtils.stringValue(attrs.get("dcmFallbackCMoveSCP"), null));
        ext.setFallbackCMoveSCPDestination(LdapUtils.stringValue(attrs.get("dcmFallbackCMoveSCPDestination"), null));
        ext.setFallbackCMoveSCPLevel(
                LdapUtils.enumValue(MoveForwardLevel.class, attrs.get("dcmFallbackCMoveSCPLevel"), null));
        ext.setFallbackCMoveSCPRetries(LdapUtils.intValue(attrs.get("dcmFallbackCMoveSCPRetries"), 0));
        ext.setAlternativeCMoveSCP(LdapUtils.stringValue(attrs.get("dcmAltCMoveSCP"), null));
        ext.setWadoSR2HtmlTemplateURI(LdapUtils.stringValue(attrs.get("dcmWadoSR2HtmlTemplateURI"), null));
        ext.setWadoSR2TextTemplateURI(LdapUtils.stringValue(attrs.get("dcmWadoSR2TextTemplateURI"), null));
        ext.setQidoMaxNumberOfResults(LdapUtils.intValue(attrs.get("dcmQidoMaxNumberOfResults"), 0));
        ext.setMppsForwardDestinations(LdapUtils.stringArray(attrs.get("dcmFwdMppsDestination")));
        ext.setIanDestinations(LdapUtils.stringArray(attrs.get("dcmIanDestination")));
        ext.setIanDelay(toDuration(LdapUtils.stringValue(attrs.get("dcmIanDelay"), null)));
        ext.setIanTimeout(toDuration(LdapUtils.stringValue(attrs.get("dcmIanTimeout"), null)));
        ext.setIanOnTimeout(LdapUtils.booleanValue(attrs.get("dcmIanOnTimeout"), null));
    }

    @Override
    protected void storeDiffs(ApplicationEntity prev, ApplicationEntity ae, List<ModificationItem> mods) {
        ArchiveAEExtension aa = prev.getAEExtension(ArchiveAEExtension.class);
        ArchiveAEExtension bb = ae.getAEExtension(ArchiveAEExtension.class);
        if (aa == null || bb == null)
            return;

        LdapUtils.storeDiff(mods, "dcmStorageID", aa.getStorageID(), bb.getStorageID());
        LdapUtils.storeDiff(mods, "dcmStoreAccessControlID", aa.getStoreAccessControlID(), bb.getStoreAccessControlID());
        LdapUtils.storeDiff(mods, "dcmAccessControlIDs", aa.getAccessControlIDs(), bb.getAccessControlIDs());
        LdapUtils.storeDiff(mods, "dcmOverwritePolicy", aa.getBulkDataSpoolDirectory(), bb.getBulkDataSpoolDirectory());
        LdapUtils.storeDiff(mods, "dcmBulkDataSpoolDirectory",
                aa.getBulkDataSpoolDirectory(), bb.getBulkDataSpoolDirectory());
        LdapUtils.storeDiff(mods, "dcmQueryRetrieveViewID", aa.getQueryRetrieveViewID(), bb.getQueryRetrieveViewID());
        LdapUtils.storeDiff(mods, "dcmPersonNameComponentOrderInsensitiveMatching",
                aa.getPersonNameComponentOrderInsensitiveMatching(),
                bb.getPersonNameComponentOrderInsensitiveMatching());
        LdapUtils.storeDiff(mods, "dcmSendPendingCGet", aa.getSendPendingCGet(), bb.getSendPendingCGet());
        LdapUtils.storeDiff(mods, "dcmSendPendingCMoveInterval",
                aa.getSendPendingCMoveInterval(), bb.getSendPendingCMoveInterval());
        LdapUtils.storeDiff(mods, "dcmFallbackCMoveSCP", aa.getFallbackCMoveSCP(), bb.getFallbackCMoveSCP());
        LdapUtils.storeDiff(mods, "dcmFallbackCMoveSCPDestination",
                aa.getFallbackCMoveSCPDestination(), bb.getFallbackCMoveSCPDestination());
        LdapUtils.storeDiff(mods, "dcmFallbackCMoveSCPLevel",
                aa.getFallbackCMoveSCPLevel(), bb.getFallbackCMoveSCPLevel());
        LdapUtils.storeDiff(mods, "dcmFallbackCMoveSCPRetries",
                aa.getFallbackCMoveSCPRetries(), bb.getFallbackCMoveSCPRetries(),  0);
        LdapUtils.storeDiff(mods, "dcmAltCMoveSCP", aa.getAlternativeCMoveSCP(), bb.getAlternativeCMoveSCP());
        LdapUtils.storeDiff(mods, "dcmWadoSR2HtmlTemplateURI",
                aa.getWadoSR2HtmlTemplateURI(), bb.getWadoSR2HtmlTemplateURI());
        LdapUtils.storeDiff(mods, "dcmWadoSR2TextTemplateURI",
                aa.getWadoSR2TextTemplateURI(), bb.getWadoSR2TextTemplateURI());
        LdapUtils.storeDiff(mods, "dcmQidoMaxNumberOfResults",
                aa.getQidoMaxNumberOfResults(), bb.getQidoMaxNumberOfResults(), 0);
        LdapUtils.storeDiff(mods, "dcmFwdMppsDestination",
                aa.getMppsForwardDestinations(), bb.getMppsForwardDestinations());
        LdapUtils.storeDiff(mods, "dcmIanDestination", aa.getIanDestinations(), bb.getIanDestinations());
        LdapUtils.storeDiff(mods, "dcmIanDelay", aa.getIanDelay(), bb.getIanDelay());
        LdapUtils.storeDiff(mods, "dcmIanTimeout", aa.getIanTimeout(), bb.getIanTimeout());
        LdapUtils.storeDiff(mods, "dcmIanOnTimeout", aa.getIanOnTimeout(), bb.getIanOnTimeout());
    }

    @Override
    protected void storeChilds(String aeDN, ApplicationEntity ae) throws NamingException {
        ArchiveAEExtension aeExt = ae.getAEExtension(ArchiveAEExtension.class);
        if (aeExt == null)
            return;

        storeExportRules(aeExt.getExportRules(), aeDN);
        storeCompressionRules(aeExt.getCompressionRules(), aeDN);
        storeAttributeCoercions(aeExt.getAttributeCoercions(), aeDN);
    }

    @Override
    protected void loadChilds(ApplicationEntity ae, String aeDN) throws NamingException {
        ArchiveAEExtension aeExt = ae.getAEExtension(ArchiveAEExtension.class);
        if (aeExt == null)
            return;

        loadExportRules(aeExt.getExportRules(), aeDN);
        loadCompressionRules(aeExt.getCompressionRules(), aeDN);
        loadAttributeCoercions(aeExt.getAttributeCoercions(), aeDN);
    }

    @Override
    protected void mergeChilds(ApplicationEntity prev, ApplicationEntity ae, String aeDN) throws NamingException {
        ArchiveAEExtension aa = prev.getAEExtension(ArchiveAEExtension.class);
        ArchiveAEExtension bb = ae.getAEExtension(ArchiveAEExtension.class);
        if (aa == null || bb == null)
            return;

        mergeExportRules(aa.getExportRules(), bb.getExportRules(), aeDN);
        mergeCompressionRules(aa.getCompressionRules(), bb.getCompressionRules(), aeDN);
        mergeAttributeCoercions(aa.getAttributeCoercions(), bb.getAttributeCoercions(), aeDN);
    }

    private void storeAttributeFilter(String deviceDN, ArchiveDeviceExtension arcDev)
            throws NamingException {
        for (Entity entity : Entity.values()) {
            config.createSubcontext(
                    LdapUtils.dnOf("dcmEntity", entity.name(), deviceDN),
                    storeTo(arcDev.getAttributeFilter(entity), entity, new BasicAttributes(true)));
        }
    }

    private static Attributes storeTo(AttributeFilter filter, Entity entity,  BasicAttributes attrs) {
        attrs.put("objectclass", "dcmAttributeFilter");
        attrs.put("dcmEntity", entity.name());
        attrs.put(tagsAttr("dcmTag", filter.getSelection()));
        LdapUtils.storeNotNull(attrs, "dcmCustomAttribute1", filter.getCustomAttribute1());
        LdapUtils.storeNotNull(attrs, "dcmCustomAttribute2", filter.getCustomAttribute2());
        LdapUtils.storeNotNull(attrs, "dcmCustomAttribute3", filter.getCustomAttribute3());
        LdapUtils.storeNotNull(attrs, "dcmAttributeUpdatePolicy", filter.getAttributeUpdatePolicy());
        return attrs;
    }
    private static Attribute tagsAttr(String attrID, int[] tags) {
        Attribute attr = new BasicAttribute(attrID);
        for (int tag : tags)
            attr.add(TagUtils.toHexString(tag));
        return attr;
    }


    private void loadAttributeFilters(ArchiveDeviceExtension device, String deviceDN)
            throws NamingException {
        NamingEnumeration<SearchResult> ne = config.search(deviceDN, "(objectclass=dcmAttributeFilter)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                AttributeFilter filter = new AttributeFilter(tags(attrs.get("dcmTag")));
                filter.setCustomAttribute1(valueSelector(attrs.get("dcmCustomAttribute1")));
                filter.setCustomAttribute2(valueSelector(attrs.get("dcmCustomAttribute2")));
                filter.setCustomAttribute3(valueSelector(attrs.get("dcmCustomAttribute3")));
                filter.setAttributeUpdatePolicy(
                        LdapUtils.enumValue(org.dcm4che3.data.Attributes.UpdatePolicy.class,
                        attrs.get("dcmAttributeUpdatePolicy"), null));
                device.setAttributeFilter(
                        Entity.valueOf(LdapUtils.stringValue(attrs.get("dcmEntity"), null)),
                        filter);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
    }

    private static ValueSelector valueSelector(Attribute attr)
            throws NamingException {
        return attr != null ? ValueSelector.valueOf((String) attr.get()) : null;
    }

    private static int[] tags(Attribute attr) throws NamingException {
        int[] is = new int[attr.size()];
        for (int i = 0; i < is.length; i++)
            is[i] = Integer.parseInt((String) attr.get(i), 16);

        return is;
    }

    private void mergeAttributeFilters(ArchiveDeviceExtension prev, ArchiveDeviceExtension devExt,
                                       String deviceDN) throws NamingException {
        for (Entity entity : Entity.values())
            config.modifyAttributes(
                    LdapUtils.dnOf("dcmEntity", entity.toString(), deviceDN),
                    storeDiffs(prev.getAttributeFilter(entity),
                            devExt.getAttributeFilter(entity),
                            new ArrayList<ModificationItem>()));
    }

    private List<ModificationItem> storeDiffs(AttributeFilter prev, AttributeFilter filter,
                                              List<ModificationItem> mods) {
        storeDiffTags(mods, "dcmTag", prev.getSelection(), filter.getSelection());
        LdapUtils.storeDiff(mods, "dcmCustomAttribute1",
                prev.getCustomAttribute1(), filter.getCustomAttribute1());
        LdapUtils.storeDiff(mods, "dcmCustomAttribute2",
                prev.getCustomAttribute2(), filter.getCustomAttribute2());
        LdapUtils.storeDiff(mods, "dcmCustomAttribute3",
                prev.getCustomAttribute3(), filter.getCustomAttribute3());
        LdapUtils.storeDiff(mods, "dcmAttributeUpdatePolicy",
                prev.getAttributeUpdatePolicy(), filter.getAttributeUpdatePolicy());
        return mods;
    }

    private void storeDiffTags(List<ModificationItem> mods, String attrId, int[] prevs, int[] vals) {
        if (!Arrays.equals(prevs, vals))
            mods.add(new ModificationItem(DirContext.REPLACE_ATTRIBUTE, tagsAttr(attrId, vals)));
    }

    private void storeStorageDescriptors(String deviceDN, ArchiveDeviceExtension arcDev) throws NamingException {
        for (StorageDescriptor descriptor : arcDev.getStorageDescriptors()) {
            String storageID = descriptor.getStorageID();
            config.createSubcontext(
                    LdapUtils.dnOf("dcmStorageID", storageID, deviceDN),
                    storeTo(descriptor, new BasicAttributes(true)));
        }
    }

    private Attributes storeTo(StorageDescriptor descriptor, BasicAttributes attrs) {
        attrs.put("objectclass", "dcmStorage");
        attrs.put("dcmStorageID", descriptor.getStorageID());
        attrs.put("dcmURI", descriptor.getStorageURIStr());
        LdapUtils.storeNotNull(attrs, "dcmDigestAlgorithm", descriptor.getDigestAlgorithm());
        LdapUtils.storeNotNull(attrs, "dcmInstanceAvailability", descriptor.getInstanceAvailability());
        LdapUtils.storeNotEmpty(attrs, "dcmRetrieveAET", descriptor.getRetrieveAETitles());
        LdapUtils.storeNotDef(attrs, "dcmReadOnly", descriptor.isReadOnly(), false);
        LdapUtils.storeNotEmpty(attrs, "dcmDeleterThreshold", descriptor.getDeleterThresholdsAsStrings());
        LdapUtils.storeNotEmpty(attrs, "dcmProperty", toStrings(descriptor.getProperties()));
        return attrs;
    }

    private String[] toStrings(Map<String, ?> props) {
        String[] ss = new String[props.size()];
        int i = 0;
        for (Map.Entry<String, ?> entry : props.entrySet())
            ss[i++] = entry.getKey() + '=' + entry.getValue();
        return ss;
    }

    private void loadStorageDescriptors(ArchiveDeviceExtension arcdev, String deviceDN) throws NamingException {
        NamingEnumeration<SearchResult> ne = config.search(deviceDN, "(objectclass=dcmStorage)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                StorageDescriptor desc = new StorageDescriptor(LdapUtils.stringValue(attrs.get("dcmStorageID"), null));
                desc.setStorageURIStr(LdapUtils.stringValue(attrs.get("dcmURI"), null));
                desc.setDigestAlgorithm(LdapUtils.stringValue(attrs.get("dcmDigestAlgorithm"), null));
                desc.setInstanceAvailability(
                        LdapUtils.enumValue(Availability.class, attrs.get("dcmInstanceAvailability"), null));
                desc.setRetrieveAETitles(LdapUtils.stringArray(attrs.get("dcmRetrieveAET")));
                desc.setReadOnly(LdapUtils.booleanValue(attrs.get("dcmReadOnly"), false));
                desc.setDeleterThresholdsFromStrings(LdapUtils.stringArray(attrs.get("dcmDeleterThreshold")));
                desc.setProperties(LdapUtils.stringArray(attrs.get("dcmProperty")));
                arcdev.addStorageDescriptor(desc);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
    }

    private void mergeStorageDescriptors(ArchiveDeviceExtension prev, ArchiveDeviceExtension arcDev, String deviceDN)
            throws NamingException {
        for (StorageDescriptor descriptor : prev.getStorageDescriptors()) {
            String storageID = descriptor.getStorageID();
            if (arcDev.getStorageDescriptor(storageID) == null)
                config.destroySubcontext(LdapUtils.dnOf("dcmStorageID", storageID, deviceDN));
        }
        for (StorageDescriptor descriptor : arcDev.getStorageDescriptors()) {
            String storageID = descriptor.getStorageID();
            String dn = LdapUtils.dnOf("dcmStorageID", storageID, deviceDN);
            StorageDescriptor prevDescriptor = prev.getStorageDescriptor(storageID);
            if (prevDescriptor == null)
                config.createSubcontext(dn,
                        storeTo(descriptor, new BasicAttributes(true)));
            else
                config.modifyAttributes(dn,
                        storeDiffs(prevDescriptor, descriptor, new ArrayList<ModificationItem>()));
        }
    }

    private List<ModificationItem> storeDiffs(StorageDescriptor prev, StorageDescriptor desc,
                                              List<ModificationItem> mods) {
        LdapUtils.storeDiff(mods, "dcmURI", prev.getStorageURIStr(), desc.getStorageURIStr());
        LdapUtils.storeDiff(mods, "dcmDigestAlgorithm", prev.getDigestAlgorithm(), desc.getDigestAlgorithm());
        LdapUtils.storeDiff(mods, "dcmInstanceAvailability",
                prev.getInstanceAvailability(), desc.getInstanceAvailability());
        LdapUtils.storeDiff(mods, "dcmRetrieveAET", prev.getRetrieveAETitles(), desc.getRetrieveAETitles());
        LdapUtils.storeDiff(mods, "dcmReadOnly", prev.isReadOnly(), desc.isReadOnly(), false);
        LdapUtils.storeDiff(mods, "dcmDeleterThreshold",
                prev.getDeleterThresholdsAsStrings(), desc.getDeleterThresholdsAsStrings());
        storeDiffProperties(mods, prev.getProperties(), desc.getProperties());
        return mods;
    }

    private void storeDiffProperties(List<ModificationItem> mods, Map<String, ?> prev, Map<String, ?> props) {
        if (!prev.equals(props)) {
            mods.add(props.size() == 0
                    ? new ModificationItem(DirContext.REMOVE_ATTRIBUTE,
                        new BasicAttribute("dcmProperty"))
                    : new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
                        LdapUtils.attr("dcmProperty", toStrings(props))));
        }
    }

    private void storeQueueDescriptors(String deviceDN, ArchiveDeviceExtension arcDev) throws NamingException {
        for (QueueDescriptor descriptor : arcDev.getQueueDescriptors()) {
            String queueName = descriptor.getQueueName();
            config.createSubcontext(
                    LdapUtils.dnOf("dcmQueueName", queueName, deviceDN),
                    storeTo(descriptor, new BasicAttributes(true)));
        }
    }

    private Attributes storeTo(QueueDescriptor descriptor, BasicAttributes attrs) {
        attrs.put("objectclass", "dcmQueue");
        attrs.put("dcmQueueName", descriptor.getQueueName());
        LdapUtils.storeNotNull(attrs, "dcmJndiName", descriptor.getJndiName());
        LdapUtils.storeNotNull(attrs, "dicomDescription", descriptor.getDescription());
        LdapUtils.storeNotDef(attrs, "dcmMaxRetries", descriptor.getMaxRetries(), 0);
        LdapUtils.storeNotNull(attrs, "dcmRetryDelay", descriptor.getRetryDelay());
        LdapUtils.storeNotNull(attrs, "dcmMaxRetryDelay", descriptor.getMaxRetryDelay());
        LdapUtils.storeNotDef(attrs, "dcmRetryDelayMultiplier", descriptor.getRetryDelayMultiplier(), 100);
        LdapUtils.storeNotNull(attrs, "dcmPurgeQueueMessageCompletedDelay", descriptor.getPurgeQueueMessageCompletedDelay());
        return attrs;
    }

    private void loadQueueDescriptors(ArchiveDeviceExtension arcdev, String deviceDN) throws NamingException {
        NamingEnumeration<SearchResult> ne = config.search(deviceDN, "(objectclass=dcmQueue)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                QueueDescriptor desc = new QueueDescriptor(LdapUtils.stringValue(attrs.get("dcmQueueName"), null));
                desc.setDescription(LdapUtils.stringValue(attrs.get("dicomDescription"), null));
                desc.setJndiName(LdapUtils.stringValue(attrs.get("dcmJndiName"), null));
                desc.setMaxRetries(LdapUtils.intValue(attrs.get("dcmMaxRetries"), 0));
                desc.setRetryDelay(toDuration(LdapUtils.stringValue(attrs.get("dcmRetryDelay"), null)));
                desc.setMaxRetryDelay(toDuration(LdapUtils.stringValue(attrs.get("dcmMaxRetryDelay"), null)));
                desc.setRetryDelayMultiplier(LdapUtils.intValue(attrs.get("dcmRetryDelayMultiplier"), 0));
                desc.setPurgeQueueMessageCompletedDelay(toDuration(LdapUtils.stringValue(
                        attrs.get("dcmPurgeQueueMessageCompletedDelay"), null)));
                arcdev.addQueueDescriptor(desc);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
    }

    private void mergeQueueDescriptors(ArchiveDeviceExtension prev, ArchiveDeviceExtension arcDev, String deviceDN)
            throws NamingException {
        for (QueueDescriptor descriptor : prev.getQueueDescriptors()) {
            String queueName = descriptor.getQueueName();
            if (arcDev.getQueueDescriptor(queueName) == null)
                config.destroySubcontext(LdapUtils.dnOf("dcmQueueName", queueName, deviceDN));
        }
        for (QueueDescriptor descriptor : arcDev.getQueueDescriptors()) {
            String queueName = descriptor.getQueueName();
            String dn = LdapUtils.dnOf("dcmQueueName", queueName, deviceDN);
            QueueDescriptor prevDescriptor = prev.getQueueDescriptor(queueName);
            if (prevDescriptor == null)
                config.createSubcontext(dn,
                        storeTo(descriptor, new BasicAttributes(true)));
            else
                config.modifyAttributes(dn,
                        storeDiffs(prevDescriptor, descriptor, new ArrayList<ModificationItem>()));
        }
    }

    private List<ModificationItem> storeDiffs(QueueDescriptor prev, QueueDescriptor desc,
                                              List<ModificationItem> mods) {
        LdapUtils.storeDiff(mods, "dicomDescription", prev.getDescription(), desc.getDescription());
        LdapUtils.storeDiff(mods, "dcmJndiName", prev.getJndiName(), desc.getJndiName());
        LdapUtils.storeDiff(mods, "dcmMaxRetries", prev.getMaxRetries(), desc.getMaxRetries(), 0);
        LdapUtils.storeDiff(mods, "dcmRetryDelay", prev.getRetryDelay(), desc.getRetryDelay());
        LdapUtils.storeDiff(mods, "dcmMaxRetryDelay", prev.getMaxRetryDelay(), desc.getMaxRetryDelay());
        LdapUtils.storeDiff(mods, "dcmMaxRetries", prev.getRetryDelayMultiplier(), desc.getRetryDelayMultiplier(), 100);
        return mods;
    }

    private void storeExporterDescriptors(String deviceDN, ArchiveDeviceExtension arcDev) throws NamingException {
        for (ExporterDescriptor descriptor : arcDev.getExporterDescriptors()) {
            String storageID = descriptor.getExporterID();
            config.createSubcontext(
                    LdapUtils.dnOf("dcmExporterID", storageID, deviceDN),
                    storeTo(descriptor, new BasicAttributes(true)));
        }
    }

    private Attributes storeTo(ExporterDescriptor descriptor, BasicAttributes attrs) {
        attrs.put("objectclass", "dcmExporter");
        attrs.put("dcmExporterID", descriptor.getExporterID());
        LdapUtils.storeNotNull(attrs, "dcmURI", descriptor.getExportURI());
        LdapUtils.storeNotNull(attrs, "dicomDescription", descriptor.getDescription());
        LdapUtils.storeNotNull(attrs, "dcmQueueName", descriptor.getQueueName());
        LdapUtils.storeNotNull(attrs, "dicomAETitle", descriptor.getAETitle());
        LdapUtils.storeNotEmpty(attrs, "dcmSchedule", descriptor.getSchedules());
        LdapUtils.storeNotEmpty(attrs, "dcmProperty", toStrings(descriptor.getProperties()));
        return attrs;
    }

    private void loadExporterDescriptors(ArchiveDeviceExtension arcdev, String deviceDN) throws NamingException {
        NamingEnumeration<SearchResult> ne = config.search(deviceDN, "(objectclass=dcmExporter)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                ExporterDescriptor desc = new ExporterDescriptor(LdapUtils.stringValue(attrs.get("dcmExporterID"), null));
                desc.setExportURI(URI.create(LdapUtils.stringValue(attrs.get("dcmURI"), null)));
                desc.setDescription(LdapUtils.stringValue(attrs.get("dicomDescription"), null));
                desc.setQueueName(LdapUtils.stringValue(attrs.get("dcmQueueName"), null));
                desc.setAETitle(LdapUtils.stringValue(attrs.get("dicomAETitle"), null));
                desc.setSchedules(toScheduleExpressions(LdapUtils.stringArray(attrs.get("dcmSchedule"))));
                desc.setProperties(LdapUtils.stringArray(attrs.get("dcmProperty")));
                arcdev.addExporterDescriptor(desc);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
    }

    private ScheduleExpression[] toScheduleExpressions(String[] ss) {
        ScheduleExpression[] schedules = new ScheduleExpression[ss.length];
        for (int i = 0; i < ss.length; i++)
            schedules[i] = ScheduleExpression.valueOf(ss[i]);
        return schedules;
    }

    private void mergeExportDescriptors(ArchiveDeviceExtension prev, ArchiveDeviceExtension arcDev, String deviceDN)
            throws NamingException {
        for (ExporterDescriptor descriptor : prev.getExporterDescriptors()) {
            String exporterID = descriptor.getExporterID();
            if (arcDev.getExporterDescriptor(exporterID) == null)
                config.destroySubcontext(LdapUtils.dnOf("dcmExporterID", exporterID, deviceDN));
        }
        for (ExporterDescriptor descriptor : arcDev.getExporterDescriptors()) {
            String exporterID = descriptor.getExporterID();
            String dn = LdapUtils.dnOf("dcmExporterID", exporterID, deviceDN);
            ExporterDescriptor prevDescriptor = prev.getExporterDescriptor(exporterID);
            if (prevDescriptor == null)
                config.createSubcontext(dn,
                        storeTo(descriptor, new BasicAttributes(true)));
            else
                config.modifyAttributes(dn,
                        storeDiffs(prevDescriptor, descriptor, new ArrayList<ModificationItem>()));
        }
    }

    private List<ModificationItem> storeDiffs(ExporterDescriptor prev, ExporterDescriptor desc,
                                              List<ModificationItem> mods) {
        LdapUtils.storeDiff(mods, "dcmURI", prev.getExportURI().toString(), desc.getExportURI().toString());
        LdapUtils.storeDiff(mods, "dicomDescription", prev.getDescription(), desc.getDescription());
        LdapUtils.storeDiff(mods, "dcmQueueName", prev.getQueueName(), desc.getQueueName());
        LdapUtils.storeDiff(mods, "dicomAETitle", prev.getAETitle(), desc.getAETitle());
        LdapUtils.storeDiff(mods, "dcmSchedule", prev.getSchedules(), desc.getSchedules());
        storeDiffProperties(mods, prev.getProperties(), desc.getProperties());
        return mods;
    }

    private void storeExportRules(Collection<ExportRule> exportRules, String parentDN) throws NamingException {
        for (ExportRule rule : exportRules) {
            String cn = rule.getCommonName();
            config.createSubcontext(
                    LdapUtils.dnOf("cn", cn, parentDN),
                    storeTo(rule, new BasicAttributes(true)));
        }
    }

    private Attributes storeTo(ExportRule rule, BasicAttributes attrs) {
        attrs.put("objectclass", "dcmExportRule");
        attrs.put("cn", rule.getCommonName());
        LdapUtils.storeNotEmpty(attrs, "dcmSchedule", rule.getSchedules());
        LdapUtils.storeNotEmpty(attrs, "dcmProperty", toStrings(rule.getConditions().getMap()));
        LdapUtils.storeNotEmpty(attrs, "dcmExporterID", rule.getExporterIDs());
        LdapUtils.storeNotNull(attrs, "dcmEntity", rule.getEntity());
        LdapUtils.storeNotNull(attrs, "dcmDuration", rule.getExportDelay());
        return attrs;
    }

    private void loadExportRules(Collection<ExportRule> exportRules, String parentDN) throws NamingException {
        NamingEnumeration<SearchResult> ne = config.search(parentDN, "(objectclass=dcmExportRule)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                ExportRule rule = new ExportRule(LdapUtils.stringValue(attrs.get("cn"), null));
                rule.setSchedules(toScheduleExpressions(LdapUtils.stringArray(attrs.get("dcmSchedule"))));
                rule.setConditions(new Conditions(LdapUtils.stringArray(attrs.get("dcmProperty"))));
                rule.setExporterIDs(LdapUtils.stringArray(attrs.get("dcmExporterID")));
                rule.setEntity(LdapUtils.enumValue(Entity.class, attrs.get("dcmEntity"), null));
                rule.setExportDelay(toDuration(LdapUtils.stringValue(attrs.get("dcmDuration"), null)));
                exportRules.add(rule);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
    }

    private Duration toDuration(String s) {
        return s != null ? Duration.parse(s) : null;
    }

    private void mergeExportRules(Collection<ExportRule> prevRules, Collection<ExportRule> rules, String parentDN)
            throws NamingException {
        for (ExportRule prevRule : prevRules) {
            String cn = prevRule.getCommonName();
            if (findExportRuleByCN(rules, cn) == null)
                config.destroySubcontext(LdapUtils.dnOf("cn", cn, parentDN));
        }
        for (ExportRule rule : rules) {
            String cn = rule.getCommonName();
            String dn = LdapUtils.dnOf("cn", cn, parentDN);
            ExportRule prevRule = findExportRuleByCN(prevRules, cn);
            if (prevRule == null)
                config.createSubcontext(dn, storeTo(rule, new BasicAttributes(true)));
            else
                config.modifyAttributes(dn, storeDiffs(prevRule, rule, new ArrayList<ModificationItem>()));
        }
    }

    private List<ModificationItem> storeDiffs(ExportRule prev, ExportRule rule, ArrayList<ModificationItem> mods) {
        LdapUtils.storeDiff(mods, "dcmSchedule", prev.getSchedules(), rule.getSchedules());
        storeDiffProperties(mods, prev.getConditions().getMap(), rule.getConditions().getMap());
        LdapUtils.storeDiff(mods, "dcmExporterID", prev.getExporterIDs(), rule.getExporterIDs());
        LdapUtils.storeDiff(mods, "dcmEntity", prev.getEntity(), rule.getEntity());
        LdapUtils.storeDiff(mods, "dcmDuration", prev.getExportDelay(), rule.getExportDelay());
        return mods;
    }

    private ExportRule findExportRuleByCN(Collection<ExportRule> rules, String cn) {
        for (ExportRule rule : rules)
            if (rule.getCommonName().equals(cn))
                return rule;
        return null;
    }

    private void storeCompressionRules(Collection<ArchiveCompressionRule> rules, String parentDN)
            throws NamingException {
        for (ArchiveCompressionRule rule : rules) {
            String cn = rule.getCommonName();
            config.createSubcontext(
                    LdapUtils.dnOf("cn", cn, parentDN),
                    storeTo(rule, new BasicAttributes(true)));
        }
    }

    private Attributes storeTo(ArchiveCompressionRule rule, BasicAttributes attrs) {
        attrs.put("objectclass", "dcmArchiveCompressionRule");
        attrs.put("cn", rule.getCommonName());
        LdapUtils.storeNotEmpty(attrs, "dcmProperty", toStrings(rule.getConditions().getMap()));
        LdapUtils.storeNotNull(attrs, "dicomTransferSyntax", rule.getTransferSyntax());
        LdapUtils.storeNotEmpty(attrs, "dcmImageWriteParam", rule.getImageWriteParams());
        LdapUtils.storeNotDef(attrs, "dcmRulePriority", rule.getPriority(), 0);
        return attrs;
    }

    private void loadCompressionRules(Collection<ArchiveCompressionRule> rules, String parentDN)
            throws NamingException {
        NamingEnumeration<SearchResult> ne = config.search(parentDN, "(objectclass=dcmArchiveCompressionRule)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                ArchiveCompressionRule rule = new ArchiveCompressionRule(LdapUtils.stringValue(attrs.get("cn"), null));
                rule.setConditions(new Conditions(LdapUtils.stringArray(attrs.get("dcmProperty"))));
                rule.setTransferSyntax(LdapUtils.stringValue(attrs.get("dicomTransferSyntax"), null));
                rule.setImageWriteParams(Property.valueOf(LdapUtils.stringArray(attrs.get("dcmImageWriteParam"))));
                rule.setPriority(LdapUtils.intValue(attrs.get("dcmRulePriority"), 0));
                rules.add(rule);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
    }

    private void mergeCompressionRules(
            Collection<ArchiveCompressionRule> prevRules, Collection<ArchiveCompressionRule> rules, String parentDN)
            throws NamingException {
        for (ArchiveCompressionRule prevRule : prevRules) {
            String cn = prevRule.getCommonName();
            if (findCompressionRuleByCN(rules, cn) == null)
                config.destroySubcontext(LdapUtils.dnOf("cn", cn, parentDN));
        }
        for (ArchiveCompressionRule rule : rules) {
            String cn = rule.getCommonName();
            String dn = LdapUtils.dnOf("cn", cn, parentDN);
            ArchiveCompressionRule prevRule = findCompressionRuleByCN(prevRules, cn);
            if (prevRule == null)
                config.createSubcontext(dn, storeTo(rule, new BasicAttributes(true)));
            else
                config.modifyAttributes(dn, storeDiffs(prevRule, rule, new ArrayList<ModificationItem>()));
        }
    }


    private void mergeAttributeCoercions(
            Collection<ArchiveAttributeCoercion> prevCoercions,
            Collection<ArchiveAttributeCoercion> coercions,
            String parentDN) throws NamingException {
        for (ArchiveAttributeCoercion prev : prevCoercions) {
            String cn = prev.getCommonName();
            if (findAttributeCoercionByCN(coercions, cn) == null)
                config.destroySubcontext(LdapUtils.dnOf("cn", cn, parentDN));
        }
        for (ArchiveAttributeCoercion coercion : coercions) {
            String cn = coercion.getCommonName();
            String dn = LdapUtils.dnOf("cn", cn, parentDN);
            ArchiveAttributeCoercion prev = findAttributeCoercionByCN(prevCoercions, cn);
            if (prev == null)
                config.createSubcontext(dn, storeTo(coercion, new BasicAttributes(true)));
            else
                config.modifyAttributes(dn, storeDiffs(prev, coercion, new ArrayList<ModificationItem>()));
        }
    }

    private List<ModificationItem> storeDiffs(
            ArchiveCompressionRule prev, ArchiveCompressionRule rule, ArrayList<ModificationItem> mods) {
        storeDiffProperties(mods, prev.getConditions().getMap(), rule.getConditions().getMap());
        LdapUtils.storeDiff(mods, "dicomTransferSyntax", prev.getTransferSyntax(), rule.getTransferSyntax());
        LdapUtils.storeDiff(mods, "dcmImageWriteParam", prev.getImageWriteParams(), rule.getImageWriteParams());
        LdapUtils.storeDiff(mods, "dcmRulePriority", prev.getPriority(), rule.getPriority(), 0);
        return mods;
    }

    private ArchiveCompressionRule findCompressionRuleByCN(Collection<ArchiveCompressionRule> rules, String cn) {
        for (ArchiveCompressionRule rule : rules)
            if (rule.getCommonName().equals(cn))
                return rule;
        return null;
    }

    private void storeQueryRetrieveViews(String deviceDN, ArchiveDeviceExtension arcDev) throws NamingException {
        for (QueryRetrieveView view : arcDev.getQueryRetrieveViews())
            config.createSubcontext(
                    LdapUtils.dnOf("dcmQueryRetrieveViewID", view.getViewID(), deviceDN),
                    storeTo(view, new BasicAttributes(true)));
    }

    private Attributes storeTo(QueryRetrieveView qrView, BasicAttributes attrs) {
        attrs.put("objectclass", "dcmQueryRetrieveView");
        attrs.put("dcmQueryRetrieveViewID", qrView.getViewID());
        LdapUtils.storeNotEmpty(attrs, "dcmShowInstancesRejectedByCode", qrView.getShowInstancesRejectedByCodes());
        LdapUtils.storeNotEmpty(attrs, "dcmHideRejectionNoteWithCode", qrView.getHideRejectionNotesWithCodes());
        LdapUtils.storeNotDef(attrs, "dcmHideNotRejectedInstances", qrView.isHideNotRejectedInstances(), false);
        return attrs;
    }

    private void loadQueryRetrieveViews(ArchiveDeviceExtension arcdev, String deviceDN) throws NamingException {
        ArrayList<QueryRetrieveView> views = new ArrayList<>();
        NamingEnumeration<SearchResult> ne = config.search(deviceDN, "(objectclass=dcmQueryRetrieveView)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                QueryRetrieveView view = new QueryRetrieveView();
                view.setViewID(LdapUtils.stringValue(attrs.get("dcmQueryRetrieveViewID"), null));
                view.setShowInstancesRejectedByCodes(
                        LdapUtils.codeArray(attrs.get("dcmShowInstancesRejectedByCode")));
                view.setHideRejectionNotesWithCodes(
                        LdapUtils.codeArray(attrs.get("dcmHideRejectionNoteWithCode")));
                view.setHideNotRejectedInstances(
                        LdapUtils.booleanValue(attrs.get("dcmHideNotRejectedInstances"), false));
                views.add(view);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
        arcdev.setQueryRetrieveViews(views.toArray(new QueryRetrieveView[views.size()]));
    }

    private void mergeQueryRetrieveViews(ArchiveDeviceExtension prev, ArchiveDeviceExtension arcDev, String deviceDN)
            throws NamingException {
        for (QueryRetrieveView entry : prev.getQueryRetrieveViews()) {
            String viewID = entry.getViewID();
            if (arcDev.getQueryRetrieveView(viewID) == null)
                config.destroySubcontext( LdapUtils.dnOf("dcmQueryRetrieveViewID", viewID, deviceDN));
        }
        for (QueryRetrieveView entryNew : arcDev.getQueryRetrieveViews()) {
            String viewID = entryNew.getViewID();
            String dn = LdapUtils.dnOf("dcmQueryRetrieveViewID", viewID, deviceDN);
            QueryRetrieveView entryOld = prev.getQueryRetrieveView(viewID);
            if (entryOld == null) {
                config.createSubcontext(dn, storeTo(entryNew, new BasicAttributes(true)));
            } else{
                config.modifyAttributes(dn, storeDiffs(entryOld, entryNew, new ArrayList<ModificationItem>()));
            }
        }
    }

    private List<ModificationItem> storeDiffs(
            QueryRetrieveView prev, QueryRetrieveView view, ArrayList<ModificationItem> mods) {
        LdapUtils.storeDiff(mods, "dcmShowInstancesRejectedByCode",
                prev.getShowInstancesRejectedByCodes(),
                view.getShowInstancesRejectedByCodes());
        LdapUtils.storeDiff(mods, "dcmHideRejectionNoteWithCode",
                prev.getHideRejectionNotesWithCodes(),
                view.getHideRejectionNotesWithCodes());
        LdapUtils.storeDiff(mods, "dcmHideNotRejectedInstances",
                prev.isHideNotRejectedInstances(),
                view.isHideNotRejectedInstances(),
                false);
        return mods;
    }

    private void storeAttributeCoercions(Collection<ArchiveAttributeCoercion> coercions, String parentDN)
            throws NamingException {
        for (ArchiveAttributeCoercion coercion : coercions) {
            String cn = coercion.getCommonName();
            config.createSubcontext(
                    LdapUtils.dnOf("cn", cn, parentDN),
                    storeTo(coercion, new BasicAttributes(true)));
        }
    }

    private Attributes storeTo(ArchiveAttributeCoercion coercion, BasicAttributes attrs) {
        attrs.put("objectclass", "dcmArchiveAttributeCoercion");
        attrs.put("cn", coercion.getCommonName());
        LdapUtils.storeNotNull(attrs, "dcmDIMSE", coercion.getDIMSE());
        LdapUtils.storeNotNull(attrs, "dicomTransferRole", coercion.getRole());
        LdapUtils.storeNotEmpty(attrs, "dcmHostname", coercion.getHostNames());
        LdapUtils.storeNotEmpty(attrs, "dcmAETitle", coercion.getAETitles());
        LdapUtils.storeNotEmpty(attrs, "dcmSOPClass", coercion.getSOPClasses());
        LdapUtils.storeNotNull(attrs, "dcmURI", coercion.getXSLTStylesheetURI());
        LdapUtils.storeNotDef(attrs, "dcmNoKeywords", coercion.isNoKeywords(), false);
        LdapUtils.storeNotDef(attrs, "dcmRulePriority", coercion.getPriority(), 0);
        return attrs;
    }

    private void loadAttributeCoercions(Collection<ArchiveAttributeCoercion> coercions, String parentDN)
            throws NamingException {
        NamingEnumeration<SearchResult> ne = config.search(parentDN, "(objectclass=dcmArchiveAttributeCoercion)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                ArchiveAttributeCoercion coercion =
                        new ArchiveAttributeCoercion(LdapUtils.stringValue(attrs.get("cn"), null));
                coercion.setDIMSE(Dimse.valueOf(LdapUtils.stringValue(attrs.get("dcmDIMSE"), null)));
                coercion.setRole(TransferCapability.Role.valueOf(
                        LdapUtils.stringValue(attrs.get("dicomTransferRole"), null)));
                coercion.setHostNames(LdapUtils.stringArray(attrs.get("dcmHostname")));
                coercion.setAETitles(LdapUtils.stringArray(attrs.get("dcmAETitle")));
                coercion.setSOPClasses(LdapUtils.stringArray(attrs.get("dcmSOPClass")));
                coercion.setXSLTStylesheetURI(LdapUtils.stringValue(attrs.get("dcmURI"), null));
                coercion.setNoKeywords(LdapUtils.booleanValue(attrs.get("dcmNoKeywords"), false));
                coercion.setPriority(LdapUtils.intValue(attrs.get("dcmRulePriority"), 0));
                coercions.add(coercion);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
    }


    private List<ModificationItem> storeDiffs(
            ArchiveAttributeCoercion prev, ArchiveAttributeCoercion coercion, ArrayList<ModificationItem> mods) {
        LdapUtils.storeDiff(mods, "dcmDIMSE", prev.getDIMSE(), coercion.getDIMSE());
        LdapUtils.storeDiff(mods, "dicomTransferRole", prev.getRole(), coercion.getRole());
        LdapUtils.storeDiff(mods, "dcmHostname", prev.getHostNames(), coercion.getHostNames());
        LdapUtils.storeDiff(mods, "dcmAETitle", prev.getAETitles(), coercion.getAETitles());
        LdapUtils.storeDiff(mods, "dcmSOPClass", prev.getSOPClasses(), coercion.getSOPClasses());
        LdapUtils.storeDiff(mods, "dcmURI", prev.getXSLTStylesheetURI(), coercion.getXSLTStylesheetURI());
        LdapUtils.storeDiff(mods, "dcmNoKeywords", prev.isNoKeywords(), coercion.isNoKeywords(), false);
        LdapUtils.storeDiff(mods, "dcmRulePriority", prev.getPriority(), coercion.getPriority(), 0);
        return mods;
    }

    private ArchiveAttributeCoercion findAttributeCoercionByCN(
            Collection<ArchiveAttributeCoercion> coercions, String cn) {
        for (ArchiveAttributeCoercion coercion : coercions)
            if (coercion.getCommonName().equals(cn))
                return coercion;
        return null;
    }

    private void storeRejectNotes(String deviceDN, ArchiveDeviceExtension arcDev) throws NamingException {
        for (RejectionNote rejectionNote : arcDev.getRejectionNotes()) {
            String id = rejectionNote.getRejectionNoteLabel();
            config.createSubcontext(
                    LdapUtils.dnOf("dcmRejectionNoteLabel", id, deviceDN),
                    storeTo(rejectionNote, new BasicAttributes(true)));
        }
    }

    private Attributes storeTo(RejectionNote rjNote, BasicAttributes attrs) {
        attrs.put("objectclass", "dcmRejectionNote");
        attrs.put("dcmRejectionNoteLabel", rjNote.getRejectionNoteLabel());
        LdapUtils.storeNotNull(attrs, "dcmRejectionNoteCode", rjNote.getRejectionNoteCode());
        LdapUtils.storeNotDef(attrs, "dcmRevokeRejection", rjNote.isRevokeRejection(), false);
        LdapUtils.storeNotNull(attrs, "dcmAcceptPreviousRejectedInstance", rjNote.getAcceptPreviousRejectedInstance());
        LdapUtils.storeNotEmpty(attrs, "dcmOverwritePreviousRejection", rjNote.getOverwritePreviousRejection());
        LdapUtils.storeNotNull(attrs, "dcmDeleteRejectedInstanceDelay", rjNote.getDeleteRejectedInstanceDelay());
        LdapUtils.storeNotNull(attrs, "dcmDeleteRejectionNoteDelay", rjNote.getDeleteRejectionNoteDelay());
        return attrs;
    }

    private void loadRejectNotes(ArchiveDeviceExtension arcdev, String deviceDN) throws NamingException {
        NamingEnumeration<SearchResult> ne = config.search(deviceDN, "(objectclass=dcmRejectionNote)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                RejectionNote rjNote = new RejectionNote(LdapUtils.stringValue(attrs.get("dcmRejectionNoteLabel"), null));
                rjNote.setRejectionNoteCode(LdapUtils.codeValue(attrs.get("dcmRejectionNoteCode")));
                rjNote.setRevokeRejection(LdapUtils.booleanValue(attrs.get("dcmRevokeRejection"), false));
                rjNote.setAcceptPreviousRejectedInstance(LdapUtils.enumValue(
                        RejectionNote.AcceptPreviousRejectedInstance.class,
                        attrs.get("dcmAcceptPreviousRejectedInstance"),
                        null));
                rjNote.setOverwritePreviousRejection(LdapUtils.codeArray(attrs.get("dcmOverwritePreviousRejection")));
                rjNote.setDeleteRejectedInstanceDelay(
                        toDuration(LdapUtils.stringValue(attrs.get("dcmDeleteRejectedInstanceDelay"), null)));
                rjNote.setDeleteRejectionNoteDelay(
                        toDuration(LdapUtils.stringValue(attrs.get("dcmDeleteRejectionNoteDelay"), null)));
                arcdev.addRejectionNote(rjNote);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
    }

    private void mergeRejectNotes(ArchiveDeviceExtension prev, ArchiveDeviceExtension arcDev, String deviceDN)
            throws NamingException {
        for (RejectionNote entry : prev.getRejectionNotes()) {
            String rjNoteID = entry.getRejectionNoteLabel();
            if (arcDev.getRejectionNote(rjNoteID) == null)
                config.destroySubcontext(LdapUtils.dnOf("dcmRejectionNoteLabel", rjNoteID, deviceDN));
        }
        for (RejectionNote entryNew : arcDev.getRejectionNotes()) {
            String rjNoteID = entryNew.getRejectionNoteLabel();
            String dn = LdapUtils.dnOf("dcmRejectionNoteLabel", rjNoteID, deviceDN);
            RejectionNote entryOld = prev.getRejectionNote(rjNoteID);
            if (entryOld == null) {
                config.createSubcontext(dn, storeTo(entryNew, new BasicAttributes(true)));
            } else{
                config.modifyAttributes(dn, storeDiffs(entryOld, entryNew, new ArrayList<ModificationItem>()));
            }
        }
    }

    private List<ModificationItem> storeDiffs(RejectionNote prev, RejectionNote rjNote,
                                              ArrayList<ModificationItem> mods) {
        LdapUtils.storeDiff(mods, "dcmRejectionNoteCode", prev.getRejectionNoteCode(), rjNote.getRejectionNoteCode());
        LdapUtils.storeDiff(mods, "dcmRevokeRejection", prev.isRevokeRejection(), rjNote.isRevokeRejection(), false);
        LdapUtils.storeDiff(mods, "dcmAcceptPreviousRejectedInstance",
                prev.getAcceptPreviousRejectedInstance(),
                rjNote.getAcceptPreviousRejectedInstance());
        LdapUtils.storeDiff(mods, "dcmOverwritePreviousRejection",
                prev.getOverwritePreviousRejection(),
                rjNote.getOverwritePreviousRejection());
        LdapUtils.storeDiff(mods, "dcmDeleteRejectedInstanceDelay",
                prev.getDeleteRejectedInstanceDelay(),
                rjNote.getDeleteRejectedInstanceDelay());
        LdapUtils.storeDiff(mods, "dcmDeleteRejectionNoteDelay",
                prev.getDeleteRejectionNoteDelay(),
                rjNote.getDeleteRejectionNoteDelay());
        return mods;
    }

}
