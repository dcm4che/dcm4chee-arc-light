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
 * *** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.conf.json;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.json.ConfigurationDelegate;
import org.dcm4che3.conf.json.JsonConfigurationExtension;
import org.dcm4che3.conf.json.JsonReader;
import org.dcm4che3.conf.json.JsonWriter;
import org.dcm4che3.data.ValueSelector;
import org.dcm4che3.net.*;
import org.dcm4chee.arc.conf.*;
import org.dcm4che3.data.Code;
import org.dcm4che3.util.Property;

import javax.json.stream.JsonParser;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jan 2016
 */
public class JsonArchiveConfiguration extends JsonConfigurationExtension {

    @Override
    protected void storeTo(Device device, JsonWriter writer) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        if (arcDev == null)
            return;

        writer.writeStartObject("dcmArchiveDevice");
        writer.writeNotNull("dcmFuzzyAlgorithmClass", arcDev.getFuzzyAlgorithmClass());
        writer.writeNotNull("dcmStorageID", arcDev.getStorageID());
        writer.writeNotNull("dcmQueryRetrieveViewID", arcDev.getQueryRetrieveViewID());
        writer.writeNotNull("dcmOverwritePolicy", arcDev.getOverwritePolicy().toString());
        writer.writeNotNull("dcmBulkDataSpoolDirectory", arcDev.getBulkDataSpoolDirectory());
        writer.writeNotDef("dcmQueryMatchUnknown", arcDev.isQueryMatchUnknown(), true);
        writer.writeNotDef("dcmPersonNameComponentOrderInsensitiveMatching", arcDev.isPersonNameComponentOrderInsensitiveMatching(), false);
        writer.writeNotDef("dcmSendPendingCGet", arcDev.isSendPendingCGet(), false);
        writer.writeNotNull("dcmSendPendingCMoveInterval", arcDev.getSendPendingCMoveInterval());
        writer.writeNotEmpty("dcmWadoSupportedSRClasses", arcDev.getWadoSupportedSRClasses());
        writer.writeNotNull("dcmWadoSR2HtmlTemplateURI", arcDev.getWadoSR2HtmlTemplateURI());
        writer.writeNotNull("dcmWadoSR2TextTemplateURI", arcDev.getWadoSR2TextTemplateURI());
        writer.writeNotDef("dcmQidoMaxNumberOfResults", arcDev.getQidoMaxNumberOfResults(), 0);
        writer.writeNotEmpty("dcmFwdMppsDestination", arcDev.getMppsForwardDestinations());
        writer.writeNotNull("dcmFallbackCMoveSCP", arcDev.getFallbackCMoveSCP());
        writer.writeNotNull("dcmFallbackCMoveSCPDestination", arcDev.getFallbackCMoveSCPDestination());
        writer.writeNotNull("dcmFallbackCMoveSCPLevel", arcDev.getFallbackCMoveSCPLevel());
        writer.writeNotNull("dcmAltCMoveSCP", arcDev.getAlternativeCMoveSCP());
        writer.writeNotNull("dcmExportTaskPollingInterval", arcDev.getExportTaskPollingInterval());
        writer.writeNotDef("dcmExportTaskFetchSize", arcDev.getExportTaskFetchSize(), 5);
        writer.writeNotNull("dcmPurgeStoragePollingInterval", arcDev.getPurgeStoragePollingInterval());
        writer.writeNotDef("dcmPurgeStorageFetchSize", arcDev.getPurgeStorageFetchSize(), 100);
        writer.writeNotDef("dcmDeleteStudyBatchSize", arcDev.getDeleteStudyBatchSize(), 10);
        writer.writeNotDef("dcmDeletePatientOnDeleteLastStudy", arcDev.isDeletePatientOnDeleteLastStudy(), false);
        writer.writeNotNull("dcmDeleteRejectedPollingInterval", arcDev.getDeleteRejectedPollingInterval());
        writer.writeNotDef("dcmDeleteRejectedFetchSize", arcDev.getDeleteRejectedFetchSize(), 100);
        writer.writeNotNull("dcmMaxAccessTimeStaleness", arcDev.getMaxAccessTimeStaleness());
        writer.writeNotNull("hl7PatientUpdateTemplateURI", arcDev.getPatientUpdateTemplateURI());
        writer.writeNotNull("dcmUnzipVendorDataToURI", arcDev.getUnzipVendorDataToURI());
        writeAttributeFilters(writer, arcDev);
        writeStorageDescriptor(writer, arcDev.getStorageDescriptors());
        writeQueryRetrieve(writer, arcDev.getQueryRetrieveViews());
        writeQueue(writer, arcDev.getQueueDescriptors());
        writeExporterDescriptor(writer, arcDev.getExporterDescriptors());
        writeExportRule(writer, arcDev.getExportRules());
        writeArchiveCompressionRules(writer, arcDev.getCompressionRules());
        writeArchiveAttributeCoercion(writer, arcDev.getAttributeCoercions());
        writeRejectionNote(writer, arcDev.getRejectionNotes());
        writer.writeEnd();
    }

    protected void writeAttributeFilters(JsonWriter writer, ArchiveDeviceExtension arcDev) {
        writer.writeStartArray("dcmAttributeFilter");
        for (Entity entity : Entity.values()) {
            writer.writeStartObject();
            writer.writeNotNull("dcmEntity", entity.name());
            Integer[] selection = new Integer[arcDev.getAttributeFilter(entity).getSelection().length];
            for (int i = 0; i < arcDev.getAttributeFilter(entity).getSelection().length; i++) {
                selection[i] = Integer.valueOf(arcDev.getAttributeFilter(entity).getSelection()[i]);
            }
            writer.writeNotEmpty("dcmTag", selection);
            writer.writeNotNull("dcmCustomAttribute1", arcDev.getAttributeFilter(entity).getCustomAttribute1());
            writer.writeNotNull("dcmCustomAttribute2", arcDev.getAttributeFilter(entity).getCustomAttribute2());
            writer.writeNotNull("dcmCustomAttribute3", arcDev.getAttributeFilter(entity).getCustomAttribute3());
            writer.writeEnd();
        }
        writer.writeEnd();
    }

    protected void writeStorageDescriptor(JsonWriter writer, Collection<StorageDescriptor> storageDescriptorList) {
        writer.writeStartArray("dcmStorage");
        for (StorageDescriptor st : storageDescriptorList) {
            writer.writeStartObject();
            writer.writeNotNull("dcmStorageID", st.getStorageID());
            writer.writeNotNull("dcmURI", st.getStorageURI());
            writer.writeNotNull("dcmDigestAlgorithm", st.getDigestAlgorithm());
            writer.writeNotEmpty("dcmRetrieveAET", st.getRetrieveAETitles());
            writer.writeNotNull("dcmInstanceAvailability", st.getInstanceAvailability());
            writer.writeNotEmpty("dcmDeleterThreshold", st.getDeleterThresholdsAsStrings());
            writer.writeNotEmpty("dcmProperty", descriptorProperties(st.getProperties()));
            writer.writeEnd();
        }
        writer.writeEnd();
    }

    private String[] descriptorProperties(Map<String, ?> props) {
        String[] ss = new String[props.size()];
        int i = 0;
        for (Map.Entry<String, ?> entry : props.entrySet())
            ss[i++] = entry.getKey() + '=' + entry.getValue();
        return ss;
    }

    protected void writeQueryRetrieve(JsonWriter writer, QueryRetrieveView[] queryRetrieveViewList) {
        writer.writeStartArray("dcmQueryRetrieveView");
        for (QueryRetrieveView qrv : queryRetrieveViewList) {
            writer.writeStartObject();
            writer.writeNotNull("dcmQueryRetrieveViewID", qrv.getViewID());
            writer.writeNotEmpty("dcmShowInstancesRejectedByCode", qrv.getShowInstancesRejectedByCodes());
            writer.writeNotEmpty("dcmHideRejectionNoteWithCode", qrv.getHideRejectionNotesWithCodes());
            writer.writeNotDef("dcmHideNotRejectedInstances", qrv.isHideNotRejectedInstances(), false);
            writer.writeEnd();
        }
        writer.writeEnd();
    }

    protected void writeQueue(JsonWriter writer, Collection<QueueDescriptor> queueDescriptorsList) {
        writer.writeStartArray("dcmQueue");
        for (QueueDescriptor qd : queueDescriptorsList) {
            writer.writeStartObject();
            writer.writeNotNull("dcmQueueName", qd.getQueueName());
            writer.writeNotNull("dcmJndiName", qd.getJndiName());
            writer.writeNotNull("dicomDescription", qd.getDescription());
            writer.writeNotDef("dcmMaxRetries", qd.getMaxRetries(), 0);
            writer.writeNotNull("dcmRetryDelay", qd.getRetryDelay());
            writer.writeNotNull("dcmMaxRetryDelay", qd.getMaxRetryDelay());
            writer.writeNotDef("dcmRetryDelayMultiplier", qd.getRetryDelayMultiplier(), 100);
            writer.writeEnd();
        }
        writer.writeEnd();
    }

    protected void writeExporterDescriptor (JsonWriter writer, Collection<ExporterDescriptor> exportDescriptorList) {
        writer.writeStartArray("dcmExporter");
        for (ExporterDescriptor ed : exportDescriptorList) {
            writer.writeStartObject();
            writer.writeNotNull("dcmExporterID", ed.getExporterID());
            writer.writeNotNull("dcmURI", ed.getExportURI());
            writer.writeNotNull("dcmQueueName", ed.getQueueName());
            writer.writeNotNull("dicomAETitle", ed.getAETitle());
            writer.writeNotEmpty("dcmSchedule", ed.getSchedules());
            writer.writeNotEmpty("dcmProperty", descriptorProperties(ed.getProperties()));
            writer.writeEnd();
        }
        writer.writeEnd();
    }

    protected void writeExportRule(JsonWriter writer, Collection<ExportRule> exportRuleList) {
        writer.writeStartArray("dcmExportRule");
        for (ExportRule er : exportRuleList) {
            writer.writeStartObject();
            writer.writeNotNull("cn", er.getCommonName());
            writer.writeNotNull("dcmEntity", er.getEntity());
            writer.writeNotEmpty("dcmExporterID", er.getExporterIDs());
            writer.writeNotEmpty("dcmProperty", toStrings(er.getConditions().getMap()));
            writer.writeNotEmpty("dcmSchedule", er.getSchedules());
            writer.writeNotNull("dcmDuration", er.getExportDelay());
            writer.writeEnd();
        }
        writer.writeEnd();
    }

    protected void writeArchiveCompressionRules(JsonWriter writer, Collection<ArchiveCompressionRule> archiveCompressionRuleList) {
        writer.writeStartArray("dcmArchiveCompressionRule");
        for (ArchiveCompressionRule acr : archiveCompressionRuleList) {
            writer.writeStartObject();
            writer.writeNotNull("cn", acr.getCommonName());
            writer.writeNotNull("dicomTransferSyntax", acr.getTransferSyntax());
            writer.writeNotDef("dcmRulePriority", acr.getPriority(), 0);
            writer.writeNotEmpty("dcmProperty", toStrings(acr.getConditions().getMap()));
            writer.writeNotEmpty("dcmImageWriteParam", acr.getImageWriteParams());
            writer.writeEnd();
        }
        writer.writeEnd();
    }

    private String[] toStrings(Map<String, ?> props) {
        String[] ss = new String[props.size()];
        int i = 0;
        for (Map.Entry<String, ?> entry : props.entrySet())
            ss[i++] = entry.getKey() + '=' + entry.getValue();
        return ss;
    }

    protected void writeArchiveAttributeCoercion (JsonWriter writer, Collection<ArchiveAttributeCoercion> archiveAttributeCoercionList) {
        writer.writeStartArray("dcmArchiveAttributeCoercion");
        for (ArchiveAttributeCoercion aac : archiveAttributeCoercionList) {
            writer.writeStartObject();
            writer.writeNotNull("cn", aac.getCommonName());
            writer.writeNotNull("dcmDIMSE", aac.getDIMSE());
            writer.writeNotNull("dicomTransferRole", aac.getRole());
            writer.writeNotNull("dcmURI", aac.getXSLTStylesheetURI());
            writer.writeNotDef("dcmRulePriority", aac.getPriority(), 0);
            writer.writeNotEmpty("dcmAETitle", aac.getAETitles());
            writer.writeNotEmpty("dcmHostname", aac.getHostNames());
            writer.writeNotEmpty("dcmSOPClass", aac.getSOPClasses());
            writer.writeNotNull("dcmNoKeywords", aac.isNoKeywords());
            writer.writeEnd();
        }
        writer.writeEnd();
    }

    protected void writeRejectionNote (JsonWriter writer, Collection<RejectionNote> rejectionNoteList) {
        writer.writeStartArray("dcmRejectionNote");
        for (RejectionNote rn : rejectionNoteList) {
            writer.writeStartObject();
            writer.writeNotNull("dcmRejectionNoteLabel", rn.getRejectionNoteLabel());
            writer.writeNotNull("dcmRejectionNoteCode", rn.getRejectionNoteCode());
            writer.writeNotNull("dcmRevokeRejection", rn.isRevokeRejection());
            writer.writeNotNull("dcmAcceptPreviousRejectedInstance", rn.getAcceptPreviousRejectedInstance());
            writer.writeNotEmpty("dcmOverwritePreviousRejection", rn.getOverwritePreviousRejection());
            writer.writeNotNull("dcmDeleteRejectedInstanceDelay", rn.getDeleteRejectedInstanceDelay());
            writer.writeNotNull("dcmDeleteRejectionNoteDelay", rn.getDeleteRejectionNoteDelay());
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
        writer.writeNotNull("dcmStorageID", arcAE.getStorageID());
        writer.writeNotNull("dcmOverwritePolicy", arcAE.getOverwritePolicy());
        writer.writeNotNull("dcmQueryRetrieveViewID", arcAE.getQueryRetrieveViewID());
        writer.writeNotNull("dcmBulkDataSpoolDirectory", arcAE.getBulkDataSpoolDirectory());
        writer.writeNotNull("dcmQueryMatchUnknown", arcAE.getQueryMatchUnknown());
        writer.writeNotNull("dcmPersonNameComponentOrderInsensitiveMatching", arcAE.getPersonNameComponentOrderInsensitiveMatching());
        writer.writeNotNull("dcmSendPendingCGet", arcAE.getSendPendingCGet());
        writer.writeNotNull("dcmSendPendingCMoveInterval", arcAE.getSendPendingCMoveInterval());
        writer.writeNotNull("dcmWadoSR2HtmlTemplateURI", arcAE.getWadoSR2HtmlTemplateURI());
        writer.writeNotNull("dcmWadoSR2TextTemplateURI", arcAE.getWadoSR2TextTemplateURI());
        writer.writeNotDef("dcmQidoMaxNumberOfResults", arcAE.getQidoMaxNumberOfResults(), 0);
        writer.writeNotEmpty("dcmFwdMppsDestination", arcAE.getMppsForwardDestinations());
        writer.writeNotNull("dcmFallbackCMoveSCP", arcAE.getFallbackCMoveSCP());
        writer.writeNotNull("dcmFallbackCMoveSCPDestination", arcAE.getFallbackCMoveSCPDestination());
        writer.writeNotNull("dcmFallbackCMoveSCPLevel", arcAE.getFallbackCMoveSCPLevel());
        writer.writeNotNull("dcmAltCMoveSCP", arcAE.getAlternativeCMoveSCP());
        writer.writeEnd();
    }

    @Override
    public boolean loadDeviceExtension(Device device, JsonReader reader, ConfigurationDelegate config)
            throws ConfigurationException {
        if (!reader.getString().equals("dcmArchiveDevice"))
            return false;

        reader.next();
        reader.expect(JsonParser.Event.START_OBJECT);
        ArchiveDeviceExtension arcDev = new ArchiveDeviceExtension();
        loadFrom(arcDev, reader, device.listConnections());
        device.addDeviceExtension(arcDev);
        reader.expect(JsonParser.Event.END_OBJECT);
        return true;
    }

    private void loadFrom(ArchiveDeviceExtension arcDev, JsonReader reader, List<Connection> conns) {
        while (reader.next() == JsonParser.Event.KEY_NAME) {
            switch (reader.getString()) {
                case "dcmFuzzyAlgorithmClass":
                    arcDev.setFuzzyAlgorithmClass(reader.stringValue());
                    break;
                case "dcmStorageID":
                    arcDev.setStorageID(reader.stringValue());
                    break;
                case "dcmOverwritePolicy":
                    arcDev.setOverwritePolicy(OverwritePolicy.valueOf(reader.stringValue()));
                    break;
                case "dcmQueryRetrieveViewID":
                    arcDev.setQueryRetrieveViewID(reader.stringValue());
                    break;
                case "dcmBulkDataSpoolDirectory":
                    arcDev.setBulkDataSpoolDirectory(reader.stringValue());
                    break;
                case "dcmQueryMatchUnknown":
                    arcDev.setQueryMatchUnknown(reader.booleanValue());
                    break;
                case "dcmPersonNameComponentOrderInsensitiveMatching":
                    arcDev.setPersonNameComponentOrderInsensitiveMatching(reader.booleanValue());
                    break;
                case "dcmSendPendingCGet":
                    arcDev.setSendPendingCGet(reader.booleanValue());
                    break;
                case "dcmSendPendingCMoveInterval":
                    arcDev.setSendPendingCMoveInterval(Duration.parse(reader.stringValue()));
                    break;
                case "dcmWadoSupportedSRClasses":
                    arcDev.setWadoSupportedSRClasses(reader.stringArray());
                    break;
                case "dcmWadoSR2HtmlTemplateURI":
                    arcDev.setWadoSR2HtmlTemplateURI(reader.stringValue());
                    break;
                case "dcmWadoSR2TextTemplateURI":
                    arcDev.setWadoSR2TextTemplateURI(reader.stringValue());
                    break;
                case "dcmQidoMaxNumberOfResults":
                    arcDev.setQidoMaxNumberOfResults(reader.intValue());
                    break;
                case "dcmFwdMppsDestination":
                    arcDev.setMppsForwardDestinations(reader.stringArray());
                    break;
                case "dcmFallbackCMoveSCP":
                    arcDev.setFallbackCMoveSCP(reader.stringValue());
                    break;
                case "dcmFallbackCMoveSCPDestination":
                    arcDev.setFallbackCMoveSCPDestination(reader.stringValue());
                    break;
                case "dcmFallbackCMoveSCPLevel":
                    arcDev.setFallbackCMoveSCPLevel(MoveForwardLevel.valueOf(reader.stringValue()));
                    break;
                case "dcmAltCMoveSCP":
                    arcDev.setAlternativeCMoveSCP(reader.stringValue());
                    break;
                case "dcmExportTaskPollingInterval":
                    arcDev.setExportTaskPollingInterval(Duration.parse(reader.stringValue()));
                    break;
                case "dcmExportTaskFetchSize":
                    arcDev.setExportTaskFetchSize(reader.intValue());
                    break;
                case "dcmPurgeStoragePollingInterval":
                    arcDev.setPurgeStoragePollingInterval(Duration.parse(reader.stringValue()));
                    break;
                case "dcmPurgeStorageFetchSize":
                    arcDev.setPurgeStorageFetchSize(reader.intValue());
                    break;
                case "dcmDeleteStudyBatchSize":
                    arcDev.setDeleteStudyBatchSize(reader.intValue());
                    break;
                case "dcmDeletePatientOnDeleteLastStudy":
                    arcDev.setDeletePatientOnDeleteLastStudy(reader.booleanValue());
                    break;
                case "dcmDeleteRejectedPollingInterval":
                    arcDev.setDeleteRejectedPollingInterval(Duration.parse(reader.stringValue()));
                    break;
                case "dcmDeleteRejectedFetchSize":
                    arcDev.setDeleteRejectedFetchSize(reader.intValue());
                    break;
                case "dcmMaxAccessTimeStaleness":
                    arcDev.setMaxAccessTimeStaleness(Duration.parse(reader.stringValue()));
                    break;
                case "hl7PatientUpdateTemplateURI":
                    arcDev.setPatientUpdateTemplateURI(reader.stringValue());
                    break;
                case "dcmUnzipVendorDataToURI":
                    arcDev.setUnzipVendorDataToURI(reader.stringValue());
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
                case "dcmExporter":
                    loadExporterDescriptorFrom(arcDev, reader);
                    break;
                case "dcmExportRule":
                    loadExportRuleFrom(arcDev, reader);
                    break;
                case "dcmArchiveCompressionRule":
                    loadArchiveCompressionRuleFrom(arcDev, reader);
                    break;
                case "dcmArchiveAttributeCoercion":
                    loadArchiveAttributeCoercionFrom(arcDev, reader);
                    break;
                case "dcmRejectionNote":
                    loadRejectionNoteFrom(arcDev, reader);
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
                        af.setSelection(tags(reader.stringArray()));
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
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            arcDev.setAttributeFilter(entity, af);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }

    private int[] tags(String[] tagsAsStringArray) {
        int[] selection = new int[tagsAsStringArray.length];
        for (int i = 0; i < tagsAsStringArray.length; i++) {
            selection[i] = Integer.parseInt(tagsAsStringArray[i]);
        }
        return selection;
    }

    private void loadStorageDescriptorFrom(ArchiveDeviceExtension arcDev, JsonReader reader) {
        reader.next();
        reader.expect(JsonParser.Event.START_ARRAY);
        while (reader.next() == JsonParser.Event.START_OBJECT) {
            reader.expect(JsonParser.Event.START_OBJECT);
            StorageDescriptor st = new StorageDescriptor(arcDev.getStorageID());
            while (reader.next() == JsonParser.Event.KEY_NAME) {
                switch (reader.getString()) {
                    case "dcmStorageID":
                        st.setStorageID(reader.stringValue());
                        break;
                    case  "dcmURI":
                        st.setStorageURI(URI.create(reader.stringValue()));
                        break;
                    case "dcmDigestAlgorithm":
                        st.setDigestAlgorithm(reader.stringValue());
                        break;
                    case "dcmRetrieveAET":
                        st.setRetrieveAETitles(reader.stringArray());
                        break;
                    case "dcmInstanceAvailability":
                        st.setInstanceAvailability(Availability.valueOf(reader.stringValue()));
                        break;
                    case "dcmDeleterThreshold":
                        st.setDeleterThresholdsFromStrings(reader.stringArray());
                        break;
                    case "dcmProperty":
                        st.setProperties(reader.stringArray());
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
        Collection<QueryRetrieveView> qrviews = new ArrayList<>();
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
            qrviews.add(qrv);
        }
        arcDev.setQueryRetrieveViews(qrviews.toArray(new QueryRetrieveView[0]));
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
                        qd.setRetryDelay(Duration.parse(reader.stringValue()));
                        break;
                    case "dcmMaxRetryDelay":
                        qd.setMaxRetryDelay(Duration.parse(reader.stringValue()));
                        break;
                    case "dcmRetryDelayMultiplier":
                        qd.setRetryDelayMultiplier(reader.intValue());
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
                    case "dicomAETitle":
                        ed.setAETitle(reader.stringValue());
                        break;
                    case "dcmSchedule":
                        ed.setSchedules(scheduleExpressions(reader.stringArray()));
                        break;
                    case "dcmProperty":
                        ed.setProperties(reader.stringArray());
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

    private ScheduleExpression[] scheduleExpressions(String[] scheduleExpressionAsStringArray) {
        ScheduleExpression[] se = new ScheduleExpression[scheduleExpressionAsStringArray.length];
        for (int i = 0; i < scheduleExpressionAsStringArray.length; i++) {
            se[i] = ScheduleExpression.valueOf(scheduleExpressionAsStringArray[i]);
        }
        return se;
    }

    private void loadExportRuleFrom(ArchiveDeviceExtension arcDev, JsonReader reader) {
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
                        er.setSchedules(scheduleExpressions(reader.stringArray()));
                        break;
                    case "dcmDuration":
                        er.setExportDelay(Duration.parse(reader.stringValue()));
                        break;
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            arcDev.addExportRule(er);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }

    private void loadArchiveCompressionRuleFrom(ArchiveDeviceExtension arcDev, JsonReader reader) {
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
            arcDev.addCompressionRule(acr);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
    }

    private void loadArchiveAttributeCoercionFrom(ArchiveDeviceExtension arcDev, JsonReader reader) {
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
                    case "dcmURI":
                        aac.setXSLTStylesheetURI(reader.stringValue());
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
                    case "dcmNoKeywords":
                        aac.setNoKeywords(reader.booleanValue());
                        break;
                    default:
                        reader.skipUnknownProperty();
                }
            }
            reader.expect(JsonParser.Event.END_OBJECT);
            arcDev.addAttributeCoercion(aac);
        }
        reader.expect(JsonParser.Event.END_ARRAY);
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
                    case "dcmRejectionNoteCode":
                        rn.setRejectionNoteCode(new Code(reader.stringValue()));
                        break;
                    case "dcmRevokeRejection":
                        rn.setRevokeRejection(reader.booleanValue());
                        break;
                    case "dcmAcceptPreviousRejectedInstance":
                        rn.setAcceptPreviousRejectedInstance(RejectionNote.AcceptPreviousRejectedInstance.valueOf(reader.stringValue()));
                        break;
                    case "dcmOverwritePreviousRejection":
                        rn.setOverwritePreviousRejection(overwritePreviousRejection(reader.stringArray()));
                        break;
                    case "dcmDeleteRejectedInstanceDelay":
                        rn.setDeleteRejectedInstanceDelay(Duration.parse(reader.stringValue()));
                        break;
                    case "dcmDeleteRejectionNoteDelay":
                        rn.setDeleteRejectionNoteDelay(Duration.parse(reader.stringValue()));
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

    @Override
    public boolean loadApplicationEntityExtension(Device device, ApplicationEntity ae, JsonReader reader) {
        if (!reader.getString().equals("dcmArchiveNetworkAE"))
            return false;

        reader.next();
        reader.expect(JsonParser.Event.START_OBJECT);
        ArchiveAEExtension arcAE = new ArchiveAEExtension();
        loadFrom(arcAE, reader);
        ae.addAEExtension(arcAE);
        reader.expect(JsonParser.Event.END_OBJECT);
        return true;
    }

    private void loadFrom(ArchiveAEExtension arcAE, JsonReader reader) {
        while (reader.next() == JsonParser.Event.KEY_NAME) {
            switch (reader.getString()) {
                case "dcmStorageID":
                    arcAE.setStorageID(reader.stringValue());
                    break;
                case "dcmQueryRetrieveViewID":
                    arcAE.setQueryRetrieveViewID(reader.stringValue());
                    break;
                case "dcmOverwritePolicy":
                    arcAE.setOverwritePolicy(OverwritePolicy.valueOf(reader.stringValue()));
                    break;
                case "dcmBulkDataSpoolDirectory":
                    arcAE.setBulkDataSpoolDirectory(reader.stringValue());
                    break;
                case "dcmQueryMatchUnknown":
                    arcAE.setQueryMatchUnknown(reader.booleanValue());
                    break;
                case "dcmPersonNameComponentOrderInsensitiveMatching":
                    arcAE.setPersonNameComponentOrderInsensitiveMatching(reader.booleanValue());
                    break;
                case "dcmSendPendingCGet":
                    arcAE.setSendPendingCGet(reader.booleanValue());
                    break;
                case "dcmSendPendingCMoveInterval":
                    arcAE.setSendPendingCMoveInterval(Duration.parse(reader.stringValue()));
                    break;
                case "dcmWadoSR2HtmlTemplateURI":
                    arcAE.setWadoSR2HtmlTemplateURI(reader.stringValue());
                    break;
                case "dcmWadoSR2TextTemplateURI":
                    arcAE.setWadoSR2TextTemplateURI(reader.stringValue());
                    break;
                case "dcmQidoMaxNumberOfResults":
                    arcAE.setQidoMaxNumberOfResults(reader.intValue());
                    break;
                case "dcmFwdMppsDestination":
                    arcAE.setMppsForwardDestinations(reader.stringArray());
                    break;
                case "dcmFallbackCMoveSCP":
                    arcAE.setFallbackCMoveSCP(reader.stringValue());
                    break;
                case "dcmFallbackCMoveSCPDestination":
                    arcAE.setFallbackCMoveSCPDestination(reader.stringValue());
                    break;
                case "dcmFallbackCMoveSCPLevel":
                    arcAE.setFallbackCMoveSCPLevel(MoveForwardLevel.valueOf(reader.stringValue()));
                    break;
                case "dcmAltCMoveSCP":
                    arcAE.setAlternativeCMoveSCP(reader.stringValue());
                    break;
                default:
                    reader.skipUnknownProperty();
            }
        }
    }
}
