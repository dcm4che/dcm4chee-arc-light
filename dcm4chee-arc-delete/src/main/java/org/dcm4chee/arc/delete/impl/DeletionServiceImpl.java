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
 * *** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.delete.impl;


import org.dcm4che3.data.Code;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4chee.arc.conf.AllowDeleteStudyPermanently;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.delete.*;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.patient.PatientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.servlet.http.HttpServletRequest;
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
    private DeletionServiceEJB ejb;

    @Inject
    private PatientService patientService;

    @Inject
    private Device device;

    @Inject
    private Event<StudyDeleteContext> studyDeletedEvent;

    @Inject
    private Event<PatientMgtContext> patientMgtEvent;


    @Override
    public int deleteRejectedInstancesBefore(Code rjCode, Date before, int fetchSize) {
        return delete(before != null ? Location.FIND_BY_REJECTION_CODE_BEFORE : Location.FIND_BY_REJECTION_CODE,
                rjCode, before, fetchSize);
    }

    @Override
    public int deleteRejectionNotesBefore(Code rjCode, Date before, int fetchSize) {
        return delete(before != null ? Location.FIND_BY_CONCEPT_NAME_CODE_BEFORE : Location.FIND_BY_CONCEPT_NAME_CODE,
                rjCode, before, fetchSize);
    }

    private int delete(String queryName, Code rjCode, Date before, int fetchSize) {
        int total = 0;
        int deleted;
        do {
            total += deleted = ejb.deleteRejectedInstancesOrRejectionNotesBefore(queryName, rjCode, before, fetchSize);
        } while (deleted == fetchSize);
        return total;
    }

    @Override
    public StudyDeleteContext createStudyDeleteContext(Long pk, HttpServletRequest request) {
        StudyDeleteContext ctx = new StudyDeleteContextImpl(pk);
        ctx.setHttpRequest(request);
        return ctx;
    }

    @Override
    public void deleteStudy(String studyUID, HttpServletRequest request, ApplicationEntity ae)
            throws StudyNotFoundException, StudyNotEmptyException {
        StudyDeleteContext ctx = null;
        try {
            Study study = em.createNamedQuery(Study.FIND_BY_STUDY_IUID, Study.class)
                    .setParameter(1, studyUID).getSingleResult();
            ArchiveAEExtension arcAE = ae.getAEExtension(ArchiveAEExtension.class);
            AllowDeleteStudyPermanently allowDeleteStudy = arcAE.allowDeleteStudy();
            if (study != null) {
                ctx = createStudyDeleteContext(study.getPk(), request);
                boolean studyDeleted = studyDeleted(ctx, study, allowDeleteStudy);
                if (!studyDeleted)
                    throw new StudyNotEmptyException("Study is not empty. - ");
                if (allowDeleteStudy == AllowDeleteStudyPermanently.ALWAYS && studyDeleted
                        && (study.getRejectionState() == RejectionState.NONE || study.getRejectionState() == RejectionState.PARTIAL))
                    studyDeletedEvent.fire(ctx);
            }
            LOG.info("Successfully delete {} from database", ctx.getStudy());
        } catch (NoResultException e) {
            throw new StudyNotFoundException(e.getMessage());
        } catch (StudyNotEmptyException e) {
            ctx.setException(e);
            studyDeletedEvent.fire(ctx);
            throw e;
        } catch (Exception e) {
            LOG.warn("Failed to delete {} on {}", ctx.getStudy(), e);
            ctx.setException(e);
            studyDeletedEvent.fire(ctx);
        }
    }

    @Override
    public void deletePatient(PatientMgtContext ctx) {
        ejb.deleteMWLItemsOfPatient(ctx);
        List<Study> sList = em.createNamedQuery(Study.FIND_BY_PATIENT, Study.class)
                .setParameter(1, ctx.getPatient()).getResultList();
        StudyDeleteContext sCtx;
        if (!sList.isEmpty()) {
            for (Study study : sList) {
                try {
                    sCtx = createStudyDeleteContext(study.getPk(), ctx.getHttpRequest());
                    studyDeleted(sCtx, study, AllowDeleteStudyPermanently.REJECTED);
                } catch (Exception e) {
                    LOG.warn("Failed to delete {} on {}", study, e);
                    ctx.setException(e);
                    patientMgtEvent.fire(ctx);
                }
            }
        }
        patientService.deletePatientFromUI(ctx);
        LOG.info("Successfully delete {} from database", ctx.getPatient());
    }

    private boolean studyDeleted(StudyDeleteContext ctx, Study study, AllowDeleteStudyPermanently allowDeleteStudy) {
        ctx.setStudy(study);
        ctx.setPatient(study.getPatient());
        ctx.setDeletePatientOnDeleteLastStudy(false);
        if (study.getRejectionState() == RejectionState.COMPLETE || allowDeleteStudy == AllowDeleteStudyPermanently.ALWAYS) {
            ejb.deleteStudy(ctx);
            return true;
        }
        else if (study.getRejectionState() == RejectionState.EMPTY) {
            ejb.deleteEmptyStudy(ctx);
            return true;
        }
        else
            return false;
    }
}
