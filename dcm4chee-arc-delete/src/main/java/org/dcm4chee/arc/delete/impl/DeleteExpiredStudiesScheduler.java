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

import org.dcm4che3.net.Device;
import org.dcm4chee.arc.Scheduler;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.Duration;
import org.dcm4chee.arc.entity.Series;
import org.dcm4chee.arc.entity.Study;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since June 2016
 */

public class DeleteExpiredStudiesScheduler extends Scheduler {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteExpiredStudiesScheduler.class);

    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    @Inject
    private Device device;

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
        int studyFetchSize = arcDev.getRejectExpiredStudiesFetchSize();
        if (studyFetchSize == 0)
            return;
        List<Study> studies;
        do {
            studies = em.createNamedQuery(Study.GET_EXPIRED_STUDIES, Study.class)
                    .setParameter(1, studyFetchSize)
                    .setParameter(2, LocalDate.now().toString()).getResultList();
            for (Study study : studies) {
                //Call reject studies - pass getStartTime() as parameter
            }
        } while (studyFetchSize == studies.size());
        int seriesFetchSize = arcDev.getRejectExpiredSeriesFetchSize();
        if (seriesFetchSize == 0)
            return;
        List<Series> seriesList;
        do {
            seriesList = em.createNamedQuery(Series.GET_EXPIRED_SERIES, Series.class)
                    .setParameter(1, seriesFetchSize)
                    .setParameter(2, LocalDate.now().toString()).getResultList();
            for (Series series : seriesList) {
                //Call reject series - pass getStartTime() as parameter
            }
        } while (seriesFetchSize == seriesList.size());
    }
}
