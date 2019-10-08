/*
 * **** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * J4Care.
 * Portions created by the Initial Developer are Copyright (C) 2015-2019
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * **** END LICENSE BLOCK *****
 *
 */
package org.dcm4chee.arc.store;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.Duration;
import org.dcm4chee.arc.entity.Instance;
import org.dcm4chee.arc.entity.Location;
import org.dcm4chee.arc.keycloak.HttpServletRequestInfo;
import org.dcm4chee.arc.storage.ReadContext;

import javax.management.Attribute;
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
    int CONFLICTING_PATIENT_ATTRS_REJECTED = 0xA779;

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
    String CONFLICTING_PATIENT_ATTRS_REJECTED_MSG = "Patient with {0} differs from previous received object in attribute {1} {2}";

    StoreSession newStoreSession(Association as);

    StoreSession newStoreSession(HttpServletRequestInfo httpRequestInfo, ApplicationEntity ae, String sourceAET);

    StoreSession newStoreSession(ApplicationEntity ae);

    StoreSession newStoreSession(HL7Application hl7App, Socket socket, UnparsedHL7Message msg, ApplicationEntity ae);

    StoreContext newStoreContext(StoreSession session);

    void store(StoreContext ctx, InputStream data) throws IOException;

    void addStorageID(String studyIUID, String storageID);

    void scheduleMetadataUpdate(String studyIUID, String seriesIUID);

    void store(StoreContext ctx, Attributes attrs) throws IOException;

    void importInstanceOnStorage(StoreContext ctx, Attributes attrs, ReadContext readCtx) throws IOException;

    Attributes copyInstances(StoreSession session, Collection<InstanceLocations> instances, Attributes coerceAttrs,
                             Attributes.UpdatePolicy updatePolicy)
            throws Exception;

    ZipInputStream openZipInputStream(
            StoreSession session, String storageID, String storagePath, String studyUID)
            throws IOException;

    List<Instance> restoreInstances(StoreSession session, String studyUID, String seriesUID, Duration duration)
            throws IOException;

    List<String> studyIUIDsByAccessionNo(String accNo);

    void addLocation(StoreSession storeSession, Long instancePk, Location location);

    void replaceLocation(StoreSession storeSession, Long instancePk, Location newLocation,
            List<Location> replaceLoactions);

    void compress(StoreContext ctx, InstanceLocations inst, InputStream data)
            throws IOException;

    void updateLocations(ArchiveAEExtension arcAE, List<UpdateLocation> updateLocations);
}
