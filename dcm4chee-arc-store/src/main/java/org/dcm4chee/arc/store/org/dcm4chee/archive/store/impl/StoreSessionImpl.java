package org.dcm4chee.arc.store.org.dcm4chee.archive.store.impl;

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
    private final Socket socket;
    private final HL7Segment msh;
    private Storage storage;
    private Study cachedStudy;
    private final Map<String,Series> seriesCache = new HashMap<>();
    private Map<Long,UIDMap> uidMapCache = new HashMap<>();
    private Map<String, String> uidMap;

    StoreSessionImpl(HttpServletRequest httpRequest, Association as, ApplicationEntity ae,
                            Socket socket, HL7Segment msh) {
        this.httpRequest = httpRequest;
        this.as = as;
        this.ae = ae;
        this.socket = socket;
        this.msh = msh;
        this.uidMapCache = new HashMap<>();
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
    public Storage getStorage() {
        return storage;
    }

    @Override
    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    @Override
    public String getCalledAET() {
        return as != null ? as.getCalledAET() : ae.getAETitle();
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
