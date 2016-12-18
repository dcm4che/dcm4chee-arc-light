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
 * Portions created by the Initial Developer are Copyright (C) 2013
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
import org.dcm4che3.io.*;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.util.SafeClose;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.MetadataFilter;
import org.dcm4chee.arc.retrieve.*;
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
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Mar 2016
 */
@RequestScoped
@Path("aets/{AETitle}/rs")
public class WadoRS {

    private static final Logger LOG = LoggerFactory.getLogger(WadoRS.class);
    private static final String JBOSS_SERVER_TEMP = "${jboss.server.temp}";

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

    private Collection<String> acceptableTransferSyntaxes;
    private List<MediaType> acceptableMediaTypes;
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
    @Produces("multipart/related;type=application/dicom")
    public void retrieveStudy(
            @PathParam("studyUID") String studyUID,
            @Suspended AsyncResponse ar) {
        retrieve("retrieveStudy", studyUID, null, null, null, null, null, ar, Output.DICOM);
    }

    @GET
    @Path("/studies/{studyUID}")
    @Produces("multipart/related")
    public void retrieveStudyBulkdata(
            @PathParam("studyUID") String studyUID,
            @Suspended AsyncResponse ar) {
        retrieve("retrieveStudyBulkdata", studyUID, null, null, null, null, null, ar, Output.BULKDATA);
    }

    @GET
    @Path("/studies/{studyUID}/metadata")
    @Produces("application/json")
    public void retrieveStudyMetadataAsJSON(
            @PathParam("studyUID") String studyUID,
            @QueryParam("includefields") String includefields,
            @Suspended AsyncResponse ar) {
        retrieve("retrieveStudyMetadataAsJSON", studyUID, null, null, null, null, includefields, ar, Output.METADATA_JSON);
    }

    @GET
    @Path("/studies/{studyUID}/metadata")
    @Produces("multipart/related;type=application/dicom+xml")
    public void retrieveStudyMetadataAsXML(
            @PathParam("studyUID") String studyUID,
            @QueryParam("includefields") String includefields,
            @Suspended AsyncResponse ar) {
        retrieve("retrieveStudyMetadataAsXML", studyUID, null, null, null, null, includefields, ar, Output.METADATA_XML);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}")
    @Produces("multipart/related;type=application/dicom")
    public void retrieveSeries(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @Suspended AsyncResponse ar) {
        retrieve("retrieveSeries", studyUID, seriesUID, null, null, null, null, ar, Output.DICOM);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}")
    @Produces("multipart/related")
    public void retrieveSeriesBulkdata(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @Suspended AsyncResponse ar) {
        retrieve("retrieveSeriesBulkdata", studyUID, seriesUID, null, null, null, null, ar, Output.BULKDATA);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}/metadata")
    @Produces("application/json")
    public void retrieveSeriesMetadataAsJSON(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @QueryParam("includefields") String includefields,
            @Suspended AsyncResponse ar) {
        retrieve("retrieveSeriesMetadataAsJSON", studyUID, seriesUID, null, null, null, includefields, ar, Output.METADATA_JSON);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}/metadata")
    @Produces("multipart/related;type=application/dicom+xml")
    public void retrieveSeriesMetadataAsXML(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @QueryParam("includefields") String includefields,
            @Suspended AsyncResponse ar) {
        retrieve("retrieveSeriesMetadataAsXML", studyUID, seriesUID, null, null, null, includefields, ar, Output.METADATA_XML);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}/instances/{objectUID}")
    @Produces("multipart/related;type=application/dicom")
    public void retrieveInstance(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @PathParam("objectUID") String objectUID,
            @Suspended AsyncResponse ar) {
        retrieve("retrieveInstance", studyUID, seriesUID, objectUID, null, null, null, ar, Output.DICOM);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}/instances/{objectUID}")
    @Produces("multipart/related")
    public void retrieveInstanceBulkdata(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @PathParam("objectUID") String objectUID,
            @Suspended AsyncResponse ar) {
        retrieve("retrieveInstanceBulkdata", studyUID, seriesUID, objectUID, null, null, null, ar, Output.BULKDATA);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}/instances/{objectUID}/bulkdata/{attributePath:.+}")
    @Produces("multipart/related")
    public void retrieveBulkdata(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @PathParam("objectUID") String objectUID,
            @PathParam("attributePath") @ValidValueOf(type = AttributePath.class) String attributePath,
            @Suspended AsyncResponse ar) {
        retrieve("retrieveBulkdata", studyUID, seriesUID, objectUID,
                null, new AttributePath(attributePath).path, null, ar, Output.BULKDATA_PATH);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}/instances/{objectUID}/frames/{frameList}")
    @Produces("multipart/related")
    public void retrieveFrames(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @PathParam("objectUID") String objectUID,
            @PathParam("frameList") @ValidValueOf(type = FrameList.class) String frameList,
            @Suspended AsyncResponse ar) {
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
        retrieve("retrieveRenderedStudy", studyUID, null, null, null, null, ar, Output.RENDER);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}/rendered")
    @Produces("multipart/related")
    public void retrieveRenderedSeries(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @Suspended AsyncResponse ar) {
        retrieve("retrieveRenderedSeries", studyUID, seriesUID, null, null, null, ar, Output.RENDER);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}/instances/{objectUID}/rendered")
    @Produces("multipart/related")
    public void retrieveRenderedInstance(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @PathParam("objectUID") String objectUID,
            @Suspended AsyncResponse ar) {
        retrieve("retrieveRenderedInstance", studyUID, seriesUID, objectUID, null, null, ar, Output.RENDER);
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
        retrieve("retrieveRenderedFrames", studyUID, seriesUID, objectUID,
                new FrameList(frameList).frames, null, ar, Output.RENDER_FRAME);
    }
*/

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}/instances/{objectUID}/metadata")
    @Produces("application/json")
    public void retrieveInstanceMetadataAsJSON(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @PathParam("objectUID") String objectUID,
            @QueryParam("includefields") String includefields,
            @Suspended AsyncResponse ar) {
        retrieve("retrieveInstanceMetadataAsJSON", studyUID, seriesUID, objectUID, null, null, includefields, ar,
                Output.METADATA_JSON);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}/instances/{objectUID}/metadata")
    @Produces("multipart/related;type=application/dicom+xml")
    public void retrieveInstanceMetadataAsXML(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @PathParam("objectUID") String objectUID,
            @QueryParam("includefields") String includefields,
            @Suspended AsyncResponse ar) {
        retrieve("retrieveInstanceMetadataAsXML", studyUID, seriesUID, objectUID, null, null, includefields, ar, Output.METADATA_XML);
    }

    private void retrieve(String method, String studyUID, String seriesUID, String objectUID, int[] frameList,
                          int[] attributePath, String includefields, AsyncResponse ar, Output output) {
        LOG.info("Process GET {} from {}@{}", this, request.getRemoteUser(), request.getRemoteHost());
        try {
            checkAET();
            // @Inject does not work:
            // org.jboss.resteasy.spi.LoggableFailure: Unable to find contextual data of type: javax.servlet.http.HttpServletRequest
            // s. https://issues.jboss.org/browse/RESTEASY-903
            HttpServletRequest request = ResteasyProviderFactory.getContextData(HttpServletRequest.class);
            final RetrieveContext ctx = service.newRetrieveContextWADO(request, aet, studyUID, seriesUID, objectUID);
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
                throw new WebApplicationException(Response.Status.NOT_FOUND);
            Response.ResponseBuilder respBuilder = evaluatePreConditions(lastModified);

            if (respBuilder == null)
                buildResponse(method, frameList, attributePath, ar, output, ctx, lastModified);
            else
                ar.resume(respBuilder.build());
        } catch (Exception e) {
            ar.resume(e);
        }
    }

    private MetadataFilter getMetadataFilter(String name) {
        if (name == null)
            return null;

        MetadataFilter filter = device.getDeviceExtension(ArchiveDeviceExtension.class).getMetadataFilter(name);
        if (filter == null)
            LOG.info("No Metadata filter configured for includefields={}", name);
        return filter;
    }

    private void buildResponse(String method, int[] frameList, int[] attributePath, AsyncResponse ar, Output output,
                               final RetrieveContext ctx, Date lastModified) throws IOException {
        service.calculateMatches(ctx);
        LOG.info("{}: {} Matches", method, ctx.getNumberOfMatches());
        if (ctx.getNumberOfMatches() == 0)
            throw new WebApplicationException(Response.Status.NOT_FOUND);
//            Collection<InstanceLocations> notAccessable = service.removeNotAccessableMatches(ctx);
        Collection<InstanceLocations> notAccepted = output.removeNotAcceptedMatches(this, ctx);
        if (ctx.getMatches().isEmpty())
            throw new WebApplicationException(
                    notAccepted.isEmpty() ? Response.Status.NOT_FOUND : Response.Status.NOT_ACCEPTABLE);

        if (lastModified == null)
            lastModified = service.getLastModifiedFromMatches(ctx);

        retrieveStart.fire(ctx);
        ar.register(new CompletionCallback() {
            @Override
            public void onComplete(Throwable throwable) {
                SafeClose.close(compressedMFPixelDataOutput);
                SafeClose.close(uncompressedFramesOutput);
                SafeClose.close(compressedFramesOutput);
                SafeClose.close(decompressFramesOutput);
                purgeSpoolDirectory();
                ctx.setException(throwable);
                retrieveEnd.fire(ctx);
            }
        });
        responseStatus = notAccepted.isEmpty() ? Response.Status.OK : Response.Status.PARTIAL_CONTENT;
        Object entity = output.entity(this, ctx, frameList, attributePath);
        ar.resume(Response.status(responseStatus).lastModified(lastModified)
                .tag(String.valueOf(lastModified.hashCode())).entity(entity).build());
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
        };

        public Object entity(WadoRS wadoRS, RetrieveContext ctx, int[] frameList, int[] attributePath)
                throws IOException {
            MultipartRelatedOutput output = new MultipartRelatedOutput();
            for (InstanceLocations inst : ctx.getMatches()) {
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
                MediaType mediaType = wadoRS.selectMediaType(mediaTypes);
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

        protected MediaType[] mediaTypesFor(InstanceLocations match) {
            return mediaTypesFor(match, ObjectType.objectTypeOf(match, null));
        }

        protected MediaType[] mediaTypesFor(InstanceLocations match, ObjectType objectType) {
            return objectType.getBulkdataContentTypes(match);
        }

        protected void addPart(MultipartRelatedOutput output, WadoRS wadoRS, RetrieveContext ctx,
                               InstanceLocations inst, int[] frameList, int[] attributePath) throws IOException {
             throw new WebApplicationException(name() + " not implemented", Response.Status.SERVICE_UNAVAILABLE);
        }

        public boolean isMetadata() {
            return false;
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
                throw new AssertionError("Unexcepted object type: " + objectType);
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
            throw new WebApplicationException(Response.Status.NOT_FOUND);

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
        DicomObjectOutput entity = new DicomObjectOutput(ctx, inst, acceptableTransferSyntaxes());
        output.addPart(entity, MediaTypes.APPLICATION_DICOM_TYPE);
    }

    private Collection<String> acceptableTransferSyntaxes() {
        Collection<String> tsuids = acceptableTransferSyntaxes;
        if (tsuids == null) {
            tsuids = new HashSet<>();
            for (MediaType mediaType : headers.getAcceptableMediaTypes()) {
                tsuids.add(MediaTypes.getTransferSyntax(MediaTypes.getMultiPartRelatedType(mediaType)));
            }
            tsuids.remove(null);
            acceptableTransferSyntaxes = tsuids;
        }
        return tsuids;
    }

    private List<MediaType> acceptableMediaTypes() {
        List<MediaType> mediaTypes = acceptableMediaTypes;
        if (mediaTypes == null) {
            mediaTypes = new ArrayList<>();
            for (MediaType mediaType : headers.getAcceptableMediaTypes()) {
                mediaTypes.add(mediaType.isWildcardType() ? mediaType : MediaTypes.getMultiPartRelatedType(mediaType));
            }
            mediaTypes.remove(null);
            acceptableMediaTypes = mediaTypes;
        }
        return mediaTypes;
    }

    private MediaType selectMediaType(MediaType... mediaTypes) {
        for (MediaType acceptableMediaType : acceptableMediaTypes()) {
            for (MediaType mediaType : mediaTypes) {
                if (mediaType.isCompatible(acceptableMediaType))
                    return mediaType;
            }
        }
        return null;
    }

    private void writeMetadataXML(MultipartRelatedOutput output, final RetrieveContext ctx,
                                  final InstanceLocations inst) {
        output.addPart(
                new StreamingOutput() {
                    @Override
                    public void write(OutputStream out) throws IOException,
                            WebApplicationException {
                        try {
                            SAXTransformer.getSAXWriter(new StreamResult(out)).write(loadMetadata(ctx, inst));
                        } catch (Exception e) {
                            throw new WebApplicationException(e);
                        }
                    }
                },
                MediaTypes.APPLICATION_DICOM_XML_TYPE);

    }

    private Object writeMetadataJSON(final RetrieveContext ctx) {
        final Collection<InstanceLocations> insts = ctx.getMatches();
        return new StreamingOutput() {
            @Override
            public void write(OutputStream out) throws IOException {
                try {
                    JsonGenerator gen = Json.createGenerator(out);
                    JSONWriter writer = new JSONWriter(gen);
                    gen.writeStartArray();
                    for (InstanceLocations inst : insts) {
                        writer.write(loadMetadata(ctx, inst));
                    }
                    gen.writeEnd();
                    gen.flush();
                } catch (Exception e) {
                    throw new WebApplicationException(e);
                }
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

    private void setBulkdataURI(Attributes attrs, String retrieveURL)
            throws IOException {
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
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        return  Files.createDirectories(Paths.get(StringUtils.replaceSystemProperties(
                StringUtils.maskNull(arcDev.getWadoSpoolDirectory(), JBOSS_SERVER_TEMP))));
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
}
