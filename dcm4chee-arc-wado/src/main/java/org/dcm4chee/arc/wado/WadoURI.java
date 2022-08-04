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
 * Portions created by the Initial Developer are Copyright (C) 2015-2017
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

package org.dcm4chee.arc.wado;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.IWebApplicationCache;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.image.ICCProfile;
import org.dcm4che3.imageio.plugins.dcm.DicomImageReadParam;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.net.*;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.keycloak.KeycloakContext;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveService;
import org.dcm4chee.arc.retrieve.RetrieveWADO;
import org.dcm4chee.arc.retrieve.stream.DicomObjectOutput;
import org.dcm4chee.arc.store.InstanceLocations;
import org.dcm4chee.arc.validation.constraints.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.CompletionCallback;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.*;
import java.awt.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.util.List;
import java.util.*;
import java.util.stream.Stream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2015
 */
@RequestScoped
@Path("aets/{AETitle}/wado")
@NotAllowedIfEquals(paramName = "contentType", paramValue = MediaTypes.APPLICATION_DICOM,
        notAllowed = { "annotation", "rows", "columns", "region", "windowCenter",
                "windowWidth", "frameNumber", "presentationUID", "presentationSeriesUID" })
@NotAllowedIfNotEquals(paramName = "contentType", paramValue = MediaTypes.APPLICATION_DICOM,
        notAllowed = { "anonymize", "transferSyntax" })
@RequiredIfPresent.List({
    @RequiredIfPresent(paramName = "windowCenter", required = "windowWidth"),
    @RequiredIfPresent(paramName = "windowWidth", required = "windowCenter"),
    @RequiredIfPresent(paramName = "presentationUID", required = "presentationSeriesUID")
})
@NotAllowedIfNotPresent(paramName = "presentationUID", notAllowed = "presentationSeriesUID")
@NotAllowedIfPresent(paramName = "presentationUID", notAllowed = { "windowWidth", "windowCenter" })
public class WadoURI {

    private static final Logger LOG = LoggerFactory.getLogger(WadoURI.class);
    private static final String SUPER_USER_ROLE = "super-user-role";

    @Inject
    private RetrieveService service;

    @Context
    private HttpServletRequest request;

    @Inject
    private IWebApplicationCache iWebAppCache;

    @Inject
    private Device device;

    @Inject @RetrieveWADO
    private Event<RetrieveContext> retrieveWado;

    @Context
    private Request req;

    @PathParam("AETitle")
    private String aet;

    @QueryParam("requestType")
    @NotNull
    @Pattern(regexp = "WADO")
    private String requestType;

    @QueryParam("studyUID")
    @NotNull
    private String studyUID;

    @QueryParam("seriesUID")
    private String seriesUID;

    @QueryParam("objectUID")
    private String objectUID;

    @QueryParam("contentType")
    @ValidValueOf(type = ContentTypes.class)
    private String contentType;

    @QueryParam("charset")
    private String charset;

    @QueryParam("anonymize")
    @Pattern(regexp = "yes")
    private String anonymize;

    @QueryParam("annotation")
    @Pattern(regexp = "patient|technique|patient,technique|technique,patient")
    private String annotation;

    @QueryParam("rows")
    @Pattern(regexp = "[1-9]\\d{0,4}")
    private String rows;

    @QueryParam("columns")
    @Pattern(regexp = "[1-9]\\d{0,4}")
    private String columns;

    @QueryParam("region")
    @ValidValueOf(type = Region.class)
    private String region;

    @QueryParam("windowCenter")
    @Digits(integer = 5, fraction = 5)
    private String windowCenter;

    @QueryParam("windowWidth")
    @DecimalMin(value = "1")
    private String windowWidth;

    @QueryParam("frameNumber")
    @Pattern(regexp = "[1-9]\\d{0,4}")
    private String frameNumber;

    @QueryParam("imageQuality")
    @Pattern(regexp = "([1-9]\\d?)|100")
    private String imageQuality;

    @QueryParam("presentationUID")
    private String presentationUID;

    @QueryParam("presentationSeriesUID")
    private String presentationSeriesUID;

    @QueryParam("transferSyntax")
    private String transferSyntax;

    @QueryParam("iccprofile")
    @Pattern(regexp = "no|yes|srgb|adobergb|rommrgb")
    private String iccprofile;

    private Collection<String> acceptableTransferSyntaxes;
    private ContentTypes contentTypes;

    @Override
    public String toString() {
        String requestURI = request.getRequestURI();
        String queryString = request.getQueryString();
        return queryString == null ? requestURI : requestURI + '?' + queryString;
    }

    @GET
    public void get(@Suspended AsyncResponse ar) {
        if (contentType != null)
            contentTypes = new ContentTypes(contentType);
        logRequest();
        ApplicationEntity ae = getApplicationEntity();
        validateAcceptedUserRoles(ae.getAEExtensionNotNull(ArchiveAEExtension.class));
        if (aet.equals(ae.getAETitle()))
            validateWebAppServiceClass();
        try {
            final RetrieveContext ctx = service.newRetrieveContextWADO(HttpServletRequestInfo.valueOf(request), aet, studyUID, seriesUID, objectUID);

            if (request.getHeader(HttpHeaders.IF_MODIFIED_SINCE) == null && request.getHeader(HttpHeaders.IF_UNMODIFIED_SINCE) == null
                    && request.getHeader(HttpHeaders.IF_MATCH) == null && request.getHeader(HttpHeaders.IF_NONE_MATCH) == null) {
                buildResponse(ar, ctx, null);
                return;
            }

            LOG.debug("Query Last Modified date of Instance");
            Date lastModified = service.getLastModified(ctx, ignorePatientUpdates());
            if (lastModified == null) {
                LOG.info("Last Modified date for Study[uid={}] Series[uid={} Instance[uid={}] is unavailable.",
                        studyUID, seriesUID, objectUID);
                throw new WebApplicationException(errResponse("No matches found.", Response.Status.NOT_FOUND));
            }
            LOG.debug("Last Modified date: {}", lastModified);
            Response.ResponseBuilder respBuilder = evaluatePreConditions(lastModified);

            if (respBuilder == null) {
                LOG.debug("Preconditions are not met - build response");
                buildResponse(ar, ctx, lastModified);
            } else {
                Response response = respBuilder.build();
                LOG.debug("Preconditions are met - return status {}", response.getStatus());
                ar.resume(response);
            }
        } catch (Exception e) {
            ar.resume(e);
        }
    }
    private void logRequest() {
        LOG.info("Process {} {} from {}@{}",
                request.getMethod(),
                toString(),
                request.getRemoteUser(),
                request.getRemoteHost());
        LOG.debug(" with HTTPHeaders[{}]", headers());
    }

    private String headers() {
        Enumeration<String> headerNames = request.getHeaderNames();
        StringBuilder header = new StringBuilder();
        boolean multipleHeaders = false;
        while (headerNames.hasMoreElements()) {
            if (multipleHeaders)
                header.append(", ");
            String headerName = headerNames.nextElement();
            header.append(headerName).append(":").append(headerValues(headerName));
            multipleHeaders = true;
        }
        return header.toString();
    }

    private String headerValues(String headerName) {
        Enumeration<String> header = request.getHeaders(headerName);
        StringBuilder headerValues = new StringBuilder();
        boolean multipleValues = false;
        while (header.hasMoreElements()) {
            if (multipleValues)
                headerValues.append(",");
            headerValues.append(header.nextElement());
            multipleValues = true;
        }
        return headerValues.toString();
    }

    private void validateAcceptedUserRoles(ArchiveAEExtension arcAE) {
        KeycloakContext keycloakContext = KeycloakContext.valueOf(request);
        if (keycloakContext.isSecured() && !keycloakContext.isUserInRole(System.getProperty(SUPER_USER_ROLE))) {
            arcAE.getAcceptedUserRoles1()
                    .stream()
                    .filter(keycloakContext::isUserInRole)
                    .findAny()
                    .orElseThrow(() -> new WebApplicationException(errResponse(
                            "Application Entity " + arcAE.getApplicationEntity().getAETitle()
                                    + " does not list role of accessing user",
                            Response.Status.FORBIDDEN)));
        }
    }

    private void validateWebAppServiceClass() {
        device.getWebApplications().stream()
                .filter(webApp -> request.getRequestURI().startsWith(webApp.getServicePath())
                        && Arrays.asList(webApp.getServiceClasses())
                        .contains(WebApplication.ServiceClass.WADO_URI))
                .findFirst()
                .orElseThrow(() -> new WebApplicationException(errResponse(
                        "No Web Application with WADO_URI service class found for Application Entity: " + aet,
                        Response.Status.NOT_FOUND)));
    }

    private void buildResponse(@Suspended AsyncResponse ar, final RetrieveContext ctx, Date lastModified) throws IOException {
        LOG.debug("Query for requested instance");
        if (!service.calculateMatches(ctx)) {
            ArchiveAEExtension arcAE = ctx.getArchiveAEExtension();
            String webAppName = arcAE.fallbackWadoURIWebApplication();
            if (webAppName != null) {
                try {
                    ar.resume(Response.status(arcAE.fallbackWadoURIHttpStatusCode())
                            .location(redirectURI(webAppName))
                            .build());
                    return;
                } catch (Exception e) {
                    LOG.warn("Failed to redirect to {}:\n", webAppName, e);
                }
            }
            throw new WebApplicationException(errResponse("No matches found.", Response.Status.NOT_FOUND));
        }

        List<InstanceLocations> matches = ctx.getMatches();
        int numMatches = matches.size();
        if (numMatches > 1)
            LOG.debug("{} matches found. Return {}. match", numMatches, numMatches >>> 1);
        InstanceLocations inst = matches.get(numMatches >>> 1);
        int frame = frame(inst.getAttributes());
        ObjectType objectType = ObjectType.objectTypeOf(ctx, inst, frame);
        MediaType mimeType = selectMimeType(objectType).orElseThrow(() ->
            new WebApplicationException(errResponse(
                    "Supported Media Types for " + objectType + " not acceptable",
                    Response.Status.NOT_ACCEPTABLE)));

        if (lastModified == null)
            lastModified = service.getLastModifiedFromMatches(ctx, ignorePatientUpdates());

        StreamingOutput entity = entityOf(ctx, inst, objectType, mimeType, frame);
        ar.register((CompletionCallback) throwable -> {
            ctx.getRetrieveService().updateLocations(ctx);
            ctx.setException(throwable);
            retrieveWado.fire(ctx);
        });

        ar.resume(Response.ok(entity, mimeType == MediaTypes.APPLICATION_DICOM_TYPE
                                        ? new MediaType(mimeType.getType(), mimeType.getSubtype(), parameters(inst))
                                        : mimeType)
                .lastModified(lastModified)
                .tag(String.valueOf(lastModified.hashCode()))
                .build());
    }

    private boolean ignorePatientUpdates() {
        return contentTypes != null && contentTypes.ignorePatientUpdates;
    }

    private URI redirectURI(String webAppName) throws ConfigurationException {
        WebApplication webApp = iWebAppCache.findWebApplication(webAppName);
        if (!webApp.containsServiceClass(WebApplication.ServiceClass.WADO_URI)) {
            throw new ConfigurationException("WebApplication: " + webAppName
                    + " does not provide WADO-URI service");
        }
        if (webApp.getDevice().getDeviceName().equals(device.getDeviceName())) {
            throw new ConfigurationException("WebApplication: " + webAppName
                    + " is provided by this Device: " + device.getDeviceName() + " - prevent redirect to itself");
        }
        return URI.create(webApp.getServiceURL(selectConnection(webApp))
                .append('?').append(request.getQueryString()).toString());
    }

    private Connection selectConnection(WebApplication webApp) throws ConfigurationException {
        boolean https = "https:".equalsIgnoreCase(request.getRequestURL().substring(0,6));
        Connection altConn = null;
        for (Connection conn : webApp.getConnections()) {
            if (conn.isInstalled() && (altConn = conn).isTls() == https) {
                return conn;
            }
        }
        if (altConn == null) {
            throw new ConfigurationException(
                    "No installed Network Connection for WebApplication: " + webApp.getApplicationName());
        }
        return altConn;
    }

    private Response.ResponseBuilder evaluatePreConditions(Date lastModified) {
        return req.evaluatePreconditions(new Date((lastModified.getTime() / 1000) * 1000),
                new EntityTag(String.valueOf(lastModified.hashCode())));
    }

    private ApplicationEntity getApplicationEntity() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        if (ae == null || !ae.isInstalled())
            throw new WebApplicationException(errResponse(
                    "No such Application Entity: " + aet,
                    Response.Status.NOT_FOUND));
        return ae;
    }

    private Map<String, String> parameters(InstanceLocations inst) {
        Map<String, String> parameters = new HashMap<>();
        String tsuid = acceptableTransferSyntaxes.iterator().next();
        parameters.put("transfer-syntax", tsuid.equals("*") ? inst.getLocations().get(0).getTransferSyntaxUID() : tsuid);
        return parameters;
    }

    private StreamingOutput entityOf(RetrieveContext ctx, InstanceLocations inst, ObjectType objectType,
                            MediaType mimeType, int frame)
            throws IOException {
        if (mimeType == MediaTypes.APPLICATION_DICOM_TYPE)
                return new DicomObjectOutput(
                        ctx, inst, (acceptableTransferSyntaxes = acceptableTransferSyntaxes(objectType, inst)));

        switch (objectType) {
            case UncompressedSingleFrameImage:
            case CompressedSingleFrameImage:
            case UncompressedMultiFrameImage:
            case CompressedMultiFrameImage:
                return renderImage(ctx, inst, mimeType, frame);
            case EncapsulatedCDA:
                return decapsulateCDA(service.openDicomInputStream(ctx, inst),
                        ctx.getArchiveAEExtension().wadoCDA2HtmlTemplateURI());
            case EncapsulatedPDF:
            case EncapsulatedSTL:
            case EncapsulatedOBJ:
            case EncapsulatedMTL:
            case EncapsulatedGenozip:
                return decapsulateDocument(service.openDicomInputStream(ctx, inst));
            case MPEG2Video:
            case MPEG4Video:
                return decapsulateVideo(service.openDicomInputStream(ctx, inst));
            case SRDocument:
                return new DicomXSLTOutput(ctx, inst, mimeType, wadoURL());
        }
        throw new AssertionError("objectType: " + objectType);
    }

    private RenderedImageOutput renderImage(RetrieveContext ctx, InstanceLocations inst,
                                            MediaType mimeType, int frame) throws IOException {
        Attributes attrs = inst.getAttributes();
        DicomImageReadParam readParam = new DicomImageReadParam();
        if (windowCenter != null && windowWidth != null) {
            readParam.setWindowCenter(Float.parseFloat(windowCenter));
            readParam.setWindowWidth(Float.parseFloat(windowWidth));
        }
        if (region != null)
            readParam.setSourceRegion(new Region(region).getSourceRegion(
                    attrs.getInt(Tag.Rows, 1),
                    attrs.getInt(Tag.Columns, 1)));
        if (presentationUID != null)
            readParam.setPresentationState(retrievePresentationState());

        readParam.setIgnorePresentationLUTShape(ctx.getArchiveAEExtension().isWadoIgnorePresentationLUTShape());
        return new RenderedImageOutput(ctx, inst, readParam, parseInt(rows), parseInt(columns), mimeType, imageQuality,
                iccProfile(mimeType), frame);
    }

    private ICCProfile.Option iccProfile(MediaType mimeType) {
        if (iccprofile == null)
            return ICCProfile.Option.none;

        ICCProfile.Option iccProfile = ICCProfile.Option.valueOf(iccprofile);
        if (iccProfile != ICCProfile.Option.no
                && !MediaTypes.equalsIgnoreParameters(mimeType, MediaTypes.IMAGE_JPEG_TYPE)) {
            throw new WebApplicationException(errResponseAsTextPlain(
                    "Cannot embed ICC profile into " + mimeType,
                    Response.Status.BAD_REQUEST));
        }
        return iccProfile;
    }

    private int frame(Attributes attrs) {
        int numFrames = attrs.getInt(Tag.NumberOfFrames, 1);
        if (frameNumber == null)
            return numFrames > 1 ? 0 : 1;

        int n = Integer.parseInt(frameNumber);
        if (n > numFrames)
                throw new WebApplicationException(errResponse(
                        "frameNumber=" + frameNumber + " exceeds number of frames of specified resource",
                        Response.Status.NOT_FOUND));
        return n;
    }

    private static int parseInt(String s) {
        return s != null ? Integer.parseInt(s) : 0;
    }

    private String wadoURL() {
        return device.getDeviceExtension(ArchiveDeviceExtension.class).remapRetrieveURL(request).toString();
    }

    private StreamingOutput decapsulateVideo(DicomInputStream dis) throws IOException {
        dis.readDataset(-1, Tag.PixelData);
        if (dis.tag() != Tag.PixelData || dis.length() != -1
                || !dis.readItemHeader() || dis.length() != 0
                || !dis.readItemHeader())
            throw new IOException("No or incorrect encapsulated video stream in requested object");

        return new StreamCopyOutput(dis, dis.length());
    }

    private StreamingOutput decapsulateCDA(DicomInputStream dis, String templateURI) throws IOException {
        seekEncapsulatedDocument(dis);
        return templateURI != null
                ? new CDAOutput(dis, dis.length(), templateURI)
                : new StreamCopyOutput(dis, dis.length());
    }

    private StreamingOutput decapsulateDocument(DicomInputStream dis) throws IOException {
        seekEncapsulatedDocument(dis);
        return new StreamCopyOutput(dis, dis.length());
    }

    private void seekEncapsulatedDocument(DicomInputStream dis) throws IOException {
        dis.readDataset(-1, Tag.EncapsulatedDocument);
        if (dis.tag() != Tag.EncapsulatedDocument)
            throw new IOException("No encapsulated document in requested object");
    }


    private Attributes retrievePresentationState() throws IOException {
        RetrieveContext ctx = service.newRetrieveContextWADO(
                HttpServletRequestInfo.valueOf(request), aet, studyUID, presentationSeriesUID, presentationUID);
        if (!service.calculateMatches(ctx))
            throw new WebApplicationException(errResponse(
                    "Specified Presentation State does not exist", Response.Status.NOT_FOUND));

        Collection<InstanceLocations> matches = ctx.getMatches();
        if (matches.size() > 1)
            throw new WebApplicationException(errResponse(
                    "More than one matching Presentation State found", Response.Status.BAD_REQUEST));

        InstanceLocations inst = matches.iterator().next();
        try (DicomInputStream dis = service.openDicomInputStream(ctx, inst)) {
            return dis.readDataset(-1, -1);
        }
    }

    private Optional<MediaType> selectMimeType(ObjectType objectType) {
        if (contentType == null)
            return Optional.of(objectType.getDefaultMimeType());

        return Stream.of(contentTypes.values)
                .map(objectType::getCompatibleMimeType)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    private Collection<String> acceptableTransferSyntaxes(ObjectType objectType, InstanceLocations inst) {
        Collection<String> tsuids = new ArrayList<>(transferSyntax == null
                ? Collections.singleton(UID.ExplicitVRLittleEndian)
                : Arrays.asList(StringUtils.split(transferSyntax, ',')));
        tsuids.removeIf(tsuid -> !transcodeableTo(objectType, inst, tsuid));
        if (tsuids.isEmpty())
            throw new WebApplicationException(errResponse(
                    "Supported Transfer Syntaxes for " + objectType + " not acceptable",
                    Response.Status.NOT_ACCEPTABLE));
        return tsuids;
    }

    private static boolean transcodeableTo(ObjectType objectType, InstanceLocations inst, String accepted) {
        return accepted.equals("*")
                || !objectType.isVideo() && isUncompressed(accepted)
                || inst.getLocations().stream().anyMatch(l -> accepted.equals(l.getTransferSyntaxUID()));
    }

    private static boolean isUncompressed(String tsuid) {
        switch (tsuid) {
            case UID.ImplicitVRLittleEndian:
            case UID.ExplicitVRLittleEndian:
            case UID.ExplicitVRBigEndian:
            case UID.DeflatedExplicitVRLittleEndian:
                return true;
        }
        return false;
    }

    public static final class ContentTypes {
        final MediaType[] values;
        final boolean ignorePatientUpdates;

        public ContentTypes(String s) {
            String[] ss = StringUtils.split(s, ',');
            values = new MediaType[ss.length];
            boolean ignorePatientUpdates = true;
            for (int i = 0; i < ss.length; i++) {
                if (!ignorePatientUpdates(values[i] = MediaType.valueOf(ss[i])))
                    ignorePatientUpdates = false;
            }
            this.ignorePatientUpdates = ignorePatientUpdates;
        }
    }

    static boolean ignorePatientUpdates(MediaType mediaType) {
        return mediaType.getType().equalsIgnoreCase("image")
                || mediaType.getType().equalsIgnoreCase("video")
                || mediaType.getSubtype().equalsIgnoreCase("pdf");
    }

    public static final class Region {
        private final float left;
        private final float top;
        private final float right;
        private final float bottom;

        public Region(String s) {
            String[] ss = StringUtils.split(s, ',');
            if (ss.length != 4)
                throw new IllegalArgumentException(s);
            left = Float.parseFloat(ss[0]);
            top = Float.parseFloat(ss[1]);
            right = Float.parseFloat(ss[2]);
            bottom = Float.parseFloat(ss[3]);
            if (left < 0. || right > 1. || top < 0. || bottom > 1.
                    || left >= right || top >= bottom)
                throw new IllegalArgumentException(s);
        }

        public Rectangle getSourceRegion(int rows, int columns) {
            return new Rectangle(
                    (int) (left * columns),
                    (int) (top * rows),
                    (int) Math.ceil((right - left) * columns),
                    (int) Math.ceil((bottom - top) * rows));
        }
    }

    private static Response errResponse(String errorMessage, Response.Status status) {
        return errResponseAsTextPlain("{\"errorMessage\":\"" + errorMessage + "\"}", status);
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
