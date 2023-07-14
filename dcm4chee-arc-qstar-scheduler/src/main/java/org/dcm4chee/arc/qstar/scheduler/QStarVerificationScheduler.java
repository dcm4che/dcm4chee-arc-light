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
 * Portions created by the Initial Developer are Copyright (C) 2013-2021
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

package org.dcm4chee.arc.qstar.scheduler;

import org.dcm4che3.qstar.*;
import org.dcm4chee.arc.Scheduler;
import org.dcm4chee.arc.conf.*;
import org.dcm4chee.arc.entity.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Gunter Zeilinger (gunterze@protonmail.com)
 * @since Jun 2023
 */
public class QStarVerificationScheduler extends Scheduler {
    private static final Logger LOG = LoggerFactory.getLogger(QStarVerificationScheduler.class);

    @Inject
    private QStarVerificationEJB ejb;

    protected QStarVerificationScheduler() {
        super(Scheduler.Mode.scheduleWithFixedDelay);
    }

    @Override
    protected Logger log() {
        return LOG;
    }

    @Override
    protected Duration getPollingInterval() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        return arcDev != null
                && (arcDev.getQStarVerificationURL() != null || arcDev.getQStarVerificationMockAccessState() != null)
                ? arcDev.getQStarVerificationPollingInterval()
                : null;
    }

    @Override
    protected void execute() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        List<Location> locations;
        Set<Integer> verifiedTars = new HashSet<>();
        int fetchSize = arcDev.getQStarVerificationFetchSize();
        try (QStarConnection conn = new QStarConnection(arcDev)) {
            do {
                LOG.debug("Query for objects to verify QStar Access State");
                locations = ejb.findByLocationsWithStatusCreatedBefore(
                        LocationStatus.VERIFY_QSTAR_ACCESS_STATE,
                        new Date(System.currentTimeMillis() - arcDev.getQStarVerificationDelay().getSeconds() * 1000L),
                        fetchSize);
                if (locations.isEmpty()) {
                    LOG.debug("No objects to verify QStar Access State found ");
                    break;
                }

                LOG.info("Start verifying QStar Access State of {} objects", locations.size());
                int[] counts = new int[LocationStatus.values().length];
                for (Location location : locations) {
                    StorageDescriptor storageDescriptor = arcDev.getStorageDescriptorNotNull(location.getStorageID());
                    String storagePath = location.getStoragePath();
                    int tarPathEnd;
                    if (!storageDescriptor.isArchiveSeriesAsTAR() || (tarPathEnd = storagePath.indexOf('!')) < 0) {
                        LocationStatus status = conn.nextLocationStatus(location, storagePath);
                        counts[status.ordinal()] +=
                            ejb.setLocationStatus(location.getPk(), status);
                    } else {
                        if (!verifiedTars.contains(location.getMultiReference())) {
                            LocationStatus status = conn.nextLocationStatus(location, storagePath.substring(0, tarPathEnd));
                            counts[status.ordinal()] +=
                                    ejb.setLocationStatusByMultiRef(location.getMultiReference(), status);
                            verifiedTars.add(location.getMultiReference());
                        }
                    }
                }
                for (LocationStatus locationStatus : LocationStatus.values()) {
                    if (counts[locationStatus.ordinal()] > 0)
                        LOG.info("Update status of {} objects to {}", counts[locationStatus.ordinal()],  locationStatus);
                }
            } while (locations.size() != fetchSize && arcDev.getQStarVerificationPollingInterval() != null);
        }
    }

    private static LocationStatus toLocationStatus(int accessState) {
        switch (accessState) {
            case 0:
                return LocationStatus.QSTAR_ACCESS_STATE_NONE;
            case 1:
                return LocationStatus.QSTAR_ACCESS_STATE_EMPTY;
            case 2:
                return LocationStatus.QSTAR_ACCESS_STATE_UNSTABLE;
            case 3:
                return LocationStatus.OK;
            case 4:
                return LocationStatus.QSTAR_ACCESS_STATE_OUT_OF_CACHE;
            case 5:
                return LocationStatus.QSTAR_ACCESS_STATE_OFFLINE;
            default:
                return LocationStatus.QSTAR_ACCESS_STATE_ERROR_STATUS;
        }
    }

    private final class QStarConnection implements AutoCloseable {

        private final LocationStatus mockLocationStatus;
        private final String url;
        private final String user;
        private final String password;
        private WSWebServiceSoapPort port;
        private WSUserLoginResponse userLogin;

        public QStarConnection(ArchiveDeviceExtension arcDev) {
            this.mockLocationStatus =  arcDev.getQStarVerificationMockAccessState() != null
                    ? toLocationStatus(arcDev.getQStarVerificationMockAccessState())
                    : null;
            this.url = arcDev.getQStarVerificationURLwoUserInfo();
            this.user = arcDev.getQStarVerificationUser();
            this.password = arcDev.getQStarVerificationPassword();
        }

        public LocationStatus nextLocationStatus(Location location, String filePath) {
            if (mockLocationStatus != null) return mockLocationStatus;
            if (userLogin == null) {
                port = QStarUtils.getWSWebServiceSoapPort(url);
                userLogin = QStarUtils.login(port, user, password);
                if (userLogin.getResult() != 0) {
                    LOG.warn("Failed to authenticate QStar user: {} @ {} - result={}, resultString='{}'",
                            user, url, userLogin.getResult(), userLogin.getResultString());
                    throw new RuntimeException("Failed to authenticate user: " + user + " @ " + url);
                }
            }
            WSGetFileInfoResponse fileInfo = QStarUtils.getFileInfo(port, userLogin, filePath);
            if (fileInfo.getStatus() == 0 && fileInfo.getInfo() != null) {
                long stateAccess = fileInfo.getInfo().getStateAccess();
                if (stateAccess != 3) {
                    LOG.info("Get QStar Access State {} for {} from {}",
                            QStarUtils.stateAccessAsString(stateAccess),
                            location, url);
                }
                return toLocationStatus((int) stateAccess);
            }
            LOG.warn("Failed to get QStar Access State for {} from {} - status={}",
                    location, url, fileInfo.getStatus());
            return LocationStatus.QSTAR_ACCESS_STATE_ERROR_STATUS;
        }

        @Override
        public void close() {
            if (userLogin != null && userLogin.getResult() == 0) {
                QStarUtils.logout(port, userLogin);
            }
        }

    }
}
