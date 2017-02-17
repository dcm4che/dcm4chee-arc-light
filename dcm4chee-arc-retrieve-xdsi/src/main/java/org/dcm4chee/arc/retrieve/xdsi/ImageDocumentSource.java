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
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
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

import com.google.inject.Inject;
import org.dcm4chee.arc.retrieve.RetrieveService;

import javax.jws.WebService;
import javax.xml.ws.BindingType;
import javax.xml.ws.soap.Addressing;
import javax.xml.ws.soap.MTOM;
import javax.xml.ws.soap.SOAPBinding;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Feb 2017
 */
@MTOM
@BindingType(value = SOAPBinding.SOAP12HTTP_MTOM_BINDING)
@Addressing
@WebService(endpointInterface="org.dcm4chee.arc.retrieve.xdsi.ImagingDocumentSourcePortType",
        name="ImagingDocumentSource",
        serviceName="ImagingDocumentSource",
        portName="ImagingDocumentSource_Port_Soap12",
        targetNamespace="urn:ihe:rad:xdsi-b:2009",
        wsdlLocation = "WEB-INF/wsdl/XDS-I.b_ImagingDocumentSource.wsdl")
public class ImageDocumentSource implements ImagingDocumentSourcePortType {

    //Response stati
    public static final String XDS_B_STATUS_SUCCESS = "urn:oasis:names:tc:ebxml-regrep:ResponseStatusType:Success";
    public static final String XDS_B_STATUS_FAILURE = "urn:oasis:names:tc:ebxml-regrep:ResponseStatusType:Failure";
    public static final String XDS_B_STATUS_PARTIAL_SUCCESS = "urn:oasis:names:tc:ebxml-regrep:ResponseStatusType:PartialSuccess";

    @Inject
    private RetrieveService retrieveService;

    @Override
    public RetrieveDocumentSetResponseType imagingDocumentSourceRetrieveImagingDocumentSet(RetrieveImagingDocumentSetRequestType body) {
        RetrieveDocumentSetResponseType rsp = new RetrieveDocumentSetResponseType();
        rsp.setRegistryResponse(createRegistryResponseType(XDS_B_STATUS_SUCCESS));
        return rsp;
    }

    private RegistryResponseType createRegistryResponseType(String status) {
        RegistryResponseType type = new RegistryResponseType();
        type.setStatus(status);
        return type;
    }
}
