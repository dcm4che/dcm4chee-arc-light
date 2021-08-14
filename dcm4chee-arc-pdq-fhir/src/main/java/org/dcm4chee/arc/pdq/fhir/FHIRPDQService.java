package org.dcm4chee.arc.pdq.fhir;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.IWebApplicationCache;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.data.Issuer;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.WebApplication;
import org.dcm4chee.arc.conf.Entity;
import org.dcm4chee.arc.conf.PDQServiceDescriptor;
import org.dcm4chee.arc.keycloak.AccessTokenRequestor;
import org.dcm4chee.arc.pdq.AbstractPDQService;
import org.dcm4chee.arc.pdq.PDQServiceContext;
import org.dcm4chee.arc.pdq.PDQServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.event.Event;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;

public class FHIRPDQService extends AbstractPDQService {
    private static final Logger LOG = LoggerFactory.getLogger(FHIRPDQService.class);
    private static final String FHIR_PAT_2_DCM_XSL = "${jboss.server.temp.url}/dcm4chee-arc/fhir-pat2dcm.xsl";

    private final Device device;
    private final IWebApplicationCache webAppCache;
    private final AccessTokenRequestor accessTokenRequestor;
    private final Event<PDQServiceContext> pdqEvent;

    public FHIRPDQService(PDQServiceDescriptor descriptor,
                          Device device,
                          IWebApplicationCache webAppCache,
                          AccessTokenRequestor accessTokenRequestor,
                          Event<PDQServiceContext> pdqEvent) {
        super(descriptor);
        this.device = device;
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
            pdqEvent.fire(ctx);
            return demographics;
        } catch (ConfigurationException e) {
            throw new PDQServiceException(e);
        }
    }

    private Attributes query(PDQServiceContext ctx, WebApplication webApp) {
        //TODO
        return null;
    }

    private String xslStylesheetURI() {
        return descriptor.getProperties().getOrDefault("XSLStylesheetURI", FHIR_PAT_2_DCM_XSL);
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

    private void setQueryParameters(WebTarget webTarget, IDWithIssuer pid) {
        webTarget.queryParam("identifier", identifier(pid));
        descriptor.getProperties().entrySet().stream()
                .filter(e -> e.getKey().startsWith("search.")
                        && !e.getKey().startsWith("identifier.", 7))
                .forEach(e -> webTarget.queryParam(e.getKey().substring(7), e.getValue()));
    }

}
