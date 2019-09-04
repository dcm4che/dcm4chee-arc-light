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

package org.dcm4chee.arc.stgcmt.rs;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.IApplicationEntityCache;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.entity.ExpirationState;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.qmgt.QueueSizeLimitExceededException;
import org.dcm4chee.arc.query.Query;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.query.util.QueryAttributes;
import org.dcm4chee.arc.stgcmt.StgCmtSCU;
import org.dcm4chee.arc.validation.constraints.InvokeValidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * @since Sep 2019
 */
@InvokeValidate(type = StgCmtSCUMatching.class)
class StgCmtSCUMatching {
    private static final Logger LOG = LoggerFactory.getLogger(StgCmtSCUMatching.class);

    @Context
    private UriInfo uriInfo;

    @Inject
    private Device device;

    @Inject
    private StgCmtSCU stgCmtSCU;

    @Inject
    private IApplicationEntityCache aeCache;

    @Inject
    private QueryService queryService;

    @Context
    private HttpServletRequest request;

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

    @QueryParam("batchID")
    private String batchID;

    public void validate() {
        logRequest();
        new QueryAttributes(uriInfo, null);
    }

    Response storageCommitMatching(String aet, String stgcmtscp, String method, QueryRetrieveLevel2 qrlevel,
                                           String studyUID, String seriesUID) {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        if (ae == null || !ae.isInstalled())
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);

        try {
            aeCache.findApplicationEntity(stgcmtscp);
            ArchiveDeviceExtension arcDev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
            QueryContext ctx = queryContext(method, qrlevel, studyUID, seriesUID, ae);
            String warning = null;
            int count = 0;
            Response.Status status = Response.Status.ACCEPTED;
            try (Query query = queryService.createQuery(ctx)) {
                try {
                    query.executeQuery(arcDev.getQueryFetchSize());
                    while (query.hasMoreMatches()) {
                        Attributes match = query.nextMatch();
                        if (match == null)
                            continue;

                        stgCmtSCU.scheduleStorageCommit(aet, stgcmtscp, match, batchID, qrlevel);
                        count++;
                    }
                } catch (QueueSizeLimitExceededException e) {
                    status = Response.Status.SERVICE_UNAVAILABLE;
                    warning = e.getMessage();
                } catch (Exception e) {
                    warning = e.getMessage();
                    status = Response.Status.INTERNAL_SERVER_ERROR;
                }
            }
            Response.ResponseBuilder builder = Response.status(status);
            if (warning != null) {
                LOG.warn("Response {} caused by {}", status, warning);
                builder.header("Warning", warning);
            }
            return builder.entity("{\"count\":" + count + '}').build();
        } catch (IllegalStateException | ConfigurationException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private QueryContext queryContext(String method, QueryRetrieveLevel2 qrlevel, String studyUID,
                                         String seriesUID, ApplicationEntity ae) {
        QueryContext ctx = queryService.newQueryContextQIDO(request, method, ae, queryParam(ae));
        ctx.setQueryRetrieveLevel(qrlevel);
        QueryAttributes queryAttrs = new QueryAttributes(uriInfo, null);
        Attributes keys = queryAttrs.getQueryKeys();
        IDWithIssuer idWithIssuer = IDWithIssuer.pidOf(keys);
        if (idWithIssuer != null)
            ctx.setPatientIDs(idWithIssuer);
        if (studyUID != null)
            keys.setString(Tag.StudyInstanceUID, VR.UI, studyUID);
        if (seriesUID != null)
            keys.setString(Tag.SeriesInstanceUID, VR.UI, seriesUID);
        ctx.setQueryKeys(keys);
        Attributes returnKeys = new Attributes(3);
        returnKeys.setNull(Tag.StudyInstanceUID, VR.UI);
        switch (qrlevel) {
            case IMAGE:
                returnKeys.setNull(Tag.SOPInstanceUID, VR.UI);
            case SERIES:
                returnKeys.setNull(Tag.SeriesInstanceUID, VR.UI);
        }
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
}
