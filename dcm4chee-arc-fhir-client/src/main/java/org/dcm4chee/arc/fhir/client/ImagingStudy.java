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
import jakarta.ws.rs.core.StreamingOutput;
import org.dcm4che3.data.*;
import org.dcm4che3.dcmr.AnatomicRegion;
import org.dcm4che3.net.Device;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Gunter Zeilinger <gunterze@protonmail.com>
 * @since Oct 2025
 */
public enum ImagingStudy {
    FHIR_R5_XML {
        @Override
        public Entity<StreamingOutput> create(Device device, Attributes kosAttrs, Map<String, Attributes> seriesAttrs) {
            return Entity.entity(
                    out -> writeXML(device, kosAttrs, seriesAttrs, out), MediaTypes.APPLICATION_FHIR_XML_TYPE);
        }

        private void writeXML(
                Device device, Attributes kosAttrs, Map<String, Attributes> seriesAttrsByIUID, OutputStream out) {
            SimpleDateFormat ISO_DATE_TIME = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
            ArchiveDeviceExtension arcdev = device.getDeviceExtension(ArchiveDeviceExtension.class);
            Sequence refSeriesSeq = kosAttrs.getNestedDataset(Tag.CurrentRequestedProcedureEvidenceSequence)
                    .getSequence(Tag.ReferencedSeriesSequence);
            try {
                XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newFactory();
                XMLStreamWriter writer = xmlOutputFactory.createXMLStreamWriter(out, "UTF-8");
                writer.writeStartDocument("UTF-8", "1.0");
                writer.writeStartElement("ImagingStudy");
                writer.writeDefaultNamespace("http://hl7.org/fhir");
                writer.writeStartElement("contained");
                writePatient(writer, "p1", kosAttrs, arcdev);
                writer.writeEndElement();
                writeEmptyElement(writer, "status", "value", "available");
                writer.writeStartElement("identifier");
                writeEmptyElement(writer, "system", "value", "urn:dicom:uid");
                writeEmptyElement(writer, "value", "value",
                        "urn:oid:" + kosAttrs.getString(Tag.StudyInstanceUID));
                writer.writeEndElement();
                writer.writeStartElement("subject");
                writeEmptyElement(writer, "reference", "value", "#p1");
                writer.writeEndElement();
                writeAccessionNumber(writer,
                        IDWithIssuer.valueOf(kosAttrs, Tag.AccessionNumber, Tag.IssuerOfAccessionNumberSequence), arcdev);
                writeEmptyElementNotNull(writer, "started", "value",
                        kosAttrs.getDate(Tag.StudyDateAndTime), ISO_DATE_TIME);
                writeEmptyElement(writer, "numberOfSeries", "value", refSeriesSeq.size());
                writeEmptyElement(writer, "numberOfInstances", "value", countInstances(refSeriesSeq));
                for (String modality : listModalities(seriesAttrsByIUID)) {
                    writeModality(writer, modality);
                }
                writeEmptyElementNotNull(writer, "description", "value",
                        kosAttrs.getString(Tag.StudyDescription));
                for (Attributes refSeries : refSeriesSeq) {
                    String seriesIUID = refSeries.getString(Tag.SeriesInstanceUID);
                    Attributes seriesAttrs = seriesAttrsByIUID.get(seriesIUID);
                    Sequence refSOPSeq = refSeries.getSequence(Tag.ReferencedSOPSequence);
                    writer.writeStartElement("series");
                    writeEmptyElement(writer,"uid", "value", seriesIUID);
                    writeEmptyElement(writer,"number", "value",
                            seriesAttrs.getInt(Tag.SeriesNumber, 0));
                    writeModalityNotNull(writer, seriesAttrs.getString(Tag.Modality));
                    writeEmptyElementNotNull(writer, "description", "value",
                            seriesAttrs.getString(Tag.SeriesDescription));
                    writeEmptyElement(writer, "numberOfInstances", "value", refSOPSeq.size());
                    writeBodyPartExamined(writer, seriesAttrs.getString(Tag.BodyPartExamined));
                    writeLaterality(writer, seriesAttrs.getString(Tag.Laterality));
                    writeEmptyElementNotNull(writer, "started", "value",
                            seriesAttrs.getDate(Tag.SeriesDateAndTime), ISO_DATE_TIME);
                    for (Attributes refSOP : refSOPSeq) {
                        writer.writeStartElement("instance");
                        writeEmptyElement(writer,"uid", "value",
                                refSOP.getString(Tag.ReferencedSOPInstanceUID));
                        writer.writeStartElement("sopClass");
                        writeEmptyElement(writer, "system", "value", "urn:ietf:rfc:3986");
                        writeEmptyElement(writer, "value", "value",
                                "urn:oid:" + refSOP.getString(Tag.ReferencedSOPClassUID));
                        writer.writeEndElement();
                        writeEmptyElement(writer,"number", "value",
                                refSOP.getInt(Tag.InstanceNumber, 0));
                        writer.writeEndElement();
                    }
                    writer.writeEndElement();
                }
                writer.writeEndElement();
                writer.writeEndDocument();
                writer.close();
            } catch (XMLStreamException e) {
                LoggerFactory.getLogger(ImagingStudy.class).error("Failed to write XML", e);
                throw new RuntimeException(e);
            }
        }

        private void writeAccessionNumber(XMLStreamWriter writer, IDWithIssuer idWithIssuer, ArchiveDeviceExtension arcdev)
                throws XMLStreamException {
            if (idWithIssuer != null) {
                writer.writeStartElement("basedOn");
                writeEmptyElement(writer, "type", "value", "ServiceRequest");
                writer.writeStartElement("identifier");
                writer.writeStartElement("type");
                writeCoding(writer, "http://terminology.hl7.org/CodeSystem/v2-0203", "ACSN", null);
                writer.writeEndElement();
                writeEmptyElementNotNull(writer, "system", "value",
                        arcdev.fhirSystemOfAccessionNumber(idWithIssuer.getIssuer()));
                writeEmptyElement(writer, "value", "value", idWithIssuer.getID());
                writer.writeEndElement();
                writer.writeEndElement();
            }
        }

        private void writeModalityNotNull(XMLStreamWriter writer, String modality) throws XMLStreamException {
            if (modality != null) writeModality(writer, modality);
        }

        private void writeModality(XMLStreamWriter writer, String modality) throws XMLStreamException {
            writer.writeStartElement("modality");
            writeCoding(writer, "http://dicom.nema.org/resources/ontology/DCM", modality, null);
            writer.writeEndElement();
        }

        private void writeBodyPartExamined(XMLStreamWriter writer, String bodyPartExamined) throws XMLStreamException {
            Code code = bodyPartExamined != null ? AnatomicRegion.codeOf(bodyPartExamined) : null;
            if (code != null) {
                writer.writeStartElement("bodySite");
                writer.writeStartElement("concept");
                writeCoding(writer, "http://snomed.info/sct", code.getCodeValue(), code.getCodeMeaning());
                writer.writeEndElement();
                writer.writeEndElement();
            }
        }

        private void writeLaterality(XMLStreamWriter writer, String laterality) throws XMLStreamException {
            boolean l;
            if (laterality != null && ((l = laterality.equals("L")) || laterality.equals("R"))) {
                writer.writeStartElement("laterality");
                if (l)
                    writeCoding(writer, "http://snomed.info/sct", "7771000", "Left");
                else {
                    writeCoding(writer, "http://snomed.info/sct", "24028007", "Right");
                }
                writer.writeEndElement();
            }
        }
    },
    LTNHR_V1_XML {
        @Override
        public Entity<StreamingOutput> create(Device device, Attributes kosAttrs, Map<String, Attributes> seriesAttrs) {
            return Entity.entity(
                    out -> writeXML(device, kosAttrs, seriesAttrs, out), MediaTypes.APPLICATION_FHIR_XML_TYPE);
        }

        private void writeXML(
                Device device, Attributes kosAttrs, Map<String, Attributes> seriesAttrsByIUID, OutputStream out) {
            SimpleDateFormat ISO_DATE_TIME = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
            ArchiveDeviceExtension arcdev = device.getDeviceExtension(ArchiveDeviceExtension.class);
            Sequence refSeriesSeq = kosAttrs.getNestedDataset(Tag.CurrentRequestedProcedureEvidenceSequence)
                    .getSequence(Tag.ReferencedSeriesSequence);
            try {
                XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newFactory();
                XMLStreamWriter writer = xmlOutputFactory.createXMLStreamWriter(out, "UTF-8");
                writer.writeStartDocument("UTF-8", "1.0");
                writer.writeStartElement("ImagingStudy");
                writer.writeDefaultNamespace("http://hl7.org/fhir");
                writer.writeStartElement("contained");
                writePatient(writer, "p1", kosAttrs, arcdev);
                writer.writeEndElement();
                writeEmptyElement(writer, "uid", "value",
                        "urn:oid:" + kosAttrs.getString(Tag.StudyInstanceUID));
                writer.writeStartElement("subject");
                writeEmptyElement(writer, "reference", "value", "#p1");
                writer.writeEndElement();
                writeExtension(writer, "http://esveikata.lt/Profile/ltnhr-imagingstudy#achi-code",
                        kosAttrs.getString(Tag.AccessionNumber));
                writeEmptyElementNotNull(writer, "datetime", "value",
                        kosAttrs.getDate(Tag.StudyDateAndTime), ISO_DATE_TIME);
                writeEmptyElement(writer, "numberOfSeries", "value", refSeriesSeq.size());
                writeEmptyElement(writer, "numberOfInstances", "value", countInstances(refSeriesSeq));
                writeEmptyElementNotNull(writer, "description", "value", kosAttrs.getString(Tag.StudyDescription));
                for (Attributes refSeries : refSeriesSeq) {
                    String seriesIUID = refSeries.getString(Tag.SeriesInstanceUID);
                    Attributes seriesAttrs = seriesAttrsByIUID.get(seriesIUID);
                    Sequence refSOPSeq = refSeries.getSequence(Tag.ReferencedSOPSequence);
                    writer.writeStartElement("series");
                    writeEmptyElement(writer,"uid", "value", "urn:oid:" + seriesIUID);
                    writeEmptyElement(writer,"number", "value",
                            seriesAttrs.getInt(Tag.SeriesNumber, 0));
                    writeEmptyElementNotNull(writer, "modality", "value",
                            seriesAttrs.getString(Tag.Modality));
                    writeEmptyElementNotNull(writer, "description", "value",
                            seriesAttrs.getString(Tag.SeriesDescription));
                    writeEmptyElement(writer, "numberOfInstances", "value", refSOPSeq.size());
                    for (Attributes refSOP : refSOPSeq) {
                        writer.writeStartElement("instance");
                        writeEmptyElement(writer,"uid", "value",
                                "urn:oid:" + refSOP.getString(Tag.ReferencedSOPInstanceUID));
                        writeEmptyElement(writer,"sopclass", "value",
                                "urn:oid:" + refSOP.getString(Tag.ReferencedSOPClassUID));
                        writeEmptyElement(writer,"number", "value",
                                refSOP.getInt(Tag.InstanceNumber, 0));
                        writer.writeEndElement();
                    }
                    writer.writeEndElement();
                }
                writer.writeEndElement();
                writer.writeEndDocument();
                writer.close();
            } catch (XMLStreamException e) {
                LoggerFactory.getLogger(ImagingStudy.class).error("Failed to write XML", e);
                throw new RuntimeException(e);
            }
        }

        private void writeExtension(XMLStreamWriter writer, String url, String value)
                throws XMLStreamException {
            if (value != null) {
                writer.writeStartElement("extension");
                writer.writeAttribute("url", url);
                writeEmptyElement(writer, "valueString", "value", value);
                writer.writeEndElement();
            }
        }
    },
    FHIR_R5_JSON {
        @Override
        public Entity<StreamingOutput> create(Device device, Attributes kosAttrs, Map<String, Attributes> seriesAttrs) {
            return Entity.entity(
                    out -> writeJSON(device, kosAttrs, seriesAttrs, out), MediaTypes.APPLICATION_FHIR_JSON_TYPE);
        }

        private void writeJSON(
                Device device, Attributes kosAttrs, Map<String, Attributes> seriesAttrsByIUID, OutputStream out) {
            ArchiveDeviceExtension arcdev = device.getDeviceExtension(ArchiveDeviceExtension.class);
            Sequence refSeriesSeq = kosAttrs.getNestedDataset(Tag.CurrentRequestedProcedureEvidenceSequence)
                    .getSequence(Tag.ReferencedSeriesSequence);
            try (JsonGenerator gen = Json.createGenerator(out)) {
                SimpleDateFormat ISO_DATE_TIME = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
                gen.writeStartObject();
                gen.write("resourceType", "ImagingStudy");
                gen.writeStartArray("contained");
                writePatient(gen, "p1", kosAttrs, arcdev);
                gen.writeEnd();
                gen.write("status", "available");
                gen.writeStartArray("identifier");
                gen.writeStartObject();
                gen.write("system", "urn:dicom:uid");
                gen.write("value", "urn:oid:" + kosAttrs.getString(Tag.StudyInstanceUID));
                gen.writeEnd();
                gen.writeEnd();
                gen.writeStartObject("subject");
                gen.write("reference", "#p1");
                gen.writeEnd();
                writeAccessionNumber(gen, IDWithIssuer.valueOf(kosAttrs, Tag.AccessionNumber, Tag.IssuerOfAccessionNumberSequence), arcdev);
                writeNotNull(gen, "started", kosAttrs.getDate(Tag.StudyDateAndTime), ISO_DATE_TIME);
                gen.write("numberOfSeries", refSeriesSeq.size());
                gen.write("numberOfInstances", countInstances(refSeriesSeq));
                writeModalities(gen, listModalities(seriesAttrsByIUID));
                writeNotNull(gen, "description", kosAttrs.getString(Tag.StudyDescription));
                gen.writeStartArray("series");
                for (Attributes refSeries : refSeriesSeq) {
                    String seriesIUID = refSeries.getString(Tag.SeriesInstanceUID);
                    Attributes seriesAttrs = seriesAttrsByIUID.get(seriesIUID);
                    Sequence refSOPSeq = refSeries.getSequence(Tag.ReferencedSOPSequence);
                    gen.writeStartObject();
                    gen.write("uid", seriesIUID);
                    gen.write("number", seriesAttrs.getInt(Tag.SeriesNumber, 0));
                    writeModality(gen, seriesAttrs.getString(Tag.Modality));
                    writeNotNull(gen, "description", seriesAttrs.getString(Tag.SeriesDescription));
                    gen.write("numberOfInstances", refSOPSeq.size());
                    writeBodyPartExamined(gen, seriesAttrs.getString(Tag.BodyPartExamined));
                    writeLaterality(gen, seriesAttrs.getString(Tag.Laterality));
                    writeNotNull(gen, "started", seriesAttrs.getDate(Tag.SeriesDateAndTime), ISO_DATE_TIME);
                    gen.writeStartArray("instance");
                    for (Attributes refSOP : refSOPSeq) {
                        gen.writeStartObject();
                        gen.write("uid", refSOP.getString(Tag.ReferencedSOPInstanceUID));
                        gen.writeStartObject("sopClass");
                        gen.write("system", "urn:ietf:rfc:3986");
                        gen.write("code", "urn:oid:" + refSOP.getString(Tag.ReferencedSOPClassUID));
                        gen.writeEnd();
                        gen.write("number", refSOP.getInt(Tag.InstanceNumber, 0));
                        gen.writeEnd();
                    }
                    gen.writeEnd();
                    gen.writeEnd();
                }
                gen.writeEnd();
                gen.writeEnd();
            } catch (RuntimeException e) {
                LoggerFactory.getLogger(ImagingStudy.class).error("Failed to write JSON", e);
                throw e;
            }
        }

        private void writePatient(JsonGenerator gen, String id, Attributes kosAttrs, ArchiveDeviceExtension arcdev) {
            gen.writeStartObject();
            gen.write("resourceType", "Patient");
            gen.write("id", id);
            writePatientIDs(gen, ImagingStudy.preferredPatientIDs(kosAttrs, arcdev), arcdev);
            writePatientName(gen, kosAttrs.getString(Tag.PatientName));
            ImagingStudy.writeNotNull(gen, "gender", toGender(kosAttrs.getString(Tag.PatientSex)));
            ImagingStudy.writeNotNull(gen, "birthDate", toDate(kosAttrs.getString(Tag.PatientBirthDate)));
            gen.writeEnd();
        }

        private void writePatientIDs(JsonGenerator gen, Set<IDWithIssuer> idWithIssuers, ArchiveDeviceExtension arcdev) {
            if (!idWithIssuers.isEmpty()) {
                gen.writeStartArray("identifier");
                for (IDWithIssuer idWithIssuer : idWithIssuers) {
                    gen.writeStartObject();
                    gen.write("type", "Patient");
                    gen.writeStartObject("identifier");
                    writeNotNull(gen, "system", arcdev.fhirSystemOfPatientID(idWithIssuer.getIssuer()));
                    gen.write("value", idWithIssuer.getID());
                    gen.writeEnd();
                    gen.writeEnd();
                }
                gen.writeEnd();
            }
        }

        private void writePatientName(JsonGenerator gen, String value) {
            if (value != null) {
                PersonName name = new PersonName(value, true);
                String family = name.get(PersonName.Component.FamilyName);
                String given = name.get(PersonName.Component.GivenName);
                if (family != null || given != null) {
                    gen.writeStartArray("name");
                    gen.writeStartObject();
                    ImagingStudy.writeNotNull(gen, "family", family);
                    if (given != null) {
                        gen.writeStartArray("given");
                        StringTokenizer tokens = new StringTokenizer(given, " ");
                        while (tokens.hasMoreTokens()) {
                            gen.write(tokens.nextToken());
                        }
                        gen.writeEnd();
                    }
                    gen.writeEnd();
                    gen.writeEnd();
                }
            }
        }

        private void writeAccessionNumber(JsonGenerator gen, IDWithIssuer idWithIssuer, ArchiveDeviceExtension arcdev) {
            if (idWithIssuer != null) {
                gen.writeStartArray("basedOn");
                gen.writeStartObject();
                gen.write("type", "ServiceRequest");
                writeAccessionNumber(gen, "identifier", idWithIssuer, arcdev);
                gen.writeEnd();
                gen.writeEnd();
            }
        }

        private void writeModalities(JsonGenerator gen, Collection<String> modalities) {
            if (!modalities.isEmpty()) {
                gen.writeStartArray("modality");
                for (String modality : modalities) {
                    gen.writeStartObject();
                    writeCoding(gen, "http://dicom.nema.org/resources/ontology/DCM", modality, null);
                    gen.writeEnd();
                }
                gen.writeEnd();
            }
        }

        private void writeModality(JsonGenerator gen, String modality) {
            if (modality != null) {
                gen.writeStartObject("modality");
                writeCoding(gen, "http://dicom.nema.org/resources/ontology/DCM", modality, null);
                gen.writeEnd();
            }
        }

        private void writeBodyPartExamined(JsonGenerator gen, String bodyPartExamined) {
            Code code = bodyPartExamined != null ? AnatomicRegion.codeOf(bodyPartExamined) : null;
            if (code != null) {
                gen.writeStartObject("bodySite");
                gen.writeStartObject("concept");
                writeCoding(gen, "http://snomed.info/sct", code.getCodeValue(), code.getCodeMeaning());
                gen.writeEnd();
                gen.writeEnd();
            }
        }

        private void writeLaterality(JsonGenerator gen, String laterality) {
            boolean l;
            if (laterality != null && ((l = laterality.equals("L")) || laterality.equals("R"))) {
                gen.writeStartObject("laterality");
                if (l)
                    writeCoding(gen, "http://snomed.info/sct", "7771000", "Left");
                else {
                    writeCoding(gen, "http://snomed.info/sct", "24028007", "Right");
                }
                gen.writeEnd();
            }
        }
    };

    private static String toDate(String value) {
        return value == null ? null : switch (value.length()) {
            case 8 -> value.substring(0, 4) + '-' + value.substring(4, 6) + '-' + value.substring(6);
            case 10 -> value;
            default -> null;
        };
    }

    private static String toGender(String value) {
        return value == null ? null : switch (value) {
            case "M" -> "male";
            case "F" -> "female";
            case "O" -> "other";
            default -> null;
        };
    }

    private static int countInstances(Sequence refSeriesSeq) {
        return refSeriesSeq.stream()
                .mapToInt(refSeries -> refSeries.getSequence(Tag.ReferencedSOPSequence).size())
                .sum();
    }

    private static void writeCoding(XMLStreamWriter writer, String system, String code, String display)
            throws XMLStreamException {
        writer.writeStartElement("coding");
        writeEmptyElement(writer, "system", "value", system);
        writeEmptyElement(writer, "code", "value", code);
        if (display != null) writeEmptyElement(writer, "display", "value", display);
        writer.writeEndElement();
    }

    private static void writePatient(XMLStreamWriter writer, String id, Attributes kosAttrs,
                                     ArchiveDeviceExtension arcdev)
            throws XMLStreamException {
        writer.writeStartElement("Patient");
        writeEmptyElement(writer, "id", "value", id);
        writePatientIDs(writer, ImagingStudy.preferredPatientIDs(kosAttrs, arcdev), arcdev);
        writePatientName(writer, kosAttrs.getString(Tag.PatientName));
        writeEmptyElementNotNull(writer, "gender", "value",
                toGender(kosAttrs.getString(Tag.PatientSex)));
        writeEmptyElementNotNull(writer, "birthDate", "value",
                toDate(kosAttrs.getString(Tag.PatientBirthDate)));
        writer.writeEndElement();
    }

    private static void writePatientIDs(XMLStreamWriter writer, Set<IDWithIssuer> idWithIssuers,
                                        ArchiveDeviceExtension arcdev)
            throws XMLStreamException {
        for (IDWithIssuer idWithIssuer : idWithIssuers) {
            writer.writeStartElement("identifier");
            writeEmptyElementNotNull(writer, "system", "value",
                    arcdev.fhirSystemOfPatientID(idWithIssuer.getIssuer()));
            writeEmptyElement(writer, "value", "value", idWithIssuer.getID());
            writer.writeEndElement();
        }
    }

    private static void writePatientName(XMLStreamWriter writer, String value) throws XMLStreamException {
        if (value != null) {
            PersonName name = new PersonName(value, true);
            String family = name.get(PersonName.Component.FamilyName);
            String given = name.get(PersonName.Component.GivenName);
            if (family != null || given != null) {
                writer.writeStartElement("name");
                writeEmptyElementNotNull(writer, "family", "value", family);
                StringTokenizer tokens = new StringTokenizer(given, " ");
                while (tokens.hasMoreTokens()) {
                    writeEmptyElement(writer, "given", "value", tokens.nextToken());
                }
                writer.writeEndElement();
            }
        }
    }

    static void writeAccessionNumber(JsonGenerator gen, String name, IDWithIssuer idWithIssuer, ArchiveDeviceExtension arcdev) {
        gen.writeStartObject(name);
        gen.writeStartObject("type");
        writeCoding(gen, "http://terminology.hl7.org/CodeSystem/v2-0203", "ACSN", null);
        gen.writeEnd();
        writeNotNull(gen, "system", arcdev.fhirSystemOfAccessionNumber(idWithIssuer.getIssuer()));
        gen.write("value", idWithIssuer.getID());
        gen.writeEnd();
    }

    private static void writeCoding(JsonGenerator gen, String system, String code, String display) {
        gen.writeStartArray("coding");
        gen.writeStartObject();
        gen.write("system", system);
        gen.write("code", code);
        writeNotNull(gen,"display", display);
        gen.writeEnd();
        gen.writeEnd();
    }

    private static Collection<String> listModalities(Map<String, Attributes> seriesAttrsByIUID) {
        return seriesAttrsByIUID.values().stream()
                .map(attrs -> attrs.getString(Tag.Modality))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private static Set<IDWithIssuer> preferredPatientIDs(Attributes kosAttrs, ArchiveDeviceExtension arcdev) {
        Set<IDWithIssuer> idWithIssuers = IDWithIssuer.pidsOf(kosAttrs);
        Issuer[] preferreds = arcdev.getFhirPreferredAssigningAuthorityOfPatientID();
        return preferreds.length > 0
                ? idWithIssuers.stream()
                    .filter(idWithIssuer -> Stream.of(preferreds).anyMatch(
                            preferred -> preferred.matches(idWithIssuer.getIssuer())))
                    .collect(Collectors.toSet())
                : idWithIssuers;
    }

    private static void writeNotNull(JsonGenerator gen, String name, Date value, DateFormat dateFormat) {
        if (value != null) gen.write(name, dateFormat.format(value));
    }

    private static void writeNotNull(JsonGenerator gen, String name, String value) {
        if (value != null) gen.write(name, value);
    }

    private static void writeEmptyElementNotNull(XMLStreamWriter writer, String elmName, String attrName,
                                                 Date value, DateFormat dateFormat) throws XMLStreamException {
        if (value != null) writeEmptyElement(writer, elmName, attrName, dateFormat.format(value));
    }

    private static void writeEmptyElementNotNull(XMLStreamWriter writer, String elmName, String attrName, Object value)
            throws XMLStreamException {
        if (value != null) writeEmptyElement(writer, elmName, attrName, value);
    }

    private static void writeEmptyElement(XMLStreamWriter writer, String elmName, String attrName, Object value)
            throws XMLStreamException {
        writer.writeEmptyElement(elmName);
        writer.writeAttribute(attrName, value.toString());
    }

    public abstract Entity<StreamingOutput> create(
            Device device, Attributes kosAttrs, Map<String, Attributes> seriesAttrs);
}
