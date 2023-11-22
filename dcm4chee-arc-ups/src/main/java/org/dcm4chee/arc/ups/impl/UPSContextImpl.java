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
import org.dcm4che3.net.Connection;
import org.dcm4che3.util.ReverseDNS;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.store.StoreContext;
import org.dcm4chee.arc.ups.UPSContext;
import org.dcm4chee.arc.ups.UPSEvent;

import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Sep 2019
 */
public class UPSContextImpl implements UPSContext {
    private final Association as;
    private final HttpServletRequestInfo httpRequestInfo;
    private final ArchiveAEExtension archiveAEExtension;
    private final ArchiveHL7ApplicationExtension archiveHL7AppExtension;
    private final Connection conn;
    private final Socket socket;
    private final Patient patient;
    private Attributes attributes;
    private Attributes mergeAttributes;
    private String upsInstanceUID;
    private String requesterAET;
    private String subscriberAET;
    private boolean deletionLock;
    private boolean template;
    private int status;
    private List<UPSEvent> upsEvents;

    public UPSContextImpl(HttpServletRequestInfo httpRequestInfo, ArchiveAEExtension archiveAEExtension) {
        this.as = null;
        this.conn = null;
        this.httpRequestInfo = httpRequestInfo;
        this.archiveAEExtension = archiveAEExtension;
        this.patient = null;
        this.socket = null;
        this.archiveHL7AppExtension = null;
    }

    public UPSContextImpl(Association as) {
        this.as = as;
        this.conn = as.getConnection();
        this.requesterAET = as.getCallingAET();
        this.httpRequestInfo = null;
        this.archiveAEExtension = as.getApplicationEntity().getAEExtensionNotNull(ArchiveAEExtension.class);
        this.patient = null;
        this.socket = null;
        this.archiveHL7AppExtension = null;
    }

    public UPSContextImpl(StoreContext storeContext, UPSOnStore rule) {
        this.as = storeContext.getStoreSession().getAssociation();
        this.conn = as.getConnection();
        this.httpRequestInfo = storeContext.getStoreSession().getHttpRequest();
        this.archiveAEExtension = storeContext.getStoreSession().getArchiveAEExtension();
        this.patient = rule.isIncludePatient()
                        ? storeContext.getStoredInstance().getSeries().getStudy().getPatient()
                        : null;
        this.socket = storeContext.getStoreSession().getSocket();
        this.archiveHL7AppExtension = null;
    }

    public UPSContextImpl(Socket socket, Connection conn, ArchiveHL7ApplicationExtension archiveHL7AppExtension) {
        this.as = null;
        this.conn = conn;
        this.httpRequestInfo = null;
        this.archiveAEExtension = archiveHL7AppExtension.getArchiveAEExtension();
        this.patient = null;
        this.socket = socket;
        this.requesterAET = archiveHL7AppExtension.getAETitle();
        this.archiveHL7AppExtension = archiveHL7AppExtension;
    }

    public UPSContextImpl(UPSContext other) {
        this.as = other.getAssociation();
        this.conn = other.getConnection();
        this.httpRequestInfo = other.getHttpRequestInfo();
        this.socket = other.getSocket();
        this.archiveAEExtension = other.getArchiveAEExtension();
        this.archiveHL7AppExtension = other.getArchiveHL7AppExtension();
        this.patient = other.getPatient();
        this.requesterAET = other.getRequesterAET();
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
        return archiveAEExtension != null
                ? archiveAEExtension.getArchiveDeviceExtension() : archiveHL7AppExtension.getArchiveDeviceExtension();
    }

    @Override
    public String getRemoteHostName() {
        return httpRequestInfo != null ? httpRequestInfo.requesterHost
                : socket != null ? ReverseDNS.hostNameOf(socket.getInetAddress())
                : null;
    }
    @Override
    public String getLocalHostName() {
        return httpRequestInfo != null ? httpRequestInfo.localHost
                : conn != null ? conn.getHostname()
                : null;
    }

    @Override
    public String getUPSInstanceUID() {
        return upsInstanceUID;
    }

    @Override
    public void setUPSInstanceUID(String upsInstanceUID) {
        this.upsInstanceUID = upsInstanceUID;
    }

    @Override
    public boolean isGlobalSubscription() {
        return upsInstanceUID.equals(UID.UPSGlobalSubscriptionInstance)
                || upsInstanceUID.equals(UID.UPSFilteredGlobalSubscriptionInstance);
    }

    @Override
    public Patient getPatient() {
        return patient;
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
    public Attributes getMergeAttributes() {
        return mergeAttributes;
    }

    @Override
    public void setMergeAttributes(Attributes mergeAttributes) {
        this.mergeAttributes = mergeAttributes;
    }

    @Override
    public boolean isTemplate() {
        return template;
    }

    @Override
    public void setTemplate(boolean template) {
        this.template = template;
    }

    @Override
    public boolean isUPSUpdateWithoutTransactionUID() {
        return archiveAEExtension.upsUpdateWithoutTransactionUID();
    }

    @Override
    public ArchiveHL7ApplicationExtension getArchiveHL7AppExtension() {
        return archiveHL7AppExtension;
    }

    @Override
    public Connection getConnection() {
        return conn;
    }

    @Override
    public Socket getSocket() {
        return socket;
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
                : httpRequestInfo != null
                    ? httpRequestInfo.requesterUserID +
                        '@' + httpRequestInfo.requesterHost +
                        "->" + archiveAEExtension.getApplicationEntity().getAETitle()
                    : socket != null
                        ? socket.toString() : archiveAEExtension.getApplicationEntity().getAETitle();
    }
}
