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

package org.dcm4chee.arc.hl7.rs;

import org.dcm4che3.audit.AuditMessages;
import org.dcm4che3.conf.api.ConfigurationNotFoundException;
import org.dcm4che3.conf.json.JsonWriter;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.hl7.HL7Exception;
import org.dcm4che3.hl7.HL7Message;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.json.JSONReader;
import org.dcm4chee.arc.hl7.RESTfulHL7Sender;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.patient.PatientService;
import org.dcm4chee.arc.qmgt.HttpServletRequestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonParsingException;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.*;
import java.net.ConnectException;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Aug 2017
 */

@RequestScoped
@Path("/hl7apps/{appName}/hl7/{externalAppName}/patients")
public class HL7RS {
    private static final Logger LOG = LoggerFactory.getLogger(HL7RS.class);

    @Inject
    private PatientService patientService;

    @Inject
    private RESTfulHL7Sender rsHL7Sender;

    @Inject
    private Event<PatientMgtContext> patientMgtEvent;

    @Context
    private HttpServletRequest request;

    @PathParam("appName")
    private String appName;

    @PathParam("externalAppName")
    private String externalAppName;

    @QueryParam("queue")
    private boolean queue;

    @POST
    @Consumes({"application/dicom+json,application/json"})
    @Produces("application/json")
    public Response createPatient(InputStream in) {
        logRequest();
        PatientMgtContext ctx = toPatientMgtContext(toAttributes(in));
        ctx.setEventActionCode(AuditMessages.EventActionCode.Create);
        return scheduleOrSendHL7("ADT^A28^ADT_A05", ctx);
    }

    @PUT
    @Consumes({"application/dicom+json,application/json"})
    @Produces("application/json")
    public Response updatePatient(InputStream in) {
        logRequest();
        PatientMgtContext ctx = toPatientMgtContext(toAttributes(in));
        ctx.setEventActionCode(AuditMessages.EventActionCode.Update);
        return scheduleOrSendHL7("ADT^A31^ADT_A05", ctx);
    }

    private PatientMgtContext toPatientMgtContext(Attributes attrs) {
        PatientMgtContext ctx = patientService.createPatientMgtContextWEB(request);
        ctx.setAttributes(attrs);
        return ctx;
    }

    private Attributes toAttributes(InputStream in) {
        JSONReader reader;
        try {
            reader = new JSONReader(Json.createParser(new InputStreamReader(in, "UTF-8")));
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
        try {
            return reader.readDataset(null);
        } catch (JsonParsingException e) {
            throw new WebApplicationException(buildErrorResponse(e.getMessage() + " at location : "
                    + e.getLocation(), Response.Status.BAD_REQUEST));
        }
    }

    @POST
    @Path("/{priorPatientID}/merge")
    @Consumes({"application/dicom+json,application/json"})
    @Produces("application/json")
    public Response mergePatient(@PathParam("priorPatientID") IDWithIssuer priorPatientID, InputStream in) {
        return mergePatientOrChangePID(priorPatientID, in, "ADT^A40^ADT_A39");
    }

    @POST
    @Path("/{priorPatientID}/changeid")
    @Consumes({"application/dicom+json,application/json"})
    @Produces("application/json")
    public Response changePatientID(@PathParam("priorPatientID") IDWithIssuer priorPatientID, InputStream in) {
        return mergePatientOrChangePID(priorPatientID, in, "ADT^A47^ADT_A30");
    }

    private Response mergePatientOrChangePID(IDWithIssuer priorPatientID, InputStream in, String msgType) {
        logRequest();
        PatientMgtContext ctx = toPatientMgtContext(toAttributes(in));
        ctx.setPreviousAttributes(priorPatientID.exportPatientIDWithIssuer(null));
        ctx.setEventActionCode(AuditMessages.EventActionCode.Update);
        return scheduleOrSendHL7(msgType, ctx);
    }

    private Response scheduleOrSendHL7(String msgType, PatientMgtContext ctx) {
        ctx.setHttpServletRequestInfo(HttpServletRequestInfo.valueOf(request));
        try {
            if (queue) {
                rsHL7Sender.scheduleHL7Message(msgType, ctx, appName, externalAppName);
                return Response.accepted().build();
            }
            else {
                HL7Message ack = rsHL7Sender.sendHL7Message(msgType, ctx, appName, externalAppName);
                patientMgtEvent.fire(ctx);
                return buildResponse(ack);
            }
        } catch (ConnectException e) {
            return buildErrorResponse(e.getMessage(), Response.Status.GATEWAY_TIMEOUT);
        } catch (IOException e) {
            return buildErrorResponse(e.getMessage(), Response.Status.BAD_GATEWAY);
        } catch (ConfigurationNotFoundException e) {
            return buildErrorResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (Exception e) {
            return buildErrorResponse(e.getMessage(), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private void logRequest() {
        LOG.info("Process {} {} from {}@{}", request.getMethod(), request.getRequestURI(),
                request.getRemoteUser(), request.getRemoteHost());
    }

    private Response buildErrorResponse(String errorMessage, Response.Status status) {
        Object entity = "{\"errorMessage\":\"" + errorMessage + "\"}";
        return Response.status(status).entity(entity).build();
    }

    private Response buildResponse(HL7Message ack) {
        if (ack.getSegment("MSA") == null)
            return buildErrorResponse( "Missing MSA segment in response message", Response.Status.BAD_GATEWAY);

        String status = ack.getSegment("MSA").getField(1, null);
        return HL7Exception.AA.equals(status)
                ? Response.noContent().build()
                : Response.status(Response.Status.CONFLICT).entity(toStreamingOutput(ack, status)).build();
    }

    private StreamingOutput toStreamingOutput(HL7Message ack, String status) {
        HL7Segment msa = ack.getSegment("MSA");
        HL7Segment err = ack.getSegment("ERR");

        return new StreamingOutput() {
            @Override
            public void write(OutputStream out) throws IOException {
                JsonGenerator gen = Json.createGenerator(out);
                JsonWriter writer = new JsonWriter(gen);
                gen.writeStartObject();
                writer.writeNotNullOrDef("msa-1", status, null);
                writer.writeNotNullOrDef("msa-3", msa.getField(3, null), null);
                if (err != null) {
                    writer.writeNotNullOrDef("err-3", err.getField(3, null), null);
                    writer.writeNotNullOrDef("err-7", err.getField(7, null), null);
                    writer.writeNotNullOrDef("err-8", err.getField(8, null), null);
                }
                writer.writeNotNullOrDef("message", ack.toString(), null);
                gen.writeEnd();
                gen.flush();
            }
        };
    }
}
