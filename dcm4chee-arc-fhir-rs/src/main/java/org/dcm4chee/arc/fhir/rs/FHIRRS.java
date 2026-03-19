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
 * Java(TM), hosted at https://github.com/dcm4che.
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
 */

package org.dcm4chee.arc.fhir.rs;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.stream.JsonGenerator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.dcm4che3.data.*;
import org.dcm4che3.dict.archive.PrivateTag;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4che3.util.DateUtils;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.fhir.util.FHIRBuilder;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.patient.PatientService;
import org.dcm4chee.arc.query.Query;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.query.QueryService;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.resteasy.util.MediaTypeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@protonmail.com>
 * @since Jan 2026
 */
@RequestScoped
@Path("aets/{AETitle}/fhir")
public class FHIRRS {
    private static final Logger LOG = LoggerFactory.getLogger(FHIRRS.class);
    private static final long MILLIS_OF_DAY = 24 * 60 * 60000L;

    @Inject
    private QueryService service;

    @Inject
    private PatientService patientService;

    @Context
    private HttpServletRequest request;

    @Context
    private UriInfo uriInfo;

    @Context
    private HttpHeaders headers;

    @Inject
    private Device device;
    private ArchiveDeviceExtension arcdev;

    @PathParam("AETitle")
    private String aet;

    @GET
    @Path("/Patient/{id}")
    public Response getPatient(@PathParam("id") String id) {
        logRequest(null);
        arcdev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        ArchiveAEExtension arcAE = getArchiveAE();
        if (arcAE == null)
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("No such Application Entity: " + aet)
                    .type(MediaType.TEXT_PLAIN_TYPE)
                    .build();
        MultivaluedMap<String, String> params = uriInfo.getQueryParameters();
        MediaType mediaType = selectMediaType(params);
        if (mediaType == null) {
            return Response.notAcceptable(
                            Variant.mediaTypes(
                                            MediaTypes.APPLICATION_FHIR_JSON_TYPE,
                                            MediaTypes.APPLICATION_FHIR_XML_TYPE)
                                    .build())
                    .build();
        }
        try {
            Long.parseUnsignedLong(id);
            QueryContext ctx = newQueryContext("fhirReadPatient", id, arcAE);
            try (Query query = service.createPatientQuery(ctx)) {
                LOG.debug("Query for Patient/" + id);
                query.executeQuery(1);
                if (query.hasMoreMatches()) {
                    return Response.ok(
                                    isJSON(mediaType)
                                            ? writeJSON(query.nextMatch(), id)
                                            : writeXML(query.nextMatch(), id),
                                    mediaType)
                            .build();
                }
            } catch (Exception e) {
                return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
            }
        } catch (NumberFormatException e) {
        }
        return Response.status(Response.Status.NOT_FOUND)
                .entity("Resource Patient/" + id + " not found")
                .type(MediaType.TEXT_PLAIN_TYPE)
                .build();
    }

    @GET
    @NoCache
    @Path("/Patient")
    public Response searchForPatients() {
        return searchForPatients(null);
    }

    @POST
    @NoCache
    @Path("/Patient/_search")
    public Response searchForPatients(MultivaluedMap<String, String> params) {
        logRequest(params);
        arcdev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        ArchiveAEExtension arcAE = getArchiveAE();
        if (arcAE == null)
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("No such Application Entity: " + aet)
                    .type(MediaType.TEXT_PLAIN_TYPE)
                    .build();
        if (params == null) {
            params = uriInfo.getQueryParameters();
        } else {
            params.putAll(uriInfo.getQueryParameters());
        }
        int maxresults = 0;
        String maxresultsStr = params.getFirst("_maxresults");
        if (maxresultsStr != null) {
            try {
                maxresults = Integer.parseInt(maxresultsStr);
            } catch (NumberFormatException e) {}
            if (maxresults <= 0) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("Invalid _maxresults=" + maxresultsStr)
                        .type(MediaType.TEXT_PLAIN_TYPE)
                        .build();
            }
        }
        int qidoMaxNumberOfResults = arcAE.qidoMaxNumberOfResults();
        if (qidoMaxNumberOfResults > 0 || maxresults > qidoMaxNumberOfResults) {
            maxresults = qidoMaxNumberOfResults;
        }
        MediaType mediaType = selectMediaType(params);
        if (mediaType == null) {
            return Response.notAcceptable(
                            Variant.mediaTypes(
                                            MediaTypes.APPLICATION_FHIR_JSON_TYPE,
                                            MediaTypes.APPLICATION_FHIR_XML_TYPE)
                                    .build())
                    .build();
        }
        QueryContext ctx = newQueryContext("fhirSearchPatients" + request.getMethod(), params, arcAE);
        OffsetDateTime now = OffsetDateTime.now();
        try (Query query = service.createPatientQuery(ctx)) {
            LOG.debug("Count matching Patients");
            long count = query.fetchCount();
            int fetchSize = arcAE.getArchiveDeviceExtension().getQueryFetchSize();
            LOG.debug("Query for matching Patients");
            if (maxresults > 0 && maxresults < count) {
                query.executeQuery(fetchSize, 0, maxresults);
            } else {
                query.executeQuery(fetchSize);
            }
            return Response.ok(
                            isJSON(mediaType)
                                    ? writeJSON(now, count, query)
                                    : writeXML(now, count, query), mediaType)
                    .build();
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private byte[] writeJSON(Attributes attrs, String id) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (JsonGenerator gen = Json.createGenerator(baos)) {
            gen.writeStartObject();
            new FHIRBuilder.JSON(request, arcdev, gen).writePatient(attrs, id);
            gen.writeEnd();
        }
        return baos.toByteArray();
    }

    private byte[] writeJSON(OffsetDateTime now, long count, Query query) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (JsonGenerator gen = Json.createGenerator(baos)) {
            FHIRBuilder.JSON fhirJson = new FHIRBuilder.JSON(request, arcdev, gen);
            fhirJson.writePatientBundle(now, count, query);
        }
        return baos.toByteArray();
    }

    private byte[] writeXML(Attributes attrs, String id) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newFactory();
        XMLStreamWriter writer = xmlOutputFactory.createXMLStreamWriter(baos, "UTF-8");
        writer.writeStartDocument("UTF-8", "1.0");
        new FHIRBuilder.XML(request, arcdev, writer).writePatient(attrs, id);
        writer.writeEndDocument();
        writer.close();
        return baos.toByteArray();
    }

    private byte[] writeXML(OffsetDateTime now, long count, Query query) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newFactory();
        XMLStreamWriter writer = xmlOutputFactory.createXMLStreamWriter(baos, "UTF-8");
        FHIRBuilder.XML fhirXml = new FHIRBuilder.XML(request, arcdev, writer);
        fhirXml.writePatientBundle(now, count, query);
        writer.close();
        return baos.toByteArray();
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

    private ArchiveAEExtension getArchiveAE() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        return ae == null || !ae.isInstalled() ? null : ae.getAEExtension(ArchiveAEExtension.class);
    }

    private MediaType selectMediaType(MultivaluedMap<String, String> params) {
        String format = params.getFirst("_format");
        if (format != null) {
            switch (format) {
                case "json":
                case "application/json":
                case "application/fhir+json":
                    return MediaTypes.APPLICATION_FHIR_JSON_TYPE;
                case "xml":
                case "text/xml":
                case "application/xml":
                case "application/xml+json":
                    return MediaTypes.APPLICATION_FHIR_XML_TYPE;
            }
        }
        List<MediaType> desired = new ArrayList<>(headers.getAcceptableMediaTypes());
        MediaTypeHelper.sortByWeight(desired);
        MediaType[] provided = {
                MediaTypes.APPLICATION_FHIR_JSON_TYPE,
                MediaTypes.APPLICATION_FHIR_XML_TYPE,
                MediaType.APPLICATION_JSON_TYPE,
                MediaType.APPLICATION_XML_TYPE,
                MediaType.TEXT_XML_TYPE
        };

        for (MediaType desire : desired) {
            for (MediaType provide : provided) {
                if (provide.isCompatible(desire))
                    return provide;
            }
        }
        return null;
    }

    private static boolean isJSON(MediaType mediaType) {
        return mediaType.getSubtype().endsWith("json");
    }

    private QueryContext newQueryContext(String method, MultivaluedMap<String, String> params, ArchiveAEExtension arcae) {
        org.dcm4chee.arc.query.util.QueryParam queryParam = new org.dcm4chee.arc.query.util.QueryParam(arcae);
        QueryContext ctx = service.newQueryContextQIDO(
                HttpServletRequestInfo.valueOf(request), method, aet, arcae.getApplicationEntity(), queryParam);
        ctx.setQueryRetrieveLevel(QueryRetrieveLevel2.PATIENT);
        setPatientIDs(ctx, params.getFirst("identifier"));
        ctx.setQueryKeys(toKeys(params));
        ctx.setReturnPrivate(true);
        return ctx;
    }

    private QueryContext newQueryContext(String method, String id, ArchiveAEExtension arcae) {
        org.dcm4chee.arc.query.util.QueryParam queryParam = new org.dcm4chee.arc.query.util.QueryParam(arcae);
        QueryContext ctx = service.newQueryContextQIDO(
                HttpServletRequestInfo.valueOf(request), method, aet, arcae.getApplicationEntity(), queryParam);
        ctx.setQueryRetrieveLevel(QueryRetrieveLevel2.PATIENT);
        Attributes keys = new Attributes(1);
        setLogicalPatientID(keys, id);
        ctx.setQueryKeys(keys);
        ctx.setReturnPrivate(true);
        return ctx;
    }

    private void setPatientIDs(QueryContext ctx, String identifier) {
        if (identifier != null) {
            String[] ids = StringUtils.split(identifier, ',');
            List<IDWithIssuer> patIDs = new ArrayList<>(ids.length);
            for (String id : ids) {
                String[] split = StringUtils.split(id.trim(), '|');
                Issuer issuer = null;
                int codeIndex = 0;
                if (split.length > 1) {
                    issuer = arcdev.fhirSystem2IssuerOfPatientID(split[0]);
                    codeIndex++;
                }
                if (split.length > codeIndex) {
                    if (!split[codeIndex].isEmpty()) {
                        patIDs.add(new IDWithIssuer(split[0], issuer));
                    } else if (issuer != null) {
                        ctx.setIssuerOfPatientID(issuer);
                    }
                }
            }
            if (!patIDs.isEmpty()) {
                ctx.setPatientIDs(patIDs.toArray(new IDWithIssuer[patIDs.size()]));
            }
        }
    }

    private Attributes toKeys(MultivaluedMap<String, String> params) {
        Attributes keys = new Attributes(4);
        setPatientName(keys, params.getFirst("family"), params.getFirst("given"));
        setPatientBirthDate(keys, params.get("birthdate"));
        setPatientSex(keys, params.getFirst("gender"));
        setLogicalPatientID(keys, params.getFirst("_id"));
        return keys;
    }

    private void setPatientName(Attributes keys, String family, String given) {
        if (family == null && given == null) return;
        StringBuilder sb = new StringBuilder();
        String exactFamily = exactMatch(family);
        String exactGiven = exactMatch(given);
        if (exactFamily != null) {
            if (given == null) {
                sb.append(exactFamily);
            } else if (exactGiven != null) {
                sb.append(exactFamily).append("^").append(exactGiven);
            } else {
                sb.append(exactFamily.toUpperCase()).append("^")
                        .append(given.toUpperCase()).append('*');
            }
        } else if (family != null) {
            sb.append(family.toUpperCase()).append('*');
            if (given != null) {
                sb.append('^');
                if (exactGiven != null) {
                    sb.append(exactGiven.toUpperCase());
                } else {
                    sb.append(given.toUpperCase()).append('*');
                }
            }
        } else { // given != null
            sb.append('*').append('^');
            if (exactGiven != null) {
                sb.append(exactGiven);
            } else {
                sb.append(given.toUpperCase()).append('*');
            }
        }
        keys.setString(Tag.PatientName, VR.PN, sb.toString());
    }

    private static String exactMatch(String s) {
        return s != null && s.endsWith(":exact") ? s.substring(0, s.length() - 6) : null;
    }

    private static void setPatientBirthDate(Attributes keys, List<String> list) {
        if (list == null) return;
        Date start = null;
        Date end = null;
        for (String s : list) {
            try {
                boolean noPrefix = Character.isDigit(s.charAt(0));
                Date value = DateUtils.parseDA(null, noPrefix ? s : s.substring(2));
                if (noPrefix || s.startsWith("eq")) {
                    keys.setDate(Tag.PatientBirthDate, VR.PN, value);
                    return;
                }
                boolean gt = s.startsWith("gt");
                if (gt) {
                    value = new Date(value.getTime() + MILLIS_OF_DAY);
                }
                if (gt || s.startsWith("ge")) {
                    if (start == null || start.before(value))
                        start = value;
                } else {
                    boolean lt = s.startsWith("lt");
                    if (lt) {
                        value = new Date(value.getTime() - MILLIS_OF_DAY);
                    }
                    if (lt || s.startsWith("le")) {
                        if (end == null || end.after(value))
                            end = value;
                    }
                }
            } catch (IllegalArgumentException ignore) {
            }
        }
        if (start != null || end != null) {
            keys.setDateRange(Tag.PatientBirthDate, VR.PN, new DateRange(start, end));
        }
    }

    private static void setPatientSex(Attributes keys, String gender) {
        if (gender != null) {
            String value = switch (gender) {
                case "female" -> "F";
                case "male" -> "M";
                case "other" -> "O";
                default -> null;
            };
            if (value != null) {
                keys.setString(Tag.PatientSex, VR.CS, value);
            }
        }
    }

    private void setLogicalPatientID(Attributes keys, String id) {
        if (id != null) {
            keys.setString(PrivateTag.PrivateCreator, PrivateTag.LogicalPatientID, VR.LO, id);
        }
    }

    private void logRequest(MultivaluedMap<String, String> params) {
        StringBuilder sb = new StringBuilder(128)
                .append("Process ")
                .append(request.getMethod())
                .append(' ')
                .append(request.getRequestURI());
        String queryString = request.getQueryString();
        if (queryString != null) {
            sb.append('?').append(queryString);
        }
        if (params != null) {
            sb.append(' ');
            params.forEach((name, values) -> values.forEach(
                    value -> sb.append(name).append('=').append(value).append('&')));
        }
        sb.setLength(sb.length() - 1);
        sb.append(" Accept: ")
                .append(headers.getHeaderString(HttpHeaders.ACCEPT))
                .append(" from ")
                .append(request.getRemoteUser())
                .append('@')
                .append(request.getRemoteHost());
        LOG.info(sb.toString());
    }

}
