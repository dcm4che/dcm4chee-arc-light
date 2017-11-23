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
 * Portions created by the Initial Developer are Copyright (C) 2017
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

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.NumberPath;
import org.dcm4che3.data.*;
import org.dcm4che3.io.SAXTransformer;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.query.util.*;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.query.Query;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.validation.constraints.ValidUriInfo;
import org.hibernate.Transaction;
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
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.*;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Sep 2015
 */
@RequestScoped
@Path("aets/{AETitle}/rs")
@ValidUriInfo(type = QueryAttributes.class)
public class QidoRS {

    private static final Logger LOG = LoggerFactory.getLogger(QidoRS.class);

    @Inject
    private QueryService service;

    @Context
    private HttpServletRequest request;

    @Context
    private UriInfo uriInfo;

    @Inject
    private Device device;

    @PathParam("AETitle")
    private String aet;

    @QueryParam("returnempty")
    @Pattern(regexp = "true|false")
    private String returnempty;

    @QueryParam("expired")
    @Pattern(regexp = "true|false")
    private String expired;

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

    @GET
    @NoCache
    @Path("/patients")
    @Produces("multipart/related;type=application/dicom+xml")
    public Response searchForPatientsXML() throws Exception {
        return search("SearchForPatients", Model.PATIENT, null, null, QIDO.PATIENT, Output.DICOM_XML);
    }

    @GET
    @NoCache
    @Path("/patients")
    @Produces("application/dicom+json,application/json")
    public Response searchForPatientsJSON() throws Exception {
        return search("SearchForPatients", Model.PATIENT, null, null, QIDO.PATIENT, Output.JSON);
    }

    @GET
    @NoCache
    @Path("/studies")
    @Produces("multipart/related;type=application/dicom+xml")
    public Response searchForStudiesXML() throws Exception {
        return search("SearchForStudies", Model.STUDY, null, null, QIDO.STUDY, Output.DICOM_XML);
    }

    @GET
    @NoCache
    @Path("/studies")
    @Produces("application/dicom+json,application/json")
    public Response searchForStudiesJSON() throws Exception {
        return search("SearchForStudies", Model.STUDY, null, null, QIDO.STUDY, Output.JSON);
    }

    @GET
    @NoCache
    @Path("/series")
    @Produces("multipart/related;type=application/dicom+xml")
    public Response searchForSeriesXML() throws Exception {
        return search("SearchForSeries", Model.SERIES, null, null, QIDO.STUDY_SERIES, Output.DICOM_XML);
    }

    @GET
    @NoCache
    @Path("/series")
    @Produces("application/dicom+json,application/json")
    public Response searchForSeriesJSON() throws Exception {
        return search("SearchForSeries", Model.SERIES, null, null, QIDO.STUDY_SERIES, Output.JSON);
    }

    @GET
    @NoCache
    @Path("/studies/{StudyInstanceUID}/series")
    @Produces("multipart/related;type=application/dicom+xml")
    public Response searchForSeriesOfStudyXML(
            @PathParam("StudyInstanceUID") String studyInstanceUID) throws Exception {
        return search("SearchForStudySeries", Model.SERIES, studyInstanceUID, null, QIDO.SERIES, Output.DICOM_XML);
    }

    @GET
    @NoCache
    @Path("/studies/{StudyInstanceUID}/series")
    @Produces("application/dicom+json,application/json")
    public Response searchForSeriesOfStudyJSON(
            @PathParam("StudyInstanceUID") String studyInstanceUID) throws Exception {
        return search("SearchForStudySeries", Model.SERIES, studyInstanceUID, null, QIDO.SERIES, Output.JSON);
    }

    @GET
    @NoCache
    @Path("/instances")
    @Produces("multipart/related;type=application/dicom+xml")
    public Response searchForInstancesXML() throws Exception {
        return search("SearchForInstances", Model.INSTANCE, null, null, QIDO.STUDY_SERIES_INSTANCE, Output.DICOM_XML);
    }

    @GET
    @NoCache
    @Path("/instances")
    @Produces("application/dicom+json,application/json")
    public Response searchForInstancesJSON() throws Exception {
        return search("SearchForInstances", Model.INSTANCE, null, null, QIDO.STUDY_SERIES_INSTANCE, Output.JSON);
    }

    @GET
    @NoCache
    @Path("/studies/{StudyInstanceUID}/instances")
    @Produces("multipart/related;type=application/dicom+xml")
    public Response searchForInstancesOfStudyXML(
            @PathParam("StudyInstanceUID") String studyInstanceUID) throws Exception {
        return search("SearchForStudyInstances", Model.INSTANCE,
                studyInstanceUID, null, QIDO.SERIES_INSTANCE, Output.DICOM_XML);
    }

    @GET
    @NoCache
    @Path("/studies/{StudyInstanceUID}/instances")
    @Produces("application/dicom+json,application/json")
    public Response searchForInstancesOfStudyJSON(
            @PathParam("StudyInstanceUID") String studyInstanceUID) throws Exception {
        return search("SearchForStudyInstances", Model.INSTANCE,
                studyInstanceUID, null, QIDO.SERIES_INSTANCE, Output.JSON);
    }

    @GET
    @NoCache
    @Path("/studies/{StudyInstanceUID}/series/{SeriesInstanceUID}/instances")
    @Produces("multipart/related;type=application/dicom+xml")
    public Response searchForInstancesOfSeriesXML(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @PathParam("SeriesInstanceUID") String seriesInstanceUID) throws Exception {
        return search("SearchForStudySeriesInstances", Model.INSTANCE,
                studyInstanceUID, seriesInstanceUID, QIDO.INSTANCE, Output.DICOM_XML);
    }

    @GET
    @NoCache
    @Path("/studies/{StudyInstanceUID}/series/{SeriesInstanceUID}/instances")
    @Produces("application/dicom+json,application/json")
    public Response searchForInstancesOfSeriesJSON(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @PathParam("SeriesInstanceUID") String seriesInstanceUID) throws Exception {
        return search("SearchForStudySeriesInstances", Model.INSTANCE,
                studyInstanceUID, seriesInstanceUID, QIDO.INSTANCE, Output.JSON);
    }

    @GET
    @NoCache
    @Path("/mwlitems")
    @Produces("multipart/related;type=application/dicom+xml")
    public Response searchForSPSXML() throws Exception {
        return search("SearchForSPS", Model.MWL, null, null, QIDO.MWL, Output.DICOM_XML);
    }

    @GET
    @NoCache
    @Path("/mwlitems")
    @Produces("application/dicom+json,application/json")
    public Response searchForSPSJSON() throws Exception {
        return search("SearchForSPS", Model.MWL, null, null, QIDO.MWL, Output.JSON);
    }

    @GET
    @NoCache
    @Path("/patients/count")
    @Produces("application/json")
    public Response countPatients() throws Exception {
        return count("CountPatients", Model.PATIENT, null, null);
    }

    @GET
    @NoCache
    @Path("/studies/count")
    @Produces("application/json")
    public Response countStudies() throws Exception {
        return count("CountStudies", Model.STUDY, null, null);
    }

    @GET
    @NoCache
    @Path("/series/count")
    @Produces("application/json")
    public Response countSeries() throws Exception {
        return count("CountSeries", Model.SERIES, null, null);
    }

    @GET
    @NoCache
    @Path("/studies/{StudyInstanceUID}/series/count")
    @Produces("application/json")
    public Response countSeriesOfStudy(
            @PathParam("StudyInstanceUID") String studyInstanceUID) throws Exception {
        return count("CountStudySeries", Model.SERIES, studyInstanceUID, null);
    }

    @GET
    @NoCache
    @Path("/instances/count")
    @Produces("application/json")
    public Response countInstances() throws Exception {
        return count("CountInstances", Model.INSTANCE, null, null);
    }

    @GET
    @NoCache
    @Path("/studies/{StudyInstanceUID}/instances/count")
    @Produces("application/json")
    public Response countInstancesOfStudy(
            @PathParam("StudyInstanceUID") String studyInstanceUID) throws Exception {
        return count("CountStudyInstances", Model.INSTANCE, studyInstanceUID, null);
    }

    @GET
    @NoCache
    @Path("/studies/{StudyInstanceUID}/series/{SeriesInstanceUID}/instances/count")
    @Produces("application/json")
    public Response countInstancesOfSeries(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @PathParam("SeriesInstanceUID") String seriesInstanceUID) throws Exception {
        return count("CountStudySeriesInstances", Model.INSTANCE, studyInstanceUID, seriesInstanceUID);
    }

    @GET
    @NoCache
    @Path("/mwlitems/count")
    @Produces("application/json")
    public Response countSPS() throws Exception {
        return count("CountSPS", Model.MWL, null, null);
    }

    @GET
    @NoCache
    @Path("/studies/size")
    @Produces("application/json")
    public Response sizeOfStudies() throws Exception {
        logRequest();
        QueryAttributes queryAttrs = new QueryAttributes(uriInfo);
        QueryContext ctx = newQueryContext(
                "SizeOfStudies", queryAttrs, null, null, Model.STUDY);
        try (Query query = service.createQuery(ctx)) {
            query.initUnknownSizeQuery();
            Transaction transaction = query.beginTransaction();
            try {
                query.setFetchSize(device.getDeviceExtension(ArchiveDeviceExtension.class).getQueryFetchSize());
                query.executeQuery();
                Long studyPk;
                while ((studyPk = query.nextPk()) != null)
                    ctx.getQueryService().calculateStudySize(studyPk);
            } finally {
                try {
                    transaction.commit();
                } catch (Exception e) {
                    LOG.warn("Failed to commit transaction:\n{}", e);
                }
            }
        }
        try (Query query = service.createQuery(ctx)) {
            query.initSizeQuery();
            return Response.ok("{\"size\":" + query.size() + '}').build();
        }
    }

    private Response count(String method, Model model, String studyInstanceUID, String seriesInstanceUID)
            throws Exception {
        logRequest();
        QueryAttributes queryAttrs = new QueryAttributes(uriInfo);
        QueryContext ctx = newQueryContext(method, queryAttrs, studyInstanceUID, seriesInstanceUID, model);
        try (Query query = model.createQuery(service, ctx)) {
            query.initQuery();
            return Response.ok("{\"count\":" + query.count() + '}').build();
        }
    }

    private Response search(String method, Model model, String studyInstanceUID, String seriesInstanceUID,
                            QIDO qido, Output output)
            throws Exception {
        logRequest();
        QueryAttributes queryAttrs = new QueryAttributes(uriInfo);
        QueryContext ctx = newQueryContext(method, queryAttrs, studyInstanceUID, seriesInstanceUID, model);
        ctx.setReturnKeys(queryAttrs.getReturnKeys(qido.includetags));
        ArchiveAEExtension arcAE = ctx.getArchiveAEExtension();
        try (Query query = model.createQuery(service, ctx)) {
            query.initQuery();
            int maxResults = arcAE.qidoMaxNumberOfResults();
            int offsetInt = parseInt(offset);
            int limitInt = parseInt(limit);
            int remaining = 0;
            if (maxResults > 0 && (limitInt == 0 || limitInt > maxResults) && !ctx.isConsiderPurgedInstances()) {
                int numResults = (int) (query.count() - offsetInt);
                if (numResults <= 0)
                    return Response.noContent().build();

                remaining = numResults - maxResults;
            }
            if (offsetInt > 0)
                query.offset(offsetInt);

            if (remaining > 0)
                query.limit(maxResults);
            else if (limitInt > 0)
                query.limit(limitInt);

            ArrayList<QueryAttributes.OrderByTag> orderByTags = queryAttrs.getOrderByTags();
            if (!orderByTags.isEmpty()) {
                ArrayList<OrderSpecifier<?>> list = new ArrayList<>(orderByTags.size() + 1);
                for (QueryAttributes.OrderByTag orderByTag : orderByTags) {
                    model.addOrderSpecifier(orderByTag.tag, orderByTag.order, list);
                }
                if (limitInt > 0)
                    list.add(model.getPk().asc());
                query.orderBy(list.toArray(new OrderSpecifier<?>[list.size()]));
            }
            Transaction transaction = query.beginTransaction();
            try {
                query.setFetchSize(arcAE.getArchiveDeviceExtension().getQueryFetchSize());
                query.executeQuery();
                if (!query.hasMoreMatches())
                    return Response.noContent().build();

                Response.ResponseBuilder builder = Response.ok();
                if (remaining > 0)
                    builder.header("Warning", warning(remaining));

                return builder.entity(
                        output.entity(this, method, query, model, model.getAttributesCoercion(service, ctx)))
                        .build();
            } finally {
                try {
                    transaction.commit();
                } catch (Exception e) {
                    LOG.warn("Failed to commit transaction:\n{}", e);
                }
            }
        }
    }

    private void logRequest() {
        LOG.info("Process GET {} from {}@{}", request.getRequestURI(), request.getRemoteUser(), request.getRemoteHost());
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
        queryParam.setReturnEmpty(Boolean.parseBoolean(returnempty));
        queryParam.setExpired(Boolean.parseBoolean(expired));
        queryParam.setWithoutStudies(withoutstudies == null || Boolean.parseBoolean(withoutstudies));
        queryParam.setIncomplete(Boolean.parseBoolean(incomplete));
        queryParam.setRetrieveFailed(Boolean.parseBoolean(retrievefailed));
        queryParam.setSendingApplicationEntityTitleOfSeries(sendingApplicationEntityTitleOfSeries);
        queryParam.setStudyReceiveDateTime(studyReceiveDateTime);
        queryParam.setExternalRetrieveAET(externalRetrieveAET);
        queryParam.setExternalRetrieveAETNot(externalRetrieveAETNot);
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
        ctx.setOrderByPatientName(queryAttrs.isOrderByPatientName());
        return ctx;
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
        PATIENT(QueryRetrieveLevel2.PATIENT, QPatient.patient.pk, UID.PatientRootQueryRetrieveInformationModelFIND){
            @Override
            public AttributesCoercion getAttributesCoercion(QueryService service, QueryContext ctx) {
                return null;
            }

            @Override
            public void addRetrieveURL(QidoRS qidoRS, Attributes match) {
            }
        },
        STUDY(QueryRetrieveLevel2.STUDY, QStudy.study.pk, UID.StudyRootQueryRetrieveInformationModelFIND) {
            @Override
            public StringBuffer retrieveURL(QidoRS qidoRS, Attributes match) {
                return super.retrieveURL(qidoRS, match)
                        .append("/studies/").append(match.getString(Tag.StudyInstanceUID));
            }
        },
        SERIES(QueryRetrieveLevel2.SERIES, QSeries.series.pk, UID.StudyRootQueryRetrieveInformationModelFIND) {
            @Override
            StringBuffer retrieveURL(QidoRS qidoRS, Attributes match) {
                return STUDY.retrieveURL(qidoRS, match)
                        .append("/series/").append(match.getString(Tag.SeriesInstanceUID));
            }
        },
        INSTANCE(QueryRetrieveLevel2.IMAGE, QInstance.instance.pk, UID.StudyRootQueryRetrieveInformationModelFIND) {
            @Override
            StringBuffer retrieveURL(QidoRS qidoRS, Attributes match) {
                return SERIES.retrieveURL(qidoRS, match)
                        .append("/instances/").append(match.getString(Tag.SOPInstanceUID));
            }
        },
        MWL(null, QMWLItem.mWLItem.pk, UID.ModalityWorklistInformationModelFIND) {
            @Override
            Query createQuery(QueryService service, QueryContext ctx) {
                return service.createMWLQuery(ctx);
            }

            @Override
            boolean addOrderSpecifier(int tag, Order order, List<OrderSpecifier<?>> result) {
                return QueryBuilder.addMWLOrderSpecifier(tag, order, result);
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
        final NumberPath<Long> pk;
        final private String sopClassUID;

        Model(QueryRetrieveLevel2 qrLevel, NumberPath<Long> pk, String sopClassUID) {
            this.qrLevel = qrLevel;
            this.pk = pk;
            this.sopClassUID = sopClassUID;
        }

        QueryRetrieveLevel2 getQueryRetrieveLevel() {
            return qrLevel;
        }

        NumberPath<Long> getPk() {
            return pk;
        }

        Query createQuery(QueryService service, QueryContext ctx) {
            return service.createQuery(ctx);
        }

        boolean addOrderSpecifier(int tag, Order order, List<OrderSpecifier<?>> result) {
            return QueryBuilder.addOrderSpecifier(qrLevel, tag, order, result);
        }

        AttributesCoercion getAttributesCoercion(QueryService service, QueryContext ctx) {
            return service.getAttributesCoercion(ctx);
        }

        StringBuffer retrieveURL(QidoRS qidoRS, Attributes match) {
            StringBuffer sb = qidoRS.device.getDeviceExtension(ArchiveDeviceExtension.class)
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

    private enum Output {
        DICOM_XML {
            @Override
            Object entity(QidoRS service, String method, Query query, Model model, AttributesCoercion coercion)
                    throws DicomServiceException {
                return service.writeXML(method, query, model, coercion);
            }
        },
        JSON {
            @Override
            Object entity(QidoRS service, String method, Query query, Model model, AttributesCoercion coercion)
                    throws DicomServiceException {
                return service.writeJSON(method, query, model, coercion);
            }
        };

        abstract Object entity(QidoRS service, String method, Query query, Model model, AttributesCoercion coercion)
                throws DicomServiceException;
    }

    private Object writeXML(String method, Query query, Model model, AttributesCoercion coercion)
            throws DicomServiceException {
        MultipartRelatedOutput output = new MultipartRelatedOutput();
        int count = 0;
        while (query.hasMoreMatches()) {
            Attributes tmp = query.nextMatch();
            if (tmp == null)
                continue;

            final Attributes match = adjust(tmp, model, query, coercion);
            LOG.debug("{}: Match #{}:\n{}", method, ++count, match);
            output.addPart(
                    new StreamingOutput() {
                        @Override
                        public void write(OutputStream out) throws IOException,
                                WebApplicationException {
                            try {
                                SAXTransformer.getSAXWriter(new StreamResult(out)).write(match);
                            } catch (Exception e) {
                                throw new WebApplicationException(e);
                            }
                        }
                    },
                    MediaTypes.APPLICATION_DICOM_XML_TYPE);
        }
        LOG.info("{}: {} Matches", method, count);
        return output;
    }

    private Object writeJSON(String method, Query query, Model model, AttributesCoercion coercion)
            throws DicomServiceException {
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
        return new StreamingOutput() {
            @Override
            public void write(OutputStream out) throws IOException {
                JsonGenerator gen = Json.createGenerator(out);
                JSONWriter writer = new JSONWriter(gen);
                gen.writeStartArray();
                for (Attributes match : matches) {
                    writer.write(match);
                }
                gen.writeEnd();
                gen.flush();
            }
        };
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
}
