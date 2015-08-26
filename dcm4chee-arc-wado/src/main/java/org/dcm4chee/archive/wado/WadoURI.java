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

package org.dcm4chee.archive.wado;

import org.dcm4che3.data.*;
import org.dcm4che3.imageio.plugins.dcm.DicomImageReadParam;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.archive.retrieve.InstanceLocations;
import org.dcm4chee.archive.retrieve.RetrieveContext;
import org.dcm4chee.archive.retrieve.RetrieveService;
import org.dcm4chee.archive.validation.constraints.*;

import javax.enterprise.context.RequestScoped;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.*;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.awt.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2015
 */
@RequestScoped
@Path("/wado/{AETitle}")
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

    @Inject
    private RetrieveService service;

    @Context
    private HttpServletRequest request;

    @Context
    private HttpHeaders headers;

    @Inject
    private Device device;

    @QueryParam("requestType")
    @NotNull
    @Pattern(regexp = "WADO")
    private String requestType;

    @QueryParam("studyUID")
    @NotNull
    private String studyUID;

    @QueryParam("seriesUID")
    @NotNull
    private String seriesUID;

    @QueryParam("objectUID")
    @NotNull
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
    @Min(value = 1)
    @Digits(integer = 5, fraction = 0)
    private String rows;

    @QueryParam("columns")
    @Min(value = 1)
    @Digits(integer = 5, fraction = 0)
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
    @Min(value = 1)
    @Digits(integer = 5, fraction = 0)
    private String frameNumber;

    @QueryParam("imageQuality")
    @Min(value = 1)
    @Max(value = 100)
    @Digits(integer = 3, fraction = 0)
    private String imageQuality;

    @QueryParam("presentationUID")
    private String presentationUID;

    @QueryParam("presentationSeriesUID")
    private String presentationSeriesUID;

    @QueryParam("transferSyntax")
    private String transferSyntax;

    private Attributes presentationState;

    @Override
    public String toString() {
        return request.getQueryString();
    }

    @GET
    public Response get(@PathParam("AETitle") String aet) {
        ApplicationEntity ae = device.getApplicationEntity(aet);
        if (ae == null || !ae.isInstalled())
            throw new WebApplicationException(
                    "No such Application Entity: " + aet,
                    Response.Status.NOT_FOUND);

        RetrieveContext ctx = service.newRetrieveContextWADO(request, ae, studyUID, seriesUID, objectUID);
        if (!service.calculateMatches(ctx))
            throw new WebApplicationException(
                    "Specified resource does not exist",
                    Response.Status.NOT_FOUND);

        Collection<InstanceLocations> matches = ctx.getMatches();
        if (matches.size() > 1)
            throw new WebApplicationException(
                    "More than one matching resource found",
                    Response.Status.INTERNAL_SERVER_ERROR);

        InstanceLocations inst = matches.iterator().next();
        ObjectType objectType = objectTypeOf(inst);
        MediaType mimeType = selectMimeType(objectType);
        if (mimeType == null)
            throw new WebApplicationException(Response.Status.NOT_ACCEPTABLE);

        MergeAttributesCoercion coerce = new MergeAttributesCoercion(inst.getAttributes());
        Object entity;
        try {
            if (mimeType.isCompatible(MediaTypes.APPLICATION_DICOM_TYPE)) {
                mimeType = MediaTypes.APPLICATION_DICOM_TYPE;
                entity = new DicomObjectOutput(service.openTranscoder(ctx, inst, tsuids(), true), coerce);
            } else {
                if (objectType.isImage() && presentationUID != null)
                    presentationState = retrievePresentationState(ae);
                entity = entityOf(objectType, mimeType, service.openDicomInputStream(ctx, inst), coerce,
                        inst.getAttributes());
            }
        } catch (IOException e) {
            throw new WebApplicationException(e);
        }
        return Response.ok(entity, mimeType).build();
    }

    private Object entityOf(ObjectType objectType, MediaType mimeType, DicomInputStream dis,
                            AttributesCoercion coerce, Attributes attrs) throws IOException {
        int imageIndex = -1;
        switch (objectType) {
            case SingleFrameImage:
                imageIndex = parseInt(frameNumber, 1) - 1;
            case MultiFrameImage:
                return new RenderedImageOutput(
                        getDicomImageReader(dis),
                        getImageReadParam(attrs),
                        parseInt(rows, 0),
                        parseInt(columns, 0),
                        imageIndex,
                        getImageWriter(mimeType),
                        parseInt(imageQuality, 0));
            case EncapsulatedCDA:
            case EncapsulatedPDF:
                return decapsulateDocument(dis);
            case MPEG2Video:
            case MPEG4Video:
                return decapsulateVideo(dis);
            case SRDocument:
                return renderSRDocument(dis, mimeType, coerce);
        }
        throw new AssertionError("objectType: " + objectType);
    }

    private static int parseInt(String s, int defVal) {
        return s != null ? Integer.parseInt(s) : defVal;
    }

    private Object renderSRDocument(DicomInputStream dis, MediaType mimeType, AttributesCoercion coerce) {
        //TODO
        return null;
    }

    private Object decapsulateVideo(DicomInputStream dis) throws IOException {
        dis.readDataset(-1, Tag.PixelData);
        if (dis.tag() != Tag.PixelData || dis.length() != -1
                || !dis.readItemHeader() || dis.length() != 0
                || !dis.readItemHeader())
            throw new IOException("No or incorrect encapsulated video stream in requested object");

        return new StreamCopyOutput(dis, dis.length());
    }

    private Object decapsulateDocument(DicomInputStream dis) throws IOException {
        dis.readDataset(-1, Tag.EncapsulatedDocument);
        if (dis.tag() != Tag.EncapsulatedDocument)
            throw new IOException("No encapsulated document in requested object");

        return new StreamCopyOutput(dis, dis.length());
    }

    private static ImageReader getDicomImageReader(DicomInputStream dis) {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("DICOM");
        if (!readers.hasNext()) {
            ImageIO.scanForPlugins();
            readers = ImageIO.getImageReadersByFormatName("DICOM");
            if (!readers.hasNext())
                throw new RuntimeException("DICOM Image Reader not registered");
        }
        ImageReader reader = readers.next();
        reader.setInput(dis);
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


    private DicomImageReadParam getImageReadParam(Attributes attrs) {
        DicomImageReadParam param = new DicomImageReadParam();
        if (windowCenter != null && windowWidth != null) {
            param.setWindowCenter(Float.parseFloat(windowCenter));
            param.setWindowWidth(Float.parseFloat(windowWidth));
        }
        if (region != null)
            param.setSourceRegion(new Region(region).getSourceRegion(
                    attrs.getInt(Tag.Rows, 1),
                    attrs.getInt(Tag.Columns, 1)));
        param.setPresentationState(presentationState);
        return param;
    }

    private Attributes retrievePresentationState(ApplicationEntity ae) throws IOException {
        RetrieveContext ctx = service.newRetrieveContextWADO(
                request, ae, studyUID, presentationSeriesUID, presentationUID);
        if (!service.calculateMatches(ctx))
            throw new WebApplicationException(
                    "Specified Presentation State does not exist", Response.Status.NOT_FOUND);

        Collection<InstanceLocations> matches = ctx.getMatches();
        if (matches.size() > 1)
            throw new WebApplicationException(
                    "More than one matching Presentation State found",
                    Response.Status.INTERNAL_SERVER_ERROR);

        InstanceLocations inst = matches.iterator().next();
        try (DicomInputStream dis = service.openDicomInputStream(ctx, inst)) {
            return dis.readDataset(-1, -1);
        }
    }

    private ObjectType objectTypeOf(InstanceLocations inst) {
        String cuid = inst.getSopClassUID();
        String tsuid = inst.getLocations().get(0).getTransferSyntaxUID();
        Attributes attrs = inst.getAttributes();
        return ObjectType.valueOf(cuid, tsuid, attrs, frameNumber);
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

    private enum ObjectType {
        SingleFrameImage(
                MediaTypes.IMAGE_JPEG_TYPE,
                MediaTypes.APPLICATION_DICOM_TYPE,
                MediaTypes.IMAGE_GIF_TYPE,
                MediaTypes.IMAGE_PNG_TYPE),
        MultiFrameImage(MediaTypes.APPLICATION_DICOM_TYPE, MediaTypes.IMAGE_GIF_TYPE),
        MPEG2Video(MediaTypes.APPLICATION_DICOM_TYPE, MediaTypes.VIDEO_MPEG_TYPE),
        MPEG4Video(MediaTypes.APPLICATION_DICOM_TYPE, MediaTypes.VIDEO_MP4_TYPE),
        SRDocument(MediaTypes.APPLICATION_DICOM_TYPE),
        EncapsulatedPDF(MediaTypes.APPLICATION_DICOM_TYPE, MediaTypes.APPLICATION_PDF_TYPE),
        EncapsulatedCDA(MediaTypes.APPLICATION_DICOM_TYPE, MediaType.TEXT_XML_TYPE),
        Other(MediaTypes.APPLICATION_DICOM_TYPE);

        private final MediaType[] mimeTypes;

        ObjectType(MediaType... mimeTypes) {
            this.mimeTypes = mimeTypes;
        }

        public static ObjectType valueOf(String cuid, String tsuid, Attributes attrs, String frameNumber) {
            if (cuid.equals(UID.EncapsulatedPDFStorage))
                return EncapsulatedPDF;
            if (cuid.equals(UID.EncapsulatedCDAStorage))
                return EncapsulatedCDA;
            if (tsuid.equals(UID.MPEG2) || tsuid.equals(UID.MPEG2MainProfileHighLevel))
                return MPEG2Video;
            if (tsuid.equals(UID.MPEG4AVCH264HighProfileLevel41)
                    || tsuid.equals(UID.MPEG4AVCH264BDCompatibleHighProfileLevel41))
                return MPEG4Video;
            if (attrs.contains(Tag.BitsAllocated) && !cuid.equals(UID.RTDoseStorage))
                return (frameNumber == null && attrs.getInt(Tag.NumberOfFrames, 1) > 1)
                        ? MultiFrameImage
                        : SingleFrameImage;
            if (attrs.contains(Tag.ContentSequence))
                return SRDocument;
            return Other;
        }

        public MediaType getDefaultMimeType() {
            return mimeTypes[0];
        }

        public boolean isCompatibleMimeType(MediaType other) {
            for (MediaType type : mimeTypes) {
                if (type.isCompatible(other))
                    return true;
            }
            return false;
        }

        public boolean isImage() {
            return this == SingleFrameImage || this == MultiFrameImage;
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
