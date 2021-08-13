package org.dcm4chee.arc.pdq.fhir;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.IWebApplicationCache;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.IDWithIssuer;
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

public class FHIRPDQService extends AbstractPDQService {
    private static final Logger LOG = LoggerFactory.getLogger(FHIRPDQService.class);
    private static final String FHIR_XML_FHIR_VERSION_4_0 = "application/fhir+xml; fhirVersion=4.0";
    private static final String FHIR_PAT_2_DCM_XSL = "${jboss.server.temp.url}/dcm4chee-arc/fhir-pat2dcm.xsl";

    private final Device device;
    private final IWebApplicationCache webAppCache;
    private final AccessTokenRequestor accessTokenRequestor;
    private final Event<PDQServiceContext> pdqEvent;
    private PDQServiceContext pdqServiceCtx;

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
        pdqServiceCtx = ctx;
        Attributes demographics = query(ctx.getPatientID());
        ctx.setPatientAttrs(demographics);
        pdqEvent.fire(ctx);
        return demographics;
    }

    @Override
    public Attributes query(IDWithIssuer pid) throws PDQServiceException {
        try {
            requireQueryEntity(Entity.Patient);
            return query(
                    webAppCache.findWebApplication(descriptor.getPDQServiceURI().getSchemeSpecificPart()),
                    descriptor.getProperties().getOrDefault("Accept", FHIR_XML_FHIR_VERSION_4_0),
                    identifier(pid),
                    descriptor.getProperties().getOrDefault("XSLStylesheetURI", FHIR_PAT_2_DCM_XSL));
        } catch (ConfigurationException e) {
            throw new PDQServiceException(e);
        }
    }

    private Attributes query(WebApplication webApp, String accept, String identifier, String xslStylesheetURI) {
        //TODO
        return null;
    }

    private String identifier(IDWithIssuer pid) {
        String identifierSystem = descriptor.getProperties().get("IdentifierSystem");
        if (identifierSystem == null && pid.getIssuer() != null)
            identifierSystem = identifierSystemOf(
                    pid.getIssuer().getUniversalEntityID(),
                    pid.getIssuer().getUniversalEntityIDType());
        return identifierSystem != null ? identifierSystem + '|' + pid.getID() : pid.getID();
    }

    private static String identifierSystemOf(String id, String type) {
        return id == null ? null
                : "ISO".equals(type) ? "urn:oid:" + id
                : "UUID".equals(type) ? "urn:uuid:" + id
                : "URI".equals(type) ? id
                : null;
    }
}
