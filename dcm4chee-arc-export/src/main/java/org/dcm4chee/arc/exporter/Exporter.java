package org.dcm4chee.arc.exporter;

import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.qmgt.Outcome;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
public interface Exporter {
    ExporterDescriptor getExporterDescriptor();

    ExportContext createExportContext();

    Outcome export(ExportContext exportContext) throws Exception;
}
