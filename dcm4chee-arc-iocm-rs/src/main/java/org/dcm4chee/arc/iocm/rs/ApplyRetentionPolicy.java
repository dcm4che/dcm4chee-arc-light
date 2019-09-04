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

import org.dcm4che3.audit.AuditMessages;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.data.Tag;
import org.dcm4che3.dict.archive.PrivateTag;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.RSOperation;
import org.dcm4chee.arc.conf.StudyRetentionPolicy;
import org.dcm4chee.arc.entity.ExpirationState;
import org.dcm4chee.arc.query.Query;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.query.util.OrderByTag;
import org.dcm4chee.arc.query.util.QueryAttributes;
import org.dcm4chee.arc.rs.client.RSForward;
import org.dcm4chee.arc.study.StudyMgtContext;
import org.dcm4chee.arc.study.StudyService;
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
import java.time.LocalDate;
import java.util.Collections;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jul 2018
 */
@Path("aets/{AETitle}/rs/expire")
@RequestScoped
public class ApplyRetentionPolicy {

    private static final Logger LOG = LoggerFactory.getLogger(ApplyRetentionPolicy.class);

    @Context
    private HttpServletRequest request;

    @Context
    private UriInfo uriInfo;

    @Inject
    private Device device;

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
        return request.getRequestURI() + '?' + request.getQueryString();
    }

    @POST
    @Path("/series")
    @Produces("application/json")
    public Response applyRetentionPolicy() {
        logRequest();
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        if (ae == null || !ae.isInstalled())
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);

        try {
            ArchiveAEExtension arcAE = ae.getAEExtensionNotNull(ArchiveAEExtension.class);
            int queryFetchSize = arcAE.getArchiveDeviceExtension().getQueryFetchSize();
            int count = 0;
            QueryContext ctx = queryContext(ae);
            try (Query query = queryService.createQuery(ctx)) {
                try {
                    query.executeQuery(queryFetchSize);
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
                                        aet,
                                        attrs);

                        String studyExpirationState = attrs.getString(PrivateTag.PrivateCreator, PrivateTag.StudyExpirationState);
                        String studyExpirationDate = attrs.getString(PrivateTag.PrivateCreator, PrivateTag.StudyExpirationDate);
                        String studyInstanceUID = attrs.getString(Tag.StudyInstanceUID);

                        if (retentionPolicy == null
                                || (ExpirationState.valueOf(studyExpirationState) == ExpirationState.FROZEN
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
                    return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
                }
            }
            rsForward.forward(RSOperation.ApplyRetentionPolicy, arcAE, null, request);
            return Response.ok("{\"count\":" + count + '}').build();
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private void logRequest() {
        LOG.info("Process {} {}?{} from {}@{}",
                request.getMethod(),
                request.getRequestURI(),
                request.getQueryString(),
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

    private QueryContext queryContext(ApplicationEntity ae) {
        QueryContext ctx = queryService.newQueryContextQIDO(
                request, "applyRetentionPolicy", ae, queryParam(ae));
        ctx.setQueryRetrieveLevel(QueryRetrieveLevel2.SERIES);
        QueryAttributes queryAttrs = new QueryAttributes(uriInfo, null);
        Attributes keys = queryAttrs.getQueryKeys();
        IDWithIssuer idWithIssuer = IDWithIssuer.pidOf(keys);
        if (idWithIssuer != null)
            ctx.setPatientIDs(idWithIssuer);
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
        StudyMgtContext ctx = studyService.createStudyMgtContextWEB(request, ae);
        ctx.setStudyInstanceUID(studyIUID);
        ctx.setSeriesInstanceUID(seriesIUID);
        ctx.setExpirationDate(policy.protectStudy() ? null : expirationDate);
        ctx.setEventActionCode(AuditMessages.EventActionCode.Update);
        ctx.setExpirationExporterID(policy.getExporterID());
        ctx.setFreezeExpirationDate(policy.isFreezeExpirationDate());
        studyService.updateExpirationDate(ctx);
    }
}
