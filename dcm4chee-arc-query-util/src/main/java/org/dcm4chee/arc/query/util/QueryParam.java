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

package org.dcm4chee.arc.query.util;

import org.dcm4che3.data.Issuer;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.soundex.FuzzyStr;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.entity.ExpirationState;
import org.dcm4chee.arc.entity.Patient;

import java.util.List;


/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Aug 2015
 */
public class QueryParam {
    private final ArchiveAEExtension arcAE;
    private final ArchiveDeviceExtension arcDev;
    private final QueryRetrieveView qrView;
    private boolean combinedDatetimeMatching;
    private boolean fuzzySemanticMatching;
    private boolean allOfModalitiesInStudy;
    private boolean withoutStudies = true;
    private boolean incomplete;
    private boolean retrieveFailed;
    private boolean storageVerificationFailed;
    private boolean metadataUpdateFailed;
    private boolean compressionfailed;
    private String externalRetrieveAET;
    private String externalRetrieveAETNot;
    private String subscriberAET;
    private String notSubscribedByAET;
    private Patient.VerificationStatus patientVerificationStatus;
    private ExpirationState[] expirationState;
    private String expirationDate;
    private List<String> studyStorageIDs;
    private long minStudySize;
    private long maxStudySize;

    public QueryParam(ApplicationEntity ae) {
        this.arcAE = ae.getAEExtensionNotNull(ArchiveAEExtension.class);
        this.arcDev = arcAE.getArchiveDeviceExtension();
        this.qrView = arcAE.getQueryRetrieveView();
    }

    public String getAETitle() {
        return arcAE.getApplicationEntity().getAETitle();
    }

    public String[] getAccessControlIDs() {
        return arcAE.getAccessControlIDs();
    }

    public SPSStatus[] getHideSPSWithStatusFromMWL() {
        return arcAE.hideSPSWithStatusFromMWL();
    }

    public FuzzyStr getFuzzyStr() {
        return arcDev.getFuzzyStr();
    }

    public boolean isPersonNameComponentOrderInsensitiveMatching() {
        return arcAE.personNameComponentOrderInsensitiveMatching();
    }

    public boolean isHideNotRejectedInstances() {
        return qrView.isHideNotRejectedInstances();
    }

    public AttributeFilter getAttributeFilter(Entity entity) {
        return arcDev.getAttributeFilter(entity);
    }

    public String getViewID() {
        return qrView.getViewID();
    }

    public QueryRetrieveView getQueryRetrieveView() {
        return qrView;
    }

    public Issuer getDefaultIssuerOfAccessionNumber() {
        return null;
    }

    public boolean isCombinedDatetimeMatching() {
        return combinedDatetimeMatching;
    }

    public void setCombinedDatetimeMatching(boolean combinedDatetimeMatching) {
        this.combinedDatetimeMatching = combinedDatetimeMatching;
    }

    public boolean isFuzzySemanticMatching() {
        return fuzzySemanticMatching;
    }

    public void setFuzzySemanticMatching(boolean fuzzySemanticMatching) {
        this.fuzzySemanticMatching = fuzzySemanticMatching;
    }

    public boolean isAllOfModalitiesInStudy() {
        return allOfModalitiesInStudy;
    }

    public void setAllOfModalitiesInStudy(boolean allOfModalitiesInStudy) {
        this.allOfModalitiesInStudy = allOfModalitiesInStudy;
    }

    public boolean isWithoutStudies() {
        return withoutStudies;
    }

    public void setWithoutStudies(boolean withoutStudies) {
        this.withoutStudies = withoutStudies;
    }

    public boolean isIncomplete() {
        return incomplete;
    }

    public void setIncomplete(boolean incomplete) {
        this.incomplete = incomplete;
    }

    public boolean isRetrieveFailed() {
        return retrieveFailed;
    }

    public void setRetrieveFailed(boolean retrieveFailed) {
        this.retrieveFailed = retrieveFailed;
    }

    public boolean isStorageVerificationFailed() {
        return storageVerificationFailed;
    }

    public void setStorageVerificationFailed(boolean storageVerificationFailed) {
        this.storageVerificationFailed = storageVerificationFailed;
    }

    public boolean isMetadataUpdateFailed() {
        return metadataUpdateFailed;
    }

    public void setMetadataUpdateFailed(boolean metadataUpdateFailed) {
        this.metadataUpdateFailed = metadataUpdateFailed;
    }

    public boolean isCompressionFailed() {
        return compressionfailed;
    }

    public void setCompressionFailed(boolean compressionfailed) {
        this.compressionfailed = compressionfailed;
    }

    public String getExternalRetrieveAET() {
        return externalRetrieveAET;
    }

    public void setExternalRetrieveAET(String externalRetrieveAET) {
        this.externalRetrieveAET = externalRetrieveAET;
    }

    public String getExternalRetrieveAETNot() {
        return externalRetrieveAETNot;
    }

    public void setExternalRetrieveAETNot(String externalRetrieveAETNot) {
        this.externalRetrieveAETNot = externalRetrieveAETNot;
    }

    public String getNotSubscribedByAET() {
        return notSubscribedByAET;
    }

    public void setNotSubscribedByAET(String notSubscribedByAET) {
        this.notSubscribedByAET = notSubscribedByAET;
    }

    public Patient.VerificationStatus getPatientVerificationStatus() {
        return patientVerificationStatus;
    }

    public void setPatientVerificationStatus(Patient.VerificationStatus patientVerificationStatus) {
        this.patientVerificationStatus = patientVerificationStatus;
    }

    public String getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(String expirationDate) {
        this.expirationDate = expirationDate;
    }

    public List<String> getStudyStorageIDs() {
        return studyStorageIDs;
    }

    public void setStudyStorageIDs(List<String> studyStorageIDs) {
        this.studyStorageIDs = studyStorageIDs;
    }

    public boolean noMatches() {
        return studyStorageIDs != null && studyStorageIDs.isEmpty();
    }

    public long getMinStudySize() {
        return minStudySize;
    }

    public long getMaxStudySize() {
        return maxStudySize;
    }

    public void setStudySizeRange(String studySizeInKB) {
        if (studySizeInKB == null || studySizeInKB.isEmpty())
            return;

        int delim = studySizeInKB.indexOf('-');
        if (delim == -1) {
            long size = Long.parseLong(studySizeInKB) * 1000;
            minStudySize = size;
            maxStudySize = size + 999;
        } else {
            if (delim > 0)
                minStudySize =  Long.parseLong(studySizeInKB.substring(0, delim)) * 1000;
            if (delim < studySizeInKB.length() - 1)
                maxStudySize =  (Long.parseLong(studySizeInKB.substring(delim+1)) * 1000) + 999;
        }
    }

    public ExpirationState[] getExpirationState() {
        return expirationState;
    }

    public void setExpirationState(ExpirationState... expirationState) {
        this.expirationState = expirationState;
    }
}
