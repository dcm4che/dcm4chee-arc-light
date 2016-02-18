package org.dcm4chee.arc.export.dicom;

import org.dcm4che3.net.Device;
import org.dcm4che3.net.service.RetrieveTask;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.exporter.Exporter;
import org.dcm4chee.arc.exporter.ExporterProvider;
import org.dcm4chee.arc.qmgt.MessageCanceled;
import org.dcm4chee.arc.retrieve.RetrieveService;
import org.dcm4chee.arc.store.scu.CStoreSCU;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
@ApplicationScoped
@Named("dicom")
public class DicomExporterProvider implements ExporterProvider {

    @Inject
    private RetrieveService retrieveService;

    @Inject
    private CStoreSCU storeSCU;

    private final Map<String, RetrieveTask> retrieveTaskMap =
            Collections.synchronizedMap(new HashMap<String, RetrieveTask>());

    @Override
    public Exporter getExporter(ExporterDescriptor descriptor) {
        return new DicomExporter(descriptor, retrieveService, storeSCU, retrieveTaskMap);
    }

    public void cancelRetrieveTask(@Observes MessageCanceled event) {
        RetrieveTask retrieveTask = retrieveTaskMap.get(event.getMessageID());
        if (retrieveTask != null)
            retrieveTask.onCancelRQ(null);
    }
}
