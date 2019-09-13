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

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.imageio.plugins.dcm.DicomImageReadParam;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveService;
import org.dcm4chee.arc.retrieve.RetrieveWADO;
import org.dcm4chee.arc.store.InstanceLocations;
import org.dcm4chee.arc.validation.constraints.*;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
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

    @Inject
    private RetrieveService service;

    private HttpServletRequest request;

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

    @Override
    public String toString() {
        return request != null ? request.getRequestURI() + '?' + request.getQueryString() : null;
    }

    @GET
    public void get(@Suspended AsyncResponse ar) {
        // @Inject does not work:
        // org.jboss.resteasy.spi.LoggableFailure: Unable to find contextual data of type: javax.servlet.http.HttpServletRequest
        // s. https://issues.jboss.org/browse/RESTEASY-903
        request = ResteasyProviderFactory.getContextData(HttpServletRequest.class);
        logRequest();
        try {
            checkAET();
            final RetrieveContext ctx = service.newRetrieveContextWADO(HttpServletRequestInfo.valueOf(request), aet, studyUID, seriesUID, objectUID);

            if (request.getHeader(HttpHeaders.IF_MODIFIED_SINCE) == null && request.getHeader(HttpHeaders.IF_UNMODIFIED_SINCE) == null
                    && request.getHeader(HttpHeaders.IF_MATCH) == null && request.getHeader(HttpHeaders.IF_NONE_MATCH) == null) {
                buildResponse(ar, ctx, null);
                return;
            }

            Date lastModified = service.getLastModified(ctx);
            if (lastModified == null)
                throw new WebApplicationException(errResponse("Last Modified date is null.", Response.Status.NOT_FOUND));
            Response.ResponseBuilder respBuilder = evaluatePreConditions(lastModified);

            if (respBuilder == null)
                buildResponse(ar, ctx, lastModified);
            else
                ar.resume(respBuilder.build());
        } catch (Exception e) {
            ar.resume(e);
        }
    }

    private void logRequest() {
        LOG.info("Process {} {}?{} from {}@{}",
                request.getMethod(),
                request.getRequestURI(),
                request.getQueryString(),
                request.getRemoteUser(),
                request.getRemoteHost());
    }

    private void buildResponse(@Suspended AsyncResponse ar, final RetrieveContext ctx, Date lastModified) throws IOException {
        if (!service.calculateMatches(ctx))
            throw new WebApplicationException(errResponse("No matches found.", Response.Status.NOT_FOUND));

        List<InstanceLocations> matches = ctx.getMatches();
        int numMatches = matches.size();
        if (numMatches > 1)
            LOG.debug("{} matches found. Return {}. match", numMatches, numMatches >>> 1);
        InstanceLocations inst = matches.get(numMatches >>> 1);

        if (lastModified == null)
            lastModified = service.getLastModifiedFromMatches(ctx);

        int frame = frame(inst.getAttributes());
        ObjectType objectType = ObjectType.objectTypeOf(ctx, inst, frame);
        MediaType mimeType = selectMimeType(objectType).orElseThrow(() ->
            new WebApplicationException(errResponse(
                    "Supported Media Types for " + objectType + " not acceptable",
                    Response.Status.NOT_ACCEPTABLE)));

        StreamingOutput entity = entityOf(ctx, inst, objectType, mimeType, frame);
        ar.register((CompletionCallback) throwable -> {
            ctx.getRetrieveService().updateLocations(ctx);
            ctx.setException(throwable);
            retrieveWado.fire(ctx);
        });
        ar.resume(Response.ok(entity, mimeType).lastModified(lastModified).tag(String.valueOf(lastModified.hashCode())).build());
    }

    private Response.ResponseBuilder evaluatePreConditions(Date lastModified) {
        return req.evaluatePreconditions(lastModified, new EntityTag(String.valueOf(lastModified.hashCode())));
    }

    private void checkAET() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        if (ae == null || !ae.isInstalled())
            throw new WebApplicationException(errResponse(
                    "No such Application Entity: " + aet,
                    Response.Status.NOT_FOUND));
    }

    private StreamingOutput entityOf(RetrieveContext ctx, InstanceLocations inst, ObjectType objectType,
                            MediaType mimeType, int frame)
            throws IOException {
        if (mimeType == MediaTypes.APPLICATION_DICOM_TYPE)
                return new DicomObjectOutput(ctx, inst, acceptableTransferSyntaxes(objectType, inst));

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

        return new RenderedImageOutput(ctx, inst, readParam, parseInt(rows), parseInt(columns), mimeType, imageQuality,
                frame);
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

        return Stream.of(new ContentTypes(contentType).values)
                .map(mediaType -> objectType.getCompatibleMimeType(mediaType))
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
            case UID.ExplicitVRBigEndianRetired:
            case UID.DeflatedExplicitVRLittleEndian:
                return true;
        }
        return false;
    }

    public static final class ContentTypes {
        final MediaType[] values;

        public ContentTypes(String s) {
            String[] ss = StringUtils.split(s, ',');
            values = new MediaType[ss.length];
            for (int i = 0; i < ss.length; i++)
                values[i] = MediaType.valueOf(ss[i]);
        }
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
