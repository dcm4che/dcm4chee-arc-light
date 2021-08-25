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
 * Portions created by the Initial Developer are Copyright (C) 2019
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

import org.dcm4che3.audit.AuditMessages;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.entity.ExpirationState;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.query.Query;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.query.RunInTransaction;
import org.dcm4chee.arc.query.util.QueryAttributes;
import org.dcm4chee.arc.study.StudyMgtContext;
import org.dcm4chee.arc.study.StudyMissingException;
import org.dcm4chee.arc.study.StudyService;
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

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Mar 2019
 */
@RequestScoped
@Path("aets/{AETitle}/rs")
@InvokeValidate(type = UpdateStudyAccessMatchingRS.class)
public class UpdateStudyAccessMatchingRS {
    private static final Logger LOG = LoggerFactory.getLogger(UpdateStudyAccessMatchingRS.class);

    @PathParam("AETitle")
    private String aet;

    @Inject
    private Device device;

    @Inject
    private StudyService studyService;

    @Inject
    private QueryService queryService;

    @Inject
    private RunInTransaction runInTx;

    @Context
    private HttpServletRequest request;

    @Context
    private UriInfo uriInfo;

    @QueryParam("fuzzymatching")
    @Pattern(regexp = "true|false")
    private String fuzzymatching;

    @QueryParam("incomplete")
    @Pattern(regexp = "true|false")
    private String incomplete;

    @QueryParam("retrievefailed")
    @Pattern(regexp = "true|false")
    private String retrievefailed;

    @QueryParam("storageVerificationFailed")
    @Pattern(regexp = "true|false")
    private String storageVerificationFailed;

    @QueryParam("metadataUpdateFailed")
    @Pattern(regexp = "true|false")
    private String metadataUpdateFailed;

    @QueryParam("compressionfailed")
    @Pattern(regexp = "true|false")
    private String compressionfailed;

    @QueryParam("ExternalRetrieveAET")
    private String externalRetrieveAET;

    @QueryParam("ExternalRetrieveAET!")
    private String externalRetrieveAETNot;

    @QueryParam("patientVerificationStatus")
    @Pattern(regexp = "UNVERIFIED|VERIFIED|NOT_FOUND|VERIFICATION_FAILED")
    private String patientVerificationStatus;

    @QueryParam("ExpirationDate")
    private String expirationDate;

    @QueryParam("storageID")
    private String storageID;

    @QueryParam("storageClustered")
    @Pattern(regexp = "true|false")
    private String storageClustered;

    @QueryParam("storageExported")
    @Pattern(regexp = "true|false")
    private String storageExported;

    @QueryParam("allOfModalitiesInStudy")
    @Pattern(regexp = "true|false")
    private String allOfModalitiesInStudy;

    @QueryParam("StudySizeInKB")
    @Pattern(regexp = "\\d{1,9}(-\\d{0,9})?|-\\d{1,9}")
    private String studySizeInKB;

    @QueryParam("ExpirationState")
    @Pattern(regexp = "UPDATEABLE|FROZEN|REJECTED|EXPORT_SCHEDULED|FAILED_TO_EXPORT|FAILED_TO_REJECT")
    private String expirationState;

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
    @Path("/studies/access/{accessControlID}")
    public Response updateStudyAccessControlID(
            @PathParam("accessControlID") String accessControlID) {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        if (ae == null || !ae.isInstalled())
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);

        try {
            QueryContext qCtx = queryContext(ae);
            int count;
            try (Query query = queryService.createQuery(qCtx)) {
                int queryMaxNumberOfResults = qCtx.getArchiveAEExtension().queryMaxNumberOfResults();
                if (queryMaxNumberOfResults > 0 && !qCtx.containsUniqueKey()
                        && query.fetchCount() > queryMaxNumberOfResults)
                    return errResponse("Request entity too large", Response.Status.BAD_REQUEST);

                UpdateStudyAccess updateStudyAccess = new UpdateStudyAccess(ae, query, accessControlID);
                runInTx.execute(updateStudyAccess);
                count = updateStudyAccess.getCount();
            }
            return Response.ok("{\"count\":" + count + '}').build();
        } catch (IllegalStateException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (WebApplicationException e) {
            return errResponse(e.getMessage(), Response.Status.fromStatusCode(e.getResponse().getStatus()));
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    class UpdateStudyAccess implements Runnable {
        private int count;
        private final ApplicationEntity ae;
        private final Query query;
        private final String accessControlID;

        UpdateStudyAccess(ApplicationEntity ae, Query query, String accessControlID) {
            this.ae = ae;
            this.query = query;
            this.accessControlID = accessControlID;
        }

        int getCount() {
            return count;
        }

        @Override
        public void run() {
            try {
                query.executeQuery(device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class).getQueryFetchSize());
                while (query.hasMoreMatches()) {
                    Attributes match = query.nextMatch();
                    if (match == null)
                        continue;

                    StudyMgtContext ctx = studyService.createStudyMgtContextWEB(
                            HttpServletRequestInfo.valueOf(request), ae);
                    ctx.setStudyInstanceUID(match.getString(Tag.StudyInstanceUID));
                    ctx.setAccessControlID("null".equals(accessControlID) ? "*" :  accessControlID);
                    ctx.setAttributes(match);
                    ctx.setEventActionCode(AuditMessages.EventActionCode.Update);
                    studyService.updateAccessControlID(ctx);
                    count++;
                }
            } catch (StudyMissingException e) {
                throw new WebApplicationException(e.getMessage(), Response.Status.NOT_FOUND);
            } catch (DicomServiceException e) {
                throw new WebApplicationException(e.getMessage(), Response.Status.INTERNAL_SERVER_ERROR);
            } catch (Exception e) {
                throw new WebApplicationException(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
            }
        }
    }

    private QueryContext queryContext(ApplicationEntity ae) {
        QueryContext ctx = queryService.newQueryContextQIDO(
                HttpServletRequestInfo.valueOf(request), "matchingStudyUpdateAccessControl", aet, ae, queryParam(ae));
        ctx.setQueryRetrieveLevel(QueryRetrieveLevel2.STUDY);
        QueryAttributes queryAttrs = new QueryAttributes(uriInfo, null);
        Attributes keys = queryAttrs.getQueryKeys();
        IDWithIssuer idWithIssuer = IDWithIssuer.pidOf(keys);
        if (idWithIssuer != null)
            ctx.setPatientIDs(idWithIssuer);
        ctx.setQueryKeys(keys);
        Attributes returnKeys = new Attributes(3);
        returnKeys.setNull(Tag.StudyInstanceUID, VR.UI);
        ctx.setReturnKeys(returnKeys);
        return ctx;
    }

    private org.dcm4chee.arc.query.util.QueryParam queryParam(ApplicationEntity ae) {
        org.dcm4chee.arc.query.util.QueryParam queryParam = new org.dcm4chee.arc.query.util.QueryParam(ae);
        queryParam.setCombinedDatetimeMatching(true);
        queryParam.setFuzzySemanticMatching(Boolean.parseBoolean(fuzzymatching));
        queryParam.setAllOfModalitiesInStudy(Boolean.parseBoolean(allOfModalitiesInStudy));
        queryParam.setIncomplete(Boolean.parseBoolean(incomplete));
        queryParam.setRetrieveFailed(Boolean.parseBoolean(retrievefailed));
        queryParam.setStorageVerificationFailed(Boolean.parseBoolean(storageVerificationFailed));
        queryParam.setMetadataUpdateFailed(Boolean.parseBoolean(metadataUpdateFailed));
        queryParam.setCompressionFailed(Boolean.parseBoolean(compressionfailed));
        queryParam.setExternalRetrieveAET(externalRetrieveAET);
        queryParam.setExternalRetrieveAETNot(externalRetrieveAETNot);
        queryParam.setExpirationDate(expirationDate);
        queryParam.setStudySizeRange(studySizeInKB);
        if (patientVerificationStatus != null)
            queryParam.setPatientVerificationStatus(Patient.VerificationStatus.valueOf(patientVerificationStatus));
        if (storageID != null)
            queryParam.setStudyStorageIDs(device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class)
                    .getStudyStorageIDs(storageID, parseBoolean(storageClustered), parseBoolean(storageExported)));
        if (expirationState != null)
            queryParam.setExpirationState(ExpirationState.valueOf(expirationState));
        return queryParam;
    }

    private static Boolean parseBoolean(String s) {
        return s != null ? Boolean.valueOf(s) : null;
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

}
