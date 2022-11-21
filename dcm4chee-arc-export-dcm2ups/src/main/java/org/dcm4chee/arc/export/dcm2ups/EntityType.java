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
 * Portions created by the Initial Developer are Copyright (C) 2015-2020
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
 *
 */

package org.dcm4chee.arc.export.dcm2ups;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.StreamingOutput;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.io.SAXTransformer;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.ws.rs.MediaTypes;

import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.xml.transform.stream.StreamResult;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since May 2021
 */
enum EntityType {
    DICOM_JSON(MediaTypes.APPLICATION_DICOM_JSON_TYPE) {
        @Override
        Entity<StreamingOutput> entity(final Attributes upsAttrs) {
            return Entity.entity(out -> {
                        JsonGenerator gen = Json.createGenerator(out);
                        new JSONWriter(gen).write(upsAttrs);
                        gen.flush();
                    }, MediaTypes.APPLICATION_DICOM_JSON_TYPE);
        }
    },
    DICOM_XML(MediaTypes.APPLICATION_DICOM_XML_TYPE) {
        @Override
        Entity<StreamingOutput> entity(final Attributes upsAttrs) {
            return Entity.entity(out -> {
                try {
                    SAXTransformer.getSAXWriter(new StreamResult(out)).write(upsAttrs);
                } catch (Exception e) {
                    throw new WebApplicationException(e);
                }
            }, MediaTypes.APPLICATION_DICOM_XML_TYPE);
        }
    };

    final MediaType type;
    EntityType(MediaType type) {
        this.type = type;
    }

    static EntityType valueOf(MediaType type) {
        return MediaTypes.APPLICATION_DICOM_XML_TYPE.isCompatible(type) ? DICOM_XML : DICOM_JSON;
    }

    abstract Entity<StreamingOutput> entity(final Attributes upsAttrs);
}
