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
 * Portions created by the Initial Developer are Copyright (C) 2013-2019
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

package org.dcm4chee.arc.conf.json;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.json.ConfigurationDelegate;
import org.dcm4che3.conf.json.JsonReader;
import org.dcm4che3.conf.json.JsonWriter;
import org.dcm4che3.conf.json.hl7.JsonHL7ConfigurationExtension;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4chee.arc.conf.*;

import javax.json.stream.JsonParser;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jan 2016
 */
public class JsonArchivHL7Configuration implements JsonHL7ConfigurationExtension {
    @Override
    public void storeTo(HL7Application hl7App, Device device, JsonWriter writer) {
        ArchiveHL7ApplicationExtension ext = hl7App.getHL7ApplicationExtension(ArchiveHL7ApplicationExtension.class);
        if (ext == null)
            return;

        writer.writeStartObject("dcmArchiveHL7Application");
        writer.writeNotNullOrDef("hl7PatientUpdateTemplateURI", ext.getPatientUpdateTemplateURI(), null);
        writer.writeNotNullOrDef("hl7ImportReportTemplateURI", ext.getImportReportTemplateURI(), null);
        writer.writeNotEmpty("hl7ImportReportTemplateParam", JsonArchiveConfiguration.descriptorProperties(ext.getImportReportTemplateParams()));
        writer.writeNotNullOrDef("hl7ScheduleProcedureTemplateURI", ext.getScheduleProcedureTemplateURI(), null);
        writer.writeNotNullOrDef("hl7LogFilePattern", ext.getHL7LogFilePattern(), null);
        writer.writeNotNullOrDef("hl7ErrorLogFilePattern", ext.getHL7ErrorLogFilePattern(), null);
        writer.writeNotNullOrDef("dicomAETitle", ext.getAETitle(), null);
        writer.writeNotNullOrDef("hl7ScheduledProtocolCodeInOrder", ext.getHL7ScheduledProtocolCodeInOrder(), null);
        writer.writeNotNullOrDef("hl7ScheduledStationAETInOrder", ext.getHL7ScheduledStationAETInOrder(), null);
        writer.writeNotEmpty("hl7NoPatientCreateMessageType", ext.getHL7NoPatientCreateMessageTypes());
        writer.writeNotNull("hl7UseNullValue", ext.getHL7UseNullValue());
        writer.writeNotNullOrDef("hl7OrderMissingStudyIUIDPolicy", ext.getHL7OrderMissingStudyIUIDPolicy(), null);
        writer.writeNotNullOrDef("hl7ImportReportMissingStudyIUIDPolicy", ext.getHl7ImportReportMissingStudyIUIDPolicy(), null);
        writer.writeNotNullOrDef("hl7DicomCharacterSet", ext.getHl7DicomCharacterSet(), null);
        writer.writeNotNullOrDef("hl7VeterinaryUsePatientName", ext.getHl7VeterinaryUsePatientName(), null);
        JsonArchiveConfiguration.writeHL7ForwardRules(writer, ext.getHL7ForwardRules());
        JsonArchiveConfiguration.writeHL7ExportRules(writer, ext.getHL7ExportRules());
        JsonArchiveConfiguration.writeHL7PrefetchRules(writer, ext.getHL7PrefetchRules());
        JsonArchiveConfiguration.writeHL7StudyRetentionPolicies(writer, ext.getHL7StudyRetentionPolicies());
        JsonArchiveConfiguration.writeScheduledStations(writer, ext.getHL7OrderScheduledStations());
        JsonArchiveConfiguration.writeHL7OrderSPSStatus(writer, ext.getHL7OrderSPSStatuses());
        writer.writeEnd();
    }

    @Override
    public boolean loadHL7ApplicationExtension(Device device, HL7Application hl7App, JsonReader reader,
                                               ConfigurationDelegate config) throws ConfigurationException {
        if (!reader.getString().equals("dcmArchiveHL7Application"))
            return false;

        reader.next();
        reader.expect(JsonParser.Event.START_OBJECT);
        ArchiveHL7ApplicationExtension ext = new ArchiveHL7ApplicationExtension();
        loadFrom(ext, reader, config);
        hl7App.addHL7ApplicationExtension(ext);
        reader.expect(JsonParser.Event.END_OBJECT);
        return true;
    }

    private void loadFrom(ArchiveHL7ApplicationExtension ext, JsonReader reader, ConfigurationDelegate config)
            throws ConfigurationException {
        while (reader.next() == JsonParser.Event.KEY_NAME) {
            switch (reader.getString()) {
                case "hl7PatientUpdateTemplateURI":
                    ext.setPatientUpdateTemplateURI(reader.stringValue());
                    break;
                case "hl7ImportReportTemplateURI":
                    ext.setImportReportTemplateURI(reader.stringValue());
                    break;
                case "hl7ImportReportTemplateParam":
                    ext.setImportReportTemplateParams(reader.stringArray());
                    break;
                case "hl7ScheduleProcedureTemplateURI":
                    ext.setScheduleProcedureTemplateURI(reader.stringValue());
                    break;
                case "hl7LogFilePattern":
                    ext.setHL7LogFilePattern(reader.stringValue());
                    break;
                case "hl7ErrorLogFilePattern":
                    ext.setHL7ErrorLogFilePattern(reader.stringValue());
                    break;
                case "dicomAETitle":
                    ext.setAETitle(reader.stringValue());
                    break;
                case "hl7ScheduledProtocolCodeInOrder":
                    ext.setHL7ScheduledProtocolCodeInOrder(ScheduledProtocolCodeInOrder.valueOf(reader.stringValue()));
                    break;
                case "hl7ScheduledStationAETInOrder":
                    ext.setHL7ScheduledStationAETInOrder(ScheduledStationAETInOrder.valueOf(reader.stringValue()));
                    break;
                case "hl7NoPatientCreateMessageType":
                    ext.setHL7NoPatientCreateMessageTypes(reader.stringArray());
                    break;
                case "hl7UseNullValue":
                    ext.setHL7UseNullValue(reader.booleanValue());
                    break;
                case "hl7OrderMissingStudyIUIDPolicy":
                    ext.setHL7OrderMissingStudyIUIDPolicy(HL7OrderMissingStudyIUIDPolicy.valueOf(reader.stringValue()));
                    break;
                case "hl7ImportReportMissingStudyIUIDPolicy":
                    ext.setHl7ImportReportMissingStudyIUIDPolicy(
                            HL7ImportReportMissingStudyIUIDPolicy.valueOf(reader.stringValue()));
                    break;
                case "hl7DicomCharacterSet":
                    ext.setHl7DicomCharacterSet(reader.stringValue());
                    break;
                case "hl7VeterinaryUsePatientName":
                    ext.setHl7VeterinaryUsePatientName(reader.booleanValue());
                    break;
                case "hl7ForwardRule":
                    JsonArchiveConfiguration.loadHL7ForwardRules(ext.getHL7ForwardRules(), reader);
                    break;
                case "hl7ExportRule":
                    JsonArchiveConfiguration.loadHL7ExportRules(ext.getHL7ExportRules(), reader);
                    break;
                case "hl7PrefetchRule":
                    JsonArchiveConfiguration.loadHL7PrefetchRules(ext.getHL7PrefetchRules(), reader);
                    break;
                case "hl7StudyRetentionPolicy":
                    JsonArchiveConfiguration.loadHL7StudyRetentionPolicy(ext.getHL7StudyRetentionPolicies(), reader);
                    break;
                case "hl7OrderScheduledStation":
                    JsonArchiveConfiguration.loadScheduledStations(ext.getHL7OrderScheduledStations(), reader, config);
                    break;
                case "hl7OrderSPSStatus":
                    JsonArchiveConfiguration.loadHL7OrderSPSStatus(ext.getHL7OrderSPSStatuses(), reader);
                    break;
                default:
                    reader.skipUnknownProperty();
            }
        }
    }
}
