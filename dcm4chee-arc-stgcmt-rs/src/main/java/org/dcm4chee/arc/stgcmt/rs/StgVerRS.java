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
 * Portions created by the Initial Developer are Copyright (C) 2017-2019
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

package org.dcm4chee.arc.stgcmt.rs;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.StorageVerificationPolicy;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.stgcmt.StgCmtContext;
import org.dcm4chee.arc.stgcmt.StgCmtManager;
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
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Sep 2016
 */
@RequestScoped
@Path("aets/{aet}/rs")
public class StgVerRS {
    private static final Logger LOG = LoggerFactory.getLogger(StgVerRS.class);

    @Inject
    private Device device;

    @Inject
    private StgCmtManager stgCmtMgr;

    @Inject
    private Event<StgCmtContext> stgCmtEvent;

    @PathParam("aet")
    private String aet;

    @Context
    private HttpServletRequest request;

    @QueryParam("storageVerificationPolicy")
    @Pattern(regexp = "DB_RECORD_EXISTS|OBJECT_EXISTS|OBJECT_SIZE|OBJECT_FETCH|OBJECT_CHECKSUM|S3_MD5SUM")
    private String storageVerificationPolicy;

    @QueryParam("storageVerificationUpdateLocationStatus")
    @Pattern(regexp = "true|false")
    private String storageVerificationUpdateLocationStatus;

    @QueryParam("storageVerificationStorageID")
    private List<String> storageVerificationStorageIDs;

    @POST
    @Path("/studies/{StudyInstanceUID}/stgver")
    @Produces("application/dicom+json,application/json")
    public Response studyStorageCommit(
            @PathParam("StudyInstanceUID") String studyUID) {
        return storageCommit(studyUID, null, null);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}/series/{SeriesInstanceUID}/stgver")
    @Produces("application/dicom+json,application/json")
    public Response seriesStorageCommit(
            @PathParam("StudyInstanceUID") String studyUID,
            @PathParam("SeriesInstanceUID") String seriesUID) {
        return storageCommit(studyUID, seriesUID, null);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}/series/{SeriesInstanceUID}/instances/{SOPInstanceUID}/stgver")
    @Produces("application/dicom+json,application/json")
    public Response instanceStorageCommit(
            @PathParam("StudyInstanceUID") String studyUID,
            @PathParam("SeriesInstanceUID") String seriesUID,
            @PathParam("SOPInstanceUID") String sopUID) {
        return storageCommit(studyUID, seriesUID, sopUID);
    }

    private Response storageCommit(String studyUID, String seriesUID, String sopUID) {
        logRequest();
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        if (ae == null || !ae.isInstalled())
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);

        StgCmtContext ctx = new StgCmtContext(ae, aet)
                                 .setRequest(HttpServletRequestInfo.valueOf(request));
        if (storageVerificationPolicy != null)
            ctx.setStorageVerificationPolicy(StorageVerificationPolicy.valueOf(storageVerificationPolicy));
        if (storageVerificationUpdateLocationStatus != null)
            ctx.setUpdateLocationStatus(Boolean.valueOf(storageVerificationUpdateLocationStatus));
        if (!storageVerificationStorageIDs.isEmpty())
            ctx.setStorageIDs(storageVerificationStorageIDs.toArray(StringUtils.EMPTY_STRING));
        try {
            if (!stgCmtMgr.calculateResult(ctx, studyUID, seriesUID, sopUID))
                return errResponse("No matching instances", Response.Status.NOT_FOUND);

        } catch (IOException e) {
            ctx.setException(e);
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        } finally {
            stgCmtEvent.fire(ctx);
        }
        Attributes eventInfo = ctx.getEventInfo();
        return Response.status(toStatus(eventInfo))
                .entity(toStreamingOutput(eventInfo))
                .build();
    }

    private void logRequest() {
        LOG.info("Process {} {}?{} from {}@{}",
                request.getMethod(),
                request.getRequestURI(),
                request.getQueryString(),
                request.getRemoteUser(),
                request.getRemoteHost());
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

    private Response errResponse(String errorMessage, Response.Status status) {
        return errResponseAsTextPlain("{\"errorMessage\":\"" + errorMessage + "\"}", status);
    }

    private Response.Status toStatus(Attributes eventInfo) {
        int completed = sizeOf(eventInfo.getSequence(Tag.ReferencedSOPSequence));
        int failed = sizeOf(eventInfo.getSequence(Tag.FailedSOPSequence));
        return failed == 0 ? Response.Status.OK
                : completed == 0 ? Response.Status.CONFLICT
                : Response.Status.ACCEPTED;
    }

    private int sizeOf(Sequence seq) {
        return seq != null ? seq.size() : 0;
    }

    private StreamingOutput toStreamingOutput(Attributes eventInfo) {
        return out -> {
            try (JsonGenerator gen = Json.createGenerator(out)) {
                JSONWriter writer = new JSONWriter(gen);
                gen.writeStartArray();
                writer.write(eventInfo);
                gen.writeEnd();
            }
        };
    }
}
