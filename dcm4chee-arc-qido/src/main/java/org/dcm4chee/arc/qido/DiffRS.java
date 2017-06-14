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
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.DimseRSP;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.query.scu.CFindSCU;
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

import static org.dcm4che3.util.ByteUtils.EMPTY_INTS;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since May 2017
 */
@RequestScoped
@Path("aets/{AETitle}/diff/{RemoteAETitle1}/{RemoteAETitle2}")
@ValidUriInfo(type = QueryAttributes.class)
public class DiffRS {

    private static final Logger LOG = LoggerFactory.getLogger(DiffRS.class);

    private static final int[] DEF_INCLUDE_FIELDS = {
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

    @Context
    private HttpServletRequest request;

    @Context
    private UriInfo uriInfo;

    @Inject
    private Device device;

    @PathParam("AETitle")
    private String aet;

    @PathParam("RemoteAETitle1")
    private String remoteAET1;

    @PathParam("RemoteAETitle2")
    private String remoteAET2;

    @QueryParam("offset")
    @Pattern(regexp = "0|([1-9]\\d{0,4})")
    private String offset;

    @QueryParam("limit")
    @Pattern(regexp = "[1-9]\\d{0,4}")
    private String limit;

    @Inject
    private CFindSCU findSCU;

    private Association as1;
    private Association as2;

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
        ar.register(new CompletionCallback() {
            @Override
            public void onComplete(Throwable throwable) {
                safeRelease(as1);
                safeRelease(as2);
            }
        });
        ApplicationEntity localAE = getApplicationEntity();
        as1 = findSCU.openAssociation(localAE, remoteAET1);
        as2 = findSCU.openAssociation(localAE, remoteAET2);
        ArchiveAEExtension arcAE = localAE.getAEExtension(ArchiveAEExtension.class);
        Attributes keys = new QueryAttributes(uriInfo).getQueryKeys(DEF_INCLUDE_FIELDS,
                arcAE != null ? arcAE.diffStudiesIncludefieldAll() : EMPTY_INTS);
        int[] returnKeys = keys.tags();
        keys.setString(Tag.QueryRetrieveLevel, VR.CS, "STUDY");
        DimseRSP dimseRSP = findSCU.queryStudies(as1, keys);
        int skip = parseInt(offset, 0);
        while (dimseRSP.next()) {
            if (diff(dimseRSP, returnKeys) && skip-- == 0) {
                ar.resume(Response.ok(entity(dimseRSP, returnKeys)).build());
                return;
            }
        }
        ar.resume(Response.noContent().build());
    }

    private void safeRelease(Association as) {
        if (as != null)
            try {
                as.release();
            } catch (IOException e) {
                LOG.info("{}: Failed to release association:\\n", as, e);
            }
    }

    private static int parseInt(String s, int defval) {
        return s != null ? Integer.parseInt(s) : defval;
    }

    private ApplicationEntity getApplicationEntity() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        if (ae == null || !ae.isInstalled())
            throw new WebApplicationException(
                    "No such Application Entity: " + aet,
                    Response.Status.SERVICE_UNAVAILABLE);
        return ae;
    }

    private Object entity(final DimseRSP dimseRSP, final int[] returnKeys) {
        return new StreamingOutput() {
            @Override
            public void write(OutputStream out) throws IOException {
                try (JsonGenerator gen = Json.createGenerator(out)) {
                    JSONWriter writer = new JSONWriter(gen);
                    gen.writeStartArray();
                    writer.write(dimseRSP.getDataset());
                    int remaining = parseInt(limit, -1);
                    try {
                        while ((remaining < 0 || remaining-- > 0) && dimseRSP.next())
                            if (diff(dimseRSP, returnKeys))
                                writer.write(dimseRSP.getDataset());
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

    private boolean diff(DimseRSP dimseRSP, int[] returnKeys) throws Exception {
        Attributes match = dimseRSP.getDataset();
        if (match == null)
            return false;

        Attributes other = findSCU.queryStudy(as2, match.getString(Tag.StudyInstanceUID), returnKeys);
        Attributes modified = new Attributes(match.size());
        if (other == null) {
            modified.setInt(Tag.NumberOfStudyRelatedSeries, VR.IS, 0);
            modified.setInt(Tag.NumberOfStudyRelatedInstances, VR.IS, 0);
        } else if (!other.testUpdateSelected(Attributes.UpdatePolicy.MERGE, match, modified, returnKeys)
                || modified.isEmpty())
            return false;

        Sequence sq = match.newSequence(Tag.OriginalAttributesSequence, 1);
        Attributes item = new Attributes();
        sq.add(item);
        item.newSequence(Tag.ModifiedAttributesSequence, 1).add(modified);
        item.setString(Tag.SourceOfPreviousValues, VR.LO, remoteAET2);
        item.setDate(Tag.AttributeModificationDateTime, VR.DT, new Date());
        item.setString(Tag.ModifyingSystem, VR.LO, remoteAET1);
        item.setString(Tag.ReasonForTheAttributeModification, VR.CS, "DIFFS");
        return true;
    }

}
