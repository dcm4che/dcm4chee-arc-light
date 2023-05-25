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

package org.dcm4chee.arc.qido;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;
import org.dcm4che3.data.*;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.SAXTransformer;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.WebApplication;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.AttributeSet;
import org.dcm4chee.arc.conf.Entity;
import org.dcm4chee.arc.entity.ExpirationState;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.keycloak.KeycloakContext;
import org.dcm4chee.arc.query.Query;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.query.util.QIDO;
import org.dcm4chee.arc.query.util.QueryAttributes;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveService;
import org.dcm4chee.arc.rs.util.MediaTypeUtils;
import org.dcm4chee.arc.validation.constraints.InvokeValidate;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.resteasy.plugins.providers.multipart.MultipartRelatedOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Sep 2015
 */
@RequestScoped
@Path("aets/{AETitle}/rs")
@InvokeValidate(type = QidoRS.class)
public class QidoRS {

    private static final Logger LOG = LoggerFactory.getLogger(QidoRS.class);
    private static final String SUPER_USER_ROLE = "super-user-role";

    @Inject
    private QueryService service;

    @Inject
    private RetrieveService retrieveService;

    @Context
    private HttpServletRequest request;

    @Context
    private UriInfo uriInfo;

    @Context
    private Request req;

    @Context
    private HttpHeaders headers;

    @Inject
    private Device device;

    @PathParam("AETitle")
    private String aet;

    @QueryParam("fuzzymatching")
    @Pattern(regexp = "true|false")
    private String fuzzymatching;

    @QueryParam("offset")
    @Pattern(regexp = "0|([1-9]\\d{0,4})")
    private String offset;

    @QueryParam("limit")
    @Pattern(regexp = "[1-9]\\d{0,4}")
    private String limit;

    @QueryParam("onlyWithStudies")
    @Pattern(regexp = "true|false")
    private String onlyWithStudies;

    @QueryParam("incomplete")
    @Pattern(regexp = "true|false")
    private String incomplete;

    @QueryParam("retrievefailed")
    @Pattern(regexp = "true|false")
    private String retrievefailed;

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

    @QueryParam("merged")
    @Pattern(regexp = "true|false")
    private String merged;

    @QueryParam("accept")
    private List<String> accept;

    @QueryParam("includedefaults")
    @Pattern(regexp = "true|false")
    private String includedefaults;

    @QueryParam("ExpirationDate")
    private String expirationDate;

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
    @Pattern(regexp = "\\d{1,9}(-\\d{0,9})?|-\\d{1,9}")
    private String studySizeInKB;

    @QueryParam("ExpirationState")
    @Pattern(regexp = "UPDATEABLE|FROZEN|REJECTED|EXPORT_SCHEDULED|FAILED_TO_EXPORT|FAILED_TO_REJECT")
    private String expirationState;

    @QueryParam("template")
    @Pattern(regexp = "true|false")
    private String template;

    @QueryParam("requested")
    @Pattern(regexp = "true|false")
    private String requested;

    @QueryParam("allmodified")
    @Pattern(regexp = "true|false")
    private String allmodifiedAsString;
    private boolean allmodified;

    private char csvDelimiter = ',';
    private QueryAttributes queryAttrs;

    @Override
    public String toString() {
        String requestURI = request.getRequestURI();
        String queryString = request.getQueryString();
        return queryString == null ? requestURI : requestURI + '?' + queryString;
    }

    public void validate() {
        logRequest();
        queryAttrs = new QueryAttributes(uriInfo, attributeSetMap());
        allmodified = Boolean.parseBoolean(allmodifiedAsString);
    }

    @GET
    @NoCache
    @Path("/patients")
    public Response searchForPatients() {
        return search("SearchForPatients", Model.PATIENT,
                null, null, QIDO.PATIENT, false, WebApplication.ServiceClass.QIDO_RS);
    }

    @GET
    @NoCache
    @Path("/studies")
    public Response searchForStudies() {
        return search("SearchForStudies", Model.STUDY,
                null, null, QIDO.STUDY, false, WebApplication.ServiceClass.QIDO_RS);
    }

    @GET
    @NoCache
    @Path("/series")
    public Response searchForSeries() {
        return search("SearchForSeries", Model.SERIES,
                null, null, QIDO.STUDY_SERIES, false, WebApplication.ServiceClass.QIDO_RS);
    }

    @GET
    @Path("/studies/{StudyInstanceUID}/series")
    public Response searchForSeriesOfStudy(
            @PathParam("StudyInstanceUID") String studyInstanceUID) {
        return search("SearchForStudySeries", Model.SERIES,
                studyInstanceUID, null, QIDO.SERIES, true, WebApplication.ServiceClass.QIDO_RS);
    }

    @GET
    @NoCache
    @Path("/instances")
    public Response searchForInstances() {
        return search("SearchForInstances", Model.INSTANCE,
                null, null, QIDO.STUDY_SERIES_INSTANCE, false, WebApplication.ServiceClass.QIDO_RS);
    }

    @GET
    @Path("/studies/{StudyInstanceUID}/instances")
    public Response searchForInstancesOfStudy(
            @PathParam("StudyInstanceUID") String studyInstanceUID) {
        return search("SearchForStudyInstances", Model.INSTANCE,
                studyInstanceUID, null, QIDO.SERIES_INSTANCE, true, WebApplication.ServiceClass.QIDO_RS);
    }

    @GET
    @Path("/studies/{StudyInstanceUID}/series/{SeriesInstanceUID}/instances")
    public Response searchForInstancesOfSeries(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @PathParam("SeriesInstanceUID") String seriesInstanceUID) {
        return search("SearchForStudySeriesInstances", Model.INSTANCE,
                studyInstanceUID, seriesInstanceUID, QIDO.INSTANCE, true, WebApplication.ServiceClass.QIDO_RS);
    }

    @GET
    @NoCache
    @Path("/mwlitems")
    public Response searchForSPS() {
        return search("SearchForSPS", Model.MWL, null,
                null, QIDO.MWL, false, WebApplication.ServiceClass.MWL_RS);
    }

    @GET
    @NoCache
    @Path("/mpps")
    public Response searchForMPPS() {
        return search("SearchForMPPS", Model.MPPS, null,
                null, QIDO.MPPS, false, WebApplication.ServiceClass.MPPS_RS);
    }

    @GET
    @NoCache
    @Path("/workitems")
    public Response searchForUPS() {
        return search("SearchForUPS", Model.UPS, null,
                null, QIDO.UPS, false, WebApplication.ServiceClass.UPS_RS);
    }

    @GET
    @NoCache
    @Path("/patients/count")
    @Produces("application/json")
    public Response countPatients() {
        return count("CountPatients", Model.PATIENT, null, null, WebApplication.ServiceClass.QIDO_COUNT);
    }

    @GET
    @NoCache
    @Path("/studies/count")
    @Produces("application/json")
    public Response countStudies() {
        return count("CountStudies", Model.STUDY, null, null, WebApplication.ServiceClass.QIDO_COUNT);
    }

    @GET
    @NoCache
    @Path("/series/count")
    @Produces("application/json")
    public Response countSeries() {
        return count("CountSeries", Model.SERIES, null, null, WebApplication.ServiceClass.QIDO_COUNT);
    }

    @GET
    @NoCache
    @Path("/studies/{StudyInstanceUID}/series/count")
    @Produces("application/json")
    public Response countSeriesOfStudy(
            @PathParam("StudyInstanceUID") String studyInstanceUID) {
        return count("CountStudySeries", Model.SERIES, studyInstanceUID, null, WebApplication.ServiceClass.QIDO_COUNT);
    }

    @GET
    @NoCache
    @Path("/instances/count")
    @Produces("application/json")
    public Response countInstances() {
        return count("CountInstances", Model.INSTANCE, null, null, WebApplication.ServiceClass.QIDO_COUNT);
    }

    @GET
    @NoCache
    @Path("/studies/{StudyInstanceUID}/instances/count")
    @Produces("application/json")
    public Response countInstancesOfStudy(
            @PathParam("StudyInstanceUID") String studyInstanceUID) {
        return count("CountStudyInstances", Model.INSTANCE, studyInstanceUID, null, WebApplication.ServiceClass.QIDO_COUNT);
    }

    @GET
    @NoCache
    @Path("/studies/{StudyInstanceUID}/series/{SeriesInstanceUID}/instances/count")
    @Produces("application/json")
    public Response countInstancesOfSeries(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @PathParam("SeriesInstanceUID") String seriesInstanceUID) {
        return count("CountStudySeriesInstances", Model.INSTANCE, studyInstanceUID, seriesInstanceUID, WebApplication.ServiceClass.QIDO_COUNT);
    }

    @GET
    @NoCache
    @Path("/mwlitems/count")
    @Produces("application/json")
    public Response countSPS() {
        return count("CountSPS", Model.MWL, null, null, WebApplication.ServiceClass.MWL_RS);
    }

    @GET
    @NoCache
    @Path("/mpps/count")
    @Produces("application/json")
    public Response countMPPS() {
        return count("CountMPPS", Model.MPPS, null, null, WebApplication.ServiceClass.MPPS_RS);
    }

    @GET
    @NoCache
    @Path("/workitems/count")
    @Produces("application/json")
    public Response countUPS() {
        return count("CountUPS", Model.UPS, null, null, WebApplication.ServiceClass.UPS_RS);
    }

    @GET
    @NoCache
    @Path("/studies/size")
    @Produces("application/json")
    public Response sizeOfStudies() {
        ArchiveAEExtension arcAE = getArchiveAE();
        if (arcAE == null)
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);

        validateAcceptedUserRoles(arcAE);
        ApplicationEntity ae = arcAE.getApplicationEntity();
        if (aet.equals(ae.getAETitle()))
            validateWebAppServiceClass(WebApplication.ServiceClass.DCM4CHEE_ARC_AET);

        try {
            QueryContext ctx = newQueryContext(
                    "SizeOfStudies", queryAttrs, null, null, Model.STUDY, ae);
            if (ctx.getQueryParam().noMatches()) {
                return Response.ok("{\"size\":0}").build();
            }
            ArchiveDeviceExtension arcdev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
            try (Query query = service.createStudyQuery(ctx)) {
                try (Stream<Long> studyPkStream = query.withUnknownSize(arcdev.getQueryFetchSize())) {
                    Iterator<Long> studyPks = studyPkStream.iterator();
                    while (studyPks.hasNext())
                        ctx.getQueryService().calculateStudySize(studyPks.next());
                }
                return Response.ok("{\"size\":" + query.fetchSize() + '}').build();
            }
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private Response count(String method, Model model, String studyInstanceUID, String seriesInstanceUID,
                           WebApplication.ServiceClass serviceClass) {
        ArchiveAEExtension arcAE = getArchiveAE();
        if (arcAE == null)
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);

        validateAcceptedUserRoles(arcAE);
        ApplicationEntity ae = arcAE.getApplicationEntity();
        if (aet.equals(ae.getAETitle()))
            validateWebAppServiceClass(serviceClass);

        try {
            QueryContext ctx = newQueryContext(method, queryAttrs, studyInstanceUID, seriesInstanceUID, model, ae);
            if (ctx.getQueryParam().noMatches())
                return Response.ok("{\"count\":0}").build();

            try (Query query = model.createQuery(service, ctx)) {
                return Response.ok("{\"count\":" + query.fetchCount() + '}').build();
            }
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private Map<String, AttributeSet> attributeSetMap() {
        return device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class).getAttributeSet(AttributeSet.Type.QIDO_RS);
    }

    private Response.ResponseBuilder evaluatePreConditions(Date lastModified) {
        return req.evaluatePreconditions(new Date((lastModified.getTime() / 1000) * 1000),
                new EntityTag(String.valueOf(lastModified.hashCode())));
    }

    private boolean ignorePatientUpdates(Attributes returnKeys) {
        int[] patientTags = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class)
                .getAttributeFilter(Entity.Patient).getSelection();
        for (int tag : patientTags)
            if (tag != Tag.SpecificCharacterSet && returnKeys.contains(tag))
                return false;
        return true;
    }

    private Response search(String method, Model model, String studyInstanceUID, String seriesInstanceUID, QIDO qido,
                            boolean etag, WebApplication.ServiceClass serviceClass) {
        ArchiveAEExtension arcAE = getArchiveAE();
        if (arcAE == null)
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);

        validateAcceptedUserRoles(arcAE);
        ApplicationEntity ae = arcAE.getApplicationEntity();
        if (aet.equals(ae.getAETitle()))
            validateWebAppServiceClass(serviceClass);

        Output output = selectMediaType();
        try {
            QueryContext ctx = newQueryContext(method, queryAttrs, studyInstanceUID, seriesInstanceUID, model, ae);
            ctx.setReturnKeys(queryAttrs.isIncludeAll()
                    ? null
                    : queryAttrs.getReturnKeys(includeDefaults() ? qido.includetags : qido.uids));
            Date lastModified = null;
            if (etag && arcAE.qidoETag()) {
                LOG.debug("Query Last Modified date of {}", model);
                lastModified = service.getLastModified(
                        !queryAttrs.isIncludeAll() && ignorePatientUpdates(ctx.getReturnKeys()),
                                        studyInstanceUID,
                                        seriesInstanceUID);
                if (lastModified == null) {
                    LOG.info("Last Modified date for Study[uid={}] Series[uid={}] is unavailable.",
                            studyInstanceUID, seriesInstanceUID);
                    return Response.noContent().build();
                }
                LOG.debug("Last Modified date: {}", lastModified);

                if (request.getHeader(HttpHeaders.IF_MODIFIED_SINCE) != null
                    || request.getHeader(HttpHeaders.IF_UNMODIFIED_SINCE) != null
                    || request.getHeader(HttpHeaders.IF_MATCH) != null
                    || request.getHeader(HttpHeaders.IF_NONE_MATCH) != null) {
                    Response.ResponseBuilder respBuilder = evaluatePreConditions(lastModified);
                    if (respBuilder != null) {
                        Response response = respBuilder.build();
                        LOG.debug("Preconditions are met - return status {}", response.getStatus());
                        return response;
                    }
                    LOG.debug("Preconditions are not met - build response");
                }
            }
            ctx.setReturnPrivate(queryAttrs.isIncludePrivate());
            if (ctx.getQueryParam().noMatches()) {
                return Response.ok(
                        output.entity(this, method, null, model, null, ctx))
                        .type(output.type())
                        .build();
            }
            try (Query query = model.createQuery(service, ctx)) {
                int maxResults = arcAE.qidoMaxNumberOfResults();
                int offsetInt = parseInt(offset);
                int limitInt = parseInt(limit);
                int remaining = 0;
                if (maxResults > 0 && (limitInt == 0 || limitInt > maxResults) && !ctx.isConsiderPurgedInstances()) {
                    LOG.debug("Query for number of matching {}s", model);
                    long matches = query.fetchCount();
                    LOG.debug("Number of matching {}s: {}", model, matches);
                    int numResults = (int) (matches - offsetInt);
                    if (numResults <= 0) {
                        LOG.debug("Offset {} >= {} - return 204 No Content", offsetInt, matches);
                        return Response.noContent().build();
                    }

                    remaining = numResults - maxResults;
                }
                int fetchSize = arcAE.getArchiveDeviceExtension().getQueryFetchSize();
                LOG.debug("Query for matching {}s", model);
                query.executeQuery(fetchSize, offsetInt, remaining > 0 ? maxResults : limitInt);
                if (!query.hasMoreMatches()) {
                    LOG.debug("No matching {}s found - return 204 No Content", model);
                    return Response.noContent().build();
                }
                Response.ResponseBuilder builder = Response.ok();
                if (remaining > 0) {
                    builder.header("Warning", warning(remaining));
                }
                if (lastModified != null) {
                    builder.lastModified(lastModified);
                    builder.tag(String.valueOf(lastModified.hashCode()));
                } else {
                    builder.header("Cache-Control", "no-cache");
                }
                Response response = builder.entity(
                        output.entity(this, method, query, model, model.getAttributesCoercion(service, ctx), ctx))
                        .type(output.type())
                        .build();
                LOG.debug("Writing response {}, {}", response.getStatus(), response.getHeaders());
                return response;
            }
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private boolean includeDefaults() {
        return !"false".equals(includedefaults);
    }

    private void logRequest() {
        LOG.info("Process {} {} from {}@{}",
                request.getMethod(),
                toString(),
                request.getRemoteUser(),
                request.getRemoteHost());
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
    
    private void validateWebAppServiceClass(WebApplication.ServiceClass serviceClass) {
        device.getWebApplications().stream()
                .filter(webApp -> request.getRequestURI().startsWith(webApp.getServicePath())
                                    && Arrays.asList(webApp.getServiceClasses())
                                        .contains(serviceClass))
                .findFirst()
                .orElseThrow(() -> new WebApplicationException(errResponse(
                        "No Web Application with " + serviceClass.name()
                                + "service class found for Application Entity: " + aet,
                        Response.Status.NOT_FOUND)));
    }

    private Output selectMediaType() {
        List<MediaType> acceptableMediaTypes = MediaTypeUtils.acceptableMediaTypesOf(headers, accept);
        if (acceptableMediaTypes.stream()
                .anyMatch(
                        ((Predicate<MediaType>) MediaTypes.APPLICATION_DICOM_JSON_TYPE::isCompatible)
                                .or(MediaType.APPLICATION_JSON_TYPE::isCompatible)))
            return Output.JSON;

        if (acceptableMediaTypes.stream()
                .map(MediaTypes::getMultiPartRelatedType)
                .anyMatch(MediaTypes.APPLICATION_DICOM_XML_TYPE::isCompatible))
            return Output.DICOM_XML;

        Optional<MediaType> csvMediaType = acceptableMediaTypes.stream()
                .filter(MediaTypes.TEXT_CSV_UTF8_TYPE::isCompatible).findFirst();
        if (csvMediaType.isPresent()) {
            if ("semicolon".equals(csvMediaType.get().getParameters().get("delimiter")))
                csvDelimiter = ';';
            return Output.CSV;
        }

        throw new WebApplicationException(Response.Status.NOT_ACCEPTABLE);
    }

    private String warning(int remaining) {
        return "299 " + request.getServerName() + ':' + request.getServerPort()
                + " \"There are " + remaining + " additional results that can be requested\"";
    }

    private QueryContext newQueryContext(String method, QueryAttributes queryAttrs, String studyInstanceUID,
                                         String seriesInstanceUID, Model model, ApplicationEntity ae) throws Exception {
        org.dcm4chee.arc.query.util.QueryParam queryParam = new org.dcm4chee.arc.query.util.QueryParam(ae);
        queryParam.setCalledAET(aet);
        queryParam.setCombinedDatetimeMatching(true);
        queryParam.setFuzzySemanticMatching(Boolean.parseBoolean(fuzzymatching));
        queryParam.setAllOfModalitiesInStudy(Boolean.parseBoolean(allOfModalitiesInStudy));
        queryParam.setOnlyWithStudies(Boolean.parseBoolean(onlyWithStudies));
        queryParam.setIncomplete(Boolean.parseBoolean(incomplete));
        queryParam.setRetrieveFailed(Boolean.parseBoolean(retrievefailed));
        queryParam.setStorageVerificationFailed(Boolean.parseBoolean(storageVerificationFailed));
        queryParam.setMetadataUpdateFailed(Boolean.parseBoolean(metadataUpdateFailed));
        queryParam.setCompressionFailed(Boolean.parseBoolean(compressionfailed));
        queryParam.setTemplate(Boolean.parseBoolean(template));
        queryParam.setMerged(Boolean.parseBoolean(merged));
        queryParam.setExternalRetrieveAET(externalRetrieveAET);
        queryParam.setExternalRetrieveAETNot(externalRetrieveAETNot);
        queryParam.setExpirationDate(expirationDate);
        queryParam.setStudySizeRange(studySizeInKB);
        queryParam.setRequested(requested);
        if (storageID != null)
            queryParam.setStudyStorageIDs(device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class)
                    .getStudyStorageIDs(storageID, parseBoolean(storageClustered), parseBoolean(storageExported)));
        if (patientVerificationStatus != null)
            queryParam.setPatientVerificationStatus(Patient.VerificationStatus.valueOf(patientVerificationStatus));
        if (expirationState != null)
            queryParam.setExpirationState(ExpirationState.valueOf(expirationState));
        QueryContext ctx = service.newQueryContextQIDO(
                HttpServletRequestInfo.valueOf(request), method, aet, ae, queryParam);
        ctx.setQueryRetrieveLevel(model.getQueryRetrieveLevel());
        ctx.setSOPClassUID(model.getSOPClassUID());
        Attributes keys = queryAttrs.getQueryKeys();
        ctx.setQueryKeys(keys);
        service.coerceAttributes(ctx);
        IDWithIssuer idWithIssuer = IDWithIssuer.pidOf(keys);
        if (idWithIssuer != null && !idWithIssuer.getID().equals("*"))
            ctx.setPatientIDs(idWithIssuer);
        else if (ctx.getArchiveAEExtension().filterByIssuerOfPatientID())
            ctx.setIssuerOfPatientID(Issuer.fromIssuerOfPatientID(keys));
        if (studyInstanceUID != null)
            keys.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUID);
        if (seriesInstanceUID != null)
            keys.setString(Tag.SeriesInstanceUID, VR.UI, seriesInstanceUID);
        ctx.setOrderByTags(queryAttrs.getOrderByTags());
        return ctx;
    }

    private static Boolean parseBoolean(String s) {
        return s != null ? Boolean.valueOf(s) : null;
    }

    private static int parseInt(String s) {
        return s != null ? Integer.parseInt(s) : 0;
    }

    private ArchiveAEExtension getArchiveAE() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        return ae == null || !ae.isInstalled() ? null : ae.getAEExtension(ArchiveAEExtension.class);
    }

    private enum Model {
        PATIENT(QueryRetrieveLevel2.PATIENT, UID.PatientRootQueryRetrieveInformationModelFind){
            @Override
            public AttributesCoercion getAttributesCoercion(QueryService service, QueryContext ctx) {
                return null;
            }

            @Override
            public void addRetrieveURL(QidoRS qidoRS, Attributes match) {
            }
        },
        STUDY(QueryRetrieveLevel2.STUDY, UID.StudyRootQueryRetrieveInformationModelFind, QidoRS::acceptStudyMatch) {
            @Override
            public StringBuffer retrieveURL(QidoRS qidoRS, Attributes match) {
                return super.retrieveURL(qidoRS, match)
                        .append("/studies/").append(match.getString(Tag.StudyInstanceUID));
            }
        },
        SERIES(QueryRetrieveLevel2.SERIES, UID.StudyRootQueryRetrieveInformationModelFind, QidoRS::acceptSeriesMatch) {
            @Override
            StringBuffer retrieveURL(QidoRS qidoRS, Attributes match) {
                return STUDY.retrieveURL(qidoRS, match)
                        .append("/series/").append(match.getString(Tag.SeriesInstanceUID));
            }
        },
        INSTANCE(QueryRetrieveLevel2.IMAGE, UID.StudyRootQueryRetrieveInformationModelFind, QidoRS::acceptInstanceMatch) {
            @Override
            StringBuffer retrieveURL(QidoRS qidoRS, Attributes match) {
                return SERIES.retrieveURL(qidoRS, match)
                        .append("/instances/").append(match.getString(Tag.SOPInstanceUID));
            }
        },
        MWL(null, UID.ModalityWorklistInformationModelFind) {
            @Override
            Query createQuery(QueryService service, QueryContext ctx) {
                return service.createMWLQuery(ctx);
            }

            @Override
            public AttributesCoercion getAttributesCoercion(QueryService service, QueryContext ctx) {
                return null;
            }

            @Override
            public void addRetrieveURL(QidoRS qidoRS, Attributes match) {
            }
        },
        MPPS(null, UID.ModalityPerformedProcedureStepRetrieve) {
            @Override
            Query createQuery(QueryService service, QueryContext ctx) {
                return service.createMPPSQuery(ctx);
            }

            @Override
            public AttributesCoercion getAttributesCoercion(QueryService service, QueryContext ctx) {
                return null;
            }

            @Override
            public void addRetrieveURL(QidoRS qidoRS, Attributes match) {
            }
        },
        UPS(null, UID.UnifiedProcedureStepPull) {
            @Override
            Query createQuery(QueryService service, QueryContext ctx) {
                return service.createUPSQuery(ctx);
            }

            @Override
            public AttributesCoercion getAttributesCoercion(QueryService service, QueryContext ctx) {
                return null;
            }

            @Override
            public void addRetrieveURL(QidoRS qidoRS, Attributes match) {
            }
        };

        final QueryRetrieveLevel2 qrLevel;
        final String sopClassUID;
        final BiPredicate<QidoRS, Attributes> acceptMatch;
        Model(QueryRetrieveLevel2 qrLevel, String sopClassUID) {
            this(qrLevel, sopClassUID, (qidoRS, match) -> true);
        }

        Model(QueryRetrieveLevel2 qrLevel, String sopClassUID, BiPredicate<QidoRS, Attributes> acceptMatch) {
            this.qrLevel = qrLevel;
            this.sopClassUID = sopClassUID;
            this.acceptMatch = acceptMatch;
        }

        QueryRetrieveLevel2 getQueryRetrieveLevel() {
            return qrLevel;
        }

        Query createQuery(QueryService service, QueryContext ctx) {
            return service.createQuery(ctx);
        }

        AttributesCoercion getAttributesCoercion(QueryService service, QueryContext ctx) {
            return service.getAttributesCoercion(ctx);
        }

        StringBuffer retrieveURL(QidoRS qidoRS, Attributes match) {
            StringBuffer sb = qidoRS.device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class)
                    .remapRetrieveURL(qidoRS.request);
            sb.setLength(sb.lastIndexOf("/rs/") + 3);
            return sb;
        }

        void addRetrieveURL(QidoRS qidoRS, Attributes match) {
            match.setString(Tag.RetrieveURL, VR.UR, retrieveURL(qidoRS, match).toString());
        }

        String getSOPClassUID() {
            return sopClassUID;
        }
    }

    private boolean acceptStudyMatch(Attributes match) {
        return queryAttrs.getModified().isEmpty() || isModified(match, readObject(
                match.getString(Tag.StudyInstanceUID),
                null, null));
    }

    private boolean acceptSeriesMatch(Attributes match) {
        return queryAttrs.getModified().isEmpty() || isModified(match, readObject(
                match.getString(Tag.StudyInstanceUID),
                match.getString(Tag.SeriesInstanceUID),
                null));
    }

    private boolean acceptInstanceMatch(Attributes match) {
        return queryAttrs.getModified().isEmpty() || isModified(match, readObject(
                match.getString(Tag.StudyInstanceUID),
                match.getString(Tag.SeriesInstanceUID),
                match.getString(Tag.SOPInstanceUID)));
    }

    private Attributes readObject(String studyUID, String seriesUID, String objectUID) {
        final RetrieveContext ctx = retrieveService.newRetrieveContextWADO(
                HttpServletRequestInfo.valueOf(request), aet, studyUID, seriesUID, objectUID);
        try {
            retrieveService.calculateMatches(ctx);
            try (DicomInputStream din = retrieveService.openDicomInputStream(ctx, ctx.getMatches().get(0))) {
                return din.readDatasetUntilPixelData();
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isModified(Attributes match, Attributes attrs) {
        return allmodified ? isAllModified(match, attrs) : isAnyModified(match, attrs);
    }

    private boolean isAllModified(Attributes match, Attributes attrs) {
        for (int[] tagPath : queryAttrs.getModified()) {
            if (Objects.equals(getString(match, tagPath), getString(attrs, tagPath)))
                return false;
        }
        return true;
    }

    private boolean isAnyModified(Attributes match, Attributes attrs) {
        for (int[] tagPath : queryAttrs.getModified()) {
            if (!Objects.equals(getString(match, tagPath), getString(attrs, tagPath)))
                return true;
        }
        return false;
    }

    private static String getString(Attributes item, int[] tagPath) {
        int last = tagPath.length - 1;
        for (int i  = 0; i < last; i++)
            if ((item = item.getNestedDataset(tagPath[i])) == null)
                return null;
        return item.getString(tagPath[last]);
    }

    private enum Output {
        DICOM_XML {
            @Override
            Object entity(QidoRS service, String method, Query query, Model model, AttributesCoercion coercion,
                          QueryContext ctx)
                    throws Exception {
                return service.writeXML(method, query, model, coercion);
            }

            @Override
            MediaType type() {
                return MediaTypes.MULTIPART_RELATED_APPLICATION_DICOM_XML_TYPE;
            }
        },
        JSON {
            @Override
            Object entity(QidoRS service, String method, Query query, Model model, AttributesCoercion coercion,
                          QueryContext ctx)
                    throws Exception {
                return service.writeJSON(method, query, model, coercion);
            }

            @Override
            MediaType type() {
                return MediaTypes.APPLICATION_DICOM_JSON_TYPE;
            }
        },
        CSV {
            @Override
            Object entity(QidoRS service, String method, Query query, Model model, AttributesCoercion coercion,
                          QueryContext ctx)
                    throws Exception {
                return service.writeCSV(method, query, model, coercion, ctx);
            }

            @Override
            MediaType type() {
                return MediaTypes.TEXT_CSV_UTF8_TYPE;
            }
        };

        abstract Object entity(QidoRS service, String method, Query query, Model model, AttributesCoercion coercion,
                               QueryContext ctx)
                throws Exception;

        abstract MediaType type();
    }

    private Object writeXML(String method, Query query, Model model, AttributesCoercion coercion)
            throws Exception {
        MultipartRelatedOutput output = new MultipartRelatedOutput();
        int count = 0;
        while (query != null && query.hasMoreMatches()) {
            Attributes tmp = query.nextMatch();
            if (tmp == null)
                continue;

            if (!model.acceptMatch.test(this, tmp)) {
                query.incrementLimit();
                continue;
            }
            final Attributes match = adjust(tmp, model, query, coercion);
            LOG.debug("{}: Match #{}:\n{}", method, ++count, match);
            output.addPart((StreamingOutput) out -> {
                            try {
                                SAXTransformer.getSAXWriter(new StreamResult(out)).write(match);
                            } catch (Exception e) {
                                throw new WebApplicationException(e);
                            }
                    },
                    MediaTypes.APPLICATION_DICOM_XML_TYPE);
        }
        LOG.info("{}: {} Matches", method, count);
        return output;
    }

    private Object writeJSON(String method, Query query, Model model, AttributesCoercion coercion)
            throws Exception {
        final List<Attributes> matches = matches(method, query, model, coercion);
        return (StreamingOutput) out -> {
                LOG.debug("Enter StreamingOutput.write");
                ArchiveAEExtension arcAE = getArchiveAE();
                if (arcAE != null) {
                    JsonGenerator gen = Json.createGenerator(out);
                    JSONWriter writer = arcAE.encodeAsJSONNumber(new JSONWriter(gen));
                    gen.writeStartArray();
                    for (Attributes match : matches)
                        writer.write(match);
                    gen.writeEnd();
                    gen.flush();
                }
                LOG.debug("Leave StreamingOutput.write");
        };
    }

    private Object writeCSV(String method, Query query, Model model, AttributesCoercion coercion, QueryContext ctx)
            throws Exception {
        final List<Attributes> matches =  matches(method, query, model, coercion);
        return (StreamingOutput) out -> {
            Writer writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
            if (matches.size() > 0) {
                int[] tags = tagsFrom(model, matches.get(0), ctx);
                if (tags.length != 0) {
                    CSVPrinter printer = new CSVPrinter(writer, CSVFormat.RFC4180.builder()
                            .setHeader(csvHeader(matches.get(0), tags))
                            .setDelimiter(csvDelimiter)
                            .setQuoteMode(QuoteMode.ALL)
                            .build());
                    matches.forEach(match -> printRecord(printer, match, tags));
                }
            }
            writer.flush();
        };
    }

    private List<Attributes> matches(String method, Query query, Model model, AttributesCoercion coercion)
            throws Exception {
        if (query == null)
            return Collections.emptyList();

        final ArrayList<Attributes> matches = new ArrayList<>();
        int count = 0;
        while (query.hasMoreMatches()) {
            Attributes tmp = query.nextMatch();
            if (tmp == null)
                continue;
            if (!model.acceptMatch.test(this, tmp)) {
                query.incrementLimit();
                continue;
            }
            Attributes match = adjust(tmp, model, query, coercion);
            LOG.debug("{}: Match #{}:\n{}", method, ++count, match);
            matches.add(match);
        }
        LOG.info("{}: {} Matches", method, count);
        return matches;
    }

    private int[] tagsFrom(Model model, Attributes match, QueryContext ctx) {
        Attributes returnKeys = ctx.getReturnKeys();
        return returnKeys == null
                ? allFieldsOf(model, match)
                : nonSeqTagsFrom(returnKeys);
    }

    private int[] allFieldsOf(Model model, Attributes match) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
        int[] tags = arcDev.getAttributeFilter(Entity.Patient).getSelection();
        switch (model) {
            case STUDY:
                return allNonSeqTags(match,
                                    tags,
                                    arcDev.getAttributeFilter(Entity.Study).getSelection(),
                                    new int[] { Tag.ModalitiesInStudy,
                                                Tag.NumberOfPatientRelatedStudies,
                                                Tag.NumberOfStudyRelatedSeries,
                                                Tag.NumberOfStudyRelatedInstances });
            case SERIES:
                return allNonSeqTags(match,
                                    tags,
                                    arcDev.getAttributeFilter(Entity.Study).getSelection(),
                                    arcDev.getAttributeFilter(Entity.Series).getSelection(),
                                    new int[] { Tag.NumberOfPatientRelatedStudies,
                                                Tag.NumberOfStudyRelatedSeries,
                                                Tag.NumberOfStudyRelatedInstances,
                                                Tag.NumberOfSeriesRelatedInstances,
                                                Tag.AvailableTransferSyntaxUID });
            case INSTANCE:
                return allNonSeqTags(match,
                                    tags,
                                    arcDev.getAttributeFilter(Entity.Study).getSelection(),
                                    arcDev.getAttributeFilter(Entity.Series).getSelection(),
                                    arcDev.getAttributeFilter(Entity.Instance).getSelection(),
                                    new int[] { Tag.NumberOfPatientRelatedStudies,
                                                Tag.NumberOfStudyRelatedSeries,
                                                Tag.NumberOfStudyRelatedInstances,
                                                Tag.NumberOfSeriesRelatedInstances,
                                                Tag.AvailableTransferSyntaxUID });
            case MWL:
                return allNonSeqTags(match, tags,
                        arcDev.getAttributeFilter(Entity.MWL).getSelection());
            case UPS:
                return allNonSeqTags(match, tags,
                        arcDev.getAttributeFilter(Entity.UPS).getSelection());
        }
        return allNonSeqTags(match, tags, new int[] { Tag.NumberOfPatientRelatedStudies });
    }

    private int[] allNonSeqTags(Attributes match, int[]... tags) {
        Set<Integer> allNonSeqTags = new HashSet<>();
        for (int[] entityTags : tags)
            for (int tag : entityTags)
                if (ElementDictionary.vrOf(tag, match.getPrivateCreator(tag)) != VR.SQ)
                    allNonSeqTags.add(tag);
        return allNonSeqTags.stream().mapToInt(Integer::intValue).toArray();
    }

    private int[] nonSeqTagsFrom(Attributes attrs) {
        Set<Integer> tags = new HashSet<>();
        try {
            attrs.accept((attrs1, tag, vr, value) -> {
                if (ElementDictionary.vrOf(tag, attrs.getPrivateCreator(tag)) != VR.SQ)
                    tags.add(tag);
                return true;
            }, false);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return tags.stream().mapToInt(Integer::intValue).toArray();
    }

    private String[] csvHeader(Attributes match, int[] tags) {
        return Arrays.stream(tags)
                .mapToObj(tag -> ElementDictionary.keywordOf(tag, match.getPrivateCreator(tag)))
                .toArray(String[]::new);
    }

    private void printRecord(CSVPrinter printer, Attributes match, int[] tags) {
        try {
            for (int tag : tags)
                try {
                    if (tag == Tag.ModalitiesInStudy) {
                        printer.print(String.join("\\", match.getStrings(tag)));
                    } else
                        printer.print(match.getString(tag));
                } catch (IOException e) {
                    LOG.debug("Error printing record for {}", tag);
                }
            printer.println();
        } catch (IOException e) {
            LOG.debug("Error printing record for newline");
        }
    }

    private Attributes adjust(Attributes match, Model model, Query query, AttributesCoercion coercion) throws Exception {
        if (coercion != null)
            coercion.coerce(match, null);
        match = query.adjust(match);
        if (model != Model.PATIENT && model != Model.MWL) {
            model.addRetrieveURL(this, match);
            StringBuffer sb = model.retrieveURL(this, match);
            if (sb != null)
                match.setString(Tag.RetrieveURL, VR.UR, sb.toString());
        }
        return match;
    }

    private Response errResponse(String errorMessage, Response.Status status) {
        return errResponseAsTextPlain("{\"errorMessage\":\"" + errorMessage + "\"}", status);
    }

    private Response errResponseAsTextPlain(String errorMsg, Response.Status status) {
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
}
