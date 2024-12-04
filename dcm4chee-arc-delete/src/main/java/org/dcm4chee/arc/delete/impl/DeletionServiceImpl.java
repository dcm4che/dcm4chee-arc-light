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
 * Portions created by the Initial Developer are Copyright (C) 2015-2017
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

package org.dcm4chee.arc.delete.impl;


import jakarta.ejb.EJBException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import org.dcm4che3.data.Code;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4chee.arc.conf.AllowDeleteStudyPermanently;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.LocationStatus;
import org.dcm4chee.arc.delete.*;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.patient.PatientService;
import org.dcm4chee.arc.store.StoreService;
import org.dcm4chee.arc.store.StoreSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Nov 2015
 */
@ApplicationScoped
public class DeletionServiceImpl implements DeletionService {

    private static final Logger LOG = LoggerFactory.getLogger(DeletionServiceImpl.class);

    @PersistenceContext(unitName = "dcm4chee-arc")
    private EntityManager em;

    @Inject
    private Device device;

    @Inject
    private DeletionServiceEJB ejb;

    @Inject
    private PatientService patientService;

    @Inject
    private StoreService storeService;

    @Inject
    private Event<StudyDeleteContext> studyDeletedEvent;

    @Inject
    private Event<PatientMgtContext> patientMgtEvent;


    @Override
    public int deleteRejectedInstancesBefore(Code rjCode, Date before, int fetchSize) {
        return delete(ejb::deleteRejectedInstances, rjCode, before, fetchSize);
    }

    @Override
    public int deleteRejectionNotesBefore(Code rjCode, Date before, int fetchSize) {
        return delete(ejb::deleteRejectionNotes, rjCode, before, fetchSize);
    }

    private int delete(DeletionServiceEJB.DeleteRejectedInstancesOrRejectionNotes cmd,
            Code rjCode, Date before, int fetchSize) {
        int total = 0;
        int deleted;
        do {
            total += deleted = cmd.delete(rjCode, before, fetchSize);
        } while (deleted == fetchSize);
        return total;
    }

    @Override
    public StudyDeleteContext createStudyDeleteContext(Long pk, HttpServletRequestInfo httpServletRequestInfo) {
        StudyDeleteContext ctx = new StudyDeleteContextImpl(pk);
        ctx.setHttpServletRequestInfo(httpServletRequestInfo);
        return ctx;
    }

    @Override
    public StudyDeleteContext createStudyDeleteContext(Study study, HttpServletRequestInfo httpServletRequestInfo) {
        StudyDeleteContext ctx = new StudyDeleteContextImpl(study.getPk());
        ctx.setHttpServletRequestInfo(httpServletRequestInfo);
        ctx.setStudy(study);
        return ctx;
    }

    @Override
    public void deleteStudy(
            String studyUID, HttpServletRequestInfo httpServletRequestInfo, ArchiveAEExtension arcAE, boolean retainObj)
            throws Exception {
        deleteStudy(createStudyDeleteContext(findStudy(studyUID), httpServletRequestInfo), arcAE, retainObj, false);
    }

    private Study findStudy(String studyUID) throws StudyNotFoundException {
        try {
            return em.createNamedQuery(Study.FIND_BY_STUDY_IUID, Study.class)
                    .setParameter(1, studyUID)
                    .getSingleResult();
        } catch (NoResultException e) {
            throw new StudyNotFoundException("No study found for " + studyUID);
        }
    }

    @Override
    public List<Location> reimportStudy(String studyUID, HttpServletRequestInfo httpServletRequestInfo, ArchiveAEExtension arcAE)
            throws Exception {
        return deleteStudy(
                createStudyDeleteContext(findStudy(studyUID), httpServletRequestInfo), arcAE, false, true);
    }

    private List<Location> deleteStudy(
            StudyDeleteContext ctx, ArchiveAEExtension arcAE, boolean retainObj, boolean reimport) throws Exception {
        Study study = ctx.getStudy();
        try {
            LOG.info("Start deleting {} from database", study);
            return deleteStudy(ctx, study, arcAE, retainObj, reimport);
        } catch (Exception e) {
            LOG.warn("Failed to delete {} from database:\n", study, e);
            ctx.setException(e);
            throw e;
        } finally {
            if (ctx.getStudy() != null)
                try {
                    studyDeletedEvent.fire(ctx);
                } catch (Exception e) {
                    LOG.warn("Unexpected exception in Study Deletion audit : " + e.getMessage());
                }
        }
    }

    @Override
    public void deletePatient(PatientMgtContext ctx, ArchiveAEExtension arcAE) throws Exception {
        LOG.info("Start deleting {} from database", ctx.getPatient());
        List<Study> resultList = em.createNamedQuery(Study.FIND_BY_PATIENT, Study.class)
                .setParameter(1, ctx.getPatient())
                .getResultList();
        for (Study study : resultList) {
            StudyDeleteContext studyDeleteCtx = createStudyDeleteContext(study, ctx.getHttpServletRequestInfo());
            studyDeleteCtx.setPatientDeletionTriggered(true);
            deleteStudy(studyDeleteCtx, arcAE, false, false);
        }
        patientService.deletePatient(ctx);
    }

    private List<Location> deleteStudy(
            StudyDeleteContext ctx, Study study, ArchiveAEExtension arcAE, boolean retainObj, boolean reimport)
            throws Exception {
        List<Location> locations = new ArrayList<>();
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        RejectionState rejectionState = study.getRejectionState();
        if (!ctx.isPatientDeletionTriggered()) {
            if (!reimport && arcAE.allowDeleteStudy() == AllowDeleteStudyPermanently.REJECTED
                && (rejectionState == RejectionState.NONE || rejectionState == RejectionState.PARTIAL)) {
                ctx.setStudy(study);
                throw new StudyNotEmptyException(
                        "Deletion of Study with Rejection State: " + rejectionState + " not permitted");
            }
        }
        if (ejb.updateStudyDeleting(study, true) == 0) {
            throw new StudyDeletionInProgressException(
                    "Deletion of Study[uid=" + study.getStudyInstanceUID() + "] in progress");
        }
        try {
            if (rejectionState == RejectionState.EMPTY) {
                ejb.deleteEmptyStudy(ctx);
                return locations;
            }
            List<Series> seriesWithPurgedInstances = ejb.findSeriesWithPurgedInstances(study.getPk());
            if (!seriesWithPurgedInstances.isEmpty()) {
                StoreSession session = storeService.newStoreSession(arcAE.getApplicationEntity());
                for (Series series : seriesWithPurgedInstances) {
                    storeService.restoreInstances(
                            session,
                            study.getStudyInstanceUID(),
                            series.getSeriesInstanceUID(),
                            arcDev.getPurgeInstanceRecordsDelay(), null);
                }
            }
            int limit = arcDev.getDeleteStudyChunkSize();
            if (!reimport) {
                LOG.debug("Marking objects of {} for deletion", study);
                int markForDeletion = ejb.markForDeletion(study, Location.ObjectType.DICOM_FILE,
                        retainObj ? LocationStatus.ORPHANED : LocationStatus.TO_DELETE);
                LOG.debug("Marked {} objects of {} for deletion", markForDeletion, study);
                ejb.markForDeletion(study, Location.ObjectType.METADATA, LocationStatus.TO_DELETE);
                int deleted;
                do {
                    markForDeletion -= (deleted = deleteInstancesWithoutLocationsOfStudy(ctx, study, limit));
                } while (deleted == limit);
                if (markForDeletion < 0)
                    LOG.info("Successfully delete {} instances of {} without locations from database",
                            -markForDeletion, study);
            }
            List<Location> locations1;
            while (!(locations1 = deleteStudy(ctx, limit, retainObj || reimport)).isEmpty())
                locations.addAll(locations1);
            if (ejb.updateStudyDeleting(study, false) != 0)
                LOG.warn("Incomplete deletion of Study[uid=" + study.getStudyInstanceUID() + ']');
            else
                LOG.info("Successfully delete {} from database", study);
        } catch (Exception e) {
            if (ejb.updateStudyDeleting(study, false) == 0) {
                LOG.warn("Failed to reset deletion in process flag on failed deletion of Study[uid="
                        + study.getStudyInstanceUID() + ']');
            }
            throw e;
        }
        return locations;
    }

    private List<Location> deleteStudy(StudyDeleteContext ctx, int limit, boolean orphaned) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        int retries = arcDev.getStoreUpdateDBMaxRetries();
        for (;;) {
            try {
                return ejb.deleteStudy(ctx, limit, orphaned);
            } catch (EJBException e) {
                if (retries-- > 0) {
                    LOG.info("Failure on deleting {} from database caused by {} - retry",
                            ctx.getStudy(),
                            DicomServiceException.initialCauseOf(e));
                } else {
                    LOG.warn("Failure on deleting {} from database:\n", ctx.getStudy(), e);
                    throw e;
                }
            }
            try {
                Thread.sleep(arcDev.storeUpdateDBRetryDelay());
            } catch (InterruptedException e) {
                LOG.info("Failed to delay retry of deleting study from database:\n", e);
            }
        }
    }

    private int deleteInstancesWithoutLocationsOfStudy(StudyDeleteContext ctx, Study study, int limit) {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        int retries = arcDev.getStoreUpdateDBMaxRetries();
        for (;;) {
            try {
                return ejb.deleteInstancesWithoutLocationsOfStudy(ctx, study, limit);
            } catch (EJBException e) {
                if (retries-- > 0) {
                    LOG.info("Failure on deleting {} from database caused by {} - retry",
                            ctx.getStudy(),
                            DicomServiceException.initialCauseOf(e));
                } else {
                    LOG.warn("Failure on deleting {} from database:\n", ctx.getStudy(), e);
                    throw e;
                }
            }
            try {
                Thread.sleep(arcDev.storeUpdateDBRetryDelay());
            } catch (InterruptedException e) {
                LOG.info("Failed to delay retry of deleting study from database:\n", e);
            }
        }
    }
}
