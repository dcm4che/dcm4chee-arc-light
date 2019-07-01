/*
 * **** BEGIN LICENSE BLOCK *****
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
 * Portions created by the Initial Developer are Copyright (C) 2015-2019
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
 * **** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.delete.impl;

import org.dcm4che3.net.ApplicationEntity;
import org.dcm4chee.arc.Scheduler;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.delete.RejectionService;
import org.dcm4chee.arc.entity.ExpirationState;
import org.dcm4chee.arc.entity.Series;
import org.dcm4chee.arc.entity.Study;
import org.dcm4chee.arc.export.mgt.ExportManager;
import org.dcm4chee.arc.qmgt.QueueSizeLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Calendar;
import java.util.List;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since June 2016
 */
@ApplicationScoped
public class RejectExpiredStudiesScheduler extends Scheduler {

    private static final Logger LOG = LoggerFactory.getLogger(RejectExpiredStudiesScheduler.class);

    @Inject
    private DeletionServiceEJB ejb;

    @Inject
    private RejectionService rejectionService;

    @Inject
    private ExportManager exportManager;

    protected RejectExpiredStudiesScheduler() {
        super(Mode.scheduleWithFixedDelay);
    }

    @Override
    protected Logger log() {
        return LOG;
    }

    @Override
    protected Duration getPollingInterval() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        return arcDev != null ? arcDev.getRejectExpiredStudiesPollingInterval() : null;
    }

    @Override
    protected void execute() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        if (!ScheduleExpression.emptyOrAnyContains(Calendar.getInstance(), arcDev.getRejectExpiredStudiesSchedules()))
            return;

        ApplicationEntity ae = getApplicationEntity(arcDev.getRejectExpiredStudiesAETitle());
        if (ae == null || !ae.isInstalled()) {
            LOG.warn("No such Application Entity: {}", arcDev.getRejectExpiredStudiesAETitle());
            return;
        }
        RejectionNote rn = arcDev.getRejectionNote(RejectionNote.Type.DATA_RETENTION_POLICY_EXPIRED);
        if (rn == null) {
            LOG.warn("Data Retention Policy Expired Rejection Note not configured.");
            return;
        }

        process(arcDev, ae, rn);
    }

    private void process(ArchiveDeviceExtension arcDev, ApplicationEntity ae, RejectionNote rjNote) {
        int studyFetchSize = arcDev.getRejectExpiredStudiesFetchSize();
        if (studyFetchSize == 0) {
            LOG.warn("RejectExpiredStudies operation ABORT : Study fetch size is 0");
            return;
        }
        processExpiredStudies(ae, rjNote, studyFetchSize);
        int seriesFetchSize = arcDev.getRejectExpiredSeriesFetchSize();
        if (seriesFetchSize == 0) {
            LOG.warn("RejectExpiredSeries operation ABORT : Series fetch size is 0");
            return;
        }
        processExpiredSeries(ae, rjNote, seriesFetchSize);
    }

    private void processExpiredSeries(ApplicationEntity ae, RejectionNote rn, int seriesFetchSize) {
        List<Series> seriesList;
        do {
            seriesList = ejb.findExpiredSeries(seriesFetchSize);
            for (Series series : seriesList) {
                if (getPollingInterval() == null)
                    return;

                if (series.getExpirationExporterID() == null)
                    rejectExpiredSeries(series, ae, rn);
                else
                    exportExpiredSeries(series);
            }
        } while (seriesFetchSize == seriesList.size());
    }

    private void rejectExpiredSeries(Series series, ApplicationEntity ae, RejectionNote rn) {
        try {
            if (ejb.claimExpiredSeriesFor(series, ExpirationState.REJECTED))
                rejectionService.reject(ae, series.getStudy().getStudyInstanceUID(), series.getSeriesInstanceUID(),
                        null, rn, null);
        } catch (Exception e) {
            LOG.warn("Failed to reject Expired Series[UID={}] of Study[UID={}].\n",
                    series.getSeriesInstanceUID(), series.getStudy().getStudyInstanceUID(), e);
            ejb.claimExpiredSeriesFor(series, ExpirationState.FAILED_TO_REJECT);
        }
    }

    private void exportExpiredSeries(Series series) {
        String expirationExporterID = series.getExpirationExporterID();
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        ExporterDescriptor exporter = arcDev.getExporterDescriptor(expirationExporterID);
        if (exporter == null) {
            LOG.warn("No configuration found for [ExpirationExporterID={}] of Series[UID={}]",
                    expirationExporterID, series.getSeriesInstanceUID());
            ejb.claimExpiredSeriesFor(series, ExpirationState.FAILED_TO_EXPORT);
            return;
        }

        try {
            if (ejb.claimExpiredSeriesFor(series, ExpirationState.EXPORT_SCHEDULED))
                exportManager.scheduleExportTask(
                        series.getStudy().getStudyInstanceUID(),
                        series.getSeriesInstanceUID(),
                        null,
                        exporter,
                        null,
                        null);
        } catch (QueueSizeLimitExceededException e) {
            LOG.warn(e.getMessage());
        }
    }

    private void processExpiredStudies(ApplicationEntity ae, RejectionNote rn, int studyFetchSize) {
        List<Study> studies;
        do {
            studies = ejb.findExpiredStudies(studyFetchSize);
            for (Study study : studies) {
                if (getPollingInterval() == null)
                    return;

                if (study.getExpirationExporterID() == null)
                    rejectExpiredStudy(study, ae, rn);
                else
                    exportExpiredStudy(study);
            }
        } while (studyFetchSize == studies.size());
    }

    private void rejectExpiredStudy(Study study, ApplicationEntity ae, RejectionNote rn) {
        try {
            if (ejb.claimExpiredStudyFor(study, ExpirationState.REJECTED))
                rejectionService.reject(ae, study.getStudyInstanceUID(), null, null, rn, null);
        } catch (Exception e) {
            LOG.warn("Failed to reject Expired Study[UID={}].\n", study.getStudyInstanceUID(), e);
            ejb.claimExpiredStudyFor(study, ExpirationState.FAILED_TO_REJECT);
        }
    }

    private void exportExpiredStudy(Study study) {
        String expirationExporterID = study.getExpirationExporterID();
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        ExporterDescriptor exporter = arcDev.getExporterDescriptor(expirationExporterID);
        if (exporter == null) {
            LOG.warn("No configuration found for [ExpirationExporterID={}] of Study[UID={}]",
                    expirationExporterID, study.getStudyInstanceUID());
            ejb.claimExpiredStudyFor(study, ExpirationState.FAILED_TO_EXPORT);
            return;
        }

        try {
            if (ejb.claimExpiredStudyFor(study, ExpirationState.EXPORT_SCHEDULED))
                exportManager.scheduleExportTask(
                        study.getStudyInstanceUID(),
                        null,
                        null,
                        exporter,
                        null,
                        null);
        } catch (QueueSizeLimitExceededException e) {
            LOG.warn(e.getMessage());
        }
    }

    private ApplicationEntity getApplicationEntity(String aet) {
        return device.getApplicationEntity(aet, true);
    }

}
