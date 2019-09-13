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
import org.dcm4che3.conf.api.IApplicationEntityCache;
import org.dcm4che3.conf.api.IDeviceCache;
import org.dcm4che3.conf.json.JsonWriter;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.*;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4che3.util.ReverseDNS;
import org.dcm4che3.util.TagUtils;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.qmgt.QueueSizeLimitExceededException;
import org.dcm4chee.arc.retrieve.ExternalRetrieveContext;
import org.dcm4chee.arc.retrieve.mgt.RetrieveManager;
import org.dcm4chee.arc.retrieve.scu.CMoveSCU;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jul 2017
 */
@RequestScoped
@Path("aets/{AETitle}/dimse/{ExternalAET}")
public class RetrieveRS {

    private static final Logger LOG = LoggerFactory.getLogger(RetrieveRS.class);

    @Context
    private HttpServletRequest request;

    @Inject
    private Device device;

    @Inject
    private IApplicationEntityCache aeCache;

    @Inject
    private IDeviceCache deviceCache;

    @Inject
    private Event<ExternalRetrieveContext> instancesRetrievedEvent;

    @PathParam("AETitle")
    private String aet;

    @PathParam("ExternalAET")
    private String externalAET;

    @QueryParam("priority")
    @Pattern(regexp = "0|1|2")
    private String priority;

    @QueryParam("dcmQueueName")
    @Pattern(regexp =
            "Retrieve1|" +
            "Retrieve2|" +
            "Retrieve3|" +
            "Retrieve4|" +
            "Retrieve5|" +
            "Retrieve6|" +
            "Retrieve7|" +
            "Retrieve8|" +
            "Retrieve9|" +
            "Retrieve10|" +
            "Retrieve11|" +
            "Retrieve12|" +
            "Retrieve13")
    private String queueName;

    @QueryParam("batchID")
    private String batchID;

    @QueryParam("dicomDeviceName")
    private String deviceName;

    @Inject
    private CMoveSCU moveSCU;

    @Inject
    private RetrieveManager retrieveManager;

    @Override
    public String toString() {
        return request.getRequestURI() + '?' + request.getQueryString();
    }

    @POST
    @Path("/studies/{StudyUID}/export/dicom:{DestinationAET}")
    @Produces("application/json")
    public Response exportStudy(
            @PathParam("StudyUID") String studyUID,
            @PathParam("DestinationAET") String destinationAET) {
        return export(destinationAET, studyUID);
    }

    @POST
    @Path("/studies/{StudyUID}/series/{SeriesUID}/export/dicom:{DestinationAET}")
    @Produces("application/json")
    public Response exportSeries(
            @PathParam("StudyUID") String studyUID,
            @PathParam("SeriesUID") String seriesUID,
            @PathParam("DestinationAET") String destinationAET) {
        return export(destinationAET, studyUID, seriesUID);
    }

    @POST
    @Path("/studies/{StudyUID}/series/{SeriesUID}/instances/{ObjectUID}/export/dicom:{DestinationAET}")
    @Produces("application/json")
    public Response exportSeries(
            @PathParam("StudyUID") String studyUID,
            @PathParam("SeriesUID") String seriesUID,
            @PathParam("ObjectUID") String objectUID,
            @PathParam("DestinationAET") String destinationAET) {
        return export(destinationAET, studyUID, seriesUID, objectUID);
    }

    @POST
    @Path("/studies/{StudyUID}/mark4retrieve/dicom:{DestinationAET}")
    @Produces("application/json")
    public Response markStudy4Retrieve(
            @PathParam("StudyUID") String studyUID,
            @PathParam("DestinationAET") String destinationAET) {
        return createRetrieveTask(destinationAET, studyUID);
    }

    @POST
    @Path("/studies/{StudyUID}/series/{SeriesUID}/mark4retrieve/dicom:{DestinationAET}")
    @Produces("application/json")
    public Response markSeries4Retrieve(
            @PathParam("StudyUID") String studyUID,
            @PathParam("SeriesUID") String seriesUID,
            @PathParam("DestinationAET") String destinationAET) {
        return createRetrieveTask(destinationAET, studyUID, seriesUID);
    }

    @POST
    @Path("/studies/{StudyUID}/series/{SeriesUID}/instances/{ObjectUID}/mark4retrieve/dicom:{DestinationAET}")
    @Produces("application/json")
    public Response markInstance4Retrieve(
            @PathParam("StudyUID") String studyUID,
            @PathParam("SeriesUID") String seriesUID,
            @PathParam("ObjectUID") String objectUID,
            @PathParam("DestinationAET") String destinationAET) {
        return createRetrieveTask(destinationAET, studyUID, seriesUID, objectUID);
    }

    private int priority() {
        return parseInt(priority, 0);
    }

    private static int parseInt(String s, int defval) {
        return s != null ? Integer.parseInt(s) : defval;
    }

    private Response export(String destAET, String... uids) {
        logRequest();
        if (uids[0].startsWith("csv"))
            return errResponse("Missing Content-type Header in 'Retrieve Studies specified in CSV from external archive' service " +
                            "causes invocation of 'Retrieve Study from external archive' service.",
                    Response.Status.BAD_REQUEST);

        try {
            validate();
            Attributes keys = toKeys(uids);
            return queueName != null
                    ? queueExport(destAET, toKeys(uids))
                    : export(destAET, keys);
        } catch (IllegalStateException | ConfigurationException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (IllegalArgumentException e) {
            return errResponse(e.getMessage(), Response.Status.BAD_REQUEST);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private Response createRetrieveTask(String destAET, String... uids) {
        logRequest();
        if (queueName == null)
            queueName = "Retrieve1";

        try {
            validate();
            Attributes keys = toKeys(uids);
            retrieveManager.createRetrieveTask(createExtRetrieveCtx(destAET, keys));
        } catch (IllegalStateException | IllegalArgumentException | ConfigurationException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
        return Response.noContent().build();
    }

    private void logRequest() {
        LOG.info("Process {} {}?{} from {}@{}",
                request.getMethod(),
                request.getRequestURI(),
                request.getQueryString(),
                request.getRemoteUser(),
                request.getRemoteHost());
    }

    private Response queueExport(String destAET, Attributes keys) {
        try {
            retrieveManager.scheduleRetrieveTask(
                    priority(), createExtRetrieveCtx(destAET, keys), null, 0L);
        } catch (QueueSizeLimitExceededException e) {
            return errResponse(e.getMessage(), Response.Status.SERVICE_UNAVAILABLE);
        }
        return Response.accepted().build();
    }

    private Response export(String destAET, Attributes keys)
            throws Exception {
        ApplicationEntity localAE = device.getApplicationEntity(aet, true);
        if (localAE == null || !localAE.isInstalled())
            throw new ConfigurationException("No such Application Entity: " + aet);

        Association as = moveSCU.openAssociation(localAE, externalAET);
        try {
            final DimseRSP rsp = moveSCU.cmove(as, priority(), destAET, keys);
            while (rsp.next());
            Attributes cmd = rsp.getCommand();
            instancesRetrievedEvent.fire(
                    createExtRetrieveCtx(destAET, keys)
                    .setRemoteHostName(ReverseDNS.hostNameOf(as.getSocket().getInetAddress()))
                    .setResponse(cmd));
            return status(cmd).entity(entity(cmd)).build();
        } finally {
            try {
                as.release();
            } catch (IOException e) {
                LOG.info("{}: Failed to release association:\\n", as, e);
            }
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

    private void validate() throws ConfigurationException {
        aeCache.findApplicationEntity(externalAET);
        if (deviceName != null) {
            Device device = deviceCache.findDevice(deviceName);
            ApplicationEntity ae = device.getApplicationEntity(aet, true);
            if (ae == null || !ae.isInstalled())
                throw new ConfigurationException("No such Application Entity: " + aet + " found in device: " + deviceName);

            validateQueue(device);
        } else
            validateQueue(device);
    }

    private void validateQueue(Device device) {
        if (queueName == null)
            return;
        
        device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class).getQueueDescriptorNotNull(queueName);
    }

    private ExternalRetrieveContext createExtRetrieveCtx(String destAET, Attributes keys) {
        return new ExternalRetrieveContext()
                .setDeviceName(deviceName != null ? deviceName : device.getDeviceName())
                .setQueueName(queueName)
                .setBatchID(batchID)
                .setLocalAET(aet)
                .setRemoteAET(externalAET)
                .setDestinationAET(destAET)
                .setHttpServletRequestInfo(HttpServletRequestInfo.valueOf(request))
                .setKeys(keys);
    }

    private Attributes toKeys(String[] iuids) {
        int n = iuids.length;
        Attributes keys = new Attributes(n + 1);
        keys.setString(Tag.QueryRetrieveLevel, VR.CS, QueryRetrieveLevel2.values()[n].name());
        keys.setString(Tag.StudyInstanceUID, VR.UI, iuids[0]);
        if (n > 1) {
            keys.setString(Tag.SeriesInstanceUID, VR.UI, iuids[1]);
            if (n > 2)
                keys.setString(Tag.SOPInstanceUID, VR.UI, iuids[2]);
        }
        return keys;
    }

    private Response.ResponseBuilder status(Attributes cmd) {
        int status = cmd.getInt(Tag.Status, -1);
        switch (status) {
            case 0:
                return Response.ok();
            case Status.OneOrMoreFailures:
                return Response.status(Response.Status.PARTIAL_CONTENT).header("Warning", warning(status));
            default:
                return Response.status(Response.Status.BAD_GATEWAY).header("Warning", warning(status));
        }
    }

    private String warning(int status) {
        switch (status) {
            case Status.OneOrMoreFailures:
                return "B000: Sub-operations Complete - One or more Failures";
            case Status.UnableToCalculateNumberOfMatches:
                return "A701: Refused: Out of Resources - Unable to calculate number of matches";
            case Status.UnableToPerformSubOperations:
                return "A702: Refused: Out of Resources - Unable to perform sub-operations";
            case Status.MoveDestinationUnknown:
                return "A801: Refused: Move Destination unknown";
            case Status.IdentifierDoesNotMatchSOPClass:
                return "A900: Identifier does not match SOP Class";
        }
        return TagUtils.shortToHexString(status)
                + ((status & Status.UnableToProcess) == Status.UnableToProcess
                    ? ": Unable to Process"
                    : ": Unexpected status code");
    }

    private Object entity(Attributes cmd) {
        return (StreamingOutput) out -> {
                JsonGenerator gen = Json.createGenerator(out);
                JsonWriter writer = new JsonWriter(gen);
                gen.writeStartObject();
                gen.write("status", TagUtils.shortToHexString(cmd.getInt(Tag.Status, -1)));
                writer.writeNotNullOrDef("error", cmd.getString(Tag.ErrorComment), null);
                writer.writeNotDef("completed", cmd.getInt(Tag.NumberOfCompletedSuboperations, -1), -1);
                writer.writeNotDef("warning", cmd.getInt(Tag.NumberOfWarningSuboperations, -1), -1);
                writer.writeNotDef("failed", cmd.getInt(Tag.NumberOfFailedSuboperations, -1), -1);
                gen.writeEnd();
                gen.flush();
        };
    }

}
