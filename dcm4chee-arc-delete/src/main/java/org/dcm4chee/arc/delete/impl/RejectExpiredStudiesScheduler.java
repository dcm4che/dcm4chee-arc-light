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
 * Portions created by the Initial Developer are Copyright (C) 2013
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
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.Scheduler;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.Duration;
import org.dcm4chee.arc.conf.RejectionNote;
import org.dcm4chee.arc.delete.RejectionService;
import org.dcm4chee.arc.entity.Series;
import org.dcm4chee.arc.entity.Study;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.store.StoreService;
import org.dcm4chee.arc.store.StoreSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since June 2016
 */
@ApplicationScoped
public class RejectExpiredStudiesScheduler extends Scheduler {

    private static final Logger LOG = LoggerFactory.getLogger(RejectExpiredStudiesScheduler.class);

    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    @Inject
    private Device device;

    @Inject
    private QueryService queryService;

    @Inject
    private StoreService storeService;

    @Inject
    private RejectionService rejectionService;

    protected RejectExpiredStudiesScheduler() {
        super(Mode.scheduleAtFixedRate);
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
    protected LocalTime getStartTime() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        return arcDev.getRejectExpiredStudiesPollingStartTime();
    }

    @Override
    protected void execute() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        ApplicationEntity ae = getApplicationEntity(arcDev.getRejectExpiredStudiesAETitle());
        if (ae == null || !ae.isInstalled()) {
            LOG.warn("No such Application Entity: {}", arcDev.getRejectExpiredStudiesAETitle());
            return;
        }
        Optional<RejectionNote> rn = arcDev.getRejectionNotes().stream()
                            .filter(RejectionNote::isDataRetentionPolicyExpired)
                            .findFirst();
        if (!rn.isPresent()) {
            LOG.warn("Data Retention Policy Expired Rejection Note not configured.");
            return;
        }

        String rejectionNoteObjectStorageID = rejectionNoteObjectStorageID(arcDev.getRejectionNoteStorageAET());
        if (arcDev.getRejectionNoteStorageAET() == null || rejectionNoteObjectStorageID != null)
            reject(arcDev, ae, rn.get(), rejectionNoteObjectStorageID);
    }

    private void reject(ArchiveDeviceExtension arcDev, ApplicationEntity ae,
                        RejectionNote rjNote, String rejectionNoteObjectStorageID) {
        int studyFetchSize = arcDev.getRejectExpiredStudiesFetchSize();
        if (studyFetchSize == 0) {
            LOG.warn("DeleteExpiredStudies operation ABORT : Study fetch size is 0");
            return;
        }
        rejectExpiredStudies(ae, rjNote, studyFetchSize, rejectionNoteObjectStorageID);
        int seriesFetchSize = arcDev.getRejectExpiredSeriesFetchSize();
        if (seriesFetchSize == 0) {
            LOG.warn("DeleteExpiredStudies operation ABORT : Series fetch size is == 0");
            return;
        }
        rejectExpiredSeries(ae, rjNote, seriesFetchSize, rejectionNoteObjectStorageID);
    }

    private String rejectionNoteObjectStorageID(String rejectionNoteStorageAET) {
        ApplicationEntity rjAE = getApplicationEntity(rejectionNoteStorageAET);
        ArchiveAEExtension rjArcAE;
        if (rjAE == null || !rjAE.isInstalled() || (rjArcAE = rjAE.getAEExtension(ArchiveAEExtension.class)) == null) {
            LOG.warn("Rejection Note Storage Application Entity with an Archive AE Extension not configured: {}",
                    rejectionNoteStorageAET);
            return null;
        }
        String[] objectStorageIDs;
        if ((objectStorageIDs = rjArcAE.getObjectStorageIDs()).length == 0) {
            LOG.warn("Object storage not configured for Rejection Note Storage AE: {}", rejectionNoteStorageAET);
            return null;
        }
        return objectStorageIDs[0];
    }

    private void rejectExpiredSeries(
            ApplicationEntity ae, RejectionNote rn, int seriesFetchSize, String rejectionNoteObjectStorageID) {
        List<Series> seriesList;
        do {
            seriesList = em.createNamedQuery(Series.GET_EXPIRED_SERIES, Series.class)
                    .setParameter(1, DateTimeFormatter.BASIC_ISO_DATE.format(LocalDate.now()))
                    .setMaxResults(seriesFetchSize)
                    .getResultList();
            for (Series series : seriesList) {
                if (getPollingInterval() == null)
                    return;

                try {
                    StoreSession session = storeService.newStoreSession(ae)
                            .withObjectStorageID(rejectionNoteObjectStorageID);
                    rejectionService.reject(
                            session, ae, series.getStudy().getStudyInstanceUID(), series.getSeriesInstanceUID(),
                            null, rn);
                } catch (Exception e) {
                    LOG.warn("Failed to reject Expired Series[UID={}] of Study[UID={}].\n",
                            series.getSeriesInstanceUID(), series.getStudy().getStudyInstanceUID(), e);
                }
            }
        } while (seriesFetchSize == seriesList.size());
    }

    private void rejectExpiredStudies(
            ApplicationEntity ae, RejectionNote rn, int studyFetchSize, String rejectionNoteObjectStorageID) {
        List<Study> studies;
        do {
            studies = em.createNamedQuery(Study.GET_EXPIRED_STUDIES, Study.class)
                    .setParameter(1, DateTimeFormatter.BASIC_ISO_DATE.format(LocalDate.now()))
                    .setMaxResults(studyFetchSize)
                    .getResultList();
            for (Study study : studies) {
                if (getPollingInterval() == null)
                    return;

                try {
                    StoreSession session = storeService.newStoreSession(ae)
                            .withObjectStorageID(rejectionNoteObjectStorageID);
                    rejectionService.reject(
                            session, ae, study.getStudyInstanceUID(), null, null, rn);
                } catch (Exception e) {
                    LOG.warn("Failed to reject Expired Study[UID={}].\n", study.getStudyInstanceUID(), e);
                }
            }
        } while (studyFetchSize == studies.size());
    }

    private ApplicationEntity getApplicationEntity(String aet) {
        return device.getApplicationEntity(aet, true);
    }

}
