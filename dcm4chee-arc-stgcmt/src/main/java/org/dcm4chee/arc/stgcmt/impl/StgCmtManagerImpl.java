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

package org.dcm4chee.arc.stgcmt.impl;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Status;
import org.dcm4che3.util.StreamUtils;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.util.TagUtils;
import org.dcm4chee.arc.conf.StorageVerificationPolicy;
import org.dcm4chee.arc.conf.StorageDescriptor;
import org.dcm4chee.arc.entity.*;
import org.dcm4chee.arc.event.QueueMessageEvent;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.qmgt.IllegalTaskStateException;
import org.dcm4chee.arc.qmgt.Outcome;
import org.dcm4chee.arc.qmgt.QueueSizeLimitExceededException;
import org.dcm4chee.arc.query.util.TaskQueryParam;
import org.dcm4chee.arc.retrieve.*;
import org.dcm4chee.arc.stgcmt.*;
import org.dcm4chee.arc.storage.ReadContext;
import org.dcm4chee.arc.storage.Storage;
import org.dcm4chee.arc.store.InstanceLocations;
import org.dcm4chee.arc.store.StoreService;
import org.dcm4chee.arc.store.UpdateLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.persistence.Tuple;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jul 2018
 */
@ApplicationScoped
public class StgCmtManagerImpl implements StgCmtManager {
    private final Logger LOG = LoggerFactory.getLogger(StgCmtManagerImpl.class);

    @Inject
    private Device device;

    @Inject
    private StgCmtEJB ejb;

    @Inject
    private RetrieveService retrieveService;

    @Inject
    private StoreService storeService;

    @Inject
    private Event<StgCmtContext> stgCmtEvent;

    @Override
    public void addExternalRetrieveAETs(Attributes eventInfo, Device device) {
        ejb.addExternalRetrieveAETs(eventInfo, device);
    }

    @Override
    public void persistStgCmtResult(StgCmtResult result) {
        ejb.persistStgCmtResult(result);
    }

    @Override
    public List<StgCmtResult> listStgCmts(TaskQueryParam stgCmtResultQueryParam, int offset, int limit) {
        return ejb.listStgCmts(stgCmtResultQueryParam, offset, limit);
    }

    @Override
    public boolean deleteStgCmt(String transactionUID) {
        return ejb.deleteStgCmt(transactionUID);
    }

    @Override
    public int deleteStgCmts(StgCmtResult.Status status, Date updatedBefore) {
        return ejb.deleteStgCmts(status, updatedBefore);
    }

    @Override
    public void calculateResult(StgCmtContext ctx, Sequence refSopSeq) {
        int numRefSOPs = refSopSeq.size();
        try (RetrieveContext retrCtx = retrieveService.newRetrieveContext(ctx.getLocalAET(), refSopSeq)) {
            retrieveService.calculateMatches(retrCtx);
            for (Attributes refSOP : refSopSeq) {
                checkRefSop(ctx, retrCtx, refSOP, numRefSOPs);
            }
            if (!retrCtx.getMatches().isEmpty()) {
                checkLocations(ctx, retrCtx, null);
            }
        } catch (IOException e) {
            LOG.warn("Failed to calculate Storage Commitment Result\n", e);
            Sequence failedSOPSeq = ctx.getEventInfo().ensureSequence(Tag.FailedSOPSequence, numRefSOPs);
            for (Attributes refSOP : refSopSeq) {
                failedSOPSeq.add(failedSOP(
                        refSOP.getString(Tag.ReferencedSOPClassUID),
                        refSOP.getString(Tag.ReferencedSOPInstanceUID),
                        Status.ProcessingFailure));
            }
            ctx.setException(e);
        }
    }

    @Override
    public boolean calculateResult(StgCmtContext ctx, String studyIUID, String seriesIUID, String sopIUID)
            throws IOException {
        try (RetrieveContext retrCtx = retrieveService.newRetrieveContext(
                ctx.getLocalAET(), studyIUID, seriesIUID, sopIUID) ) {
            if (!retrieveService.calculateMatches(retrCtx)) {
                return false;
            }
            Map<String, SeriesResult> seriesResultMap = sopIUID == null ? new HashMap<>() : null;
            checkLocations(ctx, retrCtx, seriesResultMap);
            if (seriesResultMap != null) {
                seriesResultMap.forEach((iuid, seriesResult) -> updateSeries(retrCtx, iuid, seriesResult));
            }
        }
        return true;
    }

    private void updateSeries(RetrieveContext retrCtx, String seriesIUID, SeriesResult seriesResult) {
        try {
            SeriesInfo seriesInfo = retrCtx.getSeriesInfos().stream()
                    .filter(x -> seriesIUID.equals(x.getSeriesInstanceUID())).findFirst()
                    .get();
            if (seriesInfo.getSeriesSize() > 0 && seriesInfo.getSeriesSize() != seriesResult.size) {
                LOG.warn("Correct size of Series[uid={}] of Study[uid={}] from {} to {}",
                        seriesIUID, retrCtx.getStudyInstanceUID(), seriesInfo.getSeriesSize(), seriesResult.size);
                StudyInfo studyInfo = retrCtx.getStudyInfos().get(0);
                if (studyInfo.getStudySize() > 0) {
                    long studySize = studyInfo.getStudySize() - seriesInfo.getSeriesSize() + seriesResult.size;
                    LOG.warn("Correct size of Study[uid={}] from {} to {}",
                            retrCtx.getStudyInstanceUID(), studyInfo.getStudySize(), studySize);
                    try {
                        ejb.updateStudySize(studyInfo.getStudyPk(), studySize);
                    } catch (Exception e) {
                        LOG.warn("Failed to update size[={}] of Study[uid={}]\n",
                                studySize, retrCtx.getStudyInstanceUID(), e);
                    }
                    studyInfo.setStudySize(studySize);
                }
            }
            ejb.updateSeries(retrCtx.getStudyInstanceUID(), seriesIUID, seriesResult.failures, seriesResult.size);
        } catch (Exception e) {
            LOG.warn("Failed to update size[={}] and failures[={}] of Storage Verification of Series[uid={}] of Study[uid={}]\n",
                    seriesResult.size, seriesResult.failures, seriesIUID, retrCtx.getStudyInstanceUID(), e);
        }
    }

    @Override
    public boolean scheduleStgVerTask(
            StorageVerificationTask storageVerificationTask, HttpServletRequestInfo httpServletRequestInfo, String batchID)
            throws QueueSizeLimitExceededException {
        return ejb.scheduleStgVerTask(storageVerificationTask, httpServletRequestInfo, batchID);
    }

    @Override
    public boolean cancelStgVerTask(Long pk, QueueMessageEvent queueEvent) throws IllegalTaskStateException {
        return ejb.cancelStgVerTask(pk, queueEvent);
    }

    @Override
    public long cancelStgVerTasks(TaskQueryParam queueTaskQueryParam, TaskQueryParam stgVerTaskQueryParam) {
        return ejb.cancelStgVerTasks(queueTaskQueryParam, stgVerTaskQueryParam);
    }

    @Override
    public Tuple findDeviceNameAndMsgPropsByPk(Long pk) {
        return ejb.findDeviceNameAndMsgPropsByPk(pk);
    }

    @Override
    public void rescheduleStgVerTask(Long pk, QueueMessageEvent queueEvent) {
        ejb.rescheduleStgVerTask(pk, queueEvent);
    }

    @Override
    public void rescheduleStgVerTask(String stgVerTaskQueueMsgId) {
        ejb.rescheduleStgVerTask(stgVerTaskQueueMsgId, null);
    }

    @Override
    public List<String> listDistinctDeviceNames(TaskQueryParam queueTaskQueryParam, TaskQueryParam stgVerTaskQueryParam) {
        return ejb.listDistinctDeviceNames(queueTaskQueryParam, stgVerTaskQueryParam);
    }

    @Override
    public List<String> listStgVerTaskQueueMsgIDs(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam stgVerTaskQueryParam, int limit) {
        return ejb.listStgVerTaskQueueMsgIDs(queueTaskQueryParam, stgVerTaskQueryParam, limit);
    }

    @Override
    public List<Tuple> listStgVerTaskQueueMsgIDAndMsgProps(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam stgVerTaskQueryParam, int limit) {
        return ejb.listStgVerTaskQueueMsgIDAndMsgProps(queueTaskQueryParam, stgVerTaskQueryParam, limit);
    }

    @Override
    public boolean deleteStgVerTask(Long pk, QueueMessageEvent queueEvent) {
        return ejb.deleteStgVerTask(pk, queueEvent);
    }

    @Override
    public int deleteTasks(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam stgVerTaskQueryParam, int deleteTasksFetchSize) {
        return ejb.deleteTasks(queueTaskQueryParam, stgVerTaskQueryParam, deleteTasksFetchSize);
    }

    @Override
    public List<StgVerBatch> listStgVerBatches(
            TaskQueryParam queueBatchQueryParam, TaskQueryParam stgVerBatchQueryParam, int offset, int limit) {
        return ejb.listStgVerBatches(queueBatchQueryParam, stgVerBatchQueryParam, offset, limit);
    }

    @Override
    public Outcome executeStgVerTask(StorageVerificationTask storageVerificationTask, HttpServletRequestInfo request) throws IOException {
        String localAET = storageVerificationTask.getLocalAET();
        StgCmtContext ctx = new StgCmtContext(device.getApplicationEntity(localAET, true), localAET)
                .setRequest(request);
        if (storageVerificationTask.getStorageVerificationPolicy() != null)
            ctx.setStorageVerificationPolicy(storageVerificationTask.getStorageVerificationPolicy());
        if (storageVerificationTask.getUpdateLocationStatus() != null)
            ctx.setUpdateLocationStatus(Boolean.valueOf(storageVerificationTask.getUpdateLocationStatus()));
        String[] storageIDs = storageVerificationTask.getStorageIDs();
        if (storageIDs.length > 0)
            ctx.setStorageIDs(storageIDs);
        try {
            calculateResult(ctx,
                    storageVerificationTask.getStudyInstanceUID(),
                    storageVerificationTask.getSeriesInstanceUID(),
                    storageVerificationTask.getSOPInstanceUID());
        } catch (IOException e) {
            ctx.setException(e);
            stgCmtEvent.fire(ctx);
            throw e;
        }
        stgCmtEvent.fire(ctx);
        Attributes eventInfo = ctx.getEventInfo();
        int completed = sizeOf(eventInfo.getSequence(Tag.ReferencedSOPSequence));
        int failed = sizeOf(eventInfo.getSequence(Tag.FailedSOPSequence));
        storageVerificationTask.setCompleted(completed);
        storageVerificationTask.setFailed(failed);
        ejb.updateStgVerTask(storageVerificationTask);
        return new Outcome(
                failed == 0
                        ? QueueMessage.Status.COMPLETED
                        : QueueMessage.Status.WARNING,
                toOutcomeMessage(storageVerificationTask, ctx));
    }

    public void onRetrieveFailures(@Observes @RetrieveFailures RetrieveContext ctx) {
        if (ctx.isStorageVerificationOnRetrieve()) {
            Map<String, Map<String, List<UpdateLocation>>> updateLocationsByStudyAndSeriesIUID = ctx.getUpdateLocations().stream()
                    .collect(Collectors.groupingBy(
                            x -> x.instanceLocation.getAttributes().getString(Tag.StudyInstanceUID),
                            Collectors.groupingBy(
                                    x -> x.instanceLocation.getAttributes().getString(Tag.SeriesInstanceUID))));
            updateLocationsByStudyAndSeriesIUID.forEach(
                    (studyIUID, seriesMap) -> seriesMap.keySet().forEach(
                            seriesIUID -> scheduleStgVerTask(ctx, studyIUID, seriesIUID)));
        }
    }

    private void scheduleStgVerTask(RetrieveContext ctx, String studyIUID, String seriesIUID) {
        StorageVerificationTask storageVerificationTask = new StorageVerificationTask();
        storageVerificationTask.setLocalAET(ctx.getLocalAETitle());
        storageVerificationTask.setStudyInstanceUID(studyIUID);
        storageVerificationTask.setSeriesInstanceUID(seriesIUID);
        try {
            ejb.scheduleStgVerTask(storageVerificationTask, ctx.getHttpServletRequestInfo(), null);
        } catch (Exception e) {
            LOG.warn("Failed to schedule {}\n", storageVerificationTask, e);
        }
    }

    private String toOutcomeMessage(StorageVerificationTask storageVerificationTask, StgCmtContext ctx) {
        return (ctx.getStorageIDs().length == 0)
            ? (storageVerificationTask.getSeriesInstanceUID() == null)
                ? String.format("Commit Storage of Study[uid=%s] for %s: - completed: %d, failed: %d",
                    storageVerificationTask.getStudyInstanceUID(),
                    ctx.getStorageVerificationPolicy(), storageVerificationTask.getCompleted(), storageVerificationTask.getFailed())
                : (storageVerificationTask.getSOPInstanceUID() == null)
                    ? String.format("Commit Storage of Series[uid=%s] of Study[uid=%s] for %s: - completed: %d, failed: %d",
                        storageVerificationTask.getSeriesInstanceUID(),
                        storageVerificationTask.getStudyInstanceUID(),
                        ctx.getStorageVerificationPolicy(), storageVerificationTask.getCompleted(), storageVerificationTask.getFailed())
                    :  String.format("Commit Storage of Instance[uid=%s] of Series[uid=%s] of Study[uid=%s] for %s: - completed: %d, failed: %d",
                        storageVerificationTask.getSOPInstanceUID(),
                        storageVerificationTask.getSeriesInstanceUID(),
                        storageVerificationTask.getStudyInstanceUID(),
                        ctx.getStorageVerificationPolicy(), storageVerificationTask.getCompleted(), storageVerificationTask.getFailed())
            : (storageVerificationTask.getSeriesInstanceUID() == null)
                ? String.format("Commit Storage of Study[uid=%s] on Storage%s for %s: - completed: %d, failed: %d",
                    storageVerificationTask.getStudyInstanceUID(),
                    Arrays.toString(ctx.getStorageIDs()),
                    ctx.getStorageVerificationPolicy(), storageVerificationTask.getCompleted(), storageVerificationTask.getFailed())
                : (storageVerificationTask.getSOPInstanceUID() == null)
                    ? String.format("Commit Storage of Series[uid=%s] of Study[uid=%s] on Storage%s for %s: - completed: %d, failed: %d",
                        storageVerificationTask.getSeriesInstanceUID(),
                        storageVerificationTask.getStudyInstanceUID(),
                        Arrays.toString(ctx.getStorageIDs()),
                        ctx.getStorageVerificationPolicy(), storageVerificationTask.getCompleted(), storageVerificationTask.getFailed())
                    :  String.format("Commit Storage of Instance[uid=%s] of Series[uid=%s] on Storage%s of Study[uid=%s] for %s: - completed: %d, failed: %d",
                        storageVerificationTask.getSOPInstanceUID(),
                        storageVerificationTask.getSeriesInstanceUID(),
                        storageVerificationTask.getStudyInstanceUID(),
                        Arrays.toString(ctx.getStorageIDs()),
                        ctx.getStorageVerificationPolicy(), storageVerificationTask.getCompleted(), storageVerificationTask.getFailed());
    }

    private static int sizeOf(Sequence seq) {
        return seq != null ? seq.size() : 0;
    }

    private void checkRefSop(StgCmtContext ctx, RetrieveContext retrCtx, Attributes refSop, int numRefSOPs) {
        String cuid = refSop.getString(Tag.ReferencedSOPClassUID);
        String iuid = refSop.getString(Tag.ReferencedSOPInstanceUID);
        int failureReason = Status.NoSuchObjectInstance;
        Iterator<InstanceLocations> matches = retrCtx.getMatches().iterator();
        while (matches.hasNext()) {
            InstanceLocations match = matches.next();
            if (!match.getSopInstanceUID().equals(iuid)) continue;
            if (match.getSopClassUID().equals(cuid)) return;
            failureReason = Status.ClassInstanceConflict;
            matches.remove();
            break;
        }
        ctx.getEventInfo().ensureSequence(Tag.FailedSOPSequence, numRefSOPs)
                .add(failedSOP(cuid, iuid, failureReason));
    }

    private void checkLocations(StgCmtContext ctx, RetrieveContext retrCtx, Map<String,SeriesResult> seriesResultMap) {
        List<InstanceLocations> matches = retrCtx.getMatches();
        Attributes eventInfo = ctx.getEventInfo();
        String commonRetrieveAET = commonRetrieveAET(matches);
        if (commonRetrieveAET != null)
            eventInfo.setString(Tag.RetrieveAETitle, VR.AE, commonRetrieveAET);

        Set<String> studyInstanceUIDs = new HashSet<>();
        for (InstanceLocations inst : matches) {
            String cuid = inst.getSopClassUID();
            String iuid = inst.getSopInstanceUID();
            Attributes attr = inst.getAttributes();
            SeriesResult seriesResult = seriesResultMap != null
                    ? seriesResultMap.computeIfAbsent(
                            attr.getString(Tag.SeriesInstanceUID), key -> new SeriesResult())
                    : null;
            if (seriesResult != null) {
                seriesResult.size += inst.getLocations().stream().mapToLong(Location::getSize).max().getAsLong();
            }
            if (ctx.getStorageVerificationPolicy() == StorageVerificationPolicy.DB_RECORD_EXISTS
                    || checkLocationsOfInstance(ctx, retrCtx, inst)) {
                eventInfo.ensureSequence(Tag.ReferencedSOPSequence, retrCtx.getNumberOfMatches())
                        .add(refSOP(cuid, iuid, commonRetrieveAET == null ? inst.getRetrieveAETs() : null));
            } else {
                eventInfo.ensureSequence(Tag.FailedSOPSequence, retrCtx.getNumberOfMatches())
                        .add(failedSOP(cuid, iuid, Status.ProcessingFailure));
                if (seriesResult != null) {
                    seriesResult.failures++;
                }
            }
            if (studyInstanceUIDs.isEmpty()) {
                eventInfo.setString(Tag.PatientID, VR.LO, attr.getString(Tag.PatientID));
                eventInfo.setString(Tag.IssuerOfPatientID, VR.LO, attr.getString(Tag.IssuerOfPatientID));
                eventInfo.setString(Tag.PatientName, VR.PN, attr.getString(Tag.PatientName));
            }
            studyInstanceUIDs.add(attr.getString(Tag.StudyInstanceUID));
        }
        if (!studyInstanceUIDs.isEmpty()) {
            eventInfo.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUIDs.toArray(StringUtils.EMPTY_STRING));
        }
        if (!retrCtx.getUpdateLocations().isEmpty()) {
            storeService.updateLocations(ctx.getArchiveAEExtension(), retrCtx.getUpdateLocations());
        }
    }

    private String commonRetrieveAET(List<InstanceLocations> matches) {
        if (matches.isEmpty())
            return null;

        Iterator<InstanceLocations> iter = matches.iterator();
        String aets = iter.next().getRetrieveAETs();
        while (iter.hasNext())
            if (!aets.equals(iter.next().getRetrieveAETs()))
                return null;

        return aets;
    }

    private static Attributes refSOP(String cuid, String iuid, String retrieveAET) {
        Attributes attrs = new Attributes(3);
        if (retrieveAET != null) {
            attrs.setString(Tag.RetrieveAETitle, VR.AE, retrieveAET);
        }
        attrs.setString(Tag.ReferencedSOPClassUID, VR.UI, cuid);
        attrs.setString(Tag.ReferencedSOPInstanceUID, VR.UI, iuid);
        return attrs;
    }

    private static Attributes failedSOP(String cuid, String iuid, int failureReason) {
        Attributes attrs = new Attributes(3);
        attrs.setString(Tag.ReferencedSOPClassUID, VR.UI, cuid);
        attrs.setString(Tag.ReferencedSOPInstanceUID, VR.UI, iuid);
        attrs.setInt(Tag.FailureReason, VR.US, failureReason);
        return attrs;
    }

    private boolean checkLocationsOfInstance(StgCmtContext ctx, RetrieveContext retrCtx, InstanceLocations inst) {
        List<UpdateLocation> updateLocations = retrCtx.getUpdateLocations();
        int locationsOnStgCmtStorage = 0;
        Attributes attrs = inst.getAttributes();
        String studyInstanceUID = attrs.getString(Tag.StudyInstanceUID);
        for (Location l : inst.getLocations()) {
            if (ctx.checkStorageID(l.getStorageID())) {
                locationsOnStgCmtStorage++;
                Storage storage = retrieveService.getStorage(l.getStorageID(), retrCtx);
                CheckResult result = checkLocation(ctx, inst, l, storage, updateLocations);
                if (ctx.isUpdateLocationStatus() && l.getStatus() != result.status) {
                    updateLocations.add(new UpdateLocation(inst, l, result.status, null));
                }
                if (result.ok()) {
                    return true;
                }
                if (result.ioException != null) {
                    LOG.info("{} of {} of Instance[uid={}] of Study[uid={}]:\n",
                            result.status,
                            l,
                            inst.getSopInstanceUID(),
                            studyInstanceUID,
                            result.ioException);
                } else {
                    LOG.info("{} of {} of Instance[uid={}] of Study[uid={}]",
                            result.status,
                            l,
                            inst.getSopInstanceUID(),
                            studyInstanceUID);
                }
            }
        }
        if (locationsOnStgCmtStorage == 0) {
            LOG.info("Instance[uid={}] of Study[uid={}] not stored on Storage{}",
                    inst.getSopInstanceUID(),
                    studyInstanceUID,
                    Arrays.toString(ctx.getStorageIDs()));
        }
        return false;
    }

    private CheckResult checkLocation(StgCmtContext ctx, InstanceLocations inst, Location l, Storage storage,
                                      List<UpdateLocation> updateLocations) {
        ReadContext readContext = storage.createReadContext();
        readContext.setStoragePath(l.getStoragePath());
        readContext.setStudyInstanceUID(inst.getAttributes().getString(Tag.StudyInstanceUID));
        switch (ctx.getStorageVerificationPolicy()) {
            case OBJECT_EXISTS:
                return objectExists(readContext);
            case OBJECT_SIZE:
                return compareObjectSize(readContext, l);
            case OBJECT_FETCH:
                return fetchObject(readContext);
            case OBJECT_CHECKSUM:
                return recalcChecksum(readContext, inst, l, updateLocations);
            case S3_MD5SUM:
                return compareS3md5Sum(readContext, inst, l, updateLocations);
        }
        throw new AssertionError("StgCmtPolicy: " + ctx.getStorageVerificationPolicy());
    }

    private static class CheckResult {
        final Location.Status status;
        final IOException ioException;

        CheckResult(Location.Status status, IOException ioException) {
            this.status = status;
            this.ioException = ioException;
        }

        CheckResult(Location.Status status) {
            this(status, null);
        }

        boolean ok() {
            return status == Location.Status.OK;
        }
    }

    private CheckResult objectExists(ReadContext readContext) {
        return (readContext.getStorage().exists(readContext))
                ? new CheckResult(Location.Status.OK)
                : new CheckResult(Location.Status.MISSING_OBJECT);
    }

    private CheckResult compareObjectSize(ReadContext readContext, Location l) {
        try {
            return (readContext.getStorage().getContentLength(readContext) == l.getSize())
                    ? new CheckResult(Location.Status.OK)
                    : new CheckResult(Location.Status.DIFFERING_OBJECT_SIZE);
        } catch (NoSuchFileException e) {
            return new CheckResult(Location.Status.MISSING_OBJECT, e);
        } catch (IOException e) {
            return new CheckResult(Location.Status.FAILED_TO_FETCH_METADATA, e);
        }
    }

    private CheckResult fetchObject(ReadContext readContext) {
        try (InputStream stream = readContext.getStorage().openInputStream(readContext)) {
            StreamUtils.copy(stream, null);
            return new CheckResult(Location.Status.OK);
        } catch (NoSuchFileException e) {
            return new CheckResult(Location.Status.MISSING_OBJECT, e);
        } catch (IOException e) {
            return new CheckResult(Location.Status.FAILED_TO_FETCH_OBJECT, e);
        }
    }

    private CheckResult recalcChecksum(ReadContext readContext, InstanceLocations inst, Location l,
                                       List<UpdateLocation> updateLocations) {
        StorageDescriptor storageDescriptor = readContext.getStorage().getStorageDescriptor();
        MessageDigest messageDigest = storageDescriptor.getMessageDigest();
        readContext.setMessageDigest(messageDigest);
        CheckResult checkResult = fetchObject(readContext);
        if (!checkResult.ok() || messageDigest == null)
            return checkResult;

        String calculatedDigest = TagUtils.toHexString(readContext.getDigest());
        String digest = l.getDigestAsHexString();
        if (digest == null) {
            updateLocations.add(new UpdateLocation(inst, l, null, calculatedDigest));
            return checkResult;
        }

        return (calculatedDigest.equals(digest))
                ? new CheckResult(Location.Status.OK)
                : new CheckResult(Location.Status.DIFFERING_OBJECT_CHECKSUM);
    }

    private CheckResult compareS3md5Sum(ReadContext readContext, InstanceLocations inst, Location l,
                                        List<UpdateLocation> updateLocations) {
        StorageDescriptor storageDescriptor = readContext.getStorage().getStorageDescriptor();
        if (!"MD5".equals(storageDescriptor.getDigestAlgorithm())) {
            LOG.info("Digest Algorithm of {} != MD5 -> compare object size instead compare S3 MD5",
                    storageDescriptor);
            return compareObjectSize(readContext, l);
        }

        byte[] contentMD5;
        try {
            contentMD5 = readContext.getStorage().getContentMD5(readContext);
        } catch (NoSuchFileException e) {
            return new CheckResult(Location.Status.MISSING_OBJECT, e);
        } catch (IOException e) {
            return new CheckResult(Location.Status.FAILED_TO_FETCH_METADATA, e);
        }
        if (contentMD5 == null) {
            LOG.info("S3 MD5SUM not supported by {} -> recalculate object checksum instead compare S3 MD5",
                    storageDescriptor);
            return recalcChecksum(readContext, inst, l, updateLocations);
        }
        String digest = l.getDigestAsHexString();
        if (digest == null || contentMD5 == null) {
            CheckResult checkResult = recalcChecksum(readContext, inst, l, updateLocations);
            if (!checkResult.ok())
                return checkResult;

            digest = TagUtils.toHexString(readContext.getDigest());
        }
        return (TagUtils.toHexString(contentMD5).equals(digest))
                ? new CheckResult(Location.Status.OK)
                : new CheckResult(Location.Status.DIFFERING_S3_MD5SUM);
    }

    @Override
    public Iterator<StorageVerificationTask> listStgVerTasks(
            TaskQueryParam queueTaskQueryParam, TaskQueryParam stgVerTaskQueryParam, int offset, int limit) {
        return ejb.listStgVerTasks(queueTaskQueryParam, stgVerTaskQueryParam, offset, limit);
    }

    @Override
    public long countTasks(TaskQueryParam queueTaskQueryParam, TaskQueryParam stgVerTaskQueryParam) {
        return ejb.countTasks(queueTaskQueryParam, stgVerTaskQueryParam);
    }

    private static class SeriesResult {
        public int failures;
        public int size;
    }
}
