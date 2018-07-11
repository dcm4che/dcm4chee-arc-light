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
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.SAXTransformer;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.util.AttributesFormat;
import org.dcm4che3.util.SafeClose;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.AttributeSet;
import org.dcm4chee.arc.qmgt.HttpServletRequestInfo;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveEnd;
import org.dcm4chee.arc.retrieve.RetrieveService;
import org.dcm4chee.arc.retrieve.RetrieveStart;
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
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.CompletionCallback;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.*;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
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

    private List<MediaType> acceptableMediaTypes;
    private List<MediaType> acceptableMultipartRelatedMediaTypes;
    private Collection<String> acceptableTransferSyntaxes;
    private Collection<String> acceptableZipTransferSyntaxes;
    private Map<String, MediaType> selectedMediaTypes;
    private CompressedMFPixelDataOutput compressedMFPixelDataOutput;
    private UncompressedFramesOutput uncompressedFramesOutput;
    private CompressedFramesOutput compressedFramesOutput;
    private DecompressFramesOutput decompressFramesOutput;
    private Response.Status responseStatus;
    private java.nio.file.Path spoolDirectory;

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
        logRequest();
        checkAET();
        Output output = dicomOrBulkdataOrZIP();
        retrieve("retrieveStudy", studyUID, null, null, null, null, null, ar, output);
    }

    @GET
    @Path("/studies/{studyUID}/metadata")
    public void retrieveStudyMetadata(
            @PathParam("studyUID") String studyUID,
            @QueryParam("includefields") String includefields,
            @Suspended AsyncResponse ar) {
        logRequest();
        checkAET();
        Output output = metadataJSONorXML();
        retrieve("retrieveStudyMetadata", studyUID, null, null, null, null, includefields, ar, output);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}")
    public void retrieveSeries(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @Suspended AsyncResponse ar) {
        logRequest();
        checkAET();
        Output output = dicomOrBulkdataOrZIP();
        retrieve("retrieveSeries", studyUID, seriesUID, null, null, null, null, ar, output);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}/metadata")
    public void retrieveSeriesMetadata(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @QueryParam("includefields") String includefields,
            @Suspended AsyncResponse ar) {
        logRequest();
        checkAET();
        Output output = metadataJSONorXML();
        retrieve("retrieveSeriesMetadata", studyUID, seriesUID, null, null, null, includefields, ar, output);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}/instances/{objectUID}")
    public void retrieveInstance(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @PathParam("objectUID") String objectUID,
            @Suspended AsyncResponse ar) {
        logRequest();
        checkAET();
        Output output = dicomOrBulkdataOrZIP();
        retrieve("retrieveInstance", studyUID, seriesUID, objectUID, null, null, null, ar, output);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}/instances/{objectUID}/metadata")
    public void retrieveInstanceMetadata(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @PathParam("objectUID") String objectUID,
            @Suspended AsyncResponse ar) {
        logRequest();
        checkAET();
        Output output = metadataJSONorXML();
        retrieve("retrieveInstanceMetadata", studyUID, seriesUID, objectUID, null, null, null, ar, output);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}/instances/{objectUID}/bulkdata/{attributePath:.+}")
    public void retrieveBulkdata(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @PathParam("objectUID") String objectUID,
            @PathParam("attributePath") @ValidValueOf(type = AttributePath.class) String attributePath,
            @Suspended AsyncResponse ar) {
        logRequest();
        checkAET();
        checkMultipartRelatedAcceptable();
        retrieve("retrieveBulkdata", studyUID, seriesUID, objectUID,
                null, new AttributePath(attributePath).path, null, ar, Output.BULKDATA_PATH);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}/instances/{objectUID}/frames/{frameList}")
    public void retrieveFrames(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @PathParam("objectUID") String objectUID,
            @PathParam("frameList") @ValidValueOf(type = FrameList.class) String frameList,
            @Suspended AsyncResponse ar) {
        logRequest();
        checkAET();
        checkMultipartRelatedAcceptable();
        retrieve("retrieveFrames", studyUID, seriesUID, objectUID,
                new FrameList(frameList).frames, null, null, ar, Output.BULKDATA_FRAME);
    }

/*
    @GET
    @Path("/studies/{studyUID}/rendered")
    @Produces("multipart/related")
    public void retrieveRenderedStudy(
            @PathParam("studyUID") String studyUID,
            @Suspended AsyncResponse ar) {
        retrieve("retrieveRenderedStudy", null, null, null, null, ar, Output.RENDER);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}/rendered")
    @Produces("multipart/related")
    public void retrieveRenderedSeries(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @Suspended AsyncResponse ar) {
        retrieve("retrieveRenderedSeries", seriesUID, null, null, null, ar, Output.RENDER);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}/instances/{objectUID}/rendered")
    @Produces("multipart/related")
    public void retrieveRenderedInstance(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @PathParam("objectUID") String objectUID,
            @Suspended AsyncResponse ar) {
        retrieve("retrieveRenderedInstance", seriesUID, objectUID, null, null, ar, Output.RENDER);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}/instances/{objectUID}/frames/{frameList}/rendered")
    @Produces("multipart/related")
    public void retrieveRenderedFrames(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @PathParam("objectUID") String objectUID,
            @PathParam("frameList") @ValidValueOf(type = FrameList.class) String frameList,
            @Suspended AsyncResponse ar) {
        retrieve("retrieveRenderedFrames", seriesUID, objectUID,
                new FrameList(frameList).frames, null, ar, Output.RENDER_FRAME);
    }
*/

    private void logRequest() {
        LOG.info("Process GET {} from {}@{}", request.getRequestURI(), request.getRemoteUser(), request.getRemoteHost());
    }

    private void initAcceptableMediaTypes() {
        if (accept != null && !accept.isEmpty()) {
            headers.getRequestHeaders().put("Accept",
                    accept.stream().flatMap(s -> Stream.of(StringUtils.split(s, ',')))
                            .collect(Collectors.toList()));
        }
        acceptableMediaTypes = headers.getAcceptableMediaTypes();
        acceptableMultipartRelatedMediaTypes = acceptableMediaTypes.stream()
                .map(m -> MediaTypes.getMultiPartRelatedType(m))
                .filter(t -> t != null)
                .collect(Collectors.toList());
        acceptableTransferSyntaxes = transferSyntaxesOf(acceptableMultipartRelatedMediaTypes.stream()
                .filter(m -> m.isCompatible(MediaTypes.APPLICATION_DICOM_JSON_TYPE)));
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

    private Output dicomOrBulkdataOrZIP() {
        initAcceptableMediaTypes();
        if (acceptableMultipartRelatedMediaTypes.isEmpty() && acceptableZipTransferSyntaxes.isEmpty()) {
            throw new WebApplicationException(Response.Status.NOT_ACCEPTABLE);
        }
        return selectMediaType(acceptableMultipartRelatedMediaTypes, MediaTypes.APPLICATION_DICOM_TYPE).isPresent()
                ? Output.DICOM
                : acceptableZipTransferSyntaxes.isEmpty() ? Output.BULKDATA : Output.ZIP;
    }

    private Output metadataJSONorXML() {
        initAcceptableMediaTypes();
        MediaType mediaType = selectMediaType(acceptableMediaTypes,
                MediaTypes.APPLICATION_DICOM_JSON_TYPE,
                MediaType.APPLICATION_JSON_TYPE)
                .orElseGet(() ->
                        selectMediaType(acceptableMultipartRelatedMediaTypes, MediaTypes.APPLICATION_DICOM_XML_TYPE)
                                .orElseThrow(() -> new WebApplicationException(Response.Status.NOT_ACCEPTABLE)));
        return mediaType == MediaTypes.APPLICATION_DICOM_XML_TYPE ? Output.METADATA_XML : Output.METADATA_JSON;
    }

    private static Optional<MediaType> selectMediaType(List<MediaType> list, MediaType... mediaTypes) {
        return list.stream()
                .map(entry -> Stream.of(mediaTypes).filter(mediaType -> mediaType.isCompatible(entry)).findFirst())
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    private void retrieve(String method, String studyUID, String seriesUID, String objectUID, int[] frameList,
                          int[] attributePath, String includefields, AsyncResponse ar, Output output) {
        try {
            // @Inject does not work:
            // org.jboss.resteasy.spi.LoggableFailure: Unable to find contextual data of type: javax.servlet.http.HttpServletRequest
            // s. https://issues.jboss.org/browse/RESTEASY-903
            HttpServletRequest request = ResteasyProviderFactory.getContextData(HttpServletRequest.class);
            final RetrieveContext ctx = service.newRetrieveContextWADO(HttpServletRequestInfo.valueOf(request), aet, studyUID, seriesUID, objectUID);
            if (output.isMetadata()) {
                ctx.setObjectType(null);
                ctx.setMetadataFilter(getMetadataFilter(includefields));
            }

            if (request.getHeader(HttpHeaders.IF_MODIFIED_SINCE) == null && request.getHeader(HttpHeaders.IF_UNMODIFIED_SINCE) == null
                    && request.getHeader(HttpHeaders.IF_MATCH) == null && request.getHeader(HttpHeaders.IF_NONE_MATCH) == null) {
                buildResponse(method, frameList, attributePath, ar, output, ctx, null);
                return;
            }

            Date lastModified = service.getLastModified(ctx);
            if (lastModified == null)
                throw new WebApplicationException(errResponse("Last Modified date is null.", Response.Status.NOT_FOUND));
            Response.ResponseBuilder respBuilder = evaluatePreConditions(lastModified);

            if (respBuilder == null)
                buildResponse(method, frameList, attributePath, ar, output, ctx, lastModified);
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

    private void buildResponse(String method, int[] frameList, int[] attributePath, AsyncResponse ar, Output output,
                               final RetrieveContext ctx, Date lastModified) throws IOException {
        service.calculateMatches(ctx);
        LOG.info("{}: {} Matches", method, ctx.getNumberOfMatches());
        if (ctx.getNumberOfMatches() == 0)
            throw new WebApplicationException(errResponse("No matches found.", Response.Status.NOT_FOUND));
//            Collection<InstanceLocations> notAccessable = service.removeNotAccessableMatches(ctx);
        Collection<InstanceLocations> notAccepted = output.removeNotAcceptedMatches(this, ctx);
        if (ctx.getMatches().isEmpty()) {
            Response errResp = notAccepted.isEmpty()
                    ? errResponse("No matches found.", Response.Status.NOT_FOUND)
                    : errResponse("Not accepted instances present.", Response.Status.NOT_ACCEPTABLE);
            throw new WebApplicationException(errResp);
        }

        if (lastModified == null)
            lastModified = service.getLastModifiedFromMatches(ctx);

        retrieveStart.fire(ctx);
        ar.register((CompletionCallback) throwable -> {
                SafeClose.close(compressedMFPixelDataOutput);
                SafeClose.close(uncompressedFramesOutput);
                SafeClose.close(compressedFramesOutput);
                SafeClose.close(decompressFramesOutput);
                purgeSpoolDirectory();
                ctx.setException(throwable);
                retrieveEnd.fire(ctx);
        });
        responseStatus = notAccepted.isEmpty() ? Response.Status.OK : Response.Status.PARTIAL_CONTENT;
        Object entity = output.entity(this, ctx, frameList, attributePath);
        ar.resume(output.adjustType(Response.status(responseStatus).lastModified(lastModified)
                .tag(String.valueOf(lastModified.hashCode())).entity(entity)).build());
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

    private enum Output {
        DICOM {
            @Override
            public Collection<InstanceLocations> removeNotAcceptedMatches(WadoRS wadoRS, RetrieveContext ctx) {
                return Collections.EMPTY_LIST;
            }
            @Override
            protected void addPart(MultipartRelatedOutput output, WadoRS wadoRS, RetrieveContext ctx,
                                   InstanceLocations inst, int[] frameList, int[] attributePath) {
                wadoRS.writeDICOM(output, ctx, inst);
            }
        },
        ZIP {
            @Override
            public Collection<InstanceLocations> removeNotAcceptedMatches(WadoRS wadoRS, RetrieveContext ctx) {
                return Collections.EMPTY_LIST;
            }
            @Override
            public Object entity(WadoRS wadoRS, RetrieveContext ctx, int[] frameList, int[] attributePath) {
                return wadoRS.writeZIP(ctx);
            }

            @Override
            public Response.ResponseBuilder adjustType(Response.ResponseBuilder builder) {
                builder.type(MediaTypes.APPLICATION_ZIP_TYPE);
                return builder;
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
            protected MediaType[] mediaTypesFor(InstanceLocations match, ObjectType objectType) {
                return objectType.getPixelDataContentTypes(match);
            }
            @Override
            protected void addPart(MultipartRelatedOutput output, WadoRS wadoRS, RetrieveContext ctx,
                                   InstanceLocations inst, int[] frameList, int[] attributePath) throws IOException {
                wadoRS.writeFrames(output, ctx, inst, frameList);
            }
        },
        BULKDATA_PATH {
            @Override
            protected MediaType[] mediaTypesFor(InstanceLocations match) {
                return new MediaType[] { MediaType.APPLICATION_OCTET_STREAM_TYPE };
            }
            @Override
            protected void addPart(MultipartRelatedOutput output, WadoRS wadoRS, RetrieveContext ctx,
                                   InstanceLocations inst, int[] frameList, int[] attributePath) {
                wadoRS.writeBulkdata(output, ctx, inst, attributePath);
            }
        },
        RENDER {
            @Override
            protected void addPart(MultipartRelatedOutput output, WadoRS wadoRS, RetrieveContext ctx,
                                   InstanceLocations inst, int[] frameList, int[] attributePath) throws IOException {
                super.addPart(output, wadoRS, ctx, inst, frameList, attributePath);
            }
        },
        RENDER_FRAME {
            @Override
            protected void addPart(MultipartRelatedOutput output, WadoRS wadoRS, RetrieveContext ctx,
                                   InstanceLocations inst, int[] frameList, int[] attributePath) throws IOException {
                super.addPart(output, wadoRS, ctx, inst, frameList, attributePath);
            }
        },
        METADATA_XML {
            @Override
            public Collection<InstanceLocations> removeNotAcceptedMatches(WadoRS wadoRS, RetrieveContext ctx) {
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
        METADATA_JSON {
            @Override
            public Collection<InstanceLocations> removeNotAcceptedMatches(WadoRS wadoRS, RetrieveContext ctx) {
                return Collections.EMPTY_LIST;
            }
            @Override
            public Object entity(WadoRS wadoRS, RetrieveContext ctx, int[] frameList, int[] attributePath) {
                return wadoRS.writeMetadataJSON(ctx);
            }
            @Override
            public boolean isMetadata() {
                return true;
            }

            @Override
            public Response.ResponseBuilder adjustType(Response.ResponseBuilder builder) {
                builder.type(MediaTypes.APPLICATION_DICOM_JSON_TYPE);
                return builder;
            }
        };

        public Object entity(WadoRS wadoRS, RetrieveContext ctx, int[] frameList, int[] attributePath)
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

        public Collection<InstanceLocations> removeNotAcceptedMatches(WadoRS wadoRS, RetrieveContext ctx) {
            Collection<InstanceLocations> matches = ctx.getMatches();
            Collection<InstanceLocations> notAcceptable = new ArrayList<>(matches.size());
            Map<String,MediaType> selectedMediaTypes = new HashMap<>(matches.size() * 4 / 3);
            Iterator<InstanceLocations> iter = matches.iterator();
            while (iter.hasNext()) {
                InstanceLocations match = iter.next();
                MediaType[] mediaTypes = mediaTypesFor(match);
                if (mediaTypes == null) {
                    iter.remove();
                    continue;
                }
                Optional<MediaType> mediaType =
                        wadoRS.selectMediaType(wadoRS.acceptableMultipartRelatedMediaTypes, mediaTypes);
                if (mediaType.isPresent()) {
                    selectedMediaTypes.put(match.getSopInstanceUID(), mediaType.get());
                } else {
                    iter.remove();
                    notAcceptable.add(match);
                }
            }
            wadoRS.selectedMediaTypes = selectedMediaTypes;
            return notAcceptable;
        }

        protected MediaType[] mediaTypesFor(InstanceLocations match) {
            return mediaTypesFor(match, ObjectType.objectTypeOf(match, null));
        }

        protected MediaType[] mediaTypesFor(InstanceLocations match, ObjectType objectType) {
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

        public Response.ResponseBuilder adjustType(Response.ResponseBuilder builder) {
            return builder;
        }
    }

    private void writeBulkdata(MultipartRelatedOutput output, RetrieveContext ctx, InstanceLocations inst) {
        MediaType mediaType = selectedMediaTypes.get(inst.getSopInstanceUID());
        StringBuffer bulkdataURL = request.getRequestURL();
        mkInstanceURL(bulkdataURL, inst);
        StreamingOutput entity;
        ObjectType objectType = ObjectType.objectTypeOf(inst, null);
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
        frameList = adjustFrameList(frameList, numFrames);
        MediaType mediaType = selectedMediaTypes.get(inst.getSopInstanceUID());
        StringBuffer bulkdataURL = request.getRequestURL();
        mkInstanceURL(bulkdataURL, inst);
        StreamingOutput entity;
        ObjectType objectType = ObjectType.objectTypeOf(inst, null);
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
        outputPart.getHeaders().putSingle("Content-Location", bulkdataURL.toString());
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
            throw new WebApplicationException(errResponse("Frame length is zero.", Response.Status.NOT_FOUND));

        responseStatus = Response.Status.PARTIAL_CONTENT;
        return Arrays.copyOf(frameList, len);
    }

    private void writeUncompressedFrames(MultipartRelatedOutput output, RetrieveContext ctx, InstanceLocations inst,
                                         int[] frameList, StringBuffer bulkdataURL) throws IOException {
        bulkdataURL.append("/frames/");
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
        bulkdataURL.append("/frames/");
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
        bulkdataURL.append("/frames/");
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
        OutputPart outputPart = output.addPart(entity, MediaType.APPLICATION_OCTET_STREAM_TYPE);
        outputPart.getHeaders().putSingle("Content-Location", request.getRequestURL());
    }

    private void writeDICOM(MultipartRelatedOutput output, RetrieveContext ctx, InstanceLocations inst)  {
        DicomObjectOutput entity = new DicomObjectOutput(ctx, inst, acceptableTransferSyntaxes);
        output.addPart(entity, MediaTypes.APPLICATION_DICOM_TYPE);
    }

    private Object writeZIP(RetrieveContext ctx) {
        AttributesFormat pathFormat = new AttributesFormat(
                ctx.getLocalApplicationEntity().getAEExtensionNotNull(ArchiveAEExtension.class).wadoZIPEntryNameFormat());
        final Collection<InstanceLocations> insts = ctx.getMatches();
        return (StreamingOutput) out -> {
            try {
                Set<String> dirPaths = new HashSet<>();
                ZipOutputStream zip = new ZipOutputStream(out);
                for (InstanceLocations inst : insts) {
                    String name = pathFormat.format(inst.getAttributes());
                    addDirEntries(zip, name, dirPaths);
                    zip.putNextEntry(new ZipEntry(name));
                    new DicomObjectOutput(ctx, inst, acceptableZipTransferSyntaxes).write(zip);
                    zip.closeEntry();
                }
                zip.finish();
                zip.flush();
            } catch (Exception e) {
                throw new WebApplicationException(errResponseAsTextPlain(e));
            }
        };
    }

    private void addDirEntries(ZipOutputStream zip, String name, Set<String> added) throws IOException {
        try {
            IntStream.range(0, name.length())
                    .filter(i -> name.charAt(i) == '/')
                    .mapToObj(i -> name.substring(0, i + 1))
                    .filter(dirPath -> added.add(dirPath))
                    .forEach(dirPath -> {
                        try {
                            zip.putNextEntry(new ZipEntry(dirPath));
                            zip.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException)
                throw (IOException) e.getCause();
        }
    }

    private void writeMetadataXML(MultipartRelatedOutput output, final RetrieveContext ctx,
                                  final InstanceLocations inst) {
        output.addPart(
                (StreamingOutput) out -> {
                        try {
                            SAXTransformer.getSAXWriter(new StreamResult(out)).write(loadMetadata(ctx, inst));
                        } catch (Exception e) {
                            throw new WebApplicationException(errResponseAsTextPlain(e));
                        }
                },
                MediaTypes.APPLICATION_DICOM_XML_TYPE);

    }

    private Object writeMetadataJSON(final RetrieveContext ctx) {
        final Collection<InstanceLocations> insts = ctx.getMatches();
        return (StreamingOutput) out -> {
                try {
                    JsonGenerator gen = Json.createGenerator(out);
                    JSONWriter writer = new JSONWriter(gen);
                    gen.writeStartArray();
                    for (InstanceLocations inst : insts)
                        writer.write(loadMetadata(ctx, inst));
                    gen.writeEnd();
                    gen.flush();
                } catch (Exception e) {
                    throw new WebApplicationException(errResponseAsTextPlain(e));
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
            final List<ItemPointer> itemPointers = new ArrayList<>(4);
            attrs.accept(new Attributes.SequenceVisitor() {
                @Override
                public void startItem(int sqTag, int itemIndex) {
                    itemPointers.add(new ItemPointer(sqTag, itemIndex));
                }

                @Override
                public void endItem() {
                    itemPointers.remove(itemPointers.size()-1);
                }

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
        return Response.status(status).entity("{\"errorMessage\":\"" + errorMessage + "\"}").build();
    }

    private Response errResponseAsTextPlain(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String exceptionAsString = sw.toString();
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(exceptionAsString).type("text/plain").build();
    }
}
