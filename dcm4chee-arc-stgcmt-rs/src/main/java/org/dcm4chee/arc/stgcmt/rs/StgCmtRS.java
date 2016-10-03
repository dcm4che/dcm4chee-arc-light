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
 * Portions created by the Initial Developer are Copyright (C) 2016
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

package org.dcm4chee.arc.stgcmt.rs;

import org.dcm4chee.arc.entity.StgCmtResult;
import org.dcm4chee.arc.stgcmt.StgCmtManager;
import org.jboss.resteasy.annotations.cache.NoCache;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Sep 2016
 */
@RequestScoped
@Path("stgcmt")
public class StgCmtRS {

    @Inject
    private StgCmtManager mgr;

    @QueryParam("status")
    @Pattern(regexp = "PENDING|COMPLETED|WARNING|FAILED")
    private String status;

    @QueryParam("studyUID")
    private String studyUID;

    @QueryParam("exporterID")
    private String exporterID;

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
    public StreamingOutput listStgCmts() throws Exception {
        final List<StgCmtResult> stgCmtResults = mgr.listStgCmts(
                statusOf(status), studyUID, exporterID, parseInt(offset), parseInt(limit));
        return new StreamingOutput() {
            @Override
            public void write(OutputStream out) throws IOException {
                JsonGenerator gen = Json.createGenerator(out);
                gen.writeStartArray();
                for (StgCmtResult stgCmtResult : stgCmtResults) {
                    gen.writeStartObject();
                    gen.write("dicomDeviceName", stgCmtResult.getDeviceName());
                    gen.write("transactionUID", stgCmtResult.getTransactionUID());
                    gen.write("status", stgCmtResult.getStatus().toString());
                    gen.write("studyUID", stgCmtResult.getStudyInstanceUID());
                    gen.write("seriesUID", stgCmtResult.getSeriesInstanceUID());
                    gen.write("objectUID", stgCmtResult.getSopInstanceUID());
                    gen.write("exporterID", stgCmtResult.getExporterID());
                    gen.write("requested", stgCmtResult.getNumberOfInstances());
                    gen.write("failures", stgCmtResult.getNumberOfFailures());
                    gen.write("createdTime", stgCmtResult.getCreatedTime().toString());
                    gen.write("updatedTime", stgCmtResult.getUpdatedTime().toString());
                    gen.writeEnd();
                }
                gen.writeEnd();
                gen.flush();
            }
        };
    }

    @DELETE
    @Path("{transactionUID}")
    public void deleteStgCmt(@PathParam("transactionUID") String transactionUID) {
        if (!mgr.deleteStgCmt(transactionUID))
            throw new WebApplicationException(Response.Status.NOT_FOUND);
    }

    @DELETE
    @Produces("application/json")
    public String deleteStgCmts() {
        return "{\"deleted\":"
                + mgr.deleteStgCmts(statusOf(status), parseDate(updatedBefore))
                + '}';
    }

    private static StgCmtResult.Status statusOf(String status) {
        return status != null ? StgCmtResult.Status.valueOf(status) : null;
    }

    private static int parseInt(String s) {
        return s != null ? Integer.parseInt(s) : 0;
    }

    private static Date parseDate(String s) {
        try {
            return s != null
                    ? new SimpleDateFormat("yyyy-MM-dd").parse(s)
                    : null;
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }
}
