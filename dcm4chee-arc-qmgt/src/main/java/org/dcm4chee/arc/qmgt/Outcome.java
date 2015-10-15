package org.dcm4chee.arc.qmgt;

import org.dcm4chee.arc.entity.QueueMessage;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
public class Outcome {

    private final QueueMessage.Status status;
    private final String description;

    public Outcome(QueueMessage.Status status, String description) {
        this.status = status;
        this.description = description;
    }

    public QueueMessage.Status getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }
}
