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

package org.dcm4chee.arc.mwl.rs;


import org.dcm4che3.audit.AuditMessages;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.json.JSONReader;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.WebApplication;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.util.UIDUtils;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.id.IDService;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.keycloak.KeycloakContext;
import org.dcm4chee.arc.patient.PatientService;
import org.dcm4chee.arc.procedure.ProcedureContext;
import org.dcm4chee.arc.procedure.ProcedureService;
import org.dcm4chee.arc.rs.client.RSForward;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonParsingException;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jun 2016
 */
@RequestScoped
@Path("aets/{AETitle}/rs")
public class MwlRS {

    private static final Logger LOG = LoggerFactory.getLogger(MwlRS.class);
    private static final String SUPER_USER_ROLE = "super-user-role";

    @Inject
    private Device device;

    @Inject
    private PatientService patientService;

    @Inject
    private ProcedureService procedureService;

    @Inject
    private IDService idService;

    @Inject
    private RSForward rsForward;

    @PathParam("AETitle")
    private String aet;

    @Context
    private HttpServletRequest request;

    @POST
    @Path("/mwlitems")
    @Consumes("application/dicom+json,application/json")
    @Produces("application/dicom+json,application/json")
    public Response updateSPS(@QueryParam("mwlscp") String mwlscp, InputStream in) {
        logRequest();
        ArchiveAEExtension arcAE = getArchiveAE();
        if (arcAE == null)
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);

        validateAcceptedUserRoles(arcAE);
        if (aet.equals(arcAE.getApplicationEntity().getAETitle()))
            validateWebAppServiceClass();

        final Attributes attrs = toAttributes(in);
        IDWithIssuer patientID = IDWithIssuer.pidOf(attrs);
        if (patientID == null)
            return errResponse("missing Patient ID in message body", Response.Status.BAD_REQUEST);

        Attributes spsItem = attrs.getNestedDataset(Tag.ScheduledProcedureStepSequence);
        if (spsItem == null)
            return errResponse("Missing or empty (0040,0100) Scheduled Procedure Step Sequence",
                    Response.Status.BAD_REQUEST);

        Patient patient = patientService.findPatient(patientID);
        if (patient == null)
            return errResponse("Patient[id=" + patientID + "] does not exists", Response.Status.NOT_FOUND);

        try {
            if (!attrs.containsValue(Tag.AccessionNumber))
                idService.newAccessionNumber(arcAE.mwlAccessionNumberGenerator(), attrs);
            if (!attrs.containsValue(Tag.RequestedProcedureID))
                idService.newRequestedProcedureID(arcAE.mwlRequestedProcedureIDGenerator(), attrs);
            if (!spsItem.containsValue(Tag.ScheduledProcedureStepID))
                idService.newScheduledProcedureStepID(arcAE.mwlScheduledProcedureStepIDGenerator(), spsItem);
            if (!attrs.containsValue(Tag.StudyInstanceUID))
                attrs.setString(Tag.StudyInstanceUID, VR.UI, UIDUtils.createUID());
            if (!spsItem.containsValue(Tag.ScheduledProcedureStepStatus))
                spsItem.setString(Tag.ScheduledProcedureStepStatus, VR.CS, SPSStatus.SCHEDULED.toString());
            if (!spsItem.containsValue(Tag.ScheduledStationAETitle))
                adjustScheduledStations(spsItem);
            ProcedureContext ctx = procedureService.createProcedureContext()
                    .setHttpServletRequest(HttpServletRequestInfo.valueOf(request));
            ctx.setLocalAET(StringUtils.maskNull(mwlscp, "*"));
            ctx.setArchiveAEExtension(arcAE);
            ctx.setPatient(patient);
            ctx.setAttributes(attrs);
            procedureService.updateProcedure(ctx);
            RSOperation rsOp = ctx.getEventActionCode().equals(AuditMessages.EventActionCode.Create)
                                ? RSOperation.CreateMWL : RSOperation.UpdateMWL;
            rsForward.forward(rsOp, arcAE, attrs, request);
            return Response.ok((StreamingOutput) out -> {
                                        try (JsonGenerator gen = Json.createGenerator(out)) {
                                            arcAE.encodeAsJSONNumber(new JSONWriter(gen)).write(attrs);
                                        }
                                })
                            .build();
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @DELETE
    @Path("/mwlitems/{studyIUID}/{spsID}")
    public Response deleteSPS(@PathParam("studyIUID") String studyIUID, @PathParam("spsID") String spsID) {
        logRequest();
        ArchiveAEExtension arcAE = getArchiveAE();
        if (arcAE == null)
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);

        validateAcceptedUserRoles(arcAE);
        if (aet.equals(arcAE.getApplicationEntity().getAETitle()))
            validateWebAppServiceClass();

        try {
            ProcedureContext ctx = procedureService.createProcedureContext()
                    .setHttpServletRequest(HttpServletRequestInfo.valueOf(request));
            ctx.setStudyInstanceUID(studyIUID);
            ctx.setSpsID(spsID);
            procedureService.deleteProcedure(ctx);
            if (ctx.getEventActionCode() == null)
                return errResponse("MWLItem with study instance UID : " + studyIUID + " and SPS ID : "
                                + spsID + " not found.",
                        Response.Status.NOT_FOUND);
            rsForward.forward(RSOperation.DeleteMWL, arcAE, null, request);
            return Response.noContent().build();
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @POST
    @Path("/mwlitems/{study}/{spsID}/status/{status}")
    public void updateSPSStatus(
            @PathParam("study") String studyIUID,
            @PathParam("spsID") String spsID,
            @PathParam("status")
            @Pattern(regexp = "SCHEDULED|ARRIVED|READY|STARTED|DEPARTED|CANCELED|DISCONTINUED|COMPLETED")
            String spsStatus) {
        logRequest();
        ArchiveAEExtension arcAE = getArchiveAE();
        try {
            ProcedureContext ctx = procedureService.createProcedureContext()
                    .setHttpServletRequest(HttpServletRequestInfo.valueOf(request));
            ctx.setStudyInstanceUID(studyIUID);
            ctx.setSpsID(spsID);
            ctx.setSpsStatus(SPSStatus.valueOf(spsStatus));
            procedureService.updateMWLStatus(ctx);
            rsForward.forward(RSOperation.UpdateMWL, arcAE, null, request);
        } catch (Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    private Attributes toAttributes(InputStream in) {
        try {
            return new JSONReader(Json.createParser(new InputStreamReader(in, "UTF-8")))
                    .readDataset(null);
        } catch (JsonParsingException e) {
            throw new WebApplicationException(
                    errResponse(e.getMessage() + " at location : " + e.getLocation(),
                    Response.Status.BAD_REQUEST));
        } catch (Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    private void logRequest() {
        LOG.info("Process {} {} from {}@{}",
                request.getMethod(),
                request.getRequestURI(),
                request.getRemoteUser(),
                request.getRemoteHost());
    }

    private ArchiveAEExtension getArchiveAE() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        return ae == null || !ae.isInstalled() ? null : ae.getAEExtension(ArchiveAEExtension.class);
    }

    private Response errResponse(String msg, Response.Status status) {
        return errResponseAsTextPlain("{\"errorMessage\":\"" + msg + "\"}", status);
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

    private void adjustScheduledStations(Attributes sps) {
        List<String> ssAETs = new ArrayList<>();
        Collection<HL7OrderScheduledStation> hl7OrderScheduledStations = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class)
                                                                                .getHL7OrderScheduledStations();
        hl7OrderScheduledStations.forEach(station -> ssAETs.addAll(station.getDevice().getApplicationAETitles()));

        if (!ssAETs.isEmpty())
            sps.setString(Tag.ScheduledStationAETitle, VR.AE, ssAETs.toArray(new String[0]));

        String[] ssNames = hl7OrderScheduledStations.stream()
                                                    .filter(station -> station.getDevice().getStationName() != null)
                                                    .map(station -> station.getDevice().getStationName())
                                                    .toArray(String[]::new);
        if (ssNames.length > 0)
            sps.setString(Tag.ScheduledStationName, VR.SH, ssNames);
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
                        .contains(WebApplication.ServiceClass.DCM4CHEE_ARC_AET))
                .findFirst()
                .orElseThrow(() -> new WebApplicationException(errResponse(
                        "No Web Application with DCM4CHEE_ARC_AET service class found for Application Entity: " + aet,
                        Response.Status.NOT_FOUND)));
    }
}
