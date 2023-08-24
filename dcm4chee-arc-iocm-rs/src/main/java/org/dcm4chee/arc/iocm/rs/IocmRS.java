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

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import jakarta.ws.rs.core.UriInfo;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Code;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.WebApplication;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.delete.RejectionService;
import org.dcm4chee.arc.entity.MWLItem;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.keycloak.KeycloakContext;
import org.dcm4chee.arc.procedure.ProcedureContext;
import org.dcm4chee.arc.procedure.ProcedureService;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.query.util.QueryAttributes;
import org.dcm4chee.arc.retrieve.RetrieveService;
import org.dcm4chee.arc.rs.client.RSForward;
import org.dcm4chee.arc.store.StoreService;
import org.dcm4chee.arc.store.StoreSession;
import org.dcm4chee.arc.validation.ParseDateTime;
import org.dcm4chee.arc.validation.constraints.InvokeValidate;
import org.dcm4chee.arc.validation.constraints.ValidValueOf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.Json;
import javax.json.stream.JsonGenerator;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
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
    private static final String SUPER_USER_ROLE = "super-user-role";

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

    @QueryParam("scheduledTime")
    @ValidValueOf(type = ParseDateTime.class)
    private String scheduledTime;

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
                                              @QueryParam("strategy") @Pattern(regexp = "IOCM|MERGE") String strategy,
                                              InputStream in) {
        ArchiveAEExtension arcAE = getArchiveAE();
        if (arcAE == null)
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);

        validateAcceptedUserRoles(arcAE);
        if (aet.equals(arcAE.getApplicationEntity().getAETitle()))
            validateWebAppServiceClass();
        RejectionNote rjNote = toRejectionNote(codeValue, designator);
        try {
            ProcedureContext ctx = procedureService.createProcedureContext()
                    .setHttpServletRequest(HttpServletRequestInfo.valueOf(request));
            ctx.setArchiveAEExtension(arcAE);
            ctx.setStudyInstanceUID(studyUID);
            ctx.setSpsID(spsID);
            MWLItem mwl = procedureService.findMWLItem(ctx);
            if (mwl == null)
                return errResponse("MWLItem[studyUID=" + studyUID + ", spsID=" + spsID + "] does not exist.",
                        Response.Status.NOT_FOUND);

            ctx.setAttributes(mwl.getAttributes());
            ctx.setPatient(mwl.getPatient());
            ctx.setLinkStrategy(strategy);
            String changeRequesterAET = arcAE.changeRequesterAET();
            StoreSession session = storeService.newStoreSession(
                    HttpServletRequestInfo.valueOf(request),
                    arcAE.getApplicationEntity(),
                    aet,
                    changeRequesterAET != null ? changeRequesterAET : arcAE.getApplicationEntity().getAETitle())
                    .withObjectStorageID(rejectionNoteObjectStorageID());

            Attributes result = IocmUtils.linkInstancesWithMWL(
                    session, retrieveService, procedureService, ctx, queryService, rjNote, instAttrs(mwl), in);
            if (linkStudyToMWLMerge(ctx, strategy))
                rsForward.forward(RSOperation.LinkStudyToMWLMerge, arcAE, ctx.getSourceInstanceRefs(), request);
            return result == null
                    ? errResponse("No Instances found.", Response.Status.NOT_FOUND)
                    : toResponse(result);
        } catch (IllegalStateException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (IllegalArgumentException e) {
            return errResponse(e.getMessage(), Response.Status.BAD_REQUEST);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private boolean linkStudyToMWLMerge(ProcedureContext ctx, String strategy) {
        return strategy == null
                ? ctx.getStudyInstanceUID().equals(ctx.getSourceInstanceRefs().getString(Tag.StudyInstanceUID))
                : strategy.equals("MERGE");
    }

    private Attributes instAttrs(MWLItem mwlItem) {
        Attributes mwlItemAttrs = mwlItem.getAttributes();
        Attributes patAttrs = mwlItem.getPatient().getAttributes();
        Attributes.unifyCharacterSets(mwlItemAttrs, patAttrs);
        Attributes attrs = new Attributes(mwlItemAttrs, arcDev().getAttributeFilter(Entity.Study).getSelection());
        attrs.addAll(patAttrs);
        attrs.setString(Tag.StudyDescription, VR.LO, mwlItemAttrs.getString(Tag.RequestedProcedureDescription));
        attrs.setString(Tag.StudyID, VR.SH, mwlItemAttrs.getString(Tag.RequestedProcedureID));
        attrs.setString(Tag.InstitutionName, VR.LO, mwlItemAttrs.getString(Tag.InstitutionName));
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

    public void validate() {
        logRequest();
        String[] uriPath = StringUtils.split(uriInfo.getPath(), '/');
        if ("copy".equals(uriPath[uriPath.length -1])
            || ("move".equals(uriPath[uriPath.length -2]) && "studies".equals(uriPath[uriPath.length -4])))
            coerceAttrs = new QueryAttributes(uriInfo, null).getQueryKeys();
    }

    private ArchiveAEExtension getArchiveAE() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        return ae == null || !ae.isInstalled() ? null : ae.getAEExtensionNotNull(ArchiveAEExtension.class);
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
        if (arcAE == null)
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);

        validateAcceptedUserRoles(arcAE);
        if (aet.equals(arcAE.getApplicationEntity().getAETitle()))
            validateWebAppServiceClass();
        try {
            RejectionNote rjNote = toRejectionNote(codeValue, designator);
            if (queue)
                return queueReject(rsOp, arcAE, studyUID, seriesUID, objectUID, rjNote);

            int count = rejectionService.reject(arcAE.getApplicationEntity(), aet, studyUID, seriesUID, objectUID, rjNote,
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
        rejectionService.createRejectionTask(aet,
                                        rjNote.getRejectionNoteCode(),
                                        HttpServletRequestInfo.valueOf(request),
                                        batchID,
                                        scheduledTime(),
                                        studyUID,
                                        seriesUID,
                                        objectUID);
        rsForward.forward(rsOp, arcAE, null, request);
        return Response.accepted().build();
    }

    private Response copyOrMoveInstances(String studyUID, InputStream in, String codeValue, String designator) {
        ArchiveAEExtension arcAE = getArchiveAE();
        if (arcAE == null)
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);

        validateAcceptedUserRoles(arcAE);
        if (aet.equals(arcAE.getApplicationEntity().getAETitle()))
            validateWebAppServiceClass();
        RejectionNote rjNote = toRejectionNote(codeValue, designator);
        try {
            String changeRequesterAET = arcAE.changeRequesterAET();
            StoreSession session = storeService.newStoreSession(
                    HttpServletRequestInfo.valueOf(request),
                    arcAE.getApplicationEntity(),
                    aet,
                    changeRequesterAET != null ? changeRequesterAET : arcAE.getApplicationEntity().getAETitle());
            if (rjNote != null)
                session.withObjectStorageID(rejectionNoteObjectStorageID());

            Attributes result = IocmUtils.copyMove(
                                        session, retrieveService, queryService, studyUID, coerceAttrs, rjNote, in);
            return result == null
                    ? errResponse("No Instances found.", Response.Status.NOT_FOUND)
                    : toResponse(result);
        } catch (IllegalStateException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (IllegalArgumentException e) {
            return errResponse(e.getMessage(), Response.Status.BAD_REQUEST);
        } catch (DicomServiceException e) {
            return errResponse(IocmRS::rejectFailed, e);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
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

    private Response.Status status(Attributes result) {
        return result.getSequence(Tag.ReferencedSOPSequence).isEmpty()
                ? Response.Status.CONFLICT
                : result.getSequence(Tag.FailedSOPSequence) == null
                    || result.getSequence(Tag.FailedSOPSequence).isEmpty()
                    ? Response.Status.OK : Response.Status.ACCEPTED;
    }

    private Date scheduledTime() {
        if (scheduledTime != null)
            try {
                return new SimpleDateFormat("yyyyMMddhhmmss").parse(scheduledTime);
            } catch (Exception e) {
                LOG.info(e.getMessage());
            }
        return new Date();
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
