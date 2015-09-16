package org.dcm4chee.archive.stgcmt.scp;

import org.dcm4che3.data.Attributes;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Sep 2015
 */
public interface StgCmtSCP {

    void sendNEventReport(String localAET, String remoteAET, Attributes eventInfo) throws Exception;
}
