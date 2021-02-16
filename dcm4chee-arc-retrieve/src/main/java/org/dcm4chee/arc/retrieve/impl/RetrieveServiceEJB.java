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

package org.dcm4chee.arc.retrieve.impl;

import org.dcm4chee.arc.entity.Completeness;
import org.dcm4chee.arc.entity.Series;
import org.dcm4chee.arc.entity.Study;
import org.dcm4chee.arc.entity.UIDMap;
import org.dcm4chee.arc.retrieve.RetrieveContext;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jan 2016
 */
@Stateless
public class RetrieveServiceEJB {

    @PersistenceContext(unitName = "dcm4chee-arc")
    private EntityManager em;

    public void updateStudyAccessTime(Long studyPk) {
        em.createNamedQuery(Study.UPDATE_ACCESS_TIME)
                .setParameter(1, studyPk)
                .executeUpdate();
    }

    public void updateCompleteness(RetrieveContext ctx, Completeness completeness) {
        String[] studyIUIDs = ctx.getStudyInstanceUIDs();
        String[] seriesIUIDs = ctx.getSeriesInstanceUIDs();
        switch (ctx.getQueryRetrieveLevel()) {
            case STUDY:
                for (String studyIUID : studyIUIDs)
                    setCompletenessOfStudy(studyIUID, completeness);
                break;
            case SERIES:
                for (String seriesIUID : seriesIUIDs) {
                    setCompletenessOfSeries(studyIUIDs[0], seriesIUID, completeness);
                }
                setCompletenessOfStudy(studyIUIDs[0], Completeness.UNKNOWN);
                break;
            case IMAGE:
                setCompletenessOfSeries(studyIUIDs[0], seriesIUIDs[0], Completeness.UNKNOWN);
                setCompletenessOfStudy(studyIUIDs[0], Completeness.UNKNOWN);
                break;
        }
    }

    public UIDMap getUIDMapReference(Long uidMapPk, Map<Long, UIDMap> uidMapCache) {
        UIDMap uidMap = uidMapCache.get(uidMapPk) ;
        if ( null == uidMap ) {
            uidMap = em.find(UIDMap.class, uidMapPk) ;
            uidMapCache.put(uidMapPk, uidMap) ;
        }
        return uidMap;
    }

    private void setCompletenessOfStudy(String studyInstanceUID, Completeness completeness) {
        em.createNamedQuery(completeness == Completeness.PARTIAL
                ? Study.INCREMENT_FAILED_RETRIEVES
                : Study.SET_COMPLETENESS)
                .setParameter(1, studyInstanceUID)
                .setParameter(2, completeness)
                .executeUpdate();
        if (completeness != Completeness.UNKNOWN)
            em.createNamedQuery(Series.SET_COMPLETENESS_OF_STUDY)
                    .setParameter(1, studyInstanceUID)
                    .setParameter(2, Completeness.COMPLETE)
                    .executeUpdate();
    }

    private void setCompletenessOfSeries(String studyInstanceUID, String seriesInstanceUID,
                                         Completeness completeness) {
        em.createNamedQuery(completeness == Completeness.PARTIAL
                ? Series.INCREMENT_FAILED_RETRIEVES
                : Series.SET_COMPLETENESS)
                .setParameter(1, studyInstanceUID)
                .setParameter(2, seriesInstanceUID)
                .setParameter(3, completeness)
                .executeUpdate();
    }
}
