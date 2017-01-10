package org.dcm4chee.arc.store;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.hl7.HL7Segment;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4chee.arc.entity.Location;
import org.dcm4chee.arc.retrieve.InstanceLocations;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.Collection;
import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jul 2015
 */
public interface StoreService {

    int DUPLICATE_REJECTION_NOTE = 0xA770;
    int SUBSEQUENT_OCCURENCE_OF_REJECTED_OBJECT = 0xA771;
    int REJECTION_FAILED_NO_SUCH_INSTANCE = 0xA772;
    int REJECTION_FAILED_CLASS_INSTANCE_CONFLICT  = 0xA773;
    int REJECTION_FAILED_ALREADY_REJECTED  = 0xA774;
    int REJECTION_FOR_RETENTION_POLICY_EXPIRED_NOT_AUTHORIZED = 0xA775;
    int RETENTION_PERIOD_OF_STUDY_NOT_YET_EXPIRED = 0xA776;
    int PATIENT_ID_MISSING_IN_OBJECT = 0xA777;
    int CONFLICTING_PID_NOT_ACCEPTED = 0xA778;

    String DUPLICATE_REJECTION_NOTE_MSG = "Rejection Note [uid={}] already received.";
    String SUBSEQUENT_OCCURENCE_OF_REJECTED_OBJECT_MSG = "Subsequent occurrence of rejected Object [uid={}, rejection={}]";
    String REJECTION_FAILED_NO_SUCH_INSTANCE_MSG = "Failed to reject Instance[uid={}] - no such Instance.";
    String REJECTION_FAILED_CLASS_INSTANCE_CONFLICT_MSG  = "Failed to reject Instance[uid={}] - class-instance conflict.";
    String REJECTION_FAILED_ALREADY_REJECTED_MSG  = "Failed to reject Instance[uid={}] - already rejected.";
    String REJECTION_FOR_RETENTION_POLICY_EXPIRED_NOT_AUTHORIZED_MSG = "Rejection for Retention Policy Expired not authorized.";
    String RETENTION_PERIOD_OF_STUDY_NOT_YET_EXPIRED_MSG = "Retention Period of Study not yet expired.";
    String PATIENT_ID_MISSING_IN_OBJECT_MSG = "Storage denied as Patient ID missing in object.";
    String NOT_AUTHORIZED = "Storage denied.";
    String FAILED_TO_QUERY_STORE_PERMISSION_SERVICE = "Failed to query Store Permission Service";
    String CONFLICTING_PID_NOT_ACCEPTED_MSG = "Patient ID in incoming object does not match with that of patient associated with study.";

    StoreSession newStoreSession(Association as);

    StoreSession newStoreSession(HttpServletRequest httpRequest, String pathParam, ApplicationEntity ae);

    StoreSession newStoreSession(ApplicationEntity ae);

    StoreSession newStoreSession(Socket socket, HL7Segment msh, ApplicationEntity ae);

    StoreContext newStoreContext(StoreSession session);

    void store(StoreContext ctx, InputStream data) throws IOException;

    void store(StoreContext ctx, Attributes attrs) throws IOException;

    Attributes copyInstances(
            StoreSession session, Collection<InstanceLocations> instances, Map<String, String> uidMap)
            throws IOException;

    Collection<InstanceLocations> queryInstances(
            StoreSession session, Attributes instanceRefs, String targetStudyIUID, Map<String, String> uidMap)
            throws IOException;
}
