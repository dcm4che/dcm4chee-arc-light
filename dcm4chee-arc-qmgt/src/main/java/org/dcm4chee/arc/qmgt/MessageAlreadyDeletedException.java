package org.dcm4chee.arc.qmgt;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
public class MessageAlreadyDeletedException extends Exception {
    public MessageAlreadyDeletedException(String message) {
        super(message);
    }
}
