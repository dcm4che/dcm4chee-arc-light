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

import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Priority;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4che3.util.SafeClose;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.MetadataFilter;
import org.dcm4chee.arc.conf.QueryRetrieveView;
import org.dcm4chee.arc.entity.CodeEntity;
import org.dcm4chee.arc.entity.Location;
import org.dcm4chee.arc.entity.Series;
import org.dcm4chee.arc.retrieve.*;
import org.dcm4chee.arc.storage.Storage;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

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
    private HttpServletRequest httpRequest;
    private final RetrieveService retrieveService;
    private final ArchiveAEExtension arcAE;
    private final String localAETitle;
    private final QueryRetrieveView qrView;
    private QueryRetrieveLevel2 qrLevel;
    private int priority = Priority.NORMAL;
    private int moveOriginatorMessageID;
    private String moveOriginatorAETitle;
    private String destinationAETitle;
    private ApplicationEntity destinationAE;
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
    private final Collection<InstanceLocations> cstoreForwards =
            Collections.synchronizedCollection(new ArrayList<InstanceLocations>());
    private final Collection<String> failedSOPInstanceUIDs =
            Collections.synchronizedCollection(new ArrayList<String>());
    private final HashMap<String, Storage> storageMap = new HashMap<>();
    private CodeEntity[] showInstancesRejectedByCode = {};
    private CodeEntity[] hideRejectionNotesWithCode = {};
    private ScheduledFuture<?> writePendingRSP;
    private volatile int fallbackMoveRSPNumberOfMatches;
    private volatile int fallbackMoveRSPFailed;
    private volatile String[] fallbackMoveRSPFailedIUIDs = {};
    private Date patientUpdatedTime;
    private boolean retryFailedRetrieve;
    private MetadataFilter metadataFilter;

    RetrieveContextImpl(RetrieveService retrieveService, ArchiveAEExtension arcAE, String localAETitle,
                        QueryRetrieveView qrView) {
        this.retrieveService = retrieveService;
        this.arcAE = arcAE;
        this.localAETitle = localAETitle;
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
    public HttpServletRequest getHttpRequest() {
        return httpRequest;
    }

    @Override
    public void setHttpRequest(HttpServletRequest httpRequest) {
        this.httpRequest = httpRequest;
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
    public boolean isHideNotRejectedInstances() {
        return qrView.isHideNotRejectedInstances();
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
    public String getRequestorAET() {
        return requestAssociation != null ? requestAssociation.getRemoteAET() : null;
    }

    @Override
    public String getRequestorHostName() {
        return httpRequest != null
                ? httpRequest.getRemoteHost()
                : requestAssociation != null
                    ? requestAssociation.getSocket().getInetAddress().getHostName()
                    : null;
    }

    @Override
    public String getDestinationHostName() {
        return httpRequest != null
                ? httpRequest.getRemoteHost()
                : storeAssociation != null
                    ? storeAssociation.getSocket().getInetAddress().getHostName()
                    : destinationAE != null && !destinationAE.getConnections().isEmpty()
                        ? destinationAE.getConnections().get(0).getHostname()
                        : null;
    }

    @Override
    public boolean isDestinationRequestor() {
        return httpRequest != null || requestAssociation == storeAssociation;
    }

    @Override
    public boolean isLocalRequestor() {
        return httpRequest == null && requestAssociation == null;
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
        return failed.get() + fallbackMoveRSPFailed;
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
    public String[] failedSOPInstanceUIDs() {
        return failedSOPInstanceUIDs.toArray(StringUtils.EMPTY_STRING);
    }

    @Override
    public int remaining() {
         return Math.max(0,
                 Math.max(numberOfMatches, fallbackMoveRSPNumberOfMatches) - completed() - warning() - failed());
    }

    @Override
    public int status() {
        return (failed() == 0 && warning() == 0)
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
    public CodeEntity[] getShowInstancesRejectedByCode() {
        return showInstancesRejectedByCode;
    }

    @Override
    public void setShowInstancesRejectedByCode(CodeEntity[] showInstancesRejectedByCode) {
        this.showInstancesRejectedByCode = showInstancesRejectedByCode;
    }

    @Override
    public CodeEntity[] getHideRejectionNotesWithCode() {
        return hideRejectionNotesWithCode;
    }

    @Override
    public void setHideRejectionNotesWithCode(CodeEntity[] hideRejectionNotesWithCode) {
        this.hideRejectionNotesWithCode = hideRejectionNotesWithCode;
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
    public int getFallbackMoveRSPNumberOfMatches() {
        return fallbackMoveRSPNumberOfMatches;
    }

    @Override
    public void setFallbackMoveRSPNumberOfMatches(int fallbackMoveRSPNumberOfMatches) {
        this.fallbackMoveRSPNumberOfMatches = fallbackMoveRSPNumberOfMatches;
    }

    @Override
    public int getFallbackMoveRSPFailed() {
        return fallbackMoveRSPFailed;
    }

    @Override
    public void setFallbackMoveRSPFailed(int fallbackMoveRSPFailed) {
        this.fallbackMoveRSPFailed = fallbackMoveRSPFailed;
    }

    @Override
    public String[] getFallbackMoveRSPFailedIUIDs() {
        return fallbackMoveRSPFailedIUIDs;
    }

    @Override
    public void setFallbackMoveRSPFailedIUIDs(String[] fallbackMoveRSPFailedIUIDs) {
        this.fallbackMoveRSPFailedIUIDs = fallbackMoveRSPFailedIUIDs;
    }

    @Override
    public void close() throws IOException {
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
    public MetadataFilter getMetadataFilter() {
        return metadataFilter;
    }

    @Override
    public void setMetadataFilter(MetadataFilter metadataFilter) {
        this.metadataFilter = metadataFilter;
    }

    @Override
    public boolean isUpdateSeriesMetadata() {
        return metadataUpdate != null;
    }

    @Override
    public boolean isConsiderPurgedInstances() {
        return arcAE != null
                && arcAE.getArchiveDeviceExtension().getPurgeInstanceRecordsPollingInterval() != null
                && (qrLevel != QueryRetrieveLevel2.IMAGE || seriesInstanceUIDs.length != 0);
    }

    @Override
    public boolean isRetrieveMetadata() {
        return objectType == null;
    }
}
