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
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.StorageDescriptor;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.storage.ReadContext;
import org.dcm4chee.arc.storage.Storage;
import org.dcm4chee.arc.storage.StorageFactory;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreService;
import org.dcm4chee.arc.store.StoreSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.*;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jul 2019
 */
@RequestScoped
@Path("aets/{AETitle}/rs")
public class ImportStorageRS {

    private static final Logger LOG = LoggerFactory.getLogger(ImportStorageRS.class);

    @Inject
    private StorageFactory storageFactory;

    @Inject
    private StoreService service;

    @Context
    private HttpServletRequest request;

    @Inject
    private Device device;

    @PathParam("AETitle")
    private String aet;

    @QueryParam("readPixelData")
    @Pattern(regexp = "true|false")
    private String readPixelData;

    private final Attributes response = new Attributes();
    private final Set<String> studyInstanceUIDs = new HashSet<>();
    private Sequence sopSequence;
    private Sequence failedSOPSequence;

    @POST
    @Path("/instances/storage/{StorageID}")
    @Consumes("text/*")
    @Produces("application/dicom+xml")
    public void importInstancesXML(
            @PathParam("StorageID") String storageID,
            @Suspended AsyncResponse ar,
            InputStream in) {
        importInstanceOnStorage(ar, in, storageID, OutputType.DICOM_XML);
    }

    @POST
    @Path("/instances/storage/{StorageID}")
    @Consumes("text/*")
    @Produces("application/dicom+json")
    public void importInstancesJSON(
            @PathParam("StorageID") String storageID,
            @Suspended AsyncResponse ar,
            InputStream in) {
        importInstanceOnStorage(ar, in, storageID, OutputType.JSON);
    }

    private void importInstanceOnStorage(AsyncResponse ar, InputStream in, String storageID, OutputType output) {
        logRequest();
        ApplicationEntity ae = getApplicationEntity();
        Storage storage = storageFactory.getStorage(getStorageDesc(storageID));
        final StoreSession session = service.newStoreSession(
                HttpServletRequestInfo.valueOf(request), ae, null)
                .withObjectStorageID(storageID);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            reader.lines().forEach(storagePath -> importInstanceOnStorage(storage, session, storagePath));
        } catch (Exception e) {
            throw new WebApplicationException(errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }

        response.setString(Tag.RetrieveURL, VR.UR, retrieveURL());
        Response.ResponseBuilder responseBuilder = Response.status(status());
        ar.resume(responseBuilder
                .entity(output.entity(response))
                .header("Warning", response.getString(Tag.ErrorComment))
                .build());
    }

    private void importInstanceOnStorage(Storage storage, StoreSession session, String storagePath) {
        ReadContext readContext = createReadContext(storage, storagePath);
        StoreContext ctx = service.newStoreContext(session);
        try {
            Attributes attrs = getAttributes(storage, session, readContext, ctx);
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
            ctx.setReceiveTransferSyntax(dis.getTransferSyntax());
            return dis.readDataset(-1, stopTag(storage));
        }
    }

    private int stopTag(Storage storage) {
        return Boolean.parseBoolean(readPixelData) || storage.getStorageDescriptor().getDigestAlgorithm() != null
                ? -1
                : Tag.PixelData;
    }

    private ReadContext createReadContext(Storage storage, String storagePath) {
        ReadContext readContext = storage.createReadContext();
        readContext.setStoragePath(storagePath);
        readContext.setMessageDigest(storage.getStorageDescriptor().getMessageDigest());
        return readContext;
    }

    private void logRequest() {
        LOG.info("Process {} {}?{} from {}@{}",
                request.getMethod(),
                request.getRequestURI(),
                request.getQueryString(),
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
