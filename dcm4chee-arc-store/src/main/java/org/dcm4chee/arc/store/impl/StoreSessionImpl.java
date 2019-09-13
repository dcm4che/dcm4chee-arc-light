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

package org.dcm4chee.arc.store.impl;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4che3.util.ReverseDNS;
import org.dcm4che3.util.SafeClose;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.entity.Series;
import org.dcm4chee.arc.entity.Study;
import org.dcm4chee.arc.entity.UIDMap;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.storage.Storage;
import org.dcm4chee.arc.storage.StorageFactory;
import org.dcm4chee.arc.store.StoreService;
import org.dcm4chee.arc.store.StoreSession;

import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jul 2015
 */
class StoreSessionImpl implements StoreSession {
    private static final AtomicInteger prevSerialNo = new AtomicInteger();

    private final int serialNo;
    private ApplicationEntity ae;
    private Association as;
    private HttpServletRequestInfo httpRequest;
    private HL7Application hl7App;
    private String calledAET;
    private String callingAET;
    private Socket socket;
    private UnparsedHL7Message msg;
    private final StoreService storeService;
    private final Map<String, Storage> storageMap = new HashMap<>();
    private Study cachedStudy;
    private final Map<String,Series> seriesCache = new HashMap<>();
    private final Set<String> processedPrefetchRules = new HashSet<>();
    private final Map<Long,UIDMap> uidMapCache = new HashMap<>();
    private Map<String, String> uidMap;
    private String objectStorageID;
    private String metadataStorageID;
    private AcceptMissingPatientID acceptMissingPatientID;
    private AcceptConflictingPatientID acceptConflictingPatientID;
    private Attributes.UpdatePolicy patientUpdatePolicy;
    private Attributes.UpdatePolicy studyUpdatePolicy;
    private String impaxReportEndpoint;

    StoreSessionImpl(StoreService storeService) {
        this.serialNo = prevSerialNo.incrementAndGet();
        this.storeService = storeService;
    }

    @Override
    public String toString() {
        return httpRequest != null
                ? httpRequest.requesterUserID +
                    '@' + httpRequest.requesterHost +
                    "->" + ae.getAETitle()
                : as != null ? as.toString()
                : msg != null ? msg.msh().toString()
                : ae.getAETitle();
    }

    void setApplicationEntity(ApplicationEntity ae) {
        this.ae = ae;
        this.calledAET = ae.getAETitle();
        ArchiveAEExtension arcAE = ae.getAEExtensionNotNull(ArchiveAEExtension.class);
        this.acceptMissingPatientID = arcAE.acceptMissingPatientID();
        this.acceptConflictingPatientID = arcAE.acceptConflictingPatientID();
        ArchiveDeviceExtension arcDev = arcAE.getArchiveDeviceExtension();
        this.patientUpdatePolicy = arcDev.getAttributeFilter(Entity.Patient).getAttributeUpdatePolicy();
        this.studyUpdatePolicy = arcDev.getAttributeFilter(Entity.Study).getAttributeUpdatePolicy();
    }

    void setHttpRequest(HttpServletRequestInfo httpRequest) {
        this.httpRequest = httpRequest;
    }

    void setHL7Application(HL7Application hl7App) {
        this.hl7App = hl7App;
    }

    void setCalledAET(String calledAET) {
        this.calledAET = calledAET;
    }

    void setCallingAET(String callingAET) {
        this.callingAET = callingAET;
    }

    void setSocket(Socket socket) {
        this.socket = socket;
    }

    public void setMsg(UnparsedHL7Message msg) {
        this.msg = msg;
        this.callingAET = msg.msh().getSendingApplicationWithFacility();
    }

    void setAssociation(Association as) {
        setApplicationEntity(as.getApplicationEntity());
        this.as = as;
        this.socket = as.getSocket();
        this.calledAET = as.getCalledAET();
        this.callingAET = as.getCallingAET();
    }

    @Override
    public int getSerialNo() {
        return serialNo;
    }

    @Override
    public Association getAssociation() {
        return as;
    }

    @Override
    public HttpServletRequestInfo getHttpRequest() {
        return httpRequest;
    }

    @Override
    public Socket getSocket() {
        return socket;
    }

    @Override
    public UnparsedHL7Message getUnparsedHL7Message() {
        return msg;
    }

    @Override
    public HL7Application getLocalHL7Application() {
        return hl7App;
    }

    @Override
    public ApplicationEntity getLocalApplicationEntity() {
        return ae;
    }

    @Override
    public ArchiveAEExtension getArchiveAEExtension() {
        return ae.getAEExtension(ArchiveAEExtension.class);
    }

    @Override
    public StoreService getStoreService() {
        return storeService;
    }

    @Override
    public Storage getStorage(String storageID, StorageFactory storageFactory) {
        return storageMap.computeIfAbsent(storageID,
                x -> storageFactory.getStorage(
                        getArchiveAEExtension().getArchiveDeviceExtension().getStorageDescriptor(x)));
    }

    @Override
    public void putStorage(String storageID, Storage storage) {
        storageMap.put(storageID, storage);
    }

    @Override
    public String getCalledAET() {
        return calledAET;
    }

    @Override
    public String getCallingAET() {
        return callingAET;
    }

    @Override
    public String getRemoteHostName() {
        return httpRequest != null ? httpRequest.requesterHost
                : socket != null ? ReverseDNS.hostNameOf(socket.getInetAddress())
                : null;
    }

    @Override
    public Study getCachedStudy(String studyInstanceUID) {
        return isStudyCached(studyInstanceUID) ? cachedStudy : null;
    }

    @Override
    public Series getCachedSeries(String studyInstanceUID, String seriesIUID) {
        return isStudyCached(studyInstanceUID) ? seriesCache.get(seriesIUID) : null;
    }

    @Override
    public void cacheSeries(Series series) {
        Study study = series.getStudy();
        if (!isStudyCached(study.getStudyInstanceUID())) {
            cachedStudy = study;
            seriesCache.clear();
            processedPrefetchRules.clear();
        }
        seriesCache.put(series.getSeriesInstanceUID(), series);
    }

    @Override
    public boolean isNotProcessed(ExportPriorsRule rule) {
        return !processedPrefetchRules.contains(rule.getCommonName());
    }

    @Override
    public boolean markAsProcessed(ExportPriorsRule rule) {
        return processedPrefetchRules.add(rule.getCommonName());
    }

    private boolean isStudyCached(String studyInstanceUID) {
        return cachedStudy != null && cachedStudy.getStudyInstanceUID().equals(studyInstanceUID);
    }

    @Override
    public void close() throws IOException {
        for (Storage storage : storageMap.values())
            SafeClose.close(storage);
    }

    @Override
    public Map<Long, UIDMap> getUIDMapCache() {
        return uidMapCache;
    }

    @Override
    public Map<String, String> getUIDMap() {
        if (uidMap == null)
            uidMap = new HashMap<>();

        return uidMap;
    }

    @Override
    public String getObjectStorageID() {
        return objectStorageID;
    }

    @Override
    public StoreSession withObjectStorageID(String objectStorageID) {
        this.objectStorageID = objectStorageID;
        return this;
    }

    @Override
    public String getMetadataStorageID() {
        return metadataStorageID;
    }

    @Override
    public void setMetadataStorageID(String metadataStorageID) {
        this.metadataStorageID = metadataStorageID;
    }

    @Override
    public AcceptMissingPatientID getAcceptMissingPatientID() {
        return acceptMissingPatientID;
    }

    @Override
    public AcceptConflictingPatientID getAcceptConflictingPatientID() {
        return acceptConflictingPatientID;
    }

    @Override
    public void setAcceptConflictingPatientID(AcceptConflictingPatientID acceptConflictingPatientID) {
        this.acceptConflictingPatientID = acceptConflictingPatientID;
    }

    @Override
    public Attributes.UpdatePolicy getPatientUpdatePolicy() {
        return patientUpdatePolicy;
    }

    @Override
    public void setPatientUpdatePolicy(Attributes.UpdatePolicy patientUpdatePolicy) {
        this.patientUpdatePolicy = patientUpdatePolicy;
    }

    @Override
    public Attributes.UpdatePolicy getStudyUpdatePolicy() {
        return studyUpdatePolicy;
    }

    @Override
    public void setStudyUpdatePolicy(Attributes.UpdatePolicy studyUpdatePolicy) {
        this.studyUpdatePolicy = studyUpdatePolicy;
    }

    @Override
    public String getImpaxReportEndpoint() {
        return impaxReportEndpoint;
    }

    @Override
    public void setImpaxReportEndpoint(String impaxReportEndpoint) {
        this.impaxReportEndpoint = impaxReportEndpoint;
    }
}
