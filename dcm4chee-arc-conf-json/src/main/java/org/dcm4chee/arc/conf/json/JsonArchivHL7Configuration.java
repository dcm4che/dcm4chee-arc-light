package org.dcm4chee.arc.conf.json;

import org.dcm4che3.conf.json.JsonReader;
import org.dcm4che3.conf.json.JsonWriter;
import org.dcm4che3.conf.json.hl7.JsonHL7ConfigurationExtension;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.hl7.HL7Application;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jan 2016
 */
public class JsonArchivHL7Configuration implements JsonHL7ConfigurationExtension {
    @Override
    public void storeTo(HL7Application hl7App, Device device, JsonWriter writer) {
        //TODO
    }

    @Override
    public boolean loadHL7ApplicationExtension(Device device, HL7Application hl7App, JsonReader reader) {
        //TODO
        return false;
    }
}
