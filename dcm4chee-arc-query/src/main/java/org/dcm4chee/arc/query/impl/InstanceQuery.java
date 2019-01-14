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

package org.dcm4chee.arc.query.impl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.jpa.hibernate.HibernateQuery;
import org.dcm4che3.data.*;
import org.dcm4che3.dict.archive.ArchiveTag;
import org.dcm4che3.json.JSONReader;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4che3.util.SafeClose;
import org.dcm4chee.arc.code.CodeCache;
import org.dcm4chee.arc.conf.Availability;
import org.dcm4chee.arc.conf.Entity;
import org.dcm4chee.arc.conf.QueryRetrieveView;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.query.util.QueryBuilder;
import org.dcm4chee.arc.query.util.QueryParam;
import org.hibernate.StatelessSession;

import javax.json.Json;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Aug 2015
 */
class InstanceQuery extends AbstractQuery {

    private static final Expression<?>[] SELECT = {
            QSeries.series.pk,
            QInstance.instance.pk,
            QInstance.instance.retrieveAETs,
            QInstance.instance.externalRetrieveAET,
            QInstance.instance.availability,
            QInstance.instance.createdTime,
            QInstance.instance.updatedTime,
            QCodeEntity.codeEntity.codeValue,
            QCodeEntity.codeEntity.codingSchemeDesignator,
            QCodeEntity.codeEntity.codeMeaning,
            QInstance.instance.attributesBlob.encodedAttributes
    };

    private static final Expression<?>[] METADATA_STORAGE_PATH = {
            QSeries.series.pk,
            QMetadata.metadata.storageID,
            QMetadata.metadata.storagePath,
    };

    private static final int[] ARCHIVE_INST_TAGS = {
            (ArchiveTag.InstanceReceiveDateTime & 0xffff0000) | 0x0010,
            ArchiveTag.InstanceReceiveDateTime | 0x1000,
            ArchiveTag.InstanceUpdateDateTime | 0x1000,
            ArchiveTag.RejectionCodeSequence | 0x1000,
            ArchiveTag.InstanceExternalRetrieveAETitle | 0x1000,
            ArchiveTag.StorageID | 0x1000,
            ArchiveTag.StoragePath | 0x1000,
            ArchiveTag.StorageTransferSyntaxUID | 0x1000,
            ArchiveTag.StorageObjectSize | 0x1000,
            ArchiveTag.StorageObjectDigest | 0x1000,
            ArchiveTag.StorageObjectStatus | 0x1000
    };
    private final CodeCache codeCache;
    private Long seriesPk;
    private Attributes seriesAttrs;
    private List<Tuple> seriesMetadataStoragePaths;
    private ZipInputStream seriesMetadataStream;
    private Attributes nextMatchFromMetadata;
    private String[] sopInstanceUIDs;
    private int[] instTags;
    private Attributes instQueryKeys;

    public InstanceQuery(QueryContext context, StatelessSession session, CodeCache codeCache) {
        super(context, session);
        this.codeCache = codeCache;
    }

    @Override
    protected HibernateQuery<Tuple> newHibernateQuery(boolean forCount) {
        HibernateQuery<Tuple> q = new HibernateQuery<Void>(session).select(SELECT).from(QInstance.instance);
        return newHibernateQuery(q, forCount);
    }

    @Override
    public long fetchCount() {
        HibernateQuery<Void> q = new HibernateQuery<Void>(session).from(QInstance.instance);
        return newHibernateQuery(q, true).fetchCount();
    }

    private <T> HibernateQuery<T> newHibernateQuery(HibernateQuery<T> q, boolean forCount) {
        Attributes keys = context.getQueryKeys();
        IDWithIssuer[] pids = context.getPatientIDs();
        QueryParam queryParam = context.getQueryParam();
        QueryRetrieveView qrView = queryParam.getQueryRetrieveView();
        q = QueryBuilder.applyInstanceLevelJoins(q, keys, queryParam, forCount);
//        q = q.leftJoin(QInstance.instance.locations, QLocation.location)
//                .on(QLocation.location.objectType.eq(Location.ObjectType.DICOM_FILE));
        q = QueryBuilder.applySeriesLevelJoins(q, keys, queryParam, forCount);
        q = QueryBuilder.applyStudyLevelJoins(q, keys, queryParam, forCount, true);
        q = QueryBuilder.applyPatientLevelJoins(q, pids, keys, queryParam, context.isOrderByPatientName(), forCount);
        BooleanBuilder predicates = new BooleanBuilder();
        QueryBuilder.addPatientLevelPredicates(predicates, pids, keys, queryParam);
        QueryBuilder.addStudyLevelPredicates(predicates, keys, queryParam, QueryRetrieveLevel2.IMAGE);
        QueryBuilder.addSeriesLevelPredicates(predicates, keys, queryParam, QueryRetrieveLevel2.IMAGE);
        QueryBuilder.addInstanceLevelPredicates(predicates, keys, queryParam,
                codeCache.findOrCreateEntities(qrView.getShowInstancesRejectedByCodes()),
                codeCache.findOrCreateEntities(qrView.getHideRejectionNotesWithCodes()));
        return q.where(predicates);
    }

    private HibernateQuery<Tuple> queryMetadataStoragePath() {
        HibernateQuery<Tuple> query = new HibernateQuery<Void>(session).select(METADATA_STORAGE_PATH)
                .from(QSeries.series)
                .join(QSeries.series.metadata, QMetadata.metadata)
                .join(QSeries.series.study, QStudy.study);

        Attributes keys = context.getQueryKeys();
        BooleanBuilder builder = new BooleanBuilder();
        builder.and(QueryBuilder.accessControl(context.getQueryParam().getAccessControlIDs()));
        builder.and(QStudy.study.studyInstanceUID.eq(keys.getString(Tag.StudyInstanceUID)));
        builder.and(QSeries.series.seriesInstanceUID.eq(keys.getString(Tag.SeriesInstanceUID)));
        builder.and(QSeries.series.instancePurgeState.eq(Series.InstancePurgeState.PURGED));
        return query.where(builder);
    }

    @Override
    protected Attributes toAttributes(Tuple results) {
        Long seriesPk = results.get(QSeries.series.pk);
        Availability availability = results.get(QInstance.instance.availability);
        if (!seriesPk.equals(this.seriesPk)) {
            this.seriesAttrs = context.getQueryService().getSeriesAttributes(context, seriesPk);
            this.seriesPk = seriesPk;
        }
        Attributes instAtts = AttributesBlob.decodeAttributes(
                results.get(QInstance.instance.attributesBlob.encodedAttributes), null);
        Attributes.unifyCharacterSets(seriesAttrs, instAtts);
        Attributes attrs = new Attributes(seriesAttrs.size() + instAtts.size() + 10);
        attrs.addAll(seriesAttrs);
        attrs.addAll(instAtts);
        attrs.setString(Tag.RetrieveAETitle, VR.AE,
                retrieveAETs(
                        results.get(QInstance.instance.retrieveAETs),
                        results.get(QInstance.instance.externalRetrieveAET)));
        attrs.setString(Tag.InstanceAvailability, VR.CS, availability.toString());
        if (!context.isReturnPrivate())
            return attrs;

        attrs.setDate(ArchiveTag.PrivateCreator, ArchiveTag.InstanceReceiveDateTime, VR.DT,
                results.get(QInstance.instance.createdTime));
        attrs.setDate(ArchiveTag.PrivateCreator, ArchiveTag.InstanceUpdateDateTime, VR.DT,
                results.get(QInstance.instance.updatedTime));
        if (results.get(QCodeEntity.codeEntity.codeValue) != null) {
            Sequence rejectionCodeSeq = attrs.newSequence(ArchiveTag.PrivateCreator, ArchiveTag.RejectionCodeSequence, 1);
            Attributes item = new Attributes();
            item.setString(Tag.CodeValue, VR.SH, results.get(QCodeEntity.codeEntity.codeValue));
            item.setString(Tag.CodeMeaning, VR.LO, results.get(QCodeEntity.codeEntity.codeMeaning));
            item.setString(Tag.CodingSchemeDesignator, VR.SH, results.get(QCodeEntity.codeEntity.codingSchemeDesignator));
            rejectionCodeSeq.add(item);
        }
        context.getQueryService().addLocationAttributes(attrs, results.get(QInstance.instance.pk));
        return attrs;
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


                seriesMetadataStoragePaths = queryMetadataStoragePath().fetch();
                if (!nextSeriesMetadataStream())
                    return false;

                int[] tags = context.getArchiveAEExtension().getArchiveDeviceExtension()
                        .getAttributeFilter(Entity.Instance).getSelection();
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

    private boolean nextSeriesMetadataStream() throws IOException {
        SafeClose.close(seriesMetadataStream);
        seriesMetadataStream = null;
        if (seriesMetadataStoragePaths.isEmpty())
            return false;

        Tuple tuple = seriesMetadataStoragePaths.remove(0);
        this.seriesAttrs = context.getQueryService().getSeriesAttributes(context, tuple.get(QSeries.series.pk));
        seriesMetadataStream = context.getQueryService().openZipInputStream(context,
                tuple.get(QMetadata.metadata.storageID), tuple.get(QMetadata.metadata.storagePath));
        return true;
    }

    private Attributes nextMatchFromMetadata() throws IOException {
        QueryRetrieveView qrView = context.getQueryParam().getQueryRetrieveView();
        ZipEntry entry;
        do {
            while ((entry = seriesMetadataStream.getNextEntry()) != null) {
                if (matchSOPInstanceUID(entry.getName())) {
                    JSONReader jsonReader = new JSONReader(Json.createParser(
                            new InputStreamReader(seriesMetadataStream, "UTF-8")));
                    jsonReader.setSkipBulkDataURI(true);
                    Attributes metadata = jsonReader.readDataset(null);
                    if (!qrView.hideRejectedInstance(
                            metadata.getNestedDataset(ArchiveTag.PrivateCreator, ArchiveTag.RejectionCodeSequence))
                            && !qrView.hideRejectionNote(metadata)
                            && metadata.matches(instQueryKeys, false, false)) {
                        seriesMetadataStream.closeEntry();
                        Attributes instAtts = new Attributes(metadata, instTags);
                        Attributes.unifyCharacterSets(seriesAttrs, instAtts);
                        Attributes attrs = new Attributes(seriesAttrs.size() + instAtts.size());
                        attrs.addAll(seriesAttrs);
                        attrs.addAll(instAtts);
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
}
