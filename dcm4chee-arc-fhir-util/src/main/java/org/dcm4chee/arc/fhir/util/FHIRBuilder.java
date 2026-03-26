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

package org.dcm4chee.arc.fhir.util;

import jakarta.json.stream.JsonGenerator;
import jakarta.servlet.http.HttpServletRequest;
import org.dcm4che3.data.*;
import org.dcm4che3.dcmr.AnatomicRegion;
import org.dcm4che3.dict.archive.PrivateTag;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.query.Query;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Gunter Zeilinger <gunterze@protonmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Mar 2026
 */
public class FHIRBuilder {
    protected final ArchiveDeviceExtension arcdev;

    protected FHIRBuilder(ArchiveDeviceExtension arcdev) {
        this.arcdev = arcdev;
    }

    public static class XML extends FHIRBuilder {
        private final XMLStreamWriter writer;

        public XML(ArchiveDeviceExtension arcdev, XMLStreamWriter writer) {
            super(arcdev);
            this.writer = writer;
        }

        public void writePatientBundle(HttpServletRequest request, OffsetDateTime now, long count, Query query)
                throws Exception {
            writer.writeStartDocument("UTF-8", "1.0");
            writer.writeStartElement("Bundle");
            writer.writeDefaultNamespace("http://hl7.org/fhir");
            writeEmptyElement("id", "value", UUID.randomUUID().toString());
            writer.writeStartElement("meta");
            writeEmptyElement("lastUpdated", "value", now.toString());
            writer.writeEndElement();
            writeEmptyElement("type", "value", "searchset");
            writeEmptyElement("total", "value", count);
            writer.writeStartElement("link");
            writeEmptyElement("relation", "value", "self");
            writeEmptyElement("url", "value", request.getRequestURL().toString());
            writer.writeEndElement();
            while (query.hasMoreMatches()) {
                writePatientEntry(request, query.nextMatch());
            }
            writer.writeEndElement();
            writer.writeEndDocument();
        }

        public void writePatientEntry(HttpServletRequest request, Attributes match) throws XMLStreamException {
            writer.writeStartElement("entry");
            String id = match.getString(PrivateTag.PrivateCreator, PrivateTag.LogicalPatientID);
            writeEmptyElement("fullUrl", "value", patientURL(request, id));
            writePatient(id, match, "http://hl7.org/fhir");
            writer.writeStartElement("search");
            writeEmptyElement("mode", "value", "match");
            writer.writeEndElement();
            writer.writeEndElement();
        }

        public void writePatient(String id, Attributes match, String namespaceURI) throws XMLStreamException {
            writer.writeStartElement("Patient");
            if (namespaceURI != null) {
                writer.writeDefaultNamespace(namespaceURI);
            }
            writeEmptyElement("id", "value", id);
            writePersonName(match.getString(Tag.PatientName));
            writePatientIDs(preferredPatientIDs(match));
            writeEmptyElementNotNull("birthDate", "value",
                    toDate(match.getString(Tag.PatientBirthDate)));
            writeEmptyElementNotNull("gender", "value",
                    toGender(match.getString(Tag.PatientSex)));
            writer.writeEndElement();
        }

        public void writeImagingStudy(List<Attributes> instancesOfStudy, boolean LTNHR_V1) throws XMLStreamException {
            SimpleDateFormat ISO_DATE_TIME = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
            Attributes study = instancesOfStudy.get(0);
            Collection<List<Attributes>> instancesBySeries = instancesOfStudy.stream()
                    .collect(Collectors.groupingBy(attrs -> attrs.getString(Tag.SeriesInstanceUID)))
                    .values();
            writer.writeStartElement("ImagingStudy");
            writer.writeDefaultNamespace("http://hl7.org/fhir");
            if (LTNHR_V1) writeOrganization("Organization1", instancesBySeries);
            writeStudyIUID(study.getString(Tag.StudyInstanceUID));
            writeEmptyElement("status", "value", "available");
            writer.writeStartElement("contained");
            writePatient("patient1", study, null);
            writer.writeEndElement();
            writer.writeStartElement("subject");
            writeEmptyElement("reference", "value", "#patient1");
            writer.writeEndElement();
            boolean referrer = writePractitioner("Practitioner1", study.getString(Tag.ReferringPhysicianName)) ;
            int practitionerNo = referrer ? 1 : 0;
            writePerformingPhysicians(instancesBySeries, practitionerNo);
            if (referrer) {
                writer.writeStartElement("referrer");
                writeEmptyElement("reference", "value", "#practitioner1");
                writer.writeEndElement();
            }
            writeAccessionNumber(IDWithIssuer.valueOf(study, Tag.AccessionNumber, Tag.IssuerOfAccessionNumberSequence));
            writeEmptyElementNotNull("started", "value",
                    study.getDate(Tag.StudyDateAndTime), ISO_DATE_TIME);
            writeEmptyElement("numberOfSeries", "value", instancesBySeries.size());
            writeEmptyElement("numberOfInstances", "value", instancesOfStudy.size());
            for (String modality : listModalities(instancesBySeries)) {
                writeModality(modality);
            }
            writeEmptyElementNotNull("description", "value", study.getString(Tag.StudyDescription));
            writeConceptCode("procedure", study.getNestedDataset(Tag.ProcedureCodeSequence));
            writeConceptCode("reason", study.getNestedDataset(Tag.ReasonForPerformedProcedureCodeSequence));
            for (List<Attributes> seriesOfInstances : instancesBySeries) {
                Attributes series = seriesOfInstances.get(0);
                String seriesIUID = series.getString(Tag.SeriesInstanceUID);
                writer.writeStartElement("series");
                writeEmptyElement("uid", "value", seriesIUID);
                writeEmptyElement("number", "value", series.getInt(Tag.SeriesNumber, 0));
                writeModalityNotNull(series.getString(Tag.Modality));
                writeEmptyElementNotNull("description", "value", series.getString(Tag.SeriesDescription));
                writeEmptyElement("numberOfInstances", "value", seriesOfInstances.size());
                if (series.containsValue(Tag.PerformingPhysicianName)) {
                   writePerformerReference("#Practitioner" + ++practitionerNo);
                }
                writeBodyPartExamined(series.getString(Tag.BodyPartExamined));
                writeLaterality(series.getString(Tag.Laterality));
                writeEmptyElementNotNull("started", "value", seriesStartDate(series), ISO_DATE_TIME);
                for (Attributes inst : seriesOfInstances) {
                    writer.writeStartElement("instance");
                    writeEmptyElement("uid", "value", inst.getString(Tag.SOPInstanceUID));
                    writer.writeStartElement("sopClass");
                    writeEmptyElement("system", "value", "urn:ietf:rfc:3986");
                    writeEmptyElement("value", "value",
                            "urn:oid:" + inst.getString(Tag.SOPClassUID));
                    writer.writeEndElement();
                    writeEmptyElement("number", "value",
                            inst.getInt(Tag.InstanceNumber, 0));
                    writeEmptyElementNotNull("title", "value", documentTitle(inst));
                    writer.writeEndElement();
                }
                writer.writeEndElement();
            }
            writer.writeEndElement();
        }

        private void writePerformingPhysicians(Collection<List<Attributes>> instancesBySeries, int practitionerNo)
                throws XMLStreamException {
            String id = "Practitioner" + ++practitionerNo;
            for (List<Attributes> instancesOfSeries : instancesBySeries) {
                if (writePractitioner(id, instancesOfSeries.get(0).getString(Tag.PerformingPhysicianName))) {
                    id = "Practitioner" + ++practitionerNo;
                }
            }
        }

        private void writePatientIDs(Set<IDWithIssuer> idWithIssuers) throws XMLStreamException {
            for (IDWithIssuer idWithIssuer : idWithIssuers) {
                writer.writeStartElement("identifier");
                writeEmptyElementNotNull("system", "value",
                        arcdev.fhirSystemOfPatientID(idWithIssuer.getIssuer()));
                writeEmptyElement("value", "value", idWithIssuer.getID());
                writer.writeEndElement();
            }
        }

        private void writePersonName(String value) throws XMLStreamException {
            if (value != null) {
                PersonName name = new PersonName(value, true);
                String family = name.get(PersonName.Component.FamilyName);
                String given = name.get(PersonName.Component.GivenName);
                if (family != null || given != null) {
                    writer.writeStartElement("name");
                    writeEmptyElementNotNull("family", "value", family);
                    if (given != null) {
                        StringTokenizer tokens = new StringTokenizer(given, " ");
                        while (tokens.hasMoreTokens()) {
                            writeEmptyElement("given", "value", tokens.nextToken());
                        }
                    }
                    writer.writeEndElement();
                }
            }
        }

        private void writeStudyIUID(String suid) throws XMLStreamException {
            writer.writeStartElement("identifier");
            writeEmptyElement("use", "value", "official");
            writeEmptyElement("system", "value", "urn:dicom:uid");
            writeEmptyElement("value", "value", "urn:oid:" + suid);
            writer.writeEndElement();
        }

        private void writeAccessionNumber(IDWithIssuer idWithIssuer) throws XMLStreamException {
            if (idWithIssuer != null) {
                writer.writeStartElement("basedOn");
                writeEmptyElement( "type", "value", "ServiceRequest");
                writer.writeStartElement("identifier");
                writer.writeStartElement("type");
                writeCoding("http://terminology.hl7.org/CodeSystem/v2-0203", "ACSN", null);
                writer.writeEndElement();
                writeEmptyElement( "value", "value", idWithIssuer.getID());
                Issuer issuer = idWithIssuer.getIssuer();
                String system = arcdev.fhirSystemOfAccessionNumber(issuer);
                if (system != null) {
                    writer.writeStartElement("assigner");
                    writeEmptyElement("system", "value", system);
                    if (issuer != null) {
                        writeEmptyElementNotNull("display", "value", issuer.getLocalNamespaceEntityID());
                    }
                    writer.writeEndElement();
                }
                writer.writeEndElement();
                writer.writeEndElement();
            }
        }

        private void writeModalityNotNull(String modality) throws XMLStreamException {
            if (modality != null) writeModality(modality);
        }

        private void writeModality(String modality) throws XMLStreamException {
            writer.writeStartElement("modality");
            writeCoding("http://dicom.nema.org/resources/ontology/DCM", modality, null );
            writer.writeEndElement();
        }

        private boolean writeOrganization(String id, Collection<List<Attributes>> instancesBySeries)
                throws XMLStreamException {
            String retrieveAET = instancesBySeries.iterator()
                                                    .next()
                                                    .get(0).
                                                    getString(PrivateTag.PrivateCreator, PrivateTag.ReceivingApplicationEntityTitleOfSeries, VR.AE);
            if (retrieveAET != null) {
                ApplicationEntity retrieveAE = arcdev.getDevice().getApplicationEntity(retrieveAET);
                if (retrieveAE != null && retrieveAE.isInstalled()) {
                    writer.writeStartElement("extension");
                    writer.writeAttribute("url", "http://esveikata.lt/Profile/ltnhr-imagingstudy#organization");
                    writer.writeStartElement("valueResource");
                    writeEmptyElement("reference", "value", "#" + id);
                    writer.writeEndElement();
                    writer.writeEndElement();
                    writer.writeStartElement("contained");
                    writer.writeStartElement("Organization");
                    writer.writeAttribute("id", id);
                    writer.writeStartElement("name");
                    writeEmptyElementNotNull("code", "value",
                            retrieveAE.getAEExtension(ArchiveAEExtension.class).getStoreAccessControlID());
                    writeEmptyElementNotNull("AET", "value", retrieveAE.getAETitle());
                    writer.writeEndElement();
                    writer.writeEndElement();
                    writer.writeEndElement();
                    return true;
                } else {
                    LoggerFactory.getLogger(FHIRBuilder.class)
                            .info("No Application Entity found for Retrieve AE Title : {}", retrieveAET);
                }
            }
            return false;
        }

        private void writeOrganizationExtension(String reference) throws XMLStreamException {
            writer.writeStartElement("extension");
            writer.writeAttribute("url", "http://esveikata.lt/Profile/ltnhr-imagingstudy#organization");
            writer.writeStartElement("valueResource");
            writeEmptyElement("reference", "value", reference);
            writer.writeEndElement();
            writer.writeEndElement();
        }

        private void writeExtension(String url, String valueType, String value)
                throws XMLStreamException {
            if (value != null) {
                writer.writeStartElement("extension");
                writer.writeAttribute("url", url);
                writeEmptyElement(valueType, "value", value);
                writer.writeEndElement();
            }
        }

        private boolean writePractitioner(String id, String name)
                throws XMLStreamException {
            if (name == null) return false;

            writer.writeStartElement("contained");
            writer.writeStartElement("Practitioner");
            writeEmptyElement("id", "value", id);
            writePersonName(name);
            writer.writeEndElement();
            writer.writeEndElement();
            return true;
        }

        private void writePerformerReference(String reference)
                throws XMLStreamException {
            writer.writeStartElement("performer");
            writer.writeStartElement("function");
            writeCoding("http://terminology.hl7.org/CodeSystem/v3-ParticipationType", "PRF", null);
            writer.writeEndElement();
            writer.writeStartElement("actor");
            writeEmptyElement("reference", "value", reference);
            writer.writeEndElement();
            writer.writeEndElement();
        }

        private void writeBodyPartExamined(String bodyPartExamined) throws XMLStreamException {
            Code code = bodyPartExamined != null ? AnatomicRegion.codeOf(bodyPartExamined) : null;
            if (code != null) {
                writer.writeStartElement("bodySite");
                writer.writeStartElement("concept");
                writeCodingWithText("http://snomed.info/sct", code.getCodeValue(), code.getCodeMeaning());
                writer.writeEndElement();
                writer.writeEndElement();
            }
        }

        private void writeLaterality(String laterality) throws XMLStreamException {
            boolean l;
            if (laterality != null && ((l = laterality.equals("L")) || laterality.equals("R"))) {
                writer.writeStartElement("laterality");
                if (l)
                    writeCodingWithText("http://snomed.info/sct", "7771000", "Unilateral Left");
                else {
                    writeCodingWithText("http://snomed.info/sct", "24028007", "Right");
                }
                writer.writeEndElement();
            }
        }

        private void writeCoding(String system, String code, String display) throws XMLStreamException {
            writer.writeStartElement("coding");
            writeEmptyElement("system", "value", system);
            writeEmptyElement( "code", "value", code);
            writeEmptyElementNotNull( "display", "value", display);
            writer.writeEndElement();
        }

        private void writeCodingWithText(String system, String code, String display) throws XMLStreamException {
            writeCoding(system, code, display);
            writeEmptyElementNotNull( "text", "value", display);
        }

        private void writeConceptCode(String name, Attributes item) throws XMLStreamException {
            if (item != null) {
                writer.writeStartElement(name);
                writer.writeStartElement("concept");
                writeCodingWithText(
                        item.getString(Tag.CodingSchemeDesignator),
                        item.getString(Tag.CodeValue),
                        item.getString(Tag.CodeMeaning));
                writer.writeEndElement();
                writer.writeEndElement();
            }
        }

        private void writeEmptyElementNotNull(String elmName, String attrName, Object value) throws XMLStreamException {
            if (value != null) writeEmptyElement(elmName, attrName, value);
        }

        private void writeEmptyElementNotNull(String elmName, String attrName, Date value, DateFormat dateFormat)
                throws XMLStreamException {
            if (value != null) writeEmptyElement(elmName, attrName, dateFormat.format(value));
        }

        private void writeEmptyElement(String elmName, String attrName, Object value)
                throws XMLStreamException {
            writer.writeEmptyElement(elmName);
            writer.writeAttribute(attrName, value.toString());
        }

    }

    public static class JSON extends FHIRBuilder {
        private final JsonGenerator gen;

        public JSON(ArchiveDeviceExtension arcdev, JsonGenerator gen) {
            super(arcdev);
            this.gen = gen;
        }

        public void writePatientBundle(HttpServletRequest request, OffsetDateTime now, long count, Query query)
                throws Exception {
            gen.writeStartObject();
            gen.write("resourceType", "Bundle");
            gen.write("id", UUID.randomUUID().toString());
            gen.writeStartObject("meta");
            gen.write("lastUpdated", now.toString());
            gen.writeEnd();
            gen.write("type", "searchset");
            gen.write("total", count);
            gen.writeStartArray("link");
            gen.writeStartObject();
            gen.write("relation", "self");
            gen.write("url", request.getRequestURL().toString());
            gen.writeEnd();
            gen.writeEnd();
            if (query.hasMoreMatches()) {
                gen.writeStartArray("entry");
                do {
                    writePatientEntry(request, query.nextMatch());
                } while (query.hasMoreMatches());
                gen.writeEnd();
            }
            gen.writeEnd();
        }

        public void writePatientEntry(HttpServletRequest request, Attributes match) {
            gen.writeStartObject();
            String id = match.getString(PrivateTag.PrivateCreator, PrivateTag.LogicalPatientID);
            gen.write("fullUrl", patientURL(request, id));
            gen.writeStartObject("resource");
            writePatient(id, match);
            gen.writeEnd();
            gen.writeStartObject("search");
            gen.write("mode", "match");
            gen.writeEnd();
            gen.writeEnd();
        }

        public void writePatient(String id, Attributes match) {
            gen.write("resourceType", "Patient");
            gen.write("id", id);
            writePatientIDs(preferredPatientIDs(match));
            writePersonName(match.getString(Tag.PatientName));
            writeNotNull("gender", toGender(match.getString(Tag.PatientSex)));
            writeNotNull("birthDate", toDate(match.getString(Tag.PatientBirthDate)));
        }


        public void writeImagingStudy(List<Attributes> instances) {
            SimpleDateFormat ISO_DATE_TIME = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
            Attributes study = instances.get(0);
            Collection<List<Attributes>> instancesBySeries = instances.stream()
                    .collect(Collectors.groupingBy(attrs -> attrs.getString(Tag.SeriesInstanceUID)))
                    .values();
            gen.writeStartObject();
            gen.write("resourceType", "ImagingStudy");
            gen.writeStartArray("contained");
            gen.writeStartObject();
            writePatient("patient1", study);
            gen.writeEnd();
            boolean referrer = writePractitioner("practitioner1", study.getString(Tag.ReferringPhysicianName)) ;
            int practitionerNo = referrer ? 1 : 0;
            writePerformingPhysicians(instancesBySeries, practitionerNo);
            gen.writeEnd();
            gen.write("status", "available");
            gen.writeStartArray("identifier");
            gen.writeStartObject();
            gen.write("system", "urn:dicom:uid");
            gen.write("value", "urn:oid:" + study.getString(Tag.StudyInstanceUID));
            gen.writeEnd();
            gen.writeEnd();
            gen.writeStartObject("subject");
            gen.write("reference", "#patient1");
            gen.writeEnd();
            if (referrer) {
                gen.writeStartObject("referrer");
                gen.write("reference", "#practitioner1");
                gen.writeEnd();
            }
            writeAccessionNumber(IDWithIssuer.valueOf(study, Tag.AccessionNumber, Tag.IssuerOfAccessionNumberSequence));
            writeNotNull("started", study.getDate(Tag.StudyDateAndTime), ISO_DATE_TIME);
            gen.write("numberOfSeries", instancesBySeries.size());
            gen.write("numberOfInstances", instances.size());
            writeModalities(listModalities(instancesBySeries));
            writeNotNull("description", study.getString(Tag.StudyDescription));
            writeConceptCode("procedure", study.getNestedDataset(Tag.ProcedureCodeSequence));
            writeConceptCode("reason", study.getNestedDataset(Tag.ReasonForPerformedProcedureCodeSequence));
            gen.writeStartArray("series");
            for (List<Attributes> seriesOfInstances : instancesBySeries) {
                Attributes series = seriesOfInstances.get(0);
                String seriesIUID = series.getString(Tag.SeriesInstanceUID);
                gen.writeStartObject();
                gen.write("uid", seriesIUID);
                gen.write("number", series.getInt(Tag.SeriesNumber, 0));
                writeModality(series.getString(Tag.Modality));
                writeNotNull("description", series.getString(Tag.SeriesDescription));
                gen.write("numberOfInstances", seriesOfInstances.size());
                if (series.containsValue(Tag.PerformingPhysicianName)) {
                    writePerformerReference("#practitioner" + ++practitionerNo);
                }
                writeBodyPartExamined(series.getString(Tag.BodyPartExamined));
                writeLaterality(series.getString(Tag.Laterality));
                writeNotNull("started", FHIRBuilder.seriesStartDate(series), ISO_DATE_TIME);
                gen.writeStartArray("instance");
                for (Attributes inst : seriesOfInstances) {
                    gen.writeStartObject();
                    gen.write("uid", inst.getString(Tag.SOPInstanceUID));
                    gen.writeStartObject("sopClass");
                    gen.write("system", "urn:ietf:rfc:3986");
                    gen.write("code", "urn:oid:" + inst.getString(Tag.SOPClassUID));
                    gen.writeEnd();
                    gen.write("number", inst.getInt(Tag.InstanceNumber, 0));
                    writeNotNull("title", documentTitle(inst));
                    gen.writeEnd();
                }
                gen.writeEnd();
                gen.writeEnd();
            }
            gen.writeEnd();
            gen.writeEnd();
        }

        private void writePerformerReference(String reference) {
            gen.writeStartArray("performer");
            gen.writeStartObject("function");
            writeCoding("http://terminology.hl7.org/CodeSystem/v3-ParticipationType", "PRF", null);
            gen.writeEnd();
            gen.writeStartObject("actor");
            gen.write("reference", reference);
            gen.writeEnd();
            gen.writeEnd();
        }

        private void writePatientIDs(Set<IDWithIssuer> idWithIssuers) {
            if (!idWithIssuers.isEmpty()) {
                gen.writeStartArray("identifier");
                for (IDWithIssuer idWithIssuer : idWithIssuers) {
                    gen.writeStartObject();
                    gen.write("type", "Patient");
                    gen.writeStartObject("identifier");
                    writeNotNull("system", arcdev.fhirSystemOfPatientID(idWithIssuer.getIssuer()));
                    gen.write("value", idWithIssuer.getID());
                    gen.writeEnd();
                    gen.writeEnd();
                }
                gen.writeEnd();
            }
        }

        private void writePersonName(String value) {
            if (value != null) {
                PersonName name = new PersonName(value, true);
                String family = name.get(PersonName.Component.FamilyName);
                String given = name.get(PersonName.Component.GivenName);
                if (family != null || given != null) {
                    gen.writeStartArray("name");
                    gen.writeStartObject();
                    writeNotNull("family", family);
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

        private boolean writePractitioner(String id, String name) {
            if (name == null) return false;
            gen.writeStartObject();
            gen.write("resourceType", "Practitioner");
            gen.write("id", id);
            writePersonName(name);
            gen.writeEnd();
            return true;
        }

        private void writePerformingPhysicians(Collection<List<Attributes>> instancesBySeries, int practitionerNo) {
            String id = "practitioner" + ++practitionerNo;
            for (List<Attributes> instancesOfSeries : instancesBySeries) {
                if (writePractitioner(id, instancesOfSeries.get(0).getString(Tag.PerformingPhysicianName))) {
                    id = "practitioner" + ++practitionerNo;
                }
            }
        }

        private void writeAccessionNumber(IDWithIssuer idWithIssuer) {
            if (idWithIssuer != null) {
                gen.writeStartArray("basedOn");
                gen.writeStartObject();
                gen.write("type", "ServiceRequest");
                gen.writeStartObject("identifier");
                gen.writeStartObject("type");
                writeCoding("http://terminology.hl7.org/CodeSystem/v2-0203", "ACSN", null);
                gen.writeEnd();
                gen.write("value", idWithIssuer.getID());
                Issuer issuer = idWithIssuer.getIssuer();
                String system = arcdev.fhirSystemOfAccessionNumber(issuer);
                if (system != null) {
                    gen.writeStartObject("assigner");
                    gen.write("system", system);
                    writeNotNull("display", issuer.getLocalNamespaceEntityID());
                    gen.writeEnd();
                }
                gen.writeEnd();
                gen.writeEnd();
                gen.writeEnd();
            }
        }

        private void writeModalities(Collection<String> modalities) {
            if (!modalities.isEmpty()) {
                gen.writeStartArray("modality");
                for (String modality : modalities) {
                    gen.writeStartObject();
                    writeCoding("http://dicom.nema.org/resources/ontology/DCM", modality, null);
                    gen.writeEnd();
                }
                gen.writeEnd();
            }
        }

        private void writeModality(String modality) {
            if (modality != null) {
                gen.writeStartObject("modality");
                writeCoding("http://dicom.nema.org/resources/ontology/DCM", modality, null);
                gen.writeEnd();
            }
        }

        private void writeBodyPartExamined(String bodyPartExamined) {
            Code code = bodyPartExamined != null ? AnatomicRegion.codeOf(bodyPartExamined) : null;
            if (code != null) {
                gen.writeStartObject("bodySite");
                gen.writeStartObject("concept");
                writeCoding("http://snomed.info/sct", code.getCodeValue(), code.getCodeMeaning());
                gen.writeEnd();
                gen.writeEnd();
            }
        }

        private void writeLaterality(String laterality) {
            boolean l;
            if (laterality != null && ((l = laterality.equals("L")) || laterality.equals("R"))) {
                gen.writeStartObject("laterality");
                if (l)
                    writeCoding("http://snomed.info/sct", "7771000", "Left");
                else {
                    writeCoding("http://snomed.info/sct", "24028007", "Right");
                }
                gen.writeEnd();
            }
        }

        private void writeCoding(String system, String code, String display) {
            gen.writeStartArray("coding");
            gen.writeStartObject();
            gen.write("system", system);
            gen.write("code", code);
            writeNotNull("display", display);
            gen.writeEnd();
            gen.writeEnd();
        }

        private void writeCodingWithText(String system, String code, String display) {
            writeCoding(system, code, display);
            writeNotNull( "text", display);
        }

        private void writeConceptCode(String name, Attributes item) {
            if (item != null) {
                gen.writeStartArray(name);
                gen.writeStartObject("concept");
                writeCodingWithText(
                        item.getString(Tag.CodingSchemeDesignator),
                        item.getString(Tag.CodeValue),
                        item.getString(Tag.CodeMeaning));
                gen.writeEnd();
                gen.writeEnd();
            }
        }

        private void writeNotNull(String name, Date value, DateFormat dateFormat) {
            if (value != null) gen.write(name, dateFormat.format(value));
        }
 
        private void writeNotNull(String name, String value) {
            if (value != null) gen.write(name, value);
        }
    }

    protected Set<IDWithIssuer> preferredPatientIDs(Attributes match) {
        Set<IDWithIssuer> idWithIssuers = IDWithIssuer.pidsOf(match);
        Issuer[] preferreds = arcdev.getFhirPreferredAssigningAuthorityOfPatientID();
        return preferreds.length > 0
                ? idWithIssuers.stream()
                .filter(idWithIssuer -> Stream.of(preferreds).anyMatch(
                        preferred -> preferred.matches(idWithIssuer.getIssuer())))
                .collect(Collectors.toSet())
                : idWithIssuers;
    }

    protected String patientURL(HttpServletRequest request, String logicalPatientID) {
        StringBuffer requestURL = request.getRequestURL();
        requestURL.setLength(requestURL.indexOf("/patient") + 8);
        return requestURL.append('/').append(logicalPatientID).toString();
    }

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

    private static Collection<String> listModalities(Collection<List<Attributes>> instancesBySeries) {
        return instancesBySeries.stream().map(l -> l.get(0)
                .getString(Tag.Modality))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private static Date seriesStartDate(Attributes series) {
        Date seriesStartDate = series.getDate(Tag.AcquisitionDateTime);
        if (seriesStartDate == null) {
            seriesStartDate = series.getDate(Tag.AcquisitionDateAndTime);
            if (seriesStartDate == null) {
                seriesStartDate = series.getDate(Tag.SeriesDateAndTime);
            }
        }
        return seriesStartDate;
    }

    private static String documentTitle(Attributes inst) {
        Attributes conceptNameCode = inst.getNestedDataset(Tag.ConceptNameCodeSequence);
        return conceptNameCode != null
                ? conceptNameCode.getString(Tag.CodeMeaning)
                : inst.getString(Tag.DocumentTitle);
    }

}
