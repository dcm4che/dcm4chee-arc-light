/*
 * *** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * J4Care.
 * Portions created by the Initial Developer are Copyright (C) 2017
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * *** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.export.mgt.impl;

import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.entity.QueueMessage;
import org.dcm4chee.arc.export.mgt.ExportManager;
import org.dcm4chee.arc.exporter.ExportContext;
import org.dcm4chee.arc.exporter.Exporter;
import org.dcm4chee.arc.exporter.ExporterFactory;
import org.dcm4chee.arc.qmgt.Outcome;
import org.dcm4chee.arc.qmgt.QueueManager;
import org.dcm4chee.arc.qmgt.HttpServletRequestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class ExportManagerMDB implements MessageListener {
    private static final Logger LOG = LoggerFactory.getLogger(ExportManagerMDB.class);

    @Inject
    private ExportManager ejb;

    @Inject
    private QueueManager queueManager;

    @Inject
    private ExporterFactory exporterFactory;

    @Inject
    private Event<ExportContext> exportEvent;

    @Inject
    private Device device;

    @Override
    public void onMessage(Message msg) {
        String msgID;
        try {
            msgID = msg.getJMSMessageID();
        } catch (JMSException e) {
            LOG.error("Failed to process {}", msg, e);
            return;
        }
        QueueMessage queueMessage = queueManager.onProcessingStart(msgID);
        if (queueMessage == null)
            return;

        Long exportTaskPk = (Long) queueMessage.getMessageBody();
        Outcome outcome;
        try {
            ExporterDescriptor exporterDesc = getExporterDescriptor(msg.getStringProperty("ExporterID"));
            Exporter exporter = exporterFactory.getExporter(exporterDesc);
            ExportContext exportContext = exporter.createExportContext();
            exportContext.setMessageID(msgID);
            exportContext.setStudyInstanceUID(msg.getStringProperty("StudyInstanceUID"));
            exportContext.setSeriesInstanceUID(msg.getStringProperty("SeriesInstanceUID"));
            exportContext.setSopInstanceUID(msg.getStringProperty("SopInstanceUID"));
            exportContext.setAETitle(exporterDesc.getAETitle());
            exportContext.setHttpServletRequestInfo(HttpServletRequestInfo.valueOf(msg));
            outcome = exporter.export(exportContext);
            exportContext.setOutcome(outcome);
            exportEvent.fire(exportContext);
        } catch (Throwable e) {
            LOG.warn("Failed to process {}", msg, e);
            queueManager.onProcessingFailed(msgID, e);
            return;
        }
        queueManager.onProcessingSuccessful(msgID, outcome);
    }

    private ExporterDescriptor getExporterDescriptor(String exporterID) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        return arcDev != null ? arcDev.getExporterDescriptorNotNull(exporterID) : null;
    }
}
