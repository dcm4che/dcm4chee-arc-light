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
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
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

import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.export.mgt.ExportManager;
import org.dcm4chee.arc.exporter.ExportContext;
import org.dcm4chee.arc.exporter.Exporter;
import org.dcm4chee.arc.exporter.ExporterFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

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
    private ExportManager exportManager;

    @Inject
    private ExporterFactory exporterFactory;

    @Inject
    private Event<ExportContext> exportEvent;

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
    public void exportStudy(
            @PathParam("StudyUID") String studyUID,
            @PathParam("ExporterID") String exporterID) {
        export(studyUID, "*", "*", exporterID);
    }

    @POST
    @Path("/studies/{StudyUID}/series/{SeriesUID}/export/{ExporterID}")
    public void exportSeries(
            @PathParam("StudyUID") String studyUID,
            @PathParam("SeriesUID") String seriesUID,
            @PathParam("ExporterID") String exporterID) {
        export(studyUID, seriesUID, "*", exporterID);
    }

    @POST
    @Path("/studies/{StudyUID}/series/{SeriesUID}/instances/{ObjectUID}/export/{ExporterID}")
    public void exportInstance(
            @PathParam("StudyUID") String studyUID,
            @PathParam("SeriesUID") String seriesUID,
            @PathParam("ObjectUID") String objectUID,
            @PathParam("ExporterID") String exporterID) {
        export(studyUID, seriesUID, objectUID, exporterID);
    }

    private void export(String studyUID, String seriesUID, String objectUID, String exporterID) {
        LOG.info("Process POST {} from {}@{}", request.getRequestURI(), request.getRemoteUser(), request.getRemoteHost());
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        if (ae == null || !ae.isInstalled())
            throw new WebApplicationException(getResponse("No such Application Entity: " + aet,
                    Response.Status.SERVICE_UNAVAILABLE));

        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        ExporterDescriptor exporter = arcDev.getExporterDescriptor(exporterID);
        if (exporter == null)
            throw new WebApplicationException(getResponse("Exporter not found.", Response.Status.NOT_FOUND));

        boolean bOnlyIAN = Boolean.parseBoolean(onlyIAN);
        boolean bOnlyStgCmt = Boolean.parseBoolean(onlyStgCmt);
        if (bOnlyIAN && exporter.getIanDestinations().length == 0)
            throw new WebApplicationException(getResponse("No IAN Destinations configured", Response.Status.NOT_FOUND));
        if (bOnlyStgCmt && exporter.getStgCmtSCPAETitle() == null)
            throw new WebApplicationException(getResponse("No Storage Commitment SCP configured", Response.Status.NOT_FOUND));

        if (bOnlyIAN || bOnlyStgCmt)
            exportEvent.fire(createExportContext(studyUID, seriesUID, objectUID, exporter, aet, bOnlyIAN, bOnlyStgCmt));
        else
            exportManager.scheduleExportTask(studyUID, seriesUID, objectUID, exporter, aet);
    }

    private Response getResponse(String errorMessage, Response.Status status) {
        Object entity = "{\"errorMessage\":\"" + errorMessage + "\"}";
        return Response.status(status).entity(entity).build();
    }

    private ExportContext createExportContext(
            String studyUID, String seriesUID, String objectUID, ExporterDescriptor exporter, String aeTitle,
            boolean bOnlyIAN, boolean bOnlyStgCmt) {
        Exporter e = exporterFactory.getExporter(exporter);
        ExportContext ctx = e.createExportContext();
        ctx.setStudyInstanceUID(studyUID);
        ctx.setSeriesInstanceUID(seriesUID);
        ctx.setSopInstanceUID(objectUID);
        ctx.setAETitle(aeTitle);
        ctx.setOnlyStgCmt(bOnlyStgCmt);
        ctx.setOnlyIAN(bOnlyIAN);
        return ctx;
    }
}
