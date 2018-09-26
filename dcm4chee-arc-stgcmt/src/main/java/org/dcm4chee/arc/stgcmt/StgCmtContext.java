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
 * Portions created by the Initial Developer are Copyright (C) 2013
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

package org.dcm4chee.arc.stgcmt;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.StorageVerificationPolicy;
import org.dcm4chee.arc.qmgt.HttpServletRequestInfo;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since March 2017
 */
public class StgCmtContext {
    private final ArchiveAEExtension arcAE;
    private final String localAET;
    private final Attributes eventInfo = new Attributes(4);
    private ApplicationEntity remoteAE;
    private HttpServletRequestInfo request;
    private StorageVerificationPolicy storageVerificationPolicy;
    private boolean stgCmtUpdateLocationStatus;
    private String[] stgCmtStorageIDs;
    private Throwable exception;

    public StgCmtContext(ApplicationEntity localAE, String localAET) {
        this.arcAE = localAE.getAEExtensionNotNull(ArchiveAEExtension.class);
        this.localAET = localAET;
        this.storageVerificationPolicy = arcAE.storageVerificationPolicy();
        this.stgCmtUpdateLocationStatus = arcAE.stgCmtUpdateLocationStatus();
        this.stgCmtStorageIDs = arcAE.stgCmtStorageIDs();
    }

    public String getLocalAET() {
        return localAET;
    }

    public ArchiveAEExtension getArchiveAEExtension() {
        return arcAE;
    }

    public ApplicationEntity getRemoteAE() {
        return remoteAE;
    }

    public StgCmtContext setRemoteAE(ApplicationEntity remoteAE) {
        this.remoteAE = remoteAE;
        return this;
    }

    public HttpServletRequestInfo getRequest() {
        return request;
    }

    public StgCmtContext setRequest(HttpServletRequestInfo request) {
        this.request = request;
        return this;
    }

    public StgCmtContext setTransactionUID(String transactionUID) {
        eventInfo.setString(Tag.TransactionUID, VR.UI, transactionUID);
        return this;
    }

    public Throwable getException() {
        return exception;
    }

    public void setException(Throwable exception) {
        this.exception = exception;
    }

    public Attributes getEventInfo() {
        return eventInfo;
    }

    public StorageVerificationPolicy getStorageVerificationPolicy() {
        return storageVerificationPolicy;
    }

    public void setStorageVerificationPolicy(StorageVerificationPolicy storageVerificationPolicy) {
        this.storageVerificationPolicy = storageVerificationPolicy;
    }

    public boolean isStgCmtUpdateLocationStatus() {
        return stgCmtUpdateLocationStatus;
    }

    public void setStgCmtUpdateLocationStatus(boolean stgCmtUpdateLocationStatus) {
        this.stgCmtUpdateLocationStatus = stgCmtUpdateLocationStatus;
    }

    public String[] getStgCmtStorageIDs() {
        return stgCmtStorageIDs;
    }

    public void setStgCmtStorageIDs(String... stgCmtStorageIDs) {
        this.stgCmtStorageIDs = stgCmtStorageIDs;
    }

    public boolean isStgCmtStorageID(String storageID) {
        return stgCmtStorageIDs.length == 0 || StringUtils.contains(stgCmtStorageIDs, storageID);
    }

}
