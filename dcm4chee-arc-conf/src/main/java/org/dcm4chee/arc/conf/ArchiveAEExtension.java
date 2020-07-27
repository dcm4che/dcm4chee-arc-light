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

import org.dcm4che3.data.Attributes;
import org.dcm4che3.io.BulkDataDescriptor;
import org.dcm4che3.net.AEExtension;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Dimse;
import org.dcm4che3.net.TransferCapability;
import org.dcm4che3.util.StringUtils;

import java.io.File;
import java.time.Period;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jul 2015
 */
public class ArchiveAEExtension extends AEExtension {
    private String defaultCharacterSet;
    private String upsWorklistLabel;
    private String[] upsEventSCUs = {};
    private int upsEventSCUKeepAlive;
    private String[] objectStorageIDs = {};
    private int objectStorageCount = 1;
    private String[] metadataStorageIDs = {};
    private String bulkDataDescriptorID;
    private Duration seriesMetadataDelay;
    private Duration purgeInstanceRecordsDelay;
    private String storeAccessControlID;
    private String[] accessControlIDs = {};
    private Boolean rejectIfNoUserIdentity;
    private OverwritePolicy overwritePolicy;
    private Boolean recordAttributeModification;
    private String bulkDataSpoolDirectory;
    private String queryRetrieveViewID;
    private Boolean validateCallingAEHostname;
    private Boolean personNameComponentOrderInsensitiveMatching;
    private Boolean sendPendingCGet;
    private Duration sendPendingCMoveInterval;
    private String wadoThumbnailViewPort;
    private String wadoZIPEntryNameFormat;
    private String wadoSR2HtmlTemplateURI;
    private String wadoSR2TextTemplateURI;
    private String wadoCDA2HtmlTemplateURI;
    private String[] mppsForwardDestinations = {};
    private String[] ianDestinations = {};
    private Duration ianDelay;
    private Duration ianTimeout;
    private Boolean ianOnTimeout;
    private String spanningCFindSCP;
    private String[] spanningCFindSCPRetrieveAETitles = {};
    private SpanningCFindSCPPolicy spanningCFindSCPPolicy;
    private Integer fallbackCMoveSCPRetries;
    private String fallbackCMoveSCP;
    private String fallbackCMoveSCPDestination;
    private String fallbackCMoveSCPCallingAET;
    private String fallbackCMoveSCPLeadingCFindSCP;
    private String fallbackCMoveSCPStudyOlderThan;
    private String fallbackWadoURIWebApplication;
    private Integer fallbackWadoURIHttpStatusCode;
    private String externalRetrieveAEDestination;
    private String alternativeCMoveSCP;
    private Integer queryMaxNumberOfResults;
    private Integer qidoMaxNumberOfResults;
    private SPSStatus[] hideSPSWithStatusFromMWL = {};
    private String storePermissionServiceURL;
    private String storePermissionServiceResponse;
    private Pattern storePermissionServiceResponsePattern;
    private Pattern storePermissionServiceExpirationDatePattern;
    private Pattern storePermissionServiceErrorCommentPattern;
    private Pattern storePermissionServiceErrorCodePattern;
    private AllowRejectionForDataRetentionPolicyExpired allowRejectionForDataRetentionPolicyExpired;
    private AcceptMissingPatientID acceptMissingPatientID;
    private AllowDeletePatient allowDeletePatient;
    private AllowDeleteStudyPermanently allowDeleteStudyPermanently;
    private AcceptConflictingPatientID acceptConflictingPatientID;
    private String[] retrieveAETitles = {};
    private String[] returnRetrieveAETitles = {};
    private String hl7PSUSendingApplication;
    private String[] hl7PSUReceivingApplications = {};
    private Duration hl7PSUDelay;
    private Duration hl7PSUTimeout;
    private Boolean hl7PSUOnTimeout;
    private Boolean hl7PSUMWL;
    private Boolean hl7PSUForRequestedProcedure;
    private Boolean hl7PSUPIDPV1;
    private String hl7PSURequestedProcedureID;
    private String hl7PSUAccessionNumber;
    private String hl7PSUFillerOrderNumber;
    private String hl7PSUPlacerOrderNumber;
    private HL7PSUMessageType hl7PSUMessageType;
    private Conditions hl7PSUConditions = new Conditions();
    private Attributes.UpdatePolicy copyMoveUpdatePolicy;
    private Attributes.UpdatePolicy linkMWLEntryUpdatePolicy;
    private StorageVerificationPolicy storageVerificationPolicy;
    private Boolean storageVerificationUpdateLocationStatus;
    private String[] storageVerificationStorageIDs = {};
    private Period storageVerificationInitialDelay;
    private Boolean updateLocationStatusOnRetrieve;
    private Boolean storageVerificationOnRetrieve;
    private Boolean relationalQueryNegotiationLenient;
    private Boolean relationalRetrieveNegotiationLenient;
    private Boolean stowRetiredTransferSyntax;
    private Boolean stowExcludeAPPMarkers;
    private Boolean restrictRetrieveSilently;
    private Boolean retrieveTaskWarningOnNoMatch;
    private Boolean retrieveTaskWarningOnWarnings;
    private Boolean stowQuicktime2MP4;
    private int[] rejectConflictingPatientAttribute = {};
    private MultipleStoreAssociations[] multipleStoreAssociations = {};
    private final LinkedHashSet<String> acceptedMoveDestinations = new LinkedHashSet<>();
    private final List<UPSOnStore> upsOnStoreList = new ArrayList<>();
    private final List<ExportRule> exportRules = new ArrayList<>();
    private final List<ExportPriorsRule> exportPriorsRules = new ArrayList<>();
    private final List<RSForwardRule> rsForwardRules = new ArrayList<>();
    private final List<ArchiveCompressionRule> compressionRules = new ArrayList<>();
    private final List<ArchiveAttributeCoercion> attributeCoercions = new ArrayList<>();
    private final List<StudyRetentionPolicy> studyRetentionPolicies = new ArrayList<>();
    private final List<StoreAccessControlIDRule> storeAccessControlIDRules = new ArrayList<>();

    public String getDefaultCharacterSet() {
        return defaultCharacterSet;
    }

    public void setDefaultCharacterSet(String defaultCharacterSet) {
        this.defaultCharacterSet = defaultCharacterSet;
    }

    public String defaultCharacterSet() {
        return defaultCharacterSet != null
                ? defaultCharacterSet
                : getArchiveDeviceExtension().getDefaultCharacterSet();
    }

    public String getUPSWorklistLabel() {
        return upsWorklistLabel;
    }

    public void setUPSWorklistLabel(String upsWorklistLabel) {
        this.upsWorklistLabel = upsWorklistLabel;
    }

    public String upsWorklistLabel() {
        return defaultCharacterSet != null
                ? upsWorklistLabel
                : StringUtils.maskNull(getArchiveDeviceExtension().getUPSWorklistLabel(), ae.getAETitle());
    }

    public String[] getUPSEventSCUs() {
        return upsEventSCUs;
    }

    public void setUPSEventSCUs(String[] upsEventSCUs) {
        this.upsEventSCUs = upsEventSCUs;
    }

    public String[] upsEventSCUs() {
        return upsEventSCUs.length > 0
                ? upsEventSCUs
                : getArchiveDeviceExtension().getUPSEventSCUs();
    }

    public boolean isUPSEventSCU(String aet) {
        return Stream.of(upsEventSCUs()).anyMatch(aet::equals);
    }

    public int getUPSEventSCUKeepAlive() {
        return upsEventSCUKeepAlive;
    }

    public void setUPSEventSCUKeepAlive(int upsEventSCUKeepAlive) {
        this.upsEventSCUKeepAlive = upsEventSCUKeepAlive;
    }

    public int upsEventSCUKeepAlive() {
        return upsEventSCUKeepAlive > 0
                ? upsEventSCUKeepAlive
                : getArchiveDeviceExtension().getUPSEventSCUKeepAlive();
    }


    public String[] getObjectStorageIDs() {
        return objectStorageIDs;
    }

    public void setObjectStorageIDs(String... objectStorageIDs) {
        Arrays.sort(this.objectStorageIDs = objectStorageIDs);
    }

    public int getObjectStorageCount() {
        return objectStorageCount;
    }

    public void setObjectStorageCount(int objectStorageCount) {
        this.objectStorageCount = objectStorageCount;
    }

    public String[] getMetadataStorageIDs() {
        return metadataStorageIDs;
    }

    public void setMetadataStorageIDs(String... metadataStorageIDs) {
        Arrays.sort(this.metadataStorageIDs = metadataStorageIDs);
    }

    public String getBulkDataDescriptorID() {
        return bulkDataDescriptorID;
    }

    public void setBulkDataDescriptorID(String bulkDataDescriptorID) {
        this.bulkDataDescriptorID = bulkDataDescriptorID;
    }

    public BulkDataDescriptor getBulkDataDescriptor() {
        ArchiveDeviceExtension arcdev = getArchiveDeviceExtension();
        return arcdev.getBulkDataDescriptor(bulkDataDescriptorID != null
                ? bulkDataDescriptorID
                : arcdev.getBulkDataDescriptorID());
    }

    public Duration getSeriesMetadataDelay() {
        return seriesMetadataDelay;
    }

    public void setSeriesMetadataDelay(Duration seriesMetadataDelay) {
        this.seriesMetadataDelay = seriesMetadataDelay;
    }

    public Duration seriesMetadataDelay() {
        return seriesMetadataDelay != null
                ? seriesMetadataDelay
                : getArchiveDeviceExtension().getSeriesMetadataDelay();
    }

    public Duration getPurgeInstanceRecordsDelay() {
        return purgeInstanceRecordsDelay;
    }

    public void setPurgeInstanceRecordsDelay(Duration purgeInstanceRecordsDelay) {
        this.purgeInstanceRecordsDelay = purgeInstanceRecordsDelay;
    }

    public Duration purgeInstanceRecordsDelay() {
        ArchiveDeviceExtension arcdev = getArchiveDeviceExtension();
        return arcdev.isPurgeInstanceRecords()
            ? purgeInstanceRecordsDelay != null
                ? purgeInstanceRecordsDelay
                : arcdev.getPurgeInstanceRecordsDelay()
            : null;
    }

    public String getStoreAccessControlID() {
        return storeAccessControlID;
    }

    public void setStoreAccessControlID(String storeAccessControlID) {
        this.storeAccessControlID = storeAccessControlID;
    }

    public String[] getAccessControlIDs() {
        return accessControlIDs;
    }

    public void setAccessControlIDs(String[] accessControlIDs) {
        this.accessControlIDs = accessControlIDs;
    }

    public Boolean getRejectIfNoUserIdentity() { return rejectIfNoUserIdentity; }

    public void setRejectIfNoUserIdentity(Boolean rejectIfNoUserIdentity) {
        this.rejectIfNoUserIdentity = rejectIfNoUserIdentity;
    }

    public boolean rejectIfNoUserIdentity() {
        return rejectIfNoUserIdentity != null
                ? rejectIfNoUserIdentity
                : getArchiveDeviceExtension().isRejectIfNoUserIdentity();
    }

    public OverwritePolicy getOverwritePolicy() {
        return overwritePolicy;
    }

    public void setOverwritePolicy(OverwritePolicy overwritePolicy) {
        this.overwritePolicy = overwritePolicy;
    }

    public OverwritePolicy overwritePolicy() {
        return overwritePolicy != null
                ? overwritePolicy
                : getArchiveDeviceExtension().getOverwritePolicy();
    }

    public Boolean getRecordAttributeModification() {
        return recordAttributeModification;
    }

    public void setRecordAttributeModification(Boolean recordAttributeModification) {
        this.recordAttributeModification = recordAttributeModification;
    }

    public boolean recordAttributeModification() {
        return recordAttributeModification != null
                ? recordAttributeModification
                : getArchiveDeviceExtension().isRecordAttributeModification();
    }

    public AcceptMissingPatientID getAcceptMissingPatientID() {
        return acceptMissingPatientID;
    }

    public void setAcceptMissingPatientID(AcceptMissingPatientID acceptMissingPatientID) {
        this.acceptMissingPatientID = acceptMissingPatientID;
    }

    public AcceptMissingPatientID acceptMissingPatientID() {
        return acceptMissingPatientID != null
                ? acceptMissingPatientID
                : getArchiveDeviceExtension().getAcceptMissingPatientID();
    }

    public String getBulkDataSpoolDirectory() {
        return bulkDataSpoolDirectory;
    }

    public void setBulkDataSpoolDirectory(String bulkDataSpoolDirectory) {
        this.bulkDataSpoolDirectory = bulkDataSpoolDirectory;
    }

    public String bulkDataSpoolDirectory() {
        return bulkDataSpoolDirectory != null
                ? bulkDataSpoolDirectory
                : getArchiveDeviceExtension().getBulkDataSpoolDirectory();
    }

    public File getBulkDataSpoolDirectoryFile() {
        return new File(StringUtils.replaceSystemProperties(bulkDataSpoolDirectory()));
    }

    public String getQueryRetrieveViewID() {
        return queryRetrieveViewID;
    }

    public void setQueryRetrieveViewID(String queryRetrieveViewID) {
        this.queryRetrieveViewID = queryRetrieveViewID;
    }

    public Boolean getValidateCallingAEHostname() {
        return validateCallingAEHostname;
    }

    public void setValidateCallingAEHostname(Boolean validateCallingAEHostname) {
        this.validateCallingAEHostname = validateCallingAEHostname;
    }

    public boolean validateCallingAEHostname() {
        return validateCallingAEHostname != null
                ? validateCallingAEHostname
                : getArchiveDeviceExtension().isValidateCallingAEHostname();
    }

    public Boolean getPersonNameComponentOrderInsensitiveMatching() {
        return personNameComponentOrderInsensitiveMatching;
    }

    public void setPersonNameComponentOrderInsensitiveMatching(Boolean personNameComponentOrderInsensitiveMatching) {
        this.personNameComponentOrderInsensitiveMatching = personNameComponentOrderInsensitiveMatching;
    }

    public boolean personNameComponentOrderInsensitiveMatching() {
        return personNameComponentOrderInsensitiveMatching != null
                ? personNameComponentOrderInsensitiveMatching
                : getArchiveDeviceExtension().isPersonNameComponentOrderInsensitiveMatching();
    }

    public Boolean getSendPendingCGet() {
        return sendPendingCGet;
    }

    public void setSendPendingCGet(Boolean sendPendingCGet) {
        this.sendPendingCGet = sendPendingCGet;
    }

    public boolean sendPendingCGet() {
        return sendPendingCGet != null
                ? sendPendingCGet
                : getArchiveDeviceExtension().isSendPendingCGet();
    }

    public Duration getSendPendingCMoveInterval() {
        return sendPendingCMoveInterval;
    }

    public void setSendPendingCMoveInterval(Duration sendPendingCMoveInterval) {
        this.sendPendingCMoveInterval = sendPendingCMoveInterval;
    }

    public Duration sendPendingCMoveInterval() {
        return sendPendingCMoveInterval != null
                ? sendPendingCMoveInterval
                : getArchiveDeviceExtension().getSendPendingCMoveInterval();
    }

    public String getWadoThumbnailViewPort() {
        return wadoThumbnailViewPort;
    }

    public void setWadoThumbnailViewPort(String wadoThumbnailViewPort) {
        this.wadoThumbnailViewPort = wadoThumbnailViewPort;
    }

    public String wadoThumbnailViewPort() {
        return wadoThumbnailViewPort != null
                ? wadoThumbnailViewPort
                : getArchiveDeviceExtension().getWadoThumbnailViewPort();
    }

    public String getWadoZIPEntryNameFormat() {
        return wadoZIPEntryNameFormat;
    }

    public void setWadoZIPEntryNameFormat(String wadoZIPEntryNameFormat) {
        this.wadoZIPEntryNameFormat = wadoZIPEntryNameFormat;
    }

    public String wadoZIPEntryNameFormat() {
        return wadoZIPEntryNameFormat != null
                ? wadoZIPEntryNameFormat
                : getArchiveDeviceExtension().getWadoZIPEntryNameFormat();
    }

    public String getWadoSR2HtmlTemplateURI() {
        return wadoSR2HtmlTemplateURI;
    }

    public void setWadoSR2HtmlTemplateURI(String wadoSR2HtmlTemplateURI) {
        this.wadoSR2HtmlTemplateURI = wadoSR2HtmlTemplateURI;
    }

    public String wadoSR2HtmlTemplateURI() {
        return wadoSR2HtmlTemplateURI != null
                ? wadoSR2HtmlTemplateURI
                : getArchiveDeviceExtension().getWadoSR2HtmlTemplateURI();
    }

    public String getWadoSR2TextTemplateURI() {
        return wadoSR2TextTemplateURI;
    }

    public void setWadoSR2TextTemplateURI(String wadoSR2TextTemplateURI) {
        this.wadoSR2TextTemplateURI = wadoSR2TextTemplateURI;
    }

    public String wadoSR2TextTemplateURI() {
        return wadoSR2TextTemplateURI != null
                ? wadoSR2TextTemplateURI
                : getArchiveDeviceExtension().getWadoSR2TextTemplateURI();
    }

    public String getWadoCDA2HtmlTemplateURI() {
        return wadoCDA2HtmlTemplateURI;
    }

    public void setWadoCDA2HtmlTemplateURI(String wadoCDA2HtmlTemplateURI) {
        this.wadoCDA2HtmlTemplateURI = wadoCDA2HtmlTemplateURI;
    }

    public String wadoCDA2HtmlTemplateURI() {
        return wadoCDA2HtmlTemplateURI != null
                ? wadoCDA2HtmlTemplateURI
                : getArchiveDeviceExtension().getWadoCDA2HtmlTemplateURI();
    }

    public String[] getMppsForwardDestinations() {
        return mppsForwardDestinations;
    }

    public void setMppsForwardDestinations(String... mppsForwardDestinations) {
        this.mppsForwardDestinations = mppsForwardDestinations;
    }

    public String[] mppsForwardDestinations() {
        return mppsForwardDestinations.length > 0
                ? mppsForwardDestinations
                : getArchiveDeviceExtension().getMppsForwardDestinations();
    }

    public String[] getIanDestinations() {
        return ianDestinations;
    }

    public void setIanDestinations(String... ianDestinations) {
        this.ianDestinations = ianDestinations;
    }

    public String[] ianDestinations() {
        return ianDestinations.length > 0
                ? ianDestinations
                : getArchiveDeviceExtension().getIanDestinations();
    }

    public Duration getIanDelay() {
        return ianDelay;
    }

    public void setIanDelay(Duration ianDelay) {
        this.ianDelay = ianDelay;
    }

    public Duration ianDelay() {
        return ianDelay != null
                ? ianDelay
                : getArchiveDeviceExtension().getIanDelay();
    }

    public Duration getIanTimeout() {
        return ianTimeout;
    }

    public void setIanTimeout(Duration ianTimeout) {
        this.ianTimeout = ianTimeout;
    }

    public Duration ianTimeout() {
        return ianTimeout != null
                ? ianTimeout
                : getArchiveDeviceExtension().getIanTimeout();
    }

    public Boolean getIanOnTimeout() {
        return ianOnTimeout;
    }

    public void setIanOnTimeout(Boolean ianOnTimeout) {
        this.ianOnTimeout = ianOnTimeout;
    }

    public boolean ianOnTimeout() {
        return ianOnTimeout != null
                ? ianOnTimeout
                : getArchiveDeviceExtension().isIanOnTimeout();
    }

    public String getSpanningCFindSCP() {
        return spanningCFindSCP;
    }

    public void setSpanningCFindSCP(String spanningCFindSCP) {
        this.spanningCFindSCP = spanningCFindSCP;
    }

    public String spanningCFindSCP() {
        return spanningCFindSCP != null
                ? spanningCFindSCP
                : getArchiveDeviceExtension().getSpanningCFindSCP();
    }

    public String[] getSpanningCFindSCPRetrieveAETitles() {
        return spanningCFindSCPRetrieveAETitles;
    }

    public void setSpanningCFindSCPRetrieveAETitles(String[] spanningCFindSCPRetrieveAETitles) {
        this.spanningCFindSCPRetrieveAETitles = spanningCFindSCPRetrieveAETitles;
    }

    public String[] spanningCFindSCPRetrieveAETitles() {
        return spanningCFindSCPRetrieveAETitles.length > 0
                ? spanningCFindSCPRetrieveAETitles
                : getArchiveDeviceExtension().getSpanningCFindSCPRetrieveAETitles();
    }

    public SpanningCFindSCPPolicy getSpanningCFindSCPPolicy() {
        return spanningCFindSCPPolicy;
    }

    public void setSpanningCFindSCPPolicy(SpanningCFindSCPPolicy spanningCFindSCPPolicy) {
        this.spanningCFindSCPPolicy = spanningCFindSCPPolicy;
    }

    public SpanningCFindSCPPolicy spanningCFindSCPPolicy() {
        return spanningCFindSCPPolicy != null
                ? spanningCFindSCPPolicy
                : getArchiveDeviceExtension().getSpanningCFindSCPPolicy();
    }

    public String getFallbackCMoveSCP() {
        return fallbackCMoveSCP;
    }

    public void setFallbackCMoveSCP(String fallbackCMoveSCP) {
        this.fallbackCMoveSCP = fallbackCMoveSCP;
    }

    public String fallbackCMoveSCP() {
        return fallbackCMoveSCP != null
                ? fallbackCMoveSCP
                : getArchiveDeviceExtension().getFallbackCMoveSCP();
    }

    public String getFallbackCMoveSCPDestination() {
        return fallbackCMoveSCPDestination;
    }

    public void setFallbackCMoveSCPDestination(String fallbackCMoveSCPDestination) {
        this.fallbackCMoveSCPDestination = fallbackCMoveSCPDestination;
    }

    public String fallbackCMoveSCPDestination() {
        return fallbackCMoveSCPDestination != null
                ? fallbackCMoveSCPDestination
                : getArchiveDeviceExtension().getFallbackCMoveSCPDestination();
    }

    public String getFallbackCMoveSCPCallingAET() {
        return fallbackCMoveSCPCallingAET;
    }

    public void setFallbackCMoveSCPCallingAET(String fallbackCMoveSCPCallingAET) {
        this.fallbackCMoveSCPCallingAET = fallbackCMoveSCPCallingAET;
    }

    public String fallbackCMoveSCPCallingAET(Association as) {
        String aet = fallbackCMoveSCPCallingAET != null
                ? fallbackCMoveSCPCallingAET
                : getArchiveDeviceExtension().getFallbackCMoveSCPCallingAET();
        return aet != null ? aet : as.getCallingAET();
    }

    public String getFallbackCMoveSCPLeadingCFindSCP() {
        return fallbackCMoveSCPLeadingCFindSCP;
    }

    public void setFallbackCMoveSCPLeadingCFindSCP(String fallbackCMoveSCPLeadingCFindSCP) {
        this.fallbackCMoveSCPLeadingCFindSCP = fallbackCMoveSCPLeadingCFindSCP;
    }

    public String fallbackCMoveSCPLeadingCFindSCP() {
        return fallbackCMoveSCPLeadingCFindSCP != null
                ? fallbackCMoveSCPLeadingCFindSCP
                : getArchiveDeviceExtension().getFallbackCMoveSCPLeadingCFindSCP();
    }

    public void setFallbackCMoveSCPRetries(Integer fallbackCMoveSCPRetries) {
        this.fallbackCMoveSCPRetries = fallbackCMoveSCPRetries;
    }

    public Integer getFallbackCMoveSCPRetries() {
        return fallbackCMoveSCPRetries;
    }

    public int fallbackCMoveSCPRetries() {
        return fallbackCMoveSCPRetries != null
                ? fallbackCMoveSCPRetries
                : getArchiveDeviceExtension().getFallbackCMoveSCPRetries();
    }


    public String getFallbackWadoURIWebApplication() {
        return fallbackWadoURIWebApplication;
    }

    public void setFallbackWadoURIWebApplication(String fallbackWadoURIWebApplication) {
        this.fallbackWadoURIWebApplication = fallbackWadoURIWebApplication;
    }

    public String fallbackWadoURIWebApplication() {
        return fallbackWadoURIWebApplication != null
                ? fallbackWadoURIWebApplication
                : getArchiveDeviceExtension().getFallbackWadoURIWebApplication();
    }

    public Integer getFallbackWadoURIHttpStatusCode() {
        return fallbackWadoURIHttpStatusCode;
    }

    public void setFallbackWadoURIHttpStatusCode(Integer fallbackWadoURIHttpStatusCode) {
        this.fallbackWadoURIHttpStatusCode = fallbackWadoURIHttpStatusCode;
    }

    public int fallbackWadoURIHttpStatusCode() {
        return fallbackWadoURIHttpStatusCode != null
                ? fallbackWadoURIHttpStatusCode
                : getArchiveDeviceExtension().getFallbackWadoURIHttpStatusCode();
    }
    public String getExternalRetrieveAEDestination() {
        return externalRetrieveAEDestination;
    }

    public void setExternalRetrieveAEDestination(String externalRetrieveAEDestination) {
        this.externalRetrieveAEDestination = externalRetrieveAEDestination;
    }

    public String externalRetrieveAEDestination() {
        return externalRetrieveAEDestination != null
                ? externalRetrieveAEDestination
                : getArchiveDeviceExtension().getExternalRetrieveAEDestination();
    }

    public String getAlternativeCMoveSCP() {
        return alternativeCMoveSCP;
    }

    public void setAlternativeCMoveSCP(String alternativeCMoveSCP) {
        this.alternativeCMoveSCP = alternativeCMoveSCP;
    }

    public String alternativeCMoveSCP() {
        return alternativeCMoveSCP != null
                ? alternativeCMoveSCP
                : getArchiveDeviceExtension().getAlternativeCMoveSCP();
    }

    public String fallbackCMoveSCPStudyOlderThan() {
        return fallbackCMoveSCPStudyOlderThan != null
                ? fallbackCMoveSCPStudyOlderThan
                : getArchiveDeviceExtension().getFallbackCMoveSCPStudyOlderThan();
    }

    public Integer getQueryMaxNumberOfResults() {
        return queryMaxNumberOfResults;
    }

    public void setQueryMaxNumberOfResults(Integer queryMaxNumberOfResults) {
        this.queryMaxNumberOfResults = queryMaxNumberOfResults;
    }

    public int queryMaxNumberOfResults() {
        return queryMaxNumberOfResults != null
                ? queryMaxNumberOfResults
                : getArchiveDeviceExtension().getQueryMaxNumberOfResults();
    }

    public Integer getQidoMaxNumberOfResults() {
        return qidoMaxNumberOfResults;
    }

    public void setQidoMaxNumberOfResults(Integer qidoMaxNumberOfResults) {
        this.qidoMaxNumberOfResults = qidoMaxNumberOfResults;
    }

    public int qidoMaxNumberOfResults() {
        return qidoMaxNumberOfResults != null
                ? qidoMaxNumberOfResults
                : getArchiveDeviceExtension().getQidoMaxNumberOfResults();
    }

    public SPSStatus[] getHideSPSWithStatusFromMWL() {
        return hideSPSWithStatusFromMWL;
    }

    public void setHideSPSWithStatusFromMWL(SPSStatus[] hideSPSWithStatusFromMWL) {
        this.hideSPSWithStatusFromMWL = hideSPSWithStatusFromMWL;
    }

    public SPSStatus[] hideSPSWithStatusFromMWL() {
        return hideSPSWithStatusFromMWL.length > 0
                ? hideSPSWithStatusFromMWL
                : getArchiveDeviceExtension().getHideSPSWithStatusFrom();
    }

    public String getFallbackCMoveSCPStudyOlderThan() {
        return fallbackCMoveSCPStudyOlderThan;
    }

    public void setFallbackCMoveSCPStudyOlderThan(String fallbackCMoveSCPStudyOlderThan) {
        this.fallbackCMoveSCPStudyOlderThan = fallbackCMoveSCPStudyOlderThan;
    }

    public String getStorePermissionServiceURL() {
        return storePermissionServiceURL;
    }

    public void setStorePermissionServiceURL(String storePermissionServiceURL) {
        this.storePermissionServiceURL = storePermissionServiceURL;
    }

    public String storePermissionServiceURL() {
        return storePermissionServiceURL != null
                ? storePermissionServiceURL
                : getArchiveDeviceExtension().getStorePermissionServiceURL();
    }

    public Pattern getStorePermissionServiceResponsePattern() {
        return storePermissionServiceResponsePattern;
    }

    public void setStorePermissionServiceResponsePattern(Pattern storePermissionServiceResponsePattern) {
        this.storePermissionServiceResponsePattern = storePermissionServiceResponsePattern;
    }

    public Pattern storePermissionServiceResponsePattern() {
        return storePermissionServiceResponsePattern != null
                ? storePermissionServiceResponsePattern
                : getArchiveDeviceExtension().getStorePermissionServiceResponsePattern();
    }

    public String getStorePermissionServiceResponse() {
        return storePermissionServiceResponse;
    }

    public void setStorePermissionServiceResponse(String storePermissionServiceResponse) {
        this.storePermissionServiceResponse = storePermissionServiceResponse;
    }

    public String storePermissionServiceResponse() {
        return storePermissionServiceResponse != null
                ? storePermissionServiceResponse
                : getArchiveDeviceExtension().getStorePermissionServiceResponse();
    }

    public Pattern getStorePermissionServiceExpirationDatePattern() {
        return storePermissionServiceExpirationDatePattern;
    }

    public void setStorePermissionServiceExpirationDatePattern(Pattern storePermissionServiceExpirationDatePattern) {
        this.storePermissionServiceExpirationDatePattern = storePermissionServiceExpirationDatePattern;
    }

    public Pattern storePermissionServiceExpirationDatePattern() {
        return storePermissionServiceExpirationDatePattern != null
                ? storePermissionServiceExpirationDatePattern
                : getArchiveDeviceExtension().getStorePermissionServiceExpirationDatePattern();
    }

    public Pattern getStorePermissionServiceErrorCommentPattern() {
        return storePermissionServiceErrorCommentPattern;
    }

    public void setStorePermissionServiceErrorCommentPattern(Pattern storePermissionServiceErrorCommentPattern) {
        this.storePermissionServiceErrorCommentPattern = storePermissionServiceErrorCommentPattern;
    }

    public Pattern storePermissionServiceErrorCommentPattern() {
        return storePermissionServiceErrorCommentPattern != null
                ? storePermissionServiceErrorCommentPattern
                : getArchiveDeviceExtension().getStorePermissionServiceErrorCommentPattern();
    }

    public Pattern getStorePermissionServiceErrorCodePattern() {
        return storePermissionServiceErrorCodePattern;
    }

    public void setStorePermissionServiceErrorCodePattern(Pattern storePermissionServiceErrorCodePattern) {
        this.storePermissionServiceErrorCodePattern = storePermissionServiceErrorCodePattern;
    }

    public Pattern storePermissionServiceErrorCodePattern() {
        return storePermissionServiceErrorCodePattern != null
                ? storePermissionServiceErrorCodePattern
                : getArchiveDeviceExtension().getStorePermissionServiceErrorCodePattern();
    }

    public QueryRetrieveView getQueryRetrieveView() {
        return getArchiveDeviceExtension().getQueryRetrieveViewNotNull(getQueryRetrieveViewID());
    }

    public AllowRejectionForDataRetentionPolicyExpired getAllowRejectionForDataRetentionPolicyExpired() {
        return allowRejectionForDataRetentionPolicyExpired;
    }

    public void setAllowRejectionForDataRetentionPolicyExpired(AllowRejectionForDataRetentionPolicyExpired allowRejectionForDataRetentionPolicyExpired) {
        this.allowRejectionForDataRetentionPolicyExpired = allowRejectionForDataRetentionPolicyExpired;
    }

    public AllowRejectionForDataRetentionPolicyExpired allowRejectionForDataRetentionPolicyExpired() {
        return allowRejectionForDataRetentionPolicyExpired != null
                ? allowRejectionForDataRetentionPolicyExpired
                : getArchiveDeviceExtension().getAllowRejectionForDataRetentionPolicyExpired();
    }

    public String[] getAcceptedMoveDestinations() {
        return acceptedMoveDestinations.toArray(new String[acceptedMoveDestinations.size()]);
    }

    public void setAcceptedMoveDestinations(String... aets) {
        acceptedMoveDestinations.clear();
        for (String name : aets)
            acceptedMoveDestinations.add(name);
    }

    public boolean isAcceptedMoveDestination(String aet) {
        return acceptedMoveDestinations.isEmpty() || acceptedMoveDestinations.contains(aet);
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

    public void removePrefetchRule(ExportPriorsRule rule) {
        exportPriorsRules.remove(rule);
    }

    public void clearPrefetchRules() {
        exportPriorsRules.clear();
    }

    public void addPrefetchRule(ExportPriorsRule rule) {
        exportPriorsRules.add(rule);
    }

    public Collection<ExportPriorsRule> getExportPriorsRules() {
        return exportPriorsRules;
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

    public void removeStudyRetentionPolicies(StudyRetentionPolicy policy) {
        studyRetentionPolicies.remove(policy);
    }

    public void clearStudyRetentionPolicy() {
        studyRetentionPolicies.clear();
    }

    public void addStudyRetentionPolicy(StudyRetentionPolicy policy) {
        studyRetentionPolicies.add(policy);
    }

    public Collection<StudyRetentionPolicy> getStudyRetentionPolicies() {
        return studyRetentionPolicies;
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

    public AllowDeleteStudyPermanently getAllowDeleteStudyPermanently() {
        return allowDeleteStudyPermanently;
    }

    public void setAllowDeleteStudyPermanently(AllowDeleteStudyPermanently allowDeleteStudyPermanently) {
        this.allowDeleteStudyPermanently = allowDeleteStudyPermanently;
    }

    public AllowDeleteStudyPermanently allowDeleteStudy() {
        return allowDeleteStudyPermanently != null
                ? allowDeleteStudyPermanently
                : getArchiveDeviceExtension().getAllowDeleteStudyPermanently();
    }

    public AllowDeletePatient getAllowDeletePatient() {
        return allowDeletePatient;
    }

    public void setAllowDeletePatient(AllowDeletePatient allowDeletePatient) {
        this.allowDeletePatient = allowDeletePatient;
    }

    public AllowDeletePatient allowDeletePatient() {
        return allowDeletePatient != null
                ? allowDeletePatient
                : getArchiveDeviceExtension().getAllowDeletePatient();
    }

    public String[] getRetrieveAETitles() {
        return retrieveAETitles;
    }

    public void setRetrieveAETitles(String... retrieveAETitles) {
        this.retrieveAETitles = retrieveAETitles;
    }

    public String[] retrieveAETitles() {
        return retrieveAETitles.length > 0 ? retrieveAETitles : getArchiveDeviceExtension().getRetrieveAETitles();
    }

    public String[] getReturnRetrieveAETitles() {
        return returnRetrieveAETitles;
    }

    public void setReturnRetrieveAETitles(String[] returnRetrieveAETitles) {
        this.returnRetrieveAETitles = returnRetrieveAETitles;
    }

    public String[] returnRetrieveAETitles() {
        return returnRetrieveAETitles.length > 0
                ? returnRetrieveAETitles
                : getArchiveDeviceExtension().getReturnRetrieveAETitles();
    }

    public String getHL7PSUSendingApplication() {
        return hl7PSUSendingApplication;
    }

    public void setHL7PSUSendingApplication(String hl7PSUSendingApplication) {
        this.hl7PSUSendingApplication = hl7PSUSendingApplication;
    }

    public String hl7PSUSendingApplication() {
        return hl7PSUSendingApplication != null
                ? hl7PSUSendingApplication
                : getArchiveDeviceExtension().getHL7PSUSendingApplication();
    }

    public String[] getHL7PSUReceivingApplications() {
        return hl7PSUReceivingApplications;
    }

    public void setHL7PSUReceivingApplications(String[] hl7PSUReceivingApplications) {
        this.hl7PSUReceivingApplications = hl7PSUReceivingApplications;
    }

    public String[] hl7PSUReceivingApplications() {
        return hl7PSUReceivingApplications.length > 0
                ? hl7PSUReceivingApplications
                : getArchiveDeviceExtension().getHL7PSUReceivingApplications();
    }

    public Duration getHL7PSUDelay() {
        return hl7PSUDelay;
    }

    public void setHL7PSUDelay(Duration hl7PSUDelay) {
        this.hl7PSUDelay = hl7PSUDelay;
    }

    public Duration hl7PSUDelay() {
        return hl7PSUDelay != null
                ? hl7PSUDelay
                : getArchiveDeviceExtension().getHL7PSUDelay();
    }

    public Duration getHL7PSUTimeout() {
        return hl7PSUTimeout;
    }

    public void setHL7PSUTimeout(Duration hl7PSUTimeout) {
        this.hl7PSUTimeout = hl7PSUTimeout;
    }

    public Duration hl7PSUTimeout() {
        return hl7PSUTimeout != null
                ? hl7PSUTimeout
                : getArchiveDeviceExtension().getHL7PSUTimeout();
    }

    public Boolean getHL7PSUOnTimeout() {
        return hl7PSUOnTimeout;
    }

    public void setHL7PSUOnTimeout(Boolean hl7PSUOnTimeout) {
        this.hl7PSUOnTimeout = hl7PSUOnTimeout;
    }

    public boolean hl7PSUOnTimeout() {
        return hl7PSUOnTimeout != null
                ? hl7PSUOnTimeout
                : getArchiveDeviceExtension().isHL7PSUOnTimeout();
    }

    public Boolean getHL7PSUMWL() {
        return hl7PSUMWL;
    }

    public void setHL7PSUMWL(Boolean hl7PSUMWL) {
        this.hl7PSUMWL = hl7PSUMWL;
    }

    public boolean hl7PSUMWL() {
        return hl7PSUMWL != null
                ? hl7PSUMWL
                : getArchiveDeviceExtension().isHL7PSUMWL();
    }

    public Boolean getHl7PSUForRequestedProcedure() {
        return hl7PSUForRequestedProcedure;
    }

    public void setHl7PSUForRequestedProcedure(Boolean hl7PSUForRequestedProcedure) {
        this.hl7PSUForRequestedProcedure = hl7PSUForRequestedProcedure;
    }

    public boolean hl7PSUForRequestedProcedure() {
        return hl7PSUForRequestedProcedure != null
                ? hl7PSUForRequestedProcedure
                : getArchiveDeviceExtension().isHl7PSUForRequestedProcedure();
    }

    public Boolean getHl7PSUPIDPV1() {
        return hl7PSUPIDPV1;
    }

    public void setHl7PSUPIDPV1(Boolean hl7PSUPIDPV1) {
        this.hl7PSUPIDPV1 = hl7PSUPIDPV1;
    }

    public boolean hl7PSUPIDPV1() {
        return hl7PSUPIDPV1 != null
                ? hl7PSUPIDPV1
                : getArchiveDeviceExtension().isHl7PSUPIDPV1();
    }

    public String getHl7PSURequestedProcedureID() {
        return hl7PSURequestedProcedureID;
    }

    public void setHl7PSURequestedProcedureID(String hl7PSURequestedProcedureID) {
        this.hl7PSURequestedProcedureID = hl7PSURequestedProcedureID;
    }

    public String hl7PSURequestedProcedureID() {
        return hl7PSURequestedProcedureID != null
                ? hl7PSURequestedProcedureID
                : getArchiveDeviceExtension().getHl7PSURequestedProcedureID();
    }

    public String getHl7PSUAccessionNumber() {
        return hl7PSUAccessionNumber;
    }

    public void setHl7PSUAccessionNumber(String hl7PSUAccessionNumber) {
        this.hl7PSUAccessionNumber = hl7PSUAccessionNumber;
    }

    public String hl7PSUAccessionNumber() {
        return hl7PSUAccessionNumber != null
                ? hl7PSUAccessionNumber
                : getArchiveDeviceExtension().getHl7PSUAccessionNumber();
    }

    public String getHl7PSUFillerOrderNumber() {
        return hl7PSUFillerOrderNumber;
    }

    public void setHl7PSUFillerOrderNumber(String hl7PSUFillerOrderNumber) {
        this.hl7PSUFillerOrderNumber = hl7PSUFillerOrderNumber;
    }

    public String hl7PSUFillerOrderNumber() {
        return hl7PSUFillerOrderNumber != null
                ? hl7PSUFillerOrderNumber
                : getArchiveDeviceExtension().getHl7PSUFillerOrderNumber();
    }

    public String getHl7PSUPlacerOrderNumber() {
        return hl7PSUPlacerOrderNumber;
    }

    public void setHl7PSUPlacerOrderNumber(String hl7PSUPlacerOrderNumber) {
        this.hl7PSUPlacerOrderNumber = hl7PSUPlacerOrderNumber;
    }

    public String hl7PSUPlacerOrderNumber() {
        return hl7PSUPlacerOrderNumber != null
                ? hl7PSUPlacerOrderNumber
                : getArchiveDeviceExtension().getHl7PSUPlacerOrderNumber();
    }

    public boolean hl7PSUOnStudy() {
        return (hl7PSUSendingApplication() != null && hl7PSUReceivingApplications().length > 0 && hl7PSUDelay() != null)
                || (hl7PSUDelay() != null && hl7PSUMWL());
    }

    public boolean hl7PSUOnMPPS() {
        return (hl7PSUSendingApplication() != null && hl7PSUReceivingApplications().length > 0 && hl7PSUDelay() == null)
                || (hl7PSUDelay() == null && hl7PSUMWL());
    }

    public HL7PSUMessageType getHl7PSUMessageType() {
        return hl7PSUMessageType;
    }

    public void setHl7PSUMessageType(HL7PSUMessageType hl7PSUMessageType) {
        this.hl7PSUMessageType = hl7PSUMessageType;
    }

    public HL7PSUMessageType hl7PSUMessageType() {
        return hl7PSUMessageType != null
                ? hl7PSUMessageType : getArchiveDeviceExtension().getHl7PSUMessageType();
    }

    public Conditions getHl7PSUConditions() {
        return hl7PSUConditions;
    }

    public void setHl7PSUConditions(Conditions hl7PSUConditions) {
        this.hl7PSUConditions = hl7PSUConditions;
    }

    public Conditions hl7PSUConditions() {
        return !hl7PSUConditions.getMap().isEmpty()
                ? hl7PSUConditions : getArchiveDeviceExtension().getHl7PSUConditions();
    }

    public AcceptConflictingPatientID getAcceptConflictingPatientID() {
        return acceptConflictingPatientID;
    }

    public void setAcceptConflictingPatientID(AcceptConflictingPatientID acceptConflictingPatientID) {
        this.acceptConflictingPatientID = acceptConflictingPatientID;
    }

    public AcceptConflictingPatientID acceptConflictingPatientID() {
        return acceptConflictingPatientID != null
                ? acceptConflictingPatientID
                : getArchiveDeviceExtension().getAcceptConflictingPatientID();
    }

    public Attributes.UpdatePolicy getCopyMoveUpdatePolicy() {
        return copyMoveUpdatePolicy;
    }

    public void setCopyMoveUpdatePolicy(Attributes.UpdatePolicy copyMoveUpdatePolicy) {
        this.copyMoveUpdatePolicy = copyMoveUpdatePolicy;
    }

    public Attributes.UpdatePolicy copyMoveUpdatePolicy() {
        return copyMoveUpdatePolicy != null
                ? copyMoveUpdatePolicy
                : getArchiveDeviceExtension().getCopyMoveUpdatePolicy();
    }

    public Attributes.UpdatePolicy getLinkMWLEntryUpdatePolicy() {
        return linkMWLEntryUpdatePolicy;
    }

    public void setLinkMWLEntryUpdatePolicy(Attributes.UpdatePolicy linkMWLEntryUpdatePolicy) {
        this.linkMWLEntryUpdatePolicy = linkMWLEntryUpdatePolicy;
    }

    public Attributes.UpdatePolicy linkMWLEntryUpdatePolicy() {
        return linkMWLEntryUpdatePolicy != null
                ? linkMWLEntryUpdatePolicy
                : getArchiveDeviceExtension().getLinkMWLEntryUpdatePolicy();
    }

    public StorageVerificationPolicy getStorageVerificationPolicy() {
        return storageVerificationPolicy;
    }

    public void setStorageVerificationPolicy(StorageVerificationPolicy storageVerificationPolicy) {
        this.storageVerificationPolicy = storageVerificationPolicy;
    }

    public StorageVerificationPolicy storageVerificationPolicy() {
        return storageVerificationPolicy != null
                ? storageVerificationPolicy
                : getArchiveDeviceExtension().getStorageVerificationPolicy();
    }

    public Boolean getStorageVerificationUpdateLocationStatus() {
        return storageVerificationUpdateLocationStatus;
    }

    public void setStorageVerificationUpdateLocationStatus(Boolean storageVerificationUpdateLocationStatus) {
        this.storageVerificationUpdateLocationStatus = storageVerificationUpdateLocationStatus;
    }

    public boolean storageVerificationUpdateLocationStatus() {
        return storageVerificationUpdateLocationStatus != null
                ? storageVerificationUpdateLocationStatus
                : getArchiveDeviceExtension().isStorageVerificationUpdateLocationStatus();
    }

    public String[] getStorageVerificationStorageIDs() {
        return storageVerificationStorageIDs;
    }

    public void setStorageVerificationStorageIDs(String... storageVerificationStorageIDs) {
        this.storageVerificationStorageIDs = storageVerificationStorageIDs;
    }

    public String[] storageVerificationStorageIDs() {
        return storageVerificationStorageIDs.length > 0
                ? storageVerificationStorageIDs
                : getArchiveDeviceExtension().getStorageVerificationStorageIDs();
    }

    public Period getStorageVerificationInitialDelay() {
        return storageVerificationInitialDelay;
    }

    public void setStorageVerificationInitialDelay(Period storageVerificationInitialDelay) {
        this.storageVerificationInitialDelay = storageVerificationInitialDelay;
    }

    public Period storageVerificationInitialDelay() {
        ArchiveDeviceExtension arcdev = getArchiveDeviceExtension();
        return storageVerificationInitialDelay != null
                ? storageVerificationInitialDelay
                : arcdev.getStorageVerificationInitialDelay();
    }

    public Boolean getUpdateLocationStatusOnRetrieve() {
        return updateLocationStatusOnRetrieve;
    }

    public void setUpdateLocationStatusOnRetrieve(Boolean updateLocationStatusOnRetrieve) {
        this.updateLocationStatusOnRetrieve = updateLocationStatusOnRetrieve;
    }

    public boolean updateLocationStatusOnRetrieve() {
        return updateLocationStatusOnRetrieve != null
                ? updateLocationStatusOnRetrieve
                : getArchiveDeviceExtension().isUpdateLocationStatusOnRetrieve();
    }

    public Boolean getStorageVerificationOnRetrieve() {
        return storageVerificationOnRetrieve;
    }

    public void setStorageVerificationOnRetrieve(Boolean storageVerificationOnRetrieve) {
        this.storageVerificationOnRetrieve = storageVerificationOnRetrieve;
    }

    public boolean storageVerificationOnRetrieve() {
        return storageVerificationOnRetrieve != null
                ? storageVerificationOnRetrieve
                : getArchiveDeviceExtension().isStorageVerificationOnRetrieve();
    }

    public Boolean getRelationalQueryNegotiationLenient() {
        return relationalQueryNegotiationLenient;
    }

    public void setRelationalQueryNegotiationLenient(Boolean relationalQueryNegotiationLenient) {
        this.relationalQueryNegotiationLenient = relationalQueryNegotiationLenient;
    }

    public boolean relationalQueryNegotiationLenient() {
        return relationalQueryNegotiationLenient != null
                ? relationalQueryNegotiationLenient
                : getArchiveDeviceExtension().isRelationalQueryNegotiationLenient();
    }

    public Boolean getRelationalRetrieveNegotiationLenient() {
        return relationalRetrieveNegotiationLenient;
    }

    public void setRelationalRetrieveNegotiationLenient(Boolean relationalRetrieveNegotiationLenient) {
        this.relationalRetrieveNegotiationLenient = relationalRetrieveNegotiationLenient;
    }

    public boolean relationalRetrieveNegotiationLenient() {
        return relationalRetrieveNegotiationLenient != null
                ? relationalRetrieveNegotiationLenient
                : getArchiveDeviceExtension().isRelationalRetrieveNegotiationLenient();
    }

    public int[] getRejectConflictingPatientAttribute() {
        return rejectConflictingPatientAttribute;
    }

    public void setRejectConflictingPatientAttribute(int[] rejectConflictingPatientAttribute) {
        Arrays.sort(this.rejectConflictingPatientAttribute = rejectConflictingPatientAttribute);
    }

    public int[] rejectConflictingPatientAttribute() {
        return rejectConflictingPatientAttribute.length > 0
                ? rejectConflictingPatientAttribute
                : getArchiveDeviceExtension().getRejectConflictingPatientAttribute();
    }

    public Boolean getStowRetiredTransferSyntax() {
        return stowRetiredTransferSyntax;
    }

    public void setStowRetiredTransferSyntax(Boolean stowRetiredTransferSyntax) {
        this.stowRetiredTransferSyntax = stowRetiredTransferSyntax;
    }

    public boolean stowRetiredTransferSyntax() {
        return stowRetiredTransferSyntax != null
                ? stowRetiredTransferSyntax
                : getArchiveDeviceExtension().isStowRetiredTransferSyntax();
    }

    public Boolean getStowExcludeAPPMarkers() {
        return stowExcludeAPPMarkers;
    }

    public void setStowExcludeAPPMarkers(Boolean stowExcludeAPPMarkers) {
        this.stowExcludeAPPMarkers = stowExcludeAPPMarkers;
    }

    public boolean stowExcludeAPPMarkers() {
        return stowExcludeAPPMarkers != null
                ? stowExcludeAPPMarkers
                : getArchiveDeviceExtension().isStowExcludeAPPMarkers();
    }

    public Boolean getRestrictRetrieveSilently() {
        return restrictRetrieveSilently;
    }

    public void setRestrictRetrieveSilently(Boolean restrictRetrieveSilently) {
        this.restrictRetrieveSilently = restrictRetrieveSilently;
    }

    public boolean restrictRetrieveSilently() {
        return restrictRetrieveSilently != null
                ? restrictRetrieveSilently.booleanValue()
                : getArchiveDeviceExtension().isRestrictRetrieveSilently();
    }

    public MultipleStoreAssociations[] getMultipleStoreAssociations() {
        return multipleStoreAssociations;
    }

    public void setMultipleStoreAssociations(String[] ss) {
        multipleStoreAssociations = MultipleStoreAssociations.of(ss);
    }

    public int maxStoreAssociationsTo(String aet) {
        return MultipleStoreAssociations.maxTo(aet,
                multipleStoreAssociations,
                getArchiveDeviceExtension().getMultipleStoreAssociations());
    }

    public Boolean getRetrieveTaskWarningOnNoMatch() {
        return retrieveTaskWarningOnNoMatch;
    }

    public void setRetrieveTaskWarningOnNoMatch(Boolean retrieveTaskWarningOnNoMatch) {
        this.retrieveTaskWarningOnNoMatch = retrieveTaskWarningOnNoMatch;
    }

    public boolean retrieveTaskWarningOnNoMatch() {
        return retrieveTaskWarningOnNoMatch != null
                ? retrieveTaskWarningOnNoMatch
                : getArchiveDeviceExtension().isRetrieveTaskWarningOnNoMatch();
    }

    public Boolean getRetrieveTaskWarningOnWarnings() {
        return retrieveTaskWarningOnWarnings;
    }

    public void setRetrieveTaskWarningOnWarnings(Boolean retrieveTaskWarningOnWarnings) {
        this.retrieveTaskWarningOnWarnings = retrieveTaskWarningOnWarnings;
    }

    public boolean retrieveTaskWarningOnWarnings() {
        return retrieveTaskWarningOnWarnings != null
                ? retrieveTaskWarningOnWarnings
                : getArchiveDeviceExtension().isRetrieveTaskWarningOnWarnings();
    }

    public Boolean getStowQuicktime2MP4() {
        return stowQuicktime2MP4;
    }

    public void setStowQuicktime2MP4(Boolean stowQuicktime2MP4) {
        this.stowQuicktime2MP4 = stowQuicktime2MP4;
    }

    public boolean stowQuicktime2MP4() {
        return stowQuicktime2MP4 != null
                ? stowQuicktime2MP4
                : getArchiveDeviceExtension().isStowQuicktime2MP4();
    }

    @Override
    public void reconfigure(AEExtension from) {
        ArchiveAEExtension aeExt = (ArchiveAEExtension) from;
        defaultCharacterSet = aeExt.defaultCharacterSet;
        upsWorklistLabel = aeExt.upsWorklistLabel;
        upsEventSCUs = aeExt.upsEventSCUs;
        upsEventSCUKeepAlive = aeExt.upsEventSCUKeepAlive;
        objectStorageIDs = aeExt.objectStorageIDs;
        objectStorageCount = aeExt.objectStorageCount;
        metadataStorageIDs = aeExt.metadataStorageIDs;
        bulkDataDescriptorID = aeExt.bulkDataDescriptorID;
        seriesMetadataDelay = aeExt.seriesMetadataDelay;
        purgeInstanceRecordsDelay = aeExt.purgeInstanceRecordsDelay;
        storeAccessControlID = aeExt.storeAccessControlID;
        accessControlIDs = aeExt.accessControlIDs;
        overwritePolicy = aeExt.overwritePolicy;
        recordAttributeModification = aeExt.recordAttributeModification;
        bulkDataSpoolDirectory = aeExt.bulkDataSpoolDirectory;
        queryRetrieveViewID = aeExt.queryRetrieveViewID;
        validateCallingAEHostname = aeExt.validateCallingAEHostname;
        personNameComponentOrderInsensitiveMatching = aeExt.personNameComponentOrderInsensitiveMatching;
        sendPendingCGet = aeExt.sendPendingCGet;
        sendPendingCMoveInterval = aeExt.sendPendingCMoveInterval;
        wadoThumbnailViewPort = aeExt.wadoThumbnailViewPort;
        wadoZIPEntryNameFormat = aeExt.wadoZIPEntryNameFormat;
        wadoSR2HtmlTemplateURI = aeExt.wadoSR2HtmlTemplateURI;
        wadoSR2TextTemplateURI = aeExt.wadoSR2TextTemplateURI;
        wadoCDA2HtmlTemplateURI = aeExt.wadoCDA2HtmlTemplateURI;
        mppsForwardDestinations = aeExt.mppsForwardDestinations;
        ianDestinations = aeExt.ianDestinations;
        ianDelay = aeExt.ianDelay;
        ianTimeout = aeExt.ianTimeout;
        ianOnTimeout = aeExt.ianOnTimeout;
        spanningCFindSCP = aeExt.spanningCFindSCP;
        spanningCFindSCPRetrieveAETitles = aeExt.spanningCFindSCPRetrieveAETitles;
        spanningCFindSCPPolicy = aeExt.spanningCFindSCPPolicy;
        fallbackCMoveSCP = aeExt.fallbackCMoveSCP;
        fallbackCMoveSCPDestination = aeExt.fallbackCMoveSCPDestination;
        fallbackCMoveSCPCallingAET = aeExt.fallbackCMoveSCPCallingAET;
        fallbackCMoveSCPLeadingCFindSCP = aeExt.fallbackCMoveSCPLeadingCFindSCP;
        fallbackCMoveSCPRetries = aeExt.fallbackCMoveSCPRetries;
        fallbackWadoURIWebApplication = aeExt.fallbackWadoURIWebApplication;
        fallbackWadoURIHttpStatusCode = aeExt.fallbackWadoURIHttpStatusCode;
        externalRetrieveAEDestination = aeExt.externalRetrieveAEDestination;
        alternativeCMoveSCP = aeExt.alternativeCMoveSCP;
        queryMaxNumberOfResults = aeExt.queryMaxNumberOfResults;
        qidoMaxNumberOfResults = aeExt.qidoMaxNumberOfResults;
        hideSPSWithStatusFromMWL = aeExt.hideSPSWithStatusFromMWL;
        fallbackCMoveSCPStudyOlderThan = aeExt.fallbackCMoveSCPStudyOlderThan;
        storePermissionServiceURL = aeExt.storePermissionServiceURL;
        storePermissionServiceResponse = aeExt.storePermissionServiceResponse;
        storePermissionServiceResponsePattern = aeExt.storePermissionServiceResponsePattern;
        storePermissionServiceExpirationDatePattern = aeExt.storePermissionServiceExpirationDatePattern;
        storePermissionServiceErrorCommentPattern = aeExt.storePermissionServiceErrorCommentPattern;
        storePermissionServiceErrorCodePattern = aeExt.storePermissionServiceErrorCodePattern;
        allowRejectionForDataRetentionPolicyExpired = aeExt.allowRejectionForDataRetentionPolicyExpired;
        acceptMissingPatientID = aeExt.acceptMissingPatientID;
        allowDeleteStudyPermanently = aeExt.allowDeleteStudyPermanently;
        allowDeletePatient = aeExt.allowDeletePatient;
        acceptConflictingPatientID = aeExt.acceptConflictingPatientID;
        copyMoveUpdatePolicy = aeExt.copyMoveUpdatePolicy;
        linkMWLEntryUpdatePolicy = aeExt.linkMWLEntryUpdatePolicy;
        retrieveAETitles = aeExt.retrieveAETitles;
        returnRetrieveAETitles = aeExt.returnRetrieveAETitles;
        hl7PSUSendingApplication = aeExt.hl7PSUSendingApplication;
        hl7PSUReceivingApplications = aeExt.hl7PSUReceivingApplications;
        hl7PSUDelay = aeExt.hl7PSUDelay;
        hl7PSUTimeout = aeExt.hl7PSUTimeout;
        hl7PSUOnTimeout = aeExt.hl7PSUOnTimeout;
        hl7PSUMWL = aeExt.hl7PSUMWL;
        hl7PSUForRequestedProcedure = aeExt.hl7PSUForRequestedProcedure;
        hl7PSUPIDPV1 = aeExt.hl7PSUPIDPV1;
        hl7PSURequestedProcedureID = aeExt.hl7PSURequestedProcedureID;
        hl7PSUAccessionNumber = aeExt.hl7PSUAccessionNumber;
        hl7PSUFillerOrderNumber = aeExt.hl7PSUFillerOrderNumber;
        hl7PSUPlacerOrderNumber = aeExt.hl7PSUPlacerOrderNumber;
        hl7PSUMessageType = aeExt.hl7PSUMessageType;
        hl7PSUConditions = aeExt.hl7PSUConditions;
        storageVerificationPolicy = aeExt.storageVerificationPolicy;
        storageVerificationUpdateLocationStatus = aeExt.storageVerificationUpdateLocationStatus;
        storageVerificationStorageIDs = aeExt.storageVerificationStorageIDs;
        storageVerificationInitialDelay = aeExt.storageVerificationInitialDelay;
        updateLocationStatusOnRetrieve = aeExt.updateLocationStatusOnRetrieve;
        storageVerificationOnRetrieve = aeExt.storageVerificationOnRetrieve;
        relationalQueryNegotiationLenient = aeExt.relationalQueryNegotiationLenient;
        relationalRetrieveNegotiationLenient = aeExt.relationalRetrieveNegotiationLenient;
        rejectConflictingPatientAttribute = aeExt.rejectConflictingPatientAttribute;
        stowRetiredTransferSyntax = aeExt.stowRetiredTransferSyntax;
        stowExcludeAPPMarkers = aeExt.stowExcludeAPPMarkers;
        restrictRetrieveSilently = aeExt.restrictRetrieveSilently;
        retrieveTaskWarningOnNoMatch = aeExt.retrieveTaskWarningOnNoMatch;
        retrieveTaskWarningOnWarnings = aeExt.retrieveTaskWarningOnWarnings;
        stowQuicktime2MP4 = aeExt.stowQuicktime2MP4;
        multipleStoreAssociations = aeExt.multipleStoreAssociations;
        acceptedMoveDestinations.clear();
        acceptedMoveDestinations.addAll(aeExt.acceptedMoveDestinations);
        upsOnStoreList.clear();
        upsOnStoreList.addAll(aeExt.upsOnStoreList);
        exportRules.clear();
        exportRules.addAll(aeExt.exportRules);
        exportPriorsRules.clear();
        exportPriorsRules.addAll(aeExt.exportPriorsRules);
        rsForwardRules.clear();
        rsForwardRules.addAll(aeExt.rsForwardRules);
        compressionRules.clear();
        compressionRules.addAll(aeExt.compressionRules);
        studyRetentionPolicies.clear();
        studyRetentionPolicies.addAll(aeExt.studyRetentionPolicies);
        attributeCoercions.clear();
        attributeCoercions.addAll(aeExt.attributeCoercions);
        storeAccessControlIDRules.clear();
        storeAccessControlIDRules.addAll(aeExt.storeAccessControlIDRules);
    }

    public ArchiveDeviceExtension getArchiveDeviceExtension() {
        return ae.getDevice().getDeviceExtension(ArchiveDeviceExtension.class);
    }

    public Stream<ExportRule> exportRules() {
        return Utils.concatCopyStream(exportRules,
                getArchiveDeviceExtension().getExportRules(),
                ExportRule.EMPTY);
    }

    public Stream<UPSOnStore> upsOnStoreStream() {
        return Utils.concatCopyStream(upsOnStoreList,
                getArchiveDeviceExtension().listUPSOnStore(),
                UPSOnStore.EMPTY);
    }

    public Stream<ExportPriorsRule> prefetchRules() {
        return Utils.concatCopyStream(exportPriorsRules,
                getArchiveDeviceExtension().getExportPriorsRules(),
                ExportPriorsRule.EMPTY);
    }

    public Stream<RSForwardRule> rsForwardRules() {
        return Utils.concatCopyStream(rsForwardRules,
                getArchiveDeviceExtension().getRSForwardRules(),
                RSForwardRule.EMPTY);
    }

    public Stream<ArchiveCompressionRule> compressionRules() {
        return Utils.concatCopyStream(compressionRules,
                getArchiveDeviceExtension().getCompressionRules(),
                ArchiveCompressionRule.EMPTY)
                .sorted(Comparator.comparingInt(ArchiveCompressionRule::getPriority).reversed());
    }

    public Stream<ArchiveAttributeCoercion> attributeCoercions() {
        return Utils.concatCopyStream(attributeCoercions,
                getArchiveDeviceExtension().getAttributeCoercions(),
                ArchiveAttributeCoercion.EMPTY)
                .sorted(Comparator.comparingInt(ArchiveAttributeCoercion::getPriority).reversed());
    }

    public ArchiveAttributeCoercion findAttributeCoercion(Dimse dimse, TransferCapability.Role role, String sopClass,
            String sendingHost, String sendingAET, String receivingHost, String receivingAET, Attributes attrs) {
        return attributeCoercions()
                .filter(coercion -> coercion.match(role, dimse, sopClass,
                        sendingHost, sendingAET, receivingHost, receivingAET, attrs))
                .findFirst()
                .orElse(null);
    }

    public Stream<StudyRetentionPolicy> studyRetentionPolicies() {
        return Utils.concatCopyStream(studyRetentionPolicies,
                getArchiveDeviceExtension().getStudyRetentionPolicies(),
                StudyRetentionPolicy.EMPTY)
                .sorted(Comparator.comparingInt(StudyRetentionPolicy::getPriority).reversed());
    }

    public StudyRetentionPolicy findStudyRetentionPolicy(String sendingHost, String sendingAET,
            String receivingHost, String receivingAET, Attributes attrs) {
        return studyRetentionPolicies()
                .filter(policy -> policy.match(sendingHost, sendingAET, receivingHost, receivingAET, attrs))
                .findFirst()
                .orElse(null);
    }

    public Stream<StoreAccessControlIDRule> storeAccessControlIDRules() {
        return Utils.concatCopyStream(storeAccessControlIDRules,
                getArchiveDeviceExtension().getStoreAccessControlIDRules(),
                StoreAccessControlIDRule.EMPTY)
                .sorted(Comparator.comparingInt(StoreAccessControlIDRule::getPriority).reversed());
    }

}
