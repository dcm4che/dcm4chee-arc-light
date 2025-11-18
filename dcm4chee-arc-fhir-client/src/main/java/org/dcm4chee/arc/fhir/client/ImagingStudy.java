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
import org.dcm4che3.net.Device;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;

import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Gunter Zeilinger <gunterze@protonmail.com>
 * @since Oct 2025
 */
public enum ImagingStudy {
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
                gen.write("status", "available");
                gen.writeStartArray("identifier");
                gen.writeStartObject();
                gen.write("system", "urn:dicom:uid");
                gen.write("value", "urn:oid:" + kosAttrs.getString(Tag.StudyInstanceUID));
                gen.writeEnd();
                gen.writeEnd();
                writePatientID(gen, preferredPatientID(IDWithIssuer.pidsOf(kosAttrs), arcdev), arcdev);
                writeAccessionNumber(gen, IDWithIssuer.valueOf(kosAttrs, Tag.AccessionNumber, Tag.IssuerOfAccessionNumberSequence), arcdev);
                writeNotNull(gen, "started", kosAttrs.getDate(Tag.StudyDateAndTime), ISO_DATE_TIME);
                gen.write("numberOfSeries", refSeriesSeq.size());
                gen.write("numberOfInstances", refSeriesSeq.stream()
                        .mapToInt(refSeries -> refSeries.getSequence(Tag.ReferencedSOPSequence).size())
                        .sum());
                writeModalities(gen, seriesAttrsByIUID.values().stream()
                        .map(attrs -> attrs.getString(Tag.Modality))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()));
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
            }
        }

        private void writePatientID(JsonGenerator gen, IDWithIssuer idWithIssuer, ArchiveDeviceExtension arcdev) {
            if (idWithIssuer != null) {
                gen.writeStartObject("subject");
                gen.write("type", "Patient");
                gen.writeStartObject("identifier");
                writeNotNull(gen, "system", arcdev.fhirSystemOfPatientID(idWithIssuer.getIssuer()));
                gen.write("value", idWithIssuer.getID());
                gen.writeEnd();
                gen.writeEnd();
            }
        }

        private void writeAccessionNumber(JsonGenerator gen, IDWithIssuer idWithIssuer, ArchiveDeviceExtension arcdev) {
            if (idWithIssuer != null) {
                gen.writeStartArray("basedOn");
                gen.writeStartObject();
                gen.write("type", "ServiceRequest");
                gen.writeStartObject("identifier");
                gen.writeStartObject("type");
                gen.writeStartArray("coding");
                gen.writeStartObject();
                gen.write("system", "http://terminology.hl7.org/CodeSystem/v2-0203");
                gen.write("code", "ACSN");
                gen.writeEnd();
                gen.writeEnd();
                gen.writeEnd();
                writeNotNull(gen, "system", arcdev.fhirSystemOfAccessionNumber(idWithIssuer.getIssuer()));
                gen.write("value", idWithIssuer.getID());
                gen.writeEnd();
                gen.writeEnd();
                gen.writeEnd();
            }
        }
    };

    private static IDWithIssuer preferredPatientID(Set<IDWithIssuer> idWithIssuers, ArchiveDeviceExtension arcdev) {
        switch (idWithIssuers.size()) {
            case 0:
                return null;
            case 1:
                return idWithIssuers.iterator().next();
        }
        Issuer preferred = arcdev.getFhirPreferredAssigningAuthorityOfPatientID();
        if (preferred != null) {
            Optional<IDWithIssuer> first = idWithIssuers.stream()
                    .filter(idWithIssuer -> preferred.matches(idWithIssuer.getIssuer()))
                    .findFirst();
            if (first.isPresent()) return first.get();
        }
        return idWithIssuers.iterator().next();
    }

    private static void writeNotNull(JsonGenerator gen, String name, Date value, DateFormat dateFormat) {
        if (value != null) gen.write(name, dateFormat.format(value));
    }
    private static void writeNotNull(JsonGenerator gen, String name, String value) {
        if (value != null) gen.write(name, value);
    }
    private static void writeModalities(JsonGenerator gen, Set<String> modalities) {
        if (!modalities.isEmpty()) {
            gen.writeStartArray("modality");
            for (String modality : modalities) {
                gen.writeStartObject();
                gen.write("system", "http://dicom.nema.org/resources/ontology/DCM");
                gen.write("code", modality );
                gen.writeEnd();
            }
            gen.writeEnd();
        }
    }
    private static void writeModality(JsonGenerator gen, String modality) {
        if (modality != null) {
            gen.writeStartObject("modality");
            gen.write("system", "http://dicom.nema.org/resources/ontology/DCM");
            gen.write("code", modality );
            gen.writeEnd();
        }
    }

    public abstract Entity<StreamingOutput> create(
            Device device, Attributes kosAttrs, Map<String, Attributes> seriesAttrs);
}
