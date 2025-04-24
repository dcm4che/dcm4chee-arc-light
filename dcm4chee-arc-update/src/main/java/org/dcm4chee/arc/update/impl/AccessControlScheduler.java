/*
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
 * Portions created by the Initial Developer are Copyright (C) 2015-2018
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
 */

package org.dcm4chee.arc.update.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4chee.arc.Scheduler;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.conf.ChangeAccessControlIDRule;
import org.dcm4chee.arc.conf.Duration;
import org.dcm4chee.arc.query.util.QueryParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Gunter Zeilinger <gunterze@protonmail.com>
 * @since Apr 2025
 */
@ApplicationScoped
public class AccessControlScheduler extends Scheduler {
    private static final Logger LOG = LoggerFactory.getLogger(AccessControlScheduler.class);

    @Inject
    UpdateServiceEJB ejb;

    protected AccessControlScheduler() {
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
                ? arcDev.getChangeAccessControlIDPollingInterval()
                : null;
    }

    @Override
    protected void execute() {
        ArchiveDeviceExtension arcDev = device.getDeviceExtension(ArchiveDeviceExtension.class);
        for (ChangeAccessControlIDRule rule : arcDev.getChangeAccessControlIDRules()) {
            String aet = rule.getAETitle();
            ApplicationEntity ae = device.getApplicationEntity(aet, true);
            if (ae == null || !ae.isInstalled()) {
                LOG.warn("No such Application Entity: " + aet);
                continue;
            }
            QueryParam queryParam = new QueryParam(ae);
            if (rule.getEntitySelectors().length == 0) {
                int updated;
                switch (rule.getEntity()) {
                    case Study:
                        updated = ejb.updateAccessControlIDOfStudies(
                               new Attributes(), queryParam, rule.getStoreAccessControlID());
                        LOG.info("{}: Changed access control ID of {} Studies to {}",
                                ae.getAETitle(), updated, rule.getStoreAccessControlID());
                        break;
                    case Series:
                        updated = ejb.updateAccessControlIDOfSeries(
                                new Attributes(), queryParam, rule.getStoreAccessControlID());
                        LOG.info("{}: Changed access control ID of {} Series with {} to {}",
                                ae.getAETitle(), updated, rule.getStoreAccessControlID());
                        break;
                }
            } else for (ChangeAccessControlIDRule.EntitySelector entitySelector : rule.getEntitySelectors()) {
                int updated;
                switch (rule.getEntity()) {
                    case Study:
                        updated = ejb.updateAccessControlIDOfStudies(
                                entitySelector.getQueryKeys(rule), queryParam, rule.getStoreAccessControlID());
                        LOG.info("{}: Changed access control ID of {} Studies with {} to {}",
                                ae.getAETitle(), updated, entitySelector, rule.getStoreAccessControlID());
                        break;
                    case Series:
                        updated = ejb.updateAccessControlIDOfSeries(
                                entitySelector.getQueryKeys(rule), queryParam, rule.getStoreAccessControlID());
                        LOG.info("{}: Changed access control ID of {} Series with {} to {}",
                                ae.getAETitle(), updated, entitySelector, rule.getStoreAccessControlID());
                        break;
                }
            }
        }
    }
}
