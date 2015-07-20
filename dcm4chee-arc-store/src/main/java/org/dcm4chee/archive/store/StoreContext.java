package org.dcm4chee.archive.store;

import org.dcm4chee.archive.storage.StorageContext;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2015
 */
public interface StoreContext {

    StoreSession getStoreSession();

    String getSopClassUID();

    void setSOPClassUID(String string);

    String getSopInstanceUID();

    void setSOPInstanceUID(String string);

    String getOriginalTranferSyntaxUID();

    void setOriginalTransferSyntax(String transferSyntax);

    StorageContext getStorageContext();

    void setStorageContext(StorageContext storageContext);

}
