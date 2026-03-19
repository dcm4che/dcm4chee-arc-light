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
import org.dcm4che3.dict.archive.PrivateTag;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.query.Query;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Gunter Zeilinger <gunterze@protonmail.com>
 * @since Mar 2026
 */
public class FHIRBuilder {
    protected final HttpServletRequest request;
    protected final ArchiveDeviceExtension arcdev;

    protected FHIRBuilder(HttpServletRequest request, ArchiveDeviceExtension arcdev) {
        this.request = request;
        this.arcdev = arcdev;
    }

    public static class XML extends FHIRBuilder {
        private final XMLStreamWriter writer;

        public XML(HttpServletRequest request, ArchiveDeviceExtension arcdev, XMLStreamWriter writer) {
            super(request, arcdev);
            this.writer = writer;
        }

        public void writePatientBundle(OffsetDateTime now, long count, Query query) throws Exception {
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
                writePatientEntry(query.nextMatch());
            }
            writer.writeEndElement();
            writer.writeEndDocument();
        }

        public void writePatientEntry(Attributes match) {
            try {
                writer.writeStartElement("entry");
                String id = match.getString(PrivateTag.PrivateCreator, PrivateTag.LogicalPatientID);
                writeEmptyElement("fullUrl", "value", patientURL(id));
                writePatient(match, id);
                writer.writeStartElement("search");
                writeEmptyElement("mode", "value", "match");
                writer.writeEndElement();
                writer.writeEndElement();
            } catch (XMLStreamException e) {
                throw new RuntimeException(e);
            }
        }

        public void writePatient(Attributes match, String id)
                throws XMLStreamException {
            writer.writeStartElement("Patient");
            writer.writeDefaultNamespace("http://hl7.org/fhir");
            writeEmptyElement("id", "value", id);
            writePatientName(match.getString(Tag.PatientName));
            writePatientIDs(preferredPatientIDs(match));
            writeEmptyElementNotNull("birthDate", "value",
                    toDate(match.getString(Tag.PatientBirthDate)));
            writeEmptyElementNotNull("gender", "value",
                    toGender(match.getString(Tag.PatientSex)));
            writer.writeEndElement();
        }

        private void writePatientIDs(Set<IDWithIssuer> idWithIssuers)
                throws XMLStreamException {
            for (IDWithIssuer idWithIssuer : idWithIssuers) {
                writer.writeStartElement("identifier");
                writeEmptyElementNotNull("system", "value",
                        arcdev.fhirSystemOfPatientID(idWithIssuer.getIssuer()));
                writeEmptyElement("value", "value", idWithIssuer.getID());
                writer.writeEndElement();
            }
        }

        private void writePatientName(String value) throws XMLStreamException {
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

        private void writeEmptyElementNotNull(String elmName, String attrName, Object value)
                throws XMLStreamException {
            if (value != null) writeEmptyElement(elmName, attrName, value);
        }

        private void writeEmptyElement(String elmName, String attrName, Object value)
                throws XMLStreamException {
            writer.writeEmptyElement(elmName);
            writer.writeAttribute(attrName, value.toString());
        }

    }

    public static class JSON extends FHIRBuilder {
        private final JsonGenerator gen;

        public JSON(HttpServletRequest request, ArchiveDeviceExtension arcdev, JsonGenerator gen) {
            super(request, arcdev);
            this.gen = gen;
        }

        public void writePatientBundle(OffsetDateTime now, long count, Query query) throws Exception {
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
                    writePatientEntry(query.nextMatch());
                } while (query.hasMoreMatches());
                gen.writeEnd();
            }
            gen.writeEnd();
        }

        public void writePatientEntry(Attributes match) {
            gen.writeStartObject();
            String id = match.getString(PrivateTag.PrivateCreator, PrivateTag.LogicalPatientID);
            gen.write("fullUrl", patientURL(id));
            gen.writeStartObject("resource");
            writePatient(match, id);
            gen.writeEnd();
            gen.writeStartObject("search");
            gen.write("mode", "match");
            gen.writeEnd();
            gen.writeEnd();
        }

        public void writePatient(Attributes match, String id) {
            gen.write("resourceType", "Patient");
            gen.write("id", id);
            writePatientIDs(preferredPatientIDs(match));
            writePatientName(match.getString(Tag.PatientName));
            writeNotNull("gender", toGender(match.getString(Tag.PatientSex)));
            writeNotNull("birthDate", toDate(match.getString(Tag.PatientBirthDate)));
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

        private void writePatientName(String value) {
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

    protected String patientURL(String logicalPatientID) {
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
}
