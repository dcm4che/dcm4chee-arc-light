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

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParsingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.hl7.IHL7ApplicationCache;
import org.dcm4che3.conf.json.JsonWriter;
import org.dcm4che3.data.*;
import org.dcm4che3.hl7.HL7Exception;
import org.dcm4che3.hl7.HL7Message;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.json.JSONReader;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.HL7DeviceExtension;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.hl7.ArchiveHL7Message;
import org.dcm4chee.arc.hl7.HL7Sender;
import org.dcm4chee.arc.hl7.HL7SenderUtils;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.patient.PatientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

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
    private IHL7ApplicationCache hl7AppCache;

    @Inject
    private PatientService patientService;

    @Inject
    private HL7Sender hl7Sender;

    @Context
    private HttpServletRequest request;

    @PathParam("appName")
    private String appName;

    @PathParam("externalAppName")
    private String externalAppName;

    @QueryParam("queue")
    private boolean queue;

    @Override
    public String toString() {
        String requestURI = request.getRequestURI();
        String queryString = request.getQueryString();
        return queryString == null ? requestURI : requestURI + '?' + queryString;
    }

    @POST
    @Consumes({"application/dicom+json,application/json"})
    @Produces("application/json")
    public Response createPatient(InputStream in) {
        logRequest();
        PatientMgtContext ctx = toPatientMgtContext(toAttributes(in));
        if (ctx.getPatientIDs().isEmpty())
            return errResponse(
                    "Missing patient identifier with trusted assigning authority in " + ctx.getPatientIDs(),
                    Response.Status.BAD_REQUEST);

        return scheduleOrSendHL7("ADT^A28^ADT_A05", ctx);
    }

    @PUT
    @Path("/{priorPatientID}")
    @Consumes({"application/dicom+json,application/json"})
    @Produces("application/json")
    public Response updatePatient(
            @PathParam("priorPatientID") String multiplePriorPatientIDs,
            @QueryParam("merge") @Pattern(regexp = "true|false") @DefaultValue("false") String merge,
            InputStream in) {
        logRequest();
        Collection<IDWithIssuer> trustedPriorPatientIDs = trustedPatientIDs(multiplePriorPatientIDs);
        if (trustedPriorPatientIDs.isEmpty())
            return errResponse(
                    "Missing prior patient identifier with trusted assigning authority in " + multiplePriorPatientIDs,
                    Response.Status.BAD_REQUEST);

        String msgType = "ADT^A31^ADT_A05";
        PatientMgtContext ctx = toPatientMgtContext(toAttributes(in));
        if (ctx.getPatientIDs().isEmpty())
            return errResponse(
                    "Missing patient identifier with trusted assigning authority in " + ctx.getPatientIDs(),
                    Response.Status.BAD_REQUEST);

        boolean mergePatients = Boolean.parseBoolean(merge);
        boolean patientMatch = isPatientMatch(ctx.getPatientIDs(), trustedPriorPatientIDs);
        if (!patientMatch) {
            ctx.setPreviousAttributes(exportPatientIDsWithIssuer(new Attributes(), trustedPriorPatientIDs));
            msgType = mergePatients ? "ADT^A40^ADT_A39" : "ADT^A47^ADT_A30";
        } else if (mergePatients)
            return errResponse("Circular merge of patients not allowed.", Response.Status.BAD_REQUEST);

        return scheduleOrSendHL7(msgType, ctx);
    }

    private boolean isPatientMatch(
            Collection<IDWithIssuer> targetPatientIDs, Collection<IDWithIssuer> trustedPriorPatientIDs) {
        for (IDWithIssuer trustedPriorPatientID : trustedPriorPatientIDs)
            if (targetPatientIDs.contains(trustedPriorPatientID))
                return true;

        return false;
    }

    @PUT
    @Consumes({"application/dicom+json,application/json"})
    @Produces("application/json")
    public Response updatePatient1(InputStream in) {
        logRequest();
        PatientMgtContext ctx = toPatientMgtContext(toAttributes(in));
        if (ctx.getPatientIDs().isEmpty())
            return errResponse(
                    "Missing patient identifier with trusted assigning authority in " + ctx.getPatientIDs(),
                    Response.Status.BAD_REQUEST);

        return scheduleOrSendHL7("ADT^A31^ADT_A05", ctx);
    }

    private Collection<IDWithIssuer> trustedPatientIDs(String multiplePatientIDs) {
        String[] patientIDs = multiplePatientIDs.split("~");
        Set<IDWithIssuer> patientIdentifiers = new LinkedHashSet<>(patientIDs.length);
        for (String cx : patientIDs)
            patientIdentifiers.add(new IDWithIssuer(cx));
        return device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class)
                     .withTrustedIssuerOfPatientID(patientIdentifiers);
    }

    private PatientMgtContext toPatientMgtContext(Attributes attrs) {
        PatientMgtContext ctx = patientService.createPatientMgtContextWEB(HttpServletRequestInfo.valueOf(request));
        ctx.setAttributes(attrs);
        ctx.setPatientIDs(device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class)
                            .withTrustedIssuerOfPatientID(ctx.getPatientIDs()));
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
    public Response mergePatient(@PathParam("priorPatientID") String multiplePriorPatientIDs, InputStream in) {
        Collection<IDWithIssuer> trustedPriorPatientIDs = trustedPatientIDs(multiplePriorPatientIDs);
        if (trustedPriorPatientIDs.isEmpty())
            return errResponse(
                    "Missing prior patient identifier with trusted assigning authority in " + multiplePriorPatientIDs,
                    Response.Status.BAD_REQUEST);

        return mergePatientOrChangePID(trustedPriorPatientIDs, in, "ADT^A40^ADT_A39");
    }

    @POST
    @Path("/{priorPatientID}/changeid")
    @Consumes({"application/dicom+json,application/json"})
    @Produces("application/json")
    public Response changePatientID(@PathParam("priorPatientID") String multiplePriorPatientIDs, InputStream in) {
        Collection<IDWithIssuer> trustedPriorPatientIDs = trustedPatientIDs(multiplePriorPatientIDs);
        if (trustedPriorPatientIDs.isEmpty())
            return errResponse(
                    "Missing prior patient identifier with trusted assigning authority in " + multiplePriorPatientIDs,
                    Response.Status.BAD_REQUEST);

        return mergePatientOrChangePID(trustedPriorPatientIDs, in, "ADT^A47^ADT_A30");
    }

    private Response mergePatientOrChangePID(
            Collection<IDWithIssuer> trustedPriorPatientIDs, InputStream in, String msgType) {
        logRequest();
        PatientMgtContext ctx = toPatientMgtContext(toAttributes(in));
        if (ctx.getPatientIDs().isEmpty())
            return errResponse(
                    "Missing patient identifier with trusted assigning authority in " + ctx.getPatientIDs(),
                    Response.Status.BAD_REQUEST);

        ctx.setPreviousPatientIDs(trustedPriorPatientIDs);
        ctx.setPreviousAttributes(exportPatientIDsWithIssuer(new Attributes(), trustedPriorPatientIDs));
        return scheduleOrSendHL7(msgType, ctx);
    }

    private Attributes exportPatientIDsWithIssuer(Attributes attrs, Collection<IDWithIssuer> idWithIssuers) {
        attrs.setNull(Tag.PatientID, VR.LO);
        attrs.setNull(Tag.IssuerOfPatientID, VR.LO);
        attrs.setNull(Tag.IssuerOfPatientIDQualifiersSequence, VR.SQ);
        attrs.setNull(Tag.OtherPatientIDsSequence, VR.SQ);
        Iterator<IDWithIssuer> iter = idWithIssuers.iterator();
        attrs = iter.next().exportPatientIDWithIssuer(attrs);
        Sequence otherPatientIDsSequence = attrs.ensureSequence(
                Tag.OtherPatientIDsSequence,
                idWithIssuers.size() - 1);
        while (iter.hasNext())
            otherPatientIDsSequence.add(iter.next().exportPatientIDWithIssuer(null));
        return attrs;
    }

    private Response scheduleOrSendHL7(String msgType, PatientMgtContext ctx) {
        try {
            HL7Application sender = device.getDeviceExtensionNotNull(HL7DeviceExtension.class)
                                    .getHL7Application(appName, true);
            if (sender == null)
                return errResponse("Sending HL7 Application not configured : " + appName, Response.Status.NOT_FOUND);

            HL7Application receiver = hl7AppCache.findHL7Application(externalAppName);
            String outgoingPatientUpdateTemplateURI = device.getDeviceExtension(ArchiveDeviceExtension.class)
                                                            .getOutgoingPatientUpdateTemplateURI();
            byte[] data = HL7SenderUtils.data(sender, appName, receiver, ctx.getAttributes(), ctx.getPreviousAttributes(),
                                            msgType, outgoingPatientUpdateTemplateURI,null, null);
            if (queue) {
                hl7Sender.scheduleMessage(ctx.getHttpServletRequestInfo(), data);
                return Response.accepted().build();
            }

            ArchiveHL7Message hl7Msg = new ArchiveHL7Message(data);
            hl7Msg.setHttpServletRequestInfo(ctx.getHttpServletRequestInfo());
            UnparsedHL7Message rsp = hl7Sender.sendMessage(sender, receiver, hl7Msg);
            return response(HL7Message.parse(rsp.data(), sender.getHL7DefaultCharacterSet()));
        } catch (ConnectException e) {
            return errResponse(e.getMessage(), Response.Status.GATEWAY_TIMEOUT);
        } catch (IOException e) {
            return errResponse(e.getMessage(), Response.Status.BAD_GATEWAY);
        } catch (ConfigurationException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private void logRequest() {
        LOG.info("Process {} {} from {}@{}",
                request.getMethod(),
                toString(),
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
