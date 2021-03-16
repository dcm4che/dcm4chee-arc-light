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

package org.dcm4chee.arc.wado;

import org.dcm4che3.data.*;
import org.dcm4che3.image.ICCProfile;
import org.dcm4che3.imageio.plugins.dcm.DicomImageReadParam;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.SAXTransformer;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.media.DicomDirWriter;
import org.dcm4che3.media.RecordFactory;
import org.dcm4che3.media.RecordType;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.WebApplication;
import org.dcm4che3.util.*;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.AttributeSet;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.keycloak.KeycloakContext;
import org.dcm4chee.arc.retrieve.*;
import org.dcm4chee.arc.retrieve.stream.DicomObjectOutput;
import org.dcm4chee.arc.rs.util.MediaTypeUtils;
import org.dcm4chee.arc.store.InstanceLocations;
import org.dcm4chee.arc.validation.constraints.ValidValueOf;
import org.jboss.resteasy.plugins.providers.multipart.MultipartRelatedOutput;
import org.jboss.resteasy.plugins.providers.multipart.OutputPart;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.CompletionCallback;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.*;
import javax.xml.transform.stream.StreamResult;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Mar 2016
 */
@RequestScoped
@Path("aets/{AETitle}/rs")
public class WadoRS {

    private static final Logger LOG = LoggerFactory.getLogger(WadoRS.class);
    private static final RecordFactory RECORD_FACTORY = new RecordFactory();

    @Inject
    private RetrieveService service;

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

    @Inject @RetrieveStart
    private Event<RetrieveContext> retrieveStart;

    @Inject @RetrieveEnd
    private Event<RetrieveContext> retrieveEnd;

    @PathParam("AETitle")
    private String aet;

    @QueryParam("accept")
    private List<String> accept;

    @QueryParam("dicomdir")
    @Pattern(regexp = "true|false")
    private String dicomdir;

    @QueryParam("charset")
    private String charset;

    @QueryParam("annotation")
    @Pattern(regexp = "patient|technique|patient,technique|technique,patient")
    private String annotation;

    @QueryParam("quality")
    @Pattern(regexp = "([1-9]\\d?)|100")
    private String imageQuality;

    @QueryParam("viewport")
    @ValidValueOf(type = Viewport.class)
    private String viewport;

    @QueryParam("window")
    @ValidValueOf(type = Windowing.class)
    private String windowing;

    @QueryParam("iccprofile")
    @Pattern(regexp = "no|yes|srgb|adobergb|rommrgb")
    private String iccprofile;

    private List<MediaType> acceptableMediaTypes;
    private List<MediaType> acceptableMultipartRelatedMediaTypes;
    private Collection<String> acceptableTransferSyntaxes;
    private Collection<String> acceptableZipTransferSyntaxes;
    private Map<String, MediaType> selectedMediaTypes;
    private MediaType renderedMediaType;
    private Attributes presentationState;
    private CompressedMFPixelDataOutput compressedMFPixelDataOutput;
    private UncompressedFramesOutput uncompressedFramesOutput;
    private CompressedFramesOutput compressedFramesOutput;
    private DecompressFramesOutput decompressFramesOutput;
    private Response.Status responseStatus;
    private java.nio.file.Path spoolDirectory;
    private java.nio.file.Path dicomdirPath;
    private String dicomRootPartTransferSyntax;

    @Override
    public String toString() {
        String requestURI = request.getRequestURI();
        String queryString = request.getQueryString();
        return queryString == null ? requestURI : requestURI + '?' + queryString;
    }

    @GET
    @Path("/studies/{studyUID}")
    public void retrieveStudy(
            @PathParam("studyUID") String studyUID,
            @Suspended AsyncResponse ar) {
        retrieve(Target.Study, studyUID, null, null, null, null, null, ar);
    }

    @GET
    @Path("/studies/{studyUID}/metadata")
    public void retrieveStudyMetadata(
            @PathParam("studyUID") String studyUID,
            @QueryParam("includefields") String includefields,
            @Suspended AsyncResponse ar) {
        retrieve(Target.StudyMetadata, studyUID, null, null, null, null, includefields, ar);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}")
    public void retrieveSeries(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @Suspended AsyncResponse ar) {
        retrieve(Target.Series, studyUID, seriesUID, null, null, null, null, ar);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}/metadata")
    public void retrieveSeriesMetadata(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @QueryParam("includefields") String includefields,
            @Suspended AsyncResponse ar) {
        retrieve(Target.SeriesMetadata, studyUID, seriesUID, null, null, null, includefields, ar);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}/instances/{objectUID}")
    public void retrieveInstance(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @PathParam("objectUID") String objectUID,
            @Suspended AsyncResponse ar) {
        retrieve(Target.Instance, studyUID, seriesUID, objectUID, null, null, null, ar);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}/instances/{objectUID}/metadata")
    public void retrieveInstanceMetadata(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @PathParam("objectUID") String objectUID,
            @Suspended AsyncResponse ar) {
        retrieve(Target.InstanceMetadata, studyUID, seriesUID, objectUID, null, null, null, ar);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}/instances/{objectUID}/bulkdata/{attributePath:.+}")
    public void retrieveBulkdata(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @PathParam("objectUID") String objectUID,
            @PathParam("attributePath") @ValidValueOf(type = AttributePath.class) String attributePath,
            @Suspended AsyncResponse ar) {
        retrieve(Target.Bulkdata, studyUID, seriesUID, objectUID,
                null, new AttributePath(attributePath).path, null, ar);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}/instances/{objectUID}/frames/{frameList}")
    public void retrieveFrames(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @PathParam("objectUID") String objectUID,
            @PathParam("frameList") @ValidValueOf(type = FrameList.class) String frameList,
            @Suspended AsyncResponse ar) {
        retrieve(Target.Frame, studyUID, seriesUID, objectUID,
                new FrameList(frameList).frames, null, null, ar);
    }

    @GET
    @Path("/studies/{studyUID}/rendered")
    public void retrieveRenderedStudy(
            @PathParam("studyUID") String studyUID,
            @Suspended AsyncResponse ar) {
        retrieve(Target.RenderedStudy, studyUID, null, null,
                null, null, null, ar);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}/rendered")
    public void retrieveRenderedSeries(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @Suspended AsyncResponse ar) {
        retrieve(Target.RenderedSeries, studyUID, seriesUID, null,
                null, null, null, ar);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}/instances/{objectUID}/rendered")
    public void retrieveRenderedInstance(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @PathParam("objectUID") String objectUID,
            @Suspended AsyncResponse ar) {
        retrieve(Target.RenderedInstance, studyUID, seriesUID, objectUID,
                null, null, null, ar);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}/instances/{objectUID}/frames/{frameList}/rendered")
    public void retrieveRenderedFrames(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @PathParam("objectUID") String objectUID,
            @PathParam("frameList") @ValidValueOf(type = FrameList.class) String frameList,
            @Suspended AsyncResponse ar) {
        retrieve(Target.RenderedFrame, studyUID, seriesUID, objectUID,
                new FrameList(frameList).frames, null, null, ar);
    }

    @GET
    @Path("/studies/{studyUID}/thumbnail")
    public void retrieveStudyThumbnail(
            @PathParam("studyUID") String studyUID,
            @Suspended AsyncResponse ar) {
        retrieve(Target.StudyThumbnail, studyUID, null, null,
                null, null, null, ar);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}/thumbnail")
    public void retrieveSeriesThumbnail(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @Suspended AsyncResponse ar) {
        retrieve(Target.SeriesThumbnail, studyUID, seriesUID, null,
                null, null, null, ar);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}/instances/{objectUID}/thumbnail")
    public void retrieveInstanceThumbnail(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @PathParam("objectUID") String objectUID,
            @Suspended AsyncResponse ar) {
        retrieve(Target.InstanceThumbnail, studyUID, seriesUID, objectUID,
                null, null, null, ar);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}/instances/{objectUID}/frames/{frameList}/thumbnail")
    public void retrieveFramesThumbnail(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @PathParam("objectUID") String objectUID,
            @PathParam("frameList") @ValidValueOf(type = FrameList.class) String frameList,
            @Suspended AsyncResponse ar) {
        retrieve(Target.FrameThumbnail, studyUID, seriesUID, objectUID,
                new FrameList(frameList).frames, null, null, ar);
    }

    Output bulkdataPath() {
        checkMultipartRelatedAcceptable();
        return Output.BULKDATA_PATH;
    }

    Output bulkdataFrame() {
        checkMultipartRelatedAcceptable();
        return Output.BULKDATA_FRAME;
    }

    Output render() {
        initAcceptableMediaTypes();
        return Output.RENDER_MULTIPART;
    }

    Output renderFrame() {
        initAcceptableMediaTypes();
        return Output.RENDER_FRAME_MULTIPART;
    }

    private enum Target {
        Study(WadoRS::dicomOrBulkdataOrZIP),
        Series(WadoRS::dicomOrBulkdataOrZIP),
        Instance(WadoRS::dicomOrBulkdataOrZIP),
        Frame(WadoRS::bulkdataFrame),
        Bulkdata(WadoRS::bulkdataPath),
        StudyMetadata(WadoRS::metadataJSONorXML),
        SeriesMetadata(WadoRS::metadataJSONorXML),
        InstanceMetadata(WadoRS::metadataJSONorXML),
        RenderedStudy(WadoRS::render),
        RenderedSeries(WadoRS::render),
        RenderedInstance(WadoRS::render),
        RenderedFrame(WadoRS::renderFrame),
        StudyThumbnail(WadoRS::thumbnail) {
            @Override
            public InstanceLocations selectThumbnailInstance(RetrieveContext ctx) {
                List<InstanceLocations> matches = ctx.getMatches();
                int i = -1;
                do if (++i == matches.size()) return matches.get(i >> 1); while (!matches.get(i).isImage());
                String seriesIUID = matches.get(i).getAttributes().getString(Tag.SeriesInstanceUID);
                int j = i;
                while (++j < matches.size() &&
                        seriesIUID.equals(matches.get(j).getAttributes().getString(Tag.SeriesInstanceUID)));
                return matches.get((i + j) >> 1);
            }
        },
        SeriesThumbnail(WadoRS::thumbnail),
        InstanceThumbnail(WadoRS::thumbnail),
        FrameThumbnail(WadoRS::thumbnail);

        final Function<WadoRS,Output> output;

        Target(Function<WadoRS, Output> output) {
            this.output = output;
        }

        Output output(WadoRS wadoRS) {
            return output.apply(wadoRS);
        }

        public InstanceLocations selectThumbnailInstance(RetrieveContext ctx) {
            List<InstanceLocations> matches = ctx.getMatches();
            return matches.get(matches.size() >> 1);
        }
    }

    private void logRequest() {
        LOG.info("Process {} {} with HTTPHeaders[{}] from {}@{}",
                request.getMethod(),
                toString(),
                headers(),
                request.getRemoteUser(),
                request.getRemoteHost());
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

    private void validateWebApp() {
        WebApplication webApplication = device.getWebApplications().stream()
                .filter(webApp -> request.getRequestURI().startsWith(webApp.getServicePath())
                        && Arrays.asList(webApp.getServiceClasses())
                        .contains(WebApplication.ServiceClass.WADO_RS))
                .findFirst()
                .orElseThrow(() -> new WebApplicationException(errResponse(
                        "No Web Application with WADO_RS service class found for Application Entity: " + aet,
                        Response.Status.NOT_FOUND)));

        KeycloakContext keycloakContext = KeycloakContext.valueOf(request);
        if (keycloakContext.isSecured()
                && webApplication.getProperties().containsKey("roles"))
            Arrays.stream(webApplication.getProperties().get("roles").split(","))
                    .filter(keycloakContext::isUserInRole)
                    .findFirst()
                    .orElseThrow(() -> new WebApplicationException(errResponse(
                            "Web Application with WADO_RS service class does not list role of accessing user",
                            Response.Status.FORBIDDEN)));
    }

    private void initAcceptableMediaTypes() {
        acceptableMediaTypes = MediaTypeUtils.acceptableMediaTypesOf(headers, accept);
        acceptableMultipartRelatedMediaTypes = acceptableMediaTypes.stream()
                .map(MediaTypes::getMultiPartRelatedType)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        acceptableTransferSyntaxes = transferSyntaxesOf(acceptableMultipartRelatedMediaTypes.stream()
                .filter(m -> m.isCompatible(MediaTypes.APPLICATION_DICOM_TYPE)));
        acceptableZipTransferSyntaxes = transferSyntaxesOf(acceptableMediaTypes.stream()
                .filter(m -> m.isCompatible(MediaTypes.APPLICATION_ZIP_TYPE)));
    }

    private List<String> transferSyntaxesOf(Stream<MediaType> mediaTypeStream) {
        return mediaTypeStream
                .map(m -> m.getParameters().getOrDefault("transfer-syntax", UID.ExplicitVRLittleEndian))
                .collect(Collectors.toList());
    }

    private void checkMultipartRelatedAcceptable() {
        initAcceptableMediaTypes();
        if (acceptableMultipartRelatedMediaTypes.isEmpty()) {
            throw new WebApplicationException(Response.Status.NOT_ACCEPTABLE);
        }
    }

    private Output thumbnail() {
        initAcceptableMediaTypes();
        MediaType mediaType = selectMediaType(acceptableMediaTypes,
                MediaTypes.IMAGE_PNG_TYPE,
                MediaTypes.IMAGE_JPEG_TYPE,
                MediaTypes.IMAGE_GIF_TYPE);
        if (mediaType == null) new WebApplicationException(Response.Status.NOT_ACCEPTABLE);
        renderedMediaType = mediaType;
        return Output.THUMBNAIL;
    }

    private Output dicomOrBulkdataOrZIP() {
        initAcceptableMediaTypes();
        if (acceptableMultipartRelatedMediaTypes.isEmpty() && acceptableZipTransferSyntaxes.isEmpty()) {
            throw new WebApplicationException(Response.Status.NOT_ACCEPTABLE);
        }
        return selectMediaType(acceptableMultipartRelatedMediaTypes, MediaTypes.APPLICATION_DICOM_TYPE) != null
                ? Output.DICOM
                : acceptableZipTransferSyntaxes.isEmpty() ? Output.BULKDATA : Output.ZIP;
    }

    private Output metadataJSONorXML() {
        initAcceptableMediaTypes();
        MediaType mediaType = selectMediaType(acceptableMediaTypes,
                MediaTypes.APPLICATION_DICOM_JSON_TYPE,
                MediaType.APPLICATION_JSON_TYPE);
        if (mediaType == null) {
            mediaType = selectMediaType(acceptableMultipartRelatedMediaTypes, MediaTypes.APPLICATION_DICOM_XML_TYPE);
            if (mediaType == null) {
                throw new WebApplicationException(Response.Status.NOT_ACCEPTABLE);
            }
        }
        return mediaType == MediaTypes.APPLICATION_DICOM_XML_TYPE ? Output.METADATA_XML : Output.METADATA_JSON;
    }

    private static MediaType selectMediaType(List<MediaType> accepted, MediaType... provided) {
        for (MediaType acceptedMediaType : accepted) {
            for (MediaType mediaType : provided) {
                if (mediaType.isCompatible(acceptedMediaType)) return mediaType;
            }
        }
        return null;
    }

    private void retrieve(Target target, String studyUID, String seriesUID, String objectUID, int[] frameList,
            int[] attributePath, String includefields, AsyncResponse ar) {
        logRequest();
        if (aet.equals(getApplicationEntity().getAETitle()))
            validateWebApp();
        Output output = target.output(this);
        try {
            // @Inject does not work:
            // org.jboss.resteasy.spi.LoggableFailure: Unable to find contextual data of type: javax.servlet.http.HttpServletRequest
            // s. https://issues.jboss.org/browse/RESTEASY-903
            HttpServletRequest request = ResteasyProviderFactory.getContextData(HttpServletRequest.class);
            final RetrieveContext ctx = service.newRetrieveContextWADO(
                    HttpServletRequestInfo.valueOf(request), aet, studyUID, seriesUID, objectUID);
            if (output.isMetadata()) {
                ctx.setObjectType(null);
                ctx.setMetadataFilter(getMetadataFilter(includefields));
            }

            if (request.getHeader(HttpHeaders.IF_MODIFIED_SINCE) == null
                    && request.getHeader(HttpHeaders.IF_UNMODIFIED_SINCE) == null
                    && request.getHeader(HttpHeaders.IF_MATCH) == null
                    && request.getHeader(HttpHeaders.IF_NONE_MATCH) == null) {
                buildResponse(target, frameList, attributePath, ar, output, ctx, null);
                return;
            }

            Date lastModified = service.getLastModified(ctx);
            if (lastModified == null)
                throw new WebApplicationException(
                        errResponse("Last Modified date is null.", Response.Status.NOT_FOUND));
            Response.ResponseBuilder respBuilder = evaluatePreConditions(lastModified);

            if (respBuilder == null)
                buildResponse(target, frameList, attributePath, ar, output, ctx, lastModified);
            else
                ar.resume(respBuilder.build());
        } catch (Exception e) {
            ar.resume(e);
        }
    }

    private AttributeSet getMetadataFilter(String name) {
        if (name == null)
            return null;

        AttributeSet filter = device.getDeviceExtension(ArchiveDeviceExtension.class)
                .getAttributeSet(AttributeSet.Type.WADO_RS).get(name);
        if (filter == null)
            LOG.info("No Metadata filter configured for includefields={}", name);
        return filter;
    }

    private void buildResponse(Target target, int[] frameList, int[] attributePath, AsyncResponse ar, Output output,
            final RetrieveContext ctx, Date lastModified) throws IOException {
        service.calculateMatches(ctx);
        LOG.info("retrieve{}: {} Matches", target, ctx.getNumberOfMatches());
        if (ctx.getNumberOfMatches() == 0)
            throw new WebApplicationException(errResponse("No matches found.", Response.Status.NOT_FOUND));
//            Collection<InstanceLocations> notAccessable = service.removeNotAccessableMatches(ctx);
        responseStatus = Response.Status.OK;
        if (frameList != null) {
            Attributes attrs = ctx.getMatches().get(0).getAttributes();
            if (!attrs.containsValue(Tag.Rows))
                throw new WebApplicationException(errResponse("Not an image.", Response.Status.NOT_FOUND));
            frameList = adjustFrameList(frameList, attrs.getInt(Tag.NumberOfFrames, 1));
            if (frameList.length == 0)
                throw new WebApplicationException(errResponse("No such frame.", Response.Status.NOT_FOUND));
        }
        output = output.adjust(this, frameList, ctx);
        Collection<InstanceLocations> notAccepted = output.removeNotAcceptedMatches(
                this, ctx, frameList, attributePath);
        if (ctx.getMatches().isEmpty()) {
            Response errResp = notAccepted.isEmpty()
                    ? errResponse("No matches found.", Response.Status.NOT_FOUND)
                    : errResponse("Not accepted instances present.", Response.Status.NOT_ACCEPTABLE);
            throw new WebApplicationException(errResp);
        }
        if (!notAccepted.isEmpty())
            responseStatus = Response.Status.PARTIAL_CONTENT;

        if (lastModified == null)
            lastModified = service.getLastModifiedFromMatches(ctx);

        retrieveStart.fire(ctx);
        ar.register((CompletionCallback) throwable -> {
                SafeClose.close(compressedMFPixelDataOutput);
                SafeClose.close(uncompressedFramesOutput);
                SafeClose.close(compressedFramesOutput);
                SafeClose.close(decompressFramesOutput);
                purgeSpoolDirectory();
            ctx.getRetrieveService().updateLocations(ctx);
            ctx.setException(throwable);
                retrieveEnd.fire(ctx);
        });
        Object entity = output.entity(this, target, ctx, frameList, attributePath);
        ar.resume(output.response(this, lastModified, entity).build());
    }

    private static boolean matchPresentionState(RetrieveContext ctx) {
        InstanceLocations inst = ctx.getMatches().iterator().next();
        ArchiveDeviceExtension arcDev = ctx.getArchiveAEExtension().getArchiveDeviceExtension();
        return arcDev.isWadoSupportedPRClass(inst.getSopClassUID());
    }

    private void retrievePresentionState(RetrieveContext ctx) throws IOException {
        InstanceLocations inst = ctx.getMatches().iterator().next();
        if (ctx.copyToRetrieveCache(inst)) {
            ctx.copyToRetrieveCache(null);
            inst = ctx.copiedToRetrieveCache();
        }
        Attributes attrs;
        try (DicomInputStream dis = service.openDicomInputStream(ctx, inst)){
            attrs = dis.readDataset();
            service.getAttributesCoercion(ctx, inst).coerce(attrs, null);
        }
        Collection<InstanceLocations> matches = new ArrayList<>();
        for (Attributes series : attrs.getSequence(Tag.ReferencedSeriesSequence)) {
            ctx.setSeriesInstanceUIDs(series.getString(Tag.SeriesInstanceUID));
            ctx.setSopInstanceUIDs(series.getSequence(Tag.ReferencedImageSequence)
                    .stream().map(item -> item.getString(Tag.ReferencedSOPInstanceUID)).toArray(String[]::new));
            service.calculateMatches(ctx);
            matches.addAll(ctx.getMatches());
        }
        ctx.getMatches().clear();
        ctx.getMatches().addAll(matches);
        ctx.setNumberOfMatches(matches.size());
        presentationState = attrs;
    }

    private Response.ResponseBuilder evaluatePreConditions(Date lastModified) {
        return req.evaluatePreconditions(lastModified, new EntityTag(String.valueOf(lastModified.hashCode())));
    }

    private ApplicationEntity getApplicationEntity() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        if (ae == null || !ae.isInstalled())
            throw new WebApplicationException(errResponse(
                    "No such Application Entity: " + aet,
                    Response.Status.NOT_FOUND));
        return ae;
    }

    private enum Output {
        DICOM {
            @Override
            public List<InstanceLocations> removeNotAcceptedMatches(WadoRS wadoRS, RetrieveContext ctx,
                    int[] frameList, int[] attributePath) {
                return Collections.EMPTY_LIST;
            }
            @Override
            protected void addPart(MultipartRelatedOutput output, WadoRS wadoRS, RetrieveContext ctx,
                    InstanceLocations inst, int[] frameList, int[] attributePath) {
                wadoRS.writeDICOM(output, ctx, inst);
            }
            @Override
            public Object entity(WadoRS wadoRS, Target target, RetrieveContext ctx, int[] frameList, int[] attributePath)
                    throws IOException {
                MultipartRelatedOutput output = new MultipartRelatedOutput();
                for (InstanceLocations inst : ctx.getMatches()) {
                    if (!ctx.copyToRetrieveCache(inst))
                        addPart(output, wadoRS, ctx, inst, frameList, attributePath);
                }
                ctx.copyToRetrieveCache(null);
                InstanceLocations inst;
                while ((inst = ctx.copiedToRetrieveCache()) != null) {
                    addPart(output, wadoRS, ctx, inst, frameList, attributePath);
                }
                wadoRS.dicomRootPartTransferSyntax = output.getRootPart().getMediaType().getParameters().get("transfer-syntax");
                return output;
            }
            @Override
            public Response.ResponseBuilder response(WadoRS wadoRS, Date lastModified, Object entity) {
                Map<String, String> parameters = new HashMap<>();
                parameters.put("type", MediaTypes.APPLICATION_DICOM);
                parameters.put("transfer-syntax", wadoRS.dicomRootPartTransferSyntax);
                MediaType responseContentType = new MediaType(
                        MediaTypes.MULTIPART_RELATED_TYPE.getType(),
                        MediaTypes.MULTIPART_RELATED_TYPE.getSubtype(),
                        parameters);
                return Response.status(wadoRS.responseStatus)
                        .lastModified(lastModified)
                        .tag(String.valueOf(lastModified.hashCode()))
                        .type(responseContentType)
                        .entity(entity);
            }
        },
        ZIP {
            @Override
            public List<InstanceLocations> removeNotAcceptedMatches(WadoRS wadoRS, RetrieveContext ctx,
                    int[] frameList, int[] attributePath) {
                return Collections.EMPTY_LIST;
            }
            @Override
            public Object entity(WadoRS wadoRS, Target target, RetrieveContext ctx, int[] frameList, int[] attributePath) {
                return wadoRS.writeZIP(ctx);
            }

            @Override
            public Response.ResponseBuilder response(WadoRS wadoRS, Date lastModified, Object entity) {
                return super.response(wadoRS, lastModified, entity).type(MediaTypes.APPLICATION_ZIP_TYPE);
            }
        },
        BULKDATA {
            @Override
            protected void addPart(MultipartRelatedOutput output, WadoRS wadoRS, RetrieveContext ctx,
                    InstanceLocations inst, int[] frameList, int[] attributePath) {
                wadoRS.writeBulkdata(output, ctx, inst);
            }
        },
        BULKDATA_FRAME {
            @Override
            protected MediaType[] mediaTypesFor(InstanceLocations match, ObjectType objectType, int[] attributePath) {
                return objectType.isImage() ? objectType.getBulkdataContentTypes(match) : null;
            }

            @Override
            protected void addPart(MultipartRelatedOutput output, WadoRS wadoRS, RetrieveContext ctx,
                    InstanceLocations inst, int[] frameList, int[] attributePath) throws IOException {
                wadoRS.writeFrames(output, ctx, inst, frameList);
            }
        },
        BULKDATA_PATH {
            @Override
            protected MediaType[] mediaTypesFor(InstanceLocations match, RetrieveContext ctx, int[] attributePath, int frame) {
                return isEncapsulatedDocument(attributePath)
                        ? super.mediaTypesFor(match, ctx, attributePath, 0)
                        : new MediaType[] { MediaType.APPLICATION_OCTET_STREAM_TYPE };
            }

            @Override
            protected void addPart(MultipartRelatedOutput output, WadoRS wadoRS, RetrieveContext ctx,
                    InstanceLocations inst, int[] frameList, int[] attributePath) {
                wadoRS.writeBulkdata(output, ctx, inst, attributePath);
            }
        },
        RENDER_MULTIPART {
            @Override
            protected MediaType[] mediaTypesFor(InstanceLocations match, ObjectType objectType, int[] attributePath) {
                return objectType.getRenderedContentTypes();
            }

            @Override
            protected void addPart(MultipartRelatedOutput output, WadoRS wadoRS, RetrieveContext ctx,
                    InstanceLocations inst, int[] frameList, int[] attributePath) {
                wadoRS.writeRenderedInstance(output, ctx, inst);
            }

            @Override
            public Output adjust(WadoRS wadoRS, int[] frameList, RetrieveContext ctx) throws IOException {
                if (ctx.getNumberOfMatches() == 1) {
                    if (matchPresentionState(ctx)) {
                        wadoRS.retrievePresentionState(ctx);
                        return Output.RENDER_FRAME_MULTIPART.adjust(wadoRS, null, ctx);
                    }
                    InstanceLocations inst = ctx.getMatches().iterator().next();
                    MediaType[] mediaTypes = mediaTypesFor(inst, ctx, null, 0);
                    if (mediaTypes != null) {
                        MediaType mediaType = wadoRS.selectMediaType(wadoRS.acceptableMediaTypes, mediaTypes);
                        if (mediaType != null) {
                            wadoRS.renderedMediaType = mediaType;
                            return RENDER;
                        }
                    }
                }
                return this;
            }
        },
        RENDER_FRAME_MULTIPART {
            @Override
            public Output adjust(WadoRS wadoRS, int[] frameList, RetrieveContext ctx) throws IOException {
                if (ctx.getNumberOfMatches() == 1) {
                    InstanceLocations inst = ctx.getMatches().iterator().next();
                    if (frameList == null ? !inst.isMultiframe() : frameList.length == 1) {
                        MediaType[] mediaTypes = mediaTypesFor(inst, ctx, null, 1);
                        if (mediaTypes != null) {
                            MediaType mediaType =
                                    wadoRS.selectMediaType(wadoRS.acceptableMediaTypes, mediaTypes);
                            if (mediaType != null) {
                                wadoRS.renderedMediaType = mediaType;
                                return RENDER_FRAME;
                            }
                        }
                    }
                }
                return this;
            }

            @Override
            protected MediaType[] mediaTypesFor(InstanceLocations match, ObjectType objectType, int[] attributePath) {
                return objectType.isImage() ? objectType.getRenderedContentTypes() : null;
            }

            @Override
            protected void addPart(MultipartRelatedOutput output, WadoRS wadoRS, RetrieveContext ctx,
                    InstanceLocations inst, int[] frameList, int[] attributePath) {
                wadoRS.writeRenderedFrames(output, ctx, inst, frameList);
            }
        },
        RENDER {
            @Override
            public List<InstanceLocations> removeNotAcceptedMatches(WadoRS wadoRS, RetrieveContext ctx,
                    int[] frameList, int[] attributePath) {
                return Collections.EMPTY_LIST;
            }

            @Override
            public Object entity(WadoRS wadoRS, Target target, RetrieveContext ctx, int[] frameList,
                    int[] attributePath) {
                InstanceLocations inst = target.selectThumbnailInstance(ctx);
                if (ctx.copyToRetrieveCache(inst)) {
                    ctx.copyToRetrieveCache(null);
                    inst = ctx.copiedToRetrieveCache();
                }
                return wadoRS.renderInstance(ctx, inst, wadoRS.renderedMediaType);
            }

            @Override
            public Response.ResponseBuilder response(WadoRS wadoRS, Date lastModified, Object entity) {
                return super.response(wadoRS, lastModified, entity).type(wadoRS.renderedMediaType);
            }
        },
        RENDER_FRAME {
            @Override
            public List<InstanceLocations> removeNotAcceptedMatches(WadoRS wadoRS, RetrieveContext ctx,
                    int[] frameList, int[] attributePath) {
                return Collections.EMPTY_LIST;
            }

            @Override
            public Object entity(WadoRS wadoRS, Target target, RetrieveContext ctx, int[] frameList,
                    int[] attributePath) {
                InstanceLocations inst = target.selectThumbnailInstance(ctx);
                if (ctx.copyToRetrieveCache(inst)) {
                    ctx.copyToRetrieveCache(null);
                    inst = ctx.copiedToRetrieveCache();
                }
                return wadoRS.renderFrame(ctx, inst, wadoRS.renderedMediaType, frameList == null ? 1 : frameList[0]);
            }

            @Override
            public Response.ResponseBuilder response(WadoRS wadoRS, Date lastModified, Object entity) {
                return super.response(wadoRS, lastModified, entity).type(wadoRS.renderedMediaType);
            }
        },
        METADATA_XML {
            @Override
            public List<InstanceLocations> removeNotAcceptedMatches(WadoRS wadoRS, RetrieveContext ctx, int[] frameList, int[] attributePath) {
                return Collections.EMPTY_LIST;
            }
            @Override
            protected void addPart(MultipartRelatedOutput output, WadoRS wadoRS, RetrieveContext ctx,
                    InstanceLocations inst, int[] frameList, int[] attributePath) {
                wadoRS.writeMetadataXML(output, ctx, inst);
            }
            @Override
            public boolean isMetadata() {
                return true;
            }
        },
        THUMBNAIL {
            @Override
            public List<InstanceLocations> removeNotAcceptedMatches(WadoRS wadoRS, RetrieveContext ctx,
                    int[] frameList, int[] attributePath) {
                return Collections.EMPTY_LIST;
            }

            @Override
            public Object entity(WadoRS wadoRS, Target target, RetrieveContext ctx, int[] frameList,
                    int[] attributePath) {
                Viewport viewport = wadoRS.thumbnailViewPort(ctx);
                InstanceLocations inst = target.selectThumbnailInstance(ctx);
                if (!inst.isImage() || inst.isVideo())
                    return wadoRS.renderThumbnail(inst, viewport);

                if (ctx.copyToRetrieveCache(inst)) {
                    ctx.copyToRetrieveCache(null);
                    inst = ctx.copiedToRetrieveCache();
                }
                return wadoRS.renderImage(ctx, inst, wadoRS.renderedMediaType,
                        frameList != null ? frameList[0] : 1, null, viewport);
            }

            @Override
            public Response.ResponseBuilder response(WadoRS wadoRS, Date lastModified, Object entity) {
                return super.response(wadoRS, lastModified, entity).type(wadoRS.renderedMediaType);
            }
        },
        METADATA_JSON {
            @Override
            public List<InstanceLocations> removeNotAcceptedMatches(WadoRS wadoRS, RetrieveContext ctx, int[] frameList, int[] attributePath) {
                return Collections.EMPTY_LIST;
            }
            @Override
            public Object entity(WadoRS wadoRS, Target target, RetrieveContext ctx, int[] frameList, int[] attributePath) {
                return wadoRS.writeMetadataJSON(ctx);
            }
            @Override
            public boolean isMetadata() {
                return true;
            }

            @Override
            public Response.ResponseBuilder response(WadoRS wadoRS, Date lastModified, Object entity) {
                return super.response(wadoRS, lastModified, entity).type(MediaTypes.APPLICATION_DICOM_JSON_TYPE);
            }
        };

        public Object entity(WadoRS wadoRS, Target target, RetrieveContext ctx, int[] frameList, int[] attributePath)
                throws IOException {
            MultipartRelatedOutput output = new MultipartRelatedOutput();
            for (InstanceLocations inst : ctx.getMatches()) {
                if (!ctx.copyToRetrieveCache(inst))
                    addPart(output, wadoRS, ctx, inst, frameList, attributePath);
            }
            ctx.copyToRetrieveCache(null);
            InstanceLocations inst;
            while ((inst = ctx.copiedToRetrieveCache()) != null) {
                addPart(output, wadoRS, ctx, inst, frameList, attributePath);
            }
            return output;
        }

        public List<InstanceLocations> removeNotAcceptedMatches(
                WadoRS wadoRS, RetrieveContext ctx, int[] frameList, int[] attributePath) {
            Collection<InstanceLocations> matches = ctx.getMatches();
            List<InstanceLocations> notAcceptable = new ArrayList<>(matches.size());
            Map<String,MediaType> selectedMediaTypes = new HashMap<>(matches.size() * 4 / 3);
            Iterator<InstanceLocations> iter = matches.iterator();
            while (iter.hasNext()) {
                InstanceLocations match = iter.next();
                MediaType[] mediaTypes = mediaTypesFor(match, ctx, attributePath, frameList == null ? 0 : 1);
                if (mediaTypes == null) {
                    iter.remove();
                    continue;
                }
                MediaType mediaType =
                        selectMediaType(wadoRS.acceptableMultipartRelatedMediaTypes, mediaTypes);
                if (mediaType != null) {
                    selectedMediaTypes.put(match.getSopInstanceUID(), mediaType);
                } else {
                    iter.remove();
                    notAcceptable.add(match);
                }
            }
            wadoRS.selectedMediaTypes = selectedMediaTypes;
            return notAcceptable;
        }

        protected MediaType[] mediaTypesFor(InstanceLocations match, RetrieveContext ctx, int[] attributePath, int frame) {
            return mediaTypesFor(match, ObjectType.objectTypeOf(ctx, match, frame), attributePath);
        }

        protected MediaType[] mediaTypesFor(InstanceLocations match, ObjectType objectType, int[] attributePath) {
            return objectType.getBulkdataContentTypes(match);
        }

        protected void addPart(MultipartRelatedOutput output, WadoRS wadoRS, RetrieveContext ctx,
                InstanceLocations inst, int[] frameList, int[] attributePath) throws IOException {
             throw new WebApplicationException(errResponse(
                     name() + " not implemented", Response.Status.SERVICE_UNAVAILABLE));
        }

        public boolean isMetadata() {
            return false;
        }

        public Response.ResponseBuilder response(WadoRS wadoRS, Date lastModified, Object entity) {
            return Response.status(wadoRS.responseStatus).lastModified(lastModified)
                    .tag(String.valueOf(lastModified.hashCode())).entity(entity);
        }

        public Output adjust(WadoRS wadoRS, int[] frameList, RetrieveContext ctx) throws IOException {
            return this;
        }
    }

    private Object renderThumbnail(InstanceLocations inst, Viewport viewport) {
        File file = new File(URI.create(StringUtils.replaceSystemProperties(thumbnailURL(inst))));
        return viewport.isThumbnailDefault() && renderedMediaType.equals(MediaTypes.IMAGE_PNG_TYPE)
                ? file
                : new ThumbnailOutput(file, viewport.rows, viewport.columns, renderedMediaType);
    }

    private String thumbnailURL(InstanceLocations inst) {
        if (inst.isVideo())
            return "${jboss.server.temp.url}/dcm4chee-arc/thumbnails/video.png";

        String cuid = inst.getSopClassUID();
        switch (cuid) {
            case UID.EncapsulatedPDFStorage:
                return "${jboss.server.temp.url}/dcm4chee-arc/thumbnails/pdf.png";
            case UID.EncapsulatedCDAStorage:
                return "${jboss.server.temp.url}/dcm4chee-arc/thumbnails/cda.png";
            case UID.EncapsulatedSTLStorage:
            case UID.EncapsulatedOBJStorage:
            case UID.EncapsulatedMTLStorage:
                return "${jboss.server.temp.url}/dcm4chee-arc/thumbnails/stl.png";
            case UID.KeyObjectSelectionDocumentStorage:
                return "${jboss.server.temp.url}/dcm4chee-arc/thumbnails/ko.png";
            case UID.RawDataStorage:
                return "${jboss.server.temp.url}/dcm4chee-arc/thumbnails/rawdata.png";
        }
        if (cuid.startsWith("1.2.840.10008.5.1.4.1.1.88."))
            return "${jboss.server.temp.url}/dcm4chee-arc/thumbnails/sr.png";
        if (cuid.startsWith("1.2.840.10008.5.1.4.1.1.9."))
            return "${jboss.server.temp.url}/dcm4chee-arc/thumbnails/waveform.png";
        if (cuid.startsWith("1.2.840.10008.5.1.4.1.1.11."))
            return "${jboss.server.temp.url}/dcm4chee-arc/thumbnails/pr.png";
        return "${jboss.server.temp.url}/dcm4chee-arc/thumbnails/other.png";
    }

    private Windowing windowing() {
        return windowing != null ? new Windowing(windowing) : null;
    }

    private Viewport viewport() {
        return viewport != null ? new Viewport(viewport) : null;
    }

    private Viewport thumbnailViewPort(RetrieveContext ctx) {
        return new Viewport(viewport != null ? viewport : ctx.getArchiveAEExtension().wadoThumbnailViewPort());
    }

    private static boolean isEncapsulatedDocument(int[] attributePath) {
        return attributePath.length == 1 && attributePath[0] == Tag.EncapsulatedDocument;
    }

    private void writeRenderedInstance(MultipartRelatedOutput output, RetrieveContext ctx, InstanceLocations inst) {
        MediaType mediaType = selectedMediaTypes.get(inst.getSopInstanceUID());
        StringBuffer bulkdataURL = request.getRequestURL();
        bulkdataURL.setLength(bulkdataURL.length() - 9);
        mkInstanceURL(bulkdataURL, inst);
        bulkdataURL.append("/rendered");
        OutputPart outputPart = output.addPart(renderInstance(ctx, inst, mediaType), mediaType);
        outputPart.getHeaders().putSingle("Content-Location", bulkdataURL.toString());
    }

    private StreamingOutput renderInstance(RetrieveContext ctx, InstanceLocations inst, MediaType mediaType) {
        ObjectType objectType = ObjectType.objectTypeOf(ctx, inst, 0);
        switch (objectType) {
            case UncompressedSingleFrameImage:
            case CompressedSingleFrameImage:
                return renderImage(ctx, inst, mediaType, 1, windowing(), viewport());
            case UncompressedMultiFrameImage:
            case CompressedMultiFrameImage:
                return renderImage(ctx, inst, mediaType, 0, windowing(), viewport());
            case MPEG2Video:
            case MPEG4Video:
                return new CompressedPixelDataOutput(ctx, inst);
            case EncapsulatedPDF:
                return new BulkdataOutput(ctx, inst, Tag.EncapsulatedDocument);
            case SRDocument:
                return new DicomXSLTOutput(ctx, inst, mediaType, wadoURL());
            default:
                throw new AssertionError("Unexpected object type: " + objectType);
        }
    }

    private String wadoURL() {
        StringBuffer sb = device.getDeviceExtension(ArchiveDeviceExtension.class).remapRetrieveURL(request);
        sb.setLength(sb.indexOf("/rs/studies/"));
        return sb.append("/wado").toString();
    }

    private void writeRenderedFrames(MultipartRelatedOutput output, RetrieveContext ctx, InstanceLocations inst,
            int[] frameList) {
        int numFrames = inst.getAttributes().getInt(Tag.NumberOfFrames, 1);
        StringBuffer bulkdataURL = request.getRequestURL();
        if (frameList == null) { // render PR
            frameList = IntStream.rangeClosed(1, numFrames).toArray();
            bulkdataURL.setLength(bulkdataURL.lastIndexOf("/series/"));
            mkInstanceURL(bulkdataURL, inst);
        } else { // render Frames
            bulkdataURL.setLength(bulkdataURL.lastIndexOf("/frames/"));
        }
        int length = bulkdataURL.append("/frames/").length();
        MediaType mediaType = selectedMediaTypes.get(inst.getSopInstanceUID());
        for (int frame : frameList) {
            OutputPart outputPart = output.addPart(renderFrame(ctx, inst, mediaType, frame), mediaType);
            bulkdataURL.setLength(length);
            bulkdataURL.append(frame);
            bulkdataURL.append("/rendered");
            outputPart.getHeaders().putSingle("Content-Location", bulkdataURL.toString());
        }
    }

    private RenderedImageOutput renderFrame(RetrieveContext ctx, InstanceLocations inst, MediaType mediaType,
            int frame) {
        return renderImage(ctx, inst, mediaType, frame, windowing(), viewport());
    }

    private void writeBulkdata(MultipartRelatedOutput output, RetrieveContext ctx, InstanceLocations inst) {
        MediaType mediaType = selectedMediaTypes.get(inst.getSopInstanceUID());
        StringBuffer bulkdataURL = request.getRequestURL();
        mkInstanceURL(bulkdataURL, inst);
        StreamingOutput entity;
        ObjectType objectType = ObjectType.objectTypeOf(ctx, inst, 0);
        switch (objectType) {
            case UncompressedSingleFrameImage:
            case UncompressedMultiFrameImage:
                entity = new BulkdataOutput(ctx, inst, Tag.PixelData);
                break;
            case CompressedMultiFrameImage:
                if (mediaType == MediaType.APPLICATION_OCTET_STREAM_TYPE) {
                    entity = new DecompressPixelDataOutput(ctx, inst);
                    break;
                }
                writeCompressedMultiFrameImage(output, ctx, inst, mediaType, bulkdataURL);
                return;
            case CompressedSingleFrameImage:
                if (mediaType == MediaType.APPLICATION_OCTET_STREAM_TYPE) {
                    entity = new DecompressPixelDataOutput(ctx, inst);
                    break;
                }
            case MPEG2Video:
            case MPEG4Video:
                entity = new CompressedPixelDataOutput(ctx, inst);
                break;
            case EncapsulatedPDF:
            case EncapsulatedCDA:
            case EncapsulatedSTL:
            case EncapsulatedOBJ:
            case EncapsulatedMTL:
                entity = new BulkdataOutput(ctx, inst, Tag.EncapsulatedDocument);
                break;
            default:
                throw new AssertionError("Unexpected object type: " + objectType);
        }
        OutputPart outputPart = output.addPart(entity, mediaType);
        outputPart.getHeaders().putSingle("Content-Location", bulkdataURL.toString());
    }

    private void writeFrames(MultipartRelatedOutput output, RetrieveContext ctx, InstanceLocations inst,
                             int[] frameList) throws IOException {
        int numFrames = inst.getAttributes().getInt(Tag.NumberOfFrames, 1);
        MediaType mediaType = selectedMediaTypes.get(inst.getSopInstanceUID());
        StringBuffer bulkdataURL = request.getRequestURL();
        bulkdataURL.setLength(bulkdataURL.lastIndexOf("/frames/") + 8);
        StreamingOutput entity;
        ObjectType objectType = ObjectType.objectTypeOf(ctx, inst, 0);
        switch (objectType) {
            case UncompressedMultiFrameImage:
                writeUncompressedFrames(output, ctx, inst, frameList, bulkdataURL);
                return;
            case CompressedMultiFrameImage:
                if (mediaType == MediaType.APPLICATION_OCTET_STREAM_TYPE)
                    writeDecompressedFrames(output, ctx, inst, frameList, bulkdataURL);
                else
                    writeCompressedFrames(output, ctx, inst, frameList, mediaType, bulkdataURL);
                return;
            case UncompressedSingleFrameImage:
                entity = new BulkdataOutput(ctx, inst, Tag.PixelData);
                break;
            case CompressedSingleFrameImage:
                entity = mediaType == MediaType.APPLICATION_OCTET_STREAM_TYPE
                        ? new DecompressPixelDataOutput(ctx, inst)
                        : new CompressedPixelDataOutput(ctx, inst);
                break;
            default:
                throw new AssertionError("Unexcepted object type: " + objectType);
        }
        OutputPart outputPart = output.addPart(entity, mediaType);
        outputPart.getHeaders().putSingle("Content-Location", bulkdataURL.append('1').toString());
    }

    private int[] adjustFrameList(int[] frameList, int numFrames) {
        int len = 0;
        for (int frame : frameList) {
            if (frame <= numFrames)
                frameList[len++] = frame;
        }
        if (len == frameList.length)
            return frameList;

        if (len == 0)
            return ByteUtils.EMPTY_INTS;

        responseStatus = Response.Status.PARTIAL_CONTENT;
        return Arrays.copyOf(frameList, len);
    }

    private void writeUncompressedFrames(MultipartRelatedOutput output, RetrieveContext ctx, InstanceLocations inst,
                                         int[] frameList, StringBuffer bulkdataURL) throws IOException {
        int length = bulkdataURL.length();
        uncompressedFramesOutput = new UncompressedFramesOutput(ctx, inst, frameList, spoolDirectory(frameList));
        for (int frame : frameList) {
            OutputPart outputPart = output.addPart(uncompressedFramesOutput, MediaType.APPLICATION_OCTET_STREAM_TYPE);
            bulkdataURL.setLength(length);
            bulkdataURL.append(frame);
            outputPart.getHeaders().putSingle("Content-Location", bulkdataURL.toString());
        }
    }

    private void writeCompressedFrames(MultipartRelatedOutput output, RetrieveContext ctx, InstanceLocations inst,
                                       int[] frameList, MediaType mediaType, StringBuffer bulkdataURL)
            throws IOException {
        int length = bulkdataURL.length();
        compressedFramesOutput = new CompressedFramesOutput(ctx, inst, frameList, spoolDirectory(frameList));
        for (int frame : frameList) {
            OutputPart outputPart = output.addPart(compressedFramesOutput, mediaType);
            bulkdataURL.setLength(length);
            bulkdataURL.append(frame);
            outputPart.getHeaders().putSingle("Content-Location", bulkdataURL.toString());
        }
    }

    private void writeDecompressedFrames(MultipartRelatedOutput output, RetrieveContext ctx, InstanceLocations inst,
                                         int[] frameList, StringBuffer bulkdataURL) throws IOException {
        int length = bulkdataURL.length();
        decompressFramesOutput = new DecompressFramesOutput(ctx, inst, frameList, spoolDirectory(frameList));
        for (int frame : frameList) {
            OutputPart outputPart = output.addPart(decompressFramesOutput, MediaType.APPLICATION_OCTET_STREAM_TYPE);
            bulkdataURL.setLength(length);
            bulkdataURL.append(frame);
            outputPart.getHeaders().putSingle("Content-Location", bulkdataURL.toString());
        }
    }

    private void writeCompressedMultiFrameImage(MultipartRelatedOutput output, RetrieveContext ctx,
                                                InstanceLocations inst, MediaType mediaType, StringBuffer bulkdataURL) {
        bulkdataURL.append("/frames/");
        int length = bulkdataURL.length();
        int numFrames = inst.getAttributes().getInt(Tag.NumberOfFrames, 1);
        compressedMFPixelDataOutput = new CompressedMFPixelDataOutput(ctx, inst, numFrames);
        for (int i = 1; i <= numFrames; i++) {
            OutputPart outputPart = output.addPart(compressedMFPixelDataOutput, mediaType);
            bulkdataURL.setLength(length);
            bulkdataURL.append(i);
            outputPart.getHeaders().putSingle("Content-Location", bulkdataURL.toString());
        }
    }

    private void writeBulkdata(MultipartRelatedOutput output, RetrieveContext ctx, InstanceLocations inst,
            int[] attributePath) {
        StreamingOutput entity = new BulkdataOutput(ctx, inst, attributePath);
        MediaType mediaType = selectedMediaTypes.get(inst.getSopInstanceUID());
        OutputPart outputPart = output.addPart(entity, mediaType);
        outputPart.getHeaders().putSingle("Content-Location", request.getRequestURL());
    }

    private void writeDICOM(MultipartRelatedOutput output, RetrieveContext ctx, InstanceLocations inst)  {
        output.addPart(new DicomObjectOutput(ctx, inst, acceptableTransferSyntaxes),
                MediaTypes.applicationDicomWithTransferSyntax(MediaTypeUtils.selectTransferSyntax(
                        acceptableTransferSyntaxes, inst.getLocations().get(0).getTransferSyntaxUID())));
    }

    private Object writeZIP(RetrieveContext ctx) {
        AttributesFormat pathFormat = new AttributesFormat(
                ctx.getLocalApplicationEntity().getAEExtensionNotNull(ArchiveAEExtension.class).wadoZIPEntryNameFormat());
        final Collection<InstanceLocations> insts = ctx.getMatches();
        return (StreamingOutput) out -> {
            try {
                Set<String> dirPaths = new HashSet<>();
                ZipOutputStream zip = new ZipOutputStream(out);
                try (DicomDirWriter dicomDirWriter = dicomDirWriter()) {
                    for (InstanceLocations inst : insts) {
                        String name = pathFormat.format(inst.getAttributes());
                        addDirEntries(zip, name, dirPaths);
                        zip.putNextEntry(new ZipEntry(name));
                        DicomObjectOutput output = new DicomObjectOutput(ctx, inst, acceptableZipTransferSyntaxes);
                        output.write(zip);
                        zip.closeEntry();
                        if (dicomDirWriter != null) {
                            addDicomDirRecords(dicomDirWriter, output.getFileMetaInformation(), inst.getAttributes(), toFileIDs(name));
                        }
                    }
                }
                if (dicomdirPath != null) {
                    zip.putNextEntry(new ZipEntry("DICOMDIR"));
                    Files.copy(dicomdirPath, zip);
                    zip.closeEntry();
                }
                zip.finish();
                zip.flush();
            } catch (Exception e) {
                throw new WebApplicationException(
                        errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
            }
        };
    }

    private void addDirEntries(ZipOutputStream zip, String name, Set<String> added) throws IOException {
        int endIndex = 0;
        int i;
        while ((i = name.indexOf('/', endIndex)) >= 0) {
            zip.putNextEntry(new ZipEntry(name.substring(0, endIndex = (i + 1))));
            zip.closeEntry();
        }
    }

    private void addDicomDirRecords(DicomDirWriter writer, Attributes fmi, Attributes dataset, String[] fileIDs)
            throws IOException {
        String pid = dataset.getString(Tag.PatientID, null);
        String styuid = dataset.getString(Tag.StudyInstanceUID, null);
        String seruid = dataset.getString(Tag.SeriesInstanceUID, null);

        if (pid == null) {
            dataset.setString(Tag.PatientID, VR.LO, pid = styuid);
        }
        Attributes patRec = writer.findPatientRecord(pid);
        if (patRec == null) {
            patRec = RECORD_FACTORY.createRecord(RecordType.PATIENT, null,
                    dataset, null, null);
            writer.addRootDirectoryRecord(patRec);
        }
        Attributes studyRec = writer.findStudyRecord(patRec, styuid);
        if (studyRec == null) {
            studyRec = RECORD_FACTORY.createRecord(RecordType.STUDY, null,
                    dataset, null, null);
            writer.addLowerDirectoryRecord(patRec, studyRec);
        }

        Attributes seriesRec = writer.findSeriesRecord(studyRec, seruid);
        if (seriesRec == null) {
            seriesRec = RECORD_FACTORY.createRecord(RecordType.SERIES, null,
                    dataset, null, null);
            writer.addLowerDirectoryRecord(studyRec, seriesRec);
        }

        Attributes instRec = RECORD_FACTORY.createRecord(dataset, fmi, fileIDs);
        writer.addLowerDirectoryRecord(seriesRec, instRec);
    }

    private static String[] toFileIDs(String filePath) {
        return StringUtils.split(truncateSuffix(filePath, ".dcm"), '/');
    }

    private static String truncateSuffix(String filePath, String suffix) {
        return filePath.endsWith(suffix) ? filePath.substring(0, filePath.length() - 4) : filePath;
    }

    private void writeMetadataXML(MultipartRelatedOutput output, final RetrieveContext ctx,
                                  final InstanceLocations inst) {
        output.addPart(
                (StreamingOutput) out -> {
                        try {
                            SAXTransformer.getSAXWriter(new StreamResult(out)).write(loadMetadata(ctx, inst));
                        } catch (Exception e) {
                            throw new WebApplicationException(
                                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
                        }
                },
                MediaTypes.APPLICATION_DICOM_XML_TYPE);

    }

    private Object writeMetadataJSON(final RetrieveContext ctx) {
        final Collection<InstanceLocations> insts = ctx.getMatches();
        return (StreamingOutput) out -> {
                try {
                    JsonGenerator gen = Json.createGenerator(out);
                    JSONWriter writer = ctx.getArchiveAEExtension().encodeAsJSONNumber(new JSONWriter(gen));
                    gen.writeStartArray();
                    for (InstanceLocations inst : insts)
                        writer.write(loadMetadata(ctx, inst));
                    gen.writeEnd();
                    gen.flush();
                } catch (Exception e) {
                    throw new WebApplicationException(
                            errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
                }
        };
    }

    private Attributes loadMetadata(RetrieveContext ctx, InstanceLocations inst) throws IOException {
        Attributes metadata = inst.isContainsMetadata() ? inst.getAttributes() : service.loadMetadata(ctx, inst);
        StringBuffer sb = device.getDeviceExtension(ArchiveDeviceExtension.class).remapRetrieveURL(request);
        sb.setLength(sb.lastIndexOf("/metadata"));
        mkInstanceURL(sb, inst);
        if (ctx.getMetadataFilter() != null)
            metadata = new Attributes(metadata, ctx.getMetadataFilter().getSelection());
        setBulkdataURI(metadata, sb.toString());
        return metadata;
    }

    private void setBulkdataURI(Attributes attrs, String retrieveURL) {
        try {
            attrs.accept(new Attributes.ItemPointerVisitor() {
                @Override
                public boolean visit(Attributes attrs, int tag, VR vr, Object value) {
                    if (value instanceof BulkData) {
                        BulkData bulkData = (BulkData) value;
                        if (tag == Tag.PixelData && itemPointers.isEmpty()) {
                            bulkData.setURI(retrieveURL);
                        } else {
                            bulkData.setURI(retrieveURL + "/bulkdata"
                                    + DicomInputStream.toAttributePath(itemPointers, tag));
                        }
                    }
                    return true;
                }
            }, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void mkInstanceURL(StringBuffer sb, InstanceLocations inst) {
        if (sb.lastIndexOf("/instances/") < 0) {
            if (sb.lastIndexOf("/series/") < 0) {
                sb.append("/series/").append(inst.getAttributes().getString(Tag.SeriesInstanceUID));
            }
            sb.append("/instances/").append(inst.getSopInstanceUID());
        }
    }

    public static final class AttributePath {
        final int[] path;

        public AttributePath(String s) {
            String[] split = StringUtils.split(s, '/');
            if ((split.length & 1) == 0)
                throw new IllegalArgumentException(s);

            int[] path = new int[split.length];
            for (int i = 0; i < split.length; i++) {
                path[i] = Integer.parseInt(split[i], (i & 1) == 0 ? 16 : 10);
            }
            this.path = path;
        }
    }

    public static class FrameList {
        final int[] frames;

        public FrameList(String s) {
            String[] split = StringUtils.split(s, ',');
            int[] frames = new int[split.length];
            for (int i = 0; i < split.length; i++) {
                if ((frames[i] = Integer.parseInt(split[i])) <= 0)
                    throw new IllegalArgumentException(s);
            }
            this.frames = frames;
        }
    }

    private java.nio.file.Path spoolDirectoryRoot() throws IOException {
        return  Files.createDirectories(Paths.get(StringUtils.replaceSystemProperties(
                device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class).getWadoSpoolDirectory())));
    }

    private java.nio.file.Path spoolDirectory(int[] frameList) throws IOException {
        for (int i = 1; i < frameList.length; i++) {
            if (frameList[i-1] > frameList[i])
                return (spoolDirectory = Files.createTempDirectory(spoolDirectoryRoot(), null));
        }
        return null;
    }

    private DicomDirWriter dicomDirWriter() throws IOException {
        if (Boolean.parseBoolean(dicomdir)) {
            spoolDirectory = Files.createTempDirectory(spoolDirectoryRoot(), null);
            dicomdirPath = Files.createFile(spoolDirectory.resolve("DICOMDIR"));
            File file = dicomdirPath.toFile();
            DicomDirWriter.createEmptyDirectory(file, UIDUtils.createUID(), null, null, null);
            return DicomDirWriter.open(file);
        }
        return null;
    }

    private void purgeSpoolDirectory() {
        if (spoolDirectory == null)
            return;

        try {
            try (DirectoryStream<java.nio.file.Path> dir = Files.newDirectoryStream(spoolDirectory)) {
                for (java.nio.file.Path file : dir) {
                    try {
                        Files.delete(file);
                    } catch (IOException e) {
                        LOG.warn("Failed to delete frame spool file {}", file, e);
                    }
                }
            }
            Files.delete(spoolDirectory);
        } catch (IOException e) {
            LOG.warn("Failed to purge spool directory {}", spoolDirectory, e);
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

    public static final class Viewport {
        private final int rows;
        private final int columns;
        private final float[] region;
        public Viewport(String s) {
            String[] ss = StringUtils.split(s, ',');
            switch(ss.length) {
                case 2:
                    region = null;
                    break;
                case 6:
                    region = new float[]{0, 0, Float.NaN, Float.NaN};
                    for (int i = 2; i < 6; i++) {
                        if (!ss[i].isEmpty())
                            region[i-2] = Float.parseFloat(ss[i]);
                    }
                default:
                    throw new IllegalArgumentException(s);
            }
            columns = Integer.parseUnsignedInt(ss[0]);
            rows = Integer.parseUnsignedInt(ss[1]);
        }

        public Rectangle getSourceRegion(int rows, int columns) {
            if (region == null)
                return null;

            Rectangle result = new Rectangle();
            result.x = (int) Math.abs(region[0]);
            result.y = (int) Math.abs(region[1]);
            result.width = Float.isNaN(region[2])
                    ? columns - result.x
                    : (int) Math.abs(region[2]);
            result.height = Float.isNaN(region[3])
                    ? rows - result.y
                    : (int) Math.abs(region[3]);
            return result;
        }

        public boolean isThumbnailDefault() {
            return rows == 64 && columns == 64;
        }
    }

    public static final class Windowing {
        private final float center;
        private final float width;
        private final String voilutFunction;

        public Windowing(String s) {
            String[] ss = StringUtils.split(s, ',');
            if (ss.length != 3)
                throw new IllegalArgumentException(s);

            center = Float.parseFloat(ss[0]);
            width = Float.parseFloat(ss[1]);
            if (width <= 0.f)
                throw new IllegalArgumentException(s);
            switch (ss[2]) {
                case "linear":
                    if (width < 1.f)
                        throw new IllegalArgumentException(s);
                    voilutFunction = "LINEAR";
                    break;
                case "linear-exact":
                    voilutFunction = "LINEAR_EXACT";
                    break;
                case "sigmoid":
                    voilutFunction = "SIGMOID";
                    break;
                default:
                    throw new IllegalArgumentException(s);
            }
        }
    }

    private RenderedImageOutput renderImage(RetrieveContext ctx, InstanceLocations inst, MediaType mimeType,
            int frame, Windowing windowing, Viewport viewport) {
        Attributes attrs = inst.getAttributes();
        DicomImageReadParam readParam = new DicomImageReadParam();
        readParam.setPresentationState(presentationState);
        if (windowing != null) {
            readParam.setWindowCenter(windowing.center);
            readParam.setWindowWidth(windowing.width);
        }
        int rows = 0;
        int columns = 0;
        if (viewport != null) {
            rows = viewport.rows;
            columns = viewport.columns;
            readParam.setSourceRegion(viewport.getSourceRegion(
                    attrs.getInt(Tag.Rows, 1),
                    attrs.getInt(Tag.Columns, 1)));
        }
        return new RenderedImageOutput(ctx, inst, readParam, rows, columns, mimeType, imageQuality,
                iccProfile(mimeType), frame);
    }

    private ICCProfile.Option iccProfile(MediaType mimeType) {
        if (iccprofile == null) {
            return ICCProfile.Option.none;
        }
        ICCProfile.Option iccProfile = ICCProfile.Option.valueOf(iccprofile);
        if (iccProfile != ICCProfile.Option.no
                && !MediaTypes.equalsIgnoreParameters(mimeType, MediaTypes.IMAGE_JPEG_TYPE)) {
            throw new WebApplicationException(errResponseAsTextPlain(
                    "Cannot embed ICC profile into " + mimeType,
                    Response.Status.BAD_REQUEST));
        }
        return iccProfile;
    }
}
