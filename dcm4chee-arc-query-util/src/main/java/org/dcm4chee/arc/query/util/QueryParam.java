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

package org.dcm4chee.arc.query.util;

import org.dcm4che3.data.Issuer;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.soundex.FuzzyStr;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.entity.CodeEntity;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Aug 2015
 */
public class QueryParam {
    private final ArchiveDeviceExtension arcDev;
    private final ArchiveAEExtension arcAE;
    private final boolean combinedDatetimeMatching;
    private final boolean fuzzySemanticMatching;
    private final boolean returnEmpty;
    private final boolean expired;
    private final boolean expiredSeries;
    private final QueryRetrieveView qrView;
    private CodeEntity[] showInstancesRejectedByCode = {};
    private CodeEntity[] hideRejectionNotesWithCode = {};


    public QueryParam(ApplicationEntity ae, boolean combinedDatetimeMatching, boolean fuzzySemanticMatching,
                      boolean returnEmpty, boolean expired, boolean expiredSeries) {
        this.arcAE = ae.getAEExtension(ArchiveAEExtension.class);
        this.arcDev = arcAE.getArchiveDeviceExtension();
        this.qrView = arcAE.getQueryRetrieveView();
        this.combinedDatetimeMatching = combinedDatetimeMatching;
        this.fuzzySemanticMatching = fuzzySemanticMatching;
        this.returnEmpty = returnEmpty;
        this.expired = expired;
        this.expiredSeries = expiredSeries;
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

    public boolean isFuzzySemanticMatching() {
        return fuzzySemanticMatching;
    }

    public boolean isReturnEmpty() {
        return returnEmpty;
    }

    public boolean isExpired() {
        return expired;
    }

    public boolean isExpiredSeries() {
        return expiredSeries;
    }

    public FuzzyStr getFuzzyStr() {
        return arcDev.getFuzzyStr();
    }

    public boolean isPersonNameComponentOrderInsensitiveMatching() {
        return arcAE.personNameComponentOrderInsensitiveMatching();
    }

    public CodeEntity[] getShowInstancesRejectedByCode() {
        return showInstancesRejectedByCode;
    }

    public void setShowInstancesRejectedByCode(CodeEntity[] showInstancesRejectedByCode) {
        this.showInstancesRejectedByCode = showInstancesRejectedByCode;
    }

    public CodeEntity[] getHideRejectionNotesWithCode() {
        return hideRejectionNotesWithCode;
    }

    public void setHideRejectionNotesWithCode(CodeEntity[] hideRejectionNotesWithCode) {
        this.hideRejectionNotesWithCode = hideRejectionNotesWithCode;
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

    public boolean isCombinedDatetimeMatching() {
        return combinedDatetimeMatching;
    }

    public Issuer getDefaultIssuerOfAccessionNumber() {
        return null;
    }

}
