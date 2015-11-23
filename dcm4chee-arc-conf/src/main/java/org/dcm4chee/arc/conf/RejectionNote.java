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

package org.dcm4chee.arc.conf;

import org.dcm4che3.data.Code;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Nov 2015
 */
public class RejectionNote {
    public enum AcceptPreviousRejectedInstance { REJECT, RESTORE, IGNORE }
    private final String rejectionNoteID;
    private Code rejectionNoteCode;
    private boolean revokeRejection;
    private AcceptPreviousRejectedInstance acceptPreviousRejectedInstance;
    private Code[] overwritePreviousRejection = {};
    private Duration deleteRejectedInstanceDelay;
    private Duration deleteRejectionNoteDelay;
    private Duration deletionPollingInterval;
    private int deletionTaskSize = 100;

    public RejectionNote(String rejectionNoteID) {
        this.rejectionNoteID = rejectionNoteID;
    }

    public String getRejectionNoteID() {
        return rejectionNoteID;
    }

    public Code getRejectionNoteCode() {
        return rejectionNoteCode;
    }

    public void setRejectionNoteCode(Code rejectionNoteCode) {
        this.rejectionNoteCode = rejectionNoteCode;
    }

    public boolean isRevokeRejection() {
        return revokeRejection;
    }

    public void setRevokeRejection(boolean revokeRejection) {
        this.revokeRejection = revokeRejection;
    }

    public AcceptPreviousRejectedInstance getAcceptPreviousRejectedInstance() {
        return acceptPreviousRejectedInstance;
    }

    public void setAcceptPreviousRejectedInstance(AcceptPreviousRejectedInstance acceptPreviousRejectedInstance) {
        this.acceptPreviousRejectedInstance = acceptPreviousRejectedInstance;
    }

    public Code[] getOverwritePreviousRejection() {
        return overwritePreviousRejection;
    }

    public boolean canOverwritePreviousRejection(Code code) {
        for (Code code1 : overwritePreviousRejection)
            if (code1.equalsIgnoreMeaning(code))
                return true;
        return false;
    }

    public void setOverwritePreviousRejection(Code[] overwritePreviousRejection) {
        this.overwritePreviousRejection = overwritePreviousRejection;
    }

    public Duration getDeleteRejectedInstanceDelay() {
        return deleteRejectedInstanceDelay;
    }

    public void setDeleteRejectedInstanceDelay(Duration deleteRejectedInstanceDelay) {
        this.deleteRejectedInstanceDelay = deleteRejectedInstanceDelay;
    }

    public Duration getDeleteRejectionNoteDelay() {
        return deleteRejectionNoteDelay;
    }

    public void setDeleteRejectionNoteDelay(Duration deleteRejectionNoteDelay) {
        this.deleteRejectionNoteDelay = deleteRejectionNoteDelay;
    }

    public Duration getDeletionPollingInterval() {
        return deletionPollingInterval;
    }

    public void setDeletionPollingInterval(Duration deletionPollingInterval) {
        this.deletionPollingInterval = deletionPollingInterval;
    }

    public int getDeletionTaskSize() {
        return deletionTaskSize;
    }

    public void setDeletionTaskSize(int deletionTaskSize) {
        this.deletionTaskSize = deletionTaskSize;
    }
}
