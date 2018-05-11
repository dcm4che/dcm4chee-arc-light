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

import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
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
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since April 2017
 */
@RequestScoped
@Path("syslogsearch")
public class SyslogSearchRS {

    @Inject
    private Device device;

    @Context
    private HttpServletRequest httpRequest;

    @QueryParam("date")
    @Size(min = 1)
    @Pattern(regexp = "(ge20|le20)\\d\\d-\\d\\d-\\d\\d")
    private List<String> dates;

    @QueryParam("pri")
    private List<String> pri;

    @QueryParam("version")
    private List<String> version;

    @QueryParam("hostname")
    private List<String> hostname;

    @QueryParam("app-name")
    private List<String> app_name;

    @QueryParam("procid")
    private List<String> procid;

    @QueryParam("msg-id")
    private List<String> msg_id;

    @QueryParam("msg")
    private List<String> msg;

    @GET
    @Produces("application/json")
    public StreamingOutput retrieveSyslogEvent() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        //String esURL = arcDev.getElasticSearchURL();
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
                writeTo(response, out);
            }
        };
    }

    private Response queryElasticSearch(String esURL) {
        Client client = ClientBuilder.newBuilder().build();
        WebTarget target = client.target(esURL);
        //TODO
        // target = target.path(index);
        // target = target.path("_search");
        // target = target.queryParam(name1, values1);
        // target = target.queryParam(name2, values2);
        return target.request().get();
    }

    private void writeTo(Response response, OutputStream out) {
        JsonGenerator gen = Json.createGenerator(out);
        gen.writeStartArray();
        //TODO
        gen.writeEnd();
        gen.flush();
    }

}
