/*
 * ** BEGIN LICENSE BLOCK *****
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
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * J4Care.
 * Portions created by the Initial Developer are Copyright (C) 2016
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
 * ** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.arr;

import dcm4chee.arc.audit.arr.AuditLogUsed;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;

import javax.enterprise.context.RequestScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import java.io.InputStream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jan 2017
 */
@RequestScoped
@Path("/{path: .*}")
public class ProxyRS {

    @Inject
    private Device device;

    @Inject
    private Event<AuditLogUsed> auditLogUsedEvent;

    @Context
    private HttpServletRequest httpRequest;

    @Context
    private Request request;

    @PathParam("path")
    private String path;

    @GET
    public Response doGet(InputStream in) {
        String targetURL = createURL(httpRequest);
        Client client = ClientBuilder.newBuilder().build();
        WebTarget target = client.target(targetURL);
        Response response = target.request().get();
        auditLogUsedEvent.fire(new AuditLogUsed(httpRequest));
        ResponseDelegate resp = new ResponseDelegate(response);
        return resp;
    }

    @POST
    public Response doPost(InputStream in) {
        String targetURL = createURL(httpRequest);
        Client client = ClientBuilder.newBuilder().build();
        WebTarget target = client.target(targetURL);
        Response response = target.request().post(Entity.entity(in, MediaType.TEXT_HTML_TYPE));
        auditLogUsedEvent.fire(new AuditLogUsed(httpRequest));
        ResponseDelegate resp = new ResponseDelegate(response);
        return resp;
    }

    private String createURL(HttpServletRequest req) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        String arrURL = arcDev.getAuditRecordRepositoryURL();
        StringBuffer sb = new StringBuffer();
        sb = req.getRequestURI().lastIndexOf("arr/") == -1
                ? sb.append(arrURL).append("/app/kibana")
                : sb.append(arrURL).append(req.getRequestURI());
        return sb.toString();
    }
}
