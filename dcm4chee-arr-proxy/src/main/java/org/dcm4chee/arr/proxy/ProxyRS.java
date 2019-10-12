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
 * Java(TM), hosted at https://github.com/dcm4che.
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

package org.dcm4chee.arr.proxy;

import org.dcm4che3.net.Device;
import org.dcm4che3.util.SafeClose;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.jboss.resteasy.core.Headers;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.SyncInvoker;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.*;
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

    @Context
    private HttpServletRequest httpRequest;

    @Context
    private HttpHeaders httpHeaders;

    @PathParam("path")
    private String path;

    @GET
    public Response doGet() {
        Response resp = invoker(false).get();
        AuditService.auditLogUsed(device, httpRequest);
        return new ResponseDelegate(resp);
    }

    @POST
    public Response doPost(InputStream in) {
        return new ResponseDelegate(invoker(true).post(createEntity(in)));
    }

    @PUT
    public Response doPut(InputStream in) {
        return new ResponseDelegate(invoker(true).put(createEntity(in)));
    }

    private Entity<InputStream> createEntity(InputStream in) {
        MediaType mediaType = httpHeaders.getMediaType();
        if (mediaType != null)
            return Entity.entity(in, mediaType);

        SafeClose.close(in);
        return null;
    }

    @DELETE
    public Response doDelete() {
        return new ResponseDelegate(invoker(true).delete());
    }

    private SyncInvoker invoker(boolean removeContentLength) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        String arrURL = arcDev.getAuditRecordRepositoryURL();
        if (arrURL == null) {
            throw new WebApplicationException(
                    Response.status(Response.Status.NOT_FOUND)
                            .entity("Audit Record Repository URL configuration missing.")
                            .build());
        }
        String targetURL = arrURL.charAt(arrURL.length()-1) != '/' ? arrURL + "/" + path : arrURL + path;
        WebTarget target = ClientBuilder.newBuilder().build().target(targetURL);
        MultivaluedMap<String, String> headers = httpHeaders.getRequestHeaders();
        if (removeContentLength) {
            Headers<String> newHeaders = new Headers<>();
            headers.entrySet().stream()
                    .filter(e -> !e.getKey().equalsIgnoreCase("Content-Length"))
                    .forEach(e -> newHeaders.addAll(e.getKey(), e.getValue()));
            headers = newHeaders;
        }
        return target.request().headers((MultivaluedMap) headers);
    }
}
