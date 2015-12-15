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
import org.dcm4che3.net.Priority;
import org.dcm4che3.net.Status;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.QueryRetrieveView;
import org.dcm4chee.arc.entity.CodeEntity;
import org.dcm4chee.arc.retrieve.InstanceLocations;
import org.dcm4chee.arc.retrieve.RetrieveContext;
import org.dcm4chee.arc.retrieve.RetrieveService;
import org.dcm4chee.arc.storage.Storage;

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
public class RetrieveContextImpl implements RetrieveContext {
    private final RetrieveService retrieveService;
    private final ApplicationEntity ae;
    private final QueryRetrieveView qrView;
    private int priority = Priority.NORMAL;
    private int moveOriginatorMessageID;
    private String moveOriginatorAETitle;
    private String destinationAETitle;
    private IDWithIssuer[] patientIDs = {};
    private String[] studyInstanceUIDs = {};
    private String[] seriesInstanceUIDs = {};
    private String[] sopInstanceUIDs = {};
    private int numberOfMatches;
    private final Collection<InstanceLocations> matches = new ArrayList<>();
    private final AtomicInteger completed = new AtomicInteger();
    private final AtomicInteger warning = new AtomicInteger();
    private final Collection<String> failedSOPInstanceUIDs =
            Collections.synchronizedCollection(new ArrayList<String>());
    private final HashMap<String, Storage> storageMap = new HashMap<>();
    private CodeEntity[] showInstancesRejectedByCode = {};
    private CodeEntity[] hideRejectionNotesWithCode = {};


    public RetrieveContextImpl(RetrieveService retrieveService, ApplicationEntity ae) {
        this.retrieveService = retrieveService;
        this.ae = ae;
        this.qrView = getArchiveAEExtension().getQueryRetrieveView();
    }

    @Override
    public RetrieveService getRetrieveService() {
        return retrieveService;
    }

    @Override
    public ApplicationEntity getLocalApplicationEntity() {
        return ae;
    }

    @Override
    public ArchiveAEExtension getArchiveAEExtension() {
        return ae.getAEExtension(ArchiveAEExtension.class);
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
    public IDWithIssuer[] getPatientIDs() {
        return patientIDs;
    }

    @Override
    public void setPatientIDs(IDWithIssuer... patientIDs) {
        this.patientIDs = patientIDs != null ? patientIDs : IDWithIssuer.EMPTY;
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
    public void close() throws IOException {
        for (Storage storage : storageMap.values())
            storage.close();
    }
}
