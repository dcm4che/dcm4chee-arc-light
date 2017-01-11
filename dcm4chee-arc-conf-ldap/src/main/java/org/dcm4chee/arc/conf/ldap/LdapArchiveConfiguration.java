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
        LdapUtils.storeNotNull(attrs, "dcmFuzzyAlgorithmClass", ext.getFuzzyAlgorithmClass());
        LdapUtils.storeNotNull(attrs, "dcmStorageID", ext.getStorageID());
        LdapUtils.storeNotNull(attrs, "dcmMetadataStorageID", ext.getMetadataStorageID());
        LdapUtils.storeNotNull(attrs, "dcmSeriesMetadataStorageID", ext.getSeriesMetadataStorageID());
        LdapUtils.storeNotNull(attrs, "dcmSeriesMetadataDelay", ext.getSeriesMetadataDelay());
        LdapUtils.storeNotNull(attrs, "dcmSeriesMetadataPollingInterval", ext.getSeriesMetadataPollingInterval());
        LdapUtils.storeNotDef(attrs, "dcmSeriesMetadataFetchSize", ext.getSeriesMetadataFetchSize(), 100);
        LdapUtils.storeNotNull(attrs, "dcmPurgeInstanceRecordsDelay", ext.getPurgeInstanceRecordsDelay());
        LdapUtils.storeNotNull(attrs, "dcmPurgeInstanceRecordsPollingInterval", ext.getPurgeInstanceRecordsPollingInterval());
        LdapUtils.storeNotDef(attrs, "dcmPurgeInstanceRecordsFetchSize", ext.getPurgeInstanceRecordsFetchSize(), 100);
        LdapUtils.storeNotNull(attrs, "dcmOverwritePolicy", ext.getOverwritePolicy());
        LdapUtils.storeNotNull(attrs, "dcmBulkDataSpoolDirectory", ext.getBulkDataSpoolDirectory());
        LdapUtils.storeNotNull(attrs, "dcmQueryRetrieveViewID", ext.getQueryRetrieveViewID());
        LdapUtils.storeNotDef(attrs, "dcmPersonNameComponentOrderInsensitiveMatching",
                ext.isPersonNameComponentOrderInsensitiveMatching(), false);
        LdapUtils.storeNotDef(attrs, "dcmSendPendingCGet", ext.isSendPendingCGet(), false);
        LdapUtils.storeNotNull(attrs, "dcmSendPendingCMoveInterval", ext.getSendPendingCMoveInterval());
        LdapUtils.storeNotNull(attrs, "dcmFallbackCMoveSCP", ext.getFallbackCMoveSCP());
        LdapUtils.storeNotNull(attrs, "dcmFallbackCMoveSCPDestination", ext.getFallbackCMoveSCPDestination());
        LdapUtils.storeNotNull(attrs, "dcmFallbackCMoveSCPLeadingCFindSCP", ext.getFallbackCMoveSCPLeadingCFindSCP());
        LdapUtils.storeNotDef(attrs, "dcmFallbackCMoveSCPRetries", ext.getFallbackCMoveSCPRetries(), 0);
        LdapUtils.storeNotNull(attrs, "dcmAltCMoveSCP", ext.getAlternativeCMoveSCP());
        LdapUtils.storeNotNull(attrs, "dcmWadoSR2HtmlTemplateURI", ext.getWadoSR2HtmlTemplateURI());
        LdapUtils.storeNotNull(attrs, "dcmWadoSR2TextTemplateURI", ext.getWadoSR2TextTemplateURI());
        LdapUtils.storeNotNull(attrs, "hl7PatientUpdateTemplateURI", ext.getPatientUpdateTemplateURI());
        LdapUtils.storeNotNull(attrs, "hl7ImportReportTemplateURI", ext.getImportReportTemplateURI());
        LdapUtils.storeNotNull(attrs, "hl7ScheduleProcedureTemplateURI", ext.getScheduleProcedureTemplateURI());
        LdapUtils.storeNotNull(attrs, "hl7LogFilePattern", ext.getHl7LogFilePattern());
        LdapUtils.storeNotNull(attrs, "hl7ErrorLogFilePattern", ext.getHl7ErrorLogFilePattern());
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
        LdapUtils.storeNotNull(attrs, "dcmAECacheStaleTimeout", ext.getAECacheStaleTimeout());
        LdapUtils.storeNotNull(attrs, "dcmLeadingCFindSCPQueryCacheStaleTimeout", ext.getLeadingCFindSCPQueryCacheStaleTimeout());
        LdapUtils.storeNotDef(attrs, "dcmLeadingCFindSCPQueryCacheSize", ext.getLeadingCFindSCPQueryCacheSize(), 10);
        LdapUtils.storeNotNull(attrs, "dcmAuditSpoolDirectory", ext.getAuditSpoolDirectory());
        LdapUtils.storeNotNull(attrs, "dcmAuditPollingInterval", ext.getAuditPollingInterval());
        LdapUtils.storeNotNull(attrs, "dcmAuditAggregateDuration", ext.getAuditAggregateDuration());
        LdapUtils.storeNotNull(attrs, "dcmStowSpoolDirectory", ext.getStowSpoolDirectory());
        LdapUtils.storeNotNull(attrs, "dcmPurgeQueueMessagePollingInterval", ext.getPurgeQueueMessagePollingInterval());
        LdapUtils.storeNotNull(attrs, "dcmWadoSpoolDirectory", ext.getWadoSpoolDirectory());
        LdapUtils.storeNotEmpty(attrs, "dcmHideSPSWithStatusFromMWL", ext.getHideSPSWithStatusFrom());
        LdapUtils.storeNotNull(attrs, "dcmRejectExpiredStudiesPollingInterval", ext.getRejectExpiredStudiesPollingInterval());
        LdapUtils.storeNotNull(attrs, "dcmRejectExpiredStudiesPollingStartTime", ext.getRejectExpiredStudiesPollingStartTime());
        LdapUtils.storeNotDef(attrs, "dcmRejectExpiredStudiesFetchSize", ext.getRejectExpiredStudiesFetchSize(), 0);
        LdapUtils.storeNotDef(attrs, "dcmRejectExpiredSeriesFetchSize", ext.getRejectExpiredSeriesFetchSize(), 0);
        LdapUtils.storeNotNull(attrs, "dcmRejectExpiredStudiesAETitle", ext.getRejectExpiredStudiesAETitle());
        LdapUtils.storeNotNull(attrs, "dcmFallbackCMoveSCPStudyOlderThan", ext.getFallbackCMoveSCPStudyOlderThan());
        LdapUtils.storeNotNull(attrs, "dcmStorePermissionServiceURL", ext.getStorePermissionServiceURL());
        LdapUtils.storeNotNull(attrs, "dcmStorePermissionServiceResponsePattern", ext.getStorePermissionServiceResponsePattern());
        LdapUtils.storeNotNull(attrs, "dcmStorePermissionCacheStaleTimeout", ext.getStorePermissionCacheStaleTimeout());
        LdapUtils.storeNotDef(attrs, "dcmStorePermissionCacheSize", ext.getStorePermissionCacheSize(), 10);
        LdapUtils.storeNotNull(attrs, "dcmMergeMWLCacheStaleTimeout", ext.getMergeMWLCacheStaleTimeout());
        LdapUtils.storeNotDef(attrs, "dcmMergeMWLCacheSize", ext.getMergeMWLCacheSize(), 10);
        LdapUtils.storeNotDef(attrs, "dcmStoreUpdateDBMaxRetries", ext.getStoreUpdateDBMaxRetries(), 1);
        LdapUtils.storeNotDef(attrs, "dcmStoreUpdateDBMaxRetryDelay", ext.getStoreUpdateDBMaxRetryDelay(), 1000);
        LdapUtils.storeNotNull(attrs, "dcmAllowRejectionForDataRetentionPolicyExpired", ext.getAllowRejectionForDataRetentionPolicyExpired());
        LdapUtils.storeNotNull(attrs, "dcmAcceptMissingPatientID", ext.getAcceptMissingPatientID());
        LdapUtils.storeNotNull(attrs, "dcmAllowDeleteStudyPermanently", ext.getAllowDeleteStudyPermanently());
        LdapUtils.storeNotNull(attrs, "dcmStorePermissionServiceExpirationDatePattern", ext.getStorePermissionServiceExpirationDatePattern());
        LdapUtils.storeNotNull(attrs, "dcmShowPatientInfoInSystemLog", ext.getShowPatientInfoInSystemLog());
        LdapUtils.storeNotNull(attrs, "dcmShowPatientInfoInAuditLog", ext.getShowPatientInfoInAuditLog());
        LdapUtils.storeNotNull(attrs, "dcmPurgeStgCmtCompletedDelay", ext.getPurgeStgCmtCompletedDelay());
        LdapUtils.storeNotNull(attrs, "dcmPurgeStgCmtPollingInterval", ext.getPurgeStgCmtPollingInterval());
        LdapUtils.storeNotNull(attrs, "dcmDefaultCharacterSet", ext.getDefaultCharacterSet());
        LdapUtils.storeNotNull(attrs, "dcmStorePermissionServiceErrorCommentPattern", ext.getStorePermissionServiceErrorCommentPattern());
        LdapUtils.storeNotNull(attrs, "dcmStorePermissionServiceErrorCodePattern", ext.getStorePermissionServiceErrorCodePattern());
        LdapUtils.storeNotEmpty(attrs, "dcmRetrieveAET", ext.getRetrieveAETitles());
        LdapUtils.storeNotNull(attrs, "dcmExternalRetrieveAEDestination", ext.getExternalRetrieveAEDestination());
        LdapUtils.storeNotNull(attrs, "dcmRemapRetrieveURL", ext.getRemapRetrieveURL());
        LdapUtils.storeNotDef(attrs, "dcmValidateCallingAEHostname", ext.isValidateCallingAEHostname(), false);
        LdapUtils.storeNotNull(attrs, "hl7PSUSendingApplication", ext.getHl7PSUSendingApplication());
        LdapUtils.storeNotEmpty(attrs, "hl7PSUReceivingApplication", ext.getHl7PSUReceivingApplications());
        LdapUtils.storeNotNull(attrs, "hl7PSUDelay", ext.getHl7PSUDelay());
        LdapUtils.storeNotNull(attrs, "hl7PSUTimeout", ext.getHl7PSUTimeout());
        LdapUtils.storeNotDef(attrs, "hl7PSUOnTimeout", ext.isHl7PSUOnTimeout(), false);
        LdapUtils.storeNotNull(attrs, "hl7PSUTaskPollingInterval", ext.getHl7PSUTaskPollingInterval());
        LdapUtils.storeNotDef(attrs, "hl7PSUTaskFetchSize", ext.getHl7PSUTaskFetchSize(), 100);
        LdapUtils.storeNotDef(attrs, "hl7PSUMWL", ext.isHl7PSUMWL(), false);
        LdapUtils.storeNotNull(attrs, "dcmAcceptConflictingPatientID", ext.getAcceptConflictingPatientID());
    }

    @Override
    protected void loadFrom(Device device, Attributes attrs) throws NamingException, CertificateException {
        if (!LdapUtils.hasObjectClass(attrs, "dcmArchiveDevice"))
            return;

        ArchiveDeviceExtension ext = new ArchiveDeviceExtension();
        device.addDeviceExtension(ext);
        ext.setFuzzyAlgorithmClass(LdapUtils.stringValue(attrs.get("dcmFuzzyAlgorithmClass"), null));
        ext.setStorageID(LdapUtils.stringValue(attrs.get("dcmStorageID"), null));
        ext.setMetadataStorageID(LdapUtils.stringValue(attrs.get("dcmMetadataStorageID"), null));
        ext.setSeriesMetadataStorageID(LdapUtils.stringValue(attrs.get("dcmSeriesMetadataStorageID"), null));
        ext.setSeriesMetadataDelay(toDuration(attrs.get("dcmSeriesMetadataDelay")));
        ext.setSeriesMetadataPollingInterval(toDuration(attrs.get("dcmSeriesMetadataPollingInterval")));
        ext.setSeriesMetadataFetchSize(LdapUtils.intValue(attrs.get("dcmSeriesMetadataFetchSize"), 100));
        ext.setPurgeInstanceRecordsDelay(toDuration(attrs.get("dcmPurgeInstanceRecordsDelay")));
        ext.setPurgeInstanceRecordsPollingInterval(toDuration(attrs.get("dcmPurgeInstanceRecordsPollingInterval")));
        ext.setPurgeInstanceRecordsFetchSize(
                LdapUtils.intValue(attrs.get("dcmPurgeInstanceRecordsFetchSize"), 100));
        ext.setOverwritePolicy(LdapUtils.enumValue(OverwritePolicy.class, attrs.get("dcmOverwritePolicy"), null));
        ext.setBulkDataSpoolDirectory(LdapUtils.stringValue(attrs.get("dcmBulkDataSpoolDirectory"), null));
        ext.setQueryRetrieveViewID(LdapUtils.stringValue(attrs.get("dcmQueryRetrieveViewID"), null));
        ext.setPersonNameComponentOrderInsensitiveMatching(
                LdapUtils.booleanValue(attrs.get("dcmPersonNameComponentOrderInsensitiveMatching"), false));
        ext.setSendPendingCGet(LdapUtils.booleanValue(attrs.get("dcmSendPendingCGet"), false));
        ext.setSendPendingCMoveInterval(toDuration(attrs.get("dcmSendPendingCMoveInterval")));
        ext.setFallbackCMoveSCP(LdapUtils.stringValue(attrs.get("dcmFallbackCMoveSCP"), null));
        ext.setFallbackCMoveSCPDestination(LdapUtils.stringValue(attrs.get("dcmFallbackCMoveSCPDestination"), null));
        ext.setFallbackCMoveSCPRetries(LdapUtils.intValue(attrs.get("dcmFallbackCMoveSCPRetries"), 0));
        ext.setFallbackCMoveSCPLeadingCFindSCP(LdapUtils.stringValue(attrs.get("dcmFallbackCMoveSCPLeadingCFindSCP"), null));
        ext.setAlternativeCMoveSCP(LdapUtils.stringValue(attrs.get("dcmAltCMoveSCP"), null));
        ext.setWadoSR2HtmlTemplateURI(LdapUtils.stringValue(attrs.get("dcmWadoSR2HtmlTemplateURI"), null));
        ext.setWadoSR2TextTemplateURI(LdapUtils.stringValue(attrs.get("dcmWadoSR2TextTemplateURI"), null));
        ext.setPatientUpdateTemplateURI(LdapUtils.stringValue(attrs.get("hl7PatientUpdateTemplateURI"), null));
        ext.setImportReportTemplateURI(LdapUtils.stringValue(attrs.get("hl7ImportReportTemplateURI"), null));
        ext.setScheduleProcedureTemplateURI(LdapUtils.stringValue(attrs.get("hl7ScheduleProcedureTemplateURI"), null));
        ext.setHl7LogFilePattern(LdapUtils.stringValue(attrs.get("hl7LogFilePattern"), null));
        ext.setHl7ErrorLogFilePattern(LdapUtils.stringValue(attrs.get("hl7ErrorLogFilePattern"), null));
        ext.setUnzipVendorDataToURI(LdapUtils.stringValue(attrs.get("dcmUnzipVendorDataToURI"), null));
        ext.setWadoSupportedSRClasses(LdapUtils.stringArray(attrs.get("dcmWadoSupportedSRClasses")));
        ext.setQidoMaxNumberOfResults(LdapUtils.intValue(attrs.get("dcmQidoMaxNumberOfResults"), 0));
        ext.setMppsForwardDestinations(LdapUtils.stringArray(attrs.get("dcmFwdMppsDestination")));
        ext.setIanDestinations(LdapUtils.stringArray(attrs.get("dcmIanDestination")));
        ext.setIanDelay(toDuration(attrs.get("dcmIanDelay")));
        ext.setIanTimeout(toDuration(attrs.get("dcmIanTimeout")));
        ext.setIanOnTimeout(LdapUtils.booleanValue(attrs.get("dcmIanOnTimeout"), false));
        ext.setIanTaskPollingInterval(toDuration(attrs.get("dcmIanTaskPollingInterval")));
        ext.setIanTaskFetchSize(LdapUtils.intValue(attrs.get("dcmIanTaskFetchSize"), 100));
        ext.setExportTaskPollingInterval(toDuration(attrs.get("dcmExportTaskPollingInterval")));
        ext.setExportTaskFetchSize(LdapUtils.intValue(attrs.get("dcmExportTaskFetchSize"), 5));
        ext.setPurgeStoragePollingInterval(toDuration(attrs.get("dcmPurgeStoragePollingInterval")));
        ext.setPurgeStorageFetchSize(LdapUtils.intValue(attrs.get("dcmPurgeStorageFetchSize"), 100));
        ext.setDeleteRejectedPollingInterval(toDuration(attrs.get("dcmDeleteRejectedPollingInterval")));
        ext.setDeleteRejectedFetchSize(LdapUtils.intValue(attrs.get("dcmDeleteRejectedFetchSize"), 100));
        ext.setDeleteStudyBatchSize(LdapUtils.intValue(attrs.get("dcmDeleteStudyBatchSize"), 10));
        ext.setDeletePatientOnDeleteLastStudy(
                LdapUtils.booleanValue(attrs.get("dcmDeletePatientOnDeleteLastStudy"), false));
        ext.setMaxAccessTimeStaleness(toDuration(attrs.get("dcmMaxAccessTimeStaleness")));
        ext.setAECacheStaleTimeout(toDuration(attrs.get("dcmAECacheStaleTimeout")));
        ext.setLeadingCFindSCPQueryCacheStaleTimeout(toDuration(attrs.get("dcmLeadingCFindSCPQueryCacheStaleTimeout")));
        ext.setLeadingCFindSCPQueryCacheSize(LdapUtils.intValue(attrs.get("dcmLeadingCFindSCPQueryCacheSize"), 10));
        ext.setAuditSpoolDirectory(LdapUtils.stringValue(attrs.get("dcmAuditSpoolDirectory"), null));
        ext.setAuditPollingInterval(toDuration(attrs.get("dcmAuditPollingInterval")));
        ext.setAuditAggregateDuration(toDuration(attrs.get("dcmAuditAggregateDuration")));
        ext.setStowSpoolDirectory(LdapUtils.stringValue(attrs.get("dcmStowSpoolDirectory"), null));
        ext.setPurgeQueueMessagePollingInterval(toDuration(attrs.get("dcmPurgeQueueMessagePollingInterval")));
        ext.setWadoSpoolDirectory(LdapUtils.stringValue(attrs.get("dcmWadoSpoolDirectory"), null));
        ext.setHideSPSWithStatusFrom(LdapUtils.enumArray(SPSStatus.class, attrs.get("dcmHideSPSWithStatusFromMWL")));
        ext.setRejectExpiredStudiesPollingInterval(toDuration(attrs.get("dcmRejectExpiredStudiesPollingInterval")));
        ext.setRejectExpiredStudiesPollingStartTime(toLocalTime(attrs.get("dcmRejectExpiredStudiesPollingStartTime")));
        ext.setRejectExpiredStudiesFetchSize(LdapUtils.intValue(attrs.get("dcmRejectExpiredStudiesFetchSize"), 0));
        ext.setRejectExpiredSeriesFetchSize(LdapUtils.intValue(attrs.get("dcmRejectExpiredSeriesFetchSize"), 0));
        ext.setRejectExpiredStudiesAETitle(LdapUtils.stringValue(attrs.get("dcmRejectExpiredStudiesAETitle"), null));
        ext.setFallbackCMoveSCPStudyOlderThan(LdapUtils.stringValue(attrs.get("dcmFallbackCMoveSCPStudyOlderThan"), null));
        ext.setStorePermissionServiceURL(LdapUtils.stringValue(attrs.get("dcmStorePermissionServiceURL"), null));
        ext.setStorePermissionServiceResponsePattern(toPattern(attrs.get("dcmStorePermissionServiceResponsePattern")));
        ext.setStorePermissionCacheStaleTimeout(toDuration(attrs.get("dcmStorePermissionCacheStaleTimeout")));
        ext.setStorePermissionCacheSize(LdapUtils.intValue(attrs.get("dcmStorePermissionCacheSize"), 10));
        ext.setMergeMWLCacheStaleTimeout(toDuration(attrs.get("dcmMergeMWLCacheStaleTimeout")));
        ext.setMergeMWLCacheSize(LdapUtils.intValue(attrs.get("dcmMergeMWLCacheSize"), 10));
        ext.setStoreUpdateDBMaxRetries(LdapUtils.intValue(attrs.get("dcmStoreUpdateDBMaxRetries"), 1));
        ext.setStoreUpdateDBMaxRetryDelay(LdapUtils.intValue(attrs.get("dcmStoreUpdateDBMaxRetryDelay"), 1000));
        ext.setAllowRejectionForDataRetentionPolicyExpired(
                LdapUtils.enumValue(AllowRejectionForDataRetentionPolicyExpired.class,
                        attrs.get("dcmAllowRejectionForDataRetentionPolicyExpired"), null));
        ext.setAcceptMissingPatientID(
                LdapUtils.enumValue(AcceptMissingPatientID.class, attrs.get("dcmAcceptMissingPatientID"), null));
        ext.setAllowDeleteStudyPermanently(LdapUtils.enumValue(AllowDeleteStudyPermanently.class, attrs.get("dcmAllowDeleteStudyPermanently"), null));
        ext.setStorePermissionServiceExpirationDatePattern(toPattern(attrs.get("dcmStorePermissionServiceExpirationDatePattern")));
        ext.setShowPatientInfoInSystemLog(LdapUtils.enumValue(ShowPatientInfo.class, attrs.get("dcmShowPatientInfoInSystemLog"), null));
        ext.setShowPatientInfoInAuditLog(LdapUtils.enumValue(ShowPatientInfo.class, attrs.get("dcmShowPatientInfoInAuditLog"), null));
        ext.setPurgeStgCmtCompletedDelay(toDuration(attrs.get("dcmPurgeStgCmtCompletedDelay")));
        ext.setPurgeStgCmtPollingInterval(toDuration(attrs.get("dcmPurgeStgCmtPollingInterval")));
        ext.setDefaultCharacterSet(LdapUtils.stringValue(attrs.get("dcmDefaultCharacterSet"), null));
        ext.setStorePermissionServiceErrorCommentPattern(toPattern(attrs.get("dcmStorePermissionServiceErrorCommentPattern")));
        ext.setStorePermissionServiceErrorCodePattern(toPattern(attrs.get("dcmStorePermissionServiceErrorCodePattern")));
        ext.setRetrieveAETitles(LdapUtils.stringArray(attrs.get("dcmRetrieveAET")));
        ext.setExternalRetrieveAEDestination(LdapUtils.stringValue(attrs.get("dcmExternalRetrieveAEDestination"), null));
        ext.setRemapRetrieveURL(LdapUtils.stringValue(attrs.get("dcmRemapRetrieveURL"), null));
        ext.setValidateCallingAEHostname(LdapUtils.booleanValue(attrs.get("dcmValidateCallingAEHostname"), false));
        ext.setHl7PSUSendingApplication(LdapUtils.stringValue(attrs.get("hl7PSUSendingApplication"), null));
        ext.setHl7PSUReceivingApplications(LdapUtils.stringArray(attrs.get("hl7PSUReceivingApplication")));
        ext.setHl7PSUDelay(toDuration(attrs.get("hl7PSUDelay")));
        ext.setHl7PSUTimeout(toDuration(attrs.get("hl7PSUTimeout")));
        ext.setHl7PSUOnTimeout(LdapUtils.booleanValue(attrs.get("hl7PSUOnTimeout"), false));
        ext.setHl7PSUTaskPollingInterval(toDuration(attrs.get("hl7PSUTaskPollingInterval")));
        ext.setHl7PSUTaskFetchSize(LdapUtils.intValue(attrs.get("hl7PSUTaskFetchSize"), 100));
        ext.setHl7PSUMWL(LdapUtils.booleanValue(attrs.get("hl7PSUMWL"), false));
        ext.setAcceptConflictingPatientID(
                LdapUtils.enumValue(AcceptConflictingPatientID.class, attrs.get("dcmAcceptConflictingPatientID"), null));
    }

    @Override
    protected void storeDiffs(Device prev, Device device, List<ModificationItem> mods) {
        ArchiveDeviceExtension aa = prev.getDeviceExtension(ArchiveDeviceExtension.class);
        ArchiveDeviceExtension bb = device.getDeviceExtension(ArchiveDeviceExtension.class);
        if (aa == null || bb == null)
            return;

        LdapUtils.storeDiff(mods, "dcmFuzzyAlgorithmClass", aa.getFuzzyAlgorithmClass(), bb.getFuzzyAlgorithmClass());
        LdapUtils.storeDiff(mods, "dcmStorageID", aa.getStorageID(), bb.getStorageID());
        LdapUtils.storeDiff(mods, "dcmMetadataStorageID", aa.getMetadataStorageID(), bb.getMetadataStorageID());
        LdapUtils.storeDiff(mods, "dcmSeriesMetadataStorageID",
                aa.getSeriesMetadataStorageID(),
                bb.getSeriesMetadataStorageID());
        LdapUtils.storeDiff(mods, "dcmSeriesMetadataDelay", aa.getSeriesMetadataDelay(), bb.getSeriesMetadataDelay());
        LdapUtils.storeDiff(mods, "dcmSeriesMetadataPollingInterval",
                aa.getSeriesMetadataPollingInterval(),
                bb.getSeriesMetadataPollingInterval());
        LdapUtils.storeDiff(mods, "dcmSeriesMetadataFetchSize",
                aa.getSeriesMetadataFetchSize(),
                bb.getSeriesMetadataFetchSize(),
                100);
        LdapUtils.storeDiff(mods, "dcmPurgeInstanceRecordsDelay",
                aa.getPurgeInstanceRecordsDelay(),
                bb.getPurgeInstanceRecordsDelay());
        LdapUtils.storeDiff(mods, "dcmPurgeInstanceRecordsPollingInterval",
                aa.getPurgeInstanceRecordsPollingInterval(),
                bb.getPurgeInstanceRecordsPollingInterval());
        LdapUtils.storeDiff(mods, "dcmPurgeInstanceRecordsFetchSize",
                aa.getPurgeInstanceRecordsFetchSize(),
                bb.getPurgeInstanceRecordsFetchSize(),
                100);
        LdapUtils.storeDiff(mods, "dcmOverwritePolicy", aa.getOverwritePolicy(), bb.getOverwritePolicy());
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
        LdapUtils.storeDiff(mods, "dcmFallbackCMoveSCPRetries",
                aa.getFallbackCMoveSCPRetries(), bb.getFallbackCMoveSCPRetries(),  0);
        LdapUtils.storeDiff(mods, "dcmFallbackCMoveSCPLeadingCFindSCP",
                aa.getFallbackCMoveSCPLeadingCFindSCP(), bb.getFallbackCMoveSCPLeadingCFindSCP());
        LdapUtils.storeDiff(mods, "dcmAltCMoveSCP", aa.getAlternativeCMoveSCP(), bb.getAlternativeCMoveSCP());
        LdapUtils.storeDiff(mods, "dcmWadoSR2HtmlTemplateURI",
                aa.getWadoSR2HtmlTemplateURI(), bb.getWadoSR2HtmlTemplateURI());
        LdapUtils.storeDiff(mods, "dcmWadoSR2TextTemplateURI",
                aa.getWadoSR2TextTemplateURI(), bb.getWadoSR2TextTemplateURI());
        LdapUtils.storeDiff(mods, "hl7ImportReportTemplateURI",
                aa.getImportReportTemplateURI(), bb.getImportReportTemplateURI());
        LdapUtils.storeDiff(mods, "hl7PatientUpdateTemplateURI",
                aa.getPatientUpdateTemplateURI(), bb.getPatientUpdateTemplateURI());
        LdapUtils.storeDiff(mods, "hl7ScheduleProcedureTemplateURI", aa.getScheduleProcedureTemplateURI(),
                bb.getScheduleProcedureTemplateURI());
        LdapUtils.storeDiff(mods, "hl7LogFilePattern", aa.getHl7LogFilePattern(), bb.getHl7LogFilePattern());
        LdapUtils.storeDiff(mods, "hl7ErrorLogFilePattern", aa.getHl7ErrorLogFilePattern(), bb.getHl7ErrorLogFilePattern());
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
        LdapUtils.storeDiff(mods, "dcmAECacheStaleTimeout",
                aa.getAECacheStaleTimeout(), bb.getAECacheStaleTimeout());
        LdapUtils.storeDiff(mods, "dcmLeadingCFindSCPQueryCacheStaleTimeout",
                aa.getLeadingCFindSCPQueryCacheStaleTimeout(), bb.getLeadingCFindSCPQueryCacheStaleTimeout());
        LdapUtils.storeDiff(mods, "dcmLeadingCFindSCPQueryCacheSize",
                aa.getLeadingCFindSCPQueryCacheSize(), bb.getLeadingCFindSCPQueryCacheSize(), 10);
        LdapUtils.storeDiff(mods, "dcmAuditSpoolDirectory",
                aa.getAuditSpoolDirectory(), bb.getAuditSpoolDirectory());
        LdapUtils.storeDiff(mods, "dcmAuditPollingInterval",
                aa.getAuditPollingInterval(), bb.getAuditPollingInterval());
        LdapUtils.storeDiff(mods, "dcmAuditAggregateDuration",
                aa.getAuditAggregateDuration(), bb.getAuditAggregateDuration());
        LdapUtils.storeDiff(mods, "dcmStowSpoolDirectory",
                aa.getStowSpoolDirectory(), bb.getStowSpoolDirectory());
        LdapUtils.storeDiff(mods, "dcmPurgeQueueMessagePollingInterval", aa.getPurgeQueueMessagePollingInterval(),
                bb.getPurgeQueueMessagePollingInterval());
        LdapUtils.storeDiff(mods, "dcmWadoSpoolDirectory",
                aa.getWadoSpoolDirectory(), bb.getWadoSpoolDirectory());
        LdapUtils.storeDiff(mods, "dcmHideSPSWithStatusFromMWL", aa.getHideSPSWithStatusFrom(), bb.getHideSPSWithStatusFrom());
        LdapUtils.storeDiff(mods, "dcmRejectExpiredStudiesPollingInterval",
                aa.getRejectExpiredStudiesPollingInterval(), bb.getRejectExpiredStudiesPollingInterval());
        LdapUtils.storeDiff(mods, "dcmRejectExpiredStudiesPollingStartTime",
                aa.getRejectExpiredStudiesPollingStartTime(), bb.getRejectExpiredStudiesPollingStartTime());
        LdapUtils.storeDiff(mods, "dcmRejectExpiredStudiesFetchSize",
                aa.getRejectExpiredStudiesFetchSize(), bb.getRejectExpiredStudiesFetchSize(), 0);
        LdapUtils.storeDiff(mods, "dcmRejectExpiredSeriesFetchSize",
                aa.getRejectExpiredSeriesFetchSize(), bb.getRejectExpiredSeriesFetchSize(), 0);
        LdapUtils.storeDiff(mods, "dcmRejectExpiredStudiesAETitle",
                aa.getRejectExpiredStudiesAETitle(), bb.getRejectExpiredStudiesAETitle());
        LdapUtils.storeDiff(mods, "dcmFallbackCMoveSCPStudyOlderThan",
                aa.getFallbackCMoveSCPStudyOlderThan(), bb.getFallbackCMoveSCPStudyOlderThan());
        LdapUtils.storeDiff(mods, "dcmStorePermissionServiceURL",
                aa.getStorePermissionServiceURL(), bb.getStorePermissionServiceURL());
        LdapUtils.storeDiff(mods, "dcmStorePermissionServiceResponsePattern",
                aa.getStorePermissionServiceResponsePattern(), bb.getStorePermissionServiceResponsePattern());
        LdapUtils.storeDiff(mods, "dcmStorePermissionCacheStaleTimeout",
                aa.getStorePermissionCacheStaleTimeout(), bb.getStorePermissionCacheStaleTimeout());
        LdapUtils.storeDiff(mods, "dcmStorePermissionCacheSize",
                aa.getStorePermissionCacheSize(), bb.getStorePermissionCacheSize(), 10);
        LdapUtils.storeDiff(mods, "dcmMergeMWLCacheStaleTimeout",
                aa.getMergeMWLCacheStaleTimeout(), bb.getMergeMWLCacheStaleTimeout());
        LdapUtils.storeDiff(mods, "dcmMergeMWLCacheSize",
                aa.getMergeMWLCacheSize(), bb.getMergeMWLCacheSize(), 10);
        LdapUtils.storeDiff(mods, "dcmStoreUpdateDBMaxRetries",
                aa.getStoreUpdateDBMaxRetries(), bb.getStoreUpdateDBMaxRetries(), 1);
        LdapUtils.storeDiff(mods, "dcmStoreUpdateDBMaxRetryDelay",
                aa.getStoreUpdateDBMaxRetryDelay(), bb.getStoreUpdateDBMaxRetryDelay(), 1000);
        LdapUtils.storeDiff(mods, "dcmAllowRejectionForDataRetentionPolicyExpired",
                aa.getAllowRejectionForDataRetentionPolicyExpired(), bb.getAllowRejectionForDataRetentionPolicyExpired());
        LdapUtils.storeDiff(mods, "dcmAcceptMissingPatientID", aa.getAcceptMissingPatientID(), bb.getAcceptMissingPatientID());
        LdapUtils.storeDiff(mods, "dcmAllowDeleteStudyPermanently", aa.getAllowDeleteStudyPermanently(), bb.getAllowDeleteStudyPermanently());
        LdapUtils.storeDiff(mods, "dcmStorePermissionServiceExpirationDatePattern",
                aa.getStorePermissionServiceExpirationDatePattern(), bb.getStorePermissionServiceExpirationDatePattern());
        LdapUtils.storeDiff(mods, "dcmShowPatientInfoInSystemLog", aa.getShowPatientInfoInSystemLog(), bb.getShowPatientInfoInSystemLog());
        LdapUtils.storeDiff(mods, "dcmShowPatientInfoInAuditLog", aa.getShowPatientInfoInAuditLog(), bb.getShowPatientInfoInAuditLog());
        LdapUtils.storeDiff(mods, "dcmPurgeStgCmtCompletedDelay", aa.getPurgeStgCmtCompletedDelay(), bb.getPurgeStgCmtCompletedDelay());
        LdapUtils.storeDiff(mods, "dcmPurgeStgCmtPollingInterval", aa.getPurgeStgCmtPollingInterval(), bb.getPurgeStgCmtPollingInterval());
        LdapUtils.storeDiff(mods, "dcmDefaultCharacterSet", aa.getDefaultCharacterSet(), bb.getDefaultCharacterSet());
        LdapUtils.storeDiff(mods, "dcmStorePermissionServiceErrorCommentPattern",
                aa.getStorePermissionServiceErrorCommentPattern(), bb.getStorePermissionServiceErrorCommentPattern());
        LdapUtils.storeDiff(mods, "dcmStorePermissionServiceErrorCodePattern",
                aa.getStorePermissionServiceErrorCodePattern(), bb.getStorePermissionServiceErrorCodePattern());
        LdapUtils.storeDiff(mods, "dcmRetrieveAET", aa.getRetrieveAETitles(), bb.getRetrieveAETitles());
        LdapUtils.storeDiff(mods, "dcmExternalRetrieveAEDestination",
                aa.getExternalRetrieveAEDestination(), bb.getExternalRetrieveAEDestination());
        LdapUtils.storeDiff(mods, "dcmRemapRetrieveURL", aa.getRemapRetrieveURL(), bb.getRemapRetrieveURL());
        LdapUtils.storeDiff(mods, "dcmValidateCallingAEHostname", aa.isValidateCallingAEHostname(), bb.isValidateCallingAEHostname());
        LdapUtils.storeDiff(mods, "hl7PSUSendingApplication", aa.getHl7PSUSendingApplication(), bb.getHl7PSUSendingApplication());
        LdapUtils.storeDiff(mods, "hl7PSUReceivingApplication", aa.getHl7PSUReceivingApplications(), bb.getHl7PSUReceivingApplications());
        LdapUtils.storeDiff(mods, "hl7PSUDelay", aa.getHl7PSUDelay(), bb.getHl7PSUDelay());
        LdapUtils.storeDiff(mods, "hl7PSUTimeout", aa.getHl7PSUTimeout(), bb.getHl7PSUTimeout());
        LdapUtils.storeDiff(mods, "hl7PSUOnTimeout", aa.isHl7PSUOnTimeout(), bb.isHl7PSUOnTimeout(), false);
        LdapUtils.storeDiff(mods, "hl7PSUTaskPollingInterval",
                aa.getHl7PSUTaskPollingInterval(), bb.getHl7PSUTaskPollingInterval());
        LdapUtils.storeDiff(mods, "hl7PSUTaskFetchSize", aa.getHl7PSUTaskFetchSize(), bb.getHl7PSUTaskFetchSize(), 100);
        LdapUtils.storeDiff(mods, "hl7PSUMWL", aa.isHl7PSUMWL(), bb.isHl7PSUMWL(), false);
        LdapUtils.storeDiff(mods, "dcmAcceptConflictingPatientID", aa.getAcceptConflictingPatientID(), bb.getAcceptConflictingPatientID());
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
        if (aa == null || bb == null)
            return;

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
        LdapUtils.storeNotNull(attrs, "dcmStorageID", ext.getStorageID());
        LdapUtils.storeNotNull(attrs, "dcmMetadataStorageID", ext.getMetadataStorageID());
        LdapUtils.storeNotNull(attrs, "dcmSeriesMetadataDelay", ext.getSeriesMetadataDelay());
        LdapUtils.storeNotNull(attrs, "dcmPurgeInstanceRecordsDelay", ext.getPurgeInstanceRecordsDelay());
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
        LdapUtils.storeNotDef(attrs, "dcmFallbackCMoveSCPRetries", ext.getFallbackCMoveSCPRetries(), 0);
        LdapUtils.storeNotNull(attrs, "dcmFallbackCMoveSCPLeadingCFindSCP", ext.getFallbackCMoveSCPLeadingCFindSCP());
        LdapUtils.storeNotNull(attrs, "dcmAltCMoveSCP", ext.getAlternativeCMoveSCP());
        LdapUtils.storeNotNull(attrs, "dcmWadoSR2HtmlTemplateURI", ext.getWadoSR2HtmlTemplateURI());
        LdapUtils.storeNotNull(attrs, "dcmWadoSR2TextTemplateURI", ext.getWadoSR2TextTemplateURI());
        LdapUtils.storeNotDef(attrs, "dcmQidoMaxNumberOfResults", ext.getQidoMaxNumberOfResults(), 0);
        LdapUtils.storeNotEmpty(attrs, "dcmFwdMppsDestination", ext.getMppsForwardDestinations());
        LdapUtils.storeNotEmpty(attrs, "dcmIanDestination", ext.getIanDestinations());
        LdapUtils.storeNotNull(attrs, "dcmIanDelay", ext.getIanDelay());
        LdapUtils.storeNotNull(attrs, "dcmIanTimeout", ext.getIanTimeout());
        LdapUtils.storeNotNull(attrs, "dcmIanOnTimeout", ext.getIanOnTimeout());
        LdapUtils.storeNotEmpty(attrs, "dcmHideSPSWithStatusFromMWL", ext.getHideSPSWithStatusFromMWL());
        LdapUtils.storeNotNull(attrs, "dcmFallbackCMoveSCPStudyOlderThan", ext.getFallbackCMoveSCPStudyOlderThan());
        LdapUtils.storeNotNull(attrs, "dcmStorePermissionServiceURL", ext.getStorePermissionServiceURL());
        LdapUtils.storeNotNull(attrs, "dcmStorePermissionServiceResponsePattern", ext.getStorePermissionServiceResponsePattern());
        LdapUtils.storeNotNull(attrs, "dcmAllowRejectionForDataRetentionPolicyExpired", ext.getAllowRejectionForDataRetentionPolicyExpired());
        LdapUtils.storeNotEmpty(attrs, "dcmAcceptedUserRole", ext.getAcceptedUserRoles());
        LdapUtils.storeNotNull(attrs, "dcmAcceptMissingPatientID", ext.getAcceptMissingPatientID());
        LdapUtils.storeNotNull(attrs, "dcmAllowDeleteStudyPermanently", ext.getAllowDeleteStudyPermanently());
        LdapUtils.storeNotNull(attrs, "dcmStorePermissionServiceExpirationDatePattern", ext.getStorePermissionServiceExpirationDatePattern());
        LdapUtils.storeNotNull(attrs, "dcmDefaultCharacterSet", ext.getDefaultCharacterSet());
        LdapUtils.storeNotNull(attrs, "dcmStorePermissionServiceErrorCommentPattern", ext.getStorePermissionServiceErrorCommentPattern());
        LdapUtils.storeNotNull(attrs, "dcmStorePermissionServiceErrorCodePattern", ext.getStorePermissionServiceErrorCodePattern());
        LdapUtils.storeNotEmpty(attrs, "dcmRetrieveAET", ext.getRetrieveAETitles());
        LdapUtils.storeNotNull(attrs, "dcmExternalRetrieveAEDestination", ext.getExternalRetrieveAEDestination());
        LdapUtils.storeNotEmpty(attrs, "dcmAcceptedMoveDestination", ext.getAcceptedMoveDestinations());
        LdapUtils.storeNotNull(attrs, "dcmValidateCallingAEHostname", ext.getValidateCallingAEHostname());
        LdapUtils.storeNotNull(attrs, "hl7PSUSendingApplication", ext.getHl7PSUSendingApplication());
        LdapUtils.storeNotEmpty(attrs, "hl7PSUReceivingApplication", ext.getHl7PSUReceivingApplications());
        LdapUtils.storeNotNull(attrs, "hl7PSUDelay", ext.getHl7PSUDelay());
        LdapUtils.storeNotNull(attrs, "hl7PSUTimeout", ext.getHl7PSUTimeout());
        LdapUtils.storeNotNull(attrs, "hl7PSUOnTimeout", ext.getHl7PSUOnTimeout());
        LdapUtils.storeNotNull(attrs, "hl7PSUMWL", ext.getHl7PSUMWL());
        LdapUtils.storeNotNull(attrs, "dcmAcceptConflictingPatientID", ext.getAcceptConflictingPatientID());
    }

    @Override
    protected void loadFrom(ApplicationEntity ae, Attributes attrs) throws NamingException {
        if (!LdapUtils.hasObjectClass(attrs, "dcmArchiveNetworkAE"))
            return;

        ArchiveAEExtension ext = new ArchiveAEExtension();
        ae.addAEExtension(ext);
        ext.setStorageID(LdapUtils.stringValue(attrs.get("dcmStorageID"), null));
        ext.setMetadataStorageID(LdapUtils.stringValue(attrs.get("dcmMetadataStorageID"), null));
        ext.setSeriesMetadataDelay(toDuration(attrs.get("dcmSeriesMetadataDelay")));
        ext.setPurgeInstanceRecordsDelay(toDuration(attrs.get("dcmPurgeInstanceRecordsDelay")));
        ext.setStoreAccessControlID(LdapUtils.stringValue(attrs.get("dcmStoreAccessControlID"), null));
        ext.setAccessControlIDs(LdapUtils.stringArray(attrs.get("dcmAccessControlID")));
        ext.setOverwritePolicy(LdapUtils.enumValue(OverwritePolicy.class, attrs.get("dcmOverwritePolicy"), null));
        ext.setBulkDataSpoolDirectory(LdapUtils.stringValue(attrs.get("dcmBulkDataSpoolDirectory"), null));
        ext.setQueryRetrieveViewID(LdapUtils.stringValue(attrs.get("dcmQueryRetrieveViewID"), null));
        ext.setPersonNameComponentOrderInsensitiveMatching(
                LdapUtils.booleanValue(attrs.get("dcmPersonNameComponentOrderInsensitiveMatching"), null));
        ext.setSendPendingCGet(LdapUtils.booleanValue(attrs.get("dcmSendPendingCGet"), null));
        ext.setSendPendingCMoveInterval(toDuration(attrs.get("dcmSendPendingCMoveInterval")));
        ext.setFallbackCMoveSCP(LdapUtils.stringValue(attrs.get("dcmFallbackCMoveSCP"), null));
        ext.setFallbackCMoveSCPDestination(LdapUtils.stringValue(attrs.get("dcmFallbackCMoveSCPDestination"), null));
        ext.setFallbackCMoveSCPRetries(LdapUtils.intValue(attrs.get("dcmFallbackCMoveSCPRetries"), 0));
        ext.setFallbackCMoveSCPLeadingCFindSCP(LdapUtils.stringValue(attrs.get("dcmFallbackCMoveSCPLeadingCFindSCP"), null));
        ext.setAlternativeCMoveSCP(LdapUtils.stringValue(attrs.get("dcmAltCMoveSCP"), null));
        ext.setWadoSR2HtmlTemplateURI(LdapUtils.stringValue(attrs.get("dcmWadoSR2HtmlTemplateURI"), null));
        ext.setWadoSR2TextTemplateURI(LdapUtils.stringValue(attrs.get("dcmWadoSR2TextTemplateURI"), null));
        ext.setQidoMaxNumberOfResults(LdapUtils.intValue(attrs.get("dcmQidoMaxNumberOfResults"), 0));
        ext.setMppsForwardDestinations(LdapUtils.stringArray(attrs.get("dcmFwdMppsDestination")));
        ext.setIanDestinations(LdapUtils.stringArray(attrs.get("dcmIanDestination")));
        ext.setIanDelay(toDuration(attrs.get("dcmIanDelay")));
        ext.setIanTimeout(toDuration(attrs.get("dcmIanTimeout")));
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
        ext.setHl7PSUDelay(toDuration(attrs.get("hl7PSUDelay")));
        ext.setHl7PSUTimeout(toDuration(attrs.get("hl7PSUTimeout")));
        ext.setHl7PSUOnTimeout(LdapUtils.booleanValue(attrs.get("hl7PSUOnTimeout"), null));
        ext.setHl7PSUMWL(LdapUtils.booleanValue(attrs.get("hl7PSUMWL"), null));
        ext.setAcceptConflictingPatientID(
                LdapUtils.enumValue(AcceptConflictingPatientID.class, attrs.get("dcmAcceptConflictingPatientID"), null));
    }

    @Override
    protected void storeDiffs(ApplicationEntity prev, ApplicationEntity ae, List<ModificationItem> mods) {
        ArchiveAEExtension aa = prev.getAEExtension(ArchiveAEExtension.class);
        ArchiveAEExtension bb = ae.getAEExtension(ArchiveAEExtension.class);
        if (aa == null || bb == null)
            return;

        LdapUtils.storeDiff(mods, "dcmStorageID", aa.getStorageID(), bb.getStorageID());
        LdapUtils.storeDiff(mods, "dcmMetadataStorageID", aa.getMetadataStorageID(), bb.getMetadataStorageID());
        LdapUtils.storeDiff(mods, "dcmSeriesMetadataDelay",
                aa.getSeriesMetadataDelay(),
                bb.getSeriesMetadataDelay());
        LdapUtils.storeDiff(mods, "dcmPurgeInstanceRecordsDelay",
                aa.getPurgeInstanceRecordsDelay(),
                bb.getPurgeInstanceRecordsDelay());
        LdapUtils.storeDiff(mods, "dcmStoreAccessControlID", aa.getStoreAccessControlID(), bb.getStoreAccessControlID());
        LdapUtils.storeDiff(mods, "dcmAccessControlIDs", aa.getAccessControlIDs(), bb.getAccessControlIDs());
        LdapUtils.storeDiff(mods, "dcmOverwritePolicy", aa.getOverwritePolicy(), bb.getOverwritePolicy());
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
        LdapUtils.storeDiff(mods, "dcmFallbackCMoveSCPRetries",
                aa.getFallbackCMoveSCPRetries(), bb.getFallbackCMoveSCPRetries(),  0);
        LdapUtils.storeDiff(mods, "dcmFallbackCMoveSCPLeadingCFindSCP",
                aa.getFallbackCMoveSCPLeadingCFindSCP(), bb.getFallbackCMoveSCPLeadingCFindSCP());
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
        LdapUtils.storeDiff(mods, "dcmHideSPSWithStatusFromMWL", aa.getHideSPSWithStatusFromMWL(), bb.getHideSPSWithStatusFromMWL());
        LdapUtils.storeDiff(mods, "dcmFallbackCMoveSCPStudyOlderThan",
                aa.getFallbackCMoveSCPStudyOlderThan(), bb.getFallbackCMoveSCPStudyOlderThan());
        LdapUtils.storeDiff(mods, "dcmStorePermissionServiceURL",
                aa.getStorePermissionServiceURL(), bb.getStorePermissionServiceURL());
        LdapUtils.storeDiff(mods, "dcmStorePermissionServiceResponsePattern",
                aa.getStorePermissionServiceResponsePattern(), bb.getStorePermissionServiceResponsePattern());
        LdapUtils.storeDiff(mods, "dcmAllowRejectionForDataRetentionPolicyExpired",
                aa.getAllowRejectionForDataRetentionPolicyExpired(), bb.getAllowRejectionForDataRetentionPolicyExpired());
        LdapUtils.storeDiff(mods, "dcmAcceptedUserRole", aa.getAcceptedUserRoles(), bb.getAcceptedUserRoles());
        LdapUtils.storeDiff(mods, "dcmAcceptMissingPatientID", aa.getAcceptMissingPatientID(), bb.getAcceptMissingPatientID());
        LdapUtils.storeDiff(mods, "dcmAllowDeleteStudyPermanently", aa.getAllowDeleteStudyPermanently(), bb.getAllowDeleteStudyPermanently());
        LdapUtils.storeDiff(mods, "dcmStorePermissionServiceExpirationDatePattern",
                aa.getStorePermissionServiceExpirationDatePattern(), bb.getStorePermissionServiceExpirationDatePattern());
        LdapUtils.storeDiff(mods, "dcmDefaultCharacterSet", aa.getDefaultCharacterSet(), bb.getDefaultCharacterSet());
        LdapUtils.storeDiff(mods, "dcmStorePermissionServiceErrorCommentPattern",
                aa.getStorePermissionServiceErrorCommentPattern(), bb.getStorePermissionServiceErrorCommentPattern());
        LdapUtils.storeDiff(mods, "dcmStorePermissionServiceErrorCodePattern",
                aa.getStorePermissionServiceErrorCodePattern(), bb.getStorePermissionServiceErrorCodePattern());
        LdapUtils.storeDiff(mods, "dcmRetrieveAET", aa.getRetrieveAETitles(), bb.getRetrieveAETitles());
        LdapUtils.storeDiff(mods, "dcmExternalRetrieveAEDestination",
                aa.getExternalRetrieveAEDestination(), bb.getExternalRetrieveAEDestination());
        LdapUtils.storeDiff(mods, "dcmAcceptedMoveDestination", aa.getAcceptedMoveDestinations(), bb.getAcceptedMoveDestinations());
        LdapUtils.storeDiff(mods, "dcmValidateCallingAEHostname", aa.getValidateCallingAEHostname(), bb.getValidateCallingAEHostname());
        LdapUtils.storeDiff(mods, "hl7PSUSendingApplication", aa.getHl7PSUSendingApplication(), bb.getHl7PSUSendingApplication());
        LdapUtils.storeDiff(mods, "hl7PSUReceivingApplication", aa.getHl7PSUReceivingApplications(), bb.getHl7PSUReceivingApplications());
        LdapUtils.storeDiff(mods, "hl7PSUDelay", aa.getHl7PSUDelay(), bb.getHl7PSUDelay());
        LdapUtils.storeDiff(mods, "hl7PSUTimeout", aa.getHl7PSUTimeout(), bb.getHl7PSUTimeout());
        LdapUtils.storeDiff(mods, "hl7PSUOnTimeout", aa.getHl7PSUOnTimeout(), bb.getHl7PSUOnTimeout());
        LdapUtils.storeDiff(mods, "hl7PSUMWL", aa.getHl7PSUMWL(), bb.getHl7PSUMWL());
        LdapUtils.storeDiff(mods, "dcmAcceptConflictingPatientID", aa.getAcceptConflictingPatientID(), bb.getAcceptConflictingPatientID());
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
        if (aa == null || bb == null)
            return;

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
        attrs.put(tagsAttr("dcmTag", filter.getSelection()));
        LdapUtils.storeNotNull(attrs, "dcmCustomAttribute1", filter.getCustomAttribute1());
        LdapUtils.storeNotNull(attrs, "dcmCustomAttribute2", filter.getCustomAttribute2());
        LdapUtils.storeNotNull(attrs, "dcmCustomAttribute3", filter.getCustomAttribute3());
        LdapUtils.storeNotNull(attrs, "dcmAttributeUpdatePolicy", filter.getAttributeUpdatePolicy());
        return attrs;
    }

    private static Attributes storeTo(MetadataFilter filter, String filterName,  BasicAttributes attrs) {
        attrs.put("objectclass", "dcmMetadataFilter");
        attrs.put("dcmMetadataFilterName", filterName);
        attrs.put(tagsAttr("dcmTag", filter.getSelection()));
        return attrs;
    }

    private static Attributes storeTo(HL7OrderSPSStatus hl7OrderSPSStatus, SPSStatus spsStatus, BasicAttributes attrs) {
        attrs.put("objectclass", "hl7OrderSPSStatus");
        attrs.put("dcmSPSStatus", spsStatus.name());
        LdapUtils.storeNotEmpty(attrs, "hl7OrderControlStatus", hl7OrderSPSStatus.getOrderControlStatusCodes());
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
        int[] is = new int[attr.size()];
        for (int i = 0; i < is.length; i++)
            is[i] = Integer.parseInt((String) attr.get(i), 16);

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

    private List<ModificationItem> storeDiffs(MetadataFilter prev, MetadataFilter filter,
                                              List<ModificationItem> mods) {
        storeDiffTags(mods, "dcmTag", prev.getSelection(), filter.getSelection());
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
        LdapUtils.storeNotDef(attrs, "dcmReadOnly", descriptor.isReadOnly(), false);
        LdapUtils.storeNotEmpty(attrs, "dcmDeleterThreshold", descriptor.getDeleterThresholdsAsStrings());
        LdapUtils.storeNotEmpty(attrs, "dcmProperty", toStrings(descriptor.getProperties()));
        LdapUtils.storeNotNull(attrs, "dcmExternalRetrieveAET", descriptor.getExternalRetrieveAETitle());
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
                        LdapUtils.enumValue(Availability.class, attrs.get("dcmInstanceAvailability"), null));
                desc.setReadOnly(LdapUtils.booleanValue(attrs.get("dcmReadOnly"), false));
                desc.setDeleterThresholdsFromStrings(LdapUtils.stringArray(attrs.get("dcmDeleterThreshold")));
                desc.setProperties(LdapUtils.stringArray(attrs.get("dcmProperty")));
                desc.setExternalRetrieveAETitle(LdapUtils.stringValue(attrs.get("dcmExternalRetrieveAET"), null));
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
        LdapUtils.storeDiff(mods, "dcmReadOnly", prev.isReadOnly(), desc.isReadOnly(), false);
        LdapUtils.storeDiff(mods, "dcmDeleterThreshold",
                prev.getDeleterThresholdsAsStrings(), desc.getDeleterThresholdsAsStrings());
        storeDiffProperties(mods, prev.getProperties(), desc.getProperties());
        LdapUtils.storeDiff(mods, "dcmExternalRetrieveAET", prev.getExternalRetrieveAETitle(), desc.getExternalRetrieveAETitle());
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
        LdapUtils.storeNotNull(attrs, "dcmJndiName", descriptor.getJndiName());
        LdapUtils.storeNotNull(attrs, "dicomDescription", descriptor.getDescription());
        LdapUtils.storeNotDef(attrs, "dcmMaxRetries", descriptor.getMaxRetries(), 0);
        LdapUtils.storeNotNull(attrs, "dcmRetryDelay", descriptor.getRetryDelay());
        LdapUtils.storeNotNull(attrs, "dcmMaxRetryDelay", descriptor.getMaxRetryDelay());
        LdapUtils.storeNotDef(attrs, "dcmRetryDelayMultiplier", descriptor.getRetryDelayMultiplier(), 100);
        LdapUtils.storeNotDef(attrs, "dcmRetryOnWarning", descriptor.isRetryOnWarning(), false);
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
                desc.setRetryDelay(toDuration(attrs.get("dcmRetryDelay")));
                desc.setMaxRetryDelay(toDuration(attrs.get("dcmMaxRetryDelay")));
                desc.setRetryDelayMultiplier(LdapUtils.intValue(attrs.get("dcmRetryDelayMultiplier"), 100));
                desc.setRetryOnWarning(LdapUtils.booleanValue(attrs.get("dcmRetryOnWarning"), false));
                desc.setPurgeQueueMessageCompletedDelay(toDuration(attrs.get("dcmPurgeQueueMessageCompletedDelay")));
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
        LdapUtils.storeDiff(mods, "dcmRetryDelayMultiplier",
                prev.getRetryDelayMultiplier(), desc.getRetryDelayMultiplier(), 100);
        LdapUtils.storeDiff(mods, "dcmRetryOnWarning", prev.isRetryOnWarning(), desc.isRetryOnWarning(), false);
        LdapUtils.storeDiff(mods, "dcmPurgeQueueMessageCompletedDelay",
                prev.getPurgeQueueMessageCompletedDelay(), desc.getPurgeQueueMessageCompletedDelay());
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
        LdapUtils.storeNotNull(attrs, "dcmStgCmtSCP", descriptor.getStgCmtSCPAETitle());
        LdapUtils.storeNotEmpty(attrs, "dcmIanDestination", descriptor.getIanDestinations());
        LdapUtils.storeNotEmpty(attrs, "dcmRetrieveAET", descriptor.getRetrieveAETitles());
        LdapUtils.storeNotNull(attrs, "dcmInstanceAvailability", descriptor.getInstanceAvailability());
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
                desc.setInstanceAvailability(
                        LdapUtils.enumValue(Availability.class, attrs.get("dcmInstanceAvailability"), null));
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
        LdapUtils.storeDiff(mods, "dcmStgCmtSCP", prev.getStgCmtSCPAETitle(), desc.getStgCmtSCPAETitle());
        LdapUtils.storeDiff(mods, "dcmIanDestination", prev.getIanDestinations(), desc.getIanDestinations());
        LdapUtils.storeDiff(mods, "dcmRetrieveAET", prev.getRetrieveAETitles(), desc.getRetrieveAETitles());
        LdapUtils.storeDiff(mods, "dcmInstanceAvailability",
                prev.getInstanceAvailability(), desc.getInstanceAvailability());
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
                rule.setExportDelay(toDuration(attrs.get("dcmDuration")));
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

    private static Duration toDuration(Attribute attr) throws NamingException {
        return attr != null ? Duration.parse((String) attr.get()) : null;
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
        LdapUtils.storeDiff(mods, "dcmEntity", prev.getEntity(), rule.getEntity());
        LdapUtils.storeDiff(mods, "dcmDuration", prev.getExportDelay(), rule.getExportDelay());
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
        LdapUtils.storeNotNull(attrs, "dicomTransferSyntax", rule.getTransferSyntax());
        LdapUtils.storeNotEmpty(attrs, "dcmImageWriteParam", rule.getImageWriteParams());
        LdapUtils.storeNotDef(attrs, "dcmRulePriority", rule.getPriority(), 0);
        return attrs;
    }

    private Attributes storeTo(StoreAccessControlIDRule rule, BasicAttributes attrs) {
        attrs.put("objectclass", "dcmStoreAccessControlIDRule");
        attrs.put("cn", rule.getCommonName());
        LdapUtils.storeNotEmpty(attrs, "dcmProperty", toStrings(rule.getConditions().getMap()));
        LdapUtils.storeNotNull(attrs, "dcmStoreAccessControlID", rule.getStoreAccessControlID());
        LdapUtils.storeNotDef(attrs, "dcmRulePriority", rule.getPriority(), 0);
        return attrs;
    }

    private Attributes storeTo(StudyRetentionPolicy policy, BasicAttributes attrs) {
        attrs.put("objectclass", "dcmStudyRetentionPolicy");
        attrs.put("cn", policy.getCommonName());
        LdapUtils.storeNotEmpty(attrs, "dcmProperty", toStrings(policy.getConditions().getMap()));
        LdapUtils.storeNotNull(attrs, "dcmRetentionPeriod", policy.getRetentionPeriod());
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
        LdapUtils.storeNotNull(attrs, "hl7OrderScheduledStationDeviceReference", config.deviceRef(station.getDeviceName()));
        LdapUtils.storeNotDef(attrs, "dcmRulePriority", station.getPriority(), 0);
        LdapUtils.storeNotEmpty(attrs, "dcmProperty", toStrings(station.getConditions().getMap()));
        return attrs;
    }

    private Attributes storeTo(RSForwardRule rule, BasicAttributes attrs) {
        attrs.put("objectclass", "dcmRSForwardRule");
        attrs.put("cn", rule.getCommonName());
        LdapUtils.storeNotNull(attrs, "dcmURI", rule.getBaseURI());
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
        LdapUtils.storeDiff(mods, "dicomTransferSyntax", prev.getTransferSyntax(), rule.getTransferSyntax());
        LdapUtils.storeDiff(mods, "dcmImageWriteParam", prev.getImageWriteParams(), rule.getImageWriteParams());
        LdapUtils.storeDiff(mods, "dcmRulePriority", prev.getPriority(), rule.getPriority(), 0);
        return mods;
    }

    private List<ModificationItem> storeDiffs(
            StoreAccessControlIDRule prev, StoreAccessControlIDRule rule, ArrayList<ModificationItem> mods) {
        storeDiffProperties(mods, prev.getConditions().getMap(), rule.getConditions().getMap());
        LdapUtils.storeDiff(mods, "dcmStoreAccessControlID",
                prev.getStoreAccessControlID(), rule.getStoreAccessControlID());
        LdapUtils.storeDiff(mods, "dcmRulePriority", prev.getPriority(), rule.getPriority(), 0);
        return mods;
    }

    private List<ModificationItem> storeDiffs(
            StudyRetentionPolicy prev, StudyRetentionPolicy policy, ArrayList<ModificationItem> mods) {
        storeDiffProperties(mods, prev.getConditions().getMap(), policy.getConditions().getMap());
        LdapUtils.storeDiff(mods, "dcmRetentionPeriod", prev.getRetentionPeriod(), policy.getRetentionPeriod());
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
        LdapUtils.storeDiff(mods, "hl7OrderScheduledStationDeviceReference", prev.getDeviceName(), station.getDeviceName());
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
        LdapUtils.storeDiff(mods, "dcmURI", prev.getBaseURI(), rule.getBaseURI());
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
        LdapUtils.storeNotNull(attrs, "dcmDIMSE", coercion.getDIMSE());
        LdapUtils.storeNotNull(attrs, "dicomTransferRole", coercion.getRole());
        LdapUtils.storeNotEmpty(attrs, "dcmHostname", coercion.getHostNames());
        LdapUtils.storeNotEmpty(attrs, "dcmAETitle", coercion.getAETitles());
        LdapUtils.storeNotEmpty(attrs, "dcmSOPClass", coercion.getSOPClasses());
        LdapUtils.storeNotNull(attrs, "dcmURI", coercion.getXSLTStylesheetURI());
        LdapUtils.storeNotDef(attrs, "dcmNoKeywords", coercion.isNoKeywords(), false);
        LdapUtils.storeNotNull(attrs, "dcmLeadingCFindSCP", coercion.getLeadingCFindSCP());
        LdapUtils.storeNotNull(attrs, "dcmMergeMWLTemplateURI",
                coercion.getMergeMWLTemplateURI());
        LdapUtils.storeNotNull(attrs, "dcmMergeMWLMatchingKey",
                coercion.getMergeMWLMatchingKey());
        LdapUtils.storeNotNull(attrs, "dcmAttributeUpdatePolicy", coercion.getAttributeUpdatePolicy());
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
                coercion.setMergeMWLTemplateURI(
                        LdapUtils.stringValue(attrs.get("dcmMergeMWLTemplateURI"), null));
                coercion.setMergeMWLMatchingKey(
                        LdapUtils.enumValue(MergeMWLMatchingKey.class,
                        attrs.get("dcmMergeMWLMatchingKey"), null));
                coercion.setAttributeUpdatePolicy(LdapUtils.enumValue(org.dcm4che3.data.Attributes.UpdatePolicy.class,
                        attrs.get("dcmAttributeUpdatePolicy"), null));
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
        LdapUtils.storeDiff(mods, "dcmLeadingCFindSCP", prev.getLeadingCFindSCP(), coercion.getLeadingCFindSCP());
        LdapUtils.storeDiff(mods, "dcmMergeMWLTemplateURI",
                prev.getMergeMWLTemplateURI(),
                coercion.getMergeMWLTemplateURI());
        LdapUtils.storeDiff(mods, "dcmMergeMWLMatchingKey",
                prev.getMergeMWLMatchingKey(),
                coercion.getMergeMWLMatchingKey());
        LdapUtils.storeDiff(mods, "dcmAttributeUpdatePolicy",
                prev.getAttributeUpdatePolicy(),
                coercion.getAttributeUpdatePolicy());
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
        LdapUtils.storeNotNull(attrs, "dcmRejectionNoteType", rjNote.getRejectionNoteType());
        LdapUtils.storeNotNull(attrs, "dcmRejectionNoteCode", rjNote.getRejectionNoteCode());
        LdapUtils.storeNotNull(attrs, "dcmAcceptPreviousRejectedInstance", rjNote.getAcceptPreviousRejectedInstance());
        LdapUtils.storeNotEmpty(attrs, "dcmOverwritePreviousRejection", rjNote.getOverwritePreviousRejection());
        LdapUtils.storeNotNull(attrs, "dcmDeleteRejectedInstanceDelay", rjNote.getDeleteRejectedInstanceDelay());
        LdapUtils.storeNotNull(attrs, "dcmDeleteRejectionNoteDelay", rjNote.getDeleteRejectionNoteDelay());
        return attrs;
    }

    private Attributes storeTo(IDGenerator generator, BasicAttributes attrs) {
        attrs.put("objectClass", "dcmIDGenerator");
        attrs.put("dcmIDGeneratorName", generator.getName().name());
        LdapUtils.storeNotNull(attrs, "dcmIDGeneratorFormat", generator.getFormat());
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
                        null));
                rjNote.setOverwritePreviousRejection(LdapUtils.codeArray(attrs.get("dcmOverwritePreviousRejection")));
                rjNote.setDeleteRejectedInstanceDelay(toDuration(attrs.get("dcmDeleteRejectedInstanceDelay")));
                rjNote.setDeleteRejectionNoteDelay(toDuration(attrs.get("dcmDeleteRejectionNoteDelay")));
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
                            config.destroySubcontext(LdapUtils.dnOf("dcmIDGenerator", name.name(), deviceDN));
        }
        for (IDGenerator entryNew : arcDev.getIDGenerators().values()) {
            IDGenerator.Name name = entryNew.getName();
            String dn = LdapUtils.dnOf("dcmIDGenerator", name.name(), deviceDN);
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
        LdapUtils.storeDiff(mods, "dcmRejectionNoteType", prev.getRejectionNoteType(), rjNote.getRejectionNoteType());
        LdapUtils.storeDiff(mods, "dcmRejectionNoteCode", prev.getRejectionNoteCode(), rjNote.getRejectionNoteCode());
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

    private List<ModificationItem> storeDiffs(IDGenerator prev, IDGenerator generator,
                                              ArrayList<ModificationItem> mods) {
//        LdapUtils.storeDiff(mods, "dcmIDGeneratorName", prev.getName(), generator.getName());
        LdapUtils.storeDiff(mods, "dcmIDGeneratorFormat", prev.getFormat(), generator.getFormat());
        LdapUtils.storeDiff(mods, "dcmIDGeneratorInitialValue", prev.getInitialValue(), generator.getInitialValue(), 1);
        return mods;
    }

}
