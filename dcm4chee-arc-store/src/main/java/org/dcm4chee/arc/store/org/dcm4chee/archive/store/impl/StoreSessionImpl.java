package org.dcm4chee.arc.store.org.dcm4chee.archive.store.impl;

import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.util.SafeClose;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.entity.Series;
import org.dcm4chee.arc.entity.Study;
import org.dcm4chee.arc.storage.Storage;
import org.dcm4chee.arc.store.StoreSession;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2015
 */
class StoreSessionImpl implements StoreSession {
    private final Association as;
    private final ApplicationEntity ae;
    private Storage storage;
    private Study cachedStudy;
    private final Map<String,Series> seriesCache = new HashMap<>();

    public StoreSessionImpl(Association as) {
        this.as = as;
        this.ae = as.getApplicationEntity();
    }

    @Override
    public Association getAssociation() {
        return as;
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
    public String getRemoteApplicationEntityTitle() {
        return as.getRemoteAET();
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
}
