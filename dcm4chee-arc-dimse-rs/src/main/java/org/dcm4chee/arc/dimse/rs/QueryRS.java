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
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.net.*;
import org.dcm4che3.util.TagUtils;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.Entity;
import org.dcm4chee.arc.query.scu.CFindSCU;
import org.dcm4chee.arc.query.util.QIDO;
import org.dcm4chee.arc.query.util.QueryAttributes;
import org.dcm4chee.arc.validation.constraints.ValidUriInfo;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.CompletionCallback;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.io.OutputStream;
import java.util.EnumSet;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2017
 */
@RequestScoped
@Path("aets/{AETitle}/dimse/{ExternalAET}")
@ValidUriInfo(type = QueryAttributes.class)
public class QueryRS {

    private static final Logger LOG = LoggerFactory.getLogger(QueryRS.class);

    @Context
    private HttpServletRequest request;

    @Context
    private UriInfo uriInfo;

    @Inject
    private Device device;

    @PathParam("AETitle")
    private String aet;

    @PathParam("ExternalAET")
    private String externalAET;

    @QueryParam("fuzzymatching")
    @Pattern(regexp = "true|false")
    private String fuzzymatching;

    @QueryParam("offset")
    @Pattern(regexp = "0|([1-9]\\d{0,4})")
    private String offset;

    @QueryParam("limit")
    @Pattern(regexp = "[1-9]\\d{0,4}")
    private String limit;

    @QueryParam("priority")
    @Pattern(regexp = "0|1|2")
    private String priority;

    @Inject
    private CFindSCU findSCU;

    private Association as;

    @Override
    public String toString() {
        return request.getRequestURI() + '?' + request.getQueryString();
    }

    @GET
    @NoCache
    @Path("/patients")
    @Produces("application/dicom+json,application/json")
    public void searchForPatientsJSON(@Suspended AsyncResponse ar) throws Exception {
        search(ar, Level.PATIENT, null, null, QIDO.PATIENT);
    }

    @GET
    @NoCache
    @Path("/studies")
    @Produces("application/dicom+json,application/json")
    public void searchForStudiesJSON(@Suspended AsyncResponse ar) throws Exception {
        search(ar, Level.STUDY, null, null, QIDO.STUDY);
    }

    @GET
    @NoCache
    @Path("/studies/{StudyInstanceUID}/series")
    @Produces("application/dicom+json,application/json")
    public void searchForSeriesOfStudyJSON(
            @Suspended AsyncResponse ar,
            @PathParam("StudyInstanceUID") String studyInstanceUID)
            throws Exception {
        search(ar, Level.SERIES, studyInstanceUID, null, QIDO.STUDY_SERIES);
    }

    @GET
    @NoCache
    @Path("/studies/{StudyInstanceUID}/series/{SeriesInstanceUID}/instances")
    @Produces("application/dicom+json,application/json")
    public void searchForInstancesOfSeriesJSON(
            @Suspended AsyncResponse ar,
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @PathParam("SeriesInstanceUID") String seriesInstanceUID)
            throws Exception {
        search(ar, Level.IMAGE, studyInstanceUID, seriesInstanceUID, QIDO.STUDY_SERIES_INSTANCE);
    }

    private ApplicationEntity getApplicationEntity() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        if (ae == null || !ae.isInstalled())
            throw new WebApplicationException(
                    "No such Application Entity: " + aet,
                    Response.Status.NOT_FOUND);
        return ae;
    }

    private int offset() {
        return parseInt(offset, 0);
    }

    private int limit() {
        return parseInt(limit, 0);
    }

    private int priority() {
        return parseInt(priority, 0);
    }

    private static int parseInt(String s, int defval) {
        return s != null ? Integer.parseInt(s) : defval;
    }

    private void search(AsyncResponse ar, Level level, String studyInstanceUID, String seriesInstanceUID, QIDO qido)
            throws Exception {
        LOG.info("Process GET {} from {}@{}", this, request.getRemoteUser(), request.getRemoteHost());
        QueryAttributes queryAttributes = new QueryAttributes(uriInfo);
        queryAttributes.addReturnTags(qido.includetags);
        if (queryAttributes.isIncludeAll()) {
            ArchiveDeviceExtension arcdev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
            switch (level) {
                case IMAGE:
                    queryAttributes.addReturnTags(arcdev.getAttributeFilter(Entity.Instance).getSelection());
                case SERIES:
                    queryAttributes.addReturnTags(arcdev.getAttributeFilter(Entity.Series).getSelection());
                case STUDY:
                    queryAttributes.addReturnTags(arcdev.getAttributeFilter(Entity.Study).getSelection());
                case PATIENT:
                    queryAttributes.addReturnTags(arcdev.getAttributeFilter(Entity.Patient).getSelection());
            }
        }
        Attributes keys = queryAttributes.getQueryKeys();
        keys.setString(Tag.QueryRetrieveLevel, VR.CS, level.name());
        if (studyInstanceUID != null)
            keys.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUID);
        if (seriesInstanceUID != null)
            keys.setString(Tag.SeriesInstanceUID, VR.UI, seriesInstanceUID);
        ApplicationEntity localAE = getApplicationEntity();
        EnumSet<QueryOption> queryOptions = EnumSet.of(QueryOption.DATETIME);
        if (Boolean.parseBoolean(fuzzymatching))
            queryOptions.add(QueryOption.FUZZY);
        ar.register(new CompletionCallback() {
            @Override
            public void onComplete(Throwable throwable) {
                if (as != null)
                    try {
                        as.release();
                    } catch (IOException e) {
                        LOG.info("{}: Failed to release association:\\n", as, e);
                    }
            }
        });
        as = findSCU.openAssociation(localAE, externalAET, level.cuid, queryOptions);
        DimseRSP dimseRSP = findSCU.query(as, priority(), keys, limit != null ? offset() + limit() : 0);
        dimseRSP.next();
        ar.resume(responseBuilder(dimseRSP).build());
    }

    private Response.ResponseBuilder responseBuilder(DimseRSP dimseRSP) {
        int status = dimseRSP.getCommand().getInt(Tag.Status, -1);
        switch (status) {
            case 0:
                return Response.ok("[]");
            case Status.Pending:
            case Status.PendingWarning:
                return Response.ok(writeJSON(dimseRSP));
        }
        return Response.status(Response.Status.BAD_GATEWAY).header("Warning", warning(status));
    }

    private String warning(int status) {
        switch (status) {
            case Status.OutOfResources:
                return "A700: Refused: Out of Resources";
            case Status.IdentifierDoesNotMatchSOPClass:
                return "A900: Identifier does not match SOP Class";
        }
        return TagUtils.shortToHexString(status)
                + ((status & Status.UnableToProcess) == Status.UnableToProcess
                ? ": Unable to Process"
                : ": Unexpected status code");
    }

    private enum Level {
        PATIENT(UID.PatientRootQueryRetrieveInformationModelFIND),
        STUDY(UID.StudyRootQueryRetrieveInformationModelFIND),
        SERIES(UID.StudyRootQueryRetrieveInformationModelFIND),
        IMAGE(UID.StudyRootQueryRetrieveInformationModelFIND);
        final String cuid;

        Level(String cuid) {
            this.cuid = cuid;
        }
    }

    private Object writeJSON(final DimseRSP dimseRSP) {
        return new StreamingOutput() {
            @Override
            public void write(OutputStream out) throws IOException {
                JsonGenerator gen = Json.createGenerator(out);
                JSONWriter writer = new JSONWriter(gen);
                gen.writeStartArray();
                int skip = offset();
                int remaining = limit();
                try {
                    Attributes dataset = dimseRSP.getDataset();
                    dimseRSP.next();
                    do {
                        if (skip > 0)
                            skip--;
                        else  {
                            writer.write(dataset);
                            if (limit != null && --remaining == 0)
                                break;
                        }
                        dataset = dimseRSP.getDataset();
                    } while (dimseRSP.next());
                } catch (InterruptedException e) {
                    LOG.warn("Failed to read next C-FIND RSP from {}:\\n", externalAET, e);
                }
                gen.writeEnd();
                gen.flush();
            }
        };
    }

}
