/*
 * **** BEGIN LICENSE BLOCK *****
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
 * Portions created by the Initial Developer are Copyright (C) 2015-2019
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
 * **** END LICENSE BLOCK *****
 *
 */

package org.dcm4chee.arc.ups.rs;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.UIDUtils;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.query.util.QueryAttributes;
import org.dcm4chee.arc.rs.util.MediaTypeUtils;
import org.dcm4chee.arc.ups.UPSContext;
import org.dcm4chee.arc.ups.UPSService;
import org.dcm4chee.arc.validation.constraints.InvokeValidate;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.*;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Sep 2019
 */
@RequestScoped
@Path("aets/{AETitle}/rs")
@InvokeValidate(type = UpsRS.class)
public class UpsRS {

    private static final Logger LOG = LoggerFactory.getLogger(UpsRS.class);

    private static final int[] EXCLUDE_FROM_REPLACEMENT = {
            Tag.SOPClassUID,
            Tag.SOPInstanceUID,
            Tag.ScheduledProcedureStepModificationDateTime,
            Tag.ProcedureStepProgressInformationSequence,
            Tag.UnifiedProcedureStepPerformedProcedureSequence
    };

    @PathParam("AETitle")
    private String aet;

    @QueryParam("accept")
    private List<String> accept;

    @QueryParam("charset")
    private String charset;

    @QueryParam("deletionlock")
    @Pattern(regexp = "true|false")
    private String deletionlock;

    @Context
    private UriInfo uriInfo;

    @Context
    private HttpServletRequest request;

    @Context
    private HttpHeaders headers;

    @Inject
    private Device device;

    @Inject
    private UPSService service;

    private Attributes matchKeys;

    @POST
    @Path("/workitems")
    public Response createUPS(
            @QueryParam("workitem") String iuid,
            @QueryParam("template") @Pattern(regexp = "true|false") String template,
            InputStream in) {
        InputType inputType = InputType.valueOf(headers.getMediaType());
        if (inputType == null)
            return notAcceptable();

        return createUPS(iuid, Boolean.parseBoolean(template), inputType.parse(in));
    }

    @POST
    @Path("/workitems/{workitem}")
    public Response updateUPS(@PathParam("workitem") String iuid, InputStream in) {
        InputType inputType = InputType.valueOf(headers.getMediaType());
        if (inputType == null)
            return notAcceptable();

        return updateUPS(iuid, inputType.parse(in));
    }

    @PUT
    @Path("/workitems/{workitem}/state/{requester}")
    public Response changeUPSState(
            @PathParam("workitem") String iuid,
            @PathParam("requester") String requester,
            InputStream in) {
        InputType inputType = InputType.valueOf(headers.getMediaType());
        if (inputType == null)
            return notAcceptable();

        return changeUPSState(iuid, requester, inputType.parse(in));
    }

    @GET
    @NoCache
    @Path("/workitems/{workitem}")
    public Response retrieveUPS(@PathParam("workitem") String iuid) {
        ResponseMediaType responseMediaType = getResponseMediaType();
        UPSContext ctx = service.newUPSContext(HttpServletRequestInfo.valueOf(request), getArchiveAE());
        ctx.setUPSInstanceUID(iuid);
        try {
            service.findUPS(ctx);
        } catch (DicomServiceException e) {
            return errResponse(UpsRS::retrieveFailed, e);
        }
        return Response.ok(responseMediaType.entity(ctx.getAttributes()), responseMediaType.type).build();
    }

    @POST
    @Path("/workitems/{workitem}/cancelrequest/{requester}")
    public Response requestUPSCancel(
            @PathParam("workitem") String iuid,
            @PathParam("requester") String requester) {
        return requestUPSCancel(iuid, requester, new Attributes());
    }

    @POST
    @Path("/workitems/{workitem}/cancelrequest/{requester}")
    @Consumes({"application/dicom+json", "application/dicom+xml"})
    public Response requestUPSCancel(
            @PathParam("workitem") String iuid,
            @PathParam("requester") String requester,
            InputStream in) {
        InputType inputType = InputType.valueOf(headers.getMediaType());
        if (inputType == null)
            return notAcceptable();

        return requestUPSCancel(iuid, requester, inputType.parse(in));
    }

    @POST
    @Path("/workitems/{workitem}/subscribers/{SubscriberAET}")
    public Response subscribe(
            @PathParam("workitem") String iuid,
            @PathParam("SubscriberAET") String subscriber) {
        UPSContext ctx = service.newUPSContext(HttpServletRequestInfo.valueOf(request), getArchiveAE());
        ctx.setUPSInstanceUID(iuid);
        ctx.setSubscriberAET(subscriber);
        ctx.setDeletionLock(Boolean.parseBoolean(deletionlock));
        ctx.setAttributes(matchKeys);
        try {
            service.createSubscription(ctx);
        } catch (DicomServiceException e) {
            return errResponse(UpsRS::subscriptionFailed, e);
        }
        return Response.created(websocketOf(ctx)).build();
    }

    @DELETE
    @Path("/workitems/{workitem}/subscribers/{SubscriberAET}")
    public Response unsubscribe(
            @PathParam("workitem") String iuid,
            @PathParam("SubscriberAET") String subscriber) {
        UPSContext ctx = service.newUPSContext(HttpServletRequestInfo.valueOf(request), getArchiveAE());
        ctx.setUPSInstanceUID(iuid);
        ctx.setSubscriberAET(subscriber);
        try {
            service.deleteSubscription(ctx);
        } catch (DicomServiceException e) {
            return errResponse(UpsRS::internalServerError, e);
        }
        return Response.ok().build();
    }

    @POST
    @Path("/workitems/{workitem}/subscribers/{SubscriberAET}/suspend")
    public Response suspendSubscription(
            @PathParam("workitem") String iuid,
            @PathParam("SubscriberAET") String subscriber) {
        UPSContext ctx = service.newUPSContext(HttpServletRequestInfo.valueOf(request), getArchiveAE());
        ctx.setUPSInstanceUID(iuid);
        ctx.setSubscriberAET(subscriber);
        try {
            service.suspendSubscription(ctx);
        } catch (DicomServiceException e) {
            return errResponse(UpsRS::internalServerError, e);
        }
        return Response.ok().build();
    }

    @POST
    @Path("/workitems/{workitem}/reschedule")
    public Response rescheduleWorkitem(
            @PathParam("workitem") String iuid,
            @QueryParam("newWorkitem") String newIUID,
            @QueryParam("upsScheduledTime") String upsScheduledTime) {
        UPSContext ctx = service.newUPSContext(HttpServletRequestInfo.valueOf(request), getArchiveAE());
        ctx.setUPSInstanceUID(iuid);
        try {
            service.findUPS(ctx);
            return createUPS(newIUID, false, replacement(ctx.getAttributes(), upsScheduledTime));
        } catch (DicomServiceException e) {
            return errResponse(UpsRS::retrieveFailed, e);
        }
    }

    private Attributes replacement(Attributes upsAttrs, String upsScheduledTime) {
        Attributes replacement = new Attributes(upsAttrs.size());
        replacement.addNotSelected(upsAttrs, EXCLUDE_FROM_REPLACEMENT);
        replacement.setDate(Tag.ScheduledProcedureStepStartDateTime, VR.DT, scheduledTime(upsScheduledTime));
        replacement.setString(Tag.ProcedureStepState, VR.CS, "SCHEDULED");
        replacement.ensureSequence(Tag.ReplacedProcedureStepSequence, 1)
                .add(refSOP(upsAttrs.getString(Tag.SOPClassUID), upsAttrs.getString(Tag.SOPInstanceUID)));
        return replacement;
    }

    private Date scheduledTime(String upsScheduledTime) {
        if (upsScheduledTime != null)
            try {
                return new SimpleDateFormat("yyyyMMddhhmmss").parse(upsScheduledTime);
            } catch (Exception e) {
                LOG.info("Can not parse upsScheduledTime[={}]: {}", upsScheduledTime, e.getMessage());
            }
        return new Date();
    }

    private static Attributes refSOP(String cuid, String iuid) {
        Attributes item = new Attributes(2);
        item.setString(Tag.ReferencedSOPClassUID, VR.UI, cuid);
        item.setString(Tag.ReferencedSOPInstanceUID, VR.UI, iuid);
        return item;
    }

    @Override
    public String toString() {
        String requestURI = request.getRequestURI();
        String queryString = request.getQueryString();
        return queryString == null ? requestURI : requestURI + '?' + queryString;
    }

    public void validate() {
        LOG.info("Process {} {} from {}@{}",
                request.getMethod(), toString(), request.getRemoteUser(), request.getRemoteHost());
        if (uriInfo.getPath().contains(UID.UPSFilteredGlobalSubscriptionInstance)
                && "POST".equals(request.getMethod())
                && !uriInfo.getPath().endsWith("/suspend")) {
            matchKeys = new QueryAttributes(uriInfo, null).getQueryKeys();
        }
    }

    private Response createUPS(String iuid, boolean template, Attributes attrs) {
        if (template && attrs.containsValue(Tag.ScheduledProcedureStepStartDateTime))
            return Response.status(Response.Status.BAD_REQUEST)
                    .header("Warning",
                            toWarning("UPS Template workitem creation shall not contain Scheduled Procedure Step Start DateTime"))
                    .build();

        UPSContext ctx = service.newUPSContext(HttpServletRequestInfo.valueOf(request), getArchiveAE());
        ctx.setUPSInstanceUID(iuid == null ? UIDUtils.createUID() : iuid);
        ctx.setAttributes(attrs);
        ctx.setTemplate(template);
        try {
            service.createUPS(ctx);
        } catch (DicomServiceException e) {
            return errResponse(UpsRS::createFailed, e);
        }
        return Response.created(locationOf(ctx)).build();
    }

    private Response updateUPS(String iuid, Attributes attrs) {
        UPSContext ctx = service.newUPSContext(HttpServletRequestInfo.valueOf(request), getArchiveAE());
        ctx.setUPSInstanceUID(iuid);
        ctx.setAttributes(attrs);
        try {
            service.updateUPS(ctx);
        } catch (DicomServiceException e) {
            return errResponse(UpsRS::updateFailed, e);
        }
        return Response.ok().build();
    }

    private Response changeUPSState(String iuid, String requester, Attributes attrs) {
        UPSContext ctx = service.newUPSContext(HttpServletRequestInfo.valueOf(request), getArchiveAE());
        ctx.setUPSInstanceUID(iuid);
        ctx.setRequesterAET(requester);
        ctx.setAttributes(attrs);
        try {
            service.changeUPSState(ctx);
        } catch (DicomServiceException e) {
            return errResponse(UpsRS::changeStateFailed, e);
        }
        Response.ResponseBuilder ok = Response.ok();
        switch (ctx.getStatus()) {
            case Status.UPSAlreadyInRequestedStateOfCanceled:
                ok.header("Warning", toWarning(
                        "The UPS is already in the requested state of CANCELED."));
                break;
            case Status.UPSAlreadyInRequestedStateOfCompleted:
                ok.header("Warning", toWarning(
                        "The UPS is already in the requested state of COMPLETED."));
                break;
        }
        return ok.build();
    }

    private Response requestUPSCancel(String iuid, String requester, Attributes attrs) {
        UPSContext ctx = service.newUPSContext(HttpServletRequestInfo.valueOf(request), getArchiveAE());
        ctx.setUPSInstanceUID(iuid);
        ctx.setRequesterAET(requester);
        ctx.setAttributes(attrs);
        try {
            service.requestUPSCancel(ctx);
        } catch (DicomServiceException e) {
            return errResponse(UpsRS::requestCancelFailed, e);
        }
        Response.ResponseBuilder accepted = Response.accepted();
        switch (ctx.getStatus()) {
            case Status.UPSAlreadyInRequestedStateOfCanceled:
                accepted.header("Warning", toWarning(
                        "The UPS is already in the requested state of CANCELED."));
                break;
        }
        return accepted.build();
    }

    private Response notAcceptable() {
        LOG.info("Response Status : Not Acceptable. Content Type in request : \n{}", headers.getMediaType());
        return Response.notAcceptable(
                Variant.mediaTypes(
                        MediaTypes.APPLICATION_DICOM_JSON_TYPE, MediaTypes.APPLICATION_DICOM_XML_TYPE)
                        .build())
                .build();
    }

    private Response errResponse(IntFunction<Response.Status> httpStatusOf, DicomServiceException e) {
        return Response.status(httpStatusOf.apply(e.getStatus()))
                .header("Warning", toWarning(e.getMessage()))
                .build();
    }

    private String toWarning(String message) {
        return "299 " + baseURL() + ": " + message;
    }

    private String baseURL() {
        StringBuffer sb = request.getRequestURL();
        sb.setLength(sb.lastIndexOf("/workitems") + 10);
        return sb.toString();
    }

    private static Response.Status createFailed(int status) {
        switch (status) {
            case Status.DuplicateSOPinstance:
                return Response.Status.CONFLICT;
            case Status.UPSNotScheduled:
            case Status.NoSuchAttribute:
            case Status.MissingAttribute:
            case Status.MissingAttributeValue:
            case Status.InvalidAttributeValue:
                return Response.Status.BAD_REQUEST;
        }
        return Response.Status.INTERNAL_SERVER_ERROR;
    }

    private static Response.Status updateFailed(int status) {
        switch (status) {
            case Status.UPSDoesNotExist:
                return Response.Status.NOT_FOUND;
            case Status.UPSNotYetInProgress:
            case Status.UPSTransactionUIDNotCorrect:
            case Status.UPSMayNoLongerBeUpdated:
                return Response.Status.CONFLICT;
            case Status.NoSuchAttribute:
            case Status.MissingAttribute:
            case Status.MissingAttributeValue:
            case Status.InvalidAttributeValue:
                return Response.Status.BAD_REQUEST;
        }
        return Response.Status.INTERNAL_SERVER_ERROR;
    }

    private static Response.Status changeStateFailed(int status) {
        switch (status) {
            case Status.UPSDoesNotExist:
                return Response.Status.NOT_FOUND;
            case Status.UPSNotYetInProgress:
            case Status.UPSTransactionUIDNotCorrect:
            case Status.UPSMayNoLongerBeUpdated:
            case Status.UPSStateMayNotChangedToScheduled:
            case Status.UPSAlreadyInProgress:
            case Status.UPSNotMetFinalStateRequirements:
                return Response.Status.CONFLICT;
            case Status.InvalidArgumentValue:
                return Response.Status.BAD_REQUEST;
        }
        return Response.Status.INTERNAL_SERVER_ERROR;
    }

    private static Response.Status requestCancelFailed(int status) {
        switch (status) {
            case Status.UPSDoesNotExist:
                return Response.Status.NOT_FOUND;
            case Status.UPSAlreadyCompleted:
            case Status.UPSPerformerCannotBeContacted:
            case Status.UPSPerformerChoosesNotToCancel:
                return Response.Status.CONFLICT;
        }
        return Response.Status.INTERNAL_SERVER_ERROR;
    }

    private static Response.Status subscriptionFailed(int status) {
        switch (status) {
            case Status.UPSDoesNotExist:
            case Status.UPSUnknownReceivingAET:
                return Response.Status.NOT_FOUND;
            case Status.UPSDoesNotSupportEventReports:
                return Response.Status.FORBIDDEN;
            case Status.InvalidArgumentValue:
                return Response.Status.BAD_REQUEST;
        }
        return Response.Status.INTERNAL_SERVER_ERROR;
    }

    private static Response.Status internalServerError(int status) {
        return Response.Status.INTERNAL_SERVER_ERROR;
    }

    private static Response.Status retrieveFailed(int status) {
        switch (status) {
            case Status.UPSDoesNotExist:
                return Response.Status.NOT_FOUND;
        }
        return Response.Status.INTERNAL_SERVER_ERROR;
    }

    private URI locationOf(UPSContext ctx) {
        return URI.create(
                request.getRequestURL().append('/').append(ctx.getUPSInstanceUID()).toString());
    }

    private URI websocketOf(UPSContext ctx) {
        StringBuffer sb = request.getRequestURL();
        sb.setLength(sb.indexOf("/rs/"));
        return URI.create("ws" + sb.append("/ws/subscribers/").append(ctx.getSubscriberAET()).substring(4));
    }

    private ArchiveAEExtension getArchiveAE() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        if (ae == null || !ae.isInstalled()) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        return ae.getAEExtensionNotNull(ArchiveAEExtension.class);
    }

    private ResponseMediaType getResponseMediaType () {
        return MediaTypeUtils.acceptableMediaTypesOf(headers, accept)
                .stream()
                .map(UpsRS::selectResponseMediaType)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new WebApplicationException(Response.Status.NOT_ACCEPTABLE));
    }

    private static ResponseMediaType selectResponseMediaType(MediaType acceptableMediaType) {
        return MediaTypes.APPLICATION_DICOM_JSON_TYPE.isCompatible(acceptableMediaType)
                ? ResponseMediaType.DICOM_JSON
                : MediaTypes.APPLICATION_DICOM_XML_TYPE.isCompatible(acceptableMediaType)
                ? ResponseMediaType.DICOM_XML
                : null;
    }

    private enum ResponseMediaType {
        DICOM_XML(MediaTypes.APPLICATION_DICOM_XML_TYPE, DicomXMLOutput::new){
            @Override
            Object entity(Attributes attrs) {
                return new DicomXMLOutput(attrs);
            }
        },
        DICOM_JSON(MediaTypes.APPLICATION_DICOM_JSON_TYPE, DicomJSONOutput::new){
            @Override
            Object entity(Attributes attrs) {
                return new DicomJSONOutput(attrs);
            }
        };

        final MediaType type;
        private final Function<Attributes, Object> toEntity;

        ResponseMediaType(MediaType type, Function<Attributes, Object> toEntity) {
            this.type = type;
            this.toEntity = toEntity;
        }

        Object entity(Attributes attrs) {
            return toEntity.apply(attrs);
        }
    }
}
