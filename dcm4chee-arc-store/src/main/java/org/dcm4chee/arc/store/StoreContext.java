package org.dcm4chee.arc.store;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4chee.arc.conf.RejectionNote;
import org.dcm4chee.arc.entity.Instance;
import org.dcm4chee.arc.entity.Location;
import org.dcm4chee.arc.storage.WriteContext;

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

    String getMppsInstanceUID();

    String getReceiveTranferSyntax();

    void setReceiveTransferSyntax(String transferSyntax);

    String getStoreTranferSyntax();

    void setStoreTranferSyntax(String storeTranferSyntaxUID);

    Attributes getAttributes();

    void setAttributes(Attributes dataset);

    Attributes getCoercedAttributes();

    String getStudyInstanceUID();

    String getSeriesInstanceUID();

    WriteContext getWriteContext();

    void setWriteContext(WriteContext writeContext);

    Location getLocation();

    void setLocation(Location location);

    RejectionNote getRejectionNote();

    void setRejectionNote(RejectionNote rejectionNote);

    Exception getException();

    void setException(Exception ex);

    Instance getPreviousInstance();

    void setPreviousInstance(Instance previousInstance);
}
