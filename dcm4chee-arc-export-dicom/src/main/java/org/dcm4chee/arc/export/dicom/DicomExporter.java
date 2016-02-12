package org.dcm4chee.arc.export.dicom;

import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.service.RetrieveTask;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.exporter.AbstractExporter;
import org.dcm4chee.arc.exporter.ExportContext;
import org.dcm4chee.arc.qmgt.Outcome;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveService;
import org.dcm4chee.arc.store.scu.CStoreSCU;

import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
public class DicomExporter extends AbstractExporter {

    private final Device device;
    private final RetrieveService retrieveService;
    private final CStoreSCU storeSCU;
    private final String destAET;
    private final Map<String, RetrieveTask> retrieveTaskMap;

    protected DicomExporter(ExporterDescriptor descriptor, Device device, RetrieveService retrieveService,
                            CStoreSCU storeSCU, Map<String, RetrieveTask> retrieveTaskMap) {
        super(descriptor);
        this.device = device;
        this.retrieveService = retrieveService;
        this.storeSCU = storeSCU;
        this.destAET = descriptor.getExportURI().getSchemeSpecificPart();
        this.retrieveTaskMap = retrieveTaskMap;
    }

    @Override
    public Outcome export(ExportContext exportContext) throws Exception {
        ApplicationEntity ae = device.getApplicationEntity(exportContext.getAETitle(), true);
        RetrieveContext retrieveContext = retrieveService.newRetrieveContextSTORE(ae,
                exportContext.getStudyInstanceUID(),
                exportContext.getSeriesInstanceUID(),
                exportContext.getSopInstanceUID(),
                destAET);
        if (!retrieveService.calculateMatches(retrieveContext))
            return new Outcome(QueueMessage.Status.WARNING, noMatches(exportContext));

        String messageID = exportContext.getMessageID();
        RetrieveTask retrieveTask = storeSCU.newRetrieveTaskSTORE(retrieveContext);
        retrieveTaskMap.put(messageID, retrieveTask);
        try {
            retrieveTask.run();
            return new Outcome(
                    retrieveContext.remaining() > 0
                            ? QueueMessage.Status.CANCELED
                            : retrieveContext.failed() > 0
                            ? QueueMessage.Status.WARNING
                            : QueueMessage.Status.COMPLETED,
                    outcomeMessage(exportContext, retrieveContext));
        } finally {
            retrieveTaskMap.remove(messageID);
        }
    }

    private String noMatches(ExportContext exportContext) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("Could not find ");
        appendEntity(exportContext, sb);
        return sb.toString();
    }

    private String outcomeMessage(ExportContext exportContext, RetrieveContext retrieveContext) {
        int remaining = retrieveContext.remaining();
        int completed = retrieveContext.completed();
        int warning = retrieveContext.warning();
        int failed = retrieveContext.failed();
        StringBuilder sb = new StringBuilder(256);
        sb.append("Export ");
        appendEntity(exportContext, sb);
        sb.append(" to AE: ").append(destAET);
        if (remaining > 0)
            sb.append(" canceled - remaining:").append(remaining).append(", ");
        else
            sb.append(" - ");
        sb.append("completed:").append(completed);
        if (warning > 0)
            sb.append(", ").append("warning:").append(warning);
        if (failed > 0)
            sb.append(", ").append("failed:").append(failed);
        return sb.toString();
    }

    private StringBuilder appendEntity(ExportContext exportContext, StringBuilder sb) {
        String studyInstanceUID = exportContext.getStudyInstanceUID();
        String seriesInstanceUID = exportContext.getSeriesInstanceUID();
        String sopInstanceUID = exportContext.getSopInstanceUID();
        if (!sopInstanceUID.equals("*"))
            sb.append("Instance[uid=").append(sopInstanceUID).append("] of ");
        if (!seriesInstanceUID.equals("*"))
            sb.append("Series[uid=").append(seriesInstanceUID).append("] of ");
        return sb.append("Study[uid=").append(studyInstanceUID).append("]");
    }

}
