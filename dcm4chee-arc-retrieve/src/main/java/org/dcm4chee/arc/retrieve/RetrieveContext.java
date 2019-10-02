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

package org.dcm4chee.arc.retrieve;

import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.AttributeSet;
import org.dcm4chee.arc.conf.QueryRetrieveView;
import org.dcm4chee.arc.conf.StorageDescriptor;
import org.dcm4chee.arc.entity.Location;
import org.dcm4chee.arc.entity.Series;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.storage.Storage;
import org.dcm4chee.arc.store.InstanceLocations;
import org.dcm4chee.arc.store.UpdateLocation;

import java.io.Closeable;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Aug 2015
 */
public interface RetrieveContext extends Closeable {
    Association getRequestAssociation();

    void setRequestAssociation(Association requestAssociation);

    QueryRetrieveLevel2 getQueryRetrieveLevel();

    void setQueryRetrieveLevel(QueryRetrieveLevel2 qrLevel);

    Association getStoreAssociation();

    void setStoreAssociation(Association storeAssociation);

    Association getForwardAssociation();

    void setForwardAssociation(Association fwdas);

    Association getFallbackAssociation();

    void setFallbackAssociation(Association fallbackAssociation);

    RetrieveService getRetrieveService();

    ApplicationEntity getLocalApplicationEntity();

    ArchiveAEExtension getArchiveAEExtension();

    String[] getAccessControlIDs();

    QueryRetrieveView getQueryRetrieveView();

    int getPriority();

    void setPriority(int priority);

    int getMoveOriginatorMessageID();

    void setMoveOriginatorMessageID(int moveOriginatorMessageID);

    String getMoveOriginatorAETitle();

    void setMoveOriginatorAETitle(String moveOriginatorAETitle);

    String getDestinationAETitle();

    void setDestinationAETitle(String destinationAETitle);

    ApplicationEntity getDestinationAE();

    void setDestinationAE(ApplicationEntity remoteAE);

    StorageDescriptor getDestinationStorage();

    void setDestinationStorage(StorageDescriptor storageDescriptor);

    Throwable getException();

    void setException(Throwable exception);

    String getLocalAETitle();

    String getRequestorAET();

    String getRequestorHostName();

    String getDestinationHostName();

    IDWithIssuer[] getPatientIDs();

    void setPatientIDs(IDWithIssuer... patientIDs);

    String getStudyInstanceUID();

    String[] getStudyInstanceUIDs();

    void setStudyInstanceUIDs(String... studyInstanceUIDs);

    String getSeriesInstanceUID();

    String[] getSeriesInstanceUIDs();

    void setSeriesInstanceUIDs(String... seriesInstanceUIDs);

    Series.MetadataUpdate getSeriesMetadataUpdate();

    void setSeriesMetadataUpdate(Series.MetadataUpdate metadataUpdate);

    String getSopInstanceUID();

    String[] getSopInstanceUIDs();

    void setSopInstanceUIDs(String... sopInstanceUIDs);

    Location.ObjectType getObjectType();

    void setObjectType(Location.ObjectType objectType);

    List<InstanceLocations> getMatches();

    List<StudyInfo> getStudyInfos();

    List<SeriesInfo> getSeriesInfos();

    int getNumberOfMatches();

    void setNumberOfMatches(int numberOfMatches);

    int completed();

    void incrementCompleted();

    void addCompleted(int delta);

    int warning();

    void incrementWarning();

    void addWarning(int delta);

    int failed();

    void incrementFailed();

    void addFailed(int delta);

    void addFailedSOPInstanceUID(String iuid);

    String[] failedSOPInstanceUIDs();

    int remaining();

    int status();

    String getOutcomeDescription();

    Storage getStorage(String storageID);

    void putStorage(String storageID, Storage storage);

    void incrementPendingCStoreForward();

    void decrementPendingCStoreForward();

    void waitForPendingCStoreForward() throws InterruptedException;

    void incrementMissing();

    int missing();

    void addCStoreForward(InstanceLocations inst);

    Collection<InstanceLocations> getCStoreForwards();

    void setWritePendingRSP(ScheduledFuture<?> scheduledFuture);

    void stopWritePendingRSP();

    int getFallbackMoveRSPNumberOfMatches();

    void setFallbackMoveRSPNumberOfMatches(int fallbackMoveRSPNumberOfMatches);

    int getFallbackMoveRSPFailed();

    void setFallbackMoveRSPFailed(int fallbackMoveRSPFailed);

    String[] getFallbackMoveRSPFailedIUIDs();

    void setFallbackMoveRSPFailedIUIDs(String[] fallbackMoveRSPFailedIUIDs);

    boolean isRetryFailedRetrieve();

    void setRetryFailedRetrieve(boolean retryFailedRetrieve);

    Date getPatientUpdatedTime();

    void setPatientUpdatedTime(Date patientUpdatedTime);

    AttributeSet getMetadataFilter();

    void setMetadataFilter(AttributeSet metadataFilter);

    boolean isUpdateSeriesMetadata();

    boolean isConsiderPurgedInstances();

    boolean isRetrieveMetadata();

    HttpServletRequestInfo getHttpServletRequestInfo();

    void setHttpServletRequestInfo(HttpServletRequestInfo httpServletRequestInfo);

    boolean copyToRetrieveCache(InstanceLocations match);

    InstanceLocations copiedToRetrieveCache();

    List<UpdateLocation> getUpdateLocations();

    boolean isUpdateLocationStatusOnRetrieve();

    boolean isStorageVerificationOnRetrieve();

    void decrementNumberOfMatches();
}
