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
 * Portions created by the Initial Developer are Copyright (C) 2017
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

package org.dcm4chee.arc.monitor.rs;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jun 2016
 */

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Dimse;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.Json;
import javax.json.stream.JsonGenerator;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

@Path("/monitor")
@RequestScoped
public class ArchiveMonitor {
    private static final Logger LOG = LoggerFactory.getLogger(ArchiveMonitor.class);

    private static final String[] COMMANDS = {
            "C-STORE",
            "C-GET",
            "C-FIND",
            "C-MOVE",
            "C-ECHO",
            "N-EVENT-REPORT",
            "N-GET",
            "N-SET",
            "N-ACTION",
            "N-CREATE",
            "N-DELETE"
    };

    private static final Dimse[] RQs = {
            Dimse.C_STORE_RQ,
            Dimse.C_GET_RQ,
            Dimse.C_FIND_RQ,
            Dimse.C_MOVE_RQ,
            Dimse.C_ECHO_RQ,
            Dimse.N_EVENT_REPORT_RQ,
            Dimse.N_GET_RQ,
            Dimse.N_SET_RQ,
            Dimse.N_ACTION_RQ,
            Dimse.N_CREATE_RQ,
            Dimse.N_DELETE_RQ
    };

    private static final Dimse[] RSPs = {
            Dimse.C_STORE_RSP,
            Dimse.C_GET_RSP,
            Dimse.C_FIND_RSP,
            Dimse.C_MOVE_RSP,
            Dimse.C_ECHO_RSP,
            Dimse.N_EVENT_REPORT_RSP,
            Dimse.N_GET_RSP,
            Dimse.N_SET_RSP,
            Dimse.N_ACTION_RSP,
            Dimse.N_CREATE_RSP,
            Dimse.N_DELETE_RSP
    };

    @Inject
    private Device device;

    @Context
    private HttpServletRequest request;

    @GET
    @NoCache
    @Path("associations")
    @Produces("application/json")
    public StreamingOutput listOpenAssociations() {
        logRequest();
        return this::writeOpenAssociations;
    }

    @DELETE
    @Path("associations/{serialNo}")
    public void abortAssociation(@PathParam("serialNo") int serialNo) {
        logRequest();
        for (Association as : device.listOpenAssociations()) {
            if (as.getSerialNo() == serialNo) {
                as.abort();
                return;
            }
        }
        throw new WebApplicationException(Response.Status.NOT_FOUND);
    }

    @GET
    @NoCache
    @Path("/serverTime")
    @Produces("application/json")
    public String getServerTime() {
        logRequest();
        return "{\"serverTimeWithTimezone\": \""
                + new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(new Date()) + "\"}";
    }

    @GET
    @NoCache
    @Path("serverTimeZone")
    @Produces("application/json")
    public StreamingOutput getServerTimeZone() {
        logRequest();
        return out -> {
            try (JsonGenerator gen = Json.createGenerator(out)) {
                gen.writeStartObject();
                gen.write("timeZone", java.util.TimeZone.getDefault().getID());
                gen.write("offset", java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()));
                gen.writeEnd();
            }
        };
    }

    private void writeOpenAssociations(OutputStream out) {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        try (JsonGenerator gen = Json.createGenerator(out)) {
            gen.writeStartArray();
            for (Association as : device.listOpenAssociations()) {
                if (as == null || as.getAAssociateRQ() == null) continue;
                gen.writeStartObject();
                gen.write("serialNo", as.getSerialNo());
                gen.write("connectTime", df.format(new Date(as.getConnectTimeInMillis())));
                gen.write("initiated", as.isRequestor());
                gen.write("localAETitle", as.getLocalAET());
                gen.write("remoteAETitle", as.getRemoteAET());
                gen.writeStartObject("performedOps");
                for (int i = 0; i < COMMANDS.length; i++) {
                    writeCommand(gen, COMMANDS[i], as.getNumberOfReceived(RQs[i]), as.getNumberOfSent(RSPs[i]));
                }
                gen.writeEnd();
                gen.writeStartObject("invokedOps");
                for (int i = 0; i < COMMANDS.length; i++) {
                    writeCommand(gen, COMMANDS[i], as.getNumberOfSent(RQs[i]), as.getNumberOfReceived(RSPs[i]));
                }
                gen.writeEnd();
                for (String key : as.getPropertyNames()) {
                    Object value = as.getProperty(key);
                    if (value instanceof String) {
                        gen.write(key, (String) value);
                    }
                }
                gen.writeEnd();
            }
            gen.writeEnd();
        }
    }

    private static void writeCommand(JsonGenerator gen, String command, int rq, int rsp) {
        if (rq == 0) return;
        gen.writeStartObject(command);
        gen.write("RQ", rq);
        gen.write("RSP", rsp);
        gen.writeEnd();
    }

    private void logRequest() {
        LOG.info("Process {} {} from {}@{}", request.getMethod(), request.getRequestURI(),
                request.getRemoteUser(), request.getRemoteHost());
    }
}
