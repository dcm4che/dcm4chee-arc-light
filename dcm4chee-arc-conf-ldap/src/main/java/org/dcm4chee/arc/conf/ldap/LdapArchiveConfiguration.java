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

package org.dcm4chee.arc.conf.ldap;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.ldap.LdapDicomConfiguration;
import org.dcm4che3.conf.ldap.LdapDicomConfigurationExtension;
import org.dcm4che3.conf.api.ConfigurationChanges;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOG = LoggerFactory.getLogger(LdapArchiveConfiguration.class);

    @Override
    protected void storeTo(ConfigurationChanges.ModifiedObject ldapObj, Device device, Attributes attrs) {
        ArchiveDeviceExtension ext = device.getDeviceExtension(ArchiveDeviceExtension.class);
        if (ext == null)
            return;

        attrs.get("objectclass").add("dcmArchiveDevice");
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmFuzzyAlgorithmClass", ext.getFuzzyAlgorithmClass(), null);
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmSeriesMetadataStorageID", ext.getSeriesMetadataStorageIDs());
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmSeriesMetadataDelay", ext.getSeriesMetadataDelay(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmSeriesMetadataPollingInterval", ext.getSeriesMetadataPollingInterval(), null);
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmSeriesMetadataFetchSize", ext.getSeriesMetadataFetchSize(), 100);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmPurgeInstanceRecordsDelay", ext.getPurgeInstanceRecordsDelay(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmPurgeInstanceRecordsPollingInterval", ext.getPurgeInstanceRecordsPollingInterval(), null);
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmPurgeInstanceRecordsFetchSize", ext.getPurgeInstanceRecordsFetchSize(), 100);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmOverwritePolicy", ext.getOverwritePolicy(), OverwritePolicy.NEVER);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmBulkDataSpoolDirectory",
                ext.getBulkDataSpoolDirectory(), ArchiveDeviceExtension.JBOSS_SERVER_TEMP_DIR);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmQueryRetrieveViewID", ext.getQueryRetrieveViewID(), null);
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmPersonNameComponentOrderInsensitiveMatching",
                ext.isPersonNameComponentOrderInsensitiveMatching(), false);
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmSendPendingCGet", ext.isSendPendingCGet(), false);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmSendPendingCMoveInterval", ext.getSendPendingCMoveInterval(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmSpanningCFindSCP", ext.getSpanningCFindSCP(), null);
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmSpanningCFindSCPRetrieveAET", ext.getSpanningCFindSCPRetrieveAETitles());
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmSpanningCFindSCPPolicy",
                ext.getSpanningCFindSCPPolicy(), SpanningCFindSCPPolicy.REPLACE);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmFallbackCMoveSCP", ext.getFallbackCMoveSCP(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmFallbackCMoveSCPDestination", ext.getFallbackCMoveSCPDestination(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmFallbackCMoveSCPLeadingCFindSCP", ext.getFallbackCMoveSCPLeadingCFindSCP(), null);
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmFallbackCMoveSCPRetries", ext.getFallbackCMoveSCPRetries(), 0);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmAltCMoveSCP", ext.getAlternativeCMoveSCP(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmWadoSR2HtmlTemplateURI", ext.getWadoSR2HtmlTemplateURI(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmWadoSR2TextTemplateURI", ext.getWadoSR2TextTemplateURI(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "hl7PatientUpdateTemplateURI", ext.getPatientUpdateTemplateURI(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "hl7ImportReportTemplateURI", ext.getImportReportTemplateURI(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "hl7ScheduleProcedureTemplateURI", ext.getScheduleProcedureTemplateURI(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "hl7OutgoingPatientUpdateTemplateURI", ext.getOutgoingPatientUpdateTemplateURI(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "hl7LogFilePattern", ext.getHl7LogFilePattern(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "hl7ErrorLogFilePattern", ext.getHl7ErrorLogFilePattern(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmUnzipVendorDataToURI", ext.getUnzipVendorDataToURI(), null);
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmWadoSupportedSRClasses", ext.getWadoSupportedSRClasses());
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmQueryFetchSize", ext.getQueryFetchSize(), 100);
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmQueryMaxNumberOfResults", ext.getQueryMaxNumberOfResults(), 0);
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmQidoMaxNumberOfResults", ext.getQidoMaxNumberOfResults(), 100);
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmFwdMppsDestination", ext.getMppsForwardDestinations());
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmIanDestination", ext.getIanDestinations());
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmIanDelay", ext.getIanDelay(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmIanTimeout", ext.getIanTimeout(), null);
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmIanOnTimeout", ext.isIanOnTimeout(), false);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmIanTaskPollingInterval", ext.getIanTaskPollingInterval(), null);
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmIanTaskFetchSize", ext.getIanTaskFetchSize(), 100);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmExportTaskPollingInterval", ext.getExportTaskPollingInterval(), null);
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmExportTaskFetchSize", ext.getExportTaskFetchSize(), 5);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmPurgeStoragePollingInterval", ext.getPurgeStoragePollingInterval(), null);
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmPurgeStorageFetchSize", ext.getPurgeStorageFetchSize(), 100);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmDeleteRejectedPollingInterval", ext.getDeleteRejectedPollingInterval(), null);
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmDeleteRejectedFetchSize", ext.getDeleteRejectedFetchSize(), 100);
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmDeleteStudyBatchSize", ext.getDeleteStudyBatchSize(), 10);
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmDeletePatientOnDeleteLastStudy",
                ext.isDeletePatientOnDeleteLastStudy(), false);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmMaxAccessTimeStaleness", ext.getMaxAccessTimeStaleness(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmAECacheStaleTimeout", ext.getAECacheStaleTimeout(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmLeadingCFindSCPQueryCacheStaleTimeout", ext.getLeadingCFindSCPQueryCacheStaleTimeout(), null);
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmLeadingCFindSCPQueryCacheSize", ext.getLeadingCFindSCPQueryCacheSize(), 10);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmAuditSpoolDirectory",
                ext.getAuditSpoolDirectory(), ArchiveDeviceExtension.JBOSS_SERVER_TEMP_DIR);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmAuditPollingInterval", ext.getAuditPollingInterval(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmAuditAggregateDuration", ext.getAuditAggregateDuration(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmStowSpoolDirectory",
                ext.getStowSpoolDirectory(), ArchiveDeviceExtension.JBOSS_SERVER_TEMP_DIR);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmPurgeQueueMessagePollingInterval",
                ext.getPurgeQueueMessagePollingInterval(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmWadoSpoolDirectory",
                ext.getWadoSpoolDirectory(), ArchiveDeviceExtension.JBOSS_SERVER_TEMP_DIR);
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmHideSPSWithStatusFromMWL", ext.getHideSPSWithStatusFrom());
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmRejectExpiredStudiesPollingInterval",
                ext.getRejectExpiredStudiesPollingInterval(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmRejectExpiredStudiesPollingStartTime",
                ext.getRejectExpiredStudiesPollingStartTime(), null);
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmRejectExpiredStudiesFetchSize", ext.getRejectExpiredStudiesFetchSize(), 0);
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmRejectExpiredSeriesFetchSize", ext.getRejectExpiredSeriesFetchSize(), 0);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmRejectExpiredStudiesAETitle", ext.getRejectExpiredStudiesAETitle(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmFallbackCMoveSCPStudyOlderThan", ext.getFallbackCMoveSCPStudyOlderThan(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmStorePermissionServiceURL", ext.getStorePermissionServiceURL(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmStorePermissionServiceResponsePattern",
                ext.getStorePermissionServiceResponsePattern(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmStorePermissionCacheStaleTimeout",
                ext.getStorePermissionCacheStaleTimeout(), null);
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmStorePermissionCacheSize", ext.getStorePermissionCacheSize(), 10);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmMergeMWLCacheStaleTimeout", ext.getMergeMWLCacheStaleTimeout(), null);
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmMergeMWLCacheSize", ext.getMergeMWLCacheSize(), 10);
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmStoreUpdateDBMaxRetries", ext.getStoreUpdateDBMaxRetries(), 1);
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmStoreUpdateDBMaxRetryDelay", ext.getStoreUpdateDBMaxRetryDelay(), 1000);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmAllowRejectionForDataRetentionPolicyExpired",
                ext.getAllowRejectionForDataRetentionPolicyExpired(), AllowRejectionForDataRetentionPolicyExpired.STUDY_RETENTION_POLICY);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmAcceptMissingPatientID",
                ext.getAcceptMissingPatientID(), AcceptMissingPatientID.CREATE);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmAllowDeleteStudyPermanently",
                ext.getAllowDeleteStudyPermanently(), AllowDeleteStudyPermanently.REJECTED);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmStorePermissionServiceExpirationDatePattern",
                ext.getStorePermissionServiceExpirationDatePattern(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmShowPatientInfoInSystemLog",
                ext.getShowPatientInfoInSystemLog(), ShowPatientInfo.PLAIN_TEXT);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmShowPatientInfoInAuditLog",
                ext.getShowPatientInfoInAuditLog(), ShowPatientInfo.PLAIN_TEXT);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmPurgeStgCmtCompletedDelay", ext.getPurgeStgCmtCompletedDelay(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmPurgeStgCmtPollingInterval", ext.getPurgeStgCmtPollingInterval(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmDefaultCharacterSet", ext.getDefaultCharacterSet(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmStorePermissionServiceErrorCommentPattern", ext.getStorePermissionServiceErrorCommentPattern(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmStorePermissionServiceErrorCodePattern", ext.getStorePermissionServiceErrorCodePattern(), null);
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmRetrieveAET", ext.getRetrieveAETitles());
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmExternalRetrieveAEDestination", ext.getExternalRetrieveAEDestination(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmXDSiImagingDocumentSourceAETitle", ext.getXDSiImagingDocumentSourceAETitle(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmRemapRetrieveURL", ext.getRemapRetrieveURL(), null);
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmValidateCallingAEHostname", ext.isValidateCallingAEHostname(), false);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "hl7PSUSendingApplication", ext.getHl7PSUSendingApplication(), null);
        LdapUtils.storeNotEmpty(ldapObj, attrs, "hl7PSUReceivingApplication", ext.getHl7PSUReceivingApplications());
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "hl7PSUDelay", ext.getHl7PSUDelay(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "hl7PSUTimeout", ext.getHl7PSUTimeout(), null);
        LdapUtils.storeNotDef(ldapObj, attrs, "hl7PSUOnTimeout", ext.isHl7PSUOnTimeout(), false);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "hl7PSUTaskPollingInterval", ext.getHl7PSUTaskPollingInterval(), null);
        LdapUtils.storeNotDef(ldapObj, attrs, "hl7PSUTaskFetchSize", ext.getHl7PSUTaskFetchSize(), 100);
        LdapUtils.storeNotDef(ldapObj, attrs, "hl7PSUMWL", ext.isHl7PSUMWL(), false);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmAcceptConflictingPatientID",
                ext.getAcceptConflictingPatientID(), AcceptConflictingPatientID.MERGED);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmAuditRecordRepositoryURL", ext.getAuditRecordRepositoryURL(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmElasticSearchURL", ext.getElasticSearchURL(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs,"dcmAudit2JsonFhirTemplateURI", ext.getAudit2JsonFhirTemplateURI(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs,"dcmAudit2XmlFhirTemplateURI", ext.getAudit2XmlFhirTemplateURI(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmCopyMoveUpdatePolicy", ext.getCopyMoveUpdatePolicy(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmLinkMWLEntryUpdatePolicy", ext.getLinkMWLEntryUpdatePolicy(), null);
        LdapUtils.storeNotDef(ldapObj, attrs, "hl7TrackChangedPatientID", ext.isHl7TrackChangedPatientID(), true);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmInvokeImageDisplayPatientURL", ext.getInvokeImageDisplayPatientURL(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmInvokeImageDisplayStudyURL", ext.getInvokeImageDisplayStudyURL(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "hl7ADTSendingApplication", ext.getHl7ADTSendingApplication(), null);
        LdapUtils.storeNotEmpty(ldapObj, attrs, "hl7ADTReceivingApplication", ext.getHl7ADTReceivingApplication());
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "hl7ScheduledProtocolCodeInOrder",
                ext.getHl7ScheduledProtocolCodeInOrder(), ScheduledProtocolCodeInOrder.OBR_4_4);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "hl7ScheduledStationAETInOrder", ext.getHl7ScheduledStationAETInOrder(), null);
        LdapUtils.storeNotEmpty(ldapObj, attrs, "hl7NoPatientCreateMessageType", ext.getHl7NoPatientCreateMessageTypes());
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmAuditUnknownStudyInstanceUID",
                ext.getAuditUnknownStudyInstanceUID(), ArchiveDeviceExtension.AUDIT_UNKNOWN_STUDY_INSTANCE_UID);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmAuditUnknownPatientID",
                ext.getAuditUnknownPatientID(), ArchiveDeviceExtension.AUDIT_UNKNOWN_PATIENT_ID);
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmAuditSoftwareConfigurationVerbose", ext.isAuditSoftwareConfigurationVerbose(), false);
        LdapUtils.storeNotDef(ldapObj, attrs, "hl7UseNullValue", ext.isHl7UseNullValue(), false);
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
        ext.setSpanningCFindSCP(LdapUtils.stringValue(attrs.get("dcmSpanningCFindSCP"), null));
        ext.setSpanningCFindSCPRetrieveAETitles(LdapUtils.stringArray(attrs.get("dcmSpanningCFindSCPRetrieveAET")));
        ext.setSpanningCFindSCPPolicy(LdapUtils.enumValue(
                SpanningCFindSCPPolicy.class, attrs.get("dcmSpanningCFindSCPPolicy"), SpanningCFindSCPPolicy.REPLACE));
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
        ext.setOutgoingPatientUpdateTemplateURI(LdapUtils.stringValue(attrs.get("hl7OutgoingPatientUpdateTemplateURI"), null));
        ext.setHl7LogFilePattern(LdapUtils.stringValue(attrs.get("hl7LogFilePattern"), null));
        ext.setHl7ErrorLogFilePattern(LdapUtils.stringValue(attrs.get("hl7ErrorLogFilePattern"), null));
        ext.setUnzipVendorDataToURI(LdapUtils.stringValue(attrs.get("dcmUnzipVendorDataToURI"), null));
        ext.setWadoSupportedSRClasses(LdapUtils.stringArray(attrs.get("dcmWadoSupportedSRClasses")));
        ext.setQueryFetchSize(LdapUtils.intValue(attrs.get("dcmQueryFetchSize"), 100));
        ext.setQueryMaxNumberOfResults(LdapUtils.intValue(attrs.get("dcmQueryMaxNumberOfResults"), 0));
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
        ext.setLinkMWLEntryUpdatePolicy(LdapUtils.enumValue(org.dcm4che3.data.Attributes.UpdatePolicy.class, attrs.get("dcmLinkMWLEntryUpdatePolicy"), null));
        ext.setHl7TrackChangedPatientID(LdapUtils.booleanValue(attrs.get("hl7TrackChangedPatientID"), true));
        ext.setInvokeImageDisplayPatientURL(LdapUtils.stringValue(attrs.get("dcmInvokeImageDisplayPatientURL"), null));
        ext.setInvokeImageDisplayStudyURL(LdapUtils.stringValue(attrs.get("dcmInvokeImageDisplayStudyURL"), null));
        ext.setHl7ADTReceivingApplication(LdapUtils.stringArray(attrs.get("hl7ADTReceivingApplication")));
        ext.setHl7ADTSendingApplication(LdapUtils.stringValue(attrs.get("hl7ADTSendingApplication"), null));
        ext.setHl7ScheduledProtocolCodeInOrder(LdapUtils.enumValue(ScheduledProtocolCodeInOrder.class,
                attrs.get("hl7ScheduledProtocolCodeInOrder"), ScheduledProtocolCodeInOrder.OBR_4_4));
        ext.setHl7ScheduledStationAETInOrder(LdapUtils.enumValue(ScheduledStationAETInOrder.class,
                attrs.get("hl7ScheduledStationAETInOrder"), null));
        ext.setHl7NoPatientCreateMessageTypes(LdapUtils.stringArray(attrs.get("hl7NoPatientCreateMessageType")));
        ext.setAuditUnknownStudyInstanceUID(LdapUtils.stringValue(
                attrs.get("dcmAuditUnknownStudyInstanceUID"), ArchiveDeviceExtension.AUDIT_UNKNOWN_STUDY_INSTANCE_UID));
        ext.setAuditUnknownPatientID(LdapUtils.stringValue(
                attrs.get("dcmAuditUnknownPatientID"), ArchiveDeviceExtension.AUDIT_UNKNOWN_PATIENT_ID));
        ext.setAuditSoftwareConfigurationVerbose(LdapUtils.booleanValue(attrs.get("dcmAuditSoftwareConfigurationVerbose"), false));
        ext.setHl7UseNullValue(LdapUtils.booleanValue(attrs.get("hl7UseNullValue"), false));
    }

    @Override
    protected void storeDiffs(ConfigurationChanges.ModifiedObject ldapObj, Device prev, Device device, List<ModificationItem> mods) {
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
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmFuzzyAlgorithmClass", aa.getFuzzyAlgorithmClass(), bb.getFuzzyAlgorithmClass(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmSeriesMetadataStorageID",
                aa.getSeriesMetadataStorageIDs(),
                bb.getSeriesMetadataStorageIDs());
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmSeriesMetadataDelay", aa.getSeriesMetadataDelay(), bb.getSeriesMetadataDelay(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmSeriesMetadataPollingInterval",
                aa.getSeriesMetadataPollingInterval(),
                bb.getSeriesMetadataPollingInterval(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmSeriesMetadataFetchSize",
                aa.getSeriesMetadataFetchSize(),
                bb.getSeriesMetadataFetchSize(),
                100);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmPurgeInstanceRecordsDelay",
                aa.getPurgeInstanceRecordsDelay(),
                bb.getPurgeInstanceRecordsDelay(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmPurgeInstanceRecordsPollingInterval",
                aa.getPurgeInstanceRecordsPollingInterval(),
                bb.getPurgeInstanceRecordsPollingInterval(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmPurgeInstanceRecordsFetchSize",
                aa.getPurgeInstanceRecordsFetchSize(),
                bb.getPurgeInstanceRecordsFetchSize(),
                100);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmOverwritePolicy", aa.getOverwritePolicy(), bb.getOverwritePolicy(), OverwritePolicy.NEVER);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmBulkDataSpoolDirectory",
                aa.getBulkDataSpoolDirectory(),
                bb.getBulkDataSpoolDirectory(),
                ArchiveDeviceExtension.JBOSS_SERVER_TEMP_DIR);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmQueryRetrieveViewID", aa.getQueryRetrieveViewID(), bb.getQueryRetrieveViewID(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmPersonNameComponentOrderInsensitiveMatching",
                aa.isPersonNameComponentOrderInsensitiveMatching(),
                bb.isPersonNameComponentOrderInsensitiveMatching(),
                false);
        LdapUtils.storeDiff(ldapObj, mods, "dcmSendPendingCGet", aa.isSendPendingCGet(), bb.isSendPendingCGet(), false);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmSendPendingCMoveInterval",
                aa.getSendPendingCMoveInterval(), bb.getSendPendingCMoveInterval(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmSpanningCFindSCP",
                aa.getSpanningCFindSCP(), bb.getSpanningCFindSCP(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmSpanningCFindSCPRetrieveAET",
                aa.getSpanningCFindSCPRetrieveAETitles(), bb.getSpanningCFindSCPRetrieveAETitles());
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmSpanningCFindSCPPolicy",
                aa.getSpanningCFindSCPPolicy(), bb.getSpanningCFindSCPPolicy(), SpanningCFindSCPPolicy.REPLACE);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmFallbackCMoveSCP", aa.getFallbackCMoveSCP(), bb.getFallbackCMoveSCP(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmFallbackCMoveSCPDestination",
                aa.getFallbackCMoveSCPDestination(), bb.getFallbackCMoveSCPDestination(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmFallbackCMoveSCPRetries",
                aa.getFallbackCMoveSCPRetries(), bb.getFallbackCMoveSCPRetries(),  0);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmFallbackCMoveSCPLeadingCFindSCP",
                aa.getFallbackCMoveSCPLeadingCFindSCP(), bb.getFallbackCMoveSCPLeadingCFindSCP(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmAltCMoveSCP", aa.getAlternativeCMoveSCP(), bb.getAlternativeCMoveSCP(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmWadoSR2HtmlTemplateURI",
                aa.getWadoSR2HtmlTemplateURI(), bb.getWadoSR2HtmlTemplateURI(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmWadoSR2TextTemplateURI",
                aa.getWadoSR2TextTemplateURI(), bb.getWadoSR2TextTemplateURI(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "hl7ImportReportTemplateURI",
                aa.getImportReportTemplateURI(), bb.getImportReportTemplateURI(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "hl7PatientUpdateTemplateURI",
                aa.getPatientUpdateTemplateURI(), bb.getPatientUpdateTemplateURI(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "hl7ScheduleProcedureTemplateURI", aa.getScheduleProcedureTemplateURI(),
                bb.getScheduleProcedureTemplateURI(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "hl7OutgoingPatientUpdateTemplateURI", aa.getOutgoingPatientUpdateTemplateURI(),
                bb.getOutgoingPatientUpdateTemplateURI(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "hl7LogFilePattern", aa.getHl7LogFilePattern(), bb.getHl7LogFilePattern(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "hl7ErrorLogFilePattern", aa.getHl7ErrorLogFilePattern(), bb.getHl7ErrorLogFilePattern(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmUnzipVendorDataToURI",
                aa.getUnzipVendorDataToURI(), bb.getUnzipVendorDataToURI(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmWadoSupportedSRClasses",
                aa.getWadoSupportedSRClasses(), bb.getWadoSupportedSRClasses());
        LdapUtils.storeDiff(ldapObj, mods, "dcmQueryFetchSize",
                aa.getQueryFetchSize(), bb.getQueryFetchSize(),  100);
        LdapUtils.storeDiff(ldapObj, mods, "dcmQueryMaxNumberOfResults",
                aa.getQueryMaxNumberOfResults(), bb.getQueryMaxNumberOfResults(),  0);
        LdapUtils.storeDiff(ldapObj, mods, "dcmQidoMaxNumberOfResults",
                aa.getQidoMaxNumberOfResults(), bb.getQidoMaxNumberOfResults(),  0);
        LdapUtils.storeDiff(ldapObj, mods, "dcmFwdMppsDestination",
                aa.getMppsForwardDestinations(), bb.getMppsForwardDestinations());
        LdapUtils.storeDiff(ldapObj, mods, "dcmIanDestination", aa.getIanDestinations(), bb.getIanDestinations());
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmIanDelay", aa.getIanDelay(), bb.getIanDelay(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmIanTimeout", aa.getIanTimeout(), bb.getIanTimeout(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmIanOnTimeout", aa.isIanOnTimeout(), bb.isIanOnTimeout(), false);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmIanTaskPollingInterval",
                aa.getIanTaskPollingInterval(), bb.getIanTaskPollingInterval(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmIanTaskFetchSize", aa.getIanTaskFetchSize(), bb.getIanTaskFetchSize(), 100);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmExportTaskPollingInterval",
                aa.getExportTaskPollingInterval(), bb.getExportTaskPollingInterval(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmExportTaskFetchSize",
                aa.getExportTaskFetchSize(), bb.getExportTaskFetchSize(), 5);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmPurgeStoragePollingInterval",
                aa.getPurgeStoragePollingInterval(), bb.getPurgeStoragePollingInterval(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmPurgeStorageFetchSize",
                aa.getPurgeStorageFetchSize(), bb.getPurgeStorageFetchSize(), 100);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmDeleteRejectedPollingInterval",
                aa.getDeleteRejectedPollingInterval(), bb.getDeleteRejectedPollingInterval(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmDeleteRejectedFetchSize",
                aa.getDeleteRejectedFetchSize(), bb.getDeleteRejectedFetchSize(), 100);
        LdapUtils.storeDiff(ldapObj, mods, "dcmDeleteStudyBatchSize",
                aa.getDeleteStudyBatchSize(), bb.getDeleteStudyBatchSize(), 10);
        LdapUtils.storeDiff(ldapObj, mods, "dcmDeletePatientOnDeleteLastStudy",
                aa.isDeletePatientOnDeleteLastStudy(), bb.isDeletePatientOnDeleteLastStudy(), false);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmMaxAccessTimeStaleness",
                aa.getMaxAccessTimeStaleness(), bb.getMaxAccessTimeStaleness(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmAECacheStaleTimeout",
                aa.getAECacheStaleTimeout(), bb.getAECacheStaleTimeout(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmLeadingCFindSCPQueryCacheStaleTimeout",
                aa.getLeadingCFindSCPQueryCacheStaleTimeout(), bb.getLeadingCFindSCPQueryCacheStaleTimeout(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmLeadingCFindSCPQueryCacheSize",
                aa.getLeadingCFindSCPQueryCacheSize(), bb.getLeadingCFindSCPQueryCacheSize(), 10);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmAuditSpoolDirectory",
                aa.getAuditSpoolDirectory(),
                bb.getAuditSpoolDirectory(),
                ArchiveDeviceExtension.JBOSS_SERVER_TEMP_DIR);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmAuditPollingInterval",
                aa.getAuditPollingInterval(), bb.getAuditPollingInterval(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmAuditAggregateDuration",
                aa.getAuditAggregateDuration(), bb.getAuditAggregateDuration(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmStowSpoolDirectory",
                aa.getStowSpoolDirectory(),
                bb.getStowSpoolDirectory(),
                ArchiveDeviceExtension.JBOSS_SERVER_TEMP_DIR);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmPurgeQueueMessagePollingInterval", aa.getPurgeQueueMessagePollingInterval(),
                bb.getPurgeQueueMessagePollingInterval(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmWadoSpoolDirectory",
                aa.getWadoSpoolDirectory(),
                bb.getWadoSpoolDirectory(),
                ArchiveDeviceExtension.JBOSS_SERVER_TEMP_DIR);
        LdapUtils.storeDiff(ldapObj, mods, "dcmHideSPSWithStatusFromMWL", aa.getHideSPSWithStatusFrom(), bb.getHideSPSWithStatusFrom());
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmRejectExpiredStudiesPollingInterval",
                aa.getRejectExpiredStudiesPollingInterval(), bb.getRejectExpiredStudiesPollingInterval(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmRejectExpiredStudiesPollingStartTime",
                aa.getRejectExpiredStudiesPollingStartTime(), bb.getRejectExpiredStudiesPollingStartTime(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmRejectExpiredStudiesFetchSize",
                aa.getRejectExpiredStudiesFetchSize(), bb.getRejectExpiredStudiesFetchSize(), 0);
        LdapUtils.storeDiff(ldapObj, mods, "dcmRejectExpiredSeriesFetchSize",
                aa.getRejectExpiredSeriesFetchSize(), bb.getRejectExpiredSeriesFetchSize(), 0);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmRejectExpiredStudiesAETitle",
                aa.getRejectExpiredStudiesAETitle(), bb.getRejectExpiredStudiesAETitle(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmFallbackCMoveSCPStudyOlderThan",
                aa.getFallbackCMoveSCPStudyOlderThan(), bb.getFallbackCMoveSCPStudyOlderThan(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmStorePermissionServiceURL",
                aa.getStorePermissionServiceURL(), bb.getStorePermissionServiceURL(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmStorePermissionServiceResponsePattern",
                aa.getStorePermissionServiceResponsePattern(), bb.getStorePermissionServiceResponsePattern(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmStorePermissionCacheStaleTimeout",
                aa.getStorePermissionCacheStaleTimeout(), bb.getStorePermissionCacheStaleTimeout(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmStorePermissionCacheSize",
                aa.getStorePermissionCacheSize(), bb.getStorePermissionCacheSize(), 10);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmMergeMWLCacheStaleTimeout",
                aa.getMergeMWLCacheStaleTimeout(), bb.getMergeMWLCacheStaleTimeout(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmMergeMWLCacheSize",
                aa.getMergeMWLCacheSize(), bb.getMergeMWLCacheSize(), 10);
        LdapUtils.storeDiff(ldapObj, mods, "dcmStoreUpdateDBMaxRetries",
                aa.getStoreUpdateDBMaxRetries(), bb.getStoreUpdateDBMaxRetries(), 1);
        LdapUtils.storeDiff(ldapObj, mods, "dcmStoreUpdateDBMaxRetryDelay",
                aa.getStoreUpdateDBMaxRetryDelay(), bb.getStoreUpdateDBMaxRetryDelay(), 1000);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmAllowRejectionForDataRetentionPolicyExpired",
                aa.getAllowRejectionForDataRetentionPolicyExpired(), bb.getAllowRejectionForDataRetentionPolicyExpired(),
                AllowRejectionForDataRetentionPolicyExpired.STUDY_RETENTION_POLICY);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmAcceptMissingPatientID",
                aa.getAcceptMissingPatientID(), bb.getAcceptMissingPatientID(), AcceptMissingPatientID.CREATE);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmAllowDeleteStudyPermanently",
                aa.getAllowDeleteStudyPermanently(), bb.getAllowDeleteStudyPermanently(),
                AllowDeleteStudyPermanently.REJECTED);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmStorePermissionServiceExpirationDatePattern",
                aa.getStorePermissionServiceExpirationDatePattern(), bb.getStorePermissionServiceExpirationDatePattern(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmShowPatientInfoInSystemLog",
                aa.getShowPatientInfoInSystemLog(), bb.getShowPatientInfoInSystemLog(), ShowPatientInfo.PLAIN_TEXT);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmShowPatientInfoInAuditLog",
                aa.getShowPatientInfoInAuditLog(), bb.getShowPatientInfoInAuditLog(), ShowPatientInfo.PLAIN_TEXT);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmPurgeStgCmtCompletedDelay",
                aa.getPurgeStgCmtCompletedDelay(), bb.getPurgeStgCmtCompletedDelay(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmPurgeStgCmtPollingInterval",
                aa.getPurgeStgCmtPollingInterval(), bb.getPurgeStgCmtPollingInterval(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmDefaultCharacterSet",
                aa.getDefaultCharacterSet(), bb.getDefaultCharacterSet(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmStorePermissionServiceErrorCommentPattern",
                aa.getStorePermissionServiceErrorCommentPattern(), bb.getStorePermissionServiceErrorCommentPattern(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmStorePermissionServiceErrorCodePattern",
                aa.getStorePermissionServiceErrorCodePattern(), bb.getStorePermissionServiceErrorCodePattern(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmRetrieveAET", aa.getRetrieveAETitles(), bb.getRetrieveAETitles());
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmExternalRetrieveAEDestination",
                aa.getExternalRetrieveAEDestination(), bb.getExternalRetrieveAEDestination(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmXDSiImagingDocumentSourceAETitle",
                aa.getXDSiImagingDocumentSourceAETitle(), bb.getXDSiImagingDocumentSourceAETitle(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmRemapRetrieveURL",
                aa.getRemapRetrieveURL(), bb.getRemapRetrieveURL(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmValidateCallingAEHostname",
                aa.isValidateCallingAEHostname(), bb.isValidateCallingAEHostname(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "hl7PSUSendingApplication",
                aa.getHl7PSUSendingApplication(), bb.getHl7PSUSendingApplication(), null);
        LdapUtils.storeDiff(ldapObj, mods, "hl7PSUReceivingApplication",
                aa.getHl7PSUReceivingApplications(), bb.getHl7PSUReceivingApplications());
        LdapUtils.storeDiffObject(ldapObj, mods, "hl7PSUDelay", aa.getHl7PSUDelay(), bb.getHl7PSUDelay(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "hl7PSUTimeout", aa.getHl7PSUTimeout(), bb.getHl7PSUTimeout(), null);
        LdapUtils.storeDiff(ldapObj, mods, "hl7PSUOnTimeout", aa.isHl7PSUOnTimeout(), bb.isHl7PSUOnTimeout(), false);
        LdapUtils.storeDiffObject(ldapObj, mods, "hl7PSUTaskPollingInterval",
                aa.getHl7PSUTaskPollingInterval(), bb.getHl7PSUTaskPollingInterval(), null);
        LdapUtils.storeDiff(ldapObj, mods, "hl7PSUTaskFetchSize",
                aa.getHl7PSUTaskFetchSize(), bb.getHl7PSUTaskFetchSize(), 100);
        LdapUtils.storeDiff(ldapObj, mods, "hl7PSUMWL", aa.isHl7PSUMWL(), bb.isHl7PSUMWL(), false);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmAcceptConflictingPatientID",
                aa.getAcceptConflictingPatientID(), bb.getAcceptConflictingPatientID(),
                AcceptConflictingPatientID.MERGED);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmAuditRecordRepositoryURL",
                aa.getAuditRecordRepositoryURL(), bb.getAuditRecordRepositoryURL(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmElasticSearchURL",
                aa.getElasticSearchURL(), bb.getElasticSearchURL(), null);
        LdapUtils.storeDiffObject(ldapObj, mods,"dcmAudit2JsonFhirTemplateURI",
                aa.getAudit2JsonFhirTemplateURI(), bb.getAudit2JsonFhirTemplateURI(), null);
        LdapUtils.storeDiffObject(ldapObj, mods,"dcmAudit2XmlFhirTemplateURI",
                aa.getAudit2XmlFhirTemplateURI(), bb.getAudit2XmlFhirTemplateURI(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmCopyMoveUpdatePolicy",
                aa.getCopyMoveUpdatePolicy(), bb.getCopyMoveUpdatePolicy(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmLinkMWLEntryUpdatePolicy",
                aa.getLinkMWLEntryUpdatePolicy(), bb.getLinkMWLEntryUpdatePolicy(), null);
        LdapUtils.storeDiff(ldapObj, mods, "hl7TrackChangedPatientID",
                aa.isHl7TrackChangedPatientID(), bb.isHl7TrackChangedPatientID(), true);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmInvokeImageDisplayPatientURL",
                aa.getInvokeImageDisplayPatientURL(), bb.getInvokeImageDisplayPatientURL(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmInvokeImageDisplayStudyURL",
                aa.getInvokeImageDisplayStudyURL(), bb.getInvokeImageDisplayStudyURL(), null);
        LdapUtils.storeDiff(ldapObj, mods, "hl7ADTReceivingApplication",
                aa.getHl7ADTReceivingApplication(), bb.getHl7ADTReceivingApplication());
        LdapUtils.storeDiffObject(ldapObj, mods, "hl7ADTSendingApplication",
                aa.getHl7ADTSendingApplication(), bb.getHl7ADTSendingApplication(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "hl7ScheduledProtocolCodeInOrder",
                aa.getHl7ScheduledProtocolCodeInOrder(), bb.getHl7ScheduledProtocolCodeInOrder(),
                ScheduledProtocolCodeInOrder.OBR_4_4);
        LdapUtils.storeDiffObject(ldapObj, mods, "hl7ScheduledStationAETInOrder",
                aa.getHl7ScheduledStationAETInOrder(), bb.getHl7ScheduledStationAETInOrder(), null);
        LdapUtils.storeDiff(ldapObj, mods, "hl7NoPatientCreateMessageType",
                aa.getHl7NoPatientCreateMessageTypes(), bb.getHl7NoPatientCreateMessageTypes());
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmAuditUnknownStudyInstanceUID",
                aa.getAuditUnknownStudyInstanceUID(), bb.getAuditUnknownStudyInstanceUID(),
                ArchiveDeviceExtension.AUDIT_UNKNOWN_STUDY_INSTANCE_UID);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmAuditUnknownPatientID",
                aa.getAuditUnknownPatientID(), bb.getAuditUnknownPatientID(),
                ArchiveDeviceExtension.AUDIT_UNKNOWN_PATIENT_ID);
        LdapUtils.storeDiff(ldapObj, mods, "dcmAuditSoftwareConfigurationVerbose",
                aa.isAuditSoftwareConfigurationVerbose(), bb.isAuditSoftwareConfigurationVerbose(), false);
        LdapUtils.storeDiff(ldapObj, mods, "hl7UseNullValue",
                aa.isHl7UseNullValue(), bb.isHl7UseNullValue(), false);
        if (remove)
            mods.add(new ModificationItem(DirContext.REMOVE_ATTRIBUTE,
                    LdapUtils.attr("objectClass", "dcmArchiveDevice")));
    }

    @Override
    protected void storeChilds(ConfigurationChanges diffs, String deviceDN, Device device)
            throws NamingException, ConfigurationException {
        ArchiveDeviceExtension arcDev = device
                .getDeviceExtension(ArchiveDeviceExtension.class);
        if (arcDev == null)
            return;

        storeAttributeFilter(diffs, deviceDN, arcDev);
        storeStorageDescriptors(diffs, deviceDN, arcDev);
        storeQueueDescriptors(diffs, deviceDN, arcDev);
        storeExporterDescriptors(diffs, deviceDN, arcDev);
        storeExportRules(diffs, arcDev.getExportRules(), deviceDN);
        storeCompressionRules(diffs, arcDev.getCompressionRules(), deviceDN);
        storeStoreAccessControlIDRules(diffs, arcDev.getStoreAccessControlIDRules(), deviceDN);
        storeAttributeCoercions(diffs, arcDev.getAttributeCoercions(), deviceDN);
        storeQueryRetrieveViews(diffs, deviceDN, arcDev);
        storeRejectNotes(diffs, deviceDN, arcDev);
        storeStudyRetentionPolicies(diffs, arcDev.getStudyRetentionPolicies(), deviceDN);
        storeIDGenerators(diffs, deviceDN, arcDev);
        storeHL7ForwardRules(diffs, arcDev.getHL7ForwardRules(), deviceDN, config);
        storeRSForwardRules(diffs, arcDev.getRSForwardRules(), deviceDN);
        storeAttributeSet(diffs, deviceDN, arcDev);
        storeScheduledStations(diffs, arcDev.getHL7OrderScheduledStations(), deviceDN, config);
        storeHL7OrderSPSStatus(diffs, arcDev.getHL7OrderSPSStatuses(), deviceDN, config);
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
        loadAttributeCoercions(arcdev.getAttributeCoercions(), deviceDN, device);
        loadQueryRetrieveViews(arcdev, deviceDN);
        loadRejectNotes(arcdev, deviceDN);
        loadStudyRetentionPolicies(arcdev.getStudyRetentionPolicies(), deviceDN);
        loadIDGenerators(arcdev, deviceDN);
        loadHL7ForwardRules(arcdev.getHL7ForwardRules(), deviceDN, config);
        loadRSForwardRules(arcdev.getRSForwardRules(), deviceDN);
        loadAttributeSet(arcdev, deviceDN);
        loadScheduledStations(arcdev.getHL7OrderScheduledStations(), deviceDN, config, device);
        loadHL7OrderSPSStatus(arcdev.getHL7OrderSPSStatuses(), deviceDN, config);
    }

    @Override
    protected void mergeChilds(ConfigurationChanges diffs, Device prev, Device device, String deviceDN)
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

        mergeAttributeFilters(diffs, aa, bb, deviceDN);
        mergeStorageDescriptors(diffs, aa, bb, deviceDN);
        mergeQueueDescriptors(diffs, aa, bb, deviceDN);
        mergeExportDescriptors(diffs, aa, bb, deviceDN);
        mergeExportRules(diffs, aa.getExportRules(), bb.getExportRules(), deviceDN);
        mergeCompressionRules(diffs, aa.getCompressionRules(), bb.getCompressionRules(), deviceDN);
        mergeStoreAccessControlIDRules(diffs, aa.getStoreAccessControlIDRules(), bb.getStoreAccessControlIDRules(), deviceDN);
        mergeAttributeCoercions(diffs, aa.getAttributeCoercions(), bb.getAttributeCoercions(), deviceDN);
        mergeQueryRetrieveViews(diffs, aa, bb, deviceDN);
        mergeRejectNotes(diffs, aa, bb, deviceDN);
        mergeStudyRetentionPolicies(diffs, aa.getStudyRetentionPolicies(), bb.getStudyRetentionPolicies(), deviceDN);
        mergeIDGenerators(diffs, aa, bb, deviceDN);
        mergeHL7ForwardRules(diffs, aa.getHL7ForwardRules(), bb.getHL7ForwardRules(), deviceDN, config);
        mergeRSForwardRules(diffs, aa.getRSForwardRules(), bb.getRSForwardRules(), deviceDN);
        mergeAttributeSet(diffs, aa, bb, deviceDN);
        mergeScheduledStations(diffs, aa.getHL7OrderScheduledStations(), bb.getHL7OrderScheduledStations(), deviceDN, config);
        mergeHL7OrderSPSStatus(diffs, aa.getHL7OrderSPSStatuses(), bb.getHL7OrderSPSStatuses(), deviceDN, config);
    }

    @Override
    protected void storeTo(ConfigurationChanges.ModifiedObject ldapObj, ApplicationEntity ae, Attributes attrs) {
        ArchiveAEExtension ext = ae.getAEExtension(ArchiveAEExtension.class);
        if (ext == null)
            return;

        attrs.get("objectclass").add("dcmArchiveNetworkAE");
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmObjectStorageID", ext.getObjectStorageIDs());
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmObjectStorageCount", ext.getObjectStorageCount(), 1);
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmMetadataStorageID", ext.getMetadataStorageIDs());
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmSeriesMetadataDelay", ext.getSeriesMetadataDelay(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmPurgeInstanceRecordsDelay", ext.getPurgeInstanceRecordsDelay(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmStoreAccessControlID", ext.getStoreAccessControlID(), null);
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmAccessControlID", ext.getAccessControlIDs());
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmOverwritePolicy", ext.getOverwritePolicy(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmBulkDataSpoolDirectory", ext.getBulkDataSpoolDirectory(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmQueryRetrieveViewID", ext.getQueryRetrieveViewID(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmPersonNameComponentOrderInsensitiveMatching",
                ext.getPersonNameComponentOrderInsensitiveMatching(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmSendPendingCGet", ext.getSendPendingCGet(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmSendPendingCMoveInterval", ext.getSendPendingCMoveInterval(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmSpanningCFindSCP", ext.getSpanningCFindSCP(), null);
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmSpanningCFindSCPRetrieveAET", ext.getSpanningCFindSCPRetrieveAETitles());
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmSpanningCFindSCPPolicy", ext.getSpanningCFindSCPPolicy(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmFallbackCMoveSCP", ext.getFallbackCMoveSCP(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmFallbackCMoveSCPDestination", ext.getFallbackCMoveSCPDestination(), null);
        LdapUtils.storeNotNull(ldapObj, attrs, "dcmFallbackCMoveSCPRetries", ext.getFallbackCMoveSCPRetries());
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmFallbackCMoveSCPLeadingCFindSCP", ext.getFallbackCMoveSCPLeadingCFindSCP(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmAltCMoveSCP", ext.getAlternativeCMoveSCP(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmWadoSR2HtmlTemplateURI", ext.getWadoSR2HtmlTemplateURI(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmWadoSR2TextTemplateURI", ext.getWadoSR2TextTemplateURI(), null);
        LdapUtils.storeNotNull(ldapObj, attrs, "dcmQueryMaxNumberOfResults", ext.getQueryMaxNumberOfResults());
        LdapUtils.storeNotNull(ldapObj, attrs, "dcmQidoMaxNumberOfResults", ext.getQidoMaxNumberOfResults());
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmFwdMppsDestination", ext.getMppsForwardDestinations());
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmIanDestination", ext.getIanDestinations());
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmIanDelay", ext.getIanDelay(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmIanTimeout", ext.getIanTimeout(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmIanOnTimeout", ext.getIanOnTimeout(), null);
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmHideSPSWithStatusFromMWL", ext.getHideSPSWithStatusFromMWL());
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmFallbackCMoveSCPStudyOlderThan", ext.getFallbackCMoveSCPStudyOlderThan(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmStorePermissionServiceURL", ext.getStorePermissionServiceURL(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmStorePermissionServiceResponsePattern", ext.getStorePermissionServiceResponsePattern(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmAllowRejectionForDataRetentionPolicyExpired", ext.getAllowRejectionForDataRetentionPolicyExpired(), null);
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmAcceptedUserRole", ext.getAcceptedUserRoles());
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmAcceptMissingPatientID", ext.getAcceptMissingPatientID(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmAllowDeleteStudyPermanently", ext.getAllowDeleteStudyPermanently(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmStorePermissionServiceExpirationDatePattern", ext.getStorePermissionServiceExpirationDatePattern(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmDefaultCharacterSet", ext.getDefaultCharacterSet(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmStorePermissionServiceErrorCommentPattern", ext.getStorePermissionServiceErrorCommentPattern(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmStorePermissionServiceErrorCodePattern", ext.getStorePermissionServiceErrorCodePattern(), null);
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmRetrieveAET", ext.getRetrieveAETitles());
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmExternalRetrieveAEDestination", ext.getExternalRetrieveAEDestination(), null);
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmAcceptedMoveDestination", ext.getAcceptedMoveDestinations());
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmValidateCallingAEHostname", ext.getValidateCallingAEHostname(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "hl7PSUSendingApplication", ext.getHl7PSUSendingApplication(), null);
        LdapUtils.storeNotEmpty(ldapObj, attrs, "hl7PSUReceivingApplication", ext.getHl7PSUReceivingApplications());
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "hl7PSUDelay", ext.getHl7PSUDelay(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "hl7PSUTimeout", ext.getHl7PSUTimeout(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "hl7PSUOnTimeout", ext.getHl7PSUOnTimeout(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "hl7PSUMWL", ext.getHl7PSUMWL(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmAcceptConflictingPatientID",
                ext.getAcceptConflictingPatientID(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmCopyMoveUpdatePolicy", ext.getCopyMoveUpdatePolicy(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmLinkMWLEntryUpdatePolicy", ext.getLinkMWLEntryUpdatePolicy(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmInvokeImageDisplayPatientURL", ext.getInvokeImageDisplayPatientURL(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmInvokeImageDisplayStudyURL", ext.getInvokeImageDisplayStudyURL(), null);
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
        ext.setSpanningCFindSCP(LdapUtils.stringValue(attrs.get("dcmSpanningCFindSCP"), null));
        ext.setSpanningCFindSCPRetrieveAETitles(LdapUtils.stringArray(attrs.get("dcmSpanningCFindSCPRetrieveAET")));
        ext.setSpanningCFindSCPPolicy(LdapUtils.enumValue(
                SpanningCFindSCPPolicy.class, attrs.get("dcmSpanningCFindSCPPolicy"), null));
        ext.setFallbackCMoveSCP(LdapUtils.stringValue(attrs.get("dcmFallbackCMoveSCP"), null));
        ext.setFallbackCMoveSCPDestination(LdapUtils.stringValue(attrs.get("dcmFallbackCMoveSCPDestination"), null));
        ext.setFallbackCMoveSCPRetries(LdapUtils.intValue(attrs.get("dcmFallbackCMoveSCPRetries"), null));
        ext.setFallbackCMoveSCPLeadingCFindSCP(LdapUtils.stringValue(attrs.get("dcmFallbackCMoveSCPLeadingCFindSCP"), null));
        ext.setAlternativeCMoveSCP(LdapUtils.stringValue(attrs.get("dcmAltCMoveSCP"), null));
        ext.setWadoSR2HtmlTemplateURI(LdapUtils.stringValue(attrs.get("dcmWadoSR2HtmlTemplateURI"), null));
        ext.setWadoSR2TextTemplateURI(LdapUtils.stringValue(attrs.get("dcmWadoSR2TextTemplateURI"), null));
        ext.setQueryMaxNumberOfResults(LdapUtils.intValue(attrs.get("dcmQueryMaxNumberOfResults"), null));
        ext.setQidoMaxNumberOfResults(LdapUtils.intValue(attrs.get("dcmQidoMaxNumberOfResults"), null));
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
        ext.setAllowDeleteStudyPermanently(
                LdapUtils.enumValue(AllowDeleteStudyPermanently.class, attrs.get("dcmAllowDeleteStudyPermanently"), null));
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
        ext.setLinkMWLEntryUpdatePolicy(LdapUtils.enumValue(org.dcm4che3.data.Attributes.UpdatePolicy.class, attrs.get("dcmLinkMWLEntryUpdatePolicy"), null));
        ext.setInvokeImageDisplayPatientURL(LdapUtils.stringValue(attrs.get("dcmInvokeImageDisplayPatientURL"), null));
        ext.setInvokeImageDisplayStudyURL(LdapUtils.stringValue(attrs.get("dcmInvokeImageDisplayStudyURL"), null));
    }

    @Override
    protected void storeDiffs(ConfigurationChanges.ModifiedObject ldapObj, ApplicationEntity prev, ApplicationEntity ae, List<ModificationItem> mods) {
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
        LdapUtils.storeDiff(ldapObj, mods, "dcmObjectStorageID", aa.getObjectStorageIDs(), bb.getObjectStorageIDs());
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmObjectStorageCount", aa.getObjectStorageCount(), bb.getObjectStorageCount(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmMetadataStorageID", aa.getMetadataStorageIDs(), bb.getMetadataStorageIDs());
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmSeriesMetadataDelay",
                aa.getSeriesMetadataDelay(),
                bb.getSeriesMetadataDelay(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmPurgeInstanceRecordsDelay",
                aa.getPurgeInstanceRecordsDelay(),
                bb.getPurgeInstanceRecordsDelay(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmStoreAccessControlID", aa.getStoreAccessControlID(), bb.getStoreAccessControlID(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmAccessControlID", aa.getAccessControlIDs(), bb.getAccessControlIDs());
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmOverwritePolicy", aa.getOverwritePolicy(), bb.getOverwritePolicy(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmBulkDataSpoolDirectory",
                aa.getBulkDataSpoolDirectory(), bb.getBulkDataSpoolDirectory(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmQueryRetrieveViewID", aa.getQueryRetrieveViewID(), bb.getQueryRetrieveViewID(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmPersonNameComponentOrderInsensitiveMatching",
                aa.getPersonNameComponentOrderInsensitiveMatching(),
                bb.getPersonNameComponentOrderInsensitiveMatching(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmSendPendingCGet", aa.getSendPendingCGet(), bb.getSendPendingCGet(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmSendPendingCMoveInterval",
                aa.getSendPendingCMoveInterval(), bb.getSendPendingCMoveInterval(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmSpanningCFindSCP",
                aa.getSpanningCFindSCP(), bb.getSpanningCFindSCP(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmSpanningCFindSCPRetrieveAET",
                aa.getSpanningCFindSCPRetrieveAETitles(), bb.getSpanningCFindSCPRetrieveAETitles());
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmSpanningCFindSCPPolicy",
                aa.getSpanningCFindSCPPolicy(), bb.getSpanningCFindSCPPolicy(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmFallbackCMoveSCP", aa.getFallbackCMoveSCP(), bb.getFallbackCMoveSCP(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmFallbackCMoveSCPDestination",
                aa.getFallbackCMoveSCPDestination(), bb.getFallbackCMoveSCPDestination(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmFallbackCMoveSCPRetries",
                aa.getFallbackCMoveSCPRetries(), bb.getFallbackCMoveSCPRetries(),  null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmFallbackCMoveSCPLeadingCFindSCP",
                aa.getFallbackCMoveSCPLeadingCFindSCP(), bb.getFallbackCMoveSCPLeadingCFindSCP(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmAltCMoveSCP", aa.getAlternativeCMoveSCP(), bb.getAlternativeCMoveSCP(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmWadoSR2HtmlTemplateURI",
                aa.getWadoSR2HtmlTemplateURI(), bb.getWadoSR2HtmlTemplateURI(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmWadoSR2TextTemplateURI",
                aa.getWadoSR2TextTemplateURI(), bb.getWadoSR2TextTemplateURI(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmQueryMaxNumberOfResults",
                aa.getQueryMaxNumberOfResults(), bb.getQueryMaxNumberOfResults(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmQidoMaxNumberOfResults",
                aa.getQidoMaxNumberOfResults(), bb.getQidoMaxNumberOfResults(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmFwdMppsDestination",
                aa.getMppsForwardDestinations(), bb.getMppsForwardDestinations());
        LdapUtils.storeDiff(ldapObj, mods, "dcmIanDestination", aa.getIanDestinations(), bb.getIanDestinations());
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmIanDelay", aa.getIanDelay(), bb.getIanDelay(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmIanTimeout", aa.getIanTimeout(), bb.getIanTimeout(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmIanOnTimeout", aa.getIanOnTimeout(), bb.getIanOnTimeout(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmHideSPSWithStatusFromMWL", aa.getHideSPSWithStatusFromMWL(), bb.getHideSPSWithStatusFromMWL());
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmFallbackCMoveSCPStudyOlderThan",
                aa.getFallbackCMoveSCPStudyOlderThan(), bb.getFallbackCMoveSCPStudyOlderThan(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmStorePermissionServiceURL",
                aa.getStorePermissionServiceURL(), bb.getStorePermissionServiceURL(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmStorePermissionServiceResponsePattern",
                aa.getStorePermissionServiceResponsePattern(), bb.getStorePermissionServiceResponsePattern(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmAllowRejectionForDataRetentionPolicyExpired",
                aa.getAllowRejectionForDataRetentionPolicyExpired(), bb.getAllowRejectionForDataRetentionPolicyExpired(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmAcceptedUserRole", aa.getAcceptedUserRoles(), bb.getAcceptedUserRoles());
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmAcceptMissingPatientID", aa.getAcceptMissingPatientID(), bb.getAcceptMissingPatientID(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmAllowDeleteStudyPermanently", aa.getAllowDeleteStudyPermanently(), bb.getAllowDeleteStudyPermanently(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmStorePermissionServiceExpirationDatePattern",
                aa.getStorePermissionServiceExpirationDatePattern(), bb.getStorePermissionServiceExpirationDatePattern(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmDefaultCharacterSet", aa.getDefaultCharacterSet(), bb.getDefaultCharacterSet(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmStorePermissionServiceErrorCommentPattern",
                aa.getStorePermissionServiceErrorCommentPattern(), bb.getStorePermissionServiceErrorCommentPattern(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmStorePermissionServiceErrorCodePattern",
                aa.getStorePermissionServiceErrorCodePattern(), bb.getStorePermissionServiceErrorCodePattern(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmRetrieveAET", aa.getRetrieveAETitles(), bb.getRetrieveAETitles());
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmExternalRetrieveAEDestination",
                aa.getExternalRetrieveAEDestination(), bb.getExternalRetrieveAEDestination(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmAcceptedMoveDestination", aa.getAcceptedMoveDestinations(), bb.getAcceptedMoveDestinations());
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmValidateCallingAEHostname", aa.getValidateCallingAEHostname(), bb.getValidateCallingAEHostname(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "hl7PSUSendingApplication", aa.getHl7PSUSendingApplication(), bb.getHl7PSUSendingApplication(), null);
        LdapUtils.storeDiff(ldapObj, mods, "hl7PSUReceivingApplication", aa.getHl7PSUReceivingApplications(), bb.getHl7PSUReceivingApplications());
        LdapUtils.storeDiffObject(ldapObj, mods, "hl7PSUDelay", aa.getHl7PSUDelay(), bb.getHl7PSUDelay(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "hl7PSUTimeout", aa.getHl7PSUTimeout(), bb.getHl7PSUTimeout(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "hl7PSUOnTimeout", aa.getHl7PSUOnTimeout(), bb.getHl7PSUOnTimeout(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "hl7PSUMWL", aa.getHl7PSUMWL(), bb.getHl7PSUMWL(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmAcceptConflictingPatientID",
                aa.getAcceptConflictingPatientID(), bb.getAcceptConflictingPatientID(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmCopyMoveUpdatePolicy", aa.getCopyMoveUpdatePolicy(), bb.getCopyMoveUpdatePolicy(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmLinkMWLEntryUpdatePolicy", aa.getLinkMWLEntryUpdatePolicy(), bb.getLinkMWLEntryUpdatePolicy(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmInvokeImageDisplayPatientURL", aa.getInvokeImageDisplayPatientURL(), bb.getInvokeImageDisplayPatientURL(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmInvokeImageDisplayStudyURL", aa.getInvokeImageDisplayStudyURL(), bb.getInvokeImageDisplayStudyURL(), null);
        if (remove)
            mods.add(new ModificationItem(DirContext.REMOVE_ATTRIBUTE,
                    LdapUtils.attr("objectClass", "dcmArchiveNetworkAE")));
    }

    @Override
    protected void storeChilds(ConfigurationChanges diffs, String aeDN, ApplicationEntity ae) throws NamingException {
        ArchiveAEExtension aeExt = ae.getAEExtension(ArchiveAEExtension.class);
        if (aeExt == null)
            return;

        storeExportRules(diffs, aeExt.getExportRules(), aeDN);
        storeCompressionRules(diffs, aeExt.getCompressionRules(), aeDN);
        storeStoreAccessControlIDRules(diffs, aeExt.getStoreAccessControlIDRules(), aeDN);
        storeAttributeCoercions(diffs, aeExt.getAttributeCoercions(), aeDN);
        storeStudyRetentionPolicies(diffs, aeExt.getStudyRetentionPolicies(), aeDN);
        storeRSForwardRules(diffs, aeExt.getRSForwardRules(), aeDN);
    }

    @Override
    protected void loadChilds(ApplicationEntity ae, String aeDN) throws NamingException, ConfigurationException {
        ArchiveAEExtension aeExt = ae.getAEExtension(ArchiveAEExtension.class);
        if (aeExt == null)
            return;

        loadExportRules(aeExt.getExportRules(), aeDN);
        loadCompressionRules(aeExt.getCompressionRules(), aeDN);
        loadStoreAccessControlIDRules(aeExt.getStoreAccessControlIDRules(), aeDN);
        loadAttributeCoercions(aeExt.getAttributeCoercions(), aeDN, ae.getDevice());
        loadStudyRetentionPolicies(aeExt.getStudyRetentionPolicies(), aeDN);
        loadRSForwardRules(aeExt.getRSForwardRules(), aeDN);
    }

    @Override
    protected void mergeChilds(ConfigurationChanges diffs, ApplicationEntity prev, ApplicationEntity ae, String aeDN)
            throws NamingException {
        ArchiveAEExtension aa = prev.getAEExtension(ArchiveAEExtension.class);
        ArchiveAEExtension bb = ae.getAEExtension(ArchiveAEExtension.class);
        if (aa == null && bb == null)
            return;

        if (aa == null)
            aa = new ArchiveAEExtension();
        else if (bb == null)
            bb = new ArchiveAEExtension();

        mergeExportRules(diffs, aa.getExportRules(), bb.getExportRules(), aeDN);
        mergeCompressionRules(diffs, aa.getCompressionRules(), bb.getCompressionRules(), aeDN);
        mergeStoreAccessControlIDRules(diffs, aa.getStoreAccessControlIDRules(), bb.getStoreAccessControlIDRules(), aeDN);
        mergeAttributeCoercions(diffs, aa.getAttributeCoercions(), bb.getAttributeCoercions(), aeDN);
        mergeStudyRetentionPolicies(diffs, aa.getStudyRetentionPolicies(), bb.getStudyRetentionPolicies(), aeDN);
        mergeRSForwardRules(diffs, aa.getRSForwardRules(), bb.getRSForwardRules(), aeDN);
    }

    private void storeAttributeFilter(ConfigurationChanges diffs, String deviceDN, ArchiveDeviceExtension arcDev)
            throws NamingException {
        for (Map.Entry<Entity, AttributeFilter> entry : arcDev.getAttributeFilters().entrySet()) {
            String dn = LdapUtils.dnOf("dcmEntity", entry.getKey().name(), deviceDN);
            ConfigurationChanges.ModifiedObject ldapObj =
                    ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
            config.createSubcontext(dn, storeTo(ldapObj, entry.getValue(), entry.getKey(), new BasicAttributes(true)));
        }
    }

    private void storeAttributeSet(ConfigurationChanges diffs, String deviceDN, ArchiveDeviceExtension arcDev)
            throws NamingException {
        for (Map<String, AttributeSet> map : arcDev.getAttributeSet().values()) {
            for (AttributeSet attributeSet : map.values()) {
                String dn = LdapUtils.dnOf("dcmAttributeSetType", attributeSet.getType().name(),
                        "dcmAttributeSetID", attributeSet.getID(), deviceDN);
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
                config.createSubcontext(dn, storeTo(ldapObj, attributeSet, new BasicAttributes(true)));
            }
        }
    }

    private Attributes storeTo(ConfigurationChanges.ModifiedObject ldapObj, AttributeSet attributeSet, BasicAttributes attrs) {
        attrs.put("objectclass", "dcmAttributeSet");
        attrs.put("dcmAttributeSetType", attributeSet.getType().name());
        attrs.put("dcmAttributeSetID", attributeSet.getID());
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmAttributeSetTitle", attributeSet.getTitle(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dicomDescription", attributeSet.getDescription(), null);
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmAttributeSetNumber", attributeSet.getNumber(), 0);
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmProperty", toStrings(attributeSet.getProperties()));
        LdapUtils.storeNotDef(ldapObj, attrs, "dicomInstalled", attributeSet.isInstalled(), true);
        storeNotEmptyTags(ldapObj, attrs, "dcmTag", attributeSet.getSelection());
        return attrs;
    }

    private static Attributes storeTo(ConfigurationChanges.ModifiedObject ldapObj, AttributeFilter filter, Entity entity,  BasicAttributes attrs) {
        attrs.put("objectclass", "dcmAttributeFilter");
        attrs.put("dcmEntity", entity.name());
        storeNotEmptyTags(ldapObj, attrs, "dcmTag", filter.getSelection());
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmCustomAttribute1", filter.getCustomAttribute1(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmCustomAttribute2", filter.getCustomAttribute2(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmCustomAttribute3", filter.getCustomAttribute3(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmAttributeUpdatePolicy", filter.getAttributeUpdatePolicy(), null);
        return attrs;
    }

    private static Attributes storeTo(ConfigurationChanges.ModifiedObject ldapObj, HL7OrderSPSStatus hl7OrderSPSStatus, SPSStatus spsStatus, BasicAttributes attrs) {
        attrs.put("objectclass", "hl7OrderSPSStatus");
        attrs.put("dcmSPSStatus", spsStatus.name());
        LdapUtils.storeNotEmpty(ldapObj, attrs, "hl7OrderControlStatus", hl7OrderSPSStatus.getOrderControlStatusCodes());
        return attrs;
    }

    private static void storeNotEmptyTags(ConfigurationChanges.ModifiedObject ldapObj, Attributes attrs, String attrid, int[] vals) {
        if (vals != null && vals.length > 0) {
            attrs.put(tagsAttr(attrid, vals));
            if (ldapObj != null) {
                ConfigurationChanges.ModifiedAttribute attribute = new ConfigurationChanges.ModifiedAttribute(attrid);
                for (int val : vals)
                    attribute.addValue(val);
                ldapObj.add(attribute);
            }
        }
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

    private void loadAttributeSet(ArchiveDeviceExtension device, String deviceDN) throws NamingException {
        NamingEnumeration<SearchResult> ne = config.search(deviceDN, "(objectclass=dcmAttributeSet)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                AttributeSet attributeSet = new AttributeSet();
                attributeSet.setType(
                        LdapUtils.enumValue(AttributeSet.Type.class, attrs.get("dcmAttributeSetType"), null));
                attributeSet.setID(LdapUtils.stringValue(attrs.get("dcmAttributeSetID"), null));
                attributeSet.setTitle(LdapUtils.stringValue(attrs.get("dcmAttributeSetTitle"), null));
                attributeSet.setDescription(LdapUtils.stringValue(attrs.get("dicomDescription"), null));
                attributeSet.setNumber(LdapUtils.intValue(attrs.get("dcmAttributeSetNumber"), 0));
                attributeSet.setProperties(LdapUtils.stringArray(attrs.get("dcmProperty")));
                attributeSet.setInstalled(LdapUtils.booleanValue(attrs.get("dicomInstalled"), true));
                attributeSet.setSelection(tags(attrs.get("dcmTag")));
                device.addAttributeSet(attributeSet);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
    }

    static void loadHL7OrderSPSStatus(
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

    private void mergeAttributeFilters(ConfigurationChanges diffs, ArchiveDeviceExtension prev, ArchiveDeviceExtension arcDev,
                                       String deviceDN) throws NamingException {
        for (Entity entity : prev.getAttributeFilters().keySet())
            if (!arcDev.getAttributeFilters().containsKey(entity)) {
                String dn = LdapUtils.dnOf("dcmEntity", entity.name(), deviceDN);
                config.destroySubcontext(dn);
                ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.D);
            }
        
        for (Map.Entry<Entity, AttributeFilter> entry : arcDev.getAttributeFilters().entrySet()) {
            Entity entity = entry.getKey();
            String dn = LdapUtils.dnOf("dcmEntity", entity.name(), deviceDN);
            AttributeFilter prevFilter = prev.getAttributeFilters().get(entity);
            if (prevFilter == null) {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
                config.createSubcontext(dn,
                        storeTo(ConfigurationChanges.nullifyIfNotVerbose(diffs, ldapObj),
                                entry.getValue(), entity, new BasicAttributes(true)));
            } else {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.U);
                config.modifyAttributes(dn,
                        storeDiffs(ldapObj, prevFilter, entry.getValue(), new ArrayList<ModificationItem>()));
                ConfigurationChanges.removeLastIfEmpty(diffs, ldapObj);
            }
        }
    }

    private void mergeAttributeSet(ConfigurationChanges diffs, ArchiveDeviceExtension prev, ArchiveDeviceExtension arcDev,
                                   String deviceDN) throws NamingException {
        for (Map.Entry<AttributeSet.Type, Map<String, AttributeSet>> prevEntry : prev.getAttributeSet().entrySet()) {
            AttributeSet.Type type = prevEntry.getKey();
            Map<String, AttributeSet> map = arcDev.getAttributeSet(type);
            for (String name : prevEntry.getValue().keySet()) {
                if (!map.containsKey(name)) {
                    String dn = LdapUtils.dnOf("dcmAttributeSetType", type.name(),
                            "dcmAttributeSetID", name, deviceDN);
                    config.destroySubcontext(dn);
                    ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.D);
                }
            }
        }
        for (Map.Entry<AttributeSet.Type, Map<String, AttributeSet>> entry : arcDev.getAttributeSet().entrySet()) {
            Map<String, AttributeSet> prevMap = prev.getAttributeSet(entry.getKey());
            for (AttributeSet attributeSet : entry.getValue().values()) {
                String dn = LdapUtils.dnOf("dcmAttributeSetType", attributeSet.getType().name(),
                        "dcmAttributeSetID", attributeSet.getID(), deviceDN);
                AttributeSet prevAttributeSet = prevMap.get(attributeSet.getID());
                if (prevAttributeSet == null) {
                    ConfigurationChanges.ModifiedObject ldapObj =
                            ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
                    config.createSubcontext(dn,
                            storeTo(ConfigurationChanges.nullifyIfNotVerbose(diffs, ldapObj),
                                    attributeSet, new BasicAttributes(true)));
                } else {
                    ConfigurationChanges.ModifiedObject ldapObj =
                            ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.U);
                    config.modifyAttributes(dn,
                            storeDiffs(ldapObj, prevAttributeSet, attributeSet, new ArrayList<ModificationItem>()));
                    ConfigurationChanges.removeLastIfEmpty(diffs, ldapObj);
                }
            }
        }
    }

    static void mergeHL7OrderSPSStatus(
            ConfigurationChanges diffs, Map<SPSStatus, HL7OrderSPSStatus> prev, Map<SPSStatus, HL7OrderSPSStatus> hl7OrderSPSStatusMap, String deviceDN,
            LdapDicomConfiguration config) throws NamingException {
        for (SPSStatus spsStatus : prev.keySet())
            if (!hl7OrderSPSStatusMap.containsKey(spsStatus)) {
                String dn = LdapUtils.dnOf("dcmSPSStatus", spsStatus.toString(), deviceDN);
                config.destroySubcontext(dn);
                ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.D);
            }
        for (Map.Entry<SPSStatus, HL7OrderSPSStatus> entry : hl7OrderSPSStatusMap.entrySet()) {
            SPSStatus spsStatus = entry.getKey();
            String dn = LdapUtils.dnOf("dcmSPSStatus", spsStatus.toString(), deviceDN);
            HL7OrderSPSStatus prevHL7OrderSPSStatus = prev.get(spsStatus);
            if (prevHL7OrderSPSStatus == null) {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
                config.createSubcontext(dn,
                        storeTo(ConfigurationChanges.nullifyIfNotVerbose(diffs, ldapObj),
                                entry.getValue(), spsStatus, new BasicAttributes(true)));
            } else {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.U);
                config.modifyAttributes(dn, storeDiffs(ldapObj, prevHL7OrderSPSStatus, entry.getValue(), new ArrayList<ModificationItem>()));
                ConfigurationChanges.removeLastIfEmpty(diffs, ldapObj);
            }
        }
    }

    private List<ModificationItem> storeDiffs(ConfigurationChanges.ModifiedObject ldapObj, AttributeFilter prev, AttributeFilter filter,
                                              List<ModificationItem> mods) {
        storeDiffTags(mods, "dcmTag", prev.getSelection(), filter.getSelection());
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmCustomAttribute1",
                prev.getCustomAttribute1(), filter.getCustomAttribute1(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmCustomAttribute2",
                prev.getCustomAttribute2(), filter.getCustomAttribute2(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmCustomAttribute3",
                prev.getCustomAttribute3(), filter.getCustomAttribute3(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmAttributeUpdatePolicy",
                prev.getAttributeUpdatePolicy(), filter.getAttributeUpdatePolicy(), null);
        return mods;
    }

    private List<ModificationItem> storeDiffs(ConfigurationChanges.ModifiedObject ldapObj, AttributeSet prev, AttributeSet attributeSet,
                                              List<ModificationItem> mods) {
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmAttributeSetTitle",
                prev.getTitle(), attributeSet.getTitle(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dicomDescription",
                prev.getDescription(), attributeSet.getDescription(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmAttributeSetNumber",
                prev.getNumber(), attributeSet.getNumber(), 0);
        storeDiffProperties(ldapObj, mods, prev.getProperties(), attributeSet.getProperties());
        LdapUtils.storeDiff(ldapObj, mods, "dicomInstalled",
                prev.isInstalled(), attributeSet.isInstalled(), true);
        storeDiffTags(mods, "dcmTag", prev.getSelection(), attributeSet.getSelection());
        return mods;
    }

    private void storeDiffTags(List<ModificationItem> mods, String attrId, int[] prevs, int[] vals) {
        if (!Arrays.equals(prevs, vals))
            mods.add((vals != null && vals.length == 0)
                    ? new ModificationItem(DirContext.REMOVE_ATTRIBUTE, new BasicAttribute(attrId))
                    : new ModificationItem(DirContext.REPLACE_ATTRIBUTE, tagsAttr(attrId, vals)));
    }

    private void storeStorageDescriptors(ConfigurationChanges diffs, String deviceDN, ArchiveDeviceExtension arcDev) throws NamingException {
        for (StorageDescriptor descriptor : arcDev.getStorageDescriptors()) {
            String dn = LdapUtils.dnOf("dcmStorageID", descriptor.getStorageID(), deviceDN);
            ConfigurationChanges.ModifiedObject ldapObj =
                    ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
            config.createSubcontext(dn,
                    storeTo(ConfigurationChanges.nullifyIfNotVerbose(diffs, ldapObj),
                            descriptor, new BasicAttributes(true)));
        }
    }

    private Attributes storeTo(ConfigurationChanges.ModifiedObject ldapObj, StorageDescriptor descriptor, BasicAttributes attrs) {
        attrs.put("objectclass", "dcmStorage");
        attrs.put("dcmStorageID", descriptor.getStorageID());
        attrs.put("dcmURI", descriptor.getStorageURIStr());
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmDigestAlgorithm", descriptor.getDigestAlgorithm(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmInstanceAvailability",
                descriptor.getInstanceAvailability(), Availability.ONLINE);
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmReadOnly", descriptor.isReadOnly(), false);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmStorageThreshold", descriptor.getStorageThreshold(), null);
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmDeleterThreshold", descriptor.getDeleterThresholdsAsStrings());
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmProperty", toStrings(descriptor.getProperties()));
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmExternalRetrieveAET", descriptor.getExternalRetrieveAETitle(), null);
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

    private void mergeStorageDescriptors(ConfigurationChanges diffs, ArchiveDeviceExtension prev, ArchiveDeviceExtension arcDev, String deviceDN)
            throws NamingException {
        for (StorageDescriptor descriptor : prev.getStorageDescriptors()) {
            String storageID = descriptor.getStorageID();
            if (arcDev.getStorageDescriptor(storageID) == null) {
                String dn = LdapUtils.dnOf("dcmStorageID", storageID, deviceDN);
                config.destroySubcontext(dn);
                ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.D);
            }
        }
        for (StorageDescriptor descriptor : arcDev.getStorageDescriptors()) {
            String storageID = descriptor.getStorageID();
            String dn = LdapUtils.dnOf("dcmStorageID", storageID, deviceDN);
            StorageDescriptor prevDescriptor = prev.getStorageDescriptor(storageID);
            if (prevDescriptor == null) {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
                config.createSubcontext(dn,
                        storeTo(ConfigurationChanges.nullifyIfNotVerbose(diffs, ldapObj),
                                descriptor, new BasicAttributes(true)));
            } else {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.U);
                config.modifyAttributes(dn,
                        storeDiffs(ldapObj, prevDescriptor, descriptor, new ArrayList<>()));
                ConfigurationChanges.removeLastIfEmpty(diffs, ldapObj);
            }
        }
    }

    private List<ModificationItem> storeDiffs(ConfigurationChanges.ModifiedObject ldapObj, StorageDescriptor prev, StorageDescriptor desc,
                                              List<ModificationItem> mods) {
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmURI", prev.getStorageURIStr(), desc.getStorageURIStr(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmDigestAlgorithm", prev.getDigestAlgorithm(), desc.getDigestAlgorithm(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmInstanceAvailability",
                prev.getInstanceAvailability(), desc.getInstanceAvailability(), Availability.ONLINE);
        LdapUtils.storeDiff(ldapObj, mods, "dcmReadOnly", prev.isReadOnly(), desc.isReadOnly(), false);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmStorageThreshold", prev.getStorageThreshold(), desc.getStorageThreshold(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmDeleterThreshold",
                prev.getDeleterThresholdsAsStrings(), desc.getDeleterThresholdsAsStrings());
        storeDiffProperties(ldapObj, mods, prev.getProperties(), desc.getProperties());
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmExternalRetrieveAET", prev.getExternalRetrieveAETitle(), desc.getExternalRetrieveAETitle(), null);
        return mods;
    }

    private static void storeDiffProperties(ConfigurationChanges.ModifiedObject ldapObj, List<ModificationItem> mods, Map<String, ?> prevs, Map<String, ?> props) {
        if (!equalsProperties(prevs, props)) {
            mods.add(props.size() == 0
                    ? new ModificationItem(DirContext.REMOVE_ATTRIBUTE,
                        new BasicAttribute("dcmProperty"))
                    : new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
                        LdapUtils.attr("dcmProperty", toStrings(props))));
            if (ldapObj != null) {
                ConfigurationChanges.ModifiedAttribute attribute = new ConfigurationChanges.ModifiedAttribute("dcmProperty");
                for (String val : toStrings(props))
                    attribute.addValue(val);
                for (String prev : toStrings(prevs))
                    attribute.removeValue(prev);
                ldapObj.add(attribute);
            }
        }
    }

    private static boolean equalsProperties(Map<String, ?> prevs, Map<String, ?> props) {
        if (prevs == props)
            return true;

        if (prevs.size() != props.size())
            return false;

        for (Map.Entry<String, ?> prop : props.entrySet()) {
            Object value = prop.getValue();
            Object prevValue = prevs.get(prop.getKey());
            if (!(value == null
                    ? prevValue == null && prevs.containsKey(prop.getKey())
                    : prevValue != null && prevValue.toString().equals(value.toString())))
                return false;
        }
        return true;
    }

    private void storeQueueDescriptors(ConfigurationChanges diffs, String deviceDN, ArchiveDeviceExtension arcDev) throws NamingException {
        for (QueueDescriptor descriptor : arcDev.getQueueDescriptors()) {
            String dn = LdapUtils.dnOf("dcmQueueName", descriptor.getQueueName(), deviceDN);
            ConfigurationChanges.ModifiedObject ldapObj =
                    ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
            config.createSubcontext(dn, storeTo(ldapObj, descriptor, new BasicAttributes(true)));
        }
    }

    private Attributes storeTo(ConfigurationChanges.ModifiedObject ldapObj, QueueDescriptor descriptor, BasicAttributes attrs) {
        attrs.put("objectclass", "dcmQueue");
        attrs.put("dcmQueueName", descriptor.getQueueName());
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmJndiName", descriptor.getJndiName(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dicomDescription", descriptor.getDescription(), null);
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmMaxRetries", descriptor.getMaxRetries(), 0);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmRetryDelay",
                descriptor.getRetryDelay(), QueueDescriptor.DEFAULT_RETRY_DELAY);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmMaxRetryDelay", descriptor.getMaxRetryDelay(), null);
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmRetryDelayMultiplier", descriptor.getRetryDelayMultiplier(), 100);
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmRetryOnWarning", descriptor.isRetryOnWarning(), false);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmPurgeQueueMessageCompletedDelay", descriptor.getPurgeQueueMessageCompletedDelay(), null);
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

    private void mergeQueueDescriptors(ConfigurationChanges diffs, ArchiveDeviceExtension prev, ArchiveDeviceExtension arcDev, String deviceDN)
            throws NamingException {
        for (QueueDescriptor descriptor : prev.getQueueDescriptors()) {
            String queueName = descriptor.getQueueName();
            if (arcDev.getQueueDescriptor(queueName) == null) {
                String dn = LdapUtils.dnOf("dcmQueueName", queueName, deviceDN);
                config.destroySubcontext(dn);
                ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.D);
            }
        }
        for (QueueDescriptor descriptor : arcDev.getQueueDescriptors()) {
            String queueName = descriptor.getQueueName();
            String dn = LdapUtils.dnOf("dcmQueueName", queueName, deviceDN);
            QueueDescriptor prevDescriptor = prev.getQueueDescriptor(queueName);
            if (prevDescriptor == null) {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
                config.createSubcontext(dn,
                        storeTo(ConfigurationChanges.nullifyIfNotVerbose(diffs, ldapObj),
                                descriptor, new BasicAttributes(true)));
            } else {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.U);
                config.modifyAttributes(dn,
                        storeDiffs(ldapObj, prevDescriptor, descriptor, new ArrayList<ModificationItem>()));
                ConfigurationChanges.removeLastIfEmpty(diffs, ldapObj);
            }
        }
    }

    private List<ModificationItem> storeDiffs(ConfigurationChanges.ModifiedObject ldapObj, QueueDescriptor prev, QueueDescriptor desc,
                                              List<ModificationItem> mods) {
        LdapUtils.storeDiffObject(ldapObj, mods, "dicomDescription",
                prev.getDescription(), desc.getDescription(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmJndiName", prev.getJndiName(), desc.getJndiName(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmMaxRetries", prev.getMaxRetries(), desc.getMaxRetries(), 0);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmRetryDelay",
                prev.getRetryDelay(), desc.getRetryDelay(), QueueDescriptor.DEFAULT_RETRY_DELAY);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmMaxRetryDelay",
                prev.getMaxRetryDelay(), desc.getMaxRetryDelay(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmRetryDelayMultiplier",
                prev.getRetryDelayMultiplier(), desc.getRetryDelayMultiplier(), 100);
        LdapUtils.storeDiff(ldapObj, mods, "dcmRetryOnWarning", prev.isRetryOnWarning(), desc.isRetryOnWarning(), false);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmPurgeQueueMessageCompletedDelay",
                prev.getPurgeQueueMessageCompletedDelay(), desc.getPurgeQueueMessageCompletedDelay(), null);
        return mods;
    }

    private void storeExporterDescriptors(ConfigurationChanges diffs, String deviceDN, ArchiveDeviceExtension arcDev) throws NamingException {
        for (ExporterDescriptor descriptor : arcDev.getExporterDescriptors()) {
            String dn = LdapUtils.dnOf("dcmExporterID", descriptor.getExporterID(), deviceDN);
            ConfigurationChanges.ModifiedObject ldapObj =
                    ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
            config.createSubcontext(dn, storeTo(ldapObj, descriptor, new BasicAttributes(true)));
        }
    }

    private Attributes storeTo(ConfigurationChanges.ModifiedObject ldapObj, ExporterDescriptor descriptor, BasicAttributes attrs) {
        attrs.put("objectclass", "dcmExporter");
        attrs.put("dcmExporterID", descriptor.getExporterID());
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmURI", descriptor.getExportURI(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dicomDescription", descriptor.getDescription(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmQueueName", descriptor.getQueueName(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dicomAETitle", descriptor.getAETitle(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmStgCmtSCP", descriptor.getStgCmtSCPAETitle(), null);
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmIanDestination", descriptor.getIanDestinations());
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmRetrieveAET", descriptor.getRetrieveAETitles());
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmRetrieveLocationUID", descriptor.getRetrieveLocationUID(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmInstanceAvailability",
                descriptor.getInstanceAvailability(), Availability.ONLINE);
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmSchedule", descriptor.getSchedules());
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmProperty", toStrings(descriptor.getProperties()));
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

    private void mergeExportDescriptors(ConfigurationChanges diffs, ArchiveDeviceExtension prev, ArchiveDeviceExtension arcDev, String deviceDN)
            throws NamingException {
        for (ExporterDescriptor descriptor : prev.getExporterDescriptors()) {
            String exporterID = descriptor.getExporterID();
            if (arcDev.getExporterDescriptor(exporterID) == null) {
                String dn = LdapUtils.dnOf("dcmExporterID", exporterID, deviceDN);
                config.destroySubcontext(dn);
                ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.D);
            }
        }
        for (ExporterDescriptor descriptor : arcDev.getExporterDescriptors()) {
            String exporterID = descriptor.getExporterID();
            String dn = LdapUtils.dnOf("dcmExporterID", exporterID, deviceDN);
            ExporterDescriptor prevDescriptor = prev.getExporterDescriptor(exporterID);
            if (prevDescriptor == null) {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
                config.createSubcontext(dn,
                        storeTo(ConfigurationChanges.nullifyIfNotVerbose(diffs, ldapObj),
                                descriptor, new BasicAttributes(true)));
            } else {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.U);
                config.modifyAttributes(dn,
                        storeDiffs(ldapObj, prevDescriptor, descriptor, new ArrayList<ModificationItem>()));
                ConfigurationChanges.removeLastIfEmpty(diffs, ldapObj);
            }
        }
    }

    private List<ModificationItem> storeDiffs(ConfigurationChanges.ModifiedObject ldapObj, ExporterDescriptor prev, ExporterDescriptor desc,
                                              List<ModificationItem> mods) {
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmURI", prev.getExportURI().toString(), desc.getExportURI().toString(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dicomDescription", prev.getDescription(), desc.getDescription(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmQueueName", prev.getQueueName(), desc.getQueueName(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dicomAETitle", prev.getAETitle(), desc.getAETitle(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmStgCmtSCP", prev.getStgCmtSCPAETitle(), desc.getStgCmtSCPAETitle(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmIanDestination", prev.getIanDestinations(), desc.getIanDestinations());
        LdapUtils.storeDiff(ldapObj, mods, "dcmRetrieveAET", prev.getRetrieveAETitles(), desc.getRetrieveAETitles());
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmRetrieveLocationUID",
                prev.getRetrieveLocationUID(), desc.getRetrieveLocationUID(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmInstanceAvailability",
                prev.getInstanceAvailability(), desc.getInstanceAvailability(), Availability.ONLINE);
        LdapUtils.storeDiff(ldapObj, mods, "dcmSchedule", prev.getSchedules(), desc.getSchedules());
        storeDiffProperties(ldapObj, mods, prev.getProperties(), desc.getProperties());
        return mods;
    }

    private void storeExportRules(ConfigurationChanges diffs, Collection<ExportRule> exportRules, String parentDN) throws NamingException {
        for (ExportRule rule : exportRules) {
            String dn = LdapUtils.dnOf("cn", rule.getCommonName(), parentDN);
            ConfigurationChanges.ModifiedObject ldapObj =
                    ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
            config.createSubcontext(dn, storeTo(ldapObj, rule, new BasicAttributes(true)));
        }
    }

    private Attributes storeTo(ConfigurationChanges.ModifiedObject ldapObj, ExportRule rule, BasicAttributes attrs) {
        attrs.put("objectclass", "dcmExportRule");
        attrs.put("cn", rule.getCommonName());
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmSchedule", rule.getSchedules());
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmProperty", toStrings(rule.getConditions().getMap()));
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmExporterID", rule.getExporterIDs());
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmEntity", rule.getEntity(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmDuration", rule.getExportDelay(), null);
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmExportPreviousEntity", rule.isExportPreviousEntity(), false);
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

    private void mergeExportRules(ConfigurationChanges diffs, Collection<ExportRule> prevRules, Collection<ExportRule> rules, String parentDN)
            throws NamingException {
        for (ExportRule prevRule : prevRules) {
            String cn = prevRule.getCommonName();
            if (findExportRuleByCN(rules, cn) == null) {
                String dn = LdapUtils.dnOf("cn", cn, parentDN);
                config.destroySubcontext(dn);
                ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.D);
            }
        }
        for (ExportRule rule : rules) {
            String cn = rule.getCommonName();
            String dn = LdapUtils.dnOf("cn", cn, parentDN);
            ExportRule prevRule = findExportRuleByCN(prevRules, cn);
            if (prevRule == null) {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
                config.createSubcontext(dn,
                        storeTo(ConfigurationChanges.nullifyIfNotVerbose(diffs, ldapObj),
                                rule, new BasicAttributes(true)));
            } else {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.U);
                config.modifyAttributes(dn, storeDiffs(ldapObj, prevRule, rule, new ArrayList<ModificationItem>()));
                ConfigurationChanges.removeLastIfEmpty(diffs, ldapObj);
            }
        }
    }

    private List<ModificationItem> storeDiffs(ConfigurationChanges.ModifiedObject ldapObj, ExportRule prev, ExportRule rule, ArrayList<ModificationItem> mods) {
        LdapUtils.storeDiff(ldapObj, mods, "dcmSchedule", prev.getSchedules(), rule.getSchedules());
        storeDiffProperties(ldapObj, mods, prev.getConditions().getMap(), rule.getConditions().getMap());
        LdapUtils.storeDiff(ldapObj, mods, "dcmExporterID", prev.getExporterIDs(), rule.getExporterIDs());
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmEntity", prev.getEntity(), rule.getEntity(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmDuration", prev.getExportDelay(), rule.getExportDelay(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmExportPreviousEntity",
                prev.isExportPreviousEntity(), rule.isExportPreviousEntity(), false);
        return mods;
    }

    private ExportRule findExportRuleByCN(Collection<ExportRule> rules, String cn) {
        for (ExportRule rule : rules)
            if (rule.getCommonName().equals(cn))
                return rule;
        return null;
    }

    private void storeCompressionRules(ConfigurationChanges diffs, Collection<ArchiveCompressionRule> rules, String parentDN)
            throws NamingException {
        for (ArchiveCompressionRule rule : rules) {
            String dn = LdapUtils.dnOf("cn", rule.getCommonName(), parentDN);
            ConfigurationChanges.ModifiedObject ldapObj =
                    ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
            config.createSubcontext(dn, storeTo(ldapObj, rule, new BasicAttributes(true)));
        }
    }

    private void storeStudyRetentionPolicies(ConfigurationChanges diffs, Collection<StudyRetentionPolicy> policies, String parentDN)
            throws NamingException {
        for (StudyRetentionPolicy policy : policies) {
            String dn = LdapUtils.dnOf("cn", policy.getCommonName(), parentDN);
            ConfigurationChanges.ModifiedObject ldapObj =
                    ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
            config.createSubcontext(dn, storeTo(ldapObj, policy, new BasicAttributes(true)));
        }
    }

    private void storeStoreAccessControlIDRules(ConfigurationChanges diffs, Collection<StoreAccessControlIDRule> rules, String parentDN)
            throws NamingException {
        for (StoreAccessControlIDRule rule : rules) {
            String dn = LdapUtils.dnOf("cn", rule.getCommonName(), parentDN);
            ConfigurationChanges.ModifiedObject ldapObj =
                    ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
            config.createSubcontext(dn, storeTo(ldapObj, rule, new BasicAttributes(true)));
        }
    }


    static void storeHL7ForwardRules(ConfigurationChanges diffs,
            Collection<HL7ForwardRule> rules, String parentDN, LdapDicomConfiguration config)
            throws NamingException{
        for (HL7ForwardRule rule : rules) {
            String dn = LdapUtils.dnOf("cn", rule.getCommonName(), parentDN);
            ConfigurationChanges.ModifiedObject ldapObj =
                    ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
            config.createSubcontext(dn, storeTo(ldapObj, rule, new BasicAttributes(true)));
        }
    }

    static void storeScheduledStations(ConfigurationChanges diffs,
            Collection<HL7OrderScheduledStation> stations, String parentDN, LdapDicomConfiguration config)
            throws NamingException{
        for (HL7OrderScheduledStation station : stations) {
            String dn = LdapUtils.dnOf("cn", station.getCommonName(), parentDN);
            ConfigurationChanges.ModifiedObject ldapObj =
                    ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
            config.createSubcontext(dn, storeTo(ldapObj, station, new BasicAttributes(true), config));
        }
    }

    static void storeHL7OrderSPSStatus(ConfigurationChanges diffs,
            Map<SPSStatus, HL7OrderSPSStatus> hl7OrderSPSStatusMap, String parentDN, LdapDicomConfiguration config)
            throws NamingException {
        for (Map.Entry<SPSStatus, HL7OrderSPSStatus> entry : hl7OrderSPSStatusMap.entrySet()) {
            String dn = LdapUtils.dnOf("dcmSPSStatus", entry.getKey().toString(), parentDN);
            ConfigurationChanges.ModifiedObject ldapObj =
                    ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
            config.createSubcontext(dn, storeTo(ldapObj, entry.getValue(), entry.getKey(), new BasicAttributes(true)));
        }
    }

    private void storeRSForwardRules(ConfigurationChanges diffs, Collection<RSForwardRule> rules, String parentDN)
            throws NamingException {
        for (RSForwardRule rule : rules) {
            String dn = LdapUtils.dnOf("cn", rule.getCommonName(), parentDN);
            ConfigurationChanges.ModifiedObject ldapObj =
                    ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
            config.createSubcontext(dn, storeTo(ldapObj, rule, new BasicAttributes(true)));
        }
    }

    private Attributes storeTo(ConfigurationChanges.ModifiedObject ldapObj, ArchiveCompressionRule rule, BasicAttributes attrs) {
        attrs.put("objectclass", "dcmArchiveCompressionRule");
        attrs.put("cn", rule.getCommonName());
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmProperty", toStrings(rule.getConditions().getMap()));
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dicomTransferSyntax", rule.getTransferSyntax(), null);
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmImageWriteParam", rule.getImageWriteParams());
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmRulePriority", rule.getPriority(), 0);
        return attrs;
    }

    private Attributes storeTo(ConfigurationChanges.ModifiedObject ldapObj, StoreAccessControlIDRule rule, BasicAttributes attrs) {
        attrs.put("objectclass", "dcmStoreAccessControlIDRule");
        attrs.put("cn", rule.getCommonName());
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmProperty", toStrings(rule.getConditions().getMap()));
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmStoreAccessControlID", rule.getStoreAccessControlID(), null);
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmRulePriority", rule.getPriority(), 0);
        return attrs;
    }

    private Attributes storeTo(ConfigurationChanges.ModifiedObject ldapObj, StudyRetentionPolicy policy, BasicAttributes attrs) {
        attrs.put("objectclass", "dcmStudyRetentionPolicy");
        attrs.put("cn", policy.getCommonName());
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmProperty", toStrings(policy.getConditions().getMap()));
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmRetentionPeriod", policy.getRetentionPeriod(), null);
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmRulePriority", policy.getPriority(), 0);
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmExpireSeriesIndividually", policy.isExpireSeriesIndividually(), false);
        return attrs;
    }

    private static Attributes storeTo(ConfigurationChanges.ModifiedObject ldapObj, HL7ForwardRule rule, BasicAttributes attrs) {
        attrs.put("objectclass", "hl7ForwardRule");
        attrs.put("cn", rule.getCommonName());
        LdapUtils.storeNotEmpty(ldapObj, attrs, "hl7FwdApplicationName", rule.getDestinations());
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmProperty", toStrings(rule.getConditions().getMap()));
        return attrs;
    }

    private static Attributes storeTo(ConfigurationChanges.ModifiedObject ldapObj, HL7OrderScheduledStation station, BasicAttributes attrs, LdapDicomConfiguration config) {
        attrs.put("objectclass", "hl7OrderScheduledStation");
        attrs.put("cn", station.getCommonName());
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "hl7OrderScheduledStationDeviceReference",
                scheduledStationDeviceRef(station, config), null);
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmRulePriority", station.getPriority(), 0);
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmProperty", toStrings(station.getConditions().getMap()));
        return attrs;
    }

    private Attributes storeTo(ConfigurationChanges.ModifiedObject ldapObj, RSForwardRule rule, BasicAttributes attrs) {
        attrs.put("objectclass", "dcmRSForwardRule");
        attrs.put("cn", rule.getCommonName());
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmURI", rule.getBaseURI(), null);
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmRSOperation", rule.getRSOperations());
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

    static void loadHL7ForwardRules(
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

    static void loadScheduledStations(
            Collection<HL7OrderScheduledStation> stations, String parentDN, LdapDicomConfiguration config, Device device)
            throws NamingException, ConfigurationException {
        NamingEnumeration<SearchResult> ne = config.search(parentDN, "(objectclass=hl7OrderScheduledStation)");
        try {
            while (ne.hasMore()) {
                SearchResult sr = ne.next();
                Attributes attrs = sr.getAttributes();
                HL7OrderScheduledStation station = new HL7OrderScheduledStation(LdapUtils.stringValue(attrs.get("cn"), null));
                String scheduledStationDeviceRef = LdapUtils.stringValue(attrs.get("hl7OrderScheduledStationDeviceReference"), null);
                station.setDevice(parentDN.equals(scheduledStationDeviceRef)
                                    ? device
                                    : loadScheduledStation(scheduledStationDeviceRef, config));
                station.setPriority(LdapUtils.intValue(attrs.get("dcmRulePriority"), 0));
                station.setConditions(new HL7Conditions(LdapUtils.stringArray(attrs.get("dcmProperty"))));
                stations.add(station);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
    }

    private static Device loadScheduledStation(String scheduledStationDeviceRef, LdapDicomConfiguration config) {
        try {
            return config.loadDevice(scheduledStationDeviceRef);
        } catch (ConfigurationException e) {
            LOG.info("Failed to load Scheduled Station device "
                    + scheduledStationDeviceRef + " referenced by HL7 Order Scheduled Station", e);
            return null;
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
            ConfigurationChanges diffs, Collection<ArchiveCompressionRule> prevRules, Collection<ArchiveCompressionRule> rules, String parentDN)
            throws NamingException {
        for (ArchiveCompressionRule prevRule : prevRules) {
            String cn = prevRule.getCommonName();
            if (findCompressionRuleByCN(rules, cn) == null) {
                String dn = LdapUtils.dnOf("cn", cn, parentDN);
                config.destroySubcontext(dn);
                ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.D);
            }
        }
        for (ArchiveCompressionRule rule : rules) {
            String cn = rule.getCommonName();
            String dn = LdapUtils.dnOf("cn", cn, parentDN);
            ArchiveCompressionRule prevRule = findCompressionRuleByCN(prevRules, cn);
            if (prevRule == null) {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
                config.createSubcontext(dn,
                        storeTo(ConfigurationChanges.nullifyIfNotVerbose(diffs, ldapObj),
                                rule, new BasicAttributes(true)));
            } else {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.U);
                config.modifyAttributes(dn, storeDiffs(ldapObj, prevRule, rule, new ArrayList<ModificationItem>()));
                ConfigurationChanges.removeLastIfEmpty(diffs, ldapObj);
            }
        }
    }

    private void mergeStoreAccessControlIDRules(
            ConfigurationChanges diffs, Collection<StoreAccessControlIDRule> prevRules, Collection<StoreAccessControlIDRule> rules, String parentDN)
            throws NamingException {
        for (StoreAccessControlIDRule prevRule : prevRules) {
            String cn = prevRule.getCommonName();
            if (findStoreAccessControlIDRuleByCN(rules, cn) == null) {
                String dn = LdapUtils.dnOf("cn", cn, parentDN);
                config.destroySubcontext(dn);
                ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.D);
            }
        }
        for (StoreAccessControlIDRule rule : rules) {
            String cn = rule.getCommonName();
            String dn = LdapUtils.dnOf("cn", cn, parentDN);
            StoreAccessControlIDRule prevRule = findStoreAccessControlIDRuleByCN(prevRules, cn);
            if (prevRule == null) {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
                config.createSubcontext(dn,
                        storeTo(ConfigurationChanges.nullifyIfNotVerbose(diffs, ldapObj),
                                rule, new BasicAttributes(true)));
            } else {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.U);
                config.modifyAttributes(dn, storeDiffs(ldapObj, prevRule, rule, new ArrayList<ModificationItem>()));
                ConfigurationChanges.removeLastIfEmpty(diffs, ldapObj);
            }
        }
    }

    private void mergeStudyRetentionPolicies(
            ConfigurationChanges diffs, Collection<StudyRetentionPolicy> prevPolicies, Collection<StudyRetentionPolicy> policies, String parentDN)
            throws NamingException {
        for (StudyRetentionPolicy prevRule : prevPolicies) {
            String cn = prevRule.getCommonName();
            if (findStudyRetentionPolicyByCN(policies, cn) == null) {
                String dn = LdapUtils.dnOf("cn", cn, parentDN);
                config.destroySubcontext(dn);
                ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.D);
            }
        }
        for (StudyRetentionPolicy policy : policies) {
            String cn = policy.getCommonName();
            String dn = LdapUtils.dnOf("cn", cn, parentDN);
            StudyRetentionPolicy prevPolicy = findStudyRetentionPolicyByCN(prevPolicies, cn);
            if (prevPolicy == null) {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
                config.createSubcontext(dn,
                        storeTo(ConfigurationChanges.nullifyIfNotVerbose(diffs, ldapObj),
                                policy, new BasicAttributes(true)));
            } else {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.U);
                config.modifyAttributes(dn, storeDiffs(ldapObj, prevPolicy, policy, new ArrayList<ModificationItem>()));
                ConfigurationChanges.removeLastIfEmpty(diffs, ldapObj);
            }
        }
    }

    static void mergeHL7ForwardRules(ConfigurationChanges diffs, Collection<HL7ForwardRule> prevRules, Collection<HL7ForwardRule> rules,
                                               String parentDN, LdapDicomConfiguration config)
            throws NamingException {
        for (HL7ForwardRule prevRule : prevRules) {
            String cn = prevRule.getCommonName();
            if (findHL7ForwardRuleByCN(rules, cn) == null) {
                String dn = LdapUtils.dnOf("cn", cn, parentDN);
                config.destroySubcontext(dn);
                ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.D);
            }
        }
        for (HL7ForwardRule rule : rules) {
            String cn = rule.getCommonName();
            String dn = LdapUtils.dnOf("cn", cn, parentDN);
            HL7ForwardRule prevRule = findHL7ForwardRuleByCN(prevRules, cn);
            if (prevRule == null) {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
                config.createSubcontext(dn,
                        storeTo(ConfigurationChanges.nullifyIfNotVerbose(diffs, ldapObj),
                                rule, new BasicAttributes(true)));
             } else {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.U);
                config.modifyAttributes(dn, storeDiffs(ldapObj, prevRule, rule, new ArrayList<ModificationItem>()));
                ConfigurationChanges.removeLastIfEmpty(diffs, ldapObj);
            }
        }
    }

    static void mergeScheduledStations(ConfigurationChanges diffs, Collection<HL7OrderScheduledStation> prevStations, Collection<HL7OrderScheduledStation> stations,
                                                 String parentDN, LdapDicomConfiguration config)
            throws NamingException {
        for (HL7OrderScheduledStation prevRule : prevStations) {
            String cn = prevRule.getCommonName();
            if (findScheduledStationByCN(stations, cn) == null) {
                String dn = LdapUtils.dnOf("cn", cn, parentDN);
                config.destroySubcontext(dn);
                ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.D);
            }
        }
        for (HL7OrderScheduledStation station : stations) {
            String cn = station.getCommonName();
            String dn = LdapUtils.dnOf("cn", cn, parentDN);
            HL7OrderScheduledStation prevStation = findScheduledStationByCN(prevStations, cn);
            if (prevStation == null) {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
                config.createSubcontext(dn,
                        storeTo(ConfigurationChanges.nullifyIfNotVerbose(diffs, ldapObj),
                                station, new BasicAttributes(true), config));
            } else {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.U);
                config.modifyAttributes(dn, storeDiffs(ldapObj, prevStation, station, new ArrayList<ModificationItem>(), config));
                ConfigurationChanges.removeLastIfEmpty(diffs, ldapObj);
            }
        }
    }

    private void mergeAttributeCoercions(
            ConfigurationChanges diffs, Collection<ArchiveAttributeCoercion> prevCoercions,
            Collection<ArchiveAttributeCoercion> coercions,
            String parentDN) throws NamingException {
        for (ArchiveAttributeCoercion prev : prevCoercions) {
            String cn = prev.getCommonName();
            if (findAttributeCoercionByCN(coercions, cn) == null) {
                String dn = LdapUtils.dnOf("cn", cn, parentDN);
                config.destroySubcontext(dn);
                ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.D);
            }
        }
        for (ArchiveAttributeCoercion coercion : coercions) {
            String cn = coercion.getCommonName();
            String dn = LdapUtils.dnOf("cn", cn, parentDN);
            ArchiveAttributeCoercion prev = findAttributeCoercionByCN(prevCoercions, cn);
            if (prev == null) {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
                config.createSubcontext(dn,
                        storeTo(ConfigurationChanges.nullifyIfNotVerbose(diffs, ldapObj),
                                coercion, new BasicAttributes(true)));
            } else {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.U);
                config.modifyAttributes(dn, storeDiffs(ldapObj, prev, coercion, new ArrayList<ModificationItem>()));
                ConfigurationChanges.removeLastIfEmpty(diffs, ldapObj);
            }
        }
    }

    private void mergeRSForwardRules(
            ConfigurationChanges diffs, Collection<RSForwardRule> prevRules, Collection<RSForwardRule> rules, String parentDN)
            throws NamingException {
        for (RSForwardRule prevRule : prevRules) {
            String cn = prevRule.getCommonName();
            if (findRSForwardRuleByCN(rules, cn) == null) {
                String dn = LdapUtils.dnOf("cn", cn, parentDN);
                config.destroySubcontext(dn);
                ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.D);
            }
        }
        for (RSForwardRule rule : rules) {
            String cn = rule.getCommonName();
            String dn = LdapUtils.dnOf("cn", cn, parentDN);
            RSForwardRule prevRule = findRSForwardRuleByCN(prevRules, cn);
            if (prevRule == null) {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
                config.createSubcontext(dn,
                        storeTo(ConfigurationChanges.nullifyIfNotVerbose(diffs, ldapObj),
                                rule, new BasicAttributes(true)));
            } else {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.U);
                config.modifyAttributes(dn, storeDiffs(ldapObj, prevRule, rule, new ArrayList<ModificationItem>()));
                ConfigurationChanges.removeLastIfEmpty(diffs, ldapObj);
            }
        }
    }

    private List<ModificationItem> storeDiffs(
            ConfigurationChanges.ModifiedObject ldapObj, ArchiveCompressionRule prev, ArchiveCompressionRule rule, ArrayList<ModificationItem> mods) {
        storeDiffProperties(ldapObj, mods, prev.getConditions().getMap(), rule.getConditions().getMap());
        LdapUtils.storeDiffObject(ldapObj, mods, "dicomTransferSyntax", prev.getTransferSyntax(), rule.getTransferSyntax(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmImageWriteParam", prev.getImageWriteParams(), rule.getImageWriteParams());
        LdapUtils.storeDiff(ldapObj, mods, "dcmRulePriority", prev.getPriority(), rule.getPriority(), 0);
        return mods;
    }

    private List<ModificationItem> storeDiffs(
            ConfigurationChanges.ModifiedObject ldapObj, StoreAccessControlIDRule prev, StoreAccessControlIDRule rule, ArrayList<ModificationItem> mods) {
        storeDiffProperties(ldapObj, mods, prev.getConditions().getMap(), rule.getConditions().getMap());
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmStoreAccessControlID",
                prev.getStoreAccessControlID(), rule.getStoreAccessControlID(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmRulePriority", prev.getPriority(), rule.getPriority(), 0);
        return mods;
    }

    private List<ModificationItem> storeDiffs(
            ConfigurationChanges.ModifiedObject ldapObj, StudyRetentionPolicy prev, StudyRetentionPolicy policy, ArrayList<ModificationItem> mods) {
        storeDiffProperties(ldapObj, mods, prev.getConditions().getMap(), policy.getConditions().getMap());
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmRetentionPeriod", prev.getRetentionPeriod(), policy.getRetentionPeriod(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmRulePriority", prev.getPriority(), policy.getPriority(), 0);
        LdapUtils.storeDiff(ldapObj, mods, "dcmExpireSeriesIndividually", prev.isExpireSeriesIndividually(),
                policy.isExpireSeriesIndividually(), false);
        return mods;
    }

    private static List<ModificationItem> storeDiffs(
            ConfigurationChanges.ModifiedObject ldapObj, HL7ForwardRule prev, HL7ForwardRule rule, ArrayList<ModificationItem> mods) {
        storeDiffProperties(ldapObj, mods, prev.getConditions().getMap(), rule.getConditions().getMap());
        LdapUtils.storeDiff(ldapObj, mods, "hl7FwdApplicationName", prev.getDestinations(), rule.getDestinations());
        return mods;
    }

    private static List<ModificationItem> storeDiffs(ConfigurationChanges.ModifiedObject ldapObj, HL7OrderScheduledStation prev, HL7OrderScheduledStation station,
                                                     ArrayList<ModificationItem> mods, LdapDicomConfiguration config) {
        LdapUtils.storeDiffObject(ldapObj, mods, "hl7OrderScheduledStationDeviceReference",
                scheduledStationDeviceRef(prev, config),
                scheduledStationDeviceRef(station, config), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmRulePriority", prev.getPriority(), station.getPriority(), 0);
        return mods;
    }

    private static List<ModificationItem> storeDiffs(
            ConfigurationChanges.ModifiedObject ldapObj, HL7OrderSPSStatus prev, HL7OrderSPSStatus hl7OrderSPSStatus, ArrayList<ModificationItem> mods) {
        LdapUtils.storeDiff(ldapObj, mods, "hl7OrderControlStatus", prev.getOrderControlStatusCodes(), hl7OrderSPSStatus.getOrderControlStatusCodes());
        return mods;
    }

    private List<ModificationItem> storeDiffs(
            ConfigurationChanges.ModifiedObject ldapObj, RSForwardRule prev, RSForwardRule rule, ArrayList<ModificationItem> mods) {
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmURI", prev.getBaseURI(), rule.getBaseURI(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmRSOperation", prev.getRSOperations(), rule.getRSOperations());
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

    private void storeQueryRetrieveViews(ConfigurationChanges diffs, String deviceDN,
                                         ArchiveDeviceExtension arcDev) throws NamingException {
        for (QueryRetrieveView view : arcDev.getQueryRetrieveViews()) {
            String dn = LdapUtils.dnOf("dcmQueryRetrieveViewID", view.getViewID(), deviceDN);
            ConfigurationChanges.ModifiedObject ldapObj =
                    ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
            config.createSubcontext(dn, storeTo(ldapObj, view, new BasicAttributes(true)));
        }
    }

    private Attributes storeTo(ConfigurationChanges.ModifiedObject ldapObj, QueryRetrieveView qrView, BasicAttributes attrs) {
        attrs.put("objectclass", "dcmQueryRetrieveView");
        attrs.put("dcmQueryRetrieveViewID", qrView.getViewID());
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmShowInstancesRejectedByCode", qrView.getShowInstancesRejectedByCodes());
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmHideRejectionNoteWithCode", qrView.getHideRejectionNotesWithCodes());
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmHideNotRejectedInstances", qrView.isHideNotRejectedInstances(), false);
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

    private void mergeQueryRetrieveViews(ConfigurationChanges diffs, ArchiveDeviceExtension prev, ArchiveDeviceExtension arcDev, String deviceDN)
            throws NamingException {
        for (QueryRetrieveView entry : prev.getQueryRetrieveViews()) {
            String viewID = entry.getViewID();
            if (arcDev.getQueryRetrieveView(viewID) == null) {
                String dn = LdapUtils.dnOf("dcmQueryRetrieveViewID", viewID, deviceDN);
                config.destroySubcontext(dn);
                ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.D);
            }
        }
        for (QueryRetrieveView entryNew : arcDev.getQueryRetrieveViews()) {
            String viewID = entryNew.getViewID();
            String dn = LdapUtils.dnOf("dcmQueryRetrieveViewID", viewID, deviceDN);
            QueryRetrieveView entryOld = prev.getQueryRetrieveView(viewID);
            if (entryOld == null) {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
                config.createSubcontext(dn,
                        storeTo(ConfigurationChanges.nullifyIfNotVerbose(diffs, ldapObj),
                                entryNew, new BasicAttributes(true)));
            } else{
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.U);
                config.modifyAttributes(dn, storeDiffs(ldapObj, entryOld, entryNew, new ArrayList<ModificationItem>()));
                ConfigurationChanges.removeLastIfEmpty(diffs, ldapObj);
            }
        }
    }

    private List<ModificationItem> storeDiffs(
            ConfigurationChanges.ModifiedObject ldapObj, QueryRetrieveView prev, QueryRetrieveView view, ArrayList<ModificationItem> mods) {
        LdapUtils.storeDiff(ldapObj, mods, "dcmShowInstancesRejectedByCode",
                prev.getShowInstancesRejectedByCodes(),
                view.getShowInstancesRejectedByCodes());
        LdapUtils.storeDiff(ldapObj, mods, "dcmHideRejectionNoteWithCode",
                prev.getHideRejectionNotesWithCodes(),
                view.getHideRejectionNotesWithCodes());
        LdapUtils.storeDiff(ldapObj, mods, "dcmHideNotRejectedInstances",
                prev.isHideNotRejectedInstances(),
                view.isHideNotRejectedInstances(),
                false);
        return mods;
    }

    private void storeAttributeCoercions(ConfigurationChanges diffs, Collection<ArchiveAttributeCoercion> coercions, String parentDN)
            throws NamingException {
        for (ArchiveAttributeCoercion coercion : coercions) {
            String dn = LdapUtils.dnOf("cn", coercion.getCommonName(), parentDN);
            ConfigurationChanges.ModifiedObject ldapObj =
                    ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
            config.createSubcontext(dn, storeTo(ldapObj, coercion, new BasicAttributes(true)));
        }
    }

    private Attributes storeTo(ConfigurationChanges.ModifiedObject ldapObj, ArchiveAttributeCoercion coercion, BasicAttributes attrs) {
        attrs.put("objectclass", "dcmArchiveAttributeCoercion");
        attrs.put("cn", coercion.getCommonName());
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmDIMSE", coercion.getDIMSE(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dicomTransferRole", coercion.getRole(), null);
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmHostname", coercion.getHostNames());
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmAETitle", coercion.getAETitles());
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmSOPClass", coercion.getSOPClasses());
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmURI", coercion.getXSLTStylesheetURI(), null);
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmNoKeywords", coercion.isNoKeywords(), false);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmLeadingCFindSCP", coercion.getLeadingCFindSCP(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmMergeMWLTemplateURI",
                coercion.getMergeMWLTemplateURI(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmMergeMWLMatchingKey",
                coercion.getMergeMWLMatchingKey(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmAttributeUpdatePolicy",
                coercion.getAttributeUpdatePolicy(), org.dcm4che3.data.Attributes.UpdatePolicy.MERGE);
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmRulePriority", coercion.getPriority(), 0);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmSupplementFromDeviceReference",
               supplementDeviceRef(coercion), null);
        return attrs;
    }

    private void loadAttributeCoercions(Collection<ArchiveAttributeCoercion> coercions, String parentDN, Device device)
            throws NamingException, ConfigurationException {
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
                        attrs.get("dcmAttributeUpdatePolicy"), org.dcm4che3.data.Attributes.UpdatePolicy.MERGE));
                coercion.setPriority(LdapUtils.intValue(attrs.get("dcmRulePriority"), 0));
                String supplementDeviceDN = LdapUtils.stringValue(attrs.get("dcmSupplementFromDeviceReference"), null);
                coercion.setSupplementFromDevice(parentDN.equals(supplementDeviceDN)
                        ? device
                        : loadSupplementFromDevice(supplementDeviceDN));
                coercions.add(coercion);
            }
        } finally {
            LdapUtils.safeClose(ne);
        }
    }

    private Device loadSupplementFromDevice(String supplementDeviceRef) {
        try {
            return supplementDeviceRef != null
                    ? config.loadDevice(supplementDeviceRef)
                    : null;
        } catch (ConfigurationException e) {
            LOG.info("Failed to load Supplement Device Reference "
                    + supplementDeviceRef + " referenced by Attribute Coercion", e);
            return null;
        }
    }

    private List<ModificationItem> storeDiffs(
            ConfigurationChanges.ModifiedObject ldapObj, ArchiveAttributeCoercion prev, ArchiveAttributeCoercion coercion, ArrayList<ModificationItem> mods) {
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmDIMSE", prev.getDIMSE(), coercion.getDIMSE(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dicomTransferRole", prev.getRole(), coercion.getRole(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmHostname", prev.getHostNames(), coercion.getHostNames());
        LdapUtils.storeDiff(ldapObj, mods, "dcmAETitle", prev.getAETitles(), coercion.getAETitles());
        LdapUtils.storeDiff(ldapObj, mods, "dcmSOPClass", prev.getSOPClasses(), coercion.getSOPClasses());
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmURI", prev.getXSLTStylesheetURI(), coercion.getXSLTStylesheetURI(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmNoKeywords", prev.isNoKeywords(), coercion.isNoKeywords(), false);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmLeadingCFindSCP", prev.getLeadingCFindSCP(), coercion.getLeadingCFindSCP(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmMergeMWLTemplateURI",
                prev.getMergeMWLTemplateURI(),
                coercion.getMergeMWLTemplateURI(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmMergeMWLMatchingKey",
                prev.getMergeMWLMatchingKey(),
                coercion.getMergeMWLMatchingKey(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmAttributeUpdatePolicy",
                prev.getAttributeUpdatePolicy(),
                coercion.getAttributeUpdatePolicy(),
                org.dcm4che3.data.Attributes.UpdatePolicy.MERGE);
        LdapUtils.storeDiff(ldapObj, mods, "dcmRulePriority", prev.getPriority(), coercion.getPriority(), 0);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmSupplementFromDeviceReference",
                supplementDeviceRef(prev),
                supplementDeviceRef(coercion), null);
        return mods;
    }

    private ArchiveAttributeCoercion findAttributeCoercionByCN(
            Collection<ArchiveAttributeCoercion> coercions, String cn) {
        for (ArchiveAttributeCoercion coercion : coercions)
            if (coercion.getCommonName().equals(cn))
                return coercion;
        return null;
    }

    private void storeRejectNotes(ConfigurationChanges diffs, String deviceDN, ArchiveDeviceExtension arcDev) throws NamingException {
        for (RejectionNote rejectionNote : arcDev.getRejectionNotes()) {
            String dn = LdapUtils.dnOf("dcmRejectionNoteLabel", rejectionNote.getRejectionNoteLabel(), deviceDN);
            ConfigurationChanges.ModifiedObject ldapObj =
                    ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
            config.createSubcontext(dn, storeTo(ldapObj, rejectionNote, new BasicAttributes(true)));
        }
    }

    private void storeIDGenerators(ConfigurationChanges diffs, String deviceDN, ArchiveDeviceExtension arcDev) throws NamingException {
        for (IDGenerator generator : arcDev.getIDGenerators().values()) {
            String dn = LdapUtils.dnOf("dcmIDGeneratorName", generator.getName().name(), deviceDN);
            ConfigurationChanges.ModifiedObject ldapObj =
                    ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
            config.createSubcontext(dn, storeTo(ldapObj, generator, new BasicAttributes(true)));
        }
    }

    private Attributes storeTo(ConfigurationChanges.ModifiedObject ldapObj, RejectionNote rjNote, BasicAttributes attrs) {
        attrs.put("objectclass", "dcmRejectionNote");
        attrs.put("dcmRejectionNoteLabel", rjNote.getRejectionNoteLabel());
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmRejectionNoteType", rjNote.getRejectionNoteType(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmRejectionNoteCode", rjNote.getRejectionNoteCode(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmAcceptPreviousRejectedInstance",
                rjNote.getAcceptPreviousRejectedInstance(), RejectionNote.AcceptPreviousRejectedInstance.REJECT);
        LdapUtils.storeNotEmpty(ldapObj, attrs, "dcmOverwritePreviousRejection", rjNote.getOverwritePreviousRejection());
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmDeleteRejectedInstanceDelay", rjNote.getDeleteRejectedInstanceDelay(), null);
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmDeleteRejectionNoteDelay", rjNote.getDeleteRejectionNoteDelay(), null);
        return attrs;
    }

    private Attributes storeTo(ConfigurationChanges.ModifiedObject ldapObj, IDGenerator generator, BasicAttributes attrs) {
        attrs.put("objectClass", "dcmIDGenerator");
        attrs.put("dcmIDGeneratorName", generator.getName().name());
        LdapUtils.storeNotNullOrDef(ldapObj, attrs, "dcmIDGeneratorFormat", generator.getFormat(), null);
        LdapUtils.storeNotDef(ldapObj, attrs, "dcmIDGeneratorInitialValue", generator.getInitialValue(), 1);
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

    private void mergeRejectNotes(ConfigurationChanges diffs, ArchiveDeviceExtension prev, ArchiveDeviceExtension arcDev, String deviceDN)
            throws NamingException {
        for (RejectionNote entry : prev.getRejectionNotes()) {
            String rjNoteID = entry.getRejectionNoteLabel();
            if (arcDev.getRejectionNote(rjNoteID) == null) {
                String dn = LdapUtils.dnOf("dcmRejectionNoteLabel", rjNoteID, deviceDN);
                config.destroySubcontext(dn);
                ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.D);
            }
        }
        for (RejectionNote entryNew : arcDev.getRejectionNotes()) {
            String rjNoteID = entryNew.getRejectionNoteLabel();
            String dn = LdapUtils.dnOf("dcmRejectionNoteLabel", rjNoteID, deviceDN);
            RejectionNote entryOld = prev.getRejectionNote(rjNoteID);
            if (entryOld == null) {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
                config.createSubcontext(dn,
                        storeTo(ConfigurationChanges.nullifyIfNotVerbose(diffs, ldapObj),
                                entryNew, new BasicAttributes(true)));
            } else {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.U);
                config.modifyAttributes(dn, storeDiffs(ldapObj, entryOld, entryNew, new ArrayList<ModificationItem>()));
                ConfigurationChanges.removeLastIfEmpty(diffs, ldapObj);
            }
        }
    }

    private void mergeIDGenerators(ConfigurationChanges diffs, ArchiveDeviceExtension prev, ArchiveDeviceExtension arcDev, String deviceDN)
            throws NamingException {
        for (IDGenerator.Name name : prev.getIDGenerators().keySet()) {
            if (!arcDev.getIDGenerators().containsKey(name)) {
                String dn = LdapUtils.dnOf("dcmIDGeneratorName", name.name(), deviceDN);
                config.destroySubcontext(dn);
                ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.D);
            }
        }
        for (IDGenerator entryNew : arcDev.getIDGenerators().values()) {
            IDGenerator.Name name = entryNew.getName();
            String dn = LdapUtils.dnOf("dcmIDGeneratorName", name.name(), deviceDN);
            IDGenerator entryOld = prev.getIDGenerators().get(name);
            if (entryOld == null) {
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.C);
                config.createSubcontext(dn,
                        storeTo(ConfigurationChanges.nullifyIfNotVerbose(diffs, ldapObj),
                                entryNew, new BasicAttributes(true)));
            } else{
                ConfigurationChanges.ModifiedObject ldapObj =
                        ConfigurationChanges.addModifiedObject(diffs, dn, ConfigurationChanges.ChangeType.U);
                config.modifyAttributes(dn, storeDiffs(ldapObj, entryOld, entryNew, new ArrayList<ModificationItem>()));
                ConfigurationChanges.removeLastIfEmpty(diffs, ldapObj);
            }
        }
    }

    private List<ModificationItem> storeDiffs(ConfigurationChanges.ModifiedObject ldapObj, RejectionNote prev, RejectionNote rjNote,
                                              ArrayList<ModificationItem> mods) {
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmRejectionNoteType", prev.getRejectionNoteType(), rjNote.getRejectionNoteType(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmRejectionNoteCode", prev.getRejectionNoteCode(), rjNote.getRejectionNoteCode(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmAcceptPreviousRejectedInstance",
                prev.getAcceptPreviousRejectedInstance(),
                rjNote.getAcceptPreviousRejectedInstance(),
                RejectionNote.AcceptPreviousRejectedInstance.REJECT);
        LdapUtils.storeDiff(ldapObj, mods, "dcmOverwritePreviousRejection",
                prev.getOverwritePreviousRejection(),
                rjNote.getOverwritePreviousRejection());
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmDeleteRejectedInstanceDelay",
                prev.getDeleteRejectedInstanceDelay(),
                rjNote.getDeleteRejectedInstanceDelay(), null);
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmDeleteRejectionNoteDelay",
                prev.getDeleteRejectionNoteDelay(),
                rjNote.getDeleteRejectionNoteDelay(), null);
        return mods;
    }

    private List<ModificationItem> storeDiffs(ConfigurationChanges.ModifiedObject ldapObj, IDGenerator prev, IDGenerator generator,
                                              ArrayList<ModificationItem> mods) {
//        LdapUtils.storeDiffObject(mods, "dcmIDGeneratorName", prev.getId(), generator.getId());
        LdapUtils.storeDiffObject(ldapObj, mods, "dcmIDGeneratorFormat", prev.getFormat(), generator.getFormat(), null);
        LdapUtils.storeDiff(ldapObj, mods, "dcmIDGeneratorInitialValue", prev.getInitialValue(), generator.getInitialValue(), 1);
        return mods;
    }

    private String supplementDeviceRef(ArchiveAttributeCoercion a) {
        Device supplementDevice = a.getSupplementFromDevice();
        return supplementDevice != null
                ? config.deviceRef(supplementDevice.getDeviceName())
                : null;
    }

    private static String scheduledStationDeviceRef(HL7OrderScheduledStation scheduledStation, LdapDicomConfiguration config) {
        Device scheduledStationDevice = scheduledStation.getDevice();
        return scheduledStationDevice != null
                ? config.deviceRef(scheduledStationDevice.getDeviceName())
                : null;
    }
}
