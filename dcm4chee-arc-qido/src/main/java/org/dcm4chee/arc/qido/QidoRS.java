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
import org.dcm4che3.data.*;
import org.dcm4che3.io.SAXTransformer;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.query.Query;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.query.util.QueryBuilder;
import org.dcm4chee.arc.validation.constraints.ValidUriInfo;
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
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Sep 2015
 */
@RequestScoped
@Path("aets/{AETitle}/rs")
@ValidUriInfo(type = QidoRS.QueryAttributes.class)
public class QidoRS {

    private static final Logger LOG = LoggerFactory.getLogger(QidoRS.class);

    private static ElementDictionary DICT = ElementDictionary.getStandardElementDictionary();

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

    @QueryParam("fuzzymatching")
    @Pattern(regexp = "true|false")
    private String fuzzymatching;

    @QueryParam("offset")
    @Pattern(regexp = "0|([1-9]\\d{0,4})")
    private String offset;

    @QueryParam("limit")
    @Pattern(regexp = "[1-9]\\d{0,4}")
    private String limit;

    @Override
    public String toString() {
        return request.getQueryString();
    }

    @GET
    @Path("/studies")
    @Produces("multipart/related;type=application/dicom+xml")
    public Response searchForStudiesXML() throws Exception {
        return search("searchForStudiesXML", QueryRetrieveLevel2.STUDY,
                null, null, STUDY_FIELDS, Output.DICOM_XML);
    }

    @GET
    @Path("/studies")
    @Produces("application/json")
    public Response searchForStudiesJSON() throws Exception {
        return search("searchForStudiesJSON", QueryRetrieveLevel2.STUDY,
                null, null, STUDY_FIELDS, Output.JSON);
    }

    @GET
    @Path("/series")
    @Produces("multipart/related;type=application/dicom+xml")
    public Response searchForSeriesXML() throws Exception {
        return search("searchForSeriesXML", QueryRetrieveLevel2.SERIES,
                null, null, STUDY_SERIES_FIELDS, Output.DICOM_XML);
    }

    @GET
    @Path("/series")
    @Produces("application/json")
    public Response searchForSeriesJSON() throws Exception {
        return search("searchForSeriesJSON", QueryRetrieveLevel2.SERIES,
                null, null, STUDY_SERIES_FIELDS, Output.JSON);
    }

    @GET
    @Path("/studies/{StudyInstanceUID}/series")
    @Produces("multipart/related;type=application/dicom+xml")
    public Response searchForSeriesOfStudyXML(
            @PathParam("StudyInstanceUID") String studyInstanceUID) throws Exception {
        return search("searchForSeriesOfStudyXML", QueryRetrieveLevel2.SERIES,
                studyInstanceUID, null, SERIES_FIELDS, Output.DICOM_XML);
    }

    @GET
    @Path("/studies/{StudyInstanceUID}/series")
    @Produces("application/json")
    public Response searchForSeriesOfStudyJSON(
            @PathParam("StudyInstanceUID") String studyInstanceUID) throws Exception {
        return search("searchForSeriesOfStudyJSON", QueryRetrieveLevel2.SERIES,
                studyInstanceUID, null, SERIES_FIELDS, Output.JSON);
    }

    @GET
    @Path("/instances")
    @Produces("multipart/related;type=application/dicom+xml")
    public Response searchForInstancesXML() throws Exception {
        return search("searchForInstancesXML", QueryRetrieveLevel2.IMAGE,
                null, null, STUDY_SERIES_INSTANCE_FIELDS, Output.DICOM_XML);
    }

    @GET
    @Path("/instances")
    @Produces("application/json")
    public Response searchForInstancesJSON() throws Exception {
        return search("searchForInstancesJSON", QueryRetrieveLevel2.IMAGE,
                null, null, STUDY_SERIES_INSTANCE_FIELDS, Output.JSON);
    }

    @GET
    @Path("/studies/{StudyInstanceUID}/instances")
    @Produces("multipart/related;type=application/dicom+xml")
    public Response searchForInstancesOfStudyXML(
            @PathParam("StudyInstanceUID") String studyInstanceUID) throws Exception {
        return search("searchForInstancesOfStudyXML", QueryRetrieveLevel2.IMAGE,
                studyInstanceUID, null, SERIES_INSTANCE_FIELDS, Output.DICOM_XML);
    }

    @GET
    @Path("/studies/{StudyInstanceUID}/instances")
    @Produces("application/json")
    public Response searchForInstancesOfStudyJSON(
            @PathParam("StudyInstanceUID") String studyInstanceUID) throws Exception {
        return search("searchForInstancesOfStudyJSON", QueryRetrieveLevel2.IMAGE,
                studyInstanceUID, null, SERIES_INSTANCE_FIELDS, Output.JSON);
    }

    @GET
    @Path("/studies/{StudyInstanceUID}/series/{SeriesInstanceUID}/instances")
    @Produces("multipart/related;type=application/dicom+xml")
    public Response searchForInstancesOfSeriesXML(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @PathParam("SeriesInstanceUID") String seriesInstanceUID) throws Exception {
        return search("searchForInstancesOfSeriesXML", QueryRetrieveLevel2.IMAGE,
                studyInstanceUID, seriesInstanceUID, INSTANCE_FIELDS, Output.DICOM_XML);
    }

    @GET
    @Path("/studies/{StudyInstanceUID}/series/{SeriesInstanceUID}/instances")
    @Produces("application/json")
    public Response searchForInstancesOfSeriesJSON(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @PathParam("SeriesInstanceUID") String seriesInstanceUID) throws Exception {
        return search("searchForInstancesOfSeriesJSON", QueryRetrieveLevel2.IMAGE,
                studyInstanceUID, seriesInstanceUID, INSTANCE_FIELDS, Output.JSON);
    }

    private Response search(String method, QueryRetrieveLevel2 qrlevel,
                            String studyInstanceUID, String seriesInstanceUID, int[] includetags, Output output)
            throws Exception {
        QueryAttributes queryAttrs = new QueryAttributes(uriInfo);
        QueryContext ctx = newQueryContext(queryAttrs, studyInstanceUID, seriesInstanceUID, includetags);
        Query query = service.createQuery(qrlevel, ctx);
        try {
            query.initQuery();
            Response.Status status = Response.Status.OK;
            int maxResults = ctx.getArchiveAEExtension().qidoMaxNumberOfResults();
            int offsetInt = parseInt(offset);
            int limitInt = parseInt(limit);
            if (maxResults > 0 && (limitInt == 0 || limitInt >  maxResults)) {
                int numResults = (int) (query.count() - offsetInt);
                if (numResults == 0)
                    return Response.ok().build();

                if (numResults > maxResults) {
                    limitInt = maxResults;
                    status = Response.Status.PARTIAL_CONTENT;
                }
            }
            if (offsetInt > 0)
                query.offset(offsetInt);

            if (limitInt > 0)
                query.limit(limitInt);

            query.orderBy(queryAttrs.getOrderSpecifiers(qrlevel));

            query.executeQuery();
            if (!query.hasMoreMatches())
                return Response.ok().build();

            return Response.status(status).entity(output.entity(this, method, query, qrlevel)).build();
        } finally {
            query.close();
        }
    }

    private QueryContext newQueryContext(QueryAttributes queryAttrs, String studyInstanceUID,
                                         String seriesInstanceUID, int[] includetags) {
        QueryContext ctx = service.newQueryContextQIDO(getApplicationEntity(), Boolean.parseBoolean(fuzzymatching));
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
        ApplicationEntity ae = device.getApplicationEntity(aet);
        if (ae == null || !ae.isInstalled())
            throw new WebApplicationException(
                    "No such Application Entity: " + aet,
                    Response.Status.SERVICE_UNAVAILABLE);
        return ae;
    }

    public static class QueryAttributes {
        private static final OrderSpecifier<?>[] EMPTY_ORDER_SPECIFIERS = new OrderSpecifier<?>[]{};
        private final Attributes keys = new Attributes();
        private boolean includeAll;
        private final ArrayList<OrderByTag> orderByTags = new ArrayList<>();
        private boolean orderByPatientName;

        public QueryAttributes(UriInfo info) {
            MultivaluedMap<String, String> map = info.getQueryParameters();
            for (Map.Entry<String, List<String>> entry : map.entrySet()) {
                String key = entry.getKey();
                if (key.equals("includefield"))
                    addIncludeTag(entry.getValue());
                else if (key.equals("orderby"))
                    addOrderByTag(entry.getValue());
                else if (!key.equals("offset") && !key.equals("limit") && !key.equals("fuzzymatching"))
                    addQueryKey(key, entry.getValue());
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
                        int[] tagPath = parseTagPath(field);
                        int tag = tagPath[tagPath.length-1];
                        nestedKeys(tagPath).setNull(tag, DICT.vrOf(tag));
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
                        int tag = parseTag(desc ? field.substring(1) : field);
                        orderByTags.add(new OrderByTag(tag, desc ? Order.DESC : Order.ASC));
                        if (tag == Tag.PatientName)
                            orderByPatientName = true;
                    }
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("orderby=" + s);
                }
            }
        }

        public OrderSpecifier<?>[] getOrderSpecifiers(QueryRetrieveLevel2 qrlevel) {
            if (orderByTags.isEmpty())
                return EMPTY_ORDER_SPECIFIERS;
            ArrayList<OrderSpecifier<?>> list = new ArrayList<>(orderByTags.size());
            for (OrderByTag orderByTag : orderByTags)
                QueryBuilder.addOrderSpecifier(qrlevel, orderByTag.tag, orderByTag.order, list);
            return list.toArray(EMPTY_ORDER_SPECIFIERS);
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
                int[] tagPath = parseTagPath(attrPath);
                int tag = tagPath[tagPath.length-1];
                nestedKeys(tagPath).setString(tag, DICT.vrOf(tag),
                        values.toArray(new String[values.size()]));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(attrPath + "=" + values.get(0));
            }
        }

        private Attributes nestedKeys(int[] tags) {
            Attributes item = keys;
            for (int i = 0; i < tags.length-1; i++) {
                int tag = tags[i];
                Sequence sq = item.getSequence(tag);
                if (sq == null)
                    sq = item.newSequence(tag, 1);
                if (sq.isEmpty())
                    sq.add(new Attributes());
                item = sq.get(0);
            }
            return item;
        }

        private static int[] parseTagPath(String attrPath) {
            return parseTagPath(StringUtils.split(attrPath, '.'));
        }

        private static int[] parseTagPath(String[] attrPath) {
            int[] tags = new int[attrPath.length];
            for (int i = 0; i < tags.length; i++)
                tags[i] = parseTag(attrPath[i]);
            return tags;
        }

        private static int parseTag(String tagOrKeyword) {
            try {
                return Integer.parseInt(tagOrKeyword, 16);
            } catch (IllegalArgumentException e) {
                int tag = DICT.tagForKeyword(tagOrKeyword);
                if (tag == -1)
                    throw new IllegalArgumentException(tagOrKeyword);
                return tag;
            }
        }
    }

    private enum Output {
        DICOM_XML {
            @Override
            Object entity(QidoRS service, String method, Query query, QueryRetrieveLevel2 qrlevel) {
                return service.writeXML(method, query, qrlevel);
            }
        },
        JSON {
            @Override
            Object entity(QidoRS service, String method, Query query, QueryRetrieveLevel2 qrlevel) {
                return service.writeJSON(method, query, qrlevel);
            }
        };

        abstract Object entity(QidoRS service, String method, Query query, QueryRetrieveLevel2 qrlevel);
    }

    private Object writeXML(String method, Query query, QueryRetrieveLevel2 qrlevel) {
        MultipartRelatedOutput output = new MultipartRelatedOutput();
        int count = 0;
        while (query.hasMoreMatches()) {
            Attributes tmp = query.nextMatch();
            if (tmp == null)
                continue;

            final Attributes match = adjust(tmp, qrlevel, query);
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

    private Object writeJSON(String method, Query query, QueryRetrieveLevel2 qrlevel) {
        final ArrayList<Attributes> matches = new ArrayList<>();
        int count = 0;
        while (query.hasMoreMatches()) {
            Attributes tmp = query.nextMatch();
            if (tmp == null)
                continue;
            Attributes match = adjust(tmp, qrlevel, query);
            LOG.debug("{}: Match #{}:\n{}", method, ++count, match);
            matches.add(match);
        }
        LOG.info("{}: {} Matches", method, count);
        return new StreamingOutput() {
            @Override
            public void write(OutputStream out) throws IOException {
                try {
                    JsonGenerator gen = Json.createGenerator(out);
                    JSONWriter writer = new JSONWriter(gen);
                    gen.writeStartArray();
                    for (int i = 0, n=matches.size(); i < n; i++) {
                        Attributes match = matches.get(i);
                        writer.write(match);
                    }
                    gen.writeEnd();
                    gen.flush();
                } catch (Exception e) {
                    throw new WebApplicationException(e);
                }
            }
        };
    }

    private Attributes adjust(Attributes match, QueryRetrieveLevel2 qrlevel, Query query) {
        match = query.adjust(match);
        match.setString(Tag.RetrieveURL, VR.UR, retrieveURL(match, qrlevel));
        return match;
    }

    private String retrieveURL(Attributes match, QueryRetrieveLevel2 qrlevel) {
        StringBuilder sb = new StringBuilder(256);
        sb.append(uriInfo.getBaseUri())
                .append("aets/")
                .append(aet)
                .append("/rs/studies/")
                .append(match.getString(Tag.StudyInstanceUID));

        if (qrlevel == QueryRetrieveLevel2.STUDY)
            return sb.toString();

        sb.append("/series/")
                .append(match.getString(Tag.SeriesInstanceUID));

        if (qrlevel == QueryRetrieveLevel2.SERIES)
            return sb.toString();

        sb.append("/instances/")
                .append(match.getString(Tag.SOPInstanceUID));
        return sb.toString();
    }
}
