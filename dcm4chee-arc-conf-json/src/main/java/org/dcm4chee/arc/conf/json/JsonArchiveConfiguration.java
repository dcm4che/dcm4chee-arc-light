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
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.*;

import javax.json.stream.JsonParser;
import java.net.URI;
import java.util.Collection;
import java.util.List;

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
        writer.writeNotNull("dcmSendPendingCMoveInterval", arcDev.getSendPendingCMoveInterval().toString());
        writer.writeNotEmpty("dcmWadoSupportedSRClasses", arcDev.getWadoSupportedSRClasses());
        writer.writeNotNull("dcmWadoSR2HtmlTemplateURI", arcDev.getWadoSR2HtmlTemplateURI());
        writer.writeNotNull("dcmWadoSR2TextTemplateURI", arcDev.getWadoSR2TextTemplateURI());
        writer.writeNotDef("dcmQidoMaxNumberOfResults", arcDev.getQidoMaxNumberOfResults(), 0);
        writer.writeNotEmpty("dcmFwdMppsDestination", arcDev.getMppsForwardDestinations());
        writer.writeNotNull("dcmFallbackCMoveSCP", arcDev.getFallbackCMoveSCP());
        writer.writeNotNull("dcmFallbackCMoveSCPDestination", arcDev.getFallbackCMoveSCPDestination());
        writer.writeNotNull("dcmFallbackCMoveSCPLevel", arcDev.getFallbackCMoveSCPLevel().toString());
        writer.writeNotNull("dcmAltCMoveSCP", arcDev.getAlternativeCMoveSCP());
        writer.writeNotNull("dcmExportTaskPollingInterval", arcDev.getExportTaskPollingInterval().toString());
        writer.writeNotDef("dcmExportTaskFetchSize", arcDev.getExportTaskFetchSize(), 5);
        writer.writeNotNull("dcmPurgeStoragePollingInterval", arcDev.getPurgeStoragePollingInterval().toString());
        writer.writeNotDef("dcmPurgeStorageFetchSize", arcDev.getPurgeStorageFetchSize(), 100);
        writer.writeNotNull("dcmDeleteRejectedPollingInterval", arcDev.getDeleteRejectedPollingInterval().toString());
        writer.writeNotDef("dcmDeleteRejectedFetchSize", arcDev.getDeleteRejectedFetchSize(), 100);
        writer.writeNotNull("hl7PatientUpdateTemplateURI", arcDev.getPatientUpdateTemplateURI());
        writer.writeNotNull("dcmUnzipVendorDataToURI", arcDev.getUnzipVendorDataToURI());
//        writeAttributeFilters(writer, arcDev);
        writeStorageDescriptor(writer, arcDev.getStorageDescriptors());
        writeExporterDescriptor(writer, arcDev.getExporterDescriptors());
        writeExportRule(writer, arcDev.getExportRules());
        writeArchiveAttributeCoercion(writer, arcDev.getAttributeCoercions());
        writeRejectionNote(writer, arcDev.getRejectionNotes());
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
            writer.writeNotNull("dcmProperty", st.getProperty("checkMountFile", "NO_MOUNT"));
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
            writer.writeNotNull("dcmProperty", ed.getProperty("checkMountFile", "NO_MOUNT"));
            writer.writeEnd();
        }
        writer.writeEnd();
    }

    protected void writeExportRule(JsonWriter writer, Collection<ExportRule> exportRuleList) {
        writer.writeStartArray("dcmExportRule");
        for (ExportRule er : exportRuleList) {
            writer.writeStartObject();
            writer.writeNotNull("dcmEntity", er.getEntity());
            writer.writeNotEmpty("dcmExporterID", er.getExporterIDs());
            writer.writeNotEmpty("dcmSchedule", er.getSchedules());
            writer.writeNotNull("dcmDuration", er.getExportDelay());
            writer.writeEnd();
        }
        writer.writeEnd();
    }

    protected void writeArchiveAttributeCoercion (JsonWriter writer, Collection<ArchiveAttributeCoercion> archiveAttributeCoercionList) {
        writer.writeStartArray("dcmArchiveAttributeCoercion");
        for (ArchiveAttributeCoercion aac : archiveAttributeCoercionList) {
            writer.writeStartObject();
            writer.writeNotNull("cn", aac.getCommonName());
            writer.writeNotNull("dcmDIMSE", aac.getDIMSE());
            writer.writeNotNull("dcmTransferRole", aac.getRole());
            writer.writeNotNull("dcmURI", aac.getXSLTStylesheetURI());
            writer.writeNotNull("dcmRulePriority", aac.getPriority());
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
            writer.writeNotNull("dcmAcceptPreviousRejectedInstance", rn.getAcceptPreviousRejectedInstance().toString());
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
        writer.writeNotNull("dcmOverwritePolicy", arcAE.getOverwritePolicy().toString());
        writer.writeNotNull("dcmQueryRetrieveViewID", arcAE.getQueryRetrieveViewID());
        writer.writeNotNull("dcmBulkDataSpoolDirectory", arcAE.getBulkDataSpoolDirectory());
        writer.writeNotDef("dcmQueryMatchUnknown", arcAE.getQueryMatchUnknown(), true);
        writer.writeNotDef("dcmPersonNameComponentOrderInsensitiveMatching", arcAE.getPersonNameComponentOrderInsensitiveMatching(), false);
        writer.writeNotDef("dcmSendPendingCGet", arcAE.getSendPendingCGet(), false);
        writer.writeNotNull("dcmSendPendingCMoveInterval", arcAE.getSendPendingCMoveInterval().toString());
        writer.writeNotNull("dcmWadoSR2HtmlTemplateURI", arcAE.getWadoSR2HtmlTemplateURI());
        writer.writeNotNull("dcmWadoSR2TextTemplateURI", arcAE.getWadoSR2TextTemplateURI());
        writer.writeNotDef("dcmQidoMaxNumberOfResults", arcAE.getQidoMaxNumberOfResults(), 0);
        writer.writeNotEmpty("dcmFwdMppsDestination", arcAE.getMppsForwardDestinations());
        writer.writeNotNull("dcmFallbackCMoveSCP", arcAE.getFallbackCMoveSCP());
        writer.writeNotNull("dcmFallbackCMoveSCPDestination", arcAE.getFallbackCMoveSCPDestination());
        writer.writeNotNull("dcmFallbackCMoveSCPLevel", arcAE.getFallbackCMoveSCPLevel().toString());
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
                case "dcmDeleteRejectedPollingInterval":
                    arcDev.setDeleteRejectedPollingInterval(Duration.parse(reader.stringValue()));
                    break;
                case "dcmDeleteRejectedFetchSize":
                    arcDev.setDeleteRejectedFetchSize(reader.intValue());
                    break;
                case "hl7PatientUpdateTemplateURI":
                    arcDev.setPatientUpdateTemplateURI(reader.stringValue());
                    break;
                case "dcmUnzipVendorDataToURI":
                    arcDev.setUnzipVendorDataToURI(reader.stringValue());
                    break;
                case "dcmStorage":
                    StorageDescriptor st = new StorageDescriptor(arcDev.getStorageID());
                    reader.next();
                    reader.expect(JsonParser.Event.START_ARRAY);
                    while (reader.next() == JsonParser.Event.START_OBJECT) {
                        reader.expect(JsonParser.Event.START_OBJECT);
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
                                case "dcmProperty":
                                    st.setProperty("checkMountFile", reader.stringValue());
                                    break;
                                default:
                                    reader.skipUnknownProperty();
                            }
                        }
                        reader.expect(JsonParser.Event.END_OBJECT);
                    }
                    reader.expect(JsonParser.Event.END_ARRAY);
                    arcDev.addStorageDescriptor(st);
                    break;
                default:
                    reader.skipUnknownProperty();
            }
        }
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
