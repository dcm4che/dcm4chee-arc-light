/*
 * ** BEGIN LICENSE BLOCK *****
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
 * Portions created by the Initial Developer are Copyright (C) 2016
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
 * ** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.retrieve.xdsi;

import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.util.UIDUtils;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.entity.Location;
import org.dcm4chee.arc.retrieve.*;
import org.dcm4chee.arc.store.InstanceLocations;
import org.dcm4che3.xdsi.*;
import org.dcm4che3.xdsi.RetrieveDocumentSetRequestType.DocumentRequest;
import org.dcm4che3.xdsi.RetrieveDocumentSetResponseType.DocumentResponse;
import org.dcm4che3.xdsi.RetrieveRenderedImagingDocumentSetRequestType.StudyRequest.SeriesRequest.RenderedDocumentRequest;
import org.dcm4che3.xdsi.RetrieveRenderedImagingDocumentSetResponseType.RenderedDocumentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.activation.DataHandler;
import javax.enterprise.event.Event;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import javax.inject.Inject;
import javax.jws.WebService;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.xml.ws.BindingType;
import javax.xml.ws.soap.Addressing;
import javax.xml.ws.soap.MTOM;
import javax.xml.ws.soap.SOAPBinding;
import java.util.*;
import java.util.function.IntPredicate;

import static org.dcm4che3.xdsi.XDSConstants.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Feb 2017
 */
@MTOM
@BindingType(value = SOAPBinding.SOAP12HTTP_MTOM_BINDING)
@Addressing(required=true)
@WebService(endpointInterface="org.dcm4che3.xdsi.ImagingDocumentSourcePortType",
        name="ImagingDocumentSource",
        serviceName="ImagingDocumentSource",
        portName="ImagingDocumentSource_Port_Soap12",
        targetNamespace="urn:ihe:rad:xdsi-b:2009",
        wsdlLocation = "/wsdl/XDS-I.b_ImagingDocumentSource.wsdl")
public class ImageDocumentSource implements ImagingDocumentSourcePortType {

    private static final Logger LOG = LoggerFactory.getLogger(ImageDocumentSource.class);

    public static final String UNSUPPORTED_ANON_ERR_CODE = "urn:dicom:wado:0002";
    public static final String NO_ACCEPTABLE_ERR_CODE = "urn:dicom:wado:0006";
    public static final String NOT_PROVIDED_ERR_CODE = "urn:dicom:wado:0007";
    public static final String MF_NOT_AVAILABLE_AS_SF_ERR_CODE = "urn:dicom:wado:0008";
    public static final String INVALID_PARAM_ERR_CODE = "urn:dicom:wado:0012";
    public static final String UNSUPPORTED_PARAM_ERR_CODE = "urn:dicom:wado:0013";
    public static final String PROCESSING_FAILURE_ERR_CODE = "urn:dicom:wado:0014";
    public static final String OUT_OF_RANGE_FRAME_NUMBER_ERR_CODE = "urn:dicom:wado:0018";

    public static final String DICOM_OBJECT_NOT_FOUND = "DICOM Object not found";
    public static final String UNSUPPORTED_ANON = "Web Server does not support anonymization";
    public static final String NO_ACCEPTABLE_CONTENT_TYPE =
            "Web Server does not support the requested content type";
    public static final String NOT_PROVIDED_TRANSFER_SYNTAX =
            "The requested instance(s) cannot be provided in the requested transfer syntax.";
    public static final String NOT_PROVIDED_CONTENT_TYPE =
            "The requested instance(s) cannot be provided in the requested content type";
    public static final String MF_NOT_AVAILABLE_AS_SF =
            "Single image format is not available for multi-frame images";
    public static final String INVALID_PARAM = "Invalid parameter value in request: ";
    public static final String UNSUPPORTED_PARAM = "Unsupported parameter in request: ";
    public static final String OUT_OF_RANGE_FRAME_NUMBER = "Out of range Frame number";
    public static final String WC_REQUIRED_IF_WW = "WindowCenter is required if WindowWidth is present";
    public static final String WW_REQUIRED_IF_WC = "WindowWidth is required if WindowCenter is present";

    @Inject
    private RetrieveService retrieveService;

    @Inject
    private Device device;

    @Inject
    private HttpServletRequest request;

    @Inject @RetrieveStart
    private Event<RetrieveContext> retrieveStart;

    @Inject @RetrieveEnd
    private Event<RetrieveContext> retrieveEnd;

    @Override
    public RetrieveDocumentSetResponseType imagingDocumentSourceRetrieveImagingDocumentSet(
            RetrieveImagingDocumentSetRequestType req) {
        log(req);
        RetrieveDocumentSetResponseType rsp = new RetrieveDocumentSetResponseType();
        RegistryResponseType regRsp = new RegistryResponseType();
        rsp.setRegistryResponse(regRsp);
        MultivaluedHashMap<String, DocumentRequest> map = new MultivaluedHashMap<>();
        RetrieveContext ctx = newRetrieveContextXDSI(req, map);
        List<String> tsuids = req.getTransferSyntaxUIDList().getTransferSyntaxUID();
        if (calculateMatches(ctx, regRsp, map.keySet(), tsuids)) {
            retrieveStart.fire(ctx);
            DicomDataHandler dh = null;
            for (InstanceLocations match : ctx.getMatches()) {
                if (!ctx.copyToRetrieveCache(match)) {
                    dh = new DicomDataHandler(ctx, match, tsuids);
                    for (DocumentRequest docReq
                            : map.get(match.getSopInstanceUID())) {
                            rsp.getDocumentResponse().add(createDocumentResponse(docReq, dh));
                    }
                }
            }
            ctx.copyToRetrieveCache(null);
            InstanceLocations match;
            while ((match = ctx.copiedToRetrieveCache()) != null) {
                dh = new DicomDataHandler(ctx, match, tsuids);
                for (DocumentRequest docReq : map.get(match.getSopInstanceUID())) {
                    rsp.getDocumentResponse().add(createDocumentResponse(docReq, dh));
                }
            }
            if (dh != null)
                dh.setRetrieveEnd(retrieveEnd);
        }
        regRsp.setStatus(regRsp.getRegistryErrorList() == null ? XDS_STATUS_SUCCESS
                : rsp.getDocumentResponse().isEmpty() ? XDS_STATUS_FAILURE
                : XDS_STATUS_PARTIAL_SUCCESS);
        log(rsp);
        return rsp;
    }

    @Override
    public RetrieveRenderedImagingDocumentSetResponseType imagingDocumentSourceRetrieveRenderedImagingDocumentSet(
            RetrieveRenderedImagingDocumentSetRequestType req) {
        log(req);
        RetrieveRenderedImagingDocumentSetResponseType rsp = new RetrieveRenderedImagingDocumentSetResponseType();
        RegistryResponseType regRsp = new RegistryResponseType();
        rsp.setRegistryResponse(regRsp);
        MultivaluedHashMap<String, RenderedDocumentRequest> map = new MultivaluedHashMap<>();
        RetrieveContext ctx = newRetrieveContextXDSI(req, map);
        if (calculateMatches(ctx, regRsp, map)) {
            retrieveStart.fire(ctx);
            RenderedImageDataHandler dh = null;
            ImageReader imageReader = getDicomImageReader();
            ImageWriter imageWriter = getImageWriter(MediaTypes.IMAGE_JPEG_TYPE);
            for (InstanceLocations match : ctx.getMatches()) {
                if (!ctx.copyToRetrieveCache(match)) {
                    for (RenderedDocumentRequest docReq : map.get(match.getSopInstanceUID())) {
                        dh = new RenderedImageDataHandler(ctx, match, docReq, imageReader, imageWriter);
                        rsp.getRenderedDocumentResponse().add(createRenderedDocumentResponse(docReq, dh));
                    }
                }
            }
            ctx.copyToRetrieveCache(null);
            InstanceLocations match;
            while ((match = ctx.copiedToRetrieveCache()) != null) {
                for (RenderedDocumentRequest docReq : map.get(match.getSopInstanceUID())) {
                    dh = new RenderedImageDataHandler(ctx, match, docReq, imageReader, imageWriter);
                    rsp.getRenderedDocumentResponse().add(createRenderedDocumentResponse(docReq, dh));
                }
            }
            if (dh != null)
                dh.setRetrieveEnd(retrieveEnd);
        }
        regRsp.setStatus(regRsp.getRegistryErrorList() == null ? XDS_STATUS_SUCCESS
                : rsp.getRenderedDocumentResponse().isEmpty() ? XDS_STATUS_FAILURE
                : XDS_STATUS_PARTIAL_SUCCESS);
        log(rsp);
        return rsp;
    }

    private void log(RetrieveImagingDocumentSetRequestType req) {
        logRequest(new Object(){
            @Override
            public String toString() {
                StringBuilder sb = new StringBuilder(256).append("RetrieveImagingDocumentSetRequest:");
                for (RetrieveImagingDocumentSetRequestType.StudyRequest study : req.getStudyRequest()) {
                    sb.append(StringUtils.LINE_SEPARATOR).append("   Study[uid=")
                            .append(study.getStudyInstanceUID())
                            .append("]:");
                    for (RetrieveImagingDocumentSetRequestType.StudyRequest.SeriesRequest series :
                            study.getSeriesRequest()) {
                        sb.append(StringUtils.LINE_SEPARATOR).append("      Series[uid=")
                                .append(series.getSeriesInstanceUID())
                                .append("]:");
                        for (DocumentRequest doc : series.getDocumentRequest()) {
                            sb.append(StringUtils.LINE_SEPARATOR).append("         Document[uid=")
                                    .append(doc.getDocumentUniqueId())
                                    .append("]");
                        }
                    }
                }
                for (String s : req.getTransferSyntaxUIDList().getTransferSyntaxUID()) {
                    sb.append(StringUtils.LINE_SEPARATOR).append("   TransferSyntax[");
                    UIDUtils.promptTo(s, sb).append(']');
                }
                return sb.toString();
            }
        });
    }

    private void log(RetrieveDocumentSetResponseType rsp) {
        logResponse(new Object(){
            @Override
            public String toString() {
                StringBuilder sb = new StringBuilder(256).append("RetrieveDocumentSetResponse");
                appendTo(rsp.getRegistryResponse(), sb);
                for (DocumentResponse doc : rsp.getDocumentResponse()) {
                    sb.append(StringUtils.LINE_SEPARATOR)
                            .append("   Document[uid=").append(doc.getDocumentUniqueId())
                            .append(", type=").append(doc.getMimeType())
                            .append(']');
                }
                return sb.toString();
            }
        });
    }

    private void log(RetrieveRenderedImagingDocumentSetRequestType req) {
        logRequest(new Object(){
            @Override
            public String toString() {
                StringBuilder sb = new StringBuilder(256).append("RetrieveRenderedImagingDocumentSetRequest:");
                for (RetrieveRenderedImagingDocumentSetRequestType.StudyRequest study : req.getStudyRequest()) {
                    sb.append(StringUtils.LINE_SEPARATOR)
                            .append("   Study[uid=").append(study.getStudyInstanceUID()).append("]:");
                    for (RetrieveRenderedImagingDocumentSetRequestType.StudyRequest.SeriesRequest series :
                            study.getSeriesRequest()) {
                        sb.append(StringUtils.LINE_SEPARATOR)
                                .append("      Series[uid=").append(series.getSeriesInstanceUID()).append("]:");
                        for (RenderedDocumentRequest doc : series.getRenderedDocumentRequest()) {
                            sb.append(StringUtils.LINE_SEPARATOR)
                                    .append("         Document[uid=").append(doc.getDocumentUniqueId())
                                    .append(", type=").append(doc.getContentTypeList().getContentType());
                            appendNotNullTo(", rows=", doc.getRows(), sb);
                            appendNotNullTo(", cols=", doc.getColumns(), sb);
                            appendNotNullTo(", center=", doc.getWindowCenter(), sb);
                            appendNotNullTo(", width=", doc.getWindowWidth(), sb);
                            appendNotNullTo(", quality=", doc.getImageQuality(), sb);
                            appendNotNullTo(", frame=", doc.getFrameNumber(), sb);
                            sb.append("]");
                        }
                    }
                }
                return sb.toString();
            }
        });
    }

    private void log(RetrieveRenderedImagingDocumentSetResponseType rsp) {
        logResponse(new Object(){
            @Override
            public String toString() {
                StringBuilder sb = new StringBuilder(256).append("RetrieveRenderedImagingDocumentSetResponse");
                appendTo(rsp.getRegistryResponse(), sb);
                for (RenderedDocumentResponse doc : rsp.getRenderedDocumentResponse()) {
                    sb.append(StringUtils.LINE_SEPARATOR)
                            .append("   Document[uid=").append(doc.getSourceDocumentUniqueId())
                            .append(", type=").append(doc.getMimeType())
                            .append(']');
                }
                return sb.append(']').toString();
            }
        });
    }

    private void logRequest(Object rq) {
        LOG.info("{}@{}->{} >> {}", request.getRemoteUser(), request.getRemoteHost(), request.getRequestURI(), rq);
    }

    private void logResponse(Object rsp) {
        LOG.info("{}@{}->{} << {}", request.getRemoteUser(), request.getRemoteHost(), request.getRequestURI(), rsp);
    }


    private static void appendNotNullTo(String prompt, String val, StringBuilder sb) {
        if (val != null)
            sb.append(prompt).append(val);
    }

    private static void appendTo(RegistryResponseType regRsp, StringBuilder sb) {
        sb.append("[").append(regRsp.getStatus()).append("]:");
        RegistryErrorList registryErrorList = regRsp.getRegistryErrorList();
        if (registryErrorList != null) {
            for (RegistryError error : registryErrorList.getRegistryError()) {
                sb.append(StringUtils.LINE_SEPARATOR).append("   Error[code=").append(error.getErrorCode())
                        .append(", context=").append(error.getCodeContext())
                        .append(", severity=").append(error.getSeverity())
                        .append(", location=").append(error.getLocation())
                        .append(']');
            }
        }
    }

    private static ImageReader getDicomImageReader() {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("DICOM");
        if (!readers.hasNext()) {
            ImageIO.scanForPlugins();
            readers = ImageIO.getImageReadersByFormatName("DICOM");
            if (!readers.hasNext())
                throw new RuntimeException("DICOM Image Reader not registered");
        }
        return readers.next();
    }

    private static ImageWriter getImageWriter(MediaType mimeType) {
        String formatName = mimeType.getSubtype();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(formatName);
        if (!writers.hasNext())
            throw new RuntimeException(formatName + " Image Writer not registered");

        return writers.next();
    }

    private RetrieveContext newRetrieveContextXDSI(
            RetrieveImagingDocumentSetRequestType req,
            MultivaluedHashMap<String, DocumentRequest> map) {
        List<RetrieveImagingDocumentSetRequestType.StudyRequest> studyRequest = req.getStudyRequest();
        String[] studyUIDs = new String[studyRequest.size()];
        String[] seriesUIDs = studyUIDs.length == 1
                ? new String[studyRequest.get(0).getSeriesRequest().size()]
                : null;

        int i = 0;
        for (RetrieveImagingDocumentSetRequestType.StudyRequest studyRequest1 : studyRequest) {
            studyUIDs[i++] = studyRequest1.getStudyInstanceUID();
            int j = 0;
            for (RetrieveImagingDocumentSetRequestType.StudyRequest.SeriesRequest seriesRequest :
                    studyRequest1.getSeriesRequest()) {
                if (seriesUIDs != null)
                    seriesUIDs[j++] = seriesRequest.getSeriesInstanceUID();
                for (DocumentRequest documentRequest
                        : seriesRequest.getDocumentRequest()) {
                    map.computeIfAbsent(documentRequest.getDocumentUniqueId(), key -> new ArrayList<>())
                            .add(documentRequest);
                }
            }
        }
        String[] objectUIDs = seriesUIDs != null && seriesUIDs.length == 1
                ? map.keySet().toArray(StringUtils.EMPTY_STRING)
                : null;
        return retrieveService.newRetrieveContextXDSI(request, getLocalAET(), studyUIDs, seriesUIDs, objectUIDs);
    }

    private RetrieveContext newRetrieveContextXDSI(
            RetrieveRenderedImagingDocumentSetRequestType req,
            MultivaluedHashMap<String, RenderedDocumentRequest> map) {
        List<RetrieveRenderedImagingDocumentSetRequestType.StudyRequest> studyRequest = req.getStudyRequest();
        String[] studyUIDs = new String[studyRequest.size()];
        String[] seriesUIDs = studyUIDs.length == 1
                ? new String[studyRequest.get(0).getSeriesRequest().size()]
                : null;

        int i = 0;
        for (RetrieveRenderedImagingDocumentSetRequestType.StudyRequest studyRequest1 : studyRequest) {
            studyUIDs[i++] = studyRequest1.getStudyInstanceUID();
            int j = 0;
            for (RetrieveRenderedImagingDocumentSetRequestType.StudyRequest.SeriesRequest seriesRequest :
                    studyRequest1.getSeriesRequest()) {
                if (seriesUIDs != null)
                    seriesUIDs[j++] = seriesRequest.getSeriesInstanceUID();
                for (RenderedDocumentRequest documentRequest
                        : seriesRequest.getRenderedDocumentRequest()) {
                    map.computeIfAbsent(documentRequest.getDocumentUniqueId(), key -> new ArrayList<>())
                            .add(documentRequest);
                }
            }
        }
        String[] objectUIDs = seriesUIDs != null && seriesUIDs.length == 1
                ? map.keySet().toArray(StringUtils.EMPTY_STRING)
                : null;
        return retrieveService.newRetrieveContextXDSI(request, getLocalAET(), studyUIDs, seriesUIDs, objectUIDs);
    }

    private boolean calculateMatches(RetrieveContext ctx, RegistryResponseType regRsp, Set<String> iuids, 
                                     List<String> tsuids) {
        if (!calculateMatches(ctx, regRsp, iuids))
            return false;

        ctx.getMatches().removeIf(match -> !validateTransferSyntax(tsuids, regRsp, match));
        return !ctx.getMatches().isEmpty();
    }

    private boolean calculateMatches(RetrieveContext ctx, RegistryResponseType regRsp, MultivaluedHashMap<String, 
            RenderedDocumentRequest> map) {
        if (!calculateMatches(ctx, regRsp, map.keySet()))
            return false;

        ctx.getMatches().removeIf(match -> map.get(match.getSopInstanceUID())
                .stream().anyMatch(reqDoc -> !validateRenderedDocumentRequest(reqDoc, regRsp, match)));

        return !ctx.getMatches().isEmpty();
    }

    private boolean calculateMatches(RetrieveContext ctx, RegistryResponseType regRsp, Set<String> iuids) {
        try {
            retrieveService.calculateMatches(ctx);
        } catch (DicomServiceException e) {
            addRegisterErrors(regRsp, iuids, PROCESSING_FAILURE_ERR_CODE, e.getMessage());
            return false;
        }

        List<InstanceLocations> matches = ctx.getMatches();
        Set<String> missing = new HashSet<>(iuids);
        matches.removeIf(match -> !missing.remove(match.getSopInstanceUID()));
        for (String iuid : missing) {
            errors(regRsp).add(createRegistryError(
                    XDS_ERR_MISSING_DOCUMENT, DICOM_OBJECT_NOT_FOUND, XDS_ERR_SEVERITY_ERROR,
                    iuid));
        }
        ctx.setNumberOfMatches(matches.size());
        return !matches.isEmpty();
    }

    private boolean validateTransferSyntax(List<String> tsuids, RegistryResponseType regRsp, InstanceLocations match) {
        if (match.getLocations().stream().map(Location::getStoragePath).anyMatch(
                tsuid -> tsuids.contains(tsuid)
                        || (!isVideo(tsuid)
                            && (tsuids.contains(UID.ExplicitVRLittleEndian)
                            || tsuids.contains(UID.ImplicitVRLittleEndian))))) {
            return true;
        }
        errors(regRsp).add(createRegistryError(
                NOT_PROVIDED_ERR_CODE, NOT_PROVIDED_TRANSFER_SYNTAX, XDS_ERR_SEVERITY_ERROR,
                match));
        return false;
    }

    private boolean validateRenderedDocumentRequest(RetrieveRenderedImagingDocumentSetRequestType.StudyRequest
                                                            .SeriesRequest.RenderedDocumentRequest reqDoc, 
                                                    RegistryResponseType regRsp, InstanceLocations match) {
        RegistryError registryError = validateRenderedDocumentRequest(reqDoc, match);
        if (registryError == null) {
            return true;
        }
        errors(regRsp).add(registryError);
        return false;
    }

    private RegistryError validateRenderedDocumentRequest(RenderedDocumentRequest docReq, InstanceLocations match) {

        if (docReq.getAnonymize() != null) {
            return createRegistryError(UNSUPPORTED_ANON_ERR_CODE, UNSUPPORTED_ANON, XDS_ERR_SEVERITY_ERROR, match);
        }

        if (docReq.getAnnotation() != null) {
            return createUnsupportedParamError("Annotation", match);
        }

        if (docReq.getContentTypeList().getContentType().stream().noneMatch(
                accepted -> MediaTypes.IMAGE_JPEG.equalsIgnoreCase(accepted))) {
            return createRegistryError(NO_ACCEPTABLE_ERR_CODE, NO_ACCEPTABLE_CONTENT_TYPE, XDS_ERR_SEVERITY_ERROR, match);
        }

        if (!isImage(match)) {
            return createRegistryError(NOT_PROVIDED_ERR_CODE, NOT_PROVIDED_CONTENT_TYPE, XDS_ERR_SEVERITY_ERROR, match);
        }

        if (docReq.getRegion() != null) {
            return createUnsupportedParamError("Region", match);
        }

        if (docReq.getPresentationUID() != null) {
            return createUnsupportedParamError("PresentationUID", match);
        }

        if (docReq.getPresentationSeriesUID() != null) {
            return createUnsupportedParamError("PresentationSeriesUID", match);
        }

        if (docReq.getImageQuality() != null) {
            try {
                parseInt(docReq.getImageQuality(), i -> i > 0 && i <= 100);
            } catch (IllegalArgumentException e) {
                return createInvalidParamError("ImageQuality", docReq.getImageQuality(), match);
            }
        }

        if (docReq.getRows() != null) {
            try {
                parseInt(docReq.getRows(), i -> i > 0);
            } catch (IllegalArgumentException e) {
                return createInvalidParamError("Rows", docReq.getRows(), match);
            }
        }

        if (docReq.getColumns() != null) {
            try {
                parseInt(docReq.getColumns(), i -> i > 0);
            } catch (IllegalArgumentException e) {
                return createInvalidParamError("Columns", docReq.getColumns(), match);
            }
        }

        if (docReq.getWindowWidth() != null) {
            try {
                parseInt(docReq.getWindowWidth(), i -> i > 0);
            } catch (IllegalArgumentException e) {
                return createInvalidParamError("WindowWidth", docReq.getWindowWidth(), match);
            }
            if (docReq.getWindowCenter() == null) {
                return createRegistryError(INVALID_PARAM_ERR_CODE, WC_REQUIRED_IF_WW, XDS_ERR_SEVERITY_ERROR, match);
            }
        }

        if (docReq.getWindowCenter() != null) {
            try {
                Integer.parseInt(docReq.getWindowCenter());
            } catch (IllegalArgumentException e) {
                return createInvalidParamError("WindowCenter", docReq.getWindowCenter(), match);
            }
            if (docReq.getWindowWidth() == null) {
                return createRegistryError(INVALID_PARAM_ERR_CODE, WW_REQUIRED_IF_WC, XDS_ERR_SEVERITY_ERROR, match);
            }
        }

        int numberOfFrames = match.getAttributes().getInt(Tag.NumberOfFrames, 1);
        if (docReq.getFrameNumber() == null) {
            if (numberOfFrames > 1) {
                return createRegistryError(
                        MF_NOT_AVAILABLE_AS_SF_ERR_CODE, MF_NOT_AVAILABLE_AS_SF, XDS_ERR_SEVERITY_ERROR, match);
            }
        } else {
            try {
                if (numberOfFrames < parseInt(docReq.getFrameNumber(), i -> i > 0)) {
                    return createRegistryError(
                            OUT_OF_RANGE_FRAME_NUMBER_ERR_CODE, OUT_OF_RANGE_FRAME_NUMBER, XDS_ERR_SEVERITY_ERROR, match);
                }
            } catch (IllegalArgumentException e) {
                return createInvalidParamError("FrameNumber", docReq.getFrameNumber(), match);
            }
        }

        return null;
    }

    private static RegistryError createRegistryError(String errorCode, String codeContext, String severity,
                                                     InstanceLocations inst) {
        return createRegistryError(errorCode, codeContext, severity, inst.getSopInstanceUID());
    }

    private static RegistryError createRegistryError(String errorCode, String codeContext, String severity, String iuid) {
        RegistryError error = new RegistryError();
        error.setErrorCode(errorCode);
        error.setCodeContext(codeContext);
        error.setSeverity(severity);
        error.setLocation(iuid);
        return error;
    }

    private static RegistryError createUnsupportedParamError(String paramName, InstanceLocations inst) {
        return createRegistryError(
                UNSUPPORTED_PARAM_ERR_CODE,
                UNSUPPORTED_PARAM + paramName,
                XDS_ERR_SEVERITY_ERROR,
                inst);
    }

    private static RegistryError createInvalidParamError(String paramName, String paramValue, InstanceLocations inst) {
        return createRegistryError(
                INVALID_PARAM_ERR_CODE,
                INVALID_PARAM + paramName + ": " + paramValue,
                XDS_ERR_SEVERITY_ERROR,
                inst);
    }

    private static void addRegisterErrors(RegistryResponseType regRsp, Collection<String> iuids, String errorCode,
                                          String codeContext) {
        List<RegistryError> errors = errors(regRsp);
        for (String iuid : iuids) {
            errors.add(createRegistryError(errorCode, codeContext, XDS_ERR_SEVERITY_ERROR, iuid));
        }
    }

    private static int parseInt(String s, IntPredicate test) {
        int i = Integer.parseInt(s);
        if (!test.test(i))
            throw new IllegalArgumentException(s);

        return i;
    }

    private static boolean isImage(InstanceLocations inst) {
        return inst.getAttributes().contains(Tag.BitsAllocated)
                && !inst.getSopClassUID().equals(UID.RTDoseStorage)
                && !isVideo(inst);
    }

    private static boolean isVideo(InstanceLocations inst) {
        return isVideo(inst.getLocations().get(0).getTransferSyntaxUID());
    }

    private static boolean isVideo(String tsuid) {
        switch (tsuid) {
            case UID.MPEG2:
            case UID.MPEG2MainProfileHighLevel:
            case UID.MPEG4AVCH264HighProfileLevel41:
            case UID.MPEG4AVCH264BDCompatibleHighProfileLevel41:
            case UID.MPEG4AVCH264HighProfileLevel42For2DVideo:
            case UID.MPEG4AVCH264HighProfileLevel42For3DVideo:
            case UID.MPEG4AVCH264StereoHighProfileLevel42:
            case UID.HEVCH265MainProfileLevel51:
            case UID.HEVCH265Main10ProfileLevel51:
                return true;
        }
        return false;
    }

    private static List<RegistryError> errors(RegistryResponseType regRsp) {
        RegistryErrorList errorList = regRsp.getRegistryErrorList();
        if (errorList == null)
            regRsp.setRegistryErrorList(errorList = new RegistryErrorList());
        return errorList.getRegistryError();
    }

    private String getLocalAET() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        return arcDev.getXDSiImagingDocumentSourceAETitle();
    }

    private DocumentResponse createDocumentResponse(DocumentRequest docReq, DataHandler dh) {
        DocumentResponse docRsp = new DocumentResponse();
        docRsp.setDocument(dh);
        docRsp.setDocumentUniqueId(docReq.getDocumentUniqueId());
        docRsp.setHomeCommunityId(docReq.getHomeCommunityId());
        docRsp.setMimeType(MediaTypes.APPLICATION_DICOM);
        docRsp.setRepositoryUniqueId(docReq.getRepositoryUniqueId());
        return docRsp;
    }

    private RenderedDocumentResponse createRenderedDocumentResponse(RenderedDocumentRequest docReq, DataHandler dh) {
        RenderedDocumentResponse docRsp = new RenderedDocumentResponse();
        docRsp.setDocument(dh);
        docRsp.setSourceDocumentUniqueId(docReq.getDocumentUniqueId());
        docRsp.setHomeCommunityId(docReq.getHomeCommunityId());
        docRsp.setMimeType(MediaTypes.IMAGE_JPEG);
        docRsp.setRepositoryUniqueId(docReq.getRepositoryUniqueId());
        docRsp.setAnnotation(docReq.getAnnotation());
        docRsp.setRows(docReq.getRows());
        docRsp.setColumns(docReq.getColumns());
        docRsp.setRegion(docReq.getRegion());
        docRsp.setWindowWidth(docReq.getWindowWidth());
        docRsp.setWindowCenter(docReq.getWindowCenter());
        docRsp.setImageQuality(docReq.getImageQuality());
        docRsp.setPresentationUID(docReq.getPresentationUID());
        docRsp.setPresentationSeriesUID(docReq.getPresentationSeriesUID());
        docRsp.setAnonymize(docReq.getAnonymize());
        docRsp.setFrameNumber(docReq.getFrameNumber());
        return docRsp;
    }

}
