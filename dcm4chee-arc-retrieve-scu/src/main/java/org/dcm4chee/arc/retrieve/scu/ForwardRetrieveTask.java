package org.dcm4chee.arc.retrieve.scu;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.net.service.RetrieveTask;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Dec 2015
 */
public interface ForwardRetrieveTask extends RetrieveTask {
    Attributes getFinalMoveRSP();

    Attributes getFinalMoveRSPData();
}
