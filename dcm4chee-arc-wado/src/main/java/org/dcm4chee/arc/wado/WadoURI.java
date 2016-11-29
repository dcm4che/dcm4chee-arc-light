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

package org.dcm4chee.arc.wado;

import org.dcm4che3.data.*;
import org.dcm4che3.imageio.plugins.dcm.DicomImageReadParam;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.SAXTransformer;
import org.dcm4che3.io.TemplatesCache;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.retrieve.InstanceLocations;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveService;
import org.dcm4chee.arc.retrieve.RetrieveWADO;
import org.dcm4chee.arc.validation.constraints.*;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.enterprise.event.Event;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.*;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.CompletionCallback;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.*;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;

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
    @Pattern(regexp = "([1-9]\\d?})|100")
    private String imageQuality;

    @QueryParam("presentationUID")
    private String presentationUID;

    @QueryParam("presentationSeriesUID")
    private String presentationSeriesUID;

    @QueryParam("transferSyntax")
    private String transferSyntax;

    @Override
    public String toString() {
        return request.getRequestURI() + '?' + request.getQueryString();
    }

    @GET
    public void get(@Suspended AsyncResponse ar) {
        // @Inject does not work:
        // org.jboss.resteasy.spi.LoggableFailure: Unable to find contextual data of type: javax.servlet.http.HttpServletRequest
        // s. https://issues.jboss.org/browse/RESTEASY-903
        request = ResteasyProviderFactory.getContextData(HttpServletRequest.class);
        LOG.info("Process GET {} from {}@{}", this, request.getRemoteUser(), request.getRemoteHost());
        try {
            checkAET();
            final RetrieveContext ctx = service.newRetrieveContextWADO(request, aet, studyUID, seriesUID, objectUID);

            if (request.getHeader(HttpHeaders.IF_MODIFIED_SINCE) == null && request.getHeader(HttpHeaders.IF_UNMODIFIED_SINCE) == null
                    && request.getHeader(HttpHeaders.IF_MATCH) == null && request.getHeader(HttpHeaders.IF_NONE_MATCH) == null) {
                buildResponse(ar, ctx, null);
                return;
            }

            Date lastModified = service.getLastModified(ctx);
            if (lastModified == null)
                throw new WebApplicationException(Response.Status.NOT_FOUND);
            Response.ResponseBuilder respBuilder = evaluatePreConditions(lastModified);

            if (respBuilder == null)
                buildResponse(ar, ctx, lastModified);
            else
                ar.resume(respBuilder.build());
        } catch (Exception e) {
            ar.resume(e);
        }
    }

    private void buildResponse(@Suspended AsyncResponse ar, final RetrieveContext ctx, Date lastModified) throws IOException {
        if (!service.calculateMatches(ctx))
            throw new WebApplicationException(Response.Status.NOT_FOUND);

        List<InstanceLocations> matches = ctx.getMatches();
        int numMatches = matches.size();
        if (numMatches > 1)
            LOG.debug("{} matches found. Return {}. match", numMatches, numMatches >>> 1);
        InstanceLocations inst = matches.get(numMatches >>> 1);

        if (lastModified == null)
            lastModified = service.getLastModifiedFromMatches(ctx);

        ObjectType objectType = ObjectType.objectTypeOf(ctx, inst, frameNumber);
        MediaType mimeType = selectMimeType(objectType);
        if (mimeType == null)
            throw new WebApplicationException(Response.Status.NOT_ACCEPTABLE);

        StreamingOutput entity;
        if (mimeType.isCompatible(MediaTypes.APPLICATION_DICOM_TYPE)) {
            mimeType = MediaTypes.APPLICATION_DICOM_TYPE;
            entity = new DicomObjectOutput(ctx, inst, tsuids());
        } else {
            entity = entityOf(ctx, inst, objectType, mimeType);
        }
        ar.register(new CompletionCallback() {
            @Override
            public void onComplete(Throwable throwable) {
                ctx.setException(throwable);
                retrieveWado.fire(ctx);
            }
        });
        ar.resume(Response.ok(entity, mimeType).lastModified(lastModified).tag(String.valueOf(lastModified.hashCode())).build());
    }

    private Response.ResponseBuilder evaluatePreConditions(Date lastModified) {
        return req.evaluatePreconditions(lastModified, new EntityTag(String.valueOf(lastModified.hashCode())));
    }

    private void checkAET() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        if (ae == null || !ae.isInstalled())
            throw new WebApplicationException(
                    "No such Application Entity: " + aet,
                    Response.Status.SERVICE_UNAVAILABLE);
    }

    private StreamingOutput entityOf(RetrieveContext ctx, InstanceLocations inst, ObjectType objectType,
                            MediaType mimeType)
            throws IOException {
        int imageIndex = -1;
        switch (objectType) {
            case CompressedSingleFrameImage:
            case UncompressedSingleFrameImage:
                imageIndex = frameNumber(inst.getAttributes()) - 1;
            case CompressedMultiFrameImage:
            case UncompressedMultiFrameImage:
                return renderImage(ctx, inst, mimeType, imageIndex);
            case EncapsulatedCDA:
            case EncapsulatedPDF:
                return decapsulateDocument(service.openDicomInputStream(ctx, inst));
            case MPEG2Video:
            case MPEG4Video:
                return decapsulateVideo(service.openDicomInputStream(ctx, inst));
            case SRDocument:
                return renderSRDocument(ctx, inst, mimeType);
        }
        throw new AssertionError("objectType: " + objectType);
    }

    private RenderedImageOutput renderImage(RetrieveContext ctx, InstanceLocations inst,
                                            MediaType mimeType, int imageIndex) throws IOException {
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

        ImageWriter imageWriter = getImageWriter(mimeType);
        ImageWriteParam writeParam = imageWriter.getDefaultWriteParam();
        if (imageQuality != null)
            writeParam.setCompressionQuality(parseInt(imageQuality) / 100.f);

        ImageReader imageReader = getDicomImageReader();
        return new RenderedImageOutput(service.openDicomInputStream(ctx, inst),
                imageReader, readParam, parseInt(rows), parseInt(columns), imageIndex,
                imageWriter, writeParam);
    }

    private int frameNumber(Attributes attrs) {
        if (frameNumber == null)
            return 1;

        int n = Integer.parseInt(frameNumber);
        if (n > attrs.getInt(Tag.NumberOfFrames, 1))
                throw new WebApplicationException("frameNumber=" + frameNumber
                                + " exceeds number of frames of specified resource",
                        Response.Status.NOT_FOUND);
        return n;
    }

    private static int parseInt(String s) {
        return s != null ? Integer.parseInt(s) : 0;
    }

    private StreamingOutput renderSRDocument(RetrieveContext ctx, InstanceLocations inst, MediaType mimeType)
            throws IOException {
        Attributes attrs;
        try (DicomInputStream dis = service.openDicomInputStream(ctx, inst)){
            attrs = dis.readDataset(-1, -1);
        }
        service.getAttributesCoercion(ctx, inst).coerce(attrs, null);
        return new DicomXSLTOutput(attrs, getTemplate(ctx, mimeType), new SAXTransformer.SetupTransformer() {
            @Override
            public void setup(Transformer transformer) {
                transformer.setParameter("wadoURL", request.getRequestURL().toString());
            }
        });
    }

    private Templates getTemplate(RetrieveContext ctx, MediaType mimeType) {
        ArchiveAEExtension arcAE = ctx.getArchiveAEExtension();
        String uri = StringUtils.replaceSystemProperties(
                mimeType.isCompatible(MediaType.TEXT_HTML_TYPE)
                    ? arcAE.wadoSR2HtmlTemplateURI()
                    : arcAE.wadoSR2TextTemplateURI());
        try {
            return TemplatesCache.getDefault().get(uri);
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    private StreamingOutput decapsulateVideo(DicomInputStream dis) throws IOException {
        dis.readDataset(-1, Tag.PixelData);
        if (dis.tag() != Tag.PixelData || dis.length() != -1
                || !dis.readItemHeader() || dis.length() != 0
                || !dis.readItemHeader())
            throw new IOException("No or incorrect encapsulated video stream in requested object");

        return new StreamCopyOutput(dis, dis.length());
    }

    private StreamingOutput decapsulateDocument(DicomInputStream dis) throws IOException {
        dis.readDataset(-1, Tag.EncapsulatedDocument);
        if (dis.tag() != Tag.EncapsulatedDocument)
            throw new IOException("No encapsulated document in requested object");

        return new StreamCopyOutput(dis, dis.length());
    }

    private static ImageReader getDicomImageReader() {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("DICOM");
        if (!readers.hasNext()) {
            ImageIO.scanForPlugins();
            readers = ImageIO.getImageReadersByFormatName("DICOM");
            if (!readers.hasNext())
                throw new RuntimeException("DICOM Image Reader not registered");
        }
        ImageReader reader = readers.next();
        return reader;
    }

    private static ImageWriter getImageWriter(MediaType mimeType) {
        String formatName = formatNameOf(mimeType);
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(formatName);
        if (!writers.hasNext()) {
            throw new RuntimeException(formatName + " Image Writer not registered");
        }
        return writers.next();
    }

    private static String formatNameOf(MediaType mimeType) {
        return mimeType.getSubtype().toUpperCase();
    }


    private Attributes retrievePresentationState() throws IOException {
        RetrieveContext ctx = service.newRetrieveContextWADO(
                request, aet, studyUID, presentationSeriesUID, presentationUID);
        if (!service.calculateMatches(ctx))
            throw new WebApplicationException(
                    "Specified Presentation State does not exist", Response.Status.NOT_FOUND);

        Collection<InstanceLocations> matches = ctx.getMatches();
        if (matches.size() > 1)
            throw new WebApplicationException("More than one matching Presentation State found");

        InstanceLocations inst = matches.iterator().next();
        try (DicomInputStream dis = service.openDicomInputStream(ctx, inst)) {
            return dis.readDataset(-1, -1);
        }
    }

    private MediaType selectMimeType(ObjectType objectType) {
        if (contentType == null)
            return objectType.getDefaultMimeType();

        for (MediaType mimeType : new ContentTypes(contentType).values) {
            if (objectType.isCompatibleMimeType(mimeType))
                return mimeType;
        }
        return null;
    }

    private Collection<String> tsuids() {
        if (transferSyntax == null)
            return Collections.singleton(UID.ExplicitVRLittleEndian);
        if (transferSyntax.equals("*"))
            return Collections.emptyList();
        return Arrays.asList(StringUtils.split(transferSyntax, ','));
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
}
