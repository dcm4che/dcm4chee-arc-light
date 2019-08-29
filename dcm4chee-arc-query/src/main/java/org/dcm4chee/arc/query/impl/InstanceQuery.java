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

package org.dcm4chee.arc.query.impl;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.dict.archive.PrivateTag;
import org.dcm4che3.json.JSONReader;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.SafeClose;
import org.dcm4chee.arc.code.CodeCache;
import org.dcm4chee.arc.conf.Availability;
import org.dcm4chee.arc.conf.Entity;
import org.dcm4chee.arc.conf.QueryRetrieveView;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.query.util.QueryBuilder;

import javax.json.Json;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.Tuple;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.*;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Aug 2015
 */
class InstanceQuery extends AbstractQuery {

    private static final int[] ARCHIVE_INST_TAGS = {
            (PrivateTag.InstanceReceiveDateTime & 0xffff0000) | 0x0010,
            PrivateTag.InstanceReceiveDateTime | 0x1000,
            PrivateTag.InstanceUpdateDateTime | 0x1000,
            PrivateTag.RejectionCodeSequence | 0x1000,
            PrivateTag.InstanceExternalRetrieveAETitle | 0x1000,
            PrivateTag.StorageID | 0x1000,
            PrivateTag.StoragePath | 0x1000,
            PrivateTag.StorageTransferSyntaxUID | 0x1000,
            PrivateTag.StorageObjectSize | 0x1000,
            PrivateTag.StorageObjectDigest | 0x1000,
            PrivateTag.StorageObjectStatus | 0x1000
    };

    private final CodeCache codeCache;
    private Root<Instance> instance;
    private Join<Instance, Series> series;
    private Join<Series, Study> study;
    private Join<Study, Patient> patient;
    private Path<byte[]> instanceAttrBlob;
    private Long seriesPk;
    private Attributes seriesAttrs;
    private List<MetadataStoragePath> seriesMetadataStoragePaths;
    private ZipInputStream seriesMetadataStream;
    private Attributes nextMatchFromMetadata;
    private String[] sopInstanceUIDs;
    private int[] instTags;
    private Attributes instQueryKeys;
    private Map<String, CodeEntity> rejectedInstancesOfSeries;

    InstanceQuery(QueryContext context, EntityManager em, CodeCache codeCache) {
        super(context, em);
        this.codeCache = codeCache;
    }

    @Override
    protected CriteriaQuery<Tuple> multiselect() {
        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        this.instance = q.from(Instance.class);
        this.series = instance.join(Instance_.series);
        this.study = series.join(Series_.study);
        this.patient = study.join(Study_.patient);
        return order(restrict(q, patient, study, series, instance)).multiselect(
                series.get(Series_.pk),
                instance.get(Instance_.pk),
                instance.get(Instance_.retrieveAETs),
                instance.get(Instance_.externalRetrieveAET),
                instance.get(Instance_.availability),
                instance.get(Instance_.createdTime),
                instance.get(Instance_.updatedTime),
                instanceAttrBlob = instance.join(Instance_.attributesBlob).get(AttributesBlob_.encodedAttributes));
    }

    @Override
    protected CriteriaQuery<Long> count() {
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        Root<Instance> instance = q.from(Instance.class);
        Join<Instance, Series> series = instance.join(Instance_.series);
        Join<Series, Study> study = series.join(Series_.study);
        Join<Study, Patient> patient = study.join(Study_.patient);
        return restrict(q, patient, study, series, instance).select(cb.count(instance));
    }

    @Override
    protected Attributes toAttributes(Tuple results) {
        Long seriesPk = results.get(series.get(Series_.pk));
        Availability availability = results.get(instance.get(Instance_.availability));
        if (!seriesPk.equals(this.seriesPk)) {
            this.seriesAttrs = context.getQueryService().getSeriesAttributes(context, seriesPk);
            this.seriesPk = seriesPk;
            if (context.isReturnPrivate())
                this.rejectedInstancesOfSeries = getRejectedInstancesOfSeries(seriesAttrs);
        }
        Attributes instAttrs = AttributesBlob.decodeAttributes(results.get(instanceAttrBlob), null);
        Attributes.unifyCharacterSets(seriesAttrs, instAttrs);
        Attributes attrs = new Attributes(seriesAttrs.size() + instAttrs.size() + 10);
        attrs.addAll(seriesAttrs);
        attrs.addAll(instAttrs, true);
        attrs.setString(Tag.RetrieveAETitle, VR.AE,
                retrieveAETs(
                        results.get(instance.get(Instance_.retrieveAETs)),
                        results.get(instance.get(Instance_.externalRetrieveAET))));
        attrs.setString(Tag.InstanceAvailability, VR.CS, availability.toString());
        if (!context.isReturnPrivate())
            return attrs;

        attrs.setDate(PrivateTag.PrivateCreator, PrivateTag.InstanceReceiveDateTime, VR.DT,
                results.get(instance.get(Instance_.createdTime)));
        attrs.setDate(PrivateTag.PrivateCreator, PrivateTag.InstanceUpdateDateTime, VR.DT,
                results.get(instance.get(Instance_.updatedTime)));
        addRejectionNoteCode(attrs, rejectedInstancesOfSeries.get(instAttrs.getString(Tag.SOPInstanceUID)));
        context.getQueryService().addLocationAttributes(attrs, results.get(instance.get(Instance_.pk)));
        return attrs;
    }

    private Map<String, CodeEntity> getRejectedInstancesOfSeries(Attributes seriesAttrs) {
            return em.createNamedQuery(RejectedInstance.FIND_BY_SERIES_UID, RejectedInstance.class)
                    .setParameter(1, seriesAttrs.getString(Tag.StudyInstanceUID))
                    .setParameter(2, seriesAttrs.getString(Tag.SeriesInstanceUID))
                    .getResultStream()
                    .collect(Collectors.toMap(RejectedInstance::getSopInstanceUID, RejectedInstance::getRejectionNoteCode));
    }

    private void addRejectionNoteCode(Attributes attrs, CodeEntity rejectionCode) {
        if (rejectionCode != null)
            attrs.newSequence(PrivateTag.PrivateCreator, PrivateTag.RejectionCodeSequence, 1)
                    .add(rejectionCode.getCode().toItem());
    }

    @Override
    public boolean isOptionalKeysNotSupported() {
        //TODO
        return false;
    }

    @Override
    public boolean hasMoreMatches() throws DicomServiceException {
        if (nextMatchFromMetadata != null)
            return true;
        try {
            if (seriesMetadataStoragePaths == null) {
                boolean hasMoreMatches = super.hasMoreMatches();
                if (hasMoreMatches || !context.isConsiderPurgedInstances())
                    return hasMoreMatches;


                seriesMetadataStoragePaths = queryMetadataStoragePath();
                if (!nextSeriesMetadataStream())
                    return false;

                int[] tags = context.getArchiveAEExtension().getArchiveDeviceExtension()
                        .getAttributeFilter(Entity.Instance).getSelection(true);
                instTags = new int[tags.length + ARCHIVE_INST_TAGS.length];
                System.arraycopy(tags, 0, instTags, 0, tags.length);
                System.arraycopy(ARCHIVE_INST_TAGS, 0, instTags, tags.length, ARCHIVE_INST_TAGS.length);
                Attributes queryKeys = context.getQueryKeys();
                instQueryKeys = new Attributes(queryKeys, tags);
                sopInstanceUIDs = queryKeys.getStrings(Tag.SOPInstanceUID);
            }
            nextMatchFromMetadata = nextMatchFromMetadata();
        } catch (IOException e) {
            throw new DicomServiceException(Status.UnableToCalculateNumberOfMatches, e);
        }
        return nextMatchFromMetadata != null;
    }

    private CriteriaQuery<Tuple> order(CriteriaQuery<Tuple> q) {
        if (context.getOrderByTags() != null)
            q = q.orderBy(builder.orderInstances(patient, study, series, instance, context.getOrderByTags()));
        return q;
    }

    private <T> CriteriaQuery<T> restrict(CriteriaQuery<T> q, Join<Study, Patient> patient,
            Join<Series, Study> study, Join<Instance, Series> series, Root<Instance> instance) {
        List<Predicate> predicates = builder.instancePredicates(q, patient, study, series, instance,
                context.getPatientIDs(),
                context.getQueryKeys(),
                context.getQueryParam(),
                codeCache.findOrCreateEntities(
                        context.getQueryParam().getQueryRetrieveView().getShowInstancesRejectedByCodes()),
                codeCache.findOrCreateEntities(
                        context.getQueryParam().getQueryRetrieveView().getHideRejectionNotesWithCodes()));
        if (!predicates.isEmpty())
            q.where(predicates.toArray(new Predicate[0]));
        return q;
    }

    private List<MetadataStoragePath> queryMetadataStoragePath() {
        Attributes keys = context.getQueryKeys();
        String studyInstanceUID = keys.getString(Tag.StudyInstanceUID);
        String seriesInstanceUID = keys.getString(Tag.SeriesInstanceUID);
        if (studyInstanceUID == null || seriesInstanceUID == null)
            return Collections.emptyList();

        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        Root<Series> series = q.from(Series.class);
        Join<Series, Metadata> metadata = series.join(Series_.metadata);
        Join<Series, Study> study = series.join(Series_.study);
        Join<Study, Patient> patient = study.join(Study_.patient);
        TypedQuery<Tuple> query = em.createQuery(
                restrict(q, patient, study, series).multiselect(
                    series.get(Series_.pk),
                    metadata.get(Metadata_.storageID),
                    metadata.get(Metadata_.storagePath)));
        try (Stream<Tuple> resultStream = query.getResultStream()) {
            return resultStream.map(t -> new MetadataStoragePath(
                        t.get(series.get(Series_.pk)),
                        t.get(metadata.get(Metadata_.storageID)),
                        t.get(metadata.get(Metadata_.storagePath))))
                    .collect(Collectors.toList());
        }
    }

    private CriteriaQuery<Tuple> restrict(CriteriaQuery<Tuple> q, Join<Study, Patient> patient,
            Join<Series, Study> study, Root<Series> series) {
        List<Predicate> predicates = builder.seriesPredicates(q, patient, study, series,
                context.getPatientIDs(),
                context.getQueryKeys(),
                context.getQueryParam()
        );
        predicates.add(cb.equal(series.get(Series_.instancePurgeState), Series.InstancePurgeState.PURGED));
        return q.where(predicates.toArray(new Predicate[0]));
    }

    private boolean nextSeriesMetadataStream() throws IOException {
        SafeClose.close(seriesMetadataStream);
        seriesMetadataStream = null;
        if (seriesMetadataStoragePaths.isEmpty())
            return false;

        MetadataStoragePath metadataStoragePath = seriesMetadataStoragePaths.remove(0);
        this.seriesAttrs = context.getQueryService().getSeriesAttributes(context, metadataStoragePath.seriesPk);
        seriesMetadataStream = context.getQueryService().openZipInputStream(context,
                metadataStoragePath.storageID, metadataStoragePath.storagePath);
        return true;
    }

    private Attributes nextMatchFromMetadata() throws IOException {
        QueryRetrieveView qrView = context.getQueryParam().getQueryRetrieveView();
        ZipEntry entry;
        do {
            while ((entry = seriesMetadataStream.getNextEntry()) != null) {
                if (matchSOPInstanceUID(entry.getName())) {
                    JSONReader jsonReader = new JSONReader(Json.createParser(
                            new InputStreamReader(seriesMetadataStream, StandardCharsets.UTF_8)));
                    jsonReader.setSkipBulkDataURI(true);
                    Attributes metadata = jsonReader.readDataset(null);
                    if (!qrView.hideRejectedInstance(
                            metadata.getNestedDataset(PrivateTag.PrivateCreator, PrivateTag.RejectionCodeSequence))
                            && !qrView.hideRejectionNote(metadata)
                            && metadata.matches(instQueryKeys, false, false)) {
                        seriesMetadataStream.closeEntry();
                        Attributes instAtts = new Attributes(metadata, instTags);
                        Attributes.unifyCharacterSets(seriesAttrs, instAtts);
                        Attributes attrs = new Attributes(seriesAttrs.size() + instAtts.size());
                        attrs.addAll(seriesAttrs);
                        attrs.addAll(instAtts, true);
                        return attrs;
                    }
                }
                seriesMetadataStream.closeEntry();
            }
        } while (nextSeriesMetadataStream());
        return null;
    }

    private boolean matchSOPInstanceUID(String iuid) {
        if (sopInstanceUIDs == null || sopInstanceUIDs.length == 0)
            return true;

        for (String sopInstanceUID : sopInstanceUIDs)
            if (sopInstanceUID.equals(iuid))
                return true;

        return false;
    }

    @Override
    public Attributes nextMatch() {
        if (seriesMetadataStoragePaths == null)
            return super.nextMatch();

        Attributes tmp = nextMatchFromMetadata;
        nextMatchFromMetadata = null;
        return tmp;
    }

    @Override
    public void close() {
        super.close();
        SafeClose.close(seriesMetadataStream);
    }

    private static class MetadataStoragePath {
        final long seriesPk;
        final String storageID;
        final String storagePath;

        MetadataStoragePath(long seriesPk, String storageID, String storagePath) {
            this.seriesPk = seriesPk;
            this.storageID = storageID;
            this.storagePath = storagePath;
        }
    }
}
