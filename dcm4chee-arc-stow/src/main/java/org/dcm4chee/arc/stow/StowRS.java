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
 * Portions created by the Initial Developer are Copyright (C) 2017-2019
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
import org.dcm4che3.image.BufferedImageUtils;
import org.dcm4che3.imageio.codec.jpeg.JPEG;
import org.dcm4che3.imageio.codec.jpeg.JPEGParser;
import org.dcm4che3.imageio.codec.mp4.MP4FileType;
import org.dcm4che3.imageio.codec.mp4.MP4Parser;
import org.dcm4che3.imageio.codec.mpeg.MPEG2Parser;
import org.dcm4che3.io.SAXReader;
import org.dcm4che3.json.JSONReader;
import org.dcm4che3.mime.MultipartInputStream;
import org.dcm4che3.mime.MultipartParser;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.WebApplication;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.*;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.keycloak.KeycloakContext;
import org.dcm4chee.arc.query.util.QueryAttributes;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreService;
import org.dcm4chee.arc.store.StoreSession;
import org.dcm4chee.arc.validation.constraints.InvokeValidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.enterprise.context.RequestScoped;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonParsingException;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.CompletionCallback;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.*;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
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
@InvokeValidate(type = StowRS.class)
public class StowRS {

    private static final Logger LOG = LoggerFactory.getLogger(StowRS.class);
    private static final String NOT_A_DICOM_FILE_UID = "1.2.40.0.13.1.15.110.3.165.2";
    private static final int[] IUIDS_TAGS = {
            Tag.StudyInstanceUID,
            Tag.SeriesInstanceUID,
            Tag.SOPInstanceUID
    };

    private static final int[] IMAGE_PIXEL_TAGS = {
            Tag.SamplesPerPixel,
            Tag.PhotometricInterpretation,
            Tag.Rows,
            Tag.Columns,
            Tag.BitsAllocated,
            Tag.BitsStored,
            Tag.HighBit,
            Tag.PixelRepresentation
    };

    private static final ElementDictionary DICT = ElementDictionary.getStandardElementDictionary();
    private static final String SUPER_USER_ROLE = "super-user-role";

    @Inject
    private StoreService service;

    @Context
    private HttpServletRequest request;

    @Context
    private UriInfo uriInfo;

    @Inject
    private Device device;

    @HeaderParam("Content-Type")
    private MediaType contentType;

    @PathParam("AETitle")
    private String aet;

    @QueryParam("updatePolicy")
    @Pattern(regexp = "SUPPLEMENT|MERGE|OVERWRITE")
    @DefaultValue("OVERWRITE")
    private String updatePolicy;

    @QueryParam("reasonForModification")
    @Pattern(regexp = "COERCE|CORRECT")
    private String reasonForModification;

    @QueryParam("sourceOfPreviousValues")
    private String sourceOfPreviousValues;

    private String acceptedStudyInstanceUID;
    private final Set<String> studyInstanceUIDs = new HashSet<>();

    private final ArrayList<Attributes> instances = new ArrayList<>();
    private final Attributes response = new Attributes();
    private volatile String warning;
    private Sequence sopSequence;
    private Sequence failedSOPSequence;
    private java.nio.file.Path spoolDirectory;
    private Map<String, PathWithMediaType> bulkdataMap = new HashMap<>();

    public void validate() {
        logRequest();
        new QueryAttributes(uriInfo, null);
    }

    @Override
    public String toString() {
        String requestURI = request.getRequestURI();
        String queryString = request.getQueryString();
        return queryString == null ? requestURI : requestURI + '?' + queryString;
    }

    @POST
    @Path("/studies")
    @Consumes("multipart/related;type=application/dicom")
    @Produces("application/dicom+xml")
    public void storeInstancesXML(@Suspended AsyncResponse ar, InputStream in) throws Exception {
        store(ar, in, Input.DICOM, OutputType.DICOM_XML);
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
        store(ar, in, Input.DICOM, OutputType.DICOM_XML);
    }

    @POST
    @Path("/studies")
    @Consumes("multipart/related;type=application/dicom+xml")
    @Produces("application/dicom+xml")
    public void storeXMLMetadataAndBulkdataXML(@Suspended AsyncResponse ar, InputStream in) throws Exception {
        store(ar, in, Input.METADATA_XML, OutputType.DICOM_XML);
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
        store(ar, in, Input.METADATA_XML, OutputType.DICOM_XML);
    }

    @POST
    @Path("/studies")
    @Consumes("multipart/related;type=application/dicom+json")
    @Produces("application/dicom+xml")
    public void storeJSONMetadataAndBulkdataXML(@Suspended AsyncResponse ar, InputStream in) throws Exception {
        store(ar, in, Input.METADATA_JSON, OutputType.DICOM_XML);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}")
    @Consumes("multipart/related;type=application/dicom+json")
    @Produces("application/dicom+xml")
    public void storeJSONMetadataAndBulkdata(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @Suspended AsyncResponse ar,
            InputStream in) throws Exception {
        acceptedStudyInstanceUID = studyInstanceUID;
        store(ar, in, Input.METADATA_JSON, OutputType.DICOM_XML);
    }

    @POST
    @Path("/studies")
    @Consumes("multipart/related;type=application/dicom")
    @Produces("application/dicom+json")
    public void storeInstancesJSON(@Suspended AsyncResponse ar, InputStream in) throws Exception {
        store(ar, in, Input.DICOM, OutputType.JSON);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}")
    @Consumes("multipart/related;type=application/dicom")
    @Produces("application/dicom+json")
    public void storeInstancesJSON(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @Suspended AsyncResponse ar,
            InputStream in) throws Exception {
        acceptedStudyInstanceUID = studyInstanceUID;
        store(ar, in, Input.DICOM, OutputType.JSON);
    }

    @POST
    @Path("/studies")
    @Consumes("multipart/related;type=application/dicom+xml")
    @Produces("application/dicom+json")
    public void storeXMLMetadataAndBulkdataJSON(@Suspended AsyncResponse ar, InputStream in) throws Exception {
        store(ar, in, Input.METADATA_XML, OutputType.JSON);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}")
    @Consumes("multipart/related;type=application/dicom+xml")
    @Produces("application/dicom+json")
    public void storeXMLMetadataAndBulkdataJSON(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @Suspended AsyncResponse ar, InputStream in) throws Exception {
        acceptedStudyInstanceUID = studyInstanceUID;
        store(ar, in, Input.METADATA_XML, OutputType.JSON);
    }

    @POST
    @Path("/studies")
    @Consumes("multipart/related;type=application/dicom+json")
    @Produces("application/dicom+json")
    public void storeJSONMetadataAndBulkdataJSON(@Suspended AsyncResponse ar, InputStream in) throws Exception {
        store(ar, in, Input.METADATA_JSON, OutputType.JSON);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}")
    @Consumes("multipart/related;type=application/dicom+json")
    @Produces("application/dicom+json")
    public void storeJSONMetadataAndBulkdataJSON(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @Suspended AsyncResponse ar,
            InputStream in) throws Exception {
        acceptedStudyInstanceUID = studyInstanceUID;
        store(ar, in, Input.METADATA_JSON, OutputType.JSON);
    }

    private static String getHeaderParamValue(Map<String, List<String>> headerParams, String key) {
        List<String> list = headerParams.get(key);
        return list != null && !list.isEmpty() ? list.get(0) : null;
    }

    private void store(AsyncResponse ar, InputStream in, final Input input, OutputType output)  throws Exception {
        ApplicationEntity ae = getApplicationEntity();
        validateAcceptedUserRoles(ae.getAEExtensionNotNull(ArchiveAEExtension.class));
        if (aet.equals(ae.getAETitle()))
            validateWebAppServiceClass();
        ar.register((CompletionCallback) throwable -> purgeSpoolDirectory());
        final StoreSession session = service.newStoreSession(
                HttpServletRequestInfo.valueOf(request), ae, aet, null);
        new MultipartParser(boundary())
                .parse(new BufferedInputStream(in), (partNumber, multipartInputStream) -> {
                    Map<String, List<String>> headerParams = multipartInputStream.readHeaderParams();
                    LOG.info("storeInstances: Extract Part #{}{}", partNumber, headerParams);
                    String contentLocation = getHeaderParamValue(headerParams, "content-location");
                    String contentType = getHeaderParamValue(headerParams, "content-type");
                    MediaType mediaType = normalize(MediaType.valueOf(contentType));
                    try {
                        if (!input.readBodyPart(StowRS.this, session, multipartInputStream, mediaType, contentLocation)) {
                            LOG.info("{}: Ignore Part with Content-Type={}", session, mediaType);
                            multipartInputStream.skipAll();
                        }
                    } catch (JsonParsingException | SAXException e) {
                        throw new WebApplicationException(
                                errResponse(e.getMessage(), Response.Status.BAD_REQUEST));
                    } catch (Exception e) {
                        if (instances.size() == 1)
                            throw new WebApplicationException(
                                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
                        else {
                            LOG.warn("Failed to process Part #" + partNumber + headerParams);
                            throw new WebApplicationException(
                                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
                        }
                    }
                });
        int instanceNumber = 0;
        for (Attributes instance : instances) 
            storeDicomObject(session, instance, ++instanceNumber);

        response.setString(Tag.RetrieveURL, VR.UR, retrieveURL());
        Response.ResponseBuilder responseBuilder = Response.status(status());
        ar.resume(responseBuilder
                    .entity(output.entity(response, ae))
                    .header("Warning", warning)
                    .build());
    }

    private static MediaType normalize(MediaType mediaType) {
        return MediaTypes.isSTLType(mediaType) ? MediaTypes.MODEL_STL_TYPE : mediaType;
    }

    private void logRequest() {
        LOG.info("Process {} {} from {}@{}:{}",
                request.getMethod(),
                toString(),
                request.getRemoteUser(),
                request.getRemoteHost(),
                request.getRemotePort());
    }

    private void validateAcceptedUserRoles(ArchiveAEExtension arcAE) {
        KeycloakContext keycloakContext = KeycloakContext.valueOf(request);
        if (keycloakContext.isSecured() && !keycloakContext.isUserInRole(System.getProperty(SUPER_USER_ROLE))) {
            if (!arcAE.isAcceptedUserRole(keycloakContext.getRoles()))
                throw new WebApplicationException(
                        "Application Entity " + arcAE.getApplicationEntity().getAETitle() + " does not list role of accessing user",
                        Response.Status.FORBIDDEN);
        }
    }

    private void validateWebAppServiceClass() {
        device.getWebApplications().stream()
                .filter(webApp -> request.getRequestURI().startsWith(webApp.getServicePath())
                        && Arrays.asList(webApp.getServiceClasses())
                        .contains(WebApplication.ServiceClass.STOW_RS))
                .findFirst()
                .orElseThrow(() -> new WebApplicationException(errResponse(
                        "No Web Application with STOW_RS service class found for Application Entity: " + aet,
                        Response.Status.NOT_FOUND)));
    }

    private void validateWebApp() {
        WebApplication webApplication = device.getWebApplications().stream()
                .filter(webApp -> request.getRequestURI().startsWith(webApp.getServicePath())
                        && Arrays.asList(webApp.getServiceClasses())
                        .contains(WebApplication.ServiceClass.STOW_RS))
                .findFirst()
                .orElseThrow(() -> new WebApplicationException(errResponse(
                        "No Web Application with STOW_RS service class found for Application Entity: " + aet,
                        Response.Status.NOT_FOUND)));

        KeycloakContext keycloakContext = KeycloakContext.valueOf(request);
        if (keycloakContext.isSecured()
                && webApplication.getProperties().containsKey("roles"))
            Arrays.stream(webApplication.getProperties().get("roles").split(","))
                    .filter(keycloakContext::isUserInRole)
                    .findFirst()
                    .orElseThrow(() -> new WebApplicationException(errResponse(
                            "Web Application with STOW_RS service class does not list role of accessing user",
                            Response.Status.FORBIDDEN)));
    }

    private void purgeSpoolDirectory() {
        if (spoolDirectory == null)
            return;

        try {
            try (DirectoryStream<java.nio.file.Path> dir = Files.newDirectoryStream(spoolDirectory)) {
                dir.forEach(file -> {
                    try {
                        Files.delete(file);
                    } catch (IOException e) {
                        LOG.warn("Failed to delete bulkdata spool file {}", file, e);
                    }
                });
            }
            Files.delete(spoolDirectory);
        } catch (IOException e) {
            LOG.warn("Failed to purge spool directory {}", spoolDirectory, e);
        }

    }

    private String boundary() {
        String boundary = contentType.getParameters().get("boundary");
        if (boundary == null)
            throw new WebApplicationException(errResponse("Missing Boundary Parameter", Response.Status.BAD_REQUEST));

        return boundary;
    }

    private ApplicationEntity getApplicationEntity() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        if (ae == null || !ae.isInstalled())
            throw new WebApplicationException(errResponse(
                    "No such Application Entity: " + aet, Response.Status.NOT_FOUND));
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
                    return stowRS.spoolBulkdata(in, mediaType, contentLocation);

                stowRS.instances.add(SAXReader.parse(in));
                return true;
            }
        },
        METADATA_JSON {
            @Override
            boolean readBodyPart(StowRS stowRS, StoreSession session, MultipartInputStream in,
                                 MediaType mediaType, String contentLocation) throws Exception {
                if (!MediaTypes.equalsIgnoreParameters(mediaType, MediaTypes.APPLICATION_DICOM_JSON_TYPE))
                    return stowRS.spoolBulkdata(in, mediaType, contentLocation);

                JSONReader reader = new JSONReader(Json.createParser(new InputStreamReader(in, StandardCharsets.UTF_8)));
                reader.readDatasets((fmi, dataset) -> stowRS.instances.add(dataset));
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
            service.store(ctx, in, this::coerceAttributes);
            studyInstanceUIDs.add(ctx.getStudyInstanceUID());
            sopSequence().add(mkSOPRefWithRetrieveURL(ctx));
        } catch (DicomServiceException e) {
            LOG.info("{}: Failed to store {}", session, getSopClassName(ctx), e);
            setWarning(e);
            failedSOPSequence().add(mkSOPRefWithFailureReason(ctx, e));
        }
    }

    private void setWarning(DicomServiceException e) {
        warning = "299 " + device.getDeviceName() + " \"" + e.getMessage() + "\"";
    }

    private static String getSopClassName(StoreContext ctx) {
        Attributes attributes = ctx.getAttributes();
        return attributes != null ? UID.nameOf(attributes.getString(Tag.SOPClassUID)) : "?";
    }

    private void coerceAttributes(Attributes attrs) {
        Attributes coerce = new QueryAttributes(uriInfo, null).getQueryKeys();
        if (!coerce.isEmpty()) {
            Attributes modified = new Attributes();
            attrs.update(Attributes.UpdatePolicy.valueOf(updatePolicy), false, coerce, modified);
            if (!modified.isEmpty() && reasonForModification != null) {
                attrs.addOriginalAttributes(sourceOfPreviousValues, new Date(),
                        reasonForModification, device.getDeviceName(), modified);
            }
        }
    }

    private void storeDicomObject(StoreSession session, Attributes attrs, int instanceNumber) throws IOException {
        StoreContext ctx = service.newStoreContext(session);
        ctx.setAcceptedStudyInstanceUID(acceptedStudyInstanceUID);
        try {
            PathWithMediaType pathWithMediaType = resolveBulkdataRefs(session, attrs);
            if (pathWithMediaType == null)
                ctx.setReceiveTransferSyntax(UID.ExplicitVRLittleEndian);
            else {
                ctx.setReceiveTransferSyntax(MediaTypes.transferSyntaxOf(pathWithMediaType.mediaType));
                supplementAttrs(ctx, session, attrs, instanceNumber, pathWithMediaType);
            }
            service.store(ctx, attrs);
            studyInstanceUIDs.add(ctx.getStudyInstanceUID());
            sopSequence().add(mkSOPRefWithRetrieveURL(ctx));
        } catch (DicomServiceException e) {
            ctx.setAttributes(attrs);
            LOG.info("{}: Failed to store {}", session, getSopClassName(ctx), e);
            setWarning(e);
            failedSOPSequence().add(mkSOPRefWithFailureReason(ctx, e));
        }
    }

    private void supplementAttrs(StoreContext ctx, StoreSession session, Attributes attrs, int instanceNumber,
                                 PathWithMediaType bulkdata) throws DicomServiceException {
        for (int tag : IUIDS_TAGS)
            if (!attrs.containsValue(tag)) {
                String uid = UIDUtils.createUID();
                logSupplementMissing(session, tag, uid);
                attrs.setString(tag, VR.UI, uid);
            }
        String cuid = attrs.getString(Tag.SOPClassUID);
        if (cuid == null) {
            cuid = MediaTypes.sopClassOf(bulkdata.mediaType);
            if (cuid == null)
                throw missingAttribute(Tag.SOPClassUID);
            logSupplementMissing(session, Tag.SOPClassUID, cuid +  " " + UID.nameOf(cuid));
            attrs.setString(Tag.SOPClassUID, VR.UI, cuid);
        }
        if (!attrs.containsValue(Tag.InstanceCreationDate)) {
            Date now = new Date();
            logSupplementMissing(session, Tag.InstanceCreationDate, now);
            attrs.setDate(Tag.InstanceCreationDateAndTime, now);
        }
        if (!attrs.containsValue(Tag.SeriesNumber)) {
            logSupplementMissing(session, Tag.SeriesNumber, 999);
            attrs.setInt(Tag.SeriesNumber, VR.IS, 999);
        }
        if (!attrs.containsValue(Tag.InstanceNumber)) {
            logSupplementMissing(session, Tag.InstanceNumber, instanceNumber);
            attrs.setInt(Tag.InstanceNumber, VR.IS, instanceNumber);
        }
        if (attrs.containsValue(Tag.PixelData)) {
            supplementImagePixelModule(ctx, session, attrs, bulkdata);
            verifyImagePixelModule(attrs);
        }
        if (attrs.containsValue(Tag.EncapsulatedDocument)) {
            verifyEncapsulatedDocumentModule(session, attrs, bulkdata);
        } else {
            switch (cuid) {
                case UID.EncapsulatedPDFStorage:
                case UID.EncapsulatedCDAStorage:
                case UID.EncapsulatedSTLStorage:
                case UID.EncapsulatedOBJStorage:
                case UID.EncapsulatedMTLStorage:
                case UID.PrivateDcm4cheEncapsulatedGenozipStorage:
                    throw missingAttribute(Tag.EncapsulatedDocument);
            }
        }
    }

    private void supplementImagePixelModule(StoreContext ctx, StoreSession session, Attributes attrs,
            PathWithMediaType bulkdata) throws DicomServiceException {
        CompressedPixelData compressedPixelData = CompressedPixelData.valueOf(bulkdata.mediaType);
        ImageReader imageReader;
        if (compressedPixelData != null) {
            try (SeekableByteChannel channel = Files.newByteChannel(bulkdata.path)) {
                ctx.setReceiveTransferSyntax(compressedPixelData.supplementImagePixelModule(session, channel, attrs,
                        bulkdata.length > session.getArchiveAEExtension().stowMaxFragmentLength()));
            } catch (IOException e) {
                LOG.info("Failed to parse {} compressed pixel data from {}:\n", compressedPixelData, bulkdata.path, e);
                throw new DicomServiceException(Status.ProcessingFailure, e);
            }
        } else if ((imageReader = findImageReader(bulkdata.mediaType, "com.sun.imageio")) != null) {
            try (ImageInputStream iio = ImageIO.createImageInputStream(bulkdata.path.toFile())) {
                imageReader.setInput(iio);
                BufferedImageUtils.toImagePixelModule(imageReader, attrs);
                ctx.setReceiveTransferSyntax(UID.ExplicitVRLittleEndian);
            } catch (Exception e) {
                LOG.info("Failed to extract pixel data from bulkdata:\n", e);
                throw new DicomServiceException(Status.ProcessingFailure, e);
            } finally {
                imageReader.dispose();
            }
        }
    }

    private static ImageReader findImageReader(MediaType mediaType, String pkg) {
        Iterator<ImageReader> iter = ImageIO.getImageReadersByMIMEType(mediaType.toString());
        if (iter.hasNext()) {
            ImageReader reader = iter.next();
            while (!reader.getClass().getName().startsWith(pkg) && iter.hasNext()) {
                reader = iter.next();
            }
            return reader;
        } else {
            return null;
        }
    }

    private static void supplementMissing(StoreSession session, int tag, VR vr, String value, Attributes attrs) {
        logSupplementMissing(session, tag, value);
        attrs.setString(tag, vr, value);
    }

    private static void supplementMissing(StoreSession session, int tag, VR vr, int value, Attributes attrs) {
        logSupplementMissing(session, tag, value);
        attrs.setInt(tag, vr, value);
    }

    private static void logSupplementMissing(StoreSession session, int tag, Object value) {
        LOG.info("{}: Supplement Missing {} {} - {}", session, DICT.keywordOf(tag), TagUtils.toString(tag), value);
    }

    private static void verifyImagePixelModule(Attributes attrs) throws DicomServiceException {
        for (int tag : IMAGE_PIXEL_TAGS)
            if (!attrs.containsValue(tag))
                throw missingAttribute(tag);
        if (attrs.getInt(Tag.SamplesPerPixel, 1) > 1 && !attrs.containsValue(Tag.PlanarConfiguration))
            throw missingAttribute(Tag.PlanarConfiguration);
    }

    private static void verifyEncapsulatedDocumentModule(
            StoreSession session, Attributes attrs, PathWithMediaType bulkdata) throws DicomServiceException {
        String cuid = attrs.getString(Tag.SOPClassUID);
        if (!attrs.containsValue(Tag.MIMETypeOfEncapsulatedDocument)) {
            String mimeType = bulkdata.mediaType.toString();
            if (mimeType == null)
                throw missingAttribute(Tag.MIMETypeOfEncapsulatedDocument);
            supplementMissing(session, Tag.MIMETypeOfEncapsulatedDocument, VR.LO, mimeType, attrs);
        }
        if (!attrs.containsValue(Tag.BurnedInAnnotation))
            supplementMissing(session, Tag.BurnedInAnnotation, VR.CS, "YES", attrs);
        if (!attrs.containsValue(Tag.EncapsulatedDocumentLength))
            supplementMissing(session, Tag.EncapsulatedDocumentLength, VR.UL, (int) bulkdata.length, attrs);
        switch (cuid) {
            case UID.EncapsulatedSTLStorage:
            case UID.EncapsulatedMTLStorage:
            case UID.EncapsulatedOBJStorage:
                if (!attrs.contains(Tag.MeasurementUnitsCodeSequence)) {
                    Attributes item = new Attributes(3);
                    item.setString(Tag.CodeValue, VR.SH, "mm");
                    item.setString(Tag.CodingSchemeDesignator, VR.SH, "UCUM");
                    item.setString(Tag.CodeMeaning, VR.LO, "mm");
                    logSupplementMissing(session, Tag.MeasurementUnitsCodeSequence, item);
                    attrs.newSequence(Tag.MeasurementUnitsCodeSequence, 1).add(item);
                }
                if (!attrs.containsValue(Tag.Modality))
                    supplementMissing(session, Tag.Modality, VR.CS, "M3D", attrs);
                if (!attrs.containsValue(Tag.Manufacturer))
                    supplementMissing(session, Tag.Manufacturer, VR.LO, "UNKNOWN", attrs);
                if (!attrs.containsValue(Tag.ManufacturerModelName))
                    supplementMissing(session, Tag.ManufacturerModelName, VR.LO, "UNKNOWN", attrs);
                if (!attrs.containsValue(Tag.DeviceSerialNumber))
                    supplementMissing(session, Tag.DeviceSerialNumber, VR.LO, "UNKNOWN", attrs);
                if (!attrs.containsValue(Tag.SoftwareVersions))
                    supplementMissing(session, Tag.SoftwareVersions, VR.LO, "UNKNOWN", attrs);
                if (!attrs.containsValue(Tag.FrameOfReferenceUID) && !cuid.equals(UID.EncapsulatedMTLStorage))
                    supplementMissing(session, Tag.FrameOfReferenceUID, VR.UI, UIDUtils.createUID(), attrs);
                break;
            case UID.EncapsulatedPDFStorage:
                if (!attrs.containsValue(Tag.Modality))
                    supplementMissing(session, Tag.Modality, VR.CS, "DOC", attrs);
                if (!attrs.containsValue(Tag.ConversionType))
                    supplementMissing(session, Tag.ConversionType, VR.CS, "SD", attrs);
                break;
            case UID.EncapsulatedCDAStorage:
                if (!attrs.containsValue(Tag.Modality))
                    supplementMissing(session, Tag.Modality, VR.CS, "SR", attrs);
                if (!attrs.containsValue(Tag.ConversionType))
                    supplementMissing(session, Tag.ConversionType, VR.CS, "WSD", attrs);
                break;
        }
    }

    private static DicomServiceException missingAttribute(int tag) {
        return new DicomServiceException(Status.IdentifierDoesNotMatchSOPClass,
                "Missing " + DICT.keywordOf(tag) + " " + TagUtils.toString(tag));
    }

    private PathWithMediaType resolveBulkdataRefs(StoreSession session, Attributes attrs) throws DicomServiceException {
        final PathWithMediaType[] bulkdataWithMediaType = new PathWithMediaType[1];
        try {
            attrs.accept((attrs1, tag, vr, value) -> {
                if (value instanceof BulkData)
                    bulkdataWithMediaType[0] = resolveBulkdataRef(session, attrs1, tag, vr, (BulkData) value);

                return true;
            }, true);
        } catch (DicomServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new DicomServiceException(Status.ProcessingFailure, e);
        }
        return bulkdataWithMediaType[0];
    }

    private PathWithMediaType resolveBulkdataRef(StoreSession session, Attributes attrs, int tag, VR vr, BulkData bulkdata)
            throws IOException {
        PathWithMediaType pathWithMediaType = bulkdataMap.get(bulkdata.getURI());
        if (pathWithMediaType == null)
            throw new DicomServiceException(0xA922, "Missing Bulkdata: " + bulkdata.getURI());
        if (tag != Tag.PixelData || MediaType.APPLICATION_OCTET_STREAM_TYPE.equals(pathWithMediaType.mediaType))
            bulkdata.setURI(pathWithMediaType.path.toUri() + "?offset=0&length=" + pathWithMediaType.length);
        else
            addFragments(session, attrs, pathWithMediaType, tag, vr);
        return pathWithMediaType;
    }

    private static void addFragments(StoreSession session, Attributes attrs, PathWithMediaType pathWithMediaType,
                                     int tag, VR vr) {
        String uri = pathWithMediaType.path.toUri().toString();
        long offset = 0;
        long remaining = pathWithMediaType.length;
        long maxFragmentLength = session.getArchiveAEExtension().stowMaxFragmentLength();
        Fragments frags = attrs.newFragments(tag, vr, 2 + (int)((remaining - 1) / maxFragmentLength));
        frags.add(ByteUtils.EMPTY_BYTES);
        while (remaining > maxFragmentLength) {
            frags.add(new BulkData(uri, offset, maxFragmentLength, false));
            offset += maxFragmentLength;
            remaining -= maxFragmentLength;
        }
        frags.add(new BulkData(uri, offset, remaining, false));
    }

    private enum CompressedPixelData {
        JPEG {
            @Override
            String supplementImagePixelModule(StoreSession session, SeekableByteChannel channel, Attributes attrs, boolean fragmented)
                    throws IOException {
                JPEGParser jpegParser = new JPEGParser(channel);
                jpegParser.getAttributes(attrs);
                ArchiveAEExtension arcAE = session.getArchiveAEExtension();
                adjustBulkdata(attrs, jpegParser, arcAE);
                return adjustJPEGTransferSyntax(jpegParser.getTransferSyntaxUID(fragmented),
                        arcAE.stowRetiredTransferSyntax(), attrs);
            }
        },
        MPEG {
            @Override
            String supplementImagePixelModule(StoreSession session, SeekableByteChannel channel, Attributes attrs, boolean fragmented)
                    throws IOException {
                MPEG2Parser mpeg2Parser = new MPEG2Parser(channel);
                mpeg2Parser.getAttributes(attrs);
                return mpeg2Parser.getTransferSyntaxUID(fragmented);
            }
        },
        MP4 {
            @Override
            String supplementImagePixelModule(StoreSession session, SeekableByteChannel channel, Attributes attrs, boolean fragmented)
                    throws IOException {
                MP4Parser mp4Parser = new MP4Parser(channel);
                mp4Parser.getAttributes(attrs);
                adjustBulkdata(attrs, mp4Parser, session.getArchiveAEExtension());
                return mp4Parser.getTransferSyntaxUID(fragmented);
            }
        };

        abstract String supplementImagePixelModule(StoreSession session, SeekableByteChannel channel, Attributes attrs, boolean fragmented)
                throws IOException;

        static CompressedPixelData valueOf(MediaType mediaType) {
            return MediaTypes.equalsIgnoreParameters(mediaType, MediaTypes.IMAGE_JPEG_TYPE)
                        || MediaTypes.equalsIgnoreParameters(mediaType, MediaTypes.IMAGE_JP2_TYPE) ? JPEG
                    : MediaTypes.equalsIgnoreParameters(mediaType, MediaTypes.VIDEO_MPEG_TYPE) ? MPEG
                    : MediaTypes.equalsIgnoreParameters(mediaType, MediaTypes.VIDEO_MP4_TYPE)
                        || MediaTypes.equalsIgnoreParameters(mediaType, MediaTypes.VIDEO_QUICKTIME_TYPE) ? MP4
                    : null;
        }
    }

    private static void adjustBulkdata(Attributes attrs, JPEGParser parser, ArchiveAEExtension arcAE) {
        if (parser.getCodeStreamPosition() > 0) {
            excludeJP2FileFormatHeader(parser, attrs);
            return;
        }

        if (parser.getPositionAfterAPPSegments() != -1 && arcAE.stowExcludeAPPMarkers())
            excludeAppMarkers(attrs, parser);
    }

    private static void excludeJP2FileFormatHeader(JPEGParser parser, Attributes attrs) {
        BulkData bulkData = (BulkData) ((Fragments) attrs.getValue(Tag.PixelData)).get(1);
        bulkData.setLength(bulkData.length() - parser.getCodeStreamPosition());
        bulkData.setOffset(parser.getCodeStreamPosition());
    }

    private static void excludeAppMarkers(Attributes attrs, JPEGParser parser) {
        Fragments fragments = (Fragments) attrs.getValue(Tag.PixelData);
        BulkData bulkData = (BulkData) fragments.get(1);
        fragments.set(1, new BulkDataWithPrefix(
                bulkData.uriWithoutOffsetAndLength(),
                parser.getPositionAfterAPPSegments(),
                (int) (bulkData.length() - parser.getPositionAfterAPPSegments()),
                false,
                (byte) -1, (byte) JPEG.SOI));
    }

    private static String adjustJPEGTransferSyntax(String tsuid, boolean allowRetired, Attributes attrs) {
        return allowRetired || !tsuid.equals(UID.JPEGFullProgressionNonHierarchical1012)
                ? tsuid
                : attrs.getInt(Tag.BitsAllocated, 8) == 8
                ? UID.JPEGBaseline8Bit
                : UID.JPEGExtended12Bit;
    }

    private static void adjustBulkdata(Attributes attrs, MP4Parser parser, ArchiveAEExtension arcAE) throws IOException {
        if (parser.getMP4FileType() != null
                && parser.getMP4FileType().majorBrand() == MP4FileType.qt
                && arcAE.stowQuicktime2MP4()) {
            Fragments fragments = (Fragments) attrs.getValue(Tag.PixelData);
            BulkData bulkData = (BulkData) fragments.get(1);
            File mpFile = qt2MP4(bulkData.getFile());
            bulkData.setURI(mpFile.toURI().toString());
            bulkData.setLength(mpFile.length());
        }
    }

    private static File qt2MP4(File qtFile) throws IOException {
        File mp4File = new File(qtFile.getParent(), qtFile.getName() + ".mp4");
        long start = System.currentTimeMillis();
        Process process = new ProcessBuilder(
                "ffmpeg", "-i", qtFile.toString(), "-c", "copy", mp4File.toString())
                .redirectErrorStream(true)
                .start();
        String line;
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        while ((line = reader.readLine()) != null) {
            LOG.debug(line);
        }
        int exitValue = exitValueOf(process);
        long end = System.currentTimeMillis();
        LOG.info("Converted Quicktime to MP4 container in {} ms - exit code: {}", start - end, exitValue);
        if (exitValue != 0) {
            throw new IOException("Failed to convert Quicktime to MP4 container - " + exitValue);
        }
        return mp4File;
    }

    private static int exitValueOf(Process process) {
        try {
            return process.waitFor();
        } catch (InterruptedException e) {
            return 999;
        }
    }

    private boolean spoolBulkdata(MultipartInputStream in, MediaType mediaType,
                                  String contentLocation) {
        try {
            if (spoolDirectory == null)
                spoolDirectory = Files.createTempDirectory(spoolDirectoryRoot(), null);
            java.nio.file.Path spoolFile = Files.createTempFile(spoolDirectory, null, null);
            try (OutputStream out = Files.newOutputStream(spoolFile)) {
                StreamUtils.copy(in, out);
            }
            bulkdataMap.put(contentLocation, new PathWithMediaType(spoolFile, mediaType));
            return true;
        } catch (IOException e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            LOG.error(sw.toString());
            throw new WebApplicationException(
                    errResponse("IOException caught while spooling bulkdata : " + e.getMessage(),
                    Response.Status.BAD_REQUEST));
        }
    }

    private java.nio.file.Path spoolDirectoryRoot() throws IOException {
        return  Files.createDirectories(Paths.get(StringUtils.replaceSystemProperties(
                device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class).getStowSpoolDirectory())));
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
        if (ctx.getAttributes() != null) {
            attrs.setString(Tag.ReferencedSOPClassUID, VR.UI, ctx.getSopClassUID());
            attrs.setString(Tag.ReferencedSOPInstanceUID, VR.UI, ctx.getSopInstanceUID());
        } else {
            attrs.setString(Tag.ReferencedSOPClassUID, VR.UI, NOT_A_DICOM_FILE_UID);
            attrs.setString(Tag.ReferencedSOPInstanceUID, VR.UI, UIDUtils.createUID());
        }
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

    private static class PathWithMediaType {
        final java.nio.file.Path path;
        final long length;
        final MediaType mediaType;

        private PathWithMediaType(java.nio.file.Path path, MediaType mediaType) throws IOException {
            this.path = path;
            this.length = Files.size(path);
            this.mediaType = mediaType;
        }
    }

    private Response errResponse(String errorMessage, Response.Status status) {
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
