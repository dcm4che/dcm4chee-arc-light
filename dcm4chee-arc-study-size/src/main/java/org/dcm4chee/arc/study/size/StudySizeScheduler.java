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
 *  Portions created by the Initial Developer are Copyright (C) 2015-2020
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

package org.dcm4chee.arc.study.size;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.Tuple;
import org.dcm4chee.arc.Scheduler;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.Duration;
import org.dcm4chee.arc.entity.PatientID;
import org.dcm4chee.arc.event.StudySizeEvent;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.query.impl.QueryAttributesEJB;
import org.dcm4chee.arc.query.impl.QuerySizeEJB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Oct 2020
 */
@ApplicationScoped
public class StudySizeScheduler extends Scheduler {
    private static final Logger LOG = LoggerFactory.getLogger(StudySizeScheduler.class);

    @Inject
    private QueryService queryService;

    @Inject
    private QuerySizeEJB querySizeEJB;

    @Inject
    private QueryAttributesEJB queryAttrsEJB;

    @Inject
    private Event<StudySizeEvent> studySizeEvent;

    protected StudySizeScheduler() {
        super(Mode.scheduleWithFixedDelay);
    }

    @Override
    protected Logger log() {
        return LOG;
    }

    @Override
    protected Duration getPollingInterval() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        return arcDev.getCalculateStudySizePollingInterval();
    }

    @Override
    protected void execute() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        int calculated = 0;
        List<Tuple> studies;
        int studySizeFetchSize;
        do {
            studies = queryService.unknownSizeStudies(
                    updatedTime(arcDev.getStudySizeDelay()),
                    studySizeFetchSize = arcDev.getCalculateStudySizeFetchSize());
            for (Tuple studyTuple : studies) {
                Long studyPk = studyTuple.get(0, Long.class);
                try {
                    long studySize = querySizeEJB.claimAndCalculateStudySize(studyPk);
                    if (studySize > 0L) {
                        if (arcDev.isCalculateQueryAttributes())
                            queryAttrsEJB.calculateStudyQueryAttributes(studyPk);
                        calculated++;
                        studySizeEvent.fire(new StudySizeEvent(
                                studyTuple.get(1, String.class),
                                studyTuple.get(2, PatientID.class).getIDWithIssuer()));
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to update DB with calculated size or query attributes for Study[pk={}]:{}",
                            studyPk, System.lineSeparator(), e);
                }
            }
        } while (studies.size() == studySizeFetchSize && getPollingInterval() != null);
        if (calculated > 0)
            LOG.info("Calculated size of {} studies", calculated);
    }

    private Date updatedTime(Duration delay) {
        Calendar now = Calendar.getInstance();
        return delay != null ? new Date(now.getTimeInMillis() - delay.getSeconds() * 1000) : now.getTime();
    }
}
