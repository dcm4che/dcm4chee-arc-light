package org.dcm4chee.arc.export.dicom;

import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.exporter.Exporter;
import org.dcm4chee.arc.exporter.ExporterProvider;
import org.dcm4chee.arc.retrieve.RetrieveService;
import org.dcm4chee.arc.store.scu.CStoreSCU;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
@ApplicationScoped
@Named("dicom")
public class DicomExporterProvider implements ExporterProvider {

    @Inject
    private Device device;

    @Inject
    private RetrieveService retrieveService;

    @Inject
    private CStoreSCU storeSCU;

    @Override
    public Exporter getExporter(ExporterDescriptor descriptor) {
        return new DicomExporter(descriptor, device, retrieveService, storeSCU);
    }
}
