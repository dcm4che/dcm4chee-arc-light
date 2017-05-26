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
 *  Java(TM), hosted at https://github.com/gunterze/dcm4che.
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

package org.dcm4chee.arc.qido;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.data.Tag;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4che3.util.SafeClose;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.query.Query;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.validation.constraints.ValidUriInfo;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.CompletionCallback;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since May 2017
 */
@RequestScoped
@Path("aets/{AETitle}/diff/{RemoteAETitle}")
@ValidUriInfo(type = QueryAttributes.class)
public class DiffRS {

    private static final Logger LOG = LoggerFactory.getLogger(DiffRS.class);

    private final static int[] STUDY_FIELDS = {
            Tag.StudyDate,
            Tag.StudyTime,
            Tag.AccessionNumber,
            Tag.ModalitiesInStudy,
            Tag.ReferringPhysicianName,
            Tag.PatientName,
            Tag.PatientID,
            Tag.PatientBirthDate,
            Tag.PatientSex,
            Tag.StudyID,
            Tag.StudyInstanceUID,
            Tag.NumberOfStudyRelatedSeries,
            Tag.NumberOfStudyRelatedInstances
    };

    @Inject
    private QueryService service;

    @Context
    private HttpServletRequest request;

    @Context
    private UriInfo uriInfo;

    @Inject
    private Device device;

    @PathParam("AETitle")
    private String aet;

    @PathParam("RemoteAETitle")
    private String remoteAET;

    @QueryParam("missing")
    @Pattern(regexp = "local|remote")
    private String missing;

    @QueryParam("returnempty")
    @Pattern(regexp = "true|false")
    private String returnempty;

    @QueryParam("expired")
    @Pattern(regexp = "true|false")
    private String expired;

    @QueryParam("fuzzymatching")
    @Pattern(regexp = "true|false")
    private String fuzzymatching;

    @QueryParam("limit")
    @Pattern(regexp = "[1-9]\\d{0,4}")
    private String limit;

    @QueryParam("withoutstudies")
    @Pattern(regexp = "true|false")
    private String withoutstudies;

    @QueryParam("incomplete")
    @Pattern(regexp = "true|false")
    private String incomplete;

    @QueryParam("retrievefailed")
    @Pattern(regexp = "true|false")
    private String retrievefailed;

    @QueryParam("SendingApplicationEntityTitleOfSeries")
    private String sendingApplicationEntityTitleOfSeries;

    @QueryParam("StudyReceiveDateTime")
    private String studyReceiveDateTime;

    @QueryParam("ExternalRetrieveAET")
    private String externalRetrieveAET;

    @QueryParam("ExternalRetrieveAET!")
    private String externalRetrieveAETNot;

    private Query query;

    @Override
    public String toString() {
        return request.getRequestURI() + '?' + request.getQueryString();
    }

    @GET
    @NoCache
    @Path("/studies")
    @Produces("application/dicom+json,application/json")
    public void searchForStudiesJSON(@Suspended AsyncResponse ar) throws Exception {
        LOG.info("Process GET {} from {}@{}", this, request.getRemoteUser(), request.getRemoteHost());
        QueryAttributes queryAttrs = new QueryAttributes(uriInfo);
        QueryContext ctx = newQueryContext(queryAttrs);
        ArchiveAEExtension arcAE = ctx.getArchiveAEExtension();
        query = service.createQuery(ctx);
        ar.register(new CompletionCallback() {
            @Override
            public void onComplete(Throwable throwable) {
                if (query != null)
                    query.close();
            }
        });
        query.initQuery();
        query.setFetchSize(arcAE.getArchiveDeviceExtension().getQueryFetchSize());
        int limitInt = parseInt(limit);
        query.executeQuery();
        while (query.hasMoreMatches()) {
            Attributes match = query.adjust(query.nextMatch());
            if (diff(match)) {
                ar.resume(Response.ok(entity(query, match, limitInt)).build());
                return;
            }
        }
        ar.resume(Response.noContent().build());
    }

    private boolean diff(Attributes match) {
        return match != null;
    }

    private QueryContext newQueryContext(QueryAttributes queryAttrs) {
        ApplicationEntity ae = getApplicationEntity();

        org.dcm4chee.arc.query.util.QueryParam queryParam = new org.dcm4chee.arc.query.util.QueryParam(ae);
        queryParam.setCombinedDatetimeMatching(true);
        queryParam.setFuzzySemanticMatching(Boolean.parseBoolean(fuzzymatching));
        queryParam.setReturnEmpty(Boolean.parseBoolean(returnempty));
        queryParam.setExpired(Boolean.parseBoolean(expired));
        queryParam.setWithoutStudies(withoutstudies == null || Boolean.parseBoolean(withoutstudies));
        queryParam.setIncomplete(Boolean.parseBoolean(incomplete));
        queryParam.setRetrieveFailed(Boolean.parseBoolean(retrievefailed));
        queryParam.setSendingApplicationEntityTitleOfSeries(sendingApplicationEntityTitleOfSeries);
        queryParam.setStudyReceiveDateTime(studyReceiveDateTime);
        queryParam.setExternalRetrieveAET(externalRetrieveAET);
        queryParam.setExternalRetrieveAETNot(externalRetrieveAETNot);
        QueryContext ctx = service.newQueryContextQIDO(request, "CompareStudies", ae, queryParam);
        ctx.setQueryRetrieveLevel(QueryRetrieveLevel2.STUDY);
        Attributes keys = queryAttrs.getQueryKeys();
        IDWithIssuer idWithIssuer = IDWithIssuer.pidOf(keys);
        if (idWithIssuer != null)
            ctx.setPatientIDs(idWithIssuer);
        ctx.setQueryKeys(keys);
        ctx.setReturnKeys(queryAttrs.getReturnKeys(STUDY_FIELDS));
        ctx.setOrderByPatientName(queryAttrs.isOrderByPatientName());
        return ctx;
    }


    private static int parseInt(String s) {
        return s != null ? Integer.parseInt(s) : 0;
    }

    private ApplicationEntity getApplicationEntity() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        if (ae == null || !ae.isInstalled())
            throw new WebApplicationException(
                    "No such Application Entity: " + aet,
                    Response.Status.SERVICE_UNAVAILABLE);
        return ae;
    }

    private Object entity(final Query query, final Attributes firstMatch, final int limit) {
        return new StreamingOutput() {
            @Override
            public void write(OutputStream out) throws IOException {
                try (JsonGenerator gen = Json.createGenerator(out)) {
                    JSONWriter writer = new JSONWriter(gen);
                    gen.writeStartArray();
                    writer.write(firstMatch);
                    int count = 1;
                    while ((limit == 0 || limit > count) && query.hasMoreMatches()) {
                        Attributes match = query.adjust(query.nextMatch());
                        if (diff(match)) {
                            writer.write(match);
                            count++;
                        }
                    }
                    gen.writeEnd();
                }
            }
        };
    }

}
