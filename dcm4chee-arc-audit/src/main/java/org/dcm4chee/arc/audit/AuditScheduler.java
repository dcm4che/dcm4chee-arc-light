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

package org.dcm4chee.arc.audit;

import org.dcm4che3.net.Device;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4che3.net.audit.AuditLoggerDeviceExtension;
import org.dcm4che3.util.StringUtils;
import org.dcm4chee.arc.Scheduler;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Oct 2015
 */
@ApplicationScoped
public class AuditScheduler extends Scheduler {

    private static final Logger LOG = LoggerFactory.getLogger(AuditScheduler.class);
    private static final String FAILED = ".failed";

    @Inject
    private Device device;

    @Inject
    private AuditService service;

    protected AuditScheduler() {
        super(Mode.scheduleWithFixedDelay);
    }

    @Override
    protected Logger log() {
        return LOG;
    }

    @Override
    protected Duration getPollingInterval() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        return arcDev != null && arcDev.isAuditAggregate() ? arcDev.getAuditPollingInterval() : null;
    }

    @Override
    protected void execute() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        AuditLoggerDeviceExtension ext = device.getDeviceExtension(AuditLoggerDeviceExtension.class);
        String auditSpoolDir = arcDev.getAuditSpoolDirectory();
        Duration duration = arcDev.getAuditAggregateDuration();

        if (auditSpoolDir == null || duration == null)
            return;

        Path auditSpoolDirPath = Paths.get(StringUtils.replaceSystemProperties(auditSpoolDir));
        for (AuditLogger logger : ext.getAuditLoggers()) {
            if (!logger.isInstalled())
                continue;

            Path dir = auditSpoolDirPath.resolve(logger.getCommonName().replaceAll("\\W", "_"));
            if (!Files.isDirectory(dir))
                continue;

            final long maxLastModifiedTime = System.currentTimeMillis() - duration.getSeconds() * 1000L;
            try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(dir, file ->
                    !file.getFileName().toString().endsWith(FAILED)
                        && Files.getLastModifiedTime(file).toMillis() <= maxLastModifiedTime)) {
                for (Path path : dirStream) {
                    if (arcDev.getAuditPollingInterval() == null)
                        return;

                    service.auditAndProcessFile(logger, path);
                }
            } catch (IOException e) {
                LOG.warn("Failed to access Audit Spool Directory - {}", dir, e);
            }
        }
    }
}
