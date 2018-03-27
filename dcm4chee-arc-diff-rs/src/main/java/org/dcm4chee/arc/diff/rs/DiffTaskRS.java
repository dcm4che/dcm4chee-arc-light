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
 * Portions created by the Initial Developer are Copyright (C) 2015-2018
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

package org.dcm4chee.arc.diff.rs;

import org.dcm4che3.net.Device;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.diff.DiffService;
import org.dcm4chee.arc.entity.DiffTask;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.query.util.MatchTask;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.*;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Mar 2018
 */
@RequestScoped
@Path("monitor/diff")
public class DiffTaskRS {
    private static final Logger LOG = LoggerFactory.getLogger(DiffTaskRS.class);

    @Inject
    private Device device;

    @Inject
    private DiffService diffService;

    @Context
    private HttpHeaders httpHeaders;

    @Context
    private HttpServletRequest request;

    @QueryParam("dicomDeviceName")
    private String deviceName;

    @QueryParam("LocalAET")
    private String localAET;

    @QueryParam("PrimaryAET")
    private String primaryAET;

    @QueryParam("SecondaryAET")
    private String secondaryAET;

    @QueryParam("checkDifferent")
    @Pattern(regexp = "true|false")
    private String checkDifferent;

    @QueryParam("checkMissing")
    @Pattern(regexp = "true|false")
    private String checkMissing;

    @QueryParam("status")
    @Pattern(regexp = "SCHEDULED|IN PROCESS|COMPLETED|WARNING|FAILED|CANCELED")
    private String status;

    @QueryParam("createdTime")
    private String createdTime;

    @QueryParam("updatedTime")
    private String updatedTime;

    @QueryParam("batchID")
    private String batchID;

    @QueryParam("comparefield")
    private String comparefields;

    @QueryParam("offset")
    @Pattern(regexp = "0|([1-9]\\d{0,4})")
    private String offset;

    @QueryParam("limit")
    @Pattern(regexp = "[1-9]\\d{0,4}")
    private String limit;

    @QueryParam("orderby")
    @Pattern(regexp = "(-?)createdTime|(-?)updatedTime")
    private String orderby;

    @GET
    @NoCache
    public Response listCompareStudiesTasks(@QueryParam("accept") String accept) {
        logRequest();
        Output output = selectMediaType(accept);
        if (output == null)
            return Response.notAcceptable(
                    Variant.mediaTypes(MediaType.APPLICATION_JSON_TYPE, MediaTypes.TEXT_CSV_UTF8_TYPE).build())
                    .build();

        List<DiffTask> diffTasks = diffService.listDiffTasks(
                MatchTask.matchQueueMessage(
                        null, deviceName, status(), batchID, null, null, null, null),
                MatchTask.matchDiffTask(localAET, primaryAET, secondaryAET, checkDifferent, checkMissing,
                        comparefields, createdTime, updatedTime),
                parseInt(offset), parseInt(limit), orderby);

        return Response.ok(output.entity(diffTasks), output.type).build();
    }

    @GET
    @NoCache
    @Path("/count")
    @Produces("application/json")
    public Response countRetrieveTasks() {
        logRequest();
        return count(diffService.countDiffTasks(
                MatchTask.matchQueueMessage(
                        null, deviceName, status(), batchID, null, null, null, null),
                MatchTask.matchDiffTask(localAET, primaryAET, secondaryAET, checkDifferent, checkMissing,
                        comparefields, createdTime, updatedTime)));
    }

    private Output selectMediaType(String accept) {
        Stream<MediaType> acceptableTypes = httpHeaders.getAcceptableMediaTypes().stream();
        if (accept != null) {
            try {
                MediaType type = MediaType.valueOf(accept);
                return acceptableTypes.anyMatch(type::isCompatible) ? Output.valueOf(type) : null;
            } catch (IllegalArgumentException ae) {
                return null;
            }
        }
        return acceptableTypes.map(Output::valueOf)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private enum Output {
        JSON(MediaType.APPLICATION_JSON_TYPE) {
            @Override
            Object entity(final List<DiffTask> tasks) {
                return (StreamingOutput) out -> {
                    JsonGenerator gen = Json.createGenerator(out);
                    gen.writeStartArray();
                    for (DiffTask task : tasks)
                        task.writeAsJSONTo(gen);
                    gen.writeEnd();
                    gen.flush();
                };
            }
        },
        CSV(MediaTypes.TEXT_CSV_UTF8_TYPE) {
            @Override
            Object entity(final List<DiffTask> tasks) {
                return (StreamingOutput) out -> {
                    Writer writer = new BufferedWriter(new OutputStreamWriter(out, "UTF-8"));
                    DiffTask.writeCSVHeader(writer, delimiter);
                    for (DiffTask task : tasks)
                        task.writeAsCSVTo(writer, delimiter);
                    writer.flush();
                };
            }
        };

        private static char delimiter;
        final MediaType type;

        Output(MediaType type) {
            this.type = type;
        }

        static Output valueOf(MediaType type) {
            return MediaType.APPLICATION_JSON_TYPE.isCompatible(type) ? Output.JSON
                    : isCSV(type) ? Output.CSV
                    : null;
        }

        private static boolean isCSV(MediaType type) {
            boolean csvCompatible = MediaTypes.TEXT_CSV_UTF8_TYPE.isCompatible(type);
            delimiter = csvCompatible
                    && type.getParameters().keySet().contains("delimiter")
                    && type.getParameters().get("delimiter").equals("semicolon")
                    ? ';' : ',';
            return csvCompatible;
        }

        abstract Object entity(final List<DiffTask> tasks);
    }

    private void logRequest() {
        LOG.info("Process GET {} from {}@{}", request.getRequestURI(),
                request.getRemoteUser(), request.getRemoteHost());
    }

    private static Response count(long count) {
        return rsp(Response.Status.OK, "{\"count\":" + count + '}');
    }

    private static Response rsp(Response.Status status, Object entity) {
        return Response.status(status).entity(entity).build();
    }

    private static int parseInt(String s) {
        return s != null ? Integer.parseInt(s) : 0;
    }

    private QueueMessage.Status status() {
        return status != null ? QueueMessage.Status.fromString(status) : null;
    }
}
