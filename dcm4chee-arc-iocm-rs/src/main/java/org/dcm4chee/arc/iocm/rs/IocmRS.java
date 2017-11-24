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

package org.dcm4chee.arc.iocm.rs;

import org.dcm4che3.audit.AuditMessages;
import org.dcm4che3.data.*;
import org.dcm4che3.json.JSONReader;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.UIDUtils;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.hl7.RESTfulHL7Sender;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.delete.DeletionService;
import org.dcm4chee.arc.delete.StudyNotEmptyException;
import org.dcm4chee.arc.delete.StudyNotFoundException;
import org.dcm4chee.arc.id.IDService;
import org.dcm4chee.arc.patient.*;
import org.dcm4chee.arc.procedure.ProcedureContext;
import org.dcm4chee.arc.procedure.ProcedureService;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.retrieve.InstanceLocations;
import org.dcm4chee.arc.rs.client.RSForward;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreService;
import org.dcm4chee.arc.store.StoreSession;
import org.dcm4chee.arc.study.StudyMgtContext;
import org.dcm4chee.arc.study.StudyService;
import org.dcm4chee.arc.qmgt.HttpServletRequestInfo;
import org.dcm4chee.arc.validation.constraints.ValidValueOf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonParser;
import javax.json.stream.JsonParsingException;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
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

    @Inject
    private RSForward rsForward;

    @Inject
    private RESTfulHL7Sender rsHL7Sender;

    @Inject
    private ProcedureService procedureService;

    @PathParam("AETitle")
    private String aet;

    @Context
    private HttpServletRequest request;


    @POST
    @Path("/studies/{StudyUID}/reject/{CodeValue}^{CodingSchemeDesignator}")
    public void rejectStudy(
            @PathParam("StudyUID") String studyUID,
            @PathParam("CodeValue") String codeValue,
            @PathParam("CodingSchemeDesignator") String designator) throws Exception {
        reject(RSOperation.RejectStudy, studyUID, null, null, codeValue, designator);
    }

    @POST
    @Path("/studies/{StudyUID}/series/{SeriesUID}/reject/{CodeValue}^{CodingSchemeDesignator}")
    public void rejectSeries(
            @PathParam("StudyUID") String studyUID,
            @PathParam("SeriesUID") String seriesUID,
            @PathParam("CodeValue") String codeValue,
            @PathParam("CodingSchemeDesignator") String designator) throws Exception {
        reject(RSOperation.RejectSeries, studyUID, seriesUID, null, codeValue, designator);
    }

    @POST
    @Path("/studies/{StudyUID}/series/{SeriesUID}/instances/{ObjectUID}/reject/{CodeValue}^{CodingSchemeDesignator}")
    public void rejectInstance(
            @PathParam("StudyUID") String studyUID,
            @PathParam("SeriesUID") String seriesUID,
            @PathParam("ObjectUID") String objectUID,
            @PathParam("CodeValue") String codeValue,
            @PathParam("CodingSchemeDesignator") String designator) throws Exception {
        reject(RSOperation.RejectInstance, studyUID, seriesUID, objectUID, codeValue, designator);
    }

    @POST
    @Path("/studies/{StudyUID}/copy")
    @Consumes("application/json")
    @Produces("application/json")
    public Response copyInstances(@PathParam("StudyUID") String studyUID, InputStream in) throws Exception {
        return copyOrMoveInstances(RSOperation.CopyInstances, studyUID, in, null, null);
    }

    @POST
    @Path("/studies/{StudyUID}/move/{CodeValue}^{CodingSchemeDesignator}")
    @Consumes("application/json")
    @Produces("application/json")
    public Response moveInstances(
            @PathParam("StudyUID") String studyUID,
            @PathParam("CodeValue") String codeValue,
            @PathParam("CodingSchemeDesignator") String designator,
            InputStream in) throws Exception {
        return copyOrMoveInstances(RSOperation.MoveInstances, studyUID, in, codeValue, designator);
    }

    @DELETE
    @Path("/patients/{PatientID}")
    public void deletePatient(@PathParam("PatientID") IDWithIssuer patientID) throws Exception {
        logRequest();
        ArchiveAEExtension arcAE = getArchiveAE();
        Patient patient = patientService.findPatient(patientID);
        if (patient == null)
            throw new WebApplicationException(getResponse(
                    "Patient having patient ID : " + patientID + " not found.", Response.Status.NOT_FOUND));
        if (patient.getNumberOfStudies() > 0)
            throw new WebApplicationException(getResponse(
                    "Patient having patient ID : " + patientID + " has non empty studies.", Response.Status.FORBIDDEN));
        PatientMgtContext ctx = patientService.createPatientMgtContextWEB(request);
        ctx.setPatientID(patientID);
        ctx.setAttributes(patient.getAttributes());
        ctx.setEventActionCode(AuditMessages.EventActionCode.Delete);
        ctx.setPatient(patient);
        deletionService.deletePatient(ctx);
        rsForward.forward(RSOperation.DeletePatient, arcAE, null, request);
    }

    @DELETE
    @Path("/studies/{StudyUID}")
    public void deleteStudy(@PathParam("StudyUID") String studyUID) throws Exception {
        logRequest();
        ArchiveAEExtension arcAE = getArchiveAE();
        try {
            deletionService.deleteStudy(studyUID, HttpServletRequestInfo.valueOf(request), arcAE.getApplicationEntity());
            rsForward.forward(RSOperation.DeleteStudy, arcAE, null, request);
        } catch (StudyNotFoundException e) {
            throw new WebApplicationException(getResponse("Study having study instance UID " + studyUID + " not found.",
                    Response.Status.NOT_FOUND));
        } catch (StudyNotEmptyException e) {
            throw new WebApplicationException(getResponse(e.getMessage() + studyUID, Response.Status.FORBIDDEN));
        }
    }

    @POST
    @Path("/patients")
    @Consumes({"application/dicom+json,application/json"})
    public String createPatient(InputStream in) throws Exception {
        logRequest();
        ArchiveAEExtension arcAE = getArchiveAE();
        try {
            JSONReader reader = new JSONReader(Json.createParser(new InputStreamReader(in, "UTF-8")));
            Attributes attrs = reader.readDataset(null);
            if (attrs.containsValue(Tag.PatientID))
                throw new WebApplicationException(getResponse("Patient ID in message body", Response.Status.BAD_REQUEST));
            idService.newPatientID(attrs);
            PatientMgtContext ctx = patientService.createPatientMgtContextWEB(request);
            ctx.setAttributes(attrs);
            ctx.setAttributeUpdatePolicy(Attributes.UpdatePolicy.REPLACE);
            patientService.updatePatient(ctx);
            rsForward.forward(RSOperation.CreatePatient, arcAE, attrs, request);
            rsHL7Sender.sendHL7Message("ADT^A28^ADT_A05", ctx);
            return IDWithIssuer.pidOf(attrs).toString();
        } catch (JsonParsingException e) {
            throw new WebApplicationException(
                    getResponse(e.getMessage() + " at location : " + e.getLocation(), Response.Status.BAD_REQUEST));
        } catch (IOException e) {
            throw new WebApplicationException(getResponse(e.getMessage(), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    @PUT
    @Path("/patients/{PatientID}")
    @Consumes("application/dicom+json,application/json")
    public void updatePatient(@PathParam("PatientID") IDWithIssuer patientID, InputStream in) throws Exception {
        logRequest();
        ArchiveAEExtension arcAE = getArchiveAE();
        try {
            PatientMgtContext ctx = patientService.createPatientMgtContextWEB(request);
            JSONReader reader = new JSONReader(Json.createParser(new InputStreamReader(in, "UTF-8")));
            Attributes attrs = reader.readDataset(null);
            ctx.setAttributes(attrs);
            ctx.setAttributeUpdatePolicy(Attributes.UpdatePolicy.REPLACE);
            IDWithIssuer bodyPatientID = ctx.getPatientID();
            if (bodyPatientID == null)
                throw new WebApplicationException(getResponse("missing Patient ID in message body", Response.Status.BAD_REQUEST));
            boolean newPatient = patientID.equals(bodyPatientID);
            if (newPatient)
                patientService.updatePatient(ctx);
            else {
                ctx.setPreviousAttributes(patientID.exportPatientIDWithIssuer(null));
                patientService.changePatientID(ctx);
            }
            rsForward.forward(RSOperation.UpdatePatient, arcAE, attrs, request);
            String msgType = ctx.getEventActionCode().equals(AuditMessages.EventActionCode.Create)
                    ? newPatient
                        ? "ADT^A28^ADT_A05" : "ADT^A47^ADT_A30"
                    : "ADT^A31^ADT_A05";
            rsHL7Sender.sendHL7Message(msgType, ctx);
        } catch (JsonParsingException e) {
            throw new WebApplicationException(
                    getResponse(e.getMessage() + " at location : " + e.getLocation(), Response.Status.BAD_REQUEST));
        } catch (PatientTrackingNotAllowedException e) {
            throw new WebApplicationException(getResponse(e.getMessage(), Response.Status.CONFLICT));
        } catch (CircularPatientMergeException e) {
            throw new WebApplicationException(
                    getResponse("PriorPatientID same as target PatientID", Response.Status.CONFLICT));
        } catch(Exception e) {
            throw new WebApplicationException(getResponseAsTextPlain(e));
        }
    }

    @POST
    @Path("/patients/{patientID}/merge")
    @Consumes("application/json")
    public void mergePatients(@PathParam("patientID") IDWithIssuer patientID, InputStream in) throws Exception {
        logRequest();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = in.read(buffer)) != -1) {
                baos.write(buffer, 0, length);
            }
            InputStream is1 = new ByteArrayInputStream(baos.toByteArray());
            final Attributes attrs = parseOtherPatientIDs(is1);
            for (Attributes otherPID : attrs.getSequence(Tag.OtherPatientIDsSequence))
                mergePatient(patientID, otherPID);

            rsForward.forwardMergeMultiplePatients(RSOperation.MergePatients, getArchiveAE(), baos.toByteArray(), request);
        } catch (JsonParsingException e) {
            throw new WebApplicationException(
                    getResponse(e.getMessage() + " at location : " + e.getLocation(), Response.Status.BAD_REQUEST));
        }
    }

    @POST
    @Path("/patients/{priorPatientID}/merge/{patientID}")
    public void mergePatient(@PathParam("priorPatientID") IDWithIssuer priorPatientID,
                             @PathParam("patientID") IDWithIssuer patientID) throws Exception {
        logRequest();
        Attributes priorPatAttr = new Attributes(3);
        priorPatAttr.setString(Tag.PatientID, VR.LO, priorPatientID.getID());
        setIssuer(priorPatientID, priorPatAttr);
        mergePatient(patientID, priorPatAttr);
        rsForward.forward(RSOperation.MergePatient, getArchiveAE(), null, request);
    }

    private void mergePatient(IDWithIssuer patientID, Attributes priorPatAttr) throws Exception {
        try {
            PatientMgtContext patMgtCtx = patientService.createPatientMgtContextWEB(request);
            patMgtCtx.setPatientID(patientID);
            Attributes patAttr = new Attributes(3);
            patAttr.setString(Tag.PatientID, VR.LO, patientID.getID());
            setIssuer(patientID, patAttr);
            patMgtCtx.setAttributes(patAttr);
            patMgtCtx.setPreviousAttributes(priorPatAttr);
            LOG.info("Prior patient ID {} and target patient ID {}", patMgtCtx.getPreviousPatientID(), patMgtCtx.getPatientID());
            patientService.mergePatient(patMgtCtx);
            rsHL7Sender.sendHL7Message("ADT^A40^ADT_A39", patMgtCtx);
        } catch (NonUniquePatientException | PatientMergedException e) {
            throw new WebApplicationException(getResponse(e.getMessage(), Response.Status.CONFLICT));
        } catch (CircularPatientMergeException e) {
            throw new WebApplicationException(
                    getResponse("PriorPatientID same as target PatientID", Response.Status.CONFLICT));
        }
    }

    @POST
    @Path("/patients/{priorPatientID}/changeid/{patientID}")
    public void changePatientID(@PathParam("priorPatientID") IDWithIssuer priorPatientID,
                                @PathParam("patientID") IDWithIssuer patientID) throws Exception {
        logRequest();
        ArchiveAEExtension arcAE = getArchiveAE();
        try {
            PatientMgtContext ctx = patientService.createPatientMgtContextWEB(request);
            ctx.setAttributeUpdatePolicy(Attributes.UpdatePolicy.REPLACE);
            ctx.setPreviousAttributes(priorPatientID.exportPatientIDWithIssuer(null));
            ctx.setAttributes(patientID.exportPatientIDWithIssuer(null));
            patientService.changePatientID(ctx);
            rsHL7Sender.sendHL7Message("ADT^A47^ADT_A30", ctx);
            rsForward.forward(RSOperation.ChangePatientID, arcAE, null, request);
        } catch (PatientTrackingNotAllowedException e) {
            throw new WebApplicationException(getResponse(e.getMessage(), Response.Status.CONFLICT));
        } catch (CircularPatientMergeException e) {
            throw new WebApplicationException(
                    getResponse("PriorPatientID same as target PatientID", Response.Status.CONFLICT));
        }
    }

    private void setIssuer(IDWithIssuer patientID, Attributes attrs) {
        Issuer pidIssuer = patientID.getIssuer();
        if (pidIssuer == null)
            return;

        attrs.setString(Tag.IssuerOfPatientID, VR.LO, pidIssuer.getLocalNamespaceEntityID());
        setPIDQualifier(attrs, pidIssuer);
    }

    private void setPIDQualifier(Attributes attrs, Issuer pidIssuer) {
        Sequence pidQualifiers = attrs.getSequence(Tag.IssuerOfPatientIDQualifiersSequence);
        if (hasUniversalEntityIDAndType(pidIssuer)) {
            if (pidQualifiers != null)
                for (Attributes item : pidQualifiers)
                    setUniversalEntityIDAndType(pidIssuer, item);

            else {
                pidQualifiers = attrs.newSequence(Tag.IssuerOfPatientIDQualifiersSequence, 1);
                Attributes item = new Attributes(2);
                setUniversalEntityIDAndType(pidIssuer, item);
                pidQualifiers.add(item);
            }
        }
        if (pidQualifiers != null)
            attrs.remove(Tag.IssuerOfPatientIDQualifiersSequence);
    }

    private boolean hasUniversalEntityIDAndType(Issuer pidIssuer) {
        return pidIssuer.getUniversalEntityID() != null && pidIssuer.getUniversalEntityIDType() != null;
    }

    private void setUniversalEntityIDAndType(Issuer pidIssuer, Attributes item) {
        item.setString(Tag.UniversalEntityID, VR.UT, pidIssuer.getUniversalEntityID());
        item.setString(Tag.UniversalEntityIDType, VR.CS, pidIssuer.getUniversalEntityIDType());
    }

    @POST
    @Path("/studies")
    @Consumes("application/dicom+json,application/json")
    @Produces("application/json")
    public StreamingOutput updateStudy(InputStream in) throws Exception {
        logRequest();
        ArchiveAEExtension arcAE = getArchiveAE();
        try {
            JSONReader reader = new JSONReader(Json.createParser(new InputStreamReader(in, "UTF-8")));
            final Attributes attrs = reader.readDataset(null);
            IDWithIssuer patientID = IDWithIssuer.pidOf(attrs);
            if (patientID == null)
                throw new WebApplicationException(getResponse("missing Patient ID in message body", Response.Status.BAD_REQUEST));

            Patient patient = patientService.findPatient(patientID);
            if (patient == null)
                throw new WebApplicationException(getResponse("Patient[id=" + patientID + "] does not exist.",
                        Response.Status.NOT_FOUND));

            boolean studyIUIDPresent = attrs.containsValue(Tag.StudyInstanceUID);
            if (!studyIUIDPresent)
                attrs.setString(Tag.StudyInstanceUID, VR.UI, UIDUtils.createUID());

            StudyMgtContext ctx = studyService.createStudyMgtContextWEB(request, arcAE.getApplicationEntity());
            ctx.setPatient(patient);
            ctx.setAttributes(attrs);
            studyService.updateStudy(ctx);
            rsForward.forward(RSOperation.UpdateStudy, arcAE, attrs, request);
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
                    getResponse(e.getMessage() + " at location : " + e.getLocation(), Response.Status.BAD_REQUEST));
        }
    }

    @PUT
    @Path("/studies/{studyUID}/expire/{expirationDate}")
    public void updateStudyExpirationDate(@PathParam("studyUID") String studyUID,
                                          @PathParam("expirationDate")
                                          @ValidValueOf(type = ExpireDate.class, message = "Expiration date cannot be parsed.")
                                                  String expirationDate) throws Exception {
        updateExpirationDate(RSOperation.UpdateStudyExpirationDate, studyUID, null, expirationDate);
    }

    @PUT
    @Path("/studies/{studyUID}/series/{seriesUID}/expire/{expirationDate}")
    public void updateSeriesExpirationDate(@PathParam("studyUID") String studyUID, @PathParam("seriesUID") String seriesUID,
                                           @PathParam("expirationDate")
                                           @ValidValueOf(type = ExpireDate.class, message = "Expiration date cannot be parsed.")
                                                   String expirationDate) throws Exception {
        updateExpirationDate(RSOperation.UpdateSeriesExpirationDate, studyUID, seriesUID, expirationDate);
    }

    private void updateExpirationDate(RSOperation op, String studyUID, String seriesUID, String expirationDate)
            throws Exception {
        logRequest();
        ArchiveAEExtension arcAE = getArchiveAE();
        try {
            StudyMgtContext ctx = studyService.createStudyMgtContextWEB(request, arcAE.getApplicationEntity());
            ctx.setStudyInstanceUID(studyUID);
            if (seriesUID != null)
                ctx.setSeriesInstanceUID(seriesUID);
            LocalDate expireDate = LocalDate.parse(expirationDate, DateTimeFormatter.BASIC_ISO_DATE);
            ctx.setExpirationDate(expireDate);
            studyService.updateExpirationDate(ctx);
            rsForward.forward(op, arcAE, null, request);
        } catch (Exception e) {
            String message;
            if (seriesUID != null)
                message = "Series not found. " + seriesUID;
            else
                message = "Study not found. " + studyUID;
            throw new WebApplicationException(getResponse(message, Response.Status.NOT_FOUND));
        }
    }

    public static final class ExpireDate {
        public ExpireDate(String date) {
            LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE);
        }
    }

    @POST
    @Path("/mwlitems/{studyUID}/{spsID}/move/{codeValue}^{codingSchemeDesignator}")
    @Consumes("application/json")
    @Produces("application/json")
    public Response linkInstancesWithMWLEntry(@PathParam("studyUID") String studyUID,
                                              @PathParam("spsID") String spsID,
                                              @PathParam("codeValue") String codeValue,
                                              @PathParam("codingSchemeDesignator") String designator,
                                              InputStream in) throws Exception {
        logRequest();
        ArchiveAEExtension arcAE = getArchiveAE();
        RejectionNote rjNote = toRejectionNote(arcAE, codeValue, designator);
        Attributes instanceRefs = parseSOPInstanceReferences(in);
        Attributes forwardOriginal = new Attributes(instanceRefs);

        ProcedureContext ctx = procedureService.createProcedureContextWEB(request);
        ctx.setStudyInstanceUID(studyUID);
        ctx.setSpsID(spsID);

        MWLItem mwl = procedureService.findMWLItem(ctx);
        if (mwl == null)
            return getResponse("MWLItem[studyUID=" + studyUID + ", spsID=" + spsID + "] does not exist.",
                        Response.Status.NOT_FOUND);

        ctx.setAttributes(mwl.getAttributes());
        ctx.setPatient(mwl.getPatient());
        ctx.setSourceInstanceRefs(instanceRefs);


        StoreSession session = storeService.newStoreSession(request, aet, arcAE.getApplicationEntity());
        restoreInstances(session, instanceRefs);
        Collection<InstanceLocations> instanceLocations = storeService.queryInstances(session, instanceRefs, studyUID);
        if (instanceLocations.isEmpty())
            return getResponse("No Instances found. ", Response.Status.NOT_FOUND);

        final Attributes result;

        if (studyUID.equals(instanceRefs.getString(Tag.StudyInstanceUID))) {
            procedureService.updateStudySeriesAttributes(ctx);
            result = getResult(instanceLocations);
        } else {
            mergeMWLItemTo(arcAE, mwl, instanceLocations);
            Attributes sopInstanceRefs = getSOPInstanceRefs(instanceRefs, instanceLocations, arcAE.getApplicationEntity());
            moveSequence(sopInstanceRefs, Tag.ReferencedSeriesSequence, instanceRefs);
            session.setAcceptConflictingPatientID(AcceptConflictingPatientID.YES);
            session.setPatientUpdatePolicy(null);
            session.setStudyUpdatePolicy(arcAE.linkMWLEntryUpdatePolicy());
            result = storeService.copyInstances(session, instanceLocations);
            rejectInstances(instanceRefs, rjNote, session, result);
        }
        rsForward.forward(RSOperation.LinkInstancesWithMWL, arcAE, forwardOriginal, request);
        return toResponse(result);
    }

    private void mergeMWLItemTo(ArchiveAEExtension arcAE, MWLItem mwl, Collection<InstanceLocations> instanceLocations) {
        ArchiveDeviceExtension arcDev = arcAE.getArchiveDeviceExtension();
        AttributeFilter patFilter = arcDev.getAttributeFilter(Entity.Patient);
        AttributeFilter studyFilter = arcDev.getAttributeFilter(Entity.Study);
        Attributes mwlAttrs = new Attributes(mwl.getAttributes(), studyFilter.getSelection());
        mwlAttrs.addAll(mwl.getPatient().getAttributes());
        mwlAttrs.newSequence(Tag.RequestAttributesSequence, 1)
                .add(mwl.getRequestAttributesSequenceItem());
        for (InstanceLocations instanceLocation : instanceLocations)
            mergeMWLItemTo(mwlAttrs, instanceLocation.getAttributes(), patFilter);
    }

    private void mergeMWLItemTo(Attributes mwlAttrs, Attributes instAttrs, AttributeFilter patFilter) {
        for (int tag : patFilter.getSelection())
            instAttrs.remove(tag);

        instAttrs.addAll(mwlAttrs);
    }

    private Response toResponse(Attributes result) {
        StreamingOutput entity = new StreamingOutput() {
            @Override
            public void write(OutputStream out) throws IOException {
                try (JsonGenerator gen = Json.createGenerator(out)) {
                    new JSONWriter(gen).write(result);
                }
            }
        };
        return Response.status(status(result)).entity(entity).build();
    }

    private Attributes getResult(Collection<InstanceLocations> instanceLocations) {
        Attributes result = new Attributes();
        Sequence refSOPSeq = result.newSequence(Tag.ReferencedSOPSequence, instanceLocations.size());
        for (InstanceLocations instanceLocation : instanceLocations)
            populateResult(refSOPSeq, instanceLocation);
        return result;
    }

    private void populateResult(Sequence refSOPSeq, InstanceLocations instanceLocation) {
        Attributes refSOP = new Attributes(2);
        refSOP.setString(Tag.ReferencedSOPClassUID, VR.UI, instanceLocation.getSopClassUID());
        refSOP.setString(Tag.ReferencedSOPInstanceUID, VR.UI, instanceLocation.getSopInstanceUID());
        refSOPSeq.add(refSOP);
    }

    private void logRequest() {
        LOG.info("Process {} {} from {}@{}", request.getMethod(), request.getRequestURI(),
                request.getRemoteUser(), request.getRemoteHost());
    }

    private ArchiveAEExtension getArchiveAE() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        if (ae == null || !ae.isInstalled())
            throw new WebApplicationException(getResponse(
                    "No such Application Entity: " + aet,
                    Response.Status.NOT_FOUND));
        return ae.getAEExtension(ArchiveAEExtension.class);
    }

    private void reject(RSOperation rsOp, String studyUID, String seriesUID, String objectUID,
                        String codeValue, String designator) throws Exception {
        logRequest();
        try {
            ArchiveAEExtension arcAE = getArchiveAE();
            RejectionNote rjNote = toRejectionNote(arcAE, codeValue, designator);
            StoreSession session = storeService.newStoreSession(request, aet, arcAE.getApplicationEntity());
            storeService.restoreInstances(session, studyUID, seriesUID);

            Attributes attrs = queryService.createRejectionNote(
                    arcAE.getApplicationEntity(), studyUID, seriesUID, objectUID, rjNote);
            if (attrs == null)
                throw new WebApplicationException(getResponse("No Study with UID: " + studyUID, Response.Status.NOT_FOUND));

            StoreContext ctx = storeService.newStoreContext(session);
            ctx.setSopClassUID(attrs.getString(Tag.SOPClassUID));
            ctx.setSopInstanceUID(attrs.getString(Tag.SOPInstanceUID));
            ctx.setReceiveTransferSyntax(UID.ExplicitVRLittleEndian);
            storeService.store(ctx, attrs);
            rsForward.forward(rsOp, arcAE, null, request);
        } catch (DicomServiceException e) {
            Response response = getResponse(e.getMessage(), Response.Status.CONFLICT);
            throw new WebApplicationException(response);
        } catch (Exception e) {
            throw new WebApplicationException(getResponse(e.getMessage(), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    private Response copyOrMoveInstances(
            RSOperation op, String studyUID, InputStream in, String codeValue, String designator)
            throws Exception {
        logRequest();
        ArchiveAEExtension arcAE = getArchiveAE();
        RejectionNote rjNote = toRejectionNote(arcAE, codeValue, designator);
        Attributes instanceRefs = parseSOPInstanceReferences(in);
        Attributes forwardOriginal = new Attributes(instanceRefs);
        StoreSession session = storeService.newStoreSession(request, aet, arcAE.getApplicationEntity());
        restoreInstances(session, instanceRefs);
        Collection<InstanceLocations> instances = storeService.queryInstances(session, instanceRefs, studyUID);
        if (instances.isEmpty())
            return getResponse("No Instances found. ", Response.Status.NOT_FOUND);

        Attributes sopInstanceRefs = getSOPInstanceRefs(instanceRefs, instances, arcAE.getApplicationEntity());
        moveSequence(sopInstanceRefs, Tag.ReferencedSeriesSequence, instanceRefs);
        session.setAcceptConflictingPatientID(AcceptConflictingPatientID.YES);
        session.setPatientUpdatePolicy(null);
        session.setStudyUpdatePolicy(arcAE.copyMoveUpdatePolicy());
        Attributes result = storeService.copyInstances(session, instances);
        if (rjNote != null)
            rejectInstances(instanceRefs, rjNote, session, result);

        rsForward.forward(op, arcAE, forwardOriginal, request);
        return toResponse(result);
    }

    private void restoreInstances(StoreSession session, Attributes sopInstanceRefs) throws IOException {
        String studyUID = sopInstanceRefs.getString(Tag.StudyInstanceUID);
        Sequence seq = sopInstanceRefs.getSequence(Tag.ReferencedSeriesSequence);
        if (seq == null || seq.isEmpty())
            storeService.restoreInstances(session, studyUID, null);
        else for (Attributes item : seq)
            storeService.restoreInstances(session, studyUID, item.getString(Tag.SeriesInstanceUID));
    }

    private RejectionNote toRejectionNote(ArchiveAEExtension arcAE, String codeValue, String designator) {
        if (codeValue == null)
            return null;

        RejectionNote rjNote = arcAE.getArchiveDeviceExtension().getRejectionNote(
                new Code(codeValue, designator, null, ""));

        if (rjNote == null)
            throw new WebApplicationException(getResponse("Unknown Rejection Note Code: ("
                    + codeValue + ", " + designator + ')', Response.Status.NOT_FOUND));

        return rjNote;
    }

    private void rejectInstances(Attributes instanceRefs, RejectionNote rjNote, StoreSession session, Attributes result)
            throws IOException {
        Sequence refSeriesSeq = instanceRefs.getSequence(Tag.ReferencedSeriesSequence);
        removeFailedInstanceRefs(refSeriesSeq, failedIUIDs(result));
        if (!refSeriesSeq.isEmpty())
            reject(session, instanceRefs, rjNote);
    }

    private Set<String> failedIUIDs(Attributes result) {
        Sequence failedSOPSeq = result.getSequence(Tag.FailedSOPSequence);
        if (failedSOPSeq == null || failedSOPSeq.isEmpty())
            return Collections.EMPTY_SET;

        Set<String> failedIUIDs = new HashSet<>(failedSOPSeq.size() * 4 / 3 + 1);
        for (Attributes failedSOPRef : failedSOPSeq)
            failedIUIDs.add(failedSOPRef.getString(Tag.ReferencedSOPInstanceUID));

        return failedIUIDs;
    }

    private void removeFailedInstanceRefs(Sequence refSeriesSeq, Set<String> failedIUIDs) {
        if (failedIUIDs.isEmpty())
            return;

        for (Iterator<Attributes> refSeriesIter = refSeriesSeq.iterator(); refSeriesIter.hasNext();) {
            Sequence refSOPSeq = refSeriesIter.next().getSequence(Tag.ReferencedSOPSequence);
            removeFailedRefSOPs(refSOPSeq, failedIUIDs);
            if (refSOPSeq.isEmpty())
                refSeriesIter.remove();
        }
    }

    private void removeFailedRefSOPs(Sequence refSOPSeq, Set<String> failedIUIDs) {
        for (Iterator<Attributes> refSopIter = refSOPSeq.iterator(); refSopIter.hasNext();)
            if (failedIUIDs.contains(refSopIter.next().getString(Tag.ReferencedSOPInstanceUID)))
                refSopIter.remove();
    }

    private Response.Status status(Attributes result) {
        return result.getSequence(Tag.ReferencedSOPSequence).isEmpty()
                ? Response.Status.CONFLICT
                : result.getSequence(Tag.FailedSOPSequence) == null || result.getSequence(Tag.FailedSOPSequence).isEmpty()
                    ? Response.Status.OK : Response.Status.ACCEPTED;
    }

    private void reject(StoreSession session, Attributes instanceRefs, RejectionNote rjNote) throws IOException {
        StoreContext koctx = storeService.newStoreContext(session);
        Attributes ko = queryService.createRejectionNote(instanceRefs, rjNote);
        koctx.setSopClassUID(ko.getString(Tag.SOPClassUID));
        koctx.setSopInstanceUID(ko.getString(Tag.SOPInstanceUID));
        koctx.setReceiveTransferSyntax(UID.ExplicitVRLittleEndian);
        storeService.store(koctx, ko);
    }

    private Attributes getSOPInstanceRefs(Attributes instanceRefs, Collection<InstanceLocations> instances, ApplicationEntity ae) {
        String sourceStudyUID = instanceRefs.getString(Tag.StudyInstanceUID);
        Attributes refStudy = new Attributes(2);
        Sequence refSeriesSeq = refStudy.newSequence(Tag.ReferencedSeriesSequence, 10);
        refStudy.setString(Tag.StudyInstanceUID, VR.UI, sourceStudyUID);
        HashMap<String, Sequence> seriesMap = new HashMap<>();
        for (InstanceLocations instance : instances) {
            Attributes iAttr = instance.getAttributes();
            String seriesIUID = iAttr.getString(Tag.SeriesInstanceUID);
            Sequence refSOPSeq = seriesMap.get(seriesIUID);
            if (refSOPSeq == null) {
                Attributes refSeries = new Attributes(4);
                refSeries.setString(Tag.RetrieveAETitle, VR.AE, ae.getAETitle());
                refSOPSeq = refSeries.newSequence(Tag.ReferencedSOPSequence, 10);
                refSeries.setString(Tag.SeriesInstanceUID, VR.UI, seriesIUID);
                seriesMap.put(seriesIUID, refSOPSeq);
                refSeriesSeq.add(refSeries);
            }
            Attributes refSOP = new Attributes(2);
            refSOP.setString(Tag.ReferencedSOPClassUID, VR.UI, instance.getSopClassUID());
            refSOP.setString(Tag.ReferencedSOPInstanceUID, VR.UI, instance.getSopInstanceUID());
            refSOPSeq.add(refSOP);
        }
        return refStudy;
    }

    private void moveSequence(Attributes src, int tag, Attributes dest) {
        Sequence srcSeq = src.getSequence(tag);
        int size = srcSeq.size();
        Sequence destSeq = dest.newSequence(tag, size);
        for (int i = 0; i < size; i++)
            destSeq.add(srcSeq.remove(0));
    }


    private void expect(JsonParser parser, JsonParser.Event expected) {
        JsonParser.Event next = parser.next();
        if (next != expected)
            throw new WebApplicationException(getResponse("Unexpected " + next, Response.Status.BAD_REQUEST));
    }

    private Attributes parseOtherPatientIDs(InputStream in) throws IOException {
        JsonParser parser = Json.createParser(new InputStreamReader(in, "UTF-8"));
        Attributes attrs = new Attributes(10);
        expect(parser, JsonParser.Event.START_ARRAY);
        Sequence otherPIDseq = attrs.newSequence(Tag.OtherPatientIDsSequence, 10);
        while (parser.next() == JsonParser.Event.START_OBJECT) {
            Attributes otherPID = new Attributes(5);
            while (parser.next() == JsonParser.Event.KEY_NAME) {
                switch (parser.getString()) {
                    case "PatientID":
                        expect(parser, JsonParser.Event.VALUE_STRING);
                        otherPID.setString(Tag.PatientID, VR.LO, parser.getString());
                        break;
                    case "IssuerOfPatientID":
                        expect(parser, JsonParser.Event.VALUE_STRING);
                        otherPID.setString(Tag.IssuerOfPatientID, VR.LO, parser.getString());
                        break;
                    case "IssuerOfPatientIDQualifiers":
                        expect(parser, JsonParser.Event.START_OBJECT);
                        otherPID.newSequence(Tag.IssuerOfPatientIDQualifiersSequence, 2).add(parseIssuerOfPIDQualifier(parser));
                        break;
                    default:
                        throw new WebApplicationException(
                                getResponse("Unexpected Key name", Response.Status.BAD_REQUEST));
                }
            }
            otherPIDseq.add(otherPID);
        }
        if (otherPIDseq.isEmpty())
            throw new WebApplicationException(
                    getResponse("Patients to be merged not sent in the request.", Response.Status.BAD_REQUEST));
        return attrs;
    }

    private Attributes parseIssuerOfPIDQualifier(JsonParser parser) {
        Attributes attr = new Attributes(2);
        while (parser.next() == JsonParser.Event.KEY_NAME) {
            switch (parser.getString()) {
                case "UniversalEntityID":
                    expect(parser, JsonParser.Event.VALUE_STRING);
                    attr.setString(Tag.UniversalEntityID, VR.UT, parser.getString());
                    break;
                case "UniversalEntityIDType":
                    expect(parser, JsonParser.Event.VALUE_STRING);
                    attr.setString(Tag.UniversalEntityIDType, VR.CS, parser.getString());
                    break;
                default:
                    throw new WebApplicationException(
                            getResponse("Unexpected Key name", Response.Status.BAD_REQUEST));
            }
        }
        return attr;
    }

    private Attributes parseSOPInstanceReferences(InputStream in) throws IOException {
        JsonParser parser = Json.createParser(new InputStreamReader(in, "UTF-8"));
        Attributes attrs = new Attributes(2);
        expect(parser, JsonParser.Event.START_OBJECT);
        while (parser.next() == JsonParser.Event.KEY_NAME) {
            switch (parser.getString()) {
                case "StudyInstanceUID":
                    expect(parser, JsonParser.Event.VALUE_STRING);
                    attrs.setString(Tag.StudyInstanceUID, VR.UI, parser.getString());
                    break;
                case "ReferencedSeriesSequence":
                    parseReferencedSeriesSequence(parser,
                            attrs.newSequence(Tag.ReferencedSeriesSequence, 10));
                    break;
                default:
                    throw new WebApplicationException(getResponse("Unexpected Key name", Response.Status.BAD_REQUEST));
            }
        }
        if (!attrs.contains(Tag.StudyInstanceUID))
            throw new WebApplicationException(getResponse("Missing StudyInstanceUID", Response.Status.BAD_REQUEST));

        return attrs;
    }

    private void parseReferencedSeriesSequence(JsonParser parser, Sequence seq) {
        expect(parser, JsonParser.Event.START_ARRAY);
        while (parser.next() == JsonParser.Event.START_OBJECT)
            seq.add(parseReferencedSeries(parser));
    }

    private Attributes parseReferencedSeries(JsonParser parser) {
        Attributes attrs = new Attributes(2);
        while (parser.next() == JsonParser.Event.KEY_NAME) {
            switch (parser.getString()) {
                case "SeriesInstanceUID":
                    expect(parser, JsonParser.Event.VALUE_STRING);
                    attrs.setString(Tag.SeriesInstanceUID, VR.UI, parser.getString());
                    break;
                case "ReferencedSOPSequence":
                    parseReferencedSOPSequence(parser,
                            attrs.newSequence(Tag.ReferencedSOPSequence, 10));
                    break;
                default:
                    throw new WebApplicationException(getResponse("Unexpected Key name", Response.Status.BAD_REQUEST));
            }
        }
        if (!attrs.contains(Tag.SeriesInstanceUID))
            throw new WebApplicationException(getResponse("Missing SeriesInstanceUID", Response.Status.BAD_REQUEST));

        return attrs;
    }

    private void parseReferencedSOPSequence(JsonParser parser, Sequence seq) {
        expect(parser, JsonParser.Event.START_ARRAY);
        while (parser.next() == JsonParser.Event.START_OBJECT)
            seq.add(parseReferencedSOP(parser));
    }

    private Attributes parseReferencedSOP(JsonParser parser) {
        Attributes attrs = new Attributes(2);
        while (parser.next() == JsonParser.Event.KEY_NAME) {
            switch (parser.getString()) {
                case "ReferencedSOPClassUID":
                    expect(parser, JsonParser.Event.VALUE_STRING);
                    attrs.setString(Tag.ReferencedSOPClassUID, VR.UI, parser.getString());
                    break;
                case "ReferencedSOPInstanceUID":
                    expect(parser, JsonParser.Event.VALUE_STRING);
                    attrs.setString(Tag.ReferencedSOPInstanceUID, VR.UI, parser.getString());
                    break;
                default:
                    throw new WebApplicationException(getResponse("Unexpected Key name", Response.Status.BAD_REQUEST));
            }
        }
        if (!attrs.contains(Tag.ReferencedSOPClassUID))
            throw new WebApplicationException(getResponse("Missing ReferencedSOPClassUID", Response.Status.BAD_REQUEST));

        if (!attrs.contains(Tag.ReferencedSOPInstanceUID))
            throw new WebApplicationException(getResponse("Missing ReferencedSOPInstanceUID", Response.Status.BAD_REQUEST));

        return attrs;
    }

    private Response getResponse(String errorMessage, Response.Status status) {
        Object entity = "{\"errorMessage\":\"" + errorMessage + "\"}";
        return Response.status(status).entity(entity).build();
    }

    private Response getResponseAsTextPlain(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String exceptionAsString = sw.toString();
        return Response.status(Response.Status.BAD_REQUEST).entity(exceptionAsString).type("text/plain").build();
    }
}
