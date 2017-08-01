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
import org.keycloak.KeycloakSecurityContext;
import org.keycloak.adapters.RefreshableKeycloakSecurityContext;

import javax.servlet.http.HttpServletRequest;


/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2017
 */
public class InstancesRetrieved {

    private String callingUserID;
    private String callingHost;
    private String localAET;
    private String remoteAET;
    private String destinationAET;
    private Attributes keys;
    private Attributes response;
    private String calledUserID;

    public InstancesRetrieved() {
    }

    public String getCallingUserID() {
        return callingUserID;
    }

    public String getCallingHost() {
        return callingHost;
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

    public String getCalledUserID() {
        return calledUserID;
    }

    public Attributes getResponse() {
        return response;
    }

    public InstancesRetrieved setCallingUserID(String callingUserID) {
        this.callingUserID = callingUserID;
        return this;
    }

    public InstancesRetrieved setCallingHost(String callingHost) {
        this.callingHost = callingHost;
        return this;
    }

    public InstancesRetrieved setCalledUserID(String calledUserID) {
        this.calledUserID = calledUserID;
        return this;
    }

    public InstancesRetrieved setRequestInfo(HttpServletRequest request) {
        this.callingUserID = getPreferredUsername(request);
        this.callingHost = request.getRemoteHost();
        this.calledUserID = request.getRequestURI();
        return this;
    }

    public InstancesRetrieved setLocalAET(String localAET) {
        this.localAET = localAET;
        return this;
    }

    public InstancesRetrieved setRemoteAET(String remoteAET) {
        this.remoteAET = remoteAET;
        return this;
    }

    public InstancesRetrieved setDestinationAET(String destinationAET) {
        this.destinationAET = destinationAET;
        return this;
    }

    public InstancesRetrieved setKeys(Attributes keys) {
        this.keys = keys;
        return this;
    }

    public InstancesRetrieved setResponse(Attributes response) {
        this.response = response;
        return this;
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
        return response != null ? response.getInt(Tag.NumberOfWarningSuboperations, 0) : 0;
    }

    public int failed() {
        return response != null ? response.getInt(Tag.NumberOfFailedSuboperations, 0) : 0;
    }

    public int completed() {
        return response != null ? response.getInt(Tag.NumberOfCompletedSuboperations, 0) : 0;
    }

    @Override
    public String toString() {
        return "InstancesRetrieved[" + callingUserID + '@' + callingHost
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

    private String getPreferredUsername(HttpServletRequest req) {
        return req.getAttribute("org.keycloak.KeycloakSecurityContext") != null
                ? ((RefreshableKeycloakSecurityContext) req.getAttribute(KeycloakSecurityContext.class.getName()))
                .getToken().getPreferredUsername()
                : req.getRemoteAddr();
    }

}
