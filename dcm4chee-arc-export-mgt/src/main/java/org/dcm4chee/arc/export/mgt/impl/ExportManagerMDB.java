package org.dcm4chee.arc.export.mgt.impl;

import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.exporter.ExportContext;
import org.dcm4chee.arc.exporter.Exporter;
import org.dcm4chee.arc.exporter.ExporterFactory;
import org.dcm4chee.arc.qmgt.QueueManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import javax.ejb.MessageDrivenContext;
import javax.inject.Inject;
import javax.jms.Message;
import javax.jms.MessageListener;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
public class ExportManagerMDB implements MessageListener {
    private static final Logger LOG = LoggerFactory.getLogger(ExportManagerMDB.class);

    @Resource
    private MessageDrivenContext ctx;

    @Inject
    private QueueManager queueManager;

    @Inject
    private ExporterFactory exporterFactory;

    @Inject
    private Device device;

    @Override
    public void onMessage(Message msg) {
        QueueMessage queueMessage = queueManager.onProcessingStart(msg);
        if (queueMessage == null)
            return;
        try {
            Exporter exporter = exporterFactory.getExporter(getExporterDescriptor(msg.getStringProperty("ExporterID")));
            ExportContext exportContext = exporter.createExportContext();
            exportContext.setStudyInstanceUID(msg.getStringProperty("StudyInstanceUID"));
            exportContext.setSeriesInstanceUID(msg.getStringProperty("SeriesInstanceUID"));
            exportContext.setSopInstanceUID(msg.getStringProperty("SopInstanceUID"));
            exporter.export(exportContext);
            queueManager.onProcessingSuccessful(queueMessage);
        } catch (Exception e) {
            LOG.warn("Failed to process {}", msg, e);
            queueManager.onProcessingFailed(queueMessage, e);
            ctx.setRollbackOnly();
        }
    }

    private ExporterDescriptor getExporterDescriptor(String exporterID) {
        return device.getDeviceExtension(ArchiveDeviceExtension.class).getExporterDescriptor(exporterID);
    }
}
