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
 * **** END LICENSE BLOCK *****
 */
package org.dcm4chee.arc.export.rs;

import com.querydsl.core.Tuple;
import org.dcm4che3.net.Device;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.entity.QExportTask;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.export.mgt.ExportManager;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.validation.constraints.Pattern;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Mar 2017
 */
@RequestScoped
@Path("monitor/export")
public class ExportTaskRS {
    private static final Logger LOG = LoggerFactory.getLogger(ExportTaskRS.class);

    @Inject
    private ExportManager mgr;

    @Inject
    private Device device;

    @QueryParam("studyUID")
    private String studyUID;

    @QueryParam("exporterID")
    private String exporterID;

    @QueryParam("status")
    @Pattern(regexp = "TO_SCHEDULE|SCHEDULED|IN PROCESS|COMPLETED|WARNING|FAILED|CANCELED")
    private String status;

    @QueryParam("updatedBefore")
    @Pattern(regexp = "(19|20)\\d{2}\\-\\d{2}\\-\\d{2}")
    private String updatedBefore;

    @QueryParam("offset")
    @Pattern(regexp = "0|([1-9]\\d{0,4})")
    private String offset;

    @QueryParam("limit")
    @Pattern(regexp = "[1-9]\\d{0,4}")
    private String limit;


    @GET
    @NoCache
    @Produces("application/json")
    public Response search() throws Exception {
        return Response.ok(toEntity(mgr.search(exporterID, studyUID, parseStatus(status), parseInt(offset), parseInt(limit))))
                .build();
    }

    private Object toEntity(final List<Tuple> tasks) {
        return new StreamingOutput() {
            @Override
            public void write(OutputStream out) throws IOException {
                Writer w = new OutputStreamWriter(out, "UTF-8");
                int count = 0;
                w.write('[');
                for (Tuple task : tasks) {
                    if (count++ > 0)
                        w.write(',');
                    writeAsJSON(w, task);
                }
                w.write(']');
                w.flush();
            }
        };
    }

    private static QueueMessage.Status parseStatus(String s) {
        return s != null
                ? s.equals("IN PROCESS")
                ? QueueMessage.Status.IN_PROCESS
                : QueueMessage.Status.valueOf(s)
                : null;
    }

    private static int parseInt(String s) {
        return s != null ? Integer.parseInt(s) : 0;
    }

    public void writeAsJSON(Writer out, Tuple tuple) throws IOException {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
        out.write("{\"id\":\"");
        out.write(tuple.get(QExportTask.exportTask.exporterID));
        ExporterDescriptor ed = device.getDeviceExtension(ArchiveDeviceExtension.class)
                .getExporterDescriptor(tuple.get(QExportTask.exportTask.exporterID));
        out.write("\",\"queue\":\"");
        out.write(ed.getQueueName());
        out.write("\",\"status\":\"");
        out.write(tuple.get(QExportTask.exportTask.status).toString());
        if (tuple.get(QExportTask.exportTask.messageID) != null) {
            out.write("\",\"taskID\":\"");
            out.write(tuple.get(QExportTask.exportTask.messageID));
        }
        out.write(",\"studyUID\":\"");
        out.write(tuple.get(QExportTask.exportTask.studyInstanceUID));
        if (!tuple.get(QExportTask.exportTask.seriesInstanceUID).equals("*")) {
            out.write("\",\"seriesUID\":\"");
            out.write(tuple.get(QExportTask.exportTask.seriesInstanceUID));
        }
        if (!tuple.get(QExportTask.exportTask.sopInstanceUID).equals("*")) {
            out.write("\",\"objectUID\":\"");
            out.write(tuple.get(QExportTask.exportTask.sopInstanceUID));
        }
        if (tuple.get(QExportTask.exportTask.modalities) != null) {
            out.write("\",\"modality\":[\"");
            String[] modalities = StringUtils.split(tuple.get(QExportTask.exportTask.modalities), '\\');
            out.write(modalities[0]);
            out.write("\"");
            for (int i = 1; i < modalities.length; i++) {
                out.write(",\"");
                out.write(modalities[i]);
                out.write("\"");
            }
            out.write("]");
        }
        if (tuple.get(QExportTask.exportTask.numberOfInstances) != null
                && tuple.get(QExportTask.exportTask.numberOfInstances) > 0) {
            out.write("\",\"numberOfInstances\":");
            out.write(String.valueOf(tuple.get(QExportTask.exportTask.numberOfInstances)));
        }
        if (tuple.get(QExportTask.exportTask.numberOfFailures) > 0) {
            out.write("\",\"failures\":");
            out.write(String.valueOf(tuple.get(QExportTask.exportTask.numberOfFailures)));
        }
        out.write("\",\"createdTime\":\"");
        out.write(df.format(tuple.get(QExportTask.exportTask.createdTime)));
        out.write("\",\"updatedTime\":\"");
        out.write(df.format(tuple.get(QExportTask.exportTask.updatedTime)));
        out.write("\",\"scheduledTime\":\"");
        out.write(df.format(tuple.get(QExportTask.exportTask.scheduledTime)));
        if (tuple.get(QExportTask.exportTask.processingStartTime) != null) {
            out.write("\",\"processingStartTime\":\"");
            out.write(df.format(tuple.get(QExportTask.exportTask.processingStartTime)));
        }
        if (tuple.get(QExportTask.exportTask.processingEndTime) != null) {
            out.write("\",\"processingEndTime\":\"");
            out.write(df.format(tuple.get(QExportTask.exportTask.processingEndTime)));
        }
        if (tuple.get(QExportTask.exportTask.errorMessage) != null) {
            out.write("\",\"errorMessage\":\"");
            out.write(tuple.get(QExportTask.exportTask.errorMessage).replace('"', '\''));
        }
        if (tuple.get(QExportTask.exportTask.outcomeMessage) != null) {
            out.write("\",\"outcomeMessage\":\"");
            out.write(tuple.get(QExportTask.exportTask.outcomeMessage).replace('"', '\''));
        }
        out.write("\"");
        out.write('}');
    }

}
