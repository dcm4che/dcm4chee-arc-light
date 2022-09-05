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
 * Portions created by the Initial Developer are Copyright (C) 2013-2021
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

package org.dcm4chee.arc.restore.rs;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.json.JSONReader;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.StorageDescriptor;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.restore.RestoreFromMetadata;
import org.dcm4chee.arc.storage.ReadContext;
import org.dcm4chee.arc.storage.Storage;
import org.dcm4chee.arc.storage.StorageFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipInputStream;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Aug 2022
 */
@RequestScoped
@Path("/")
public class ImportMetadataRS {

    private static final Logger LOG = LoggerFactory.getLogger(ImportMetadataRS.class);

    @Inject
    private StorageFactory storageFactory;

    @Inject
    private RestoreFromMetadata restoreFromMetadata;

    @Inject
    private Device device;

    @Context
    private HttpServletRequest request;

    @Context
    private HttpHeaders httpHeaders;

    private List<Result> completed = new ArrayList<>();
    private List<Failure> failures = new ArrayList<>();

    @POST
    @Path("/metadata/storage/{StorageID}")
    @Consumes("text/*")
    @Produces("application/json")
    public Response importMetadata(@PathParam("StorageID") String storageID, InputStream in) {
        logRequest();
        StorageDescriptor storageDescriptor =
                device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class).getStorageDescriptor(storageID);
        if (storageDescriptor == null)
            return errResponse(
                    "No such Storage: " + storageID, Response.Status.NOT_FOUND);
        try {
            Storage storage = storageFactory.getStorage(storageDescriptor);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            reader.lines().forEach(storagePath ->
                    importMetadataOnStorage(storage, storagePath, HttpServletRequestInfo.valueOf(request)));
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e),
                    Response.Status.INTERNAL_SERVER_ERROR);
        }

        return Response.status(failures.isEmpty() ? Response.Status.OK
                        : completed.isEmpty() ? Response.Status.CONFLICT
                        : Response.Status.ACCEPTED)
                .entity((StreamingOutput) this::writeTo)
                .build();
    }

    private void writeTo(OutputStream out) throws IOException, WebApplicationException {
        JsonGenerator gen = Json.createGenerator(out);
        gen.writeStartObject();
        gen.write("completed", completed.size());
        if (!failures.isEmpty()) gen.write("failed", failures.size());
        if (!completed.isEmpty()) {
            gen.writeStartArray("series");
            completed.forEach(result -> result.writeTo(gen));
            gen.writeEnd();
        }
        if (!failures.isEmpty()) {
            gen.writeStartArray("failures");
            failures.forEach(failure -> failure.writeTo(gen));
            gen.writeEnd();
        }
        gen.writeEnd();
        gen.flush();
    }

    private void importMetadataOnStorage(Storage storage, String storagePath, HttpServletRequestInfo httpRequest) {
        ReadContext readContext = storage.createReadContext();
        readContext.setStoragePath(storagePath);
        try {
            List<Attributes> seriesMetadata = new ArrayList<>();
            try (ZipInputStream zip = new ZipInputStream(storage.openInputStream(readContext))) {
                if (zip.getNextEntry() == null) throw new IOException("No ZIP Entry in " + storagePath);
                do {
                    JSONReader jsonReader = new JSONReader(
                            Json.createParser(new InputStreamReader(zip, StandardCharsets.UTF_8)));
                    seriesMetadata.add(jsonReader.readDataset(null));
                    zip.closeEntry();
                } while ((zip.getNextEntry()) != null);
            }
            restoreFromMetadata.restore(readContext, seriesMetadata, httpRequest);
            completed.add(new Result(storagePath,
                    seriesMetadata.get(0).getString(Tag.StudyInstanceUID),
                    seriesMetadata.get(0).getString(Tag.SeriesInstanceUID),
                    seriesMetadata.size()));
        } catch (Exception e) {
           failures.add(new Failure(storagePath, e));
        }
    }

    private void logRequest() {
        LOG.info("Process {} {} from {}@{}",
                request.getMethod(),
                request.getRequestURI(),
                request.getRemoteUser(),
                request.getRemoteHost());
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

    private static class Result {
        private final String storagePath;
        private final String studyInstanceUID;
        private final String seriesInstanceUID;
        private final int numberOfSeriesRelatedInstances;

        public Result(String storagePath,
                      String studyInstanceUID,
                      String seriesInstanceUID,
                      int numberOfSeriesRelatedInstances) {
            this.storagePath = storagePath;
            this.studyInstanceUID = studyInstanceUID;
            this.seriesInstanceUID = seriesInstanceUID;
            this.numberOfSeriesRelatedInstances = numberOfSeriesRelatedInstances;
        }

        public void writeTo(JsonGenerator gen) {
            gen.writeStartObject();
            gen.write("storagePath", storagePath);
            gen.write("StudyInstanceUID", studyInstanceUID);
            gen.write("SeriesInstanceUID", seriesInstanceUID);
            gen.write("NumberOfSeriesRelatedInstances", numberOfSeriesRelatedInstances);
            gen.writeEnd();
        }
    }

    private static class Failure {
        private final String storagePath;
        private final Exception cause;

        public Failure(String storagePath, Exception cause) {
            this.storagePath = storagePath;
            this.cause = cause;
        }

        public void writeTo(JsonGenerator gen) {
            gen.writeStartObject();
            gen.write("storagePath", storagePath);
            gen.write("errorMessage", rootCauseOf(cause).toString());
            gen.writeEnd();
        }

        private static Throwable rootCauseOf(Throwable cause) {
            while (cause.getCause() != null) cause = cause.getCause();
            return cause;
        }
    }
}
