/*
 * **** BEGIN LICENSE BLOCK *****
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
 * Portions created by the Initial Developer are Copyright (C) 2015-2019
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
 * **** END LICENSE BLOCK *****
 *
 */

package org.dcm4chee.arc.stow;

import org.dcm4che3.data.*;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.RSOperation;
import org.dcm4chee.arc.conf.StorageDescriptor;
import org.dcm4chee.arc.delete.DeletionService;
import org.dcm4chee.arc.delete.StudyNotEmptyException;
import org.dcm4chee.arc.delete.StudyNotFoundException;
import org.dcm4chee.arc.entity.Location;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.query.util.QueryAttributes;
import org.dcm4chee.arc.rs.client.RSForward;
import org.dcm4chee.arc.storage.ReadContext;
import org.dcm4chee.arc.storage.Storage;
import org.dcm4chee.arc.storage.StorageFactory;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreService;
import org.dcm4chee.arc.store.StoreSession;
import org.dcm4chee.arc.validation.constraints.InvokeValidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.*;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jul 2019
 */
@RequestScoped
@Path("aets/{AETitle}/rs")
@InvokeValidate(type = ImportStorageRS.class)
public class ImportStorageRS {

    private static final Logger LOG = LoggerFactory.getLogger(ImportStorageRS.class);

    @Inject
    private StorageFactory storageFactory;

    @Inject
    private StoreService service;

    @Inject
    private DeletionService deletionService;

    @Inject
    private RSForward rsForward;

    @Context
    private HttpServletRequest request;

    @Context
    private UriInfo uriInfo;

    @Inject
    private Device device;

    @PathParam("AETitle")
    private String aet;

    @QueryParam("readPixelData")
    @Pattern(regexp = "true|false")
    private String readPixelData;

    @QueryParam("updatePolicy")
    @Pattern(regexp = "SUPPLEMENT|MERGE|OVERWRITE")
    @DefaultValue("OVERWRITE")
    private String updatePolicy;

    @QueryParam("reasonForModification")
    @Pattern(regexp = "COERCE|CORRECT")
    private String reasonForModification;

    @QueryParam("sourceOfPreviousValues")
    private String sourceOfPreviousValues;

    @Context
    private HttpHeaders httpHeaders;

    @Override
    public String toString() {
        String requestURI = request.getRequestURI();
        String queryString = request.getQueryString();
        return queryString == null ? requestURI : requestURI + '?' + queryString;
    }

    private final Attributes response = new Attributes();
    private final Set<String> studyInstanceUIDs = new HashSet<>();
    private Sequence sopSequence;
    private Sequence failedSOPSequence;

    @POST
    @Path("/instances/storage/{StorageID}")
    @Consumes("text/*")
    public void importInstances(
            @PathParam("StorageID") String storageID,
            @Suspended AsyncResponse ar,
            InputStream in) {
        importInstanceOnStorage(ar, in, storageID, selectMediaType());
    }

    public void validate() {
        logRequest();
        new QueryAttributes(uriInfo, null);
    }

    @POST
    @Path("/studies/{study}/reimport")
    public void reimportStudy(@Suspended AsyncResponse ar, @PathParam("study") String studyUID) {
        Output output = selectMediaType();
        ApplicationEntity ae = getApplicationEntity();
        try {
            ArchiveAEExtension arcAE = ae.getAEExtensionNotNull(ArchiveAEExtension.class);
            List<Location> locations = deletionService.reimportStudy(
                                            studyUID, HttpServletRequestInfo.valueOf(request), arcAE);
            Attributes coerce = new QueryAttributes(uriInfo, null).getQueryKeys();
            Date now = reasonForModification != null && !coerce.isEmpty() ? new Date() : null;
            Attributes.UpdatePolicy updatePolicy = Attributes.UpdatePolicy.valueOf(this.updatePolicy);
            for (Location location : locations) {
                if (location.getObjectType() == Location.ObjectType.METADATA)
                    continue;

                Storage storage = storageFactory.getStorage(getStorageDesc(location.getStorageID()));
                final StoreSession session = service.newStoreSession(
                        HttpServletRequestInfo.valueOf(request), ae, null)
                        .withObjectStorageID(location.getStorageID());
                StoreContext ctx = service.newStoreContext(session);
                ctx.getLocations().add(location);
                importInstanceOnStorage(
                        storage, ctx, coerce, updatePolicy, now, location.getStoragePath());
            }
            rsForward.forward(RSOperation.ReimportStudy, arcAE, null, request);
        } catch (StudyNotFoundException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (StudyNotEmptyException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.FORBIDDEN);
        } catch (Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }
        response.setString(Tag.RetrieveURL, VR.UR, retrieveURL());
        Response.ResponseBuilder responseBuilder = Response.status(status());
        ar.resume(responseBuilder
                .entity(output.entity(response, ae))
                .header("Warning", response.getString(Tag.ErrorComment))
                .build());
    }

    private void importInstanceOnStorage(AsyncResponse ar, InputStream in, String storageID, Output output) {
        ApplicationEntity ae = getApplicationEntity();
        Storage storage = storageFactory.getStorage(getStorageDesc(storageID));
        final StoreSession session = service.newStoreSession(
                HttpServletRequestInfo.valueOf(request), ae, null)
                .withObjectStorageID(storageID);
        Attributes coerce = new QueryAttributes(uriInfo, null).getQueryKeys();
        Date now = reasonForModification != null && !coerce.isEmpty() ? new Date() : null;
        Attributes.UpdatePolicy updatePolicy = Attributes.UpdatePolicy.valueOf(this.updatePolicy);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            reader.lines().forEach(storagePath ->
                    importInstanceOnStorage(
                            storage, service.newStoreContext(session), coerce, updatePolicy, now, storagePath));
        } catch (Exception e) {
            throw new WebApplicationException(errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }

        response.setString(Tag.RetrieveURL, VR.UR, retrieveURL());
        Response.ResponseBuilder responseBuilder = Response.status(status());
        ar.resume(responseBuilder
                .entity(output.entity(response, ae))
                .header("Warning", response.getString(Tag.ErrorComment))
                .build());
    }

    private void importInstanceOnStorage(Storage storage, StoreContext ctx, Attributes coerce,
            Attributes.UpdatePolicy updatePolicy, Date now, String storagePath) {
        ReadContext readContext = createReadContext(storage, storagePath);
        StoreSession session = ctx.getStoreSession();
        try {
            Attributes attrs = getAttributes(storage, session, readContext, ctx);
            if (!coerce.isEmpty()) {
                Attributes modified = new Attributes();
                attrs.update(updatePolicy, false, coerce, modified);
                if (!modified.isEmpty() && reasonForModification != null) {
                    attrs.addOriginalAttributes(sourceOfPreviousValues, now,
                            reasonForModification, device.getDeviceName(), modified);
                }
            }
            service.importInstanceOnStorage(ctx, attrs, readContext);
            studyInstanceUIDs.add(ctx.getStudyInstanceUID());
            sopSequence().add(mkSOPRefWithRetrieveURL(ctx));
        } catch (DicomServiceException e) {
            LOG.info("{}: Failed to import instance on storage SopClassUID={}, StoragePath={}",
                    session, UID.nameOf(ctx.getSopClassUID()), storagePath, e);
            response.setString(Tag.ErrorComment, VR.LO, e.getMessage());
            failedSOPSequence().add(mkSOPRefWithFailureReason(ctx, e));
        } catch (IOException e) {
            LOG.info("{}: Failed to import instance on storage SopClassUID={}, StoragePath={}",
                    session, UID.nameOf(ctx.getSopClassUID()), storagePath, e);
            response.setString(Tag.ErrorComment, VR.LO, e.getMessage());
        }
    }

    private Attributes getAttributes(Storage storage, StoreSession session, ReadContext readContext, StoreContext ctx)
            throws IOException {
        try (DicomInputStream dis = new DicomInputStream(storage.openInputStream(readContext))) {
            dis.setIncludeBulkData(DicomInputStream.IncludeBulkData.URI);
            dis.setBulkDataDescriptor(session.getArchiveAEExtension().getBulkDataDescriptor());
            dis.setURI("java:iis"); // avoid copy of bulkdata to temporary file
            dis.readFileMetaInformation();
            ctx.setReceiveTransferSyntax(dis.getTransferSyntax());
            return Boolean.parseBoolean(readPixelData) || storage.getStorageDescriptor().getDigestAlgorithm() != null
                    ? dis.readDataset() : dis.readDatasetUntilPixelData();
        }
    }

    private Output selectMediaType() {
        return httpHeaders.getAcceptableMediaTypes()
                .stream()
                .map(Output::valueOf)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> {
                        LOG.warn("Response Status : Not Acceptable. Accept Media Type(s) in request : \n{}",
                                httpHeaders.getAcceptableMediaTypes().stream()
                                        .map(MediaType::toString)
                                        .collect(Collectors.joining("\n")));
                        return new WebApplicationException(Response.notAcceptable(
                                Variant.mediaTypes(MediaTypes.APPLICATION_DICOM_JSON_TYPE,
                                        MediaTypes.APPLICATION_DICOM_XML_TYPE).build())
                                .build());
                    }
                );
    }

    private enum Output {
        JSON(MediaTypes.APPLICATION_DICOM_JSON_TYPE) {
            @Override
            Object entity(final Attributes response, ApplicationEntity ae) {
                return OutputType.JSON.entity(response, ae);
            }
        },
        XML(MediaTypes.APPLICATION_DICOM_XML_TYPE) {
            @Override
            Object entity(final Attributes response, ApplicationEntity ae) {
                return OutputType.DICOM_XML.entity(response, ae);
            }
        };

        final MediaType type;

        Output(MediaType type) {
            this.type = type;
        }

        static Output valueOf(MediaType type) {
            return MediaTypes.APPLICATION_DICOM_JSON_TYPE.isCompatible(type) ? Output.JSON
                    : MediaTypes.APPLICATION_DICOM_XML_TYPE.isCompatible(type) ? Output.XML
                    : null;
        }

        abstract Object entity(final Attributes response, ApplicationEntity ae);
    }

    private ReadContext createReadContext(Storage storage, String storagePath) {
        ReadContext readContext = storage.createReadContext();
        readContext.setStoragePath(storagePath);
        readContext.setMessageDigest(storage.getStorageDescriptor().getMessageDigest());
        return readContext;
    }

    private void logRequest() {
        LOG.info("Process {} {} from {}@{}",
                request.getMethod(),
                toString(),
                request.getRemoteUser(),
                request.getRemoteHost());
    }

    private ApplicationEntity getApplicationEntity() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        if (ae == null || !ae.isInstalled())
            throw new WebApplicationException(errResponse(
                    "No such Application Entity: " + aet, Response.Status.NOT_FOUND));
        return ae;
    }

    private StorageDescriptor getStorageDesc(String storageID) {
        StorageDescriptor storageDescriptor =
                device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class).getStorageDescriptor(storageID);
        if (storageDescriptor == null)
            throw new WebApplicationException(errResponse(
                    "No such Storage: " + storageID, Response.Status.NOT_FOUND));

        return storageDescriptor;
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

    private Attributes mkSOPRefWithRetrieveURL(StoreContext ctx) {
        Attributes attrs = mkSOPRef(ctx, 3);
        attrs.setString(Tag.RetrieveURL, VR.UR, retrieveURL(ctx));
        return attrs;
    }

    private Attributes mkSOPRefWithFailureReason(StoreContext ctx, DicomServiceException e) {
        Attributes attrs = mkSOPRef(ctx, 3);
        attrs.setInt(Tag.FailureReason, VR.US, e.getStatus());
        return attrs;
    }

    private String retrieveURL(StoreContext ctx) {
        StringBuffer requestURL = request.getRequestURL();
        return requestURL.substring(0, requestURL.indexOf("/rs") + 3)
                + "/studies/" + ctx.getStudyInstanceUID()
                + "/series/" + ctx.getSeriesInstanceUID()
                + "/instances/" + ctx.getSopInstanceUID();
    }

    private Attributes mkSOPRef(StoreContext ctx, int size) {
        Attributes attrs = new Attributes(size);
        attrs.setString(Tag.ReferencedSOPClassUID, VR.UI, ctx.getSopClassUID());
        attrs.setString(Tag.ReferencedSOPInstanceUID, VR.UI, ctx.getSopInstanceUID());
        return attrs;
    }

    private String retrieveURL() {
        if (studyInstanceUIDs.size() != 1)
            return null;

        StringBuffer requestURL = request.getRequestURL();
        return requestURL.substring(0, requestURL.indexOf("/rs") + 3)
                + "/studies/" + studyInstanceUIDs.iterator().next();
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
