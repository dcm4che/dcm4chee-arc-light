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
 * Portions created by the Initial Developer are Copyright (C) 2015-2017
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
 */

package org.dcm4chee.arr.query;

import org.dcm4che3.io.*;
import org.dcm4che3.net.Device;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import javax.ws.rs.*;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since April 2017
 */
@RequestScoped
@Path("AuditEvent")
public class AuditEventRS {

    @Inject
    private Device device;

    @Context
    private HttpServletRequest httpRequest;

    @QueryParam("date")
    @Size(min = 1)
    @Pattern(regexp = "(ge20|le20)\\d\\d-\\d\\d-\\d\\d")
    private List<String> dates;

    @QueryParam("address")
    private String address;

    @QueryParam("patient.identifier")
    private String patientid;

    @QueryParam("identity")
    private String identity;

    @QueryParam("object-type ")
    private String objecttype;

    @QueryParam("role")
    private String role;

    @QueryParam("source")
    private String source;

    @QueryParam("type")
    private String type;

    @QueryParam("user")
    private String user;

    @QueryParam("subtype")
    private String subtype;

    @QueryParam("outcome")
    private String outcome;

    @GET
    @Produces("application/json+fhir")
    public StreamingOutput retrieveAuditEventJSON() {
        return retrieveAuditEvent(Format.JSON);
    }

    @GET
    @Produces("application/xml+fhir")
    public StreamingOutput retrieveAuditEventXML() {
        return retrieveAuditEvent(Format.XML);
    }

    @GET
    public StreamingOutput retrieveAuditEvent(@QueryParam("_format") List<String> formats) {
        return retrieveAuditEvent(Format.fromQueryParam(formats));
    }

    private StreamingOutput retrieveAuditEvent(Format format) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
       // String esURL = arcDev.getElasticSearchURL();
        String esURL = null;
        if (esURL == null) {
            throw new WebApplicationException(
                    Response.status(Response.Status.NOT_FOUND)
                            .entity("Elastic Search URL configuration missing.")
                            .build());
        }

        Response response = queryElasticSearch(esURL);
        AuditService.auditLogUsed(device, httpRequest);
        return new StreamingOutput() {
            @Override
            public void write(OutputStream out) throws IOException, WebApplicationException {
                format.writeTo(AuditEventRS.this, response, out);
            }
        };
    }

    private Response queryElasticSearch(String esURL) {
        Client client = ClientBuilder.newBuilder().build();
        WebTarget target = client.target(esURL);
        //TODO
        // target = target.path(index);
        // target = target.path("_search");
        // target = target.queryParam(name, values);
        return target.request().get();
    }

    private enum Format {
        XML {
            @Override
            public void writeTo(AuditEventRS auditEvent, Response response, OutputStream out) {
                auditEvent.writeJSONTo(response, out);

            }
        }, JSON {
            @Override
            public void writeTo(AuditEventRS auditEvent, Response response, OutputStream out) {
                auditEvent.writeXMLTo(response, out);
            }
        };

        public static Format fromQueryParam(List<String> formats) {
            for (String format : formats) {
                switch (format.toLowerCase()) {
                    case "application/json+fhir":
                        return JSON;
                    case "application/xml+fhir":
                        return XML;
                }
            }
            return JSON;
        }

        public abstract void writeTo(AuditEventRS auditEvent, Response response, OutputStream out);
    }

    private void writeXMLTo(Response response, OutputStream out) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        String tpluri = arcDev.getAudit2XmlFhirTemplateURI();
        //TODO
    }

    private void writeJSONTo(Response response, OutputStream out) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        String tpluri = arcDev.getAudit2JsonFhirTemplateURI();
        //TODO
    }

    private static void transform(String auditMessage, String tpluri, OutputStream out) throws TransformerException {
        Templates tpl = TemplatesCache.getDefault().get(StringUtils.replaceSystemProperties(tpluri));
        tpl.newTransformer().transform(new StreamSource(new StringReader(auditMessage)), new StreamResult(out));
    }
}
