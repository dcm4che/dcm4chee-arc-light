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

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.DicomConfiguration;
import org.dcm4che3.conf.json.JsonWriter;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.*;
import org.dcm4che3.net.pdu.AAbort;
import org.dcm4che3.net.pdu.AAssociateRJ;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.keycloak.KeycloakContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.Json;
import javax.json.stream.JsonGenerator;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Feb 2016
 */
@RequestScoped
@Path("aets/{AETitle}/dimse/{RemoteAET}")
public class EchoRS {
    private static final Logger LOG = LoggerFactory.getLogger(EchoRS.class);
    private static final String SUPER_USER_ROLE = "super-user-role";

    @Inject
    private DicomConfiguration conf;

    @Inject
    private Device device;

    @PathParam("AETitle")
    private String aet;

    @PathParam("RemoteAET")
    private String remoteAET;

    @Context
    private HttpServletRequest request;

    @QueryParam("host")
    private String host;

    @QueryParam("port")
    @Pattern(regexp = "^[1-9]\\d{0,4}+$")
    private String port;

    @Override
    public String toString() {
        String requestURI = request.getRequestURI();
        String queryString = request.getQueryString();
        return queryString == null ? requestURI : requestURI + '?' + queryString;
    }

    private ArchiveAEExtension getArchiveAE() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        return ae == null || !ae.isInstalled() ? null : ae.getAEExtension(ArchiveAEExtension.class);
    }

    private ApplicationEntity getRemoteApplicationEntity() {
        try {
            return conf.findApplicationEntity(remoteAET);
        } catch (ConfigurationException e) {
            throw new WebApplicationException(
                    errResponse(e.getMessage(), Response.Status.NOT_FOUND));
        }
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

    private AAssociateRQ createAARQ() {
        AAssociateRQ aarq = new AAssociateRQ();
        aarq.addPresentationContextFor(UID.Verification, UID.ImplicitVRLittleEndian);
        return aarq;
    }

    @POST
    @Produces("application/json")
    public StreamingOutput echo() {
        logRequest();
        int remotePort = parseInt(port);
        ArchiveAEExtension arcAE = getArchiveAE();
        if (arcAE == null)
            throw new WebApplicationException(errResponse(
                    "No such Application Entity: " + aet, Response.Status.NOT_FOUND));

        validateAcceptedUserRoles(arcAE);
        ApplicationEntity remote = host != null && remotePort > 0 ? createRemoteAE(remotePort) : getRemoteApplicationEntity();
        try {
            Association as = null;
            long t1, t2;
            Result result = new Result();
            try {
                t1 = System.currentTimeMillis();
                as = arcAE.getApplicationEntity().connect(remote, createAARQ());
                t2 = System.currentTimeMillis();
                result.connectionTime = Long.toString(t2 - t1);
                try {
                    DimseRSP rsp = as.cecho();
                    try {
                        rsp.next();
                        t1 = System.currentTimeMillis();
                        result.echoTime = Long.toString(t1 - t2);
                    } catch (IOException e) {
                        result.error(Result.Code.FailedToSendCEchoRQ, e);
                    }
                } catch (IOException e) {
                    result.error(Result.Code.FailedToReceiveCEchoRSP, e);
                }
            } catch (IncompatibleConnectionException e) {
                result.error(Result.Code.IncompatibleConnection, e);
            } catch (AAssociateRJ e) {
                result.error(Result.Code.AssociationRejected, e);
            } catch (IOException e) {
                result.error(Result.Code.FailedToConnect, e);
            } finally {
                if (as != null) {
                    try {
                        t1 = System.currentTimeMillis();
                        as.release();
                        t2 = System.currentTimeMillis();
                        result.releaseTime = Long.toString(t2 - t1);
                    } catch (IOException e) {
                        result.error(Result.Code.FailedToRelease, e);
                    }
                }
            }
            return result;
        } catch (Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    private void logRequest() {
        LOG.info("Process {} {} from {}@{}",
                request.getMethod(),
                toString(),
                request.getRemoteUser(),
                request.getRemoteHost());
    }

    private ApplicationEntity createRemoteAE(int remotePort) {
        Device device = new Device();
        device.setDeviceName(remoteAET.toLowerCase());
        device.setInstalled(true);
        Connection conn = new Connection();
        conn.setHostname(host);
        conn.setPort(remotePort);
        device.addConnection(conn);
        ApplicationEntity remoteAE = new ApplicationEntity();
        remoteAE.setAETitle(remoteAET);
        remoteAE.addConnection(conn);
        device.addApplicationEntity(remoteAE);
        return remoteAE;
    }

    private static class Result implements StreamingOutput {
        enum Code {
            Success(null),
            IncompatibleConnection("Incompatible Connection: "),
            FailedToConnect("Failed to connect: "),
            AssociationRejected("Association rejected: "),
            FailedToSendCEchoRQ("Failed to send C-ECHO-RSP: "),
            FailedToReceiveCEchoRSP("Failed to receive C-ECHO-RSP: "),
            FailedToRelease("Failed to release association: ");

            final String prefix;

            Code(String prefix) {
                this.prefix = prefix;
            }

            String errorMessage(Exception ex) {
                return prefix + (
                        (ex instanceof IncompatibleConnectionException
                                || ex instanceof AAssociateRJ
                                || ex instanceof AAbort
                        )
                                ? ex.getMessage()
                                : ex
                );
            }
        }

        Code code = Code.Success;
        Exception exception;
        String connectionTime;
        String echoTime;
        String releaseTime;

        void error(Code code, Exception e) {
            if (exception == null) {
                this.code = code;
                this.exception = e;
            }
        }

        @Override
        public void write(OutputStream out) {
            JsonGenerator gen = Json.createGenerator(out);
            JsonWriter writer = new JsonWriter(gen);
            gen.writeStartObject();
            writer.writeNotNullOrDef("result", Integer.toString(code.ordinal()), null);
            if (exception != null)
                writer.writeNotNullOrDef("errorMessage", code.errorMessage(exception), null);
            writer.writeNotNullOrDef("connectionTime", connectionTime, null);
            writer.writeNotNullOrDef("echoTime", echoTime, null);
            writer.writeNotNullOrDef("releaseTime", releaseTime, null);
            gen.writeEnd();
            gen.flush();
        }
    }

    private static int parseInt(String s) {
        return s != null ? Integer.parseInt(s) : 0;
    }

    private void validateAcceptedUserRoles(ArchiveAEExtension arcAE) {
        KeycloakContext keycloakContext = KeycloakContext.valueOf(request);
        if (keycloakContext.isSecured() && !keycloakContext.isUserInRole(System.getProperty(SUPER_USER_ROLE))) {
            if (!arcAE.isAcceptedUserRole(keycloakContext.getRoles()))
                throw new WebApplicationException(
                        "Application Entity " + arcAE.getApplicationEntity().getAETitle() + " does not list role of accessing user",
                        Response.Status.FORBIDDEN);
        }
    }
}
