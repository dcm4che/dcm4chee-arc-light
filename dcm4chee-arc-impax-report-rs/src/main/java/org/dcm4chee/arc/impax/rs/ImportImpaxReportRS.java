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
 * Portions created by the Initial Developer are Copyright (C) 2015-2018
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

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.data.*;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.impax.report.ImpaxReportConverter;
import org.dcm4chee.arc.impax.report.ReportServiceProvider;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreService;
import org.dcm4chee.arc.store.StoreSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.xml.ws.WebServiceException;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2018
 */
@RequestScoped
@Path("aets/{AETitle}/rs")
public class ImportImpaxReportRS {
    private static final Logger LOG = LoggerFactory.getLogger(ImportImpaxReportRS.class);

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
        LOG.info("Process {} {} from {}@{}", request.getMethod(), request.getRequestURI(),
                request.getRemoteUser(), request.getRemoteHost());
        ApplicationEntity ae = getApplicationEntity();
        Attributes studyAttrs = queryStudyAttributes(studyUID);
        Map<String, String> props = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class)
                .getImpaxReportProperties();
        ImpaxReportConverter converter = createConverter(studyAttrs, props);
        List<String> reports = queryReports(studyUID);
        if (reports.isEmpty()) {
            throw new WebApplicationException(Response.Status.CONFLICT);
        }
        Attributes response = new Attributes();
        response.setString(Tag.RetrieveURL, VR.UR, studyRetrieveURL().toString());
        try (StoreSession session = storeService.newStoreSession(request, ae, props.get("SourceAET"))) {
            for (String report : reports) {
                storeReport(session, converter.convert(report), response);
            }
        } catch (Exception e) {
            throw new WebApplicationException(e, Response.Status.INTERNAL_SERVER_ERROR);
        }
        return buildResponse(response);
    }

    private ApplicationEntity getApplicationEntity() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        if (ae == null || !ae.isInstalled()) {
            throw new WebApplicationException("No such Application Entity: " + aet, Response.Status.NOT_FOUND);
        }
        return ae;
    }

    private Attributes queryStudyAttributes(String studyUID) {
        Attributes studyAttrs = queryService.getStudyAttributes(studyUID);
        if (studyAttrs == null) {
            throw new WebApplicationException("No such Study: " + studyUID, Response.Status.NOT_FOUND);
        }
        return studyAttrs;
    }

    private ImpaxReportConverter createConverter(Attributes studyAttrs, Map<String, String> props) {
        ImpaxReportConverter converter = null;
        try {
            converter = new ImpaxReportConverter(props, studyAttrs);
        } catch (Exception e) {
            throw new WebApplicationException(e, Response.Status.INTERNAL_SERVER_ERROR);
        }
        return converter;
    }

    private List<String> queryReports(String studyUID) {
        try {
            return reportService.queryReportByStudyUid(studyUID);
        } catch (ConfigurationException e) {
            throw new WebApplicationException(e, Response.Status.INTERNAL_SERVER_ERROR);
        } catch (WebServiceException e) {
            throw new WebApplicationException(e, Response.Status.BAD_GATEWAY);
        }
    }

    private void storeReport(StoreSession session, Attributes attrs, Attributes response) {
        StoreContext ctx = storeService.newStoreContext(session);
        try {
            ctx.setReceiveTransferSyntax(UID.ExplicitVRLittleEndian);
            storeService.store(ctx, attrs);
            response.ensureSequence(Tag.ReferencedSOPSequence, 1)
                    .add(mkSOPRefWithRetrieveURL(ctx));
        } catch (IOException e) {
            ctx.setAttributes(attrs);
            LOG.info("{}: Failed to store {}", session, UID.nameOf(ctx.getSopClassUID()), e);
            response.setString(Tag.ErrorComment, VR.LO, e.getMessage());
            response.ensureSequence(Tag.ReferencedSOPSequence, 1)
                    .add(mkSOPRefWithFailureReason(ctx, e));
        }
    }

    private Response buildResponse(Attributes response) {
        Response.Status status = Response.Status.ACCEPTED;
        if (!response.contains(Tag.ReferencedSOPSequence)) {
            status = Response.Status.CONFLICT;
        } else if (!response.contains(Tag.FailedSOPSequence)) {
            status = Response.Status.OK;
        }
        return Response.status(status).entity((StreamingOutput) out -> {
            JsonGenerator gen = Json.createGenerator(out);
            new JSONWriter(gen).write(response);
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

}
