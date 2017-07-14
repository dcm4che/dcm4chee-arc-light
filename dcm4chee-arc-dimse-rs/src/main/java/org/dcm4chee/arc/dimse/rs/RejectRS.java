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
 *  Portions created by the Initial Developer are Copyright (C) 2015-2017
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

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Code;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.*;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4che3.util.TagUtils;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.RejectionNote;
import org.dcm4chee.arc.query.scu.CFindSCU;
import org.dcm4chee.arc.query.util.KOSBuilder;
import org.dcm4chee.arc.store.scu.CStoreSCU;
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
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2017
 */
@RequestScoped
@Path("aets/{AETitle}/dimse/{ExternalAET}")
public class RejectRS {

    private static final Logger LOG = LoggerFactory.getLogger(RejectRS.class);

    @Context
    private HttpServletRequest request;

    @Inject
    private Device device;

    @PathParam("AETitle")
    private String aet;

    @PathParam("ExternalAET")
    private String externalAET;

    @QueryParam("storescp")
    private String storescp;

    @QueryParam("priority")
    @Pattern(regexp = "0|1|2")
    private String priority;

    @Inject
    private CFindSCU findSCU;

    @Inject
    private CStoreSCU storeSCU;

    @Override
    public String toString() {
        return request.getRequestURI() + '?' + request.getQueryString();
    }

    @POST
    @Path("/studies/{StudyUID}/reject/{CodeValue}^{CodingSchemeDesignator}")
    @Produces("application/json")
    public Response exportStudy(
            @PathParam("StudyUID") String studyUID,
            @PathParam("CodeValue") String codeValue,
            @PathParam("CodingSchemeDesignator") String designator) throws Exception {
        return reject(studyUID, null, null,codeValue, designator);
    }

    @POST
    @Path("/studies/{StudyUID}/series/{SeriesUID}/reject/{CodeValue}^{CodingSchemeDesignator}")
    @Produces("application/json")
    public Response exportSeries(
            @PathParam("StudyUID") String studyUID,
            @PathParam("SeriesUID") String seriesUID,
            @PathParam("CodeValue") String codeValue,
            @PathParam("CodingSchemeDesignator") String designator) throws Exception {
        return reject(studyUID, seriesUID, null, codeValue, designator);
    }

    @POST
    @Path("/studies/{StudyUID}/series/{SeriesUID}/instances/{ObjectUID}/reject/{CodeValue}^{CodingSchemeDesignator}")
    @Produces("application/json")
    public Response exportSeries(
            @PathParam("StudyUID") String studyUID,
            @PathParam("SeriesUID") String seriesUID,
            @PathParam("ObjectUID") String objectUID,
            @PathParam("CodeValue") String codeValue,
            @PathParam("CodingSchemeDesignator") String designator) throws Exception {
        return reject(studyUID, seriesUID, objectUID, codeValue, designator);
    }

    private int priority() {
        return parseInt(priority, 0);
    }

    private static int parseInt(String s, int defval) {
        return s != null ? Integer.parseInt(s) : defval;
    }

    private Response reject(String studyUID, String seriesUID, String objectUID,String codeValue, String designator)
            throws Exception {
        LOG.info("Process POST {} from {}@{}", this, request.getRemoteUser(), request.getRemoteHost());
        ApplicationEntity localAE = getApplicationEntity();
        ArchiveDeviceExtension arcDev = localAE.getDevice().getDeviceExtension(ArchiveDeviceExtension.class);
        Code code = new Code(codeValue, designator, null, "?");
        RejectionNote rjNote = arcDev.getRejectionNote(code);
        if (rjNote == null)
            throw new WebApplicationException(Response.Status.NOT_FOUND);

        List<Attributes> matches = findSCU.find(localAE, externalAET, priority(), QueryRetrieveLevel2.IMAGE,
                studyUID, seriesUID, objectUID,
                Tag.SOPClassUID,
                Tag.SOPInstanceUID,
                Tag.StudyDate,
                Tag.StudyTime,
                Tag.AccessionNumber,
                Tag.IssuerOfAccessionNumberSequence,
                Tag.PatientID,
                Tag.IssuerOfPatientID,
                Tag.PatientName,
                Tag.PatientBirthDate,
                Tag.PatientSex,
                Tag.StudyID,
                Tag.StudyInstanceUID,
                Tag.SeriesInstanceUID);
        if (matches.isEmpty())
            throw new WebApplicationException(Response.Status.NOT_FOUND);

        KOSBuilder builder = new KOSBuilder(code, 999, 0);
        for (Attributes match : matches)
            builder.addInstanceRef(match);

        return status(storeSCU.store(localAE, storescp(), priority(), builder.getAttributes())).build();
    }

    private String storescp() {
        return storescp != null ? storescp : externalAET;
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
        return new StreamingOutput() {
            @Override
            public void write(OutputStream out) throws IOException {
                try (JsonGenerator gen = Json.createGenerator(out)) {
                    gen.writeStartObject();
                    gen.write("status", TagUtils.shortToHexString(cmd.getInt(Tag.Status, -1)));
                    writeStringTo(cmd, Tag.ErrorComment, gen, "error");
                    writeIntTo(cmd, Tag.NumberOfCompletedSuboperations, gen, "completed");
                    writeIntTo(cmd, Tag.NumberOfWarningSuboperations, gen, "warning");
                    writeIntTo(cmd, Tag.NumberOfFailedSuboperations, gen, "failed");
                    gen.writeEnd();
                }
            }
        };
    }

    private static void writeStringTo(Attributes cmd, int tag, JsonGenerator gen, String name) {
        String value = cmd.getString(tag);
        if (value != null) {
            gen.write(name, value);
        }
    }

    private static void writeIntTo(Attributes cmd, int tag, JsonGenerator gen, String name) {
        if (cmd.containsValue(tag)) {
            gen.write(name, cmd.getInt(tag, -1));
        }
    }

    private ApplicationEntity getApplicationEntity() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        if (ae == null || !ae.isInstalled())
            throw new WebApplicationException(
                    "No such Application Entity: " + aet,
                    Response.Status.SERVICE_UNAVAILABLE);
        return ae;
    }

}
