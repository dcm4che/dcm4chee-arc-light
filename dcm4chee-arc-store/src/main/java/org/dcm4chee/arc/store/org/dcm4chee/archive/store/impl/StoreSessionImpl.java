package org.dcm4chee.arc.store.org.dcm4chee.archive.store.impl;

import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.util.SafeClose;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.OverwritePolicy;
import org.dcm4chee.arc.entity.Series;
import org.dcm4chee.arc.entity.Study;
import org.dcm4chee.arc.storage.Storage;
import org.dcm4chee.arc.store.StoreSession;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2015
 */
class StoreSessionImpl implements StoreSession {
    private final Association as;
    private final HttpServletRequest httpRequest;
    private final ApplicationEntity ae;
    private Storage storage;
    private Study cachedStudy;
    private final Map<String,Series> seriesCache = new HashMap<>();

    public StoreSessionImpl(Association as) {
        this.as = as;
        this.ae = as.getApplicationEntity();
        this.httpRequest = null;
    }

    public StoreSessionImpl(HttpServletRequest httpRequest, ApplicationEntity ae) {
        this.as = null;
        this.httpRequest = httpRequest;
        this.ae = ae;
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
        return as != null ? as.getCallingAET() : null;
    }

    @Override
    public String getRemoteHostName() {
        return httpRequest != null ? httpRequest.getRemoteHost() : as.getSocket().getInetAddress().getHostName();
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
    public String toString() {
        if (as != null)
            return as.toString();

        return httpRequest.getRemoteUser() + '@' + httpRequest.getRemoteHost() + "->" + ae.getAETitle();
    }
}
