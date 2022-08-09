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

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.WebApplication;
import org.dcm4che3.util.UIDUtils;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.export.mgt.ExportManager;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.keycloak.KeycloakContext;
import org.dcm4chee.arc.validation.ParseDateTime;
import org.dcm4chee.arc.validation.constraints.ValidValueOf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Nov 2018
 */
@RequestScoped
@Path("aets/{AETitle}/rs")
public class ExportCSVRS {

    private static final Logger LOG = LoggerFactory.getLogger(ExportCSVRS.class);
    private static final String SUPER_USER_ROLE = "super-user-role";

    @PathParam("AETitle")
    private String aet;

    @Context
    private HttpServletRequest request;

    @Context
    private UriInfo uriInfo;

    @Inject
    private Device device;

    @Inject
    private ExportManager exportManager;

    @QueryParam("batchID")
    private String batchID;

    @QueryParam("scheduledTime")
    @ValidValueOf(type = ParseDateTime.class)
    private String scheduledTime;

    @HeaderParam("Content-Type")
    private MediaType contentType;

    @POST
    @Path("/studies/csv:{studyUIDField}/export/{ExporterID}")
    @Consumes("text/csv")
    @Produces("application/json")
    public Response exportStudies(
            @PathParam("ExporterID") String exporterID,
            @PathParam("studyUIDField") int studyUIDField,
            InputStream in) {
        return exportFromCSV(aet, exporterID, studyUIDField, null, in);

    }

    @POST
    @Path("/studies/csv:{studyUIDField}/series/csv:{seriesUIDField}/export/{ExporterID}")
    @Consumes("text/csv")
    @Produces("application/json")
    public Response exportSeries(
            @PathParam("ExporterID") String exporterID,
            @PathParam("studyUIDField") int studyUIDField,
            @PathParam("seriesUIDField") int seriesUIDField,
            InputStream in) {
        return exportFromCSV(aet, exporterID, studyUIDField, seriesUIDField, in);

    }

    @Override
    public String toString() {
        String requestURI = request.getRequestURI();
        String queryString = request.getQueryString();
        return queryString == null ? requestURI : requestURI + '?' + queryString;
    }

    Response exportFromCSV(String aet, String exporterID, int studyUIDField, Integer seriesUIDField, InputStream in) {
        logRequest();
        ArchiveAEExtension arcAE = getArchiveAE();
        if (arcAE == null)
            return errResponse(Response.Status.NOT_FOUND, "No such Application Entity: " + aet);

        validateAcceptedUserRoles(arcAE);
        if (aet.equals(arcAE.getApplicationEntity().getAETitle()))
            validateWebAppServiceClass();

        Response.Status status = Response.Status.BAD_REQUEST;
        try {
            if (studyUIDField < 1)
                return errResponse(status,
                        "CSV field for Study Instance UID should be greater than or equal to 1");

            if (seriesUIDField != null) {
                if (studyUIDField == seriesUIDField)
                    return errResponse(status, "CSV fields for Study and Series Instance UIDs should be different");

                if (seriesUIDField < 1)
                    return errResponse(status,
                            "CSV field for Series Instance UID should be greater than or equal to 1");
            }

            ArchiveDeviceExtension arcDev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
            ExporterDescriptor exporter = arcDev.getExporterDescriptor(exporterID);
            if (exporter == null)
                return errResponse(Response.Status.NOT_FOUND, "No such Exporter: " + exporterID);

            int count = 0;
            String warning = null;
            int csvUploadChunkSize = arcDev.getCSVUploadChunkSize();
            List<StudySeriesInfo> studySeries = new ArrayList<>();
            try (
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                    CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT.builder()
                                                                                .setDelimiter(csvDelimiter())
                                                                                .build())
            ) {
                boolean header = true;
                for (CSVRecord csvRecord : parser) {
                    if (csvRecord.size() == 0 || csvRecord.get(0).isEmpty())
                        continue;

                    String studyUID = csvRecord.get(studyUIDField - 1).replaceAll("\"", "");
                    if (header && studyUID.chars().allMatch(Character::isLetter)) {
                        header = false;
                        continue;
                    }

                    if (!arcDev.isValidateUID() || validateUID(studyUID)) {
                        StudySeriesInfo studySeriesInfo = new StudySeriesInfo(studyUID);
                        addSeriesUID(studySeriesInfo, csvRecord, seriesUIDField, arcDev);
                        studySeries.add(studySeriesInfo);
                    }

                    if (studySeries.size() == csvUploadChunkSize) {
                        count += scheduleExportTasks(exporter, studySeries, scheduledTime());
                        studySeries.clear();
                    }
                }

                if (!studySeries.isEmpty())
                    count += scheduleExportTasks(exporter, studySeries, scheduledTime());
                
                if (count == 0) {
                    warning = "Empty file or Incorrect field position or Not a CSV file or Invalid UIDs.";
                    status = Response.Status.NO_CONTENT;
                }

            } catch (Exception e) {
                warning = e.getMessage();
                status = Response.Status.INTERNAL_SERVER_ERROR;
            }

            if (warning == null && count > 0)
                return Response.accepted(count(count)).build();

            LOG.info("Response {} caused by {}", status, warning);
            Response.ResponseBuilder builder = Response.status(status)
                    .header("Warning", warning);
            if (count > 0)
                builder.entity(count(count));
            return builder.build();
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private void addSeriesUID(StudySeriesInfo studySeriesInfo, CSVRecord csvRecord, Integer seriesUIDField,
                              ArchiveDeviceExtension arcDev) {
        if (seriesUIDField == null)
            return;

        String seriesUID = csvRecord.get(seriesUIDField - 1).replaceAll("\"", "");
        if (arcDev.isValidateUID() && !validateUID(seriesUID)) {
            LOG.info("Invalid Series[uid={}] of valid Study[uid={}] present in CSV file",
                    seriesUID, studySeriesInfo.getStudyUID());
            return;
        }

        studySeriesInfo.setSeriesUID(seriesUID);
    }

    static class StudySeriesInfo {
        private final String studyUID;
        private String seriesUID = "*";

        StudySeriesInfo(String studyUID) {
            this.studyUID = studyUID;
        }

        String getStudyUID() {
            return studyUID;
        }

        String getSeriesUID() {
            return seriesUID;
        }

        void setSeriesUID(String seriesUID) {
            this.seriesUID = seriesUID;
        }
    }

    private int scheduleExportTasks(
            ExporterDescriptor exporter, List<StudySeriesInfo> studySeriesInfos, Date scheduledTime) {
        for (StudySeriesInfo studySeriesInfo : studySeriesInfos) {
            exportManager.createExportTask(
                    device.getDeviceName(),
                    exporter,
                    studySeriesInfo.getStudyUID(),
                    studySeriesInfo.getSeriesUID(),
                    "*",
                    batchID,
                    scheduledTime,
                    HttpServletRequestInfo.valueOf(request));
        }
        return studySeriesInfos.size();
    }

    private boolean validateUID(String uid) {
        boolean valid = UIDUtils.isValid(uid);
        if (!valid)
            LOG.info("Invalid UID in CSV file: " + uid);
        return valid;
    }

    private char csvDelimiter() {
        return ("semicolon".equals(contentType.getParameters().get("delimiter"))) ? ';' : ',';
    }

    private Date scheduledTime() {
        if (scheduledTime != null)
            try {
                return new SimpleDateFormat("yyyyMMddhhmmss").parse(scheduledTime);
            } catch (Exception e) {
                LOG.info(e.getMessage());
            }

        return new Date();
    }

    private void logRequest() {
        LOG.info("Process {} {} from {}@{}",
                request.getMethod(),
                toString(),
                request.getRemoteUser(),
                request.getRemoteHost());
    }

    private static String count(int count) {
        return "{\"count\":" + count + '}';
    }

    private static Response errResponse(Response.Status status, String msg) {
        return errResponseAsTextPlain("{\"errorMessage\":\"" + msg + "\"}", status);
    }

    private static Response errResponseAsTextPlain(String errorMsg, Response.Status status) {
        LOG.info("Response {} caused by {}", status, errorMsg);
        return Response.status(status)
                .entity(errorMsg)
                .type("text/plain")
                .build();
    }

    private static String exceptionAsString(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private ArchiveAEExtension getArchiveAE() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        return ae == null || !ae.isInstalled() ? null : ae.getAEExtension(ArchiveAEExtension.class);
    }

    private void validateAcceptedUserRoles(ArchiveAEExtension arcAE) {
        KeycloakContext keycloakContext = KeycloakContext.valueOf(request);
        if (keycloakContext.isSecured() && !keycloakContext.isUserInRole(System.getProperty(SUPER_USER_ROLE))) {
            if (!arcAE.isAcceptedUserRole(keycloakContext.getRoles()))
                throw new WebApplicationException(
                        "Application Entity " + arcAE.getApplicationEntity().getAETitle() + " does not list role of accessing user",
                        Response.Status.FORBIDDEN);
        }
    }

    private void validateWebAppServiceClass() {
        device.getWebApplications().stream()
                .filter(webApp -> request.getRequestURI().startsWith(webApp.getServicePath())
                        && Arrays.asList(webApp.getServiceClasses())
                        .contains(WebApplication.ServiceClass.DCM4CHEE_ARC_AET))
                .findFirst()
                .orElseThrow(() -> new WebApplicationException(
                        errResponse(Response.Status.NOT_FOUND,
                        "No Web Application with DCM4CHEE_ARC_AET service class found for Application Entity: " + aet)));
    }
}