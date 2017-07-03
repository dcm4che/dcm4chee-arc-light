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

import org.dcm4che3.data.UID;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.entity.Location;
import org.dcm4chee.arc.retrieve.*;
import org.dcm4chee.arc.xdsi.*;

import javax.activation.DataHandler;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.jws.WebService;
import javax.servlet.http.HttpServletRequest;
import javax.xml.ws.BindingType;
import javax.xml.ws.soap.Addressing;
import javax.xml.ws.soap.MTOM;
import javax.xml.ws.soap.SOAPBinding;
import java.util.*;

import static org.dcm4chee.arc.xdsi.XDSConstants.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Feb 2017
 */
@MTOM
@BindingType(value = SOAPBinding.SOAP12HTTP_MTOM_BINDING)
@Addressing(required=true)
@WebService(endpointInterface="org.dcm4chee.arc.xdsi.ImagingDocumentSourcePortType",
        name="ImagingDocumentSource",
        serviceName="ImagingDocumentSource",
        portName="ImagingDocumentSource_Port_Soap12",
        targetNamespace="urn:ihe:rad:xdsi-b:2009",
        wsdlLocation = "/wsdl/XDS-I.b_ImagingDocumentSource.wsdl")
public class ImageDocumentSource implements ImagingDocumentSourcePortType {

    public static final String DICOM_OBJECT_NOT_FOUND = "DICOM Object not found";
    public static final String NO_ACCEPTABLE_TRANSFER_SYNTAX =
            "DICOM Object not acceptable according provided TransferSyntaxUIDList";

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
        RetrieveDocumentSetResponseType rsp = new RetrieveDocumentSetResponseType();
        RegistryResponseType regRsp = new RegistryResponseType();
        rsp.setRegistryResponse(regRsp);
        DocumentRequests docReqs = new DocumentRequests(req.getStudyRequest());
        RetrieveContext ctx = retrieveService.newRetrieveContextXDSI(request, getLocalAET(),
                docReqs.studyUIDs, docReqs.seriesUIDs, docReqs.objectUIDs);
        List<String> tsuids = req.getTransferSyntaxUIDList().getTransferSyntaxUID();
        if (calculateMatches(ctx, docReqs, regRsp, tsuids)) {
            retrieveStart.fire(ctx);
            DicomDataHandler dh = null;
            for (InstanceLocations match : ctx.getMatches()) {
                dh = new DicomDataHandler(ctx, match, tsuids);
                rsp.getDocumentResponse().add(
                        createDocumentResponse(docReqs.get(match.getSopInstanceUID()), dh));
            }
            if (dh != null)
                dh.setRetrieveEnd(retrieveEnd);
        }
        regRsp.setStatus(regRsp.getRegistryErrorList() == null ? XDS_STATUS_SUCCESS
                : rsp.getDocumentResponse().isEmpty() ? XDS_STATUS_FAILURE
                : XDS_STATUS_PARTIAL_SUCCESS);
        return rsp;
    }

    private void addRegisterErrors(
            RegistryResponseType regRsp, DocumentRequests docReqs, String errorCode, String codeContext) {
        List<RegistryError> errors = errors(regRsp);
        for (String iuid : docReqs.keySet()) {
            errors.add(createRegistryError(errorCode, codeContext, XDS_ERR_SEVERITY_ERROR, iuid));
        }
    }

    private boolean calculateMatches(
            RetrieveContext ctx, DocumentRequests docReqs, RegistryResponseType regRsp, List<String> tsuids) {
        try {
            retrieveService.calculateMatches(ctx);
        } catch (DicomServiceException e) {
            addRegisterErrors(regRsp, docReqs, XDS_ERR_REPOSITORY_ERROR, e.getMessage());
            return false;
        }
        List<InstanceLocations> matches = ctx.getMatches();
        HashSet<String> iuids = new HashSet<>(docReqs.keySet());
        Iterator<InstanceLocations> iter = matches.iterator();
        while (iter.hasNext()) {
            InstanceLocations match = iter.next();
            if (!iuids.remove(match.getSopInstanceUID()) || !validateTransferSyntax(match, regRsp, tsuids))
                iter.remove();
        }
        for (String iuid : iuids) {
            errors(regRsp).add(createRegistryError(
                    XDS_ERR_MISSING_DOCUMENT, DICOM_OBJECT_NOT_FOUND, XDS_ERR_SEVERITY_ERROR,
                    iuid));
        }
        ctx.setNumberOfMatches(matches.size());
        return !matches.isEmpty();
    }

    private boolean validateTransferSyntax(InstanceLocations match, RegistryResponseType regRsp, List<String> tsuids) {
        for (Location location : match.getLocations()) {
            if (tsuids.contains(location.getTransferSyntaxUID()))
                return true;
        }
        if (tsuids.contains(UID.ExplicitVRLittleEndian) || tsuids.contains(UID.ImplicitVRLittleEndian))
            return true;

        errors(regRsp).add(createRegistryError(
                XDS_ERR_MISSING_DOCUMENT, NO_ACCEPTABLE_TRANSFER_SYNTAX, XDS_ERR_SEVERITY_ERROR,
                match.getSopInstanceUID()));
        return false;
    }

    private RegistryError createRegistryError(
            String errorCode, String codeContext, String severity, String location) {
        RegistryError error = new RegistryError();
        error.setErrorCode(errorCode);
        error.setCodeContext(codeContext);
        error.setSeverity(severity);
        error.setLocation(location);
        return error;
    }

    private List<RegistryError> errors(RegistryResponseType regRsp) {
        RegistryErrorList errorList = regRsp.getRegistryErrorList();
        if (errorList == null)
            regRsp.setRegistryErrorList(errorList = new RegistryErrorList());
        return errorList.getRegistryError();
    }

    private String getLocalAET() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        return arcDev.getXDSiImagingDocumentSourceAETitle();
    }

    private RetrieveDocumentSetResponseType.DocumentResponse createDocumentResponse(
            RetrieveDocumentSetRequestType.DocumentRequest docReq, DataHandler dh) {
        RetrieveDocumentSetResponseType.DocumentResponse docRsp =
                new RetrieveDocumentSetResponseType.DocumentResponse();
        docRsp.setDocument(dh);
        docRsp.setDocumentUniqueId(docReq.getDocumentUniqueId());
        docRsp.setHomeCommunityId(docReq.getHomeCommunityId());
        docRsp.setMimeType(MediaTypes.APPLICATION_DICOM);
        docRsp.setRepositoryUniqueId(docReq.getRepositoryUniqueId());
        return docRsp;
    }

    private static class DocumentRequests extends HashMap<String,RetrieveDocumentSetRequestType.DocumentRequest> {
        final List<String> studyUIDs = new ArrayList<>();
        final List<String> seriesUIDs = new ArrayList<>();
        final List<String> objectUIDs = new ArrayList<>();

        public DocumentRequests(List<RetrieveImagingDocumentSetRequestType.StudyRequest> studyReqs) {
            for (RetrieveImagingDocumentSetRequestType.StudyRequest studyReq : studyReqs) {
                studyUIDs.add(studyReq.getStudyInstanceUID());
                for (RetrieveImagingDocumentSetRequestType.StudyRequest.SeriesRequest seriesReq
                        : studyReq.getSeriesRequest()) {
                    seriesUIDs.add(seriesReq.getSeriesInstanceUID());
                    for (RetrieveDocumentSetRequestType.DocumentRequest docReq : seriesReq.getDocumentRequest()) {
                        String documentUniqueId = docReq.getDocumentUniqueId();
                        objectUIDs.add(documentUniqueId);
                        put(documentUniqueId, docReq);
                    }
                }
            }
        }
    }
}
