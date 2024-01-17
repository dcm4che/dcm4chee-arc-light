/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 *  The contents of this file are subject to the Mozilla Public License Version
 *  1.1 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  http://www.mozilla.org/MPL/
 *
 *  Software distributed under the License is distributed on an "AS IS" basis,
 *  WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 *  for the specific language governing rights and limitations under the
 *  License.
 *
 *  The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 *  Java(TM), hosted at https://github.com/dcm4che.
 *
 *  The Initial Developer of the Original Code is
 *  J4Care.
 *  Portions created by the Initial Developer are Copyright (C) 2015-2017
 *  the Initial Developer. All Rights Reserved.
 *
 *  Contributor(s):
 *  See @authors listed below
 *
 *  Alternatively, the contents of this file may be used under the terms of
 *  either the GNU General Public License Version 2 or later (the "GPL"), or
 *  the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 *  in which case the provisions of the GPL or the LGPL are applicable instead
 *  of those above. If you wish to allow use of your version of this file only
 *  under the terms of either the GPL or the LGPL, and not to allow others to
 *  use your version of this file under the terms of the MPL, indicate your
 *  decision by deleting the provisions above and replace them with the notice
 *  and other provisions required by the GPL or the LGPL. If you do not delete
 *  the provisions above, a recipient may use your version of this file under
 *  the terms of any one of the MPL, the GPL or the LGPL.
 *
 */

package org.dcm4chee.arc.export.dicom;

import org.dcm4che3.net.service.RetrieveTask;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.entity.Task;
import org.dcm4chee.arc.exporter.AbstractExporter;
import org.dcm4chee.arc.exporter.ExportContext;
import org.dcm4chee.arc.qmgt.Outcome;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveService;
import org.dcm4chee.arc.retrieve.SeriesInfo;
import org.dcm4chee.arc.store.InstanceLocations;
import org.dcm4chee.arc.store.scu.CStoreSCU;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
public class DicomExporter extends AbstractExporter {

    private final RetrieveService retrieveService;
    private final CStoreSCU storeSCU;
    private final String destAET;
    private final Map<Long, RetrieveTask> retrieveTaskMap;

    protected DicomExporter(ExporterDescriptor descriptor, RetrieveService retrieveService,
                            CStoreSCU storeSCU, Map<Long, RetrieveTask> retrieveTaskMap) {
        super(descriptor);
        this.retrieveService = retrieveService;
        this.storeSCU = storeSCU;
        this.destAET = descriptor.getExportURI().getSchemeSpecificPart();
        this.retrieveTaskMap = retrieveTaskMap;
    }

    @Override
    public Outcome export(ExportContext exportContext) throws Exception {
        RetrieveContext retrieveContext = retrieveService.newRetrieveContextSTORE(
                exportContext.getAETitle(),
                exportContext.getStudyInstanceUID(),
                exportContext.getSeriesInstanceUID(),
                exportContext.getSopInstanceUID(),
                destAET);
        retrieveContext.setHttpServletRequestInfo(exportContext.getHttpServletRequestInfo());
        if (!retrieveService.calculateMatches(retrieveContext))
            return new Outcome(Task.Status.WARNING, noMatches(exportContext));

        if (!retrieveService.restrictRetrieveAccordingTransferCapabilities(retrieveContext))
            return new Outcome(
                    retrieveContext.failed() > 0
                            ? Task.Status.WARNING
                            : Task.Status.COMPLETED,
                    outcomeMessage(exportContext, retrieveContext, destAET));

        Map<String, Collection<InstanceLocations>> notAccessable = retrieveService.removeNotAccessableMatches(retrieveContext);

        if (descriptor.isExportAsSourceAE()) {
            retrieveContext.getSeriesInfos().stream()
                    .map(SeriesInfo::getSendingAET)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .ifPresent(retrieveContext::setCallingAET);
        }
        Long taskPk = exportContext.getTaskPK();
        RetrieveTask retrieveTask = storeSCU.newRetrieveTaskSTORE(retrieveContext);
        retrieveTaskMap.put(taskPk, retrieveTask);
        try {
            retrieveTask.run();
            return new Outcome(
                    retrieveContext.remaining() > 0
                            ? Task.Status.CANCELED
                            : retrieveContext.failed() > 0
                            ? (retrieveContext.missing() > 0
                                ? Task.Status.FAILED
                                : Task.Status.WARNING)
                            : Task.Status.COMPLETED,
                    outcomeMessage(exportContext, retrieveContext, destAET));
        } finally {
            retrieveTaskMap.remove(taskPk);
        }
    }
}
