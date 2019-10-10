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

package org.dcm4chee.arc.iocm.rs;

import org.dcm4che3.audit.AuditMessages;
import org.dcm4che3.data.*;
import org.dcm4che3.json.JSONReader;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.util.UIDUtils;
import org.dcm4chee.arc.delete.RejectionService;
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
import org.dcm4chee.arc.qmgt.QueueSizeLimitExceededException;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.query.util.QueryAttributes;
import org.dcm4chee.arc.retrieve.RetrieveService;
import org.dcm4chee.arc.store.InstanceLocations;
import org.dcm4chee.arc.rs.client.RSForward;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreService;
import org.dcm4chee.arc.store.StoreSession;
import org.dcm4chee.arc.study.StudyMgtContext;
import org.dcm4chee.arc.study.StudyService;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.validation.constraints.InvokeValidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonException;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonParser;
import javax.json.stream.JsonParsingException;
import javax.persistence.NoResultException;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Nov 2015
 */
@RequestScoped
@Path("aets/{AETitle}/rs")
@InvokeValidate(type = IocmRS.class)
public class IocmRS {

    private static final Logger LOG = LoggerFactory.getLogger(IocmRS.class);

    @Inject
    private Device device;

    @Inject
    private QueryService queryService;

    @Inject
    private RetrieveService retrieveService;

    @Inject
    private StoreService storeService;

    @Inject
    private RejectionService rejectionService;

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

    @QueryParam("queue")
    private boolean queue;

    @QueryParam("batchID")
    private String batchID;

    @Context
    private HttpServletRequest request;

    @Context
    private UriInfo uriInfo;

    private Attributes coerceAttrs;

    @POST
    @Path("/studies/{StudyUID}/reject/{CodeValue}^{CodingSchemeDesignator}")
    @Produces("application/json")
    public Response rejectStudy(
            @PathParam("StudyUID") String studyUID,
            @PathParam("CodeValue") String codeValue,
            @PathParam("CodingSchemeDesignator") String designator) {
        return reject(RSOperation.RejectStudy, studyUID, null, null, codeValue, designator);
    }

    @POST
    @Path("/studies/{StudyUID}/series/{SeriesUID}/reject/{CodeValue}^{CodingSchemeDesignator}")
    @Produces("application/json")
    public Response rejectSeries(
            @PathParam("StudyUID") String studyUID,
            @PathParam("SeriesUID") String seriesUID,
            @PathParam("CodeValue") String codeValue,
            @PathParam("CodingSchemeDesignator") String designator) {
        return reject(RSOperation.RejectSeries, studyUID, seriesUID, null, codeValue, designator);
    }

    @POST
    @Path("/studies/{StudyUID}/series/{SeriesUID}/instances/{ObjectUID}/reject/{CodeValue}^{CodingSchemeDesignator}")
    @Produces("application/json")
    public Response rejectInstance(
            @PathParam("StudyUID") String studyUID,
            @PathParam("SeriesUID") String seriesUID,
            @PathParam("ObjectUID") String objectUID,
            @PathParam("CodeValue") String codeValue,
            @PathParam("CodingSchemeDesignator") String designator) {
        return reject(RSOperation.RejectInstance, studyUID, seriesUID, objectUID, codeValue, designator);
    }

    @POST
    @Path("/studies/{StudyUID}/copy")
    @Consumes("application/json")
    @Produces("application/json")
    public Response copyInstances(@PathParam("StudyUID") String studyUID, InputStream in) {
        return copyOrMoveInstances(studyUID, in, null, null);
    }

    @POST
    @Path("/studies/{StudyUID}/move/{CodeValue}^{CodingSchemeDesignator}")
    @Consumes("application/json")
    @Produces("application/json")
    public Response moveInstances(
            @PathParam("StudyUID") String studyUID,
            @PathParam("CodeValue") String codeValue,
            @PathParam("CodingSchemeDesignator") String designator,
            InputStream in) {
        return copyOrMoveInstances(studyUID, in, codeValue, designator);
    }

    @DELETE
    @Path("/patients/{PatientID}")
    public void deletePatient(@PathParam("PatientID") IDWithIssuer patientID) {
        ArchiveAEExtension arcAE = getArchiveAE();
        Patient patient = patientService.findPatient(patientID);
        if (patient == null)
            throw new WebApplicationException(
                    errResponse("Patient having patient ID : " + patientID + " not found.",
                    Response.Status.NOT_FOUND));
        AllowDeletePatient allowDeletePatient = arcAE.allowDeletePatient();
        String patientDeleteForbidden = allowDeletePatient == AllowDeletePatient.NEVER
                ? "Patient deletion as per configuration is never allowed."
                : allowDeletePatient == AllowDeletePatient.WITHOUT_STUDIES && patient.getNumberOfStudies() > 0
                    ? "Patient having patient ID : " + patientID + " has non empty studies."
                    : null;
        if (patientDeleteForbidden != null)
            throw new WebApplicationException(errResponse(patientDeleteForbidden, Response.Status.FORBIDDEN));

        try {
            PatientMgtContext ctx = patientService.createPatientMgtContextWEB(HttpServletRequestInfo.valueOf(request));
            ctx.setPatientID(patientID);
            ctx.setAttributes(patient.getAttributes());
            ctx.setEventActionCode(AuditMessages.EventActionCode.Delete);
            ctx.setPatient(patient);
            deletionService.deletePatient(ctx, arcAE);
            rsForward.forward(RSOperation.DeletePatient, arcAE, null, request);
        } catch (NonUniquePatientException | PatientMergedException e) {
            throw new WebApplicationException(
                    errResponse(e.getMessage(), Response.Status.CONFLICT));
        } catch (Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    @DELETE
    @Path("/studies/{StudyUID}")
    public void deleteStudy(@PathParam("StudyUID") String studyUID) {
        ArchiveAEExtension arcAE = getArchiveAE();
        try {
            deletionService.deleteStudy(studyUID, HttpServletRequestInfo.valueOf(request), arcAE);
            rsForward.forward(RSOperation.DeleteStudy, arcAE, null, request);
        } catch (StudyNotFoundException e) {
            throw new WebApplicationException(
                    errResponse("Study with study instance UID " + studyUID + " not found.",
                    Response.Status.NOT_FOUND));
        } catch (StudyNotEmptyException e) {
            throw new WebApplicationException(
                    errResponse(e.getMessage() + studyUID, Response.Status.FORBIDDEN));
        } catch (Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    @POST
    @Path("/patients")
    @Consumes({"application/dicom+json,application/json"})
    @Produces("application/json")
    public String createPatient(InputStream in) {
        ArchiveAEExtension arcAE = getArchiveAE();
        try {
            PatientMgtContext ctx = patientMgtCtx(in);
            if (!ctx.getAttributes().containsValue(Tag.PatientID)) {
                idService.newPatientID(ctx.getAttributes());
                ctx.setPatientID(IDWithIssuer.pidOf(ctx.getAttributes()));
            }
            patientService.updatePatient(ctx);
            rsForward.forward(RSOperation.CreatePatient, arcAE, ctx.getAttributes(), request);
            rsHL7Sender.sendHL7Message("ADT^A28^ADT_A05", ctx);
            return "{\"PatientID\":\"" + ctx.getAttributes().getString(Tag.PatientID) + "\"}";
        } catch (Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    private PatientMgtContext patientMgtCtx(InputStream in) {
        PatientMgtContext ctx = patientService.createPatientMgtContextWEB(HttpServletRequestInfo.valueOf(request));
        ctx.setAttributes(toAttributes(in));
        ctx.setAttributeUpdatePolicy(Attributes.UpdatePolicy.REPLACE);
        return ctx;
    }

    @PUT
    @Path("/patients/{priorPatientID}")
    @Consumes("application/dicom+json,application/json")
    public void updatePatient(
            @PathParam("priorPatientID") IDWithIssuer priorPatientID,
            @QueryParam("merge") @Pattern(regexp = "true|false") @DefaultValue("false") String merge,
            InputStream in) {
        ArchiveAEExtension arcAE = getArchiveAE();
        PatientMgtContext ctx = patientMgtCtx(in);
        IDWithIssuer targetPatientID = ctx.getPatientID();
        if (targetPatientID == null)
            throw new WebApplicationException(
                    errResponse("missing Patient ID in message body", Response.Status.BAD_REQUEST));

        boolean mergePatients = Boolean.parseBoolean(merge);
        boolean patientMatch = priorPatientID.equals(targetPatientID);
        if (patientMatch && mergePatients)
            throw new WebApplicationException(
                    errResponse("Circular Merge of Patients not allowed.", Response.Status.BAD_REQUEST));

        RSOperation rsOp = RSOperation.CreatePatient;
        String msgType = "ADT^A28^ADT_A05";
        try {
            if (patientMatch) {
                patientService.updatePatient(ctx);
                if (ctx.getEventActionCode().equals(AuditMessages.EventActionCode.Update)) {
                    rsOp = RSOperation.UpdatePatient;
                    msgType = "ADT^A31^ADT_A05";
                }
            } else {
                ctx.setPreviousAttributes(priorPatientID.exportPatientIDWithIssuer(null));
                if (mergePatients) {
                    msgType = "ADT^A40^ADT_A39";
                    rsOp = RSOperation.MergePatient;
                    patientService.mergePatient(ctx);
                } else {
                    msgType = "ADT^A47^ADT_A30";
                    rsOp = RSOperation.ChangePatientID;
                    patientService.changePatientID(ctx);
                }
            }

            if (ctx.getEventActionCode().equals(AuditMessages.EventActionCode.Read))
                return;

            rsForward.forward(rsOp, arcAE, ctx.getAttributes(), request);
            rsHL7Sender.sendHL7Message(msgType, ctx);
        } catch (PatientTrackingNotAllowedException | CircularPatientMergeException e) {
            throw new WebApplicationException(
                    errResponse(e.getMessage(), Response.Status.CONFLICT));
        } catch(Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    @POST
    @Path("/patients/{patientID}/merge")
    @Consumes("application/json")
    public void mergePatients(@PathParam("patientID") IDWithIssuer patientID, InputStream in) {
        ArchiveAEExtension arcAE = getArchiveAE();
        final Attributes attrs;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = in.read(buffer)) != -1)
                baos.write(buffer, 0, length);

            InputStream is1 = new ByteArrayInputStream(baos.toByteArray());
            attrs = parseOtherPatientIDs(is1);
            for (Attributes otherPID : attrs.getSequence(Tag.OtherPatientIDsSequence))
                mergePatient(patientID, otherPID);

            rsForward.forward(RSOperation.MergePatients, arcAE, baos.toByteArray(), null, request);
        } catch (JsonParsingException e) {
            throw new WebApplicationException(
                    errResponse(e.getMessage() + " at location : " + e.getLocation(), Response.Status.BAD_REQUEST));
        } catch (NonUniquePatientException | PatientMergedException | CircularPatientMergeException e) {
            throw new WebApplicationException(
                    errResponse(e.getMessage(), Response.Status.CONFLICT));
        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    @POST
    @Path("/patients/{priorPatientID}/merge/{patientID}")
    public void mergePatient(@PathParam("priorPatientID") IDWithIssuer priorPatientID,
                             @PathParam("patientID") IDWithIssuer patientID) {
        ArchiveAEExtension arcAE = getArchiveAE();
        try {
            Attributes priorPatAttr = new Attributes(3);
            priorPatAttr.setString(Tag.PatientID, VR.LO, priorPatientID.getID());
            setIssuer(priorPatientID, priorPatAttr);
            mergePatient(patientID, priorPatAttr);
            rsForward.forward(RSOperation.MergePatient, arcAE, null, request);
        } catch (NonUniquePatientException | PatientMergedException | CircularPatientMergeException e) {
            throw new WebApplicationException(
                    errResponse(e.getMessage(), Response.Status.CONFLICT));
        } catch (Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    private void mergePatient(IDWithIssuer patientID, Attributes priorPatAttr) throws Exception {
        PatientMgtContext patMgtCtx = patientService.createPatientMgtContextWEB(HttpServletRequestInfo.valueOf(request));
        patMgtCtx.setPatientID(patientID);
        Attributes patAttr = new Attributes(3);
        patAttr.setString(Tag.PatientID, VR.LO, patientID.getID());
        setIssuer(patientID, patAttr);
        patMgtCtx.setAttributes(patAttr);
        patMgtCtx.setPreviousAttributes(priorPatAttr);
        LOG.info("Prior patient ID {} and target patient ID {}", patMgtCtx.getPreviousPatientID(),
                patMgtCtx.getPatientID());
        patientService.mergePatient(patMgtCtx);
        rsHL7Sender.sendHL7Message("ADT^A40^ADT_A39", patMgtCtx);
    }

    @POST
    @Path("/patients/{priorPatientID}/changeid/{patientID}")
    public void changePatientID(@PathParam("priorPatientID") IDWithIssuer priorPatientID,
                                @PathParam("patientID") IDWithIssuer patientID) {
        ArchiveAEExtension arcAE = getArchiveAE();
        try {
            Patient prevPatient = patientService.findPatient(priorPatientID);
            PatientMgtContext ctx = patientService.createPatientMgtContextWEB(HttpServletRequestInfo.valueOf(request));
            ctx.setAttributeUpdatePolicy(Attributes.UpdatePolicy.REPLACE);
            ctx.setPreviousAttributes(priorPatientID.exportPatientIDWithIssuer(null));
            ctx.setAttributes(patientID.exportPatientIDWithIssuer(prevPatient.getAttributes()));
            patientService.changePatientID(ctx);
            rsHL7Sender.sendHL7Message("ADT^A47^ADT_A30", ctx);
            rsForward.forward(RSOperation.ChangePatientID, arcAE, null, request);
        } catch (PatientTrackingNotAllowedException | CircularPatientMergeException e) {
            throw new WebApplicationException(
                    errResponse(e.getMessage(), Response.Status.CONFLICT));
        } catch(Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
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
                pidQualifiers.forEach(item -> setUniversalEntityIDAndType(pidIssuer, item));
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
    public StreamingOutput updateStudy(InputStream in) {
        ArchiveAEExtension arcAE = getArchiveAE();
        final Attributes attrs = toAttributes(in);
        IDWithIssuer patientID = IDWithIssuer.pidOf(attrs);
        if (patientID == null)
            throw new WebApplicationException(
                    errResponse("missing Patient ID in message body", Response.Status.BAD_REQUEST));

        Patient patient = patientService.findPatient(patientID);
        if (patient == null)
            throw new WebApplicationException(
                    errResponse("Patient[id=" + patientID + "] does not exist.", Response.Status.NOT_FOUND));

        try {
            boolean studyIUIDPresent = attrs.containsValue(Tag.StudyInstanceUID);
            if (!studyIUIDPresent)
                attrs.setString(Tag.StudyInstanceUID, VR.UI, UIDUtils.createUID());

            StudyMgtContext ctx = studyService.createStudyMgtContextWEB(request, arcAE.getApplicationEntity());
            ctx.setPatient(patient);
            ctx.setAttributes(attrs);
            studyService.updateStudy(ctx);
            rsForward.forward(RSOperation.UpdateStudy, arcAE, attrs, request);
            return out -> {
                    try (JsonGenerator gen = Json.createGenerator(out)) {
                        new JSONWriter(gen).write(attrs);
                    }
            };
        } catch (Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    @PUT
    @Path("/studies/{StudyInstanceUID}/access/{accessControlID}")
    public Response updateStudyAccessControlID(
            @PathParam("StudyInstanceUID") String studyUID,
            @PathParam("accessControlID") String accessControlID) {
        ArchiveAEExtension arcAE = getArchiveAE();
        try {
            StudyMgtContext ctx = studyService.createStudyMgtContextWEB(request, arcAE.getApplicationEntity());
            ctx.setStudyInstanceUID(studyUID);
            ctx.setAccessControlID("null".equals(accessControlID) ? "*" :  accessControlID);
            ctx.setEventActionCode(AuditMessages.EventActionCode.Update);
            if (studyService.updateAccessControlID(ctx) == 0)
                return errResponse("Study not found. " + studyUID, Response.Status.NOT_FOUND);

            rsForward.forward(RSOperation.UpdateStudyAccessControlID, arcAE, null, request);
            return Response.noContent().build();
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @PUT
    @Path("/studies/{studyUID}/expire/{expirationDate}")
    public Response updateStudyExpirationDate(
            @PathParam("studyUID") String studyUID,
            @PathParam("expirationDate") String expirationDate,
            @QueryParam("ExporterID") String expirationExporterID,
            @QueryParam("FreezeExpirationDate") @Pattern(regexp = "true|false") String freezeExpirationDate) {
        return updateExpirationDate(RSOperation.UpdateStudyExpirationDate, studyUID, null, expirationDate,
                expirationExporterID, freezeExpirationDate);
    }

    @PUT
    @Path("/studies/{studyUID}/series/{seriesUID}/expire/{expirationDate}")
    public Response updateSeriesExpirationDate(
            @PathParam("studyUID") String studyUID, @PathParam("seriesUID") String seriesUID,
            @PathParam("expirationDate") String expirationDate,
            @QueryParam("ExporterID") String expirationExporterID) {
        return updateExpirationDate(RSOperation.UpdateSeriesExpirationDate, studyUID, seriesUID, expirationDate,
                expirationExporterID, null);
    }

    private Response updateExpirationDate(RSOperation op, String studyUID, String seriesUID, String expirationDate,
                                          String expirationExporterID, String freezeExpirationDate) {
        boolean updateSeriesExpirationDate = seriesUID != null;
        ArchiveAEExtension arcAE = getArchiveAE();
        try {
            StudyMgtContext ctx = studyService.createStudyMgtContextWEB(request, arcAE.getApplicationEntity());
            ctx.setStudyInstanceUID(studyUID);
            boolean revokeExpiration = expirationDate.equals("never");
            if (revokeExpiration && seriesUID != null)
                return errResponse("Revoke expiration on Series not allowed.", Response.Status.BAD_REQUEST);

            ctx.setExpirationDate(
                    revokeExpiration ? null : LocalDate.parse(expirationDate, DateTimeFormatter.BASIC_ISO_DATE));
            ctx.setExpirationExporterID(expirationExporterID);
            ctx.setFreezeExpirationDate(Boolean.parseBoolean(freezeExpirationDate));
            if ("false".equals(freezeExpirationDate))
                ctx.setUnfreezeExpirationDate(true);
            ctx.setSeriesInstanceUID(seriesUID);
            studyService.updateExpirationDate(ctx);
            rsForward.forward(op, arcAE, null, request);
            return Response.noContent().build();
        } catch (DateTimeParseException e) {
            return errResponse("Expiration date cannot be parsed.", Response.Status.BAD_REQUEST);
        } catch (NoResultException e) {
            return errResponse(
                    updateSeriesExpirationDate ? "Series not found. " + seriesUID : "Study not found. " + studyUID,
                    Response.Status.NOT_FOUND);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
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
                                              InputStream in) {
        ArchiveAEExtension arcAE = getArchiveAE();
        try {
            RejectionNote rjNote = toRejectionNote(codeValue, designator);
            Attributes instanceRefs = parseSOPInstanceReferences(in);

            ProcedureContext ctx = procedureService.createProcedureContextWEB(request);
            ctx.setStudyInstanceUID(studyUID);
            ctx.setSpsID(spsID);

            MWLItem mwl = procedureService.findMWLItem(ctx);
            if (mwl == null)
                return errResponse("MWLItem[studyUID=" + studyUID + ", spsID=" + spsID + "] does not exist.",
                        Response.Status.NOT_FOUND);

            ctx.setAttributes(mwl.getAttributes());
            ctx.setPatient(mwl.getPatient());
            ctx.setSourceInstanceRefs(instanceRefs);

            StoreSession session = storeService.newStoreSession(
                    HttpServletRequestInfo.valueOf(request), arcAE.getApplicationEntity(), null)
                    .withObjectStorageID(rejectionNoteObjectStorageID());

            restoreInstances(session, instanceRefs);
            Collection<InstanceLocations> instanceLocations = toInstanceLocations(studyUID, instanceRefs, session);
            if (instanceLocations.isEmpty())
                return errResponse("No Instances found. ", Response.Status.NOT_FOUND);


            final Attributes result;
            if (studyUID.equals(instanceRefs.getString(Tag.StudyInstanceUID))) {
                procedureService.updateStudySeriesAttributes(ctx);
                result = getResult(instanceLocations);
            } else {
                Attributes sopInstanceRefs = getSOPInstanceRefs(instanceRefs, instanceLocations, arcAE.getApplicationEntity());
                moveSequence(sopInstanceRefs, Tag.ReferencedSeriesSequence, instanceRefs);
                session.setAcceptConflictingPatientID(AcceptConflictingPatientID.YES);
                session.setPatientUpdatePolicy(Attributes.UpdatePolicy.PRESERVE);
                session.setStudyUpdatePolicy(arcAE.linkMWLEntryUpdatePolicy());
                result = storeService.copyInstances(
                        session, instanceLocations, instAttrs(mwl), Attributes.UpdatePolicy.OVERWRITE);
                rejectInstances(instanceRefs, rjNote, session, result);
            }
            return toResponse(result);
        } catch (IllegalStateException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private Attributes instAttrs(MWLItem mwlItem) {
        Attributes mwlItemAttrs = mwlItem.getAttributes();
        Attributes attrs = new Attributes(mwlItemAttrs, arcDev().getAttributeFilter(Entity.Study).getSelection());
        attrs.addAll(mwlItem.getPatient().getAttributes());
        attrs.setString(Tag.StudyDescription, VR.LO, mwlItemAttrs.getString(Tag.RequestedProcedureDescription));
        attrs.setString(Tag.StudyID, VR.SH, mwlItemAttrs.getString(Tag.RequestedProcedureID));
        attrs.newSequence(Tag.RequestAttributesSequence, 1)
                .add(mwlItem.getRequestAttributesSequenceItem());
        return attrs;
    }

    private Response toResponse(Attributes result) {
        StreamingOutput entity = out -> {
                try (JsonGenerator gen = Json.createGenerator(out)) {
                    new JSONWriter(gen).write(result);
                }
        };
        return Response.status(status(result)).entity(entity).build();
    }

    private Attributes getResult(Collection<InstanceLocations> instanceLocations) {
        Attributes result = new Attributes();
        Sequence refSOPSeq = result.newSequence(Tag.ReferencedSOPSequence, instanceLocations.size());
        instanceLocations.forEach(instanceLocation -> populateResult(refSOPSeq, instanceLocation));
        return result;
    }

    private void populateResult(Sequence refSOPSeq, InstanceLocations instanceLocation) {
        Attributes refSOP = new Attributes(2);
        refSOP.setString(Tag.ReferencedSOPClassUID, VR.UI, instanceLocation.getSopClassUID());
        refSOP.setString(Tag.ReferencedSOPInstanceUID, VR.UI, instanceLocation.getSopInstanceUID());
        refSOPSeq.add(refSOP);
    }

    public void validate() {
        logRequest();
        String[] uriPath = StringUtils.split(uriInfo.getPath(), '/');
        if ("copy".equals(uriPath[uriPath.length -1])
            || ("move".equals(uriPath[uriPath.length -2])
                && "studies".equals(uriPath[uriPath.length -4]))) {
            coerceAttrs = new QueryAttributes(uriInfo, null).getQueryKeys();
        }
    }

    private void logRequest() {
        LOG.info("Process {} {}?{} from {}@{}",
                request.getMethod(),
                request.getRequestURI(),
                request.getQueryString(),
                request.getRemoteUser(),
                request.getRemoteHost());
    }

    private Attributes toAttributes(InputStream in) {
        try {
            return new JSONReader(Json.createParser(new InputStreamReader(in, StandardCharsets.UTF_8)))
                    .readDataset(null);
        } catch (JsonParsingException e) {
            throw new WebApplicationException(
                    errResponse(e.getMessage() + " at location : " + e.getLocation(), Response.Status.BAD_REQUEST));
        } catch (Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    private ArchiveAEExtension getArchiveAE() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        if (ae == null || !ae.isInstalled())
            throw new WebApplicationException(
                    errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND));
        return ae.getAEExtensionNotNull(ArchiveAEExtension.class);
    }

    private Response reject(RSOperation rsOp, String studyUID, String seriesUID, String objectUID,
                        String codeValue, String designator) {
        logRequest();
        ArchiveAEExtension arcAE = getArchiveAE();

        try {
            RejectionNote rjNote = toRejectionNote(codeValue, designator);
            if (queue)
                return queueReject(rsOp, arcAE, studyUID, seriesUID, objectUID, rjNote);

            int count = rejectionService.reject(arcAE.getApplicationEntity(), studyUID, seriesUID, objectUID, rjNote,
                    HttpServletRequestInfo.valueOf(request));
            if (count == 0) {
                return errResponse("No instances of Study[UID=" + studyUID + "] found for rejection.",
                        Response.Status.NOT_FOUND);
            }
            rsForward.forward(rsOp, arcAE, null, request);
            return Response.ok("{\"count\":" + count + '}').build();
        } catch (IllegalStateException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    private Response queueReject(RSOperation rsOp, ArchiveAEExtension arcAE, String studyUID, String seriesUID,
                                 String objectUID, RejectionNote rjNote) {
        try {
            rejectionService.scheduleReject(aet, studyUID, seriesUID, objectUID, rjNote.getRejectionNoteCode(),
                    HttpServletRequestInfo.valueOf(request), batchID);
        } catch (QueueSizeLimitExceededException e) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
        }
        rsForward.forward(rsOp, arcAE, null, request);
        return Response.accepted().build();
    }

    private Response copyOrMoveInstances(String studyUID, InputStream in, String codeValue, String designator) {
        logRequest();
        ArchiveAEExtension arcAE = getArchiveAE();
        try {
            RejectionNote rjNote = toRejectionNote(codeValue, designator);
            Attributes instanceRefs = parseSOPInstanceReferences(in);
            StoreSession session = storeService.newStoreSession(
                    HttpServletRequestInfo.valueOf(request), arcAE.getApplicationEntity(), null);
            if (rjNote != null)
                session.withObjectStorageID(rejectionNoteObjectStorageID());

            restoreInstances(session, instanceRefs);
            Collection<InstanceLocations> instances = toInstanceLocations(studyUID, instanceRefs, session);
            if (instances.isEmpty())
                return errResponse("No Instances found. ", Response.Status.NOT_FOUND);

            Attributes sopInstanceRefs = getSOPInstanceRefs(instanceRefs, instances, arcAE.getApplicationEntity());
            moveSequence(sopInstanceRefs, Tag.ReferencedSeriesSequence, instanceRefs);
            session.setAcceptConflictingPatientID(AcceptConflictingPatientID.YES);
            session.setPatientUpdatePolicy(Attributes.UpdatePolicy.PRESERVE);
            session.setStudyUpdatePolicy(arcAE.copyMoveUpdatePolicy());
            Attributes result = storeService.copyInstances(
                    session, instances, coerceAttrs, Attributes.UpdatePolicy.MERGE);
            if (rjNote != null)
                rejectInstances(instanceRefs, rjNote, session, result);

            return toResponse(result);
        } catch (IllegalStateException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    private Collection<InstanceLocations> toInstanceLocations(
            String studyUID, Attributes instanceRefs, StoreSession session) {
        try {
            return retrieveService.queryInstances(session, instanceRefs, studyUID);
        } catch (Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    private void restoreInstances(StoreSession session, Attributes sopInstanceRefs) {
        try {
            String studyUID = sopInstanceRefs.getString(Tag.StudyInstanceUID);
            Sequence seq = sopInstanceRefs.getSequence(Tag.ReferencedSeriesSequence);
            if (seq == null || seq.isEmpty())
                storeService.restoreInstances(session, studyUID, null, null);
            else for (Attributes item : seq)
                storeService.restoreInstances(session, studyUID, item.getString(Tag.SeriesInstanceUID), null);
        } catch (Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    private RejectionNote toRejectionNote(String codeValue, String designator) {
        if (codeValue == null)
            return null;

        RejectionNote rjNote = arcDev().getRejectionNote(
                new Code(codeValue, designator, null, ""));

        if (rjNote == null)
            throw new WebApplicationException(
                    errResponse("Unknown Rejection Note Code: (" + codeValue + ", " + designator + ')',
                    Response.Status.NOT_FOUND));

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
            return Collections.emptySet();

        Set<String> failedIUIDs = new HashSet<>(failedSOPSeq.size() * 4 / 3 + 1);
        failedSOPSeq.forEach(failedSOPRef -> failedIUIDs.add(failedSOPRef.getString(Tag.ReferencedSOPInstanceUID)));
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
                : result.getSequence(Tag.FailedSOPSequence) == null
                    || result.getSequence(Tag.FailedSOPSequence).isEmpty()
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

    private Attributes getSOPInstanceRefs(Attributes instanceRefs, Collection<InstanceLocations> instances,
                                          ApplicationEntity ae) {
        String sourceStudyUID = instanceRefs.getString(Tag.StudyInstanceUID);
        Attributes refStudy = new Attributes(2);
        Sequence refSeriesSeq = refStudy.newSequence(Tag.ReferencedSeriesSequence, 10);
        refStudy.setString(Tag.StudyInstanceUID, VR.UI, sourceStudyUID);
        HashMap<String, Sequence> seriesMap = new HashMap<>();
        instances.forEach(instance -> {
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
        });
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
            throw new WebApplicationException(
                    errResponse("Unexpected " + next, Response.Status.BAD_REQUEST));
    }

    private Attributes parseOtherPatientIDs(InputStream in) {
        JsonParser parser = Json.createParser(new InputStreamReader(in, StandardCharsets.UTF_8));
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
                        otherPID.newSequence(Tag.IssuerOfPatientIDQualifiersSequence, 2)
                                .add(parseIssuerOfPIDQualifier(parser));
                        break;
                    default:
                        throw new WebApplicationException(
                                errResponse("Unexpected Key name", Response.Status.BAD_REQUEST));
                }
            }
            otherPIDseq.add(otherPID);
        }
        if (otherPIDseq.isEmpty())
            throw new WebApplicationException(
                    errResponse("Patients to be merged not sent in the request.", Response.Status.BAD_REQUEST));
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
                            errResponse("Unexpected Key name", Response.Status.BAD_REQUEST));
            }
        }
        return attr;
    }

    private Attributes parseSOPInstanceReferences(InputStream in) {
        Attributes attrs = new Attributes(2);
        try {
            JsonParser parser = Json.createParser(new InputStreamReader(in, StandardCharsets.UTF_8));
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
                        throw new WebApplicationException(
                                errResponse("Unexpected Key name", Response.Status.BAD_REQUEST));
                }
            }
        } catch (JsonParsingException e) {
            throw new WebApplicationException(
                    errResponse(e.getMessage() + " at location : " + e.getLocation(), Response.Status.BAD_REQUEST));
        } catch (NoSuchElementException e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }

        if (!attrs.contains(Tag.StudyInstanceUID))
            throw new WebApplicationException(
                    errResponse("Missing StudyInstanceUID", Response.Status.BAD_REQUEST));

        return attrs;
    }

    private void parseReferencedSeriesSequence(JsonParser parser, Sequence seq) {
        expect(parser, JsonParser.Event.START_ARRAY);
        while (parser.next() == JsonParser.Event.START_OBJECT)
            seq.add(parseReferencedSeries(parser));
    }

    private Attributes parseReferencedSeries(JsonParser parser) {
        Attributes attrs = new Attributes(2);
        try {
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
                        throw new WebApplicationException(
                                errResponse("Unexpected Key name", Response.Status.BAD_REQUEST));
                }
            }
        } catch (JsonException | NoSuchElementException e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }

        if (!attrs.contains(Tag.SeriesInstanceUID))
            throw new WebApplicationException(
                    errResponse("Missing SeriesInstanceUID", Response.Status.BAD_REQUEST));

        return attrs;
    }

    private void parseReferencedSOPSequence(JsonParser parser, Sequence seq) {
        expect(parser, JsonParser.Event.START_ARRAY);
        while (parser.next() == JsonParser.Event.START_OBJECT)
            seq.add(parseReferencedSOP(parser));
    }

    private Attributes parseReferencedSOP(JsonParser parser) {
        Attributes attrs = new Attributes(2);
        try {
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
                        throw new WebApplicationException(
                                errResponse("Unexpected Key name", Response.Status.BAD_REQUEST));
                }
            }
        } catch (JsonException | NoSuchElementException e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }

        if (!attrs.contains(Tag.ReferencedSOPClassUID))
            throw new WebApplicationException(
                    errResponse("Missing ReferencedSOPClassUID", Response.Status.BAD_REQUEST));

        if (!attrs.contains(Tag.ReferencedSOPInstanceUID))
            throw new WebApplicationException(
                    errResponse("Missing ReferencedSOPInstanceUID", Response.Status.BAD_REQUEST));

        return attrs;
    }

    private String rejectionNoteObjectStorageID() {
        String rjNoteStorageAET = arcDev().getRejectionNoteStorageAET();
        if (rjNoteStorageAET == null)
            return null;

        ApplicationEntity rjAE = device.getApplicationEntity(rjNoteStorageAET, true);
        ArchiveAEExtension rjArcAE;
        if (rjAE == null || !rjAE.isInstalled() || (rjArcAE = rjAE.getAEExtension(ArchiveAEExtension.class)) == null) {
            LOG.warn("Rejection Note Storage Application Entity with an Archive AE Extension not configured: {}",
                    rjNoteStorageAET);
            return null;
        }

        String[] objectStorageIDs;
        if ((objectStorageIDs = rjArcAE.getObjectStorageIDs()).length > 0)
            return objectStorageIDs[0];

        LOG.warn("Object storage for rejection notes shall fall back on those configured for AE: {} since none are " +
                "configured for RejectionNoteStorageAE: {}", aet, rjNoteStorageAET);
        return null;
    }

    private ArchiveDeviceExtension arcDev() {
        return device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
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
}
