package org.dcm4chee.arc.qmgt;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
public class IllegalMessageStatusException extends Exception {
    public IllegalMessageStatusException(String message) {
        super(message);
    }
}
