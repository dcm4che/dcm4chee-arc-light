/*
 * *** BEGIN LICENSE BLOCK *****
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
 * Portions created by the Initial Developer are Copyright (C) 2013
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
 * *** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.store;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.hl7.HL7Application;
import org.dcm4che3.net.hl7.UnparsedHL7Message;
import org.dcm4chee.arc.conf.AcceptConflictingPatientID;
import org.dcm4chee.arc.conf.AcceptMissingPatientID;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.PrefetchRule;
import org.dcm4chee.arc.entity.Series;
import org.dcm4chee.arc.entity.Study;
import org.dcm4chee.arc.entity.UIDMap;
import org.dcm4chee.arc.storage.Storage;
import org.dcm4chee.arc.storage.StorageFactory;

import javax.servlet.http.HttpServletRequest;
import java.io.Closeable;
import java.net.Socket;
import java.util.Map;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Jul 2015
 */
public interface StoreSession extends Closeable {
    int getSerialNo();

    Association getAssociation();

    HttpServletRequest getHttpRequest();

    Socket getSocket();

    UnparsedHL7Message getUnparsedHL7Message();

    HL7Application getLocalHL7Application();

    ApplicationEntity getLocalApplicationEntity();

    ArchiveAEExtension getArchiveAEExtension();

    StoreService getStoreService();

    Storage getStorage(String storageID, StorageFactory storageFactory);

    void putStorage(String storageID, Storage storage);

    String getCalledAET();

    String getCallingAET();

    String getRemoteHostName();

    Study getCachedStudy(String studyInstanceUID);

    Series getCachedSeries(String studyInstanceUID, String seriesIUID);

    void cacheSeries(Series series);

    boolean isNotProcessed(PrefetchRule rule);

    boolean markAsProcessed(PrefetchRule rule);

    Map<Long, UIDMap> getUIDMapCache();

    Map<String, String> getUIDMap();

    String getObjectStorageID();

    StoreSession withObjectStorageID(String objectStorageID);

    String getMetadataStorageID();

    void setMetadataStorageID(String metadataStorageID);

    AcceptConflictingPatientID getAcceptConflictingPatientID();

    AcceptMissingPatientID getAcceptMissingPatientID();

    void setAcceptConflictingPatientID(AcceptConflictingPatientID acceptConflictingPatientID);

    Attributes.UpdatePolicy getPatientUpdatePolicy();

    void setPatientUpdatePolicy(Attributes.UpdatePolicy patientUpdatePolicy);

    Attributes.UpdatePolicy getStudyUpdatePolicy();

    void setStudyUpdatePolicy(Attributes.UpdatePolicy studyUpdatePolicy);
}
