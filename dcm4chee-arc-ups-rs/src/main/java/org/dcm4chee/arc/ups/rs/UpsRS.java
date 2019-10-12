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
import org.dcm4che3.data.UID;
import org.dcm4che3.io.SAXReader;
import org.dcm4che3.json.JSONReader;
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
import org.xml.sax.SAXException;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonException;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Sep 2019
 */
@RequestScoped
@Path("aets/{AETitle}/rs")
@InvokeValidate(type = UpsRS.class)
public class UpsRS {

    private static final Logger LOG = LoggerFactory.getLogger(UpsRS.class);

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
    @Consumes("application/dicom+json")
    public Response createUPSJSON(@QueryParam("workitem") String iuid, InputStream in) {
        return createUPS(iuid, parseJSON(in));
    }

    @POST
    @Path("/workitems")
    @Consumes("application/dicom+xml")
    public Response createUPSXML(@QueryParam("workitem") String iuid, InputStream in) {
        return createUPS(iuid, parseXML(in));
    }

    @POST
    @Path("/workitems/{workitem}")
    @Consumes("application/dicom+json")
    public Response updateUPSJSON(@PathParam("workitem") String iuid, InputStream in) {
        return updateUPS(iuid, parseJSON(in));
    }

    @POST
    @Path("/workitems/{workitem}")
    @Consumes("application/dicom+xml")
    public Response updateUPSXML(@PathParam("workitem") String iuid, InputStream in) {
        return updateUPS(iuid, parseXML(in));
    }

    @PUT
    @Path("/workitems/{workitem}/state/{requester}")
    @Consumes("application/dicom+json")
    public Response changeUPSStateJSON(
            @PathParam("workitem") String iuid,
            @PathParam("requester") String requester,
            InputStream in) {
        return changeUPSState(iuid, requester, parseJSON(in));
    }

    @PUT
    @Path("/workitems/{workitem}/state/{requester}")
    @Consumes("application/dicom+xml")
    public Response changeUPSStateXML(
            @PathParam("workitem") String iuid,
            @PathParam("requester") String requester,
            InputStream in) {
        return changeUPSState(iuid, requester, parseXML(in));
    }

    @GET
    @NoCache
    @Path("/workitems/{workitem}")
    public Response retrieveUPS(@PathParam("workitem") String iuid) {
        ResponseMediaType responseMediaType = getResponseMediaType();
        UPSContext ctx = service.newUPSContext(HttpServletRequestInfo.valueOf(request), getArchiveAE());
        ctx.setUpsInstanceUID(iuid);
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
    @Consumes("application/dicom+json")
    public Response requestUPSCancelJSON(
            @PathParam("workitem") String iuid,
            @PathParam("requester") String requester,
            InputStream in) {
        return requestUPSCancel(iuid, requester, parseJSON(in));
    }

    @POST
    @Path("/workitems/{workitem}/cancelrequest/{requester}")
    @Consumes("application/dicom+xml")
    public Response requestUPSCancelXML(
            @PathParam("workitem") String iuid,
            @PathParam("requester") String requester,
            InputStream in) {
        return requestUPSCancel(iuid, requester, parseXML(in));
    }

    @POST
    @Path("/workitems/{workitem}/subscribers/{SubscriberAET}")
    public Response subscribe(
            @PathParam("workitem") String iuid,
            @PathParam("SubscriberAET") String subscriber) {
        UPSContext ctx = service.newUPSContext(HttpServletRequestInfo.valueOf(request), getArchiveAE());
        ctx.setUpsInstanceUID(iuid);
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
        ctx.setUpsInstanceUID(iuid);
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
        ctx.setUpsInstanceUID(iuid);
        ctx.setSubscriberAET(subscriber);
        try {
            service.suspendSubscription(ctx);
        } catch (DicomServiceException e) {
            return errResponse(UpsRS::internalServerError, e);
        }
        return Response.ok().build();
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
        if (uriInfo.getPath().indexOf(UID.UPSFilteredGlobalSubscriptionSOPInstance) >= 0
                && "POST".equals(request.getMethod())
                && !uriInfo.getPath().endsWith("/suspend")) {
            matchKeys = new QueryAttributes(uriInfo, null).getQueryKeys();
        }
    }

    private Response createUPS(String iuid, Attributes attrs) {
        UPSContext ctx = service.newUPSContext(HttpServletRequestInfo.valueOf(request), getArchiveAE());
        ctx.setUpsInstanceUID(iuid == null ? UIDUtils.createUID() : iuid);
        ctx.setAttributes(attrs);
        try {
            service.createUPS(ctx);
        } catch (DicomServiceException e) {
            return errResponse(UpsRS::createFailed, e);
        }
        return Response.created(locationOf(ctx)).build();
    }

    private Response updateUPS(String iuid, Attributes attrs) {
        UPSContext ctx = service.newUPSContext(HttpServletRequestInfo.valueOf(request), getArchiveAE());
        ctx.setUpsInstanceUID(iuid);
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
        ctx.setUpsInstanceUID(iuid);
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
        ctx.setUpsInstanceUID(iuid);
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
                request.getRequestURL().append('/').append(ctx.getUpsInstanceUID()).toString());
    }

    private URI websocketOf(UPSContext ctx) {
        StringBuffer sb = request.getRequestURL();
        sb.setLength(sb.indexOf("/rs/"));
        return URI.create("ws" + sb.append("/ws/subscribers/").append(ctx.getSubscriberAET()).substring(4));
    }

    private static Attributes parseJSON(InputStream in) {
        try {
            return new JSONReader(Json.createParser(new InputStreamReader(in, StandardCharsets.UTF_8)))
                    .readDataset(null);
        } catch (JsonException e) {
            throw new WebApplicationException(e,
                    Response.status(Response.Status.BAD_REQUEST).entity("Invalid JSON payload").build());
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    private static Attributes parseXML(InputStream in) {
        try {
            return SAXReader.parse(in);
        } catch (SAXException e) {
            throw new WebApplicationException(e,
                    Response.status(Response.Status.BAD_REQUEST).entity("Invalid XML payload").build());
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
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
