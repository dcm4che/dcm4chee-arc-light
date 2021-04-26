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
import org.dcm4che3.conf.api.ConfigurationNotFoundException;
import org.dcm4che3.data.*;
import org.dcm4che3.json.JSONReader;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Priority;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.AttributesFormat;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.AllowDeletePatient;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.RSOperation;
import org.dcm4chee.arc.delete.DeletionService;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.hl7.RESTfulHL7Sender;
import org.dcm4chee.arc.id.IDService;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.patient.*;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.query.scu.CFindSCU;
import org.dcm4chee.arc.query.util.QueryAttributes;
import org.dcm4chee.arc.rs.client.RSForward;
import org.dcm4chee.arc.validation.constraints.InvokeValidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonParser;
import javax.json.stream.JsonParsingException;
import javax.persistence.criteria.CriteriaQuery;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;
import java.io.*;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Apr 2021
 */

@RequestScoped
@Path("aets/{AETitle}/rs")
@InvokeValidate(type = PamRS.class)
public class PamRS {
    private static final Logger LOG = LoggerFactory.getLogger(PamRS.class);

    @Inject
    private Device device;

    @Inject
    private IDService idService;

    @Inject
    private RSForward rsForward;

    @Inject
    private PatientService patientService;

    @Inject
    private DeletionService deletionService;

    @Inject
    private RESTfulHL7Sender rsHL7Sender;

    @Inject
    private QueryService queryService;

    @Inject
    private CFindSCU cfindscu;

    @PathParam("AETitle")
    private String aet;

    @QueryParam("fuzzymatching")
    @Pattern(regexp = "true|false")
    private String fuzzymatching;

    @Context
    private HttpServletRequest request;

    @Context
    private UriInfo uriInfo;

    @Override
    public String toString() {
        String requestURI = request.getRequestURI();
        String queryString = request.getQueryString();
        return queryString == null ? requestURI : requestURI + '?' + queryString;
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
            ctx.setArchiveAEExtension(arcAE);
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

    @POST
    @Path("/patients")
    @Consumes({"application/dicom+json,application/json"})
    @Produces("application/json")
    public String createPatient(InputStream in) {
        ArchiveAEExtension arcAE = getArchiveAE();
        try {
            PatientMgtContext ctx = patientMgtCtx(in);
            ctx.setArchiveAEExtension(arcAE);
            if (!ctx.getAttributes().containsValue(Tag.PatientID)) {
                idService.newPatientID(ctx.getAttributes());
                ctx.setPatientID(IDWithIssuer.pidOf(ctx.getAttributes()));
            }
            patientService.updatePatient(ctx);
            rsForward.forward(RSOperation.CreatePatient, arcAE, ctx.getAttributes(), request);
            rsHL7Sender.sendHL7Message("ADT^A28^ADT_A05", ctx);
            return "{\"PatientID\":\"" + IDWithIssuer.pidOf(ctx.getAttributes()) + "\"}";
        } catch (NonUniquePatientException e) {
            throw new WebApplicationException(errResponse(e.getMessage(), Response.Status.CONFLICT));
        } catch (PatientMergedException e) {
            throw new WebApplicationException(errResponse(e.getMessage(), Response.Status.FORBIDDEN));
        } catch (Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    private PatientMgtContext patientMgtCtx(InputStream in) {
        PatientMgtContext ctx = patientMgtCtx();
        ctx.setAttributes(toAttributes(in));
        return ctx;
    }

    private PatientMgtContext patientMgtCtx() {
        PatientMgtContext ctx = patientService.createPatientMgtContextWEB(HttpServletRequestInfo.valueOf(request));
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
        ctx.setArchiveAEExtension(arcAE);
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
                    rsOp = RSOperation.MergePatient2;
                    patientService.mergePatient(ctx);
                } else {
                    msgType = "ADT^A47^ADT_A30";
                    rsOp = RSOperation.ChangePatientID2;
                    patientService.changePatientID(ctx);
                }
            }

            if (ctx.getEventActionCode().equals(AuditMessages.EventActionCode.Read))
                return;

            rsForward.forward(rsOp, arcAE, ctx.getAttributes(), request);
            rsHL7Sender.sendHL7Message(msgType, ctx);
        } catch (PatientAlreadyExistsException | NonUniquePatientException | PatientTrackingNotAllowedException
                | CircularPatientMergeException e) {
            throw new WebApplicationException(errResponse(e.getMessage(), Response.Status.CONFLICT));
        } catch (PatientMergedException e) {
            throw new WebApplicationException(errResponse(e.getMessage(), Response.Status.FORBIDDEN));
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
                mergePatient(patientID, otherPID, arcAE);

            rsForward.forward(RSOperation.MergePatient, arcAE, baos.toByteArray(), null, request);
        } catch (JsonParsingException e) {
            throw new WebApplicationException(
                    errResponse(e.getMessage() + " at location : " + e.getLocation(), Response.Status.BAD_REQUEST));
        } catch (NonUniquePatientException | PatientMergedException | CircularPatientMergeException e) {
            throw new WebApplicationException(
                    errResponse(e.getMessage(), Response.Status.CONFLICT));
        } catch (Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    @POST
    @Path("/patients/{priorPatientID}/merge/{patientID}")
    public void mergePatient(@PathParam("priorPatientID") IDWithIssuer priorPatientID,
                             @PathParam("patientID") IDWithIssuer patientID,
                             @QueryParam("verify") String findSCP) {
        ArchiveAEExtension arcAE = getArchiveAE();
        try {
            if (findSCP != null)
                verifyMergePatient(priorPatientID, patientID, findSCP, cfindscu, arcAE.getApplicationEntity());
            mergePatient(patientID,
                    priorPatientID.exportPatientIDWithIssuer(null),
                    arcAE);
            rsForward.forward(RSOperation.MergePatient, arcAE, null, request);
        } catch (NonUniquePatientException
                | PatientMergedException
                | CircularPatientMergeException
                | VerifyMergePatientException e) {
            throw new WebApplicationException(errResponse(e.getMessage(), Response.Status.CONFLICT));
        } catch (Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    @POST
    @Path("/patients/issuer/{issuer}")
    public Response supplementIssuer(
            @PathParam("issuer") AttributesFormat issuer,
            @QueryParam("test") @Pattern(regexp = "true|false") @DefaultValue("false") String test) {
        ArchiveAEExtension arcAE = getArchiveAE();
        Set<IDWithIssuer> success = new HashSet<>();
        Map<IDWithIssuer, Long> ambiguous = new HashMap<>();
        Map<String, String> failures = new HashMap<>();
        try {
            QueryAttributes queryAttrs = new QueryAttributes(uriInfo, null);
            Attributes queryKeys = queryAttrs.getQueryKeys();
            if (queryKeys.getString(Tag.IssuerOfPatientID) != null
                    || queryKeys.getNestedDataset(Tag.IssuerOfPatientIDQualifiersSequence) != null)
                return errResponse(
                        "Issuer of Patient ID or Issuer of Patient ID Qualifiers Sequence not allowed in query filters",
                        Response.Status.BAD_REQUEST);

            CriteriaQuery<Patient> query = queryService.createPatientWithUnknownIssuerQuery(
                    queryParam(arcAE.getApplicationEntity()), queryKeys);
            String toManyDuplicates = null;
            int supplementIssuerFetchSize = arcAE.getArchiveDeviceExtension().getSupplementIssuerFetchSize();
            boolean testIssuer = Boolean.parseBoolean(test);
            if (testIssuer) {
                patientService.testSupplementIssuers(query, supplementIssuerFetchSize, success, ambiguous, issuer);
            } else {
                Set<Long> failedPks = new HashSet<>();
                boolean remaining;
                int carry = 0;
                do {
                    int limit = supplementIssuerFetchSize + failedPks.size() + carry;
                    List<Patient> matches = patientService.queryWithLimit(query, limit);
                    remaining = matches.size() == limit;
                    matches.removeIf(p -> failedPks.contains(p.getPk()));
                    if (matches.isEmpty())
                        break;

                    carry = 0;
                    if (remaining) {
                        try {
                            ListIterator<Patient> itr = matches.listIterator(matches.size());
                            toManyDuplicates = itr.previous().getPatientID().getID();
                            do {
                                itr.remove();
                                carry++;
                            } while (toManyDuplicates.equals(itr.previous().getPatientID().getID()));
                            toManyDuplicates = null;
                        } catch (NoSuchElementException e) {
                            break;
                        }
                    }
                    matches.stream()
                            .collect(Collectors.groupingBy(
                                    p -> new IDWithIssuer(p.getPatientID().getID(),issuer.format(p.getAttributes()))))
                            .forEach((idWithIssuer, patients) -> {
                                if (patients.size() > 1) {
                                    ambiguous.put(idWithIssuer, Long.valueOf(patients.size()));
                                    patients.stream().map(Patient::getPk).forEach(failedPks::add);
                                } else {
                                    Patient patient = patients.get(0);
                                    try {
                                        if (patientService.supplementIssuer(
                                                patientMgtCtx(), patient, idWithIssuer, ambiguous)) {
                                            success.add(idWithIssuer);
                                        } else {
                                            failedPks.add(patient.getPk());
                                        }
                                    } catch (Exception e) {
                                        failures.put(idWithIssuer.toString(), e.getMessage());
                                        failedPks.add(patient.getPk());
                                    }
                                }
                            });
                } while (remaining && failedPks.size() < supplementIssuerFetchSize);
                if (!success.isEmpty())
                    rsForward.forward(RSOperation.SupplementIssuer, arcAE, null, request);
            }
            return supplementIssuerResponse(success, ambiguous, failures, toManyDuplicates).build();
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private static Response.ResponseBuilder supplementIssuerResponse(
            Set<IDWithIssuer> success,
            Map<IDWithIssuer, Long> ambiguous,
            Map<String, String> failures,
            String toManyDuplicates) {
        boolean ok = ambiguous.isEmpty() && failures.isEmpty() && toManyDuplicates == null;
        return (ok && success.isEmpty())
                ? Response.status(Response.Status.NO_CONTENT)
                : Response.status(ok
                ? Response.Status.OK
                : success.isEmpty()
                ? Response.Status.CONFLICT
                : Response.Status.ACCEPTED)
                .entity((StreamingOutput) out -> supplementIssuerResponsePayload(
                        success, ambiguous, failures, toManyDuplicates, out));
    }

    private static void supplementIssuerResponsePayload(
            Set<IDWithIssuer> success,
            Map<IDWithIssuer, Long> ambiguous,
            Map<String, String> failures,
            String toManyDuplicates,
            OutputStream out) {
        JsonGenerator gen = Json.createGenerator(out);
        gen.writeStartObject();
        if (!success.isEmpty()) {
            gen.writeStartArray("pids");
            success.stream().map(Object::toString).forEach(gen::write);
            gen.writeEnd();
        }
        if (!ambiguous.isEmpty()) {
            gen.writeStartArray("ambiguous");
            ambiguous.forEach((idWithIssuer, count) -> {
                gen.writeStartObject();
                gen.write("pid", idWithIssuer.toString());
                gen.write("count", count);
                gen.writeEnd();
            });
            gen.writeEnd();
        }
        if (!failures.isEmpty()) {
            gen.writeStartArray("failures");
            failures.forEach((pid, errorMsg) -> {
                gen.writeStartObject();
                gen.write("pid", pid);
                gen.write("errorMessage", errorMsg);
                gen.writeEnd();
            });
            gen.writeEnd();
        }
        if (toManyDuplicates != null) {
            gen.write("tooManyDuplicates", toManyDuplicates);
        }
        gen.writeEnd();
        gen.flush();
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

    private org.dcm4chee.arc.query.util.QueryParam queryParam(ApplicationEntity ae) {
        org.dcm4chee.arc.query.util.QueryParam queryParam = new org.dcm4chee.arc.query.util.QueryParam(ae);
        queryParam.setFuzzySemanticMatching(Boolean.parseBoolean(fuzzymatching));
        queryParam.setWithoutIssuer(true);
        return queryParam;
    }

    private void verifyMergePatient(IDWithIssuer priorPatientID, IDWithIssuer patientID, String findSCP,
                                    CFindSCU cfindscu, ApplicationEntity localAE) throws Exception {
        try {
            List<Attributes> studiesOfPriorPatient = cfindscu.findStudiesOfPatient(
                    localAE, findSCP, Priority.NORMAL, priorPatientID);
            if (!studiesOfPriorPatient.isEmpty()) {
                throw new VerifyMergePatientException("Found " + studiesOfPriorPatient.size()
                        + " studies of prior Patient[id=" + priorPatientID + "] at " + findSCP);
            }
            Patient priorPatient = patientService.findPatient(priorPatientID);
            if (priorPatient != null) {
                for (String studyIUID : patientService.studyInstanceUIDsOf(priorPatient)) {
                    studiesOfPriorPatient = cfindscu.findStudy(localAE, findSCP, Priority.NORMAL, studyIUID,
                            Tag.PatientID, Tag.IssuerOfPatientID, Tag.IssuerOfPatientIDQualifiersSequence);
                    if (!studiesOfPriorPatient.isEmpty()) {
                        IDWithIssuer findPatientID = IDWithIssuer.pidOf(studiesOfPriorPatient.get(0));
                        if (!findPatientID.matches(patientID)) {
                            throw new VerifyMergePatientException("Found Study[uid=" + studyIUID
                                    + "] of different Patient[id=" + findPatientID
                                    + "] than target Patient[id=" + patientID
                                    + "] at " + findSCP);
                        }
                    }
                }
            }
        } catch (ConfigurationNotFoundException e) {
            throw new WebApplicationException(
                    errResponse("No such Application Entity: " + findSCP, Response.Status.NOT_FOUND));
        } catch (ConnectException | DicomServiceException e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.BAD_GATEWAY));
        }
    }

    private static class VerifyMergePatientException extends Exception {
        public VerifyMergePatientException(String message) {
            super(message);
        }
    }

    private void mergePatient(IDWithIssuer patientID, Attributes priorPatAttr, ArchiveAEExtension arcAE) throws Exception {
        PatientMgtContext patMgtCtx = patientService.createPatientMgtContextWEB(HttpServletRequestInfo.valueOf(request));
        patMgtCtx.setArchiveAEExtension(arcAE);
        patMgtCtx.setPatientID(patientID);
        patMgtCtx.setAttributes(patientID.exportPatientIDWithIssuer(null));
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
            ctx.setArchiveAEExtension(arcAE);
            ctx.setAttributeUpdatePolicy(Attributes.UpdatePolicy.REPLACE);
            ctx.setPreviousAttributes(priorPatientID.exportPatientIDWithIssuer(null));
            ctx.setAttributes(patientID.exportPatientIDWithIssuer(prevPatient.getAttributes()));
            patientService.changePatientID(ctx);
            rsHL7Sender.sendHL7Message("ADT^A47^ADT_A30", ctx);
            rsForward.forward(RSOperation.ChangePatientID, arcAE, null, request);
        } catch (PatientAlreadyExistsException | NonUniquePatientException | PatientTrackingNotAllowedException
                | CircularPatientMergeException e) {
            throw new WebApplicationException(errResponse(e.getMessage(), Response.Status.CONFLICT));
        } catch (PatientMergedException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.FORBIDDEN);
        } catch(Exception e) {
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

    private void logRequest() {
        LOG.info("Process {} {} from {}@{}",
                request.getMethod(),
                this,
                request.getRemoteUser(),
                request.getRemoteHost());
    }

    public void validate() {
        logRequest();
        String[] uriPath = StringUtils.split(uriInfo.getPath(), '/');
        if ("issuer".equals(uriPath[uriPath.length - 2]))
            new QueryAttributes(uriInfo, null);
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

    private void expect(JsonParser parser, JsonParser.Event expected) {
        JsonParser.Event next = parser.next();
        if (next != expected)
            throw new WebApplicationException(
                    errResponse("Unexpected " + next, Response.Status.BAD_REQUEST));
    }

    private Response errResponse(String msg, Response.Status status) {
        return errResponseAsTextPlain("{\"errorMessage\":\"" + msg + "\"}", status);
    }

    private Response errResponseAsTextPlain(String errorMsg, Response.Status status) {
        LOG.info("Response {} caused by {}", status, errorMsg);
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
