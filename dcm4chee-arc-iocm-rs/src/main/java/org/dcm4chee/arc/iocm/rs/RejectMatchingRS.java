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

package org.dcm4chee.arc.iocm.rs;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.data.*;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4che3.util.UIDUtils;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.RejectionNote;
import org.dcm4chee.arc.delete.RejectionService;
import org.dcm4chee.arc.entity.ExpirationState;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.query.Query;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.query.RunInTransaction;
import org.dcm4chee.arc.query.util.QueryAttributes;
import org.dcm4chee.arc.validation.constraints.InvokeValidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @author Gunter Zeilinger <gunterze@protonmail.com>
 * @since Jan 2019
 */
@RequestScoped
@Path("aets/{AETitle}/rs")
@InvokeValidate(type = RejectMatchingRS.class)
public class RejectMatchingRS {

    private static final Logger LOG = LoggerFactory.getLogger(RejectMatchingRS.class);

    @PathParam("AETitle")
    private String aet;

    @Context
    private HttpServletRequest request;

    @Context
    private UriInfo uriInfo;

    @Inject
    private Device device;

    @Inject
    private RejectionService rejectionService;

    @Inject
    private QueryService queryService;

    @Inject
    private RunInTransaction runInTx;

    @QueryParam("batchID")
    private String batchID;

    @QueryParam("fuzzymatching")
    @Pattern(regexp = "true|false")
    private String fuzzymatching;

    @QueryParam("incomplete")
    @Pattern(regexp = "true|false")
    private String incomplete;

    @QueryParam("retrievefailed")
    @Pattern(regexp = "true|false")
    private String retrievefailed;

    @QueryParam("ExpirationDate")
    private String expirationDate;

    @QueryParam("storageVerificationFailed")
    @Pattern(regexp = "true|false")
    private String storageVerificationFailed;

    @QueryParam("metadataUpdateFailed")
    @Pattern(regexp = "true|false")
    private String metadataUpdateFailed;

    @QueryParam("compressionfailed")
    @Pattern(regexp = "true|false")
    private String compressionfailed;

    @QueryParam("ExternalRetrieveAET")
    private String externalRetrieveAET;

    @QueryParam("ExternalRetrieveAET!")
    private String externalRetrieveAETNot;

    @QueryParam("patientVerificationStatus")
    @Pattern(regexp = "UNVERIFIED|VERIFIED|NOT_FOUND|VERIFICATION_FAILED")
    private String patientVerificationStatus;

    @QueryParam("storageID")
    private String storageID;

    @QueryParam("storageClustered")
    @Pattern(regexp = "true|false")
    private String storageClustered;

    @QueryParam("storageExported")
    @Pattern(regexp = "true|false")
    private String storageExported;

    @QueryParam("allOfModalitiesInStudy")
    @Pattern(regexp = "true|false")
    private String allOfModalitiesInStudy;

    @QueryParam("StudySizeInKB")
    @Pattern(regexp = "\\d{1,6}(-\\d{0,6})?|-\\d{1,6}")
    private String studySizeInKB;

    @QueryParam("ExpirationState")
    @Pattern(regexp = "UPDATEABLE|FROZEN|REJECTED|EXPORT_SCHEDULED|FAILED_TO_EXPORT|FAILED_TO_REJECT")
    private String expirationState;

    @HeaderParam("Content-Type")
    private MediaType contentType;

    private static Boolean parseBoolean(String s) {
        return s != null ? Boolean.valueOf(s) : null;
    }

    private static String count(int count) {
        return "{\"count\":" + count + '}';
    }

    @POST
    @Path("/studies/reject/{codeValue}^{codingSchemeDesignator}")
    @Produces("application/json")
    public Response rejectMatchingStudies(
            @PathParam("codeValue") String codeValue,
            @PathParam("codingSchemeDesignator") String designator) {
        return rejectMatching(aet, codeValue, designator,
                "rejectMatchingStudies",
                QueryRetrieveLevel2.STUDY,
                null,
                null);
    }

    @POST
    @Path("/series/reject/{codeValue}^{codingSchemeDesignator}")
    @Produces("application/json")
    public Response rejectMatchingSeries(
            @PathParam("codeValue") String codeValue,
            @PathParam("codingSchemeDesignator") String designator) {
        return rejectMatching(aet, codeValue, designator,
                "rejectMatchingSeries",
                QueryRetrieveLevel2.SERIES,
                null,
                null);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}/series/reject/{codeValue}^{codingSchemeDesignator}")
    @Produces("application/json")
    public Response rejectMatchingSeriesOfStudy(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @PathParam("codeValue") String codeValue,
            @PathParam("codingSchemeDesignator") String designator) {
        return rejectMatching(aet, codeValue, designator,
                "rejectMatchingSeriesOfStudy",
                QueryRetrieveLevel2.SERIES,
                studyInstanceUID,
                null);
    }

    @POST
    @Path("/instances/reject/{codeValue}^{codingSchemeDesignator}")
    @Produces("application/json")
    public Response rejectMatchingInstances(
            @PathParam("codeValue") String codeValue,
            @PathParam("codingSchemeDesignator") String designator) {
        return rejectMatching(aet, codeValue, designator,
                "rejectMatchingInstances",
                QueryRetrieveLevel2.IMAGE,
                null,
                null);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}/instances/reject/{codeValue}^{codingSchemeDesignator}")
    @Produces("application/json")
    public Response rejectMatchingInstancesOfStudy(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @PathParam("codeValue") String codeValue,
            @PathParam("codingSchemeDesignator") String designator) {
        return rejectMatching(aet, codeValue, designator,
                "rejectMatchingInstancesOfStudy",
                QueryRetrieveLevel2.IMAGE, studyInstanceUID,
                null);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}/series/{SeriesInstanceUID}/instances/reject/{codeValue}^{codingSchemeDesignator}")
    @Produces("application/json")
    public Response rejectMatchingInstancesOfSeries(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @PathParam("SeriesInstanceUID") String seriesInstanceUID,
            @PathParam("codeValue") String codeValue,
            @PathParam("codingSchemeDesignator") String designator) {
        return rejectMatching(aet, codeValue, designator,
                "rejectMatchingInstancesOfSeries",
                QueryRetrieveLevel2.IMAGE,
                studyInstanceUID,
                seriesInstanceUID);
    }

    @POST
    @Path("/studies/csv:{field}/reject/{codeValue}^{codingSchemeDesignator}")
    @Consumes("text/csv")
    @Produces("application/json")
    public Response retrieveMatchingStudiesFromCSV(
            @PathParam("field") int field,
            @PathParam("codeValue") String codeValue,
            @PathParam("codingSchemeDesignator") String designator,
            InputStream in) {
        return rejectStudiesFromCSV(aet, field, codeValue, designator, in);
    }

    @Override
    public String toString() {
        String requestURI = request.getRequestURI();
        String queryString = request.getQueryString();
        return queryString == null ? requestURI : requestURI + '?' + queryString;
    }

    public void validate() {
        logRequest();
        new QueryAttributes(uriInfo, null);
    }

    Response rejectMatching(String aet, String codeValue, String designator,
                            String method, QueryRetrieveLevel2 qrlevel, String studyInstanceUID, String seriesInstanceUID) {
        try {
            ApplicationEntity ae = validateAE(aet);
            Code rjNoteCode = validateRejectionNote(codeValue, designator);
            if (rjNoteCode == null)
                return errResponse(
                        "No such Rejection Note : " + codeValue + "^" + designator, Response.Status.NOT_FOUND);

            QueryContext ctx = queryContext(method, qrlevel, studyInstanceUID, seriesInstanceUID, ae);
            String warning;
            int count;
            Response.Status status = Response.Status.ACCEPTED;
            try (Query query = queryService.createQuery(ctx)) {
                int queryMaxNumberOfResults = ctx.getArchiveAEExtension().queryMaxNumberOfResults();
                if (queryMaxNumberOfResults > 0 && !ctx.containsUniqueKey()
                        && query.fetchCount() > queryMaxNumberOfResults)
                    return errResponse("Request entity too large", Response.Status.BAD_REQUEST);

                RejectMatchingObjects rejectMatchingObjects = new RejectMatchingObjects(
                                                                        aet, rjNoteCode, qrlevel, query, status);
                runInTx.execute(rejectMatchingObjects);
                count = rejectMatchingObjects.getCount();
                status = rejectMatchingObjects.getStatus();
                warning = rejectMatchingObjects.getWarning();
            }
            Response.ResponseBuilder builder = Response.status(status);
            if (warning != null) {
                LOG.warn("Response {} caused by {}", status, warning);
                builder.header("Warning", warning);
            }
            return builder.entity("{\"count\":" + count + '}').build();
        } catch (IllegalStateException | ConfigurationException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    Response rejectStudiesFromCSV(String aet, int field, String codeValue, String designator, InputStream in) {
        try {
            validateAE(aet);
            Response.Status status = Response.Status.BAD_REQUEST;
            if (field < 1)
                return errResponse(
                        "CSV field for Study Instance UID should be greater than or equal to 1", status);

            Code rjNoteCode = validateRejectionNote(codeValue, designator);
            if (rjNoteCode == null)
                return errResponse(
                        "No such Rejection Note : " + codeValue + "^" + designator, Response.Status.NOT_FOUND);

            int count = 0;
            String warning = null;
            ArchiveDeviceExtension arcDev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
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

                    if (studyUIDs.size() == csvUploadChunkSize)
                        count = scheduleStudyRejectTasks(aet, count, rjNoteCode, studyUIDs);
                }
                if (!studyUIDs.isEmpty())
                    count = scheduleStudyRejectTasks(aet, count, rjNoteCode, studyUIDs);

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

        } catch (ConfigurationException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        }
    }

    private ApplicationEntity validateAE(String aet) throws ConfigurationException {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        if (ae == null || !ae.isInstalled())
            throw new ConfigurationException(
                    "No such Application Entity: " + aet + " found in device: " + device.getDeviceName());

        return ae;
    }

    private Code validateRejectionNote(String codeValue, String designator) {
        Code rjNoteCode;
        if (codeValue == null
                || (rjNoteCode = toRejectionNote(codeValue, designator).getRejectionNoteCode()) == null)
            return null;

        return rjNoteCode;
    }

    private char csvDelimiter() {
        return ("semicolon".equals(contentType.getParameters().get("delimiter"))) ? ';' : ',';
    }

    private boolean validateUID(String studyUID) {
        boolean valid = UIDUtils.isValid(studyUID);
        if (!valid)
            LOG.warn("Invalid UID in CSV file: " + studyUID);
        return valid;
    }

    private void logRequest() {
        LOG.info("Process {} {} from {}@{}",
                request.getMethod(),
                toString(),
                request.getRemoteUser(),
                request.getRemoteHost());
    }

    private void rejectMatching(String aet, Code rjNoteCode, Attributes match, QueryRetrieveLevel2 qrlevel,
                                HttpServletRequestInfo httpRequestInfo) {
        rejectionService.scheduleReject(aet,
                match.getString(Tag.StudyInstanceUID),
                qrlevel != QueryRetrieveLevel2.STUDY ? match.getString(Tag.SeriesInstanceUID) : null,
                qrlevel == QueryRetrieveLevel2.IMAGE ? match.getString(Tag.SOPInstanceUID) : null,
                rjNoteCode,
                httpRequestInfo,
                batchID);
    }

    private int scheduleStudyRejectTasks(String aet, int count, Code rjNoteCode, List<String> studyUIDs) {
        rejectionService.scheduleStudyRejectTasks(aet,
                studyUIDs,
                rjNoteCode,
                HttpServletRequestInfo.valueOf(request),
                batchID);
        count += studyUIDs.size();
        studyUIDs.clear();
        return count;
    }

    private RejectionNote toRejectionNote(String codeValue, String designator) {
        return arcDev().getRejectionNote(new Code(codeValue, designator, null, ""));
    }

    private QueryContext queryContext(
            String method, QueryRetrieveLevel2 qrlevel, String studyInstanceUID, String seriesInstanceUID,
            ApplicationEntity ae) {
        QueryContext ctx = queryService.newQueryContextQIDO(
                HttpServletRequestInfo.valueOf(request), method, aet, ae, queryParam(ae));
        ctx.setQueryRetrieveLevel(qrlevel);
        QueryAttributes queryAttrs = new QueryAttributes(uriInfo, null);
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
        queryParam.setAllOfModalitiesInStudy(Boolean.parseBoolean(allOfModalitiesInStudy));
        queryParam.setIncomplete(Boolean.parseBoolean(incomplete));
        queryParam.setRetrieveFailed(Boolean.parseBoolean(retrievefailed));
        queryParam.setStorageVerificationFailed(Boolean.parseBoolean(storageVerificationFailed));
        queryParam.setMetadataUpdateFailed(Boolean.parseBoolean(metadataUpdateFailed));
        queryParam.setCompressionFailed(Boolean.parseBoolean(compressionfailed));
        queryParam.setExternalRetrieveAET(externalRetrieveAET);
        queryParam.setExternalRetrieveAETNot(externalRetrieveAETNot);
        queryParam.setExpirationDate(expirationDate);
        if (patientVerificationStatus != null)
            queryParam.setPatientVerificationStatus(Patient.VerificationStatus.valueOf(patientVerificationStatus));
        if (storageID != null)
            queryParam.setStudyStorageIDs(
                    arcDev().getStudyStorageIDs(storageID, parseBoolean(storageClustered), parseBoolean(storageExported)));
        queryParam.setStudySizeRange(studySizeInKB);
        if (expirationState != null)
            queryParam.setExpirationState(ExpirationState.valueOf(expirationState));
        return queryParam;
    }

    private ArchiveDeviceExtension arcDev() {
        return device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
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

    class RejectMatchingObjects implements Runnable {
        private int count;
        private final String aet;
        private final Code rjNoteCode;
        private final QueryRetrieveLevel2 qrLevel;
        private final Query query;
        private Response.Status status;
        private String warning;

        RejectMatchingObjects(
                String aet, Code rjNoteCode, QueryRetrieveLevel2 qrLevel, Query query, Response.Status status) {
            this.aet = aet;
            this.rjNoteCode = rjNoteCode;
            this.qrLevel = qrLevel;
            this.query = query;
            this.status = status;
        }

        int getCount() {
            return count;
        }

        Response.Status getStatus() {
            return status;
        }

        String getWarning() {
            return warning;
        }

        @Override
        public void run() {
            try {
                HttpServletRequestInfo httpRequestInfo = HttpServletRequestInfo.valueOf(request);
                query.executeQuery(arcDev().getQueryFetchSize());
                while (query.hasMoreMatches()) {
                    Attributes match = query.nextMatch();
                    if (match == null)
                        continue;

                    rejectMatching(aet, rjNoteCode, match, qrLevel, httpRequestInfo);
                    count++;
                }
            } catch (Exception e) {
                warning = e.getMessage();
                status = Response.Status.INTERNAL_SERVER_ERROR;
            }
        }
    }
}
