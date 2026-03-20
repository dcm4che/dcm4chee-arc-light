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
        public Entity<byte[]> create(ArchiveDeviceExtension arcdev, List<Attributes> instances) {
            return Entity.entity(writeXML(arcdev, instances), MediaTypes.APPLICATION_FHIR_XML_TYPE);
        }

/*
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
*/

        private byte[] writeXML(ArchiveDeviceExtension arcdev, List<Attributes> instances) {
            Attributes study = instances.get(0);
            Collection<List<Attributes>> instancesBySeries = instances.stream()
                    .collect(Collectors.groupingBy(attrs -> attrs.getString(Tag.SeriesInstanceUID)))
                    .values();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newFactory();
                XMLStreamWriter writer = xmlOutputFactory.createXMLStreamWriter(baos, "UTF-8");
                writer.writeStartDocument("UTF-8", "1.0");
                writer.writeStartElement("ImagingStudy");
                writer.writeDefaultNamespace("http://hl7.org/fhir");
                writeOrganization(writer, "Organization1", arcdev.getDevice(), study.getString(Tag.RetrieveAETitle));
                writeExtension(writer, "http://esveikata.lt/Profile/ltnhr-imagingstudy#reported",
                        "valueBoolean", "false");
                writeEmptyElementNotNull(writer, "status", "value", "available");
                writer.writeStartElement("modality");
                for (String modality : listModalities(instancesBySeries))
                    writeModality(writer, modality);
                writer.writeEndElement();
                writeStarted(writer, study.getDate(Tag.StudyDateAndTime), null);
                writeAccessionNo(writer, study);
                writePatient(writer, "patient1", study, arcdev);
                writeStudyIUID(writer, study);
                int practitionerNo = 0;
                writeReferringPhysician(writer, study, practitionerNo++);
                writeEmptyElement(writer, "numberOfSeries", "value", instancesBySeries.size());
                writeEmptyElement(writer, "numberOfInstances", "value", instances.size());
                writeEmptyElementNotNull(writer, "description", "value", study.getString(Tag.StudyDescription));
                writeCode(writer, study, Tag.ProcedureCodeSequence, "procedure");
                writeCode(writer, study, Tag.ReasonForPerformedProcedureCodeSequence, "reason");
                for (List<Attributes> seriesOfInstances : instancesBySeries) {
                    Attributes series = seriesOfInstances.get(0);
                    String seriesIUID = series.getString(Tag.SeriesInstanceUID);
                    writer.writeStartElement("series");
                    writeEmptyElement(writer,"uid", "value", "urn:oid:" + seriesIUID);
                    writeEmptyElement(writer,"number", "value",
                            series.getInt(Tag.SeriesNumber, 0));
                    writer.writeStartElement("modality");
                    writeModality(writer, series.getString(Tag.Modality));
                    writer.writeEndElement();
                    writeEmptyElementNotNull(writer, "description", "value",
                            series.getString(Tag.SeriesDescription));
                    writeEmptyElement(writer, "numberOfInstances", "value", seriesOfInstances.size());
                    writePerformingPhysician(writer, series, practitionerNo++);
                    writeBodyPart(writer, series);
                    writeLaterality(writer, series.getString(Tag.Laterality));
                    writeStarted(writer, series.getDate(Tag.AcquisitionDateTime), series.getDate(Tag.SeriesDateAndTime));
                    for (Attributes inst : seriesOfInstances) {
                        writer.writeStartElement("instance");
                        writeEmptyElement(writer,"uid", "value",
                                "urn:oid:" + inst.getString(Tag.SOPInstanceUID));
                        writer.writeStartElement("sopClass");
                        writeEmptyElement(writer, "system", "value", "urn:ietf:rfc:3986");
                        writeEmptyElement(writer, "code", "value",
                                "urn:oid:" + inst.getString(Tag.SOPClassUID));
                        writer.writeEndElement();
                        writeEmptyElement(writer,"number", "value", inst.getInt(Tag.InstanceNumber, 0));
                        Attributes conceptNameCode = inst.getNestedDataset(Tag.ConceptNameCodeSequence);
                        if (conceptNameCode != null)
                            writeEmptyElement(writer,"title", "value", conceptNameCode.getString(Tag.CodeValue));
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
            return baos.toByteArray();
        }

        private void writeExtension(XMLStreamWriter writer, String url, String valueType, String value)
                throws XMLStreamException {
            if (value != null) {
                writer.writeStartElement("extension");
                writer.writeAttribute("url", url);
                writeEmptyElement(writer, valueType, "value", value);
                writer.writeEndElement();
            }
        }

        private static void writePatient(XMLStreamWriter writer, String id, Attributes kosAttrs,
                                         ArchiveDeviceExtension arcdev)
                throws XMLStreamException {
            writer.writeStartElement("contained");
            writer.writeStartElement("Patient");
            writer.writeAttribute("id", id);
            writePatientName(writer, kosAttrs.getString(Tag.PatientName));
            writePatientIDs(writer, ImagingStudy.preferredPatientIDs(kosAttrs, arcdev), arcdev);
            writeEmptyElementNotNull(writer, "birthDate", "value",
                    toDate(kosAttrs.getString(Tag.PatientBirthDate)));
            writeEmptyElementNotNull(writer, "gender", "value",
                    toGender(kosAttrs.getString(Tag.PatientSex)));
            writer.writeEndElement();
            writer.writeEndElement();
            writer.writeStartElement("subject");
            writeEmptyElementNotNull(writer, "reference", "value", "#patient1");
            writer.writeEndElement();
        }

        private static void writePatientName(XMLStreamWriter writer, String value) throws XMLStreamException {
            if (value != null) {
                PersonName name = new PersonName(value, true);
                String family = name.get(PersonName.Component.FamilyName);
                String given = name.get(PersonName.Component.GivenName);
                if (family != null || given != null) {
                    writer.writeStartElement("name");
                    writeEmptyElementNotNull(writer, "text", "value", given + " " + family);
                    writeEmptyElementNotNull(writer, "family", "value", family);
                    StringTokenizer tokens = new StringTokenizer(given, " ");
                    while (tokens.hasMoreTokens()) {
                        writeEmptyElement(writer, "given", "value", tokens.nextToken());
                    }
                    writer.writeEndElement();
                }
            }
        }

        private static void writeStudyIUID(XMLStreamWriter writer, Attributes study) throws XMLStreamException {
            writer.writeStartElement("identifier");
            writeEmptyElement(writer, "use", "value", "official");
            writeEmptyElement(writer, "system", "value", "urn:dicom:uid");
            writeEmptyElement(writer, "value", "value",
                    "urn:oid:" + study.getString(Tag.StudyInstanceUID));
            writer.writeEndElement();
        }

        private static void writeReferringPhysician(XMLStreamWriter writer, Attributes kosAttrs, int practitionerNo)
                throws XMLStreamException {
            String referringPhysician = kosAttrs.getString(Tag.ReferringPhysicianName);
            if (referringPhysician == null)
                return;

            String practitionerID = "Practitioner" + practitionerNo;
            writer.writeStartElement("referrer");
            writeEmptyElement(writer, "reference", "value", "#" + practitionerID);
            writer.writeEndElement();

            writer.writeStartElement("contained");
            writer.writeStartElement("Practitioner");
            writer.writeAttribute("id", practitionerID);
            PersonName name = new PersonName(referringPhysician, true);
            String family = name.get(PersonName.Component.FamilyName);
            String given = name.get(PersonName.Component.GivenName);
            if (family != null || given != null) {
                writer.writeStartElement("name");
                writeEmptyElementNotNull(writer, "text", "value", given + " " + family);
                writer.writeEndElement();
            }
            writer.writeEndElement();
            writer.writeEndElement();
        }

        private static void writeAccessionNo(XMLStreamWriter writer, Attributes kosAttrs) throws XMLStreamException {
            String accessionNo = kosAttrs.getString(Tag.AccessionNumber);
            if (accessionNo == null)
                return;

            Attributes accessionNoIssuer = kosAttrs.getNestedDataset(Tag.IssuerOfAccessionNumberSequence);
            writer.writeStartElement("basedOn");
            writeEmptyElement(writer, "type", "value", "ServiceRequest");
            writer.writeStartElement("identifier");
            writer.writeStartElement("type");
            writer.writeStartElement("coding");
            writeEmptyElement(writer, "system", "value", "http://terminology.hl7.org/CodeSystem/v2-0203");
            writeEmptyElement(writer, "code", "value", "ASCN");
            writer.writeEndElement();
            writer.writeEndElement();
            writeEmptyElement(writer, "value", "value", accessionNo);
            if (accessionNoIssuer != null) {
                String accessionNoIssuerLocal = accessionNoIssuer.getString(Tag.LocalNamespaceEntityID);
                String accessionNoIssuerUniversal = accessionNoIssuer.getString(Tag.UniversalEntityID);
                writer.writeStartElement("assigner");
                if (accessionNoIssuerLocal != null)
                    writeEmptyElement(writer, "display", "value", accessionNoIssuerLocal);
                if (accessionNoIssuerUniversal != null)
                    writeEmptyElement(writer, "system", "value", "urn:oid:" + accessionNoIssuer.getString(Tag.UniversalEntityID));
                writer.writeEndElement();
            }
            writer.writeEndElement();
            writer.writeEndElement();
        }

        private static void writeCode(XMLStreamWriter writer, Attributes kosAttrs, int tag, String startElementName) throws XMLStreamException {
            Attributes code = kosAttrs.getNestedDataset(tag);
            if (code == null)
                return;

            writer.writeStartElement(startElementName);
            writer.writeStartElement("concept");
            writer.writeStartElement("coding");
            writeEmptyElement(writer, "system", "value",
                    StringUtils.maskNull(code.getString(Tag.CodingSchemeDesignator), "http://snomed.info/sct"));
            writeEmptyElement(writer, "code", "value", code.getString(Tag.CodeValue));
            writeEmptyElement(writer, "display", "value", code.getString(Tag.CodeMeaning));
            writer.writeEndElement();
            writeEmptyElement(writer, "text", "value", code.getString(Tag.CodeMeaning));
            writer.writeEndElement();
            writer.writeEndElement();
        }

        private static void writeModality(XMLStreamWriter writer, String modality) throws XMLStreamException {
            writer.writeStartElement("coding");
            writeEmptyElement(writer, "system", "value", "http://dicom.nema.org/resources/ontology/DCM");
            writeEmptyElement(writer, "code", "value", modality);
            Code modalityCode = AcquisitionModality.codeOf(modality);
            if (modalityCode != null)
                writeEmptyElement(writer, "display", "value", modalityCode.getCodeMeaning());
            writer.writeEndElement();
        }

        private static void writeBodyPart(XMLStreamWriter writer, Attributes seriesAttrs) throws XMLStreamException {
            writer.writeStartElement("bodySite");
            writer.writeStartElement("concept");
            writer.writeStartElement("coding");
            writeEmptyElement(writer, "system", "value", "http://snomed.info/sct");
            String bodyPartExamined = seriesAttrs.getString(Tag.BodyPartExamined);
            Code bodyPartCode = AnatomicRegion.codeOf(bodyPartExamined.toUpperCase());
            if (bodyPartCode != null)
                writeEmptyElement(writer, "code", "value", bodyPartCode.getCodeValue());
            writeEmptyElement(writer, "display", "value", bodyPartExamined);
            writer.writeEndElement();
            writeEmptyElement(writer, "text", "value", bodyPartExamined);
            writer.writeEndElement();
            writer.writeEndElement();
        }

        private static void writePerformingPhysician(XMLStreamWriter writer, Attributes seriesAttrs, int practitionerNo)
                throws XMLStreamException {
            String performingPhysician = seriesAttrs.getString(Tag.PerformingPhysicianName);
            if (performingPhysician == null)
                return;

            String practitionerID = "Practitioner" + practitionerNo;
            writer.writeStartElement("performer");
            writer.writeStartElement("function");
            writer.writeStartElement("coding");
            writeEmptyElement(writer, "system", "value", "http://terminology.hl7.org/CodeSystem/v3-ParticipationType");
            writeEmptyElement(writer, "code", "value", "PRF");
            writer.writeEndElement();
            writer.writeEndElement();
            writer.writeStartElement("actor");
            writeEmptyElement(writer, "reference", "value", "#" + practitionerID);
            writer.writeEndElement();
            writer.writeEndElement();

            writer.writeStartElement("contained");
            writer.writeStartElement("Practitioner");
            writer.writeAttribute("id", practitionerID);
            PersonName name = new PersonName(performingPhysician, true);
            String family = name.get(PersonName.Component.FamilyName);
            String given = name.get(PersonName.Component.GivenName);
            if (family != null || given != null) {
                writer.writeStartElement("name");
                writeEmptyElementNotNull(writer, "text", "value", given + " " + family);
                writer.writeEndElement();
            }
            writer.writeEndElement();
            writer.writeEndElement();
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

    private static void writeCoding(XMLStreamWriter writer, String system, String code, String display)
            throws XMLStreamException {
        writer.writeStartElement("coding");
        writeEmptyElement(writer, "system", "value", system);
        writeEmptyElement(writer, "code", "value", code);
        if (display != null) writeEmptyElement(writer, "display", "value", display);
        writer.writeEndElement();
    }

    private static void writeStarted(XMLStreamWriter writer, Date val, Date defVal) throws XMLStreamException {
        if (val == null && defVal == null)
            return;

        SimpleDateFormat ISO_DATE_TIME = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
        writeEmptyElementNotNull(writer, "started", "value", val == null ? defVal : val, ISO_DATE_TIME);
    }

    private static void writeLaterality(XMLStreamWriter writer, String laterality) throws XMLStreamException {
        boolean l;
        if (laterality != null && ((l = laterality.equals("L")) || laterality.equals("R"))) {
            writer.writeStartElement("laterality");
            if (l)
                writeCoding(writer, "http://snomed.info/sct", "7771000", "Unilateral Left");
            else {
                writeCoding(writer, "http://snomed.info/sct", "24028007", "Right");
            }
            writer.writeEndElement();
        }
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

    private static void writeOrganization(XMLStreamWriter writer, String id, Device device, String retrieveAET)
            throws XMLStreamException {
        ApplicationEntity retrieveAE = device.getApplicationEntity(retrieveAET);
        if (retrieveAE == null || !retrieveAE.isInstalled()) {
            LoggerFactory.getLogger(ImagingStudy.class).info("No Application Entity found for Retrieve AE Title : ", retrieveAET);
            return;
        }

        writer.writeStartElement("extension");
        writer.writeAttribute("url", "http://esveikata.lt/Profile/ltnhr-imagingstudy#organization");
        writer.writeStartElement("valueResource");
        writeEmptyElement(writer, "reference", "value", "#" + id);
        writer.writeEndElement();
        writer.writeEndElement();

        writer.writeStartElement("contained");
        writer.writeStartElement("Organization");
        writer.writeAttribute("id", id);
        writer.writeStartElement("name");
        writeEmptyElementNotNull(writer, "code", "value", retrieveAE.getAEExtension(ArchiveAEExtension.class).getStoreAccessControlID());
        writeEmptyElementNotNull(writer, "AET", "value", retrieveAET);
        writer.writeEndElement();
        writer.writeEndElement();
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

    private static Collection<String> listModalities(Collection<List<Attributes>> instancesBySeries) {
        return instancesBySeries.stream().map(l -> l.get(0).getString(Tag.Modality)).collect(Collectors.toSet());
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

    private static ApplicationEntity retrieveAE(Device device, Attributes study) {
        String retrieveAET = study.getString(Tag.RetrieveAETitle);
        if (retrieveAET != null) {
            ApplicationEntity retrieveAE = device.getApplicationEntity(retrieveAET);
            if (retrieveAE != null && retrieveAE.isInstalled()) {
                return retrieveAE;
            }
            LoggerFactory.getLogger(ImagingStudy.class)
                    .info("No Application Entity found for Retrieve AE Title : ", retrieveAET);
        }
        return null;
    }

    public abstract Entity<byte[]> create(ArchiveDeviceExtension arcdev, List<Attributes> instances) throws XMLStreamException;
}
