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

import org.dcm4che3.conf.api.IApplicationEntityCache;
import org.dcm4che3.conf.json.JsonWriter;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Code;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.*;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4che3.util.TagUtils;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.RejectionNote;
import org.dcm4chee.arc.event.RejectionNoteSent;
import org.dcm4chee.arc.query.scu.CFindSCU;
import org.dcm4chee.arc.query.util.KOSBuilder;
import org.dcm4chee.arc.store.scu.CStoreSCU;
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

    @Inject
    private IApplicationEntityCache aeCache;

    @Inject
    private Event<RejectionNoteSent> rejectionNoteSentEvent;

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

        List<Attributes> matches = null;
        try {
            matches = findSCU.find(localAE, externalAET, priority(), QueryRetrieveLevel2.IMAGE,
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
        } catch (DicomServiceException e) {
            return failed(e.getStatus(), e.getMessage(), null);
        } catch (Exception e) {
            return failed(Status.ProcessingFailure, e.getMessage(), null);
        }
        if (matches.isEmpty())
            throw new WebApplicationException(Response.Status.NOT_FOUND);

        KOSBuilder builder = new KOSBuilder(rjNote.getRejectionNoteCode(), 999, 1);

        for (Attributes match : matches)
            builder.addInstanceRef(match);

        Attributes kos = builder.getAttributes();
        try {
            String remoteAET = storescp != null ? storescp : externalAET;
            ApplicationEntity remoteAE = aeCache.get(remoteAET);
            Attributes cmd = storeSCU.store(localAE, remoteAE, priority(), kos);
            int status = cmd.getInt(Tag.Status, -1);
            String errorComment = cmd.getString(Tag.ErrorComment);
            boolean studyDeleted = seriesUID == null;
            rejectionNoteSentEvent.fire(
                    new RejectionNoteSent(request, localAE, remoteAE, kos, studyDeleted, status, errorComment));
            switch (status) {
                case Status.Success:
                case Status.CoercionOfDataElements:
                case Status.ElementsDiscarded:
                case Status.DataSetDoesNotMatchSOPClassWarning:
                    return success(status, errorComment, matches);
                default:
                    return failed(status, errorComment, matches);
            }
        } catch (Exception e) {
            return failed(Status.ProcessingFailure, e.getMessage(), matches);
        }
    }

    private Response success(int status, String errorComment, List<Attributes> matches) {
        return Response.ok(entity(status, errorComment, matches.size(), 0)).build();
    }

    private Response failed(int status, String errorComment, List<Attributes> matches) {
        return Response.status(Response.Status.BAD_GATEWAY)
                .header("Warning", warning(status))
                .entity(entity(status, errorComment, 0, matches != null ? matches.size() : 0))
                .build();
    }

    private String warning(int status) {
        return TagUtils.shortToHexString(status)
                + (status == Status.ProcessingFailure
                ? ": Error: Processing Failure"
                : (status & Status.OutOfResources) == Status.OutOfResources
                ? ": Refused: Out of Resources"
                : (status & Status.DataSetDoesNotMatchSOPClassError) == Status.DataSetDoesNotMatchSOPClassError
                ? ": Error: Data Set does not match SOP Class"
                : (status & Status.CannotUnderstand) == Status.CannotUnderstand
                ? ": Cannot Understand"
                : ": Unexpected status code");
    }

    private Object entity(int status, String error, int rejected, int failed) {
        return new StreamingOutput() {
            @Override
            public void write(OutputStream out) throws IOException {
                JsonGenerator gen = Json.createGenerator(out);
                JsonWriter writer = new JsonWriter(gen);
                gen.writeStartObject();
                gen.write("status", TagUtils.shortToHexString(status));
                writer.writeNotNullOrDef("error", error, null);
                writer.writeNotDef("rejected", rejected, 0);
                writer.writeNotDef("failed", failed, 0);
                gen.writeEnd();
                gen.flush();
            }
        };
    }

    private ApplicationEntity getApplicationEntity() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        if (ae == null || !ae.isInstalled())
            throw new WebApplicationException(
                    "No such Application Entity: " + aet,
                    Response.Status.NOT_FOUND);
        return ae;
    }

}
