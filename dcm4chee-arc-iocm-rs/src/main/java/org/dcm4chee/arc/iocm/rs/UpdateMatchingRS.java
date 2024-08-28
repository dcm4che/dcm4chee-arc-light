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

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParsingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import jakarta.ws.rs.core.UriInfo;
import org.dcm4che3.data.*;
import org.dcm4che3.json.JSONReader;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.WebApplication;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.entity.ExpirationState;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.keycloak.KeycloakContext;
import org.dcm4chee.arc.query.Query;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.query.RunInTransaction;
import org.dcm4chee.arc.query.util.QueryAttributes;
import org.dcm4chee.arc.study.StudyMgtContext;
import org.dcm4chee.arc.study.StudyService;
import org.dcm4chee.arc.validation.ParseDateTime;
import org.dcm4chee.arc.validation.constraints.InvokeValidate;
import org.dcm4chee.arc.validation.constraints.ValidValueOf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * @author Gunter Zeilinger <gunterze@protonmail.com>
 * @since Apr 2023
 */
@RequestScoped
@Path("aets/{AETitle}/rs")
@InvokeValidate(type = UpdateMatchingRS.class)
public class UpdateMatchingRS {

    private static final Logger LOG = LoggerFactory.getLogger(UpdateMatchingRS.class);
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
    private QueryService queryService;

    @Inject
    private StudyService studyService;

    @Inject
    private RunInTransaction runInTx;

    @QueryParam("batchID")
    private String batchID;

    @QueryParam("scheduledTime")
    @ValidValueOf(type = ParseDateTime.class)
    private String scheduledTime;

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

    @QueryParam("updatePolicy")
    @DefaultValue("OVERWRITE")
    @Pattern(regexp = "SUPPLEMENT|MERGE|OVERWRITE")
    private String updatePolicyName;

    @QueryParam("reasonForModification")
    @Pattern(regexp = "COERCE|CORRECT")
    private String reasonForModification;

    @QueryParam("sourceOfPreviousValues")
    private String sourceOfPreviousValues;

    private QueryAttributes queryAttrs;

    private static Boolean parseBoolean(String s) {
        return s != null ? Boolean.valueOf(s) : null;
    }

    @POST
    @Path("/studies/update")
    @Consumes("application/dicom+json,application/json")
    @Produces("application/json")
    public Response updateMatchingStudies(InputStream in) {
        return updateMatching(aet, "updateMatchingStudies", QueryRetrieveLevel2.STUDY,
                null, in);
    }

    @POST
    @Path("/series/update")
    @Consumes("application/dicom+json,application/json")
    @Produces("application/json")
    public Response updateMatchingSeries(InputStream in) {
        return updateMatching(aet, "updateMatchingSeries", QueryRetrieveLevel2.SERIES,
                null, in);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}/series/update")
    @Consumes("application/dicom+json,application/json")
    @Produces("application/json")
    public Response updateMatchingSeriesOfStudy(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            InputStream in) {
        return updateMatching(aet, "updateMatchingSeriesOfStudy", QueryRetrieveLevel2.SERIES,
                studyInstanceUID, in);
    }

    @Override
    public String toString() {
        String requestURI = request.getRequestURI();
        String queryString = request.getQueryString();
        return queryString == null ? requestURI : requestURI + '?' + queryString;
    }

    public void validate() {
        logRequest();
        queryAttrs = new QueryAttributes(uriInfo, null);
    }

    private Attributes toAttributes(InputStream in) {
        try {
            return new JSONReader(Json.createParser(new InputStreamReader(in, StandardCharsets.UTF_8)))
                    .readDataset(null);
        } catch (JsonParsingException e) {
            throw new WebApplicationException(
                    errResponse(e.getMessage() + " at location : " + e.getLocation(), Response.Status.BAD_REQUEST));
        } catch (Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    Response updateMatching(String aet, String method, QueryRetrieveLevel2 qrlevel,
                            String studyInstanceUID, InputStream in) {
        ArchiveAEExtension arcAE = getArchiveAE();
        if (arcAE == null)
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);

        validateAcceptedUserRoles(arcAE);
        ApplicationEntity ae = arcAE.getApplicationEntity();
        if (aet.equals(ae.getAETitle()))
            validateWebAppServiceClass();

        StudyMgtContext studyMgtCtx = studyService.createStudyMgtContextWEB(
                HttpServletRequestInfo.valueOf(request), arcAE.getApplicationEntity());
        studyMgtCtx.setReasonForModification(reasonForModification);
        studyMgtCtx.setSourceOfPreviousValues(sourceOfPreviousValues);
        final Attributes attrs = new Attributes(toAttributes(in),
                    (qrlevel == QueryRetrieveLevel2.STUDY
                            ? studyMgtCtx.getStudyAttributeFilter()
                            : studyMgtCtx.getSeriesAttributeFilter())
                        .getSelection());
        QueryContext ctx = queryContext(method, qrlevel, studyInstanceUID, ae);
        try (Query query = queryService.createQuery(ctx)) {
            int queryMaxNumberOfResults = ctx.getArchiveAEExtension().queryMaxNumberOfResults();
            if (queryMaxNumberOfResults > 0 && !ctx.containsUniqueKey()
                    && query.fetchCount() > queryMaxNumberOfResults)
                return errResponse("Request entity too large. Query count exceeds configured Query Max Number of Results, narrow down search using query filters.",
                        Response.Status.REQUEST_ENTITY_TOO_LARGE);

            UpdateMatchingEntities updateMatchingObjects =
                    new UpdateMatchingEntities(aet, attrs, qrlevel, studyMgtCtx, query);
            runInTx.execute(updateMatchingObjects);
            return updateMatchingObjects.buildResponse();
        }
     }

    private void logRequest() {
        LOG.info("Process {} {} from {}@{}",
                request.getMethod(),
                toString(),
                request.getRemoteUser(),
                request.getRemoteHost());
    }

    private QueryContext queryContext(
            String method, QueryRetrieveLevel2 qrlevel, String studyInstanceUID,
            ApplicationEntity ae) {
        QueryContext ctx = queryService.newQueryContextQIDO(
                HttpServletRequestInfo.valueOf(request), method, aet, ae, queryParam(ae));
        ctx.setQueryRetrieveLevel(qrlevel);
        QueryAttributes queryAttrs = new QueryAttributes(uriInfo, null);
        Attributes keys = queryAttrs.getQueryKeys();
        IDWithIssuer idWithIssuer = IDWithIssuer.pidOf(keys);
        if (idWithIssuer != null && !idWithIssuer.getID().equals("*"))
            ctx.setPatientIDs(idWithIssuer);
        else if (ctx.getArchiveAEExtension().filterByIssuerOfPatientID())
            ctx.setIssuerOfPatientID(Issuer.fromIssuerOfPatientID(keys));
        if (studyInstanceUID != null)
            keys.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUID);
        ctx.setQueryKeys(keys);
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

    class UpdateMatchingEntities implements Runnable {
        private final StudyMgtContext studyMgtCtx;
        private final Attributes.UpdatePolicy updatePolicy;
        private final String aet;
        private final Attributes attrs;
        private final QueryRetrieveLevel2 qrLevel;
        private final Query query;
        private int count;
        private int updated;
        private int failed;
        private Exception ex;

        UpdateMatchingEntities(String aet, Attributes attrs, QueryRetrieveLevel2 qrLevel, StudyMgtContext studyMgtCtx, Query query) {
            this.aet = aet;
            this.attrs = attrs;
            this.qrLevel = qrLevel;
            this.studyMgtCtx = studyMgtCtx;
            this.query = query;
            this.updatePolicy =Attributes.UpdatePolicy.valueOf(updatePolicyName);
        }

        int getCount() {
            return count;
        }

        Exception getException() {
            return ex;
        }

        @Override
        public void run() {
            try {
                query.executeQuery(arcDev().getQueryFetchSize());
                while (query.hasMoreMatches()) {
                    Attributes match = query.nextMatch();
                    if (match == null)
                        continue;

                    Attributes.unifyCharacterSets(match, attrs);
                    if (match.update(updatePolicy, attrs, null)) {
                        try {
                            studyMgtCtx.setAttributes(match);
                            if (qrLevel == QueryRetrieveLevel2.STUDY) {
                                studyService.updateStudy(studyMgtCtx);
                            } else {
                                assert qrLevel == QueryRetrieveLevel2.SERIES;
                                studyService.updateSeries(studyMgtCtx);
                            }
                            updated++;
                        } catch (Exception e) {
                            ex = e;
                            failed++;
                        }
                    }
                    count++;
                }
            } catch (Exception e) {
                ex = e;
            }
        }

        Response buildResponse() {
            if (count == 0) {
                return ex == null
                        ? Response.noContent().build()
                        : errResponse(ex.getMessage(), Response.Status.INTERNAL_SERVER_ERROR);
            }
            return Response.status(failed == 0
                    ? Response.Status.OK : updated > 0
                    ? Response.Status.ACCEPTED : Response.Status.CONFLICT)
                    .entity((StreamingOutput) this::writeTo)
                    .build();
        }

        private void writeTo(OutputStream out) {
            JsonGenerator gen = Json.createGenerator(out);
            gen.writeStartObject();
            gen.write("count", count);
            if (updated > 0) gen.write("updated", updated);
            if (failed > 0) gen.write("failed", failed);
            if (ex != null) gen.write("error", ex.getMessage());
            gen.writeEnd();
            gen.flush();
        }
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
                .orElseThrow(() -> new WebApplicationException(errResponse(
                        "No Web Application with DCM4CHEE_ARC_AET service class found for Application Entity: " + aet,
                        Response.Status.NOT_FOUND)));
    }

    private ArchiveAEExtension getArchiveAE() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        return ae == null || !ae.isInstalled() ? null : ae.getAEExtensionNotNull(ArchiveAEExtension.class);
    }
}
