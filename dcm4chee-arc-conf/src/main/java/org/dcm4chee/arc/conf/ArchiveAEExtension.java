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

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jul 2015
 */
public class ArchiveAEExtension extends AEExtension {
    private static final String JBOSS_SERVER_TEMP_DIR = "${jboss.server.temp.dir}";
    private String storageID;
    private String storeAccessControlID;
    private String[] accessControlIDs = {};
    private OverwritePolicy overwritePolicy;
    private String bulkDataSpoolDirectory;
    private String queryRetrieveViewID;
    private Boolean personNameComponentOrderInsensitiveMatching;
    private Boolean sendPendingCGet;
    private Duration sendPendingCMoveInterval;
    private String wadoSR2HtmlTemplateURI;
    private String wadoSR2TextTemplateURI;
    private String[] mppsForwardDestinations = {};
    private String[] ianDestinations = {};
    private Duration ianDelay;
    private String fallbackCMoveSCP;
    private String fallbackCMoveSCPDestination;
    private Duration ianTimeout;
    private Boolean ianOnTimeout;
    private int fallbackCMoveSCPRetries;
    private String alternativeCMoveSCP;
    private int qidoMaxNumberOfResults;
    private MWLStatus[] hideSPSWithStatusFromMWL = {};
    private final ArrayList<ExportRule> exportRules = new ArrayList<>();
    private final ArrayList<ArchiveCompressionRule> compressionRules = new ArrayList<>();
    private final ArrayList<ArchiveAttributeCoercion> attributeCoercions = new ArrayList<>();

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

    public MWLStatus[] getHideSPSWithStatusFromMWL() {
        return hideSPSWithStatusFromMWL;
    }

    public void setHideSPSWithStatusFromMWL(MWLStatus[] hideSPSWithStatusFromMWL) {
        this.hideSPSWithStatusFromMWL = hideSPSWithStatusFromMWL;
    }

    public MWLStatus[] hideSPSWithStatusFromMWL() {
        return hideSPSWithStatusFromMWL.length > 0
                ? hideSPSWithStatusFromMWL
                : getArchiveDeviceExtension().getHideSPSWithStatusFrom();
    }

    public QueryRetrieveView getQueryRetrieveView() {
        return getArchiveDeviceExtension().getQueryRetrieveViewNotNull(queryRetrieveViewID());
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

    @Override
    public void reconfigure(AEExtension from) {
        ArchiveAEExtension aeExt = (ArchiveAEExtension) from;
        storageID = aeExt.storageID;
        storeAccessControlID = aeExt.storeAccessControlID;
        accessControlIDs = aeExt.accessControlIDs;
        storageID = aeExt.storageID;
        overwritePolicy = aeExt.overwritePolicy;
        bulkDataSpoolDirectory = aeExt.bulkDataSpoolDirectory;
        queryRetrieveViewID = aeExt.queryRetrieveViewID;
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
        fallbackCMoveSCPRetries = aeExt.fallbackCMoveSCPRetries;
        alternativeCMoveSCP = aeExt.alternativeCMoveSCP;
        qidoMaxNumberOfResults = aeExt.qidoMaxNumberOfResults;
        hideSPSWithStatusFromMWL = aeExt.hideSPSWithStatusFromMWL;
        exportRules.clear();
        exportRules.addAll(aeExt.exportRules);
        compressionRules.clear();
        compressionRules.addAll(aeExt.compressionRules);
        attributeCoercions.clear();
        attributeCoercions.addAll(aeExt.attributeCoercions);
    }

    public ArchiveDeviceExtension getArchiveDeviceExtension() {
        return ae.getDevice().getDeviceExtension(ArchiveDeviceExtension.class);
    }

    public StorageDescriptor getStorageDescriptor() {
        return getArchiveDeviceExtension().getStorageDescriptorNotNull(storageID());
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

}
