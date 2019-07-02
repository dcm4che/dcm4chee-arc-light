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
 * Portions created by the Initial Developer are Copyright (C) 2019
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

package org.dcm4chee.arc.metrics.rs;

import org.dcm4chee.arc.metrics.MetricsService;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.DoubleSummaryStatistics;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since July 2019
 */

@RequestScoped
@Path("/metrics")
public class MetricsRS {

    private static final Logger LOG = LoggerFactory.getLogger(MetricsRS.class);

    @Inject
    private MetricsService metricsService;

    @Context
    private HttpServletRequest request;

    @QueryParam("start")
    @Pattern(regexp = "^(?:[1-9]|[12345][0-9]|60)$")
    @DefaultValue("60")
    private String start;

    @QueryParam("bin")
    @Pattern(regexp = "^(?:[1-9]|[12345][0-9]|60)$")
    @DefaultValue("1")
    private String binSize;

    @QueryParam("limit")
    @Pattern(regexp = "^(?:[1-9]|[12345][0-9]|60)$")
    private String limit;

    @GET
    @NoCache
    @Path("/{name}")
    public Response getMetrics(@PathParam("name") String name) {
        logRequest();
        try {
            return Response.ok((StreamingOutput) out -> {
                JsonGenerator gen = Json.createGenerator(out);
                gen.writeStartArray();
                metricsService.forEach(
                        name,
                        parseInt(start),
                        parseInt(limit),
                        parseInt(binSize),
                        dss -> write(dss, gen));
                gen.writeEnd();
                gen.flush();
            }).build();
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private int parseInt(String s) {
        return s != null ? Integer.parseInt(s) : 0;
    }

    private void logRequest() {
        LOG.info("Process {} {}?{} from {}@{}",
                request.getMethod(),
                request.getRequestURI(),
                request.getQueryString(),
                request.getRemoteUser(),
                request.getRemoteHost());
    }

    private void write(DoubleSummaryStatistics dss, JsonGenerator gen) {
        gen.writeStartObject();
        long count = dss.getCount();
        gen.write("count", count);
        if (count > 0) {
            gen.write("avg", dss.getAverage());
            gen.write("min", dss.getMin());
            gen.write("max", dss.getMax());
        }
        gen.writeEnd();
    }

    private Response errResponseAsTextPlain(String errorMsg, Response.Status status) {
        LOG.warn("Response {} caused by {}", status, errorMsg);
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
