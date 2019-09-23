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

    private ArchiveAEExtension arcAE;
    private ResponseMediaType responseMediaType;

    @POST
    @Path("/workitems")
    @Consumes("application/dicom+json")
    public Response createJSONWorkitem(
            @QueryParam("workitem")
            @Pattern(regexp = "^([0-2])((\\.0)|(\\.[1-9][0-9]*))*")
            String iuid,
            InputStream in) {
        return createWorkitem(iuid, parseJSON(in));
    }

    @POST
    @Path("/workitems")
    @Consumes("application/dicom+xml")
    public Response createXMLWorkitem(
            @QueryParam("workitem")
            @Pattern(regexp = "^([0-2])((\\.0)|(\\.[1-9][0-9]*))*")
            String iuid,
            InputStream in) {
        return createWorkitem(iuid, parseXML(in));
    }

    @POST
    @Path("/workitems/{workitem}")
    @Consumes("application/dicom+json")
    public Response updateJSONWorkitem(
            @PathParam("workitem")
            String iuid,
            InputStream in) {
        return updateWorkitem(iuid, parseJSON(in));
    }

    @POST
    @Path("/workitems/{workitem}")
    @Consumes("application/dicom+xml")
    public Response updateXMLWorkitem(
            @PathParam("workitem")
            String iuid,
            InputStream in) {
        return updateWorkitem(iuid, parseXML(in));
    }

    @GET
    @NoCache
    @Path("/workitems/{workitem}")
    public Response retrieveWorkitem(@PathParam("workitem") String iuid) {
        ResponseMediaType responseMediaType = getResponseMediaType();
        UPSContext ctx = service.newUPSContext(HttpServletRequestInfo.valueOf(request), getArchiveAE());
        ctx.setSopInstanceUID(iuid);
        try {
            service.findWorkitem(ctx);
        } catch (DicomServiceException e) {
            return errResponse(UpsRS::retrieveFailed, e);
        }
        return Response.ok(responseMediaType.entity(ctx.getAttributes()), responseMediaType.type).build();
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
    }

    private Response createWorkitem(String iuid, Attributes workitem) {
        UPSContext ctx = service.newUPSContext(HttpServletRequestInfo.valueOf(request), getArchiveAE());
        ctx.setSopInstanceUID(iuid == null ? UIDUtils.createUID() : iuid);
        ctx.setAttributes(workitem);
        try {
            service.createWorkitem(ctx);
        } catch (DicomServiceException e) {
            return errResponse(UpsRS::createFailed, e);
        }
        return Response.created(locationOf(ctx)).build();
    }

    private Response updateWorkitem(String iuid, Attributes workitem) {
        UPSContext ctx = service.newUPSContext(HttpServletRequestInfo.valueOf(request), getArchiveAE());
        ctx.setSopInstanceUID(iuid);
        ctx.setAttributes(workitem);
        try {
            service.updateWorkitem(ctx);
        } catch (DicomServiceException e) {
            return errResponse(UpsRS::updateFailed, e);
        }
        return Response.ok().build();
    }

    private Response errResponse(IntFunction<Response.Status> httpStatusOf, DicomServiceException e) {
        return Response.status(httpStatusOf.apply(e.getStatus()))
                .header("Warning", toWarning(e))
                .build();
    }

    private String toWarning(DicomServiceException e) {
        return Integer.toHexString(e.getStatus()) + " " + baseURL() + ": " + e.getMessage();
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
            case Status.UPSStateNotScheduled:
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
            case Status.NoSuchUPSInstance:
                return Response.Status.NOT_FOUND;
            case Status.UPSStateNotInProgress:
                return Response.Status.CONFLICT;
            case Status.TransactionUIDNotCorrect:
            case Status.UPSMayNoLongerBeUpdated:
            case Status.NoSuchAttribute:
            case Status.MissingAttribute:
            case Status.MissingAttributeValue:
            case Status.InvalidAttributeValue:
                return Response.Status.BAD_REQUEST;
        }
        return Response.Status.INTERNAL_SERVER_ERROR;
    }

    private static Response.Status retrieveFailed(int status) {
        switch (status) {
            case Status.NoSuchUPSInstance:
                return Response.Status.NOT_FOUND;
        }
        return Response.Status.INTERNAL_SERVER_ERROR;
    }

    private URI locationOf(UPSContext ctx) {
        return URI.create(
                request.getRequestURL().append('/').append(ctx.getSopInstanceUID()).toString());
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
        if (accept != null) {
            for (String mediaType : accept) {
                headers.getRequestHeaders().add("Accept", mediaType);
            }
        }
        return headers.getAcceptableMediaTypes().stream()
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
