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
 *  Portions created by the Initial Developer are Copyright (C) 2015-2017
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

package org.dcm4chee.arc.query.impl;

import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.entity.Location;
import org.dcm4chee.arc.entity.Series;
import org.dcm4chee.arc.entity.Study;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Nov 2017
 */
@Stateless
@TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
public class QuerySizeEJB {

    private static final Long ZERO = Long.valueOf(0L);

    @PersistenceContext(unitName = "dcm4chee-arc")
    EntityManager em;

    public long calculateStudySize(Long studyPk) {
        for (Long seriesPk : em.createNamedQuery(Series.SERIES_PKS_OF_STUDY_WITH_UNKNOWN_SIZE, Long.class)
                .setParameter(1, studyPk)
                .getResultList()) {
            calculateSeriesSize(seriesPk);
        }
        Long size = StringUtils.maskNull(
                em.createNamedQuery(Series.SIZE_OF_STUDY, Long.class)
                    .setParameter(1, studyPk)
                    .getSingleResult(),
                ZERO);
        em.createNamedQuery(Study.SET_STUDY_SIZE)
                .setParameter(1, studyPk)
                .setParameter(2, size)
                .executeUpdate();
        return size;
    }

    public long calculateSeriesSize(Long seriesPk) {
        Object result = em.createNamedQuery(Location.SIZE_OF_SERIES)
                .setParameter(1, seriesPk)
                .setParameter(2, Location.ObjectType.DICOM_FILE.ordinal())
                .getSingleResult();
        long size = result instanceof Number ? ((Number) result).longValue() : 0L;
        em.createNamedQuery(Series.SET_SERIES_SIZE)
                .setParameter(1, seriesPk)
                .setParameter(2, size)
                .executeUpdate();
        return size;
    }

    public long calculateStudySize(String studyUID) {
        Long studyPk;
        try {
            studyPk = em.createNamedQuery(Study.FIND_PK_BY_STUDY_UID, Long.class)
                    .setParameter(1, studyUID)
                    .getSingleResult();
        } catch (NoResultException e) {
            return -1L;
        }
        return calculateStudySize(studyPk);
    }
}
