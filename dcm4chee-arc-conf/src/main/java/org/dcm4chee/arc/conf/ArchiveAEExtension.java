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

import org.dcm4che3.data.Attributes;
import org.dcm4che3.net.AEExtension;
import org.dcm4che3.net.Dimse;
import org.dcm4che3.net.TransferCapability;
import org.dcm4che3.util.StringUtils;

import java.io.File;
import java.util.*;
import java.util.regex.Pattern;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jul 2015
 */
public class ArchiveAEExtension extends AEExtension {
    private static final String JBOSS_SERVER_TEMP_DIR = "${jboss.server.temp.dir}";
    private String defaultCharacterSet;
    private String storageID;
    private String metadataStorageID;
    private Duration seriesMetadataDelay;
    private Duration purgeInstanceRecordsDelay;
    private String storeAccessControlID;
    private String[] accessControlIDs = {};
    private OverwritePolicy overwritePolicy;
    private String bulkDataSpoolDirectory;
    private String queryRetrieveViewID;
    private Boolean validateCallingAEHostname;
    private Boolean personNameComponentOrderInsensitiveMatching;
    private Boolean sendPendingCGet;
    private Duration sendPendingCMoveInterval;
    private String wadoSR2HtmlTemplateURI;
    private String wadoSR2TextTemplateURI;
    private String[] mppsForwardDestinations = {};
    private String[] ianDestinations = {};
    private Duration ianDelay;
    private Duration ianTimeout;
    private Boolean ianOnTimeout;
    private int fallbackCMoveSCPRetries;
    private String fallbackCMoveSCP;
    private String fallbackCMoveSCPDestination;
    private String fallbackCMoveSCPLeadingCFindSCP;
    private String fallbackCMoveSCPStudyOlderThan;
    private String externalRetrieveAEDestination;
    private String alternativeCMoveSCP;
    private int qidoMaxNumberOfResults;
    private SPSStatus[] hideSPSWithStatusFromMWL = {};
    private String storePermissionServiceURL;
    private Pattern storePermissionServiceResponsePattern;
    private Pattern storePermissionServiceExpirationDatePattern;
    private Pattern storePermissionServiceErrorCommentPattern;
    private Pattern storePermissionServiceErrorCodePattern;
    private AllowRejectionForDataRetentionPolicyExpired allowRejectionForDataRetentionPolicyExpired;
    private AcceptMissingPatientID acceptMissingPatientID;
    private AllowDeleteStudyPermanently allowDeleteStudyPermanently;
    private AcceptConflictingPatientID acceptConflictingPatientID;
    private String[] retrieveAETitles = {};
    private String hl7PSUSendingApplication;
    private String[] hl7PSUReceivingApplications = {};
    private Duration hl7PSUDelay;
    private Duration hl7PSUTimeout;
    private Boolean hl7PSUOnTimeout;
    private Boolean hl7PSUMWL;
    private final LinkedHashSet<String> acceptedMoveDestinations = new LinkedHashSet<>();
    private final LinkedHashSet<String> acceptedUserRoles = new LinkedHashSet<>();
    private final ArrayList<ExportRule> exportRules = new ArrayList<>();
    private final ArrayList<RSForwardRule> rsForwardRules = new ArrayList<>();
    private final ArrayList<ArchiveCompressionRule> compressionRules = new ArrayList<>();
    private final ArrayList<ArchiveAttributeCoercion> attributeCoercions = new ArrayList<>();
    private final ArrayList<StudyRetentionPolicy> studyRetentionPolicies = new ArrayList<>();
    private final ArrayList<StoreAccessControlIDRule> storeAccessControlIDRules = new ArrayList<>();

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

    public String getStorageID() {
        return storageID;
    }

    public void setStorageID(String storageID) {
        this.storageID = storageID;
    }

    public String storageID() {
        return storageID != null
                ? storageID
                : getArchiveDeviceExtension().getStorageID();
    }

    public String getMetadataStorageID() {
        return metadataStorageID;
    }

    public void setMetadataStorageID(String metadataStorageID) {
        this.metadataStorageID = metadataStorageID;
    }

    public String metadataStorageID() {
        return metadataStorageID != null
                ? metadataStorageID
                : getArchiveDeviceExtension().getMetadataStorageID();
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
        return purgeInstanceRecordsDelay != null
                ? purgeInstanceRecordsDelay
                : getArchiveDeviceExtension().getPurgeInstanceRecordsDelay();
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

    public OverwritePolicy getOverwritePolicy() {
        return overwritePolicy;
    }

    public void setOverwritePolicy(OverwritePolicy overwritePolicy) {
        this.overwritePolicy = overwritePolicy;
    }

    public OverwritePolicy overwritePolicy() {
        return overwritePolicy != null
                ? overwritePolicy
                : StringUtils.maskNull(getArchiveDeviceExtension().getOverwritePolicy(), OverwritePolicy.NEVER);
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
                : StringUtils.maskNull(getArchiveDeviceExtension().getAcceptMissingPatientID(), AcceptMissingPatientID.CREATE);
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
        return new File(StringUtils.replaceSystemProperties(
                StringUtils.maskNull(bulkDataSpoolDirectory(), JBOSS_SERVER_TEMP_DIR)));
    }

    public String getQueryRetrieveViewID() {
        return queryRetrieveViewID;
    }

    public void setQueryRetrieveViewID(String queryRetrieveViewID) {
        this.queryRetrieveViewID = queryRetrieveViewID;
    }

    public String queryRetrieveViewID() {
        return queryRetrieveViewID != null
                ? queryRetrieveViewID
                : getArchiveDeviceExtension().getQueryRetrieveViewID();
    }

    public Boolean getValidateCallingAEHostname() {
        return validateCallingAEHostname;
    }

    public void setValidateCallingAEHostname(Boolean validateCallingAEHostname) {
        this.validateCallingAEHostname = validateCallingAEHostname;
    }

    public boolean validateCallingAEHostname() {
        return validateCallingAEHostname != null
                ? validateCallingAEHostname.booleanValue()
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
                ? personNameComponentOrderInsensitiveMatching.booleanValue()
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
                ? sendPendingCGet.booleanValue()
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
                ? ianOnTimeout.booleanValue()
                : getArchiveDeviceExtension().isIanOnTimeout();
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

    public void setFallbackCMoveSCPRetries(int fallbackCMoveSCPRetries) {
        this.fallbackCMoveSCPRetries = fallbackCMoveSCPRetries;
    }

    public int getFallbackCMoveSCPRetries() {
        return fallbackCMoveSCPRetries;
    }

    public int fallbackCMoveSCPRetries() {
        return fallbackCMoveSCPRetries > 0
                ? fallbackCMoveSCPRetries
                : getArchiveDeviceExtension().getFallbackCMoveSCPRetries();
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

    public int getQidoMaxNumberOfResults() {
        return qidoMaxNumberOfResults;
    }

    public void setQidoMaxNumberOfResults(int qidoMaxNumberOfResults) {
        this.qidoMaxNumberOfResults = qidoMaxNumberOfResults;
    }

    public int qidoMaxNumberOfResults() {
        return qidoMaxNumberOfResults > 0
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
        return getArchiveDeviceExtension().getQueryRetrieveViewNotNull(queryRetrieveViewID());
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
                : StringUtils.maskNull(getArchiveDeviceExtension().getAllowRejectionForDataRetentionPolicyExpired(),
                    AllowRejectionForDataRetentionPolicyExpired.STUDY_RETENTION_POLICY);
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

    public String[] getAcceptedUserRoles() {
        return acceptedUserRoles.toArray(
                new String[acceptedUserRoles.size()]);
    }

    public void setAcceptedUserRoles(String... roles) {
        acceptedUserRoles.clear();
        for (String name : roles)
            acceptedUserRoles.add(name);
    }

    public boolean isAcceptedUserRole(String... roles) {
        if (acceptedUserRoles.isEmpty())
            return true;

        for (String role : roles)
            if (acceptedUserRoles.contains(role))
                return true;

        return false;
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

    public void addCompressionRule(ArchiveAttributeCoercion coercion) {
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

    public ArrayList<StoreAccessControlIDRule> getStoreAccessControlIDRules() {
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
                : StringUtils.maskNull(getArchiveDeviceExtension().getAllowDeleteStudyPermanently(),
                AllowDeleteStudyPermanently.REJECTED);
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

    public String getHl7PSUSendingApplication() {
        return hl7PSUSendingApplication;
    }

    public void setHl7PSUSendingApplication(String hl7PSUSendingApplication) {
        this.hl7PSUSendingApplication = hl7PSUSendingApplication;
    }

    public String hl7PSUSendingApplication() {
        return hl7PSUSendingApplication != null
                ? hl7PSUSendingApplication
                : getArchiveDeviceExtension().getHl7PSUSendingApplication();
    }

    public String[] getHl7PSUReceivingApplications() {
        return hl7PSUReceivingApplications;
    }

    public void setHl7PSUReceivingApplications(String[] hl7PSUReceivingApplications) {
        this.hl7PSUReceivingApplications = hl7PSUReceivingApplications;
    }

    public String[] hl7PSUReceivingApplications() {
        return hl7PSUReceivingApplications.length > 0
                ? hl7PSUReceivingApplications
                : getArchiveDeviceExtension().getHl7PSUReceivingApplications();
    }

    public Duration getHl7PSUDelay() {
        return hl7PSUDelay;
    }

    public void setHl7PSUDelay(Duration hl7PSUDelay) {
        this.hl7PSUDelay = hl7PSUDelay;
    }

    public Duration hl7PSUDelay() {
        return hl7PSUDelay != null
                ? hl7PSUDelay
                : getArchiveDeviceExtension().getHl7PSUDelay();
    }

    public Duration getHl7PSUTimeout() {
        return hl7PSUTimeout;
    }

    public void setHl7PSUTimeout(Duration hl7PSUTimeout) {
        this.hl7PSUTimeout = hl7PSUTimeout;
    }

    public Duration hl7PSUTimeout() {
        return hl7PSUTimeout != null
                ? hl7PSUTimeout
                : getArchiveDeviceExtension().getHl7PSUTimeout();
    }

    public Boolean getHl7PSUOnTimeout() {
        return hl7PSUOnTimeout;
    }

    public void setHl7PSUOnTimeout(Boolean hl7PSUOnTimeout) {
        this.hl7PSUOnTimeout = hl7PSUOnTimeout;
    }

    public boolean hl7PSUOnTimeout() {
        return hl7PSUOnTimeout != null
                ? hl7PSUOnTimeout.booleanValue()
                : getArchiveDeviceExtension().isHl7PSUOnTimeout();
    }

    public Boolean getHl7PSUMWL() {
        return hl7PSUMWL;
    }

    public void setHl7PSUMWL(Boolean hl7PSUMWL) {
        this.hl7PSUMWL = hl7PSUMWL;
    }

    public boolean hl7PSUMWL() {
        return hl7PSUMWL != null
                ? hl7PSUMWL.booleanValue()
                : getArchiveDeviceExtension().isHl7PSUMWL();
    }

    public boolean hl7PSUOnStudy() {
        return (hl7PSUSendingApplication() != null && hl7PSUReceivingApplications().length > 0 && hl7PSUDelay() != null)
                || (hl7PSUDelay() != null && hl7PSUMWL());
    }

    public boolean hl7PSUOnMPPS() {
        return hl7PSUSendingApplication() != null && hl7PSUReceivingApplications().length > 0 && hl7PSUDelay() == null;
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
                : StringUtils.maskNull(getArchiveDeviceExtension().getAcceptConflictingPatientID(),
                AcceptConflictingPatientID.MERGED);
    }

    @Override
    public void reconfigure(AEExtension from) {
        ArchiveAEExtension aeExt = (ArchiveAEExtension) from;
        defaultCharacterSet = aeExt.defaultCharacterSet;
        storageID = aeExt.storageID;
        metadataStorageID = aeExt.metadataStorageID;
        seriesMetadataDelay = aeExt.seriesMetadataDelay;
        purgeInstanceRecordsDelay = aeExt.purgeInstanceRecordsDelay;
        storeAccessControlID = aeExt.storeAccessControlID;
        accessControlIDs = aeExt.accessControlIDs;
        overwritePolicy = aeExt.overwritePolicy;
        bulkDataSpoolDirectory = aeExt.bulkDataSpoolDirectory;
        queryRetrieveViewID = aeExt.queryRetrieveViewID;
        validateCallingAEHostname = aeExt.validateCallingAEHostname;
        personNameComponentOrderInsensitiveMatching = aeExt.personNameComponentOrderInsensitiveMatching;
        sendPendingCGet = aeExt.sendPendingCGet;
        sendPendingCMoveInterval = aeExt.sendPendingCMoveInterval;
        wadoSR2HtmlTemplateURI = aeExt.wadoSR2HtmlTemplateURI;
        wadoSR2TextTemplateURI = aeExt.wadoSR2TextTemplateURI;
        mppsForwardDestinations = aeExt.mppsForwardDestinations;
        ianDestinations = aeExt.ianDestinations;
        ianDelay = aeExt.ianDelay;
        ianTimeout = aeExt.ianTimeout;
        ianOnTimeout = aeExt.ianOnTimeout;
        fallbackCMoveSCP = aeExt.fallbackCMoveSCP;
        fallbackCMoveSCPDestination = aeExt.fallbackCMoveSCPDestination;
        fallbackCMoveSCPLeadingCFindSCP = aeExt.fallbackCMoveSCPLeadingCFindSCP;
        fallbackCMoveSCPRetries = aeExt.fallbackCMoveSCPRetries;
        externalRetrieveAEDestination = aeExt.externalRetrieveAEDestination;
        alternativeCMoveSCP = aeExt.alternativeCMoveSCP;
        qidoMaxNumberOfResults = aeExt.qidoMaxNumberOfResults;
        hideSPSWithStatusFromMWL = aeExt.hideSPSWithStatusFromMWL;
        fallbackCMoveSCPStudyOlderThan = aeExt.fallbackCMoveSCPStudyOlderThan;
        storePermissionServiceURL = aeExt.storePermissionServiceURL;
        storePermissionServiceResponsePattern = aeExt.storePermissionServiceResponsePattern;
        storePermissionServiceExpirationDatePattern = aeExt.storePermissionServiceExpirationDatePattern;
        storePermissionServiceErrorCommentPattern = aeExt.storePermissionServiceErrorCommentPattern;
        storePermissionServiceErrorCodePattern = aeExt.storePermissionServiceErrorCodePattern;
        allowRejectionForDataRetentionPolicyExpired = aeExt.allowRejectionForDataRetentionPolicyExpired;
        acceptMissingPatientID = aeExt.acceptMissingPatientID;
        allowDeleteStudyPermanently = aeExt.allowDeleteStudyPermanently;
        acceptConflictingPatientID = aeExt.acceptConflictingPatientID;
        retrieveAETitles = aeExt.retrieveAETitles;
        hl7PSUSendingApplication = aeExt.hl7PSUSendingApplication;
        hl7PSUReceivingApplications = aeExt.hl7PSUReceivingApplications;
        hl7PSUDelay = aeExt.hl7PSUDelay;
        hl7PSUTimeout = aeExt.hl7PSUTimeout;
        hl7PSUOnTimeout = aeExt.hl7PSUOnTimeout;
        acceptedMoveDestinations.clear();
        acceptedMoveDestinations.addAll(aeExt.acceptedMoveDestinations);
        acceptedUserRoles.clear();
        acceptedUserRoles.addAll(aeExt.acceptedUserRoles);
        exportRules.clear();
        exportRules.addAll(aeExt.exportRules);
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

    public StorageDescriptor getStorageDescriptor() {
        return getArchiveDeviceExtension().getStorageDescriptorNotNull(storageID());
    }

    public StorageDescriptor getMetadataStorageDescriptor() {
        String storageID = metadataStorageID();
        return storageID != null ? getArchiveDeviceExtension().getStorageDescriptorNotNull(storageID) : null;
    }

    public Map<String, ExportRule> findExportRules(
            String hostName, String sendingAET, String receivingAET, Attributes attrs, Calendar cal) {
        HashMap<String, ExportRule> result = new HashMap<>();
        for (Collection<ExportRule> rules
                : new Collection[]{exportRules, getArchiveDeviceExtension().getExportRules() })
            for (ExportRule rule : rules)
                if (rule.match(hostName, sendingAET, receivingAET, attrs, cal))
                    for (String exporterID : rule.getExporterIDs()) {
                        ExportRule rule1 = result.get(exporterID);
                        if (rule1 == null || rule1.getEntity().compareTo(rule.getEntity()) > 0)
                            result.put(exporterID, rule);
                    }
        return result;
    }

    public List<RSForwardRule> findRSForwardRules(RSOperation rsOperation) {
        ArrayList<RSForwardRule> result = new ArrayList<>();
        for (Collection<RSForwardRule> rules
                : new Collection[]{rsForwardRules, getArchiveDeviceExtension().getRSForwardRules()})
            for (RSForwardRule rule : rules)
                if (rule.match(rsOperation))
                    result.add(rule);
        return result;
    }

    public ArchiveCompressionRule findCompressionRule(
            String hostName, String sendingAET, String receivingAET, Attributes attrs) {
        ArchiveCompressionRule rule1 = null;
        for (Collection<ArchiveCompressionRule> rules
                : new Collection[]{ compressionRules, getArchiveDeviceExtension().getCompressionRules() })
            for (ArchiveCompressionRule rule : rules)
                if (rule.match(hostName, sendingAET, receivingAET, attrs))
                    if (rule1 == null || rule1.getPriority() < rule.getPriority())
                        rule1 = rule;
        return rule1;
    }

    public ArchiveAttributeCoercion findAttributeCoercion(
            String hostName, String aet, TransferCapability.Role role, Dimse dimse, String sopClass) {
        ArchiveAttributeCoercion coercion1 = null;
        for (Collection<ArchiveAttributeCoercion> coercions
                : new Collection[]{ attributeCoercions, getArchiveDeviceExtension().getAttributeCoercions() })
            for (ArchiveAttributeCoercion coercion : coercions)
                if (coercion.match(hostName, aet, role, dimse, sopClass))
                    if (coercion1 == null || coercion1.getPriority() < coercion.getPriority())
                        coercion1 = coercion;
        return coercion1;
    }

    public StudyRetentionPolicy findStudyRetentionPolicy(
            String hostName, String sendingAET, String receivingAET, Attributes attrs) {
        StudyRetentionPolicy policy1 = null;
        for (Collection<StudyRetentionPolicy> policies
                : new Collection[]{ studyRetentionPolicies, getArchiveDeviceExtension().getStudyRetentionPolicies() })
            for (StudyRetentionPolicy policy : policies)
                if (policy.match(hostName, sendingAET, receivingAET, attrs))
                    if (policy1 == null || policy1.getPriority() < policy.getPriority())
                        policy1 = policy;
        return policy1;
    }

    public String storeAccessControlID(String hostName, String sendingAET, String receivingAET, Attributes attrs) {
        StoreAccessControlIDRule rule1 = null;
        for (Collection<StoreAccessControlIDRule> rules : new Collection[]{
                storeAccessControlIDRules,
                    getArchiveDeviceExtension().getStoreAccessControlIDRules()
        }) {
            for (StoreAccessControlIDRule rule : rules)
                if (rule.match(hostName, sendingAET, receivingAET, attrs))
                    if (rule1 == null || rule.getPriority() < rule.getPriority())
                        rule1 = rule;
        }
        return rule1 != null ? rule1.getStoreAccessControlID() : storeAccessControlID;
    }
}
