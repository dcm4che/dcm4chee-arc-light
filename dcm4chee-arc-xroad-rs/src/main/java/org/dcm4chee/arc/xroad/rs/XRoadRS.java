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
 *
 * **** END LICENSE BLOCK *****
 *
 */

package org.dcm4chee.arc.xroad.rs;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.xroad.XRoadException;
import org.dcm4chee.arc.xroad.XRoadServiceProvider;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Feb 2018
 */
@RequestScoped
@Path("/xroad")
public class XRoadRS {
    private static final Logger LOG = LoggerFactory.getLogger(XRoadRS.class);

    @Context
    private HttpServletRequest request;

    @Inject
    private Device device;

    @Inject
    private XRoadServiceProvider service;

    @GET
    @NoCache
    @Path("/RR441/{PatientID}")
    @Produces("application/dicom+json,application/json")
    public Response rr441(@PathParam("PatientID") IDWithIssuer patientID) throws Exception {
        logRequest();
        Map<String, String> props = device.getDeviceExtension(ArchiveDeviceExtension.class)
                .getXRoadProperties();
        String endpoint = props.get("endpoint");
        if (endpoint == null)
            throw new ConfigurationException("Missing XRoadProperty endpoint");

        Attributes attrs;
        try {
            attrs = service.rr441(endpoint, props, patientID.getID());
        } catch (XRoadException e) {
            return errResponse(e.getMessage(), Response.Status.BAD_GATEWAY);
        }
        return (attrs == null ? Response.status(Response.Status.NOT_FOUND) : Response.ok(toJSON(attrs))).build();
    }

    private void logRequest() {
        LOG.info("Process {} {} from {}@{}", request.getMethod(), request.getRequestURI(),
                request.getRemoteUser(), request.getRemoteHost());
    }

    private Response errResponse(String errorMessage, Response.Status status) {
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity("{\"errorMessage\":\"" + errorMessage + "\"}")
                .build();
    }

    private StreamingOutput toJSON(Attributes attrs) {
        return out -> {
            try (JsonGenerator gen = Json.createGenerator(out)) {
                (new JSONWriter(gen)).write(attrs);
            }
        };
    }
}
