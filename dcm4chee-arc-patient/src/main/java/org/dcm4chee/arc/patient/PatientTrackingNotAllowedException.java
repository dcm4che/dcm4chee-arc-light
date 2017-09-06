package org.dcm4chee.arc.patient;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Sep 2017
 */
public class PatientTrackingNotAllowedException extends RuntimeException {
    public PatientTrackingNotAllowedException() {
        super();
    }

    public PatientTrackingNotAllowedException(String message) {
        super(message);
    }
}
