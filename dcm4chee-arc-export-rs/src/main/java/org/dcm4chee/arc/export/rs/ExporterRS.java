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

package org.dcm4chee.arc.export.rs;

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
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.qmgt.QueueSizeLimitExceededException;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveService;
import org.dcm4chee.arc.stgcmt.StgCmtSCU;
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
import java.io.PrintWriter;
import java.io.StringWriter;
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

    @QueryParam("batchID")
    private String batchID;

    @Context
    private HttpServletRequest request;

    @POST
    @Path("/studies/{StudyUID}/export/{ExporterID}")
    @Produces("application/json")
    public Response exportStudy(
            @PathParam("StudyUID") String studyUID,
            @PathParam("ExporterID") String exporterID) {
        return export(studyUID, null, null, exporterID);
    }

    @POST
    @Path("/studies/{StudyUID}/series/{SeriesUID}/export/{ExporterID}")
    @Produces("application/json")
    public Response exportSeries(
            @PathParam("StudyUID") String studyUID,
            @PathParam("SeriesUID") String seriesUID,
            @PathParam("ExporterID") String exporterID) {
        return export(studyUID, seriesUID, null, exporterID);
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
        logRequest();
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        if (ae == null || !ae.isInstalled())
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);

        boolean bOnlyIAN = Boolean.parseBoolean(onlyIAN);
        boolean bOnlyStgCmt = Boolean.parseBoolean(onlyStgCmt);
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        if (arcDev == null)
            return errResponse("Archive Device Extension not configured for device: " + device.getDeviceName(),
                    Response.Status.NOT_FOUND);

        ExporterDescriptor exporter = arcDev.getExporterDescriptor(exporterID);
        if (exporter != null) {
            if (bOnlyIAN && exporter.getIanDestinations().length == 0)
                return errResponse("No IAN Destinations configured", Response.Status.NOT_FOUND);

            if (bOnlyStgCmt && exporter.getStgCmtSCPAETitle() == null)
                return errResponse("No Storage Commitment SCP configured", Response.Status.NOT_FOUND);

            try {
                if (bOnlyIAN || bOnlyStgCmt) {
                    ExportContext ctx = createExportContext(studyUID, seriesUID, objectUID, exporter);
                    if (bOnlyIAN)
                        ianScheduler.scheduleIAN(ctx, exporter);
                    if (bOnlyStgCmt)
                        stgCmtSCU.scheduleStorageCommit(ctx, exporter);
                } else
                    exportManager.scheduleExportTask(studyUID, seriesUID, objectUID, exporter,
                            HttpServletRequestInfo.valueOf(request), batchID);
            } catch (QueueSizeLimitExceededException e) {
                return errResponse(e.getMessage(), Response.Status.SERVICE_UNAVAILABLE);
            } catch (Exception e) {
                return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
            }

            return Response.accepted().build();
        }

        URI exportURI = toDicomURI(exporterID);
        if (exportURI == null)
            return errResponse("Export destination should start with dicom:", Response.Status.NOT_FOUND);
        if (bOnlyStgCmt)
            return errResponse(
                    "only-stgcmt=true not allowed with exporterID: " + exporterID, Response.Status.BAD_REQUEST);
        if (bOnlyIAN)
            return errResponse(
                    "only-ian=true not allowed with exporterID: " + exporterID, Response.Status.BAD_REQUEST);

        try {
            RetrieveContext retrieveContext = retrieveService.newRetrieveContextSTORE(
                    aet, studyUID, seriesUID, objectUID, exportURI.getSchemeSpecificPart());
            retrieveContext.setHttpServletRequestInfo(HttpServletRequestInfo.valueOf(request));
            exporterFactory.getExporter(new ExporterDescriptor(exporterID, exportURI))
                    .export(retrieveContext);
            return toResponse(retrieveContext);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private void logRequest() {
        LOG.info("Process {} {}?{} from {}@{}",
                request.getMethod(),
                request.getRequestURI(),
                request.getQueryString(),
                request.getRemoteUser(),
                request.getRemoteHost());
    }

    private static Response toResponse(RetrieveContext retrieveContext) {
        return Response.status(status(retrieveContext)).entity(entity(retrieveContext)).build();
    }

    private static Response.Status status(RetrieveContext ctx) {
        return ctx.getException() != null
                ? Response.Status.BAD_GATEWAY
                : ctx.failed() == 0
                    ? Response.Status.OK
                    : ctx.completed() + ctx.warning() > 0
                        ? Response.Status.PARTIAL_CONTENT
                        : Response.Status.BAD_GATEWAY;
    }

    private static Object entity(final RetrieveContext ctx) {
        return (StreamingOutput) out -> {
                JsonGenerator gen = Json.createGenerator(out);
                JsonWriter writer = new JsonWriter(gen);
                gen.writeStartObject();
                gen.write("completed", ctx.completed());
                writer.writeNotDef("warning", ctx.warning(), 0);
                writer.writeNotDef("failed", ctx.failed(), 0);
                writer.writeNotNullOrDef("error", ctx.getException(), null);
                gen.writeEnd();
                gen.flush();
        };
    }

    private static URI toDicomURI(String exporterID) {
        if (exporterID.startsWith("dicom:"))
            try {
                return new URI(exporterID);
            } catch (URISyntaxException e) {
                LOG.warn("Malformed URI : {}", exporterID);
            }
        return null;
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

    private ExportContext createExportContext(
            String studyUID, String seriesUID, String objectUID, ExporterDescriptor exporter) {
        Exporter e = exporterFactory.getExporter(exporter);
        ExportContext ctx = e.createExportContext();
        ctx.setStudyInstanceUID(studyUID);
        ctx.setSeriesInstanceUID(seriesUID);
        ctx.setSopInstanceUID(objectUID);
        ctx.setAETitle(aet);
        ctx.setBatchID(batchID);
        return ctx;
    }
}
