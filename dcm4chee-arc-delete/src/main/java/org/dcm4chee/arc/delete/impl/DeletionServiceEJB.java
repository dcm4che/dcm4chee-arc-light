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
 * Portions created by the Initial Developer are Copyright (C) 2015
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
import org.dcm4chee.arc.code.CodeCache;
import org.dcm4chee.arc.conf.StorageDescriptor;
import org.dcm4chee.arc.entity.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.*;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
@Stateless
public class DeletionServiceEJB {

    static final Logger LOG = LoggerFactory.getLogger(DeletionServiceEJB.class);

    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    @Inject
    private CodeCache codeCahe;

    public List<Location> findLocationsToDelete(String storageID, int limit) {
        return em.createNamedQuery(Location.FIND_BY_STORAGE_ID_AND_STATUS, Location.class)
                .setParameter(1, storageID)
                .setParameter(2, Location.Status.TO_DELETE)
                .setMaxResults(limit)
                .getResultList();
    }

    public List<Long> findStudiesForDeletionOnStorage(String storageID, int limit) {
        return em.createNamedQuery(Study.FIND_PK_BY_STORAGE_ID_ORDER_BY_ACCESS_TIME, Long.class)
                .setParameter(1, storageID)
                .setMaxResults(limit)
                .getResultList();
    }

    public void failedToDelete(Location location) {
        location.setStatus(Location.Status.FAILED_TO_DELETE);
        em.merge(location);
    }

    public void removeLocation(Location location) {
        em.remove(em.merge(location));
    }

    public boolean removeStudyOnStorage(Long studyPk, boolean deletePatient) {
        List<String> storageIDs = em.createNamedQuery(Location.FIND_STORAGE_IDS_BY_STUDY_PK, String.class)
                .setParameter(1, studyPk)
                .getResultList();
        if (storageIDs.size() > 1) {
            Study study = em.find(Study.class, studyPk);
            study.setScatteredStorage(true);
            LOG.info("objects of {} scattered over Storages{} - will not be deleted", study, storageIDs);
            return false;
        }
        List<Location> locations = em.createNamedQuery(Location.FIND_BY_STUDY_PK, Location.class)
                .setParameter(1, studyPk)
                .getResultList();
        List<Study> deleteWholeStudy = new ArrayList<>(1);
        deleteInstances(locations, deleteWholeStudy, deletePatient);
        LOG.info("Successfully delete {} on Storage{} from database", deleteWholeStudy.get(0), storageIDs);
        return true;
    }

    public int deleteRejectedInstancesAndRejectionNotes(Code rejectionCode, int limit) {
        CodeEntity codeEntity = codeCahe.findOrCreate(rejectionCode);
        List<Location> locations = em.createNamedQuery(Location.FIND_BY_REJECTION_OR_CONCEPT_NAME_CODE, Location.class)
                .setParameter(1, codeEntity)
                .setMaxResults(limit)
                .getResultList();
        return deleteInstances(locations, null, false);
    }

    public int deleteRejectedInstancesOrRejectionNotesBefore(
            String queryName, Code rejectionCode, Date before, int limit) {
        CodeEntity codeEntity = codeCahe.findOrCreate(rejectionCode);
        List<Location> locations = em.createNamedQuery(queryName, Location.class)
                .setParameter(1, codeEntity)
                .setParameter(2, before)
                .setMaxResults(limit)
                .getResultList();
        return deleteInstances(locations, null, false);
    }

    private int deleteInstances(List<Location> locations, List<Study> deleteWholeStudy, boolean deletePatient) {
        if (locations.isEmpty())
            return 0;

        HashMap<Long,Instance> insts = new HashMap<>();
        for (Location location : locations) {
            Instance inst = location.getInstance();
            insts.put(inst.getPk(), inst);
            location.setInstance(null);
            location.setStatus(Location.Status.TO_DELETE);
        }
        HashMap<Long,Series> series = new HashMap<>();
        for (Instance inst : insts.values()) {
            Series ser = inst.getSeries();
            if (!series.containsKey(ser.getPk())) {
                series.put(ser.getPk(), ser);
                deleteSeriesQueryAttributes(ser);
            }
            em.remove(inst);
        }
        HashMap<Long,Study> studies = new HashMap<>();
        for (Series ser : series.values()) {
            Study study = ser.getStudy();
            if (!studies.containsKey(study.getPk())) {
                studies.put(study.getPk(), study);
                deleteStudyQueryAttributes(study);
            }
            if (deleteWholeStudy != null || countInstancesOfSeries(ser) == 0) {
                em.remove(ser);
            } else {
                studies.put(study.getPk(), null);
            }
        }
        Patient patient = null;
        for (Study study : studies.values()) {
            if (study == null)
                continue;

            if (deleteWholeStudy != null) {
                deleteWholeStudy.add(study);
            } else if (countSeriesOfStudy(study) > 0) {
                deletePatient = false;
                continue;
            }
            if (patient == null && deletePatient)
                patient = study.getPatient();
            em.remove(study);
        }
        if (patient != null) {
            deletePatient(patient);
        }
        return insts.size();
    }

    private void deletePatient(Patient patient) {
        if (em.createNamedQuery(Patient.COUNT_BY_MERGED_WITH, Long.class)
                .setParameter(1, patient)
                .getSingleResult() > 0) {
             return;
        }
        List<MPPS> mppsList = em.createNamedQuery(MPPS.FIND_BY_PATIENT, MPPS.class)
                .setParameter(1, patient)
                .getResultList();
        for (MPPS mpps : mppsList)
            em.remove(mpps);
        em.remove(patient);
    }

    private long countSeriesOfStudy(Study study) {
        return em.createNamedQuery(Series.COUNT_SERIES_OF_STUDY, Long.class).setParameter(1, study)
                .getSingleResult();
    }

    private long countInstancesOfSeries(Series series) {
        return em.createNamedQuery(Instance.COUNT_INSTANCES_OF_SERIES, Long.class).setParameter(1, series).
                getSingleResult();
    }

    private int deleteStudyQueryAttributes(Study study) {
        return em.createNamedQuery(StudyQueryAttributes.DELETE_FOR_STUDY).setParameter(1, study).executeUpdate();
    }

    private int deleteSeriesQueryAttributes(Series series) {
        return em.createNamedQuery(SeriesQueryAttributes.DELETE_FOR_SERIES).setParameter(1, series).executeUpdate();
    }
}
