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

package org.dcm4chee.arc.retrieve.impl;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.*;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4che3.util.ReverseDNS;
import org.dcm4che3.util.SafeClose;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.entity.Location;
import org.dcm4chee.arc.entity.Series;
import org.dcm4chee.arc.retrieve.*;
import org.dcm4chee.arc.storage.Storage;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.store.InstanceLocations;
import org.dcm4chee.arc.store.UpdateLocation;

import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Aug 2015
 */
class RetrieveContextImpl implements RetrieveContext {
    private Association requestAssociation;
    private Association storeAssociation;
    private Association forwardAssociation;
    private Association fallbackAssociation;
    private final RetrieveService retrieveService;
    private final ArchiveAEExtension arcAE;
    private final String localAETitle;
    private String callingAET;
    private final QueryRetrieveView qrView;
    private QueryRetrieveLevel2 qrLevel;
    private int priority = Priority.NORMAL;
    private int moveOriginatorMessageID;
    private String moveOriginatorAETitle;
    private String destinationAETitle;
    private ApplicationEntity destinationAE;
    private WebApplication destinationWebApp;
    private StorageDescriptor destinationStorage;
    private Throwable exception;
    private IDWithIssuer[] patientIDs = {};
    private String[] studyInstanceUIDs = {};
    private String[] seriesInstanceUIDs = {};
    private Series.MetadataUpdate metadataUpdate;
    private String[] sopInstanceUIDs = {};
    private Location.ObjectType objectType = Location.ObjectType.DICOM_FILE;
    private int numberOfMatches;
    private final List<InstanceLocations> matches = new ArrayList<>();
    private final List<StudyInfo> studyInfos = new ArrayList<>();
    private final List<SeriesInfo> seriesInfos = new ArrayList<>();
    private final AtomicInteger completed = new AtomicInteger();
    private final AtomicInteger warning = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();
    private final AtomicInteger pendingCStoreForward = new AtomicInteger();
    private final AtomicInteger missing = new AtomicInteger();
    private final Collection<InstanceLocations> cstoreForwards =
            Collections.synchronizedCollection(new ArrayList<InstanceLocations>());
    private final Collection<String> failedSOPInstanceUIDs =
            Collections.synchronizedCollection(new ArrayList<String>());
    private final HashMap<String, Storage> storageMap = new HashMap<>();
    private ScheduledFuture<?> writePendingRSP;
    private volatile Attributes fallbackMoveRSPCommand;
    private volatile Attributes fallbackMoveRSPData;
    private volatile int fallbackMoveRSPNumberOfMatches = -1;
    private Date patientUpdatedTime;
    private boolean retryFailedRetrieve;
    private AttributeSet metadataFilter;
    private HttpServletRequestInfo httpServletRequestInfo;
    private CopyToRetrieveCacheTask copyToRetrieveCacheTask;
    private final List<UpdateLocation> updateLocations = new ArrayList<>();

    RetrieveContextImpl(RetrieveService retrieveService, ArchiveAEExtension arcAE, String localAETitle,
                        QueryRetrieveView qrView) {
        this.retrieveService = retrieveService;
        this.arcAE = arcAE;
        this.localAETitle = localAETitle;
        this.callingAET = localAETitle;
        this.qrView = qrView;
    }

    @Override
    public Association getRequestAssociation() {
        return requestAssociation;
    }

    @Override
    public void setRequestAssociation(Association requestAssociation) {
        this.requestAssociation = requestAssociation;
    }

    @Override
    public QueryRetrieveLevel2 getQueryRetrieveLevel() {
        return qrLevel;
    }

    @Override
    public void setQueryRetrieveLevel(QueryRetrieveLevel2 qrLevel) {
        this.qrLevel = qrLevel;
    }

    @Override
    public Association getStoreAssociation() {
        return storeAssociation;
    }

    @Override
    public void setStoreAssociation(Association storeAssociation) {
        this.storeAssociation = storeAssociation;
    }

    @Override
    public Association getForwardAssociation() {
        return forwardAssociation;
    }

    @Override
    public void setForwardAssociation(Association forwardAssociation) {
        this.forwardAssociation = forwardAssociation;
    }

    @Override
    public Association getFallbackAssociation() {
        return fallbackAssociation;
    }

    @Override
    public void setFallbackAssociation(Association fallbackAssociation) {
        this.fallbackAssociation = fallbackAssociation;
    }

    @Override
    public RetrieveService getRetrieveService() {
        return retrieveService;
    }

    @Override
    public ApplicationEntity getLocalApplicationEntity() {
        return arcAE.getApplicationEntity();
    }

    @Override
    public ArchiveAEExtension getArchiveAEExtension() {
        return arcAE;
    }

    @Override
    public String[] getAccessControlIDs() {
        return arcAE.getAccessControlIDs();
    }

    @Override
    public QueryRetrieveView getQueryRetrieveView() {
        return qrView;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public void setPriority(int priority) {
        this.priority = priority;
    }

    @Override
    public int getMoveOriginatorMessageID() {
        return moveOriginatorMessageID;
    }

    @Override
    public void setMoveOriginatorMessageID(int moveOriginatorMessageID) {
        this.moveOriginatorMessageID = moveOriginatorMessageID;
    }

    @Override
    public String getMoveOriginatorAETitle() {
        return moveOriginatorAETitle;
    }

    @Override
    public void setMoveOriginatorAETitle(String moveOriginatorAETitle) {
        this.moveOriginatorAETitle = moveOriginatorAETitle;
    }

    @Override
    public String getDestinationAETitle() {
        return destinationAETitle;
    }

    @Override
    public void setDestinationAETitle(String destinationAETitle) {
        this.destinationAETitle = destinationAETitle;
    }

    @Override
    public ApplicationEntity getDestinationAE() {
        return destinationAE;
    }

    @Override
    public void setDestinationAE(ApplicationEntity destinationAE) {
        this.destinationAE = destinationAE;
    }

    @Override
    public WebApplication getDestinationWebApp() {
        return destinationWebApp;
    }

    @Override
    public void setDestinationWebApp(WebApplication destinationWebApp) {
        this.destinationWebApp = destinationWebApp;
    }

    @Override
    public StorageDescriptor getDestinationStorage() {
        return destinationStorage;
    }

    @Override
    public void setDestinationStorage(StorageDescriptor destinationStorage) {
        this.destinationStorage = destinationStorage;
    }

    @Override
    public Throwable getException() {
        return exception;
    }

    @Override
    public void setException(Throwable exception) {
        this.exception = exception;
    }

    @Override
    public String getLocalAETitle() {
        return localAETitle;
    }

    @Override
    public String getCallingAET() {
        return callingAET;
    }

    @Override
    public void setCallingAET(String callingAET) {
        this.callingAET = Objects.requireNonNull(callingAET);
    }

    @Override
    public String getRequestorAET() {
        return requestAssociation != null ? requestAssociation.getRemoteAET() : null;
    }

    @Override
    public String getRequestorHostName() {
        return httpServletRequestInfo != null
                ? httpServletRequestInfo.requesterHost
                : requestAssociation != null
                    ? ReverseDNS.hostNameOf(requestAssociation.getSocket().getInetAddress())
                    : null;
    }

    @Override
    public String getDestinationHostName() {
        return destinationAE != null && !destinationAE.getConnections().isEmpty()
                ? destinationAE.getConnections().get(0).getHostname()
                : httpServletRequestInfo != null
                    ? httpServletRequestInfo.requesterHost
                    : storeAssociation != null
                        ? ReverseDNS.hostNameOf(storeAssociation.getSocket().getInetAddress())
                        : null;
    }

    @Override
    public String getLocalHostName() {
        return httpServletRequestInfo != null
                    ? httpServletRequestInfo.localHost
                    : storeAssociation != null
                        ? ReverseDNS.hostNameOf(storeAssociation.getSocket().getLocalAddress())
                        : null;
    }

    @Override
    public IDWithIssuer[] getPatientIDs() {
        return patientIDs;
    }

    @Override
    public void setPatientIDs(IDWithIssuer... patientIDs) {
        this.patientIDs = patientIDs != null ? patientIDs : IDWithIssuer.EMPTY;
    }

    @Override
    public String getStudyInstanceUID() {
        return studyInstanceUIDs.length > 0 ? studyInstanceUIDs[0] : null;
    }

    @Override
    public String[] getStudyInstanceUIDs() {
        return studyInstanceUIDs;
    }

    @Override
    public void setStudyInstanceUIDs(String... studyInstanceUIDs) {
        this.studyInstanceUIDs = studyInstanceUIDs != null ? studyInstanceUIDs : StringUtils.EMPTY_STRING;
    }

    @Override
    public String getSeriesInstanceUID() {
        return seriesInstanceUIDs.length > 0 ? seriesInstanceUIDs[0] : null;
    }

    @Override
    public String[] getSeriesInstanceUIDs() {
        return seriesInstanceUIDs;
    }

    @Override
    public void setSeriesInstanceUIDs(String... seriesInstanceUIDs) {
        this.seriesInstanceUIDs = seriesInstanceUIDs != null ? seriesInstanceUIDs : StringUtils.EMPTY_STRING;
    }

    public Series.MetadataUpdate getSeriesMetadataUpdate() {
        return metadataUpdate;
    }

    @Override
    public void setSeriesMetadataUpdate(Series.MetadataUpdate metadataUpdate) {
        this.metadataUpdate = metadataUpdate;
    }

    @Override
    public String getSopInstanceUID() {
        return sopInstanceUIDs.length > 0 ? sopInstanceUIDs[0] : null;
    }

    @Override
    public String[] getSopInstanceUIDs() {
        return sopInstanceUIDs;
    }

    @Override
    public void setSopInstanceUIDs(String... sopInstanceUIDs) {
        this.sopInstanceUIDs = sopInstanceUIDs != null ? sopInstanceUIDs : StringUtils.EMPTY_STRING;
    }

    @Override
    public Location.ObjectType getObjectType() {
        return objectType;
    }

    @Override
    public void setObjectType(Location.ObjectType objectType) {
        this.objectType = objectType;
    }

    @Override
    public List<InstanceLocations> getMatches() {
        return matches;
    }

    @Override
    public List<StudyInfo> getStudyInfos() {
        return studyInfos;
    }

    @Override
    public List<SeriesInfo> getSeriesInfos() {
        return seriesInfos;
    }

    @Override
    public int getNumberOfMatches() {
        return numberOfMatches;
    }

    @Override
    public void setNumberOfMatches(int numberOfMatches) {
        this.numberOfMatches = numberOfMatches;
    }

    @Override
    public int completed() {
        return completed.get();
    }

    @Override
    public void incrementCompleted() {
        completed.getAndIncrement();
    }

    @Override
    public void addCompleted(int delta) {
        completed.getAndAdd(delta);
    }

    @Override
    public int warning() {
        return warning.get();
    }

    @Override
    public void incrementWarning() {
        warning.getAndIncrement();
    }

    @Override
    public void addWarning(int delta) {
        warning.getAndAdd(delta);
    }

    @Override
    public int failed() {
        return failed.get()
                + (fallbackMoveRSPCommand != null
                    ? fallbackMoveRSPCommand.getInt(Tag.NumberOfFailedSuboperations, 0)
                    : 0);
    }

    @Override
    public void incrementFailed() {
        failed.getAndIncrement();
    }

    @Override
    public void addFailed(int delta) {
        failed.getAndAdd(delta);
    }

    @Override
    public void addFailedSOPInstanceUID(String iuid) {
        failedSOPInstanceUIDs.add(iuid);
    }

    @Override
    public boolean isFailedSOPInstanceUID(String iuid) {
        return failedSOPInstanceUIDs.contains(iuid);
    }

    @Override
    public String[] failedSOPInstanceUIDs() {
        String[] src;
        if (fallbackMoveRSPData == null
                || (src = fallbackMoveRSPData.getStrings(Tag.FailedSOPInstanceUIDList)) == null
                || src.length == 0)
            return failedSOPInstanceUIDs.toArray(StringUtils.EMPTY_STRING);

        int destPos = failedSOPInstanceUIDs.size();
        String[] dest = failedSOPInstanceUIDs.toArray(new String[destPos + src.length]);
        System.arraycopy(src, 0, dest, destPos, src.length);
        return dest;
    }

    @Override
    public int remaining() {
         return Math.max(0,
                 Math.max(numberOfMatches, fallbackMoveRSPNumberOfMatches) - completed() - warning() - failed());
    }

    @Override
    public int status() {
        if (fallbackMoveRSPCommand != null) {
            int status = fallbackMoveRSPCommand.getInt(Tag.Status, 0);
            if (status != Status.Success)
                return (completed() == 0 && warning() == 0)
                        ? Status.UnableToPerformSubOperations
                        : Status.OneOrMoreFailures;
        }
        return (failed() == 0)
                ? Status.Success
                : (completed() == 0 && warning() == 0)
                    ? Status.UnableToPerformSubOperations
                    : Status.OneOrMoreFailures;
    }

    @Override
    public String getOutcomeDescription() {
        return (failed() == 0 && warning() == 0)
                ? "Success"
                : (completed() == 0 && warning() == 0)
                ? "Unable to perform sup-operations"
                : (failed() == 0)
                ? "Warnings on retrieve of " + warning() + " objects"
                : "Retrieve of " + failed() + " objects failed";

    }

    @Override
    public Storage getStorage(String storageID) {
        return storageMap.get(storageID);
    }

    @Override
    public void putStorage(String storageID, Storage storage) {
        storageMap.put(storageID, storage);
    }

    @Override
    public void incrementPendingCStoreForward() {
        pendingCStoreForward.getAndIncrement();
    }

    @Override
    public void decrementPendingCStoreForward() {
        synchronized (pendingCStoreForward) {
            pendingCStoreForward.getAndDecrement();
            pendingCStoreForward.notifyAll();
        }
    }

    @Override
    public void waitForPendingCStoreForward() throws InterruptedException {
        synchronized (pendingCStoreForward) {
            while (pendingCStoreForward.get() > 0)
                pendingCStoreForward.wait();
        }
    }

    @Override
    public void incrementMissing() {
        missing.getAndIncrement();
    }

    @Override
    public int missing() {
        return missing.get();
    }

    @Override
    public void addCStoreForward(InstanceLocations inst) {
        cstoreForwards.add(inst);
    }

    @Override
    public Collection<InstanceLocations> getCStoreForwards() {
        return cstoreForwards;
    }

    @Override
    public void setWritePendingRSP(ScheduledFuture<?> writePendingRSP) {
        this.writePendingRSP = writePendingRSP;
    }

    @Override
    public void stopWritePendingRSP() {
        if (writePendingRSP != null)
            writePendingRSP.cancel(true);
    }

    @Override
    public void setFallbackMoveRSP(Attributes cmd, Attributes data) {
        if (fallbackMoveRSPCommand == null) {
            fallbackMoveRSPNumberOfMatches = fallbackMoveRSPNumberOfMatchesOf(cmd);
        }
        this.fallbackMoveRSPCommand = cmd;
        this.fallbackMoveRSPData = data;
    }

    private int fallbackMoveRSPNumberOfMatchesOf(Attributes cmd) {
        int[] a = IntStream.of(
                Tag.NumberOfRemainingSuboperations,
                Tag.NumberOfCompletedSuboperations,
                Tag.NumberOfWarningSuboperations,
                Tag.NumberOfFailedSuboperations)
                .map(tag -> cmd.getInt(tag, -1))
                .filter(n -> n >= 0)
                .toArray();

        return a.length == 0 ? -1 : Arrays.stream(a).sum();
    }

    @Override
    public Attributes getFallbackMoveRSPCommand() {
        return fallbackMoveRSPCommand;
    }

    @Override
    public Attributes getFallbackMoveRSPData() {
        return fallbackMoveRSPData;
    }

    @Override
    public int getFallbackMoveRSPNumberOfMatches() {
        return fallbackMoveRSPNumberOfMatches;
    }

    @Override
    public int getFallbackMoveRSPFailed() {
        return fallbackMoveRSPCommand != null
                ? fallbackMoveRSPCommand.getInt(Tag.NumberOfFailedSuboperations, -1)
                : -1;
    }

    @Override
    public String[] getFallbackMoveRSPFailedIUIDs() {
        return fallbackMoveRSPData != null
                ? StringUtils.maskNull(fallbackMoveRSPData.getStrings(Tag.FailedSOPInstanceUIDList))
                : StringUtils.EMPTY_STRING;
    }

    @Override
    public int getFallbackMoveRSPStatus() {
        return fallbackMoveRSPCommand != null
                ? fallbackMoveRSPCommand.getInt(Tag.Status, -1)
                : -1;
    }

    @Override
    public void close() {
        for (Storage storage : storageMap.values())
            SafeClose.close(storage);
    }

    @Override
    public boolean isRetryFailedRetrieve() {
        return retryFailedRetrieve;
    }

    @Override
    public void setRetryFailedRetrieve(boolean retryFailedRetrieve) {
        this.retryFailedRetrieve = retryFailedRetrieve;
    }

    @Override
    public Date getPatientUpdatedTime() { return patientUpdatedTime; }

    @Override
    public void setPatientUpdatedTime(Date patientUpdatedTime) {
        this.patientUpdatedTime = patientUpdatedTime;
    }

    @Override
    public AttributeSet getMetadataFilter() {
        return metadataFilter;
    }

    @Override
    public void setMetadataFilter(AttributeSet metadataFilter) {
        this.metadataFilter = metadataFilter;
    }

    @Override
    public boolean isUpdateSeriesMetadata() {
        return metadataUpdate != null;
    }

    @Override
    public boolean isConsiderPurgedInstances() {
        return arcAE != null
                && arcAE.getArchiveDeviceExtension().isPurgeInstanceRecords()
                && (qrLevel != QueryRetrieveLevel2.IMAGE || seriesInstanceUIDs.length != 0);
    }

    @Override
    public boolean isRetrieveMetadata() {
        return objectType == null;
    }

    @Override
    public HttpServletRequestInfo getHttpServletRequestInfo() {
        return httpServletRequestInfo;
    }

    @Override
    public void setHttpServletRequestInfo(HttpServletRequestInfo httpServletRequestInfo) {
        this.httpServletRequestInfo = httpServletRequestInfo;
    }

    @Override
    public boolean copyToRetrieveCache(InstanceLocations match) {
        if (match == null) {
            if (copyToRetrieveCacheTask != null)
                copyToRetrieveCacheTask.schedule(null);
            return false;
        }
        ArchiveDeviceExtension arcdev = retrieveService.getArchiveDeviceExtension();
        if (match.getLocations().stream().anyMatch(location ->
                arcdev.getStorageDescriptorNotNull(location.getStorageID())
                        .getRetrieveCacheStorageID() == null))
            return false;

        return copyToRetrieveCacheTask(match).schedule(match);

    }

    private CopyToRetrieveCacheTask copyToRetrieveCacheTask(InstanceLocations match) {
        CopyToRetrieveCacheTask task = copyToRetrieveCacheTask;
        if (task == null) {
            retrieveService.getDevice().execute(task = new CopyToRetrieveCacheTask(this, match));
            copyToRetrieveCacheTask = task;
        }
        return task;
    }

    @Override
    public InstanceLocations copiedToRetrieveCache() {
        return copyToRetrieveCacheTask != null ? copyToRetrieveCacheTask.copiedToRetrieveCache() : null;
    }

    @Override
    public List<UpdateLocation> getUpdateLocations() {
        return updateLocations;
    }

    @Override
    public boolean isUpdateLocationStatusOnRetrieve() {
        return arcAE.updateLocationStatusOnRetrieve();
    }

    @Override
    public boolean isStorageVerificationOnRetrieve() {
        return arcAE.storageVerificationOnRetrieve();
    }

    @Override
    public void decrementNumberOfMatches() {
        numberOfMatches--;
    }
}
