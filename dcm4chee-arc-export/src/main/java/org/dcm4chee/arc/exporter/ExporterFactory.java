package org.dcm4chee.arc.exporter;

import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.conf.NamedQualifier;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
@ApplicationScoped
public class ExporterFactory {
    @Inject
    private Instance<ExporterProvider> providers;

    public Exporter getExporter(ExporterDescriptor descriptor) {
        String scheme = descriptor.getExportURI().getScheme();
        ExporterProvider provider = providers.select(new NamedQualifier(scheme)).get();
        return provider.getExporter(descriptor);
    }
}
