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
import org.dcm4che3.conf.ldap.LdapDicomConfiguration;
import org.dcm4che3.conf.ldap.LdapDicomConfigurationExtension;
import org.dcm4che3.conf.ldap.LdapUtils;
import org.dcm4che3.data.*;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Dimse;
import org.dcm4che3.net.TransferCapability;
import org.dcm4che3.util.ByteUtils;
import org.dcm4che3.util.Property;
import org.dcm4che3.util.TagUtils;
import org.dcm4chee.arc.conf.*;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.*;
import javax.naming.directory.Attributes;
import java.net.URI;
import java.security.cert.CertificateException;
import java.time.LocalTime;
import java.time.Period;
import java.util.*;
import java.util.regex.Pattern;

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
        LdapUtils.storeNotNullOrDef(attrs, "dcmFuzzyAlgorithmClass", ext.getFuzzyAlgorithmClass(), null);
        LdapUtils.storeNotEmpty(attrs, "dcmSeriesMetadataStorageID", ext.getSeriesMetadataStorageIDs());
        LdapUtils.storeNotNullOrDef(attrs, "dcmSeriesMetadataDelay", ext.getSeriesMetadataDelay(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmSeriesMetadataPollingInterval", ext.getSeriesMetadataPollingInterval(), null);
        LdapUtils.storeNotDef(attrs, "dcmSeriesMetadataFetchSize", ext.getSeriesMetadataFetchSize(), 100);
        LdapUtils.storeNotNullOrDef(attrs, "dcmPurgeInstanceRecordsDelay", ext.getPurgeInstanceRecordsDelay(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmPurgeInstanceRecordsPollingInterval", ext.getPurgeInstanceRecordsPollingInterval(), null);
        LdapUtils.storeNotDef(attrs, "dcmPurgeInstanceRecordsFetchSize", ext.getPurgeInstanceRecordsFetchSize(), 100);
        LdapUtils.storeNotNullOrDef(attrs, "dcmOverwritePolicy", ext.getOverwritePolicy(), OverwritePolicy.NEVER);
        LdapUtils.storeNotNullOrDef(attrs, "dcmBulkDataSpoolDirectory",
                ext.getBulkDataSpoolDirectory(), ArchiveDeviceExtension.JBOSS_SERVER_TEMP_DIR);
        LdapUtils.storeNotNullOrDef(attrs, "dcmQueryRetrieveViewID", ext.getQueryRetrieveViewID(), null);
        LdapUtils.storeNotDef(attrs, "dcmPersonNameComponentOrderInsensitiveMatching",
                ext.isPersonNameComponentOrderInsensitiveMatching(), false);
        LdapUtils.storeNotDef(attrs, "dcmSendPendingCGet", ext.isSendPendingCGet(), false);
        LdapUtils.storeNotNullOrDef(attrs, "dcmSendPendingCMoveInterval", ext.getSendPendingCMoveInterval(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmFallbackCMoveSCP", ext.getFallbackCMoveSCP(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmFallbackCMoveSCPDestination", ext.getFallbackCMoveSCPDestination(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmFallbackCMoveSCPLeadingCFindSCP", ext.getFallbackCMoveSCPLeadingCFindSCP(), null);
        LdapUtils.storeNotDef(attrs, "dcmFallbackCMoveSCPRetries", ext.getFallbackCMoveSCPRetries(), 0);
        LdapUtils.storeNotNullOrDef(attrs, "dcmAltCMoveSCP", ext.getAlternativeCMoveSCP(), null);
        storeNotEmptyTags(attrs, "dcmDiffStudiesIncludefieldAll", ext.getDiffStudiesIncludefieldAll());
        LdapUtils.storeNotNullOrDef(attrs, "dcmWadoSR2HtmlTemplateURI", ext.getWadoSR2HtmlTemplateURI(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmWadoSR2TextTemplateURI", ext.getWadoSR2TextTemplateURI(), null);
        LdapUtils.storeNotNullOrDef(attrs, "hl7PatientUpdateTemplateURI", ext.getPatientUpdateTemplateURI(), null);
        LdapUtils.storeNotNullOrDef(attrs, "hl7ImportReportTemplateURI", ext.getImportReportTemplateURI(), null);
        LdapUtils.storeNotNullOrDef(attrs, "hl7ScheduleProcedureTemplateURI", ext.getScheduleProcedureTemplateURI(), null);
        LdapUtils.storeNotNullOrDef(attrs, "hl7LogFilePattern", ext.getHl7LogFilePattern(), null);
        LdapUtils.storeNotNullOrDef(attrs, "hl7ErrorLogFilePattern", ext.getHl7ErrorLogFilePattern(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmUnzipVendorDataToURI", ext.getUnzipVendorDataToURI(), null);
        LdapUtils.storeNotEmpty(attrs, "dcmWadoSupportedSRClasses", ext.getWadoSupportedSRClasses());
        LdapUtils.storeNotDef(attrs, "dcmQueryFetchSize", ext.getQueryFetchSize(), 100);
        LdapUtils.storeNotDef(attrs, "dcmQidoMaxNumberOfResults", ext.getQidoMaxNumberOfResults(), 100);
        LdapUtils.storeNotEmpty(attrs, "dcmFwdMppsDestination", ext.getMppsForwardDestinations());
        LdapUtils.storeNotEmpty(attrs, "dcmIanDestination", ext.getIanDestinations());
        LdapUtils.storeNotNullOrDef(attrs, "dcmIanDelay", ext.getIanDelay(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmIanTimeout", ext.getIanTimeout(), null);
        LdapUtils.storeNotDef(attrs, "dcmIanOnTimeout", ext.isIanOnTimeout(), false);
        LdapUtils.storeNotNullOrDef(attrs, "dcmIanTaskPollingInterval", ext.getIanTaskPollingInterval(), null);
        LdapUtils.storeNotDef(attrs, "dcmIanTaskFetchSize", ext.getIanTaskFetchSize(), 100);
        LdapUtils.storeNotNullOrDef(attrs, "dcmExportTaskPollingInterval", ext.getExportTaskPollingInterval(), null);
        LdapUtils.storeNotDef(attrs, "dcmExportTaskFetchSize", ext.getExportTaskFetchSize(), 5);
        LdapUtils.storeNotNullOrDef(attrs, "dcmPurgeStoragePollingInterval", ext.getPurgeStoragePollingInterval(), null);
        LdapUtils.storeNotDef(attrs, "dcmPurgeStorageFetchSize", ext.getPurgeStorageFetchSize(), 100);
        LdapUtils.storeNotNullOrDef(attrs, "dcmDeleteRejectedPollingInterval", ext.getDeleteRejectedPollingInterval(), null);
        LdapUtils.storeNotDef(attrs, "dcmDeleteRejectedFetchSize", ext.getDeleteRejectedFetchSize(), 100);
        LdapUtils.storeNotDef(attrs, "dcmDeleteStudyBatchSize", ext.getDeleteStudyBatchSize(), 10);
        LdapUtils.storeNotDef(attrs, "dcmDeletePatientOnDeleteLastStudy",
                ext.isDeletePatientOnDeleteLastStudy(), false);
        LdapUtils.storeNotNullOrDef(attrs, "dcmMaxAccessTimeStaleness", ext.getMaxAccessTimeStaleness(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmAECacheStaleTimeout", ext.getAECacheStaleTimeout(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmLeadingCFindSCPQueryCacheStaleTimeout", ext.getLeadingCFindSCPQueryCacheStaleTimeout(), null);
        LdapUtils.storeNotDef(attrs, "dcmLeadingCFindSCPQueryCacheSize", ext.getLeadingCFindSCPQueryCacheSize(), 10);
        LdapUtils.storeNotNullOrDef(attrs, "dcmAuditSpoolDirectory",
                ext.getAuditSpoolDirectory(), ArchiveDeviceExtension.JBOSS_SERVER_TEMP_DIR);
        LdapUtils.storeNotNullOrDef(attrs, "dcmAuditPollingInterval", ext.getAuditPollingInterval(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmAuditAggregateDuration", ext.getAuditAggregateDuration(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmStowSpoolDirectory",
                ext.getStowSpoolDirectory(), ArchiveDeviceExtension.JBOSS_SERVER_TEMP_DIR);
        LdapUtils.storeNotNullOrDef(attrs, "dcmPurgeQueueMessagePollingInterval",
                ext.getPurgeQueueMessagePollingInterval(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmWadoSpoolDirectory",
                ext.getWadoSpoolDirectory(), ArchiveDeviceExtension.JBOSS_SERVER_TEMP_DIR);
        LdapUtils.storeNotEmpty(attrs, "dcmHideSPSWithStatusFromMWL", ext.getHideSPSWithStatusFrom());
        LdapUtils.storeNotNullOrDef(attrs, "dcmRejectExpiredStudiesPollingInterval",
                ext.getRejectExpiredStudiesPollingInterval(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmRejectExpiredStudiesPollingStartTime",
                ext.getRejectExpiredStudiesPollingStartTime(), null);
        LdapUtils.storeNotDef(attrs, "dcmRejectExpiredStudiesFetchSize", ext.getRejectExpiredStudiesFetchSize(), 0);
        LdapUtils.storeNotDef(attrs, "dcmRejectExpiredSeriesFetchSize", ext.getRejectExpiredSeriesFetchSize(), 0);
        LdapUtils.storeNotNullOrDef(attrs, "dcmRejectExpiredStudiesAETitle", ext.getRejectExpiredStudiesAETitle(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmFallbackCMoveSCPStudyOlderThan", ext.getFallbackCMoveSCPStudyOlderThan(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmStorePermissionServiceURL", ext.getStorePermissionServiceURL(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmStorePermissionServiceResponsePattern",
                ext.getStorePermissionServiceResponsePattern(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmStorePermissionCacheStaleTimeout",
                ext.getStorePermissionCacheStaleTimeout(), null);
        LdapUtils.storeNotDef(attrs, "dcmStorePermissionCacheSize", ext.getStorePermissionCacheSize(), 10);
        LdapUtils.storeNotNullOrDef(attrs, "dcmMergeMWLCacheStaleTimeout", ext.getMergeMWLCacheStaleTimeout(), null);
        LdapUtils.storeNotDef(attrs, "dcmMergeMWLCacheSize", ext.getMergeMWLCacheSize(), 10);
        LdapUtils.storeNotDef(attrs, "dcmStoreUpdateDBMaxRetries", ext.getStoreUpdateDBMaxRetries(), 1);
        LdapUtils.storeNotDef(attrs, "dcmStoreUpdateDBMaxRetryDelay", ext.getStoreUpdateDBMaxRetryDelay(), 1000);
        LdapUtils.storeNotNullOrDef(attrs, "dcmAllowRejectionForDataRetentionPolicyExpired",
                ext.getAllowRejectionForDataRetentionPolicyExpired(), AllowRejectionForDataRetentionPolicyExpired.STUDY_RETENTION_POLICY);
        LdapUtils.storeNotNullOrDef(attrs, "dcmAcceptMissingPatientID",
                ext.getAcceptMissingPatientID(), AcceptMissingPatientID.CREATE);
        LdapUtils.storeNotNullOrDef(attrs, "dcmAllowDeleteStudyPermanently",
                ext.getAllowDeleteStudyPermanently(), AllowDeleteStudyPermanently.REJECTED);
        LdapUtils.storeNotNullOrDef(attrs, "dcmStorePermissionServiceExpirationDatePattern",
                ext.getStorePermissionServiceExpirationDatePattern(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmShowPatientInfoInSystemLog",
                ext.getShowPatientInfoInSystemLog(), ShowPatientInfo.PLAIN_TEXT);
        LdapUtils.storeNotNullOrDef(attrs, "dcmShowPatientInfoInAuditLog",
                ext.getShowPatientInfoInAuditLog(), ShowPatientInfo.PLAIN_TEXT);
        LdapUtils.storeNotNullOrDef(attrs, "dcmPurgeStgCmtCompletedDelay", ext.getPurgeStgCmtCompletedDelay(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmPurgeStgCmtPollingInterval", ext.getPurgeStgCmtPollingInterval(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmDefaultCharacterSet", ext.getDefaultCharacterSet(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmStorePermissionServiceErrorCommentPattern", ext.getStorePermissionServiceErrorCommentPattern(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmStorePermissionServiceErrorCodePattern", ext.getStorePermissionServiceErrorCodePattern(), null);
        LdapUtils.storeNotEmpty(attrs, "dcmRetrieveAET", ext.getRetrieveAETitles());
        LdapUtils.storeNotNullOrDef(attrs, "dcmExternalRetrieveAEDestination", ext.getExternalRetrieveAEDestination(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmXDSiImagingDocumentSourceAETitle", ext.getXDSiImagingDocumentSourceAETitle(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmRemapRetrieveURL", ext.getRemapRetrieveURL(), null);
        LdapUtils.storeNotDef(attrs, "dcmValidateCallingAEHostname", ext.isValidateCallingAEHostname(), false);
        LdapUtils.storeNotNullOrDef(attrs, "hl7PSUSendingApplication", ext.getHl7PSUSendingApplication(), null);
        LdapUtils.storeNotEmpty(attrs, "hl7PSUReceivingApplication", ext.getHl7PSUReceivingApplications());
        LdapUtils.storeNotNullOrDef(attrs, "hl7PSUDelay", ext.getHl7PSUDelay(), null);
        LdapUtils.storeNotNullOrDef(attrs, "hl7PSUTimeout", ext.getHl7PSUTimeout(), null);
        LdapUtils.storeNotDef(attrs, "hl7PSUOnTimeout", ext.isHl7PSUOnTimeout(), false);
        LdapUtils.storeNotNullOrDef(attrs, "hl7PSUTaskPollingInterval", ext.getHl7PSUTaskPollingInterval(), null);
        LdapUtils.storeNotDef(attrs, "hl7PSUTaskFetchSize", ext.getHl7PSUTaskFetchSize(), 100);
        LdapUtils.storeNotDef(attrs, "hl7PSUMWL", ext.isHl7PSUMWL(), false);
        LdapUtils.storeNotNullOrDef(attrs, "dcmAcceptConflictingPatientID", ext.getAcceptConflictingPatientID(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmAuditRecordRepositoryURL", ext.getAuditRecordRepositoryURL(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmElasticSearchURL", ext.getElasticSearchURL(), null);
        LdapUtils.storeNotNullOrDef(attrs,"dcmAudit2JsonFhirTemplateURI", ext.getAudit2JsonFhirTemplateURI(), null);
        LdapUtils.storeNotNullOrDef(attrs,"dcmAudit2XmlFhirTemplateURI", ext.getAudit2XmlFhirTemplateURI(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmCopyMoveUpdatePolicy", ext.getCopyMoveUpdatePolicy(), null);
        LdapUtils.storeNotDef(attrs, "hl7TrackChangedPatientID", ext.isHl7TrackChangedPatientID(), true);
        LdapUtils.storeNotNullOrDef(attrs, "dcmInvokeImageDisplayPatientURL", ext.getInvokeImageDisplayPatientURL(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmInvokeImageDisplayStudyURL", ext.getInvokeImageDisplayStudyURL(), null);
        LdapUtils.storeNotNullOrDef(attrs, "hl7ADTSendingApplication", ext.getHl7ADTSendingApplication(), null);
        LdapUtils.storeNotEmpty(attrs, "hl7ADTReceivingApplication", ext.getHl7ADTReceivingApplication());
        LdapUtils.storeNotNullOrDef(attrs, "hl7ScheduledProtocolCodeInOrder",
                ext.getHl7ScheduledProtocolCodeInOrder(), ScheduledProtocolCodeInOrder.OBR_4_4);
        LdapUtils.storeNotNullOrDef(attrs, "hl7ScheduledStationAETInOrder", ext.getHl7ScheduledStationAETInOrder(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmAuditUnknownStudyInstanceUID",
                ext.getAuditUnknownStudyInstanceUID(), ArchiveDeviceExtension.AUDIT_UNKNOWN_STUDY_INSTANCE_UID);
        LdapUtils.storeNotNullOrDef(attrs, "dcmAuditUnknownPatientID",
                ext.getAuditUnknownPatientID(), ArchiveDeviceExtension.AUDIT_UNKNOWN_PATIENT_ID);
    }

    @Override
    protected void loadFrom(Device device, Attributes attrs) throws NamingException, CertificateException {
        if (!LdapUtils.hasObjectClass(attrs, "dcmArchiveDevice"))
            return;

        ArchiveDeviceExtension ext = new ArchiveDeviceExtension();
        device.addDeviceExtension(ext);
        ext.setFuzzyAlgorithmClass(LdapUtils.stringValue(attrs.get("dcmFuzzyAlgorithmClass"), null));
        ext.setSeriesMetadataStorageIDs(LdapUtils.stringArray(attrs.get("dcmSeriesMetadataStorageID")));
        ext.setSeriesMetadataDelay(toDuration(attrs.get("dcmSeriesMetadataDelay"), null));
        ext.setSeriesMetadataPollingInterval(toDuration(attrs.get("dcmSeriesMetadataPollingInterval"), null));
        ext.setSeriesMetadataFetchSize(LdapUtils.intValue(attrs.get("dcmSeriesMetadataFetchSize"), 100));
        ext.setPurgeInstanceRecordsDelay(toDuration(attrs.get("dcmPurgeInstanceRecordsDelay"), null));
        ext.setPurgeInstanceRecordsPollingInterval(toDuration(attrs.get("dcmPurgeInstanceRecordsPollingInterval"), null));
        ext.setPurgeInstanceRecordsFetchSize(
                LdapUtils.intValue(attrs.get("dcmPurgeInstanceRecordsFetchSize"), 100));
        ext.setOverwritePolicy(LdapUtils.enumValue(OverwritePolicy.class, attrs.get("dcmOverwritePolicy"), OverwritePolicy.NEVER));
        ext.setBulkDataSpoolDirectory(
                LdapUtils.stringValue(attrs.get("dcmBulkDataSpoolDirectory"), ArchiveDeviceExtension.JBOSS_SERVER_TEMP_DIR));
        ext.setQueryRetrieveViewID(LdapUtils.stringValue(attrs.get("dcmQueryRetrieveViewID"), null));
        ext.setPersonNameComponentOrderInsensitiveMatching(
                LdapUtils.booleanValue(attrs.get("dcmPersonNameComponentOrderInsensitiveMatching"), false));
        ext.setSendPendingCGet(LdapUtils.booleanValue(attrs.get("dcmSendPendingCGet"), false));
        ext.setSendPendingCMoveInterval(toDuration(attrs.get("dcmSendPendingCMoveInterval"), null));
        ext.setFallbackCMoveSCP(LdapUtils.stringValue(attrs.get("dcmFallbackCMoveSCP"), null));
        ext.setFallbackCMoveSCPDestination(LdapUtils.stringValue(attrs.get("dcmFallbackCMoveSCPDestination"), null));
        ext.setFallbackCMoveSCPRetries(LdapUtils.intValue(attrs.get("dcmFallbackCMoveSCPRetries"), 0));
        ext.setFallbackCMoveSCPLeadingCFindSCP(LdapUtils.stringValue(attrs.get("dcmFallbackCMoveSCPLeadingCFindSCP"), null));
        ext.setAlternativeCMoveSCP(LdapUtils.stringValue(attrs.get("dcmAltCMoveSCP"), null));
        ext.setDiffStudiesIncludefieldAll(tags(attrs.get("dcmDiffStudiesIncludefieldAll")));
        ext.setWadoSR2HtmlTemplateURI(LdapUtils.stringValue(attrs.get("dcmWadoSR2HtmlTemplateURI"), null));
        ext.setWadoSR2TextTemplateURI(LdapUtils.stringValue(attrs.get("dcmWadoSR2TextTemplateURI"), null));
        ext.setPatientUpdateTemplateURI(LdapUtils.stringValue(attrs.get("hl7PatientUpdateTemplateURI"), null));
        ext.setImportReportTemplateURI(LdapUtils.stringValue(attrs.get("hl7ImportReportTemplateURI"), null));
        ext.setScheduleProcedureTemplateURI(LdapUtils.stringValue(attrs.get("hl7ScheduleProcedureTemplateURI"), null));
        ext.setHl7LogFilePattern(LdapUtils.stringValue(attrs.get("hl7LogFilePattern"), null));
        ext.setHl7ErrorLogFilePattern(LdapUtils.stringValue(attrs.get("hl7ErrorLogFilePattern"), null));
        ext.setUnzipVendorDataToURI(LdapUtils.stringValue(attrs.get("dcmUnzipVendorDataToURI"), null));
        ext.setWadoSupportedSRClasses(LdapUtils.stringArray(attrs.get("dcmWadoSupportedSRClasses")));
        ext.setQueryFetchSize(LdapUtils.intValue(attrs.get("dcmQueryFetchSize"), 100));
        ext.setQidoMaxNumberOfResults(LdapUtils.intValue(attrs.get("dcmQidoMaxNumberOfResults"), 0));
        ext.setMppsForwardDestinations(LdapUtils.stringArray(attrs.get("dcmFwdMppsDestination")));
        ext.setIanDestinations(LdapUtils.stringArray(attrs.get("dcmIanDestination")));
        ext.setIanDelay(toDuration(attrs.get("dcmIanDelay"), null));
        ext.setIanTimeout(toDuration(attrs.get("dcmIanTimeout"), null));
        ext.setIanOnTimeout(LdapUtils.booleanValue(attrs.get("dcmIanOnTimeout"), false));
        ext.setIanTaskPollingInterval(toDuration(attrs.get("dcmIanTaskPollingInterval"), null));
        ext.setIanTaskFetchSize(LdapUtils.intValue(attrs.get("dcmIanTaskFetchSize"), 100));
        ext.setExportTaskPollingInterval(toDuration(attrs.get("dcmExportTaskPollingInterval"), null));
        ext.setExportTaskFetchSize(LdapUtils.intValue(attrs.get("dcmExportTaskFetchSize"), 5));
        ext.setPurgeStoragePollingInterval(toDuration(attrs.get("dcmPurgeStoragePollingInterval"), null));
        ext.setPurgeStorageFetchSize(LdapUtils.intValue(attrs.get("dcmPurgeStorageFetchSize"), 100));
        ext.setDeleteRejectedPollingInterval(toDuration(attrs.get("dcmDeleteRejectedPollingInterval"), null));
        ext.setDeleteRejectedFetchSize(LdapUtils.intValue(attrs.get("dcmDeleteRejectedFetchSize"), 100));
        ext.setDeleteStudyBatchSize(LdapUtils.intValue(attrs.get("dcmDeleteStudyBatchSize"), 10));
        ext.setDeletePatientOnDeleteLastStudy(
                LdapUtils.booleanValue(attrs.get("dcmDeletePatientOnDeleteLastStudy"), false));
        ext.setMaxAccessTimeStaleness(toDuration(attrs.get("dcmMaxAccessTimeStaleness"), null));
        ext.setAECacheStaleTimeout(toDuration(attrs.get("dcmAECacheStaleTimeout"), null));
        ext.setLeadingCFindSCPQueryCacheStaleTimeout(toDuration(attrs.get("dcmLeadingCFindSCPQueryCacheStaleTimeout"), null));
        ext.setLeadingCFindSCPQueryCacheSize(LdapUtils.intValue(attrs.get("dcmLeadingCFindSCPQueryCacheSize"), 10));
        ext.setAuditSpoolDirectory(
                LdapUtils.stringValue(attrs.get("dcmAuditSpoolDirectory"), ArchiveDeviceExtension.JBOSS_SERVER_TEMP_DIR));
        ext.setAuditPollingInterval(toDuration(attrs.get("dcmAuditPollingInterval"), null));
        ext.setAuditAggregateDuration(toDuration(attrs.get("dcmAuditAggregateDuration"), null));
        ext.setStowSpoolDirectory(
                LdapUtils.stringValue(attrs.get("dcmStowSpoolDirectory"), ArchiveDeviceExtension.JBOSS_SERVER_TEMP_DIR));
        ext.setPurgeQueueMessagePollingInterval(toDuration(attrs.get("dcmPurgeQueueMessagePollingInterval"), null));
        ext.setWadoSpoolDirectory(
                LdapUtils.stringValue(attrs.get("dcmWadoSpoolDirectory"), ArchiveDeviceExtension.JBOSS_SERVER_TEMP_DIR));
        ext.setHideSPSWithStatusFrom(LdapUtils.enumArray(SPSStatus.class, attrs.get("dcmHideSPSWithStatusFromMWL")));
        ext.setRejectExpiredStudiesPollingInterval(toDuration(attrs.get("dcmRejectExpiredStudiesPollingInterval"), null));
        ext.setRejectExpiredStudiesPollingStartTime(toLocalTime(attrs.get("dcmRejectExpiredStudiesPollingStartTime")));
        ext.setRejectExpiredStudiesFetchSize(LdapUtils.intValue(attrs.get("dcmRejectExpiredStudiesFetchSize"), 0));
        ext.setRejectExpiredSeriesFetchSize(LdapUtils.intValue(attrs.get("dcmRejectExpiredSeriesFetchSize"), 0));
        ext.setRejectExpiredStudiesAETitle(LdapUtils.stringValue(attrs.get("dcmRejectExpiredStudiesAETitle"), null));
        ext.setFallbackCMoveSCPStudyOlderThan(LdapUtils.stringValue(attrs.get("dcmFallbackCMoveSCPStudyOlderThan"), null));
        ext.setStorePermissionServiceURL(LdapUtils.stringValue(attrs.get("dcmStorePermissionServiceURL"), null));
        ext.setStorePermissionServiceResponsePattern(toPattern(attrs.get("dcmStorePermissionServiceResponsePattern")));
        ext.setStorePermissionCacheStaleTimeout(toDuration(attrs.get("dcmStorePermissionCacheStaleTimeout"), null));
        ext.setStorePermissionCacheSize(LdapUtils.intValue(attrs.get("dcmStorePermissionCacheSize"), 10));
        ext.setMergeMWLCacheStaleTimeout(toDuration(attrs.get("dcmMergeMWLCacheStaleTimeout"), null));
        ext.setMergeMWLCacheSize(LdapUtils.intValue(attrs.get("dcmMergeMWLCacheSize"), 10));
        ext.setStoreUpdateDBMaxRetries(LdapUtils.intValue(attrs.get("dcmStoreUpdateDBMaxRetries"), 1));
        ext.setStoreUpdateDBMaxRetryDelay(LdapUtils.intValue(attrs.get("dcmStoreUpdateDBMaxRetryDelay"), 1000));
        ext.setAllowRejectionForDataRetentionPolicyExpired(
                LdapUtils.enumValue(AllowRejectionForDataRetentionPolicyExpired.class,
                        attrs.get("dcmAllowRejectionForDataRetentionPolicyExpired"),
                        AllowRejectionForDataRetentionPolicyExpired.STUDY_RETENTION_POLICY));
        ext.setAcceptMissingPatientID(
                LdapUtils.enumValue(AcceptMissingPatientID.class,
                        attrs.get("dcmAcceptMissingPatientID"), AcceptMissingPatientID.CREATE));
        ext.setAllowDeleteStudyPermanently(LdapUtils.enumValue(AllowDeleteStudyPermanently.class,
                attrs.get("dcmAllowDeleteStudyPermanently"),
                AllowDeleteStudyPermanently.REJECTED));
        ext.setStorePermissionServiceExpirationDatePattern(toPattern(attrs.get("dcmStorePermissionServiceExpirationDatePattern")));
        ext.setShowPatientInfoInSystemLog(LdapUtils.enumValue(ShowPatientInfo.class,
                attrs.get("dcmShowPatientInfoInSystemLog"), ShowPatientInfo.PLAIN_TEXT));
        ext.setShowPatientInfoInAuditLog(LdapUtils.enumValue(ShowPatientInfo.class,
                attrs.get("dcmShowPatientInfoInAuditLog"), ShowPatientInfo.PLAIN_TEXT));
        ext.setPurgeStgCmtCompletedDelay(toDuration(attrs.get("dcmPurgeStgCmtCompletedDelay"), null));
        ext.setPurgeStgCmtPollingInterval(toDuration(attrs.get("dcmPurgeStgCmtPollingInterval"), null));
        ext.setDefaultCharacterSet(LdapUtils.stringValue(attrs.get("dcmDefaultCharacterSet"), null));
        ext.setStorePermissionServiceErrorCommentPattern(toPattern(attrs.get("dcmStorePermissionServiceErrorCommentPattern")));
        ext.setStorePermissionServiceErrorCodePattern(toPattern(attrs.get("dcmStorePermissionServiceErrorCodePattern")));
        ext.setRetrieveAETitles(LdapUtils.stringArray(attrs.get("dcmRetrieveAET")));
        ext.setExternalRetrieveAEDestination(LdapUtils.stringValue(attrs.get("dcmExternalRetrieveAEDestination"), null));
        ext.setXDSiImagingDocumentSourceAETitle(LdapUtils.stringValue(attrs.get("dcmXDSiImagingDocumentSourceAETitle"), null));
        ext.setRemapRetrieveURL(LdapUtils.stringValue(attrs.get("dcmRemapRetrieveURL"), null));
        ext.setValidateCallingAEHostname(LdapUtils.booleanValue(attrs.get("dcmValidateCallingAEHostname"), false));
        ext.setHl7PSUSendingApplication(LdapUtils.stringValue(attrs.get("hl7PSUSendingApplication"), null));
        ext.setHl7PSUReceivingApplications(LdapUtils.stringArray(attrs.get("hl7PSUReceivingApplication")));
        ext.setHl7PSUDelay(toDuration(attrs.get("hl7PSUDelay"), null));
        ext.setHl7PSUTimeout(toDuration(attrs.get("hl7PSUTimeout"), null));
        ext.setHl7PSUOnTimeout(LdapUtils.booleanValue(attrs.get("hl7PSUOnTimeout"), false));
        ext.setHl7PSUTaskPollingInterval(toDuration(attrs.get("hl7PSUTaskPollingInterval"), null));
        ext.setHl7PSUTaskFetchSize(LdapUtils.intValue(attrs.get("hl7PSUTaskFetchSize"), 100));
        ext.setHl7PSUMWL(LdapUtils.booleanValue(attrs.get("hl7PSUMWL"), false));
        ext.setAcceptConflictingPatientID(
                LdapUtils.enumValue(AcceptConflictingPatientID.class,
                        attrs.get("dcmAcceptConflictingPatientID"), AcceptConflictingPatientID.MERGED));
        ext.setAuditRecordRepositoryURL(LdapUtils.stringValue(attrs.get("dcmAuditRecordRepositoryURL"), null));
        ext.setElasticSearchURL(LdapUtils.stringValue(attrs.get("dcmElasticSearchURL"), null));
        ext.setAudit2JsonFhirTemplateURI(LdapUtils.stringValue(attrs.get("dcmAudit2JsonFhirTemplateURI"), null));
        ext.setAudit2XmlFhirTemplateURI(LdapUtils.stringValue(attrs.get("dcmAudit2XmlFhirTemplateURI"), null));
        ext.setCopyMoveUpdatePolicy(LdapUtils.enumValue(org.dcm4che3.data.Attributes.UpdatePolicy.class, attrs.get("dcmCopyMoveUpdatePolicy"), null));
        ext.setHl7TrackChangedPatientID(LdapUtils.booleanValue(attrs.get("hl7TrackChangedPatientID"), true));
        ext.setInvokeImageDisplayPatientURL(LdapUtils.stringValue(attrs.get("dcmInvokeImageDisplayPatientURL"), null));
        ext.setInvokeImageDisplayStudyURL(LdapUtils.stringValue(attrs.get("dcmInvokeImageDisplayStudyURL"), null));
        ext.setHl7ADTReceivingApplication(LdapUtils.stringArray(attrs.get("hl7ADTReceivingApplication")));
        ext.setHl7ADTSendingApplication(LdapUtils.stringValue(attrs.get("hl7ADTSendingApplication"), null));
        ext.setHl7ScheduledProtocolCodeInOrder(LdapUtils.enumValue(ScheduledProtocolCodeInOrder.class,
                attrs.get("hl7ScheduledProtocolCodeInOrder"), ScheduledProtocolCodeInOrder.OBR_4_4));
        ext.setHl7ScheduledStationAETInOrder(LdapUtils.enumValue(ScheduledStationAETInOrder.class,
                attrs.get("hl7ScheduledStationAETInOrder"), null));
        ext.setAuditUnknownStudyInstanceUID(LdapUtils.stringValue(
                attrs.get("dcmAuditUnknownStudyInstanceUID"), ArchiveDeviceExtension.AUDIT_UNKNOWN_STUDY_INSTANCE_UID));
        ext.setAuditUnknownPatientID(LdapUtils.stringValue(
                attrs.get("dcmAuditUnknownPatientID"), ArchiveDeviceExtension.AUDIT_UNKNOWN_PATIENT_ID));
    }

    @Override
    protected void storeDiffs(Device prev, Device device, List<ModificationItem> mods) {
        ArchiveDeviceExtension aa = prev.getDeviceExtension(ArchiveDeviceExtension.class);
        ArchiveDeviceExtension bb = device.getDeviceExtension(ArchiveDeviceExtension.class);
        if (aa == null && bb == null)
            return;

        boolean remove = bb == null;
        if (remove) {
            bb = new ArchiveDeviceExtension();
        } else if (aa == null) {
            aa = new ArchiveDeviceExtension();
            mods.add(new ModificationItem(DirContext.ADD_ATTRIBUTE,
                    LdapUtils.attr("objectClass", "dcmArchiveDevice")));
        }
        LdapUtils.storeDiffObject(mods, "dcmFuzzyAlgorithmClass", aa.getFuzzyAlgorithmClass(), bb.getFuzzyAlgorithmClass(), null);
        LdapUtils.storeDiff(mods, "dcmSeriesMetadataStorageID",
                aa.getSeriesMetadataStorageIDs(),
                bb.getSeriesMetadataStorageIDs());
        LdapUtils.storeDiffObject(mods, "dcmSeriesMetadataDelay", aa.getSeriesMetadataDelay(), bb.getSeriesMetadataDelay(), null);
        LdapUtils.storeDiffObject(mods, "dcmSeriesMetadataPollingInterval",
                aa.getSeriesMetadataPollingInterval(),
                bb.getSeriesMetadataPollingInterval(), null);
        LdapUtils.storeDiff(mods, "dcmSeriesMetadataFetchSize",
                aa.getSeriesMetadataFetchSize(),
                bb.getSeriesMetadataFetchSize(),
                100);
        LdapUtils.storeDiffObject(mods, "dcmPurgeInstanceRecordsDelay",
                aa.getPurgeInstanceRecordsDelay(),
                bb.getPurgeInstanceRecordsDelay(), null);
        LdapUtils.storeDiffObject(mods, "dcmPurgeInstanceRecordsPollingInterval",
                aa.getPurgeInstanceRecordsPollingInterval(),
                bb.getPurgeInstanceRecordsPollingInterval(), null);
        LdapUtils.storeDiff(mods, "dcmPurgeInstanceRecordsFetchSize",
                aa.getPurgeInstanceRecordsFetchSize(),
                bb.getPurgeInstanceRecordsFetchSize(),
                100);
        LdapUtils.storeDiffObject(mods, "dcmOverwritePolicy", aa.getOverwritePolicy(), bb.getOverwritePolicy(), OverwritePolicy.NEVER);
        LdapUtils.storeDiffObject(mods, "dcmBulkDataSpoolDirectory",
                aa.getBulkDataSpoolDirectory(),
                bb.getBulkDataSpoolDirectory(),
                ArchiveDeviceExtension.JBOSS_SERVER_TEMP_DIR);
        LdapUtils.storeDiffObject(mods, "dcmQueryRetrieveViewID", aa.getQueryRetrieveViewID(), bb.getQueryRetrieveViewID(), null);
        LdapUtils.storeDiff(mods, "dcmPersonNameComponentOrderInsensitiveMatching",
                aa.isPersonNameComponentOrderInsensitiveMatching(),
                bb.isPersonNameComponentOrderInsensitiveMatching(),
                false);
        LdapUtils.storeDiff(mods, "dcmSendPendingCGet", aa.isSendPendingCGet(), bb.isSendPendingCGet(), false);
        LdapUtils.storeDiffObject(mods, "dcmSendPendingCMoveInterval",
                aa.getSendPendingCMoveInterval(), bb.getSendPendingCMoveInterval(), null);
        LdapUtils.storeDiffObject(mods, "dcmFallbackCMoveSCP", aa.getFallbackCMoveSCP(), bb.getFallbackCMoveSCP(), null);
        LdapUtils.storeDiffObject(mods, "dcmFallbackCMoveSCPDestination",
                aa.getFallbackCMoveSCPDestination(), bb.getFallbackCMoveSCPDestination(), null);
        LdapUtils.storeDiff(mods, "dcmFallbackCMoveSCPRetries",
                aa.getFallbackCMoveSCPRetries(), bb.getFallbackCMoveSCPRetries(),  0);
        LdapUtils.storeDiffObject(mods, "dcmFallbackCMoveSCPLeadingCFindSCP",
                aa.getFallbackCMoveSCPLeadingCFindSCP(), bb.getFallbackCMoveSCPLeadingCFindSCP(), null);
        LdapUtils.storeDiffObject(mods, "dcmAltCMoveSCP", aa.getAlternativeCMoveSCP(), bb.getAlternativeCMoveSCP(), null);
        storeDiffTags(mods, "dcmDiffStudiesIncludefieldAll", aa.getDiffStudiesIncludefieldAll(), bb.getDiffStudiesIncludefieldAll());
        LdapUtils.storeDiffObject(mods, "dcmWadoSR2HtmlTemplateURI",
                aa.getWadoSR2HtmlTemplateURI(), bb.getWadoSR2HtmlTemplateURI(), null);
        LdapUtils.storeDiffObject(mods, "dcmWadoSR2TextTemplateURI",
                aa.getWadoSR2TextTemplateURI(), bb.getWadoSR2TextTemplateURI(), null);
        LdapUtils.storeDiffObject(mods, "hl7ImportReportTemplateURI",
                aa.getImportReportTemplateURI(), bb.getImportReportTemplateURI(), null);
        LdapUtils.storeDiffObject(mods, "hl7PatientUpdateTemplateURI",
                aa.getPatientUpdateTemplateURI(), bb.getPatientUpdateTemplateURI(), null);
        LdapUtils.storeDiffObject(mods, "hl7ScheduleProcedureTemplateURI", aa.getScheduleProcedureTemplateURI(),
                bb.getScheduleProcedureTemplateURI(), null);
        LdapUtils.storeDiffObject(mods, "hl7LogFilePattern", aa.getHl7LogFilePattern(), bb.getHl7LogFilePattern(), null);
        LdapUtils.storeDiffObject(mods, "hl7ErrorLogFilePattern", aa.getHl7ErrorLogFilePattern(), bb.getHl7ErrorLogFilePattern(), null);
        LdapUtils.storeDiffObject(mods, "dcmUnzipVendorDataToURI",
                aa.getUnzipVendorDataToURI(), bb.getUnzipVendorDataToURI(), null);
        LdapUtils.storeDiff(mods, "dcmWadoSupportedSRClasses",
                aa.getWadoSupportedSRClasses(), bb.getWadoSupportedSRClasses());
        LdapUtils.storeDiff(mods, "dcmQueryFetchSize",
                aa.getQueryFetchSize(), bb.getQueryFetchSize(),  100);
        LdapUtils.storeDiff(mods, "dcmQidoMaxNumberOfResults",
                aa.getQidoMaxNumberOfResults(), bb.getQidoMaxNumberOfResults(),  0);
        LdapUtils.storeDiff(mods, "dcmFwdMppsDestination",
                aa.getMppsForwardDestinations(), bb.getMppsForwardDestinations());
        LdapUtils.storeDiff(mods, "dcmIanDestination", aa.getIanDestinations(), bb.getIanDestinations());
        LdapUtils.storeDiffObject(mods, "dcmIanDelay", aa.getIanDelay(), bb.getIanDelay(), null);
        LdapUtils.storeDiffObject(mods, "dcmIanTimeout", aa.getIanTimeout(), bb.getIanTimeout(), null);
        LdapUtils.storeDiff(mods, "dcmIanOnTimeout", aa.isIanOnTimeout(), bb.isIanOnTimeout(), false);
        LdapUtils.storeDiffObject(mods, "dcmIanTaskPollingInterval",
                aa.getIanTaskPollingInterval(), bb.getIanTaskPollingInterval(), null);
        LdapUtils.storeDiff(mods, "dcmIanTaskFetchSize", aa.getIanTaskFetchSize(), bb.getIanTaskFetchSize(), 100);
        LdapUtils.storeDiffObject(mods, "dcmExportTaskPollingInterval",
                aa.getExportTaskPollingInterval(), bb.getExportTaskPollingInterval(), null);
        LdapUtils.storeDiff(mods, "dcmExportTaskFetchSize",
                aa.getExportTaskFetchSize(), bb.getExportTaskFetchSize(), 5);
        LdapUtils.storeDiffObject(mods, "dcmPurgeStoragePollingInterval",
                aa.getPurgeStoragePollingInterval(), bb.getPurgeStoragePollingInterval(), null);
        LdapUtils.storeDiff(mods, "dcmPurgeStorageFetchSize",
                aa.getPurgeStorageFetchSize(), bb.getPurgeStorageFetchSize(), 100);
        LdapUtils.storeDiffObject(mods, "dcmDeleteRejectedPollingInterval",
                aa.getDeleteRejectedPollingInterval(), bb.getDeleteRejectedPollingInterval(), null);
        LdapUtils.storeDiff(mods, "dcmDeleteRejectedFetchSize",
                aa.getDeleteRejectedFetchSize(), bb.getDeleteRejectedFetchSize(), 100);
        LdapUtils.storeDiff(mods, "dcmDeleteStudyBatchSize",
                aa.getDeleteStudyBatchSize(), bb.getDeleteStudyBatchSize(), 10);
        LdapUtils.storeDiff(mods, "dcmDeletePatientOnDeleteLastStudy",
                aa.isDeletePatientOnDeleteLastStudy(), bb.isDeletePatientOnDeleteLastStudy(), false);
        LdapUtils.storeDiffObject(mods, "dcmMaxAccessTimeStaleness",
                aa.getMaxAccessTimeStaleness(), bb.getMaxAccessTimeStaleness(), null);
        LdapUtils.storeDiffObject(mods, "dcmAECacheStaleTimeout",
                aa.getAECacheStaleTimeout(), bb.getAECacheStaleTimeout(), null);
        LdapUtils.storeDiffObject(mods, "dcmLeadingCFindSCPQueryCacheStaleTimeout",
                aa.getLeadingCFindSCPQueryCacheStaleTimeout(), bb.getLeadingCFindSCPQueryCacheStaleTimeout(), null);
        LdapUtils.storeDiff(mods, "dcmLeadingCFindSCPQueryCacheSize",
                aa.getLeadingCFindSCPQueryCacheSize(), bb.getLeadingCFindSCPQueryCacheSize(), 10);
        LdapUtils.storeDiffObject(mods, "dcmAuditSpoolDirectory",
                aa.getAuditSpoolDirectory(),
                bb.getAuditSpoolDirectory(),
                ArchiveDeviceExtension.JBOSS_SERVER_TEMP_DIR);
        LdapUtils.storeDiffObject(mods, "dcmAuditPollingInterval",
                aa.getAuditPollingInterval(), bb.getAuditPollingInterval(), null);
        LdapUtils.storeDiffObject(mods, "dcmAuditAggregateDuration",
                aa.getAuditAggregateDuration(), bb.getAuditAggregateDuration(), null);
        LdapUtils.storeDiffObject(mods, "dcmStowSpoolDirectory",
                aa.getStowSpoolDirectory(),
                bb.getStowSpoolDirectory(),
                ArchiveDeviceExtension.JBOSS_SERVER_TEMP_DIR);
        LdapUtils.storeDiffObject(mods, "dcmPurgeQueueMessagePollingInterval", aa.getPurgeQueueMessagePollingInterval(),
                bb.getPurgeQueueMessagePollingInterval(), null);
        LdapUtils.storeDiffObject(mods, "dcmWadoSpoolDirectory",
                aa.getWadoSpoolDirectory(),
                bb.getWadoSpoolDirectory(),
                ArchiveDeviceExtension.JBOSS_SERVER_TEMP_DIR);
        LdapUtils.storeDiff(mods, "dcmHideSPSWithStatusFromMWL", aa.getHideSPSWithStatusFrom(), bb.getHideSPSWithStatusFrom());
        LdapUtils.storeDiffObject(mods, "dcmRejectExpiredStudiesPollingInterval",
                aa.getRejectExpiredStudiesPollingInterval(), bb.getRejectExpiredStudiesPollingInterval(), null);
        LdapUtils.storeDiffObject(mods, "dcmRejectExpiredStudiesPollingStartTime",
                aa.getRejectExpiredStudiesPollingStartTime(), bb.getRejectExpiredStudiesPollingStartTime(), null);
        LdapUtils.storeDiff(mods, "dcmRejectExpiredStudiesFetchSize",
                aa.getRejectExpiredStudiesFetchSize(), bb.getRejectExpiredStudiesFetchSize(), 0);
        LdapUtils.storeDiff(mods, "dcmRejectExpiredSeriesFetchSize",
                aa.getRejectExpiredSeriesFetchSize(), bb.getRejectExpiredSeriesFetchSize(), 0);
        LdapUtils.storeDiffObject(mods, "dcmRejectExpiredStudiesAETitle",
                aa.getRejectExpiredStudiesAETitle(), bb.getRejectExpiredStudiesAETitle(), null);
        LdapUtils.storeDiffObject(mods, "dcmFallbackCMoveSCPStudyOlderThan",
                aa.getFallbackCMoveSCPStudyOlderThan(), bb.getFallbackCMoveSCPStudyOlderThan(), null);
        LdapUtils.storeDiffObject(mods, "dcmStorePermissionServiceURL",
                aa.getStorePermissionServiceURL(), bb.getStorePermissionServiceURL(), null);
        LdapUtils.storeDiffObject(mods, "dcmStorePermissionServiceResponsePattern",
                aa.getStorePermissionServiceResponsePattern(), bb.getStorePermissionServiceResponsePattern(), null);
        LdapUtils.storeDiffObject(mods, "dcmStorePermissionCacheStaleTimeout",
                aa.getStorePermissionCacheStaleTimeout(), bb.getStorePermissionCacheStaleTimeout(), null);
        LdapUtils.storeDiff(mods, "dcmStorePermissionCacheSize",
                aa.getStorePermissionCacheSize(), bb.getStorePermissionCacheSize(), 10);
        LdapUtils.storeDiffObject(mods, "dcmMergeMWLCacheStaleTimeout",
                aa.getMergeMWLCacheStaleTimeout(), bb.getMergeMWLCacheStaleTimeout(), null);
        LdapUtils.storeDiff(mods, "dcmMergeMWLCacheSize",
                aa.getMergeMWLCacheSize(), bb.getMergeMWLCacheSize(), 10);
        LdapUtils.storeDiff(mods, "dcmStoreUpdateDBMaxRetries",
                aa.getStoreUpdateDBMaxRetries(), bb.getStoreUpdateDBMaxRetries(), 1);
        LdapUtils.storeDiff(mods, "dcmStoreUpdateDBMaxRetryDelay",
                aa.getStoreUpdateDBMaxRetryDelay(), bb.getStoreUpdateDBMaxRetryDelay(), 1000);
        LdapUtils.storeDiffObject(mods, "dcmAllowRejectionForDataRetentionPolicyExpired",
                aa.getAllowRejectionForDataRetentionPolicyExpired(), bb.getAllowRejectionForDataRetentionPolicyExpired(),
                AllowRejectionForDataRetentionPolicyExpired.STUDY_RETENTION_POLICY);
        LdapUtils.storeDiffObject(mods, "dcmAcceptMissingPatientID",
                aa.getAcceptMissingPatientID(), bb.getAcceptMissingPatientID(), AcceptMissingPatientID.CREATE);
        LdapUtils.storeDiffObject(mods, "dcmAllowDeleteStudyPermanently",
                aa.getAllowDeleteStudyPermanently(), bb.getAllowDeleteStudyPermanently(),
                AllowDeleteStudyPermanently.REJECTED);
        LdapUtils.storeDiffObject(mods, "dcmStorePermissionServiceExpirationDatePattern",
                aa.getStorePermissionServiceExpirationDatePattern(), bb.getStorePermissionServiceExpirationDatePattern(), null);
        LdapUtils.storeDiffObject(mods, "dcmShowPatientInfoInSystemLog",
                aa.getShowPatientInfoInSystemLog(), bb.getShowPatientInfoInSystemLog(), ShowPatientInfo.PLAIN_TEXT);
        LdapUtils.storeDiffObject(mods, "dcmShowPatientInfoInAuditLog",
                aa.getShowPatientInfoInAuditLog(), bb.getShowPatientInfoInAuditLog(), ShowPatientInfo.PLAIN_TEXT);
        LdapUtils.storeDiffObject(mods, "dcmPurgeStgCmtCompletedDelay",
                aa.getPurgeStgCmtCompletedDelay(), bb.getPurgeStgCmtCompletedDelay(), null);
        LdapUtils.storeDiffObject(mods, "dcmPurgeStgCmtPollingInterval",
                aa.getPurgeStgCmtPollingInterval(), bb.getPurgeStgCmtPollingInterval(), null);
        LdapUtils.storeDiffObject(mods, "dcmDefaultCharacterSet",
                aa.getDefaultCharacterSet(), bb.getDefaultCharacterSet(), null);
        LdapUtils.storeDiffObject(mods, "dcmStorePermissionServiceErrorCommentPattern",
                aa.getStorePermissionServiceErrorCommentPattern(), bb.getStorePermissionServiceErrorCommentPattern(), null);
        LdapUtils.storeDiffObject(mods, "dcmStorePermissionServiceErrorCodePattern",
                aa.getStorePermissionServiceErrorCodePattern(), bb.getStorePermissionServiceErrorCodePattern(), null);
        LdapUtils.storeDiff(mods, "dcmRetrieveAET", aa.getRetrieveAETitles(), bb.getRetrieveAETitles());
        LdapUtils.storeDiffObject(mods, "dcmExternalRetrieveAEDestination",
                aa.getExternalRetrieveAEDestination(), bb.getExternalRetrieveAEDestination(), null);
        LdapUtils.storeDiffObject(mods, "dcmXDSiImagingDocumentSourceAETitle",
                aa.getXDSiImagingDocumentSourceAETitle(), bb.getXDSiImagingDocumentSourceAETitle(), null);
        LdapUtils.storeDiffObject(mods, "dcmRemapRetrieveURL",
                aa.getRemapRetrieveURL(), bb.getRemapRetrieveURL(), null);
        LdapUtils.storeDiffObject(mods, "dcmValidateCallingAEHostname",
                aa.isValidateCallingAEHostname(), bb.isValidateCallingAEHostname(), null);
        LdapUtils.storeDiffObject(mods, "hl7PSUSendingApplication",
                aa.getHl7PSUSendingApplication(), bb.getHl7PSUSendingApplication(), null);
        LdapUtils.storeDiff(mods, "hl7PSUReceivingApplication",
                aa.getHl7PSUReceivingApplications(), bb.getHl7PSUReceivingApplications());
        LdapUtils.storeDiffObject(mods, "hl7PSUDelay", aa.getHl7PSUDelay(), bb.getHl7PSUDelay(), null);
        LdapUtils.storeDiffObject(mods, "hl7PSUTimeout", aa.getHl7PSUTimeout(), bb.getHl7PSUTimeout(), null);
        LdapUtils.storeDiff(mods, "hl7PSUOnTimeout", aa.isHl7PSUOnTimeout(), bb.isHl7PSUOnTimeout(), false);
        LdapUtils.storeDiffObject(mods, "hl7PSUTaskPollingInterval",
                aa.getHl7PSUTaskPollingInterval(), bb.getHl7PSUTaskPollingInterval(), null);
        LdapUtils.storeDiff(mods, "hl7PSUTaskFetchSize",
                aa.getHl7PSUTaskFetchSize(), bb.getHl7PSUTaskFetchSize(), 100);
        LdapUtils.storeDiff(mods, "hl7PSUMWL", aa.isHl7PSUMWL(), bb.isHl7PSUMWL(), false);
        LdapUtils.storeDiffObject(mods, "dcmAcceptConflictingPatientID",
                aa.getAcceptConflictingPatientID(), bb.getAcceptConflictingPatientID(),
                AcceptConflictingPatientID.MERGED);
        LdapUtils.storeDiffObject(mods, "dcmAuditRecordRepositoryURL",
                aa.getAuditRecordRepositoryURL(), bb.getAuditRecordRepositoryURL(), null);
        LdapUtils.storeDiffObject(mods, "dcmElasticSearchURL",
                aa.getElasticSearchURL(), bb.getElasticSearchURL(), null);
        LdapUtils.storeDiffObject(mods,"dcmAudit2JsonFhirTemplateURI",
                aa.getAudit2JsonFhirTemplateURI(), bb.getAudit2JsonFhirTemplateURI(), null);
        LdapUtils.storeDiffObject(mods,"dcmAudit2XmlFhirTemplateURI",
                aa.getAudit2XmlFhirTemplateURI(), bb.getAudit2XmlFhirTemplateURI(), null);
        LdapUtils.storeDiffObject(mods, "dcmCopyMoveUpdatePolicy",
                aa.getCopyMoveUpdatePolicy(), bb.getCopyMoveUpdatePolicy(), null);
        LdapUtils.storeDiff(mods, "hl7TrackChangedPatientID",
                aa.isHl7TrackChangedPatientID(), bb.isHl7TrackChangedPatientID(), true);
        LdapUtils.storeDiffObject(mods, "dcmInvokeImageDisplayPatientURL",
                aa.getInvokeImageDisplayPatientURL(), bb.getInvokeImageDisplayPatientURL(), null);
        LdapUtils.storeDiffObject(mods, "dcmInvokeImageDisplayStudyURL",
                aa.getInvokeImageDisplayStudyURL(), bb.getInvokeImageDisplayStudyURL(), null);
        LdapUtils.storeDiff(mods, "hl7ADTReceivingApplication",
                aa.getHl7ADTReceivingApplication(), bb.getHl7ADTReceivingApplication());
        LdapUtils.storeDiffObject(mods, "hl7ADTSendingApplication",
                aa.getHl7ADTSendingApplication(), bb.getHl7ADTSendingApplication(), null);
        LdapUtils.storeDiffObject(mods, "hl7ScheduledProtocolCodeInOrder",
                aa.getHl7ScheduledProtocolCodeInOrder(), bb.getHl7ScheduledProtocolCodeInOrder(),
                ScheduledProtocolCodeInOrder.OBR_4_4);
        LdapUtils.storeDiffObject(mods, "hl7ScheduledStationAETInOrder",
                aa.getHl7ScheduledStationAETInOrder(), bb.getHl7ScheduledStationAETInOrder(), null);
        LdapUtils.storeDiffObject(mods, "dcmAuditUnknownStudyInstanceUID",
                aa.getAuditUnknownStudyInstanceUID(), bb.getAuditUnknownStudyInstanceUID(),
                ArchiveDeviceExtension.AUDIT_UNKNOWN_STUDY_INSTANCE_UID);
        LdapUtils.storeDiffObject(mods, "dcmAuditUnknownPatientID",
                aa.getAuditUnknownPatientID(), bb.getAuditUnknownPatientID(),
                ArchiveDeviceExtension.AUDIT_UNKNOWN_PATIENT_ID);
        if (remove)
            mods.add(new ModificationItem(DirContext.REMOVE_ATTRIBUTE,
                    LdapUtils.attr("objectClass", "dcmArchiveDevice")));
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
        storeStoreAccessControlIDRules(arcDev.getStoreAccessControlIDRules(), deviceDN);
        storeAttributeCoercions(arcDev.getAttributeCoercions(), deviceDN);
        storeQueryRetrieveViews(deviceDN, arcDev);
        storeRejectNotes(deviceDN, arcDev);
        storeStudyRetentionPolicies(arcDev.getStudyRetentionPolicies(), deviceDN);
        storeIDGenerators(deviceDN, arcDev);
        storeHL7ForwardRules(arcDev.getHL7ForwardRules(), deviceDN, config);
        storeRSForwardRules(arcDev.getRSForwardRules(), deviceDN);
        storeMetadataFilter(deviceDN, arcDev);
        storeScheduledStations(arcDev.getHL7OrderScheduledStations(), deviceDN, config);
        storeHL7OrderSPSStatus(arcDev.getHL7OrderSPSStatuses(), deviceDN, config);
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
        loadStoreAccessControlIDRules(arcdev.getStoreAccessControlIDRules(), deviceDN);
        loadAttributeCoercions(arcdev.getAttributeCoercions(), deviceDN);
        loadQueryRetrieveViews(arcdev, deviceDN);
        loadRejectNotes(arcdev, deviceDN);
        loadStudyRetentionPolicies(arcdev.getStudyRetentionPolicies(), deviceDN);
        loadIDGenerators(arcdev, deviceDN);
        loadHL7ForwardRules(arcdev.getHL7ForwardRules(), deviceDN, config);
        loadRSForwardRules(arcdev.getRSForwardRules(), deviceDN);
        loadMetadataFilters(arcdev, deviceDN);
        loadScheduledStations(arcdev.getHL7OrderScheduledStations(), deviceDN, config);
        loadHL7OrderSPSStatus(arcdev.getHL7OrderSPSStatuses(), deviceDN, config);
    }

    @Override
    protected void mergeChilds(Device prev, Device device, String deviceDN)
            throws NamingException, ConfigurationException {
        ArchiveDeviceExtension aa = prev
                .getDeviceExtension(ArchiveDeviceExtension.class);
        ArchiveDeviceExtension bb = device
                .getDeviceExtension(ArchiveDeviceExtension.class);
        if (aa == null && bb == null)
            return;

        if (aa == null)
            aa = new ArchiveDeviceExtension();
        else if (bb == null)
            bb = new ArchiveDeviceExtension();

        mergeAttributeFilters(aa, bb, deviceDN);
        mergeStorageDescriptors(aa, bb, deviceDN);
        mergeQueueDescriptors(aa, bb, deviceDN);
        mergeExportDescriptors(aa, bb, deviceDN);
        mergeExportRules(aa.getExportRules(), bb.getExportRules(), deviceDN);
        mergeCompressionRules(aa.getCompressionRules(), bb.getCompressionRules(), deviceDN);
        mergeStoreAccessControlIDRules(aa.getStoreAccessControlIDRules(), bb.getStoreAccessControlIDRules(), deviceDN);
        mergeAttributeCoercions(aa.getAttributeCoercions(), bb.getAttributeCoercions(), deviceDN);
        mergeQueryRetrieveViews(aa, bb, deviceDN);
        mergeRejectNotes(aa, bb, deviceDN);
        mergeStudyRetentionPolicies(aa.getStudyRetentionPolicies(), bb.getStudyRetentionPolicies(), deviceDN);
        mergeIDGenerators(aa, bb, deviceDN);
        mergeHL7ForwardRules(aa.getHL7ForwardRules(), bb.getHL7ForwardRules(), deviceDN, config);
        mergeRSForwardRules(aa.getRSForwardRules(), bb.getRSForwardRules(), deviceDN);
        mergeMetadataFilters(aa, bb, deviceDN);
        mergeScheduledStations(aa.getHL7OrderScheduledStations(), bb.getHL7OrderScheduledStations(), deviceDN, config);
        mergeHL7OrderSPSStatus(aa.getHL7OrderSPSStatuses(), bb.getHL7OrderSPSStatuses(), deviceDN, config);
    }

    @Override
    protected void storeTo(ApplicationEntity ae, Attributes attrs) {
        ArchiveAEExtension ext = ae.getAEExtension(ArchiveAEExtension.class);
        if (ext == null)
            return;

        attrs.get("objectclass").add("dcmArchiveNetworkAE");
        LdapUtils.storeNotEmpty(attrs, "dcmObjectStorageID", ext.getObjectStorageIDs());
        LdapUtils.storeNotDef(attrs, "dcmObjectStorageCount", ext.getObjectStorageCount(), 1);
        LdapUtils.storeNotEmpty(attrs, "dcmMetadataStorageID", ext.getMetadataStorageIDs());
        LdapUtils.storeNotNullOrDef(attrs, "dcmSeriesMetadataDelay", ext.getSeriesMetadataDelay(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmPurgeInstanceRecordsDelay", ext.getPurgeInstanceRecordsDelay(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmStoreAccessControlID", ext.getStoreAccessControlID(), null);
        LdapUtils.storeNotEmpty(attrs, "dcmAccessControlID", ext.getAccessControlIDs());
        LdapUtils.storeNotNullOrDef(attrs, "dcmOverwritePolicy", ext.getOverwritePolicy(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmBulkDataSpoolDirectory", ext.getBulkDataSpoolDirectory(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmQueryRetrieveViewID", ext.getQueryRetrieveViewID(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmPersonNameComponentOrderInsensitiveMatching",
                ext.getPersonNameComponentOrderInsensitiveMatching(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmSendPendingCGet", ext.getSendPendingCGet(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmSendPendingCMoveInterval", ext.getSendPendingCMoveInterval(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmFallbackCMoveSCP", ext.getFallbackCMoveSCP(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmFallbackCMoveSCPDestination", ext.getFallbackCMoveSCPDestination(), null);
        LdapUtils.storeNotDef(attrs, "dcmFallbackCMoveSCPRetries", ext.getFallbackCMoveSCPRetries(), 0);
        LdapUtils.storeNotNullOrDef(attrs, "dcmFallbackCMoveSCPLeadingCFindSCP", ext.getFallbackCMoveSCPLeadingCFindSCP(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmAltCMoveSCP", ext.getAlternativeCMoveSCP(), null);
        storeNotEmptyTags(attrs, "dcmDiffStudiesIncludefieldAll", ext.getDiffStudiesIncludefieldAll());
        LdapUtils.storeNotNullOrDef(attrs, "dcmWadoSR2HtmlTemplateURI", ext.getWadoSR2HtmlTemplateURI(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmWadoSR2TextTemplateURI", ext.getWadoSR2TextTemplateURI(), null);
        LdapUtils.storeNotDef(attrs, "dcmQidoMaxNumberOfResults", ext.getQidoMaxNumberOfResults(), 0);
        LdapUtils.storeNotEmpty(attrs, "dcmFwdMppsDestination", ext.getMppsForwardDestinations());
        LdapUtils.storeNotEmpty(attrs, "dcmIanDestination", ext.getIanDestinations());
        LdapUtils.storeNotNullOrDef(attrs, "dcmIanDelay", ext.getIanDelay(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmIanTimeout", ext.getIanTimeout(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmIanOnTimeout", ext.getIanOnTimeout(), null);
        LdapUtils.storeNotEmpty(attrs, "dcmHideSPSWithStatusFromMWL", ext.getHideSPSWithStatusFromMWL());
        LdapUtils.storeNotNullOrDef(attrs, "dcmFallbackCMoveSCPStudyOlderThan", ext.getFallbackCMoveSCPStudyOlderThan(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmStorePermissionServiceURL", ext.getStorePermissionServiceURL(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmStorePermissionServiceResponsePattern", ext.getStorePermissionServiceResponsePattern(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmAllowRejectionForDataRetentionPolicyExpired", ext.getAllowRejectionForDataRetentionPolicyExpired(), null);
        LdapUtils.storeNotEmpty(attrs, "dcmAcceptedUserRole", ext.getAcceptedUserRoles());
        LdapUtils.storeNotNullOrDef(attrs, "dcmAcceptMissingPatientID", ext.getAcceptMissingPatientID(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmAllowDeleteStudyPermanently", ext.getAllowDeleteStudyPermanently(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmStorePermissionServiceExpirationDatePattern", ext.getStorePermissionServiceExpirationDatePattern(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmDefaultCharacterSet", ext.getDefaultCharacterSet(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmStorePermissionServiceErrorCommentPattern", ext.getStorePermissionServiceErrorCommentPattern(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmStorePermissionServiceErrorCodePattern", ext.getStorePermissionServiceErrorCodePattern(), null);
        LdapUtils.storeNotEmpty(attrs, "dcmRetrieveAET", ext.getRetrieveAETitles());
        LdapUtils.storeNotNullOrDef(attrs, "dcmExternalRetrieveAEDestination", ext.getExternalRetrieveAEDestination(), null);
        LdapUtils.storeNotEmpty(attrs, "dcmAcceptedMoveDestination", ext.getAcceptedMoveDestinations());
        LdapUtils.storeNotNullOrDef(attrs, "dcmValidateCallingAEHostname", ext.getValidateCallingAEHostname(), null);
        LdapUtils.storeNotNullOrDef(attrs, "hl7PSUSendingApplication", ext.getHl7PSUSendingApplication(), null);
        LdapUtils.storeNotEmpty(attrs, "hl7PSUReceivingApplication", ext.getHl7PSUReceivingApplications());
        LdapUtils.storeNotNullOrDef(attrs, "hl7PSUDelay", ext.getHl7PSUDelay(), null);
        LdapUtils.storeNotNullOrDef(attrs, "hl7PSUTimeout", ext.getHl7PSUTimeout(), null);
        LdapUtils.storeNotNullOrDef(attrs, "hl7PSUOnTimeout", ext.getHl7PSUOnTimeout(), null);
        LdapUtils.storeNotNullOrDef(attrs, "hl7PSUMWL", ext.getHl7PSUMWL(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmAcceptConflictingPatientID", ext.getAcceptConflictingPatientID(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmCopyMoveUpdatePolicy", ext.getCopyMoveUpdatePolicy(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmInvokeImageDisplayPatientURL", ext.getInvokeImageDisplayPatientURL(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmInvokeImageDisplayStudyURL", ext.getInvokeImageDisplayStudyURL(), null);
    }

    @Override
    protected void loadFrom(ApplicationEntity ae, Attributes attrs) throws NamingException {
        if (!LdapUtils.hasObjectClass(attrs, "dcmArchiveNetworkAE"))
            return;

        ArchiveAEExtension ext = new ArchiveAEExtension();
        ae.addAEExtension(ext);
        ext.setObjectStorageIDs(LdapUtils.stringArray(attrs.get("dcmObjectStorageID")));
        ext.setObjectStorageCount(LdapUtils.intValue(attrs.get("dcmObjectStorageCount"), 1));
        ext.setMetadataStorageIDs(LdapUtils.stringArray(attrs.get("dcmMetadataStorageID")));
        ext.setSeriesMetadataDelay(toDuration(attrs.get("dcmSeriesMetadataDelay"), null));
        ext.setPurgeInstanceRecordsDelay(toDuration(attrs.get("dcmPurgeInstanceRecordsDelay"), null));
        ext.setStoreAccessControlID(LdapUtils.stringValue(attrs.get("dcmStoreAccessControlID"), null));
        ext.setAccessControlIDs(LdapUtils.stringArray(attrs.get("dcmAccessControlID")));
        ext.setOverwritePolicy(LdapUtils.enumValue(OverwritePolicy.class, attrs.get("dcmOverwritePolicy"), null));
        ext.setBulkDataSpoolDirectory(LdapUtils.stringValue(attrs.get("dcmBulkDataSpoolDirectory"), null));
        ext.setQueryRetrieveViewID(LdapUtils.stringValue(attrs.get("dcmQueryRetrieveViewID"), null));
        ext.setPersonNameComponentOrderInsensitiveMatching(
                LdapUtils.booleanValue(attrs.get("dcmPersonNameComponentOrderInsensitiveMatching"), null));
        ext.setSendPendingCGet(LdapUtils.booleanValue(attrs.get("dcmSendPendingCGet"), null));
        ext.setSendPendingCMoveInterval(toDuration(attrs.get("dcmSendPendingCMoveInterval"), null));
        ext.setFallbackCMoveSCP(LdapUtils.stringValue(attrs.get("dcmFallbackCMoveSCP"), null));
        ext.setFallbackCMoveSCPDestination(LdapUtils.stringValue(attrs.get("dcmFallbackCMoveSCPDestination"), null));
        ext.setFallbackCMoveSCPRetries(LdapUtils.intValue(attrs.get("dcmFallbackCMoveSCPRetries"), 0));
        ext.setFallbackCMoveSCPLeadingCFindSCP(LdapUtils.stringValue(attrs.get("dcmFallbackCMoveSCPLeadingCFindSCP"), null));
        ext.setAlternativeCMoveSCP(LdapUtils.stringValue(attrs.get("dcmAltCMoveSCP"), null));
        ext.setDiffStudiesIncludefieldAll(tags(attrs.get("dcmDiffStudiesIncludefieldAll")));
        ext.setWadoSR2HtmlTemplateURI(LdapUtils.stringValue(attrs.get("dcmWadoSR2HtmlTemplateURI"), null));
        ext.setWadoSR2TextTemplateURI(LdapUtils.stringValue(attrs.get("dcmWadoSR2TextTemplateURI"), null));
        ext.setQidoMaxNumberOfResults(LdapUtils.intValue(attrs.get("dcmQidoMaxNumberOfResults"), 100));
        ext.setMppsForwardDestinations(LdapUtils.stringArray(attrs.get("dcmFwdMppsDestination")));
        ext.setIanDestinations(LdapUtils.stringArray(attrs.get("dcmIanDestination")));
        ext.setIanDelay(toDuration(attrs.get("dcmIanDelay"), null));
        ext.setIanTimeout(toDuration(attrs.get("dcmIanTimeout"), null));
        ext.setIanOnTimeout(LdapUtils.booleanValue(attrs.get("dcmIanOnTimeout"), null));
        ext.setHideSPSWithStatusFromMWL(LdapUtils.enumArray(SPSStatus.class, attrs.get("dcmHideSPSWithStatusFromMWL")));
        ext.setFallbackCMoveSCPStudyOlderThan(LdapUtils.stringValue(attrs.get("dcmFallbackCMoveSCPStudyOlderThan"), null));
        ext.setStorePermissionServiceURL(LdapUtils.stringValue(attrs.get("dcmStorePermissionServiceURL"), null));
        ext.setStorePermissionServiceResponsePattern(toPattern(attrs.get("dcmStorePermissionServiceResponsePattern")));
        ext.setAllowRejectionForDataRetentionPolicyExpired(
                LdapUtils.enumValue(AllowRejectionForDataRetentionPolicyExpired.class,
                        attrs.get("dcmAllowRejectionForDataRetentionPolicyExpired"), null));
        ext.setAcceptedUserRoles(LdapUtils.stringArray(attrs.get("dcmAcceptedUserRole")));
        ext.setAcceptMissingPatientID(
                LdapUtils.enumValue(AcceptMissingPatientID.class, attrs.get("dcmAcceptMissingPatientID"), null));
        ext.setAllowDeleteStudyPermanently(LdapUtils.enumValue(AllowDeleteStudyPermanently.class, attrs.get("dcmAllowDeleteStudyPermanently"), null));
        ext.setStorePermissionServiceExpirationDatePattern(toPattern(attrs.get("dcmStorePermissionServiceExpirationDatePattern")));
        ext.setDefaultCharacterSet(LdapUtils.stringValue(attrs.get("dcmDefaultCharacterSet"), null));
        ext.setStorePermissionServiceErrorCommentPattern(toPattern(attrs.get("dcmStorePermissionServiceErrorCommentPattern")));
        ext.setStorePermissionServiceErrorCodePattern(toPattern(attrs.get("dcmStorePermissionServiceErrorCodePattern")));
        ext.setRetrieveAETitles(LdapUtils.stringArray(attrs.get("dcmRetrieveAET")));
        ext.setExternalRetrieveAEDestination(LdapUtils.stringValue(attrs.get("dcmExternalRetrieveAEDestination"), null));
        ext.setAcceptedMoveDestinations(LdapUtils.stringArray(attrs.get("dcmAcceptedMoveDestination")));
        ext.setValidateCallingAEHostname(LdapUtils.booleanValue(attrs.get("dcmValidateCallingAEHostname"), null));
        ext.setHl7PSUSendingApplication(LdapUtils.stringValue(attrs.get("hl7PSUSendingApplication"), null));
        ext.setHl7PSUReceivingApplications(LdapUtils.stringArray(attrs.get("hl7PSUReceivingApplication")));
        ext.setHl7PSUDelay(toDuration(attrs.get("hl7PSUDelay"), null));
        ext.setHl7PSUTimeout(toDuration(attrs.get("hl7PSUTimeout"), null));
        ext.setHl7PSUOnTimeout(LdapUtils.booleanValue(attrs.get("hl7PSUOnTimeout"), null));
        ext.setHl7PSUMWL(LdapUtils.booleanValue(attrs.get("hl7PSUMWL"), null));
        ext.setAcceptConflictingPatientID(
                LdapUtils.enumValue(AcceptConflictingPatientID.class, attrs.get("dcmAcceptConflictingPatientID"), null));
        ext.setCopyMoveUpdatePolicy(LdapUtils.enumValue(org.dcm4che3.data.Attributes.UpdatePolicy.class, attrs.get("dcmCopyMoveUpdatePolicy"), null));
        ext.setInvokeImageDisplayPatientURL(LdapUtils.stringValue(attrs.get("dcmInvokeImageDisplayPatientURL"), null));
        ext.setInvokeImageDisplayStudyURL(LdapUtils.stringValue(attrs.get("dcmInvokeImageDisplayStudyURL"), null));
    }

    @Override
    protected void storeDiffs(ApplicationEntity prev, ApplicationEntity ae, List<ModificationItem> mods) {
        ArchiveAEExtension aa = prev.getAEExtension(ArchiveAEExtension.class);
        ArchiveAEExtension bb = ae.getAEExtension(ArchiveAEExtension.class);
        if (aa == null && bb == null)
            return;

        boolean remove = bb == null;
        if (remove) {
            bb = new ArchiveAEExtension();
        } else if (aa == null) {
            aa = new ArchiveAEExtension();
            mods.add(new ModificationItem(DirContext.ADD_ATTRIBUTE,
                    LdapUtils.attr("objectClass", "dcmArchiveNetworkAE")));
        }
        LdapUtils.storeDiff(mods, "dcmObjectStorageID", aa.getObjectStorageIDs(), bb.getObjectStorageIDs());
        LdapUtils.storeDiffObject(mods, "dcmObjectStorageCount", aa.getObjectStorageCount(), bb.getObjectStorageCount(), null);
        LdapUtils.storeDiff(mods, "dcmMetadataStorageID", aa.getMetadataStorageIDs(), bb.getMetadataStorageIDs());
        LdapUtils.storeDiffObject(mods, "dcmSeriesMetadataDelay",
                aa.getSeriesMetadataDelay(),
                bb.getSeriesMetadataDelay(), null);
        LdapUtils.storeDiffObject(mods, "dcmPurgeInstanceRecordsDelay",
                aa.getPurgeInstanceRecordsDelay(),
                bb.getPurgeInstanceRecordsDelay(), null);
        LdapUtils.storeDiffObject(mods, "dcmStoreAccessControlID", aa.getStoreAccessControlID(), bb.getStoreAccessControlID(), null);
        LdapUtils.storeDiff(mods, "dcmAccessControlIDs", aa.getAccessControlIDs(), bb.getAccessControlIDs());
        LdapUtils.storeDiffObject(mods, "dcmOverwritePolicy", aa.getOverwritePolicy(), bb.getOverwritePolicy(), null);
        LdapUtils.storeDiffObject(mods, "dcmBulkDataSpoolDirectory",
                aa.getBulkDataSpoolDirectory(), bb.getBulkDataSpoolDirectory(), null);
        LdapUtils.storeDiffObject(mods, "dcmQueryRetrieveViewID", aa.getQueryRetrieveViewID(), bb.getQueryRetrieveViewID(), null);
        LdapUtils.storeDiffObject(mods, "dcmPersonNameComponentOrderInsensitiveMatching",
                aa.getPersonNameComponentOrderInsensitiveMatching(),
                bb.getPersonNameComponentOrderInsensitiveMatching(), null);
        LdapUtils.storeDiffObject(mods, "dcmSendPendingCGet", aa.getSendPendingCGet(), bb.getSendPendingCGet(), null);
        LdapUtils.storeDiffObject(mods, "dcmSendPendingCMoveInterval",
                aa.getSendPendingCMoveInterval(), bb.getSendPendingCMoveInterval(), null);
        LdapUtils.storeDiffObject(mods, "dcmFallbackCMoveSCP", aa.getFallbackCMoveSCP(), bb.getFallbackCMoveSCP(), null);
        LdapUtils.storeDiffObject(mods, "dcmFallbackCMoveSCPDestination",
                aa.getFallbackCMoveSCPDestination(), bb.getFallbackCMoveSCPDestination(), null);
        LdapUtils.storeDiff(mods, "dcmFallbackCMoveSCPRetries",
                aa.getFallbackCMoveSCPRetries(), bb.getFallbackCMoveSCPRetries(),  0);
        LdapUtils.storeDiffObject(mods, "dcmFallbackCMoveSCPLeadingCFindSCP",
                aa.getFallbackCMoveSCPLeadingCFindSCP(), bb.getFallbackCMoveSCPLeadingCFindSCP(), null);
        LdapUtils.storeDiffObject(mods, "dcmAltCMoveSCP", aa.getAlternativeCMoveSCP(), bb.getAlternativeCMoveSCP(), null);
        storeDiffTags(mods, "dcmDiffStudiesIncludefieldAll", aa.getDiffStudiesIncludefieldAll(), bb.getDiffStudiesIncludefieldAll());
        LdapUtils.storeDiffObject(mods, "dcmWadoSR2HtmlTemplateURI",
                aa.getWadoSR2HtmlTemplateURI(), bb.getWadoSR2HtmlTemplateURI(), null);
        LdapUtils.storeDiffObject(mods, "dcmWadoSR2TextTemplateURI",
                aa.getWadoSR2TextTemplateURI(), bb.getWadoSR2TextTemplateURI(), null);
        LdapUtils.storeDiff(mods, "dcmQidoMaxNumberOfResults",
                aa.getQidoMaxNumberOfResults(), bb.getQidoMaxNumberOfResults(), 100);
        LdapUtils.storeDiff(mods, "dcmFwdMppsDestination",
                aa.getMppsForwardDestinations(), bb.getMppsForwardDestinations());
        LdapUtils.storeDiff(mods, "dcmIanDestination", aa.getIanDestinations(), bb.getIanDestinations());
        LdapUtils.storeDiffObject(mods, "dcmIanDelay", aa.getIanDelay(), bb.getIanDelay(), null);
        LdapUtils.storeDiffObject(mods, "dcmIanTimeout", aa.getIanTimeout(), bb.getIanTimeout(), null);
        LdapUtils.storeDiffObject(mods, "dcmIanOnTimeout", aa.getIanOnTimeout(), bb.getIanOnTimeout(), null);
        LdapUtils.storeDiff(mods, "dcmHideSPSWithStatusFromMWL", aa.getHideSPSWithStatusFromMWL(), bb.getHideSPSWithStatusFromMWL());
        LdapUtils.storeDiffObject(mods, "dcmFallbackCMoveSCPStudyOlderThan",
                aa.getFallbackCMoveSCPStudyOlderThan(), bb.getFallbackCMoveSCPStudyOlderThan(), null);
        LdapUtils.storeDiffObject(mods, "dcmStorePermissionServiceURL",
                aa.getStorePermissionServiceURL(), bb.getStorePermissionServiceURL(), null);
        LdapUtils.storeDiffObject(mods, "dcmStorePermissionServiceResponsePattern",
                aa.getStorePermissionServiceResponsePattern(), bb.getStorePermissionServiceResponsePattern(), null);
        LdapUtils.storeDiffObject(mods, "dcmAllowRejectionForDataRetentionPolicyExpired",
                aa.getAllowRejectionForDataRetentionPolicyExpired(), bb.getAllowRejectionForDataRetentionPolicyExpired(), null);
        LdapUtils.storeDiff(mods, "dcmAcceptedUserRole", aa.getAcceptedUserRoles(), bb.getAcceptedUserRoles());
        LdapUtils.storeDiffObject(mods, "dcmAcceptMissingPatientID", aa.getAcceptMissingPatientID(), bb.getAcceptMissingPatientID(), null);
        LdapUtils.storeDiffObject(mods, "dcmAllowDeleteStudyPermanently", aa.getAllowDeleteStudyPermanently(), bb.getAllowDeleteStudyPermanently(), null);
        LdapUtils.storeDiffObject(mods, "dcmStorePermissionServiceExpirationDatePattern",
                aa.getStorePermissionServiceExpirationDatePattern(), bb.getStorePermissionServiceExpirationDatePattern(), null);
        LdapUtils.storeDiffObject(mods, "dcmDefaultCharacterSet", aa.getDefaultCharacterSet(), bb.getDefaultCharacterSet(), null);
        LdapUtils.storeDiffObject(mods, "dcmStorePermissionServiceErrorCommentPattern",
                aa.getStorePermissionServiceErrorCommentPattern(), bb.getStorePermissionServiceErrorCommentPattern(), null);
        LdapUtils.storeDiffObject(mods, "dcmStorePermissionServiceErrorCodePattern",
                aa.getStorePermissionServiceErrorCodePattern(), bb.getStorePermissionServiceErrorCodePattern(), null);
        LdapUtils.storeDiff(mods, "dcmRetrieveAET", aa.getRetrieveAETitles(), bb.getRetrieveAETitles());
        LdapUtils.storeDiffObject(mods, "dcmExternalRetrieveAEDestination",
                aa.getExternalRetrieveAEDestination(), bb.getExternalRetrieveAEDestination(), null);
        LdapUtils.storeDiff(mods, "dcmAcceptedMoveDestination", aa.getAcceptedMoveDestinations(), bb.getAcceptedMoveDestinations());
        LdapUtils.storeDiffObject(mods, "dcmValidateCallingAEHostname", aa.getValidateCallingAEHostname(), bb.getValidateCallingAEHostname(), null);
        LdapUtils.storeDiffObject(mods, "hl7PSUSendingApplication", aa.getHl7PSUSendingApplication(), bb.getHl7PSUSendingApplication(), null);
        LdapUtils.storeDiff(mods, "hl7PSUReceivingApplication", aa.getHl7PSUReceivingApplications(), bb.getHl7PSUReceivingApplications());
        LdapUtils.storeDiffObject(mods, "hl7PSUDelay", aa.getHl7PSUDelay(), bb.getHl7PSUDelay(), null);
        LdapUtils.storeDiffObject(mods, "hl7PSUTimeout", aa.getHl7PSUTimeout(), bb.getHl7PSUTimeout(), null);
        LdapUtils.storeDiffObject(mods, "hl7PSUOnTimeout", aa.getHl7PSUOnTimeout(), bb.getHl7PSUOnTimeout(), null);
        LdapUtils.storeDiffObject(mods, "hl7PSUMWL", aa.getHl7PSUMWL(), bb.getHl7PSUMWL(), null);
        LdapUtils.storeDiffObject(mods, "dcmAcceptConflictingPatientID", aa.getAcceptConflictingPatientID(), bb.getAcceptConflictingPatientID(), null);
        LdapUtils.storeDiffObject(mods, "dcmCopyMoveUpdatePolicy", aa.getCopyMoveUpdatePolicy(), bb.getCopyMoveUpdatePolicy(), null);
        LdapUtils.storeDiffObject(mods, "dcmInvokeImageDisplayPatientURL", aa.getInvokeImageDisplayPatientURL(), bb.getInvokeImageDisplayPatientURL(), null);
        LdapUtils.storeDiffObject(mods, "dcmInvokeImageDisplayStudyURL", aa.getInvokeImageDisplayStudyURL(), bb.getInvokeImageDisplayStudyURL(), null);
        if (remove)
            mods.add(new ModificationItem(DirContext.REMOVE_ATTRIBUTE,
                    LdapUtils.attr("objectClass", "dcmArchiveNetworkAE")));
    }

    @Override
    protected void storeChilds(String aeDN, ApplicationEntity ae) throws NamingException {
        ArchiveAEExtension aeExt = ae.getAEExtension(ArchiveAEExtension.class);
        if (aeExt == null)
            return;

        storeExportRules(aeExt.getExportRules(), aeDN);
        storeCompressionRules(aeExt.getCompressionRules(), aeDN);
        storeStoreAccessControlIDRules(aeExt.getStoreAccessControlIDRules(), aeDN);
        storeAttributeCoercions(aeExt.getAttributeCoercions(), aeDN);
        storeStudyRetentionPolicies(aeExt.getStudyRetentionPolicies(), aeDN);
        storeRSForwardRules(aeExt.getRSForwardRules(), aeDN);
    }

    @Override
    protected void loadChilds(ApplicationEntity ae, String aeDN) throws NamingException {
        ArchiveAEExtension aeExt = ae.getAEExtension(ArchiveAEExtension.class);
        if (aeExt == null)
            return;

        loadExportRules(aeExt.getExportRules(), aeDN);
        loadCompressionRules(aeExt.getCompressionRules(), aeDN);
        loadStoreAccessControlIDRules(aeExt.getStoreAccessControlIDRules(), aeDN);
        loadAttributeCoercions(aeExt.getAttributeCoercions(), aeDN);
        loadStudyRetentionPolicies(aeExt.getStudyRetentionPolicies(), aeDN);
        loadRSForwardRules(aeExt.getRSForwardRules(), aeDN);
    }

    @Override
    protected void mergeChilds(ApplicationEntity prev, ApplicationEntity ae, String aeDN) throws NamingException {
        ArchiveAEExtension aa = prev.getAEExtension(ArchiveAEExtension.class);
        ArchiveAEExtension bb = ae.getAEExtension(ArchiveAEExtension.class);
        if (aa == null && bb == null)
            return;

        if (aa == null)
            aa = new ArchiveAEExtension();
        else if (bb == null)
            bb = new ArchiveAEExtension();

        mergeExportRules(aa.getExportRules(), bb.getExportRules(), aeDN);
        mergeCompressionRules(aa.getCompressionRules(), bb.getCompressionRules(), aeDN);
        mergeStoreAccessControlIDRules(aa.getStoreAccessControlIDRules(), bb.getStoreAccessControlIDRules(), aeDN);
        mergeAttributeCoercions(aa.getAttributeCoercions(), bb.getAttributeCoercions(), aeDN);
        mergeStudyRetentionPolicies(aa.getStudyRetentionPolicies(), bb.getStudyRetentionPolicies(), aeDN);
        mergeRSForwardRules(aa.getRSForwardRules(), bb.getRSForwardRules(), aeDN);
    }

    private void storeAttributeFilter(String deviceDN, ArchiveDeviceExtension arcDev)
            throws NamingException {
        for (Map.Entry<Entity, AttributeFilter> entry : arcDev.getAttributeFilters().entrySet()) {
            config.createSubcontext(
                    LdapUtils.dnOf("dcmEntity", entry.getKey().name(), deviceDN),
                    storeTo(entry.getValue(), entry.getKey(), new BasicAttributes(true)));
        }
    }

    private void storeMetadataFilter(String deviceDN, ArchiveDeviceExtension arcDev)
            throws NamingException {
        for (Map.Entry<String, MetadataFilter> entry : arcDev.getMetadataFilters().entrySet()) {
            config.createSubcontext(
                    LdapUtils.dnOf("dcmMetadataFilterName", entry.getKey(), deviceDN),
                    storeTo(entry.getValue(), entry.getKey(), new BasicAttributes(true)));
        }
    }

    private static Attributes storeTo(AttributeFilter filter, Entity entity,  BasicAttributes attrs) {
        attrs.put("objectclass", "dcmAttributeFilter");
        attrs.put("dcmEntity", entity.name());
        storeNotEmptyTags(attrs, "dcmTag", filter.getSelection());
        LdapUtils.storeNotNullOrDef(attrs, "dcmCustomAttribute1", filter.getCustomAttribute1(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmCustomAttribute2", filter.getCustomAttribute2(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmCustomAttribute3", filter.getCustomAttribute3(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmAttributeUpdatePolicy", filter.getAttributeUpdatePolicy(), null);
        return attrs;
    }

    private static Attributes storeTo(MetadataFilter filter, String filterName,  BasicAttributes attrs) {
        attrs.put("objectclass", "dcmMetadataFilter");
        attrs.put("dcmMetadataFilterName", filterName);
        storeNotEmptyTags(attrs, "dcmTag", filter.getSelection());
        return attrs;
    }

    private static Attributes storeTo(HL7OrderSPSStatus hl7OrderSPSStatus, SPSStatus spsStatus, BasicAttributes attrs) {
        attrs.put("objectclass", "hl7OrderSPSStatus");
        attrs.put("dcmSPSStatus", spsStatus.name());
        LdapUtils.storeNotEmpty(attrs, "hl7OrderControlStatus", hl7OrderSPSStatus.getOrderControlStatusCodes());
        return attrs;
    }

    private static void storeNotEmptyTags(Attributes attrs, String attrid, int[] vals) {
        if (vals != null && vals.length > 0)
            attrs.put(tagsAttr(attrid, vals));
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

    private void loadMetadataFilters(ArchiveDeviceExtension device, String deviceDN)
            throws NamingException {
        NamingEnumeration<SearchResult> ne = config.search(deviceDN, "(objectclass=dcmMetadataFilter)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                MetadataFilter filter = new MetadataFilter();
                filter.setName(LdapUtils.stringValue(attrs.get("dcmMetadataFilterName"), null));
                filter.setSelection(tags(attrs.get("dcmTag")));
                device.addMetadataFilter(filter);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
    }

    protected static void loadHL7OrderSPSStatus(
            Map<SPSStatus, HL7OrderSPSStatus> hl7OrderSPSStatusMap, String deviceDN, LdapDicomConfiguration config)
            throws NamingException {
        NamingEnumeration<SearchResult> ne = config.search(deviceDN, "(objectclass=hl7OrderSPSStatus)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                HL7OrderSPSStatus hl7OrderSPSStatus = new HL7OrderSPSStatus();
                hl7OrderSPSStatus.setSPSStatus(SPSStatus.valueOf(LdapUtils.stringValue(attrs.get("dcmSPSStatus"), null)));
                hl7OrderSPSStatus.setOrderControlStatusCodes(LdapUtils.stringArray(attrs.get("hl7OrderControlStatus")));
                hl7OrderSPSStatusMap.put(hl7OrderSPSStatus.getSPSStatus(), hl7OrderSPSStatus);
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
        if (attr == null)
            return ByteUtils.EMPTY_INTS;

        int[] is = new int[attr.size()];
        for (int i = 0; i < is.length; i++)
            is[i] = TagUtils.intFromHexString((String) attr.get(i));

        return is;
    }

    private void mergeAttributeFilters(ArchiveDeviceExtension prev, ArchiveDeviceExtension arcDev,
                                       String deviceDN) throws NamingException {
        for (Entity entity : prev.getAttributeFilters().keySet())
            if (!arcDev.getAttributeFilters().containsKey(entity))
                config.destroySubcontext(LdapUtils.dnOf("dcmEntity", entity.name(), deviceDN));
        for (Map.Entry<Entity, AttributeFilter> entry : arcDev.getAttributeFilters().entrySet()) {
            Entity entity = entry.getKey();
            String dn = LdapUtils.dnOf("dcmEntity", entity.name(), deviceDN);
            AttributeFilter prevFilter = prev.getAttributeFilters().get(entity);
            if (prevFilter == null)
                config.createSubcontext(dn,
                        storeTo(entry.getValue(), entity, new BasicAttributes(true)));
            else
                config.modifyAttributes(dn,
                        storeDiffs(prevFilter, entry.getValue(), new ArrayList<ModificationItem>()));
        }
    }

    private void mergeMetadataFilters(ArchiveDeviceExtension prev, ArchiveDeviceExtension arcDev,
                                       String deviceDN) throws NamingException {
        for (String filterName : prev.getMetadataFilters().keySet())
            if (!arcDev.getMetadataFilters().containsKey(filterName))
                config.destroySubcontext(LdapUtils.dnOf("dcmMetadataFilterName", filterName, deviceDN));
        for (Map.Entry<String, MetadataFilter> entry : arcDev.getMetadataFilters().entrySet()) {
            String filterName = entry.getKey();
            String dn = LdapUtils.dnOf("dcmMetadataFilterName", filterName, deviceDN);
            MetadataFilter prevFilter = prev.getMetadataFilters().get(filterName);
            if (prevFilter == null)
                config.createSubcontext(dn,
                        storeTo(entry.getValue(), filterName, new BasicAttributes(true)));
            else
                config.modifyAttributes(dn,
                        storeDiffs(prevFilter, entry.getValue(), new ArrayList<ModificationItem>()));
        }
    }

    protected static void mergeHL7OrderSPSStatus(
            Map<SPSStatus, HL7OrderSPSStatus> prev, Map<SPSStatus, HL7OrderSPSStatus> hl7OrderSPSStatusMap, String deviceDN,
            LdapDicomConfiguration config) throws NamingException {
        for (SPSStatus spsStatus : prev.keySet())
            if (!hl7OrderSPSStatusMap.containsKey(spsStatus))
                config.destroySubcontext(LdapUtils.dnOf("dcmSPSStatus", spsStatus.toString(), deviceDN));
        for (Map.Entry<SPSStatus, HL7OrderSPSStatus> entry : hl7OrderSPSStatusMap.entrySet()) {
            SPSStatus spsStatus = entry.getKey();
            String dn = LdapUtils.dnOf("dcmSPSStatus", spsStatus.toString(), deviceDN);
            HL7OrderSPSStatus prevHL7OrderSPSStatus = prev.get(spsStatus);
            if (prevHL7OrderSPSStatus == null)
                config.createSubcontext(dn, storeTo(entry.getValue(), spsStatus, new BasicAttributes(true)));
            else
                config.modifyAttributes(dn, storeDiffs(prevHL7OrderSPSStatus, entry.getValue(), new ArrayList<ModificationItem>()));
        }
    }

    private List<ModificationItem> storeDiffs(AttributeFilter prev, AttributeFilter filter,
                                              List<ModificationItem> mods) {
        storeDiffTags(mods, "dcmTag", prev.getSelection(), filter.getSelection());
        LdapUtils.storeDiffObject(mods, "dcmCustomAttribute1",
                prev.getCustomAttribute1(), filter.getCustomAttribute1(), null);
        LdapUtils.storeDiffObject(mods, "dcmCustomAttribute2",
                prev.getCustomAttribute2(), filter.getCustomAttribute2(), null);
        LdapUtils.storeDiffObject(mods, "dcmCustomAttribute3",
                prev.getCustomAttribute3(), filter.getCustomAttribute3(), null);
        LdapUtils.storeDiffObject(mods, "dcmAttributeUpdatePolicy",
                prev.getAttributeUpdatePolicy(), filter.getAttributeUpdatePolicy(), null);
        return mods;
    }

    private List<ModificationItem> storeDiffs(MetadataFilter prev, MetadataFilter filter,
                                              List<ModificationItem> mods) {
        storeDiffTags(mods, "dcmTag", prev.getSelection(), filter.getSelection());
        return mods;
    }

    private void storeDiffTags(List<ModificationItem> mods, String attrId, int[] prevs, int[] vals) {
        if (!Arrays.equals(prevs, vals))
            mods.add((vals != null && vals.length == 0)
                    ? new ModificationItem(DirContext.REMOVE_ATTRIBUTE, new BasicAttribute(attrId))
                    : new ModificationItem(DirContext.REPLACE_ATTRIBUTE, tagsAttr(attrId, vals)));
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
        LdapUtils.storeNotNullOrDef(attrs, "dcmDigestAlgorithm", descriptor.getDigestAlgorithm(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmInstanceAvailability",
                descriptor.getInstanceAvailability(), Availability.ONLINE);
        LdapUtils.storeNotDef(attrs, "dcmReadOnly", descriptor.isReadOnly(), false);
        LdapUtils.storeNotNullOrDef(attrs, "dcmStorageThreshold", descriptor.getStorageThreshold(), null);
        LdapUtils.storeNotEmpty(attrs, "dcmDeleterThreshold", descriptor.getDeleterThresholdsAsStrings());
        LdapUtils.storeNotEmpty(attrs, "dcmProperty", toStrings(descriptor.getProperties()));
        LdapUtils.storeNotNullOrDef(attrs, "dcmExternalRetrieveAET", descriptor.getExternalRetrieveAETitle(), null);
        return attrs;
    }

    private static String[] toStrings(Map<String, ?> props) {
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
                        LdapUtils.enumValue(Availability.class, attrs.get("dcmInstanceAvailability"), Availability.ONLINE));
                desc.setReadOnly(LdapUtils.booleanValue(attrs.get("dcmReadOnly"), false));
                desc.setStorageThreshold(toStorageThreshold(attrs.get("dcmStorageThreshold")));
                desc.setDeleterThresholdsFromStrings(LdapUtils.stringArray(attrs.get("dcmDeleterThreshold")));
                desc.setProperties(LdapUtils.stringArray(attrs.get("dcmProperty")));
                desc.setExternalRetrieveAETitle(LdapUtils.stringValue(attrs.get("dcmExternalRetrieveAET"), null));
                arcdev.addStorageDescriptor(desc);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
    }

    private static StorageThreshold toStorageThreshold(Attribute attr) throws NamingException {
        return attr != null ? StorageThreshold.valueOf((String) attr.get()) : null;
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
        LdapUtils.storeDiffObject(mods, "dcmURI", prev.getStorageURIStr(), desc.getStorageURIStr(), null);
        LdapUtils.storeDiffObject(mods, "dcmDigestAlgorithm", prev.getDigestAlgorithm(), desc.getDigestAlgorithm(), null);
        LdapUtils.storeDiffObject(mods, "dcmInstanceAvailability",
                prev.getInstanceAvailability(), desc.getInstanceAvailability(), Availability.ONLINE);
        LdapUtils.storeDiff(mods, "dcmReadOnly", prev.isReadOnly(), desc.isReadOnly(), false);
        LdapUtils.storeDiffObject(mods, "dcmStorageThreshold", prev.getStorageThreshold(), desc.getStorageThreshold(), null);
        LdapUtils.storeDiff(mods, "dcmDeleterThreshold",
                prev.getDeleterThresholdsAsStrings(), desc.getDeleterThresholdsAsStrings());
        storeDiffProperties(mods, prev.getProperties(), desc.getProperties());
        LdapUtils.storeDiffObject(mods, "dcmExternalRetrieveAET", prev.getExternalRetrieveAETitle(), desc.getExternalRetrieveAETitle(), null);
        return mods;
    }

    private static void storeDiffProperties(List<ModificationItem> mods, Map<String, ?> prev, Map<String, ?> props) {
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
        LdapUtils.storeNotNullOrDef(attrs, "dcmJndiName", descriptor.getJndiName(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dicomDescription", descriptor.getDescription(), null);
        LdapUtils.storeNotDef(attrs, "dcmMaxRetries", descriptor.getMaxRetries(), 0);
        LdapUtils.storeNotNullOrDef(attrs, "dcmRetryDelay",
                descriptor.getRetryDelay(), QueueDescriptor.DEFAULT_RETRY_DELAY);
        LdapUtils.storeNotNullOrDef(attrs, "dcmMaxRetryDelay", descriptor.getMaxRetryDelay(), null);
        LdapUtils.storeNotDef(attrs, "dcmRetryDelayMultiplier", descriptor.getRetryDelayMultiplier(), 100);
        LdapUtils.storeNotDef(attrs, "dcmRetryOnWarning", descriptor.isRetryOnWarning(), false);
        LdapUtils.storeNotNullOrDef(attrs, "dcmPurgeQueueMessageCompletedDelay", descriptor.getPurgeQueueMessageCompletedDelay(), null);
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
                desc.setRetryDelay(toDuration(attrs.get("dcmRetryDelay"), QueueDescriptor.DEFAULT_RETRY_DELAY));
                desc.setMaxRetryDelay(toDuration(attrs.get("dcmMaxRetryDelay"), null));
                desc.setRetryDelayMultiplier(LdapUtils.intValue(attrs.get("dcmRetryDelayMultiplier"), 100));
                desc.setRetryOnWarning(LdapUtils.booleanValue(attrs.get("dcmRetryOnWarning"), false));
                desc.setPurgeQueueMessageCompletedDelay(toDuration(attrs.get("dcmPurgeQueueMessageCompletedDelay"), null));
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
        LdapUtils.storeDiffObject(mods, "dicomDescription",
                prev.getDescription(), desc.getDescription(), null);
        LdapUtils.storeDiffObject(mods, "dcmJndiName", prev.getJndiName(), desc.getJndiName(), null);
        LdapUtils.storeDiff(mods, "dcmMaxRetries", prev.getMaxRetries(), desc.getMaxRetries(), 0);
        LdapUtils.storeDiffObject(mods, "dcmRetryDelay",
                prev.getRetryDelay(), desc.getRetryDelay(), QueueDescriptor.DEFAULT_RETRY_DELAY);
        LdapUtils.storeDiffObject(mods, "dcmMaxRetryDelay",
                prev.getMaxRetryDelay(), desc.getMaxRetryDelay(), null);
        LdapUtils.storeDiff(mods, "dcmRetryDelayMultiplier",
                prev.getRetryDelayMultiplier(), desc.getRetryDelayMultiplier(), 100);
        LdapUtils.storeDiff(mods, "dcmRetryOnWarning", prev.isRetryOnWarning(), desc.isRetryOnWarning(), false);
        LdapUtils.storeDiffObject(mods, "dcmPurgeQueueMessageCompletedDelay",
                prev.getPurgeQueueMessageCompletedDelay(), desc.getPurgeQueueMessageCompletedDelay(), null);
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
        LdapUtils.storeNotNullOrDef(attrs, "dcmURI", descriptor.getExportURI(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dicomDescription", descriptor.getDescription(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmQueueName", descriptor.getQueueName(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dicomAETitle", descriptor.getAETitle(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmStgCmtSCP", descriptor.getStgCmtSCPAETitle(), null);
        LdapUtils.storeNotEmpty(attrs, "dcmIanDestination", descriptor.getIanDestinations());
        LdapUtils.storeNotEmpty(attrs, "dcmRetrieveAET", descriptor.getRetrieveAETitles());
        LdapUtils.storeNotNullOrDef(attrs, "dcmRetrieveLocationUID", descriptor.getRetrieveLocationUID(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmInstanceAvailability",
                descriptor.getInstanceAvailability(), Availability.ONLINE);
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
                desc.setExportURI(toURI(attrs.get("dcmURI")));
                desc.setDescription(LdapUtils.stringValue(attrs.get("dicomDescription"), null));
                desc.setQueueName(LdapUtils.stringValue(attrs.get("dcmQueueName"), null));
                desc.setAETitle(LdapUtils.stringValue(attrs.get("dicomAETitle"), null));
                desc.setStgCmtSCPAETitle(LdapUtils.stringValue(attrs.get("dcmStgCmtSCP"), null));
                desc.setIanDestinations(LdapUtils.stringArray(attrs.get("dcmIanDestination")));
                desc.setRetrieveAETitles(LdapUtils.stringArray(attrs.get("dcmRetrieveAET")));
                desc.setRetrieveLocationUID(LdapUtils.stringValue(attrs.get("dcmRetrieveLocationUID"), null));
                desc.setInstanceAvailability(
                        LdapUtils.enumValue(Availability.class, attrs.get("dcmInstanceAvailability"), Availability.ONLINE));
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
        LdapUtils.storeDiffObject(mods, "dcmURI", prev.getExportURI().toString(), desc.getExportURI().toString(), null);
        LdapUtils.storeDiffObject(mods, "dicomDescription", prev.getDescription(), desc.getDescription(), null);
        LdapUtils.storeDiffObject(mods, "dcmQueueName", prev.getQueueName(), desc.getQueueName(), null);
        LdapUtils.storeDiffObject(mods, "dicomAETitle", prev.getAETitle(), desc.getAETitle(), null);
        LdapUtils.storeDiffObject(mods, "dcmStgCmtSCP", prev.getStgCmtSCPAETitle(), desc.getStgCmtSCPAETitle(), null);
        LdapUtils.storeDiff(mods, "dcmIanDestination", prev.getIanDestinations(), desc.getIanDestinations());
        LdapUtils.storeDiff(mods, "dcmRetrieveAET", prev.getRetrieveAETitles(), desc.getRetrieveAETitles());
        LdapUtils.storeDiffObject(mods, "dcmRetrieveLocationUID",
                prev.getRetrieveLocationUID(), desc.getRetrieveLocationUID(), null);
        LdapUtils.storeDiffObject(mods, "dcmInstanceAvailability",
                prev.getInstanceAvailability(), desc.getInstanceAvailability(), Availability.ONLINE);
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
        LdapUtils.storeNotNullOrDef(attrs, "dcmEntity", rule.getEntity(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmDuration", rule.getExportDelay(), null);
        LdapUtils.storeNotDef(attrs, "dcmExportPreviousEntity", rule.isExportPreviousEntity(), false);
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
                rule.setExportDelay(toDuration(attrs.get("dcmDuration"), null));
                rule.setExportPreviousEntity(LdapUtils.booleanValue(attrs.get("dcmExportPreviousEntity"), false));
                exportRules.add(rule);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
    }

    private static URI toURI(Attribute attr) throws NamingException {
        return attr != null ? URI.create((String) attr.get()) : null;
    }

    private static Duration toDuration(Attribute attr, Duration defValue) throws NamingException {
        return attr != null ? Duration.parse((String) attr.get()) : defValue;
    }

    private Period toPeriod(Attribute attr) throws NamingException {
        return attr != null ? Period.parse((String) attr.get()) : null;
    }

    private static LocalTime toLocalTime(Attribute attr) throws NamingException {
        return attr != null ? LocalTime.parse((String) attr.get()) : null;
    }

    private static Pattern toPattern(Attribute attr) throws NamingException {
        return attr != null ? Pattern.compile((String) attr.get()) : null;
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
        LdapUtils.storeDiffObject(mods, "dcmEntity", prev.getEntity(), rule.getEntity(), null);
        LdapUtils.storeDiffObject(mods, "dcmDuration", prev.getExportDelay(), rule.getExportDelay(), null);
        LdapUtils.storeDiff(mods, "dcmExportPreviousEntity",
                prev.isExportPreviousEntity(), rule.isExportPreviousEntity(), false);
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

    private void storeStudyRetentionPolicies(Collection<StudyRetentionPolicy> policies, String parentDN)
            throws NamingException {
        for (StudyRetentionPolicy policy : policies) {
            String cn = policy.getCommonName();
            config.createSubcontext(
                    LdapUtils.dnOf("cn", cn, parentDN),
                    storeTo(policy, new BasicAttributes(true)));
        }
    }

    private void storeStoreAccessControlIDRules(Collection<StoreAccessControlIDRule> rules, String parentDN)
            throws NamingException {
        for (StoreAccessControlIDRule rule : rules) {
            String cn = rule.getCommonName();
            config.createSubcontext(
                    LdapUtils.dnOf("cn", cn, parentDN),
                    storeTo(rule, new BasicAttributes(true)));
        }
    }


    protected static void storeHL7ForwardRules(
            Collection<HL7ForwardRule> rules, String parentDN, LdapDicomConfiguration config)
            throws NamingException{
        for (HL7ForwardRule rule : rules) {
            String cn = rule.getCommonName();
            config.createSubcontext(
                    LdapUtils.dnOf("cn", cn, parentDN),
                    storeTo(rule, new BasicAttributes(true)));
        }
    }

    protected static void storeScheduledStations(
            Collection<HL7OrderScheduledStation> stations, String parentDN, LdapDicomConfiguration config)
            throws NamingException{
        for (HL7OrderScheduledStation station : stations) {
            String cn = station.getCommonName();
            config.createSubcontext(
                    LdapUtils.dnOf("cn", cn, parentDN),
                    storeTo(station, new BasicAttributes(true), config));
        }
    }

    protected static void storeHL7OrderSPSStatus(
            Map<SPSStatus, HL7OrderSPSStatus> hl7OrderSPSStatusMap, String parentDN, LdapDicomConfiguration config)
            throws NamingException {
        for (Map.Entry<SPSStatus, HL7OrderSPSStatus> entry : hl7OrderSPSStatusMap.entrySet()) {
            config.createSubcontext(
                    LdapUtils.dnOf("dcmSPSStatus", entry.getKey().toString(), parentDN),
                    storeTo(entry.getValue(), entry.getKey(), new BasicAttributes(true)));
        }
    }

    private void storeRSForwardRules(Collection<RSForwardRule> rules, String parentDN)
            throws NamingException {
        for (RSForwardRule rule : rules) {
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
        LdapUtils.storeNotNullOrDef(attrs, "dicomTransferSyntax", rule.getTransferSyntax(), null);
        LdapUtils.storeNotEmpty(attrs, "dcmImageWriteParam", rule.getImageWriteParams());
        LdapUtils.storeNotDef(attrs, "dcmRulePriority", rule.getPriority(), 0);
        return attrs;
    }

    private Attributes storeTo(StoreAccessControlIDRule rule, BasicAttributes attrs) {
        attrs.put("objectclass", "dcmStoreAccessControlIDRule");
        attrs.put("cn", rule.getCommonName());
        LdapUtils.storeNotEmpty(attrs, "dcmProperty", toStrings(rule.getConditions().getMap()));
        LdapUtils.storeNotNullOrDef(attrs, "dcmStoreAccessControlID", rule.getStoreAccessControlID(), null);
        LdapUtils.storeNotDef(attrs, "dcmRulePriority", rule.getPriority(), 0);
        return attrs;
    }

    private Attributes storeTo(StudyRetentionPolicy policy, BasicAttributes attrs) {
        attrs.put("objectclass", "dcmStudyRetentionPolicy");
        attrs.put("cn", policy.getCommonName());
        LdapUtils.storeNotEmpty(attrs, "dcmProperty", toStrings(policy.getConditions().getMap()));
        LdapUtils.storeNotNullOrDef(attrs, "dcmRetentionPeriod", policy.getRetentionPeriod(), null);
        LdapUtils.storeNotDef(attrs, "dcmRulePriority", policy.getPriority(), 0);
        LdapUtils.storeNotDef(attrs, "dcmExpireSeriesIndividually", policy.isExpireSeriesIndividually(), false);
        return attrs;
    }

    private static Attributes storeTo(HL7ForwardRule rule, BasicAttributes attrs) {
        attrs.put("objectclass", "hl7ForwardRule");
        attrs.put("cn", rule.getCommonName());
        LdapUtils.storeNotEmpty(attrs, "hl7FwdApplicationName", rule.getDestinations());
        LdapUtils.storeNotEmpty(attrs, "dcmProperty", toStrings(rule.getConditions().getMap()));
        return attrs;
    }

    private static Attributes storeTo(HL7OrderScheduledStation station, BasicAttributes attrs, LdapDicomConfiguration config) {
        attrs.put("objectclass", "hl7OrderScheduledStation");
        attrs.put("cn", station.getCommonName());
        LdapUtils.storeNotNullOrDef(attrs, "hl7OrderScheduledStationDeviceReference", config.deviceRef(station.getDeviceName()), null);
        LdapUtils.storeNotDef(attrs, "dcmRulePriority", station.getPriority(), 0);
        LdapUtils.storeNotEmpty(attrs, "dcmProperty", toStrings(station.getConditions().getMap()));
        return attrs;
    }

    private Attributes storeTo(RSForwardRule rule, BasicAttributes attrs) {
        attrs.put("objectclass", "dcmRSForwardRule");
        attrs.put("cn", rule.getCommonName());
        LdapUtils.storeNotNullOrDef(attrs, "dcmURI", rule.getBaseURI(), null);
        LdapUtils.storeNotEmpty(attrs, "dcmRSOperation", rule.getRSOperations());
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

    private void loadStoreAccessControlIDRules(Collection<StoreAccessControlIDRule> rules, String parentDN)
            throws NamingException {
        NamingEnumeration<SearchResult> ne = config.search(parentDN, "(objectclass=dcmStoreAccessControlIDRule)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                StoreAccessControlIDRule rule = new StoreAccessControlIDRule(LdapUtils.stringValue(attrs.get("cn"), null));
                rule.setConditions(new Conditions(LdapUtils.stringArray(attrs.get("dcmProperty"))));
                rule.setStoreAccessControlID(LdapUtils.stringValue(attrs.get("dcmStoreAccessControlID"), null));
                rule.setPriority(LdapUtils.intValue(attrs.get("dcmRulePriority"), 0));
                rules.add(rule);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
    }

    private void loadStudyRetentionPolicies(Collection<StudyRetentionPolicy> policies, String parentDN)
            throws NamingException {
        NamingEnumeration<SearchResult> ne = config.search(parentDN, "(objectclass=dcmStudyRetentionPolicy)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                StudyRetentionPolicy policy = new StudyRetentionPolicy(LdapUtils.stringValue(attrs.get("cn"), null));
                policy.setConditions(new Conditions(LdapUtils.stringArray(attrs.get("dcmProperty"))));
                policy.setRetentionPeriod(toPeriod(attrs.get("dcmRetentionPeriod")));
                policy.setPriority(LdapUtils.intValue(attrs.get("dcmRulePriority"), 0));
                policy.setExpireSeriesIndividually(LdapUtils.booleanValue(attrs.get("dcmExpireSeriesIndividually"), false));
                policies.add(policy);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
    }

    protected static void loadHL7ForwardRules(
            Collection<HL7ForwardRule> rules, String parentDN, LdapDicomConfiguration config)
            throws NamingException {
        NamingEnumeration<SearchResult> ne = config.search(parentDN, "(objectclass=hl7ForwardRule)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                HL7ForwardRule rule = new HL7ForwardRule(LdapUtils.stringValue(attrs.get("cn"), null));
                rule.setDestinations(LdapUtils.stringArray(attrs.get("hl7FwdApplicationName")));
                rule.setConditions(new HL7Conditions(LdapUtils.stringArray(attrs.get("dcmProperty"))));
                rules.add(rule);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
    }

    protected static void loadScheduledStations(
            Collection<HL7OrderScheduledStation> stations, String parentDN, LdapDicomConfiguration config)
            throws NamingException, ConfigurationException {
        NamingEnumeration<SearchResult> ne = config.search(parentDN, "(objectclass=hl7OrderScheduledStation)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                HL7OrderScheduledStation station = new HL7OrderScheduledStation(LdapUtils.stringValue(attrs.get("cn"), null));
                station.setDevice(config.loadDevice(
                        LdapUtils.stringValue(attrs.get("hl7OrderScheduledStationDeviceReference"), null)));
                station.setPriority(LdapUtils.intValue(attrs.get("dcmRulePriority"), 0));
                station.setConditions(new HL7Conditions(LdapUtils.stringArray(attrs.get("dcmProperty"))));
                stations.add(station);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
    }

    private void loadRSForwardRules(Collection<RSForwardRule> rules, String parentDN)
            throws NamingException {
        NamingEnumeration<SearchResult> ne = config.search(parentDN, "(objectclass=dcmRSForwardRule)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                RSForwardRule rule = new RSForwardRule(LdapUtils.stringValue(attrs.get("cn"), null));
                rule.setBaseURI(LdapUtils.stringValue(attrs.get("dcmURI"), null));
                rule.setRSOperations(LdapUtils.enumArray(RSOperation.class, attrs.get("dcmRSOperation")));
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

    private void mergeStoreAccessControlIDRules(
            Collection<StoreAccessControlIDRule> prevRules, Collection<StoreAccessControlIDRule> rules, String parentDN)
            throws NamingException {
        for (StoreAccessControlIDRule prevRule : prevRules) {
            String cn = prevRule.getCommonName();
            if (findStoreAccessControlIDRuleByCN(rules, cn) == null)
                config.destroySubcontext(LdapUtils.dnOf("cn", cn, parentDN));
        }
        for (StoreAccessControlIDRule rule : rules) {
            String cn = rule.getCommonName();
            String dn = LdapUtils.dnOf("cn", cn, parentDN);
            StoreAccessControlIDRule prevRule = findStoreAccessControlIDRuleByCN(prevRules, cn);
            if (prevRule == null)
                config.createSubcontext(dn, storeTo(rule, new BasicAttributes(true)));
            else
                config.modifyAttributes(dn, storeDiffs(prevRule, rule, new ArrayList<ModificationItem>()));
        }
    }

    private void mergeStudyRetentionPolicies(
            Collection<StudyRetentionPolicy> prevPolicies, Collection<StudyRetentionPolicy> policies, String parentDN)
            throws NamingException {
        for (StudyRetentionPolicy prevRule : prevPolicies) {
            String cn = prevRule.getCommonName();
            if (findStudyRetentionPolicyByCN(policies, cn) == null)
                config.destroySubcontext(LdapUtils.dnOf("cn", cn, parentDN));
        }
        for (StudyRetentionPolicy policy : policies) {
            String cn = policy.getCommonName();
            String dn = LdapUtils.dnOf("cn", cn, parentDN);
            StudyRetentionPolicy prevPolicy = findStudyRetentionPolicyByCN(prevPolicies, cn);
            if (prevPolicy == null)
                config.createSubcontext(dn, storeTo(policy, new BasicAttributes(true)));
            else
                config.modifyAttributes(dn, storeDiffs(prevPolicy, policy, new ArrayList<ModificationItem>()));
        }
    }

    protected static void mergeHL7ForwardRules(Collection<HL7ForwardRule> prevRules, Collection<HL7ForwardRule> rules,
               String parentDN, LdapDicomConfiguration config)
            throws NamingException {
        for (HL7ForwardRule prevRule : prevRules) {
            String cn = prevRule.getCommonName();
            if (findHL7ForwardRuleByCN(rules, cn) == null)
                config.destroySubcontext(LdapUtils.dnOf("cn", cn, parentDN));
        }
        for (HL7ForwardRule rule : rules) {
            String cn = rule.getCommonName();
            String dn = LdapUtils.dnOf("cn", cn, parentDN);
            HL7ForwardRule prevRule = findHL7ForwardRuleByCN(prevRules, cn);
            if (prevRule == null)
                config.createSubcontext(dn, storeTo(rule, new BasicAttributes(true)));
            else
                config.modifyAttributes(dn, storeDiffs(prevRule, rule, new ArrayList<ModificationItem>()));
        }
    }

    protected static void mergeScheduledStations(Collection<HL7OrderScheduledStation> prevStations, Collection<HL7OrderScheduledStation> stations,
                                                 String parentDN, LdapDicomConfiguration config)
            throws NamingException {
        for (HL7OrderScheduledStation prevRule : prevStations) {
            String cn = prevRule.getCommonName();
            if (findScheduledStationByCN(stations, cn) == null)
                config.destroySubcontext(LdapUtils.dnOf("cn", cn, parentDN));
        }
        for (HL7OrderScheduledStation station : stations) {
            String cn = station.getCommonName();
            String dn = LdapUtils.dnOf("cn", cn, parentDN);
            HL7OrderScheduledStation prevStation = findScheduledStationByCN(prevStations, cn);
            if (prevStation == null)
                config.createSubcontext(dn, storeTo(station, new BasicAttributes(true), config));
            else
                config.modifyAttributes(dn, storeDiffs(prevStation, station, new ArrayList<ModificationItem>()));
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

    private void mergeRSForwardRules(
            Collection<RSForwardRule> prevRules, Collection<RSForwardRule> rules, String parentDN)
            throws NamingException {
        for (RSForwardRule prevRule : prevRules) {
            String cn = prevRule.getCommonName();
            if (findRSForwardRuleByCN(rules, cn) == null)
                config.destroySubcontext(LdapUtils.dnOf("cn", cn, parentDN));
        }
        for (RSForwardRule rule : rules) {
            String cn = rule.getCommonName();
            String dn = LdapUtils.dnOf("cn", cn, parentDN);
            RSForwardRule prevRule = findRSForwardRuleByCN(prevRules, cn);
            if (prevRule == null)
                config.createSubcontext(dn, storeTo(rule, new BasicAttributes(true)));
            else
                config.modifyAttributes(dn, storeDiffs(prevRule, rule, new ArrayList<ModificationItem>()));
        }
    }

    private List<ModificationItem> storeDiffs(
            ArchiveCompressionRule prev, ArchiveCompressionRule rule, ArrayList<ModificationItem> mods) {
        storeDiffProperties(mods, prev.getConditions().getMap(), rule.getConditions().getMap());
        LdapUtils.storeDiffObject(mods, "dicomTransferSyntax", prev.getTransferSyntax(), rule.getTransferSyntax(), null);
        LdapUtils.storeDiff(mods, "dcmImageWriteParam", prev.getImageWriteParams(), rule.getImageWriteParams());
        LdapUtils.storeDiff(mods, "dcmRulePriority", prev.getPriority(), rule.getPriority(), 0);
        return mods;
    }

    private List<ModificationItem> storeDiffs(
            StoreAccessControlIDRule prev, StoreAccessControlIDRule rule, ArrayList<ModificationItem> mods) {
        storeDiffProperties(mods, prev.getConditions().getMap(), rule.getConditions().getMap());
        LdapUtils.storeDiffObject(mods, "dcmStoreAccessControlID",
                prev.getStoreAccessControlID(), rule.getStoreAccessControlID(), null);
        LdapUtils.storeDiff(mods, "dcmRulePriority", prev.getPriority(), rule.getPriority(), 0);
        return mods;
    }

    private List<ModificationItem> storeDiffs(
            StudyRetentionPolicy prev, StudyRetentionPolicy policy, ArrayList<ModificationItem> mods) {
        storeDiffProperties(mods, prev.getConditions().getMap(), policy.getConditions().getMap());
        LdapUtils.storeDiffObject(mods, "dcmRetentionPeriod", prev.getRetentionPeriod(), policy.getRetentionPeriod(), null);
        LdapUtils.storeDiff(mods, "dcmRulePriority", prev.getPriority(), policy.getPriority(), 0);
        LdapUtils.storeDiff(mods, "dcmExpireSeriesIndividually", prev.isExpireSeriesIndividually(),
                policy.isExpireSeriesIndividually(), false);
        return mods;
    }

    private static List<ModificationItem> storeDiffs(
            HL7ForwardRule prev, HL7ForwardRule rule, ArrayList<ModificationItem> mods) {
        storeDiffProperties(mods, prev.getConditions().getMap(), rule.getConditions().getMap());
        LdapUtils.storeDiff(mods, "hl7FwdApplicationName", prev.getDestinations(), rule.getDestinations());
        return mods;
    }

    private static List<ModificationItem> storeDiffs(
            HL7OrderScheduledStation prev, HL7OrderScheduledStation station, ArrayList<ModificationItem> mods) {
        LdapUtils.storeDiffObject(mods, "hl7OrderScheduledStationDeviceReference", prev.getDeviceName(), station.getDeviceName(), null);
        LdapUtils.storeDiff(mods, "dcmRulePriority", prev.getPriority(), station.getPriority(), 0);
        return mods;
    }

    private static List<ModificationItem> storeDiffs(
            HL7OrderSPSStatus prev, HL7OrderSPSStatus hl7OrderSPSStatus, ArrayList<ModificationItem> mods) {
        LdapUtils.storeDiff(mods, "hl7OrderControlStatus", prev.getOrderControlStatusCodes(), hl7OrderSPSStatus.getOrderControlStatusCodes());
        return mods;
    }

    private List<ModificationItem> storeDiffs(
            RSForwardRule prev, RSForwardRule rule, ArrayList<ModificationItem> mods) {
        LdapUtils.storeDiffObject(mods, "dcmURI", prev.getBaseURI(), rule.getBaseURI(), null);
        LdapUtils.storeDiff(mods, "dcmRSOperation", prev.getRSOperations(), rule.getRSOperations());
        return mods;
    }

    private ArchiveCompressionRule findCompressionRuleByCN(Collection<ArchiveCompressionRule> rules, String cn) {
        for (ArchiveCompressionRule rule : rules)
            if (rule.getCommonName().equals(cn))
                return rule;
        return null;
    }

    private StoreAccessControlIDRule findStoreAccessControlIDRuleByCN(
            Collection<StoreAccessControlIDRule> rules, String cn) {
        for (StoreAccessControlIDRule rule : rules)
            if (rule.getCommonName().equals(cn))
                return rule;
        return null;
    }

    private StudyRetentionPolicy findStudyRetentionPolicyByCN(Collection<StudyRetentionPolicy> policies, String cn) {
        for (StudyRetentionPolicy policy : policies)
            if (policy.getCommonName().equals(cn))
                return policy;
        return null;
    }

    private static HL7ForwardRule findHL7ForwardRuleByCN(Collection<HL7ForwardRule> rules, String cn) {
        for (HL7ForwardRule rule : rules)
            if (rule.getCommonName().equals(cn))
                return rule;
        return null;
    }

    private static HL7OrderScheduledStation findScheduledStationByCN(Collection<HL7OrderScheduledStation> stations, String cn) {
        for (HL7OrderScheduledStation station : stations)
            if (station.getCommonName().equals(cn))
                return station;
        return null;
    }

    private RSForwardRule findRSForwardRuleByCN(
            Collection<RSForwardRule> rules, String cn) {
        for (RSForwardRule rule : rules)
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
                arcdev.addQueryRetrieveView(view);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
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
        LdapUtils.storeNotNullOrDef(attrs, "dcmDIMSE", coercion.getDIMSE(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dicomTransferRole", coercion.getRole(), null);
        LdapUtils.storeNotEmpty(attrs, "dcmHostname", coercion.getHostNames());
        LdapUtils.storeNotEmpty(attrs, "dcmAETitle", coercion.getAETitles());
        LdapUtils.storeNotEmpty(attrs, "dcmSOPClass", coercion.getSOPClasses());
        LdapUtils.storeNotNullOrDef(attrs, "dcmURI", coercion.getXSLTStylesheetURI(), null);
        LdapUtils.storeNotDef(attrs, "dcmNoKeywords", coercion.isNoKeywords(), false);
        LdapUtils.storeNotNullOrDef(attrs, "dcmLeadingCFindSCP", coercion.getLeadingCFindSCP(), null);
        storeNotEmptyTags(attrs, "dcmTag", coercion.getLeadingCFindSCPReturnKeys());
        LdapUtils.storeNotNullOrDef(attrs, "dcmMergeMWLTemplateURI",
                coercion.getMergeMWLTemplateURI(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmMergeMWLMatchingKey",
                coercion.getMergeMWLMatchingKey(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmAttributeUpdatePolicy",
                coercion.getAttributeUpdatePolicy(), org.dcm4che3.data.Attributes.UpdatePolicy.MERGE);
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
                coercion.setDIMSE(LdapUtils.enumValue(Dimse.class, attrs.get("dcmDIMSE"), null));
                coercion.setRole(
                        LdapUtils.enumValue(TransferCapability.Role.class, attrs.get("dicomTransferRole"), null));
                coercion.setHostNames(LdapUtils.stringArray(attrs.get("dcmHostname")));
                coercion.setAETitles(LdapUtils.stringArray(attrs.get("dcmAETitle")));
                coercion.setSOPClasses(LdapUtils.stringArray(attrs.get("dcmSOPClass")));
                coercion.setXSLTStylesheetURI(LdapUtils.stringValue(attrs.get("dcmURI"), null));
                coercion.setNoKeywords(LdapUtils.booleanValue(attrs.get("dcmNoKeywords"), false));
                coercion.setLeadingCFindSCP(LdapUtils.stringValue(attrs.get("dcmLeadingCFindSCP"), null));
                coercion.setLeadingCFindSCPReturnKeys(tags(attrs.get("dcmTag")));
                coercion.setMergeMWLTemplateURI(
                        LdapUtils.stringValue(attrs.get("dcmMergeMWLTemplateURI"), null));
                coercion.setMergeMWLMatchingKey(
                        LdapUtils.enumValue(MergeMWLMatchingKey.class,
                        attrs.get("dcmMergeMWLMatchingKey"), null));
                coercion.setAttributeUpdatePolicy(LdapUtils.enumValue(org.dcm4che3.data.Attributes.UpdatePolicy.class,
                        attrs.get("dcmAttributeUpdatePolicy"), org.dcm4che3.data.Attributes.UpdatePolicy.MERGE));
                coercion.setPriority(LdapUtils.intValue(attrs.get("dcmRulePriority"), 0));
                coercions.add(coercion);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
    }


    private List<ModificationItem> storeDiffs(
            ArchiveAttributeCoercion prev, ArchiveAttributeCoercion coercion, ArrayList<ModificationItem> mods) {
        LdapUtils.storeDiffObject(mods, "dcmDIMSE", prev.getDIMSE(), coercion.getDIMSE(), null);
        LdapUtils.storeDiffObject(mods, "dicomTransferRole", prev.getRole(), coercion.getRole(), null);
        LdapUtils.storeDiff(mods, "dcmHostname", prev.getHostNames(), coercion.getHostNames());
        LdapUtils.storeDiff(mods, "dcmAETitle", prev.getAETitles(), coercion.getAETitles());
        LdapUtils.storeDiff(mods, "dcmSOPClass", prev.getSOPClasses(), coercion.getSOPClasses());
        LdapUtils.storeDiffObject(mods, "dcmURI", prev.getXSLTStylesheetURI(), coercion.getXSLTStylesheetURI(), null);
        LdapUtils.storeDiff(mods, "dcmNoKeywords", prev.isNoKeywords(), coercion.isNoKeywords(), false);
        LdapUtils.storeDiffObject(mods, "dcmLeadingCFindSCP", prev.getLeadingCFindSCP(), coercion.getLeadingCFindSCP(), null);
        storeDiffTags(mods, "dcmTag", prev.getLeadingCFindSCPReturnKeys(), coercion.getLeadingCFindSCPReturnKeys());
        LdapUtils.storeDiffObject(mods, "dcmMergeMWLTemplateURI",
                prev.getMergeMWLTemplateURI(),
                coercion.getMergeMWLTemplateURI(), null);
        LdapUtils.storeDiffObject(mods, "dcmMergeMWLMatchingKey",
                prev.getMergeMWLMatchingKey(),
                coercion.getMergeMWLMatchingKey(), null);
        LdapUtils.storeDiffObject(mods, "dcmAttributeUpdatePolicy",
                prev.getAttributeUpdatePolicy(),
                coercion.getAttributeUpdatePolicy(),
                org.dcm4che3.data.Attributes.UpdatePolicy.MERGE);
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

    private void storeIDGenerators(String deviceDN, ArchiveDeviceExtension arcDev) throws NamingException {
        for (IDGenerator generator : arcDev.getIDGenerators().values()) {
            config.createSubcontext(
                    LdapUtils.dnOf("dcmIDGeneratorName", generator.getName().name(), deviceDN),
                    storeTo(generator, new BasicAttributes(true)));
        }
    }

    private Attributes storeTo(RejectionNote rjNote, BasicAttributes attrs) {
        attrs.put("objectclass", "dcmRejectionNote");
        attrs.put("dcmRejectionNoteLabel", rjNote.getRejectionNoteLabel());
        LdapUtils.storeNotNullOrDef(attrs, "dcmRejectionNoteType", rjNote.getRejectionNoteType(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmRejectionNoteCode", rjNote.getRejectionNoteCode(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmAcceptPreviousRejectedInstance",
                rjNote.getAcceptPreviousRejectedInstance(), RejectionNote.AcceptPreviousRejectedInstance.REJECT);
        LdapUtils.storeNotEmpty(attrs, "dcmOverwritePreviousRejection", rjNote.getOverwritePreviousRejection());
        LdapUtils.storeNotNullOrDef(attrs, "dcmDeleteRejectedInstanceDelay", rjNote.getDeleteRejectedInstanceDelay(), null);
        LdapUtils.storeNotNullOrDef(attrs, "dcmDeleteRejectionNoteDelay", rjNote.getDeleteRejectionNoteDelay(), null);
        return attrs;
    }

    private Attributes storeTo(IDGenerator generator, BasicAttributes attrs) {
        attrs.put("objectClass", "dcmIDGenerator");
        attrs.put("dcmIDGeneratorName", generator.getName().name());
        LdapUtils.storeNotNullOrDef(attrs, "dcmIDGeneratorFormat", generator.getFormat(), null);
        LdapUtils.storeNotDef(attrs, "dcmIDGeneratorInitialValue", generator.getInitialValue(), 1);
        return attrs;
    }

    private void loadRejectNotes(ArchiveDeviceExtension arcdev, String deviceDN) throws NamingException {
        NamingEnumeration<SearchResult> ne = config.search(deviceDN, "(objectclass=dcmRejectionNote)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                RejectionNote rjNote = new RejectionNote();
                rjNote.setRejectionNoteLabel(LdapUtils.stringValue(attrs.get("dcmRejectionNoteLabel"), null));
                rjNote.setRejectionNoteType(LdapUtils.enumValue(
                        RejectionNote.Type.class,
                        attrs.get("dcmRejectionNoteType"),
                        null));
                rjNote.setRejectionNoteCode(LdapUtils.codeValue(attrs.get("dcmRejectionNoteCode")));
                rjNote.setAcceptPreviousRejectedInstance(LdapUtils.enumValue(
                        RejectionNote.AcceptPreviousRejectedInstance.class,
                        attrs.get("dcmAcceptPreviousRejectedInstance"),
                        RejectionNote.AcceptPreviousRejectedInstance.REJECT));
                rjNote.setOverwritePreviousRejection(LdapUtils.codeArray(attrs.get("dcmOverwritePreviousRejection")));
                rjNote.setDeleteRejectedInstanceDelay(toDuration(attrs.get("dcmDeleteRejectedInstanceDelay"), null));
                rjNote.setDeleteRejectionNoteDelay(toDuration(attrs.get("dcmDeleteRejectionNoteDelay"), null));
                arcdev.addRejectionNote(rjNote);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
    }

    private void loadIDGenerators(ArchiveDeviceExtension arcdev, String deviceDN) throws NamingException {
        NamingEnumeration<SearchResult> ne = config.search(deviceDN, "(objectclass=dcmIDGenerator)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                IDGenerator generator = new IDGenerator();
                generator.setName(LdapUtils.enumValue(IDGenerator.Name.class, attrs.get("dcmIDGeneratorName"), null));
                generator.setFormat(LdapUtils.stringValue(attrs.get("dcmIDGeneratorFormat"), null));
                generator.setInitialValue(LdapUtils.intValue(attrs.get("dcmIDGeneratorInitialValue"),1));
                arcdev.addIDGenerator(generator);
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

    private void mergeIDGenerators(ArchiveDeviceExtension prev, ArchiveDeviceExtension arcDev, String deviceDN)
            throws NamingException {
        for (IDGenerator.Name name : prev.getIDGenerators().keySet()) {
            if (!arcDev.getIDGenerators().containsKey(name))
                            config.destroySubcontext(LdapUtils.dnOf("dcmIDGeneratorName", name.name(), deviceDN));
        }
        for (IDGenerator entryNew : arcDev.getIDGenerators().values()) {
            IDGenerator.Name name = entryNew.getName();
            String dn = LdapUtils.dnOf("dcmIDGeneratorName", name.name(), deviceDN);
            IDGenerator entryOld = prev.getIDGenerators().get(name);
            if (entryOld == null) {
                config.createSubcontext(dn, storeTo(entryNew, new BasicAttributes(true)));
            } else{
                config.modifyAttributes(dn, storeDiffs(entryOld, entryNew, new ArrayList<ModificationItem>()));
            }
        }
    }

    private List<ModificationItem> storeDiffs(RejectionNote prev, RejectionNote rjNote,
                                              ArrayList<ModificationItem> mods) {
        LdapUtils.storeDiffObject(mods, "dcmRejectionNoteType", prev.getRejectionNoteType(), rjNote.getRejectionNoteType(), null);
        LdapUtils.storeDiffObject(mods, "dcmRejectionNoteCode", prev.getRejectionNoteCode(), rjNote.getRejectionNoteCode(), null);
        LdapUtils.storeDiffObject(mods, "dcmAcceptPreviousRejectedInstance",
                prev.getAcceptPreviousRejectedInstance(),
                rjNote.getAcceptPreviousRejectedInstance(),
                RejectionNote.AcceptPreviousRejectedInstance.REJECT);
        LdapUtils.storeDiff(mods, "dcmOverwritePreviousRejection",
                prev.getOverwritePreviousRejection(),
                rjNote.getOverwritePreviousRejection());
        LdapUtils.storeDiffObject(mods, "dcmDeleteRejectedInstanceDelay",
                prev.getDeleteRejectedInstanceDelay(),
                rjNote.getDeleteRejectedInstanceDelay(), null);
        LdapUtils.storeDiffObject(mods, "dcmDeleteRejectionNoteDelay",
                prev.getDeleteRejectionNoteDelay(),
                rjNote.getDeleteRejectionNoteDelay(), null);
        return mods;
    }

    private List<ModificationItem> storeDiffs(IDGenerator prev, IDGenerator generator,
                                              ArrayList<ModificationItem> mods) {
//        LdapUtils.storeDiffObject(mods, "dcmIDGeneratorName", prev.getName(), generator.getName());
        LdapUtils.storeDiffObject(mods, "dcmIDGeneratorFormat", prev.getFormat(), generator.getFormat(), null);
        LdapUtils.storeDiff(mods, "dcmIDGeneratorInitialValue", prev.getInitialValue(), generator.getInitialValue(), 1);
        return mods;
    }

}
