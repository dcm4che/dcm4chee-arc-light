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
 * Portions created by the Initial Developer are Copyright (C) 2015-2018
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

package org.dcm4chee.arc.retrieve.impl;

import org.dcm4che3.data.Attributes;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.retrieve.SeriesInfo;
import org.dcm4chee.arc.retrieve.StudyInfo;

import javax.persistence.EntityManager;
import javax.persistence.Tuple;
import javax.persistence.criteria.*;
import java.util.Date;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Mar 2019
 */
class SeriesAttributes {
    final Attributes attrs;
    final StudyInfo studyInfo;
    final SeriesInfo seriesInfo;
    final Date patientUpdatedTime;

    SeriesAttributes(EntityManager em, CriteriaBuilder cb, Long seriesPk) {
        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        Root<Series> series = q.from(Series.class);
        Join<Series, Study> study = series.join(Series_.study);
        Join<Study, Patient> patient = study.join(Study_.patient);
        Path<byte[]> patientAttrBlob = patient.join(Patient_.attributesBlob).get(AttributesBlob_.encodedAttributes);
        Path<byte[]> studyAttrBlob = study.join(Study_.attributesBlob).get(AttributesBlob_.encodedAttributes);
        Path<byte[]> seriesAttrBlob = series.join(Series_.attributesBlob).get(AttributesBlob_.encodedAttributes);
        Tuple tuple = em.createQuery(q
                .where(cb.equal(series.get(Series_.pk), seriesPk))
                .multiselect(
                        patient.get(Patient_.updatedTime),
                        study.get(Study_.pk),
                        study.get(Study_.studyInstanceUID),
                        study.get(Study_.accessTime),
                        study.get(Study_.failedRetrieves),
                        study.get(Study_.completeness),
                        study.get(Study_.modifiedTime),
                        study.get(Study_.expirationDate),
                        study.get(Study_.accessControlID),
                        study.get(Study_.size),
                        series.get(Series_.seriesInstanceUID),
                        series.get(Series_.failedRetrieves),
                        series.get(Series_.completeness),
                        series.get(Series_.updatedTime),
                        series.get(Series_.expirationDate),
                        series.get(Series_.sourceAET),
                        series.get(Series_.size),
                        patientAttrBlob,
                        studyAttrBlob,
                        seriesAttrBlob))
                .getSingleResult();
        studyInfo = new StudyInfoImpl(
                tuple.get(study.get(Study_.pk)),
                tuple.get(study.get(Study_.studyInstanceUID)),
                tuple.get(study.get(Study_.accessTime)),
                tuple.get(study.get(Study_.failedRetrieves)),
                tuple.get(study.get(Study_.completeness)),
                tuple.get(study.get(Study_.modifiedTime)),
                tuple.get(study.get(Study_.expirationDate)),
                tuple.get(study.get(Study_.accessControlID)),
                tuple.get(study.get(Study_.size)));
        seriesInfo = new SeriesInfoImpl(
                studyInfo.getStudyInstanceUID(),
                tuple.get(series.get(Series_.seriesInstanceUID)),
                tuple.get(series.get(Series_.failedRetrieves)),
                tuple.get(series.get(Series_.completeness)),
                tuple.get(series.get(Series_.updatedTime)),
                tuple.get(series.get(Series_.expirationDate)),
                tuple.get(series.get(Series_.sourceAET)),
                tuple.get(series.get(Series_.size)));
        patientUpdatedTime = tuple.get(patient.get(Patient_.updatedTime));
        Attributes patAttrs = AttributesBlob.decodeAttributes(tuple.get(patientAttrBlob), null);
        Attributes studyAttrs = AttributesBlob.decodeAttributes(tuple.get(studyAttrBlob), null);
        Attributes seriesAttrs = AttributesBlob.decodeAttributes(tuple.get(seriesAttrBlob), null);
        Attributes.unifyCharacterSets(patAttrs, studyAttrs, seriesAttrs);
        attrs = new Attributes(patAttrs.size() + studyAttrs.size() + seriesAttrs.size() + 5);
        attrs.addAll(patAttrs);
        attrs.addAll(studyAttrs, true);
        attrs.addAll(seriesAttrs, true);
    }
}
