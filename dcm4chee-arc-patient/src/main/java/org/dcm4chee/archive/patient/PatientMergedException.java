package org.dcm4chee.archive.patient;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Sep 2015
 */
public class PatientMergedException extends RuntimeException {
    public PatientMergedException() {
        super();
    }

    public PatientMergedException(String message) {
        super(message);
    }
}
