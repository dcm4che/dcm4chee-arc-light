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
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.ws.rs.MediaTypes;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@protonmail.com>
 * @since Oct 2025
 */
public enum ImagingStudy {
    FHIR_R4_JSON {
        @Override
        public Entity<StreamingOutput> create(Attributes kosAttrs, Map<String, Attributes> seriesAttrs) {
            return Entity.entity(out -> writeJSON(kosAttrs, seriesAttrs, out), MediaTypes.APPLICATION_FHIR_JSON_TYPE);
        }

        private void writeJSON(Attributes kosAttrs, Map<String, Attributes> seriesAttrs, OutputStream out) {
            Sequence refSeriesSeq = kosAttrs.getNestedDataset(Tag.CurrentRequestedProcedureEvidenceSequence)
                    .getSequence(Tag.ReferencedSeriesSequence);
            try (JsonGenerator gen = Json.createGenerator(out)) {
                SimpleDateFormat ISO_DATE_TIME = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'XXX");
                gen.writeStartObject();
                gen.write("resourceType", "ImagingStudy");
                gen.writeStartArray("identifier");
                gen.writeStartObject();
                gen.write("system", "urn:dicom:uid");
                gen.write("value", "urn:oid:" + kosAttrs.getString(Tag.StudyInstanceUID));
                gen.writeEnd();
                gen.writeEnd();
                Date studyDateTime = kosAttrs.getDate(Tag.StudyDateAndTime);
                if (studyDateTime != null) gen.write("started", ISO_DATE_TIME.format(studyDateTime));
                gen.write("numberOfSeries", refSeriesSeq.size());
                gen.write("numberOfInstances", refSeriesSeq.stream()
                        .mapToInt(refSeries -> refSeries.getSequence(Tag.ReferencedSOPSequence).size())
                        .sum());
                gen.writeStartArray("series");
                for (Attributes refSeries : refSeriesSeq) {
                    String seriesIUID = refSeries.getString(Tag.SeriesInstanceUID);
                    Sequence refSOPSeq = refSeries.getSequence(Tag.ReferencedSOPSequence);
                    gen.writeStartObject();
                    gen.write("uid", "urn:oid:" + seriesIUID);
                    gen.write("numberOfInstances", refSOPSeq.size());
                    gen.writeStartArray("instance");
                    for (Attributes refSOP : refSOPSeq) {
                        gen.writeStartObject();
                        gen.write("uid", "urn:oid:" + refSOP.getString(Tag.SOPInstanceUID));
                        gen.write("sopClass", "urn:oid:" + refSOP.getString(Tag.SOPClassUID));
                        gen.writeEnd();
                    }
                    gen.writeEnd();
                    gen.writeEnd();
                }
                gen.writeEnd();
                gen.writeEnd();
            }
        }
    };
    public abstract Entity<StreamingOutput> create(Attributes kosAttrs, Map<String, Attributes> seriesAttrs);
}
