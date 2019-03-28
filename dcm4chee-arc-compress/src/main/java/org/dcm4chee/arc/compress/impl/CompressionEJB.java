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
 * Portions created by the Initial Developer are Copyright (C) 2015-2019
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
 *
 */

package org.dcm4chee.arc.compress.impl;

import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.entity.Location;
import org.dcm4chee.arc.entity.Series;
import org.dcm4chee.arc.entity.Study;
import org.dcm4chee.arc.query.impl.QuerySizeEJB;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Collections;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2018
 */
@Stateless
public class CompressionEJB {
    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    @Inject
    private QuerySizeEJB querySizeEJB;

    public List<Series.Compression> findSeriesForCompression(int fetchSize) {
        return em.createNamedQuery(Series.SCHEDULED_COMPRESSION, Series.Compression.class)
                .setMaxResults(fetchSize)
                .getResultList();
    }

    public boolean claimForCompression(Series.Compression compression) {
        return em.createNamedQuery(Series.CLAIM_COMPRESSION)
                .setParameter(1, compression.seriesPk)
                .setParameter(2, compression.compressionTime)
                .executeUpdate() > 0;
    }

    public void updateDB(Series.Compression compr, int completed, int failures) {
        if (completed > 0) {
            querySizeEJB.calculateSeriesSize(compr.seriesPk);
            querySizeEJB.calculateStudySize(compr.studyPk);
            em.createNamedQuery(Series.SCHEDULE_METADATA_UPDATE_FOR_SERIES)
                    .setParameter(1, compr.seriesPk);
            em.createNamedQuery(failures > 0
                        ? Series.UPDATE_COMPRESSION_FAILURES_AND_TSUID
                        : Series.UPDATE_COMPRESSION_COMPLETED)
                    .setParameter(1, compr.seriesPk)
                    .setParameter(2, failures)
                    .setParameter(3, compr.transferSyntaxUID)
                    .executeUpdate();
            List<String> storageIDs = em.createNamedQuery(Location.STORAGE_IDS_BY_STUDY_PK_AND_OBJECT_TYPE, String.class)
                    .setParameter(1, compr.studyPk)
                    .setParameter(2, Location.ObjectType.DICOM_FILE)
                    .getResultList();
            Collections.sort(storageIDs);
            em.createNamedQuery(Study.SET_STORAGE_IDS)
                    .setParameter(1, compr.studyPk)
                    .setParameter(2, StringUtils.concat(storageIDs, '\\'));
        } else if (failures > 0) {
            em.createNamedQuery(Series.UPDATE_COMPRESSION_FAILURES)
                    .setParameter(1, compr.seriesPk)
                    .setParameter(2, failures)
                    .executeUpdate();
        } else { // all skipped
            em.createNamedQuery(Series.UPDATE_COMPRESSION_COMPLETED)
                    .setParameter(1, compr.seriesPk)
                    .setParameter(2, failures)
                    .setParameter(3, compr.transferSyntaxUID)
                    .executeUpdate();
        }
    }
}
