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
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
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

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.Scheduler;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.Duration;
import org.dcm4chee.arc.conf.RejectionNote;
import org.dcm4chee.arc.entity.Series;
import org.dcm4chee.arc.entity.Study;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.store.StoreService;
import org.dcm4chee.arc.store.StoreSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since June 2016
 */
@ApplicationScoped
public class DeleteExpiredStudiesScheduler extends Scheduler {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteExpiredStudiesScheduler.class);

    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    @Inject
    private Device device;

    @Inject
    private QueryService queryService;

    @Inject
    private StoreService storeService;

    @Inject
    private DeletionServiceEJB ejb;

    protected DeleteExpiredStudiesScheduler() {
        super(Mode.scheduleAtFixedRate);
    }

    @Override
    protected Logger log() {
        return LOG;
    }

    @Override
    protected Duration getPollingInterval() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        return arcDev.getRejectExpiredStudiesPollingInterval();
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
            LOG.warn("No such Application Entity: " + arcDev.getRejectExpiredStudiesAETitle(),
                    Response.Status.SERVICE_UNAVAILABLE);
        }
        RejectionNote rn = getRejectionNote(arcDev.getRejectionNotes());
        if (rn == null) {
            LOG.warn("Unknown Rejection Note Code: ", Response.Status.NOT_FOUND);
            return;
        }
        int studyFetchSize = arcDev.getRejectExpiredStudiesFetchSize();
        if (studyFetchSize == 0) {
            LOG.warn("DeleteExpiredStudies operation ABORT : Study fetch size is == 0");
            return;
        }
        List<Study> studies;
        do {
            studies = em.createNamedQuery(Study.GET_EXPIRED_STUDIES, Study.class)
                    .setParameter(1, LocalDate.now().toString()).setMaxResults(studyFetchSize).getResultList();
            for (Study study : studies) {
                try {
                    reject(ae, study.getStudyInstanceUID(), null, rn);
                } catch (IOException e) {
                    LOG.warn("IOException caught is : ", e);
                }
            }
        } while (studyFetchSize == studies.size());
        int seriesFetchSize = arcDev.getRejectExpiredSeriesFetchSize();
        if (seriesFetchSize == 0) {
            LOG.warn("DeleteExpiredStudies operation ABORT : Series fetch size is == 0");
            return;
        }
        List<Series> seriesList;
        do {
            seriesList = em.createNamedQuery(Series.GET_EXPIRED_SERIES, Series.class)
                    .setParameter(1, LocalDate.now().toString()).setMaxResults(seriesFetchSize).getResultList();
            for (Series series : seriesList) {
                try {
                    reject(ae, series.getStudy().getStudyInstanceUID(), series.getSeriesInstanceUID(), rn);
                } catch (IOException e) {
                    LOG.warn("IOException caught is : ", e);
                }
            }
        } while (seriesFetchSize == seriesList.size());
    }

    private ApplicationEntity getApplicationEntity(String aet) {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        return ae;
    }

    private RejectionNote getRejectionNote(Collection<RejectionNote> rjns) {
        for (RejectionNote rn : rjns)
            if (rn.getRejectionNoteType() == RejectionNote.Type.DATA_RETENTION_POLICY_EXPIRED)
                return rn;
        return null;
    }

    private void reject(ApplicationEntity ae, String studyUID, String seriesUID,
                        RejectionNote rn) throws IOException {
        Attributes attrs = queryService.createRejectionNote(ae, studyUID, seriesUID, null, rn);
        if (attrs == null)
            LOG.warn("No Study with UID: " + studyUID, Response.Status.NOT_FOUND);

        StoreSession session = storeService.newStoreSession(ae);
        StoreContext ctx = storeService.newStoreContext(session);
        ctx.setSopClassUID(attrs.getString(Tag.SOPClassUID));
        ctx.setSopInstanceUID(attrs.getString(Tag.SOPInstanceUID));
        ctx.setReceiveTransferSyntax(UID.ExplicitVRLittleEndian);
        storeService.store(ctx, attrs);
    }
}
