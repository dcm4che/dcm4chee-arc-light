package org.dcm4chee.arc.exporter;

import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.retrieve.RetrieveContext;

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

    protected static String noMatches(ExportContext exportContext) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("Could not find ");
        appendEntity(exportContext, sb);
        return sb.toString();
    }

    protected static StringBuilder appendEntity(ExportContext exportContext, StringBuilder sb) {
        String studyInstanceUID = exportContext.getStudyInstanceUID();
        String seriesInstanceUID = exportContext.getSeriesInstanceUID();
        String sopInstanceUID = exportContext.getSopInstanceUID();
        if (sopInstanceUID != null && !sopInstanceUID.equals("*"))
            sb.append("Instance[uid=").append(sopInstanceUID).append("] of ");
        if (seriesInstanceUID != null && !seriesInstanceUID.equals("*"))
            sb.append("Series[uid=").append(seriesInstanceUID).append("] of ");
        return sb.append("Study[uid=").append(studyInstanceUID).append("]");
    }

    protected static String outcomeMessage(ExportContext exportContext, RetrieveContext retrieveContext, Object dest) {
        int remaining = retrieveContext.remaining();
        int completed = retrieveContext.completed();
        int warning = retrieveContext.warning();
        int failed = retrieveContext.failed();
        int missing = retrieveContext.missing();
        StringBuilder sb = new StringBuilder(256);
        sb.append("Export ");
        appendEntity(exportContext, sb);
        sb.append(" to ").append(dest);
        if (remaining > 0)
            sb.append(" canceled - remaining:").append(remaining).append(", ");
        else
            sb.append(" - ");
        sb.append("completed:").append(completed);
        if (warning > 0)
            sb.append(", ").append("warning:").append(warning);
        if (failed > 0)
            sb.append(", ").append("failed:").append(failed);
        if (missing > 0)
            sb.append(", ").append("missing:").append(missing);
        return sb.toString();
    }
}
