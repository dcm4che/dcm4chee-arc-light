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

import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.util.SafeClose;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.OverwritePolicy;
import org.dcm4chee.arc.entity.Series;
import org.dcm4chee.arc.entity.Study;
import org.dcm4chee.arc.entity.UIDMap;
import org.dcm4chee.arc.storage.Storage;
import org.dcm4chee.arc.store.StoreSession;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jul 2015
 */
class StoreSessionImpl implements StoreSession {
    private final Association as;
    private final HttpServletRequest httpRequest;
    private final ApplicationEntity ae;
    private final String calledAET;
    private final Socket socket;
    private final HL7Segment msh;
    private final Map<String, Storage> storageMap = new HashMap<>();
    private Study cachedStudy;
    private final Map<String,Series> seriesCache = new HashMap<>();
    private Map<Long,UIDMap> uidMapCache = new HashMap<>();
    private Map<String, String> uidMap;

    StoreSessionImpl(HttpServletRequest httpRequest, String pathParam, Association as, ApplicationEntity ae,
                            Socket socket, HL7Segment msh) {
        this.httpRequest = httpRequest;
        this.as = as;
        this.ae = ae;
        this.socket = socket;
        this.msh = msh;
        this.uidMapCache = new HashMap<>();
        this.calledAET = as != null ? as.getCalledAET() : httpRequest != null ? pathParam : ae.getAETitle();
    }

    @Override
    public Association getAssociation() {
        return as;
    }

    @Override
    public HttpServletRequest getHttpRequest() {
        return httpRequest;
    }

    @Override
    public Socket getSocket() {
        return socket;
    }

    @Override
    public HL7Segment getHL7MessageHeader() {
        return msh;
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
    public Storage getStorage(String storageID) {
        return storageMap.get(storageID);
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
        return as != null ? as.getCallingAET() : msh != null ? msh.getSendingApplicationWithFacility() : null;
    }

    @Override
    public String getRemoteHostName() {
        return httpRequest != null ? httpRequest.getRemoteHost()
                : socket != null ? socket.getInetAddress().getHostName()
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
        }
        seriesCache.put(series.getSeriesInstanceUID(), series);
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
        return uidMap;
    }

    @Override
    public void setUIDMap(Map<String, String> uidMap) {
        this.uidMap = uidMap;
    }

    @Override
    public String toString() {
        return httpRequest != null
                ? httpRequest.getRemoteUser() + '@' + httpRequest.getRemoteHost() + "->" + ae.getAETitle()
                : as != null ? as.toString()
                : msh != null ? msh.toString()
                : ae.getAETitle();
    }
}
