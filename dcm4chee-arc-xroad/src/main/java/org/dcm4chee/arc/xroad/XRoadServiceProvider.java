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

package org.dcm4chee.arc.xroad;

import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.transport.http.HTTPConduit;
import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.dict.archive.PrivateTag;
import org.dcm4che3.net.Device;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Holder;
import java.util.List;
import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Feb 2018
 */
@ApplicationScoped
public class XRoadServiceProvider {

    @Inject
    private Device device;

    private XRoadService service = new XRoadService();

    public Attributes rr441(String endpoint, Map<String, String> props, String patientID)
            throws XRoadException, ConfigurationException {
        Headers h = new Headers(props, "RR441");
        Holder<RR441RequestType> request = new Holder<>(toRR441(props, patientID));
        Holder<RR441ResponseType> response = new Holder<>();
        port(endpoint, props)
                .rr441(request, h.client, h.service, h.userId, h.id, h.protocolVersion, response,  h.requestHash);
        return toAttributes(props, XRoadException.validate(response.value));
    }

    private static RR441RequestType toRR441(Map<String, String> props, String patientID) {
        RR441RequestType rq = new RR441RequestType();
        rq.setCValjad(props.getOrDefault("rr441.cValjad", "1,2,6,7,9,10"));
        rq.setCIsikukoodid(patientID);
        return rq;
    }

    private static Attributes toAttributes(Map<String, String> props, RR441ResponseType rsp) {
        List<RR441ResponseType.TtIsikuid.TtIsikud> ttIsikudList = rsp.getTtIsikuid().getTtIsikud();
        if (ttIsikudList.isEmpty())
            return null;

        RR441ResponseType.TtIsikuid.TtIsikud ttIsikud = ttIsikudList.get(0);
        Attributes attrs = new Attributes();
        attrs.setString(Tag.SpecificCharacterSet, VR.CS,
                props.getOrDefault("SpecificCharacterSet","ISO_IR 100"));
        attrs.setString(Tag.PatientName, VR.PN, patientName(ttIsikud));
        attrs.setString(Tag.PatientID, VR.LO, ttIsikud.getTtIsikudCIsikukood());
        attrs.setString(Tag.PatientSex, VR.CS, patientSex(ttIsikud.getTtIsikudCSugu()));
        attrs.setString(Tag.PatientBirthDate, VR.DA, patientBirthDate(ttIsikud.getTtIsikudCSynniaeg()));
        attrs.setString(PrivateTag.PrivateCreator, PrivateTag.XRoadPersonStatus, VR.CS,
                ttIsikud.getTtIsikudCIsStaatus());
        attrs.setString(PrivateTag.PrivateCreator, PrivateTag.XRoadDataStatus, VR.CS,
                ttIsikud.getTtIsikudCKirjeStaatus());
        return attrs;
    }

    private static String patientName(RR441ResponseType.TtIsikuid.TtIsikud ttIsikud) {
        return ttIsikud.getTtIsikudCPerenimi() + '^' + ttIsikud.getTtIsikudCEesnimi();
    }

    private static String patientBirthDate(String synniaeg) {
        if (synniaeg == null || synniaeg.length() != 10)
            return null;

        char[] data = new char[8];
        synniaeg.getChars(6, 10, data, 0);
        synniaeg.getChars(3, 5, data, 4);
        synniaeg.getChars(0, 2, data, 6);
        return new String(data);
    }

    private static String patientSex(String sugu) {
        if (sugu != null && sugu.length() == 1) {
            switch (sugu.charAt(0)) {
                case 'M':
                    return "M";
                case 'N':
                    return "F";
            }
        }
        return null;
    }

    private XRoadAdapterPortType port(String endpoint, Map<String, String> props) throws ConfigurationException {
        XRoadAdapterPortType port = service.getXRoadServicePort();
        BindingProvider bindingProvider = (BindingProvider) port;
        Map<String, Object> reqCtx = bindingProvider.getRequestContext();
        reqCtx.put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, endpoint);
        if (endpoint.startsWith("https")) {
            Client client = ClientProxy.getClient(port);
            HTTPConduit conduit = (HTTPConduit) client.getConduit();
            conduit.setTlsClientParameters(tlsClientParams(props));
        }
        return port;
    }

    private TLSClientParameters tlsClientParams(Map<String, String> props) throws ConfigurationException {
        TLSClientParameters params = new TLSClientParameters();
        try {
            params.setKeyManagers(device.keyManagers());
            params.setTrustManagers(device.trustManagers());
        } catch (Exception e) {
            throw new ConfigurationException(e);
        }
        params.setSecureSocketProtocol(props.get("TLS.protocol"));
        for (String cipherSuite : StringUtils.split(props.get("TLS.cipherSuites"), ','))
            params.getCipherSuites().add(cipherSuite.trim());
        params.setDisableCNCheck(
                Boolean.parseBoolean(props.getOrDefault("TLS.disableCNCheck", "false")));
        return params;
    }

    private static class Headers {
        final Holder<XRoadClientIdentifierType> client = new Holder<>();
        final Holder<XRoadServiceIdentifierType> service = new Holder<>();
        final Holder<String> userId = new Holder<>();
        final Holder<String> id = new Holder<>();
        final Holder<String> protocolVersion = new Holder<>();
        final Holder<RequestHash> requestHash = new Holder<>();

        private Headers(Map<String, String> props, String serviceCode) {
            client.value = client(props);
            service.value = service(props, serviceCode);
            userId.value = props.getOrDefault("userId", "EE11111111111");
            id.value = props.getOrDefault("id", "");
            protocolVersion.value = props.getOrDefault("protocolVersion", "4.0");
        }

        private static XRoadClientIdentifierType client(Map<String, String> props) {
            XRoadClientIdentifierType type = new XRoadClientIdentifierType();
            type.setObjectType(XRoadObjectType.valueOf(
                    props.getOrDefault("client.objectType", XRoadObjectType.SUBSYSTEM.name())));
            type.setXRoadInstance(props.getOrDefault("client.xRoadInstance", "EE"));
            type.setMemberClass(props.getOrDefault("client.memberClass", "NGO"));
            type.setMemberCode(props.getOrDefault("client.memberCode", "90007945"));
            type.setSubsystemCode(props.getOrDefault("client.subsystemCode", "mia"));
            return type;
        }

        private static XRoadServiceIdentifierType service(Map<String, String> props, String serviceCode) {
            XRoadServiceIdentifierType type = new XRoadServiceIdentifierType();
            type.setObjectType(XRoadObjectType.valueOf(
                    props.getOrDefault("service.objectType", XRoadObjectType.SERVICE.name())));
            type.setXRoadInstance(props.getOrDefault("service.xRoadInstance", "EE"));
            type.setMemberClass(props.getOrDefault("service.memberClass", "GOV"));
            type.setMemberCode(props.getOrDefault("service.memberCode", "70008440"));
            type.setSubsystemCode(props.getOrDefault("service.subsystemCode", "rr"));
            type.setServiceCode(serviceCode);
            type.setServiceVersion(props.getOrDefault("serviceVersion", "v1"));
            return type;
        }
    }
}
