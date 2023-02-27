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
 * Portions created by the Initial Developer are Copyright (C) 2017-2019
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

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.Device;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.entity.Task;
import org.dcm4chee.arc.export.mgt.ExportManager;
import org.dcm4chee.arc.exporter.ExportContext;
import org.dcm4chee.arc.exporter.Exporter;
import org.dcm4chee.arc.exporter.ExporterFactory;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.qmgt.Outcome;
import org.dcm4chee.arc.qmgt.TaskProcessor;
import org.dcm4chee.arc.query.QueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.inject.Named;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2021
 */
@ApplicationScoped
@Named("EXPORT")
public class ExportTaskProcessor implements TaskProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(ExportTaskProcessor.class);

    @Inject
    private ExporterFactory exporterFactory;

    @Inject
    private ExportManager ejb;

    @Inject
    private Device device;

    @Inject
    private QueryService queryService;

    @Inject
    private Event<ExportContext> exportEvent;

    @Override
    public Outcome process(Task task) throws Exception {
        Outcome outcome;
        ExportContext exportContext = null;
        try {
            ExporterDescriptor exporterDesc = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class)
                    .getExporterDescriptorNotNull(task.getExporterID());
            Attributes attrs = queryService.queryExportTaskInfo(
                    task, device.getApplicationEntity(exporterDesc.getAETitle(), true));
            if (attrs != null) {
                task.setModalities(attrs.getStrings(Tag.ModalitiesInStudy));
                task.setNumberOfInstances(attrs.getInt(Tag.NumberOfStudyRelatedInstances, -1));
                ejb.merge(task);
            } else {
                LOG.info("No Export Task Info found for {}", task);
            }
            Exporter exporter = exporterFactory.getExporter(exporterDesc);
            exportContext = exporter.createExportContext();
            exportContext.setTaskPK(task.getPk());
            exportContext.setBatchID(task.getBatchID());
            exportContext.setStudyInstanceUID(task.getStudyInstanceUID());
            exportContext.setSeriesInstanceUID(StringUtils.nullify(task.getSeriesInstanceUID(), "*"));
            exportContext.setSopInstanceUID(StringUtils.nullify(task.getSOPInstanceUID(), "*"));
            exportContext.setAETitle(exporterDesc.getAETitle());
            exportContext.setHttpServletRequestInfo(HttpServletRequestInfo.valueOf(
                    task.getRequesterUserID(),
                    task.getRequesterHost(),
                    task.getRequestURI(),
                    task.getQueryString()));
            outcome = exporter.export(exportContext);
            exportContext.setOutcome(outcome);
        } catch (Throwable e) {
            if (exportContext != null)
                exportContext.setException(e);
            LOG.warn("Failed to process {}", task, e);
            throw e;
        } finally {
            if (exportContext != null)
                try {
                    exportEvent.fire(exportContext);
                } catch (Exception e) {
                    LOG.warn("Failed on firing export context {}", task, e);
                }
        }
        return outcome;
    }
}
