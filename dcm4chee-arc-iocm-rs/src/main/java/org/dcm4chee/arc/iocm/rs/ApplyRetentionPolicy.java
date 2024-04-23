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

package org.dcm4chee.arc.iocm.rs;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.dcm4che3.audit.AuditMessages;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.data.Issuer;
import org.dcm4che3.data.Tag;
import org.dcm4che3.dict.archive.PrivateTag;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.WebApplication;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.RSOperation;
import org.dcm4chee.arc.conf.StudyRetentionPolicy;
import org.dcm4chee.arc.entity.ExpirationState;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.keycloak.KeycloakContext;
import org.dcm4chee.arc.query.Query;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.query.RunInTransaction;
import org.dcm4chee.arc.query.util.OrderByTag;
import org.dcm4chee.arc.query.util.QueryAttributes;
import org.dcm4chee.arc.rs.client.RSForward;
import org.dcm4chee.arc.study.StudyMgtContext;
import org.dcm4chee.arc.study.StudyService;
import org.dcm4chee.arc.validation.constraints.InvokeValidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jul 2018
 */
@Path("aets/{AETitle}/rs")
@RequestScoped
@InvokeValidate(type = ApplyRetentionPolicy.class)
public class ApplyRetentionPolicy {

    private static final Logger LOG = LoggerFactory.getLogger(ApplyRetentionPolicy.class);
    private static final String SUPER_USER_ROLE = "super-user-role";

    @Context
    private HttpServletRequest request;

    @Context
    private UriInfo uriInfo;

    @Inject
    private Device device;

    @Inject
    private RunInTransaction runInTx;

    @Inject
    private QueryService queryService;

    @Inject
    private StudyService studyService;

    @Inject
    private RSForward rsForward;

    @PathParam("AETitle")
    private String aet;

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
        LOG.info("Process {} {} from {}@{}",
                request.getMethod(),
                toString(),
                request.getRemoteUser(),
                request.getRemoteHost());
        new QueryAttributes(uriInfo, null);
    }

    @POST
    @Path("/series/expire")
    @Produces("application/json")
    public Response applyRetentionPolicy() {
        ArchiveAEExtension arcAE = getArchiveAE();
        if (arcAE == null)
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);

        validateAcceptedUserRoles(arcAE);
        ApplicationEntity ae = arcAE.getApplicationEntity();
        if (aet.equals(ae.getAETitle()))
            validateWebAppServiceClass();

        try {
            int count;
            QueryContext ctx = queryContext(ae);
            try (Query query = queryService.createQuery(ctx)) {
                int queryMaxNumberOfResults = arcAE.queryMaxNumberOfResults();
                if (queryMaxNumberOfResults > 0 && !ctx.containsUniqueKey()
                        && query.fetchCount() > queryMaxNumberOfResults)
                    return errResponse("Request entity too large. Query count exceeds configured Query Max Number of Results, narrow down search using query filters.",
                            Response.Status.REQUEST_ENTITY_TOO_LARGE);

                ExpireSeries es = new ExpireSeries(ae, query);
                runInTx.execute(es);
                count = es.getCount();
            }
            rsForward.forward(RSOperation.ApplyRetentionPolicy, arcAE, null, request);
            return Response.ok("{\"count\":" + count + '}').build();
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    class ExpireSeries implements Runnable {
        private int count;
        private final ApplicationEntity ae;
        private final Query query;
        private final ArchiveAEExtension arcAE;

        ExpireSeries(ApplicationEntity ae, Query query) {
            this.ae = ae;
            this.arcAE = ae.getAEExtensionNotNull(ArchiveAEExtension.class);
            this.query = query;
        }

        int getCount() {
            return count;
        }

        @Override
        public void run() {
            try {
                query.executeQuery(device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class).getQueryFetchSize());
                String prevStudyInstanceUID = null;
                LocalDate prevStudyExpirationDate = null;
                while (query.hasMoreMatches()) {
                    Attributes attrs = query.nextMatch();
                    if (attrs == null)
                        continue;

                    StudyRetentionPolicy retentionPolicy =
                            arcAE.findStudyRetentionPolicy(
                                    null,
                                    attrs.getString(PrivateTag.PrivateCreator, PrivateTag.SendingApplicationEntityTitleOfSeries),
                                    null,
                                    aet,
                                    attrs);

                    String studyExpirationState = attrs.getString(PrivateTag.PrivateCreator, PrivateTag.StudyExpirationState);
                    String studyExpirationDate = attrs.getString(PrivateTag.PrivateCreator, PrivateTag.StudyExpirationDate);
                    String studyInstanceUID = attrs.getString(Tag.StudyInstanceUID);

                    if (retentionPolicy == null
                            || ((studyExpirationState != null
                                    && ExpirationState.valueOf(studyExpirationState) == ExpirationState.FROZEN)
                                && (studyExpirationDate == null || !retentionPolicy.protectStudy()))) {
                        LOG.info("Skip applying {} to Study[UID={}, ExpirationDate={}, ExpirationState={}]",
                                retentionPolicy, studyInstanceUID, studyExpirationDate, studyExpirationState);
                        continue;
                    }

                    LocalDate expirationDate = retentionPolicy.expirationDate(attrs);
                    if (!studyInstanceUID.equals(prevStudyInstanceUID)) {
                        prevStudyInstanceUID = studyInstanceUID;
                        prevStudyExpirationDate = expirationDate;
                        updateExpirationDate(studyInstanceUID, null, expirationDate, ae,
                                retentionPolicy);
                        count++;
                    } else if (retentionPolicy.isFreezeExpirationDate()
                            || prevStudyExpirationDate.compareTo(expirationDate) < 0) {
                        prevStudyExpirationDate = expirationDate;
                        updateExpirationDate(studyInstanceUID, null, expirationDate, ae,
                                retentionPolicy);
                    }

                    if (retentionPolicy.isExpireSeriesIndividually() && !retentionPolicy.isFreezeExpirationDate())
                        updateExpirationDate(studyInstanceUID, attrs.getString(Tag.SeriesInstanceUID),
                                expirationDate, ae, retentionPolicy);
                }
            } catch (Exception e) {
                throw new WebApplicationException(e);
            }
        }
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

    private QueryContext queryContext(ApplicationEntity ae) {
        QueryContext ctx = queryService.newQueryContextQIDO(
                HttpServletRequestInfo.valueOf(request), "applyRetentionPolicy", aet, ae, queryParam(ae));
        ctx.setQueryRetrieveLevel(QueryRetrieveLevel2.SERIES);
        QueryAttributes queryAttrs = new QueryAttributes(uriInfo, null);
        Attributes keys = queryAttrs.getQueryKeys();
        IDWithIssuer idWithIssuer = IDWithIssuer.pidOf(keys);
        if (idWithIssuer != null && !idWithIssuer.getID().equals("*"))
            ctx.setPatientIDs(idWithIssuer);
        else if (ctx.getArchiveAEExtension().filterByIssuerOfPatientID())
            ctx.setIssuerOfPatientID(Issuer.fromIssuerOfPatientID(keys));
        ctx.setQueryKeys(keys);
        ctx.setOrderByTags(Collections.singletonList(OrderByTag.asc(Tag.StudyInstanceUID)));
        ctx.setReturnPrivate(true);
        return ctx;
    }

    private org.dcm4chee.arc.query.util.QueryParam queryParam(ApplicationEntity ae) {
        org.dcm4chee.arc.query.util.QueryParam queryParam = new org.dcm4chee.arc.query.util.QueryParam(ae);
        queryParam.setCombinedDatetimeMatching(true);
        queryParam.setFuzzySemanticMatching(Boolean.parseBoolean(fuzzymatching));
        queryParam.setExpirationState(ExpirationState.UPDATEABLE, ExpirationState.FROZEN);
        return queryParam;
    }

    private void updateExpirationDate(
            String studyIUID, String seriesIUID, LocalDate expirationDate, ApplicationEntity ae,
            StudyRetentionPolicy policy) throws Exception {
        LOG.info("Applying {} with ExpirationDate[={}] to Study[UID={}], Series[UID={}]",
                policy, expirationDate, studyIUID, seriesIUID);
        StudyMgtContext ctx = studyService.createStudyMgtContextWEB(HttpServletRequestInfo.valueOf(request), ae);
        ctx.setStudyInstanceUID(studyIUID);
        ctx.setSeriesInstanceUID(seriesIUID);
        ctx.setExpirationDate(policy.protectStudy() ? null : expirationDate);
        ctx.setEventActionCode(AuditMessages.EventActionCode.Update);
        ctx.setExpirationExporterID(policy.getExporterID());
        ctx.setFreezeExpirationDate(policy.isFreezeExpirationDate());
        studyService.updateExpirationDate(ctx);
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
        return ae == null || !ae.isInstalled() ? null : ae.getAEExtensionNotNull(ArchiveAEExtension.class);
    }
}
