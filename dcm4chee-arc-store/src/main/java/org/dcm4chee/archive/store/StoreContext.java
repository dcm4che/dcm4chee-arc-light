package org.dcm4chee.archive.store;

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
}
