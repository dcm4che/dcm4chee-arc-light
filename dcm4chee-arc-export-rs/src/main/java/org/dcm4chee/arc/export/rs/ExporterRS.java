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
 * Portions created by the Initial Developer are Copyright (C) 2013
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

package org.dcm4chee.arc.export.rs;

import org.dcm4che3.conf.api.ConfigurationNotFoundException;
import org.dcm4che3.conf.json.JsonWriter;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.export.mgt.ExportManager;
import org.dcm4chee.arc.exporter.ExportContext;
import org.dcm4chee.arc.exporter.Exporter;
import org.dcm4chee.arc.exporter.ExporterFactory;
import org.dcm4chee.arc.ian.scu.IANScheduler;
import org.dcm4chee.arc.qmgt.QueueSizeLimitExceededException;
import org.dcm4chee.arc.retrieve.HttpServletRequestInfo;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveService;
import org.dcm4chee.arc.stgcmt.StgCmtSCU;
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
import java.net.URI;
import java.net.URISyntaxException;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Feb 2016
 */
@RequestScoped
@Path("aets/{AETitle}/rs")
public class ExporterRS {

    private static final Logger LOG = LoggerFactory.getLogger(ExporterRS.class);

    @Inject
    private Device device;

    @Inject
    private RetrieveService retrieveService;

    @Inject
    private ExportManager exportManager;

    @Inject
    private ExporterFactory exporterFactory;

    @Inject
    private IANScheduler ianScheduler;

    @Inject
    private StgCmtSCU stgCmtSCU;

    @PathParam("AETitle")
    private String aet;

    @QueryParam("only-stgcmt")
    @Pattern(regexp = "true|false")
    private String onlyStgCmt;

    @QueryParam("only-ian")
    @Pattern(regexp = "true|false")
    private String onlyIAN;

    @Context
    private HttpServletRequest request;

    @POST
    @Path("/studies/{StudyUID}/export/{ExporterID}")
    @Produces("application/json")
    public Response exportStudy(
            @PathParam("StudyUID") String studyUID,
            @PathParam("ExporterID") String exporterID) {
        return export(studyUID, "*", "*", exporterID);
    }

    @POST
    @Path("/studies/{StudyUID}/series/{SeriesUID}/export/{ExporterID}")
    @Produces("application/json")
    public Response exportSeries(
            @PathParam("StudyUID") String studyUID,
            @PathParam("SeriesUID") String seriesUID,
            @PathParam("ExporterID") String exporterID) {
        return export(studyUID, seriesUID, "*", exporterID);
    }

    @POST
    @Path("/studies/{StudyUID}/series/{SeriesUID}/instances/{ObjectUID}/export/{ExporterID}")
    @Produces("application/json")
    public Response exportInstance(
            @PathParam("StudyUID") String studyUID,
            @PathParam("SeriesUID") String seriesUID,
            @PathParam("ObjectUID") String objectUID,
            @PathParam("ExporterID") String exporterID) {
        return export(studyUID, seriesUID, objectUID, exporterID);
    }

    private Response export(String studyUID, String seriesUID, String objectUID, String exporterID) {
        LOG.info("Process POST {} from {}@{}", request.getRequestURI(), request.getRemoteUser(), request.getRemoteHost());
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        if (ae == null || !ae.isInstalled())
            return errResponse(Response.Status.SERVICE_UNAVAILABLE, "No such Application Entity: " + aet);

        boolean bOnlyIAN = Boolean.parseBoolean(onlyIAN);
        boolean bOnlyStgCmt = Boolean.parseBoolean(onlyStgCmt);
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        ExporterDescriptor exporter = arcDev.getExporterDescriptor(exporterID);
        if (exporter != null) {
            if (bOnlyIAN && exporter.getIanDestinations().length == 0)
                return errResponse(Response.Status.NOT_FOUND, "No IAN Destinations configured");

            if (bOnlyStgCmt && exporter.getStgCmtSCPAETitle() == null)
                return errResponse(Response.Status.NOT_FOUND, "No Storage Commitment SCP configured");

            try {
                if (bOnlyIAN || bOnlyStgCmt) {
                    ExportContext ctx = createExportContext(studyUID, seriesUID, objectUID, exporter, aet);
                    if (bOnlyIAN)
                        ianScheduler.scheduleIAN(ctx, exporter);
                    if (bOnlyStgCmt)
                        stgCmtSCU.scheduleStorageCommit(ctx, exporter);
                } else
                    exportManager.scheduleExportTask(studyUID, seriesUID, objectUID, exporter,
                            HttpServletRequestInfo.valueOf(request));
            } catch (QueueSizeLimitExceededException e) {
                return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
            }

            return Response.accepted().build();
        }

        URI exportURI = toDicomURI(exporterID);
        if (exportURI == null)
            return errResponse(Response.Status.NOT_FOUND, "Exporter not found.");
        if (bOnlyStgCmt)
            return errResponse(Response.Status.BAD_REQUEST,
                    "only-stgcmt=true not allowed with exporterID: " + exporterID);
        if (bOnlyIAN)
            return errResponse(Response.Status.BAD_REQUEST,
                    "only-ian=true not allowed with exporterID: " + exporterID);

        try {
            RetrieveContext retrieveContext = retrieveService.newRetrieveContextSTORE(
                    aet, studyUID, seriesUID, objectUID, exportURI.getSchemeSpecificPart());
            retrieveContext.setHttpServletRequestInfo(HttpServletRequestInfo.valueOf(request));
            exporterFactory.getExporter(new ExporterDescriptor(exporterID, exportURI))
                    .export(retrieveContext);
            return toResponse(retrieveContext);
        } catch (ConfigurationNotFoundException e) {
            return errResponse(Response.Status.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            return errResponse(Response.Status.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    private static Response toResponse(RetrieveContext retrieveContext) {
        return Response.status(status(retrieveContext)).entity(entity(retrieveContext)).build();
    }

    private static Response.Status status(RetrieveContext ctx) {
        return ctx.getException() != null ? Response.Status.BAD_GATEWAY
            : ctx.failed() == 0 ? Response.Status.OK
                : ctx.completed() + ctx.warning() > 0 ? Response.Status.PARTIAL_CONTENT
                : Response.Status.BAD_GATEWAY;
    }

    private static Object entity(final RetrieveContext ctx) {
        return new StreamingOutput() {
            @Override
            public void write(OutputStream out) throws IOException {
                JsonGenerator gen = Json.createGenerator(out);
                JsonWriter writer = new JsonWriter(gen);
                gen.writeStartObject();
                gen.write("completed", ctx.completed());
                writer.writeNotDef("warning", ctx.warning(), 0);
                writer.writeNotDef("failed", ctx.failed(), 0);
                writer.writeNotNullOrDef("failed", ctx.getException(), null);
                writer.writeNotNullOrDef("error", ctx.getException(), null);
                gen.writeEnd();
                gen.flush();
            }
        };
    }

    private static URI toDicomURI(String exporterID) {
        if (exporterID.startsWith("dicom:"))
            try {
                return new URI(exporterID);
            } catch (URISyntaxException e) {}
        return null;
    }

    private Response errResponse(Response.Status status, String message) {
        return Response.status(status)
                .entity("{\"errorMessage\":\"" + message + "\"}")
                .build();
    }

    private ExportContext createExportContext(
            String studyUID, String seriesUID, String objectUID, ExporterDescriptor exporter, String aeTitle) {
        Exporter e = exporterFactory.getExporter(exporter);
        ExportContext ctx = e.createExportContext();
        ctx.setStudyInstanceUID(studyUID);
        ctx.setSeriesInstanceUID(seriesUID);
        ctx.setSopInstanceUID(objectUID);
        ctx.setAETitle(aeTitle);
        return ctx;
    }
}
