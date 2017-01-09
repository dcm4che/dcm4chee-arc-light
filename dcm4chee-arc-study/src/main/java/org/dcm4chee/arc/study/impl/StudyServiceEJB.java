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

package org.dcm4chee.arc.study.impl;

import org.dcm4che3.audit.AuditMessages;
import org.dcm4che3.data.*;
import org.dcm4chee.arc.code.CodeCache;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.AttributeFilter;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.issuer.IssuerService;
import org.dcm4chee.arc.patient.PatientMismatchException;
import org.dcm4chee.arc.study.StudyMgtContext;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jun 2016
 */
@Stateless
public class StudyServiceEJB {
    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    @Inject
    private CodeCache codeCache;

    @Inject
    private IssuerService issuerService;

    public void updateStudy(StudyMgtContext ctx) {
        ArchiveAEExtension arcAE = ctx.getArchiveAEExtension();
        AttributeFilter filter = ctx.getStudyAttributeFilter();
        Attributes attrs = new Attributes(ctx.getAttributes(), filter.getSelection());
        try {
            Study study = em.createNamedQuery(Study.FIND_BY_STUDY_IUID_EAGER, Study.class)
                    .setParameter(1, ctx.getStudyInstanceUID())
                    .getSingleResult();
            if (attrs.equals(study.getAttributes()))
                return;

            ctx.setEventActionCode(AuditMessages.EventActionCode.Update);
            ctx.setStudy(study);
            if (study.getPatient().getPk() != ctx.getPatient().getPk())
                throw new PatientMismatchException("" + ctx.getPatient() + " does not match " +
                        study.getPatient() + " in existing " + study);

            study.setAttributes(attrs, filter, ctx.getFuzzyStr());
            study.setIssuerOfAccessionNumber(
                    findOrCreateIssuer(attrs.getNestedDataset(Tag.IssuerOfAccessionNumberSequence)));
            setCodes(study.getProcedureCodes(), attrs.getSequence(Tag.ProcedureCodeSequence));
            em.createNamedQuery(Series.SCHEDULE_METADATA_UPDATE_FOR_STUDY)
                    .setParameter(1, study)
                    .executeUpdate();
        } catch (NoResultException e) {
            ctx.setEventActionCode(AuditMessages.EventActionCode.Create);
            Study study = new Study();
            study.addStorageID(arcAE.storageID());
            study.setRejectionState(RejectionState.EMPTY);
            study.setAccessControlID(arcAE.storeAccessControlID(
                    ctx.getRemoteHostName(), null, ctx.getApplicationEntity().getAETitle(), ctx.getAttributes()));
            study.setAttributes(attrs, filter, ctx.getFuzzyStr());
            study.setIssuerOfAccessionNumber(
                    findOrCreateIssuer(attrs.getNestedDataset(Tag.IssuerOfAccessionNumberSequence)));
            setCodes(study.getProcedureCodes(), attrs.getSequence(Tag.ProcedureCodeSequence));
            study.setPatient(ctx.getPatient());
            ctx.setStudy(study);
            em.persist(study);
        }
    }

    public void updateStudyExpirationDate(StudyMgtContext ctx) throws NoResultException {
        ctx.setEventActionCode(AuditMessages.EventActionCode.Update);
        List<Series> seriesOfStudy = em.createNamedQuery(Series.FIND_SERIES_OF_STUDY, Series.class)
                .setParameter(1, ctx.getStudyInstanceUID()).getResultList();
        LocalDate studyExpireDate = ctx.getExpirationDate();
        Study study;
        if (!seriesOfStudy.isEmpty()) {
            study = seriesOfStudy.get(0).getStudy();
            study.setExpirationDate(studyExpireDate);
            for (Series series : seriesOfStudy) {
                LocalDate seriesExpirationDate = series.getExpirationDate();
                if (seriesExpirationDate != null && seriesExpirationDate.isAfter(studyExpireDate))
                    series.setExpirationDate(studyExpireDate);
            }
        } else {
            study = em.createNamedQuery(Study.FIND_BY_STUDY_IUID, Study.class)
                    .setParameter(1, ctx.getStudyInstanceUID()).getSingleResult();
            study.setExpirationDate(studyExpireDate);
        }
        ctx.setStudy(study);
        ctx.setAttributes(study.getAttributes());
    }

    public void updateSeriesExpirationDate(StudyMgtContext ctx) throws NoResultException {
        Series series = em.createNamedQuery(Series.FIND_BY_SERIES_IUID, Series.class)
                .setParameter(1, ctx.getStudyInstanceUID())
                .setParameter(2, ctx.getSeriesInstanceUID()).getSingleResult();
        LocalDate studyExpirationDate = series.getStudy().getExpirationDate();
        series.setExpirationDate(ctx.getExpirationDate());
        if (studyExpirationDate == null || studyExpirationDate.isBefore(ctx.getExpirationDate())) {
            Study study = series.getStudy();
            study.setExpirationDate(ctx.getExpirationDate());
            ctx.setStudy(study);
            ctx.setAttributes(study.getAttributes());
            ctx.setEventActionCode(AuditMessages.EventActionCode.Update);
        }
    }

    private void setCodes(Collection<CodeEntity> codes, Sequence seq) {
        codes.clear();
        if (seq != null)
            for (Attributes item : seq) {
                codes.add(codeCache.findOrCreate(new Code(item)));
            }
    }

    private IssuerEntity findOrCreateIssuer(Attributes item) {
        return item != null && !item.isEmpty() ? issuerService.mergeOrCreate(new Issuer(item)) : null;
    }
}
