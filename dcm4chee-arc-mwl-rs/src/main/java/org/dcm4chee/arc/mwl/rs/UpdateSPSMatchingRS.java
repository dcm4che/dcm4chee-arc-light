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
 * Portions created by the Initial Developer are Copyright (C) 2015-2020
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

package org.dcm4chee.arc.mwl.rs;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.WebApplication;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.SPSStatus;
import org.dcm4chee.arc.keycloak.KeycloakContext;
import org.dcm4chee.arc.procedure.ProcedureService;
import org.dcm4chee.arc.query.util.QueryAttributes;
import org.dcm4chee.arc.validation.constraints.InvokeValidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.stream.Stream;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Feb 2020
 */
@RequestScoped
@Path("aets/{AETitle}/rs")
@InvokeValidate(type = UpdateSPSMatchingRS.class)
public class UpdateSPSMatchingRS {
    private static final Logger LOG = LoggerFactory.getLogger(UpdateSPSMatchingRS.class);
    private static final String SUPER_USER_ROLE = "super-user-role";

    @PathParam("AETitle")
    private String aet;

    @Inject
    private Device device;

    @Inject
    private ProcedureService procedureService;

    @Context
    private HttpServletRequest request;

    @Context
    private UriInfo uriInfo;

    @QueryParam("fuzzymatching")
    @Pattern(regexp = "true|false")
    private String fuzzymatching;

    @Override
    public String toString() {
        String requestURI = request.getRequestURI();
        String queryString = request.getQueryString();
        return queryString == null ? requestURI : requestURI + '?' + queryString;
    }

    public void validate() {
        logRequest();
        new QueryAttributes(uriInfo, null);
    }

    @POST
    @Path("/mwlitems/status/{status}")
    public Response updateSPSStatus(
            @PathParam("status")
            @Pattern(regexp = "SCHEDULED|ARRIVED|READY|STARTED|DEPARTED|CANCELED|DISCONTINUED|COMPLETED")
            String spsStatus) {
        ArchiveAEExtension arcAE = getArchiveAE();
        if (arcAE == null)
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);

        validateAcceptedUserRoles(arcAE);
        ApplicationEntity ae = arcAE.getApplicationEntity();
        if (aet.equals(ae.getAETitle()))
            validateWebAppServiceClass();

        int updated = 0;
        int count;
        int mwlFetchSize = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class)
                                    .getMWLFetchSize();

        SPSStatus targetSPSStatus = SPSStatus.valueOf(spsStatus);
        try {
            do {
                count = procedureService.updateMatchingSPS(
                                            targetSPSStatus,
                                            queryKeys(targetSPSStatus),
                                            queryParam(ae),
                                            mwlFetchSize);
                updated += count;
            } while (count >= mwlFetchSize);
            if (updated > 0)
                LOG.info("Updated {} MWL Items with SPS Status {}", updated, spsStatus);

            return Response.ok("{\"count\":" + updated + '}').build();
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private Attributes queryKeys(SPSStatus targetSPSStatus) {
        QueryAttributes queryAttrs = new QueryAttributes(uriInfo, null);
        Attributes sps = queryAttrs.getQueryKeys().getNestedDataset(Tag.ScheduledProcedureStepSequence);
        if (sps == null)
            queryKeysNoTargetSPSStatus(targetSPSStatus, new Attributes(1), queryAttrs);
        else if (sps.getString(Tag.ScheduledProcedureStepStatus) == null) {
            queryAttrs.getQueryKeys().remove(Tag.ScheduledProcedureStepSequence);
            queryKeysNoTargetSPSStatus(targetSPSStatus, sps, queryAttrs);
        }
        return queryAttrs.getQueryKeys();
    }

    private void queryKeysNoTargetSPSStatus(SPSStatus targetSPSStatus, Attributes sps, QueryAttributes queryAttrs) {
        sps.setString(Tag.ScheduledProcedureStepStatus, VR.CS,
                Stream.of(SPSStatus.values())
                        .filter(spsStatus -> spsStatus != targetSPSStatus)
                        .map(Enum::name)
                        .toArray(String[]::new));
        queryAttrs.getQueryKeys().newSequence(Tag.ScheduledProcedureStepSequence, 1).add(sps);
    }

    private org.dcm4chee.arc.query.util.QueryParam queryParam(ApplicationEntity ae) {
        org.dcm4chee.arc.query.util.QueryParam queryParam = new org.dcm4chee.arc.query.util.QueryParam(ae);
        queryParam.setCombinedDatetimeMatching(true);
        queryParam.setFuzzySemanticMatching(Boolean.parseBoolean(fuzzymatching));
        queryParam.setCalledAET(aet);
        return queryParam;
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

    private ArchiveAEExtension getArchiveAE() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        return ae == null || !ae.isInstalled() ? null : ae.getAEExtension(ArchiveAEExtension.class);
    }
}
