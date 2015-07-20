package org.dcm4chee.archive.store.org.dcm4chee.archive.store.impl;

import org.dcm4chee.archive.storage.StorageContext;
import org.dcm4chee.archive.store.StoreContext;
import org.dcm4chee.archive.store.StoreSession;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2015
 */
class StoreContextImpl implements StoreContext {

    private final StoreSession storeSession;
    private String sopClassUID;
    private String sopInstanceUID;
    private String originalTranferSyntaxUID;
    private StorageContext storageContext;

    public StoreContextImpl(StoreSession storeSession) {
        this.storeSession = storeSession;
    }

    @Override
    public StoreSession getStoreSession() {
        return storeSession;
    }

    @Override
    public String getSopClassUID() {
        return sopClassUID;
    }

    @Override
    public void setSOPClassUID(String sopClassUID) {
        this.sopClassUID = sopClassUID;
    }

    @Override
    public String getSopInstanceUID() {
        return sopInstanceUID;
    }

    @Override
    public void setSOPInstanceUID(String sopInstanceUID) {
        this.sopInstanceUID = sopInstanceUID;
    }

    @Override
    public String getOriginalTranferSyntaxUID() {
        return originalTranferSyntaxUID;
    }

    @Override
    public void setOriginalTransferSyntax(String transferSyntaxUID) {
        this.originalTranferSyntaxUID = transferSyntaxUID;
    }

    @Override
    public StorageContext getStorageContext() {
        return storageContext;
    }

    @Override
    public void setStorageContext(StorageContext storageContext) {
        this.storageContext = storageContext;
    }
}
