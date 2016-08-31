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

package org.dcm4chee.arc.mwl.rs;


import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.json.JSONReader;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.util.UIDUtils;
import org.dcm4chee.arc.conf.SPSStatus;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.id.IDService;
import org.dcm4chee.arc.patient.PatientService;
import org.dcm4chee.arc.procedure.ProcedureContext;
import org.dcm4chee.arc.procedure.ProcedureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonParsingException;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Date;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jun 2016
 */
@RequestScoped
@Path("aets/{AETitle}/rs")
public class MwlRS {

    private static final Logger LOG = LoggerFactory.getLogger(MwlRS.class);

    @Inject
    private Device device;

    @Inject
    private PatientService patientService;

    @Inject
    private ProcedureService procedureService;

    @Inject
    private IDService idService;

    @PathParam("AETitle")
    private String aet;

    private ApplicationEntity ae;

    @Context
    private HttpServletRequest request;

    @POST
    @Path("/mwlitems")
    @Consumes("application/json")
    @Produces("application/json")
    public StreamingOutput updateSPS(InputStream in) throws Exception {
        LOG.info("Process POST {} from {}@{}", this, request.getRemoteUser(), request.getRemoteHost());
        try {
            JSONReader reader = new JSONReader(Json.createParser(new InputStreamReader(in, "UTF-8")));
            final Attributes attrs = reader.readDataset(null);
            IDWithIssuer patientID = IDWithIssuer.pidOf(attrs);
            if (patientID == null)
                throw new WebApplicationException(getResponse("missing Patient ID in message body", Response.Status.BAD_REQUEST));

            Attributes spsItem = attrs.getNestedDataset(Tag.ScheduledProcedureStepSequence);
            if (spsItem == null)
                throw new WebApplicationException(getResponse(
                        "Missing or empty (0040,0100) Scheduled Procedure Step Sequence", Response.Status.BAD_REQUEST));

            Patient patient = patientService.findPatient(patientID);
            if (patient == null)
                throw new WebApplicationException(getResponse("Patient[id=" + patientID + "] does not exists",
                        Response.Status.NOT_FOUND));

            if (!attrs.containsValue(Tag.AccessionNumber))
                idService.newAccessionNumber(attrs);
            if (!attrs.containsValue(Tag.RequestedProcedureID))
                idService.newRequestedProcedureID(attrs);
            if (!spsItem.containsValue(Tag.ScheduledProcedureStepID))
                idService.newScheduledProcedureStepID(spsItem);
            if (!attrs.containsValue(Tag.StudyInstanceUID))
                attrs.setString(Tag.StudyInstanceUID, VR.UI, UIDUtils.createUID());
            if (!spsItem.containsValue(Tag.ScheduledProcedureStepStartDate) && !spsItem.containsValue(Tag.ScheduledProcedureStepStartTime))
                spsItem.setDate(Tag.ScheduledProcedureStepStartDateAndTime, new Date());
            if (!spsItem.containsValue(Tag.ScheduledProcedureStepStatus))
                spsItem.setString(Tag.ScheduledProcedureStepStatus, VR.CS, SPSStatus.SCHEDULED.toString());
            ProcedureContext ctx = procedureService.createProcedureContextWEB(request, getApplicationEntity());
            ctx.setPatient(patient);
            ctx.setAttributes(attrs);
            procedureService.updateProcedure(ctx);
            return new StreamingOutput() {
                @Override
                public void write(OutputStream out) throws IOException {
                    try (JsonGenerator gen = Json.createGenerator(out)) {
                        new JSONWriter(gen).write(attrs);
                    }
                }
            };
        } catch (JsonParsingException e) {
            throw new WebApplicationException(
                    getResponse(e.getMessage() + " at location : " + e.getLocation(), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    @DELETE
    @Path("/mwlitems/{studyIUID}/{spsID}")
    public void deleteSPS(@PathParam("studyIUID") String studyIUID, @PathParam("spsID") String spsID)
            throws Exception {
        LOG.info("Process DELETE {} from {}@{}", this, request.getRemoteUser(), request.getRemoteHost());
        ProcedureContext ctx = procedureService.createProcedureContextWEB(request, getApplicationEntity());
        ctx.setStudyInstanceUID(studyIUID);
        ctx.setSpsID(spsID);
        procedureService.deleteProcedure(ctx);
        if (ctx.getEventActionCode() == null)
            throw new WebApplicationException(getResponse("MWLItem with study instance UID : " + studyIUID +
                    " and SPS ID : " + spsID + " not found.", Response.Status.NOT_FOUND));
    }

    private ApplicationEntity getApplicationEntity() {
        ApplicationEntity ae = this.ae;
        if (ae == null) {
            ae = device.getApplicationEntity(aet, true);
            if (ae == null || !ae.isInstalled())
                throw new WebApplicationException(getResponse(
                        "No such Application Entity: " + aet,
                        Response.Status.SERVICE_UNAVAILABLE));
            this.ae = ae;
        }
        return ae;
    }

    private Response getResponse(String errorMessage, Response.Status status) {
        Object entity = "{\"errorMessage\":\"" + errorMessage + "\"}";
        return Response.status(status).entity(entity).build();
    }
}
