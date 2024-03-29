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
 * Portions created by the Initial Developer are Copyright (C) 2016-2020
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

package org.dcm4chee.arc.proxy;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.SyncInvoker;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jan 2017
 */
@RequestScoped
@Path("/")
public class ProxyRS {

    private static final Logger LOG = LoggerFactory.getLogger(ProxyRS.class);

    @Inject
    private Device device;

    @Context
    private HttpServletRequest httpRequest;

    @Context
    private HttpHeaders httpHeaders;

    @GET
    public Response doGet() {
        logRequest();
        Response resp = invoker().get();
        return new ResponseDelegate(resp);
    }

    @Override
    public String toString() {
        String requestURI = httpRequest.getRequestURI();
        String queryString = httpRequest.getQueryString();
        return queryString == null ? requestURI : requestURI + '?' + queryString;
    }

    private void logRequest() {
        LOG.info("Process {} {} from {}@{}",
                httpRequest.getMethod(),
                toString(),
                httpRequest.getRemoteUser(),
                httpRequest.getRemoteHost());
    }

    private SyncInvoker invoker() {
        String proxyUpstreamURL = device.getDeviceExtension(ArchiveDeviceExtension.class)
                                        .getProxyUpstreamURL();
        if (proxyUpstreamURL == null)
            throw new WebApplicationException(
                    Response.status(Response.Status.NOT_FOUND)
                            .entity("Proxy Upstream URL configuration missing.")
                            .build());

        WebTarget target = ClientBuilder.newBuilder()
                            .build()
                            .target(proxyUpstreamURL + "?" + httpRequest.getQueryString());
        return target.request().headers((MultivaluedMap) httpHeaders.getRequestHeaders());
    }
}
