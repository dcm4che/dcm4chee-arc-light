package org.dcm4chee.archive.store;

import org.dcm4che3.data.Attributes;
import org.dcm4chee.archive.entity.Location;
import org.dcm4chee.archive.storage.StorageContext;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Jul 2015
 */
public interface StoreContext {

    StoreSession getStoreSession();

    String getSopClassUID();

    void setSopClassUID(String string);

    String getSopInstanceUID();

    void setSopInstanceUID(String string);

    String getReceiveTranferSyntax();

    void setReceiveTransferSyntax(String transferSyntax);

    String getStoreTranferSyntax();

    void setStoreTranferSyntax(String storeTranferSyntaxUID);

    Attributes getAttributes();

    void setAttributes(Attributes dataset);

    String getStudyInstanceUID();

    String getSeriesInstanceUID();

    StorageContext getStorageContext();

    void setStorageContext(StorageContext storageContext);

}
