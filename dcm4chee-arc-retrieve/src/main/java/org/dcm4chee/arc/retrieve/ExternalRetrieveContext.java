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

package org.dcm4chee.arc.retrieve;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.util.TagUtils;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;


/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2017
 */
public class ExternalRetrieveContext {

    private HttpServletRequestInfo httpServletRequestInfo;
    private String localAET;
    private String remoteHostName;
    private String remoteAET;
    private String destinationAET;
    private Attributes keys;
    private Attributes response;
    private String deviceName;
    private String queueName;
    private String batchID;

    public ExternalRetrieveContext() {
    }

    public String getDeviceName() {
        return deviceName;
    }

    public ExternalRetrieveContext setDeviceName(String deviceName) {
        this.deviceName = deviceName;
        return this;
    }

    public String getQueueName() {
        return queueName;
    }

    public ExternalRetrieveContext setQueueName(String queueName) {
        this.queueName = queueName;
        return this;
    }

    public String getBatchID() {
        return batchID;
    }

    public ExternalRetrieveContext setBatchID(String batchID) {
        this.batchID = batchID;
        return this;
    }

    public String getRequesterUserID() {
        return httpServletRequestInfo != null ? httpServletRequestInfo.requesterUserID : null;
    }

    public String getRequesterHostName() {
        return httpServletRequestInfo != null ? httpServletRequestInfo.requesterHost : null;
    }

    public String getLocalAET() {
        return localAET;
    }

    public String getRequestURI() {
        return httpServletRequestInfo != null ? httpServletRequestInfo.requestURI : null;
    }

    public String getRemoteAET() {
        return remoteAET;
    }

    public String getRemoteHostName() {
        return remoteHostName;
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

    public HttpServletRequestInfo getHttpServletRequestInfo() {
        return httpServletRequestInfo;
    }

    public ExternalRetrieveContext setHttpServletRequestInfo(HttpServletRequestInfo httpServletRequestInfo) {
        this.httpServletRequestInfo = httpServletRequestInfo;
        return this;
    }

    public ExternalRetrieveContext setLocalAET(String localAET) {
        this.localAET = localAET;
        return this;
    }

    public ExternalRetrieveContext setRemoteAET(String remoteAET) {
        this.remoteAET = remoteAET;
        return this;
    }

    public ExternalRetrieveContext setRemoteHostName(String remoteHostName) {
        this.remoteHostName = remoteHostName;
        return this;
    }

    public ExternalRetrieveContext setDestinationAET(String destinationAET) {
        this.destinationAET = destinationAET;
        return this;
    }

    public ExternalRetrieveContext setKeys(Attributes keys) {
        this.keys = keys;
        return this;
    }

    public ExternalRetrieveContext setResponse(Attributes response) {
        this.response = response;
        return this;
    }

    public String getStudyInstanceUID() {
        return keys.getString(Tag.StudyInstanceUID);
    }

    public String getSeriesInstanceUID() {
        return keys.getString(Tag.SeriesInstanceUID);
    }

    public String getSOPInstanceUID() {
        return keys.getString(Tag.SOPInstanceUID);
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
        return "InstancesRetrieved[" + getRequesterUserID() + '@' + getRequesterHostName()
                + ", queueName=" + queueName
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
