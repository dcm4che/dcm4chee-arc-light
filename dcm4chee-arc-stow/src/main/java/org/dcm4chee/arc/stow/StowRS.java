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

package org.dcm4chee.arc.stow;

import org.dcm4che3.data.*;
import org.dcm4che3.io.SAXReader;
import org.dcm4che3.io.SAXTransformer;
import org.dcm4che3.json.JSONReader;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.mime.MultipartInputStream;
import org.dcm4che3.mime.MultipartParser;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.ByteUtils;
import org.dcm4che3.util.StreamUtils;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreService;
import org.dcm4chee.arc.store.StoreSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.CompletionCallback;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Apr 2016
 */
@RequestScoped
@Path("aets/{AETitle}/rs")
public class StowRS {

    private static final Logger LOG = LoggerFactory.getLogger(StowRS.class);
    private static final String JBOSS_SERVER_TEMP = "${jboss.server.temp}";

    @Inject
    private StoreService service;

    @Context
    private HttpServletRequest request;

    @Inject
    private Device device;

    @HeaderParam("Content-Type")
    private MediaType contentType;

    @PathParam("AETitle")
    private String aet;

    private String acceptedStudyInstanceUID;
    private final Set<String> studyInstanceUIDs = new HashSet<>();

    private final ArrayList<Attributes> instances = new ArrayList<>();
    private final Attributes response = new Attributes();
    private Sequence sopSequence;
    private Sequence failedSOPSequence;
    private java.nio.file.Path spoolDirectory;
    private Map<String, BulkDataWithMediaType> bulkdataMap = new HashMap<>();

    @Override
    public String toString() {
        return request.getRequestURI();
    }

    @POST
    @Path("/studies")
    @Consumes("multipart/related;type=application/dicom")
    @Produces("application/dicom+xml")
    public void storeInstancesXML(@Suspended AsyncResponse ar, InputStream in) throws Exception {
        store(ar, in, Input.DICOM, Output.DICOM_XML);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}")
    @Consumes("multipart/related;type=application/dicom")
    @Produces("application/dicom+xml")
    public void storeInstancesXML(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @Suspended AsyncResponse ar,
            InputStream in) throws Exception {
        acceptedStudyInstanceUID = studyInstanceUID;
        store(ar, in, Input.DICOM, Output.DICOM_XML);
    }

    @POST
    @Path("/studies")
    @Consumes("multipart/related;type=application/dicom+xml")
    @Produces("application/dicom+xml")
    public void storeXMLMetadataAndBulkdataXML(@Suspended AsyncResponse ar, InputStream in) throws Exception {
        store(ar, in, Input.METADATA_XML, Output.DICOM_XML);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}")
    @Consumes("multipart/related;type=application/dicom+xml")
    @Produces("application/dicom+xml")
    public void storeXMLMetadataAndBulkdataXML(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @Suspended AsyncResponse ar,
            InputStream in) throws Exception {
        acceptedStudyInstanceUID = studyInstanceUID;
        store(ar, in, Input.METADATA_XML, Output.DICOM_XML);
    }

    @POST
    @Path("/studies")
    @Consumes("multipart/related;type=application/json")
    @Produces("application/dicom+xml")
    public void storeJSONMetadataAndBulkdataXML(@Suspended AsyncResponse ar, InputStream in) throws Exception {
        store(ar, in, Input.METADATA_JSON, Output.DICOM_XML);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}")
    @Consumes("multipart/related;type=application/json")
    @Produces("application/dicom+xml")
    public void storeJSONMetadataAndBulkdata(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @Suspended AsyncResponse ar,
            InputStream in) throws Exception {
        acceptedStudyInstanceUID = studyInstanceUID;
        store(ar, in, Input.METADATA_JSON, Output.DICOM_XML);
    }

    @POST
    @Path("/studies")
    @Consumes("multipart/related;type=application/dicom")
    @Produces("application/json")
    public void storeInstancesJSON(@Suspended AsyncResponse ar, InputStream in) throws Exception {
        store(ar, in, Input.DICOM, Output.JSON);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}")
    @Consumes("multipart/related;type=application/dicom")
    @Produces("application/json")
    public void storeInstancesJSON(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @Suspended AsyncResponse ar,
            InputStream in) throws Exception {
        acceptedStudyInstanceUID = studyInstanceUID;
        store(ar, in, Input.DICOM, Output.JSON);
    }

    @POST
    @Path("/studies")
    @Consumes("multipart/related;type=application/dicom+xml")
    @Produces("application/json")
    public void storeXMLMetadataAndBulkdataJSON(@Suspended AsyncResponse ar, InputStream in) throws Exception {
        store(ar, in, Input.METADATA_XML, Output.JSON);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}")
    @Consumes("multipart/related;type=application/dicom+xml")
    @Produces("application/json")
    public void storeXMLMetadataAndBulkdataJSON(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @Suspended AsyncResponse ar, InputStream in) throws Exception {
        acceptedStudyInstanceUID = studyInstanceUID;
        store(ar, in, Input.METADATA_XML, Output.JSON);
    }

    @POST
    @Path("/studies")
    @Consumes("multipart/related;type=application/json")
    @Produces("application/json")
    public void storeJSONMetadataAndBulkdataJSON(@Suspended AsyncResponse ar, InputStream in) throws Exception {
        store(ar, in, Input.METADATA_JSON, Output.JSON);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}")
    @Consumes("multipart/related;type=application/json")
    @Produces("application/json")
    public void storeJSONMetadataAndBulkdataJSON(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @Suspended AsyncResponse ar,
            InputStream in) throws Exception {
        acceptedStudyInstanceUID = studyInstanceUID;
        store(ar, in, Input.METADATA_JSON, Output.JSON);
    }

    private static String getHeaderParamValue(Map<String, List<String>> headerParams, String key) {
        List<String> list = headerParams.get(key);
        return list != null && !list.isEmpty() ? list.get(0) : null;
    }

    private void store(AsyncResponse ar, InputStream in, final Input input, Output output)  throws Exception {
        LOG.info("Process POST {} from {}@{}", this, request.getRemoteUser(), request.getRemoteHost());
        ar.register(new CompletionCallback() {
            @Override
            public void onComplete(Throwable throwable) {
                purgeSpoolDirectory();
            }
        });
        final StoreSession session = service.newStoreSession(request, aet, getApplicationEntity());
        new MultipartParser(boundary()).parse(new BufferedInputStream(in), new MultipartParser.Handler() {
            @Override
            public void bodyPart(int partNumber, MultipartInputStream in) throws IOException {
                Map<String, List<String>> headerParams = in.readHeaderParams();
                LOG.info("storeInstances: Extract Part #{}{}", partNumber, headerParams);
                String contentLocation = getHeaderParamValue(headerParams, "content-location");
                String contentType = getHeaderParamValue(headerParams, "content-type");
                MediaType mediaType = MediaType.valueOf(contentType);
                try {
                    if (!input.readBodyPart(StowRS.this, session, in, mediaType, contentLocation)) {
                        LOG.info("{}: Ignore Part with Content-Type={}", session, mediaType);
                        in.skipAll();
                    }
                } catch (Exception e) {
                    new WebApplicationException("Failed to process Part #" + partNumber + headerParams, e);
                }
            }
        });
        for (Attributes instance : instances) {
            storeDicomObject(session, instance);
        }

        response.setString(Tag.RetrieveURL, VR.UR, retrieveURL());
        ar.resume(Response.status(status()).entity(output.entity(response)).build());
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
                        LOG.warn("Failed to delete bulkdata spool file {}", file, e);
                    }
                }
            }
            Files.delete(spoolDirectory);
        } catch (IOException e) {
            LOG.warn("Failed to purge spool directory {}", spoolDirectory, e);
        }

    }

    private String boundary() {
        String boundary = contentType.getParameters().get("boundary");
        if (boundary == null)
            throw new WebApplicationException(getResponse("Missing Boundary Parameter", Response.Status.BAD_REQUEST));

        return boundary;
    }

    private ApplicationEntity getApplicationEntity() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        if (ae == null || !ae.isInstalled())
            throw new WebApplicationException(getResponse(
                    "No such Application Entity: " + aet, Response.Status.SERVICE_UNAVAILABLE));
        return ae;
    }

    private enum Input {
        DICOM {
            @Override
            boolean readBodyPart(StowRS stowRS, StoreSession session, MultipartInputStream in,
                                 MediaType mediaType, String contentLocation) throws Exception {
                if (!MediaTypes.equalsIgnoreParameters(mediaType, MediaTypes.APPLICATION_DICOM_TYPE))
                    return false;

                stowRS.storeDicomObject(session, in);
                return true;
            }
        },
        METADATA_XML {
            @Override
            boolean readBodyPart(StowRS stowRS, StoreSession session, MultipartInputStream in,
                                 MediaType mediaType, String contentLocation) throws Exception {
                if (!MediaTypes.equalsIgnoreParameters(mediaType, MediaTypes.APPLICATION_DICOM_XML_TYPE))
                    return stowRS.spoolBulkdata(session, in, mediaType, contentLocation);

                stowRS.instances.add(SAXReader.parse(in));
                return true;
            }
        },
        METADATA_JSON {
            @Override
            boolean readBodyPart(StowRS stowRS, StoreSession session, MultipartInputStream in,
                                 MediaType mediaType, String contentLocation) throws Exception {
                if (!MediaTypes.equalsIgnoreParameters(mediaType, MediaType.APPLICATION_JSON_TYPE))
                    return stowRS.spoolBulkdata(session, in, mediaType, contentLocation);

                JSONReader reader = new JSONReader(Json.createParser(new InputStreamReader(in, "UTF-8")));
                stowRS.instances.add(reader.readDataset(null));
                return true;
            }
        };

        abstract boolean readBodyPart(StowRS stowRS, StoreSession session, MultipartInputStream in,
                                      MediaType mediaType, String contentLocation) throws Exception;
    }

    private void storeDicomObject(StoreSession session, MultipartInputStream in) throws IOException {
        StoreContext ctx = service.newStoreContext(session);
        ctx.setAcceptedStudyInstanceUID(acceptedStudyInstanceUID);
        try {
            service.store(ctx, in);
            studyInstanceUIDs.add(ctx.getStudyInstanceUID());
            sopSequence().add(mkSOPRefWithRetrieveURL(ctx));
        } catch (DicomServiceException e) {
            LOG.info("{}: Failed to store {}", session, UID.nameOf(ctx.getSopClassUID()), e);
            failedSOPSequence().add(mkSOPRefWithFailureReason(ctx, e));
        }
    }

    private void storeDicomObject(StoreSession session, Attributes attrs) throws IOException {
        StoreContext ctx = service.newStoreContext(session);
        ctx.setAcceptedStudyInstanceUID(acceptedStudyInstanceUID);
        try {
            ctx.setReceiveTransferSyntax(MediaTypes.transferSyntaxOf(resolveBulkdataRefs(attrs)));
            service.store(ctx, attrs);
            studyInstanceUIDs.add(ctx.getStudyInstanceUID());
            sopSequence().add(mkSOPRefWithRetrieveURL(ctx));
        } catch (DicomServiceException e) {
            ctx.setAttributes(attrs);
            LOG.info("{}: Failed to store {}", session, UID.nameOf(ctx.getSopClassUID()), e);
            failedSOPSequence().add(mkSOPRefWithFailureReason(ctx, e));
        }
    }

    private MediaType resolveBulkdataRefs(Attributes attrs) throws DicomServiceException {
        final MediaType[] mediaType = { MediaType.APPLICATION_OCTET_STREAM_TYPE };
        try {
            attrs.accept(new Attributes.Visitor() {
                @Override
                public boolean visit(Attributes attrs, int tag, VR vr, Object value) throws Exception {
                    if (value instanceof BulkData) {
                        resolveBulkdataRef(attrs, tag, vr, (BulkData) value, mediaType);
                    }
                    return true;
                }
            }, true);
        } catch (DicomServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new DicomServiceException(Status.ProcessingFailure, e);
        }
        return mediaType[0];
    }

    private void resolveBulkdataRef(Attributes attrs, int tag, VR vr, BulkData bulkdata, MediaType[] mediaType)
            throws DicomServiceException {
        BulkDataWithMediaType bulkdataWithMediaType = bulkdataMap.get(bulkdata.getURI());
        if (bulkdataWithMediaType == null)
            throw new DicomServiceException(0xA922, "Missing Bulkdata: " + bulkdata.getURI());
        if (tag != Tag.PixelData || MediaType.APPLICATION_OCTET_STREAM_TYPE.equals(bulkdataWithMediaType.mediaType)) {
            bulkdata.setURI(bulkdataWithMediaType.bulkData.getURI());
        } else {
            Fragments frags = attrs.newFragments(tag, vr, 2);
            frags.add(ByteUtils.EMPTY_BYTES);
            frags.add(new BulkData(null, bulkdataWithMediaType.bulkData.getURI(), false));
            mediaType[0] = bulkdataWithMediaType.mediaType;
        }
    }

    private boolean spoolBulkdata(StoreSession session, MultipartInputStream in, MediaType mediaType,
                                  String contentLocation) throws IOException {
        if (spoolDirectory == null)
            spoolDirectory = Files.createTempDirectory(spoolDirectoryRoot(), null);
        java.nio.file.Path spoolFile = Files.createTempFile(spoolDirectory, null, null);
        try (OutputStream out = Files.newOutputStream(spoolFile)) {
            StreamUtils.copy(in, out);
        }
        bulkdataMap.put(contentLocation, new BulkDataWithMediaType(spoolFile, mediaType));
        return true;
    }

    private java.nio.file.Path spoolDirectoryRoot() throws IOException {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        return  Files.createDirectories(Paths.get(StringUtils.replaceSystemProperties(
                StringUtils.maskNull(arcDev.getStowSpoolDirectory(), JBOSS_SERVER_TEMP))));
    }

    private Attributes mkSOPRefWithRetrieveURL(StoreContext ctx) {
        Attributes attrs = mkSOPRef(ctx, 3);
        attrs.setString(Tag.RetrieveURL, VR.UR, retrieveURL(ctx));
        return attrs;
    }

    private String retrieveURL() {
        if (studyInstanceUIDs.size() != 1)
            return null;

        StringBuffer retrieveURL = request.getRequestURL();
        if (retrieveURL.lastIndexOf("/studies") + 8 == retrieveURL.length())
            retrieveURL.append('/').append(studyInstanceUIDs.iterator().next());
        return retrieveURL.toString();
    }

    private String retrieveURL(StoreContext ctx) {
        StringBuffer retrieveURL = request.getRequestURL();
        if (retrieveURL.lastIndexOf("/studies") + 8 == retrieveURL.length())
            retrieveURL.append('/').append(ctx.getStudyInstanceUID());
        retrieveURL.append("/series/").append(ctx.getSeriesInstanceUID());
        retrieveURL.append("/instances/").append(ctx.getSopInstanceUID());
        return retrieveURL.toString();
    }

    private Attributes mkSOPRefWithFailureReason(StoreContext ctx, DicomServiceException e) {
        Attributes attrs = mkSOPRef(ctx, 3);
        attrs.setInt(Tag.FailureReason, VR.US, e.getStatus());
        return attrs;
    }

    private Attributes mkSOPRef(StoreContext ctx, int size) {
        Attributes attrs = new Attributes(size);
        attrs.setString(Tag.ReferencedSOPClassUID, VR.UI, ctx.getSopClassUID());
        attrs.setString(Tag.ReferencedSOPInstanceUID, VR.UI, ctx.getSopInstanceUID());
        return attrs;
    }

    private Sequence sopSequence() {
        if (sopSequence == null)
            sopSequence = response.newSequence(Tag.ReferencedSOPSequence, 10);
        return sopSequence;
    }

    private Sequence failedSOPSequence() {
        if (failedSOPSequence == null)
            failedSOPSequence = response.newSequence(Tag.FailedSOPSequence, 10);
        return failedSOPSequence;
    }

    private Response.Status status() {
        return sopSequence == null ? Response.Status.CONFLICT
                : failedSOPSequence == null ? Response.Status.OK : Response.Status.ACCEPTED;
    }

    private enum Output {
        DICOM_XML {
            @Override
            StreamingOutput entity(final Attributes response) {
                return new StreamingOutput() {
                    @Override
                    public void write(OutputStream out) throws IOException {
                        try {
                            SAXTransformer.getSAXWriter(new StreamResult(out)).write(response);
                        } catch (Exception e) {
                            throw new WebApplicationException(e);
                        }
                    }
                };
            }
        },
        JSON {
            @Override
            StreamingOutput entity(final Attributes response) {
                return new StreamingOutput() {
                    @Override
                    public void write(OutputStream out) throws IOException {
                        try (JsonGenerator gen = Json.createGenerator(out)){
                            new JSONWriter(gen).write(response);
                        }
                    }
                };
            }
        };

        abstract StreamingOutput entity(Attributes response);
    }

    private static class BulkDataWithMediaType {
        final BulkData bulkData;
        final MediaType mediaType;

        private BulkDataWithMediaType(java.nio.file.Path path, MediaType mediaType) throws IOException {
            this.bulkData = new BulkData(path.toUri().toString(), 0, (int) Files.size(path), false);
            this.mediaType = mediaType;
        }
    }

    private Response getResponse(String errorMessage, Response.Status status) {
        Object entity = "{\"errorMessage\":\"" + errorMessage + "\"}";
        return Response.status(status).entity(entity).build();
    }
}
