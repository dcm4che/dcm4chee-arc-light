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
 * Portions created by the Initial Developer are Copyright (C) 2013-2021
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

package org.dcm4chee.keyvalue.rs;

import org.dcm4chee.arc.entity.KeyValue;
import org.dcm4chee.arc.keycloak.KeycloakContext;
import org.dcm4chee.arc.keyvalue.ContentTypeMismatchException;
import org.dcm4chee.arc.keyvalue.KeyValueService;
import org.dcm4chee.arc.keyvalue.UserMismatchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Aug 2022
 */
@Path("values")
@RequestScoped
public class KeyValueRS {

    private static final Logger LOG = LoggerFactory.getLogger(KeyValueRS.class);

    @Inject
    private KeyValueService service;

    @Context
    private HttpServletRequest request;

    @Context
    private HttpHeaders headers;

    @GET
    @Path("/{key}")
    public Response getValue(@PathParam("key") String key) {
        logRequest();
        KeyValue keyValue = service.getKeyValue(key, username());
        if (keyValue == null)
            return errResponse("There is no Value with the specified Key.", Response.Status.NOT_FOUND);
        return Response.ok(keyValue.getValue(), keyValue.getContentType()).build();
    }

    @PUT
    @Path("/{key}")
    public Response setValue(@PathParam("key") String key,
                             @QueryParam("share") @Pattern(regexp = "true|false") String share,
                             String value) {
        logRequest();
        try {
            String contentType = request.getContentType();
            service.setKeyValue(key, username(), Boolean.parseBoolean(share), value, contentType);
            return Response.noContent().build();
        } catch (UserMismatchException e) {
            return errResponse(e.getMessage(), Response.Status.FORBIDDEN);
        } catch (ContentTypeMismatchException e) {
            return errResponse(e.getMessage(), Response.Status.CONFLICT);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @DELETE
    @Path("/{key}")
    public Response deleteValue(@PathParam("key") String key) {
        logRequest();
        try {
            KeyValue keyValue = service.deleteKeyValue(key, username());
            if (keyValue == null)
                return errResponse("There is no Value with the specified Key.", Response.Status.NOT_FOUND);

            return Response.noContent().build();
        } catch (UserMismatchException e) {
            return errResponse(e.getMessage(), Response.Status.FORBIDDEN);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private String charset(String contentType) {
        if (contentType.contains("charset")) {
            String[] split = contentType.split(";");
            for (String s : split)
                if (s.contains("charset"))
                    return s.substring(s.indexOf("=") + 1);
        }
        return StandardCharsets.UTF_8.name();
    }

    private String username() {
        KeycloakContext keycloakContext = KeycloakContext.valueOf(request);
        return keycloakContext.isSecured()
                ? keycloakContext.getUserName()
                : null;
    }

    private void logRequest() {
        LOG.info("Process {} {} from {}@{}",
                request.getMethod(),
                toString(),
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

