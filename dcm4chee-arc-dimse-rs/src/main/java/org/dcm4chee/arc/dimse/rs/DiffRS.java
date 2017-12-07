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
import org.dcm4che3.data.*;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.net.*;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4che3.util.TagUtils;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.AttributeSet;
import org.dcm4chee.arc.conf.Entity;
import org.dcm4chee.arc.query.scu.CFindSCU;
import org.dcm4chee.arc.query.util.QIDO;
import org.dcm4chee.arc.query.util.QueryAttributes;
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
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since May 2017
 */
@RequestScoped
@Path("aets/{AETitle}/dimse/{ExternalAET}/diff/{OriginalAET}")
@ValidUriInfo(type = QueryAttributes.class)
public class DiffRS {

    private static final Logger LOG = LoggerFactory.getLogger(DiffRS.class);

    @Context
    private HttpServletRequest request;

    @Context
    private UriInfo uriInfo;

    @Inject
    private Device device;

    @Inject
    private IApplicationEntityCache aeCache;

    @PathParam("AETitle")
    private String aet;

    @PathParam("ExternalAET")
    private String externalAET;

    @PathParam("OriginalAET")
    private String originalAET;

    @QueryParam("fuzzymatching")
    @Pattern(regexp = "true|false")
    private String fuzzymatching;

    @QueryParam("offset")
    @Pattern(regexp = "0|([1-9]\\d{0,4})")
    private String offset;

    @QueryParam("limit")
    @Pattern(regexp = "[1-9]\\d{0,4}")
    private String limit;

    @QueryParam("different")
    @Pattern(regexp = "true|false")
    private String different;

    @QueryParam("missing")
    @Pattern(regexp = "true|false")
    private String missing;

    @QueryParam("comparefield")
    private List<String> comparefields;

    @QueryParam("priority")
    @Pattern(regexp = "0|1|2")
    private String priority;

    @Inject
    private CFindSCU findSCU;

    private Association as1;
    private Association as2;
    private boolean includeDifferent;
    private boolean includeMissing;

    @Override
    public String toString() {
        return request.getRequestURI() + '?' + request.getQueryString();
    }

    @GET
    @NoCache
    @Path("/studies")
    @Produces("application/dicom+json,application/json")
    public void searchForStudiesJSON(@Suspended AsyncResponse ar) throws Exception {
        search(ar, false);
    }

    @GET
    @NoCache
    @Path("/studies/count")
    @Produces("application/json")
    public void countDiffs(@Suspended AsyncResponse ar) throws Exception {
        search(ar, true);
    }

    private void search(AsyncResponse ar, boolean count) throws Exception {
        LOG.info("Process GET {} from {}@{}", request.getRequestURI(), request.getRemoteUser(), request.getRemoteHost());
        ApplicationEntity localAE = checkAE(aet, device.getApplicationEntity(aet, true));
        checkAE(externalAET, aeCache.get(externalAET));
        checkAE(originalAET, aeCache.get(originalAET));
        QueryAttributes queryAttributes = new QueryAttributes(uriInfo);
        int[] compareKeys = compareKeys();
        addReturnTags(queryAttributes, compareKeys);
        Attributes keys = queryAttributes.getQueryKeys();
        int[] returnKeys = keys.tags();
        keys.setString(Tag.QueryRetrieveLevel, VR.CS, "STUDY");
        ar.register(new CompletionCallback() {
            @Override
            public void onComplete(Throwable throwable) {
                safeRelease(as1);
                safeRelease(as2);
            }
        });
        EnumSet<QueryOption> queryOptions = EnumSet.of(QueryOption.DATETIME);
        if (Boolean.parseBoolean(fuzzymatching))
            queryOptions.add(QueryOption.FUZZY);
        as1 = findSCU.openAssociation(localAE, externalAET, UID.StudyRootQueryRetrieveInformationModelFIND, queryOptions);
        as2 = findSCU.openAssociation(localAE, originalAET, UID.StudyRootQueryRetrieveInformationModelFIND, queryOptions);
        DimseRSP dimseRSP = findSCU.query(as1, priority(), keys, 0);
        if (count) {
            int[] counts = new int[2];
            while (dimseRSP.next()) {
                diff(dimseRSP, compareKeys, returnKeys, counts);
            }
            ar.resume(Response.ok("{\"missing\":" + counts[0] + ",\"different\":" + counts[1] + "}").build());
            return;
        }
        includeMissing = missing != null && Boolean.parseBoolean(missing);
        includeDifferent = different == null || Boolean.parseBoolean(different);
        int skip = offset();
        while (dimseRSP.next()) {
            if (diff(dimseRSP, compareKeys, returnKeys, null) && skip-- == 0) {
                ar.resume(Response.ok(entity(dimseRSP, compareKeys, returnKeys)).build());
                return;
            }
        }
        ar.resume(Response.noContent().build());
    }

    private ApplicationEntity checkAE(String aet, ApplicationEntity ae) {
        if (ae == null || !ae.isInstalled())
            throw new WebApplicationException(
                    "No such Application Entity: " + aet,
                    Response.Status.NOT_FOUND);
        return ae;
    }

    private void addReturnTags(QueryAttributes queryAttributes, int[] compareKeys) {
        queryAttributes.addReturnTags(QIDO.STUDY.includetags);
        if (compareKeys != QIDO.STUDY.includetags)
            queryAttributes.addReturnTags(compareKeys);
        if (queryAttributes.isIncludeAll()) {
            ArchiveDeviceExtension arcdev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
            queryAttributes.addReturnTags(arcdev.getAttributeFilter(Entity.Patient).getSelection());
            queryAttributes.addReturnTags(arcdev.getAttributeFilter(Entity.Study).getSelection());
        }
    }

    private int[] compareKeys() {
        if (comparefields == null || comparefields.isEmpty())
            return QIDO.STUDY.includetags;

        int size = comparefields.size();
        if (size == 1) {
            ArchiveDeviceExtension arcdev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
            Map<String, AttributeSet> attributeSetMap = arcdev.getAttributeSet(AttributeSet.Type.DIFF_RS);
            AttributeSet attributeSet = attributeSetMap.get(comparefields.get(0));
            if (attributeSet != null) {
                return attributeSet.getSelection();
            }
        }
        int[] compareKeys = new int[size];
        for (int i = 0; i < size; i++) {
            try {
                compareKeys[i] = TagUtils.forName(comparefields.get(i));
            } catch (IllegalArgumentException e2) {
                String message = "comparefield=" + comparefields.get(i);
                throw new WebApplicationException(message,
                        Response.status(Response.Status.BAD_REQUEST).encoding(message).build());
            }
        }
        return compareKeys;
    }

    private void safeRelease(Association as) {
        if (as != null)
            try {
                as.release();
            } catch (IOException e) {
                LOG.info("{}: Failed to release association:\\n", as, e);
            }
    }

    private int offset() {
        return parseInt(offset, 0);
    }

    private int limit() {
        return parseInt(limit, 0);
    }

    private int priority() {
        return parseInt(priority, 0);
    }

    private static int parseInt(String s, int defval) {
        return s != null ? Integer.parseInt(s) : defval;
    }

    private Object entity(final DimseRSP dimseRSP, final int[] compareKeys, final int[] returnKeys) {
        return new StreamingOutput() {
            @Override
            public void write(OutputStream out) throws IOException {
                try (JsonGenerator gen = Json.createGenerator(out)) {
                    JSONWriter writer = new JSONWriter(gen);
                    gen.writeStartArray();
                    writer.write(dimseRSP.getDataset());
                    int remaining = limit();
                    try {
                        while (dimseRSP.next())
                            if (diff(dimseRSP, compareKeys, returnKeys, null)) {
                                writer.write(dimseRSP.getDataset());
                                if (limit != null && --remaining == 0)
                                    break;
                            }
                    } catch (Exception e) {
                        writer.write(toAttributes(e));
                        LOG.info("Failure on query for matching studies:\\n", e);
                    }
                    gen.writeEnd();
                }
            }
        };
    }

    private Attributes toAttributes(Exception e) {
        return null;
    }

    private boolean diff(DimseRSP dimseRSP, int[] compareKeys, int[] returnKeys, int[] counts) throws Exception {
        Attributes match = dimseRSP.getDataset();
        if (match == null)
            return false;

        List<Attributes> matches = findSCU.find(as2, priority(), QueryRetrieveLevel2.STUDY,
                match.getString(Tag.StudyInstanceUID), null, null, returnKeys);
        Attributes other = !matches.isEmpty() ? matches.get(0) : null;
        if (counts != null) {
            if (other == null)
                counts[0]++;
            else if (other.diff(match, compareKeys,null) > 0)
                counts[1]++;
            else
                return false;

            return true;
        }
        Attributes modified = new Attributes(match.size());
        if (other == null) {
            if (!includeMissing)
                return false;

            modified = new Attributes(2);
            modified.setInt(Tag.NumberOfStudyRelatedSeries, VR.IS, 0);
            modified.setInt(Tag.NumberOfStudyRelatedInstances, VR.IS, 0);
        } else if (!includeDifferent || other.diff(match, compareKeys, modified) == 0)
            return false;

        Sequence sq = match.newSequence(Tag.OriginalAttributesSequence, 1);
        Attributes item = new Attributes();
        sq.add(item);
        item.newSequence(Tag.ModifiedAttributesSequence, 1).add(modified);
        item.setString(Tag.SourceOfPreviousValues, VR.LO, originalAET);
        item.setDate(Tag.AttributeModificationDateTime, VR.DT, new Date());
        item.setString(Tag.ModifyingSystem, VR.LO, externalAET);
        item.setString(Tag.ReasonForTheAttributeModification, VR.CS, "DIFFS");
        return true;
    }

}
