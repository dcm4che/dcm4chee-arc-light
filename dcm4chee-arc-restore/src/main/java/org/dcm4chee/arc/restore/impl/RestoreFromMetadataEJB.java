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
 * Portions created by the Initial Developer are Copyright (C) 2013-2021
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

package org.dcm4chee.arc.restore.impl;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Code;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.dict.archive.PrivateTag;
import org.dcm4che3.net.Device;
import org.dcm4che3.util.TagUtils;
import org.dcm4chee.arc.code.CodeCache;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.patient.PatientMgtContext;
import org.dcm4chee.arc.patient.PatientService;
import org.dcm4chee.arc.restore.RestoreFromMetadata;
import org.dcm4chee.arc.storage.ReadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Sep 2022
 */
@Stateless
public class RestoreFromMetadataEJB implements RestoreFromMetadata {

    private static final Logger LOG = LoggerFactory.getLogger(RestoreFromMetadataEJB.class);

    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    @Inject
    private CodeCache codeCache;

    @Inject
    private PatientService patientService;

    @Inject
    private Device device;

    @Override
    public void restore(ReadContext readContext, List<Attributes> seriesMetadata, HttpServletRequestInfo httpRequest) {
        ArchiveDeviceExtension arcDev = getArchiveDeviceExtension();
        Attributes attrs = seriesMetadata.get(0);
        Series series = createSeries(arcDev,
                attrs,
                calculateSeriesSize(seriesMetadata),
                findOrCreateStudy(arcDev, attrs, httpRequest),
                createMetadata(readContext));
        for (QueryRetrieveView qrView : arcDev.getQueryRetrieveViews()) {
            createSeriesQueryAttributes(qrView, seriesMetadata, series);
        }
        LOG.info("Restore {}", series);
    }

    private void createSeriesQueryAttributes(QueryRetrieveView qrView, List<Attributes> seriesMetadata, Series series) {
        SeriesQueryAttributes queryAttrs = new SeriesQueryAttributes();
        int numberOfInstances = 0;
        for (Attributes attrs : seriesMetadata) {
            if (qrView.hideRejectionNote(attrs) || qrView.hideRejectedInstance(
                    attrs.getNestedDataset(PrivateTag.PrivateCreator, PrivateTag.RejectionCodeSequence))) continue;
            numberOfInstances++;
            queryAttrs.retainRetrieveAETs(attrs.getStrings(Tag.RetrieveAETitle));
            queryAttrs.floorAvailability(Availability.valueOf(attrs.getString(Tag.InstanceAvailability)));
            queryAttrs.addSOPClassInSeries(attrs.getString(Tag.SOPClassUID));
        }
        queryAttrs.setNumberOfInstances(numberOfInstances);
        queryAttrs.setViewID(qrView.getViewID());
        queryAttrs.setSeries(series);
        em.persist(queryAttrs);
    }

    private static long calculateSeriesSize(List<Attributes> seriesMetadata) {
        return seriesMetadata.stream()
                .mapToLong(attrs -> attrs.getInt(PrivateTag.PrivateCreator, PrivateTag.StorageObjectSize, 0, 0))
                .sum();
    }

    private ArchiveDeviceExtension getArchiveDeviceExtension() {
        return device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
    }

    private Metadata createMetadata(ReadContext readContext) {
        Metadata metadata = new Metadata();
        metadata.setStorageID(readContext.getStorage().getStorageDescriptor().getStorageID());
        metadata.setStoragePath(readContext.getStoragePath());
        metadata.setSize(readContext.getSize());
        metadata.setDigest(readContext.getDigest());
        em.persist(metadata);
        return metadata;
    }

    private Study findOrCreateStudy(ArchiveDeviceExtension arcDev, Attributes attrs, HttpServletRequestInfo httpRequest) {
        try {
            return em.createNamedQuery(Study.FIND_BY_STUDY_IUID, Study.class)
                    .setParameter(1, attrs.getString(Tag.StudyInstanceUID))
                    .getSingleResult();
        } catch (NoResultException e) {
            return createStudy(arcDev, attrs, findOrCreatePatient(attrs, httpRequest));
        }
    }

    private Patient findOrCreatePatient(Attributes attrs, HttpServletRequestInfo httpRequest) {
        PatientMgtContext ctx = patientService.createPatientMgtContextWEB(httpRequest);
        ctx.setAttributes(attrs);
        Patient pat = patientService.findPatient(ctx);
        return pat != null ? pat : patientService.createPatient(ctx);
    }

    private Study createStudy(ArchiveDeviceExtension arcDev, Attributes attrs, Patient patient) {
        Study study = new Study();
        study.addStorageID(attrs.getString(PrivateTag.PrivateCreator, PrivateTag.StorageID));
        Sequence seq = attrs.getSequence(PrivateTag.PrivateCreator, PrivateTag.OtherStorageSequence);
        if (seq != null)
            for (Attributes item : seq) {
                study.addStorageID(item.getString(PrivateTag.PrivateCreator, PrivateTag.StorageID));
            }
        study.setAccessControlID(attrs.getString(PrivateTag.PrivateCreator, PrivateTag.StudyAccessControlID));
        study.setCompleteness(Completeness.COMPLETE);
        study.setExpirationDate(toLocalDate(attrs.getDate(PrivateTag.PrivateCreator, PrivateTag.StudyExpirationDate)));
        study.setExpirationState(ExpirationState.UPDATEABLE);
        study.setAttributes(attrs, arcDev.getAttributeFilter(Entity.Study), false, arcDev.getFuzzyStr());
        setCodes(study.getProcedureCodes(), attrs, Tag.ProcedureCodeSequence);
        study.setPatient(patient);
        study.setRejectionState(RejectionState.NONE);
        patient.incrementNumberOfStudies();
        em.persist(study);
        LOG.info("Create {}", study);
        return study;
    }

    private Series createSeries(ArchiveDeviceExtension arcDev, Attributes attrs, long seriesSize, Study study, Metadata metadata) {
        Series series = new Series();
        series.setAttributes(attrs, arcDev.getAttributeFilter(Entity.Series), false, arcDev.getFuzzyStr());
        series.setInstitutionCode(findOrCreateCode(attrs, Tag.InstitutionCodeSequence));
        series.setInstitutionalDepartmentTypeCode(findOrCreateCode(attrs, Tag.InstitutionalDepartmentTypeCodeSequence));
        Sequence seq = attrs.getSequence(Tag.RequestAttributesSequence);
        Collection<SeriesRequestAttributes> requestAttributes = series.getRequestAttributes();
        if (seq != null)
            for (Attributes item : seq) {
                SeriesRequestAttributes request = new SeriesRequestAttributes(item, arcDev.getFuzzyStr());
                requestAttributes.add(request);
            }
        series.setSendingAET(
                attrs.getString(PrivateTag.PrivateCreator, PrivateTag.SendingApplicationEntityTitleOfSeries));
        series.setReceivingAET(
                attrs.getString(PrivateTag.PrivateCreator, PrivateTag.ReceivingApplicationEntityTitleOfSeries));
        series.setSendingPresentationAddress(
                attrs.getString(PrivateTag.PrivateCreator, PrivateTag.SendingPresentationAddressOfSeries));
        series.setReceivingPresentationAddress(
                attrs.getString(PrivateTag.PrivateCreator, PrivateTag.ReceivingPresentationAddressOfSeries));
        series.setSopClassUID(attrs.getString(Tag.SOPClassUID));
        series.setTransferSyntaxUID(attrs.getString(PrivateTag.PrivateCreator, PrivateTag.StorageTransferSyntaxUID));
        series.setInstancePurgeState(Series.InstancePurgeState.PURGED);
        series.setExpirationDate(toLocalDate(attrs.getDate(PrivateTag.PrivateCreator, PrivateTag.SeriesExpirationDate)));
        series.setExpirationState(ExpirationState.UPDATEABLE);
        series.setCompleteness(Completeness.COMPLETE);
        series.setRejectionState(RejectionState.NONE);
        series.setSize(seriesSize);
        series.setStudy(study);
        series.setMetadata(metadata);
        em.persist(series);
        return series;
    }

    private static LocalDate toLocalDate(Date dateToConvert) {
        return dateToConvert != null
                ? dateToConvert.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                : null;
    }

    private CodeEntity findOrCreateCode(Attributes attrs, int seqTag) {
        Attributes item = attrs.getNestedDataset(seqTag);
        if (item != null)
            try {
                return codeCache.findOrCreate(new Code(item));
            } catch (Exception e) {
                LOG.info("Illegal code item in Sequence {}:\n{}", TagUtils.toString(seqTag), item);
            }
        return null;
    }

    private void setCodes(Collection<CodeEntity> codes, Attributes attrs, int seqTag) {
        Sequence seq = attrs.getSequence(seqTag);
        codes.clear();
        if (seq != null)
            for (Attributes item : seq) {
                try {
                    codes.add(codeCache.findOrCreate(new Code(item)));
                } catch (Exception e) {
                    LOG.info("Illegal code item in Sequence {}:\n{}", TagUtils.toString(seqTag), item);
                }
            }
    }
}
