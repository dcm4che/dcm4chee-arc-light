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
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.StgCmtPolicy;
import org.dcm4chee.arc.conf.StorageDescriptor;
import org.dcm4chee.arc.entity.Instance;
import org.dcm4chee.arc.entity.Location;
import org.dcm4chee.arc.entity.StgCmtResult;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveService;
import org.dcm4chee.arc.stgcmt.StgCmtContext;
import org.dcm4chee.arc.stgcmt.StgCmtManager;
import org.dcm4chee.arc.storage.ReadContext;
import org.dcm4chee.arc.storage.Storage;
import org.dcm4chee.arc.store.InstanceLocations;
import org.dcm4chee.arc.store.StoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2018
 */
@ApplicationScoped
public class StgCmtManagerImpl implements StgCmtManager {
    private final Logger LOG = LoggerFactory.getLogger(StgCmtManagerImpl.class);
    private static final int BUFFER_SIZE = 8192;

    @Inject
    private StgCmtEJB ejb;

    @Inject
    private RetrieveService retrieveService;

    @Inject
    private StoreService storeService;

    @Override
    public void addExternalRetrieveAETs(Attributes eventInfo, Device device) {
        ejb.addExternalRetrieveAETs(eventInfo, device);
    }

    @Override
    public void persistStgCmtResult(StgCmtResult result) {
        ejb.persistStgCmtResult(result);
    }

    @Override
    public List<StgCmtResult> listStgCmts(
            StgCmtResult.Status status, String studyUID, String exporterID, int offset, int limit) {
        return ejb.listStgCmts(status, studyUID, exporterID, offset, limit);
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
                checkLocations(ctx, retrCtx);
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
    public void calculateResult(StgCmtContext ctx, String studyIUID, String seriesIUID, String sopIUID)
            throws IOException {
        try (RetrieveContext retrCtx = retrieveService.newRetrieveContext(
                ctx.getLocalAET(), studyIUID, seriesIUID, sopIUID) ) {
            if (retrieveService.calculateMatches(retrCtx)) {
                checkLocations(ctx, retrCtx);
            }
        }
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

    private void checkLocations(StgCmtContext ctx, RetrieveContext retrCtx) {
        List<InstanceLocations> matches = retrCtx.getMatches();
        Attributes eventInfo = ctx.getEventInfo();
        String commonRetrieveAET = matches.stream()
                .map(InstanceLocations::getRetrieveAETs)
                .reduce((x,y) -> x != null && x.equals(y) ? x : null)
                .get();
        if (commonRetrieveAET != null) {
            eventInfo.setString(Tag.RetrieveAETitle, VR.AE, commonRetrieveAET);
        }

        Set<String> studyInstanceUIDs = new HashSet<>();
        List<UpdateLocation> updateLocations = new ArrayList<>();
        for (InstanceLocations inst : matches) {
            String cuid = inst.getSopClassUID();
            String iuid = inst.getSopInstanceUID();
            if (ctx.getStgCmtPolicy() == StgCmtPolicy.DB_RECORD_EXISTS
                    || checkLocations(ctx, retrCtx, inst, updateLocations)) {
                eventInfo.ensureSequence(Tag.ReferencedSOPSequence, retrCtx.getNumberOfMatches())
                        .add(refSOP(cuid, iuid,
                                commonRetrieveAET == null ? inst.getRetrieveAETs() : null));
            } else {
                eventInfo.ensureSequence(Tag.FailedSOPSequence, retrCtx.getNumberOfMatches())
                        .add(failedSOP(cuid, iuid, Status.ProcessingFailure));
            }
            Attributes attr = inst.getAttributes();
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
        if (!updateLocations.isEmpty()) {
            updateLocations(ctx, updateLocations);
        }
    }

    private void updateLocations(StgCmtContext ctx, List<UpdateLocation> updateLocations) {
        Map<String, Map<String, List<UpdateLocation>>> updateLocationsByStudyAndSeriesIUID = updateLocations.stream()
                .collect(Collectors.groupingBy(
                        x -> x.instanceLocation.getAttributes().getString(Tag.StudyInstanceUID),
                        Collectors.groupingBy(
                                x -> x.instanceLocation.getAttributes().getString(Tag.SeriesInstanceUID))));
        updateLocationsByStudyAndSeriesIUID.forEach(
                (studyIUID, seriesMap) -> seriesMap.forEach(
                        (seriesIUID, updateLocationsOfSeries) -> {
            boolean instancesPurged = updateLocationsOfSeries.get(0).location.getPk() == 0;
            if (instancesPurged) {
                try {
                    restoreInstances(ctx.getArchiveAEExtension(), studyIUID, seriesIUID, updateLocationsOfSeries);
                    instancesPurged = false;
                } catch (Exception e) {
                    LOG.warn("Failed to restore Instance records of Series[uid={}] of Study[uid={}]" +
                                    " - cannot update Location records\n",
                            seriesIUID, studyIUID, e);
                }
            }
            if (!instancesPurged) {
                for (UpdateLocation updateLocation : updateLocationsOfSeries) {
                    if (updateLocation.newStatus != null) {
                        LOG.debug("Update status of {} of Instance[uid={}] of Study[uid={}] to {}",
                                updateLocation.location,
                                updateLocation.instanceLocation.getSopInstanceUID(),
                                studyIUID,
                                updateLocation.newStatus);
                        ejb.setStatus(updateLocation.location.getPk(), updateLocation.newStatus);
                    } else {
                        LOG.debug("Set missing digest of {} of Instance[uid={}] of Study[uid={}]",
                                updateLocation.location,
                                updateLocation.instanceLocation.getSopInstanceUID(),
                                studyIUID);
                        ejb.setDigest(updateLocation.location.getPk(), updateLocation.newDigest);
                    }
                }
                storeService.scheduleMetadataUpdate(studyIUID, seriesIUID);
            }
        }));
    }

    private void restoreInstances(ArchiveAEExtension arcAE, String studyIUID, String seriesIUID,
                                  List<UpdateLocation> updateLocations) throws IOException {
        List<Instance> instances = storeService.restoreInstances(storeService.newStoreSession(
                arcAE.getApplicationEntity(), null),
                studyIUID,
                seriesIUID,
                arcAE.getPurgeInstanceRecordsDelay());
        Map<String, Map<String, Location>> restoredLocations = instances.stream()
                .flatMap(inst -> inst.getLocations().stream())
                .collect(Collectors.groupingBy(l -> l.getStorageID(),
                        Collectors.toMap(l -> l.getStoragePath(), Function.identity())));
        for (Iterator<UpdateLocation> iter = updateLocations.iterator(); iter.hasNext(); ) {
            UpdateLocation updateLocation = iter.next();
            Location l = updateLocation.location;
            updateLocation.location = restoredLocations.get(l.getStorageID()).get(l
                    .getStoragePath());
            if (updateLocation.location == null) {
                LOG.warn("Failed to find {} record of Instance[uid={}] of Series[uid={}] of Study[uid={}]" +
                                " - cannot update Location record",
                        l, updateLocation.instanceLocation.getSopInstanceUID(), seriesIUID,
                        studyIUID);
                iter.remove();
            }
        }
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

    private boolean checkLocations(StgCmtContext ctx, RetrieveContext retrCtx, InstanceLocations inst,
                                   List<UpdateLocation> updateLocations) {
        int locationsOnStgCmtStorage = 0;
        Attributes attrs = inst.getAttributes();
        String studyInstanceUID = attrs.getString(Tag.StudyInstanceUID);
        for (Location l : inst.getLocations()) {
            if (ctx.isStgCmtStorageID(l.getStorageID())) {
                locationsOnStgCmtStorage++;
                Storage storage = retrieveService.getStorage(l.getStorageID(), retrCtx);
                CheckResult result = checkLocation(ctx, inst, l, storage, updateLocations);
                if (ctx.isStgCmtUpdateLocationStatus() && l.getStatus() != result.status) {
                    updateLocations.add(new UpdateLocation(inst, l, result.status, null));
                }
                if (result.ok()) {
                    return true;
                }
                if (result.ioException != null) {
                    LOG.info("{} of {} on {} of Instance[uid={}] of Study[uid={}]:\n",
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
                    Arrays.toString(ctx.getStgCmtStorageIDs()));
        }
        return false;
    }

    private CheckResult checkLocation(StgCmtContext ctx, InstanceLocations inst, Location l, Storage storage,
                                      List<UpdateLocation> updateLocations) {
        ReadContext readContext = storage.createReadContext();
        readContext.setStoragePath(l.getStoragePath());
        readContext.setStudyInstanceUID(inst.getAttributes().getString(Tag.StudyInstanceUID));
        switch (ctx.getStgCmtPolicy()) {
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
        throw new AssertionError("StgCmtPolicy: " + ctx.getStgCmtPolicy());
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
        } catch (FileNotFoundException e) {
            return new CheckResult(Location.Status.MISSING_OBJECT, e);
        } catch (IOException e) {
            return new CheckResult(Location.Status.FAILED_TO_FETCH_METADATA, e);
        }
    }

    private CheckResult fetchObject(ReadContext readContext) {
        try (InputStream stream = readContext.getStorage().openInputStream(readContext)) {
            StreamUtils.copy(stream, null);
            return new CheckResult(Location.Status.OK);
        } catch (FileNotFoundException e) {
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
        } catch (FileNotFoundException e) {
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

    private static class UpdateLocation {
        final InstanceLocations instanceLocation;
        Location location;
        final Location.Status newStatus;
        final String newDigest;

        UpdateLocation(InstanceLocations instanceLocation, Location location,
                               Location.Status newStatus, String newDigest) {
            this.instanceLocation = instanceLocation;
            this.location = location;
            this.newStatus = newStatus;
            this.newDigest = newDigest;
        }
    }
}
