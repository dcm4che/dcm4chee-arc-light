package org.dcm4chee.arc.conf.json;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.json.ConfigurationDelegate;
import org.dcm4che3.conf.json.JsonReader;
import org.dcm4che3.conf.json.JsonWriter;
import org.dcm4che3.conf.json.hl7.JsonHL7ConfigurationExtension;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4chee.arc.conf.ArchiveHL7ApplicationExtension;

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
        writer.writeNotNull("hl7PatientUpdateTemplateURI", ext.getPatientUpdateTemplateURI());
        writer.writeNotNull("hl7ImportReportTemplateURI", ext.getImportReportTemplateURI());
        writer.writeNotNull("hl7ScheduleProcedureTemplateURI", ext.getScheduleProcedureTemplateURI());
        writer.writeNotNull("hl7LogFilePattern", ext.getHl7LogFilePattern());
        writer.writeNotNull("hl7ErrorLogFilePattern", ext.getHl7ErrorLogFilePattern());
        writer.writeNotNull("dicomAETitle", ext.getAETitle());
        JsonArchiveConfiguration.writeHL7ForwardRules(writer, ext.getHL7ForwardRules());
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
                case "hl7ScheduleProcedureTemplateURI":
                    ext.setScheduleProcedureTemplateURI(reader.stringValue());
                    break;
                case "hl7LogFilePattern":
                    ext.setHl7LogFilePattern(reader.stringValue());
                    break;
                case "hl7ErrorLogFilePattern":
                    ext.setHl7ErrorLogFilePattern(reader.stringValue());
                    break;
                case "dicomAETitle":
                    ext.setAETitle(reader.stringValue());
                    break;
                case "hl7ForwardRule":
                    JsonArchiveConfiguration.loadHL7ForwardRules(ext.getHL7ForwardRules(), reader);
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
