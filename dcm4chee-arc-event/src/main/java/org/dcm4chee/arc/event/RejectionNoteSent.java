/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 *  The contents of this file are subject to the Mozilla Public License Version
 *  1.1 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  http://www.mozilla.org/MPL/
 *
 *  Software distributed under the License is distributed on an "AS IS" basis,
 *  WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 *  for the specific language governing rights and limitations under the
 *  License.
 *
 *  The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 *  Java(TM), hosted at https://github.com/dcm4che.
 *
 *  The Initial Developer of the Original Code is
 *  J4Care.
 *  Portions created by the Initial Developer are Copyright (C) 2015-2017
 *  the Initial Developer. All Rights Reserved.
 *
 *  Contributor(s):
 *  See @authors listed below
 *
 *  Alternatively, the contents of this file may be used under the terms of
 *  either the GNU General Public License Version 2 or later (the "GPL"), or
 *  the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 *  in which case the provisions of the GPL or the LGPL are applicable instead
 *  of those above. If you wish to allow use of your version of this file only
 *  under the terms of either the GPL or the LGPL, and not to allow others to
 *  use your version of this file under the terms of the MPL, indicate your
 *  decision by deleting the provisions above and replace them with the notice
 *  and other provisions required by the GPL or the LGPL. If you do not delete
 *  the provisions above, a recipient may use your version of this file under
 *  the terms of any one of the MPL, the GPL or the LGPL.
 *
 */

package org.dcm4chee.arc.event;

import jakarta.servlet.http.HttpServletRequest;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.util.TagUtils;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2017
 */
public class RejectionNoteSent {

    private final HttpServletRequest request;
    private final ApplicationEntity localAE;
    private final ApplicationEntity remoteAE;
    private final Attributes rejectionNote;
    private final boolean studyDeleted;
    private final int status;
    private final String errorComment;

    public RejectionNoteSent(HttpServletRequest request, ApplicationEntity localAE, ApplicationEntity remoteAE, Attributes rejectionNote,
                             boolean studyDeleted, int status, String errorComment) {
        this.request = request;
        this.localAE = localAE;
        this.remoteAE = remoteAE;
        this.rejectionNote = rejectionNote;
        this.studyDeleted = studyDeleted;
        this.status = status;
        this.errorComment = errorComment;
    }

    public HttpServletRequest getRequest() {
        return request;
    }

    public ApplicationEntity getLocalAE() {
        return localAE;
    }

    public ApplicationEntity getRemoteAE() {
        return remoteAE;
    }

    public Attributes getRejectionNote() {
        return rejectionNote;
    }

    public boolean isStudyDeleted() {
        return studyDeleted;
    }

    public int getStatus() {
        return status;
    }

    public boolean success() {
        return status == 0;
    }

    public boolean warning() {
        return (status & 0xB000) == 0xB000;
    }

    public boolean failed() {
        return !success() && !warning();
    }

    public String getErrorComment() {
        return errorComment;
    }

    @Override
    public String toString() {
        return "RejectInstancesEvent[" + request.getRemoteUser() + '@' + request.getRemoteHost()
                + ", localAET=" + localAE.getAETitle()
                + ", remoteAET=" + remoteAE.getAETitle()
                + ", studyUID=" + rejectionNote.getString(Tag.StudyInstanceUID)
                + ", studyDeleted=" + studyDeleted
                + ", status=" + TagUtils.shortToHexString(status)
                + ", errorComment=" + errorComment
                + ']';
    }
}
