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

package org.dcm4chee.arc.impax.rs;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.stream.JsonGenerator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import jakarta.xml.ws.WebServiceException;
import org.dcm4che3.data.*;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.WebApplication;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.impax.report.ImpaxReportConverter;
import org.dcm4chee.arc.impax.report.ReportServiceProvider;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.keycloak.KeycloakContext;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreService;
import org.dcm4chee.arc.store.StoreSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jul 2018
 */
@RequestScoped
@Path("aets/{AETitle}/rs")
public class ImportImpaxReportRS {
    private static final Logger LOG = LoggerFactory.getLogger(ImportImpaxReportRS.class);
    private static final String SUPER_USER_ROLE = "super-user-role";

    @Context
    private HttpServletRequest request;

    @Inject
    private Device device;

    @Inject
    private ReportServiceProvider reportService;

    @Inject
    private QueryService queryService;

    @Inject
    private StoreService storeService;

    @PathParam("AETitle")
    private String aet;


    @POST
    @Path("/studies/{studyUID}/impax/reports")
    @Produces("application/dicom+json")
    public Response importReportsOfStudy(@PathParam("studyUID") String studyUID) {
        logRequest();
        ArchiveAEExtension arcAE = getArchiveAE();
        if (arcAE == null)
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);

        validateAcceptedUserRoles(arcAE);
        ApplicationEntity ae = arcAE.getApplicationEntity();
        if (aet.equals(ae.getAETitle()))
            validateWebAppServiceClass();

        Attributes studyAttrs = queryService.getStudyAttributes(studyUID);
        if (studyAttrs == null)
            return errResponse("No such Study: " + studyUID, Response.Status.NOT_FOUND);

        List<String> xmlReports = queryReports(studyUID);
        try {
            Map<String, String> props = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class)
                    .getImpaxReportProperties();
            ImpaxReportConverter converter = new ImpaxReportConverter(props, studyAttrs);
            List<Attributes> srReports = converter.convert(xmlReports);

            if (srReports.isEmpty())
                return errResponse("SR Reports not found for the study", Response.Status.CONFLICT);

            Attributes response = new Attributes();
            response.setString(Tag.RetrieveURL, VR.UR, studyRetrieveURL().toString());
            try (StoreSession session = storeService.newStoreSession(
                    HttpServletRequestInfo.valueOf(request), ae, aet, props.get("SourceAET"))) {
                session.setImpaxReportEndpoint(converter.getEndpoint());
                srReports.forEach(sr -> {
                    StoreContext storeCtx = storeService.newStoreContext(session);
                    storeCtx.setImpaxReportPatientMismatch(converter.patientMismatchOf(sr));
                    storeCtx.setReceiveTransferSyntax(UID.ExplicitVRLittleEndian);
                    storeReport(storeCtx, sr, response);
                });
            }
            return buildResponse(xmlReports, response, ae);
        } catch (IllegalStateException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private void logRequest() {
        LOG.info("Process {} {} from {}@{}",
                request.getMethod(),
                request.getRequestURI(),
                request.getRemoteUser(),
                request.getRemoteHost());
    }

    private ArchiveAEExtension getArchiveAE() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        return ae == null || !ae.isInstalled() ? null : ae.getAEExtension(ArchiveAEExtension.class);
    }

    private List<String> queryReports(String studyUID) {
        try {
            return reportService.queryReportByStudyUid(studyUID);
        } catch (WebServiceException e) {
            throw new WebApplicationException(errResponse(e.getMessage(), Response.Status.BAD_GATEWAY));
        } catch (Exception e) {
            throw new WebApplicationException(
                    errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR));
        }
    }

    private void storeReport(StoreContext ctx, Attributes attrs, Attributes response) {
        try {
            storeService.store(ctx, attrs);
            response.ensureSequence(Tag.ReferencedSOPSequence, 1)
                    .add(mkSOPRefWithRetrieveURL(ctx));
        } catch (IOException e) {
            ctx.setAttributes(attrs);
            LOG.info("{}: Failed to store {}", ctx.getStoreSession(), UID.nameOf(ctx.getSopClassUID()), e);
            response.setString(Tag.ErrorComment, VR.LO, e.getMessage());
            response.ensureSequence(Tag.ReferencedSOPSequence, 1)
                    .add(mkSOPRefWithFailureReason(ctx, e));
        } catch (Exception e) {
            LOG.warn("Exception caught while storing the SR report. {} \n", ctx.getStoreSession(), e);
        }
    }

    private Response buildResponse(List<String> xmlReports, Attributes response, ApplicationEntity ae) {
        Response.Status status = !response.contains(Tag.ReferencedSOPSequence)
                ? Response.Status.CONFLICT
                : !xmlReports.isEmpty() && !response.contains(Tag.FailedSOPSequence)
                    ? Response.Status.OK
                    : Response.Status.ACCEPTED;
        return Response.status(status)
                .entity((StreamingOutput) out -> {
                    JsonGenerator gen = Json.createGenerator(out);
                    ae.getAEExtensionNotNull(ArchiveAEExtension.class)
                            .encodeAsJSONNumber(new JSONWriter(gen))
                            .write(response);
                    gen.flush();
                }).build();
    }

    private StringBuffer studyRetrieveURL() {
        StringBuffer retrieveURL = request.getRequestURL();
        retrieveURL.setLength(retrieveURL.length() - 14); // remove /impax/reports
        return retrieveURL;
    }

    private Attributes mkSOPRefWithRetrieveURL(StoreContext ctx) {
        Attributes attrs = mkSOPRef(ctx, 3);
        attrs.setString(Tag.RetrieveURL, VR.UR, retrieveURL(ctx));
        return attrs;
    }

    private String retrieveURL(StoreContext ctx) {
        StringBuffer retrieveURL = studyRetrieveURL();
        retrieveURL.append("/series/").append(ctx.getSeriesInstanceUID());
        retrieveURL.append("/instances/").append(ctx.getSopInstanceUID());
        return retrieveURL.toString();
    }

    private Attributes mkSOPRefWithFailureReason(StoreContext ctx, Exception e) {
        Attributes attrs = mkSOPRef(ctx, 3);
        int status = e instanceof DicomServiceException
                ? ((DicomServiceException) e).getStatus()
                : Status.ProcessingFailure;
        attrs.setInt(Tag.FailureReason, VR.US, status);
        return attrs;
    }

    private Attributes mkSOPRef(StoreContext ctx, int size) {
        Attributes attrs = new Attributes(size);
        attrs.setString(Tag.ReferencedSOPClassUID, VR.UI, ctx.getSopClassUID());
        attrs.setString(Tag.ReferencedSOPInstanceUID, VR.UI, ctx.getSopInstanceUID());
        return attrs;
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
}
