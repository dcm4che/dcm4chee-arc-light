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
 * Portions created by the Initial Developer are Copyright (C) 2015
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
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.query.util.*;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.util.TagUtils;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.query.Query;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.validation.constraints.ValidUriInfo;
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
@ValidUriInfo(type = QidoRS.QueryAttributes.class)
public class QidoRS {

    private static final Logger LOG = LoggerFactory.getLogger(QidoRS.class);

    private static ElementDictionary DICT = ElementDictionary.getStandardElementDictionary();

    private final static int[] PATIENT_FIELDS = {
            Tag.PatientName,
            Tag.PatientID,
            Tag.PatientBirthDate,
            Tag.PatientSex,
    };

    private final static int[] STUDY_FIELDS = {
            Tag.StudyDate,
            Tag.StudyTime,
            Tag.AccessionNumber,
            Tag.ModalitiesInStudy,
            Tag.ReferringPhysicianName,
            Tag.PatientName,
            Tag.PatientID,
            Tag.PatientBirthDate,
            Tag.PatientSex,
            Tag.StudyID,
            Tag.StudyInstanceUID,
            Tag.NumberOfStudyRelatedSeries,
            Tag.NumberOfStudyRelatedInstances
    };

    private final static int[] SERIES_FIELDS = {
            Tag.Modality,
            Tag.SeriesDescription,
            Tag.SeriesNumber,
            Tag.SeriesInstanceUID,
            Tag.NumberOfSeriesRelatedInstances,
            Tag.PerformedProcedureStepStartDate,
            Tag.PerformedProcedureStepStartTime,
            Tag.RequestAttributesSequence
    };

    private final static int[] INSTANCE_FIELDS = {
            Tag.SOPClassUID,
            Tag.SOPInstanceUID,
            Tag.InstanceNumber,
            Tag.Rows,
            Tag.Columns,
            Tag.BitsAllocated,
            Tag.NumberOfFrames
    };

    private final static int[] MWL_FIELDS = {
            Tag.AccessionNumber,
            Tag.ReferringPhysicianName,
            Tag.ReferencedStudySequence,
            Tag.ReferencedPatientSequence,
            Tag.PatientName,
            Tag.PatientID,
            Tag.PatientBirthDate,
            Tag.PatientSex,
            Tag.PatientWeight,
            Tag.MedicalAlerts,
            Tag.Allergies,
            Tag.PregnancyStatus,
            Tag.StudyInstanceUID,
            Tag.RequestingPhysician,
            Tag.RequestedProcedureDescription,
            Tag.RequestedProcedureCodeSequence,
            Tag.AdmissionID,
            Tag.SpecialNeeds,
            Tag.CurrentPatientLocation,
            Tag.PatientState,
            Tag.ScheduledProcedureStepSequence,
            Tag.RequestedProcedureID,
            Tag.RequestedProcedurePriority,
            Tag.PatientTransportArrangements,
            Tag.ConfidentialityConstraintOnPatientDataDescription
    };

    private final static int[] STUDY_SERIES_FIELDS = catAndSort(STUDY_FIELDS, SERIES_FIELDS);

    private final static int[] STUDY_SERIES_INSTANCE_FIELDS = catAndSort(STUDY_SERIES_FIELDS, INSTANCE_FIELDS);

    private final static int[] SERIES_INSTANCE_FIELDS = catAndSort(SERIES_FIELDS, INSTANCE_FIELDS);

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

    @Override
    public String toString() {
        return request.getRequestURI() + '?' + request.getQueryString();
    }

    @GET
    @NoCache
    @Path("/patients")
    @Produces("multipart/related;type=application/dicom+xml")
    public Response searchForPatientsXML() throws Exception {
        return search("SearchForPatients", Model.PATIENT, null, null, PATIENT_FIELDS, Output.DICOM_XML);
    }

    @GET
    @NoCache
    @Path("/patients")
    @Produces("application/json")
    public Response searchForPatientsJSON() throws Exception {
        return search("SearchForPatients", Model.PATIENT, null, null, PATIENT_FIELDS, Output.JSON);
    }

    @GET
    @NoCache
    @Path("/studies")
    @Produces("multipart/related;type=application/dicom+xml")
    public Response searchForStudiesXML() throws Exception {
        return search("SearchForStudies", Model.STUDY, null, null, STUDY_FIELDS, Output.DICOM_XML);
    }

    @GET
    @NoCache
    @Path("/studies")
    @Produces("application/json")
    public Response searchForStudiesJSON() throws Exception {
        return search("SearchForStudies", Model.STUDY, null, null, STUDY_FIELDS, Output.JSON);
    }

    @GET
    @NoCache
    @Path("/series")
    @Produces("multipart/related;type=application/dicom+xml")
    public Response searchForSeriesXML() throws Exception {
        return search("SearchForSeries", Model.SERIES, null, null, STUDY_SERIES_FIELDS, Output.DICOM_XML);
    }

    @GET
    @NoCache
    @Path("/series")
    @Produces("application/json")
    public Response searchForSeriesJSON() throws Exception {
        return search("SearchForSeries", Model.SERIES, null, null, STUDY_SERIES_FIELDS, Output.JSON);
    }

    @GET
    @NoCache
    @Path("/studies/{StudyInstanceUID}/series")
    @Produces("multipart/related;type=application/dicom+xml")
    public Response searchForSeriesOfStudyXML(
            @PathParam("StudyInstanceUID") String studyInstanceUID) throws Exception {
        return search("SearchForStudySeries", Model.SERIES, studyInstanceUID, null, SERIES_FIELDS, Output.DICOM_XML);
    }

    @GET
    @NoCache
    @Path("/studies/{StudyInstanceUID}/series")
    @Produces("application/json")
    public Response searchForSeriesOfStudyJSON(
            @PathParam("StudyInstanceUID") String studyInstanceUID) throws Exception {
        return search("SearchForStudySeries", Model.SERIES, studyInstanceUID, null, SERIES_FIELDS, Output.JSON);
    }

    @GET
    @NoCache
    @Path("/instances")
    @Produces("multipart/related;type=application/dicom+xml")
    public Response searchForInstancesXML() throws Exception {
        return search("SearchForInstances", Model.INSTANCE, null, null, STUDY_SERIES_INSTANCE_FIELDS, Output.DICOM_XML);
    }

    @GET
    @NoCache
    @Path("/instances")
    @Produces("application/json")
    public Response searchForInstancesJSON() throws Exception {
        return search("SearchForInstances", Model.INSTANCE, null, null, STUDY_SERIES_INSTANCE_FIELDS, Output.JSON);
    }

    @GET
    @NoCache
    @Path("/studies/{StudyInstanceUID}/instances")
    @Produces("multipart/related;type=application/dicom+xml")
    public Response searchForInstancesOfStudyXML(
            @PathParam("StudyInstanceUID") String studyInstanceUID) throws Exception {
        return search("SearchForStudyInstances", Model.INSTANCE,
                studyInstanceUID, null, SERIES_INSTANCE_FIELDS, Output.DICOM_XML);
    }

    @GET
    @NoCache
    @Path("/studies/{StudyInstanceUID}/instances")
    @Produces("application/json")
    public Response searchForInstancesOfStudyJSON(
            @PathParam("StudyInstanceUID") String studyInstanceUID) throws Exception {
        return search("SearchForStudyInstances", Model.INSTANCE,
                studyInstanceUID, null, SERIES_INSTANCE_FIELDS, Output.JSON);
    }

    @GET
    @NoCache
    @Path("/studies/{StudyInstanceUID}/series/{SeriesInstanceUID}/instances")
    @Produces("multipart/related;type=application/dicom+xml")
    public Response searchForInstancesOfSeriesXML(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @PathParam("SeriesInstanceUID") String seriesInstanceUID) throws Exception {
        return search("SearchForStudySeriesInstances", Model.INSTANCE,
                studyInstanceUID, seriesInstanceUID, INSTANCE_FIELDS, Output.DICOM_XML);
    }

    @GET
    @NoCache
    @Path("/studies/{StudyInstanceUID}/series/{SeriesInstanceUID}/instances")
    @Produces("application/json")
    public Response searchForInstancesOfSeriesJSON(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @PathParam("SeriesInstanceUID") String seriesInstanceUID) throws Exception {
        return search("SearchForStudySeriesInstances", Model.INSTANCE,
                studyInstanceUID, seriesInstanceUID, INSTANCE_FIELDS, Output.JSON);
    }

    @GET
    @NoCache
    @Path("/mwlitems")
    @Produces("multipart/related;type=application/dicom+xml")
    public Response searchForSPSXML() throws Exception {
        return search("SearchForSPS", Model.MWL, null, null, MWL_FIELDS, Output.DICOM_XML);
    }

    @GET
    @NoCache
    @Path("/mwlitems")
    @Produces("application/json")
    public Response searchForSPSJSON() throws Exception {
        return search("SearchForSPS", Model.MWL, null, null, MWL_FIELDS, Output.JSON);
    }

    private Response search(String method, Model model, String studyInstanceUID, String seriesInstanceUID,
                            int[] includetags, Output output)
            throws Exception {
        LOG.info("Process GET {} from {}@{}", this, request.getRemoteUser(), request.getRemoteHost());
        QueryAttributes queryAttrs = new QueryAttributes(uriInfo);
        QueryContext ctx = newQueryContext(method, queryAttrs, studyInstanceUID, seriesInstanceUID, includetags, model);
        Query query = model.createQuery(service, ctx);
        try {
            query.initQuery();
            int maxResults = ctx.getArchiveAEExtension().qidoMaxNumberOfResults();
            int offsetInt = parseInt(offset);
            int limitInt = parseInt(limit);
            int remaining = 0;
            if (maxResults > 0 && (limitInt == 0 || limitInt >  maxResults) && !ctx.isConsiderPurgedInstances()) {
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

            List<OrderSpecifier<?>> orderSpecifiers = queryAttrs.getOrderSpecifiers(model);
            if (!orderSpecifiers.isEmpty()) {
                if (limitInt > 0)
                    orderSpecifiers.add(model.getPk().asc());
                query.orderBy(orderSpecifiers.toArray(new OrderSpecifier<?>[orderSpecifiers.size()]));
            }

            query.executeQuery();
            if (!query.hasMoreMatches())
                return Response.noContent().build();

            Response.ResponseBuilder builder = Response.ok();
            if (remaining > 0)
                builder.header("Warning", warning(remaining));

            return builder.entity(output.entity(this, method, query, model)).build();
        } finally {
            query.close();
        }
    }

    private String warning(int remaining) {
        return "299 " + request.getServerName() + ':' + request.getServerPort()
                + " \"There are " + remaining + " additional results that can be requested\"";
    }

    private QueryContext newQueryContext(String method, QueryAttributes queryAttrs, String studyInstanceUID,
                                         String seriesInstanceUID, int[] includetags, Model model) {
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
        QueryContext ctx = service.newQueryContextQIDO(request, method, ae, queryParam);
        ctx.setQueryRetrieveLevel(model.getQueryRetrieveLevel());
        Attributes keys = queryAttrs.getQueryKeys();
        IDWithIssuer idWithIssuer = IDWithIssuer.pidOf(keys);
        if (idWithIssuer != null)
            ctx.setPatientIDs(idWithIssuer);
        if (studyInstanceUID != null)
            keys.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUID);
        if (seriesInstanceUID != null)
            keys.setString(Tag.SeriesInstanceUID, VR.UI, seriesInstanceUID);
        ctx.setQueryKeys(keys);
        ctx.setReturnKeys(queryAttrs.getReturnKeys(includetags));
        ctx.setOrderByPatientName(queryAttrs.isOrderByPatientName());
        return ctx;
    }


    private static int[] catAndSort(int[] src1, int[] src2) {
        int[] dest = new int[src1.length + src2.length];
        System.arraycopy(src1, 0, dest, 0, src1.length);
        System.arraycopy(src2, 0, dest, src1.length, src2.length);
        Arrays.sort(dest);
        return dest;
    }

    private static int parseInt(String s) {
        return s != null ? Integer.parseInt(s) : 0;
    }

    private ApplicationEntity getApplicationEntity() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        if (ae == null || !ae.isInstalled())
            throw new WebApplicationException(
                    "No such Application Entity: " + aet,
                    Response.Status.SERVICE_UNAVAILABLE);
        return ae;
    }

    public static class QueryAttributes {
        private final Attributes keys = new Attributes();
        private final AttributesBuilder builder = new AttributesBuilder(keys);
        private boolean includeAll;
        private final ArrayList<OrderByTag> orderByTags = new ArrayList<>();
        private boolean orderByPatientName;

        public QueryAttributes(UriInfo info) {
            MultivaluedMap<String, String> map = info.getQueryParameters();
            for (Map.Entry<String, List<String>> entry : map.entrySet()) {
                String key = entry.getKey();
                switch (key) {
                    case "includefield":
                        addIncludeTag(entry.getValue());
                        break;
                    case "orderby":
                        addOrderByTag(entry.getValue());
                        break;
                    case "offset":
                    case "limit":
                    case "withoutstudies":
                    case "fuzzymatching":
                    case "returnempty":
                    case "expired":
                    case "retrievefailed":
                    case "incomplete":
                    case "SendingApplicationEntityTitleOfSeries":
                    case "StudyReceiveDateTime":
                        break;
                    default:
                        addQueryKey(key, entry.getValue());
                        break;
                }
            }
        }

        private void addIncludeTag(List<String> includefields) {
            for (String s : includefields) {
                if (s.equals("all")) {
                    includeAll = true;
                    break;
                }
                for (String field : StringUtils.split(s, ',')) {
                    try {
                        builder.setNull(field);
                    } catch (IllegalArgumentException e2) {
                        throw new IllegalArgumentException("includefield=" + s);
                    }
                }
            }
        }

        private void addOrderByTag(List<String> orderby) {
            for (String s : orderby) {
                try {
                    for (String field : StringUtils.split(s, ',')) {
                        boolean desc = field.charAt(0) == '-';
                        int tags[] = TagUtils.parseTagPath(desc ? field.substring(1) : field);
                        orderByTags.add(new OrderByTag(tags[tags.length-1], desc ? Order.DESC : Order.ASC));
                        if (tags[0] == Tag.PatientName)
                            orderByPatientName = true;
                    }
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("orderby=" + s);
                }
            }
        }

        public List<OrderSpecifier<?>> getOrderSpecifiers(Model model) {
            if (orderByTags.isEmpty())
                return Collections.emptyList();
            ArrayList<OrderSpecifier<?>> list = new ArrayList<>(orderByTags.size()+1);
            for (OrderByTag orderByTag : orderByTags)
                model.addOrderSpecifier(orderByTag.tag, orderByTag.order, list);
            return list;
        }

        public Attributes getQueryKeys() {
            return keys;
        }

        public Attributes getReturnKeys(int[] includetags) {
            if (includeAll)
                return null;

            Attributes returnKeys = new Attributes(keys.size() + 3 + includetags.length);
            returnKeys.addAll(keys);
            returnKeys.setNull(Tag.SpecificCharacterSet, VR.CS);
            returnKeys.setNull(Tag.RetrieveAETitle, VR.AE);
            returnKeys.setNull(Tag.InstanceAvailability, VR.CS);
            for (int tag : includetags)
               returnKeys.setNull(tag, DICT.vrOf(tag));
            return returnKeys;
        }

        public boolean isOrderByPatientName() {
            return orderByPatientName;
        }

        private static class OrderByTag {
            final int tag;
            final Order order;

            private OrderByTag(int tag, Order order) {
                this.tag = tag;
                this.order = order;
            }
        }

        private void addQueryKey(String attrPath, List<String> values) {
            try {
                builder.setString(attrPath, values.toArray(new String[values.size()]));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(attrPath + "=" + values.get(0));
            }
        }

    }

    private enum Model {
        PATIENT(QueryRetrieveLevel2.PATIENT, QPatient.patient.pk),
        STUDY(QueryRetrieveLevel2.STUDY, QStudy.study.pk),
        SERIES(QueryRetrieveLevel2.SERIES, QSeries.series.pk),
        INSTANCE(QueryRetrieveLevel2.IMAGE, QInstance.instance.pk),
        MWL(null, QMWLItem.mWLItem.pk) {
            @Override
            Query createQuery(QueryService service, QueryContext ctx) {
                return service.createMWLQuery(ctx);
            }

            @Override
            boolean addOrderSpecifier(int tag, Order order, List<OrderSpecifier<?>> result) {
                return QueryBuilder.addMWLOrderSpecifier(tag, order, result);
            }
        };

        final QueryRetrieveLevel2 qrLevel;
        final NumberPath<Long> pk;

        Model(QueryRetrieveLevel2 qrLevel, NumberPath<Long> pk) {
            this.qrLevel = qrLevel;
            this.pk = pk;
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
    }

    private enum Output {
        DICOM_XML {
            @Override
            Object entity(QidoRS service, String method, Query query, Model model) throws DicomServiceException {
                return service.writeXML(method, query, model);
            }
        },
        JSON {
            @Override
            Object entity(QidoRS service, String method, Query query, Model model) throws DicomServiceException {
                return service.writeJSON(method, query, model);
            }
        };

        abstract Object entity(QidoRS service, String method, Query query, Model model) throws DicomServiceException;
    }

    private Object writeXML(String method, Query query, Model model) throws DicomServiceException {
        MultipartRelatedOutput output = new MultipartRelatedOutput();
        int count = 0;
        while (query.hasMoreMatches()) {
            Attributes tmp = query.nextMatch();
            if (tmp == null)
                continue;

            final Attributes match = adjust(tmp, model, query);
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

    private Object writeJSON(String method, Query query, Model model) throws DicomServiceException {
        final ArrayList<Attributes> matches = new ArrayList<>();
        int count = 0;
        while (query.hasMoreMatches()) {
            Attributes tmp = query.nextMatch();
            if (tmp == null)
                continue;
            Attributes match = adjust(tmp, model, query);
            LOG.debug("{}: Match #{}:\n{}", method, ++count, match);
            matches.add(match);
        }
        LOG.info("{}: {} Matches", method, count);
        return new StreamingOutput() {
            @Override
            public void write(OutputStream out) throws IOException {
                try (JsonGenerator gen = Json.createGenerator(out)) {
                    JSONWriter writer = new JSONWriter(gen);
                    gen.writeStartArray();
                    for (Attributes match : matches) {
                        writer.write(match);
                    }
                    gen.writeEnd();
                }
            }
        };
    }

    private Attributes adjust(Attributes match, Model model, Query query) {
        match = query.adjust(match);
        switch(model) {
            case STUDY:
            case SERIES:
            case INSTANCE:
                match.setString(Tag.RetrieveURL, VR.UR, retrieveURL(match, model));
        }
        return match;
    }

    private String retrieveURL(Attributes match, Model model) {
        StringBuffer sb = device.getDeviceExtension(ArchiveDeviceExtension.class).remapRetrieveURL(request);
        sb.setLength(sb.lastIndexOf("/rs/"));
        sb.append("/rs/studies/").append(match.getString(Tag.StudyInstanceUID));
        if (model != Model.STUDY) {
            sb.append("/series/").append(match.getString(Tag.SeriesInstanceUID));
            if (model != Model.SERIES)
                sb.append("/instances/").append(match.getString(Tag.SOPInstanceUID));
        }
        return sb.toString();
    }
}
