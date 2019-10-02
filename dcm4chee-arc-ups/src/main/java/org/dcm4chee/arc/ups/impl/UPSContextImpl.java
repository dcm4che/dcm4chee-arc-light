/*
 * **** BEGIN LICENSE BLOCK *****
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
 * **** END LICENSE BLOCK *****
 *
 */

package org.dcm4chee.arc.ups.impl;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.ups.UPSContext;
import org.dcm4chee.arc.ups.UPSEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Sep 2019
 */
public class UPSContextImpl implements UPSContext {
    private final Association as;
    private final HttpServletRequestInfo httpRequestInfo;
    private final ArchiveAEExtension archiveAEExtension;
    private Attributes attributes;
    private String upsInstanceUID;
    private String requesterAET;
    private String subscriberAET;
    private boolean deletionLock;
    private int status;
    private List<UPSEvent> upsEvents;

    public UPSContextImpl(HttpServletRequestInfo httpRequestInfo, ArchiveAEExtension archiveAEExtension) {
        this.as = null;
        this.httpRequestInfo = httpRequestInfo;
        this.archiveAEExtension = archiveAEExtension;
    }

    public UPSContextImpl(Association as) {
        this.as = as;
        this.requesterAET = as.getCallingAET();
        this.httpRequestInfo = null;
        this.archiveAEExtension = as.getApplicationEntity().getAEExtensionNotNull(ArchiveAEExtension.class);
    }

    @Override
    public HttpServletRequestInfo getHttpRequestInfo() {
        return httpRequestInfo;
    }

    @Override
    public Association getAssociation() {
        return as;
    }

    @Override
    public ArchiveAEExtension getArchiveAEExtension() {
        return archiveAEExtension;
    }

    @Override
    public ApplicationEntity getApplicationEntity() {
        return archiveAEExtension.getApplicationEntity();
    }

    @Override
    public ArchiveDeviceExtension getArchiveDeviceExtension() {
        return archiveAEExtension.getArchiveDeviceExtension();
    }

    @Override
    public String getUpsInstanceUID() {
        return upsInstanceUID;
    }

    @Override
    public void setUpsInstanceUID(String upsInstanceUID) {
        this.upsInstanceUID = upsInstanceUID;
    }

    @Override
    public boolean isGlobalSubscription() {
        return upsInstanceUID.equals(UID.UPSGlobalSubscriptionSOPInstance)
                || upsInstanceUID.equals(UID.UPSFilteredGlobalSubscriptionSOPInstance);
    }

    @Override
    public String getRequesterAET() {
        return requesterAET;
    }

    @Override
    public void setRequesterAET(String requesterAET) {
        this.requesterAET = requesterAET;
    }

    @Override
    public String getSubscriberAET() {
        return subscriberAET;
    }

    @Override
    public void setSubscriberAET(String subscriberAET) {
        this.subscriberAET = subscriberAET;
    }

    @Override
    public boolean isDeletionLock() {
        return deletionLock;
    }

    @Override
    public void setDeletionLock(boolean deletionLock) {
        this.deletionLock = deletionLock;
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public void setStatus(int status) {
        this.status = status;
    }

    @Override
    public Attributes getAttributes() {
        return attributes;
    }

    @Override
    public void setAttributes(Attributes attributes) {
        this.attributes = attributes;
    }

    @Override
    public List<UPSEvent> getUPSEvents() {
        if (upsEvents == null) {
            upsEvents = new ArrayList<>();
        }
        return upsEvents;
    }

    @Override
    public void addUPSEvent(UPSEvent.Type type, String upsInstanceUID, Attributes eventInformation,
            List<String> subcribers) {
        getUPSEvents().add(
                new UPSEvent(archiveAEExtension, type, upsInstanceUID, eventInformation, subcribers));
    }

    @Override
    public String toString() {
        return as != null ? as.toString()
                : httpRequestInfo.requesterUserID +
                    '@' + httpRequestInfo.requesterHost +
                    "->" + archiveAEExtension.getApplicationEntity().getAETitle();
    }
}
