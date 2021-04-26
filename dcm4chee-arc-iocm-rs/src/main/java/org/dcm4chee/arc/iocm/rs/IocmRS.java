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

import org.dcm4che3.data.*;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.delete.RejectionService;
import org.dcm4chee.arc.entity.MWLItem;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.procedure.ProcedureContext;
import org.dcm4chee.arc.procedure.ProcedureService;
import org.dcm4chee.arc.qmgt.QueueSizeLimitExceededException;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.query.util.QueryAttributes;
import org.dcm4chee.arc.retrieve.RetrieveService;
import org.dcm4chee.arc.rs.client.RSForward;
import org.dcm4chee.arc.store.InstanceLocations;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreService;
import org.dcm4chee.arc.store.StoreSession;
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
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.IntFunction;

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
    private RSForward rsForward;

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

    @Override
    public String toString() {
        String requestURI = request.getRequestURI();
        String queryString = request.getQueryString();
        return queryString == null ? requestURI : requestURI + '?' + queryString;
    }

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
            ctx.setArchiveAEExtension(arcAE);
            ctx.setStudyInstanceUID(studyUID);
            ctx.setSpsID(spsID);

            MWLItem mwl = procedureService.findMWLItem(ctx);
            if (mwl == null)
                return errResponse("MWLItem[studyUID=" + studyUID + ", spsID=" + spsID + "] does not exist.",
                        Response.Status.NOT_FOUND);

            ctx.setAttributes(mwl.getAttributes());
            ctx.setPatient(mwl.getPatient());
            ctx.setSourceInstanceRefs(instanceRefs);

            String changeRequesterAET = arcAE.changeRequesterAET();
            StoreSession session = storeService.newStoreSession(
                    HttpServletRequestInfo.valueOf(request),
                    arcAE.getApplicationEntity(),
                    changeRequesterAET != null ? changeRequesterAET : arcAE.getApplicationEntity().getAETitle())
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
        } catch (DicomServiceException e) {
            return errResponse(IocmRS::rejectFailed, e);
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
        mwlItem.addItemToRequestAttributesSequence(attrs.newSequence(Tag.RequestAttributesSequence, 1));
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
            || ("move".equals(uriPath[uriPath.length -2]) && "studies".equals(uriPath[uriPath.length -4])))
            coerceAttrs = new QueryAttributes(uriInfo, null).getQueryKeys();
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
                toString(),
                request.getRemoteUser(),
                request.getRemoteHost());
    }

    private Response reject(RSOperation rsOp, String studyUID, String seriesUID, String objectUID,
                        String codeValue, String designator) {
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
        } catch (DicomServiceException e) {
            return errResponse(IocmRS::rejectFailed, e);
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
        ArchiveAEExtension arcAE = getArchiveAE();
        try {
            RejectionNote rjNote = toRejectionNote(codeValue, designator);
            Attributes instanceRefs = parseSOPInstanceReferences(in);
            String changeRequesterAET = arcAE.changeRequesterAET();
            StoreSession session = storeService.newStoreSession(
                    HttpServletRequestInfo.valueOf(request),
                    arcAE.getApplicationEntity(),
                    changeRequesterAET != null ? changeRequesterAET : arcAE.getApplicationEntity().getAETitle());
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
        } catch (DicomServiceException e) {
            return errResponse(IocmRS::rejectFailed, e);
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

    private Response errResponse(IntFunction<Response.Status> httpStatusOf, DicomServiceException e) {
        return errResponse(e.getMessage(), httpStatusOf.apply(e.getStatus()));
    }

    private static Response.Status rejectFailed(int status) {
        switch (status) {
            case StoreService.CONFLICTING_PID_NOT_ACCEPTED:
            case StoreService.CONFLICTING_PATIENT_ATTRS_REJECTED:
                return Response.Status.CONFLICT;
            case StoreService.SUBSEQUENT_OCCURRENCE_OF_REJECTED_OBJECT:
            case StoreService.DUPLICATE_REJECTION_NOTE:
            case StoreService.REJECTION_FAILED_ALREADY_REJECTED:
            case StoreService.PATIENT_ID_MISSING_IN_OBJECT:
                return Response.Status.BAD_REQUEST;
            case StoreService.REJECTION_FAILED_NO_SUCH_INSTANCE:
                return Response.Status.NOT_FOUND;
            case StoreService.REJECTION_FOR_RETENTION_POLICY_EXPIRED_NOT_ALLOWED:
            case StoreService.RETENTION_PERIOD_OF_STUDY_NOT_YET_EXPIRED:
                return Response.Status.FORBIDDEN;
        }
        return Response.Status.INTERNAL_SERVER_ERROR;
    }
}
