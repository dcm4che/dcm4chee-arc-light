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

package org.dcm4chee.arc.retrieve.impl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Predicate;
import com.querydsl.jpa.hibernate.HibernateQuery;
import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.IApplicationEntityCache;
import org.dcm4che3.data.*;
import org.dcm4che3.deident.DeIdentificationAttributesCoercion;
import org.dcm4che3.dict.archive.ArchiveTag;
import org.dcm4che3.imageio.codec.Transcoder;
import org.dcm4che3.io.*;
import org.dcm4che3.json.JSONReader;
import org.dcm4che3.net.*;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4che3.util.SafeClose;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.util.UIDUtils;
import org.dcm4chee.arc.LeadingCFindSCPQueryCache;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.query.scu.CFindSCU;
import org.dcm4chee.arc.query.scu.CFindSCUAttributeCoercion;
import org.dcm4chee.arc.retrieve.*;
import org.dcm4chee.arc.code.CodeCache;
import org.dcm4chee.arc.query.util.QueryBuilder;
import org.dcm4chee.arc.storage.ReadContext;
import org.dcm4chee.arc.storage.Storage;
import org.dcm4chee.arc.storage.StorageFactory;
import org.dcm4chee.arc.qmgt.HttpServletRequestInfo;
import org.dcm4chee.arc.store.InstanceLocations;
import org.dcm4chee.arc.store.StoreService;
import org.dcm4chee.arc.store.StoreSession;
import org.hibernate.Session;
import org.hibernate.StatelessSession;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.servlet.http.HttpServletRequest;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Aug 2015
 */
@ApplicationScoped
public class RetrieveServiceImpl implements RetrieveService {

    private static Logger LOG = LoggerFactory.getLogger(RetrieveServiceImpl.class);

    private static final int MAX_FAILED_IUIDS_LEN = 4000;

    private static final Expression<?>[] SELECT = {
            QLocation.location.pk,
            QLocation.location.storageID,
            QLocation.location.storagePath,
            QLocation.location.objectType,
            QLocation.location.transferSyntaxUID,
            QLocation.location.digest,
            QLocation.location.size,
            QLocation.location.status,
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
            QUIDMap.uIDMap.pk,
            QUIDMap.uIDMap.encodedMap,
            QueryBuilder.instanceAttributesBlob.encodedAttributes
    };

    static final Expression<?>[] PATIENT_STUDY_SERIES_ATTRS = {
            QPatient.patient.updatedTime,
            QStudy.study.pk,
            QStudy.study.studyInstanceUID,
            QStudy.study.accessTime,
            QStudy.study.failedRetrieves,
            QStudy.study.completeness,
            QStudy.study.modifiedTime,
            QStudy.study.expirationDate,
            QStudy.study.accessControlID,
            QSeries.series.seriesInstanceUID,
            QSeries.series.failedRetrieves,
            QSeries.series.completeness,
            QSeries.series.updatedTime,
            QSeries.series.expirationDate,
            QSeries.series.sourceAET,
            QueryBuilder.seriesAttributesBlob.encodedAttributes,
            QueryBuilder.studyAttributesBlob.encodedAttributes,
            QueryBuilder.patientAttributesBlob.encodedAttributes
    };

    static final Expression<?>[] METADATA_STORAGE_PATH = {
            QSeries.series.pk,
            QMetadata.metadata.storageID,
            QMetadata.metadata.storagePath,
    };

    @PersistenceContext(unitName = "dcm4chee-arc")
    private EntityManager em;

    @Inject
    private StorageFactory storageFactory;

    @Inject
    private StoreService storeService;

    @Inject
    private Device device;

    @Inject
    private CodeCache codeCache;

    @Inject
    private RetrieveServiceEJB ejb;

    @Inject
    private CFindSCU cfindscu;

    @Inject
    private IApplicationEntityCache aeCache;

    @Inject
    private LeadingCFindSCPQueryCache leadingCFindSCPQueryCache;

    StatelessSession openStatelessSession() {
        return em.unwrap(Session.class).getSessionFactory().openStatelessSession();
    }

    private int getInExpressionCountLimit() {
        return ((SessionFactoryImplementor) em.unwrap(Session.class).getSessionFactory())
                .getDialect().getInExpressionCountLimit();
    }

    @Override
    public Device getDevice() {
        return device;
    }

    @Override
    public ArchiveDeviceExtension getArchiveDeviceExtension() {
        return device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
    }

    @Override
    public RetrieveContext newRetrieveContextGET(ArchiveAEExtension arcAE,
            Association as, Attributes rqCmd, QueryRetrieveLevel2 qrLevel, Attributes keys) {
        RetrieveContext ctx = newRetrieveContext(arcAE, as, qrLevel, keys);
        ctx.setPriority(rqCmd.getInt(Tag.Priority, 0));
        ctx.setDestinationAETitle(as.getRemoteAET());
        return ctx;
    }

    @Override
    public RetrieveContext newRetrieveContextMOVE(ArchiveAEExtension arcAE,
            Association as, Attributes rqCmd, QueryRetrieveLevel2 qrLevel, Attributes keys)
            throws ConfigurationException {
        RetrieveContext ctx = newRetrieveContext(arcAE, as, qrLevel, keys);
        ctx.setPriority(rqCmd.getInt(Tag.Priority, 0));
        ctx.setDestinationAETitle(rqCmd.getString(Tag.MoveDestination));
        ctx.setMoveOriginatorMessageID(rqCmd.getInt(Tag.MessageID, 0));
        ctx.setMoveOriginatorAETitle(as.getRemoteAET());
        ctx.setDestinationAE(aeCache.findApplicationEntity(ctx.getDestinationAETitle()));
        return ctx;
    }

    @Override
    public RetrieveContext newRetrieveContextWADO(
            HttpServletRequestInfo httpServletRequestInfo, String localAET, String studyUID, String seriesUID, String objectUID) {
        RetrieveContext ctx = newRetrieveContext(localAET, studyUID, seriesUID, objectUID);
        ctx.setHttpServletRequestInfo(httpServletRequestInfo);
        return ctx;
    }

    @Override
    public RetrieveContext newRetrieveContextSTORE(
            String localAET, String studyUID, String seriesUID, String objectUID, String destAET)
            throws ConfigurationException {
        RetrieveContext ctx = newRetrieveContext(localAET, studyUID, seriesUID, objectUID);
        ctx.setDestinationAETitle(destAET);
        ctx.setDestinationAE(aeCache.findApplicationEntity(destAET));
        return ctx;
    }

    @Override
    public RetrieveContext newRetrieveContextIOCM(
            HttpServletRequest request, String localAET, String studyUID, String... seriesUIDs) {
        ArchiveAEExtension arcAE = device.getApplicationEntity(localAET, true).getAEExtension(ArchiveAEExtension.class);
        RetrieveContext ctx = new RetrieveContextImpl(this, arcAE, localAET, null);
        ctx.setHttpRequest(request);
        ctx.setStudyInstanceUIDs(studyUID);
        ctx.setSeriesInstanceUIDs(seriesUIDs);
        return ctx;
    }

    @Override
    public RetrieveContext newRetrieveContextXDSI(
            HttpServletRequest request, String localAET,
            String[] studyUIDs, String[] seriesUIDs, String[] objectUIDs) {
        ArchiveAEExtension arcAE = device.getApplicationEntity(localAET, true).getAEExtension(ArchiveAEExtension.class);
        RetrieveContext ctx = new RetrieveContextImpl(this, arcAE, localAET, arcAE.getQueryRetrieveView());
        ctx.setHttpServletRequestInfo(HttpServletRequestInfo.valueOf(request));
        ctx.setStudyInstanceUIDs(studyUIDs);
        if (studyUIDs.length == 1) {
            ctx.setSeriesInstanceUIDs(seriesUIDs);
            if (seriesUIDs.length == 1) {
                ctx.setSopInstanceUIDs(objectUIDs);
            }
        }
        return ctx;
    }

    @Override
    public RetrieveContext newRetrieveContext(String localAET, String studyUID, String seriesUID, String objectUID) {
        ArchiveAEExtension arcAE = device.getApplicationEntity(localAET, true).getAEExtension(ArchiveAEExtension.class);
        RetrieveContext ctx = new RetrieveContextImpl(this, arcAE, localAET, arcAE.getQueryRetrieveView());
        if (studyUID != null)
            ctx.setStudyInstanceUIDs(studyUID);
        if (seriesUID != null)
            ctx.setSeriesInstanceUIDs(seriesUID);
        if (objectUID != null)
            ctx.setSopInstanceUIDs(objectUID);
        return ctx;
    }

    @Override
    public RetrieveContext newRetrieveContext(String localAET, Sequence refSopSeq) {
        ArchiveAEExtension arcAE = device.getApplicationEntity(localAET, true).getAEExtension(ArchiveAEExtension.class);
        RetrieveContext ctx = new RetrieveContextImpl(this, arcAE, localAET, arcAE.getQueryRetrieveView());
        String[] uids = refSopSeq.stream()
                .map(refSop -> refSop.getString(Tag.ReferencedSOPInstanceUID))
                .toArray(String[]::new);
        ctx.setSopInstanceUIDs(uids);
        return ctx;
    }

    private RetrieveContext newRetrieveContext(ArchiveAEExtension arcAE, Association as, QueryRetrieveLevel2 qrLevel, Attributes keys) {
        RetrieveContext ctx = new RetrieveContextImpl(this, arcAE, as.getLocalAET(), arcAE.getQueryRetrieveView());
        ctx.setRequestAssociation(as);
        ctx.setQueryRetrieveLevel(qrLevel);
        IDWithIssuer pid = IDWithIssuer.pidOf(keys);
        if (pid != null)
            ctx.setPatientIDs(pid);
        switch (qrLevel) {
            case IMAGE:
                ctx.setSopInstanceUIDs(keys.getStrings(Tag.SOPInstanceUID));
            case SERIES:
                ctx.setSeriesInstanceUIDs(keys.getStrings(Tag.SeriesInstanceUID));
            case STUDY:
                ctx.setStudyInstanceUIDs(keys.getStrings(Tag.StudyInstanceUID));
        }
        return ctx;
    }

    @Override
    public RetrieveContext newRetrieveContextSeriesMetadata(Series.MetadataUpdate metadataUpdate) {
        RetrieveContext ctx = new RetrieveContextImpl(this, null, null, null);
        ctx.setQueryRetrieveLevel(QueryRetrieveLevel2.SERIES);
        ctx.setSeriesMetadataUpdate(metadataUpdate);
        ctx.setObjectType(null);
        return ctx;
    }

    @Override
    public Date getLastModified(RetrieveContext ctx) {
        List<Object[]> dates = queryLastModified(
                ctx.getStudyInstanceUID(), ctx.getSeriesInstanceUID(), ctx.getSopInstanceUIDs());
        Date lastModified = null;
        for (Object[] objs : dates) {
            for (Object obj : objs) {
                Date date = (Date) obj;
                if (lastModified == null || lastModified.compareTo(date) < 0)
                    lastModified = date;
            }
        }
        return lastModified;
    }

    @Override
    public Date getLastModifiedFromMatches(RetrieveContext ctx) {
        Date lastModified = ctx.getStudyInfos().iterator().next().getModifiedTime();
        if (lastModified.compareTo(ctx.getPatientUpdatedTime()) < 0)
            lastModified = ctx.getPatientUpdatedTime();
        for (SeriesInfo si : ctx.getSeriesInfos())
            if (lastModified.compareTo(si.getUpdatedTime()) < 0)
                lastModified = si.getUpdatedTime();
        for (InstanceLocations il : ctx.getMatches())
            if (il.getUpdatedTime() != null && lastModified.compareTo(il.getUpdatedTime()) < 0)
                lastModified = il.getUpdatedTime();
        return lastModified;
    }

    private List<Object[]> queryLastModified(String studyIUID, String seriesIUID, String[] sopIUIDs) {
        return (sopIUIDs.length > 0 // sopIUIDs.length == 1, because WADO-RS does not support multiple sopIUIDs
                    ? em.createNamedQuery(Instance.FIND_LAST_MODIFIED_INSTANCE_LEVEL, Object[].class)
                        .setParameter(1, studyIUID)
                        .setParameter(2, seriesIUID)
                        .setParameter(3, sopIUIDs[0])
                    : seriesIUID != null
                    ? em.createNamedQuery(Instance.FIND_LAST_MODIFIED_SERIES_LEVEL, Object[].class)
                        .setParameter(1, studyIUID)
                        .setParameter(2, seriesIUID)
                    : em.createNamedQuery(Instance.FIND_LAST_MODIFIED_STUDY_LEVEL, Object[].class)
                        .setParameter(1, studyIUID)
        ).getResultList();
    }

    @Override
    public boolean calculateMatches(RetrieveContext ctx) throws DicomServiceException {
        StatelessSession session = openStatelessSession();
        Collection<InstanceLocations> matches = ctx.getMatches();
        matches.clear();
        try {
            HashMap<Long,InstanceLocations> instMap = new HashMap<>();
            HashMap<Long,Attributes> seriesAttrsMap = new HashMap<>();
            HashMap<Long,StudyInfo> studyInfoMap = new HashMap<>();
            Series.MetadataUpdate metadataUpdate = ctx.getSeriesMetadataUpdate();
            if (metadataUpdate != null && metadataUpdate.instancePurgeState == Series.InstancePurgeState.PURGED) {
                SeriesAttributes seriesAttributes = getSeriesAttributes(session, metadataUpdate.seriesPk);
                studyInfoMap.put(seriesAttributes.studyInfo.getStudyPk(), seriesAttributes.studyInfo);
                ctx.getSeriesInfos().add(seriesAttributes.seriesInfo);
                addLocationsFromMetadata(ctx,
                        metadataUpdate.storageID,
                        metadataUpdate.storagePath,
                        seriesAttributes.attrs);
            } else {
                HibernateQuery<Tuple> query = createQuery(ctx, session);
                for (Predicate predicate : createPredicates(ctx)) {
                    for (Tuple tuple : query.where(predicate).fetch()) {
                        Long instPk = tuple.get(QInstance.instance.pk);
                        InstanceLocations match = instMap.get(instPk);
                        if (match == null) {
                            Long seriesPk = tuple.get(QSeries.series.pk);
                            Attributes seriesAttrs = seriesAttrsMap.get(seriesPk);
                            if (seriesAttrs == null) {
                                SeriesAttributes seriesAttributes = getSeriesAttributes(session, seriesPk);
                                studyInfoMap.put(seriesAttributes.studyInfo.getStudyPk(), seriesAttributes.studyInfo);
                                ctx.getSeriesInfos().add(seriesAttributes.seriesInfo);
                                ctx.setPatientUpdatedTime(seriesAttributes.patientUpdatedTime);
                                seriesAttrsMap.put(seriesPk, seriesAttrs = seriesAttributes.attrs);
                            }
                            Attributes instAttrs = AttributesBlob.decodeAttributes(
                                    tuple.get(QueryBuilder.instanceAttributesBlob.encodedAttributes), null);
                            Attributes.unifyCharacterSets(seriesAttrs, instAttrs);
                            instAttrs.addAll(seriesAttrs);
                            match = instanceLocationsFromDB(tuple, instAttrs);
                            matches.add(match);
                            instMap.put(instPk, match);
                        }
                        addLocation(match, tuple);
                    }
                }
                if (ctx.isConsiderPurgedInstances()) {
                    for (Tuple tuple : queryMetadataStoragePath(ctx, session).fetch()) {
                        Long seriesPk = tuple.get(QSeries.series.pk);
                        SeriesAttributes seriesAttributes = getSeriesAttributes(session, seriesPk);
                        studyInfoMap.put(seriesAttributes.studyInfo.getStudyPk(), seriesAttributes.studyInfo);
                        ctx.getSeriesInfos().add(seriesAttributes.seriesInfo);
                        ctx.setPatientUpdatedTime(seriesAttributes.patientUpdatedTime);
                        addLocationsFromMetadata(ctx,
                                tuple.get(QMetadata.metadata.storageID),
                                tuple.get(QMetadata.metadata.storagePath),
                                seriesAttributes.attrs);
                    }
                }
            }
            ctx.setNumberOfMatches(matches.size());
            ctx.getStudyInfos().addAll(studyInfoMap.values());
            updateStudyAccessTime(ctx);
            return !matches.isEmpty();
        } catch (IOException e) {
            throw new DicomServiceException(Status.UnableToCalculateNumberOfMatches, e);
        } finally {
            session.close();
        }
    }

    @Override
    public Collection<InstanceLocations> queryInstances(
            StoreSession session, Attributes instanceRefs, String targetStudyIUID)
            throws IOException {
        Map<String, String> uidMap = session.getUIDMap();
        String sourceStudyUID = instanceRefs.getString(Tag.StudyInstanceUID);
        uidMap.put(sourceStudyUID, targetStudyIUID);
        Sequence refSeriesSeq = instanceRefs.getSequence(Tag.ReferencedSeriesSequence);
        Map<String, Set<String>> refIUIDsBySeriesIUID = new HashMap<>();
        RetrieveContext ctx;
        if (refSeriesSeq == null) {
            ctx = newRetrieveContextIOCM(session.getHttpRequest(), session.getCalledAET(),
                    sourceStudyUID);
        } else {
            for (Attributes item : refSeriesSeq) {
                String seriesIUID = item.getString(Tag.SeriesInstanceUID);
                uidMap.put(seriesIUID, UIDUtils.createUID());
                refIUIDsBySeriesIUID.put(seriesIUID, refIUIDs(item.getSequence(Tag.ReferencedSOPSequence)));
            }
            ctx = newRetrieveContextIOCM(session.getHttpRequest(), session.getCalledAET(),
                    sourceStudyUID, refIUIDsBySeriesIUID.keySet().toArray(StringUtils.EMPTY_STRING));
        }
        ctx.setObjectType(null);
        if (!calculateMatches(ctx))
            return null;
        Collection<InstanceLocations> matches = ctx.getMatches();
        Iterator<InstanceLocations> matchesIter = matches.iterator();
        while (matchesIter.hasNext()) {
            InstanceLocations il = matchesIter.next();
            if (contains(refIUIDsBySeriesIUID, il)) {
                uidMap.put(il.getSopInstanceUID(), UIDUtils.createUID());
                if (refSeriesSeq == null)
                    if (!uidMap.containsKey(il.getAttributes().getString(Tag.SeriesInstanceUID)))
                        uidMap.put(il.getAttributes().getString(Tag.SeriesInstanceUID), UIDUtils.createUID());
            } else
                matchesIter.remove();
        }
        return matches;
    }

    private Set<String> refIUIDs(Sequence refSOPSeq) {
        if (refSOPSeq == null)
            return null;
        Set<String> iuids = new HashSet<>(refSOPSeq.size() * 4 / 3 + 1);
        for (Attributes refSOP : refSOPSeq)
            iuids.add(refSOP.getString(Tag.ReferencedSOPInstanceUID));
        return iuids;
    }

    private boolean contains(Map<String, Set<String>> refIUIDsBySeriesIUID, InstanceLocations il) {
        Set<String> iuids = refIUIDsBySeriesIUID.get(il.getAttributes().getString(Tag.SeriesInstanceUID));
        return iuids == null || iuids.contains(il.getSopInstanceUID());
    }

    private InstanceLocations instanceLocationsFromDB(Tuple tuple, Attributes instAttrs) {
        InstanceLocationsImpl inst = new InstanceLocationsImpl(instAttrs);
        inst.setInstancePk(tuple.get(QInstance.instance.pk));
        inst.setRetrieveAETs(tuple.get(QInstance.instance.retrieveAETs));
        inst.setExternalRetrieveAET(tuple.get(QInstance.instance.externalRetrieveAET));
        inst.setAvailability(tuple.get(QInstance.instance.availability));
        inst.setCreatedTime(tuple.get(QInstance.instance.createdTime));
        inst.setUpdatedTime(tuple.get(QInstance.instance.updatedTime));
        inst.setRejectionCode(rejectionCode(tuple));
        return inst;
    }

    private void addLocationsFromMetadata(
            RetrieveContext ctx, String storageID, String storagePath, Attributes seriesAttrs)
            throws IOException {
        QueryRetrieveView qrView = ctx.getQueryRetrieveView();
        Storage storage = getStorage(storageID, ctx);
        try (InputStream in = storage.openInputStream(
                createReadContext(storage, storagePath, null))) {
            ZipInputStream zip = new ZipInputStream(in);
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (isEmptyOrContains(ctx.getSopInstanceUIDs(), entry.getName())) {
                    Attributes metadata = parseJSON(zip, !ctx.isRetrieveMetadata());
                    if (qrView == null
                            || !qrView.hideRejectedInstance(
                                metadata.getNestedDataset(ArchiveTag.PrivateCreator, ArchiveTag.RejectionCodeSequence))
                            && !qrView.hideRejectionNote(metadata)) {
                        Attributes.unifyCharacterSets(seriesAttrs, metadata);
                        metadata.addAll(seriesAttrs);
                        ctx.getMatches().add(instanceLocationsFromMetadata(ctx, metadata));
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private static boolean isEmptyOrContains(String[] ss, String s) {
        if (ss.length == 0)
            return true;

        for (String s1 : ss) {
            if (s1.equals(s))
                return true;
        }
        return false;
    }

    private InstanceLocations instanceLocationsFromMetadata(RetrieveContext ctx, Attributes attrs) {
        InstanceLocationsImpl inst = new InstanceLocationsImpl(attrs);
        inst.setRetrieveAETs(StringUtils.concat(attrs.getStrings(Tag.RetrieveAETitle), '\\'));
        inst.setAvailability(Availability.valueOf(attrs.getString(Tag.InstanceAvailability)));
        inst.setCreatedTime(attrs.getDate(ArchiveTag.PrivateCreator, ArchiveTag.InstanceReceiveDateTime));
        inst.setUpdatedTime(attrs.getDate(ArchiveTag.PrivateCreator, ArchiveTag.InstanceUpdateDateTime));
        inst.setRejectionCode(attrs.getNestedDataset(ArchiveTag.PrivateCreator, ArchiveTag.RejectionCodeSequence));
        inst.setExternalRetrieveAET(
                attrs.getString(ArchiveTag.PrivateCreator, ArchiveTag.InstanceExternalRetrieveAETitle));
        inst.setContainsMetadata(true);
        addLocationFromMetadata(inst, attrs);
        Sequence otherStorageSeq = attrs.getSequence(ArchiveTag.PrivateCreator, ArchiveTag.OtherStorageSequence);
        if (otherStorageSeq != null)
            for (Attributes otherStorageItem : otherStorageSeq)
                addLocationFromMetadata(inst, otherStorageItem);
        if (ctx.getSeriesMetadataUpdate() == null)
            attrs.removePrivateAttributes(ArchiveTag.PrivateCreator, 0x7777);
        return inst;
    }

    private void addLocationFromMetadata(InstanceLocationsImpl inst, Attributes attrs) {
        inst.getLocations().add(new Location.Builder()
                .storageID(attrs.getString(ArchiveTag.PrivateCreator, ArchiveTag.StorageID))
                .storagePath(StringUtils.concat(attrs.getStrings(ArchiveTag.PrivateCreator, ArchiveTag.StoragePath), '/'))
                .transferSyntaxUID(attrs.getString(ArchiveTag.PrivateCreator, ArchiveTag.StorageTransferSyntaxUID))
                .digest(attrs.getString(ArchiveTag.PrivateCreator, ArchiveTag.StorageObjectDigest))
                .size(attrs.getInt(ArchiveTag.PrivateCreator, ArchiveTag.StorageObjectSize, -1))
                .status(attrs.getString(ArchiveTag.PrivateCreator, ArchiveTag.StorageObjectStatus))
                .build());
    }

    private Attributes rejectionCode(Tuple tuple) {
        if (tuple.get(QCodeEntity.codeEntity.codeValue) == null)
            return null;

        Attributes item = new Attributes();
        item.setString(Tag.CodeValue, VR.SH, tuple.get(QCodeEntity.codeEntity.codeValue));
        item.setString(Tag.CodeMeaning, VR.LO, tuple.get(QCodeEntity.codeEntity.codeMeaning));
        item.setString(Tag.CodingSchemeDesignator, VR.SH, tuple.get(QCodeEntity.codeEntity.codingSchemeDesignator));
        return item;
    }

    private void addLocation(InstanceLocations match, Tuple tuple) {
        Long pk = tuple.get(QLocation.location.pk);
        if (pk == null)
            return;

        Location location = new Location.Builder()
                .pk(pk)
                .storageID(tuple.get(QLocation.location.storageID))
                .storagePath(tuple.get(QLocation.location.storagePath))
                .objectType(tuple.get(QLocation.location.objectType))
                .transferSyntaxUID(tuple.get(QLocation.location.transferSyntaxUID))
                .digest(tuple.get(QLocation.location.digest))
                .size(tuple.get(QLocation.location.size))
                .status(tuple.get(QLocation.location.status))
                .build();
        Long uidMapPk = tuple.get(QUIDMap.uIDMap.pk);
        if (uidMapPk != null) {
            location.setUidMap(new UIDMap(uidMapPk, tuple.get(QUIDMap.uIDMap.encodedMap)));
        }
        match.getLocations().add(location);
    }

    @Override
    public InstanceLocations newInstanceLocations(Attributes attrs) {
        return new InstanceLocationsImpl(attrs);
    }

    private void updateStudyAccessTime(RetrieveContext ctx) {
        if (ctx.isUpdateSeriesMetadata())
            return;

        Duration maxAccessTimeStaleness = getArchiveDeviceExtension().getMaxAccessTimeStaleness();
        if (maxAccessTimeStaleness == null)
            return;

        long now = System.currentTimeMillis();
        long minAccessTime = now - maxAccessTimeStaleness.getSeconds() * 1000;
        for (StudyInfo study : ctx.getStudyInfos()) {
            if (study.getAccessTime().getTime() < minAccessTime)
                ejb.updateStudyAccessTime(study.getStudyPk());
        }
    }

    @Override
    public StoreService getStoreService() {
        return storeService;
    }

    private static class SeriesAttributes {
        final Attributes attrs;
        final StudyInfo studyInfo;
        final SeriesInfo seriesInfo;
        final Date patientUpdatedTime;

        SeriesAttributes(Attributes attrs, StudyInfo studyInfo, SeriesInfo seriesInfo, Date patientUpdatedTime) {
            this.attrs = attrs;
            this.studyInfo = studyInfo;
            this.seriesInfo = seriesInfo;
            this.patientUpdatedTime = patientUpdatedTime;
        }

    }

    private SeriesAttributes getSeriesAttributes(StatelessSession session, Long seriesPk) {
        Tuple tuple = new HibernateQuery<Void>(session).select(PATIENT_STUDY_SERIES_ATTRS)
                .from(QSeries.series)
                .join(QSeries.series.attributesBlob, QueryBuilder.seriesAttributesBlob)
                .join(QSeries.series.study, QStudy.study)
                .join(QStudy.study.attributesBlob, QueryBuilder.studyAttributesBlob)
                .join(QStudy.study.patient, QPatient.patient)
                .join(QPatient.patient.attributesBlob, QueryBuilder.patientAttributesBlob)
                .where(QSeries.series.pk.eq(seriesPk))
                .fetchOne();
        StudyInfo studyInfo = new StudyInfoImpl(
                tuple.get(QStudy.study.pk),
                tuple.get(QStudy.study.studyInstanceUID),
                tuple.get(QStudy.study.accessTime),
                tuple.get(QStudy.study.failedRetrieves),
                tuple.get(QStudy.study.completeness),
                tuple.get(QStudy.study.modifiedTime),
                tuple.get(QStudy.study.expirationDate),
                tuple.get(QStudy.study.accessControlID));
        SeriesInfo seriesInfo = new SeriesInfoImpl(
                studyInfo.getStudyInstanceUID(),
                tuple.get(QSeries.series.seriesInstanceUID),
                tuple.get(QSeries.series.failedRetrieves),
                tuple.get(QSeries.series.completeness),
                tuple.get(QSeries.series.updatedTime),
                tuple.get(QSeries.series.expirationDate),
                tuple.get(QSeries.series.sourceAET));
        Date patientUpdatedTime = tuple.get(QPatient.patient.updatedTime);
        Attributes patAttrs = AttributesBlob.decodeAttributes(
                tuple.get(QueryBuilder.patientAttributesBlob.encodedAttributes), null);
        Attributes studyAttrs = AttributesBlob.decodeAttributes(
                tuple.get(QueryBuilder.studyAttributesBlob.encodedAttributes), null);
        Attributes seriesAttrs = AttributesBlob.decodeAttributes(
                tuple.get(QueryBuilder.seriesAttributesBlob.encodedAttributes), null);
        Attributes.unifyCharacterSets(patAttrs, studyAttrs, seriesAttrs);
        Attributes attrs = new Attributes(patAttrs.size() + studyAttrs.size() + seriesAttrs.size() + 5);
        attrs.addAll(patAttrs);
        attrs.addAll(studyAttrs);
        attrs.addAll(seriesAttrs);
        return new SeriesAttributes(attrs, studyInfo, seriesInfo, patientUpdatedTime);
}

    private HibernateQuery<Tuple> createQuery(RetrieveContext ctx, StatelessSession session) {
        HibernateQuery<Tuple> query = new HibernateQuery<Void>(session).select(SELECT)
                .from(QInstance.instance)
                .join(QInstance.instance.attributesBlob, QueryBuilder.instanceAttributesBlob)
                .join(QInstance.instance.series, QSeries.series)
                .join(QSeries.series.study, QStudy.study)
                .leftJoin(QInstance.instance.rejectionNoteCode, QCodeEntity.codeEntity)
                .leftJoin(QInstance.instance.locations, QLocation.location);

        Location.ObjectType objectType = ctx.getObjectType();
        if (objectType != null)
            query.on(QLocation.location.objectType.eq(objectType));

        query = query.leftJoin(QLocation.location.uidMap, QUIDMap.uIDMap);

        if (ctx.getSeriesMetadataUpdate() != null)
            return query.orderBy(QInstance.instance.instanceNumber.asc());

        IDWithIssuer[] pids = ctx.getPatientIDs();
        if (pids.length > 0) {
            query = query.join(QStudy.study.patient, QPatient.patient);
            query = QueryBuilder.applyPatientIDJoins(query, pids);
        }

        return query;
    }

    private Predicate[] createPredicates(RetrieveContext ctx) {
        if (ctx.getSeriesMetadataUpdate() != null)
            return new Predicate[]{ QSeries.series.pk.eq(ctx.getSeriesMetadataUpdate().seriesPk) };

        BooleanBuilder predicate = new BooleanBuilder();
        predicate.and(QueryBuilder.patientIDPredicate(ctx.getPatientIDs()));
        predicate.and(QueryBuilder.accessControl(ctx.getAccessControlIDs()));
        predicate.and(QueryBuilder.uidsPredicate(QStudy.study.studyInstanceUID, ctx.getStudyInstanceUIDs()));
        predicate.and(QueryBuilder.uidsPredicate(QSeries.series.seriesInstanceUID, ctx.getSeriesInstanceUIDs()));
        QueryRetrieveView qrView = ctx.getQueryRetrieveView();
        if (qrView != null) {
            predicate.and(QueryBuilder.hideRejectedInstance(
                    codeCache.findOrCreateEntities(qrView.getShowInstancesRejectedByCodes()),
                    qrView.isHideNotRejectedInstances()));
            predicate.and(QueryBuilder.hideRejectionNote(
                    codeCache.findOrCreateEntities(qrView.getHideRejectionNotesWithCodes())));
        }
        String[] sopInstanceUIDs = ctx.getSopInstanceUIDs();
        if (QueryBuilder.isUniversalMatching(sopInstanceUIDs)) {
            return new Predicate[]{ predicate };
        }
        // SQL Server actually does support lesser parameters than its specified limit (2100)
        int limit = getInExpressionCountLimit() - 10;
        if (limit <= 0 || sopInstanceUIDs.length < limit) {
            return new Predicate[]{
                    ExpressionUtils.and(predicate, QInstance.instance.sopInstanceUID.in(sopInstanceUIDs))};
        }
        int index = sopInstanceUIDs.length / limit + 1;
        int remainder = sopInstanceUIDs.length % limit;
        Predicate[] predicates = new Predicate[index];
        if (remainder > 0) {
            String[] sopIUIDs = new String[remainder];
            System.arraycopy(sopInstanceUIDs, sopInstanceUIDs.length - remainder, sopIUIDs, 0, remainder);
            predicates[--index] = ExpressionUtils.and(predicate, QInstance.instance.sopInstanceUID.in(sopIUIDs));
        }
        String[] sopIUIDs = new String[limit];
        while (--index >= 0) {
            System.arraycopy(sopInstanceUIDs, index * limit, sopIUIDs, 0, limit);
            predicates[index] = ExpressionUtils.and(predicate, QInstance.instance.sopInstanceUID.in(sopIUIDs));
        }
        return predicates;
    }

    private HibernateQuery<Tuple> queryMetadataStoragePath(RetrieveContext ctx, StatelessSession session) {
        HibernateQuery<Tuple> query = new HibernateQuery<Void>(session).select(METADATA_STORAGE_PATH)
                .from(QSeries.series)
                .join(QSeries.series.metadata, QMetadata.metadata)
                .join(QSeries.series.study, QStudy.study);

        IDWithIssuer[] pids = ctx.getPatientIDs();
        if (pids.length > 0) {
            query = query.join(QStudy.study.patient, QPatient.patient);
            query = QueryBuilder.applyPatientIDJoins(query, pids);
        }

        BooleanBuilder predicate = new BooleanBuilder();
        predicate.and(QueryBuilder.patientIDPredicate(pids));
        predicate.and(QueryBuilder.accessControl(ctx.getAccessControlIDs()));
        predicate.and(QueryBuilder.uidsPredicate(QStudy.study.studyInstanceUID, ctx.getStudyInstanceUIDs()));
        predicate.and(QueryBuilder.uidsPredicate(QSeries.series.seriesInstanceUID, ctx.getSeriesInstanceUIDs()));
        predicate.and(QSeries.series.instancePurgeState.eq(Series.InstancePurgeState.PURGED));
        return query.where(predicate);
    }

    @Override
    public Transcoder openTranscoder(RetrieveContext ctx, InstanceLocations inst,
                                     Collection<String> tsuids, boolean fmi) throws IOException {
        removeUnsupportedTransferSyntax(inst, tsuids);
        LocationInputStream locationInputStream = openLocationInputStream(ctx, inst);
        Transcoder transcoder = new Transcoder(toDicomInputStream(locationInputStream));
        transcoder.setIncludeBulkData(DicomInputStream.IncludeBulkData.URI);
        transcoder.setConcatenateBulkDataFiles(true);
        transcoder.setBulkDataDirectory(ctx.getArchiveAEExtension().getBulkDataSpoolDirectoryFile());
        transcoder.setDestinationTransferSyntax(selectTransferSyntax(locationInputStream, tsuids));
        transcoder.setCloseOutputStream(false);
        transcoder.setIncludeFileMetaInformation(fmi);
        return transcoder;
    }

    private static void removeUnsupportedTransferSyntax(InstanceLocations inst, Collection<String> tsuids)
            throws NoPresentationContextException {
        if (tsuids.isEmpty()
                || tsuids.contains(UID.ExplicitVRLittleEndian)
                || tsuids.contains(UID.ImplicitVRLittleEndian))
            return;

        Location prev = null;
        List<Location> locations = inst.getLocations();
        for (Iterator<Location> iter = locations.iterator(); iter.hasNext();) {
            Location next = iter.next();
            if (next.getObjectType()  != Location.ObjectType.DICOM_FILE
                    || !tsuids.contains((prev = next).getTransferSyntaxUID()))
                iter.remove();
        }
        if (locations.isEmpty())
            throw new NoPresentationContextException(inst.getSopClassUID(), prev.getTransferSyntaxUID());
    }

    private static String selectTransferSyntax(LocationInputStream lis, Collection<String> tsuids) {
        String tsuid = lis.location.getTransferSyntaxUID();
        return tsuids.isEmpty() || tsuids.contains(tsuid)
                ? tsuid
                : tsuids.contains(UID.ExplicitVRLittleEndian)
                ? UID.ExplicitVRLittleEndian
                : UID.ImplicitVRLittleEndian;
    }

    @Override
    public DicomInputStream openDicomInputStream(RetrieveContext ctx, InstanceLocations inst) throws IOException {
        return toDicomInputStream(openLocationInputStream(ctx, inst));
    }

    private static DicomInputStream toDicomInputStream(LocationInputStream lis) throws IOException {
        try {
            return new DicomInputStream(lis.stream);
        } catch (IOException e) {
            SafeClose.close(lis.stream);
            throw e;
        }
    }

    @Override
    public Map<String,Collection<InstanceLocations>> removeNotAccessableMatches(RetrieveContext ctx) {
        ArchiveAEExtension arcAE = ctx.getArchiveAEExtension();
        String altCMoveSCP = arcAE.alternativeCMoveSCP();
        ArchiveDeviceExtension arcDev = getArchiveDeviceExtension();
        Collection<InstanceLocations> matches = ctx.getMatches();
        int numMatches = matches.size();
        Map<String,Collection<InstanceLocations>> notAccessable = new HashMap<>(1);
        Iterator<InstanceLocations> iter = matches.iterator();
        while (iter.hasNext()) {
            InstanceLocations match = iter.next();
            if (!isAccessable(arcDev, match)) {
                iter.remove();
                if (!match.getLocations().isEmpty())
                    putMatchTo(notAccessable, altCMoveSCP, match, numMatches);
                else
                    putMatchTo(notAccessable, StringUtils.maskNull(match.getExternalRetrieveAET(), altCMoveSCP),
                            match, numMatches);
            }
        }
        return notAccessable;
    }

    private void putMatchTo(
            Map<String, Collection<InstanceLocations>> map, String aet, InstanceLocations match, int numMatches) {
        Collection<InstanceLocations> list = map.get(aet);
        if (list == null)
            map.put(aet, list = new ArrayList<InstanceLocations>(numMatches));
        list.add(match);
    }

    @Override
    public AttributesCoercion getAttributesCoercion(RetrieveContext ctx, InstanceLocations inst) {
        return uidRemap(inst, new MergeAttributesCoercion(inst.getAttributes(), coercion(ctx, inst)));
    }

    @Override
    public void waitForPendingCStoreForward(RetrieveContext ctx) {
        Association fwdas = ctx.getFallbackAssociation();
        if (fwdas == null)
            return;

        Association rqas = ctx.getRequestAssociation();
        String fallbackMoveSCP = fwdas.getCalledAET();
        String suids = Arrays.toString(ctx.getStudyInstanceUIDs());
        String destAET = ctx.getDestinationAETitle();
        try {
            LOG.info("{}: wait for pending forward of objects of study{} from {} to {}",
                    rqas, suids, fallbackMoveSCP, destAET);
            ctx.waitForPendingCStoreForward();
            LOG.info("{}: complete forward of objects of study{} from {} to {} - remaining={}, completed={}, failed={}, warning={}",
                    rqas, suids, fallbackMoveSCP, destAET, ctx.remaining(), ctx.completed(), ctx.failed(), ctx.warning());
        } catch (InterruptedException e) {
            LOG.warn("{}: failed to wait for pending forward of  objects of study{} from {} to {}:\n",
                    rqas, suids, fallbackMoveSCP, destAET, e);
        }
    }

    @Override
    public void waitForPendingCMoveForward(RetrieveContext ctx) {
        waitForPendingCMoveForward(ctx, ctx.getForwardAssociation());
        waitForPendingCMoveForward(ctx, ctx.getFallbackAssociation());
    }

    private void waitForPendingCMoveForward(RetrieveContext ctx, Association fwdas) {
        if (fwdas == null)
            return;

        Association rqas = ctx.getRequestAssociation();
        String moveSCP = fwdas.getRemoteAET();
        LOG.info("{}: wait for outstanding C-MOVE RSP(s) for C-MOVE RQ(s) forwarded to {}",
                rqas, moveSCP);
        try {
            fwdas.waitForOutstandingRSP();
        } catch (InterruptedException e) {
            LOG.warn("{}: failed to wait for outstanding C-MOVE RSP(s) for C-MOVE RQ(s) forwarded to {}",
                    rqas, moveSCP, e);
        }
        try {
            fwdas.release();
        } catch (IOException e) {
            LOG.warn("{}: failed to release association to {}:\n", rqas, moveSCP, e);
        }
    }

    @Override
    public void updateCompleteness(RetrieveContext ctx) {
        if (ctx.isRetryFailedRetrieve())
            try {
                ejb.updateCompleteness(ctx, completeness(ctx));
            } catch (Exception e) {
                LOG.warn("{}: failed to update completeness of {}\n",
                        ctx.getRequestAssociation(), ctx.getQueryRetrieveLevel(), e);
            }
    }

    private Completeness completeness(RetrieveContext ctx) {
        Association as = ctx.getRequestAssociation();
        Association fwdas = ctx.getFallbackAssociation();
        String fallbackMoveSCP = fwdas.getRemoteAET();
        int expected = queryFallbackCMoveSCPLeadingCFindSCP(ctx);
        if (expected > 0) {
            int retrieved = ctx.completed() + ctx.warning();
            if (retrieved >= expected)
                return Completeness.COMPLETE;

            LOG.warn("{}: Expected {} but actual retrieved {} objects of study{} from {}",
                    as, expected, retrieved, Arrays.toString(ctx.getStudyInstanceUIDs()), fallbackMoveSCP);
            return Completeness.PARTIAL;
        }

        int failed = ctx.getFallbackMoveRSPFailed();
        if (failed == 0)
            return Completeness.COMPLETE;

        LOG.warn("{}: Failed to retrieve {} from {} objects of study{} from {}",
                as, failed, ctx.getFallbackMoveRSPNumberOfMatches(),
                Arrays.toString(ctx.getStudyInstanceUIDs()), fallbackMoveSCP);

        String[] failedIUIDs = ctx.getFallbackMoveRSPFailedIUIDs();
        if (failedIUIDs.length == 0) {
            LOG.warn("{}: Missing Failed SOP Instance UID List in C-MOVE-RSP from {}", as, fallbackMoveSCP);
            return Completeness.PARTIAL;
        }
        if (failed != failedIUIDs.length) {
            LOG.warn("{}: Number Of Failed Suboperations [{}] does not match " +
                            "size of Failed SOP Instance UID List [{}] in C-MOVE-RSP from {}",
                    as, failed, failedIUIDs.length, fallbackMoveSCP);
        }
        return Completeness.PARTIAL;
    }

    private int queryFallbackCMoveSCPLeadingCFindSCP(RetrieveContext ctx) {
        String findSCP = ctx.getArchiveAEExtension().fallbackCMoveSCPLeadingCFindSCP();
        if (findSCP == null || ctx.getQueryRetrieveLevel() != QueryRetrieveLevel2.STUDY)
            return -1;

        int expected = 0;
        ApplicationEntity localAE = ctx.getLocalApplicationEntity();
        for (String studyIUID : ctx.getStudyInstanceUIDs()) {
            List<Attributes> studies;
            try {
                studies = cfindscu.findStudy(localAE, findSCP, Priority.NORMAL, studyIUID,
                        Tag.NumberOfStudyRelatedInstances);
            } catch (Exception e) {
                LOG.warn("Failed to query Study[{}] from {} - cannot verify number of retrieved objects from {}:\n",
                        studyIUID, findSCP, ctx.getFallbackAssociation().getRemoteAET(), e);
                return -1;
            }
            if (studies.isEmpty()) {
                LOG.warn("Study[{}] not found at {} - cannot verify number of retrieved objects from {}",
                        studyIUID, findSCP, ctx.getFallbackAssociation().getRemoteAET());
                return -1;
            }
            expected += studies.get(0).getInt(Tag.NumberOfStudyRelatedInstances, 0);
        }
        return expected;
    }

    private AttributesCoercion uidRemap(InstanceLocations inst, AttributesCoercion next) {
        UIDMap uidMap = inst.getLocations().get(0).getUidMap();
        return uidMap != null ? new RemapUIDsAttributesCoercion(uidMap.getUIDMap(), next) : next;
    }

    private AttributesCoercion coercion(RetrieveContext ctx, InstanceLocations inst) {
        if (ctx.isUpdateSeriesMetadata())
            return new SeriesMetadataAttributeCoercion(ctx, inst);

        ArchiveAEExtension aeExt = ctx.getArchiveAEExtension();
        ArchiveAttributeCoercion rule = aeExt.findAttributeCoercion(
                ctx.getRequestorHostName(), ctx.getDestinationAETitle(), TransferCapability.Role.SCP, Dimse.C_STORE_RQ,
                inst.getSopClassUID());
        if (rule == null)
            return null;

        AttributesCoercion coercion = DeIdentificationAttributesCoercion.valueOf(rule.getDeIdentification(), null);
        String xsltStylesheetURI = rule.getXSLTStylesheetURI();
        if (xsltStylesheetURI != null)
        try {
            Templates tpls = TemplatesCache.getDefault().get(StringUtils.replaceSystemProperties(xsltStylesheetURI));
            coercion = new XSLTAttributesCoercion(tpls, coercion)
                    .includeKeyword(!rule.isNoKeywords())
                    .setupTransformer(setupTransformer(ctx));
        } catch (TransformerConfigurationException e) {
            LOG.error("{}: Failed to compile XSL: {}", ctx.getLocalAETitle(), xsltStylesheetURI, e);
        }
        String leadingCFindSCP = rule.getLeadingCFindSCP();
        if (leadingCFindSCP != null) {
            coercion = new CFindSCUAttributeCoercion(ctx.getLocalApplicationEntity(), leadingCFindSCP,
                    rule.getAttributeUpdatePolicy(), cfindscu, leadingCFindSCPQueryCache, coercion);
        }
        LOG.info("Coerce Attributes from rule: {}", rule);
        return coercion;
    }

    private SAXTransformer.SetupTransformer setupTransformer(RetrieveContext ctx) {
        return t -> {
            t.setParameter("LocalAET", ctx.getLocalAETitle());
            if (ctx.getDestinationAETitle() != null)
                t.setParameter("RemoteAET", ctx.getDestinationAETitle());
            t.setParameter("RemoteHostname", ctx.getDestinationHostName());
        };
    }

    private boolean isAccessable(ArchiveDeviceExtension arcDev, InstanceLocations match) {
        for (Location location : match.getLocations()) {
            if (arcDev.getStorageDescriptor(location.getStorageID()) != null)
                return true;
        }
        return false;
    }

    @Override
    public LocationInputStream openLocationInputStream(RetrieveContext ctx, InstanceLocations inst)
            throws IOException {
        String studyInstanceUID = inst.getAttributes().getString(Tag.StudyInstanceUID);
        ArchiveDeviceExtension arcdev = getArchiveDeviceExtension();
        Map<Availability, List<Location>> locationsByAvailability = inst.getLocations()
                .stream().filter(Location::isDicomFile)
                .collect(Collectors.groupingBy(l -> arcdev
                        .getStorageDescriptor(l.getStorageID()).getInstanceAvailability()));

        List<Location> locations = locationsByAvailability.get(Availability.ONLINE);
        if (locations == null)
            locations = locationsByAvailability.get(Availability.NEARLINE);
        else if (locationsByAvailability.containsKey(Availability.NEARLINE))
            locations.addAll(locationsByAvailability.get(Availability.NEARLINE));

        if (locations == null || locations.isEmpty()) {
            throw new IOException("Failed to find location of " + inst);
        }
        IOException ex = null;
        for (Location location : locations) {
            try {
                LOG.debug("Read {} from {}", inst, location);
                return openLocationInputStream(getStorage(location.getStorageID(), ctx), location, studyInstanceUID);
            } catch (IOException e) {
                LOG.warn("Failed to read {} from {}", inst, location);
                ex = e;
            }
        }
        throw ex;
    }

    private LocationInputStream openLocationInputStream(
            Storage storage, Location location, String studyInstanceUID)
            throws IOException {
        ReadContext readContext = createReadContext(storage, location.getStoragePath(), studyInstanceUID);
        InputStream stream = storage.openInputStream(readContext);
        return new LocationInputStream(stream, readContext, location);
    }

    private ReadContext createReadContext(Storage storage, String storagePath, String studyInstanceUID) {
        ReadContext readContext = storage.createReadContext();
        readContext.setStoragePath(storagePath);
        readContext.setStudyInstanceUID(studyInstanceUID);
        return readContext;
    }

    @Override
    public Storage getStorage(String storageID, RetrieveContext ctx) {
        Storage storage = ctx.getStorage(storageID);
        if (storage == null) {
            ArchiveDeviceExtension arcDev = getArchiveDeviceExtension();
            storage = storageFactory.getStorage(arcDev.getStorageDescriptorNotNull(storageID));
            ctx.putStorage(storageID, storage);
        }
        return storage;
    }

    @Override
    public Attributes loadMetadata(RetrieveContext ctx, InstanceLocations inst)
            throws IOException {
        Attributes attrs;
        attrs = loadMetadataFromJSONFile(ctx, inst);
        if (attrs == null)
            attrs = loadMetadataFromDicomFile(ctx, inst);

        getAttributesCoercion(ctx, inst).coerce(attrs, null);
        return attrs;
    }

    private Attributes loadMetadataFromJSONFile(RetrieveContext ctx, InstanceLocations inst) throws IOException {
        String studyInstanceUID = inst.getAttributes().getString(Tag.StudyInstanceUID);
        for (Location location : inst.getLocations()) {
            if (location.getObjectType() == Location.ObjectType.METADATA) {
                Storage storage = getStorage(location.getStorageID(), ctx);
                try (InputStream in = storage.openInputStream(
                        createReadContext(storage, location.getStoragePath(), studyInstanceUID))) {
                    return parseJSON(in, false);
                }
            }
        }
        return null;
    }

    private Attributes parseJSON(InputStream in, boolean skipBulkDataURI) throws IOException {
        JSONReader jsonReader = new JSONReader(Json.createParser(new InputStreamReader(in, "UTF-8")));
        jsonReader.setSkipBulkDataURI(skipBulkDataURI);
        return jsonReader.readDataset(null);
    }

    private Attributes loadMetadataFromDicomFile(RetrieveContext ctx, InstanceLocations inst) throws IOException {
        Attributes attrs;
        try (DicomInputStream dis = openDicomInputStream(ctx, inst)) {
            dis.setIncludeBulkData(DicomInputStream.IncludeBulkData.URI);
            dis.setBulkDataCreator(new BulkDataCreator() {
                @Override
                public BulkData createBulkData(DicomInputStream dis) throws IOException {
                    dis.skipFully(dis.length());
                    return new BulkData(null, "", dis.bigEndian());
                }
            });
            attrs = dis.readDataset(-1, Tag.PixelData);
            if (dis.tag() == Tag.PixelData) {
                attrs.setValue(Tag.PixelData, dis.vr(), new BulkData(null, "", dis.bigEndian()));
            }
        }
        return attrs;
    }
}
