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
 * *** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.study.impl;

import org.dcm4che3.audit.AuditMessages;
import org.dcm4che3.data.*;
import org.dcm4che3.net.Device;
import org.dcm4che3.soundex.FuzzyStr;
import org.dcm4chee.arc.code.CodeCache;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.AttributeFilter;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.patient.PatientMismatchException;
import org.dcm4chee.arc.patient.PatientService;
import org.dcm4chee.arc.study.StudyMgtContext;
import org.dcm4chee.arc.study.StudyMissingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jun 2016
 */
@Stateless
public class StudyServiceEJB {
    private static final Logger LOG = LoggerFactory.getLogger(StudyServiceEJB.class);

    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    @Inject
    private CodeCache codeCache;

    @Inject
    private PatientService patientService;

    @Inject
    private Device device;

    public void updateStudy(StudyMgtContext ctx) throws StudyMissingException, PatientMismatchException {
        AttributeFilter filter = ctx.getStudyAttributeFilter();
        Study study = findStudy(ctx.getStudyInstanceUID());
        Attributes attrs = study.getAttributes();
        Attributes newAttrs = new Attributes(ctx.getAttributes(), filter.getSelection(false));
        Attributes modified = new Attributes();
        if (attrs.diff(newAttrs, filter.getSelection(false), modified, true) == 0)
            return;

        ctx.setEventActionCode(AuditMessages.EventActionCode.Update);
        ctx.setStudy(study);
        if (ctx.getPatient() == null)
            ctx.setPatient(study.getPatient());
        else if (study.getPatient().getPk() != ctx.getPatient().getPk())
            throw new PatientMismatchException("" + ctx.getPatient() + " does not match " +
                    study.getPatient() + " in existing " + study);

        Attributes.unifyCharacterSets(newAttrs, attrs);
        newAttrs.addSelected(attrs, null, Tag.OriginalAttributesSequence);
        attrs = newAttrs;
        study.setAttributes(ctx.getArchiveAEExtension().recordAttributeModification()
                ? attrs.addOriginalAttributes(
                    null,
                    new Date(),
                    Attributes.CORRECT,
                    device.getDeviceName(),
                    modified)
                : attrs,
                filter, true, ctx.getFuzzyStr());
        setCodes(study.getProcedureCodes(), attrs.getSequence(Tag.ProcedureCodeSequence));
        em.createNamedQuery(Series.SCHEDULE_METADATA_UPDATE_FOR_STUDY)
                .setParameter(1, study)
                .executeUpdate();
    }

    public void updateSeries(StudyMgtContext ctx) throws StudyMissingException, PatientMismatchException {
        AttributeFilter filter = ctx.getSeriesAttributeFilter();
        Series series = findSeries(ctx);
        Attributes attrs = series.getAttributes();
        Attributes newAttrs = new Attributes(ctx.getAttributes(), filter.getSelection(false));
        Attributes modified = new Attributes();
        if (attrs.diff(newAttrs, filter.getSelection(false), modified, true) == 0)
            return;

        ctx.setEventActionCode(AuditMessages.EventActionCode.Update);
        ctx.setStudy(series.getStudy());
        if (ctx.getPatient() == null)
            ctx.setPatient(series.getStudy().getPatient());
        else if (series.getStudy().getPatient().getPk() != ctx.getPatient().getPk())
            throw new PatientMismatchException("" + ctx.getPatient() + " does not match " +
                    series.getStudy().getPatient() + " in existing " + series.getStudy());

        Attributes.unifyCharacterSets(newAttrs, attrs);
        newAttrs.addSelected(attrs, null, Tag.OriginalAttributesSequence);
        attrs = newAttrs;
        series.setAttributes(ctx.getArchiveAEExtension().recordAttributeModification()
                        ? attrs.addOriginalAttributes(
                        null,
                        new Date(),
                        Attributes.CORRECT,
                        device.getDeviceName(),
                        modified)
                        : attrs,
                filter, true, ctx.getFuzzyStr());
        em.createNamedQuery(Series.SCHEDULE_METADATA_UPDATE_FOR_SERIES)
                .setParameter(1, series.getPk())
                .executeUpdate();
    }

    public void updateStudyRequest(StudyMgtContext ctx) throws StudyMissingException {
        AttributeFilter studyAttrFilter = ctx.getStudyAttributeFilter();
        List<Series> seriesList = em.createNamedQuery(Series.FIND_SERIES_OF_STUDY_BY_STUDY_IUID_EAGER, Series.class)
                .setParameter(1, ctx.getStudyInstanceUID()).getResultList();
        if (seriesList.isEmpty())
            throw new StudyMissingException("Study to be updated does not exist: " + ctx.getStudyInstanceUID());

        for (Series series : seriesList) {
            updateSeriesRequest(ctx, series);
        }
        if (ctx.getEventActionCode() == null)
            return;

        IDWithIssuer accWithIssuer = null;
        for (Attributes requestAttribute : ctx.getRequestAttributes()) {
            IDWithIssuer accWithIssuerI = IDWithIssuer.valueOf(requestAttribute,
                    Tag.AccessionNumber, Tag.IssuerOfAccessionNumberSequence);
            if (accWithIssuerI != null)
                if (accWithIssuer == null)
                    accWithIssuer = accWithIssuerI;
                else if (!accWithIssuer.equals(accWithIssuerI))
                    return;
        }
        if (accWithIssuer == null)
            return;

        Study study = ctx.getStudy();
        Attributes attrs = study.getAttributes();
        if (accWithIssuer.equals(IDWithIssuer.valueOf(attrs, Tag.AccessionNumber, Tag.IssuerOfAccessionNumberSequence)))
            return;

        Attributes modified = ctx.getArchiveAEExtension().recordAttributeModification()
                ? new Attributes(attrs, Tag.AccessionNumber, Tag.IssuerOfAccessionNumberSequence)
                : null;

        attrs.setString(Tag.AccessionNumber, VR.SH, accWithIssuer.getID());
        if (accWithIssuer.getIssuer() == null)
            attrs.setNull(Tag.IssuerOfAccessionNumberSequence, VR.SQ);
        else
            attrs.newSequence(Tag.IssuerOfAccessionNumberSequence, 1).add(accWithIssuer.getIssuer().toItem());

        study.setAttributes(modified != null
                        ? attrs.addOriginalAttributes(
                        null,
                        new Date(),
                        Attributes.COERCE,
                        device.getDeviceName(),
                        modified)
                        : attrs,
                studyAttrFilter, true, ctx.getFuzzyStr());
    }

    public void updateSeriesRequest(StudyMgtContext ctx) throws StudyMissingException {
        updateSeriesRequest(ctx, findSeries(ctx));
    }

    private void updateSeriesRequest(StudyMgtContext ctx, Series series) {
        Attributes seriesAttr = series.getAttributes();
        List<Attributes> requestAttrs = ctx.getRequestAttributes();
        Sequence origRequestAttributes = seriesAttr.getSequence(Tag.RequestAttributesSequence);
        if (Objects.equals(origRequestAttributes, requestAttrs))
            return;

        Attributes modified = null;
        if (ctx.getArchiveAEExtension().recordAttributeModification() && origRequestAttributes != null) {
            modified = new Attributes(1);
            Sequence rqAttrsSeq = modified.newSequence(Tag.RequestAttributesSequence, origRequestAttributes.size());
            for (Attributes requestAttr : origRequestAttributes) {
                rqAttrsSeq.add(new Attributes(requestAttr));
            }
        }
        FuzzyStr fuzzyStr = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class).getFuzzyStr();
        Sequence rqAttrsSeq = seriesAttr.newSequence(Tag.RequestAttributesSequence, requestAttrs.size());
        Collection<SeriesRequestAttributes> requestAttributes = series.getRequestAttributes();
        requestAttributes.clear();

        for (Attributes requestAttr : requestAttrs) {
            rqAttrsSeq.add(new Attributes(requestAttr));
            requestAttributes.add(new SeriesRequestAttributes(requestAttr, fuzzyStr));
        }

        AttributeFilter seriesAttrFilter = ctx.getSeriesAttributeFilter();
        series.setAttributes(modified != null
                        ? seriesAttr.addOriginalAttributes(
                        null,
                        new Date(),
                        Attributes.COERCE,
                        device.getDeviceName(),
                        modified)
                        : seriesAttr,
                seriesAttrFilter, true, ctx.getFuzzyStr());
        ctx.setStudy(series.getStudy());
        ctx.setPatient(series.getStudy().getPatient());
        ctx.setEventActionCode(AuditMessages.EventActionCode.Update);
    }

    private Study findStudy(String studyUID) throws StudyMissingException {
        try {
            return em.createNamedQuery(Study.FIND_BY_STUDY_IUID_EAGER, Study.class)
                    .setParameter(1, studyUID)
                    .getSingleResult();
        } catch (NoResultException e) {
            throw new StudyMissingException("Study to be updated does not exist: " + studyUID);
        }
    }

    private Series findSeries(StudyMgtContext ctx) throws StudyMissingException {
        try {
            return em.createNamedQuery(Series.FIND_BY_SERIES_IUID_EAGER, Series.class)
                    .setParameter(1, ctx.getStudyInstanceUID())
                    .setParameter(2, ctx.getSeriesInstanceUID())
                    .getSingleResult();
        } catch (NoResultException e) {
            throw new StudyMissingException("Series[uid=" + ctx.getSeriesInstanceUID()
                    + "] of Study[uid=" + ctx.getStudyInstanceUID()
                    + "] to be updated does not exist");
        }
    }

    private void updateStudyExpirationDate(StudyMgtContext ctx) {
        List<Series> seriesOfStudy = em.createNamedQuery(Series.FIND_SERIES_OF_STUDY, Series.class)
                .setParameter(1, ctx.getStudyInstanceUID()).getResultList();
        Study study = !seriesOfStudy.isEmpty()
                ? seriesOfStudy.get(0).getStudy()
                : em.createNamedQuery(Study.FIND_BY_STUDY_IUID, Study.class)
                    .setParameter(1, ctx.getStudyInstanceUID()).getSingleResult();

        ctx.setStudy(study);
        ctx.setPatient(study.getPatient());
        ctx.setAttributes(study.getAttributes());
        ctx.setEventActionCode(AuditMessages.EventActionCode.Update);

        ExpirationOperation expirationOp = ExpirationOperation.compute(ctx, study);
        if (expirationOp == ExpirationOperation.Protect) {
            studyExpirationTo(expirationOp, ctx, study);
            seriesOfStudy.forEach(series ->
                    seriesExpirationTo(expirationOp, ctx, series));
            return;
        }
        if (expirationOp == ExpirationOperation.Skip) {
            LOG.info("{} updating {} Study[UID={}, ExpirationDate[={}]] with ExpirationDate[={}]",
                    expirationOp.name(), expirationOp.expirationState,
                    study.getStudyInstanceUID(), study.getExpirationDate(), ctx.getExpirationDate());
            ctx.setEventActionCode(null);
            return;
        }

        studyExpirationTo(expirationOp, ctx, study);
        if (expirationOp == ExpirationOperation.Update) {
            seriesOfStudy.forEach(series -> {
                LocalDate seriesExpirationDate = series.getExpirationDate();
                if (seriesExpirationDate != null && seriesExpirationDate.isAfter(ctx.getExpirationDate()))
                    seriesExpirationTo(expirationOp, ctx, series);
            });
        } else
            seriesOfStudy.forEach(series -> seriesExpirationTo(expirationOp, ctx, series));
    }

    enum ExpirationOperation {
        Freeze(ExpirationState.FROZEN),
        Unfreeze(ExpirationState.UPDATEABLE),
        Protect(ExpirationState.FROZEN),
        Update(ExpirationState.UPDATEABLE),
        Skip(ExpirationState.FROZEN);

        final ExpirationState expirationState;

        ExpirationOperation(ExpirationState expirationState) {
            this.expirationState = expirationState;
        }

        static ExpirationOperation compute(StudyMgtContext ctx, Study study) {
            return ctx.getExpirationDate() == null
                    ? ExpirationOperation.Protect
                    : study.getExpirationState() == ExpirationState.FROZEN
                        ? ctx.isUnfreezeExpirationDate()
                            ? ExpirationOperation.Unfreeze
                            : ExpirationOperation.Skip
                        : ctx.isFreezeExpirationDate()
                            ? ExpirationOperation.Freeze
                            : ExpirationOperation.Update;
        }
    }

    private void studyExpirationTo(ExpirationOperation expirationOp, StudyMgtContext ctx, Study study) {
        LOG.info("{} Study[UID={}] with ExpirationDate[={}] and ExpirationState[={}]",
                expirationOp.name(), study.getStudyInstanceUID(), ctx.getExpirationDate(), expirationOp.expirationState);
        study.setExpirationDate(ctx.getExpirationDate());
        study.setExpirationExporterID(ctx.getExpirationExporterID());
        study.setExpirationState(expirationOp.expirationState);
    }

    private void seriesExpirationTo(ExpirationOperation expirationOp, StudyMgtContext ctx, Series series) {
        LOG.info("{} Series[UID={}] with ExpirationDate[={}] and ExpirationState[={}]",
                expirationOp.name(), series.getSeriesInstanceUID(), ctx.getExpirationDate(), expirationOp.expirationState);
        series.setExpirationDate(ctx.getExpirationDate());
        series.setExpirationExporterID(ctx.getExpirationExporterID());
        series.setExpirationState(expirationOp.expirationState);
    }

    private void updateSeriesExpirationDate(StudyMgtContext ctx) {
        Series series = em.createNamedQuery(Series.FIND_BY_SERIES_IUID, Series.class)
                .setParameter(1, ctx.getStudyInstanceUID())
                .setParameter(2, ctx.getSeriesInstanceUID()).getSingleResult();
        Study study = series.getStudy();
        LocalDate expirationDate = ctx.getExpirationDate();
        if (series.getExpirationState() == ExpirationState.FROZEN) {
            LOG.info("Skip updating frozen Series[UID={}, ExpirationDate={}] of Study[UID={}] with ExpirationDate[={}]",
                    series.getSeriesInstanceUID(), series.getExpirationDate(),
                    study.getStudyInstanceUID(), expirationDate);
            return;
        }

        LocalDate studyExpirationDate = study.getExpirationDate();
        seriesExpirationTo(ExpirationOperation.Update, ctx, series);
        ctx.setStudy(study);
        ctx.setPatient(study.getPatient());
        ctx.setAttributes(study.getAttributes());
        if (studyExpirationDate == null || studyExpirationDate.isBefore(expirationDate))
            studyExpirationTo(ExpirationOperation.Update, ctx, study);
        ctx.setEventActionCode(AuditMessages.EventActionCode.Update);
    }

    public void updateExpirationDate(StudyMgtContext ctx) {
        if (ctx.getSeriesInstanceUID() != null) {
            updateSeriesExpirationDate(ctx);
            return;
        }
        updateStudyExpirationDate(ctx);
    }

    public void updateAccessControlID(StudyMgtContext ctx) throws StudyMissingException {
        if (ctx.getAttributes() != null) {
            em.createNamedQuery(Study.UPDATE_ACCESS_CONTROL_ID)
                    .setParameter(1, ctx.getStudyInstanceUID())
                    .setParameter(2, ctx.getAccessControlID())
                    .executeUpdate();
            return;
        }

        Study study = findStudy(ctx.getStudyInstanceUID());
        ctx.setAttributes(study.getAttributes());
        ctx.setPatient(study.getPatient());
        ctx.setEventActionCode(AuditMessages.EventActionCode.Update);
        study.setAccessControlID(ctx.getAccessControlID());
    }

    public void moveStudyToPatient(String studyUID, PatientMgtContext ctx)
            throws StudyMissingException {
        Study study = findStudy(studyUID);
        Patient patient = patientService.updatePatient(ctx);
        patient.incrementNumberOfStudies();
        study.getPatient().decrementNumberOfStudies();
        study.setPatient(patient);
    }

    private void setCodes(Collection<CodeEntity> codes, Sequence seq) {
        codes.clear();
        if (seq != null)
            for (Attributes item : seq) {
                try {
                    codes.add(codeCache.findOrCreate(new Code(item)));
                } catch (Exception e) {
                    LOG.info("Illegal Procedure Code:\n{}", item);
                }
            }
    }
}
