/*
 * *** BEGIN LICENSE BLOCK *****
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
 * Portions created by the Initial Developer are Copyright (C) 2015-2023
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
 * *** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.host.rs;

import jakarta.enterprise.context.RequestScoped;
import jakarta.json.Json;
import jakarta.json.stream.JsonGenerator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Oct 2023
 */

@RequestScoped
@Path("/host")
public class HostRS {
    private static final Logger LOG = LoggerFactory.getLogger(HostRS.class);

    @Context
    private HttpServletRequest request;

    @GET
    @NoCache
    @Path("/{host}")
    @Produces("application/json")
    public Response listHosts(@PathParam("host") String host) {
        logRequest();
        try {
            return Response.ok(new InetAddresses(host)).build();
        } catch (UnknownHostException e) {
            return errResponse("No IP address for the host could be found: " + host, Response.Status.NOT_FOUND);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private static class InetAddresses implements StreamingOutput {
        private final InetAddress[] inetAddresses;
        private final long lookupTime;

        private InetAddresses(String host) throws UnknownHostException {
            long start= System.currentTimeMillis();
            this.inetAddresses = InetAddress.getAllByName(host);
            this.lookupTime = System.currentTimeMillis() - start;
        }

        @Override
        public void write(OutputStream out) {
            try (JsonGenerator gen = Json.createGenerator(out)) {
                gen.writeStartObject();
                gen.write("dnsLookupTime", lookupTime);
                gen.writeStartArray("hosts");
                for (InetAddress inetAddress : inetAddresses) {
                    long startRDNSLookup = System.currentTimeMillis();
                    String hostName = inetAddress.getHostName();
                    long endRDNSLookup = System.currentTimeMillis();
                    gen.writeStartObject();
                    gen.write("rdnsLookupTime", endRDNSLookup - startRDNSLookup);
                    gen.write("hostName", hostName);
                    gen.write("hostAddress", inetAddress.getHostAddress());
                    gen.writeEnd();
                }
                gen.writeEnd();
                gen.writeEnd();
            }
        }
    }

    private void logRequest() {
        LOG.info("Process GET {} from {}@{}",
                request.getRequestURI(),
                request.getRemoteUser(),
                request.getRemoteHost());
    }

    private Response errResponse(String errorMessage, Response.Status status) {
        return errResponseAsTextPlain("{\"errorMessage\":\"" + errorMessage + "\"}", status);
    }

    private Response errResponseAsTextPlain(String errorMsg, Response.Status status) {
        return Response.status(status)
                .entity(errorMsg)
                .type("text/plain")
                .build();
    }

    private String exceptionAsString(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
