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

package org.dcm4chee.arc.impax.report;

import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.transport.http.HTTPConduit;
import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.net.Device;
import org.dcm4che3.util.StreamUtils;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Holder;
import javax.xml.ws.WebServiceException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jun 2018
 */
@ApplicationScoped
public class ReportServiceProvider {

    @Inject
    private Device device;

    private ReportService service = new ReportService();

    public List<String> queryReportByStudyUid(String studyIUID) throws ConfigurationException {
        Map<String, String> props = device.getDeviceExtension(ArchiveDeviceExtension.class)
                .getImpaxReportProperties();
        String wget = props.get("wget");
        if (wget != null) {
            return Collections.singletonList(wget(wget.replace("{}",studyIUID)));
        }
        Holder<Boolean> result = new Holder<>();
        Holder<ArrayOfString> xmlReports = new Holder<>();
        port(props).queryReportByStudyUid(studyIUID, result, xmlReports);
        return result.value ? xmlReports.value.getString() : Collections.EMPTY_LIST;
    }

    private String wget(String url) {
        try (InputStream in = new URL(url).openStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            StreamUtils.copy(in, out);
            return new String(out.toByteArray(), "UTF-8");
        } catch (Exception e) {
            throw new WebServiceException("Failed to fetch report from " + url, e);
        }
    }

    private ReportServicePortType port(Map<String, String> props) throws ConfigurationException {
        String endpoint = props.get("endpoint");
        if (endpoint == null)
            throw new ConfigurationException("Missing ImpaxReportProperty endpoint");

        ReportServicePortType port = service.getReportServicePort();
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
}
