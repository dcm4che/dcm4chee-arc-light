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

package org.dcm4chee.arc.qmgt.rs;

import org.dcm4che3.conf.json.JsonWriter;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.conf.QueueDescriptor;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Oct 2015
 */
@Path("queue")
@RequestScoped
public class QueryQueues {
    private static final Logger LOG = LoggerFactory.getLogger(QueryQueues.class);

    @Inject
    private Device device;

    @Context
    private HttpServletRequest request;

    @QueryParam("includeExporters")
    @Pattern(regexp = "true|false")
    private String includeExporters;

    @Override
    public String toString() {
        String requestURI = request.getRequestURI();
        String queryString = request.getQueryString();
        return queryString == null ? requestURI : requestURI + '?' + queryString;
    }

    @GET
    @NoCache
    @Produces("application/json")
    public StreamingOutput query() {
        logRequest();
        try {
            return out -> {
                JsonGenerator gen = Json.createGenerator(out);
                gen.writeStartArray();
                writeQueues(gen);
                gen.writeEnd();
                gen.flush();
            };
        } catch (Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    private void writeQueuesWithExporters(JsonGenerator gen) {
        associatedQueuesExporters().forEach((queueName, queueDescExporterIDs) -> {
            JsonWriter writer = new JsonWriter(gen);
            gen.writeStartObject();
            writer.writeNotNullOrDef("name", queueName, null);
            writer.writeNotNullOrDef("description", queueDescExporterIDs.getQueue().getDescription(), null);
            writer.writeNotEmpty("exporterIDs", queueDescExporterIDs.getExporterIDs().toArray(new String[0]));
            gen.writeEnd();
        });
    }

    private void writeQueues(JsonGenerator gen) {
        if (Boolean.parseBoolean(includeExporters)) {
            writeQueuesWithExporters(gen);
            return;
        }
        for (QueueDescriptor queueDesc : sortedQueueDescriptors()) {
            JsonWriter writer = new JsonWriter(gen);
            gen.writeStartObject();
            writer.writeNotNullOrDef("name", queueDesc.getQueueName(), null);
            writer.writeNotNullOrDef("description", queueDesc.getDescription(), null);
            gen.writeEnd();
        }
    }

    private QueueDescriptor[] sortedQueueDescriptors() {
        return device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class)
                .getQueueDescriptors().stream()
                .sorted(Comparator.comparing(QueueDescriptor::getQueueName))
                .toArray(QueueDescriptor[]::new);
    }

    private Map<String, QueueDescExporterIDs> associatedQueuesExporters() {
        Map<String, List<String>> queueNameExporterIDs = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class)
                .getExporterDescriptors().stream()
                .collect(Collectors.groupingBy(ExporterDescriptor::getQueueName,
                        Collectors.mapping(ExporterDescriptor::getExporterID,
                                Collectors.toList())));

        return device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class)
                .getQueueDescriptors().stream()
                .filter(qd -> queueNameExporterIDs.containsKey(qd.getQueueName()))
                .collect(Collectors.toMap(QueueDescriptor::getQueueName,
                        qd -> new QueueDescExporterIDs(qd, queueNameExporterIDs.get(qd.getQueueName())),
                        (v1, v2) -> v1,
                        TreeMap::new));
    }

    static class QueueDescExporterIDs {
        private QueueDescriptor queue;
        private List<String> exporterIDs;

        public QueueDescExporterIDs(QueueDescriptor queue, List<String> exporterIDs) {
            this.queue = queue;
            this.exporterIDs = exporterIDs;
        }

        public QueueDescriptor getQueue() {
            return queue;
        }

        public List<String> getExporterIDs() {
            return exporterIDs;
        }
    }

    private void logRequest() {
        LOG.info("Process {} {} from {}@{}",
                request.getMethod(),
                toString(),
                request.getRemoteUser(),
                request.getRemoteHost());
    }

    private Response errResponseAsTextPlain(String errorMsg, Response.Status status) {
        LOG.warn("Response {} caused by {}", status, errorMsg);
        return Response.status(status)
                .entity(errorMsg)
                .type("text/plain")
                .build();
    }

    private String exceptionAsString(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
