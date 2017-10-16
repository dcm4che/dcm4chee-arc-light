package org.dcm4chee.arc.store;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4chee.arc.retrieve.InstanceLocations;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.Collection;
import java.util.List;
import java.util.zip.ZipInputStream;

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
    int REJECTION_FOR_RETENTION_POLICY_EXPIRED_NOT_ALLOWED = 0xA775;
    int RETENTION_PERIOD_OF_STUDY_NOT_YET_EXPIRED = 0xA776;
    int PATIENT_ID_MISSING_IN_OBJECT = 0xA777;
    int CONFLICTING_PID_NOT_ACCEPTED = 0xA778;

    String DUPLICATE_REJECTION_NOTE_MSG = "Rejection Note [uid={0}] already received.";
    String SUBSEQUENT_OCCURENCE_OF_REJECTED_OBJECT_MSG = "Subsequent occurrence of rejected Object [uid={0}, rejection={1}]";
    String REJECTION_FAILED_NO_SUCH_INSTANCE_MSG = "Failed to reject Instance[uid={0}] - no such Instance.";
    String REJECTION_FAILED_NO_SUCH_SERIES_MSG = "Failed to reject Instance of Series[uid={0}] - no such Series.";
    String REJECTION_FAILED_CLASS_INSTANCE_CONFLICT_MSG  = "Failed to reject Instance[uid={0}] - class-instance conflict.";
    String REJECTION_FAILED_ALREADY_REJECTED_MSG  = "Failed to reject Instance[uid={0}] - already rejected.";
    String REJECTION_FOR_RETENTION_POLICY_EXPIRED_NOT_ALLOWED_MSG = "Rejection for Retention Policy Expired not allowed.";
    String RETENTION_PERIOD_OF_STUDY_NOT_YET_EXPIRED_MSG = "Retention Period of Study not yet expired.";
    String PATIENT_ID_MISSING_IN_OBJECT_MSG = "Patient ID missing in object.";
    String NOT_AUTHORIZED = "Storage denied.";
    String FAILED_TO_QUERY_STORE_PERMISSION_SERVICE = "Failed to query Store Permission Service";
    String CONFLICTING_PID_NOT_ACCEPTED_MSG = "Patient ID {0} differs from Patient ID {1} in previous received object of Study[uid={2}].";

    StoreSession newStoreSession(Association as);

    StoreSession newStoreSession(HttpServletRequest httpRequest, String pathParam, ApplicationEntity ae);

    StoreSession newStoreSession(ApplicationEntity ae);

    StoreSession newStoreSession(HL7Application hl7App, Socket socket, UnparsedHL7Message msg, ApplicationEntity ae);

    StoreContext newStoreContext(StoreSession session);

    void store(StoreContext ctx, InputStream data) throws IOException;

    void store(StoreContext ctx, Attributes attrs) throws IOException;

    Attributes copyInstances(StoreSession session, Collection<InstanceLocations> instances)
            throws Exception;

    Collection<InstanceLocations> queryInstances(
            StoreSession session, Attributes instanceRefs, String targetStudyIUID)
            throws IOException;

    ZipInputStream openZipInputStream(
            StoreSession session, String storageID, String storagePath, String studyUID)
            throws IOException;

    void restoreInstances(StoreSession session, String studyUID, String seriesUID) throws IOException;

    List<String> studyIUIDsByAccessionNo(String accNo);
}
