/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 *  The contents of this file are subject to the Mozilla Public License Version
 *  1.1 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  http://www.mozilla.org/MPL/
 *
 *  Software distributed under the License is distributed on an "AS IS" basis,
 *  WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 *  for the specific language governing rights and limitations under the
 *  License.
 *
 *  The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 *  Java(TM), hosted at https://github.com/dcm4che.
 *
 *  The Initial Developer of the Original Code is
 *  J4Care.
 *  Portions created by the Initial Developer are Copyright (C) 2015-2017
 *  the Initial Developer. All Rights Reserved.
 *
 *  Contributor(s):
 *  See @authors listed below
 *
 *  Alternatively, the contents of this file may be used under the terms of
 *  either the GNU General Public License Version 2 or later (the "GPL"), or
 *  the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 *  in which case the provisions of the GPL or the LGPL are applicable instead
 *  of those above. If you wish to allow use of your version of this file only
 *  under the terms of either the GPL or the LGPL, and not to allow others to
 *  use your version of this file under the terms of the MPL, indicate your
 *  decision by deleting the provisions above and replace them with the notice
 *  and other provisions required by the GPL or the LGPL. If you do not delete
 *  the provisions above, a recipient may use your version of this file under
 *  the terms of any one of the MPL, the GPL or the LGPL.
 *
 */

package org.dcm4chee.arc.dimse.rs;

import org.dcm4che3.conf.api.IApplicationEntityCache;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.*;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4che3.util.TagUtils;
import org.dcm4chee.arc.qmgt.QueueSizeLimitExceededException;
import org.dcm4chee.arc.query.scu.CFindSCU;
import org.dcm4chee.arc.query.util.QueryAttributes;
import org.dcm4chee.arc.retrieve.ExternalRetrieveContext;
import org.dcm4chee.arc.retrieve.mgt.RetrieveManager;
import org.dcm4chee.arc.validation.constraints.ValidUriInfo;
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
import java.io.IOException;
import java.util.EnumSet;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2017
 */
@RequestScoped
@Path("aets/{AETitle}/dimse/{ExternalAET}")
@ValidUriInfo(type = QueryAttributes.class)
public class QueryRetrieveRS {

    private static final Logger LOG = LoggerFactory.getLogger(QueryRetrieveRS.class);

    @Context
    private HttpServletRequest request;

    @Context
    private UriInfo uriInfo;

    @Inject
    private Device device;

    @PathParam("AETitle")
    private String aet;

    @PathParam("ExternalAET")
    private String externalAET;

    @QueryParam("fuzzymatching")
    @Pattern(regexp = "true|false")
    private String fuzzymatching;

    @QueryParam("priority")
    @Pattern(regexp = "0|1|2")
    private String priority;

    @Inject
    private CFindSCU findSCU;

    @Inject
    private RetrieveManager retrieveManager;

    @Inject
    private IApplicationEntityCache aeCache;

    @Override
    public String toString() {
        return request.getRequestURI() + '?' + request.getQueryString();
    }

    @POST
    @Path("/studies/query:{QueryAET}/export/dicom:{DestinationAET}")
    @Produces("application/json")
    public Response retrieveMatchingStudies(
            @PathParam("QueryAET") String queryAET,
            @PathParam("DestinationAET") String destAET) throws Exception {
        return retrieveMatching(QueryRetrieveLevel2.STUDY, null, null, queryAET, destAET);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}/series/query:{QueryAET}/export/dicom:{DestinationAET}")
    @Produces("application/json")
    public Response retrieveMatchingSeriesOfStudy(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @PathParam("QueryAET") String queryAET,
            @PathParam("DestinationAET") String destAET)
            throws Exception {
        return retrieveMatching(QueryRetrieveLevel2.SERIES, studyInstanceUID, null, queryAET, destAET);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}/series/{SeriesInstanceUID}/instances/query:{QueryAET}/export/dicom:{DestinationAET}")
    @Produces("application/json")
    public Response retrieveMatchingInstancesOfSeriesJSON(
            @PathParam("StudyInstanceUID") String studyInstanceUID,
            @PathParam("SeriesInstanceUID") String seriesInstanceUID,
            @PathParam("QueryAET") String queryAET,
            @PathParam("DestinationAET") String destAET)
            throws Exception {
        return retrieveMatching(QueryRetrieveLevel2.IMAGE, studyInstanceUID, seriesInstanceUID, queryAET, destAET);
    }

    private ApplicationEntity checkAE(String aet, ApplicationEntity ae) {
        if (ae == null || !ae.isInstalled())
            throw new WebApplicationException(errResponse(
                    "No such Application Entity: " + aet,
                    Response.Status.NOT_FOUND));
        return ae;
    }

    private Response errResponse(String errorMessage, Response.Status status) {
        return Response.status(status).entity("{\"errorMessage\":\"" + errorMessage + "\"}").build();
    }

    private int priority() {
        return parseInt(priority, 0);
    }

    private static int parseInt(String s, int defval) {
        return s != null ? Integer.parseInt(s) : defval;
    }

    private Response retrieveMatching(QueryRetrieveLevel2 level, String studyInstanceUID, String seriesInstanceUID,
                                      String queryAET, String destAET) throws Exception {
        LOG.info("Process POST {} from {}@{}", request.getRequestURI(), request.getRemoteUser(), request.getRemoteHost());
        ApplicationEntity localAE = checkAE(aet, device.getApplicationEntity(aet, true));
        checkAE(externalAET, aeCache.get(externalAET));
        checkAE(queryAET, aeCache.get(queryAET));
        QueryAttributes queryAttributes = new QueryAttributes(uriInfo);
        queryAttributes.addReturnTags(level.uniqueKey());
        Attributes keys = queryAttributes.getQueryKeys();
        keys.setString(Tag.QueryRetrieveLevel, VR.CS, level.name());
        if (studyInstanceUID != null)
            keys.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUID);
        if (seriesInstanceUID != null)
            keys.setString(Tag.SeriesInstanceUID, VR.UI, seriesInstanceUID);
        EnumSet<QueryOption> queryOptions = EnumSet.of(QueryOption.DATETIME);
        if (Boolean.parseBoolean(fuzzymatching))
            queryOptions.add(QueryOption.FUZZY);
        Association as = null;
        String warning;
        int count = 0;
        Response.Status errorStatus = Response.Status.BAD_GATEWAY;
        try {
            as = findSCU.openAssociation(localAE, queryAET, UID.StudyRootQueryRetrieveInformationModelFIND, queryOptions);
            DimseRSP dimseRSP = findSCU.query(as, priority(), keys, 0);
            dimseRSP.next();
            int status;
            do {
                status = dimseRSP.getCommand().getInt(Tag.Status, -1);
                if (Status.isPending(status)) {
                    retrieveManager.scheduleRetrieveTask(priority(), toInstancesRetrieved(destAET, dimseRSP));
                    count++;
                }
            } while (dimseRSP.next()) ;
            warning = warning(status);
        } catch (QueueSizeLimitExceededException e) {
            errorStatus = Response.Status.SERVICE_UNAVAILABLE;
            warning = e.getMessage();
        } catch (Exception e) {
            warning = e.getMessage();
        } finally {
            if (as != null)
                try {
                    as.release();
                } catch (IOException e) {
                    LOG.info("{}: Failed to release association:\\n", as, e);
                }
        }
        if (warning == null)
            return Response.accepted(count(count)).build();

        Response.ResponseBuilder builder = Response.status(errorStatus)
                .header("Warning", warning);
        if (count > 0)
            builder.entity(count(count));
        return builder.build();
    }

    private ExternalRetrieveContext toInstancesRetrieved(String destAET, DimseRSP dimseRSP) {
        Attributes keys = new Attributes(dimseRSP.getDataset(),
                Tag.QueryRetrieveLevel, Tag.StudyInstanceUID, Tag.SeriesInstanceUID, Tag.SOPInstanceUID);
        return new ExternalRetrieveContext()
                .setLocalAET(aet)
                .setRemoteAET(externalAET)
                .setDestinationAET(destAET)
                .setRequestInfo(request)
                .setKeys(keys);
    }

    private static String count(int count) {
        return "{\"count\":" + count + '}';
    }

    private static String warning(int status) {
        switch (status) {
            case Status.Success:
                return null;
            case Status.OutOfResources:
                return "A700: Refused: Out of Resources";
            case Status.IdentifierDoesNotMatchSOPClass:
                return "A900: Identifier does not match SOP Class";
        }
        return TagUtils.shortToHexString(status)
                + ((status & Status.UnableToProcess) == Status.UnableToProcess
                ? ": Unable to Process"
                : ": Unexpected status code");
    }

}
