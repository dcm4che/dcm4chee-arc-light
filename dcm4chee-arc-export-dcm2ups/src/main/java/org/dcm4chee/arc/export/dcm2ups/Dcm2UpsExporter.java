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

package org.dcm4chee.arc.export.dcm2ups;

import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.dcm4che3.conf.api.IWebApplicationCache;
import org.dcm4che3.data.*;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.WebApplication;
import org.dcm4che3.util.AttributesFormat;
import org.dcm4che3.util.Base64;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.conf.InputReadinessState;
import org.dcm4chee.arc.conf.UPSPriority;
import org.dcm4chee.arc.conf.UPSState;
import org.dcm4chee.arc.entity.Task;
import org.dcm4chee.arc.exporter.AbstractExporter;
import org.dcm4chee.arc.exporter.ExportContext;
import org.dcm4chee.arc.keycloak.AccessTokenRequestor;
import org.dcm4chee.arc.qmgt.Outcome;
import org.dcm4chee.arc.query.QueryService;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since May 2021
 */
public class Dcm2UpsExporter extends AbstractExporter {

    private static final Logger LOG = LoggerFactory.getLogger(Dcm2UpsExporter.class);
    private static final ElementDictionary DICT = ElementDictionary.getStandardElementDictionary();

    private static final String DEFAULT_INPUT_READINESS_STATE = "READY";
    private static final String DEFAULT_SCHEDULED_PROCEDURE_STEP_PRIORITY = "MEDIUM";
    private static final String DEFAULT_PROCEDURE_STEP_LABEL = "DEFAULT";
    private static final MediaType DEFAULT_CONTENT_TYPE = MediaTypes.APPLICATION_DICOM_JSON_TYPE;

    private final String destWebAppName;
    private final Device device;
    private final QueryService queryService;
    private final IWebApplicationCache webAppCache;
    private final AccessTokenRequestor accessTokenRequestor;
    private final AttributesFormat procedureStepLabel;
    private final boolean copyPatient;
    private final boolean copyVisit;
    private final boolean copyRequest;
    private String inputReadinessState;
    private String upsPriority;

    private static final int[] REQUEST_ATTR = {
            Tag.StudyInstanceUID,
            Tag.AccessionNumber,
            Tag.IssuerOfAccessionNumberSequence,
            Tag.ReferringPhysicianName
    };

    private static final int[] PATIENT_ATTR = {
            Tag.PatientName,
            Tag.PatientID,
            Tag.IssuerOfPatientID,
            Tag.IssuerOfPatientIDQualifiersSequence,
            Tag.PatientBirthDate,
            Tag.PatientSex,
            Tag.OtherPatientIDsSequence
    };

    private static final int[] VISIT_ATTR = {
            Tag.AdmissionID,
            Tag.IssuerOfAdmissionIDSequence
    };

    private static final int[] TYPE2_ATTR = {
            Tag.WorklistLabel,
            Tag.ScheduledProcessingParametersSequence,
            Tag.ScheduledStationNameCodeSequence,
            Tag.ScheduledStationClassCodeSequence,
            Tag.ScheduledStationGeographicLocationCodeSequence,
            Tag.ScheduledHumanPerformersSequence,
            Tag.ScheduledWorkitemCodeSequence,
            Tag.CommentsOnTheScheduledProcedureStep,
            Tag.AdmittingDiagnosesDescription,
            Tag.AdmittingDiagnosesCodeSequence,
            Tag.OrderPlacerIdentifierSequence,
            Tag.OrderFillerIdentifierSequence,
            Tag.RequestedProcedureID,
            Tag.RequestedProcedureDescription,
            Tag.RequestedProcedureCodeSequence,
            Tag.ProcedureStepProgressInformationSequence,
            Tag.UnifiedProcedureStepPerformedProcedureSequence
    };

    public Dcm2UpsExporter(ExporterDescriptor descriptor, Device device, QueryService queryService,
                           IWebApplicationCache webAppCache, AccessTokenRequestor accessTokenRequestor) {
        super(descriptor);
        this.destWebAppName = descriptor.getExportURI().getSchemeSpecificPart();
        this.device = device;
        this.queryService = queryService;
        this.webAppCache = webAppCache;
        this.accessTokenRequestor = accessTokenRequestor;
        this.procedureStepLabel = AttributesFormat.valueOf(descriptor.getProperty(
                "ProcedureStepLabel", DEFAULT_PROCEDURE_STEP_LABEL));
        this.copyPatient = Boolean.parseBoolean(descriptor.getProperty("patient", "false"));
        this.copyVisit = Boolean.parseBoolean(descriptor.getProperty("visit", "false"));
        this.copyRequest = Boolean.parseBoolean(descriptor.getProperty("request", "false"));
    }

    @Override
    public Outcome export(ExportContext ctx) throws Exception {
        WebApplication destWebApp = webAppCache.findWebApplication(destWebAppName);
        if (!destWebApp.containsServiceClass(WebApplication.ServiceClass.UPS_RS))
            return new Outcome(Task.Status.WARNING,
                    "Destination webapp " + destWebAppName + " is not configured for UPS_RS web service");

        try {
            setInputReadinessState(descriptor.getProperty("InputReadinessState", DEFAULT_INPUT_READINESS_STATE));
            setUPSPriority(descriptor.getProperty("ScheduledProcedureStepPriority", DEFAULT_SCHEDULED_PROCEDURE_STEP_PRIORITY));
        } catch (IllegalArgumentException e) {
            return new Outcome(Task.Status.WARNING, e.getMessage());
        }

        String url = destWebApp.getServiceURL().append("/workitems").toString();
        ResteasyClient client = accessTokenRequestor.resteasyClientBuilder(url, destWebApp).build();
        WebTarget target = client.target(url);
        Invocation.Builder request = target.request();
        String token = authorization(destWebApp);
        if (token != null)
            request.header("Authorization", token);

        ApplicationEntity ae = device.getApplicationEntity(descriptor.getAETitle(), true);

        Attributes upsInfo = queryService.createUPSInfo(
                ae, ctx.getStudyInstanceUID(), ctx.getSeriesInstanceUID(), ctx.getSopInstanceUID(), descriptor);

        return outcome(request.post(EntityType.valueOf(mediaType(destWebApp.getProperties().get("content-type")))
                .entity(upsAttrs(upsInfo))));
    }

    public String getInputReadinessState() {
        return inputReadinessState;
    }

    public void setInputReadinessState(String inputReadinessState) {
        this.inputReadinessState = InputReadinessState.valueOf(inputReadinessState).name();
    }

    public String getUPSPriority() {
        return upsPriority;
    }

    public void setUPSPriority(String upsPriority) {
        this.upsPriority = UPSPriority.valueOf(upsPriority).name();
    }

    private Attributes upsAttrs(Attributes attrs) {
        Attributes upsAttrs = new Attributes();
        upsAttrs.setString(Tag.SpecificCharacterSet, VR.CS, attrs.getString(Tag.SpecificCharacterSet));
        copyFromEntity(upsAttrs, attrs, copyPatient, PATIENT_ATTR);
        copyFromEntity(upsAttrs, attrs, copyVisit, VISIT_ATTR);
        if (copyRequest) {
            Attributes refReq = new Attributes();
            copyFromEntity(refReq, attrs, true, REQUEST_ATTR);
            upsAttrs.newSequence(Tag.ReferencedRequestSequence, 1).add(refReq);
        }
        upsAttrs.setString(Tag.InputReadinessState, VR.CS, getInputReadinessState());
        upsAttrs.setString(Tag.ScheduledProcedureStepPriority, VR.CS, getUPSPriority());
        upsAttrs.setString(Tag.ProcedureStepLabel, VR.LO, format(procedureStepLabel, attrs));
        includeInputInformation(upsAttrs, attrs);
        upsAttrs.setDate(Tag.ScheduledProcedureStepStartDateTime, VR.DT, new Date());
        upsAttrs.setString(Tag.ProcedureStepState, VR.CS, UPSState.SCHEDULED.name());
        addType2Attrs(upsAttrs, TYPE2_ATTR, attrs);
        return upsAttrs;
    }

    private static String format(AttributesFormat format, Attributes attrs) {
        return format != null ? StringUtils.nullify(format.format(attrs), "null") : null;
    }

    private void includeInputInformation(Attributes upsAttrs, Attributes attrs) {
        Sequence refSeriesSeq = attrs.getSequence(Tag.ReferencedSeriesSequence);
        String studyIUID = attrs.getString(Tag.StudyInstanceUID);
        Sequence inputInfoSeq = upsAttrs.newSequence(Tag.InputInformationSequence, refSeriesSeq.size());
        refSeriesSeq.forEach(refSeries -> inputInfoSeq.add(addInputInfo(refSeries, studyIUID)));
    }

    private Attributes addInputInfo(Attributes refSeries, String studyIUID) {
        Attributes inputInfo = new Attributes();
        inputInfo.setString(Tag.TypeOfInstances, VR.CS, "DICOM");
        inputInfo.setString(Tag.StudyInstanceUID, VR.UI, studyIUID);
        inputInfo.setString(Tag.SeriesInstanceUID, VR.UI, refSeries.getString(Tag.SeriesInstanceUID));
        inputInfo.newSequence(Tag.DICOMRetrievalSequence, 1)
                .add(retrieveAETItem(refSeries.getNestedDataset(Tag.ReferencedSOPSequence)));
        Sequence srcRefSOPSeq = refSeries.getSequence(Tag.ReferencedSOPSequence);
        Sequence refSOPSeq = inputInfo.newSequence(Tag.ReferencedSOPSequence, srcRefSOPSeq.size());
        srcRefSOPSeq.forEach(srcRefSOP -> refSOPSeq.add(addRefSOP(srcRefSOP)));
        return inputInfo;
    }

    private Attributes addRefSOP(Attributes srcRefSOP) {
        Attributes refSOP = new Attributes(2);
        refSOP.setString(Tag.ReferencedSOPClassUID, VR.UI, srcRefSOP.getString(Tag.ReferencedSOPClassUID));
        refSOP.setString(Tag.ReferencedSOPInstanceUID, VR.UI, srcRefSOP.getString(Tag.ReferencedSOPInstanceUID));
        return refSOP;
    }

    private Attributes retrieveAETItem(Attributes refSop) {
        Attributes item = new Attributes(1);
        item.setString(Tag.RetrieveAETitle, VR.AE, refSop.getString(Tag.RetrieveAETitle));
        return item;
    }

    private void copyFromEntity(Attributes upsAttrs, Attributes attrs, boolean copyEntity, int[] tags) {
        if (!copyEntity) {
            addType2Attrs(upsAttrs, tags, attrs);
            return;
        }

        for (int tag : tags)
            copyFromEntity(upsAttrs, attrs, tag);
    }

    private void addType2Attrs(Attributes upsAttrs, int[] tags, Attributes attrs) {
        for (int tag : tags) {
            VR vr = DICT.vrOf(tag);
            if (vr == VR.SQ)
                upsAttrs.setNull(tag, vr);
            else
                upsAttrs.setString(tag, vr, formatFromPropsOrNull(tag, attrs));
        }
    }

    private void copyFromEntity(Attributes upsAttrs, Attributes attrs, int tag) {
        VR vr = DICT.vrOf(tag);
        if (vr == VR.SQ)
            setNullOrNestedVal(upsAttrs, attrs, tag);
        else {
            String val = attrs.getString(tag);
            upsAttrs.setString(tag, vr, val == null ? formatFromPropsOrNull(tag, attrs) : val);
        }
    }

    private String formatFromPropsOrNull(int tag, Attributes attrs) {
        String keyword = DICT.keywordOf(tag);
        Map<String, String> properties = descriptor.getProperties();
        return properties.containsKey(keyword) ? format(new AttributesFormat(properties.get(keyword)), attrs) : null;
    }

    private void setNullOrNestedVal(Attributes upsAttrs, Attributes attrs, int tag) {
        Attributes sqItem = attrs.getNestedDataset(tag);
        if (sqItem == null)
            upsAttrs.setNull(tag, VR.SQ);
        else
            upsAttrs.newSequence(tag, 1).add(new Attributes(sqItem));
    }

    private MediaType mediaType(String contentType) {
        MediaType mediaType = DEFAULT_CONTENT_TYPE;
        if (contentType == null)
            return mediaType;

        if (contentType.equals("xml"))
            mediaType = MediaTypes.APPLICATION_DICOM_XML_TYPE;
        else
            LOG.info("Invalid Content type configured on web application [name={}, content-type={}]. Fallback to application/dicom+json",
                    destWebAppName, contentType);

        return mediaType;
    }

    private Outcome outcome(Response rsp) {
        int status = rsp.getStatus();
        if (status == Response.Status.CREATED.getStatusCode())
            return new Outcome(Task.Status.COMPLETED,
                    "UPS created at : " + rsp.getHeaderString("Location"));

        return new Outcome(Task.Status.WARNING,
                "UPS creation unsuccessful : " + rsp.getHeaderString("Warning"));
    }

    private String authorization(WebApplication destWebApp) throws Exception {
        Map<String, String> properties = destWebApp.getProperties();
        return destWebApp.getKeycloakClientID() != null
                ? "Bearer " + accessTokenRequestor.getAccessToken2(destWebApp).getToken()
                : properties.containsKey("bearer-token")
                ? "Bearer " + properties.get("bearer-token")
                : properties.containsKey("basic-auth")
                ? "Basic " + encodeBase64(properties.get("basic-auth").getBytes(StandardCharsets.UTF_8))
                : null;
    }

    private String encodeBase64(byte[] b) {
        int len = (b.length * 4 / 3 + 3) & ~3;
        char[] ch = new char[len];
        Base64.encode(b, 0, b.length, ch, 0);
        return new String(ch);
    }

}
