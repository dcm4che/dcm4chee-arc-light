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
import org.dcm4che3.conf.json.JsonReader;
import org.dcm4che3.data.*;
import org.dcm4che3.json.JSONReader;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.util.ByteUtils;
import org.dcm4che3.util.UIDUtils;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.delete.DeletionService;
import org.dcm4chee.arc.delete.StudyNotEmptyException;
import org.dcm4chee.arc.delete.StudyNotFoundException;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.id.IDService;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.patient.PatientService;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.retrieve.InstanceLocations;
import org.dcm4chee.arc.rs.client.RSClient;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreService;
import org.dcm4chee.arc.store.StoreSession;
import org.dcm4chee.arc.study.StudyMgtContext;
import org.dcm4chee.arc.study.StudyService;
import org.dcm4chee.arc.validation.constraints.ValidValueOf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.keycloak.KeycloakSecurityContext;
import org.keycloak.adapters.RefreshableKeycloakSecurityContext;

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
import java.net.InetAddress;
import java.net.URI;
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
    private static final String ORG_KEYCLOAK_KEYCLOAK_SECURITY_CONTEXT = "org.keycloak.KeycloakSecurityContext";

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
    private RSClient rsClient;

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
    public StreamingOutput copyInstances(@PathParam("StudyUID") String studyUID, InputStream in) throws Exception {
        return copyOrMoveInstances(RSOperation.CopyInstances, studyUID, in, null);
    }

    @POST
    @Path("/studies/{StudyUID}/move/{CodeValue}^{CodingSchemeDesignator}")
    @Consumes("application/json")
    @Produces("application/json")
    public StreamingOutput moveInstances(
            @PathParam("StudyUID") String studyUID,
            @PathParam("CodeValue") String codeValue,
            @PathParam("CodingSchemeDesignator") String designator,
            InputStream in) throws Exception {
        return copyOrMoveInstances(RSOperation.MoveInstances, studyUID, in, new Code(codeValue, designator, null, "?"));
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
        PatientMgtContext ctx = patientService.createPatientMgtContextWEB(request, arcAE.getApplicationEntity());
        ctx.setPatientID(patientID);
        ctx.setAttributes(patient.getAttributes());
        ctx.setEventActionCode(AuditMessages.EventActionCode.Delete);
        ctx.setPatient(patient);
        deletionService.deletePatient(ctx);
        forwardRS("DELETE", RSOperation.DeletePatient, arcAE, null);
    }

    @DELETE
    @Path("/studies/{StudyUID}")
    public void deleteStudy(@PathParam("StudyUID") String studyUID) throws Exception {
        logRequest();
        ArchiveAEExtension arcAE = getArchiveAE();
        try {
            deletionService.deleteStudy(studyUID, request, arcAE.getApplicationEntity());
            forwardRS("DELETE", RSOperation.DeleteStudy, arcAE, null);
        } catch (StudyNotFoundException e) {
            throw new WebApplicationException(getResponse("Study having study instance UID " + studyUID + " not found.",
                    Response.Status.NOT_FOUND));
        } catch (StudyNotEmptyException e) {
            throw new WebApplicationException(getResponse(e.getMessage() + studyUID, Response.Status.FORBIDDEN));
        }
    }

    @POST
    @Path("/patients")
    @Consumes("application/json")
    public String createPatient(InputStream in) throws Exception {
        logRequest();
        ArchiveAEExtension arcAE = getArchiveAE();
        try {
            JSONReader reader = new JSONReader(Json.createParser(new InputStreamReader(in, "UTF-8")));
            Attributes attrs = reader.readDataset(null);
            if (attrs.containsValue(Tag.PatientID))
                throw new WebApplicationException(getResponse("Patient ID in message body", Response.Status.BAD_REQUEST));
            idService.newPatientID(attrs);
            PatientMgtContext ctx = patientService.createPatientMgtContextWEB(request, arcAE.getApplicationEntity());
            ctx.setAttributes(attrs);
            ctx.setAttributeUpdatePolicy(Attributes.UpdatePolicy.REPLACE);
            patientService.updatePatient(ctx);
            forwardRS(HttpMethod.PUT, RSOperation.CreatePatient, arcAE, attrs);
            return IDWithIssuer.pidOf(attrs).toString();
        } catch (JsonParsingException e) {
            throw new WebApplicationException(
                    getResponse(e.getMessage() + " at location : " + e.getLocation(), Response.Status.INTERNAL_SERVER_ERROR));
        } catch (IOException e) {
            throw new WebApplicationException(getResponse(e.getMessage(), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    @PUT
    @Path("/patients/{PatientID}")
    @Consumes("application/json")
    public void updatePatient(@PathParam("PatientID") IDWithIssuer patientID, InputStream in) throws Exception {
        logRequest();
        ArchiveAEExtension arcAE = getArchiveAE();
        try {
            PatientMgtContext ctx = patientService.createPatientMgtContextWEB(request, arcAE.getApplicationEntity());

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
            forwardRS(HttpMethod.PUT, newPatient ? RSOperation.CreatePatient : RSOperation.UpdatePatient, arcAE, attrs);
        } catch (JsonParsingException e) {
            throw new WebApplicationException(
                    getResponse(e.getMessage() + " at location : " + e.getLocation(), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    @POST
    @Path("/patients/{patientID}/merge")
    @Consumes("application/json")
    public void mergePatient(@PathParam("patientID") IDWithIssuer patientID, InputStream in) throws Exception {
        logRequest();
        try {
            final Attributes attrs = parseOtherPatientIDs(in);
            PatientMgtContext patMgtCtx = patientService.createPatientMgtContextWEB(request, getArchiveAE().getApplicationEntity());
            patMgtCtx.setPatientID(patientID);
            Attributes patAttr = new Attributes(2);
            patAttr.setString(Tag.PatientID, VR.LO, patientID.toString());
            patMgtCtx.setAttributes(patAttr);
            for (Attributes otherPID : attrs.getSequence(Tag.OtherPatientIDsSequence)) {
                patMgtCtx.setPreviousAttributes(otherPID);
                patientService.mergePatient(patMgtCtx);
            }
        } catch (JsonParsingException e) {
            throw new WebApplicationException(
                    getResponse(e.getMessage() + " at location : " + e.getLocation(), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    @POST
    @Path("/studies")
    @Consumes("application/json")
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
                throw new WebApplicationException(getResponse("Patient[id=" + patientID + "] does not exists",
                        Response.Status.NOT_FOUND));

            boolean studyIUIDPresent = attrs.containsValue(Tag.StudyInstanceUID);
            if (!studyIUIDPresent)
                attrs.setString(Tag.StudyInstanceUID, VR.UI, UIDUtils.createUID());

            StudyMgtContext ctx = studyService.createStudyMgtContextWEB(request, arcAE.getApplicationEntity());
            ctx.setPatient(patient);
            ctx.setAttributes(attrs);
            studyService.updateStudy(ctx);
            forwardRS(HttpMethod.POST, RSOperation.UpdateStudy, arcAE, attrs);
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
            forwardRS(HttpMethod.PUT, op, arcAE, null);
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

    private void logRequest() {
        LOG.info("Process {} {} from {}@{}", request.getMethod(), request.getRequestURI(),
                request.getRemoteUser(), request.getRemoteHost());
    }

    private ArchiveAEExtension getArchiveAE() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        if (ae == null || !ae.isInstalled())
            throw new WebApplicationException(getResponse(
                    "No such Application Entity: " + aet,
                    Response.Status.SERVICE_UNAVAILABLE));
        ArchiveAEExtension arcAE = ae.getAEExtension(ArchiveAEExtension.class);
        if (request.getAttribute(ORG_KEYCLOAK_KEYCLOAK_SECURITY_CONTEXT) != null)
            if(!authenticatedUser(request, arcAE.getAcceptedUserRoles()))
                throw new WebApplicationException(getResponse("User not allowed to perform this service.", Response.Status.FORBIDDEN));
        return arcAE;
    }

    private boolean authenticatedUser(HttpServletRequest request, String[] acceptedUserRoles) {
        RefreshableKeycloakSecurityContext securityContext = (RefreshableKeycloakSecurityContext)
                request.getAttribute(KeycloakSecurityContext.class.getName());
        Set<String> userRoles = securityContext.getToken().getRealmAccess().getRoles();
        for (String s : userRoles)
            if (Arrays.asList(acceptedUserRoles).contains(s))
                return true;
        return false;
    }

    private void reject(RSOperation rsOp, String studyUID, String seriesUID, String objectUID,
                        String codeValue, String designator) throws IOException {
        logRequest();
        ArchiveAEExtension arcAE = getArchiveAE();
        ArchiveDeviceExtension arcDev = arcAE.getArchiveDeviceExtension();
        Code code = new Code(codeValue, designator, null, "?");
        RejectionNote rjNote = arcDev.getRejectionNote(code);
        if (rjNote == null)
            throw new WebApplicationException(getResponse("Unknown Rejection Note Code: " + code, Response.Status.NOT_FOUND));

        Attributes attrs = queryService.createRejectionNote(
                arcAE.getApplicationEntity(), studyUID, seriesUID, objectUID, rjNote);
        if (attrs == null)
            throw new WebApplicationException(getResponse("No Study with UID: " + studyUID, Response.Status.NOT_FOUND));

        StoreSession session = storeService.newStoreSession(request, aet, arcAE.getApplicationEntity());
        StoreContext ctx = storeService.newStoreContext(session);
        ctx.setSopClassUID(attrs.getString(Tag.SOPClassUID));
        ctx.setSopInstanceUID(attrs.getString(Tag.SOPInstanceUID));
        ctx.setReceiveTransferSyntax(UID.ExplicitVRLittleEndian);
        storeService.store(ctx, attrs);
        forwardRS(HttpMethod.POST, rsOp, arcAE, null);
    }

    private void forwardRS(String method, RSOperation rsOp, ArchiveAEExtension arcAE, Attributes attrs) {
        List<RSForwardRule> rules = arcAE.findRSForwardRules(rsOp);
        for (RSForwardRule rule : rules) {
            String baseURI = rule.getBaseURI();
            try {
                if (!request.getRemoteAddr().equals(
                        InetAddress.getByName(URI.create(baseURI).getHost()).getHostAddress())) {
                    rsClient.scheduleRequest(method, mkForwardURI(baseURI, rsOp, attrs), toContent(attrs));
                }
            } catch (Exception e) {
                LOG.warn("Failed to apply {}:\n", rule, e);
            }
        }
    }

    private String mkForwardURI(String baseURI, RSOperation rsOp, Attributes attrs) {
        String requestURI = request.getRequestURI();
        int requestURIIndex = requestURI.indexOf("/rs");
        int baseURIIndex = baseURI.indexOf("/rs");
        StringBuilder sb = new StringBuilder(requestURI.length() + 16);
        sb.append(baseURI.substring(0, baseURIIndex));
        sb.append(requestURI.substring(requestURIIndex));
        if (rsOp == RSOperation.CreatePatient)
            sb.append(IDWithIssuer.pidOf(attrs));
        return sb.toString();
    }

    private static byte[] toContent(Attributes attrs) {
        if (attrs == null)
            return ByteUtils.EMPTY_BYTES;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JsonGenerator gen = Json.createGenerator(out)) {
            new JSONWriter(gen).write(attrs);
        }
        return out.toByteArray();
    }

    private StreamingOutput copyOrMoveInstances(RSOperation op, String studyUID, InputStream in, Code code)
            throws Exception {
        logRequest();
        Attributes instanceRefs = parseSOPInstanceReferences(in);
        ArchiveAEExtension arcAE = getArchiveAE();
        ArchiveDeviceExtension arcDev = arcAE.getArchiveDeviceExtension();

        Map<String, String> uidMap = new HashMap<>();
        StoreSession session = storeService.newStoreSession(request, aet, arcAE.getApplicationEntity());
        Collection<InstanceLocations> instances = storeService.queryInstances(session, instanceRefs, studyUID, uidMap);
        if (instances.isEmpty())
            throw new WebApplicationException(getResponse("No Instances found. ", Response.Status.NOT_FOUND));
        Attributes sopInstanceRefs = getSOPInstanceRefs(instanceRefs, instances, arcAE.getApplicationEntity(), false);
        moveSequence(sopInstanceRefs, Tag.ReferencedSeriesSequence, instanceRefs);
        rejectInstanceRefs(code, instanceRefs, session, arcDev);
        final Attributes result = storeService.copyInstances(session, instances, uidMap);
        forwardRS(HttpMethod.POST, op, arcAE, instanceRefs);
        return new StreamingOutput() {
            @Override
            public void write(OutputStream out) throws IOException {
                try (JsonGenerator gen = Json.createGenerator(out)) {
                    new JSONWriter(gen).write(result);
                }
            }
        };
    }

    private Attributes getSOPInstanceRefs(Attributes instanceRefs, Collection<InstanceLocations> instances,
                  ApplicationEntity ae, boolean availability) {
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
            Attributes refSOP = new Attributes(3);
            if (availability)
                refSOP.setString(Tag.InstanceAvailability, VR.CS, instance.getAvailability().toString());
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

    private void rejectInstanceRefs(Code code, Attributes instanceRefs, StoreSession session,
                                            ArchiveDeviceExtension arcDev) throws IOException {
        RejectionNote rjNote = null;
        if (code != null) {
            rjNote = arcDev.getRejectionNote(code);
            if (rjNote == null)
                throw new WebApplicationException(getResponse("Unknown Rejection Note Code: " + code, Response.Status.NOT_FOUND));
        }
        if (rjNote != null) {
            Attributes ko = queryService.createRejectionNote(instanceRefs, rjNote);
            StoreContext ctx = storeService.newStoreContext(session);
            ctx.setSopClassUID(ko.getString(Tag.SOPClassUID));
            ctx.setSopInstanceUID(ko.getString(Tag.SOPInstanceUID));
            ctx.setReceiveTransferSyntax(UID.ExplicitVRLittleEndian);
            storeService.store(ctx, ko);
        }
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
}
