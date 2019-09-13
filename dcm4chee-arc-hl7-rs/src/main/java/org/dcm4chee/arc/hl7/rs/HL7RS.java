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

import org.dcm4che3.conf.api.ConfigurationNotFoundException;
import org.dcm4che3.conf.json.JsonWriter;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.hl7.HL7Exception;
import org.dcm4che3.hl7.HL7Message;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.json.JSONReader;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.HL7DeviceExtension;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4chee.arc.hl7.RESTfulHL7Sender;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.patient.PatientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonParsingException;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.*;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Aug 2017
 */

@RequestScoped
@Path("/hl7apps/{appName}/hl7/{externalAppName}/patients")
public class HL7RS {
    private static final Logger LOG = LoggerFactory.getLogger(HL7RS.class);

    @Inject
    private Device device;

    @Inject
    private PatientService patientService;

    @Inject
    private RESTfulHL7Sender rsHL7Sender;

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
        return scheduleOrSendHL7("ADT^A28^ADT_A05", toPatientMgtContext(toAttributes(in)));
    }

    @PUT
    @Path("/{priorPatientID}")
    @Consumes({"application/dicom+json,application/json"})
    @Produces("application/json")
    public Response updatePatient(
            @PathParam("priorPatientID") IDWithIssuer priorPatientID,
            @QueryParam("merge") @Pattern(regexp = "true|false") @DefaultValue("false") String merge,
            InputStream in) {
        logRequest();
        String msgType = "ADT^A31^ADT_A05";
        PatientMgtContext ctx = toPatientMgtContext(toAttributes(in));
        IDWithIssuer patientID = ctx.getPatientID();
        boolean mergePatients = Boolean.parseBoolean(merge);
        if (!patientID.equals(priorPatientID)) {
            ctx.setPreviousAttributes(priorPatientID.exportPatientIDWithIssuer(null));
            msgType = mergePatients ? "ADT^A40^ADT_A39" : "ADT^A47^ADT_A30";
        } else if (mergePatients)
            return errResponse("Circular merge of patients not allowed.", Response.Status.BAD_REQUEST);

        return scheduleOrSendHL7(msgType, ctx);
    }

    @PUT
    @Consumes({"application/dicom+json,application/json"})
    @Produces("application/json")
    public Response updatePatient1(InputStream in) {
        logRequest();
        return scheduleOrSendHL7("ADT^A31^ADT_A05", toPatientMgtContext(toAttributes(in)));
    }

    private PatientMgtContext toPatientMgtContext(Attributes attrs) {
        PatientMgtContext ctx = patientService.createPatientMgtContextWEB(
                HttpServletRequestInfo.valueOf(request));
        ctx.setAttributes(attrs);
        return ctx;
    }

    private Attributes toAttributes(InputStream in) {
        try {
            return new JSONReader(Json.createParser(new InputStreamReader(in, StandardCharsets.UTF_8)))
                    .readDataset(null);
        } catch (JsonParsingException e) {
            throw new WebApplicationException(errResponse(
                    e.getMessage() + " at location : " + e.getLocation(),
                    Response.Status.BAD_REQUEST));
        } catch (Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
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
        return scheduleOrSendHL7(msgType, ctx);
    }

    private Response scheduleOrSendHL7(String msgType, PatientMgtContext ctx) {
        try {
            HttpServletRequestInfo httpServletRequestInfo = HttpServletRequestInfo.valueOf(request);
            ctx.setHttpServletRequestInfo(httpServletRequestInfo);
            if (queue) {
                rsHL7Sender.scheduleHL7Message(msgType, ctx, appName, externalAppName);
                return Response.accepted().build();
            }
            else {
                HL7Application sender = getSendingHl7Application();
                UnparsedHL7Message rsp = rsHL7Sender.sendHL7Message(
                                            httpServletRequestInfo, msgType, ctx, sender, externalAppName);
                return response(HL7Message.parse(rsp.data(), sender.getHL7DefaultCharacterSet()));
            }
        } catch (ConnectException e) {
            return errResponse(e.getMessage(), Response.Status.GATEWAY_TIMEOUT);
        } catch (IOException e) {
            return errResponse(e.getMessage(), Response.Status.BAD_GATEWAY);
        } catch (ConfigurationNotFoundException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private HL7Application getSendingHl7Application() throws ConfigurationNotFoundException {
        HL7DeviceExtension hl7Dev = device.getDeviceExtensionNotNull(HL7DeviceExtension.class);
        HL7Application sender = hl7Dev.getHL7Application(appName, true);
        if (sender == null)
            throw new ConfigurationNotFoundException("Sending HL7 Application not configured : " + appName);
        return sender;
    }

    private void logRequest() {
        LOG.info("Process {} {}?{} from {}@{}",
                request.getMethod(),
                request.getRequestURI(),
                request.getQueryString(),
                request.getRemoteUser(),
                request.getRemoteHost());
    }

    private Response response(HL7Message ack) {
        if (ack.getSegment("MSA") == null)
            return errResponse( "Missing MSA segment in response message", Response.Status.BAD_GATEWAY);

        String status = ack.getSegment("MSA").getField(1, null);
        if (!HL7Exception.AA.equals(status)) {
            LOG.warn("Response Conflict caused by HL7 Exception Error Status {}", status);
            return Response.status(Response.Status.CONFLICT).entity(toStreamingOutput(ack, status)).build();
        }

        return Response.noContent().build();
    }

    private StreamingOutput toStreamingOutput(HL7Message ack, String status) {
        HL7Segment msa = ack.getSegment("MSA");
        HL7Segment err = ack.getSegment("ERR");

        return out -> {
                JsonGenerator gen = Json.createGenerator(out);
                JsonWriter writer = new JsonWriter(gen);
                gen.writeStartObject();
                writer.writeNotNullOrDef("msa-1", status, null);
                writer.writeNotNullOrDef("msa-3", msa.getField(3, null), null);
                if (err != null) {
                    writer.writeNotNullOrDef("err-3", err.getField(3, null), null);
                    writer.writeNotNullOrDef("err-7", err.getField(7, null), null);
                    String errComment = err.getField(8, null);
                    LOG.warn(errComment);
                    writer.writeNotNullOrDef("err-8", errComment, null);
                }
                writer.writeNotNullOrDef("message", ack.toString(), null);
                gen.writeEnd();
                gen.flush();
        };
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
}
