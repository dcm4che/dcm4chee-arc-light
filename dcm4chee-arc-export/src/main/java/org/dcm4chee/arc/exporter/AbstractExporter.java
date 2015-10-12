package org.dcm4chee.arc.exporter;

import org.dcm4chee.arc.conf.ExporterDescriptor;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
public abstract class AbstractExporter implements Exporter {

    protected final ExporterDescriptor descriptor;

    protected AbstractExporter(ExporterDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    @Override
    public ExporterDescriptor getExporterDescriptor() {
        return descriptor;
    }

    @Override
    public ExportContext createExportContext() {
        return new DefaultExportContext(this);
    }
}
