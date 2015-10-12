package org.dcm4chee.arc.exporter;

import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.conf.StorageDescriptor;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
public interface ExporterProvider {

    Exporter getExporter(ExporterDescriptor descriptor);
}
