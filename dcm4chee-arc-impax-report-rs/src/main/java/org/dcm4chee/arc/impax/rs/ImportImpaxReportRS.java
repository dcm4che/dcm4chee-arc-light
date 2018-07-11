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
import org.dcm4che3.io.ContentHandlerAdapter;
import org.dcm4che3.io.TemplatesCache;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.util.TagUtils;
import org.dcm4che3.util.UIDUtils;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.impax.report.ReportServiceProvider;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreService;
import org.dcm4chee.arc.store.StoreSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.ws.WebServiceException;
import java.io.*;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2018
 */
@RequestScoped
@Path("aets/{AETitle}/rs")
public class ImportImpaxReportRS {
    private static final ElementDictionary dict = ElementDictionary.getStandardElementDictionary();
    private static final Logger LOG = LoggerFactory.getLogger(ImportImpaxReportRS.class);
    private static final String DEFAULT_XSL = "${jboss.server.temp.url}/dcm4chee-arc/impax-report2sr.xsl ";
    private static SAXTransformerFactory tranformerFactory = (SAXTransformerFactory) TransformerFactory.newInstance();
    private static SAXParserFactory parserFactory = SAXParserFactory.newInstance();

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

    private String studyUID;
    private ApplicationEntity ae;
    private Map<String, String> props;
    private Templates tpls;
    private Attributes studyAttrs;
    private Attributes propsAttrs;
    private List<String> reports;
    private Attributes response;
    private Sequence failedSOPSequence;
    private Sequence sopSequence;
    private int instanceNumber;

    private static final int[] TYPE2_TAGS = {
            Tag.StudyID,
            Tag.Manufacturer,
            Tag.ReferencedPerformedProcedureStepSequence,
            Tag.PerformedProcedureCodeSequence
    };

    @POST
    @Path("/studies/{studyUID}/impax/reports")
    @Produces("application/dicom+json")
    public Response importReportsOfStudy(@PathParam("studyUID") String studyUID) {
        LOG.info("Process {} {} from {}@{}", request.getMethod(), request.getRequestURI(),
                request.getRemoteUser(), request.getRemoteHost());
        init(studyUID);
        queryStudy();
        queryReports();
        initResponse();
        storeReports();
        return buildResponse();
    }

    private void init(String studyUID) {
        this.studyUID = studyUID;
        ae = device.getApplicationEntity(aet, true);
        if (ae == null || !ae.isInstalled()) {
            throw new WebApplicationException("No such Application Entity: " + aet, Response.Status.NOT_FOUND);
        }
        props = device.getDeviceExtension(ArchiveDeviceExtension.class).getImpaxReportProperties();
        try {
            tpls = TemplatesCache.getDefault().get(
                        StringUtils.replaceSystemProperties(props.getOrDefault("xsl", DEFAULT_XSL)));
        } catch (TransformerConfigurationException e) {
            throw new WebApplicationException(e, Response.Status.INTERNAL_SERVER_ERROR);
        }
        propsAttrs = new Attributes();
        for (Map.Entry<String, String> prop : props.entrySet()) {
            if (prop.getKey().startsWith("SR.")) {
                int tag = TagUtils.forName(prop.getKey().substring(3));
                propsAttrs.setString(tag, dict.vrOf(tag), prop.getValue());
            }
        }
    }

    private void queryStudy() {
        studyAttrs = queryService.getStudyAttributes(studyUID);
        if (studyAttrs == null) {
            throw new WebApplicationException("No such Study: " + studyUID, Response.Status.NOT_FOUND);
        }
    }

    private void queryReports() {
        try {
            reports = reportService.queryReportByStudyUid(studyUID);
        } catch (ConfigurationException e) {
            throw new WebApplicationException(e, Response.Status.INTERNAL_SERVER_ERROR);
        } catch (WebServiceException e) {
            throw new WebApplicationException(e, Response.Status.BAD_GATEWAY);
        }
        if (reports.isEmpty()) {
            throw new WebApplicationException(Response.Status.CONFLICT);
        }
    }

    private void initResponse() {
        response = new Attributes();
        response.setString(Tag.RetrieveURL, VR.UR, studyRetrieveURL().toString());
        failedSOPSequence = response.newSequence(Tag.FailedSOPSequence, reports.size());
        sopSequence = response.newSequence(Tag.ReferencedSOPSequence, reports.size());
    }

    private Response buildResponse() {
        Response.Status status = Response.Status.ACCEPTED;
        if (sopSequence.isEmpty()) {
            response.remove(Tag.ReferencedSOPSequence);
            status = Response.Status.CONFLICT;
        } else if (failedSOPSequence.isEmpty()) {
            response.remove(Tag.FailedSOPSequence);
            status = Response.Status.OK;
        }
        return Response.status(status).entity((StreamingOutput) out -> {
            JsonGenerator gen = Json.createGenerator(out);
            new JSONWriter(gen).write(response);
            gen.flush();
        }).build();
    }

    private void storeReports() {
        try (StoreSession session = storeService.newStoreSession(ae, null)) {
            for (String report : reports) {
                storeReport(session, report);
            }
        } catch (IOException e) {
            throw new WebApplicationException(e, Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private void storeReport(StoreSession session, String report) {
        Attributes attrs = new Attributes(propsAttrs.size() + studyAttrs.size());
        attrs.addAll(propsAttrs);
        attrs.addAll(studyAttrs);
        addUIDs(report, attrs);
        StoreContext ctx = storeService.newStoreContext(session);
        try {
            xslt(report, attrs);
            adjust(attrs);
            ctx.setReceiveTransferSyntax(UID.ExplicitVRLittleEndian);
            storeService.store(ctx, attrs);
            sopSequence.add(mkSOPRefWithRetrieveURL(ctx));
        } catch (Exception e) {
            ctx.setAttributes(attrs);
            LOG.info("{}: Failed to store {}", session, UID.nameOf(ctx.getSopClassUID()), e);
            response.setString(Tag.ErrorComment, VR.LO, e.getMessage());
            failedSOPSequence.add(mkSOPRefWithFailureReason(ctx, e));
        }
    }

    private void xslt(String report, Attributes attrs) throws Exception {
        TransformerHandler th = tranformerFactory.newTransformerHandler(tpls);
        th.setResult(new SAXResult(new ContentHandlerAdapter(attrs)));
        XMLReader reader = parserFactory.newSAXParser().getXMLReader();
        reader.setContentHandler(th);
        reader.setDTDHandler(th);
        reader.parse(new InputSource(new StringReader(report)));
    }

    private void addUIDs(String report, Attributes attrs) {
        try {
            attrs.setString(Tag.SeriesInstanceUID, VR.UI,
                    UIDUtils.createNameBasedUID(studyUID.getBytes("UTF-8")));
            attrs.setString(Tag.SOPInstanceUID, VR.UI,
                    UIDUtils.createNameBasedUID(report.getBytes("UTF-8")));
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

    private void adjust(Attributes attrs) {
        setStringIfMissing(attrs, Tag.SOPClassUID, VR.UI, UID.BasicTextSRStorage);
        setStringIfMissing(attrs, Tag.SeriesNumber, VR.IS, "0");
        setStringIfMissing(attrs, Tag.InstanceNumber, VR.IS, String.valueOf(++instanceNumber));
        setDateTimeIfMissing(attrs, Tag.ContentDateAndTime, new Date());
        supplementMissingType2(attrs);
        supplementVerifyingObserverSequence(attrs);
    }

    private void setStringIfMissing(Attributes attrs, int tag, VR vr, String value) {
        if (!attrs.containsValue(tag))
            attrs.setString(tag, vr, value);
    }

    private void setDateTimeIfMissing(Attributes attrs, long tag, Date date) {
        if (!attrs.containsValue((int) (tag >>> 32)))
            attrs.setDate(tag, date);
    }

    private void supplementMissingType2(Attributes attrs) {
        for (int tag : TYPE2_TAGS)
            if (!attrs.contains(tag))
                attrs.setNull(tag, dict.vrOf(tag));
    }

    private void supplementVerifyingObserverSequence(Attributes attrs) {
        if (!attrs.contains(Tag.VerifyingObserverSequence))
            return;

        Attributes item = attrs.getNestedDataset(Tag.VerifyingObserverSequence);
        item.setString(Tag.VerifyingOrganization, VR.LO,
                attrs.contains(Tag.VerifyingOrganization) ? attrs.getString(Tag.VerifyingOrganization) : "VerifyingOrganization");
        if (item.getString(Tag.VerifyingObserverName) == null)
            item.setString(Tag.VerifyingObserverName, VR.PN,
                attrs.contains(Tag.VerifyingObserverName) ? attrs.getString(Tag.VerifyingObserverName) : "VerifyingObserver");
        attrs.remove(Tag.VerifyingOrganization);
        attrs.remove(Tag.VerifyingObserverName);
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
