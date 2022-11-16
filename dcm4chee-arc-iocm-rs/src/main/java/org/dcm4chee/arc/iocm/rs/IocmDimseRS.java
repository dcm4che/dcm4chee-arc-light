package org.dcm4chee.arc.iocm.rs;


import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.IApplicationEntityCache;
import org.dcm4che3.data.*;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Priority;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.MergeMWLQueryParam;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.Entity;
import org.dcm4chee.arc.conf.RejectionNote;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.keycloak.KeycloakContext;
import org.dcm4chee.arc.procedure.ProcedureContext;
import org.dcm4chee.arc.procedure.ProcedureService;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.query.scu.CFindSCU;
import org.dcm4chee.arc.retrieve.RetrieveService;
import org.dcm4chee.arc.store.StoreService;
import org.dcm4chee.arc.store.StoreSession;
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
import javax.ws.rs.core.UriInfo;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.function.IntFunction;

@RequestScoped
@Path("aets/{AETitle}/dimse/{mwlscp}")
public class IocmDimseRS {

    private static final Logger LOG = LoggerFactory.getLogger(IocmDimseRS.class);
    private static final String SUPER_USER_ROLE = "super-user-role";

    @Context
    private HttpServletRequest request;

    @Context
    private UriInfo uriInfo;

    @Inject
    private Device device;

    @Inject
    private IApplicationEntityCache aeCache;

    @Inject
    private ProcedureService procedureService;

    @Inject
    private StoreService storeService;

    @Inject
    private RetrieveService retrieveService;

    @Inject
    private QueryService queryService;

    @Inject
    private CFindSCU cFindSCU;

    @PathParam("AETitle")
    private String aet;

    @PathParam("mwlscp")
    private String mwlscp;

    @Override
    public String toString() {
        String requestURI = request.getRequestURI();
        String queryString = request.getQueryString();
        return queryString == null ? requestURI : requestURI + '?' + queryString;
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
        logRequest();
        ArchiveAEExtension arcAE = getArchiveAE();
        if (arcAE == null)
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);

        validateAcceptedUserRoles(arcAE);
        RejectionNote rjNote = toRejectionNote(codeValue, designator);
        ProcedureContext ctx = procedureService.createProcedureContext()
                .setHttpServletRequest(HttpServletRequestInfo.valueOf(request));
        ctx.setArchiveAEExtension(arcAE);
        ctx.setStudyInstanceUID(studyUID);
        ctx.setSpsID(spsID);
        try {
            aeCache.findApplicationEntity(mwlscp);
            List<Attributes> mwlItems = cFindSCU.findMWLItems(
                    arcAE.getApplicationEntity(),
                    new MergeMWLQueryParam(mwlscp, StringUtils.EMPTY_STRING, null, null, null, studyUID, spsID),
                    Priority.NORMAL);
            if (mwlItems.isEmpty())
                return errResponse("MWLItem[studyUID=" + studyUID + ", spsID=" + spsID + "] does not exist.",
                        Response.Status.NOT_FOUND);
            if (mwlItems.size() > 1)
                return errResponse(mwlscp + " returned multiple MWLItems for [studyUID=" + studyUID + ", spsID=" + spsID + "]",
                        Response.Status.CONFLICT);

            Attributes mwlAttrs = mwlItems.get(0);
            ctx.setAttributes(mwlAttrs);
            String changeRequesterAET = arcAE.changeRequesterAET();
            StoreSession session = storeService.newStoreSession(
                    HttpServletRequestInfo.valueOf(request),
                    arcAE.getApplicationEntity(),
                    aet,
                    changeRequesterAET != null ? changeRequesterAET : arcAE.getApplicationEntity().getAETitle())
                    .withObjectStorageID(rejectionNoteObjectStorageID());

            Attributes result = IocmUtils.linkInstancesWithMWL(
                    session, retrieveService, procedureService, ctx, queryService, rjNote, instAttrs(mwlAttrs), in);
            return result == null
                    ? errResponse("No Instances found.", Response.Status.NOT_FOUND)
                    : toResponse(result);
        } catch (IllegalStateException | ConfigurationException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (IllegalArgumentException e) {
            return errResponse(e.getMessage(), Response.Status.BAD_REQUEST);
        } catch (DicomServiceException e) {
            return errResponse(IocmDimseRS::rejectFailed, e);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private Response toResponse(Attributes result) {
        StreamingOutput entity = out -> {
            try (JsonGenerator gen = Json.createGenerator(out)) {
                new JSONWriter(gen).write(result);
            }
        };
        return Response.status(status(result)).entity(entity).build();
    }

    private Response.Status status(Attributes result) {
        return result.getSequence(Tag.ReferencedSOPSequence).isEmpty()
                ? Response.Status.CONFLICT
                : result.getSequence(Tag.FailedSOPSequence) == null
                    || result.getSequence(Tag.FailedSOPSequence).isEmpty()
                    ? Response.Status.OK : Response.Status.ACCEPTED;
    }

    private Attributes instAttrs(Attributes mwlItemAttrs) {
        Attributes attrs = new Attributes(mwlItemAttrs, arcDev().getAttributeFilter(Entity.Study).getSelection());
        attrs.addSelected(mwlItemAttrs, arcDev().getAttributeFilter(Entity.Patient).getSelection());
        attrs.setString(Tag.StudyDescription, VR.LO, mwlItemAttrs.getString(Tag.RequestedProcedureDescription));
        attrs.setString(Tag.StudyID, VR.SH, mwlItemAttrs.getString(Tag.RequestedProcedureID));
        Sequence reqAttrSeq = attrs.newSequence(Tag.RequestAttributesSequence, 1);
        Attributes spsItem = mwlItemAttrs.getNestedDataset(Tag.ScheduledProcedureStepSequence);
        Attributes item = new Attributes(7);
        reqAttrSeq.add(item);
        item.addSelected(mwlItemAttrs, REQUEST_ATTR);
        item.addSelected(spsItem, SPS_REQUEST_ATTR);
        return attrs;
    }

    private static final int[] REQUEST_ATTR = {
            Tag.AccessionNumber,
            Tag.RequestedProcedureID,
            Tag.StudyInstanceUID,
            Tag.RequestedProcedureDescription
    };

    private static final int[] SPS_REQUEST_ATTR = {
            Tag.ScheduledProcedureStepDescription,
            Tag.ScheduledProtocolCodeSequence,
            Tag.ScheduledProcedureStepID
    };

    private ArchiveAEExtension getArchiveAE() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        return ae == null || !ae.isInstalled() ? null : ae.getAEExtensionNotNull(ArchiveAEExtension.class);
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

    private ArchiveDeviceExtension arcDev() {
        return device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
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

    private void logRequest() {
        LOG.info("Process {} {} from {}@{}",
                request.getMethod(),
                toString(),
                request.getRemoteUser(),
                request.getRemoteHost());
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
}
