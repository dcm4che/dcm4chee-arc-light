package org.dcm4chee.arc.qmgt;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
public class MessageCanceled {

    private final String messageID;

    public MessageCanceled(String messageID) {
        this.messageID = messageID;
    }

    public String getMessageID() {
        return messageID;
    }
}
