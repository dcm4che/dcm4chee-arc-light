package org.dcm4chee.arc.qmgt;

import org.dcm4chee.arc.entity.QueueMessage;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
public class MessageCanceled {

    public final QueueMessage queueMessage;

    public MessageCanceled(QueueMessage queueMessage) {
        this.queueMessage = queueMessage;
    }
}
