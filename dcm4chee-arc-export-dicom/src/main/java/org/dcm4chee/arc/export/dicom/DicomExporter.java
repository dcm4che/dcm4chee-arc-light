package org.dcm4chee.arc.export.dicom;

import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.service.RetrieveTask;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.exporter.AbstractExporter;
import org.dcm4chee.arc.exporter.ExportContext;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveService;
import org.dcm4chee.arc.store.scu.CStoreSCU;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
public class DicomExporter extends AbstractExporter {

    private final ApplicationEntity ae;
    private final RetrieveService retrieveService;
    private final CStoreSCU storeSCU;
    private final String destAET;

    protected DicomExporter(ExporterDescriptor descriptor, Device device, RetrieveService retrieveService,
                            CStoreSCU storeSCU) {
        super(descriptor);
        this.ae = device.getApplicationEntity(descriptor.getAETitle());
        this.retrieveService = retrieveService;
        this.storeSCU = storeSCU;
        this.destAET = descriptor.getExportURI().getSchemeSpecificPart();
    }

    @Override
    public void export(ExportContext exportContext) throws Exception {
        RetrieveContext retrieveContext = retrieveService.newRetrieveContextSTORE(ae,
                exportContext.getStudyInstanceUID(),
                exportContext.getSeriesInstanceUID(),
                exportContext.getSopInstanceUID(),
                destAET);
        if (!retrieveService.calculateMatches(retrieveContext))
            return;

        RetrieveTask retrieveTask = storeSCU.newRetrieveTaskSTORE(retrieveContext);
        retrieveTask.run();
    }
}
