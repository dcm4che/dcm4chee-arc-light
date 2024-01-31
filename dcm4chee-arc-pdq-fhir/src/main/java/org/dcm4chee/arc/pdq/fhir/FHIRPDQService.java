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
 *  Java(TM), hosted at https://github.com/dcm4che.
 *
 *  The Initial Developer of the Original Code is
 *  J4Care.
 *  Portions created by the Initial Developer are Copyright (C) 2015-2022
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

package org.dcm4chee.arc.pdq.fhir;

import jakarta.enterprise.event.Event;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.Response;
import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.IWebApplicationCache;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.data.Issuer;
import org.dcm4che3.net.WebApplication;
import org.dcm4che3.util.Base64;
import org.dcm4chee.arc.conf.Entity;
import org.dcm4chee.arc.conf.PDQServiceDescriptor;
import org.dcm4chee.arc.keycloak.AccessTokenRequestor;
import org.dcm4chee.arc.pdq.AbstractPDQService;
import org.dcm4chee.arc.pdq.PDQServiceContext;
import org.dcm4chee.arc.pdq.PDQServiceException;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Aug 2021
 */
public class FHIRPDQService extends AbstractPDQService {
    private static final Logger LOG = LoggerFactory.getLogger(FHIRPDQService.class);
    private static final String FHIR_PAT_2_DCM_XSL = "${jboss.server.temp.url}/dcm4chee-arc/fhir-pat2dcm.xsl";

    private final IWebApplicationCache webAppCache;
    private final AccessTokenRequestor accessTokenRequestor;
    private final Event<PDQServiceContext> pdqEvent;

    public FHIRPDQService(PDQServiceDescriptor descriptor,
                          IWebApplicationCache webAppCache,
                          AccessTokenRequestor accessTokenRequestor,
                          Event<PDQServiceContext> pdqEvent) {
        super(descriptor);
        this.webAppCache = webAppCache;
        this.accessTokenRequestor = accessTokenRequestor;
        this.pdqEvent = pdqEvent;
    }

    @Override
    public Attributes query(PDQServiceContext ctx) throws PDQServiceException {
        try {
            requireQueryEntity(Entity.Patient);
            Attributes demographics = query(ctx, webApp());
            ctx.setPatientAttrs(demographics);
            return demographics;
        } catch (ConfigurationException e) {
            ctx.setException(e);
            throw new PDQServiceException(e);
        } finally {
            pdqEvent.fire(ctx);
        }
    }

    private Attributes query(PDQServiceContext ctx, WebApplication webApp) throws PDQServiceException {
        ctx.setFhirWebAppName(webApp.getApplicationName());
        String authorization;
        try {
            String url = webApp.getServiceURL().toString();
            ResteasyClient client = accessTokenRequestor.resteasyClientBuilder(url, webApp).build();
            ResteasyWebTarget target = client.target(url);
            target = setQueryParameters(target, ctx);
            Invocation.Builder request = target.request();
            setHeaders(request);
            if ((authorization = authorization(webApp)) != null)
                request.header("Authorization", authorization);
            LOG.info("Request invoked is : {}", target.getUri());
            Response response = request.get();
            if (response.getStatus() != Response.Status.OK.getStatusCode())
                return null;

            return SAXTransformer.transform(response,
                                            descriptor.getProperties().getOrDefault("XSLStylesheetURI", FHIR_PAT_2_DCM_XSL),
                                            null);
        } catch (Exception e) {
            LOG.info("Exception caught on querying FHIR Supplier {}", webApp);
            ctx.setException(e);
            throw new PDQServiceException(e);
        }
    }

    private String authorization(WebApplication webApp) throws Exception {
        Map<String, String> props = webApp.getProperties();
        return webApp.getKeycloakClientID() != null
                ? "Bearer " + accessTokenRequestor.getAccessToken2(webApp).getToken()
                : props.containsKey("bearer-token")
                    ? "Bearer " + props.get("bearer-token")
                    : props.containsKey("basic-auth")
                        ? "Basic " + encodeBase64(props.get("basic-auth").getBytes(StandardCharsets.UTF_8))
                        : null;
    }

    private WebApplication webApp() throws ConfigurationException {
        return webAppCache.findWebApplication(
                descriptor.getPDQServiceURI().getSchemeSpecificPart());
    }

    private String identifier(IDWithIssuer pid) {
        String system = systemOf(pid.getIssuer());
        return system != null ? system + '|' + pid.getID() : pid.getID();
    }

    private String systemOf(Issuer issuer) {
        if (issuer != null) {
            String universalEntityID = issuer.getUniversalEntityID();
            String type = issuer.getUniversalEntityIDType();
            if (universalEntityID != null && type != null) {
                String prefix = descriptor.getProperties()
                        .get("search.identifier.system.type." + type);
                if (prefix != null)
                    return prefix.equals("NONE") ? universalEntityID : prefix + universalEntityID;
            }
            String issuerOfPatientID = issuer.getLocalNamespaceEntityID();
            if (issuerOfPatientID != null) {
                String system = descriptor.getProperties()
                        .get("search.identifier.system.issuer." + issuerOfPatientID);
                if (system != null)
                    return system;
            }
        }
        return descriptor.getProperties().get("search.identifier.system");
    }

    private void setHeaders(Invocation.Builder request) {
        descriptor.getProperties().entrySet().stream()
                .filter(e -> e.getKey().startsWith("header."))
                .forEach(e -> request.header(e.getKey().substring(7), e.getValue()));
    }

    private ResteasyWebTarget setQueryParameters(ResteasyWebTarget webTarget, PDQServiceContext ctx) {
        webTarget = webTarget.queryParam("identifier", identifier(ctx.getPatientID()));
        for (Map.Entry<String, String> e : descriptor.getProperties().entrySet())
            if (e.getKey().startsWith("search.")
                    && !e.getKey().startsWith("identifier.", 7))
                webTarget = webTarget.queryParam(e.getKey().substring(7), e.getValue());

        String targetURI = webTarget.getUri().toString();
        LOG.info("Web Target is : {}", targetURI);
        ctx.setFhirQueryParams(targetURI.substring(targetURI.indexOf('?') + 1));
        return webTarget;
    }

    private static String encodeBase64(byte[] b) {
        int len = (b.length * 4 / 3 + 3) & ~3;
        char[] ch = new char[len];
        Base64.encode(b, 0, b.length, ch, 0);
        return new String(ch);
    }

}
