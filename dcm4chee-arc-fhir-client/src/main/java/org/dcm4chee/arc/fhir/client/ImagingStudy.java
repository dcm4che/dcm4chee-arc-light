/*
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
 * Portions created by the Initial Developer are Copyright (C) 2015-2018
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
 */

package org.dcm4chee.arc.fhir.client;

import jakarta.json.Json;
import jakarta.json.stream.JsonGenerator;
import jakarta.ws.rs.client.Entity;
import org.dcm4che3.data.*;
import org.dcm4che3.dcmr.AnatomicRegion;
import org.dcm4che3.dcmr.AcquisitionModality;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.fhir.util.FHIRBuilder;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Gunter Zeilinger <gunterze@protonmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Oct 2025
 */
public enum ImagingStudy {
    FHIR_R5_XML {
        @Override
        public Entity<byte[]> create(ArchiveDeviceExtension arcdev, List<Attributes> instances)
                throws XMLStreamException {
            return Entity.entity(writeXML(arcdev, instances), MediaTypes.APPLICATION_FHIR_XML_TYPE);
        }

       private byte[] writeXML(ArchiveDeviceExtension arcdev, List<Attributes> instances) throws XMLStreamException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newFactory();
            XMLStreamWriter writer = xmlOutputFactory.createXMLStreamWriter(baos, "UTF-8");
            writer.writeStartDocument("UTF-8", "1.0");
            new FHIRBuilder.XML(arcdev, writer).writeImagingStudy(instances, false);
            writer.writeEndDocument();
            writer.close();
            return baos.toByteArray();
        }
    },

    LTNHR_V1_XML {
        @Override
        public Entity<byte[]> create(ArchiveDeviceExtension arcdev, List<Attributes> instances)
                throws XMLStreamException {
            return Entity.entity(writeXML(arcdev, instances), MediaTypes.APPLICATION_FHIR_XML_TYPE);
        }


        private byte[] writeXML(ArchiveDeviceExtension arcdev, List<Attributes> instances) throws XMLStreamException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newFactory();
            XMLStreamWriter writer = xmlOutputFactory.createXMLStreamWriter(baos, "UTF-8");
            writer.writeStartDocument("UTF-8", "1.0");
            new FHIRBuilder.XML(arcdev, writer).writeImagingStudy(instances, true);
            writer.writeEndDocument();
            writer.close();
            return baos.toByteArray();
        }
    },

    FHIR_R5_JSON {
        @Override
        public Entity<byte[]> create(ArchiveDeviceExtension arcdev, List<Attributes> instances) {
            return Entity.entity(writeJSON(arcdev, instances), MediaTypes.APPLICATION_FHIR_JSON_TYPE);
        }

        private byte[] writeJSON(ArchiveDeviceExtension arcdev, List<Attributes> instances) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (JsonGenerator gen = Json.createGenerator(baos)) {
                new FHIRBuilder.JSON(arcdev, gen).writeImagingStudy(instances);
            }
            return baos.toByteArray();
        }
    };

    public abstract Entity<byte[]> create(ArchiveDeviceExtension arcdev, List<Attributes> instances) throws XMLStreamException;
}
