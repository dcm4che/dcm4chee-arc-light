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
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.QueryRetrieveView;
import org.dcm4chee.arc.entity.CodeEntity;
import org.dcm4chee.arc.retrieve.*;
import org.dcm4chee.arc.storage.Storage;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2015
 */
class RetrieveContextImpl implements RetrieveContext {
    private Association requestAssociation;
    private Association storeAssociation;
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
    private String[] sopInstanceUIDs = {};
    private int numberOfMatches;
    private final Collection<InstanceLocations> matches = new ArrayList<>();
    private final Collection<StudyInfo> studyInfos = new ArrayList<>();
    private final Collection<SeriesInfo> seriesInfos = new ArrayList<>();
    private final AtomicInteger completed = new AtomicInteger();
    private final AtomicInteger warning = new AtomicInteger();
    private final AtomicInteger pendingCStoreForward = new AtomicInteger();
    private final Collection<String> failedSOPInstanceUIDs =
            Collections.synchronizedCollection(new ArrayList<String>());
    private final HashMap<String, Storage> storageMap = new HashMap<>();
    private CodeEntity[] showInstancesRejectedByCode = {};
    private CodeEntity[] hideRejectionNotesWithCode = {};


    RetrieveContextImpl(RetrieveService retrieveService, ArchiveAEExtension arcAE, String localAETitle) {
        this.retrieveService = retrieveService;
        this.arcAE = arcAE;
        this.localAETitle = localAETitle;
        this.qrView = arcAE.getQueryRetrieveView();
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

    @Override
    public String[] getSopInstanceUIDs() {
        return sopInstanceUIDs;
    }

    @Override
    public void setSopInstanceUIDs(String... sopInstanceUIDs) {
        this.sopInstanceUIDs = sopInstanceUIDs != null ? sopInstanceUIDs : StringUtils.EMPTY_STRING;
    }

    @Override
    public Collection<InstanceLocations> getMatches() {
        return matches;
    }

    @Override
    public Collection<StudyInfo> getStudyInfos() {
        return studyInfos;
    }

    @Override
    public Collection<SeriesInfo> getSeriesInfos() {
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
    public void incrementNumberOfMatches(int inc) {
        numberOfMatches += inc;
    }

    @Override
    public int completed() {
        return completed.get();
    }

    @Override
    public void incrementCompleted() {
        completed.incrementAndGet();
    }

    @Override
    public int warning() {
        return warning.get();
    }

    @Override
    public void incrementWarning() {
        warning.incrementAndGet();
    }

    @Override
    public int failed() {
        return failedSOPInstanceUIDs.size();
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
        return numberOfMatches - completed() - warning() - failed();
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
        pendingCStoreForward.incrementAndGet();
    }

    @Override
    public void decrementPendingCStoreForward() {
        synchronized (pendingCStoreForward) {
            pendingCStoreForward.decrementAndGet();
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
    public void addMatch(InstanceLocations inst) {
        synchronized (matches) {
            matches.add(inst);
        }
    }

    @Override
    public void close() throws IOException {
        for (Storage storage : storageMap.values())
            storage.close();
    }
}
