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


import org.dcm4che3.data.Code;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.AllowDeleteStudyPermanently;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.delete.*;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.patient.PatientService;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.store.StoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import java.io.IOException;
import java.util.Date;

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
    public void deleteStudy(String studyUID, HttpServletRequestInfo httpServletRequestInfo, ArchiveAEExtension arcAE)
            throws Exception {
        try {
            deleteStudy(em.createNamedQuery(Study.FIND_BY_STUDY_IUID, Study.class)
                    .setParameter(1, studyUID).getSingleResult(),
                httpServletRequestInfo,
                arcAE,
            null);
        } catch (NoResultException e) {
            throw new StudyNotFoundException(e.getMessage());
        }
    }

    private void deleteStudy(Study study, HttpServletRequestInfo httpServletRequestInfo, ArchiveAEExtension arcAE,
                       PatientMgtContext pCtx) throws Exception {
        StudyDeleteContext ctx = createStudyDeleteContext(study.getPk(), httpServletRequestInfo);
        try {
            studyDeleted(ctx, study, arcAE, pCtx);
            LOG.info("Successfully delete {} from database", study);
        } catch (Exception e) {
            LOG.warn("Failed to delete {} on {}", study, e);
            ctx.setException(e);
            throw e;
        } finally {
            studyDeletedEvent.fire(ctx);
        }
    }

    @Override
    public void deletePatient(PatientMgtContext ctx, ArchiveAEExtension arcAE) {
        em.createNamedQuery(Study.FIND_BY_PATIENT, Study.class)
                .setParameter(1, ctx.getPatient())
                .getResultList()
                .forEach(study -> {
                    try {
                        deleteStudy(study, ctx.getHttpServletRequestInfo(), arcAE, ctx);
                    } catch (Exception e) {
                        ctx.setException(e);
                    }
                });
        if (ctx.getException() == null) {
            patientService.deletePatient(ctx);
            LOG.info("Successfully delete {} from database", ctx.getPatient());
        }

        patientMgtEvent.fire(ctx);
    }

    private void studyDeleted(StudyDeleteContext ctx, Study study, ArchiveAEExtension arcAE, PatientMgtContext pCtx)
            throws Exception {
        AllowDeleteStudyPermanently allowDeleteStudy = AllowDeleteStudyPermanently.ALWAYS;
        ctx.setStudy(study);
        ctx.setPatient(study.getPatient());
        if (pCtx == null) {
            ctx.setDeletePatientOnDeleteLastStudy(arcAE.getArchiveDeviceExtension().isDeletePatientOnDeleteLastStudy());
            allowDeleteStudy = arcAE.allowDeleteStudy();
        }
        RejectionState rejectionState = study.getRejectionState();

        if (rejectionState == RejectionState.NONE && allowDeleteStudy == AllowDeleteStudyPermanently.ALWAYS) {
            ejb.findSeriesWithPurgedInstances(study.getPk())
                    .forEach(series ->
                        {
                            try {
                                storeService.restoreInstances(
                                        storeService.newStoreSession(device.getApplicationEntities().iterator().next()),
                                        study.getStudyInstanceUID(),
                                        series.getSeriesInstanceUID(),
                                        device.getDeviceExtension(ArchiveDeviceExtension.class).getPurgeInstanceRecordsDelay());
                            } catch (IOException e) {
                                LOG.info("Restore instances failed for series {} \n", series.getSeriesInstanceUID(), e);
                            }
                        });
            ejb.deleteStudy(ctx);
            return;
        }

        if (rejectionState == RejectionState.COMPLETE
                || allowDeleteStudy == AllowDeleteStudyPermanently.ALWAYS)
            ejb.deleteStudy(ctx);
        else if (rejectionState == RejectionState.EMPTY)
            ejb.deleteEmptyStudy(ctx);
        else
            throw new StudyNotEmptyException("Study is not empty.");
    }
}
