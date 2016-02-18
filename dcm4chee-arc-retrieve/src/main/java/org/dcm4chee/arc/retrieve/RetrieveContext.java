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

package org.dcm4chee.arc.retrieve;

import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.QueryRetrieveView;
import org.dcm4chee.arc.entity.CodeEntity;
import org.dcm4chee.arc.storage.Storage;

import javax.servlet.http.HttpServletRequest;
import java.io.Closeable;
import java.util.Collection;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Aug 2015
 */
public interface RetrieveContext extends Closeable {
    Association getRequestAssociation();

    void setRequestAssociation(Association requestAssociation);

    Association getStoreAssociation();

    void setStoreAssociation(Association storeAssociation);

    HttpServletRequest getHttpRequest();

    void setHttpRequest(HttpServletRequest httpRequest);

    RetrieveService getRetrieveService();

    ApplicationEntity getLocalApplicationEntity();

    ArchiveAEExtension getArchiveAEExtension();

    String[] getAccessControlIDs();

    QueryRetrieveView getQueryRetrieveView();

    boolean isHideNotRejectedInstances();

    int getPriority();

    void setPriority(int priority);

    int getMoveOriginatorMessageID();

    void setMoveOriginatorMessageID(int moveOriginatorMessageID);

    String getMoveOriginatorAETitle();

    void setMoveOriginatorAETitle(String moveOriginatorAETitle);

    String getDestinationAETitle();

    void setDestinationAETitle(String destinationAETitle);

    String getLocalAETitle();

    String getRequestorAET();

    String getRequestorHostName();

    String getDestinationHostName();

    boolean isDestinationRequestor();

    boolean isLocalRequestor();

    IDWithIssuer[] getPatientIDs();

    void setPatientIDs(IDWithIssuer... patientIDs);

    String[] getStudyInstanceUIDs();

    void setStudyInstanceUIDs(String... studyInstanceUIDs);

    String[] getSeriesInstanceUIDs();

    void setSeriesInstanceUIDs(String... seriesInstanceUIDs);

    String[] getSopInstanceUIDs();

    void setSopInstanceUIDs(String... sopInstanceUIDs);

    Collection<InstanceLocations> getMatches();

    int getNumberOfMatches();

    void setNumberOfMatches(int numberOfMatches);

    int completed();

    void incrementCompleted();

    int warning();

    void incrementWarning();

    int failed();

    void addFailedSOPInstanceUID(String iuid);

    String[] failedSOPInstanceUIDs();

    int remaining();

    int status();

    Storage getStorage(String storageID);

    void putStorage(String storageID, Storage storage);

    CodeEntity[] getShowInstancesRejectedByCode();

    void setShowInstancesRejectedByCode(CodeEntity[] showInstancesRejectedByCode);

    CodeEntity[] getHideRejectionNotesWithCode();

    void setHideRejectionNotesWithCode(CodeEntity[] hideRejectionNotesWithCode);
}
