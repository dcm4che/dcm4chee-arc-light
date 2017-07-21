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

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.util.TagUtils;

import javax.servlet.http.HttpServletRequest;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2017
 */
public class InstancesRetrieved {

    private final HttpServletRequest request;
    private final String localAET;
    private final String remoteAET;
    private final String destinationAET;
    private final Attributes keys;
    private final Attributes response;

    public InstancesRetrieved(HttpServletRequest request, String localAET, String remoteAET, String destinationAET,
                              Attributes keys, Attributes response) {
        this.request = request;
        this.localAET = localAET;
        this.remoteAET = remoteAET;
        this.destinationAET = destinationAET;
        this.keys = keys;
        this.response = response;
    }

    public HttpServletRequest getRequest() {
        return request;
    }

    public String getLocalAET() {
        return localAET;
    }

    public String getRemoteAET() {
        return remoteAET;
    }

    public String getDestinationAET() {
        return destinationAET;
    }

    public Attributes getKeys() {
        return keys;
    }

    public Attributes getResponse() {
        return response;
    }

    public String getStudyInstanceUID() {
        return keys.getString(Tag.StudyInstanceUID);
    }

    public int getStatus() {
        return response.getInt(Tag.Status, -1);
    }

    public String getErrorComment() {
        return response.getString(Tag.ErrorComment);
    }

    public int warning() {
        return response.getInt(Tag.NumberOfWarningSuboperations, 0);
    }

    public int failed() {
        return response.getInt(Tag.NumberOfFailedSuboperations, 0);
    }

    public int completed() {
        return response.getInt(Tag.NumberOfCompletedSuboperations, 0);
    }

    @Override
    public String toString() {
        return "InstancesRetrieved[" + request.getRemoteUser() + '@' + request.getRemoteHost()
                + ", localAET=" + localAET
                + ", remoteAET=" + remoteAET
                + ", destinationAET=" + destinationAET
                + ", studyUID=" + getStudyInstanceUID()
                + ", status=" + TagUtils.shortToHexString(getStatus())
                + ", completed=" + completed()
                + ", failed=" + failed()
                + ", warning=" + warning()
                + ", errorComment=" + getErrorComment()
                + ']';
    }

}
