/*
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
 * Portions created by the Initial Developer are Copyright (C) 2015-2018
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
 */

package org.dcm4chee.arc.export.rs;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.export.mgt.ExportManager;
import org.dcm4chee.arc.exporter.ExportContext;
import org.dcm4chee.arc.exporter.Exporter;
import org.dcm4chee.arc.exporter.ExporterFactory;
import org.dcm4chee.arc.ian.scu.IANScheduler;
import org.dcm4chee.arc.qmgt.HttpServletRequestInfo;
import org.dcm4chee.arc.qmgt.QueueSizeLimitExceededException;
import org.dcm4chee.arc.query.Query;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.query.util.QueryAttributes;
import org.dcm4chee.arc.stgcmt.StgCmtSCU;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Dec 2017
 */
@RequestScoped
@Path("aets/{AETitle}/export/{ExporterID}")
public class ExportMatchingRS {

    private static final Logger LOG = LoggerFactory.getLogger(ExportMatchingRS.class);

    @Context
    private HttpServletRequest request;

    @Context
    private UriInfo uriInfo;

    @Inject
    private Device device;

    @Inject
    private QueryService queryService;

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

    @PathParam("ExporterID")
    private String exporterID;

    @QueryParam("only-stgcmt")
    @Pattern(regexp = "true|false")
    private String onlyStgCmt;

    @QueryParam("only-ian")
    @Pattern(regexp = "true|false")
    private String onlyIAN;

    @QueryParam("expired")
    @Pattern(regexp = "true|false")
    private String expired;

    @QueryParam("fuzzymatching")
    @Pattern(regexp = "true|false")
    private String fuzzymatching;

    @QueryParam("incomplete")
    @Pattern(regexp = "true|false")
    private String incomplete;

    @QueryParam("retrievefailed")
    @Pattern(regexp = "true|false")
    private String retrievefailed;

    @QueryParam("SendingApplicationEntityTitleOfSeries")
    private String sendingApplicationEntityTitleOfSeries;

    @QueryParam("StudyReceiveDateTime")
    private String studyReceiveDateTime;

    @QueryParam("ExternalRetrieveAET")
    private String externalRetrieveAET;

    @QueryParam("ExternalRetrieveAET!")
    private String externalRetrieveAETNot;

    @Override
    public String toString() {
        return request.getRequestURI() + '?' + request.getQueryString();
    }

    @POST
    @Path("/studies")
    @Produces("application/json")
    public Response exportMatchingStudies() {
        return exportMatching(
                "exportMatchingStudies",
                QueryRetrieveLevel2.STUDY,
                null,
                null);
    }

    @POST
    @Path("/series")
    @Produces("application/json")
    public Response exportMatchingSeries() {
        return exportMatching(
                "exportMatchingSeries",
                QueryRetrieveLevel2.SERIES,
                null,
                null);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}/series")
    @Produces("application/json")
    public Response exportMatchingSeriesOfStudy(
            @PathParam("StudyInstanceUID") String studyInstanceUID) {
        return exportMatching(
                "exportMatchingSeriesOfStudy",
                QueryRetrieveLevel2.SERIES,
                studyInstanceUID,
                null);
    }

    @POST
    @Path("/instances")
    @Produces("application/json")
    public Response exportMatchingInstances() {
        return exportMatching(
                "exportMatchingInstances",
                QueryRetrieveLevel2.IMAGE,
                null,
                null);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}/instances")
    @Produces("application/json")
    public Response exportMatchingInstancesOfStudy(
            @PathParam("StudyInstanceUID") String studyInstanceUID) {
        return exportMatching(
                "exportMatchingInstancesOfStudy",
                QueryRetrieveLevel2.IMAGE, studyInstanceUID,
                null);
    }

    @Path("/studies/{StudyInstanceUID}/series/{SeriesInstanceUID}/instances")
    @Produces("application/json")
    public Response exportMatchingInstancesOfSeries(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @PathParam("SeriesInstanceUID") String seriesInstanceUID) {
        return exportMatching(
                "exportMatchingInstancesOfSeries",
                QueryRetrieveLevel2.IMAGE,
                studyInstanceUID,
                seriesInstanceUID);
    }

    private Response exportMatching(
            String method, QueryRetrieveLevel2 qrlevel, String studyInstanceUID, String seriesInstanceUID) {
        LOG.info("Process POST {}?{} from {}@{}",
                request.getRequestURI(),
                request.getQueryString(),
                request.getRemoteUser(),
                request.getRemoteHost());
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        if (ae == null || !ae.isInstalled())
            return errResponse(Response.Status.NOT_FOUND, "No such Application Entity: " + aet);

        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        ExporterDescriptor exporter = arcDev.getExporterDescriptor(exporterID);
        if (exporter == null)
            return errResponse(Response.Status.NOT_FOUND, "No such Exporter: " + exporterID);

        boolean bOnlyIAN = Boolean.parseBoolean(onlyIAN);
        if (bOnlyIAN && exporter.getIanDestinations().length == 0)
            return errResponse(Response.Status.NOT_FOUND,
                    "No IAN Destinations configured in Exporter: " + exporterID);

        boolean bOnlyStgCmt = Boolean.parseBoolean(onlyStgCmt);
        if (bOnlyStgCmt && exporter.getStgCmtSCPAETitle() == null)
            return errResponse(Response.Status.NOT_FOUND,
                    "No Storage Commitment SCP configured in Exporter: " + exporterID);

        QueryContext ctx = queryContext(method, qrlevel, studyInstanceUID, seriesInstanceUID, ae);
        String warning = null;
        int count = 0;
        Response.Status status = Response.Status.ACCEPTED;
        try (Query query = queryService.createQuery(ctx)) {
            query.initQuery();
            Transaction transaction = query.beginTransaction();
            try {
                query.setFetchSize(arcDev.getQueryFetchSize());
                query.executeQuery();
                while (query.hasMoreMatches()) {
                    Attributes match = query.nextMatch();
                    if (bOnlyIAN || bOnlyStgCmt) {
                        ExportContext exportContext = createExportContext(match, qrlevel, exporter, aet);
                        if (bOnlyIAN)
                            ianScheduler.scheduleIAN(exportContext, exporter);
                        if (bOnlyStgCmt)
                            stgCmtSCU.scheduleStorageCommit(exportContext, exporter);
                    } else
                        scheduleExportTask(exporter, match, qrlevel);
                    count++;
                }
            } catch (QueueSizeLimitExceededException e) {
                status = Response.Status.SERVICE_UNAVAILABLE;
                warning = e.getMessage();
            } catch (Exception e) {
                warning = e.getMessage();
                status = Response.Status.INTERNAL_SERVER_ERROR;
            } finally {
                try {
                    transaction.commit();
                } catch (Exception e) {
                    LOG.warn("Failed to commit transaction:\n{}", e);
                }
            }
        }
        Response.ResponseBuilder builder = Response.status(status);
        if (warning != null)
            builder.header("Warning", warning);
        return builder.entity("{\"count\":" + count + '}').build();
    }

    private static Response errResponse(Response.Status status, String message) {
        return Response.status(status)
                .entity("{\"errorMessage\":\"" + message + "\"}")
                .build();
    }

    private QueryContext queryContext(
            String method, QueryRetrieveLevel2 qrlevel, String studyInstanceUID, String seriesInstanceUID,
            ApplicationEntity ae) {
        QueryContext ctx = queryService.newQueryContextQIDO(request, method, ae, queryParam(ae));
        ctx.setQueryRetrieveLevel(qrlevel);
        QueryAttributes queryAttrs = new QueryAttributes(uriInfo);
        Attributes keys = queryAttrs.getQueryKeys();
        IDWithIssuer idWithIssuer = IDWithIssuer.pidOf(keys);
        if (idWithIssuer != null)
            ctx.setPatientIDs(idWithIssuer);
        if (studyInstanceUID != null)
            keys.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUID);
        if (seriesInstanceUID != null)
            keys.setString(Tag.SeriesInstanceUID, VR.UI, seriesInstanceUID);
        ctx.setQueryKeys(keys);
        Attributes returnKeys = new Attributes(3);
        returnKeys.setNull(Tag.StudyInstanceUID, VR.UI);
        switch (qrlevel) {
            case IMAGE:
                returnKeys.setNull(Tag.SOPInstanceUID, VR.UI);
            case SERIES:
                returnKeys.setNull(Tag.SeriesInstanceUID, VR.UI);
        }
        ctx.setReturnKeys(returnKeys);
        return ctx;
    }

    private org.dcm4chee.arc.query.util.QueryParam queryParam(ApplicationEntity ae) {
        org.dcm4chee.arc.query.util.QueryParam queryParam = new org.dcm4chee.arc.query.util.QueryParam(ae);
        queryParam.setCombinedDatetimeMatching(true);
        queryParam.setFuzzySemanticMatching(Boolean.parseBoolean(fuzzymatching));
        queryParam.setExpired(Boolean.parseBoolean(expired));
        queryParam.setIncomplete(Boolean.parseBoolean(incomplete));
        queryParam.setRetrieveFailed(Boolean.parseBoolean(retrievefailed));
        queryParam.setSendingApplicationEntityTitleOfSeries(sendingApplicationEntityTitleOfSeries);
        queryParam.setStudyReceiveDateTime(studyReceiveDateTime);
        queryParam.setExternalRetrieveAET(externalRetrieveAET);
        queryParam.setExternalRetrieveAETNot(externalRetrieveAETNot);
        return queryParam;
    }

    private ExportContext createExportContext(
            Attributes match, QueryRetrieveLevel2 qrlevel, ExporterDescriptor exporter, String aeTitle) {
        Exporter e = exporterFactory.getExporter(exporter);
        ExportContext ctx = e.createExportContext();
        ctx.setStudyInstanceUID(match.getString(Tag.StudyInstanceUID));
        switch (qrlevel) {
            case IMAGE:
                ctx.setSopInstanceUID(match.getString(Tag.SOPInstanceUID));
            case SERIES:
                ctx.setSeriesInstanceUID(match.getString(Tag.SeriesInstanceUID));
        }
        ctx.setAETitle(aeTitle);
        return ctx;
    }

    private void scheduleExportTask(ExporterDescriptor exporter, Attributes match, QueryRetrieveLevel2 qrlevel)
            throws QueueSizeLimitExceededException {
        exportManager.scheduleExportTask(
                match.getString(Tag.StudyInstanceUID),
                qrlevel != QueryRetrieveLevel2.STUDY ? match.getString(Tag.SeriesInstanceUID) : null,
                qrlevel == QueryRetrieveLevel2.IMAGE ? match.getString(Tag.SOPInstanceUID) : null,
                exporter,
                HttpServletRequestInfo.valueOf(request));
    }

}
