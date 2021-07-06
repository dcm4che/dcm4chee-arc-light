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
import org.dcm4che3.util.UIDUtils;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.export.mgt.ExportManager;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
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
    private String scheduledTime;

    @HeaderParam("Content-Type")
    private MediaType contentType;


    @POST
    @Path("/studies/csv:{field}/export/{ExporterID}")
    @Consumes("text/csv")
    @Produces("application/json")
    public Response exportStudies(
            @PathParam("ExporterID") String exporterID,
            @PathParam("field") int field,
            InputStream in) {
        return exportStudiesFromCSV(aet, exporterID, field, in);

    }

    @Override
    public String toString() {
        String requestURI = request.getRequestURI();
        String queryString = request.getQueryString();
        return queryString == null ? requestURI : requestURI + '?' + queryString;
    }

    Response exportStudiesFromCSV(String aet, String exporterID, int field, InputStream in) {
        logRequest();
        Response.Status status = Response.Status.BAD_REQUEST;
        try {
            if (field < 1)
                return errResponse(status,
                        "CSV field for Study Instance UID should be greater than or equal to 1");

            ApplicationEntity ae = device.getApplicationEntity(aet, true);
            if (ae == null || !ae.isInstalled())
                return errResponse(Response.Status.NOT_FOUND, "No such Application Entity: " + aet);

            ArchiveDeviceExtension arcDev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
            ExporterDescriptor exporter = arcDev.getExporterDescriptor(exporterID);
            if (exporter == null)
                return errResponse(Response.Status.NOT_FOUND, "No such Exporter: " + exporterID);

            int count = 0;
            String warning = null;
            int csvUploadChunkSize = arcDev.getCSVUploadChunkSize();
            List<String> studyUIDs = new ArrayList<>();

            try (
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                    CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT.withDelimiter(csvDelimiter()))
            ) {
                boolean header = true;
                for (CSVRecord csvRecord : parser) {
                    if (csvRecord.size() == 0 || csvRecord.get(0).isEmpty())
                        continue;

                    String studyUID = csvRecord.get(field - 1).replaceAll("\"", "");
                    if (header && studyUID.chars().allMatch(Character::isLetter)) {
                        header = false;
                        continue;
                    }

                    if (!arcDev.isValidateUID() || validateUID(studyUID))
                        studyUIDs.add(studyUID);

                    if (studyUIDs.size() == csvUploadChunkSize) {
                        count += scheduleExportTasks(exporter, studyUIDs, scheduledTime());
                        studyUIDs.clear();
                    }
                }
                if (!studyUIDs.isEmpty()) {
                    count += scheduleExportTasks(exporter, studyUIDs, scheduledTime());
                }

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

            LOG.warn("Response {} caused by {}", status, warning);
            Response.ResponseBuilder builder = Response.status(status)
                    .header("Warning", warning);
            if (count > 0)
                builder.entity(count(count));
            return builder.build();
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private int scheduleExportTasks(ExporterDescriptor exporter, List<String> studyUIDs, Date scheduledTime) {
        for (String studyUID : studyUIDs) {
            exportManager.createExportTask(
                    device.getDeviceName(),
                    exporter,
                    studyUID,
                    "*",
                    "*",
                    batchID,
                    scheduledTime,
                    HttpServletRequestInfo.valueOf(request));
        }
        return studyUIDs.size();
    }

    private static boolean validateUID(String studyUID) {
        boolean valid = UIDUtils.isValid(studyUID);
        if (!valid)
            LOG.warn("Invalid UID in CSV file: " + studyUID);
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
        LOG.warn("Response {} caused by {}", status, errorMsg);
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
}