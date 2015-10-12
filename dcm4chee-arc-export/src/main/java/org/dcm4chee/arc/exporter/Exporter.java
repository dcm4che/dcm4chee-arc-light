package org.dcm4chee.arc.exporter;

import org.dcm4chee.arc.conf.ExporterDescriptor;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
public interface Exporter {
    ExporterDescriptor getExporterDescriptor();

    ExportContext createExportContext();

    void export(ExportContext exportContext) throws Exception;
}
