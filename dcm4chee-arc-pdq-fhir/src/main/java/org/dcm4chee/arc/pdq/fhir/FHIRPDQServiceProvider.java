package org.dcm4chee.arc.pdq.fhir;

import org.dcm4che3.conf.api.IWebApplicationCache;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.PDQServiceDescriptor;
import org.dcm4chee.arc.keycloak.AccessTokenRequestor;
import org.dcm4chee.arc.pdq.PDQService;
import org.dcm4chee.arc.pdq.PDQServiceContext;
import org.dcm4chee.arc.pdq.PDQServiceProvider;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.inject.Named;

@ApplicationScoped
@Named("pdq-fhir")
public class FHIRPDQServiceProvider implements PDQServiceProvider {
    @Inject
    private Device device;

    @Inject
    private IWebApplicationCache webAppCache;

    @Inject
    private AccessTokenRequestor accessTokenRequestor;

    @Inject
    private Event<PDQServiceContext> pdqEvent;

    @Override
    public PDQService getPDQService(PDQServiceDescriptor descriptor) {
        return new FHIRPDQService(descriptor, device, webAppCache, accessTokenRequestor, pdqEvent);
    }
}
