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

import org.dcm4che3.data.*;
import org.dcm4che3.io.SAXTransformer;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.AttributeSet;
import org.dcm4chee.arc.conf.Entity;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.query.Query;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.query.util.QIDO;
import org.dcm4chee.arc.query.util.QueryAttributes;
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
import java.util.function.Predicate;
import java.util.stream.Collectors;
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

    @Inject
    private QueryService service;

    @Context
    private HttpServletRequest request;

    @Context
    private UriInfo uriInfo;

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

    @QueryParam("withoutstudies")
    @Pattern(regexp = "true|false")
    private String withoutstudies;

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

    private char csvDelimiter = ',';

    @Override
    public String toString() {
        return request.getRequestURI() + '?' + request.getQueryString();
    }

    public void validate() {
        logRequest();
        new QueryAttributes(uriInfo, attributeSetMap());
    }

    @GET
    @NoCache
    @Path("/patients")
    public Response searchForPatients() {
        return search("SearchForPatients", Model.PATIENT,
                null, null, QIDO.PATIENT);
    }

    @GET
    @NoCache
    @Path("/studies")
    public Response searchForStudies() {
        return search("SearchForStudies", Model.STUDY,
                null, null, QIDO.STUDY);
    }

    @GET
    @NoCache
    @Path("/series")
    public Response searchForSeries() {
        return search("SearchForSeries", Model.SERIES,
                null, null, QIDO.STUDY_SERIES);
    }

    @GET
    @NoCache
    @Path("/studies/{StudyInstanceUID}/series")
    public Response searchForSeriesOfStudy(
            @PathParam("StudyInstanceUID") String studyInstanceUID) {
        return search("SearchForStudySeries", Model.SERIES,
                studyInstanceUID, null, QIDO.SERIES);
    }

    @GET
    @NoCache
    @Path("/instances")
    public Response searchForInstances() {
        return search("SearchForInstances", Model.INSTANCE,
                null, null, QIDO.STUDY_SERIES_INSTANCE);
    }

    @GET
    @NoCache
    @Path("/studies/{StudyInstanceUID}/instances")
    public Response searchForInstancesOfStudy(
            @PathParam("StudyInstanceUID") String studyInstanceUID) {
        return search("SearchForStudyInstances", Model.INSTANCE,
                studyInstanceUID, null, QIDO.SERIES_INSTANCE);
    }

    @GET
    @NoCache
    @Path("/studies/{StudyInstanceUID}/series/{SeriesInstanceUID}/instances")
    public Response searchForInstancesOfSeries(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @PathParam("SeriesInstanceUID") String seriesInstanceUID) {
        return search("SearchForStudySeriesInstances", Model.INSTANCE,
                studyInstanceUID, seriesInstanceUID, QIDO.INSTANCE);
    }

    @GET
    @NoCache
    @Path("/mwlitems")
    public Response searchForSPS() {
        return search("SearchForSPS", Model.MWL, null, null, QIDO.MWL);
    }

    @GET
    @NoCache
    @Path("/workitems")
    public Response searchForUPS() {
        return search("SearchForUPS", Model.UPS, null, null, QIDO.UPS);
    }

    @GET
    @NoCache
    @Path("/patients/count")
    @Produces("application/json")
    public Response countPatients() {
        return count("CountPatients", Model.PATIENT, null, null);
    }

    @GET
    @NoCache
    @Path("/studies/count")
    @Produces("application/json")
    public Response countStudies() {
        return count("CountStudies", Model.STUDY, null, null);
    }

    @GET
    @NoCache
    @Path("/series/count")
    @Produces("application/json")
    public Response countSeries() {
        return count("CountSeries", Model.SERIES, null, null);
    }

    @GET
    @NoCache
    @Path("/studies/{StudyInstanceUID}/series/count")
    @Produces("application/json")
    public Response countSeriesOfStudy(
            @PathParam("StudyInstanceUID") String studyInstanceUID) {
        return count("CountStudySeries", Model.SERIES, studyInstanceUID, null);
    }

    @GET
    @NoCache
    @Path("/instances/count")
    @Produces("application/json")
    public Response countInstances() {
        return count("CountInstances", Model.INSTANCE, null, null);
    }

    @GET
    @NoCache
    @Path("/studies/{StudyInstanceUID}/instances/count")
    @Produces("application/json")
    public Response countInstancesOfStudy(
            @PathParam("StudyInstanceUID") String studyInstanceUID) {
        return count("CountStudyInstances", Model.INSTANCE, studyInstanceUID, null);
    }

    @GET
    @NoCache
    @Path("/studies/{StudyInstanceUID}/series/{SeriesInstanceUID}/instances/count")
    @Produces("application/json")
    public Response countInstancesOfSeries(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @PathParam("SeriesInstanceUID") String seriesInstanceUID) {
        return count("CountStudySeriesInstances", Model.INSTANCE, studyInstanceUID, seriesInstanceUID);
    }

    @GET
    @NoCache
    @Path("/mwlitems/count")
    @Produces("application/json")
    public Response countSPS() {
        return count("CountSPS", Model.MWL, null, null);
    }

    @GET
    @NoCache
    @Path("/workitems/count")
    @Produces("application/json")
    public Response countUPS() {
        return count("CountUPS", Model.UPS, null, null);
    }

    @GET
    @NoCache
    @Path("/studies/size")
    @Produces("application/json")
    public Response sizeOfStudies() {
        QueryAttributes queryAttrs = new QueryAttributes(uriInfo, null);
        QueryContext ctx = newQueryContext(
                "SizeOfStudies", queryAttrs, null, null, Model.STUDY);
        if (ctx.getQueryParam().noMatches()) {
            return Response.ok("{\"size\":0}").build();
        }
        ArchiveDeviceExtension arcdev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
        try (Query query = service.createStudyQuery(ctx)) {
            try (Stream<Long> studyPkStream = query.withUnknownSize(arcdev.getQueryFetchSize())) {
                Iterator<Long> studyPks = studyPkStream.iterator();
                while (studyPks.hasNext())
                    ctx.getQueryService().calculateStudySize(studyPks.next());
            } catch (Exception e) {
                return errResponseAsTextPlain(e);
            }
            return Response.ok("{\"size\":" + query.fetchSize() + '}').build();
        }
    }

    private Response count(String method, Model model, String studyInstanceUID, String seriesInstanceUID) {
        QueryAttributes queryAttrs = new QueryAttributes(uriInfo, null);
        QueryContext ctx = newQueryContext(method, queryAttrs, studyInstanceUID, seriesInstanceUID, model);
        if (ctx.getQueryParam().noMatches()) {
            return Response.ok("{\"count\":0}").build();
        }
        try (Query query = model.createQuery(service, ctx)) {
            return Response.ok("{\"count\":" + query.fetchCount() + '}').build();
        } catch (Exception e) {
            return errResponseAsTextPlain(e);
        }
    }

    private Map<String, AttributeSet> attributeSetMap() {
        return device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class).getAttributeSet(AttributeSet.Type.QIDO_RS);
    }

    private Response search(String method, Model model, String studyInstanceUID, String seriesInstanceUID, QIDO qido) {
        Output output = selectMediaType();
        QueryAttributes queryAttrs = new QueryAttributes(uriInfo, attributeSetMap());
        try {
            QueryContext ctx = newQueryContext(method, queryAttrs, studyInstanceUID, seriesInstanceUID, model);
            ctx.setReturnKeys(queryAttrs.isIncludeAll()
                    ? null
                    : includeDefaults() || queryAttrs.getQueryKeys().isEmpty()
                    ? queryAttrs.getReturnKeys(qido.includetags)
                    : queryAttrs.getQueryKeys());
            ctx.setReturnPrivate(queryAttrs.isIncludePrivate());
            ArchiveAEExtension arcAE = ctx.getArchiveAEExtension();
            ArchiveDeviceExtension arcdev = arcAE.getArchiveDeviceExtension();
            if (output == Output.CSV) {
                model.setIncludeAll(queryAttrs.isIncludeAll());
                model.setReturnKeys(ctx.getReturnKeys());
            }
            if (ctx.getQueryParam().noMatches()) {
                return Response.ok(
                        output.entity(this, method, null, model, null))
                        .type(output.type())
                        .build();
            }
            try (Query query = model.createQuery(service, ctx)) {
                int maxResults = arcAE.qidoMaxNumberOfResults();
                int offsetInt = parseInt(offset);
                int limitInt = parseInt(limit);
                int remaining = 0;
                if (maxResults > 0 && (limitInt == 0 || limitInt > maxResults) && !ctx.isConsiderPurgedInstances()) {
                    int numResults = (int) (query.fetchCount() - offsetInt);
                    if (numResults <= 0)
                        return Response.noContent().build();

                    remaining = numResults - maxResults;
                }
                query.executeQuery(arcdev.getQueryFetchSize(), offsetInt, remaining > 0 ? maxResults : limitInt);
                if (!query.hasMoreMatches())
                    return Response.noContent().build();

                Response.ResponseBuilder builder = Response.ok();
                if (remaining > 0)
                    builder.header("Warning", warning(remaining));

                return builder.entity(
                        output.entity(this, method, query, model, model.getAttributesCoercion(service, ctx)))
                        .type(output.type())
                        .build();
            }
        } catch (Exception e) {
            return errResponseAsTextPlain(e);
        }
    }

    private boolean includeDefaults() {
        return !"false".equals(includedefaults);
    }

    private void logRequest() {
        LOG.info("Process GET {}?{} from {}@{}",
                request.getRequestURI(),
                request.getQueryString(),
                request.getRemoteUser(),
                request.getRemoteHost());
    }

    private Output selectMediaType() {
        List<MediaType> acceptableMediaTypes = MediaTypeUtils.acceptableMediaTypesOf(headers, accept);
        if (acceptableMediaTypes.stream()
                .anyMatch(
                        ((Predicate<MediaType>) MediaTypes.APPLICATION_DICOM_JSON_TYPE::isCompatible)
                                .or(MediaType.APPLICATION_JSON_TYPE::isCompatible)))
            return Output.JSON;

        if (acceptableMediaTypes.stream()
                .map(m -> MediaTypes.getMultiPartRelatedType(m))
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
                                         String seriesInstanceUID, Model model) {
        ApplicationEntity ae = getApplicationEntity();

        org.dcm4chee.arc.query.util.QueryParam queryParam = new org.dcm4chee.arc.query.util.QueryParam(ae);
        queryParam.setCombinedDatetimeMatching(true);
        queryParam.setFuzzySemanticMatching(Boolean.parseBoolean(fuzzymatching));
        queryParam.setAllOfModalitiesInStudy(Boolean.parseBoolean(allOfModalitiesInStudy));
        queryParam.setWithoutStudies(withoutstudies == null || Boolean.parseBoolean(withoutstudies));
        queryParam.setIncomplete(Boolean.parseBoolean(incomplete));
        queryParam.setRetrieveFailed(Boolean.parseBoolean(retrievefailed));
        queryParam.setStorageVerificationFailed(Boolean.parseBoolean(storageVerificationFailed));
        queryParam.setMetadataUpdateFailed(Boolean.parseBoolean(metadataUpdateFailed));
        queryParam.setCompressionFailed(Boolean.parseBoolean(compressionfailed));
        queryParam.setExternalRetrieveAET(externalRetrieveAET);
        queryParam.setExternalRetrieveAETNot(externalRetrieveAETNot);
        queryParam.setExpirationDate(expirationDate);
        queryParam.setStudySizeRange(studySizeInKB);
        if (storageID != null)
            queryParam.setStudyStorageIDs(device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class)
                    .getStudyStorageIDs(storageID, parseBoolean(storageClustered), parseBoolean(storageExported)));
        if (patientVerificationStatus != null)
            queryParam.setPatientVerificationStatus(Patient.VerificationStatus.valueOf(patientVerificationStatus));
        if (expirationState != null)
            queryParam.setExpirationState(ExpirationState.valueOf(expirationState));
        QueryContext ctx = service.newQueryContextQIDO(request, method, ae, queryParam);
        ctx.setQueryRetrieveLevel(model.getQueryRetrieveLevel());
        ctx.setSOPClassUID(model.getSOPClassUID());
        Attributes keys = queryAttrs.getQueryKeys();
        IDWithIssuer idWithIssuer = IDWithIssuer.pidOf(keys);
        if (idWithIssuer != null)
            ctx.setPatientIDs(idWithIssuer);
        if (studyInstanceUID != null)
            keys.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUID);
        if (seriesInstanceUID != null)
            keys.setString(Tag.SeriesInstanceUID, VR.UI, seriesInstanceUID);
        ctx.setQueryKeys(keys);
        ctx.setOrderByTags(queryAttrs.getOrderByTags());
        return ctx;
    }

    private static Boolean parseBoolean(String s) {
        return s != null ? Boolean.valueOf(s) : null;
    }

    private static int parseInt(String s) {
        return s != null ? Integer.parseInt(s) : 0;
    }

    private ApplicationEntity getApplicationEntity() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        if (ae == null || !ae.isInstalled())
            throw new WebApplicationException(
                    "No such Application Entity: " + aet,
                    Response.Status.NOT_FOUND);
        return ae;
    }

    private enum Model {
        PATIENT(QueryRetrieveLevel2.PATIENT, UID.PatientRootQueryRetrieveInformationModelFIND){
            @Override
            public AttributesCoercion getAttributesCoercion(QueryService service, QueryContext ctx) {
                return null;
            }

            @Override
            public void addRetrieveURL(QidoRS qidoRS, Attributes match) {
            }
        },
        STUDY(QueryRetrieveLevel2.STUDY, UID.StudyRootQueryRetrieveInformationModelFIND) {
            @Override
            public StringBuffer retrieveURL(QidoRS qidoRS, Attributes match) {
                return super.retrieveURL(qidoRS, match)
                        .append("/studies/").append(match.getString(Tag.StudyInstanceUID));
            }
        },
        SERIES(QueryRetrieveLevel2.SERIES, UID.StudyRootQueryRetrieveInformationModelFIND) {
            @Override
            StringBuffer retrieveURL(QidoRS qidoRS, Attributes match) {
                return STUDY.retrieveURL(qidoRS, match)
                        .append("/series/").append(match.getString(Tag.SeriesInstanceUID));
            }
        },
        INSTANCE(QueryRetrieveLevel2.IMAGE, UID.StudyRootQueryRetrieveInformationModelFIND) {
            @Override
            StringBuffer retrieveURL(QidoRS qidoRS, Attributes match) {
                return SERIES.retrieveURL(qidoRS, match)
                        .append("/instances/").append(match.getString(Tag.SOPInstanceUID));
            }
        },
        MWL(null, UID.ModalityWorklistInformationModelFIND) {
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
        UPS(null, UID.UnifiedProcedureStepPullSOPClass) {
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
        Attributes returnKeys;
        boolean includeAll;

        Model(QueryRetrieveLevel2 qrLevel, String sopClassUID) {
            this.qrLevel = qrLevel;
            this.sopClassUID = sopClassUID;
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

        void setIncludeAll(boolean includeAll) {
            this.includeAll = includeAll;
        }

        void setReturnKeys(Attributes returnKeys) {
            this.returnKeys = returnKeys;
        }
    }

    private enum Output {
        DICOM_XML {
            @Override
            Object entity(QidoRS service, String method, Query query, Model model, AttributesCoercion coercion)
                    throws DicomServiceException {
                return service.writeXML(method, query, model, coercion);
            }

            @Override
            MediaType type() {
                return MediaTypes.MULTIPART_RELATED_APPLICATION_DICOM_XML_TYPE;
            }
        },
        JSON {
            @Override
            Object entity(QidoRS service, String method, Query query, Model model, AttributesCoercion coercion)
                    throws DicomServiceException {
                return service.writeJSON(method, query, model, coercion);
            }

            @Override
            MediaType type() {
                return MediaTypes.APPLICATION_DICOM_JSON_TYPE;
            }
        },
        CSV {
            @Override
            Object entity(QidoRS service, String method, Query query, Model model, AttributesCoercion coercion)
                    throws DicomServiceException {
                return service.writeCSV(method, query, model, coercion);
            }

            @Override
            MediaType type() {
                return MediaTypes.TEXT_CSV_UTF8_TYPE;
            }
        };

        abstract Object entity(QidoRS service, String method, Query query, Model model, AttributesCoercion coercion)
                throws DicomServiceException;

        abstract MediaType type();
    }

    private Object writeXML(String method, Query query, Model model, AttributesCoercion coercion)
            throws DicomServiceException {
        MultipartRelatedOutput output = new MultipartRelatedOutput();
        int count = 0;
        while (query != null && query.hasMoreMatches()) {
            Attributes tmp = query.nextMatch();
            if (tmp == null)
                continue;

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
            throws DicomServiceException {
        final List<Attributes> matches = matches(method, query, model, coercion);
        return (StreamingOutput) out -> {
                JsonGenerator gen = Json.createGenerator(out);
                JSONWriter writer = new JSONWriter(gen);
                gen.writeStartArray();
                for (Attributes match : matches)
                    writer.write(match);
                gen.writeEnd();
                gen.flush();
        };
    }

    private Object writeCSV(String method, Query query, Model model, AttributesCoercion coercion)
            throws DicomServiceException {
        final List<Attributes> matches =  matches(method, query, model, coercion);
        return (StreamingOutput) out -> {
            Writer writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
            int[] tags = tagsFrom(model, matches.get(0));
            if (tags.length != 0) {
                writeCSVHeader(writer, tags, matches.get(0));
                for (Attributes match : matches)
                    write(writer, match, tags);
            }
            writer.flush();
        };
    }

    private List<Attributes> matches(String method, Query query, Model model, AttributesCoercion coercion)
            throws DicomServiceException {
        if (query == null)
            return Collections.emptyList();

        final ArrayList<Attributes> matches = new ArrayList<>();
        int count = 0;
        while (query.hasMoreMatches()) {
            Attributes tmp = query.nextMatch();
            if (tmp == null)
                continue;
            Attributes match = adjust(tmp, model, query, coercion);
            LOG.debug("{}: Match #{}:\n{}", method, ++count, match);
            matches.add(match);
        }
        LOG.info("{}: {} Matches", method, count);
        return matches;
    }

    private int[] tagsFrom(Model model, Attributes match) {
        return model.includeAll
                ? allFieldsOf(model, match)
                : nonSeqTagsFrom(model.returnKeys);
    }

    private int[] allFieldsOf(Model model, Attributes match) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
        int[] tags = arcDev.getAttributeFilter(Entity.Patient).getSelection();
        switch (model) {
            case STUDY:
                return allNonSeqTags(match, tags, arcDev.getAttributeFilter(Entity.Study).getSelection());
            case SERIES:
                return allNonSeqTags(match, tags,
                        arcDev.getAttributeFilter(Entity.Study).getSelection(),
                        arcDev.getAttributeFilter(Entity.Series).getSelection());
            case INSTANCE:
                return allNonSeqTags(match, tags,
                        arcDev.getAttributeFilter(Entity.Study).getSelection(),
                        arcDev.getAttributeFilter(Entity.Series).getSelection(),
                        arcDev.getAttributeFilter(Entity.Instance).getSelection());
            case MWL:
                return allNonSeqTags(match, tags,
                        arcDev.getAttributeFilter(Entity.MWL).getSelection());
            case UPS:
                return allNonSeqTags(match, tags,
                        arcDev.getAttributeFilter(Entity.UPS).getSelection());
        }
        return allNonSeqTags(match, tags);
    }

    private int[] allNonSeqTags(Attributes match, int[]... tags) {
        Set<Integer> allNonSeqTags = new HashSet<>();
        for (int entityTags[] : tags)
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

    private void writeCSVHeader(Writer writer, int[] tags, Attributes match) throws IOException {
        writer.write(ElementDictionary.keywordOf(tags[0], match.getPrivateCreator(tags[0])));
        for (int i = 1; i < tags.length; i++) {
            writer.write(csvDelimiter);
            writer.write(ElementDictionary.keywordOf(tags[i], match.getPrivateCreator(tags[i])));
        }
        writer.write('\r');
        writer.write('\n');
    }

    private void write(Writer writer, Attributes attrs, int[] tags) throws IOException {
        writeNotNull(writer, attrs.getString(tags[0]));
        for (int i = 1; i < tags.length; i++) {
            writer.write(csvDelimiter);
            writeNotNull(writer, attrs.getString(tags[i]));
        }
        writer.write('\r');
        writer.write('\n');
    }

    private void writeNotNull(Writer writer, String val) throws IOException {
        if (val != null) {
            writer.append("\"");
            writer.write(val.replace("\"", "\"\""));
            writer.append("\"");
        }
    }

    private Attributes adjust(Attributes match, Model model, Query query, AttributesCoercion coercion) {
        if (coercion != null)
            coercion.coerce(match, null);
        match = query.adjust(match);
        model.addRetrieveURL(this, match);
        StringBuffer sb = model.retrieveURL(this, match);
        if (sb != null)
            match.setString(Tag.RetrieveURL, VR.UR, sb.toString());
        return match;
    }

    private Response errResponseAsTextPlain(Exception e) {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(exceptionAsString(e))
                .type("text/plain")
                .build();
    }

    private String exceptionAsString(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
