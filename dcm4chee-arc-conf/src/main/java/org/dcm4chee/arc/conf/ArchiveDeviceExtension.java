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

import org.dcm4che3.data.Code;
import org.dcm4che3.net.DeviceExtension;
import org.dcm4che3.soundex.FuzzyStr;
import org.dcm4che3.util.StringUtils;

import java.util.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jul 2015
 */
public class ArchiveDeviceExtension extends DeviceExtension {

    private String fuzzyAlgorithmClass;

    private String storageID;
    private OverwritePolicy overwritePolicy;
    private String bulkDataSpoolDirectory;
    private String queryRetrieveViewID;
    private boolean sendPendingCGet = false;
    private Duration sendPendingCMoveInterval;
    private boolean personNameComponentOrderInsensitiveMatching = false;
    private int qidoMaxNumberOfResults = 0;
    private String wadoSR2HtmlTemplateURI;
    private String wadoSR2TextTemplateURI;
    private String patientUpdateTemplateURI;
    private String importReportTemplateURI;
    private String scheduleProcedureTemplateURI;
    private String unzipVendorDataToURI;
    private String[] mppsForwardDestinations = {};
    private String[] ianDestinations = {};
    private Duration ianDelay;
    private Duration ianTimeout;
    private boolean ianOnTimeout;
    private Duration ianTaskPollingInterval;
    private int ianTaskFetchSize = 100;
    private String fallbackCMoveSCP;
    private String fallbackCMoveSCPDestination;
    private int fallbackCMoveSCPRetries;
    private String alternativeCMoveSCP;
    private Duration exportTaskPollingInterval;
    private int exportTaskFetchSize = 5;
    private Duration deleteRejectedPollingInterval;
    private int deleteRejectedFetchSize = 100;
    private Duration purgeStoragePollingInterval;
    private int purgeStorageFetchSize = 100;
    private int deleteStudyBatchSize = 10;
    private boolean deletePatientOnDeleteLastStudy = false;
    private Duration maxAccessTimeStaleness;
    private Duration aeCacheStaleTimeout;
    private Duration leadingCFindSCPQueryCacheStaleTimeout;
    private int leadingCFindSCPQueryCacheSize = 10;
    private String auditSpoolDirectory;
    private Duration auditPollingInterval;
    private Duration auditAggregateDuration;
    private String stowSpoolDirectory;
    private String wadoSpoolDirectory;
    private Duration purgeQueueMessagePollingInterval;
    private int purgeQueueMessageFetchSize = 100;
    private MWLStatus[] hideSPSWithStatusFrom = {};
    private String hl7LogDirectory;
    private String hl7ErrorLogDirectory;

    private final HashSet<String> wadoSupportedSRClasses = new HashSet<>();
    private final EnumMap<Entity,AttributeFilter> attributeFilters = new EnumMap<>(Entity.class);
    private QueryRetrieveView[] queryRetrieveViews = {};
    private final Map<String, StorageDescriptor> storageDescriptorMap = new HashMap<>();
    private final Map<String, QueueDescriptor> queueDescriptorMap = new HashMap<>();
    private final Map<String, ExporterDescriptor> exporterDescriptorMap = new HashMap<>();
    private final Map<String, RejectionNote> rejectionNoteMap = new HashMap<>();
    private final ArrayList<ExportRule> exportRules = new ArrayList<>();
    private final ArrayList<ArchiveCompressionRule> compressionRules = new ArrayList<>();

    private final ArrayList<ArchiveAttributeCoercion> attributeCoercions = new ArrayList<>();
    private transient FuzzyStr fuzzyStr;

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

    public String getBulkDataSpoolDirectory() {
        return bulkDataSpoolDirectory;
    }

    public void setBulkDataSpoolDirectory(String bulkDataSpoolDirectory) {
        this.bulkDataSpoolDirectory = bulkDataSpoolDirectory;
    }

    public String getStorageID() {
        return storageID;
    }

    public void setStorageID(String storageID) {
        this.storageID = storageID;
    }

    public String getQueryRetrieveViewID() {
        return queryRetrieveViewID;
    }

    public void setQueryRetrieveViewID(String queryRetrieveViewID) {
        this.queryRetrieveViewID = queryRetrieveViewID;
    }

    public boolean isPersonNameComponentOrderInsensitiveMatching() {
        return personNameComponentOrderInsensitiveMatching;
    }

    public void setPersonNameComponentOrderInsensitiveMatching(boolean personNameComponentOrderInsensitiveMatching) {
        this.personNameComponentOrderInsensitiveMatching = personNameComponentOrderInsensitiveMatching;
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
        this.ianTaskFetchSize = ianTaskFetchSize;
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

    public int getFallbackCMoveSCPRetries() {
        return fallbackCMoveSCPRetries;
    }

    public void setFallbackCMoveSCPRetries(int fallbackCMoveSCPRetries) {
        this.fallbackCMoveSCPRetries = fallbackCMoveSCPRetries;
    }

    public String getAlternativeCMoveSCP() {
        return alternativeCMoveSCP;
    }

    public void setAlternativeCMoveSCP(String alternativeCMoveSCP) {
        this.alternativeCMoveSCP = alternativeCMoveSCP;
    }

    public int getQidoMaxNumberOfResults() {
        return qidoMaxNumberOfResults;
    }

    public void setQidoMaxNumberOfResults(int qidoMaxNumberOfResults) {
        this.qidoMaxNumberOfResults = qidoMaxNumberOfResults;
    }

    public int getExportTaskFetchSize() {
        return exportTaskFetchSize;
    }

    public void setExportTaskFetchSize(int exportTaskFetchSize) {
        this.exportTaskFetchSize = exportTaskFetchSize;
    }

    public Duration getExportTaskPollingInterval() {
        return exportTaskPollingInterval;
    }

    public void setExportTaskPollingInterval(Duration exportTaskPollingInterval) {
        this.exportTaskPollingInterval = exportTaskPollingInterval;
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
        this.deleteRejectedFetchSize = deleteRejectedFetchSize;
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
        this.purgeStorageFetchSize = purgeStorageFetchSize;
    }

    public int getDeleteStudyBatchSize() {
        return deleteStudyBatchSize;
    }

    public void setDeleteStudyBatchSize(int deleteStudyBatchSize) {
        this.deleteStudyBatchSize = deleteStudyBatchSize;
    }

    public boolean isDeletePatientOnDeleteLastStudy() {
        return deletePatientOnDeleteLastStudy;
    }

    public void setDeletePatientOnDeleteLastStudy(boolean deletePatientOnDeleteLastStudy) {
        this.deletePatientOnDeleteLastStudy = deletePatientOnDeleteLastStudy;
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
        this.leadingCFindSCPQueryCacheSize = leadingCFindSCPQueryCacheSize;
    }

    public String getAuditSpoolDirectory() {
        return auditSpoolDirectory;
    }

    public void setAuditSpoolDirectory(String auditSpoolDirectory) {
        this.auditSpoolDirectory = auditSpoolDirectory;
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
        return auditSpoolDirectory != null && auditPollingInterval != null && auditAggregateDuration != null;
    }

    public String getStowSpoolDirectory() {
        return stowSpoolDirectory;
    }

    public void setStowSpoolDirectory(String stowSpoolDirectory) {
        this.stowSpoolDirectory = stowSpoolDirectory;
    }

    public String getWadoSpoolDirectory() {
        return wadoSpoolDirectory;
    }

    public void setWadoSpoolDirectory(String wadoSpoolDirectory) {
        this.wadoSpoolDirectory = wadoSpoolDirectory;
    }

    public String getHl7LogDirectory() {
        return hl7LogDirectory;
    }

    public void setHl7LogDirectory(String hl7LogDirectory) {
        this.hl7LogDirectory = hl7LogDirectory;
    }

    public String getHl7ErrorLogDirectory() {
        return hl7ErrorLogDirectory;
    }

    public void setHl7ErrorLogDirectory(String hl7ErrorLogDirectory) {
        this.hl7ErrorLogDirectory = hl7ErrorLogDirectory;
    }

    public Duration getPurgeQueueMessagePollingInterval() {
        return purgeQueueMessagePollingInterval;
    }

    public void setPurgeQueueMessagePollingInterval(Duration purgeQueueMessagePollingInterval) {
        this.purgeQueueMessagePollingInterval = purgeQueueMessagePollingInterval;
    }

    public int getPurgeQueueMessageFetchSize() {
        return purgeQueueMessageFetchSize;
    }

    public void setPurgeQueueMessageFetchSize(int purgeQueueMessageFetchSize) {
        this.purgeQueueMessageFetchSize = purgeQueueMessageFetchSize;
    }

    public MWLStatus[] getHideSPSWithStatusFrom() {
        return hideSPSWithStatusFrom;
    }

    public void setHideSPSWithStatusFrom(MWLStatus[] hideSPSWithStatusFrom) {
        this.hideSPSWithStatusFrom = hideSPSWithStatusFrom;
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

    public QueryRetrieveView[] getQueryRetrieveViews() {
        return queryRetrieveViews;
    }

    public void setQueryRetrieveViews(QueryRetrieveView... queryRetrieveViews) {
        this.queryRetrieveViews = queryRetrieveViews;
    }

    public QueryRetrieveView getQueryRetrieveView(String viewID) {
        for (QueryRetrieveView view : queryRetrieveViews) {
            if (view.getViewID().equals(viewID))
                return view;
        }
        return null;
    }

    public QueryRetrieveView getQueryRetrieveViewNotNull(String viewID) {
        QueryRetrieveView view = getQueryRetrieveView(viewID);
        if (view == null)
            throw new IllegalArgumentException("No Query Retrieve View configured with ID:" + viewID);
        return view;
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

    public Collection<AttributeFilter> getAttributeFilters() {
        return attributeFilters.values();
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

    public Collection<ExporterDescriptor> getExporterDescriptors() {
        return exporterDescriptorMap.values();
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

    public void addAttributeCoercion(ArchiveAttributeCoercion coercion) {
        attributeCoercions.add(coercion);
    }

    public Collection<ArchiveAttributeCoercion> getAttributeCoercions() {
        return attributeCoercions;
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

    public RejectionNote removeRejectionNote(String rjNoteID) {
        return rejectionNoteMap.remove(rjNoteID);
    }

    public void addRejectionNote(RejectionNote rjNote) {
        rejectionNoteMap.put(rjNote.getRejectionNoteLabel(), rjNote);
    }

    public Collection<RejectionNote> getRejectionNotes() {
        return rejectionNoteMap.values();
    }

    @Override
    public void reconfigure(DeviceExtension from) {
        ArchiveDeviceExtension arcdev = (ArchiveDeviceExtension) from;
        fuzzyAlgorithmClass = arcdev.fuzzyAlgorithmClass;
        fuzzyStr = arcdev.fuzzyStr;
        storageID = arcdev.storageID;
        overwritePolicy = arcdev.overwritePolicy;
        bulkDataSpoolDirectory = arcdev.bulkDataSpoolDirectory;
        queryRetrieveViewID = arcdev.queryRetrieveViewID;
        personNameComponentOrderInsensitiveMatching = arcdev.personNameComponentOrderInsensitiveMatching;
        sendPendingCGet = arcdev.sendPendingCGet;
        sendPendingCMoveInterval = arcdev.sendPendingCMoveInterval;
        wadoSupportedSRClasses.clear();
        wadoSupportedSRClasses.addAll(arcdev.wadoSupportedSRClasses);
        wadoSR2HtmlTemplateURI = arcdev.wadoSR2HtmlTemplateURI;
        wadoSR2TextTemplateURI = arcdev.wadoSR2TextTemplateURI;
        patientUpdateTemplateURI = arcdev.patientUpdateTemplateURI;
        importReportTemplateURI = arcdev.importReportTemplateURI;
        scheduleProcedureTemplateURI = arcdev.scheduleProcedureTemplateURI;
        qidoMaxNumberOfResults = arcdev.qidoMaxNumberOfResults;
        queryRetrieveViews = arcdev.queryRetrieveViews;
        mppsForwardDestinations = arcdev.mppsForwardDestinations;
        ianDestinations = arcdev.ianDestinations;
        ianDelay = arcdev.ianDelay;
        ianTimeout = arcdev.ianTimeout;
        ianOnTimeout = arcdev.ianOnTimeout;
        ianTaskPollingInterval = arcdev.ianTaskPollingInterval;
        ianTaskFetchSize = arcdev.ianTaskFetchSize;
        fallbackCMoveSCP = arcdev.fallbackCMoveSCP;
        fallbackCMoveSCPDestination = arcdev.fallbackCMoveSCPDestination;
        fallbackCMoveSCPRetries = arcdev.fallbackCMoveSCPRetries;
        alternativeCMoveSCP = arcdev.alternativeCMoveSCP;
        exportTaskPollingInterval = arcdev.exportTaskPollingInterval;
        exportTaskFetchSize = arcdev.exportTaskFetchSize;
        deleteRejectedPollingInterval = arcdev.deleteRejectedPollingInterval;
        deleteRejectedFetchSize = arcdev.deleteRejectedFetchSize;
        purgeStoragePollingInterval = arcdev.purgeStoragePollingInterval;
        purgeStorageFetchSize = arcdev.purgeStorageFetchSize;
        deleteStudyBatchSize = arcdev.deleteStudyBatchSize;
        deletePatientOnDeleteLastStudy = arcdev.deletePatientOnDeleteLastStudy;
        maxAccessTimeStaleness = arcdev.maxAccessTimeStaleness;
        aeCacheStaleTimeout = arcdev.aeCacheStaleTimeout;
        leadingCFindSCPQueryCacheStaleTimeout = arcdev.leadingCFindSCPQueryCacheStaleTimeout;
        leadingCFindSCPQueryCacheSize = arcdev.leadingCFindSCPQueryCacheSize;
        auditSpoolDirectory = arcdev.auditSpoolDirectory;
        auditPollingInterval = arcdev.auditPollingInterval;
        auditAggregateDuration = arcdev.auditAggregateDuration;
        stowSpoolDirectory = arcdev.stowSpoolDirectory;
        wadoSpoolDirectory = arcdev.wadoSpoolDirectory;
        hl7LogDirectory = arcdev.hl7LogDirectory;
        hl7ErrorLogDirectory = arcdev.hl7ErrorLogDirectory;
        purgeQueueMessagePollingInterval = arcdev.purgeQueueMessagePollingInterval;
        purgeQueueMessageFetchSize = arcdev.purgeQueueMessageFetchSize;
        hideSPSWithStatusFrom = arcdev.hideSPSWithStatusFrom;
        attributeFilters.clear();
        attributeFilters.putAll(arcdev.attributeFilters);
        storageDescriptorMap.clear();
        storageDescriptorMap.putAll(arcdev.storageDescriptorMap);
        queueDescriptorMap.clear();
        queueDescriptorMap.putAll(arcdev.queueDescriptorMap);
        exporterDescriptorMap.clear();
        exporterDescriptorMap.putAll(arcdev.exporterDescriptorMap);
        exportRules.clear();
        exportRules.addAll(arcdev.exportRules);
        compressionRules.clear();
        compressionRules.addAll(arcdev.compressionRules);
        attributeCoercions.clear();
        attributeCoercions.addAll(arcdev.attributeCoercions);
        rejectionNoteMap.clear();
        rejectionNoteMap.putAll(arcdev.rejectionNoteMap);
    }
}
