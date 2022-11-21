package org.dcm4chee.arc.exporter;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.dcm4chee.arc.NamedCDIBeanCache;
import org.dcm4chee.arc.conf.ExporterDescriptor;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
@ApplicationScoped
public class ExporterFactory {
    @Inject
    private NamedCDIBeanCache namedCDIBeanCache;

    @Inject
    private Instance<ExporterProvider> providers;

    public Exporter getExporter(ExporterDescriptor descriptor) {
        String scheme = descriptor.getExportURI().getScheme();
        ExporterProvider provider = namedCDIBeanCache.get(providers, scheme);
        return provider.getExporter(descriptor);
    }
}
