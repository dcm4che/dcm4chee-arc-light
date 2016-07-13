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

package org.dcm4chee.arc.iocm.rs;

import org.dcm4che3.audit.AuditMessages;
import org.dcm4che3.data.*;
import org.dcm4che3.json.JSONReader;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.util.UIDUtils;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.RejectionNote;
import org.dcm4chee.arc.delete.DeletionService;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.id.IDService;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.patient.PatientService;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreService;
import org.dcm4chee.arc.store.StoreSession;
import org.dcm4chee.arc.study.StudyMgtContext;
import org.dcm4chee.arc.study.StudyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Nov 2015
 */
@RequestScoped
@Path("aets/{AETitle}/rs")
public class IocmRS {

    private static final Logger LOG = LoggerFactory.getLogger(IocmRS.class);

    @Inject
    private Device device;

    @Inject
    private QueryService queryService;

    @Inject
    private StoreService storeService;

    @Inject
    private DeletionService deletionService;

    @Inject
    private PatientService patientService;

    @Inject
    private StudyService studyService;

    @Inject
    private IDService idService;

    @PathParam("AETitle")
    private String aet;

    @Context
    private HttpServletRequest request;


    @GET
    @Path("/studies/{StudyUID}/reject/{CodeValue}^{CodingSchemeDesignator}")
    public void rejectStudy(
            @PathParam("StudyUID") String studyUID,
            @PathParam("CodeValue") String codeValue,
            @PathParam("CodingSchemeDesignator") String designator) throws Exception {
        reject("rejectStudy", studyUID, null, null, codeValue, designator);
    }

    @GET
    @Path("/studies/{StudyUID}/series/{SeriesUID}/reject/{CodeValue}^{CodingSchemeDesignator}")
    public void rejectSeries(
            @PathParam("StudyUID") String studyUID,
            @PathParam("SeriesUID") String seriesUID,
            @PathParam("CodeValue") String codeValue,
            @PathParam("CodingSchemeDesignator") String designator) throws Exception {
        reject("rejectSeries", studyUID, seriesUID, null, codeValue, designator);
    }

    @GET
    @Path("/studies/{StudyUID}/series/{SeriesUID}/instances/{ObjectUID}/reject/{CodeValue}^{CodingSchemeDesignator}")
    public void rejectInstance(
            @PathParam("StudyUID") String studyUID,
            @PathParam("SeriesUID") String seriesUID,
            @PathParam("ObjectUID") String objectUID,
            @PathParam("CodeValue") String codeValue,
            @PathParam("CodingSchemeDesignator") String designator) throws Exception {
        reject("rejectInstance", studyUID, seriesUID, objectUID, codeValue, designator);
    }

    @POST
    @Path("/studies/{StudyUID}/move")
    @Consumes("application/json")
    @Produces("application/json")
    public StreamingOutput moveInstances(@PathParam("StudyUID") String studyUID, InputStream in) throws Exception {
        logRequest();
        JSONReader reader = new JSONReader(Json.createParser(new InputStreamReader(in, "UTF-8")));
        Attributes ko = reader.readDataset(null);
        Attributes instanceRefs = ko.getNestedDataset(Tag.CurrentRequestedProcedureEvidenceSequence);
        if (!queryService.addSOPInstanceReferences(instanceRefs, getApplicationEntity()))
            throw new WebApplicationException(Response.Status.NOT_FOUND);

        StudyMgtContext ctx = studyService.createStudyMgtContextWEB(request, getApplicationEntity());
        ctx.setAttributes(instanceRefs);
        ctx.setTargetStudyInstanceUID(studyUID);
        final Attributes result = studyService.moveInstances(ctx);
        return new StreamingOutput() {
            @Override
            public void write(OutputStream out) throws IOException {
                try (JsonGenerator gen = Json.createGenerator(out)) {
                    new JSONWriter(gen).write(result);
                }
            }
        };
    }

    @DELETE
    @Path("/patients/{PatientID}")
    public void deletePatient(@PathParam("PatientID") IDWithIssuer patientID) throws Exception {
        logRequest();
        Patient patient = patientService.findPatient(patientID);
        if (patient == null)
            throw new WebApplicationException(
                    "Patient having patient ID : " + patientID + " not found.", Response.Status.NOT_FOUND);

        PatientMgtContext ctx = patientService.createPatientMgtContextWEB(request, getApplicationEntity());
        ctx.setPatientID(patientID);
        ctx.setAttributes(patient.getAttributes());
        ctx.setEventActionCode(AuditMessages.EventActionCode.Delete);
        ctx.setPatient(patient);
        deletionService.deletePatient(ctx);
    }

    @DELETE
    @Path("/studies/{StudyUID}")
    public void deleteStudy(@PathParam("StudyUID") String studyUID) throws Exception {
        logRequest();
        deletionService.deleteStudy(studyUID, request);
    }

    @POST
    @Path("/patients")
    @Consumes("application/json")
    public String createPatient(InputStream in) throws Exception {
        logRequest();
        JSONReader reader = new JSONReader(Json.createParser(new InputStreamReader(in, "UTF-8")));
        Attributes attrs = reader.readDataset(null);
        if (attrs.containsValue(Tag.PatientID))
            throw new WebApplicationException("Patient ID in message body", Response.Status.BAD_REQUEST);
        idService.newPatientID(attrs);
        PatientMgtContext ctx = patientService.createPatientMgtContextWEB(request, getApplicationEntity());
        ctx.setAttributes(attrs);
        ctx.setAttributeUpdatePolicy(Attributes.UpdatePolicy.REPLACE);
        patientService.updatePatient(ctx);
        return IDWithIssuer.pidOf(attrs).toString();
    }

    @PUT
    @Path("/patients/{PatientID}")
    @Consumes("application/json")
    public void updatePatient(@PathParam("PatientID") IDWithIssuer patientID, InputStream in) throws Exception {
        logRequest();
        PatientMgtContext ctx = patientService.createPatientMgtContextWEB(request, getApplicationEntity());
        JSONReader reader = new JSONReader(Json.createParser(new InputStreamReader(in, "UTF-8")));
        ctx.setAttributes(reader.readDataset(null));
        ctx.setAttributeUpdatePolicy(Attributes.UpdatePolicy.REPLACE);
        IDWithIssuer bodyPatientID = ctx.getPatientID();
        if (bodyPatientID == null)
            throw new WebApplicationException("missing Patient ID in message body", Response.Status.BAD_REQUEST);
        if (patientID.equals(bodyPatientID)) {
            patientService.updatePatient(ctx);
        } else {
            ctx.setPreviousAttributes(patientID.exportPatientIDWithIssuer(null));
            patientService.changePatientID(ctx);
        }
    }

    @POST
    @Path("/studies")
    @Consumes("application/json")
    @Produces("application/json")
    public StreamingOutput updateStudy(InputStream in) throws Exception {
        logRequest();
        JSONReader reader = new JSONReader(Json.createParser(new InputStreamReader(in, "UTF-8")));
        final Attributes attrs = reader.readDataset(null);
        IDWithIssuer patientID = IDWithIssuer.pidOf(attrs);
        if (patientID == null)
            throw new WebApplicationException("missing Patient ID in message body", Response.Status.BAD_REQUEST);

        Patient patient = patientService.findPatient(patientID);
        if (patient == null)
            throw new WebApplicationException("Patient[id=" + patientID + "] does not exists",
                    Response.Status.NOT_FOUND);

        if (!attrs.containsValue(Tag.StudyInstanceUID))
            attrs.setString(Tag.StudyInstanceUID, VR.UI, UIDUtils.createUID());

        StudyMgtContext ctx = studyService.createStudyMgtContextWEB(request, getApplicationEntity());
        ctx.setPatient(patient);
        ctx.setAttributes(attrs);
        studyService.updateStudy(ctx);
        return new StreamingOutput() {
            @Override
            public void write(OutputStream out) throws IOException {
                try (JsonGenerator gen = Json.createGenerator(out)) {
                    new JSONWriter(gen).write(attrs);
                }
            }
        };
    }

    @POST
    @Path("/patients/{PatientID}/studies")
    @Consumes("application/json")
    public String updateStudy(@PathParam("PatientID") IDWithIssuer patientID,
                              InputStream in) throws Exception {
        logRequest();
        JSONReader reader = new JSONReader(Json.createParser(new InputStreamReader(in, "UTF-8")));
        Attributes attrs = reader.readDataset(null);
        String studyIUID = attrs.getString(Tag.StudyInstanceUID);
        if (studyIUID != null)
            throw new WebApplicationException("Study Instance UID in message body", Response.Status.BAD_REQUEST);

        Patient patient = patientService.findPatient(patientID);
        if (patient == null)
            throw new WebApplicationException("Patient[id=" + patientID + "] does not exists",
                    Response.Status.NOT_FOUND);

        attrs.setString(Tag.StudyInstanceUID, VR.UI, UIDUtils.createUID());

        StudyMgtContext ctx = studyService.createStudyMgtContextWEB(request, getApplicationEntity());
        ctx.setPatient(patient);
        ctx.setAttributes(attrs);
        studyService.updateStudy(ctx);
        return studyIUID;
    }

    @PUT
    @Path("/patients/{PatientID}/studies/{StudyUID}")
    @Consumes("application/json")
    public void updateStudy(@PathParam("PatientID") IDWithIssuer patientID,
                            @PathParam("StudyUID") String studyUID,
                            InputStream in) throws Exception {
        logRequest();
        JSONReader reader = new JSONReader(Json.createParser(new InputStreamReader(in, "UTF-8")));

        StudyMgtContext ctx = studyService.createStudyMgtContextWEB(request, getApplicationEntity());
        ctx.setAttributes(reader.readDataset(null));
        String studyIUIDBody = ctx.getStudyInstanceUID();
        if (studyIUIDBody == null)
            throw new WebApplicationException("missing Study Instance UID in message body", Response.Status.BAD_REQUEST);
        if (!studyIUIDBody.equals(studyUID))
            throw new WebApplicationException("Study Instance UID[" + studyIUIDBody +
                    "] in message body does not match Study Instance UID[" + studyUID + "] in path",
                    Response.Status.BAD_REQUEST);

        Patient patient = patientService.findPatient(patientID);
        if (patient == null)
            throw new WebApplicationException("Patient[id=" + patientID + "] does not exists",
                    Response.Status.NOT_FOUND);

        ctx.setPatient(patient);

        studyService.updateStudy(ctx);
    }

    private void logRequest() {
        LOG.info("Process {} {} from {}@{}", request.getMethod(), request.getRequestURI(),
                request.getRemoteUser(), request.getRemoteHost());
    }

    private ApplicationEntity getApplicationEntity() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        if (ae == null || !ae.isInstalled())
            throw new WebApplicationException(
                    "No such Application Entity: " + aet,
                    Response.Status.SERVICE_UNAVAILABLE);
        return ae;
    }

    private void reject(String method, String studyUID, String seriesUID, String objectUID,
                        String codeValue, String designator) throws IOException {
        logRequest();
        ApplicationEntity ae = getApplicationEntity();
        ArchiveAEExtension arcAE = ae.getAEExtension(ArchiveAEExtension.class);
        ArchiveDeviceExtension arcDev = arcAE.getArchiveDeviceExtension();
        Code code = new Code(codeValue, designator, null, "?");
        RejectionNote rjNote = arcDev.getRejectionNote(code);
        if (rjNote == null)
            throw new WebApplicationException("Unknown Rejection Note Code: " + code, Response.Status.NOT_FOUND);

        Attributes attrs = queryService.createRejectionNote(ae, studyUID, seriesUID, objectUID, rjNote);
        if (attrs == null)
            throw new WebApplicationException("No Study with UID: " + studyUID, Response.Status.NOT_FOUND);

        StoreSession session = storeService.newStoreSession(request, ae);
        StoreContext ctx = storeService.newStoreContext(session);
        ctx.setSopClassUID(attrs.getString(Tag.SOPClassUID));
        ctx.setSopInstanceUID(attrs.getString(Tag.SOPInstanceUID));
        ctx.setReceiveTransferSyntax(UID.ExplicitVRLittleEndian);
        storeService.store(ctx, attrs);
    }

}
