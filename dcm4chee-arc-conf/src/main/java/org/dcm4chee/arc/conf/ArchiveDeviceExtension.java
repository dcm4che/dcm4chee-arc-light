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

import org.dcm4che3.data.*;
import org.dcm4che3.io.BasicBulkDataDescriptor;
import org.dcm4che3.io.BulkDataDescriptor;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.net.DeviceExtension;
import org.dcm4che3.soundex.FuzzyStr;
import org.dcm4che3.util.ByteUtils;
import org.dcm4che3.util.StringUtils;

import javax.json.JsonValue;
import javax.servlet.http.HttpServletRequest;
import java.time.Period;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jul 2015
 */
public class ArchiveDeviceExtension extends DeviceExtension {

    public static final String AUDIT_UNKNOWN_STUDY_INSTANCE_UID = "1.2.40.0.13.1.15.110.3.165.1";
    public static final String AUDIT_UNKNOWN_PATIENT_ID = "<none>";
    public static final String JBOSS_SERVER_TEMP_DIR = "${jboss.server.temp.dir}";
    public static final String DEFAULT_WADO_ZIP_ENTRY_NAME_FORMAT =
            "DICOM/{0020000D,hash}/{0020000E,hash}/{00080018,hash}.dcm";
    public static final String WADO_THUMBNAIL_VIEWPORT = "64,64";
    public static final String MWL_ACCESSION_NUMBER_GENERATOR = "AccessionNumber";
    public static final String MWL_REQUESTED_PROCEDURE_ID_GENERATOR = "RequestedProcedureID";
    public static final String MWL_SCHEDULED_PROCEDURE_STEP_ID_GENERATOR = "ScheduledProcedureStepID";

    private volatile String defaultCharacterSet;
    private volatile String upsWorklistLabel;
    private volatile String[] upsEventSCUs = {};
    private volatile int upsEventSCUKeepAlive;
    private volatile boolean upsUpdateWithoutTransactionUID;
    private volatile String fuzzyAlgorithmClass;
    private volatile String bulkDataDescriptorID;
    private volatile String[] seriesMetadataStorageIDs = {};
    private volatile Duration seriesMetadataDelay;
    private volatile Duration seriesMetadataPollingInterval;
    private volatile int seriesMetadataFetchSize = 100;
    private volatile int seriesMetadataThreads = 1;
    private volatile int seriesMetadataMaxRetries = 0;
    private volatile Duration seriesMetadataRetryInterval;
    private volatile boolean purgeInstanceRecords;
    private volatile Duration purgeInstanceRecordsDelay;
    private volatile Duration purgeInstanceRecordsPollingInterval;
    private volatile int purgeInstanceRecordsFetchSize = 100;
    private volatile Duration deleteUPSPollingInterval;
    private volatile int deleteUPSFetchSize = 100;
    private volatile Duration deleteUPSCompletedDelay;
    private volatile Duration deleteUPSCanceledDelay;
    private volatile Duration upsProcessingPollingInterval;
    private volatile int upsProcessingFetchSize = 100;
    private volatile Duration taskPollingInterval;
    private volatile int taskFetchSize = 100;
    private volatile OverwritePolicy overwritePolicy = OverwritePolicy.NEVER;
    private volatile boolean recordAttributeModification = true;
    private volatile ShowPatientInfo showPatientInfoInSystemLog = ShowPatientInfo.PLAIN_TEXT;
    private volatile ShowPatientInfo showPatientInfoInAuditLog = ShowPatientInfo.PLAIN_TEXT;
    private volatile String bulkDataSpoolDirectory = JBOSS_SERVER_TEMP_DIR;
    private volatile boolean validateCallingAEHostname = false;
    private volatile boolean sendPendingCGet = false;
    private volatile Duration sendPendingCMoveInterval;
    private volatile boolean personNameComponentOrderInsensitiveMatching = false;
    private volatile int queryFetchSize = 100;
    private volatile int queryMaxNumberOfResults = 0;
    private volatile int qidoMaxNumberOfResults = 0;
    private volatile boolean qidoETag = false;
    private volatile boolean filterByIssuerOfPatientID = false;
    private volatile boolean matchSOPClassOnInstanceLevel = false;
    private volatile boolean wadoMetadataWithoutPrivate = false;
    private volatile boolean wadoIgnorePresentationLUTShape = false;
    private volatile String wadoThumbnailViewPort = WADO_THUMBNAIL_VIEWPORT;
    private volatile String wadoZIPEntryNameFormat = DEFAULT_WADO_ZIP_ENTRY_NAME_FORMAT;
    private volatile String wadoSR2HtmlTemplateURI;
    private volatile String wadoSR2TextTemplateURI;
    private volatile String wadoCDA2HtmlTemplateURI;
    private volatile String patientUpdateTemplateURI;
    private volatile String importReportTemplateURI;
    private volatile String scheduleProcedureTemplateURI;
    private volatile String unzipVendorDataToURI;
    private volatile String outgoingPatientUpdateTemplateURI;
    private volatile String[] mppsForwardDestinations = {};
    private volatile String[] ianDestinations = {};
    private volatile Duration ianDelay;
    private volatile Duration ianTimeout;
    private volatile boolean ianOnTimeout;
    private volatile Duration ianTaskPollingInterval;
    private volatile int ianTaskFetchSize = 100;
    private volatile String spanningCFindSCP;
    private volatile String[] spanningCFindSCPRetrieveAETitles = {};
    private volatile SpanningCFindSCPPolicy spanningCFindSCPPolicy = SpanningCFindSCPPolicy.REPLACE;
    private volatile String fallbackCMoveSCP;
    private volatile String fallbackCMoveSCPDestination;
    private volatile String fallbackCMoveSCPCallingAET;
    private volatile String fallbackCMoveSCPLeadingCFindSCP;
    private volatile int fallbackCMoveSCPRetries;
    private volatile String fallbackWadoURIWebApplication;
    private volatile int fallbackWadoURIHttpStatusCode = 303;
    private volatile String externalRetrieveAEDestination;
    private volatile String xdsiImagingDocumentSourceAETitle;
    private volatile String alternativeCMoveSCP;
    private volatile boolean retrieveTaskWarningOnNoMatch;
    private volatile boolean retrieveTaskWarningOnWarnings;
    private volatile Duration deleteRejectedPollingInterval;
    private volatile int deleteRejectedFetchSize = 100;
    private volatile Duration purgeStoragePollingInterval;
    private volatile int purgeStorageFetchSize = 100;
    private volatile int deleteStudyBatchSize = 10;
    private volatile int deleteStudyChunkSize = 100;
    private volatile boolean deletePatientOnDeleteLastStudy = false;
    private volatile Duration failedToDeletePollingInterval;
    private volatile int failedToDeleteFetchSize = 100;
    private volatile Duration maxAccessTimeStaleness;
    private volatile Duration aeCacheStaleTimeout;
    private volatile Duration leadingCFindSCPQueryCacheStaleTimeout;
    private volatile int leadingCFindSCPQueryCacheSize = 10;
    private volatile String auditSpoolDirectory = JBOSS_SERVER_TEMP_DIR;
    private volatile Duration auditPollingInterval;
    private volatile Duration auditAggregateDuration;
    private volatile String stowSpoolDirectory = JBOSS_SERVER_TEMP_DIR;
    private volatile String wadoSpoolDirectory = JBOSS_SERVER_TEMP_DIR;
    private volatile Duration purgeTaskPollingInterval;
    private volatile Duration purgeStgCmtPollingInterval;
    private volatile Duration purgeStgCmtCompletedDelay;
    private volatile Duration keyValueRetentionPollingInterval;
    private volatile int keyValueRetentionFetchSize = 100;
    private volatile Duration keyValueRetentionPeriod;
    private volatile Duration mwlPollingInterval;
    private volatile int mwlFetchSize = 100;
    private volatile Duration mwlImportInterval;
    private volatile String[] deleteMWLDelay = {};
    private volatile SPSStatus[] hideSPSWithStatusFrom = {};
    private volatile SPSStatus[] hideSPSWithStatusFromMWLRS = {};
    private volatile HL7ORUAction[] hl7ORUAction = {};
    private volatile String mwlAccessionNumberGenerator = MWL_ACCESSION_NUMBER_GENERATOR;
    private volatile String mwlRequestedProcedureIDGenerator = MWL_REQUESTED_PROCEDURE_ID_GENERATOR;
    private volatile String mwlScheduledProcedureStepIDGenerator = MWL_SCHEDULED_PROCEDURE_STEP_ID_GENERATOR;
    private volatile String hl7LogFilePattern;
    private volatile String hl7ErrorLogFilePattern;
    private volatile Duration rejectExpiredStudiesPollingInterval;
    private volatile ScheduleExpression[] rejectExpiredStudiesSchedules = {};
    private volatile int rejectExpiredStudiesFetchSize = 0;
    private volatile int rejectExpiredSeriesFetchSize = 0;
    private volatile String rejectExpiredStudiesAETitle;
    private volatile String fallbackCMoveSCPStudyOlderThan;
    private volatile String storePermissionServiceURL;
    private volatile String storePermissionServiceResponse;
    private volatile Pattern storePermissionServiceResponsePattern;
    private volatile Pattern storePermissionServiceExpirationDatePattern;
    private volatile Pattern storePermissionServiceErrorCommentPattern;
    private volatile Pattern storePermissionServiceErrorCodePattern;
    private volatile Duration storePermissionCacheStaleTimeout;
    private volatile int storePermissionCacheSize = 10;
    private volatile Duration mergeMWLCacheStaleTimeout;
    private volatile int mergeMWLCacheSize = 10;
    private volatile int storeUpdateDBMaxRetries = 3;
    private volatile int storeUpdateDBMaxRetryDelay = 1000;
    private volatile int storeUpdateDBMinRetryDelay = 500;
    private volatile AllowRejectionForDataRetentionPolicyExpired allowRejectionForDataRetentionPolicyExpired =
            AllowRejectionForDataRetentionPolicyExpired.EXPIRED_UNSET;
    private volatile AcceptMissingPatientID acceptMissingPatientID = AcceptMissingPatientID.CREATE;
    private volatile AllowDeletePatient allowDeletePatient = AllowDeletePatient.WITHOUT_STUDIES;
    private volatile AllowDeleteStudyPermanently allowDeleteStudyPermanently = AllowDeleteStudyPermanently.REJECTED;
    private volatile AcceptConflictingPatientID acceptConflictingPatientID = AcceptConflictingPatientID.MERGED;
    private volatile UserIdentityNegotiation userIdentityNegotiation = UserIdentityNegotiation.SUPPORTS;
    private volatile String userIdentityNegotiationRole;
    private volatile String userIdentityNegotiationKeycloakClientID;
    private volatile String[] retrieveAETitles = {};
    private volatile String[] returnRetrieveAETitles = {};
    private volatile String remapRetrieveURL;
    private volatile String remapRetrieveURLClientHost;
    private volatile String hl7PSUSendingApplication;
    private volatile String[] hl7PSUReceivingApplications = {};
    private volatile Duration hl7PSUDelay;
    private volatile Duration hl7PSUTimeout;
    private volatile boolean hl7PSUOnTimeout;
    private volatile int hl7PSUTaskFetchSize = 100;
    private volatile Duration hl7PSUTaskPollingInterval;
    private volatile boolean hl7PSUMWL = false;
    private volatile HL7PSUMWLMatchingKey hl7PSUMWLMatchingKey = HL7PSUMWLMatchingKey.StudyInstanceUID;
    private volatile boolean hl7PSUForRequestedProcedure = false;
    private volatile boolean hl7PSUPIDPV1 = false;
    private volatile String hl7PSURequestedProcedureID;
    private volatile String hl7PSUAccessionNumber;
    private volatile String hl7PSUFillerOrderNumber;
    private volatile String hl7PSUPlacerOrderNumber;
    private volatile HL7PSUMessageType hl7PSUMessageType = HL7PSUMessageType.OMG_O19;
    private volatile Conditions hl7PSUConditions = new Conditions();
    private volatile String hl7PSUMppsTemplateURI;
    private volatile String hl7PSUStudyTemplateURI;
    private volatile String proxyUpstreamURL;
    private volatile String atna2JsonFhirTemplateURI;
    private volatile String atna2XmlFhirTemplateURI;
    private volatile Attributes.UpdatePolicy copyMoveUpdatePolicy = Attributes.UpdatePolicy.PRESERVE;
    private volatile Attributes.UpdatePolicy linkMWLEntryUpdatePolicy = Attributes.UpdatePolicy.PRESERVE;
    private volatile boolean hl7TrackChangedPatientID = true;
    private volatile boolean auditSoftwareConfigurationVerbose = false;
    private volatile boolean hl7UseNullValue = false;
    private volatile String[] hl7ADTReceivingApplication = {};
    private volatile String hl7ADTSendingApplication;
    private volatile int queueTasksFetchSize = 100;
    private volatile int auditHL7MsgLimit = 1000;
    private volatile ScheduledProtocolCodeInOrder hl7ScheduledProtocolCodeInOrder =
            ScheduledProtocolCodeInOrder.OBR_4_4;
    private volatile ScheduledStationAETInOrder hl7ScheduledStationAETInOrder;
    private volatile String auditUnknownStudyInstanceUID = AUDIT_UNKNOWN_STUDY_INSTANCE_UID;
    private volatile String auditUnknownPatientID = AUDIT_UNKNOWN_PATIENT_ID;
    private volatile String rejectionNoteStorageAET;
    private volatile String uiConfigurationDeviceName;
    private volatile StorageVerificationPolicy storageVerificationPolicy = StorageVerificationPolicy.OBJECT_CHECKSUM;
    private volatile boolean storageVerificationUpdateLocationStatus;
    private volatile String[] storageVerificationStorageIDs = {};
    private volatile String storageVerificationAETitle;
    private volatile String storageVerificationBatchID;
    private volatile Period storageVerificationInitialDelay;
    private volatile Period storageVerificationPeriod;
    private volatile int storageVerificationMaxScheduled;
    private volatile Duration storageVerificationPollingInterval;
    private volatile ScheduleExpression[] storageVerificationSchedules = {};
    private volatile int storageVerificationFetchSize = 100;
    private volatile boolean updateLocationStatusOnRetrieve;
    private volatile boolean storageVerificationOnRetrieve;
    private volatile String compressionAETitle;
    private volatile Duration compressionPollingInterval;
    private volatile int compressionFetchSize = 100;
    private volatile int compressionThreads = 1;
    private volatile ScheduleExpression[] compressionSchedules = {};
    private volatile Duration diffTaskProgressUpdateInterval;
    private volatile String patientVerificationPDQServiceID;
    private volatile Duration patientVerificationPollingInterval;
    private volatile int patientVerificationFetchSize = 100;
    private volatile Duration patientVerificationMaxStaleness;
    private volatile Period patientVerificationPeriod;
    private volatile Period patientVerificationPeriodOnNotFound;
    private volatile Duration patientVerificationRetryInterval;
    private volatile int patientVerificationMaxRetries;
    private volatile boolean patientVerificationAdjustIssuerOfPatientID;
    private volatile HL7OrderMissingStudyIUIDPolicy hl7OrderMissingStudyIUIDPolicy = HL7OrderMissingStudyIUIDPolicy.GENERATE;
    private volatile HL7OrderMissingAdmissionIDPolicy hl7OrderMissingAdmissionIDPolicy = HL7OrderMissingAdmissionIDPolicy.ACCEPT;
    private volatile HL7ImportReportMissingAdmissionIDPolicy hl7ImportReportMissingAdmissionIDPolicy =
            HL7ImportReportMissingAdmissionIDPolicy.ACCEPT;
    private volatile HL7ImportReportMissingStudyIUIDPolicy hl7ImportReportMissingStudyIUIDPolicy =
            HL7ImportReportMissingStudyIUIDPolicy.GENERATE;
    private volatile String hl7ImportReportMissingStudyIUIDCFindSCP;
    private volatile HL7ReferredMergedPatientPolicy hl7ReferredMergedPatientPolicy =
            HL7ReferredMergedPatientPolicy.REJECT;
    private volatile String hl7DicomCharacterSet;
    private volatile boolean hl7VeterinaryUsePatientName;
    private volatile String hl7PatientArrivalMessageType;
    private volatile int csvUploadChunkSize = 100;
    private volatile boolean validateUID = true;
    private volatile boolean relationalQueryNegotiationLenient;
    private volatile boolean relationalRetrieveNegotiationLenient;
    private volatile int[] rejectConflictingPatientAttribute = {};
    private volatile int schedulerMinStartDelay = 60;
    private volatile boolean stowRetiredTransferSyntax;
    private volatile boolean stowExcludeAPPMarkers;
    private volatile boolean restrictRetrieveSilently;
    private volatile boolean stowQuicktime2MP4;
    private volatile boolean identifyPatientByAllAttributes;
    private volatile String changeRequesterAET;
    private volatile MultipleStoreAssociations[] multipleStoreAssociations = {};
    private volatile Duration studySizeDelay;
    private volatile Duration calculateStudySizePollingInterval;
    private volatile int calculateStudySizeFetchSize = 100;
    private volatile boolean calculateQueryAttributes;
    private volatile boolean storeImplementationVersionName = true;
    private volatile int supplementIssuerFetchSize = 100;
    private volatile int updateCharsetFetchSize = 100;
    private volatile Issuer auditAssigningAuthorityOfPatientID;

    private final EnumSet<VR> encodeAsJSONNumber = EnumSet.noneOf(VR.class);
    private final HashSet<String> wadoSupportedSRClasses = new HashSet<>();
    private final HashSet<String> wadoSupportedPRClasses = new HashSet<>();
    private final EnumMap<Entity,AttributeFilter> attributeFilters = new EnumMap<>(Entity.class);
    private final Map<AttributeSet.Type,Map<String,AttributeSet>> attributeSet = new EnumMap<>(AttributeSet.Type.class);
    private final Map<String, BasicBulkDataDescriptor> bulkDataDescriptorMap = new HashMap<>();
    private final Map<String, IDGenerator> idGenerators = new HashMap<>();
    private final Map<String, QueryRetrieveView> queryRetrieveViewMap = new HashMap<>();
    private final Map<String, StorageDescriptor> storageDescriptorMap = new HashMap<>();
    private final Map<String, QueueDescriptor> queueDescriptorMap = new HashMap<>();
    private final Map<String, MetricsDescriptor> metricsDescriptorMap = new HashMap<>();
    private final Map<String, ExporterDescriptor> exporterDescriptorMap = new HashMap<>();
    private final Map<String, PDQServiceDescriptor> pdqServiceDescriptorMap = new HashMap<>();
    private final Map<String, RejectionNote> rejectionNoteMap = new HashMap<>();
    private final List<UPSOnStore> upsOnStoreList = new ArrayList<>();
    private final List<UPSOnHL7> upsOnHL7List = new ArrayList<>();
    private final List<UPSOnUPSCompleted> upsOnUPSCompletedList = new ArrayList<>();
    private final List<UPSProcessingRule> upsProcessingRuleList = new ArrayList<>();
    private final List<ExportRule> exportRules = new ArrayList<>();
    private final List<ExportPriorsRule> exportPriorsRules = new ArrayList<>();
    private final List<MPPSForwardRule> mppsForwardRule = new ArrayList<>();
    private final List<HL7ExportRule> hl7ExportRules = new ArrayList<>();
    private final List<HL7PrefetchRule> hl7PrefetchRules = new ArrayList<>();
    private final List<RSForwardRule> rsForwardRules = new ArrayList<>();
    private final List<HL7ForwardRule> hl7ForwardRules = new ArrayList<>();
    private final List<HL7OrderScheduledStation> hl7OrderScheduledStations = new ArrayList<>();
    private final EnumMap<SPSStatus,HL7OrderSPSStatus> hl7OrderSPSStatuses = new EnumMap<>(SPSStatus.class);
    private final List<ArchiveCompressionRule> compressionRules = new ArrayList<>();
    private final List<StudyRetentionPolicy> studyRetentionPolicies = new ArrayList<>();
    private final List<HL7StudyRetentionPolicy> hl7StudyRetentionPolicies = new ArrayList<>();
    private final List<ArchiveAttributeCoercion> attributeCoercions = new ArrayList<>();
    private final List<ArchiveAttributeCoercion2> attributeCoercions2 = new ArrayList<>();
    private final List<StoreAccessControlIDRule> storeAccessControlIDRules = new ArrayList<>();
    private final List<MWLIdleTimeout> mwlIdleTimeoutList = new ArrayList<>();
    private final List<MWLImport> mwlImportList = new ArrayList<>();
    private final LinkedHashSet<String> hl7NoPatientCreateMessageTypes = new LinkedHashSet<>();
    private final Map<String,String> xRoadProperties = new HashMap<>();
    private final Map<String,String> impaxReportProperties = new HashMap<>();
    private final Map<String, String> importReportTemplateParams = new HashMap<>();
    private final Map<String, String> hl7PSUTemplateParams = new HashMap<>();
    private final Map<String, String> cStoreSCUOfCMoveSCP = new HashMap<>();
    private final Map<String, String> dicomCharsetNameMappings = new HashMap<>();
    private final Map<String, String> hl7CharsetNameMappings = new HashMap<>();
    private final Map<String, Integer> upsEventWebSocketQueueSizes = new HashMap<>();

    private transient FuzzyStr fuzzyStr;

    public String getDefaultCharacterSet() {
        return defaultCharacterSet;
    }

    public void setDefaultCharacterSet(String defaultCharacterSet) {
        this.defaultCharacterSet = defaultCharacterSet;
    }

    public String getUPSWorklistLabel() {
        return upsWorklistLabel;
    }

    public void setUPSWorklistLabel(String upsWorklistLabel) {
        this.upsWorklistLabel = upsWorklistLabel;
    }

    public String[] getUPSEventSCUs() {
        return upsEventSCUs;
    }

    public void setUPSEventSCUs(String[] upsEventSCUs) {
        this.upsEventSCUs = upsEventSCUs;
    }

    public int getUPSEventSCUKeepAlive() {
        return upsEventSCUKeepAlive;
    }

    public void setUPSEventSCUKeepAlive(int upsEventSCUKeepAlive) {
        this.upsEventSCUKeepAlive = upsEventSCUKeepAlive;
    }

    public boolean isUPSUpdateWithoutTransactionUID() {
        return upsUpdateWithoutTransactionUID;
    }

    public void setUPSUpdateWithoutTransactionUID(boolean upsUpdateWithoutTransactionUID) {
        this.upsUpdateWithoutTransactionUID = upsUpdateWithoutTransactionUID;
    }

    public String getFuzzyAlgorithmClass() {
        return fuzzyAlgorithmClass;
    }

    public void setFuzzyAlgorithmClass(String fuzzyAlgorithmClass) {
        this.fuzzyStr = fuzzyStr(fuzzyAlgorithmClass);
        this.fuzzyAlgorithmClass = fuzzyAlgorithmClass;
    }

    public FuzzyStr getFuzzyStr() {
        if (fuzzyStr == null)
            if (fuzzyAlgorithmClass == null)
                throw new IllegalStateException("No Fuzzy Algorithm Class configured");
            else
                fuzzyStr = fuzzyStr(fuzzyAlgorithmClass);
        return fuzzyStr;
    }

    private static FuzzyStr fuzzyStr(String s) {
        try {
            return (FuzzyStr) Class.forName(s).newInstance();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(s);
        }
    }

    public OverwritePolicy getOverwritePolicy() {
        return overwritePolicy;
    }

    public void setOverwritePolicy(OverwritePolicy overwritePolicy) {
        this.overwritePolicy = overwritePolicy;
    }

    public boolean isRecordAttributeModification() {
        return recordAttributeModification;
    }

    public void setRecordAttributeModification(boolean recordAttributeModification) {
        this.recordAttributeModification = recordAttributeModification;
    }

    public ShowPatientInfo getShowPatientInfoInSystemLog() {
        return showPatientInfoInSystemLog;
    }

    public ShowPatientInfo showPatientInfoInSystemLog() {
        return showPatientInfoInSystemLog != null ? showPatientInfoInSystemLog : ShowPatientInfo.PLAIN_TEXT;
    }

    public void setShowPatientInfoInSystemLog(ShowPatientInfo showPatientInfoInSystemLog) {
        this.showPatientInfoInSystemLog = showPatientInfoInSystemLog;
    }

    public ShowPatientInfo getShowPatientInfoInAuditLog() {
        return showPatientInfoInAuditLog;
    }

    public void setShowPatientInfoInAuditLog(ShowPatientInfo showPatientInfoInAuditLog) {
        this.showPatientInfoInAuditLog = showPatientInfoInAuditLog;
    }

    public ShowPatientInfo showPatientInfoInAuditLog() {
        return showPatientInfoInAuditLog != null ? showPatientInfoInAuditLog : ShowPatientInfo.PLAIN_TEXT;
    }

    public AcceptMissingPatientID getAcceptMissingPatientID() {
        return acceptMissingPatientID;
    }

    public void setAcceptMissingPatientID(AcceptMissingPatientID acceptMissingPatientID) {
        this.acceptMissingPatientID = acceptMissingPatientID;
    }

    public String getBulkDataSpoolDirectory() {
        return bulkDataSpoolDirectory;
    }

    public void setBulkDataSpoolDirectory(String bulkDataSpoolDirectory) {
        this.bulkDataSpoolDirectory = Objects.requireNonNull(bulkDataSpoolDirectory, "BulkDataSpoolDirectory");
    }

    public String getBulkDataDescriptorID() {
        return bulkDataDescriptorID;
    }

    public void setBulkDataDescriptorID(String bulkDataDescriptorID) {
        this.bulkDataDescriptorID = bulkDataDescriptorID;
    }

    public String[] getSeriesMetadataStorageIDs() {
        return seriesMetadataStorageIDs;
    }

    public void setSeriesMetadataStorageIDs(String... seriesMetadataStorageIDs) {
        Arrays.sort(this.seriesMetadataStorageIDs = seriesMetadataStorageIDs);
    }

    public Duration getSeriesMetadataDelay() {
        return seriesMetadataDelay;
    }

    public void setSeriesMetadataDelay(Duration seriesMetadataDelay) {
        this.seriesMetadataDelay = seriesMetadataDelay;
    }

    public Duration getSeriesMetadataPollingInterval() {
        return seriesMetadataPollingInterval;
    }

    public void setSeriesMetadataPollingInterval(Duration seriesMetadataPollingInterval) {
        this.seriesMetadataPollingInterval = seriesMetadataPollingInterval;
    }

    public int getSeriesMetadataFetchSize() {
        return seriesMetadataFetchSize;
    }

    public void setSeriesMetadataFetchSize(int seriesMetadataFetchSize) {
        this.seriesMetadataFetchSize =  greaterZero(seriesMetadataFetchSize, "seriesMetadataFetchSize");
    }

    public int getSeriesMetadataThreads() {
        return seriesMetadataThreads;
    }

    public void setSeriesMetadataThreads(int seriesMetadataThreads) {
        this.seriesMetadataThreads = seriesMetadataThreads;
    }

    public int getSeriesMetadataMaxRetries() {
        return seriesMetadataMaxRetries;
    }

    public void setSeriesMetadataMaxRetries(int seriesMetadataMaxRetries) {
        this.seriesMetadataMaxRetries = seriesMetadataMaxRetries;
    }

    public Duration getSeriesMetadataRetryInterval() {
        return seriesMetadataRetryInterval;
    }

    public void setSeriesMetadataRetryInterval(Duration seriesMetadataRetryInterval) {
        this.seriesMetadataRetryInterval = seriesMetadataRetryInterval;
    }

    public boolean isPurgeInstanceRecords() {
        return purgeInstanceRecords;
    }

    public void setPurgeInstanceRecords(boolean purgeInstanceRecords) {
        this.purgeInstanceRecords = purgeInstanceRecords;
    }

    public Duration getPurgeInstanceRecordsDelay() {
        return purgeInstanceRecordsDelay;
    }

    public void setPurgeInstanceRecordsDelay(Duration purgeInstanceRecordsDelay) {
        this.purgeInstanceRecordsDelay = purgeInstanceRecordsDelay;
    }

    public Duration getPurgeInstanceRecordsPollingInterval() {
        return purgeInstanceRecordsPollingInterval;
    }

    public void setPurgeInstanceRecordsPollingInterval(Duration purgeInstanceRecordsPollingInterval) {
        this.purgeInstanceRecordsPollingInterval = purgeInstanceRecordsPollingInterval;
    }

    public int getPurgeInstanceRecordsFetchSize() {
        return purgeInstanceRecordsFetchSize;
    }

    public void setPurgeInstanceRecordsFetchSize(int purgeInstanceRecordsFetchSize) {
        this.purgeInstanceRecordsFetchSize =  greaterZero(purgeInstanceRecordsFetchSize, "purgeInstanceRecordsFetchSize");
    }

    public Duration getDeleteUPSPollingInterval() {
        return deleteUPSPollingInterval;
    }

    public void setDeleteUPSPollingInterval(Duration deleteUPSPollingInterval) {
        this.deleteUPSPollingInterval = deleteUPSPollingInterval;
    }

    public int getDeleteUPSFetchSize() {
        return deleteUPSFetchSize;
    }

    public void setDeleteUPSFetchSize(int deleteUPSFetchSize) {
        this.deleteUPSFetchSize = deleteUPSFetchSize;
    }

    public Duration getDeleteUPSCompletedDelay() {
        return deleteUPSCompletedDelay;
    }

    public void setDeleteUPSCompletedDelay(Duration deleteUPSCompletedDelay) {
        this.deleteUPSCompletedDelay = deleteUPSCompletedDelay;
    }

    public Duration getDeleteUPSCanceledDelay() {
        return deleteUPSCanceledDelay;
    }

    public void setDeleteUPSCanceledDelay(Duration deleteUPSCanceledDelay) {
        this.deleteUPSCanceledDelay = deleteUPSCanceledDelay;
    }

    public Duration getUPSProcessingPollingInterval() {
        return upsProcessingPollingInterval;
    }

    public void setUPSProcessingPollingInterval(Duration upsProcessingPollingInterval) {
        this.upsProcessingPollingInterval = upsProcessingPollingInterval;
    }

    public int getUPSProcessingFetchSize() {
        return upsProcessingFetchSize;
    }

    public void setUPSProcessingFetchSize(int upsProcessingFetchSize) {
        this.upsProcessingFetchSize = upsProcessingFetchSize;
    }

    public Duration getTaskPollingInterval() {
        return taskPollingInterval;
    }

    public void setTaskPollingInterval(Duration taskPollingInterval) {
        this.taskPollingInterval = taskPollingInterval;
    }

    public int getTaskFetchSize() {
        return taskFetchSize;
    }

    public void setTaskFetchSize(int taskFetchSize) {
        this.taskFetchSize = taskFetchSize;
    }

    public boolean isPersonNameComponentOrderInsensitiveMatching() {
        return personNameComponentOrderInsensitiveMatching;
    }

    public void setPersonNameComponentOrderInsensitiveMatching(boolean personNameComponentOrderInsensitiveMatching) {
        this.personNameComponentOrderInsensitiveMatching = personNameComponentOrderInsensitiveMatching;
    }

    public boolean isValidateCallingAEHostname() {
        return validateCallingAEHostname;
    }

    public void setValidateCallingAEHostname(boolean validateCallingAEHostname) {
        this.validateCallingAEHostname = validateCallingAEHostname;
    }

    public boolean isSendPendingCGet() {
        return sendPendingCGet;
    }

    public void setSendPendingCGet(boolean sendPendingCGet) {
        this.sendPendingCGet = sendPendingCGet;
    }

    public Duration getSendPendingCMoveInterval() {
        return sendPendingCMoveInterval;
    }

    public void setSendPendingCMoveInterval(Duration sendPendingCMoveInterval) {
        this.sendPendingCMoveInterval = sendPendingCMoveInterval;
    }

    public VR[] getEncodeAsJSONNumber() {
        return encodeAsJSONNumber.toArray(new VR[0]);
    }

    public void setEncodeAsJSONNumber(VR[] vrs) {
        Stream.of(vrs).forEach(ArchiveDeviceExtension::requireIS_DS_SV_UV);
        this.encodeAsJSONNumber.clear();
        this.encodeAsJSONNumber.addAll(Arrays.asList(vrs));
    }

    public JSONWriter encodeAsJSONNumber(JSONWriter writer) {
        return encodeAsJSONNumber(writer, encodeAsJSONNumber);
    }

    static JSONWriter encodeAsJSONNumber(JSONWriter writer, EnumSet<VR> encodeAsJSONNumber) {
        encodeAsJSONNumber.forEach(vr -> writer.setJsonType(vr, JsonValue.ValueType.NUMBER));
        return writer;
    }

    static void requireIS_DS_SV_UV(VR vr) {
        if (vr != VR.DS && vr != VR.IS && vr != VR.SV && vr != VR.UV)
            throw new IllegalArgumentException("vr:" + vr);
    }

    public String[] getWadoSupportedSRClasses() {
        return wadoSupportedSRClasses.toArray(StringUtils.EMPTY_STRING);
    }

    public void setWadoSupportedSRClasses(String... wadoSupportedSRClasses) {
        this.wadoSupportedSRClasses.clear();
        this.wadoSupportedSRClasses.addAll(Arrays.asList(wadoSupportedSRClasses));
    }

    public boolean isWadoSupportedSRClass(String cuid) {
        return wadoSupportedSRClasses.contains(cuid);
    }

    public String[] getWadoSupportedPRClasses() {
        return wadoSupportedPRClasses.toArray(StringUtils.EMPTY_STRING);
    }

    public void setWadoSupportedPRClasses(String... wadoSupportedPRClasses) {
        this.wadoSupportedPRClasses.clear();
        this.wadoSupportedPRClasses.addAll(Arrays.asList(wadoSupportedPRClasses));
    }

    public boolean isWadoSupportedPRClass(String cuid) {
        return wadoSupportedPRClasses.contains(cuid);
    }

    public boolean isWadoMetadataWithoutPrivate() {
        return wadoMetadataWithoutPrivate;
    }

    public void setWadoMetadataWithoutPrivate(boolean wadoMetadataWithoutPrivate) {
        this.wadoMetadataWithoutPrivate = wadoMetadataWithoutPrivate;
    }

    public boolean isWadoIgnorePresentationLUTShape() {
        return wadoIgnorePresentationLUTShape;
    }

    public void setWadoIgnorePresentationLUTShape(boolean wadoIgnorePresentationLUTShape) {
        this.wadoIgnorePresentationLUTShape = wadoIgnorePresentationLUTShape;
    }

    public String getWadoThumbnailViewPort() {
        return wadoThumbnailViewPort;
    }

    public void setWadoThumbnailViewPort(String wadoThumbnailViewPort) {
        if (!Pattern.matches("[1-9]\\d{0,2},[1-9]\\d{0,2}", wadoThumbnailViewPort))
            throw new IllegalArgumentException(wadoThumbnailViewPort);
        this.wadoThumbnailViewPort = wadoThumbnailViewPort;
    }

    public String getWadoZIPEntryNameFormat() {
        return wadoZIPEntryNameFormat;
    }

    public void setWadoZIPEntryNameFormat(String wadoZIPEntryNameFormat) {
        this.wadoZIPEntryNameFormat = wadoZIPEntryNameFormat;
    }

    public String getWadoSR2HtmlTemplateURI() {
        return wadoSR2HtmlTemplateURI;
    }

    public void setWadoSR2HtmlTemplateURI(String wadoSR2HtmlTemplateURI) {
        this.wadoSR2HtmlTemplateURI = wadoSR2HtmlTemplateURI;
    }

    public String getWadoSR2TextTemplateURI() {
        return wadoSR2TextTemplateURI;
    }

    public void setWadoSR2TextTemplateURI(String wadoSR2TextTemplateURI) {
        this.wadoSR2TextTemplateURI = wadoSR2TextTemplateURI;
    }

    public String getWadoCDA2HtmlTemplateURI() {
        return wadoCDA2HtmlTemplateURI;
    }

    public void setWadoCDA2HtmlTemplateURI(String wadoCDA2HtmlTemplateURI) {
        this.wadoCDA2HtmlTemplateURI = wadoCDA2HtmlTemplateURI;
    }

    public String getPatientUpdateTemplateURI() {
        return patientUpdateTemplateURI;
    }

    public void setPatientUpdateTemplateURI(String patientUpdateTemplateURI) {
        this.patientUpdateTemplateURI = patientUpdateTemplateURI;
    }

    public String getImportReportTemplateURI() {
        return importReportTemplateURI;
    }

    public void setImportReportTemplateURI(String importReportTemplateURI) {
        this.importReportTemplateURI = importReportTemplateURI;
    }

    public String getScheduleProcedureTemplateURI() {
        return scheduleProcedureTemplateURI;
    }

    public void setScheduleProcedureTemplateURI(String scheduleProcedureTemplateURI) {
        this.scheduleProcedureTemplateURI = scheduleProcedureTemplateURI;
    }

    public String getUnzipVendorDataToURI() {
        return unzipVendorDataToURI;
    }

    public void setUnzipVendorDataToURI(String unzipVendorDataToURI) {
        this.unzipVendorDataToURI = unzipVendorDataToURI;
    }

    public String getOutgoingPatientUpdateTemplateURI() {
        return outgoingPatientUpdateTemplateURI;
    }

    public void setOutgoingPatientUpdateTemplateURI(String outgoingPatientUpdateTemplateURI) {
        this.outgoingPatientUpdateTemplateURI = outgoingPatientUpdateTemplateURI;
    }

    public String[] getMppsForwardDestinations() {
        return mppsForwardDestinations;
    }

    public void setMppsForwardDestinations(String... mppsForwardDestinations) {
        this.mppsForwardDestinations = mppsForwardDestinations;
    }

    public String[] getIanDestinations() {
        return ianDestinations;
    }

    public void setIanDestinations(String... ianDestinations) {
        this.ianDestinations = ianDestinations;
    }

    public Duration getIanDelay() {
        return ianDelay;
    }

    public void setIanDelay(Duration ianDelay) {
        this.ianDelay = ianDelay;
    }

    public Duration getIanTimeout() {
        return ianTimeout;
    }

    public void setIanTimeout(Duration ianTimeout) {
        this.ianTimeout = ianTimeout;
    }

    public boolean isIanOnTimeout() {
        return ianOnTimeout;
    }

    public void setIanOnTimeout(boolean ianOnTimeout) {
        this.ianOnTimeout = ianOnTimeout;
    }

    public Duration getIanTaskPollingInterval() {
        return ianTaskPollingInterval;
    }

    public void setIanTaskPollingInterval(Duration ianTaskPollingInterval) {
        this.ianTaskPollingInterval = ianTaskPollingInterval;
    }

    public int getIanTaskFetchSize() {
        return ianTaskFetchSize;
    }

    public void setIanTaskFetchSize(int ianTaskFetchSize) {
        this.ianTaskFetchSize = greaterZero(ianTaskFetchSize, "ianTaskFetchSize");
    }

    public String getSpanningCFindSCP() {
        return spanningCFindSCP;
    }

    public void setSpanningCFindSCP(String spanningCFindSCP) {
        this.spanningCFindSCP = spanningCFindSCP;
    }

    public String[] getSpanningCFindSCPRetrieveAETitles() {
        return spanningCFindSCPRetrieveAETitles;
    }

    public void setSpanningCFindSCPRetrieveAETitles(String[] spanningCFindSCPRetrieveAETitles) {
        this.spanningCFindSCPRetrieveAETitles = spanningCFindSCPRetrieveAETitles;
    }

    public SpanningCFindSCPPolicy getSpanningCFindSCPPolicy() {
        return spanningCFindSCPPolicy;
    }

    public void setSpanningCFindSCPPolicy(SpanningCFindSCPPolicy spanningCFindSCPPolicy) {
        this.spanningCFindSCPPolicy = spanningCFindSCPPolicy;
    }

    public String getFallbackCMoveSCP() {
        return fallbackCMoveSCP;
    }

    public void setFallbackCMoveSCP(String fallbackCMoveSCP) {
        this.fallbackCMoveSCP = fallbackCMoveSCP;
    }

    public String getFallbackCMoveSCPDestination() {
        return fallbackCMoveSCPDestination;
    }

    public void setFallbackCMoveSCPDestination(String fallbackCMoveSCPDestination) {
        this.fallbackCMoveSCPDestination = fallbackCMoveSCPDestination;
    }

    public String getFallbackCMoveSCPCallingAET() {
        return fallbackCMoveSCPCallingAET;
    }

    public void setFallbackCMoveSCPCallingAET(String fallbackCMoveSCPCallingAET) {
        this.fallbackCMoveSCPCallingAET = fallbackCMoveSCPCallingAET;
    }

    public String getFallbackCMoveSCPLeadingCFindSCP() {
        return fallbackCMoveSCPLeadingCFindSCP;
    }

    public void setFallbackCMoveSCPLeadingCFindSCP(String fallbackCMoveSCPLeadingCFindSCP) {
        this.fallbackCMoveSCPLeadingCFindSCP = fallbackCMoveSCPLeadingCFindSCP;
    }

    public int getFallbackCMoveSCPRetries() {
        return fallbackCMoveSCPRetries;
    }

    public void setFallbackCMoveSCPRetries(int fallbackCMoveSCPRetries) {
        this.fallbackCMoveSCPRetries = fallbackCMoveSCPRetries;
    }

    public String getFallbackWadoURIWebApplication() {
        return fallbackWadoURIWebApplication;
    }

    public void setFallbackWadoURIWebApplication(String fallbackWadoURIWebApplication) {
        this.fallbackWadoURIWebApplication = fallbackWadoURIWebApplication;
    }

    public int getFallbackWadoURIHttpStatusCode() {
        return fallbackWadoURIHttpStatusCode;
    }

    public void setFallbackWadoURIHttpStatusCode(int fallbackWadoURIHttpStatusCode) {
        this.fallbackWadoURIHttpStatusCode = fallbackWadoURIHttpStatusCode;
    }

    public String getExternalRetrieveAEDestination() {
        return externalRetrieveAEDestination;
    }

    public void setExternalRetrieveAEDestination(String externalRetrieveAEDestination) {
        this.externalRetrieveAEDestination = externalRetrieveAEDestination;
    }

    public String getXDSiImagingDocumentSourceAETitle() {
        return xdsiImagingDocumentSourceAETitle;
    }

    public void setXDSiImagingDocumentSourceAETitle(String xdsiImagingDocumentSourceAETitle) {
        this.xdsiImagingDocumentSourceAETitle = xdsiImagingDocumentSourceAETitle;
    }

    public String getAlternativeCMoveSCP() {
        return alternativeCMoveSCP;
    }

    public void setAlternativeCMoveSCP(String alternativeCMoveSCP) {
        this.alternativeCMoveSCP = alternativeCMoveSCP;
    }

    public int getQueryFetchSize() {
        return queryFetchSize;
    }

    public void setQueryFetchSize(int queryFetchSize) {
       this.queryFetchSize = greaterOrEqualsZero(queryFetchSize, "queryFetchSize");
    }

    public int getQueryMaxNumberOfResults() {
        return queryMaxNumberOfResults;
    }

    public void setQueryMaxNumberOfResults(int queryMaxNumberOfResults) {
        this.queryMaxNumberOfResults = queryMaxNumberOfResults;
    }

    public int getQidoMaxNumberOfResults() {
        return qidoMaxNumberOfResults;
    }

    public void setQidoMaxNumberOfResults(int qidoMaxNumberOfResults) {
        this.qidoMaxNumberOfResults = qidoMaxNumberOfResults;
    }

    public boolean isQidoETag() {
        return qidoETag;
    }

    public void setQidoETag(boolean qidoETag) {
        this.qidoETag = qidoETag;
    }

    public boolean isFilterByIssuerOfPatientID() {
        return filterByIssuerOfPatientID;
    }

    public void setFilterByIssuerOfPatientID(boolean filterByIssuerOfPatientID) {
        this.filterByIssuerOfPatientID = filterByIssuerOfPatientID;
    }

    public boolean isMatchSOPClassOnInstanceLevel() {
        return matchSOPClassOnInstanceLevel;
    }

    public void setMatchSOPClassOnInstanceLevel(boolean matchSOPClassOnInstanceLevel) {
        this.matchSOPClassOnInstanceLevel = matchSOPClassOnInstanceLevel;
    }

    public Duration getDeleteRejectedPollingInterval() {
        return deleteRejectedPollingInterval;
    }

    public void setDeleteRejectedPollingInterval(Duration deleteRejectedPollingInterval) {
        this.deleteRejectedPollingInterval = deleteRejectedPollingInterval;
    }

    public int getDeleteRejectedFetchSize() {
        return deleteRejectedFetchSize;
    }

    public void setDeleteRejectedFetchSize(int deleteRejectedFetchSize) {
        this.deleteRejectedFetchSize =  greaterZero(deleteRejectedFetchSize, "deleteRejectedFetchSize");

    }
    public Duration getPurgeStoragePollingInterval() {
        return purgeStoragePollingInterval;
    }

    public void setPurgeStoragePollingInterval(Duration purgeStoragePollingInterval) {
        this.purgeStoragePollingInterval = purgeStoragePollingInterval;
    }

    public int getPurgeStorageFetchSize() {
        return purgeStorageFetchSize;
    }

    public void setPurgeStorageFetchSize(int purgeStorageFetchSize) {
        this.purgeStorageFetchSize = greaterZero(purgeStorageFetchSize, "purgeStorageFetchSize");
    }

    public int getDeleteStudyBatchSize() {
        return deleteStudyBatchSize;
    }

    public void setDeleteStudyBatchSize(int deleteStudyBatchSize) {
        this.deleteStudyBatchSize = greaterZero(deleteStudyBatchSize, "deleteStudyBatchSize");
    }

    public int getDeleteStudyChunkSize() {
        return deleteStudyChunkSize;
    }

    public void setDeleteStudyChunkSize(int deleteStudyChunkSize) {
        this.deleteStudyChunkSize = greaterZero(deleteStudyChunkSize, "deleteStudyChunkSize");
    }

    public boolean isDeletePatientOnDeleteLastStudy() {
        return deletePatientOnDeleteLastStudy;
    }

    public void setDeletePatientOnDeleteLastStudy(boolean deletePatientOnDeleteLastStudy) {
        this.deletePatientOnDeleteLastStudy = deletePatientOnDeleteLastStudy;
    }

    public Duration getFailedToDeletePollingInterval() {
        return failedToDeletePollingInterval;
    }

    public void setFailedToDeletePollingInterval(Duration failedToDeletePollingInterval) {
        this.failedToDeletePollingInterval = failedToDeletePollingInterval;
    }

    public int getFailedToDeleteFetchSize() {
        return failedToDeleteFetchSize;
    }

    public void setFailedToDeleteFetchSize(int failedToDeleteFetchSize) {
        this.failedToDeleteFetchSize = failedToDeleteFetchSize;
    }

    public Duration getMaxAccessTimeStaleness() {
        return maxAccessTimeStaleness;
    }

    public void setMaxAccessTimeStaleness(Duration maxAccessTimeStaleness) {
        this.maxAccessTimeStaleness = maxAccessTimeStaleness;
    }

    public Duration getAECacheStaleTimeout() {
        return aeCacheStaleTimeout;
    }

    public void setAECacheStaleTimeout(Duration aeCacheStaleTimeout) {
        this.aeCacheStaleTimeout = aeCacheStaleTimeout;
    }

    public int getAECacheStaleTimeoutSeconds() {
        return toSeconds(aeCacheStaleTimeout);
    }

    public Duration getLeadingCFindSCPQueryCacheStaleTimeout() {
        return leadingCFindSCPQueryCacheStaleTimeout;
    }

    public void setLeadingCFindSCPQueryCacheStaleTimeout(Duration leadingCFindSCPQueryCacheStaleTimeout) {
        this.leadingCFindSCPQueryCacheStaleTimeout = leadingCFindSCPQueryCacheStaleTimeout;
    }

    public int getLeadingCFindSCPQueryCacheStaleTimeoutSeconds() {
        return toSeconds(leadingCFindSCPQueryCacheStaleTimeout);
    }

    private static int toSeconds(Duration timeout) {
        return timeout != null ? (int) timeout.getSeconds() : 0;
    }

    public int getLeadingCFindSCPQueryCacheSize() {
        return leadingCFindSCPQueryCacheSize;
    }

    public void setLeadingCFindSCPQueryCacheSize(int leadingCFindSCPQueryCacheSize) {
        this.leadingCFindSCPQueryCacheSize =
                greaterZero(leadingCFindSCPQueryCacheSize, "leadingCFindSCPQueryCacheSize");
    }

    public String getAuditSpoolDirectory() {
        return auditSpoolDirectory;
    }

    public void setAuditSpoolDirectory(String auditSpoolDirectory) {
        this.auditSpoolDirectory = Objects.requireNonNull(auditSpoolDirectory, "AuditSpoolDirectory");
    }

    public Duration getAuditPollingInterval() {
        return auditPollingInterval;
    }

    public void setAuditPollingInterval(Duration auditPollingInterval) {
        this.auditPollingInterval = auditPollingInterval;
    }

    public Duration getAuditAggregateDuration() {
        return auditAggregateDuration;
    }

    public void setAuditAggregateDuration(Duration auditAggregateDuration) {
        this.auditAggregateDuration = auditAggregateDuration;
    }

    public boolean isAuditAggregate() {
        return auditPollingInterval != null && auditAggregateDuration != null;
    }

    public String getStowSpoolDirectory() {
        return stowSpoolDirectory;
    }

    public void setStowSpoolDirectory(String stowSpoolDirectory) {
        this.stowSpoolDirectory = Objects.requireNonNull(stowSpoolDirectory, "StowSpoolDirectory");
    }

    public String getWadoSpoolDirectory() {
        return wadoSpoolDirectory;
    }

    public void setWadoSpoolDirectory(String wadoSpoolDirectory) {
        this.wadoSpoolDirectory = Objects.requireNonNull(wadoSpoolDirectory, "WadoSpoolDirectory");
    }

    public String getHL7LogFilePattern() {
        return hl7LogFilePattern;
    }

    public void setHL7LogFilePattern(String hl7LogFilePattern) {
        this.hl7LogFilePattern = hl7LogFilePattern;
    }

    public String getHL7ErrorLogFilePattern() {
        return hl7ErrorLogFilePattern;
    }

    public void setHL7ErrorLogFilePattern(String hl7ErrorLogFilePattern) {
        this.hl7ErrorLogFilePattern = hl7ErrorLogFilePattern;
    }

    public int getRejectExpiredStudiesFetchSize() {
        return rejectExpiredStudiesFetchSize;
    }

    public void setRejectExpiredStudiesFetchSize(int rejectExpiredStudiesFetchSize) {
        this.rejectExpiredStudiesFetchSize =
                greaterOrEqualsZero(rejectExpiredStudiesFetchSize, "rejectExpiredStudiesFetchSize");
    }

    public int getRejectExpiredSeriesFetchSize() {
        return rejectExpiredSeriesFetchSize;
    }

    public void setRejectExpiredSeriesFetchSize(int rejectExpiredSeriesFetchSize) {
        this.rejectExpiredSeriesFetchSize =
                greaterOrEqualsZero(rejectExpiredSeriesFetchSize, "rejectExpiredSeriesFetchSize");
    }

    public Duration getRejectExpiredStudiesPollingInterval() {
        return rejectExpiredStudiesPollingInterval;
    }

    public void setRejectExpiredStudiesPollingInterval(Duration rejectExpiredStudiesPollingInterval) {
        this.rejectExpiredStudiesPollingInterval = rejectExpiredStudiesPollingInterval;
    }

    public ScheduleExpression[] getRejectExpiredStudiesSchedules() {
        return rejectExpiredStudiesSchedules;
    }

    public void setRejectExpiredStudiesSchedules(ScheduleExpression[] rejectExpiredStudiesSchedules) {
        this.rejectExpiredStudiesSchedules = rejectExpiredStudiesSchedules;
    }

    public String getRejectExpiredStudiesAETitle() {
        return rejectExpiredStudiesAETitle;
    }

    public void setRejectExpiredStudiesAETitle(String rejectExpiredStudiesAETitle) {
        this.rejectExpiredStudiesAETitle = rejectExpiredStudiesAETitle;
    }

    public String getFallbackCMoveSCPStudyOlderThan() {
        return fallbackCMoveSCPStudyOlderThan;
    }

    public void setFallbackCMoveSCPStudyOlderThan(String fallbackCMoveSCPStudyOlderThan) {
        this.fallbackCMoveSCPStudyOlderThan = fallbackCMoveSCPStudyOlderThan;
    }

    public Duration getPurgeTaskPollingInterval() {
        return purgeTaskPollingInterval;
    }

    public void setPurgeTaskPollingInterval(Duration purgeTaskPollingInterval) {
        this.purgeTaskPollingInterval = purgeTaskPollingInterval;
    }

    public Duration getPurgeStgCmtPollingInterval() {
        return purgeStgCmtPollingInterval;
    }

    public void setPurgeStgCmtPollingInterval(Duration purgeStgCmtPollingInterval) {
        this.purgeStgCmtPollingInterval = purgeStgCmtPollingInterval;
    }

    public Duration getPurgeStgCmtCompletedDelay() {
        return purgeStgCmtCompletedDelay;
    }

    public void setPurgeStgCmtCompletedDelay(Duration purgeStgCmtCompletedDelay) {
        this.purgeStgCmtCompletedDelay = purgeStgCmtCompletedDelay;
    }

    public Duration getKeyValueRetentionPollingInterval() {
        return keyValueRetentionPollingInterval;
    }

    public void setKeyValueRetentionPollingInterval(Duration keyValueRetentionPollingInterval) {
        this.keyValueRetentionPollingInterval = keyValueRetentionPollingInterval;
    }

    public int getKeyValueRetentionFetchSize() {
        return keyValueRetentionFetchSize;
    }

    public void setKeyValueRetentionFetchSize(int keyValueRetentionFetchSize) {
        this.keyValueRetentionFetchSize = keyValueRetentionFetchSize;
    }

    public Duration getKeyValueRetentionPeriod() {
        return keyValueRetentionPeriod;
    }

    public void setKeyValueRetentionPeriod(Duration keyValueRetentionPeriod) {
        this.keyValueRetentionPeriod = keyValueRetentionPeriod;
    }

    public SPSStatus[] getHideSPSWithStatusFrom() {
        return hideSPSWithStatusFrom;
    }

    public void setHideSPSWithStatusFrom(SPSStatus[] hideSPSWithStatusFrom) {
        this.hideSPSWithStatusFrom = hideSPSWithStatusFrom;
    }

    public SPSStatus[] getHideSPSWithStatusFromMWLRS() {
        return hideSPSWithStatusFromMWLRS;
    }

    public void setHideSPSWithStatusFromMWLRS(SPSStatus[] hideSPSWithStatusFromMWLRS) {
        this.hideSPSWithStatusFromMWLRS = hideSPSWithStatusFromMWLRS;
    }

    public HL7ORUAction[] getHl7ORUAction() {
        return hl7ORUAction;
    }

    public void setHl7ORUAction(HL7ORUAction[] hl7ORUAction) {
        this.hl7ORUAction = hl7ORUAction;
    }

    public String getMWLAccessionNumberGenerator() {
        return mwlAccessionNumberGenerator;
    }

    public void setMWLAccessionNumberGenerator(String mwlAccessionNumberGenerator) {
        this.mwlAccessionNumberGenerator = mwlAccessionNumberGenerator;
    }

    public String getMWLRequestedProcedureIDGenerator() {
        return mwlRequestedProcedureIDGenerator;
    }

    public void setMWLRequestedProcedureIDGenerator(String mwlRequestedProcedureIDGenerator) {
        this.mwlRequestedProcedureIDGenerator = mwlRequestedProcedureIDGenerator;
    }

    public String getMWLScheduledProcedureStepIDGenerator() {
        return mwlScheduledProcedureStepIDGenerator;
    }

    public void setMWLScheduledProcedureStepIDGenerator(String mwlScheduledProcedureStepIDGenerator) {
        this.mwlScheduledProcedureStepIDGenerator = mwlScheduledProcedureStepIDGenerator;
    }

    public String getStorePermissionServiceURL() {
        return storePermissionServiceURL;
    }

    public void setStorePermissionServiceURL(String storePermissionServiceURL) {
        this.storePermissionServiceURL = storePermissionServiceURL;
    }

    public String getStorePermissionServiceResponse() {
        return storePermissionServiceResponse;
    }

    public void setStorePermissionServiceResponse(String storePermissionServiceResponse) {
        this.storePermissionServiceResponse = storePermissionServiceResponse;
    }

    public Pattern getStorePermissionServiceResponsePattern() {
        return storePermissionServiceResponsePattern;
    }

    public void setStorePermissionServiceResponsePattern(Pattern storePermissionServiceResponsePattern) {
        this.storePermissionServiceResponsePattern = storePermissionServiceResponsePattern;
    }

    public Pattern getStorePermissionServiceExpirationDatePattern() {
        return storePermissionServiceExpirationDatePattern;
    }

    public void setStorePermissionServiceExpirationDatePattern(Pattern storePermissionServiceExpirationDatePattern) {
        this.storePermissionServiceExpirationDatePattern = storePermissionServiceExpirationDatePattern;
    }

    public Pattern getStorePermissionServiceErrorCommentPattern() {
        return storePermissionServiceErrorCommentPattern;
    }

    public void setStorePermissionServiceErrorCommentPattern(Pattern storePermissionServiceErrorCommentPattern) {
        this.storePermissionServiceErrorCommentPattern = storePermissionServiceErrorCommentPattern;
    }

    public Pattern getStorePermissionServiceErrorCodePattern() {
        return storePermissionServiceErrorCodePattern;
    }

    public void setStorePermissionServiceErrorCodePattern(Pattern storePermissionServiceErrorCodePattern) {
        this.storePermissionServiceErrorCodePattern = storePermissionServiceErrorCodePattern;
    }

    public Duration getStorePermissionCacheStaleTimeout() {
        return storePermissionCacheStaleTimeout;
    }

    public void setStorePermissionCacheStaleTimeout(Duration storePermissionCacheStaleTimeout) {
        this.storePermissionCacheStaleTimeout = storePermissionCacheStaleTimeout;
    }

    public int getStorePermissionCacheStaleTimeoutSeconds() {
        return toSeconds(storePermissionCacheStaleTimeout);
    }

    public int getStorePermissionCacheSize() {
        return storePermissionCacheSize;
    }

    public void setStorePermissionCacheSize(int storePermissionCacheSize) {
        this.storePermissionCacheSize = greaterZero(storePermissionCacheSize, "storePermissionCacheSize");
    }

    public Duration getMergeMWLCacheStaleTimeout() {
        return mergeMWLCacheStaleTimeout;
    }

    public void setMergeMWLCacheStaleTimeout(Duration mergeMWLCacheStaleTimeout) {
        this.mergeMWLCacheStaleTimeout = mergeMWLCacheStaleTimeout;
    }

    public int getMergeMWLCacheStaleTimeoutSeconds() {
        return toSeconds(mergeMWLCacheStaleTimeout);
    }

    public int getMergeMWLCacheSize() {
        return mergeMWLCacheSize;
    }

    public void setMergeMWLCacheSize(int mergeMWLCacheSize) {
        this.mergeMWLCacheSize = greaterZero(mergeMWLCacheSize, "mergeMWLCacheSize");
    }

    public int getStoreUpdateDBMaxRetries() {
        return storeUpdateDBMaxRetries;
    }

    public void setStoreUpdateDBMaxRetries(int storeUpdateDBMaxRetries) {
        this.storeUpdateDBMaxRetries = storeUpdateDBMaxRetries;
    }

    public int getStoreUpdateDBMaxRetryDelay() {
        return storeUpdateDBMaxRetryDelay;
    }

    public void setStoreUpdateDBMaxRetryDelay(int storeUpdateDBMaxRetryDelay) {
        this.storeUpdateDBMaxRetryDelay = storeUpdateDBMaxRetryDelay;
    }

    public int getStoreUpdateDBMinRetryDelay() {
        return storeUpdateDBMinRetryDelay;
    }

    public void setStoreUpdateDBMinRetryDelay(int storeUpdateDBMinRetryDelay) {
        this.storeUpdateDBMinRetryDelay = storeUpdateDBMinRetryDelay;
    }

    public int storeUpdateDBRetryDelay() {
        return storeUpdateDBMinRetryDelay + ThreadLocalRandom.current().nextInt(Math.max(1,
                (storeUpdateDBMaxRetryDelay - storeUpdateDBMinRetryDelay)));
    }

    public AllowRejectionForDataRetentionPolicyExpired getAllowRejectionForDataRetentionPolicyExpired() {
        return allowRejectionForDataRetentionPolicyExpired;
    }

    public void setAllowRejectionForDataRetentionPolicyExpired(
            AllowRejectionForDataRetentionPolicyExpired allowRejectionForDataRetentionPolicyExpired) {
        this.allowRejectionForDataRetentionPolicyExpired = allowRejectionForDataRetentionPolicyExpired;
    }

    public String getRemapRetrieveURL() {
        return remapRetrieveURLClientHost != null
                ? ('[' + remapRetrieveURLClientHost + ']' + remapRetrieveURL)
                : remapRetrieveURL;
    }

    public void setRemapRetrieveURL(String remapRetrieveURL) {
        if (remapRetrieveURL == null || remapRetrieveURL.charAt(0) != '[') {
            this.remapRetrieveURL = remapRetrieveURL;
            this.remapRetrieveURLClientHost = null;
        } else {
            String[] ss = StringUtils.split(remapRetrieveURL.substring(1), ']');
            if (ss.length != 2)
                throw new IllegalArgumentException(remapRetrieveURL);

            this.remapRetrieveURL = ss[1];
            this.remapRetrieveURLClientHost = ss[0];
        }
    }

    public StringBuffer remapRetrieveURL(HttpServletRequest request) {
        StringBuffer sb = request.getRequestURL();
        if (remap(request)) {
            sb.setLength(0);
            sb.append(remapRetrieveURL).append(request.getRequestURI());
        }
        return sb;
    }

    private boolean remap(HttpServletRequest request) {
        return remapRetrieveURL != null
                && (remapRetrieveURLClientHost == null || remapRetrieveURLClientHost.equals(
                        StringUtils.isIPAddr(remapRetrieveURLClientHost)
                                ? request.getRemoteAddr()
                                : request.getRemoteHost()));
    }

    public String getHL7PSUSendingApplication() {
        return hl7PSUSendingApplication;
    }

    public void setHL7PSUSendingApplication(String hl7PSUSendingApplication) {
        this.hl7PSUSendingApplication = hl7PSUSendingApplication;
    }

    public Duration getHL7PSUTaskPollingInterval() {
        return hl7PSUTaskPollingInterval;
    }

    public void setHL7PSUTaskPollingInterval(Duration hl7PSUTaskPollingInterval) {
        this.hl7PSUTaskPollingInterval = hl7PSUTaskPollingInterval;
    }

    public String[] getHL7PSUReceivingApplications() {
        return hl7PSUReceivingApplications;
    }

    public void setHL7PSUReceivingApplications(String[] hl7PSUReceivingApplications) {
        this.hl7PSUReceivingApplications = hl7PSUReceivingApplications;
    }

    public Duration getHL7PSUDelay() {
        return hl7PSUDelay;
    }

    public void setHL7PSUDelay(Duration hl7PSUDelay) {
        this.hl7PSUDelay = hl7PSUDelay;
    }

    public Duration getHL7PSUTimeout() {
        return hl7PSUTimeout;
    }

    public void setHL7PSUTimeout(Duration hl7PSUTimeout) {
        this.hl7PSUTimeout = hl7PSUTimeout;
    }

    public boolean isHL7PSUOnTimeout() {
        return hl7PSUOnTimeout;
    }

    public void setHL7PSUOnTimeout(boolean hl7PSUOnTimeout) {
        this.hl7PSUOnTimeout = hl7PSUOnTimeout;
    }

    public int getHL7PSUTaskFetchSize() {
        return hl7PSUTaskFetchSize;
    }

    public void setHL7PSUTaskFetchSize(int hl7PSUTaskFetchSize) {
        this.hl7PSUTaskFetchSize = hl7PSUTaskFetchSize;
    }

    public boolean isHL7PSUMWL() {
        return hl7PSUMWL;
    }

    public void setHL7PSUMWL(boolean hl7PSUMWL) {
        this.hl7PSUMWL = hl7PSUMWL;
    }

    public HL7PSUMWLMatchingKey getHL7PSUMWLMatchingKey() {
        return hl7PSUMWLMatchingKey;
    }

    public void setHL7PSUMWLMatchingKey(HL7PSUMWLMatchingKey hl7PSUMWLMatchingKey) {
        this.hl7PSUMWLMatchingKey = hl7PSUMWLMatchingKey;
    }

    public boolean isHl7PSUForRequestedProcedure() {
        return hl7PSUForRequestedProcedure;
    }

    public void setHl7PSUForRequestedProcedure(boolean hl7PSUForRequestedProcedure) {
        this.hl7PSUForRequestedProcedure = hl7PSUForRequestedProcedure;
    }

    public boolean isHl7PSUPIDPV1() {
        return hl7PSUPIDPV1;
    }

    public void setHl7PSUPIDPV1(boolean hl7PSUPIDPV1) {
        this.hl7PSUPIDPV1 = hl7PSUPIDPV1;
    }

    public String getHl7PSURequestedProcedureID() {
        return hl7PSURequestedProcedureID;
    }

    public void setHl7PSURequestedProcedureID(String hl7PSURequestedProcedureID) {
        this.hl7PSURequestedProcedureID = hl7PSURequestedProcedureID;
    }

    public String getHl7PSUAccessionNumber() {
        return hl7PSUAccessionNumber;
    }

    public void setHl7PSUAccessionNumber(String hl7PSUAccessionNumber) {
        this.hl7PSUAccessionNumber = hl7PSUAccessionNumber;
    }

    public String getHl7PSUFillerOrderNumber() {
        return hl7PSUFillerOrderNumber;
    }

    public void setHl7PSUFillerOrderNumber(String hl7PSUFillerOrderNumber) {
        this.hl7PSUFillerOrderNumber = hl7PSUFillerOrderNumber;
    }

    public String getHl7PSUPlacerOrderNumber() {
        return hl7PSUPlacerOrderNumber;
    }

    public void setHl7PSUPlacerOrderNumber(String hl7PSUPlacerOrderNumber) {
        this.hl7PSUPlacerOrderNumber = hl7PSUPlacerOrderNumber;
    }

    public HL7PSUMessageType getHl7PSUMessageType() {
        return hl7PSUMessageType;
    }

    public void setHl7PSUMessageType(HL7PSUMessageType hl7PSUMessageType) {
        this.hl7PSUMessageType = hl7PSUMessageType;
    }

    public Conditions getHl7PSUConditions() {
        return hl7PSUConditions;
    }

    public void setHl7PSUConditions(Conditions hl7PSUConditions) {
        this.hl7PSUConditions = hl7PSUConditions;
    }

    public String getHl7PSUMppsTemplateURI() {
        return hl7PSUMppsTemplateURI;
    }

    public void setHl7PSUMppsTemplateURI(String hl7PSUMppsTemplateURI) {
        this.hl7PSUMppsTemplateURI = hl7PSUMppsTemplateURI;
    }

    public String getHl7PSUStudyTemplateURI() {
        return hl7PSUStudyTemplateURI;
    }

    public void setHl7PSUStudyTemplateURI(String hl7PSUStudyTemplateURI) {
        this.hl7PSUStudyTemplateURI = hl7PSUStudyTemplateURI;
    }

    public String[] getHL7NoPatientCreateMessageTypes() {
        return hl7NoPatientCreateMessageTypes.toArray(
                new String[hl7NoPatientCreateMessageTypes.size()]);
    }

    public void setHL7NoPatientCreateMessageTypes(String... messageTypes) {
        hl7NoPatientCreateMessageTypes.clear();
        for (String messageType : messageTypes)
            hl7NoPatientCreateMessageTypes.add(messageType);
    }

    public boolean isHL7NoPatientCreateMessageType(String messageType) {
        return hl7NoPatientCreateMessageTypes.contains(messageType);
    }

    public Map<String, String> getXRoadProperties() {
        return xRoadProperties;
    }

    public void setXRoadProperty(String name, String value) {
        xRoadProperties.put(name, value);
    }

    public void setXRoadProperties(String[] ss) {
        xRoadProperties.clear();
        for (String s : ss) {
            int index = s.indexOf('=');
            if (index < 0)
                throw new IllegalArgumentException("Property in incorrect format : " + s);
            setXRoadProperty(s.substring(0, index), s.substring(index+1));
        }
    }

    public boolean hasXRoadProperties() {
        return xRoadProperties.containsKey("endpoint");
    }

    public Map<String, String> getImpaxReportProperties() {
        return impaxReportProperties;
    }

    public void setImpaxReportProperty(String name, String value) {
        impaxReportProperties.put(name, value);
    }

    public void setImpaxReportProperties(String[] ss) {
        impaxReportProperties.clear();
        for (String s : ss) {
            int index = s.indexOf('=');
            if (index < 0)
                throw new IllegalArgumentException("Property in incorrect format : " + s);
            setImpaxReportProperty(s.substring(0, index), s.substring(index+1));
        }
    }

    public boolean hasImpaxReportProperties() {
        return impaxReportProperties.containsKey("endpoint");
    }

    public AttributeFilter getAttributeFilter(Entity entity) {
        AttributeFilter filter = attributeFilters.get(entity);
        if (filter == null)
            throw new IllegalArgumentException("No Attribute Filter for " + entity + " configured");

        return filter;
    }

    public void setAttributeFilter(Entity entity, AttributeFilter filter) {
        attributeFilters.put(entity, filter);
    }

    public Map<Entity, AttributeFilter> getAttributeFilters() {
        return attributeFilters;
    }

    public int[] returnKeysForLeadingCFindSCP(String aet) {
        Map<String, AttributeSet> map = getAttributeSet(AttributeSet.Type.LEADING_CFIND_SCP);
        AttributeSet attributeSet = map.get(aet);
        if (attributeSet == null)
            attributeSet = map.get("*");

        return attributeSet != null
                ? attributeSet.getSelection()
                : catAttributeFilters(Entity.Patient, Entity.Study);
    }

    private int[] catAttributeFilters(Entity... entities) {
        int[] tags = ByteUtils.EMPTY_INTS;
        for (Entity entity : entities) {
            int[] src = getAttributeFilter(entity).getSelection();
            int[] dest = Arrays.copyOf(tags, tags.length + src.length);
            System.arraycopy(src, 0, dest, tags.length, src.length);
            tags = dest;
        }
        return tags;
    }

    public void addAttributeSet(AttributeSet tags) {
        Map<String, AttributeSet> map = attributeSet.get(tags.getType());
        if (map == null)
            attributeSet.put(tags.getType(), map = new LinkedHashMap<>());
        map.put(tags.getID(), tags);
    }

    public void removeAttributeSet(AttributeSet tags) {
        Map<String, AttributeSet> map = attributeSet.get(tags.getType());
        if (map != null)
            map.remove(tags.getID());
    }

    public Map<AttributeSet.Type, Map<String, AttributeSet>> getAttributeSet() {
        return attributeSet;
    }

    public Map<String, AttributeSet> getAttributeSet(AttributeSet.Type type) {
        return StringUtils.maskNull(attributeSet.get(type), Collections.emptyMap());
    }

    public BulkDataDescriptor getBulkDataDescriptor(String bulkDataDescriptorID) {
        if (bulkDataDescriptorID == null)
            return BulkDataDescriptor.DEFAULT;

        BasicBulkDataDescriptor descriptor = bulkDataDescriptorMap.get(bulkDataDescriptorID);
        if (descriptor == null)
            throw new IllegalArgumentException("No Bulk Data Descriptor with ID " + bulkDataDescriptorID + " configured");

        return descriptor;
    }

    public BulkDataDescriptor getBulkDataDescriptor() {
        return getBulkDataDescriptor(bulkDataDescriptorID);
    }

    public void addBulkDataDescriptor(BasicBulkDataDescriptor descriptor) {
        bulkDataDescriptorMap.put(descriptor.getBulkDataDescriptorID(), descriptor);
    }

    public BasicBulkDataDescriptor removeBulkDataDescriptor(String bulkDataDescriptorID) {
        return bulkDataDescriptorMap.remove(bulkDataDescriptorID);
    }

    public void clearBulkDataDescriptors() {
        bulkDataDescriptorMap.clear();
    }

    public Map<String, BasicBulkDataDescriptor> getBulkDataDescriptors() {
        return bulkDataDescriptorMap;
    }

    public IDGenerator getIDGenerator(String name) {
        IDGenerator generator = idGenerators.get(name);
        if (generator == null)
            throw new IllegalArgumentException("No ID Generator for " + name + " configured");

        return generator;
    }

    public void addIDGenerator(IDGenerator generator) {
        idGenerators.put(generator.getName(), generator);
    }

    public void removeIDGenerator(IDGenerator generator) {
        idGenerators.remove(generator.getName());
    }

    public Map<String, IDGenerator> getIDGenerators() {
        return idGenerators;
    }

    public QueryRetrieveView getQueryRetrieveView(String viewID) {
        return queryRetrieveViewMap.get(viewID);
    }

    public QueryRetrieveView getQueryRetrieveViewNotNull(String viewID) {
        QueryRetrieveView view = getQueryRetrieveView(viewID);
        if (view == null)
            throw new IllegalArgumentException("No Query Retrieve View configured with ID:" + viewID);
        return view;
    }

    public QueryRetrieveView removeQueryRetrieveView(String viewID) {
        return queryRetrieveViewMap.remove(viewID);
    }

    public void addQueryRetrieveView(QueryRetrieveView view) {
        queryRetrieveViewMap.put(view.getViewID(), view);
    }

    public Collection<QueryRetrieveView> getQueryRetrieveViews() {
        return queryRetrieveViewMap.values();
    }

    public Collection<String> getQueryRetrieveViewIDs() {
        return queryRetrieveViewMap.keySet();
    }

    public StorageDescriptor getStorageDescriptor(String storageID) {
        return storageDescriptorMap.get(storageID);
    }

    public StorageDescriptor getStorageDescriptorNotNull(String storageID) {
        StorageDescriptor descriptor = getStorageDescriptor(storageID);
        if (descriptor == null)
            throw new IllegalArgumentException("No Storage configured with ID:" + storageID);
        return descriptor;
    }

    public StorageDescriptor removeStorageDescriptor(String storageID) {
        return storageDescriptorMap.remove(storageID);
    }

    public void addStorageDescriptor(StorageDescriptor descriptor) {
        storageDescriptorMap.put(descriptor.getStorageID(), descriptor);
    }

    public Collection<StorageDescriptor> getStorageDescriptors() {
        return storageDescriptorMap.values();
    }

    public List<StorageDescriptor> getFreeStorageDescriptors(String[] storageIDs) {
        return getStorageDescriptors(storageIDs, false);
    }

    public List<StorageDescriptor> getFullStorageDescriptors(String[] storageIDs) {
        return getStorageDescriptors(storageIDs, true);
    }

    private List<StorageDescriptor> getStorageDescriptors(String[] storageIDs, boolean storageThresholdExceeded) {
        List<StorageDescriptor> list = new ArrayList<>(storageIDs.length);
        for (String storageID : storageIDs) {
            StorageDescriptor descriptor = storageDescriptorMap.get(storageID);
            if (descriptor == null)
                throw new IllegalArgumentException("No Storage configured with ID:" + storageID);
            if (descriptor.isStorageThresholdExceeded() == storageThresholdExceeded) {
                list.add(descriptor);
            }
        }
        return list;
    }

    public Stream<String> getStorageIDsOfCluster(String clusterID) {
        return storageDescriptorMap.values().stream()
                .filter(desc -> clusterID.equals(desc.getStorageClusterID()))
                .map(StorageDescriptor::getStorageID);
    }

    public List<String> getOtherStorageIDs(StorageDescriptor desc) {
        return desc.getStorageClusterID() != null
                ? getStorageIDsOfCluster(desc.getStorageClusterID())
                    .filter(storageID -> !storageID.equals(desc.getStorageID()))
                    .collect(Collectors.toList())
                : Collections.emptyList();
    }

    public List<String> getStudyStorageIDs(String storageID, Boolean storageClustered, Boolean storageExported) {
        StorageDescriptor desc = getStorageDescriptor(storageID);
        return desc != null
                ? desc.getStudyStorageIDs(getOtherStorageIDs(desc), storageClustered, storageExported)
                : Collections.emptyList();
    }

    public QueueDescriptor getQueueDescriptor(String queueName) {
        return queueDescriptorMap.get(queueName);
    }

    public QueueDescriptor getQueueDescriptorNotNull(String queueName) {
        QueueDescriptor descriptor = getQueueDescriptor(queueName);
        if (descriptor == null)
            throw new IllegalArgumentException("No Queue configured with name:" + queueName);
        return descriptor;
    }

    public QueueDescriptor removeQueueDescriptor(String queueName) {
        return queueDescriptorMap.remove(queueName);
    }

    public void addQueueDescriptor(QueueDescriptor descriptor) {
        queueDescriptorMap.put(descriptor.getQueueName(), descriptor);
    }

    public Collection<QueueDescriptor> getQueueDescriptors() {
        return queueDescriptorMap.values();
    }

    public MetricsDescriptor getMetricsDescriptor(String metricsName) {
        return metricsDescriptorMap.get(metricsName);
    }

    public boolean hasMetricsDescriptor(String metricsName) {
        return metricsDescriptorMap.containsKey(metricsName);
    }

    public MetricsDescriptor removeMetricsDescriptor(String metricsName) {
        return metricsDescriptorMap.remove(metricsName);
    }

    public void addMetricsDescriptor(MetricsDescriptor descriptor) {
        metricsDescriptorMap.put(descriptor.getMetricsName(), descriptor);
    }

    public Collection<MetricsDescriptor> getMetricsDescriptors() {
        return metricsDescriptorMap.values();
    }

    public ExporterDescriptor getExporterDescriptor(String exporterID) {
        return exporterDescriptorMap.get(exporterID);
    }

    public ExporterDescriptor getExporterDescriptorNotNull(String exporterID) {
        ExporterDescriptor descriptor = getExporterDescriptor(exporterID);
        if (descriptor == null)
            throw new IllegalArgumentException("No Exporter configured with ID:" + exporterID);
        return descriptor;
    }

    public ExporterDescriptor removeExporterDescriptor(String exporterID) {
        return exporterDescriptorMap.remove(exporterID);
    }

    public void addExporterDescriptor(ExporterDescriptor destination) {
        exporterDescriptorMap.put(destination.getExporterID(), destination);
    }

    public PDQServiceDescriptor getPDQServiceDescriptor(String pdqServiceID) {
        return pdqServiceDescriptorMap.get(pdqServiceID);
    }

    public PDQServiceDescriptor getPDQServiceDescriptorNotNull(String pdqServiceID) {
        PDQServiceDescriptor descriptor = getPDQServiceDescriptor(pdqServiceID);
        if (descriptor == null)
            throw new IllegalArgumentException("No PDQService configured with ID:" + pdqServiceID);
        return descriptor;
    }

    public PDQServiceDescriptor removePDQServiceDescriptor(String pDQServiceID) {
        return pdqServiceDescriptorMap.remove(pDQServiceID);
    }

    public void addPDQServiceDescriptor(PDQServiceDescriptor destination) {
        pdqServiceDescriptorMap.put(destination.getPDQServiceID(), destination);
    }

    public Collection<PDQServiceDescriptor> getPDQServiceDescriptors() {
        return pdqServiceDescriptorMap.values();
    }

    private int greaterZero(int i, String prompt) {
        if (i <= 0)
            throw new IllegalArgumentException(prompt + ": " + i);
        return i;
    }

    private int greaterOrEqualsZero(int i, String prompt) {
        if (i < 0)
            throw new IllegalArgumentException(prompt + ": " + i);
        return i;
    }

    public Collection<ExporterDescriptor> getExporterDescriptors() {
        return exporterDescriptorMap.values();
    }

    public void removeUPSOnStore(UPSOnStore rule) {
        upsOnStoreList.remove(rule);
    }

    public void clearUPSOnStore() {
        upsOnStoreList.clear();
    }

    public void addUPSOnStore(UPSOnStore upsOnStore) {
        upsOnStoreList.add(upsOnStore);
    }

    public Collection<UPSOnStore> listUPSOnStore() {
        return upsOnStoreList;
    }

    public void removeUPSOnUPSCompleted(UPSOnUPSCompleted rule) {
        upsOnUPSCompletedList.remove(rule);
    }

    public void clearUPSOnUPSCompleted() {
        upsOnUPSCompletedList.clear();
    }

    public void addUPSOnUPSCompleted(UPSOnUPSCompleted upsOnUPSCompleted) {
        upsOnUPSCompletedList.add(upsOnUPSCompleted);
    }

    public Collection<UPSOnUPSCompleted> listUPSOnUPSCompleted() {
        return upsOnUPSCompletedList;
    }

    public void removeUPSOnHL7(UPSOnHL7 rule) {
        upsOnHL7List.remove(rule);
    }

    public void clearUPSOnHL7() {
        upsOnHL7List.clear();
    }

    public void addUPSOnHL7(UPSOnHL7 upsOnHL7) {
        upsOnHL7List.add(upsOnHL7);
    }

    public Collection<UPSOnHL7> listUPSOnHL7() {
        return upsOnHL7List;
    }

    public void removeUPSProcessingRule(UPSProcessingRule rule) {
        upsProcessingRuleList.remove(rule);
    }

    public void clearUPSProcessingRules() {
        upsProcessingRuleList.clear();
    }

    public void addUPSProcessingRule(UPSProcessingRule rule) {
        upsProcessingRuleList.add(rule);
    }

    public Collection<UPSProcessingRule> listUPSProcessingRules() {
        return upsProcessingRuleList;
    }

    public void removeExportRule(ExportRule rule) {
        exportRules.remove(rule);
    }

    public void clearExportRules() {
        exportRules.clear();
    }

    public void addExportRule(ExportRule rule) {
        exportRules.add(rule);
    }

    public Collection<ExportRule> getExportRules() {
        return exportRules;
    }

    public void removeExportPriorsRule(ExportPriorsRule rule) {
        exportPriorsRules.remove(rule);
    }

    public void clearExportPriorsRules() {
        exportPriorsRules.clear();
    }

    public void addExportPriorsRule(ExportPriorsRule rule) {
        exportPriorsRules.add(rule);
    }

    public Collection<ExportPriorsRule> getExportPriorsRules() {
        return exportPriorsRules;
    }

    public void removeMPPSForwardRule(MPPSForwardRule rule) {
        mppsForwardRule.remove(rule);
    }

    public void clearMPPSForwardRules() {
        mppsForwardRule.clear();
    }

    public void addMPPSForwardRule(MPPSForwardRule rule) {
        mppsForwardRule.add(rule);
    }

    public Collection<MPPSForwardRule> getMPPSForwardRules() {
        return mppsForwardRule;
    }

    public void removeHL7ExportRule(HL7ExportRule rule) {
        hl7ExportRules.remove(rule);
    }

    public void clearHL7ExportRules() {
        hl7ExportRules.clear();
    }

    public void addHL7ExportRule(HL7ExportRule rule) {
        hl7ExportRules.add(rule);
    }

    public Collection<HL7ExportRule> getHL7ExportRules() {
        return hl7ExportRules;
    }

    public void removeHL7PrefetchRule(HL7PrefetchRule rule) {
        hl7PrefetchRules.remove(rule);
    }

    public void clearHL7PrefetchRules() {
        hl7PrefetchRules.clear();
    }

    public void addHL7PrefetchRule(HL7PrefetchRule rule) {
        hl7PrefetchRules.add(rule);
    }

    public Collection<HL7PrefetchRule> getHL7PrefetchRules() {
        return hl7PrefetchRules;
    }

    public void removeRSForwardRule(RSForwardRule rule) {
        rsForwardRules.remove(rule);
    }

    public void clearRSForwardRules() {
        rsForwardRules.clear();
    }

    public void addRSForwardRule(RSForwardRule rule) {
        rsForwardRules.add(rule);
    }

    public Collection<RSForwardRule> getRSForwardRules() {
        return rsForwardRules;
    }

    public void removeHL7ForwardRule(HL7ForwardRule rule) {
        hl7ForwardRules.remove(rule);
    }

    public void clearHL7ForwardRules() {
        hl7ForwardRules.clear();
    }

    public void addHL7ForwardRule(HL7ForwardRule rule) {
        hl7ForwardRules.add(rule);
    }

    public Collection<HL7ForwardRule> getHL7ForwardRules() {
        return hl7ForwardRules;
    }

    public void removeHL7OrderScheduledStation(HL7OrderScheduledStation scheduledStation) {
        hl7OrderScheduledStations.remove(scheduledStation);
    }

    public void clearHL7OrderScheduledStations() {
        hl7OrderScheduledStations.clear();
    }

    public void addHL7OrderScheduledStation(HL7OrderScheduledStation scheduledStation) {
        hl7OrderScheduledStations.add(scheduledStation);
    }

    public Collection<HL7OrderScheduledStation> getHL7OrderScheduledStations() {
        return hl7OrderScheduledStations;
    }

    public void removeHL7OrderSPSStatus(HL7OrderSPSStatus rule) {
        hl7OrderSPSStatuses.remove(rule.getSPSStatus());
    }

    public void clearHL7OrderSPSStatuses() {
        hl7OrderSPSStatuses.clear();
    }

    public void addHL7OrderSPSStatus(HL7OrderSPSStatus rule) {
        hl7OrderSPSStatuses.put(rule.getSPSStatus(), rule);
    }

    public Map<SPSStatus, HL7OrderSPSStatus> getHL7OrderSPSStatuses() {
        return hl7OrderSPSStatuses;
    }

    public void removeCompressionRule(ArchiveCompressionRule rule) {
        compressionRules.remove(rule);
    }

    public void clearCompressionRules() {
        compressionRules.clear();
    }

    public void addCompressionRule(ArchiveCompressionRule rule) {
        compressionRules.add(rule);
    }

    public Collection<ArchiveCompressionRule> getCompressionRules() {
        return compressionRules;
    }

    public void removeStudyRetentionPolicy(StudyRetentionPolicy policy) {
        studyRetentionPolicies.remove(policy);
    }

    public void clearStudyRetentionPolicies() {
        studyRetentionPolicies.clear();
    }

    public void addStudyRetentionPolicy(StudyRetentionPolicy policy) {
        studyRetentionPolicies.add(policy);
    }

    public Collection<StudyRetentionPolicy> getStudyRetentionPolicies() {
        return studyRetentionPolicies;
    }

    public void removeHL7StudyRetentionPolicy(HL7StudyRetentionPolicy policy) {
        hl7StudyRetentionPolicies.remove(policy);
    }

    public void clearHL7StudyRetentionPolicies() {
        hl7StudyRetentionPolicies.clear();
    }

    public void addHL7StudyRetentionPolicy(HL7StudyRetentionPolicy policy) {
        hl7StudyRetentionPolicies.add(policy);
    }

    public Collection<HL7StudyRetentionPolicy> getHL7StudyRetentionPolicies() {
        return hl7StudyRetentionPolicies;
    }

    public void removeAttributeCoercion(ArchiveAttributeCoercion coercion) {
        attributeCoercions.remove(coercion);
    }

    public void clearAttributeCoercions() {
        attributeCoercions.clear();
    }

    public void addAttributeCoercion(ArchiveAttributeCoercion coercion) {
        attributeCoercions.add(coercion);
    }

    public Collection<ArchiveAttributeCoercion> getAttributeCoercions() {
        return attributeCoercions;
    }

    public void removeAttributeCoercion2(ArchiveAttributeCoercion2 coercion) {
        attributeCoercions2.remove(coercion);
    }

    public void clearAttributeCoercions2() {
        attributeCoercions2.clear();
    }

    public void addAttributeCoercion2(ArchiveAttributeCoercion2 coercion) {
        attributeCoercions2.add(coercion);
    }

    public Collection<ArchiveAttributeCoercion2> getAttributeCoercions2() {
        return attributeCoercions2;
    }

    public void removeStoreAccessControlIDRule(StoreAccessControlIDRule storeAccessControlIDRule) {
        storeAccessControlIDRules.remove(storeAccessControlIDRule);
    }

    public void clearStoreAccessControlIDRules() {
        storeAccessControlIDRules.clear();
    }

    public void addStoreAccessControlIDRule(StoreAccessControlIDRule storeAccessControlIDRule) {
        storeAccessControlIDRules.add(storeAccessControlIDRule);
    }

    public List<StoreAccessControlIDRule> getStoreAccessControlIDRules() {
        return storeAccessControlIDRules;
    }

    public void removeMWLIdleTimeout(MWLIdleTimeout mwlIdleTimeout) {
        mwlIdleTimeoutList.remove(mwlIdleTimeout);
    }

    public void clearMWLIdleTimeouts() {
        mwlIdleTimeoutList.clear();
    }

    public void addMWLIdleTimeout(MWLIdleTimeout mwlIdleTimeout) {
        mwlIdleTimeoutList.add(mwlIdleTimeout);
    }

    public List<MWLIdleTimeout> getMWLIdleTimeouts() {
        return mwlIdleTimeoutList;
    }

    public void removeMWLImport(MWLImport mWLImport) {
        mwlImportList.remove(mWLImport);
    }

    public void clearMWLImports() {
        mwlImportList.clear();
    }

    public void addMWLImport(MWLImport mWLImport) {
        mwlImportList.add(mWLImport);
    }

    public List<MWLImport> getMWLImports() {
        return mwlImportList;
    }

    public RejectionNote getRejectionNote(String rjNoteID) {
        return rejectionNoteMap.get(rjNoteID);
    }

    public RejectionNote getRejectionNote(Code code) {
        if (code != null)
            for (RejectionNote rjNote : rejectionNoteMap.values()) {
                if (rjNote.getRejectionNoteCode().equalsIgnoreMeaning(code))
                    return rjNote;
            }
        return null;
    }

    public RejectionNote getRejectionNote(RejectionNote.Type rejectionNoteType) {
        for (RejectionNote rejectionNote : rejectionNoteMap.values()) {
            if (rejectionNote.getRejectionNoteType() == rejectionNoteType)
                return rejectionNote;
        }
        return null;
    }

    public RejectionNote removeRejectionNote(String rjNoteID) {
        return rejectionNoteMap.remove(rjNoteID);
    }

    public void addRejectionNote(RejectionNote rjNote) {
        rejectionNoteMap.put(rjNote.getRejectionNoteLabel(), rjNote);
    }

    public Collection<RejectionNote> getRejectionNotes() {
        return rejectionNoteMap.values();
    }

    public AllowDeleteStudyPermanently getAllowDeleteStudyPermanently() {
        return allowDeleteStudyPermanently;
    }

    public void setAllowDeleteStudyPermanently(AllowDeleteStudyPermanently allowDeleteStudyPermanently) {
        this.allowDeleteStudyPermanently = allowDeleteStudyPermanently;
    }

    public AllowDeletePatient getAllowDeletePatient() {
        return allowDeletePatient;
    }

    public void setAllowDeletePatient(AllowDeletePatient allowDeletePatient) {
        this.allowDeletePatient = allowDeletePatient;
    }

    public AcceptConflictingPatientID getAcceptConflictingPatientID() {
        return acceptConflictingPatientID;
    }

    public void setAcceptConflictingPatientID(AcceptConflictingPatientID acceptConflictingPatientID) {
        this.acceptConflictingPatientID = acceptConflictingPatientID;
    }

    public UserIdentityNegotiation getUserIdentityNegotiation() {
        return userIdentityNegotiation;
    }

    public void setUserIdentityNegotiation(UserIdentityNegotiation userIdentityNegotiation) {
        this.userIdentityNegotiation = userIdentityNegotiation;
    }

    public String getUserIdentityNegotiationRole() {
        return userIdentityNegotiationRole;
    }

    public void setUserIdentityNegotiationRole(String userIdentityNegotiationRole) {
        this.userIdentityNegotiationRole = userIdentityNegotiationRole;
    }

    public String getUserIdentityNegotiationKeycloakClientID() {
        return userIdentityNegotiationKeycloakClientID;
    }

    public void setUserIdentityNegotiationKeycloakClientID(String userIdentityNegotiationKeycloakClientID) {
        this.userIdentityNegotiationKeycloakClientID = userIdentityNegotiationKeycloakClientID;
    }

    public String[] getRetrieveAETitles() {
        return retrieveAETitles;
    }

    public void setRetrieveAETitles(String... retrieveAETitles) {
        this.retrieveAETitles = retrieveAETitles;
    }

    public String[] getReturnRetrieveAETitles() {
        return returnRetrieveAETitles;
    }

    public void setReturnRetrieveAETitles(String... returnRetrieveAETitles) {
        this.returnRetrieveAETitles = returnRetrieveAETitles;
    }

    public String getProxyUpstreamURL() {
        return proxyUpstreamURL;
    }

    public void setProxyUpstreamURL(String proxyUpstreamURL) {
        this.proxyUpstreamURL = proxyUpstreamURL;
    }

    public String getAudit2JsonFhirTemplateURI() {
        return atna2JsonFhirTemplateURI;
    }

    public void setAudit2JsonFhirTemplateURI(String atna2JsonFhirTemplateURI) {
        this.atna2JsonFhirTemplateURI = atna2JsonFhirTemplateURI;
    }

    public String getAudit2XmlFhirTemplateURI() {
        return atna2XmlFhirTemplateURI;
    }

    public void setAudit2XmlFhirTemplateURI(String atna2XmlFhirTemplateURI) {
        this.atna2XmlFhirTemplateURI = atna2XmlFhirTemplateURI;
    }

    public Attributes.UpdatePolicy getCopyMoveUpdatePolicy() {
        return copyMoveUpdatePolicy;
    }

    public void setCopyMoveUpdatePolicy(Attributes.UpdatePolicy copyMoveUpdatePolicy) {
        this.copyMoveUpdatePolicy = copyMoveUpdatePolicy;
    }

    public Attributes.UpdatePolicy getLinkMWLEntryUpdatePolicy() {
        return linkMWLEntryUpdatePolicy;
    }

    public void setLinkMWLEntryUpdatePolicy(Attributes.UpdatePolicy linkMWLEntryUpdatePolicy) {
        this.linkMWLEntryUpdatePolicy = linkMWLEntryUpdatePolicy;
    }

    public boolean isHL7TrackChangedPatientID() {
        return hl7TrackChangedPatientID;
    }

    public void setHL7TrackChangedPatientID(boolean hl7TrackChangedPatientID) {
        this.hl7TrackChangedPatientID = hl7TrackChangedPatientID;
    }

    public boolean isAuditSoftwareConfigurationVerbose() {
        return auditSoftwareConfigurationVerbose;
    }

    public void setAuditSoftwareConfigurationVerbose(boolean auditSoftwareConfigurationVerbose) {
        this.auditSoftwareConfigurationVerbose = auditSoftwareConfigurationVerbose;
    }

    public String[] getHL7ADTReceivingApplication() {
        return hl7ADTReceivingApplication;
    }

    public void setHL7ADTReceivingApplication(String[] hl7ADTReceivingApplication) {
        this.hl7ADTReceivingApplication = hl7ADTReceivingApplication;
    }

    public String getHL7ADTSendingApplication() {
        return hl7ADTSendingApplication;
    }

    public void setHL7ADTSendingApplication(String hl7ADTSendingApplication) {
        this.hl7ADTSendingApplication = hl7ADTSendingApplication;
    }

    public ScheduledProtocolCodeInOrder getHL7ScheduledProtocolCodeInOrder() {
        return hl7ScheduledProtocolCodeInOrder;
    }

    public void setHL7ScheduledProtocolCodeInOrder(ScheduledProtocolCodeInOrder hl7ScheduledProtocolCodeInOrder) {
        this.hl7ScheduledProtocolCodeInOrder = hl7ScheduledProtocolCodeInOrder;
    }

    public ScheduledStationAETInOrder getHL7ScheduledStationAETInOrder() {
        return hl7ScheduledStationAETInOrder;
    }

    public void setHL7ScheduledStationAETInOrder(ScheduledStationAETInOrder hl7ScheduledStationAETInOrder) {
        this.hl7ScheduledStationAETInOrder = hl7ScheduledStationAETInOrder;
    }

    public String getAuditUnknownStudyInstanceUID() {
        return auditUnknownStudyInstanceUID;
    }

    public void setAuditUnknownStudyInstanceUID(String auditUnknownStudyInstanceUID) {
        this.auditUnknownStudyInstanceUID = auditUnknownStudyInstanceUID;
    }

    public String auditUnknownStudyInstanceUID() {
        return StringUtils.maskNull(auditUnknownStudyInstanceUID, "1.2.40.0.13.1.15.110.3.165.1");
    }

    public String getAuditUnknownPatientID() {
        return auditUnknownPatientID;
    }

    public void setAuditUnknownPatientID(String auditUnknownPatientID) {
        this.auditUnknownPatientID = auditUnknownPatientID;
    }

    public String auditUnknownPatientID() {
        return StringUtils.maskNull(auditUnknownPatientID, "<none>");
    }

    public boolean isHL7UseNullValue() {
        return hl7UseNullValue;
    }

    public void setHL7UseNullValue(boolean hl7UseNullValue) {
        this.hl7UseNullValue = hl7UseNullValue;
    }

    public int getQueueTasksFetchSize() {
        return queueTasksFetchSize;
    }

    public void setQueueTasksFetchSize(int queueTasksFetchSize) {
        this.queueTasksFetchSize = queueTasksFetchSize;
    }

    public int getAuditHL7MsgLimit() {
        return auditHL7MsgLimit;
    }

    public void setAuditHL7MsgLimit(int auditHL7MsgLimit) {
        this.auditHL7MsgLimit = auditHL7MsgLimit;
    }

    public String getRejectionNoteStorageAET() {
        return rejectionNoteStorageAET;
    }

    public void setRejectionNoteStorageAET(String rejectionNoteStorageAET) {
        this.rejectionNoteStorageAET = rejectionNoteStorageAET;
    }

    public String getUiConfigurationDeviceName() {
        return uiConfigurationDeviceName;
    }

    public void setUiConfigurationDeviceName(String uiConfigurationDeviceName) {
        this.uiConfigurationDeviceName = uiConfigurationDeviceName;
    }

    public StorageVerificationPolicy getStorageVerificationPolicy() {
        return storageVerificationPolicy;
    }

    public void setStorageVerificationPolicy(StorageVerificationPolicy storageVerificationPolicy) {
        this.storageVerificationPolicy = storageVerificationPolicy;
    }

    public boolean isStorageVerificationUpdateLocationStatus() {
        return storageVerificationUpdateLocationStatus;
    }

    public void setStorageVerificationUpdateLocationStatus(boolean storageVerificationUpdateLocationStatus) {
        this.storageVerificationUpdateLocationStatus = storageVerificationUpdateLocationStatus;
    }

    public String[] getStorageVerificationStorageIDs() {
        return storageVerificationStorageIDs;
    }

    public void setStorageVerificationStorageIDs(String... storageVerificationStorageIDs) {
        this.storageVerificationStorageIDs = storageVerificationStorageIDs;
    }

    public String getStorageVerificationAETitle() {
        return storageVerificationAETitle;
    }

    public void setStorageVerificationAETitle(String storageVerificationAETitle) {
        this.storageVerificationAETitle = storageVerificationAETitle;
    }

    public String getStorageVerificationBatchID() {
        return storageVerificationBatchID;
    }

    public void setStorageVerificationBatchID(String storageVerificationBatchID) {
        this.storageVerificationBatchID = storageVerificationBatchID;
    }

    public Period getStorageVerificationInitialDelay() {
        return storageVerificationInitialDelay;
    }

    public void setStorageVerificationInitialDelay(Period storageVerificationInitialDelay) {
        this.storageVerificationInitialDelay = storageVerificationInitialDelay;
    }

    public Period getStorageVerificationPeriod() {
        return storageVerificationPeriod;
    }

    public void setStorageVerificationPeriod(Period storageVerificationPeriod) {
        this.storageVerificationPeriod = storageVerificationPeriod;
    }

    public ScheduleExpression[] getStorageVerificationSchedules() {
        return storageVerificationSchedules;
    }

    public void setStorageVerificationSchedules(ScheduleExpression[] storageVerificationSchedules) {
        this.storageVerificationSchedules = storageVerificationSchedules;
    }

    public int getStorageVerificationMaxScheduled() {
        return storageVerificationMaxScheduled;
    }

    public void setStorageVerificationMaxScheduled(int storageVerificationMaxScheduled) {
        this.storageVerificationMaxScheduled = storageVerificationMaxScheduled;
    }

    public Duration getStorageVerificationPollingInterval() {
        return storageVerificationPollingInterval;
    }

    public void setStorageVerificationPollingInterval(Duration storageVerificationPollingInterval) {
        this.storageVerificationPollingInterval = storageVerificationPollingInterval;
    }

    public int getStorageVerificationFetchSize() {
        return storageVerificationFetchSize;
    }

    public void setStorageVerificationFetchSize(int storageVerificationFetchSize) {
        this.storageVerificationFetchSize = storageVerificationFetchSize;
    }

    public boolean isUpdateLocationStatusOnRetrieve() {
        return updateLocationStatusOnRetrieve;
    }

    public void setUpdateLocationStatusOnRetrieve(boolean updateLocationStatusOnRetrieve) {
        this.updateLocationStatusOnRetrieve = updateLocationStatusOnRetrieve;
    }

    public boolean isStorageVerificationOnRetrieve() {
        return storageVerificationOnRetrieve;
    }

    public void setStorageVerificationOnRetrieve(boolean storageVerificationOnRetrieve) {
        this.storageVerificationOnRetrieve = storageVerificationOnRetrieve;
    }

    public String getCompressionAETitle() {
        return compressionAETitle;
    }

    public void setCompressionAETitle(String compressionAETitle) {
        this.compressionAETitle = compressionAETitle;
    }

    public Duration getCompressionPollingInterval() {
        return compressionPollingInterval;
    }

    public void setCompressionPollingInterval(Duration compressionPollingInterval) {
        this.compressionPollingInterval = compressionPollingInterval;
    }

    public int getCompressionFetchSize() {
        return compressionFetchSize;
    }

    public void setCompressionFetchSize(int compressionFetchSize) {
        this.compressionFetchSize = greaterZero(compressionFetchSize, "CompressionFetchSize");
    }

    public int getCompressionThreads() {
        return compressionThreads;
    }

    public void setCompressionThreads(int compressionThreads) {
        this.compressionThreads = greaterZero(compressionThreads, "CompressionThreads");
    }

    public ScheduleExpression[] getCompressionSchedules() {
        return compressionSchedules;
    }

    public void setCompressionSchedules(ScheduleExpression[] compressionSchedules) {
        this.compressionSchedules = compressionSchedules;
    }

    public Duration getDiffTaskProgressUpdateInterval() {
        return diffTaskProgressUpdateInterval;
    }

    public void setDiffTaskProgressUpdateInterval(Duration diffTaskProgressUpdateInterval) {
        this.diffTaskProgressUpdateInterval = diffTaskProgressUpdateInterval;
    }

    public void setPatientVerificationPDQServiceID(String patientVerificationPDQServiceID) {
        this.patientVerificationPDQServiceID = patientVerificationPDQServiceID;
    }

    public Duration getPatientVerificationPollingInterval() {
        return patientVerificationPollingInterval;
    }

    public void setPatientVerificationPollingInterval(Duration patientVerificationPollingInterval) {
        this.patientVerificationPollingInterval = patientVerificationPollingInterval;
    }

    public int getPatientVerificationFetchSize() {
        return patientVerificationFetchSize;
    }

    public void setPatientVerificationFetchSize(int patientVerificationFetchSize) {
        this.patientVerificationFetchSize = patientVerificationFetchSize;
    }

    public String getPatientVerificationPDQServiceID() {
        return patientVerificationPDQServiceID;
    }

    public Period getPatientVerificationPeriod() {
        return patientVerificationPeriod;
    }

    public void setPatientVerificationPeriod(Period patientVerificationPeriod) {
        this.patientVerificationPeriod = patientVerificationPeriod;
    }

    public Period getPatientVerificationPeriodOnNotFound() {
        return patientVerificationPeriodOnNotFound;
    }

    public void setPatientVerificationPeriodOnNotFound(Period patientVerificationPeriodOnNotFound) {
        this.patientVerificationPeriodOnNotFound = patientVerificationPeriodOnNotFound;
    }

    public Duration getPatientVerificationRetryInterval() {
        return patientVerificationRetryInterval;
    }

    public void setPatientVerificationRetryInterval(Duration patientVerificationRetryInterval) {
        this.patientVerificationRetryInterval = patientVerificationRetryInterval;
    }

    public int getPatientVerificationMaxRetries() {
        return patientVerificationMaxRetries;
    }

    public void setPatientVerificationMaxRetries(int patientVerificationMaxRetries) {
        this.patientVerificationMaxRetries = patientVerificationMaxRetries;
    }

    public boolean isPatientVerificationAdjustIssuerOfPatientID() {
        return patientVerificationAdjustIssuerOfPatientID;
    }

    public void setPatientVerificationAdjustIssuerOfPatientID(boolean patientVerificationAdjustIssuerOfPatientID) {
        this.patientVerificationAdjustIssuerOfPatientID = patientVerificationAdjustIssuerOfPatientID;
    }

    public Duration getPatientVerificationMaxStaleness() {
        return patientVerificationMaxStaleness;
    }

    public void setPatientVerificationMaxStaleness(Duration patientVerificationMaxStaleness) {
        this.patientVerificationMaxStaleness = patientVerificationMaxStaleness;
    }

    public MultipleStoreAssociations[] getMultipleStoreAssociations() {
        return multipleStoreAssociations;
    }

    public void setMultipleStoreAssociations(String[] ss) {
        multipleStoreAssociations = MultipleStoreAssociations.of(ss);
    }

    public Map<String, String> getImportReportTemplateParams() {
        return importReportTemplateParams;
    }

    public void setImportReportTemplateParam(String name, String value) {
        importReportTemplateParams.put(name, value);
    }

    public void setImportReportTemplateParams(String[] ss) {
        importReportTemplateParams.clear();
        for (String s : ss) {
            int index = s.indexOf('=');
            if (index < 0)
                throw new IllegalArgumentException("XSLT parameter in incorrect format : " + s);
            setImportReportTemplateParam(s.substring(0, index), s.substring(index+1));
        }
    }

    public Map<String, String> getHL7PSUTemplateParams() {
        return hl7PSUTemplateParams;
    }

    public void setHL7PSUTemplateParam(String name, String value) {
        hl7PSUTemplateParams.put(name, value);
    }

    public void setHL7PSUTemplateParams(String[] ss) {
        hl7PSUTemplateParams.clear();
        for (String s : ss) {
            int index = s.indexOf('=');
            if (index < 0)
                throw new IllegalArgumentException("XSLT parameter in incorrect format : " + s);
            setHL7PSUTemplateParam(s.substring(0, index), s.substring(index+1));
        }
    }

    public Map<String, String> getCStoreSCUOfCMoveSCPs() {
        return cStoreSCUOfCMoveSCP;
    }

    public void setCStoreSCUOfCMoveSCP(String storeSCU, String moveSCP) {
        cStoreSCUOfCMoveSCP.put(storeSCU, moveSCP);
    }

    public void setCStoreSCUOfCMoveSCPs(String[] ss) {
        cStoreSCUOfCMoveSCP.clear();
        for (String s : ss) {
            int index = s.indexOf('=');
            if (index < 0)
                throw new IllegalArgumentException("CStoreSCUofCMoveSCP in incorrect format : " + s);
            setCStoreSCUOfCMoveSCP(s.substring(0, index), s.substring(index+1));
        }
    }

    public Map<String, String> getDicomCharsetNameMappings() {
        return dicomCharsetNameMappings;
    }

    public void setDicomCharsetNameMappings(String code, String charsetName) {
        dicomCharsetNameMappings.put(
                SpecificCharacterSet.checkSpecificCharacterSet(code),
                SpecificCharacterSet.checkCharsetName(charsetName));
    }

    public void setDicomCharsetNameMappings(String[] ss) {
        HashMap<String, String> prev = new HashMap<>(dicomCharsetNameMappings);
        dicomCharsetNameMappings.clear();
        try {
            for (String s : ss) {
                int index = s.indexOf('=');
                if (index < 0)
                    throw new IllegalArgumentException("DicomCharsetNameMapping in incorrect format : " + s);
                setDicomCharsetNameMappings(s.substring(0, index), s.substring(index+1));
            }
        } catch (IllegalArgumentException e) {
            dicomCharsetNameMappings.clear();
            dicomCharsetNameMappings.putAll(prev);
            throw e;
        }
    }

    public Map<String, String> getHL7CharsetNameMappings() {
        return hl7CharsetNameMappings;
    }

    public void setHL7CharsetNameMappings(String code, String charsetName) {
        hl7CharsetNameMappings.put(code, SpecificCharacterSet.checkCharsetName(charsetName));
    }

    public void setHL7CharsetNameMappings(String[] ss) {
        HashMap<String, String> prev = new HashMap<>(hl7CharsetNameMappings);
        hl7CharsetNameMappings.clear();
        try {
            for (String s : ss) {
                int index = s.indexOf('=');
                if (index < 0)
                    throw new IllegalArgumentException("HL7CharsetNameMapping in incorrect format : " + s);
                setHL7CharsetNameMappings(s.substring(0, index), s.substring(index+1));
            }
        } catch (IllegalArgumentException e) {
            hl7CharsetNameMappings.clear();
            hl7CharsetNameMappings.putAll(prev);
            throw e;
        }
    }

    public Map<String, Integer> getUPSEventWebSocketQueueSizes() {
        return upsEventWebSocketQueueSizes;
    }

    public void setUPSEventWebSocketQueueSize(String subscriberAET, int size) {
        if (size > 0)
            upsEventWebSocketQueueSizes.put(subscriberAET, size);
        else
            upsEventWebSocketQueueSizes.remove(subscriberAET);
    }

    public int getUPSEventWebSocketQueueSize(String subscriberAET) {
        return upsEventWebSocketQueueSizes.getOrDefault(subscriberAET, Integer.valueOf(0));
    }

    public void setUPSEventWebSocketQueueSizes(String[] ss) {
        HashMap<String, Integer> prev = new HashMap<>(upsEventWebSocketQueueSizes);
        upsEventWebSocketQueueSizes.clear();
        try {
            for (String s : ss) {
                int index = s.indexOf('=');
                try {
                    setUPSEventWebSocketQueueSize(s.substring(0, index), Integer.valueOf(s.substring(index+1)));
                } catch (IndexOutOfBoundsException | NumberFormatException e) {
                    throw new IllegalArgumentException("UPSEventWebSocketQueueSize in incorrect format: " + s);
                }
            }
        } catch (IllegalArgumentException e) {
            upsEventWebSocketQueueSizes.clear();
            upsEventWebSocketQueueSizes.putAll(prev);
            throw e;
        }
    }

    public HL7OrderMissingStudyIUIDPolicy getHl7OrderMissingStudyIUIDPolicy() {
        return hl7OrderMissingStudyIUIDPolicy;
    }

    public void setHl7OrderMissingStudyIUIDPolicy(HL7OrderMissingStudyIUIDPolicy hl7OrderMissingStudyIUIDPolicy) {
        this.hl7OrderMissingStudyIUIDPolicy = hl7OrderMissingStudyIUIDPolicy;
    }

    public HL7OrderMissingAdmissionIDPolicy getHl7OrderMissingAdmissionIDPolicy() {
        return hl7OrderMissingAdmissionIDPolicy;
    }

    public void setHl7OrderMissingAdmissionIDPolicy(HL7OrderMissingAdmissionIDPolicy hl7OrderMissingAdmissionIDPolicy) {
        this.hl7OrderMissingAdmissionIDPolicy = hl7OrderMissingAdmissionIDPolicy;
    }

    public HL7ImportReportMissingStudyIUIDPolicy getHl7ImportReportMissingStudyIUIDPolicy() {
        return hl7ImportReportMissingStudyIUIDPolicy;
    }

    public void setHl7ImportReportMissingStudyIUIDPolicy(
            HL7ImportReportMissingStudyIUIDPolicy hl7ImportReportMissingStudyIUIDPolicy) {
        this.hl7ImportReportMissingStudyIUIDPolicy = hl7ImportReportMissingStudyIUIDPolicy;
    }

    public HL7ImportReportMissingAdmissionIDPolicy getHl7ImportReportMissingAdmissionIDPolicy() {
        return hl7ImportReportMissingAdmissionIDPolicy;
    }

    public void setHl7ImportReportMissingAdmissionIDPolicy(
            HL7ImportReportMissingAdmissionIDPolicy hl7ImportReportMissingAdmissionIDPolicy) {
        this.hl7ImportReportMissingAdmissionIDPolicy = hl7ImportReportMissingAdmissionIDPolicy;
    }

    public String getHl7ImportReportMissingStudyIUIDCFindSCP() {
        return hl7ImportReportMissingStudyIUIDCFindSCP;
    }

    public void setHl7ImportReportMissingStudyIUIDCFindSCP(String hl7ImportReportMissingStudyIUIDCFindSCP) {
        this.hl7ImportReportMissingStudyIUIDCFindSCP = hl7ImportReportMissingStudyIUIDCFindSCP;
    }

    public HL7ReferredMergedPatientPolicy getHl7ReferredMergedPatientPolicy() {
        return hl7ReferredMergedPatientPolicy;
    }

    public void setHl7ReferredMergedPatientPolicy(HL7ReferredMergedPatientPolicy hl7ReferredMergedPatientPolicy) {
        this.hl7ReferredMergedPatientPolicy = hl7ReferredMergedPatientPolicy;
    }

    public String getHl7DicomCharacterSet() {
        return hl7DicomCharacterSet;
    }

    public void setHl7DicomCharacterSet(String hl7DicomCharacterSet) {
        this.hl7DicomCharacterSet = hl7DicomCharacterSet;
    }

    public boolean isHl7VeterinaryUsePatientName() {
        return hl7VeterinaryUsePatientName;
    }

    public void setHl7VeterinaryUsePatientName(boolean hl7VeterinaryUsePatientName) {
        this.hl7VeterinaryUsePatientName = hl7VeterinaryUsePatientName;
    }

    public String getHL7PatientArrivalMessageType() {
        return hl7PatientArrivalMessageType;
    }

    public void setHL7PatientArrivalMessageType(String hl7PatientArrivalMessageType) {
        this.hl7PatientArrivalMessageType = hl7PatientArrivalMessageType;
    }

    public int getCSVUploadChunkSize() {
        return csvUploadChunkSize;
    }

    public void setCSVUploadChunkSize(int csvUploadChunkSize) {
        this.csvUploadChunkSize = csvUploadChunkSize;
    }

    public boolean isValidateUID() {
        return validateUID;
    }

    public void setValidateUID(boolean validateUID) {
        this.validateUID = validateUID;
    }

    public boolean isRelationalQueryNegotiationLenient() {
        return relationalQueryNegotiationLenient;
    }

    public void setRelationalQueryNegotiationLenient(boolean relationalQueryNegotiationLenient) {
        this.relationalQueryNegotiationLenient = relationalQueryNegotiationLenient;
    }

    public boolean isRelationalRetrieveNegotiationLenient() {
        return relationalRetrieveNegotiationLenient;
    }

    public void setRelationalRetrieveNegotiationLenient(boolean relationalRetrieveNegotiationLenient) {
        this.relationalRetrieveNegotiationLenient = relationalRetrieveNegotiationLenient;
    }

    public int[] getRejectConflictingPatientAttribute() {
        return rejectConflictingPatientAttribute;
    }

    public void setRejectConflictingPatientAttribute(int[] rejectConflictingPatientAttribute) {
        Arrays.sort(this.rejectConflictingPatientAttribute = rejectConflictingPatientAttribute);
    }

    public int getSchedulerMinStartDelay() {
        return schedulerMinStartDelay;
    }

    public void setSchedulerMinStartDelay(int schedulerMinStartDelay) {
        this.schedulerMinStartDelay = schedulerMinStartDelay;
    }

    public boolean isStowRetiredTransferSyntax() {
        return stowRetiredTransferSyntax;
    }

    public void setStowRetiredTransferSyntax(boolean stowRetiredTransferSyntax) {
        this.stowRetiredTransferSyntax = stowRetiredTransferSyntax;
    }

    public boolean isStowExcludeAPPMarkers() {
        return stowExcludeAPPMarkers;
    }

    public void setStowExcludeAPPMarkers(boolean stowExcludeAPPMarkers) {
        this.stowExcludeAPPMarkers = stowExcludeAPPMarkers;
    }

    public boolean isRestrictRetrieveSilently() {
        return restrictRetrieveSilently;
    }

    public void setRestrictRetrieveSilently(boolean restrictRetrieveSilently) {
        this.restrictRetrieveSilently = restrictRetrieveSilently;
    }

    public boolean isRetrieveTaskWarningOnNoMatch() {
        return retrieveTaskWarningOnNoMatch;
    }

    public void setRetrieveTaskWarningOnNoMatch(boolean retrieveTaskWarningOnNoMatch) {
        this.retrieveTaskWarningOnNoMatch = retrieveTaskWarningOnNoMatch;
    }

    public boolean isRetrieveTaskWarningOnWarnings() {
        return retrieveTaskWarningOnWarnings;
    }

    public void setRetrieveTaskWarningOnWarnings(boolean retrieveTaskWarningOnWarnings) {
        this.retrieveTaskWarningOnWarnings = retrieveTaskWarningOnWarnings;
    }

    public boolean isStowQuicktime2MP4() {
        return stowQuicktime2MP4;
    }

    public void setStowQuicktime2MP4(boolean stowQuicktime2MP4) {
        this.stowQuicktime2MP4 = stowQuicktime2MP4;
    }

    public boolean isIdentifyPatientByAllAttributes() {
        return identifyPatientByAllAttributes;
    }

    public void setIdentifyPatientByAllAttributes(boolean identifyPatientByAllAttributes) {
        this.identifyPatientByAllAttributes = identifyPatientByAllAttributes;
    }

    public Duration getMWLPollingInterval() {
        return mwlPollingInterval;
    }

    public void setMWLPollingInterval(Duration mwlPollingInterval) {
        this.mwlPollingInterval = mwlPollingInterval;
    }

    public int getMWLFetchSize() {
        return mwlFetchSize;
    }

    public void setMWLFetchSize(int mwlFetchSize) {
        this.mwlFetchSize = mwlFetchSize;
    }

    public Duration getMWLImportInterval() {
        return mwlImportInterval;
    }

    public void setMWLImportInterval(Duration mwlImportInterval) {
        this.mwlImportInterval = mwlImportInterval;
    }

    public String[] getDeleteMWLDelay() {
        return deleteMWLDelay;
    }

    public void setDeleteMWLDelay(String[] deleteMWLDelay) {
        this.deleteMWLDelay = deleteMWLDelay;
    }

    public Duration getStudySizeDelay() {
        return studySizeDelay;
    }

    public void setStudySizeDelay(Duration studySizeDelay) {
        this.studySizeDelay = studySizeDelay;
    }

    public Duration getCalculateStudySizePollingInterval() {
        return calculateStudySizePollingInterval;
    }

    public void setCalculateStudySizePollingInterval(Duration calculateStudySizePollingInterval) {
        this.calculateStudySizePollingInterval = calculateStudySizePollingInterval;
    }

    public int getCalculateStudySizeFetchSize() {
        return calculateStudySizeFetchSize;
    }

    public void setCalculateStudySizeFetchSize(int calculateStudySizeFetchSize) {
        this.calculateStudySizeFetchSize = calculateStudySizeFetchSize;
    }

    public boolean isCalculateQueryAttributes() {
        return calculateQueryAttributes;
    }

    public void setCalculateQueryAttributes(boolean calculateQueryAttributes) {
        this.calculateQueryAttributes = calculateQueryAttributes;
    }

    public boolean isStoreImplementationVersionName() {
        return storeImplementationVersionName;
    }

    public void setStoreImplementationVersionName(boolean storeImplementationVersionName) {
        this.storeImplementationVersionName = storeImplementationVersionName;
    }

    public int getSupplementIssuerFetchSize() {
        return supplementIssuerFetchSize;
    }

    public void setSupplementIssuerFetchSize(int supplementIssuerFetchSize) {
        this.supplementIssuerFetchSize = supplementIssuerFetchSize;
    }

    public int getUpdateCharsetFetchSize() {
        return updateCharsetFetchSize;
    }

    public void setUpdateCharsetFetchSize(int updateCharsetFetchSize) {
        this.updateCharsetFetchSize = updateCharsetFetchSize;
    }

    public Issuer getAuditAssigningAuthorityOfPatientID() {
        return auditAssigningAuthorityOfPatientID;
    }

    public void setAuditAssigningAuthorityOfPatientID(Issuer auditAssigningAuthorityOfPatientID) {
        this.auditAssigningAuthorityOfPatientID = auditAssigningAuthorityOfPatientID;
    }

    public String getChangeRequesterAET() {
        return changeRequesterAET;
    }

    public void setChangeRequesterAET(String changeRequesterAET) {
        this.changeRequesterAET = changeRequesterAET;
    }

    @Override
    public void reconfigure(DeviceExtension from) {
        ArchiveDeviceExtension arcdev = (ArchiveDeviceExtension) from;
        defaultCharacterSet = arcdev.defaultCharacterSet;
        upsWorklistLabel = arcdev.upsWorklistLabel;
        upsEventSCUs = arcdev.upsEventSCUs;
        upsEventSCUKeepAlive = arcdev.upsEventSCUKeepAlive;
        upsUpdateWithoutTransactionUID = arcdev.upsUpdateWithoutTransactionUID;
        fuzzyAlgorithmClass = arcdev.fuzzyAlgorithmClass;
        fuzzyStr = arcdev.fuzzyStr;
        bulkDataDescriptorID = arcdev.bulkDataDescriptorID;
        seriesMetadataStorageIDs = arcdev.seriesMetadataStorageIDs;
        seriesMetadataDelay = arcdev.seriesMetadataDelay;
        seriesMetadataPollingInterval = arcdev.seriesMetadataPollingInterval;
        seriesMetadataFetchSize = arcdev.seriesMetadataFetchSize;
        seriesMetadataThreads = arcdev.seriesMetadataThreads;
        seriesMetadataRetryInterval = arcdev.seriesMetadataRetryInterval;
        purgeInstanceRecords = arcdev.purgeInstanceRecords;
        purgeInstanceRecordsDelay = arcdev.purgeInstanceRecordsDelay;
        purgeInstanceRecordsPollingInterval = arcdev.purgeInstanceRecordsPollingInterval;
        purgeInstanceRecordsFetchSize = arcdev.purgeInstanceRecordsFetchSize;
        deleteUPSPollingInterval = arcdev.deleteUPSPollingInterval;
        deleteUPSFetchSize = arcdev.deleteUPSFetchSize;
        deleteUPSCompletedDelay = arcdev.deleteUPSCompletedDelay;
        deleteUPSCanceledDelay = arcdev.deleteUPSCanceledDelay;
        upsProcessingPollingInterval = arcdev.upsProcessingPollingInterval;
        upsProcessingFetchSize = arcdev.upsProcessingFetchSize;
        taskPollingInterval = arcdev.taskPollingInterval;
        taskFetchSize = arcdev.taskFetchSize;
        overwritePolicy = arcdev.overwritePolicy;
        recordAttributeModification = arcdev.recordAttributeModification;
        showPatientInfoInSystemLog = arcdev.showPatientInfoInSystemLog;
        showPatientInfoInAuditLog = arcdev.showPatientInfoInAuditLog;
        bulkDataSpoolDirectory = arcdev.bulkDataSpoolDirectory;
        personNameComponentOrderInsensitiveMatching = arcdev.personNameComponentOrderInsensitiveMatching;
        validateCallingAEHostname = arcdev.validateCallingAEHostname;
        sendPendingCGet = arcdev.sendPendingCGet;
        sendPendingCMoveInterval = arcdev.sendPendingCMoveInterval;
        encodeAsJSONNumber.clear();
        encodeAsJSONNumber.addAll(arcdev.encodeAsJSONNumber);
        wadoSupportedSRClasses.clear();
        wadoSupportedSRClasses.addAll(arcdev.wadoSupportedSRClasses);
        wadoSupportedPRClasses.clear();
        wadoSupportedPRClasses.addAll(arcdev.wadoSupportedPRClasses);
        wadoIgnorePresentationLUTShape = arcdev.wadoIgnorePresentationLUTShape;
        wadoThumbnailViewPort = arcdev.wadoThumbnailViewPort;
        wadoZIPEntryNameFormat = arcdev.wadoZIPEntryNameFormat;
        wadoSR2HtmlTemplateURI = arcdev.wadoSR2HtmlTemplateURI;
        wadoSR2TextTemplateURI = arcdev.wadoSR2TextTemplateURI;
        wadoCDA2HtmlTemplateURI = arcdev.wadoCDA2HtmlTemplateURI;
        patientUpdateTemplateURI = arcdev.patientUpdateTemplateURI;
        importReportTemplateURI = arcdev.importReportTemplateURI;
        scheduleProcedureTemplateURI = arcdev.scheduleProcedureTemplateURI;
        outgoingPatientUpdateTemplateURI = arcdev.outgoingPatientUpdateTemplateURI;
        queryFetchSize = arcdev.queryFetchSize;
        queryMaxNumberOfResults = arcdev.queryMaxNumberOfResults;
        qidoMaxNumberOfResults = arcdev.qidoMaxNumberOfResults;
        qidoETag = arcdev.qidoETag;
        filterByIssuerOfPatientID = arcdev.filterByIssuerOfPatientID;
        matchSOPClassOnInstanceLevel = arcdev.matchSOPClassOnInstanceLevel;
        queryRetrieveViewMap.clear();
        queryRetrieveViewMap.putAll(arcdev.queryRetrieveViewMap);
        mppsForwardDestinations = arcdev.mppsForwardDestinations;
        ianDestinations = arcdev.ianDestinations;
        ianDelay = arcdev.ianDelay;
        ianTimeout = arcdev.ianTimeout;
        ianOnTimeout = arcdev.ianOnTimeout;
        ianTaskPollingInterval = arcdev.ianTaskPollingInterval;
        ianTaskFetchSize = arcdev.ianTaskFetchSize;
        spanningCFindSCP = arcdev.spanningCFindSCP;
        spanningCFindSCPRetrieveAETitles = arcdev.spanningCFindSCPRetrieveAETitles;
        spanningCFindSCPPolicy = arcdev.spanningCFindSCPPolicy;
        fallbackCMoveSCP = arcdev.fallbackCMoveSCP;
        fallbackCMoveSCPDestination = arcdev.fallbackCMoveSCPDestination;
        fallbackCMoveSCPCallingAET = arcdev.fallbackCMoveSCPCallingAET;
        fallbackCMoveSCPLeadingCFindSCP = arcdev.fallbackCMoveSCPLeadingCFindSCP;
        fallbackCMoveSCPRetries = arcdev.fallbackCMoveSCPRetries;
        fallbackWadoURIWebApplication = arcdev.fallbackWadoURIWebApplication;
        fallbackWadoURIHttpStatusCode = arcdev.fallbackWadoURIHttpStatusCode;
        externalRetrieveAEDestination = arcdev.externalRetrieveAEDestination;
        xdsiImagingDocumentSourceAETitle = arcdev.xdsiImagingDocumentSourceAETitle;
        alternativeCMoveSCP = arcdev.alternativeCMoveSCP;
        retrieveTaskWarningOnNoMatch = arcdev.retrieveTaskWarningOnNoMatch;
        retrieveTaskWarningOnWarnings = arcdev.retrieveTaskWarningOnWarnings;
        deleteRejectedPollingInterval = arcdev.deleteRejectedPollingInterval;
        deleteRejectedFetchSize = arcdev.deleteRejectedFetchSize;
        purgeStoragePollingInterval = arcdev.purgeStoragePollingInterval;
        purgeStorageFetchSize = arcdev.purgeStorageFetchSize;
        deleteStudyBatchSize = arcdev.deleteStudyBatchSize;
        deleteStudyChunkSize = arcdev.deleteStudyChunkSize;
        deletePatientOnDeleteLastStudy = arcdev.deletePatientOnDeleteLastStudy;
        failedToDeletePollingInterval = arcdev.failedToDeletePollingInterval;
        failedToDeleteFetchSize = arcdev.failedToDeleteFetchSize;
        maxAccessTimeStaleness = arcdev.maxAccessTimeStaleness;
        aeCacheStaleTimeout = arcdev.aeCacheStaleTimeout;
        leadingCFindSCPQueryCacheStaleTimeout = arcdev.leadingCFindSCPQueryCacheStaleTimeout;
        leadingCFindSCPQueryCacheSize = arcdev.leadingCFindSCPQueryCacheSize;
        auditSpoolDirectory = arcdev.auditSpoolDirectory;
        auditPollingInterval = arcdev.auditPollingInterval;
        auditAggregateDuration = arcdev.auditAggregateDuration;
        stowSpoolDirectory = arcdev.stowSpoolDirectory;
        wadoSpoolDirectory = arcdev.wadoSpoolDirectory;
        hl7LogFilePattern = arcdev.hl7LogFilePattern;
        hl7ErrorLogFilePattern = arcdev.hl7ErrorLogFilePattern;
        purgeTaskPollingInterval = arcdev.purgeTaskPollingInterval;
        purgeStgCmtPollingInterval = arcdev.purgeStgCmtPollingInterval;
        purgeStgCmtCompletedDelay = arcdev.purgeStgCmtCompletedDelay;
        keyValueRetentionPollingInterval = arcdev.keyValueRetentionPollingInterval;
        keyValueRetentionFetchSize = arcdev.keyValueRetentionFetchSize;
        keyValueRetentionPeriod = arcdev.keyValueRetentionPeriod;
        hideSPSWithStatusFrom = arcdev.hideSPSWithStatusFrom;
        hideSPSWithStatusFromMWLRS = arcdev.hideSPSWithStatusFromMWLRS;
        hl7ORUAction = arcdev.hl7ORUAction;
        mwlAccessionNumberGenerator = arcdev.mwlAccessionNumberGenerator;
        mwlRequestedProcedureIDGenerator = arcdev.mwlRequestedProcedureIDGenerator;
        mwlScheduledProcedureStepIDGenerator = arcdev.mwlScheduledProcedureStepIDGenerator;
        rejectExpiredStudiesPollingInterval = arcdev.rejectExpiredStudiesPollingInterval;
        rejectExpiredStudiesSchedules = arcdev.rejectExpiredStudiesSchedules;
        rejectExpiredStudiesFetchSize = arcdev.rejectExpiredStudiesFetchSize;
        rejectExpiredSeriesFetchSize = arcdev.rejectExpiredSeriesFetchSize;
        rejectExpiredStudiesAETitle = arcdev.rejectExpiredStudiesAETitle;
        fallbackCMoveSCPStudyOlderThan = arcdev.fallbackCMoveSCPStudyOlderThan;
        storePermissionServiceURL = arcdev.storePermissionServiceURL;
        storePermissionServiceResponse = arcdev.storePermissionServiceResponse;
        storePermissionServiceResponsePattern = arcdev.storePermissionServiceResponsePattern;
        storePermissionServiceExpirationDatePattern = arcdev.storePermissionServiceExpirationDatePattern;
        storePermissionServiceErrorCommentPattern = arcdev.storePermissionServiceErrorCommentPattern;
        storePermissionServiceErrorCodePattern = arcdev.storePermissionServiceErrorCodePattern;
        storePermissionCacheStaleTimeout = arcdev.storePermissionCacheStaleTimeout;
        storePermissionCacheSize = arcdev.storePermissionCacheSize;
        mergeMWLCacheStaleTimeout = arcdev.mergeMWLCacheStaleTimeout;
        mergeMWLCacheSize = arcdev.mergeMWLCacheSize;
        storeUpdateDBMaxRetries = arcdev.storeUpdateDBMaxRetries;
        storeUpdateDBMaxRetryDelay = arcdev.storeUpdateDBMaxRetryDelay;
        storeUpdateDBMinRetryDelay = arcdev.storeUpdateDBMinRetryDelay;
        allowRejectionForDataRetentionPolicyExpired = arcdev.allowRejectionForDataRetentionPolicyExpired;
        acceptMissingPatientID = arcdev.acceptMissingPatientID;
        allowDeleteStudyPermanently = arcdev.allowDeleteStudyPermanently;
        allowDeletePatient = arcdev.allowDeletePatient;
        retrieveAETitles = arcdev.retrieveAETitles;
        returnRetrieveAETitles = arcdev.returnRetrieveAETitles;
        remapRetrieveURL = arcdev.remapRetrieveURL;
        remapRetrieveURLClientHost = arcdev.remapRetrieveURLClientHost;
        hl7PSUSendingApplication = arcdev.hl7PSUSendingApplication;
        hl7PSUReceivingApplications = arcdev.hl7PSUReceivingApplications;
        hl7PSUDelay = arcdev.hl7PSUDelay;
        hl7PSUTimeout = arcdev.hl7PSUTimeout;
        hl7PSUOnTimeout = arcdev.hl7PSUOnTimeout;
        hl7PSUTaskPollingInterval = arcdev.hl7PSUTaskPollingInterval;
        hl7PSUTaskFetchSize = arcdev.hl7PSUTaskFetchSize;
        hl7PSUMWL = arcdev.hl7PSUMWL;
        hl7PSUMWLMatchingKey = arcdev.hl7PSUMWLMatchingKey;
        hl7PSUForRequestedProcedure = arcdev.hl7PSUForRequestedProcedure;
        hl7PSUPIDPV1 = arcdev.hl7PSUPIDPV1;
        hl7PSURequestedProcedureID = arcdev.hl7PSURequestedProcedureID;
        hl7PSUAccessionNumber = arcdev.hl7PSUAccessionNumber;
        hl7PSUFillerOrderNumber = arcdev.hl7PSUFillerOrderNumber;
        hl7PSUPlacerOrderNumber = arcdev.hl7PSUPlacerOrderNumber;
        hl7PSUMessageType = arcdev.hl7PSUMessageType;
        hl7PSUConditions = arcdev.hl7PSUConditions;
        hl7PSUMppsTemplateURI = arcdev.hl7PSUMppsTemplateURI;
        hl7PSUStudyTemplateURI = arcdev.hl7PSUStudyTemplateURI;
        acceptConflictingPatientID = arcdev.acceptConflictingPatientID;
        userIdentityNegotiation = arcdev.userIdentityNegotiation;
        userIdentityNegotiationRole = arcdev.userIdentityNegotiationRole;
        userIdentityNegotiationKeycloakClientID = arcdev.userIdentityNegotiationKeycloakClientID;
        proxyUpstreamURL = arcdev.proxyUpstreamURL;
        atna2JsonFhirTemplateURI = arcdev.atna2JsonFhirTemplateURI;
        atna2XmlFhirTemplateURI = arcdev.atna2XmlFhirTemplateURI;
        copyMoveUpdatePolicy = arcdev.copyMoveUpdatePolicy;
        linkMWLEntryUpdatePolicy = arcdev.linkMWLEntryUpdatePolicy;
        hl7TrackChangedPatientID = arcdev.hl7TrackChangedPatientID;
        hl7ADTReceivingApplication = arcdev.hl7ADTReceivingApplication;
        hl7ADTSendingApplication = arcdev.hl7ADTSendingApplication;
        hl7ScheduledProtocolCodeInOrder = arcdev.hl7ScheduledProtocolCodeInOrder;
        hl7ScheduledStationAETInOrder = arcdev.hl7ScheduledStationAETInOrder;
        auditUnknownStudyInstanceUID = arcdev.auditUnknownStudyInstanceUID;
        auditUnknownPatientID = arcdev.auditUnknownPatientID;
        auditSoftwareConfigurationVerbose = arcdev.auditSoftwareConfigurationVerbose;
        hl7UseNullValue = arcdev.hl7UseNullValue;
        queueTasksFetchSize = arcdev.queueTasksFetchSize;
        auditHL7MsgLimit = arcdev.auditHL7MsgLimit;
        rejectionNoteStorageAET = arcdev.rejectionNoteStorageAET;
        uiConfigurationDeviceName = arcdev.uiConfigurationDeviceName;
        storageVerificationPolicy = arcdev.storageVerificationPolicy;
        storageVerificationUpdateLocationStatus = arcdev.storageVerificationUpdateLocationStatus;
        storageVerificationStorageIDs = arcdev.storageVerificationStorageIDs;
        storageVerificationAETitle = arcdev.storageVerificationAETitle;
        storageVerificationBatchID = arcdev.storageVerificationBatchID;
        storageVerificationInitialDelay = arcdev.storageVerificationInitialDelay;
        storageVerificationPeriod = arcdev.storageVerificationPeriod;
        storageVerificationSchedules = arcdev.storageVerificationSchedules;
        storageVerificationMaxScheduled = arcdev.storageVerificationMaxScheduled;
        storageVerificationPollingInterval = arcdev.storageVerificationPollingInterval;
        storageVerificationFetchSize = arcdev.storageVerificationFetchSize;
        updateLocationStatusOnRetrieve = arcdev.updateLocationStatusOnRetrieve;
        storageVerificationOnRetrieve = arcdev.storageVerificationOnRetrieve;
        compressionAETitle = arcdev.compressionAETitle;
        compressionPollingInterval = arcdev.compressionPollingInterval;
        compressionFetchSize = arcdev.compressionFetchSize;
        compressionSchedules = arcdev.compressionSchedules;
        compressionThreads = arcdev.compressionThreads;
        diffTaskProgressUpdateInterval = arcdev.diffTaskProgressUpdateInterval;
        patientVerificationPDQServiceID = arcdev.patientVerificationPDQServiceID;
        patientVerificationPollingInterval = arcdev.patientVerificationPollingInterval;
        patientVerificationFetchSize = arcdev.patientVerificationFetchSize;
        patientVerificationMaxStaleness = arcdev.patientVerificationMaxStaleness;
        patientVerificationPeriod = arcdev.patientVerificationPeriod;
        patientVerificationRetryInterval = arcdev.patientVerificationRetryInterval;
        patientVerificationPeriodOnNotFound = arcdev.patientVerificationPeriodOnNotFound;
        patientVerificationMaxRetries = arcdev.patientVerificationMaxRetries;
        patientVerificationAdjustIssuerOfPatientID = arcdev.patientVerificationAdjustIssuerOfPatientID;
        csvUploadChunkSize = arcdev.csvUploadChunkSize;
        validateUID = arcdev.validateUID;
        hl7OrderMissingStudyIUIDPolicy = arcdev.hl7OrderMissingStudyIUIDPolicy;
        hl7OrderMissingAdmissionIDPolicy = arcdev.hl7OrderMissingAdmissionIDPolicy;
        hl7ImportReportMissingStudyIUIDPolicy = arcdev.hl7ImportReportMissingStudyIUIDPolicy;
        hl7ImportReportMissingAdmissionIDPolicy = arcdev.hl7ImportReportMissingAdmissionIDPolicy;
        hl7ImportReportMissingStudyIUIDCFindSCP = arcdev.hl7ImportReportMissingStudyIUIDCFindSCP;
        hl7ReferredMergedPatientPolicy = arcdev.hl7ReferredMergedPatientPolicy;
        hl7DicomCharacterSet = arcdev.hl7DicomCharacterSet;
        hl7VeterinaryUsePatientName = arcdev.hl7VeterinaryUsePatientName;
        hl7PatientArrivalMessageType = arcdev.hl7PatientArrivalMessageType;
        relationalQueryNegotiationLenient = arcdev.relationalQueryNegotiationLenient;
        relationalRetrieveNegotiationLenient = arcdev.relationalRetrieveNegotiationLenient;
        rejectConflictingPatientAttribute = arcdev.rejectConflictingPatientAttribute;
        schedulerMinStartDelay = arcdev.schedulerMinStartDelay;
        stowRetiredTransferSyntax = arcdev.stowRetiredTransferSyntax;
        stowExcludeAPPMarkers = arcdev.stowExcludeAPPMarkers;
        restrictRetrieveSilently = arcdev.restrictRetrieveSilently;
        stowQuicktime2MP4 = arcdev.stowQuicktime2MP4;
        identifyPatientByAllAttributes = arcdev.identifyPatientByAllAttributes;
        multipleStoreAssociations = arcdev.multipleStoreAssociations;
        mwlPollingInterval = arcdev.mwlPollingInterval;
        mwlFetchSize = arcdev.mwlFetchSize;
        mwlImportInterval = arcdev.mwlImportInterval;
        deleteMWLDelay = arcdev.deleteMWLDelay;
        studySizeDelay = arcdev.studySizeDelay;
        calculateStudySizePollingInterval = arcdev.calculateStudySizePollingInterval;
        calculateStudySizeFetchSize = arcdev.calculateStudySizeFetchSize;
        calculateQueryAttributes = arcdev.calculateQueryAttributes;
        storeImplementationVersionName = arcdev.storeImplementationVersionName;
        supplementIssuerFetchSize = arcdev.supplementIssuerFetchSize;
        updateCharsetFetchSize = arcdev.updateCharsetFetchSize;
        auditAssigningAuthorityOfPatientID = arcdev.auditAssigningAuthorityOfPatientID;
        changeRequesterAET = arcdev.changeRequesterAET;
        attributeFilters.clear();
        attributeFilters.putAll(arcdev.attributeFilters);
        attributeSet.clear();
        attributeSet.putAll(arcdev.attributeSet);
        bulkDataDescriptorMap.clear();
        bulkDataDescriptorMap.putAll(arcdev.bulkDataDescriptorMap);
        idGenerators.clear();
        idGenerators.putAll(arcdev.idGenerators);
        storageDescriptorMap.clear();
        storageDescriptorMap.putAll(arcdev.storageDescriptorMap);
        queueDescriptorMap.clear();
        queueDescriptorMap.putAll(arcdev.queueDescriptorMap);
        metricsDescriptorMap.clear();
        metricsDescriptorMap.putAll(arcdev.metricsDescriptorMap);
        pdqServiceDescriptorMap.clear();
        pdqServiceDescriptorMap.putAll(arcdev.pdqServiceDescriptorMap);
        exporterDescriptorMap.clear();
        exporterDescriptorMap.putAll(arcdev.exporterDescriptorMap);
        upsOnStoreList.clear();
        upsOnStoreList.addAll(arcdev.upsOnStoreList);
        upsOnUPSCompletedList.clear();
        upsOnUPSCompletedList.addAll(arcdev.upsOnUPSCompletedList);
        upsOnHL7List.clear();
        upsOnHL7List.addAll(arcdev.upsOnHL7List);
        upsProcessingRuleList.clear();
        upsProcessingRuleList.addAll(arcdev.upsProcessingRuleList);
        exportRules.clear();
        exportRules.addAll(arcdev.exportRules);
        exportPriorsRules.clear();
        exportPriorsRules.addAll(arcdev.exportPriorsRules);
        mppsForwardRule.clear();
        mppsForwardRule.addAll(arcdev.mppsForwardRule);
        hl7ExportRules.clear();
        hl7ExportRules.addAll(arcdev.hl7ExportRules);
        hl7PrefetchRules.clear();
        hl7PrefetchRules.addAll(arcdev.hl7PrefetchRules);
        rsForwardRules.clear();
        rsForwardRules.addAll(arcdev.rsForwardRules);
        hl7ForwardRules.clear();
        hl7ForwardRules.addAll(arcdev.hl7ForwardRules);
        hl7OrderScheduledStations.clear();
        hl7OrderScheduledStations.addAll(arcdev.hl7OrderScheduledStations);
        hl7OrderSPSStatuses.clear();
        hl7OrderSPSStatuses.putAll(arcdev.hl7OrderSPSStatuses);
        hl7NoPatientCreateMessageTypes.clear();
        hl7NoPatientCreateMessageTypes.addAll(arcdev.hl7NoPatientCreateMessageTypes);
        compressionRules.clear();
        compressionRules.addAll(arcdev.compressionRules);
        studyRetentionPolicies.clear();
        studyRetentionPolicies.addAll(arcdev.studyRetentionPolicies);
        hl7StudyRetentionPolicies.clear();
        hl7StudyRetentionPolicies.addAll(arcdev.hl7StudyRetentionPolicies);
        attributeCoercions.clear();
        attributeCoercions.addAll(arcdev.attributeCoercions);
        attributeCoercions2.clear();
        attributeCoercions2.addAll(arcdev.attributeCoercions2);
        storeAccessControlIDRules.clear();
        storeAccessControlIDRules.addAll(arcdev.storeAccessControlIDRules);
        mwlIdleTimeoutList.clear();
        mwlIdleTimeoutList.addAll(arcdev.mwlIdleTimeoutList);
        mwlImportList.clear();
        mwlImportList.addAll(arcdev.mwlImportList);
        rejectionNoteMap.clear();
        rejectionNoteMap.putAll(arcdev.rejectionNoteMap);
        xRoadProperties.clear();
        xRoadProperties.putAll(arcdev.xRoadProperties);
        impaxReportProperties.clear();
        impaxReportProperties.putAll(arcdev.impaxReportProperties);
        importReportTemplateParams.clear();
        importReportTemplateParams.putAll(arcdev.importReportTemplateParams);
        hl7PSUTemplateParams.clear();
        hl7PSUTemplateParams.putAll(arcdev.hl7PSUTemplateParams);
        cStoreSCUOfCMoveSCP.clear();
        cStoreSCUOfCMoveSCP.putAll(arcdev.cStoreSCUOfCMoveSCP);
        dicomCharsetNameMappings.clear();
        dicomCharsetNameMappings.putAll(arcdev.dicomCharsetNameMappings);
        hl7CharsetNameMappings.clear();
        hl7CharsetNameMappings.putAll(arcdev.hl7CharsetNameMappings);
        upsEventWebSocketQueueSizes.clear();
        upsEventWebSocketQueueSizes.putAll(arcdev.upsEventWebSocketQueueSizes);
    }
}
