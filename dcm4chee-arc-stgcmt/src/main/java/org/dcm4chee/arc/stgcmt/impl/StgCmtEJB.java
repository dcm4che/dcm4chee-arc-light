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
 * Portions created by the Initial Developer are Copyright (C) 2016
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

package org.dcm4chee.arc.stgcmt.impl;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.jpa.hibernate.HibernateQuery;
import org.dcm4che3.data.*;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Status;
import org.dcm4che3.util.SafeClose;
import org.dcm4che3.util.StreamUtils;
import org.dcm4che3.util.TagUtils;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.ExporterDescriptor;
import org.dcm4chee.arc.conf.RejectionNote;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.stgcmt.StgCmtManager;
import org.dcm4chee.arc.storage.ReadContext;
import org.dcm4chee.arc.storage.Storage;
import org.dcm4chee.arc.storage.StorageFactory;
import org.hibernate.Session;
import org.hibernate.StatelessSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Sep 2016
 */
@Stateless
public class StgCmtEJB implements StgCmtManager {

    private final Logger LOG = LoggerFactory.getLogger(StgCmtEJB.class);

    private static final Expression<?>[] SELECT = {
            QLocation.location.pk,
            QLocation.location.storageID,
            QLocation.location.storagePath,
            QLocation.location.digest,
            QLocation.location.status,
            QInstance.instance.sopClassUID,
            QInstance.instance.sopInstanceUID,
            QInstance.instance.retrieveAETs,
            QStudy.study.studyInstanceUID
    };
    private static final int BUFFER_SIZE = 8192;


    @PersistenceContext(unitName="dcm4chee-arc")
    private EntityManager em;

    @Inject
    private Device device;

    @Inject
    private StorageFactory storageFactory;

    StatelessSession openStatelessSession() {
        return em.unwrap(Session.class).getSessionFactory().openStatelessSession();
    }

    @Override
    public void addExternalRetrieveAETs(Attributes eventInfo, Device device) {
        String transactionUID = eventInfo.getString(Tag.TransactionUID);
        StgCmtResult result = getStgCmtResult(transactionUID);
        if (result == null)
            return;
        updateExternalRetrieveAETs(eventInfo, result.getStudyInstanceUID(),
                device.getDeviceExtension(ArchiveDeviceExtension.class).getExporterDescriptorNotNull(result.getExporterID()));
        result.setStgCmtResult(eventInfo);
    }

    private StgCmtResult getStgCmtResult(String transactionUID) throws NoResultException {
        StgCmtResult result;
        try {
            result = em.createNamedQuery(StgCmtResult.FIND_BY_TRANSACTION_UID, StgCmtResult.class)
                    .setParameter(1, transactionUID).getSingleResult();
        } catch (NoResultException e) {
            LOG.warn("No Storage Commitment result found with transaction UID : " + transactionUID);
            return null;
        }
        return result;
    }

    @Override
    public Attributes calculateResult(Sequence refSopSeq, String transactionUID) {
        int size = refSopSeq.size();
        Map<String,List<Tuple>> instances = new HashMap<>(size * 4 / 3);
        String commonRetrieveAETs = queryInstances(createPredicate(refSopSeq), instances);
        Attributes eventInfo = new Attributes(4);
        if (commonRetrieveAETs != null)
            eventInfo.setString(Tag.RetrieveAETitle, VR.AE, commonRetrieveAETs);
        eventInfo.setString(Tag.TransactionUID, VR.UI, transactionUID);
        Sequence successSeq = eventInfo.newSequence(Tag.ReferencedSOPSequence, size);
        Sequence failedSeq = eventInfo.newSequence(Tag.FailedSOPSequence, size);
        HashMap<String,Storage> storageMap = new HashMap<>();
        try {
            byte[] buffer = new byte[BUFFER_SIZE];
            for (Attributes refSOP : refSopSeq) {
                String iuid = refSOP.getString(Tag.ReferencedSOPInstanceUID);
                String cuid = refSOP.getString(Tag.ReferencedSOPClassUID);
                List<Tuple> tuples = instances.get(iuid);
                if (tuples == null)
                    failedSeq.add(refSOP(cuid, iuid, Status.NoSuchObjectInstance));
                else {
                    Tuple tuple = tuples.get(0);
                    if (!cuid.equals(tuple.get(QInstance.instance.sopClassUID)))
                        failedSeq.add(refSOP(cuid, iuid, Status.ClassInstanceConflict));
                    else if (validateLocations(tuples, storageMap, buffer))
                        successSeq.add(refSOP(cuid, iuid,
                                commonRetrieveAETs == null ? tuple.get(QInstance.instance.retrieveAETs) : null));
                    else
                        failedSeq.add(refSOP(iuid, cuid, Status.ProcessingFailure));
                }
            }
        } finally {
            for (Storage storage : storageMap.values())
                SafeClose.close(storage);
        }
        if (failedSeq.isEmpty())
            eventInfo.remove(Tag.FailedSOPSequence);
        return eventInfo;
    }

    @Override
    public Attributes calculateResult(String studyIUID, String seriesIUID, String sopIUID) {
        HashMap<String,List<Tuple>> instances = new HashMap<>();
        String commonRetrieveAETs = queryInstances(createPredicate(studyIUID, seriesIUID, sopIUID), instances);
        Attributes eventInfo = new Attributes(3);
        if (commonRetrieveAETs != null)
            eventInfo.setString(Tag.RetrieveAETitle, VR.AE, commonRetrieveAETs);
        int size = instances.size();
        Sequence successSeq = eventInfo.newSequence(Tag.ReferencedSOPSequence, size);
        Sequence failedSeq = eventInfo.newSequence(Tag.FailedSOPSequence, size);
        HashMap<String,Storage> storageMap = new HashMap<>();
        try {
            byte[] buffer = new byte[BUFFER_SIZE];
            for (List<Tuple> tuples : instances.values()) {
                Tuple tuple = tuples.get(0);
                String cuid = tuple.get(QInstance.instance.sopClassUID);
                String iuid = tuple.get(QInstance.instance.sopInstanceUID);
                if (validateLocations(tuples, storageMap, buffer))
                    successSeq.add(refSOP(cuid,iuid,
                            commonRetrieveAETs == null ? tuple.get(QInstance.instance.retrieveAETs) : null));
                else
                    failedSeq.add(refSOP(cuid, iuid, Status.ProcessingFailure));
            }
        } finally {
            for (Storage storage : storageMap.values())
                SafeClose.close(storage);
        }
        if (failedSeq.isEmpty())
            eventInfo.remove(Tag.FailedSOPSequence);
        return eventInfo;
    }

    private Predicate createPredicate(Sequence refSopSeq) {
        BooleanBuilder builder = new BooleanBuilder();
        int size = refSopSeq.size();
        String[] sopIUIDs = new String[size];
        for (int i = 0; i < size; i++)
            sopIUIDs[i] = refSopSeq.get(i).getString(Tag.ReferencedSOPInstanceUID);
        builder.and(QInstance.instance.sopInstanceUID.in(sopIUIDs));
        builder.and(QLocation.location.objectType.eq(Location.ObjectType.DICOM_FILE));
        return builder;
    }

    private Predicate createPredicate(String studyIUID, String seriesIUID, String sopIUID) {
        BooleanBuilder builder = new BooleanBuilder(QStudy.study.studyInstanceUID.eq(studyIUID));
        if (seriesIUID != null) {
            builder.and(QSeries.series.seriesInstanceUID.eq(seriesIUID));
            if (sopIUID != null)
                builder.and(QInstance.instance.sopInstanceUID.eq(sopIUID));
        }
        builder.and(QLocation.location.objectType.eq(Location.ObjectType.DICOM_FILE));
        return builder;
    }

    private String queryInstances(Predicate predicate, Map<String, List<Tuple>> instances) {
        String commonRetrieveAETs = null;
        for (Tuple location : queryLocations(predicate)) {
            String iuid = location.get(QInstance.instance.sopInstanceUID);
            List<Tuple> list = instances.get(iuid);
            if (list == null) {
                instances.put(iuid, list = new ArrayList<>());
                if (instances.isEmpty())
                    commonRetrieveAETs = location.get(QInstance.instance.retrieveAETs);
                else if (commonRetrieveAETs != null
                        && !commonRetrieveAETs.equals(location.get(QInstance.instance.retrieveAETs)))
                    commonRetrieveAETs = null;
            }
            list.add(location);
        }
        return commonRetrieveAETs;
    }

    private List<Tuple> queryLocations(Predicate predicate) {
        return new HibernateQuery<Void>(em.unwrap(Session.class))
                .select(SELECT)
                .from(QLocation.location)
                .join(QLocation.location.instance, QInstance.instance)
                .join(QInstance.instance.series, QSeries.series)
                .join(QSeries.series.study, QStudy.study)
                .where(predicate)
                .fetch();
    }

    private boolean validateLocations(List<Tuple> tuples, HashMap<String, Storage> storageMap, byte[] buffer) {
        for (Tuple tuple : tuples)
            if (validateLocation(tuple, storageMap, buffer))
                return true;
        return false;
    }

    private boolean validateLocation(Tuple tuple, HashMap<String, Storage> storageMap, byte[] buffer) {
        if (tuple.get(QLocation.location.status) != Location.Status.OK)
            return false;

        String digest = tuple.get(QLocation.location.digest);
        if (digest == null)
            return true;

        Storage storage = getStorage(storageMap, tuple.get(QLocation.location.storageID));
        ReadContext readContext = storage.createReadContext();
        readContext.setStoragePath(tuple.get(QLocation.location.storagePath));
        readContext.setStudyInstanceUID(tuple.get(QStudy.study.studyInstanceUID));
        readContext.setMessageDigest(storage.getStorageDescriptor().getMessageDigest());
        try {
            try (InputStream stream = storage.openInputStream(readContext)) {
                StreamUtils.copy(stream, null, buffer);
            }
        } catch (IOException e) {
            return false;
        }
        if (TagUtils.toHexString(readContext.getDigest()).equals(digest))
            return true;

        return false;
    }

    private Storage getStorage(HashMap<String, Storage> storageMap, String storageID) {
        Storage storage = storageMap.get(storageID);
        if (storage == null) {
            storage = storageFactory.getStorage(
                    device.getDeviceExtension(ArchiveDeviceExtension.class).getStorageDescriptorNotNull(storageID));
            storageMap.put(storageID, storage);
        }
        return storage;
    }

    private static Attributes refSOP(String cuid, String iuid, String retrieveAETs) {
        Attributes attrs = new Attributes(3);
        if (retrieveAETs != null)
            attrs.setString(Tag.RetrieveAETitle, VR.AE, retrieveAETs);
        attrs.setString(Tag.ReferencedSOPClassUID, VR.UI, cuid);
        attrs.setString(Tag.ReferencedSOPInstanceUID, VR.UI,  iuid);
        return attrs;
    }

    private static Attributes refSOP(String cuid, String iuid, int failureReason) {
        Attributes attrs = new Attributes(3);
        attrs.setString(Tag.ReferencedSOPClassUID, VR.UI, cuid);
        attrs.setString(Tag.ReferencedSOPInstanceUID, VR.UI, iuid);
        attrs.setInt(Tag.FailureReason, VR.US, failureReason);
        return attrs;
    }

    private void updateExternalRetrieveAETs(Attributes eventInfo, String suid, ExporterDescriptor ed) {
        String configRetrieveAET = ed.getRetrieveAETitles().length > 0 ? ed.getRetrieveAETitles()[0] : null;
        String defRetrieveAET = eventInfo.getString(Tag.RetrieveAETitle, ed.getStgCmtSCPAETitle());
        Sequence sopSeq = eventInfo.getSequence(Tag.ReferencedSOPSequence);
        List<Instance> instances = em.createNamedQuery(Instance.FIND_BY_STUDY_IUID, Instance.class)
                                .setParameter(1, suid).getResultList();
        Set<String> studyExternalAETs = new HashSet<>(4);
        Map<Series,Set<String>> seriesExternalAETsMap = new IdentityHashMap<>();
        for (Instance inst : instances) {
            Attributes sopRef = sopRefOf(inst.getSopInstanceUID(), sopSeq);
            if (sopRef != null) {
                inst.setExternalRetrieveAET(
                        configRetrieveAET != null
                                ? configRetrieveAET
                                : sopRef.getString(Tag.RetrieveAETitle, defRetrieveAET));
            }
            if (!isRejectedOrRejectionNoteDataRetentionPolicyExpired(inst)) {
                String externalRetrieveAET = inst.getExternalRetrieveAET();
                Series series = inst.getSeries();
                Set<String> seriesExternalAETs = seriesExternalAETsMap.get(series);
                if (seriesExternalAETs == null)
                    seriesExternalAETsMap.put(series, seriesExternalAETs = new HashSet<String>(4));
                seriesExternalAETs.add(externalRetrieveAET);
                studyExternalAETs.add(externalRetrieveAET);
            }
        }
        for (Map.Entry<Series, Set<String>> entry : seriesExternalAETsMap.entrySet()) {
            Set<String> seriesExternalAETs = entry.getValue();
            if (seriesExternalAETs.size() == 1 && !seriesExternalAETs.contains(null))
                entry.getKey().setExternalRetrieveAET(seriesExternalAETs.iterator().next());
        }
        if (studyExternalAETs.size() == 1 && !studyExternalAETs.contains(null))
            instances.get(0).getSeries().getStudy().setExternalRetrieveAET(studyExternalAETs.iterator().next());
    }

    private Instance nextNotRejected(Iterator<Instance> iter) {
        while (iter.hasNext()) {
            Instance next = iter.next();
            if (!isRejectedOrRejectionNoteDataRetentionPolicyExpired(next))
                return next;
        }
        return null;
    }

    private boolean isRejectedOrRejectionNoteDataRetentionPolicyExpired(Instance inst) {
        if (inst.getRejectionNoteCode() != null)
            return true;

        if (!inst.getSopClassUID().equals(UID.KeyObjectSelectionDocumentStorage)
                || inst.getConceptNameCode() == null)
            return false;

        ArchiveDeviceExtension arcdev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        RejectionNote rjnote = arcdev.getRejectionNote(inst.getConceptNameCode().getCode());
        return rjnote != null && rjnote.getRejectionNoteType() == RejectionNote.Type.DATA_RETENTION_POLICY_EXPIRED;
    }

    private Attributes sopRefOf(String iuid, Sequence seq) {
        for (Attributes item : seq) {
            if (iuid.equals(item.getString(Tag.ReferencedSOPInstanceUID)))
                return item;
        }
        return null;
    }

    @Override
    public void persistStgCmtResult(StgCmtResult result) {
        em.persist(result);
    }

    @Override
    public List<StgCmtResult> listStgCmts(
            StgCmtResult.Status status, String studyUID, String exporterID, int offset, int limit) {
        HibernateQuery<StgCmtResult> query = getStgCmtResults(status, studyUID, exporterID);
        List<StgCmtResult> results = query.fetch();
        if (results.isEmpty())
            return Collections.emptyList();
        return results;
    }

    @Override
    public boolean deleteStgCmt(String transactionUID) {
        try {
            StgCmtResult result = getStgCmtResult(transactionUID);
            if (result != null) {
                em.remove(result);
                return true;
            }
        } catch (Exception e) {
            LOG.warn("Deletion of Storage Commitment Result threw exception : " + e);
        }
        return false;
    }

    @Override
    public int deleteStgCmts(StgCmtResult.Status status, Date updatedBefore) {
        List<StgCmtResult> results = status != null
                                    ? updatedBefore != null
                                        ? em.createNamedQuery(StgCmtResult.FIND_BY_STATUS_AND_UPDATED_BEFORE, StgCmtResult.class)
                                            .setParameter(1, status).setParameter(2, updatedBefore).getResultList()
                                        : em.createNamedQuery(StgCmtResult.FIND_BY_STATUS, StgCmtResult.class)
                                            .setParameter(1, status).getResultList()
                                    : updatedBefore != null
                                        ? em.createNamedQuery(StgCmtResult.FIND_BY_UPDATED_BEFORE, StgCmtResult.class)
                                            .setParameter(1, updatedBefore).getResultList()
                                        : em.createNamedQuery(StgCmtResult.FIND_ALL, StgCmtResult.class).getResultList();
        if (results.isEmpty())
            return 0;
        for (StgCmtResult result : results)
            em.remove(result);
        return results.size();
    }

    private HibernateQuery<StgCmtResult> getStgCmtResults(StgCmtResult.Status status, String studyUID, String exporterId) {
        Predicate predicate = getPredicates(status, studyUID, exporterId);
        HibernateQuery<StgCmtResult> query = new HibernateQuery<Void>(em.unwrap(Session.class))
                .select(QStgCmtResult.stgCmtResult).from(QStgCmtResult.stgCmtResult);
        return query.where(predicate);
    }

    private Predicate getPredicates(StgCmtResult.Status status, String studyUID, String exporterId) {
        BooleanBuilder predicate = new BooleanBuilder();
        if (status != null)
            predicate.and(QStgCmtResult.stgCmtResult.status.eq(status));
        if (studyUID != null)
            predicate.and(QStgCmtResult.stgCmtResult.studyInstanceUID.eq(studyUID));
        if (exporterId != null)
            predicate.and(QStgCmtResult.stgCmtResult.exporterID.eq(exporterId.toUpperCase()));
        return predicate;
    }
}
