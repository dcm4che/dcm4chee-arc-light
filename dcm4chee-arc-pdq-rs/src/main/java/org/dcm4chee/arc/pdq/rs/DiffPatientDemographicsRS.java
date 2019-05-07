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

package org.dcm4chee.arc.pdq.rs;

import org.dcm4che3.data.*;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.Entity;
import org.dcm4chee.arc.conf.PDQServiceDescriptor;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.pdq.PDQService;
import org.dcm4chee.arc.pdq.PDQServiceException;
import org.dcm4chee.arc.pdq.PDQServiceFactory;
import org.dcm4chee.arc.query.Query;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.query.util.QueryAttributes;
import org.dcm4chee.arc.validation.constraints.InvokeValidate;
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
import javax.ws.rs.core.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Feb 2019
 */
@RequestScoped
@Path("aets/{AETitle}/diff/pdq/{PDQServiceID}")
@InvokeValidate(type = DiffPatientDemographicsRS.class)
public class DiffPatientDemographicsRS {

    private static final Logger LOG = LoggerFactory.getLogger(DiffPatientDemographicsRS.class);

    @Inject
    private QueryService queryService;

    @Inject
    private PDQServiceFactory serviceFactory;

    @Context
    private HttpServletRequest request;

    @Context
    private UriInfo uriInfo;

    @Context
    private HttpHeaders headers;

    @Inject
    private Device device;

    @PathParam("AETitle")
    private String aet;

    @PathParam("PDQServiceID")
    private String pdqServiceID;

    @QueryParam("withoutstudies")
    @Pattern(regexp = "true|false")
    private String withoutstudies;

    @QueryParam("patientVerificationStatus")
    @Pattern(regexp = "UNVERIFIED|VERIFIED|NOT_FOUND|VERIFICATION_FAILED")
    private String patientVerificationStatus;

    @QueryParam("isFuzzymatching")
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
    private String checkDifferent;

    @QueryParam("missing")
    @Pattern(regexp = "true|false")
    private String checkMissing;

    @Override
    public String toString() {
        return request.getRequestURI() + '?' + request.getQueryString();
    }

    public void validate() {
        logRequest();
        new QueryAttributes(uriInfo, null);
    }

    @GET
    @NoCache
    @Path("/patients")
    @Produces("application/dicom+json,application/json")
    public Response comparePatients() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        if (ae == null || !ae.isInstalled())
            return errResponse("No such Application Entity: " + aet, Response.Status.NOT_FOUND);

        ArchiveDeviceExtension arcdev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        if (arcdev == null)
            return errResponse("Archive Device Extension not configured.", Response.Status.NOT_FOUND);

        PDQServiceDescriptor descriptor = arcdev.getPDQServiceDescriptor(pdqServiceID);
        if (descriptor == null)
            return errResponse("No such PDQ Service: " + pdqServiceID, Response.Status.NOT_FOUND);

        try {
            QueryContext ctx = createQueryContext(ae);
            Query query = ctx.getQueryService().createQuery(ctx);
            PDQService service = serviceFactory.getPDQService(descriptor);
            int queryMaxNumberOfResults1 = ctx.getArchiveAEExtension().queryMaxNumberOfResults();
            if (queryMaxNumberOfResults1 > 0 && !ctx.containsUniqueKey()
                    && query.fetchCount() > queryMaxNumberOfResults1)
                return errResponse("Request entity too large", Response.Status.BAD_REQUEST);

            query.executeQuery(arcdev.getQueryFetchSize());
            return (query.hasMoreMatches()
                        ? Response.ok(entity(calculateDiffs(query, service)))
                        : Response.noContent())
                    .build();
        } catch (IllegalStateException e) {
            return errResponse(e.getMessage(), Response.Status.NOT_FOUND);
        } catch (Exception e) {
            return errResponseAsTextPlain(exceptionAsString(e), Response.Status.INTERNAL_SERVER_ERROR);
        }
   }

    private StreamingOutput entity(List<Attributes> diffs) {
        return out -> {
            JsonGenerator gen = Json.createGenerator(out);
            JSONWriter writer = new JSONWriter(gen);
            gen.writeStartArray();
            diffs.forEach(writer::write);
            gen.writeEnd();
            gen.flush();
        };
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

    private static Response errResponseAsTextPlain(String message, Response.Status status) {
        LOG.warn("Response {} caused by {}", status, message);
        return Response.status(status)
                .entity(message)
                .type("text/plain")
                .build();
    }

    private static String exceptionAsString(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private QueryContext createQueryContext(ApplicationEntity ae) {
        QueryAttributes queryAttrs = new QueryAttributes(uriInfo, null);
        org.dcm4chee.arc.query.util.QueryParam queryParam = new org.dcm4chee.arc.query.util.QueryParam(ae);
        queryParam.setCombinedDatetimeMatching(true);
        queryParam.setFuzzySemanticMatching(Boolean.parseBoolean(fuzzymatching));
        queryParam.setWithoutStudies(withoutstudies == null || Boolean.parseBoolean(withoutstudies));
        if (patientVerificationStatus != null)
            queryParam.setPatientVerificationStatus(Patient.VerificationStatus.valueOf(patientVerificationStatus));
        QueryContext ctx = queryService.newQueryContextQIDO(request, "GET", ae, queryParam);
        ctx.setQueryRetrieveLevel(QueryRetrieveLevel2.PATIENT);
        ctx.setSOPClassUID(UID.PatientRootQueryRetrieveInformationModelFIND);
        Attributes keys = queryAttrs.getQueryKeys();
        IDWithIssuer idWithIssuer = IDWithIssuer.pidOf(keys);
        if (idWithIssuer != null)
            ctx.setPatientIDs(idWithIssuer);
        ctx.setQueryKeys(keys);
        ctx.setOrderByTags(queryAttrs.getOrderByTags());
        return ctx;
    }

    private boolean missing() {
        return parseBoolean(checkMissing, false);
    }

    private boolean different() {
        return parseBoolean(checkDifferent, true);
    }

    private static boolean parseBoolean(String s, boolean defVal) {
        return s != null ? s.equals("true") : defVal;
    }

    private int offset() {
        return parseInt(offset, 0);
    }

    private int limit() {
        return parseInt(limit, Integer.MAX_VALUE);
    }

    private static int parseInt(String s, int defVal) {
        return s != null ? Integer.parseInt(s) : defVal;
    }

    private List<Attributes> calculateDiffs(Query query, PDQService service) throws DicomServiceException {
        List<Attributes> result = new ArrayList<>();
        int skip = offset();
        int remaining = limit();
        boolean missing = missing();
        boolean different = different();
        int[] tags = getPatientAttributes(service);
        Attributes diff;
        while (remaining > 0 && (diff = nextDiff(query, service, missing, different, tags)) != null) {
            if (skip-- > 0)
                continue;

            result.add(diff);
            remaining--;
        }
        return result;
    }

    private int[] getPatientAttributes(PDQService service) {
        int[] selection = service.getPDQServiceDescriptor().getSelection();
        return selection.length > 0 ? selection
                : device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class)
                        .getAttributeFilter(Entity.Patient)
                        .getSelection(false);
    }

    private Attributes nextDiff(Query query, PDQService service, boolean missing, boolean different, int[] tags)
            throws DicomServiceException {
        while (query.hasMoreMatches()) {
            Attributes match = query.nextMatch();
            Attributes other = queryPDQService(service, match);
            if (other == null) {
                if (missing)
                    return addOriginalAttributesSequence(match, new Attributes(0));
            } else if (different) {
                Attributes modified = new Attributes(match.size());
                if (other.diff(match, tags, modified) > 0)
                    return addOriginalAttributesSequence(match, modified);
            }
        }
        return null;
    }

    private Attributes queryPDQService(PDQService service, Attributes match) {
        try {
            return service.query(IDWithIssuer.pidOf(match));
        } catch (PDQServiceException e) {
            throw new WebApplicationException(errResponseAsTextPlain(exceptionAsString(e), Response.Status.BAD_GATEWAY));
        }
    }

    private Attributes addOriginalAttributesSequence(Attributes match, Attributes modified) {
        Sequence sq = match.newSequence(Tag.OriginalAttributesSequence, 1);
        Attributes item = new Attributes();
        sq.add(item);
        item.newSequence(Tag.ModifiedAttributesSequence, 1).add(modified);
        item.setString(Tag.SourceOfPreviousValues, VR.LO, pdqServiceID);
        item.setDate(Tag.AttributeModificationDateTime, VR.DT, new Date());
        item.setString(Tag.ModifyingSystem, VR.LO, aet);
        item.setString(Tag.ReasonForTheAttributeModification, VR.CS, "DIFFS");
        return match;
    }
}
