/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 *  The contents of this file are subject to the Mozilla Public License Version
 *  1.1 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  http://www.mozilla.org/MPL/
 *
 *  Software distributed under the License is distributed on an "AS IS" basis,
 *  WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 *  for the specific language governing rights and limitations under the
 *  License.
 *
 *  The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 *  Java(TM), hosted at https://github.com/dcm4che.
 *
 *  The Initial Developer of the Original Code is
 *  J4Care.
 *  Portions created by the Initial Developer are Copyright (C) 2015-2019
 *  the Initial Developer. All Rights Reserved.
 *
 *  Contributor(s):
 *  See @authors listed below
 *
 *  Alternatively, the contents of this file may be used under the terms of
 *  either the GNU General Public License Version 2 or later (the "GPL"), or
 *  the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 *  in which case the provisions of the GPL or the LGPL are applicable instead
 *  of those above. If you wish to allow use of your version of this file only
 *  under the terms of either the GPL or the LGPL, and not to allow others to
 *  use your version of this file under the terms of the MPL, indicate your
 *  decision by deleting the provisions above and replace them with the notice
 *  and other provisions required by the GPL or the LGPL. If you do not delete
 *  the provisions above, a recipient may use your version of this file under
 *  the terms of any one of the MPL, the GPL or the LGPL.
 *
 */

package org.dcm4chee.arc.dimse.rs;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.net.Status;
import org.dcm4che3.util.TagUtils;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.procedure.ImportResult;
import org.dcm4chee.arc.procedure.ProcedureService;
import org.dcm4chee.arc.query.util.QueryAttributes;
import org.dcm4chee.arc.validation.constraints.InvokeValidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since April 2021
 */
@RequestScoped
@Path("aets/{AETitle}/dimse/{mwlscp}")
@InvokeValidate(type = MWLImportRS.class)
public class MWLImportRS {

    private static final Logger LOG = LoggerFactory.getLogger(MWLImportRS.class);

    @Context
    private HttpServletRequest request;

    @Context
    private UriInfo uriInfo;

    @PathParam("AETitle")
    private String aet;

    @PathParam("mwlscp")
    private String mwlscp;

    @QueryParam("fuzzymatching")
    @Pattern(regexp = "true|false")
    private String fuzzymatching;

    @QueryParam("filterbyscu")
    @Pattern(regexp = "true|false")
    private String filterbyscu;

    @QueryParam("delete")
    @Pattern(regexp = "true|false")
    private String delete;

    @QueryParam("test")
    @Pattern(regexp = "true|false")
    private String test;

    @QueryParam("priority")
    @Pattern(regexp = "0|1|2")
    private String priority;

    @Inject
    private ProcedureService procedureService;

    private QueryAttributes queryAttributes;

    @Override
    public String toString() {
        String requestURI = request.getRequestURI();
        String queryString = request.getQueryString();
        return queryString == null ? requestURI : requestURI + '?' + queryString;
    }

    public void validate() {
        LOG.info("Process {} {} from {}@{}",
                request.getMethod(),
                toString(),
                request.getRemoteUser(),
                request.getRemoteHost());
        this.queryAttributes = new QueryAttributes(uriInfo, null);
    }

    @POST
    @Path("/mwlitems/import/{destination}")
    @Produces("application/json")
    public Response mwlImport(@PathParam("destination") String destAET) {
        try {
            ImportResult result = procedureService.importMWL(
                    HttpServletRequestInfo.valueOf(request),
                    aet, mwlscp, destAET, priority(), queryAttributes,
                    Boolean.parseBoolean(fuzzymatching),
                    Boolean.parseBoolean(filterbyscu),
                    Boolean.parseBoolean(delete),
                    Boolean.parseBoolean(test));
            return Response.status(statusOf(result)).entity(toJson(result)).build();
        } catch (IllegalStateException| ConfigurationException e) {
            throw new WebApplicationException(errResponse(e.getMessage(), Response.Status.NOT_FOUND));
        } catch (IOException e) {
            throw new WebApplicationException(errResponse(e.getMessage(), Response.Status.BAD_GATEWAY));
        } catch (Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    private String toJson(ImportResult result) {
        StringBuilder sb = new StringBuilder(64);
        sb.append("{\"count\":").append(result.count);
        appendNotZeroTo(",\"created\":", result.created, sb);
        appendNotZeroTo(",\"updated\":", result.updated, sb);
        appendNotZeroTo(",\"deleted\":", result.deleted, sb);
        if (!result.exceptions.isEmpty()) {
            sb.append(",\"failures\":").append(result.exceptions.size());
            sb.append(",\"error\":\"").append(result.exceptions.get(0).getMessage()).append('\"');
        }
        return sb.append('}').toString();
    }

    private void appendNotZeroTo(String name, int n, StringBuilder sb) {
        if (n > 0) sb.append(name).append(n);
    }

    private static Response.Status statusOf(ImportResult result) {
        return result.exceptions.isEmpty() ? Response.Status.OK
                : result.created == 0 && result.updated == 0 && result.deleted == 0
                ? Response.Status.CONFLICT
                : Response.Status.ACCEPTED;
    }

    private Response errResponse(String msg, Response.Status status) {
        return errResponseAsTextPlain("{\"errorMessage\":\"" + msg + "\"}", status);
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

    private int priority() {
        return parseInt(priority, 0);
    }

    private static int parseInt(String s, int defval) {
        return s != null ? Integer.parseInt(s) : defval;
    }

    private static String warning(int status) {
        switch (status) {
            case Status.Success:
                return null;
            case Status.OutOfResources:
                return "A700: Refused: Out of Resources";
            case Status.IdentifierDoesNotMatchSOPClass:
                return "A900: Identifier does not match SOP Class";
        }
        return TagUtils.shortToHexString(status)
                + ((status & Status.UnableToProcess) == Status.UnableToProcess
                ? ": Unable to Process"
                : ": Unexpected status code");
    }
}
