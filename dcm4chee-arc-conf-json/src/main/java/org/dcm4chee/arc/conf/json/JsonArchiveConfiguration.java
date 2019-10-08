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
 * Java(TM), hosted at https://github.com/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * J4Care.
 * Portions created by the Initial Developer are Copyright (C) 2017-2019
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

package org.dcm4chee.arc.conf.json;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.json.ConfigurationDelegate;
import org.dcm4che3.conf.json.JsonConfigurationExtension;
import org.dcm4che3.conf.json.JsonReader;
import org.dcm4che3.conf.json.JsonWriter;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Code;
import org.dcm4che3.data.Issuer;
import org.dcm4che3.data.ValueSelector;
import org.dcm4che3.deident.DeIdentifier;
import org.dcm4che3.net.*;
import org.dcm4che3.util.Property;
import org.dcm4che3.util.TagUtils;
import org.dcm4chee.arc.conf.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.stream.JsonParser;
import java.net.URI;
import java.time.Period;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jan 2016
 */
public class JsonArchiveConfiguration extends JsonConfigurationExtension {

    private static final Logger LOG = LoggerFactory.getLogger(JsonArchiveConfiguration.class);

    @Override
    protected void storeTo(Device device, JsonWriter writer) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        if (arcDev == null)
            return;

        writer.writeStartObject("dcmArchiveDevice");
        writer.writeNotNullOrDef("dcmFuzzyAlgorithmClass", arcDev.getFuzzyAlgorithmClass(), null);
        writer.writeNotNullOrDef("dcmBulkDataDescriptorID", arcDev.getBulkDataDescriptorID(), null);
        writer.writeNotEmpty("dcmSeriesMetadataStorageID", arcDev.getSeriesMetadataStorageIDs());
        writer.writeNotNullOrDef("dcmSeriesMetadataDelay", arcDev.getSeriesMetadataDelay(), null);
        writer.writeNotNullOrDef("dcmSeriesMetadataPollingInterval", arcDev.getSeriesMetadataPollingInterval(), null);
        writer.writeNotDef("dcmSeriesMetadataFetchSize", arcDev.getSeriesMetadataFetchSize(), 100);
        writer.writeNotDef("dcmSeriesMetadataThreads", arcDev.getSeriesMetadataThreads(), 1);
        writer.writeNotDef("dcmSeriesMetadataMaxRetries", arcDev.getSeriesMetadataMaxRetries(), 0);
        writer.writeNotNullOrDef("dcmSeriesMetadataRetryInterval", arcDev.getSeriesMetadataRetryInterval(), null);
        writer.writeNotDef("dcmPurgeInstanceRecords", arcDev.isPurgeInstanceRecords(), false);
        writer.writeNotNullOrDef("dcmPurgeInstanceRecordsDelay", arcDev.getPurgeInstanceRecordsDelay(), null);
        writer.writeNotNullOrDef("dcmPurgeInstanceRecordsPollingInterval",
                arcDev.getPurgeInstanceRecordsPollingInterval(), null);
        writer.writeNotDef("dcmPurgeInstanceRecordsFetchSize",
                arcDev.getPurgeInstanceRecordsFetchSize(), 100);
        writer.writeNotNullOrDef("dcmDeleteUPSPollingInterval", arcDev.getDeleteUPSPollingInterval(), null);
        writer.writeNotDef("dcmDeleteUPSFetchSize", arcDev.getDeleteUPSFetchSize(), 100);
        writer.writeNotNullOrDef("dcmDeleteUPSCompletedDelay", arcDev.getDeleteUPSCompletedDelay(), null);
        writer.writeNotNullOrDef("dcmDeleteUPSCanceledDelay", arcDev.getDeleteUPSCanceledDelay(), null);
        writer.writeNotNullOrDef("dcmOverwritePolicy", arcDev.getOverwritePolicy(), OverwritePolicy.NEVER);
        writer.writeNotNullOrDef("dcmBulkDataSpoolDirectory",
                arcDev.getBulkDataSpoolDirectory(), ArchiveDeviceExtension.JBOSS_SERVER_TEMP_DIR);
        writer.writeNotEmpty("dcmHideSPSWithStatusFromMWL", arcDev.getHideSPSWithStatusFrom());
        writer.writeNotDef("dcmPersonNameComponentOrderInsensitiveMatching",
                arcDev.isPersonNameComponentOrderInsensitiveMatching(), false);
        writer.writeNotDef("dcmSendPendingCGet", arcDev.isSendPendingCGet(), false);
        writer.writeNotNullOrDef("dcmSendPendingCMoveInterval", arcDev.getSendPendingCMoveInterval(), null);
        writer.writeNotEmpty("dcmWadoSupportedSRClasses", arcDev.getWadoSupportedSRClasses());
        writer.writeNotEmpty("dcmWadoSupportedPRClasses", arcDev.getWadoSupportedPRClasses());
        writer.writeNotNullOrDef("dcmWadoZIPEntryNameFormat",
                arcDev.getWadoZIPEntryNameFormat(), ArchiveDeviceExtension.DEFAULT_WADO_ZIP_ENTRY_NAME_FORMAT);
        writer.writeNotNullOrDef("dcmWadoSR2HtmlTemplateURI", arcDev.getWadoSR2HtmlTemplateURI(), null);
        writer.writeNotNullOrDef("dcmWadoSR2TextTemplateURI", arcDev.getWadoSR2TextTemplateURI(), null);
        writer.writeNotNullOrDef("dcmWadoCDA2HtmlTemplateURI", arcDev.getWadoCDA2HtmlTemplateURI(), null);
        writer.writeNotDef("dcmQueryFetchSize", arcDev.getQueryFetchSize(), 100);
        writer.writeNotDef("dcmQueryMaxNumberOfResults", arcDev.getQueryMaxNumberOfResults(), 0);
        writer.writeNotDef("dcmQidoMaxNumberOfResults", arcDev.getQidoMaxNumberOfResults(), 0);
        writer.writeNotEmpty("dcmFwdMppsDestination", arcDev.getMppsForwardDestinations());
        writer.writeNotEmpty("dcmIanDestination", arcDev.getIanDestinations());
        writer.writeNotNullOrDef("dcmIanDelay", arcDev.getIanDelay(), null);
        writer.writeNotNullOrDef("dcmIanTimeout", arcDev.getIanTimeout(), null);
        writer.writeNotDef("dcmIanOnTimeout", arcDev.isIanOnTimeout(), false);
        writer.writeNotNullOrDef("dcmIanTaskPollingInterval", arcDev.getIanTaskPollingInterval(), null);
        writer.writeNotDef("dcmIanTaskFetchSize", arcDev.getIanTaskFetchSize(), 100);
        writer.writeNotNullOrDef("dcmSpanningCFindSCP", arcDev.getSpanningCFindSCP(), null);
        writer.writeNotEmpty("dcmSpanningCFindSCPRetrieveAET", arcDev.getSpanningCFindSCPRetrieveAETitles());
        writer.writeNotNullOrDef("dcmSpanningCFindSCPPolicy",
                arcDev.getSpanningCFindSCPPolicy(), SpanningCFindSCPPolicy.REPLACE);
        writer.writeNotNullOrDef("dcmFallbackCMoveSCP", arcDev.getFallbackCMoveSCP(), null);
        writer.writeNotNullOrDef("dcmFallbackCMoveSCPDestination", arcDev.getFallbackCMoveSCPDestination(), null);
        writer.writeNotNullOrDef("dcmFallbackCMoveSCPCallingAET", arcDev.getFallbackCMoveSCPCallingAET(), null);
        writer.writeNotDef("dcmFallbackCMoveSCPRetries", arcDev.getFallbackCMoveSCPRetries(), 0);
        writer.writeNotNullOrDef("dcmFallbackCMoveSCPLeadingCFindSCP", arcDev.getFallbackCMoveSCPLeadingCFindSCP(), null);
        writer.writeNotNullOrDef("dcmAltCMoveSCP", arcDev.getAlternativeCMoveSCP(), null);
        writer.writeNotNullOrDef("dcmExportTaskPollingInterval", arcDev.getExportTaskPollingInterval(), null);
        writer.writeNotDef("dcmExportTaskFetchSize", arcDev.getExportTaskFetchSize(), 5);
        writer.writeNotNullOrDef("dcmPurgeStoragePollingInterval", arcDev.getPurgeStoragePollingInterval(), null);
        writer.writeNotDef("dcmPurgeStorageFetchSize", arcDev.getPurgeStorageFetchSize(), 100);
        writer.writeNotNullOrDef("dcmFailedToDeletePollingInterval", arcDev.getFailedToDeletePollingInterval(), null);
        writer.writeNotDef("dcmFailedToDeleteFetchSize", arcDev.getFailedToDeleteFetchSize(), 100);
        writer.writeNotDef("dcmDeleteStudyBatchSize", arcDev.getDeleteStudyBatchSize(), 10);
        writer.writeNotDef("dcmDeletePatientOnDeleteLastStudy", arcDev.isDeletePatientOnDeleteLastStudy(), false);
        writer.writeNotNullOrDef("dcmDeleteRejectedPollingInterval", arcDev.getDeleteRejectedPollingInterval(), null);
        writer.writeNotDef("dcmDeleteRejectedFetchSize", arcDev.getDeleteRejectedFetchSize(), 100);
        writer.writeNotNullOrDef("dcmMaxAccessTimeStaleness", arcDev.getMaxAccessTimeStaleness(), null);
        writer.writeNotNullOrDef("dcmAECacheStaleTimeout", arcDev.getAECacheStaleTimeout(), null);
        writer.writeNotNullOrDef("dcmLeadingCFindSCPQueryCacheStaleTimeout",
                arcDev.getLeadingCFindSCPQueryCacheStaleTimeout(), null);
        writer.writeNotDef("dcmLeadingCFindSCPQueryCacheSize", arcDev.getLeadingCFindSCPQueryCacheSize(), 10);
        writer.writeNotNullOrDef("dcmAuditSpoolDirectory",
                arcDev.getAuditSpoolDirectory(), ArchiveDeviceExtension.JBOSS_SERVER_TEMP_DIR);
        writer.writeNotNullOrDef("dcmAuditPollingInterval", arcDev.getAuditPollingInterval(), null);
        writer.writeNotNullOrDef("dcmAuditAggregateDuration", arcDev.getAuditAggregateDuration(), null);
        writer.writeNotNullOrDef("dcmStowSpoolDirectory",
                arcDev.getStowSpoolDirectory(), ArchiveDeviceExtension.JBOSS_SERVER_TEMP_DIR);
        writer.writeNotNullOrDef("hl7PatientUpdateTemplateURI", arcDev.getPatientUpdateTemplateURI(), null);
        writer.writeNotNullOrDef("hl7ImportReportTemplateURI", arcDev.getImportReportTemplateURI(), null);
        writer.writeNotEmpty("hl7ImportReportTemplateParam", JsonArchiveConfiguration.descriptorProperties(arcDev.getImportReportTemplateParams()));
        writer.writeNotNullOrDef("hl7ScheduleProcedureTemplateURI", arcDev.getScheduleProcedureTemplateURI(), null);
        writer.writeNotNullOrDef("hl7OutgoingPatientUpdateTemplateURI", arcDev.getOutgoingPatientUpdateTemplateURI(), null);
        writer.writeNotNullOrDef("hl7LogFilePattern", arcDev.getHL7LogFilePattern(), null);
        writer.writeNotNullOrDef("hl7ErrorLogFilePattern", arcDev.getHL7ErrorLogFilePattern(), null);
        writer.writeNotNullOrDef("dcmUnzipVendorDataToURI", arcDev.getUnzipVendorDataToURI(), null);
        writer.writeNotNullOrDef("dcmPurgeQueueMessagePollingInterval",
                arcDev.getPurgeQueueMessagePollingInterval(), null);
        writer.writeNotNullOrDef("dcmWadoSpoolDirectory",
                arcDev.getWadoSpoolDirectory(), ArchiveDeviceExtension.JBOSS_SERVER_TEMP_DIR);
        writer.writeNotNullOrDef("dcmRejectExpiredStudiesPollingInterval",
                arcDev.getRejectExpiredStudiesPollingInterval(), null);
        writer.writeNotEmpty("dcmRejectExpiredStudiesSchedule", arcDev.getRejectExpiredStudiesSchedules());
        writer.writeNotDef("dcmRejectExpiredStudiesFetchSize", arcDev.getRejectExpiredStudiesFetchSize(), 0);
        writer.writeNotDef("dcmRejectExpiredSeriesFetchSize", arcDev.getRejectExpiredSeriesFetchSize(), 0);
        writer.writeNotNullOrDef("dcmRejectExpiredStudiesAETitle", arcDev.getRejectExpiredStudiesAETitle(), null);
        writer.writeNotNullOrDef("dcmFallbackCMoveSCPStudyOlderThan", arcDev.getFallbackCMoveSCPStudyOlderThan(), null);
        writer.writeNotNullOrDef("dcmStorePermissionServiceURL", arcDev.getStorePermissionServiceURL(), null);
        writer.writeNotNullOrDef("dcmStorePermissionServiceResponse",
                arcDev.getStorePermissionServiceResponse(), null);
        writer.writeNotNullOrDef("dcmStorePermissionServiceResponsePattern",
                arcDev.getStorePermissionServiceResponsePattern(), null);
        writer.writeNotNullOrDef("dcmStorePermissionCacheStaleTimeout", arcDev.getStorePermissionCacheStaleTimeout(), null);
        writer.writeNotDef("dcmStorePermissionCacheSize", arcDev.getStorePermissionCacheSize(), 10);
        writer.writeNotNullOrDef("dcmMergeMWLCacheStaleTimeout",
                arcDev.getMergeMWLCacheStaleTimeout(), null);
        writer.writeNotDef("dcmMergeMWLCacheSize",
                arcDev.getMergeMWLCacheSize(), 10);
        writer.writeNotDef("dcmStoreUpdateDBMaxRetries", arcDev.getStoreUpdateDBMaxRetries(), 1);
        writer.writeNotDef("dcmStoreUpdateDBMinRetryDelay", arcDev.getStoreUpdateDBMinRetryDelay(), 500);
        writer.writeNotDef("dcmStoreUpdateDBMaxRetryDelay", arcDev.getStoreUpdateDBMaxRetryDelay(), 1000);
        writer.writeNotNullOrDef("dcmAllowRejectionForDataRetentionPolicyExpired",
                arcDev.getAllowRejectionForDataRetentionPolicyExpired(),
                AllowRejectionForDataRetentionPolicyExpired.EXPIRED_UNSET);
        writer.writeNotNullOrDef("dcmAcceptMissingPatientID", arcDev.getAcceptMissingPatientID(),
                AcceptMissingPatientID.CREATE);
        writer.writeNotNullOrDef("dcmAllowDeleteStudyPermanently", arcDev.getAllowDeleteStudyPermanently(),
                AllowDeleteStudyPermanently.REJECTED);
        writer.writeNotNullOrDef("dcmAllowDeletePatient", arcDev.getAllowDeletePatient(),
                AllowDeletePatient.WITHOUT_STUDIES);
        writer.writeNotNullOrDef("dcmStorePermissionServiceExpirationDatePattern",
                arcDev.getStorePermissionServiceExpirationDatePattern(), null);
        writer.writeNotNullOrDef("dcmShowPatientInfoInSystemLog",
                arcDev.getShowPatientInfoInSystemLog(), ShowPatientInfo.PLAIN_TEXT);
        writer.writeNotNullOrDef("dcmShowPatientInfoInAuditLog",
                arcDev.getShowPatientInfoInAuditLog(), ShowPatientInfo.PLAIN_TEXT);
        writer.writeNotNullOrDef("dcmPurgeStgCmtCompletedDelay", arcDev.getPurgeStgCmtCompletedDelay(), null);
        writer.writeNotNullOrDef("dcmPurgeStgCmtPollingInterval", arcDev.getPurgeStgCmtPollingInterval(), null);
        writer.writeNotNullOrDef("dcmDefaultCharacterSet", arcDev.getDefaultCharacterSet(), null);
        writer.writeNotNullOrDef("dcmUPSWorklistLabel", arcDev.getUPSWorklistLabel(), null);
        writer.writeNotEmpty("dcmUPSEventSCU", arcDev.getUPSEventSCUs());
        writer.writeNotDef("dcmUPSEventSCUKeepAlive", arcDev.getUPSEventSCUKeepAlive(), 0);
        writer.writeNotNullOrDef("dcmStorePermissionServiceErrorCommentPattern",
                arcDev.getStorePermissionServiceErrorCommentPattern(), null);
        writer.writeNotNullOrDef("dcmStorePermissionServiceErrorCodePattern",
                arcDev.getStorePermissionServiceErrorCodePattern(), null);
        writer.writeNotEmpty("dcmRetrieveAET", arcDev.getRetrieveAETitles());
        writer.writeNotEmpty("dcmReturnRetrieveAET", arcDev.getReturnRetrieveAETitles());
        writer.writeNotNullOrDef("dcmExternalRetrieveAEDestination", arcDev.getExternalRetrieveAEDestination(),
                null);
        writer.writeNotNullOrDef("dcmXDSiImagingDocumentSourceAETitle", arcDev.getXDSiImagingDocumentSourceAETitle(),
                null);
        writer.writeNotNullOrDef("dcmRemapRetrieveURL", arcDev.getRemapRetrieveURL(), null);
        writer.writeNotDef("dcmValidateCallingAEHostname", arcDev.isValidateCallingAEHostname(), false);
        writer.writeNotNullOrDef("hl7PSUSendingApplication", arcDev.getHL7PSUSendingApplication(), null);
        writer.writeNotEmpty("hl7PSUReceivingApplication", arcDev.getHL7PSUReceivingApplications());
        writer.writeNotNullOrDef("hl7PSUDelay", arcDev.getHL7PSUDelay(), null);
        writer.writeNotNullOrDef("hl7PSUTimeout", arcDev.getHL7PSUTimeout(), null);
        writer.writeNotDef("hl7PSUOnTimeout", arcDev.isHL7PSUOnTimeout(), false);
        writer.writeNotNullOrDef("hl7PSUTaskPollingInterval", arcDev.getHL7PSUTaskPollingInterval(), null);
        writer.writeNotDef("hl7PSUTaskFetchSize", arcDev.getHL7PSUTaskFetchSize(), 100);
        writer.writeNotDef("hl7PSUMWL", arcDev.isHL7PSUMWL(), false);
        writer.writeNotNullOrDef("dcmAcceptConflictingPatientID",
                arcDev.getAcceptConflictingPatientID(), AcceptConflictingPatientID.MERGED);
        writer.writeNotNullOrDef("dcmAuditRecordRepositoryURL", arcDev.getAuditRecordRepositoryURL(), null);
        writer.writeNotNullOrDef("dcmAudit2JsonFhirTemplateURI", arcDev.getAudit2JsonFhirTemplateURI(), null);
        writer.writeNotNullOrDef("dcmAudit2XmlFhirTemplateURI", arcDev.getAudit2XmlFhirTemplateURI(), null);
        writer.writeNotNullOrDef("dcmCopyMoveUpdatePolicy",
                arcDev.getCopyMoveUpdatePolicy(), Attributes.UpdatePolicy.PRESERVE);
        writer.writeNotNullOrDef("dcmLinkMWLEntryUpdatePolicy",
                arcDev.getLinkMWLEntryUpdatePolicy(), Attributes.UpdatePolicy.PRESERVE);
        writer.writeNotNullOrDef("dcmStorageVerificationPolicy", arcDev.getStorageVerificationPolicy(),
                StorageVerificationPolicy.OBJECT_CHECKSUM);
        writer.writeNotDef("dcmStorageVerificationUpdateLocationStatus",
                arcDev.isStorageVerificationUpdateLocationStatus(), false);
        writer.writeNotEmpty("dcmStorageVerificationStorageID", arcDev.getStorageVerificationStorageIDs());
        writer.writeNotNullOrDef("dcmStorageVerificationAETitle", arcDev.getStorageVerificationAETitle(),
                null);
        writer.writeNotNullOrDef("dcmStorageVerificationBatchID", arcDev.getStorageVerificationBatchID(),
                null);
        writer.writeNotNullOrDef("dcmStorageVerificationInitialDelay", arcDev.getStorageVerificationInitialDelay(),
                null);
        writer.writeNotNullOrDef("dcmStorageVerificationPeriod", arcDev.getStorageVerificationPeriod(),
                null);
        writer.writeNotDef("dcmStorageVerificationMaxScheduled", arcDev.getStorageVerificationMaxScheduled(),
                0);
        writer.writeNotNullOrDef("dcmStorageVerificationPollingInterval",
                arcDev.getStorageVerificationPollingInterval(), null);
        writer.writeNotEmpty("dcmStorageVerificationSchedule", arcDev.getStorageVerificationSchedules());
        writer.writeNotDef("dcmStorageVerificationFetchSize", arcDev.getStorageVerificationFetchSize(),
                100);
        writer.writeNotDef("dcmUpdateLocationStatusOnRetrieve",
                arcDev.isUpdateLocationStatusOnRetrieve(), false);
        writer.writeNotDef("dcmStorageVerificationOnRetrieve",
                arcDev.isStorageVerificationOnRetrieve(), false);
        writer.writeNotDef("hl7TrackChangedPatientID", arcDev.isHL7TrackChangedPatientID(), true);
        writer.writeNotNullOrDef("dcmInvokeImageDisplayPatientURL", arcDev.getInvokeImageDisplayPatientURL(),
                null);
        writer.writeNotNullOrDef("dcmInvokeImageDisplayStudyURL", arcDev.getInvokeImageDisplayStudyURL(),
                null);
        writer.writeNotNullOrDef("hl7ADTSendingApplication", arcDev.getHL7ADTSendingApplication(),
                null);
        writer.writeNotEmpty("hl7ADTReceivingApplication", arcDev.getHL7ADTReceivingApplication());
        writer.writeNotNullOrDef("hl7ScheduledProtocolCodeInOrder",
                arcDev.getHL7ScheduledProtocolCodeInOrder(), ScheduledProtocolCodeInOrder.OBR_4_4);
        writer.writeNotNullOrDef("hl7ScheduledStationAETInOrder", arcDev.getHL7ScheduledStationAETInOrder(),
                null);
        writer.writeNotEmpty("hl7NoPatientCreateMessageType", arcDev.getHL7NoPatientCreateMessageTypes());
        writer.writeNotNullOrDef("dcmAuditUnknownStudyInstanceUID",
                arcDev.getAuditUnknownStudyInstanceUID(), ArchiveDeviceExtension.AUDIT_UNKNOWN_STUDY_INSTANCE_UID);
        writer.writeNotNullOrDef("dcmAuditUnknownPatientID",
                arcDev.getAuditUnknownPatientID(), ArchiveDeviceExtension.AUDIT_UNKNOWN_PATIENT_ID);
        writer.writeNotDef("dcmAuditSoftwareConfigurationVerbose", arcDev.isAuditSoftwareConfigurationVerbose(),
                false);
        writer.writeNotDef("hl7UseNullValue", arcDev.isHL7UseNullValue(), false);
        writer.writeNotDef("dcmQueueTasksFetchSize", arcDev.getQueueTasksFetchSize(), 100);
        writer.writeNotNullOrDef("dcmRejectionNoteStorageAET", arcDev.getRejectionNoteStorageAET(), null);
        writer.writeNotEmpty("dcmXRoadProperty", descriptorProperties(arcDev.getXRoadProperties()));
        writer.writeNotEmpty("dcmImpaxReportProperty", descriptorProperties(arcDev.getImpaxReportProperties()));
        writer.writeNotNullOrDef("dcmUIConfigurationDeviceName", arcDev.getUiConfigurationDeviceName(),
                null);
        writer.writeNotNullOrDef("dcmCompressionAETitle", arcDev.getCompressionAETitle(), null);
        writer.writeNotNullOrDef("dcmCompressionPollingInterval", arcDev.getCompressionPollingInterval(),
                null);
        writer.writeNotDef("dcmCompressionFetchSize", arcDev.getCompressionFetchSize(), 100);
        writer.writeNotEmpty("dcmCompressionSchedule", arcDev.getCompressionSchedules());
        writer.writeNotDef("dcmCompressionThreads", arcDev.getCompressionThreads(), 1);
        writer.writeNotNullOrDef("dcmDiffTaskProgressUpdateInterval",
                arcDev.getDiffTaskProgressUpdateInterval(), null);
        writer.writeNotNullOrDef("dcmPatientVerificationPDQServiceID",
                arcDev.getPatientVerificationPDQServiceID(), null);
        writer.writeNotNullOrDef("dcmPatientVerificationPollingInterval",
                arcDev.getPatientVerificationPollingInterval(), null);
        writer.writeNotDef("dcmPatientVerificationFetchSize",
                arcDev.getPatientVerificationFetchSize(), 100);
        writer.writeNotDef("dcmPatientVerificationAdjustIssuerOfPatientID",
                arcDev.isPatientVerificationAdjustIssuerOfPatientID(), false);
        writer.writeNotNullOrDef("dcmPatientVerificationPeriod",
                arcDev.getPatientVerificationPeriod(), null);
        writer.writeNotNullOrDef("dcmPatientVerificationPeriodOnNotFound",
                arcDev.getPatientVerificationPeriodOnNotFound(), null);
        writer.writeNotNullOrDef("dcmPatientVerificationRetryInterval",
                arcDev.getPatientVerificationRetryInterval(), null);
        writer.writeNotDef("dcmPatientVerificationMaxRetries",
                arcDev.getPatientVerificationMaxRetries(), 0);
        writer.writeNotNullOrDef("dcmPatientVerificationMaxStaleness",
                arcDev.getPatientVerificationMaxStaleness(), null);
        writer.writeNotNullOrDef("hl7OrderMissingStudyIUIDPolicy", arcDev.getHl7OrderMissingStudyIUIDPolicy(),
                HL7OrderMissingStudyIUIDPolicy.GENERATE);
        writer.writeNotNullOrDef("hl7ImportReportMissingStudyIUIDPolicy",
                arcDev.getHl7ImportReportMissingStudyIUIDPolicy(), HL7ImportReportMissingStudyIUIDPolicy.GENERATE);
        writer.writeNotNullOrDef("hl7DicomCharacterSet", arcDev.getHl7DicomCharacterSet(), null);
        writer.writeNotDef("hl7VeterinaryUsePatientName", arcDev.isHl7VeterinaryUsePatientName(), false);
        writer.writeNotDef("dcmCSVUploadChunkSize", arcDev.getCSVUploadChunkSize(), 100);
        writer.writeNotDef("dcmValidateUID", arcDev.isValidateUID(), true);
        writer.writeNotDef("dcmRelationalQueryNegotiationLenient",
                arcDev.isRelationalQueryNegotiationLenient(), false);
        writer.writeNotDef("dcmRelationalRetrieveNegotiationLenient",
                arcDev.isRelationalRetrieveNegotiationLenient(), false);
        writer.writeNotEmpty("dcmRejectConflictingPatientAttribute",
                TagUtils.toHexStrings(arcDev.getRejectConflictingPatientAttribute()));
        writer.writeNotDef("dcmSchedulerMinStartDelay", arcDev.getSchedulerMinStartDelay(), 60);
        writer.writeNotDef("dcmStowRetiredTransferSyntax", arcDev.isStowRetiredTransferSyntax(), false);
        writer.writeNotDef("dcmStowExcludeAPPMarkers", arcDev.isStowExcludeAPPMarkers(), false);
        writer.writeNotNullOrDef("dcmWadoThumbnailViewport", arcDev.getWadoThumbnailViewPort(),
                ArchiveDeviceExtension.WADO_THUMBNAIL_VIEWPORT);
        writer.writeNotNullOrDef("dcmRestrictRetrieveAccordingTransferCapabilities",
                arcDev.getRestrictRetrieveAccordingTransferCapabilities(),
                RestrictRetrieveAccordingTransferCapabilities.CONFIGURATION);
        writeAttributeFilters(writer, arcDev);
        writeStorageDescriptor(writer, arcDev.getStorageDescriptors());
        writeQueryRetrieveView(writer, arcDev.getQueryRetrieveViews());
        writeQueue(writer, arcDev.getQueueDescriptors());
        writePDQServiceDescriptor(writer, arcDev.getPDQServiceDescriptors());
        writeExporterDescriptor(writer, arcDev.getExporterDescriptors());
        writeExportRule(writer, arcDev.getExportRules());
        writeExportPrefetchRules(writer, arcDev.getExportPriorsRules());
        writeArchiveCompressionRules(writer, arcDev.getCompressionRules());
        writeStoreAccessControlIDRules(writer, arcDev.getStoreAccessControlIDRules());
        writeArchiveAttributeCoercion(writer, arcDev.getAttributeCoercions());
        writeRejectionNote(writer, arcDev.getRejectionNotes());
        writeStudyRetentionPolicies(writer, arcDev.getStudyRetentionPolicies());
        writeHL7StudyRetentionPolicies(writer, arcDev.getHL7StudyRetentionPolicies());
        writeIDGenerators(writer, arcDev);
        writeHL7ForwardRules(writer, arcDev.getHL7ForwardRules());
        writeHL7ExportRules(writer, arcDev.getHL7ExportRules());
        writeHL7PrefetchRules(writer, arcDev.getHL7PrefetchRules());
        writeRSForwardRules(writer, arcDev.getRSForwardRules());
        writeAttributeSet(writer, arcDev);
        writeScheduledStations(writer, arcDev.getHL7OrderScheduledStations());
        writeHL7OrderSPSStatus(writer, arcDev.getHL7OrderSPSStatuses());
        writeKeycloakServers(writer, arcDev.getKeycloakServers());
        writeMetricsDescriptors(writer, arcDev.getMetricsDescriptors());
        config.writeBulkdataDescriptors(arcDev.getBulkDataDescriptors(), writer);
        writer.writeEnd();
    }

    private void writeAttributeFilters(JsonWriter writer, ArchiveDeviceExtension arcDev) {
        writer.writeStartArray("dcmAttributeFilter");
        for (Map.Entry<Entity, AttributeFilter> entry : arcDev.getAttributeFilters().entrySet()) {
            writeAttributeFilter(writer, entry.getKey(), entry.getValue());
        }
        writer.writeEnd();
    }

    private void writeAttributeSet(JsonWriter writer, ArchiveDeviceExtension arcDev) {
        writer.writeStartArray("dcmAttributeSet");
        for (Map<String, AttributeSet> map : arcDev.getAttributeSet().values()) {
            for (AttributeSet attributeSet : map.values()) {
                writeAttributeSet(writer, attributeSet);
            }
        }
        writer.writeEnd();
    }

    public void writeAttributeFilter(JsonWriter writer, Entity entity, AttributeFilter attributeFilter) {
        writer.writeStartObject();
        writer.writeNotNullOrDef("dcmEntity", entity.name(), null);
        writer.writeNotEmpty("dcmTag", TagUtils.toHexStrings(attributeFilter.getSelection()));
        writer.writeNotNullOrDef("dcmCustomAttribute1", attributeFilter.getCustomAttribute1(), null);
        writer.writeNotNullOrDef("dcmCustomAttribute2", attributeFilter.getCustomAttribute2(), null);
        writer.writeNotNullOrDef("dcmCustomAttribute3", attributeFilter.getCustomAttribute3(), null);
        writer.writeNotNullOrDef("dcmAttributeUpdatePolicy",
                attributeFilter.getAttributeUpdatePolicy(), Attributes.UpdatePolicy.PRESERVE);
        writer.writeEnd();
    }

    private void writeAttributeSet(JsonWriter writer, AttributeSet attributeSet) {
        writer.writeStartObject();
        writer.writeNotNullOrDef("dcmAttributeSetType", attributeSet.getType(), null);
        writer.writeNotNullOrDef("dcmAttributeSetID", attributeSet.getID(), null);
        writer.writeNotNullOrDef("dicomDescription", attributeSet.getDescription(), null);
        writer.writeNotNullOrDef("dcmAttributeSetTitle", attributeSet.getTitle(), null);
        writer.writeNotDef("dcmAttributeSetNumber", attributeSet.getNumber(), 0);
        writer.writeNotDef("dicomInstalled", attributeSet.isInstalled(), true);
        writer.writeNotEmpty("dcmProperty", descriptorProperties(attributeSet.getProperties()));
        writer.writeNotEmpty("dcmTag", TagUtils.toHexStrings(attributeSet.getSelection()));
        writer.writeEnd();
    }

    private void writeStorageDescriptor(JsonWriter writer, Collection<StorageDescriptor> storageDescriptorList) {
        writer.writeStartArray("dcmStorage");
        for (StorageDescriptor st : storageDescriptorList) {
            writer.writeStartObject();
            writer.writeNotNullOrDef("dcmStorageID", st.getStorageID(), null);
            writer.writeNotNullOrDef("dcmURI", st.getStorageURIStr(), null);
            writer.writeNotNullOrDef("dcmDigestAlgorithm", st.getDigestAlgorithm(), null);
            writer.writeNotNullOrDef("dcmInstanceAvailability", st.getInstanceAvailability(), Availability.ONLINE);
            writer.writeNotNullOrDef("dcmStorageDuration", st.getStorageDuration(), StorageDuration.PERMANENT);
            writer.writeNotDef("dcmReadOnly", st.isReadOnly(), false);
            writer.writeNotDef("dcmNoDeletionConstraint", st.isNoDeletionConstraint(), false);
            writer.writeNotDef("dcmDeleterThreads", st.getDeleterThreads(), 1);
            writer.writeNotNullOrDef("dcmStorageClusterID", st.getStorageClusterID(), null);
            writer.writeNotNullOrDef("dcmStorageThreshold", st.getStorageThreshold(), null);
            writer.writeNotEmpty("dcmDeleterThreshold", st.getDeleterThresholdsAsStrings());
            writer.writeNotEmpty("dcmProperty", descriptorProperties(st.getProperties()));
            writer.writeNotEmpty("dcmExternalRetrieveAET", st.getExternalRetrieveAETitles());
            writer.writeNotNullOrDef("dcmExportStorageID", st.getExportStorageID(), null);
            writer.writeNotNullOrDef("dcmRetrieveCacheStorageID", st.getRetrieveCacheStorageID(), null);
            writer.writeNotDef("dcmRetrieveCacheMaxParallel", st.getRetrieveCacheMaxParallel(), 10);
            writer.writeNotEmpty("dcmDeleteStudiesOlderThan", st.getRetentionPeriodsAsStrings(RetentionPeriod.DeleteStudies.OlderThan));
            writer.writeNotEmpty("dcmDeleteStudiesReceivedBefore", st.getRetentionPeriodsAsStrings(RetentionPeriod.DeleteStudies.ReceivedBefore));
            writer.writeNotEmpty("dcmDeleteStudiesNotUsedSince", st.getRetentionPeriodsAsStrings(RetentionPeriod.DeleteStudies.NotUsedSince));
            writer.writeEnd();
        }
        writer.writeEnd();
    }

    static String[] descriptorProperties(Map<String, ?> props) {
        String[] ss = new String[props.size()];
        int i = 0;
        for (Map.Entry<String, ?> entry : props.entrySet())
            ss[i++] = entry.getKey() + '=' + entry.getValue();
        return ss;
    }

    private void writeQueryRetrieveView(JsonWriter writer, Collection<QueryRetrieveView> queryRetrieveViewList) {
        writer.writeStartArray("dcmQueryRetrieveView");
        for (QueryRetrieveView qrv : queryRetrieveViewList) {
            writer.writeStartObject();
            writer.writeNotNullOrDef("dcmQueryRetrieveViewID", qrv.getViewID(), null);
            writer.writeNotEmpty("dcmShowInstancesRejectedByCode", qrv.getShowInstancesRejectedByCodes());
            writer.writeNotEmpty("dcmHideRejectionNoteWithCode", qrv.getHideRejectionNotesWithCodes());
            writer.writeNotDef("dcmHideNotRejectedInstances", qrv.isHideNotRejectedInstances(), false);
            writer.writeEnd();
        }
        writer.writeEnd();
    }

    private void writeQueue(JsonWriter writer, Collection<QueueDescriptor> queueDescriptorsList) {
        writer.writeStartArray("dcmQueue");
        for (QueueDescriptor qd : queueDescriptorsList) {
            writer.writeStartObject();
            writer.writeNotNullOrDef("dcmQueueName", qd.getQueueName(), null);
            writer.writeNotNullOrDef("dcmJndiName", qd.getJndiName(), null);
            writer.writeNotNullOrDef("dicomDescription", qd.getDescription(), null);
            writer.writeNotDef("dcmMaxRetries", qd.getMaxRetries(), 0);
            writer.writeNotNullOrDef("dcmRetryDelay", qd.getRetryDelay(), QueueDescriptor.DEFAULT_RETRY_DELAY);
            writer.writeNotNullOrDef("dcmMaxRetryDelay", qd.getMaxRetryDelay(), null);
            writer.writeNotDef("dcmRetryDelayMultiplier", qd.getRetryDelayMultiplier(), 100);
            writer.writeNotDef("dcmRetryOnWarning", qd.isRetryOnWarning(), false);
            writer.writeNotNullOrDef(
                    "dcmPurgeQueueMessageCompletedDelay", qd.getPurgeQueueMessageCompletedDelay(), null);
            writer.writeNotNullOrDef(
                    "dcmPurgeQueueMessageFailedDelay", qd.getPurgeQueueMessageFailedDelay(), null);
            writer.writeNotNullOrDef(
                    "dcmPurgeQueueMessageWarningDelay", qd.getPurgeQueueMessageWarningDelay(), null);
            writer.writeNotNullOrDef(
                    "dcmPurgeQueueMessageCanceledDelay", qd.getPurgeQueueMessageCanceledDelay(), null);
            writer.writeNotDef("dcmMaxQueueSize", qd.getMaxQueueSize(), 0);
            writer.writeEnd();
        }
        writer.writeEnd();
    }

    private void writePDQServiceDescriptor(JsonWriter writer, Collection<PDQServiceDescriptor> pdqServiceDescriptors) {
        writer.writeStartArray("dcmPDQService");
        for (PDQServiceDescriptor desc : pdqServiceDescriptors) {
            writer.writeStartObject();
            writer.writeNotNullOrDef("dcmPDQServiceID", desc.getPDQServiceID(), null);
            writer.writeNotNullOrDef("dcmURI", desc.getPDQServiceURI(), null);
            writer.writeNotNullOrDef("dicomDescription", desc.getDescription(), null);
            writer.writeNotEmpty("dcmTag", TagUtils.toHexStrings(desc.getSelection()));
            writer.writeNotEmpty("dcmProperty", descriptorProperties(desc.getProperties()));
            writer.writeNotNullOrDef("dcmEntity", desc.getEntity(), Entity.Patient);
            writer.writeEnd();
        }
        writer.writeEnd();
    }

    private void writeExporterDescriptor (JsonWriter writer, Collection<ExporterDescriptor> exportDescriptorList) {
        writer.writeStartArray("dcmExporter");
        for (ExporterDescriptor ed : exportDescriptorList) {
            writer.writeStartObject();
            writer.writeNotNullOrDef("dcmExporterID", ed.getExporterID(), null);
            writer.writeNotNullOrDef("dcmURI", ed.getExportURI(), null);
            writer.writeNotNullOrDef("dcmQueueName", ed.getQueueName(), null);
            writer.writeNotNullOrDef("dicomDescription", ed.getDescription(), null);
            writer.writeNotNullOrDef("dicomAETitle", ed.getAETitle(), null);
            writer.writeNotNullOrDef("dcmStgCmtSCP", ed.getStgCmtSCPAETitle(), null);
            writer.writeNotNullOrDef("dcmDeleteStudyFromStorageID", ed.getDeleteStudyFromStorageID(), null);
            writer.writeNotEmpty("dcmIanDestination", ed.getIanDestinations());
            writer.writeNotEmpty("dcmRetrieveAET", ed.getRetrieveAETitles());
            writer.writeNotNullOrDef("dcmRetrieveLocationUID", ed.getRetrieveLocationUID(), null);
            writer.writeNotNullOrDef("dcmInstanceAvailability", ed.getInstanceAvailability(), Availability.ONLINE);
            writer.writeNotEmpty("dcmSchedule", ed.getSchedules());
            writer.writeNotEmpty("dcmProperty", descriptorProperties(ed.getProperties()));
            writer.writeNotDef("dcmExportPriority", ed.getPriority(), 4);
            writer.writeNotDef("dcmRejectForDataRetentionExpiry", ed.isRejectForDataRetentionExpiry(), false);
            writer.writeEnd();
        }
        writer.writeEnd();
    }

    private void writeExportRule(JsonWriter writer, Collection<ExportRule> exportRuleList) {
        writer.writeStartArray("dcmExportRule");
        for (ExportRule er : exportRuleList) {
            writer.writeStartObject();
            writer.writeNotNullOrDef("cn", er.getCommonName(), null);
            writer.writeNotNullOrDef("dcmEntity", er.getEntity(), null);
            writer.writeNotEmpty("dcmExporterID", er.getExporterIDs());
            writer.writeNotEmpty("dcmProperty", toStrings(er.getConditions().getMap()));
            writer.writeNotEmpty("dcmSchedule", er.getSchedules());
            writer.writeNotNullOrDef("dcmDuration", er.getExportDelay(), null);
            writer.writeNotDef("dcmExportPreviousEntity", er.isExportPreviousEntity(), false);
            writer.writeNotNullOrDef("dcmExportReoccurredInstances", er.getExportReoccurredInstances(),
                    ExportReoccurredInstances.REPLACE);
            writer.writeEnd();
        }
        writer.writeEnd();
    }

    private void writeExportPrefetchRules(JsonWriter writer, Collection<ExportPriorsRule> exportPriorsRuleList) {
        writer.writeStartArray("dcmExportPriorsRule");
        for (ExportPriorsRule rule : exportPriorsRuleList) {
            writer.writeStartObject();
            writer.writeNotNullOrDef("cn", rule.getCommonName(), null);
            writer.writeNotEmpty("dcmExporterID", rule.getExporterIDs());
            writer.writeNotEmpty("dcmProperty", toStrings(rule.getConditions().getMap()));
            writer.writeNotEmpty("dcmSchedule", rule.getSchedules());
            writer.writeNotEmpty("dcmEntitySelector", rule.getEntitySelectors());
            writer.writeNotNullOrDef("dcmDuration", rule.getSuppressDuplicateExportInterval(), null);
            writer.writeNotNullOrDef("dcmExportReoccurredInstances", rule.getExportReoccurredInstances(),
                    ExportReoccurredInstances.REPLACE);
            writer.writeEnd();
        }
        writer.writeEnd();
    }

    static void writeHL7ExportRules(JsonWriter writer, Collection<HL7ExportRule> exportRuleList) {
        writer.writeStartArray("hl7ExportRule");
        for (HL7ExportRule rule : exportRuleList) {
            writer.writeStartObject();
            writer.writeNotNullOrDef("cn", rule.getCommonName(), null);
            writer.writeNotEmpty("dcmExporterID", rule.getExporterIDs());
            writer.writeNotEmpty("dcmProperty", toStrings(rule.getConditions().getMap()));
            writer.writeNotNullOrDef("dcmNullifyIssuerOfPatientID", rule.getIgnoreAssigningAuthorityOfPatientID(), null);
            writer.writeNotEmpty("dcmIssuerOfPatientID", rule.getAssigningAuthorityOfPatientIDs());
            writer.writeNotEmpty("dcmEntitySelector", rule.getEntitySelectors());
            writer.writeNotNullOrDef("dcmDuration", rule.getSuppressDuplicateExportInterval(), null);
            writer.writeEnd();
        }
        writer.writeEnd();
    }

    static void writeHL7PrefetchRules(JsonWriter writer, Collection<HL7PrefetchRule> prefetchRuleList) {
        writer.writeStartArray("hl7PrefetchRule");
        for (HL7PrefetchRule rule : prefetchRuleList) {
            writer.writeStartObject();
            writer.writeNotNullOrDef("cn", rule.getCommonName(), null);
            writer.writeNotNullOrDef("dicomAETitle", rule.getAETitle(), null);
            writer.writeNotNullOrDef("dcmQueueName", rule.getQueueName(), null);
            writer.writeNotNullOrDef("dcmPrefetchCFindSCP", rule.getPrefetchCFindSCP(), null);
            writer.writeNotNullOrDef("dcmPrefetchCMoveSCP", rule.getPrefetchCMoveSCP(), null);
            writer.writeNotEmpty("dcmPrefetchCStoreSCP", rule.getPrefetchCStoreSCPs());
            writer.writeNotEmpty("dcmProperty", toStrings(rule.getConditions().getMap()));
            writer.writeNotEmpty("dcmSchedule", rule.getSchedules());
            writer.writeNotNullOrDef("dcmNullifyIssuerOfPatientID", rule.getIgnoreAssigningAuthorityOfPatientID(), null);
            writer.writeNotEmpty("dcmIssuerOfPatientID", rule.getAssigningAuthorityOfPatientIDs());
            writer.writeNotEmpty("dcmEntitySelector", rule.getEntitySelectors());
            writer.writeNotNullOrDef("dcmDuration", rule.getSuppressDuplicateRetrieveInterval(), null);
            writer.writeEnd();
        }
        writer.writeEnd();
    }

    private void writeArchiveCompressionRules(
            JsonWriter writer, Collection<ArchiveCompressionRule> archiveCompressionRuleList) {
        writer.writeStartArray("dcmArchiveCompressionRule");
        for (ArchiveCompressionRule acr : archiveCompressionRuleList) {
            writer.writeStartObject();
            writer.writeNotNullOrDef("cn", acr.getCommonName(), null);
            writer.writeNotNullOrDef("dcmCompressionDelay", acr.getDelay(), null);
            writer.writeNotNullOrDef("dicomTransferSyntax", acr.getTransferSyntax(), null);
            writer.writeNotDef("dcmRulePriority", acr.getPriority(), 0);
            writer.writeNotEmpty("dcmProperty", toStrings(acr.getConditions().getMap()));
            writer.writeNotEmpty("dcmImageWriteParam", acr.getImageWriteParams());
            writer.writeEnd();
        }
        writer.writeEnd();
    }

    private void writeStoreAccessControlIDRules(JsonWriter writer, Collection<StoreAccessControlIDRule> rules) {
        writer.writeStartArray("dcmStoreAccessControlIDRule");
        for (StoreAccessControlIDRule acr : rules) {
            writer.writeStartObject();
            writer.writeNotNullOrDef("cn", acr.getCommonName(), null);
            writer.writeNotNullOrDef("dcmStoreAccessControlID", acr.getStoreAccessControlID(), null);
            writer.writeNotDef("dcmRulePriority", acr.getPriority(), 0);
            writer.writeNotEmpty("dcmProperty", toStrings(acr.getConditions().getMap()));
            writer.writeEnd();
        }
        writer.writeEnd();
    }

    private static String[] toStrings(Map<String, ?> props) {
        String[] ss = new String[props.size()];
        int i = 0;
        for (Map.Entry<String, ?> entry : props.entrySet())
            ss[i++] = entry.getKey() + '=' + entry.getValue();
        return ss;
    }

    private void writeArchiveAttributeCoercion(
            JsonWriter writer, Collection<ArchiveAttributeCoercion> archiveAttributeCoercionList) {
        writer.writeStartArray("dcmArchiveAttributeCoercion");
        for (ArchiveAttributeCoercion aac : archiveAttributeCoercionList) {
            writer.writeStartObject();
            writer.writeNotNullOrDef("cn", aac.getCommonName(), null);
            writer.writeNotNullOrDef("dcmDIMSE", aac.getDIMSE(), null);
            writer.writeNotNullOrDef("dicomTransferRole", aac.getRole(), null);
            writer.writeNotDef("dcmRulePriority", aac.getPriority(), 0);
            writer.writeNotEmpty("dcmAETitle", aac.getAETitles());
            writer.writeNotEmpty("dcmHostname", aac.getHostNames());
            writer.writeNotEmpty("dcmSOPClass", aac.getSOPClasses());
            writer.writeNotDef("dcmRetrieveAsReceived", aac.isRetrieveAsReceived(), false);
            writer.writeNotEmpty("dcmDeIdentification", aac.getDeIdentification());
            writer.writeNotDef("dcmNoKeywords", aac.isNoKeywords(), false);
            writer.writeNotNullOrDef("dcmURI", aac.getXSLTStylesheetURI(), null);
            writer.writeNotNullOrDef("dcmLeadingCFindSCP", aac.getLeadingCFindSCP(), null);
            writer.writeNotNullOrDef("dcmMergeMWLMatchingKey", aac.getMergeMWLMatchingKey(), null);
            writer.writeNotNullOrDef("dcmMergeMWLTemplateURI", aac.getMergeMWLTemplateURI(), null);
            writer.writeNotNullOrDef("dcmNullifyIssuerOfPatientID", aac.getNullifyIssuerOfPatientID(), null);
            writer.writeNotEmpty("dcmIssuerOfPatientID", aac.getIssuerOfPatientIDs());
            writer.writeNotNullOrDef("dcmAttributeUpdatePolicy",
                    aac.getAttributeUpdatePolicy(), Attributes.UpdatePolicy.MERGE);
            writer.writeNotEmpty("dcmNullifyTag", TagUtils.toHexStrings(aac.getNullifyTags()));
            writer.writeNotNullOrDef("dcmSupplementFromDeviceName", deviceNameOf(aac.getSupplementFromDevice()), null);
            writer.writeNotNullOrDef("dcmIssuerOfPatientIDFormat", aac.getIssuerOfPatientIDFormat(), null);
            writer.writeNotDef("dcmTrimISO2022CharacterSet", aac.isTrimISO2022CharacterSet(), false);
            writer.writeNotNullOrDef("dcmUseCallingAETitleAs", aac.getUseCallingAETitleAs(), null);
            writer.writeEnd();
        }
        writer.writeEnd();
    }

    private static String deviceNameOf(Device device) {
        return device != null ? device.getDeviceName() : null;
    }

    private void writeRejectionNote(JsonWriter writer, Collection<RejectionNote> rejectionNoteList) {
        writer.writeStartArray("dcmRejectionNote");
        for (RejectionNote rn : rejectionNoteList) {
            writer.writeStartObject();
            writer.writeNotNullOrDef("dcmRejectionNoteLabel", rn.getRejectionNoteLabel(), null);
            writer.writeNotNullOrDef("dcmRejectionNoteType", rn.getRejectionNoteType(), null);
            writer.writeNotNullOrDef("dcmRejectionNoteCode", rn.getRejectionNoteCode(), null);
            writer.writeNotNullOrDef("dcmAcceptPreviousRejectedInstance",
                    rn.getAcceptPreviousRejectedInstance(), RejectionNote.AcceptPreviousRejectedInstance.REJECT);
            writer.writeNotEmpty("dcmOverwritePreviousRejection", rn.getOverwritePreviousRejection());
            writer.writeNotNullOrDef("dcmAcceptRejectionBeforeStorage", rn.getAcceptRejectionBeforeStorage(), null);
            writer.writeNotNullOrDef("dcmDeleteRejectedInstanceDelay", rn.getDeleteRejectedInstanceDelay(), null);
            writer.writeNotNullOrDef("dcmDeleteRejectionNoteDelay", rn.getDeleteRejectionNoteDelay(), null);
            writer.writeEnd();
        }
        writer.writeEnd();
    }

    private void writeStudyRetentionPolicies(
            JsonWriter writer, Collection<StudyRetentionPolicy> studyRetentionPolicies) {
        writer.writeStartArray("dcmStudyRetentionPolicy");
        for (StudyRetentionPolicy srp : studyRetentionPolicies) {
            writer.writeStartObject();
            writer.writeNotNullOrDef("cn", srp.getCommonName(), null);
            writer.writeNotNullOrDef("dcmRetentionPeriod", srp.getRetentionPeriod(), null);
            writer.writeNotDef("dcmRulePriority", srp.getPriority(), 0);
            writer.writeNotEmpty("dcmProperty", toStrings(srp.getConditions().getMap()));
            writer.writeNotDef("dcmExpireSeriesIndividually", srp.isExpireSeriesIndividually(), false);
            writer.writeNotDef("dcmStartRetentionPeriodOnStudyDate", srp.isStartRetentionPeriodOnStudyDate(), false);
            writer.writeNotNullOrDef("dcmExporterID", srp.getExporterID(), null);
            writer.writeNotDef("dcmFreezeExpirationDate", srp.isFreezeExpirationDate(), false);
            writer.writeNotDef("dcmRevokeExpiration", srp.isRevokeExpiration(), false);
            writer.writeEnd();
        }
        writer.writeEnd();
    }

    static void writeHL7StudyRetentionPolicies(
            JsonWriter writer, Collection<HL7StudyRetentionPolicy> studyRetentionPolicies) {
        writer.writeStartArray("hl7StudyRetentionPolicy");
        for (HL7StudyRetentionPolicy srp : studyRetentionPolicies) {
            writer.writeStartObject();
            writer.writeNotNullOrDef("cn", srp.getCommonName(), null);
            writer.writeNotNullOrDef("dicomAETitle", srp.getAETitle(), null);
            writer.writeNotNullOrDef("dcmRetentionPeriod", srp.getMinRetentionPeriod(), null);
            writer.writeNotNullOrDef("dcmMaxRetentionPeriod", srp.getMaxRetentionPeriod(), null);
            writer.writeNotDef("dcmRulePriority", srp.getPriority(), 0);
            writer.writeNotEmpty("dcmProperty", toStrings(srp.getConditions().getMap()));
            writer.writeNotDef("dcmStartRetentionPeriodOnStudyDate", srp.isStartRetentionPeriodOnStudyDate(), false);
            writer.writeNotNullOrDef("dcmExporterID", srp.getExporterID(), null);
            writer.writeNotDef("dcmFreezeExpirationDate", srp.isFreezeExpirationDate(), false);
            writer.writeNotDef("dcmRevokeExpiration", srp.isRevokeExpiration(), false);
            writer.writeEnd();
        }
        writer.writeEnd();
    }

    static void writeHL7ForwardRules(JsonWriter writer, Collection<HL7ForwardRule> rules) {
        writer.writeStartArray("hl7ForwardRule");
        for (HL7ForwardRule rule : rules) {
            writer.writeStartObject();
            writer.writeNotNullOrDef("cn", rule.getCommonName(), null);
            writer.writeNotEmpty("hl7FwdApplicationName", rule.getDestinations());
            writer.writeNotEmpty("dcmProperty", toStrings(rule.getConditions().getMap()));
            writer.writeEnd();
        }
        writer.writeEnd();
    }

    static void writeScheduledStations(JsonWriter writer, Collection<HL7OrderScheduledStation> stations) {
        writer.writeStartArray("hl7OrderScheduledStation");
        for (HL7OrderScheduledStation station : stations) {
            writer.writeStartObject();
            writer.writeNotNullOrDef("cn", station.getCommonName(), null);
            writer.writeNotNullOrDef("hl7OrderScheduledStationDeviceName", deviceNameOf(station.getDevice()), null);
            writer.writeNotDef("dcmRulePriority", station.getPriority(), 0);
            writer.writeNotEmpty("dcmProperty", toStrings(station.getConditions().getMap()));
            writer.writeEnd();
        }
        writer.writeEnd();
    }

    static void writeHL7OrderSPSStatus(JsonWriter writer, Map<SPSStatus, HL7OrderSPSStatus> hl7OrderSPSStatusMap) {
        writer.writeStartArray("hl7OrderSPSStatus");
        for (Map.Entry<SPSStatus, HL7OrderSPSStatus> entry : hl7OrderSPSStatusMap.entrySet()) {
            writer.writeStartObject();
            writer.writeNotNullOrDef("dcmSPSStatus", entry.getKey(), null);
            writer.writeNotEmpty("hl7OrderControlStatus", entry.getValue().getOrderControlStatusCodes());
            writer.writeEnd();
        }
        writer.writeEnd();
    }

    private static void writeRSForwardRules(JsonWriter writer, Collection<RSForwardRule> rules) {
        writer.writeStartArray("dcmRSForwardRule");
        for (RSForwardRule rule : rules) {
            writer.writeStartObject();
            writer.writeNotNullOrDef("cn", rule.getCommonName(), null);
            writer.writeNotNullOrDef("dcmWebAppName", rule.getWebAppName(), null);
            writer.writeNotEmpty("dcmRSOperation", rule.getRSOperations());
            writer.writeNotDef("dcmTLSAllowAnyHostname", rule.isTlsAllowAnyHostname(), false);
            writer.writeNotDef("dcmTLSDisableTrustManager", rule.isTlsDisableTrustManager(), false);
            writer.writeNotNullOrDef("dcmURIPattern", rule.getRequestURLPattern(), null);
            writer.writeEnd();
        }
        writer.writeEnd();
    }

    private static void writeKeycloakServers(JsonWriter writer, Collection<KeycloakServer> keycloakServers) {
        writer.writeStartArray("dcmKeycloakServer");
        for (KeycloakServer keycloakServer : keycloakServers) {
            writer.writeStartObject();
            writer.writeNotNullOrDef("dcmKeycloakServerID", keycloakServer.getKeycloakServerID(), null);
            writer.writeNotNullOrDef("dcmURI", keycloakServer.getServerURL(), null);
            writer.writeNotNullOrDef("dcmKeycloakRealm", keycloakServer.getRealm(), null);
            writer.writeNotNullOrDef("dcmKeycloakClientID", keycloakServer.getClientID(), null);
            writer.writeNotNullOrDef("dcmKeycloakGrantType", keycloakServer.getGrantType(), null);
            writer.writeNotNullOrDef("dcmKeycloakClientSecret", keycloakServer.getClientSecret(), null);
            writer.writeNotDef("dcmTLSAllowAnyHostname", keycloakServer.isTlsAllowAnyHostname(), false);
            writer.writeNotDef("dcmTLSDisableTrustManager", keycloakServer.isTlsDisableTrustManager(), false);
            writer.writeNotNullOrDef("uid", keycloakServer.getUserID(), null);
            writer.writeNotNullOrDef("userPassword", keycloakServer.getPassword(), null);
            writer.writeEnd();
        }
        writer.writeEnd();
    }

    private static void writeMetricsDescriptors(JsonWriter writer, Collection<MetricsDescriptor> metricsDescriptors) {
        writer.writeStartArray("dcmMetrics");
        for (MetricsDescriptor metricsDescriptor : metricsDescriptors) {
            writer.writeStartObject();
            writer.writeNotNullOrDef("dcmMetricsName", metricsDescriptor.getMetricsName(), null);
            writer.writeNotNullOrDef("dicomDescription", metricsDescriptor.getDescription(), null);
            writer.writeNotDef("dcmMetricsRetentionPeriod", metricsDescriptor.getRetentionPeriod(), 60);
            writer.writeNotNullOrDef("dcmUnit", metricsDescriptor.getUnit(), null);
            writer.writeEnd();
        }
        writer.writeEnd();
    }

    private void writeIDGenerators(JsonWriter writer, ArchiveDeviceExtension arcDev) {
        writer.writeStartArray("dcmIDGenerator");
        for (IDGenerator generator : arcDev.getIDGenerators().values()) {
            writer.writeStartObject();
            writer.writeNotNullOrDef("dcmIDGeneratorName", generator.getName(), null);
            writer.writeNotNullOrDef("dcmIDGeneratorFormat", generator.getFormat(), null);
            writer.writeNotDef("dcmIDGeneratorInitialValue", generator.getInitialValue(), 1);
            writer.writeEnd();
        }
        writer.writeEnd();
    }

    @Override
    protected void storeTo(ApplicationEntity ae, JsonWriter writer) {
        ArchiveAEExtension arcAE = ae.getAEExtension(ArchiveAEExtension.class);
        if (arcAE == null)
            return;

        writer.writeStartObject("dcmArchiveNetworkAE");
        writer.writeNotEmpty("dcmObjectStorageID", arcAE.getObjectStorageIDs());
        writer.writeNotDef("dcmObjectStorageCount", arcAE.getObjectStorageCount(), 1);
        writer.writeNotEmpty("dcmMetadataStorageID", arcAE.getMetadataStorageIDs());
        writer.writeNotNullOrDef("dcmBulkDataDescriptorID", arcAE.getBulkDataDescriptorID(), null);
        writer.writeNotNullOrDef("dcmSeriesMetadataDelay", arcAE.getSeriesMetadataDelay(), null);
        writer.writeNotNullOrDef("dcmPurgeInstanceRecordsDelay", arcAE.getPurgeInstanceRecordsDelay(), null);
        writer.writeNotNullOrDef("dcmStoreAccessControlID", arcAE.getStoreAccessControlID(), null);
        writer.writeNotEmpty("dcmAccessControlID", arcAE.getAccessControlIDs());
        writer.writeNotNullOrDef("dcmOverwritePolicy", arcAE.getOverwritePolicy(), null);
        writer.writeNotNullOrDef("dcmQueryRetrieveViewID", arcAE.getQueryRetrieveViewID(), null);
        writer.writeNotNullOrDef("dcmBulkDataSpoolDirectory", arcAE.getBulkDataSpoolDirectory(), null);
        writer.writeNotEmpty("dcmHideSPSWithStatusFromMWL", arcAE.getHideSPSWithStatusFromMWL());
        writer.writeNotNull("dcmPersonNameComponentOrderInsensitiveMatching",
                arcAE.getPersonNameComponentOrderInsensitiveMatching());
        writer.writeNotNull("dcmSendPendingCGet", arcAE.getSendPendingCGet());
        writer.writeNotNullOrDef("dcmSendPendingCMoveInterval", arcAE.getSendPendingCMoveInterval(), null);
        writer.writeNotNullOrDef("dcmWadoZIPEntryNameFormat", arcAE.getWadoZIPEntryNameFormat(), null);
        writer.writeNotNullOrDef("dcmWadoSR2HtmlTemplateURI", arcAE.getWadoSR2HtmlTemplateURI(), null);
        writer.writeNotNullOrDef("dcmWadoSR2TextTemplateURI", arcAE.getWadoSR2TextTemplateURI(), null);
        writer.writeNotNullOrDef("dcmWadoCDA2HtmlTemplateURI", arcAE.getWadoCDA2HtmlTemplateURI(), null);
        writer.writeNotNull("dcmQueryMaxNumberOfResults", arcAE.getQueryMaxNumberOfResults());
        writer.writeNotNull("dcmQidoMaxNumberOfResults", arcAE.getQidoMaxNumberOfResults());
        writer.writeNotEmpty("dcmFwdMppsDestination", arcAE.getMppsForwardDestinations());
        writer.writeNotEmpty("dcmIanDestination", arcAE.getIanDestinations());
        writer.writeNotNullOrDef("dcmIanDelay", arcAE.getIanDelay(), null);
        writer.writeNotNullOrDef("dcmIanTimeout", arcAE.getIanTimeout(), null);
        writer.writeNotNull("dcmIanOnTimeout", arcAE.getIanOnTimeout());
        writer.writeNotNullOrDef("dcmSpanningCFindSCP", arcAE.getSpanningCFindSCP(), null);
        writer.writeNotEmpty("dcmSpanningCFindSCPRetrieveAET", arcAE.getSpanningCFindSCPRetrieveAETitles());
        writer.writeNotNullOrDef("dcmSpanningCFindSCPPolicy", arcAE.getSpanningCFindSCPPolicy(), null);
        writer.writeNotNullOrDef("dcmFallbackCMoveSCP", arcAE.getFallbackCMoveSCP(), null);
        writer.writeNotNullOrDef("dcmFallbackCMoveSCPDestination", arcAE.getFallbackCMoveSCPDestination(), null);
        writer.writeNotNullOrDef("dcmFallbackCMoveSCPCallingAET", arcAE.getFallbackCMoveSCPCallingAET(), null);
        writer.writeNotNull("dcmFallbackCMoveSCPRetries", arcAE.getFallbackCMoveSCPRetries());
        writer.writeNotNullOrDef("dcmFallbackCMoveSCPLeadingCFindSCP", arcAE.getFallbackCMoveSCPLeadingCFindSCP(), null);
        writer.writeNotNullOrDef("dcmAltCMoveSCP", arcAE.getAlternativeCMoveSCP(), null);
        writer.writeNotNullOrDef("dcmFallbackCMoveSCPStudyOlderThan", arcAE.getFallbackCMoveSCPStudyOlderThan(), null);
        writer.writeNotNullOrDef("dcmStorePermissionServiceURL", arcAE.getStorePermissionServiceURL(), null);
        writer.writeNotNullOrDef("dcmStorePermissionServiceResponse",
                arcAE.getStorePermissionServiceResponse(), null);
        writer.writeNotNullOrDef("dcmStorePermissionServiceResponsePattern",
                arcAE.getStorePermissionServiceResponsePattern(), null);
        writer.writeNotNullOrDef("dcmAllowRejectionForDataRetentionPolicyExpired",
                arcAE.getAllowRejectionForDataRetentionPolicyExpired(), null);
        writer.writeNotEmpty("dcmAcceptedUserRole", arcAE.getAcceptedUserRoles());
        writer.writeNotNullOrDef("dcmAcceptMissingPatientID", arcAE.getAcceptMissingPatientID(), null);
        writer.writeNotNullOrDef("dcmAllowDeleteStudyPermanently", arcAE.getAllowDeleteStudyPermanently(), null);
        writer.writeNotNullOrDef("dcmAllowDeletePatient", arcAE.getAllowDeletePatient(), null);
        writer.writeNotNullOrDef("dcmStorePermissionServiceExpirationDatePattern",
                arcAE.getStorePermissionServiceExpirationDatePattern(), null);
        writer.writeNotNullOrDef("dcmDefaultCharacterSet", arcAE.getDefaultCharacterSet(), null);
        writer.writeNotNullOrDef("dcmUPSWorklistLabel", arcAE.getUPSWorklistLabel(), null);
        writer.writeNotEmpty("dcmUPSEventSCU", arcAE.getUPSEventSCUs());
        writer.writeNotDef("dcmUPSEventSCUKeepAlive", arcAE.getUPSEventSCUKeepAlive(), 0);
        writer.writeNotNullOrDef("dcmStorePermissionServiceErrorCommentPattern",
                arcAE.getStorePermissionServiceErrorCommentPattern(), null);
        writer.writeNotNullOrDef("dcmStorePermissionServiceErrorCodePattern",
                arcAE.getStorePermissionServiceErrorCodePattern(), null);
        writer.writeNotEmpty("dcmReturnRetrieveAET", arcAE.getReturnRetrieveAETitles());
        writer.writeNotNullOrDef("dcmExternalRetrieveAEDestination",
                arcAE.getExternalRetrieveAEDestination(), null);
        writer.writeNotEmpty("dcmAcceptedMoveDestination", arcAE.getAcceptedMoveDestinations());
        writer.writeNotNull("dcmValidateCallingAEHostname", arcAE.getValidateCallingAEHostname());
        writer.writeNotNullOrDef("hl7PSUSendingApplication", arcAE.getHL7PSUSendingApplication(), null);
        writer.writeNotEmpty("hl7PSUReceivingApplication", arcAE.getHL7PSUReceivingApplications());
        writer.writeNotNullOrDef("hl7PSUDelay", arcAE.getHL7PSUDelay(), null);
        writer.writeNotNullOrDef("hl7PSUTimeout", arcAE.getHL7PSUTimeout(), null);
        writer.writeNotNull("hl7PSUOnTimeout", arcAE.getHL7PSUOnTimeout());
        writer.writeNotNull("hl7PSUMWL", arcAE.getHL7PSUMWL());
        writer.writeNotNullOrDef("dcmAcceptConflictingPatientID", arcAE.getAcceptConflictingPatientID(), null);
        writer.writeNotNullOrDef("dcmCopyMoveUpdatePolicy", arcAE.getCopyMoveUpdatePolicy(), null);
        writer.writeNotNullOrDef("dcmLinkMWLEntryUpdatePolicy", arcAE.getLinkMWLEntryUpdatePolicy(), null);
        writer.writeNotNullOrDef("dcmStorageVerificationPolicy", arcAE.getStorageVerificationPolicy(), null);
        writer.writeNotNull("dcmStorageVerificationUpdateLocationStatus",
                arcAE.getStorageVerificationUpdateLocationStatus());
        writer.writeNotEmpty("dcmStorageVerificationStorageID", arcAE.getStorageVerificationStorageIDs());
        writer.writeNotNullOrDef("dcmStorageVerificationInitialDelay", arcAE.getStorageVerificationInitialDelay(), null);
        writer.writeNotNullOrDef("dcmInvokeImageDisplayPatientURL", arcAE.getInvokeImageDisplayPatientURL(), null);
        writer.writeNotNullOrDef("dcmInvokeImageDisplayStudyURL", arcAE.getInvokeImageDisplayStudyURL(), null);
        writer.writeNotNull("dcmUpdateLocationStatusOnRetrieve",
                arcAE.getUpdateLocationStatusOnRetrieve());
        writer.writeNotNull("dcmStorageVerificationOnRetrieve",
                arcAE.getStorageVerificationOnRetrieve());
        writer.writeNotEmpty("dcmRejectConflictingPatientAttribute",
                TagUtils.toHexStrings(arcAE.getRejectConflictingPatientAttribute()));
        writer.writeNotNull("dcmRelationalQueryNegotiationLenient", arcAE.getRelationalQueryNegotiationLenient());
        writer.writeNotNull("dcmRelationalRetrieveNegotiationLenient", arcAE.getRelationalRetrieveNegotiationLenient());
        writer.writeNotNull("dcmStowRetiredTransferSyntax", arcAE.getStowRetiredTransferSyntax());
        writer.writeNotNull("dcmStowExcludeAPPMarkers", arcAE.getStowExcludeAPPMarkers());
        writer.writeNotNullOrDef("dcmWadoThumbnailViewport", arcAE.getWadoThumbnailViewPort(), null);
        writer.writeNotNullOrDef("dcmRestrictRetrieveAccordingTransferCapabilities",
                arcAE.getRestrictRetrieveAccordingTransferCapabilities(), null);
        writeExportRule(writer, arcAE.getExportRules());
        writeExportPrefetchRules(writer, arcAE.getExportPriorsRules());
        writeArchiveCompressionRules(writer, arcAE.getCompressionRules());
        writeStoreAccessControlIDRules(writer, arcAE.getStoreAccessControlIDRules());
        writeArchiveAttributeCoercion(writer, arcAE.getAttributeCoercions());
        writeStudyRetentionPolicies(writer, arcAE.getStudyRetentionPolicies());
        writeRSForwardRules(writer, arcAE.getRSForwardRules());
        writer.writeEnd();
    }

    @Override
    public boolean loadDeviceExtension(Device device, JsonReader reader, ConfigurationDelegate config) {
        if (!reader.getString().equals("dcmArchiveDevice"))
            return false;

        reader.next();
        reader.expect(JsonParser.Event.START_OBJECT);
        ArchiveDeviceExtension arcDev = new ArchiveDeviceExtension();
        loadFrom(arcDev, reader, config);
        device.addDeviceExtension(arcDev);
        reader.expect(JsonParser.Event.END_OBJECT);
        return true;
    }

    private void loadFrom(ArchiveDeviceExtension arcDev, JsonReader reader, ConfigurationDelegate config) {
        while (reader.next() == JsonParser.Event.KEY_NAME) {
            switch (reader.getString()) {
                case "dcmFuzzyAlgorithmClass":
                    arcDev.setFuzzyAlgorithmClass(reader.stringValue());
                    break;
                case "dcmBulkDataDescriptorID":
                    arcDev.setBulkDataDescriptorID(reader.stringValue());
                    break;
                case "dcmSeriesMetadataStorageID":
                    arcDev.setSeriesMetadataStorageIDs(reader.stringArray());
                    break;
                case "dcmSeriesMetadataDelay":
                    arcDev.setSeriesMetadataDelay(Duration.valueOf(reader.stringValue()));
                    break;
                case "dcmSeriesMetadataPollingInterval":
                    arcDev.setSeriesMetadataPollingInterval(Duration.valueOf(reader.stringValue()));
                    break;
                case "dcmSeriesMetadataFetchSize":
                    arcDev.setSeriesMetadataFetchSize(reader.intValue());
                    break;
                case "dcmSeriesMetadataThreads":
                    arcDev.setSeriesMetadataThreads(reader.intValue());
                    break;
                case "dcmSeriesMetadataMaxRetries":
                    arcDev.setSeriesMetadataMaxRetries(reader.intValue());
                    break;
                case "dcmSeriesMetadataRetryInterval":
                    arcDev.setSeriesMetadataRetryInterval(Duration.valueOf(reader.stringValue()));
                    break;
                case "dcmPurgeInstanceRecords":
                    arcDev.setPurgeInstanceRecords(reader.booleanValue());
                    break;
                case "dcmPurgeInstanceRecordsDelay":
                    arcDev.setPurgeInstanceRecordsDelay(Duration.valueOf(reader.stringValue()));
                    break;
                case "dcmPurgeInstanceRecordsPollingInterval":
                    arcDev.setPurgeInstanceRecordsPollingInterval(Duration.valueOf(reader.stringValue()));
                    break;
                case "dcmPurgeInstanceRecordsFetchSize":
                    arcDev.setPurgeInstanceRecordsFetchSize(reader.intValue());
                    break;
                case "dcmDeleteUPSPollingInterval":
                    arcDev.setDeleteUPSPollingInterval(Duration.valueOf(reader.stringValue()));
                    break;
                case "dcmDeleteUPSFetchSize":
                    arcDev.setDeleteUPSFetchSize(reader.intValue());
                    break;
                case "dcmDeleteUPSCompletedDelay":
                    arcDev.setDeleteUPSCompletedDelay(Duration.valueOf(reader.stringValue()));
                    break;
                case "dcmDeleteUPSCanceledDelay":
                    arcDev.setDeleteUPSCanceledDelay(Duration.valueOf(reader.stringValue()));
                    break;
                case "dcmOverwritePolicy":
                    arcDev.setOverwritePolicy(OverwritePolicy.valueOf(reader.stringValue()));
                    break;
                case "dcmBulkDataSpoolDirectory":
                    arcDev.setBulkDataSpoolDirectory(reader.stringValue());
                    break;
                case "dcmHideSPSWithStatusFromMWL":
                    arcDev.setHideSPSWithStatusFrom(reader.enumArray(SPSStatus.class));
                    break;
                case "dcmPersonNameComponentOrderInsensitiveMatching":
                    arcDev.setPersonNameComponentOrderInsensitiveMatching(reader.booleanValue());
                    break;
                case "dcmSendPendingCGet":
                    arcDev.setSendPendingCGet(reader.booleanValue());
                    break;
                case "dcmSendPendingCMoveInterval":
                    arcDev.setSendPendingCMoveInterval(Duration.valueOf(reader.stringValue()));
                    break;
                case "dcmWadoSupportedSRClasses":
                    arcDev.setWadoSupportedSRClasses(reader.stringArray());
                    break;
                case "dcmWadoSupportedPRClasses":
                    arcDev.setWadoSupportedPRClasses(reader.stringArray());
                    break;
                case "dcmWadoZIPEntryNameFormat":
                    arcDev.setWadoZIPEntryNameFormat(reader.stringValue());
                    break;
                case "dcmWadoSR2HtmlTemplateURI":
                    arcDev.setWadoSR2HtmlTemplateURI(reader.stringValue());
                    break;
                case "dcmWadoSR2TextTemplateURI":
                    arcDev.setWadoSR2TextTemplateURI(reader.stringValue());
                    break;
                case "dcmWadoCDA2HtmlTemplateURI":
                    arcDev.setWadoCDA2HtmlTemplateURI(reader.stringValue());
                    break;
                case "dcmQueryFetchSize":
                    arcDev.setQueryFetchSize(reader.intValue());
                    break;
                case "dcmQueryMaxNumberOfResults":
                    arcDev.setQueryMaxNumberOfResults(reader.intValue());
                    break;
                case "dcmQidoMaxNumberOfResults":
                    arcDev.setQidoMaxNumberOfResults(reader.intValue());
                    break;
                case "dcmFwdMppsDestination":
                    arcDev.setMppsForwardDestinations(reader.stringArray());
                    break;
                case "dcmIanDestination":
                    arcDev.setIanDestinations(reader.stringArray());
                    break;
                case "dcmIanDelay":
                    arcDev.setIanDelay(Duration.valueOf(reader.stringValue()));
                    break;
                case "dcmIanTimeout":
                    arcDev.setIanTimeout(Duration.valueOf(reader.stringValue()));
                    break;
                case "dcmIanOnTimeout":
                    arcDev.setIanOnTimeout(reader.booleanValue());
                    break;
                case "dcmIanTaskPollingInterval":
                    arcDev.setIanTaskPollingInterval(Duration.valueOf(reader.stringValue()));
                    break;
                case "dcmIanTaskFetchSize":
                    arcDev.setIanTaskFetchSize(reader.intValue());
                    break;
                case "dcmSpanningCFindSCP":
                    arcDev.setSpanningCFindSCP(reader.stringValue());
                    break;
                case "dcmSpanningCFindSCPRetrieveAET":
                    arcDev.setSpanningCFindSCPRetrieveAETitles(reader.stringArray());
                    break;
                case "dcmSpanningCFindSCPPolicy":
                    arcDev.setSpanningCFindSCPPolicy(SpanningCFindSCPPolicy.valueOf(reader.stringValue()));
                    break;
                case "dcmFallbackCMoveSCP":
                    arcDev.setFallbackCMoveSCP(reader.stringValue());
                    break;
                case "dcmFallbackCMoveSCPDestination":
                    arcDev.setFallbackCMoveSCPDestination(reader.stringValue());
                    break;
                case "dcmFallbackCMoveSCPCallingAET":
                    arcDev.setFallbackCMoveSCPCallingAET(reader.stringValue());
                    break;
                case "dcmFallbackCMoveSCPRetries":
                    arcDev.setFallbackCMoveSCPRetries(reader.intValue());
                    break;
                case "dcmFallbackCMoveSCPLeadingCFindSCP":
                    arcDev.setFallbackCMoveSCPLeadingCFindSCP(reader.stringValue());
                    break;
                case "dcmAltCMoveSCP":
                    arcDev.setAlternativeCMoveSCP(reader.stringValue());
                    break;
                case "dcmExportTaskPollingInterval":
                    arcDev.setExportTaskPollingInterval(Duration.valueOf(reader.stringValue()));
                    break;
                case "dcmExportTaskFetchSize":
                    arcDev.setExportTaskFetchSize(reader.intValue());
                    break;
                case "dcmPurgeStoragePollingInterval":
                    arcDev.setPurgeStoragePollingInterval(Duration.valueOf(reader.stringValue()));
                    break;
                case "dcmPurgeStorageFetchSize":
                    arcDev.setPurgeStorageFetchSize(reader.intValue());
                    break;
                case "dcmFailedToDeletePollingInterval":
                    arcDev.setFailedToDeletePollingInterval(Duration.valueOf(reader.stringValue()));
                    break;
                case "dcmFailedToDeleteFetchSize":
                    arcDev.setFailedToDeleteFetchSize(reader.intValue());
                    break;
                case "dcmDeleteStudyBatchSize":
                    arcDev.setDeleteStudyBatchSize(reader.intValue());
                    break;
                case "dcmDeletePatientOnDeleteLastStudy":
                    arcDev.setDeletePatientOnDeleteLastStudy(reader.booleanValue());
                    break;
                case "dcmDeleteRejectedPollingInterval":
                    arcDev.setDeleteRejectedPollingInterval(Duration.valueOf(reader.stringValue()));
                    break;
                case "dcmDeleteRejectedFetchSize":
                    arcDev.setDeleteRejectedFetchSize(reader.intValue());
                    break;
                case "dcmMaxAccessTimeStaleness":
                    arcDev.setMaxAccessTimeStaleness(Duration.valueOf(reader.stringValue()));
                    break;
                case "dcmAECacheStaleTimeout":
                    arcDev.setAECacheStaleTimeout(Duration.valueOf(reader.stringValue()));
                    break;
                case "dcmLeadingCFindSCPQueryCacheStaleTimeout":
                    arcDev.setLeadingCFindSCPQueryCacheStaleTimeout(Duration.valueOf(reader.stringValue()));
                    break;
                case "dcmLeadingCFindSCPQueryCacheSize":
                    arcDev.setLeadingCFindSCPQueryCacheSize(reader.intValue());
                    break;
                case "dcmAuditSpoolDirectory":
                    arcDev.setAuditSpoolDirectory(reader.stringValue());
                    break;
                case "dcmAuditPollingInterval":
                    arcDev.setAuditPollingInterval(Duration.valueOf(reader.stringValue()));
                    break;
                case "dcmAuditAggregateDuration":
                    arcDev.setAuditAggregateDuration(Duration.valueOf(reader.stringValue()));
                    break;
                case "dcmStowSpoolDirectory":
                    arcDev.setStowSpoolDirectory(reader.stringValue());
                    break;
                case "hl7PatientUpdateTemplateURI":
                    arcDev.setPatientUpdateTemplateURI(reader.stringValue());
                    break;
                case "hl7ImportReportTemplateURI":
                    arcDev.setImportReportTemplateURI(reader.stringValue());
                    break;
                case "hl7ImportReportTemplateParam":
                    arcDev.setImportReportTemplateParams(reader.stringArray());
                    break;
                case "hl7ScheduleProcedureTemplateURI":
                    arcDev.setScheduleProcedureTemplateURI(reader.stringValue());
                    break;
                case "hl7OutgoingPatientUpdateTemplateURI":
                    arcDev.setOutgoingPatientUpdateTemplateURI(reader.stringValue());
                    break;
                case "hl7LogFilePattern":
                    arcDev.setHL7LogFilePattern(reader.stringValue());
                    break;
                case "hl7ErrorLogFilePattern":
                    arcDev.setHL7ErrorLogFilePattern(reader.stringValue());
                    break;
                case "dcmUnzipVendorDataToURI":
                    arcDev.setUnzipVendorDataToURI(reader.stringValue());
                    break;
                case "dcmPurgeQueueMessagePollingInterval":
                    arcDev.setPurgeQueueMessagePollingInterval(Duration.valueOf(reader.stringValue()));
                    break;
                case "dcmWadoSpoolDirectory":
                    arcDev.setWadoSpoolDirectory(reader.stringValue());
                    break;
                case "dcmRejectExpiredStudiesPollingInterval":
                    arcDev.setRejectExpiredStudiesPollingInterval(Duration.valueOf(reader.stringValue()));
                    break;
                case "dcmRejectExpiredStudiesSchedule":
                    arcDev.setRejectExpiredStudiesSchedules(ScheduleExpression.valuesOf(reader.stringArray()));
                    break;
                case "dcmRejectExpiredStudiesFetchSize":
                    arcDev.setRejectExpiredStudiesFetchSize(reader.intValue());
                    break;
                case "dcmRejectExpiredSeriesFetchSize":
                    arcDev.setRejectExpiredSeriesFetchSize(reader.intValue());
                    break;
                case "dcmRejectExpiredStudiesAETitle":
                    arcDev.setRejectExpiredStudiesAETitle(reader.stringValue());
                    break;
                case "dcmFallbackCMoveSCPStudyOlderThan":
                    arcDev.setFallbackCMoveSCPStudyOlderThan(reader.stringValue());
                    break;
                case "dcmStorePermissionServiceURL":
                    arcDev.setStorePermissionServiceURL(reader.stringValue());
                    break;
                case "dcmStorePermissionServiceResponse":
                    arcDev.setStorePermissionServiceResponse(reader.stringValue());
                    break;
                case "dcmStorePermissionServiceResponsePattern":
                    arcDev.setStorePermissionServiceResponsePattern(Pattern.compile(reader.stringValue()));
                    break;
                case "dcmStorePermissionCacheStaleTimeout":
                    arcDev.setStorePermissionCacheStaleTimeout(Duration.valueOf(reader.stringValue()));
                    break;
                case "dcmStorePermissionCacheSize":
                    arcDev.setStorePermissionCacheSize(reader.intValue());
                    break;
                case "dcmMergeMWLCacheStaleTimeout":
                    arcDev.setMergeMWLCacheStaleTimeout(Duration.valueOf(reader.stringValue()));
                    break;
                case "dcmMergeMWLCacheSize":
                    arcDev.setMergeMWLCacheSize(reader.intValue());
                    break;
                case "dcmStoreUpdateDBMaxRetries":
                    arcDev.setStoreUpdateDBMaxRetries(reader.intValue());
                    break;
                case "dcmStoreUpdateDBMinRetryDelay":
                    arcDev.setStoreUpdateDBMinRetryDelay(reader.intValue());
                    break;
                case "dcmStoreUpdateDBMaxRetryDelay":
                    arcDev.setStoreUpdateDBMaxRetryDelay(reader.intValue());
                    break;
                case "dcmAllowRejectionForDataRetentionPolicyExpired":
                    arcDev.setAllowRejectionForDataRetentionPolicyExpired(
                            AllowRejectionForDataRetentionPolicyExpired.valueOf(reader.stringValue()));
                    break;
                case "dcmAcceptMissingPatientID":
                    arcDev.setAcceptMissingPatientID(AcceptMissingPatientID.valueOf(reader.stringValue()));
                    break;
                case "dcmAllowDeleteStudyPermanently":
                    arcDev.setAllowDeleteStudyPermanently(AllowDeleteStudyPermanently.valueOf(reader.stringValue()));
                    break;
                case "dcmAllowDeletePatient":
                    arcDev.setAllowDeletePatient(AllowDeletePatient.valueOf(reader.stringValue()));
                    break;
                case "dcmStorePermissionServiceExpirationDatePattern":
                    arcDev.setStorePermissionServiceExpirationDatePattern(Pattern.compile(reader.stringValue()));
                    break;
                case "dcmShowPatientInfoInSystemLog":
                    arcDev.setShowPatientInfoInSystemLog(ShowPatientInfo.valueOf(reader.stringValue()));
                    break;
                case "dcmShowPatientInfoInAuditLog":
                    arcDev.setShowPatientInfoInAuditLog(ShowPatientInfo.valueOf(reader.stringValue()));
                    break;
                case "dcmPurgeStgCmtCompletedDelay":
                    arcDev.setPurgeStgCmtCompletedDelay(Duration.valueOf(reader.stringValue()));
                    break;
                case "dcmPurgeStgCmtPollingInterval":
                    arcDev.setPurgeStgCmtPollingInterval(Duration.valueOf(reader.stringValue()));
                    break;
                case "dcmDefaultCharacterSet":
                    arcDev.setDefaultCharacterSet(reader.stringValue());
                    break;
                case "dcmUPSWorklistLabel":
                    arcDev.setUPSWorklistLabel(reader.stringValue());
                    break;
                case "dcmUPSEventSCU":
                    arcDev.setUPSEventSCUs(reader.stringArray());
                    break;
                case "dcmUPSEventSCUKeepAlive":
                    arcDev.setUPSEventSCUKeepAlive(reader.intValue());
                    break;
                case "dcmStorePermissionServiceErrorCommentPattern":
                    arcDev.setStorePermissionServiceErrorCommentPattern(Pattern.compile(reader.stringValue()));
                    break;
                case "dcmStorePermissionServiceErrorCodePattern":
                    arcDev.setStorePermissionServiceErrorCodePattern(Pattern.compile(reader.stringValue()));
                    break;
                case "dcmRetrieveAET":
                    arcDev.setRetrieveAETitles(reader.stringArray());
                    break;
                case "dcmReturnRetrieveAET":
                    arcDev.setReturnRetrieveAETitles(reader.stringArray());
                    break;
                case "dcmExternalRetrieveAEDestination":
                    arcDev.setExternalRetrieveAEDestination(reader.stringValue());
                    break;
                case "dcmXDSiImagingDocumentSourceAETitle":
                    arcDev.setXDSiImagingDocumentSourceAETitle(reader.stringValue());
                    break;
                case "dcmRemapRetrieveURL":
                    arcDev.setRemapRetrieveURL(reader.stringValue());
                    break;
                case "dcmValidateCallingAEHostname":
                    arcDev.setValidateCallingAEHostname(reader.booleanValue());
                    break;
                case "hl7PSUSendingApplication":
                    arcDev.setHL7PSUSendingApplication(reader.stringValue());
                    break;
                case "hl7PSUReceivingApplication":
                    arcDev.setHL7PSUReceivingApplications(reader.stringArray());
                    break;
                case "hl7PSUDelay":
                    arcDev.setHL7PSUDelay(Duration.valueOf(reader.stringValue()));
                    break;
                case "hl7PSUTimeout":
                    arcDev.setHL7PSUTimeout(Duration.valueOf(reader.stringValue()));
                    break;
                case "hl7PSUOnTimeout":
                    arcDev.setHL7PSUOnTimeout(reader.booleanValue());
                    break;
                case "hl7PSUTaskPollingInterval":
                    arcDev.setHL7PSUTaskPollingInterval(Duration.valueOf(reader.stringValue()));
                    break;
                case "hl7PSUTaskFetchSize":
                    arcDev.setHL7PSUTaskFetchSize(reader.intValue());
                    break;
                case "hl7PSUMWL":
                    arcDev.setHL7PSUMWL(reader.booleanValue());
                    break;
                case "dcmAcceptConflictingPatientID":
                    arcDev.setAcceptConflictingPatientID(AcceptConflictingPatientID.valueOf(reader.stringValue()));
                    break;
                case "dcmAuditRecordRepositoryURL":
                    arcDev.setAuditRecordRepositoryURL(reader.stringValue());
                    break;
                case "dcmAudit2JsonFhirTemplateURI":
                    arcDev.setAudit2JsonFhirTemplateURI(reader.stringValue());
                    break;
                case "dcmAudit2XmlFhirTemplateURI":
                    arcDev.setAudit2XmlFhirTemplateURI(reader.stringValue());
                    break;
                case "dcmCopyMoveUpdatePolicy":
                    arcDev.setCopyMoveUpdatePolicy(Attributes.UpdatePolicy.valueOf(reader.stringValue()));
                    break;
                case "dcmLinkMWLEntryUpdatePolicy":
                    arcDev.setLinkMWLEntryUpdatePolicy(Attributes.UpdatePolicy.valueOf(reader.stringValue()));
                    break;
                case "dcmStorageVerificationPolicy":
                    arcDev.setStorageVerificationPolicy(StorageVerificationPolicy.valueOf(reader.stringValue()));
                    break;
                case "dcmStorageVerificationUpdateLocationStatus":
                    arcDev.setStorageVerificationUpdateLocationStatus(reader.booleanValue());
                    break;
                case "dcmStorageVerificationStorageID":
                    arcDev.setStorageVerificationStorageIDs(reader.stringArray());
                    break;
                case "dcmStorageVerificationAETitle":
                    arcDev.setStorageVerificationAETitle(reader.stringValue());
                    break;
                case "dcmStorageVerificationBatchID":
                    arcDev.setStorageVerificationBatchID(reader.stringValue());
                    break;
                case "dcmStorageVerificationInitialDelay":
                    arcDev.setStorageVerificationInitialDelay(Period.parse(reader.stringValue()));
                    break;
                case "dcmStorageVerificationPeriod":
                    arcDev.setStorageVerificationPeriod(Period.parse(reader.stringValue()));
                    break;
                case "dcmStorageVerificationMaxScheduled":
                    arcDev.setStorageVerificationMaxScheduled(reader.intValue());
                    break;
                case "dcmStorageVerificationPollingInterval":
                    arcDev.setStorageVerificationPollingInterval(Duration.valueOf(reader.stringValue()));
                    break;
                case "dcmStorageVerificationSchedule":
                    arcDev.setStorageVerificationSchedules(ScheduleExpression.valuesOf(reader.stringArray()));
                    break;
                case "dcmStorageVerificationFetchSize":
                    arcDev.setStorageVerificationFetchSize(reader.intValue());
                    break;
                case "dcmUpdateLocationStatusOnRetrieve":
                    arcDev.setUpdateLocationStatusOnRetrieve(reader.booleanValue());
                    break;
                case "dcmStorageVerificationOnRetrieve":
                    arcDev.setStorageVerificationOnRetrieve(reader.booleanValue());
                    break;
                case "hl7TrackChangedPatientID":
                    arcDev.setHL7TrackChangedPatientID(reader.booleanValue());
                    break;
                case "dcmInvokeImageDisplayPatientURL":
                    arcDev.setInvokeImageDisplayPatientURL(reader.stringValue());
                    break;
                case "dcmInvokeImageDisplayStudyURL":
                    arcDev.setInvokeImageDisplayStudyURL(reader.stringValue());
                    break;
                case "hl7ADTSendingApplication":
                    arcDev.setHL7ADTSendingApplication(reader.stringValue());
                    break;
                case "hl7ADTReceivingApplication":
                    arcDev.setHL7ADTReceivingApplication(reader.stringArray());
                    break;
                case "hl7ScheduledProtocolCodeInOrder":
                    arcDev.setHL7ScheduledProtocolCodeInOrder(ScheduledProtocolCodeInOrder.valueOf(reader.stringValue()));
                    break;
                case "hl7ScheduledStationAETInOrder":
                    arcDev.setHL7ScheduledStationAETInOrder(ScheduledStationAETInOrder.valueOf(reader.stringValue()));
                    break;
                case "hl7NoPatientCreateMessageType":
                    arcDev.setHL7NoPatientCreateMessageTypes(reader.stringArray());
                    break;
                case "dcmAuditUnknownStudyInstanceUID":
                    arcDev.setAuditUnknownStudyInstanceUID(reader.stringValue());
                    break;
                case "dcmAuditUnknownPatientID":
                    arcDev.setAuditUnknownPatientID(reader.stringValue());
                    break;
                case "dcmAuditSoftwareConfigurationVerbose":
                    arcDev.setAuditSoftwareConfigurationVerbose(reader.booleanValue());
                    break;
                case "hl7UseNullValue":
                    arcDev.setHL7UseNullValue(reader.booleanValue());
                    break;
                case "dcmQueueTasksFetchSize":
                    arcDev.setQueueTasksFetchSize(reader.intValue());
                    break;
                case "dcmRejectionNoteStorageAET":
                    arcDev.setRejectionNoteStorageAET(reader.stringValue());
                    break;
                case "dcmXRoadProperty":
                    arcDev.setXRoadProperties(reader.stringArray());
                    break;
                case "dcmImpaxReportProperty":
                    arcDev.setImpaxReportProperties(reader.stringArray());
                    break;
                case "dcmUIConfigurationDeviceName":
                    arcDev.setUiConfigurationDeviceName(reader.stringValue());
                    break;
                case "dcmCompressionAETitle":
                    arcDev.setCompressionAETitle(reader.stringValue());
                    break;
                case "dcmCompressionPollingInterval":
                    arcDev.setCompressionPollingInterval(Duration.valueOf(reader.stringValue()));
                    break;
                case "dcmCompressionFetchSize":
                    arcDev.setCompressionFetchSize(reader.intValue());
                    break;
                case "dcmCompressionSchedule":
                    arcDev.setCompressionSchedules(ScheduleExpression.valuesOf(reader.stringArray()));
                    break;
                case "dcmCompressionThreads":
                    arcDev.setCompressionThreads(reader.intValue());
                    break;
                case "dcmDiffTaskProgressUpdateInterval":
                    arcDev.setDiffTaskProgressUpdateInterval(Duration.valueOf(reader.stringValue()));
                    break;
                case "dcmPatientVerificationPDQServiceID":
                    arcDev.setPatientVerificationPDQServiceID(reader.stringValue());
                    break;
                case "dcmPatientVerificationPollingInterval":
                    arcDev.setPatientVerificationPollingInterval(Duration.valueOf(reader.stringValue()));
                    break;
                case "dcmPatientVerificationFetchSize":
                    arcDev.setPatientVerificationFetchSize(reader.intValue());
                    break;
                case "dcmPatientVerificationAdjustIssuerOfPatientID":
                    arcDev.setPatientVerificationAdjustIssuerOfPatientID(reader.booleanValue());
                    break;
                case "dcmPatientVerificationPeriod":
                    arcDev.setPatientVerificationPeriod(Period.parse(reader.stringValue()));
                    break;
                case "dcmPatientVerificationPeriodOnNotFound":
                    arcDev.setPatientVerificationPeriodOnNotFound(Period.parse(reader.stringValue()));
                    break;
                case "dcmPatientVerificationRetryInterval":
                    arcDev.setPatientVerificationRetryInterval(Duration.valueOf(reader.stringValue()));
                    break;
                case "dcmPatientVerificationMaxRetries":
                    arcDev.setPatientVerificationMaxRetries(reader.intValue());
                    break;
                case "dcmPatientVerificationMaxStaleness":
                    arcDev.setPatientVerificationMaxStaleness(Duration.valueOf(reader.stringValue()));
                    break;
                case "dcmCSVUploadChunkSize":
                    arcDev.setCSVUploadChunkSize(reader.intValue());
                    break;
                case "dcmValidateUID":
                    arcDev.setValidateUID(reader.booleanValue());
                    break;
                case "dcmRelationalQueryNegotiationLenient":
                    arcDev.setRelationalQueryNegotiationLenient(reader.booleanValue());
                    break;
                case "dcmRelationalRetrieveNegotiationLenient":
                    arcDev.setRelationalRetrieveNegotiationLenient(reader.booleanValue());
                    break;
                case "dcmSchedulerMinStartDelay":
                    arcDev.setSchedulerMinStartDelay(reader.intValue());
                    break;
                case "hl7OrderMissingStudyIUIDPolicy":
                    arcDev.setHl7OrderMissingStudyIUIDPolicy(HL7OrderMissingStudyIUIDPolicy.valueOf(reader.stringValue()));
                    break;
                case "hl7ImportReportMissingStudyIUIDPolicy":
                    arcDev.setHl7ImportReportMissingStudyIUIDPolicy(
                            HL7ImportReportMissingStudyIUIDPolicy.valueOf(reader.stringValue()));
                    break;
                case "hl7DicomCharacterSet":
                    arcDev.setHl7DicomCharacterSet(reader.stringValue());
                    break;
                case "hl7VeterinaryUsePatientName":
                    arcDev.setHl7VeterinaryUsePatientName(reader.booleanValue());
                    break;
                case "dcmRejectConflictingPatientAttribute":
                    arcDev.setRejectConflictingPatientAttribute(TagUtils.fromHexStrings(reader.stringArray()));
                    break;
                case "dcmStowRetiredTransferSyntax":
                    arcDev.setStowRetiredTransferSyntax(reader.booleanValue());
                    break;
                case "dcmStowExcludeAPPMarkers":
                    arcDev.setStowExcludeAPPMarkers(reader.booleanValue());
                    break;
                case "dcmWadoThumbnailViewport":
                    arcDev.setWadoThumbnailViewPort(reader.stringValue());
                    break;
                case "dcmRestrictRetrieveAccordingTransferCapabilities":
                    arcDev.setRestrictRetrieveAccordingTransferCapabilities(
                            RestrictRetrieveAccordingTransferCapabilities.valueOf(reader.stringValue()));
                    break;
                case "dcmAttributeFilter":
                    loadAttributeFilterListFrom(arcDev, reader);
                    break;
                case "dcmStorage":
                    loadStorageDescriptorFrom(arcDev, reader);
                    break;
                case "dcmQueryRetrieveView":
                    loadQueryRetrieveViewFrom(arcDev, reader);
                    break;
                case "dcmQueue":
                    loadQueueDescriptorFrom(arcDev, reader);
                    break;
                case "dcmPDQService":
                    loadPDQServiceDescriptorFrom(arcDev, reader);
                    break;
                case "dcmExporter":
                    loadExporterDescriptorFrom(arcDev, reader);
                    break;
                case "dcmExportRule":
                    loadExportRule(arcDev.getExportRules(), reader);
                    break;
                case "dcmExportPriorsRule":
                    loadExportPriorsRules(arcDev.getExportPriorsRules(), reader);
                    break;
                case "dcmArchiveCompressionRule":
                    loadArchiveCompressionRule(arcDev.getCompressionRules(), reader);
                    break;
                case "dcmStoreAccessControlIDRule":
                    loadStoreAccessControlIDRule(arcDev.getStoreAccessControlIDRules(), reader);
                    break;
                case "dcmArchiveAttributeCoercion":
                    loadArchiveAttributeCoercion(arcDev.getAttributeCoercions(), reader, config);
                    break;
                case "dcmRejectionNote":
                    loadRejectionNoteFrom(arcDev, reader);
                    break;
                case "dcmStudyRetentionPolicy":
                    loadStudyRetentionPolicy(arcDev.getStudyRetentionPolicies(), reader);
                    break;
                case "dcmIDGenerator":
                    loadIDGenerators(arcDev, reader);
                    break;
                case "hl7ForwardRule":
                    loadHL7ForwardRules(arcDev.getHL7ForwardRules(), reader);
                    break;
                case "hl7ExportRule":
                    loadHL7ExportRules(arcDev.getHL7ExportRules(), reader);
                    break;
                case "hl7PrefetchRule":
                    loadHL7PrefetchRules(arcDev.getHL7PrefetchRules(), reader);
                    break;
                case "hl7StudyRetentionPolicy":
                    loadHL7StudyRetentionPolicy(arcDev.getHL7StudyRetentionPolicies(), reader);
                    break;
                case "dcmRSForwardRule":
                    loadRSForwardRules(arcDev.getRSForwardRules(), reader);
                    break;
                case "dcmAttributeSet":
                    loadAttributeSetFrom(arcDev, reader);
                    break;
                case "dcmBulkDataDescriptor":
                    this.config.loadBulkdataDescriptors(arcDev.getBulkDataDescriptors(), reader);
                    break;
                case "hl7OrderScheduledStation":
                    loadScheduledStations(arcDev.getHL7OrderScheduledStations(), reader, config);
                    break;
                case "hl7OrderSPSStatus":
                    loadHL7OrderSPSStatus(arcDev.getHL7OrderSPSStatuses(), reader);
                    break;
                case "dcmKeycloakServer":
                    loadKeycloakServers(arcDev, reader);
                    break;
                case "dcmMetrics":
                    loadMetricsDescriptors(arcDev, reader);
                    break;
                default:
                    reader.skipUnknownProperty();
            }
        }
    }

    private void loadAttributeFilterListFrom(ArchiveDeviceExtension arcDev, JsonReader reader) {
        Entity entity = null;
        reader.next();
        reader.expect(JsonParser.Event.START_ARRAY);
        while (reader.next() == JsonParser.Event.START_OBJECT) {
            reader.expect(JsonParser.Event.START_OBJECT);
            AttributeFilter af = new AttributeFilter();
            while (reader.next() == JsonParser.Event.KEY_NAME) {
                switch (reader.getString()) {
                    case "dcmEntity":
                        entity = Entity.valueOf(reader.stringValue());
                        break;
                    case "dcmTag":
                        af.setSelection(TagUtils.fromHexStrings(reader.stringArray()));
                        break;
                    case "dcmCustomAttribute1":
                        af.setCustomAttribute1(ValueSelector.valueOf(reader.stringValue()));
                        break;
                    case "dcmCustomAttribute2":
                        af.setCustomAttribute2(ValueSelector.valueOf(reader.stringValue()));
                        break;
                    case "dcmCustomAttribute3":
                        af.setCustomAttribute3(ValueSelector.valueOf(reader.stringValue()));
                        break;
                    case "dcmAttributeUpdatePolicy":
                        af.setAttributeUpdatePolicy(Attributes.UpdatePolicy.valueOf(reader.stringValue()));
                        break;
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            arcDev.setAttributeFilter(entity, af);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }

    private void loadAttributeSetFrom(ArchiveDeviceExtension arcDev, JsonReader reader) {
        reader.next();
        reader.expect(JsonParser.Event.START_ARRAY);
        while (reader.next() == JsonParser.Event.START_OBJECT) {
            reader.expect(JsonParser.Event.START_OBJECT);
            AttributeSet attributeSet = new AttributeSet();
            while (reader.next() == JsonParser.Event.KEY_NAME) {
                switch (reader.getString()) {
                    case "dcmAttributeSetType":
                        attributeSet.setType(AttributeSet.Type.valueOf(reader.stringValue()));
                        break;
                    case "dcmAttributeSetID":
                        attributeSet.setID(reader.stringValue());
                        break;
                    case "dicomDescription":
                        attributeSet.setDescription(reader.stringValue());
                        break;
                    case "dcmAttributeSetNumber":
                        attributeSet.setNumber(reader.intValue());
                        break;
                    case "dicomInstalled":
                        attributeSet.setInstalled(reader.booleanValue());
                        break;
                    case "dcmAttributeSetTitle":
                        attributeSet.setTitle(reader.stringValue());
                        break;
                    case "dcmTag":
                        attributeSet.setSelection(TagUtils.fromHexStrings(reader.stringArray()));
                        break;
                    case "dcmProperty":
                        attributeSet.setProperties(reader.stringArray());
                        break;
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            arcDev.addAttributeSet(attributeSet);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }

    private void loadStorageDescriptorFrom(ArchiveDeviceExtension arcDev, JsonReader reader) {
        reader.next();
        reader.expect(JsonParser.Event.START_ARRAY);
        while (reader.next() == JsonParser.Event.START_OBJECT) {
            reader.expect(JsonParser.Event.START_OBJECT);
            StorageDescriptor st = new StorageDescriptor();
            while (reader.next() == JsonParser.Event.KEY_NAME) {
                switch (reader.getString()) {
                    case "dcmStorageID":
                        st.setStorageID(reader.stringValue());
                        break;
                    case  "dcmURI":
                        st.setStorageURIStr(reader.stringValue());
                        break;
                    case "dcmDigestAlgorithm":
                        st.setDigestAlgorithm(reader.stringValue());
                        break;
                    case "dcmInstanceAvailability":
                        st.setInstanceAvailability(Availability.valueOf(reader.stringValue()));
                        break;
                    case "dcmStorageDuration":
                        st.setStorageDuration(StorageDuration.valueOf(reader.stringValue()));
                        break;
                    case "dcmReadOnly":
                        st.setReadOnly(reader.booleanValue());
                        break;
                    case "dcmNoDeletionConstraint":
                        st.setNoDeletionConstraint(reader.booleanValue());
                        break;
                    case "dcmDeleterThreads":
                        st.setDeleterThreads(reader.intValue());
                        break;
                    case "dcmStorageClusterID":
                        st.setStorageClusterID(reader.stringValue());
                        break;
                    case "dcmStorageThreshold":
                        st.setStorageThreshold(StorageThreshold.valueOf(reader.stringValue()));
                        break;
                    case "dcmDeleterThreshold":
                        st.setDeleterThresholdsFromStrings(reader.stringArray());
                        break;
                    case "dcmProperty":
                        st.setProperties(reader.stringArray());
                        break;
                    case "dcmExternalRetrieveAET":
                        st.setExternalRetrieveAETitles(reader.stringArray());
                        break;
                    case "dcmExportStorageID":
                        st.setExportStorageID(reader.stringValue());
                        break;
                    case "dcmRetrieveCacheStorageID":
                        st.setRetrieveCacheStorageID(reader.stringValue());
                        break;
                    case "dcmRetrieveCacheMaxParallel":
                        st.setRetrieveCacheMaxParallel(reader.intValue());
                        break;
                    case "dcmDeleteStudiesOlderThan":
                        st.setRetentionPeriods(RetentionPeriod.DeleteStudies.OlderThan, reader.stringArray());
                        break;
                    case "dcmDeleteStudiesReceivedBefore":
                        st.setRetentionPeriods(RetentionPeriod.DeleteStudies.ReceivedBefore, reader.stringArray());
                        break;
                    case "dcmDeleteStudiesNotUsedSince":
                        st.setRetentionPeriods(RetentionPeriod.DeleteStudies.NotUsedSince, reader.stringArray());
                        break;
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            arcDev.addStorageDescriptor(st);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }

    private void loadQueryRetrieveViewFrom(ArchiveDeviceExtension arcDev, JsonReader reader) {
        reader.next();
        reader.expect(JsonParser.Event.START_ARRAY);
        while (reader.next() == JsonParser.Event.START_OBJECT) {
            reader.expect(JsonParser.Event.START_OBJECT);
            QueryRetrieveView qrv = new QueryRetrieveView();
            while (reader.next() == JsonParser.Event.KEY_NAME) {
                switch (reader.getString()) {
                    case "dcmQueryRetrieveViewID":
                        qrv.setViewID(reader.stringValue());
                        break;
                    case "dcmShowInstancesRejectedByCode":
                        qrv.setShowInstancesRejectedByCodes(reader.codeArray());
                        break;
                    case "dcmHideRejectionNoteWithCode":
                        qrv.setHideRejectionNotesWithCodes(reader.codeArray());
                        break;
                    case "dcmHideNotRejectedInstances":
                        qrv.setHideNotRejectedInstances(reader.booleanValue());
                        break;
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            arcDev.addQueryRetrieveView(qrv);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }

    private void loadQueueDescriptorFrom(ArchiveDeviceExtension arcDev, JsonReader reader) {
        reader.next();
        reader.expect(JsonParser.Event.START_ARRAY);
        while (reader.next() == JsonParser.Event.START_OBJECT) {
            reader.expect(JsonParser.Event.START_OBJECT);
            QueueDescriptor qd = new QueueDescriptor();
            while (reader.next() == JsonParser.Event.KEY_NAME) {
                switch (reader.getString()) {
                    case "dcmQueueName":
                        qd.setQueueName(reader.stringValue());
                        break;
                    case "dcmJndiName":
                        qd.setJndiName(reader.stringValue());
                        break;
                    case "dicomDescription":
                        qd.setDescription(reader.stringValue());
                        break;
                    case "dcmMaxRetries":
                        qd.setMaxRetries(reader.intValue());
                        break;
                    case "dcmRetryDelay":
                        qd.setRetryDelay(Duration.valueOf(reader.stringValue()));
                        break;
                    case "dcmMaxRetryDelay":
                        qd.setMaxRetryDelay(Duration.valueOf(reader.stringValue()));
                        break;
                    case "dcmRetryDelayMultiplier":
                        qd.setRetryDelayMultiplier(reader.intValue());
                        break;
                    case "dcmRetryOnWarning":
                        qd.setRetryOnWarning(reader.booleanValue());
                        break;
                    case "dcmPurgeQueueMessageCompletedDelay":
                        qd.setPurgeQueueMessageCompletedDelay(Duration.valueOf(reader.stringValue()));
                        break;
                    case "dcmPurgeQueueMessageFailedDelay":
                        qd.setPurgeQueueMessageFailedDelay(Duration.valueOf(reader.stringValue()));
                        break;
                    case "dcmPurgeQueueMessageWarningDelay":
                        qd.setPurgeQueueMessageWarningDelay(Duration.valueOf(reader.stringValue()));
                        break;
                    case "dcmPurgeQueueMessageCanceledDelay":
                        qd.setPurgeQueueMessageCanceledDelay(Duration.valueOf(reader.stringValue()));
                        break;
                    case "dcmMaxQueueSize":
                        qd.setMaxQueueSize(reader.intValue());
                        break;
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            arcDev.addQueueDescriptor(qd);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }

    private void loadPDQServiceDescriptorFrom(ArchiveDeviceExtension arcDev, JsonReader reader) {
        reader.next();
        reader.expect(JsonParser.Event.START_ARRAY);
        while (reader.next() == JsonParser.Event.START_OBJECT) {
            reader.expect(JsonParser.Event.START_OBJECT);
            PDQServiceDescriptor desc = new PDQServiceDescriptor();
            while (reader.next() == JsonParser.Event.KEY_NAME) {
                switch (reader.getString()) {
                    case "dcmPDQServiceID":
                        desc.setPDQServiceID(reader.stringValue());
                        break;
                    case "dcmURI":
                        desc.setPDQServiceURI(URI.create(reader.stringValue()));
                        break;
                    case "dicomDescription":
                        desc.setDescription(reader.stringValue());
                        break;
                    case "dcmTag":
                        desc.setSelection(TagUtils.fromHexStrings(reader.stringArray()));
                        break;
                    case "dcmProperty":
                        desc.setProperties(reader.stringArray());
                        break;
                    case "dcmEntity":
                        desc.setEntity(Entity.valueOf(reader.stringValue()));
                        break;
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            arcDev.addPDQServiceDescriptor(desc);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }

    private void loadExporterDescriptorFrom(ArchiveDeviceExtension arcDev, JsonReader reader){
        reader.next();
        reader.expect(JsonParser.Event.START_ARRAY);
        while (reader.next() == JsonParser.Event.START_OBJECT) {
            reader.expect(JsonParser.Event.START_OBJECT);
            ExporterDescriptor ed = new ExporterDescriptor();
            while (reader.next() == JsonParser.Event.KEY_NAME) {
                switch (reader.getString()) {
                    case "dcmExporterID":
                        ed.setExporterID(reader.stringValue());
                        break;
                    case "dcmURI":
                        ed.setExportURI(URI.create(reader.stringValue()));
                        break;
                    case "dcmQueueName":
                        ed.setQueueName(reader.stringValue());
                        break;
                    case "dicomDescription":
                        ed.setDescription(reader.stringValue());
                        break;
                    case "dicomAETitle":
                        ed.setAETitle(reader.stringValue());
                        break;
                    case "dcmStgCmtSCP":
                        ed.setStgCmtSCPAETitle(reader.stringValue());
                        break;
                    case "dcmDeleteStudyFromStorageID":
                        ed.setDeleteStudyFromStorageID(reader.stringValue());
                        break;
                    case "dcmIanDestination":
                        ed.setIanDestinations(reader.stringArray());
                        break;
                    case "dcmRetrieveAET":
                        ed.setRetrieveAETitles(reader.stringArray());
                        break;
                    case "dcmRetrieveLocationUID":
                        ed.setRetrieveLocationUID(reader.stringValue());
                        break;
                    case "dcmInstanceAvailability":
                        ed.setInstanceAvailability(Availability.valueOf(reader.stringValue()));
                        break;
                    case "dcmSchedule":
                        ed.setSchedules(ScheduleExpression.valuesOf(reader.stringArray()));
                        break;
                    case "dcmProperty":
                        ed.setProperties(reader.stringArray());
                        break;
                    case "dcmExportPriority":
                        ed.setPriority(reader.intValue());
                        break;
                    case "dcmRejectForDataRetentionExpiry":
                        ed.setRejectForDataRetentionExpiry(reader.booleanValue());
                        break;
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            arcDev.addExporterDescriptor(ed);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }

    private void loadExportRule(Collection<ExportRule> rules, JsonReader reader) {
        reader.next();
        reader.expect(JsonParser.Event.START_ARRAY);
        while (reader.next() == JsonParser.Event.START_OBJECT) {
            reader.expect(JsonParser.Event.START_OBJECT);
            ExportRule er = new ExportRule();
            while (reader.next() == JsonParser.Event.KEY_NAME) {
                switch (reader.getString()) {
                    case "cn":
                        er.setCommonName(reader.stringValue());
                        break;
                    case "dcmEntity":
                        er.setEntity(Entity.valueOf(reader.stringValue()));
                        break;
                    case "dcmExporterID":
                        er.setExporterIDs(reader.stringArray());
                        break;
                    case "dcmProperty":
                        er.setConditions(new Conditions(reader.stringArray()));
                        break;
                    case "dcmSchedule":
                        er.setSchedules(ScheduleExpression.valuesOf(reader.stringArray()));
                        break;
                    case "dcmDuration":
                        er.setExportDelay(Duration.valueOf(reader.stringValue()));
                        break;
                    case "dcmExportPreviousEntity":
                        er.setExportPreviousEntity(reader.booleanValue());
                        break;
                    case "dcmExportReoccurredInstances":
                        er.setExportReoccurredInstances(ExportReoccurredInstances.valueOf(reader.stringValue()));
                        break;
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            rules.add(er);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }

    private void loadExportPriorsRules(Collection<ExportPriorsRule> rules, JsonReader reader) {
        reader.next();
        reader.expect(JsonParser.Event.START_ARRAY);
        while (reader.next() == JsonParser.Event.START_OBJECT) {
            reader.expect(JsonParser.Event.START_OBJECT);
            ExportPriorsRule rule = new ExportPriorsRule();
            while (reader.next() == JsonParser.Event.KEY_NAME) {
                switch (reader.getString()) {
                    case "cn":
                        rule.setCommonName(reader.stringValue());
                        break;
                    case "dcmEntitySelector":
                        rule.setEntitySelectors(EntitySelector.valuesOf(reader.stringArray()));
                        break;
                    case "dcmExporterID":
                        rule.setExporterIDs(reader.stringArray());
                        break;
                    case "dcmProperty":
                        rule.setConditions(new Conditions(reader.stringArray()));
                        break;
                    case "dcmSchedule":
                        rule.setSchedules(ScheduleExpression.valuesOf(reader.stringArray()));
                        break;
                    case "dcmDuration":
                        rule.setSuppressDuplicateExportInterval(Duration.valueOf(reader.stringValue()));
                        break;
                    case "dcmExportReoccurredInstances":
                        rule.setExportReoccurredInstances(ExportReoccurredInstances.valueOf(reader.stringValue()));
                        break;
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            rules.add(rule);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }

    static void loadHL7ExportRules(Collection<HL7ExportRule> rules, JsonReader reader) {
        reader.next();
        reader.expect(JsonParser.Event.START_ARRAY);
        while (reader.next() == JsonParser.Event.START_OBJECT) {
            reader.expect(JsonParser.Event.START_OBJECT);
            HL7ExportRule rule = new HL7ExportRule();
            while (reader.next() == JsonParser.Event.KEY_NAME) {
                switch (reader.getString()) {
                    case "cn":
                        rule.setCommonName(reader.stringValue());
                        break;
                    case "dcmEntitySelector":
                        rule.setEntitySelectors(EntitySelector.valuesOf(reader.stringArray()));
                        break;
                    case "dcmExporterID":
                        rule.setExporterIDs(reader.stringArray());
                        break;
                    case "dcmProperty":
                        rule.setConditions(new HL7Conditions(reader.stringArray()));
                        break;
                    case "dcmNullifyIssuerOfPatientID":
                        rule.setIgnoreAssigningAuthorityOfPatientID(NullifyIssuer.valueOf(reader.stringValue()));
                        break;
                    case "dcmIssuerOfPatientID":
                        rule.setAssigningAuthorityOfPatientIDs(toIssuers(reader.stringArray()));
                        break;
                    case "dcmDuration":
                        rule.setSuppressDuplicateExportInterval(Duration.valueOf(reader.stringValue()));
                        break;
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            rules.add(rule);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }

    static void loadHL7PrefetchRules(Collection<HL7PrefetchRule> rules, JsonReader reader) {
        reader.next();
        reader.expect(JsonParser.Event.START_ARRAY);
        while (reader.next() == JsonParser.Event.START_OBJECT) {
            reader.expect(JsonParser.Event.START_OBJECT);
            HL7PrefetchRule rule = new HL7PrefetchRule();
            while (reader.next() == JsonParser.Event.KEY_NAME) {
                switch (reader.getString()) {
                    case "cn":
                        rule.setCommonName(reader.stringValue());
                        break;
                    case "dcmQueueName":
                        rule.setQueueName(reader.stringValue());
                        break;
                    case "dicomAETitle":
                        rule.setAETitle(reader.stringValue());
                        break;
                    case "dcmPrefetchCFindSCP":
                        rule.setPrefetchCFindSCP(reader.stringValue());
                        break;
                    case "dcmPrefetchCMoveSCP":
                        rule.setPrefetchCMoveSCP(reader.stringValue());
                        break;
                    case "dcmPrefetchCStoreSCP":
                        rule.setPrefetchCStoreSCPs(reader.stringArray());
                        break;
                    case "dcmEntitySelector":
                        rule.setEntitySelectors(EntitySelector.valuesOf(reader.stringArray()));
                        break;
                    case "dcmProperty":
                        rule.setConditions(new HL7Conditions(reader.stringArray()));
                        break;
                    case "dcmSchedule":
                        rule.setSchedules(ScheduleExpression.valuesOf(reader.stringArray()));
                        break;
                    case "dcmNullifyIssuerOfPatientID":
                        rule.setIgnoreAssigningAuthorityOfPatientID(NullifyIssuer.valueOf(reader.stringValue()));
                        break;
                    case "dcmIssuerOfPatientID":
                        rule.setAssigningAuthorityOfPatientIDs(toIssuers(reader.stringArray()));
                        break;
                    case "dcmDuration":
                        rule.setSuppressDuplicateRetrieveInterval(Duration.valueOf(reader.stringValue()));
                        break;
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            rules.add(rule);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }

    private void loadArchiveCompressionRule(Collection<ArchiveCompressionRule> rules, JsonReader reader) {
        reader.next();
        reader.expect(JsonParser.Event.START_ARRAY);
        while (reader.next() == JsonParser.Event.START_OBJECT) {
            reader.expect(JsonParser.Event.START_OBJECT);
            ArchiveCompressionRule acr = new ArchiveCompressionRule();
            while (reader.next() == JsonParser.Event.KEY_NAME) {
                switch (reader.getString()) {
                    case "cn":
                        acr.setCommonName(reader.stringValue());
                        break;
                    case "dicomTransferSyntax":
                        acr.setTransferSyntax(reader.stringValue());
                        break;
                    case "dcmRulePriority":
                        acr.setPriority(reader.intValue());
                        break;
                    case "dcmCompressionDelay":
                        acr.setDelay(Duration.valueOf(reader.stringValue()));
                        break;
                    case "dcmProperty":
                        acr.setConditions(new Conditions(reader.stringArray()));
                        break;
                    case "dcmImageWriteParam":
                        acr.setImageWriteParams(Property.valueOf(reader.stringArray()));
                        break;
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            rules.add(acr);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }

    private void loadStoreAccessControlIDRule(Collection<StoreAccessControlIDRule> rules, JsonReader reader) {
        reader.next();
        reader.expect(JsonParser.Event.START_ARRAY);
        while (reader.next() == JsonParser.Event.START_OBJECT) {
            reader.expect(JsonParser.Event.START_OBJECT);
            StoreAccessControlIDRule acr = new StoreAccessControlIDRule();
            while (reader.next() == JsonParser.Event.KEY_NAME) {
                switch (reader.getString()) {
                    case "cn":
                        acr.setCommonName(reader.stringValue());
                        break;
                    case "dcmStoreAccessControlID":
                        acr.setStoreAccessControlID(reader.stringValue());
                        break;
                    case "dcmRulePriority":
                        acr.setPriority(reader.intValue());
                        break;
                    case "dcmProperty":
                        acr.setConditions(new Conditions(reader.stringArray()));
                        break;
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            rules.add(acr);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }

    private void loadArchiveAttributeCoercion(Collection<ArchiveAttributeCoercion> coercions, JsonReader reader,
                                              ConfigurationDelegate config) {
        reader.next();
        reader.expect(JsonParser.Event.START_ARRAY);
        while (reader.next() == JsonParser.Event.START_OBJECT) {
            reader.expect(JsonParser.Event.START_OBJECT);
            ArchiveAttributeCoercion aac = new ArchiveAttributeCoercion();
            while (reader.next() == JsonParser.Event.KEY_NAME) {
                switch (reader.getString()) {
                    case "cn":
                        aac.setCommonName(reader.stringValue());
                        break;
                    case "dcmDIMSE":
                        aac.setDIMSE(Dimse.valueOf(reader.stringValue()));
                        break;
                    case "dicomTransferRole":
                        aac.setRole(TransferCapability.Role.valueOf(reader.stringValue()));
                        break;
                    case "dcmRulePriority":
                        aac.setPriority(reader.intValue());
                        break;
                    case "dcmAETitle":
                        aac.setAETitles(reader.stringArray());
                        break;
                    case "dcmHostname":
                        aac.setHostNames(reader.stringArray());
                        break;
                    case "dcmSOPClass":
                        aac.setSOPClasses(reader.stringArray());
                        break;
                    case "dcmRetrieveAsReceived":
                        aac.setRetrieveAsReceived(reader.booleanValue());
                        break;
                    case "dcmDeIdentification":
                        aac.setDeIdentification(reader.enumArray(DeIdentifier.Option.class));
                        break;
                    case "dcmURI":
                        aac.setXSLTStylesheetURI(reader.stringValue());
                        break;
                    case "dcmNoKeywords":
                        aac.setNoKeywords(reader.booleanValue());
                        break;
                    case "dcmLeadingCFindSCP":
                        aac.setLeadingCFindSCP(reader.stringValue());
                        break;
                    case "dcmMergeMWLMatchingKey":
                        aac.setMergeMWLMatchingKey(MergeMWLMatchingKey.valueOf(reader.stringValue()));
                        break;
                    case "dcmMergeMWLTemplateURI":
                        aac.setMergeMWLTemplateURI(reader.stringValue());
                        break;
                    case "dcmAttributeUpdatePolicy":
                        aac.setAttributeUpdatePolicy(Attributes.UpdatePolicy.valueOf(reader.stringValue()));
                        break;
                    case "dcmNullifyTag":
                        aac.setNullifyTags(TagUtils.fromHexStrings(reader.stringArray()));
                        break;
                    case "dcmSupplementFromDeviceName":
                        aac.setSupplementFromDevice(loadSupplementFromDevice(config, reader.stringValue()));
                        break;
                    case "dcmNullifyIssuerOfPatientID":
                        aac.setNullifyIssuerOfPatientID(NullifyIssuer.valueOf(reader.stringValue()));
                        break;
                    case "dcmIssuerOfPatientID":
                        aac.setIssuerOfPatientIDs(toIssuers(reader.stringArray()));
                        break;
                    case "dcmIssuerOfPatientIDFormat":
                        aac.setIssuerOfPatientIDFormat(reader.stringValue());
                        break;
                    case "dcmTrimISO2022CharacterSet":
                        aac.setTrimISO2022CharacterSet(reader.booleanValue());
                        break;
                    case "dcmUseCallingAETitleAs":
                        aac.setUseCallingAETitleAs(UseCallingAETitleAsCoercion.Type.valueOf(reader.stringValue()));
                        break;
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            coercions.add(aac);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }

    private static Issuer[] toIssuers(String[] issuerOfPatientIds) {
        Issuer[] issuers = new Issuer[issuerOfPatientIds.length];
        for (int i = 0; i < issuerOfPatientIds.length; i++)
            issuers[i] = new Issuer(issuerOfPatientIds[i]);
        return issuers;
    }

    private Device loadSupplementFromDevice(ConfigurationDelegate config, String supplementDeviceRef) {
        try {
            return supplementDeviceRef != null
                    ? config.findDevice(supplementDeviceRef)
                    : null;
        } catch (ConfigurationException e) {
            LOG.info("Failed to load Supplement Device Reference "
                    + supplementDeviceRef + " referenced by Attribute Coercion", e);
            return null;
        }
    }

    private static Device loadScheduledStation(ConfigurationDelegate config, String scheduledStationDeviceRef) {
        try {
            return config.findDevice(scheduledStationDeviceRef);
        } catch (ConfigurationException e) {
            LOG.info("Failed to load Scheduled Station Device Reference "
                    + scheduledStationDeviceRef + " referenced by HL7 Order Scheduled Station", e);
            return null;
        }
    }

    private void loadRejectionNoteFrom(ArchiveDeviceExtension arcDev, JsonReader reader) {
        reader.next();
        reader.expect(JsonParser.Event.START_ARRAY);
        while (reader.next() == JsonParser.Event.START_OBJECT) {
            reader.expect(JsonParser.Event.START_OBJECT);
            RejectionNote rn = new RejectionNote();
            while (reader.next() == JsonParser.Event.KEY_NAME) {
                switch (reader.getString()) {
                    case "dcmRejectionNoteLabel":
                        rn.setRejectionNoteLabel(reader.stringValue());
                        break;
                    case "dcmRejectionNoteType":
                        rn.setRejectionNoteType(RejectionNote.Type.valueOf(reader.stringValue()));
                        break;
                    case "dcmRejectionNoteCode":
                        rn.setRejectionNoteCode(new Code(reader.stringValue()));
                        break;
                    case "dcmAcceptPreviousRejectedInstance":
                        rn.setAcceptPreviousRejectedInstance(
                                RejectionNote.AcceptPreviousRejectedInstance.valueOf(reader.stringValue()));
                        break;
                    case "dcmOverwritePreviousRejection":
                        rn.setOverwritePreviousRejection(overwritePreviousRejection(reader.stringArray()));
                        break;
                    case "dcmAcceptRejectionBeforeStorage":
                        rn.setAcceptRejectionBeforeStorage(Duration.valueOf(reader.stringValue()));
                        break;
                    case "dcmDeleteRejectedInstanceDelay":
                        rn.setDeleteRejectedInstanceDelay(Duration.valueOf(reader.stringValue()));
                        break;
                    case "dcmDeleteRejectionNoteDelay":
                        rn.setDeleteRejectionNoteDelay(Duration.valueOf(reader.stringValue()));
                        break;
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            arcDev.addRejectionNote(rn);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }

    private Code[] overwritePreviousRejection(String[] overwritePreviousRejectionAsStringArray) {
        Code[] overwritePreviousRejectionCodes = new Code[overwritePreviousRejectionAsStringArray.length];
        for (int i = 0; i < overwritePreviousRejectionAsStringArray.length; i++) {
            overwritePreviousRejectionCodes[i] = new Code(overwritePreviousRejectionAsStringArray[i]);
        }
        return overwritePreviousRejectionCodes;
    }

    private void loadStudyRetentionPolicy(Collection<StudyRetentionPolicy> policies, JsonReader reader) {
        reader.next();
        reader.expect(JsonParser.Event.START_ARRAY);
        while (reader.next() == JsonParser.Event.START_OBJECT) {
            reader.expect(JsonParser.Event.START_OBJECT);
            StudyRetentionPolicy srp = new StudyRetentionPolicy();
            while (reader.next() == JsonParser.Event.KEY_NAME) {
                switch (reader.getString()) {
                    case "cn":
                        srp.setCommonName(reader.stringValue());
                        break;
                    case "dcmRetentionPeriod":
                        srp.setRetentionPeriod(Period.parse(reader.stringValue()));
                        break;
                    case "dcmRulePriority":
                        srp.setPriority(reader.intValue());
                        break;
                    case "dcmProperty":
                        srp.setConditions(new Conditions(reader.stringArray()));
                        break;
                    case "dcmExpireSeriesIndividually":
                        srp.setExpireSeriesIndividually(reader.booleanValue());
                        break;
                    case "dcmStartRetentionPeriodOnStudyDate":
                        srp.setStartRetentionPeriodOnStudyDate(reader.booleanValue());
                        break;
                    case "dcmExporterID":
                        srp.setExporterID(reader.stringValue());
                        break;
                    case "dcmFreezeExpirationDate":
                        srp.setFreezeExpirationDate(reader.booleanValue());
                        break;
                    case "dcmRevokeExpiration":
                        srp.setRevokeExpiration(reader.booleanValue());
                        break;
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            policies.add(srp);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }

    static void loadHL7StudyRetentionPolicy(Collection<HL7StudyRetentionPolicy> policies, JsonReader reader) {
        reader.next();
        reader.expect(JsonParser.Event.START_ARRAY);
        while (reader.next() == JsonParser.Event.START_OBJECT) {
            reader.expect(JsonParser.Event.START_OBJECT);
            HL7StudyRetentionPolicy srp = new HL7StudyRetentionPolicy();
            while (reader.next() == JsonParser.Event.KEY_NAME) {
                switch (reader.getString()) {
                    case "cn":
                        srp.setCommonName(reader.stringValue());
                        break;
                    case "dicomAETitle":
                        srp.setAETitle(reader.stringValue());
                        break;
                    case "dcmRetentionPeriod":
                        srp.setMinRetentionPeriod(Period.parse(reader.stringValue()));
                        break;
                    case "dcmMaxRetentionPeriod":
                        srp.setMaxRetentionPeriod(Period.parse(reader.stringValue()));
                        break;
                    case "dcmRulePriority":
                        srp.setPriority(reader.intValue());
                        break;
                    case "dcmProperty":
                        srp.setConditions(new HL7Conditions(reader.stringArray()));
                        break;
                    case "dcmStartRetentionPeriodOnStudyDate":
                        srp.setStartRetentionPeriodOnStudyDate(reader.booleanValue());
                        break;
                    case "dcmExporterID":
                        srp.setExporterID(reader.stringValue());
                        break;
                    case "dcmFreezeExpirationDate":
                        srp.setFreezeExpirationDate(reader.booleanValue());
                        break;
                    case "dcmRevokeExpiration":
                        srp.setRevokeExpiration(reader.booleanValue());
                        break;
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            policies.add(srp);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }

    static void loadHL7ForwardRules(Collection<HL7ForwardRule> rules, JsonReader reader) {
        reader.next();
        reader.expect(JsonParser.Event.START_ARRAY);
        while (reader.next() == JsonParser.Event.START_OBJECT) {
            reader.expect(JsonParser.Event.START_OBJECT);
            HL7ForwardRule rule = new HL7ForwardRule();
            while (reader.next() == JsonParser.Event.KEY_NAME) {
                switch (reader.getString()) {
                    case "cn":
                        rule.setCommonName(reader.stringValue());
                        break;
                    case "hl7FwdApplicationName":
                        rule.setDestinations(reader.stringArray());
                        break;
                    case "dcmProperty":
                        rule.setConditions(new HL7Conditions(reader.stringArray()));
                        break;
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            rules.add(rule);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }

    static void loadScheduledStations(Collection<HL7OrderScheduledStation> stations, JsonReader reader,
                                      ConfigurationDelegate config) {
        reader.next();
        reader.expect(JsonParser.Event.START_ARRAY);
        while (reader.next() == JsonParser.Event.START_OBJECT) {
            reader.expect(JsonParser.Event.START_OBJECT);
            HL7OrderScheduledStation station = new HL7OrderScheduledStation();
            while (reader.next() == JsonParser.Event.KEY_NAME) {
                switch (reader.getString()) {
                    case "cn":
                        station.setCommonName(reader.stringValue());
                        break;
                    case "hl7OrderScheduledStationDeviceName":
                        station.setDevice(loadScheduledStation(config, reader.stringValue()));
                        break;
                    case "dcmRulePriority":
                        station.setPriority(reader.intValue());
                        break;
                    case "dcmProperty":
                        station.setConditions(new HL7Conditions(reader.stringArray()));
                        break;
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            stations.add(station);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }

    static void loadHL7OrderSPSStatus(Map<SPSStatus, HL7OrderSPSStatus> hl7OrderSPSStatusMap, JsonReader reader) {
        reader.next();
        reader.expect(JsonParser.Event.START_ARRAY);
        while (reader.next() == JsonParser.Event.START_OBJECT) {
            reader.expect(JsonParser.Event.START_OBJECT);
            HL7OrderSPSStatus hl7OrderSPSStatus = new HL7OrderSPSStatus();
            while (reader.next() == JsonParser.Event.KEY_NAME) {
                switch (reader.getString()) {
                    case "dcmSPSStatus":
                        hl7OrderSPSStatus.setSPSStatus(SPSStatus.valueOf(reader.stringValue()));
                        break;
                    case "hl7OrderControlStatus":
                        hl7OrderSPSStatus.setOrderControlStatusCodes(reader.stringArray());
                        break;
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            hl7OrderSPSStatusMap.put(hl7OrderSPSStatus.getSPSStatus(), hl7OrderSPSStatus);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }

    private static void loadRSForwardRules(Collection<RSForwardRule> rules, JsonReader reader) {
        reader.next();
        reader.expect(JsonParser.Event.START_ARRAY);
        while (reader.next() == JsonParser.Event.START_OBJECT) {
            reader.expect(JsonParser.Event.START_OBJECT);
            RSForwardRule rule = new RSForwardRule();
            while (reader.next() == JsonParser.Event.KEY_NAME) {
                switch (reader.getString()) {
                    case "cn":
                        rule.setCommonName(reader.stringValue());
                        break;
                    case "dcmWebAppName":
                        rule.setWebAppName(reader.stringValue());
                        break;
                    case "dcmRSOperation":
                        rule.setRSOperations(reader.enumArray(RSOperation.class));
                        break;
                    case "dcmTLSAllowAnyHostname":
                        rule.setTlsAllowAnyHostname(reader.booleanValue());
                        break;
                    case "dcmTLSDisableTrustManager":
                        rule.setTlsDisableTrustManager(reader.booleanValue());
                        break;
                    case "dcmURIPattern":
                        rule.setRequestURLPattern(reader.stringValue());
                        break;
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            rules.add(rule);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }

    private static void loadKeycloakServers(ArchiveDeviceExtension arcDev, JsonReader reader) {
        reader.next();
        reader.expect(JsonParser.Event.START_ARRAY);
        while (reader.next() == JsonParser.Event.START_OBJECT) {
            reader.expect(JsonParser.Event.START_OBJECT);
            KeycloakServer keycloakServer = new KeycloakServer();
            while (reader.next() == JsonParser.Event.KEY_NAME) {
                switch (reader.getString()) {
                    case "dcmKeycloakServerID":
                        keycloakServer.setKeycloakServerID(reader.stringValue());
                        break;
                    case "dcmURI":
                        keycloakServer.setServerURL(reader.stringValue());
                        break;
                    case "dcmKeycloakRealm":
                        keycloakServer.setRealm(reader.stringValue());
                        break;
                    case "dcmKeycloakClientID":
                        keycloakServer.setClientID(reader.stringValue());
                        break;
                    case "dcmKeycloakGrantType":
                        keycloakServer.setGrantType(KeycloakServer.GrantType.valueOf(reader.stringValue()));
                        break;
                    case "dcmKeycloakClientSecret":
                        keycloakServer.setClientSecret(reader.stringValue());
                        break;
                    case "dcmTLSAllowAnyHostname":
                        keycloakServer.setTlsAllowAnyHostname(reader.booleanValue());
                        break;
                    case "dcmTLSDisableTrustManager":
                        keycloakServer.setTlsDisableTrustManager(reader.booleanValue());
                        break;
                    case "uid":
                        keycloakServer.setUserID(reader.stringValue());
                        break;
                    case "userPassword":
                        keycloakServer.setPassword(reader.stringValue());
                        break;
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            arcDev.addKeycloakServer(keycloakServer);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }

    private static void loadMetricsDescriptors(ArchiveDeviceExtension arcDev, JsonReader reader) {
        reader.next();
        reader.expect(JsonParser.Event.START_ARRAY);
        while (reader.next() == JsonParser.Event.START_OBJECT) {
            reader.expect(JsonParser.Event.START_OBJECT);
            MetricsDescriptor metricsDescriptor = new MetricsDescriptor();
            while (reader.next() == JsonParser.Event.KEY_NAME) {
                switch (reader.getString()) {
                    case "dcmMetricsName":
                        metricsDescriptor.setMetricsName(reader.stringValue());
                        break;
                    case "dicomDescription":
                        metricsDescriptor.setDescription(reader.stringValue());
                        break;
                    case "dcmMetricsRetentionPeriod":
                        metricsDescriptor.setRetentionPeriod(reader.intValue());
                        break;
                    case "dcmUnit":
                        metricsDescriptor.setUnit(reader.stringValue());
                        break;
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            arcDev.addMetricsDescriptor(metricsDescriptor);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }

    private void loadIDGenerators(ArchiveDeviceExtension arcDev, JsonReader reader) {
        reader.next();
        reader.expect(JsonParser.Event.START_ARRAY);
        while (reader.next() == JsonParser.Event.START_OBJECT) {
            reader.expect(JsonParser.Event.START_OBJECT);
            IDGenerator generator = new IDGenerator();
            while (reader.next() == JsonParser.Event.KEY_NAME) {
                switch (reader.getString()) {
                    case "dcmIDGeneratorName":
                        generator.setName(IDGenerator.Name.valueOf(reader.stringValue()));
                        break;
                    case "dcmIDGeneratorFormat":
                        generator.setFormat(reader.stringValue());
                        break;
                    case "dcmIDGeneratorInitialValue":
                        generator.setInitialValue(reader.intValue());
                        break;
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            arcDev.addIDGenerator(generator);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }

    @Override
    public boolean loadApplicationEntityExtension(Device device, ApplicationEntity ae, JsonReader reader,
                                                  ConfigurationDelegate config) {
        if (!reader.getString().equals("dcmArchiveNetworkAE"))
            return false;

        reader.next();
        reader.expect(JsonParser.Event.START_OBJECT);
        ArchiveAEExtension arcAE = new ArchiveAEExtension();
        loadFrom(arcAE, reader, config);
        ae.addAEExtension(arcAE);
        reader.expect(JsonParser.Event.END_OBJECT);
        return true;
    }

    private void loadFrom(ArchiveAEExtension arcAE, JsonReader reader, ConfigurationDelegate config) {
        while (reader.next() == JsonParser.Event.KEY_NAME) {
            switch (reader.getString()) {
                case "dcmObjectStorageID":
                    arcAE.setObjectStorageIDs(reader.stringArray());
                    break;
                case "dcmObjectStorageCount":
                    arcAE.setObjectStorageCount(reader.intValue());
                    break;
                case "dcmMetadataStorageID":
                    arcAE.setMetadataStorageIDs(reader.stringArray());
                    break;
                case "dcmBulkDataDescriptorID":
                    arcAE.setBulkDataDescriptorID(reader.stringValue());
                    break;
                case "dcmSeriesMetadataDelay":
                    arcAE.setSeriesMetadataDelay(Duration.valueOf(reader.stringValue()));
                    break;
                case "dcmPurgeInstanceRecordsDelay":
                    arcAE.setPurgeInstanceRecordsDelay(Duration.valueOf(reader.stringValue()));
                    break;
                case "dcmStoreAccessControlID":
                    arcAE.setStoreAccessControlID(reader.stringValue());
                    break;
                case "dcmAccessControlID":
                    arcAE.setAccessControlIDs(reader.stringArray());
                    break;
                case "dcmOverwritePolicy":
                    arcAE.setOverwritePolicy(OverwritePolicy.valueOf(reader.stringValue()));
                    break;
                case "dcmQueryRetrieveViewID":
                    arcAE.setQueryRetrieveViewID(reader.stringValue());
                    break;
                case "dcmBulkDataSpoolDirectory":
                    arcAE.setBulkDataSpoolDirectory(reader.stringValue());
                    break;
                case "dcmHideSPSWithStatusFromMWL":
                    arcAE.setHideSPSWithStatusFromMWL(reader.enumArray(SPSStatus.class));
                    break;
                case "dcmPersonNameComponentOrderInsensitiveMatching":
                    arcAE.setPersonNameComponentOrderInsensitiveMatching(reader.booleanValue());
                    break;
                case "dcmSendPendingCGet":
                    arcAE.setSendPendingCGet(reader.booleanValue());
                    break;
                case "dcmSendPendingCMoveInterval":
                    arcAE.setSendPendingCMoveInterval(Duration.valueOf(reader.stringValue()));
                    break;
                case "dcmWadoZIPEntryNameFormat":
                    arcAE.setWadoZIPEntryNameFormat(reader.stringValue());
                    break;
                case "dcmWadoSR2HtmlTemplateURI":
                    arcAE.setWadoSR2HtmlTemplateURI(reader.stringValue());
                    break;
                case "dcmWadoSR2TextTemplateURI":
                    arcAE.setWadoSR2TextTemplateURI(reader.stringValue());
                    break;
                case "dcmWadoCDA2HtmlTemplateURI":
                    arcAE.setWadoCDA2HtmlTemplateURI(reader.stringValue());
                    break;
                case "dcmQueryMaxNumberOfResults":
                    arcAE.setQueryMaxNumberOfResults(reader.intValue());
                    break;
                case "dcmQidoMaxNumberOfResults":
                    arcAE.setQidoMaxNumberOfResults(reader.intValue());
                    break;
                case "dcmFwdMppsDestination":
                    arcAE.setMppsForwardDestinations(reader.stringArray());
                    break;
                case "dcmIanDestination":
                    arcAE.setIanDestinations(reader.stringArray());
                    break;
                case "dcmIanDelay":
                    arcAE.setIanDelay(Duration.valueOf(reader.stringValue()));
                    break;
                case "dcmIanTimeout":
                    arcAE.setIanTimeout(Duration.valueOf(reader.stringValue()));
                    break;
                case "dcmIanOnTimeout":
                    arcAE.setIanOnTimeout(reader.booleanValue());
                    break;
                case "dcmSpanningCFindSCP":
                    arcAE.setSpanningCFindSCP(reader.stringValue());
                    break;
                case "dcmSpanningCFindSCPRetrieveAET":
                    arcAE.setSpanningCFindSCPRetrieveAETitles(reader.stringArray());
                    break;
                case "dcmSpanningCFindSCPPolicy":
                    arcAE.setSpanningCFindSCPPolicy(SpanningCFindSCPPolicy.valueOf(reader.stringValue()));
                    break;
                case "dcmFallbackCMoveSCP":
                    arcAE.setFallbackCMoveSCP(reader.stringValue());
                    break;
                case "dcmFallbackCMoveSCPDestination":
                    arcAE.setFallbackCMoveSCPDestination(reader.stringValue());
                    break;
                case "dcmFallbackCMoveSCPCallingAET":
                    arcAE.setFallbackCMoveSCPCallingAET(reader.stringValue());
                    break;
                case "dcmFallbackCMoveSCPRetries":
                    arcAE.setFallbackCMoveSCPRetries(reader.intValue());
                    break;
                case "dcmFallbackCMoveSCPLeadingCFindSCP":
                    arcAE.setFallbackCMoveSCPLeadingCFindSCP(reader.stringValue());
                    break;
                case "dcmAltCMoveSCP":
                    arcAE.setAlternativeCMoveSCP(reader.stringValue());
                    break;
                case "dcmFallbackCMoveSCPStudyOlderThan":
                    arcAE.setFallbackCMoveSCPStudyOlderThan(reader.stringValue());
                    break;
                case "dcmStorePermissionServiceURL":
                    arcAE.setStorePermissionServiceURL(reader.stringValue());
                    break;
                case "dcmStorePermissionServiceResponse":
                    arcAE.setStorePermissionServiceResponse(reader.stringValue());
                    break;
                case "dcmStorePermissionServiceResponsePattern":
                    arcAE.setStorePermissionServiceResponsePattern(Pattern.compile(reader.stringValue()));
                    break;
                case "dcmAllowRejectionForDataRetentionPolicyExpired":
                    arcAE.setAllowRejectionForDataRetentionPolicyExpired(
                            AllowRejectionForDataRetentionPolicyExpired.valueOf(reader.stringValue()));
                    break;
                case "dcmAcceptedUserRole":
                    arcAE.setAcceptedUserRoles(reader.stringArray());
                    break;
                case "dcmAcceptMissingPatientID":
                    arcAE.setAcceptMissingPatientID(AcceptMissingPatientID.valueOf(reader.stringValue()));
                    break;
                case "dcmAllowDeleteStudyPermanently":
                    arcAE.setAllowDeleteStudyPermanently(AllowDeleteStudyPermanently.valueOf(reader.stringValue()));
                    break;
                case "dcmAllowDeletePatient":
                    arcAE.setAllowDeletePatient(AllowDeletePatient.valueOf(reader.stringValue()));
                    break;
                case "dcmStorePermissionServiceExpirationDatePattern":
                    arcAE.setStorePermissionServiceExpirationDatePattern(Pattern.compile(reader.stringValue()));
                    break;
                case "dcmDefaultCharacterSet":
                    arcAE.setDefaultCharacterSet(reader.stringValue());
                    break;
                case "dcmUPSWorklistLabel":
                    arcAE.setUPSWorklistLabel(reader.stringValue());
                    break;
                case "dcmUPSEventSCU":
                    arcAE.setUPSEventSCUs(reader.stringArray());
                    break;
                case "dcmUPSEventSCUKeepAlive":
                    arcAE.setUPSEventSCUKeepAlive(reader.intValue());
                    break;
                case "dcmStorePermissionServiceErrorCommentPattern":
                    arcAE.setStorePermissionServiceErrorCommentPattern(Pattern.compile(reader.stringValue()));
                    break;
                case "dcmStorePermissionServiceErrorCodePattern":
                    arcAE.setStorePermissionServiceErrorCodePattern(Pattern.compile(reader.stringValue()));
                    break;
                case "dcmRetrieveAET":
                    arcAE.setRetrieveAETitles(reader.stringArray());
                    break;
                case "dcmReturnRetrieveAET":
                    arcAE.setReturnRetrieveAETitles(reader.stringArray());
                    break;
                case "dcmExternalRetrieveAEDestination":
                    arcAE.setExternalRetrieveAEDestination(reader.stringValue());
                    break;
                case "dcmAcceptedMoveDestination":
                    arcAE.setAcceptedMoveDestinations(reader.stringArray());
                    break;
                case "dcmValidateCallingAEHostname":
                    arcAE.setValidateCallingAEHostname(reader.booleanValue());
                    break;
                case "hl7PSUSendingApplication":
                    arcAE.setHL7PSUSendingApplication(reader.stringValue());
                    break;
                case "hl7PSUReceivingApplication":
                    arcAE.setHL7PSUReceivingApplications(reader.stringArray());
                    break;
                case "hl7PSUDelay":
                    arcAE.setHL7PSUDelay(Duration.valueOf(reader.stringValue()));
                    break;
                case "hl7PSUTimeout":
                    arcAE.setHL7PSUTimeout(Duration.valueOf(reader.stringValue()));
                    break;
                case "hl7PSUOnTimeout":
                    arcAE.setHL7PSUOnTimeout(reader.booleanValue());
                    break;
                case "hl7PSUMWL":
                    arcAE.setHL7PSUMWL(reader.booleanValue());
                    break;
                case "dcmAcceptConflictingPatientID":
                    arcAE.setAcceptConflictingPatientID(AcceptConflictingPatientID.valueOf(reader.stringValue()));
                    break;
                case "dcmCopyMoveUpdatePolicy":
                    arcAE.setCopyMoveUpdatePolicy(Attributes.UpdatePolicy.valueOf(reader.stringValue()));
                    break;
                case "dcmLinkMWLEntryUpdatePolicy":
                    arcAE.setLinkMWLEntryUpdatePolicy(Attributes.UpdatePolicy.valueOf(reader.stringValue()));
                    break;
                case "dcmStorageVerificationPolicy":
                    arcAE.setStorageVerificationPolicy(StorageVerificationPolicy.valueOf(reader.stringValue()));
                    break;
                case "dcmStorageVerificationUpdateLocationStatus":
                    arcAE.setStorageVerificationUpdateLocationStatus(reader.booleanValue());
                    break;
                case "dcmStorageVerificationStorageID":
                    arcAE.setStorageVerificationStorageIDs(reader.stringArray());
                    break;
                case "dcmStorageVerificationInitialDelay":
                    arcAE.setStorageVerificationInitialDelay(Period.parse(reader.stringValue()));
                    break;
                case "dcmUpdateLocationStatusOnRetrieve":
                    arcAE.setUpdateLocationStatusOnRetrieve(reader.booleanValue());
                    break;
                case "dcmStorageVerificationOnRetrieve":
                    arcAE.setStorageVerificationOnRetrieve(reader.booleanValue());
                    break;
                case "dcmInvokeImageDisplayPatientURL":
                    arcAE.setInvokeImageDisplayPatientURL(reader.stringValue());
                    break;
                case "dcmInvokeImageDisplayStudyURL":
                    arcAE.setInvokeImageDisplayStudyURL(reader.stringValue());
                    break;
                case "dcmRejectConflictingPatientAttribute":
                    arcAE.setRejectConflictingPatientAttribute(TagUtils.fromHexStrings(reader.stringArray()));
                    break;
                case "dcmRelationalQueryNegotiationLenient":
                    arcAE.setRelationalQueryNegotiationLenient(reader.booleanValue());
                    break;
                case "dcmRelationalRetrieveNegotiationLenient":
                    arcAE.setRelationalRetrieveNegotiationLenient(reader.booleanValue());
                    break;
                case "dcmStowRetiredTransferSyntax":
                    arcAE.setStowRetiredTransferSyntax(reader.booleanValue());
                    break;
                case "dcmStowExcludeAPPMarkers":
                    arcAE.setStowExcludeAPPMarkers(reader.booleanValue());
                    break;
                case "dcmWadoThumbnailViewport":
                    arcAE.setWadoThumbnailViewPort(reader.stringValue());
                    break;
                case "dcmRestrictRetrieveAccordingTransferCapabilities":
                    arcAE.setRestrictRetrieveAccordingTransferCapabilities(
                            RestrictRetrieveAccordingTransferCapabilities.valueOf(reader.stringValue()));
                    break;
                case "dcmExportRule":
                    loadExportRule(arcAE.getExportRules(), reader);
                    break;
                case "dcmExportPriorsRule":
                    loadExportPriorsRules(arcAE.getExportPriorsRules(), reader);
                    break;
                case "dcmArchiveCompressionRule":
                    loadArchiveCompressionRule(arcAE.getCompressionRules(), reader);
                    break;
                case "dcmStoreAccessControlIDRule":
                    loadStoreAccessControlIDRule(arcAE.getStoreAccessControlIDRules(), reader);
                    break;
                case "dcmArchiveAttributeCoercion":
                    loadArchiveAttributeCoercion(arcAE.getAttributeCoercions(), reader, config);
                    break;
                case "dcmStudyRetentionPolicy":
                    loadStudyRetentionPolicy(arcAE.getStudyRetentionPolicies(), reader);
                    break;
                case "dcmRSForwardRule":
                    loadRSForwardRules(arcAE.getRSForwardRules(), reader);
                    break;
                default:
                    reader.skipUnknownProperty();
            }
        }
    }

}
